package com.example.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class GithubUpdateTask {
    data object Idle : GithubUpdateTask()
    data object Checking : GithubUpdateTask()
    data class UpToDate(val releaseLabel: String, val detail: String? = null) : GithubUpdateTask()
    data class Downloading(val releaseLabel: String, val progress: Float) : GithubUpdateTask()
    data class Completed(val releaseLabel: String, val fileName: String, val apkPath: String) : GithubUpdateTask()
    data class Failed(val message: String) : GithubUpdateTask()
}

/**
 * Runs the GitHub Releases check and the APK download in a foreground service so the
 * work keeps going when the app is backgrounded or the device is locked. The release
 * APK is only downloaded when it is newer than the installed version.
 */
class GithubApkUpdateService : Service() {

    // Tanpa handler, satu exception yang lolos dari coroutine langsung mematikan
    // proses aplikasi (termasuk saat layar mati di tengah unduhan).
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + com.example.util.AppCrashGuard.handler
    )
    private var workJob: Job? = null
    private var lastNotifiedPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannel()
        } catch (t: Throwable) {
            android.util.Log.e("GithubApkUpdateService", "Channel notifikasi gagal dibuat: ${t.message}", t)
        }
        if (workJob?.isActive == true) {
            // A second tap must still satisfy the foreground-service contract.
            try {
                startForegroundCompat(buildNotification("Memproses pembaruan...", indeterminate = true))
            } catch (t: Throwable) {
                android.util.Log.e("GithubApkUpdateService", "startForeground ditolak: ${t.message}", t)
            }
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat(buildNotification("Memeriksa pembaruan...", indeterminate = true))
        } catch (t: Throwable) {
            // Android 12+ menolak startForeground dari latar belakang; layanan harus
            // berhenti sendiri, bukan menjatuhkan aplikasi.
            android.util.Log.e("GithubApkUpdateService", "startForeground ditolak: ${t.message}", t)
            stopSelf()
            return START_NOT_STICKY
        }
        _state.value = GithubUpdateTask.Checking

        workJob = scope.launch { checkAndSave() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        workJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun checkAndSave() {
        val result = try {
            GitHubUpdateManager.checkForUpdate(this)
        } catch (e: Exception) {
            GithubUpdateTask.Failed(e.localizedMessage ?: "Gagal menghubungi GitHub.")
        }

        when (result) {
            is UpdateCheckState.UpdateAvailable -> downloadAndSave(result.apkAsset, result.release.tagName.ifBlank { result.release.name })
            is UpdateCheckState.UpToDate -> {
                val release = result.latestRelease
                val tag = release?.tagName ?: ""
                // Nothing to download: the installed version is already the newest release.
                finish(
                    state = GithubUpdateTask.UpToDate(tag, result.customMessage),
                    title = "Aplikasi sudah versi terbaru",
                    text = "Tidak ada unduhan baru. Versi terpasang v${BuildConfig.VERSION_NAME}."
                )
            }
            is UpdateCheckState.Error -> finish(
                state = GithubUpdateTask.Failed(result.errorMessage),
                title = "Gagal memeriksa pembaruan",
                text = result.errorMessage
            )
            else -> finish(
                state = GithubUpdateTask.Failed("Tidak dapat memeriksa pembaruan GitHub Releases."),
                title = "Gagal memeriksa pembaruan",
                text = "Tidak dapat memeriksa pembaruan GitHub Releases."
            )
        }
    }

    private suspend fun downloadAndSave(asset: GitHubAsset, releaseLabel: String) {
        try {
            _state.value = GithubUpdateTask.Downloading(releaseLabel, 0f)
            val config = GitHubUpdateManager.loadConfig(this)
            val downloaded = GitHubUpdateManager.downloadApk(
                context = this,
                downloadUrl = asset.browserDownloadUrl,
                fileName = asset.name,
                token = config.token
            ) { progress, _, _ ->
                _state.value = GithubUpdateTask.Downloading(releaseLabel, progress)
                updateProgressNotification(releaseLabel, progress)
            }

            val fileName = ApkDownloadsSaver.releaseFileName(this, downloaded, asset.name)
            ApkDownloadsSaver.saveToDownloads(this, downloaded, fileName)
            PendingUpdateStore.save(this, releaseLabel, fileName, downloaded.absolutePath)
            finish(
                state = GithubUpdateTask.Completed(releaseLabel, fileName, downloaded.absolutePath),
                title = "APK $releaseLabel tersimpan",
                text = "Tersimpan di folder Download: $fileName. Ketuk untuk memasang.",
                installApkPath = downloaded.absolutePath
            )
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Coba lagi."
            finish(
                state = GithubUpdateTask.Failed(message),
                title = "Gagal menyimpan APK",
                text = message
            )
        }
    }

    private fun finish(
        state: GithubUpdateTask,
        title: String,
        text: String,
        installApkPath: String? = null
    ) {
        _state.value = state
        // A separate id keeps the result visible after the foreground notification is removed.
        try {
            notifyManager()?.notify(
                COMPLETION_NOTIFICATION_ID,
                buildNotification(
                    text = text,
                    title = title,
                    indeterminate = false,
                    ongoing = false,
                    autoCancel = true,
                    installApkPath = installApkPath
                )
            )
        } catch (t: Throwable) {
            android.util.Log.e("GithubApkUpdateService", "Notifikasi hasil gagal: ${t.message}", t)
        }
        try {
            stopForegroundCompat()
        } catch (t: Throwable) {
            android.util.Log.e("GithubApkUpdateService", "stopForeground gagal: ${t.message}", t)
        }
        stopSelf()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun updateProgressNotification(releaseLabel: String, progress: Float) {
        val percent = (progress * 100).toInt()
        if (percent == lastNotifiedPercent) return
        lastNotifiedPercent = percent
        notifyManager()?.notify(
            NOTIFICATION_ID,
            buildNotification(
                title = "Mengunduh APK $releaseLabel",
                text = "Menyimpan ke folder Download ($percent%)",
                indeterminate = false,
                progressPercent = percent
            )
        )
    }

    private fun buildNotification(
        text: String,
        title: String? = null,
        indeterminate: Boolean,
        progressPercent: Int = 0,
        ongoing: Boolean = true,
        autoCancel: Boolean = false,
        installApkPath: String? = null
    ): android.app.Notification {
        val contentIntent = buildContentIntent(installApkPath)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_singghara)
            .setContentTitle(title ?: "Pembaruan My Singghara")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else if (progressPercent > 0) {
            builder.setProgress(100, progressPercent, false)
        }
        return builder.build()
    }

    /**
     * A finished download opens the package installer on tap; every other step opens the app.
     */
    private fun buildContentIntent(installApkPath: String?): PendingIntent? {
        if (installApkPath != null) {
            return PendingIntent.getBroadcast(
                this,
                INSTALL_REQUEST_CODE,
                ApkInstallReceiver.intentFor(this, installApkPath),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Unduhan Pembaruan",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Memeriksa GitHub Releases dan menyimpan APK pembaruan" }
        notifyManager()?.createNotificationChannel(channel)
    }

    private fun notifyManager(): NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    companion object {
        private const val CHANNEL_ID = "github_update_download"
        private const val NOTIFICATION_ID = 4801
        private const val COMPLETION_NOTIFICATION_ID = 4802
        private const val INSTALL_REQUEST_CODE = 4803
        private const val ACTION_CHECK_AND_SAVE = "com.example.update.action.CHECK_AND_SAVE"

        private val _state = MutableStateFlow<GithubUpdateTask>(GithubUpdateTask.Idle)
        val state: StateFlow<GithubUpdateTask> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, GithubApkUpdateService::class.java).setAction(ACTION_CHECK_AND_SAVE)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // Bisa dilempar saat aplikasi baru saja pindah ke background.
                android.util.Log.e("GithubApkUpdateService", "Layanan pembaruan tidak dapat dimulai: ${t.message}", t)
            }
        }
    }
}
