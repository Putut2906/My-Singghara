package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Sumber tunggal izin penyimpanan "Izinkan Akses Media" yang diminta pada
 * setup pertama kali instal. Tabungan, cadangan/pemulihan, dan pemutar musik
 * memakai daftar izin yang sama agar tidak ada lagi picker SAF.
 */
object MediaPermission {

    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        // WRITE_EXTERNAL_STORAGE tidak lagi bisa diberikan mulai Android 11,
        // jadi hanya diminta pada perangkat lama.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    fun missingPermissions(context: Context): List<String> = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

    /** Izin media sudah diberikan, atau akses semua file mencakupnya. */
    fun hasAccess(context: Context): Boolean =
        missingPermissions(context).isEmpty() || hasAllFilesAccess()

    fun openAllFilesSettings(context: Context) {
        val appSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                appSettings
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                try {
                    context.startActivity(appSettings)
                } catch (_: Exception) {
                }
            }
        }
    }
}
