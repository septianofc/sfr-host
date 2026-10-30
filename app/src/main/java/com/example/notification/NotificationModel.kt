package com.example.notification

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationApiResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "app") val app: String = "SFR Host",
    @Json(name = "version") val version: String? = null,
    @Json(name = "last_updated") val lastUpdated: String? = null,
    @Json(name = "notifications") val notifications: List<RemoteNotificationItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteNotificationItem(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "message") val message: String,
    @Json(name = "type") val type: String = "INFORMATION", // UPDATE, ANNOUNCEMENT, MAINTENANCE, IMPORTANT, INFORMATION
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "priority") val priority: String? = "normal", // normal, high
    @Json(name = "sound") val sound: Boolean? = true,
    @Json(name = "enabled") val enabled: Boolean? = true,
    @Json(name = "url") val url: String? = null
)

data class LocalNotificationRecord(
    val id: String,
    val title: String,
    val message: String,
    val type: String,
    val createdAt: String,
    val receivedAt: Long,
    val isRead: Boolean = false,
    val url: String? = null,
    val priority: String = "normal"
)
