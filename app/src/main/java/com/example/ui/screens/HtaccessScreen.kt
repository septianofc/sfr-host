package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.files.FileManager
import com.example.server.DefaultWebPages
import com.example.server.HtaccessManager
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import java.io.File

@Composable
fun HtaccessScreen(
    activeWebsite: String
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }
    val websiteDir = remember(activeWebsite) { fileManager.getWebsiteDirectory(activeWebsite) }
    val publicHtmlDir = remember(websiteDir) { File(websiteDir, "public_html") }
    val htaccessFile = remember(publicHtmlDir) { File(publicHtmlDir, ".htaccess") }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: .htaccess Editor, 1: Halaman Error Bawaan, 2: Directory Listing
    var editorContent by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var previewStatusCode by remember { mutableIntStateOf(404) }

    fun loadContent() {
        editorContent = if (htaccessFile.exists()) {
            htaccessFile.readText(Charsets.UTF_8)
        } else {
            HtaccessManager.DEFAULT_HTACCESS_CONTENT
        }
    }

    LaunchedEffect(activeWebsite) {
        loadContent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Column {
            Text(
                text = "Error Pages & .htaccess Manager",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SfrPrimaryBlue
            )
            Text(
                text = "Kelola Halaman Error, Direktori Listing, dan Konfigurasi Apache .htaccess",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = SfrPrimaryBlue
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Editor .htaccess", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Halaman Error", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Directory Index", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // .htaccess Editor
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File: ${htaccessFile.relativeTo(fileManager.rootBaseDir).path}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SfrPrimaryBlue
                        )

                        Row {
                            OutlinedButton(
                                onClick = {
                                    val backup = HtaccessManager.backupHtaccess(htaccessFile)
                                    if (backup != null) {
                                        Toast.makeText(context, "Backup dibuat: ${backup.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Belum ada file untuk dibackup.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup", fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = {
                                    val latest = HtaccessManager.getLatestBackup(websiteDir)
                                    if (latest != null) {
                                        editorContent = latest.readText(Charsets.UTF_8)
                                        htaccessFile.writeText(editorContent, Charsets.UTF_8)
                                        Toast.makeText(context, "Berhasil di-restore dari ${latest.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Tidak ada file backup ditemukan.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editorContent,
                        onValueChange = {
                            editorContent = it
                            validationMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        label = { Text("Konfigurasi .htaccess") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                validationMessage = HtaccessManager.validateSyntax(editorContent)
                            }
                        ) {
                            Text("Validasi Sintaks")
                        }

                        Button(
                            onClick = {
                                try {
                                    if (!publicHtmlDir.exists()) publicHtmlDir.mkdirs()
                                    htaccessFile.writeText(editorContent, Charsets.UTF_8)
                                    Toast.makeText(context, "Konfigurasi .htaccess berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan .htaccess")
                        }
                    }

                    validationMessage?.let { (isValid, msg) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isValid) Color(0xFF1B2838) else Color(0xFF3B1E1E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isValid) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isValid) Color(0xFF4CAF50) else Color(0xFFEF5350)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = if (isValid) Color(0xFF81C784) else Color(0xFFE57373)
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Default Error Pages
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Daftar Kode Error SFR Host Bawaan:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Semua kode error dilengkapi logo SFR Host, pesan ramah pengguna, tombol kembali, dan tautan unduh.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val errorCodes = listOf(400, 401, 403, 404, 405, 408, 413, 429, 500, 502, 503, 504)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(400, 403, 404, 500).forEach { code ->
                            val isSel = previewStatusCode == code
                            OutlinedButton(
                                onClick = { previewStatusCode = code },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) SfrPrimaryBlue.copy(alpha = 0.15f) else Color.Transparent
                                )
                            ) {
                                Text("$code", fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Pratinjau Halaman Error HTTP $previewStatusCode", color = SfrPrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("• Menampilkan SVG Logo SFR Host", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text("• Status HTTP $previewStatusCode dengan warna aksen sesuai tingkat error", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text("• Deskripsi berbahasa Indonesia & istilah teknis standar", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text("• Tombol 'Kembali ke Beranda' untuk pengunjung", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Text("• Footer rapi: Powered by SFR Host • Repositori resmi di https://github.com/septianofc/sfr-host", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }

            2 -> {
                // Directory Indexing Guide & Config
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Aturan Directory Indexing", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Jika terdapat 'index.php', server langsung mengeksekusi PHP.\n" +
                                       "2. Jika tidak ada, server mencari 'index.html', lalu 'index.htm'.\n" +
                                       "3. Jika tidak ada file index sama sekali, server menampilkan Directory Listing profesional dengan breadcrumb dan filter pencarian.\n" +
                                       "4. File rahasia (.env, .git, .sqlite, .db, .htaccess) otomatis disembunyikan dari pengunjung.",
                                fontSize = 12.sp,
                                lineHeight = 1.8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Untuk Mematikan Directory Listing:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "Tambahkan baris berikut di tab Editor .htaccess:\nOptions -Indexes",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = SfrPrimaryBlue
                            )
                        }
                    }
                }
            }
        }
    }
}
