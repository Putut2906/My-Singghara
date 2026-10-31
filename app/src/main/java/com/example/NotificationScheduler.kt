package com.example

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
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
    const val CHANNEL_AGENDA = "agenda_channel"
    const val CHANNEL_WEB_SECURITY = "web_security_channel"
    const val CHANNEL_HOLIDAY = "holiday_channel"
    const val CHANNEL_FCM = "fcm_cloud_messaging_channel"

    const val NOTIFICATION_ID_AGENDA = 8888
    const val NOTIFICATION_ID_PRAYER = 9999
    const val NOTIFICATION_ID_WEB_SECURITY = 7777
    const val NOTIFICATION_ID_HOLIDAY = 6666
    const val NOTIFICATION_ID_FCM = 5555

    const val ACTION_DAILY_AGENDA_CHECK = "com.example.ACTION_DAILY_AGENDA_CHECK"
    const val ACTION_MORNING_HOLIDAY_CHECK = "com.example.ACTION_MORNING_HOLIDAY_CHECK"
    const val ACTION_PRAYER_NOTIFICATION = "com.example.ACTION_PRAYER_NOTIFICATION"
    const val ACTION_CANCEL_AGENDA_NOTIFICATION = "com.example.ACTION_CANCEL_AGENDA_NOTIFICATION"
    const val ACTION_STOP_AZAN = "com.example.ACTION_STOP_AZAN"
    const val ACTION_REJECT_WEB_ACCESS = "com.example.ACTION_REJECT_WEB_ACCESS"

    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_PRAYER_TIME = "extra_prayer_time"

    private var azanMediaPlayer: android.media.MediaPlayer? = null
    private var activeAzanNotificationId: Int? = null
    private var appContext: Context? = null

    fun playAzanSound(context: Context, prayerName: String, notificationId: Int? = null) {
        try {
            stopAzanSound()
            appContext = context.applicationContext
            activeAzanNotificationId = notificationId ?: (NOTIFICATION_ID_PRAYER + prayerName.hashCode())

            val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
            val defaultVal = if (prayerName == "Imsak") 1 else 2
            val setting = sharedPrefs.getInt("azan_sound_setting_${prayerName.lowercase(Locale.getDefault())}", defaultVal)

            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            if (setting == 3) {
                val prayerKey = prayerName.lowercase(Locale.getDefault())
                val customFile = java.io.File(context.filesDir, "azan_custom_$prayerKey.mp3")
                val externalMp3 = java.io.File(com.example.util.SinggharaFolderManager.getMp3Folder(), "azan_$prayerKey.mp3")
                val customChosenPath = sharedPrefs.getString("azan_custom_path_$prayerKey", null)
                val targetFile = when {
                    customChosenPath != null && java.io.File(customChosenPath).exists() && java.io.File(customChosenPath).name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> java.io.File(customChosenPath)
                    customFile.exists() -> customFile
                    externalMp3.exists() && externalMp3.name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> externalMp3
                    else -> null
                }
                if (externalMp3.exists() && externalMp3.name.lowercase() in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES) {
                    try { externalMp3.delete() } catch (ignored: Exception) {}
                }

                if (targetFile != null && targetFile.exists()) {
                    azanMediaPlayer = android.media.MediaPlayer().apply {
                        setAudioAttributes(audioAttributes)
                        try {
                            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                        } catch (e: Exception) {
                            Log.e("NotificationScheduler", "Failed to set wake mode: ${e.message}")
                        }
                        setDataSource(targetFile.absolutePath)
                        isLooping = false
                        setOnCompletionListener {
                            Log.d("NotificationScheduler", "Custom Azan playback finished for $prayerName. Removing notification.")
                            stopAzanSound()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("NotificationScheduler", "Custom Azan error: what=$what, extra=$extra")
                            stopAzanSound()
                            true
                        }
                        prepare()
                        start()
                    }
                    Log.d("NotificationScheduler", "Custom Azan playback for $prayerName started successfully from ${targetFile.name}.")
                } else {
                    playDefaultAzan(context, prayerName, audioAttributes)
                }
            } else {
                playDefaultAzan(context, prayerName, audioAttributes)
            }
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to play Azan for $prayerName: ${e.message}")
            stopAzanSound()
        }
    }

    private fun playDefaultAzan(context: Context, prayerName: String, audioAttributes: android.media.AudioAttributes) {
        try {
            var defaultFile = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayerName)
            if (defaultFile == null || !defaultFile.exists()) {
                com.example.util.SinggharaFolderManager.seedDefaultMp3sFromAssets(context)
                defaultFile = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayerName)
            }

            if (defaultFile != null && defaultFile.exists()) {
                azanMediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    try {
                        setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    } catch (e: Exception) {
                        Log.e("NotificationScheduler", "Failed to set wake mode: ${e.message}")
                    }
                    setDataSource(defaultFile.absolutePath)
                    isLooping = false
                    setOnCompletionListener {
                        Log.d("NotificationScheduler", "Default Azan playback finished for $prayerName. Removing notification.")
                        stopAzanSound()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("NotificationScheduler", "Default Azan error: what=$what, extra=$extra")
                        stopAzanSound()
                        true
                    }
                    prepare()
                    start()
                }
                Log.d("NotificationScheduler", "Default Azan playback for $prayerName started successfully from ${defaultFile.name} on ALARM channel.")
            } else {
                Log.w("NotificationScheduler", "Default Azan file not found in storage, playing directly from bundled assets.")
                try {
                    val assetName = if (prayerName.contains("subuh", true) || prayerName.contains("fajr", true)) {
                        "mp3/fajr_128_44.mp3"
                    } else {
                        "mp3/adzan_turkey-1.mp3"
                    }
                    val afd = context.assets.openFd(assetName)
                    azanMediaPlayer = android.media.MediaPlayer().apply {
                        setAudioAttributes(audioAttributes)
                        try {
                            setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                        } catch (e: Exception) {
                            Log.e("NotificationScheduler", "Failed to set wake mode: ${e.message}")
                        }
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                        isLooping = false
                        setOnCompletionListener {
                            stopAzanSound()
                        }
                        setOnErrorListener { _, _, _ ->
                            stopAzanSound()
                            true
                        }
                        prepare()
                        start()
                    }
                } catch (assetErr: Exception) {
                    Log.e("NotificationScheduler", "Bundled asset fallback failed: ${assetErr.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to play default Azan: ${e.message}")
            stopAzanSound()
        }
    }

    fun stopAzanSound() {
        try {
            azanMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            Log.d("NotificationScheduler", "Azan playback stopped successfully.")
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to stop Azan: ${e.message}")
        } finally {
            azanMediaPlayer = null
            // Otomatis hapus notifikasi di status bar yang memiliki aksi "Matikan Azan" saat azan berhenti
            activeAzanNotificationId?.let { notifId ->
                try {
                    appContext?.let { ctx ->
                        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notifId)
                        Log.d("NotificationScheduler", "Prayer notification $notifId automatically cancelled because Azan stopped.")
                    }
                } catch (e: Exception) {
                    Log.e("NotificationScheduler", "Failed to auto-cancel prayer notification: ${e.message}")
                }
            }
            activeAzanNotificationId = null
        }
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1a. Prayer Channel - Silent
            val silentChannel = NotificationChannel(
                "prayer_times_channel_silent",
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
                "prayer_times_channel_notification",
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
                "prayer_times_channel_azan",
                "Jadwal Sholat (Azan Madinah)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat waktu sholat dengan Azan Madinah"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Handled dynamically via MediaPlayer on USAGE_ALARM
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

            // 3. Web PC Access Security Channel (High Priority Heads-Up Notification)
            val webSecurityChannel = NotificationChannel(
                CHANNEL_WEB_SECURITY,
                "Keamanan Akses Web PC",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi verifikasi keamanan saat ada perangkat PC mengakses Web Singghara"
                enableVibration(true)
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
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
        }
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

        // Action Intent "Tolak" langsung dari notifikasi
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

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isScreenLocked = keyguardManager?.isKeyguardLocked == true || keyguardManager?.isDeviceLocked == true
        val isForeground = MainActivity.isAppInForeground

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_WEB_SECURITY)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("🚨 Permintaan Akses Web PC Terdeteksi")
            .setContentText("Perangkat ($clientIp) meminta akses ke Web PC. Apakah anda akses web PC?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Perangkat lokal dengan IP $clientIp mencoba membuka My Singghara PC pada pukul $timeStr.\n\n" +
                "Ketuk untuk memilih izin ('Ya' untuk PIN & QR Code, atau 'Tidak' untuk tolak akses)."
            ))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tolak Akses", pendingRejectIntent)
            .addAction(android.R.drawable.ic_menu_send, "Verifikasi / Buka", pendingOpenIntent)

        // Hanya sertakan fullScreenIntent jika layar HP terkunci atau aplikasi sedang tidak di foreground
        if (isScreenLocked || !isForeground) {
            notificationBuilder.setFullScreenIntent(pendingOpenIntent, true)
        }

        notificationManager.notify(NOTIFICATION_ID_WEB_SECURITY, notificationBuilder.build())

        // WebAuthPromptActivity HANYA diluncurkan jika:
        // 1. Layar HP terkunci (isScreenLocked), ATAU
        // 2. Pengguna sedang membuka aplikasi lain (seperti YouTube, TikTok, dll - !isForeground).
        // Jika aplikasi My Singghara sedang aktif di latar depan (isForeground && !isScreenLocked),
        // JANGAN buka WebAuthPromptActivity agar tidak muncul pop-up ganda, cukup dialog internal aplikasi (WebSecurityDialog).
        if (isScreenLocked || !isForeground) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val isScreenOn = powerManager?.isInteractive ?: true
                if (isScreenOn) {
                    context.startActivity(openIntent)
                }
            } catch (e: Exception) {
                Log.e("NotificationScheduler", "Failed to launch WebAuthPromptActivity: ${e.message}")
            }
        }
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

                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode + 5000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    // METHOD 1: setAlarmClock
                    // AlarmClockInfo has the highest execution priority in Android.
                    // It wakes up the CPU from deep Doze mode, ignores OEM battery standby, fires at the exact second,
                    // and does not require SCHEDULE_EXACT_ALARM special user-level app-op approval.
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(prayerCal.timeInMillis, showPendingIntent)
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

        val channelId = when (setting) {
            0 -> "prayer_times_channel_silent"
            1 -> "prayer_times_channel_notification"
            else -> "prayer_times_channel_azan"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = NOTIFICATION_ID_PRAYER + name.hashCode()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("Waktunya Sholat $name")
            .setContentText("Waktu sholat $name telah tiba ($time). Mari laksanakan ibadah.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Waktu sholat $name telah tiba ($time). Mari laksanakan ibadah."))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (setting == 1) {
            // Suara Notifikasi Saja: Berbunyi nada notifikasi standar sekali, kategori pengingat, bebas dari alarm OS
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
        } else if (setting >= 2) {
            // Suara Azan: Menggunakan USAGE_ALARM dengan opsi Matikan Azan
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            builder.setFullScreenIntent(pendingIntent, false)

            // Play Azan dynamically
            playAzanSound(context, name, notificationId)

            // Intent to Stop Azan
            val stopIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_STOP_AZAN
                putExtra("notification_id", notificationId)
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                android.R.drawable.ic_lock_silent_mode_off,
                "Matikan Azan",
                stopPendingIntent
            )

            // When swiped away, also stop Azan
            builder.setDeleteIntent(stopPendingIntent)
        } else {
            // Senyap (Setting == 0)
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
