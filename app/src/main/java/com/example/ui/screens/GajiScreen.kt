package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AnggotaItem
import com.example.data.GajiItem
import com.example.ui.MainViewModel
import com.example.ui.components.CustomDatePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Helper formatting currency IDR
fun formatRupiahGaji(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(amount).replace("Rp", "Rp ")
}

// Preset Standard Salaries based on Status and Peran
object GajiPresets {
    val STATUS_OPTIONS = listOf(
        "Karyawan Tetap",
        "Karyawan Kontrak",
        "Staf",
        "Guru / Dosen",
        "Magang / Trainee",
        "Freelance / Part-time",
        "Outsourcing"
    )

    val PERAN_OPTIONS = listOf(
        "Manager / Pimpinan",
        "Supervisor / Koordinator",
        "Staff IT / Developer",
        "Staff Keuangan / Akuntan",
        "Staff Operasional",
        "Guru / Pengajar",
        "Admin & Personalia",
        "Marketing & Sales",
        "Desainer / Kreatif",
        "Security & Umum"
    )

    val METODE_PEMBAYARAN_OPTIONS = listOf(
        "Transfer Bank",
        "Tunai",
        "E-Wallet (GoPay/OVO/Dana)",
        "Cek / Bilyet Giro"
    )

    val STATUS_BAYAR_OPTIONS = listOf(
        "Lunas",
        "Menunggu",
        "Diproses",
        "Draf"
    )

    data class PresetGaji(
        val gajiPokok: Double,
        val tunjanganJabatan: Double,
        val tunjanganMakanTransport: Double,
        val tunjanganKehadiran: Double,
        val potonganBpjs: Double,
        val potonganPph: Double
    )

    fun getPreset(status: String, peran: String): PresetGaji {
        val multiplier = when (status) {
            "Karyawan Tetap" -> 1.0
            "Karyawan Kontrak" -> 0.85
            "Staf" -> 0.8
            "Guru / Dosen" -> 0.9
            "Magang / Trainee" -> 0.35
            "Freelance / Part-time" -> 0.6
            "Outsourcing" -> 0.7
            else -> 0.8
        }

        val basePokok = when {
            peran.contains("Manager", ignoreCase = true) -> 9000000.0
            peran.contains("Supervisor", ignoreCase = true) -> 6500000.0
            peran.contains("IT", ignoreCase = true) -> 6000000.0
            peran.contains("Keuangan", ignoreCase = true) -> 5200000.0
            peran.contains("Guru", ignoreCase = true) -> 4800000.0
            peran.contains("Marketing", ignoreCase = true) -> 4500000.0
            peran.contains("Desainer", ignoreCase = true) -> 4800000.0
            peran.contains("Admin", ignoreCase = true) -> 4200000.0
            peran.contains("Security", ignoreCase = true) -> 3500000.0
            else -> 4000000.0
        }

        val baseJabatan = when {
            peran.contains("Manager", ignoreCase = true) -> 2500000.0
            peran.contains("Supervisor", ignoreCase = true) -> 1200000.0
            peran.contains("IT", ignoreCase = true) -> 800000.0
            peran.contains("Keuangan", ignoreCase = true) -> 600000.0
            else -> 300000.0
        }

        val calcPokok = (basePokok * multiplier).coerceAtLeast(1500000.0)
        val calcJabatan = (baseJabatan * multiplier).coerceAtLeast(0.0)
        val calcTransport = if (status == "Magang / Trainee") 500000.0 else 800000.0
        val calcKehadiran = if (status == "Magang / Trainee") 300000.0 else 500000.0
        val calcBpjs = if (status == "Karyawan Tetap") (calcPokok * 0.03) else 0.0
        val calcPph = if (calcPokok > 5000000) ((calcPokok - 4500000) * 0.05) else 0.0

        return PresetGaji(
            gajiPokok = calcPokok,
            tunjanganJabatan = calcJabatan,
            tunjanganMakanTransport = calcTransport,
            tunjanganKehadiran = calcKehadiran,
            potonganBpjs = calcBpjs,
            potonganPph = calcPph
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GajiScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allGaji by viewModel.allGaji.collectAsState()
    val allAnggota by viewModel.allAnggota.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Daftar Slip, 1: Input Gaji, 2: Standar Tarif
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("Semua") }
    var selectedFilterPeriode by remember { mutableStateOf("Semua") }

    // Dialog state for viewing/printing digital payslip
    var viewingSlip by remember { mutableStateOf<GajiItem?>(null) }
    var editingGajiId by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<GajiItem?>(null) }

    // Form states for Input / Edit
    var selectedAnggotaId by remember { mutableStateOf<Int?>(null) }
    var inputNomorInduk by remember { mutableStateOf("") }
    var inputNama by remember { mutableStateOf("") }
    var inputStatus by remember { mutableStateOf(GajiPresets.STATUS_OPTIONS[0]) }
    var inputPeran by remember { mutableStateOf(GajiPresets.PERAN_OPTIONS[2]) }
    
