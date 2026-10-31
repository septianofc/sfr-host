package com.example.tunnel

import android.content.Context
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CloudflareTunnelRunner manages the real native `cloudflared` process on Android.
 * It strictly adheres to:
 * 1. Validating token structure (Base64 JSON containing a, t, s).
 * 2. Ensuring the binary exists in app storage and is executable.
 * 3. Starting the process via ProcessBuilder and reading actual stdout/stderr.
 * 4. Only setting CONNECTED when Cloudflare registers the edge connection.
 * 5. Masking sensitive tokens in logs.
 */
class CloudflareTunnelRunner(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _tunnelState = MutableStateFlow(TunnelState.DISCONNECTED)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _logs = MutableSharedFlow<TunnelLog>(replay = 100, extraBufferCapacity = 200)
    val logs: SharedFlow<TunnelLog> = _logs.asSharedFlow()

    private val _connectedSince = MutableStateFlow<Long?>(null)
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    private var tunnelProcess: Process? = null
    private var processJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    val binDir: File
        get() = File(context.filesDir, "bin").apply { if (!exists()) mkdirs() }

    val cloudflaredBinary: File
        get() = File(binDir, "cloudflared")

    val detectedAbi: String
        get() {
            val supported = Build.SUPPORTED_ABIS
            return when {
                supported.contains("arm64-v8a") -> "arm64-v8a"
                supported.contains("armeabi-v7a") -> "armeabi-v7a"
                supported.contains("x86_64") -> "x86_64"
                supported.contains("x86") -> "x86"
                else -> supported.firstOrNull() ?: "unknown"
            }
        }

    val isBinaryInstalled: Boolean
        get() = cloudflaredBinary.exists() && cloudflaredBinary.canExecute()

    fun validateToken(token: String): Pair<Boolean, String> {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            return Pair(false, "Token Cloudflare Tunnel tidak boleh kosong.")
        }
        return try {
            val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
            val jsonStr = String(decodedBytes, Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val hasAccount = json.has("a")
            val hasTunnel = json.has("t")
            val hasSecret = json.has("s")

            if (hasAccount && hasTunnel && hasSecret) {
                Pair(true, "Format token valid (Remotely-managed tunnel).")
            } else {
                Pair(false, "Token bukan format Cloudflare Tunnel yang valid (hilang field a/t/s).")
            }
        } catch (e: Exception) {
            Pair(false, "Token tidak valid: Bukan Base64 terenkripsi yang sesuai.")
        }
    }

    fun startTunnel(
        localPort: Int,
        cloudflareToken: String
    ) {
        if (isRunning.get()) {
            log("Tunnel sudah berjalan atau sedang dalam proses koneksi.", isError = false)
            return
        }

        // Validate token
        val (isValidToken, tokenError) = validateToken(cloudflareToken)
        if (!isValidToken) {
            _tunnelState.value = TunnelState.ERROR
            log("Error Token: $tokenError", isError = true)
            log("Salin Token Resmi dari Cloudflare Zero Trust Dashboard -> Access -> Tunnels.", isError = true)
            return
        }

        // Check binary
        if (!cloudflaredBinary.exists()) {
            _tunnelState.value = TunnelState.ERROR
            log("Binary 'cloudflared' belum terpasang di ${cloudflaredBinary.absolutePath}", isError = true)
            log("Silakan klik tombol 'Pasang / Unduh Binary' untuk mengunduh cloudflared yang sesuai dengan ABI perangkat Anda ($detectedAbi).", isError = true)
            return
        }

        if (!cloudflaredBinary.canExecute()) {
            cloudflaredBinary.setExecutable(true, false)
        }

        isRunning.set(true)
        _tunnelState.value = TunnelState.CONNECTING

        val maskedToken = cloudflareToken.take(6) + "..." + cloudflareToken.takeLast(6)
        log("Memulai proses cloudflared (ABI: $detectedAbi, Token: $maskedToken)...")

        processJob = scope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(
                    cloudflaredBinary.absolutePath,
                    "tunnel",
                    "--no-autoupdate",
                    "--loglevel", "info",
                    "run",
                    "--token", cloudflareToken.trim()
                )

                // Environment variables
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                pb.environment()["HOME"] = context.filesDir.absolutePath
                pb.directory(context.filesDir)
                pb.redirectErrorStream(true) // Merge stderr into stdout

                val process = pb.start()
                tunnelProcess = process

                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                var line: String?

                while (isRunning.get() && isActive) {
                    line = reader.readLine() ?: break
                    val cleanLine = sanitizeLogLine(line, cloudflareToken)
                    processCloudflaredOutput(cleanLine)
                }

                val exitCode = try { process.waitFor() } catch (e: Exception) { -1 }
                if (isRunning.get()) {
                    _tunnelState.value = TunnelState.ERROR
                    log("Proses cloudflared berhenti dengan exit code: $exitCode", isError = true)
                }
            } catch (e: Exception) {
                _tunnelState.value = TunnelState.ERROR
                log("Gagal menjalankan cloudflared: ${e.message}", isError = true)
            } finally {
                stopProcess()
            }
        }
    }

    private fun processCloudflaredOutput(line: String) {
        log(line)

        val lower = line.lowercase()

        // 1. Connection success patterns
        if (lower.contains("registered tunnel connection") ||
            lower.contains("connection registered") ||
            lower.contains("route propagating, waiting to serve traffic") ||
            lower.contains("inf connection") && lower.contains("registered connindex")
        ) {
            _tunnelState.value = TunnelState.CONNECTED
            _connectedSince.value = System.currentTimeMillis()
            log("✓ Cloudflare Edge Berhasil Terhubung! Website HP kini dapat diakses publik melalui domain Cloudflare Anda.")
        }

        // 2. Auth error patterns
        else if (lower.contains("cannot determine default origin certificate") ||
            lower.contains("invalid tunnel token") ||
            lower.contains("unauthorized") ||
            lower.contains("authentication failed") ||
            lower.contains("failed to create tunnel")
        ) {
            _tunnelState.value = TunnelState.ERROR
            log("Autentikasi Cloudflare Gagal: Token salah atau dicabut oleh Cloudflare Zero Trust.", isError = true)
        }

        // 3. Reconnection patterns
        else if (lower.contains("retrying connection in") ||
            lower.contains("connection terminated") ||
            lower.contains("quitting connection")
        ) {
            if (_tunnelState.value == TunnelState.CONNECTED) {
                _tunnelState.value = TunnelState.RECONNECTING
                log("Koneksi ke Cloudflare Edge terputus, mencoba menyambungkan ulang...", isError = true)
            }
        }
    }

    private fun sanitizeLogLine(line: String, token: String): String {
        return if (token.length > 8 && line.contains(token)) {
            line.replace(token, "[TOKEN_TERLINDUNGI]")
        } else {
            line
        }
    }

    fun stopTunnel() {
        isRunning.set(false)
        stopProcess()
        _tunnelState.value = TunnelState.DISCONNECTED
        _connectedSince.value = null
        log("Cloudflare Tunnel dihentikan.")
    }

    private fun stopProcess() {
        try {
            tunnelProcess?.destroy()
            tunnelProcess?.destroyForcibly()
        } catch (ignored: Exception) {}
        tunnelProcess = null
        processJob?.cancel()
        processJob = null
    }

    /**
     * Download or install pre-built cloudflared binary for Android ABI.
     */
    suspend fun downloadOrInstallBinary(onProgress: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Mendeteksi ABI perangkat: $detectedAbi...")

                // Target download URL according to architecture
                val arch = when (detectedAbi) {
                    "arm64-v8a" -> "arm64"
                    "armeabi-v7a" -> "arm"
                    "x86_64" -> "amd64"
                    "x86" -> "386"
                    else -> "arm64"
                }

                val downloadUrl = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-$arch"
                onProgress("Mengunduh cloudflared dari $downloadUrl...")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(downloadUrl).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    onProgress("Gagal mengunduh binary: HTTP ${response.code}")
                    response.close()
                    return@withContext false
                }

                val tempFile = File(binDir, "cloudflared.tmp")
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                response.close()

                tempFile.renameTo(cloudflaredBinary)
                cloudflaredBinary.setExecutable(true, false)

                onProgress("✓ Binary cloudflared berhasil dipasang di ${cloudflaredBinary.absolutePath}!")
                log("Binary cloudflared siap digunakan (Ukuran: ${cloudflaredBinary.length()} bytes)")
                true
            } catch (e: Exception) {
                onProgress("Gagal memasang binary: ${e.message}")
                log("Error instalasi binary: ${e.message}", isError = true)
                false
            }
        }
    }

    private fun log(message: String, isError: Boolean = false) {
        _logs.tryEmit(TunnelLog(System.currentTimeMillis(), message, isError))
    }
}
