package com.example.ui.screens

import com.example.getAccountRef

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AdjustmentEntry
import com.example.JournalEntry
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdjustedTrialBalanceRow(
    val accountName: String,
    val initialDebit: Double,
    val initialCredit: Double,
    val adjustmentDebit: Double,
    val adjustmentCredit: Double,
    val adjustedDebit: Double,
    val adjustedCredit: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeracaSaldoDisesuaikanScreen(
    journalEntries: List<JournalEntry>,
    adjustmentEntries: List<AdjustmentEntry>,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

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

    val rows = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val list = mutableListOf<AdjustedTrialBalanceRow>()
        allUniqueAccounts.forEach { accName ->
            // A. Saldo Awal (Dari Jurnal Umum)
            val matchingDebits = journalEntries.filter { it.debetAkun == accName }
            val matchingCredits = journalEntries.filter { it.kreditAkun == accName }
            val debitSum = matchingDebits.sumOf { it.nominal }
            val creditSum = matchingCredits.sumOf { it.nominal }

            val normalType = getNormalBalanceTypeLocal(accName)
            var initialDebit = 0.0
            var initialCredit = 0.0

            if (normalType == "D") {
                val netSum = debitSum - creditSum
                if (netSum >= 0) initialDebit = netSum else initialCredit = kotlin.math.abs(netSum)
            } else {
                val netSum = creditSum - debitSum
                if (netSum >= 0) initialCredit = netSum else initialDebit = kotlin.math.abs(netSum)
            }

            // B. Penyesuaian
            val adjDebits = adjustmentEntries.filter { it.debetAkun == accName }
            val adjCredits = adjustmentEntries.filter { it.kreditAkun == accName }
            val adjustmentDebit = adjDebits.sumOf { it.nominal }
            val adjustmentCredit = adjCredits.sumOf { it.nominal }

            // C. Saldo Disesuaikan
            val totalDebitFlow = debitSum + adjustmentDebit
            val totalCreditFlow = creditSum + adjustmentCredit

            var adjustedDebit = 0.0
            var adjustedCredit = 0.0

            if (normalType == "D") {
                val netPercent = totalDebitFlow - totalCreditFlow
                if (netPercent >= 0) adjustedDebit = netPercent else adjustedCredit = kotlin.math.abs(netPercent)
            } else {
                val netPercent = totalCreditFlow - totalDebitFlow
                if (netPercent >= 0) adjustedCredit = netPercent else adjustedDebit = kotlin.math.abs(netPercent)
            }

            if (initialDebit > 0 || initialCredit > 0 || adjustmentDebit > 0 || adjustmentCredit > 0 || adjustedDebit > 0 || adjustedCredit > 0) {
                list.add(
                    AdjustedTrialBalanceRow(
                        accountName = accName,
                        initialDebit = initialDebit,
                        initialCredit = initialCredit,
                        adjustmentDebit = adjustmentDebit,
                        adjustmentCredit = adjustmentCredit,
                        adjustedDebit = adjustedDebit,
                        adjustedCredit = adjustedCredit
                    )
                )
            }
        }
        list
    }

    val filteredRows = remember(rows, searchQuery) {
        if (searchQuery.isBlank()) rows
        else rows.filter { it.accountName.contains(searchQuery, ignoreCase = true) }
    }

    val totalInitialDebit = rows.sumOf { it.initialDebit }
    val totalInitialCredit = rows.sumOf { it.initialCredit }
    val totalAdjDebit = rows.sumOf { it.adjustmentDebit }
    val totalAdjCredit = rows.sumOf { it.adjustmentCredit }
    val totalAdjustedDebit = rows.sumOf { it.adjustedDebit }
    val totalAdjustedCredit = rows.sumOf { it.adjustedCredit }
    val isBalanced = totalAdjustedDebit == totalAdjustedCredit && rows.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- HERO SUMMARY CARD ---
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
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Neraca Saldo Disesuaikan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${rows.size} Akun Terdaftar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBalanced) MaterialTheme.colorScheme.primary else if (rows.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = if (isBalanced) "SEIMBANG" else if (rows.isEmpty()) "KOSONG" else "SELISIH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBalanced) MaterialTheme.colorScheme.onPrimary else if (rows.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Disesuaikan Debet Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Debet Akhir", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatRupiah(totalAdjustedDebit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Total Disesuaikan Kredit Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Kredit Akhir", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatRupiah(totalAdjustedCredit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // --- SEARCH & PRINT BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama rekening...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            OutlinedButton(
                onClick = { printAdjustedTrialBalance(context, rows) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.testTag("print_neraca_saldo_disesuaikan")
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Cetak Laporan",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- TABEL DATA ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Headers Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(2.0f).padding(6.dp)) {
                                Text("Nama Rekening", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(0.8f).padding(6.dp)) {
                                Text("Ref", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.2f).padding(6.dp)) {
                                Text("Neraca Saldo", fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.2f).padding(6.dp)) {
                                Text("Penyesuaian", fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.5f).padding(6.dp)) {
                                Text("Disesuaikan", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(2.0f).padding(2.dp)) {
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(0.8f).padding(2.dp)) {
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            
                            // Neraca Saldo
                            Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Deb", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Kre", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            }
                            
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            
                            // Penyesuaian
                            Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Deb", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Kre", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
                            }
                            
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            
                            // Disesuaikan
                            Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Deb", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text("Kre", fontSize = 8.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                if (filteredRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(if (searchQuery.isNotEmpty()) "Tidak ada rekening yang cocok." else "Belum ada saldo akun.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredRows) { row ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(2.0f).padding(6.dp)) {
                                        Text(row.accountName, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    Box(modifier = Modifier.weight(0.8f).padding(6.dp)) {
                                        Text(getAccountRef(row.accountName), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Neraca Saldo
                                    Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.initialDebit > 0) formatMoneyShort(row.initialDebit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.initialCredit > 0) formatMoneyShort(row.initialCredit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }

                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Penyesuaian
                                    Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.adjustmentDebit > 0) formatMoneyShort(row.adjustmentDebit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, color = Color(0xFF388E3C), modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.adjustmentCredit > 0) formatMoneyShort(row.adjustmentCredit) else "-", fontSize = 9.sp, textAlign = TextAlign.End, color = Color(0xFFD32F2F), modifier = Modifier.fillMaxWidth())
                                    }

                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Disesuaikan
                                    Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.adjustedDebit > 0) formatMoneyShort(row.adjustedDebit) else "-", fontSize = 10.sp, textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                                        Text(if (row.adjustedCredit > 0) formatMoneyShort(row.adjustedCredit) else "-", fontSize = 10.sp, textAlign = TextAlign.End, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            }
                        }
                    }

                    // Total Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(2.5f).padding(6.dp)) {
                            Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                        // Neraca Saldo
                        Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalInitialDebit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalInitialCredit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }

                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                        // Penyesuaian
                        Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalAdjDebit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = Color(0xFF388E3C), modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalAdjCredit), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = Color(0xFFD32F2F), modifier = Modifier.fillMaxWidth())
                        }

                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                        // Disesuaikan
                        Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalAdjustedDebit), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.25f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                            Text(formatMoneyShort(totalAdjustedCredit), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

private fun getNormalBalanceTypeLocal(accountName: String): String {
    val creditKeywords = listOf("Utang", "Kewajiban", "Modal", "Pendapatan", "Sewa diterima", "Akumulasi")
    for (kw in creditKeywords) {
        if (accountName.contains(kw, ignoreCase = true)) {
            if (accountName.contains("Prive", ignoreCase = true)) return "D"
            if (accountName.contains("Piutang", ignoreCase = true)) return "D"
            if (accountName.contains("Beban", ignoreCase = true)) return "D"
            return "K"
        }
    }
    return "D"
}

private fun formatRupiah(value: Double): String {
    return try {
        "%,.0f".format(Locale.US, value).replace(",", ".")
    } catch (e: Exception) {
        "%.0f".format(value)
    }
}

private fun formatMoneyShort(value: Double): String {
    return try {
        if (value >= 1_000_000) {
            "%.1fJ".format(Locale.US, value / 1_000_000.0).replace(".", ",")
        } else if (value >= 1_000) {
            "%.0fR".format(Locale.US, value / 1_000.0)
        } else {
            "%.0f".format(value)
        }
    } catch (e: Exception) {
        "%.0f".format(value)
    }
}

private fun printAdjustedTrialBalance(
    context: Context,
    rows: List<AdjustedTrialBalanceRow>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Neraca Saldo Disesuaikan - My Singghara"

    val totalAdjustedD = rows.sumOf { it.adjustedDebit }
    val totalAdjustedC = rows.sumOf { it.adjustedCredit }
    val isBalanced = totalAdjustedD == totalAdjustedC

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 20px; border-bottom: 3px double #333; padding-bottom: 10px; text-align: center; }
                h1 { color: #111; margin: 0; font-size: 20px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { color: #555; margin: 5px 0 0 0; font-size: 13px; font-weight: normal; }
                .meta-time { color: #777; margin-top: 5px; font-size: 10px; }
                
                .status-container {
                    border: 1px solid ${if (isBalanced && rows.isNotEmpty()) "#c3e6cb" else "#f5c6cb"};
                    background-color: ${if (isBalanced && rows.isNotEmpty()) "#d4edda" else "#f8d7da"};
                    color: ${if (isBalanced && rows.isNotEmpty()) "#155724" else "#721c24"};
                    padding: 10px;
                    border-radius: 4px;
                    margin-bottom: 15px;
                    font-size: 12px;
                    text-align: center;
                    font-weight: bold;
                }
                
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; text-align: center; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #f9f9f9; border-top: 3px double #333; }
            </style>
        </head>
        <body>
            <header>
                <h1>Neraca Saldo Disesuaikan</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <div class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</div>
            </header>

            <div class="status-container">
                STATUS NERACA: ${if (isBalanced && rows.isNotEmpty()) "SEIMBANG (BALANCED)" else "TIDAK SEIMBANG / KOSONG"}<br/>
                Total Debit: Rp ${String.format("%,.0f", totalAdjustedD).replace(',', '.')} | Total Kredit: Rp ${String.format("%,.0f", totalAdjustedC).replace(',', '.')}
            </div>

            <table>
                <thead>
                    <tr>
                        <th rowspan="2">Nama Rekening</th>
                        <th rowspan="2" style="width: 8%;">Ref</th>
                        <th colspan="2">Neraca Saldo</th>
                        <th colspan="2">Penyesuaian</th>
                        <th colspan="2">Setelah Penyesuaian</th>
                    </tr>
                    <tr>
                        <th style="width: 13%;">Debit</th>
                        <th style="width: 13%;">Kredit</th>
                        <th style="width: 13%;">Debit</th>
                        <th style="width: 13%;">Kredit</th>
                        <th style="width: 13%;">Debit</th>
                        <th style="width: 13%;">Kredit</th>
                    </tr>
                </thead>
                <tbody>
    """)

    rows.forEach { row ->
        htmlBuilder.append("""
            <tr>
                <td class="bold">${row.accountName}</td>
                <td class="text-center bold" style="color: #555;">${getAccountRef(row.accountName)}</td>
                <td class="text-right">${if (row.initialDebit > 0) "Rp " + String.format("%,.0f", row.initialDebit).replace(',', '.') else "-"}</td>
                <td class="text-right">${if (row.initialCredit > 0) "Rp " + String.format("%,.0f", row.initialCredit).replace(',', '.') else "-"}</td>
                <td class="text-right" style="color: green;">${if (row.adjustmentDebit > 0) "Rp " + String.format("%,.0f", row.adjustmentDebit).replace(',', '.') else "-"}</td>
                <td class="text-right" style="color: red;">${if (row.adjustmentCredit > 0) "Rp " + String.format("%,.0f", row.adjustmentCredit).replace(',', '.') else "-"}</td>
                <td class="text-right" style="font-weight: bold; color: blue;">${if (row.adjustedDebit > 0) "Rp " + String.format("%,.0f", row.adjustedDebit).replace(',', '.') else "-"}</td>
                <td class="text-right" style="font-weight: bold; color: darkorange;">${if (row.adjustedCredit > 0) "Rp " + String.format("%,.0f", row.adjustedCredit).replace(',', '.') else "-"}</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="2">TOTAL:</td>
                        <td class="text-right">Rp ${String.format("%,.0f", rows.sumOf { it.initialDebit }).replace(',', '.')}</td>
                        <td class="text-right">Rp ${String.format("%,.0f", rows.sumOf { it.initialCredit }).replace(',', '.')}</td>
                        <td class="text-right" style="color: green;">Rp ${String.format("%,.0f", rows.sumOf { it.adjustmentDebit }).replace(',', '.')}</td>
                        <td class="text-right" style="color: red;">Rp ${String.format("%,.0f", rows.sumOf { it.adjustmentCredit }).replace(',', '.')}</td>
                        <td class="text-right" style="color: blue;">Rp ${String.format("%,.0f", totalAdjustedD).replace(',', '.')}</td>
                        <td class="text-right" style="color: darkorange;">Rp ${String.format("%,.0f", totalAdjustedC).replace(',', '.')}</td>
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
