package com.example.update

import android.content.Context
import com.example.BuildConfig
import java.io.File

/**
 * Remembers an APK that was downloaded but not installed yet. The update prompt can then be
 * restored after the user leaves the app or the process is recreated, instead of silently
 * disappearing once the download finishes in the background.
 */
object PendingUpdateStore {
    private const val PREFS = "pending_apk_update"
    private const val KEY_VERSION = "version"
    private const val KEY_FILE_NAME = "file_name"
    private const val KEY_PATH = "path"

    data class PendingUpdate(
        val versionTag: String,
        val fileName: String,
        val apkPath: String
    ) {
        val displayVersion: String
            get() = versionTag.removePrefix("v").removePrefix("V").trim()
    }

    fun save(context: Context, versionTag: String, fileName: String, apkPath: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VERSION, versionTag)
            .putString(KEY_FILE_NAME, fileName)
            .putString(KEY_PATH, apkPath)
            .apply()
    }

    fun load(context: Context): PendingUpdate? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versionTag = prefs.getString(KEY_VERSION, null) ?: return null
        val apkPath = prefs.getString(KEY_PATH, null) ?: return null
        if (apkPath.isBlank() || !File(apkPath).exists()) {
            clear(context)
            return null
        }
        // The APK is stale once the installed build already matches or exceeds it.
        if (!GitHubUpdateManager.isRemoteVersionNewer(BuildConfig.VERSION_NAME, versionTag)) {
            clear(context)
            return null
        }
        return PendingUpdate(
            versionTag = versionTag,
            fileName = prefs.getString(KEY_FILE_NAME, "") ?: "",
            apkPath = apkPath
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
