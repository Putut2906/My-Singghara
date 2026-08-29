package com.example.ui.screens

import com.example.getAccountRef

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.JournalEntry
import com.example.ui.MainViewModel

data class TrialBalanceRow(
    val accountName: String,
    val debitAmount: Double,
    val creditAmount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeracaSaldoScreen(
    journalEntries: List<JournalEntry>,
    viewModel: MainViewModel
) {
    var searchQuery by remember { mutableStateOf("") }

    // Kumpulkan seluruh daftar semua akun standar yang ada untuk memastikan urutannya logis
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

    // Deteksi juga jika ada akun kustom yang dimasukkan di luar daftar standar
    val allUniqueAccounts = remember(journalEntries) {
        val set = mutableSetOf<String>()
        journalEntries.forEach {
            set.add(it.debetAkun)
            set.add(it.kreditAkun)
        }
        val customAccs = set.filter { !standardAccountsOrdered.contains(it) }.sorted()
        standardAccountsOrdered.filter { set.contains(it) } + customAccs
    }

    // Hitung baris neraca saldo secara dinamis berdasarkan postingan jurnal umum
    val trialBalanceRows = remember(journalEntries, allUniqueAccounts) {
        val rows = mutableListOf<TrialBalanceRow>()
        
        allUniqueAccounts.forEach { accName ->
            // Filter postingan debit & kredit untuk akun ini
            val matchingDebits = journalEntries.filter { it.debetAkun == accName }
            val matchingCredits = journalEntries.filter { it.kreditAkun == accName }

            val debitSum = matchingDebits.sumOf { it.nominal }
            val creditSum = matchingCredits.sumOf { it.nominal }

            val normalType = getNormalBalanceType(accName)
            
            var rowDebit = 0.0
            var rowCredit = 0.0

            if (normalType == "D") {
                val netSum = debitSum - creditSum
                if (netSum >= 0) {
                    rowDebit = netSum
                } else {
                    rowCredit = kotlin.math.abs(netSum)
                }
            } else {
                val netSum = creditSum - debitSum
                if (netSum >= 0) {
                    rowCredit = netSum
                } else {
                    rowDebit = kotlin.math.abs(netSum)
                }
            }

            // Hanya tampilkan jika setidaknya memiliki nilai saldo akhir di salah satu kolom
            if (rowDebit > 0 || rowCredit > 0) {
                rows.add(
                    TrialBalanceRow(
                        accountName = accName,
                        debitAmount = rowDebit,
                        creditAmount = rowCredit
                    )
                )
            }
        }
        rows
    }

    val filteredRows = remember(trialBalanceRows, searchQuery) {
        if (searchQuery.isBlank()) trialBalanceRows
        else trialBalanceRows.filter { it.accountName.contains(searchQuery, ignoreCase = true) }
    }

    val grandTotalDebit = trialBalanceRows.sumOf { it.debitAmount }
    val grandTotalCredit = trialBalanceRows.sumOf { it.creditAmount }
    val isBalanced = grandTotalDebit == grandTotalCredit && trialBalanceRows.isNotEmpty()

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
                                text = "Neraca Saldo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${trialBalanceRows.size} Akun Memiliki Saldo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBalanced) MaterialTheme.colorScheme.primary else if (trialBalanceRows.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = if (isBalanced) "SEIMBANG" else if (trialBalanceRows.isEmpty()) "KOSONG" else "SELISIH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBalanced) MaterialTheme.colorScheme.onPrimary else if (trialBalanceRows.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Debet Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Debet", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatMoney(grandTotalDebit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Total Kredit Card
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Kredit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Rp " + formatMoney(grandTotalCredit),
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
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama akun...", fontSize = 12.sp) },
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
                onClick = { printTrialBalanceReport(context, trialBalanceRows) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.testTag("print_neraca_saldo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Cetak Neraca Saldo",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- TABEL NERACA SALDO ---
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
                        Box(modifier = Modifier.weight(3.5f).padding(10.dp)) {
                            Text(
                                text = "Nama Akun",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Box(modifier = Modifier.weight(1.1f).padding(10.dp)) {
                            Text(
                                text = "Ref",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                            Text(
                                text = "Debet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                            Text(
                                text = "Kredit",
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

                if (filteredRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Tidak ada akun yang cocok." else "Belum ada saldo akun untuk neraca saldo.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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
                                    // Nama Akun
                                    Box(modifier = Modifier.weight(3.5f).padding(10.dp)) {
                                        Text(
                                            text = row.accountName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )

                                    // Ref
                                    Box(modifier = Modifier.weight(1.1f).padding(10.dp)) {
                                        Text(
                                            text = getAccountRef(row.accountName),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )

                                    // Debet
                                    Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                                        Text(
                                            text = if (row.debitAmount > 0) "Rp " + formatMoney(row.debitAmount) else "-",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = if (row.debitAmount > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (row.debitAmount > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )

                                    // Kredit
                                    Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                                        Text(
                                            text = if (row.creditAmount > 0) "Rp " + formatMoney(row.creditAmount) else "-",
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth(),
                                            fontWeight = if (row.creditAmount > 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (row.creditAmount > 0) MaterialTheme.colorScheme.tertiary else Color.Gray
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                            }
                        }
                    }

                    // Total Footer Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(4.6f).padding(10.dp)) {
                            Text(
                                text = "TOTAL NERACA",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                            Text(
                                text = "Rp " + formatMoney(grandTotalDebit),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Box(modifier = Modifier.weight(2.7f).padding(10.dp)) {
                            Text(
                                text = "Rp " + formatMoney(grandTotalCredit),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(value: Double): String {
    return try {
        "%,.0f".format(value).replace(",", ".")
    } catch (e: Exception) {
        value.toString()
    }
}

private fun printTrialBalanceReport(
    context: Context,
    rows: List<TrialBalanceRow>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Neraca Saldo - My Singghara"

    val totalDebit = rows.sumOf { it.debitAmount }
    val totalCredit = rows.sumOf { it.creditAmount }
    val isBalanced = totalDebit == totalCredit

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 20px; border-bottom: 3px double #333; padding-bottom: 10px; }
                h1 { text-align: center; color: #111; margin: 0; font-size: 20px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { text-align: center; color: #555; margin: 5px 0 0 0; font-size: 13px; font-weight: normal; }
                .meta-time { text-align: center; color: #777; margin-top: 5px; font-size: 10px; }
                .status-container {
                    border: 1px solid ${if (isBalanced && rows.isNotEmpty()) "#c3e6cb" else "#f5c6cb"};
                    background-color: ${if (isBalanced && rows.isNotEmpty()) "#d4edda" else "#f8d7da"};
                    color: ${if (isBalanced && rows.isNotEmpty()) "#155724" else "#721c24"};
                    padding: 10px;
                    border-radius: 4px;
                    margin-bottom: 15px;
                    font-size: 11px;
                    text-align: center;
                    font-weight: bold;
                }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 10px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; background-color: #f9f9f9; border-top: 3px double #333; }
            </style>
        </head>
        <body>
            <header>
                <h1>Neraca Saldo</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            </header>

            <div class="status-container">
                STATUS KESEIMBANGAN: ${if (isBalanced && rows.isNotEmpty()) "SEIMBANG (BALANCED)" else "TIDAK SEIMBANG / BELUM ADA ENTRI"}
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 44%;">NAMA AKUN / REKENING</th>
                        <th style="width: 12%;" class="text-center">REF</th>
                        <th style="width: 22%;" class="text-right">DEBET</th>
                        <th style="width: 22%;" class="text-right">KREDIT</th>
                    </tr>
                </thead>
                <tbody>
    """)

    rows.forEach { row ->
        htmlBuilder.append("""
            <tr>
                <td class="bold" style="color: #333;">${row.accountName}</td>
                <td class="text-center bold" style="color: #555;">${getAccountRef(row.accountName)}</td>
                <td class="text-right" style="color: ${if (row.debitAmount > 0) "green" else "#777"}; font-weight: ${if (row.debitAmount > 0) "bold" else "normal"};">
                    ${if (row.debitAmount > 0) "Rp " + String.format(Locale.US, "%,.0f", row.debitAmount).replace(',', '.') else "-"}
                </td>
                <td class="text-right" style="color: ${if (row.creditAmount > 0) "red" else "#777"}; font-weight: ${if (row.creditAmount > 0) "bold" else "normal"};">
                    ${if (row.creditAmount > 0) "Rp " + String.format(Locale.US, "%,.0f", row.creditAmount).replace(',', '.') else "-"}
                </td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <!-- Footer Total Row -->
                    <tr class="total-row">
                        <td colspan="2">TOTAL NERACA</td>
                        <td class="text-right" style="color: green; font-size: 12px; font-weight: bold;">
                            Rp ${String.format(Locale.US, "%,.0f", totalDebit).replace(',', '.')}
                        </td>
                        <td class="text-right" style="color: red; font-size: 12px; font-weight: bold;">
                            Rp ${String.format(Locale.US, "%,.0f", totalCredit).replace(',', '.')}
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
