package com.example.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class HttpRequestLog(
    val id: Long,
    val timestamp: Long,
    val clientIp: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val responseTimeMs: Long,
    val bytesSent: Long
)

class SfrHttpServer(
    val port: Int,
    private val webRootDir: File
) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val isRunningFlag = AtomicBoolean(false)
    private val requestCounter = AtomicLong(0)
    private val logIdCounter = AtomicLong(0)
    private val startedAt = AtomicLong(0)

    private val _requestLogs = MutableSharedFlow<HttpRequestLog>(replay = 50, extraBufferCapacity = 100)
    val requestLogs: SharedFlow<HttpRequestLog> = _requestLogs.asSharedFlow()

    val isRunning: Boolean get() = isRunningFlag.get()
    val totalRequests: Long get() = requestCounter.get()
    val uptimeMillis: Long get() = if (isRunningFlag.get()) System.currentTimeMillis() - startedAt.get() else 0L

    fun start(): Boolean {
        if (isRunningFlag.get()) return true
        return try {
            serverSocket = ServerSocket(port)
            isRunningFlag.set(true)
            startedAt.set(System.currentTimeMillis())

            executor.execute {
                while (isRunningFlag.get()) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        executor.execute {
                            handleClientSocket(socket)
                        }
                    } catch (e: Exception) {
                        if (!isRunningFlag.get()) break
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunningFlag.set(false)
            false
        }
    }

    fun stop() {
        isRunningFlag.set(false)
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    private fun handleClientSocket(socket: Socket) {
        val startTime = System.currentTimeMillis()
        var statusCode = 200
        var bytesSent = 0L
        var clientIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
        var requestMethod = "GET"
        var requestPath = "/"

        try {
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())

            val requestLine = reader.readLine()
            if (requestLine.isNullOrBlank()) {
                socket.close()
                return
            }

            val tokens = requestLine.split(" ")
            if (tokens.size >= 2) {
                requestMethod = tokens[0].uppercase(Locale.ROOT)
                requestPath = tokens[1]
            }

            // Consume headers
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                if (headerLine.startsWith("X-Forwarded-For:", ignoreCase = true)) {
                    val forwarded = headerLine.substringAfter(":").trim()
                    if (forwarded.isNotEmpty()) clientIp = forwarded.split(",")[0].trim()
                }
                headerLine = reader.readLine()
            }

            if (requestMethod != "GET" && requestMethod != "HEAD") {
                statusCode = 405
                bytesSent = sendErrorResponse(out, 405, "Method Not Allowed", "Hanya HTTP GET dan HEAD yang didukung.", requestMethod)
            } else {
                val cleanPath = URLDecoder.decode(requestPath.substringBefore('?'), "UTF-8")
                val targetFile = resolveFile(cleanPath)

                if (targetFile == null) {
                    // Forbidden directory traversal
                    statusCode = 403
                    bytesSent = sendErrorResponse(out, 403, "403 Forbidden", "Akses ditolak: Directory traversal terdeteksi.", requestMethod)
                } else if (!targetFile.exists()) {
                    statusCode = 404
                    bytesSent = sendCustom404(out, cleanPath, requestMethod)
                } else if (targetFile.isDirectory) {
                    val indexFile = File(targetFile, "index.html").takeIf { it.exists() }
                        ?: File(targetFile, "index.htm").takeIf { it.exists() }

                    if (indexFile != null) {
                        statusCode = 200
                        bytesSent = sendFileResponse(out, indexFile, requestMethod)
                    } else {
                        // Directory listing
                        statusCode = 200
                        bytesSent = sendDirectoryListing(out, targetFile, cleanPath, requestMethod)
                    }
                } else {
                    statusCode = 200
                    bytesSent = sendFileResponse(out, targetFile, requestMethod)
                }
            }
            out.flush()
        } catch (e: Exception) {
            statusCode = 500
        } finally {
            val responseTime = System.currentTimeMillis() - startTime
            val count = requestCounter.incrementAndGet()
            val log = HttpRequestLog(
                id = logIdCounter.incrementAndGet(),
                timestamp = System.currentTimeMillis(),
                clientIp = clientIp,
                method = requestMethod,
                path = requestPath,
                statusCode = statusCode,
                responseTimeMs = responseTime,
                bytesSent = bytesSent
            )
            _requestLogs.tryEmit(log)

            try {
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun resolveFile(path: String): File? {
        val normalized = if (path.startsWith("/")) path.substring(1) else path

        // Check public_html/ subdirectory first if present
        val publicHtmlDir = File(webRootDir, "public_html")
        if (publicHtmlDir.isDirectory) {
            val candidateInPublicHtml = File(publicHtmlDir, normalized)
            val canonicalPublicHtml = publicHtmlDir.canonicalPath
            val canonicalCandidate = candidateInPublicHtml.canonicalPath
            if (canonicalCandidate.startsWith(canonicalPublicHtml) && candidateInPublicHtml.exists()) {
                return candidateInPublicHtml
            }
        }

        val candidate = File(webRootDir, normalized)
        val canonicalRoot = webRootDir.canonicalPath
        val canonicalCandidate = candidate.canonicalPath

        // Directory traversal protection:
        if (!canonicalCandidate.startsWith(canonicalRoot)) {
            return null
        }
        return candidate
    }

    private fun sendFileResponse(out: BufferedOutputStream, file: File, method: String): Long {
        val mimeType = getMimeType(file.name)
        val length = file.length()
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native)\r\n")
            append("Content-Type: $mimeType\r\n")
            append("Content-Length: $length\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))

        if (method != "HEAD") {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }
        }
        return length
    }

    private fun sendCustom404(out: BufferedOutputStream, path: String, method: String): Long {
        val html = """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>404 Halaman Tidak Ditemukan - SFR Host</title>
                <style>
                    body {
                        background: #101418;
                        color: #e1e2e5;
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        height: 100vh;
                        margin: 0;
                        padding: 20px;
                        box-sizing: border-box;
                    }
                    .box {
                        background: #181c20;
                        border: 1px solid #242a30;
                        border-radius: 16px;
                        padding: 32px;
                        max-width: 420px;
                        text-align: center;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                    }
                    h1 { font-size: 64px; color: #FF9800; margin: 0 0 8px 0; }
                    h2 { font-size: 20px; margin: 0 0 16px 0; color: #42A5F5; }
                    p { color: #8c9199; font-size: 14px; line-height: 1.6; margin-bottom: 24px; word-break: break-all; }
                    a {
                        background: #42A5F5;
                        color: #002244;
                        text-decoration: none;
                        padding: 10px 24px;
                        border-radius: 20px;
                        font-weight: bold;
                        font-size: 14px;
                        display: inline-block;
                    }
                    .footer { margin-top: 24px; font-size: 12px; color: #555; }
                </style>
            </head>
            <body>
                <div class="box">
                    <h1>404</h1>
                    <h2>Halaman Tidak Ditemukan</h2>
                    <p>File atau path <code>${escapeHtml(path)}</code> tidak tersedia di server web ini.</p>
                    <a href="/">Kembali ke Beranda</a>
                    <div class="footer">SFR Host Embedded Server • Powered by Android</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 404 Not Found\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native)\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            out.write(bytes)
        }
        return bytes.size.toLong()
    }

    private fun sendErrorResponse(out: BufferedOutputStream, code: Int, title: String, message: String, method: String): Long {
        val html = "<!DOCTYPE html><html><head><title>$code $title</title></head><body><h1>$code $title</h1><p>$message</p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $code $title\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native)\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            out.write(bytes)
        }
        return bytes.size.toLong()
    }

    private fun sendDirectoryListing(out: BufferedOutputStream, dir: File, requestPath: String, method: String): Long {
        val files = dir.listFiles() ?: emptyArray()
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Index of ${escapeHtml(requestPath)}</title>")
            append("<style>body{font-family:sans-serif;padding:24px;background:#f5f7fa;}ul{list-style:none;padding:0;}li{padding:8px 0;border-bottom:1px solid #ddd;}a{text-decoration:none;color:#1e88e5;}</style></head>")
            append("<body><h2>Index of ${escapeHtml(requestPath)}</h2><ul>")
            append("<li><a href='../'>📁 .. (Parent Directory)</a></li>")
            files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
                val icon = if (f.isDirectory) "📁 " else "📄 "
                val name = f.name + (if (f.isDirectory) "/" else "")
                append("<li><a href='${escapeHtml(name)}'>$icon${escapeHtml(name)}</a> (${if (f.isDirectory) "-" else "${f.length()} bytes"})</li>")
            }
            append("</ul><hr><p><small>SFR Host Embedded HTTP Server</small></p></body></html>")
        }
        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native)\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            out.write(bytes)
        }
        return bytes.size.toLong()
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "txt" -> "text/plain; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun getHttpDate(): String {
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT")
        return fmt.format(Date())
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
