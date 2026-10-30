package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val dataStore = DataStoreManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                val shouldCheck = dataStore.notifCheckOnBoot.first()
                if (shouldCheck) {
                    NotificationSyncWorker.schedule(context)
                }
            }
        }
    }
}
