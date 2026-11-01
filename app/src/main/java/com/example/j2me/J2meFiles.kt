package com.example.j2me

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** File picker and storage helpers for J2ME .jar files. */
object J2meFiles {

    /** MIME types passed to [androidx.activity.result.contract.ActivityResultContracts.OpenDocument]. */
    val JAR_MIME_TYPES = arrayOf(
        "application/java-archive",
        "application/x-java-archive",
        "application/zip",
        "application/octet-stream",
        "*/*"
    )

    fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    fun hasJarExtension(name: String?): Boolean =
        name?.trim()?.endsWith(".jar", ignoreCase = true) == true

    /**
     * Copies a picked .jar into filesDir/j2me/imports so the emulator never touches
     * public/external storage. The caller owns the returned file and should delete it
     * after the game has been installed.
     */
    suspend fun copyJarToInternal(
        context: Context,
        uri: Uri,
        displayName: String? = null
    ): File = withContext(Dispatchers.IO) {
        val name = sanitizeFileName(displayName ?: queryDisplayName(context, uri) ?: "game.jar")
        val imports = File(J2meStore.rootDir(context), "imports").apply { mkdirs() }
        val target = File(imports, "${System.currentTimeMillis()}_$name")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw J2meInstaller.InstallException("Berkas .jar tidak bisa dibuka.")

            if (target.length() == 0L) {
                throw J2meInstaller.InstallException("Berkas .jar kosong.")
            }
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
            .take(50)
            .ifBlank { "game" }
        return "$cleaned.jar"
    }
}
