package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.transfer.FileTransferManager
import com.example.transfer.LocalTransferItem
import com.example.ui.MainViewModel
import com.example.utils.QrCodeHelper
import com.example.web.LocalHttpServer
import kotlinx.coroutines.launch

// -------------------------------------------------------------
// LIGHT MODE PANEL PALETTE (mode gelap tidak berubah)
// -------------------------------------------------------------
private val TransferAccentDeep = Color(0xFF0369A1)
private val TransferAccentSoft = Color(0xFFE0F2FE)
private val TransferAccentBorder = Color(0xFFBAE6FD)
private val TransferHairline = Color(0xFFE2E8F0)
private val TransferSunken = Color(0xFFF1F5F9)

@Composable
private fun transferIsLight(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f

@Composable
private fun transferPanelColor(): Color =
    if (transferIsLight()) Color.White else MaterialTheme.colorScheme.surface

@Composable
private fun transferBorderColor(): Color =
    if (transferIsLight()) TransferHairline else MaterialTheme.colorScheme.outlineVariant

@Composable
private fun transferCardElevation(): Dp = if (transferIsLight()) 2.dp else 0.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferFileScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val transferManager = remember { FileTransferManager.getInstance(context) }

    val sharedFiles by transferManager.sharedFromPhone.collectAsState()
    val receivedFiles by transferManager.receivedFromPc.collectAsState()
    val activityLogs by transferManager.activityLogs.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Kirim ke PC, 1: Diterima dari PC, 2: Riwayat
    var showQrDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<LocalTransferItem?>(null) }
    var isPickingFiles by remember { mutableStateOf(false) }

    val ipAddress = remember { LocalHttpServer.getIpAddress(context) }
    val port = 8080
    val webConnectUrl = "http://$ipAddress:$port"

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = transferManager.addFilesFromPhone(uris)
                Toast.makeText(context, "$count berkas ditambahkan untuk PC", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Transfer File",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Kirim & Terima Berkas (100% Offline PC & HP ke HP)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.currentRoute = "home" }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, contentDescription = "Petunjuk & QR Code", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { transferManager.refreshReceivedFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Segarkan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SUB-MENU SWITCHER (Share Cloud vs Transfer File)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (transferIsLight()) TransferSunken
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { viewModel.currentRoute = "share" },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share (Google Drive)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    onClick = { /* Already on Transfer File */ },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Transfer File (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            // KARTU STATUS KONEKSI WEB CONNECT (OFFLINE)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (transferIsLight()) TransferAccentSoft
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (transferIsLight()) TransferAccentBorder
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (transferIsLight()) Color.White
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Web Connect PC",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "100% OFFLINE",
                                        color = Color(0xFF047857),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = webConnectUrl,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Buka alamat di atas pada browser PC",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(webConnectUrl))
                                Toast.makeText(context, "Alamat disalin ke clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin Alamat", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = "QR Code", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // TAB ROWS (Kirim ke PC, Diterima dari PC, Riwayat)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Kirim ke PC (${sharedFiles.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Diterima (${receivedFiles.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "HP ke HP",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(
                            text = "Riwayat",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // ISI TAB
            when (selectedTab) {
                0 -> SendToPcSection(
                    sharedFiles = sharedFiles,
                    onPickFiles = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onRemove = { transferManager.removeSharedFile(it.id) },
                    onClearAll = { transferManager.clearSharedFiles() },
                    onOpenFile = { transferManager.openFile(context, it) }
                )
                1 -> ReceivedFromPcSection(
                    receivedFiles = receivedFiles,
                    onOpenFile = { transferManager.openFile(context, it) },
                    onShareFile = { transferManager.shareFile(context, it) },
                    onSaveToDownloads = {
                        scope.launch {
                            val ok = transferManager.saveToPublicDownloads(it)
                            if (ok) {
                                Toast.makeText(context, "Disimpan ke folder Unduhan HP", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal menyimpan ke folder Unduhan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDelete = { itemToDelete = it }
                )
                2 -> PhoneToPhoneSection(
                    viewModel = viewModel,
                    webConnectUrl = webConnectUrl
                )
                3 -> TransferHistorySection(
                    logs = activityLogs,
                    onClearLogs = { transferManager.clearLogs() }
                )
            }
        }
    }

    // DIALOG KONFIRMASI HAPUS
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Berkas?") },
            text = { Text("Berkas '${itemToDelete?.name}' akan dihapus dari memori HP.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = itemToDelete
                        itemToDelete = null
                        if (item != null) {
                            scope.launch {
                                transferManager.deleteReceivedFile(item.id)
                                Toast.makeText(context, "Berkas dihapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG QR CODE & PETUNJUK
    if (showQrDialog) {
        WebConnectQrDialog(
            url = webConnectUrl,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun SendToPcSection(
    sharedFiles: List<LocalTransferItem>,
    onPickFiles: () -> Unit,
    onRemove: (LocalTransferItem) -> Unit,
    onClearAll: () -> Unit,
    onOpenFile: (LocalTransferItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tombol Pilih Berkas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickFiles,
                modifier = Modifier.weight(1f).height(46.dp).testTag("pick_files_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pilih Berkas dari HP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            if (sharedFiles.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Bersihkan Semua", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (sharedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                if (transferIsLight()) TransferAccentSoft
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        text = "Belum Ada Berkas yang Dibagikan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pilih berkas dari HP Anda agar dapat langsung diunduh oleh browser PC di menu 'Transfer File' Web Connect secara offline.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Text(
                text = "Berkas Siap Diunduh di PC (${sharedFiles.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sharedFiles, key = { it.id }) { item ->
                    FileItemCard(
                        item = item,
                        isReceived = false,
                        onOpen = { onOpenFile(item) },
                        onAction = { onRemove(item) },
                        actionIcon = Icons.Default.Close,
                        actionDescription = "Hapus dari Daftar Berbagi"
                    )
                }
            }
        }
    }
}

@Composable
fun ReceivedFromPcSection(
    receivedFiles: List<LocalTransferItem>,
    onOpenFile: (LocalTransferItem) -> Unit,
    onShareFile: (LocalTransferItem) -> Unit,
    onSaveToDownloads: (LocalTransferItem) -> Unit,
    onDelete: (LocalTransferItem) -> Unit
) {
    if (receivedFiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            if (transferIsLight()) TransferAccentSoft
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    text = "Belum Ada Berkas Diterima dari PC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Buka Web Connect di browser PC Anda, masuk ke sub menu 'Transfer File', lalu seret atau pilih berkas yang ingin dikirim ke HP ini.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(receivedFiles, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (transferIsLight()) transferPanelColor()
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (transferIsLight()) transferBorderColor()
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (transferIsLight()) TransferAccentSoft
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getFileIconVector(item.extension),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.formattedSize} • Diterima ${FileTransferManager.formatTimestamp(item.timestamp)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Baris Aksi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onOpenFile(item) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Buka", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { onShareFile(item) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bagi", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { onSaveToDownloads(item) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simpan", fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { onDelete(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferHistorySection(
    logs: List<com.example.transfer.TransferActivityLog>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (logs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aktivitas Transfer Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClearLogs) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus Riwayat", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(
                                if (transferIsLight()) TransferAccentSoft
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Text("Belum Ada Riwayat Transfer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Kirim atau terima berkas untuk melihat catatan di sini.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (transferIsLight()) transferPanelColor()
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (transferIsLight()) transferBorderColor() else Color.Transparent
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (log.isIncoming) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else Color(0xFF0284C7).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (log.isIncoming) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (log.isIncoming) Color(0xFF10B981) else Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = log.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = FileTransferManager.formatTimestamp(log.timestamp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemCard(
    item: LocalTransferItem,
    isReceived: Boolean,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (transferIsLight()) transferPanelColor()
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (transferIsLight()) transferBorderColor()
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (transferIsLight()) TransferAccentSoft
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (transferIsLight()) TransferAccentBorder else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIconVector(item.extension),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (transferIsLight()) TransferAccentSoft
                            else MaterialTheme.colorScheme.secondaryContainer,
                            border = if (transferIsLight()) BorderStroke(1.dp, TransferAccentBorder) else null
                        ) {
                            Text(
                                text = item.extension.ifEmpty { "BERKAS" },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (transferIsLight()) TransferAccentDeep
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = item.formattedSize,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onAction) {
                Icon(actionIcon, contentDescription = actionDescription, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun WebConnectQrDialog(
    url: String,
    onDismiss: () -> Unit
) {
    val qrBitmap: Bitmap? = remember(url) {
        QrCodeHelper.generateQrBitmap(url, size = 400)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = transferPanelColor()),
            border = BorderStroke(
                1.dp,
                if (transferIsLight()) transferBorderColor() else Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Akses Web Connect Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code Web Connect",
                            modifier = Modifier.size(180.dp).padding(8.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (transferIsLight()) TransferAccentSoft
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    border = if (transferIsLight()) BorderStroke(1.dp, TransferAccentBorder) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = url,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Petunjuk Penggunaan:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("1. Pastikan PC dan HP terhubung ke Wi-Fi yang sama (atau hotspot HP).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2. Buka browser di PC (Chrome/Edge/Firefox) lalu ketik alamat di atas atau domain http://mysingghara.com:8080.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("3. Pilih sub menu 'Transfer File' di PC untuk kirim dan terima berkas 100% offline tanpa internet.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB "HP ke HP" — dua HP My Singghara bertukar berkas 100% offline.
// Sisi terima tetap memakai LocalHttpServer yang sama; HP pengirim bertindak
// sebagai klien HTTP (lihat P2pTransferManager).
// -----------------------------------------------------------------------------
@Composable
fun PhoneToPhoneSection(
    viewModel: MainViewModel,
    webConnectUrl: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val p2p = remember { com.example.transfer.P2pTransferManager.getInstance(context) }

    val peers by p2p.peers.collectAsState()
    val isScanning by p2p.isScanning.collectAsState()
    val jobs by p2p.jobs.collectAsState()
    val listening by p2p.responderRunning.collectAsState()

    var showScanner by remember { mutableStateOf(false) }
    var showPairQr by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var manualAddress by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf("") }
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var remoteRefreshTick by remember { mutableStateOf(0) }
    var remoteFiles by remember { mutableStateOf<List<com.example.transfer.P2pRemoteFile>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var remoteNotice by remember { mutableStateOf("") }

    val serverRunning = LocalHttpServer.activeInstance?.isServerRunning() == true
    val selectedPeer = peers.firstOrNull { it.id == selectedPeerId }

    val sendPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val peer = selectedPeer
        if (uris.isEmpty() || peer == null) return@rememberLauncherForActivityResult
        scope.launch {
            var okCount = 0
            uris.forEach { uri ->
                if (p2p.sendUriToPeer(peer, uri)) okCount++
            }
            Toast.makeText(
                context,
                if (okCount > 0) "$okCount berkas dikirim ke ${peer.label}" else "Gagal mengirim ke ${peer.label}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        p2p.startResponder()
        if (peers.isEmpty()) p2p.scan()
    }

    LaunchedEffect(selectedPeerId, remoteRefreshTick) {
        val peer = peers.firstOrNull { it.id == selectedPeerId }
        if (peer == null) {
            remoteFiles = emptyList()
            return@LaunchedEffect
        }
        isLoadingRemote = true
        remoteNotice = ""
        val (_, files) = p2p.remoteState(peer)
        remoteFiles = files
        if (files.isEmpty()) {
            remoteNotice = "HP ${peer.label} belum membagikan berkas. Di HP itu buka Transfer File tab \"Kirim ke PC\" lalu pilih berkas."
        }
        isLoadingRemote = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // KARTU "HP INI SIAP MENERIMA"
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (transferIsLight()) TransferAccentSoft
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            ),
            border = BorderStroke(
                1.dp,
                if (transferIsLight()) TransferAccentBorder else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (transferIsLight()) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.dp, if (transferIsLight()) TransferAccentBorder else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = android.os.Build.MODEL ?: "HP Ini",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = webConnectUrl,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (listening && serverRunning)
                            if (transferIsLight()) Color.White else MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = if (listening && serverRunning) "Siap" else "Belum aktif",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (listening && serverRunning)
                                if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (!serverRunning) {
                    Text(
                        text = "HP lain hanya bisa mengambil/mengirim berkas kalau Web Connect di HP ini menyala (server port 8080).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp
                    )
                    Button(
                        onClick = { viewModel.currentRoute = "web_connect" },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aktifkan Web Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showPairQr = true },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QR Pasangan", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(webConnectUrl))
                            Toast.makeText(context, "Alamat HP disalin", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin", fontSize = 11.sp)
                    }
                }
            }
        }

        // TOMBOL PENCARIAN
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { p2p.scan() },
                enabled = !isScanning,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(if (isScanning) "Mencari HP..." else "Cari HP Terdekat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { showScanner = true },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(7.dp))
                Text("Pindai QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        TextButton(onClick = { showManual = !showManual }) {
            Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Isi alamat HP manual", fontSize = 11.sp)
        }

        AnimatedVisibility(visible = showManual) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = { manualAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("192.168.43.12 atau http://192.168.1.7:8080", fontSize = 12.sp) },
                    singleLine = true,
                    isError = manualError.isNotEmpty(),
                    supportingText = {
                        if (manualError.isNotEmpty()) Text(manualError, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                Button(
                    onClick = {
                        val parsed = parseP2pAddress(manualAddress)
                        if (parsed == null) {
                            manualError = "Alamat tidak dikenali"
                            return@Button
                        }
                        manualError = ""
                        scope.launch {
                            val id = "${parsed.first}:${parsed.second}"
                            val existing = p2p.peers.value.firstOrNull { it.id == id }
                            val candidate = existing?.copy(reachable = true) ?: com.example.transfer.P2pPeer(
                                id = id,
                                label = parsed.first,
                                ip = parsed.first,
                                port = parsed.second,
                                reachable = false
                            )
                            val verified = p2p.verifyOne(candidate)
                            if (verified != null) {
                                selectedPeerId = verified.id
                                manualAddress = ""
                                showManual = false
                            } else {
                                manualError = "Tidak ada My Singghara di alamat itu (Web Connect-nya nyala?)"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Hubungi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // DAFTAR HP TERDEKAT
        Text(
            text = if (peers.isEmpty()) "HP My Singghara terdekat" else "HP Terdeteksi (${peers.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (peers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (transferIsLight()) TransferSunken else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, transferBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Belum ada HP lain yang terlihat", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = "1. Kedua HP harus satu Wi-Fi, atau salah satu menyalakan hotspot yang dipakai HP lainnya.\n" +
                                "2. Di HP tujuan: buka Transfer File tab ini (Web Connect harus aktif).\n" +
                                "3. Kalau jaringan Wi-Fi memblokir perangkat antar-klien, pakai tombol QR atau isi alamat manual.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            peers.forEach { peer ->
                PeerDeviceCard(
                    peer = peer,
                    isSelected = peer.id == selectedPeerId,
                    onConnect = { selectedPeerId = peer.id },
                    onForget = {
                        p2p.forgetPeer(peer.id)
                        if (selectedPeerId == peer.id) selectedPeerId = null
                    }
                )
            }
        }

        // ISI HP YANG DIPILIH
        selectedPeer?.let { peer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (transferIsLight()) transferPanelColor() else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, transferBorderColor()),
                elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Berkas di ${peer.label}", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { remoteRefreshTick++ }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Segarkan", modifier = Modifier.size(16.dp))
                        }
                    }

                    if (isLoadingRemote) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("Mengambil daftar berkas...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (remoteNotice.isNotEmpty()) {
                        Text(remoteNotice, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    } else {
                        remoteFiles.forEach { remote ->
                            RemotePeerFileRow(
                                remote = remote,
                                onTake = {
                                    scope.launch {
                                        val saved = p2p.takeFromPeer(peer, remote)
                                        Toast.makeText(
                                            context,
                                            if (saved != null) "Disimpan di HP ini: ${saved.name}" else "Gagal mengambil ${remote.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Button(
                        onClick = { sendPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Kirim Berkas ke ${peer.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PROSES BERJALAN
        if (jobs.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sedang berjalan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                jobs.forEach { job -> P2pJobRow(job) }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismissRequest = { showScanner = false },
            onBarcodeScanned = { raw ->
                showScanner = false
                val parsed = parseP2pAddress(raw)
                if (parsed == null) {
                    Toast.makeText(context, "QR bukan alamat My Singghara", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val id = "${parsed.first}:${parsed.second}"
                        val existing = p2p.peers.value.firstOrNull { it.id == id }
                        val candidate = existing ?: com.example.transfer.P2pPeer(
                            id = id,
                            label = parsed.first,
                            ip = parsed.first,
                            port = parsed.second,
                            reachable = false
                        )
                        val verified = p2p.verifyOne(candidate)
                        if (verified != null) {
                            selectedPeerId = verified.id
                            Toast.makeText(context, "Terhubung ke ${verified.label}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "HP tujuan tidak menjawab", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            title = "Pindai QR HP Tujuan",
            hintText = "Arahkan ke QR \"Akses Web Connect Offline\" di HP yang akan dikirimi berkas",
            isQrSquare = true
        )
    }

    if (showPairQr) {
        P2pPairQrDialog(
            url = webConnectUrl,
            onDismiss = { showPairQr = false }
        )
    }
}

/** QR pendek untuk HP pasangan; isinya alamat Web Connect yang sama seperti untuk PC. */
@Composable
private fun P2pPairQrDialog(url: String, onDismiss: () -> Unit) {
    val qrBitmap: Bitmap? = remember(url) { QrCodeHelper.generateQrBitmap(url, size = 400) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = transferPanelColor()),
            border = BorderStroke(1.dp, if (transferIsLight()) transferBorderColor() else Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("QR HP Ini", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = Color.White,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Transfer HP ke HP",
                            modifier = Modifier.size(170.dp).padding(8.dp)
                        )
                    }
                }
                Text(url, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Pindai QR ini dari HP lain lewat tombol \"Pindai QR\" di tab HP ke HP. Satu Wi-Fi / hotspot yang sama, tanpa internet.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                    Text("Tutup")
                }
            }
        }
    }
}

@Composable
private fun PeerDeviceCard(
    peer: com.example.transfer.P2pPeer,
    isSelected: Boolean,
    onConnect: () -> Unit,
    onForget: () -> Unit
) {
    Card(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                if (transferIsLight()) TransferAccentSoft else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else if (transferIsLight()) transferPanelColor() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else transferBorderColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = transferCardElevation())
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (transferIsLight()) TransferAccentSoft else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${peer.ip}:${peer.port}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = if (peer.reachable)
                    if (transferIsLight()) Color.White else MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (peer.reachable) "Online" else "Riwayat",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (peer.reachable)
                        if (transferIsLight()) TransferAccentDeep else MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            IconButton(onClick = onForget, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Lupakan", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RemotePeerFileRow(
    remote: com.example.transfer.P2pRemoteFile,
    onTake: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (transferIsLight()) TransferSunken else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = getFileIconVector(remote.extension),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = remote.name,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = remote.formattedSize,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(
            onClick = onTake,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Ambil", fontSize = 11.sp)
        }
    }
}

@Composable
private fun P2pJobRow(job: com.example.transfer.P2pJob) {
    val isOut = job.direction == "out"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (transferIsLight()) TransferSunken else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = if (isOut) Icons.Default.Upload else Icons.Default.Download,
                contentDescription = null,
                tint = if (job.state == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = job.fileName,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isOut) "ke ${job.peerLabel}" else "dari ${job.peerLabel}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (job.state == "error") {
            Text(job.message.ifBlank { "Gagal" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
        } else if (job.state == "done") {
            Text("Selesai • ${FileTransferManager.formatFileSize(job.totalBytes)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        } else {
            LinearProgressIndicator(
                progress = { if (job.totalBytes > 0) job.percent / 100f else 0f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            )
            Text(
                text = "${job.percent}% • ${FileTransferManager.formatFileSize(job.doneBytes)} / ${FileTransferManager.formatFileSize(job.totalBytes)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Terima "192.168.1.9", "192.168.1.9:8080" atau "http://192.168.1.9:8080"
 * (hasil QR Web Connect) menjadi pasangan IP + port.
 */
private fun parseP2pAddress(raw: String): Pair<String, Int>? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (value.contains("SINGGHARA_AUTH:")) {
        // QR lama berisi token; alamat tetap ada di bagian http://... bila digabung.
        val idx = value.indexOf("http")
        if (idx >= 0) value = value.substring(idx) else return null
    }
    value = value.removePrefix("http://").removePrefix("https://").trimEnd('/')
    val pathCut = value.indexOf('/')
    if (pathCut >= 0) value = value.substring(0, pathCut)
    if (value.isEmpty()) return null
    val hostPart = if (value.contains(":")) value.substringBefore(":") else value
    val portPart = if (value.contains(":")) value.substringAfter(":").toIntOrNull() ?: 8080 else 8080
    if (hostPart.isEmpty()) return null
    return hostPart to portPart
}

fun getFileIconVector(ext: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (ext.uppercase()) {
        "APK" -> Icons.Default.Android
        "PDF" -> Icons.Default.PictureAsPdf
        "JPG", "JPEG", "PNG", "WEBP", "GIF" -> Icons.Default.Image
        "MP3", "WAV", "M4A", "FLAC", "AAC" -> Icons.Default.Audiotrack
        "MP4", "MKV", "AVI", "MOV" -> Icons.Default.VideoFile
        "ZIP", "RAR", "7Z", "TAR" -> Icons.Default.FolderZip
        "DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX", "TXT" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}
