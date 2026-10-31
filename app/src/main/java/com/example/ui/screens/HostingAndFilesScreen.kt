package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.files.FileCategory
import com.example.files.FileItem
import com.example.files.FileManager
import com.example.files.WebsiteProject
import com.example.ui.theme.SfrPrimaryBlue
import com.example.ui.theme.SfrSecondaryOrange
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.StatusOnlineGreen
import kotlinx.coroutines.launch
import java.io.File

enum class SortMode {
    NAME,
    SIZE,
    DATE
}

/**
 * Unified Hosting & File Management screen consolidating Website and File Browser into a single seamless experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingAndFilesScreen(
    activeWebsiteName: String,
    serverPort: Int,
    isServerOnline: Boolean,
    onSetActiveWebsite: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileManager = remember { FileManager(context) }

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Websites, 1: File Manager

    var projects by remember { mutableStateOf(emptyList<WebsiteProject>()) }
    var currentWebsite by remember { mutableStateOf(activeWebsiteName) }
    var currentSubPath by remember { mutableStateOf("") }
    var fileItems by remember { mutableStateOf(emptyList<FileItem>()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }

    // Dialog & Sheet States
    var showNewHostingDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteTargetItem by remember { mutableStateOf<FileItem?>(null) }
    var editingFileItem by remember { mutableStateOf<FileItem?>(null) }
    var zipExtractTargetItem by remember { mutableStateOf<FileItem?>(null) }

    fun refresh() {
        projects = fileManager.getWebsites()
        if (projects.none { it.name == currentWebsite }) {
            currentWebsite = projects.firstOrNull()?.name ?: "website1"
        }
        val items = fileManager.listFiles(currentWebsite, currentSubPath)
        fileItems = when (sortMode) {
            SortMode.NAME -> items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortMode.SIZE -> items.sortedWith(compareBy({ !it.isDirectory }, { it.sizeBytes }))
            SortMode.DATE -> items.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
        }
    }

    LaunchedEffect(currentWebsite, currentSubPath, sortMode) {
        refresh()
    }

    // SAF Picker: Import Single File
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
            val success = fileManager.importFromUri(uri, fileName, currentWebsite, currentSubPath)
            if (success) {
                Toast.makeText(context, "Berkas berhasil diimpor!", Toast.LENGTH_SHORT).show()
                refresh()
            } else {
                Toast.makeText(context, "Gagal mengimpor berkas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF Picker: Import ZIP
    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = fileManager.importZipFromUri(uri, currentWebsite)
            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
            if (result.first) refresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Sub-Tab Navigation Bar
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SfrPrimaryBlue
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Daftar Website", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("File Manager (ZArchiver)", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // Tab Content
        if (selectedSubTab == 0) {
            // ==================== TAB 1: WEBSITES ====================
            WebsitesListSection(
                projects = projects,
                activeWebsiteName = activeWebsiteName,
                serverPort = serverPort,
                isServerOnline = isServerOnline,
                onSelectWebsite = { ws ->
                    currentWebsite = ws
                    selectedSubTab = 1 // Open its files
                },
                onSetActiveWebsite = onSetActiveWebsite,
                onCreateHostingClick = { showNewHostingDialog = true },
                onDeleteWebsite = { wsName ->
                    fileManager.deleteWebsite(wsName)
                    refresh()
                    Toast.makeText(context, "Website '$wsName' dihapus.", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            // ==================== TAB 2: FILE MANAGER (ZARCHIVER STYLE) ====================
            FileManagerSection(
                projects = projects,
                currentWebsite = currentWebsite,
                currentSubPath = currentSubPath,
                fileItems = fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) },
                searchQuery = searchQuery,
                sortMode = sortMode,
                onWebsiteChange = { ws ->
                    currentWebsite = ws
                    currentSubPath = ""
                },
                onSubPathChange = { path -> currentSubPath = path },
                onSearchChange = { searchQuery = it },
                onSortChange = { sortMode = it },
                onFileClick = { item ->
                    if (item.isDirectory) {
                        currentSubPath = if (currentSubPath.isEmpty()) item.name else "$currentSubPath/${item.name}"
                    } else if (isTextEditable(item.extension)) {
                        editingFileItem = item
                    } else if (item.extension == "zip") {
                        zipExtractTargetItem = item
                    }
                },
                onNewFolderClick = { showNewFolderDialog = true },
                onNewFileClick = { showNewFileDialog = true },
                onImportFileClick = { fileImportLauncher.launch("*/*") },
                onImportZipClick = { zipImportLauncher.launch("application/zip") },
                onRenameClick = { renameTargetItem = it },
                onDeleteClick = { deleteTargetItem = it },
                onRefreshClick = { refresh() }
            )
        }
    }

    // ==================== DIALOGS & MODALS ====================

    // 1. Buat Hosting Baru Dialog (With Real SAF HP Storage / ZIP / Template Source)
    if (showNewHostingDialog) {
        NewHostingDialog(
            fileManager = fileManager,
            onDismiss = { showNewHostingDialog = false },
            onCreated = { newName ->
                showNewHostingDialog = false
                currentWebsite = newName
                onSetActiveWebsite(newName)
                refresh()
            }
        )
    }

    // 2. Buat File Baru Dialog
    if (showNewFileDialog) {
        var fileNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Buat Berkas Baru") },
            text = {
                Column {
                    Text("Nama file beserta ekstensi (contoh: contact.php, style.css):", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = fileNameInput,
                        onValueChange = { fileNameInput = it },
                        placeholder = { Text("index.php") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileNameInput.isNotBlank()) {
                            fileManager.createFile(currentWebsite, currentSubPath, fileNameInput.trim())
                            showNewFileDialog = false
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Buat Berkas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Batal") }
            }
        )
    }

    // 3. Buat Folder Baru Dialog
    if (showNewFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Buat Direktori / Folder Baru") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            fileManager.createFolder(currentWebsite, currentSubPath, folderNameInput.trim())
                            showNewFolderDialog = false
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Buat Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Batal") }
            }
        )
    }

    // 4. Rename Item Dialog
    renameTargetItem?.let { item ->
        var newNameInput by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renameTargetItem = null },
            title = { Text("Ubah Nama Berkas/Folder") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNameInput.isNotBlank()) {
                            fileManager.renameItem(currentWebsite, item.relativePath, newNameInput.trim())
                            renameTargetItem = null
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Ubah Nama")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetItem = null }) { Text("Batal") }
            }
        )
    }

    // 5. Delete Item Dialog
    deleteTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("Hapus ${if (item.isDirectory) "Folder" else "Berkas"}") },
            text = { Text("Apakah Anda yakin ingin menghapus '${item.name}'? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        fileManager.deleteItem(currentWebsite, item.relativePath)
                        deleteTargetItem = null
                        refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetItem = null }) { Text("Batal") }
            }
        )
    }

    // 6. Ekstrak ZIP Dialog
    zipExtractTargetItem?.let { item ->
        AlertDialog(
            onDismissRequest = { zipExtractTargetItem = null },
            title = { Text("Ekstrak Arsip ZIP") },
            text = { Text("Ekstrak isi arsip '${item.name}' ke dalam folder ini?") },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = fileManager.unzipFile(currentWebsite, item.relativePath)
                        zipExtractTargetItem = null
                        if (ok) {
                            Toast.makeText(context, "Arsip ZIP berhasil diekstrak!", Toast.LENGTH_SHORT).show()
                            refresh()
                        } else {
                            Toast.makeText(context, "Gagal mengekstrak ZIP.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
                ) {
                    Text("Ekstrak Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { zipExtractTargetItem = null }) { Text("Batal") }
            }
        )
    }

    // 7. Full-Featured Code / Text Editor Modal Sheet
    editingFileItem?.let { file ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var fileContent by remember { mutableStateOf(fileManager.readFileContent(currentWebsite, file.relativePath)) }

        ModalBottomSheet(
            onDismissRequest = { editingFileItem = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentWebsite}/${file.relativePath}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val saved = fileManager.saveFileContent(currentWebsite, file.relativePath, fileContent)
                                if (saved) {
                                    Toast.makeText(context, "Tersimpan!", Toast.LENGTH_SHORT).show()
                                    refresh()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = { editingFileItem = null },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F141C))
                        .padding(10.dp)
                ) {
                    BasicTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Tab 1: Websites List section
 */
@Composable
fun WebsitesListSection(
    projects: List<WebsiteProject>,
    activeWebsiteName: String,
    serverPort: Int,
    isServerOnline: Boolean,
    onSelectWebsite: (String) -> Unit,
    onSetActiveWebsite: (String) -> Unit,
    onCreateHostingClick: () -> Unit,
    onDeleteWebsite: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Action Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pengelolaan Website Hosting", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Pilih website untuk mengaktifkan traffic origin", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }

                Button(
                    onClick = onCreateHostingClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buat Hosting", fontSize = 12.sp)
                }
            }
        }

        // List of websites
        items(projects) { project ->
            val isActive = project.name == activeWebsiteName

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, SfrPrimaryBlue) else null
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) SfrPrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isActive) SfrPrimaryBlue else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = project.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isActive) "AKTIF MELAYANI TRAFFIC" else "TERSEDIA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) StatusOnlineGreen else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Set Active / Active Badge
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StatusOnlineGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "LIVE",
                                    color = StatusOnlineGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSetActiveWebsite(project.name) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Aktifkan", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Berkas: ${project.fileCount} file", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("Ukuran: ${formatSize(project.sizeBytes)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("Port: $serverPort", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectWebsite(project.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buka Berkas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        if (projects.size > 1 && !isActive) {
                            OutlinedButton(
                                onClick = { onDeleteWebsite(project.name) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusOfflineRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * Tab 2: Full ZArchiver-like File Manager Section
 */
@Composable
fun FileManagerSection(
    projects: List<WebsiteProject>,
    currentWebsite: String,
    currentSubPath: String,
    fileItems: List<FileItem>,
    searchQuery: String,
    sortMode: SortMode,
    onWebsiteChange: (String) -> Unit,
    onSubPathChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onNewFolderClick: () -> Unit,
    onNewFileClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onImportZipClick: () -> Unit,
    onRenameClick: (FileItem) -> Unit,
    onDeleteClick: (FileItem) -> Unit,
    onRefreshClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Website Switcher & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Website Dropdown Selector
            var websiteDropdownExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { websiteDropdownExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(currentWebsite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = websiteDropdownExpanded,
                    onDismissRequest = { websiteDropdownExpanded = false }
                ) {
                    projects.forEach { prj ->
                        DropdownMenuItem(
                            text = { Text(prj.name) },
                            onClick = {
                                onWebsiteChange(prj.name)
                                websiteDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Quick Operation Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNewFolderClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Buat Folder", tint = SfrPrimaryBlue)
                }
                IconButton(onClick = onNewFileClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "Buat Berkas", tint = SfrPrimaryBlue)
                }
                IconButton(onClick = onImportFileClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Impor Berkas", tint = SfrSecondaryOrange)
                }
                IconButton(onClick = onImportZipClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Ekstrak ZIP", tint = SfrSecondaryOrange)
                }
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Search & Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari berkas...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp)
            )

            // Sort Filter
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Urutkan: Nama (A-Z)") }, onClick = { onSortChange(SortMode.NAME); sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Urutkan: Ukuran") }, onClick = { onSortChange(SortMode.SIZE); sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text("Urutkan: Tanggal") }, onClick = { onSortChange(SortMode.DATE); sortMenuExpanded = false })
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Breadcrumb Path Bar (ZArchiver style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Up One Level Button
                if (currentSubPath.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            val parent = currentSubPath.substringBeforeLast('/', "")
                            onSubPathChange(parent)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Naik Satu Tingkat", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Breadcrumb clickable segments
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "root",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = SfrPrimaryBlue,
                        modifier = Modifier.clickable { onSubPathChange("") }
                    )

                    if (currentSubPath.isNotEmpty()) {
                        val parts = currentSubPath.split('/')
                        var accumulated = ""
                        parts.forEach { part ->
                            accumulated = if (accumulated.isEmpty()) part else "$accumulated/$part"
                            val targetPath = accumulated
                            Text(" / ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = part,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable { onSubPathChange(targetPath) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. File and Folder List (ZArchiver format)
        if (fileItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "Tidak ada berkas yang cocok dengan pencarian." else "Folder ini kosong. Tambahkan berkas dengan tombol '+' di atas.",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(fileItems) { item ->
                    FileItemRow(
                        item = item,
                        onClick = { onFileClick(item) },
                        onRename = { onRenameClick(item) },
                        onDelete = { onDeleteClick(item) }
                    )
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }
}

@Composable
fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val (icon, iconColor) = when (item.category) {
        FileCategory.FOLDER -> Pair(Icons.Default.Folder, SfrPrimaryBlue)
        FileCategory.PHP -> Pair(Icons.Default.Code, Color(0xFF818CF8))
        FileCategory.HTML -> Pair(Icons.Default.Html, Color(0xFFF97316))
        FileCategory.CSS -> Pair(Icons.Default.Description, Color(0xFF38BDF8))
        FileCategory.JAVASCRIPT -> Pair(Icons.Default.Code, Color(0xFFFACC15))
        FileCategory.JSON -> Pair(Icons.Default.Description, Color(0xFFA3E635))
        FileCategory.SQL -> Pair(Icons.Default.Storage, SfrSecondaryOrange)
        FileCategory.ARCHIVE -> Pair(Icons.Default.Archive, Color(0xFFEC4899))
        FileCategory.IMAGE -> Pair(Icons.Default.Image, Color(0xFF34D399))
        FileCategory.TEXT -> Pair(Icons.Default.Description, Color(0xFF94A3B8))
        FileCategory.CONFIG -> Pair(Icons.Default.Description, Color(0xFFF43F5E))
        FileCategory.UNKNOWN -> Pair(Icons.Default.Description, MaterialTheme.colorScheme.outline)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = item.formattedSize, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    Text(text = item.formattedDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(text = { Text("Ubah Nama") }, onClick = { onRename(); menuExpanded = false })
                    DropdownMenuItem(text = { Text("Hapus") }, onClick = { onDelete(); menuExpanded = false })
                }
            }
        }
    }
}

/**
 * Buat Hosting Baru Dialog with Real HP Storage Folder / ZIP / Template options.
 */
@Composable
fun NewHostingDialog(
    fileManager: FileManager,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val context = LocalContext.current
    var websiteName by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("TEMPLATE") } // "TEMPLATE", "FOLDER", "ZIP"
    var selectedTreeUri by remember { mutableStateOf<Uri?>(null) }
    var selectedZipUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // SAF Tree Folder Picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedTreeUri = uri
            errorMessage = null
        }
    }

    // SAF ZIP File Picker
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedZipUri = uri
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Website Hosting Baru", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = websiteName,
                    onValueChange = { websiteName = it },
                    label = { Text("Nama Website (Folder Root)") },
                    placeholder = { Text("toko-online / blog") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Pilih Sumber Berkas Website:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                // Option 1: Template SFR Host
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSource = "TEMPLATE" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedSource == "TEMPLATE", onClick = { selectedSource = "TEMPLATE" })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Template SFR Host (index.php + index.html)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Mulai dengan file starter PHP dan HTML", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                // Option 2: Folder dari Penyimpanan HP (SAF)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSource = "FOLDER" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedSource == "FOLDER", onClick = { selectedSource = "FOLDER" })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pilih Folder dari HP", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (selectedTreeUri != null) "Folder terpilih: ${selectedTreeUri?.lastPathSegment}"
                            else "Pilih direktori website Anda melalui pemilih folder HP",
                            fontSize = 10.sp,
                            color = if (selectedTreeUri != null) StatusOnlineGreen else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (selectedSource == "FOLDER") {
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedTreeUri != null) "Ganti Folder HP" else "Buka Pemilih Folder HP", fontSize = 11.sp)
                    }
                }

                // Option 3: File ZIP dari Penyimpanan HP
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSource = "ZIP" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedSource == "ZIP", onClick = { selectedSource = "ZIP" })
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("File Arsip ZIP dari HP", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (selectedZipUri != null) "ZIP terpilih: ${selectedZipUri?.lastPathSegment}"
                            else "Unggah berkas website terkompresi ZIP",
                            fontSize = 10.sp,
                            color = if (selectedZipUri != null) StatusOnlineGreen else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                if (selectedSource == "ZIP") {
                    OutlinedButton(
                        onClick = { zipPickerLauncher.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedZipUri != null) "Ganti File ZIP" else "Pilih Berkas ZIP", fontSize = 11.sp)
                    }
                }

                errorMessage?.let { err ->
                    Text(text = err, color = StatusOfflineRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = websiteName.trim()
                    if (clean.isEmpty()) {
                        errorMessage = "Nama website tidak boleh kosong."
                        return@Button
                    }
                    if (selectedSource == "FOLDER" && selectedTreeUri == null) {
                        errorMessage = "Silakan pilih folder dari HP terlebih dahulu."
                        return@Button
                    }
                    if (selectedSource == "ZIP" && selectedZipUri == null) {
                        errorMessage = "Silakan pilih file ZIP terlebih dahulu."
                        return@Button
                    }

                    val created = fileManager.createWebsite(clean, selectedSource)
                    if (!created) {
                        errorMessage = "Website dengan nama '$clean' sudah ada."
                        return@Button
                    }

                    if (selectedSource == "FOLDER" && selectedTreeUri != null) {
                        fileManager.importFolderFromTreeUri(selectedTreeUri!!, clean)
                    } else if (selectedSource == "ZIP" && selectedZipUri != null) {
                        fileManager.importZipFromUri(selectedZipUri!!, clean)
                    }

                    Toast.makeText(context, "Website '$clean' berhasil dibuat!", Toast.LENGTH_SHORT).show()
                    onCreated(clean)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SfrPrimaryBlue)
            ) {
                Text("Buat Website")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

private fun isTextEditable(ext: String): Boolean {
    return ext in listOf("php", "html", "htm", "css", "js", "json", "sql", "txt", "md", "xml", "htaccess", "conf", "env")
}

private fun formatSize(bytes: Long): String {
    val df = java.text.DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
        else -> "$bytes B"
    }
}
