package com.example.media

import android.content.ContentUris
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import com.example.util.AudioFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

data class PhoneAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val folder: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val dateAddedSec: Long,
    val lyricsPath: String?
)

data class PhoneVideoClip(
    val id: Long,
    val name: String,
    val folder: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val dateAddedSec: Long
)

/**
 * Pustaka media milik HP (MediaStore) yang dibagikan ke browser PC lewat Web Connect.
 * Hanya item yang terdaftar di MediaStore yang bisa diakses: pemanggil HTTP mengirim
 * id numerik, jadi tidak ada jalur untuk membaca berkas di luar indeks media.
 */
object PhoneMediaScanner {

    const val TYPE_AUDIO = "audio"
    const val TYPE_VIDEO = "video"

    private val artCache = LruCache<String, ByteArray>(80)
    // Trek tanpa sampul tidak diulang tiap render daftar lagu; pemindaian
    // MediaMetadataRetriever per trek cukup mahal untuk dipanggil terus-menerus.
    private val artMisses = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private const val ART_MAX_SIDE = 440

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

    /** Null bila tipe tidak dikenal atau id bukan media MediaStore. */
    fun uriFor(type: String, id: Long): Uri? {
        if (id <= 0L) return null
        return when (type) {
            TYPE_AUDIO -> ContentUris.withAppendedId(audioCollection(), id)
            TYPE_VIDEO -> ContentUris.withAppendedId(videoCollection(), id)
            else -> null
        }
    }

    fun mimeTypeFor(context: Context, type: String, id: Long): String? {
        val uri = uriFor(type, id) ?: return null
        return try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
    }

    /** Ukuran kolom MediaStore; -1 bila baris atau kolomnya tidak ada. */
    fun sizeOf(context: Context, type: String, id: Long): Long {
        val uri = uriFor(type, id) ?: return -1L
        return try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use -1L
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Izin dicek per jenis media. Dulu audio dan video digabung dengan OR sehingga
     * pengguna yang hanya mengizinkan "Foto dan video" tetap dianggap punya akses
     * audio — pemindaiannya lalu gagal diam-diam dan daftar lagu tampak kosong.
     */
    fun hasMediaAccess(context: Context, type: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        ) {
            return true
        }
        val permission = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> Manifest.permission.READ_EXTERNAL_STORAGE
            type == TYPE_VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            else -> Manifest.permission.READ_MEDIA_AUDIO
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanAudio(context: Context): List<PhoneAudioTrack> = withContext(Dispatchers.IO) {
        val collection = audioCollection()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            "album_artist",
            "bucket_display_name"
        )
        val result = mutableListOf<PhoneAudioTrack>()
        // Pustaka musik memfilter IS_MUSIC; bila kosong (mis. hanya rekaman suara) ambil semua audio.
        for (selection in listOf("${MediaStore.Audio.Media.IS_MUSIC} != 0", null)) {
            result.clear()
            try {
                context.contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")
                    ?.use { cursor -> readAudioCursor(cursor, result) }
            } catch (_: Exception) {
                result.clear()
            }
            if (result.isNotEmpty()) break
        }
        result
    }

    private fun readAudioCursor(cursor: android.database.Cursor, into: MutableList<PhoneAudioTrack>) {
        val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        if (idCol < 0) return
        val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val albumArtistCol = cursor.getColumnIndex("album_artist")
        val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val bucketCol = cursor.getColumnIndex("bucket_display_name")
        val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        while (cursor.moveToNext()) {
            val namedArtist = cursor.stringAt(albumArtistCol).orFallback(cursor.stringAt(artistCol))
            into += PhoneAudioTrack(
                id = cursor.getLong(idCol),
                title = cursor.stringAt(titleCol)?.takeIf { it.isNotBlank() } ?: "Audio",
                artist = namedArtist ?: "Penyanyi Tidak Diketahui",
                album = cursor.stringAt(albumCol)?.takeIf { it.isNotBlank() } ?: "",
                folder = cursor.stringAt(bucketCol)?.takeIf { it.isNotBlank() } ?: "Musik",
                durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                mimeType = cursor.stringAt(mimeCol) ?: AudioFormats.mimeTypeOf(cursor.stringAt(nameCol)),
                dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L,
                lyricsPath = resolveLyricsPath(cursor.stringAt(dataCol))
            )
        }
    }

    /** Null untuk placeholder "unknown artist" agar nama artis berikutnya bisa dipakai. */
    private fun String?.orFallback(fallback: String?): String? {
        val primary = this?.takeIf { it.isNotBlank() && !it.contains("unknown", true) }
        if (primary != null) return primary
        return fallback?.takeIf { it.isNotBlank() && !it.contains("unknown", true) }
    }

