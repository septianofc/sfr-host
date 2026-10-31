package com.example.server

import java.io.File
import java.util.regex.Pattern

data class HtaccessConfig(
    val directoryIndex: List<String> = listOf("index.php", "index.html", "index.htm"),
    val customErrorDocuments: Map<Int, String> = emptyMap(),
    val allowDirectoryListing: Boolean = true,
    val denyAll: Boolean = false,
    val rewriteRules: List<Pair<Regex, String>> = emptyList()
)

object HtaccessManager {

    fun parse(htaccessFile: File): HtaccessConfig {
        if (!htaccessFile.exists()) return HtaccessConfig()

        val lines = try {
            htaccessFile.readLines(Charsets.UTF_8)
        } catch (e: Exception) {
            return HtaccessConfig()
        }

        var dirIndex = listOf("index.php", "index.html", "index.htm")
        val errorDocs = mutableMapOf<Int, String>()
        var allowListing = true
        var deny = false
        val rewrites = mutableListOf<Pair<Regex, String>>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#") || line.isEmpty()) continue

            // DirectoryIndex file1 file2 file3
            if (line.startsWith("DirectoryIndex", ignoreCase = true)) {
                val tokens = line.substring(14).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (tokens.isNotEmpty()) {
                    dirIndex = tokens
                }
            }

            // ErrorDocument 404 /404.html
            else if (line.startsWith("ErrorDocument", ignoreCase = true)) {
                val parts = line.substring(13).trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val code = parts[0].toIntOrNull()
                    if (code != null) {
                        errorDocs[code] = parts[1].trim('"', '\'')
                    }
                }
            }

            // Options -Indexes / Options +Indexes
            else if (line.startsWith("Options", ignoreCase = true)) {
                if (line.contains("-Indexes", ignoreCase = true)) {
                    allowListing = false
                } else if (line.contains("+Indexes", ignoreCase = true)) {
                    allowListing = true
                }
            }

            // Deny from all
            else if (line.contains("Deny from all", ignoreCase = true)) {
                deny = true
            }

            // RewriteRule pattern substitution [flags]
            else if (line.startsWith("RewriteRule", ignoreCase = true)) {
                val parts = line.substring(11).trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    try {
                        val pattern = Regex(parts[0])
                        val sub = parts[1]
                        rewrites.add(Pair(pattern, sub))
                    } catch (ignored: Exception) {}
                }
            }
        }

        return HtaccessConfig(
            directoryIndex = dirIndex,
            customErrorDocuments = errorDocs,
            allowDirectoryListing = allowListing,
            denyAll = deny,
            rewriteRules = rewrites
        )
    }

    fun validateSyntax(content: String): Pair<Boolean, String> {
        val lines = content.lines()
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.startsWith("#") || line.isEmpty()) continue

            val directive = line.split(Regex("\\s+"), limit = 2)[0]
            val validDirectives = listOf(
                "DirectoryIndex", "ErrorDocument", "Options", "RewriteEngine",
                "RewriteRule", "RewriteCond", "Deny", "Allow", "Order",
                "Require", "Header", "SetEnv", "AddType", "AddDefaultCharset",
                "<IfModule", "</IfModule>", "<Files", "</Files>", "<Directory", "</Directory>"
            )

            val isValid = validDirectives.any { directive.equals(it, ignoreCase = true) }
            if (!isValid) {
                return Pair(false, "Baris ${index + 1}: Perintah '$directive' tidak dikenali atau belum didukung.")
            }
        }
        return Pair(true, "Sintaks .htaccess valid dan siap digunakan.")
    }

    fun backupHtaccess(htaccessFile: File): File? {
        if (!htaccessFile.exists()) return null
        val backupFile = File(htaccessFile.parentFile, ".htaccess.backup.${System.currentTimeMillis()}")
        return try {
            htaccessFile.copyTo(backupFile, overwrite = true)
            backupFile
        } catch (e: Exception) {
            null
        }
    }

    fun getLatestBackup(websiteDirectory: File): File? {
        val publicHtml = File(websiteDirectory, "public_html")
        val dir = if (publicHtml.exists()) publicHtml else websiteDirectory
        return dir.listFiles()?.filter { it.name.startsWith(".htaccess.backup.") }
            ?.maxByOrNull { it.lastModified() }
    }

    val DEFAULT_HTACCESS_CONTENT = """
        # ========================================================
        # SFR Host .htaccess Configuration
        # Web Hosting Server Pribadi Android
        # ========================================================
        
        # 1. Default Index Files
        DirectoryIndex index.php index.html index.htm
        
        # 2. Directory Listing (-Indexes untuk mematikan, +Indexes untuk menyalakan)
        Options -Indexes
        
        # 3. Custom Error Pages
        ErrorDocument 400 /400.html
        ErrorDocument 403 /403.html
        ErrorDocument 404 /404.html
        ErrorDocument 500 /500.html
        
        # 4. Blokir Akses File Sensitif
        <Files .env>
            Deny from all
        </Files>
        
        # 5. Clean URLs / URL Rewrite (Aktifkan jika menggunakan routing framework)
        RewriteEngine On
        # RewriteRule ^api/(.*)$ index.php?route=$1 [L,QSA]
    """.trimIndent()
}
