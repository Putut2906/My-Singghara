package com.example.ui.video

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class VideoItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val bucketName: String,
    val bucketId: Long,
    val isPrivate: Boolean = false
) {
    val durationLabel: String get() = formatDuration(durationMs)
    val sizeLabel: String get() = formatSize(sizeBytes)
}

data class VideoFolder(
    val bucketId: Long,
    val name: String,
    val videos: List<VideoItem>
) {
    val count: Int get() = videos.size
    val totalSize: Long get() = videos.sumOf { it.sizeBytes }
}

enum class VideoSort { DATE_DESC, NAME_ASC, SIZE_DESC, DURATION_DESC }

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

object VideoLibraryScanner {

    suspend fun scan(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            "bucket_display_name",
            "bucket_id"
        )
        val result = mutableListOf<VideoItem>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val bucketNameCol = cursor.getColumnIndex("bucket_display_name")
                val bucketIdCol = cursor.getColumnIndex("bucket_id")
                while (cursor.moveToNext()) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val item = VideoItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name?.takeIf { it.isNotBlank() } ?: "Video",
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L,
                        bucketName = if (bucketNameCol >= 0) {
                            cursor.getString(bucketNameCol)?.takeIf { it.isNotBlank() } ?: "Lainnya"
                        } else "Lainnya",
                        bucketId = if (bucketIdCol >= 0) cursor.getLong(bucketIdCol) else 0L
                    )
                    result += item
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    fun groupIntoFolders(videos: List<VideoItem>): List<VideoFolder> =
        videos.groupBy { it.bucketId to it.bucketName }
            .map { (key, items) -> VideoFolder(key.first, key.second, items) }
            .sortedByDescending { folder -> folder.videos.maxOfOrNull { it.dateAddedSec } ?: 0L }

    fun sort(videos: List<VideoItem>, sort: VideoSort): List<VideoItem> = when (sort) {
        VideoSort.DATE_DESC -> videos.sortedByDescending { it.dateAddedSec }
        VideoSort.NAME_ASC -> videos.sortedBy { it.name.lowercase(Locale.getDefault()) }
        VideoSort.SIZE_DESC -> videos.sortedByDescending { it.sizeBytes }
        VideoSort.DURATION_DESC -> videos.sortedByDescending { it.durationMs }
    }

    suspend fun loadThumbnail(context: Context, item: VideoItem): Bitmap? = withContext(Dispatchers.IO) {
        if (item.isPrivate) return@withContext null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    ContentUris.parseId(item.uri),
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

object PrivateVideoStore {
    private const val PREFS_NAME = "video_private_prefs"
    private const val KEY_PIN_HASH = "pin_hash"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun directory(context: Context): File =
        File(context.filesDir, "video_private").apply { if (!exists()) mkdirs() }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it) }

    fun hasPin(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null)?.isNotBlank() == true

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == hash(pin)

    fun list(context: Context): List<VideoItem> =
        (directory(context).listFiles() ?: emptyArray())
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                VideoItem(
                    uri = Uri.fromFile(file),
                    name = file.name,
                    durationMs = 0L,
                    sizeBytes = file.length(),
                    dateAddedSec = file.lastModified() / 1000L,
                    bucketName = "Privat",
                    bucketId = Long.MIN_VALUE,
                    isPrivate = true
                )
            }

    suspend fun import(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rawName = context.contentResolver.query(source, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
            val safeName = (rawName ?: "video_${System.currentTimeMillis()}.mp4")
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            var target = File(directory(context), safeName)
            if (target.exists()) {
                target = File(directory(context), "${System.currentTimeMillis()}_$safeName")
            }
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(fileUri: Uri): Boolean = try {
        val file = File(fileUri.path ?: return false)
        if (file.exists() && file.parentFile?.name == "video_private") file.delete() else false
    } catch (_: Exception) {
        false
    }
}

object VideoPrefs {
    private const val PREFS_NAME = "video_player_prefs"
    private const val KEY_DECODER = "decoder_mode"
    private const val KEY_SUBTITLE_SIZE = "subtitle_size"
    private const val KEY_BACKGROUND_PLAY = "background_play"

    enum class DecoderMode(val label: String, val description: String) {
        HW_PLUS("HW+", "Perangkat keras dengan cadangan perangkat lunak (paling kompatibel)"),
        HW("HW", "Perangkat keras saja (hemat baterai)"),
        SW("SW", "Perangkat lunak saja (untuk video yang tidak didukung GPU)")
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadDecoder(context: Context): DecoderMode {
        val raw = prefs(context).getString(KEY_DECODER, DecoderMode.HW_PLUS.name)
        return DecoderMode.entries.firstOrNull { it.name == raw } ?: DecoderMode.HW_PLUS
    }

    fun saveDecoder(context: Context, mode: DecoderMode) {
        prefs(context).edit().putString(KEY_DECODER, mode.name).apply()
    }

    fun loadSubtitleSize(context: Context): Float =
        prefs(context).getFloat(KEY_SUBTITLE_SIZE, 16f).coerceIn(10f, 32f)

    fun saveSubtitleSize(context: Context, size: Float) {
        prefs(context).edit().putFloat(KEY_SUBTITLE_SIZE, size).apply()
    }

    fun loadBackgroundPlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKGROUND_PLAY, true)

    fun saveBackgroundPlay(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BACKGROUND_PLAY, enabled).apply()
    }
}
