package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.MainViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

// ============================ MODEL DATA ============================

const val CAT_TWK = "TWK"
const val CAT_TIU = "TIU"
const val CAT_TKP = "TKP"

data class SoalCat(
    val id: String,
    val kategori: String,          // TWK | TIU | TKP
    val teks: String,
    val opsi: List<String>,        // 2..5 opsi
    val kunci: Int,                // index benar (TWK/TIU) atau opsi berbobot 5 (TKP)
    val bobotOpsi: List<Int>,      // TKP: skor tiap opsi (1..5); TWK/TIU: list kosong
    val pembahasan: String,
    val gambar: String = ""        // path file gambar (TIU: soal bergambar)
)

data class CatSesi(
    val startTime: Long,
    val endTime: Long,
    val order: List<String>,       // id soal urut sesi (stabil walau di-reload)
    val jawaban: Map<Int, Int>,    // index soal -> index opsi
    val currentIndex: Int,
    val selesai: Boolean,
    val paketId: String = ""       // paket tryout yang dipakai
)

data class CatPaket(
    val id: String,
    val nama: String
)

data class RiwayatItem(
    val id: String,
    val paketId: String,
    val paketNama: String,
    val tanggal: Long,
    val twk: Int,
    val tiu: Int,
    val tkp: Int,
    val total: Int,
    val lulus: Boolean
)

data class UlasanSoal(
    val nomor: Int,
    val kategori: String,
    val soal: SoalCat,
    val jawaban: Int,              // -1 jika kosong
    val poin: Int,
    val benar: Boolean
)

data class CatHasil(
    val twk: Int,
    val tiu: Int,
    val tkp: Int,
    val total: Int,
    val twkLulus: Boolean,
    val tiuLulus: Boolean,
    val tkpLulus: Boolean,
    val lulus: Boolean,
    val jumlahDijawab: Int,
    val jumlahSoal: Int,
    val ulasan: List<UlasanSoal>
)

// ============================ PENYIMPANAN & LOGIKA ============================

object CatStore {
    private const val PREFS = "cat_prefs"
    private const val KEY_BANK_LEGACY = "cat_soal_bank"
    private const val KEY_PAKET = "cat_paket_list"
    private const val KEY_AKTIF = "cat_aktif_paket"
    private const val KEY_SESI = "cat_sesi_aktif"
    private const val KEY_RIWAYAT = "cat_riwayat"

    const val DURASI_MS = 100L * 60L * 1000L // 100 menit
    const val TARGET_TWK = 30
    const val TARGET_TIU = 35
    const val TARGET_TKP = 45
    const val TOTAL_SOAL = 110

    // Passing grade SKD (dapat disesuaikan).
    const val PG_TWK = 65
    const val PG_TIU = 80
    const val PG_TKP = 156

    /** Batas maksimal pengulangan ujian per paket tryout. */
    const val MAX_PERCOBAAN = 5

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun bankKey(paketId: String) = "cat_bank_" + paketId

    // ------------------------- PAKET (tryout) -------------------------

    fun paketList(ctx: Context): MutableList<CatPaket> {
        val raw = prefs(ctx).getString(KEY_PAKET, null) ?: return mutableListOf()
        val out = mutableListOf<CatPaket>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(CatPaket(o.getString("id"), o.getString("nama")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun savePaketList(ctx: Context, list: List<CatPaket>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("nama", it.nama)) }
        prefs(ctx).edit().putString(KEY_PAKET, arr.toString()).apply()
    }

    /** Pastikan ada minimal 1 paket; migrasikan bank lama (global) ke paket pertama. */
    fun ensureInit(ctx: Context) {
        val list = paketList(ctx)
        if (list.isNotEmpty()) return
        val p = CatPaket(UUID.randomUUID().toString(), "Tryout 1")
        savePaketList(ctx, listOf(p))
        prefs(ctx).edit().putString(KEY_AKTIF, p.id).apply()
        val legacy = prefs(ctx).getString(KEY_BANK_LEGACY, null)
        if (!legacy.isNullOrEmpty() && legacy != "[]") {
            prefs(ctx).edit().putString(bankKey(p.id), legacy).remove(KEY_BANK_LEGACY).apply()
        }
    }

    fun aktifPaketId(ctx: Context): String {
        ensureInit(ctx)
        val stored = prefs(ctx).getString(KEY_AKTIF, null)
        val list = paketList(ctx)
        return if (stored != null && list.any { it.id == stored }) stored else (list.firstOrNull()?.id ?: "")
    }

    fun setAktifPaket(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_AKTIF, id).apply()
    }

    fun buatPaket(ctx: Context, nama: String): CatPaket {
        ensureInit(ctx)
        val list = paketList(ctx)
        val p = CatPaket(UUID.randomUUID().toString(), nama.ifBlank { "Tryout ${list.size + 1}" })
        list.add(p)
        savePaketList(ctx, list)
        setAktifPaket(ctx, p.id)
        return p
    }

