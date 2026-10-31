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

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Device rebooted, system clock changed, timezone changed, or app updated: reschedule all notifications
            Log.d("AlarmReceiver", "Rescheduling alarms on system trigger: $action")
            NotificationScheduler.scheduleAlarms(context)
            return
        }

        when (action) {
            NotificationScheduler.ACTION_DAILY_AGENDA_CHECK -> {
                // Fired at 00:00 midnight (Pergantian Hari)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NotificationScheduler.checkAndShowDailyAgenda(context)
                        NotificationScheduler.checkAndShowDailyHolidayAndObservance(context, timeSlot = "00:00")
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Error checking daily agenda and holiday at 00:00: ${e.message}")
                    } finally {
                        // Reschedule midnight check for the next day
                        NotificationScheduler.scheduleDailyMidnightCheck(context)
                        // Also update today's remaining prayer alarms just in case
                        NotificationScheduler.schedulePrayerAlarmsForToday(context)
                        pendingResult.finish()
                    }
                }
            }

            NotificationScheduler.ACTION_MORNING_HOLIDAY_CHECK -> {
                // Fired at 06:00 morning
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NotificationScheduler.checkAndShowDailyHolidayAndObservance(context, timeSlot = "06:00")
                    } catch (e: Exception) {
                        Log.e("AlarmReceiver", "Error checking morning holiday at 06:00: ${e.message}")
                    } finally {
                        // Reschedule morning holiday check for the next day
                        NotificationScheduler.scheduleMorningHolidayCheck(context)
                        pendingResult.finish()
                    }
                }
            }

            NotificationScheduler.ACTION_PRAYER_NOTIFICATION -> {
                val prayerName = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_NAME) ?: "Sholat"
                val prayerTime = intent.getStringExtra(NotificationScheduler.EXTRA_PRAYER_TIME) ?: ""
                Log.d("AlarmReceiver", "Executing prayer notification for: $prayerName at $prayerTime")

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "MySingghara:PrayerAlarmWakeLock"
                )
                try {
                    wakeLock?.acquire(60 * 1000L) // Keep CPU awake up to 60 seconds for notification display and audio
                    NotificationScheduler.showPrayerNotification(context, prayerName, prayerTime)
                    // Immediately re-evaluate and schedule next prayer alarms (ensures continuous loop)
                    NotificationScheduler.schedulePrayerAlarmsForToday(context)
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error delivering prayer notification for $prayerName: ${e.message}")
                }
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
