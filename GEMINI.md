# Proyek ZIVPN Native - Dokumentasi Pengerjaan

Dokumen ini mencatat upaya pengembangan dan otomatisasi yang sedang dilakukan dalam direktori ini.

## Fokus Utama
Mengembangkan aplikasi Android native **ZIVPN** yang mengintegrasikan beberapa komponen core untuk performa maksimal dan stabilitas tanpa harus bergantung sepenuhnya pada modul Magisk.

## Arsitektur Sistem
Aplikasi ini menggunakan alur trafik sebagai berikut:
1.  **Android VpnService**: Membuat interface `tun0` dan mengelola routing sistem.
2.  **Clash/Mihomo Meta (Core Go)**: Bertindak sebagai pengatur trafik (otak) yang menerima File Descriptor dari `tun0`.
3.  **Proxy Exit**: Clash dikonfigurasi untuk meneruskan trafik ke **SOCKS5 127.0.0.1:7777**.
4.  **Load Balancer (libload)**: Mendengarkan pada port `7777` dan membagi beban ke beberapa instance Hysteria.
5.  **Hysteria (libuz)**: Berjalan dalam 8 instance (port 1080-1087) untuk memaksa kecepatan tunnel.

## Status Pengerjaan Direktori
*   **zivpn-native/**: Repositori utama aplikasi Android. Telah diperbaiki sistem build CMake-nya dan dikonfigurasi fitur rilis otomatis.
*   **zivpn_proxy_build/**: Modul Magisk lama yang sedang di-porting logikanya ke versi native app.
*   **Lainnya**: Berbagai script pembantu (`service.sh`, `injector.py`) dan binari core untuk keperluan testing dan debugging.

## Otomatisasi CI/CD
Telah diimplementasikan workflow GitHub Actions di `zivpn-native`:
*   **Build Debug**: Menghasilkan Universal APK untuk testing cepat.
*   **Build Release**: Otomatis dipicu oleh tag `v*` (misal: `v2.11.21-release`).
*   **Target**: Khusus **arm64-v8a** untuk efisiensi ukuran.
*   **Signing**: Menggunakan `gh secret` untuk menyimpan keystore secara aman di GitHub.

## Referensi Modul Magisk
Proyek native ini adalah evolusi dari **zivpn_proxy_build** (Modul Magisk). AI harus memperhatikan hal-hal berikut:
*   **Logika Routing**: Semua logika `iptables` dan manajemen interface TUN yang ada di `zivpn_proxy_build/service.sh` adalah referensi utama untuk diimplementasikan ke dalam kode Kotlin (`TunService.kt`) dan Go di proyek native.
*   **Kunci Teknis (Anchors)**: Penggunaan **Port 7777** adalah titik temu tetap antara Load Balancer (`libload`) dan sistem tunnel. Jangan mengubah port ini tanpa sinkronisasi di seluruh komponen.
*   **Transisi Teknologi**: Menggantikan `hev-socks5` (sederhana) dengan **Clash Meta** (kompleks/native) agar mendapatkan fitur routing berbasis rule (anti-iklan, game mode) yang lebih baik.

## Instruksi Analisis AI
Sebelum melakukan modifikasi, AI **wajib**:
1.  **Pahami Kode**: Membaca file-file inti (`TunService.kt`, `ConfigurationModule.kt`, `tun.go`) secara menyeluruh.
2.  **Verifikasi Logika**: Memastikan apakah alur trafik (Android -> Clash -> libload -> Hysteria) sudah sinkron dan tidak ada konflik (seperti masalah `auto-route` yang pernah diperbaiki).
3.  **Cross-Reference**: Selalu bandingkan logika baru dengan referensi modul Magisk untuk memastikan tidak ada fitur fungsional yang tertinggal.

## Protokol Perencanaan (Planning)
Untuk setiap tugas refactoring, penambahan fitur, atau perbaikan bug yang kompleks, AI **harus** merancang strategi terlebih dahulu dengan **minimal 5 langkah terstruktur**:
1.  **Analisis Dampak**: Identifikasi bagian mana yang akan terpengaruh.
2.  **Pemetaan Dependensi**: Cek hubungan antar komponen (Kotlin, Go, C++).
3.  **Langkah Eksekusi**: Rincian teknis urutan perubahan kode.
4.  **Rencana Pengujian**: Bagaimana memverifikasi bahwa perubahan tersebut berhasil.
5.  **Strategi Rollback**: Langkah antisipasi jika perubahan menyebabkan kegagalan build atau sistem.

## Tujuan Akhir & Standar Hasil
Setiap modifikasi harus memberikan hasil yang sesuai dengan **Tujuan Utama**, yaitu:
*   **Fungsionalitas Penuh**: VPN harus bisa terhubung (connect) dan mengalirkan trafik internet melalui tunnel Hysteria dengan stabil.
*   **Kualitas Native**: Aplikasi tidak boleh crash dan harus mampu menangani siklus hidup Android (background/foreground) dengan baik.
*   **User Experience**: Migrasi dari modul Magisk ke aplikasi native harus membuat penggunaan VPN menjadi lebih mudah bagi pengguna awam melalui antarmuka (UI).
*   **Performa**: Mempertahankan keunggulan kecepatan Load Balancer (port 7777) dan Hysteria dari versi Magisk sebelumnya.

## Status Terkini (Checkpoint - 2 Januari 2026 - FINAL PLATINUM)
**STATUS: UNKILLABLE, LITE & PRODUCTION READY**
ZIVPN Native telah mencapai bentuk finalnya. Dioptimalkan untuk ukuran (Lite) dan ketahanan terhadap background kill (Unkillable).

### Fitur "Anti-Kill" (Stability):
1.  **WakeLock Implemented**: `TunService` memegang `PARTIAL_WAKE_LOCK` selama VPN aktif untuk mencegah CPU deep sleep.
2.  **Suspend Mode Disabled**: `SuspendModule` dimodifikasi agar **TIDAK PERNAH** mensuspend Clash Core saat layar mati.
3.  **Process Persistence**: Manifest `TunService` diset `stopWithTask="false"`, mencegah kill saat swipe recent apps.
4.  **Crash Fixes**: Menangani `InterruptedIOException` saat stop dan `FileNotFoundException` saat startup.

### Optimasi Ukuran (Lite):
*   **GeoIP Removed**: Database IP dunia dihapus (~10MB saved). Logika startup dibersihkan.
*   **Code Purge**: Activity/Service tak terpakai dihapus fisik file-nya (NewProfile, Settings, dll).
*   **Dependencies Trimmed**: Library QR Scanner (`quickie`) dihapus.

### Ringkasan Teknis Final:
1.  **Branch**: `cleanup-unused-features` (Main Development).
2.  **Size**: ~20MB (arm64-v8a Release).
3.  **Permissions**: Ditambahkan `WAKE_LOCK`.

## Performance Tuning (Experimental - 2 Jan 2026)
Optimasi ini bertujuan untuk meningkatkan throughput dan mengurangi CPU usage, namun meningkatkan penggunaan RAM.

### Parameter Berubah:
1.  **Go Runtime**:
    *   `GOGC` = **200** (Default: 100). *Efek: GC lebih jarang, Speed naik, RAM usage naik.*
    *   `GOMAXPROCS` = **CPU Core Count**. *Efek: Multithreading optimal.*
2.  **Hysteria (`libuz`)**:
    *   `recvwindow` = **6553600** (6.25MB). *Efek: Bandwidth naik drastis.*
3.  **Clash Core**:
    *   `tcp-concurrent` = **true**. *Efek: Proses TCP paralel.*
    *   `keep-alive-interval` = **15s**.

> **Note:** Jika terjadi OOM Kill (Aplikasi sering keluar sendiri karena RAM penuh), kembalikan `GOGC` ke 100 dan `recvwindow` ke 327680.

## Checkpoint Teknis
| Komponen | Status | Detail |
| :--- | :--- | :--- |
| **Background** | **ROCK SOLID** | WakeLock + No Suspend + stopWithTask=false. |
| **Core Engine** | **ACTIVE** | Hysteria + LB running continuously (Insomnia Mode). |
| **Asset Size** | **MINIMAL** | No GeoIP files included. |
| **Startup** | **SMOOTH** | Zero-Config & Auto-Init Profile. |

## Catatan Lingkungan
*   **Platform**: Android (Termux)
*   **Tools**: `gh` CLI, `git`, `gradle`, `cmake`
*   **Mode**: Root digunakan untuk inspeksi jaringan secara mendalam.

---
*Terakhir diperbarui oleh Gemini: 1 Januari 2026*
