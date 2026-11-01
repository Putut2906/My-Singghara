package com.example

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.util.SinggharaFolderManager
import java.io.File
import java.util.Locale

/**
 * Plays the azan as a foreground service so the recitation keeps running when the app is in the
 * background. The service owns the azan notification and never starts an activity: the app only
 * opens if the user taps the notification themselves.
 */
class AzanService : Service() {

    private var player: MediaPlayer? = null

    private var activePrayerName: String = ""
    private var activePrayerTime: String = ""
    private var activeNotificationId: Int = NotificationScheduler.NOTIFICATION_ID_PRAYER

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP) {
            finishPlayback()
            return START_NOT_STICKY
        }

        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Sholat"
        val prayerTime = intent.getStringExtra(EXTRA_PRAYER_TIME).orEmpty()
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            NotificationScheduler.NOTIFICATION_ID_PRAYER
        )
        activePrayerName = prayerName
        activePrayerTime = prayerTime
        activeNotificationId = notificationId

        try {
            startForegroundWithType(notificationId, buildNotification(prayerName, prayerTime, notificationId))
        } catch (t: Throwable) {
            Log.e(TAG, "Layanan azan tidak dapat dijalankan: ${t.message}")
            stopSelf()
            NotificationScheduler.postPrayerReminder(this, prayerName, prayerTime, notificationId, withSound = false)
            return START_NOT_STICKY
        }

        playAzan(prayerName)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun startForegroundWithType(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(id, notification)
        }
    }

    private fun buildNotification(prayerName: String, prayerTime: String, notificationId: Int): Notification {
        NotificationScheduler.createNotificationChannels(this)

        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            notificationId,
            Intent(this, AzanService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Waktu sholat $prayerName telah tiba ($prayerTime). Mari laksanakan ibadah."
        return NotificationCompat.Builder(this, NotificationScheduler.CHANNEL_PRAYER_AZAN)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle("Waktunya Sholat $prayerName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_lock_silent_mode_off, "Matikan Azan", stopIntent)
            .setDeleteIntent(stopIntent)
            .build()
    }

    private fun playAzan(prayerName: String) {
        releasePlayer()

        val prefs = getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val prayerKey = prayerName.lowercase(Locale.getDefault())
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val setting = prefs.getInt("azan_sound_setting_$prayerKey", if (prayerName == "Imsak") 1 else 2)
        if (setting >= 3) {
            resolveCustomFile(prefs, prayerKey)?.let { custom ->
                if (startFile(custom, attributes)) return
            }
        }

        val defaultFile = resolveDefaultFile(prayerName)
        if (defaultFile != null && startFile(defaultFile, attributes)) return

        if (startBundledAsset(prayerName, attributes)) return

        fallbackToReminder()
    }

    private fun resolveCustomFile(prefs: android.content.SharedPreferences, prayerKey: String): File? {
        val stored = File(filesDir, "azan_custom_$prayerKey.mp3")
        val external = File(SinggharaFolderManager.getMp3Folder(), "azan_$prayerKey.mp3")
        if (external.exists() && external.name.lowercase(Locale.getDefault()) in SinggharaFolderManager.DISALLOWED_MP3_FILES) {
            try {
                external.delete()
            } catch (_: Exception) {
            }
        }
        val chosen = prefs.getString("azan_custom_path_$prayerKey", null)?.let { File(it) }
        val usable = listOfNotNull(chosen, stored, external).firstOrNull { file ->
            file.exists() && file.name.lowercase(Locale.getDefault()) !in SinggharaFolderManager.DISALLOWED_MP3_FILES
        }
        return usable
    }

    private fun resolveDefaultFile(prayerName: String): File? {
        var file = SinggharaFolderManager.getDefaultAzanFile(this, prayerName)
        if (file == null || !file.exists()) {
            SinggharaFolderManager.seedDefaultMp3sFromAssets(this)
            file = SinggharaFolderManager.getDefaultAzanFile(this, prayerName)
        }
        return file?.takeIf { it.exists() }
    }

    private fun startFile(file: File, attributes: AudioAttributes): Boolean {
        val mp = createPlayer(attributes)
        player = mp
        return try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
            Log.d(TAG, "Azan diputar dari ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar azan dari ${file.name}: ${e.message}")
            releasePlayer()
            false
        }
    }

    private fun startBundledAsset(prayerName: String, attributes: AudioAttributes): Boolean {
        val mp = createPlayer(attributes)
        player = mp
        return try {
            val assetName = if (prayerName.contains("subuh", true) || prayerName.contains("fajr", true)) {
                "mp3/fajr_128_44.mp3"
            } else {
                "mp3/adzan_turkey-1.mp3"
            }
            val asset = assets.openFd(assetName)
            mp.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            asset.close()
            mp.prepare()
            mp.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar azan bawaan aplikasi: ${e.message}")
            releasePlayer()
            false
        }
    }

    /** Audio is unavailable: keep the prayer reminder visible instead of staying silent. */
    private fun fallbackToReminder() {
        val name = activePrayerName
        val time = activePrayerTime
        val id = activeNotificationId
        finishPlayback()
        if (name.isNotEmpty()) {
            NotificationScheduler.postPrayerReminder(this, name, time, id, withSound = false)
        }
    }

    private fun createPlayer(attributes: AudioAttributes): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(attributes)
            try {
                setWakeMode(this@AzanService, PowerManager.PARTIAL_WAKE_LOCK)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengunci CPU saat azan: ${e.message}")
            }
            isLooping = false
            setOnCompletionListener {
                Log.d(TAG, "Azan selesai diputar.")
                finishPlayback()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Pemutaran azan gagal: what=$what, extra=$extra")
                finishPlayback()
                true
            }
        }

    private fun finishPlayback() {
        releasePlayer()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {
        }
        stopSelf()
    }

    private fun releasePlayer() {
        try {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghentikan azan: ${e.message}")
        } finally {
            player = null
        }
    }

    companion object {
        private const val TAG = "AzanService"
        const val ACTION_START = "com.example.ACTION_START_AZAN"
        const val ACTION_STOP = "com.example.ACTION_STOP_AZAN_SERVICE"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        /** Starts background azan playback. Returns false when the service cannot be launched. */
        fun start(context: Context, prayerName: String, prayerTime: String, notificationId: Int): Boolean {
            val intent = Intent(context, AzanService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PRAYER_NAME, prayerName)
                putExtra(EXTRA_PRAYER_TIME, prayerTime)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal memulai layanan azan: ${t.message}")
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AzanService::class.java))
            } catch (t: Throwable) {
                Log.e(TAG, "Gagal menghentikan layanan azan: ${t.message}")
            }
        }
    }
}