    fun renamePaket(ctx: Context, id: String, nama: String) {
        val list = paketList(ctx)
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) { list[idx] = list[idx].copy(nama = nama.ifBlank { list[idx].nama }); savePaketList(ctx, list) }
    }

    fun hapusPaket(ctx: Context, id: String) {
        val list = paketList(ctx)
        if (list.size <= 1) return // sisakan minimal satu paket
        list.removeAll { it.id == id }
        savePaketList(ctx, list)
        prefs(ctx).edit().remove(bankKey(id)).apply()
        if (prefs(ctx).getString(KEY_AKTIF, null) == id) setAktifPaket(ctx, list.first().id)
    }

    // ------------------------- BANK SOAL (per paket) -------------------------

    fun loadBank(ctx: Context, paketId: String = aktifPaketId(ctx)): MutableList<SoalCat> {
        val raw = prefs(ctx).getString(bankKey(paketId), "[]") ?: "[]"
        val out = mutableListOf<SoalCat>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val opsiArr = o.getJSONArray("opsi")
                val opsi = (0 until opsiArr.length()).map { opsiArr.getString(it) }
                val bobArr = o.optJSONArray("bobot") ?: JSONArray()
                val bobot = (0 until bobArr.length()).map { bobArr.getInt(it) }
                out.add(
                    SoalCat(
                        id = o.getString("id"),
                        kategori = o.getString("kategori"),
                        teks = o.getString("teks"),
                        opsi = opsi,
                        kunci = o.getInt("kunci"),
                        bobotOpsi = bobot,
                        pembahasan = o.optString("pembahasan", ""),
                        gambar = o.optString("gambar", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun soalToJson(s: SoalCat): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("kategori", s.kategori)
        put("teks", s.teks)
        put("opsi", JSONArray(s.opsi))
        put("kunci", s.kunci)
        put("bobot", JSONArray(s.bobotOpsi))
        put("pembahasan", s.pembahasan)
        put("gambar", s.gambar)
    }

    fun saveBank(ctx: Context, list: List<SoalCat>, paketId: String = aktifPaketId(ctx)) {
        val arr = JSONArray()
        list.forEach { arr.put(soalToJson(it)) }
        prefs(ctx).edit().putString(bankKey(paketId), arr.toString()).apply()
    }

    fun addSoal(ctx: Context, soal: SoalCat, paketId: String = aktifPaketId(ctx)) {
        val list = loadBank(ctx, paketId)
        list.add(soal)
        saveBank(ctx, list, paketId)
    }

    fun deleteSoal(ctx: Context, id: String, paketId: String = aktifPaketId(ctx)) {
        val list = loadBank(ctx, paketId)
        list.removeAll { it.id == id }
        saveBank(ctx, list, paketId)
    }

    fun countByKategori(ctx: Context, paketId: String = aktifPaketId(ctx)): Map<String, Int> {
        val map = mutableMapOf(CAT_TWK to 0, CAT_TIU to 0, CAT_TKP to 0)
        loadBank(ctx, paketId).forEach { s -> map[s.kategori] = (map[s.kategori] ?: 0) + 1 }
        return map
    }

    // ------------------------- SESI -------------------------

    fun loadSesi(ctx: Context): CatSesi? {
        val raw = prefs(ctx).getString(KEY_SESI, null) ?: return null
        return try {
            val o = JSONObject(raw)
            val orderArr = o.getJSONArray("order")
            val order = (0 until orderArr.length()).map { orderArr.getString(it) }
            val jArr = o.getJSONObject("jawaban")
            val jawaban = mutableMapOf<Int, Int>()
            jArr.keys().forEach { k -> jawaban[k.toInt()] = jArr.getInt(k) }
            CatSesi(
                startTime = o.getLong("startTime"),
                endTime = o.getLong("endTime"),
                order = order,
                jawaban = jawaban,
                currentIndex = o.getInt("currentIndex"),
                selesai = o.getBoolean("selesai"),
                paketId = o.optString("paketId", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveSesi(ctx: Context, s: CatSesi) {
        val o = JSONObject().apply {
            put("startTime", s.startTime)
            put("endTime", s.endTime)
            put("order", JSONArray(s.order))
            put("currentIndex", s.currentIndex)
            put("selesai", s.selesai)
            put("paketId", s.paketId)
            val j = JSONObject()
            s.jawaban.forEach { (k, v) -> j.put(k.toString(), v) }
            put("jawaban", j)
        }
        prefs(ctx).edit().putString(KEY_SESI, o.toString()).apply()
    }

    fun clearSesi(ctx: Context) {
        prefs(ctx).edit().remove(KEY_SESI).apply()
    }

    /** Sisa waktu (ms) dihitung dari jam sistem sehingga menutup/membuka ulang tidak mereset. */
    fun sisaMs(sesi: CatSesi, now: Long): Long = (sesi.endTime - now).coerceAtLeast(0L)

    /** Mulai sesi baru dari paket tertentu: urut per kategori TWK -> TIU -> TKP (bukan acak), maks MAX_PERCOBAAN. */
    fun mulaiSesi(ctx: Context, paketId: String = aktifPaketId(ctx)): CatSesi? {
        if (percobaanTerpakai(ctx, paketId) >= MAX_PERCOBAAN) return null
        val bank = loadBank(ctx, paketId)
        val twk = bank.filter { it.kategori == CAT_TWK }.take(TARGET_TWK)
        val tiu = bank.filter { it.kategori == CAT_TIU }.take(TARGET_TIU)
        val tkp = bank.filter { it.kategori == CAT_TKP }.take(TARGET_TKP)
        val chosen = twk + tiu + tkp
        if (chosen.isEmpty()) return null
        val now = System.currentTimeMillis()
        val sesi = CatSesi(
            startTime = now,
            endTime = now + DURASI_MS,
            order = chosen.map { it.id },
            jawaban = emptyMap(),
            currentIndex = 0,
            selesai = false,
            paketId = paketId
        )
        saveSesi(ctx, sesi)
        return sesi
    }

    // ------------------------- PENILAIAN -------------------------

    private fun poinSoal(soal: SoalCat, jawab: Int): Int {
        if (jawab < 0) return 0
        return when (soal.kategori) {
            CAT_TKP -> soal.bobotOpsi.getOrElse(jawab) { 0 }
            else -> if (jawab == soal.kunci) 5 else 0
        }
    }

    fun hitungHasil(ctx: Context, sesi: CatSesi): CatHasil {
        val pid = sesi.paketId.ifBlank { aktifPaketId(ctx) }
        val bank = loadBank(ctx, pid).associateBy { it.id }
        var twk = 0; var tiu = 0; var tkp = 0
        var dijawab = 0
        val ulasan = mutableListOf<UlasanSoal>()
        sesi.order.forEachIndexed { index, id ->
            val soal = bank[id] ?: return@forEachIndexed
            val j = sesi.jawaban[index] ?: -1
            if (j >= 0) dijawab++
            val poin = poinSoal(soal, j)
            when (soal.kategori) {
                CAT_TWK -> twk += poin
                CAT_TIU -> tiu += poin
                CAT_TKP -> tkp += poin
            }
            ulasan.add(
                UlasanSoal(
                    nomor = index + 1,
                    kategori = soal.kategori,
                    soal = soal,
                    jawaban = j,
                    poin = poin,
                    benar = if (soal.kategori == CAT_TKP) poin == 5 else j == soal.kunci && j >= 0
                )
            )
        }
        val total = twk + tiu + tkp
        val twkLulus = twk >= PG_TWK
        val tiuLulus = tiu >= PG_TIU
        val tkpLulus = tkp >= PG_TKP
        return CatHasil(
            twk = twk, tiu = tiu, tkp = tkp, total = total,
            twkLulus = twkLulus, tiuLulus = tiuLulus, tkpLulus = tkpLulus,
            lulus = twkLulus && tiuLulus && tkpLulus,
            jumlahDijawab = dijawab, jumlahSoal = sesi.order.size, ulasan = ulasan
        )
    }

    // ------------------------- RIWAYAT (untuk grafik garis) -------------------------

    fun riwayatList(ctx: Context): MutableList<RiwayatItem> {
        val raw = prefs(ctx).getString(KEY_RIWAYAT, "[]") ?: "[]"
        val out = mutableListOf<RiwayatItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    RiwayatItem(
                        id = o.getString("id"),
                        paketId = o.optString("paketId", ""),
                        paketNama = o.optString("paketNama", ""),
                        tanggal = o.getLong("tanggal"),
                        twk = o.getInt("twk"), tiu = o.getInt("tiu"), tkp = o.getInt("tkp"),
                        total = o.getInt("total"), lulus = o.getBoolean("lulus")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Jumlah percobaan (riwayat) yang sudah tercatat untuk suatu paket. */
    fun percobaanTerpakai(ctx: Context, paketId: String = aktifPaketId(ctx)): Int =
        riwayatList(ctx).count { it.paketId == paketId }

    private fun saveRiwayat(ctx: Context, list: List<RiwayatItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("paketId", it.paketId).put("paketNama", it.paketNama)
                    .put("tanggal", it.tanggal).put("twk", it.twk).put("tiu", it.tiu).put("tkp", it.tkp)
                    .put("total", it.total).put("lulus", it.lulus)
            )
        }
        prefs(ctx).edit().putString(KEY_RIWAYAT, arr.toString()).apply()
    }

    fun tambahRiwayat(ctx: Context, sesi: CatSesi, hasil: CatHasil) {
        val pid = sesi.paketId.ifBlank { aktifPaketId(ctx) }
        val nama = paketList(ctx).firstOrNull { it.id == pid }?.nama ?: "Tryout"
        val list = riwayatList(ctx)
        list.add(
            RiwayatItem(
                id = UUID.randomUUID().toString(), paketId = pid, paketNama = nama,
                tanggal = System.currentTimeMillis(), twk = hasil.twk, tiu = hasil.tiu,
                tkp = hasil.tkp, total = hasil.total, lulus = hasil.lulus
            )
        )
        saveRiwayat(ctx, list)
    }

    fun hapusRiwayat(ctx: Context) {
        prefs(ctx).edit().remove(KEY_RIWAYAT).apply()
    }

    /** Reset hitungan percobaan ujian untuk satu paket (hapus riwayat exam paket tsj; skor manual tetap). */
    fun resetPercobaan(ctx: Context, paketId: String = aktifPaketId(ctx)) {
        val list = riwayatList(ctx)
        list.removeAll { it.paketId == paketId }
        saveRiwayat(ctx, list)
    }

    /** Tambah entri skor manual (untuk Grafik Progres). total & lulus dihitung otomatis. */
    fun tambahRiwayatManual(
        ctx: Context,
        twk: Int,
        tiu: Int,
        tkp: Int,
        tanggalMs: Long = System.currentTimeMillis()
    ): RiwayatItem {
        val total = twk + tiu + tkp
        val lulus = twk >= PG_TWK && tiu >= PG_TIU && tkp >= PG_TKP
        val item = RiwayatItem(
            id = UUID.randomUUID().toString(), paketId = "", paketNama = "Manual",
            tanggal = tanggalMs, twk = twk, tiu = tiu, tkp = tkp, total = total, lulus = lulus
        )
        val list = riwayatList(ctx)
        list.add(item)
        saveRiwayat(ctx, list)
        return item
    }

    fun hapusRiwayatItem(ctx: Context, id: String) {
        val list = riwayatList(ctx)
        list.removeAll { it.id == id }
        saveRiwayat(ctx, list)
    }
}

// ============================ LAYAR UTAMA (stub, diisi bertahap) ============================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SimulasiCatScreen(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var paketList by remember { mutableStateOf(CatStore.paketList(context)) }
    var paketId by remember { mutableStateOf(CatStore.aktifPaketId(context)) }
    var bank by remember { mutableStateOf(CatStore.loadBank(context)) }
    var sesi by remember { mutableStateOf(CatStore.loadSesi(context)) }
    var hasil by remember { mutableStateOf<CatHasil?>(null) }

    fun reload() {
        paketList = CatStore.paketList(context)
        paketId = CatStore.aktifPaketId(context)
        bank = CatStore.loadBank(context)
        sesi = CatStore.loadSesi(context)
    }

    Column(Modifier.fillMaxSize()) {
        PaketBar(
            paketList = paketList,
            aktifId = paketId,
            onSelect = { id -> CatStore.setAktifPaket(context, id); reload() },
            onCreate = { nama -> CatStore.buatPaket(context, nama); reload() },
            onRename = { id, nama -> CatStore.renamePaket(context, id, nama); reload() },
            onDelete = { id -> CatStore.hapusPaket(context, id); reload() }
        )
        Box(Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> TabInputSoal(bank = bank, onChanged = { bank = CatStore.loadBank(context) })
                1 -> TabSimulasi(
                    sesi = sesi,
                    onSesiChanged = { sesi = CatStore.loadSesi(context) },
                    onFinish = { h ->
                        val done = CatStore.loadSesi(context)
                        if (done != null) CatStore.tambahRiwayat(context, done, h)
                        hasil = h
                        reload()
                        selectedTab = 2
                    },
                    onGoInput = { selectedTab = 0 }
                )
                2 -> TabHasil(
                    hasil = hasil,
                    sesi = sesi,
                    onCompute = { s -> CatStore.hitungHasil(context, s) },
                    onRestart = { selectedTab = 1 }
                )
                else -> TabGrafikProgres()
            }
        }
    }
}

// ---------------------- TAB 1: INPUT SOAL ----------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TabInputSoal(bank: List<SoalCat>, onChanged: () -> Unit) {
    val context = LocalContext.current
    var kategori by remember { mutableStateOf(CAT_TWK) }
    var teks by remember { mutableStateOf("") }
    var opsiList by remember { mutableStateOf(listOf("", "", "", "")) }
    var kunci by remember { mutableIntStateOf(0) }
    var bobot by remember { mutableStateOf(listOf(5, 4, 3, 2)) }
    var pembahasan by remember { mutableStateOf("") }
    var gambarPath by remember { mutableStateOf("") }
    val counts = remember(bank) { CatStore.countByKategori(context) }
    var importMsg by remember { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val name = catQueryFileName(context, uri) ?: "soal.txt"
                val imported = CatImport.importFromBytes(name, bytes)
                imported.forEach { CatStore.addSoal(context, it) }
                importMsg = if (imported.isEmpty())
                    "Tidak ada soal terbaca (cek format/ekstensi: .md/.txt/.docx/.xlsx)."
                else "Berhasil impor ${imported.size} soal."
                onChanged()
            } catch (e: Exception) {
                importMsg = "Gagal impor: ${e.message}"
            }
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                if (bytes.isNotEmpty()) {
                    val dir = File(context.filesDir, "cat_img").apply { mkdirs() }
                    val f = File(dir, "${UUID.randomUUID()}.img")
                    f.writeBytes(bytes)
                    gambarPath = f.absolutePath
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CatKartu {
            Text("Ketersediaan Bank Soal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountChip("TWK ${counts[CAT_TWK] ?: 0}/${CatStore.TARGET_TWK}")
                CountChip("TIU ${counts[CAT_TIU] ?: 0}/${CatStore.TARGET_TIU}")
                CountChip("TKP ${counts[CAT_TKP] ?: 0}/${CatStore.TARGET_TKP}")
            }
        }

        CatKartu {
            Text("Impor Soal dari File", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Format didukung: .md, .txt, .docx, .xlsx. Contoh tiap soal: baris \"1. Soal\", lalu \"A. opsi\"..\"E. opsi\", \"KUNCI: B\", \"PEMBAHASAN: ...\" (TKP: \"BOBOT: 5,4,3,2,1\").",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pilih File & Impor")
            }
            if (importMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(importMsg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        CatKartu {
            Text("Tambah Soal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("Kategori", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CAT_TWK, CAT_TIU, CAT_TKP).forEach { k ->
                    FilterChip(
                        selected = kategori == k,
                        onClick = { kategori = k },
                        label = { Text(k) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = teks,
                onValueChange = { teks = it },
                label = { Text("Soal / pertanyaan") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            if (kategori == CAT_TIU) {
                Spacer(Modifier.height(8.dp))
                Text("Gambar Soal (opsional — untuk TIU)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (gambarPath.isNotEmpty()) {
                    AsyncImage(
                        model = File(gambarPath),
                        contentDescription = "Pratinjau gambar soal",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { imageLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("Ganti Gambar")
                        }
                        OutlinedButton(onClick = { gambarPath = "" }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("Hapus")
                        }
                    }
                } else {
                    OutlinedButton(onClick = { imageLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("Impor Gambar Soal")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (kategori == CAT_TKP) "Opsi (atur bobot 1-5 tiap opsi; tandai yang terbaik = 5)"
                else "Opsi (tandai jawaban benar)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            opsiList.forEachIndexed { idx, nilai ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = kunci == idx, onClick = { kunci = idx })
                    OutlinedTextField(
                        value = nilai,
                        onValueChange = { nv -> opsiList = opsiList.toMutableList().also { it[idx] = nv } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Opsi ${'A' + idx}") }
                    )
                    if (kategori == CAT_TKP) {
                        Spacer(Modifier.width(6.dp))
                        BobotDropdown(value = bobot.getOrElse(idx) { 1 }) { bv ->
                            bobot = List(opsiList.size) { i -> if (i == idx) bv else bobot.getOrElse(i) { 1 } }
                        }
                    }
                    if (opsiList.size > 2) {
                        IconButton(onClick = {
                            opsiList = opsiList.filterIndexed { i, _ -> i != idx }
                            bobot = bobot.filterIndexed { i, _ -> i != idx }
                            if (kunci >= opsiList.size) kunci = 0
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (opsiList.size < 5) {
                TextButton(onClick = {
                    opsiList = opsiList + ""
                    bobot = bobot + 1
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah Opsi")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pembahasan,
                onValueChange = { pembahasan = it },
                label = { Text("Pembahasan (opsional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val b = if (kategori == CAT_TKP) List(opsiList.size) { i -> bobot.getOrElse(i) { 1 } } else emptyList()
                    CatStore.addSoal(
                        context,
                        SoalCat(
                            id = UUID.randomUUID().toString(),
                            kategori = kategori,
                            teks = teks.trim(),
                            opsi = opsiList.map { it.trim() },
                            kunci = kunci,
                            bobotOpsi = b,
                            pembahasan = pembahasan.trim(),
                            gambar = if (kategori == CAT_TIU) gambarPath else ""
                        )
                    )
                    teks = ""
                    opsiList = listOf("", "", "", "")
                    bobot = listOf(5, 4, 3, 2)
                    kunci = 0
                    pembahasan = ""
                    gambarPath = ""
                    onChanged()
                },
                enabled = (teks.isNotBlank() || gambarPath.isNotEmpty()) && opsiList.none { it.isBlank() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan Soal") }
        }

        Text("Daftar Soal (${bank.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (bank.isEmpty()) {
            Text("Belum ada soal. Tambahkan di atas.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            bank.asReversed().forEach { s ->
                CatKartu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KategoriBadge(s.kategori)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.teks, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (s.gambar.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                AsyncImage(
                                    model = File(s.gambar),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            Text(
                                "${s.opsi.size} opsi • kunci: ${'A' + s.kunci}" +
                                    if (s.kategori == CAT_TKP) " • bobot ${s.bobotOpsi}" else "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { CatStore.deleteSoal(context, s.id); onChanged() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------- Helper kecil ----------------------

@Composable
private fun CatKartu(content: @Composable ColumnScope.() -> Unit) {
    val light = !androidx.compose.foundation.isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (light) Color.White else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (light) Color(0x140F172A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (light) 8.dp else 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun CountChip(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KategoriBadge(kategori: String) {
    val c = when (kategori) {
        CAT_TWK -> Color(0xFF2563EB)
        CAT_TIU -> Color(0xFF0F766E)
        else -> Color(0xFFB45309)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.4f))
    ) {
        Text(kategori, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BobotDropdown(value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Bobot") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().width(110.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..5).forEach { n ->
                DropdownMenuItem(text = { Text(n.toString()) }, onClick = { onChange(n); expanded = false })
            }
        }
    }
}

// ---------------------- TAB 2: SIMULASI UJIAN ----------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TabSimulasi(
    sesi: CatSesi?,
    onSesiChanged: () -> Unit,
    onFinish: (CatHasil) -> Unit,
    onGoInput: () -> Unit
) {
    val context = LocalContext.current
    val aktif = sesi?.takeIf { !it.selesai }
    if (aktif == null) {
        PetunjukMulai(
            onStart = { val s = CatStore.mulaiSesi(context); if (s != null) onSesiChanged() },
            onGoInput = onGoInput,
            adaSesiSelesai = sesi?.selesai == true,
            terpakai = CatStore.percobaanTerpakai(context),
            maks = CatStore.MAX_PERCOBAAN
        )
        return
    }

    val bankMap = remember(aktif.startTime) { CatStore.loadBank(context).associateBy { it.id } }
    var jawaban by remember(aktif.startTime) { mutableStateOf(aktif.jawaban.toMap()) }
    var current by remember(aktif.startTime) { mutableIntStateOf(aktif.currentIndex.coerceIn(0, (aktif.order.size - 1).coerceAtLeast(0))) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showFinishDialog by remember { mutableStateOf(false) }

    LaunchedEffect(aktif.startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val sisa = CatStore.sisaMs(aktif, now)
    LaunchedEffect(sisa == 0L) {
        if (sisa == 0L) {
            val done = aktif.copy(jawaban = jawaban, currentIndex = current, selesai = true)
            CatStore.saveSesi(context, done)
            onFinish(CatStore.hitungHasil(context, done))
        }
    }

    val soalList = aktif.order.mapNotNull { bankMap[it] }
    val soalAktif = soalList.getOrNull(current)
    val selected = jawaban[current]
    val dijawabCount = jawaban.size
    val belum = aktif.order.size - dijawabCount
    var liveScore by remember { mutableIntStateOf(0) }
    LaunchedEffect(jawaban, bankMap) {
        var s = 0
        aktif.order.forEachIndexed { idx, id ->
            val so = bankMap[id]
            val j = jawaban[idx]
            if (so != null && j != null) {
                s += when (so.kategori) {
                    CAT_TKP -> so.bobotOpsi.getOrElse(j) { 0 }
                    else -> if (j == so.kunci) 5 else 0
                }
            }
        }
        liveScore = s
    }

    Column(Modifier.fillMaxSize()) {
        CatKartu {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Sisa Waktu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatCountdown(sisa),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (sisa < 60_000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Passing Grade", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("TWK ${CatStore.PG_TWK} • TIU ${CatStore.PG_TIU} • TKP ${CatStore.PG_TKP}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoStat("Dijawab", "$dijawabCount")
                InfoStat("Belum", "$belum")
                InfoStat("Live Score", "$liveScore")
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            if (soalAktif == null) {
                Text("Soal tidak ditemukan.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                CatKartu {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KategoriBadge(soalAktif.kategori)
                        Spacer(Modifier.width(8.dp))
                        Text("Soal ${current + 1} / ${aktif.order.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(soalAktif.teks, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (soalAktif.gambar.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = File(soalAktif.gambar),
                            contentDescription = "Gambar soal",
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    soalAktif.opsi.forEachIndexed { i, o ->
                        val isSel = selected == i
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
                                .clickable { jawaban = jawaban + (current to i) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSel, onClick = { jawaban = jawaban + (current to i) })
                            Text("${'A' + i}. $o", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Navigasi Nomor Soal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                aktif.order.indices.forEach { idx ->
                    NumberCell(nomor = idx + 1, answered = jawaban.containsKey(idx), active = idx == current, onClick = { current = idx })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { current = (current + 1).coerceAtMost(aktif.order.size - 1) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lewati", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        CatStore.saveSesi(context, aktif.copy(jawaban = jawaban, currentIndex = current))
                        current = (current + 1).coerceAtMost(aktif.order.size - 1)
                    },
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Simpan & Lanjut", fontSize = 12.sp)
                }
                Button(
                    onClick = { showFinishDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Selesai", fontSize = 12.sp)
                }
            }
        }
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Selesai Ujian?") },
            text = { Text("Masih ada $belum soal belum dijawab. Yakin mengakhiri tes? Jawaban tidak dapat diubah setelah selesai.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    val done = aktif.copy(jawaban = jawaban, currentIndex = current, selesai = true)
                    CatStore.saveSesi(context, done)
                    onFinish(CatStore.hitungHasil(context, done))
                }) { Text("Ya, Selesai", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showFinishDialog = false }) { Text("Lanjut Mengerjakan") } }
        )
    }
}

@Composable
private fun PetunjukMulai(onStart: () -> Unit, onGoInput: () -> Unit, adaSesiSelesai: Boolean, terpakai: Int = 0, maks: Int = CatStore.MAX_PERCOBAAN) {
    val context = LocalContext.current
    val counts = remember { CatStore.countByKategori(context) }
    val totalAvailable = (counts[CAT_TWK] ?: 0) + (counts[CAT_TIU] ?: 0) + (counts[CAT_TKP] ?: 0)
    var used by remember { mutableIntStateOf(terpakai) }
    var resetDialog by remember { mutableStateOf(false) }
    val habisPercobaan = used >= maks
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (adaSesiSelesai) {
            CatKartu {
                Text("Sesi sebelumnya sudah selesai.", fontWeight = FontWeight.Bold)
                Text("Lihat hasil di tab 'Hasil & Live Score', atau mulai sesi baru di bawah.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CatKartu {
            Text("Petunjuk Ujian (SKD CPNS)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Bullet("Durasi: 100 menit (timer berjalan dari waktu sistem; menutup aplikasi tidak mereset waktu).")
            Bullet("Jumlah soal: ${CatStore.TOTAL_SOAL} (TWK ${CatStore.TARGET_TWK}, TIU ${CatStore.TARGET_TIU}, TKP ${CatStore.TARGET_TKP}).")
            Bullet("Penilaian TWK/TIU: benar = 5, salah/kosong = 0.")
            Bullet("Penilaian TKP: skala 1-5 per opsi (tidak bernilai 0 bila dijawab).")
            Bullet("Passing grade: TWK ${CatStore.PG_TWK}, TIU ${CatStore.PG_TIU}, TKP ${CatStore.PG_TKP}.")
            Spacer(Modifier.height(8.dp))
            Text(
                "Ketersediaan bank soal Anda: TWK ${counts[CAT_TWK] ?: 0}, TIU ${counts[CAT_TIU] ?: 0}, TKP ${counts[CAT_TKP] ?: 0} (total $totalAvailable).",
                fontSize = 12.sp,
                color = if (totalAvailable == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (totalAvailable < CatStore.TOTAL_SOAL) {
                Text("Bank soal belum lengkap (ideal 110). Ujian akan memakai soal seadanya.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onGoInput) { Text("Ke Input Soal") }
            }
        }
        CatKartu {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Percobaan Ujian", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (used > 0) {
                    Text("Reset", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { resetDialog = true })
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Sudah dipakai: $used dari $maks. Sisa: ${(maks - used).coerceAtLeast(0)}.",
                fontSize = 13.sp,
                color = if (habisPercobaan) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (habisPercobaan) {
                Spacer(Modifier.height(4.dp))
                Text("Batas $maks percobaan tercapai — simulasi ujian dikunci. Tekan Reset untuk mengulang.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(onClick = onStart, enabled = totalAvailable > 0 && !habisPercobaan, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(if (habisPercobaan) "Batas Percobaan Tercapai" else "Mulai Ujian")
        }
        Spacer(Modifier.height(24.dp))
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset percobaan?") },
            text = { Text("Riwayat percobaan paket ini ($used) akan dihapus sehingga ujian bisa dimulai lagi dari percobaan ke-1. Tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    CatStore.resetPercobaan(context)
                    used = 0
                    resetDialog = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NumberCell(nomor: Int, answered: Boolean, active: Boolean, onClick: () -> Unit) {
    val bg = when {
        active -> MaterialTheme.colorScheme.primary
        answered -> Color(0xFF16A34A)
        else -> Color(0xFFDC2626)
    }
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg.copy(alpha = if (active) 1f else 0.85f))
            .border(
                width = if (active) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("$nomor", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

// ---------------------- TAB 3: HASIL & LIVE SCORE ----------------------

@Composable
private fun TabHasil(
    hasil: CatHasil?,
    sesi: CatSesi?,
    onCompute: (CatSesi) -> CatHasil,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    val h = hasil ?: sesi?.takeIf { it.selesai }?.let { onCompute(it) }
    var showResetDialog by remember { mutableStateOf(false) }
    val pid = (sesi?.paketId?.ifBlank { null } ?: CatStore.aktifPaketId(context))
    val riwayat = remember(h) { CatStore.riwayatList(context).sortedBy { it.tanggal } }
    val riwayatPaket = riwayat.filter { it.paketId == pid }
    val percobaanKe = riwayatPaket.size.coerceAtLeast(1)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (h == null) {
            CatKartu {
                Text("Belum ada hasil", fontWeight = FontWeight.Bold)
                Text("Selesaikan simulasi ujian terlebih dahulu untuk melihat skor dan pembahasan.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Mulai Simulasi") }
            return@Column
        }

        // Header ringkas
        CatKartu {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Hasil Ujian", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Surface(shape = RoundedCornerShape(8.dp), color = (if (h.lulus) Color(0xFF16A34A) else Color(0xFFDC2626)).copy(alpha = 0.15f)) {
                    Text(if (h.lulus) "LULUS" else "GAGAL", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (h.lulus) Color(0xFF16A34A) else Color(0xFFDC2626))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoStat("Percobaan", "$percobaanKe/${CatStore.MAX_PERCOBAAN}")
                InfoStat("Dijawab", "${h.jumlahDijawab}/${h.jumlahSoal}")
                InfoStat("Benar", "${h.ulasan.count { it.benar }}")
            }
        }

        // SATU kartu gabungan: Total Score + Rincian + Status dalam satu doughnut (konsep chart Tabungan)
        CatKartu {
            Text("Total & Rincian Skor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatDoughnut(
                    segments = listOf(
                        DoughnutSeg("TWK ${h.twk}", h.twk.toFloat(), Color(0xFF2563EB), "#1E3A8A"),
                        DoughnutSeg("TIU ${h.tiu}", h.tiu.toFloat(), Color(0xFFFBC02D), "#B45309"),
                        DoughnutSeg("TKP ${h.tkp}", h.tkp.toFloat(), Color(0xFFEC4899), "#BE185D")
                    ),
                    centerTitle = "TOTAL",
                    centerValue = "${h.total}",
                    centerSub = if (h.lulus) "LULUS / 550" else "GAGAL / 550",
                    centerColor = if (h.lulus) Color(0xFF16A34A) else Color(0xFFDC2626)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KategoriScore("TWK", h.twk, 150, CatStore.PG_TWK, h.twkLulus)
                    KategoriScore("TIU", h.tiu, 175, CatStore.PG_TIU, h.tiuLulus)
                    KategoriScore("TKP", h.tkp, 225, CatStore.PG_TKP, h.tkpLulus)
                }
            }
        }

        // Riwayat percobaan (maks 5) + reset
        if (riwayatPaket.isNotEmpty()) {
            CatKartu {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riwayat Percobaan", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showResetDialog = true }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(4.dp))
                riwayatPaket.asReversed().forEachIndexed { idx, r ->
                    val no = riwayatPaket.size - idx
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text("$no", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(r.tanggal)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("TWK ${r.twk} • TIU ${r.tiu} • TKP ${r.tkp}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${r.total}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = (if (r.lulus) Color(0xFF16A34A) else Color(0xFFDC2626)).copy(alpha = 0.15f)) {
                            Text(if (r.lulus) "LULUS" else "GAGAL", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (r.lulus) Color(0xFF16A34A) else Color(0xFFDC2626))
                        }
                    }
                }
            }
        }

        // Ulasan & Pembahasan: satu kartu daftar ringkas (bukan 1 kartu per soal)
        UlasanList(h.ulasan)
        Spacer(Modifier.height(24.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset percobaan?") },
            text = { Text("Riwayat percobaan paket ini (${riwayatPaket.size}) akan dihapus agar ujian bisa diulang dari percobaan ke-1. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    CatStore.resetPercobaan(context, pid)
                    showResetDialog = false
                    onRestart()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Batal") } }
        )
    }
}

private data class DoughnutSeg(val label: String, val value: Float, val color: Color, val textHex: String)

@Composable
private fun CatDoughnut(
    segments: List<DoughnutSeg>,
    centerTitle: String,
    centerValue: String,
    centerSub: String,
    centerColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    boxSize: Dp = 150.dp
) {
    val total = segments.sumOf { it.value.toDouble() }.toFloat()
    Box(modifier.size(boxSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(boxSize - 12.dp)) {
            val sw = 16.dp.toPx()
            val d = this.size
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arcSize = Size(d.width - sw, d.height - sw)
            if (total <= 0f) {
                drawArc(Color(0x22000000), 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(width = sw))
            } else {
                var acc = 0f
                segments.forEach { s ->
                    val sweep = (s.value / total) * 360f
                    if (sweep > 0f) {
                        drawArc(s.color, -90f + acc, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
                        acc += sweep
                    }
                }
                var acc2 = 0f
                segments.forEach { s ->
                    val sweep = (s.value / total) * 360f
                    if (sweep > 20f) {
                        val margin = 2.dp.toPx()
                        val rect = android.graphics.RectF(margin, margin, d.width - margin, d.height - margin)
                        val path = android.graphics.Path().apply { addArc(rect, -90f + acc2, sweep) }
                        val pm = android.graphics.PathMeasure(path, false)
                        if (pm.length > 0f) {
                            val paint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                color = android.graphics.Color.parseColor(s.textHex)
                                textSize = 9.5.sp.toPx()
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawIntoCanvas { c -> c.nativeCanvas.drawTextOnPath(s.label, path, pm.length / 2f, -2.dp.toPx(), paint) }
                        }
                    }
                    acc2 += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerValue, fontSize = 22.sp, fontWeight = FontWeight.Black, color = centerColor)
            Text(centerSub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KategoriScore(label: String, skor: Int, maks: Int, pg: Int, lulus: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(48.dp))
        LinearProgressIndicator(
            progress = { (skor.toFloat() / maks.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(6.dp)),
            color = if (lulus) Color(0xFF16A34A) else Color(0xFFDC2626),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text("$skor/$maks", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = (if (lulus) Color(0xFF16A34A) else Color(0xFFDC2626)).copy(alpha = 0.15f)
        ) {
            Text(if (lulus) "≥$pg" else "<$pg", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (lulus) Color(0xFF16A34A) else Color(0xFFDC2626))
        }
    }
}

@Composable
private fun UlasanList(ulasan: List<UlasanSoal>) {
    val benarColor = Color(0xFF16A34A)
    val salahColor = Color(0xFFDC2626)
    CatKartu {
        Text("Ulasan & Pembahasan (${ulasan.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        if (ulasan.isEmpty()) {
            Text("Tidak ada soal.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ulasan.forEachIndexed { index, u ->
                var expanded by remember(u.nomor) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .background(if (u.benar) benarColor.copy(alpha = 0.15f) else salahColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("${u.nomor}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (u.benar) benarColor else salahColor) }
                        Spacer(Modifier.width(8.dp))
                        KategoriBadge(u.kategori)
                        Spacer(Modifier.width(8.dp))
                        Text(u.soal.teks, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = if (expanded) 20 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(6.dp))
                        Text("+${u.poin}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (u.benar) benarColor else salahColor)
                    }
                    if (expanded) {
                        if (u.soal.gambar.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            AsyncImage(model = File(u.soal.gambar), contentDescription = "Gambar soal", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Spacer(Modifier.height(8.dp))
                        u.soal.opsi.forEachIndexed { i, o ->
                            val isKunci = i == u.soal.kunci
                            val isJwb = i == u.jawaban
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Text("${'A' + i}. ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(o, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                if (isKunci) Text("  ✓ kunci", fontSize = 11.sp, color = benarColor, fontWeight = FontWeight.Bold)
                                if (isJwb) Text("  ← jawabanmu", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (u.jawaban < 0) Text("Tidak dijawab.", fontSize = 12.sp, color = salahColor)
                        if (u.soal.pembahasan.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Pembahasan: ${u.soal.pembahasan}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (index < ulasan.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UlasanCard(u: UlasanSoal) {
    var expanded by remember { mutableStateOf(false) }
    val benarColor = Color(0xFF16A34A)
    val salahColor = Color(0xFFDC2626)
    CatKartu {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            KategoriBadge(u.kategori)
            Spacer(Modifier.width(8.dp))
            Text("${u.nomor}. ${u.soal.teks}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = if (expanded) 20 else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("+${u.poin}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (u.benar) benarColor else salahColor)
        }
        if (u.soal.gambar.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = File(u.soal.gambar),
                contentDescription = "Gambar soal",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            u.soal.opsi.forEachIndexed { i, o ->
                val isKunci = if (u.kategori == CAT_TKP) i == u.soal.kunci else i == u.soal.kunci
                val isJwb = i == u.jawaban
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Text("${'A' + i}. ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(o, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (isKunci) Text("  ✓ kunci", fontSize = 11.sp, color = benarColor, fontWeight = FontWeight.Bold)
                    if (isJwb) Text("  ← jawabanmu", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (u.jawaban < 0) Text("Tidak dijawab.", fontSize = 12.sp, color = salahColor)
            if (u.soal.pembahasan.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Pembahasan: ${u.soal.pembahasan}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun catQueryFileName(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}

// ---------------------- PEMILIH PAKET TRYOUT ----------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaketBar(
    paketList: List<CatPaket>,
    aktifId: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf("create") }
    var input by remember { mutableStateOf("") }
    val aktifNama = paketList.firstOrNull { it.id == aktifId }?.nama ?: "Paket"

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Paket Tryout", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(aktifNama, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = aktifNama,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Pilih paket") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    paketList.forEach { p ->
                        DropdownMenuItem(text = { Text(p.nama) }, onClick = { onSelect(p.id); expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { dialogMode = "create"; input = ""; showDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah", fontSize = 13.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { dialogMode = "rename"; input = aktifNama; showDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ubah", fontSize = 13.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onDelete(aktifId) },
                    enabled = paketList.size > 1,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hapus", fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (dialogMode == "create") "Buat Paket Baru" else "Ganti Nama Paket") },
            text = {
                Column {
                    Text("Contoh: Tryout 1, Tryout 2, atau nama lain.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, label = { Text("Nama paket") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (input.isNotBlank()) {
                        if (dialogMode == "create") onCreate(input.trim()) else onRename(aktifId, input.trim())
                    }
                    showDialog = false
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Batal") } }
        )
    }
}

// ---------------------- GRAFIK GARIS (perkembangan skor) ----------------------

@Composable
private fun LineChart(
    values: List<Float>,
    labels: List<String>,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = lineColor.copy(alpha = 0.12f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val padL = 44f
        val padR = 12f
        val padT = 12f
        val padB = 12f
        val chartW = (w - padL - padR).coerceAtLeast(1f)
        val chartH = (h - padT - padB).coerceAtLeast(1f)
        val maxY = maxValue.coerceAtLeast(1f)
        for (i in 0..4) {
            val yy = padT + chartH * (i / 4f)
            drawLine(gridColor.copy(alpha = 0.5f), Offset(padL, yy), Offset(padL + chartW, yy), 1f)
        }
        if (values.isEmpty()) return@Canvas
        val n = values.size
        fun xAt(i: Int): Float = if (n == 1) padL + chartW / 2f else padL + chartW * (i.toFloat() / (n - 1))
        fun yAt(v: Float): Float = padT + chartH * (1f - (v / maxY).coerceIn(0f, 1f))
        val pts = values.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }
        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(pts.first().x, padT + chartH)
            pts.forEach { path.lineTo(it.x, it.y) }
            path.lineTo(pts.last().x, padT + chartH)
            path.close()
            drawPath(path, fillColor)
        }
        for (i in 0 until pts.size - 1) drawLine(lineColor, pts[i], pts[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
        pts.forEach { drawCircle(lineColor, radius = 5f, center = it) }
    }

    if (labels.isNotEmpty() && labels.size <= 14) {
        Row(Modifier.fillMaxWidth().padding(start = 44.dp, end = 12.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ---------------------- TAB 4: GRAFIK PROGRES (input skor TWK/TIU/TKP) ----------------------

@Composable
private fun TabGrafikProgres() {
    val context = LocalContext.current
    var riwayat by remember { mutableStateOf(CatStore.riwayatList(context).sortedBy { it.tanggal }) }
    var twk by remember { mutableStateOf("") }
    var tiu by remember { mutableStateOf("") }
    var tkp by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Grafik Progres Skor", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Masukkan skor TWK, TIU, dan TKP tiap latihan untuk memantau perkembangan.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tambah Skor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { ScoreField("TWK", twk) { twk = it } }
                    Box(Modifier.weight(1f)) { ScoreField("TIU", tiu) { tiu = it } }
                    Box(Modifier.weight(1f)) { ScoreField("TKP", tkp) { tkp = it } }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val a = twk.toIntOrNull()
                    val b = tiu.toIntOrNull()
                    val c = tkp.toIntOrNull()
                    if (a == null || b == null || c == null) {
                        msg = "Isi TWK, TIU, dan TKP dengan angka." to true
                        return@Button
                    }
                    if (a !in 0..150 || b !in 0..175 || c !in 0..225) {
                        msg = "Rentang: TWK 0-150, TIU 0-175, TKP 0-225." to true
                        return@Button
                    }
                    CatStore.tambahRiwayatManual(context, a, b, c)
                    riwayat = CatStore.riwayatList(context).sortedBy { it.tanggal }
                    twk = ""; tiu = ""; tkp = ""
                    msg = "Skor tersimpan." to false
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Skor")
                }
                msg?.let { (t, err) ->
                    Spacer(Modifier.height(8.dp))
                    Text(t, fontSize = 12.sp, color = if (err) Color(0xFFDC2626) else Color(0xFF16A34A))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (riwayat.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Belum ada data. Simpan skor pertama Anda untuk melihat grafik.",
                    Modifier.padding(16.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            GroupedBarCard(riwayat)
        }

        Spacer(Modifier.height(16.dp))
        RiwayatTable(riwayat = riwayat, onDelete = { id ->
            CatStore.hapusRiwayatItem(context, id)
            riwayat = CatStore.riwayatList(context).sortedBy { it.tanggal }
        })
    }
}

@Composable
private fun GroupedBarCard(riwayat: List<RiwayatItem>) {
    val twkC = Color(0xFF2563EB); val tiuC = Color(0xFFFACC15); val tkpC = Color(0xFFEC4899)
    CatKartu {
        Text("Perbandingan Skor (TWK / TIU / TKP)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(twkC, "TWK")
            LegendDot(tiuC, "TIU")
            LegendDot(tkpC, "TKP")
        }
        Spacer(Modifier.height(10.dp))
        GroupedBarChart(
            items = riwayat,
            colors = listOf(twkC, tiuC, tkpC),
            maxes = listOf(150f, 175f, 225f),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
    }
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupedBarChart(
    items: List<RiwayatItem>,
    colors: List<Color>,
    maxes: List<Float>,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val padL = 36f; val padR = 8f; val padT = 10f; val padB = 26f
        val chartW = (size.width - padL - padR).coerceAtLeast(1f)
        val chartH = (size.height - padT - padB).coerceAtLeast(1f)
        for (i in 0..4) {
            val yy = padT + chartH * (i / 4f)
            drawLine(gridColor.copy(alpha = 0.5f), Offset(padL, yy), Offset(padL + chartW, yy), 1f)
        }
        if (items.isEmpty()) return@Canvas
        val n = items.size
        val groupW = chartW / n
        val barGap = groupW * 0.14f
        val barW = ((groupW - barGap * 2) / 3f).coerceAtLeast(2f)
        val series = listOf(
            items.map { it.twk.toFloat() },
            items.map { it.tiu.toFloat() },
            items.map { it.tkp.toFloat() }
        )
        for (i in 0 until n) {
            val gx = padL + groupW * i
            for (s in 0 until 3) {
                val v = series[s][i]
                val frac = (v / maxes[s]).coerceIn(0f, 1f)
                val h = chartH * frac
                val bx = gx + barGap + barW * s
                drawRoundRect(
                    color = colors[s],
                    topLeft = Offset(bx, padT + chartH - h),
                    size = Size(barW - 1f, h),
                    cornerRadius = CornerRadius(3f, 3f)
                )
            }
        }
    }
    if (items.isNotEmpty() && items.size <= 14) {
        Row(Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            items.forEach { Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.tanggal)), fontSize = 9.sp, color = labelColor) }
        }
    }
}

@Composable
private fun RiwayatTable(riwayat: List<RiwayatItem>, onDelete: (String) -> Unit) {
    val light = !androidx.compose.foundation.isSystemInDarkTheme()
    val headerBg = if (light) Color(0xFFF1F5F9) else MaterialTheme.colorScheme.primaryContainer
    val headerFg = if (light) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    val cellBorder = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (light) Color.White else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (light) Color(0x140F172A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (light) 6.dp else 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.background(headerBg).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    TCellHeader("Tanggal", 150.dp, headerFg)
                    TCellHeader("TWK", 70.dp, headerFg)
                    TCellHeader("TIU", 70.dp, headerFg)
                    TCellHeader("TKP", 70.dp, headerFg)
                    TCellHeader("Total", 80.dp, headerFg)
                    TCellHeader("Status", 90.dp, headerFg)
                    TCellHeader("Aksi", 80.dp, headerFg)
                }
                riwayat.asReversed().forEach { item ->
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        TCell(150.dp, cellBorder) {
                            Text(SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(item.tanggal)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        TCell(70.dp, cellBorder) { Text("${item.twk}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB)) }
                        TCell(70.dp, cellBorder) { Text("${item.tiu}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309)) }
                        TCell(70.dp, cellBorder) { Text("${item.tkp}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDB2777)) }
                        TCell(80.dp, cellBorder) { Text("${item.total}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                        TCell(90.dp, cellBorder) {
                            if (item.lulus) {
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE8F5E9)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("LULUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            } else {
                                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFFEBEE)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("GAGAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                }
                            }
                        }
                        TCell(80.dp, cellBorder) {
                            IconButton(onClick = { onDelete(item.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TCellHeader(text: String, width: Dp, fg: Color) {
    Box(
        Modifier.width(width).fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) { Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg) }
}

@Composable
private fun TCell(width: Dp, border: BorderStroke, content: @Composable () -> Unit) {
    Box(
        Modifier.width(width).fillMaxHeight().border(border).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

@Composable
private fun ScoreField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { nv -> if (nv.all { it.isDigit() } && nv.length <= 3) onValueChange(nv) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SeriesCard(title: String, values: List<Float>, labels: List<String>, maxV: Float, color: Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text("$title (maks ${maxV.toInt()})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            LineChart(values = values, labels = labels, maxValue = maxV, modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }
}
