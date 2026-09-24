import html
import sys
import json
from pathlib import Path
import shutil

REMOTE_REPO: Path = Path.cwd()
LOCAL_REPO: Path = REMOTE_REPO.parent.joinpath(sys.argv[2])

try:
    to_delete: list[str] = json.loads(sys.argv[1])
except Exception:
    to_delete = []

for module in to_delete:
    apk_name = f"aniyomi-{module}-v*.*.apk"
    icon_name = f"eu.kanade.tachiyomi.animeextension.{module}.png"
    for file in REMOTE_REPO.joinpath("apk").glob(apk_name):
        print(f"Removing old APK: {file.name}")
        file.unlink(missing_ok=True)
    for file in REMOTE_REPO.joinpath("apk").glob(f"aniyomi-{module}.apk"):
        print(f"Removing old APK: {file.name}")
        file.unlink(missing_ok=True)
    for file in REMOTE_REPO.joinpath("icon").glob(icon_name):
        print(f"Removing old icon: {file.name}")
        file.unlink(missing_ok=True)

REMOTE_REPO.joinpath("apk").mkdir(parents=True, exist_ok=True)
REMOTE_REPO.joinpath("icon").mkdir(parents=True, exist_ok=True)

if LOCAL_REPO.joinpath("apk").exists():
    shutil.copytree(src=LOCAL_REPO.joinpath("apk"), dst=REMOTE_REPO.joinpath("apk"), dirs_exist_ok=True)
if LOCAL_REPO.joinpath("icon").exists():
    shutil.copytree(src=LOCAL_REPO.joinpath("icon"), dst=REMOTE_REPO.joinpath("icon"), dirs_exist_ok=True)

remote_index = []
if REMOTE_REPO.joinpath("index.json").exists():
    try:
        with REMOTE_REPO.joinpath("index.json").open(encoding="utf-8") as remote_index_file:
            remote_index = json.load(remote_index_file)
    except Exception as e:
        print(f"Warning reading remote index.json: {e}")

local_index = []
local_index_path = LOCAL_REPO.joinpath("index.min.json")
if not local_index_path.exists():
    local_index_path = LOCAL_REPO.joinpath("index.json")

if local_index_path.exists():
    try:
        with local_index_path.open(encoding="utf-8") as local_index_file:
            local_index = json.load(local_index_file)
    except Exception as e:
        print(f"Warning reading local index: {e}")

local_pkgs = {item["pkg"] for item in local_index}
index = [
    item for item in remote_index
    if item["pkg"] not in local_pkgs and not any(item["pkg"].endswith(f".{module}") for module in to_delete)
]
index.extend(local_index)
index.sort(key=lambda x: x.get("name", ""))

with REMOTE_REPO.joinpath("index.json").open("w", encoding="utf-8") as index_file:
    json.dump(index, index_file, ensure_ascii=False, indent=2)

for item in index:
    for source in item.get("sources", []):
        source.pop("versionId", None)

with REMOTE_REPO.joinpath("index.min.json").open("w", encoding="utf-8") as index_min_file:
    json.dump(index, index_min_file, ensure_ascii=False, separators=(",", ":"))

# Ensure repo.json exists
if not REMOTE_REPO.joinpath("repo.json").exists() and LOCAL_REPO.joinpath("repo.json").exists():
    shutil.copy(LOCAL_REPO.joinpath("repo.json"), REMOTE_REPO.joinpath("repo.json"))

with REMOTE_REPO.joinpath("index.html").open("w", encoding="utf-8") as index_html_file:
    index_html_file.write('<!DOCTYPE html>\n<html>\n<head>\n<meta charset="UTF-8">\n<title>Aniyomi Anime Extensions Repo</title>\n</head>\n<body>\n<pre>\n')
    for entry in index:
        apk_escaped = 'apk/' + html.escape(entry["apk"])
        name_escaped = html.escape(entry["name"])
        version_escaped = html.escape(str(entry.get("version", "")))
        index_html_file.write(f'<a href="{apk_escaped}">{name_escaped} (v{version_escaped})</a>\n')
    index_html_file.write('</pre>\n</body>\n</html>\n')

print(f"Merged {len(local_index)} local extensions into remote repo (total {len(index)} extensions).")
