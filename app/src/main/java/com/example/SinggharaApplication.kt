package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SinggharaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        com.example.firebase.AdminUidStore.init(this)
        installCrashLogger()
        initFirebase()
        // WorkManager.getInstance() melempar IllegalStateException bila initializer-nya
        // absen/gagal; tanpa penjagaan ini setiap peluncuran aplikasi mati sebelum UI muncul.
        try {
            com.example.update.UpdateCheckScheduler.ensurePeriodic(this)
        } catch (e: Exception) {
            Log.e("SinggharaApplication", "Penjadwal cek versi dilewati: ${e.message}")
        }
    }

    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
                val isDeX = uiMode == android.content.res.Configuration.UI_MODE_TYPE_DESK
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (_: Exception) {
                    "?"
                }
                val logFile = java.io.File(
                    com.example.util.SinggharaFolderManager.getRootFolder(this),
                    "crash_log.txt"
                )
                if (logFile.length() > 256 * 1024) logFile.delete()
                logFile.appendText(
                    buildString {
                        appendLine("===== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())} =====")
                        appendLine("versi: $versionName | DeX: $isDeX | thread: ${thread.name}")
                        appendLine(Log.getStackTraceString(throwable))
                        appendLine()
                    }
                )
            } catch (_: Throwable) {
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    Log.w("SinggharaApplication", "Default FirebaseApp init skipped, trying explicit options: ${e.message}")
                }
            }

            // Fallback dengan opsi eksplisit jika getApps masih kosong
            if (FirebaseApp.getApps(this).isEmpty()) {
                val config = com.example.firebase.FirebaseClientConfig.load(this)
                val options = FirebaseOptions.Builder()
                    .setApplicationId(config.appId)
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setStorageBucket(config.storageBucket)
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("SinggharaApplication", "FirebaseApp successfully initialized with explicit options")
            }
        } catch (e: Exception) {
            Log.e("SinggharaApplication", "Failed to initialize FirebaseApp", e)
        }
    }
}
