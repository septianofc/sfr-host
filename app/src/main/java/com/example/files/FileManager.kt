package com.example.files

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.InputStream

data class WebsiteProject(
    val name: String,
    val path: String,
    val fileCount: Int,
    val sizeBytes: Long
)

data class FileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String = name.substringAfterLast('.', "")
)

class FileManager(private val context: Context) {

    val rootBaseDir: File
        get() {
            // Prioritize device shared storage (/sdcard/SFRHost/websites) so user can access it via phone file manager
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

            // Fallback to internal app files directory
            return File(context.filesDir, "SFRHost/websites").apply {
                if (!exists()) mkdirs()
            }
        }

    val storageLocationDescription: String
        get() {
            val path = rootBaseDir.absolutePath
            return when {
                path.startsWith("/storage/emulated/0") || path.startsWith("/sdcard") -> "Penyimpanan Utama HP (/sdcard/SFRHost/websites)"
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

            val indexContent = """
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
                            <p><strong>Status:</strong> Siap melayani traffic</p>
                            <p><strong>Developer:</strong> Septian Fathur Rohman</p>
                        </div>
                        <button onclick="pingServer()">Cek Koneksi</button>
                        <p id="ping-result"></p>
                    </div>
                    <script src="script.js"></script>
                </body>
                </html>
            """.trimIndent()

            val styleContent = """
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }
                body {
                    background: linear-gradient(135deg, #101418 0%, #1a2332 100%);
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #fff;
                    padding: 20px;
                }
                .card {
                    background: rgba(255, 255, 255, 0.05);
                    backdrop-filter: blur(10px);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 20px;
                    padding: 32px;
                    max-width: 440px;
                    width: 100%;
                    text-align: center;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
                }
                .badge {
                    display: inline-block;
                    background: #4CAF50;
                    color: white;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 12px;
                    font-weight: bold;
                    letter-spacing: 1px;
                    margin-bottom: 16px;
                }
                h1 {
                    font-size: 28px;
                    color: #42A5F5;
                    margin-bottom: 8px;
                }
                .subtitle {
                    color: #b0bec5;
                    font-size: 14px;
                    margin-bottom: 24px;
                }
                .status-box {
                    background: rgba(0, 0, 0, 0.25);
                    border-radius: 12px;
                    padding: 16px;
                    text-align: left;
                    font-size: 14px;
                    line-height: 1.8;
                    margin-bottom: 24px;
                    border-left: 4px solid #FF9800;
                }
                button {
                    background: linear-gradient(90deg, #42A5F5 0%, #FF9800 100%);
                    border: none;
                    color: white;
                    padding: 12px 24px;
                    border-radius: 25px;
                    font-weight: bold;
                    font-size: 14px;
                    cursor: pointer;
                    width: 100%;
                    transition: transform 0.2s;
                }
                button:active {
                    transform: scale(0.98);
                }
                #ping-result {
                    margin-top: 14px;
                    font-size: 13px;
                    color: #69f0ae;
                }
            """.trimIndent()

            val scriptContent = """
                function pingServer() {
                    const result = document.getElementById('ping-result');
                    result.textContent = 'Menghubungkan ke server lokal...';
                    const startTime = performance.now();
                    fetch('/')
                        .then(() => {
                            const latency = Math.round(performance.now() - startTime);
                            result.textContent = 'Server responsif! Latency: ' + latency + ' ms';
                        })
                        .catch(err => {
                            result.textContent = 'Koneksi lokal aktif.';
                        });
                }
            """.trimIndent()

            val phpIndexContent = """
                <?php
                echo "<h1>SFR Host PHP Berhasil!</h1>";
                echo "<p>PHP Version: " . PHP_VERSION . "</p>";
                echo "<p>Server Time: " . date("Y-m-d H:i:s") . "</p>";
                ?>
            """.trimIndent()

            val dollar = "$"
            val phpTestContent = "<?php\n" +
                    "echo \"<h2>SFR Host - PHP Runtime Test</h2>\";\n" +
                    "echo \"<p>Server Software: \" . " + dollar + "_SERVER['SERVER_SOFTWARE'] . \"</p>\";\n" +
                    "echo \"<p>Document Root: \" . " + dollar + "_SERVER['DOCUMENT_ROOT'] . \"</p>\";\n" +
                    "echo \"<p>Client IP: \" . " + dollar + "_SERVER['REMOTE_ADDR'] . \"</p>\";\n" +
                    "echo \"<p>Method: \" . " + dollar + "_SERVER['REQUEST_METHOD'] . \"</p>\";\n" +
                    "?>"

            val htaccessContent = """
                # SFR Host Apache .htaccess
                DirectoryIndex index.php index.html index.htm
                Options -Indexes
                ErrorDocument 400 /400.html
                ErrorDocument 403 /403.html
                ErrorDocument 404 /404.html
                ErrorDocument 500 /500.html
            """.trimIndent()

            // Save in public_html and root
            File(publicHtml, "index.php").writeText(phpIndexContent)
            File(publicHtml, "test.php").writeText(phpTestContent)
            File(publicHtml, ".htaccess").writeText(htaccessContent)
            File(publicHtml, "index.html").writeText(indexContent)
            File(publicHtml, "style.css").writeText(styleContent)
            File(publicHtml, "script.js").writeText(scriptContent)

            File(w1, "index.php").writeText(phpIndexContent)
            File(w1, "test.php").writeText(phpTestContent)
            File(w1, ".htaccess").writeText(htaccessContent)
            File(w1, "index.html").writeText(indexContent)
            File(w1, "style.css").writeText(styleContent)
            File(w1, "script.js").writeText(scriptContent)
        }

        val w2 = File(rootBaseDir, "website2")
        if (!w2.exists()) {
            w2.mkdirs()
            val publicHtml = File(w2, "public_html").apply { mkdirs() }
            File(w2, "logs").mkdirs()
            File(w2, "config").mkdirs()
            File(w2, "backups").mkdirs()

            val indexContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Website 2 - SFR Host</title>
                    <style>
                        body { font-family: sans-serif; text-align: center; padding: 50px; background: #f0f4f8; }
                        h1 { color: #1e88e5; }
                        p { color: #555; }
                    </style>
                </head>
                <body>
                    <h1>Website 2 Aktif</h1>
                    <p>Situs ini dihosting langsung dari HP Android Anda menggunakan SFR Host.</p>
                </body>
                </html>
            """.trimIndent()

            File(publicHtml, "index.html").writeText(indexContent)
            File(w2, "index.html").writeText(indexContent)
        }
    }

    fun getWebsites(): List<WebsiteProject> {
        val dir = rootBaseDir
        if (!dir.exists()) return emptyList()
        val projects = mutableListOf<WebsiteProject>()
        dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            val allFiles = sub.walkTopDown().filter { it.isFile }.toList()
            val totalSize = allFiles.sumOf { it.length() }
            projects.add(
                WebsiteProject(
                    name = sub.name,
                    path = sub.absolutePath,
                    fileCount = allFiles.size,
                    sizeBytes = totalSize
                )
            )
        }
        return projects.sortedBy { it.name }
    }

    fun createWebsite(name: String): Boolean {
        val sanitized = sanitizeFileName(name)
        if (sanitized.isEmpty()) return false
        val dir = File(rootBaseDir, sanitized)
        if (dir.exists()) return false
        if (dir.mkdirs()) {
            val publicHtml = File(dir, "public_html").apply { mkdirs() }
            File(dir, "logs").mkdirs()
            File(dir, "config").mkdirs()
            File(dir, "backups").mkdirs()
            val defaultHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>$sanitized</title></head><body><h1>$sanitized</h1><p>Dihosting dengan SFR Host</p></body></html>"
            File(publicHtml, "index.html").writeText(defaultHtml)
            File(dir, "index.html").writeText(defaultHtml)
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
        // Directory traversal protection check
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

    fun importFile(websiteName: String, relativeParent: String, fileName: String, inputStream: InputStream): Boolean {
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.isEmpty()) return false
        val base = getWebsiteDirectory(websiteName)
        val parent = if (relativeParent.isEmpty()) base else File(base, relativeParent)
        val target = File(parent, sanitized)
        if (!isSafePath(base, target)) return false
        return try {
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            true
        } catch (e: Exception) {
            false
        }
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

    fun copyItem(websiteName: String, relativePath: String, destParentRelative: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val source = File(base, relativePath)
        val destParent = if (destParentRelative.isEmpty()) base else File(base, destParentRelative)
        val target = File(destParent, source.name)
        if (!isSafePath(base, source) || !isSafePath(base, target) || !source.exists() || target.exists()) return false
        return try {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = false)
            } else {
                source.copyTo(target)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun moveItem(websiteName: String, relativePath: String, destParentRelative: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val source = File(base, relativePath)
        val destParent = if (destParentRelative.isEmpty()) base else File(base, destParentRelative)
        val target = File(destParent, source.name)
        if (!isSafePath(base, source) || !isSafePath(base, target) || !source.exists() || target.exists()) return false
        return source.renameTo(target)
    }

    fun zipFolder(websiteName: String, relativeDir: String, zipFileName: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val sourceDir = if (relativeDir.isEmpty()) base else File(base, relativeDir)
        val sanitized = if (zipFileName.endsWith(".zip")) zipFileName else "$zipFileName.zip"
        val zipFile = File(sourceDir.parentFile ?: base, sanitized)
        if (!isSafePath(base, sourceDir) || !isSafePath(base, zipFile)) return false

        return try {
            java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                sourceDir.walkTopDown().filter { it.isFile && it != zipFile }.forEach { file ->
                    val relPath = file.relativeTo(sourceDir).path
                    val entry = java.util.zip.ZipEntry(relPath)
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

    fun unzipFile(websiteName: String, zipRelativePath: String): Boolean {
        val base = getWebsiteDirectory(websiteName)
        val zipFile = File(base, zipRelativePath)
        if (!isSafePath(base, zipFile) || !zipFile.exists() || !zipFile.name.endsWith(".zip")) return false
        val destDir = File(zipFile.parentFile ?: base, zipFile.nameWithoutExtension)
        destDir.mkdirs()

        return try {
            java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (!isSafePath(destDir, newFile)) {
                        // Prevent zip slip vulnerability
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

    fun isSafePath(base: File, target: File): Boolean {
        val canonicalBase = base.canonicalPath
        val canonicalTarget = target.canonicalPath
        return canonicalTarget.startsWith(canonicalBase)
    }

    private fun sanitizeFileName(name: String): String {
        return name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}

