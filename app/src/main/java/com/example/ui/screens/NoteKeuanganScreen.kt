package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.CatatanKeuanganItem
import com.example.ui.MainViewModel
import com.example.ui.components.CustomDatePickerDialog
import com.example.util.CatatanKeuanganHelper
import kotlin.math.atan2
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WARNA_MASUK = Color(0xFF16A34A)
private val WARNA_KELUAR = Color(0xFFDC2626)

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

    Box(modifier = Modifier.fillMaxSize().testTag("note_keuangan_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RingkasanKeuanganCard(tersaring)
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
                item {
                    CatatanKeuanganTable(
                        items = tersaring,
                        onUbah = { item ->
                            sedangEdit = item
                            sumberAwalForm = item.sumberDana
                            tampilForm = true
                        },
                        onHapus = { hapusItem = it },
                        limit = 5
                    )
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
private fun RingkasanKeuanganCard(items: List<CatatanKeuanganItem>) {
    val totalMasuk = remember(items) { items.filter { it.jenis == "masuk" }.sumOf { it.jumlah } }
    val totalKeluar = remember(items) { items.filter { it.jenis == "keluar" }.sumOf { it.jumlah } }
    val countMasuk = remember(items) { items.count { it.jenis == "masuk" } }
    val countKeluar = remember(items) { items.count { it.jenis == "keluar" } }
    val perKategori = remember(items) { items.groupingBy { it.kategori }.eachCount() }
    val saldo = totalMasuk - totalKeluar
    val jumlahCatatan = items.size

    var selectedCategory by remember { mutableStateOf("saldo") }
    var showDetailPopup by remember { mutableStateOf(false) }

    val warnaSaldo = MaterialTheme.colorScheme.primary
    val warnaCatatan = Color(0xFF64748B)
    val context = LocalContext.current
    val modeTerang = !isSystemInDarkTheme()

    val valSaldo = saldo.coerceAtLeast(0.0)
    val sum = valSaldo + totalMasuk + totalKeluar
    val angleSaldo = if (sum > 0) (valSaldo / sum * 360f).toFloat() else 120f
    val angleMasuk = if (sum > 0) (totalMasuk / sum * 360f).toFloat() else 120f
    val angleKeluar = if (sum > 0) (totalKeluar / sum * 360f).toFloat() else 120f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (modeTerang) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (modeTerang) 5.dp else 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Saldo Tercatat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(
                            onClick = { printCatatanKeuangan(context, items) },
                            modifier = Modifier.size(24.dp).testTag("print_note_keuangan")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Cetak Note Keuangan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = rupiah(saldo),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.size(125.dp), contentAlignment = Alignment.Center) {
                    val strokeWidthSaldo = if (selectedCategory == "saldo") 14.dp else 10.dp
                    val strokeWidthMasuk = if (selectedCategory == "masuk") 14.dp else 10.dp
                    val strokeWidthKeluar = if (selectedCategory == "keluar") 14.dp else 10.dp

                    Canvas(
                        modifier = Modifier
                            .size(105.dp)
                            .pointerInput(angleSaldo, angleMasuk, angleKeluar) {
                                detectTapGestures { offset ->
                                    val dx = offset.x - size.width / 2f
                                    val dy = offset.y - size.height / 2f
                                    val distance = sqrt(dx * dx + dy * dy)

                                    val outerRadius = size.width / 2f
                                    val innerRadius = outerRadius - 20.dp.toPx()

                                    if (distance in innerRadius..outerRadius) {
                                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        angle = (angle + 90f + 360f) % 360f

                                        if (angleSaldo > 0f && angle in 0f..angleSaldo) {
                                            selectedCategory = "saldo"
                                            showDetailPopup = true
                                        } else if (angleMasuk > 0f && angle in angleSaldo..(angleSaldo + angleMasuk)) {
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
                        if (angleSaldo > 0f) {
                            drawArc(
                                color = warnaSaldo,
                                startAngle = -90f,
                                sweepAngle = angleSaldo,
                                useCenter = false,
                                style = Stroke(width = strokeWidthSaldo.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        if (angleMasuk > 0f) {
                            drawArc(
                                color = WARNA_MASUK,
                                startAngle = -90f + angleSaldo,
                                sweepAngle = angleMasuk,
                                useCenter = false,
                                style = Stroke(width = strokeWidthMasuk.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        if (angleKeluar > 0f) {
                            drawArc(
                                color = WARNA_KELUAR,
                                startAngle = -90f + angleSaldo + angleMasuk,
                                sweepAngle = angleKeluar,
                                useCenter = false,
                                style = Stroke(width = strokeWidthKeluar.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        if (selectedCategory != "catatan") {
                            val nominalValue = when (selectedCategory) {
                                "masuk" -> totalMasuk
                                "keluar" -> totalKeluar
                                else -> saldo
                            }
                            val labelValue = when (selectedCategory) {
                                "masuk" -> "Masuk"
                                "keluar" -> "Keluar"
                                else -> "Saldo"
                            }
                            val curvedText = "$labelValue ${rupiah(nominalValue)}"

                            val textStartAngle = when (selectedCategory) {
                                "masuk" -> -90f + angleSaldo
                                "keluar" -> -90f + angleSaldo + angleMasuk
                                else -> -90f
                            }
                            val textSweepAngle = when (selectedCategory) {
                                "masuk" -> angleMasuk.coerceAtLeast(40f)
                                "keluar" -> angleKeluar.coerceAtLeast(40f)
                                else -> angleSaldo.coerceAtLeast(40f)
                            }
                            val textColor = when (selectedCategory) {
                                "masuk" -> Color(0xFF166534)
                                "keluar" -> Color(0xFF991B1B)
                                else -> warnaSaldo
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
                                    color = textColor.toArgb()
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
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val centerTitle = when (selectedCategory) {
                            "masuk" -> "Masuk"
                            "keluar" -> "Keluar"
                            "catatan" -> "Catatan"
                            else -> "Saldo"
                        }
                        val centerValue = when (selectedCategory) {
                            "masuk" -> rupiah(totalMasuk)
                            "keluar" -> rupiah(totalKeluar)
                            "catatan" -> "$jumlahCatatan"
                            else -> rupiah(saldo)
                        }
                        val centerColor = when (selectedCategory) {
                            "masuk" -> WARNA_MASUK
                            "keluar" -> WARNA_KELUAR
                            "catatan" -> warnaCatatan
                            else -> warnaSaldo
                        }

                        Text(
                            text = centerTitle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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

    if (showDetailPopup) {
        Dialog(onDismissRequest = { showDetailPopup = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val icon = when (selectedCategory) {
                        "masuk" -> Icons.Default.TrendingUp
                        "keluar" -> Icons.Default.TrendingDown
                        "catatan" -> Icons.Default.Receipt
                        else -> Icons.Default.AccountBalanceWallet
                    }
                    val themeColor = when (selectedCategory) {
                        "masuk" -> WARNA_MASUK
                        "keluar" -> WARNA_KELUAR
                        "catatan" -> warnaCatatan
                        else -> warnaSaldo
                    }
                    val title = when (selectedCategory) {
                        "masuk" -> "Total Masuk"
                        "keluar" -> "Total Keluar"
                        "catatan" -> "Jumlah Catatan"
                        else -> "Saldo Tercatat"
                    }
                    val amountText = when (selectedCategory) {
                        "masuk" -> rupiah(totalMasuk)
                        "keluar" -> rupiah(totalKeluar)
                        "catatan" -> "$jumlahCatatan catatan"
                        else -> rupiah(saldo)
                    }
                    val description = when (selectedCategory) {
                        "masuk" -> "Seluruh dana masuk yang berhasil dicatatkan. Terdiri dari akumulasi sebanyak $countMasuk transaksi uang masuk."
                        "keluar" -> "Seluruh pengeluaran yang pernah dicatatkan. Terdiri dari akumulasi sebanyak $countKeluar transaksi uang keluar."
                        "catatan" -> "Total $jumlahCatatan catatan yang tampil (mengikuti filter aktif): $countMasuk masuk dan $countKeluar keluar, tersebar di ${perKategori.size} kategori."
                        else -> "Selisih total masuk dikurangi total keluar dari $jumlahCatatan catatan yang tampil." +
                            if (saldo < 0) " Saldo negatif karena pengeluaran lebih besar daripada pemasukan." else ""
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(themeColor.copy(alpha = 0.12f)),
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

                    if (selectedCategory == "catatan" && perKategori.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        val maxCount = perKategori.values.maxOrNull() ?: 1
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            perKategori.entries.sortedByDescending { it.value }.take(5).forEach { (kategori, count) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = kategori,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(96.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(count.toFloat() / maxCount)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(themeColor)
                                        )
                                    }
                                    Text("$count", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showDetailPopup = false },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tutup", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
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
private fun CatatanKeuanganTable(
    items: List<CatatanKeuanganItem>,
    onUbah: (CatatanKeuanganItem) -> Unit,
    onHapus: (CatatanKeuanganItem) -> Unit,
    limit: Int = 5
) {
    val modeTerang = !isSystemInDarkTheme()
    val perHalaman = if (limit <= 0) items.size.coerceAtLeast(1) else limit
    val totalHalaman = ((items.size + perHalaman - 1) / perHalaman).coerceAtLeast(1)
    var halamanAktif by remember(items) { mutableStateOf(0) }
    val halaman = halamanAktif.coerceIn(0, totalHalaman - 1)
    val baris = items.drop(halaman * perHalaman).take(perHalaman)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (modeTerang) 4.dp else 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderSel("Tanggal", 92.dp)
                        HeaderSel("Keterangan", 168.dp)
                        HeaderSel("Jenis", 92.dp, Alignment.Center)
                        HeaderSel("Kategori", 112.dp)
                        HeaderSel("Sumber Dana", 104.dp)
                        HeaderSel("Jumlah", 112.dp, Alignment.CenterEnd)
                        HeaderSel("Aksi", 84.dp, Alignment.Center)
                    }

                    baris.forEach { item ->
                        val masuk = item.jenis == "masuk"
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BodySel(92.dp) {
                                Text(item.tanggal, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            BodySel(168.dp) {
                                Text(
                                    text = item.keterangan.ifBlank { "-" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BodySel(92.dp, Alignment.Center) {
                                Text(
                                    text = if (masuk) "Masuk" else "Keluar",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (masuk) WARNA_MASUK else WARNA_KELUAR,
                                    modifier = Modifier
                                        .background(
                                            (if (masuk) WARNA_MASUK else WARNA_KELUAR).copy(alpha = 0.1f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            BodySel(112.dp) {
                                Text(
                                    text = item.kategori,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BodySel(104.dp) {
                                Text(
                                    text = item.sumberDana,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BodySel(112.dp, Alignment.CenterEnd) {
                                Text(
                                    text = (if (masuk) "+ " else "- ") + rupiah(item.jumlah),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (masuk) WARNA_MASUK else WARNA_KELUAR
                                )
                            }
                            BodySel(84.dp, Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onUbah(item) }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Ubah",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(onClick = { onHapus(item) }, modifier = Modifier.size(28.dp)) {
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

        if (items.size > perHalaman) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (halaman > 0) halamanAktif = halaman - 1 },
                    enabled = halaman > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sebelumnya", fontSize = 12.sp)
                }
                Text(
                    text = "Halaman ${halaman + 1} dari $totalHalaman",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = { if (halaman < totalHalaman - 1) halamanAktif = halaman + 1 },
                    enabled = halaman < totalHalaman - 1
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
private fun HeaderSel(teks: String, lebar: Dp, rata: Alignment = Alignment.CenterStart) {
    Box(
        modifier = Modifier
            .width(lebar)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = rata
    ) {
        Text(
            text = teks,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun BodySel(lebar: Dp, rata: Alignment = Alignment.CenterStart, isi: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(lebar)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = rata
    ) {
        isi()
    }
}

@Composable
private fun KartuKosong(judul: String, pesan: String) {
    val modeTerang = !isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (modeTerang) 3.dp else 0.dp)
    ) {
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

private fun escapeHtml(teks: String): String = teks
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun printCatatanKeuangan(context: Context, items: List<CatatanKeuanganItem>) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val jobName = "Note Keuangan - My Singghara"

    val totalMasuk = items.filter { it.jenis == "masuk" }.sumOf { it.jumlah }
    val totalKeluar = items.filter { it.jenis == "keluar" }.sumOf { it.jumlah }
    val saldo = totalMasuk - totalKeluar

    val html = StringBuilder()
    html.append("""
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
                table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 10px; }
                th, td { border: 1px solid #ddd; padding: 6px; text-align: left; }
                th { background-color: #f7f7f7; font-weight: bold; }
                .text-right { text-align: right; }
                .text-center { text-align: center; }
                .total-row { font-weight: bold; background-color: #fafafa; }
                .badge-masuk { color: green; font-weight: bold; background-color: #e6fced; padding: 2px 6px; border-radius: 4px; display: inline-block; }
                .badge-keluar { color: red; font-weight: bold; background-color: #ffebe9; padding: 2px 6px; border-radius: 4px; display: inline-block; }
            </style>
        </head>
        <body>
            <h1>Note Keuangan - My Singghara</h1>
            <p class="meta-time">Dicetak pada: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}</p>

            <div class="summary-box">
                <h3>Ringkasan Keuangan</h3>
                <div class="grid">
                    <div class="grid-col">
                        <p>Saldo Tercatat: <strong>${rupiah(saldo)}</strong></p>
                        <p>Jumlah Catatan: <strong>${items.size}</strong></p>
                    </div>
                    <div class="grid-col">
                        <p>Total Uang Masuk: <span style="color: green; font-weight: bold;">${rupiah(totalMasuk)}</span></p>
                        <p>Total Uang Keluar: <span style="color: red; font-weight: bold;">${rupiah(totalKeluar)}</span></p>
                    </div>
                </div>
            </div>

            <table>
                <thead>
                    <tr>
                        <th style="width: 4%;">#</th>
                        <th style="width: 12%;">Tanggal</th>
                        <th style="width: 23%;">Keterangan</th>
                        <th style="width: 9%;" class="text-center">Jenis</th>
                        <th style="width: 14%;">Kategori</th>
                        <th style="width: 14%;">Sumber Dana</th>
                        <th style="width: 16%;" class="text-right">Jumlah</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())

    items.forEachIndexed { index, item ->
        val masuk = item.jenis == "masuk"
        val badgeClass = if (masuk) "badge-masuk" else "badge-keluar"
        val warnaNominal = if (masuk) "green" else "red"
        val tanda = if (masuk) "+" else "-"
        html.append("""
            <tr>
                <td>${index + 1}</td>
                <td>${escapeHtml(item.tanggal)}</td>
                <td>${escapeHtml(item.keterangan.ifBlank { "-" })}</td>
                <td class="text-center"><span class="$badgeClass">${if (masuk) "Masuk" else "Keluar"}</span></td>
                <td>${escapeHtml(item.kategori)}</td>
                <td>${escapeHtml(item.sumberDana.ifBlank { "-" })}</td>
                <td class="text-right" style="font-weight: bold; color: $warnaNominal;">$tanda ${rupiah(item.jumlah)}</td>
            </tr>
        """.trimIndent())
    }

    html.append("""
                    <tr class="total-row">
                        <td colspan="6" class="text-right">Total Uang Masuk:</td>
                        <td class="text-right" style="color: green;">${rupiah(totalMasuk)}</td>
                    </tr>
                    <tr class="total-row">
                        <td colspan="6" class="text-right">Total Uang Keluar:</td>
                        <td class="text-right" style="color: red;">${rupiah(totalKeluar)}</td>
                    </tr>
                    <tr class="total-row" style="background-color: #eef5ff;">
                        <td colspan="6" class="text-right" style="color: #0056b3;">Saldo Tercatat:</td>
                        <td class="text-right" style="color: #0056b3;">${rupiah(saldo)}</td>
                    </tr>
                </tbody>
            </table>
        </body>
        </html>
    """.trimIndent())

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
        }
    }
    webView.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null)
}
