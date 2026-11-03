package com.example.web

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/**
 * Pustaka media yang tersimpan di HP, dipakai browser PC lewat Web Connect.
 * Sumber utama MediaStore (durasi, folder, dan thumbnail ikut tersedia); bila
 * izin media belum diberikan sehingga MediaStore kosong, pemindaian langsung ke
 * penyimpanan eksternal dipakai sebagai cadangan.
 */
data class PhoneMediaItem(
    val id: String,
    val kind: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val bucketName: String,
    val dateAddedSec: Long,
    val mimeType: String,
    val contentUri: Uri,
    val artist: String = ""
)

object PhoneMediaLibrary {

    const val KIND_AUDIO = "audio"
    const val KIND_VIDEO = "video"

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "oga", "flac", "opus", "wma", "amr", "aif", "aiff")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "ts", "mpg", "mpeg", "wmv")

    private const val MAX_FILE_SYSTEM_ITEMS = 3000
    private const val MAX_FOLDER_DEPTH = 5

    fun isAudioFileName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.US) in AUDIO_EXTENSIONS

    fun isVideoFileName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.US) in VIDEO_EXTENSIONS

    fun kindForFile(name: String): String? = when {
        isAudioFileName(name) -> KIND_AUDIO
        isVideoFileName(name) -> KIND_VIDEO
        else -> null
    }

    fun mimeForName(name: String, kind: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return when {
            kind == KIND_VIDEO -> when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "3gp" -> "video/3gpp"
                "flv" -> "video/x-flv"
                "ts" -> "video/mp2t"
                else -> "video/mp4"
            }
            else -> when (ext) {
                "mp3" -> "audio/mpeg"
                "m4a", "mp4" -> "audio/mp4"
                "aac" -> "audio/aac"
                "ogg", "oga", "opus" -> "audio/ogg"
                "wav" -> "audio/wav"
                "flac" -> "audio/flac"
                "amr" -> "audio/amr"
                else -> "audio/mpeg"
            }
        }
    }

    suspend fun scan(context: Context, kind: String): List<PhoneMediaItem> = withContext(Dispatchers.IO) {
        val fromStore = try { scanMediaStore(context, kind) } catch (_: Exception) { emptyList() }
        if (fromStore.isNotEmpty()) fromStore else scanFileSystem(kind)
    }

    /** Mengubah id opaque (audio:12 / video:12 / file:/storage/...) kembali menjadi Uri. */
    fun resolveUri(id: String): Uri? {
        return try {
            when {
                id.startsWith("file:") -> Uri.fromFile(File(id.removePrefix("file:")))
                id.startsWith("$KIND_AUDIO:") -> {
                    val mediaId = id.substringAfter(':').toLongOrNull() ?: return null
                    ContentUris.withAppendedId(audioCollection(), mediaId)
                }
                id.startsWith("$KIND_VIDEO:") -> {
                    val mediaId = id.substringAfter(':').toLongOrNull() ?: return null
                    ContentUris.withAppendedId(videoCollection(), mediaId)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun scanMediaStore(context: Context, kind: String): List<PhoneMediaItem> {
        val isAudio = kind == KIND_AUDIO
        val collection = if (isAudio) audioCollection() else videoCollection()
        val projection = ArrayList<String>().apply {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DURATION)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add("bucket_display_name")
            add(MediaStore.MediaColumns.MIME_TYPE)
            if (isAudio) {
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ARTIST)
            }
        }.toTypedArray()

        val items = ArrayList<PhoneMediaItem>()
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val bucketCol = cursor.getColumnIndex("bucket_display_name")
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val albumCol = if (isAudio) cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM) else -1
            val artistCol = if (isAudio) cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST) else -1

            while (cursor.moveToNext()) {
                if (idCol < 0) continue
                val mediaId = cursor.getLong(idCol)
                val rawName = if (nameCol >= 0) cursor.getString(nameCol) else null
                val name = rawName?.takeIf { it.isNotBlank() } ?: if (isAudio) "Audio" else "Video"
                val mimeFromStore = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                val bucket = when {
                    bucketCol >= 0 -> cursor.getString(bucketCol)?.takeIf { it.isNotBlank() }
                    else -> null
                } ?: (if (albumCol >= 0) cursor.getString(albumCol)?.takeIf { it.isNotBlank() } else null)
                    ?: (if (artistCol >= 0) cursor.getString(artistCol)?.takeIf { it.isNotBlank() } else null)
                    ?: "Lainnya"

                items.add(
                    PhoneMediaItem(
                        id = "$kind:$mediaId",
                        kind = kind,
                        name = name,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        bucketName = bucket,
                        dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L,
                        mimeType = mimeFromStore?.takeIf { it.isNotBlank() } ?: mimeForName(name, kind),
                        contentUri = ContentUris.withAppendedId(collection, mediaId),
                        artist = if (artistCol >= 0) {
                            cursor.getString(artistCol)?.takeIf {
                                it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
                            } ?: ""
                        } else ""
                    )
                )
            }
        }
        return items
    }

    private fun scanFileSystem(kind: String): List<PhoneMediaItem> {
        val wantAudio = kind == KIND_AUDIO
        val items = ArrayList<PhoneMediaItem>()
        for (root in candidateRoots()) {
            walk(root, 0, items) { file ->
                val isMatch = if (wantAudio) isAudioFileName(file.name) else isVideoFileName(file.name)
                isMatch && file.length() > 0
            }
            if (items.size >= MAX_FILE_SYSTEM_ITEMS) break
        }
        items.sortByDescending { it.dateAddedSec }
        return items
    }

    private fun candidateRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        @Suppress("DEPRECATION")
        Environment.getExternalStorageDirectory()?.let { roots.add(it) }
        return roots.filter { it.exists() && it.canRead() }.toList()
    }

    private fun walk(dir: File, depth: Int, out: ArrayList<PhoneMediaItem>, accept: (File) -> Boolean) {
        if (depth > MAX_FOLDER_DEPTH || out.size >= MAX_FILE_SYSTEM_ITEMS) return
        val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (child in children) {
            if (out.size >= MAX_FILE_SYSTEM_ITEMS) return
            if (child.isDirectory) {
                val name = child.name
                if (name.equals("Android", ignoreCase = true) || name.startsWith(".")) continue
                walk(child, depth + 1, out, accept)
            } else if (child.isFile && accept(child)) {
                out.add(
                    PhoneMediaItem(
                        id = "file:" + child.absolutePath,
                        kind = if (isAudioFileName(child.name)) KIND_AUDIO else KIND_VIDEO,
                        name = child.name,
                        sizeBytes = child.length(),
                        durationMs = 0L,
                        bucketName = child.parentFile?.name?.takeIf { it.isNotBlank() } ?: "Lainnya",
                        dateAddedSec = child.lastModified() / 1000L,
                        mimeType = mimeForName(child.name, if (isAudioFileName(child.name)) KIND_AUDIO else KIND_VIDEO),
                        contentUri = Uri.fromFile(child)
                    )
                )
            }
        }
    }

    /** Bytes thumbnail JPEG untuk satu item; null bila tidak tersedia. */
    suspend fun loadThumbnailBytes(context: Context, item: PhoneMediaItem): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bitmap = if (item.kind == KIND_VIDEO) {
                videoThumbnail(context, item)
            } else {
                embeddedAudioArt(context, item)
            } ?: return@withContext null
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                stream.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun videoThumbnail(context: Context, item: PhoneMediaItem): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return context.contentResolver.loadThumbnail(item.contentUri, Size(360, 202), null)
            } catch (_: Exception) {
            }
        }
        return try {
            if (item.id.startsWith("video:")) {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    item.id.substringAfter(':').toLong(),
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun embeddedAudioArt(context: Context, item: PhoneMediaItem): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.contentUri)
            val picture = retriever.embeddedPicture
            if (picture != null && picture.isNotEmpty()) android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size) else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun displayNameWithoutExtension(item: PhoneMediaItem): String =
        item.name.substringBeforeLast('.', item.name)

    /**
     * MediaStore sesekali melaporkan SIZE 0, sehingga panjang sebenarnya diambil dari
     * descriptor saat streaming.
     */
    fun effectiveSize(item: PhoneMediaItem, descriptorLength: Long): Long =
        if (item.sizeBytes > 0L) item.sizeBytes else descriptorLength
}
