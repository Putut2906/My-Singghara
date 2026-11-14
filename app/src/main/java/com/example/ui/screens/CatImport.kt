package com.example.ui.screens

import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Impor bank soal CAT dari file teks (.md/.txt), Word modern (.docx), dan Excel (.xlsx).
 * Tanpa dependensi pihak ketiga: .docx/.xlsx dibaca sebagai ZIP + XML (java.util.zip).
 *
 * Format teks yang dikenali (baris, toleran):
 *   KATEGORI: TWK
 *   1. Teks soal ...
 *   A. opsi
 *   B. opsi
 *   KUNCI: B            (atau JAWABAN: 2)
 *   PEMBAHASAN: ...     (opsional, satu baris)
 *   BOBOT: 5,4,3,2      (opsional, khusus TKP; urut sesuai opsi)
 *
 * Format kolom .xlsx (baris pertama boleh header): A=Kategori, B=Soal, C..G=Opsi A-E,
 * H=Kunci, I=Pembahasan, J=Bobot (mis. "5,4,3,2,1").
 */
object CatImport {

    fun importFromBytes(filename: String, bytes: ByteArray): List<SoalCat> {
        val name = filename.lowercase()
        return try {
            when {
                name.endsWith(".docx") -> parseText(docxToText(bytes))
                name.endsWith(".xlsx") -> xlsxToSoal(bytes)
                name.endsWith(".xls") -> emptyList() // .xls biner lama tidak didukung
                name.endsWith(".pdf") || name.endsWith(".doc") -> emptyList() // butuh library
                else -> parseText(String(bytes, Charsets.UTF_8)) // .md, .txt, lainnya
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun supportedExtensions(): List<String> = listOf(".md", ".txt", ".docx", ".xlsx")

    // ------------------------- Parser teks -------------------------

    private class Draft {
        var kategori: String = CAT_TWK
        var teks: String = ""
        val opsi = LinkedHashMap<String, String>()
        var kunciRaw: String = ""
        var pembahasan: String = ""
        var bobotRaw: String = ""

        fun toSoalOrNull(): SoalCat? {
            val letters = listOf("A", "B", "C", "D", "E").filter { opsi.containsKey(it) }
            if (teks.isBlank() || letters.size < 2) return null
            val opsiList = letters.map { opsi[it]!!.trim() }
            var kIdx = 0
            val kr = kunciRaw.trim().uppercase()
            val lm = Regex("([A-E])").find(kr)
            val nm = Regex("(\\d)").find(kr)
            if (lm != null) {
                val li = letters.indexOf(lm.groupValues[1]); if (li >= 0) kIdx = li
            } else if (nm != null) {
                val n = nm.groupValues[1].toInt(); if (n in 1..letters.size) kIdx = n - 1
            }
            val bobot = if (kategori == CAT_TKP) parseBobot(bobotRaw, letters.size, kIdx) else emptyList()
            return SoalCat(
                id = UUID.randomUUID().toString(),
                kategori = kategori,
                teks = teks.trim(),
                opsi = opsiList,
                kunci = kIdx,
                bobotOpsi = bobot,
                pembahasan = pembahasan.trim()
            )
        }
    }

    private fun parseBobot(raw: String, count: Int, bestIdx: Int): List<Int> {
        // Bentuk "5,4,3,2" atau "A=5,B=4,..."
        val nums = Regex("(\\d)").findAll(raw).map { it.groupValues[1].toInt() }.toList()
        if (nums.size >= count) return nums.take(count).map { it.coerceIn(1, 5) }
        // Default: opsi terbaik (bestIdx) = 5, sisanya 4,3,2,1 sesuai urutan.
        val out = MutableList(count) { i -> if (i == bestIdx) 5 else 0 }
        var v = 4
        for (i in 0 until count) if (i != bestIdx) { out[i] = v.coerceAtLeast(1); v-- }
        return out.map { it.coerceIn(1, 5) }
    }

    private val reKategori = Regex("^#*\\s*(?:KATEGORI|Kategori)\\s*[:=]\\s*(TWK|TIU|TKP)", RegexOption.IGNORE_CASE)
    private val reKategoriHeader = Regex("^#+\\s*(TWK|TIU|TKP)\\b", RegexOption.IGNORE_CASE)
    private val reSoal = Regex("^(\\d{1,3})\\s*[.)\\-:]\\s+(.+)$")
    private val reOpsi = Regex("^([A-Ea-e])\\s*[.)\\-:]\\s+(.*)$")
    private val reKunci = Regex("^\\s*(KUNCI|JAWABAN|K)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val rePembahasan = Regex("^\\s*(PEMBAHASAN|EXPLANATION|JELAS|P)\\s*[:=]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val reBobot = Regex("^\\s*BOBOT\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)

    fun parseText(text: String): List<SoalCat> {
        val result = mutableListOf<SoalCat>()
        var kategori = CAT_TWK
        var cur: Draft? = null
        for (rawLine in text.split("\n", "\r")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val km = reKategori.find(line)
            val hk = if (km == null) reKategoriHeader.find(line) else null
            if (km != null || hk != null) {
                cur?.toSoalOrNull()?.let { result.add(it) }; cur = null
                kategori = (km?.groupValues?.get(1) ?: hk!!.groupValues[1]).uppercase()
                continue
            }
            val qm = reSoal.find(line)
            if (qm != null) {
                cur?.toSoalOrNull()?.let { result.add(it) }
                cur = Draft().apply { this.kategori = kategori; teks = qm.groupValues[2].trim() }
                continue
            }
            val om = reOpsi.find(line)
            if (om != null && cur != null) {
                cur!!.opsi[om.groupValues[1].uppercase()] = om.groupValues[2].trim(); continue
            }
            val kn = reKunci.find(line)
            if (kn != null && cur != null) { cur!!.kunciRaw = kn.groupValues[2].trim(); continue }
            val pm = rePembahasan.find(line)
            if (pm != null && cur != null) { cur!!.pembahasan = pm.groupValues[2].trim(); continue }
            val bm = reBobot.find(line)
            if (bm != null && cur != null) { cur!!.bobotRaw = bm.groupValues[1].trim(); continue }
            // Lanjutan teks soal selama opsi belum dimulai.
            val c = cur
            if (c != null && c.opsi.isEmpty()) c.teks += " " + line
        }
        cur?.toSoalOrNull()?.let { result.add(it) }
        return result
    }


    // ------------------------- DOCX -> teks -------------------------

    private fun docxToText(bytes: ByteArray): String {
        val xml = readZipEntry(bytes, "word/document.xml") ?: return ""
        var t = xml
        t = t.replace(Regex("</w:p>"), "\n")
        t = t.replace(Regex("<w:tab[^>]*/>"), " ")
        t = t.replace(Regex("<w:br[^>]*/>"), "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        return decodeEntities(t)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }

    // ------------------------- XLSX -> soal -------------------------

    private fun xlsxToSoal(bytes: ByteArray): List<SoalCat> {
        val shared = readSharedStrings(bytes)
        val sheet = readZipEntry(bytes, "xl/worksheets/sheet1.xml")
            ?: firstSheetEntry(bytes)
            ?: return emptyList()
        val rows = parseSheetRows(sheet, shared)
        val result = mutableListOf<SoalCat>()
        for ((idx, row) in rows.withIndex()) {
            val kategori = (cellAt(row, "A") ?: "").trim().uppercase()
            val soal = (cellAt(row, "B") ?: "").trim()
            if (idx == 0 && (soal.isBlank() || soal.lowercase().contains("soal") || kategori == "KATEGORI")) continue
            if (soal.isBlank()) continue
            val opsi = listOf("C", "D", "E", "F", "G").map { (cellAt(row, it) ?: "").trim() }.filter { it.isNotEmpty() }
            if (opsi.size < 2) continue
            val kat = when (kategori) { CAT_TIU, CAT_TKP -> kategori; else -> CAT_TWK }
            val kunciRaw = (cellAt(row, "H") ?: "").trim().uppercase()
            var kIdx = 0
            val lm = Regex("([A-E])").find(kunciRaw)
            val nm = Regex("(\\d)").find(kunciRaw)
            if (lm != null) { val li = "ABCDE".indexOf(lm.groupValues[1]); if (li >= 0 && li < opsi.size) kIdx = li }
            else if (nm != null) { val n = nm.groupValues[1].toInt(); if (n in 1..opsi.size) kIdx = n - 1 }
            val pembahasan = (cellAt(row, "I") ?: "").trim()
            val bobotRaw = (cellAt(row, "J") ?: "").trim()
            val bobot = if (kat == CAT_TKP) parseBobot(bobotRaw, opsi.size, kIdx) else emptyList()
            result.add(SoalCat(UUID.randomUUID().toString(), kat, soal, opsi, kIdx, bobot, pembahasan))
        }
        return result
    }

    private fun firstSheetEntry(bytes: ByteArray): String? {
        // fallback: entry pertama yang cocok xl/worksheets/sheet*.xml
        var found: String? = null
        try {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml")) {
                        found = z.readBytes().toString(Charsets.UTF_8); break
                    }
                    e = z.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return found
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val xml = readZipEntry(bytes, "xl/sharedStrings.xml") ?: return emptyList()
        val out = mutableListOf<String>()
        val siMatches = Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        for (m in siMatches) {
            val inner = m.groupValues[1]
            val parts = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(inner).map { decodeEntities(it.groupValues[1]) }.toList()
            out.add(parts.joinToString(""))
        }
        return out
    }

    private fun parseSheetRows(xml: String, shared: List<String>): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        val rowMatches = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        for (rm in rowMatches) {
            val cells = mutableMapOf<String, String>()
            val cellMatches = Regex("<c\\b([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rm.groupValues[1])
            for (cm in cellMatches) {
                val attrs = cm.groupValues[1]
                val inner = cm.groupValues[2]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: continue
                val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                var value = ""
                if (type == "inlineStr") {
                    value = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: ""
                    value = decodeEntities(value)
                } else {
                    val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: ""
                    value = if (type == "s") { val i = v.toIntOrNull(); if (i != null && i in shared.indices) shared[i] else "" } else decodeEntities(v)
                }
                cells[ref] = value
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return rows
    }

    private fun cellAt(row: Map<String, String>, col: String): String? = row[col]

    private fun readZipEntry(bytes: ByteArray, entryName: String): String? {
        try {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == entryName) return z.readBytes().toString(Charsets.UTF_8)
                    e = z.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
