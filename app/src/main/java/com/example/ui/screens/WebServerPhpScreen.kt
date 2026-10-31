package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DataStoreManager
import com.example.files.FileManager
import com.example.server.php.PhpExecutionEngine
import com.example.server.php.PhpExecutionResult
import com.example.ui.theme.SfrButtonColors
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WebServerPhpScreen(
    isServerOnline: Boolean,
    serverPort: Int,
    activeWebsite: String,
    onRestartServer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = remember { DataStoreManager(context) }
    val fileManager = remember { FileManager(context) }
    val websiteDir = remember(activeWebsite) { fileManager.getWebsiteDirectory(activeWebsite) }

    val currentPhpVersion by dataStore.phpVersionProfile.collectAsState(initial = "8.2")
    val currentMemoryLimit by dataStore.phpMemoryLimit.collectAsState(initial = "256M")
    val currentUploadMax by dataStore.phpUploadMax.collectAsState(initial = "64M")
    val currentDisplayErrors by dataStore.phpDisplayErrors.collectAsState(initial = false)

    val phpEngine = remember(websiteDir, currentPhpVersion, currentMemoryLimit, currentUploadMax, currentDisplayErrors) {
        PhpExecutionEngine(
            documentRoot = File(websiteDir, "public_html"),
            versionProfile = currentPhpVersion,
            memoryLimit = currentMemoryLimit,
            uploadMaxFilesize = currentUploadMax,
            displayErrors = currentDisplayErrors
        )
    }

    var phpTestCode by remember {
        mutableStateOf("""<?php
echo "<h1>SFR Host PHP Berhasil!</h1>";
echo "<p>PHP Version: " . PHP_VERSION . "</p>";
echo "<p>memory_limit: " . ini_get('memory_limit') . "</p>";
echo "<p>Server Time: " . date("Y-m-d H:i:s") . "</p>";
?>""")
    }

    var phpTestResult by remember { mutableStateOf<PhpExecutionResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Web Server & PHP Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SfrPrimaryBlue
                )
                Text(
                    text = "Status Runtime Server HP & Eksekusi Skrip PHP Native",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRestartServer) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart Server")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Web Server Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Konfigurasi Web Server", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isServerOnline) SfrButtonColors.ActiveGreen else SfrButtonColors.DangerRed,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isServerOnline) "ONLINE" else "STOPPED",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Port Server", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("$serverPort (HTTP)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Website Aktif", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(activeWebsite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Prioritas Default Index", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("1. index.php  2. index.html  3. index.htm", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = SfrPrimaryBlue)
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Metode HTTP Didukung", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("GET, POST, HEAD, OPTIONS", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PHP Runtime Information & php.ini Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = SfrSecondaryOrange)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PHP Runtime & Profil php.ini", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider()
                Spacer(modifier = Modifier.height(10.dp))

                // PHP Version Profile Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Versi Profil PHP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Pilih dialek versi runtime PHP", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    var versionMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { versionMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrButtonColors.PrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("PHP $currentPhpVersion", fontSize = 12.sp, color = Color.White)
                        }

                        DropdownMenu(
                            expanded = versionMenuExpanded,
                            onDismissRequest = { versionMenuExpanded = false }
                        ) {
                            listOf("7.4", "8.0", "8.1", "8.2", "8.3").forEach { ver ->
                                DropdownMenuItem(
                                    text = { Text("PHP $ver") },
                                    onClick = {
                                        scope.launch { dataStore.setPhpVersionProfile(ver) }
                                        versionMenuExpanded = false
                                        Toast.makeText(context, "Profil PHP diatur ke $ver", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Memory Limit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("memory_limit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    var memMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { memMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(currentMemoryLimit, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = memMenuExpanded,
                            onDismissRequest = { memMenuExpanded = false }
                        ) {
                            listOf("128M", "256M", "512M", "1024M").forEach { limit ->
                                DropdownMenuItem(
                                    text = { Text(limit) },
                                    onClick = {
                                        scope.launch { dataStore.setPhpMemoryLimit(limit) }
                                        memMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Upload Max Filesize
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("upload_max_filesize", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    var uploadMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { uploadMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(currentUploadMax, fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = uploadMenuExpanded,
                            onDismissRequest = { uploadMenuExpanded = false }
                        ) {
                            listOf("16M", "32M", "64M", "128M", "256M").forEach { up ->
                                DropdownMenuItem(
                                    text = { Text(up) },
                                    onClick = {
                                        scope.launch { dataStore.setPhpUploadMax(up) }
                                        uploadMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Display Errors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("display_errors", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("Tampilkan error sintaks ke output", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = currentDisplayErrors,
                        onCheckedChange = { checked ->
                            scope.launch { dataStore.setPhpDisplayErrors(checked) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SfrButtonColors.PrimaryBlue)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Engine Mode", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(
                        if (phpEngine.isBinaryAvailable) "PHP CLI / CGI Binary" else "Android Native PHP Engine",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ekstensi Aktif", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("SQLite3, PDO_SQLite, JSON, Date, Hash, Core", fontWeight = FontWeight.Normal, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive PHP Tester
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = SfrPrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uji Skrip PHP Secara Langsung", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text(
                    text = "Jalankan skrip PHP untuk memverifikasi eksekusi server HP:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phpTestCode,
                    onValueChange = { phpTestCode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    label = { Text("Skrip PHP (<?php ... ?>)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        isExecuting = true
                        scope.launch {
                            val tempFile = File(context.cacheDir, "sfr_test_${System.currentTimeMillis()}.php")
                            tempFile.writeText(phpTestCode, Charsets.UTF_8)

                            val res = withContext(Dispatchers.IO) {
                                phpEngine.execute(
                                    phpFile = tempFile,
                                    method = "GET",
                                    clientIp = "127.0.0.1",
                                    serverPort = serverPort
                                )
                            }
                            tempFile.delete()
                            phpTestResult = res
                            isExecuting = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrButtonColors.PrimaryBlue),
                    enabled = !isExecuting
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isExecuting) "Mengeksekusi..." else "Jalankan PHP Test", color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PHP Output Card
                phpTestResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.statusCode == 200) Color(0xFF0F172A) else Color(0xFF3B1E1E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Status: HTTP ${res.statusCode} (${res.executionTimeMs} ms)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (res.statusCode == 200) Color(0xFF4CAF50) else Color(0xFFEF5350)
                                )
                                Text(
                                    text = res.headers["Content-Type"] ?: "text/html",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 6.dp))

                            Text(
                                text = "Hasil Output (Rendered HTML):",
                                fontSize = 11.sp,
                                color = SfrPrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.body.ifEmpty { "(Tidak ada output/string kosong)" },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }
    }
}
