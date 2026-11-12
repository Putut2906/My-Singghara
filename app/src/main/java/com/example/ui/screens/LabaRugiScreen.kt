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
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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

data class StatementItem(
    val accountName: String,
    val amount: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabaRugiScreen(
    journalEntries: List<JournalEntry>,
    adjustmentEntries: List<AdjustmentEntry>,
    viewModel: MainViewModel
) {
    val context = LocalContext.current

    // 1. FILTER DAN HITUNG SEMUA AKUN PENDAPATAN & BEBAN SETELAH PENYESUAIAN
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

    // Hitung saldo akhir disesuaikan untuk masing-masing akun
    val revenues = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val list = mutableListOf<StatementItem>()
        allUniqueAccounts.forEach { accName ->
            if (accName.contains("Pendapatan", ignoreCase = true) || accName.contains("Penjualan", ignoreCase = true)) {
                // Kecuali Pendapatan diterima di muka yang merupakan Utang (Kewajiban)
                if (accName.contains("diterima di muka", ignoreCase = true) || accName.contains("ditangguhkan", ignoreCase = true)) {
                    // Skip karena kewajiban
                    return@forEach
                }

                val debitSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                               adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
                val creditSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                                adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }
                
                val netCredit = creditSum - debitSum
                if (netCredit > 0) {
                    list.add(StatementItem(accName, netCredit))
                }
            }
        }
        list
    }

    val expenses = remember(journalEntries, adjustmentEntries, allUniqueAccounts) {
        val list = mutableListOf<StatementItem>()
        allUniqueAccounts.forEach { accName ->
            if (accName.contains("Beban", ignoreCase = true) || accName.contains("Biaya", ignoreCase = true) || accName.contains("Penyusutan", ignoreCase = true) || accName.equals("Harga Pokok Penjualan", ignoreCase = true)) {
                if (accName.contains("Akumulasi", ignoreCase = true)) {
                    // Skip karena kontra aset tetap
                    return@forEach
                }

                val debitSum = journalEntries.filter { it.debetAkun == accName }.sumOf { it.nominal } + 
                               adjustmentEntries.filter { it.debetAkun == accName }.sumOf { it.nominal }
                val creditSum = journalEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal } + 
                                adjustmentEntries.filter { it.kreditAkun == accName }.sumOf { it.nominal }
                
                val netDebit = debitSum - creditSum
                if (netDebit > 0) {
                    list.add(StatementItem(accName, netDebit))
                }
            }
        }
        list
    }

    val totalRevenues = revenues.sumOf { it.amount }
    val totalExpenses = expenses.sumOf { it.amount }
    val netProfitLoss = totalRevenues - totalExpenses
    val isProfit = netProfitLoss >= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- NET PROFIT/LOSS HERO SHOWCASE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isProfit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                1.dp,
                if (isProfit) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isProfit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (isProfit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isProfit) "LABA BERSIH" else "RUGI BERSIH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isProfit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Laporan Laba Rugi Periode Ini",
                                fontSize = 11.sp,
                                color = (if (isProfit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.7f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { printLabaRugiReport(context, revenues, expenses) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("print_laba_rugi")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Laporan",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cetak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Rp " + formatRupiah(kotlin.math.abs(netProfitLoss)),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (isProfit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                // Sub-metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Pendapatan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rp " + formatRupiah(totalRevenues), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Beban", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Rp " + formatRupiah(totalExpenses), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // --- DETAILS LIST ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Table Header Row
                item {
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
                            Box(modifier = Modifier.weight(5.2f).padding(12.dp)) {
                                Text(
                                    text = "Keterangan / Akun Laba Rugi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.8f).padding(12.dp)) {
                                Text(
                                    text = "Ref",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(12.dp)) {
                                Text(
                                    text = "Nominal (Rp)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }

                // A. PENDAPATAN SECTION
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                                Text(
                                    "A. PENDAPATAN (REVENUE)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }

                if (revenues.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                                Text(
                                    "Tidak ada akun pendapatan.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Text("-", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                } else {
                    items(revenues) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(5.2f).padding(10.dp)) {
                                Text(item.accountName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.8f).padding(10.dp)) {
                                Text(getAccountRef(item.accountName), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Text("Rp " + formatRupiah(item.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                            Text("Total Pendapatan", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                            Text(
                                text = "Rp " + formatRupiah(totalRevenues),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }

                // B. BEBAN SECTION
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                                Text(
                                    "B. BEBAN OPERASIONAL (EXPENSES)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Spacer(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }

                if (expenses.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                                Text(
                                    "Tidak ada akun beban.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Text("-", fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                } else {
                    items(expenses) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(5.2f).padding(10.dp)) {
                                Text(item.accountName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(1.8f).padding(10.dp)) {
                                Text(getAccountRef(item.accountName), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                                Text("Rp " + formatRupiah(item.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(7f).padding(10.dp)) {
                            Text("Total Beban", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(3f).padding(10.dp)) {
                            Text(
                                text = "Rp " + formatRupiah(totalExpenses),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                }

                // C. RINGKASAN
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(7f).padding(12.dp)) {
                            Text(
                                text = if (isProfit) "LABA BERSIH SETELAH PENYESUAIAN" else "RUGI BERSIH SETELAH PENYESUAIAN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(modifier = Modifier.weight(3f).padding(12.dp)) {
                            Text(
                                text = "Rp " + formatRupiah(netProfitLoss),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isProfit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
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

private fun printLabaRugiReport(
    context: Context,
    revenues: List<StatementItem>,
    expenses: List<StatementItem>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Laporan Laba Rugi - My Singghara"

    val totalRevenues = revenues.sumOf { it.amount }
    val totalExpenses = expenses.sumOf { it.amount }
    val netProfitLoss = totalRevenues - totalExpenses
    val isProfit = netProfitLoss >= 0

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
                    border: 1px solid ${if (isProfit) "#c3e6cb" else "#f5c6cb"};
                    background-color: ${if (isProfit) "#e8f5e9" else "#ffebee"};
                    color: ${if (isProfit) "#2e7d32" else "#c62828"};
                    padding: 15px;
                    border-radius: 6px;
                    margin-bottom: 20px;
                    font-size: 14px;
                    text-align: center;
                    font-weight: bold;
                }
                
                .section-title {
                    font-weight: bold;
                    font-size: 11px;
                    background-color: #f2f2f2;
                    padding: 6px 10px;
                    margin-top: 15px;
                    text-transform: uppercase;
                    letter-spacing: 0.5px;
                    border-left: 4px solid #333;
                }
                
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 11px; }
                td { padding: 8px 10px; border-bottom: 1px solid #eee; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .total-row { font-weight: bold; border-top: 1.5px solid #333; border-bottom: 1.5px solid #333; }
                .grand-total-row { font-weight: bold; font-size: 12px; border-top: 2px solid #111; border-bottom: 3px double #111; background-color: #fafafa; }
            </style>
        </head>
        <body>
            <header>
                <h1>Laporan Laba Rugi</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <div class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</div>
            </header>

            <div class="indicator-box">
                ${if (isProfit) "LABA BERSIH (NET PROFIT)" else "RUGI BERSIH (NET LOSS)"}: <br/>
                <span style="font-size: 20px; font-weight: 900;">Rp ${String.format("%,.0f", kotlin.math.abs(netProfitLoss)).replace(',', '.')}</span>
            </div>

            <div class="section-title" style="color: blue;">Pendapatan</div>
            <table>
                <tbody>
    """)

    if (revenues.isEmpty()) {
        htmlBuilder.append("""
            <tr>
                <td colspan="3" style="color: gray; font-style: italic;">Tidak ada akun pendapatan</td>
            </tr>
        """)
    } else {
        revenues.forEach { item ->
            htmlBuilder.append("""
                <tr>
                    <td>${item.accountName}</td>
                    <td class="text-center" style="color: #555; font-weight: bold;">${getAccountRef(item.accountName)}</td>
                    <td class="text-right">Rp ${String.format("%,.0f", item.amount).replace(',', '.')}</td>
                </tr>
            """)
        }
    }

    htmlBuilder.append("""
                <tr class="total-row">
                    <td colspan="2">Total Pendapatan:</td>
                    <td class="text-right" style="color: blue;">Rp ${String.format("%,.0f", totalRevenues).replace(',', '.')}</td>
                </tr>
                </tbody>
            </table>

            <div class="section-title" style="color: red;">Beban Operasional</div>
            <table>
                <tbody>
    """)

    if (expenses.isEmpty()) {
        htmlBuilder.append("""
            <tr>
                <td colspan="3" style="color: gray; font-style: italic;">Tidak ada akun beban</td>
            </tr>
        """)
    } else {
        expenses.forEach { item ->
            htmlBuilder.append("""
                <tr>
                    <td>${item.accountName}</td>
                    <td class="text-center" style="color: #555; font-weight: bold;">${getAccountRef(item.accountName)}</td>
                    <td class="text-right">Rp ${String.format("%,.0f", item.amount).replace(',', '.')}</td>
                </tr>
            """)
        }
    }

    htmlBuilder.append("""
                <tr class="total-row">
                    <td colspan="2">Total Beban Operasional:</td>
                    <td class="text-right" style="color: red;">Rp ${String.format("%,.0f", totalExpenses).replace(',', '.')}</td>
                </tr>
                </tbody>
            </table>

            <div style="margin-top: 30px;">
                <table>
                    <tbody>
                        <tr class="grand-total-row">
                            <td>${if (isProfit) "LABA BERSIH SETELAH PENYESUAIAN:" else "RUGI BERSIH SETELAH PENYESUAIAN:"}</td>
                            <td class="text-right" style="color: ${if (isProfit) "green" else "red"};">Rp ${String.format("%,.0f", netProfitLoss).replace(',', '.')}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
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
