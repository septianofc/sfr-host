# PANDUAN DEPLOYMENT PUBLIC GATEWAY SFR HOST

Dokumen ini menjelaskan cara menjalankan **SFR Host Public Gateway** di server VPS publik (DigitalOcean, AWS, GCP, Oracle Cloud, Linode, atau VPS lokal Indonesia).

---

## 1. Arsitektur Komunikasi

```
[ Pengunjung Publik di Internet ]
              │ (Membuka https://tokoku.com dari Wi-Fi / 4G / 5G mana pun)
              ▼
    [ Cloudflare / DNS ]
              │ (CNAME / A record mengarah ke Public IP Gateway)
              ▼
[ SFR HOST PUBLIC GATEWAY (VPS) ]
              │
              │  WebSocket Reverse Tunnel (Koneksi keluar dari HP)
              │  - Menembus CGNAT & Firewall Seluler
              │  - Persistent bi-directional stream
              ▼
   [ HP ANDROID PENGGUNA ]
   (Origin Web Server + File Website tokoku/)
```

---

## 2. Cara Cepat Menjalankan Gateway (Docker)

### Prasyarat:
- 1 Unit VPS dengan Public IPv4 (Ubuntu 20.04/22.04/24.04).
- Docker & Docker Compose terinstal.
- Port 80 dan 443 terbuka di Firewall / Security Group.

### Langkah Instalasi:
1. Clone atau salin folder `gateway/` ke VPS Anda:
   ```bash
   mkdir -p /opt/sfrhost-gateway
   cd /opt/sfrhost-gateway
   # Salin package.json, server.js, Dockerfile, docker-compose.yml
   ```

2. Jalankan Gateway:
   ```bash
   docker compose up -d --build
   ```

3. Cek Status:
   ```bash
   curl http://localhost/_sfr/health
   # Respons: {"status":"ONLINE","service":"SFR Host Public Gateway", ...}
   ```

---

## 3. Menghubungkan Custom Domain Pengguna (Contoh: `tokoku.com`)

1. **Pengguna Membuka Menu "Custom Domains" di Aplikasi SFR Host Android:**
   - Tambah domain: `tokoku.com` atau `www.tokoku.com`.
   - Pilih proyek website yang dituju (misal: `website1`).

2. **Pengguna Menambahkan DNS Record di Registrar / Cloudflare:**
   - **Type:** `CNAME`
   - **Name:** `@` atau `www`
   - **Target:** `tunnel.your-gateway-domain.com` (atau IP Publik Gateway VPS Anda / Cloudflare Tunnel CNAME)
   - **Proxy Status:** `Proxied` (Awan Jingga) jika menggunakan Cloudflare untuk SSL/HTTPS gratis otomatis!

3. **Verifikasi Kepemilikan (TXT Record):**
   - **Type:** `TXT`
   - **Name:** `_sfrhost-verify`
   - **Content:** `sfrhost-verify=<token-dari-aplikasi>`

4. **Nyalakan Server & Tunnel di HP:**
   - Tekan **START SERVER** dan **START TUNNEL** di aplikasi SFR Host.
   - Status di Dashboard akan berubah menjadi **PUBLIC ONLINE**.
   - Buka `https://tokoku.com` dari HP lain, laptop, atau jaringan seluler mana pun tanpa satu Wi-Fi!
