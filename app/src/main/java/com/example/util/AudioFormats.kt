package com.example.util

import java.util.Locale

/**
 * Format audio yang boleh tampil di pemutar musik. Pemutar tidak lagi khusus MP3:
 * AAC, FLAC/ALAC, WAV, dan AIFF ikut dipindai, baik dari folder aplikasi maupun
 * MediaStore. Kodek yang benar-benar bisa didekode tetap ditentukan perangkat.
 */
object AudioFormats {

    val extensions: Set<String> = setOf(
        "mp3",
        "aac", "m4a", "m4b", // AAC / ALAC
        "flac", "alac",
        "wav", "wave",
        "aiff", "aif", "aifc", "caf",
        "ogg", "oga", "opus"
    )

    fun extensionOf(fileName: String?): String =
        fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()

    fun isSupported(fileName: String?): Boolean = extensionOf(fileName) in extensions

    /** MIME untuk berkas yang kolom MIME MediaStore-nya kosong. */
    fun mimeTypeOf(fileName: String?, fallback: String = "audio/mpeg"): String =
        when (extensionOf(fileName)) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "m4a", "m4b", "alac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav", "wave" -> "audio/wav"
            "aiff", "aif", "aifc" -> "audio/aiff"
            "caf" -> "audio/x-caf"
            "ogg", "oga", "opus" -> "audio/ogg"
            else -> fallback
        }
}