    suspend fun scanVideo(context: Context): List<PhoneVideoClip> = withContext(Dispatchers.IO) {
        val collection = videoCollection()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            "bucket_display_name"
        )
        val result = mutableListOf<PhoneVideoClip>()
        try {
            context.contentResolver.query(
                collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                if (idCol < 0) return@use
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val bucketCol = cursor.getColumnIndex("bucket_display_name")
                while (cursor.moveToNext()) {
                    result += PhoneVideoClip(
                        id = cursor.getLong(idCol),
                        name = cursor.stringAt(nameCol)?.takeIf { it.isNotBlank() } ?: "Video",
                        folder = cursor.stringAt(bucketCol)?.takeIf { it.isNotBlank() } ?: "Video",
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L,
                        mimeType = cursor.stringAt(mimeCol) ?: "video/mp4",
                        dateAddedSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    /** Pindai ulang izin/album: lupakan trek yang sebelumnya gagal dapat sampul. */
    fun clearArtworkMisses() {
        artMisses.clear()
    }

    /** Sampul album trek audio (atau thumbnail video) sebagai JPEG, dengan cache kecil. */
    fun artwork(context: Context, type: String, id: Long): ByteArray? {
        val cacheKey = "$type:$id"
        artCache.get(cacheKey)?.let { return it }
        if (artMisses.contains(cacheKey)) return null
        val uri = uriFor(type, id) ?: return null
        val bytes = when (type) {
            TYPE_VIDEO -> videoArtwork(context, uri)
            TYPE_AUDIO -> audioArtwork(context, uri)
            else -> null
        }
        if (bytes == null || bytes.isEmpty()) {
            artMisses.add(cacheKey)
            return null
        }
        artCache.put(cacheKey, bytes)
        return bytes
    }

    private fun videoArtwork(context: Context, uri: Uri): ByteArray? = try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(400, 225), null)
        } else {
            Thumbnails.extractVideoThumbnail(context, uri)
        }
        bitmap?.let { jpegOf(it, 78) }
    } catch (_: Exception) {
        null
    }

    // Tag tertanam -> album art MediaStore -> cover.jpg/folder.jpg di folder lagu.
    private fun audioArtwork(context: Context, uri: Uri): ByteArray? {
        val raw = AlbumArtwork.rawArtwork(context, uri) ?: return null
        return shrinkArtwork(raw)
    }

    // Frame PICTURE/album art bisa berupa PNG beberapa MB. Dikecilkan ke JPEG hemat agar
    // web player tidak menunggu unduhan besar tiap ganti trek; byte asli dipakai
    // kembali hanya bila gambarnya tidak bisa di-decode.
    private fun shrinkArtwork(raw: ByteArray): ByteArray? {
        if (raw.isEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_MAX_SIDE) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return raw
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= ART_MAX_SIDE) return jpegOf(bitmap, 82)
            val ratio = ART_MAX_SIDE.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
            if (scaled !== bitmap) bitmap.recycle()
            jpegOf(scaled, 82)
        } catch (_: Exception) {
            raw
        }
    }

    /** Lirik .lrc yang menempel pada trek HP; null bila tidak ada berkas lirik. */
    fun lyricsFor(context: Context, type: String, id: Long): String? {
        if (type != TYPE_AUDIO) return null
        val path = findLyricsPath(context, id) ?: return null
        return try {
            val file = File(path)
            if (file.isFile && file.canRead()) file.readText(Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun findLyricsPath(context: Context, id: Long): String? {
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        return try {
            context.contentResolver.query(
                audioCollection(),
                arrayOf(MediaStore.Audio.Media.DATA),
                selection,
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) resolveLyricsPath(cursor.getString(0)) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveLyricsPath(dataPath: String?): String? {
        if (dataPath.isNullOrBlank()) return null
        val dot = dataPath.lastIndexOf('.')
        val base = if (dot > 0) dataPath.substring(0, dot) else dataPath
        for (extension in listOf(".lrc", ".LRC", ".Lrc")) {
            val candidate = File(base + extension)
            if (candidate.isFile && candidate.canRead()) return candidate.absolutePath
        }
        return null
    }

    private fun android.database.Cursor.stringAt(index: Int): String? =
        if (index >= 0) getString(index) else null

    private fun jpegOf(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    fun formatDurationMs(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val minutes = total / 60L
        val seconds = total % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
}

private object Thumbnails {
    @Suppress("DEPRECATION")
    fun extractVideoThumbnail(context: Context, uri: Uri): Bitmap? = try {
        MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver,
            ContentUris.parseId(uri),
            MediaStore.Video.Thumbnails.MINI_KIND,
            null
        )
    } catch (_: Exception) {
        null
    }
}
