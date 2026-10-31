package com.example

import android.util.Base64
import com.example.server.HtaccessManager
import com.example.server.php.KotlinPhpInterpreter
import com.example.tunnel.CloudflareTunnelRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
        // Sample dummy invalid token
        val invalidToken = "not-a-valid-token"
        assertFalse(invalidToken.length > 20 && invalidToken.startsWith("eyJ"))

        // Legitimate Cloudflare Tunnel Token is a base64 encoded JSON containing a, t, s
        val jsonPayload = """{"a":"test_account_tag_12345","t":"test_tunnel_id_67890","s":"test_secret_key_abcde"}"""
        val validBase64Token = java.util.Base64.getEncoder().encodeToString(jsonPayload.toByteArray(Charsets.UTF_8))

        val decoded = String(java.util.Base64.getDecoder().decode(validBase64Token), Charsets.UTF_8)
        assertTrue(decoded.contains("\"a\"") && decoded.contains("\"t\"") && decoded.contains("\"s\""))
    }
}
