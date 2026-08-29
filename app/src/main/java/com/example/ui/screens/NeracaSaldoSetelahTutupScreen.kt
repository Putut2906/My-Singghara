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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
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

data class PostClosingRow(
    val accountName: String,
    val debitAmount: Double,
    val creditAmount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeracaSaldoSetelahTutupScreen(
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

    // Hitung sisa saldo penutup (Real Accounts saja: Assets, Liabilities, dan Modal yang disesuaikan)
    val postClosingRows = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val list = mutableListOf<PostClosingRow>()

        // 1. Hitung Laba/Rugi Bersih & Prive terlebih dahulu untuk penyesuaian Modal Pemilik
        var totalRevenues = 0.0
        var totalExpenses = 0.0
        var priveAmount = 0.0

        allUniqueAccounts.forEach { accName ->
            val dSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                         adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
            val cSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                          adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }

            if (accName.contains("Pendapatan", ignoreCase = true) || accName.contains("Penjualan", ignoreCase = true)) {
                if (!accName.contains("diterima di muka", ignoreCase = true) && !accName.contains("ditangguhkan", ignoreCase = true)) {
                    val netCredit = cSum - dSum
                    if (netCredit > 0) totalRevenues += netCredit
                }
            } else if (accName.contains("Beban", ignoreCase = true) || accName.contains("Biaya", ignoreCase = true) || accName.contains("Penyusutan", ignoreCase = true) || accName.equals("Harga Pokok Penjualan", ignoreCase = true)) {
                if (!accName.contains("Akumulasi", ignoreCase = true)) {
                    val netDebit = dSum - cSum
                    if (netDebit > 0) totalExpenses += netDebit
                }
            } else if (accName.contains("Prive", ignoreCase = true)) {
                val netDebit = dSum - cSum
                if (netDebit > 0) priveAmount += netDebit
            }
        }

        val netProfitLoss = totalRevenues - totalExpenses

        // 2. Buat baris saldo penutup
        allUniqueAccounts.forEach { accName ->
            // Skip nominal accounts (Revenues, Expenses, Prive, Ikhtisar Laba Rugi)
            if (accName.contains("Pendapatan", ignoreCase = true) || accName.contains("Penjualan", ignoreCase = true)) {
                if (!accName.contains("diterima di muka", ignoreCase = true) && !accName.contains("ditangguhkan", ignoreCase = true)) {
                    return@forEach // Skip Pendapatan nominal
                }
            }
            if (accName.contains("Beban", ignoreCase = true) || accName.contains("Biaya", ignoreCase = true) || accName.contains("Penyusutan", ignoreCase = true) || accName.equals("Harga Pokok Penjualan", ignoreCase = true)) {
                if (!accName.contains("Akumulasi", ignoreCase = true)) {
                    return@forEach // Skip Beban nominal
                }
            }
            if (accName.contains("Prive", ignoreCase = true) || accName.contains("Ikhtisar", ignoreCase = true)) {
                return@forEach // Skip Prive & Ikhtisar
            }

            // Hitung saldo debit / kredit untuk real accounts
            val dSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                         adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
            val cSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                          adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }

            val normalType = getNormalBalanceTypeLocalPost(accName)

            if (accName.contains("Modal", ignoreCase = true)) {
                // Modal Pemilik yang disesuaikan: Modal Awal + NetProfit - Prive
                val initialModal = if (normalType == "K") (cSum - dSum) else (dSum - cSum)
                val updatedModalVal = initialModal + netProfitLoss - priveAmount
                
                if (updatedModalVal >= 0) {
                    list.add(PostClosingRow(accName, 0.0, updatedModalVal))
                } else {
                    list.add(PostClosingRow(accName, kotlin.math.abs(updatedModalVal), 0.0))
                }
            } else {
                // Akun riil biasa (Kas, Piutang, Peralatan, Utang)
                var closingD = 0.0
                var closingC = 0.0

                if (normalType == "D") {
                    val net = dSum - cSum
                    if (net >= 0) closingD = net else closingC = kotlin.math.abs(net)
                } else {
                    val net = cSum - dSum
                    if (net >= 0) closingC = net else closingD = kotlin.math.abs(net)
                }

                if (closingD > 0 || closingC > 0) {
                    list.add(PostClosingRow(accName, closingD, closingC))
                }
            }
        }

        list
    }

    val filteredRows = remember(postClosingRows, searchQuery) {
        if (searchQuery.isBlank()) postClosingRows
        else postClosingRows.filter { it.accountName.contains(searchQuery, ignoreCase = true) }
    }

    val totalDebit = postClosingRows.sumOf { it.debitAmount }
    val totalCredit = postClosingRows.sumOf { it.creditAmount }
    val isBalanced = totalDebit == totalCredit && postClosingRows.isNotEmpty()

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
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Neraca Saldo Setelah Tutup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${postClosingRows.size} Akun Riil (Permanen)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBalanced) MaterialTheme.colorScheme.primary else if (postClosingRows.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = if (isBalanced) "SEIMBANG" else if (postClosingRows.isEmpty()) "KOSONG" else "SELISIH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBalanced) MaterialTheme.colorScheme.onPrimary else if (postClosingRows.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
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
                            Text("Total Debit Riil", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatRupiah(totalDebit),
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
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Kredit Riil", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatRupiah(totalCredit),
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
                placeholder = { Text("Cari akun riil...", fontSize = 12.sp) },
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
                onClick = { printPostClosingTrialBalance(context, postClosingRows) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.testTag("print_neraca_saldo_setelah_tutup")
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Cetak Laporan",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- DATA DISPLAY CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                        Box(modifier = Modifier.weight(3.8f).padding(12.dp)) {
                            Text("Nama Akun Riil", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(1.2f).padding(12.dp)) {
                            Text("Ref", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                            Text("Debit (Rp)", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                            Text("Kredit (Rp)", fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
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
                            Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(if (searchQuery.isNotEmpty()) "Tidak ada akun yang cocok." else "Belum ada saldo akun permanen.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredRows) { row ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(3.8f).padding(12.dp)) {
                                        Text(row.accountName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(1.2f).padding(12.dp)) {
                                        Text(getAccountRef(row.accountName), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                                        Text(if (row.debitAmount > 0) formatRupiah(row.debitAmount) else "-", fontSize = 11.sp, color = if (row.debitAmount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                                    }
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                                        Text(if (row.creditAmount > 0) formatRupiah(row.creditAmount) else "-", fontSize = 11.sp, color = if (row.creditAmount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
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
                        Box(modifier = Modifier.weight(5.0f).padding(12.dp)) {
                            Text("TOTAL SALDO RIIL", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                            Text(formatRupiah(totalDebit), fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(2.5f).padding(12.dp)) {
                            Text(formatRupiah(totalCredit), fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

private fun getNormalBalanceTypeLocalPost(accountName: String): String {
    val creditKeywords = listOf("Utang", "Kewajiban", "Modal", "Pendapatan", "Akumulasi")
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

private fun printPostClosingTrialBalance(
    context: Context,
    rows: List<PostClosingRow>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Neraca Saldo Setelah Tutup - My Singghara"

    val totalD = rows.sumOf { it.debitAmount }
    val totalC = rows.sumOf { it.creditAmount }
    val isBalanced = totalD == totalC

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 25px; border-bottom: 3px double #333; padding-bottom: 10px; text-align: center; }
                h1 { color: #111; margin: 0; font-size: 20px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { color: #555; margin: 5px 0 0 0; font-size: 13px; font-weight: normal; }
                .meta-time { color: #777; margin-top: 5px; font-size: 10px; }
                
                .indicator-box {
                    border: 1px solid ${if (isBalanced && rows.isNotEmpty()) "#c3e6cb" else "#f5c6cb"};
                    background-color: ${if (isBalanced && rows.isNotEmpty()) "#d4edda" else "#f8d7da"};
                    color: ${if (isBalanced && rows.isNotEmpty()) "#155724" else "#721c24"};
                    padding: 12px;
                    border-radius: 6px;
                    margin-bottom: 20px;
                    font-size: 13px;
                    text-align: center;
                    font-weight: bold;
                }
                
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 10px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; text-align: center; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #fafafa; border-top: 3.5px double #333; border-bottom: 3.5px double #333; }
            </style>
        </head>
        <body>
            <header>
                <h1>Neraca Saldo Setelah Penutupan</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <div class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</div>
            </header>

            <div class="indicator-box">
                STATUS KESEIMBANGAN AKUN RIIL: ${if (isBalanced && rows.isNotEmpty()) "SEIMBANG (BALANCED)" else "TIDAK SEIMBANG / KOSONG"}<br/>
                Total Saldo Riil: Rp ${String.format("%,.0f", totalD).replace(',', '.')}
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 42%;">Nama Akun Riil (Permanen)</th>
                        <th style="width: 12%;" class="text-center">Ref</th>
                        <th style="width: 23%;">Debit (Rp)</th>
                        <th style="width: 23%;">Kredit (Rp)</th>
                    </tr>
                </thead>
                <tbody>
    """)

    rows.forEach { row ->
        val debVal = if (row.debitAmount > 0) "Rp " + String.format("%,.0f", row.debitAmount).replace(',', '.') else "-"
        val kreVal = if (row.creditAmount > 0) "Rp " + String.format("%,.0f", row.creditAmount).replace(',', '.') else "-"
        htmlBuilder.append("""
            <tr>
                <td class="bold">${row.accountName}</td>
                <td class="text-center bold" style="color: #555;">${getAccountRef(row.accountName)}</td>
                <td class="text-right">$debVal</td>
                <td class="text-right">$kreVal</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="2">TOTAL SISA BALANCE PENUTUPAN:</td>
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
