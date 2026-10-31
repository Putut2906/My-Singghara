package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt
import kotlin.math.atan2
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.ui.MainViewModel
import com.example.util.ReceiptScannerHelper
import com.example.util.ParsedReceiptData
import kotlinx.coroutines.launch
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

@Composable
fun TabunganScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val targetFlow by viewModel.activeSavingTarget.collectAsState()
    val transactionsFlow by viewModel.savingTransactions.collectAsState()

    var showChoosePopup by remember { mutableStateOf(false) }
    var showTargetFormPopup by remember { mutableStateOf(false) }
    var showTrxFormPopup by remember { mutableStateOf(false) }
    var showSafWarningDialog by remember { mutableStateOf(false) }
    var trxToDelete by remember { mutableStateOf<SavingTransaction?>(null) }
    var editingTrx by remember { mutableStateOf<SavingTransaction?>(null) }
    var selectedCategory by remember { mutableStateOf("sisa") } // sisa, masuk, keluar
    var showDetailPopup by remember { mutableStateOf(false) }

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sharedReceiptUri = uri
            viewModel.sharedReceiptText = null
            viewModel.showSharedReceiptDialog = true
        }
    }

    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    var feedbackBannerIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var feedbackBannerColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(feedbackBannerMessage) {
        if (feedbackBannerMessage != null) {
            delay(2800)
            feedbackBannerMessage = null
        }
    }

    // Forms States
    var inputTargetNama by remember { mutableStateOf("") }
    var inputTargetJumlah by remember { mutableStateOf("") }

    var inputTrxJenis by remember { mutableStateOf("") } // masuk, keluar
    var inputTrxKategori by remember { mutableStateOf("") }
    var inputTrxDeskripsi by remember { mutableStateOf("") }
    var inputTrxNominal by remember { mutableStateOf("") }
    var inputTrxTanggal by remember { mutableStateOf("") }

    val dateFormater = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Categories list for Saving transactions
    val categoriesMap = mapOf(
        "masuk" to listOf("Gaji", "Bonus", "Hadiah", "Investasi", "Lainnya"),
        "keluar" to listOf("Kebutuhan Pokok", "Tagihan", "Transportasi", "Pendidikan", "Gaya Hidup", "Lainnya")
    )

    // Filter & Search States
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("Semua") } // "Semua", "Masuk", "Keluar"
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var selectedDateFilter by remember { mutableStateOf("") }
    var showFilterMenuDialog by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showFilterDatePickerDialog by remember { mutableStateOf(false) }

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
    val filteredTransactions = remember(transactionsFlow, searchQuery, selectedTypeFilter, selectedCategoryFilter, selectedDateFilter) {
        transactionsFlow.filter { trx ->
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
    }

    val isFilterCriteriaActive = selectedTypeFilter != "Semua" || selectedCategoryFilter != "Semua" || selectedDateFilter.isNotEmpty()
    val isFilterActive = searchQuery.isNotEmpty() || isFilterCriteriaActive

    // Trigger choose popup if FAB set it to true
    LaunchedEffect(viewModel.showTabunganFabDialog) {
        if (viewModel.showTabunganFabDialog) {
            if (viewModel.safFolderUriStr.isEmpty()) {
                showSafWarningDialog = true
            } else {
                showChoosePopup = true
            }
            viewModel.showTabunganFabDialog = false
        }
    }

    // Calculations
    val activeTarget = targetFlow
    val targetName = activeTarget?.nama ?: "Tabungan Aktif"
    val targetAmount = activeTarget?.jumlah ?: 0.0

    val totalMasuk = transactionsFlow.filter { it.jenis == "masuk" }.sumOf { it.nominal }
    val totalKeluar = transactionsFlow.filter { it.jenis == "keluar" }.sumOf { it.nominal }
    val currentSaved = totalMasuk - totalKeluar
    val progressPercent = if (targetAmount > 0.0) {
        ((currentSaved / targetAmount) * 100).coerceIn(0.0, 100.0).toInt()
    } else {
        0
    }
    val remainingTarget = (targetAmount - currentSaved).coerceAtLeast(0.0)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("tabungan_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // TOP SUMMARY CONTAINER WITH INTEGRATED INTERACTIVE CHART
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // DOUGHNUT CHART WITH MATH PROPORTIONS VARIABLES
                    val valSisa = remainingTarget
                    val valMasuk = totalMasuk
                    val valKeluar = totalKeluar
                    val sum = valSisa + valMasuk + valKeluar

                    val angleSisa = if (sum > 0) (valSisa / sum * 360f).toFloat() else 120f
                    val angleMasuk = if (sum > 0) (valMasuk / sum * 360f).toFloat() else 120f
                    val angleKeluar = if (sum > 0) (valKeluar / sum * 360f).toFloat() else 120f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: Target Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TARGET: " + targetName.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(
                                    onClick = {
                                        printSavings(
                                            context = context,
                                            targetName = targetName,
                                            targetAmount = targetAmount,
                                            currentSaved = currentSaved,
                                            progressPercent = progressPercent,
                                            transactions = if (isFilterActive && filteredTransactions.isNotEmpty()) filteredTransactions else transactionsFlow
                                        )
                                    },
                                    modifier = Modifier.size(24.dp).testTag("print_savings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Cetak Tabungan",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("Rp %,.0f", targetAmount),
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Ikon Kelola Target & Transaksi (Tanpa Title) di atas title Terkumpul
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = {
                                        if (viewModel.safFolderUriStr.isEmpty()) {
                                            showSafWarningDialog = true
                                        } else {
                                            inputTargetNama = targetName
                                            inputTargetJumlah = targetAmount.toLong().toString()
                                            showTargetFormPopup = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.size(38.dp).testTag("manage_target_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Adjust,
                                            contentDescription = "Kelola Target",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        if (viewModel.safFolderUriStr.isEmpty()) {
                                            showSafWarningDialog = true
                                        } else {
                                            inputTrxJenis = "masuk"
                                            inputTrxKategori = ""
                                            inputTrxDeskripsi = ""
                                            inputTrxNominal = ""
                                            inputTrxTanggal = dateFormater.format(Date())
                                            showTrxFormPopup = true
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp).testTag("add_transaction_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "Tambah Transaksi",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        receiptPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(38.dp).testTag("scan_receipt_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.ReceiptLong,
                                            contentDescription = "Pindai Bukti E-Wallet (GoPay, DANA, OVO)",
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right side: The Doughnut Chart scaled down
                        Box(
                            modifier = Modifier.size(125.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val strokeWidthSisa = if (selectedCategory == "sisa") 14.dp else 10.dp
                            val strokeWidthMasuk = if (selectedCategory == "masuk") 14.dp else 10.dp
                            val strokeWidthKeluar = if (selectedCategory == "keluar") 14.dp else 10.dp

                            Canvas(
                                modifier = Modifier
                                    .size(105.dp)
                                    .pointerInput(angleSisa, angleMasuk, angleKeluar) {
                                        detectTapGestures { offset ->
                                            val dx = offset.x - size.width / 2f
                                            val dy = offset.y - size.height / 2f
                                            val distance = sqrt(dx * dx + dy * dy)
                                            
                                            val outerRadius = size.width / 2f
                                            val innerRadius = outerRadius - 20.dp.toPx()
                                            
                                            if (distance in innerRadius..outerRadius) {
                                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                angle = (angle + 90f + 360f) % 360f
                                                
                                                if (angleSisa > 0f && angle in 0f..angleSisa) {
                                                    selectedCategory = "sisa"
                                                    showDetailPopup = true
                                                } else if (angleMasuk > 0f && angle in angleSisa..(angleSisa + angleMasuk)) {
                                                    selectedCategory = "masuk"
                                                    showDetailPopup = true
                                                } else if (angleKeluar > 0f) {
                                                    selectedCategory = "keluar"
                                                    showDetailPopup = true
                                                }
                                            }
                                        }
                                    }
                            ) {
                                // Draw Sisa Target Arc (Green)
                                if (angleSisa > 0f) {
                                    drawArc(
                                        color = Color(0xFF2E7D32),
                                        startAngle = -90f,
                                        sweepAngle = angleSisa,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidthSisa.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                
                                // Draw Total Masuk Arc (Yellow/Kuning)
                                if (angleMasuk > 0f) {
                                    drawArc(
                                        color = Color(0xFFFBC02D),
                                        startAngle = -90f + angleSisa,
                                        sweepAngle = angleMasuk,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidthMasuk.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                
                                // Draw Total Keluar Arc (Red/Merah)
                                if (angleKeluar > 0f) {
                                    drawArc(
                                        color = Color(0xFFC62828),
                                        startAngle = -90f + angleSisa + angleMasuk,
                                        sweepAngle = angleKeluar,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidthKeluar.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                // Curved text following the circle shape for Sisa Nominal (or active category)
                                val nominalValue = when (selectedCategory) {
                                    "sisa" -> remainingTarget
                                    "masuk" -> totalMasuk
                                    "keluar" -> totalKeluar
                                    else -> remainingTarget
                                }
                                val formattedNominal = String.format("Rp %,.0f", nominalValue)
                                val curvedText = if (selectedCategory == "sisa") "Sisa $formattedNominal" else formattedNominal

                                val textStartAngle = when (selectedCategory) {
                                    "sisa" -> -90f
                                    "masuk" -> -90f + angleSisa
                                    "keluar" -> -90f + angleSisa + angleMasuk
                                    else -> -90f
                                }
                                val textSweepAngle = when (selectedCategory) {
                                    "sisa" -> angleSisa.coerceAtLeast(40f)
                                    "masuk" -> angleMasuk.coerceAtLeast(40f)
                                    "keluar" -> angleKeluar.coerceAtLeast(40f)
                                    else -> 120f
                                }
                                val textColorHex = when (selectedCategory) {
                                    "sisa" -> "#1B5E20"
                                    "masuk" -> "#F57F17"
                                    "keluar" -> "#B71C1C"
                                    else -> "#1B5E20"
                                }

                                val textMargin = 6.dp.toPx()
                                val arcRect = android.graphics.RectF(
                                    textMargin,
                                    textMargin,
                                    size.width - textMargin,
                                    size.height - textMargin
                                )
                                val textArcPath = android.graphics.Path().apply {
                                    addArc(arcRect, textStartAngle, textSweepAngle)
                                }

                                val pathMeasure = android.graphics.PathMeasure(textArcPath, false)
                                val pathLength = pathMeasure.length

                                if (pathLength > 0f) {
                                    val textPaint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        color = android.graphics.Color.parseColor(textColorHex)
                                        textSize = 8.5.sp.toPx()
                                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }

                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawTextOnPath(
                                            curvedText,
                                            textArcPath,
                                            pathLength / 2f,
                                            -2.dp.toPx(),
                                            textPaint
                                        )
                                    }
                                }
                            }

                            // Text center of Doughnut Chart
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val centerTitle = when (selectedCategory) {
                                    "sisa" -> "Sisa"
                                    "masuk" -> "Masuk"
                                    "keluar" -> "Keluar"
                                    else -> "Ringkasan"
                                }
                                val centerValue = when (selectedCategory) {
                                    "sisa" -> String.format("Rp %,.0f", remainingTarget)
                                    "masuk" -> String.format("Rp %,.0f", totalMasuk)
                                    "keluar" -> String.format("Rp %,.0f", totalKeluar)
                                    else -> "Rp 0"
                                }
                                val centerColor = when (selectedCategory) {
                                    "sisa" -> Color(0xFF2E7D32)
                                    "masuk" -> Color(0xFFFBC02D)
                                    "keluar" -> Color(0xFFC62828)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                
                                Text(
                                    text = centerTitle,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = centerValue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = centerColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Terkumpul: Rp " + String.format("%,.0f", currentSaved),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$progressPercent% Lengkap", 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // INTERACTIVE LEGENDS (Clickable buttons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendChip(
                            label = "Sisa Target",
                            color = Color(0xFF2E7D32),
                            isSelected = selectedCategory == "sisa",
                            onClick = { 
                                selectedCategory = "sisa"
                                showDetailPopup = true
                            }
                        )
                        LegendChip(
                            label = "Total Masuk",
                            color = Color(0xFFFBC02D),
                            isSelected = selectedCategory == "masuk",
                            onClick = { 
                                selectedCategory = "masuk"
                                showDetailPopup = true
                            }
                        )
                        LegendChip(
                            label = "Total Keluar",
                            color = Color(0xFFC62828),
                            isSelected = selectedCategory == "keluar",
                            onClick = { 
                                selectedCategory = "keluar"
                                showDetailPopup = true
                            }
                        )
                    }
                }
            }
        }


        if (transactionsFlow.isEmpty()) {
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
                            "Belum ada pencatatan transaksi tabungan.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // HEADER BAR: TITLE, SEARCH ICON (KACA PEMBESAR), AND FILTER ICON
            item {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Catatan Transaksi",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    "${filteredTransactions.size} Transaksi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // ICON KACA PEMBESAR (PENCARIAN)
                            IconButton(
                                onClick = {
                                    isSearchExpanded = !isSearchExpanded
                                },
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
                        }
                    }

                    // EXPANDABLE SEARCH TEXT FIELD (HANYA MUNCUL KETIKA ICON KACA PEMBESAR DIKLIK / ADA PENCARIAN)
                    AnimatedVisibility(
                        visible = isSearchExpanded || searchQuery.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari catatan atau keterangan...", fontSize = 12.sp) },
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
                                    Icon(Icons.Default.Close, contentDescription = "Tutup pencarian", modifier = Modifier.size(16.dp))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                    }

                    // ACTIVE FILTER CRITERIA CHIPS & RESET QUICK ACTION
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

            if (filteredTransactions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tidak ada transaksi yang cocok dengan filter atau pencarian.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedTypeFilter = "Semua"
                                    selectedCategoryFilter = "Semua"
                                    selectedDateFilter = ""
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reset Semua Filter", fontSize = 12.sp)
                            }
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
                        limit = 4,
                        onPaginationClick = {
                            viewModel.currentRoute = "semua_catatan_tabungan"
                        }
                    )
                }
            }
        }
    }

    // ════════════════════════════════════════
    // DIALOG POPUPS
    // ════════════════════════════════════════

    // Detail Pop-up untuk Klik Grafis Warna / Legenda
    if (showDetailPopup) {
        Dialog(onDismissRequest = { showDetailPopup = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val icon = when (selectedCategory) {
                        "sisa" -> Icons.Default.Flag
                        "masuk" -> Icons.Default.TrendingUp
                        "keluar" -> Icons.Default.TrendingDown
                        else -> Icons.Default.Flag
                    }
                    val themeColor = when (selectedCategory) {
                        "sisa" -> Color(0xFF2E7D32)
                        "masuk" -> Color(0xFFFBC02D)
                        "keluar" -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val bgThemeColor = when (selectedCategory) {
                        "sisa" -> Color(0xFF2E7D32).copy(alpha = 0.12f)
                        "masuk" -> Color(0xFFFBC02D).copy(alpha = 0.12f)
                        "keluar" -> Color(0xFFC62828).copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    }
                    val title = when (selectedCategory) {
                        "sisa" -> "Sisa Target"
                        "masuk" -> "Total Masuk"
                        "keluar" -> "Total Keluar"
                        else -> "Ringkasan"
                    }
                    val amountText = when (selectedCategory) {
                        "sisa" -> String.format("Rp %,.0f", remainingTarget)
                        "masuk" -> String.format("Rp %,.0f", totalMasuk)
                        "keluar" -> String.format("Rp %,.0f", totalKeluar)
                        else -> "Rp 0"
                    }
                    val description = when (selectedCategory) {
                        "sisa" -> "Sisa target dana yang perlu Anda kumpulkan untuk mencapai target \"" + targetName + "\" yaitu Rp " + String.format("%,.0f", targetAmount) + ". Kurang Rp " + String.format("%,.0f", remainingTarget) + " lagi (${100 - progressPercent}% sisa)."
                        "masuk" -> {
                            val countMasuk = transactionsFlow.count { it.jenis == "masuk" }
                            "Seluruh dana tabungan masuk yang berhasil dicatatkan. Terdiri dari akumulasi sebanyak $countMasuk transaksi uang masuk."
                        }
                        "keluar" -> {
                            val countKeluar = transactionsFlow.count { it.jenis == "keluar" }
                            "Seluruh pengeluaran atau penarikan dana dari tabungan yang pernah dilakukan. Terdiri dari akumulasi sebanyak $countKeluar transaksi uang keluar."
                        }
                        else -> ""
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(bgThemeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = amountText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = themeColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showDetailPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tutup",
                            color = if (selectedCategory == "masuk") Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dialog Opsi Pilihan 2 Menu Instan (Bottom Navigation FAB clicked)
    if (showChoosePopup) {
        Dialog(onDismissRequest = { showChoosePopup = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Kelola Tabungan Instan",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pilih menu aksi yang ingin Anda lakukan terkait target tabungan saat ini.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option 1: Target Baru
                    Button(
                        onClick = {
                            showChoosePopup = false
                            inputTargetNama = targetName
                            inputTargetJumlah = targetAmount.toLong().toString()
                            showTargetFormPopup = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Adjust, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Input Target Baru")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Transaksi Baru
                    Button(
                        onClick = {
                            showChoosePopup = false
                            inputTrxJenis = "masuk"
                            inputTrxKategori = ""
                            inputTrxDeskripsi = ""
                            inputTrxNominal = ""
                            inputTrxTanggal = dateFormater.format(Date())
                            showTrxFormPopup = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Input Transaksi Baru")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 3: Pindai Bukti Transaksi E-Wallet / Bank
                    OutlinedButton(
                        onClick = {
                            showChoosePopup = false
                            receiptPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReceiptLong, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pindai Bukti E-Wallet (GoPay/DANA/OVO)")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { showChoosePopup = false }) {
                        Text("Tutup Batal")
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

    // Dialog Input Target Baru
    if (showTargetFormPopup) {
        Dialog(onDismissRequest = { showTargetFormPopup = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Atur Target Tabungan Baru",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputTargetNama,
                        onValueChange = { inputTargetNama = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama Target Tabungan *") },
                        placeholder = { Text("Dana darurat, Umroh, dsb.") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputTargetJumlah,
                        onValueChange = { inputTargetJumlah = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Jumlah Target (Rp) *") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTargetFormPopup = false }) {
                            Text("Batal")
                        }
                        Button(onClick = {
                            val targetAmtVal = inputTargetJumlah.toDoubleOrNull() ?: 0.0
                            if (inputTargetNama.isNotEmpty() && targetAmtVal > 0) {
                                viewModel.setSavingTarget(
                                    SavingTarget(id = 1, nama = inputTargetNama, jumlah = targetAmtVal)
                                )
                                showTargetFormPopup = false
                                feedbackBannerMessage = "Target \"$inputTargetNama\" berhasil disimpan!"
                                feedbackBannerIcon = Icons.Default.CheckCircle
                                feedbackBannerColor = Color(0xFF10B981)
                            }
                        }) {
                            Text("Simpan Target")
                        }
                    }
                }
            }
        }
    }

    // Dialog Input Transaksi Baru / Edit
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
                                } else {
                                    viewModel.addSavingTransaction(
                                        SavingTransaction(
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
                                feedbackBannerMessage = "Transaksi tabungan berhasil disimpan!"
                                feedbackBannerIcon = Icons.Default.CheckCircle
                                feedbackBannerColor = Color(0xFF10B981)
                            }
                        }) {
                            Text("Simpan Transaksi")
                        }
                    }
                }
            }
        }
    }

    // Dialog Pemrosesan Bukti Pembayaran / Transaksi E-Wallet (GoPay, DANA, OVO, Bank) dari Menu Sheet OS
    if (viewModel.showSharedReceiptDialog) {
        SharedReceiptProcessDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.showSharedReceiptDialog = false
                viewModel.sharedReceiptUri = null
                viewModel.sharedReceiptText = null
            },
            onSaveSuccess = { msg ->
                feedbackBannerMessage = msg
                feedbackBannerIcon = Icons.Default.CheckCircle
                feedbackBannerColor = Color(0xFF10B981)
            }
        )
    }

    if (showSafWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSafWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Penyimpanan SAF Diperlukan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Untuk mengelola target atau menambahkan data transaksi tabungan lokal kustom Anda, " +
                     "Anda harus mengaktifkan/memilih Folder Penyimpanan utama (SAF) terlebih dahulu di tab Profil " +
                     "agar seluruh data dicadangkan dengan aman.")
            },
            confirmButton = {
                Button(onClick = {
                    showSafWarningDialog = false
                    viewModel.currentRoute = "profil"
                }) {
                    Text("Ke Pengaturan Profil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafWarningDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (trxToDelete != null) {
        AlertDialog(
            onDismissRequest = { trxToDelete = null },
            title = {
                Text(
                    text = "Hapus Transaksi Tabungan?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus transaksi \"${trxToDelete?.deskripsi?.ifEmpty { trxToDelete?.kategori }}\" sebesar Rp ${String.format("%,.0f", trxToDelete?.nominal)}? Tindakan ini tidak dapat dibatalkan.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedDesc = trxToDelete?.deskripsi?.ifEmpty { trxToDelete?.kategori } ?: "Transaksi"
                        trxToDelete?.let { viewModel.deleteSavingTransaction(it) }
                        trxToDelete = null
                        feedbackBannerMessage = "Transaksi \"$deletedDesc\" berhasil dihapus."
                        feedbackBannerIcon = Icons.Default.Delete
                        feedbackBannerColor = Color(0xFFEF4444)
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

    // DIALOG MENU LIST FILTER CATATAN TABUNGAN
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

// PRINT SAVINGS UTILITY
fun printSavings(
    context: Context,
    targetName: String,
    targetAmount: Double,
    currentSaved: Double,
    progressPercent: Int,
    transactions: List<SavingTransaction>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Catatan Tabungan - My Singghara"

    val remainingTarget = (targetAmount - currentSaved).coerceAtLeast(0.0)
    val totalMasuk = transactions.filter { it.jenis == "masuk" }.sumOf { it.nominal }
    val totalKeluar = transactions.filter { it.jenis == "keluar" }.sumOf { it.nominal }

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; color: #333; }
                h1 { text-align: center; color: #222; margin-bottom: 5px; font-size: 20px; }
                .meta-time { text-align: center; color: #666; margin-top: 5px; margin-bottom: 25px; font-size: 11px; }
                .summary-box {
                    border: 1px solid #ddd;
                    border-radius: 6px;
                    padding: 15px;
                    background-color: #f9f9f9;
                    margin-bottom: 20px;
                    line-height: 1.6;
                }
                .summary-box h3 { margin: 0 0 8px 0; color: #007bff; font-size: 14px; border-bottom: 1px solid #ddd; padding-bottom: 6px; }
                .summary-box p { text-align: left; margin: 4px 0; color: #444; font-size: 12px; }
                .grid { display: flex; justify-content: space-between; }
                .grid-col { flex: 1; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f7f7f7; font-weight: bold; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fafafa; }
                .badge-masuk { color: green; font-weight: bold; background-color: #e6fced; padding: 2px 6px; border-radius: 4px; display: inline-block; }
                .badge-keluar { color: red; font-weight: bold; background-color: #ffebe9; padding: 2px 6px; border-radius: 4px; display: inline-block; }
            </style>
        </head>
        <body>
            <h1>Catatan Tabungan - My Singghara</h1>
            <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            
            <div class="summary-box">
                <h3>Informasi Target Tabungan</h3>
                <div class="grid">
                    <div class="grid-col">
                        <p>Nama Target: <strong>$targetName</strong></p>
                        <p>Jumlah Target: <strong>Rp ${String.format("%,.0f", targetAmount)}</strong></p>
                        <p>Sisa Target: <strong>Rp ${String.format("%,.0f", remainingTarget)}</strong></p>
                    </div>
                    <div class="grid-col">
                        <p>Total Terkumpul: <strong>Rp ${String.format("%,.0f", currentSaved)}</strong> ($progressPercent%)</p>
                        <p>Total Uang Masuk: <span style="color: green; font-weight: bold;">Rp ${String.format("%,.0f", totalMasuk)}</span></p>
                        <p>Total Uang Keluar: <span style="color: red; font-weight: bold;">Rp ${String.format("%,.0f", totalKeluar)}</span></p>
                    </div>
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">#</th>
                        <th style="width: 15%;">Tanggal</th>
                        <th style="width: 15%;">Jenis</th>
                        <th style="width: 20%;">Kategori</th>
                        <th style="width: 25%;">Keterangan</th>
                        <th style="width: 20%;" class="text-right">Nominal</th>
                    </tr>
                </thead>
                <tbody>
    """)

    transactions.forEachIndexed { index, trx ->
        val badgeClass = if (trx.jenis == "masuk") "badge-masuk" else "badge-keluar"
        val jenisText = if (trx.jenis == "masuk") "Masuk" else "Keluar"
        htmlBuilder.append("""
            <tr>
                <td>${index + 1}</td>
                <td>${trx.tanggal}</td>
                <td><span class="$badgeClass">$jenisText</span></td>
                <td>${trx.kategori}</td>
                <td>${trx.deskripsi.ifEmpty { "—" }}</td>
                <td class="text-right" style="font-weight: bold;">Rp ${String.format("%,.0f", trx.nominal)}</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="5" class="text-right">Total Pemasukan (Masuk):</td>
                        <td class="text-right" style="color: green;">Rp ${String.format("%,.0f", totalMasuk)}</td>
                    </tr>
                    <tr class="total-row">
                        <td colspan="5" class="text-right">Total Pengeluaran (Keluar):</td>
                        <td class="text-right" style="color: red;">Rp ${String.format("%,.0f", totalKeluar)}</td>
                    </tr>
                    <tr class="total-row" style="background-color: #eef5ff;">
                        <td colspan="5" class="text-right" style="color: #0056b3;">Saldo Saat Ini (Terkumpul):</td>
                        <td class="text-right" style="color: #0056b3; font-size: 11px;">Rp ${String.format("%,.0f", currentSaved)}</td>
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

@Composable
fun SavingTable(
    transactions: List<SavingTransaction>,
    onEdit: (SavingTransaction) -> Unit,
    onDelete: (SavingTransaction) -> Unit,
    limit: Int = Int.MAX_VALUE,
    onPaginationClick: (() -> Unit)? = null
) {
    var currentPage by remember(transactions) { mutableStateOf(0) }
    val itemsPerPage = if (limit == Int.MAX_VALUE) transactions.size.coerceAtLeast(1) else limit
    val totalPages = (transactions.size + itemsPerPage - 1) / itemsPerPage
    val paginatedTransactions = transactions.drop(currentPage * itemsPerPage).take(itemsPerPage)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(160.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Keterangan/Deskripsi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Jenis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("Jumlah", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Body Rows
                    paginatedTransactions.forEach { trx ->
                        val isIncome = trx.jenis == "masuk"
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(trx.tanggal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(160.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(trx.deskripsi.ifEmpty { "-" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isIncome) "Uang Masuk" else "Uang Keluar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier
                                        .background(
                                            if (isIncome) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(trx.kategori, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(
                                    text = (if (isIncome) "+" else "-") + "Rp ${String.format("%,.0f", trx.nominal)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEdit(trx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(trx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pagination Controls
        if (limit != Int.MAX_VALUE && transactions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sebelumnya", fontSize = 12.sp)
                }

                Text(
                    text = "Halaman ${currentPage + 1} dari ${maxOf(1, totalPages)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = onPaginationClick != null) { onPaginationClick?.invoke() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                TextButton(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1
                ) {
                    Text("Selanjutnya", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun LegendChip(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .testTag("legend_chip_$label")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SharedReceiptProcessDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSaveSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val uri = viewModel.sharedReceiptUri
    val sharedText = viewModel.sharedReceiptText

    var isAnalyzing by remember { mutableStateOf(true) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    var jenis by remember { mutableStateOf("keluar") }
    var nominalStr by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("Lainnya") }
    var deskripsi by remember { mutableStateOf("") }
    var tanggal by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var detectedPlatform by remember { mutableStateOf("E-Wallet/Bank") }
    var rawExtractedText by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }

    val categoriesMap = mapOf(
        "masuk" to listOf("Gaji", "Bonus", "Hadiah", "Investasi", "Lainnya"),
        "keluar" to listOf("Kebutuhan Pokok", "Tagihan", "Transportasi", "Pendidikan", "Gaya Hidup", "Lainnya")
    )

    LaunchedEffect(uri, sharedText) {
        isAnalyzing = true
        analysisError = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = if (uri != null) {
                    ReceiptScannerHelper.parseReceiptImage(context, uri)
                } else if (!sharedText.isNullOrBlank()) {
                    ReceiptScannerHelper.parseReceiptText(sharedText)
                } else {
                    null
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (result != null) {
                        jenis = result.jenis
                        nominalStr = if (result.nominal > 0) result.nominal.toLong().toString() else ""
                        kategori = result.kategori
                        deskripsi = result.deskripsi
                        tanggal = result.tanggal
                        detectedPlatform = result.platformName
                        rawExtractedText = result.rawText
                    } else {
                        analysisError = "Bukti transaksi tidak dapat dibaca otomatis. Silakan masukkan data secara manual."
                    }
                    isAnalyzing = false
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    analysisError = e.message ?: "Terjadi kesalahan saat memproses gambar"
                    isAnalyzing = false
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("shared_receipt_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Catat Bukti Transaksi",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Menu Sheet OS • ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = detectedPlatform,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Gambar Bukti Preview
                if (uri != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Pratinjau Bukti Transaksi",
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Status Analisis OCR
                if (isAnalyzing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Menganalisis Bukti Pembayaran...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Mengekstrak nominal, jenis, tanggal, dan penerima via OCR",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (analysisError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                text = analysisError ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Bukti berhasil dianalisis otomatis! Periksa & simpan data di bawah ini.",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Jenis Transaksi (Masuk vs Keluar)
                Text(
                    text = "Jenis Transaksi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isMasuk = jenis == "masuk"
                    Surface(
                        onClick = {
                            jenis = "masuk"
                            if (!categoriesMap["masuk"]!!.contains(kategori)) {
                                kategori = categoriesMap["masuk"]!!.first()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMasuk) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isMasuk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f).testTag("select_jenis_masuk")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (isMasuk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Uang Masuk (+)",
                                fontWeight = if (isMasuk) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isMasuk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isKeluar = jenis == "keluar"
                    Surface(
                        onClick = {
                            jenis = "keluar"
                            if (!categoriesMap["keluar"]!!.contains(kategori)) {
                                kategori = categoriesMap["keluar"]!!.first()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isKeluar) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isKeluar) Color(0xFFC62828) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f).testTag("select_jenis_keluar")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (isKeluar) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Uang Keluar (-)",
                                fontWeight = if (isKeluar) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isKeluar) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input Nominal
                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        nominalStr = digits
                    },
                    label = { Text("Nominal Transaksi") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_shared_nominal"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input Tanggal
                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Tanggal Transaksi (YYYY-MM-DD)") },
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_shared_tanggal"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Kategori Selector
                Text(
                    text = "Kategori",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val availableCategories = categoriesMap[jenis] ?: listOf("Lainnya")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableCategories.forEach { cat ->
                        val isSelected = kategori == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { kategori = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Deskripsi / Keterangan
                OutlinedTextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Keterangan / Deskripsi") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_shared_deskripsi"),
                    singleLine = false,
                    maxLines = 3
                )

                if (rawExtractedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { showRawText = !showRawText },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (showRawText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showRawText) "Sembunyikan Teks OCR Asli" else "Lihat Teks OCR Asli yang Terbaca",
                            fontSize = 11.sp
                        )
                    }

                    if (showRawText) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rawExtractedText,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    val nominalValue = nominalStr.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (nominalValue > 0) {
                                val tglFinal = tanggal.ifBlank {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                }
                                val newTrx = SavingTransaction(
                                    jenis = jenis,
                                    kategori = kategori.ifBlank { "Lainnya" },
                                    deskripsi = deskripsi.ifBlank { "Transaksi $detectedPlatform" },
                                    nominal = nominalValue,
                                    tanggal = tglFinal
                                )
                                viewModel.addSavingTransaction(newTrx)
                                val jenisLabel = if (jenis == "masuk") "Pemasukan" else "Pengeluaran"
                                onSaveSuccess("$jenisLabel Rp %,.0f berhasil dicatat ke Tabungan!".format(nominalValue))
                                onDismiss()
                            }
                        },
                        enabled = nominalValue > 0 && !isAnalyzing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("save_shared_receipt_button")
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


