package com.example

import android.app.Application
import com.example.data.DataStoreManager
import com.example.files.FileManager
import com.example.notification.NotificationHelper
import com.example.notification.NotificationRepository
import com.example.notification.NotificationSyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SfrApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize notification channels
        NotificationHelper.initNotificationChannels(this)

        // 2. Initialize website directories & sample assets
        FileManager(this).initializeSampleWebsitesIfNeeded()

        // 3. Schedule WorkManager periodic remote sync
        try {
            NotificationSyncWorker.schedule(this)
        } catch (e: Throwable) {
            // Handled gracefully in unit tests
        }

        // 4. Fetch initial notification sync in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationRepository.getInstance(this@SfrApplication).fetchRemoteNotifications(showSystemNotificationOnNew = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Try fetching FCM registration token
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        val dataStore = DataStoreManager(this@SfrApplication)
                        CoroutineScope(Dispatchers.IO).launch {
                            dataStore.setFcmToken(token)
                        }
                    }
                }
            } catch (e: Exception) {
                // If Play Services is not available, graceful fallback
            }
        }
    }
}
