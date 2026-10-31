package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.server.SfrServerService
import com.example.tunnel.CloudflareTunnelRunner
import com.example.tunnel.TunnelLog
import com.example.tunnel.TunnelState
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val runner = remember { CloudflareTunnelRunner(context, scope) }

    var tokenInput by remember { mutableStateOf(cloudflareToken) }
    var domainInput by remember { mutableStateOf(customDomain ?: "") }
    var showTokenText by remember { mutableStateOf(false) }
    var isInstallingBinary by remember { mutableStateOf(false) }
    var binaryInstallMessage by remember { mutableStateOf<String?>(null) }
    var showSetupGuide by remember { mutableStateOf(false) }

    val tunnelLogs = remember { mutableStateListOf<TunnelLog>() }

    LaunchedEffect(cloudflareToken) { tokenInput = cloudflareToken }
    LaunchedEffect(customDomain) { domainInput = customDomain ?: "" }

    // Stream logs from runner
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
            val (statusColor, statusText, statusDesc) = when (tunnelState) {
                TunnelState.CONNECTED -> Triple(StatusOnlineGreen, "TERHUBUNG KE CLOUDFLARE EDGE", "Tunnel resmi cloudflared aktif. Trafik internet diteruskan langsung ke web server HP.")
                TunnelState.CONNECTING -> Triple(StatusWarningAmber, "MENGHUBUNGKAN CLOUDFLARED...", "Memulai proses native cloudflared dan melakukan autentikasi ke Cloudflare Edge.")
                TunnelState.RECONNECTING -> Triple(StatusWarningAmber, "RECONNECTING...", "Koneksi terputus, mencoba menyambung kembali ke Cloudflare Edge.")
                TunnelState.ERROR -> Triple(StatusOfflineRed, "GAGAL / ERROR", "Koneksi tunnel gagal. Periksa validitas token Cloudflare atau log diagnostik.")
                TunnelState.DISCONNECTED -> Triple(MaterialTheme.colorScheme.outline, "TERPUTUS (OFFLINE)", "Cloudflare Tunnel belum dijalankan.")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
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
                                text = "CLOUDFLARE ARGO TUNNEL",
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
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                )
                            }
                        }

                        // Start / Stop Button with clean visual hierarchy
                        if (isTunnelActive) {
                            Button(
                                onClick = onStopTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Putuskan")
                            }
                        } else {
                            Button(
                                onClick = onStartTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hubungkan")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    // Origin details
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Local Origin HP", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text("http://127.0.0.1:8080", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Binary cloudflared", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            if (runner.isBinaryInstalled) "Terpasang (${runner.detectedAbi})" else "Belum Terpasang",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (runner.isBinaryInstalled) StatusOnlineGreen else StatusOfflineRed
                        )
                    }
                }
            }
        }

        // 2. Binary Installation Card (If binary not installed or update requested)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (runner.isBinaryInstalled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else SfrSecondaryOrange.copy(alpha = 0.1f)
                ),
                border = if (!runner.isBinaryInstalled) androidx.compose.foundation.BorderStroke(1.dp, SfrSecondaryOrange) else null
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Engine Runtime Cloudflared", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Binary resmi cloudflared untuk arsitektur ${runner.detectedAbi}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                isInstallingBinary = true
                                scope.launch {
                                    val success = runner.downloadOrInstallBinary { msg ->
                                        binaryInstallMessage = msg
                                    }
                                    isInstallingBinary = false
                                    if (success) {
                                        Toast.makeText(context, "Binary cloudflared siap digunakan!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isInstallingBinary
                        ) {
                            if (isInstallingBinary) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (runner.isBinaryInstalled) "Perbarui Binary" else "Pasang Binary", fontSize = 11.sp)
                            }
                        }
                    }

                    binaryInstallMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = msg, fontSize = 11.sp, color = SfrPrimaryBlue)
                    }
                }
            }
        }

        // 3. Cloudflare Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Pengaturan Token Cloudflare Tunnel",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloudflare Tunnel Token
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                        },
                        label = { Text("Cloudflare Tunnel Token (Remotely-Managed)") },
                        placeholder = { Text("eyJh... (Token dari Cloudflare Zero Trust)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showTokenText) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SfrSecondaryOrange)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showTokenText = !showTokenText }) {
                                Icon(
                                    imageVector = if (showTokenText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Token dilindungi dan tidak ditampilkan di Logcat.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Button(
                            onClick = {
                                onSaveToken(tokenInput.trim())
                                Toast.makeText(context, "Token Cloudflare berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                        ) {
                            Text("Simpan Token", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(10.dp))

                    // Auto Reconnect Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Reconnect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Sambungkan ulang otomatis jika koneksi internet HP terputus", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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

        // 4. Live Tunnel Logs Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = SfrPrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log Koneksi Cloudflared", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { tunnelLogs.clear() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Bersihkan Log", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(10.dp)
                    ) {
                        if (tunnelLogs.isEmpty()) {
                            Text(
                                text = "Menunggu log koneksi dari cloudflared...\nKlik 'Hubungkan' setelah mengisi token untuk memulai.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            LazyColumn {
                                items(tunnelLogs) { log ->
                                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                    Text(
                                        text = "[$time] ${log.message}",
                                        color = if (log.isError) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
