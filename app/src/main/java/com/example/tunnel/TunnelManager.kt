package com.example.tunnel

import android.content.Context
import com.example.data.DataStoreManager
import com.example.data.DomainManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

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
    val downloadProgress: StateFlow<DownloadProgress> = runner.downloadProgress

    private val _gatewayHost = MutableStateFlow<String>("")
    val gatewayHost: StateFlow<String> = _gatewayHost.asStateFlow()

    private val _activeConnections = MutableStateFlow<Int>(0)
    val activeConnections: StateFlow<Int> = _activeConnections.asStateFlow()

    fun startTunnel(
        localPort: Int,
        cloudflareToken: String?,
        customDomainInput: String? = null,
        autoReconnect: Boolean = true
    ) {
        if (cloudflareToken.isNullOrBlank()) {
            runner.stopTunnel()
            return
        }

        scope.launch(Dispatchers.IO) {
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

    suspend fun executeInstallPipeline(): Boolean {
        return runner.executeInstallPipeline()
    }

    fun cancelDownload() {
        runner.cancelDownload()
    }

    fun importCustomBinary(inputStream: InputStream): Pair<Boolean, String> {
        return runner.importCustomBinary(inputStream)
    }
}
