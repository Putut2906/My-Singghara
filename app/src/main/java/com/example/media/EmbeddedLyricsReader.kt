package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Ekstraksi lirik dari berkas lagu (ID3 SYLT/USLT/TXXX, MediaMetadataRetriever, atom MP4
 * (c)lyr, komentar Vorbis, dan berkas .lrc berdampingan). Dikeluar dari PemutarMusikScreen
 * agar pemutar musik aplikasi dan web player memakai parser yang sama persis.
 */
object EmbeddedLyricsReader {

    private const val MAX_ID3_TAG_BYTES = 16 * 1024 * 1024
    private const val MAX_RAW_LYRIC_SCAN_BYTES = 4 * 1024 * 1024
    private const val MP3_SIZE_PLAIN = 0
    private const val MP3_SIZE_SYNCSAFE = 1

    // 1152 sampel per frame Layer III @ 44,1 kHz; dipakai bila header audio tak terbaca
    // sehingga SYLT berbasis frame tetap bisa dikonversi ke milidetik.
    private const val DEFAULT_MPEG_FRAME_DURATION_MS = 1152.0 * 1000.0 / 44100.0

    private data class Mp3TagLyrics(val synced: String?, val plain: String?)

    private val syncedLyricMarkerRegex = Regex("\\[\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?\\]")

    /** Urutan pencarian sama dengan aplikasi: .lrc -> SYLT -> MMR -> (c)lyr/Vorbis -> USLT/TXXX. */
    fun lyricsFor(context: Context, uriStr: String): String? {
        // 1. File .lrc di folder yang sama (nama sama, lalu nama yang mirip)
        try {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromSingleUri(context, uri)
            val parent = docFile?.parentFile
            val rawName = docFile?.name ?: uri.lastPathSegment?.substringAfterLast("/") ?: ""
            val baseName = rawName.substringBeforeLast(".", rawName)
            val normalizedBase = normalizeNameForLyrics(baseName)
            if (parent != null && normalizedBase.isNotEmpty()) {
                val lrcFiles = try {
                    parent.listFiles().filter { file ->
                        file.isFile &&
                            file.name?.substringAfterLast(".", "")?.equals("lrc", ignoreCase = true) == true
                    }
                } catch (e: Exception) {
                    emptyList()
                }
                val siblingLrc = lrcFiles.firstOrNull {
                    normalizeNameForLyrics(it.name?.substringBeforeLast(".", "") ?: "") == normalizedBase
                } ?: lrcFiles.firstOrNull {
                    val normalized = normalizeNameForLyrics(it.name?.substringBeforeLast(".", "") ?: "")
                    normalized.isNotEmpty() &&
                        (normalized.contains(normalizedBase) || normalizedBase.contains(normalized))
                } ?: parent.findFile("$baseName.lrc")
                    ?: parent.findFile("${baseName.lowercase()}.lrc")

                if (siblingLrc != null && siblingLrc.exists() && siblingLrc.isFile) {
                    readTextFromUri(context, siblingLrc.uri)?.let { return it }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Tag ID3: SYLT (lirik tersinkron per kata) lebih diprioritaskan daripada USLT
        val tagged = readMp3TagLyrics(context, uriStr)
        if (!tagged.synced.isNullOrBlank()) return tagged.synced

        // 3. MediaMetadataRetriever (MP4/FLAC/format lain yang menyimpan lirik)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriStr))
            val lyrics = retriever.extractMetadata(1000)
            if (!lyrics.isNullOrBlank()) return lyrics
        } catch (e: Exception) {
            // ignore
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        // 4. Pemindaian mentah untuk atom (c)lyr (MP4/M4A) dan komentar Vorbis (OGG/FLAC)
        scanRawMediaForLyrics(context, uriStr)?.let { return it }

        // 5. USLT/TXXX (lirik tanpa timestamp) sebagai upaya terakhir
        if (!tagged.plain.isNullOrBlank()) return tagged.plain
        return null
    }

    private fun sanitizeLyricText(raw: String?): String? {
        if (raw == null) return null
        val cut = raw.indexOf('\u0000')
        val base = if (cut >= 0) raw.substring(0, cut) else raw
        return base.replace("\uFEFF", "").trim().ifBlank { null }
    }

    private fun isPlausibleLyricText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        var printable = 0
        var letters = 0
        var total = 0
        for (char in text) {
            if (char == '\u0000') return false
            total++
            if (!char.isISOControl() || char == '\n' || char == '\r' || char == '\t') printable++
            if (char.isLetter()) letters++
        }
        return total >= 2 && printable * 100 / total >= 85 && letters > 0
    }

    private fun bigEndianInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)

    private fun bigEndianInt24(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 16) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                (data[offset + 2].toInt() and 0xFF)

    private fun syncSafeInt32(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0x7F) shl 21) or
                ((data[offset + 1].toInt() and 0x7F) shl 14) or
                ((data[offset + 2].toInt() and 0x7F) shl 7) or
                (data[offset + 3].toInt() and 0x7F)

