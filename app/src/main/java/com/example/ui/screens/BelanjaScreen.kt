package com.example.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ShoppingItem
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

// Category Free Vector Icon Mapping Helper
fun getCategoryIcon(jenis: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (jenis) {
        "Pakaian & Fashion" -> Icons.Default.Checkroom
        "Perlengkapan Sekolah" -> Icons.Default.School
        "Supermarket" -> Icons.Default.ShoppingCart
        "Perawatan & Kecantikan" -> Icons.Default.Spa
        "Elektronik" -> Icons.Default.Devices
        "Mainan & Anak" -> Icons.Default.SportsEsports
        "Perabotan Rumah" -> Icons.Default.Home
        else -> Icons.Default.ShoppingBag
    }
}

// Category Custom Colors for beautiful native badges
fun getCategoryColor(jenis: String): Color {
    return when (jenis) {
        "Pakaian & Fashion" -> Color(0xFF9C27B0) // Purple
        "Perlengkapan Sekolah" -> Color(0xFF2196F3) // Blue
        "Supermarket" -> Color(0xFF4CAF50) // Green
        "Perawatan & Kecantikan" -> Color(0xFFE91E63) // Pink
        "Elektronik" -> Color(0xFFFF9800) // Orange
        "Mainan & Anak" -> Color(0xFF00BCD4) // Cyan
        "Perabotan Rumah" -> Color(0xFF795548) // Brown
        else -> Color(0xFF607D8B) // Blue-Grey
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
fun BelanjaScreen(viewModel: MainViewModel) {
    val items by viewModel.shoppingItems.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilterCategory by remember { mutableStateOf("Semua") }
    var filterDateStart by remember { mutableStateOf("") }
    var filterDateEnd by remember { mutableStateOf("") }

    var showFormDialog by remember { mutableStateOf(false) }
    var isEditingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var showSafWarningDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    var feedbackBannerIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var feedbackBannerColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(feedbackBannerMessage) {
        if (feedbackBannerMessage != null) {
            delay(2800)
            feedbackBannerMessage = null
        }
    }

    // Form inputs state
    var inputNama by remember { mutableStateOf("") }
    var inputJenis by remember { mutableStateOf("") }
    var inputHarga by remember { mutableStateOf("") }
    var inputQty by remember { mutableStateOf("1") }
    var inputPajak by remember { mutableStateOf("0") }
    var inputTanggal by remember { mutableStateOf("") }

    val dateFormater = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Categories List
    val categories = listOf(
        "Pakaian & Fashion",
        "Perlengkapan Sekolah",
        "Supermarket",
        "Perawatan & Kecantikan",
        "Elektronik",
        "Mainan & Anak",
        "Perabotan Rumah"
    )

    // Reset Form Input Helper
    fun resetForm() {
        isEditingItem = null
        inputNama = ""
        inputJenis = ""
        inputHarga = ""
        inputQty = "1"
        inputPajak = "0"
        inputTanggal = dateFormater.format(Date())
    }

    // Filtered list
    val filteredItems = items.filter { item ->
        val matchesSearch = item.nama.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedFilterCategory == "Semua" || item.jenis == selectedFilterCategory
        val matchesDateStart = filterDateStart.isEmpty() || item.tanggal >= filterDateStart
        val matchesDateEnd = filterDateEnd.isEmpty() || item.tanggal <= filterDateEnd
        matchesSearch && matchesCategory && matchesDateStart && matchesDateEnd
    }

    // Calculations for summary cards
    val totalBelanja = filteredItems.sumOf { it.total }
    val totalQty = filteredItems.sumOf { it.qty }
    val totalPajakSum = filteredItems.sumOf {
        it.hargaSatuan * it.qty * (it.pajak / 100.0)
    }

    // Find major category count
    val categoryCounts = filteredItems.groupingBy { it.jenis }.eachCount()
    val maxCategory = categoryCounts.maxByOrNull { it.value }?.key ?: "—"

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("belanja_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // SECTION 1: VISUAL SUMMARY (Asymmetric Premium Cards Layout)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main Grand Card (Total Belanja) with modern primary gradient
                val gradientBrush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(gradientBrush),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSearchActive) {
                                // Search Input Field replacing the title when active
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    placeholder = { Text("Cari...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                                        disabledContainerColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                                        cursorColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Text(
                                    text = "TOTAL BELANJA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            // Right-aligned Actions Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. Filter dropdown menu list
                                var filterMenuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { filterMenuExpanded = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Filter",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = filterMenuExpanded,
                                        onDismissRequest = { filterMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Semua Kategori", fontSize = 13.sp, fontWeight = if (selectedFilterCategory == "Semua") FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedFilterCategory = "Semua"
                                                filterMenuExpanded = false
                                            }
                                        )
                                        categories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat, fontSize = 13.sp, fontWeight = if (selectedFilterCategory == cat) FontWeight.Bold else FontWeight.Normal) },
                                                onClick = {
                                                    selectedFilterCategory = cat
                                                    filterMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // 2. Printer Icon Button
                                IconButton(
                                    onClick = { printShoppingList(context, filteredItems) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Cetak",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                // 3. Add Icon Button (tanpa title/label)
                                IconButton(
                                    onClick = {
                                        if (viewModel.safFolderUriStr.isEmpty()) {
                                            showSafWarningDialog = true
                                        } else {
                                            resetForm()
                                            showFormDialog = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Tambah",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }

                                // 4. Search Icon Button ("icon kaca pembesar")
                                IconButton(
                                    onClick = {
                                        if (isSearchActive) {
                                            searchQuery = ""
                                        }
                                        isSearchActive = !isSearchActive
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "Cari",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = String.format("Rp %,.0f", totalBelanja),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            letterSpacing = (-0.5).sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kategori Terbanyak: $maxCategory",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                // Two supporting cards side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Supporting Card 1: Jumlah Item
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingBag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Jumlah Item",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$totalQty Qty",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Supporting Card 2: Total Pajak
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Percent,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Total Pajak",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("Rp %,.0f", totalPajakSum),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }





        // SECTION 5: BEAUTIFUL NATIVE LIST CARDS (Adaptive Design)
        if (filteredItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Belum ada belanjaan yang ditambahkan.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gunakan tombol Tambah Item untuk mencatat transaksi baru Anda.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                ShoppingTable(
                    items = filteredItems,
                    onEdit = { item ->
                        isEditingItem = item
                        inputNama = item.nama
                        inputJenis = item.jenis
                        inputHarga = item.hargaSatuan.toLong().toString()
                        inputQty = item.qty.toString()
                        inputPajak = item.pajak.toString()
                        inputTanggal = item.tanggal
                        showFormDialog = true
                    },
                    onDelete = { item ->
                        itemToDelete = item
                    },
                    limit = 4,
                    onPageClick = { viewModel.currentRoute = "semua_transaksi_belanja" }
                )
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

    // POP-UP / DIALOG FORM (Tambah atau Edit)
    if (showFormDialog) {
        Dialog(onDismissRequest = { showFormDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditingItem == null) "Tambah Transaksi" else "Edit Transaksi",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showFormDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Tutup", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputNama,
                        onValueChange = { inputNama = it },
                        label = { Text("Nama Barang *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // CATEGORIES SELECT
                    var selectCategoryExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { selectCategoryExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val textLabel = if (inputJenis.isEmpty()) "Pilih Jenis Barang *" else inputJenis
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inputJenis.isNotEmpty()) {
                                        Icon(
                                            imageVector = getCategoryIcon(inputJenis),
                                            contentDescription = null,
                                            tint = getCategoryColor(inputJenis),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(textLabel, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(
                            expanded = selectCategoryExpanded,
                            onDismissRequest = { selectCategoryExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = getCategoryIcon(cat),
                                                contentDescription = null,
                                                tint = getCategoryColor(cat),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(cat)
                                        }
                                    },
                                    onClick = {
                                        inputJenis = cat
                                        selectCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputHarga,
                            onValueChange = { inputHarga = it },
                            label = { Text("Harga Satuan (Rp) *") },
                            modifier = Modifier.weight(1.5f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            label = { Text("Qty *") },
                            modifier = Modifier.weight(1.0f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputPajak,
                        onValueChange = { inputPajak = it },
                        label = { Text("Pajak (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var showDatePickerDialog by remember { mutableStateOf(false) }
                    val showDatePicker = { showDatePickerDialog = true }

                    if (showDatePickerDialog) {
                        com.example.ui.components.CustomDatePickerDialog(
                            initialDateStr = inputTanggal,
                            onDismissRequest = { showDatePickerDialog = false },
                            onDateSelected = { selectedDate ->
                                inputTanggal = selectedDate
                                showDatePickerDialog = false
                            }
                        )
                    }

                    OutlinedTextField(
                        value = inputTanggal,
                        onValueChange = { inputTanggal = it },
                        label = { Text("Tanggal Pembelian (YYYY-MM-DD)") },
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

                    // Autocalculated Preview Card
                    val cleanHarga = inputHarga.replace(".", "").replace(",", ".")
                    val pUnit = cleanHarga.toDoubleOrNull() ?: 0.0
                    val pQty = inputQty.replace(".", "").replace(",", "").toIntOrNull() ?: 1
                    val pTax = inputPajak.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                    val subTotal = pUnit * pQty
                    val calculatedPajak = subTotal * (pTax / 100.0)
                    val calculatedTotal = subTotal + calculatedPajak

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                Text("Rp " + String.format("%,.0f", subTotal), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pajak:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                Text("Rp " + String.format("%,.0f", calculatedPajak), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Estimasi Total:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    text = "Rp " + String.format("%,.0f", calculatedTotal),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showFormDialog = false }) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (inputNama.isNotEmpty() && inputJenis.isNotEmpty() && pUnit > 0) {
                                    val currentItem = isEditingItem
                                    val shoppingObj = ShoppingItem(
                                        id = currentItem?.id ?: 0,
                                        nama = inputNama,
                                        jenis = inputJenis,
                                        hargaSatuan = pUnit,
                                        qty = pQty,
                                        pajak = pTax,
                                        total = calculatedTotal,
                                        tanggal = if (inputTanggal.isNotEmpty()) inputTanggal else dateFormater.format(Date())
                                    )
                                    viewModel.saveShoppingItem(shoppingObj)
                                    showFormDialog = false
                                    feedbackBannerMessage = "Item \"$inputNama\" berhasil disimpan!"
                                    feedbackBannerIcon = Icons.Default.CheckCircle
                                    feedbackBannerColor = Color(0xFF10B981)
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
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
                Text("Untuk mengelola pembelanjaan lokal atau menambahkan data item belanja kustom Anda, " +
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

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Text(
                    text = "Hapus Item Belanja?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus item \"${itemToDelete?.nama}\"? Tindakan ini tidak dapat dibatalkan.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedName = itemToDelete?.nama ?: "Item"
                        itemToDelete?.let { viewModel.deleteShoppingItem(it) }
                        itemToDelete = null
                        feedbackBannerMessage = "Item \"$deletedName\" berhasil dihapus."
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
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

// Category Chip Composable
@Composable
fun CategoryFilterChip(
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderStroke = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = backgroundColor,
        border = borderStroke,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val iconVec = getCategoryIcon(category)
            Icon(
                imageVector = iconVec,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (category == "Semua") "Semua Kategori" else category,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

// PRINT MANAGER INTEGRATION UTILITY
fun printShoppingList(context: Context, items: List<ShoppingItem>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Daftar Belanja - My Singghara"

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: sans-serif; padding: 20px; color: #333; }
                h1 { text-align: center; color: #222; margin-bottom: 5px; font-size: 20px; }
                p { text-align: center; color: #666; margin-top: 5px; font-size: 11px; }
                table { width: 100%; border-collapse: collapse; margin-top: 20px; font-size: 11px; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f7f7f7; font-weight: bold; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fafafa; }
            </style>
        </head>
        <body>
            <h1>Daftar Belanja - My Singghara</h1>
            <p>Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            <table>
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Nama Barang</th>
                        <th>Jenis</th>
                        <th class="text-right">Harga Satuan</th>
                        <th class="text-center">Qty</th>
                        <th class="text-right">Pajak</th>
                        <th class="text-right">Total</th>
                        <th>Tanggal</th>
                    </tr>
                </thead>
                <tbody>
    """)

    var totalBelanja = 0.0
    var totalPajak = 0.0
    var totalQty = 0

    items.forEachIndexed { index, item ->
        val pajakNom = item.hargaSatuan * item.qty * (item.pajak / 100.0)
        totalBelanja += item.total
        totalPajak += pajakNom
        totalQty += item.qty

        htmlBuilder.append("""
            <tr>
                <td>${index + 1}</td>
                <td>${item.nama}</td>
                <td>${item.jenis}</td>
                <td class="text-right">Rp ${String.format("%,.0f", item.hargaSatuan)}</td>
                <td class="text-center">${item.qty}</td>
                <td class="text-right">${if (item.pajak > 0) "${item.pajak}% (Rp ${String.format("%,.0f", pajakNom)})" else "—"}</td>
                <td class="text-right" style="font-weight: bold;">Rp ${String.format("%,.0f", item.total)}</td>
                <td>${item.tanggal}</td>
            </tr>
        """)
    }

    htmlBuilder.append("""
                    <tr class="total-row">
                        <td colspan="3" class="text-right">Ringkasan Total:</td>
                        <td class="text-right">—</td>
                        <td class="text-center">$totalQty Items</td>
                        <td class="text-right">Rp ${String.format("%,.0f", totalPajak)}</td>
                        <td class="text-right" style="color: #007bff;">Rp ${String.format("%,.0f", totalBelanja)}</td>
                        <td>—</td>
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

// Gorgeous Redesigned List-Based Shopping Item display replacing wide rigid table!
@Composable
fun ShoppingTable(
    items: List<ShoppingItem>,
    onEdit: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    limit: Int = Int.MAX_VALUE,
    onPageClick: (() -> Unit)? = null
) {
    var currentPage by remember(items) { mutableStateOf(0) }
    val itemsPerPage = 4
    val isPaginated = limit != Int.MAX_VALUE
    val totalPages = if (isPaginated) (items.size + itemsPerPage - 1) / itemsPerPage else 1
    val paginatedItems = if (isPaginated) items.drop(currentPage * itemsPerPage).take(itemsPerPage) else items

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
                            Text("Barang/Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(60.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Qty", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                            Text("Harga per Satuan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("Aksi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Body Rows
                    paginatedItems.forEach { item ->
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(110.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(item.tanggal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(160.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(item.nama, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(140.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text(item.jenis.ifEmpty { "-" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(60.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(item.qty.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(120.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("Rp ${String.format("%,.0f", item.hargaSatuan)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)).padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onEdit(item) },
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
                                        onClick = { onDelete(item) },
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
        if (totalPages > 1) {
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
                        .clickable { onPageClick?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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

// Beautiful Shopping Item Card Composable
@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryIcon = getCategoryIcon(item.jenis)
    val categoryColor = getCategoryColor(item.jenis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle category icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = item.jenis,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.nama,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.jenis,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = categoryColor
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = item.tanggal,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Qty: ${item.qty}  ·  Rp ${String.format("%,.0f", item.hargaSatuan)} /satuan",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Total & Actions
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format("Rp %,.0f", item.total),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                if (item.pajak > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Pajak ${item.pajak}%",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit Icon Button with a clean vector icon (free)
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Delete Icon Button with a clean vector icon (free)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
