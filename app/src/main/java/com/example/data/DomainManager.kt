package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.UUID

data class CustomDomainItem(
    val domain: String,
    val websiteName: String,
    val isPrimary: Boolean = false,
    val sslActive: Boolean = true,
    val forceHttps: Boolean = true,
    val dnsStatus: String = "PENDING", // VERIFIED, PENDING, FAILED
    val verificationToken: String = "",
    val resolvedIp: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class DomainManager(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, CustomDomainItem::class.java)
    private val adapter = moshi.adapter<List<CustomDomainItem>>(listType)

    private val DOMAINS_KEY = stringPreferencesKey("custom_domains_json")

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

    suspend fun addDomain(domain: String, websiteName: String): CustomDomainItem {
        val cleanDomain = domain.trim().lowercase().removePrefix("http://").removePrefix("https://").substringBefore('/')
        val token = "sfrhost-verify=" + UUID.randomUUID().toString().take(8)

        val newItem = CustomDomainItem(
            domain = cleanDomain,
            websiteName = websiteName,
            verificationToken = token,
            dnsStatus = "PENDING"
        )

        context.dataStore.edit { prefs ->
            val currentJson = prefs[DOMAINS_KEY]
            val currentList = if (!currentJson.isNullOrEmpty()) {
                adapter.fromJson(currentJson)?.toMutableList() ?: mutableListOf()
            } else mutableListOf()

            // Remove duplicate if exists
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

    suspend fun verifyDns(domain: String, expectedGatewayHostOrIp: String): CustomDomainItem? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = InetAddress.getAllByName(domain)
                val resolvedIps = addresses.map { it.hostAddress }
                val isResolved = resolvedIps.isNotEmpty()

                var updatedItem: CustomDomainItem? = null
                context.dataStore.edit { prefs ->
                    val currentJson = prefs[DOMAINS_KEY]
                    if (!currentJson.isNullOrEmpty()) {
                        val list = adapter.fromJson(currentJson)?.map { item ->
                            if (item.domain == domain) {
                                val verified = item.copy(
                                    dnsStatus = if (isResolved) "VERIFIED" else "FAILED",
                                    resolvedIp = resolvedIps.firstOrNull(),
                                    sslActive = isResolved
                                )
                                updatedItem = verified
                                verified
                            } else item
                        } ?: emptyList()
                        prefs[DOMAINS_KEY] = adapter.toJson(list)
                    }
                }
                updatedItem
            } catch (e: Exception) {
                var failedItem: CustomDomainItem? = null
                context.dataStore.edit { prefs ->
                    val currentJson = prefs[DOMAINS_KEY]
                    if (!currentJson.isNullOrEmpty()) {
                        val list = adapter.fromJson(currentJson)?.map { item ->
                            if (item.domain == domain) {
                                val failed = item.copy(dnsStatus = "FAILED", resolvedIp = null)
                                failedItem = failed
                                failed
                            } else item
                        } ?: emptyList()
                        prefs[DOMAINS_KEY] = adapter.toJson(list)
                    }
                }
                failedItem
            }
        }
    }
}
