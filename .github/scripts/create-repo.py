import json
import os
import re
import shutil
import subprocess
from pathlib import Path
from zipfile import ZipFile

PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
IS_NSFW_REGEX = re.compile(r"'tachiyomi.animeextension.nsfw' value='([^']+)'")
APPLICATION_LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
APPLICATION_LABEL_GENERIC = re.compile(r"application: label='([^']*)'")
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
APPLICATION_ICON_GENERIC = re.compile(r"application: label='[^']*' icon='([^']*)'")
LANGUAGE_REGEX = re.compile(r"aniyomi-([a-zA-Z0-9]+)")

# Locate aapt safely
aapt_binary = shutil.which("aapt") or "aapt"

android_candidates = [
    os.environ.get("ANDROID_HOME", ""),
    os.environ.get("ANDROID_SDK_ROOT", ""),
    "/usr/local/lib/android/sdk",
    "/root/android-sdk"
]

for candidate_dir in android_candidates:
    if candidate_dir:
        build_tools_path = Path(candidate_dir) / "build-tools"
        if build_tools_path.exists():
            available = sorted([d for d in build_tools_path.iterdir() if d.is_dir()])
            if available:
                candidate = available[-1] / "aapt"
                if candidate.exists():
                    aapt_binary = str(candidate)
                    break

REPO_DIR = Path("repo")
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

REPO_APK_DIR.mkdir(parents=True, exist_ok=True)
REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

inspector_data = {}
output_json = Path("output.json")
if output_json.exists():
    try:
        with open(output_json, encoding="utf-8") as f:
            inspector_data = json.load(f)
    except Exception as e:
        print(f"Warning loading output.json: {e}")

index_min_data = []

for apk in sorted(REPO_APK_DIR.glob("*.apk")):
    badging = ""
    try:
        badging = subprocess.check_output(
            [
                aapt_binary,
                "dump",
                "--include-meta-data",
                "badging",
                str(apk),
            ]
        ).decode("utf-8", errors="ignore")
    except Exception as e:
        print(f"Warning running aapt for {apk.name}: {e}")

    # Extract language
    lang_match = LANGUAGE_REGEX.search(apk.name)
    language = lang_match.group(1) if lang_match else "id"

    package_name = ""
    package_info_match = PACKAGE_NAME_REGEX.search(badging)
    if package_info_match:
        package_name = package_info_match.group(1)
    else:
        # Fallback from apk name
        ext_slug = apk.stem.replace(f"aniyomi-{language}-", "").split("-v")[0]
        package_name = f"eu.kanade.tachiyomi.animeextension.{language}.{ext_slug.lower()}"

    version_code_match = VERSION_CODE_REGEX.search(badging)
    version_code = int(version_code_match.group(1)) if version_code_match else 1

    version_name_match = VERSION_NAME_REGEX.search(badging)
    version_name = version_name_match.group(1) if version_name_match else "1.0"

    app_label_match = APPLICATION_LABEL_REGEX.search(badging) or APPLICATION_LABEL_GENERIC.search(badging)
    if app_label_match:
        app_name = app_label_match.group(1)
    else:
        ext_slug = apk.stem.replace(f"aniyomi-{language}-", "").split("-v")[0]
        app_name = f"Aniyomi: {ext_slug.capitalize()}"

    icon_match = APPLICATION_ICON_320_REGEX.search(badging) or APPLICATION_ICON_GENERIC.search(badging)
    if icon_match:
        icon_path = icon_match.group(1)
        try:
            with ZipFile(apk) as z:
                if icon_path in z.namelist():
                    with z.open(icon_path) as i, (REPO_ICON_DIR / f"{package_name}.png").open("wb") as f:
                        f.write(i.read())
        except Exception as e:
            print(f"Warning extracting icon from {apk.name}: {e}")

    # Fallback icon extraction
    if not (REPO_ICON_DIR / f"{package_name}.png").exists():
        try:
            with ZipFile(apk) as z:
                for name in z.namelist():
                    if "ic_launcher" in name and name.endswith(".png"):
                        with z.open(name) as i, (REPO_ICON_DIR / f"{package_name}.png").open("wb") as f:
                            f.write(i.read())
                        break
        except Exception as e:
            print(f"Warning extracting fallback icon from {apk.name}: {e}")

    nsfw_match = IS_NSFW_REGEX.search(badging)
    nsfw = int(nsfw_match.group(1)) if nsfw_match else 0

    sources = inspector_data.get(package_name, [])

    if len(sources) == 1:
        source_language = sources[0].get("lang", language)
        if (
            source_language != language
            and source_language not in {"all", "other"}
            and language not in {"all", "other"}
        ):
            language = source_language

    min_data = {
        "name": app_name,
        "pkg": package_name,
        "apk": apk.name,
        "lang": language,
        "code": version_code,
        "version": version_name,
        "nsfw": nsfw,
        "hasReadme": 0,
        "hasChangelog": 0,
        "sources": [],
    }

    if sources:
        for source in sources:
            min_data["sources"].append(
                {
                    "name": source.get("name", app_name.replace("Aniyomi: ", "")),
                    "lang": source.get("lang", language),
                    "id": str(source.get("id", "")),
                    "baseUrl": source.get("baseUrl", ""),
                    "versionId": source.get("versionId", 1),
                }
            )
    else:
        clean_name = app_name.replace("Aniyomi: ", "").strip()
        min_data["sources"].append(
            {
                "name": clean_name,
                "lang": language,
                "id": str(abs(hash(package_name))),
                "baseUrl": "",
                "versionId": 1,
            }
        )

    index_min_data.append(min_data)

