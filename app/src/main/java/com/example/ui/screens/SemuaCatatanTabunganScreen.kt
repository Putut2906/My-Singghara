package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavingTransaction
import com.example.ui.MainViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.*

private fun formatToIndonesianDate(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val sdfInput = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val date = sdfInput.parse(dateStr)
        val sdfOutput = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("id", "ID"))
        sdfOutput.format(date ?: java.util.Date())
    } catch (e: Exception) {
        dateStr
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemuaCatatanTabunganScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val targetFlow by viewModel.activeSavingTarget.collectAsState()
    val transactionsFlow by viewModel.savingTransactions.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("Semua") } // "Semua", "Masuk", "Keluar"
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var selectedDateFilter by remember { mutableStateOf("") }
    var showFilterMenuDialog by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showFilterDatePickerDialog by remember { mutableStateOf(false) }

    var trxToDelete by remember { mutableStateOf<SavingTransaction?>(null) }
    var editingTrx by remember { mutableStateOf<SavingTransaction?>(null) }
    var showTrxFormPopup by remember { mutableStateOf(false) }

    var inputTrxJenis by remember { mutableStateOf("") }
    var inputTrxKategori by remember { mutableStateOf("") }
    var inputTrxDeskripsi by remember { mutableStateOf("") }
    var inputTrxNominal by remember { mutableStateOf("") }
    var inputTrxTanggal by remember { mutableStateOf("") }

    val dateFormater = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val categoriesMap = mapOf(
        "masuk" to listOf("Gaji", "Bonus", "Hadiah", "Investasi", "Lainnya"),
        "keluar" to listOf("Kebutuhan Pokok", "Tagihan", "Transportasi", "Pendidikan", "Gaya Hidup", "Lainnya")
    )

    // Dynamic list of categories for filter dropdown
    val allDistinctCategories = remember(transactionsFlow) {
        val list = mutableListOf("Semua")
        val fromTrx = transactionsFlow.map { it.kategori }.filter { it.isNotBlank() }.distinct()
        list.addAll(fromTrx)
        if (!list.contains("Gaji")) list.add("Gaji")
        if (!list.contains("Kebutuhan Pokok")) list.add("Kebutuhan Pokok")
        list.distinct()
    }

    // Filtered transaction list
    val filteredTransactions = transactionsFlow.filter { trx ->
        val matchesSearch = trx.deskripsi.contains(searchQuery, ignoreCase = true) ||
                trx.kategori.contains(searchQuery, ignoreCase = true)
        val matchesType = when (selectedTypeFilter) {
            "Masuk" -> trx.jenis == "masuk"
            "Keluar" -> trx.jenis == "keluar"
            else -> true
        }
        val matchesCategory = (selectedCategoryFilter == "Semua") || trx.kategori.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesDate = selectedDateFilter.isEmpty() || trx.tanggal.startsWith(selectedDateFilter)
        matchesSearch && matchesType && matchesCategory && matchesDate
    }

    val targetName = targetFlow?.nama ?: "Tabungan Utama"
    val targetAmount = targetFlow?.jumlah ?: 0.0
    val targetSaved = transactionsFlow.filter { it.jenis == "masuk" }.sumOf { it.nominal }
    val targetWithdrawn = transactionsFlow.filter { it.jenis == "keluar" }.sumOf { it.nominal }
    val currentSaved = (targetSaved - targetWithdrawn).coerceAtLeast(0.0)
    val progressPercent = if (targetAmount > 0) ((currentSaved / targetAmount) * 100).coerceIn(0.0, 100.0).toInt() else 0

    BackHandler {
        viewModel.currentRoute = "tabungan"
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("semua_catatan_tabungan_screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Info
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Total Saldo Tabungan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    text = String.format("Rp %,.0f", currentSaved),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${filteredTransactions.size} Transaksi",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Search and Filters Section Header
                item {
                    val isFilterCriteriaActive = selectedTypeFilter != "Semua" || selectedCategoryFilter != "Semua" || selectedDateFilter.isNotEmpty()

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Semua Riwayat",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // ICON KACA PEMBESAR (SEARCH)
                                IconButton(
                                    onClick = { isSearchExpanded = !isSearchExpanded },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Pencarian",
                                        tint = if (isSearchExpanded || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // ICON FILTER (MENU LIST POPUP)
                                BadgedBox(
                                    badge = {
                                        if (isFilterCriteriaActive) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(8.dp)
                                            )
                                        }
                                    }
                                ) {
                                    IconButton(
                                        onClick = { showFilterMenuDialog = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            contentDescription = "Filter",
                                            tint = if (isFilterCriteriaActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // CETAK BUTTON
                                IconButton(
                                    onClick = {
                                        printSavings(
                                            context = context,
                                            targetName = targetName,
                                            targetAmount = targetAmount,
                                            currentSaved = currentSaved,
                                            progressPercent = progressPercent,
                                            transactions = filteredTransactions
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Cetak",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // EXPANDABLE SEARCH TEXT FIELD
                        AnimatedVisibility(
                            visible = isSearchExpanded || searchQuery.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Cari catatan atau kategori...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (searchQuery.isNotEmpty()) {
                                                searchQuery = ""
                                            } else {
                                                isSearchExpanded = false
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                        }

                        // ACTIVE FILTER CHIPS & RESET ACTION
                        if (isFilterCriteriaActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedTypeFilter != "Semua") {
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedTypeFilter = "Semua" },
                                        label = { Text("Jenis: ${if (selectedTypeFilter == "Masuk") "Pemasukan" else "Pengeluaran"}", fontSize = 11.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                                if (selectedCategoryFilter != "Semua") {
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedCategoryFilter = "Semua" },
                                        label = { Text("Kategori: $selectedCategoryFilter", fontSize = 11.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                                if (selectedDateFilter.isNotEmpty()) {
                                    InputChip(
                                        selected = true,
                                        onClick = { selectedDateFilter = "" },
                                        label = { Text("Tgl: $selectedDateFilter", fontSize = 11.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedTypeFilter = "Semua"
                                        selectedCategoryFilter = "Semua"
                                        selectedDateFilter = ""
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Reset Filter", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Main Table displaying ALL transactions
                if (filteredTransactions.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tidak ada riwayat tabungan yang cocok dengan filter.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        SavingTable(
                            transactions = filteredTransactions,
                            onEdit = { trx ->
                                editingTrx = trx
                                inputTrxJenis = trx.jenis
                                inputTrxKategori = trx.kategori
                                inputTrxDeskripsi = trx.deskripsi
                                inputTrxNominal = if (trx.nominal % 1.0 == 0.0) trx.nominal.toInt().toString() else trx.nominal.toString()
                                inputTrxTanggal = trx.tanggal
                                showTrxFormPopup = true
                            },
                            onDelete = { trx ->
                                trxToDelete = trx
                            },
                            limit = Int.MAX_VALUE // Show all
                        )
                    }
                }
            }
        }

        if (trxToDelete != null) {
            AlertDialog(
                onDismissRequest = { trxToDelete = null },
                title = {
                    Text(
                        text = "Hapus Riwayat Tabungan?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    val nominalStr = String.format("Rp %,.0f", trxToDelete?.nominal ?: 0.0)
                    Text(
                        text = "Apakah Anda yakin ingin menghapus catatan \"${trxToDelete?.deskripsi?.ifEmpty { trxToDelete?.kategori }}\" " +
                                "sebesar $nominalStr? Tindakan ini tidak dapat dibatalkan.",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            trxToDelete?.let { viewModel.deleteSavingTransaction(it) }
                            trxToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trxToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showTrxFormPopup) {
            Dialog(onDismissRequest = { 
                showTrxFormPopup = false
                editingTrx = null
            }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            if (editingTrx != null) "Edit Catatan Tabungan" else "Pencatatan Transaksi Baru",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dropdown Choose Jenis
                        var jenisExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { jenisExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (inputTrxJenis == "masuk") "Jenis: Uang Masuk"
                                    else if (inputTrxJenis == "keluar") "Jenis: Uang Keluar"
                                    else "Pilih Jenis Transaksi *"
                                )
                            }
                            DropdownMenu(
                                expanded = jenisExpanded,
                                onDismissRequest = { jenisExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Uang Masuk") },
                                    onClick = {
                                        inputTrxJenis = "masuk"
                                        inputTrxKategori = ""
                                        jenisExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Uang Keluar") },
                                    onClick = {
                                        inputTrxJenis = "keluar"
                                        inputTrxKategori = ""
                                        jenisExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dropdown Kategori
                        val katsList = categoriesMap[inputTrxJenis] ?: emptyList()
                        var katExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { katExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = inputTrxJenis.isNotEmpty()
                            ) {
                                Text(
                                    if (inputTrxKategori.isEmpty()) "Pilih Kategori *"
                                    else inputTrxKategori
                                )
                            }
                            DropdownMenu(
                                expanded = katExpanded,
                                onDismissRequest = { katExpanded = false }
                            ) {
                                katsList.forEach { kat ->
                                    DropdownMenuItem(
                                        text = { Text(kat) },
                                        onClick = {
                                            inputTrxKategori = kat
                                            katExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputTrxDeskripsi,
                            onValueChange = { inputTrxDeskripsi = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Keterangan/Deskripsi *)") },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputTrxNominal,
                            onValueChange = { inputTrxNominal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nominal Uang (Rp) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        var showDatePickerDialog by remember { mutableStateOf(false) }
                        val showDatePicker = { showDatePickerDialog = true }

                        if (showDatePickerDialog) {
                            com.example.ui.components.CustomDatePickerDialog(
                                initialDateStr = inputTrxTanggal,
                                onDismissRequest = { showDatePickerDialog = false },
                                onDateSelected = { selectedDate ->
                                    inputTrxTanggal = selectedDate
                                    showDatePickerDialog = false
                                }
                            )
                        }

                        OutlinedTextField(
                            value = inputTrxTanggal,
                            onValueChange = { inputTrxTanggal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Tanggal Transaksi (YYYY-MM-DD)") },
                            placeholder = { Text("Contoh: 2026-07-25") },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Dari Kalender")
                                }
                            },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { 
                                showTrxFormPopup = false
                                editingTrx = null
                            }) {
                                Text("Batal")
                            }
                            Button(onClick = {
                                val nominalVal = inputTrxNominal.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (inputTrxJenis.isNotEmpty() && inputTrxKategori.isNotEmpty() && nominalVal > 0) {
                                    val tgl = if (inputTrxTanggal.isNotEmpty()) inputTrxTanggal else dateFormater.format(Date())
                                    val currentEditing = editingTrx
                                    if (currentEditing != null) {
                                        viewModel.updateSavingTransaction(
                                            currentEditing.copy(
                                                jenis = inputTrxJenis,
                                                kategori = inputTrxKategori,
                                                deskripsi = inputTrxDeskripsi,
                                                nominal = nominalVal,
                                                tanggal = tgl
                                            )
                                        )
                                    }
                                    showTrxFormPopup = false
                                    editingTrx = null
                                }
                            }) {
                                Text("Simpan Transaksi")
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG MENU LIST FILTER SEMUA CATATAN TABUNGAN
    if (showFilterMenuDialog) {
        Dialog(onDismissRequest = { showFilterMenuDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "Filter Catatan Tabungan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showFilterMenuDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 1. FILTER BERDASARKAN JENIS (PEMASUKAN / PENGELUARAN)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Filter Berdasarkan Jenis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Semua", "Masuk", "Keluar").forEach { type ->
                                val isSelected = selectedTypeFilter == type
                                val labelText = when (type) {
                                    "Masuk" -> "Pemasukan"
                                    "Keluar" -> "Pengeluaran"
                                    else -> "Semua Jenis"
                                }
                                val activeColor = when (type) {
                                    "Masuk" -> Color(0xFF2E7D32)
                                    "Keluar" -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Surface(
                                    onClick = { selectedTypeFilter = type },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, activeColor) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = labelText,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. FILTER BERDASARKAN KATEGORI
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Filter Berdasarkan Kategori",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showCategoryDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedCategoryFilter == "Semua") "Semua Kategori" else selectedCategoryFilter,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }

                            DropdownMenu(
                                expanded = showCategoryDropdown,
                                onDismissRequest = { showCategoryDropdown = false }
                            ) {
                                allDistinctCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, fontSize = 12.sp) },
                                        onClick = {
                                            selectedCategoryFilter = cat
                                            showCategoryDropdown = false
                                        },
                                        leadingIcon = if (selectedCategoryFilter == cat) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    // 3. FILTER BERDASARKAN TANGGAL
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Filter Berdasarkan Tanggal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = selectedDateFilter,
                            onValueChange = { selectedDateFilter = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("YYYY-MM-DD (Pilih dari kalender)", fontSize = 12.sp) },
                            leadingIcon = {
                                IconButton(onClick = { showFilterDatePickerDialog = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Kalender", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingIcon = {
                                if (selectedDateFilter.isNotEmpty()) {
                                    IconButton(onClick = { selectedDateFilter = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus Tanggal", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                    }

                    // Dialog Actions (Reset & Terapkan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedTypeFilter = "Semua"
                                selectedCategoryFilter = "Semua"
                                selectedDateFilter = ""
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Filter", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showFilterMenuDialog = false },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Terapkan", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showFilterDatePickerDialog) {
        com.example.ui.components.CustomDatePickerDialog(
            initialDateStr = selectedDateFilter,
            onDismissRequest = { showFilterDatePickerDialog = false },
            onDateSelected = { selectedDate ->
                selectedDateFilter = selectedDate
                showFilterDatePickerDialog = false
            }
        )
    }
}
