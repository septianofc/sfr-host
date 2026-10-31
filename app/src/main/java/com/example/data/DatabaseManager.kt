package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SfrDatabaseItem(
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val tableCount: Int,
    val engineType: String = "SQLite 3"
)

data class MySqlConnectionConfig(
    val id: String,
    val name: String,
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "root",
    val passwordMasked: String = "",
    val isLocalTermux: Boolean = host == "127.0.0.1" || host == "localhost"
)

data class MySqlTestResult(
    val isSuccess: Boolean,
    val serverVersion: String = "",
    val protocolVersion: Int = 0,
    val latencyMs: Long = 0L,
    val message: String = ""
)

data class SqlQueryResult(
    val isSuccess: Boolean,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val rowsAffected: Int = 0,
    val message: String = "",
    val executionTimeMs: Long = 0L
)

/**
 * DatabaseManager handles:
 * 1. Local SQLite 3 databases (honest naming, no false MySQL labeling).
 * 2. Real Remote/Local MySQL & MariaDB server connections via TCP handshake.
 * 3. SQL query execution, export/import dump.
 */
class DatabaseManager(private val context: Context) {

    val databaseBaseDir: File
        get() {
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED) {
                try {
                    val sharedDir = File(Environment.getExternalStorageDirectory(), "SFRHost/databases")
                    if (sharedDir.exists() || sharedDir.mkdirs()) {
                        return sharedDir
                    }
                } catch (ignored: Exception) {}

                try {
                    val appExtDir = context.getExternalFilesDir("SFRHost/databases")
                    if (appExtDir != null && (appExtDir.exists() || appExtDir.mkdirs())) {
                        return appExtDir
                    }
                } catch (ignored: Exception) {}
            }

