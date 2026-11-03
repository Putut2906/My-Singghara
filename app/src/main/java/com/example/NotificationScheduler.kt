package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import com.example.network.HolidayClient
import com.example.ui.components.PrayerTimesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {

    const val CHANNEL_PRAYER = "prayer_times_channel"
    const val CHANNEL_PRAYER_SILENT = "prayer_times_channel_silent"
    const val CHANNEL_PRAYER_NOTIFICATION = "prayer_times_channel_notification"
    const val CHANNEL_PRAYER_AZAN = "prayer_times_channel_azan"
    const val CHANNEL_AGENDA = "agenda_channel"
    const val CHANNEL_WEB_SECURITY = "web_security_channel"
    const val CHANNEL_WEB_SECURITY_RESULT = "web_security_result_channel"
    const val CHANNEL_HOLIDAY = "holiday_channel"
    const val CHANNEL_FCM = "fcm_cloud_messaging_channel"
    const val CHANNEL_APP_UPDATE = "app_update_channel"

    const val NOTIFICATION_ID_AGENDA = 8888
    const val NOTIFICATION_ID_PRAYER = 9999
    const val NOTIFICATION_ID_WEB_SECURITY = 7777
    const val NOTIFICATION_ID_WEB_SECURITY_RESULT = 7778
    const val NOTIFICATION_ID_HOLIDAY = 6666
    const val NOTIFICATION_ID_FCM = 5555
    const val NOTIFICATION_ID_APP_UPDATE = 4804

    const val ACTION_DAILY_AGENDA_CHECK = "com.example.ACTION_DAILY_AGENDA_CHECK"
    const val ACTION_MORNING_HOLIDAY_CHECK = "com.example.ACTION_MORNING_HOLIDAY_CHECK"
    const val ACTION_PRAYER_NOTIFICATION = "com.example.ACTION_PRAYER_NOTIFICATION"
    const val ACTION_CANCEL_AGENDA_NOTIFICATION = "com.example.ACTION_CANCEL_AGENDA_NOTIFICATION"
    const val ACTION_STOP_AZAN = "com.example.ACTION_STOP_AZAN"
    const val ACTION_REJECT_WEB_ACCESS = "com.example.ACTION_REJECT_WEB_ACCESS"
    const val ACTION_APPROVE_WEB_ACCESS = "com.example.ACTION_APPROVE_WEB_ACCESS"

    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_PRAYER_TIME = "extra_prayer_time"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1a. Prayer Channel - Silent
            val silentChannel = NotificationChannel(
                CHANNEL_PRAYER_SILENT,
                "Jadwal Sholat (Senyap)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat waktu sholat tanpa suara"
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(silentChannel)

            // 1b. Prayer Channel - Suara Notifikasi Saja (Nada notifikasi singkat sistem, tidak menggunakan alarm OS agar tidak berbunyi terus)
            try {
                notificationManager.deleteNotificationChannel("prayer_times_channel_system")
            } catch (_: Exception) {}

            val notificationSoundChannel = NotificationChannel(
                CHANNEL_PRAYER_NOTIFICATION,
                "Jadwal Sholat (Suara Notifikasi)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat waktu sholat dengan suara notifikasi standar (berbunyi sekali)"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    soundUri,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(notificationSoundChannel)

            // 1c. Prayer Channel - Azan Madinah
            val azanChannel = NotificationChannel(
                CHANNEL_PRAYER_AZAN,
                "Jadwal Sholat (Azan Madinah)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat waktu sholat dengan Azan Madinah"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Azan diputar oleh AzanService, bukan oleh kanal notifikasi
            }
            notificationManager.createNotificationChannel(azanChannel)

            // 2. Agenda Channel
            val agendaChannel = NotificationChannel(
                CHANNEL_AGENDA,
                "Agenda Kegiatan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi agenda kegiatan harian"
                enableVibration(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(agendaChannel)

            // 3. Web PC Access Security Channel (High Priority Heads-Up + Lock Screen)
            val webSecurityChannel = NotificationChannel(
                CHANNEL_WEB_SECURITY,
                "Keamanan Akses Web PC",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi verifikasi keamanan saat ada perangkat PC mengakses Web Singghara"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(webSecurityChannel)

            // 3b. Result Channel for Web PC access (silent confirmation)
            val webSecurityResultChannel = NotificationChannel(
                CHANNEL_WEB_SECURITY_RESULT,
                "Hasil Keamanan Akses Web PC",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Konfirmasi senyap setelah permintaan akses Web PC diizinkan atau ditolak"
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(webSecurityResultChannel)
            // 4. Holiday & Observance Channel
            val holidayChannel = NotificationChannel(
                CHANNEL_HOLIDAY,
                "Hari Libur & Peringatan Nasional",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi hari libur nasional, hari besar, dan hari peringatan nasional"
                enableVibration(true)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(holidayChannel)

            // 5. Firebase Cloud Messaging Channel (High Priority Heads-Up Notification)
            val fcmChannel = NotificationChannel(
                CHANNEL_FCM,
                "Pesan Cloud Messaging",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pesan siaran dari Firebase Cloud Messaging"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(fcmChannel)

            // 6. Pembaruan Versi Aplikasi Channel
            val appUpdateChannel = NotificationChannel(
                CHANNEL_APP_UPDATE,
                "Pembaruan Versi Aplikasi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi saat versi My Singghara yang baru tersedia untuk diunduh"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(appUpdateChannel)
        }
    }

    // Muncul di status bar ketika pemeriksa versi menemukan build yang lebih baru.
    // Satu versi hanya sekali dinotifikasikan (kunci dedup disimpan di SharedPreferences).
    fun showUpdateAvailableNotification(context: Context, versionLabel: String, source: String) {
        if (versionLabel.isBlank()) return
        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val notifiedKey = "notified_update_version"
        val dedupKey = "$source:$versionLabel"
        if (prefs.getString(notifiedKey, null) == dedupKey) return

        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NAVIGATE_TO", "tentang_aplikasi")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_APP_UPDATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("Pembaruan My Singghara tersedia")
            .setContentText("$versionLabel via $source. Ketuk untuk mengunduh dan memasang.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Versi terbaru My Singghara ($versionLabel) sudah tersedia melalui $source.\n\n" +
                    "Ketuk notifikasi ini untuk membuka halaman pembaruan di dalam aplikasi, lalu unduh dan pasang APK-nya."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_APP_UPDATE, notification)
        prefs.edit().putString(notifiedKey, dedupKey).apply()
    }

    fun cancelUpdateAvailableNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_APP_UPDATE)
    }

    fun showFcmNotification(context: Context, title: String, body: String, dataPayload: String = "") {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_fcm_notifications", true)
            putExtra("fcm_title", title)
            putExtra("fcm_body", body)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_FCM)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (System.currentTimeMillis() % 100000).toInt() + 1000
        notificationManager.notify(notificationId, notification)
    }

    fun showWebAccessNotification(context: Context, clientIp: String, timestamp: Long = System.currentTimeMillis()) {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent untuk membuka dialog pop-up konfirmasi interaktif (WebAuthPromptActivity)
        val openIntent = Intent(context, WebAuthPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("WEB_AUTH_CLIENT_IP", clientIp)
            putExtra("WEB_AUTH_TIMESTAMP", timestamp)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            7701,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksi "Ya, ini saya" langsung mengizinkan sesi PC (gaya Google Prompt)
        val approveIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_APPROVE_WEB_ACCESS
            putExtra("WEB_AUTH_CLIENT_IP", clientIp)
        }
        val pendingApproveIntent = PendingIntent.getBroadcast(
            context,
            7703,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Aksi "Bukan" langsung menolak sesi PC
        val rejectIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REJECT_WEB_ACCESS
            putExtra("WEB_AUTH_CLIENT_IP", clientIp)
        }
        val pendingRejectIntent = PendingIntent.getBroadcast(
            context,
            7702,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_WEB_SECURITY)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("Apakah anda akses web pc?")
            .setContentText("Perangkat PC ($clientIp) meminta akses. Ketuk \"Ya, ini saya\" jika itu Anda.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Perangkat dengan IP $clientIp meminta akses ke Web PC My Singghara pada pukul $timeStr.\n\n" +
                "Ketuk \"Ya, ini saya\" jika Anda yang membuka Web PC, atau \"Bukan\" untuk menolak permintaan ini. " +
                "Notifikasi ini tetap muncul saat Anda sedang membuka aplikasi lain maupun saat layar perangkat terkunci."
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingOpenIntent)
            .addAction(0, "Ya, ini saya", pendingApproveIntent)
            .addAction(0, "Bukan", pendingRejectIntent)

        // Notifikasi hanya muncul di status bar. Aplikasi tidak dibuka otomatis:
        // pengguna mengetuk notifikasi jika ingin meninjau permintaan akses.
        notificationManager.notify(NOTIFICATION_ID_WEB_SECURITY, notificationBuilder.build())
    }

    fun showWebAccessResultNotification(context: Context, approved: Boolean) {
        createNotificationChannels(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_WEB_SECURITY_RESULT)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle(if (approved) "Akses Web PC diizinkan" else "Akses Web PC ditolak")
            .setContentText(
                if (approved) "Anda menyetujui permintaan akses Web PC (\"Ya, ini saya\")."
                else "Anda menolak permintaan akses Web PC (\"Bukan\")."
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(6000L)
            .build()

        notificationManager.notify(NOTIFICATION_ID_WEB_SECURITY_RESULT, notification)
    }

    fun cancelWebAccessNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_WEB_SECURITY)
    }

    fun scheduleAlarms(context: Context) {
        createNotificationChannels(context)
        scheduleDailyMidnightCheck(context)
        scheduleMorningHolidayCheck(context)
        schedulePrayerAlarmsForToday(context)
        // Pastikan pengecekan agenda kegiatan harian tetap berjalan jika ada agenda hari ini
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAndShowDailyAgenda(context)
            } catch (e: Exception) {
                Log.e("NotificationScheduler", "Failed to check agenda on schedule: ${e.message}")
            }
        }
    }

    // Alias for backward compatibility
    fun scheduleDailyCheck(context: Context) {
        scheduleDailyMidnightCheck(context)
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleDailyMidnightCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DAILY_AGENDA_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If 00:00 has already passed today, schedule for tomorrow at 00:00
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val triggerTime = calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("NotificationScheduler", "Scheduled midnight check (00:00) at: " + Date(triggerTime))
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to schedule midnight check: " + e.message)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleMorningHolidayCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MORNING_HOLIDAY_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1060,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If 06:00 has already passed today, schedule for tomorrow at 06:00
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val triggerTime = calendar.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d("NotificationScheduler", "Scheduled morning holiday check (06:00) at: " + Date(triggerTime))
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to schedule morning holiday check: " + e.message)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun schedulePrayerAlarmsForToday(context: Context) {
        val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val latStr = sharedPrefs.getString("user_latitude", null)
        val lngStr = sharedPrefs.getString("user_longitude", null)
        val activeTimeZone = sharedPrefs.getString("active_timezone", "WIB") ?: "WIB"

        val calendar = Calendar.getInstance()
        
        val cachedDate = sharedPrefs.getString("kemenag_cache_date", null)
        val todayStr = "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}-${String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        
        val kemenagSchedule = if (cachedDate == todayStr) {
            val imsak = sharedPrefs.getString("kemenag_imsak", null)
            val subuh = sharedPrefs.getString("kemenag_subuh", null)
            val dzuhur = sharedPrefs.getString("kemenag_dzuhur", null)
            val ashar = sharedPrefs.getString("kemenag_ashar", null)
            val maghrib = sharedPrefs.getString("kemenag_maghrib", null)
            val isya = sharedPrefs.getString("kemenag_isya", null)
            if (imsak != null && subuh != null && dzuhur != null && ashar != null && maghrib != null && isya != null) {
                com.example.ui.components.PrayerSchedule(imsak, subuh, dzuhur, ashar, maghrib, isya)
            } else null
        } else null

        val schedule = kemenagSchedule ?: if (latStr != null && lngStr != null) {
            val userLat = latStr.toDoubleOrNull()
            val userLng = lngStr.toDoubleOrNull()
            if (userLat != null && userLng != null) {
                val offsetHours = calendar.timeZone.getOffset(System.currentTimeMillis()) / 3600000.0
                PrayerTimesHelper.getPrayerTimesForLocation(userLat, userLng, offsetHours, calendar)
            } else {
                PrayerTimesHelper.getPrayerTimesForTimezone(activeTimeZone, calendar)
            }
        } else {
            PrayerTimesHelper.getPrayerTimesForTimezone(activeTimeZone, calendar)
        }

        val prayerTimes = listOf(
            "Imsak" to schedule.imsak,
            "Subuh" to schedule.subuh,
            "Dzuhur" to schedule.dzuhur,
            "Ashar" to schedule.ashar,
            "Maghrib" to schedule.maghrib,
            "Isya" to schedule.isya
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        prayerTimes.forEachIndexed { index, (name, timeStr) ->
            val timeParts = timeStr.split(":")
            if (timeParts.size == 2) {
                val hour = timeParts[0].toIntOrNull() ?: 0
                val minute = timeParts[1].toIntOrNull() ?: 0

                val prayerCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If the prayer time for today has already passed, schedule it for TOMORROW!
                // This ensures every single prayer slot ALWAYS has an active pending alarm queued up,
                // completely eliminating gaps between evening and midnight or when opening the app midday.
                if (prayerCal.timeInMillis <= System.currentTimeMillis()) {
                    prayerCal.add(Calendar.DAY_OF_YEAR, 1)
                }

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_PRAYER_NOTIFICATION
                    putExtra(EXTRA_PRAYER_NAME, name)
                    putExtra(EXTRA_PRAYER_TIME, timeStr)
                }

                // Request codes: 1001 for Imsak, 1002 for Subuh, etc.
                val requestCode = 1001 + index
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    // METHOD 1: setAlarmClock
                    // AlarmClockInfo has the highest execution priority in Android.
                    // It wakes up the CPU from deep Doze mode, ignores OEM battery standby, fires at the exact second,
                    // and does not require SCHEDULE_EXACT_ALARM special user-level app-op approval.
                    // showIntent sengaja null: jam alarm sistem tidak boleh membuka aktivitas aplikasi.
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(prayerCal.timeInMillis, null)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("NotificationScheduler", "Scheduled prayer with setAlarmClock: $name at ${Date(prayerCal.timeInMillis)}")
                } catch (e: Exception) {
                    Log.w("NotificationScheduler", "setAlarmClock failed for $name: ${e.message}, falling back to setExactAndAllowWhileIdle")
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayerCal.timeInMillis, pendingIntent)
                            } else {
                                alarmManager.set(AlarmManager.RTC_WAKEUP, prayerCal.timeInMillis, pendingIntent)
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prayerCal.timeInMillis, pendingIntent)
                        }
                        Log.d("NotificationScheduler", "Scheduled prayer fallback: $name at $timeStr")
                    } catch (e2: Exception) {
                        Log.e("NotificationScheduler", "Failed to schedule prayer $name: ${e2.message}")
                    }
                }
            }
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleCancelAgendaAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_CANCEL_AGENDA_NOTIFICATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel exactly 23 hours from now
        val cancelTime = System.currentTimeMillis() + 23 * 60 * 60 * 1000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cancelTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, cancelTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cancelTime, pendingIntent)
            }
            Log.d("NotificationScheduler", "Scheduled agenda cancel alarm 23 hours from now at: " + Date(cancelTime))
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to schedule cancel alarm: " + e.message)
        }
    }

    suspend fun checkAndShowDailyAgenda(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())

            val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
            val dismissedDate = sharedPrefs.getString("dismissed_agenda_date", "") ?: ""
            if (dismissedDate == todayStr) {
                Log.d("NotificationScheduler", "Agenda notification was already dismissed today. Skipping.")
                return
            }

            val agendas = db.agendaDao().getAgendasByDateList(todayStr)
            if (agendas.isNotEmpty()) {
                val title = "Agenda Kegiatan Hari Ini (${agendas.size} Agenda)"
                val details = agendas.joinToString("\n") { "• ${it.judul}: ${it.detail.ifBlank { "Tidak ada detail" }}" }

                showOngoingAgendaNotification(context, title, details)
                // Schedule the alarm to cancel this notification after 23 hours (at 23:00)
                scheduleCancelAgendaAlarm(context)
            } else {
                cancelAgendaNotification(context)
            }
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Error in checkAndShowDailyAgenda: ${e.message}")
        }
    }

    @SuppressLint("NotificationPermission")
    private fun showOngoingAgendaNotification(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("CONFIRM_DELETE_NOTIFICATION", true)
        }
        val cancelPendingIntent = PendingIntent.getActivity(
            context,
            12345,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_AGENDA)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle(title)
            .setContentText(content.split("\n").firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true) // Persistent / Cannot be cleared manually
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Hapus",
                cancelPendingIntent
            )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_AGENDA, builder.build())
    }

    @SuppressLint("NotificationPermission")
    fun showPrayerNotification(context: Context, name: String, time: String) {
        createNotificationChannels(context)

        val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val defaultVal = if (name == "Imsak") 1 else 2
        val setting = sharedPrefs.getInt("azan_sound_setting_${name.lowercase(Locale.getDefault())}", defaultVal)
        val notificationId = NOTIFICATION_ID_PRAYER + name.hashCode()

        // Mode azan: notifikasi dan pemutaran suara ditangani AzanService di latar belakang.
        // Tidak ada full-screen intent, jadi aplikasi tidak terbuka sendiri.
        if (setting >= 2 && AzanService.start(context, name, time, notificationId)) {
            return
        }

        postPrayerReminder(context, name, time, notificationId, withSound = setting == 1)
    }

    @SuppressLint("NotificationPermission")
    fun postPrayerReminder(
        context: Context,
        name: String,
        time: String,
        notificationId: Int = NOTIFICATION_ID_PRAYER + name.hashCode(),
        withSound: Boolean = false
    ) {
        createNotificationChannels(context)

        val channelId = if (withSound) CHANNEL_PRAYER_NOTIFICATION else CHANNEL_PRAYER_SILENT
        val text = "Waktu sholat $name telah tiba ($time). Mari laksanakan ibadah."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("Waktunya Sholat $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (withSound) {
            // Suara Notifikasi Saja: berbunyi sekali, tanpa alarm dan tanpa membuka aplikasi.
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    fun cancelAgendaNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_AGENDA)
        Log.d("NotificationScheduler", "Ongoing agenda notification cancelled successfully.")
    }

    @SuppressLint("NotificationPermission")
    fun checkAndShowDailyHolidayAndObservance(context: Context, timeSlot: String = "00:00") {
        try {
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val dateKey = String.format("%04d-%02d-%02d", year, month, day)

            val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
            val slotKey = "last_holiday_notif_${timeSlot.replace(":", "_")}"
            val lastNotifiedDate = sharedPrefs.getString(slotKey, "") ?: ""

            // Cegah notifikasi ganda pada slot jam yang sama di hari yang sama
            if (lastNotifiedDate == dateKey) {
                Log.d("NotificationScheduler", "Holiday & Observance notification for slot $timeSlot already shown today ($dateKey). Skipping.")
                return
            }

            val holidays = HolidayClient.getFallbackHolidays(year)
            val holidayName = holidays[dateKey]
            val observanceName = HolidayClient.getObservanceForDate(month, day)

            // Jika tidak ada hari libur maupun hari peringatan nasional hari ini, simpan status dan skip
            if (holidayName == null && observanceName == null) {
                sharedPrefs.edit().putString(slotKey, dateKey).apply()
                return
            }

            createNotificationChannels(context)

            val title: String
            val text: String
            val bigText: String

            when {
                holidayName != null && observanceName != null -> {
                    title = "🎉 Hari Libur & Peringatan Nasional"
                    text = "$holidayName • $observanceName"
                    bigText = "Hari ini adalah Hari Libur Nasional: $holidayName\nSerta diperingati sebagai: $observanceName.\nSelamat memperingati dan beraktivitas!"
                }
                holidayName != null -> {
                    title = "🔴 Hari Libur Nasional: $holidayName"
                    text = "Hari ini adalah hari libur nasional ($holidayName)"
                    bigText = "Hari ini ($dateKey) adalah Hari Libur Nasional: $holidayName.\nSelamat berlibur dan beristirahat!"
                }
                else -> {
                    title = "⭐ Hari Peringatan Nasional"
                    text = "$observanceName"
                    bigText = "Hari ini diperingati sebagai Hari Besar / Peringatan Nasional: $observanceName."
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                6666,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val builder = NotificationCompat.Builder(context, CHANNEL_HOLIDAY)
                .setSmallIcon(R.drawable.ic_stat_singghara)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID_HOLIDAY, builder.build())

            // Simpan tanggal notifikasi berhasil ditampilkan untuk slot ini
            sharedPrefs.edit().putString(slotKey, dateKey).apply()
            Log.d("NotificationScheduler", "Holiday & Observance notification posted for slot $timeSlot: $title")
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to show holiday notification: ${e.message}")
        }
    }

    fun cancelHolidayNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_HOLIDAY)
    }
}
