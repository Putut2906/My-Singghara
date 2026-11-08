package com.example.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Sampul album dengan rantai fallback: tag tertanam -> cache album art MediaStore
 * -> berkas cover di folder lagu. Banyak album tidak menanam sampul di setiap MP3,
 * jadi album art MediaStore dan cover.jpg folder sering satu-satunya sumber.
 */
object AlbumArtwork {

    private val COVER_BASE_NAMES = listOf("cover", "folder", "album", "front", "artwork", "albumart")
    private val COVER_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

    fun rawArtwork(context: Context, uri: Uri): ByteArray? {
        embeddedPicture(context, uri)?.let { if (it.isNotEmpty()) return it }

        if (uri.scheme == "content") {
            albumArtwork(context, uri)?.let { if (it.isNotEmpty()) return it }
            mediaStoreDataPath(context, uri)?.let { path ->
                folderCoverFromPath(path)?.let { if (it.isNotEmpty()) return it }
            }
        } else if (uri.scheme == "file") {
            folderCoverFromPath(uri.path)?.let { if (it.isNotEmpty()) return it }
        }

        return null
    }

    fun embeddedPicture(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun albumArtwork(context: Context, uri: Uri): ByteArray? {
        val albumId = queryLong(context, uri, arrayOf("album_id")) ?: return null
        if (albumId <= 0L) return null

        // Cache albumart klasik; masih diisi MediaProvider pada banyak perangkat.
        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
        readBytes(context, albumArtUri)?.let { if (it.isNotEmpty()) return it }

        // Kolom album_art di tabel album hanya tersedia sampai Android 9.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val albumPath = try {
                val albumUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    albumId
                )
                context.contentResolver.query(
                    albumUri,
                    arrayOf(MediaStore.Audio.AlbumColumns.ALBUM_ART),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
            } catch (_: Exception) {
                null
            }
            if (!albumPath.isNullOrBlank()) {
                val file = File(albumPath)
                if (file.isFile && file.canRead()) {
                    try {
                        return file.readBytes()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return null
    }

    private fun mediaStoreDataPath(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun folderCoverFromPath(dataPath: String?): ByteArray? {
        if (dataPath.isNullOrBlank()) return null
        val folder = File(dataPath).parentFile ?: return null
        if (!folder.isDirectory) return null
        val byName = try {
            folder.listFiles()?.associateBy { it.name.lowercase() }
        } catch (_: Exception) {
            null
        } ?: return null

        for (baseName in COVER_BASE_NAMES) {
            for (extension in COVER_EXTENSIONS) {
                val file = byName["$baseName.$extension"] ?: continue
                if (!file.isFile || !file.canRead()) continue
                val bytes = try {
                    file.readBytes()
                } catch (_: Exception) {
                    null
                }
                if (!bytes.isNullOrEmpty()) return bytes
            }
        }
        return null
    }

    private fun queryLong(context: Context, uri: Uri, columns: Array<String>): Long? = try {
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
