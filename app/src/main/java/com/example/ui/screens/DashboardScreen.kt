package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.example.monitoring.DeviceThermalManager
import com.example.monitoring.ThermalLevel
import com.example.ui.theme.SfrButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomDomainItem
import com.example.server.HttpRequestLog
import com.example.server.SfrServerService
import com.example.tunnel.TunnelState
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.util.NetworkUtils
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    isServerOnline: Boolean,
    tunnelState: TunnelState,
    gatewayHost: String,
    tunnelId: String,
    connectedDomains: List<CustomDomainItem>,
    totalWebsitesCount: Int,
    serverPort: Int,
    activeWebsite: String,
    trafficIn: Long,
    trafficOut: Long,
    activeConnections: Int,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRestartServer: () -> Unit = {},
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit,
    onOpenPreview: (String) -> Unit,
    onNavigateMenu: (String) -> Unit,
    onOpenOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val thermalManager = remember { DeviceThermalManager(context) }
    DisposableEffect(thermalManager) {
        thermalManager.startMonitoring()
        onDispose { thermalManager.stopMonitoring() }
    }
    val deviceTemperature by thermalManager.temperatureCelsius.collectAsState()
    val deviceThermalLevel by thermalManager.thermalLevel.collectAsState()
    val deviceThermalDesc by thermalManager.thermalStatusDescription.collectAsState()

    var localIp by remember { mutableStateOf(NetworkUtils.getLocalIpAddress()) }
    var ramUsage by remember { mutableStateOf(NetworkUtils.getRamUsageString()) }
    var uptimeMillis by remember { mutableLongStateOf(0L) }
    var totalRequests by remember { mutableLongStateOf(0L) }

    val recentLogs = remember { mutableStateListOf<HttpRequestLog>() }

    LaunchedEffect(isServerOnline) {
        while (true) {
            val server = SfrServerService.currentServerInstance
            if (server != null && server.isRunning) {
                uptimeMillis = server.uptimeMillis
                totalRequests = server.totalRequests
            } else {
                uptimeMillis = 0L
                totalRequests = 0L
            }
            ramUsage = NetworkUtils.getRamUsageString()
            delay(1000)
        }
    }

    LaunchedEffect(isServerOnline) {
        val server = SfrServerService.currentServerInstance
        server?.requestLogs?.collect { log ->
            recentLogs.add(0, log)
            if (recentLogs.size > 20) recentLogs.removeLast()
        }
    }

    val localUrl = "http://$localIp:$serverPort"
    val isPublicOnline = isServerOnline && tunnelState == TunnelState.CONNECTED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 0. Onboarding Guide Header Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenOnboarding() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SfrPrimaryBlue.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SfrPrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Panduan Onboarding SFR Host",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = SfrPrimaryBlue)
                        )
                        Text(
                            text = "Pelajari cara kerja HP sebagai server publik & tunneling",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        )
                    }
                    Text(
                        text = "Pelajari",
                        style = MaterialTheme.typography.labelSmall.copy(color = SfrPrimaryBlue, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // 1. Primary cPanel Hero Status Card
        item {
            ServerHeroCard(
                isServerOnline = isServerOnline,
                tunnelState = tunnelState,
                isPublicOnline = isPublicOnline,
                gatewayHost = gatewayHost,
                tunnelId = tunnelId,
                activeWebsite = activeWebsite,
                port = serverPort,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onRestartServer = onRestartServer,
                onStartTunnel = onStartTunnel,
                onStopTunnel = onStopTunnel
            )
        }

        // 1.5. Thermal Protection Warning Banner (Only appears when hot/critical)
        if (deviceThermalLevel == ThermalLevel.HOT || deviceThermalLevel == ThermalLevel.CRITICAL) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (deviceThermalLevel == ThermalLevel.CRITICAL) StatusOfflineRed.copy(alpha = 0.12f)
                        else StatusWarningAmber.copy(alpha = 0.12f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (deviceThermalLevel == ThermalLevel.CRITICAL) StatusOfflineRed else StatusWarningAmber
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (deviceThermalLevel == ThermalLevel.CRITICAL) StatusOfflineRed else StatusWarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (deviceThermalLevel == ThermalLevel.CRITICAL) "Proteksi Darurat: Suhu HP Kritis!" else "Peringatan: Suhu HP Meningkat",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (deviceThermalLevel == ThermalLevel.CRITICAL) StatusOfflineRed else StatusWarningAmber
                                )
                            )
                            Text(
                                text = deviceThermalDesc,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                }
            }
        }

        // 2. Connected Custom Domains Section
        item {
            ConnectedDomainsCard(
                domains = connectedDomains,
                gatewayHost = gatewayHost,
                isPublicOnline = isPublicOnline,
                onManageDomains = { onNavigateMenu("DOMAINS") },
                onOpenBrowser = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuka browser", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 3. Live Metrics Grid (Total Websites, Requests, Traffic In, Traffic Out, RAM, Connections, Temperature)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        title = "Traffic In",
                        value = NetworkUtils.formatBytes(trafficIn),
                        icon = Icons.Default.ArrowDownward,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Traffic Out",
                        value = NetworkUtils.formatBytes(trafficOut),
                        icon = Icons.Default.ArrowUpward,
                        color = SfrSecondaryOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Total Request",
                        value = totalRequests.toString(),
                        icon = Icons.Default.Speed,
                        color = StatusOnlineGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                val thermalColor = when (deviceThermalLevel) {
                    ThermalLevel.NORMAL -> StatusOnlineGreen
                    ThermalLevel.WARM -> StatusWarningAmber
                    ThermalLevel.HOT -> SfrSecondaryOrange
                    ThermalLevel.CRITICAL -> StatusOfflineRed
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        title = "RAM HP",
                        value = ramUsage.substringBefore(" /"),
                        icon = Icons.Default.Memory,
                        color = Color(0xFFAB47BC),
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Suhu HP",
                        value = if (deviceTemperature > 0f) "${deviceTemperature}°C" else "Normal",
                        icon = Icons.Default.Thermostat,
                        color = thermalColor,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Uptime HP",
                        value = if (isServerOnline) NetworkUtils.formatUptime(uptimeMillis) else "00:00:00",
                        icon = Icons.Default.Schedule,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. cPanel & Cloudflare Control Panel Shortcuts Grid
        item {
            Text(
                text = "Control Panel Suite (cPanel + Cloudflare)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpanelShortcutItem(
                        title = "Websites ($totalWebsitesCount)",
                        icon = Icons.Default.Folder,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("WEBSITES") }
                    )
                    CpanelShortcutItem(
                        title = "File Manager",
                        icon = Icons.Default.Folder,
                        color = SfrSecondaryOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("FILES") }
                    )
                    CpanelShortcutItem(
                        title = "Custom Domain",
                        icon = Icons.Default.Language,
                        color = Color(0xFF26A69A),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("DOMAINS") }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpanelShortcutItem(
                        title = "Tunnel Engine",
                        icon = Icons.Default.Cloud,
                        color = SfrSecondaryOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("TUNNEL") }
                    )
                    CpanelShortcutItem(
                        title = "DNS Manager",
                        icon = Icons.Default.Dns,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("DNS") }
                    )
                    CpanelShortcutItem(
                        title = "SSL / TLS",
                        icon = Icons.Default.Lock,
                        color = StatusOnlineGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("SSL") }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CpanelShortcutItem(
                        title = "Security Guard",
                        icon = Icons.Default.Security,
                        color = Color(0xFFEC407A),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("SECURITY") }
                    )
                    CpanelShortcutItem(
                        title = "Access Logs",
                        icon = Icons.Default.Terminal,
                        color = Color(0xFF7E57C2),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("LOGS") }
                    )
                    CpanelShortcutItem(
                        title = "Analytics",
                        icon = Icons.Default.BarChart,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateMenu("ANALYTICS") }
                    )
                }
            }
        }

        // 5. Public Access Testing Checklist Card
        item {
            PublicTestingCard(
                isPublicOnline = isPublicOnline,
                domainsCount = connectedDomains.size
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun ServerHeroCard(
    isServerOnline: Boolean,
    tunnelState: TunnelState,
    isPublicOnline: Boolean,
    gatewayHost: String,
    tunnelId: String,
    activeWebsite: String,
    port: Int,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onRestartServer: () -> Unit,
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit
) {
    val overallColor by animateColorAsState(
        targetValue = if (isPublicOnline) StatusOnlineGreen else if (isServerOnline) StatusWarningAmber else StatusOfflineRed,
        label = "heroStatus"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STATUS HOSTING HP",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(overallColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPublicOnline) "PUBLIC ONLINE" else if (isServerOnline) "LOCAL ONLY" else "OFFLINE",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = overallColor
                            )
                        )
                    }
                }

                // Gateway & Tunnel Status Badge
                val (tunnelBg, tunnelLabel) = when (tunnelState) {
                    TunnelState.CONNECTED -> StatusOnlineGreen to "TUNNEL ACTIVE"
                    TunnelState.CONNECTING -> StatusWarningAmber to "CONNECTING"
                    TunnelState.RECONNECTING -> StatusWarningAmber to "RECONNECTING"
                    TunnelState.ERROR -> StatusOfflineRed to "ERROR"
                    TunnelState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "TUNNEL OFF"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tunnelBg.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(tunnelLabel, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = tunnelBg, fontSize = 10.sp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Server Metadata Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Gateway: $gatewayHost", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Text("Tunnel ID: ${tunnelId.ifEmpty { "Belum digenerate" }}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = SfrPrimaryBlue))
                    Text("Origin: HP Android Port $port (Situs: $activeWebsite)", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = if (isServerOnline) onStopServer else onStartServer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_server_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerOnline) SfrButtonColors.DangerRed else SfrButtonColors.ActiveGreen
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = if (isServerOnline) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isServerOnline) "STOP SERVER" else "START SERVER", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }

                    if (isServerOnline) {
                        OutlinedButton(
                            onClick = onRestartServer,
                            modifier = Modifier
                                .weight(0.75f)
                                .testTag("restart_server_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SfrButtonColors.WarningOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESTART", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                val isTunnelRunning = tunnelState == TunnelState.CONNECTED || tunnelState == TunnelState.CONNECTING
                Button(
                    onClick = if (isTunnelRunning) onStopTunnel else onStartTunnel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_tunnel_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTunnelRunning) SfrButtonColors.DangerRed else SfrButtonColors.PrimaryBlue
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTunnelRunning) "STOP TUNNEL" else "START TUNNEL", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ConnectedDomainsCard(
    domains: List<CustomDomainItem>,
    gatewayHost: String,
    isPublicOnline: Boolean,
    onManageDomains: () -> Unit,
    onOpenBrowser: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = SfrPrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Domain Terhubung",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Kelola (${domains.size})",
                    style = MaterialTheme.typography.labelMedium.copy(color = SfrPrimaryBlue, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable { onManageDomains() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (domains.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Belum ada domain kustom yang terhubung. Klik 'Kelola' untuk menambahkan domain Anda (misal: tokoku.com).",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
            } else {
                domains.take(3).forEach { d ->
                    val fullUrl = "https://${d.domain}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.domain, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                            Text("Target: ${d.websiteName} • ${d.dnsStatus}", style = MaterialTheme.typography.labelSmall.copy(color = if (d.dnsStatus == "VERIFIED") StatusOnlineGreen else MaterialTheme.colorScheme.outline))
                        }

                        IconButton(onClick = { onOpenBrowser(fullUrl) }) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = "Buka", tint = if (isPublicOnline) SfrPrimaryBlue else MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), maxLines = 1)
            Text(title, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline, fontSize = 10.sp))
        }
    }
}

@Composable
fun CpanelShortcutItem(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PublicTestingCard(
    isPublicOnline: Boolean,
    domainsCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusOnlineGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Daftar Uji Akses Publik (Public Access Checklist)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pastikan website Anda lolos seluruh pengujian mandiri berikut:",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            Spacer(modifier = Modifier.height(8.dp))
            ChecklistRow(text = "1. Buka domain dari HP lain menggunakan data seluler (4G/5G).", passed = isPublicOnline && domainsCount > 0)
            ChecklistRow(text = "2. Buka domain dari laptop / Wi-Fi kantor yang berbeda.", passed = isPublicOnline && domainsCount > 0)
            ChecklistRow(text = "3. Pengunjung dapat mengakses tanpa satu jaringan Wi-Fi.", passed = isPublicOnline)
            ChecklistRow(text = "4. Server HP tetap aktif saat layar mati (Foreground Service).", passed = true)
        }
    }
}

@Composable
fun ChecklistRow(text: String, passed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (passed) StatusOnlineGreen else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
    }
}
