package com.example.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DataStoreManager
import com.example.files.FileManager
import com.example.monitoring.DeviceThermalManager
import com.example.tunnel.TunnelManager
import com.example.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class SfrServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationTickerJob: Job? = null
    private var thermalManager: DeviceThermalManager? = null

    companion object {
        const val CHANNEL_ID = "sfrhost_server"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.sfrhostapp.action.START_SERVER"
        const val ACTION_STOP = "com.sfrhostapp.action.STOP_SERVER"

        const val EXTRA_PORT = "extra_port"
        const val EXTRA_WEBSITE = "extra_website"
        const val EXTRA_START_TUNNEL = "extra_start_tunnel"
        const val EXTRA_CLOUDFLARE_TOKEN = "extra_cloudflare_token"
        const val EXTRA_CUSTOM_DOMAIN = "extra_custom_domain"

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private val _currentPort = MutableStateFlow(8080)
        val currentPort: StateFlow<Int> = _currentPort.asStateFlow()

        private val _currentWebsite = MutableStateFlow("website1")
        val currentWebsite: StateFlow<String> = _currentWebsite.asStateFlow()

        var currentServerInstance: SfrHttpServer? = null
            private set

        var currentTunnelInstance: TunnelManager? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SfrHost::ServerWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // Safe 24h timeout

        val dataStore = DataStoreManager(this)
        thermalManager = DeviceThermalManager(
            context = this,
            onHotState = {
                serviceScope.launch {
                    val enabled = dataStore.thermalProtectionEnabled.first()
                    val autoReduce = dataStore.thermalAutoReduce.first()
                    if (enabled && autoReduce) {
                        if (_currentWebsite.value != "website1") {
                            _currentWebsite.value = "website1"
                            currentServerInstance?.stop()
                            val websiteDir = FileManager(applicationContext).getWebsiteDirectory("website1")
                            val newServer = SfrHttpServer(_currentPort.value, websiteDir)
                            if (newServer.start()) {
                                currentServerInstance = newServer
                            }
                        }
                    }
                }
            },
            onCriticalState = {
                serviceScope.launch {
                    val enabled = dataStore.thermalProtectionEnabled.first()
                    val cutoff = dataStore.thermalCriticalCutoff.first()
                    if (enabled && cutoff) {
                        stopServerInternal()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopServerInternal()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        val website = intent?.getStringExtra(EXTRA_WEBSITE) ?: "website1"
        val shouldStartTunnel = intent?.getBooleanExtra(EXTRA_START_TUNNEL, false) ?: false
        val token = intent?.getStringExtra(EXTRA_CLOUDFLARE_TOKEN)
        val domain = intent?.getStringExtra(EXTRA_CUSTOM_DOMAIN)

        startServerInternal(port, website, shouldStartTunnel, token, domain)

        val notification = buildForegroundNotification(port, website, "00:00:00", 0)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun startServerInternal(
        port: Int,
        website: String,
        startTunnel: Boolean,
        token: String?,
        domain: String?
    ) {
        serviceScope.launch {
            currentServerInstance?.stop()

            val fileManager = FileManager(applicationContext)
            val websiteDir = fileManager.getWebsiteDirectory(website)

            val server = SfrHttpServer(port, websiteDir)
            if (server.start()) {
                currentServerInstance = server
                _currentPort.value = port
                _currentWebsite.value = website
                _isServerRunning.value = true

                if (startTunnel) {
                    val tunnel = TunnelManager(applicationContext, serviceScope)
                    currentTunnelInstance = tunnel
                    tunnel.startTunnel(port, token, domain)
                }

                // Launch Floating Widget if overlay permission is granted
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this@SfrServerService)) {
                    SfrFloatingWidgetService.start(this@SfrServerService)
                }

                // Start 1-second live ticker updating the persistent notification
                notificationTickerJob?.cancel()
                notificationTickerJob = serviceScope.launch {
                    while (isActive && _isServerRunning.value) {
                        val activeServer = currentServerInstance
                        val uptimeStr = if (activeServer != null && activeServer.isRunning) {
                            NetworkUtils.formatUptime(activeServer.uptimeMillis)
                        } else "00:00:00"
                        val reqCount = activeServer?.totalRequests ?: 0

                        updateNotification(port, website, uptimeStr, reqCount)
                        delay(1000L)
                    }
                }
            } else {
                _isServerRunning.value = false
            }
        }
    }

    private fun stopServerInternal() {
        notificationTickerJob?.cancel()
        notificationTickerJob = null

        // Stop Floating Widget
        SfrFloatingWidgetService.stop(this)

        currentTunnelInstance?.stopTunnel()
        currentTunnelInstance = null

        currentServerInstance?.stop()
        currentServerInstance = null

        _isServerRunning.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SFR Host Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status runtime Web Server SFR Host"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(
        port: Int,
        website: String,
        uptime: String = "00:00:00",
        reqCount: Long = 0L
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SfrServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SFR Host — Server ONLINE (⏱️ $uptime)")
            .setContentText("Port: $port • Req: $reqCount • Situs: $website")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hentikan Server", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(
        port: Int,
        website: String,
        uptime: String = "00:00:00",
        reqCount: Long = 0L
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildForegroundNotification(port, website, uptime, reqCount))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServerInternal()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
