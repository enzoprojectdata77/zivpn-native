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

## Status Terkini (Checkpoint - 1 Januari 2026 - Final UI Build)
*   **Perbaikan UI**: Kesalahan kompilasi pada `ZivpnSettingsDesign` (adapter & icon) telah diperbaiki.
*   **Menunggu Build**: Sedang menunggu workflow `ZIVPN Native Build` (ID: `20640866136`) selesai.
*   **Ekspektasi**: Build ini akan menghasilkan APK yang memiliki menu pengaturan ZIVPN fungsional.

## Checkpoint Teknis
| Komponen | Status | Detail |
| :--- | :--- | :--- |
| **ZIVPN Settings UI** | **Fixed** | Added StringAdapter & Valid Icons |
| **Dynamic Config** | **Selesai** | `TunService` membaca dari `ZivpnStore` (Default value = Magisk Config) |
| Build Pipeline | Running | APK Alpha Debug & Release (Optimized arm64-v8a) |
| Hysteria (libuz) | Terintegrasi | 8 Instance (1080-1087), JSON Config via Cache |
| Load Balancer | Terintegrasi | Port 7777, tunnel ke 8 port lokal |

## Catatan Lingkungan
*   **Platform**: Android (Termux)
*   **Tools**: `gh` CLI, `git`, `gradle`, `cmake`
*   **Mode**: Root digunakan untuk inspeksi jaringan secara mendalam.

---
*Terakhir diperbarui oleh Gemini: 1 Januari 2026*
