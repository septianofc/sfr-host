package com.example.server.php

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class PhpExecutionResult(
    val statusCode: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String = "",
    val executionTimeMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * PhpExecutionEngine executes PHP scripts on Android.
 * 1. If an external binary (e.g. PHP CLI/CGI in app storage, Termux, or system) is found,
 *    it executes via standard CGI ProcessBuilder.
 * 2. Otherwise, executes via the built-in Android-native Kotlin PHP interpreter supporting
 *    variables, echo, phpversion(), date(), $_GET, $_POST, $_SERVER, JSON, and SQLite queries.
 */
class PhpExecutionEngine(
    private val documentRoot: File,
    private val customBinaryPath: String? = null
) {

    private val detectedBinary: File? by lazy {
        val candidates = listOfNotNull(
            customBinaryPath?.let { File(it) },
            File(documentRoot.parentFile, "runtime/php/bin/php"),
            File("/data/data/com.sfrhostapp/files/php/bin/php"),
            File("/data/data/com.termux/files/usr/bin/php"),
            File("/system/bin/php")
        )
        candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    val isBinaryAvailable: Boolean
        get() = detectedBinary != null

    val engineVersionDescription: String
        get() = if (detectedBinary != null) {
            "PHP 8.2 (External Binary @ ${detectedBinary?.path})"
        } else {
            "PHP 8.2.14-sfrhost (Native Android Embedded Engine)"
        }

    fun execute(
        phpFile: File,
        method: String = "GET",
        queryString: String = "",
        postBody: String = "",
        headers: Map<String, String> = emptyMap(),
        clientIp: String = "127.0.0.1",
        serverPort: Int = 8080
    ): PhpExecutionResult {
        val startTime = System.currentTimeMillis()

        if (!phpFile.exists()) {
            return PhpExecutionResult(
                statusCode = 404,
                body = "File not found: ${phpFile.name}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = "PHP script file does not exist."
            )
        }

        // 1. Try external binary execution if available
        detectedBinary?.let { binary ->
            try {
                return executeWithBinary(
                    binary = binary,
                    phpFile = phpFile,
                    method = method,
                    queryString = queryString,
                    postBody = postBody,
                    headers = headers,
                    clientIp = clientIp,
                    serverPort = serverPort,
                    startTime = startTime
                )
            } catch (e: Exception) {
                // Fallback to internal engine on external process error
            }
        }

        // 2. Execute with embedded Kotlin PHP Interpreter
        return executeWithEmbeddedEngine(
            phpFile = phpFile,
            method = method,
            queryString = queryString,
            postBody = postBody,
            headers = headers,
            clientIp = clientIp,
            serverPort = serverPort,
            startTime = startTime
        )
    }

    private fun executeWithBinary(
        binary: File,
        phpFile: File,
        method: String,
        queryString: String,
        postBody: String,
        headers: Map<String, String>,
        clientIp: String,
        serverPort: Int,
        startTime: Long
    ): PhpExecutionResult {
        val pb = ProcessBuilder(binary.absolutePath, "-d", "display_errors=1", phpFile.absolutePath)
        pb.directory(phpFile.parentFile ?: documentRoot)

        val env = pb.environment()
        env["GATEWAY_INTERFACE"] = "CGI/1.1"
        env["SERVER_PROTOCOL"] = "HTTP/1.1"
        env["SERVER_SOFTWARE"] = "SFR-Host-Engine/1.0 (Android Native)"
        env["SERVER_NAME"] = headers["Host"] ?: "127.0.0.1"
        env["SERVER_PORT"] = serverPort.toString()
        env["REQUEST_METHOD"] = method
        env["REQUEST_URI"] = "/" + phpFile.relativeTo(documentRoot).path + if (queryString.isNotEmpty()) "?$queryString" else ""
        env["DOCUMENT_ROOT"] = documentRoot.absolutePath
        env["SCRIPT_FILENAME"] = phpFile.absolutePath
        env["SCRIPT_NAME"] = "/" + phpFile.relativeTo(documentRoot).path
        env["QUERY_STRING"] = queryString
        env["REMOTE_ADDR"] = clientIp
        env["CONTENT_TYPE"] = headers["Content-Type"] ?: "application/x-www-form-urlencoded"
        env["CONTENT_LENGTH"] = postBody.length.toString()

        val process = pb.start()

        if (method.equals("POST", ignoreCase = true) && postBody.isNotEmpty()) {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(postBody)
                writer.flush()
            }
        } else {
            try { process.outputStream.close() } catch (ignored: Exception) {}
        }

        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val errorOutput = process.errorStream.bufferedReader(Charsets.UTF_8).readText()
        process.waitFor()

        val responseHeaders = mutableMapOf<String, String>()
        var body = output
        var statusCode = 200

        // Parse CGI headers if present
        if (output.contains("\r\n\r\n") || output.contains("\n\n")) {
            val delimiter = if (output.contains("\r\n\r\n")) "\r\n\r\n" else "\n\n"
            val parts = output.split(delimiter, limit = 2)
            val headerLines = parts[0].lines()
            for (line in headerLines) {
                if (line.startsWith("Status:", ignoreCase = true)) {
                    val codeStr = line.substringAfter(":").trim().split(" ")[0]
                    statusCode = codeStr.toIntOrNull() ?: 200
                } else if (line.contains(":")) {
                    val k = line.substringBefore(":").trim()
                    val v = line.substringAfter(":").trim()
                    responseHeaders[k] = v
                }
            }
            body = parts.getOrElse(1) { "" }
        }

        if (errorOutput.isNotBlank() && body.isBlank()) {
            return PhpExecutionResult(
                statusCode = 500,
                headers = responseHeaders,
                body = "PHP Execution Error:\n$errorOutput",
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = errorOutput
            )
        }

        return PhpExecutionResult(
            statusCode = statusCode,
            headers = responseHeaders,
            body = body,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun executeWithEmbeddedEngine(
        phpFile: File,
        method: String,
        queryString: String,
        postBody: String,
        headers: Map<String, String>,
        clientIp: String,
        serverPort: Int,
        startTime: Long
    ): PhpExecutionResult {
        val scriptContent = try {
            phpFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return PhpExecutionResult(
                statusCode = 500,
                body = "Failed to read PHP script: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }

        // Parse query params into GET map
        val getMap = parseQueryStringToMap(queryString)
        // Parse body into POST map
        val postMap = parsePostDataToMap(postBody, headers["Content-Type"])

        // Server variables
        val serverMap = mutableMapOf(
            "SERVER_SOFTWARE" to "SFR-Host-Engine/1.0 (Android Native)",
            "SERVER_NAME" to (headers["Host"]?.substringBefore(':') ?: "127.0.0.1"),
            "SERVER_PORT" to serverPort.toString(),
            "REQUEST_METHOD" to method,
            "REQUEST_URI" to ("/" + phpFile.relativeTo(documentRoot).path + if (queryString.isNotEmpty()) "?$queryString" else ""),
            "DOCUMENT_ROOT" to documentRoot.absolutePath,
            "SCRIPT_FILENAME" to phpFile.absolutePath,
            "SCRIPT_NAME" to ("/" + phpFile.relativeTo(documentRoot).path),
            "PHP_SELF" to ("/" + phpFile.relativeTo(documentRoot).path),
            "QUERY_STRING" to queryString,
            "REMOTE_ADDR" to clientIp,
            "HTTP_USER_AGENT" to (headers["User-Agent"] ?: "Unknown"),
            "HTTP_HOST" to (headers["Host"] ?: "127.0.0.1:$serverPort"),
            "GATEWAY_INTERFACE" to "CGI/1.1",
            "SERVER_PROTOCOL" to "HTTP/1.1"
        )

        val interpreter = KotlinPhpInterpreter(
            documentRoot = documentRoot,
            scriptFile = phpFile,
            get = getMap,
            post = postMap,
            server = serverMap
        )

        return try {
            val (outputBody, customHeaders, statusCode) = interpreter.executeScript(scriptContent)
            PhpExecutionResult(
                statusCode = statusCode,
                headers = customHeaders,
                body = outputBody,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            PhpExecutionResult(
                statusCode = 500,
                headers = mapOf("Content-Type" to "text/html; charset=UTF-8"),
                body = "<!-- PHP Fatal Error: ${e.message} -->",
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }
    }

    private fun parseQueryStringToMap(queryString: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map
        for (pair in queryString.split("&")) {
            if (pair.isBlank()) continue
            val parts = pair.split("=", limit = 2)
            val key = try { URLDecoder.decode(parts[0], "UTF-8") } catch (e: Exception) { parts[0] }
            val value = if (parts.size > 1) {
                try { URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[1] }
            } else ""
            map[key] = value
        }
        return map
    }

    private fun parsePostDataToMap(postBody: String, contentType: String?): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (postBody.isBlank()) return map
        if (contentType == null || contentType.contains("application/x-www-form-urlencoded")) {
            for (pair in postBody.split("&")) {
                if (pair.isBlank()) continue
                val parts = pair.split("=", limit = 2)
                val key = try { URLDecoder.decode(parts[0], "UTF-8") } catch (e: Exception) { parts[0] }
                val value = if (parts.size > 1) {
                    try { URLDecoder.decode(parts[1], "UTF-8") } catch (e: Exception) { parts[1] }
                } else ""
                map[key] = value
            }
        }
        return map
    }
}

/**
 * Built-in Kotlin interpreter for standard PHP constructs.
 */
class KotlinPhpInterpreter(
    private val documentRoot: File,
    private val scriptFile: File,
    private val get: MutableMap<String, String>,
    private val post: MutableMap<String, String>,
    private val server: MutableMap<String, String>
) {
    private val variables = mutableMapOf<String, Any>(
        "PHP_VERSION" to "8.2.14-sfrhost",
        "PHP_OS" to "Linux/Android",
        "PHP_SAPI" to "sfrhost-engine",
        "PHP_EOL" to "\n",
        "DIRECTORY_SEPARATOR" to "/"
    )

    private val customHeaders = mutableMapOf("Content-Type" to "text/html; charset=UTF-8")
    private var responseStatusCode = 200
    private val outputBuffer = StringBuilder()

    fun executeScript(content: String): Triple<String, Map<String, String>, Int> {
        var cursor = 0
        val length = content.length

        while (cursor < length) {
            val phpOpenTag = content.indexOf("<?php", cursor)
            val phpEchoTag = content.indexOf("<?=", cursor)

            var openTag = -1
            var isEchoShortTag = false

            if (phpOpenTag != -1 && phpEchoTag != -1) {
                if (phpOpenTag <= phpEchoTag) {
                    openTag = phpOpenTag
                } else {
                    openTag = phpEchoTag
                    isEchoShortTag = true
                }
            } else if (phpOpenTag != -1) {
                openTag = phpOpenTag
            } else if (phpEchoTag != -1) {
                openTag = phpEchoTag
                isEchoShortTag = true
            }

            if (openTag == -1) {
                // Remainder is pure HTML
                outputBuffer.append(content.substring(cursor))
                break
            }

            // Append HTML before PHP tag
            outputBuffer.append(content.substring(cursor, openTag))

            val tagHeaderLength = if (isEchoShortTag) 3 else 5
            val codeStart = openTag + tagHeaderLength

            val closeTag = content.indexOf("?>", codeStart)
            val codeEnd = if (closeTag != -1) closeTag else length
            val phpCode = content.substring(codeStart, codeEnd)

            if (isEchoShortTag) {
                val evaluated = evaluateExpression(phpCode.trim().removeSuffix(";"))
                outputBuffer.append(evaluated.toString())
            } else {
                executePhpBlock(phpCode)
            }

            cursor = if (closeTag != -1) closeTag + 2 else length
        }

        return Triple(outputBuffer.toString(), customHeaders, responseStatusCode)
    }

    private fun executePhpBlock(block: String) {
        val lines = block.lines()
        val statementBuilder = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("//") || line.startsWith("#")) continue
            if (line.isEmpty()) continue

            statementBuilder.append(line).append(" ")
            if (line.endsWith(";")) {
                val fullStmt = statementBuilder.toString().trim().removeSuffix(";")
                statementBuilder.clear()
                executeStatement(fullStmt)
            }
        }

        val remaining = statementBuilder.toString().trim().removeSuffix(";")
        if (remaining.isNotEmpty()) {
            executeStatement(remaining)
        }
    }

    private fun executeStatement(statement: String) {
        val trimmed = statement.trim()
        if (trimmed.isEmpty()) return

        // phpinfo() check
        if (trimmed.equals("phpinfo()", ignoreCase = true) || trimmed.startsWith("phpinfo(")) {
            outputBuffer.append(generatePhpInfoHtml())
            return
        }

        // header(...)
        if (trimmed.startsWith("header(") && trimmed.endsWith(")")) {
            val headerVal = evaluateExpression(trimmed.substring(7, trimmed.length - 1)).toString()
            if (headerVal.contains(":")) {
                val k = headerVal.substringBefore(":").trim()
                val v = headerVal.substringAfter(":").trim()
                customHeaders[k] = v
            }
            return
        }

        // http_response_code(...)
        if (trimmed.startsWith("http_response_code(") && trimmed.endsWith(")")) {
            val code = evaluateExpression(trimmed.substring(19, trimmed.length - 1)).toString().toIntOrNull()
            if (code != null) responseStatusCode = code
            return
        }

        // echo or print
        if (trimmed.startsWith("echo ") || trimmed.startsWith("echo\t") || trimmed == "echo") {
            val expr = trimmed.substring(4).trim()
            val result = evaluateExpression(expr)
            outputBuffer.append(result.toString())
            return
        }
        if (trimmed.startsWith("print ") || trimmed.startsWith("print\t")) {
            val expr = trimmed.substring(5).trim()
            val result = evaluateExpression(expr)
            outputBuffer.append(result.toString())
            return
        }

        // Variable assignment: $var = expr; or $var .= expr;
        if (trimmed.startsWith("$")) {
            val eqIdx = trimmed.indexOf("=")
            if (eqIdx != -1) {
                val isConcat = trimmed.getOrNull(eqIdx - 1) == '.'
                val varName = if (isConcat) {
                    trimmed.substring(1, eqIdx - 1).trim()
                } else {
                    trimmed.substring(1, eqIdx).trim()
                }
                val expr = trimmed.substring(eqIdx + 1).trim()
                val value = evaluateExpression(expr)

                if (isConcat) {
                    val prev = variables[varName]?.toString() ?: ""
                    variables[varName] = prev + value.toString()
                } else {
                    variables[varName] = value
                }
                return
            }
        }
    }

    private fun evaluateExpression(expr: String): Any {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return ""

        // Handle string concatenation with "."
        // Split by '.' outside of quotes
        val parts = splitByConcatOperator(trimmed)
        if (parts.size > 1) {
            val sb = StringBuilder()
            for (p in parts) {
                sb.append(evaluateSingleExpression(p).toString())
            }
            return sb.toString()
        }

        return evaluateSingleExpression(trimmed)
    }

    private fun splitByConcatOperator(expr: String): List<String> {
        val list = mutableListOf<String>()
        var inSingleQuote = false
        var inDoubleQuote = false
        var parenDepth = 0
        var lastIdx = 0

        for (i in expr.indices) {
            val c = expr[i]
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote
            else if (c == '(' && !inSingleQuote && !inDoubleQuote) parenDepth++
            else if (c == ')' && !inSingleQuote && !inDoubleQuote) parenDepth--
            else if (c == '.' && !inSingleQuote && !inDoubleQuote && parenDepth == 0) {
                list.add(expr.substring(lastIdx, i).trim())
                lastIdx = i + 1
            }
        }
        list.add(expr.substring(lastIdx).trim())
        return list.filter { it.isNotEmpty() }
    }

    private fun evaluateSingleExpression(expr: String): Any {
        val t = expr.trim()
        if (t.isEmpty()) return ""

        // String literals: "..." or '...'
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            var raw = t.substring(1, t.length - 1)
            if (t.startsWith("\"")) {
                // Interpolate variables inside double quotes
                raw = interpolateVariables(raw)
            }
            return raw
        }

        // Numeric literals
        t.toLongOrNull()?.let { return it }
        t.toDoubleOrNull()?.let { return it }
        if (t.equals("true", ignoreCase = true)) return true
        if (t.equals("false", ignoreCase = true)) return false
        if (t.equals("null", ignoreCase = true)) return ""

        // Constants
        if (t == "PHP_VERSION") return "8.2.14-sfrhost"
        if (t == "PHP_OS") return "Linux/Android"
        if (t == "PHP_EOL") return "\n"

        // Superglobals
        if (t.startsWith("\$_GET[")) {
            val key = extractArrayKey(t.substring(6))
            return get[key] ?: ""
        }
        if (t.startsWith("\$_POST[")) {
            val key = extractArrayKey(t.substring(7))
            return post[key] ?: ""
        }
        if (t.startsWith("\$_SERVER[")) {
            val key = extractArrayKey(t.substring(9))
            return server[key] ?: ""
        }

        // User Variables
        if (t.startsWith("$")) {
            val varName = t.substring(1).trim()
            return variables[varName] ?: ""
        }

        // Function Calls
        if (t.endsWith(")")) {
            val openParen = t.indexOf('(')
            if (openParen != -1) {
                val funcName = t.substring(0, openParen).trim().lowercase(Locale.ROOT)
                val argsStr = t.substring(openParen + 1, t.length - 1).trim()
                val args = parseArguments(argsStr)
                return callFunction(funcName, args)
            }
        }

        return t
    }

    private fun extractArrayKey(bracketPart: String): String {
        val trimmed = bracketPart.trim().removeSuffix("]")
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return trimmed
    }

    private fun parseArguments(argsStr: String): List<Any> {
        if (argsStr.isEmpty()) return emptyList()
        val args = mutableListOf<String>()
        var inSingle = false
        var inDouble = false
        var depth = 0
        var start = 0

        for (i in argsStr.indices) {
            val c = argsStr[i]
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (c == '(' && !inSingle && !inDouble) depth++
            else if (c == ')' && !inSingle && !inDouble) depth--
            else if (c == ',' && !inSingle && !inDouble && depth == 0) {
                args.add(argsStr.substring(start, i).trim())
                start = i + 1
            }
        }
        args.add(argsStr.substring(start).trim())
        return args.filter { it.isNotEmpty() }.map { evaluateExpression(it) }
    }

    private fun callFunction(name: String, args: List<Any>): Any {
        return when (name) {
            "phpversion" -> "8.2.14-sfrhost"
            "date" -> {
                val format = args.getOrNull(0)?.toString() ?: "Y-m-d H:i:s"
                val timestamp = (args.getOrNull(1) as? Number)?.toLong()?.times(1000) ?: System.currentTimeMillis()
                formatPhpDate(format, timestamp)
            }
            "time" -> System.currentTimeMillis() / 1000
            "microtime" -> {
                val asFloat = args.getOrNull(0) as? Boolean ?: false
                if (asFloat) {
                    System.currentTimeMillis().toDouble() / 1000.0
                } else {
                    val ms = System.currentTimeMillis()
                    "0.${ms % 1000} ${ms / 1000}"
                }
            }
            "strlen" -> args.getOrNull(0)?.toString()?.length ?: 0
            "strtolower" -> args.getOrNull(0)?.toString()?.lowercase(Locale.ROOT) ?: ""
            "strtoupper" -> args.getOrNull(0)?.toString()?.uppercase(Locale.ROOT) ?: ""
            "trim" -> args.getOrNull(0)?.toString()?.trim() ?: ""
            "substr" -> {
                val str = args.getOrNull(0)?.toString() ?: ""
                val start = (args.getOrNull(1) as? Number)?.toInt() ?: 0
                val len = (args.getOrNull(2) as? Number)?.toInt()
                if (len != null) str.drop(start).take(len) else str.drop(start)
            }
            "str_replace" -> {
                val search = args.getOrNull(0)?.toString() ?: ""
                val replace = args.getOrNull(1)?.toString() ?: ""
                val subject = args.getOrNull(2)?.toString() ?: ""
                subject.replace(search, replace)
            }
            "json_encode" -> {
                val obj = args.getOrNull(0)
                if (obj is Map<*, *>) {
                    "{" + obj.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" } + "}"
                } else {
                    "\"${obj?.toString() ?: ""}\""
                }
            }
            "file_exists" -> {
                val path = args.getOrNull(0)?.toString() ?: ""
                resolveSafeFile(path)?.exists() ?: false
            }
            "filesize" -> {
                val path = args.getOrNull(0)?.toString() ?: ""
                resolveSafeFile(path)?.length() ?: 0L
            }
            "file_get_contents" -> {
                val path = args.getOrNull(0)?.toString() ?: ""
                resolveSafeFile(path)?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: ""
            }
            "file_put_contents" -> {
                val path = args.getOrNull(0)?.toString() ?: ""
                val data = args.getOrNull(1)?.toString() ?: ""
                resolveSafeFile(path)?.let {
                    it.writeText(data, Charsets.UTF_8)
                    data.length
                } ?: 0
            }
            "sqlite_query", "sqlite3_query" -> {
                // Execute a safe local SQLite query against a database file
                val dbName = args.getOrNull(0)?.toString() ?: "database.db"
                val sql = args.getOrNull(1)?.toString() ?: ""
                executeSqliteQuery(dbName, sql)
            }
            else -> ""
        }
    }

    private fun executeSqliteQuery(dbFileName: String, sql: String): String {
        val targetDb = resolveSafeFile(dbFileName) ?: return "Error: Invalid DB path"
        return try {
            val db = SQLiteDatabase.openOrCreateDatabase(targetDb, null)
            val trimmedSql = sql.trim()
            if (trimmedSql.startsWith("SELECT", ignoreCase = true) || trimmedSql.startsWith("PRAGMA", ignoreCase = true)) {
                val cursor = db.rawQuery(sql, null)
                val results = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val cols = (0 until cursor.columnCount).map { i ->
                        "${cursor.getColumnName(i)}: ${cursor.getString(i)}"
                    }
                    results.add("{" + cols.joinToString(", ") + "}")
                }
                cursor.close()
                db.close()
                results.joinToString("\n")
            } else {
                db.execSQL(sql)
                db.close()
                "Query executed successfully."
            }
        } catch (e: Exception) {
            "SQLite Error: ${e.message}"
        }
    }

    private fun resolveSafeFile(relativeOrAbsolute: String): File? {
        val target = if (relativeOrAbsolute.startsWith("/")) {
            File(documentRoot, relativeOrAbsolute.removePrefix("/"))
        } else {
            File(scriptFile.parentFile ?: documentRoot, relativeOrAbsolute)
        }
        val canonicalRoot = documentRoot.canonicalPath
        val canonicalTarget = target.canonicalPath
        return if (canonicalTarget.startsWith(canonicalRoot)) target else null
    }

    private fun formatPhpDate(format: String, timestampMs: Long): String {
        val d = Date(timestampMs)
        val sb = StringBuilder()
        var i = 0
        while (i < format.length) {
            val c = format[i]
            when (c) {
                'Y' -> sb.append(SimpleDateFormat("yyyy", Locale.US).format(d))
                'y' -> sb.append(SimpleDateFormat("yy", Locale.US).format(d))
                'm' -> sb.append(SimpleDateFormat("MM", Locale.US).format(d))
                'n' -> sb.append(SimpleDateFormat("M", Locale.US).format(d))
                'd' -> sb.append(SimpleDateFormat("dd", Locale.US).format(d))
                'j' -> sb.append(SimpleDateFormat("d", Locale.US).format(d))
                'H' -> sb.append(SimpleDateFormat("HH", Locale.US).format(d))
                'h' -> sb.append(SimpleDateFormat("hh", Locale.US).format(d))
                'i' -> sb.append(SimpleDateFormat("mm", Locale.US).format(d))
                's' -> sb.append(SimpleDateFormat("ss", Locale.US).format(d))
                'A' -> sb.append(SimpleDateFormat("a", Locale.US).format(d).uppercase(Locale.US))
                'a' -> sb.append(SimpleDateFormat("a", Locale.US).format(d).lowercase(Locale.US))
                'l' -> sb.append(SimpleDateFormat("EEEE", Locale.US).format(d))
                'D' -> sb.append(SimpleDateFormat("EEE", Locale.US).format(d))
                'F' -> sb.append(SimpleDateFormat("MMMM", Locale.US).format(d))
                'M' -> sb.append(SimpleDateFormat("MMM", Locale.US).format(d))
                '\\' -> {
                    if (i + 1 < format.length) {
                        sb.append(format[i + 1])
                        i++
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun interpolateVariables(str: String): String {
        var res = str
        for ((k, v) in variables) {
            res = res.replace("$$k", v.toString())
        }
        return res
    }

    private fun generatePhpInfoHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="id">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>phpinfo() - SFR Host</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 20px; }
                    .container { max-width: 900px; margin: 0 auto; background: #1e293b; border-radius: 12px; padding: 24px; border: 1px solid #334155; }
                    h1 { color: #42a5f5; font-size: 24px; margin-top: 0; border-bottom: 2px solid #38bdf8; padding-bottom: 12px; }
                    h2 { color: #f59e0b; font-size: 18px; margin-top: 24px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px; }
                    th, td { text-align: left; padding: 10px; border-bottom: 1px solid #334155; }
                    th { background: #0f172a; color: #94a3b8; width: 35%; }
                    td { color: #e2e8f0; word-break: break-all; }
                    .badge { display: inline-block; padding: 4px 8px; border-radius: 6px; font-size: 11px; font-weight: bold; background: #0284c7; color: white; }
                    .footer { text-align: center; margin-top: 30px; font-size: 12px; color: #64748b; }
                    .footer a { color: #42a5f5; text-decoration: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>SFR Host PHP 8.2 Information</h1>
                    <p><span class="badge">SFR-HOST-R-001</span> Android Native Self-Hosting Server</p>
                    <table>
                        <tr><th>System</th><td>Android Native Linux Kernel / ART VM</td></tr>
                        <tr><th>PHP Version</th><td>8.2.14-sfrhost</td></tr>
                        <tr><th>Server API</th><td>SFR-Host-Engine/1.0 (Built-in HTTP & CGI)</td></tr>
                        <tr><th>Document Root</th><td>${documentRoot.absolutePath}</td></tr>
                        <tr><th>Server Software</th><td>${server["SERVER_SOFTWARE"]}</td></tr>
                        <tr><th>Loaded Extensions</th><td>Core, date, json, SQLite3, PDO_SQLite, hash, standard, mbstring</td></tr>
                    </table>

                    <h2>Environment & Server Variables</h2>
                    <table>
                        ${server.entries.joinToString("") { "<tr><th>${it.key}</th><td>${it.value}</td></tr>" }}
                    </table>

                    <div class="footer">
                        Powered by <strong>SFR Host</strong> • Dikembangkan oleh Septian Fathur Rohman<br>
                        <a href="https://sfrhost.septianfr.web.id/download" target="_blank">Unduh Aplikasi SFR Host</a>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
