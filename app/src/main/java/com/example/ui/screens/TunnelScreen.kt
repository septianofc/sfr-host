package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.SfrServerService
import com.example.tunnel.TunnelLog
import com.example.tunnel.TunnelState
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TunnelScreen(
    tunnelState: TunnelState,
    publicUrl: String?,
    customDomain: String?,
    cloudflareToken: String,
    autoReconnect: Boolean,
    onSaveToken: (String) -> Unit,
    onSaveCustomDomain: (String) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onStartTunnel: () -> Unit,
    onStopTunnel: () -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf(cloudflareToken) }
    var domainInput by remember { mutableStateOf(customDomain ?: "") }
    var showDnsGuide by remember { mutableStateOf(false) }

    val tunnelLogs = remember { mutableStateListOf<TunnelLog>() }

    LaunchedEffect(cloudflareToken) { tokenInput = cloudflareToken }
    LaunchedEffect(customDomain) { domainInput = customDomain ?: "" }

    // Stream logs from current tunnel instance
    LaunchedEffect(Unit) {
        val tunnel = SfrServerService.currentTunnelInstance
        tunnel?.logs?.collect { log ->
            tunnelLogs.add(log)
            if (tunnelLogs.size > 100) tunnelLogs.removeAt(0)
        }
    }

    val isTunnelActive = tunnelState == TunnelState.CONNECTED || tunnelState == TunnelState.CONNECTING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Connection Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "STATUS TUNNELING",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            val (statusColor, statusTitle) = when (tunnelState) {
                                TunnelState.CONNECTED -> StatusOnlineGreen to "CONNECTED"
                                TunnelState.CONNECTING -> StatusWarningAmber to "CONNECTING..."
                                TunnelState.RECONNECTING -> StatusWarningAmber to "RECONNECTING..."
                                TunnelState.ERROR -> StatusOfflineRed to "ERROR"
                                TunnelState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "DISCONNECTED"
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = statusColor
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = if (isTunnelActive) onStopTunnel else onStartTunnel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTunnelActive) StatusOfflineRed else SfrSecondaryOrange
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("tunnel_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isTunnelActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTunnelActive) "STOP" else "START")
                        }
                    }

                    if (publicUrl != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Public HTTPS Endpoint:",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = publicUrl,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = SfrPrimaryBlue
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Public URL", publicUrl))
                                            Toast.makeText(context, "URL disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = SfrPrimaryBlue)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Configuration Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Konfigurasi Tunnel & Domain",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloudflare Tunnel Token
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            onSaveToken(it)
                        },
                        label = { Text("Cloudflare Tunnel Token (Opsional)") },
                        placeholder = { Text("eyJh... (Token dari Cloudflare Zero Trust)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SfrSecondaryOrange)
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Jika token kosong, SFR Host akan menggunakan Public Reverse Tunnel Gateway bawaan secara otomatis.",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Domain
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = {
                            domainInput = it
                            onSaveCustomDomain(it)
                        },
                        label = { Text("Custom Domain (Opsional)") },
                        placeholder = { Text("situs.domainanda.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = SfrPrimaryBlue)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Reconnect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Auto Reconnect",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Otomatis sambung ulang saat jaringan terputus",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                            )
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = onToggleAutoReconnect,
                            colors = SwitchDefaults.colors(checkedThumbColor = SfrPrimaryBlue)
                        )
                    }
                }
            }
        }

        // 3. DNS Configuration Guide (Expandable)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDnsGuide = !showDnsGuide },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SfrPrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Panduan DNS & Custom Domain",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Icon(
                            imageVector = if (showDnsGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (showDnsGuide) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DnsStepItem(
                            stepNumber = "1",
                            title = "Miliki Domain di Cloudflare",
                            desc = "Arahkan Name Server domain Anda ke Cloudflare agar DNS dapat dikelola secara otomatis."
                        )
                        DnsStepItem(
                            stepNumber = "2",
                            title = "Buat Cloudflare Tunnel",
                            desc = "Buka Cloudflare Zero Trust Dashboard -> Networks -> Tunnels -> Add a Tunnel -> Pilih Cloudflared. Salin Tunnel Token yang diberikan."
                        )
                        DnsStepItem(
                            stepNumber = "3",
                            title = "Tambahkan Public Hostname / CNAME Record",
                            desc = "Tambahkan Public Hostname (misal: 'website.domain.com') dengan Service 'HTTP' dan URL 'localhost:8080'. Proxy Status aktif (Awan Oranye) untuk SSL otomatis."
                        )
                        DnsStepItem(
                            stepNumber = "4",
                            title = "Hubungkan di SFR Host",
                            desc = "Tempel Token di kotak atas, isi Custom Domain Anda, lalu tekan 'START TUNNEL'. Website Anda langsung online di seluruh dunia!"
                        )
                    }
                }
            }
        }

        // 4. Live Tunnel Console Logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = SfrSecondaryOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tunnel Console Logs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (tunnelLogs.isNotEmpty()) {
                    TextButton(onClick = { tunnelLogs.clear() }) {
                        Text("Bersihkan", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14171A))
            ) {
                if (tunnelLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTunnelActive) "Menghubungkan tunnel..." else "Tunnel tidak aktif. Log akan muncul saat tunnel berjalan.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF657786),
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = true
                    ) {
                        items(tunnelLogs.reversed()) { log ->
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                            Text(
                                text = "[$timeStr] ${log.message}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (log.isError) StatusOfflineRed else Color(0xFF00E676)
                                )
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun DnsStepItem(stepNumber: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SfrPrimaryBlue)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}
