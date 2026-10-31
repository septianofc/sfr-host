package com.example.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SfrDatabaseItem(
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val lastModified: Long,
    val tableCount: Int,
    val associatedUsers: List<String> = emptyList()
)

data class SfrDatabaseUser(
    val username: String,
    val passwordMasked: String,
    val privileges: String = "ALL PRIVILEGES",
    val assignedDatabases: List<String> = emptyList()
)

data class SqlQueryResult(
    val isSuccess: Boolean,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val rowsAffected: Int = 0,
    val message: String = "",
    val executionTimeMs: Long = 0L
)

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
                    ('Selamat Datang di SFR Host', 'Web server Android Anda kini mendukung database SQLite dan PHP 8.', 'Septian Fathur Rohman'),
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
                tableCount = tableCount
            )
        }.sortedByDescending { it.lastModified }
    }

    fun createDatabase(name: String): Boolean {
        val cleanName = if (name.endsWith(".db")) name else "$name.db"
        val target = File(databaseBaseDir, cleanName)
        if (target.exists()) return false

        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(target, null)
            db.execSQL("CREATE TABLE IF NOT EXISTS demo (id INTEGER PRIMARY KEY AUTOINCREMENT, note TEXT);")
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
        sb.append("-- SFR Host SQLite Database Dump\n")
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

                // Export data
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
}
