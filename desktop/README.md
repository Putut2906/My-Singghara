# My Singghara untuk PC (Windows, macOS, Linux)

Versi desktop mandiri: berjalan tanpa HP dan tanpa jaringan. Aplikasi Android tidak diubah sama sekali;
folder ini adalah build Gradle terpisah yang memakai ulang kode dan UI web dari folder `app/`.

## Menjalankan

Butuh JDK 17 atau lebih baru (disarankan 21, sama dengan CI Android).

```bash
cd desktop
./gradlew run                              # jalankan (Windows: gradlew.bat run)
./gradlew packageDistributionForCurrentOS  # buat installer untuk OS yang sedang dipakai
```

Installer keluar di `desktop/build/compose/binaries/main/` (`.exe`/`.msi`, `.dmg`, atau `.deb`).
Untuk membangun semuanya sekaligus, jalankan workflow **Build Desktop**
(`.github/workflows/build-desktop.yml`) di GitHub Actions (tab Actions, Run workflow); satu runner per OS.

**Hanya butuh `.exe` di Windows?**

```bat
gradlew.bat createDistributable
```

Hasilnya folder `build\compose\binaries\main\app\My Singghara\` berisi `My Singghara.exe` lengkap dengan
runtime Java, jadi bisa dijalankan atau di-zip tanpa instalasi dan tanpa WiX. Installer `.exe`/`.msi`
(`gradlew.bat packageExe packageMsi`) membutuhkan WiX Toolset 3.x. Build harus dilakukan di Windows;
jpackage tidak bisa membuat `.exe` dari Linux atau macOS.

## Cara kerja

```
Jendela kontrol Compose + ikon baki ─┐
                                     ▼
Jendela aplikasi (Edge/Chrome/Chromium --app) ──HTTP──▶ DesktopServer (127.0.0.1:18765)
                                                          ├─ WebUiPage + 21 *WebScript   (UI, sama persis dengan HP)
                                                          ├─ aset web (CSS/JS/font/ikon) (dari app/src/main/assets)
                                                          └─ Api  ─▶ Store ─▶ Room ─▶ SQLite di folder data pengguna
