package desktop.server

import desktop.AppPaths
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * CAT (Simulasi SKD) untuk server desktop. Meniru persis kontrak rute api-cat pada
 * LocalHttpServer (Android) supaya CatWebScript berjalan tanpa diubah, tetapi menyimpan
 * state ke berkas JSON lokal (AppPaths/cat.json) karena desktop tidak punya SharedPreferences.
 * Timer memakai jam sistem (endTime) sehingga menutup/membuka ulang tidak mereset.
 */
class CatApi {

    private data class Paket(val id: String, val nama: String)
    private data class Ban(
        val id: String, val kategori: String, val teks: String,
        val opsi: List<String>, val kunci: Int, val bobot: List<Int>, val pembahasan: String
    )
    private data class Sesi(
        val startTime: Long, val endTime: Long, val order: List<String>,
        val jawaban: Map<Int, Int>, val currentIndex: Int, val selesai: Boolean, val paketId: String
    )
    private data class Riw(
        val id: String, val paketId: String, val paketNama: String, val tanggal: Long,
        val twk: Int, val tiu: Int, val tkp: Int, val total: Int, val lulus: Boolean
    )
    private data class Ulasan(
        val nomor: Int, val kategori: String, val teks: String,
        val opsi: List<String>, val kunci: Int, val pembahasan: String,
        val jawaban: Int, val poin: Int, val benar: Boolean
    )
    private data class Hasil(
        val twk: Int, val tiu: Int, val tkp: Int, val total: Int,
        val twkLulus: Boolean, val tiuLulus: Boolean, val tkpLulus: Boolean, val lulus: Boolean,
        val jumlahDijawab: Int, val jumlahSoal: Int, val ulasan: List<Ulasan>
    )

    private val file = File(AppPaths.dataDir, "cat.json")
    private var paketList: MutableList<Paket> = mutableListOf()
    private var aktif: String = ""
    private val banks: MutableMap<String, MutableList<Ban>> = mutableMapOf()
    private var sesi: Sesi? = null
    private var riwayat: MutableList<Riw> = mutableListOf()

    private val DURASI_MS = 100L * 60L * 1000L
    private val TARGET_TWK = 30; private val TARGET_TIU = 35; private val TARGET_TKP = 45
    private val PG_TWK = 65; private val PG_TIU = 80; private val PG_TKP = 156

    init { load() }

    @Synchronized
    fun handle(path: String, method: String, query: Map<String, String>, body: String): Reply {
        ensureInit()
        val json = parseObject(body)
        return try {
            when {
                path == "/api/cat/bank" && method == "GET" -> Reply.ok(bankJson())
                path == "/api/cat/bank" && method == "POST" -> { addSoal(json); Reply.ok(success()) }
                path == "/api/cat/bank" && method == "DELETE" -> {
                    val id = query["id"] ?: json.str("id")
                    if (id.isNotBlank()) banks[aktif]?.removeAll { it.id == id }
                    save(); Reply.ok(success())
                }
                path == "/api/cat/paket" && method == "GET" -> Reply.ok(paketJson())
                path == "/api/cat/paket" && method == "POST" -> {
                    when (json.str("action")) {
                        "create" -> { val p = Paket(UUID.randomUUID().toString(), json.str("nama", "Tryout ${paketList.size + 1}")); paketList.add(p); banks[p.id] = mutableListOf(); aktif = p.id }
                        "select" -> aktif = json.str("id").ifBlank { aktif }
                        "rename" -> { val i = paketList.indexOfFirst { it.id == json.str("id") }; if (i >= 0) paketList[i] = paketList[i].copy(nama = json.str("nama").ifBlank { paketList[i].nama }) }
                        "delete" -> { val id = json.str("id"); if (paketList.size > 1) { paketList.removeAll { it.id == id }; banks.remove(id); if (aktif == id) aktif = paketList.first().id } }
                    }
                    save(); Reply.ok(paketJson())
                }
                path == "/api/cat/sesi" && method == "GET" -> Reply.ok(sesiJson())
                path == "/api/cat/sesi" && method == "POST" -> sesiAction(json)
                path == "/api/cat/hasil" && method == "GET" -> Reply.ok(hasilJson())
                path == "/api/cat/riwayat" && method == "GET" -> Reply.ok(riwayatJson())
                path == "/api/cat/import" && method == "POST" -> {
                    val name = json.str("filename")
                    val bytes = try { java.util.Base64.getDecoder().decode(json.str("data")) } catch (e: Exception) { ByteArray(0) }
                    val imported = importFromBytes(name, bytes)
                    if (imported.isNotEmpty()) {
                        val list = banks.getOrPut(aktif) { mutableListOf() }
                        list.addAll(imported)
                        save()
                    }
                    Reply.ok(success("count" to imported.size))
                }
                else -> Reply(404, failure("Rute CAT tidak dikenal: $path"))
            }
        } catch (e: Exception) {
            Reply(500, failure(e.message ?: "error"))
        }
    }

