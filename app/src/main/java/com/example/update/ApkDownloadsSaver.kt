package com.example.update

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copies a downloaded APK into the public Download folder
 * (/storage/emulated/0/Download) so the user keeps the installer file.
 */
object ApkDownloadsSaver {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    // Android 10+ writes through MediaStore; older versions need WRITE_EXTERNAL_STORAGE.
    fun requiresLegacyWritePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    suspend fun saveToDownloads(context: Context, sourceFile: File, fileName: String): Uri =
        withContext(Dispatchers.IO) {
            val safeName = if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, sourceFile, safeName)
            } else {
                saveViaPublicDirectory(context, sourceFile, safeName)
            }
        }

    private fun saveViaMediaStore(context: Context, sourceFile: File, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Folder Download tidak dapat diakses")

        try {
            val output = resolver.openOutputStream(itemUri)
                ?: throw IOException("Gagal membuka file tujuan di folder Download")
            output.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, ready, null, null)
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            throw e
        }
        return itemUri
    }

    @Suppress("DEPRECATION")
    private fun saveViaPublicDirectory(context: Context, sourceFile: File, fileName: String): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw IOException("Folder Download tidak dapat dibuat")
        }
        val target = uniqueFile(downloadsDir, fileName)
        sourceFile.inputStream().use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(APK_MIME_TYPE), null)
        return Uri.fromFile(target)
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast(".apk", fileName)
        var candidate = File(dir, fileName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($index).apk")
            index++
        }
        return candidate
    }
}