    private fun indexOfBytes(data: ByteArray, needle: ByteArray, from: Int, limit: Int = data.size): Int {
        if (needle.isEmpty()) return -1
        val last = limit - needle.size
        var index = from
        while (index <= last) {
            var matched = true
            for (n in needle.indices) {
                if (data[index + n] != needle[n]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
            index++
        }
        return -1
    }

    /** Menghapus byte 0x00 yang disisipkan setelah 0xFF pada ID3 unsynchronisation. */
    private fun deUnsynchronise(source: ByteArray): ByteArray {
        val out = ByteArray(source.size)
        var writeIndex = 0
        var index = 0
        while (index < source.size) {
            val byte = source[index]
            out[writeIndex++] = byte
            if ((byte.toInt() and 0xFF) == 0xFF && index + 1 < source.size && source[index + 1] == 0.toByte()) {
                index += 2
            } else {
                index++
            }
        }
        return out.copyOf(writeIndex)
    }

    /** Durasi satu frame MPEG audio (ms) untuk mengubah timestamp SYLT berbasis frame. */
    private fun detectMpegFrameDurationMs(probe: ByteArray, length: Int): Double? {
        var index = 0
        while (index + 4 <= length) {
            val b1 = probe[index].toInt() and 0xFF
            val b2 = probe[index + 1].toInt() and 0xFF
            if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) {
                val versionBits = (b2 shr 3) and 0x03
                val layerBits = (b2 shr 1) and 0x03
                val b3 = probe[index + 2].toInt() and 0xFF
                val sampleRateIndex = (b3 shr 2) and 0x03
                val bitrateIndex = (b3 shr 4) and 0x0F
                if (versionBits != 1 && layerBits != 0 && sampleRateIndex != 3 &&
                    bitrateIndex != 0 && bitrateIndex != 15
                ) {
                    val sampleRate = when (versionBits) {
                        3 -> intArrayOf(44100, 48000, 32000)[sampleRateIndex]
                        2 -> intArrayOf(22050, 24000, 16000)[sampleRateIndex]
                        else -> intArrayOf(11025, 12000, 8000)[sampleRateIndex]
                    }
                    val samplesPerFrame = when (layerBits) {
                        3 -> 384
                        2 -> 1152
                        else -> if (versionBits == 3) 1152 else 576
                    }
                    return samplesPerFrame * 1000.0 / sampleRate
                }
            }
            index++
        }
        return null
    }

    private fun decodeLyricTagText(data: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end) return ""
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(data, start, end - start, charset)
    }

    private fun readTerminatedLyricString(
        data: ByteArray,
        start: Int,
        limit: Int,
        encoding: Int
    ): Pair<String, Int> {
        val isUtf16 = encoding == 1 || encoding == 2
        var cursor = start
        while (cursor < limit) {
            if (isUtf16) {
                if (cursor + 1 >= limit) { cursor = limit; break }
                if (data[cursor] == 0.toByte() && data[cursor + 1] == 0.toByte()) {
                    return decodeLyricTagText(data, start, cursor, encoding) to (cursor + 2).coerceAtMost(limit)
                }
                cursor += 2
            } else {
                if (data[cursor] == 0.toByte()) {
                    return decodeLyricTagText(data, start, cursor, encoding) to (cursor + 1).coerceAtMost(limit)
                }
                cursor++
            }
        }
        return decodeLyricTagText(data, start, limit, encoding) to limit
    }

    private fun formatLyricTag(timeMs: Long): String {
        val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L
        return String.format("%02d:%02d.%03d", totalSeconds / 60L, totalSeconds % 60L, timeMs.coerceAtLeast(0L) % 1000L)
    }