# Write index.min.json
with REPO_DIR.joinpath("index.min.json").open("w", encoding="utf-8") as index_file:
    json.dump(index_min_data, index_file, ensure_ascii=False, separators=(",", ":"))

# Write index.json
with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as index_file:
    json.dump(index_min_data, index_file, ensure_ascii=False, indent=2)

# Extract SHA-256 certificate fingerprint if possible
fingerprint = ""
try:
    first_apk = next(REPO_APK_DIR.glob("*.apk"))
    cert_out = subprocess.check_output(["keytool", "-printcert", "-jarfile", str(first_apk)]).decode("utf-8", errors="ignore")
    for line in cert_out.splitlines():
        if "SHA256:" in line or "SHA-256:" in line:
            fingerprint = line.split(":", 1)[1].strip().replace(":", "").lower()
            break
except Exception as e:
    print(f"Warning getting cert fingerprint: {e}")

if not fingerprint:
    fingerprint = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905"

repo_meta = {
    "meta": {
        "name": "Aniyomi Indonesia",
        "shortName": "Aniyomi-ID",
        "website": "https://github.com/myramm/anime-repo",
        "signingKeyFingerprint": fingerprint
    }
}

with REPO_DIR.joinpath("repo.json").open("w", encoding="utf-8") as repo_file:
    json.dump(repo_meta, repo_file, ensure_ascii=False, indent=2)

# Generate index.html landing page
html_rows = []
for item in index_min_data:
    html_rows.append(f'<li><a href="apk/{item["apk"]}">{item["name"]} (v{item["version"]})</a></li>')

html_content = f"""<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Aniyomi Indonesian Anime Extensions</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; max-width: 700px; margin: 40px auto; padding: 0 20px; line-height: 1.6; color: #24292f; background: #f6f8fa; }}
        .card {{ background: #ffffff; padding: 24px; border-radius: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }}
        h1 {{ margin-top: 0; color: #cf222e; }}
        a.btn {{ display: inline-block; background: #cf222e; color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 6px; font-weight: bold; margin: 15px 0; }}
        a.btn:hover {{ background: #a40e26; }}
        code {{ background: #eaeef2; padding: 3px 6px; border-radius: 4px; font-size: 0.9em; word-break: break-all; }}
        ul {{ list-style-type: disc; padding-left: 20px; }}
        li {{ margin-bottom: 8px; }}
    </style>
</head>
<body>
    <div class="card">
        <h1>🎬 Aniyomi Indonesian Anime Extensions</h1>
        <p>Koleksi ekstensi anime subtitle Indonesia untuk Aniyomi.</p>
        <a class="btn" href="https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/myramm/anime-repo/repo/index.min.json">📲 Tambahkan ke Aniyomi (1-Klik)</a>
        <h3>URL Repository:</h3>
        <p><code>https://raw.githubusercontent.com/myramm/anime-repo/repo/index.min.json</code></p>
        <p><code>https://raw.githubusercontent.com/myramm/anime-repo/repo/repo.json</code></p>
        <hr>
        <h3>Daftar APK Ekstensi:</h3>
        <ul>
            {"".join(html_rows)}
        </ul>
    </div>
</body>
</html>
"""

with REPO_DIR.joinpath("index.html").open("w", encoding="utf-8") as html_file:
    html_file.write(html_content)

print(f"Successfully generated repo metadata with {len(index_min_data)} extensions.")
