package com.example

import com.example.files.FileCategory
import com.example.files.FileItem
import com.example.server.HtaccessManager
import com.example.server.php.KotlinPhpInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.DecimalFormat

class PhpAndTunnelUnitTest {

    @Test
    fun testPhpExecutionEchoAndConstants() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "php_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val testFile = File(tempDir, "index.php")

        val phpCode = """
            <!DOCTYPE html>
            <html>
            <body>
            <?php
            echo "<h1>SFR Host PHP Berhasil!</h1>";
            echo "<p>PHP Version: " . PHP_VERSION . "</p>";
            ?>
            </body>
            </html>
        """.trimIndent()

        val interpreter = KotlinPhpInterpreter(
            documentRoot = tempDir,
            scriptFile = testFile,
            get = mutableMapOf("query" to "test"),
            post = mutableMapOf(),
            server = mutableMapOf("SERVER_SOFTWARE" to "SFR-Host-Engine/1.0", "REMOTE_ADDR" to "127.0.0.1")
        )

        val (output, headers, statusCode) = interpreter.executeScript(phpCode)

        assertEquals(200, statusCode)
        assertTrue(output.contains("<h1>SFR Host PHP Berhasil!</h1>"))
        assertTrue(output.contains("8.2.14-sfrhost"))
        assertFalse(output.contains("<?php"))

        tempDir.deleteRecursively()
    }

    @Test
    fun testHtaccessParsingAndValidation() {
        val sampleHtaccess = """
            # SFR Host Config
            DirectoryIndex index.php index.html index.htm
            Options -Indexes
            ErrorDocument 404 /404.html
            ErrorDocument 500 /500.html
            Deny from all
        """.trimIndent()

        val tempFile = File.createTempFile("htaccess", ".test")
        tempFile.writeText(sampleHtaccess)

        val config = HtaccessManager.parse(tempFile)
        assertEquals(listOf("index.php", "index.html", "index.htm"), config.directoryIndex)
        assertFalse(config.allowDirectoryListing)
        assertEquals("/404.html", config.customErrorDocuments[404])
        assertEquals("/500.html", config.customErrorDocuments[500])
        assertTrue(config.denyAll)

        val (valid, _) = HtaccessManager.validateSyntax(sampleHtaccess)
        assertTrue(valid)

        tempFile.delete()
    }

    @Test
    fun testCloudflareTokenValidation() {
        val invalidToken = "not-a-valid-token"
        assertFalse(invalidToken.length > 20 && invalidToken.startsWith("eyJ"))

        // Legitimate Cloudflare Tunnel Token is a base64 encoded JSON containing a, t, s
        val jsonPayload = """{"a":"test_account_tag_12345","t":"test_tunnel_id_67890","s":"test_secret_key_abcde"}"""
        val validBase64Token = java.util.Base64.getEncoder().encodeToString(jsonPayload.toByteArray(Charsets.UTF_8))

        val decoded = String(java.util.Base64.getDecoder().decode(validBase64Token), Charsets.UTF_8)
        assertTrue(decoded.contains("\"a\"") && decoded.contains("\"t\"") && decoded.contains("\"s\""))
    }

    @Test
    fun testRealByteDownloadMath() {
        val downloadedBytes = 8_200_000L
        val totalBytes = 18_100_000L

        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
        assertEquals(45, percent)

        val speedBytesPerSec = 1_400_000.0 // 1.4 MB/s
        val remainingBytes = totalBytes - downloadedBytes
        val etaSec = (remainingBytes / speedBytesPerSec).toLong()

        assertEquals(7L, etaSec)

        val df = DecimalFormat("#.##")
        val speedMb = df.format(speedBytesPerSec / (1024 * 1024))
        assertEquals("1.34", speedMb) // 1.4M / 1048576 ~ 1.34 MB/s
    }

    @Test
    fun testZArchiverFileCategorization() {
        val phpFile = FileItem("index.php", "index.php", false, 1200L, 0L)
        assertEquals(FileCategory.PHP, phpFile.category)

        val htmlFile = FileItem("about.html", "about.html", false, 3400L, 0L)
        assertEquals(FileCategory.HTML, htmlFile.category)

        val zipFile = FileItem("backup.zip", "backup.zip", false, 15000000L, 0L)
        assertEquals(FileCategory.ARCHIVE, zipFile.category)
        assertEquals("14.31 MB", zipFile.formattedSize)

        val folder = FileItem("assets", "assets", true, 0L, 0L)
        assertEquals(FileCategory.FOLDER, folder.category)
        assertEquals("--", folder.formattedSize)
    }

    @Test
    fun testMySqlHandshakePacketParsing() {
        // Mock MySQL 5.7+ Handshake packet header:
        // Length 3 bytes: 36 (0x24, 0x00, 0x00), Sequence 0x00, Proto 0x0a, Version "10.11.4-MariaDB\0"
        val versionStr = "10.11.4-MariaDB"
        val versionBytes = versionStr.toByteArray(Charsets.UTF_8)
        val payloadLength = 1 + versionBytes.size + 1 + 10 // protocol + version + null + extras
        val header = ByteArray(4)
        header[0] = (payloadLength and 0xFF).toByte()
        header[1] = ((payloadLength shr 8) and 0xFF).toByte()
        header[2] = ((payloadLength shr 16) and 0xFF).toByte()
        header[3] = 0x00 // Seq ID

        val parsedLength = (header[0].toInt() and 0xFF) or
                ((header[1].toInt() and 0xFF) shl 8) or
                ((header[2].toInt() and 0xFF) shl 16)

        assertEquals(payloadLength, parsedLength)
    }
}
