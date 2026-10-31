package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DataStoreManager
import com.example.security.AppLockManager
import com.example.ui.theme.SfrButtonColors
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    tunnelId: String,
    authToken: String,
    appLockManager: AppLockManager,
    onRotateToken: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { DataStoreManager(context) }

    val isLockEnabled by appLockManager.isLockEnabled.collectAsState(initial = false)
    val isDeviceSecure = remember { appLockManager.isDeviceSecure() }
    val screenshotProtection by dataStore.screenshotProtection.collectAsState(initial = false)
    val lockTimeoutMode by dataStore.lockTimeoutMode.collectAsState(initial = "IMMEDIATELY")

    var rateLimitEnabled by remember { mutableStateOf(true) }
    var traversalGuardEnabled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Header
        item {
            Column {
                Text(
                    text = "Security & App Lock Panel",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SfrPrimaryBlue
                )
                Text(
                    text = "Keamanan bawaan Android (Sidik Jari / PIN / Pola HP) dan proteksi server",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // App Lock Section (100% Native Android Device Credentials)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SfrPrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SfrPrimaryBlue, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Kunci Aplikasi (App Lock)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (isLockEnabled) "Aktif (Sidik Jari & Kunci Layar HP)" else "Nonaktif",
                                    fontSize = 11.sp,
                                    color = if (isLockEnabled) StatusOnlineGreen else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Switch(
                            checked = isLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !appLockManager.isDeviceSecure()) {
                                    Toast.makeText(context, "Perangkat belum memiliki Kunci Layar. Aktifkan PIN/Pola di pengaturan HP terlebih dahulu.", Toast.LENGTH_LONG).show()
                                } else {
                                    scope.launch { appLockManager.setLockEnabled(enabled) }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SfrPrimaryBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Proteksi Layar (FLAG_SECURE Anti-Screenshot)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SfrPrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = SfrPrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Proteksi Layar (FLAG_SECURE)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Cegah screenshot & rekaman layar HP", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Switch(
                            checked = screenshotProtection,
                            onCheckedChange = { checked ->
                                scope.launch { dataStore.setScreenshotProtection(checked) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SfrButtonColors.PrimaryBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Waktu Kunci Otomatis (Timeout)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SfrPrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = SfrPrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Waktu Kunci Otomatis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Kunci ulang setelah aplikasi ditinggalkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        var timeoutMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { timeoutMenuExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                val label = when (lockTimeoutMode) {
                                    "IMMEDIATELY" -> "Segera"
                                    "1M" -> "1 Menit"
                                    "5M" -> "5 Menit"
                                    "10M" -> "10 Menit"
                                    "SCREEN_OFF" -> "Layar Mati"
                                    else -> lockTimeoutMode
                                }
                                Text(label, fontSize = 11.sp)
                            }

                            DropdownMenu(
                                expanded = timeoutMenuExpanded,
                                onDismissRequest = { timeoutMenuExpanded = false }
                            ) {
                                listOf(
                                    "IMMEDIATELY" to "Segera (Saat keluar)",
                                    "1M" to "1 Menit",
                                    "5M" to "5 Menit",
                                    "10M" to "10 Menit",
                                    "SCREEN_OFF" to "Saat Layar HP Mati"
                                ).forEach { (mode, title) ->
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = {
                                            scope.launch { dataStore.setLockTimeoutMode(mode) }
                                            timeoutMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Server Protection Features
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fitur Proteksi Server Aktif",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SecurityFeatureRow(
                        title = "Directory Traversal Guard",
                        desc = "Memblokir serangan path traversal ../ pada level HTTP server",
                        active = traversalGuardEnabled,
                        onToggle = { traversalGuardEnabled = it }
                    )

                    SecurityFeatureRow(
                        title = "Blokir Berkas Sensitif",
                        desc = "Akses ke .env, .git, .sqlite, .db, .htaccess otomatis diblokir (HTTP 403 Forbidden)",
                        active = true,
                        isStatic = true
                    )

                    SecurityFeatureRow(
                        title = "Gateway Rate Limiting",
                        desc = "Membatasi 100 requests/detik per IP untuk mencegah request flooding",
                        active = rateLimitEnabled,
                        onToggle = { rateLimitEnabled = it }
                    )

                    SecurityFeatureRow(
                        title = "R8 Obfuscation & Shrinking",
                        desc = "Kode sumber diproteksi R8 untuk keamanan binary APK",
                        active = true,
                        isStatic = true
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun SecurityFeatureRow(
    title: String,
    desc: String,
    active: Boolean,
    isStatic: Boolean = false,
    onToggle: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(desc, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline))
        }

        if (isStatic) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StatusOnlineGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                Text(
                    "AKTIF",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StatusOnlineGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        } else {
            Switch(
                checked = active,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SfrPrimaryBlue
                )
            )
        }
    }
}
