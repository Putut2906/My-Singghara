package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import com.example.ui.MainViewModel
import com.example.ui.components.StoragePermissionBox
import coil.compose.AsyncImage

@Composable
fun ProfilScreen(
    viewModel: MainViewModel,
    onEditProfileClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf<String?>(null) }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showPinDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val sharedPrefs = remember { context.getSharedPreferences("mysingghara_prefs", android.content.Context.MODE_PRIVATE) }
    val defaultUrl = "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec"
    var appsScriptUrl by remember {
        val saved = sharedPrefs.getString("apps_script_url", "") ?: ""
        mutableStateOf(if (saved.trim().isEmpty()) defaultUrl else saved)
    }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showScriptTutorial by remember { mutableStateOf(false) }
    var selectedPrayerForDialog by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.fetchCurrentLocation(context)
            val today = java.util.Calendar.getInstance()
            val formatStr = String.format("%02d-%02d-%04d", today.get(java.util.Calendar.DAY_OF_MONTH), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.YEAR))
            viewModel.loadLiveAladhanHijri(formatStr)
            viewModel.showToastPopup("Izin lokasi aktif! Mengambil koordinat GPS & sinkronisasi jadwal sholat...")
        } else {
            viewModel.showToastPopup("Izin ditolak. Menggunakan kota standar WIB/WITA/WIT.")
        }
    }

    val folderPickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.saveSelectedFolderUri(uri.toString())
                statusMessage = "Folder terpilih berhasil disimpan: $uri"
                isStatusError = false
                viewModel.showToastPopup("Folder SAF diaktifkan dan disimpan!")
            } catch (e: Exception) {
                statusMessage = "Gagal mengambil hak akses: ${e.message}"
                isStatusError = true
            }
        }
    }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .testTag("profil_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // STATUS TOAST AREA
        if (statusMessage.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isStatusError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isStatusError) Icons.Default.Error else Icons.Default.CheckCircle,
                        null,
                        tint = if (isStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = if (isStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { statusMessage = "" }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ================= NAVIGASI LIST MENU PROFILE =================
        when (activeSection) {
            null -> {
                val avatarGradients = remember {
                    listOf(
                        Brush.linearGradient(listOf(Color(0xFF6200EE), Color(0xFFBB86FC))),
                        Brush.linearGradient(listOf(Color(0xFF03DAC6), Color(0xFF018786))),
                        Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800))),
                        Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFF48FB1))),
                        Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF81C784))),
                        Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF64B5F6))),
                        Brush.linearGradient(listOf(Color(0xFFFFC107), Color(0xFFFFD54F))),
                        Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFCE93D8)))
                    )
                }
                val avatarIcons = remember {
                    listOf(
                        Icons.Default.Person,
                        Icons.Default.Face,
                        Icons.Default.AccountCircle,
                        Icons.Default.Star,
                        Icons.Default.Favorite,
                        Icons.Default.Pets,
                        Icons.Default.EmojiEmotions,
                        Icons.Default.Psychology
                    )
                }

                // Profile Header (Without card wrapper)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar with Settings Icon at Top Right Corner
                    Box(
                        contentAlignment = Alignment.TopEnd
                    ) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .then(
                                    if (viewModel.profileAvatarIndex >= 0) {
                                        Modifier.background(avatarGradients.getOrElse(viewModel.profileAvatarIndex) { avatarGradients[0] })
                                    } else {
                                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                    }
                                )
                                .clickable {
                                    onEditProfileClick?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.profileAvatarIndex == -1 && viewModel.profileImageUri.isNotEmpty()) {
                                AsyncImage(
                                    model = viewModel.profileImageUri,
                                    contentDescription = "Foto Profil",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val selectedIcon = avatarIcons.getOrElse(viewModel.profileAvatarIndex) { avatarIcons[0] }
                                Icon(
                                    imageVector = selectedIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        // Icon Pengaturan di pojok kanan atas foto avatar
                        Box(
                            modifier = Modifier
                                .offset(x = 6.dp, y = (-4).dp)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    onEditProfileClick?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nama Pengguna
                    Text(
                        text = viewModel.profileName.ifEmpty { "Pengguna Setia" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            onEditProfileClick?.invoke()
                        }
                    )
                }

                Text(
                    text = "MENU UTAMA PROFIL",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(start = 4.dp, top = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        MenuItem(
                            icon = Icons.Default.Lock,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Keamanan dan Data",
                            subtitle = "App Lock, Backup & Restore, & GPS",
                            onClick = { activeSection = "keamanan_dan_data" }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Devices,
                            iconTint = Color(0xFF00A884),
                            title = "Web Connect (Akses PC)",
                            subtitle = "Hubungkan & kelola aplikasi dari browser Laptop / PC",
                            onClick = { viewModel.currentRoute = "web_connect" }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Cloud,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Pengaturan Share",
                            subtitle = "Seting Share & Bantuan",
                            onClick = { activeSection = "pengaturan_share" }
                        )
                    }
                }
            }
            "pengaturan_share" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeSection = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kembali ke Menu Utama",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pengaturan Share",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        MenuItem(
                            icon = Icons.Default.Settings,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Seting Share",
                            subtitle = "Konfigurasi URL Google Apps Script",
                            onClick = { showUrlDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Help,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Bantuan",
                            subtitle = "Petunjuk dan kode Google Apps Script",
                            onClick = { showScriptTutorial = true }
                        )
                    }
                }
            }
            "keamanan_dan_data" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeSection = null }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kembali ke Menu Utama",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Keamanan dan Data",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column {
                        // State for Popup Dialogs
                        var showBackupDialog by remember { mutableStateOf(false) }
                        var showGpsDialog by remember { mutableStateOf(false) }

                        // a. Sub-Menu: Keamanan & Penguncian (App Lock)
                        MenuItem(
                            icon = Icons.Default.Lock,
                            title = "Keamanan & Penguncian",
                            subtitle = "Kunci biometrik (Sidik Jari/Wajah) atau PIN",
                            onClick = { showAppLockDialog = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // b. Sub-Menu: Cadangkan & Pulihkan (Backup & Restore)
                        val hasFolderForBackup = viewModel.safFolderUriStr.isNotEmpty()
                        
                        MenuItem(
                            icon = Icons.Default.Backup,
                            title = "Cadangkan & Pulihkan (Backup & Restore)",
                            subtitle = "Ekspor, Pulihkan, & Manajemen Penyimpanan",
                            onClick = { showBackupDialog = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // c. Sub-Menu: Sinkronisasi & Akses Lokasi (GPS)
                        val isGpsActive = viewModel.userLatitude != null
                        
                        MenuItem(
                            icon = Icons.Default.LocationOn,
                            title = "Sinkronisasi & Akses Lokasi (GPS)",
                            subtitle = if (isGpsActive) "GPS Aktif (${String.format(java.util.Locale.US, "%.4f", viewModel.userLatitude)}, ${String.format(java.util.Locale.US, "%.4f", viewModel.userLongitude)})" else null,
                            onClick = { showGpsDialog = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        // POPUP DIALOG: APP LOCK
                        if (showAppLockDialog) {
                            AlertDialog(
                                onDismissRequest = { showAppLockDialog = false },
                                title = {
                                    Text(
                                        text = "Keamanan & Penguncian",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Status Kunci Aplikasi",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = if (viewModel.isAppLockEnabled) "Aktif (Biometrik / PIN)" else "Nonaktif",
                                                    fontSize = 11.sp,
                                                    color = if (viewModel.isAppLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = viewModel.isAppLockEnabled,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked) {
                                                        if (viewModel.appPin.isEmpty()) {
                                                            showAppLockDialog = false
                                                            showPinSetupDialog = true
                                                        } else {
                                                            viewModel.saveAppLockEnabled(true)
                                                            viewModel.showToastPopup("Kunci Aplikasi berhasil diaktifkan secara aman!")
                                                        }
                                                    } else {
                                                        if (viewModel.appPin.isNotEmpty()) {
                                                            showAppLockDialog = false
                                                            showPinVerifyDialog = true
                                                        } else {
                                                            viewModel.saveAppLockEnabled(false)
                                                            viewModel.showToastPopup("Kunci Aplikasi dinonaktifkan.")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (viewModel.appPin.isEmpty()) {
                                                Button(
                                                    onClick = { showAppLockDialog = false; showPinSetupDialog = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Pin, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Atur PIN Baru", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            
                                            if (viewModel.appPin.isNotEmpty()) {
                                                Button(
                                                    onClick = { showAppLockDialog = false; showPinChangeDialog = true },
                                                    modifier = Modifier.weight(1f).testTag("change_app_pin_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Ganti PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }

                                                Button(
                                                    onClick = { showAppLockDialog = false; showPinVerifyDialog = true },
                                                    modifier = Modifier.weight(1f).testTag("delete_app_pin_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Hapus PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showAppLockDialog = false }) {
                                        Text("Tutup")
                                    }
                                }
                            )
                        }

                        // DIALOG KONFIRMASI HAPUS PIN
                        if (showPinDeleteConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showPinDeleteConfirmDialog = false; showAppLockDialog = true },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .shadow(4.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                title = {
                                    Text(
                                        text = "Konfirmasi Hapus PIN",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Apakah Anda yakin ingin menghapus PIN Kunci Aplikasi? Setelah PIN dihapus, penguncian aplikasi otomatis akan dinonaktifkan.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.saveAppPin("")
                                            viewModel.saveAppLockEnabled(false)
                                            showPinDeleteConfirmDialog = false
                                            showAppLockDialog = true
                                            viewModel.showToastPopup("PIN & Kunci Aplikasi berhasil dihapus.")
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Ya, Hapus PIN", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    OutlinedButton(
                                        onClick = { showPinDeleteConfirmDialog = false; showAppLockDialog = true },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Batal")
                                    }
                                }
                            )
                        }

                        // DIALOG VERIFIKASI PIN SEBELUM HAPUS / NONAKTIFKAN KUNCI
                        if (showPinVerifyDialog) {
                            var verifyPinInput by remember { mutableStateOf("") }
                            var errorMessage by remember { mutableStateOf("") }

                            LaunchedEffect(verifyPinInput) {
                                if (verifyPinInput.length == 4) {
                                    delay(200)
                                    if (verifyPinInput == viewModel.appPin) {
                                        errorMessage = ""
                                        showPinVerifyDialog = false
                                        showPinDeleteConfirmDialog = true
                                    } else {
                                        errorMessage = "PIN yang Anda masukkan salah!"
                                        verifyPinInput = ""
                                    }
                                }
                            }

                            Dialog(onDismissRequest = { showPinVerifyDialog = false; showAppLockDialog = true }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Header Icon & Title
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = "Verifikasi PIN Kunci",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )

                                        Text(
                                            text = "Masukkan PIN saat ini untuk menonaktifkan kunci aplikasi",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Active Step Status Container
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                            shape = RoundedCornerShape(16.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    for (i in 0 until 4) {
                                                        val isFilled = i < verifyPinInput.length
                                                        Box(
                                                            modifier = Modifier
                                                                .size(18.dp)
                                                                .shadow(if (isFilled) 4.dp else 2.dp, CircleShape)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isFilled) MaterialTheme.colorScheme.primary
                                                                    else MaterialTheme.colorScheme.surface
                                                                )
                                                                .border(
                                                                    width = if (isFilled) 0.dp else 1.5.dp,
                                                                    color = if (isFilled) Color.Unspecified else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (errorMessage.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = errorMessage,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Keypad Grid Container
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                val buttonRows = listOf(
                                                    listOf("1", "2", "3"),
                                                    listOf("4", "5", "6"),
                                                    listOf("7", "8", "9")
                                                )

                                                for (row in buttonRows) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        for (num in row) {
                                                            Box(
                                                                modifier = Modifier.weight(1f),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                SetupKeypadButton(
                                                                    text = num,
                                                                    modifier = Modifier.size(60.dp)
                                                                ) {
                                                                    if (verifyPinInput.length < 4) verifyPinInput += num
                                                                    errorMessage = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(modifier = Modifier.weight(1f))
                                                    Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        SetupKeypadButton(
                                                            text = "0",
                                                            modifier = Modifier.size(60.dp)
                                                        ) {
                                                            if (verifyPinInput.length < 4) verifyPinInput += "0"
                                                            errorMessage = ""
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Surface(
                                                            onClick = {
                                                                if (verifyPinInput.isNotEmpty()) verifyPinInput = verifyPinInput.dropLast(1)
                                                                errorMessage = ""
                                                            },
                                                            modifier = Modifier
                                                                .size(60.dp)
                                                                .shadow(elevation = 6.dp, shape = CircleShape),
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.surface
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Backspace,
                                                                    contentDescription = "Hapus",
                                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        TextButton(
                                            onClick = { showPinVerifyDialog = false; showAppLockDialog = true },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "Batal",
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // POPUP DIALOG: BACKUP & RESTORE
                        if (showBackupDialog) {
                            AlertDialog(
                                onDismissRequest = { showBackupDialog = false },
                                title = {
                                    Text(
                                        text = "Cadangkan & Pulihkan (Backup & Restore)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (hasFolderForBackup) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) 
                                                                 else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (hasFolderForBackup) Icons.Default.CheckCircle else Icons.Default.FolderOff,
                                                    contentDescription = null,
                                                    tint = if (hasFolderForBackup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (hasFolderForBackup) "Siap: ${Uri.parse(viewModel.safFolderUriStr).path ?: viewModel.safFolderUriStr}" else "Silakan pilih direktori default lokal Anda",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = if (hasFolderForBackup) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                                    maxLines = 2,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                        
                                        Button(
                                            onClick = {
                                                try {
                                                    viewModel.preventAutoLock = true
                                                    folderPickLauncher.launch(null)
                                                } catch (e: Exception) {
                                                    viewModel.preventAutoLock = false
                                                    viewModel.showToastPopup("Gagal meluncurkan picker SAF: ${e.message}")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("select_saf_folder_button"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (hasFolderForBackup) "Ubah Folder" else "Pilih Folder", fontSize = 12.sp)
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )

                                        Text(
                                            text = "Cadangkan & Pulihkan Berkas Data",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        if (!hasFolderForBackup) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Silakan pilih folder terlebih dahulu di atas sebelum melakukan cadangan (backup).",
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.exportBackupSAF(context) { msg, success ->
                                                            statusMessage = msg
                                                            isStatusError = !success
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("saf_export_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Icon(Icons.Default.Backup, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Ekspor", fontSize = 12.sp)
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.importBackupSAF(context) { msg, success ->
                                                            statusMessage = msg
                                                            isStatusError = !success
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("saf_import_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                                ) {
                                                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Impor", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showBackupDialog = false }) {
                                        Text("Tutup")
                                    }
                                }
                            )
                        }

                        // POPUP DIALOG: GPS
                        if (showGpsDialog) {
                            AlertDialog(
                                onDismissRequest = { showGpsDialog = false },
                                title = {
                                    Text(
                                        text = "Sinkronisasi & Akses Lokasi (GPS)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Status Akses Lokasi",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = if (isGpsActive) "GPS Aktif (${String.format(java.util.Locale.US, "%.4f", viewModel.userLatitude)}, ${String.format(java.util.Locale.US, "%.4f", viewModel.userLongitude)})" else "GPS Nonaktif",
                                                    fontSize = 11.sp,
                                                    color = if (isGpsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = isGpsActive,
                                                onCheckedChange = { isChecked ->
                                                    if (isChecked) {
                                                        locationPermissionLauncher.launch(
                                                            arrayOf(
                                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                            )
                                                        )
                                                    } else {
                                                        viewModel.userLatitude = null
                                                        viewModel.userLongitude = null
                                                        viewModel.showToastPopup("GPS dinonaktifkan secara manual.")
                                                    }
                                                },
                                                modifier = Modifier.scale(0.8f)
                                            )
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    locationPermissionLauncher.launch(
                                                        arrayOf(
                                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                },
                                                modifier = Modifier.weight(1.2f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isGpsActive) MaterialTheme.colorScheme.secondary
                                                                    else MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                if (viewModel.isFetchingLocation) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 1.5.dp
                                                    )
                                                } else {
                                                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Ambil GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val today = java.util.Calendar.getInstance()
                                                    val viewYear = today.get(java.util.Calendar.YEAR)
                                                    val formatStr = String.format(java.util.Locale.US, "%02d-%02d-%04d", today.get(java.util.Calendar.DAY_OF_MONTH), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.YEAR))
                                                    viewModel.loadLiveAladhanHijri(formatStr)
                                                    viewModel.loadRetrofitHolidays(viewYear)
                                                    viewModel.showToastPopup("Menghubungkan Aladhan Kalender API...")
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                if (viewModel.isHijriApiLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 1.5.dp
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Sync API", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Koreksi Kalender (Hilal):",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                listOf(-2, -1, 0, 1).forEach { offset ->
                                                    val active = viewModel.hijriOffset == offset
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(
                                                                if (active) MaterialTheme.colorScheme.primaryContainer 
                                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                            .clickable { viewModel.hijriOffset = offset }
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = if (offset >= 0) "+$offset" else "$offset",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showGpsDialog = false }) {
                                        Text("Tutup")
                                    }
                                }
                            )
                        }
                    }
                }
            }

        }

        // DIALOG: ATUR PIN KUNCI APLIKASI
        if (showPinSetupDialog) {
            var activeStep by remember { mutableIntStateOf(1) } // 1: Buat PIN, 2: Konfirmasi PIN
            var pinInput by remember { mutableStateOf("") }
            var pinConfirmInput by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }

            LaunchedEffect(pinInput) {
                if (activeStep == 1 && pinInput.length == 4) {
                    delay(200)
                    activeStep = 2
                    errorMessage = ""
                }
            }

            LaunchedEffect(pinConfirmInput) {
                if (activeStep == 2 && pinConfirmInput.length == 4) {
                    delay(200)
                    if (pinInput == pinConfirmInput) {
                        viewModel.saveAppPin(pinInput)
                        viewModel.saveAppLockEnabled(true)
                        viewModel.isAppUnlocked = true
                        showPinSetupDialog = false
                        showAppLockDialog = true
                        viewModel.showToastPopup("PIN baru berhasil disimpan dan proteksi telah aktif!")
                    } else {
                        errorMessage = "PIN konfirmasi tidak cocok!"
                        pinConfirmInput = ""
                    }
                }
            }

            Dialog(onDismissRequest = { showPinSetupDialog = false; showAppLockDialog = true }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Icon & Title
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Atur PIN Kunci Aplikasi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Buat PIN 4 digit untuk mengamankan aplikasi Anda",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Step Status Container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (activeStep == 1) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (activeStep == 1) "1. Buat PIN Baru" else "2. Konfirmasi PIN Baru",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentText = if (activeStep == 1) pinInput else pinConfirmInput
                                    for (i in 0 until 4) {
                                        val isFilled = i < currentText.length
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .shadow(if (isFilled) 4.dp else 2.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isFilled) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = if (isFilled) 0.dp else 1.5.dp,
                                                    color = if (isFilled) Color.Unspecified else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Keypad Grid Container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val buttonRows = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9")
                                )

                                for (row in buttonRows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (num in row) {
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SetupKeypadButton(
                                                    text = num,
                                                    modifier = Modifier.size(60.dp)
                                                ) {
                                                    if (activeStep == 1) {
                                                        if (pinInput.length < 4) pinInput += num
                                                    } else {
                                                        if (pinConfirmInput.length < 4) pinConfirmInput += num
                                                    }
                                                    errorMessage = ""
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SetupKeypadButton(
                                            text = "0",
                                            modifier = Modifier.size(60.dp)
                                        ) {
                                            if (activeStep == 1) {
                                                if (pinInput.length < 4) pinInput += "0"
                                            } else {
                                                if (pinConfirmInput.length < 4) pinConfirmInput += "0"
                                            }
                                            errorMessage = ""
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = {
                                                if (activeStep == 2) {
                                                    if (pinConfirmInput.isNotEmpty()) {
                                                        pinConfirmInput = pinConfirmInput.dropLast(1)
                                                    } else {
                                                        activeStep = 1
                                                        if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                    }
                                                } else {
                                                    if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                }
                                                errorMessage = ""
                                            },
                                            modifier = Modifier
                                                .size(60.dp)
                                                .shadow(elevation = 6.dp, shape = CircleShape),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Backspace,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = { showPinSetupDialog = false; showAppLockDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Batal",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // DIALOG: GANTI PIN KUNCI APLIKASI
        if (showPinChangeDialog) {
            var activeStep by remember { mutableIntStateOf(1) } // 1: PIN Lama, 2: PIN Baru, 3: Konfirmasi PIN Baru
            var pinOldInput by remember { mutableStateOf("") }
            var pinNewInput by remember { mutableStateOf("") }
            var pinNewConfirmInput by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }

            LaunchedEffect(pinOldInput) {
                if (activeStep == 1 && pinOldInput.length == 4) {
                    delay(200)
                    if (pinOldInput == viewModel.appPin) {
                        errorMessage = ""
                        activeStep = 2
                    } else {
                        errorMessage = "PIN lama Anda salah!"
                        pinOldInput = ""
                    }
                }
            }

            LaunchedEffect(pinNewInput) {
                if (activeStep == 2 && pinNewInput.length == 4) {
                    delay(200)
                    if (pinNewInput == viewModel.appPin || pinNewInput == pinOldInput) {
                        errorMessage = "PIN baru tidak boleh sama dengan PIN lama!"
                        pinNewInput = ""
                    } else {
                        errorMessage = ""
                        activeStep = 3
                    }
                }
            }

            LaunchedEffect(pinNewConfirmInput) {
                if (activeStep == 3 && pinNewConfirmInput.length == 4) {
                    delay(200)
                    if (pinNewInput == pinOldInput || pinNewInput == viewModel.appPin) {
                        errorMessage = "PIN baru tidak boleh sama dengan PIN lama!"
                        activeStep = 2
                        pinNewInput = ""
                        pinNewConfirmInput = ""
                    } else if (pinNewInput == pinNewConfirmInput) {
                        viewModel.saveAppPin(pinNewInput)
                        showPinChangeDialog = false
                        showAppLockDialog = true
                        viewModel.showToastPopup("PIN aplikasi berhasil diperbarui!")
                    } else {
                        errorMessage = "PIN Baru & Konfirmasi tidak cocok!"
                        pinNewConfirmInput = ""
                    }
                }
            }

            Dialog(onDismissRequest = { showPinChangeDialog = false; showAppLockDialog = true }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Icon & Title
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Ganti PIN Aplikasi",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Masukkan PIN lama kemudian buat PIN baru Anda",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Active Step Status Container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (activeStep == 1) Icons.Default.Lock else if (activeStep == 2) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when(activeStep) {
                                            1 -> "Langkah 1/3: Masukkan PIN Lama"
                                            2 -> "Langkah 2/3: Buat PIN Baru"
                                            else -> "Langkah 3/3: Konfirmasi PIN Baru"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentText = when(activeStep) {
                                        1 -> pinOldInput
                                        2 -> pinNewInput
                                        else -> pinNewConfirmInput
                                    }
                                    for (i in 0 until 4) {
                                        val isFilled = i < currentText.length
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .shadow(if (isFilled) 4.dp else 2.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isFilled) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = if (isFilled) 0.dp else 1.5.dp,
                                                    color = if (isFilled) Color.Unspecified else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Keypad Grid Container
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val buttonRows = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9")
                                )

                                for (row in buttonRows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        for (num in row) {
                                            Box(
                                                modifier = Modifier.weight(1f),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                SetupKeypadButton(
                                                    text = num,
                                                    modifier = Modifier.size(60.dp)
                                                ) {
                                                    when(activeStep) {
                                                        1 -> if (pinOldInput.length < 4) pinOldInput += num
                                                        2 -> if (pinNewInput.length < 4) pinNewInput += num
                                                        3 -> if (pinNewConfirmInput.length < 4) pinNewConfirmInput += num
                                                    }
                                                    errorMessage = ""
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SetupKeypadButton(
                                            text = "0",
                                            modifier = Modifier.size(60.dp)
                                        ) {
                                            when(activeStep) {
                                                1 -> if (pinOldInput.length < 4) pinOldInput += "0"
                                                2 -> if (pinNewInput.length < 4) pinNewInput += "0"
                                                3 -> if (pinNewConfirmInput.length < 4) pinNewConfirmInput += "0"
                                            }
                                            errorMessage = ""
                                        }
                                    }
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            onClick = {
                                                when(activeStep) {
                                                    3 -> {
                                                        if (pinNewConfirmInput.isNotEmpty()) {
                                                            pinNewConfirmInput = pinNewConfirmInput.dropLast(1)
                                                        } else {
                                                            activeStep = 2
                                                            if (pinNewInput.isNotEmpty()) pinNewInput = pinNewInput.dropLast(1)
                                                        }
                                                    }
                                                    2 -> {
                                                        if (pinNewInput.isNotEmpty()) {
                                                            pinNewInput = pinNewInput.dropLast(1)
                                                        } else {
                                                            activeStep = 1
                                                            if (pinOldInput.isNotEmpty()) pinOldInput = pinOldInput.dropLast(1)
                                                        }
                                                    }
                                                    1 -> {
                                                        if (pinOldInput.isNotEmpty()) pinOldInput = pinOldInput.dropLast(1)
                                                    }
                                                }
                                                errorMessage = ""
                                            },
                                            modifier = Modifier
                                                .size(60.dp)
                                                .shadow(elevation = 6.dp, shape = CircleShape),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Backspace,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = { showPinChangeDialog = false; showAppLockDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Batal",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Dialogs for Share Settings
        if (showUrlDialog) {
            var tempUrl by remember { mutableStateOf(appsScriptUrl) }
            AlertDialog(
                onDismissRequest = { showUrlDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sambungkan Apps Script", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Masukkan URL hasil Deploy (Web App) dari Google Apps Script untuk mengakses Google Drive.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { tempUrl = it },
                            label = { Text("Web App URL") },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                            singleLine = false,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            sharedPrefs.edit().putString("apps_script_url", tempUrl).apply()
                            appsScriptUrl = tempUrl
                            showUrlDialog = false
                            viewModel.showToastPopup("URL berhasil disimpan!")
                        }
                    ) {
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        if (showScriptTutorial) {
            val appsScriptCode = """// Google Apps Script Code
// ID FOLDER DRIVE: 1-h-P5AnrhfJvutx1HcyyqOgc4C6lReYg
var FOLDER_ID = "1-h-P5AnrhfJvutx1HcyyqOgc4C6lReYg";

function doGet(e) {
  if (!e || !e.parameter) {
    return ContentService.createTextOutput(JSON.stringify({ 
      status: "error", 
      message: "Penyiapan Berhasil! Jangan jalankan (Run) langsung dari editor Google. Deploy sebagai Web App dan gunakan URL-nya." 
    })).setMimeType(ContentService.MimeType.JSON);
  }
  var action = e.parameter.action;
  
  if (action === "list") {
    try {
      var folder = DriveApp.getFolderById(FOLDER_ID);
      var files = folder.getFiles();
      var list = [];
      while (files.hasNext()) {
        var file = files.next();
        list.push({
          id: file.getId(),
          name: file.getName(),
          mimeType: file.getMimeType(),
          size: file.getSize(),
          url: file.getUrl()
        });
      }
      return ContentService.createTextOutput(JSON.stringify({ status: "success", files: list }))
        .setMimeType(ContentService.MimeType.JSON);
    } catch (err) {
      return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
        .setMimeType(ContentService.MimeType.JSON);
    }
  }
  
  if (action === "download_all") {
    try {
      var folder = DriveApp.getFolderById(FOLDER_ID);
      var files = folder.getFiles();
      var blobs = [];
      while (files.hasNext()) {
        blobs.push(files.next().getBlob());
      }
      if (blobs.length === 0) {
        return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Folder is empty" }))
          .setMimeType(ContentService.MimeType.JSON);
      }
      var zipBlob = Utilities.zip(blobs, "My_Drive_Backup.zip");
      var base64 = Utilities.base64Encode(zipBlob.getBytes());
      return ContentService.createTextOutput(JSON.stringify({ status: "success", base64: base64, filename: "My_Drive_Backup.zip" }))
        .setMimeType(ContentService.MimeType.JSON);
    } catch (err) {
      return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
        .setMimeType(ContentService.MimeType.JSON);
    }
  }
  return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Invalid action" }))
    .setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return ContentService.createTextOutput(JSON.stringify({ 
        status: "error", 
        message: "Penyiapan Berhasil! Jangan jalankan (Run) langsung dari editor Google. Gunakan Client App untuk mengunggah atau menghapus file." 
      })).setMimeType(ContentService.MimeType.JSON);
    }
    var rawData = e.postData.contents;
    var data = JSON.parse(rawData);
    var action = data.action;
    var folder = DriveApp.getFolderById(FOLDER_ID);
    
    if (action === "upload") {
      var decodedBytes = Utilities.base64Decode(data.base64Data);
      var blob = Utilities.newBlob(decodedBytes, data.mimeType, data.filename);
      var newFile = folder.createFile(blob);
      return ContentService.createTextOutput(JSON.stringify({ 
        status: "success", 
        fileId: newFile.getId(),
        name: newFile.getName()
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    if (action === "delete") {
      var file = DriveApp.getFileById(data.fileId);
      var parents = file.getParents();
      var inFolder = false;
      while (parents.hasNext()) {
        if (parents.next().getId() === FOLDER_ID) { inFolder = true; break; }
      }
      if (!inFolder) {
        return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Akses Ditolak!" })).setMimeType(ContentService.MimeType.JSON);
      }
      file.setTrashed(true);
      return ContentService.createTextOutput(JSON.stringify({ status: "success", message: "Hapus berhasil" }))
        .setMimeType(ContentService.MimeType.JSON);
    }
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Invalid action" })).setMimeType(ContentService.MimeType.JSON);
  } catch(err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}"""

            AlertDialog(
                onDismissRequest = { showScriptTutorial = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Petunjuk Setup Apps Script", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        Text(
                            "Gunakan naskah script di bawah ini sebagai perantara Cloud Drive Anda tanpa harus mendaftar GCP Console.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = {
                                clipboardManager?.setText(androidx.compose.ui.text.AnnotatedString(appsScriptCode))
                                viewModel.showToastPopup("Kode disalin ke Clipboard!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Kode Google Apps Script (.gs)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        HorizontalDivider()

                        Text(
                            "Langkah Penyebaran (Deployment):\n" +
                                    "1. Buka script.google.com & buat project baru.\n" +
                                    "2. Tempelkan kode yang telah Anda salin.\n" +
                                    "3. Klik 'Deploy' -> 'New Deployment'.\n" +
                                    "4. Pilih jenis 'Web App'.\n" +
                                    "5. Set 'Execute as': 'Me' & 'Who has access': 'Anyone'.\n" +
                                    "6. Klik 'Deploy', izinkan hak akses Drive, lalu salin URL Web App yang dihasilkan ke pengaturan share aplikasi ini.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showScriptTutorial = false }) {
                        Text("Mengerti")
                    }
                }
            )
        }
    }
}

@Composable
fun MenuItem(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

