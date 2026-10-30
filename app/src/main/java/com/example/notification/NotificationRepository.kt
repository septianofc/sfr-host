package com.example.notification

import android.content.Context
import com.example.data.DataStoreManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class NotificationRepository(private val context: Context) {

    companion object {
        const val ENDPOINT_URL = "https://sfrhost.septianfr.web.id/notif"

        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val cacheFile: File
        get() = File(context.filesDir, "notifications_cache.json")

    private val deliveredIdsFile: File
        get() = File(context.filesDir, "delivered_notif_ids.txt")

    private val _notifications = MutableStateFlow<List<LocalNotificationRecord>>(emptyList())
    val notifications: StateFlow<List<LocalNotificationRecord>> = _notifications.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _latestUpdateInfo = MutableStateFlow<LocalNotificationRecord?>(null)
    val latestUpdateInfo: StateFlow<LocalNotificationRecord?> = _latestUpdateInfo.asStateFlow()

    private val dataStore = DataStoreManager(context)

    init {
        loadCachedNotifications()
    }

    private fun loadCachedNotifications() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val listType = Types.newParameterizedType(List::class.java, LocalNotificationRecord::class.java)
                val adapter = moshi.adapter<List<LocalNotificationRecord>>(listType)
                val list = adapter.fromJson(json) ?: emptyList()
                _notifications.value = list.sortedByDescending { it.receivedAt }

                val update = list.firstOrNull { it.type.equals("UPDATE", ignoreCase = true) }
                _latestUpdateInfo.value = update
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveNotificationsToCache(list: List<LocalNotificationRecord>) {
        try {
            val listType = Types.newParameterizedType(List::class.java, LocalNotificationRecord::class.java)
            val adapter = moshi.adapter<List<LocalNotificationRecord>>(listType)
            val json = adapter.toJson(list)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDeliveredIds(): MutableSet<String> {
        return try {
            if (deliveredIdsFile.exists()) {
                deliveredIdsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            } else {
                mutableSetOf()
            }
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun saveDeliveredIds(ids: Set<String>) {
        try {
            deliveredIdsFile.writeText(ids.joinToString("\n"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchRemoteNotifications(showSystemNotificationOnNew: Boolean = true): Result<Int> {
        return withContext(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val request = Request.Builder()
                    .url(ENDPOINT_URL)
                    .header("User-Agent", "SFRHostApp/1.0 (Android Native; SFRHOST-R-001)")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _isRefreshing.value = false
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val bodyString = response.body?.string() ?: ""
                val adapter = moshi.adapter(NotificationApiResponse::class.java)
                val parsed = adapter.fromJson(bodyString)

                if (parsed == null || !parsed.success) {
                    _isRefreshing.value = false
                    return@withContext Result.failure(Exception("Format JSON notifikasi tidak valid"))
                }

                val deliveredIds = getDeliveredIds()
                val currentList = _notifications.value.toMutableList()
                var newCount = 0
                val soundEnabled = dataStore.notifSoundEnabled.first()

                // Filter enabled items, sort newest first
                val remoteItems = parsed.notifications.filter { it.enabled != false }

                for (item in remoteItems) {
                    val alreadyDelivered = deliveredIds.contains(item.id)
                    val existingIndex = currentList.indexOfFirst { it.id == item.id }

                    if (!alreadyDelivered) {
                        newCount++
                        deliveredIds.add(item.id)

                        val record = LocalNotificationRecord(
                            id = item.id,
                            title = item.title,
                            message = item.message,
                            type = item.type.uppercase(),
                            createdAt = item.createdAt ?: "",
                            receivedAt = System.currentTimeMillis(),
                            isRead = false,
                            url = item.url,
                            priority = item.priority ?: "normal"
                        )
                        currentList.add(0, record)

                        if (showSystemNotificationOnNew) {
                            NotificationHelper.showSystemNotification(context, record, soundEnabled)
                        }

                        if (item.type.equals("UPDATE", ignoreCase = true)) {
                            _latestUpdateInfo.value = record
                        }
                    } else if (existingIndex != -1) {
                        // Update content if changed
                        val old = currentList[existingIndex]
                        currentList[existingIndex] = old.copy(
                            title = item.title,
                            message = item.message,
                            url = item.url
                        )
                    }
                }

                saveDeliveredIds(deliveredIds)
                val sorted = currentList.sortedByDescending { it.receivedAt }
                _notifications.value = sorted
                saveNotificationsToCache(sorted)

                _isRefreshing.value = false
                Result.success(newCount)
            } catch (e: Exception) {
                _isRefreshing.value = false
                // Return failure but preserve existing cached list!
                Result.failure(e)
            }
        }
    }

    fun addFromPush(title: String, message: String, type: String, id: String?, url: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val notifId = id ?: "push-${System.currentTimeMillis()}"
            val deliveredIds = getDeliveredIds()
            if (deliveredIds.contains(notifId)) return@launch

            deliveredIds.add(notifId)
            saveDeliveredIds(deliveredIds)

            val record = LocalNotificationRecord(
                id = notifId,
                title = title,
                message = message,
                type = type.uppercase(),
                createdAt = "",
                receivedAt = System.currentTimeMillis(),
                isRead = false,
                url = url,
                priority = "high"
            )

            val current = _notifications.value.toMutableList()
            current.add(0, record)
            _notifications.value = current
            saveNotificationsToCache(current)

            val soundEnabled = dataStore.notifSoundEnabled.first()
            NotificationHelper.showSystemNotification(context, record, soundEnabled)

            if (type.equals("UPDATE", ignoreCase = true)) {
                _latestUpdateInfo.value = record
            }
        }
    }

    fun markAsRead(id: String) {
        val updated = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _notifications.value = updated
        saveNotificationsToCache(updated)
    }

    fun markAllAsRead() {
        val updated = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = updated
        saveNotificationsToCache(updated)
    }

    fun deleteLocalHistory() {
        _notifications.value = emptyList()
        cacheFile.delete()
        _latestUpdateInfo.value = null
    }

    fun getUnreadCount(): Int {
        return _notifications.value.count { !it.isRead }
    }
}
