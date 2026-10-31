# SFR Host — Android Web Hosting & Public Tunnel Controller

**SFR Host** adalah aplikasi Android native profesional yang memungkinkan Anda mengubah smartphone Android menjadi web hosting pribadi gratis dengan memanfaatkan koneksi internet HP sendiri.

> **Konsep Dasar:**
> - **HP Android** = Mesin server web sesungguhnya (file website disimpan di memori HP, HTTP Web Server berjalan di background HP).
> - **SFR Host APK** = Controller & Process Manager (mengatur engine web server lokal, monitoring resource CPU/RAM/Storage, reverse tunnel, file manager, dan domain routing).
> - **Reverse Tunnel** = Penghubung keluar (outbound connection) dari HP menuju jaringan publik untuk menembus CGNAT seluler/Wi-Fi tanpa perlu port-forwarding router atau IP publik statis.
> - **Domain Pengguna** = Custom domain milik pengguna sendiri (misal: `tokoku.com`, `portofolio.id`), diarahkan via DNS CNAME ke tunnel.
> - **Pengunjung** = Dapat mengakses website Anda dari mana saja (seluler, Wi-Fi lain, luar negeri) melalui internet publik.

---

## 📱 Identitas Aplikasi

- **Nama Aplikasi:** SFR Host
- **Package Name / Application ID:** `com.sfrhostapp`
- **Versi Aplikasi:** `SFRHOST-R-001`
- **Version Code:** `1`
- **Developer:** Septian Fathur Rohman
- **Platform:** Android Native (Kotlin + Jetpack Compose + Material 3)
- **Minimum OS:** Android 8.0 (API 26)
- **Arsitektur:** Native Android (Embedded ServerSocket HTTP Server, OkHttp WebSocket Client, Foreground Service, DataStore Preferences)

---

## 🛠️ Cara Build APK

Aplikasi ini menggunakan Gradle (Kotlin DSL). Anda dapat langsung mem-build APK dari terminal / Android Studio:

### 1. Build APK Debug
```bash
gradle assembleDebug
```
**Lokasi Output APK Debug:**
```
app/build/outputs/apk/debug/app-debug.apk
```

### 2. Build APK Release
```bash
gradle assembleRelease
```
**Lokasi Output APK Release:**
```
app/build/outputs/apk/release/app-release.apk
```

> **Catatan Proteksi & Keamanan:**
> Build release mengaktifkan R8 Code Shrinking, Resource Shrinking, dan Obfuscation Proguard untuk mencegah reverse engineering serta meminimalkan ukuran APK.

---

## 📦 Daftar Dependensi

- **UI Framework:** Jetpack Compose, Material Design 3 (M3)
- **Networking & Tunneling:** OkHttp 4.12.0 (WebSocket Reverse Proxy Client & HTTP connection pooling)
- **Data Persistence:** AndroidX DataStore Preferences (Konfigurasi port, tunnel, token, & domain)
- **Background Processing:** Android Foreground Service dengan Persistent Notification Ticker (`specialUse`) & Floating Window Overlay (`SYSTEM_ALERT_WINDOW`)
- **Remote Push Notification:** Firebase Cloud Messaging (FCM) & Android WorkManager
- **Coroutines:** Kotlinx Coroutines Android

---

## 🗂️ Struktur Direktori File Website di HP

File website disimpan langsung di penyimpanan perangkat (`/sdcard/SFRHost/websites/` atau direktori data aplikasi) dengan struktur standar:

```
SFRHost/websites/
├── website1/
│   ├── public_html/          <- File website publik (index.html, style.css, script.js, gambar)
│   ├── logs/                 <- Catatan access logs
│   ├── config/               <- Pengaturan website
│   └── backups/              <- Arsip cadangan
└── website2/
    ├── public_html/
    ├── logs/
    ├── config/
    └── backups/
```

File Manager bawaan SFR Host memungkinkan Anda membuat folder, membuat file HTML/CSS/JS, mengunggah file dari HP, mengedit kode langsung di layar HP, mengganti nama, dan menghapus file.

---

## 🌐 Dokumentasi Konfigurasi Tunneling

