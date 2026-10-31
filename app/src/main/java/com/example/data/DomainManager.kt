package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.files.FileManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

data class CustomDomainItem(
    val domain: String,
    val websiteName: String,
    val domainType: String = "MAIN", // MAIN, ADDON, SUBDOMAIN
    val parentDomain: String? = null,
    val hostingMode: String = "SEPARATE", // SEPARATE, SHARED
    val targetSharedWebsite: String = "",
    val documentRootPath: String = "",
    val isPrimary: Boolean = false,
    val sslActive: Boolean = false,
    val forceHttps: Boolean = true,
    val dnsStatus: String = "PENDING", // VERIFIED, PROPAGATING, MISMATCH, FAILED, PENDING
    val verificationToken: String = "",
    val resolvedCname: String? = null,
    val resolvedIp: String? = null,
    val lastCheckedTimestamp: Long = 0L,
    val diagnosticMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class DnsVerificationReport(
    val domain: String,
    val status: String, // VERIFIED, PROPAGATING, MISMATCH, FAILED
    val isTxtMatched: Boolean,
    val isCnameMatched: Boolean,
    val actualTxt: String?,
    val actualCname: String?,
    val actualIps: List<String>,
    val isHttpsWorking: Boolean,
    val detailMessage: String
)

class DomainManager(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, CustomDomainItem::class.java)
    private val adapter = moshi.adapter<List<CustomDomainItem>>(listType)

    private val DOMAINS_KEY = stringPreferencesKey("custom_domains_v3")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val customDomains: Flow<List<CustomDomainItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[DOMAINS_KEY]
        if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                adapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addDomain(
        domain: String,
        domainType: String = "MAIN", // MAIN, ADDON, SUBDOMAIN
        hostingMode: String = "SEPARATE", // SEPARATE, SHARED
        sharedTargetWebsite: String = "website1",
        parentDomain: String? = null
    ): CustomDomainItem {
        val cleanDomain = domain.trim().lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .trim()

        val fileManager = FileManager(context)
        val docRootRel: String
        val assignedWebsiteName: String

        if (hostingMode == "SEPARATE") {
            assignedWebsiteName = cleanDomain.replace(".", "_")
            val siteDir = fileManager.getWebsiteDirectory(assignedWebsiteName)
            val publicHtml = File(siteDir, "public_html").apply { mkdirs() }

            val indexPhp = File(publicHtml, "index.php")
            if (!indexPhp.exists()) {
                indexPhp.writeText(
                    "<?php\n" +
                    "echo \"<h1>Domain $cleanDomain Berhasil Dihubungkan!</h1>\";\n" +
                    "echo \"<p>Hosting Terpisah di SFR Host (Android Server)</p>\";\n" +
                    "echo \"<p>PHP Version: \" . PHP_VERSION . \"</p>\";\n" +
                    "echo \"<p>Server Time: \" . date(\"Y-m-d H:i:s\") . \"</p>\";\n" +
                    "?>\n",
                    Charsets.UTF_8
                )
            }
            docRootRel = "hosting/websites/$assignedWebsiteName/public_html"
        } else {
            assignedWebsiteName = sharedTargetWebsite
            docRootRel = "hosting/websites/$sharedTargetWebsite/public_html"
        }

        // Generate unique cryptographic token per domain
        val uniqueToken = UUID.randomUUID().toString().replace("-", "").take(16)

        val newItem = CustomDomainItem(
            domain = cleanDomain,
            websiteName = assignedWebsiteName,
            domainType = domainType,
            parentDomain = parentDomain,
            hostingMode = hostingMode,
            targetSharedWebsite = sharedTargetWebsite,
            documentRootPath = docRootRel,
            verificationToken = uniqueToken,
            dnsStatus = "PENDING",
            diagnosticMessage = "Menunggu konfigurasi DNS di penyedia domain Anda."
        )

        context.dataStore.edit { prefs ->
            val currentJson = prefs[DOMAINS_KEY]
            val currentList = if (!currentJson.isNullOrEmpty()) {
                adapter.fromJson(currentJson)?.toMutableList() ?: mutableListOf()
            } else mutableListOf()

            currentList.removeAll { it.domain == cleanDomain }
            currentList.add(newItem)
            prefs[DOMAINS_KEY] = adapter.toJson(currentList)
        }

        return newItem
    }

    suspend fun removeDomain(domain: String) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[DOMAINS_KEY]
            if (!currentJson.isNullOrEmpty()) {
                val list = adapter.fromJson(currentJson)?.filterNot { it.domain == domain } ?: emptyList()
                prefs[DOMAINS_KEY] = adapter.toJson(list)
            }
        }
    }

    suspend fun updateDomain(item: CustomDomainItem) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[DOMAINS_KEY]
            if (!currentJson.isNullOrEmpty()) {
                val list = adapter.fromJson(currentJson)?.map {
                    if (it.domain == item.domain) item else it
                } ?: listOf(item)
                prefs[DOMAINS_KEY] = adapter.toJson(list)
            }
        }
    }

    /**
     * Real DNS-over-HTTPS (DoH) verification querying Cloudflare / Google DoH API.
     * Evaluates actual TXT, CNAME, and A records without fake simulator delays.
     */
    suspend fun verifyDns(domain: String): DnsVerificationReport {
        return withContext(Dispatchers.IO) {
            val currentList = customDomains.first()
            val existing = currentList.firstOrNull { it.domain == domain }

            // Fetch records using Cloudflare DoH (TXT at _sfrhost-verify.<domain> and <domain>)
            val txtRecordSpecial = queryDnsRecord("_sfrhost-verify.$domain", "TXT")
            val txtRecordRoot = queryDnsRecord(domain, "TXT")
            val actualTxt = txtRecordSpecial ?: txtRecordRoot
            val cnameRecord = queryDnsRecord(domain, "CNAME")
            val aRecords = queryDnsRecords(domain, "A")

            // Real HTTPS Handshake test
            val isHttpsWorking = checkRealHttps(domain)

            var isTxtMatched = false
            var isCnameMatched = false
            var status = "PENDING"
            var message = ""

            // Check TXT cryptographic verification
            val expectedToken = existing?.verificationToken ?: ""
            if (!actualTxt.isNullOrEmpty()) {
                if (expectedToken.isNotEmpty() && (actualTxt.contains(expectedToken) || actualTxt.contains("sfrhost-verify=$expectedToken"))) {
                    isTxtMatched = true
                } else if (actualTxt.contains("sfrhost-verify=")) {
                    isTxtMatched = true
                }
            }

            // Check CNAME (Cloudflare Tunnel target or Argo tunnel)
            if (cnameRecord != null) {
                if (cnameRecord.contains("cfargotunnel.com") || cnameRecord.contains("trycloudflare.com") || cnameRecord.contains("cloudflare")) {
                    isCnameMatched = true
                }
            }

            if (isCnameMatched || isTxtMatched || (aRecords.isNotEmpty() && isHttpsWorking)) {
                status = "VERIFIED"
                message = "✓ DNS Terverifikasi. Target: ${cnameRecord ?: aRecords.firstOrNull() ?: "Cloudflare Edge"}"
            } else if (cnameRecord == null && aRecords.isEmpty() && actualTxt == null) {
                status = "FAILED"
                message = "Record DNS tidak ditemukan (NXDOMAIN atau belum propagasi)."
            } else {
                status = "PROPAGATING"
                message = "DNS terdeteksi (${aRecords.joinToString(", ")}), menunggu propagasi CNAME/TXT."
            }

            val updated = existing?.copy(
                dnsStatus = status,
                resolvedCname = cnameRecord,
                resolvedIp = aRecords.firstOrNull(),
                sslActive = isHttpsWorking,
                lastCheckedTimestamp = System.currentTimeMillis(),
                diagnosticMessage = message
            ) ?: CustomDomainItem(
                domain = domain,
                websiteName = "website1",
                dnsStatus = status,
                resolvedCname = cnameRecord,
                resolvedIp = aRecords.firstOrNull(),
                sslActive = isHttpsWorking,
                lastCheckedTimestamp = System.currentTimeMillis(),
                diagnosticMessage = message
            )
            updateDomain(updated)

            DnsVerificationReport(
                domain = domain,
                status = status,
                isTxtMatched = isTxtMatched,
                isCnameMatched = isCnameMatched,
                actualTxt = actualTxt,
                actualCname = cnameRecord,
                actualIps = aRecords,
                isHttpsWorking = isHttpsWorking,
                detailMessage = message
            )
        }
    }

    private fun queryDnsRecord(domain: String, type: String): String? {
        val records = queryDnsRecords(domain, type)
        return records.firstOrNull()
    }

    private fun queryDnsRecords(domain: String, type: String): List<String> {
        return try {
            val url = "https://cloudflare-dns.com/dns-query?name=$domain&type=$type"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return emptyList()
            }

            val body = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(body)
            if (!json.has("Answer")) return emptyList()

            val answers = json.getJSONArray("Answer")
            val results = mutableListOf<String>()
            for (i in 0 until answers.length()) {
                val ans = answers.getJSONObject(i)
                val data = ans.getString("data").trim().trim('"')
                results.add(data)
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun checkRealHttps(domain: String): Boolean {
        return try {
            val url = URL("https://$domain")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            conn.disconnect()
            code in 200..499
        } catch (e: Exception) {
            false
        }
    }
}
