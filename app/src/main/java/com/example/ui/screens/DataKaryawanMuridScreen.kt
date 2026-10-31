package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.AnggotaItem
import com.example.data.RiwayatPresensi
import com.example.ui.MainViewModel
import com.example.ui.components.CustomDatePickerDialog
import com.example.utils.IdCardBitmapGenerator
import com.example.utils.QrCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataKaryawanMuridScreen(
    viewModel: MainViewModel,
    isFullTableOnly: Boolean = false,
    onBack: (() -> Unit)? = null,
    onOpenFullTable: (() -> Unit)? = null
) {
    // Handle Android OS system back button
    BackHandler {
        if (onBack != null) onBack() else viewModel.currentRoute = "home"
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndCleanupExpiredAnggota()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allAnggota by viewModel.allAnggota.collectAsState()
    val allPresensi by viewModel.allPresensi.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("Semua") }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    // Dialogs state
    var showInputDialog by remember { mutableStateOf(false) }
    var editingAnggota by remember { mutableStateOf<AnggotaItem?>(null) }
    var previewingAnggota by remember { mutableStateOf<AnggotaItem?>(null) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showRiwayatPresensiDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureVersion by remember { mutableStateOf(0) }
    var deletingAnggota by remember { mutableStateOf<AnggotaItem?>(null) }
    var showToolbarOverflowMenu by remember { mutableStateOf(false) }

    // Check if deep linked from viewModel
    LaunchedEffect(viewModel.selectedAnggotaForIdCard) {
        if (viewModel.selectedAnggotaForIdCard != null) {
            previewingAnggota = viewModel.selectedAnggotaForIdCard
            viewModel.selectedAnggotaForIdCard = null
        }
    }

    val filterOptions = listOf("Semua", "Karyawan", "Murid", "Guru", "Staf", "Magang")

    val filteredList = remember(allAnggota, searchQuery, selectedFilterStatus) {
        allAnggota.filter { item ->
            val matchStatus = if (selectedFilterStatus == "Semua") true else {
                item.status.contains(selectedFilterStatus, ignoreCase = true)
            }
            val matchQuery = if (searchQuery.isBlank()) true else {
                item.nama.contains(searchQuery, ignoreCase = true) ||
                        item.nomorInduk.contains(searchQuery, ignoreCase = true) ||
                        item.divisiOrJurusan.contains(searchQuery, ignoreCase = true) ||
                        item.instansi.contains(searchQuery, ignoreCase = true)
            }
            matchStatus && matchQuery
        }
    }

    val totalKaryawan = remember(allAnggota) { allAnggota.count { it.status.contains("Karyawan", ignoreCase = true) } }
    val totalMurid = remember(allAnggota) { allAnggota.count { it.status.contains("Murid", ignoreCase = true) || it.status.contains("Siswa", ignoreCase = true) } }
    val totalGuru = remember(allAnggota) { allAnggota.count { it.status.contains("Guru", ignoreCase = true) || it.status.contains("Dosen", ignoreCase = true) || it.status.contains("Staf", ignoreCase = true) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 40.dp, top = 8.dp)
        ) {
            // Ringkasan Statistik Data (Hanya ditampilkan pada menu utama, tidak di halaman Tabel Penuh)
            if (!isFullTableOnly) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatMiniBox(
                                label = "Total",
                                count = allAnggota.size,
                                icon = Icons.Default.Groups,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniBox(
                                label = "Karyawan",
                                count = totalKaryawan,
                                icon = Icons.Default.Work,
                                color = Color(0xFF0F766E),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniBox(
                                label = "Murid/Siswa",
                                count = totalMurid,
                                icon = Icons.Default.School,
                                color = Color(0xFF4338CA),
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniBox(
                                label = "Guru/Staf",
                                count = totalGuru,
                                icon = Icons.Default.SupervisedUserCircle,
                                color = Color(0xFFB45309),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // FILTER & SEARCH TOOLBAR WITH ACTION ICONS
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Filter Icon Only (Opens Dropdown Menu)
                        Box {
                            IconButton(
                                onClick = { filterMenuExpanded = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_filter_status")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter Kategori",
                                        tint = if (selectedFilterStatus != "Semua") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    if (selectedFilterStatus != "Semua") {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false }
                            ) {
                                filterOptions.forEach { option ->
                                    val isSelected = selectedFilterStatus == option
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = option,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            selectedFilterStatus = option
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. Search Icon and Clean Borderless Input (No container box, no placeholder)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_search_karyawan")
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus pencarian",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Active filter badge if not "Semua"
                        if (selectedFilterStatus != "Semua") {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clickable { selectedFilterStatus = "Semua" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = selectedFilterStatus,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Reset filter",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // 3. Right Action Icons (Scan QR Code, Tanda Tangan Canvas, Riwayat, and +Profil)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            IconButton(
                                onClick = { showScannerDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_scan_presensi")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Presensi QR",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Ikon Pena Tanda Tangan ID Card
                            IconButton(
                                onClick = { showSignatureDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_signature_pen")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = "Tanda Tangan ID Card",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showRiwayatPresensiDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_riwayat_presensi")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Riwayat Presensi",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    editingAnggota = null
                                    showInputDialog = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_tambah_anggota")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Tambah Anggota",
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Action Bar: Icon Titik 3 (Menu Overflow)
                            Box {
                                IconButton(
                                    onClick = { showToolbarOverflowMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_toolbar_more_vert")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu Titik Tiga",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showToolbarOverflowMenu,
                                    onDismissRequest = { showToolbarOverflowMenu = false }
                                ) {
                                    if (onOpenFullTable != null && !isFullTableOnly) {
                                        DropdownMenuItem(
                                            text = { Text("Tabel Anggota Lengkap") },
                                            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                            onClick = {
                                                showToolbarOverflowMenu = false
                                                onOpenFullTable()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Riwayat Presensi") },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = {
                                            showToolbarOverflowMenu = false
                                            showRiwayatPresensiDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scan Presensi QR") },
                                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                        onClick = {
                                            showToolbarOverflowMenu = false
                                            showScannerDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Table Header Count & Status
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!isFullTableOnly) {
                                onOpenFullTable?.invoke()
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Tabel Anggota (${filteredList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isFullTableOnly && onOpenFullTable != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F766E).copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onOpenFullTable.invoke() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${filteredList.size} Data",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Buka Tabel Penuh",
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (!isFullTableOnly && onOpenFullTable != null) {
                        TextButton(
                            onClick = { onOpenFullTable.invoke() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Buka Halaman", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF0F766E))
                        }
                    } else if (allPresensi.isNotEmpty()) {
                        Text(
                            text = "${allPresensi.size} Presensi Terverifikasi",
                            fontSize = 12.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Member Table or Empty State
            if (filteredList.isEmpty()) {
                item {
                    EmptyMemberState(
                        isSearching = searchQuery.isNotBlank() || selectedFilterStatus != "Semua",
                        onAddClick = {
                            editingAnggota = null
                            showInputDialog = true
                        }
                    )
                }
            } else {
                item {
                    AnggotaTableView(
                        anggotaList = filteredList,
                        onViewIdCard = { previewingAnggota = it },
                        onEdit = {
                            editingAnggota = it
                            showInputDialog = true
                        },
                        onDelete = { deletingAnggota = it }
                    )
                }
            }
        }
    }

    // Input Popup Dialog
    if (showInputDialog) {
        AnggotaInputDialog(
            existingAnggota = editingAnggota,
            viewModel = viewModel,
            onDismiss = {
                showInputDialog = false
                editingAnggota = null
            },
            onSave = { savedItem ->
                if (editingAnggota != null) {
                    viewModel.updateAnggota(savedItem)
                    viewModel.showToastPopup("Data ${savedItem.nama} berhasil diperbarui")
                } else {
                    viewModel.saveAnggota(savedItem) { newId ->
                        viewModel.showToastPopup("Data berhasil disimpan & ID Card dibuat!")
                    }
                }
                showInputDialog = false
                editingAnggota = null
                // Open ID Card preview directly
                previewingAnggota = savedItem
            }
        )
    }

    // ID Card Preview Dialog
    if (previewingAnggota != null) {
        IdCardPreviewDialog(
            anggota = previewingAnggota!!,
            viewModel = viewModel,
            signatureVersion = signatureVersion,
            onDismiss = { previewingAnggota = null },
            onEdit = {
                editingAnggota = previewingAnggota
                previewingAnggota = null
                showInputDialog = true
            }
        )
    }

    // Tanda Tangan Canvas Dialog
    if (showSignatureDialog) {
        TandaTanganDialog(
            context = context,
            viewModel = viewModel,
            onDismiss = { showSignatureDialog = false },
            onSignatureUpdated = {
                signatureVersion++
            }
        )
    }

    // Attendance QR Scanner Dialog
    if (showScannerDialog) {
        AttendanceQrScannerDialog(
            allAnggota = allAnggota,
            onDismiss = { showScannerDialog = false },
            onRecordAttendance = { presensi ->
                viewModel.recordPresensi(presensi) {
                    viewModel.showToastPopup("Presensi ${presensi.nama} (${presensi.statusPresensi}) berhasil dicatat!")
                }
            }
        )
    }

    // Attendance History Dialog
    if (showRiwayatPresensiDialog) {
        RiwayatPresensiDialog(
            presensiList = allPresensi,
            onDismiss = { showRiwayatPresensiDialog = false },
            onDelete = { viewModel.deletePresensi(it) },
            onClearAll = { viewModel.deleteAllPresensi() }
        )
    }

    // Delete Confirmation Dialog
    if (deletingAnggota != null) {
        AlertDialog(
            onDismissRequest = { deletingAnggota = null },
            title = { Text("Hapus Data Anggota?") },
            text = { Text("Apakah Anda yakin ingin menghapus data dan ID Card untuk '${deletingAnggota?.nama}' (${deletingAnggota?.nomorInduk})?") },
            confirmButton = {
                Button(
                    onClick = {
                        deletingAnggota?.let { viewModel.deleteAnggota(it) }
                        deletingAnggota = null
                        viewModel.showToastPopup("Data anggota berhasil dihapus")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAnggota = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// SUB-COMPONENTS
// -------------------------------------------------------------

@Composable
fun StatMiniBox(
    label: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$count",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AnggotaTableView(
    anggotaList: List<AnggotaItem>,
    onViewIdCard: (AnggotaItem) -> Unit,
    onEdit: (AnggotaItem) -> Unit,
    onDelete: (AnggotaItem) -> Unit
) {
    val scrollState = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column(modifier = Modifier.widthIn(min = 680.dp)) {
                // Table Header
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "No", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(40.dp), color = MaterialTheme.colorScheme.primary)
                        Text(text = "Profil User (Detail, Pratinjau & Ekspor)", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(235.dp), color = MaterialTheme.colorScheme.primary)
                        Text(text = "NIP / NIM", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(120.dp), color = MaterialTheme.colorScheme.primary)
                        Text(text = "Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(110.dp), color = MaterialTheme.colorScheme.primary)
                        Text(text = "Divisi", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(120.dp), color = MaterialTheme.colorScheme.primary)
                        Text(text = "Aksi", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(100.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Table Rows
                anggotaList.forEachIndexed { index, item ->
                    val isMurid = item.status.contains("Murid", ignoreCase = true) || item.status.contains("Siswa", ignoreCase = true)
                    val isGuru = item.status.contains("Guru", ignoreCase = true) || item.status.contains("Dosen", ignoreCase = true)
                    val themeColor = when {
                        isMurid -> Color(0xFF4338CA)
                        isGuru -> Color(0xFFB45309)
                        else -> Color(0xFF0F766E)
                    }
                    val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)

                    Surface(
                        color = rowBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // No
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(40.dp)
                            )

                            // Profil User (Foto, Nama, Info) - Klik untuk Pop Up Detail (Fitur Pratinjau & Ekspor)
                            Row(
                                modifier = Modifier
                                    .width(235.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onViewIdCard(item) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("profil_user_${item.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(themeColor.copy(alpha = 0.12f))
                                        .border(1.dp, themeColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.fotoUri.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(Uri.parse(item.fotoUri))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Foto ${item.nama}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = if (item.nama.isNotBlank()) item.nama.first().uppercase() else "?",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeColor
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.nama,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Buka Detail, Pratinjau & Ekspor",
                                            tint = Color(0xFF0F766E),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Detail, Pratinjau & Ekspor",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0F766E)
                                        )
                                    }
                                }
                            }

                            // NIP / NIM
                            Text(
                                text = if (item.nomorInduk.isNotBlank()) item.nomorInduk else "-",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(120.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Status
                            Box(modifier = Modifier.width(110.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = themeColor.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Divisi / Jurusan
                            Text(
                                text = if (item.divisiOrJurusan.isNotBlank()) item.divisiOrJurusan else "-",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(120.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Aksi
                            Row(
                                modifier = Modifier.width(100.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onViewIdCard(item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = "Lihat ID Card",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onEdit(item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Data",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(item) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Hapus Data",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (index < anggotaList.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}

@Composable
fun AnggotaCardItem(
    anggota: AnggotaItem,
    onViewIdCard: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMurid = anggota.status.contains("Murid", ignoreCase = true) || anggota.status.contains("Siswa", ignoreCase = true)
    val isGuru = anggota.status.contains("Guru", ignoreCase = true) || anggota.status.contains("Dosen", ignoreCase = true)
    val themeColor = when {
        isMurid -> Color(0xFF4338CA)
        isGuru -> Color(0xFFB45309)
        else -> Color(0xFF0F766E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewIdCard() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onViewIdCard() }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo / Initial Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.12f))
                        .border(1.5.dp, themeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (anggota.fotoUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Uri.parse(anggota.fotoUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto ${anggota.nama}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = if (anggota.nama.isNotBlank()) anggota.nama.first().uppercase() else "?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = anggota.nama,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = themeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = anggota.status,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "NIM/NIP: ${anggota.nomorInduk}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )

                    if (anggota.divisiOrJurusan.isNotBlank()) {
                        Text(
                            text = anggota.divisiOrJurusan,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = themeColor.copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = themeColor, modifier = Modifier.size(11.dp))
                            Text(
                                text = "Klik Profil: Detail, Pratinjau & Ekspor",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = themeColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // QR Badge Preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onViewIdCard() }
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "QR Code",
                        tint = themeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "QR Presensi Aktif",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColor
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onViewIdCard,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Lihat ID Card",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Data",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Data",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMemberState(
    isSearching: Boolean,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.Badge,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSearching) "Data tidak ditemukan" else "Belum Ada Data Company",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isSearching) "Coba kata kunci pencarian lain atau pilih filter yang berbeda." else "Tambahkan data untuk membuat ID Card pengenal dan QR Code presensi.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Data Sekarang")
            }
        }
    }
}

// -------------------------------------------------------------
// INPUT POP-UP DIALOG (NIM/NIP, NAMA, STATUS, FOTO, QR CODE)
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnggotaInputDialog(
    existingAnggota: AnggotaItem?,
    viewModel: MainViewModel? = null,
    onDismiss: () -> Unit,
    onSave: (AnggotaItem) -> Unit
) {
    val context = LocalContext.current

    var nomorInduk by remember {
        mutableStateOf(existingAnggota?.nomorInduk ?: "")
    }
    var nama by remember { mutableStateOf(existingAnggota?.nama ?: "") }
    var status by remember {
        mutableStateOf(existingAnggota?.status ?: "Karyawan")
    }
    var divisiOrJurusan by remember {
        mutableStateOf(existingAnggota?.divisiOrJurusan ?: "")
    }
    var instansi by remember {
        mutableStateOf(existingAnggota?.instansi ?: "My Singghara")
    }
    var fotoUri by remember { mutableStateOf(existingAnggota?.fotoUri ?: "") }
    var kontak by remember { mutableStateOf(existingAnggota?.kontak ?: "") }
    var email by remember { mutableStateOf(existingAnggota?.email ?: "") }
    var berlakuHingga by remember {
        mutableStateOf(existingAnggota?.berlakuHingga ?: "Berlaku Selama Aktif")
    }
    var catatanQr by remember { mutableStateOf(existingAnggota?.catatanQr ?: "") }
    var memberTandaTanganUri by remember { mutableStateOf(existingAnggota?.tandaTanganUri ?: "") }

    var memberSigPaths by remember { mutableStateOf(listOf<SignaturePath>()) }
    var memberCurrentPoints by remember { mutableStateOf(listOf<SignaturePoint>()) }
    var memberCanvasWidthPx by remember { mutableStateOf(0) }
    var memberCanvasHeightPx by remember { mutableStateOf(0) }

    var isStatusDropdownExpanded by remember { mutableStateOf(false) }
    val statusOptions = listOf(
        "Karyawan",
        "Murid",
        "Guru / Tenaga Pendidik",
        "Dosen",
        "Staf Administrasi",
        "Magang / PKL",
        "Tamu / Mitra"
    )

    var isMasaBerlakuDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val masaBerlakuOptions = listOf(
        "Berlaku Selama Aktif",
        "Seumur Hidup / Permanen",
        "1 Bulan",
        "3 Bulan",
        "6 Bulan",
        "1 Tahun",
        "2 Tahun",
        "3 Tahun",
        "4 Tahun",
        "5 Tahun"
    )

    var showCropDialog by remember { mutableStateOf(false) }
    var cropSourceUri by remember { mutableStateOf<Uri?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            cropSourceUri = uri
            showCropDialog = true
        }
    }

    // Auto-generate NIM/NIP/Member ID helper
    fun generateRandomNomorInduk() {
        val prefix = when {
            status.contains("Murid", ignoreCase = true) || status.contains("Siswa", ignoreCase = true) -> "NIS-"
            status.contains("Guru", ignoreCase = true) || status.contains("Dosen", ignoreCase = true) -> "NIP-"
            else -> "EMP-"
        }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val rand = (1000..9999).random()
        nomorInduk = "$prefix$year$rand"
    }

    // Generated QR code payload string
    val qrPayload = remember(nomorInduk, nama, status, divisiOrJurusan, instansi, catatanQr) {
        val base = "SINGGHARA:ID=$nomorInduk;NAME=$nama;STATUS=$status;DIV=$divisiOrJurusan;INST=$instansi"
        if (catatanQr.isNotBlank()) "$base;NOTE=$catatanQr" else base
    }

    // Live QR Code bitmap preview
    var liveQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(qrPayload) {
        if (nomorInduk.isNotBlank() && nama.isNotBlank()) {
            withContext(Dispatchers.Default) {
                liveQrBitmap = QrCodeHelper.generateQrBitmap(qrPayload, 300)
            }
        } else {
            liveQrBitmap = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (existingAnggota != null) Icons.Default.Edit else Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (existingAnggota != null) "Edit Data" else "Input Data",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Form Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. FOTO PROFIL SECTION
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Foto Pengenal (ID Card)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (fotoUri.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(Uri.parse(fotoUri))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Foto",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Pilih Foto",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (fotoUri.isNotBlank()) "Ganti Foto" else "Impor Foto Galeri", fontSize = 12.sp)
                                    }

                                    if (fotoUri.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                cropSourceUri = if (fotoUri.startsWith("data:image") || fotoUri.startsWith("/")) {
                                                    Uri.parse(fotoUri)
                                                } else {
                                                    Uri.parse(fotoUri)
                                                }
                                                showCropDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sesuaikan", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { fotoUri = "" },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Foto",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. NOMOR INDUK (NIM / NIP)
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "NIM / NIP / ID Pengenal *",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = { generateRandomNomorInduk() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Buat Otomatis", fontSize = 11.sp)
                                }
                            }
                            OutlinedTextField(
                                value = nomorInduk,
                                onValueChange = { nomorInduk = it },
                                placeholder = { Text("Contoh: NIP-19880120 atau NIM-240109") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_nomor_induk"),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) }
                            )
                        }
                    }

                    // 3. NAMA LENGKAP
                    item {
                        Column {
                            Text(
                                text = "Nama Lengkap *",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = nama,
                                onValueChange = { nama = it },
                                placeholder = { Text("Nama lengkap karyawan / murid") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_nama_anggota"),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                            )
                        }
                    }

                    // 4. STATUS (DROPDOWN)
                    item {
                        Column {
                            Text(
                                text = "Status / Peran *",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExposedDropdownMenuBox(
                                expanded = isStatusDropdownExpanded,
                                onExpandedChange = { isStatusDropdownExpanded = !isStatusDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = status,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStatusDropdownExpanded) },
                                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = isStatusDropdownExpanded,
                                    onDismissRequest = { isStatusDropdownExpanded = false }
                                ) {
                                    statusOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = {
                                                status = opt
                                                isStatusDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. DIVISI / JURUSAN / KELAS
                    item {
                        Column {
                            Text(
                                text = "Divisi / Jurusan / Kelas / Jabatan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = divisiOrJurusan,
                                onValueChange = { divisiOrJurusan = it },
                                placeholder = { Text("Contoh: Teknik Informatika / HRD / Kelas 12 IPA") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.WorkOutline, contentDescription = null) }
                            )
                        }
                    }

                    // 6. INSTANSI / SEKOLAH / PERUSAHAAN
                    item {
                        Column {
                            Text(
                                text = "Instansi / Sekolah / Perusahaan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = instansi,
                                onValueChange = { instansi = it },
                                placeholder = { Text("Nama instansi penerbit ID Card") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                            )
                        }
                    }

                    // 7. MASA BERLAKU
                    item {
                        Column {
                            Text(
                                text = "Masa Berlaku ID Card",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ExposedDropdownMenuBox(
                                        expanded = isMasaBerlakuDropdownExpanded,
                                        onExpandedChange = { isMasaBerlakuDropdownExpanded = !isMasaBerlakuDropdownExpanded }
                                    ) {
                                        OutlinedTextField(
                                            value = berlakuHingga,
                                            onValueChange = { },
                                            readOnly = true,
                                            placeholder = { Text("Pilih masa berlaku...") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMasaBerlakuDropdownExpanded) }
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isMasaBerlakuDropdownExpanded,
                                            onDismissRequest = { isMasaBerlakuDropdownExpanded = false }
                                        ) {
                                            masaBerlakuOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = opt,
                                                                fontWeight = if (berlakuHingga == opt) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (berlakuHingga == opt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (berlakuHingga == opt) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        berlakuHingga = opt
                                                        isMasaBerlakuDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Icon Kalender di sebelah kanan form input (Date Time Picker bawaan aplikasi)
                                IconButton(
                                    onClick = { showDatePickerDialog = true },
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .testTag("btn_masa_berlaku_calendar")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "Pilih Kalender Masa Berlaku",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 8. KONTAK & EMAIL
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = kontak,
                                onValueChange = { kontak = it },
                                label = { Text("No. HP") },
                                placeholder = { Text("08...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                placeholder = { Text("@...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // 9. TANDA TANGAN PENGGUNA ID CARD (MEMBER / SISWA / KARYAWAN)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tanda Tangan Pengguna ID Card", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    if (memberSigPaths.isNotEmpty() || memberCurrentPoints.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                memberSigPaths = emptyList()
                                                memberCurrentPoints = emptyList()
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Bersihkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    } else if (memberTandaTanganUri.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                memberTandaTanganUri = ""
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Hapus TTD", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFCFDFE))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        memberCurrentPoints = listOf(SignaturePoint(offset.x, offset.y))
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        memberCurrentPoints = memberCurrentPoints + SignaturePoint(change.position.x, change.position.y)
                                                    },
                                                    onDragEnd = {
                                                        if (memberCurrentPoints.isNotEmpty()) {
                                                            memberSigPaths = memberSigPaths + SignaturePath(memberCurrentPoints, Color(0xFF0F172A), 4.5f)
                                                            memberCurrentPoints = emptyList()
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        memberCurrentPoints = emptyList()
                                                    }
                                                )
                                            }
                                    ) {
                                        memberCanvasWidthPx = size.width.toInt()
                                        memberCanvasHeightPx = size.height.toInt()

                                        for (path in memberSigPaths) {
                                            if (path.points.size > 1) {
                                                val p = Path().apply {
                                                    moveTo(path.points.first().x, path.points.first().y)
                                                    for (i in 1 until path.points.size) {
                                                        val p0 = path.points[i - 1]
                                                        val p1 = path.points[i]
                                                        quadraticBezierTo(p0.x, p0.y, (p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
                                                    }
                                                    lineTo(path.points.last().x, path.points.last().y)
                                                }
                                                drawPath(p, path.color, style = Stroke(width = path.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                            }
                                        }

                                        if (memberCurrentPoints.size > 1) {
                                            val p = Path().apply {
                                                moveTo(memberCurrentPoints.first().x, memberCurrentPoints.first().y)
                                                for (i in 1 until memberCurrentPoints.size) {
                                                    val p0 = memberCurrentPoints[i - 1]
                                                    val p1 = memberCurrentPoints[i]
                                                    quadraticBezierTo(p0.x, p0.y, (p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
                                                }
                                                lineTo(memberCurrentPoints.last().x, memberCurrentPoints.last().y)
                                            }
                                            drawPath(p, Color(0xFF0F172A), style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                        }
                                    }

                                    if (memberSigPaths.isEmpty() && memberCurrentPoints.isEmpty()) {
                                        if (memberTandaTanganUri.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(if (memberTandaTanganUri.startsWith("data:image")) memberTandaTanganUri else Uri.parse(memberTandaTanganUri))
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Tanda Tangan Tersimpan",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Goreskan tanda tangan pengguna di sini\n(Akan dicetak di belakang ID Card sebelah kanan)",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 10. QR CODE LIVE PREVIEW CARD
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("QR Code Presensi Otomatis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text("Live Preview", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (liveQrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .background(Color.White, RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = liveQrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Isi NIM & Nama\nuntuk buat QR", fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Gray)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "QR code ini dapat langsung dipindai oleh fitur Scanner Presensi untuk mencatat kehadiran.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            if (nomorInduk.isBlank() || nama.isBlank()) {
                                MainViewModel.instance?.showToastPopup("Mohon isi NIM/NIP dan Nama Lengkap")
                                return@Button
                            }

                            // Save member signature to file if drawn
                            var finalMemberSigUri = memberTandaTanganUri
                            if (memberSigPaths.isNotEmpty()) {
                                try {
                                    val w = if (memberCanvasWidthPx > 0) memberCanvasWidthPx else 500
                                    val h = if (memberCanvasHeightPx > 0) memberCanvasHeightPx else 200
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                                    for (path in memberSigPaths) {
                                        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.argb(
                                                (path.color.alpha * 255).toInt(),
                                                (path.color.red * 255).toInt(),
                                                (path.color.green * 255).toInt(),
                                                (path.color.blue * 255).toInt()
                                            )
                                            style = AndroidPaint.Style.STROKE
                                            strokeWidth = path.strokeWidth * 1.5f
                                            strokeCap = AndroidPaint.Cap.ROUND
                                            strokeJoin = AndroidPaint.Join.ROUND
                                        }
                                        if (path.points.size > 1) {
                                            val androidPath = AndroidPath().apply {
                                                moveTo(path.points.first().x, path.points.first().y)
                                                for (i in 1 until path.points.size) {
                                                    val p0 = path.points[i - 1]
                                                    val p1 = path.points[i]
                                                    quadTo(p0.x, p0.y, (p0.x + p1.x) / 2, (p0.y + p1.y) / 2)
                                                }
                                                lineTo(path.points.last().x, path.points.last().y)
                                            }
                                            canvas.drawPath(androidPath, paint)
                                        }
                                    }

                                    val sigFile = File(context.filesDir, "sig_user_${System.currentTimeMillis()}.png")
                                    FileOutputStream(sigFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    finalMemberSigUri = sigFile.absolutePath
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val itemToSave = AnggotaItem(
                                id = existingAnggota?.id ?: 0,
                                nomorInduk = nomorInduk.trim(),
                                nama = nama.trim(),
                                status = status,
                                divisiOrJurusan = divisiOrJurusan.trim(),
                                instansi = instansi.trim(),
                                fotoUri = fotoUri,
                                qrCodeData = qrPayload,
                                kontak = kontak.trim(),
                                email = email.trim(),
                                berlakuHingga = berlakuHingga.trim(),
                                catatanQr = catatanQr.trim(),
                                tandaTanganUri = finalMemberSigUri,
                                tanggalDibuat = existingAnggota?.tanggalDibuat ?: System.currentTimeMillis()
                            )
                            onSave(itemToSave)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("btn_simpan_anggota"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (existingAnggota != null) "Perbarui & Lihat ID Card" else "Simpan & Buat ID Card", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showCropDialog && cropSourceUri != null) {
        PhotoCropAdjustDialog(
            imageUri = cropSourceUri!!,
            onDismiss = { showCropDialog = false },
            onCropApplied = { resultUri ->
                fotoUri = resultUri
                showCropDialog = false
            }
        )
    }

    if (showDatePickerDialog) {
        CustomDatePickerDialog(
            initialDateStr = if (berlakuHingga.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) berlakuHingga else null,
            onDismissRequest = { showDatePickerDialog = false },
            onDateSelected = { selectedDate ->
                berlakuHingga = selectedDate
                showDatePickerDialog = false
            }
        )
    }
}

// -------------------------------------------------------------
// ID CARD PREVIEW & EXPORT DIALOG
// -------------------------------------------------------------

@Composable
fun IdCardPreviewDialog(
    anggota: AnggotaItem,
    viewModel: MainViewModel,
    signatureVersion: Int = 0,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var previewMode by remember { mutableStateOf("FRONT") } // "FRONT", "BACK", "BOTH"
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var combinedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBitmaps by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var showExportDropdown by remember { mutableStateOf(false) }

    // Generate Bitmaps using Standard Vertical Generator
    LaunchedEffect(anggota, signatureVersion) {
        isLoadingBitmaps = true
        withContext(Dispatchers.Default) {
            val qr = try {
                val qrPayload = if (anggota.qrCodeData.isNotBlank()) anggota.qrCodeData
                else "SINGGHARA_MEMBER:${anggota.nomorInduk}:${anggota.nama}:${anggota.status}"
                QrCodeHelper.generateQrBitmap(qrPayload, 450)
            } catch (e: Exception) {
                null
            }

            val f = IdCardBitmapGenerator.createVerticalIdCardBitmap(context, anggota, qr, isFrontSide = true)
            val b = IdCardBitmapGenerator.createVerticalIdCardBitmap(context, anggota, qr, isFrontSide = false)
            val c = IdCardBitmapGenerator.createCombinedDualSideBitmap(context, anggota, qr)
            frontBitmap = f
            backBitmap = b
            combinedBitmap = c
        }
        isLoadingBitmaps = false
    }

    val isMurid = anggota.status.contains("Murid", ignoreCase = true) || anggota.status.contains("Siswa", ignoreCase = true)
    val isGuru = anggota.status.contains("Guru", ignoreCase = true) || anggota.status.contains("Dosen", ignoreCase = true) || anggota.status.contains("Pendidik", ignoreCase = true)

    val primaryThemeColor = when {
        isMurid -> Color(0xFF4338CA)
        isGuru -> Color(0xFFB45309)
        else -> Color(0xFF0F766E)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar: Pop Up Detail Pengguna (Fitur Pratinjau & Ekspor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = primaryThemeColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Pop Up Detail: Fitur Pratinjau & Ekspor",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kartu ID Standar & Rekap Profil",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Ringkasan Detail Profil Pengguna (Muncul saat klik profil user)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Foto Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(primaryThemeColor.copy(alpha = 0.15f))
                                .border(1.5.dp, primaryThemeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (anggota.fotoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(Uri.parse(anggota.fotoUri))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Foto ${anggota.nama}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = if (anggota.nama.isNotBlank()) anggota.nama.first().uppercase() else "?",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryThemeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Detail Info Column
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = anggota.nama,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryThemeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, primaryThemeColor.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = anggota.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryThemeColor,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "NIP/NIM: ${anggota.nomorInduk.ifBlank { "-" }}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (anggota.divisiOrJurusan.isNotBlank()) {
                                    Text(
                                        text = "• ${anggota.divisiOrJurusan}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (anggota.emailOrTelp.isNotBlank()) {
                                    Text(
                                        text = "• ${anggota.emailOrTelp}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Front / Back / Both Sided Mode Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (previewMode == "FRONT") primaryThemeColor else Color.Transparent,
                                modifier = Modifier.clickable { previewMode = "FRONT" }
                            ) {
                                Text(
                                    text = "Muka Depan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (previewMode == "FRONT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (previewMode == "BACK") primaryThemeColor else Color.Transparent,
                                modifier = Modifier.clickable { previewMode = "BACK" }
                            ) {
                                Text(
                                    text = "Muka Belakang",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (previewMode == "BACK") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (previewMode == "BOTH") primaryThemeColor else Color.Transparent,
                                modifier = Modifier.clickable { previewMode = "BOTH" }
                            ) {
                                Text(
                                    text = "Kedua Sisi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (previewMode == "BOTH") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // ID CARD BITMAP PREVIEW CANVAS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingBitmaps) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = primaryThemeColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Menyiapkan pratinjau kartu...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        when (previewMode) {
                            "FRONT" -> {
                                if (frontBitmap != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .wrapContentWidth()
                                            .shadow(12.dp, RoundedCornerShape(18.dp)),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Image(
                                            bitmap = frontBitmap!!.asImageBitmap(),
                                            contentDescription = "Tampak Depan",
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(18.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                            "BACK" -> {
                                if (backBitmap != null) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .wrapContentWidth()
                                            .shadow(12.dp, RoundedCornerShape(18.dp)),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Image(
                                            bitmap = backBitmap!!.asImageBitmap(),
                                            contentDescription = "Tampak Belakang",
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(18.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                            "BOTH" -> {
                                val scrollState = rememberScrollState()
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .horizontalScroll(scrollState),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (frontBitmap != null) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxHeight(0.96f)
                                                .wrapContentWidth()
                                                .padding(horizontal = 8.dp)
                                                .shadow(10.dp, RoundedCornerShape(18.dp)),
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Image(
                                                bitmap = frontBitmap!!.asImageBitmap(),
                                                contentDescription = "Depan",
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(18.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                    if (backBitmap != null) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxHeight(0.96f)
                                                .wrapContentWidth()
                                                .padding(horizontal = 8.dp)
                                                .shadow(10.dp, RoundedCornerShape(18.dp)),
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Image(
                                                bitmap = backBitmap!!.asImageBitmap(),
                                                contentDescription = "Belakang",
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(18.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons: Edit / Share / Export
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Data", fontSize = 12.sp)
                    }

                    Box(modifier = Modifier.weight(1.5f)) {
                        Button(
                            onClick = { showExportDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_export_id_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryThemeColor),
                            enabled = !isExporting && !isLoadingBitmaps
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan / Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            }
                        }

                        DropdownMenu(
                            expanded = showExportDropdown,
                            onDismissRequest = { showExportDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Simpan Tampak Depan (PNG)") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showExportDropdown = false
                                    if (frontBitmap != null) {
                                        isExporting = true
                                        scope.launch {
                                            IdCardBitmapGenerator.saveAndShareIdCard(context, frontBitmap!!, "${anggota.nama}_Depan")
                                            isExporting = false
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Simpan Tampak Belakang (PNG)") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showExportDropdown = false
                                    if (backBitmap != null) {
                                        isExporting = true
                                        scope.launch {
                                            IdCardBitmapGenerator.saveAndShareIdCard(context, backBitmap!!, "${anggota.nama}_Belakang")
                                            isExporting = false
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Simpan Kedua Sisi / Dual (PNG)") },
                                leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                                onClick = {
                                    showExportDropdown = false
                                    if (combinedBitmap != null) {
                                        isExporting = true
                                        scope.launch {
                                            IdCardBitmapGenerator.saveAndShareIdCard(context, combinedBitmap!!, "${anggota.nama}_DualSide")
                                            isExporting = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ATTENDANCE QR CODE SCANNER & VERIFICATION
// -------------------------------------------------------------

@Composable
fun AttendanceQrScannerDialog(
    allAnggota: List<AnggotaItem>,
    onDismiss: () -> Unit,
    onRecordAttendance: (RiwayatPresensi) -> Unit
) {
    val context = LocalContext.current
    var verifiedAnggota by remember { mutableStateOf<AnggotaItem?>(null) }
    var scannedRawData by remember { mutableStateOf<String?>(null) }
    var attendanceStatus by remember { mutableStateOf("Hadir") }

    if (verifiedAnggota != null || scannedRawData != null) {
        // Attendance Confirmation Popup
        AlertDialog(
            onDismissRequest = {
                verifiedAnggota = null
                scannedRawData = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Presensi Berhasil Terdeteksi!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val member = verifiedAnggota
                    if (member != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = member.nama, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(text = "NIM/NIP: ${member.nomorInduk}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "Status: ${member.status} • ${member.divisiOrJurusan}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Text(
                            text = "Data QR: $scannedRawData",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Pilih Status Kehadiran:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Hadir", "Terlambat", "Izin", "Dinas").forEach { st ->
                            val isSel = attendanceStatus == st
                            FilterChip(
                                selected = isSel,
                                onClick = { attendanceStatus = st },
                                label = { Text(st, fontSize = 11.sp) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val member = verifiedAnggota
                        val now = System.currentTimeMillis()
                        val presensi = RiwayatPresensi(
                            anggotaId = member?.id?.toLong() ?: 0L,
                            nomorInduk = member?.nomorInduk ?: "QR-SCAN",
                            nama = member?.nama ?: (scannedRawData ?: "Tamu / Anggota"),
                            status = member?.status ?: "Umum",
                            divisi = member?.divisiOrJurusan ?: "",
                            statusPresensi = attendanceStatus,
                            timestamp = now,
                            metode = "QR Scanner Kamera"
                        )
                        onRecordAttendance(presensi)
                        verifiedAnggota = null
                        scannedRawData = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Catat Presensi")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    verifiedAnggota = null
                    scannedRawData = null
                }) {
                    Text("Scan Ulang")
                }
            }
        )
    } else {
        // Fullscreen Barcode / QR Scanner
        BarcodeScannerDialog(
            onDismissRequest = onDismiss,
            onBarcodeScanned = { scannedCode ->
                // Parse payload or find matching member
                val cleanCode = scannedCode.trim()
                var matchedMember: AnggotaItem? = null

                // 1. Direct NIM/NIP match
                matchedMember = allAnggota.find { it.nomorInduk.equals(cleanCode, ignoreCase = true) }

                // 2. Structured SINGGHARA payload match
                if (matchedMember == null && cleanCode.startsWith("SINGGHARA:")) {
                    val parts = cleanCode.removePrefix("SINGGHARA:").split(";")
                    var extractedId = ""
                    var extractedName = ""
                    var extractedStatus = ""
                    var extractedDiv = ""
                    for (part in parts) {
                        val kv = part.split("=")
                        if (kv.size == 2) {
                            when (kv[0].trim().uppercase()) {
                                "ID" -> extractedId = kv[1].trim()
                                "NAME" -> extractedName = kv[1].trim()
                                "STATUS" -> extractedStatus = kv[1].trim()
                                "DIV" -> extractedDiv = kv[1].trim()
                            }
                        }
                    }
                    if (extractedId.isNotBlank()) {
                        matchedMember = allAnggota.find { it.nomorInduk.equals(extractedId, ignoreCase = true) }
                    }
                    if (matchedMember == null && extractedName.isNotBlank()) {
                        matchedMember = AnggotaItem(
                            nomorInduk = extractedId.ifBlank { "ID-QR" },
                            nama = extractedName,
                            status = extractedStatus.ifBlank { "Karyawan / Murid" },
                            divisiOrJurusan = extractedDiv
                        )
                    }
                }

                if (matchedMember != null) {
                    verifiedAnggota = matchedMember
                } else {
                    scannedRawData = cleanCode
                }
            }
        )
    }
}

// -------------------------------------------------------------
// RIWAYAT PRESENSI DIALOG & LOGS
// -------------------------------------------------------------

@Composable
fun RiwayatPresensiDialog(
    presensiList: List<RiwayatPresensi>,
    onDismiss: () -> Unit,
    onDelete: (RiwayatPresensi) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredPresensi = remember(presensiList, searchQuery) {
        if (searchQuery.isBlank()) presensiList else {
            presensiList.filter {
                it.nama.contains(searchQuery, ignoreCase = true) ||
                        it.nomorInduk.contains(searchQuery, ignoreCase = true) ||
                        it.statusPresensi.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Riwayat Presensi QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Search & Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Log: ${presensiList.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (presensiList.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus Semua Log", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama atau NIM/NIP...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Logs List
                if (filteredPresensi.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Log tidak ditemukan" else "Belum ada riwayat presensi.\nPindai QR Code untuk mencatat kehadiran.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPresensi, key = { it.id }) { item ->
                            val timeStr = remember(item.timestamp) {
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = item.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (item.statusPresensi == "Hadir") Color(0xFF059669) else Color(0xFFD97706)
                                            ) {
                                                Text(
                                                    text = item.statusPresensi,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${item.nomorInduk} • ${item.status} ${if (item.divisi.isNotBlank()) "(${item.divisi})" else ""}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$timeStr • ${item.metode}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDelete(item) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Log", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Bottom Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus Semua Riwayat?") },
            text = { Text("Semua log riwayat presensi akan dihapus secara permanen.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearConfirm = false
                        MainViewModel.instance?.showToastPopup("Semua riwayat presensi telah dibersihkan")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Interactive Photo Cropping & Position Adjustment Dialog (Circular Frame)
 */
@Composable
fun PhotoCropAdjustDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropApplied: (String) -> Unit
) {
    val context = LocalContext.current
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var rotationDegrees by remember { mutableStateOf(0) }

    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val uriStr = imageUri.toString()
                val bmp = if (uriStr.startsWith("data:image")) {
                    val base64Data = uriStr.substringAfter("base64,")
                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else if (uriStr.startsWith("/")) {
                    BitmapFactory.decodeFile(uriStr)
                } else {
                    val stream = context.contentResolver.openInputStream(imageUri)
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    decoded
                }
                rawBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sesuaikan Posisi Foto Lingkaran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "Geser foto dengan jari, perbesar (zoom), atau putar agar wajah pas di dalam lingkaran ID Card.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Interactive Crop Canvas Preview
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .pointerInput(rawBitmap) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                zoom = (zoom * gestureZoom).coerceIn(0.4f, 3.5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (rawBitmap != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val centerX = canvasW / 2f
                            val centerY = canvasH / 2f
                            val circleRadius = 100.dp.toPx() // 200dp diameter

                            val bmp = rawBitmap!!
                            val minSide = Math.min(bmp.width, bmp.height).toFloat().coerceAtLeast(1f)
                            val baseScale = (circleRadius * 2f) / minSide
                            val totalScale = baseScale * zoom

                            drawContext.canvas.nativeCanvas.apply {
                                save()
                                translate(centerX + offsetX, centerY + offsetY)
                                rotate(rotationDegrees.toFloat())
                                scale(totalScale, totalScale)
                                drawBitmap(
                                    bmp,
                                    -bmp.width / 2f,
                                    -bmp.height / 2f,
                                    AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
                                )
                                restore()

                                // Semi-transparent dark vignette mask outside circle
                                save()
                                val maskPath = AndroidPath().apply {
                                    addRect(0f, 0f, canvasW, canvasH, AndroidPath.Direction.CW)
                                    addCircle(centerX, centerY, circleRadius, AndroidPath.Direction.CCW)
                                }
                                val maskPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.argb(190, 15, 23, 42)
                                    style = AndroidPaint.Style.FILL
                                }
                                drawPath(maskPath, maskPaint)
                                restore()

                                // Gold Guide Circle Rim
                                val ringPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.rgb(245, 158, 11)
                                    style = AndroidPaint.Style.STROKE
                                    strokeWidth = 6f
                                }
                                drawCircle(centerX, centerY, circleRadius, ringPaint)

                                // Crosshairs Guide
                                val crosshairPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.argb(70, 255, 255, 255)
                                    style = AndroidPaint.Style.STROKE
                                    strokeWidth = 2f
                                }
                                drawLine(centerX - circleRadius + 20f, centerY, centerX + circleRadius - 20f, centerY, crosshairPaint)
                                drawLine(centerX, centerY - circleRadius + 20f, centerX, centerY + circleRadius - 20f, crosshairPaint)
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Zoom Slider Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { zoom = (zoom - 0.15f).coerceIn(0.4f, 3.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Perkecil", modifier = Modifier.size(16.dp))
                    }
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        valueRange = 0.4f..3.5f,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { zoom = (zoom + 0.15f).coerceIn(0.4f, 3.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Perbesar", modifier = Modifier.size(16.dp))
                    }
                }

                // Action buttons: Rotate & Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Putar 90°", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            zoom = 1f
                            offsetX = 0f
                            offsetY = 0f
                            rotationDegrees = 0
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pusatkan", fontSize = 11.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons: Cancel & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal")
                    }

                    Button(
                        onClick = {
                            val bmp = rawBitmap
                            if (bmp != null) {
                                try {
                                    val targetSize = 512
                                    val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                                    val outCanvas = android.graphics.Canvas(outputBitmap)

                                    // Circular clip
                                    val clipPath = AndroidPath().apply {
                                        addCircle(targetSize / 2f, targetSize / 2f, targetSize / 2f, AndroidPath.Direction.CW)
                                    }
                                    outCanvas.clipPath(clipPath)

                                    val previewRadius = 200f // 100dp on screen approx
                                    val scaleRatio = targetSize.toFloat() / (previewRadius * 2f)

                                    val matrix = Matrix()
                                    val minSide = Math.min(bmp.width, bmp.height).toFloat().coerceAtLeast(1f)
                                    val baseScale = (previewRadius * 2f) / minSide
                                    val totalScale = baseScale * zoom * scaleRatio

                                    matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                                    matrix.postRotate(rotationDegrees.toFloat())
                                    matrix.postScale(totalScale, totalScale)
                                    matrix.postTranslate(
                                        targetSize / 2f + (offsetX * scaleRatio),
                                        targetSize / 2f + (offsetY * scaleRatio)
                                    )

                                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
                                    outCanvas.drawBitmap(bmp, matrix, paint)

                                    val dir = File(context.filesDir, "profile_photos")
                                    if (!dir.exists()) dir.mkdirs()
                                    val file = File(dir, "crop_avatar_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(file).use { out ->
                                        outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                    }
                                    onCropApplied(Uri.fromFile(file).toString())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    MainViewModel.instance?.showToastPopup("Gagal memproses foto")
                                }
                            }
                        },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terapkan Foto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class IdCardColorPalette(
    val primaryDark: Color,
    val accent: Color,
    val accentDark: Color,
    val bottomBar: Color,
    val roleBadgeTitle: String
)

fun getIdCardColorPalette(status: String): IdCardColorPalette {
    val s = status.lowercase()
    return when {
        s.contains("murid") || s.contains("siswa") || s.contains("santri") || s.contains("pelajar") -> {
            IdCardColorPalette(
                primaryDark = Color(0xFF1E1B4B),   // Midnight Indigo
                accent = Color(0xFF38BDF8),        // Sky Blue / Cyan
                accentDark = Color(0xFF0284C7),    // Ocean Cyan
                bottomBar = Color(0xFF2563EB),     // Cobalt Blue
                roleBadgeTitle = "MURID / SISWA"
            )
        }
        s.contains("guru") || s.contains("dosen") || s.contains("pendidik") || s.contains("pengajar") || s.contains("ustadz") -> {
            IdCardColorPalette(
                primaryDark = Color(0xFF064E3B),   // Deep Emerald
                accent = Color(0xFF10B981),        // Emerald Green
                accentDark = Color(0xFF059669),    // Forest Jade
                bottomBar = Color(0xFF0D9488),     // Deep Teal
                roleBadgeTitle = "GURU / PENDIDIK"
            )
        }
        s.contains("staf") || s.contains("staff") || s.contains("admin") || s.contains("keuangan") || s.contains("tata usaha") -> {
            IdCardColorPalette(
                primaryDark = Color(0xFF4C0519),   // Deep Burgundy Crimson
                accent = Color(0xFFFB7185),        // Rose Coral
                accentDark = Color(0xFFE11D48),    // Crimson Rose
                bottomBar = Color(0xFFBE123C),     // Ruby Red
                roleBadgeTitle = "STAF ADMINISTRASI"
            )
        }
        s.contains("magang") || s.contains("pkl") || s.contains("intern") || s.contains("prakerin") -> {
            IdCardColorPalette(
                primaryDark = Color(0xFF3B0764),   // Deep Violet Purple
                accent = Color(0xFFC084FC),        // Lavender
                accentDark = Color(0xFF9333EA),    // Vivid Purple
                bottomBar = Color(0xFF7E22CE),     // Royal Violet
                roleBadgeTitle = "MAGANG / PKL"
            )
        }
        s.contains("tamu") || s.contains("mitra") || s.contains("guest") || s.contains("alumni") -> {
            IdCardColorPalette(
                primaryDark = Color(0xFF1E293B),   // Deep Slate
                accent = Color(0xFF94A3B8),        // Silver Slate
                accentDark = Color(0xFF64748B),    // Cool Slate
                bottomBar = Color(0xFF475569),     // Slate Charcoal
                roleBadgeTitle = "TAMU / MITRA"
            )
        }
        else -> { // Default: Karyawan / Pegawai / Official
            IdCardColorPalette(
                primaryDark = Color(0xFF1B222E),   // Dark Navy Charcoal
                accent = Color(0xFFF59E0B),        // Vibrant Amber Gold
                accentDark = Color(0xFFD97706),    // Deep Gold
                bottomBar = Color(0xFFF59E0B),     // Amber Gold
                roleBadgeTitle = if (status.isNotBlank()) status.uppercase() else "KARYAWAN"
            )
        }
    }
}

// -------------------------------------------------------------
// SIGNATURE DRAWING & PIN PROTECTED DELETION COMPONENTS
// -------------------------------------------------------------

data class SignaturePoint(val x: Float, val y: Float)
data class SignaturePath(val points: List<SignaturePoint>, val color: Color, val strokeWidth: Float)

@Composable
fun TandaTanganDialog(
    context: Context,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSignatureUpdated: () -> Unit
) {
    val sharedPrefs = remember { context.getSharedPreferences("singghara_idcard_prefs", Context.MODE_PRIVATE) }
    var ceoLabelText by remember { mutableStateOf(sharedPrefs.getString("key_ceo_signature_label", "my singghara") ?: "my singghara") }

    var paths by remember { mutableStateOf(listOf<SignaturePath>()) }
    var currentPathPoints by remember { mutableStateOf(listOf<SignaturePoint>()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF0F172A)) } // Default navy dark ink
    var selectedStrokeWidth by remember { mutableStateOf(5.5f) }
    var showPinDialog by remember { mutableStateOf(false) }

    val signatureFile = remember { File(context.filesDir, "company_id_card_signature.png") }
    var hasExistingSignature by remember { mutableStateOf(signatureFile.exists() && signatureFile.length() > 0) }

    val colors = listOf(
        Pair("Hitam", Color(0xFF0F172A)),
        Pair("Biru Navy", Color(0xFF1E3A8A)),
        Pair("Biru Tua", Color(0xFF1D4ED8)),
        Pair("Teal", Color(0xFF0F766E))
    )

    if (showPinDialog) {
        PinVerificationDialog(
            appPin = viewModel.appPin,
            onSuccess = {
                showPinDialog = false
                try {
                    if (signatureFile.exists()) {
                        signatureFile.delete()
                    }
                    hasExistingSignature = false
                    paths = emptyList()
                    currentPathPoints = emptyList()
                    onSignatureUpdated()
                    viewModel.showToastPopup("Tanda tangan berhasil dihapus.")
                } catch (e: Exception) {
                    viewModel.showToastPopup("Gagal menghapus: ${e.message}")
                }
            },
            onDismiss = { showPinDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tanda Tangan ID Card (CEO)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Muka belakang ID Card sebelah kiri",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // CEO Label Input (e.g. "my singghara")
                OutlinedTextField(
                    value = ceoLabelText,
                    onValueChange = { ceoLabelText = it },
                    label = { Text("Label Tanda Tangan CEO (Muka Belakang Kiri)") },
                    placeholder = { Text("my singghara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Info banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Goreskan tanda tangan CEO di bawah. Tanda tangan dan label akan dicetak di sudut kiri bawah belakang kartu.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Drawing Canvas Box
                var canvasWidthPx by remember { mutableStateOf(0) }
                var canvasHeightPx by remember { mutableStateOf(0) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFCFDFE))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedColor, selectedStrokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPathPoints = listOf(SignaturePoint(offset.x, offset.y))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPathPoints = currentPathPoints + SignaturePoint(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        if (currentPathPoints.isNotEmpty()) {
                                            paths = paths + SignaturePath(currentPathPoints, selectedColor, selectedStrokeWidth)
                                            currentPathPoints = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentPathPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        canvasWidthPx = size.width.toInt()
                        canvasHeightPx = size.height.toInt()

                        // Draw dashed baseline guide
                        val baselineY = size.height * 0.78f
                        drawLine(
                            color = Color(0xFFCBD5E1),
                            start = Offset(20f, baselineY),
                            end = Offset(size.width - 20f, baselineY),
                            strokeWidth = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Render completed strokes
                        for (path in paths) {
                            if (path.points.size > 1) {
                                val composePath = Path().apply {
                                    moveTo(path.points.first().x, path.points.first().y)
                                    for (i in 1 until path.points.size) {
                                        val p0 = path.points[i - 1]
                                        val p1 = path.points[i]
                                        quadraticBezierTo(
                                            p0.x, p0.y,
                                            (p0.x + p1.x) / 2, (p0.y + p1.y) / 2
                                        )
                                    }
                                    lineTo(path.points.last().x, path.points.last().y)
                                }
                                drawPath(
                                    path = composePath,
                                    color = path.color,
                                    style = Stroke(
                                        width = path.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else if (path.points.size == 1) {
                                drawCircle(
                                    color = path.color,
                                    radius = path.strokeWidth / 2f,
                                    center = Offset(path.points[0].x, path.points[0].y)
                                )
                            }
                        }

                        // Render active stroke
                        if (currentPathPoints.size > 1) {
                            val livePath = Path().apply {
                                moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                                for (i in 1 until currentPathPoints.size) {
                                    val p0 = currentPathPoints[i - 1]
                                    val p1 = currentPathPoints[i]
                                    quadraticBezierTo(
                                        p0.x, p0.y,
                                        (p0.x + p1.x) / 2, (p0.y + p1.y) / 2
                                    )
                                }
                                lineTo(currentPathPoints.last().x, currentPathPoints.last().y)
                            }
                            drawPath(
                                path = livePath,
                                color = selectedColor,
                                style = Stroke(
                                    width = selectedStrokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (currentPathPoints.size == 1) {
                            drawCircle(
                                color = selectedColor,
                                radius = selectedStrokeWidth / 2f,
                                center = Offset(currentPathPoints[0].x, currentPathPoints[0].y)
                            )
                        }
                    }

                    // Watermark hint if canvas empty
                    if (paths.isEmpty() && currentPathPoints.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasExistingSignature) "Goreskan tanda tangan baru di sini\n(Tanda tangan lama tersimpan)" else "Tanda tangan di sini...",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Controls: Color & Stroke Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Picker
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { (name, color) ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = name,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Stroke width picker
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(Pair("Tipis", 3.5f), Pair("Sedang", 5.5f), Pair("Tebal", 8.5f)).forEach { (label, w) ->
                            val isSelected = selectedStrokeWidth == w
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStrokeWidth = w },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bersihkan kanvas
                    OutlinedButton(
                        onClick = {
                            paths = emptyList()
                            currentPathPoints = emptyList()
                        },
                        enabled = paths.isNotEmpty() || currentPathPoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bersihkan", fontSize = 12.sp)
                    }

                    // Hapus Tanda Tangan Tersimpan (dengan proteksi PIN)
                    if (hasExistingSignature) {
                        OutlinedButton(
                            onClick = {
                                if (viewModel.isAppLockEnabled && viewModel.appPin.isNotBlank()) {
                                    showPinDialog = true
                                } else {
                                    try {
                                        if (signatureFile.exists()) {
                                            signatureFile.delete()
                                        }
                                        hasExistingSignature = false
                                        paths = emptyList()
                                        currentPathPoints = emptyList()
                                        onSignatureUpdated()
                                        viewModel.showToastPopup("Tanda tangan berhasil dihapus.")
                                    } catch (e: Exception) {
                                        viewModel.showToastPopup("Gagal menghapus: ${e.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Hapus TTD", fontSize = 12.sp)
                        }
                    }

                    // Simpan Tanda Tangan & Label
                    Button(
                        onClick = {
                            val cleanLabel = ceoLabelText.trim().ifEmpty { "my singghara" }
                            sharedPrefs.edit().putString("key_ceo_signature_label", cleanLabel).apply()

                            if (paths.isNotEmpty()) {
                                try {
                                    val w = if (canvasWidthPx > 0) canvasWidthPx else 600
                                    val h = if (canvasHeightPx > 0) canvasHeightPx else 300
                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                                    for (path in paths) {
                                        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.argb(
                                                (path.color.alpha * 255).toInt(),
                                                (path.color.red * 255).toInt(),
                                                (path.color.green * 255).toInt(),
                                                (path.color.blue * 255).toInt()
                                            )
                                            style = AndroidPaint.Style.STROKE
                                            strokeWidth = path.strokeWidth * 1.5f
                                            strokeCap = AndroidPaint.Cap.ROUND
                                            strokeJoin = AndroidPaint.Join.ROUND
                                        }

                                        if (path.points.size > 1) {
                                            val androidPath = AndroidPath().apply {
                                                moveTo(path.points.first().x, path.points.first().y)
                                                for (i in 1 until path.points.size) {
                                                    val p0 = path.points[i - 1]
                                                    val p1 = path.points[i]
                                                    quadTo(
                                                        p0.x, p0.y,
                                                        (p0.x + p1.x) / 2, (p0.y + p1.y) / 2
                                                    )
                                                }
                                                lineTo(path.points.last().x, path.points.last().y)
                                            }
                                            canvas.drawPath(androidPath, paint)
                                        } else if (path.points.size == 1) {
                                            paint.style = AndroidPaint.Style.FILL
                                            canvas.drawCircle(path.points[0].x, path.points[0].y, path.strokeWidth * 0.75f, paint)
                                        }
                                    }

                                    FileOutputStream(signatureFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    onSignatureUpdated()
                                    viewModel.showToastPopup("Tanda tangan resmi & label berhasil disimpan.")
                                    onDismiss()
                                } catch (e: Exception) {
                                    viewModel.showToastPopup("Gagal menyimpan: ${e.message}")
                                }
                            } else {
                                onSignatureUpdated()
                                viewModel.showToastPopup("Label tanda tangan CEO diperbarui.")
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PinVerificationDialog(
    appPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Proteksi PIN",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Konfirmasi PIN Keamanan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Masukkan PIN aplikasi My Singghara Anda untuk memverifikasi penghapusan tanda tangan resmi.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // 4 PIN Bullet Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Numeric Keypad
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    for (row in keyRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (key in row) {
                                Surface(
                                    onClick = {
                                        errorMessage = null
                                        when (key) {
                                            "C" -> enteredPin = ""
                                            "DEL" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    val newPin = enteredPin + key
                                                    enteredPin = newPin
                                                    if (newPin.length == 4) {
                                                        if (newPin == appPin) {
                                                            onSuccess()
                                                        } else {
                                                            errorMessage = "PIN salah! Silakan coba lagi."
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (key == "DEL" || key == "C") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "DEL") {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Hapus",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}
