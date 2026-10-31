package com.example.server

import com.example.server.php.PhpExecutionEngine
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

    // PHP Engine initialized on web root
    private val phpEngine: PhpExecutionEngine by lazy {
        val docRoot = resolveDocumentRoot()
        PhpExecutionEngine(documentRoot = docRoot)
    }

    private fun resolveDocumentRoot(): File {
        val publicHtml = File(webRootDir, "public_html")
        return if (publicHtml.isDirectory) publicHtml else webRootDir
    }

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
        val requestHeaders = mutableMapOf<String, String>()
        val docRoot = resolveDocumentRoot()

        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
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
            var contentLength = 0
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx != -1) {
                    val k = headerLine.substring(0, colonIdx).trim()
                    val v = headerLine.substring(colonIdx + 1).trim()
                    requestHeaders[k] = v
                    if (k.equals("Content-Length", ignoreCase = true)) {
                        contentLength = v.toIntOrNull() ?: 0
                    }
                    if (k.equals("X-Forwarded-For", ignoreCase = true)) {
                        val forwarded = v.split(",")[0].trim()
                        if (forwarded.isNotEmpty()) clientIp = forwarded
                    }
                }
                headerLine = reader.readLine()
            }

            // Read POST body if present (up to 10MB limit)
            var postBody = ""
            if (requestMethod == "POST" && contentLength > 0) {
                val maxAllowed = 10 * 1024 * 1024 // 10MB
                val toRead = contentLength.coerceAtMost(maxAllowed)
                val bodyChars = CharArray(toRead)
                var totalRead = 0
                while (totalRead < toRead) {
                    val read = reader.read(bodyChars, totalRead, toRead - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                postBody = String(bodyChars, 0, totalRead)
            }

            // Supported methods
            if (requestMethod != "GET" && requestMethod != "POST" && requestMethod != "HEAD" && requestMethod != "OPTIONS") {
                statusCode = 405
                bytesSent = sendBrandedError(out, 405, requestMethod, requestPath, requestHeaders)
                out.flush()
                return
            }

            if (requestMethod == "OPTIONS") {
                statusCode = 200
                bytesSent = sendOptionsResponse(out)
                out.flush()
                return
            }

            // Parse URL path and query string
            val rawPath = requestPath.substringBefore('?')
            val queryString = if (requestPath.contains('?')) requestPath.substringAfter('?') else ""
            var cleanPath = try {
                URLDecoder.decode(rawPath, "UTF-8")
            } catch (e: Exception) {
                rawPath
            }

            // Parse .htaccess in docRoot
            val htaccessFile = File(docRoot, ".htaccess")
            val htaccessConfig = if (htaccessFile.exists()) HtaccessManager.parse(htaccessFile) else HtaccessConfig()

            // Apply rewrite rules if any
            for ((regex, replacement) in htaccessConfig.rewriteRules) {
                val pathWithoutLeadingSlash = cleanPath.removePrefix("/")
                if (regex.containsMatchIn(pathWithoutLeadingSlash)) {
                    cleanPath = "/" + pathWithoutLeadingSlash.replace(regex, replacement)
                    break
                }
            }

            // Block access to sensitive and secret files
            if (isSensitiveFile(cleanPath) || htaccessConfig.denyAll) {
                statusCode = 403
                bytesSent = sendBrandedError(out, 403, requestMethod, cleanPath, requestHeaders, customDoc = htaccessConfig.customErrorDocuments[403])
                out.flush()
                return
            }

            val targetFile = resolveFile(cleanPath, docRoot)
            if (targetFile == null) {
                // Directory traversal blocked
                statusCode = 403
                bytesSent = sendBrandedError(out, 403, requestMethod, cleanPath, requestHeaders, "Akses ditolak: Directory traversal terdeteksi.")
                out.flush()
                return
            }

            if (!targetFile.exists()) {
                statusCode = 404
                bytesSent = sendBrandedError(out, 404, requestMethod, cleanPath, requestHeaders, customDoc = htaccessConfig.customErrorDocuments[404])
            } else if (targetFile.isDirectory) {
                // Directory resolution with priority:
                // 1) index.php
                // 2) index.html
                // 3) index.htm
                val priorityList = htaccessConfig.directoryIndex.ifEmpty {
                    listOf("index.php", "index.html", "index.htm")
                }

                var resolvedIndexFile: File? = null
                for (name in priorityList) {
                    val candidate = File(targetFile, name)
                    if (candidate.exists() && candidate.isFile) {
                        resolvedIndexFile = candidate
                        break
                    }
                }

                if (resolvedIndexFile != null) {
                    if (isPhpScript(resolvedIndexFile)) {
                        // Execute PHP!
                        val result = phpEngine.execute(
                            phpFile = resolvedIndexFile,
                            method = requestMethod,
                            queryString = queryString,
                            postBody = postBody,
                            headers = requestHeaders,
                            clientIp = clientIp,
                            serverPort = port
                        )
                        statusCode = result.statusCode
                        bytesSent = sendPhpResponse(out, result, requestMethod)
                    } else {
                        statusCode = 200
                        bytesSent = sendFileResponse(out, resolvedIndexFile, requestMethod)
                    }
                } else {
                    // No index file found -> Directory Listing
                    if (htaccessConfig.allowDirectoryListing) {
                        statusCode = 200
                        val hostLabel = requestHeaders["Host"] ?: "127.0.0.1:$port"
                        val listingHtml = DefaultWebPages.generateDirectoryListing(
                            directory = targetFile,
                            webRoot = docRoot,
                            currentPath = cleanPath,
                            domainOrHost = hostLabel
                        )
                        bytesSent = sendHtmlResponse(out, 200, "OK", listingHtml, requestMethod)
                    } else {
                        statusCode = 403
                        bytesSent = sendBrandedError(out, 403, requestMethod, cleanPath, requestHeaders, "Directory listing dinonaktifkan oleh administrator.")
                    }
                }
            } else {
                // Target is a file
                if (isPhpScript(targetFile)) {
                    // NEVER return raw PHP script as file download! Execute it!
                    val result = phpEngine.execute(
                        phpFile = targetFile,
                        method = requestMethod,
                        queryString = queryString,
                        postBody = postBody,
                        headers = requestHeaders,
                        clientIp = clientIp,
                        serverPort = port
                    )
                    statusCode = result.statusCode
                    bytesSent = sendPhpResponse(out, result, requestMethod)
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

    private fun isPhpScript(file: File): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext == "php" || ext == "phtml" || ext == "php8"
    }

    private fun isSensitiveFile(path: String): Boolean {
        val lower = path.lowercase(Locale.ROOT)
        return lower.endsWith("/.env") || lower == "/.env" ||
               lower.contains("/.git") ||
               lower.endsWith("/.htaccess") || lower == "/.htaccess" ||
               lower.endsWith("/.htpasswd") ||
               lower.endsWith(".sqlite") || lower.endsWith(".sqlite3") ||
               lower.endsWith(".db") || lower.endsWith(".sql")
    }

    private fun resolveFile(path: String, docRoot: File): File? {
        val normalized = if (path.startsWith("/")) path.substring(1) else path
        val candidate = File(docRoot, normalized)
        val canonicalRoot = docRoot.canonicalPath
        val canonicalCandidate = candidate.canonicalPath

        if (!canonicalCandidate.startsWith(canonicalRoot)) {
            return null // Traversal attempt
        }
        return candidate
    }

    private fun sendPhpResponse(out: BufferedOutputStream, result: com.example.server.php.PhpExecutionResult, method: String): Long {
        val bodyBytes = result.body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ${result.statusCode} ${getStatusText(result.statusCode)}\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native; PHP 8.2)\r\n")
            if (!result.headers.containsKey("Content-Type")) {
                append("Content-Type: text/html; charset=UTF-8\r\n")
            }
            for ((k, v) in result.headers) {
                append("$k: $v\r\n")
            }
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }

        out.write(headers.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            out.write(bodyBytes)
        }
        return bodyBytes.size.toLong()
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

    private fun sendHtmlResponse(out: BufferedOutputStream, statusCode: Int, statusText: String, html: String, method: String): Long {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Date: ${getHttpDate()}\r\n")
            append("Server: SFR-Host-Engine/1.0 (Android Native)\r\n")
            append("Content-Type: text/html; charset=UTF-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))
        if (method != "HEAD") {
            out.write(bytes)
        }
        return bytes.size.toLong()
    }

    private fun sendBrandedError(
        out: BufferedOutputStream,
        statusCode: Int,
        method: String,
        requestPath: String,
        headers: Map<String, String>,
        customMessage: String? = null,
        customDoc: String? = null
    ): Long {
        val docRoot = resolveDocumentRoot()
        // If custom document configured in .htaccess e.g. ErrorDocument 404 /404.html
        if (customDoc != null) {
            val customFile = resolveFile(customDoc, docRoot)
            if (customFile != null && customFile.exists() && customFile.isFile) {
                return sendFileResponse(out, customFile, method)
            }
        }

        val html = DefaultWebPages.getErrorPage(statusCode, customMessage, requestPath)
        return sendHtmlResponse(out, statusCode, getStatusText(statusCode), html, method)
    }

    private fun sendOptionsResponse(out: BufferedOutputStream): Long {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Allow: GET, POST, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, HEAD, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With\r\n")
            append("Content-Length: 0\r\n")
            append("Connection: close\r\n\r\n")
        }
        val bytes = headers.toByteArray(Charsets.UTF_8)
        out.write(bytes)
        return 0L
    }

    private fun getStatusText(code: Int): String {
        return when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            504 -> "Gateway Timeout"
            else -> "Error"
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "html", "htm" -> "text/html; charset=UTF-8"
            "css" -> "text/css; charset=UTF-8"
            "js", "mjs" -> "application/javascript; charset=UTF-8"
            "json" -> "application/json; charset=UTF-8"
            "xml" -> "application/xml; charset=UTF-8"
            "txt", "log" -> "text/plain; charset=UTF-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun getHttpDate(): String {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        return format.format(Date())
    }
}
