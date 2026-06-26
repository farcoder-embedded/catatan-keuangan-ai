# Catatan Keuangan AI

Aplikasi Android sederhana untuk mencatat pemasukan dan pengeluaran pribadi atau usaha kecil. Data disimpan lokal di SQLite dan dapat disinkronkan ke Google Drive dalam folder:

`AI_folderbase/Catatan Keuangan AI/ledger.json`

## Fitur

- Tambah pemasukan dan pengeluaran
- Kategori dan catatan singkat
- Ringkasan saldo, pemasukan, dan pengeluaran
- Riwayat transaksi
- Hapus transaksi
- Sinkronisasi Google Drive untuk akses dari beberapa perangkat dengan akun Google yang sama
- Konflik dasar antar-perangkat ditangani dengan `updatedAt`; versi terbaru per transaksi akan menang

## Cara Menjalankan

1. Buka folder ini di Android Studio.
2. Pastikan Android Studio mengunduh Gradle dan dependency yang dibutuhkan.
3. Buat proyek di Google Cloud Console.
4. Aktifkan Google Drive API.
5. Buat OAuth Client ID untuk Android dengan package name:

   `com.aifolderbase.catatankeuangan`

6. Isi SHA-1 debug/release certificate sesuai build yang dipakai.
7. Jalankan aplikasi di perangkat Android.
8. Tekan `Sinkron ke Google Drive`, login dengan akun Google, lalu izinkan akses Drive.

## Cara Build APK Tanpa Android Studio

Pilihan paling ringan untuk PC spek rendah adalah build di GitHub Actions:

1. Upload folder proyek ini ke repository GitHub.
2. Buka tab `Actions`.
3. Pilih workflow `Build Android APK`.
4. Klik `Run workflow`.
5. Setelah selesai, buka hasil workflow dan unduh artifact `catatan-keuangan-debug-apk`.
6. Di dalam artifact tersebut ada `app-debug.apk` yang bisa dipindahkan ke HP dan diinstall.

Catatan untuk Google Drive: salin nilai `SHA1` dari log step `Show debug certificate SHA-1`, lalu masukkan ke OAuth Client Android di Google Cloud Console untuk package:

`com.aifolderbase.catatankeuangan`

Alternatif lokal tanpa Android Studio tetap memungkinkan, tetapi masih perlu mengunduh JDK, Android SDK command-line tools, dan Gradle. Ukurannya lebih kecil dari Android Studio, tetapi tetap butuh internet dan setup manual.

## Catatan Sinkronisasi

Aplikasi akan membuat folder `AI_folderbase` jika belum ada, lalu membuat folder `Catatan Keuangan AI` di dalamnya. Semua perangkat yang memakai akun Google yang sama akan memakai file `ledger.json` yang sama.

Untuk penggunaan 3-5 perangkat, biasakan menekan tombol sinkron setelah menambah atau menghapus catatan, dan tekan sinkron saat membuka aplikasi di perangkat lain.

## Struktur Data Drive

```json
{
  "schemaVersion": 1,
  "updatedAt": 1710000000000,
  "records": [
    {
      "id": "uuid",
      "type": "income",
      "category": "Penjualan",
      "note": "Order toko",
      "amount": 250000,
      "occurredAt": 1710000000000,
      "updatedAt": 1710000000000,
      "deleted": false
    }
  ]
}
```