Karena kartu seluler (4G/5G) dan Wi-Fi rumahan umumnya berada di balik **CGNAT** (Carrier-Grade NAT) tanpa IP publik statis, koneksi masuk langsung dari internet akan diblokir oleh operator seluler/router.

SFR Host mengatasi hal ini dengan **Reverse Outbound Tunnel**: HP membuka koneksi keluar ke server perantara publik, lalu meneruskan request pengunjung kembali ke server lokal HP di port 8080.

### Opsi 1: Cloudflare Argo Tunnel (Direkomendasikan - Gratis)
1. Buat akun di [Cloudflare Zero Trust](https://one.dash.cloudflare.com/) (Gratis).
2. Masuk ke **Networks** -> **Tunnels** -> **Create a Tunnel**.
3. Pilih **Cloudflared** dan beri nama tunnel (misal `hp-sfrhost`).
4. Salin **Tunnel Token** yang diberikan oleh Cloudflare.
5. Buka tab **Tunnel** di SFR Host, tempelkan Token tersebut, lalu klik simpan.
6. Di Cloudflare Zero Trust, tambahkan Public Hostname:
   - **Service:** `HTTP`
   - **URL:** `127.0.0.1:8080` (port lokal server HP).
7. Klik **Start Tunnel** di aplikasi SFR Host. Website Anda kini live di domain Cloudflare dengan HTTPS otomatis.

### Opsi 2: Custom VPS Gateway Tunnel (Milik Pribadi)
Jika Anda memiliki server VPS sendiri (misal Ubuntu/Debian):
1. Jalankan relay gateway di VPS Anda menggunakan script Node.js atau reverse proxy WebSocket (tersedia di folder `/gateway/`).
2. Masukkan alamat IP/Domain VPS Anda di **Settings** -> **VPS Gateway Endpoint** di aplikasi SFR Host.
3. Klik **Start Tunnel**. HP akan terhubung ke VPS Anda dan menerima request web secara real-time.

---

## 🌍 Dokumentasi Konfigurasi Custom Domain & DNS

Untuk menghubungkan domain Anda sendiri (misal `domainanda.com`):

1. Masuk ke penyedia DNS domain Anda (Cloudflare, Niagahoster, Domainesia, Namecheap, dll.).
2. Buat rekaman DNS baru:
   - **Type:** `CNAME`
   - **Name:** `@` (atau subdomain misal `web`)
   - **Target / Content:** Hostname tunnel Anda (misal `xxxx.cfargotunnel.com` atau domain gateway VPS Anda).
   - **Proxy status:** Aktif (Proxied jika menggunakan Cloudflare)
3. Buka tab **Domains** di SFR Host, klik **Tambah Domain**, dan masukkan nama domain Anda.
4. Klik **Verifikasi DNS** untuk memastikan propagasi CNAME telah merespons.

---

## 🧭 Panduan Penggunaan Langkah demi Langkah

1. **Buka Aplikasi:** Pada saat pertama kali dibuka, pelajari panduan onboarding.
2. **Kelola Website:** Masuk ke tab **Files** atau **Hosting** untuk melihat file `index.html`. Edit tampilan website sesuai keinginan Anda.
3. **Nyalakan Web Server:** Di tab **Dashboard**, klik tombol **START SERVER**. Web server lokal HP akan aktif di port 8080.
4. **Hubungkan Tunnel:** Masukkan konfigurasi Cloudflare Token atau Gateway VPS Anda, lalu klik **START TUNNEL**.
5. **Cek Akses Publik:** Uji akses website Anda dari HP lain atau laptop dengan jaringan seluler/Wi-Fi berbeda. Website Anda 100% online!
6. **Floating Bar:** Aktifkan Floating Bar di menu Settings untuk memantau status server, uptime, dan hit request yang melayang di atas aplikasi lain.

---

## 👨‍💻 Informasi Developer

- **Nama Developer:** Septian Fathur Rohman
- **WhatsApp:** [082130392599](https://wa.me/6282130392599)
- **Telegram:** [@sfrdigital](https://t.me/sfrdigital)
- **Instagram:** [@_septianfr](https://www.instagram.com/_septianfr/)
- **GitHub Repository:** [https://github.com/septianofc/sfr-host](https://github.com/septianofc/sfr-host)
