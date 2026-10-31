package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.CustomDomainItem
import com.example.data.DataStoreManager
import com.example.data.DatabaseManager
import com.example.data.DomainManager
import com.example.files.FileManager
import com.example.notification.NotificationRepository
import com.example.security.AppLockManager
import com.example.server.HttpRequestLog
import com.example.server.SfrServerService
import com.example.tunnel.TunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.components.SfrTopBar
import com.example.ui.screens.AccessLogsScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.CustomDomainsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DatabasesScreen
import com.example.ui.screens.DeveloperScreen
import com.example.ui.screens.DnsManagementScreen
import com.example.ui.screens.HostingAndFilesScreen
import com.example.ui.screens.HtaccessScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingBottomSheet
import com.example.ui.screens.SecurityLockScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SslTlsScreen
import com.example.ui.screens.TunnelScreen
import com.example.ui.screens.WebServerPhpScreen
import com.example.ui.theme.SfrHostTheme
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    OVERVIEW("Dashboard", Icons.Default.Dashboard),
    HOSTING_AND_FILES("Hosting & File", Icons.Default.Language),
    CUSTOM_DOMAINS("Domains", Icons.Default.Dns),
    DATABASES("Databases", Icons.Default.Storage),
    WEB_SERVER_PHP("Server & PHP", Icons.Default.Code),
    HTACCESS_ERRORS(".htaccess & Errors", Icons.Default.Description),
    TUNNEL("Tunnel", Icons.Default.Cloud),
    DNS_MANAGER("DNS", Icons.Default.Dns),
    SSL_TLS("SSL/TLS", Icons.Default.Lock),
    SECURITY("Security", Icons.Default.Security),
    ACCESS_LOGS("Access Logs", Icons.Default.Terminal),
    ANALYTICS("Analytics", Icons.Default.BarChart),
    SERVER_SETTINGS("Settings", Icons.Default.Settings),
    NOTIFICATIONS("Notifikasi", Icons.Default.Notifications),
    DEVELOPER_INFO("Developer", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialScreen = if (intent?.getStringExtra("navigate_to") == "notifications") {
            Screen.NOTIFICATIONS
        } else {
            Screen.OVERVIEW
        }

        setContent {
            SfrHostTheme {
                MainContent(initialScreen = initialScreen)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(initialScreen: Screen = Screen.OVERVIEW) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { DataStoreManager(context) }
    val domainManager = remember { DomainManager(context) }
    val databaseManager = remember { DatabaseManager(context) }
    val appLockManager = remember { AppLockManager(context) }
    val notifRepo = remember { NotificationRepository.getInstance(context) }
    val fileManager = remember { FileManager(context) }

    var currentScreen by remember { mutableStateOf(initialScreen) }
    var isUnlockedSession by remember { mutableStateOf(false) }

    // App Lock Security Gate (Purely Android native device credential / biometric)
    val isLockEnabled by appLockManager.isLockEnabled.collectAsState(initial = false)

    if (isLockEnabled && !isUnlockedSession) {
        SecurityLockScreen(
            appLockManager = appLockManager,
            onUnlocked = { isUnlockedSession = true }
        )
        return
    }

    // Persistent Settings State
    val serverPort by dataStore.serverPort.collectAsState(initial = 8080)
    val activeWebsite by dataStore.activeWebsite.collectAsState(initial = "website1")
    val autoStartServer by dataStore.autoStartServer.collectAsState(initial = false)
    val autoStartTunnel by dataStore.autoStartTunnel.collectAsState(initial = false)
    val autoReconnectTunnel by dataStore.autoReconnectTunnel.collectAsState(initial = true)
    val tunnelId by dataStore.tunnelId.collectAsState(initial = "sfr-node-default")
    val tunnelAuthToken by dataStore.tunnelAuthToken.collectAsState(initial = "")
    val gatewayEndpoint by dataStore.gatewayEndpoint.collectAsState(initial = "")
    val cloudflareToken by dataStore.cloudflareToken.collectAsState(initial = "")
    val customDomain by dataStore.customDomain.collectAsState(initial = "")
    val notifSoundEnabled by dataStore.notifSoundEnabled.collectAsState(initial = true)
    val notifCheckOnBoot by dataStore.notifCheckOnBoot.collectAsState(initial = true)
    val fcmToken by dataStore.fcmToken.collectAsState(initial = "")
    val trafficIn by dataStore.trafficIn.collectAsState(initial = 0L)
    val trafficOut by dataStore.trafficOut.collectAsState(initial = 0L)
    val forceHttpsGlobal by dataStore.forceHttps.collectAsState(initial = true)

    val customDomainsList by domainManager.customDomains.collectAsState(initial = emptyList())

    // Server & Service State
    var isServerRunning by remember { mutableStateOf(false) }
    var tunnelState by remember { mutableStateOf(TunnelState.DISCONNECTED) }
    var activeConnections by remember { mutableStateOf(0) }
    val sessionLogs = remember { mutableStateListOf<HttpRequestLog>() }
    var previewUrlToOpen by remember { mutableStateOf<String?>(null) }
    var showOnboardingSheet by remember { mutableStateOf(false) }

    // Notifications State
    val notifications by notifRepo.notifications.collectAsState(initial = emptyList())
    val unreadCount = notifications.count { !it.isRead }
    val isRefreshingNotifs by notifRepo.isRefreshing.collectAsState(initial = false)
    val latestUpdate by notifRepo.latestUpdateInfo.collectAsState(initial = null)

    val websitesList = remember(activeWebsite) {
        fileManager.getWebsites()
    }

    val activeTunnelManager = remember {
        SfrServerService.currentTunnelInstance ?: TunnelManager(context, scope)
    }

    // Permission Launchers
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch { notifRepo.fetchRemoteNotifications() }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        notifRepo.fetchRemoteNotifications()
    }

    // Auto-start features on launch if enabled
    LaunchedEffect(Unit) {
        val shouldAutoServer = dataStore.autoStartServer.first()
        val shouldAutoTunnel = dataStore.autoStartTunnel.first()
        val port = dataStore.serverPort.first()
        val web = dataStore.activeWebsite.first()
        val cfToken = dataStore.cloudflareToken.first()
        val domain = dataStore.customDomain.first()

        if (shouldAutoServer && !isServerRunning) {
            val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                action = SfrServerService.ACTION_START
                putExtra(SfrServerService.EXTRA_PORT, port)
                putExtra(SfrServerService.EXTRA_WEBSITE, web)
                putExtra(SfrServerService.EXTRA_START_TUNNEL, shouldAutoTunnel)
                putExtra(SfrServerService.EXTRA_CLOUDFLARE_TOKEN, cfToken)
                putExtra(SfrServerService.EXTRA_CUSTOM_DOMAIN, domain)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    // Polling / listening to SfrServerService instance
    LaunchedEffect(Unit) {
        while (true) {
            val currentServer = SfrServerService.currentServerInstance
            isServerRunning = currentServer != null && currentServer.isRunning

            val currentTunnel = SfrServerService.currentTunnelInstance
            if (currentTunnel != null) {
                tunnelState = currentTunnel.tunnelState.value
                activeConnections = currentTunnel.activeConnections.value
            } else {
                tunnelState = TunnelState.DISCONNECTED
                activeConnections = 0
            }

            delay(1000)
        }
    }

    // Collect HTTP Request Logs
    LaunchedEffect(Unit) {
        while (true) {
            val server = SfrServerService.currentServerInstance
            server?.let { s ->
                s.requestLogs.collect { log ->
                    sessionLogs.add(log)
                    if (sessionLogs.size > 200) sessionLogs.removeAt(0)
                }
            }
            delay(1500)
        }
    }

    BackHandler(enabled = currentScreen != Screen.OVERVIEW) {
        currentScreen = Screen.OVERVIEW
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                SfrTopBar(
                    isServerOnline = isServerRunning,
                    activeWebsite = activeWebsite
                )

                // cPanel Horizontal Category Suite Bar
                ScrollableTabRow(
                    selectedTabIndex = Screen.values().indexOf(currentScreen).coerceAtLeast(0),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = SfrPrimaryBlue,
                    indicator = {},
                    divider = {}
                ) {
                    Screen.values().forEach { screenItem ->
                        val isSelected = currentScreen == screenItem
                        Tab(
                            selected = isSelected,
                            onClick = { currentScreen = screenItem },
                            text = {
                                Text(
                                    text = screenItem.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) SfrPrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val primaryNavScreens = listOf(
                    Screen.OVERVIEW,
                    Screen.HOSTING_AND_FILES,
                    Screen.TUNNEL,
                    Screen.DATABASES,
                    Screen.SERVER_SETTINGS
                )

                primaryNavScreens.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        icon = {
                            Icon(imageVector = screen.icon, contentDescription = screen.title)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SfrPrimaryBlue,
                            selectedTextColor = SfrPrimaryBlue,
                            indicatorColor = SfrPrimaryBlue.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.OVERVIEW -> DashboardScreen(
                    isServerOnline = isServerRunning,
                    tunnelState = tunnelState,
                    gatewayHost = gatewayEndpoint,
                    tunnelId = tunnelId,
                    connectedDomains = customDomainsList,
                    totalWebsitesCount = websitesList.size,
                    serverPort = serverPort,
                    activeWebsite = activeWebsite,
                    trafficIn = trafficIn,
                    trafficOut = trafficOut,
                    activeConnections = activeConnections,
                    onStartServer = {
                        val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                            action = SfrServerService.ACTION_START
                            putExtra(SfrServerService.EXTRA_PORT, serverPort)
                            putExtra(SfrServerService.EXTRA_WEBSITE, activeWebsite)
                            putExtra(SfrServerService.EXTRA_START_TUNNEL, autoStartTunnel)
                            putExtra(SfrServerService.EXTRA_CLOUDFLARE_TOKEN, cloudflareToken)
                            putExtra(SfrServerService.EXTRA_CUSTOM_DOMAIN, customDomain)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    },
                    onStopServer = {
                        val stopIntent = Intent(context, SfrServerService::class.java).apply {
                            action = SfrServerService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    },
                    onRestartServer = {
                        val stopIntent = Intent(context, SfrServerService::class.java).apply {
                            action = SfrServerService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        scope.launch {
                            delay(400)
                            val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                                action = SfrServerService.ACTION_START
                                putExtra(SfrServerService.EXTRA_PORT, serverPort)
                                putExtra(SfrServerService.EXTRA_WEBSITE, activeWebsite)
                                putExtra(SfrServerService.EXTRA_START_TUNNEL, autoStartTunnel)
                                putExtra(SfrServerService.EXTRA_CLOUDFLARE_TOKEN, cloudflareToken)
                                putExtra(SfrServerService.EXTRA_CUSTOM_DOMAIN, customDomain)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    },
                    onStartTunnel = {
                        activeTunnelManager.startTunnel(
                            localPort = serverPort,
                            cloudflareToken = cloudflareToken,
                            customDomainInput = customDomain
                        )
                    },
                    onStopTunnel = {
                        activeTunnelManager.stopTunnel()
                    },
                    onOpenPreview = { url ->
                        previewUrlToOpen = url
                    },
                    onNavigateMenu = { menuName ->
                        when (menuName) {
                            "hosting", "files" -> currentScreen = Screen.HOSTING_AND_FILES
                            "domains" -> currentScreen = Screen.CUSTOM_DOMAINS
                            "databases" -> currentScreen = Screen.DATABASES
                            "server_php" -> currentScreen = Screen.WEB_SERVER_PHP
                            "htaccess" -> currentScreen = Screen.HTACCESS_ERRORS
                            "tunnel" -> currentScreen = Screen.TUNNEL
                            "security" -> currentScreen = Screen.SECURITY
                            "logs" -> currentScreen = Screen.ACCESS_LOGS
                            "settings" -> currentScreen = Screen.SERVER_SETTINGS
                            else -> {}
                        }
                    },
                    onOpenOnboarding = {
                        showOnboardingSheet = true
                    }
                )

                Screen.HOSTING_AND_FILES -> HostingAndFilesScreen(
                    activeWebsiteName = activeWebsite,
                    serverPort = serverPort,
                    isServerOnline = isServerRunning,
                    onSetActiveWebsite = { newWeb ->
                        scope.launch {
                            dataStore.setActiveWebsite(newWeb)
                            if (isServerRunning) {
                                val restartIntent = Intent(context, SfrServerService::class.java).apply {
                                    action = SfrServerService.ACTION_START
                                    putExtra(SfrServerService.EXTRA_PORT, serverPort)
                                    putExtra(SfrServerService.EXTRA_WEBSITE, newWeb)
                                    putExtra(SfrServerService.EXTRA_START_TUNNEL, autoStartTunnel)
                                    putExtra(SfrServerService.EXTRA_CLOUDFLARE_TOKEN, cloudflareToken)
                                    putExtra(SfrServerService.EXTRA_CUSTOM_DOMAIN, customDomain)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(restartIntent)
                                } else {
                                    context.startService(restartIntent)
                                }
                            }
                        }
                    }
                )

                Screen.CUSTOM_DOMAINS -> CustomDomainsScreen(
                    domains = customDomainsList,
                    websites = websitesList,
                    gatewayHost = gatewayEndpoint,
                    onAddDomain = { domain, type, mode, targetWeb ->
                        scope.launch {
                            domainManager.addDomain(domain, type, mode, targetWeb)
                        }
                    },
                    onRemoveDomain = { domain ->
                        scope.launch { domainManager.removeDomain(domain) }
                    },
                    onVerifyDns = { domain ->
                        scope.launch { domainManager.verifyDns(domain) }
                    },
                    onToggleHttps = { item ->
                        scope.launch { domainManager.updateDomain(item.copy(forceHttps = !item.forceHttps)) }
                    }
                )

                Screen.DATABASES -> DatabasesScreen(
                    databaseManager = databaseManager
                )

                Screen.WEB_SERVER_PHP -> WebServerPhpScreen(
                    isServerOnline = isServerRunning,
                    serverPort = serverPort,
                    activeWebsite = activeWebsite,
                    onRestartServer = {
                        val stopIntent = Intent(context, SfrServerService::class.java).apply {
                            action = SfrServerService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                        scope.launch {
                            delay(400)
                            val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                                action = SfrServerService.ACTION_START
                                putExtra(SfrServerService.EXTRA_PORT, serverPort)
                                putExtra(SfrServerService.EXTRA_WEBSITE, activeWebsite)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                        }
                    }
                )

                Screen.HTACCESS_ERRORS -> HtaccessScreen(
                    activeWebsite = activeWebsite
                )

                Screen.TUNNEL -> TunnelScreen(
                    tunnelState = tunnelState,
                    publicUrl = if (tunnelState == TunnelState.CONNECTED) gatewayEndpoint.ifEmpty { "https://$customDomain" } else null,
                    customDomain = customDomain,
                    cloudflareToken = cloudflareToken,
                    autoReconnect = autoReconnectTunnel,
                    onSaveToken = { scope.launch { dataStore.setCloudflareToken(it) } },
                    onSaveCustomDomain = { scope.launch { dataStore.setCustomDomain(it) } },
                    onToggleAutoReconnect = { scope.launch { dataStore.setAutoReconnectTunnel(it) } },
                    onStartTunnel = {
                        activeTunnelManager.startTunnel(
                            localPort = serverPort,
                            cloudflareToken = cloudflareToken,
                            customDomainInput = customDomain
                        )
                    },
                    onStopTunnel = {
                        activeTunnelManager.stopTunnel()
                    }
                )

                Screen.DNS_MANAGER -> DnsManagementScreen(
                    domains = customDomainsList,
                    gatewayHost = gatewayEndpoint
                )

                Screen.SSL_TLS -> SslTlsScreen(
                    domains = customDomainsList,
                    forceHttpsGlobal = forceHttpsGlobal,
                    onToggleForceHttpsGlobal = {
                        scope.launch { dataStore.setForceHttps(it) }
                    }
                )

                Screen.SECURITY -> SecurityScreen(
                    tunnelId = tunnelId,
                    authToken = tunnelAuthToken,
                    appLockManager = appLockManager,
                    onRotateToken = { newToken ->
                        scope.launch { dataStore.setTunnelAuthToken(newToken) }
                    }
                )

                Screen.ACCESS_LOGS -> AccessLogsScreen(
                    logs = sessionLogs,
                    onClearLogs = { sessionLogs.clear() }
                )

                Screen.ANALYTICS -> AnalyticsScreen(
                    totalRequests = sessionLogs.size.toLong(),
                    trafficIn = trafficIn,
                    trafficOut = trafficOut,
                    ramUsage = "38 MB",
                    uptimeMillis = SfrServerService.currentServerInstance?.uptimeMillis ?: 0L,
                    activeConnections = activeConnections,
                    requestLogs = sessionLogs
                )

                Screen.SERVER_SETTINGS -> SettingsScreen(
                    serverPort = serverPort,
                    autoStartServer = autoStartServer,
                    autoStartTunnel = autoStartTunnel,
                    autoReconnectTunnel = autoReconnectTunnel,
                    notifSoundEnabled = notifSoundEnabled,
                    notifCheckOnBoot = notifCheckOnBoot,
                    fcmToken = fcmToken,
                    gatewayEndpoint = gatewayEndpoint,
                    onSaveServerPort = { scope.launch { dataStore.setServerPort(it) } },
                    onToggleAutoStartServer = { scope.launch { dataStore.setAutoStartServer(it) } },
                    onToggleAutoStartTunnel = { scope.launch { dataStore.setAutoStartTunnel(it) } },
                    onToggleAutoReconnectTunnel = { scope.launch { dataStore.setAutoReconnectTunnel(it) } },
                    onToggleNotifSound = { scope.launch { dataStore.setNotifSoundEnabled(it) } },
                    onToggleNotifCheckOnBoot = { scope.launch { dataStore.setNotifCheckOnBoot(it) } },
                    onSaveGatewayEndpoint = { scope.launch { dataStore.setGatewayEndpoint(it) } }
                )

                Screen.NOTIFICATIONS -> NotificationsScreen(
                    notifications = notifications,
                    isRefreshing = isRefreshingNotifs,
                    latestUpdate = latestUpdate,
                    onRefresh = { scope.launch { notifRepo.fetchRemoteNotifications() } },
                    onMarkAsRead = { id -> notifRepo.markAsRead(id) },
                    onMarkAllAsRead = { notifRepo.markAllAsRead() },
                    onClearHistory = { notifRepo.deleteLocalHistory() }
                )

                Screen.DEVELOPER_INFO -> DeveloperScreen()
            }
        }
    }

    // In-App Preview Sheet
    previewUrlToOpen?.let { url ->
        val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }

        ModalBottomSheet(
            onDismissRequest = { previewUrlToOpen = null },
            sheetState = previewSheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Website Preview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = SfrPrimaryBlue
                        )
                        Text(
                            text = url,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                        IconButton(onClick = { previewUrlToOpen = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(url)
                            webViewInstance = this
                        }
                    }
                )
            }
        }
    }

    // Onboarding Sheet
    if (showOnboardingSheet) {
        OnboardingBottomSheet(
            onDismiss = { showOnboardingSheet = false }
        )
    }
}
