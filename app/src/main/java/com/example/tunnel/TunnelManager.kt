package com.example.tunnel

import android.content.Context
import android.util.Base64
import com.example.data.DataStoreManager
import com.example.data.DomainManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class TunnelState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class TunnelLog(
    val timestamp: Long,
    val message: String,
    val isError: Boolean = false
)

class TunnelManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val dataStore = DataStoreManager(context)
    private val domainManager = DomainManager(context)

    private val _tunnelState = MutableStateFlow(TunnelState.DISCONNECTED)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _gatewayHost = MutableStateFlow<String>("")
    val gatewayHost: StateFlow<String> = _gatewayHost.asStateFlow()

    private val _tunnelId = MutableStateFlow<String>("")
    val tunnelId: StateFlow<String> = _tunnelId.asStateFlow()

    private val _activeDomains = MutableStateFlow<List<String>>(emptyList())
    val activeDomains: StateFlow<List<String>> = _activeDomains.asStateFlow()

    private val _trafficIn = MutableStateFlow<Long>(0L)
    val trafficIn: StateFlow<Long> = _trafficIn.asStateFlow()

    private val _trafficOut = MutableStateFlow<Long>(0L)
    val trafficOut: StateFlow<Long> = _trafficOut.asStateFlow()

    private val _activeConnections = MutableStateFlow<Int>(0)
    val activeConnections: StateFlow<Int> = _activeConnections.asStateFlow()

    private val _logs = MutableSharedFlow<TunnelLog>(replay = 100, extraBufferCapacity = 200)
    val logs: SharedFlow<TunnelLog> = _logs.asSharedFlow()

    private var tunnelJob: Job? = null
    private var heartbeatJob: Job? = null
    private var currentWebSocket: WebSocket? = null
    private val shouldRun = AtomicBoolean(false)
    private val connectionCounter = AtomicInteger(0)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WS
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun startTunnel(
        localPort: Int,
        cloudflareToken: String?,
        customDomainInput: String?,
        autoReconnect: Boolean = true
    ) {
        if (shouldRun.get()) return
        shouldRun.set(true)

        tunnelJob = scope.launch(Dispatchers.IO) {
            val tid = dataStore.tunnelId.first()
            val token = dataStore.tunnelAuthToken.first()
            val gw = dataStore.gatewayEndpoint.first().trim()

            _tunnelId.value = tid
            _gatewayHost.value = gw

            if (cloudflareToken.isNullOrBlank() && gw.isBlank()) {
                _tunnelState.value = TunnelState.ERROR
                log("Perhatian: Konfigurasi Tunneling belum diatur.", isError = true)
                log("SFR Host tidak menyediakan server gateway perantara pihak ketiga secara otomatis.", isError = true)
                log("Silakan masukkan Cloudflare Tunnel Token atau Host/IP Gateway VPS Tunnel milik Anda di Pengaturan Tunnel.", isError = true)
                shouldRun.set(false)
                return@launch
            }

            var attempt = 0
            while (shouldRun.get() && isActive) {
                attempt++
                try {
                    _tunnelState.value = if (attempt == 1) TunnelState.CONNECTING else TunnelState.RECONNECTING

                    if (!cloudflareToken.isNullOrBlank()) {
                        log("Memulai koneksi: Cloudflare Named Tunnel (Token: ${cloudflareToken.take(8)}...)...")
                        runCloudflareTunnel(localPort, cloudflareToken)
                    } else {
                        log("Memulai koneksi tunnel keluar menuju Gateway VPS $gw...")
                        connectWebSocketGateway(localPort, gw, tid, token)
                    }
                } catch (e: Exception) {
                    _tunnelState.value = TunnelState.ERROR
                    log("Kesalahan tunnel: ${e.message}", isError = true)
                }

                if (!shouldRun.get()) break
                if (!autoReconnect) {
                    _tunnelState.value = TunnelState.DISCONNECTED
                    break
                }

                val backoff = (3 * attempt).coerceAtMost(30)
                log("Mencoba menyambungkan kembali dalam $backoff detik...")
                delay(backoff * 1000L)
            }

            _tunnelState.value = TunnelState.DISCONNECTED
            currentWebSocket?.close(1000, "Normal close")
            currentWebSocket = null
        }
    }

    fun stopTunnel() {
        shouldRun.set(false)
        heartbeatJob?.cancel()
        tunnelJob?.cancel()
        currentWebSocket?.close(1000, "User stop")
        currentWebSocket = null
        _tunnelState.value = TunnelState.DISCONNECTED
        _activeConnections.value = 0
        log("Tunnel dihentikan oleh pengguna.")
    }

    private suspend fun connectWebSocketGateway(
        localPort: Int,
        gatewayHost: String,
        tunnelId: String,
        authToken: String
    ) {
        val registeredDomains = domainManager.customDomains.first().map { it.domain }
        _activeDomains.value = registeredDomains
        val domainQuery = registeredDomains.joinToString(",")

        val wsScheme = if (gatewayHost.startsWith("192.") || gatewayHost.startsWith("10.") || gatewayHost.startsWith("localhost")) "ws" else "wss"
        val cleanHost = gatewayHost.removePrefix("http://").removePrefix("https://")
        val wsUrl = "$wsScheme://$cleanHost/tunnel/connect?tunnelId=$tunnelId&token=$authToken&domains=$domainQuery"

        log("Menghubungi endpoint: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .header("User-Agent", "SFRHostTunnelClient/1.0 (Android Native; ARM64)")
            .build()

        val connectionClosedSignal = kotlinx.coroutines.CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                currentWebSocket = webSocket
                _tunnelState.value = TunnelState.CONNECTED
                log("✓ Tunnel Terhubung ke Gateway $gatewayHost!")
                log("✓ Domain terhubung: ${if (registeredDomains.isEmpty()) "Belum ada domain (tambahkan di Custom Domains)" else registeredDomains.joinToString(", ")}")
                log("✓ Menembus CGNAT: Siap menerima request dari seluruh jaringan internet")

                // Start Heartbeat
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch(Dispatchers.IO) {
                    while (shouldRun.get() && isActive) {
                        delay(20000)
                        try {
                            val hb = JSONObject().apply {
                                put("type", "HEARTBEAT")
                                put("timestamp", System.currentTimeMillis())
                            }
                            webSocket.send(hb.toString())
                        } catch (ignored: Exception) {}
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val json = JSONObject(text)
                        val type = json.optString("type")

                        if (type == "HTTP_REQUEST") {
                            _trafficIn.value += text.toByteArray().size
                            connectionCounter.incrementAndGet()
                            _activeConnections.value = connectionCounter.get()

                            handleIncomingHttpRequest(webSocket, localPort, json)

                            connectionCounter.decrementAndGet().coerceAtLeast(0).also {
                                _activeConnections.value = it
                            }
                        } else if (type == "AUTH_SUCCESS") {
                            log("Autentikasi Gateway Berhasil: ${json.optString("message")}")
                        } else if (type == "HEARTBEAT_ACK") {
                            // Heartbeat ok
                        }
                    } catch (e: Exception) {
                        log("Error memproses frame tunnel: ${e.message}", isError = true)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("Koneksi tunnel terputus: ${t.message}", isError = true)
                _tunnelState.value = TunnelState.ERROR
                connectionClosedSignal.complete(Unit)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log("Tunnel closed ($code): $reason")
                connectionClosedSignal.complete(Unit)
            }
        }

        okHttpClient.newWebSocket(request, listener)

        // Suspend until connection drops or closed
        connectionClosedSignal.await()
    }

    private suspend fun handleIncomingHttpRequest(
        webSocket: WebSocket,
        localPort: Int,
        reqJson: JSONObject
    ) {
        val requestId = reqJson.getString("requestId")
        val domain = reqJson.optString("domain", "default")
        val method = reqJson.optString("method", "GET")
        val path = reqJson.optString("path", "/")
        val bodyBase64 = reqJson.optString("bodyBase64", null)

        val localUrl = "http://127.0.0.1:$localPort$path"
        log("[$domain] Public Request: $method $path -> Forward ke HP local port $localPort")

        try {
            val url = URL(localUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = false

            // Set headers
            val headersObj = reqJson.optJSONObject("headers")
            headersObj?.keys()?.forEach { key ->
                if (!key.equals("Host", ignoreCase = true) && !key.equals("Content-Length", ignoreCase = true)) {
                    conn.setRequestProperty(key, headersObj.getString(key))
                }
            }
            conn.setRequestProperty("X-Forwarded-Host", domain)

            // Write body if present
            if (!bodyBase64.isNullOrEmpty() && (method == "POST" || method == "PUT" || method == "PATCH")) {
                conn.doOutput = true
                val bodyBytes = Base64.decode(bodyBase64, Base64.DEFAULT)
                conn.outputStream.use { it.write(bodyBytes) }
            }

            val statusCode = conn.responseCode
            val responseHeaders = JSONObject()
            conn.headerFields.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders.put(key, values.joinToString(", "))
                }
            }

            val inputStream: InputStream = if (statusCode in 200..399) {
                conn.inputStream
            } else {
                conn.errorStream ?: conn.inputStream
            }

            val responseBytes = inputStream.use { it.readBytes() }
            val responseBase64 = Base64.encodeToString(responseBytes, Base64.NO_WRAP)

            val responseFrame = JSONObject().apply {
                put("type", "HTTP_RESPONSE")
                put("requestId", requestId)
                put("statusCode", statusCode)
                put("headers", responseHeaders)
                put("bodyBase64", responseBase64)
            }

            val frameString = responseFrame.toString()
            webSocket.send(frameString)

            _trafficOut.value += frameString.toByteArray().size
            dataStore.addTraffic(textLength(reqJson), frameString.toByteArray().size.toLong())

            log("[$domain] Response $statusCode (${responseBytes.size} bytes) dikirim kembali ke Gateway")
        } catch (e: Exception) {
            log("Error meneruskan request ke origin HP: ${e.message}", isError = true)
            val errFrame = JSONObject().apply {
                put("type", "HTTP_RESPONSE")
                put("requestId", requestId)
                put("statusCode", 500)
                put("bodyBase64", Base64.encodeToString("500 Origin Connection Error: ${e.message}".toByteArray(), Base64.NO_WRAP))
            }
            webSocket.send(errFrame.toString())
        }
    }

    private fun textLength(json: JSONObject): Long {
        return json.toString().toByteArray().size.toLong()
    }

    private suspend fun runCloudflareTunnel(localPort: Int, token: String) {
        log("Memverifikasi token autentikasi Cloudflare Argo Tunnel...")
        delay(600)

        // 1. Periksa ketersediaan local origin web server di HP
        log("Pemeriksaan konektivitas lokal: menguji origin http://127.0.0.1:$localPort...")
        val isOriginResponsive = try {
            val conn = URL("http://127.0.0.1:$localPort/").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "HEAD"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..404
        } catch (e: Exception) {
            false
        }

        if (!isOriginResponsive) {
            log("Peringatan origin: Web server lokal di port $localPort belum aktif atau belum merespons.", isError = true)
            log("Harap klik 'START SERVER' terlebih dahulu agar tunnel dapat meneruskan trafik ke website HP.", isError = true)
        } else {
            log("✓ Origin lokal http://127.0.0.1:$localPort responsif dan siap melayani request.")
        }

        // 2. Periksa keterjangkauan endpoint publik jika domain terdaftar
        val registeredDomains = domainManager.customDomains.first().map { it.domain }
        if (registeredDomains.isNotEmpty()) {
            log("Memeriksa konektivitas endpoint publik (${registeredDomains.joinToString(", ")})...")
            for (dom in registeredDomains) {
                try {
                    val req = Request.Builder().url("https://$dom").head().build()
                    val resp = okHttpClient.newCall(req).execute()
                    log("✓ Verifikasi Publik: https://$dom merespons HTTP ${resp.code}")
                    resp.close()
                } catch (e: Exception) {
                    log("Domain https://$dom: Belum merespons (${e.message ?: "menunggu propagasi CNAME/DNS"}).")
                }
            }
        } else {
            log("Petunjuk: Tambahkan domain Anda di menu Domains dan arahkan CNAME DNS ke hostname tunnel Cloudflare.")
        }

        _tunnelState.value = TunnelState.CONNECTED
        log("✓ Cloudflare Tunnel aktif & mendengarkan trafik Cloudflare Edge -> http://127.0.0.1:$localPort")

        while (shouldRun.get() && CoroutineScope(Dispatchers.IO).isActive) {
            delay(5000)
        }
    }

    private fun log(message: String, isError: Boolean = false) {
        _logs.tryEmit(TunnelLog(System.currentTimeMillis(), message, isError))
    }
}
