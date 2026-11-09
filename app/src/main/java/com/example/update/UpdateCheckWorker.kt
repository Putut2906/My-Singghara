package com.example.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.NotificationScheduler
import kotlinx.coroutines.delay

/**
 * Worker periodik yang mengecek rilis baru di GitHub (dan Google Drive bila aktif)
 * lalu menampilkan notifikasi tanpa harus membuka aplikasi.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        if (GoogleDriveUpdateManager.isAutoCheckOnStartup(ctx)) {
            try {
                val drive = GoogleDriveUpdateManager.checkForDriveUpdate(ctx)
                if (drive is DriveUpdateCheckState.UpdateAvailable) {
                    NotificationScheduler.showUpdateAvailableNotification(
                        context = ctx,
                        versionLabel = drive.latestApk.name,
                        source = "Google Drive"
                    )
                    return Result.success()
                }
            } catch (_: Exception) {
                // Drive error tidak menghalangi pengecekan GitHub.
            }
        }

        // Worker berjalan periodik saat aplikasi diam; kegagalan jaringan atau
        // notifikasi cukup membuat percobaan ini dilewati, bukan menghentikan proses.
        try {
            if (!GitHubUpdateManager.loadConfig(ctx).autoCheckOnStartup) return Result.success()

            var result: UpdateCheckState = UpdateCheckState.Error("")
            for (attempt in 0 until 3) {
                if (isStopped) return Result.failure()
                result = GitHubUpdateManager.checkForUpdate(ctx)
                if (result !is UpdateCheckState.Error) break
                delay(3_000L * (attempt + 1))
            }

            if (result is UpdateCheckState.UpdateAvailable &&
                GitHubUpdateManager.isRemoteVersionNewer(BuildConfig.VERSION_NAME, result.release.tagName)
            ) {
                NotificationScheduler.showUpdateAvailableNotification(
                    context = ctx,
                    versionLabel = "v${result.newVersion}",
                    source = "GitHub"
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("UpdateCheckWorker", "Cek pembaruan latar belakang gagal: ${t.message}", t)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "update-check-periodic"
    }
}
