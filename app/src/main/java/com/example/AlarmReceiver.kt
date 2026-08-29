package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("AlarmReceiver", "Received intent action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            // Device rebooted, reschedule all notifications
            NotificationScheduler.scheduleAlarms(context)
            return
        }

        when (action) {
            NotificationScheduler.ACTION_DAILY_AGENDA_CHECK -> {
                // Run the daily agenda check asynchronously
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NotificationScheduler.checkAndShowDailyAgenda(context)
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Error checking daily agenda: ${e.message}")
                    } finally {
                        // Reschedule the daily check for the next day
                        NotificationScheduler.scheduleDailyCheck(context)
                        // Also update today's remaining prayer alarms just in case
                        NotificationScheduler.schedulePrayerAlarmsForToday(context)
                        pendingResult.finish()
                    }
                }
            }

            NotificationScheduler.ACTION_PRAYER_NOTIFICATION -> {
                val prayerName = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_NAME) ?: "Sholat"
                val prayerTime = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_TIME) ?: ""
                NotificationScheduler.showPrayerNotification(context, prayerName, prayerTime)
            }

            NotificationScheduler.ACTION_STOP_AZAN -> {
                NotificationScheduler.stopAzanSound()
                val notificationId = intent.getIntExtra("notification_id", -1)
                if (notificationId != -1) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notificationManager.cancel(notificationId)
                }
            }

            NotificationScheduler.ACTION_CANCEL_AGENDA_NOTIFICATION -> {
                NotificationScheduler.cancelAgendaNotification(context)
            }

            NotificationScheduler.ACTION_REJECT_WEB_ACCESS -> {
                NotificationScheduler.cancelWebAccessNotification(context)
                com.example.ui.MainViewModel.instance?.onRejectWebAccess()
            }
        }
    }
}
