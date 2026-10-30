package com.example.ui.screens

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.files.FileItem
import com.example.files.FileManager
import com.example.files.WebsiteProject
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import com.example.util.NetworkUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsitesScreen(
    activeWebsiteName: String,
    serverPort: Int,
    isServerOnline: Boolean,
    onSetActiveWebsite: (String) -> Unit
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }

    var projects by remember { mutableStateOf(emptyList<WebsiteProject>()) }
    var selectedProject by remember { mutableStateOf(activeWebsiteName) }
    var currentSubPath by remember { mutableStateOf("") }
    var fileItems by remember { mutableStateOf(emptyList<FileItem>()) }

    // Dialog & Sheet States
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var editingFileItem by remember { mutableStateOf<FileItem?>(null) }
    var previewWebsiteName by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        projects = fileManager.getWebsites()
        if (projects.none { it.name == selectedProject }) {
            selectedProject = projects.firstOrNull()?.name ?: "website1"
        }
        fileItems = fileManager.listFiles(selectedProject, currentSubPath)
    }

    LaunchedEffect(selectedProject, currentSubPath) {
        refresh()
    }

    // File Import Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file"
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val ok = fileManager.importFile(selectedProject, currentSubPath, fileName, stream)
                    if (ok) {
                        Toast.makeText(context, "File berhasil diimpor: $fileName", Toast.LENGTH_SHORT).show()
                        refresh()
                    } else {
                        Toast.makeText(context, "Gagal mengimpor file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error impor: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewFileDialog = true },
                containerColor = SfrPrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_file_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah File")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Projects Horizontal Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Proyek Website",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    TextButton(onClick = { showNewProjectDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Proyek Baru")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(projects, key = { it.name }) { proj ->
                        val isCurrent = proj.name == selectedProject
                        val isHosted = proj.name == activeWebsiteName

                        Card(
                            onClick = {
                                selectedProject = proj.name
                                currentSubPath = ""
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) SfrPrimaryBlue.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = if (isCurrent) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SfrPrimaryBlue))
                            else null,
                            modifier = Modifier.width(160.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isCurrent) SfrPrimaryBlue else MaterialTheme.colorScheme.outline
                                    )

                                    if (isHosted) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(StatusOnlineGreen)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "HOSTED",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "${proj.fileCount} file • ${NetworkUtils.formatBytes(proj.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Selected Website Hosting Banner
            item {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Folder: SFRHost/websites/$selectedProject",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (selectedProject == activeWebsiteName) {
                                    Text(
                                        text = "✓ Website ini sedang aktif dihosting",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = StatusOnlineGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "Bukan website aktif saat ini",
                                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }

                            if (selectedProject != activeWebsiteName) {
                                Button(
                                    onClick = {
                                        onSetActiveWebsite(selectedProject)
                                        Toast.makeText(context, "$selectedProject dijadikan website aktif", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Jadikan Aktif", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick toolbar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { previewWebsiteName = selectedProject },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showNewFolderDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Folder", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { importLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Impor", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 3. Breadcrumb & Navigation Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentSubPath.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                currentSubPath = currentSubPath.substringBeforeLast('/', "")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = "/$selectedProject" + if (currentSubPath.isNotEmpty()) "/$currentSubPath" else "",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SfrPrimaryBlue
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 4. File Items List
            if (fileItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Direktori ini kosong",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gunakan tombol + untuk membuat file atau folder baru",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
            } else {
                items(fileItems, key = { it.relativePath }) { item ->
                    FileRowItem(
                        item = item,
                        onClick = {
                            if (item.isDirectory) {
                                currentSubPath = item.relativePath
                            } else {
                                editingFileItem = item
                            }
                        },
                        onRename = { renameTargetItem = item },
                        onDelete = { deleteTargetItem = item }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    // Dialog: Tambah Proyek
    if (showNewProjectDialog) {
        var newProjName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Buat Proyek Website Baru") },
            text = {
                OutlinedTextField(
                    value = newProjName,
                    onValueChange = { newProjName = it },
                    label = { Text("Nama Proyek (misal: portofolio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjName.isNotBlank()) {
                            if (fileManager.createWebsite(newProjName)) {
                                selectedProject = newProjName.trim()
                                currentSubPath = ""
                                refresh()
                                showNewProjectDialog = false
                            } else {
                                Toast.makeText(context, "Nama proyek tidak valid atau sudah ada", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog: Buat File Baru
    if (showNewFileDialog) {
        var newFileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Buat File Baru") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Nama File (contoh: about.html)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(".html", ".css", ".js", ".json").forEach { ext ->
                            SuggestionChip(
                                onClick = {
                                    val base = newFileName.substringBeforeLast('.')
                                    newFileName = (if (base.isEmpty()) "untitled" else base) + ext
                                },
                                label = { Text(ext, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            val ok = fileManager.createFile(selectedProject, currentSubPath, newFileName)
                            if (ok) {
                                refresh()
                                showNewFileDialog = false
                            } else {
                                Toast.makeText(context, "Gagal membuat file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog: Buat Folder Baru
    if (showNewFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Buat Folder Baru") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nama Folder (contoh: assets)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val ok = fileManager.createFolder(selectedProject, currentSubPath, newFolderName)
                            if (ok) {
                                refresh()
                                showNewFolderDialog = false
                            } else {
                                Toast.makeText(context, "Gagal membuat folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Batal") }
            }
        )
    }

    // Dialog: Ganti Nama
    renameTargetItem?.let { target ->
        var renameText by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTargetItem = null },
            title = { Text("Ganti Nama") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            val ok = fileManager.renameItem(selectedProject, target.relativePath, renameText)
                            if (ok) {
                                refresh()
                                renameTargetItem = null
                            } else {
                                Toast.makeText(context, "Gagal mengubah nama", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Ganti") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) { Text("Batal") }
            }
        )
    }

    // Dialog: Hapus
    deleteTargetItem?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("Hapus ${if (target.isDirectory) "Folder" else "File"}?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${target.name}' secara permanen?") },
            confirmButton = {
                Button(
                    onClick = {
                        fileManager.deleteItem(selectedProject, target.relativePath)
                        refresh()
                        deleteTargetItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetItem = null }) { Text("Batal") }
            }
        )
    }

    // In-App Code Editor BottomSheet
    editingFileItem?.let { file ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var fileContent by remember { mutableStateOf(fileManager.readFileContent(selectedProject, file.relativePath)) }

        ModalBottomSheet(
            onDismissRequest = { editingFileItem = null },
            sheetState = sheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "/$selectedProject/${file.relativePath}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF888888),
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }

                    Row {
                        Button(
                            onClick = {
                                val ok = fileManager.saveFileContent(selectedProject, file.relativePath, fileContent)
                                if (ok) {
                                    Toast.makeText(context, "Perubahan disimpan!", Toast.LENGTH_SHORT).show()
                                    editingFileItem = null
                                    refresh()
                                } else {
                                    Toast.makeText(context, "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan")
                        }

                        IconButton(onClick = { editingFileItem = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Monospace Code Editor
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141414))
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFD4D4D4),
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // In-App Website Preview Sheet
    previewWebsiteName?.let { projName ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var webViewRef by remember { mutableStateOf<WebView?>(null) }
        val previewUrl = "http://127.0.0.1:$serverPort/"

        ModalBottomSheet(
            onDismissRequest = { previewWebsiteName = null },
            sheetState = sheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Top Bar for Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pratinjau: $projName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isServerOnline) previewUrl else "file://${fileManager.getWebsiteDirectory(projName).absolutePath}/index.html",
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
                        IconButton(onClick = { previewWebsiteName = null }) {
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

                            val loadUrl = if (isServerOnline && projName == activeWebsiteName) {
                                previewUrl
                            } else {
                                val index = java.io.File(fileManager.getWebsiteDirectory(projName), "index.html")
                                if (index.exists()) "file://${index.absolutePath}" else previewUrl
                            }
                            loadUrl(loadUrl)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        item.isDirectory -> Icons.Default.Folder
        item.extension in listOf("html", "htm") -> Icons.Default.Html
        item.extension in listOf("css", "js", "json") -> Icons.Default.Code
        item.extension in listOf("png", "jpg", "jpeg", "webp", "gif", "svg") -> Icons.Default.Image
        else -> Icons.Default.Description
    }

    val iconTint = when {
        item.isDirectory -> SfrSecondaryOrange
        item.extension in listOf("html", "htm") -> SfrPrimaryBlue
        item.extension == "css" -> Color(0xFF42A5F5)
        item.extension == "js" -> Color(0xFFFFB300)
        item.extension == "json" -> Color(0xFF81C784)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val info = if (item.isDirectory) "Folder" else NetworkUtils.formatBytes(item.sizeBytes)
                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(item.lastModified))
                    Text(
                        text = "$info • $dateStr",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opsi",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Edit Kode") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Ganti Nama") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Hapus", color = StatusOfflineRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StatusOfflineRed) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
