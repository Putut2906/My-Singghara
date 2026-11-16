package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AgendaItem
import com.example.network.HolidayClient
import com.example.ui.MainViewModel
import com.example.util.CalendarExportHelper
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CalendarMonthScreen(
    viewModel: MainViewModel,
    initialMonth: Int,
    initialYear: Int,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agendaItems by viewModel.agendaItems.collectAsState()
    val apiHolidays by viewModel.apiHolidays.collectAsState()

    var viewCalendar by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                if (initialYear > 0) set(Calendar.YEAR, initialYear)
                if (initialMonth in 1..12) set(Calendar.MONTH, initialMonth - 1)
            }
        )
    }

    var showAddForm by remember { mutableStateOf(false) }
    var agendaDay by remember { mutableIntStateOf(1) }
    var agendaTitle by remember { mutableStateOf("") }
    var agendaDesc by remember { mutableStateOf("") }
    var agendaType by remember { mutableStateOf("agenda") }
    var exportScope by remember { mutableStateOf("12_MONTHS") }
    var exportFormat by remember { mutableStateOf("PDF") }
    var paperType by remember { mutableStateOf("Art Paper (120\u2013150 gsm)") }
    var isExporting by remember { mutableStateOf(false) }
    var agendaToDelete by remember { mutableStateOf<AgendaItem?>(null) }

    val calendarMonthName = viewCalendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("id", "ID")) ?: ""
    val calendarYear = viewCalendar.get(Calendar.YEAR)
    val currentMonthNumber = viewCalendar.get(Calendar.MONTH) + 1
    val totalDaysInCurrentMonth = viewCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    val activeMonthAgendas = remember(agendaItems, calendarYear, currentMonthNumber) {
        agendaItems.filter {
            val dateParts = it.tanggal.split("-")
            if (dateParts.size == 3) {
                dateParts[0].toIntOrNull() == calendarYear && dateParts[1].toIntOrNull() == currentMonthNumber
            } else false
        }
    }
    val activeMonthHolidays = remember(apiHolidays, calendarYear, currentMonthNumber) {
        apiHolidays.filter { (key, _) ->
            val dateParts = key.split("-")
            if (dateParts.size == 3) {
                dateParts[0].toIntOrNull() == calendarYear && dateParts[1].toIntOrNull() == currentMonthNumber
            } else false
        }.toList()
    }
    val activeMonthObservances = (1..totalDaysInCurrentMonth).mapNotNull { d ->
        val obs = HolidayClient.getObservanceForDate(currentMonthNumber, d)
        if (obs != null) Pair(d, obs) else null
    }

    // Tombol back OS Android kembali ke Home.
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // SECTION: NAVIGASI BULAN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val newCal = viewCalendar.clone() as Calendar
                newCal.add(Calendar.MONTH, -1)
                viewCalendar = newCal
            }) {
                Icon(Icons.Default.ChevronLeft, "Bulan Sebelumnya", tint = cs.primary)
            }
            Text(
                text = "Kalender $calendarMonthName $calendarYear",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = cs.primary,
                textAlign = TextAlign.Center
            )
            IconButton(onClick = {
                val newCal = viewCalendar.clone() as Calendar
                newCal.add(Calendar.MONTH, 1)
                viewCalendar = newCal
            }) {
                Icon(Icons.Default.ChevronRight, "Bulan Berikutnya", tint = cs.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION: FORM TAMBAH KEGIATAN
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
            colors = CardDefaults.cardColors(containerColor = cs.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tambah Kegiatan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = cs.onSurface
                        )
                        Text(
                            text = "Tambahkan agenda untuk bulan $calendarMonthName $calendarYear",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showAddForm = !showAddForm },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(cs.primaryContainer)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Tambah Agenda",
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (showAddForm) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tanggal:",
                            fontSize = 12.sp,
                            color = cs.onSurface
                        )
                        OutlinedTextField(
                            value = agendaDay.toString(),
                            onValueChange = { str ->
                                val v = str.filter { it.isDigit() }.toIntOrNull()
                                if (v != null && v in 1..totalDaysInCurrentMonth) {
                                    agendaDay = v
                                } else if (str.isEmpty()) {
                                    agendaDay = 1
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.width(80.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Text(
                            text = "$calendarMonthName $calendarYear",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = agendaTitle,
                        onValueChange = { agendaTitle = it },
                        label = { Text("Nama kegiatan") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = agendaDesc,
                        onValueChange = { agendaDesc = it },
                        label = { Text("Detail kegiatan (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showAddForm = false
                            agendaTitle = ""
                            agendaDesc = ""
                        }) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (agendaTitle.isNotBlank()) {
                                    val dateKey = String.format("%04d-%02d-%02d", calendarYear, currentMonthNumber, agendaDay)
                                    val savedTitle = agendaTitle
                                    viewModel.addAgendaItem(
                                        AgendaItem(
                                            tanggal = dateKey,
                                            judul = agendaTitle,
                                            detail = agendaDesc,
                                            tipe = agendaType
                                        )
                                    )
                                    viewModel.showToastPopup("Kegiatan \"$savedTitle\" berhasil disimpan!")
                                    agendaTitle = ""
                                    agendaDesc = ""
                                    showAddForm = false
                                } else {
                                    viewModel.showToastPopup("Mohon isi Nama kegiatan!")
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION: UNDUH & CETAK KALENDER
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Unduh Kalender",
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unduh & Cetak Kalender",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = cs.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Periode Cetak (12 Bulan / 1 Bulan)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Periode Cetak:", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            Pair("12_MONTHS", "12 Bulan (1 Tahun)"),
                            Pair("1_MONTH", "1 Bulan ($calendarMonthName)")
                        ).forEach { (scopeKey, label) ->
                            val isSelected = exportScope == scopeKey
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) cs.primaryContainer else cs.surface,
                                border = BorderStroke(1.dp, if (isSelected) cs.primary else cs.outlineVariant),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { exportScope = scopeKey }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) cs.onPrimaryContainer else cs.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Format Options (PDF, PNG, JPG)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Format:", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    listOf("PDF", "PNG", "JPG").forEach { fmt ->
                        val isSelected = exportFormat == fmt
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) cs.primary else cs.surface,
                            border = BorderStroke(1.dp, if (isSelected) cs.primary else cs.outlineVariant),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { exportFormat = fmt }
                        ) {
                            Text(
                                text = fmt,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) cs.onPrimary else cs.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bahan & Bentuk Cetak (Art Paper / Art Carton / Kartu ATM)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Bahan & Bentuk Cetak:", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Art Paper (120\u2013150 gsm)", "Art Carton (190\u2013260 gsm)").forEach { paper ->
                                val isSelected = paperType == paper
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) cs.secondaryContainer else cs.surface,
                                    border = BorderStroke(1.dp, if (isSelected) cs.secondary else cs.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { paperType = paper }
                                ) {
                                    Text(
                                        text = paper,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) cs.onSecondaryContainer else cs.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        val isAtmCard = paperType.contains("ATM", true) || paperType.contains("Dompet", true)
                        val atmPaperValue = "Kartu ATM / IC Card (Ukuran Dompet 85.6 x 54 mm)"
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAtmCard) Color(0xFF0F766E).copy(alpha = 0.15f) else cs.surface,
                            border = BorderStroke(1.dp, if (isAtmCard) Color(0xFF0F766E) else cs.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { paperType = atmPaperValue }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = if (isAtmCard) Color(0xFF0F766E) else cs.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kartu ATM / IC Card (Ukuran Dompet 85.6 x 54 mm)",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isAtmCard) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAtmCard) Color(0xFF0F766E) else cs.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val isAtmSelected = paperType.contains("ATM", true) || paperType.contains("Dompet", true)
                val scopeDesc = when {
                    isAtmSelected && exportScope == "12_MONTHS" ->
                        "Format Kartu ATM Dompet (85.6 x 54 mm) \u2022 Muka Depan (Semester I) & Belakang (Semester II)"
                    isAtmSelected ->
                        "Format Kartu ATM Dompet (85.6 x 54 mm) \u2022 Bulan $calendarMonthName $calendarYear Lengkap Chip IC"
                    exportScope == "12_MONTHS" && exportFormat.equals("PDF", true) ->
                        "12 Halaman Dokumen A4 (Januari \u2013 Desember) Lengkap"
                    exportScope == "12_MONTHS" ->
                        "Poster Dinding 12 Bulan Resolusi Tinggi (300 DPI)"
                    else ->
                        "1 Lembar Kalender $calendarMonthName $calendarYear"
                }
                Text(
                    text = "Keterangan: $scopeDesc",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.85f),
                    fontStyle = FontStyle.Italic
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isExporting) {
                            isExporting = true
                            coroutineScope.launch {
                                val result = CalendarExportHelper.exportCalendar(
                                    context = context,
                                    year = calendarYear,
                                    month = currentMonthNumber,
                                    hijriOffset = viewModel.hijriOffset,
                                    holidays = apiHolidays,
                                    agendas = agendaItems,
                                    format = exportFormat,
                                    paperType = paperType,
                                    scope = exportScope
                                )
                                isExporting = false
                                result.onSuccess { exportedFile ->
                                    val mime = if (exportFormat.equals("PDF", true)) "application/pdf"
                                    else if (exportFormat.equals("PNG", true)) "image/png" else "image/jpeg"
                                    val scopeLabel = if (exportScope == "12_MONTHS") "12 Bulan $calendarYear" else "$calendarMonthName $calendarYear"
                                    CalendarExportHelper.shareCalendarFile(
                                        context = context,
                                        file = exportedFile,
                                        mimeType = mime,
                                        title = "Unduh Kalender $scopeLabel"
                                    )
                                    viewModel.showToastPopup("Kalender $scopeLabel ($exportFormat) berhasil dibuat!")
                                }.onFailure { err ->
                                    viewModel.showToastPopup("Gagal membuat kalender: ${err.message}")
                                }
                            }
                        }
                    },
                    enabled = !isExporting,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = cs.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Memproses...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val btnLabel = if (exportScope == "12_MONTHS") "Unduh Kalender 12 Bulan ($exportFormat)" else "Unduh Kalender 1 Bulan ($exportFormat)"
                        Text(btnLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION: DAFTAR ACARA / HARI LIBUR / PERINGATAN
        Text(
            text = "Acara & Hari Besar $calendarMonthName $calendarYear",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (activeMonthAgendas.isEmpty() && activeMonthHolidays.isEmpty() && activeMonthObservances.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tidak ada acara atau hari besar di bulan ini.",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeMonthHolidays.forEach { (date, name) ->
                    val d = date.split("-").lastOrNull()?.toIntOrNull() ?: 1
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.toString(), fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                }
                                Text("Hari Libur Nasional (SKB 3 Menteri)", fontSize = 10.sp, color = Color(0xFFEF4444).copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                activeMonthObservances.forEach { (d, name) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                }
                                Text("Hari Peringatan / Hari Besar", fontSize = 10.sp, color = Color(0xFF10B981).copy(alpha = 0.7f))
                            }
                        }
                    }
                }

                activeMonthAgendas.forEach { agenda ->
                    val d = agenda.tanggal.split("-").lastOrNull()?.toIntOrNull() ?: 1
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(cs.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.toString(), fontWeight = FontWeight.Bold, color = cs.primary, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(agenda.judul, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                                }
                                if (agenda.detail.isNotEmpty()) {
                                    Text(agenda.detail, fontSize = 10.sp, color = cs.onSurfaceVariant)
                                }
                            }
                            IconButton(
                                onClick = { agendaToDelete = agenda },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = cs.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // DIALOG: KONFIRMASI HAPUS AGENDA
    agendaToDelete?.let { agenda ->
        AlertDialog(
            onDismissRequest = { agendaToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = cs.error
                )
            },
            title = { Text(text = "Hapus Agenda?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus agenda \"" + agenda.judul + "\"? Tindakan ini tidak dapat dibatalkan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedTitle = agenda.judul
                        viewModel.deleteAgendaItem(agenda)
                        agendaToDelete = null
                        viewModel.showToastPopup("Agenda \"$deletedTitle\" berhasil dihapus.")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = cs.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { agendaToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
