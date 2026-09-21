<div align="center">

# 🎬 Aniyomi Indonesian Anime Extensions

Koleksi ekstensi anime khusus **Bahasa Indonesia (ID)** untuk aplikasi [**Aniyomi**](https://github.com/myramm/aniyomi).

[![Build Indonesian Extensions](https://github.com/myramm/aniyomi-extensions/actions/workflows/build_extensions.yml/badge.svg)](https://github.com/myramm/aniyomi-extensions/actions/workflows/build_extensions.yml)
[![Releases](https://img.shields.io/github/v/release/myramm/aniyomi-extensions?color=blue&label=Download%20APK)](https://github.com/myramm/aniyomi-extensions/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

---

## 🇮🇩 Daftar Ekstensi Anime Indonesia

Berikut adalah daftar sumber anime Indonesia yang didukung dalam repository ini:

| Sumber / Ekstensi | Status | Modul |
| :--- | :---: | :--- |
| **OtakuDesu** | ✅ Aktif | `src/id/otakudesu` |
| **Samehadaku** | ✅ Aktif | `src/id/samehadaku` |
| **AnimeIndo** | ✅ Aktif | `src/id/animeindo` |
| **Kuramanime** | ✅ Aktif | `src/id/kuramanime` |
| **Kuronime** | ✅ Aktif | `src/id/kuronime` |
| **Neonime** | ✅ Aktif | `src/id/neonime` |
| **Nimegami** | ✅ Aktif | `src/id/nimegami` |
| **Oploverz** | ✅ Aktif | `src/id/oploverz` |
| **Minioppai** | ✅ Aktif | `src/id/minioppai` |

---

## 📥 Cara Download & Install Ekstensi

1. Buka halaman [**GitHub Releases**](https://github.com/myramm/aniyomi-extensions/releases/latest).
2. Pilih dan download file `.apk` ekstensi anime yang ingin Anda gunakan (misal `aniyomi-id-samehadaku-*.apk`).
3. Install file APK tersebut di HP Android Anda.
4. Buka aplikasi **Aniyomi** ➔ Masuk ke menu **Browse (Jelajahi)** ➔ Ekstensi akan langsung muncul dan siap digunakan untuk streaming anime.

---

## 🛠️ Panduan Pengembangan (Development)

Jika Anda ingin mengedit atau menambahkan ekstensi baru secara lokal:

### 1. Compile & Cek Error
```bash
# Ganti dengan nama modul ekstensi yang ingin dicek
./gradlew :src:id:samehadaku:compileDebugKotlin
```

### 2. Build APK Ekstensi Tertentu
```bash
./gradlew :src:id:samehadaku:assembleDebug
```
*File APK hasil kompilasi akan berada di folder `src/id/<nama>/build/outputs/apk/debug/`.*

### 3. Build Semua Ekstensi Sekaligus
```bash
./gradlew assembleDebug
```

---

## ⚖️ Disclaimer

* Ekstensi ini dibuat untuk tujuan edukasi dan mempermudah scraping data publik.
* Repository ini tidak meng-host file video anime apa pun di server GitHub. Semua konten dan video streaming disediakan langsung oleh masing-masing website penyedia pihak ketiga.