    val currentMonthYear = remember {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        sdf.format(Date())
    }
    val todayDateStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }

    var inputPeriode by remember { mutableStateOf(currentMonthYear) }
    var inputTanggalBayar by remember { mutableStateOf(todayDateStr) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Komponen Pendapatan
    var inputGajiPokok by remember { mutableStateOf("5000000") }
    var inputTunjanganJabatan by remember { mutableStateOf("1000000") }
    var inputTunjanganMakanTransport by remember { mutableStateOf("800000") }
    var inputTunjanganKehadiran by remember { mutableStateOf("500000") }
    var inputBonusLembur by remember { mutableStateOf("0") }
    var inputTunjanganLain by remember { mutableStateOf("0") }

    // Komponen Potongan
    var inputPotonganBpjs by remember { mutableStateOf("150000") }
    var inputPotonganPph by remember { mutableStateOf("50000") }
    var inputPotonganKasbon by remember { mutableStateOf("0") }
    var inputPotonganKeterlambatan by remember { mutableStateOf("0") }
    var inputPotonganLain by remember { mutableStateOf("0") }

    var inputMetodePembayaran by remember { mutableStateOf(GajiPresets.METODE_PEMBAYARAN_OPTIONS[0]) }
    var inputStatusPembayaran by remember { mutableStateOf(GajiPresets.STATUS_BAYAR_OPTIONS[0]) }
    var inputCatatan by remember { mutableStateOf("") }

    // Live calculation of totals
    val dPokok = inputGajiPokok.toDoubleOrNull() ?: 0.0
    val dJabatan = inputTunjanganJabatan.toDoubleOrNull() ?: 0.0
    val dMakanTransport = inputTunjanganMakanTransport.toDoubleOrNull() ?: 0.0
    val dKehadiran = inputTunjanganKehadiran.toDoubleOrNull() ?: 0.0
    val dLembur = inputBonusLembur.toDoubleOrNull() ?: 0.0
    val dTunjanganLain = inputTunjanganLain.toDoubleOrNull() ?: 0.0

    val dTotalPendapatan = dPokok + dJabatan + dMakanTransport + dKehadiran + dLembur + dTunjanganLain

    val dBpjs = inputPotonganBpjs.toDoubleOrNull() ?: 0.0
    val dPph = inputPotonganPph.toDoubleOrNull() ?: 0.0
    val dKasbon = inputPotonganKasbon.toDoubleOrNull() ?: 0.0
    val dKeterlambatan = inputPotonganKeterlambatan.toDoubleOrNull() ?: 0.0
    val dPotonganLain = inputPotonganLain.toDoubleOrNull() ?: 0.0

    val dTotalPotongan = dBpjs + dPph + dKasbon + dKeterlambatan + dPotonganLain
    val dGajiBersih = (dTotalPendapatan - dTotalPotongan).coerceAtLeast(0.0)

    // Function to apply preset standards
    fun applyPresetStandard(status: String, peran: String) {
        val preset = GajiPresets.getPreset(status, peran)
        inputGajiPokok = preset.gajiPokok.toLong().toString()
        inputTunjanganJabatan = preset.tunjanganJabatan.toLong().toString()
        inputTunjanganMakanTransport = preset.tunjanganMakanTransport.toLong().toString()
        inputTunjanganKehadiran = preset.tunjanganKehadiran.toLong().toString()
        inputPotonganBpjs = preset.potonganBpjs.toLong().toString()
        inputPotonganPph = preset.potonganPph.toLong().toString()
        viewModel.showToastPopup("Standar gaji untuk $status - $peran diterapkan!")
    }

    // Function to populate form when editing
    fun populateForEdit(item: GajiItem) {
        editingGajiId = item.id
        selectedAnggotaId = if (item.anggotaId > 0) item.anggotaId.toInt() else null
        inputNomorInduk = item.nomorInduk
        inputNama = item.nama
        inputStatus = item.status
        inputPeran = item.peran
        inputPeriode = item.periode
        inputTanggalBayar = item.tanggalBayar
        inputGajiPokok = item.gajiPokok.toLong().toString()
        inputTunjanganJabatan = item.tunjanganJabatan.toLong().toString()
        inputTunjanganMakanTransport = item.tunjanganMakanTransport.toLong().toString()
        inputTunjanganKehadiran = item.tunjanganKehadiran.toLong().toString()
        inputBonusLembur = item.bonusLembur.toLong().toString()
        inputTunjanganLain = item.tunjanganLain.toLong().toString()
        inputPotonganBpjs = item.potonganBpjs.toLong().toString()
        inputPotonganPph = item.potonganPph.toLong().toString()
        inputPotonganKasbon = item.potonganKasbon.toLong().toString()
        inputPotonganKeterlambatan = item.potonganKeterlambatan.toLong().toString()
        inputPotonganLain = item.potonganLain.toLong().toString()
        inputMetodePembayaran = item.metodePembayaran
        inputStatusPembayaran = item.statusPembayaran
        inputCatatan = item.catatan
        activeTab = 1 // Switch to input tab
    }

    fun resetForm() {
        editingGajiId = null
        selectedAnggotaId = null
        inputNomorInduk = ""
        inputNama = ""
        inputStatus = GajiPresets.STATUS_OPTIONS[0]
        inputPeran = GajiPresets.PERAN_OPTIONS[2]
        inputPeriode = currentMonthYear
        inputTanggalBayar = todayDateStr
        applyPresetStandard(GajiPresets.STATUS_OPTIONS[0], GajiPresets.PERAN_OPTIONS[2])
        inputBonusLembur = "0"
        inputTunjanganLain = "0"
        inputPotonganKasbon = "0"
        inputPotonganKeterlambatan = "0"
        inputPotonganLain = "0"
        inputMetodePembayaran = GajiPresets.METODE_PEMBAYARAN_OPTIONS[0]
        inputStatusPembayaran = GajiPresets.STATUS_BAYAR_OPTIONS[0]
        inputCatatan = ""
    }

    // Filtered gaji list
    val filteredGajiList = remember(allGaji, searchQuery, selectedFilterStatus, selectedFilterPeriode) {
        allGaji.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                    item.nama.contains(searchQuery, ignoreCase = true) ||
                    item.nomorInduk.contains(searchQuery, ignoreCase = true) ||
                    item.peran.contains(searchQuery, ignoreCase = true)
            val matchStatus = selectedFilterStatus == "Semua" || item.status.equals(selectedFilterStatus, ignoreCase = true)
            val matchPeriode = selectedFilterPeriode == "Semua" || item.periode.equals(selectedFilterPeriode, ignoreCase = true)
            matchQuery && matchStatus && matchPeriode
        }
    }

    // Overall summary metrics
    val totalPengeluaranGaji = remember(allGaji) { allGaji.sumOf { it.gajiBersih } }
    val totalLunas = remember(allGaji) { allGaji.count { it.statusPembayaran.equals("Lunas", ignoreCase = true) } }
    val totalMenunggu = remember(allGaji) { allGaji.count { !it.statusPembayaran.equals("Lunas", ignoreCase = true) } }
    val avgGaji = remember(allGaji) { if (allGaji.isNotEmpty()) totalPengeluaranGaji / allGaji.size else 0.0 }

    // List of unique periodes for filtering
    val uniquePeriodes = remember(allGaji) {
        listOf("Semua") + allGaji.map { it.periode }.filter { it.isNotBlank() }.distinct()
    }

    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (activeTab == 0) {
                FloatingActionButton(
                    onClick = {
                        resetForm()
                        activeTab = 1
                    },
                    containerColor = Color(0xFF0F766E),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("gaji_fab_input")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Input Gaji")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Input Gaji", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (activeTab) {
                0 -> {
                    // TAB 0: DAFTAR SLIP GAJI
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Overview Stats Cards
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Total Pengeluaran Gaji",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = formatRupiahGaji(totalPengeluaranGaji),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F766E)
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Icon titik 3 di sebelah kiri title "icon + input gaji"
                                            Box {
                                                IconButton(
                                                    onClick = { showOverflowMenu = true },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .testTag("gaji_overflow_menu_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Menu Opsi Gaji",
                                                        tint = Color(0xFF0F766E)
                                                    )
                                                }

                                                GajiOverflowDropdown(
                                                    expanded = showOverflowMenu,
                                                    onDismissRequest = { showOverflowMenu = false },
                                                    activeTab = activeTab,
                                                    onSelectTab = {
                                                        activeTab = it
                                                        if (it == 1) resetForm()
                                                    },
                                                    allGaji = allGaji,
                                                    scope = scope,
                                                    viewModel = viewModel,
                                                    onOpenDrawer = onOpenDrawer
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    resetForm()
                                                    activeTab = 1
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.testTag("gaji_tambah_cepat_btn")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Input Gaji", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFF0F766E).copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Total Slip", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${allGaji.size} Dokumen", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Column {
                                            Text("Lunas / Dibayar", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            Text("$totalLunas Slip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                                        }
                                        Column {
                                            Text("Menunggu", fontSize = 11.sp, color = Color(0xFFE65100))
                                            Text("$totalMenunggu Slip", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE65100))
                                        }
                                        Column {
                                            Text("Rata-rata", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatRupiahGaji(avgGaji), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        // Search and Filter Controls
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gaji_search_input"),
                                placeholder = { Text("Cari nama, NIP, atau peran...", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "Cari", tint = Color(0xFF0F766E))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0F766E),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }

                        // Status Filter Chips
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Filter Berdasarkan Status:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val statusOptionsWithAll = listOf("Semua") + GajiPresets.STATUS_OPTIONS
                                    statusOptionsWithAll.forEach { st ->
                                        val isSelected = selectedFilterStatus == st
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedFilterStatus = st },
                                            label = { Text(st, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0F766E),
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Filter Periode
                        if (uniquePeriodes.size > 2) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Periode:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    uniquePeriodes.forEach { per ->
                                        val isSelected = selectedFilterPeriode == per
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedFilterPeriode = per },
                                            label = { Text(per, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Empty State
                        if (filteredGajiList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payments,
                                            contentDescription = null,
                                            tint = Color(0xFF0F766E).copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (allGaji.isEmpty()) "Belum ada data slip gaji tersimpan" else "Tidak ada slip gaji yang sesuai filter",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Klik tab 'Input Gaji' untuk mulai membuat slip gaji berdasarkan status & peran karyawan.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                resetForm()
                                                activeTab = 1
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Input Gaji Baru Sekarang")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Gaji List Items
                            items(filteredGajiList, key = { it.id }) { item ->
                                GajiItemCard(
                                    item = item,
                                    onViewSlip = { viewingSlip = item },
                                    onEdit = { populateForEdit(item) },
                                    onDelete = { showDeleteConfirmDialog = item },
                                    onShare = {
                                        val shareText = """
                                            📋 *SLIP GAJI - MY SINGGHARA COMPANY*
                                            ─────────────────────────────
                                            Nama        : ${item.nama}
                                            NIP / ID    : ${item.nomorInduk.ifBlank { "-" }}
                                            Status      : ${item.status}
                                            Peran       : ${item.peran}
                                            Periode     : ${item.periode}
                                            Tanggal     : ${item.tanggalBayar}
                                            ─────────────────────────────
                                            (+) Gaji Pokok      : ${formatRupiahGaji(item.gajiPokok)}
                                            (+) Tunj. Jabatan   : ${formatRupiahGaji(item.tunjanganJabatan)}
                                            (+) Tunj. Transport : ${formatRupiahGaji(item.tunjanganMakanTransport)}
                                            (+) Tunj. Kehadiran : ${formatRupiahGaji(item.tunjanganKehadiran)}
                                            (+) Bonus / Lembur  : ${formatRupiahGaji(item.bonusLembur)}
                                            -----------------------------
                                            *Total Penerimaan*  : ${formatRupiahGaji(item.totalPendapatan)}
                                            
                                            (-) BPJS            : ${formatRupiahGaji(item.potonganBpjs)}
                                            (-) PPh 21          : ${formatRupiahGaji(item.potonganPph)}
                                            (-) Kasbon / Pinjam : ${formatRupiahGaji(item.potonganKasbon)}
                                            (-) Potongan Lain   : ${formatRupiahGaji(item.potonganLain + item.potonganKeterlambatan)}
                                            -----------------------------
                                            *Total Potongan*    : ${formatRupiahGaji(item.totalPotongan)}
                                            ─────────────────────────────
                                            💰 *GAJI BERSIH (THP)*: *${formatRupiahGaji(item.gajiBersih)}*
                                            Status: ${item.statusPembayaran.uppercase()} (${item.metodePembayaran})
                                        """.trimIndent()

                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Bagikan Slip Gaji")
                                        context.startActivity(shareIntent)
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: FORM INPUT GAJI BERDASARKAN STATUS & PERAN
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F766E).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon titik 3 ke sebelah kiri title "icon + input gaji"
                                Box {
                                    IconButton(
                                        onClick = { showOverflowMenu = true },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("gaji_overflow_menu_button_tab1")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu Opsi Gaji",
                                            tint = Color(0xFF0F766E)
                                        )
                                    }

                                    GajiOverflowDropdown(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false },
                                        activeTab = activeTab,
                                        onSelectTab = {
                                            activeTab = it
                                            if (it == 1) resetForm()
                                        },
                                        allGaji = allGaji,
                                        scope = scope,
                                        viewModel = viewModel,
                                        onOpenDrawer = onOpenDrawer
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Icon(
                                    imageVector = if (editingGajiId != null) Icons.Default.EditNote else Icons.Default.PostAdd,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (editingGajiId != null) "Edit Slip Gaji #${editingGajiId}" else "Input Gaji",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F766E)
                                    )
                                    Text(
                                        text = "Kompensasi otomatis disesuaikan dengan Status Kepegawaian dan Peran/Jabatan.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 1. DATA PENERIMA GAJI
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("1. Identitas Karyawan / Anggota", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                // Quick select from registered Anggota
                                if (allAnggota.isNotEmpty()) {
                                    Text("Pilih Cepat dari Data Anggota Terdaftar:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allAnggota.forEach { a ->
                                            val isSelected = selectedAnggotaId == a.id
                                            AssistChip(
                                                onClick = {
                                                    selectedAnggotaId = a.id
                                                    inputNomorInduk = a.nomorInduk
                                                    inputNama = a.nama
                                                    // Map status if matches
                                                    if (GajiPresets.STATUS_OPTIONS.any { it.contains(a.status, ignoreCase = true) }) {
                                                        inputStatus = GajiPresets.STATUS_OPTIONS.firstOrNull { it.contains(a.status, ignoreCase = true) } ?: inputStatus
                                                    }
                                                    // Map peran from divisi if matches
                                                    if (a.divisiOrJurusan.isNotBlank() && GajiPresets.PERAN_OPTIONS.any { it.contains(a.divisiOrJurusan, ignoreCase = true) }) {
                                                        inputPeran = GajiPresets.PERAN_OPTIONS.firstOrNull { it.contains(a.divisiOrJurusan, ignoreCase = true) } ?: inputPeran
                                                    }
                                                    // Apply standard automatically
                                                    applyPresetStandard(inputStatus, inputPeran)
                                                },
                                                label = { Text("${a.nama} (${a.status})", fontSize = 11.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.PersonOutline,
                                                        contentDescription = null,
                                                        tint = if (isSelected) Color(0xFF0F766E) else Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = if (isSelected) Color(0xFF0F766E).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                )
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = inputNama,
                                        onValueChange = { inputNama = it },
                                        label = { Text("Nama Lengkap *", fontSize = 12.sp) },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("gaji_input_nama"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputNomorInduk,
                                        onValueChange = { inputNomorInduk = it },
                                        label = { Text("NIP / NIM / ID", fontSize = 12.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("gaji_input_nip"),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                // Status Kepegawaian Dropdown Selection
                                var expandedStatus by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expandedStatus,
                                    onExpandedChange = { expandedStatus = !expandedStatus }
                                ) {
                                    OutlinedTextField(
                                        value = inputStatus,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Status Kepegawaian *", fontSize = 12.sp) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatus) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                            .testTag("gaji_select_status"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedStatus,
                                        onDismissRequest = { expandedStatus = false }
                                    ) {
                                        GajiPresets.STATUS_OPTIONS.forEach { st ->
                                            DropdownMenuItem(
                                                text = { Text(st, fontSize = 13.sp) },
                                                onClick = {
                                                    inputStatus = st
                                                    expandedStatus = false
                                                    applyPresetStandard(inputStatus, inputPeran)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Peran / Jabatan Dropdown Selection
                                var expandedPeran by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = expandedPeran,
                                    onExpandedChange = { expandedPeran = !expandedPeran }
                                ) {
                                    OutlinedTextField(
                                        value = inputPeran,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Peran / Jabatan / Posisi *", fontSize = 12.sp) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPeran) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                            .testTag("gaji_select_peran"),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expandedPeran,
                                        onDismissRequest = { expandedPeran = false }
                                    ) {
                                        GajiPresets.PERAN_OPTIONS.forEach { pr ->
                                            DropdownMenuItem(
                                                text = { Text(pr, fontSize = 13.sp) },
                                                onClick = {
                                                    inputPeran = pr
                                                    expandedPeran = false
                                                    applyPresetStandard(inputStatus, inputPeran)
                                                }
                                            )
                                        }
                                    }
                                }

                                // Auto Apply Standard Button
                                OutlinedButton(
                                    onClick = { applyPresetStandard(inputStatus, inputPeran) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E))
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Terapkan Rekomendasi Standar ($inputStatus - $inputPeran)", fontSize = 11.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = inputPeriode,
                                        onValueChange = { inputPeriode = it },
                                        label = { Text("Periode Gaji (Bulan/Tahun) *", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputTanggalBayar,
                                        onValueChange = { inputTanggalBayar = it },
                                        label = { Text("Tanggal Bayar *", fontSize = 12.sp) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { showDatePicker = true },
                                        readOnly = true,
                                        trailingIcon = {
                                            IconButton(onClick = { showDatePicker = true }) {
                                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal")
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        // 2. KOMPONEN PENDAPATAN (+)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("2. Komponen Penerimaan / Gaji (+)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                    }
                                    Text(
                                        text = formatRupiahGaji(dTotalPendapatan),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputGajiPokok,
                                        onValueChange = { inputGajiPokok = it.filter { c -> c.isDigit() } },
                                        label = { Text("Gaji Pokok (Rp) *", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputTunjanganJabatan,
                                        onValueChange = { inputTunjanganJabatan = it.filter { c -> c.isDigit() } },
                                        label = { Text("Tunj. Jabatan / Peran (Rp)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputTunjanganMakanTransport,
                                        onValueChange = { inputTunjanganMakanTransport = it.filter { c -> c.isDigit() } },
                                        label = { Text("Tunj. Makan & Transport", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputTunjanganKehadiran,
                                        onValueChange = { inputTunjanganKehadiran = it.filter { c -> c.isDigit() } },
                                        label = { Text("Tunj. Kehadiran / Disiplin", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputBonusLembur,
                                        onValueChange = { inputBonusLembur = it.filter { c -> c.isDigit() } },
                                        label = { Text("Bonus Lembur / Insentif", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputTunjanganLain,
                                        onValueChange = { inputTunjanganLain = it.filter { c -> c.isDigit() } },
                                        label = { Text("Tunjangan Lainnya", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }

                        // 3. KOMPONEN POTONGAN (-)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("3. Komponen Potongan (-)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFC62828))
                                    }
                                    Text(
                                        text = formatRupiahGaji(dTotalPotongan),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputPotonganBpjs,
                                        onValueChange = { inputPotonganBpjs = it.filter { c -> c.isDigit() } },
                                        label = { Text("BPJS Kes & Ketenagakerjaan", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputPotonganPph,
                                        onValueChange = { inputPotonganPph = it.filter { c -> c.isDigit() } },
                                        label = { Text("Pajak PPh 21 (Rp)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = inputPotonganKasbon,
                                        onValueChange = { inputPotonganKasbon = it.filter { c -> c.isDigit() } },
                                        label = { Text("Kasbon / Pinjaman", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = inputPotonganKeterlambatan,
                                        onValueChange = { inputPotonganKeterlambatan = it.filter { c -> c.isDigit() } },
                                        label = { Text("Potongan Telat / Absen", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                OutlinedTextField(
                                    value = inputPotonganLain,
                                    onValueChange = { inputPotonganLain = it.filter { c -> c.isDigit() } },
                                    label = { Text("Potongan Lainnya (Rp)", fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        // 4. METODE, STATUS & CATATAN
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("4. Metode & Status Pembayaran", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var expMetode by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expMetode,
                                        onExpandedChange = { expMetode = !expMetode },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = inputMetodePembayaran,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Metode Bayar", fontSize = 12.sp) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expMetode) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expMetode,
                                            onDismissRequest = { expMetode = false }
                                        ) {
                                            GajiPresets.METODE_PEMBAYARAN_OPTIONS.forEach { m ->
                                                DropdownMenuItem(
                                                    text = { Text(m, fontSize = 12.sp) },
                                                    onClick = {
                                                        inputMetodePembayaran = m
                                                        expMetode = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    var expStatusBayar by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expStatusBayar,
                                        onExpandedChange = { expStatusBayar = !expStatusBayar },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = inputStatusPembayaran,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Status Bayar", fontSize = 12.sp) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expStatusBayar) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expStatusBayar,
                                            onDismissRequest = { expStatusBayar = false }
                                        ) {
                                            GajiPresets.STATUS_BAYAR_OPTIONS.forEach { sb ->
                                                DropdownMenuItem(
                                                    text = { Text(sb, fontSize = 12.sp) },
                                                    onClick = {
                                                        inputStatusPembayaran = sb
                                                        expStatusBayar = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = inputCatatan,
                                    onValueChange = { inputCatatan = it },
                                    label = { Text("Catatan / Keterangan Slip (Opsional)", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        // 5. SUMMARY REAL-TIME TAKE HOME PAY & SAVE BUTTON
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Pendapatan (+):", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                    Text(formatRupiahGaji(dTotalPendapatan), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Potongan (-):", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                    Text(formatRupiahGaji(dTotalPotongan), color = Color(0xFFFFCDD2), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("GAJI BERSIH (TAKE HOME PAY)", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(formatRupiahGaji(dGajiBersih), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Surface(
                                        color = if (inputStatusPembayaran == "Lunas") Color(0xFF2E7D32) else Color(0xFFE65100),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = inputStatusPembayaran.uppercase(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { resetForm() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                                    ) {
                                        Text("Reset Form", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (inputNama.isBlank()) {
                                                viewModel.showToastPopup("Mohon isi Nama Karyawan!")
                                                return@Button
                                            }

                                            val newGaji = GajiItem(
                                                id = editingGajiId ?: 0,
                                                anggotaId = (selectedAnggotaId ?: 0).toLong(),
                                                nomorInduk = inputNomorInduk.trim(),
                                                nama = inputNama.trim(),
                                                status = inputStatus,
                                                peran = inputPeran,
                                                periode = inputPeriode.trim(),
                                                tanggalBayar = inputTanggalBayar.trim(),
                                                gajiPokok = dPokok,
                                                tunjanganJabatan = dJabatan,
                                                tunjanganMakanTransport = dMakanTransport,
                                                tunjanganKehadiran = dKehadiran,
                                                bonusLembur = dLembur,
                                                tunjanganLain = dTunjanganLain,
                                                potonganBpjs = dBpjs,
                                                potonganPph = dPph,
                                                potonganKasbon = dKasbon,
                                                potonganKeterlambatan = dKeterlambatan,
                                                potonganLain = dPotonganLain,
                                                totalPendapatan = dTotalPendapatan,
                                                totalPotongan = dTotalPotongan,
                                                gajiBersih = dGajiBersih,
                                                metodePembayaran = inputMetodePembayaran,
                                                statusPembayaran = inputStatusPembayaran,
                                                catatan = inputCatatan.trim()
                                            )

                                            if (editingGajiId != null) {
                                                viewModel.updateGaji(newGaji) {
                                                    viewModel.showToastPopup("Slip gaji berhasil diperbarui!")
                                                    resetForm()
                                                    activeTab = 0
                                                }
                                            } else {
                                                viewModel.saveGaji(newGaji) {
                                                    viewModel.showToastPopup("Slip gaji berhasil disimpan!")
                                                    resetForm()
                                                    activeTab = 0
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("gaji_save_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color(0xFF0F766E)
                                        )
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (editingGajiId != null) "Perbarui Slip" else "Simpan Slip Gaji", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                2 -> {
                    // TAB 2: STANDAR & TARIF GAJI
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Matriks Standar Kompensasi & Gaji", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F766E))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tabel panduan acuan standar gaji pokok, tunjangan jabatan, dan fasilitas berdasarkan status kepegawaian serta peran di My Singghara Company.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Iterate through status categories
                        GajiPresets.STATUS_OPTIONS.forEach { status ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(status, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F766E))
                                            Surface(
                                                color = Color(0xFF0F766E).copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("Kategori", fontSize = 10.sp, color = Color(0xFF0F766E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        // Sample roles with preset
                                        val sampleRoles = listOf("Manager / Pimpinan", "Staff IT / Developer", "Staff Keuangan / Akuntan", "Staff Operasional")
                                        sampleRoles.forEach { role ->
                                            val pr = GajiPresets.getPreset(status, role)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(role, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text(
                                                    "Pokok: ${formatRupiahGaji(pr.gajiPokok)} | Jab: ${formatRupiahGaji(pr.tunjanganJabatan)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF0F766E)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        CustomDatePickerDialog(
            initialDateStr = inputTanggalBayar,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                inputTanggalBayar = selectedDate
                showDatePicker = false
            }
        )
    }

    // Digital Payslip Detail Dialog
    viewingSlip?.let { slip ->
        Dialog(
            onDismissRequest = { viewingSlip = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header of Payslip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SLIP GAJI RESMI", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F766E))
                        }
                        IconButton(onClick = { viewingSlip = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }

                    HorizontalDivider(color = Color(0xFF0F766E).copy(alpha = 0.3f), thickness = 1.5.dp)

                    // Scrollable content of payslip
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Company Stamp & Identity
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F766E).copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("MY SINGGHARA COMPANY", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF0F766E))
                                Text("Sistem Penggajian & HR Terpadu", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Periode: ${slip.periode}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Tgl Bayar: ${slip.tanggalBayar}", fontSize = 11.sp)
                                }
                            }
                        }

                        // Employee Info
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Nama Karyawan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slip.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("NIP / ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slip.nomorInduk.ifBlank { "-" }, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Status Kepegawaian", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slip.status, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F766E))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Peran / Jabatan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(slip.peran, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Penerimaan Table
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("PENERIMAAN (EARNINGS)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                                HorizontalDivider(color = Color(0xFF2E7D32).copy(alpha = 0.15f))
                                SlipRowItem("Gaji Pokok", slip.gajiPokok)
                                if (slip.tunjanganJabatan > 0) SlipRowItem("Tunjangan Jabatan", slip.tunjanganJabatan)
                                if (slip.tunjanganMakanTransport > 0) SlipRowItem("Tunjangan Makan & Transport", slip.tunjanganMakanTransport)
                                if (slip.tunjanganKehadiran > 0) SlipRowItem("Tunjangan Kehadiran", slip.tunjanganKehadiran)
                                if (slip.bonusLembur > 0) SlipRowItem("Bonus Lembur / Insentif", slip.bonusLembur)
                                if (slip.tunjanganLain > 0) SlipRowItem("Tunjangan Lainnya", slip.tunjanganLain)
                                HorizontalDivider(color = Color(0xFF2E7D32).copy(alpha = 0.2f))
                                SlipRowItem("Total Penerimaan", slip.totalPendapatan, isBold = true, textColor = Color(0xFF2E7D32))
                            }
                        }

                        // Potongan Table
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFC62828).copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color(0xFFC62828).copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("POTONGAN (DEDUCTIONS)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFC62828))
                                HorizontalDivider(color = Color(0xFFC62828).copy(alpha = 0.15f))
                                if (slip.potonganBpjs > 0) SlipRowItem("BPJS Kesehatan & Ketenagakerjaan", slip.potonganBpjs)
                                if (slip.potonganPph > 0) SlipRowItem("Pajak PPh 21", slip.potonganPph)
                                if (slip.potonganKasbon > 0) SlipRowItem("Kasbon / Pinjaman", slip.potonganKasbon)
                                if (slip.potonganKeterlambatan > 0) SlipRowItem("Potongan Keterlambatan / Absen", slip.potonganKeterlambatan)
                                if (slip.potonganLain > 0) SlipRowItem("Potongan Lainnya", slip.potonganLain)
                                HorizontalDivider(color = Color(0xFFC62828).copy(alpha = 0.2f))
                                SlipRowItem("Total Potongan", slip.totalPotongan, isBold = true, textColor = Color(0xFFC62828))
                            }
                        }

                        // Take Home Pay Banner
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F766E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GAJI BERSIH (TAKE HOME PAY)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(formatRupiahGaji(slip.gajiBersih), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Metode: ${slip.metodePembayaran}", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                                    Text("Status: ${slip.statusPembayaran.uppercase()}", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (slip.catatan.isNotBlank()) {
                            Text(
                                text = "Catatan: ${slip.catatan}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }

                    // Bottom action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Slip Gaji", "Slip Gaji ${slip.nama} Periode ${slip.periode}: ${formatRupiahGaji(slip.gajiBersih)}")
                                clipboard.setPrimaryClip(clip)
                                viewModel.showToastPopup("Rincian slip disalin ke clipboard!")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val shareText = """
                                    📋 *SLIP GAJI - MY SINGGHARA COMPANY*
                                    ─────────────────────────────
                                    Nama        : ${slip.nama}
                                    NIP / ID    : ${slip.nomorInduk.ifBlank { "-" }}
                                    Status      : ${slip.status}
                                    Peran       : ${slip.peran}
                                    Periode     : ${slip.periode}
                                    Tanggal     : ${slip.tanggalBayar}
                                    ─────────────────────────────
                                    (+) Gaji Pokok      : ${formatRupiahGaji(slip.gajiPokok)}
                                    (+) Tunj. Jabatan   : ${formatRupiahGaji(slip.tunjanganJabatan)}
                                    (+) Tunj. Transport : ${formatRupiahGaji(slip.tunjanganMakanTransport)}
                                    (+) Tunj. Kehadiran : ${formatRupiahGaji(slip.tunjanganKehadiran)}
                                    (+) Bonus / Lembur  : ${formatRupiahGaji(slip.bonusLembur)}
                                    -----------------------------
                                    *Total Penerimaan*  : ${formatRupiahGaji(slip.totalPendapatan)}
                                    
                                    (-) BPJS            : ${formatRupiahGaji(slip.potonganBpjs)}
                                    (-) PPh 21          : ${formatRupiahGaji(slip.potonganPph)}
                                    (-) Kasbon / Pinjam : ${formatRupiahGaji(slip.potonganKasbon)}
                                    (-) Potongan Lain   : ${formatRupiahGaji(slip.potonganLain + slip.potonganKeterlambatan)}
                                    -----------------------------
                                    *Total Potongan*    : ${formatRupiahGaji(slip.totalPotongan)}
                                    ─────────────────────────────
                                    💰 *GAJI BERSIH (THP)*: *${formatRupiahGaji(slip.gajiBersih)}*
                                    Status: ${slip.statusPembayaran.uppercase()} (${slip.metodePembayaran})
                                """.trimIndent()

                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Kirim Slip Gaji")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bagikan Slip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Hapus Slip Gaji?") },
            text = { Text("Yakin ingin menghapus slip gaji ${target.nama} periode ${target.periode} (${formatRupiahGaji(target.gajiBersih)})?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGaji(target)
                        showDeleteConfirmDialog = null
                        viewModel.showToastPopup("Slip gaji berhasil dihapus")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun SlipRowItem(label: String, amount: Double, isBold: Boolean = false, textColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = textColor ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = formatRupiahGaji(amount),
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = textColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun GajiItemCard(
    item: GajiItem,
    onViewSlip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewSlip() }
            .testTag("gaji_item_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Initial Avatar
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = Color(0xFF0F766E).copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.nama.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.nama,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = Color(0xFF0F766E).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    fontSize = 10.sp,
                                    color = Color(0xFF0F766E),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "• ${item.peran}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Status Badge
                Surface(
                    color = if (item.statusPembayaran.equals("Lunas", ignoreCase = true)) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color(0xFFE65100).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.statusPembayaran.uppercase(),
                        color = if (item.statusPembayaran.equals("Lunas", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Take Home Pay (${item.periode})",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRupiahGaji(item.gajiBersih),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F766E)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF0F766E), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GajiOverflowDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    allGaji: List<GajiItem>,
    scope: CoroutineScope,
    viewModel: MainViewModel,
    onOpenDrawer: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (activeTab == 0) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Daftar Slip (${allGaji.size})",
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeTab == 0) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            onClick = {
                onSelectTab(0)
                onDismissRequest()
            }
        )

        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (activeTab == 1) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Input Gaji",
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeTab == 1) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            onClick = {
                onSelectTab(1)
                onDismissRequest()
            }
        )

        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (activeTab == 2) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Standar Tarif",
                        fontWeight = if (activeTab == 2) FontWeight.Bold else FontWeight.Normal,
                        color = if (activeTab == 2) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            onClick = {
                onSelectTab(2)
                onDismissRequest()
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Ekspor Rekap Gaji (CSV)")
                }
            },
            onClick = {
                onDismissRequest()
                scope.launch(Dispatchers.IO) {
                    try {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (downloadDir != null && !downloadDir.exists()) downloadDir.mkdirs()
                        val csvFile = File(downloadDir, "rekap_gaji_${System.currentTimeMillis()}.csv")

                        val sb = StringBuilder()
                        sb.append("ID,Nomor Induk,Nama,Status,Peran,Periode,Tanggal Bayar,Gaji Pokok,Tunjangan Jabatan,Tunjangan Makan Transport,Tunjangan Kehadiran,Bonus Lembur,Tunjangan Lain,Potongan BPJS,Potongan PPh,Potongan Kasbon,Potongan Keterlambatan,Potongan Lain,Total Pendapatan,Total Potongan,Gaji Bersih,Metode Pembayaran,Status Pembayaran,Catatan\n")

                        allGaji.forEach { g ->
                            sb.append("${g.id},\"${g.nomorInduk}\",\"${g.nama}\",\"${g.status}\",\"${g.peran}\",\"${g.periode}\",\"${g.tanggalBayar}\",${g.gajiPokok},${g.tunjanganJabatan},${g.tunjanganMakanTransport},${g.tunjanganKehadiran},${g.bonusLembur},${g.tunjanganLain},${g.potonganBpjs},${g.potonganPph},${g.potonganKasbon},${g.potonganKeterlambatan},${g.potonganLain},${g.totalPendapatan},${g.totalPotongan},${g.gajiBersih},\"${g.metodePembayaran}\",\"${g.statusPembayaran}\",\"${g.catatan}\"\n")
                        }

                        csvFile.writeText(sb.toString(), Charsets.UTF_8)
                        withContext(Dispatchers.Main) {
                            viewModel.showToastPopup("Rekap gaji berhasil diekspor ke Download/${csvFile.name}")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            viewModel.showToastPopup("Gagal ekspor CSV: ${e.message}")
                        }
                    }
                }
            }
        )

        if (onOpenDrawer != null) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Buka Menu Utama")
                    }
                },
                onClick = {
                    onDismissRequest()
                    onOpenDrawer()
                }
            )
        }
    }
}
