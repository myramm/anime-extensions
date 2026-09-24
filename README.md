<div align="center">

# 🎬 Aniyomi Indonesian Anime Extensions

Koleksi ekstensi anime khusus **Bahasa Indonesia (ID)** untuk aplikasi [**Aniyomi**](https://github.com/myramm/aniyomi).

| Install on Aniyomi | Build Status | Download APK |
|:------------------:|:------------:|:------------:|
| [![Install on Aniyomi](https://img.shields.io/badge/Click%20here%20to%20install%20repo-gray?style=for-the-badge&labelColor=red&logo=android)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/myramm/anime-repo/repo/index.min.json) | [![Build Indonesian Extensions](https://github.com/myramm/anime-extensions/actions/workflows/build_extensions.yml/badge.svg)](https://github.com/myramm/anime-extensions/actions/workflows/build_extensions.yml) | [![Releases](https://img.shields.io/github/v/release/myramm/anime-extensions?color=blue&label=Download%20APK)](https://github.com/myramm/anime-extensions/releases) |

</div>

---

## 🔗 Cara Menambahkan Repository ke Aniyomi

### 1. Otomatis (1-Klik)
Klik tombol di bawah ini langsung dari HP Android Anda (pastikan aplikasi Aniyomi sudah terinstall):

[![Install on Aniyomi](https://img.shields.io/badge/Tambahkan%20Repo%20ke%20Aniyomi-Klik%20Disini-red?style=for-the-badge&logo=android)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/myramm/anime-repo/repo/index.min.json)

---

### 2. Manual (Copy URL JSON Raw)
Salin salah satu URL repository di bawah ini:

* **Anime Repo (`index.min.json`)**:
```text
https://raw.githubusercontent.com/myramm/anime-repo/repo/index.min.json
```

* **Format Repo (`repo.json`)**:
```text
https://raw.githubusercontent.com/myramm/anime-repo/repo/repo.json
```

**Langkah menambahkan di aplikasi Aniyomi:**
1. Buka aplikasi **Aniyomi**.
2. Masuk ke **More (Lainnya)** ➔ **Settings (Pengaturan)** ➔ **Browse (Jelajahi)**.
3. Pilih **Extension repositories (Repositori ekstensi)**.
4. Klik tombol **Add repository (Tambah repositori)**.
5. Tempel (*paste*) URL di atas lalu simpan.
6. Masuk ke tab **Browse ➔ Extensions**, semua ekstensi anime Indonesia akan muncul dan bisa di-install langsung dari dalam aplikasi!

---

## 🇮🇩 Daftar Ekstensi Anime Indonesia

| Sumber / Ekstensi | Status | Modul | Website / Keterangan |
| :--- | :---: | :--- | :--- |
| **Animasu** | ✅ Aktif | `src/id/animasu` | https://animasu.love (Animestream theme, fast streaming) |
| **AnimeIndo** | ✅ Aktif | `src/id/animeindo` | https://animeindo.skin (Katalog anime lengkap) |
| **OtakuDesu** | ✅ Aktif | `src/id/otakudesu` | https://otakudesu.blog (Streaming & batch sub Indo) |
| **Kuronime** | ✅ Aktif | `src/id/kuronime` | https://kuronime.org (Streaming sub Indo) |
| **NimeGami** | ✅ Aktif | `src/id/nimegami` | https://nimegami.id (Batch & episode video HD) |
| **MiniOppai** | ✅ Aktif | `src/id/minioppai` | https://minioppai.org (Koleksi anime & NSFW) |

---

## 📥 Download APK Manual

Jika Anda tidak ingin menambahkan URL repository dan lebih memilih download file APK secara manual:
👉 [**Download APK di GitHub Releases**](https://github.com/myramm/anime-extensions/releases/latest)

---

## 🛠️ Panduan Pengembangan & Build di GitHub

Build dan test otomatis dijalankan via **GitHub Actions** di cloud runner tanpa membebani VPS lokal.

Setiap kali ada commit/push baru atau trigger manual (`workflow_dispatch`), workflow akan otomatis:
1. Mem-build seluruh APK ekstensi
2. Menghasilkan metadata repository (`index.min.json`, `index.json`, `repo.json`)
3. Men-deploy dan men-sinkronisasi ke repository [**myramm/anime-repo**](https://github.com/myramm/anime-repo) branch `repo`
4. Mempublikasikan rilis baru di GitHub Releases

---

## ⚖️ Disclaimer

* Ekstensi ini dibuat untuk tujuan edukasi dan mempermudah scraping data publik.
* Repository ini tidak meng-host file video anime apa pun. Semua konten disediakan langsung oleh masing-masing website penyedia pihak ketiga.
