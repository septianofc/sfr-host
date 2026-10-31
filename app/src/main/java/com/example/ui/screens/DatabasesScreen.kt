package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.data.SfrDatabaseItem
import com.example.data.SqlQueryResult
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DatabasesScreen(
    databaseManager: DatabaseManager
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var databasesList by remember { mutableStateOf<List<SfrDatabaseItem>>(emptyList()) }
    var selectedDb by remember { mutableStateOf<SfrDatabaseItem?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Databases, 1: SQL Query, 2: PHP Connection, 3: MySQL / Remote

    var showCreateDbDialog by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<SfrDatabaseItem?>(null) }

    // SQL Runner State
    var sqlQueryInput by remember { mutableStateOf("SELECT * FROM articles LIMIT 10;") }
    var queryResult by remember { mutableStateOf<SqlQueryResult?>(null) }
    var isRunningQuery by remember { mutableStateOf(false) }

    // Import / Export State
    var showExportDialog by remember { mutableStateOf(false) }
    var exportDumpText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importSqlText by remember { mutableStateOf("") }

    fun refreshList() {
        databasesList = databaseManager.listDatabases()
        if (selectedDb == null || !databasesList.any { it.name == selectedDb?.name }) {
            selectedDb = databasesList.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "cPanel Database Management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SfrPrimaryBlue
                )
                Text(
                    text = "Engine SQLite Native Android & Panduan MariaDB/MySQL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = { refreshList() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang")
                }
                Button(
                    onClick = { showCreateDbDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                    modifier = Modifier.testTag("create_db_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buat DB")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // cPanel Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = SfrPrimaryBlue
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Daftar DB (${databasesList.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("SQL Runner", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Koneksi PHP", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("MySQL / Remote", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // List of Databases
                if (databasesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada database. Klik 'Buat DB' untuk membuat database SQLite baru.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(databasesList) { item ->
                            val isSelected = selectedDb?.name == item.name
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) SfrPrimaryBlue else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) SfrPrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = if (isSelected) SfrPrimaryBlue else SfrSecondaryOrange,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${formatBytes(item.sizeBytes)} • ${item.tableCount} Tabel • SQLite Engine",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row {
                                        OutlinedButton(
                                            onClick = {
                                                selectedDb = item
                                                selectedTab = 1
                                            },
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Text("Kelola", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { showDeleteConfirmDialog = item },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // SQL Query Runner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Database Aktif: ${selectedDb?.name ?: "Pilih Database"}",
                        fontWeight = FontWeight.SemiBold,
                        color = SfrPrimaryBlue
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sqlQueryInput,
                        onValueChange = { sqlQueryInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        label = { Text("Perintah SQL") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            Button(
                                onClick = {
                                    val dbName = selectedDb?.name
                                    if (dbName != null) {
                                        isRunningQuery = true
                                        scope.launch {
                                            queryResult = withContext(Dispatchers.IO) {
                                                databaseManager.executeQuery(dbName, sqlQueryInput)
                                            }
                                            isRunningQuery = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                                enabled = !isRunningQuery && selectedDb != null
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Jalankan")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val dbName = selectedDb?.name ?: return@OutlinedButton
                                    scope.launch {
                                        val dump = withContext(Dispatchers.IO) {
                                            databaseManager.exportToSql(dbName)
                                        }
                                        exportDumpText = dump
                                        showExportDialog = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ekspor SQL")
                            }
                        }

                        OutlinedButton(
                            onClick = { showImportDialog = true }
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Impor SQL")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Query Execution Result
                    queryResult?.let { res ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.isSuccess) Color(0xFF1B2838) else Color(0xFF3B1E1E)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = res.message + " (${res.executionTimeMs} ms)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isSuccess) Color(0xFF4CAF50) else Color(0xFFEF5350)
                                )

                                if (res.columns.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Column {
                                            // Table Headers
                                            Row(
                                                modifier = Modifier
                                                    .background(Color(0xFF0F172A))
                                                    .padding(6.dp)
                                            ) {
                                                res.columns.forEach { col ->
                                                    Text(
                                                        text = col,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = SfrPrimaryBlue,
                                                        modifier = Modifier.width(110.dp)
                                                    )
                                                }
                                            }

                                            // Table Rows
                                            res.rows.forEach { row ->
                                                Row(modifier = Modifier.padding(6.dp)) {
                                                    row.forEach { cell ->
                                                        Text(
                                                            text = cell,
                                                            fontSize = 11.sp,
                                                            color = Color.White,
                                                            modifier = Modifier.width(110.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // PHP Code Connection Snippets
                val currentDbPath = selectedDb?.file?.absolutePath ?: "path/to/database.db"
                val sqliteSnippet = """
                    <?php
                    // 1. Menggunakan SQLite3 bawaan PHP
                    $"" + "db = new SQLite3('$currentDbPath');
                    
                    // 2. Atau menggunakan PDO SQLite
                    $"" + "pdo = new PDO('sqlite:$currentDbPath');
                    $"" + "pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
                    
                    // Query data
                    $"" + "stmt = $"" + "pdo->query('SELECT * FROM articles');
                    while ($"" + "row = $"" + "stmt->fetch(PDO::FETCH_ASSOC)) {
                        echo $"" + "row['title'] . '<br>';
                    }
                    ?>
                """.trimIndent()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Snippet Koneksi Database di File PHP",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Copy-paste kode berikut ke dalam file index.php atau script backend Anda:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("index.php (PDO / SQLite3)", fontSize = 11.sp, color = SfrPrimaryBlue, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(sqliteSnippet))
                                        Toast.makeText(context, "Snippet PHP disalin!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Divider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = sqliteSnippet,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            3 -> {
                // MariaDB / MySQL Setup Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = SfrSecondaryOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Panduan MySQL / MariaDB di HP Android", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SFR Host mendukung koneksi ke MySQL/MariaDB baik yang berjalan secara remote (Cloud/VPS) maupun lokal di HP Anda.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Opsi 1: Koneksi MySQL Remote / Cloud", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "Skrip PHP Anda dapat langsung terhubung ke database cloud (Aiven, PlanetScale, Supabase, VPS) menggunakan PDO:\n" +
                                        "\$pdo = new PDO('mysql:host=IP_ATAU_HOST;port=3306;dbname=NAMA_DB', 'user', 'pass');",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Opsi 2: Menjalankan MariaDB Server Lokal di HP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = "Jika ingin menjalankan MySQL/MariaDB server sesungguhnya langsung di HP:\n" +
                                        "1. Buka Termux di HP\n" +
                                        "2. Jalankan perintah: pkg install mariadb\n" +
                                        "3. Inisialisasi: mysql_install_db\n" +
                                        "4. Jalankan server: mysqld_safe &\n" +
                                        "5. Port 3306 akan aktif dan dapat diakses oleh PHP SFR Host di 127.0.0.1:3306.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Create DB Dialog
    if (showCreateDbDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDbDialog = false },
            title = { Text("Buat Database SQLite Baru") },
            text = {
                Column {
                    Text("Nama file database (otomatis diakhiri .db):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDbName,
                        onValueChange = { newDbName = it },
                        label = { Text("Nama Database") },
                        placeholder = { Text("toko_online") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newDbName.isNotBlank()) {
                            val success = databaseManager.createDatabase(newDbName.trim())
                            if (success) {
                                Toast.makeText(context, "Database berhasil dibuat!", Toast.LENGTH_SHORT).show()
                                refreshList()
                                showCreateDbDialog = false
                                newDbName = ""
                            } else {
                                Toast.makeText(context, "Nama database sudah ada atau tidak valid.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Buat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDbDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Delete Confirm Dialog
    showDeleteConfirmDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Hapus Database?") },
            text = {
                Text("Apakah Anda yakin ingin menghapus database '${item.name}'? Seluruh data di dalamnya akan dihapus permanen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        databaseManager.deleteDatabase(item.name)
                        Toast.makeText(context, "Database '${item.name}' telah dihapus.", Toast.LENGTH_SHORT).show()
                        refreshList()
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Export Dump Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Ekspor SQL Database") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Salin script SQL dump di bawah ini:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = exportDumpText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(exportDumpText))
                        Toast.makeText(context, "SQL Dump disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Salin ke Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Import SQL Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Impor Script SQL") },
            text = {
                Column {
                    Text("Tempelkan perintah SQL yang ingin dieksekusi:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importSqlText,
                        onValueChange = { importSqlText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        label = { Text("Perintah SQL") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dbName = selectedDb?.name
                        if (dbName != null && importSqlText.isNotBlank()) {
                            val (success, msg) = databaseManager.importSql(dbName, importSqlText)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                refreshList()
                                showImportDialog = false
                                importSqlText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Eksekusi Impor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
