package com.example.ui.screens

import com.example.getAccountRef

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import android.content.Context
import android.print.PrintManager
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.JournalEntry
import com.example.ui.MainViewModel

data class LedgerItem(
    val tanggal: String,
    val keterangan: String,
    val debit: Double,
    val kredit: Double,
    val runningBalance: Double,
    val runningBalanceType: String // "D" or "K"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BukuBesarScreen(
    journalEntries: List<JournalEntry>,
    viewModel: MainViewModel
) {
    var searchQuery by remember { mutableStateOf("") }

    // Kita kumpulkan semua akun unik yang digunakan dalam transaksi
    val accountsWithTransactions = remember(journalEntries) {
        val set = mutableSetOf<String>()
        journalEntries.forEach {
            set.add(it.debetAkun)
            set.add(it.kreditAkun)
        }
        set.toList().sorted()
    }

    var selectedAccount by remember(accountsWithTransactions) {
        mutableStateOf(accountsWithTransactions.firstOrNull() ?: "")
    }

    // Jika belum ada akun, kita tampilkan empty state
    if (accountsWithTransactions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Buku Besar Kosong",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Silakan masukkan transaksi terlebih dahulu di menu Jurnal Umum agar data buku besar terisi secara otomatis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Hitung seluruh entri buku besar untuk akun terpilih
    val ledgerItems = remember(journalEntries, selectedAccount) {
        if (selectedAccount.isEmpty()) return@remember emptyList<LedgerItem>()
        
        // Saring transaksi yang melibatkan akun terpilih, urutkan berdasarkan tanggal
        val matchingJournals = journalEntries.filter {
            it.debetAkun == selectedAccount || it.kreditAkun == selectedAccount
        }.sortedBy { it.tanggal }

        val items = mutableListOf<LedgerItem>()
        var currentBal = 0.0
        val normalType = getNormalBalanceType(selectedAccount)

        matchingJournals.forEach { entry ->
            val isDebet = entry.debetAkun == selectedAccount
            val debitAmt = if (isDebet) entry.nominal else 0.0
            val kreditAmt = if (!isDebet) entry.nominal else 0.0

            if (normalType == "D") {
                currentBal += (debitAmt - kreditAmt)
            } else {
                currentBal += (kreditAmt - debitAmt)
            }

            items.add(
                LedgerItem(
                    tanggal = entry.tanggal,
                    keterangan = entry.keterangan,
                    debit = debitAmt,
                    kredit = kreditAmt,
                    runningBalance = currentBal,
                    runningBalanceType = normalType
                )
            )
        }
        items
    }

    val filteredLedgerItems = remember(ledgerItems, searchQuery) {
        if (searchQuery.isBlank()) ledgerItems
        else ledgerItems.filter {
            it.keterangan.contains(searchQuery, ignoreCase = true) ||
            it.tanggal.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalDebit = ledgerItems.sumOf { it.debit }
    val totalKredit = ledgerItems.sumOf { it.kredit }
    val finalBalance = if (ledgerItems.isNotEmpty()) ledgerItems.last().runningBalance else 0.0
    val finalBalanceType = getNormalBalanceType(selectedAccount)

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
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Buku Besar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Akun: $selectedAccount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Saldo Normal Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (finalBalanceType == "D") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = "Saldo Normal: " + (if (finalBalanceType == "D") "Debet" else "Kredit"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Debit
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Debet", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Rp " + formatMoney(totalDebit),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Total Kredit
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Kredit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Rp " + formatMoney(totalKredit),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Saldo Akhir
                    Surface(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Saldo Akhir ($finalBalanceType)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Rp " + formatMoney(finalBalance),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (finalBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // --- ACCOUNT SELECTION CHIPS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accountsWithTransactions.forEach { acc ->
                val isSelected = acc == selectedAccount
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedAccount = acc },
                    label = { 
                        Text(
                            text = acc,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // --- SEARCH & PRINT BAR ---
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari keterangan...", fontSize = 12.sp) },
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
                onClick = { printBukuBesar(context, selectedAccount, ledgerItems) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.testTag("print_buku_besar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Cetak Buku Besar",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- TABEL BUKU BESAR DETAIL ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table Headers Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.1f).padding(8.dp)) {
                            Text(
                                text = "Tanggal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.3f).padding(8.dp)) {
                            Text(
                                text = "Keterangan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(0.9f).padding(8.dp)) {
                            Text(
                                text = "Ref",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.7f).padding(8.dp)) {
                            Text(
                                text = "Debet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.7f).padding(8.dp)) {
                            Text(
                                text = "Kredit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                            Text(
                                text = "Saldo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                if (filteredLedgerItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Tidak ada entri transaksi yang cocok." else "Belum ada entri transaksi untuk akun ini.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredLedgerItems) { item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Tanggal
                                    Box(modifier = Modifier.weight(1.1f).padding(8.dp)) {
                                        Text(
                                            text = item.tanggal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    
                                    // Keterangan
                                    Box(modifier = Modifier.weight(2.3f).padding(8.dp)) {
                                        Text(
                                            text = item.keterangan,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Ref
                                    Box(modifier = Modifier.weight(0.9f).padding(8.dp)) {
                                        Text(
                                            text = getAccountRef(selectedAccount),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Debet
                                    Box(modifier = Modifier.weight(1.7f).padding(8.dp)) {
                                        Text(
                                            text = if (item.debit > 0) formatMoney(item.debit) else "-",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = if (item.debit > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (item.debit > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Kredit
                                    Box(modifier = Modifier.weight(1.8f).padding(8.dp)) {
                                        Text(
                                            text = if (item.kredit > 0) formatMoney(item.kredit) else "-",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = if (item.kredit > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (item.kredit > 0) MaterialTheme.colorScheme.tertiary else Color.Gray
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Running Balance
                                    Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                        Text(
                                            text = formatMoney(item.runningBalance),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Deteksi Tipe Saldo Normal
fun getNormalBalanceType(accountName: String): String {
    val lower = accountName.lowercase()
    if (lower.contains("akumulasi penyusutan")) {
        return "K"
    }
    if (lower == "kas" ||
        lower.contains("piutang") ||
        lower.contains("perlengkapan") ||
        lower.contains("dibayar di muka") ||
        lower.contains("peralatan") ||
        lower.contains("kendaraan") ||
        lower.contains("beban") ||
        lower.contains("harga pokok penjualan") ||
        lower == "prive"
    ) {
        return "D"
    }
    return "K"
}

private fun formatMoney(value: Double): String {
    return try {
        "%,.0f".format(value).replace(",", ".")
    } catch (e: Exception) {
        value.toString()
    }
}

private fun printBukuBesar(
    context: Context,
    accountName: String,
    items: List<LedgerItem>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Buku Besar $accountName - My Singghara"

    val totalDebit = items.sumOf { it.debit }
    val totalKredit = items.sumOf { it.kredit }
    val finalBalance = if (items.isNotEmpty()) items.last().runningBalance else 0.0
    val normalType = getNormalBalanceType(accountName)
    val normalTypeName = if (normalType == "D") "Debet" else "Kredit"

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 20px; border-bottom: 3px double #333; padding-bottom: 10px; }
                h1 { text-align: center; color: #111; margin: 0; font-size: 20px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { text-align: center; margin: 5px 0 0 0; font-size: 14px; font-weight: bold; color: #0056b3; }
                .meta-time { text-align: center; color: #777; margin-top: 5px; font-size: 10px; }
                .summary-container {
                    border: 1px solid #ccc;
                    background-color: #fcfcfc;
                    padding: 12px;
                    border-radius: 6px;
                    margin-bottom: 15px;
                    font-size: 11px;
                }
                .summary-table {
                    width: 100%;
                    border: none;
                    margin: 0;
                }
                .summary-table td {
                    border: none;
                    padding: 4px;
                }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #fdfdfd; }
            </style>
        </head>
        <body>
            <header>
                <h1>Buku Besar Akun</h1>
                <h2>Akun: $accountName (${if (normalType == "D") "Saldo Normal: Debet" else "Saldo Normal: Kredit"})</h2>
                <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            </header>

            <div class="summary-container">
                <table class="summary-table">
                    <tr>
                        <td style="width: 20%;" class="bold">Nama Akun:</td>
                        <td style="width: 30%;">$accountName (Ref: ${getAccountRef(accountName)})</td>
                        <td style="width: 25%;" class="bold text-right">Total Debit:</td>
                        <td style="width: 25%; color: green;" class="bold text-right">Rp ${String.format(Locale.US, "%,.0f", totalDebit).replace(',', '.')}</td>
                    </tr>
                    <tr>
                        <td class="bold">Tipe Saldo Normal:</td>
                        <td>$normalTypeName</td>
                        <td class="bold text-right">Total Kredit:</td>
                        <td style="color: red;" class="bold text-right">Rp ${String.format(Locale.US, "%,.0f", totalKredit).replace(',', '.')}</td>
                    </tr>
                    <tr style="border-top: 1px solid #ddd;">
                        <td colspan="2"></td>
                        <td class="bold text-right" style="font-size: 12px; padding-top: 8px;">Saldo Akhir ($normalType):</td>
                        <td class="bold text-right" style="font-size: 12px; padding-top: 8px; color: #0056b3;">Rp ${String.format(Locale.US, "%,.0f", finalBalance).replace(',', '.')}</td>
                    </tr>
                </table>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 14%;">TANGGAL</th>
                        <th style="width: 36%;">KETERANGAN</th>
                        <th style="width: 10%;" class="text-center">REF</th>
                        <th style="width: 13%;" class="text-right">DEBET</th>
                        <th style="width: 13%;" class="text-right">KREDIT</th>
                        <th style="width: 14%;" class="text-right">SALDO</th>
                    </tr>
                </thead>
                <tbody>
    """)

    items.forEach { item ->
        htmlBuilder.append("""
            <tr>
                <td class="text-center">${item.tanggal}</td>
                <td>${item.keterangan}</td>
                <td class="text-center font-bold" style="color: #555;">${getAccountRef(accountName)}</td>
                <td class="text-right" style="color: ${if (item.debit > 0) "green" else "#777"}; font-weight: ${if (item.debit > 0) "bold" else "normal"};">
                    ${if (item.debit > 0) "Rp " + String.format(Locale.US, "%,.0f", item.debit).replace(',', '.') else "-"}
                </td>
                <td class="text-right" style="color: ${if (item.kredit > 0) "red" else "#777"}; font-weight: ${if (item.kredit > 0) "bold" else "normal"};">
                    ${if (item.kredit > 0) "Rp " + String.format(Locale.US, "%,.0f", item.kredit).replace(',', '.') else "-"}
                </td>
                <td class="text-right bold" style="color: #0056b3;">
                    Rp ${String.format(Locale.US, "%,.0f", item.runningBalance).replace(',', '.')}
                </td>
            </tr>
        """)
    }

    htmlBuilder.append("""
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
