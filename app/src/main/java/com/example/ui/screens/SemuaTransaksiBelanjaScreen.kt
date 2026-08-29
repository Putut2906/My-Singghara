package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ShoppingItem
import com.example.ui.MainViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemuaTransaksiBelanjaScreen(viewModel: MainViewModel) {
    val items by viewModel.shoppingItems.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterCategory by remember { mutableStateOf("Semua") }
    var filterDateStart by remember { mutableStateOf("") }
    var filterDateEnd by remember { mutableStateOf("") }

    var showFormDialog by remember { mutableStateOf(false) }
    var isEditingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }

    // Form inputs state
    var inputNama by remember { mutableStateOf("") }
    var inputJenis by remember { mutableStateOf("") }
    var inputHarga by remember { mutableStateOf("") }
    var inputQty by remember { mutableStateOf("1") }
    var inputPajak by remember { mutableStateOf("0") }
    var inputTanggal by remember { mutableStateOf("") }

    val dateFormater = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val categories = listOf(
        "Pakaian & Fashion",
        "Perlengkapan Sekolah",
        "Supermarket",
        "Perawatan & Kecantikan",
        "Elektronik",
        "Mainan & Anak",
        "Perabotan Rumah"
    )

    fun resetForm() {
        isEditingItem = null
        inputNama = ""
        inputJenis = ""
        inputHarga = ""
        inputQty = "1"
        inputPajak = "0"
        inputTanggal = dateFormater.format(Date())
    }

    val filteredItems = items.filter { item ->
        val matchesSearch = item.nama.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedFilterCategory == "Semua" || item.jenis == selectedFilterCategory
        val matchesDateStart = filterDateStart.isEmpty() || item.tanggal >= filterDateStart
        val matchesDateEnd = filterDateEnd.isEmpty() || item.tanggal <= filterDateEnd
        matchesSearch && matchesCategory && matchesDateStart && matchesDateEnd
    }

    BackHandler {
        viewModel.currentRoute = "belanja"
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("semua_transaksi_belanja_screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Info
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Terfilter", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                text = String.format("Rp %,.0f", filteredItems.sumOf { it.total }),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${filteredItems.size} Item",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Search and Filters
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pencarian & Filter", fontSize = 13.sp, fontWeight = FontWeight.Bold)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { printShoppingList(context, filteredItems) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Cetak",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                TextButton(onClick = {
                                    searchQuery = ""
                                    selectedFilterCategory = "Semua"
                                    filterDateStart = ""
                                    filterDateEnd = ""
                                }) {
                                    Text("Reset", fontSize = 12.sp)
                                }
                            }
                        }

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        var dropCategoryExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { dropCategoryExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Kategori: $selectedFilterCategory")
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }
                            DropdownMenu(
                                expanded = dropCategoryExpanded,
                                onDismissRequest = { dropCategoryExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Kategori") },
                                    onClick = {
                                        selectedFilterCategory = "Semua"
                                        dropCategoryExpanded = false
                                    }
                                )
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            selectedFilterCategory = cat
                                            dropCategoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Table displaying ALL filtered items
            if (filteredItems.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Tidak ada belanjaan yang cocok dengan filter.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        limit = Int.MAX_VALUE // Show all
                    )
                }
            }
        }

        // Action Dialogs
        if (showFormDialog) {
            Dialog(onDismissRequest = { showFormDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (isEditingItem == null) "Tambah Transaksi Belanja" else "Edit Transaksi Belanja",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = inputNama,
                            onValueChange = { inputNama = it },
                            label = { Text("Nama Barang") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        var jenisExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = inputJenis,
                                onValueChange = {},
                                label = { Text("Jenis Kategori") },
                                readOnly = true,
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { jenisExpanded = !jenisExpanded },
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                enabled = false
                            )
                            DropdownMenu(
                                expanded = jenisExpanded,
                                onDismissRequest = { jenisExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            inputJenis = cat
                                            jenisExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputHarga,
                            onValueChange = { inputHarga = it.filter { c -> c.isDigit() } },
                            label = { Text("Harga Satuan (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputQty,
                                onValueChange = { inputQty = it.filter { c -> c.isDigit() } },
                                label = { Text("Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inputPajak,
                                onValueChange = { inputPajak = it },
                                label = { Text("Pajak (%)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

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
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val cleanHarga = inputHarga.replace(".", "").replace(",", ".")
                        val pUnit = cleanHarga.toDoubleOrNull() ?: 0.0
                        val pQty = inputQty.replace(".", "").replace(",", "").toIntOrNull() ?: 1
                        val pTax = inputPajak.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                        val calculatedTotal = (pUnit * pQty) + (pUnit * pQty * (pTax / 100.0))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Estimasi Total Pembayaran", fontSize = 11.sp)
                                Text(
                                    text = String.format("Rp %,.0f", calculatedTotal),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showFormDialog = false }) {
                                Text("Batal")
                            }

                            Button(onClick = {
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
                                }
                            }) {
                                Text("Simpan")
                            }
                        }
                    }
                }
            }
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
                            itemToDelete?.let { viewModel.deleteShoppingItem(it) }
                            itemToDelete = null
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
    }
}
