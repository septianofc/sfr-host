package com.example.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class FileCategory {
    PHP,
    HTML,
    CSS,
    JAVASCRIPT,
    JSON,
    SQL,
    ARCHIVE,
    IMAGE,
    TEXT,
    CONFIG,
    FOLDER,
    UNKNOWN
}

data class WebsiteProject(
    val name: String,
    val path: String,
    val fileCount: Int,
    val sizeBytes: Long,
    val originPort: Int = 8080,
    val customDomain: String? = null
)

data class FileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String = name.substringAfterLast('.', "").lowercase(),
    val category: FileCategory = when {
        isDirectory -> FileCategory.FOLDER
        name.endsWith(".php", ignoreCase = true) -> FileCategory.PHP
        name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true) -> FileCategory.HTML
        name.endsWith(".css", ignoreCase = true) -> FileCategory.CSS
        name.endsWith(".js", ignoreCase = true) -> FileCategory.JAVASCRIPT
        name.endsWith(".json", ignoreCase = true) -> FileCategory.JSON
        name.endsWith(".sql", ignoreCase = true) -> FileCategory.SQL
        name.endsWith(".zip", ignoreCase = true) || name.endsWith(".tar", ignoreCase = true) || name.endsWith(".gz", ignoreCase = true) -> FileCategory.ARCHIVE
        name.endsWith(".png", ignoreCase = true) || name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true) || name.endsWith(".svg", ignoreCase = true) || name.endsWith(".ico", ignoreCase = true) -> FileCategory.IMAGE
        name.endsWith(".txt", ignoreCase = true) || name.endsWith(".md", ignoreCase = true) || name.endsWith(".log", ignoreCase = true) -> FileCategory.TEXT
        name.startsWith(".ht") || name.endsWith(".conf", ignoreCase = true) || name.endsWith(".ini", ignoreCase = true) -> FileCategory.CONFIG
        else -> FileCategory.UNKNOWN
    }
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "--"
            val df = java.text.DecimalFormat("#.##")
            return when {
                sizeBytes >= 1024 * 1024 -> "${df.format(sizeBytes.toDouble() / (1024 * 1024))} MB"
                sizeBytes >= 1024 -> "${df.format(sizeBytes.toDouble() / 1024)} KB"
                else -> "$sizeBytes B"
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }
}

class FileManager(private val context: Context) {

    val rootBaseDir: File
        get() {
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED) {
                try {
                    val sharedDir = File(Environment.getExternalStorageDirectory(), "SFRHost/websites")
                    if (sharedDir.exists() || sharedDir.mkdirs()) {
                        return sharedDir
                    }
                } catch (ignored: Exception) {}

                try {
                    val appExtDir = context.getExternalFilesDir("SFRHost/websites")
                    if (appExtDir != null && (appExtDir.exists() || appExtDir.mkdirs())) {
                        return appExtDir
                    }
                } catch (ignored: Exception) {}
            }

