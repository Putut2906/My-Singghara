package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BelanjaOnlineItem
import com.example.ui.MainViewModel
import com.example.ui.components.CustomDatePickerDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentPurple = Color(0xFF7C3AED)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BelanjaOnlineScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val items by viewModel.allBelanjaOnline.collectAsState()
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BelanjaOnlineItem?>(null) }
    var itemToDelete by remember { mutableStateOf<BelanjaOnlineItem?>(null) }

    val idrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    val filteredItems = remember(items, searchQuery, selectedCategory) {
        items.filter { item ->
            val q = searchQuery.trim()
            val matchQuery = q.isBlank() ||
                item.namaBarang.contains(q, ignoreCase = true) ||
                item.namaToko.contains(q, ignoreCase = true) ||
                item.marketplace.contains(q, ignoreCase = true) ||
                item.catatan.contains(q, ignoreCase = true)
            val matchCategory = selectedCategory == "Semua" ||
                item.kategori.equals(selectedCategory, ignoreCase = true)
            matchQuery && matchCategory
        }
    }

    val grandTotal = filteredItems.sumOf { it.total }
    val kategoriOptions = listOf(
        "Semua",
        "Kebutuhan Pokok",
        "Skincare",
        "Elektronik",
        "Hobi",
        "Fashion",
        "Perlengkapan Rumah",
        "Lain-lain"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = AccentPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Transaksi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Belanja Online",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${filteredItems.size} transaksi tercatat",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Hero card: total pengeluaran
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentPurple.copy(alpha = 0.10f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)) {
                        Text(
                            "TOTAL PENGELUARAN BELANJA ONLINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = AccentPurple
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            idrFormat.format(grandTotal),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Dihitung dari ${filteredItems.size} transaksi yang cocok dengan pencarian & filter",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Cari barang, toko, atau marketplace...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple
                    )
                )
            }

            // Kategori filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    kategoriOptions.forEach { k ->
                        FilterChip(
                            selected = selectedCategory == k,
                            onClick = { selectedCategory = k },
                            label = { Text(k, fontSize = 11.sp) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (items.isEmpty()) "Belum ada transaksi belanja online"
                                else "Tidak ada transaksi yang cocok",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (items.isEmpty()) "Tekan tombol \"+ Tambah Transaksi\" untuk mencatat pembelian pertama."
                                else "Coba ubah kata kunci atau filter kategori.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    BelanjaOnlineRow(
                        item = item,
                        idrFormat = idrFormat,
                        onClick = {
                            editingItem = item
                            showAddDialog = true
                        },
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditBelanjaOnlineDialog(
            item = editingItem,
            onDismiss = { showAddDialog = false },
            onSave = { saved ->
                viewModel.saveBelanjaOnline(saved)
                showAddDialog = false
            }
        )
    }

    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus transaksi?") },
            text = {
                Text("Hapus \"${target.namaBarang}\" dari ${target.marketplace} dari catatan belanja online?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBelanjaOnline(target)
                    itemToDelete = null
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun BelanjaOnlineRow(
    item: BelanjaOnlineItem,
    idrFormat: NumberFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentPurple.copy(alpha = 0.12f)
                ) {
                    Text(
                        item.marketplace.ifBlank { "Marketplace" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    item.tanggal.ifBlank { "-" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        item.kategori,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                item.namaBarang.ifBlank { "(tanpa nama barang)" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.namaToko.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        item.namaToko,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rincian",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${idrFormat.format(item.hargaBarang)} + ${idrFormat.format(item.ongkir)} + ${idrFormat.format(item.biayaLayanan)} − ${idrFormat.format(item.potongan)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.metodePembayaran,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentPurple
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "TOTAL",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        idrFormat.format(item.total),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (item.catatan.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    item.catatan,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AddEditBelanjaOnlineDialog(
    item: BelanjaOnlineItem?,
    onDismiss: () -> Unit,
    onSave: (BelanjaOnlineItem) -> Unit
) {
    val idrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0 }
    }

    var tanggal by remember {
        mutableStateOf(item?.tanggal ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var marketplace by remember { mutableStateOf(item?.marketplace ?: "") }
    var namaToko by remember { mutableStateOf(item?.namaToko ?: "") }
    var namaBarang by remember { mutableStateOf(item?.namaBarang ?: "") }
    var kategori by remember { mutableStateOf(item?.kategori ?: "Kebutuhan Pokok") }
    var hargaBarangStr by remember { mutableStateOf(doubleFieldInitial(item?.hargaBarang)) }
    var ongkirStr by remember { mutableStateOf(doubleFieldInitial(item?.ongkir)) }
    var biayaLayananStr by remember { mutableStateOf(doubleFieldInitial(item?.biayaLayanan)) }
    var potonganStr by remember { mutableStateOf(doubleFieldInitial(item?.potongan)) }
    var metodePembayaran by remember { mutableStateOf(item?.metodePembayaran ?: "Transfer Bank") }
    var catatan by remember { mutableStateOf(item?.catatan ?: "") }

    var showMarketplaceMenu by remember { mutableStateOf(false) }
    var showKategoriMenu by remember { mutableStateOf(false) }
    var showMetodeMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val marketplaceOptions = listOf("Tokopedia", "Shopee", "Blibli", "Lazada", "Bukalapak", "Zalora", "Amazon", "Etsy", "Instagram Shop", "TikTok Shop", "Lainnya")
    val kategoriOptions = listOf("Kebutuhan Pokok", "Skincare", "Elektronik", "Hobi", "Fashion", "Perlengkapan Rumah", "Lain-lain")
    val metodeOptions = listOf("Transfer Bank", "E-Wallet", "Kartu Kredit", "COD")

    val parsedHarga = hargaBarangStr.toDoubleSgp()
    val parsedOngkir = ongkirStr.toDoubleSgp()
    val parsedLayanan = biayaLayananStr.toDoubleSgp()
    val parsedPotongan = potonganStr.toDoubleSgp()
    val calculatedTotal = (parsedHarga + parsedOngkir + parsedLayanan - parsedPotongan).coerceAtLeast(0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentPurple.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = AccentPurple
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (item == null) "Tambah Transaksi Belanja Online" else "Edit Transaksi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Tanggal
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AccentPurple)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tanggal: $tanggal", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Marketplace
                    Box {
                        OutlinedTextField(
                            value = marketplace,
                            onValueChange = { marketplace = it },
                            label = { Text("Marketplace") },
                            placeholder = { Text("Tokopedia / Shopee / Blibli / ...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showMarketplaceMenu = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Pilih")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple
                            )
                        )
                        DropdownMenu(
                            expanded = showMarketplaceMenu,
                            onDismissRequest = { showMarketplaceMenu = false }
                        ) {
                            marketplaceOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, fontSize = 13.sp) },
                                    onClick = {
                                        marketplace = if (opt == "Lainnya") "" else opt
                                        showMarketplaceMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nama toko
                    OutlinedTextField(
                        value = namaToko,
                        onValueChange = { namaToko = it },
                        label = { Text("Nama Toko / Penjual") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nama barang
                    OutlinedTextField(
                        value = namaBarang,
                        onValueChange = { namaBarang = it },
                        label = { Text("Nama Barang") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPurple
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Kategori
                    Box {
                        OutlinedTextField(
                            value = kategori,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori Belanja") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showKategoriMenu = true },
                            trailingIcon = {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                        )
                        DropdownMenu(
                            expanded = showKategoriMenu,
                            onDismissRequest = { showKategoriMenu = false }
                        ) {
                            kategoriOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, fontSize = 13.sp) },
                                    onClick = {
                                        kategori = opt
                                        showKategoriMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Rincian Biaya",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = hargaBarangStr,
                        onValueChange = { hargaBarangStr = it },
                        label = { Text("Harga Barang (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ongkirStr,
                            onValueChange = { ongkirStr = it },
                            label = { Text("Ongkir") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = biayaLayananStr,
                            onValueChange = { biayaLayananStr = it },
                            label = { Text("Biaya Layanan") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = potonganStr,
                        onValueChange = { potonganStr = it },
                        label = { Text("Potongan (Voucher/Diskon/Cashback)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Total readonly display
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AccentPurple.copy(alpha = 0.10f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total Dibayarkan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                idrFormat.format(calculatedTotal),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AccentPurple
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metode pembayaran
                    Box {
                        OutlinedTextField(
                            value = metodePembayaran,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Metode Pembayaran") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMetodeMenu = true },
                            trailingIcon = {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                        )
                        DropdownMenu(
                            expanded = showMetodeMenu,
                            onDismissRequest = { showMetodeMenu = false }
                        ) {
                            metodeOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, fontSize = 13.sp) },
                                    onClick = {
                                        metodePembayaran = opt
                                        showMetodeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = catatan,
                        onValueChange = { catatan = it },
                        label = { Text("Catatan (Opsional)") },
                        placeholder = { Text("Nomor resi, kode voucher, dsb.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Batal") }
                        Button(
                            onClick = {
                                if (namaBarang.isBlank() || marketplace.isBlank()) return@Button
                                val base = item ?: BelanjaOnlineItem(
                                    tanggal = tanggal,
                                    marketplace = marketplace.trim(),
                                    namaToko = namaToko.trim(),
                                    namaBarang = namaBarang.trim(),
                                    kategori = kategori,
                                    hargaBarang = parsedHarga,
                                    ongkir = parsedOngkir,
                                    biayaLayanan = parsedLayanan,
                                    potongan = parsedPotongan,
                                    total = calculatedTotal,
                                    metodePembayaran = metodePembayaran,
                                    catatan = catatan.trim(),
                                    tanggalDibuat = System.currentTimeMillis()
                                )
                                val saved = base.copy(
                                    tanggal = tanggal,
                                    marketplace = marketplace.trim(),
                                    namaToko = namaToko.trim(),
                                    namaBarang = namaBarang.trim(),
                                    kategori = kategori,
                                    hargaBarang = parsedHarga,
                                    ongkir = parsedOngkir,
                                    biayaLayanan = parsedLayanan,
                                    potongan = parsedPotongan,
                                    total = calculatedTotal,
                                    metodePembayaran = metodePembayaran,
                                    catatan = catatan.trim()
                                )
                                onSave(saved)
                            },
                            enabled = namaBarang.isNotBlank() && marketplace.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                        ) {
                            Text(if (item == null) "Tambah" else "Simpan")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        CustomDatePickerDialog(
            initialDateStr = tanggal,
            onDismissRequest = { showDatePicker = false },
            onDateSelected = {
                tanggal = it
                showDatePicker = false
            }
        )
    }
}

private fun doubleFieldInitial(value: Double?): String {
    if (value == null || value == 0.0) return ""
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

private fun String.toDoubleSgp(): Double {
    val cleaned = this.replace(".", "").replace(",", ".")
    return cleaned.toDoubleOrNull() ?: 0.0
}
