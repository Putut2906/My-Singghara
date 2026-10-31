package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NotificationScheduler
import com.example.R
import com.example.data.AppDatabase
import com.example.data.FcmNotification
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "Refreshed FCM token: $token")
        val prefs = getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).putString("fcm_real_token", token).apply()
        try {
            com.example.ui.MainViewModel.instance?.apply {
                fcmToken = token
                isFcmConnected = true
                fcmTokenError = null
            }
        } catch (e: Exception) {
            Log.e("FCM_SERVICE", "Error updating ViewModel instance on new token", e)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_SERVICE", "From: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Pesan My Singghara"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: ""

        val dataString = if (remoteMessage.data.isNotEmpty()) {
            remoteMessage.data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        } else ""

        val notificationItem = FcmNotification(
            title = title,
            body = body,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            dataPayload = dataString
        )

        // Save to Room database
        val db = AppDatabase.getDatabase(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            var savedItem = notificationItem
            try {
                val insertedId = db.fcmNotificationDao().insertNotification(notificationItem)
                savedItem = notificationItem.copy(id = insertedId)
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Error saving FCM notification to DB", e)
            }

            val isForeground = MainActivity.isAppInForeground
            val viewModelInstance = com.example.ui.MainViewModel.instance
            Log.d("FCM_SERVICE", "FCM received. isAppInForeground: $isForeground, hasViewModel: ${viewModelInstance != null}")

            if (isForeground && viewModelInstance != null) {
                // Aplikasi sedang terbuka: Tampilkan pop-up In-App Messaging langsung di layar
                CoroutineScope(Dispatchers.Main).launch {
                    viewModelInstance.showInAppMessage(savedItem)
                }
            } else {
                // Aplikasi sedang tertutup / di latar belakang: Berikan notifikasi sistem OS Android
                NotificationScheduler.showFcmNotification(applicationContext, title, body, dataString)
            }
        }
    }
}