            return File(context.filesDir, "SFRHost/websites").apply {
                if (!exists()) mkdirs()
            }
        }

    val storageLocationDescription: String
        get() {
            val path = rootBaseDir.absolutePath
            return when {
                path.startsWith("/storage/emulated/0") || path.startsWith("/sdcard") -> "Penyimpanan Bersama HP (/sdcard/SFRHost/websites)"
                path.contains("/Android/data/") -> "Penyimpanan Eksternal Aplikasi"
                else -> "Penyimpanan Internal Aplikasi"
            }
        }

    init {
        initializeSampleWebsitesIfNeeded()
    }

    fun initializeSampleWebsitesIfNeeded() {
        val w1 = File(rootBaseDir, "website1")
        if (!w1.exists()) {
            w1.mkdirs()
            val publicHtml = File(w1, "public_html").apply { mkdirs() }
            File(w1, "logs").mkdirs()
            File(w1, "config").mkdirs()
            File(w1, "backups").mkdirs()
            File(w1, "assets").mkdirs()

            val indexHtml = """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Selamat Datang di SFR Host</title>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <div class="card">
                        <div class="badge">ONLINE</div>
                        <h1>SFR Host</h1>
                        <p class="subtitle">Web hosting pribadi bertenaga smartphone Android</p>
                        <div class="status-box">
                            <p><strong>Server:</strong> Embedded HTTP Server</p>
                            <p><strong>PHP Version:</strong> PHP 8.2 (CGI & Embedded)</p>
                            <p><strong>Developer:</strong> Septian Fathur Rohman</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            val indexPhp = """
                <?php
                echo "<!DOCTYPE html><html><head><title>PHP Berjalan di Android</title></head><body>";
                echo "<h1>SFR Host PHP Engine Berhasil!</h1>";
                echo "<p>Versi PHP: " . phpversion() . "</p>";
                echo "<p>Waktu Server: " . date('Y-m-d H:i:s') . "</p>";
                echo "</body></html>";
                ?>
            """.trimIndent()

            val styleCss = """
                body { font-family: sans-serif; background: #121820; color: #fff; padding: 20px; }
                .card { background: rgba(255,255,255,0.08); padding: 24px; border-radius: 16px; max-width: 400px; margin: auto; }
                .badge { background: #4CAF50; color: white; display: inline-block; padding: 4px 10px; border-radius: 8px; font-weight: bold; }
                h1 { color: #42A5F5; }
            """.trimIndent()

            File(publicHtml, "index.html").writeText(indexHtml)
            File(publicHtml, "index.php").writeText(indexPhp)
            File(publicHtml, "style.css").writeText(styleCss)
        }
    }

    fun getWebsites(): List<WebsiteProject> {
        val dirs = rootBaseDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        return dirs.map { dir ->
            val files = dir.walkTopDown().filter { it.isFile }.toList()
            val totalSize = files.sumOf { it.length() }
            WebsiteProject(
                name = dir.name,
                path = dir.absolutePath,
                fileCount = files.size,
                sizeBytes = totalSize
            )
        }.sortedBy { it.name }
    }

    fun createWebsite(
        name: String,
        sourceType: String = "TEMPLATE", // "TEMPLATE", "ZIP", "FOLDER"
        originPort: Int = 8080
    ): Boolean {
        val sanitized = sanitizeFileName(name)
        if (sanitized.isEmpty()) return false
        val dir = File(rootBaseDir, sanitized)
        if (dir.exists()) return false

        if (dir.mkdirs()) {
            val publicHtml = File(dir, "public_html").apply { mkdirs() }
            File(dir, "logs").mkdirs()
            File(dir, "config").mkdirs()
            File(dir, "backups").mkdirs()

            if (sourceType == "TEMPLATE") {
                val defaultPhp = "<?php\necho '<h1>Selamat Datang di " + sanitized + "</h1>';\necho '<p>Dihosting dengan SFR Host di HP Android.</p>';\n?>"
                val defaultHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>$sanitized</title></head><body><h1>$sanitized</h1><p>Dihosting dengan SFR Host</p></body></html>"
                File(publicHtml, "index.php").writeText(defaultPhp)
                File(publicHtml, "index.html").writeText(defaultHtml)
            }
            return true
        }
        return false
    }

    fun deleteWebsite(name: String): Boolean {
        val dir = File(rootBaseDir, name)
        return if (dir.exists() && dir.isDirectory) {
            dir.deleteRecursively()
        } else false
    }

    fun getWebsiteDirectory(websiteName: String): File {
        val dir = File(rootBaseDir, websiteName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listFiles(websiteName: String, relativeDir: String = ""): List<FileItem> {
        val base = getWebsiteDirectory(websiteName)
        val target = if (relativeDir.isEmpty()) base else File(base, relativeDir)
        if (!target.exists() || !target.isDirectory) return emptyList()

        val results = mutableListOf<FileItem>()
        target.listFiles()?.forEach { file ->
            val relPath = file.relativeTo(base).path
            results.add(
                FileItem(
                    name = file.name,
                    relativePath = relPath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified()
                )
            )
        }
        return results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun readFileContent(websiteName: String, relativePath: String): String {
        val base = getWebsiteDirectory(websiteName)
        val file = File(base, relativePath)
        if (!isSafePath(base, file) || !file.exists() || file.isDirectory) {
            return ""
        }
        return try {
            file.readText()
        } catch (e: Exception) {
            "Error membaca file: ${e.message}"
        }
    }

    fun saveFileContent(websiteName: String, relativePath: String, content: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val file = File(base, relativePath)
        if (!isSafePath(base, file)) return false
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createFile(websiteName: String, relativeParent: String, fileName: String, defaultContent: String = ""): Boolean {
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val parent = if (relativeParent.isEmpty()) base else File(base, relativeParent)
        val target = File(parent, sanitized)
        if (!isSafePath(base, target) || target.exists()) return false
        return try {
            target.parentFile?.mkdirs()
            target.writeText(defaultContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createFolder(websiteName: String, relativeParent: String, folderName: String): Boolean {
        val sanitized = sanitizeFileName(folderName)
        if (sanitized.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val parent = if (relativeParent.isEmpty()) base else File(base, relativeParent)
        val target = File(parent, sanitized)
        if (!isSafePath(base, target) || target.exists()) return false
        return target.mkdirs()
    }

    fun renameItem(websiteName: String, relativePath: String, newName: String): Boolean {
        val sanitized = sanitizeFileName(newName)
        if (sanitized.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val file = File(base, relativePath)
        if (!isSafePath(base, file) || !file.exists()) return false
        val dest = File(file.parentFile, sanitized)
        if (!isSafePath(base, dest) || dest.exists()) return false
        return file.renameTo(dest)
    }

    fun deleteItem(websiteName: String, relativePath: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val file = File(base, relativePath)
        if (!isSafePath(base, file) || !file.exists()) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun importFile(
        websiteName: String,
        relativeParent: String,
        fileName: String,
        inputStream: InputStream
    ): Boolean {
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val parent = if (relativeParent.isEmpty()) File(base, "public_html") else File(base, relativeParent)
        val target = File(parent, sanitized)
        if (!isSafePath(base, target)) return false
        return try {
            parent.mkdirs()
            target.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Import a single file from SAF Uri into website directory.
     */
    fun importFromUri(
        uri: Uri,
        fileName: String,
        websiteName: String,
        relativeParent: String = ""
    ): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                importFile(websiteName, relativeParent, fileName, input)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Import and extract a ZIP file from SAF Uri directly into website directory.
     */
    fun importZipFromUri(zipUri: Uri, websiteName: String): Pair<Boolean, String> {
        val base = getWebsiteDirectory(websiteName)
        val targetDir = File(base, "public_html").apply { mkdirs() }
        var count = 0

        return try {
            context.contentResolver.openInputStream(zipUri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(targetDir, entry.name)
                        if (!isSafePath(targetDir, newFile)) {
                            return Pair(false, "Deteksi keamanan: Berkas ZIP berisi path traversal terlarang.")
                        }
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            newFile.outputStream().use { zis.copyTo(it) }
                            count++
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            Pair(true, "Berhasil mengekstrak $count berkas dari ZIP ke public_html.")
        } catch (e: Exception) {
            Pair(false, "Gagal mengekstrak ZIP: ${e.message}")
        }
    }

    /**
     * Import entire folder tree selected via SAF ACTION_OPEN_DOCUMENT_TREE using DocumentsContract.
     */
    fun importFolderFromTreeUri(treeUri: Uri, websiteName: String): Pair<Boolean, String> {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (ignored: Exception) {}

            val base = getWebsiteDirectory(websiteName)
            val publicHtml = File(base, "public_html").apply { mkdirs() }
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

            var count = 0

            fun copyTreeDoc(documentId: String, currentDir: File) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                val cursor = context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (c.moveToNext()) {
                        val childDocId = c.getString(idCol)
                        val displayName = c.getString(nameCol) ?: "unnamed"
                        val mimeType = c.getString(mimeCol)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val subDir = File(currentDir, displayName).apply { mkdirs() }
                            copyTreeDoc(childDocId, subDir)
                        } else {
                            val targetFile = File(currentDir, displayName)
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            context.contentResolver.openInputStream(docUri)?.use { input ->
                                targetFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            count++
                        }
                    }
                }
            }

            copyTreeDoc(rootDocId, publicHtml)

            Pair(true, "Berhasil mengimpor $count berkas dari folder HP ke $websiteName/public_html.")
        } catch (e: Exception) {
            Pair(false, "Gagal mengimpor folder: ${e.message}")
        }
    }

    /**
     * Extract a ZIP file located inside the website folder.
     */
    fun unzipFile(websiteName: String, zipRelativePath: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val zipFile = File(base, zipRelativePath)
        if (!isSafePath(base, zipFile) || !zipFile.exists() || !zipFile.name.endsWith(".zip", ignoreCase = true)) return false
        val destDir = File(zipFile.parentFile ?: base, zipFile.nameWithoutExtension)
        destDir.mkdirs()

        return try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (!isSafePath(destDir, newFile)) {
                        return false
                    }
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        newFile.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create a ZIP archive from a folder.
     */
    fun zipFolder(websiteName: String, relativeDir: String, zipFileName: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val sourceDir = if (relativeDir.isEmpty()) base else File(base, relativeDir)
        val sanitized = if (zipFileName.endsWith(".zip", ignoreCase = true)) zipFileName else "$zipFileName.zip"
        val zipFile = File(sourceDir.parentFile ?: base, sanitized)
        if (!isSafePath(base, sourceDir) || !isSafePath(base, zipFile)) return false

        return try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                sourceDir.walkTopDown().filter { it.isFile && it != zipFile }.forEach { file ->
                    val relPath = file.relativeTo(sourceDir).path
                    val entry = ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compress multiple selected files and folders into a ZIP archive.
     */
    fun zipFiles(
        websiteName: String,
        relativeParent: String,
        fileNames: List<String>,
        zipFileName: String
    ): Boolean {
        if (fileNames.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val parent = if (relativeParent.isEmpty()) base else File(base, relativeParent)
        val sanitized = sanitizeFileName(if (zipFileName.endsWith(".zip", ignoreCase = true)) zipFileName else "$zipFileName.zip")
        val zipFile = File(parent, sanitized)
        if (!isSafePath(base, parent) || !isSafePath(base, zipFile)) return false

        return try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                for (name in fileNames) {
                    val item = File(parent, name)
                    if (!item.exists() || !isSafePath(base, item) || item == zipFile) continue
                    if (item.isDirectory) {
                        item.walkTopDown().forEach { sub ->
                            if (sub.isFile && sub != zipFile) {
                                val relPath = sub.relativeTo(parent).path
                                val entry = ZipEntry(relPath)
                                zos.putNextEntry(entry)
                                sub.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    } else {
                        val entry = ZipEntry(item.name)
                        zos.putNextEntry(entry)
                        item.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract a ZIP file to an explicit destination directory with overwrite toggle.
     */
    fun unzipFileTo(
        websiteName: String,
        zipRelativePath: String,
        destinationRelativePath: String,
        overwrite: Boolean
    ): Pair<Boolean, String> {
        val base = getWebsiteDirectory(websiteName)
        val zipFile = File(base, zipRelativePath)
        if (!isSafePath(base, zipFile) || !zipFile.exists() || !zipFile.name.endsWith(".zip", ignoreCase = true)) {
            return Pair(false, "Berkas ZIP tidak ditemukan atau tidak valid.")
        }

        val destDir = if (destinationRelativePath.isEmpty()) base else File(base, destinationRelativePath)
        if (!isSafePath(base, destDir)) {
            return Pair(false, "Folder tujuan ekstraksi tidak valid.")
        }
        destDir.mkdirs()

        var count = 0
        var skipped = 0
        return try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (!isSafePath(destDir, newFile)) {
                        return Pair(false, "Deteksi keamanan: Path traversal berbahaya terdeteksi di dalam ZIP.")
                    }
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        if (newFile.exists() && !overwrite) {
                            skipped++
                        } else {
                            newFile.outputStream().use { zis.copyTo(it) }
                            count++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val msg = if (skipped > 0) {
                "Berhasil mengekstrak $count berkas ($skipped dilewati karena sudah ada)."
            } else {
                "Berhasil mengekstrak $count berkas ke ${destDir.name}."
            }
            Pair(true, msg)
        } catch (e: Exception) {
            Pair(false, "Gagal mengekstrak ZIP: ${e.message}")
        }
    }

    fun isSafePath(base: File, target: File): Boolean {
        val canonicalBase = base.canonicalPath
        val canonicalTarget = target.canonicalPath
        return canonicalTarget.startsWith(canonicalBase)
    }

    private fun sanitizeFileName(name: String): String {
        return name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}
