<div align="center">

# 🎬 Aniyomi Indonesian Anime Extensions

Koleksi ekstensi anime khusus **Bahasa Indonesia (ID)** untuk aplikasi [**Aniyomi**](https://github.com/myramm/aniyomi).

| Install on Aniyomi | Build Status | Download APK |
|:------------------:|:------------:|:------------:|
| [![Install on Aniyomi](https://img.shields.io/badge/Click%20here%20to%20install%20repo-gray?style=for-the-badge&labelColor=red&logo=android)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/myramm/aniyomi-extensions/repo/index.min.json) | [![Build Indonesian Extensions](https://github.com/myramm/aniyomi-extensions/actions/workflows/build_extensions.yml/badge.svg)](https://github.com/myramm/aniyomi-extensions/actions/workflows/build_extensions.yml) | [![Releases](https://img.shields.io/github/v/release/myramm/aniyomi-extensions?color=blue&label=Download%20APK)](https://github.com/myramm/aniyomi-extensions/releases) |

</div>

---

## 🔗 Cara Menambahkan Repository ke Aniyomi

### 1. Otomatis (1-Klik)
Klik tombol di bawah ini langsung dari HP Android Anda (pastikan aplikasi Aniyomi sudah terinstall):

[![Install on Aniyomi](https://img.shields.io/badge/Tambahkan%20Repo%20ke%20Aniyomi-Klik%20Disini-red?style=for-the-badge&logo=android)](https://intradeus.github.io/http-protocol-redirector/?r=aniyomi://add-repo?url=https://raw.githubusercontent.com/myramm/aniyomi-extensions/repo/index.min.json)

---

### 2. Manual (Copy URL JSON Raw)
Salin salah satu URL repository di bawah ini:

* **Format Index (`index.min.json`)**:
```text
https://raw.githubusercontent.com/myramm/aniyomi-extensions/repo/index.min.json
```

* **Format Repo (`repo.json`)**:
```text
https://raw.githubusercontent.com/myramm/aniyomi-extensions/repo/repo.json
```

**Langkah menambahkan di aplikasi Aniyomi:**
1. Buka aplikasi **Aniyomi**.
2. Masuk ke **More (Lainnya)** ➔ **Settings (Pengaturan)** ➔ **Browse (Jelajahi)**.
3. Pilih **Extension repositories (Repositori ekstensi)**.
4. Klik tombol **Add repository (Tambah repositori)**.
5. Tempel (*paste*) URL di atas lalu simpan.
6. Masuk ke tab **Browse ➔ Extensions**, semua ekstensi anime Indonesia akan muncul dan bisa di-install langsung dari dalam aplikasi!

---

## 🇮🇩 Daftar Ekstensi Anime Indonesia (100% Anti-Cloudflare & Stabil)

| Sumber / Ekstensi | Status | Modul | Keterangan |
| :--- | :---: | :--- | :--- |
| **AnimeIndo** | ✅ Aktif | `src/id/animeindo` | Katalog anime lengkap, streaming lancar |
| **Kuronime** | ✅ Aktif | `src/id/kuronime` | Streaming & download sub Indo cepat |
| **NimeGami** | ✅ Aktif | `src/id/nimegami` | Batch & episode video kualitas tinggi |
| **MiniOppai** | ✅ Aktif | `src/id/minioppai` | Koleksi streaming & search lancar |

> ℹ️ **Catatan:** Sumber yang menggunakan proteksi bot Cloudflare Turnstile/WAF yang sering bermasalah di WebView Android (seperti Kuramanime & OtakuDesu) telah dinonaktifkan demi stabilitas dan kelancaran pemutaran video.

---

## 📥 Download APK Manual

Jika Anda tidak ingin menambahkan URL repository dan lebih memilih download file APK secara manual:
👉 [**Download APK di GitHub Releases**](https://github.com/myramm/aniyomi-extensions/releases/latest)

---

## 🛠️ Panduan Pengembangan (Development)

Jika ingin mengedit atau menambahkan ekstensi baru:

```bash
# Cek compile kode
./gradlew :src:id:samehadaku:compileDebugKotlin

# Build APK lokal
./gradlew :src:id:samehadaku:assembleDebug

# Build seluruh ekstensi
./gradlew assembleDebug
```

---

## ⚖️ Disclaimer

* Ekstensi ini dibuat untuk tujuan edukasi dan mempermudah scraping data publik.
* Repository ini tidak meng-host file video anime apa pun. Semua konten disediakan langsung oleh masing-masing website penyedia pihak ketiga.
