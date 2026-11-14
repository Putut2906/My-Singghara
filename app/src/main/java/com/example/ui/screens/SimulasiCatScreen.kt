package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

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
    val pembahasan: String
)

data class CatSesi(
    val startTime: Long,
    val endTime: Long,
    val order: List<String>,       // id soal urut sesi (stabil walau di-reload)
    val jawaban: Map<Int, Int>,    // index soal -> index opsi
    val currentIndex: Int,
    val selesai: Boolean
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
    private const val KEY_BANK = "cat_soal_bank"
    private const val KEY_SESI = "cat_sesi_aktif"
    private const val KEY_HASIL = "cat_hasil_terakhir"

    const val DURASI_MS = 100L * 60L * 1000L // 100 menit
    const val TARGET_TWK = 30
    const val TARGET_TIU = 35
    const val TARGET_TKP = 45
    const val TOTAL_SOAL = 110

    // Passing grade SKD (dapat disesuaikan).
    const val PG_TWK = 65
    const val PG_TIU = 80
    const val PG_TKP = 156

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Bank soal ----
    fun loadBank(ctx: Context): MutableList<SoalCat> {
        val raw = prefs(ctx).getString(KEY_BANK, "[]") ?: "[]"
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
                        pembahasan = o.optString("pembahasan", "")
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
    }

    fun saveBank(ctx: Context, list: List<SoalCat>) {
        val arr = JSONArray()
        list.forEach { arr.put(soalToJson(it)) }
        prefs(ctx).edit().putString(KEY_BANK, arr.toString()).apply()
    }

    fun addSoal(ctx: Context, soal: SoalCat) {
        val list = loadBank(ctx)
        list.add(soal)
        saveBank(ctx, list)
    }

    fun deleteSoal(ctx: Context, id: String) {
        val list = loadBank(ctx)
        list.removeAll { it.id == id }
        saveBank(ctx, list)
    }

    fun countByKategori(ctx: Context): Map<String, Int> {
        val map = mutableMapOf(CAT_TWK to 0, CAT_TIU to 0, CAT_TKP to 0)
        loadBank(ctx).forEach { s -> map[s.kategori] = (map[s.kategori] ?: 0) + 1 }
        return map
    }

    // ---- Sesi ----
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
                selesai = o.getBoolean("selesai")
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

    /** Mulai sesi baru: acak 30/35/45 dari bank (seadanya bila kurang), simpan urutan. */
    fun mulaiSesi(ctx: Context): CatSesi? {
        val bank = loadBank(ctx)
        val twk = bank.filter { it.kategori == CAT_TWK }.shuffled().take(TARGET_TWK)
        val tiu = bank.filter { it.kategori == CAT_TIU }.shuffled().take(TARGET_TIU)
        val tkp = bank.filter { it.kategori == CAT_TKP }.shuffled().take(TARGET_TKP)
        val chosen = (twk + tiu + tkp).shuffled()
        if (chosen.isEmpty()) return null
        val now = System.currentTimeMillis()
        val sesi = CatSesi(
            startTime = now,
            endTime = now + DURASI_MS,
            order = chosen.map { it.id },
            jawaban = emptyMap(),
            currentIndex = 0,
            selesai = false
        )
        saveSesi(ctx, sesi)
        return sesi
    }

    // ---- Penilaian ----
    private fun poinSoal(soal: SoalCat, jawab: Int): Int {
        if (jawab < 0) return 0
        return when (soal.kategori) {
            CAT_TKP -> soal.bobotOpsi.getOrElse(jawab) { 0 }
            else -> if (jawab == soal.kunci) 5 else 0
        }
    }

    fun hitungHasil(ctx: Context, sesi: CatSesi): CatHasil {
        val bank = loadBank(ctx).associateBy { it.id }
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

    fun simpanHasil(ctx: Context, hasil: CatHasil) {
        // Simpan ringkas (hanya untuk deteksi ketersediaan; hasil dihitung ulang dari sesi).
        prefs(ctx).edit().putLong(KEY_HASIL, System.currentTimeMillis()).apply()
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
    val tabTitles = listOf("Input Soal", "Simulasi Ujian", "Hasil & Live Score")
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var bank by remember { mutableStateOf(CatStore.loadBank(context)) }
    var sesi by remember { mutableStateOf(CatStore.loadSesi(context)) }
    var hasil by remember { mutableStateOf<CatHasil?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                "Simulasi CAT BKN",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { i, t ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = { Text(t, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> TabInputSoal(bank = bank, onChanged = { bank = CatStore.loadBank(context) })
                1 -> TabSimulasi(
                    sesi = sesi,
                    onSesiChanged = { sesi = CatStore.loadSesi(context) },
                    onFinish = { h -> hasil = h; sesi = CatStore.loadSesi(context); selectedTab = 2 },
                    onGoInput = { selectedTab = 0 }
                )
                else -> TabHasil(
                    hasil = hasil,
                    sesi = sesi,
                    onCompute = { s -> CatStore.hitungHasil(context, s) },
                    onRestart = { selectedTab = 1 }
                )
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
    val counts = remember(bank) { CatStore.countByKategori(context) }

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
                            pembahasan = pembahasan.trim()
                        )
                    )
                    teks = ""
                    opsiList = listOf("", "", "", "")
                    bobot = listOf(5, 4, 3, 2)
                    kunci = 0
                    pembahasan = ""
                    onChanged()
                },
                enabled = teks.isNotBlank() && opsiList.none { it.isBlank() },
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
            adaSesiSelesai = sesi?.selesai == true
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
private fun PetunjukMulai(onStart: () -> Unit, onGoInput: () -> Unit, adaSesiSelesai: Boolean) {
    val context = LocalContext.current
    val counts = remember { CatStore.countByKategori(context) }
    val totalAvailable = (counts[CAT_TWK] ?: 0) + (counts[CAT_TIU] ?: 0) + (counts[CAT_TKP] ?: 0)
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
        Button(onClick = onStart, enabled = totalAvailable > 0, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text("Mulai Ujian")
        }
        Spacer(Modifier.height(24.dp))
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
    val h = hasil ?: sesi?.takeIf { it.selesai }?.let { onCompute(it) }
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

        // Kartu status
        val light = !androidx.compose.foundation.isSystemInDarkTheme()
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (h.lulus) Color(0xFF16A34A) else Color(0xFFDC2626)),
            elevation = CardDefaults.cardElevation(defaultElevation = if (light) 8.dp else 0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL SKOR", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                Text("${h.total} / 550", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(if (h.lulus) "LULUS PASSING GRADE" else "BELUM MEMENUHI PASSING GRADE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Dijawab ${h.jumlahDijawab}/${h.jumlahSoal}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
            }
        }

        // Rekap per kategori
        CatKartu {
            Text("Rincian per Kategori", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            KategoriScore("TWK", h.twk, 150, CatStore.PG_TWK, h.twkLulus)
            Spacer(Modifier.height(6.dp))
            KategoriScore("TIU", h.tiu, 175, CatStore.PG_TIU, h.tiuLulus)
            Spacer(Modifier.height(6.dp))
            KategoriScore("TKP", h.tkp, 225, CatStore.PG_TKP, h.tkpLulus)
        }

        Text("Ulasan & Pembahasan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        h.ulasan.forEach { u -> UlasanCard(u) }
        Spacer(Modifier.height(24.dp))
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
