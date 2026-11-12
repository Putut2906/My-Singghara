package com.example.ui.screens

import com.example.getAccountRef

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AdjustmentEntry
import com.example.JournalEntry
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JurnalPenyesuaianScreen(
    journalEntries: List<JournalEntry>,
    adjustmentEntries: List<AdjustmentEntry>,
    onSaveEntry: (AdjustmentEntry) -> Unit,
    onDeleteEntry: (Int) -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Jurnal Penyesuaian, 1: Neraca Setelah Penyesuaian

    // 1. PRA-HITUNG NERACA SALDO SEBELUM PENYESUAIAN
    val standardAccountsOrdered = listOf(
        "Kas",
        "Piutang Usaha", "Perlengkapan", "Sewa dibayar di muka", "Asuransi dibayar di muka",
        "Peralatan", "Akumulasi penyusutan peralatan", "Kendaraan", "Akumulasi penyusutan kendaraan",
        "Utang usaha", "Utang gaji", "Utang bank", "Pendapatan diterima di muka",
        "Modal pemilik", "Prive",
        "Pendapatan jasa", "Pendapatan penjualan", "Pendapatan lain-lain",
        "Harga Pokok Penjualan", "Beban gaji", "Beban sewa", "Beban perlengkapan", 
        "Beban asuransi", "Beban penyusutan peralatan", "Beban penyusutan kendaraan", 
        "Beban listrik air telepon", "Beban iklan", "Beban lain-lain"
    )

    val allUniqueAccounts = remember(journalEntries, adjustmentEntries) {
        val set = mutableSetOf<String>()
        journalEntries.forEach {
            set.add(it.debetAkun)
            set.add(it.kreditAkun)
        }
        adjustmentEntries.forEach {
            set.add(it.debetAkun)
            set.add(it.kreditAkun)
        }
        val customAccs = set.filter { !standardAccountsOrdered.contains(it) }.sorted()
        standardAccountsOrdered.filter { set.contains(it) } + customAccs
    }

    // Hitung baris neraca awal, penyesuaian, dan saldo akhir secara otomatis!
    data class AdjustedBalanceRow(
        val accountName: String,
        val initialDebit: Double,
        val initialCredit: Double,
        val adjustmentDebit: Double,
        val adjustmentCredit: Double,
        val finalDebit: Double,
        val finalCredit: Double
    )

    val adjustedBalanceRows = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val rows = mutableListOf<AdjustedBalanceRow>()

        allUniqueAccounts.forEach { accName ->
            // A. Saldo Awal (Dari Jurnal Umum)
            val matchingDebits = journalEntries.filter { it.debetAkun == accName }
            val matchingCredits = journalEntries.filter { it.kreditAkun == accName }
            val debitSum = matchingDebits.sumOf { it.nominal }
            val creditSum = matchingCredits.sumOf { it.nominal }

            val normalType = getNormalBalanceType(accName)
            var initialDebit = 0.0
            var initialCredit = 0.0

            if (normalType == "D") {
                val netSum = debitSum - creditSum
                if (netSum >= 0) initialDebit = netSum else initialCredit = kotlin.math.abs(netSum)
            } else {
                val netSum = creditSum - debitSum
                if (netSum >= 0) initialCredit = netSum else initialDebit = kotlin.math.abs(netSum)
            }

            // B. Nilai Penyesuaian (Dari Jurnal Penyesuaian)
            val adjDebits = adjustmentEntries.filter { it.debetAkun == accName }
            val adjCredits = adjustmentEntries.filter { it.kreditAkun == accName }
            val adjustmentDebit = adjDebits.sumOf { it.nominal }
            val adjustmentCredit = adjCredits.sumOf { it.nominal }

            // C. Saldo Akhir Setelah Penyesuaian
            // Total debit riil dan total kredit riil sebelum ditentukan posisi normal saldo akhirnya
            val totalDebitFlow = debitSum + adjustmentDebit
            val totalCreditFlow = creditSum + adjustmentCredit

            var finalDebit = 0.0
            var finalCredit = 0.0

            if (normalType == "D") {
                val netFinal = totalDebitFlow - totalCreditFlow
                if (netFinal >= 0) finalDebit = netFinal else finalCredit = kotlin.math.abs(netFinal)
            } else {
                val netFinal = totalCreditFlow - totalDebitFlow
                if (netFinal >= 0) finalCredit = netFinal else finalDebit = kotlin.math.abs(netFinal)
            }

            // Hanya tampilkan jika memiliki salah satu saldo tidak nol
            if (initialDebit > 0 || initialCredit > 0 || adjustmentDebit > 0 || adjustmentCredit > 0 || finalDebit > 0 || finalCredit > 0) {
                rows.add(
                    AdjustedBalanceRow(
                        accountName = accName,
                        initialDebit = initialDebit,
                        initialCredit = initialCredit,
                        adjustmentDebit = adjustmentDebit,
                        adjustmentCredit = adjustmentCredit,
                        finalDebit = finalDebit,
                        finalCredit = finalCredit
                    )
                )
            }
        }
        rows
    }

    val totalInitialDebit = adjustedBalanceRows.sumOf { it.initialDebit }
    val totalInitialCredit = adjustedBalanceRows.sumOf { it.initialCredit }
    val totalAdjDebit = adjustedBalanceRows.sumOf { it.adjustmentDebit }
    val totalAdjCredit = adjustedBalanceRows.sumOf { it.adjustmentCredit }
    val totalFinalDebit = adjustedBalanceRows.sumOf { it.finalDebit }
    val totalFinalCredit = adjustedBalanceRows.sumOf { it.finalCredit }

    // Dialog Input Penyesuaian Baru (Pop-up)
    if (showAddDialog) {
        DialogInputPenyesuaian(
            onDismiss = { showAddDialog = false },
            onSave = { entry ->
                onSaveEntry(entry)
                showAddDialog = false
            }
        )
    }

    // Dialog Konfirmasi Hapus Data
    if (itemToDeleteIndex != null) {
        AlertDialog(
            onDismissRequest = { itemToDeleteIndex = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hapus Penyesuaian",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                val entryUnderDelete = itemToDeleteIndex?.let { idx ->
                    if (idx >= 0 && idx < adjustmentEntries.size) adjustmentEntries[idx] else null
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Apakah Anda yakin ingin menghapus entri jurnal penyesuaian ini? Tindakan ini tidak dapat dibatalkan.", fontSize = 14.sp)
                    if (entryUnderDelete != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Jenis: ${entryUnderDelete.jenisPenyesuaian}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Debet: ${entryUnderDelete.debetAkun} -> Kredit: ${entryUnderDelete.kreditAkun}", fontSize = 11.sp)
                                Text("Nominal: Rp ${formatMoney(entryUnderDelete.nominal)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeleteIndex?.let { index ->
                            onDeleteEntry(index)
                        }
                        itemToDeleteIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteIndex = null }) {
                    Text("Batal")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- HERO SUMMARY CARD ---
        val totalAdjNominal = remember(adjustmentEntries) { adjustmentEntries.sumOf { it.nominal } }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Jurnal Penyesuaian",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${adjustmentEntries.size} Entri Penyesuaian Recorded",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Total Nominal Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Rp " + formatMoney(totalAdjNominal),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Action buttons inside Hero
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_penyesuaian_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Penyesuaian",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah Penyesuaian", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            if (selectedTab == 0) {
                                printJurnalPenyesuaian(context, adjustmentEntries)
                            } else {
                                printNeracaSetelahPenyesuaian(context, adjustedBalanceRows)
                            }
                        },
                        modifier = Modifier.testTag("print_penyesuaian_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Laporan",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- TAB SELECTOR (Material 3 TabRow) ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Entri Jurnal", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Neraca Setelah Penyesuaian", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        // --- CONTENT BASED ON SELECTED TAB ---
        if (selectedTab == 0) {
            // TAB 1: Jurnal Penyesuaian Logs Table
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Tabel
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                Text("Tanggal / Info", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3.8f).padding(8.dp)) {
                                Text("Akun Debet / Kredit", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.5f).padding(8.dp)) {
                                Text("Ref", fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                                Text("Nominal", fontWeight = FontWeight.Black, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.width(36.dp).padding(4.dp)) {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    if (adjustmentEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Belum ada entri penyesuaian.", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(adjustmentEntries.size) { index ->
                                val entry = adjustmentEntries[index]
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 1. Tanggal / Info
                                        Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                            Column {
                                                Text(entry.tanggal, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text(entry.jenisPenyesuaian, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                                if (entry.keterangan.isNotEmpty()) {
                                                    Text(entry.keterangan, fontSize = 9.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // 2. Akun Debit / Kredit
                                        Box(modifier = Modifier.weight(3.8f).padding(8.dp)) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Green)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(entry.debetAkun, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(modifier = Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Red)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(entry.kreditAkun, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Ref
                                        Box(modifier = Modifier.weight(1.5f).padding(8.dp)) {
                                            Column {
                                                Text(getAccountRef(entry.debetAkun), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(getAccountRef(entry.kreditAkun), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // 3. Nominal
                                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                                            Text(
                                                text = "Rp " + formatMoney(entry.nominal),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // 4. Tombol Hapus
                                        Box(modifier = Modifier.width(36.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = { itemToDeleteIndex = index },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus Penyesuaian",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                                }
                            }
                        }

                        // Footer Ringkasan Jurnal Penyesuaian
                        val totalAdj = adjustmentEntries.sumOf { it.nominal }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(7.5f).padding(8.dp)) {
                                Text("TOTAL PENYESUAIAN (BALANCE)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                                Text(
                                    text = "Rp " + formatMoney(totalAdj),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.width(36.dp).padding(4.dp)) {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 2: Neraca Lajur (Adjusted Trial Balance)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Tabel yang sangat informatif (Tiga lapis)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column {
                            // Row Utama Judul Kolom
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(2.5f).padding(8.dp)) {
                                    Text("Nama Rekening", fontWeight = FontWeight.Black, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                    Text("Neraca Saldo", fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                    Text("Penyesuaian", fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(2.5f).padding(8.dp)) {
                                    Text("Setelah Penyesuaian", fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            // Row Kolom Sub-Debit/Kredit
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(modifier = Modifier.weight(2.5f).padding(6.dp)) {
                                    Spacer(modifier = Modifier.fillMaxSize())
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                // Neraca Saldo Sub
                                Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                        Text("Deb", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                        Text("Kre", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                // Penyesuaian Sub
                                Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                        Text("Deb", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                        Text("Kre", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                // Setelah Penyesuaian Sub
                                Row(modifier = Modifier.weight(2.5f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                        Text("Deb", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                        Text("Kre", fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                    if (adjustedBalanceRows.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Belum ada saldo akun.", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(adjustedBalanceRows) { row ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Nama Akun
                                        Box(modifier = Modifier.weight(2.5f).padding(8.dp)) {
                                            Text(row.accountName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Neraca Saldo Awal
                                        Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                                Text(if (row.initialDebit > 0) formatMoneyShort(row.initialDebit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                            }
                                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                            Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                                Text(if (row.initialCredit > 0) formatMoneyShort(row.initialCredit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Penyesuaian
                                        Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                                Text(if (row.adjustmentDebit > 0) formatMoneyShort(row.adjustmentDebit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, color = Color(0xFF388E3C), fontWeight = if (row.adjustmentDebit > 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth())
                                            }
                                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                            Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                                Text(if (row.adjustmentCredit > 0) formatMoneyShort(row.adjustmentCredit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, color = Color(0xFFD32F2F), fontWeight = if (row.adjustmentCredit > 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Setelah Penyesuaian
                                        Row(modifier = Modifier.weight(2.5f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                                Text(if (row.finalDebit > 0) formatMoneyShort(row.finalDebit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                                            }
                                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                            Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                                Text(if (row.finalCredit > 0) formatMoneyShort(row.finalCredit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                                }
                            }
                        }

                        // Footer Total Neraca Lajur
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(2.5f).padding(8.dp)) {
                                Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 9.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                            // Total Saldo Awal
                            Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalInitialDebit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalInitialCredit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                            // Total Penyesuaian
                            Row(modifier = Modifier.weight(2.2f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalAdjDebit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = Color(0xFF388E3C), modifier = Modifier.fillMaxWidth())
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalAdjCredit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = Color(0xFFD32F2F), modifier = Modifier.fillMaxWidth())
                                }
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                            // Total Saldo Setelah Penyesuaian
                            Row(modifier = Modifier.weight(2.5f).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalFinalDebit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Box(modifier = Modifier.weight(1.1f).padding(6.dp)) {
                                    Text(formatMoneyShort(totalFinalCredit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FORMAT RUPIAH UTILITY SEMENTARA
private fun formatMoney(value: Double): String {
    return try {
        "%,.0f".format(Locale.US, value).replace(",", ".")
    } catch (e: Exception) {
        "%.0f".format(value)
    }
}

private fun formatMoneyShort(value: Double): String {
    return try {
        if (value >= 1_000_000) {
            "%.1fM".format(Locale.US, value / 1_000_000.0).replace(".", ",")
        } else if (value >= 1_000) {
            "%.0fK".format(Locale.US, value / 1_000.0)
        } else {
            "%.0f".format(value)
        }
    } catch (e: Exception) {
        "%.0f".format(value)
    }
}

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

// ---------------- DIALOG POPUP TRANSAKSI PENYESUAIAN BARU ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogInputPenyesuaian(
    onDismiss: () -> Unit,
    onSave: (AdjustmentEntry) -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var tanggalInput by remember { mutableStateOf(sdf.format(Date())) }
    var keteranganInput by remember { mutableStateOf("") }
    var nominalInput by remember { mutableStateOf("") }

    // Dropdown Jenis Penyesuaian
    val jenisOptions = listOf(
        "Beban dibayar dimuka",
        "Pendapatan diterima dimuka",
        "Penyusutan aset tetap",
        "Beban masih harus dibayar (akrual beban)",
        "Pendapatan masih harus diterima (akrual pendapatan)",
        "Perlengkapan terpakai"
    )
    var selectedJenis by remember { mutableStateOf(jenisOptions[0]) }
    var isJenisExpanded by remember { mutableStateOf(false) }

    // Akun Debit Options (beban sewa, beban asuransi, beban perlengkapan dll)
    val debetOptions = listOf(
        "Beban sewa",
        "Beban asuransi",
        "Beban penyusutan peralatan",
        "Beban penyusutan kendaraan",
        "Beban perlengkapan",
        "Beban gaji",
        "Piutang pendapatan",
        "Beban lain-lain"
    )
    var selectedDebet by remember { mutableStateOf(debetOptions[0]) }
    var customDebetAccount by remember { mutableStateOf("") }
    var isDebetExpanded by remember { mutableStateOf(false) }
    var useCustomDebet by remember { mutableStateOf(false) }

    // Akun Kredit Options (sewa dibayar di muka, asuransi dibayar di muka, dll)
    val kreditOptions = listOf(
        "Sewa dibayar di muka",
        "Asuransi dibayar di muka",
        "Akumulasi penyusutan peralatan",
        "Akumulasi penyusutan kendaraan",
        "Perlengkapan",
        "Utang gaji",
        "Utang beban",
        "Pendapatan diterima di muka",
        "Pendapatan jasa",
        "Pendapatan lain-lain"
    )
    var selectedKredit by remember { mutableStateOf(kreditOptions[0]) }
    var customKreditAccount by remember { mutableStateOf("") }
    var isKreditExpanded by remember { mutableStateOf(false) }
    var useCustomKredit by remember { mutableStateOf(false) }

    // Otomatis menyesuaikan Akun secara logis saat Jenis Penyesuaian berubah!
    LaunchedEffect(selectedJenis) {
        when (selectedJenis) {
            "Beban dibayar dimuka" -> {
                selectedDebet = "Beban sewa"
                selectedKredit = "Sewa dibayar di muka"
                useCustomDebet = false
                useCustomKredit = false
            }
            "Pendapatan diterima dimuka" -> {
                selectedDebet = "Pendapatan diterima di muka"
                selectedKredit = "Pendapatan jasa"
                useCustomDebet = false
                useCustomKredit = false
            }
            "Penyusutan aset tetap" -> {
                selectedDebet = "Beban penyusutan peralatan"
                selectedKredit = "Akumulasi penyusutan peralatan"
                useCustomDebet = false
                useCustomKredit = false
            }
            "Beban masih harus dibayar (akrual beban)" -> {
                selectedDebet = "Beban gaji"
                selectedKredit = "Utang gaji"
                useCustomDebet = false
                useCustomKredit = false
            }
            "Pendapatan masih harus diterima (akrual pendapatan)" -> {
                selectedDebet = "Piutang pendapatan"
                selectedKredit = "Pendapatan jasa"
                useCustomDebet = false
                useCustomKredit = false
            }
            "Perlengkapan terpakai" -> {
                selectedDebet = "Beban perlengkapan"
                selectedKredit = "Perlengkapan"
                useCustomDebet = false
                useCustomKredit = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EditCalendar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tambah Jurnal Penyesuaian", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. TANGGAL INPUT (Dengan CustomDatePickerDialog)
                var showDatePickerDialog by remember { mutableStateOf(false) }
                val showDatePicker = { showDatePickerDialog = true }

                if (showDatePickerDialog) {
                    com.example.ui.components.CustomDatePickerDialog(
                        initialDateStr = tanggalInput,
                        onDismissRequest = { showDatePickerDialog = false },
                        onDateSelected = { selectedDate ->
                            tanggalInput = selectedDate
                            showDatePickerDialog = false
                        }
                    )
                }

                OutlinedTextField(
                    value = tanggalInput,
                    onValueChange = { tanggalInput = it },
                    label = { Text("Tanggal Transaksi (YYYY-MM-DD)") },
                    placeholder = { Text("Contoh: 2026-07-25") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Dari Kalender")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // 2. JENIS PENYESUAIAN DROPDOWN
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedJenis,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Jenis Penyesuaian") },
                        trailingIcon = {
                            IconButton(onClick = { isJenisExpanded = !isJenisExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { isJenisExpanded = !isJenisExpanded },
                        shape = RoundedCornerShape(10.dp)
                    )
                    DropdownMenu(
                        expanded = isJenisExpanded,
                        onDismissRequest = { isJenisExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        jenisOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 12.sp) },
                                onClick = {
                                    selectedJenis = option
                                    isJenisExpanded = false
                                }
                            )
                        }
                    }
                }

                // 3. AKUN DEBIT (With Option for manual entry)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akun Debit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useCustomDebet,
                                onCheckedChange = { useCustomDebet = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Akun Kustom", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (useCustomDebet) {
                        OutlinedTextField(
                            value = customDebetAccount,
                            onValueChange = { customDebetAccount = it },
                            placeholder = { Text("Contoh: Beban Operasional Lain") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedDebet,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { isDebetExpanded = !isDebetExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { isDebetExpanded = !isDebetExpanded },
                                shape = RoundedCornerShape(10.dp)
                            )
                            DropdownMenu(
                                expanded = isDebetExpanded,
                                onDismissRequest = { isDebetExpanded = false }
                            ) {
                                debetOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedDebet = option
                                            isDebetExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. AKUN KREDIT (With Option for manual entry)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akun Kredit", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useCustomKredit,
                                onCheckedChange = { useCustomKredit = it },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Akun Kustom", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (useCustomKredit) {
                        OutlinedTextField(
                            value = customKreditAccount,
                            onValueChange = { customKreditAccount = it },
                            placeholder = { Text("Contoh: Pendapatan Ditangguhkan") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedKredit,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { isKreditExpanded = !isKreditExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().clickable { isKreditExpanded = !isKreditExpanded },
                                shape = RoundedCornerShape(10.dp)
                            )
                            DropdownMenu(
                                expanded = isKreditExpanded,
                                onDismissRequest = { isKreditExpanded = false }
                            ) {
                                kreditOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedKredit = option
                                            isKreditExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. KETERANGAN TEXT FIELD
                OutlinedTextField(
                    value = keteranganInput,
                    onValueChange = { keteranganInput = it },
                    label = { Text("Keterangan") },
                    placeholder = { Text("Masukkan rincian keterangan penyesuaian") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // 6. NOMINAL JUMLAH INPUT
                OutlinedTextField(
                    value = nominalInput,
                    onValueChange = { nominalInput = it },
                    label = { Text("Nominal Penyesuaian (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("Contoh: 500000") },
                    leadingIcon = { Text("Rp ", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominal = nominalInput.toDoubleOrNull() ?: 0.0
                    if (nominal <= 0.0) {
                        MainViewModel.instance?.showToastPopup("Nominal harus lebih dari 0!")
                        return@Button
                    }
                    val debetAcc = if (useCustomDebet) customDebetAccount.trim() else selectedDebet
                    val kreditAcc = if (useCustomKredit) customKreditAccount.trim() else selectedKredit

                    if (debetAcc.isEmpty()) {
                        MainViewModel.instance?.showToastPopup("Akun debit tidak boleh kosong!")
                        return@Button
                    }
                    if (kreditAcc.isEmpty()) {
                        MainViewModel.instance?.showToastPopup("Akun kredit tidak boleh kosong!")
                        return@Button
                    }

                    val finalKeterangan = if (keteranganInput.trim().isEmpty()) {
                        "Penyesuaian $selectedJenis"
                    } else {
                        keteranganInput.trim()
                    }

                    onSave(
                        AdjustmentEntry(
                            tanggal = tanggalInput.ifEmpty { sdf.format(Date()) },
                            jenisPenyesuaian = selectedJenis,
                            debetAkun = debetAcc,
                            kreditAkun = kreditAcc,
                            keterangan = finalKeterangan,
                            nominal = nominal
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ---------------- PRINT TEMPLATE UTILITIES (STYLE: TABUNGAN & BELANJA) ----------------
private fun printJurnalPenyesuaian(context: Context, entries: List<AdjustmentEntry>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Jurnal Penyesuaian - My Singghara"

    val totalAdj = entries.sumOf { it.nominal }

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; color: #333; }
                h1 { text-align: center; color: #111; margin-bottom: 5px; font-size: 20px; text-transform: uppercase; }
                p { text-align: center; color: #666; margin-top: 5px; font-size: 11px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 11px; }
                th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }
                th { background-color: #f7f7f7; font-weight: bold; text-transform: uppercase; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fafafa; border-top: 2px solid #555; }
                .debit-bullet { color: #28a745; font-weight: bold; }
                .credit-bullet { color: #dc3545; font-weight: bold; margin-left: 15px; }
            </style>
        </head>
        <body>
            <h1>Jurnal Penyesuaian</h1>
            <p><strong>My Singghara - Sistem Keuangan Otomatis</strong></p>
            <p>Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            <table>
                <thead>
                    <tr>
                        <th style="width: 8%;">#</th>
                        <th style="width: 14%;">Tanggal</th>
                        <th style="width: 38%;">Akun & Detail Penyesuaian</th>
                        <th style="width: 15%;" class="text-center">Ref</th>
                        <th style="width: 25%;" class="text-right">Nominal</th>
                    </tr>
                </thead>
                <tbody>
    """)

    entries.forEachIndexed { index, item ->
        htmlBuilder.append("""
            <tr>
                <td class="text-center">${index + 1}</td>
                <td>${item.tanggal}</td>
                <td>
                    <span class="debit-bullet">(&bull;) ${item.debetAkun}</span><br/>
                    <span class="credit-bullet">(&bull;) ${item.kreditAkun}</span><br/>
                    <small style="color: #666; font-size: 9px; margin-left: 15px;">Jenis: ${item.jenisPenyesuaian} | ${item.keterangan}</small>
                </td>
                <td class="text-center bold" style="color: #555; font-size: 10px;">
                    (D) ${getAccountRef(item.debetAkun)}<br/>
                    (K) ${getAccountRef(item.kreditAkun)}
                </td>
                <td class="text-right" style="font-weight: bold; color: #333;">
                    Rp ${String.format("%,.0f", item.nominal).replace(',', '.')}
                </td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="4" class="text-right">TOTAL JURNAL PENYESUAIAN:</td>
                        <td class="text-right" style="color: #007bff; font-weight: bold; font-size: 12px;">
                            Rp ${String.format("%,.0f", totalAdj).replace(',', '.')}
                        </td>
                    </tr>
                </tbody>
            </table>
        </body>
        </html>
    """)

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            val printAttributes = PrintAttributes.Builder().build()
            printManager.print(jobName, printAdapter, printAttributes)
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
}

private fun printNeracaSetelahPenyesuaian(context: Context, rows: List<Any>) {
    // Kami re-use fungsi cetak dengan cast yang aman atau kalkulasi dinamis
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Neraca Saldo Setelah Penyesuaian - My Singghara"

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; color: #333; }
                h1 { text-align: center; color: #111; margin-bottom: 5px; font-size: 18px; text-transform: uppercase; }
                p { text-align: center; color: #666; margin-top: 5px; font-size: 11px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 10px; }
                th, td { border: 1px solid #ddd; padding: 6px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-align: center; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #fafafa; border-top: 2px solid #555; }
            </style>
        </head>
        <body>
            <h1>Neraca Saldo Setelah Penyesuaian</h1>
            <p><strong>My Singghara - Laporan Neraca Lajur Terintegrasi</strong></p>
            <p>Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            <table>
                <thead>
                    <tr>
                        <th rowspan="2" style="width: 22%;">NAMA AKUN</th>
                        <th rowspan="2" style="width: 8%;">REF</th>
                        <th colspan="2">NERACA SALDO SEBELUMNYA</th>
                        <th colspan="2">PENYESUAIAN KIRIMAN</th>
                        <th colspan="2">SETELAH PENYESUAIAN</th>
                    </tr>
                    <tr>
                        <th style="width: 11.5%;">DEB</th>
                        <th style="width: 11.5%;">KRE</th>
                        <th style="width: 11.5%;">DEB</th>
                        <th style="width: 11.5%;">KRE</th>
                        <th style="width: 11.5%;">DEB</th>
                        <th style="width: 11.5%;">KRE</th>
                    </tr>
                </thead>
                <tbody>
    """)

    var totalInitialD = 0.0
    var totalInitialC = 0.0
    var totalAdjD = 0.0
    var totalAdjC = 0.0
    var totalFinalD = 0.0
    var totalFinalC = 0.0

    // Cast ke list baris dynamic penyesuaian untuk diprint
    rows.forEach { r ->
        try {
            val f = r.javaClass.getDeclaredField("accountName")
            f.isAccessible = true
            val accName = f.get(r) as String

            val fid = r.javaClass.getDeclaredField("initialDebit")
            fid.isAccessible = true
            val initialDebit = fid.get(r) as Double

            val fic = r.javaClass.getDeclaredField("initialCredit")
            fic.isAccessible = true
            val initialCredit = fic.get(r) as Double

            val fad = r.javaClass.getDeclaredField("adjustmentDebit")
            fad.isAccessible = true
            val adjustmentDebit = fad.get(r) as Double

            val fac = r.javaClass.getDeclaredField("adjustmentCredit")
            fac.isAccessible = true
            val adjustmentCredit = fac.get(r) as Double

            val ffd = r.javaClass.getDeclaredField("finalDebit")
            ffd.isAccessible = true
            val finalDebit = ffd.get(r) as Double

            val ffc = r.javaClass.getDeclaredField("finalCredit")
            ffc.isAccessible = true
            val finalCredit = ffc.get(r) as Double

            totalInitialD += initialDebit
            totalInitialC += initialCredit
            totalAdjD += adjustmentDebit
            totalAdjC += adjustmentCredit
            totalFinalD += finalDebit
            totalFinalC += finalCredit

            htmlBuilder.append("""
                <tr>
                    <td class="bold">$accName</td>
                    <td class="text-center bold" style="color: #555;">${getAccountRef(accName)}</td>
                    <td class="text-right">${if (initialDebit > 0) "Rp " + String.format("%,.0f", initialDebit).replace(',', '.') else "-"}</td>
                    <td class="text-right">${if (initialCredit > 0) "Rp " + String.format("%,.0f", initialCredit).replace(',', '.') else "-"}</td>
                    <td class="text-right" style="color: green;">${if (adjustmentDebit > 0) "Rp " + String.format("%,.0f", adjustmentDebit).replace(',', '.') else "-"}</td>
                    <td class="text-right" style="color: red;">${if (adjustmentCredit > 0) "Rp " + String.format("%,.0f", adjustmentCredit).replace(',', '.') else "-"}</td>
                    <td class="text-right" style="font-weight: bold; color: blue;">${if (finalDebit > 0) "Rp " + String.format("%,.0f", finalDebit).replace(',', '.') else "-"}</td>
                    <td class="text-right" style="font-weight: bold; color: darkorange;">${if (finalCredit > 0) "Rp " + String.format("%,.0f", finalCredit).replace(',', '.') else "-"}</td>
                </tr>
            """)
        } catch (e: Exception) {
            // Abaikan jika error cast
        }
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="2">TOTAL RINGKASAN:</td>
                        <td class="text-right">Rp ${String.format("%,.0f", totalInitialD).replace(',', '.')}</td>
                        <td class="text-right">Rp ${String.format("%,.0f", totalInitialC).replace(',', '.')}</td>
                        <td class="text-right" style="color: green;">Rp ${String.format("%,.0f", totalAdjD).replace(',', '.')}</td>
                        <td class="text-right" style="color: red;">Rp ${String.format("%,.0f", totalAdjC).replace(',', '.')}</td>
                        <td class="text-right" style="color: blue;">Rp ${String.format("%,.0f", totalFinalD).replace(',', '.')}</td>
                        <td class="text-right" style="color: darkorange;">Rp ${String.format("%,.0f", totalFinalC).replace(',', '.')}</td>
                    </tr>
                </tbody>
            </table>
        </body>
        </html>
    """)

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            val printAttributes = PrintAttributes.Builder().build()
            printManager.print(jobName, printAdapter, printAttributes)
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
}
