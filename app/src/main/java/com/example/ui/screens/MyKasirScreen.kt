package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.MainViewModel
import java.text.NumberFormat
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

private fun compressBitmapToBase64(bitmap: Bitmap, maxSize: Int = 400): String {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxSize || height > maxSize) {
            val maxDim = Math.max(width, height).toFloat()
            maxSize / maxDim
        } else 1.0f
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        val scaledBmp = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        val outputStream = java.io.ByteArrayOutputStream()
        scaledBmp.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }
}

private fun uriToCompressedBase64(context: Context, uri: Uri, maxSize: Int = 400): String {
    return try {
        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        if (bitmap != null) compressBitmapToBase64(bitmap, maxSize) else ""
    } catch (e: Exception) {
        ""
    }
}

private fun decodePhotoToBitmap(photoData: String): Bitmap? {
    if (photoData.isBlank()) return null
    if (photoData.startsWith("http://") || photoData.startsWith("https://") ||
        photoData.startsWith("content://") || photoData.startsWith("file://") ||
        photoData.startsWith("android.resource://")) {
        return null
    }
    return try {
        val cleanData = if (photoData.contains(",")) {
            photoData.substringAfter(",")
        } else {
            photoData
        }
        val cleanBase64 = cleanData.replace("\n", "").replace("\r", "").trim()
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ProductPhotoThumbnail(
    photoData: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    placeholder: (@Composable () -> Unit)? = null
) {
    if (photoData.isBlank()) {
        if (placeholder != null) {
            placeholder()
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        return
    }

    val decodedBitmap = remember(photoData) {
        decodePhotoToBitmap(photoData)
    }

    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        AsyncImage(
            model = if (photoData.startsWith("content://") || photoData.startsWith("file://")) {
                Uri.parse(photoData)
            } else {
                photoData
            },
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyKasirScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sharedPrefs = remember { context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE) }

    // Persistent State
    var namaUsaha by remember { mutableStateOf(sharedPrefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya") }
    var listStock by remember {
        mutableStateOf(deserializeStock(sharedPrefs.getString("list_stock", "") ?: ""))
    }
    var listCashier by remember {
        mutableStateOf(deserializeCashier(sharedPrefs.getString("list_cashier", "") ?: ""))
    }

    // Helper functions for state saving
    val saveStock = { newList: List<StockItem> ->
        listStock = newList
        sharedPrefs.edit().putString("list_stock", serializeStock(newList)).apply()
    }

    val saveCashier = { newList: List<CashierTransaction> ->
        listCashier = newList
        sharedPrefs.edit().putString("list_cashier", serializeCashier(newList)).apply()
    }

    // Dialog state
    var showStockDialog by remember { mutableStateOf(false) }
    var showEditStockDialog by remember { mutableStateOf(false) }
    var selectedEditStockItem by remember { mutableStateOf<StockItem?>(null) }
    var showCashierDialog by remember { mutableStateOf(false) }
    var showBarcodeCashierDialog by remember { mutableStateOf(false) }
    var showKasirHpScannerDialog by remember { mutableStateOf(false) }
    var showRestockBarcodeDialog by remember { mutableStateOf(false) }
    var showUnduhBelanjaDialog by remember { mutableStateOf(false) }
    var showEditNamaUsahaDialog by remember { mutableStateOf(false) }
    var showPrintReportDialog by remember { mutableStateOf(false) }
    var printerMethod by remember {
        mutableStateOf(sharedPrefs.getString("printer_method", "direct") ?: "direct")
    }
    var printerPaperWidth by remember {
        mutableStateOf(sharedPrefs.getInt("printer_paper_width", 32))
    }
    var previewFotoItem by remember { mutableStateOf<StockItem?>(null) }

    val quickPhotoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null && previewFotoItem != null) {
            val b64 = compressBitmapToBase64(bitmap)
            if (b64.isNotEmpty()) {
                val targetId = previewFotoItem!!.id
                val idx = listStock.indexOfFirst { it.id == targetId }
                if (idx != -1) {
                    val updated = listStock[idx].copy(foto = b64)
                    val mutable = listStock.toMutableList()
                    mutable[idx] = updated
                    saveStock(mutable)
                    previewFotoItem = updated
                    viewModel.showToastPopup("Foto produk '${updated.nama}' berhasil disimpan!")
                }
            }
        }
    }

    val quickPhotoGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && previewFotoItem != null) {
            val b64 = uriToCompressedBase64(context, uri)
            if (b64.isNotEmpty()) {
                val targetId = previewFotoItem!!.id
                val idx = listStock.indexOfFirst { it.id == targetId }
                if (idx != -1) {
                    val updated = listStock[idx].copy(foto = b64)
                    val mutable = listStock.toMutableList()
                    mutable[idx] = updated
                    saveStock(mutable)
                    previewFotoItem = updated
                    viewModel.showToastPopup("Foto produk '${updated.nama}' berhasil disimpan!")
                }
            }
        }
    }

    var showPrinterSettingsDialog by remember { mutableStateOf(false) }

    val savePrinterSettings = { method: String, width: Int ->
        printerMethod = method
        printerPaperWidth = width
        sharedPrefs.edit()
            .putString("printer_method", method)
            .putInt("printer_paper_width", width)
            .apply()
    }

    val triggerPrintReceipt = { tx: CashierTransaction ->
        if (printerMethod == "direct") {
            sendToThermalPrinterApp(context, tx, namaUsaha, printerPaperWidth)
            viewModel.showToastPopup("Mengirim struk ke Aplikasi Thermal Printer...")
        } else {
            printReceiptWeb(context, tx, namaUsaha)
            viewModel.showToastPopup("Mencetak struk via Layanan Cetak Android...")
        }
    }
    val quantitiesBelanja = remember { mutableStateMapOf<String, String>() }

    var searchQueryStock by remember { mutableStateOf("") }
    var isSearchActiveStock by remember { mutableStateOf(false) }
    var selectedCategoryStock by remember { mutableStateOf("Semua") }
    var taxPercentageStock by remember { mutableStateOf(0.0) }
    var taxInputString by remember { mutableStateOf("0") }

    var pendingDeleteType by remember { mutableStateOf<String?>(null) }
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }

    // Formatting rupiah helper (direct IDR)
    val toRupiah = { amount: Double ->
        try {
            val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
        } catch (e: Exception) {
            "Rp " + String.format(Locale.US, "%,.0f", amount)
        }
    }

    val totalCashierSales = listCashier.sumOf { it.totalHarga }

    var subRoute by remember { mutableStateOf("main") }
    var activeTab by remember { mutableStateOf(0) }
    var currentPageStock by remember { mutableStateOf(0) }
    var currentPageCashier by remember { mutableStateOf(0) }
    var currentPageBelanja by remember { mutableStateOf(0) }

    LaunchedEffect(subRoute) {
        viewModel.hideGlobalTopAppBar = (subRoute != "main")
        viewModel.isBottomBarVisible = (subRoute == "main")
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.hideGlobalTopAppBar = false
            viewModel.isBottomBarVisible = true
        }
    }

    androidx.activity.compose.BackHandler(enabled = subRoute != "main") {
        subRoute = "main"
    }

    AnimatedContent(
        targetState = subRoute,
        label = "SubRouteTransition"
    ) { currentSubRoute ->
        when (currentSubRoute) {
            "main" -> {
                Scaffold { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = (innerPadding.calculateTopPadding() + 8.dp),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section 0: Brand New Dashboard Header
                        item {
                            val lowStockCount = listStock.count { it.stok in 1..2 }
                            val outOfStockCount = listStock.count { it.stok <= 0 }
                            CashierDashboardHeader(
                                namaUsaha = namaUsaha,
                                totalSales = totalCashierSales,
                                lowStockCount = lowStockCount,
                                outOfStockCount = outOfStockCount,
                                toRupiah = toRupiah,
                                onManageUsahaClick = { showEditNamaUsahaDialog = true },
                                onCashierInputClick = {
                                    if (listStock.isEmpty()) {
                                        viewModel.showToastPopup("Harap isi stok barang terlebih dahulu!")
                                    } else {
                                        showCashierDialog = true
                                    }
                                },
                                onRestockBarcodeClick = {
                                    if (listStock.isEmpty()) {
                                        viewModel.showToastPopup("Harap isi stok barang terlebih dahulu!")
                                    } else {
                                        showRestockBarcodeDialog = true
                                    }
                                },
                                onBarcodeScanClick = {
                                    if (listStock.isEmpty()) {
                                        viewModel.showToastPopup("Harap isi stok barang terlebih dahulu!")
                                    } else {
                                        showBarcodeCashierDialog = true
                                    }
                                },
                                activeTab = activeTab,
                                onTabChange = { 
                                    activeTab = it
                                    isSearchActiveStock = false
                                    searchQueryStock = ""
                                    selectedCategoryStock = "Semua"
                                },
                                onHpScannerClick = { showKasirHpScannerDialog = true },
                                onPrinterSettingsClick = { showPrinterSettingsDialog = true }
                            )
                        }

                        // Section 1: Active Tab Title Indicator
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val tabTitle = when (activeTab) {
                                        0 -> "Dasbor"
                                        1 -> "Stok"
                                        2 -> "Kasir"
                                        3 -> "Belanja"
                                        else -> ""
                                    }
                                    val tabIcon = when (activeTab) {
                                        0 -> Icons.Default.Dashboard
                                        1 -> Icons.Default.Inventory2
                                        2 -> Icons.Default.ReceiptLong
                                        3 -> Icons.Default.ShoppingBag
                                        else -> Icons.Default.Info
                                    }
                                    Icon(
                                        imageVector = tabIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = tabTitle,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (activeTab == 1) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        var filterMenuExpanded by remember { mutableStateOf(false) }
                                        val categories = listOf("Semua") + listStock.map { it.kategori }.distinct().filter { it.isNotEmpty() }
                                        Box {
                                            IconButton(onClick = { filterMenuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.FilterList,
                                                    contentDescription = "Filter Kategori",
                                                    tint = if (selectedCategoryStock == "Semua") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = filterMenuExpanded,
                                                onDismissRequest = { filterMenuExpanded = false }
                                            ) {
                                                categories.forEach { category ->
                                                    DropdownMenuItem(
                                                        text = { 
                                                            Text(
                                                                text = category,
                                                                fontWeight = if (selectedCategoryStock == category) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (selectedCategoryStock == category) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                            ) 
                                                        },
                                                        onClick = {
                                                            selectedCategoryStock = category
                                                            filterMenuExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (isSearchActiveStock) {
                                            OutlinedTextField(
                                                value = searchQueryStock,
                                                onValueChange = { searchQueryStock = it },
                                                modifier = Modifier.width(150.dp).height(48.dp),
                                                placeholder = { Text("Cari...", fontSize = 11.sp) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                singleLine = true,
                                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                                trailingIcon = {
                                                    IconButton(onClick = { 
                                                        searchQueryStock = ""
                                                        isSearchActiveStock = false
                                                    }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            )
                                        } else {
                                            IconButton(onClick = { isSearchActiveStock = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Cari",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        IconButton(onClick = { showStockDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Tambah",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        IconButton(onClick = {
                                            if (listStock.isEmpty()) {
                                                viewModel.showToastPopup("Harap isi stok barang terlebih dahulu!")
                                            } else {
                                                showRestockBarcodeDialog = true
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = "Stok Ulang Barcode",
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        IconButton(onClick = {
                                            if (listStock.isEmpty()) {
                                                viewModel.showToastPopup("Data stok kosong!")
                                            } else {
                                                printStockList(context, namaUsaha, listStock)
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = "Cetak",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                if (activeTab == 2) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF2E7D32),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                if (listStock.isEmpty()) {
                                                    viewModel.showToastPopup("Harap isi stok barang terlebih dahulu!")
                                                } else {
                                                    showCashierDialog = true
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.PointOfSale,
                                                    contentDescription = "Input Kasir",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Kasir",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                if (activeTab == 3) {
                                    val stockOutList = listStock.filter { it.stok <= 0 }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Daftar Belanja (${stockOutList.size} Habis)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            onClick = {
                                                if (stockOutList.isEmpty()) {
                                                    viewModel.showToastPopup("Daftar belanja kosong!")
                                                } else {
                                                    printShoppingStockOutList(context, namaUsaha, stockOutList)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = "Cetak",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Tab Content Rendering
                        when (activeTab) {
                            0 -> { // Dasbor Tab
                                // Trend Chart & Alerts

                                // Sales Trend
                                item {
                                    RevenueTrendLineChartCard(
                                        listCashier = listCashier,
                                        toRupiah = toRupiah,
                                        onPrintReportClick = {
                                            if (listCashier.isEmpty()) {
                                                viewModel.showToastPopup("Data transaksi kosong, tidak ada laporan yang dicetak!")
                                            } else {
                                                showPrintReportDialog = true
                                            }
                                        }
                                    )
                                }

                                // Warnings
                                item {
                                    val lowStockItems = listStock.filter { it.stok < 3 }
                                    if (lowStockItems.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        "Peringatan Stok Rendah / Habis!",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                    Text(
                                                        "Ada ${lowStockItems.size} produk dengan stok kritis (< 3 unit). Harap restock di tab 'Belanja'.",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> { // Stok / Inventori Tab
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (selectedCategoryStock != "Semua") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Filter Kategori: $selectedCategoryStock",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "(Hapus)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.clickable { selectedCategoryStock = "Semua" }
                                                )
                                            }
                                        }

                                        val filteredStockList = listStock.filter {
                                            (selectedCategoryStock == "Semua" || it.kategori == selectedCategoryStock) &&
                                            (it.nama.contains(searchQueryStock, ignoreCase = true) ||
                                             it.kategori.contains(searchQueryStock, ignoreCase = true) ||
                                             it.barcode.contains(searchQueryStock, ignoreCase = true))
                                        }

                                        LaunchedEffect(selectedCategoryStock, searchQueryStock) {
                                            currentPageStock = 0
                                        }

                                        val itemsPerPage = 4
                                        val totalPages = (filteredStockList.size + itemsPerPage - 1) / itemsPerPage
                                        val paginatedStockList = filteredStockList.drop(currentPageStock * itemsPerPage).take(itemsPerPage)

                                        if (filteredStockList.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                     Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                                                     Spacer(modifier = Modifier.height(8.dp))
                                                     Text(
                                                         "Tidak ada barang ditemukan.",
                                                         fontSize = 12.sp,
                                                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                     )
                                                }
                                            }
                                        } else {
                                            // Pagination Controls
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = (-12).dp)
                                                    .padding(bottom = 0.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = { if (currentPageStock > 0) currentPageStock-- },
                                                    enabled = currentPageStock > 0
                                                ) {
                                                    Text("<- Sebelumnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Text(
                                                    text = "Halaman ${currentPageStock + 1} dari ${maxOf(1, totalPages)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                                                    modifier = Modifier
                                                        .clickable { subRoute = "all_stock_table" }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )

                                                TextButton(
                                                    onClick = { if (currentPageStock < totalPages - 1) currentPageStock++ },
                                                    enabled = currentPageStock < totalPages - 1
                                                ) {
                                                    Text("Selanjutnya ->", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                    Column {
                                                        // Table Header
                                                        Row(
                                                            modifier = Modifier
                                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                                .height(IntrinsicSize.Min),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(modifier = Modifier.width(68.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(200.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Nama Barang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(130.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kode Barang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Keterangan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Harga Jual", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Stok", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                        }

                                                        // Table Body
                                                        paginatedStockList.forEach { item ->
                                                            val realIndex = listStock.indexOfFirst { it.id == item.id }
                                                            Row(
                                                                modifier = Modifier.height(IntrinsicSize.Min),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Box(modifier = Modifier.width(68.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .size(46.dp)
                                                                            .clip(RoundedCornerShape(8.dp))
                                                                            .clickable {
                                                                                previewFotoItem = item
                                                                            },
                                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                                                                    ) {
                                                                        ProductPhotoThumbnail(
                                                                            photoData = item.foto,
                                                                            contentDescription = item.nama,
                                                                            modifier = Modifier.fillMaxSize(),
                                                                            contentScale = ContentScale.Crop,
                                                                            iconSize = 22.dp
                                                                        )
                                                                    }
                                                                }
                                                                Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.tanggal.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.kategori, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                Box(modifier = Modifier.width(200.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                Box(modifier = Modifier.width(130.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.barcode.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.keterangan.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(toRupiah(item.harga), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                                
                                                                val stockColor = when {
                                                                    item.stok <= 0 -> Color(0xFFD32F2F)
                                                                    item.stok <= 3 -> Color(0xFFF57C00)
                                                                    else -> Color(0xFF388E3C)
                                                                }
                                                                Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                    Text(item.stok.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stockColor, maxLines = 1) 
                                                                }

                                                                Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                    Row(
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                selectedEditStockItem = item
                                                                                showEditStockDialog = true
                                                                            },
                                                                            modifier = Modifier.size(28.dp)
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Edit,
                                                                                contentDescription = "Edit",
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                if (realIndex != -1) {
                                                                                    pendingDeleteType = "stok"
                                                                                    pendingDeleteIndex = realIndex
                                                                                }
                                                                            },
                                                                            modifier = Modifier.size(28.dp)
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Delete,
                                                                                contentDescription = "Hapus",
                                                                                tint = MaterialTheme.colorScheme.error,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }


                                                        }
                                                        // Table Footer / Totals Row
                                                        Row(
                                                            modifier = Modifier
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                                .height(IntrinsicSize.Min),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            // Tanggal -> Label "Total"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(110.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Text(
                                                                    text = "Total",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }

                                                            // Kategori -> "Jumlah Kategori"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(140.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                val totalUniqueCategories = filteredStockList.map { it.kategori }.distinct().filter { it.isNotEmpty() }.size
                                                                Text(
                                                                    text = "$totalUniqueCategories Kategori",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }

                                                            // Nama Barang -> "Jumlah Produk"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(200.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                val totalProducts = filteredStockList.size
                                                                Text(
                                                                    text = "$totalProducts Produk",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }

                                                            // Kode Barang -> "Jumlah Kode"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(130.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                val totalCodes = filteredStockList.filter { it.barcode.isNotEmpty() }.size
                                                                Text(
                                                                    text = "$totalCodes Kode",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }

                                                            // Keterangan -> "Pajak % Input"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(180.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Pajak:",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                    BasicTextField(
                                                                        value = taxInputString,
                                                                        onValueChange = { newValue ->
                                                                            if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                                                                taxInputString = newValue
                                                                                taxPercentageStock = newValue.toDoubleOrNull() ?: 0.0
                                                                            }
                                                                        },
                                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.primary
                                                                        ),
                                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                                        modifier = Modifier
                                                                            .width(45.dp)
                                                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                                                            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                                                        singleLine = true
                                                                    )
                                                                    Text(
                                                                        text = "%",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                }
                                                            }

                                                            // Harga Jual -> "Total Harga Jual (Pajak)"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(140.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                val totalHargaRaw = filteredStockList.sumOf { it.harga }
                                                                val taxAmount = totalHargaRaw * (taxPercentageStock / 100.0)
                                                                val totalHargaWithTax = totalHargaRaw + taxAmount
                                                                Text(
                                                                    text = toRupiah(totalHargaWithTax) + if (taxPercentageStock > 0.0) " (+Pajak)" else "",
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    maxLines = 1
                                                                )
                                                            }

                                                            // Stok -> "Jumlah Stok"
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(90.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                contentAlignment = Alignment.CenterStart
                                                            ) {
                                                                val totalStok = filteredStockList.sumOf { it.stok }
                                                                Text(
                                                                    text = totalStok.toString(),
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }

                                                            // Aksi -> Placeholder Box
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(100.dp)
                                                                    .fillMaxHeight()
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                            )
                                                        }
                                                    }
                                                }
                                             }


                                         }
                                        

                                    }
                                }
                            }
                            2 -> { // Riwayat Kasir Tab
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (listCashier.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        "Belum ada transaksi tercatat.",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        } else {
                                            val itemsPerPage = 4
                                            val totalPages = (listCashier.size + itemsPerPage - 1) / itemsPerPage
                                            
                                            // Safeguard currentPageCashier
                                            if (currentPageCashier >= totalPages) {
                                                currentPageCashier = maxOf(0, totalPages - 1)
                                            }
                                            
                                            val paginatedCashierList = listCashier.drop(currentPageCashier * itemsPerPage).take(itemsPerPage)

                                            // Pagination Controls
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = (-12).dp)
                                                    .padding(bottom = 0.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = { if (currentPageCashier > 0) currentPageCashier-- },
                                                    enabled = currentPageCashier > 0
                                                ) {
                                                    Text("<- Sebelumnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Text(
                                                    text = "Halaman ${currentPageCashier + 1} dari ${maxOf(1, totalPages)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                                                    modifier = Modifier
                                                        .clickable { subRoute = "all_cashier_table" }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )

                                                TextButton(
                                                    onClick = { if (currentPageCashier < totalPages - 1) currentPageCashier++ },
                                                    enabled = currentPageCashier < totalPages - 1
                                                ) {
                                                    Text("Selanjutnya ->", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                     Column {
                                                         // Table Header
                                                         Row(
                                                             modifier = Modifier
                                                                 .background(MaterialTheme.colorScheme.primaryContainer)
                                                                 .height(IntrinsicSize.Min),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Barang/Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Harga Jual", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                         }

                                                         // Table Body
                                                         paginatedCashierList.forEach { item ->
                                                             val realIndex = listCashier.indexOfFirst { it.id == item.id }
                                                             Row(
                                                                 modifier = Modifier.height(IntrinsicSize.Min),
                                                                 verticalAlignment = Alignment.CenterVertically
                                                             ) {
                                                                 Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(item.waktu, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                                 }
                                                                 Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(
                                                                         item.items.joinToString(", ") { "${it.namaBarang} (x${it.qty})" }, 
                                                                         fontSize = 12.sp, 
                                                                         fontWeight = FontWeight.Bold, 
                                                                         color = MaterialTheme.colorScheme.onSurface,
                                                                         maxLines = 1
                                                                     ) 
                                                                 }
                                                                 Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(
                                                                         item.items.joinToString(", ") { it.kategori.ifEmpty { "-" } }, 
                                                                         fontSize = 12.sp, 
                                                                         color = MaterialTheme.colorScheme.onSurface,
                                                                         maxLines = 1
                                                                     ) 
                                                                 }
                                                                 Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(
                                                                         toRupiah(item.totalHarga), 
                                                                         fontSize = 12.sp, 
                                                                         fontWeight = FontWeight.Bold,
                                                                         color = MaterialTheme.colorScheme.primary,
                                                                         maxLines = 1
                                                                     )
                                                                 }
                                                                 Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                                     Row(
                                                                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                         verticalAlignment = Alignment.CenterVertically
                                                                     ) {
                                                                         IconButton(
                                                                             onClick = {
                                                                                 triggerPrintReceipt(item)
                                                                             },
                                                                             modifier = Modifier.size(28.dp)
                                                                         ) {
                                                                             Icon(
                                                                                 Icons.Default.Print,
                                                                                 contentDescription = "Cetak",
                                                                                 tint = MaterialTheme.colorScheme.primary,
                                                                                 modifier = Modifier.size(16.dp)
                                                                             )
                                                                         }
                                                                         IconButton(
                                                                             onClick = {
                                                                                 if (realIndex != -1) {
                                                                                     pendingDeleteType = "kasir"
                                                                                     pendingDeleteIndex = realIndex
                                                                                 }
                                                                             },
                                                                             modifier = Modifier.size(28.dp)
                                                                         ) {
                                                                             Icon(
                                                                                 Icons.Default.Delete,
                                                                                 contentDescription = "Hapus",
                                                                                 tint = MaterialTheme.colorScheme.error,
                                                                                 modifier = Modifier.size(16.dp)
                                                                             )
                                                                         }
                                                                     }
                                                                 }
                                                             }
                                                         }

                                                         // Table Footer - Total Omset Penjualan
                                                         Row(
                                                             modifier = Modifier
                                                                 .background(Color(0xFF2E7D32).copy(alpha = 0.08f))
                                                                 .height(IntrinsicSize.Min),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Box(
                                                                 modifier = Modifier
                                                                     .width(620.dp)
                                                                     .fillMaxHeight()
                                                                     .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                     .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                 contentAlignment = Alignment.CenterEnd
                                                             ) {
                                                                 Text(
                                                                     "Total Omset Penjualan:",
                                                                     fontSize = 11.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     color = Color(0xFF2E7D32)
                                                                 )
                                                             }
                                                             Box(
                                                                 modifier = Modifier
                                                                     .width(240.dp)
                                                                     .fillMaxHeight()
                                                                     .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                                     .padding(horizontal = 12.dp, vertical = 10.dp),
                                                                 contentAlignment = Alignment.CenterStart
                                                             ) {
                                                                 Text(
                                                                     toRupiah(totalCashierSales),
                                                                     fontSize = 12.sp,
                                                                     fontWeight = FontWeight.Black,
                                                                     color = Color(0xFF2E7D32)
                                                                 )
                                                             }
                                                         }
                                                     }
                                                }
                                             }

                                             // Total Omset Penjualan Banner below Table
                                             Card(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .padding(top = 8.dp),
                                                 shape = RoundedCornerShape(10.dp),
                                                 colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.08f)),
                                                 border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
                                             ) {
                                                 Row(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .padding(horizontal = 14.dp, vertical = 12.dp),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Row(verticalAlignment = Alignment.CenterVertically) {
                                                         Icon(
                                                             imageVector = Icons.Default.Payments,
                                                             contentDescription = null,
                                                             tint = Color(0xFF2E7D32),
                                                             modifier = Modifier.size(20.dp)
                                                         )
                                                         Spacer(modifier = Modifier.width(8.dp))
                                                         Text(
                                                             text = "Total Omset Penjualan",
                                                             fontSize = 12.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             color = Color(0xFF2E7D32)
                                                         )
                                                     }
                                                     Text(
                                                         text = toRupiah(totalCashierSales),
                                                         fontSize = 15.sp,
                                                         fontWeight = FontWeight.Black,
                                                         color = Color(0xFF2E7D32)
                                                     )
                                                 }
                                             }


                                         }
                                    }
                                }
                            }
                            3 -> { // Belanja Tab
                                item {
                                    val stockOutList = listStock.filter { it.stok <= 0 }
                                    
                                    if (stockOutList.isEmpty()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        "Semua stok produk aman! Tidak ada stok kosong.",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val itemsPerPage = 4
                                        val totalPages = (stockOutList.size + itemsPerPage - 1) / itemsPerPage
                                        
                                        // Safeguard currentPageBelanja
                                        if (currentPageBelanja >= totalPages) {
                                            currentPageBelanja = maxOf(0, totalPages - 1)
                                        }
                                        
                                        val paginatedShoppingList = stockOutList.drop(currentPageBelanja * itemsPerPage).take(itemsPerPage)

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Pagination Controls
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(y = (-12).dp)
                                                    .padding(bottom = 0.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = { if (currentPageBelanja > 0) currentPageBelanja-- },
                                                    enabled = currentPageBelanja > 0
                                                ) {
                                                    Text("<- Sebelumnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }

                                                Text(
                                                    text = "Halaman ${currentPageBelanja + 1} dari ${maxOf(1, totalPages)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .clickable { subRoute = "all_belanja_table" }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )

                                                TextButton(
                                                    onClick = { if (currentPageBelanja < totalPages - 1) currentPageBelanja++ },
                                                    enabled = currentPageBelanja < totalPages - 1
                                                ) {
                                                    Text("Selanjutnya ->", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                ) {
                                                     Column {
                                                         // Table Header
                                                         Row(
                                                             modifier = Modifier
                                                                 .background(MaterialTheme.colorScheme.primaryContainer)
                                                                 .height(IntrinsicSize.Min),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Barang/Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                             Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                                         }

                                                         // Table Body
                                                         paginatedShoppingList.forEach { item ->
                                                             Row(
                                                                 modifier = Modifier.height(IntrinsicSize.Min),
                                                                 verticalAlignment = Alignment.CenterVertically
                                                             ) {
                                                                 Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(item.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                                 }
                                                                 Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                                     Text(item.kategori.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                                 }
                                                                 Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { 
                                                                     Box(
                                                                         modifier = Modifier
                                                                             .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                                                             .padding(horizontal = 8.dp, vertical = 4.dp),
                                                                         contentAlignment = Alignment.Center
                                                                     ) {
                                                                         Text("HABIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                                                     }
                                                                 }
                                                             }
                                                         }
                                                     }
                                                }
                                            }


                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "all_stock" -> {
                Scaffold(
                    topBar = {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .statusBarsPadding()
                        ) {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Stok Barang",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { subRoute = "main" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali ke My Kasir")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        if (listStock.isEmpty()) {
                                            viewModel.showToastPopup("Data stok barang kosong, tidak ada yang dicetak!")
                                        } else {
                                            printStockList(context, namaUsaha, listStock)
                                        }
                                    }) {
                                        Icon(Icons.Default.Print, contentDescription = "Cetak Stok", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { showStockDialog = true }) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Tambah Stok Barang", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = (innerPadding.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Low Stock Alert (if any)
                        val lowStockItems = listStock.filter { it.stok < 3 }
                        if (lowStockItems.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Peringatan Stok Rendah",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Peringatan Stok Rendah!",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                "Ada ${lowStockItems.size} produk dengan stok kritis (< 3 unit): " +
                                                lowStockItems.joinToString(", ") { "${it.nama} (${it.stok})" },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Search Box & Summary Row
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    OutlinedTextField(
                                        value = searchQueryStock,
                                        onValueChange = { searchQueryStock = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Cari berdasarkan nama, kategori, atau barcode...", fontSize = 13.sp) },
                                        label = { Text("Cari Barang", fontSize = 12.sp) },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", modifier = Modifier.size(20.dp)) },
                                        trailingIcon = {
                                            if (searchQueryStock.isNotEmpty()) {
                                                IconButton(onClick = { searchQueryStock = "" }) {
                                                    Icon(Icons.Default.Close, contentDescription = "Bersihkan", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                    )
                                }
                            }
                        }

                        // 3. Filtered Stock List Items
                        val filteredStockList = listStock.filter {
                            it.nama.contains(searchQueryStock, ignoreCase = true) ||
                            it.kategori.contains(searchQueryStock, ignoreCase = true) ||
                            it.barcode.contains(searchQueryStock, ignoreCase = true)
                        }

                        if (filteredStockList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Tidak ada barang yang cocok dengan kata kunci \"$searchQueryStock\".",
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(filteredStockList) { _, item ->
                                val realIndex = listStock.indexOfFirst { it.id == item.id }
                                StockItemRow(
                                    item = item,
                                    onDelete = {
                                        if (realIndex != -1) {
                                            pendingDeleteType = "stok"
                                            pendingDeleteIndex = realIndex
                                        }
                                    },
                                    toRupiah = toRupiah,
                                    onPhotoClick = { previewFotoItem = item }
                                )
                            }
                        }
                    }
                }
            }
            "all_stock_table" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Tabel Seluruh Stok Barang",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (listStock.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Data stok kosong.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        // Table Header
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .height(IntrinsicSize.Min),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.width(68.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(200.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Nama Barang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(130.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kode Barang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Keterangan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Harga Jual", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Stok", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                        }

                                        // Table Body
                                        listStock.forEach { item ->
                                            val realIndex = listStock.indexOfFirst { it.id == item.id }
                                            Row(
                                                modifier = Modifier.height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.width(68.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .size(46.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                previewFotoItem = item
                                                            },
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
                                                    ) {
                                                        ProductPhotoThumbnail(
                                                            photoData = item.foto,
                                                            contentDescription = item.nama,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Crop,
                                                            iconSize = 22.dp
                                                        )
                                                    }
                                                }
                                                Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.tanggal.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.kategori, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                Box(modifier = Modifier.width(200.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                Box(modifier = Modifier.width(130.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.barcode.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(item.keterangan.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { Text(toRupiah(item.harga), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) }
                                                
                                                val stockColor = when {
                                                    item.stok <= 0 -> Color(0xFFD32F2F)
                                                    item.stok <= 3 -> Color(0xFFF57C00)
                                                    else -> Color(0xFF388E3C)
                                                }
                                                Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(item.stok.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stockColor) 
                                                }

                                                Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                selectedEditStockItem = item
                                                                showEditStockDialog = true
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Edit",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (realIndex != -1) {
                                                                    pendingDeleteType = "stok"
                                                                    pendingDeleteIndex = realIndex
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
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
                        }
                    }
                }
            }
            "all_belanja_table" -> {
                val stockOutList = listStock.filter { it.stok <= 0 }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Seluruh Daftar Belanja",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (stockOutList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Semua stok produk aman! Tidak ada stok kosong.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        // Table Header
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .height(IntrinsicSize.Min),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Barang/Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                        }

                                        // Table Body
                                        stockOutList.forEach { item ->
                                            Row(
                                                modifier = Modifier.height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(item.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                }
                                                Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(item.kategori.ifEmpty { "-" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                }
                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { 
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("HABIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "all_cashier_table" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Seluruh Riwayat Transaksi Kasir",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (listCashier.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Belum ada transaksi tercatat.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    ) {
                                        // Table Header
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .height(IntrinsicSize.Min),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Tanggal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Barang/Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) { Text("Harga Jual", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1) }
                                        }

                                        // Table Body
                                        listCashier.forEach { item ->
                                            val realIndex = listCashier.indexOfFirst { it.id == item.id }
                                            Row(
                                                modifier = Modifier.height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(item.waktu, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1) 
                                                }
                                                Box(modifier = Modifier.width(300.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(
                                                        item.items.joinToString(", ") { "${it.namaBarang} (x${it.qty})" }, 
                                                        fontSize = 12.sp, 
                                                        fontWeight = FontWeight.Bold, 
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    ) 
                                                }
                                                Box(modifier = Modifier.width(180.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(
                                                        item.items.joinToString(", ") { it.kategori.ifEmpty { "-" } }, 
                                                        fontSize = 12.sp, 
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1
                                                    ) 
                                                }
                                                Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) { 
                                                    Text(
                                                        toRupiah(item.totalHarga), 
                                                        fontSize = 12.sp, 
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1
                                                    )
                                                }
                                                Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = {
                                                                triggerPrintReceipt(item)
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Print,
                                                                contentDescription = "Cetak",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (realIndex != -1) {
                                                                    pendingDeleteType = "kasir"
                                                                    pendingDeleteIndex = realIndex
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
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
                        }
                    }
                }
            }
            "all_cashier" -> {
                Scaffold(
                    topBar = {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .statusBarsPadding()
                        ) {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Pendapatan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { subRoute = "main" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali ke My Kasir")
                                    }
                                },
                                actions = {
                                    Button(
                                        onClick = {
                                            if (listStock.isEmpty()) {
                                                viewModel.showToastPopup("Harap isi stok barang terlebih dahulu di Section atas!")
                                            } else {
                                                showCashierDialog = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Input Kasir",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = (innerPadding.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Total Sales & Chart Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Total Omset Penjualan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32).copy(alpha = 0.8f))
                                    Text(toRupiah(totalCashierSales), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                                    
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 7-day Daily Income Trend Chart Card
                                    val sdfDayChart = SimpleDateFormat("dd MMM", Locale.getDefault())
                                    val daysListChart = (0..6).map { i ->
                                        val cal = java.util.Calendar.getInstance()
                                        cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                                        sdfDayChart.format(cal.time)
                                    }.reversed()

                                    val dailyIncomeMapChart = daysListChart.associateWith { dayStr ->
                                        listCashier.filter { tx ->
                                            tx.waktu.contains(dayStr)
                                        }.sumOf { it.totalHarga }
                                    }

                                    val maxIncomeChart = dailyIncomeMapChart.values.maxOrNull() ?: 0.0

                                    val formatChartAmt = { amt: Double ->
                                        when {
                                            amt >= 1_000_000.0 -> {
                                                val formatted = amt / 1_000_000.0
                                                if (formatted % 1.0 == 0.0) String.format(Locale.US, "%.0fjt", formatted)
                                                else String.format(Locale.US, "%.1fjt", formatted)
                                            }
                                            amt >= 1_000.0 -> {
                                                val formatted = amt / 1_000.0
                                                if (formatted % 1.0 == 0.0) String.format(Locale.US, "%.0frb", formatted)
                                                else String.format(Locale.US, "%.1frb", formatted)
                                            }
                                            amt > 0.0 -> amt.toInt().toString()
                                            else -> ""
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.12f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Tren Pendapatan 7 Hari",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                val peakInPeriod = dailyIncomeMapChart.values.maxOrNull() ?: 0.0
                                                Text(
                                                    "Puncak: " + toRupiah(peakInPeriod),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF2E7D32).copy(alpha = 0.8f)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(14.dp))
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(110.dp)
                                                    .padding(horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                daysListChart.forEach { day ->
                                                    val income = dailyIncomeMapChart[day] ?: 0.0
                                                    val barHeightFraction = if (maxIncomeChart > 0) (income / maxIncomeChart).toFloat() else 0f
                                                    
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.Bottom
                                                    ) {
                                                        val topLabel = formatChartAmt(income)
                                                        Text(
                                                            text = topLabel,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2E7D32),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth(0.6f)
                                                                .height(if (barHeightFraction > 0.03f) (barHeightFraction * 65).dp else 4.dp)
                                                                .background(
                                                                    color = if (barHeightFraction > 0f) Color(0xFF4CAF50) else Color.LightGray.copy(alpha = 0.4f),
                                                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        
                                                        Text(
                                                            text = day,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Header "Riwayat Transaksi"
                        item {
                            Text(
                                "Riwayat Transaksi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }

                        // 2. Transaction List Items
                        if (listCashier.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Belum ada transaksi tercatat.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(listCashier) { index, item ->
                                SalesTransactionRow(
                                    item = item,
                                    onPrintReceipt = {
                                        triggerPrintReceipt(item)
                                    },
                                    onDelete = {
                                        pendingDeleteType = "kasir"
                                        pendingDeleteIndex = index
                                    },
                                    toRupiah = toRupiah
                                )
                            }
                        }
                    }
                }
            }
            "all_shopping" -> {
                Scaffold(
                    topBar = {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .statusBarsPadding()
                        ) {
                            TopAppBar(
                                title = {
                                    Text(
                                        "Daftar Belanja",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { subRoute = "main" }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali ke My Kasir")
                                    }
                                },
                                actions = {
                                    val stockOutList = listStock.filter { it.stok <= 0 }
                                    if (stockOutList.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                printShoppingStockOutList(context, namaUsaha, stockOutList)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Print,
                                                contentDescription = "Cetak Daftar Belanja",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.height(48.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = (innerPadding.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp),
                                bottom = innerPadding.calculateBottomPadding() + 16.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            val stockOutList = listStock.filter { it.stok <= 0 }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (stockOutList.isEmpty()) {
                                        Text(
                                            "Semua stok barang tercukupi! Tidak ada barang habis saat ini.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            "Berikut seluruh barang dengan stok habis (stok = 0). Harus segera dibeli kembali:",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            stockOutList.forEach { item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.onError.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                                        Text("Kategori : ${item.kategori}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .width(60.dp)
                                                            .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                                            .padding(vertical = 2.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("HABIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                                    }

                                                }
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = { showUnduhBelanjaDialog = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Konfigurasi & Unduh Daftar Belanja", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // Dialog 0: Ubah Nama Usaha
        if (showEditNamaUsahaDialog) {
            var tempNamaUsaha by remember { mutableStateOf(namaUsaha) }
            AlertDialog(
                onDismissRequest = { showEditNamaUsahaDialog = false },
                title = { Text("Ubah Nama Usaha", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Masukkan nama toko atau usaha Anda untuk laporan cetak & struk:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = tempNamaUsaha,
                            onValueChange = { tempNamaUsaha = it },
                            placeholder = { Text("Contoh: Toko Berkah") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempNamaUsaha.trim().isNotEmpty()) {
                                namaUsaha = tempNamaUsaha.trim()
                                sharedPrefs.edit().putString("nama_usaha", tempNamaUsaha.trim()).apply()
                                showEditNamaUsahaDialog = false
                                viewModel.showToastPopup("Nama usaha berhasil diubah!")
                            } else {
                                viewModel.showToastPopup("Nama usaha tidak boleh kosong!")
                            }
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNamaUsahaDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog: Pratinjau Foto Produk Besar
        if (previewFotoItem != null) {
            val previewItem = previewFotoItem!!
            Dialog(
                onDismissRequest = { previewFotoItem = null }
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = previewItem.nama,
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { previewFotoItem = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            ProductPhotoThumbnail(
                                photoData = previewItem.foto,
                                contentDescription = previewItem.nama,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                iconSize = 64.dp
                            )
                        }

                        // Tombol Aksi Kamera & Galeri Cepat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { quickPhotoCameraLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (previewItem.foto.isEmpty()) "Ambil Foto" else "Ganti Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { quickPhotoGalleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dari Galeri", fontSize = 11.sp)
                            }
                            if (previewItem.foto.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        val targetId = previewItem.id
                                        val idx = listStock.indexOfFirst { it.id == targetId }
                                        if (idx != -1) {
                                            val updated = listStock[idx].copy(foto = "")
                                            val mutable = listStock.toMutableList()
                                            mutable[idx] = updated
                                            saveStock(mutable)
                                            previewFotoItem = updated
                                            viewModel.showToastPopup("Foto produk '${updated.nama}' dihapus.")
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus Foto", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Kategori:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(previewItem.kategori.ifEmpty { "Umum" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Harga Jual:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(toRupiah(previewItem.harga), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Sisa Stok:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${previewItem.stok} unit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (previewItem.barcode.isNotEmpty()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Barcode:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(previewItem.barcode, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Button(
                            onClick = { previewFotoItem = null },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Tutup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Dialog: Edit Detail Barang
        if (showEditStockDialog && selectedEditStockItem != null) {
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            val itemToEdit = selectedEditStockItem!!
            var editTanggalBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.tanggal.ifEmpty { sdf.format(Date()) }) }
            var editFotoBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.foto) }
            var editNamaBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.nama) }
            var editBarcodeBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.barcode) }
            var editActiveScannerForStock by remember { mutableStateOf(false) }
            var editKategoriBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.kategori) }
            var editKeteranganBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.keterangan) }
            var editHargaBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.harga.toLong().toString()) }
            var editStokBarang by remember(itemToEdit) { mutableStateOf(itemToEdit.stok.toString()) }
            var editCatDropdownExpanded by remember { mutableStateOf(false) }

            val editCameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicturePreview()
            ) { bitmap ->
                if (bitmap != null) {
                    val b64 = compressBitmapToBase64(bitmap)
                    if (b64.isNotEmpty()) {
                        editFotoBarang = b64
                    }
                }
            }

            val editGalleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    val b64 = uriToCompressedBase64(context, uri)
                    if (b64.isNotEmpty()) {
                        editFotoBarang = b64
                    }
                }
            }

            val standardCategories = listOf(
                "Fashion",
                "Produk Kecantikan & Kesehatan",
                "Elektronik",
                "Bahan Makanan & Kebutuhan Harian",
                "Perlengkapan Rumah Tangga",
                "Hiburan, Mainan & Alat Tulis",
                "Jasa Sewa & Rental"
            )

            Dialog(
                onDismissRequest = { 
                    showEditStockDialog = false
                    selectedEditStockItem = null
                }
            ) {
                val dialogFocusManager = LocalFocusManager.current
                val dialogKeyboardController = LocalSoftwareKeyboardController.current
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Text(
                            text = "Edit Detail Barang",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Foto Produk & Akses Kamera
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Foto Produk (Kamera / Galeri)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    if (editFotoBarang.isNotEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ProductPhotoThumbnail(
                                                photoData = editFotoBarang,
                                                contentDescription = "Foto Produk",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { editFotoBarang = "" },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Hapus Foto",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AddAPhoto,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { editCameraLauncher.launch(null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ambil Foto (Kamera)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { editGalleryLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pilih dari Galeri", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Category Selection
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Kategori Barang", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { editCatDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (editKategoriBarang.isEmpty()) "-- Pilih Kategori Barang --" else editKategoriBarang,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (editKategoriBarang.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = editCatDropdownExpanded,
                                    onDismissRequest = { editCatDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                ) {
                                    standardCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                editKategoriBarang = cat
                                                editCatDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Product Name
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Nama Barang / Produk", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = editNamaBarang,
                                onValueChange = { editNamaBarang = it },
                                label = { Text("Nama Barang") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) })
                            )
                        }

                        // Tanggal Barang
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tanggal Input / Restock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            var showDatePickerDialogEdit by remember { mutableStateOf(false) }
                            val showDatePicker = { showDatePickerDialogEdit = true }

                            if (showDatePickerDialogEdit) {
                                com.example.ui.components.CustomDatePickerDialog(
                                    initialDateStr = editTanggalBarang,
                                    onDismissRequest = { showDatePickerDialogEdit = false },
                                    onDateSelected = { selectedDate ->
                                        editTanggalBarang = selectedDate
                                        showDatePickerDialogEdit = false
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = editTanggalBarang,
                                onValueChange = { editTanggalBarang = it },
                                label = { Text("Tanggal Input / Restock (YYYY-MM-DD)") },
                                placeholder = { Text("Contoh: 2026-07-25") },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker() }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Dari Kalender")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        // Barcode Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Barcode / Kode Barang (Opsional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editBarcodeBarang,
                                    onValueChange = { editBarcodeBarang = it },
                                    placeholder = { Text("Ketik atau Pindai Barcode") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) })
                                )
                                IconButton(
                                    onClick = { editActiveScannerForStock = true },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                        .size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Pindai dengan Kamera",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (editActiveScannerForStock) {
                            BarcodeScannerDialog(
                                onDismissRequest = { editActiveScannerForStock = false },
                                onBarcodeScanned = { barcode ->
                                    editBarcodeBarang = barcode
                                    editActiveScannerForStock = false
                                }
                            )
                        }

                        // Keterangan
                        OutlinedTextField(
                            value = editKeteranganBarang,
                            onValueChange = { editKeteranganBarang = it },
                            label = { Text("Keterangan") },
                            placeholder = { Text("Warna, Ukuran, atau deskripsi singkat") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Harga Jual
                        OutlinedTextField(
                            value = editHargaBarang,
                            onValueChange = { editHargaBarang = it },
                            label = { Text("Harga Jual (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Jumlah Stok (Direct Edit)
                        OutlinedTextField(
                            value = editStokBarang,
                            onValueChange = { editStokBarang = it },
                            label = { Text("Jumlah Stok") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                                dialogFocusManager.clearFocus() 
                                dialogKeyboardController?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { 
                                    showEditStockDialog = false
                                    selectedEditStockItem = null
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Batal")
                            }
                            
                            Button(
                                onClick = {
                                    val priceVal = editHargaBarang.toDoubleOrNull() ?: 0.0
                                    val stockVal = editStokBarang.toIntOrNull() ?: 0
                                    val trimmedName = editNamaBarang.trim()

                                    if (trimmedName.isNotBlank() && editKategoriBarang.isNotBlank() && priceVal >= 0.0 && stockVal >= 0) {
                                        val existingIndex = listStock.indexOfFirst { it.id == itemToEdit.id }
                                        if (existingIndex != -1) {
                                            val updatedItem = itemToEdit.copy(
                                                nama = trimmedName,
                                                kategori = editKategoriBarang.trim(),
                                                keterangan = editKeteranganBarang.trim(),
                                                harga = priceVal,
                                                stok = stockVal,
                                                barcode = editBarcodeBarang.trim(),
                                                tanggal = editTanggalBarang,
                                                foto = editFotoBarang
                                            )
                                            val mutable = listStock.toMutableList()
                                            mutable[existingIndex] = updatedItem
                                            saveStock(mutable)
                                            showEditStockDialog = false
                                            selectedEditStockItem = null
                                            viewModel.showToastPopup("Barang berhasil diperbarui!")
                                        }
                                    } else {
                                        viewModel.showToastPopup("Mohon lengkapi semua isian dengan valid!")
                                    }
                                }
                            ) {
                                Text("Simpan")
                            }
                        }
                    }
                }
            }
        }

        // Dialog: Scanner HP Kasir (Remote Barcode Gun untuk Kasir Web PC)
        if (showKasirHpScannerDialog) {
            KasirRemoteBarcodeScannerDialog(
                onDismissRequest = { showKasirHpScannerDialog = false },
                onBarcodeScanned = { barcode ->
                    viewModel.pushKasirBarcodeScan(barcode)
                    viewModel.showToastPopup("Barcode terkirim ke Kasir PC: $barcode")
                }
            )
        }

        // Dialog 1: Input Stok Barang
        if (showStockDialog) {
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
            var tanggalBarang by remember { mutableStateOf(sdf.format(Date())) }
            var fotoBarang by remember { mutableStateOf("") }
            var namaBarang by remember { mutableStateOf("") }
            var barcodeBarang by remember { mutableStateOf("") }
            var activeScannerForStock by remember { mutableStateOf(false) }
            var kategoriBarang by remember { mutableStateOf("") }
            var keteranganBarang by remember { mutableStateOf("") }
            var hargaBarang by remember { mutableStateOf("") }
            var stokBarang by remember { mutableStateOf("") }
            var catDropdownExpanded by remember { mutableStateOf(false) }
            var nameDropdownExpanded by remember { mutableStateOf(false) }

            val stockCameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicturePreview()
            ) { bitmap ->
                if (bitmap != null) {
                    val b64 = compressBitmapToBase64(bitmap)
                    if (b64.isNotEmpty()) {
                        fotoBarang = b64
                    }
                }
            }

            val stockGalleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    val b64 = uriToCompressedBase64(context, uri)
                    if (b64.isNotEmpty()) {
                        fotoBarang = b64
                    }
                }
            }

            val standardCategories = listOf(
                "Fashion",
                "Produk Kecantikan & Kesehatan",
                "Elektronik",
                "Bahan Makanan & Kebutuhan Harian",
                "Perlengkapan Rumah Tangga",
                "Hiburan, Mainan & Alat Tulis",
                "Jasa Sewa & Rental"
            )

            // Auto-fill lookup helper when scanning/typing barcode
            val onBarcodeUpdated = { code: String ->
                barcodeBarang = code
                if (code.trim().isNotEmpty()) {
                    val matched = listStock.find { it.barcode.trim().equals(code.trim(), ignoreCase = true) }
                    if (matched != null) {
                        kategoriBarang = matched.kategori
                        namaBarang = matched.nama
                        keteranganBarang = matched.keterangan
                        hargaBarang = matched.harga.toLong().toString()
                        if (matched.tanggal.isNotEmpty()) {
                            tanggalBarang = matched.tanggal
                        }
                        if (matched.foto.isNotEmpty()) {
                            fotoBarang = matched.foto
                        }
                        viewModel.showToastPopup("Produk ditemukan: ${matched.nama}")
                    }
                }
            }

            Dialog(
                onDismissRequest = { showStockDialog = false }
            ) {
                val dialogFocusManager = LocalFocusManager.current
                val dialogKeyboardController = LocalSoftwareKeyboardController.current
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Title
                        Text(
                            text = "Input & Stok Ulang Barang",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Foto Produk & Akses Kamera
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Foto Produk (Kamera / Galeri)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ) {
                                    if (fotoBarang.isNotEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ProductPhotoThumbnail(
                                                photoData = fotoBarang,
                                                contentDescription = "Foto Produk",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { fotoBarang = "" },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(22.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp))
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Hapus Foto",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AddAPhoto,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { stockCameraLauncher.launch(null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ambil Foto (Kamera)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { stockGalleryLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pilih dari Galeri", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Category Selection
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Kategori Barang", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { catDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (kategoriBarang.isEmpty()) "-- Pilih Kategori Barang --" else kategoriBarang,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (kategoriBarang.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = catDropdownExpanded,
                                    onDismissRequest = { catDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                ) {
                                    standardCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                kategoriBarang = cat
                                                catDropdownExpanded = false
                                                namaBarang = "" // Reset name selection on category change
                                                hargaBarang = ""
                                                keteranganBarang = ""
                                                fotoBarang = ""
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Product Name
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Nama Barang / Produk", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = namaBarang,
                                    onValueChange = { newValue ->
                                        namaBarang = newValue
                                        // Live check if typed name matches any existing item case-insensitively
                                        val matched = listStock.find { it.nama.trim().equals(newValue.trim(), ignoreCase = true) }
                                        if (matched != null) {
                                            kategoriBarang = matched.kategori
                                            keteranganBarang = matched.keterangan
                                            hargaBarang = matched.harga.toLong().toString()
                                            barcodeBarang = matched.barcode
                                            if (matched.tanggal.isNotEmpty()) {
                                                tanggalBarang = matched.tanggal
                                            }
                                            if (matched.foto.isNotEmpty()) {
                                                fotoBarang = matched.foto
                                            }
                                        }
                                    },
                                    label = { Text(if (kategoriBarang.isEmpty()) "Nama Barang (Pilih Kategori Terlebih Dahulu)" else "Nama Barang") },
                                    trailingIcon = {
                                        if (kategoriBarang.isNotEmpty()) {
                                            val filteredList = listStock.filter { it.kategori.equals(kategoriBarang, ignoreCase = true) }
                                            if (filteredList.isNotEmpty()) {
                                                IconButton(onClick = { nameDropdownExpanded = true }) {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih dari Daftar")
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) })
                                )

                                if (kategoriBarang.isNotEmpty()) {
                                    val filteredList = listStock.filter { it.kategori.equals(kategoriBarang, ignoreCase = true) }
                                    if (filteredList.isNotEmpty()) {
                                        DropdownMenu(
                                            expanded = nameDropdownExpanded,
                                            onDismissRequest = { nameDropdownExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            filteredList.forEach { stock ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(stock.nama, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                            Text("Stok: ${stock.stok} | ${toRupiah(stock.harga)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    },
                                                    onClick = {
                                                        namaBarang = stock.nama
                                                        kategoriBarang = stock.kategori
                                                        keteranganBarang = stock.keterangan
                                                        hargaBarang = stock.harga.toLong().toString()
                                                        barcodeBarang = stock.barcode
                                                        if (stock.tanggal.isNotEmpty()) {
                                                            tanggalBarang = stock.tanggal
                                                        }
                                                        if (stock.foto.isNotEmpty()) {
                                                            fotoBarang = stock.foto
                                                        }
                                                        nameDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tanggal Barang
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Tanggal Input / Restock", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            var showDatePickerDialogAdd by remember { mutableStateOf(false) }
                            val showDatePicker = { showDatePickerDialogAdd = true }

                            if (showDatePickerDialogAdd) {
                                com.example.ui.components.CustomDatePickerDialog(
                                    initialDateStr = tanggalBarang,
                                    onDismissRequest = { showDatePickerDialogAdd = false },
                                    onDateSelected = { selectedDate ->
                                        tanggalBarang = selectedDate
                                        showDatePickerDialogAdd = false
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = tanggalBarang,
                                onValueChange = { tanggalBarang = it },
                                label = { Text("Tanggal Input / Restock (YYYY-MM-DD)") },
                                placeholder = { Text("Contoh: 2026-07-25") },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker() }) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih Tanggal Dari Kalender")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }

                        // Barcode Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Barcode / Kode Barang (Opsional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = barcodeBarang,
                                    onValueChange = { onBarcodeUpdated(it) },
                                    placeholder = { Text("Ketik atau Pindai Barcode") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) })
                                )
                                IconButton(
                                    onClick = { activeScannerForStock = true },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                        .size(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Pindai dengan Kamera",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (activeScannerForStock) {
                            BarcodeScannerDialog(
                                onDismissRequest = { activeScannerForStock = false },
                                onBarcodeScanned = { barcode ->
                                    try {
                                        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            try { tg.release() } catch (_: Exception) {}
                                        }, 250)
                                    } catch (_: Exception) {}
                                    onBarcodeUpdated(barcode)
                                    activeScannerForStock = false
                                }
                            )
                        }

                        // Keterangan
                        OutlinedTextField(
                            value = keteranganBarang,
                            onValueChange = { keteranganBarang = it },
                            label = { Text("Keterangan") },
                            placeholder = { Text("Warna, Ukuran, atau deskripsi singkat") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Harga Jual
                        OutlinedTextField(
                            value = hargaBarang,
                            onValueChange = { hargaBarang = it },
                            label = { Text("Harga Jual (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Down) }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Jumlah Stok Baru
                        OutlinedTextField(
                            value = stokBarang,
                            onValueChange = { stokBarang = it },
                            label = { Text("Tambahan / Jumlah Stok Baru") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                                dialogFocusManager.clearFocus() 
                                dialogKeyboardController?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons (inside the scrollable Column!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showStockDialog = false },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Batal")
                            }
                            
                            Button(
                                onClick = {
                                    val priceVal = hargaBarang.toDoubleOrNull() ?: 0.0
                                    val stockVal = stokBarang.toIntOrNull() ?: 0
                                    val trimmedName = namaBarang.trim()

                                    if (trimmedName.isNotBlank() && kategoriBarang.isNotBlank() && priceVal > 0.0 && stockVal >= 0) {
                                        // Search if the item already exists case-insensitively
                                        val existingIndex = listStock.indexOfFirst {
                                            it.nama.trim().equals(trimmedName, ignoreCase = true)
                                        }

                                        if (existingIndex != -1) {
                                            // Update existing item to prevent duplication, restock it and update price
                                            val matchedItem = listStock[existingIndex]
                                            val updatedStock = matchedItem.stok + stockVal
                                            val updatedItem = matchedItem.copy(
                                                nama = matchedItem.nama, // Keep consistent casing
                                                kategori = kategoriBarang.trim(),
                                                keterangan = keteranganBarang.trim(),
                                                harga = priceVal,
                                                stok = updatedStock,
                                                barcode = if (barcodeBarang.trim().isNotEmpty()) barcodeBarang.trim() else matchedItem.barcode,
                                                tanggal = tanggalBarang,
                                                foto = if (fotoBarang.isNotEmpty()) fotoBarang else matchedItem.foto
                                            )
                                            val mutable = listStock.toMutableList()
                                            mutable[existingIndex] = updatedItem
                                            saveStock(mutable)
                                            showStockDialog = false
                                            viewModel.showToastPopup("Stok '${matchedItem.nama}' berhasil ditambah! Sisa Stok sekarang: $updatedStock")
                                        } else {
                                            // Add as a completely new item
                                            val newItem = StockItem(
                                                nama = trimmedName,
                                                kategori = kategoriBarang.trim(),
                                                keterangan = keteranganBarang.trim(),
                                                harga = priceVal,
                                                stok = stockVal,
                                                barcode = barcodeBarang.trim(),
                                                tanggal = tanggalBarang,
                                                foto = fotoBarang
                                            )
                                            val mutable = listStock.toMutableList()
                                            mutable.add(newItem)
                                            saveStock(mutable)
                                            showStockDialog = false
                                            viewModel.showToastPopup("Barang baru berhasil ditambahkan ke stok!")
                                        }
                                    } else {
                                        viewModel.showToastPopup("Mohon lengkapi semua isian dengan valid!")
                                    }
                                }
                            ) {
                                Text("Simpan dan Update")
                            }
                        }
                    }
                }
            }
        }

        // Dialog 2: Input Kasir Penjualan
        if (showCashierDialog) {
            var namaBarang by remember { mutableStateOf("") }
            var kategoriBarang by remember { mutableStateOf("") }
            var inputQty by remember { mutableStateOf("1") }
            var customHarga by remember { mutableStateOf("") }
            
            var nameDropdownExpanded by remember { mutableStateOf(false) }
            var catDropdownExpanded by remember { mutableStateOf(false) }

            var scannedInputCode by remember { mutableStateOf("") }
            var activeScannerForCashier by remember { mutableStateOf(false) }
            var pendingBarcodeItem by remember { mutableStateOf<StockItem?>(null) }
            var scannedBarcodeQtyInput by remember { mutableStateOf("1") }
            val barcodeFocusRequester = remember { FocusRequester() }

            val standardCategories = listOf(
                "Fashion",
                "Produk Kecantikan & Kesehatan",
                "Elektronik",
                "Bahan Makanan & Kebutuhan Harian",
                "Perlengkapan Rumah Tangga",
                "Hiburan, Mainan & Alat Tulis",
                "Jasa Sewa & Rental"
            )

            // Local cart items list for the current transaction session
            val cartItems = remember { mutableStateListOf<CartItem>() }

            // Tax and Cash states
            var taxEnabled by remember { mutableStateOf(true) }
            var taxRateInput by remember { mutableStateOf("11") }
            var uangTunaiInput by remember { mutableStateOf("") }

            // Computed values (re-evaluated on recomposition)
            val subtotal = cartItems.sumOf { it.totalHarga }
            val taxRatePercent = if (taxEnabled) (taxRateInput.toDoubleOrNull() ?: 0.0) else 0.0
            val ppn = subtotal * (taxRatePercent / 100.0)
            val grandTotal = subtotal + ppn
            val uangTunai = uangTunaiInput.toDoubleOrNull() ?: 0.0

            // Auto lookup of current active stock item if name matches exactly
            val activeStockItem = listStock.find { it.nama.trim().equals(namaBarang.trim(), ignoreCase = true) }

            // Autofocus on barcode input field when dialog opens to support instant OTG/hardware scanning
            LaunchedEffect(Unit) {
                try {
                    barcodeFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Safe fallback
                }
            }

            val playBarcodeBeepSuccess = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 250)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val playBarcodeBeepError = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 250)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 350)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val processBarcode = { code: String ->
                val trimmedCode = code.trim()
                if (trimmedCode.isNotEmpty()) {
                    val foundItem = listStock.find { it.barcode.trim() == trimmedCode }
                    if (foundItem != null) {
                        if (foundItem.stok <= 0) {
                            playBarcodeBeepError()
                            viewModel.showToastPopup("Gagal: Sisa stok untuk '${foundItem.nama}' habis (0)!")
                        } else {
                            playBarcodeBeepSuccess()
                            val existingInCart = cartItems.find { it.namaBarang == foundItem.nama }
                            val initialQty = existingInCart?.qty ?: 1
                            scannedBarcodeQtyInput = initialQty.toString()
                            pendingBarcodeItem = foundItem
                        }
                    } else {
                        playBarcodeBeepError()
                        viewModel.showToastPopup("Barcode '$trimmedCode' tidak terdaftar di stok!")
                    }
                    scannedInputCode = "" // Reset scanner input field
                }
            }

            if (activeScannerForCashier) {
                BarcodeScannerDialog(
                    onDismissRequest = { activeScannerForCashier = false },
                    onBarcodeScanned = { barcode ->
                        processBarcode(barcode)
                        activeScannerForCashier = false
                    }
                )
            }

            if (pendingBarcodeItem != null) {
                val item = pendingBarcodeItem!!
                val qtyVal = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                val calculatedTotal = item.harga * qtyVal

                AlertDialog(
                    onDismissRequest = { pendingBarcodeItem = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32)
                            )
                            Text("Input Jumlah (Qty) Barang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        ProductPhotoThumbnail(
                                            photoData = item.foto,
                                            contentDescription = item.nama,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            iconSize = 24.dp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Kategori: ${item.kategori.ifEmpty { "-" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Harga Satuan: ${toRupiah(item.harga)}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                        Text("Stok Tersedia: ${item.stok} unit", fontSize = 12.sp, color = if (item.stok < 5) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Text("Tentukan Jumlah Pembelian (Qty):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = {
                                        val current = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                                        if (current > 1) {
                                            scannedBarcodeQtyInput = (current - 1).toString()
                                        }
                                    },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Kurang")
                                }

                                OutlinedTextField(
                                    value = scannedBarcodeQtyInput,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { it.isDigit() }) {
                                            scannedBarcodeQtyInput = input
                                        }
                                    },
                                    modifier = Modifier
                                        .width(90.dp)
                                        .padding(horizontal = 8.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                IconButton(
                                    onClick = {
                                        val current = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                                        if (current < item.stok) {
                                            scannedBarcodeQtyInput = (current + 1).toString()
                                        } else {
                                            viewModel.showToastPopup("Batas stok maksimum tercapai (${item.stok})")
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color(0xFF2E7D32))
                                }
                            }

                            HorizontalDivider()

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Total Subtotal:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(toRupiah(calculatedTotal), fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val finalQty = scannedBarcodeQtyInput.toIntOrNull() ?: 0
                                if (finalQty <= 0) {
                                    viewModel.showToastPopup("Jumlah Qty harus minimal 1!")
                                } else if (finalQty > item.stok) {
                                    viewModel.showToastPopup("Gagal: Qty ($finalQty) melebihi stok yang tersedia (${item.stok})!")
                                } else {
                                    val existingIndex = cartItems.indexOfFirst { it.namaBarang == item.nama }
                                    if (existingIndex != -1) {
                                        cartItems[existingIndex] = cartItems[existingIndex].copy(qty = finalQty)
                                    } else {
                                        cartItems.add(
                                            CartItem(
                                                namaBarang = item.nama,
                                                kategori = item.kategori,
                                                qty = finalQty,
                                                harga = item.harga
                                            )
                                        )
                                    }
                                    viewModel.showToastPopup("Berhasil memasukkan '${item.nama}' ($finalQty x) ke keranjang!")
                                    pendingBarcodeItem = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Tambahkan ke Keranjang")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { pendingBarcodeItem = null }) {
                            Text("Batal")
                        }
                    }
                )
            }

            AlertDialog(
                onDismissRequest = { 
                    cartItems.clear()
                    showCashierDialog = false 
                },
                title = { Text("Kasir Transaksi Pembayaran", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    val dialogFocusManager = LocalFocusManager.current
                    val dialogKeyboardController = LocalSoftwareKeyboardController.current
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("TAMBAH ITEM BARU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                                
                                // Barcode Input & Scanner Section
                                Text("PINDAI BARCODE / KODE BARANG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = scannedInputCode,
                                        onValueChange = { scannedInputCode = it },
                                        placeholder = { Text("Pindai dengan alat OTG atau Kamera...") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(barcodeFocusRequester),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                processBarcode(scannedInputCode)
                                            }
                                        )
                                    )

                                    IconButton(
                                        onClick = { activeScannerForCashier = true },
                                        modifier = Modifier
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Pindai Barcode Kamera",
                                            tint = Color(0xFF2E7D32)
                                        )
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                                Text("Nama Barang / Produk:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = namaBarang,
                                        onValueChange = { namaBarang = it },
                                        placeholder = { Text("Ketik atau Pilih Barang") },
                                        trailingIcon = {
                                            IconButton(onClick = { nameDropdownExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Barang")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Next) })
                                    )

                                    DropdownMenu(
                                        expanded = nameDropdownExpanded,
                                        onDismissRequest = { nameDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        val filteredStock = if (kategoriBarang.isEmpty()) {
                                            listStock
                                        } else {
                                            listStock.filter { it.kategori.equals(kategoriBarang, ignoreCase = true) }
                                        }

                                        if (filteredStock.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text(if (kategoriBarang.isEmpty()) "-- Stok kosong, ketik manual --" else "-- Tidak ada barang di kategori ini --", color = Color.Gray, fontSize = 12.sp) },
                                                onClick = { nameDropdownExpanded = false }
                                            )
                                        } else {
                                            filteredStock.forEach { stock ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                            Text(stock.nama, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                            Text("stok: ${stock.stok} | ${toRupiah(stock.harga)}", fontSize = 10.sp, color = if (stock.stok <= 0) Color.Red else Color.Unspecified)
                                                        }
                                                    },
                                                    onClick = {
                                                        namaBarang = stock.nama
                                                        kategoriBarang = stock.kategori
                                                        customHarga = stock.harga.toLong().toString()
                                                        nameDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Dropdown for Category
                                Text("Kategori Barang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedCard(
                                        onClick = { catDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (kategoriBarang.isEmpty()) "-- Pilih Kategori --" else kategoriBarang,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (kategoriBarang.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = catDropdownExpanded,
                                        onDismissRequest = { catDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth(0.75f)
                                    ) {
                                        standardCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat) },
                                                onClick = {
                                                    kategoriBarang = cat
                                                    catDropdownExpanded = false
                                                    nameDropdownExpanded = true
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customHarga,
                                        onValueChange = { customHarga = it },
                                        label = { Text("Harga", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Next) }),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = inputQty,
                                        onValueChange = { inputQty = it },
                                        label = { Text("Qty", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { 
                                            dialogFocusManager.clearFocus() 
                                            dialogKeyboardController?.hide()
                                        }),
                                        modifier = Modifier.weight(0.6f),
                                        singleLine = true
                                    )
                                }

                                val qtyVal = inputQty.toIntOrNull() ?: 0
                                val hrgVal = customHarga.toDoubleOrNull() ?: 0.0

                                if (qtyVal > 0 && hrgVal > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Subtotal: ${toRupiah(qtyVal * hrgVal)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            if (activeStockItem != null && qtyVal > activeStockItem.stok) {
                                                Text("Stok Kurang (Sisa: ${activeStockItem.stok})", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (namaBarang.isNotBlank() && kategoriBarang.isNotBlank()) {
                                                    val newItem = CartItem(
                                                        namaBarang = namaBarang.trim(),
                                                        kategori = kategoriBarang.trim(),
                                                        qty = qtyVal,
                                                        harga = hrgVal
                                                    )
                                                    cartItems.add(newItem)
                                                    // Reset input fields
                                                    namaBarang = ""
                                                    kategoriBarang = ""
                                                    customHarga = ""
                                                    inputQty = "1"
                                                    viewModel.showToastPopup("Item ditambahkan ke keranjang!")
                                                } else {
                                                    viewModel.showToastPopup("Nama & Kategori tidak boleh kosong!")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Tambah", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Shopping Cart List Section
                        Text("DAFTAR KERANJANG BELANJA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                        
                        if (cartItems.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Keranjang kosong. Tambahkan produk atau sewa di atas.",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    cartItems.forEachIndexed { i, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.namaBarang, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${item.qty} x ${toRupiah(item.harga)} (${item.kategori})", 
                                                    fontSize = 11.sp, 
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(toRupiah(item.totalHarga), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { cartItems.removeAt(i) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete, 
                                                        contentDescription = "Hapus", 
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (i < cartItems.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }
                            }

                            // Payment Summary
                            // Config & Tax Form
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("KONFIGURASI PAJAK & PEMBAYARAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = taxEnabled,
                                                onCheckedChange = { taxEnabled = it }
                                            )
                                            Text("Pajak PPN Aktif", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                        
                                        if (taxEnabled) {
                                            OutlinedTextField(
                                                value = taxRateInput,
                                                onValueChange = { newValue ->
                                                    if (newValue.all { it.isDigit() || it == '.' }) {
                                                        taxRateInput = newValue
                                                    }
                                                },
                                                label = { Text("Pajak (%)", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                                keyboardActions = KeyboardActions(onNext = { dialogFocusManager.moveFocus(FocusDirection.Next) }),
                                                modifier = Modifier.width(90.dp),
                                                singleLine = true
                                            )
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = uangTunaiInput,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() }) {
                                                uangTunaiInput = newValue
                                            }
                                        },
                                        label = { Text("Uang Tunai Diterima (Rp)") }, keyboardActions = KeyboardActions(onDone = { dialogFocusManager.clearFocus(); dialogKeyboardController?.hide() }),
                                        placeholder = { Text("Masukkan jumlah bayar tunai") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        trailingIcon = {
                                            if (uangTunaiInput.isNotEmpty()) {
                                                IconButton(onClick = { uangTunaiInput = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Bersihkan input")
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Subtotal:", fontSize = 12.sp)
                                        Text(toRupiah(subtotal), fontSize = 12.sp)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (taxEnabled) "PPN ($taxRatePercent%):" else "Pajak (Non-Aktif):", fontSize = 11.sp)
                                        Text(toRupiah(ppn), fontSize = 11.sp)
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Belanja:", fontSize = 14.sp, fontWeight = FontWeight.Black)
                                        Text(toRupiah(grandTotal), fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                                    }
                                    
                                    if (uangTunaiInput.isNotBlank() && uangTunai > 0.0) {
                                        val kembalian = (uangTunai - grandTotal).coerceAtLeast(0.0)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Kembalian:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(toRupiah(kembalian), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (uangTunai >= grandTotal) Color(0xFF2E7D32) else Color.Red)
                                        }
                                        if (uangTunai < grandTotal) {
                                            Text("UANG TUNAI TIDAK CUKUP!", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        enabled = cartItems.isNotEmpty(),
                        onClick = {
                            if (uangTunaiInput.isNotBlank() && uangTunai < grandTotal) {
                                viewModel.showToastPopup("Pembayaran ditangguhkan: Uang tunai kurang dari total belanja!")
                                return@Button
                            }

                            var isStockValid = true
                            cartItems.forEach { item ->
                                val stockItem = listStock.find { it.nama.trim().equals(item.namaBarang.trim(), ignoreCase = true) }
                                if (stockItem != null) {
                                    val totalRequestedQty = cartItems.filter { it.namaBarang.trim().equals(stockItem.nama.trim(), ignoreCase = true) }.sumOf { it.qty }
                                    if (totalRequestedQty > stockItem.stok) {
                                        isStockValid = false
                                        viewModel.showToastPopup("Jumlah item '${item.namaBarang}' melebihi stok yang tersedia (${stockItem.stok})!")
                                    }
                                }
                            }

                            if (isStockValid) {
                                // Decrease stock for items in inventory
                                val updatedStock = listStock.map { stock ->
                                    val totalRequestedQty = cartItems.filter { it.namaBarang.trim().equals(stock.nama.trim(), ignoreCase = true) }.sumOf { it.qty }
                                    if (totalRequestedQty > 0) {
                                        stock.copy(stok = (stock.stok - totalRequestedQty).coerceAtLeast(0))
                                    } else stock
                                }
                                saveStock(updatedStock)

                                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                val currentWaktu = sdf.format(Date())

                                val newTx = CashierTransaction(
                                    items = cartItems.toList(),
                                    waktu = currentWaktu,
                                    taxRate = taxRatePercent / 100.0,
                                    taxEnabled = taxEnabled,
                                    tunai = if (uangTunai > 0.0) uangTunai else grandTotal
                                )
                                val updatedTxList = listCashier.toMutableList()
                                updatedTxList.add(newTx)
                                saveCashier(updatedTxList)

                                showCashierDialog = false
                                cartItems.clear()
                                uangTunaiInput = ""

                                triggerPrintReceipt(newTx)
                            }
                        }
                    ) {
                        Text("Proses Pembayaran")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        cartItems.clear()
                        uangTunaiInput = ""
                        showCashierDialog = false 
                    }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 2B: Kasir Transaksi Barcode (Tanpa Input Data)
        if (showBarcodeCashierDialog) {
            var activeScannerForBarcodeCashier by remember { mutableStateOf(false) }
            var pendingBarcodeItem by remember { mutableStateOf<StockItem?>(null) }
            var scannedBarcodeQtyInput by remember { mutableStateOf("1") }
            var scannedInputCode by remember { mutableStateOf("") }
            val barcodeFocusRequester = remember { FocusRequester() }

            val cartItems = remember { mutableStateListOf<CartItem>() }

            var taxEnabled by remember { mutableStateOf(true) }
            var taxRateInput by remember { mutableStateOf("11") }
            var uangTunaiInput by remember { mutableStateOf("") }

            val subtotal = cartItems.sumOf { it.totalHarga }
            val taxRatePercent = if (taxEnabled) (taxRateInput.toDoubleOrNull() ?: 0.0) else 0.0
            val ppn = subtotal * (taxRatePercent / 100.0)
            val grandTotal = subtotal + ppn
            val uangTunai = uangTunaiInput.toDoubleOrNull() ?: 0.0

            LaunchedEffect(Unit) {
                try {
                    barcodeFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }

            val playBarcodeBeepSuccess = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 250)
                } catch (_: Exception) {}
            }

            val playBarcodeBeepError = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 250)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 350)
                } catch (_: Exception) {}
            }

            val processBarcode = { code: String ->
                val trimmedCode = code.trim()
                if (trimmedCode.isNotEmpty()) {
                    val foundItem = listStock.find { it.barcode.trim() == trimmedCode }
                    if (foundItem != null) {
                        if (foundItem.stok <= 0) {
                            playBarcodeBeepError()
                            viewModel.showToastPopup("Gagal: Sisa stok untuk '${foundItem.nama}' habis (0)!")
                        } else {
                            playBarcodeBeepSuccess()
                            val existingInCart = cartItems.find { it.namaBarang == foundItem.nama }
                            val initialQty = existingInCart?.qty ?: 1
                            scannedBarcodeQtyInput = initialQty.toString()
                            pendingBarcodeItem = foundItem
                        }
                    } else {
                        playBarcodeBeepError()
                        viewModel.showToastPopup("Barcode '$trimmedCode' tidak terdaftar di stok!")
                    }
                    scannedInputCode = ""
                }
            }

            if (activeScannerForBarcodeCashier) {
                BarcodeScannerDialog(
                    onDismissRequest = { activeScannerForBarcodeCashier = false },
                    onBarcodeScanned = { barcode ->
                        processBarcode(barcode)
                        activeScannerForBarcodeCashier = false
                    }
                )
            }

            if (pendingBarcodeItem != null) {
                val item = pendingBarcodeItem!!
                val qtyVal = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                val calculatedTotal = item.harga * qtyVal

                AlertDialog(
                    onDismissRequest = { pendingBarcodeItem = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32)
                            )
                            Text("Input Jumlah (Qty) Barang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ) {
                                        ProductPhotoThumbnail(
                                            photoData = item.foto,
                                            contentDescription = item.nama,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            iconSize = 24.dp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Kategori: ${item.kategori.ifEmpty { "-" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Harga Satuan: ${toRupiah(item.harga)}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                        Text("Stok Tersedia: ${item.stok} unit", fontSize = 12.sp, color = if (item.stok < 5) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            Text("Tentukan Jumlah Pembelian (Qty):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = {
                                        val current = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                                        if (current > 1) {
                                            scannedBarcodeQtyInput = (current - 1).toString()
                                        }
                                    },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Kurang")
                                }

                                OutlinedTextField(
                                    value = scannedBarcodeQtyInput,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { it.isDigit() }) {
                                            scannedBarcodeQtyInput = input
                                        }
                                    },
                                    modifier = Modifier
                                        .width(90.dp)
                                        .padding(horizontal = 8.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                IconButton(
                                    onClick = {
                                        val current = scannedBarcodeQtyInput.toIntOrNull() ?: 1
                                        if (current < item.stok) {
                                            scannedBarcodeQtyInput = (current + 1).toString()
                                        } else {
                                            viewModel.showToastPopup("Batas stok maksimum tercapai (${item.stok})")
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color(0xFF2E7D32))
                                }
                            }

                            HorizontalDivider()

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Total Subtotal:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(toRupiah(calculatedTotal), fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val finalQty = scannedBarcodeQtyInput.toIntOrNull() ?: 0
                                if (finalQty <= 0) {
                                    viewModel.showToastPopup("Jumlah Qty harus minimal 1!")
                                } else if (finalQty > item.stok) {
                                    viewModel.showToastPopup("Gagal: Qty ($finalQty) melebihi stok yang tersedia (${item.stok})!")
                                } else {
                                    val existingIndex = cartItems.indexOfFirst { it.namaBarang == item.nama }
                                    if (existingIndex != -1) {
                                        cartItems[existingIndex] = cartItems[existingIndex].copy(qty = finalQty)
                                    } else {
                                        cartItems.add(
                                            CartItem(
                                                namaBarang = item.nama,
                                                kategori = item.kategori,
                                                qty = finalQty,
                                                harga = item.harga
                                            )
                                        )
                                    }
                                    viewModel.showToastPopup("Berhasil memasukkan '${item.nama}' ($finalQty x) ke keranjang!")
                                    pendingBarcodeItem = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Tambahkan ke Keranjang")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { pendingBarcodeItem = null }) {
                            Text("Batal")
                        }
                    }
                )
            }

            AlertDialog(
                onDismissRequest = { 
                    cartItems.clear()
                    showBarcodeCashierDialog = false 
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kasir - Scan Barcode", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Action scanner row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { activeScannerForBarcodeCashier = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Kamera Scan")
                            }
                        }

                        // OTG / Hardware barcode text field
                        OutlinedTextField(
                            value = scannedInputCode,
                            onValueChange = { input ->
                                scannedInputCode = input
                                if (input.endsWith("\n") || input.endsWith("\r")) {
                                    processBarcode(input.trim())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(barcodeFocusRequester),
                            label = { Text("Atau Ketik / Scan Barcode (Hardware/OTG)") },
                            placeholder = { Text("Sorot scanner ke kode batang...") },
                            singleLine = true,
                            trailingIcon = {
                                if (scannedInputCode.isNotEmpty()) {
                                    IconButton(onClick = { processBarcode(scannedInputCode) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Cari Barcode", tint = Color(0xFF2E7D32))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                processBarcode(scannedInputCode)
                            })
                        )

                        HorizontalDivider()

                        Text("Daftar Item Hasil Scan Barcode:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        if (cartItems.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Belum ada barang dipindai.", fontSize = 12.sp, color = Color.Gray)
                                    Text("Klik 'Buka Kamera Scan' di atas untuk memindai.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            cartItems.forEachIndexed { idx, item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.namaBarang, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${toRupiah(item.harga)} x ${item.qty} = ${toRupiah(item.totalHarga)}", fontSize = 12.sp, color = Color(0xFF2E7D32))
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    if (item.qty > 1) {
                                                        cartItems[idx] = item.copy(qty = item.qty - 1)
                                                    } else {
                                                        cartItems.removeAt(idx)
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Kurang Qty", modifier = Modifier.size(16.dp))
                                            }

                                            Text("${item.qty}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))

                                            IconButton(
                                                onClick = {
                                                    val stockItem = listStock.find { it.nama == item.namaBarang }
                                                    val maxStok = stockItem?.stok ?: 999
                                                    if (item.qty < maxStok) {
                                                        cartItems[idx] = item.copy(qty = item.qty + 1)
                                                    } else {
                                                        viewModel.showToastPopup("Mencapai batas stok (${maxStok})")
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Tambah Qty", modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                                            }

                                            IconButton(
                                                onClick = { cartItems.removeAt(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Ringkasan Pembayaran
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Subtotal:", fontSize = 13.sp)
                                Text(toRupiah(subtotal), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = taxEnabled,
                                        onCheckedChange = { taxEnabled = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                    Text("PPN (%), default 11%:", fontSize = 12.sp)
                                }
                                if (taxEnabled) {
                                    OutlinedTextField(
                                        value = taxRateInput,
                                        onValueChange = { taxRateInput = it },
                                        modifier = Modifier.width(70.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                } else {
                                    Text("0%", fontSize = 12.sp, color = Color.Gray)
                                }
                            }

                            if (taxEnabled) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pajak PPN ($taxRateInput%):", fontSize = 12.sp, color = Color.Gray)
                                    Text(toRupiah(ppn), fontSize = 12.sp, color = Color.Gray)
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Total Tagihan:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(toRupiah(grandTotal), fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF2E7D32))
                            }

                            OutlinedTextField(
                                value = uangTunaiInput,
                                onValueChange = { uangTunaiInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Uang Tunai Diterima (Rp)") },
                                placeholder = { Text("Contoh: 50000") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            if (uangTunai > 0.0) {
                                val kembalian = uangTunai - grandTotal
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (kembalian >= 0) "Kembalian:" else "Kurang Bayar:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        toRupiah(abs(kembalian)),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (kembalian >= 0) Color(0xFF2E7D32) else Color.Red
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (cartItems.isEmpty()) {
                                viewModel.showToastPopup("Keranjang masih kosong! Pindai barcode barang terlebih dahulu.")
                                return@Button
                            }
                            if (uangTunai > 0.0 && uangTunai < grandTotal) {
                                viewModel.showToastPopup("Gagal: Uang tunai kurang dari total tagihan!")
                                return@Button
                            }

                            // Verify stock
                            var isStockValid = true
                            for (item in cartItems) {
                                val stockItem = listStock.find { it.nama.trim().equals(item.namaBarang.trim(), ignoreCase = true) }
                                if (stockItem != null) {
                                    if (item.qty > stockItem.stok) {
                                        isStockValid = false
                                        viewModel.showToastPopup("Jumlah item '${item.namaBarang}' melebihi stok yang tersedia (${stockItem.stok})!")
                                    }
                                }
                            }

                            if (isStockValid) {
                                val updatedStock = listStock.map { stock ->
                                    val totalRequestedQty = cartItems.filter { it.namaBarang.trim().equals(stock.nama.trim(), ignoreCase = true) }.sumOf { it.qty }
                                    if (totalRequestedQty > 0) {
                                        stock.copy(stok = (stock.stok - totalRequestedQty).coerceAtLeast(0))
                                    } else stock
                                }
                                saveStock(updatedStock)

                                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                val currentWaktu = sdf.format(Date())

                                val newTx = CashierTransaction(
                                    items = cartItems.toList(),
                                    waktu = currentWaktu,
                                    taxRate = taxRatePercent / 100.0,
                                    taxEnabled = taxEnabled,
                                    tunai = if (uangTunai > 0.0) uangTunai else grandTotal
                                )
                                val updatedTxList = listCashier.toMutableList()
                                updatedTxList.add(newTx)
                                saveCashier(updatedTxList)

                                showBarcodeCashierDialog = false
                                cartItems.clear()
                                uangTunaiInput = ""

                                triggerPrintReceipt(newTx)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Proses Pembayaran")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            cartItems.clear()
                            uangTunaiInput = ""
                            showBarcodeCashierDialog = false
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 2C: Stok Ulang Barang via Scan Barcode (Tanpa Form Input Data Barang Baru)
        if (showRestockBarcodeDialog) {
            var activeScannerForRestock by remember { mutableStateOf(false) }
            var pendingRestockItem by remember { mutableStateOf<StockItem?>(null) }
            var restockQtyInput by remember { mutableStateOf("1") }
            var scannedRestockCode by remember { mutableStateOf("") }
            val restockBarcodeFocusRequester = remember { FocusRequester() }
            val restockHistoryList = remember { mutableStateListOf<Pair<StockItem, Int>>() }

            val playBarcodeBeepSuccess = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 250)
                } catch (_: Exception) {}
            }

            val playBarcodeBeepError = {
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 250)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { tg.release() } catch (_: Exception) {}
                    }, 350)
                } catch (_: Exception) {}
            }

            val processRestockBarcode = { code: String ->
                val trimmedCode = code.trim()
                if (trimmedCode.isNotEmpty()) {
                    val foundItem = listStock.find { it.barcode.trim() == trimmedCode }
                    if (foundItem != null) {
                        playBarcodeBeepSuccess()
                        pendingRestockItem = foundItem
                        restockQtyInput = "1"
                    } else {
                        playBarcodeBeepError()
                        viewModel.showToastPopup("Barcode '$trimmedCode' tidak terdaftar di stok barang!")
                    }
                    scannedRestockCode = ""
                }
            }

            if (activeScannerForRestock) {
                BarcodeScannerDialog(
                    onDismissRequest = { activeScannerForRestock = false },
                    onBarcodeScanned = { barcode ->
                        processRestockBarcode(barcode)
                        activeScannerForRestock = false
                    }
                )
            }

            // Sub-dialog: Dialog Opsi Jumlah (Qty) Restock
            if (pendingRestockItem != null) {
                val item = pendingRestockItem!!
                var restockFoto by remember(item) { mutableStateOf(item.foto) }
                val qtyVal = restockQtyInput.toIntOrNull() ?: 1
                val newStockCalculated = item.stok + qtyVal

                val restockCameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        val b64 = compressBitmapToBase64(bitmap)
                        if (b64.isNotEmpty()) {
                            restockFoto = b64
                        }
                    }
                }

                val restockGalleryLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        val b64 = uriToCompressedBase64(context, uri)
                        if (b64.isNotEmpty()) {
                            restockFoto = b64
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { pendingRestockItem = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = Color(0xFF1B5E20)
                            )
                            Text("Opsi Jumlah (Qty) Stok Ulang", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        ProductPhotoThumbnail(
                                            photoData = restockFoto,
                                            contentDescription = item.nama,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            iconSize = 28.dp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Kategori: ${item.kategori.ifEmpty { "-" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Harga Satuan: ${toRupiah(item.harga)}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                        Text("Stok Saat Ini: ${item.stok} unit", fontSize = 13.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Opsi Ambil/Update Foto saat Stok Ulang
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { restockCameraLauncher.launch(null) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (restockFoto.isEmpty()) "Ambil Foto" else "Ganti Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { restockGalleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dari Galeri", fontSize = 11.sp)
                                }
                            }

                            Text("Masukkan Jumlah Penambahan Stok (+Qty):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = {
                                        val current = restockQtyInput.toIntOrNull() ?: 1
                                        if (current > 1) {
                                            restockQtyInput = (current - 1).toString()
                                        }
                                    },
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Kurang")
                                }

                                OutlinedTextField(
                                    value = restockQtyInput,
                                    onValueChange = { input ->
                                        if (input.isEmpty() || input.all { it.isDigit() }) {
                                            restockQtyInput = input
                                        }
                                    },
                                    modifier = Modifier
                                        .width(90.dp)
                                        .padding(horizontal = 8.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                IconButton(
                                    onClick = {
                                        val current = restockQtyInput.toIntOrNull() ?: 1
                                        restockQtyInput = (current + 1).toString()
                                    },
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color(0xFF2E7D32))
                                }
                            }

                            HorizontalDivider()

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stok Baru Setelah Ditambah:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("$newStockCalculated unit", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val addedQty = restockQtyInput.toIntOrNull() ?: 0
                                if (addedQty <= 0) {
                                    viewModel.showToastPopup("Jumlah penambahan stok harus minimal 1!")
                                } else {
                                    val updatedStock = listStock.map { stockItem ->
                                        if (stockItem.nama.trim().equals(item.nama.trim(), ignoreCase = true)) {
                                            stockItem.copy(
                                                stok = stockItem.stok + addedQty,
                                                foto = if (restockFoto.isNotEmpty()) restockFoto else stockItem.foto
                                            )
                                        } else stockItem
                                    }
                                    saveStock(updatedStock)
                                    restockHistoryList.add(Pair(item, addedQty))
                                    viewModel.showToastPopup("Berhasil menambah stok '${item.nama}' +$addedQty unit! Stok baru: ${item.stok + addedQty}")
                                    pendingRestockItem = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Tambah Stok")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { pendingRestockItem = null }) {
                            Text("Batal")
                        }
                    }
                )
            }

            // Main Dedicated Restock Dialog
            AlertDialog(
                onDismissRequest = { showRestockBarcodeDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stok Ulang - Scan Barcode", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { activeScannerForRestock = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Kamera Scan")
                            }
                        }

                        OutlinedTextField(
                            value = scannedRestockCode,
                            onValueChange = { input ->
                                scannedRestockCode = input
                                if (input.endsWith("\n") || input.endsWith("\r")) {
                                    processRestockBarcode(input.trim())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(restockBarcodeFocusRequester),
                            label = { Text("Ketik / Scan Barcode (Hardware/OTG)") },
                            placeholder = { Text("Arahkan scanner ke kode batang...") },
                            singleLine = true,
                            trailingIcon = {
                                if (scannedRestockCode.isNotEmpty()) {
                                    IconButton(onClick = { processRestockBarcode(scannedRestockCode) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Cari Barcode", tint = Color(0xFF2E7D32))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                processRestockBarcode(scannedRestockCode)
                            })
                        )

                        HorizontalDivider()

                        Text("Riwayat Stok Ulang Sesi Ini:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        if (restockHistoryList.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Belum ada barang diisi ulang.", fontSize = 12.sp, color = Color.Gray)
                                    Text("Pindai barcode untuk menambah stok barang.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            restockHistoryList.forEach { (item, addedQty) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Barcode: ${item.barcode.ifEmpty { "-" }}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text("+$addedQty Unit", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showRestockBarcodeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Selesai")
                    }
                },
                dismissButton = null
            )
        }

        // Dialog 3: Konfirmasi Hapus Data
        if (pendingDeleteType != null && pendingDeleteIndex != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingDeleteType = null
                    pendingDeleteIndex = null
                },
                title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
                text = { Text("Apakah Anda yakin ingin menghapus data ini?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            val idx = pendingDeleteIndex!!
                            when (pendingDeleteType) {
                                "stok" -> {
                                    val mutable = listStock.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable.removeAt(idx)
                                        saveStock(mutable)
                                    }
                                }
                                "kasir" -> {
                                    val mutable = listCashier.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable.removeAt(idx)
                                        saveCashier(mutable)
                                    }
                                }
                            }
                            viewModel.showToastPopup("Data berhasil dihapus!")
                            pendingDeleteType = null
                            pendingDeleteIndex = null
                        }
                    ) {
                        Text("Hapus", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingDeleteType = null
                            pendingDeleteIndex = null
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 4: Unduh Daftar Belanja
        if (showUnduhBelanjaDialog) {
            val stockOutList = listStock.filter { it.stok <= 0 }
            
            // Initialize quantities if not set
            LaunchedEffect(stockOutList) {
                stockOutList.forEach { item ->
                    if (!quantitiesBelanja.containsKey(item.id)) {
                        quantitiesBelanja[item.id] = "10"
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showUnduhBelanjaDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Download, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unduh Daftar Belanja", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Masukkan jumlah belanja yang ingin dibeli untuk barang-barang habis berikut:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        stockOutList.forEach { item ->
                            val currentQty = quantitiesBelanja[item.id] ?: "10"
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        item.nama,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Kategori: ${item.kategori} | Harga: ${formatToRupiah(item.harga)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = currentQty,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() }) {
                                                quantitiesBelanja[item.id] = newValue
                                            }
                                        },
                                        placeholder = { Text("Contoh: 10") },
                                        label = { Text("Jumlah belanja (unit)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val textContent = formatShoppingListToText(namaUsaha, stockOutList, quantitiesBelanja)
                                val fileName = "Daftar_Belanja_${namaUsaha.replace(" ", "_").lowercase()}_${System.currentTimeMillis()}.txt"
                                val success = saveTextToDownloads(context, fileName, textContent)
                                if (success) {
                                    viewModel.showToastPopup("Daftar belanja $fileName berhasil diunduh ke folder Downloads!")
                                    showUnduhBelanjaDialog = false
                                } else {
                                    viewModel.showToastPopup("Gagal mengunduh file belanja.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unduh File .txt")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                val textContent = formatShoppingListToText(namaUsaha, stockOutList, quantitiesBelanja)
                                shareText(context, "Daftar Belanja $namaUsaha", textContent)
                                showUnduhBelanjaDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Teks / WA")
                        }

                        TextButton(
                            onClick = { showUnduhBelanjaDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Batal", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                dismissButton = null
            )
        }

        // Dialog 5: Option Dialog Cetak Laporan Pendapatan
        if (showPrintReportDialog) {
            PrintRevenueReportOptionDialog(
                listCashier = listCashier,
                toRupiah = toRupiah,
                onDismiss = { showPrintReportDialog = false },
                onSelectPeriod = { periodType, periodTitle ->
                    showPrintReportDialog = false
                    val filtered = filterCashierTransactionsByPeriod(listCashier, periodType)
                    if (filtered.isEmpty()) {
                        viewModel.showToastPopup("Tidak ada data transaksi untuk periode $periodTitle!")
                    } else {
                        printCashierReport(context, namaUsaha, filtered, periodTitle)
                    }
                }
            )
        }

        // Dialog 6: Dialog Pengaturan Printer Thermal POS
        if (showPrinterSettingsDialog) {
            PrinterSettingsDialog(
                currentMethod = printerMethod,
                currentPaperWidth = printerPaperWidth,
                namaUsaha = namaUsaha,
                sampleTx = listCashier.lastOrNull() ?: CashierTransaction(
                    items = listOf(
                        CartItem(namaBarang = "Barang Contoh A", kategori = "Makanan", qty = 1, harga = 15000.0),
                        CartItem(namaBarang = "Barang Contoh B", kategori = "Minuman", qty = 2, harga = 5000.0)
                    ),
                    waktu = "07 Aug, 10:00",
                    taxRate = 0.0,
                    taxEnabled = false,
                    tunai = 30000.0
                ),
                onSave = { method, width ->
                    savePrinterSettings(method, width)
                    showPrinterSettingsDialog = false
                    viewModel.showToastPopup("Pengaturan printer berhasil disimpan!")
                },
                onDismiss = { showPrinterSettingsDialog = false },
                showToast = { viewModel.showToastPopup(it) }
            )
        }
    }

// PRINT STOCK UTILITY
private fun printStockList(
    context: Context,
    namaUsaha: String,
    items: List<StockItem>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Laporan Stok Barang - $namaUsaha"

    val totalItems = items.size
    val totalUnits = items.sumOf { it.stok }
    val outOfStockCount = items.count { it.stok <= 0 }
    val totalAssetValue = items.sumOf { it.harga * it.stok }

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
                .badge-tersedia { color: green; font-weight: bold; background-color: #e6fced; padding: 2px 6px; border-radius: 4px; display: inline-block; }
                .badge-habis { color: red; font-weight: bold; background-color: #ffebe9; padding: 2px 6px; border-radius: 4px; display: inline-block; }
            </style>
        </head>
        <body>
            <h1>Laporan Stok Barang & Produk</h1>
            <h2 style="text-align: center; font-size: 15px; margin: 0; color: #555;">$namaUsaha</h2>
            <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            
            <div class="summary-box">
                <h3>Ringkasan Inventori / Stok</h3>
                <div class="grid">
                    <div class="grid-col">
                        <p>Total Jenis Barang: <strong>$totalItems Jenis</strong></p>
                        <p>Total Unit Barang: <strong>$totalUnits Unit</strong></p>
                    </div>
                    <div class="grid-col">
                        <p>Barang Habis (Stok 0): <span style="font-weight: bold; color: ${if (outOfStockCount > 0) "red" else "inherit"};">$outOfStockCount Jenis</span></p>
                        <p>Total Estimasi Nilai Aset: <strong style="color: #007bff;">Rp ${String.format("%,.0f", totalAssetValue)}</strong></p>
                    </div>
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">#</th>
                        <th style="width: 25%;">Nama Barang / Produk</th>
                        <th style="width: 15%;">Kategori</th>
                        <th style="width: 20%;">Keterangan</th>
                        <th style="width: 15%;" class="text-right">Harga Unit</th>
                        <th style="width: 10%;" class="text-center">Sisa Stok</th>
                        <th style="width: 10%;" class="text-center">Status</th>
                    </tr>
                </thead>
                <tbody>
    """)

    items.forEachIndexed { index, item ->
        val badgeClass = if (item.stok > 0) "badge-tersedia" else "badge-habis"
        val statusText = if (item.stok > 0) "Tersedia" else "Habis"
        htmlBuilder.append("""
            <tr>
                <td>${index + 1}</td>
                <td style="font-weight: bold;">${item.nama}</td>
                <td>${item.kategori}</td>
                <td>${item.keterangan.ifEmpty { "—" }}</td>
                <td class="text-right">Rp ${String.format("%,.0f", item.harga)}</td>
                <td class="text-center">${item.stok}</td>
                <td class="text-center"><span class="$badgeClass">$statusText</span></td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row" style="background-color: #eef5ff;">
                        <td colspan="5" class="text-right" style="color: #0056b3;">Estimasi Nilai Aset Masuk:</td>
                        <td colspan="2" class="text-right" style="color: #0056b3; font-size: 11px; padding-right: 15px;">Rp ${String.format("%,.0f", totalAssetValue)}</td>
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

// PRINT CASHIER REPORT UTILITY
private fun printCashierReport(
    context: Context,
    namaUsaha: String,
    transactions: List<CashierTransaction>,
    periodTitle: String = "Hari Ini"
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Laporan Pendapatan ($periodTitle) - $namaUsaha"

    val totalTxCount = transactions.size
    val totalQty = transactions.sumOf { tx -> tx.items.sumOf { it.qty } }
    
    val totalSubtotal = transactions.sumOf { it.totalHarga }
    val totalTax = transactions.sumOf { if (it.taxEnabled) (it.totalHarga * it.taxRate) else 0.0 }
    val totalRevenue = totalSubtotal + totalTax

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; color: #333; }
                h1 { text-align: center; color: #222; margin-bottom: 3px; font-size: 20px; }
                h2 { text-align: center; font-size: 14px; margin: 0; color: #555; }
                .meta-time { text-align: center; color: #666; margin-top: 5px; margin-bottom: 25px; font-size: 11px; }
                .summary-box {
                    border: 1px solid #c8e6c9;
                    border-radius: 8px;
                    padding: 15px;
                    background-color: #f1f8e9;
                    margin-bottom: 20px;
                    line-height: 1.6;
                }
                .summary-box h3 { margin: 0 0 8px 0; color: #2e7d32; font-size: 14px; border-bottom: 1px solid #a5d6a7; padding-bottom: 6px; }
                .summary-box p { text-align: left; margin: 4px 0; color: #333; font-size: 12px; }
                .grid { display: flex; justify-content: space-between; }
                .grid-col { flex: 1; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #e8f5e9; color: #1b5e20; font-weight: bold; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #f1f8e9; }
                .badge-paid { color: #1b5e20; font-weight: bold; background-color: #e8f5e9; padding: 2px 6px; border-radius: 4px; display: inline-block; }
                .item-row { margin-bottom: 3px; border-bottom: 1px dashed #eee; padding-bottom: 2px; }
                .item-row:last-child { border-bottom: none; }
            </style>
        </head>
        <body>
            <h1>Laporan Kasir & Pendapatan</h1>
            <h2>$namaUsaha</h2>
            <p class="meta-time">Periode Laporan: <strong>$periodTitle</strong> | Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            
            <div class="summary-box">
                <h3>Ringkasan Penjualan ($periodTitle)</h3>
                <div class="grid">
                    <div class="grid-col">
                        <p>Total Transaksi: <strong>$totalTxCount Transaksi</strong></p>
                        <p>Item Terjual: <strong>$totalQty Unit</strong></p>
                    </div>
                    <div class="grid-col">
                        <p>Total Subtotal: <strong>Rp ${String.format("%,.0f", totalSubtotal)}</strong></p>
                        <p>Total Pajak (PPN): <strong>Rp ${String.format("%,.0f", totalTax)}</strong></p>
                        <p>Total Penjualan Bersih: <strong style="color: #2e7d32; font-size: 13px;">Rp ${String.format("%,.0f", totalRevenue)}</strong></p>
                    </div>
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">#</th>
                        <th style="width: 15%;">Waktu</th>
                        <th style="width: 35%;">Detail Pembelian (Nama & Qty)</th>
                        <th style="width: 11%;" class="text-right">Subtotal</th>
                        <th style="width: 11%;" class="text-right">Pajak (PPN)</th>
                        <th style="width: 12%;" class="text-right">Total Transaksi</th>
                        <th style="width: 11%;" class="text-right">Metode / Tunai</th>
                    </tr>
                </thead>
                <tbody>
    """)

    transactions.forEachIndexed { index, tx ->
        val txTax = if (tx.taxEnabled) (tx.totalHarga * tx.taxRate) else 0.0
        val txGrandTotal = tx.totalHarga + txTax
        val payCash = if (tx.tunai > 0.0) tx.tunai else txGrandTotal
        val changeAmt = payCash - txGrandTotal

        val itemsHtml = StringBuilder()
        tx.items.forEach { cartItem ->
            itemsHtml.append("""
                <div class="item-row">
                    <strong>${cartItem.namaBarang}</strong> (${cartItem.qty}x @ Rp ${String.format("%,.0f", cartItem.harga)})
                </div>
            """)
        }

        htmlBuilder.append("""
            <tr>
                <td>${index + 1}</td>
                <td>${tx.waktu}</td>
                <td>$itemsHtml</td>
                <td class="text-right">Rp ${String.format("%,.0f", tx.totalHarga)}</td>
                <td class="text-right">Rp ${String.format("%,.0f", txTax)}</td>
                <td class="text-right" style="font-weight: bold; color: #2e7d32;">Rp ${String.format("%,.0f", txGrandTotal)}</td>
                <td class="text-right">
                    Tunai: Rp ${String.format("%,.0f", payCash)}
                    ${if (changeAmt > 0) "<br><small style='color:#666;'>Kembalian: Rp " + String.format("%,.0f", changeAmt) + "</small>" else ""}
                </td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3" class="text-right" style="color: #1b5e20;">Total Pendapatan Kotor ($periodTitle):</td>
                        <td class="text-right" style="color: #1b5e20;">Rp ${String.format("%,.0f", totalSubtotal)}</td>
                        <td class="text-right" style="color: #1b5e20;">Rp ${String.format("%,.0f", totalTax)}</td>
                        <td colspan="2" class="text-right" style="color: #1b5e20; font-size: 13px;">Rp ${String.format("%,.0f", totalRevenue)}</td>
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

fun filterCashierTransactionsByPeriod(
    list: List<CashierTransaction>,
    periodType: String
): List<CashierTransaction> {
    val sdfDay = SimpleDateFormat("dd MMM", Locale.getDefault())
    return when (periodType) {
        "harian" -> {
            val todayStr = sdfDay.format(Date())
            list.filter { it.waktu.contains(todayStr) }
        }
        "mingguan" -> {
            val days = (0..6).map { i ->
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                sdfDay.format(cal.time)
            }
            list.filter { tx -> days.any { day -> tx.waktu.contains(day) } }
        }
        "bulanan" -> {
            val days = (0..29).map { i ->
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                sdfDay.format(cal.time)
            }
            list.filter { tx -> days.any { day -> tx.waktu.contains(day) } }
        }
        "tahunan" -> {
            val days = (0..364).map { i ->
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                sdfDay.format(cal.time)
            }
            list.filter { tx -> days.any { day -> tx.waktu.contains(day) } }
        }
        else -> list
    }
}

@Composable
private fun PrintRevenueReportOptionDialog(
    listCashier: List<CashierTransaction>,
    toRupiah: (Double) -> String,
    onDismiss: () -> Unit,
    onSelectPeriod: (periodType: String, periodTitle: String) -> Unit
) {
    val options = remember {
        listOf(
            Triple("harian", "Hari Ini (Harian)", Icons.Default.CalendarToday),
            Triple("mingguan", "7 Hari Terakhir (Mingguan)", Icons.Default.DateRange),
            Triple("bulanan", "30 Hari Terakhir (Bulanan)", Icons.Default.CalendarMonth),
            Triple("tahunan", "1 Tahun Terakhir (Tahunan)", Icons.Default.EventRepeat),
            Triple("semua", "Semua Data Transaksi", Icons.Default.ReceiptLong)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Print,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cetak Laporan Pendapatan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Pilih periode laporan pendapatan kasir yang ingin Anda cetak:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                options.forEach { (type, title, icon) ->
                    val filtered = filterCashierTransactionsByPeriod(listCashier, type)
                    val txCount = filtered.size
                    val totalRev = filtered.sumOf { tx ->
                        val tax = if (tx.taxEnabled) (tx.totalHarga * tx.taxRate) else 0.0
                        tx.totalHarga + tax
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPeriod(type, title)
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (txCount > 0) Color(0xFF2E7D32).copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, if (txCount > 0) Color(0xFF2E7D32).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (txCount > 0) Color(0xFF2E7D32) else Color.Gray,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (txCount > 0) "$txCount Transaksi • ${toRupiah(totalRev)}" else "Tidak ada transaksi",
                                    fontSize = 11.sp,
                                    fontWeight = if (txCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (txCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// PRINT SHOPPING STOCK OUT LIST
private fun printShoppingStockOutList(
    context: Context,
    namaUsaha: String,
    items: List<StockItem>
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Daftar Belanja Stok Habis - $namaUsaha"

    val totalItems = items.size
    val totalEstimatedPrice = items.sumOf { it.harga }

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
                    background-color: #fef8f8;
                    border-left: 4px solid #d32f2f;
                    margin-bottom: 20px;
                    line-height: 1.6;
                }
                .summary-box h3 { margin: 0 0 8px 0; color: #d32f2f; font-size: 14px; border-bottom: 1px solid #ffcdd2; padding-bottom: 6px; }
                .summary-box p { text-align: left; margin: 4px 0; color: #444; font-size: 12px; }
                .grid { display: flex; justify-content: space-between; }
                .grid-col { flex: 1; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 11px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f7f7f7; font-weight: bold; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fef8f8; }
                .badge-habis { color: #d32f2f; font-weight: bold; background-color: #ffebe9; padding: 2px 6px; border-radius: 4px; display: inline-block; }
            </style>
        </head>
        <body>
            <h1>Daftar Belanja (Stok Habis)</h1>
            <h2 style="text-align: center; font-size: 15px; margin: 0; color: #555;">$namaUsaha</h2>
            <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            
            <div class="summary-box">
                <h3>Rencana Belanja Ulang Stok Habis</h3>
                <div class="grid">
                    <div class="grid-col">
                        <p>Total Jenis Barang: <strong>$totalItems Jenis</strong></p>
                        <p>Status: <span class="badge-habis">HABIS (0 Unit)</span></p>
                    </div>
                    <div class="grid-col" style="text-align: right;">
                        <p>Estimasi Anggaran Kebutuhan: <strong style="color: #d32f2f; font-size: 13px;">Rp ${String.format("%,.0f", totalEstimatedPrice)}</strong></p>
                    </div>
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">#</th>
                        <th style="width: 35%;">Nama Barang / Produk</th>
                        <th style="width: 20%;">Kategori</th>
                        <th style="width: 25%;">Keterangan</th>
                        <th style="width: 15%;" class="text-right">Harga Unit</th>
                    </tr>
                </thead>
                <tbody>
    """)

    items.forEachIndexed { index, item ->
        htmlBuilder.append("""
            <tr>
                <td>${index + 1}</td>
                <td style="font-weight: bold; color: #d32f2f;">${item.nama}</td>
                <td>${item.kategori}</td>
                <td>${item.keterangan.ifEmpty { "—" }}</td>
                <td class="text-right">Rp ${String.format("%,.0f", item.harga)}</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="4" class="text-right" style="color: #c62828;">Total Estimasi Kebutuhan Dana Belanja:</td>
                        <td class="text-right" style="color: #c62828; font-size: 11px; padding-right: 15px;">Rp ${String.format("%,.0f", totalEstimatedPrice)}</td>
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
fun CashierDashboardHeader(
    namaUsaha: String,
    totalSales: Double,
    lowStockCount: Int,
    outOfStockCount: Int,
    toRupiah: (Double) -> String,
    onManageUsahaClick: () -> Unit,
    onCashierInputClick: () -> Unit,
    onRestockBarcodeClick: () -> Unit = {},
    onBarcodeScanClick: () -> Unit = {},
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    onHpScannerClick: () -> Unit = {},
    onPrinterSettingsClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1B5E20), // Dark Green
                            Color(0xFF2E7D32), // Emerald Green
                            Color(0xFF388E3C)  // Leaf Green
                        )
                    )
                )
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            namaUsaha,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Ubah Nama Usaha",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onManageUsahaClick() }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Dropdown menu (titik 3) to the left of "Kasir" button
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { menuExpanded = true }
                        ) {
                            Box(
                                modifier = Modifier.padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu Navigasi",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Dasbor") },
                                onClick = {
                                    onTabChange(0)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Stok") },
                                onClick = {
                                    onTabChange(1)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Kasir") },
                                onClick = {
                                    onTabChange(2)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Belanja") },
                                onClick = {
                                    onTabChange(3)
                                    menuExpanded = false
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF16A34A)) },
                                text = { Text("Scanner HP", fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    onHpScannerClick()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2E7D32)) },
                                text = { Text("Pengaturan Printer", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    onPrinterSettingsClick()
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onBarcodeScanClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Pindai Barcode Transaksi Kasir",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Scan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // KPI stats horizontal row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stat 1: Total Sales
                    Card(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.MonetizationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFA5D6A7),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Pendapatan",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE8F5E9),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                toRupiah(totalSales),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Stat 2: Low Stock
                    Card(
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (lowStockCount > 0) Color(0xFFFFB74D) else Color(0xFFA5D6A7),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Stok Tipis",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE8F5E9),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "$lowStockCount Item",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (lowStockCount > 0) Color(0xFFFFCC80) else Color.White
                            )
                        }
                    }

                    // Stat 3: Out of Stock
                    Card(
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (outOfStockCount > 0) Color(0xFFEF5350) else Color(0xFFA5D6A7),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Habis",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE8F5E9),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "$outOfStockCount Item",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (outOfStockCount > 0) Color(0xFFFFCDD2) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StockItemRow(
    item: StockItem,
    onDelete: () -> Unit,
    toRupiah: (Double) -> String,
    onPhotoClick: (() -> Unit)? = null
) {
    val stockBg = when {
        item.stok <= 0 -> Color(0xFFFFECEC)
        item.stok <= 5 -> Color(0xFFFFF3E0)
        else -> Color(0xFFE8F5E9)
    }
    val stockColor = when {
        item.stok <= 0 -> Color(0xFFD32F2F)
        item.stok <= 5 -> Color(0xFFF57C00)
        else -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onPhotoClick != null) Modifier.clickable { onPhotoClick() } else Modifier),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                ProductPhotoThumbnail(
                    photoData = item.foto,
                    contentDescription = item.nama,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    iconSize = 24.dp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.nama,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.stok < 3) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            item.kategori,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (item.keterangan.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            item.keterangan,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    toRupiah(item.harga),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .background(stockBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (item.stok < 3) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = stockColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Text(
                            "Stok: ${item.stok}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = stockColor
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SalesTransactionRow(
    item: CashierTransaction,
    onPrintReceipt: () -> Unit,
    onDelete: () -> Unit,
    toRupiah: (Double) -> String
) {
    val displayName = if (item.items.isEmpty()) "-" else if (item.items.size == 1) item.items[0].namaBarang else "${item.items[0].namaBarang} + ${item.items.size - 1} lain"
    val totalQty = item.items.sumOf { it.qty }
    val displayDetails = if (item.items.isEmpty()) "" else "${totalQty} unit • ${item.items[0].kategori}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.15f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.waktu,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (displayDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2E7D32).copy(alpha = 0.08f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                displayDetails,
                                fontSize = 9.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    toRupiah(item.totalHarga),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF2E7D32)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onPrintReceipt,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = "Cetak Struk",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueTrendLineChartCard(
    listCashier: List<CashierTransaction>,
    toRupiah: (Double) -> String,
    onPrintReportClick: () -> Unit
) {
    var selectedDaysPeriod by remember { mutableStateOf(7) } // 7 or 30
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    val sdfDayChart = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val daysListChart = remember(selectedDaysPeriod) {
        (0 until selectedDaysPeriod).map { i ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            sdfDayChart.format(cal.time)
        }.reversed()
    }

    val dailyIncomeMapChart = remember(listCashier, daysListChart) {
        daysListChart.associateWith { dayStr ->
            listCashier.filter { tx ->
                tx.waktu.contains(dayStr)
            }.sumOf { it.totalHarga }
        }
    }

    val totalIncomeInPeriod = dailyIncomeMapChart.values.sum()
    val peakIncomeInPeriod = dailyIncomeMapChart.values.maxOrNull() ?: 0.0
    val avgDailyIncome = if (selectedDaysPeriod > 0) totalIncomeInPeriod / selectedDaysPeriod else 0.0

    val maxIncomeForScale = if (peakIncomeInPeriod > 0.0) peakIncomeInPeriod else 100_000.0

    val formatChartAmt = { amt: Double ->
        when {
            amt >= 1_000_000.0 -> {
                val formatted = amt / 1_000_000.0
                if (formatted % 1.0 == 0.0) String.format(Locale.US, "%.0fjt", formatted)
                else String.format(Locale.US, "%.1fjt", formatted)
            }
            amt >= 1_000.0 -> {
                val formatted = amt / 1_000.0
                if (formatted % 1.0 == 0.0) String.format(Locale.US, "%.0frb", formatted)
                else String.format(Locale.US, "%.1frb", formatted)
            }
            amt > 0.0 -> amt.toInt().toString()
            else -> "0"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Title and Period Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Tren Pendapatan",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }

                // Period Chips (7 Hari vs 30 Hari) & Print Button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (selectedDaysPeriod == 7) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            selectedDaysPeriod = 7
                            selectedPointIndex = null
                        }
                    ) {
                        Text(
                            "7 Hari",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedDaysPeriod == 7) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = if (selectedDaysPeriod == 30) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            selectedDaysPeriod = 30
                            selectedPointIndex = null
                        }
                    ) {
                        Text(
                            "30 Hari",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedDaysPeriod == 30) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrintReportClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = "Cetak Laporan Pendapatan Harian",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Stats Banner inside Chart
            Surface(
                color = Color(0xFF2E7D32).copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total ($selectedDaysPeriod Hari):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(toRupiah(totalIncomeInPeriod), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rata-rata/Hari:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(toRupiah(avgDailyIncome), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Puncak:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(toRupiah(peakIncomeInPeriod), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tooltip Banner when a point is selected
            if (selectedPointIndex != null && selectedPointIndex!! in daysListChart.indices) {
                val dayStr = daysListChart[selectedPointIndex!!]
                val amt = dailyIncomeMapChart[dayStr] ?: 0.0
                Surface(
                    color = Color(0xFF1B5E20),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tanggal: $dayStr", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Pendapatan: ${toRupiah(amt)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Line Chart Canvas
            val strokeColor = Color(0xFF2E7D32)
            val pointOuterColor = Color(0xFF1B5E20)
            val pointInnerColor = Color.White
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            val textColorInt = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .pointerInput(selectedDaysPeriod, daysListChart) {
                        detectTapGestures { offset ->
                            val width = size.width.toFloat()
                            val padX = 24.dp.toPx()
                            val usableW = width - 2 * padX
                            val stepX = usableW / (daysListChart.size - 1).coerceAtLeast(1)

                            var closestIndex = 0
                            var minDiff = Float.MAX_VALUE
                            daysListChart.indices.forEach { i ->
                                val px = padX + i * stepX
                                val diff = abs(offset.x - px)
                                if (diff < minDiff) {
                                    minDiff = diff
                                    closestIndex = i
                                }
                            }
                            selectedPointIndex = if (selectedPointIndex == closestIndex) null else closestIndex
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                val padX = 24.dp.toPx()
                val padTop = 20.dp.toPx()
                val padBottom = 28.dp.toPx()

                val usableW = w - 2 * padX
                val usableH = h - padTop - padBottom

                // Draw horizontal grid lines (0%, 50%, 100%)
                val gridY0 = h - padBottom
                val gridY50 = padTop + usableH * 0.5f
                val gridY100 = padTop

                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(padX, gridY0), end = androidx.compose.ui.geometry.Offset(w - padX, gridY0), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(padX, gridY50), end = androidx.compose.ui.geometry.Offset(w - padX, gridY50), strokeWidth = 1.dp.toPx())
                drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(padX, gridY100), end = androidx.compose.ui.geometry.Offset(w - padX, gridY100), strokeWidth = 1.dp.toPx())

                val stepX = usableW / (daysListChart.size - 1).coerceAtLeast(1)
                val points = daysListChart.mapIndexed { idx, day ->
                    val income = dailyIncomeMapChart[day] ?: 0.0
                    val fraction = (income / maxIncomeForScale).toFloat().coerceIn(0f, 1f)
                    val px = padX + idx * stepX
                    val py = h - padBottom - (fraction * usableH)
                    androidx.compose.ui.geometry.Offset(px, py)
                }

                if (points.isNotEmpty()) {
                    // Fill Path (Gradient)
                    val fillPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, h - padBottom)
                        lineTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                        lineTo(points.last().x, h - padBottom)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(strokeColor.copy(alpha = 0.35f), Color.Transparent),
                            startY = padTop,
                            endY = h - padBottom
                        )
                    )

                    // Line Path
                    val linePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }

                    drawPath(
                        path = linePath,
                        color = strokeColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )

                    // Draw Point Dots & Labels
                    val nativePaintText = android.graphics.Paint().apply {
                        color = textColorInt
                        textSize = if (selectedDaysPeriod == 7) 20f else 17f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val nativePaintVal = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#1B5E20")
                        textSize = 18f
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    points.forEachIndexed { idx, pt ->
                        val dayStr = daysListChart[idx]
                        val income = dailyIncomeMapChart[dayStr] ?: 0.0
                        val isSelected = selectedPointIndex == idx

                        val radiusOuter = if (isSelected) 6.dp.toPx() else (if (selectedDaysPeriod == 7) 4.dp.toPx() else 3.dp.toPx())
                        val radiusInner = if (isSelected) 3.dp.toPx() else (if (selectedDaysPeriod == 7) 2.dp.toPx() else 1.5f.dp.toPx())

                        drawCircle(color = if (isSelected) Color(0xFFC8E6C9) else strokeColor.copy(alpha = 0.2f), radius = radiusOuter + 3.dp.toPx(), center = pt)
                        drawCircle(color = pointOuterColor, radius = radiusOuter, center = pt)
                        drawCircle(color = pointInnerColor, radius = radiusInner, center = pt)

                        // X Axis Labels (Dates)
                        val shouldShowXLabel = when (selectedDaysPeriod) {
                            7 -> true
                            30 -> (idx % 5 == 0 || idx == daysListChart.size - 1)
                            else -> true
                        }

                        if (shouldShowXLabel) {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    dayStr,
                                    pt.x,
                                    h - 6.dp.toPx(),
                                    nativePaintText
                                )
                            }
                        }

                        // Value Label Above Point
                        val shouldShowValLabel = when (selectedDaysPeriod) {
                            7 -> income > 0.0
                            30 -> (income == peakIncomeInPeriod && income > 0.0) || isSelected
                            else -> false
                        }

                        if (shouldShowValLabel) {
                            val valStr = formatChartAmt(income)
                            if (valStr.isNotEmpty()) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        valStr,
                                        pt.x,
                                        pt.y - 8.dp.toPx(),
                                        nativePaintVal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrinterSettingsDialog(
    currentMethod: String,
    currentPaperWidth: Int,
    namaUsaha: String,
    sampleTx: CashierTransaction,
    onSave: (method: String, width: Int) -> Unit,
    onDismiss: () -> Unit,
    showToast: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf(currentMethod) }
    var selectedPaperWidth by remember { mutableStateOf(currentPaperWidth) }
    var showPreview by remember { mutableStateOf(false) }

    val sampleTextReceipt = remember(sampleTx, namaUsaha, selectedPaperWidth) {
        generateTextReceipt(sampleTx, namaUsaha, selectedPaperWidth)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Print,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Pengaturan Printer Thermal POS",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Atur ukuran kertas dan metode pencetakan default untuk printer Bluetooth atau USB OTG. Pilihan ini tersimpan otomatis untuk pencetakan berikutnya.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Ukuran Kertas Thermal:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPaperWidth == 32,
                        onClick = { selectedPaperWidth = 32 },
                        label = { Text("58 mm (32 Cpl)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPaperWidth == 48,
                        onClick = { selectedPaperWidth = 48 },
                        label = { Text("80 mm (48 Cpl)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text("Metode Cetak Default:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // Option 1: Direct Bluetooth / USB OTG Printer App (RawBT / ESC/POS)
                Card(
                    onClick = { selectedMethod = "direct" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMethod == "direct") Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(
                        if (selectedMethod == "direct") 2.dp else 1.dp,
                        if (selectedMethod == "direct") Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMethod == "direct",
                            onClick = { selectedMethod = "direct" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Direct Bluetooth / USB OTG Printer App",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (selectedMethod == "direct") Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Kirim format ESC/POS ke RawBT / Bluetooth Thermal Printer",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Layanan Cetak Sistem Android
                Card(
                    onClick = { selectedMethod = "system" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMethod == "system") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(
                        if (selectedMethod == "system") 2.dp else 1.dp,
                        if (selectedMethod == "system") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMethod == "system",
                            onClick = { selectedMethod = "system" }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Layanan Cetak Sistem Android",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = if (selectedMethod == "system") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Mendukung Printer Bluetooth, USB OTG, & Print Service",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (selectedMethod == "direct") {
                            sendToThermalPrinterApp(context, sampleTx, namaUsaha, selectedPaperWidth)
                            showToast("Mengirim cetak contoh ke printer POS Thermal...")
                        } else {
                            printReceiptWeb(context, sampleTx, namaUsaha)
                            showToast("Membuka Layanan Cetak Sistem...")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uji Coba Cetak Struk Contoh", fontSize = 12.sp)
                }

                TextButton(
                    onClick = { showPreview = !showPreview },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        if (showPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showPreview) "Sembunyikan Pratinjau Teks" else "Pratinjau Teks Struk POS")
                }

                if (showPreview) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFFDE7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F))
                    ) {
                        Text(
                            text = sampleTextReceipt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedMethod, selectedPaperWidth) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Simpan Pengaturan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
