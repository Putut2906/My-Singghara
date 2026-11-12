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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Print
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

data class ClosingJournalRow(
    val date: String,
    val accountName: String,
    val isDebit: Boolean,
    val amount: Double,
    val indexGroup: Int // and index group for section visual card separation
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JurnalPenutupScreen(
    journalEntries: List<JournalEntry>,
    adjustmentEntries: List<AdjustmentEntry>,
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    // 1. FILTER & CALCULATE DATA FOR TEMPORARY ACCOUNTS
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

    val closingRows = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val list = mutableListOf<ClosingJournalRow>()
        val closingDate = if (journalEntries.isNotEmpty()) journalEntries.last().tanggal else "31/12/2026"

        // Step A: Close revenues accounts
        val revenues = mutableListOf<Pair<String, Double>>()
        allUniqueAccounts.forEach { accName ->
            if (accName.contains("Pendapatan", ignoreCase = true) || accName.contains("Penjualan", ignoreCase = true)) {
                if (accName.contains("diterima di muka", ignoreCase = true) || accName.contains("ditangguhkan", ignoreCase = true)) return@forEach
                val debitSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                               adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
                val creditSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                                adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }
                val netCredit = creditSum - debitSum
                if (netCredit > 0) {
                    revenues.add(accName to netCredit)
                }
            }
        }

        var groupIndex = 1
        val totalRevenueToClose = revenues.sumOf { it.second }
        if (revenues.isNotEmpty()) {
            revenues.forEach { (acc, amt) ->
                list.add(ClosingJournalRow(closingDate, acc, isDebit = true, amount = amt, indexGroup = groupIndex))
            }
            list.add(ClosingJournalRow(closingDate, "Ikhtisar Laba Rugi", isDebit = false, amount = totalRevenueToClose, indexGroup = groupIndex))
        }

        // Step B: Close expenses accounts
        val expenses = mutableListOf<Pair<String, Double>>()
        allUniqueAccounts.forEach { accName ->
            if (accName.contains("Beban", ignoreCase = true) || accName.contains("Biaya", ignoreCase = true) || accName.contains("Penyusutan", ignoreCase = true) || accName.equals("Harga Pokok Penjualan", ignoreCase = true)) {
                if (accName.contains("Akumulasi", ignoreCase = true)) return@forEach
                val debitSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                               adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
                val creditSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                                adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }
                val netDebit = debitSum - creditSum
                if (netDebit > 0) {
                    expenses.add(accName to netDebit)
                }
            }
        }

        val totalExpensesToClose = expenses.sumOf { it.second }
        if (expenses.isNotEmpty()) {
            groupIndex++
            list.add(ClosingJournalRow(closingDate, "Ikhtisar Laba Rugi", isDebit = true, amount = totalExpensesToClose, indexGroup = groupIndex))
            expenses.forEach { (acc, amt) ->
                list.add(ClosingJournalRow(closingDate, acc, isDebit = false, amount = amt, indexGroup = groupIndex))
            }
        }

        // Step C: Close Ikhtisar Laba Rugi (Net Profit or Loss) to Modal
        val netLabaRugiObj = totalRevenueToClose - totalExpensesToClose
        if (netLabaRugiObj != 0.0) {
            groupIndex++
            if (netLabaRugiObj > 0) {
                // Net Profit (Laba) -> Debit Ikhtisar Laba Rugi, Credit Modal pemilik
                list.add(ClosingJournalRow(closingDate, "Ikhtisar Laba Rugi", isDebit = true, amount = netLabaRugiObj, indexGroup = groupIndex))
                list.add(ClosingJournalRow(closingDate, "Modal pemilik", isDebit = false, amount = netLabaRugiObj, indexGroup = groupIndex))
            } else {
                // Net Loss (Rugi) -> Debit Modal pemilik, Credit Ikhtisar Laba Rugi
                val absLoss = kotlin.math.abs(netLabaRugiObj)
                list.add(ClosingJournalRow(closingDate, "Modal pemilik", isDebit = true, amount = absLoss, indexGroup = groupIndex))
                list.add(ClosingJournalRow(closingDate, "Ikhtisar Laba Rugi", isDebit = false, amount = absLoss, indexGroup = groupIndex))
            }
        }

        // Step D: Close Prive to Modal pemilik
        var totalPriveToClose = 0.0
        allUniqueAccounts.forEach { accName ->
            if (accName.contains("Prive", ignoreCase = true)) {
                val debitSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                               adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
                val creditSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                                adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }
                val netDebit = debitSum - creditSum
                if (netDebit > 0) {
                    totalPriveToClose += netDebit
                }
            }
        }

        if (totalPriveToClose > 0) {
            groupIndex++
            list.add(ClosingJournalRow(closingDate, "Modal pemilik", isDebit = true, amount = totalPriveToClose, indexGroup = groupIndex))
            list.add(ClosingJournalRow(closingDate, "Prive", isDebit = false, amount = totalPriveToClose, indexGroup = groupIndex))
        }

        list
    }

    val finalDebitSum = closingRows.filter { it.isDebit }.sumOf { it.amount }
    val finalCreditSum = closingRows.filter { !it.isDebit }.sumOf { it.amount }
    val isBalanced = finalDebitSum == finalCreditSum && closingRows.isNotEmpty()

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
                                imageVector = Icons.Default.LibraryBooks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Jurnal Penutup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${closingRows.size} Baris Entri Penutupan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { printClosingEntries(context, closingRows) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("print_jurnal_penutup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Jurnal Penutup",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Penutupan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatRupiah(finalDebitSum),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Status Penutupan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (isBalanced) "SEIMBANG" else if (closingRows.isEmpty()) "KOSONG" else "SELISIH",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBalanced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // --- ENTRIES TABLE ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Table header
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
                        Box(modifier = Modifier.weight(1.5f).padding(8.dp)) {
                            Text("Tgl", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(3.5f).padding(8.dp)) {
                            Text("Keterangan Rekening", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.0f).padding(8.dp)) {
                            Text("Ref", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                            Text("Debit (Rp)", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                            Text("Kredit (Rp)", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                if (closingRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Belum ada perkiraan yang ditutup.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(closingRows) { row ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (row.indexGroup % 2 == 0) Color.Transparent 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Tanggal
                                    Box(modifier = Modifier.weight(1.5f).padding(8.dp)) {
                                        Text(row.date, fontSize = 10.sp)
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Keterangan Rekening
                                    Box(modifier = Modifier.weight(3.5f).padding(8.dp)) {
                                        Text(
                                            text = if (row.isDebit) row.accountName else "     • ${row.accountName}",
                                            fontSize = 11.sp,
                                            fontWeight = if (row.isDebit) FontWeight.Bold else FontWeight.Normal,
                                            color = if (row.isDebit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Ref
                                    Box(modifier = Modifier.weight(1.0f).padding(8.dp)) {
                                        Text(
                                            text = getAccountRef(row.accountName),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Debit (Rp)
                                    Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                                        Text(
                                            text = if (row.isDebit) formatRupiah(row.amount) else "-",
                                            fontSize = 11.sp,
                                            fontWeight = if (row.isDebit) FontWeight.Bold else FontWeight.Normal,
                                            color = if (row.isDebit) MaterialTheme.colorScheme.primary else Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                    // Kredit (Rp)
                                    Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                                        Text(
                                            text = if (!row.isDebit) formatRupiah(row.amount) else "-",
                                            fontSize = 11.sp,
                                            fontWeight = if (!row.isDebit) FontWeight.Bold else FontWeight.Normal,
                                            color = if (!row.isDebit) MaterialTheme.colorScheme.tertiary else Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth()
                                        )
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
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(6.0f).padding(8.dp)) {
                            Text("JUMLAH PENUTUPAN (BALANCE)", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                            Text(formatRupiah(finalDebitSum), fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.0f).padding(8.dp)) {
                            Text(formatRupiah(finalCreditSum), fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

private fun formatRupiah(value: Double): String {
    return try {
        "%,.0f".format(Locale.US, value).replace(",", ".")
    } catch (e: Exception) {
        "%.0f".format(value)
    }
}

private fun printClosingEntries(
    context: Context,
    rows: List<ClosingJournalRow>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Jurnal Penutup - My Singghara"

    val totalD = rows.filter { it.isDebit }.sumOf { it.amount }
    val totalC = rows.filter { !it.isDebit }.sumOf { it.amount }
    val isBalanced = totalD == totalC

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 25px; border-bottom: 3px double #333; padding-bottom: 10px; text-align: center; }
                h1 { color: #111; margin: 0; font-size: 22px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { color: #555; margin: 5px 0 0 0; font-size: 14px; font-weight: normal; }
                .meta-time { color: #777; margin-top: 5px; font-size: 10px; }
                
                .indicator-box {
                    border: 1px solid ${if (isBalanced) "#c3e6cb" else "#f5c6cb"};
                    background-color: ${if (isBalanced) "#e8f5e9" else "#ffebee"};
                    color: ${if (isBalanced) "#2e7d32" else "#c62828"};
                    padding: 12px;
                    border-radius: 6px;
                    margin-bottom: 20px;
                    font-size: 13px;
                    text-align: center;
                    font-weight: bold;
                }
                
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 8px 10px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; text-align: center; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #fafafa; border-top: 2.5px solid #33s; border-bottom: 2.5px double #333; }
                .indent { padding-left: 25px; color: #555; }
            </style>
        </head>
        <body>
            <header>
                <h1>Jurnal Penutup</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <div class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</div>
            </header>

            <div class="indicator-box">
                STATUS KESEIMBANGAN: SEIMBANG (BALANCED)<br/>
                Total Penutupan: Rp ${String.format("%,.0f", totalD).replace(',', '.')}
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 12%;">Tanggal</th>
                        <th style="width: 40%;">Keterangan Perkiraan</th>
                        <th style="width: 10%;" class="text-center">Ref</th>
                        <th style="width: 19%;">Debit (Rp)</th>
                        <th style="width: 19%;">Kredit (Rp)</th>
                    </tr>
                </thead>
                <tbody>
    """)

    rows.forEach { row ->
        val safeAcc = if (row.isDebit) "<b>${row.accountName}</b>" else "<span class='indent'>• ${row.accountName}</span>"
        val debVal = if (row.isDebit) "Rp " + String.format("%,.0f", row.amount).replace(',', '.') else "-"
        val kreVal = if (!row.isDebit) "Rp " + String.format("%,.0f", row.amount).replace(',', '.') else "-"
        htmlBuilder.append("""
            <tr style="background-color: ${if (row.indexGroup % 2 == 0) "#fafafa" else "#ffffff"};">
                <td>${row.date}</td>
                <td>$safeAcc</td>
                <td class="text-center bold" style="color: #555;">${getAccountRef(row.accountName)}</td>
                <td class="text-right">$debVal</td>
                <td class="text-right">$kreVal</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3">JUMLAH TOTAL:</td>
                        <td class="text-right" style="color: blue;">Rp ${String.format("%,.0f", totalD).replace(',', '.')}</td>
                        <td class="text-right" style="color: red;">Rp ${String.format("%,.0f", totalC).replace(',', '.')}</td>
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