    /** USLT: encoding(1) + language(3) + descriptor + lyric text. */
    private fun parseUsltFrame(data: ByteArray, offset: Int, size: Int): String? {
        val end = offset + size
        if (size <= 4) return null
        val encoding = data[offset].toInt() and 0xFF
        val (_, afterDescriptor) = readTerminatedLyricString(data, offset + 4, end, encoding)
        if (afterDescriptor >= end) return null
        return sanitizeLyricText(decodeLyricTagText(data, afterDescriptor, end, encoding))
    }

    /** TXXX: encoding(1) + description + value; dipakai tagger yang menyimpan lirik sebagai text frame. */
    private fun parseTxxxFrameText(data: ByteArray, offset: Int, size: Int): String? {
        val end = offset + size
        if (size <= 2) return null
        val encoding = data[offset].toInt() and 0xFF
        val (description, afterDescriptor) = readTerminatedLyricString(data, offset + 1, end, encoding)
        val key = description.uppercase()
        if (!key.contains("LYRIC") && !key.contains("LIRIK") && !key.contains("UNSYNC")) return null
        if (afterDescriptor >= end) return null
        return sanitizeLyricText(decodeLyricTagText(data, afterDescriptor, end, encoding))
    }

    private fun syltEntriesToLyricText(entries: List<Pair<Long, String>>): String? {
        if (entries.isEmpty()) return null

        // Ubah entri SYLT menjadi Enhanced LRC agar memakai parser & renderer yang sama.
        // Baris baru dimulai saat ada karakter newline atau jeda antar frasa (> 900 ms).
        val builder = StringBuilder()
        var lineHasWords = false
        var lastTimeMs = -1L
        var breakAfterPrevious = false

        entries.forEach { (timeMs, rawText) ->
            if (lineHasWords && (breakAfterPrevious || timeMs - lastTimeMs > 900L)) {
                builder.append('\n')
                lineHasWords = false
            }
            val stamp = formatLyricTag(timeMs)
            if (!lineHasWords) builder.append('[').append(stamp).append(']')

            val hasLineBreak = rawText.contains('\n') || rawText.contains('\r')
            val cleaned = rawText.replace("\r", " ").replace("\n", " ").trim()
            if (cleaned.isNotEmpty()) {
                if (lineHasWords) builder.append(' ')
                builder.append('<').append(stamp).append('>').append(cleaned)
                lineHasWords = true
            }
            breakAfterPrevious = hasLineBreak
            lastTimeMs = timeMs
        }
        return builder.toString().ifBlank { null }
    }

    /**
     * SYLT: encoding(1) + language(3) + timestamp format(1) + content type(1) + descriptor,
     * lalu pasangan teks + timestamp 4 byte. Format 2 = milidetik, format 1 = jumlah frame
     * MPEG (dikonversi memakai durasi frame audio).
     */
    private fun parseSyltFrame(
        data: ByteArray,
        offset: Int,
        size: Int,
        mpegFrameDurationMs: Double?
    ): String? {
        val end = offset + size
        if (size < 8) return null
        val encoding = data[offset].toInt() and 0xFF
        val timestampFormat = data[offset + 4].toInt() and 0xFF
        if (timestampFormat != 1 && timestampFormat != 2) return null
        if (timestampFormat == 1 && mpegFrameDurationMs == null) return null

        val (_, afterDescriptor) = readTerminatedLyricString(data, offset + 6, end, encoding)
        var cursor = afterDescriptor
        val entries = mutableListOf<Pair<Long, String>>()

        while (cursor < end - 1) {
            val (text, afterText) = readTerminatedLyricString(data, cursor, end, encoding)
            if (afterText + 4 > end) break
            val rawTime = bigEndianInt32(data, afterText).toLong() and 0xFFFFFFFFL
            cursor = afterText + 4
            val timeMs = if (timestampFormat == 2) {
                rawTime
            } else {
                (rawTime * (mpegFrameDurationMs ?: return null)).toLong()
            }
            if (text.isNotEmpty()) entries.add(timeMs to text)
        }
        return syltEntriesToLyricText(entries)
    }

    private fun readFully(stream: java.io.InputStream, buffer: ByteArray, offset: Int, count: Int): Int {
        var read = 0
        while (read < count) {
            val r = stream.read(buffer, offset + read, count - read)
            if (r <= 0) break
            read += r
        }
        return read
    }

