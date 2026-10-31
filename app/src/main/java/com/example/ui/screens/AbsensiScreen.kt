package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.AnggotaItem
import com.example.data.RiwayatPresensi
import com.example.ui.MainViewModel
import com.example.util.AbsensiAudioType
import com.example.util.AbsensiSoundHelper
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsensiScreen(
    viewModel: MainViewModel,
    isFullPage: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onOpenDrawer: (() -> Unit)? = null,
    onOpenFullHistory: (() -> Unit)? = null
) {
    // Handle Android OS system back button
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences("mysingghara_absensi_prefs", Context.MODE_PRIVATE)
    }

    // Function to play sound and TTS based on attendance event
    val playAbsensiFeedback: (RiwayatPresensi, Boolean) -> Unit = remember(context) {
        { presensi, isPulang ->
            val soundType = when {
                isPulang -> {
                    if (presensi.statusPelanggaran.contains("Awal", ignoreCase = true) ||
                        presensi.statusPelanggaran.contains("Cepat", ignoreCase = true)) {
                        AbsensiAudioType.PULANG_AWAL
                    } else {
                        AbsensiAudioType.PULANG_TEPAT
                    }
                }
                presensi.statusPelanggaran.contains("Terlambat", ignoreCase = true) ||
                presensi.statusPresensi.equals("Terlambat", ignoreCase = true) -> {
                    AbsensiAudioType.MASUK_TERLAMBAT
                }
                presensi.statusPresensi.equals("Izin", ignoreCase = true) ||
                presensi.statusPresensi.equals("Sakit", ignoreCase = true) -> {
                    AbsensiAudioType.IZIN_SAKIT
                }
                else -> {
                    AbsensiAudioType.MASUK_TEPAT
                }
            }
            AbsensiSoundHelper.playAbsensiSound(context, soundType, presensi.nama)
        }
    }

    // Default thresholds stored in preferences
    var defaultBatasMasuk by remember {
        mutableStateOf(prefs.getString("batas_waktu_masuk", "08:00") ?: "08:00")
    }
    var defaultBatasPulang by remember {
        mutableStateOf(prefs.getString("batas_waktu_pulang", "17:00") ?: "17:00")
    }

    val allPresensi by viewModel.allPresensi.collectAsState()
    val allAnggota by viewModel.allAnggota.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("Semua") }
    var selectedDateFilter by remember { mutableStateOf("Semua Tanggal") }

    // Dialog state controllers
    var showScannerDialog by remember { mutableStateOf(false) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    var recordingPulangItem by remember { mutableStateOf<RiwayatPresensi?>(null) }
    var deletingPresensi by remember { mutableStateOf<RiwayatPresensi?>(null) }
    var viewingDetailPresensi by remember { mutableStateOf<RiwayatPresensi?>(null) }

    // Violation warning popup state
    var violationPopupData by remember { mutableStateOf<PelanggaranWarningData?>(null) }

    // Date calculations
    val todayStart = remember {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    }

    val totalCount = allPresensi.size
    val hadirCount = remember(allPresensi) { allPresensi.count { it.statusPresensi.equals("Hadir", ignoreCase = true) } }
    val izinCount = remember(allPresensi) { allPresensi.count { it.statusPresensi.equals("Izin", ignoreCase = true) } }
    val sakitCount = remember(allPresensi) { allPresensi.count { it.statusPresensi.equals("Sakit", ignoreCase = true) } }
    val terlambatCount = remember(allPresensi) {
        allPresensi.count {
            it.statusPresensi.equals("Terlambat", ignoreCase = true) ||
            it.statusPelanggaran.contains("Terlambat", ignoreCase = true)
        }
    }
    val pelanggaranCount = remember(allPresensi) {
        allPresensi.count {
            !it.statusPelanggaran.equals("Tepat Waktu", ignoreCase = true) &&
            it.statusPelanggaran.isNotBlank()
        }
    }

    val filteredPresensi = remember(allPresensi, searchQuery, selectedStatusFilter, selectedDateFilter) {
        allPresensi.filter { item ->
            val matchSearch = searchQuery.isBlank() ||
                    item.nama.contains(searchQuery, ignoreCase = true) ||
                    item.nomorInduk.contains(searchQuery, ignoreCase = true) ||
                    item.divisi.contains(searchQuery, ignoreCase = true) ||
                    item.status.contains(searchQuery, ignoreCase = true) ||
                    item.statusPelanggaran.contains(searchQuery, ignoreCase = true) ||
                    item.catatanPelanggaran.contains(searchQuery, ignoreCase = true) ||
                    item.metode.contains(searchQuery, ignoreCase = true)

            val matchStatus = when (selectedStatusFilter) {
                "Semua" -> true
                "Pelanggaran" -> !item.statusPelanggaran.equals("Tepat Waktu", ignoreCase = true) && item.statusPelanggaran.isNotBlank()
                "Tepat Waktu" -> item.statusPelanggaran.equals("Tepat Waktu", ignoreCase = true)
                else -> item.statusPresensi.equals(selectedStatusFilter, ignoreCase = true)
            }

            val matchDate = when (selectedDateFilter) {
                "Hari Ini" -> item.timestamp >= todayStart
                "7 Hari Terakhir" -> item.timestamp >= (todayStart - 6 * 24 * 60 * 60 * 1000L)
                "Bulan Ini" -> {
                    val c = Calendar.getInstance().apply { timeInMillis = item.timestamp }
                    val now = Calendar.getInstance()
                    c.get(Calendar.MONTH) == now.get(Calendar.MONTH) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                else -> true
            }

            matchSearch && matchStatus && matchDate
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 30.dp)
        ) {
            // 1. STATS KPI CARDS & JAM OPERASIONAL BANNER (Hanya pada halaman ringkasan, tidak pada Tabel Riwayat Penuh)
            if (!isFullPage) {
                item {
                    AbsensiStatsOverview(
                        total = totalCount,
                        hadir = hadirCount,
                        izin = izinCount,
                        sakit = sakitCount,
                        terlambat = terlambatCount,
                        pelanggaran = pelanggaranCount,
                        batasMasuk = defaultBatasMasuk,
                        batasPulang = defaultBatasPulang,
                        onEditJamClick = { showSettingsDialog = true }
                    )
                }
            }

            // 2. FILTER & SEARCH TOOLBAR WITH ACTION ICONS (Scanner, +, Batas Jam, Bagikan, Filter to the left of search)
            item {
                var filterMenuExpanded by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Filter Icon (Dropdown Menu with Status & Tanggal options)
                        Box {
                            IconButton(
                                onClick = { filterMenuExpanded = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_filter_absensi")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter Kategori & Tanggal",
                                        tint = if (selectedStatusFilter != "Semua" || selectedDateFilter != "Semua Tanggal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (selectedStatusFilter != "Semua" || selectedDateFilter != "Semua Tanggal") {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false }
                            ) {
                                Text(
                                    text = "Filter Status & Pelanggaran",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                listOf("Semua", "Pelanggaran", "Tepat Waktu", "Hadir", "Izin", "Sakit", "Terlambat").forEach { statusOption ->
                                    val isSelected = selectedStatusFilter == statusOption
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = statusOption,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) (if (statusOption == "Pelanggaran") Color(0xFFDC2626) else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (statusOption == "Pelanggaran") Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            selectedStatusFilter = statusOption
                                            filterMenuExpanded = false
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "Filter Periode Tanggal",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                listOf("Semua Tanggal", "Hari Ini", "7 Hari Terakhir", "Bulan Ini").forEach { dateOption ->
                                    val isSelected = selectedDateFilter == dateOption
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = dateOption,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            selectedDateFilter = dateOption
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Icon Scanner QR (Tanpa Title)
                        IconButton(
                            onClick = { showScannerDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_action_scan_qr")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Presensi",
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. Icon + (Input Manual Absensi & Pulang Tanpa Title)
                        IconButton(
                            onClick = { showManualInputDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_action_manual_input")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Input Manual Absensi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 4. Icon Batas Waktu / Jam
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_action_settings_jam")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Atur Batas Jam",
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 5. Icon Bagikan (Ekspor CSV)
                        IconButton(
                            onClick = { exportPresensiToCsv(context, allPresensi) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_action_export_csv")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan CSV",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // 6. Icon Pencarian & Search Field (di sebelah kanan action icons)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 2.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                ),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Cari...",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("search_field_absensi")
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus pencarian",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        // Active filter badge if any
                        if (selectedStatusFilter != "Semua" || selectedDateFilter != "Semua Tanggal") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable {
                                        selectedStatusFilter = "Semua"
                                        selectedDateFilter = "Semua Tanggal"
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (selectedStatusFilter != "Semua") selectedStatusFilter else selectedDateFilter,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Reset filter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. TABEL DATA ABSENSI & RIWAYAT
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!isFullPage) {
                                onOpenFullHistory?.invoke()
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = "Tabel Riwayat & Pelanggaran Kehadiran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isFullPage && filteredPresensi.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F766E).copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onOpenFullHistory?.invoke() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (filteredPresensi.size > 4) "${filteredPresensi.size} Data (4 Ditampilkan)" else "${filteredPresensi.size} Data",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Buka Halaman Lengkap",
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isFullPage && filteredPresensi.size > 4) {
                            TextButton(
                                onClick = { onOpenFullHistory?.invoke() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Lihat Semua", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF0F766E))
                            }
                        }

                        if (allPresensi.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearAllConfirmDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus Semua", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 5. TABLE CONTENT
            item {
                if (filteredPresensi.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank() || selectedStatusFilter != "Semua") "Tidak ada data absensi yang sesuai filter" else "Belum ada data absensi tercatat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Gunakan tombol 'Scan QR' atau 'Input Manual' untuk mencatat kehadiran dan waktu pulang anggota.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { showScannerDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mulai Scan QR", fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val horizontalScrollState = rememberScrollState()
                        val displayedPresensi = if (isFullPage) filteredPresensi else filteredPresensi.take(4)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            // TABLE HEADER ROW (Waktu Masuk, Waktu Pulang, Nama Anggota, NIM/NIP, Status, Pelanggaran, Metode, Aksi)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .width(960.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "WAKTU MASUK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(115.dp)
                                    )
                                    Text(
                                        text = "WAKTU PULANG",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(135.dp)
                                    )
                                    Text(
                                        text = "NAMA ANGGOTA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(185.dp)
                                    )
                                    Text(
                                        text = "NIM/NIP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(110.dp)
                                    )
                                    Text(
                                        text = "STATUS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(95.dp)
                                    )
                                    Text(
                                        text = "PELANGGARAN",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(155.dp)
                                    )
                                    Text(
                                        text = "METODE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Text(
                                        text = "AKSI",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(75.dp)
                                    )
                                }
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // TABLE BODY ROWS
                            displayedPresensi.forEachIndexed { index, item ->
                                val dateObj = remember(item.timestamp) { Date(item.timestamp) }
                                val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }
                                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
                                val timeMasukStr = timeFormat.format(dateObj)
                                val dateMasukStr = dateFormat.format(dateObj)

                                val pulangDateObj = item.waktuPulang?.let { Date(it) }
                                val timePulangStr = pulangDateObj?.let { timeFormat.format(it) }
                                val datePulangStr = pulangDateObj?.let { dateFormat.format(it) }

                                val isEven = index % 2 == 0
                                val rowBg = if (isEven) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

                                Row(
                                    modifier = Modifier
                                        .background(rowBg)
                                        .clickable { viewingDetailPresensi = item }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .width(960.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. WAKTU MASUK
                                    Column(modifier = Modifier.width(115.dp)) {
                                        Text(
                                            text = timeMasukStr,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = dateMasukStr,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 2. WAKTU PULANG (dengan tombol catat pulang jika belum)
                                    Box(modifier = Modifier.width(135.dp)) {
                                        if (timePulangStr != null) {
                                            Column {
                                                Text(
                                                    text = timePulangStr,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF0F766E)
                                                )
                                                Text(
                                                    text = datePulangStr ?: "",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFD97706).copy(alpha = 0.15f),
                                                modifier = Modifier
                                                    .clickable { recordingPulangItem = item }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Catat Pulang",
                                                        color = Color(0xFFD97706),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. NAMA ANGGOTA
                                    Row(
                                        modifier = Modifier.width(185.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF0F766E).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.nama.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F766E)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.nama,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (item.divisi.isNotBlank() || item.status.isNotBlank()) {
                                                Text(
                                                    text = listOf(item.status, item.divisi).filter { it.isNotBlank() }.joinToString(" • "),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    // 4. NIM/NIP
                                    Box(modifier = Modifier.width(110.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = item.nomorInduk.ifBlank { "-" },
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // 5. STATUS
                                    Box(modifier = Modifier.width(95.dp)) {
                                        val (statusBg, statusFg) = when (item.statusPresensi.lowercase()) {
                                            "hadir" -> Color(0xFF059669).copy(alpha = 0.15f) to Color(0xFF059669)
                                            "izin" -> Color(0xFF2563EB).copy(alpha = 0.15f) to Color(0xFF2563EB)
                                            "sakit" -> Color(0xFFD97706).copy(alpha = 0.15f) to Color(0xFFD97706)
                                            "terlambat" -> Color(0xFFDC2626).copy(alpha = 0.15f) to Color(0xFFDC2626)
                                            else -> Color(0xFF4B5563).copy(alpha = 0.15f) to Color(0xFF4B5563)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = statusBg
                                        ) {
                                            Text(
                                                text = item.statusPresensi,
                                                color = statusFg,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    // 6. PELANGGARAN
                                    Box(modifier = Modifier.width(155.dp)) {
                                        val pelanggaran = item.statusPelanggaran.ifBlank { "Tepat Waktu" }
                                        val isTepat = pelanggaran.equals("Tepat Waktu", ignoreCase = true)

                                        val (pelBg, pelFg) = if (isTepat) {
                                            Color(0xFF059669).copy(alpha = 0.12f) to Color(0xFF059669)
                                        } else if (pelanggaran.contains("Terlambat & Pulang", ignoreCase = true)) {
                                            Color(0xFF991B1B).copy(alpha = 0.15f) to Color(0xFF991B1B)
                                        } else if (pelanggaran.contains("Terlambat", ignoreCase = true)) {
                                            Color(0xFFDC2626).copy(alpha = 0.15f) to Color(0xFFDC2626)
                                        } else {
                                            Color(0xFFEA580C).copy(alpha = 0.15f) to Color(0xFFEA580C)
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = pelBg
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isTepat) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = pelFg,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = pelanggaran,
                                                    color = pelFg,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // 7. METODE
                                    Row(
                                        modifier = Modifier.width(90.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val icon = if (item.metode.contains("Manual", ignoreCase = true)) Icons.Default.Edit else Icons.Default.QrCodeScanner
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = item.metode.ifBlank { "QR" },
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // 8. AKSI
                                    Row(
                                        modifier = Modifier.width(75.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { recordingPulangItem = item },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = "Catat/Edit Pulang",
                                                tint = Color(0xFF0F766E),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { deletingPresensi = item },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Hapus Log",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (index < filteredPresensi.lastIndex) {
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }

            if (!isFullPage && filteredPresensi.size > 4) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F766E).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFullHistory?.invoke() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatListNumbered,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Menampilkan 4 dari ${filteredPresensi.size} riwayat kehadiran",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Buka Halaman Lengkap",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F766E)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // --- DIALOGS ---

    // 1. POP-UP PERINGATAN PELANGGARAN
    violationPopupData?.let { violation ->
        PelanggaranAlertDialog(
            data = violation,
            onDismiss = { violationPopupData = null },
            onConfirmSave = {
                violation.onProceedAction()
                violationPopupData = null
            }
        )
    }

    // 2. Scanner Dialog (Support Mode Masuk & Mode Pulang)
    if (showScannerDialog) {
        AttendanceQrScannerDialog(
            allAnggota = allAnggota,
            allPresensi = allPresensi,
            batasMasuk = defaultBatasMasuk,
            batasPulang = defaultBatasPulang,
            onDismiss = { showScannerDialog = false },
            onRecordAttendance = { presensi, hasViolation, violationData ->
                if (hasViolation && violationData != null) {
                    violationPopupData = violationData.copy(
                        onProceedAction = {
                            viewModel.recordPresensi(presensi) {
                                playAbsensiFeedback(presensi, false)
                                viewModel.showToastPopup("Presensi ${presensi.nama} dicatat dengan status ${presensi.statusPelanggaran}")
                            }
                        }
                    )
                } else {
                    viewModel.recordPresensi(presensi) {
                        playAbsensiFeedback(presensi, false)
                        viewModel.showToastPopup("Presensi ${presensi.nama} (${presensi.statusPresensi}) berhasil dicatat!")
                    }
                }
            },
            onUpdateWaktuPulang = { updatedPresensi, hasViolation, violationData ->
                if (hasViolation && violationData != null) {
                    violationPopupData = violationData.copy(
                        onProceedAction = {
                            viewModel.updatePresensi(updatedPresensi) {
                                playAbsensiFeedback(updatedPresensi, true)
                                viewModel.showToastPopup("Waktu pulang ${updatedPresensi.nama} dicatat dengan status ${updatedPresensi.statusPelanggaran}")
                            }
                        }
                    )
                } else {
                    viewModel.updatePresensi(updatedPresensi) {
                        playAbsensiFeedback(updatedPresensi, true)
                        viewModel.showToastPopup("Waktu pulang ${updatedPresensi.nama} berhasil dicatat!")
                    }
                }
            }
        )
    }

    // 3. Manual Input Attendance Dialog (dengan input jam masuk & jam pulang + batas waktu)
    if (showManualInputDialog) {
        ManualAbsensiDialog(
            allAnggota = allAnggota,
            defaultBatasMasuk = defaultBatasMasuk,
            defaultBatasPulang = defaultBatasPulang,
            onDismiss = { showManualInputDialog = false },
            onValidateAndSave = { presensi, hasViolation, violationData ->
                if (hasViolation && violationData != null) {
                    violationPopupData = violationData.copy(
                        onProceedAction = {
                            viewModel.recordPresensi(presensi) {
                                playAbsensiFeedback(presensi, false)
                                viewModel.showToastPopup("Presensi ${presensi.nama} berhasil disimpan!")
                            }
                            showManualInputDialog = false
                        }
                    )
                } else {
                    viewModel.recordPresensi(presensi) {
                        playAbsensiFeedback(presensi, false)
                        viewModel.showToastPopup("Presensi ${presensi.nama} berhasil disimpan!")
                    }
                    showManualInputDialog = false
                }
            }
        )
    }

    // 4. Quick Record Waktu Pulang Dialog
    recordingPulangItem?.let { targetItem ->
        RecordWaktuPulangDialog(
            item = targetItem,
            defaultBatasPulang = defaultBatasPulang,
            onDismiss = { recordingPulangItem = null },
            onSave = { updated, hasViolation, violationData ->
                if (hasViolation && violationData != null) {
                    violationPopupData = violationData.copy(
                        onProceedAction = {
                            viewModel.updatePresensi(updated) {
                                playAbsensiFeedback(updated, true)
                                viewModel.showToastPopup("Waktu pulang ${updated.nama} berhasil diperbarui!")
                            }
                            recordingPulangItem = null
                        }
                    )
                } else {
                    viewModel.updatePresensi(updated) {
                        playAbsensiFeedback(updated, true)
                        viewModel.showToastPopup("Waktu pulang ${updated.nama} berhasil dicatat!")
                    }
                    recordingPulangItem = null
                }
            }
        )
    }

    // 5. Settings Batas Waktu Masuk & Pulang Dialog
    if (showSettingsDialog) {
        PengaturanBatasWaktuDialog(
            currentBatasMasuk = defaultBatasMasuk,
            currentBatasPulang = defaultBatasPulang,
            onDismiss = { showSettingsDialog = false },
            onSave = { newMasuk, newPulang ->
                defaultBatasMasuk = newMasuk
                defaultBatasPulang = newPulang
                prefs.edit()
                    .putString("batas_waktu_masuk", newMasuk)
                    .putString("batas_waktu_pulang", newPulang)
                    .apply()
                viewModel.showToastPopup("Batas jam kerja berhasil diperbarui: Masuk $newMasuk, Pulang $newPulang")
                showSettingsDialog = false
            }
        )
    }

    // 6. Confirm Delete Single Log Dialog
    deletingPresensi?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingPresensi = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Hapus Riwayat Absensi?")
            },
            text = {
                Text("Hapus log absensi atas nama ${target.nama} (${target.nomorInduk}) pada waktu ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(target.timestamp))}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePresensi(target)
                        deletingPresensi = null
                        viewModel.showToastPopup("Log absensi berhasil dihapus")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPresensi = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // 7. Confirm Clear All Logs Dialog
    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Bersihkan Semua Log Absensi?")
            },
            text = {
                Text("Tindakan ini akan menghapus SEMUA riwayat absensi secara permanen. Tindakan ini tidak dapat dibatalkan.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllPresensi()
                        showClearAllConfirmDialog = false
                        viewModel.showToastPopup("Semua riwayat absensi telah dibersihkan")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Bersihkan Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // 8. Detail Presensi Dialog
    viewingDetailPresensi?.let { item ->
        val sdfFull = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale("id", "ID")) }
        val timeMasukStr = sdfFull.format(Date(item.timestamp))
        val timePulangStr = item.waktuPulang?.let { sdfFull.format(Date(it)) } ?: "Belum Dicatat / Belum Pulang"

        AlertDialog(
            onDismissRequest = { viewingDetailPresensi = null },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0F766E))
            },
            title = {
                Text("Detail Riwayat Absensi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(label = "Nama Anggota", value = item.nama)
                    DetailRow(label = "NIM / NIP", value = item.nomorInduk.ifBlank { "-" })
                    DetailRow(label = "Peran / Status", value = "${item.status} (${item.divisi.ifBlank { "Umum" }})")
                    DetailRow(label = "Status Kehadiran", value = item.statusPresensi)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow(label = "Waktu Masuk", value = timeMasukStr)
                    DetailRow(label = "Batas Jam Masuk", value = item.batasWaktuMasuk)
                    DetailRow(label = "Waktu Pulang", value = timePulangStr)
                    DetailRow(label = "Batas Jam Pulang", value = item.batasWaktuPulang)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow(label = "Status Pelanggaran", value = item.statusPelanggaran)
                    if (item.catatanPelanggaran.isNotBlank()) {
                        DetailRow(label = "Catatan Pelanggaran", value = item.catatanPelanggaran)
                    }
                    DetailRow(label = "Metode Input", value = item.metode)
                    if (item.qrPayload.isNotBlank()) {
                        DetailRow(label = "Data QR Payload", value = item.qrPayload)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewingDetailPresensi = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                ) {
                    Text("Tutup")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// DATA CLASS: VIOLATION WARNING
// -------------------------------------------------------------
data class PelanggaranWarningData(
    val title: String,
    val nama: String,
    val jenisPelanggaran: String, // "Terlambat Masuk", "Pulang Lebih Awal", dll
    val waktuInputStr: String,
    val batasWaktuStr: String,
    val selisihMenit: Int,
    val pesanDetail: String,
    val onProceedAction: () -> Unit = {}
)

// -------------------------------------------------------------
// COMPONENT: POP-UP PERINGATAN PELANGGARAN (ALERT DIALOG)
// -------------------------------------------------------------
@Composable
fun PelanggaranAlertDialog(
    data: PelanggaranWarningData,
    onDismiss: () -> Unit,
    onConfirmSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = Color(0xFFDC2626).copy(alpha = 0.15f),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan Pelanggaran",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = "⚠️ Peringatan Pelanggaran Absensi",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFFDC2626),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFDC2626).copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = data.jenisPelanggaran.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626)
                        )
                        Text(
                            text = "Anggota: ${data.nama}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Waktu Tercatat: ${data.waktuInputStr}",
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Batas Ketentuan: ${data.batasWaktuStr}",
                            fontSize = 12.sp
                        )
                        if (data.selisihMenit > 0) {
                            Text(
                                text = "Selisih Waktu: ${data.selisihMenit} Menit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }

                Text(
                    text = data.pesanDetail,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Apakah Anda ingin tetap menyimpan data presensi dengan status pelanggaran ini?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Tetap Simpan Pelanggaran", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal / Sesuaikan Waktu", fontSize = 12.sp)
            }
        }
    )
}

// -------------------------------------------------------------
// COMPONENT: KPI STATS & BANNER OVERVIEW
// -------------------------------------------------------------
@Composable
fun AbsensiStatsOverview(
    total: Int,
    hadir: Int,
    izin: Int,
    sakit: Int,
    terlambat: Int,
    pelanggaran: Int,
    batasMasuk: String,
    batasPulang: String,
    onEditJamClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(20.dp))
                    Text(
                        text = "Ringkasan Absensi & Jam Kerja",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F766E).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Total: $total",
                        color = Color(0xFF0F766E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // BANNER BATAS JAM OPERASIONAL
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F766E).copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditJamClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(16.dp))
                        Text(
                            text = "Batas Masuk: $batasMasuk WIB • Batas Pulang: $batasPulang WIB",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F766E)
                        )
                    }
                    Text(
                        text = "Ubah ⚙️",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E)
                    )
                }
            }

            // STATS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KpiStatItem(label = "Hadir", count = hadir, color = Color(0xFF059669), modifier = Modifier.weight(1f))
                KpiStatItem(label = "Izin", count = izin, color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                KpiStatItem(label = "Sakit", count = sakit, color = Color(0xFFD97706), modifier = Modifier.weight(1f))
                KpiStatItem(label = "Terlambat", count = terlambat, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                KpiStatItem(label = "Pelanggaran", count = pelanggaran, color = Color(0xFF991B1B), modifier = Modifier.weight(1.1f))
            }
        }
    }
}

@Composable
fun KpiStatItem(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// COMPONENT: MANUAL INPUT ABSENSI & WAKTU PULANG DIALOG
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAbsensiDialog(
    allAnggota: List<AnggotaItem>,
    defaultBatasMasuk: String,
    defaultBatasPulang: String,
    onDismiss: () -> Unit,
    onValidateAndSave: (RiwayatPresensi, Boolean, PelanggaranWarningData?) -> Unit
) {
    var selectedAnggota by remember { mutableStateOf<AnggotaItem?>(null) }
    var manualNama by remember { mutableStateOf("") }
    var manualNip by remember { mutableStateOf("") }
    var manualDivisi by remember { mutableStateOf("") }
    var manualStatusAnggota by remember { mutableStateOf("Karyawan") }
    var selectedStatusPresensi by remember { mutableStateOf("Hadir") }

    var isNameDropdownExpanded by remember { mutableStateOf(false) }
    val filteredAnggotaByName = remember(manualNama, allAnggota) {
        if (manualNama.trim().isEmpty()) emptyList()
        else allAnggota.filter {
            it.nama.contains(manualNama.trim(), ignoreCase = true) ||
            it.nomorInduk.contains(manualNama.trim(), ignoreCase = true)
        }
    }

    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val currentTime = remember { sdfTime.format(Date()) }

    var inputWaktuMasuk by remember { mutableStateOf(currentTime) }
    var inputBatasMasuk by remember { mutableStateOf(defaultBatasMasuk) }

    var isCatatPulangAlso by remember { mutableStateOf(false) }
    var inputWaktuPulang by remember { mutableStateOf(defaultBatasPulang) }
    var inputBatasPulang by remember { mutableStateOf(defaultBatasPulang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Input Absensi & Waktu Pulang", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Input Nama Anggota dengan Autocomplete Menu List & Auto-fill Data
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = isNameDropdownExpanded && filteredAnggotaByName.isNotEmpty(),
                            onExpandedChange = { isNameDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = manualNama,
                                onValueChange = {
                                    manualNama = it
                                    isNameDropdownExpanded = it.isNotBlank()
                                    if (selectedAnggota != null && !it.equals(selectedAnggota?.nama, ignoreCase = true)) {
                                        selectedAnggota = null
                                    }
                                },
                                label = { Text("Nama Anggota *") },
                                placeholder = { Text("Ketik nama / NIP anggota...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (selectedAnggota != null) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (selectedAnggota != null) {
                                        IconButton(onClick = {
                                            selectedAnggota = null
                                            manualNama = ""
                                            manualNip = ""
                                            manualDivisi = ""
                                        }) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Terhubung", tint = Color(0xFF10B981))
                                        }
                                    } else if (manualNama.isNotBlank()) {
                                        IconButton(onClick = {
                                            manualNama = ""
                                            isNameDropdownExpanded = false
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            if (filteredAnggotaByName.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = isNameDropdownExpanded,
                                    onDismissRequest = { isNameDropdownExpanded = false },
                                    modifier = Modifier.heightIn(max = 240.dp)
                                ) {
                                    Text(
                                        text = "Anggota Terdaftar Ditemukan (${filteredAnggotaByName.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    filteredAnggotaByName.forEach { member ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = member.nama.take(1).uppercase(Locale.getDefault()),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 13.sp,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(member.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(
                                                            text = "${member.nomorInduk} • ${member.divisiOrJurusan.ifBlank { "Umum" }}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF0F766E).copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = member.status,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF0F766E),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedAnggota = member
                                                manualNama = member.nama
                                                manualNip = member.nomorInduk
                                                manualDivisi = member.divisiOrJurusan
                                                manualStatusAnggota = member.status
                                                isNameDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Auto-fill success indicator badge
                        if (selectedAnggota != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✓ Data terisi otomatis: ${selectedAnggota?.nama} (${selectedAnggota?.nomorInduk})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualNip,
                            onValueChange = { manualNip = it },
                            label = { Text("NIM / NIP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = manualDivisi,
                            onValueChange = { manualDivisi = it },
                            label = { Text("Divisi / Kelas") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text("1. Waktu Masuk & Batas Masuk:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputWaktuMasuk,
                            onValueChange = { inputWaktuMasuk = it },
                            label = { Text("Waktu Masuk (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = inputBatasMasuk,
                            onValueChange = { inputBatasMasuk = it },
                            label = { Text("Batas Masuk (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("2. Catat Waktu Pulang Sekaligus?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = isCatatPulangAlso,
                            onCheckedChange = { isCatatPulangAlso = it }
                        )
                    }

                    if (isCatatPulangAlso) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inputWaktuPulang,
                                onValueChange = { inputWaktuPulang = it },
                                label = { Text("Waktu Pulang (HH:mm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputBatasPulang,
                                onValueChange = { inputBatasPulang = it },
                                label = { Text("Batas Pulang (HH:mm)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Text("Status Kehadiran:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Hadir", "Izin", "Sakit", "Terlambat").forEach { st ->
                            val isSel = selectedStatusPresensi == st
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedStatusPresensi = st },
                                label = { Text(st, fontSize = 11.sp) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualNama.isBlank()) return@Button

                    val masukMinutes = parseTimeToMinutes(inputWaktuMasuk)
                    val batasMasukMinutes = parseTimeToMinutes(inputBatasMasuk)

                    var isTerlambatMasuk = masukMinutes > batasMasukMinutes && selectedStatusPresensi.equals("Hadir", ignoreCase = true)
                    var isPulangAwal = false
                    var pulangMinutes = 0
                    var batasPulangMinutes = 0

                    if (isCatatPulangAlso) {
                        pulangMinutes = parseTimeToMinutes(inputWaktuPulang)
                        batasPulangMinutes = parseTimeToMinutes(inputBatasPulang)
                        isPulangAwal = pulangMinutes < batasPulangMinutes
                    }

                    val finalStatusPresensi = if (isTerlambatMasuk) "Terlambat" else selectedStatusPresensi

                    val statusPelanggaran = when {
                        isTerlambatMasuk && isPulangAwal -> "Terlambat & Pulang Cepat"
                        isTerlambatMasuk -> "Terlambat Masuk"
                        isPulangAwal -> "Pulang Lebih Awal"
                        else -> "Tepat Waktu"
                    }

                    val catatanPelanggaran = buildString {
                        if (isTerlambatMasuk) {
                            append("Terlambat ${masukMinutes - batasMasukMinutes} mnt (Masuk: $inputWaktuMasuk > Batas: $inputBatasMasuk). ")
                        }
                        if (isPulangAwal) {
                            append("Pulang lebih awal ${batasPulangMinutes - pulangMinutes} mnt (Pulang: $inputWaktuPulang < Batas: $inputBatasPulang).")
                        }
                    }.trim()

                    // Build timestamp
                    val calMasuk = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, masukMinutes / 60)
                        set(Calendar.MINUTE, masukMinutes % 60)
                        set(Calendar.SECOND, 0)
                    }

                    val waktuPulangLong = if (isCatatPulangAlso) {
                        Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, pulangMinutes / 60)
                            set(Calendar.MINUTE, pulangMinutes % 60)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                    } else null

                    val presensi = RiwayatPresensi(
                        anggotaId = selectedAnggota?.id?.toLong() ?: 0L,
                        nomorInduk = manualNip.ifBlank { "MANUAL" },
                        nama = manualNama.trim(),
                        status = manualStatusAnggota,
                        divisi = manualDivisi.trim(),
                        statusPresensi = finalStatusPresensi,
                        timestamp = calMasuk.timeInMillis,
                        waktuPulang = waktuPulangLong,
                        batasWaktuMasuk = inputBatasMasuk,
                        batasWaktuPulang = inputBatasPulang,
                        statusPelanggaran = statusPelanggaran,
                        catatanPelanggaran = catatanPelanggaran,
                        metode = "Input Manual"
                    )

                    val hasViolation = isTerlambatMasuk || isPulangAwal
                    val violationData = if (hasViolation) {
                        val selisih = if (isTerlambatMasuk) (masukMinutes - batasMasukMinutes) else (batasPulangMinutes - pulangMinutes)
                        PelanggaranWarningData(
                            title = "Peringatan Pelanggaran Waktu",
                            nama = manualNama,
                            jenisPelanggaran = statusPelanggaran,
                            waktuInputStr = if (isTerlambatMasuk) "Masuk: $inputWaktuMasuk" else "Pulang: $inputWaktuPulang",
                            batasWaktuStr = if (isTerlambatMasuk) "Batas Masuk: $inputBatasMasuk" else "Batas Pulang: $inputBatasPulang",
                            selisihMenit = selisih,
                            pesanDetail = catatanPelanggaran
                        )
                    } else null

                    onValidateAndSave(presensi, hasViolation, violationData)
                },
                enabled = manualNama.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text("Simpan Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// -------------------------------------------------------------
// COMPONENT: RECORD WAKTU PULANG DIALOG
// -------------------------------------------------------------
@Composable
fun RecordWaktuPulangDialog(
    item: RiwayatPresensi,
    defaultBatasPulang: String,
    onDismiss: () -> Unit,
    onSave: (RiwayatPresensi, Boolean, PelanggaranWarningData?) -> Unit
) {
    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val currentTime = remember { sdfTime.format(Date()) }

    var inputWaktuPulang by remember { mutableStateOf(currentTime) }
    var inputBatasPulang by remember { mutableStateOf(item.batasWaktuPulang.ifBlank { defaultBatasPulang }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Catat Waktu Pulang", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("NIM/NIP: ${item.nomorInduk} • ${item.status}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Waktu Masuk: ${SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(item.timestamp))}", fontSize = 11.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedTextField(
                    value = inputWaktuPulang,
                    onValueChange = { inputWaktuPulang = it },
                    label = { Text("Waktu Pulang (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputBatasPulang,
                    onValueChange = { inputBatasPulang = it },
                    label = { Text("Batas Jam Pulang (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pulangMinutes = parseTimeToMinutes(inputWaktuPulang)
                    val batasPulangMinutes = parseTimeToMinutes(inputBatasPulang)
                    val isPulangAwal = pulangMinutes < batasPulangMinutes

                    val isTerlambatMasuk = item.statusPelanggaran.contains("Terlambat", ignoreCase = true)

                    val newStatusPelanggaran = when {
                        isTerlambatMasuk && isPulangAwal -> "Terlambat & Pulang Cepat"
                        isTerlambatMasuk -> "Terlambat Masuk"
                        isPulangAwal -> "Pulang Lebih Awal"
                        else -> "Tepat Waktu"
                    }

                    val extraCatatan = if (isPulangAwal) "Pulang lebih awal ${batasPulangMinutes - pulangMinutes} mnt (Pulang: $inputWaktuPulang < Batas: $inputBatasPulang)." else ""
                    val finalCatatan = listOf(item.catatanPelanggaran, extraCatatan).filter { it.isNotBlank() }.joinToString(" ")

                    val calPulang = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, pulangMinutes / 60)
                        set(Calendar.MINUTE, pulangMinutes % 60)
                        set(Calendar.SECOND, 0)
                    }

                    val updated = item.copy(
                        waktuPulang = calPulang.timeInMillis,
                        batasWaktuPulang = inputBatasPulang,
                        statusPelanggaran = newStatusPelanggaran,
                        catatanPelanggaran = finalCatatan
                    )

                    val violationData = if (isPulangAwal) {
                        PelanggaranWarningData(
                            title = "Peringatan Pulang Lebih Awal",
                            nama = item.nama,
                            jenisPelanggaran = "Pulang Lebih Awal",
                            waktuInputStr = "Pulang: $inputWaktuPulang",
                            batasWaktuStr = "Batas Pulang: $inputBatasPulang",
                            selisihMenit = batasPulangMinutes - pulangMinutes,
                            pesanDetail = "Anggota pulang sebelum batas jam kerja ($inputWaktuPulang < $inputBatasPulang). Pelanggaran akan dicatat dalam riwayat."
                        )
                    } else null

                    onSave(updated, isPulangAwal, violationData)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text("Simpan Waktu Pulang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// -------------------------------------------------------------
// COMPONENT: PENGATURAN BATAS WAKTU MASUK & PULANG DIALOG
// -------------------------------------------------------------
@Composable
fun PengaturanBatasWaktuDialog(
    currentBatasMasuk: String,
    currentBatasPulang: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var masukVal by remember { mutableStateOf(currentBatasMasuk) }
    var pulangVal by remember { mutableStateOf(currentBatasPulang) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF0F766E))
        },
        title = {
            Text("Pengaturan Batas Waktu Absensi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tentukan batas waktu absensi masuk dan waktu kepulangan. Anggota yang absen melewati batas waktu ini akan memicu peringatan pelanggaran.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = masukVal,
                    onValueChange = { masukVal = it },
                    label = { Text("Batas Jam Masuk (HH:mm)") },
                    placeholder = { Text("Contoh: 08:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pulangVal,
                    onValueChange = { pulangVal = it },
                    label = { Text("Batas Jam Pulang (HH:mm)") },
                    placeholder = { Text("Contoh: 17:00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (masukVal.isNotBlank() && pulangVal.isNotBlank()) {
                        onSave(masukVal.trim(), pulangVal.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text("Simpan Pengaturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// -------------------------------------------------------------
// COMPONENT: QR SCANNER DIALOG (SUPPORT MASUK & PULANG)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceQrScannerDialog(
    allAnggota: List<AnggotaItem>,
    allPresensi: List<RiwayatPresensi>,
    batasMasuk: String,
    batasPulang: String,
    onDismiss: () -> Unit,
    onRecordAttendance: (RiwayatPresensi, Boolean, PelanggaranWarningData?) -> Unit,
    onUpdateWaktuPulang: (RiwayatPresensi, Boolean, PelanggaranWarningData?) -> Unit
) {
    var isModePulang by remember { mutableStateOf(false) }
    var qrInputText by remember { mutableStateOf("") }
    var selectedMemberForQuickScan by remember { mutableStateOf<AnggotaItem?>(null) }
    var scanStatusChoice by remember { mutableStateOf("Hadir") }
    var showCameraScanner by remember { mutableStateOf(false) }

    var isScannerDropdownExpanded by remember { mutableStateOf(false) }
    val filteredMembersForScanner = remember(qrInputText, allAnggota) {
        if (qrInputText.trim().isEmpty()) emptyList()
        else allAnggota.filter {
            it.nama.contains(qrInputText.trim(), ignoreCase = true) ||
            it.nomorInduk.contains(qrInputText.trim(), ignoreCase = true)
        }
    }

    if (showCameraScanner) {
        BarcodeScannerDialog(
            onDismissRequest = { showCameraScanner = false },
            onBarcodeScanned = { scannedCode ->
                val trimmed = scannedCode.trim()
                qrInputText = trimmed
                val member = allAnggota.find {
                    it.nomorInduk.equals(trimmed, ignoreCase = true) ||
                    it.nama.equals(trimmed, ignoreCase = true) ||
                    it.qrCodeData.equals(trimmed, ignoreCase = true)
                }
                if (member != null) {
                    selectedMemberForQuickScan = member
                }
                showCameraScanner = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pemindai QR Absensi", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // MODE SELECTOR: MASUK VS PULANG
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { isModePulang = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isModePulang) Color(0xFF0F766E) else Color.Transparent,
                                contentColor = if (!isModePulang) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Absen Masuk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { isModePulang = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isModePulang) Color(0xFFD97706) else Color.Transparent,
                                contentColor = if (isModePulang) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Absen Pulang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!isModePulang) Color(0xFF0F766E).copy(alpha = 0.08f) else Color(0xFFD97706).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (!isModePulang) "Mode Absen Masuk • Batas Waktu Masuk: $batasMasuk" else "Mode Absen Pulang • Batas Waktu Pulang: $batasPulang",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                // INPUT MANUAL QR PAYLOAD / SCANNER TEXT DENGAN AUTOCOMPLETE LIST
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = isScannerDropdownExpanded && filteredMembersForScanner.isNotEmpty(),
                        onExpandedChange = { isScannerDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = qrInputText,
                            onValueChange = {
                                qrInputText = it
                                isScannerDropdownExpanded = it.isNotBlank()
                                if (selectedMemberForQuickScan != null &&
                                    !it.equals(selectedMemberForQuickScan?.nomorInduk, ignoreCase = true) &&
                                    !it.equals(selectedMemberForQuickScan?.nama, ignoreCase = true)) {
                                    selectedMemberForQuickScan = null
                                }
                            },
                            label = { Text("Ketik / Scan QR Anggota (NIM/NIP)") },
                            placeholder = { Text("Ketik nama atau NIM/NIP...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selectedMemberForQuickScan != null) Icons.Default.CheckCircle else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (selectedMemberForQuickScan != null) Color(0xFF10B981) else if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (qrInputText.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                qrInputText = ""
                                                selectedMemberForQuickScan = null
                                                isScannerDropdownExpanded = false
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { showCameraScanner = true },
                                        modifier = Modifier.testTag("scan_id_card_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QrCodeScanner,
                                            contentDescription = "Pindai Barcode / QR ID Card",
                                            tint = if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        if (filteredMembersForScanner.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = isScannerDropdownExpanded,
                                onDismissRequest = { isScannerDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                Text(
                                    text = "Anggota Ditemukan (${filteredMembersForScanner.size}):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                filteredMembersForScanner.forEach { member ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (!isModePulang) Color(0xFF0F766E).copy(alpha = 0.15f) else Color(0xFFD97706).copy(alpha = 0.15f),
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = member.nama.take(1).uppercase(Locale.getDefault()),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(member.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    Text(
                                                        text = "NIM/NIP: ${member.nomorInduk} • ${member.divisiOrJurusan.ifBlank { "Umum" }}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF0F766E).copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = member.status,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF0F766E),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedMemberForQuickScan = member
                                            qrInputText = member.nomorInduk
                                            isScannerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Auto-fill member indicator badge
                    if (selectedMemberForQuickScan != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✓ Anggota Terhubung: ${selectedMemberForQuickScan?.nama} (${selectedMemberForQuickScan?.nomorInduk})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    }
                }

                // PILIH CEPAT DARI DAFTAR ANGGOTA
                if (allAnggota.isNotEmpty()) {
                    Text("Atau pilih anggota cepat:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allAnggota.forEach { member ->
                            val isSelected = selectedMemberForQuickScan?.id == member.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMemberForQuickScan = if (isSelected) null else member
                                    if (!isSelected) {
                                        qrInputText = member.nomorInduk
                                    }
                                },
                                label = { Text("${member.nama} (${member.nomorInduk})", fontSize = 10.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = qrInputText.trim()
                    if (code.isBlank() && selectedMemberForQuickScan == null) return@Button

                    val member = selectedMemberForQuickScan ?: allAnggota.find {
                        it.nomorInduk.equals(code, ignoreCase = true) ||
                        it.nama.equals(code, ignoreCase = true) ||
                        it.qrCodeData.equals(code, ignoreCase = true)
                    }

                    val nama = member?.nama ?: code
                    val nip = member?.nomorInduk ?: code
                    val statusPeran = member?.status ?: "Karyawan"
                    val divisi = member?.divisiOrJurusan ?: ""

                    val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val nowTimeStr = sdfTime.format(Date())
                    val currentMinutes = parseTimeToMinutes(nowTimeStr)

                    if (!isModePulang) {
                        // MODE ABSEN MASUK
                        val batasMasukMinutes = parseTimeToMinutes(batasMasuk)
                        val isTerlambat = currentMinutes > batasMasukMinutes && scanStatusChoice.equals("Hadir", ignoreCase = true)

                        val statusPelanggaran = if (isTerlambat) "Terlambat Masuk" else "Tepat Waktu"
                        val catatan = if (isTerlambat) "Terlambat ${currentMinutes - batasMasukMinutes} mnt (Masuk: $nowTimeStr > Batas: $batasMasuk)." else ""

                        val presensi = RiwayatPresensi(
                            anggotaId = member?.id?.toLong() ?: 0L,
                            nomorInduk = nip,
                            nama = nama,
                            status = statusPeran,
                            divisi = divisi,
                            statusPresensi = if (isTerlambat) "Terlambat" else scanStatusChoice,
                            timestamp = System.currentTimeMillis(),
                            batasWaktuMasuk = batasMasuk,
                            batasWaktuPulang = batasPulang,
                            statusPelanggaran = statusPelanggaran,
                            catatanPelanggaran = catatan,
                            metode = "QR Scanner"
                        )

                        val violationData = if (isTerlambat) {
                            PelanggaranWarningData(
                                title = "Peringatan Keterlambatan Masuk",
                                nama = nama,
                                jenisPelanggaran = "Terlambat Masuk",
                                waktuInputStr = "Masuk: $nowTimeStr",
                                batasWaktuStr = "Batas Masuk: $batasMasuk",
                                selisihMenit = currentMinutes - batasMasukMinutes,
                                pesanDetail = "Anggota melakukan absensi masuk setelah batas waktu ($nowTimeStr > $batasMasuk)."
                            )
                        } else null

                        onRecordAttendance(presensi, isTerlambat, violationData)
                        onDismiss()
                    } else {
                        // MODE ABSEN PULANG
                        val batasPulangMinutes = parseTimeToMinutes(batasPulang)
                        val isPulangAwal = currentMinutes < batasPulangMinutes

                        // Cari presensi hari ini
                        val todayStart = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis

                        val existing = allPresensi.find {
                            (it.nomorInduk.equals(nip, ignoreCase = true) || it.nama.equals(nama, ignoreCase = true)) &&
                            it.timestamp >= todayStart
                        }

                        if (existing != null) {
                            val isTerlambatMasuk = existing.statusPelanggaran.contains("Terlambat", ignoreCase = true)
                            val newPelanggaran = when {
                                isTerlambatMasuk && isPulangAwal -> "Terlambat & Pulang Cepat"
                                isTerlambatMasuk -> "Terlambat Masuk"
                                isPulangAwal -> "Pulang Lebih Awal"
                                else -> "Tepat Waktu"
                            }
                            val extraCatatan = if (isPulangAwal) "Pulang lebih awal ${batasPulangMinutes - currentMinutes} mnt (Pulang: $nowTimeStr < Batas: $batasPulang)." else ""
                            val finalCatatan = listOf(existing.catatanPelanggaran, extraCatatan).filter { it.isNotBlank() }.joinToString(" ")

                            val updated = existing.copy(
                                waktuPulang = System.currentTimeMillis(),
                                batasWaktuPulang = batasPulang,
                                statusPelanggaran = newPelanggaran,
                                catatanPelanggaran = finalCatatan
                            )

                            val violationData = if (isPulangAwal) {
                                PelanggaranWarningData(
                                    title = "Peringatan Pulang Lebih Awal",
                                    nama = nama,
                                    jenisPelanggaran = "Pulang Lebih Awal",
                                    waktuInputStr = "Pulang: $nowTimeStr",
                                    batasWaktuStr = "Batas Pulang: $batasPulang",
                                    selisihMenit = batasPulangMinutes - currentMinutes,
                                    pesanDetail = "Anggota melakukan absensi pulang sebelum batas jam kerja ($nowTimeStr < $batasPulang)."
                                )
                            } else null

                            onUpdateWaktuPulang(updated, isPulangAwal, violationData)
                        } else {
                            // Belum absen masuk, langsung catat presensi dengan waktu pulang
                            val presensi = RiwayatPresensi(
                                anggotaId = member?.id?.toLong() ?: 0L,
                                nomorInduk = nip,
                                nama = nama,
                                status = statusPeran,
                                divisi = divisi,
                                statusPresensi = "Hadir",
                                timestamp = System.currentTimeMillis(),
                                waktuPulang = System.currentTimeMillis(),
                                batasWaktuMasuk = batasMasuk,
                                batasWaktuPulang = batasPulang,
                                statusPelanggaran = if (isPulangAwal) "Pulang Lebih Awal" else "Tepat Waktu",
                                catatanPelanggaran = if (isPulangAwal) "Pulang lebih awal ${batasPulangMinutes - currentMinutes} mnt." else "",
                                metode = "QR Scanner Pulang"
                            )

                            val violationData = if (isPulangAwal) {
                                PelanggaranWarningData(
                                    title = "Peringatan Pulang Lebih Awal",
                                    nama = nama,
                                    jenisPelanggaran = "Pulang Lebih Awal",
                                    waktuInputStr = "Pulang: $nowTimeStr",
                                    batasWaktuStr = "Batas Pulang: $batasPulang",
                                    selisihMenit = batasPulangMinutes - currentMinutes,
                                    pesanDetail = "Anggota melakukan absensi pulang sebelum batas jam kerja ($nowTimeStr < $batasPulang)."
                                )
                            } else null

                            onRecordAttendance(presensi, isPulangAwal, violationData)
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (!isModePulang) Color(0xFF0F766E) else Color(0xFFD97706))
            ) {
                Text(if (!isModePulang) "Proses Absen Masuk" else "Proses Absen Pulang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// -------------------------------------------------------------
// HELPER: DETAIL ROW
// -------------------------------------------------------------
@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f)
        )
    }
}

// -------------------------------------------------------------
// HELPER: PARSE TIME HH:mm TO MINUTES
// -------------------------------------------------------------
fun parseTimeToMinutes(timeStr: String): Int {
    return try {
        val parts = timeStr.trim().split(":")
        val hours = parts[0].toInt()
        val minutes = if (parts.size > 1) parts[1].toInt() else 0
        hours * 60 + minutes
    } catch (e: Exception) {
        8 * 60 // Default 08:00
    }
}

// -------------------------------------------------------------
// HELPER: EXPORT TO CSV
// -------------------------------------------------------------
fun exportPresensiToCsv(context: Context, list: List<RiwayatPresensi>) {
    if (list.isEmpty()) {
        MainViewModel.instance?.showToastPopup("Tidak ada data absensi untuk diekspor")
        return
    }

    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val fileName = "Rekap_Absensi_${sdf.format(Date())}.csv"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { fos ->
            val header = "No,Waktu Masuk,Waktu Pulang,Batas Masuk,Batas Pulang,Nama Anggota,NIM/NIP,Status Anggota,Divisi,Status Kehadiran,Status Pelanggaran,Catatan Pelanggaran,Metode\n"
            fos.write(header.toByteArray(Charsets.UTF_8))

            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("id", "ID"))
            list.forEachIndexed { index, p ->
                val masukStr = timeFormat.format(Date(p.timestamp))
                val pulangStr = p.waktuPulang?.let { timeFormat.format(Date(it)) } ?: "-"
                val row = "${index + 1},\"$masukStr\",\"$pulangStr\",\"${p.batasWaktuMasuk}\",\"${p.batasWaktuPulang}\",\"${p.nama}\",\"${p.nomorInduk}\",\"${p.status}\",\"${p.divisi}\",\"${p.statusPresensi}\",\"${p.statusPelanggaran}\",\"${p.catatanPelanggaran}\",\"${p.metode}\"\n"
                fos.write(row.toByteArray(Charsets.UTF_8))
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rekap Data Absensi & Pelanggaran My Singghara")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekap Absensi (CSV)"))
    } catch (e: Exception) {
        MainViewModel.instance?.showToastPopup("Gagal mengekspor CSV: ${e.localizedMessage}")
    }
}
