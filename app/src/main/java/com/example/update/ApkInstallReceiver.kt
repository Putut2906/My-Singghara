package com.example.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Opens the system package installer when the update notification is tapped, so a finished
 * download can be installed without re-opening the app.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: return
        val apkFile = File(apkPath)
        if (!apkFile.exists()) return
        if (GitHubUpdateManager.installApk(context, apkFile)) {
            PendingUpdateStore.clear(context)
        }
    }

    companion object {
        private const val EXTRA_APK_PATH = "com.example.update.extra.APK_PATH"

        fun intentFor(context: Context, apkPath: String): Intent =
            Intent(context, ApkInstallReceiver::class.java).putExtra(EXTRA_APK_PATH, apkPath)
    }
}
