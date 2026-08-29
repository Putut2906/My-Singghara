package com.example.ui.screens

import com.example.getAccountRef

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.JournalEntry
import com.example.DialogAccountPicker
import java.util.Calendar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JurnalUmumScreen(
    journalEntries: List<JournalEntry>,
    onSaveEntry: (JournalEntry) -> Unit,
    onDeleteEntry: ((Int) -> Unit)? = null
) {
    var showInputPopup by remember { mutableStateOf(false) }
    var itemToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    var feedbackBannerIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var feedbackBannerColor by remember { mutableStateOf<Color?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(feedbackBannerMessage) {
        if (feedbackBannerMessage != null) {
            delay(2800)
            feedbackBannerMessage = null
        }
    }

    // Filter journal entries
    val filteredEntries = remember(searchQuery, journalEntries) {
        if (searchQuery.isBlank()) {
            journalEntries
        } else {
            journalEntries.filter {
                it.keterangan.contains(searchQuery, ignoreCase = true) ||
                it.debetAkun.contains(searchQuery, ignoreCase = true) ||
                it.kreditAkun.contains(searchQuery, ignoreCase = true) ||
                it.tanggal.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Hitung total debet & kredit
    val totalDebit = journalEntries.sumOf { it.nominal }
    val totalKredit = journalEntries.sumOf { it.nominal }
    val isBalanced = totalDebit == totalKredit && journalEntries.isNotEmpty()

    // Dialog Input Transaksi Baru (Pop-up)
    if (showInputPopup) {
        DialogInputTransaksiBaru(
            onDismiss = { showInputPopup = false },
            onSave = { entry ->
                onSaveEntry(entry)
                showInputPopup = false
                feedbackBannerMessage = "Transaksi jurnal berhasil disimpan!"
                feedbackBannerIcon = Icons.Default.CheckCircle
                feedbackBannerColor = Color(0xFF10B981)
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
                        text = "Hapus Transaksi",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                val entryUnderDelete = itemToDeleteIndex?.let { idx ->
                    if (idx >= 0 && idx < journalEntries.size) journalEntries[idx] else null
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus transaksi ini?",
                        fontSize = 14.sp
                    )
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
                                Text(
                                    text = "Tanggal: ${entryUnderDelete.tanggal}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Keterangan: ${entryUnderDelete.keterangan}",
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Nominal: Rp %,.0f".format(entryUnderDelete.nominal),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tindakan ini tidak dapat dibatalkan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDeleteIndex?.let { index ->
                            onDeleteEntry?.invoke(index)
                        }
                        itemToDeleteIndex = null
                        feedbackBannerMessage = "Transaksi jurnal berhasil dihapus."
                        feedbackBannerIcon = Icons.Default.Delete
                        feedbackBannerColor = Color(0xFFEF4444)
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Jurnal Umum",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${journalEntries.size} Transaksi Tercatat",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBalanced) MaterialTheme.colorScheme.primary else if (journalEntries.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBalanced) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isBalanced) MaterialTheme.colorScheme.onPrimary else if (journalEntries.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError
                                )
                                Text(
                                    text = if (isBalanced) "SEIMBANG" else if (journalEntries.isEmpty()) "KOSONG" else "SELISIH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBalanced) MaterialTheme.colorScheme.onPrimary else if (journalEntries.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError
                                )
                            }
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Total Debet", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Rp " + formatNominalValue(totalDebit),
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Total Kredit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Rp " + formatNominalValue(totalKredit),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // --- ACTION & SEARCH BAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari akun, keterangan...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(16.dp))
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

                val context = LocalContext.current
                OutlinedButton(
                    onClick = { printJurnal(context, journalEntries) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("print_jurnal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Cetak Jurnal",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = { showInputPopup = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("add_jurnal_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Input", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // --- DATA JURNAL TABEL ---
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
                            Box(modifier = Modifier.weight(1.2f).padding(10.dp)) {
                                Text(
                                    text = "Tanggal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(3.2f).padding(10.dp)) {
                                Text(
                                    text = "Akun & Keterangan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
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
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.1f).padding(10.dp)) {
                                Text(
                                    text = "Debet (D)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.1f).padding(10.dp)) {
                                Text(
                                    text = "Kredit (K)",
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

                    if (filteredEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(54.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Tidak ada transaksi yang cocok." else "Belum ada entri transaksi di jurnal.",
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
                            items(filteredEntries.asReversed()) { entry ->
                                val indexInOriginal = journalEntries.indexOf(entry)
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Tanggal
                                        Box(modifier = Modifier.weight(1.3f).padding(8.dp)) {
                                            Text(
                                                text = entry.tanggal,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                        
                                        // Isi Akun & Keterangan
                                        Box(modifier = Modifier.weight(3.5f).padding(8.dp)) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = entry.debetAkun,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Text(
                                                    text = "   ${entry.kreditAkun}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                                )
                                                Text(
                                                    text = "(${entry.keterangan})",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Ref Column
                                        Box(modifier = Modifier.weight(1.1f).padding(8.dp)) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = getAccountRef(entry.debetAkun),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = getAccountRef(entry.kreditAkun),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Debit Nominal
                                        Box(modifier = Modifier.weight(2.1f).padding(8.dp)) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.End
                                            ) {
                                                Text(
                                                    text = "Rp " + formatNominalValue(entry.nominal),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.End,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(18.dp))
                                                Text(
                                                    text = "-",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    textAlign = TextAlign.End
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                                        // Kredit Nominal
                                        Box(modifier = Modifier.weight(2.2f).padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = "-",
                                                        fontSize = 11.sp,
                                                        color = Color.Gray,
                                                        textAlign = TextAlign.End
                                                    )
                                                    Spacer(modifier = Modifier.height(18.dp))
                                                    Text(
                                                        text = "Rp " + formatNominalValue(entry.nominal),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.End,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                
                                                // Delete Button
                                                if (onDeleteEntry != null) {
                                                    IconButton(
                                                        onClick = { itemToDeleteIndex = indexInOriginal },
                                                        modifier = Modifier.size(24.dp).padding(start = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Hapus entri",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
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
                            Box(modifier = Modifier.weight(5.5f).padding(8.dp)) {
                                Text(
                                    text = "TOTAL BALANCED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.1f).padding(8.dp)) {
                                Text(
                                    text = "Rp " + formatNominalValue(totalDebit),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Box(modifier = Modifier.weight(2.1f).padding(8.dp)) {
                                Text(
                                    text = "Rp " + formatNominalValue(totalKredit),
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

        // Floating Animated Feedback Banner / Toast
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (feedbackBannerMessage != null) {
                com.example.ui.components.SpringToastPopup(
                    message = feedbackBannerMessage,
                    onDismiss = { feedbackBannerMessage = null }
                )
            }
        }
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

@Composable
fun DialogInputTransaksiBaru(
    onDismiss: () -> Unit,
    onSave: (JournalEntry) -> Unit
) {
    var tanggalInput by remember {
        val cal = Calendar.getInstance()
        val yr = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val dy = cal.get(Calendar.DAY_OF_MONTH)
        val moStr = if (mo < 10) "0$mo" else "$mo"
        val dyStr = if (dy < 10) "0$dy" else "$dy"
        mutableStateOf("$yr-$moStr-$dyStr")
    }
    var keteranganInput by remember { mutableStateOf("") }
    var nominalInput by remember { mutableStateOf("") }
    
    var debetAkun by remember { mutableStateOf("Kas") }
    var kreditAkun by remember { mutableStateOf("Pendapatan jasa") }
    
    var showDebetPicker by remember { mutableStateOf(false) }
    var showKreditPicker by remember { mutableStateOf(false) }

    if (showDebetPicker) {
        DialogAccountPicker(
            title = "Pilih Akun Debit",
            onDismiss = { showDebetPicker = false },
            onSelectAccount = { 
                debetAkun = it
                showDebetPicker = false
            }
        )
    }
    
    if (showKreditPicker) {
        DialogAccountPicker(
            title = "Pilih Akun Kredit",
            onDismiss = { showKreditPicker = false },
            onSelectAccount = { 
                kreditAkun = it
                showKreditPicker = false
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
                Text(
                    text = "Input Transaksi Baru",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tanggal
                val context = androidx.compose.ui.platform.LocalContext.current
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
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Keterangan
                OutlinedTextField(
                    value = keteranganInput,
                    onValueChange = { keteranganInput = it },
                    label = { Text("Keterangan", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: Menerima modal awal") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Card Pemilihan Debit/Kredit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Debit
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDebetPicker = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Akun Debit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                debetAkun,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Kredit
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showKreditPicker = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Akun Kredit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                kreditAkun,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Nominal
                OutlinedTextField(
                    value = nominalInput,
                    onValueChange = { nominalInput = it },
                    label = { Text("Nominal Transaksi (Rp)", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: 150000") },
                    leadingIcon = {
                        Text(
                            "Rp", 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominalVal = nominalInput.replace(",", "").replace(".", "").toDoubleOrNull() ?: 0.0
                    if (keteranganInput.isBlank() || nominalVal <= 0.0) {
                        return@Button
                    }
                    val entry = JournalEntry(
                        tanggal = tanggalInput,
                        keterangan = keteranganInput,
                        debetAkun = debetAkun,
                        kreditAkun = kreditAkun,
                        nominal = nominalVal
                    )
                    onSave(entry)
                },
                enabled = keteranganInput.isNotBlank() && nominalInput.isNotBlank(),
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
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatNominalValue(value: Double): String {
    return try {
        "%,.0f".format(value).replace(",", ".")
    } catch (e: Exception) {
        value.toString()
    }
}

private fun printJurnal(
    context: Context,
    journalEntries: List<JournalEntry>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Buku Jurnal Umum - My Singghara"

    val totalDebit = journalEntries.sumOf { it.nominal }
    val totalKredit = journalEntries.sumOf { it.nominal }

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                header { margin-bottom: 25px; border-bottom: 3px double #333; padding-bottom: 10px; }
                h1 { text-align: center; color: #111; margin: 0; font-size: 20px; font-weight: bold; text-transform: uppercase; letter-spacing: 1px; }
                h2 { text-align: center; color: #555; margin: 5px 0 0 0; font-size: 13px; font-weight: normal; }
                .meta-time { text-align: center; color: #777; margin-top: 5px; font-size: 10px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 11px; }
                th, td { border: 1px solid #999; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; font-weight: bold; text-transform: uppercase; font-size: 10px; letter-spacing: 0.5px; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #f9f9f9; }
                .indent { padding-left: 20px; font-style: italic; color: #444; }
                .keterangan { font-size: 9px; color: #666; font-style: italic; margin-top: 2px; display: block; }
                .border-top-double { border-top: 3px double #333; }
            </style>
        </head>
        <body>
            <header>
                <h1>Buku Jurnal Umum</h1>
                <h2>My Singghara - Keuangan Mandiri</h2>
                <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            </header>

            <table>
                <thead>
                    <tr>
                        <th style="width: 15%;">TANGGAL</th>
                        <th style="width: 40%;">KETERANGAN / AKUN REK</th>
                        <th style="width: 10%;" class="text-center">REF</th>
                        <th style="width: 17.5%;" class="text-right">DEBET</th>
                        <th style="width: 17.5%;" class="text-right">KREDIT</th>
                    </tr>
                </thead>
                <tbody>
    """)

    journalEntries.forEach { entry ->
        htmlBuilder.append("""
            <!-- Baris Debet -->
            <tr>
                <td rowspan="2" class="text-center" style="vertical-align: top; font-weight: bold; color: #0056b3;">${entry.tanggal}</td>
                <td><strong>${entry.debetAkun}</strong></td>
                <td class="text-center" style="font-weight: bold; color: #555;">${getAccountRef(entry.debetAkun)}</td>
                <td class="text-right" style="font-weight: bold; color: green;">Rp ${String.format(Locale.US, "%,.0f", entry.nominal).replace(',', '.')}</td>
                <td class="text-right" style="color: #ccc;">-</td>
            </tr>
            <!-- Baris Kredit -->
            <tr>
                <td><span class="indent">${entry.kreditAkun}</span><br/><span class="keterangan">(${entry.keterangan})</span></td>
                <td class="text-center" style="font-weight: bold; color: #555;">${getAccountRef(entry.kreditAkun)}</td>
                <td class="text-right" style="color: #ccc;">-</td>
                <td class="text-right" style="font-weight: bold; color: red;">Rp ${String.format(Locale.US, "%,.0f", entry.nominal).replace(',', '.')}</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <!-- Total Row -->
                    <tr class="total-row border-top-double">
                        <td colspan="3" class="text-right" style="font-size: 12px; font-weight: bold;">TOTAL JURNAL:</td>
                        <td class="text-right" style="font-size: 12px; color: green; font-weight: bold;">Rp ${String.format(Locale.US, "%,.0f", totalDebit).replace(',', '.')}</td>
                        <td class="text-right" style="font-size: 12px; color: red; font-weight: bold;">Rp ${String.format(Locale.US, "%,.0f", totalKredit).replace(',', '.')}</td>
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
