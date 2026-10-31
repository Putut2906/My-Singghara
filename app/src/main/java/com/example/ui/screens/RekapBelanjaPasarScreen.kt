package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BelanjaPasarItem
import com.example.ui.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekapBelanjaPasarScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val items by viewModel.allBelanjaPasar.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Hardware / Gesture Back Handler
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var selectedStatusFilter by remember { mutableStateOf("Semua") } // Semua, Belum, Selesai

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BelanjaPasarItem?>(null) }
    var itemToDelete by remember { mutableStateOf<BelanjaPasarItem?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Number formatter for Rupiah
    val idrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    // Filter items
    val filteredItems = remember(items, searchQuery, selectedCategoryFilter, selectedStatusFilter) {
        items.filter { item ->
            val matchQuery = searchQuery.isBlank() || item.nama.contains(searchQuery, ignoreCase = true) ||
                    item.catatan.contains(searchQuery, ignoreCase = true)
            val matchCategory = selectedCategoryFilter == "Semua" || item.kategori.equals(selectedCategoryFilter, ignoreCase = true)
            val matchStatus = when (selectedStatusFilter) {
                "Belum" -> !item.sudahDibeli
                "Selesai" -> item.sudahDibeli
                else -> true
            }
            matchQuery && matchCategory && matchStatus
        }
    }

    // Stats calculations
    val totalCount = items.size
    val boughtCount = items.count { it.sudahDibeli }
    val progressFraction = if (totalCount > 0) boughtCount.toFloat() / totalCount else 0f

    val totalEstimasi = items.sumOf { it.perkiraanHarga * it.jumlah }
    val totalRealisasi = items.filter { it.sudahDibeli }.sumOf {
        if (it.realisasiHarga > 0) it.realisasiHarga * it.jumlah else it.perkiraanHarga * it.jumlah
    }

    // Rekap per Satuan (e.g. 5 kg, 12 pcs, 3 ikat, 2 liter)
    val rekapPerSatuan = remember(items) {
        items.groupBy { it.satuan.lowercase().trim() }
            .mapValues { entry -> entry.value.sumOf { it.jumlah } }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoriesList = listOf(
        "Semua",
        "Sayuran",
        "Daging & Unggas",
        "Ikan & Laut",
        "Bumbu & Rempah",
        "Buah-buahan",
        "Sembako",
        "Tahu & Tempe",
        "Perlengkapan",
        "Lain-lain"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_belanja_pasar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tambah Belanjaan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 0. TITLE HEADER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Rekap Belanja Pasar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$boughtCount dari $totalCount item selesai",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. STATS & PROGRESS HERO CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PROGRESS BELANJA PASAR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$boughtCount / $totalCount Item Selesai",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Percentage Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (totalCount > 0 && boughtCount == totalCount) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${(progressFraction * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (totalCount > 0 && boughtCount == totalCount) Color.White else Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Linear Progress Indicator
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF10B981),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Financial Summary Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Estimasi Biaya",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = idrFormat.format(totalEstimasi),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Realisasi Terbeli",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = idrFormat.format(totalRealisasi),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        // 2. REKAP PER SATUAN (kg, pcs, ikat, dll.)
                        if (rekapPerSatuan.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Rekap Kuantitas per Satuan:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(rekapPerSatuan) { (satuan, qty) ->
                                    val formattedQty = if (qty % 1.0 == 0.0) qty.toInt().toString() else "%.2f".format(qty).trimEnd('0').trimEnd('.')
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "$formattedQty ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = satuan,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. TOOLBAR: TITIK 3, FILTER STATUS, FILTER KATEGORI DI SEBELAH KIRI KACA PEMBESAR
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // A. ICON TITIK 3 (di sebelah kiri icon kaca pembesar)
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .testTag("btn_more_belanja_pasar")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu Opsi",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Bagikan ke WhatsApp", fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF10B981))
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            shareShoppingListWhatsApp(context, items)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cetak Rekap Belanja", fontSize = 13.sp) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Print, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            printBelanjaPasarList(context, items)
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Bersihkan Item Selesai",
                                                fontSize = 13.sp,
                                                color = if (boughtCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CleaningServices,
                                                contentDescription = null,
                                                tint = if (boughtCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        },
                                        enabled = boughtCount > 0,
                                        onClick = {
                                            showMoreMenu = false
                                            showClearConfirmDialog = true
                                        }
                                    )
                                }
                            }

                            // B. FILTER STATUS (Menu list di sebelah kiri icon kaca pembesar)
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { showStatusMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedStatusFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = null,
                                                tint = if (selectedStatusFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = when (selectedStatusFilter) {
                                                    "Belum" -> "Belum"
                                                    "Selesai" -> "Selesai"
                                                    else -> "Status"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedStatusFilter != "Semua") FontWeight.Bold else FontWeight.Medium,
                                                color = if (selectedStatusFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showStatusMenu,
                                    onDismissRequest = { showStatusMenu = false }
                                ) {
                                    listOf(
                                        "Semua" to "Semua Status",
                                        "Belum" to "Belum Dibeli",
                                        "Selesai" to "Sudah Dibeli"
                                    ).forEach { (st, label) ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selectedStatusFilter == st) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedStatusFilter == st) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                selectedStatusFilter = st
                                                showStatusMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // C. FILTER KATEGORI (Menu list di sebelah kiri icon kaca pembesar)
                            Box(modifier = Modifier.weight(1.3f)) {
                                Surface(
                                    onClick = { showCategoryMenu = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selectedCategoryFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = null,
                                                tint = if (selectedCategoryFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (selectedCategoryFilter == "Semua") "Kategori" else selectedCategoryFilter,
                                                fontSize = 12.sp,
                                                fontWeight = if (selectedCategoryFilter != "Semua") FontWeight.Bold else FontWeight.Medium,
                                                color = if (selectedCategoryFilter != "Semua") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    categoriesList.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = cat,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedCategoryFilter == cat) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                selectedCategoryFilter = cat
                                                showCategoryMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // D. ICON KACA PEMBESAR (Search Icon di sebelah kanan)
                            IconButton(
                                onClick = { isSearchExpanded = !isSearchExpanded },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSearchExpanded || searchQuery.isNotEmpty()) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .testTag("btn_search_belanja_pasar")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Cari Belanjaan",
                                    tint = if (isSearchExpanded || searchQuery.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Expandable search field when search icon is active or search query present
                        AnimatedVisibility(visible = isSearchExpanded || searchQuery.isNotEmpty()) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_search_belanja_pasar"),
                                    placeholder = { Text("Cari barang belanja pasar...", fontSize = 13.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. ITEMS LIST
            if (filteredItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBasket,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedCategoryFilter != "Semua") "Tidak ada item belanja yang cocok" else "Daftar Belanja Pasar Kosong",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tekan tombol 'Tambah Belanjaan' untuk menyusun daftar belanja pasar dalam satuan kg, pcs, ikat, dll.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    MarketShoppingItemCard(
                        item = item,
                        idrFormat = idrFormat,
                        onToggle = { isChecked ->
                            viewModel.toggleBelanjaPasarStatus(item.id, isChecked)
                        },
                        onEdit = {
                            editingItem = item
                            showAddDialog = true
                        },
                        onDelete = {
                            itemToDelete = item
                        }
                    )
                }
            }
        }
    }

    // DIALOG: ADD / EDIT ITEM
    if (showAddDialog) {
        AddEditBelanjaPasarDialog(
            item = editingItem,
            onDismiss = {
                showAddDialog = false
                editingItem = null
            },
            onSave = { savedItem ->
                viewModel.saveBelanjaPasar(savedItem)
                showAddDialog = false
                editingItem = null
                Toast.makeText(context, "Item belanja pasar berhasil disimpan", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // DIALOG: DELETE CONFIRMATION
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Item Belanja?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apakah Anda yakin ingin menghapus '${itemToDelete?.nama}' dari rekap belanja pasar?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteBelanjaPasar(it) }
                        itemToDelete = null
                        Toast.makeText(context, "Item berhasil dihapus", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG: CLEAR COMPLETED CONFIRMATION
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Bersihkan Item Selesai?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Semua item yang sudah dicentang/dibeli ($boughtCount item) akan dihapus dari daftar.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCompletedBelanjaPasar()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Item selesai dibersihkan", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Bersihkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
fun MarketShoppingItemCard(
    item: BelanjaPasarItem,
    idrFormat: NumberFormat,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = remember(item.kategori) {
        when (item.kategori.lowercase()) {
            "sayuran" -> Color(0xFF10B981)
            "daging & unggas" -> Color(0xFFEF4444)
            "ikan & laut" -> Color(0xFF0284C7)
            "bumbu & rempah" -> Color(0xFFF59E0B)
            "buah-buahan" -> Color(0xFF8B5CF6)
            "sembako" -> Color(0xFFD97706)
            "tahu & tempe" -> Color(0xFFEAB308)
            else -> Color(0xFF6B7280)
        }
    }

    val formattedQty = remember(item.jumlah) {
        if (item.jumlah % 1.0 == 0.0) item.jumlah.toInt().toString() else "%.2f".format(item.jumlah).trimEnd('0').trimEnd('.')
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("card_belanja_pasar_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.sudahDibeli) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.sudahDibeli) 0.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.sudahDibeli) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for market shopping
            Checkbox(
                checked = item.sudahDibeli,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF10B981),
                    checkmarkColor = Color.White
                ),
                modifier = Modifier.testTag("chk_item_${item.id}")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Main Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle(!item.sudahDibeli) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.nama,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (item.sudahDibeli) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.sudahDibeli) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // QUANTITY & UNIT BADGE (E.G. 1.5 kg, 2 ikat, 10 pcs)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "$formattedQty ${item.satuan}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Category Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = categoryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = item.kategori,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Price & Note Info
                val estPrice = item.perkiraanHarga * item.jumlah
                if (estPrice > 0 || item.catatan.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (estPrice > 0) {
                            Text(
                                text = "Est: ${idrFormat.format(estPrice)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.catatan.isNotBlank()) {
                            Text(
                                text = "• ${item.catatan}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_edit_belanja_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_delete_belanja_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditBelanjaPasarDialog(
    item: BelanjaPasarItem?,
    onDismiss: () -> Unit,
    onSave: (BelanjaPasarItem) -> Unit
) {
    var nama by remember { mutableStateOf(item?.nama ?: "") }
    var kategori by remember { mutableStateOf(item?.kategori ?: "Sayuran") }
    var jumlahStr by remember {
        mutableStateOf(
            if (item != null) {
                if (item.jumlah % 1.0 == 0.0) item.jumlah.toInt().toString() else item.jumlah.toString()
            } else "1"
        )
    }
    var satuan by remember { mutableStateOf(item?.satuan ?: "kg") }
    var perkiraanHargaStr by remember {
        mutableStateOf(
            if (item != null && item.perkiraanHarga > 0) item.perkiraanHarga.toInt().toString() else ""
        )
    }
    var realisasiHargaStr by remember {
        mutableStateOf(
            if (item != null && item.realisasiHarga > 0) item.realisasiHarga.toInt().toString() else ""
        )
    }
    var catatan by remember { mutableStateOf(item?.catatan ?: "") }
    var customSatuan by remember { mutableStateOf("") }
    var isCustomSatuanMode by remember { mutableStateOf(false) }
    var showCommodityMenu by remember { mutableStateOf(false) }
    var selectedCommodityTitle by remember { mutableStateOf("") }

    // Popular market commodities quick suggestions
    val popularCommodities = listOf(
        "Cabai Rawit" to "kg",
        "Bawang Merah" to "kg",
        "Bawang Putih" to "kg",
        "Tomat" to "kg",
        "Kangkung" to "ikat",
        "Bayam" to "ikat",
        "Wortel" to "kg",
        "Ayam Potong" to "kg",
        "Daging Sapi" to "kg",
        "Telur Ayam" to "kg",
        "Tahu Putih" to "bks",
        "Tempe" to "papan",
        "Ikan Nila" to "kg",
        "Minyak Goreng" to "liter",
        "Beras" to "kg"
    )

    val commonUnits = listOf("kg", "gram", "ons", "pcs", "ikat", "liter", "bks", "karung", "ekor", "butir", "sisir", "papan", "dus", "Lainnya")

    val categories = listOf(
        "Sayuran",
        "Daging & Unggas",
        "Ikan & Laut",
        "Bumbu & Rempah",
        "Buah-buahan",
        "Sembako",
        "Tahu & Tempe",
        "Perlengkapan",
        "Lain-lain"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (item == null) "Tambah Belanjaan Pasar" else "Edit Belanjaan Pasar",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Pilihan Komoditas Populer Menu List (hanya saat tambah baru)
                if (item == null) {
                    Text(
                        text = "PILIHAN KOMODITAS POPULER (MENU LIST)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { showCommodityMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedCommodityTitle.isNotEmpty()) selectedCommodityTitle else "Pilih dari Menu Komoditas Populer...",
                                        fontSize = 13.sp,
                                        color = if (selectedCommodityTitle.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCommodityMenu,
                            onDismissRequest = { showCommodityMenu = false },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .heightIn(max = 320.dp)
                        ) {
                            val groupedCommodities = listOf(
                                "Bumbu & Rempah" to listOf(
                                    "Cabai Rawit" to "kg",
                                    "Bawang Merah" to "kg",
                                    "Bawang Putih" to "kg",
                                    "Jahe" to "ons",
                                    "Kunyit" to "ons",
                                    "Lengkuas" to "ons"
                                ),
                                "Sayuran" to listOf(
                                    "Kangkung" to "ikat",
                                    "Bayam" to "ikat",
                                    "Tomat" to "kg",
                                    "Wortel" to "kg",
                                    "Kubis / Kol" to "kg",
                                    "Buncis" to "kg",
                                    "Terong" to "kg"
                                ),
                                "Daging & Unggas" to listOf(
                                    "Ayam Potong" to "kg",
                                    "Daging Sapi" to "kg",
                                    "Daging Ayam Fillet" to "kg",
                                    "Hati Ayam" to "kg"
                                ),
                                "Ikan & Laut" to listOf(
                                    "Ikan Nila" to "kg",
                                    "Ikan Bandeng" to "kg",
                                    "Ikan Lele" to "kg",
                                    "Udang Basah" to "kg",
                                    "Cumi-cumi" to "kg"
                                ),
                                "Tahu & Tempe" to listOf(
                                    "Tahu Putih" to "bks",
                                    "Tempe" to "papan"
                                ),
                                "Sembako" to listOf(
                                    "Beras" to "kg",
                                    "Minyak Goreng" to "liter",
                                    "Telur Ayam" to "kg",
                                    "Gula Pasir" to "kg",
                                    "Tepung Terigu" to "kg"
                                ),
                                "Buah-buahan" to listOf(
                                    "Pisang" to "sisir",
                                    "Jeruk" to "kg",
                                    "Apel" to "kg",
                                    "Semangka" to "kg"
                                )
                            )

                            groupedCommodities.forEach { (catName, itemList) ->
                                Text(
                                    text = "• ${catName.uppercase()}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                itemList.forEach { (cName, cUnit) ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(cName, fontSize = 13.sp)
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        cUnit,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedCommodityTitle = "$cName ($cUnit)"
                                            nama = cName
                                            satuan = cUnit
                                            kategori = catName
                                            showCommodityMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Nama Barang
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Barang / Komoditas *") },
                    placeholder = { Text("Misal: Cabai Rawit, Bawang Merah, Ayam...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_nama_belanja_pasar"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quantity & Unit Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quantity
                    OutlinedTextField(
                        value = jumlahStr,
                        onValueChange = { jumlahStr = it },
                        label = { Text("Kuantitas *") },
                        placeholder = { Text("1.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_qty_belanja_pasar"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981)
                        )
                    )

                    // Unit Selection
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Satuan Belanja *",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        var expandedUnitDropdown by remember { mutableStateOf(false) }

                        OutlinedButton(
                            onClick = { expandedUnitDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isCustomSatuanMode) customSatuan.ifBlank { "Custom" } else satuan,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = expandedUnitDropdown,
                            onDismissRequest = { expandedUnitDropdown = false }
                        ) {
                            commonUnits.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u) },
                                    onClick = {
                                        expandedUnitDropdown = false
                                        if (u == "Lainnya") {
                                            isCustomSatuanMode = true
                                        } else {
                                            isCustomSatuanMode = false
                                            satuan = u
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (isCustomSatuanMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSatuan,
                        onValueChange = { customSatuan = it },
                        label = { Text("Ketik Satuan Sendiri") },
                        placeholder = { Text("Misal: pak, kantong, box...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Quick Increment Buttons for Qty
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pintasan:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("0.25", "0.5", "1", "2", "5").forEach { qVal ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { jumlahStr = qVal }
                        ) {
                            Text(
                                text = qVal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Kategori Dropdown / Chips
                Text(
                    text = "Kategori Bahan:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                var expandedCategoryDropdown by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { expandedCategoryDropdown = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = kategori, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                DropdownMenu(
                    expanded = expandedCategoryDropdown,
                    onDismissRequest = { expandedCategoryDropdown = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                kategori = cat
                                expandedCategoryDropdown = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Perkiraan Harga Satuan
                OutlinedTextField(
                    value = perkiraanHargaStr,
                    onValueChange = { perkiraanHargaStr = it },
                    label = { Text("Estimasi Harga per Satuan (Rp) - Opsional") },
                    placeholder = { Text("Misal: 35000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Catatan Tambahan
                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan / Keterangan (Opsional)") },
                    placeholder = { Text("Misal: minta potong 8, pilih yang merah segar...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isBlank()) return@Button
                    val parsedQty = jumlahStr.replace(",", ".").toDoubleOrNull() ?: 1.0
                    val finalSatuan = if (isCustomSatuanMode) customSatuan.ifBlank { "pcs" } else satuan
                    val parsedEst = perkiraanHargaStr.toDoubleOrNull() ?: 0.0
                    val parsedReal = realisasiHargaStr.toDoubleOrNull() ?: 0.0
                    val dateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                    val result = (item ?: BelanjaPasarItem(
                        nama = nama.trim(),
                        tanggal = dateFormatted
                    )).copy(
                        nama = nama.trim(),
                        kategori = kategori,
                        jumlah = parsedQty,
                        satuan = finalSatuan.trim(),
                        perkiraanHarga = parsedEst,
                        realisasiHarga = parsedReal,
                        catatan = catatan.trim()
                    )

                    onSave(result)
                },
                enabled = nama.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.testTag("btn_save_belanja_pasar")
            ) {
                Text(if (item == null) "Tambah" else "Simpan Perubahan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// WhatsApp Share Utility
fun shareShoppingListWhatsApp(context: Context, items: List<BelanjaPasarItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "Daftar belanja masih kosong", Toast.LENGTH_SHORT).show()
        return
    }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
    val sb = StringBuilder()
    sb.append("🛒 *REKAP DAFTAR BELANJA PASAR*\n")
    sb.append("📅 Tanggal: $dateFormat\n")
    sb.append("────────────────────────\n\n")

    val grouped = items.groupBy { it.kategori }
    grouped.forEach { (category, catItems) ->
        sb.append("*[$category]*\n")
        catItems.forEach { item ->
            val checkmark = if (item.sudahDibeli) "✅" else "⬜"
            val qtyStr = if (item.jumlah % 1.0 == 0.0) item.jumlah.toInt().toString() else "%.2f".format(item.jumlah).trimEnd('0').trimEnd('.')
            sb.append("$checkmark ${item.nama} : $qtyStr ${item.satuan}")
            if (item.catatan.isNotBlank()) {
                sb.append(" _(${item.catatan})_")
            }
            if (item.perkiraanHarga > 0) {
                val subtotal = (item.perkiraanHarga * item.jumlah).toInt()
                sb.append(" ~ Rp ${NumberFormat.getNumberInstance(Locale("id", "ID")).format(subtotal)}")
            }
            sb.append("\n")
        }
        sb.append("\n")
    }

    val totalEst = items.sumOf { it.perkiraanHarga * it.jumlah }.toInt()
    if (totalEst > 0) {
        sb.append("────────────────────────\n")
        sb.append("💰 *Total Estimasi:* Rp ${NumberFormat.getNumberInstance(Locale("id", "ID")).format(totalEst)}\n")
    }
    sb.append("\n_Dibuat dengan My Singghara - Rekap Belanja Pasar_")

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekap Belanja"))
}

// Print Shopping List Utility
fun printBelanjaPasarList(context: Context, items: List<BelanjaPasarItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "Daftar belanja masih kosong", Toast.LENGTH_SHORT).show()
        return
    }

    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
    val idrFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>Rekap Belanja Pasar</title>
            <style>
                body { font-family: sans-serif; margin: 20px; color: #1f2937; }
                h1 { color: #10b981; font-size: 20px; margin-bottom: 4px; }
                p { font-size: 13px; color: #4b5563; margin-top: 0; }
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 13px; }
                th { background-color: #f3f4f6; border: 1px solid #d1d5db; padding: 8px; text-align: left; }
                td { border: 1px solid #d1d5db; padding: 8px; }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 11px; background: #e5e7eb; }
                .total-box { margin-top: 15px; text-align: right; font-weight: bold; font-size: 14px; }
            </style>
        </head>
        <body>
            <h1>🛒 REKAP DAFTAR BELANJA PASAR</h1>
            <p>Tanggal: $dateFormat | Total Item: ${items.size}</p>
            <table>
                <thead>
                    <tr>
                        <th style="width: 30px;">✓</th>
                        <th>Nama Barang</th>
                        <th>Kategori</th>
                        <th class="text-center">Kuantitas</th>
                        <th class="text-right">Estimasi Subtotal</th>
                        <th>Keterangan</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())

    items.forEach { item ->
        val checkStatus = if (item.sudahDibeli) "[✓]" else "[ ]"
        val qtyFormatted = if (item.jumlah % 1.0 == 0.0) item.jumlah.toInt().toString() else "%.2f".format(item.jumlah).trimEnd('0').trimEnd('.')
        val subtotal = item.perkiraanHarga * item.jumlah
        val subtotalStr = if (subtotal > 0) idrFormat.format(subtotal) else "-"
        htmlBuilder.append("""
            <tr>
                <td class="text-center">$checkStatus</td>
                <td><strong>${item.nama}</strong></td>
                <td><span class="badge">${item.kategori}</span></td>
                <td class="text-center"><strong>$qtyFormatted ${item.satuan}</strong></td>
                <td class="text-right">$subtotalStr</td>
                <td>${item.catatan}</td>
            </tr>
        """.trimIndent())
    }

    val totalEst = items.sumOf { it.perkiraanHarga * it.jumlah }
    htmlBuilder.append("""
                </tbody>
            </table>
            <div class="total-box">
                Total Estimasi: ${idrFormat.format(totalEst)}
            </div>
        </body>
        </html>
    """.trimIndent())

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Rekap_Belanja_Pasar")
            printManager?.print("Rekap Belanja Pasar", printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
}