```

- **UI tidak ditulis ulang.** `WebUiPage` diekstrak otomatis dari `LocalHttpServer.getWebUiHtml()` saat build
  (tugas `generateWebUiPage`), dan 21 berkas `*WebScript.kt` disalin apa adanya (`syncSharedSources`).
  Perubahan UI web di Android otomatis ikut ke desktop pada build berikutnya.
- **Data memakai entity dan DAO Room yang sama** (`Models.kt`, `Daos.kt`, dst.), basis data versi 18.
- **Server hanya mendengarkan `127.0.0.1`**, tanpa header CORS, dengan pemeriksaan `Host` dan `Origin`. Berbeda dari
  versi HP, tidak ada PIN/QR karena datanya milik pengguna PC ini dan tidak terlihat dari jaringan.
- Data ada di `%APPDATA%\MySingghara` (Windows), `~/Library/Application Support/MySingghara` (macOS),
  `~/.local/share/MySingghara` (Linux).

## Status fitur

| Sudah jalan di desktop | Belum (menjawab 501, halaman menampilkan keadaan kosong) |
|---|---|
| Dashboard (jam, kalender Jawa/Hijriah, jadwal salat hitungan), notifikasi agenda | Anggota/ID card, presensi, gaji |
| Catatan, Daftar Belanja, Agenda, Jadwal Harian, Tabungan | Kasir, pembukuan, laporan keuangan, catatan keuangan |
| Profil (nama dan avatar bawaan), zona waktu, font jam | Arisan, belanja pasar |
| Aset web, ikon, manifest | Al-Qur'an, azan (audio), musik, video |
| | Handy Talky, Telegram AI, berbagi/transfer file, backup, akun/admin (Firebase) |

Halaman yang belum dipindah tidak membuat aplikasi macet, tetapi **menambah data di halaman itu tidak akan
tersimpan**. Jangan dipakai untuk data sungguhan sebelum rutenya dipindahkan.

## Status verifikasi (jujur)

Diuji di lingkungan tanpa akses Maven, memakai compiler Kotlin dan Chromium:

- Terverifikasi: tugas Gradle `syncSharedSources` dan `generateWebUiPage`; kompilasi `Api`, `DesktopServer`,
  `Store`, helper JSON, serta semua berkas yang dibagi dengan Android; server dijalankan dan dipanggil lewat
  HTTP (CRUD catatan, belanja, agenda, jadwal, tabungan, dashboard, pengaman Host/Origin); UI dimuat di
  Chromium tanpa error JavaScript.
- **Belum pernah dikompilasi/dijalankan:** `Main.kt` (Compose), `DesktopDatabase`/`RoomStore` terhadap Room
  asli (hanya dicek dengan stub), KSP Room, dan packaging `jpackage`. Build pertama di mesinmu adalah
  ujiannya; versi pustaka ada di `build.gradle.kts` dan mudah dinaikkan.

## Jika build pertama gagal

- *`Cannot find implementation for ...DesktopDatabase`*: Room tidak menemukan kelas hasil KSP. Pastikan
  tugas `kspKotlin` jalan dan `ksp { arg("room.generateKotlin", "true") }` masih ada. Jika tetap gagal,
  pindah ke plugin `kotlin("multiplatform")` dengan target `jvm()` dan pola `@ConstructedBy` dari dokumentasi Room KMP.
- *Versi Compose Multiplatform tidak cocok dengan Kotlin 2.2.10*: naikkan plugin `org.jetbrains.compose`
  (dan Kotlin bila perlu); jangan ubah `upgradeUuid` Windows.
- *Port 18765 terpakai*: server otomatis mencoba 20 port berikutnya dan mengingat yang dipakai.
- *Tidak ada Edge/Chrome/Chromium*: halaman dibuka di browser bawaan sistem. Isi `browser_path` di
  `settings.properties` untuk memaksa browser tertentu.
- macOS: runner `macos-latest` menghasilkan `.dmg` arm64. Untuk Mac Intel, bangun di runner x64.
  Ikon Dock memerlukan berkas `.icns` (lihat komentar di `build.gradle.kts`).

## Tahap 2: urutan yang disarankan

1. **Pindahkan rute satu per satu** ke `Api.kt`: anggota/presensi/gaji, catatan keuangan, arisan, belanja
   pasar. Tiap rute = fungsi di `Store`, implementasi di `RoomStore`, satu `when` di `Api`.
   Salin bentuk JSON dari `LocalHttpServer.kt`.
2. **Backup/restore** (`/api/backup`): memungkinkan memindahkan data dari HP ke PC dengan format JSON yang sama.
3. **Hapus salinan**: pindahkan `getWebUiHtml()` dan helper Hijri/Jawa (kini disalin di `CalendarInfo.kt`) ke
   berkas mandiri di `app/`, lalu ganti `generateWebUiPage` dengan `syncSharedSources` biasa.
4. **Kasir dan pengaturan** yang kini di SharedPreferences Android: pindahkan ke tabel Room atau
   `settings.properties` agar dibagi dua platform.
5. Fitur khusus perangkat: azan lewat `javax.sound`, Al-Qur'an dan musik dari aset, Firebase lewat REST.
6. Jendela tertanam (tanpa Edge/Chrome): tambahkan KCEF (`compose-webview-multiplatform`) dan
   tampilkan `server.url` di dalam `Window`. Konsekuensinya Chromium sekitar 100 MB diunduh saat peluncuran pertama.

## Catatan

- Skema desktop dimulai dari versi 18 tanpa migrasi. Jika `AppDatabase.kt` Android naik versi atau menambah
  entity, samakan `DesktopDatabase.kt` dan tambahkan migrasinya.
- Data PC dan HP belum tersinkron otomatis (menunggu backup/restore di Tahap 2).
- Teks "Ã¢â‚¬Â¢" di bawah judul halaman adalah kesalahan penyandian yang sudah ada di `LocalHttpServer.kt`
  Android (5 baris; maksudnya "•"), muncul juga di versi HP.
