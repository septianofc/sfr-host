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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import com.example.data.DomainManager
import com.example.files.FileManager
import com.example.notification.NotificationRepository
import com.example.server.HttpRequestLog
import com.example.server.SfrServerService
import com.example.tunnel.TunnelManager
import com.example.tunnel.TunnelState
import com.example.ui.components.SfrTopBar
import com.example.ui.screens.AccessLogsScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.CustomDomainsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DnsManagementScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingBottomSheet
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SslTlsScreen
import com.example.ui.screens.TunnelScreen
import com.example.ui.screens.WebsitesScreen
import com.example.ui.theme.SfrHostTheme
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    OVERVIEW("Dashboard", Icons.Default.Dashboard),
    WEBSITES("Hosting", Icons.Default.Language),
    FILE_MANAGER("Files", Icons.Default.Folder),
    CUSTOM_DOMAINS("Domains", Icons.Default.Dns),
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
                MainApp(initialScreen = initialScreen)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(initialScreen: Screen = Screen.OVERVIEW) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dataStore = remember { DataStoreManager(context) }
    val domainManager = remember { DomainManager(context) }
    val notifRepo = remember { NotificationRepository.getInstance(context) }
    val fileManager = remember { FileManager(context) }

    var currentScreen by remember { mutableStateOf(initialScreen) }

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
    val websitesList = remember { fileManager.getWebsites() }

    // Server & Tunnel Runtime State
    val isServerRunning by SfrServerService.isServerRunning.collectAsState()
    val standaloneTunnelManager = remember { TunnelManager(context, scope) }

    val activeTunnelManager = SfrServerService.currentTunnelInstance ?: standaloneTunnelManager
    val tunnelState by activeTunnelManager.tunnelState.collectAsState()
    val activeConnections by activeTunnelManager.activeConnections.collectAsState()

    // Notification State
    val notifications by notifRepo.notifications.collectAsState()
    val isRefreshingNotifs by notifRepo.isRefreshing.collectAsState()
    val latestUpdate by notifRepo.latestUpdateInfo.collectAsState()
    val unreadNotifCount = notifications.count { !it.isRead }

    // Request logs collector for Access Logs & Analytics
    val sessionLogs = remember { mutableStateListOf<HttpRequestLog>() }
    LaunchedEffect(isServerRunning) {
        val server = SfrServerService.currentServerInstance
        server?.requestLogs?.collect { log ->
            sessionLogs.add(0, log)
            if (sessionLogs.size > 200) sessionLogs.removeLast()
        }
    }

    // In-App Preview Sheet state
    var previewUrlToOpen by remember { mutableStateOf<String?>(null) }
    var showOnboardingSheet by remember { mutableStateOf(false) }

    // Handle Back Press to return to OVERVIEW if on a sub-screen
    BackHandler(enabled = currentScreen != Screen.OVERVIEW) {
        currentScreen = Screen.OVERVIEW
    }

    // Notification Permission Request (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Check if user needs onboarding guide
        val hasOnboarded = dataStore.hasCompletedOnboarding.first()
        if (!hasOnboarded) {
            showOnboardingSheet = true
        }

        // Auto start server if configured
        val autoStart = dataStore.autoStartServer.first()
        if (autoStart && !isServerRunning) {
            val port = dataStore.serverPort.first()
            val website = dataStore.activeWebsite.first()
            val startTunnel = dataStore.autoStartTunnel.first()
            val token = dataStore.cloudflareToken.first()
            val domain = dataStore.customDomain.first()

            val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                action = SfrServerService.ACTION_START
                putExtra(SfrServerService.EXTRA_PORT, port)
                putExtra(SfrServerService.EXTRA_WEBSITE, website)
                putExtra(SfrServerService.EXTRA_START_TUNNEL, startTunnel)
                putExtra(SfrServerService.EXTRA_CLOUDFLARE_TOKEN, token)
                putExtra(SfrServerService.EXTRA_CUSTOM_DOMAIN, domain)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
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
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                val primaryNavScreens = listOf(
                    Screen.OVERVIEW,
                    Screen.WEBSITES,
                    Screen.FILE_MANAGER,
                    Screen.TUNNEL,
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
                            customDomainInput = customDomain,
                            autoReconnect = autoReconnectTunnel
                        )
                    },
                    onStopTunnel = {
                        activeTunnelManager.stopTunnel()
                    },
                    onOpenPreview = { url ->
                        previewUrlToOpen = url
                    },
                    onOpenOnboarding = {
                        showOnboardingSheet = true
                    },
                    onNavigateMenu = { menuTag ->
                        currentScreen = when (menuTag) {
                            "WEBSITES" -> Screen.WEBSITES
                            "FILES" -> Screen.FILE_MANAGER
                            "DOMAINS" -> Screen.CUSTOM_DOMAINS
                            "TUNNEL" -> Screen.TUNNEL
                            "DNS" -> Screen.DNS_MANAGER
                            "SSL" -> Screen.SSL_TLS
                            "SECURITY" -> Screen.SECURITY
                            "LOGS" -> Screen.ACCESS_LOGS
                            "ANALYTICS" -> Screen.ANALYTICS
                            else -> Screen.OVERVIEW
                        }
                    }
                )

                Screen.WEBSITES, Screen.FILE_MANAGER -> WebsitesScreen(
                    activeWebsiteName = activeWebsite,
                    serverPort = serverPort,
                    isServerOnline = isServerRunning,
                    onSetActiveWebsite = { newWebsite ->
                        scope.launch {
                            dataStore.setActiveWebsite(newWebsite)
                            if (isServerRunning) {
                                val serviceIntent = Intent(context, SfrServerService::class.java).apply {
                                    action = SfrServerService.ACTION_START
                                    putExtra(SfrServerService.EXTRA_PORT, serverPort)
                                    putExtra(SfrServerService.EXTRA_WEBSITE, newWebsite)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    context.startService(serviceIntent)
                                }
                            }
                        }
                    }
                )

                Screen.CUSTOM_DOMAINS -> CustomDomainsScreen(
                    domains = customDomainsList,
                    websites = websitesList,
                    gatewayHost = gatewayEndpoint,
                    onAddDomain = { dom, web ->
                        scope.launch { domainManager.addDomain(dom, web) }
                    },
                    onRemoveDomain = { dom ->
                        scope.launch { domainManager.removeDomain(dom) }
                    },
                    onVerifyDns = { dom ->
                        scope.launch { domainManager.verifyDns(dom, gatewayEndpoint) }
                    },
                    onToggleHttps = { item ->
                        scope.launch { domainManager.updateDomain(item.copy(forceHttps = !item.forceHttps)) }
                    }
                )

                Screen.TUNNEL -> TunnelScreen(
                    tunnelState = tunnelState,
                    publicUrl = if (customDomainsList.isNotEmpty()) "https://${customDomainsList.first().domain}" else "Menunggu Custom Domain",
                    customDomain = customDomain,
                    cloudflareToken = cloudflareToken,
                    autoReconnect = autoReconnectTunnel,
                    onSaveToken = { token -> scope.launch { dataStore.setCloudflareToken(token) } },
                    onSaveCustomDomain = { domain -> scope.launch { dataStore.setCustomDomain(domain) } },
                    onToggleAutoReconnect = { enabled -> scope.launch { dataStore.setAutoReconnectTunnel(enabled) } },
                    onStartTunnel = {
                        activeTunnelManager.startTunnel(
                            localPort = serverPort,
                            cloudflareToken = cloudflareToken,
                            customDomainInput = customDomain,
                            autoReconnect = autoReconnectTunnel
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
                    onToggleForceHttpsGlobal = { enabled -> scope.launch { dataStore.setForceHttps(enabled) } }
                )

                Screen.SECURITY -> SecurityScreen(
                    tunnelId = tunnelId,
                    authToken = tunnelAuthToken,
                    onRotateToken = { newToken -> scope.launch { dataStore.setTunnelAuthToken(newToken) } }
                )

                Screen.ACCESS_LOGS -> AccessLogsScreen(
                    logs = sessionLogs,
                    onClearLogs = { sessionLogs.clear() }
                )

                Screen.ANALYTICS -> AnalyticsScreen(
                    totalRequests = sessionLogs.size.toLong(),
                    trafficIn = trafficIn,
                    trafficOut = trafficOut,
                    ramUsage = NetworkUtils.getRamUsageString(),
                    uptimeMillis = SfrServerService.currentServerInstance?.uptimeMillis ?: 0L,
                    activeConnections = activeConnections,
                    requestLogs = sessionLogs
                )

                Screen.NOTIFICATIONS -> NotificationsScreen(
                    notifications = notifications,
                    isRefreshing = isRefreshingNotifs,
                    latestUpdate = latestUpdate,
                    onRefresh = {
                        scope.launch { notifRepo.fetchRemoteNotifications(showSystemNotificationOnNew = true) }
                    },
                    onMarkAsRead = { id -> notifRepo.markAsRead(id) },
                    onMarkAllAsRead = { notifRepo.markAllAsRead() },
                    onClearHistory = { notifRepo.deleteLocalHistory() }
                )

                Screen.SERVER_SETTINGS, Screen.DEVELOPER_INFO -> SettingsScreen(
                    serverPort = serverPort,
                    autoStartServer = autoStartServer,
                    autoStartTunnel = autoStartTunnel,
                    autoReconnectTunnel = autoReconnectTunnel,
                    notifSoundEnabled = notifSoundEnabled,
                    notifCheckOnBoot = notifCheckOnBoot,
                    fcmToken = fcmToken,
                    gatewayEndpoint = gatewayEndpoint,
                    onSaveServerPort = { port -> scope.launch { dataStore.setServerPort(port) } },
                    onToggleAutoStartServer = { enabled -> scope.launch { dataStore.setAutoStartServer(enabled) } },
                    onToggleAutoStartTunnel = { enabled -> scope.launch { dataStore.setAutoStartTunnel(enabled) } },
                    onToggleAutoReconnectTunnel = { enabled -> scope.launch { dataStore.setAutoReconnectTunnel(enabled) } },
                    onToggleNotifSound = { enabled -> scope.launch { dataStore.setNotifSoundEnabled(enabled) } },
                    onToggleNotifCheckOnBoot = { enabled -> scope.launch { dataStore.setNotifCheckOnBoot(enabled) } },
                    onSaveGatewayEndpoint = { gw -> scope.launch { dataStore.setGatewayEndpoint(gw) } }
                )
            }
        }
    }

    // In-App Web Preview Sheet
    previewUrlToOpen?.let { url ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var webViewRef by remember { mutableStateOf<WebView?>(null) }

        ModalBottomSheet(
            onDismissRequest = { previewUrlToOpen = null },
            sheetState = sheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "In-App Browser Preview",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline,
                                fontFamily = FontFamily.Monospace
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row {
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Muat Ulang")
                        }
                        IconButton(onClick = { previewUrlToOpen = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            webViewClient = WebViewClient()
                            loadUrl(url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showOnboardingSheet) {
        OnboardingBottomSheet(
            onDismiss = {
                showOnboardingSheet = false
                scope.launch { dataStore.setCompletedOnboarding(true) }
            }
        )
    }
}
