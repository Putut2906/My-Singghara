package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.MainViewModel
import java.text.NumberFormat
import java.util.Locale
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.Date

// Data Models
data class KebutuhanBarang(
    val nama: String,
    val qty: Int,
    val hargaSatuan: Double
) {
    val totalHarga: Double get() = qty * hargaSatuan
}

data class EstimasiPendapatan(
    val namaSewa: String,
    val qty: Int,
    val hargaSewa: Double,
    val durasiHari: Int // Rata-rata jam operasional per hari
) {
    /*
     * LOGIKA OPERASIONAL JAM:
     * Jam operasional usaha dihitung rata-rata dari 08:00 sampai 21:00 (13 Jam per hari).
     *
     * Total Pendapatan Per Hari = Qty * Harga Sewa * (Estimasi Durasi Per Hari / 13.0)
     * Total Pendapatan Per Bulan = Total Pendapatan Per Hari * 30 Hari
     */
    val totalPendapatanPerHari: Double get() {
        val operasionalJamTotal = 13.0 // 08:00 - 21:00 adalah 13 jam per hari
        val proporsiWaktu = durasiHari.toDouble() / operasionalJamTotal
        return qty * hargaSewa * proporsiWaktu
    }

    val totalPendapatanPerBulan: Double get() = totalPendapatanPerHari * 30.0
}

data class PengeluaranOperasional(
    val jenis: String,
    val qty: Int,
    val harga: Double,
    val jumlahHari: Int
) {
    val totalPengeluaran: Double get() = qty * harga * jumlahHari
}