    private fun looksLikeSyncedLyrics(text: String?): Boolean =
        text != null && syncedLyricMarkerRegex.containsMatchIn(text)

    private fun mergeMp3Lyrics(base: Mp3TagLyrics, extra: Mp3TagLyrics): Mp3TagLyrics {
        fun pick(a: String?, b: String?): String? = when {
            a.isNullOrBlank() -> b
            b.isNullOrBlank() -> a
            b.length > a.length -> b
            else -> a
        }
        return Mp3TagLyrics(pick(base.synced, extra.synced), pick(base.plain, extra.plain))
    }

    /**
     * Menelusuri frame ID3 secara berurutan. Mendukung v2.2 (header 6 byte, ID 3 huruf)
     * serta v2.3/v2.4 dengan ukuran syncsafe maupun ukuran biasa.
     */
    private fun collectLyricsFromTag(
        tag: ByteArray,
        startOffset: Int,
        readLimit: Int,
        version: Int,
        sizeMode: Int,
        mpegFrameDurationMs: Double?
    ): Mp3TagLyrics {
        var synced: String? = null
        var plain: String? = null
        val headerSize = if (version == 2) 6 else 10
        val idLength = if (version == 2) 3 else 4
        var offset = startOffset

        while (offset + headerSize <= readLimit) {
            val frameId = String(tag, offset, idLength, Charsets.ISO_8859_1)
            if (frameId.isEmpty() || !frameId.all { it.isLetterOrDigit() }) break

            val frameSize = when {
                version == 2 -> bigEndianInt24(tag, offset + 3)
                sizeMode == MP3_SIZE_SYNCSAFE -> syncSafeInt32(tag, offset + 4)
                else -> bigEndianInt32(tag, offset + 4)
            }
            val dataStart = offset + headerSize
            if (frameSize <= 0 || dataStart + frameSize > readLimit) break

            when (frameId) {
                "SYLT", "SLT" -> {
                    val parsed = parseSyltFrame(tag, dataStart, frameSize, mpegFrameDurationMs)
                    if (parsed != null && isPlausibleLyricText(parsed) && (synced?.length ?: -1) < parsed.length) {
                        synced = parsed
                    }
                }
                "USLT", "ULT" -> {
                    val parsed = parseUsltFrame(tag, dataStart, frameSize)
                    if (parsed != null && isPlausibleLyricText(parsed) && (plain?.length ?: -1) < parsed.length) {
                        plain = parsed
                    }
                }
                "TXXX", "TXX" -> {
                    val parsed = parseTxxxFrameText(tag, dataStart, frameSize)
                    if (parsed != null && isPlausibleLyricText(parsed)) {
                        if (looksLikeSyncedLyrics(parsed)) {
                            if ((synced?.length ?: -1) < parsed.length) synced = parsed
                        } else if ((plain?.length ?: -1) < parsed.length) {
                            plain = parsed
                        }
                    }
                }
            }
            offset = dataStart + frameSize
        }
        return Mp3TagLyrics(synced, plain)
    }

    /** Cadangan saat header frame rusak: cari tanda tangan ID frame di seluruh isi tag. */
    private fun scanLyricFrameSignatures(
        tag: ByteArray,
        version: Int,
        mpegFrameDurationMs: Double?
    ): Mp3TagLyrics {
        var synced: String? = null
        var plain: String? = null
        val headerSize = if (version == 2) 6 else 10
        val ids = if (version == 2) listOf("SLT", "ULT", "TXX") else listOf("SYLT", "USLT", "TXXX")

        ids.forEach { frameId ->
            val needle = frameId.toByteArray(Charsets.ISO_8859_1)
            var searchFrom = 0
            var hits = 0
            while (hits < 24) {
                val index = indexOfBytes(tag, needle, searchFrom)
                if (index < 0) break
                hits++
                searchFrom = index + needle.size

                val dataStart = index + headerSize
                if (dataStart >= tag.size) continue
                val remaining = tag.size - dataStart
                val sizes = if (version == 2) {
                    listOf(bigEndianInt24(tag, index + 3))
                } else {
                    listOf(bigEndianInt32(tag, index + 4), syncSafeInt32(tag, index + 4))
                }.filter { it in 1..remaining }
                if (sizes.isEmpty()) continue

                sizes.forEach { frameSize ->
                    when (frameId) {
                        "SYLT", "SLT" -> {
                            val parsed = parseSyltFrame(tag, dataStart, frameSize, mpegFrameDurationMs)
                            if (parsed != null && isPlausibleLyricText(parsed) && (synced?.length ?: -1) < parsed.length) {
                                synced = parsed
                            }
                        }
                        "USLT", "ULT" -> {
                            val parsed = parseUsltFrame(tag, dataStart, frameSize)
                            if (parsed != null && isPlausibleLyricText(parsed) && (plain?.length ?: -1) < parsed.length) {
                                plain = parsed
                            }
                        }
                        else -> {
                            val parsed = parseTxxxFrameText(tag, dataStart, frameSize)
                            if (parsed != null && isPlausibleLyricText(parsed)) {
                                if (looksLikeSyncedLyrics(parsed)) {
                                    if ((synced?.length ?: -1) < parsed.length) synced = parsed
                                } else if ((plain?.length ?: -1) < parsed.length) {
                                    plain = parsed
                                }
                            }
                        }
                    }
                }
            }
        }
        return Mp3TagLyrics(synced, plain)
    }

