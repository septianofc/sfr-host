package com.example.server

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DefaultWebPages {

    const val LOGO_SVG = """<svg width="48" height="48" viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect width="100" height="100" rx="22" fill="#0B132B"/>
        <path d="M25 35C25 29.4772 29.4772 25 35 25H65C70.5228 25 75 29.4772 75 35V45H25V35Z" fill="#42A5F5"/>
        <path d="M25 50H75V60H25V50Z" fill="#1E88E5"/>
        <path d="M25 65H75V70C75 75.5228 70.5228 80 65 80H35C29.4772 80 25 75.5228 25 70V65Z" fill="#1565C0"/>
        <circle cx="33" cy="35" r="3" fill="#E3F2FD"/>
        <circle cx="43" cy="35" r="3" fill="#E3F2FD"/>
        <circle cx="33" cy="55" r="3" fill="#E3F2FD"/>
        <circle cx="33" cy="72" r="3" fill="#E3F2FD"/>
        <path d="M68 62C68 59 70 56 73 56C76 56 78 58 78 60C80 60 82 62 82 64C82 67 79 69 77 69H68V62Z" fill="#FF9800"/>
    </svg>"""

    fun getErrorPage(statusCode: Int, customMessage: String? = null, requestPath: String = "/"): String {
        val (titleId, titleEn, descId) = getErrorDescriptions(statusCode)
        val finalMessage = customMessage ?: descId

        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$statusCode $titleEn - SFR Host</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                    body {
                        background: linear-gradient(135deg, #0b132b 0%, #1c2541 100%);
                        color: #f1f5f9;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 24px;
                    }
                    .error-container {
                        max-width: 520px;
                        width: 100%;
                        background: rgba(30, 41, 59, 0.7);
                        backdrop-filter: blur(16px);
                        -webkit-backdrop-filter: blur(16px);
                        border: 1px solid rgba(255, 255, 255, 0.1);
                        border-radius: 24px;
                        padding: 40px 32px;
                        text-align: center;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
                    }
                    .logo-wrap {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        margin-bottom: 20px;
                    }
                    .status-badge {
                        display: inline-block;
                        padding: 6px 16px;
                        background: ${getBadgeColor(statusCode)};
                        color: #ffffff;
                        font-weight: 800;
                        font-size: 14px;
                        border-radius: 999px;
                        letter-spacing: 1.5px;
                        margin-bottom: 16px;
                        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
                    }
                    h1 {
                        font-size: 24px;
                        font-weight: 700;
                        color: #f8fafc;
                        margin-bottom: 8px;
                    }
                    .subheading {
                        font-size: 14px;
                        color: #94a3b8;
                        margin-bottom: 20px;
                        font-weight: 500;
                    }
                    .message-card {
                        background: rgba(15, 23, 42, 0.6);
                        border-radius: 14px;
                        padding: 16px 20px;
                        margin-bottom: 28px;
                        text-align: left;
                        border-left: 4px solid #42A5F5;
                    }
                    .message-card p {
                        font-size: 14px;
                        line-height: 1.6;
                        color: #cbd5e1;
                    }
                    .path-indicator {
                        font-family: monospace;
                        font-size: 12px;
                        color: #38bdf8;
                        word-break: break-all;
                        margin-top: 6px;
                        display: block;
                    }
                    .action-btn {
                        display: inline-block;
                        padding: 12px 28px;
                        background: #42A5F5;
                        color: #ffffff;
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 15px;
                        border-radius: 12px;
                        transition: background 0.2s, transform 0.1s;
                        box-shadow: 0 4px 14px rgba(66, 165, 245, 0.35);
                    }
                    .action-btn:hover {
                        background: #1e88e5;
                        transform: translateY(-1px);
                    }
                    .footer {
                        margin-top: 36px;
                        padding-top: 20px;
                        border-top: 1px solid rgba(255, 255, 255, 0.08);
                        font-size: 12px;
                        color: #64748b;
                        line-height: 1.8;
                    }
                    .footer a {
                        color: #42A5F5;
                        text-decoration: none;
                        font-weight: 600;
                    }
                    .footer a:hover {
                        text-decoration: underline;
                    }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <div class="logo-wrap">$LOGO_SVG</div>
                    <div><span class="status-badge">$statusCode</span></div>
                    <h1>$titleId</h1>
                    <div class="subheading">$titleEn</div>
                    <div class="message-card">
                        <p>$finalMessage</p>
                        <span class="path-indicator">Path: $requestPath</span>
                    </div>
                    <div>
                        <a href="/" class="action-btn">Kembali ke Beranda</a>
                    </div>
                    <div class="footer">
                        Host Server HP Android • <strong>SFR Host</strong><br>
                        Dikelola oleh <strong>Septian Fathur Rohman</strong><br>
                        <a href="https://github.com/septianofc/sfr-host" target="_blank">Repository Resmi SFR Host</a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun getDefaultWelcomePage(websiteName: String, docRoot: String): String {
        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Selamat Datang di $websiteName - SFR Host</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    body {
                        background: linear-gradient(135deg, #0b132b 0%, #1c2541 100%);
                        color: #f1f5f9;
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 24px;
                    }
                    .card {
                        max-width: 560px;
                        width: 100%;
                        background: rgba(30, 41, 59, 0.85);
                        backdrop-filter: blur(16px);
                        border: 1px solid rgba(255, 255, 255, 0.12);
                        border-radius: 24px;
                        padding: 40px 32px;
                        text-align: center;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
                    }
                    .badge {
                        display: inline-block;
                        padding: 6px 16px;
                        background: #2E7D32;
                        color: #ffffff;
                        font-weight: 800;
                        font-size: 13px;
                        border-radius: 999px;
                        margin-bottom: 16px;
                        letter-spacing: 1px;
                    }
                    h1 { font-size: 26px; color: #f8fafc; margin-bottom: 8px; }
                    .subtitle { font-size: 14px; color: #94a3b8; margin-bottom: 24px; }
                    .info-box {
                        background: rgba(15, 23, 42, 0.7);
                        border-radius: 14px;
                        padding: 16px 20px;
                        text-align: left;
                        border-left: 4px solid #42A5F5;
                        margin-bottom: 24px;
                        font-size: 13px;
                        line-height: 1.8;
                    }
                    .info-box code { color: #38bdf8; font-family: monospace; }
                    .footer { font-size: 12px; color: #64748b; margin-top: 24px; }
                    .footer a { color: #42A5F5; text-decoration: none; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div style="margin-bottom: 16px;">$LOGO_SVG</div>
                    <span class="badge">SERVER AKTIF (ONLINE)</span>
                    <h1>Website Berhasil Dibuat!</h1>
                    <p class="subtitle">Hosting berjalan normal di HP Android menggunakan <strong>SFR Host</strong></p>
                    <div class="info-box">
                        <p><strong>Website:</strong> $websiteName</p>
                        <p><strong>Document Root:</strong> <code>$docRoot</code></p>
                        <p><strong>Langkah Selanjutnya:</strong></p>
                        <p>Silakan buka tab <strong>File Manager</strong> di aplikasi SFR Host, lalu unggah atau buat berkas <code>index.php</code> atau <code>index.html</code> Anda di folder <code>public_html</code>.</p>
                    </div>
                    <div class="footer">
                        Powered by <strong>SFR Host</strong> (Android Self-Hosting)<br>
                        <a href="https://github.com/septianofc/sfr-host" target="_blank">Repository GitHub SFR Host</a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getBadgeColor(statusCode: Int): String {
        return when (statusCode) {
            400, 401, 403, 405, 408, 413, 414, 415, 422, 429, 431 -> "#f59e0b"
            404 -> "#ef4444"
            500, 501, 502, 503, 504 -> "#dc2626"
            else -> "#42a5f5"
        }
    }

    private fun getErrorDescriptions(code: Int): Triple<String, String, String> {
        return when (code) {
            400 -> Triple("Permintaan Tidak Valid", "Bad Request", "Server tidak dapat memproses permintaan ini karena format sintaks tidak valid.")
            401 -> Triple("Otentikasi Diperlukan", "Unauthorized", "Akses ke halaman ini membutuhkan otentikasi atau otorisasi yang sah.")
            403 -> Triple("Akses Ditolak", "Forbidden", "Anda tidak memiliki izin untuk mengakses resource atau direktori ini.")
            404 -> Triple("Halaman Tidak Ditemukan", "Not Found", "Berkas atau halaman yang Anda minta tidak ditemukan di document root server ini.")
            405 -> Triple("Metode Tidak Diizinkan", "Method Not Allowed", "Metode HTTP yang diminta tidak diizinkan untuk resource ini.")
            408 -> Triple("Waktu Permintaan Habis", "Request Timeout", "Koneksi ke server HP melewati batas waktu tunggu yang ditentukan.")
            413 -> Triple("Ukuran Muatan Terlalu Besar", "Payload Too Large", "Ukuran file atau data yang diunggah melebihi kapasitas batas server SFR Host.")
            414 -> Triple("URI Terlalu Panjang", "URI Too Long", "Alamat URI yang diminta melebihi batas panjang yang dapat diproses server.")
            415 -> Triple("Tipe Media Tidak Didukung", "Unsupported Media Type", "Format data muatan permintaan tidak didukung oleh endpoint server.")
            422 -> Triple("Entitas Tidak Dapat Diproses", "Unprocessable Entity", "Sintaks permintaan valid namun instruksi tidak dapat diproses oleh server.")
            429 -> Triple("Terlalu Banyak Permintaan", "Too Many Requests", "Laju permintaan telah melebihi batas rate limit server Android.")
            431 -> Triple("Field Header Terlalu Besar", "Request Header Fields Too Large", "Ukuran header HTTP yang dikirim melebihi batas alokasi buffer server.")
            500 -> Triple("Kesalahan Server Internal", "Internal Server Error", "Terjadi kesalahan saat memproses skrip website atau skrip PHP di server HP.")
            501 -> Triple("Belum Diimplementasikan", "Not Implemented", "Server HP tidak mendukung fungsionalitas yang diperlukan untuk memenuhi permintaan.")
            502 -> Triple("Gateway Bermasalah", "Bad Gateway", "Server menerima respons yang tidak valid saat menghubungkan tunnel gateway.")
            503 -> Triple("Layanan Tidak Tersedia", "Service Unavailable", "Web server HP sedang dalam kondisi offline atau kelebihan beban.")
            504 -> Triple("Waktu Gateway Habis", "Gateway Timeout", "Server gateway tidak menerima respons tepat waktu dari server lokal HP.")
            else -> Triple("Kesalahan HTTP", "HTTP Error", "Terjadi kesalahan HTTP status $code.")
        }
    }

    fun generateDirectoryListing(
        directory: File,
        webRoot: File,
        currentPath: String,
        domainOrHost: String
    ): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        // Exclude secret and system files
        val files = directory.listFiles()?.filter { file ->
            val name = file.name.lowercase(Locale.ROOT)
            !name.startsWith(".") &&
            !name.endsWith(".env") &&
            !name.endsWith(".db") &&
            !name.endsWith(".sqlite") &&
            !name.endsWith(".sql") &&
            !name.endsWith(".htpasswd") &&
            name != "config" &&
            name != "logs"
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })) ?: emptyList()

        val normalizedPath = if (currentPath.startsWith("/")) currentPath else "/$currentPath"
        val parentPath = if (normalizedPath == "/" || normalizedPath.isEmpty()) null else {
            val p = normalizedPath.substringBeforeLast('/', "")
            if (p.isEmpty()) "/" else p
        }

        val rows = StringBuilder()
        if (parentPath != null) {
            rows.append("""
                <tr>
                    <td><a href="$parentPath" class="dir-link">📁 .. [Kembali ke Direktori Induk]</a></td>
                    <td>-</td>
                    <td>-</td>
                    <td><a href="$parentPath" class="view-btn">Buka</a></td>
                </tr>
            """.trimIndent())
        }

        for (file in files) {
            val isDir = file.isDirectory
            val icon = if (isDir) "📁" else getFileIcon(file.extension)
            val name = file.name + (if (isDir) "/" else "")
            val link = (if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/") + file.name + (if (isDir) "/" else "")
            val sizeStr = if (isDir) "-" else formatBytes(file.length())
            val dateStr = dateFormat.format(Date(file.lastModified()))
            val actionHtml = if (isDir) {
                """<a href="$link" class="view-btn">Buka</a>"""
            } else {
                """<a href="$link" download="${file.name}" class="dl-btn">Unduh</a>"""
            }

            rows.append("""
                <tr>
                    <td><a href="$link" class="${if (isDir) "dir-link" else "file-link"}">$icon $name</a></td>
                    <td class="meta-col">$sizeStr</td>
                    <td class="meta-col">$dateStr</td>
                    <td>$actionHtml</td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Index of $normalizedPath - SFR Host</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace; }
                    body { background: #0f172a; color: #f8fafc; padding: 24px; min-height: 100vh; }
                    .wrapper { max-width: 960px; margin: 0 auto; background: #1e293b; border-radius: 16px; border: 1px solid #334155; padding: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                    .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #334155; padding-bottom: 16px; margin-bottom: 20px; }
                    .header-brand { display: flex; align-items: center; gap: 12px; }
                    .header-brand h1 { font-size: 20px; color: #42A5F5; font-family: sans-serif; font-weight: 700; }
                    .badge { font-size: 11px; background: #0284c7; color: white; padding: 3px 8px; border-radius: 6px; font-family: sans-serif; }
                    .breadcrumb { font-size: 14px; color: #94a3b8; margin-bottom: 16px; word-break: break-all; }
                    .breadcrumb strong { color: #f1f5f9; }
                    table { width: 100%; border-collapse: collapse; font-size: 13px; }
                    th, td { text-align: left; padding: 10px 12px; border-bottom: 1px solid #334155; }
                    th { background: #0f172a; color: #94a3b8; font-weight: 600; text-transform: uppercase; font-size: 11px; letter-spacing: 0.5px; }
                    tr:hover td { background: rgba(255,255,255,0.02); }
                    .dir-link { color: #38bdf8; text-decoration: none; font-weight: 600; }
                    .file-link { color: #e2e8f0; text-decoration: none; }
                    .dir-link:hover, .file-link:hover { text-decoration: underline; }
                    .meta-col { color: #94a3b8; white-space: nowrap; }
                    .view-btn { padding: 4px 10px; background: #0284c7; color: white; text-decoration: none; border-radius: 6px; font-size: 11px; font-family: sans-serif; }
                    .dl-btn { padding: 4px 10px; background: #334155; color: #38bdf8; text-decoration: none; border-radius: 6px; font-size: 11px; font-family: sans-serif; border: 1px solid #475569; }
                    .view-btn:hover { background: #0369a1; }
                    .dl-btn:hover { background: #475569; color: white; }
                    .footer { text-align: center; margin-top: 32px; padding-top: 16px; border-top: 1px solid #334155; font-size: 12px; color: #64748b; font-family: sans-serif; }
                    .footer a { color: #42A5F5; text-decoration: none; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="wrapper">
                    <div class="header">
                        <div class="header-brand">
                            $LOGO_SVG
                            <div>
                                <h1>SFR Host File Index</h1>
                                <span class="badge">Directory Listing Aktif</span>
                            </div>
                        </div>
                        <div style="font-size: 12px; color: #94a3b8; font-family: sans-serif;">
                            Host: <strong>$domainOrHost</strong>
                        </div>
                    </div>
                    <div class="breadcrumb">
                        Lokasi: <strong>$normalizedPath</strong> (${files.size} item)
                    </div>
                    <table>
                        <thead>
                            <tr>
                                <th>Nama File / Folder</th>
                                <th>Ukuran</th>
                                <th>Terakhir Diubah</th>
                                <th>Aksi</th>
                            </tr>
                        </thead>
                        <tbody>
                            $rows
                        </tbody>
                    </table>
                    <div class="footer">
                        Web Server HP Android • <strong>SFR Host</strong> • Dikembangkan oleh Septian Fathur Rohman<br>
                        <a href="https://github.com/septianofc/sfr-host" target="_blank">Repository Resmi SFR Host</a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getFileIcon(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "php", "phtml" -> "🐘"
            "html", "htm" -> "🌐"
            "css" -> "🎨"
            "js", "mjs", "ts" -> "📜"
            "json", "xml" -> "📋"
            "png", "jpg", "jpeg", "webp", "gif", "svg" -> "🖼️"
            "mp4", "webm" -> "🎬"
            "mp3", "wav", "ogg" -> "🎵"
            "zip", "tar", "gz" -> "📦"
            "pdf", "txt", "md" -> "📄"
            else -> "📄"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
