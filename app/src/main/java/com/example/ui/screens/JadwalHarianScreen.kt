package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.JadwalKegiatan
import com.example.ui.MainViewModel

// Helper function to get category properties
private data class CategoryConfig(val name: String, val color: Color, val icon: ImageVector)

private fun getCategoryConfig(category: String): CategoryConfig {
    return when (category.lowercase()) {
        "rutinitas pagi" -> CategoryConfig("Rutinitas Pagi", Color(0xFFFBC02D), Icons.Default.WbSunny)
        "kesehatan" -> CategoryConfig("Kesehatan", Color(0xFFE53935), Icons.Default.Favorite)
        "persiapan diri" -> CategoryConfig("Persiapan Diri", Color(0xFF0288D1), Icons.Default.Person)
        "energi" -> CategoryConfig("Energi", Color(0xFFFF9800), Icons.Default.FlashOn)
        "kebersihan" -> CategoryConfig("Kebersihan", Color(0xFF00ACC1), Icons.Default.WaterDrop)
        "produktivitas" -> CategoryConfig("Produktivitas", Color(0xFF43A047), Icons.Default.Edit)
        "istirahat" -> CategoryConfig("Istirahat", Color(0xFF5E35B1), Icons.Default.NightsStay)
        "me time" -> CategoryConfig("Me Time", Color(0xFFD81B60), Icons.Default.Face)
        "evaluasi" -> CategoryConfig("Evaluasi", Color(0xFF757575), Icons.Default.Info)
        else -> CategoryConfig(category, Color(0xFF9E9E9E), Icons.Default.Schedule)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JadwalHarianScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val jadwalList by viewModel.jadwalList.collectAsState()

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(2000)
            successMessage = null
        }
    }

    // Auto reset at 00.00 (checking if lastCheckedDate is from a previous day)
    LaunchedEffect(jadwalList) {
        val outOfDateItems = jadwalList.filter { it.isCompleted && it.lastCheckedDate != todayStr }
        if (outOfDateItems.isNotEmpty()) {
            outOfDateItems.forEach { item ->
                val resetItem = item.copy(isCompleted = false, lastCheckedDate = "")
                viewModel.saveJadwal(resetItem)
            }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<JadwalKegiatan?>(null) }
    var itemToEdit by remember { mutableStateOf<JadwalKegiatan?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Main Content Area
            if (jadwalList.isEmpty()) {
                // Empty state header with modern Add button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tabel Rencana Jadwal",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { showAddDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Jadwal",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Elegant Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada Jadwal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ketuk tombol + di atas untuk merancang aktivitas harian baru Anda.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Table Title Header with '+' button next to it as requested!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tabel Rencana Jadwal",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Elegant add icon button next to title
                    FilledTonalIconButton(
                        onClick = { showAddDialog = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Jadwal",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Progress Bar Section
                if (jadwalList.isNotEmpty()) {
                    val completedCount = jadwalList.count { it.isCompleted }
                    val totalCount = jadwalList.size
                    val progressFraction = if (totalCount > 0) completedCount.toFloat() / totalCount else 0.0f
                    val percentage = (progressFraction * 100).toInt()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Progres Rencana Hari Ini",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$completedCount/$totalCount Selesai ($percentage%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = progressFraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(100.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        }
                    }
                }

                // Clean Structured Table with ALL BORDERS
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(
                            modifier = Modifier
                                .width(615.dp)
                                .fillMaxHeight()
                        ) {
                            // 1. Table Header Row with borders
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CEK",
                                    modifier = Modifier
                                        .width(50.dp)
                                        .padding(vertical = 12.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )

                                // Vertical Border Line
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                Text(
                                    text = "WAKTU",
                                    modifier = Modifier
                                        .width(100.dp)
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                
                                // Vertical Border Line
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                Text(
                                    text = "AKTIVITAS",
                                    modifier = Modifier
                                        .width(230.dp)
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Vertical Border Line
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                Text(
                                    text = "KATEGORI",
                                    modifier = Modifier
                                        .width(140.dp)
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Vertical Border Line
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                // Action / edit / delete Column placeholder
                                Text(
                                    text = "AKSI",
                                    modifier = Modifier
                                        .width(95.dp)
                                        .padding(vertical = 12.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.height(1.dp))

                        // 2. Table Data Rows with borders
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(jadwalList) { item ->
                                val catConfig = getCategoryConfig(item.kategori)
                                val rowBgColor by animateColorAsState(
                                    targetValue = if (item.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.Transparent,
                                    animationSpec = tween(durationMillis = 300),
                                    label = "rowBgColor"
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(rowBgColor),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // CEK (Checkbox) Column
                                    Box(
                                        modifier = Modifier
                                            .width(50.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Checkbox(
                                            checked = item.isCompleted,
                                            onCheckedChange = { checked ->
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val updatedItem = item.copy(
                                                    isCompleted = checked,
                                                    lastCheckedDate = if (checked) todayStr else ""
                                                )
                                                viewModel.saveJadwal(updatedItem)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier.scale(0.85f)
                                        )
                                    }

                                    // Vertical Border Line
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                    // Time Column (with border)
                                    val timeAlpha by animateFloatAsState(
                                        targetValue = if (item.isCompleted) 0.5f else 1.0f,
                                        animationSpec = tween(durationMillis = 350),
                                        label = "timeAlpha"
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .padding(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = timeAlpha * 0.7f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = item.waktu,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = timeAlpha),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // Vertical Border Line
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                    // Activity Column (with border)
                                    Box(
                                        modifier = Modifier
                                            .width(230.dp)
                                            .padding(8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        val strikeProgress by animateFloatAsState(
                                            targetValue = if (item.isCompleted) 1f else 0f,
                                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                            label = "strikethrough"
                                        )
                                        val textAlpha by animateFloatAsState(
                                            targetValue = if (item.isCompleted) 0.45f else 1.0f,
                                            animationSpec = tween(durationMillis = 350),
                                            label = "textAlpha"
                                        )
                                        val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                                        Text(
                                            text = item.kegiatan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.drawWithContent {
                                                drawContent()
                                                if (strikeProgress > 0f) {
                                                    val y = size.height / 2f
                                                     drawLine(
                                                         color = lineColor,
                                                         start = Offset(0f, y),
                                                         end = Offset(size.width * strikeProgress, y),
                                                         strokeWidth = 1.5.dp.toPx()
                                                     )
                                                }
                                            }
                                        )
                                    }

                                    // Vertical Border Line
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                    // Category Badge Column (with border)
                                    Box(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(
                                                    color = catConfig.color.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(100.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = catConfig.icon,
                                                contentDescription = null,
                                                tint = catConfig.color,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = catConfig.name,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = catConfig.color,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Vertical Border Line
                                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))

                                    // Action Column (Edit and Delete)
                                    Row(
                                        modifier = Modifier
                                            .width(95.dp)
                                            .fillMaxHeight(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { itemToEdit = item },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Jadwal",
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { itemToDelete = item },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Jadwal",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), modifier = Modifier.height(1.dp))
                            }
                        }
                    }
                }
            }
        }
        }

        // Beautiful Floating Success Alert/Popup
        AnimatedVisibility(
            visible = successMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 350.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = successMessage ?: "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        }
    }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Hapus Jadwal Kegiatan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus kegiatan '${itemToDelete?.kegiatan}' dari rencana harian Anda?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemToDelete?.let { viewModel.deleteJadwal(it) }
                        itemToDelete = null
                        successMessage = "Jadwal berhasil dihapus!"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Popup input dialog
    if (showAddDialog) {
        AddJadwalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { waktu, kegiatan, kategori ->
                val isDuplicate = jadwalList.any { 
                    it.waktu.trim() == waktu.trim() && 
                    it.kegiatan.trim().equals(kegiatan.trim(), ignoreCase = true) 
                }
                if (isDuplicate) {
                    viewModel.showToastPopup("Aktivitas dengan waktu yang sama sudah terdaftar!")
                } else {
                    val newJadwal = JadwalKegiatan(
                        waktu = waktu,
                        kegiatan = kegiatan,
                        kategori = kategori
                    )
                    viewModel.saveJadwal(newJadwal)
                    showAddDialog = false
                    successMessage = "Jadwal berhasil disimpan!"
                }
            }
        )
    }

    // Popup edit dialog
    if (itemToEdit != null) {
        AddJadwalDialog(
            initialJadwal = itemToEdit,
            onDismiss = { itemToEdit = null },
            onConfirm = { waktu, kegiatan, kategori ->
                itemToEdit?.let { existing ->
                    val isDuplicate = jadwalList.any { 
                        it.id != existing.id &&
                        it.waktu.trim() == waktu.trim() && 
                        it.kegiatan.trim().equals(kegiatan.trim(), ignoreCase = true) 
                    }
                    if (isDuplicate) {
                        viewModel.showToastPopup("Aktivitas dengan waktu yang sama sudah terdaftar!")
                    } else {
                        val updatedJadwal = existing.copy(
                            waktu = waktu,
                            kegiatan = kegiatan,
                            kategori = kategori
                        )
                        viewModel.saveJadwal(updatedJadwal)
                        successMessage = "Jadwal berhasil diperbarui!"
                        itemToEdit = null
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddJadwalDialog(
    initialJadwal: JadwalKegiatan? = null,
    onDismiss: () -> Unit,
    onConfirm: (waktu: String, kegiatan: String, kategori: String) -> Unit
) {
    val initialStart = initialJadwal?.waktu?.split(" - ")?.getOrNull(0) ?: "07.00"
    val initialEnd = initialJadwal?.waktu?.split(" - ")?.getOrNull(1) ?: "08.00"

    var startTime by remember { mutableStateOf(initialStart) }
    var endTime by remember { mutableStateOf(initialEnd) }
    var activityName by remember { mutableStateOf(initialJadwal?.kegiatan ?: "") }
    
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Categories list
    val categories = listOf(
        "rutinitas pagi",
        "kesehatan",
        "persiapan diri",
        "energi",
        "kebersihan",
        "produktivitas",
        "istirahat",
        "me time",
        "evaluasi"
    )
    var selectedCategory by remember { mutableStateOf(initialJadwal?.kategori ?: categories[0]) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header of dialog
                Text(
                    text = if (initialJadwal != null) "Edit Jadwal Kegiatan" else "Tambah Jadwal Baru",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 1. SELECT WAKTU (Interactive Clickable Dropdown)

                // Interactive Clock Visual Display Box
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Time Selector Box
                        Box {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showStartPicker = true }
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "MULAI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = startTime,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        // End Time Selector Box
                        Box {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showEndPicker = true }
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "SELESAI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = endTime,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. KEGIATAN / AKTIVITAS TEXTFIELD
                Text(
                    text = "Aktivitas / Kegiatan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    placeholder = { Text("Masukkan nama kegiatan...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    maxLines = 2
                )

                // 3. SELECT KATEGORI
                Text(
                    text = "Kategori Kegiatan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    val activeCatConfig = getCategoryConfig(selectedCategory)
                    OutlinedCard(
                        onClick = { categoryDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                    imageVector = activeCatConfig.icon,
                                    contentDescription = null,
                                    tint = activeCatConfig.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeCatConfig.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeCatConfig.color
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 280.dp)
                    ) {
                        categories.forEach { category ->
                            val config = getCategoryConfig(category)
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = config.icon,
                                        contentDescription = null,
                                        tint = config.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = config.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = config.color
                                    )
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 4. ACTION BUTTONS (BATAL & SIMPAN)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Batal", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (activityName.trim().isEmpty()) {
                                return@Button
                            }
                            val formattedRange = "$startTime - $endTime"
                            onConfirm(formattedRange, activityName, selectedCategory)
                        },
                        enabled = activityName.trim().isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simpan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        TimePicker1MinDialog(
            title = "Pilih Waktu Mulai",
            initialTime = startTime,
            onDismiss = { showStartPicker = false },
            onConfirm = { selectedTime ->
                startTime = selectedTime
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        TimePicker1MinDialog(
            title = "Pilih Waktu Selesai",
            initialTime = endTime,
            onDismiss = { showEndPicker = false },
            onConfirm = { selectedTime ->
                endTime = selectedTime
                showEndPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePicker1MinDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(".")
    val selectedHour = parts.getOrNull(0)?.toIntOrNull() ?: 7
    val selectedMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    // Ensure the initial hour is in the 0..23 range for standard clock dial
    val initialHour = if (selectedHour == 24) 0 else selectedHour

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = selectedMinute,
        is24Hour = true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Beautiful interactive dial clock picker from Material 3
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        periodSelectorBorderColor = MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batal", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            // If they select 00.00 as the end time, map it to 24.00 to represent the end of day
                            val isEndTimeZero = timePickerState.hour == 0 && timePickerState.minute == 0
                            val formattedTime = if (isEndTimeZero && title.contains("Selesai", ignoreCase = true)) {
                                "24.00"
                            } else {
                                String.format("%02d.%02d", timePickerState.hour, timePickerState.minute)
                            }
                            onConfirm(formattedTime)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pilih", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