    /**
     * Pembaca tag ID3 yang toleran: header "ID3" dicari pada 1 KB pertama, versi 2.2-2.4,
     * ukuran syncsafe maupun biasa, extended header, unsynchronisation, lalu pemindaian
     * tanda tangan frame sebagai cadangan.
     */
    private fun readMp3TagLyrics(context: Context, uriStr: String): Mp3TagLyrics {
        try {
            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                val probe = ByteArray(64 * 1024)
                val probeRead = readFully(stream, probe, 0, probe.size)
                if (probeRead < 10) return Mp3TagLyrics(null, null)

                val headerPos = indexOfBytes(
                    probe,
                    byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()),
                    0,
                    minOf(probeRead, 1024)
                )
                if (headerPos < 0 || headerPos + 10 > probeRead) return Mp3TagLyrics(null, null)

                val version = probe[headerPos + 3].toInt() and 0xFF
                if (version !in 2..4) return Mp3TagLyrics(null, null)
                val flags = probe[headerPos + 5].toInt() and 0xFF
                val tagSize = syncSafeInt32(probe, headerPos + 6)
                if (tagSize <= 10 || tagSize > MAX_ID3_TAG_BYTES) return Mp3TagLyrics(null, null)

                val tag = ByteArray(tagSize)
                val dataStart = headerPos + 10
                val fromProbe = minOf(tagSize, (probeRead - dataStart).coerceAtLeast(0))
                if (fromProbe > 0) probe.copyInto(tag, 0, dataStart, dataStart + fromProbe)
                val tagRead = fromProbe + readFully(stream, tag, fromProbe, tagSize - fromProbe)
                if (tagRead < 12) return Mp3TagLyrics(null, null)

                val raw = tag.copyOf(tagRead)
                val isUnsynchronised = (flags and 0x80) != 0
                val body = if (isUnsynchronised) deUnsynchronise(raw) else raw

                var bodyStart = 0
                if ((flags and 0x40) != 0 && body.size >= 4) {
                    bodyStart = when (version) {
                        // ID3v2.4: ukuran extended header sudah termasuk medan ukurannya.
                        4 -> syncSafeInt32(body, 0).coerceIn(0, body.size)
                        // ID3v2.3: ukuran belum termasuk 4 byte medan ukuran itu sendiri.
                        3 -> (bigEndianInt32(body, 0) + 4).coerceIn(0, body.size)
                        else -> 0
                    }
                }

                // Deteksi durasi frame MPEG untuk SYLT yang memakai satuan frame (format 1).
                val audioProbe = ByteArray(16 * 1024)
                val audioStart = dataStart + tagSize
                var audioRead = 0
                if (audioStart < probeRead) {
                    audioRead = minOf(audioProbe.size, probeRead - audioStart)
                    probe.copyInto(audioProbe, 0, audioStart, audioStart + audioRead)
                }
                audioRead += readFully(stream, audioProbe, audioRead, audioProbe.size - audioRead)
                val mpegFrameDurationMs = detectMpegFrameDurationMs(audioProbe, audioRead)
                    ?: DEFAULT_MPEG_FRAME_DURATION_MS

                val sizeModes = if (version == 4) {
                    intArrayOf(MP3_SIZE_SYNCSAFE, MP3_SIZE_PLAIN)
                } else {
                    intArrayOf(MP3_SIZE_PLAIN, MP3_SIZE_SYNCSAFE)
                }

                var result = Mp3TagLyrics(null, null)
                sizeModes.forEach { mode ->
                    result = mergeMp3Lyrics(
                        result,
                        collectLyricsFromTag(body, bodyStart, body.size, version, mode, mpegFrameDurationMs)
                    )
                }
                result = mergeMp3Lyrics(result, scanLyricFrameSignatures(body, version, mpegFrameDurationMs))

                if (isUnsynchronised && result.synced == null) {
                    // Sebagian file menyalakan flag unsync tanpa benar-benar menyisipkan 0x00.
                    result = mergeMp3Lyrics(
                        result,
                        collectLyricsFromTag(raw, bodyStart, raw.size, version, sizeModes[0], mpegFrameDurationMs)
                    )
                }
                return result
            }
        } catch (e: Exception) {
            // ignore
        }
        return Mp3TagLyrics(null, null)
    }

    private fun normalizeNameForLyrics(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun readTextFromUri(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().use { it.readText() }.ifBlank { null }
        }
    } catch (e: Exception) {
        null
    }

    /** MP4/M4A: atom (c)lyr berisi sub-atom "data" dengan teks lirik. */
    private fun findMp4LyricAtom(data: ByteArray, length: Int): String? {
        val needle = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var searchFrom = 0
        var hits = 0
        while (hits < 8) {
            val index = indexOfBytes(data, needle, searchFrom, length)
            if (index < 0) break
            hits++
            searchFrom = index + needle.size

            val dataAtom = index + 4
            if (dataAtom + 16 > length) continue
            if (String(data, dataAtom + 4, 4, Charsets.ISO_8859_1) != "data") continue

            val dataSize = bigEndianInt32(data, dataAtom)
            val dataType = bigEndianInt32(data, dataAtom + 8)
            val payloadStart = dataAtom + 16
            val payloadEnd = minOf(
                length,
                if (dataSize in 17..(length - dataAtom)) dataAtom + dataSize else payloadStart + 8192
            )
            if (payloadEnd <= payloadStart) continue

            val text = if (dataType == 2) {
                String(data, payloadStart, payloadEnd - payloadStart, Charsets.UTF_16BE)
            } else {
                String(data, payloadStart, payloadEnd - payloadStart, Charsets.UTF_8)
            }
            val sanitized = sanitizeLyricText(text) ?: continue
            if (isPlausibleLyricText(sanitized) && sanitized.length >= 8) return sanitized
        }
        return null
    }

    /** Ogg/Opus/FLAC: komentar Vorbis "LYRICS=" atau "UNSYNCEDLYRICS=". */
    private fun findVorbisLyricComment(data: ByteArray, length: Int): String? {
        val keys = listOf("LYRICS=", "UNSYNCEDLYRICS=", "UNSYNCED LYRICS=")
        keys.forEach { key ->
            val needle = key.toByteArray(Charsets.ISO_8859_1)
            var searchFrom = 0
            var hits = 0
            while (hits < 4) {
                val index = indexOfBytes(data, needle, searchFrom, length)
                if (index < 0) break
                hits++
                val valueStart = index + needle.size
                searchFrom = valueStart
                var valueEnd = valueStart
                while (valueEnd < length && valueEnd - valueStart < 128 * 1024) {
                    val b = data[valueEnd].toInt() and 0xFF
                    if (b == 0 || b < 0x09) break
                    valueEnd++
                }
                val text = sanitizeLyricText(String(data, valueStart, valueEnd - valueStart, Charsets.UTF_8)) ?: return@forEach
                if (isPlausibleLyricText(text) && text.length >= 8) return text
            }
        }
        return null
    }

    private fun scanRawMediaForLyrics(context: Context, uriStr: String): String? {
        try {
            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                val buffer = ByteArray(MAX_RAW_LYRIC_SCAN_BYTES)
                val read = readFully(stream, buffer, 0, buffer.size)
                if (read < 32) return null
                findMp4LyricAtom(buffer, read)?.let { return it }
                findVorbisLyricComment(buffer, read)?.let { return it }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }
}
