package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CatatanKeuanganItem
import com.example.ui.MainViewModel
import com.example.ui.components.CustomDatePickerDialog
import com.example.util.CatatanKeuanganHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WARNA_MASUK = Color(0xFF16A34A)
private val WARNA_KELUAR = Color(0xFFDC2626)

private fun kategoriIcon(kategori: String): ImageVector = when (kategori) {
    CatatanKeuanganHelper.KATEGORI_MAKAN -> Icons.Default.Restaurant
    CatatanKeuanganHelper.KATEGORI_TRANSPORTASI -> Icons.Default.DirectionsBus
    CatatanKeuanganHelper.KATEGORI_TAGIHAN -> Icons.Default.Receipt
    CatatanKeuanganHelper.KATEGORI_BELANJA -> Icons.Default.Movie
    CatatanKeuanganHelper.KATEGORI_PEMASUKAN -> Icons.Default.CheckCircle
    else -> Icons.Default.Category
}

private fun rupiah(nilai: Double) = CatatanKeuanganHelper.formatRupiah(nilai)

@Composable
fun NoteKeuanganScreen(viewModel: MainViewModel) {
    val semua by viewModel.catatanKeuangan.collectAsState()

    var search by remember { mutableStateOf("") }
    var filterSumber by remember { mutableStateOf("Semua") }
    var filterJenis by remember { mutableStateOf("Semua") }
    var filterKategori by remember { mutableStateOf("Semua") }

    var tampilForm by remember { mutableStateOf(false) }
    var sedangEdit by remember { mutableStateOf<CatatanKeuanganItem?>(null) }
    var sumberAwalForm by remember { mutableStateOf("") }
    var hapusItem by remember { mutableStateOf<CatatanKeuanganItem?>(null) }

    val sumberTersedia = remember(semua) {
        (CatatanKeuanganHelper.SUMBER_DANA_UMUM + semua.map { it.sumberDana })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
    }

    val tersaring = remember(semua, search, filterSumber, filterJenis, filterKategori) {
        semua.filter { item ->
            val cocokCari = search.isBlank() ||
                item.keterangan.contains(search, ignoreCase = true) ||
                item.kategori.contains(search, ignoreCase = true) ||
                item.sumberDana.contains(search, ignoreCase = true)
            val cocokSumber = filterSumber == "Semua" || item.sumberDana.equals(filterSumber, ignoreCase = true)
            val cocokJenis = filterJenis == "Semua" || item.jenis == filterJenis.lowercase(Locale.US)
            val cocokKategori = filterKategori == "Semua" || item.kategori.equals(filterKategori, ignoreCase = true)
            cocokCari && cocokSumber && cocokJenis && cocokKategori
        }
    }

    val totalMasuk = remember(tersaring) { tersaring.filter { it.jenis == "masuk" }.sumOf { it.jumlah } }
    val totalKeluar = remember(tersaring) { tersaring.filter { it.jenis == "keluar" }.sumOf { it.jumlah } }
    val kelompokTanggal = remember(tersaring) {
        tersaring.groupBy { it.tanggal }.toSortedMap(compareByDescending { it })
    }

    Box(modifier = Modifier.fillMaxSize().testTag("note_keuangan_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RingkasanKeuanganCard(totalMasuk, totalKeluar, tersaring.size)
            }
            item {
                FilterKeuanganSection(
                    search = search,
                    onSearchChange = { search = it },
                    filterSumber = filterSumber,
                    onFilterSumberChange = { filterSumber = it },
                    filterJenis = filterJenis,
                    onFilterJenisChange = { filterJenis = it },
                    filterKategori = filterKategori,
                    onFilterKategoriChange = { filterKategori = it },
                    sumberTersedia = sumberTersedia
                )
            }

            if (tersaring.isEmpty()) {
                item {
                    KartuKosong(
                        judul = "Belum ada catatan",
                        pesan = "Tambahkan transaksi m-banking atau e-wallet dengan tombol + di kanan bawah."
                    )
                }
            } else {
                kelompokTanggal.forEach { (tanggal, daftar) ->
                    item(key = "tanggal-$tanggal") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = CatatanKeuanganHelper.formatTanggalPanjang(tanggal),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val masukHari = daftar.filter { it.jenis == "masuk" }.sumOf { it.jumlah }
                            val keluarHari = daftar.filter { it.jenis == "keluar" }.sumOf { it.jumlah }
                            Text(
                                text = "Masuk ${rupiah(masukHari)}  •  Keluar ${rupiah(keluarHari)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                    items(items = daftar, key = { "trx-${it.id}" }) { item ->
                        KartuTransaksi(
                            item = item,
                            onClick = {
                                sedangEdit = item
                                sumberAwalForm = item.sumberDana
                                tampilForm = true
                            },
                            onHapus = { hapusItem = item }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }

        FloatingActionButton(
            onClick = {
                sedangEdit = null
                sumberAwalForm = sumberTersedia.firstOrNull() ?: ""
                tampilForm = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("tambah_note_keuangan")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah note keuangan")
        }
    }

    if (tampilForm) {
        FormCatatanKeuanganDialog(
            awal = sedangEdit,
            sumberDefault = sumberAwalForm,
            sumberPilihan = sumberTersedia,
            onDismiss = { tampilForm = false },
            onSimpan = { data ->
                val lama = sedangEdit
                if (lama == null) {
                    viewModel.addCatatanKeuangan(data)
                } else {
                    viewModel.updateCatatanKeuangan(data.copy(id = lama.id))
                }
                tampilForm = false
            }
        )
    }

    hapusItem?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { hapusItem = null },
            title = { Text("Hapus Catatan?") },
            text = { Text("${item.keterangan} sebesar ${rupiah(item.jumlah)} akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCatatanKeuangan(item)
                    hapusItem = null
                }) { Text("Hapus", color = WARNA_KELUAR) }
            },
            dismissButton = {
                TextButton(onClick = { hapusItem = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun RingkasanKeuanganCard(totalMasuk: Double, totalKeluar: Double, jumlahCatatan: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saldo Tercatat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = rupiah(totalMasuk - totalKeluar),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatKecil(
                    judul = "Masuk",
                    nilai = rupiah(totalMasuk),
                    warna = WARNA_MASUK,
                    modifier = Modifier.weight(1f)
                )
                StatKecil(
                    judul = "Keluar",
                    nilai = rupiah(totalKeluar),
                    warna = WARNA_KELUAR,
                    modifier = Modifier.weight(1f)
                )
                StatKecil(
                    judul = "Catatan",
                    nilai = "$jumlahCatatan",
                    warna = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatKecil(judul: String, nilai: String, warna: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(judul, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        Text(nilai, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = warna, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FilterKeuanganSection(
    search: String,
    onSearchChange: (String) -> Unit,
    filterSumber: String,
    onFilterSumberChange: (String) -> Unit,
    filterJenis: String,
    onFilterJenisChange: (String) -> Unit,
    filterKategori: String,
    onFilterKategoriChange: (String) -> Unit,
    sumberTersedia: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Cari keterangan, kategori, sumber dana...", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        DropdownSaring(
            ikon = Icons.Default.SwapVert,
            deskripsi = "Filter jenis transaksi",
            nilai = filterJenis,
            pilihan = listOf("Semua", "Masuk", "Keluar"),
            onPilih = onFilterJenisChange
        )
        DropdownSaring(
            ikon = Icons.Default.AccountBalanceWallet,
            deskripsi = "Filter sumber dana",
            nilai = filterSumber,
            pilihan = listOf("Semua") + sumberTersedia,
            onPilih = onFilterSumberChange
        )
        DropdownSaring(
            ikon = Icons.Default.Category,
            deskripsi = "Filter kategori",
            nilai = filterKategori,
            pilihan = listOf("Semua") + CatatanKeuanganHelper.KATEGORI_PENGELUARAN,
            onPilih = onFilterKategoriChange
        )
    }
}

@Composable
private fun DropdownSaring(
    ikon: ImageVector,
    deskripsi: String,
    nilai: String,
    pilihan: List<String>,
    onPilih: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val aktif = nilai != "Semua"
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (aktif) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (aktif) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .height(56.dp)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = ikon,
                    contentDescription = deskripsi,
                    tint = if (aktif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (aktif) {
                    Text(
                        text = nilai,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 64.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            pilihan.forEach { opsi ->
                DropdownMenuItem(
                    text = { Text(opsi, fontSize = 12.sp) },
                    onClick = {
                        onPilih(opsi)
                        expanded = false
                    },
                    trailingIcon = {
                        if (opsi.equals(nilai, ignoreCase = true)) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KartuTransaksi(
    item: CatatanKeuanganItem,
    onClick: () -> Unit,
    onHapus: () -> Unit
) {
    val masuk = item.jenis == "masuk"
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = (if (masuk) WARNA_MASUK else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = kategoriIcon(item.kategori),
                    contentDescription = null,
                    tint = if (masuk) WARNA_MASUK else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.keterangan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.kategori} • ${item.sumberDana}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (masuk) "+ " else "- ") + rupiah(item.jumlah),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (masuk) WARNA_MASUK else WARNA_KELUAR
                )
                Row {
                    IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Ubah", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onHapus, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun KartuKosong(judul: String, pesan: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(judul, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                pesan,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun FormCatatanKeuanganDialog(
    awal: CatatanKeuanganItem?,
    sumberDefault: String,
    sumberPilihan: List<String>,
    onDismiss: () -> Unit,
    onSimpan: (CatatanKeuanganItem) -> Unit
) {
    var jenis by remember { mutableStateOf(awal?.jenis ?: "keluar") }
    var tanggal by remember { mutableStateOf(awal?.tanggal ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var keterangan by remember { mutableStateOf(awal?.keterangan ?: "") }
    var kategori by remember { mutableStateOf(awal?.kategori ?: CatatanKeuanganHelper.KATEGORI_LAINNYA) }
    var kategoriManual by remember { mutableStateOf(awal != null) }
    var sumber by remember { mutableStateOf(awal?.sumberDana ?: sumberDefault) }
    var jumlah by remember { mutableStateOf(awal?.jumlah?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var tampilTanggal by remember { mutableStateOf(false) }
    var tampilSumber by remember { mutableStateOf(false) }

    val saran = remember(keterangan, jenis) {
        if (jenis == "keluar") CatatanKeuanganHelper.deteksiKategori(keterangan) else null
    }
    LaunchedEffect(saran, jenis) {
        if (jenis == "keluar" && saran != null && !kategoriManual) {
            kategori = saran
        }
    }
    LaunchedEffect(jenis) {
        if (jenis == "masuk") {
            kategori = CatatanKeuanganHelper.KATEGORI_PEMASUKAN
        } else if (kategori == CatatanKeuanganHelper.KATEGORI_PEMASUKAN) {
            kategori = saran ?: CatatanKeuanganHelper.KATEGORI_LAINNYA
            kategoriManual = false
        }
    }

    val nominal = jumlah.toDoubleOrNull() ?: 0.0
    val valid = keterangan.isNotBlank() && sumber.isNotBlank() && nominal > 0

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (awal == null) "Tambah Note Keuangan" else "Ubah Note Keuangan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = jenis == "keluar",
                        onClick = { jenis = "keluar" },
                        label = { Text("Keluar", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = jenis == "masuk",
                        onClick = { jenis = "masuk" },
                        label = { Text("Masuk", fontSize = 11.sp) }
                    )
                }

                Box {
                    OutlinedTextField(
                        value = tanggal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tanggal", fontSize = 12.sp) },
                        trailingIcon = {
                            IconButton(onClick = { tampilTanggal = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pilih tanggal", modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { tampilTanggal = true }
                    )
                }

                OutlinedTextField(
                    value = keterangan,
                    onValueChange = { keterangan = it },
                    label = { Text("Keterangan", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: Kopi Kenangan / Bensin Pertamax", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (jenis == "masuk") {
                    OutlinedTextField(
                        value = CatatanKeuanganHelper.KATEGORI_PEMASUKAN,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Kategori", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CatatanKeuanganHelper.KATEGORI_PENGELUARAN.forEach { pilihan ->
                                FilterChip(
                                    selected = kategori == pilihan,
                                    onClick = {
                                        kategori = pilihan
                                        kategoriManual = true
                                    },
                                    label = { Text(pilihan, fontSize = 10.sp) }
                                )
                            }
                        }
                        if (saran != null && saran == kategori && !kategoriManual) {
                            Text(
                                text = "Kategori otomatis dari keterangan: $saran",
                                fontSize = 9.sp,
                                color = WARNA_MASUK
                            )
                        }
                    }
                }

                Box {
                    OutlinedTextField(
                        value = sumber,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sumber Dana", fontSize = 12.sp) },
                        placeholder = { Text("BCA, Mandiri, GoPay...", fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih sumber dana") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { tampilSumber = true }
                    )
                }

                OutlinedTextField(
                    value = jumlah,
                    onValueChange = { masukan -> jumlah = masukan.filter { it.isDigit() } },
                    label = { Text("Jumlah (Rp)", fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (nominal > 0) {
                    Text("Nominal: ${rupiah(nominal)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Batal") }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            onSimpan(
                                CatatanKeuanganItem(
                                    tanggal = tanggal,
                                    keterangan = keterangan.trim(),
                                    kategori = if (jenis == "masuk") CatatanKeuanganHelper.KATEGORI_PEMASUKAN else kategori,
                                    sumberDana = sumber.trim(),
                                    jenis = jenis,
                                    jumlah = nominal
                                )
                            )
                        },
                        enabled = valid
                    ) {
                        Text(if (awal == null) "Simpan" else "Perbarui", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (tampilTanggal) {
        CustomDatePickerDialog(
            initialDateStr = tanggal,
            onDismissRequest = { tampilTanggal = false },
            onDateSelected = { dipilih ->
                tanggal = dipilih
                tampilTanggal = false
            }
        )
    }

    if (tampilSumber) {
        PilihSumberDanaDialog(
            sumberPilihan = sumberPilihan,
            terpilih = sumber,
            onDismiss = { tampilSumber = false },
            onPilih = {
                sumber = it
                tampilSumber = false
            }
        )
    }
}

@Composable
private fun PilihSumberDanaDialog(
    sumberPilihan: List<String>,
    terpilih: String,
    onDismiss: () -> Unit,
    onPilih: (String) -> Unit
) {
    var sumberLain by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Pilih Sumber Dana", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    sumberPilihan.forEach { sumber ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPilih(sumber) }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sumber, fontSize = 13.sp)
                            if (sumber.equals(terpilih, ignoreCase = true)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = sumberLain,
                    onValueChange = { sumberLain = it },
                    label = { Text("Sumber lain", fontSize = 11.sp) },
                    placeholder = { Text("Mis. SeaBank, Jenius", fontSize = 11.sp) },
                    trailingIcon = {
                        TextButton(onClick = { if (sumberLain.isNotBlank()) onPilih(sumberLain.trim()) }) {
                            Text("Pakai", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
            }
        }
    }
}