            return File(context.filesDir, "SFRHost/databases").apply {
                if (!exists()) mkdirs()
            }
        }

    init {
        initializeSampleDatabaseIfNeeded()
    }

    private fun initializeSampleDatabaseIfNeeded() {
        val sampleDbFile = File(databaseBaseDir, "sfr_blog.db")
        if (!sampleDbFile.exists()) {
            try {
                val db = SQLiteDatabase.openOrCreateDatabase(sampleDbFile, null)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS articles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        content TEXT,
                        author TEXT DEFAULT 'Septian',
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                    );
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO articles (title, content, author) VALUES 
                    ('Selamat Datang di SFR Host', 'Web server Android Anda kini mendukung database SQLite 3 lokal dan koneksi MySQL/MariaDB riil.', 'Septian Fathur Rohman'),
                    ('Koneksi Database PHP', 'Gunakan new SQLite3() atau PDO untuk mengakses file database dari skrip PHP.', 'Admin SFR');
                """.trimIndent())
                db.close()
            } catch (ignored: Exception) {}
        }
    }

    fun listDatabases(): List<SfrDatabaseItem> {
        val files = databaseBaseDir.listFiles { _, name ->
            name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3")
        } ?: emptyArray()

        return files.map { file ->
            var tableCount = 0
            try {
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name != 'android_metadata' AND name != 'sqlite_sequence'", null)
                if (cursor.moveToFirst()) {
                    tableCount = cursor.getInt(0)
                }
                cursor.close()
                db.close()
            } catch (ignored: Exception) {}

            SfrDatabaseItem(
                name = file.name,
                file = file,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                tableCount = tableCount,
                engineType = "SQLite 3 (Lokal)"
            )
        }.sortedByDescending { it.lastModified }
    }

    fun createDatabase(name: String): Boolean {
        val cleanName = if (name.endsWith(".db")) name else "$name.db"
        val target = File(databaseBaseDir, cleanName)
        if (target.exists()) return false

        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(target, null)
            db.execSQL("CREATE TABLE IF NOT EXISTS demo (id INTEGER PRIMARY KEY AUTOINCREMENT, catatan TEXT, dibuat_pada DATETIME DEFAULT CURRENT_TIMESTAMP);")
            db.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteDatabase(fileName: String): Boolean {
        val file = File(databaseBaseDir, fileName)
        return if (file.exists()) file.delete() else false
    }

    fun executeQuery(dbFileName: String, sql: String): SqlQueryResult {
        val startTime = System.currentTimeMillis()
        val file = File(databaseBaseDir, dbFileName)
        if (!file.exists()) {
            return SqlQueryResult(
                isSuccess = false,
                message = "File database '$dbFileName' tidak ditemukan.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val trimmed = sql.trim()
        val isSelect = trimmed.startsWith("SELECT", ignoreCase = true) ||
                       trimmed.startsWith("PRAGMA", ignoreCase = true) ||
                       trimmed.startsWith("EXPLAIN", ignoreCase = true)

        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openOrCreateDatabase(file, null)
            if (isSelect) {
                val cursor = db.rawQuery(trimmed, null)
                val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                val rows = mutableListOf<List<String>>()

                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val row = mutableListOf<String>()
                    for (i in 0 until cursor.columnCount) {
                        row.add(cursor.getString(i) ?: "NULL")
                    }
                    rows.add(row)
                    count++
                }
                val totalFound = cursor.count
                cursor.close()

                SqlQueryResult(
                    isSuccess = true,
                    columns = columns,
                    rows = rows,
                    rowsAffected = totalFound,
                    message = "Query selesai. $totalFound baris ditemukan.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                db.execSQL(trimmed)
                SqlQueryResult(
                    isSuccess = true,
                    message = "Perintah SQL berhasil dieksekusi.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            SqlQueryResult(
                isSuccess = false,
                message = "Error SQL: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } finally {
            try { db?.close() } catch (ignored: Exception) {}
        }
    }

    fun exportToSql(dbFileName: String): String {
        val file = File(databaseBaseDir, dbFileName)
        if (!file.exists()) return "-- Database file not found"

        val sb = StringBuilder()
        sb.append("-- ========================================================\n")
        sb.append("-- SFR Host SQLite 3 Database Dump\n")
        sb.append("-- Database: $dbFileName\n")
        sb.append("-- Tanggal: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("-- Powered by SFR Host (Septian Fathur Rohman)\n")
        sb.append("-- ========================================================\n\n")

        try {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            val tableCursor = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table' AND name != 'android_metadata' AND name != 'sqlite_sequence'", null)

            while (tableCursor.moveToNext()) {
                val tableName = tableCursor.getString(0)
                val createSql = tableCursor.getString(1)
                sb.append("DROP TABLE IF EXISTS `$tableName`;\n")
                sb.append("$createSql;\n\n")

                val dataCursor = db.rawQuery("SELECT * FROM `$tableName`", null)
                val colCount = dataCursor.columnCount
                while (dataCursor.moveToNext()) {
                    val vals = (0 until colCount).map { i ->
                        val v = dataCursor.getString(i)
                        if (v == null) "NULL" else "'${v.replace("'", "''")}'"
                    }
                    sb.append("INSERT INTO `$tableName` VALUES (${vals.joinToString(", ")});\n")
                }
                dataCursor.close()
                sb.append("\n")
            }
            tableCursor.close()
            db.close()
        } catch (e: Exception) {
            sb.append("-- Dump Error: ${e.message}\n")
        }

        return sb.toString()
    }

    fun importSql(dbFileName: String, sqlContent: String): Pair<Boolean, String> {
        val file = File(databaseBaseDir, dbFileName)
        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            val statements = sqlContent.split(";")
            var executed = 0
            for (stmt in statements) {
                val clean = stmt.trim()
                if (clean.isNotEmpty() && !clean.startsWith("--")) {
                    db.execSQL(clean)
                    executed++
                }
            }
            db.close()
            Pair(true, "Berhasil mengimpor $executed perintah SQL ke dalam $dbFileName.")
        } catch (e: Exception) {
            Pair(false, "Gagal impor SQL: ${e.message}")
        }
    }

    /**
     * Real MySQL / MariaDB Initial Handshake Packet probe over TCP.
     * Connects via raw socket, reads the MySQL Protocol Handshake packet sent by server:
     * - [0..2]: Packet length (3 bytes)
     * - [3]: Sequence ID (0x00)
     * - [4]: Protocol Version (usually 0x0a)
     * - [5..null]: Null-terminated server version string (e.g. "10.11.4-MariaDB" or "8.0.35")
     */
    suspend fun testMySqlConnection(host: String, port: Int): MySqlTestResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), 4000)
                socket.soTimeout = 4000

                val input = socket.getInputStream()
                // Read 4 bytes header: 3 bytes payload length + 1 byte sequence id
                val header = ByteArray(4)
                var read = 0
                while (read < 4) {
                    val r = input.read(header, read, 4 - read)
                    if (r == -1) break
                    read += r
                }

                if (read < 4) {
                    return@withContext MySqlTestResult(
                        isSuccess = false,
                        message = "Koneksi terhubung namun server menutup stream sebelum handshake."
                    )
                }

                val payloadLength = (header[0].toInt() and 0xFF) or
                        ((header[1].toInt() and 0xFF) shl 8) or
                        ((header[2].toInt() and 0xFF) shl 16)

                if (payloadLength <= 0 || payloadLength > 10000) {
                    return@withContext MySqlTestResult(
                        isSuccess = false,
                        message = "Port $port terbuka namun bukan server MySQL/MariaDB standar (panjang paket handshake tidak valid)."
                    )
                }

                val payload = ByteArray(payloadLength)
                var pRead = 0
                while (pRead < payloadLength) {
                    val r = input.read(payload, pRead, payloadLength - pRead)
                    if (r == -1) break
                    pRead += r
                }

                if (pRead < 2) {
                    return@withContext MySqlTestResult(
                        isSuccess = false,
                        message = "Payload handshake terlalu pendek."
                    )
                }

                val protoVer = payload[0].toInt() and 0xFF
                // Server version string is null-terminated starting at index 1
                var nullIdx = 1
                while (nullIdx < pRead && payload[nullIdx] != 0.toByte()) {
                    nullIdx++
                }
                val serverVersion = String(payload, 1, nullIdx - 1, Charsets.UTF_8)
                val latency = System.currentTimeMillis() - startTime

                socket.close()

                MySqlTestResult(
                    isSuccess = true,
                    serverVersion = serverVersion,
                    protocolVersion = protoVer,
                    latencyMs = latency,
                    message = "✓ Terhubung ke MySQL/MariaDB server riil ($serverVersion, Latency: ${latency}ms)"
                )
            } catch (e: Exception) {
                try { socket.close() } catch (ignored: Exception) {}
                val latency = System.currentTimeMillis() - startTime
                MySqlTestResult(
                    isSuccess = false,
                    latencyMs = latency,
                    message = "Gagal terhubung ke $host:$port (${e.message})"
                )
            }
        }
    }
}