    // --- init & persist ---------------------------------------------------------------------------

    private fun addSoal(o: JsonObject) {
        val opsi = (o["opsi"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        val bobot = (o["bobot"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList()
        val id = o.str("id").ifBlank { UUID.randomUUID().toString() }
        val ban = Ban(id, o.str("kategori", "TWK"), o.str("teks"), opsi, o.int("kunci", 0), bobot, o.str("pembahasan"))
        banks.getOrPut(aktif) { mutableListOf() }.add(ban)
        save()
    }

    private fun ensureInit() {
        if (paketList.isEmpty()) {
            val p = Paket(UUID.randomUUID().toString(), "Tryout 1")
            paketList.add(p); banks[p.id] = mutableListOf(); aktif = p.id; save()
        }
        if (aktif.isBlank() || paketList.none { it.id == aktif }) aktif = paketList.first().id
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
            aktif = root.str("aktif")
            (root["paketList"] as? JsonArray)?.forEach { paketList.add(Paket(it.jsonObject.str("id"), it.jsonObject.str("nama"))) }
            (root["banks"] as? JsonObject)?.forEach { (pid, arr) ->
                banks[pid] = (arr as? JsonArray)?.map { banFrom(it.jsonObject) }?.toMutableList() ?: mutableListOf()
            }
            (root["sesi"] as? JsonObject)?.let { s ->
                val jw = mutableMapOf<Int, Int>()
                (s["jawaban"] as? JsonObject)?.forEach { (k, v) -> k.toIntOrNull()?.let { ki -> jw[ki] = v.jsonPrimitive.content.toIntOrNull() ?: 0 } }
                sesi = Sesi(
                    s.str("startTime").toLongOrNull() ?: 0L, s.str("endTime").toLongOrNull() ?: 0L,
                    (s["order"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                    jw, s.str("currentIndex").toIntOrNull() ?: 0, s.bool("selesai"), s.str("paketId")
                )
            }
            (root["riwayat"] as? JsonArray)?.forEach { r ->
                val o = r.jsonObject
                riwayat.add(Riw(o.str("id"), o.str("paketId"), o.str("paketNama"), o.str("tanggal").toLongOrNull() ?: 0, o.str("twk").toIntOrNull() ?: 0, o.str("tiu").toIntOrNull() ?: 0, o.str("tkp").toIntOrNull() ?: 0, o.str("total").toIntOrNull() ?: 0, o.bool("lulus")))
            }
        } catch (_: Exception) { }
    }

    private fun save() {
        try {
            val text = buildJsonObject {
                put("aktif", aktif)
                put("paketList", buildJsonArray { paketList.forEach { add(buildJsonObject { put("id", it.id); put("nama", it.nama) }) } })
                put("banks", buildJsonObject { banks.forEach { (pid, list) -> put(pid, banArray(list)) } })
                sesi?.let { s ->
                    put("sesi", buildJsonObject {
                        put("startTime", s.startTime.toString()); put("endTime", s.endTime.toString())
                        put("order", buildJsonArray { s.order.forEach { add(it) } })
                        put("jawaban", buildJsonObject { s.jawaban.forEach { (k, v) -> put(k.toString(), v.toString()) } })
                        put("currentIndex", s.currentIndex.toString()); put("selesai", s.selesai); put("paketId", s.paketId)
                    })
                }
                put("riwayat", buildJsonArray {
                    riwayat.forEach { r -> add(buildJsonObject {
                        put("id", r.id); put("paketId", r.paketId); put("paketNama", r.paketNama); put("tanggal", r.tanggal.toString())
                        put("twk", r.twk.toString()); put("tiu", r.tiu.toString()); put("tkp", r.tkp.toString()); put("total", r.total.toString()); put("lulus", r.lulus)
                    }) }
                })
            }.toString()
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    private fun banArray(list: List<Ban>): JsonArray = buildJsonArray {
        list.forEach { b -> add(buildJsonObject {
            put("id", b.id); put("kategori", b.kategori); put("teks", b.teks); put("kunci", b.kunci); put("pembahasan", b.pembahasan)
                put("opsi", buildJsonArray { b.opsi.forEach { add(it) } })
                put("bobot", buildJsonArray { b.bobot.forEach { add(it) } })
        }) }
    }

    private fun banFrom(o: JsonObject): Ban {
        val opsi = (o["opsi"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
        val bobot = (o["bobot"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() } ?: emptyList()
        return Ban(o.str("id"), o.str("kategori", "TWK"), o.str("teks"), opsi, o.int("kunci", 0), bobot, o.str("pembahasan"))
    }

    // --- JSON respons ------------------------------------------------------------------------------

    private fun bankJson(): JsonArray = banArray(banks[aktif] ?: emptyList())

    private fun paketJson(): JsonObject = obj {
        put("aktif", aktif)
        put("list", buildJsonArray { paketList.forEach { add(buildJsonObject { put("id", it.id); put("nama", it.nama) }) } })
    }

    private fun sesiJson(): JsonObject {
        val s = sesi ?: return obj { put("active", false) }
        val now = System.currentTimeMillis()
        return obj {
            put("active", true); put("selesai", s.selesai)
            put("startTime", s.startTime); put("endTime", s.endTime)
            put("sisaMs", (s.endTime - now).coerceAtLeast(0L))
            put("currentIndex", s.currentIndex); put("durasiMs", DURASI_MS)
            put("order", buildJsonArray { s.order.forEach { add(it) } })
            put("jawaban", buildJsonObject { s.jawaban.forEach { (k, v) -> put(k.toString(), v) } })
            put("pg", obj { put("twk", PG_TWK); put("tiu", PG_TIU); put("tkp", PG_TKP) })
            put("counts", obj { put("twk", TARGET_TWK); put("tiu", TARGET_TIU); put("tkp", TARGET_TKP) })
        }
    }

    private fun riwayatJson(): JsonArray = buildJsonArray {
        riwayat.sortedBy { it.tanggal }.forEach { r -> add(buildJsonObject {
            put("id", r.id); put("paketId", r.paketId); put("paketNama", r.paketNama); put("tanggal", r.tanggal)
            put("twk", r.twk); put("tiu", r.tiu); put("tkp", r.tkp); put("total", r.total); put("lulus", r.lulus)
        }) }
    }

    // --- aksi sesi ---------------------------------------------------------------------------------

    private fun sesiAction(json: JsonObject): Reply {
        when (json.str("action")) {
            "start" -> {
                val bank = banks[aktif] ?: emptyList()
                val twk = bank.filter { it.kategori == "TWK" }.shuffled().take(TARGET_TWK)
                val tiu = bank.filter { it.kategori == "TIU" }.shuffled().take(TARGET_TIU)
                val tkp = bank.filter { it.kategori == "TKP" }.shuffled().take(TARGET_TKP)
                val chosen = (twk + tiu + tkp).shuffled()
                if (chosen.isEmpty()) return Reply(400, failure("Bank soal kosong"))
                val now = System.currentTimeMillis()
                sesi = Sesi(now, now + DURASI_MS, chosen.map { it.id }, emptyMap(), 0, false, aktif)
                save(); return Reply.ok(success())
            }
            "answer" -> {
                val s = sesi ?: return Reply(400, failure("Tidak ada sesi"))
                if (s.selesai) return Reply.ok(obj { put("success", false); put("expired", true) })
                if (System.currentTimeMillis() > s.endTime) { sesi = s.copy(selesai = true); save(); return Reply.ok(obj { put("success", false); put("expired", true) }) }
                val idx = json.int("index"); val op = json.int("opsi")
                sesi = s.copy(jawaban = s.jawaban + (idx to op)); save(); return Reply.ok(obj { put("success", true); put("sisaMs", (s.endTime - System.currentTimeMillis()).coerceAtLeast(0L)) })
            }
            "navigate" -> {
                val s = sesi
                if (s != null && !s.selesai) { sesi = s.copy(currentIndex = json.int("current", s.currentIndex)); save() }
                return Reply.ok(success())
            }
            "finish" -> {
                val s = sesi ?: return Reply.ok(success())
                val done = s.copy(selesai = true); sesi = done; recordFinish(done); save(); return Reply.ok(success())
            }
            "reset" -> { sesi = null; save(); return Reply.ok(success()) }
            else -> return Reply(400, failure("aksi tidak dikenal"))
        }
    }

    private fun recordFinish(s: Sesi) {
        val h = hitungHasil(s)
        val pid = s.paketId.ifBlank { aktif }
        val nama = paketList.firstOrNull { it.id == pid }?.nama ?: "Tryout"
        riwayat.add(Riw(UUID.randomUUID().toString(), pid, nama, System.currentTimeMillis(), h.twk, h.tiu, h.tkp, h.total, h.lulus))
    }

    // --- Perhitungan hasil (persis CatStore.hitungHasil di Android) -------------------------------

    private fun poinSoal(soal: Ban, jawab: Int): Int {
        if (jawab < 0) return 0
        return when (soal.kategori) {
            "TKP" -> soal.bobot.getOrElse(jawab) { 0 }
            else -> if (jawab == soal.kunci) 5 else 0
        }
    }

    private fun hitungHasil(s: Sesi): Hasil {
        val pid = s.paketId.ifBlank { aktif }
        val bank = (banks[pid] ?: emptyList()).associateBy { it.id }
        var twk = 0; var tiu = 0; var tkp = 0; var dijawab = 0
        val ulasan = mutableListOf<Ulasan>()
        s.order.forEachIndexed { index, id ->
            val soal = bank[id] ?: return@forEachIndexed
            val j = s.jawaban[index] ?: -1
            if (j >= 0) dijawab++
            val poin = poinSoal(soal, j)
            when (soal.kategori) { "TWK" -> twk += poin; "TIU" -> tiu += poin; "TKP" -> tkp += poin }
            ulasan.add(
                Ulasan(
                    nomor = index + 1, kategori = soal.kategori, teks = soal.teks, opsi = soal.opsi,
                    kunci = soal.kunci, pembahasan = soal.pembahasan, jawaban = j, poin = poin,
                    benar = if (soal.kategori == "TKP") poin == 5 else j == soal.kunci && j >= 0
                )
            )
        }
        val total = twk + tiu + tkp
        val twkLulus = twk >= PG_TWK; val tiuLulus = tiu >= PG_TIU; val tkpLulus = tkp >= PG_TKP
        return Hasil(twk, tiu, tkp, total, twkLulus, tiuLulus, tkpLulus, twkLulus && tiuLulus && tkpLulus, dijawab, s.order.size, ulasan)
    }

    private fun hasilJson(): JsonObject {
        val s = sesi ?: return obj { put("available", false) }
        if (!s.selesai) return obj { put("available", false) }
        val h = hitungHasil(s)
        return obj {
            put("available", true)
            put("hasil", obj {
                put("twk", h.twk); put("tiu", h.tiu); put("tkp", h.tkp); put("total", h.total)
                put("twkLulus", h.twkLulus); put("tiuLulus", h.tiuLulus); put("tkpLulus", h.tkpLulus); put("lulus", h.lulus)
                put("jumlahDijawab", h.jumlahDijawab); put("jumlahSoal", h.jumlahSoal)
                put("pg", obj { put("twk", PG_TWK); put("tiu", PG_TIU); put("tkp", PG_TKP) })
                put("ulasan", buildJsonArray {
                    h.ulasan.forEach { u -> add(buildJsonObject {
                        put("nomor", u.nomor); put("kategori", u.kategori); put("jawaban", u.jawaban)
                        put("poin", u.poin); put("benar", u.benar); put("teks", u.teks)
                        put("kunci", u.kunci); put("pembahasan", u.pembahasan)
                        put("opsi", buildJsonArray { u.opsi.forEach { add(it) } })
                    }) }
                })
            })
        }
    }

    // --- Importer (.md/.txt/.docx/.xlsx) -----------------------------------------------------------

    private fun importFromBytes(filename: String, bytes: ByteArray): List<Ban> {
        val n = filename.lowercase()
        return try {
            when {
                n.endsWith(".docx") -> parseText(docxToText(bytes))
                n.endsWith(".xlsx") -> xlsxToBan(bytes)
                n.endsWith(".xls") || n.endsWith(".pdf") || n.endsWith(".doc") -> emptyList()
                else -> parseText(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) { emptyList() }
    }

    private data class Draft(var kategori: String = "TWK", var teks: String = "", val opsi: LinkedHashMap<String, String> = LinkedHashMap(), var kunciRaw: String = "", var pembahasan: String = "", var bobotRaw: String = "")

    private val reKategori = Regex("^#*\\s*(?:KATEGORI|Kategori)\\s*[:=]\\s*(TWK|TIU|TKP)", RegexOption.IGNORE_CASE)
    private val reHeader = Regex("^#+\\s*(TWK|TIU|TKP)\\b", RegexOption.IGNORE_CASE)
    private val reSoal = Regex("^(\\d{1,3})\\s*[.)\\-:]\\s+(.+)$")
    private val reOpsi = Regex("^([A-Ea-e])\\s*[.)\\-:]\\s+(.*)$")
    private val reKunci = Regex("^\\s*(KUNCI|JAWABAN|K)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val rePembahasan = Regex("^\\s*(PEMBAHASAN|EXPLANATION|JELAS|P)\\s*[:=]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val reBobot = Regex("^\\s*BOBOT\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)

    private fun parseBobot(raw: String, count: Int, bestIdx: Int): List<Int> {
        val nums = Regex("(\\d)").findAll(raw).map { it.groupValues[1].toInt() }.toList()
        if (nums.size >= count) return nums.take(count).map { it.coerceIn(1, 5) }
        val out = MutableList(count) { i -> if (i == bestIdx) 5 else 0 }
        var v = 4; for (i in 0 until count) if (i != bestIdx) { out[i] = v.coerceAtLeast(1); v-- }
        return out.map { it.coerceIn(1, 5) }
    }

    private fun parseText(text: String): List<Ban> {
        val result = mutableListOf<Ban>()
        var kategori = "TWK"; var cur: Draft? = null
        fun flush() { cur?.let { toBan(it, kategori)?.let { b -> result.add(b) } }; cur = null }
        for (raw in text.split("\n", "\r")) {
            val line = raw.trim(); if (line.isEmpty()) continue
            val km = reKategori.find(line); val hk = if (km == null) reHeader.find(line) else null
            if (km != null || hk != null) { flush(); kategori = (km?.groupValues?.get(1) ?: hk!!.groupValues[1]).uppercase(); continue }
            reSoal.find(line)?.let { m -> flush(); cur = Draft(kategori = kategori, teks = m.groupValues[2].trim()) }
            if (cur != null && reSoal.find(line) != null) continue
            reOpsi.find(line)?.let { m -> cur?.opsi?.put(m.groupValues[1].uppercase(), m.groupValues[2].trim()) }
            if (cur != null && reOpsi.find(line) != null) continue
            reKunci.find(line)?.let { m -> cur?.kunciRaw = m.groupValues[2].trim() }
            if (cur != null && reKunci.find(line) != null) continue
            rePembahasan.find(line)?.let { m -> cur?.pembahasan = m.groupValues[2].trim() }
            if (cur != null && rePembahasan.find(line) != null) continue
            reBobot.find(line)?.let { m -> cur?.bobotRaw = m.groupValues[1].trim() }
            if (cur != null && reBobot.find(line) != null) continue
            val c = cur; if (c != null && c.opsi.isEmpty()) c.teks += " " + line
        }
        flush()
        return result
    }

    private fun toBan(d: Draft, kategori: String): Ban? {
        val letters = listOf("A", "B", "C", "D", "E").filter { d.opsi.containsKey(it) }
        if (d.teks.isBlank() || letters.size < 2) return null
        val opsiList = letters.map { d.opsi[it]!!.trim() }
        var kIdx = 0
        val kr = d.kunciRaw.trim().uppercase()
        Regex("([A-E])").find(kr)?.let { val li = letters.indexOf(it.groupValues[1]); if (li >= 0) kIdx = li }
            ?: Regex("(\\d)").find(kr)?.let { val n = it.groupValues[1].toInt(); if (n in 1..letters.size) kIdx = n - 1 }
        val bobot = if (kategori == "TKP") parseBobot(d.bobotRaw, letters.size, kIdx) else emptyList()
        return Ban(UUID.randomUUID().toString(), kategori, d.teks.trim(), opsiList, kIdx, bobot, d.pembahasan.trim())
    }

    private fun docxToText(bytes: ByteArray): String {
        val xml = readZip(bytes, "word/document.xml") ?: return ""
        var t = xml.replace(Regex("</w:p>"), "\n").replace(Regex("<w:tab[^>]*/>"), " ").replace(Regex("<w:br[^>]*/>"), "\n").replace(Regex("<[^>]+>"), "")
        return decodeEntities(t)
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }

    private fun xlsxToBan(bytes: ByteArray): List<Ban> {
        val shared = readSharedStrings(bytes)
        val sheet = readZip(bytes, "xl/worksheets/sheet1.xml") ?: return emptyList()
        val rows = parseSheetRows(sheet, shared)
        val result = mutableListOf<Ban>()
        rows.forEachIndexed { idx, row ->
            val kategori = (row["A"] ?: "").trim().uppercase()
            val soal = (row["B"] ?: "").trim()
            if (idx == 0 && (soal.isBlank() || soal.lowercase().contains("soal") || kategori == "KATEGORI")) return@forEachIndexed
            if (soal.isBlank()) return@forEachIndexed
            val opsi = listOf("C", "D", "E", "F", "G").map { (row[it] ?: "").trim() }.filter { it.isNotEmpty() }
            if (opsi.size < 2) return@forEachIndexed
            val kat = when (kategori) { "TIU", "TKP" -> kategori; else -> "TWK" }
            val kr = (row["H"] ?: "").trim().uppercase()
            var kIdx = 0
            Regex("([A-E])").find(kr)?.let { val li = "ABCDE".indexOf(it.groupValues[1]); if (li in 0 until opsi.size) kIdx = li }
                ?: Regex("(\\d)").find(kr)?.let { val n = it.groupValues[1].toInt(); if (n in 1..opsi.size) kIdx = n - 1 }
            val bobot = if (kat == "TKP") parseBobot((row["J"] ?: "").trim(), opsi.size, kIdx) else emptyList()
            result.add(Ban(UUID.randomUUID().toString(), kat, soal, opsi, kIdx, bobot, (row["I"] ?: "").trim()))
        }
        return result
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val xml = readZip(bytes, "xl/sharedStrings.xml") ?: return emptyList()
        return Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { m ->
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(m.groupValues[1]).joinToString("") { decodeEntities(it.groupValues[1]) }
        }.toList()
    }

    private fun parseSheetRows(xml: String, shared: List<String>): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { rm ->
            val cells = mutableMapOf<String, String>()
            Regex("<c\\b([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(rm.groupValues[1]).forEach { cm ->
                val attrs = cm.groupValues[1]; val inner = cm.groupValues[2]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: return@forEach
                val type = Regex("t=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
                var value = ""
                if (type == "inlineStr") value = decodeEntities(Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: "")
                else {
                    val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(inner)?.groupValues?.get(1) ?: ""
                    value = if (type == "s") shared.getOrNull(v.toIntOrNull() ?: -1) ?: "" else decodeEntities(v)
                }
                cells[ref] = value
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return rows
    }

    private fun readZip(bytes: ByteArray, entryName: String): String? {
        try {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) { if (e.name == entryName) return z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
            }
        } catch (_: Exception) { }
        return null
    }
}