// Serialization Helpers for local persistence to SharedPreferences
fun serializeKebutuhan(list: List<KebutuhanBarang>): String {
    return list.joinToString("&&") { "${it.nama.replace("##", " ")}##${it.qty}##${it.hargaSatuan}" }
}
fun deserializeKebutuhan(serialized: String): List<KebutuhanBarang> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull {
            val parts = it.split("##")
            if (parts.size == 3) {
                KebutuhanBarang(parts[0], parts[1].toIntOrNull() ?: 0, parts[2].toDoubleOrNull() ?: 0.0)
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializePendapatan(list: List<EstimasiPendapatan>): String {
    return list.joinToString("&&") { "${it.namaSewa.replace("##", " ")}##${it.qty}##${it.hargaSewa}##${it.durasiHari}" }
}
fun deserializePendapatan(serialized: String): List<EstimasiPendapatan> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull {
            val parts = it.split("##")
            if (parts.size == 4) {
                EstimasiPendapatan(parts[0], parts[1].toIntOrNull() ?: 0, parts[2].toDoubleOrNull() ?: 0.0, parts[3].toIntOrNull() ?: 0)
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializeOperasional(list: List<PengeluaranOperasional>): String {
    return list.joinToString("&&") { "${it.jenis.replace("##", " ")}##${it.qty}##${it.harga}##${it.jumlahHari}" }
}
fun deserializeOperasional(serialized: String): List<PengeluaranOperasional> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull {
            val parts = it.split("##")
            if (parts.size == 4) {
                PengeluaranOperasional(parts[0], parts[1].toIntOrNull() ?: 0, parts[2].toDoubleOrNull() ?: 0.0, parts[3].toIntOrNull() ?: 0)
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Stock Item Data Model
data class StockItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nama: String,
    val kategori: String,
    val keterangan: String,
    val harga: Double,
    val stok: Int,
    val barcode: String = "",
    val tanggal: String = "",
    val foto: String = ""
)

fun serializeStock(list: List<StockItem>): String {
    return list.joinToString("&&") { 
        "${it.id}##${it.nama.replace("##", " ")}##${it.kategori.replace("##", " ")}##${it.keterangan.replace("##", " ")}##${it.harga}##${it.stok}##${it.barcode.replace("##", " ")}##${it.tanggal}##${it.foto.replace("##", "")}" 
    }
}

fun deserializeStock(serialized: String): List<StockItem> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull {
            val parts = it.split("##")
            if (parts.size >= 6) {
                StockItem(
                    id = parts[0],
                    nama = parts[1],
                    kategori = parts[2],
                    keterangan = parts[3],
                    harga = parts[4].toDoubleOrNull() ?: 0.0,
                    stok = parts[5].toIntOrNull() ?: 0,
                    barcode = if (parts.size > 6) parts[6] else "",
                    tanggal = if (parts.size > 7) parts[7] else "",
                    foto = if (parts.size > 8) parts[8] else ""
                )
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Cart Item Data Model (for multi-item cashier transaction)
data class CartItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val namaBarang: String,
    val kategori: String,
    val qty: Int,
    val harga: Double
) {
    val totalHarga: Double get() = qty * harga
}

// Cashier Transaction Data Model (now supports multi-item checkout and tax setting + tendered cash)
data class CashierTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val items: List<CartItem>,
    val waktu: String,
    val taxRate: Double = 0.11,
    val taxEnabled: Boolean = true,
    val tunai: Double = 0.0
) {
    val totalHarga: Double get() = items.sumOf { it.totalHarga }
}

fun serializeCashier(list: List<CashierTransaction>): String {
    return list.joinToString("&&") { tx ->
        val itemsStr = tx.items.joinToString("||") { item ->
            val safeNama = item.namaBarang.replace("&&", " ").replace("##", " ").replace("||", " ").replace("~~", " ")
            val safeKat = item.kategori.replace("&&", " ").replace("##", " ").replace("||", " ").replace("~~", " ")
            "${item.id}~~${safeNama}~~${safeKat}~~${item.qty}~~${item.harga}"
        }
        val safeWaktu = tx.waktu.replace("##", " ").replace("&&", " ")
        "${tx.id}##${safeWaktu}##${itemsStr}##${tx.taxRate}##${tx.taxEnabled}##${tx.tunai}"
    }
}

fun deserializeCashier(serialized: String): List<CashierTransaction> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull { entry ->
            val parts = entry.split("##")
            if (parts.size >= 3) {
                val id = parts[0]
                val waktu = parts[1]
                val itemsStr = parts[2]
                
                val taxRate = if (parts.size > 3) parts[3].toDoubleOrNull() ?: 0.11 else 0.11
                val taxEnabled = if (parts.size > 4) parts[4].toBooleanStrictOrNull() ?: true else true
                val tunai = if (parts.size > 5) parts[5].toDoubleOrNull() ?: 0.0 else 0.0
                
                val itemsList = if (itemsStr.isEmpty()) emptyList() else {
                    itemsStr.split("||").mapNotNull { itemEntry ->
                        val itemParts = itemEntry.split("~~")
                        if (itemParts.size == 5) {
                            CartItem(
                                id = itemParts[0],
                                namaBarang = itemParts[1],
                                kategori = itemParts[2],
                                qty = itemParts[3].toIntOrNull() ?: 0,
                                harga = itemParts[4].toDoubleOrNull() ?: 0.0
                            )
                        } else null
                    }
                }
                CashierTransaction(id = id, items = itemsList, waktu = waktu, taxRate = taxRate, taxEnabled = taxEnabled, tunai = tunai)
            } else if (parts.size == 6) {
                // Compatibility layer for old single-item transactions
                val id = parts[0]
                val nama = parts[1]
                val kat = parts[2]
                val qty = parts[3].toIntOrNull() ?: 0
                val harga = parts[4].toDoubleOrNull() ?: 0.0
                val waktu = parts[5]
                CashierTransaction(
                    id = id,
                    items = listOf(CartItem(namaBarang = nama, kategori = kat, qty = qty, harga = harga)),
                    waktu = waktu,
                    taxRate = 0.11,
                    taxEnabled = true,
                    tunai = 0.0
                )
            } else null
        }
    } catch (e: java.lang.Exception) {
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PembukuanScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE) }

    // Persistent State
    var namaUsaha by remember { mutableStateOf(sharedPrefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya") }
    var modalUsaha by remember { mutableStateOf(sharedPrefs.getFloat("modal_usaha", 0f).toDouble()) }

    var listKebutuhan by remember {
        mutableStateOf(deserializeKebutuhan(sharedPrefs.getString("list_kebutuhan", "") ?: ""))
    }
    var listPendapatan by remember {
        mutableStateOf(deserializePendapatan(sharedPrefs.getString("list_pendapatan", "") ?: ""))
    }
    var listOperasional by remember {
        mutableStateOf(deserializeOperasional(sharedPrefs.getString("list_operasional", "") ?: ""))
    }
    var listStock by remember {
        mutableStateOf(deserializeStock(sharedPrefs.getString("list_stock", "") ?: ""))
    }
    var listCashier by remember {
        mutableStateOf(deserializeCashier(sharedPrefs.getString("list_cashier", "") ?: ""))
    }

    // Helper functions for state saving
    val saveModalData = { name: String, amount: Double ->
        namaUsaha = name
        modalUsaha = amount
        sharedPrefs.edit()
            .putString("nama_usaha", name)
            .putFloat("modal_usaha", amount.toFloat())
            .apply()
    }

    val saveKebutuhan = { newList: List<KebutuhanBarang> ->
        listKebutuhan = newList
        sharedPrefs.edit().putString("list_kebutuhan", serializeKebutuhan(newList)).apply()
    }

    val savePendapatan = { newList: List<EstimasiPendapatan> ->
        listPendapatan = newList
        sharedPrefs.edit().putString("list_pendapatan", serializePendapatan(newList)).apply()
    }

    val saveOperasional = { newList: List<PengeluaranOperasional> ->
        listOperasional = newList
        sharedPrefs.edit().putString("list_operasional", serializeOperasional(newList)).apply()
    }

    val saveStock = { newList: List<StockItem> ->
        listStock = newList
        sharedPrefs.edit().putString("list_stock", serializeStock(newList)).apply()
    }

    val saveCashier = { newList: List<CashierTransaction> ->
        listCashier = newList
        sharedPrefs.edit().putString("list_cashier", serializeCashier(newList)).apply()
    }

    // Dialog state
    var showModalDialog by remember { mutableStateOf(false) }
    var showKebutuhanDialog by remember { mutableStateOf(false) }
    var showPendapatanDialog by remember { mutableStateOf(false) }
    var showOperasionalDialog by remember { mutableStateOf(false) }
    var showStockDialog by remember { mutableStateOf(false) }
    var showCashierDialog by remember { mutableStateOf(false) }
    var showUnduhBelanjaDialog by remember { mutableStateOf(false) }
    val quantitiesBelanja = remember { mutableStateMapOf<String, String>() }

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

    // Calculations logic
    val totalCashierSales = listCashier.sumOf { it.totalHarga }
    val totalPendapatanKotorPerBulan = listPendapatan.sumOf { it.totalPendapatanPerBulan } + totalCashierSales
    val totalExpenseKebutuhan = listKebutuhan.sumOf { it.totalHarga }
    val totalExpenseOperasional = listOperasional.sumOf { it.totalPengeluaran }
    val totalPengeluaran = totalExpenseKebutuhan + totalExpenseOperasional
    val labaBersihPerBulan = totalPendapatanKotorPerBulan - totalPengeluaran

    val balikModalBulan = if (labaBersihPerBulan > 0.0) {
        val result = modalUsaha / labaBersihPerBulan
        String.format(Locale.US, "%.1f Bulan", result)
    } else {
        "Laba belum positif"
    }

    Scaffold { innerPadding ->
        var selectedTabIndex by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Business Identity Card (Always visible at the top)
            BusinessIdentityCard(
                namaUsaha = namaUsaha,
                modalUsaha = modalUsaha,
                onEditModal = { showModalDialog = true },
                onPrint = {
                    printPembukuan(
                        context = context,
                        namaUsaha = namaUsaha,
                        modalUsaha = modalUsaha,
                        kebutuhan = listKebutuhan,
                        pendapatan = listPendapatan,
                        operasional = listOperasional,
                        totalKotor = totalPendapatanKotorPerBulan,
                        totalBeban = totalPengeluaran,
                        labaBersih = labaBersihPerBulan,
                        roi = balikModalBulan
                    )
                },
                toRupiah = toRupiah,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )

            // Tab Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> OverviewTab(
                        totalPendapatanKotorPerBulan = totalPendapatanKotorPerBulan,
                        totalExpenseKebutuhan = totalExpenseKebutuhan,
                        totalExpenseOperasional = totalExpenseOperasional,
                        totalPengeluaran = totalPengeluaran,
                        labaBersihPerBulan = labaBersihPerBulan,
                        balikModalBulan = balikModalBulan,
                        toRupiah = toRupiah
                    )
                    1 -> InvestasiTab(
                        listKebutuhan = listKebutuhan,
                        onAddClick = { showKebutuhanDialog = true },
                        onDeleteClick = { index ->
                            pendingDeleteType = "kebutuhan"
                            pendingDeleteIndex = index
                        },
                        toRupiah = toRupiah
                    )
                    2 -> PendapatanTab(
                        listPendapatan = listPendapatan,
                        onAddClick = { showPendapatanDialog = true },
                        onDeleteClick = { index ->
                            pendingDeleteType = "pendapatan"
                            pendingDeleteIndex = index
                        },
                        toRupiah = toRupiah
                    )
                    3 -> OperasionalTab(
                        listOperasional = listOperasional,
                        onAddClick = { showOperasionalDialog = true },
                        onDeleteClick = { index ->
                            pendingDeleteType = "operasional"
                            pendingDeleteIndex = index
                        },
                        toRupiah = toRupiah
                    )
                }
            }
        }

        // --- DIALOGS ---

        // Dialog 1: Modal Usaha
        if (showModalDialog) {
            var tempName by remember { mutableStateOf(namaUsaha) }
            var tempModal by remember { mutableStateOf(if (modalUsaha == 0.0) "" else modalUsaha.toLong().toString()) }

            AlertDialog(
                onDismissRequest = { showModalDialog = false },
                title = { Text("Input Modal Usaha", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            label = { Text("Nama Usaha") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = tempModal,
                            onValueChange = { tempModal = it },
                            label = { Text("Modal Usaha (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val modalVal = tempModal.toDoubleOrNull() ?: 0.0
                            if (tempName.isNotBlank() && modalVal > 0.0) {
                                saveModalData(tempName.trim(), modalVal)
                                showModalDialog = false
                                MainViewModel.instance?.showToastPopup("Modal berhasil diubah!")
                            } else {
                                MainViewModel.instance?.showToastPopup("Mohon lengkapi semua isian dengan benar!")
                            }
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showModalDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 2: Kebutuhan Barang
        if (showKebutuhanDialog) {
            var inputNamaBarang by remember { mutableStateOf("") }
            var inputQty by remember { mutableStateOf("") }
            var inputHargaSatuan by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showKebutuhanDialog = false },
                title = { Text("Input Kebutuhan Barang", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = inputNamaBarang,
                            onValueChange = { inputNamaBarang = it },
                            label = { Text("Nama Barang") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            label = { Text("Jumlah / Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputHargaSatuan,
                            onValueChange = { inputHargaSatuan = it },
                            label = { Text("Harga Satuan (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qtyVal = inputQty.toIntOrNull() ?: 0
                            val priceVal = inputHargaSatuan.toDoubleOrNull() ?: 0.0
                            if (inputNamaBarang.isNotBlank() && qtyVal > 0 && priceVal > 0.0) {
                                val newItem = KebutuhanBarang(inputNamaBarang.trim(), qtyVal, priceVal)
                                val mutable = listKebutuhan.toMutableList()
                                mutable.add(newItem)
                                saveKebutuhan(mutable)
                                showKebutuhanDialog = false
                                MainViewModel.instance?.showToastPopup("Kebutuhan ditambahkan!")
                            } else {
                                MainViewModel.instance?.showToastPopup("Mohon lengkapi isian dengan angka valid!")
                            }
                        }
                    ) {
                        Text("Tambah")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showKebutuhanDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 3: Estimasi Pendapatan
        if (showPendapatanDialog) {
            var inputNamaSewa by remember { mutableStateOf("") }
            var inputQty by remember { mutableStateOf("") }
            var inputHargaJual by remember { mutableStateOf("") }
            var inputDurasi by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showPendapatanDialog = false },
                title = { Text("Input Estimasi Pendapatan", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = inputNamaSewa,
                            onValueChange = { inputNamaSewa = it },
                            label = { Text("Nama Sewa Produk/Barang") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            label = { Text("Jumlah Sewa/Transaksi") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputHargaJual,
                            onValueChange = { inputHargaJual = it },
                            label = { Text("Harga Sewa / Jual (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputDurasi,
                            onValueChange = { inputDurasi = it },
                            label = { Text("Estimasi Durasi Per Hari (Jam)") },
                            supportingText = { Text("Jam kerja operasional: 13 jam (08:00 - 21:00)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qtyVal = inputQty.toIntOrNull() ?: 0
                            val priceVal = inputHargaJual.toDoubleOrNull() ?: 0.0
                            val durationVal = inputDurasi.toIntOrNull() ?: 0
                            if (inputNamaSewa.isNotBlank() && qtyVal > 0 && priceVal > 0.0 && durationVal > 0) {
                                if (durationVal > 13) {
                                    MainViewModel.instance?.showToastPopup("Durasi tidak bisa melebihi 13 jam operasional!")
                                } else {
                                    val newItem = EstimasiPendapatan(inputNamaSewa.trim(), qtyVal, priceVal, durationVal)
                                    val mutable = listPendapatan.toMutableList()
                                    mutable.add(newItem)
                                    savePendapatan(mutable)
                                    showPendapatanDialog = false
                                    MainViewModel.instance?.showToastPopup("Estimasi ditambahkan!")
                                }
                            } else {
                                MainViewModel.instance?.showToastPopup("Mohon lengkapi isian dengan angka valid!")
                            }
                        }
                    ) {
                        Text("Tambah")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPendapatanDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // Dialog 4: Pengeluaran Operasional
        if (showOperasionalDialog) {
            var inputJenis by remember { mutableStateOf("") }
            var inputQty by remember { mutableStateOf("") }
            var inputHarga by remember { mutableStateOf("") }
            var inputJumlahHari by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showOperasionalDialog = false },
                title = { Text("Input Pengeluaran Operasional", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = inputJenis,
                            onValueChange = { inputJenis = it },
                            label = { Text("Jenis Pengeluaran") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputQty,
                            onValueChange = { inputQty = it },
                            label = { Text("Qty / Pengali") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputHarga,
                            onValueChange = { inputHarga = it },
                            label = { Text("Harga Satuan (Rupiah)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputJumlahHari,
                            onValueChange = { inputJumlahHari = it },
                            label = { Text("Jumlah Hari") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qtyVal = inputQty.toIntOrNull() ?: 0
                            val priceVal = inputHarga.toDoubleOrNull() ?: 0.0
                            val daysVal = inputJumlahHari.toIntOrNull() ?: 0
                            if (inputJenis.isNotBlank() && qtyVal > 0 && priceVal > 0.0 && daysVal > 0) {
                                val newItem = PengeluaranOperasional(inputJenis.trim(), qtyVal, priceVal, daysVal)
                                val mutable = listOperasional.toMutableList()
                                mutable.add(newItem)
                                saveOperasional(mutable)
                                showOperasionalDialog = false
                                MainViewModel.instance?.showToastPopup("Operasional ditambahkan!")
                            } else {
                                MainViewModel.instance?.showToastPopup("Mohon lengkapi isian dengan angka valid!")
                            }
                        }
                    ) {
                        Text("Tambah")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOperasionalDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }



        // Dialog 5: Konfirmasi Hapus Data
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
                                "kebutuhan" -> {
                                    val mutable = listKebutuhan.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable.removeAt(idx)
                                        saveKebutuhan(mutable)
                                    }
                                }
                                "pendapatan" -> {
                                    val mutable = listPendapatan.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable.removeAt(idx)
                                        savePendapatan(mutable)
                                    }
                                }
                                "operasional" -> {
                                    val mutable = listOperasional.toMutableList()
                                    if (idx in mutable.indices) {
                                        mutable.removeAt(idx)
                                        saveOperasional(mutable)
                                    }
                                }
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
                            MainViewModel.instance?.showToastPopup("Data berhasil dihapus!")
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


    }
}

@Composable
fun BusinessIdentityCard(
    namaUsaha: String,
    modalUsaha: Double,
    onEditModal: () -> Unit,
    onPrint: () -> Unit,
    toRupiah: (Double) -> String,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = namaUsaha,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Modal Usaha: " + toRupiah(modalUsaha),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrint,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Button(
                        onClick = onEditModal,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Input Modal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Pilihan Laporan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            val menuItems = listOf("Ringkasan", "Investasi", "Pendapatan", "Operasional")
                            menuItems.forEachIndexed { index, title ->
                                val icon = when (index) {
                                    0 -> Icons.Default.Dashboard
                                    1 -> Icons.Default.Inventory2
                                    2 -> Icons.Default.TrendingUp
                                    else -> Icons.Default.MoneyOff
                                }
                                DropdownMenuItem(
                                    text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        onTabSelected(index)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewTab(
    totalPendapatanKotorPerBulan: Double,
    totalExpenseKebutuhan: Double,
    totalExpenseOperasional: Double,
    totalPengeluaran: Double,
    labaBersihPerBulan: Double,
    balikModalBulan: String,
    toRupiah: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Kalkulasi Kelayakan & ROI Usaha",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))

            // Pendapatan Kotor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Pendapatan Kotor (Bulan)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                Text(toRupiah(totalPendapatanKotorPerBulan), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Total Kebutuhan Investasi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Investasi Barang & Alat", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                Text(toRupiah(totalExpenseKebutuhan), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Total Beban Operasional
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pengeluaran Operasional", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
                Text(toRupiah(totalExpenseOperasional), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))

            // Laba Bersih
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Laba Bersih Per Bulan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                val colorLaba = if (labaBersihPerBulan >= 0) Color.White else Color(0xFFFFD1D1)
                Text(toRupiah(labaBersihPerBulan), fontSize = 16.sp, fontWeight = FontWeight.Black, color = colorLaba)
            }

            // Estimasi Balik Modal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Estimasi Balik Modal (ROI)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        balikModalBulan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = if (labaBersihPerBulan > 0.0) Color(0xFFC4E538) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun InvestasiTab(
    listKebutuhan: List<KebutuhanBarang>,
    onAddClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    toRupiah: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Kebutuhan Barang",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Tambah Kebutuhan", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (listKebutuhan.isEmpty()) {
                Text(
                    "Belum ada kebutuhan barang diinput.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                // Table setup
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NAMA BARANG", modifier = Modifier.width(120.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("QTY", modifier = Modifier.width(40.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("HARGA", modifier = Modifier.width(90.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("TOTAL", modifier = Modifier.width(100.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("AKSI", modifier = Modifier.width(50.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }

                    listKebutuhan.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.nama, modifier = Modifier.width(120.dp), fontSize = 12.sp)
                            Text(item.qty.toString(), modifier = Modifier.width(40.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(toRupiah(item.hargaSatuan), modifier = Modifier.width(90.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text(toRupiah(item.totalHarga), modifier = Modifier.width(100.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onDeleteClick(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
fun PendapatanTab(
    listPendapatan: List<EstimasiPendapatan>,
    onAddClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    toRupiah: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color(0xFF11998E),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Estimasi Pendapatan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Tambah Estimasi", tint = Color(0xFF11998E))
                }
            }

            if (listPendapatan.isEmpty()) {
                Text(
                    "Belum ada estimasi pendapatan diinput.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF11998E).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SEWA/JUAL PRODUK", modifier = Modifier.width(130.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("QTY", modifier = Modifier.width(40.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("HARGA", modifier = Modifier.width(90.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("DURASI", modifier = Modifier.width(60.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("BULANAN", modifier = Modifier.width(110.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("AKSI", modifier = Modifier.width(50.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }

                    listPendapatan.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.namaSewa, modifier = Modifier.width(130.dp), fontSize = 12.sp)
                            Text(item.qty.toString(), modifier = Modifier.width(40.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(toRupiah(item.hargaSewa), modifier = Modifier.width(90.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${item.durasiHari} jam", modifier = Modifier.width(60.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(toRupiah(item.totalPendapatanPerBulan), modifier = Modifier.width(110.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onDeleteClick(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
fun OperasionalTab(
    listOperasional: List<PengeluaranOperasional>,
    onAddClick: () -> Unit,
    onDeleteClick: (Int) -> Unit,
    toRupiah: (Double) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MoneyOff,
                        contentDescription = null,
                        tint = Color(0xFFFF5E62),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Pengeluaran Operasional",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Tambah Operasional", tint = Color(0xFFFF5E62))
                }
            }

            if (listOperasional.isEmpty()) {
                Text(
                    "Belum ada pengeluaran operasional diinput.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFF5E62).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JENIS PENGELUARAN", modifier = Modifier.width(130.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text("QTY", modifier = Modifier.width(40.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("HARGA", modifier = Modifier.width(90.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("HARI", modifier = Modifier.width(50.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        Text("TOTAL", modifier = Modifier.width(110.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                        Text("AKSI", modifier = Modifier.width(50.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    }

                    listOperasional.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.jenis, modifier = Modifier.width(130.dp), fontSize = 12.sp)
                            Text(item.qty.toString(), modifier = Modifier.width(40.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(toRupiah(item.harga), modifier = Modifier.width(90.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text("${item.jumlahHari} hari", modifier = Modifier.width(50.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                            Text(toRupiah(item.totalPengeluaran), modifier = Modifier.width(110.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { onDeleteClick(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun saveTextToDownloads(context: Context, fileName: String, textContent: String): Boolean {
    return try {
        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(textContent.toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                }
                true
            } else {
                false
            }
        } else {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null) {
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = java.io.File(downloadDir, fileName)
                file.writeText(textContent, Charsets.UTF_8)
                true
            } else {
                false
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun formatShoppingListToText(namaUsaha: String, items: List<StockItem>, quantities: Map<String, String>): String {
    val sb = java.lang.StringBuilder()
    sb.append("==========================================\n")
    sb.append("       DAFTAR BELANJA (STOK HABIS)       \n")
    sb.append("             $namaUsaha\n")
    sb.append("==========================================\n")
    sb.append("Tanggal ekspor: ${java.text.SimpleDateFormat("dd MMMM yyyy HH:mm", java.util.Locale("id", "ID")).format(java.util.Date())}\n\n")
    sb.append("Daftar barang dengan stok habis (0) beserta jumlah yang harus dibeli:\n\n")
    
    items.forEachIndexed { index, item ->
        val qty = quantities[item.id] ?: "10"
        val cleanQty = qty.ifBlank { "0" }
        sb.append("${index + 1}. Nama Barang  : ${item.nama}\n")
        sb.append("   Kategori     : ${item.kategori}\n")
        sb.append("   Harga Satuan : ${formatToRupiah(item.harga)}\n")
        sb.append("   Jumlah Beli  : $cleanQty unit\n")
        if (item.keterangan.isNotBlank()) {
            sb.append("   Keterangan   : ${item.keterangan}\n")
        }
        sb.append("------------------------------------------\n")
    }
    
    sb.append("\nTotal Barang Harus Dibeli: ${items.size} macam.\n")
    sb.append("Dicetak otomatis via Smart Financial System - My Singghara.\n")
    sb.append("==========================================\n")
    return sb.toString()
}

fun shareText(context: Context, subject: String, body: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
            putExtra(android.content.Intent.EXTRA_TEXT, body)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Bagikan Daftar Belanja"))
    } catch (e: Exception) {
        e.printStackTrace()
        MainViewModel.instance?.showToastPopup("Gagal membagikan daftar belanja")
    }
}

fun formatToRupiah(amount: Double): String {
    return try {
        val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
        formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
    } catch (e: Exception) {
        "Rp " + String.format(Locale.US, "%,.0f", amount)
    }
}

private fun printPembukuan(
    context: Context,
    namaUsaha: String,
    modalUsaha: Double,
    kebutuhan: List<KebutuhanBarang>,
    pendapatan: List<EstimasiPendapatan>,
    operasional: List<PengeluaranOperasional>,
    totalKotor: Double,
    totalBeban: Double,
    labaBersih: Double,
    roi: String
) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Laporan Pembukuan Usaha - $namaUsaha"

    val htmlBuilder = StringBuilder()
    htmlBuilder.append("""
        <html>
        <head>
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; }
                .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #333; padding-bottom: 15px; }
                .header h1 { margin: 0 0 5px 0; font-size: 24px; color: #111; text-transform: uppercase; }
                .header p { margin: 2px 0; color: #666; font-size: 12px; }
                .biz-info { margin-bottom: 25px; padding: 15px; background-color: #f8f9fa; border-left: 4px solid #007bff; border-radius: 4px; }
                .biz-info table { margin-top: 0; }
                .biz-info td { border: none; padding: 4px 8px; }
                .section-title { font-size: 15px; font-weight: bold; color: #007bff; margin-top: 25px; margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 5px; }
                table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 11px; }
                th, td { border: 1px solid #e0e0e0; padding: 10px 8px; text-align: left; }
                th { background-color: #f5f5f5; font-weight: bold; color: #444; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fafafa; }
                .summary-card { margin-top: 35px; border: 2px solid #28a745; border-radius: 8px; padding: 20px; background-color: #f4fbf6; }
                .summary-card.negative { border-color: #dc3545; background-color: #fff8f8; }
                .summary-title { font-size: 16px; font-weight: bold; color: #28a745; margin-top: 0; margin-bottom: 15px; border-bottom: 1px solid #c3e6cb; padding-bottom: 8px; }
                .summary-card.negative .summary-title { color: #dc3545; border-bottom-color: #f5c6cb; }
                .summary-flex { display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
                .summary-item { display: flex; justify-content: space-between; align-items: center; }
                .summary-divider { height: 1px; background-color: #ddd; margin: 8px 0; }
                .bold-text { font-weight: bold; }
                .laba-value { color: #28a745; font-size: 16px; font-weight: bold; }
                .summary-card.negative .laba-value { color: #dc3545; }
                .roi-box { margin-top: 15px; padding: 10px 15px; background-color: #e2f0d9; border-radius: 6px; display: flex; justify-content: space-between; font-weight: bold; }
                .summary-card.negative .roi-box { background-color: #fcdbd9; }
                .footer-meta { text-align: center; font-size: 10px; color: #888; margin-top: 40px; }
                tr { page-break-inside: avoid; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>Laporan Analisis Kelayakan Usaha</h1>
                <p>Aplikasi Smart Financial System - My Singghara</p>
                <p>Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>
            </div>

            <div class="biz-info">
                <table style="width: auto; border:none; margin: 0;">
                    <tr>
                        <td class="bold-text" style="width: 140px; font-size: 12px; padding: 2px 0;">Nama Usaha</td>
                        <td style="font-size: 12px; padding: 2px 0;">: $namaUsaha</td>
                    </tr>
                    <tr>
                        <td class="bold-text" style="font-size: 12px; padding: 2px 0;">Modal Awal Usaha</td>
                        <td style="font-size: 12px; padding: 2px 0;">: ${formatToRupiah(modalUsaha)}</td>
                    </tr>
                </table>
            </div>

            <!-- KEBUTUHAN BARANG -->
            <div class="section-title">I. Kebutuhan Investasi Barang & Peralatan</div>
            <table>
                <thead>
                    <tr>
                        <th style="width: 5%;">No</th>
                        <th>Nama Barang / Perlengkapan</th>
                        <th class="text-center" style="width: 10%;">Qty</th>
                        <th class="text-right" style="width: 25%;">Harga Satuan</th>
                        <th class="text-right" style="width: 25%;">Total Biaya</th>
                    </tr>
                </thead>
                <tbody>
    """)

    if (kebutuhan.isEmpty()) {
        htmlBuilder.append("""
            <tr>
                <td colspan="5" class="text-center" style="color: #666; font-style: italic;">Belum ada data kebutuhan investasi barang.</td>
            </tr>
        """)
    } else {
        kebutuhan.forEachIndexed { i, item ->
            htmlBuilder.append("""
                <tr>
                    <td class="text-center">${i + 1}</td>
                    <td>${item.nama}</td>
                    <td class="text-center">${item.qty}</td>
                    <td class="text-right">${formatToRupiah(item.hargaSatuan)}</td>
                    <td class="text-right">${formatToRupiah(item.totalHarga)}</td>
                </tr>
            """)
        }
    }

    val totalKebutuhan = kebutuhan.sumOf { it.totalHarga }
    htmlBuilder.append("""
                <tr class="total-row">
                    <td colspan="4" class="text-right">Total Anggaran Investasi Barang:</td>
                    <td class="text-right" style="color: #007bff;">${formatToRupiah(totalKebutuhan)}</td>
                </tr>
            </tbody>
        </table>

        <!-- ESTIMASI PENDAPATAN -->
        <div class="section-title">II. Estimasi Proyeksi Pendapatan (Per Bulan)</div>
        <table>
            <thead>
                <tr>
                    <th style="width: 5%;">No</th>
                    <th>Nama Sewa Produk / Layanan</th>
                    <th class="text-center" style="width: 10%;">Qty</th>
                    <th class="text-right" style="width: 25%;">Tarif / Harga Sewa</th>
                    <th class="text-center" style="width: 15%;">Operasional / Hari</th>
                    <th class="text-right" style="width: 25%;">Total Pendapatan (Bulan)</th>
                </tr>
            </thead>
            <tbody>
    """)

    if (pendapatan.isEmpty()) {
        htmlBuilder.append("""
            <tr>
                <td colspan="6" class="text-center" style="color: #666; font-style: italic;">Belum ada data estimasi proyeksi pendapatan.</td>
            </tr>
        """)
    } else {
        pendapatan.forEachIndexed { i, item ->
            htmlBuilder.append("""
                <tr>
                    <td class="text-center">${i + 1}</td>
                    <td>${item.namaSewa}</td>
                    <td class="text-center">${item.qty}</td>
                    <td class="text-right">${formatToRupiah(item.hargaSewa)}</td>
                    <td class="text-center">${item.durasiHari} Jam</td>
                    <td class="text-right">${formatToRupiah(item.totalPendapatanPerBulan)}</td>
                </tr>
            """)
        }
    }

    htmlBuilder.append("""
                <tr class="total-row">
                    <td colspan="5" class="text-right">Total Estimasi Pendapatan Kotor Per Bulan:</td>
                    <td class="text-right" style="color: #28a745;">${formatToRupiah(totalKotor)}</td>
                </tr>
            </tbody>
        </table>

        <!-- PENGELUARAN OPERASIONAL -->
        <div class="section-title">III. Rincian Pengeluaran Operasional</div>
        <table>
            <thead>
                <tr>
                    <th style="width: 5%;">No</th>
                    <th>Jenis Pengeluaran / Beban Usaha</th>
                    <th class="text-center" style="width: 10%;">Qty</th>
                    <th class="text-right" style="width: 25%;">Biaya Satuan</th>
                    <th class="text-center" style="width: 15%;">Jumlah Hari Kerja</th>
                    <th class="text-right" style="width: 25%;">Total Pengeluaran</th>
                </tr>
            </thead>
            <tbody>
    """)

    if (operasional.isEmpty()) {
        htmlBuilder.append("""
            <tr>
                <td colspan="6" class="text-center" style="color: #666; font-style: italic;">Belum ada data rincian beban operasional.</td>
            </tr>
        """)
    } else {
        operasional.forEachIndexed { i, item ->
            htmlBuilder.append("""
                <tr>
                    <td class="text-center">${i + 1}</td>
                    <td>${item.jenis}</td>
                    <td class="text-center">${item.qty}</td>
                    <td class="text-right">${formatToRupiah(item.harga)}</td>
                    <td class="text-center">${item.jumlahHari} Hari</td>
                    <td class="text-right">${formatToRupiah(item.totalPengeluaran)}</td>
                </tr>
            """)
        }
    }

    val totalBebanOperasional = operasional.sumOf { it.totalPengeluaran }
    htmlBuilder.append("""
                <tr class="total-row">
                    <td colspan="5" class="text-right">Total Beban Operasional:</td>
                    <td class="text-right" style="color: #dc3545;">${formatToRupiah(totalBebanOperasional)}</td>
                </tr>
            </tbody>
        </table>

        <!-- KALKULASI KELAYAKAN DAN SUMMARY CARD -->
        <div class="summary-card ${if (labaBersih >= 0) "" else "negative"}">
            <div class="summary-title">IV. Analisis Kelayakan Finansial &amp; Payback Period (ROI)</div>
            <div class="summary-flex">
                <div class="summary-item">
                    <span>Estimasi Pendapatan Kotor (Per Bulan)</span>
                    <span class="bold-text">${formatToRupiah(totalKotor)}</span>
                </div>
                <div class="summary-item">
                    <span>Total Beban Biaya (Investasi + Operasional)</span>
                    <span class="bold-text" style="color: #dc3545;">-${formatToRupiah(totalBeban)}</span>
                </div>
                <div class="summary-divider"></div>
                <div class="summary-item">
                    <span class="bold-text">Estimasi Laba Bersih (Per Bulan)</span>
                    <span class="laba-value">${formatToRupiah(labaBersih)}</span>
                </div>
                
                <div class="roi-box">
                    <span>PROYEKSI BALIK MODAL (ROI)</span>
                    <span>$roi</span>
                </div>
            </div>
        </div>

        <div class="footer-meta">
            Laporan ini dibuat secara otomatis melalui modul analisis kelayakan bisnis "My Singghara".<br/>
            Pastikan parameter dan data diinput secara realistis untuk mendukung keputusan perencanaan bisnis Anda.
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

fun printReceiptWeb(context: Context, tx: CashierTransaction, namaUsaha: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val firstName = tx.items.firstOrNull()?.namaBarang ?: "Multi-item"
    val jobName = "Struk Pembayaran - $firstName"

    val logoBase64 = try {
        val drawable = context.resources.getDrawable(com.example.R.drawable.img_app_icon_light_1785896910266, null)
        val originalBitmap = when (drawable) {
            is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
            else -> {
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }

        // Convert to a custom black and transparent sketch outline
        val width = originalBitmap.width
        val height = originalBitmap.height
        val sketchBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x < width - 1 && y < height - 1) {
                    val currentPixel = originalBitmap.getPixel(x, y)
                    val rightPixel = originalBitmap.getPixel(x + 1, y)
                    val bottomPixel = originalBitmap.getPixel(x, y + 1)

                    val currGray = (android.graphics.Color.red(currentPixel) + android.graphics.Color.green(currentPixel) + android.graphics.Color.blue(currentPixel)) / 3
                    val rightGray = (android.graphics.Color.red(rightPixel) + android.graphics.Color.green(rightPixel) + android.graphics.Color.blue(rightPixel)) / 3
                    val bottomGray = (android.graphics.Color.red(bottomPixel) + android.graphics.Color.green(bottomPixel) + android.graphics.Color.blue(bottomPixel)) / 3

                    val diffX = java.lang.Math.abs(currGray - rightGray)
                    val diffY = java.lang.Math.abs(currGray - bottomGray)
                    val totalDiff = diffX + diffY

                    // Draw edges as clean black lines, leave other areas transparent (eliminating colored background box shapes)
                    if (totalDiff > 20) {
                        sketchBitmap.setPixel(x, y, android.graphics.Color.BLACK)
                    } else {
                        sketchBitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                    }
                } else {
                    sketchBitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                }
            }
        }

        val outputStream = java.io.ByteArrayOutputStream()
        sketchBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    }

    val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    val fToRupiah = { amount: Double ->
        formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    val tax = if (tx.taxEnabled) (tx.totalHarga * tx.taxRate) else 0.0
    val grandTotal = tx.totalHarga + tax
    val payCash = if (tx.tunai > 0.0) tx.tunai else grandTotal
    val payback = payCash - grandTotal
    val taxText = if (tx.taxEnabled) "PPN (${(tx.taxRate * 100).toInt()}%):" else "PAJAK (NON-AKTIF):"

    val itemsHtmlBuilder = StringBuilder()
    tx.items.forEach { item ->
        itemsHtmlBuilder.append("""
            <div style="margin-bottom: 8px;">
                <div class="item-row">
                    <span class="bold">${item.namaBarang.uppercase(Locale.getDefault())}</span>
                    <span class="bold">${fToRupiah(item.totalHarga)}</span>
                </div>
                <div class="item-desc">
                    ${item.qty} x ${fToRupiah(item.harga)}
                </div>
            </div>
        """)
    }

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <style>
                body {
                    font-family: 'Courier New', Courier, monospace;
                    color: #000;
                    margin: 0;
                    padding: 15px;
                    width: 280px;
                    font-size: 11px;
                }
                .text-center { text-align: center; }
                .text-right { text-align: right; }
                .bold { font-weight: bold; }
                .divider {
                    border-top: 1px dashed #000;
                    margin: 8px 0;
                }
                .store-title {
                    font-size: 14px;
                    font-weight: bold;
                    margin-bottom: 2px;
                    letter-spacing: 1px;
                }
                .item-row {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 4px;
                }
                .item-desc {
                    padding-left: 10px;
                    font-size: 10px;
                    color: #555;
                }
                .footer {
                    margin-top: 15px;
                    font-size: 10px;
                }
            </style>
        </head>
        <body>
            <div style="display: flex; justify-content: space-between; align-items: center; min-height: 45px; margin-bottom: 5px;">
                <div class="store-title" style="margin: 0; text-align: left; max-width: 200px; line-height: 1.2;">
                    ${namaUsaha.uppercase(Locale.getDefault())}
                </div>
                ${if (logoBase64.isNotEmpty()) """<img src="data:image/png;base64,$logoBase64" style="width: 45px; height: 45px; object-fit: contain; margin-left: 5px;" />""" else ""}
            </div>
            
            <div class="divider"></div>
            
            <div>
                <div>TANGGAL: ${tx.waktu}</div>
                <div>KASIR  : ADMIN MYSINGGHARA</div>
            </div>
            
            <div class="divider"></div>
            
            <div>
                ${itemsHtmlBuilder.toString()}
            </div>
            
            <div class="divider"></div>
            
            <div class="item-row">
                <span>SUBTOTAL:</span>
                <span>${fToRupiah(tx.totalHarga)}</span>
            </div>
            <div class="item-row">
                <span>$taxText</span>
                <span>${fToRupiah(tax)}</span>
            </div>
            <div class="item-row bold">
                <span>TOTAL BELANJA:</span>
                <span>${fToRupiah(grandTotal)}</span>
            </div>
            <div class="item-row">
                <span>TUNAI:</span>
                <span>${fToRupiah(payCash)}</span>
            </div>
            <div class="item-row">
                <span>KEMBALI:</span>
                <span>${fToRupiah(payback)}</span>
            </div>
            
            <div class="divider"></div>
            
            <div class="text-center footer">
                <div class="bold">TERIMA KASIH</div>
                <div>ATAS KUNJUNGAN ANDA</div>
                <div style="margin-top: 10px; font-size: 8px;">BARANG YANG SUDAH DIBELI TIDAK DAPAT DITUKAR/DIKEMBALIKAN</div>
            </div>
        </body>
        </html>
    """

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            val printAttributes = PrintAttributes.Builder().build()
            printManager.print(jobName, printAdapter, printAttributes)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

fun generateTextReceipt(
    tx: CashierTransaction,
    namaUsaha: String,
    paperWidthChars: Int = 32
): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    val fToRupiah = { amount: Double ->
        formatter.format(amount).replace("Rp", "Rp ").replace(",00", "")
    }

    val tax = if (tx.taxEnabled) (tx.totalHarga * tx.taxRate) else 0.0
    val grandTotal = tx.totalHarga + tax
    val payCash = if (tx.tunai > 0.0) tx.tunai else grandTotal
    val payback = payCash - grandTotal
    val taxText = if (tx.taxEnabled) "PPN (${(tx.taxRate * 100).toInt()}%):" else "PAJAK:"

    val centerText = { text: String ->
        if (text.length >= paperWidthChars) text
        else {
            val pad = (paperWidthChars - text.length) / 2
            " ".repeat(pad) + text
        }
    }

    val lineLeftRight = { left: String, right: String ->
        val totalLen = left.length + right.length
        if (totalLen >= paperWidthChars) {
            left + "\n" + " ".repeat((paperWidthChars - right.length).coerceAtLeast(0)) + right
        } else {
            val space = paperWidthChars - totalLen
            left + " ".repeat(space) + right
        }
    }

    val sepDouble = "=".repeat(paperWidthChars)
    val sepSingle = "-".repeat(paperWidthChars)

    val sb = StringBuilder()
    sb.append(sepDouble).append("\n")
    sb.append(centerText(namaUsaha.uppercase(Locale.getDefault()))).append("\n")
    sb.append(sepDouble).append("\n")
    sb.append("TANGGAL: ${tx.waktu}\n")
    sb.append("KASIR  : ADMIN\n")
    sb.append(sepSingle).append("\n")

    tx.items.forEach { item ->
        sb.append(item.namaBarang.uppercase(Locale.getDefault())).append("\n")
        val detail = "${item.qty} x ${fToRupiah(item.harga)}"
        val totalStr = fToRupiah(item.totalHarga)
        sb.append(lineLeftRight(detail, totalStr)).append("\n")
    }

    sb.append(sepSingle).append("\n")
    sb.append(lineLeftRight("SUBTOTAL:", fToRupiah(tx.totalHarga))).append("\n")
    sb.append(lineLeftRight(taxText, fToRupiah(tax))).append("\n")
    sb.append(lineLeftRight("TOTAL:", fToRupiah(grandTotal))).append("\n")
    sb.append(lineLeftRight("TUNAI:", fToRupiah(payCash))).append("\n")
    sb.append(lineLeftRight("KEMBALI:", fToRupiah(payback))).append("\n")
    sb.append(sepDouble).append("\n")
    sb.append(centerText("TERIMA KASIH")).append("\n")
    sb.append(centerText("ATAS KUNJUNGAN ANDA")).append("\n")
    sb.append(sepDouble).append("\n\n\n")

    return sb.toString()
}

fun sendToThermalPrinterApp(
    context: Context,
    tx: CashierTransaction,
    namaUsaha: String,
    paperWidthChars: Int = 32
) {
    val receiptText = generateTextReceipt(tx, namaUsaha, paperWidthChars)

    // Send raw text to RawBT or any Bluetooth/USB OTG Thermal printer service
    val rawBtIntent = android.content.Intent("ru.rawbt.printer.ACTION_PRINT").apply {
        putExtra(android.content.Intent.EXTRA_TEXT, receiptText)
        type = "text/plain"
    }

    try {
        if (rawBtIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(rawBtIntent)
            return
        }
    } catch (_: Exception) {}

    // Fallback: Standard Intent Share / Send to Printer App
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, receiptText)
    }

    val chooser = android.content.Intent.createChooser(intent, "Cetak Struk POS Thermal (Bluetooth / USB OTG)")
    context.startActivity(chooser)
}
