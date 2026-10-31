package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.tunnel.InstallStage
import com.example.tunnel.TunnelLog
import com.example.tunnel.TunnelState
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.ui.theme.StatusWarningAmber
import kotlinx.coroutines.launch
import java.text.DecimalFormat
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
    var showSetupGuide by remember { mutableStateOf(false) }

    val downloadProgress by runner.downloadProgress.collectAsState()
    val tunnelLogs = remember { mutableStateListOf<TunnelLog>() }

    LaunchedEffect(cloudflareToken) { tokenInput = cloudflareToken }
    LaunchedEffect(customDomain) { domainInput = customDomain ?: "" }

    // Custom Binary Import Launcher
    val binaryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val result = runner.importCustomBinary(stream)
                    if (result.first) {
                        Toast.makeText(context, "Binary kustom berhasil dipasang & terverifikasi!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Gagal uji binary: ${result.second}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Stream logs
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
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(2.dp)) }

        // 1. Connection Status Card
        item {
            val (statusColor, statusText, statusDesc) = when (tunnelState) {
                TunnelState.CONNECTED -> Triple(StatusOnlineGreen, "TERHUBUNG KE CLOUDFLARE EDGE", "Tunnel resmi cloudflared aktif. Website HP kini dapat diakses publik melalui domain Cloudflare.")
                TunnelState.CONNECTING -> Triple(StatusWarningAmber, "MENGHUBUNGKAN CLOUDFLARED...", "Memulai proses native cloudflared dan melakukan autentikasi ke Cloudflare Edge.")
                TunnelState.RECONNECTING -> Triple(StatusWarningAmber, "RECONNECTING...", "Koneksi terputus, mencoba menyambung kembali ke Cloudflare Edge.")
                TunnelState.ERROR -> Triple(StatusOfflineRed, "GAGAL / ERROR", "Koneksi tunnel gagal. Periksa token Cloudflare atau uji kompatibilitas binary.")
                TunnelState.DISCONNECTED -> Triple(MaterialTheme.colorScheme.outline, "TERPUTUS (OFFLINE)", "Cloudflare Tunnel belum dijalankan.")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CLOUDFLARE ARGO TUNNEL",
                                style = MaterialTheme.typography.labelSmall.copy(
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

                        // Connect / Disconnect button with clear color hierarchy
                        if (isTunnelActive) {
                            Button(
                                onClick = onStopTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Putuskan", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = onStartTunnel,
                                colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hubungkan", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusDesc,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

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

        // 2. Real Download & 9-Stage Installation Pipeline Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (downloadProgress.isRunning) MaterialTheme.colorScheme.surface
                    else if (runner.isBinaryInstalled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    else SfrSecondaryOrange.copy(alpha = 0.08f)
                ),
                border = if (!runner.isBinaryInstalled && !downloadProgress.isRunning) {
                    androidx.compose.foundation.BorderStroke(1.dp, SfrSecondaryOrange.copy(alpha = 0.6f))
                } else null
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pemasangan Binary Cloudflare", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Arsitektur terdeteksi: ${runner.detectedAbi}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (!downloadProgress.isRunning) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { binaryPickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Impor", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        scope.launch { runner.executeInstallPipeline() }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (runner.isBinaryInstalled) "Unduh Ulang" else "Pasang", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Active Download & Pipeline Details
                    if (downloadProgress.isRunning || downloadProgress.error != null || downloadProgress.isCompleted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Stage Indicator
                        Text(
                            text = "Tahap ${downloadProgress.stage.stepNumber}/9: ${downloadProgress.stage.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = SfrPrimaryBlue
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Real Progress Bar
                        if (downloadProgress.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SfrPrimaryBlue
                            )
                        } else if (downloadProgress.isRunning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = SfrPrimaryBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Real Download Stats: Progress %, Downloaded MB/Total MB, Speed MB/s, ETA
                        val df = DecimalFormat("#.##")
                        val speedMb = df.format(downloadProgress.speedBytesPerSec / (1024 * 1024))
                        val downloadedMb = df.format(downloadProgress.bytesDownloaded.toDouble() / (1024 * 1024))
                        val totalMb = if (downloadProgress.totalBytes > 0) df.format(downloadProgress.totalBytes.toDouble() / (1024 * 1024)) else "?"

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Progres: ${downloadProgress.progressPercent}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("$downloadedMb MB / $totalMb MB", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            if (downloadProgress.speedBytesPerSec > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Kecepatan: $speedMb MB/s", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    if (downloadProgress.etaSeconds > 0) {
                                        Text("Estimasi: ${downloadProgress.etaSeconds} detik", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        downloadProgress.message.takeIf { it.isNotEmpty() }?.let { msg ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = msg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        downloadProgress.error?.let { err ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Error: $err", fontSize = 11.sp, color = StatusOfflineRed, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { scope.launch { runner.executeInstallPipeline() } },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Coba Lagi", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = { binaryPickerLauncher.launch("*/*") },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Impor Binary Kompatibel", fontSize = 11.sp)
                                }
                            }
                        }

                        if (downloadProgress.isRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { runner.cancelDownload() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Batal Download", fontSize = 11.sp)
                            }
                        }
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pengaturan Token Cloudflare Tunnel",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cloudflare Tunnel Token Input
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Cloudflare Tunnel Token (Remotely-Managed)") },
                        placeholder = { Text("eyJh... (Token dari Cloudflare Zero Trust)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloudflare_token_input"),
                        singleLine = false,
                        maxLines = 3,
                        visualTransformation = if (showTokenText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showTokenText = !showTokenText }) {
                                Icon(
                                    imageVector = if (showTokenText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showTokenText) "Sembunyikan Token" else "Tampilkan Token"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Validate Token helper
                    val (isValidToken, tokenValidationMsg) = runner.validateToken(tokenInput)
                    if (tokenInput.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isValidToken) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isValidToken) StatusOnlineGreen else StatusOfflineRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tokenValidationMsg,
                                fontSize = 11.sp,
                                color = if (isValidToken) StatusOnlineGreen else StatusOfflineRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Custom Domain Input
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Custom Domain Milik Anda (Opsional)") },
                        placeholder = { Text("website-saya.com") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_domain_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto Reconnect Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sambungkan Ulang Otomatis", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Reconnect otomatis saat koneksi terputus", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = onToggleAutoReconnect,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SfrPrimaryBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onSaveToken(tokenInput)
                            onSaveCustomDomain(domainInput)
                            Toast.makeText(context, "Konfigurasi token berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Simpan Konfigurasi")
                    }
                }
            }
        }

        // 4. Live Cloudflared Diagnostic Logs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Cloudflared Output Log",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${tunnelLogs.size} baris",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F141C))
                            .padding(10.dp)
                    ) {
                        if (tunnelLogs.isEmpty()) {
                            Text(
                                text = "Menunggu log proses cloudflared...",
                                color = Color(0xFF6B7280),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            LazyColumn(reverseLayout = true) {
                                items(tunnelLogs.reversed()) { log ->
                                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                    Text(
                                        text = "[$time] ${log.message}",
                                        color = if (log.isError) Color(0xFFF87171) else Color(0xFF4ADE80),
                                        fontSize = 10.sp,
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
