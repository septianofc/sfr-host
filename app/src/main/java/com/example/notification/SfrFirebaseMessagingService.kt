package com.example.notification

import com.example.data.DataStoreManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SfrFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val dataStore = DataStoreManager(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.setFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Pemberitahuan SFR Host"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["message"]
            ?: "Ada informasi baru dari developer SFR Host."

        val type = remoteMessage.data["type"] ?: "INFORMATION"
        val id = remoteMessage.data["id"] ?: remoteMessage.messageId
        val url = remoteMessage.data["url"]

        val repo = NotificationRepository.getInstance(applicationContext)
        repo.addFromPush(title, body, type, id, url)

        // Trigger synchronization with remote JSON endpoint to keep list up to date
        CoroutineScope(Dispatchers.IO).launch {
            repo.fetchRemoteNotifications(showSystemNotificationOnNew = false)
        }
    }
}
