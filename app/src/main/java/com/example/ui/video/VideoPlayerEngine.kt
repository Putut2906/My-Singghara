package com.example.ui.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
fun buildVideoPlayer(
    context: Context,
    decoderMode: VideoPrefs.DecoderMode,
    handleAudioFocus: Boolean
): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(decoderMode != VideoPrefs.DecoderMode.SW)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

    when (decoderMode) {
        VideoPrefs.DecoderMode.SW -> renderersFactory.setMediaCodecSelector { mimeType, secure, tunneling ->
            MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling).filter { it.softwareOnly }
        }
        VideoPrefs.DecoderMode.HW -> renderersFactory.setMediaCodecSelector { mimeType, secure, tunneling ->
            val all = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
            all.filter { !it.softwareOnly }.ifEmpty { all }
        }
        VideoPrefs.DecoderMode.HW_PLUS -> Unit
    }

    return ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            handleAudioFocus
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
}

@OptIn(UnstableApi::class)
fun videoMediaItem(
    uri: Uri,
    externalSubtitle: Uri? = null,
    mimeType: String? = null
): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    if (!mimeType.isNullOrBlank()) {
        builder.setMimeType(mimeType)
    }
    if (externalSubtitle != null) {
        builder.setSubtitleConfigurations(
            listOf(
                MediaItem.SubtitleConfiguration.Builder(externalSubtitle)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        )
    }
    return builder.build()
}

/**
 * Writes a shifted copy of an SRT subtitle so the timing offset applies without
 * touching the original file. Returns null when the file cannot be parsed.
 */
fun createShiftedSubtitle(context: Context, source: Uri, offsetMs: Long): Uri? {
    return try {
        val text = context.contentResolver.openInputStream(source)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return null
        val timingRegex = Regex(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{3})"
        )
        val shifted = timingRegex.replace(text) { match ->
            val start = timeToMs(match.groupValues, 1) + offsetMs
            val end = timeToMs(match.groupValues, 5) + offsetMs
            "${formatSrtTime(start)} --> ${formatSrtTime(end)}"
        }
        val output = File(context.cacheDir, "video_external_subtitle.srt")
        output.writeText(shifted, Charsets.UTF_8)
        Uri.fromFile(output)
    } catch (_: Exception) {
        null
    }
}

private fun timeToMs(groups: List<String>, startIndex: Int): Long {
    val h = groups[startIndex].toLongOrNull() ?: 0L
    val m = groups[startIndex + 1].toLongOrNull() ?: 0L
    val s = groups[startIndex + 2].toLongOrNull() ?: 0L
    val ms = groups[startIndex + 3].toLongOrNull() ?: 0L
    return (h * 3600L + m * 60L + s) * 1000L + ms
}

private fun formatSrtTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val hours = safe / 3600000L
    val minutes = (safe % 3600000L) / 60000L
    val seconds = (safe % 60000L) / 1000L
    val millis = safe % 1000L
    return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
}
