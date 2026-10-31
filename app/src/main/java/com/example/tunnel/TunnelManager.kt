package com.example.tunnel

import android.content.Context
import com.example.data.DataStoreManager
import com.example.data.DomainManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    val runner = CloudflareTunnelRunner(context, scope)

    val tunnelState: StateFlow<TunnelState> = runner.tunnelState
    val logs: SharedFlow<TunnelLog> = runner.logs
    val connectedSince: StateFlow<Long?> = runner.connectedSince

    private val _gatewayHost = MutableStateFlow<String>("")
    val gatewayHost: StateFlow<String> = _gatewayHost.asStateFlow()

    private val _activeConnections = MutableStateFlow<Int>(0)
    val activeConnections: StateFlow<Int> = _activeConnections.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun startTunnel(
        localPort: Int,
        cloudflareToken: String?,
        customDomainInput: String? = null,
        autoReconnect: Boolean = true
    ) {
        if (cloudflareToken.isNullOrBlank()) {
            runner.stopTunnel()
            log("Error: Token Cloudflare Tunnel belum diisi.", isError = true)
            log("Buka Cloudflare Zero Trust Dashboard -> Networks -> Tunnels, salin Tunnel Token Anda.", isError = true)
            return
        }

        // Verify local origin server health
        scope.launch(Dispatchers.IO) {
            log("Memeriksa kesehatan origin server lokal (http://127.0.0.1:$localPort)...")
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
                log("Peringatan: Web server lokal HP di port $localPort belum aktif.", isError = true)
                log("Pastikan tombol 'START SERVER' sudah diaktifkan agar traffic dari internet dapat dilayani.", isError = true)
            } else {
                log("✓ Origin lokal http://127.0.0.1:$localPort responsif dan siap melayani pengunjung.")
            }

            // Start native cloudflared process
            runner.startTunnel(
                localPort = localPort,
                cloudflareToken = cloudflareToken
            )
        }
    }

    fun stopTunnel() {
        runner.stopTunnel()
        _activeConnections.value = 0
    }

    suspend fun installBinary(onProgress: (String) -> Unit): Boolean {
        return runner.downloadOrInstallBinary(onProgress)
    }

    private fun log(message: String, isError: Boolean = false) {
        runner.validateToken("") // No-op to touch
    }
}
