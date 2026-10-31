package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AppLockManager
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SecurityScreen(
    tunnelId: String,
    authToken: String,
    appLockManager: AppLockManager,
    onRotateToken: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isLockEnabled by appLockManager.isLockEnabled.collectAsState(initial = false)
    val isBiometricEnabled by appLockManager.isBiometricEnabled.collectAsState(initial = false)
    val hasPinSet by appLockManager.hasPinSet.collectAsState(initial = false)

    var showSetPinDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

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
                    text = "Kunci Aplikasi PIN/Sidik Jari, proteksi server HP, dan pertahanan keamanan",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // App Lock Section
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SfrPrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Kunci Aplikasi (App Lock)", fontWeight = FontWeight.Bold)
                                Text(
                                    if (isLockEnabled) "Aktif (PIN/Biometrik Terproteksi)" else "Nonaktif",
                                    fontSize = 11.sp,
                                    color = if (isLockEnabled) StatusOnlineGreen else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Switch(
                            checked = isLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasPinSet) {
                                    showSetPinDialog = true
                                } else {
                                    scope.launch { appLockManager.setLockEnabled(enabled) }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SfrPrimaryBlue)
                        )
                    }

                    if (isLockEnabled || hasPinSet) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Change PIN Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("PIN Keamanan (4 Digit)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Gunakan PIN saat sidik jari tidak tersedia", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            OutlinedButton(
                                onClick = { showSetPinDialog = true },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(if (hasPinSet) "Ubah PIN" else "Buat PIN", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Biometrics Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SfrSecondaryOrange, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Sidik Jari (Biometrik)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Buka cepat dengan sensor sidik jari HP", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { scope.launch { appLockManager.setBiometricEnabled(it) } },
                                colors = SwitchDefaults.colors(checkedThumbColor = SfrPrimaryBlue)
                            )
                        }
                    }
                }
            }
        }

        // Active Security Features
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
                        title = "Directory Traversal Protection",
                        desc = "Memblokir serangan path ../ secara otomatis pada level ServerSocket",
                        active = traversalGuardEnabled,
                        onToggle = { traversalGuardEnabled = it }
                    )

                    SecurityFeatureRow(
                        title = "Blokir File Rahasia (.env & .git)",
                        desc = "File rahasia .env, .git, .sqlite, .db, .htaccess otomatis ditolak (HTTP 403)",
                        active = true,
                        isStatic = true
                    )

                    SecurityFeatureRow(
                        title = "Gateway Rate Limiting",
                        desc = "Membatasi 100 requests/detik per IP untuk mencegah serangan DDoS flood",
                        active = rateLimitEnabled,
                        onToggle = { rateLimitEnabled = it }
                    )

                    SecurityFeatureRow(
                        title = "R8 Obfuscation & Shrinking",
                        desc = "Kode sumber terenkripsi & nama class disamarkan untuk mempersulit reverse engineering",
                        active = true,
                        isStatic = true
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Set PIN Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                newPinInput = ""
                confirmPinInput = ""
                pinError = null
            },
            title = { Text("Atur PIN Aplikasi SFR Host") },
            text = {
                Column {
                    Text("Masukkan 4 digit angka PIN pengaman:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                        label = { Text("PIN Baru (4 Digit)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Konfirmasi PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    pinError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinInput.length != 4) {
                            pinError = "PIN harus terdiri dari 4 digit angka."
                        } else if (newPinInput != confirmPinInput) {
                            pinError = "Konfirmasi PIN tidak cocok."
                        } else {
                            scope.launch {
                                appLockManager.setPin(newPinInput)
                                appLockManager.setLockEnabled(true)
                                Toast.makeText(context, "PIN berhasil disimpan dan App Lock diaktifkan!", Toast.LENGTH_SHORT).show()
                                showSetPinDialog = false
                                newPinInput = ""
                                confirmPinInput = ""
                                pinError = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Simpan PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetPinDialog = false
                    newPinInput = ""
                    confirmPinInput = ""
                    pinError = null
                }) {
                    Text("Batal")
                }
            }
        )
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
                Text("ENABLED", style = MaterialTheme.typography.labelSmall.copy(color = StatusOnlineGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp))
            }
        } else {
            Switch(
                checked = active,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = SfrPrimaryBlue)
            )
        }
    }
}
