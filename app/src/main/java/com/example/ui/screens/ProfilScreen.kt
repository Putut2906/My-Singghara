package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import com.example.ui.MainViewModel
import com.example.ui.components.StoragePermissionBox
import com.example.ui.components.FirebaseAuthDialog
import com.example.ui.components.AuthMode
import com.example.ui.components.PhotoRepositionDialog
import com.example.ui.components.AppUserAvatar
import com.example.ui.components.VipMembershipDetailDialog
import com.example.firebase.CloudUserItem
import coil.compose.AsyncImage

@Composable
fun ProfilScreen(
    viewModel: MainViewModel,
    onEditProfileClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var activeSection by remember { mutableStateOf<String?>(null) }

    var photoToRepositionUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showMembershipDialogInProfile by remember { mutableStateOf(false) }

    val profileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photoToRepositionUri = uri
        }
    }

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showPinDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showFirestoreRulesDialog by remember { mutableStateOf(false) }

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

    // Firebase Auth Dialog States in Profile Screen
    var showAuthDialogInProfile by remember { mutableStateOf(false) }
    var authDialogModeInProfile by remember { mutableStateOf(AuthMode.LOGIN) }

    // Dialog Atur Kata Sandi Email di Profil (Dilindungi OTP ke Admin UiDpHxawlBQ17mxTUHIXvhxRR3b2)
    var showSetPasswordDialogInProfile by remember { mutableStateOf(false) }
    var profilePasswordInput by remember { mutableStateOf("") }
    var profilePasswordConfirm by remember { mutableStateOf("") }
    var profilePasswordVisible by remember { mutableStateOf(false) }
    var profilePasswordConfirmVisible by remember { mutableStateOf(false) }
    var profilePasswordLoading by remember { mutableStateOf(false) }
    var profilePasswordError by remember { mutableStateOf<String?>(null) }
    var profileOtpCodeInput by remember { mutableStateOf("") }
    var isProfileOtpVerified by remember { mutableStateOf(false) }
    var profileOtpLoading by remember { mutableStateOf(false) }
    var profileOtpSentSuccess by remember { mutableStateOf(false) }
    var lastGeneratedOtpRequest by remember { mutableStateOf<com.example.firebase.AdminOtpRequest?>(null) }

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
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
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
                    if (isStatusError && (statusMessage.contains("PERMISSION", ignoreCase = true) || statusMessage.contains("permissions", ignoreCase = true))) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showFirestoreRulesDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Shield, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Solusi Izin Firebase Cloud (Salin Aturan)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
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
                        // Profile Avatar - Klik untuk membuka Pop up Detail Membership
                        AppUserAvatar(
                            name = viewModel.profileName,
                            imageUri = viewModel.profileImageUri,
                            avatarIndex = viewModel.profileAvatarIndex,
                            size = 88.dp,
                            textSize = 28.sp,
                            isLoggedIn = viewModel.currentFirebaseUser != null,
                            contentDescription = "Foto Profil (Klik untuk Detail Membership)",
                            onClick = {
                                showMembershipDialogInProfile = true
                            }
                        )

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

                        // Icon Kamera / Atur Posisi Foto di pojok kanan bawah foto avatar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    showPhotoOptionsDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (viewModel.profileAvatarIndex == -1 && viewModel.profileImageUri.isNotEmpty()) Icons.Default.Crop else Icons.Default.CameraAlt,
                                contentDescription = "Atur Posisi Foto Profil",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nama Pengguna - Klik untuk membuka Pop up Detail Membership
                    Text(
                        text = viewModel.profileName.ifEmpty { "Pengguna Setia" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            showMembershipDialogInProfile = true
                        }
                    )

                    // Badge Kartu Membership VIP Interaktif (Klik Untuk Melihat Pop Up Kartu)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { showMembershipDialogInProfile = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Kartu Membership VIP (Klik Detail)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    val currentFirebaseUser = viewModel.currentFirebaseUser
                    if (currentFirebaseUser != null) {
                        // Info Akun saat pengguna telah login
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentFirebaseUser.email ?: currentFirebaseUser.displayName ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sinkronkan Profil dari Akun Google / Facebook
                            OutlinedButton(
                                onClick = {
                                    viewModel.refreshAuthUser()
                                    viewModel.showToastPopup("Profil berhasil disinkronkan!")
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Sync, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sinkron", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Atur Kata Sandi Email (dilindungi proteksi OTP ke Admin)
                            OutlinedButton(
                                onClick = {
                                    profilePasswordInput = ""
                                    profilePasswordConfirm = ""
                                    profilePasswordError = null
                                    profileOtpCodeInput = ""
                                    isProfileOtpVerified = false
                                    profileOtpLoading = false
                                    profileOtpSentSuccess = false
                                    lastGeneratedOtpRequest = null
                                    showSetPasswordDialogInProfile = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E)),
                                border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Key, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sandi Email", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Keluar Akun
                            OutlinedButton(
                                onClick = {
                                    viewModel.requestLogoutConfirmation()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Logout, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keluar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        // Tombol Login dan Daftar langsung di bawah profil (bukan menu)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tombol Masuk / Login
                            Button(
                                onClick = {
                                    authDialogModeInProfile = AuthMode.LOGIN
                                    showAuthDialogInProfile = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Login, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Masuk", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            // Tombol Daftar
                            Button(
                                onClick = {
                                    authDialogModeInProfile = AuthMode.REGISTER
                                    showAuthDialogInProfile = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Daftar", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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

                        val isDark = isSystemInDarkTheme()
                        val webConnectTint = if (isDark) Color(0xFF34D399) else Color(0xFF00A884)
                        val shareTint = if (isDark) Color(0xFF60A5FA) else MaterialTheme.colorScheme.primary
                        val telegramTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF229ED9)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Devices,
                            iconTint = webConnectTint,
                            title = "Web Connect (Akses PC)",
                            subtitle = "Hubungkan & kelola aplikasi dari browser Laptop / PC",
                            onClick = { viewModel.currentRoute = "web_connect" }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Cloud,
                            iconTint = shareTint,
                            title = "Pengaturan Share",
                            subtitle = "Seting Share & Bantuan",
                            onClick = { activeSection = "pengaturan_share" }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Forum,
                            iconTint = telegramTint,
                            title = "Telegram AI Assistant",
                            subtitle = "Asisten bot @My_Singghara_bot: cek libur, agenda & database",
                            onClick = { viewModel.currentRoute = "telegram_ai" }
                        )

                        val isUserAdmin = viewModel.currentFirebaseUser?.uid == com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID
                        if (isUserAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val isDark = isSystemInDarkTheme()
                            MenuItem(
                                icon = Icons.Default.AdminPanelSettings,
                                iconTint = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                                title = "Manajemen Akun (Khusus Admin)",
                                subtitle = "Panel Admin: Pengguna, Pesan & Token FCM",
                                onClick = { viewModel.currentRoute = "manajemen_akun" }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        MenuItem(
                            icon = Icons.Default.Info,
                            iconTint = Color(0xFF0F766E),
                            title = "Tentang Aplikasi",
                            subtitle = "Versi v${com.example.BuildConfig.VERSION_NAME}",
                            onClick = { viewModel.currentRoute = "tentang_aplikasi" }
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
                        val currentUser = viewModel.currentFirebaseUser
                        val backupSubtitle = if (currentUser != null) {
                            "Cloud Firebase (${currentUser.email ?: currentUser.displayName}) & Berkas Lokal"
                        } else {
                            "Cloud Firebase Terisolasi & Berkas Lokal (SAF)"
                        }
                        
                        MenuItem(
                            icon = Icons.Default.Backup,
                            title = "Cadangkan & Pulihkan (Backup & Restore)",
                            subtitle = backupSubtitle,
                            onClick = {
                                viewModel.checkCloudBackupMetadata()
                                showBackupDialog = true
                            },
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

                                        val bpCount = viewModel.allBelanjaPasar.collectAsState().value.size
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "Cakupan Sinkronisasi & Cadangan:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "• Rekap Belanja Pasar ($bpCount item terintegrasi)\n• Belanja Harian & Tabungan\n• Catatan, Agenda, Jadwal & Mind Map\n• Al-Qur'an, Data Anggota, Presensi & Gaji",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                        
                                        // Firebase Cloud Backup & Restore Section
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        val currentUser = viewModel.currentFirebaseUser
                                        val isUserLoggedIn = currentUser != null

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CloudSync,
                                                    null,
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Cadangan Cloud Firebase",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = Color(0xFF0284C7)
                                                )
                                            }

                                            if (isUserLoggedIn) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "Tersambung",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF047857),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "Belum Masuk",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isUserLoggedIn) Color(0xFF0284C7).copy(alpha = 0.08f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (isUserLoggedIn) {
                                                    Text(
                                                        text = "Akun Cloud: ${currentUser?.email ?: currentUser?.displayName ?: "Pengguna"}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    val meta = viewModel.cloudBackupMetadata
                                                    if (meta != null) {
                                                        Text(
                                                            text = "Cadangan Terakhir: ${meta.dateTimeFormatted} (${meta.sizeKb})",
                                                            fontSize = 10.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "Data tersimpan di Firestore pribadi: users/${currentUser?.uid?.take(8)}.../backups",
                                                            fontSize = 10.5.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "🔒 Data akun Anda terisolasi total, tidak dapat dibaca atau diubah akun lain.",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF047857)
                                                    )
                                                } else {
                                                    Text(
                                                        text = "Masuk atau daftar akun untuk menyimpan cadangan ke Firebase Cloud Console secara otomatis dan terisolasi per akun.",
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (isUserLoggedIn) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.backupToFirebaseCloud(context) { msg, success ->
                                                            statusMessage = msg
                                                            isStatusError = !success
                                                        }
                                                    },
                                                    enabled = !viewModel.isCloudBackupLoading,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                                ) {
                                                    if (viewModel.isCloudBackupLoading) {
                                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Cadangkan Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.restoreFromFirebaseCloud(context) { msg, success ->
                                                            statusMessage = msg
                                                            isStatusError = !success
                                                        }
                                                    },
                                                    enabled = !viewModel.isCloudBackupLoading,
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                                                ) {
                                                    if (viewModel.isCloudBackupLoading) {
                                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Pulihkan Cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        Text(
                                            text = "Cadangan Berkas Lokal (SAF)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
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

                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        val azanDownloadState by com.example.util.AzanDownloader.downloadState.collectAsState()
                                        val availableMp3Count = remember(azanDownloadState) { com.example.util.SinggharaFolderManager.getAvailableMp3Files().size }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Audio Adzan (Bawaan Paket / Offline)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Text("$availableMp3Count berkas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                         }

                                        Text(
                                            "Audio adzan Google Drive sudah dimasukkan ke dalam paket pemasangan aplikasi & otomatis tersedia di folder My Singghara/Data/mp3 tanpa harus koneksi internet.",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 14.sp
                                        )

                                        when (val st = azanDownloadState) {
                                            is com.example.util.AzanDownloadState.Downloading -> {
                                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    LinearProgressIndicator(progress = { st.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                                                    Text("Memproses: ${st.currentFileName} (${st.downloadedCount}/${st.totalCount})", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            is com.example.util.AzanDownloadState.Success -> {
                                                Text("Audio adzan tersimpan di My Singghara/Data/mp3 (${st.fileNames.size} berkas siap).", fontSize = 10.5.sp, color = Color(0xFF16A34A))
                                            }
                                            is com.example.util.AzanDownloadState.Error -> {
                                                Text("Status: ${st.errorMessage}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            else -> {}
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                com.example.util.SinggharaFolderManager.seedDefaultMp3sFromAssets(context)
                                                com.example.util.AzanDownloader.startDownloadAzanMp3s(context, force = true)
                                                viewModel.showToastPopup("Memasang ulang audio adzan ke folder My Singghara/Data/mp3...")
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pasang Ulang / Sinkronkan Audio Adzan", fontSize = 11.5.sp)
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
        var dCreated = file.getDateCreated();
        var dUpdated = file.getLastUpdated();
        list.push({
          id: file.getId(),
          name: file.getName(),
          mimeType: file.getMimeType(),
          size: file.getSize(),
          url: file.getUrl(),
          date: dCreated ? dCreated.toISOString() : "",
          lastUpdated: dUpdated ? dUpdated.toISOString() : "",
          timestamp: dUpdated ? dUpdated.getTime() : (dCreated ? dCreated.getTime() : 0)
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
                                    "1. Buka script.google.com & buat project baru (atau buka project yang ada).\n" +
                                    "2. Tempelkan kode yang telah Anda salin.\n" +
                                    "3. Klik 'Deploy' -> 'New Deployment' (atau 'Manage deployments' jika sudah ada).\n" +
                                    "4. Pilih jenis 'Web App'.\n" +
                                    "5. Set 'Execute as': 'Me' & 'Who has access': 'Anyone'.\n" +
                                    "6. ⚠️ PENTING: Saat memperbarui script lama, klik ikon pensil (Edit), ubah 'Version' menjadi 'New version', lalu klik 'Deploy'.\n" +
                                    "7. Salin URL Web App yang berakhiran /exec ke aplikasi ini.",
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

        // Firebase Auth Dialog (Login, Register, Forgot Password)
        if (showAuthDialogInProfile) {
            FirebaseAuthDialog(
                viewModel = viewModel,
                initialMode = authDialogModeInProfile,
                onDismissRequest = {
                    showAuthDialogInProfile = false
                    viewModel.refreshAuthUser()
                }
            )
        }

        // Dialog Buat / Atur Kata Sandi Email dari Profil (Dilindungi Proteksi OTP ke Admin UiDpHxawlBQ17mxTUHIXvhxRR3b2)
        val currentUserInProfile = viewModel.currentFirebaseUser
        if (showSetPasswordDialogInProfile && currentUserInProfile != null) {
            val userEmail = currentUserInProfile.email ?: ""
            val isCurrentUserAdmin = currentUserInProfile.uid == com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID
            AlertDialog(
                onDismissRequest = {
                    if (!profilePasswordLoading && !profileOtpLoading) showSetPasswordDialogInProfile = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F766E).copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kata Sandi Login Email",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Proteksi OTP Admin: UiDpHxawlBQ17mxTUHIXvhxRR3b2",
                                fontSize = 10.5.sp,
                                color = Color(0xFF0F766E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Alert / Banner Proteksi Keamanan Admin
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isProfileOtpVerified) Color(0xFFECFDF5) else Color(0xFFFEF3C7)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (isProfileOtpVerified) Icons.Default.CheckCircle else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isProfileOtpVerified) Color(0xFF059669) else Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isProfileOtpVerified) "Otorisasi OTP Admin Terverifikasi" else "Proteksi Sandi Akun Singghara",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (isProfileOtpVerified) Color(0xFF065F46) else Color(0xFF92400E)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isProfileOtpVerified) {
                                            "Kode verifikasi telah disetujui. Anda sekarang dapat menetapkan kata sandi baru untuk akun $userEmail."
                                        } else {
                                            "Untuk keamanan akun $userEmail, pengaturan kata sandi wajib diverifikasi dengan kode OTP yang dikirimkan ke Admin Singghara (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2)."
                                        },
                                        fontSize = 10.5.sp,
                                        color = if (isProfileOtpVerified) Color(0xFF047857) else Color(0xFF78350F),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!profilePasswordError.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = profilePasswordError!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // STEP 1: Verifikasi OTP dari Admin jika belum terverifikasi
                        if (!isProfileOtpVerified) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Langkah 1: Minta Kode OTP ke Admin",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Kirimkan kode OTP ke Admin resmi, lalu masukkan kode yang diberikan Admin:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Tombol Kirim OTP ke Admin
                                    Button(
                                        onClick = {
                                            profileOtpLoading = true
                                            profilePasswordError = null
                                            viewModel.requestEmailPasswordOtp { success, req, err ->
                                                profileOtpLoading = false
                                                if (success && req != null) {
                                                    profileOtpSentSuccess = true
                                                    lastGeneratedOtpRequest = req
                                                    viewModel.showToastPopup("Permintaan kode OTP berhasil dikirimkan ke Admin!")
                                                } else {
                                                    profilePasswordError = err ?: "Gagal mengirim permintaan OTP ke Admin."
                                                }
                                            }
                                        },
                                        enabled = !profileOtpLoading,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        if (profileOtpLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (profileOtpSentSuccess) "Kirim Ulang OTP ke Admin" else "Kirim OTP ke Admin (UiDpHxawlBQ17mxTUHIXvhxRR3b2)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (profileOtpSentSuccess) {
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Tombol Chat WhatsApp ke Admin
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val rawPhone = viewModel.adminPhoneNumber
                                                    val formattedPhone = if (rawPhone.startsWith("0")) "62" + rawPhone.substring(1) else rawPhone.replace("+", "").replace("-", "").replace(" ", "")
                                                    val msg = "Halo Admin My Singghara, saya meminta 6-digit kode OTP untuk verifikasi kata sandi email akun: $userEmail (UID: ${currentUserInProfile.uid}). Mohon berikan kodenya."
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(msg)}"))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    viewModel.showToastPopup("Gagal membuka WhatsApp: ${e.message}")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Hubungi Admin via WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        // Jika yang sedang login adalah Admin sendiri
                                        if (isCurrentUserAdmin && lastGeneratedOtpRequest != null) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            TextButton(
                                                onClick = {
                                                    profileOtpCodeInput = lastGeneratedOtpRequest?.otpCode ?: ""
                                                    profilePasswordError = null
                                                },
                                                modifier = Modifier.height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(Icons.Default.AdminPanelSettings, null, modifier = Modifier.size(13.dp), tint = Color(0xFF047857))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Isi Otomatis Kode Admin (${lastGeneratedOtpRequest?.otpCode ?: ""})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF047857)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Kolom Input 6-Digit OTP dari Admin
                                        OutlinedTextField(
                                            value = profileOtpCodeInput,
                                            onValueChange = {
                                                if (it.length <= 6) {
                                                    profileOtpCodeInput = it.filter { char -> char.isDigit() }
                                                    profilePasswordError = null
                                                }
                                            },
                                            label = { Text("Kode OTP 6-Digit dari Admin", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp), tint = Color(0xFF0F766E)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = {
                                                if (profileOtpCodeInput.length != 6) {
                                                    profilePasswordError = "Kode OTP harus 6 digit angka."
                                                    return@Button
                                                }
                                                profileOtpLoading = true
                                                profilePasswordError = null
                                                viewModel.verifyEmailPasswordOtp(profileOtpCodeInput) { ok, err ->
                                                    profileOtpLoading = false
                                                    if (ok) {
                                                        isProfileOtpVerified = true
                                                        profilePasswordError = null
                                                        viewModel.showToastPopup("Otorisasi Admin Berhasil! Silakan masukkan kata sandi baru.")
                                                    } else {
                                                        profilePasswordError = err ?: "Kode OTP salah atau tidak cocok dari Admin."
                                                    }
                                                }
                                            },
                                            enabled = !profileOtpLoading && profileOtpCodeInput.length == 6,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                            modifier = Modifier.fillMaxWidth().height(38.dp)
                                        ) {
                                            if (profileOtpLoading) {
                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Verifikasi Kode OTP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // STEP 2: Input Sandi Baru setelah OTP Terverifikasi
                            Text(
                                text = "Langkah 2: Tentukan Kata Sandi Baru",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )

                            OutlinedTextField(
                                value = profilePasswordInput,
                                onValueChange = { profilePasswordInput = it; profilePasswordError = null },
                                label = { Text("Kata Sandi Baru (min. 6 karakter)", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { profilePasswordVisible = !profilePasswordVisible }) {
                                        Icon(
                                            imageVector = if (profilePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (profilePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = profilePasswordConfirm,
                                onValueChange = { profilePasswordConfirm = it; profilePasswordError = null },
                                label = { Text("Konfirmasi Kata Sandi", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    IconButton(onClick = { profilePasswordConfirmVisible = !profilePasswordConfirmVisible }) {
                                        Icon(
                                            imageVector = if (profilePasswordConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                visualTransformation = if (profilePasswordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    if (isProfileOtpVerified) {
                        Button(
                            onClick = {
                                if (profilePasswordInput.length < 6) {
                                    profilePasswordError = "Kata sandi minimal harus 6 karakter."
                                    return@Button
                                }
                                if (profilePasswordInput != profilePasswordConfirm) {
                                    profilePasswordError = "Konfirmasi kata sandi tidak cocok."
                                    return@Button
                                }

                                profilePasswordLoading = true
                                profilePasswordError = null
                                scope.launch {
                                    val res = viewModel.authManager.setUserPasswordForGoogleAccount(
                                        user = currentUserInProfile,
                                        newPassword = profilePasswordInput,
                                        context = context
                                    )
                                    profilePasswordLoading = false
                                    res.onSuccess {
                                        lastGeneratedOtpRequest?.id?.let { otpId ->
                                            viewModel.markAdminOtpUsed(otpId)
                                        }
                                        viewModel.showToastPopup("Kata sandi berhasil disimpan! Anda sekarang dapat login dengan email.")
                                        showSetPasswordDialogInProfile = false
                                    }.onFailure { err ->
                                        profilePasswordError = err.message ?: "Gagal memperbarui kata sandi."
                                    }
                                }
                            },
                            enabled = !profilePasswordLoading,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            if (profilePasswordLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Simpan Kata Sandi", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSetPasswordDialogInProfile = false },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (isProfileOtpVerified) "Tutup" else "Batal", fontSize = 12.sp)
                    }
                }
            )
        }

        // Firestore Rules Helper Dialog
        if (showFirestoreRulesDialog) {
            com.example.ui.components.FirestoreRulesHelperDialog(
                onDismissRequest = { showFirestoreRulesDialog = false }
            )
        }

        // Photo Options Dialog (Profil)
        if (showPhotoOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoOptionsDialog = false },
                title = {
                    Text(
                        "Foto Profil",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Atur posisi atau pilih foto dari galeri agar pas dan tidak terpotong:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (viewModel.profileAvatarIndex == -1 && viewModel.profileImageUri.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    showPhotoOptionsDialog = false
                                    try {
                                        photoToRepositionUri = Uri.parse(viewModel.profileImageUri)
                                    } catch (e: Exception) {
                                        viewModel.showToastPopup("Gagal membuka foto: ${e.message}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Atur Posisi Foto Saat Ini")
                            }
                        }

                        Button(
                            onClick = {
                                showPhotoOptionsDialog = false
                                try {
                                    viewModel.preventAutoLock = true
                                    profileImagePickerLauncher.launch("image/*")
                                } catch (e: Exception) {
                                    viewModel.preventAutoLock = false
                                    viewModel.showToastPopup("Gagal membuka galeri: ${e.message}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih Foto dari Galeri")
                        }

                        OutlinedButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                onEditProfileClick?.invoke()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pilih Avatar Karakter")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPhotoOptionsDialog = false }) {
                        Text("Tutup")
                    }
                }
            )
        }

        // Photo Reposition Dialog
        if (photoToRepositionUri != null) {
            PhotoRepositionDialog(
                imageUri = photoToRepositionUri!!,
                onDismiss = { photoToRepositionUri = null },
                onConfirm = { croppedBitmap ->
                    viewModel.saveProfileImageBitmap(croppedBitmap)
                    photoToRepositionUri = null
                    viewModel.showToastPopup("Posisi foto berhasil disimpan!")
                }
            )
        }

        // POPUP DETAIL MEMBERSHIP VIP (Muncul Saat Klik Profil User)
        if (showMembershipDialogInProfile || viewModel.showMembershipDetailDialog) {
            val currentFbUser = viewModel.currentFirebaseUser
            val isUserAdmin = currentFbUser?.let { viewModel.adminManager.isAdmin(it.uid) } ?: false
            val currentCloudUser = remember(currentFbUser, viewModel.accountMembershipTier, viewModel.accountMembershipEndDate) {
                currentFbUser?.let { fb ->
                    CloudUserItem(
                        uid = fb.uid,
                        email = fb.email ?: "",
                        displayName = viewModel.profileName.ifEmpty { fb.displayName ?: "Pengguna Setia" },
                        photoUrl = viewModel.profileImageUri.ifEmpty { fb.photoUrl?.toString() ?: "" },
                        isActive = !viewModel.isAccountBlocked,
                        isAdmin = isUserAdmin,
                        membershipDurationMonths = if (viewModel.accountMembershipTier.contains("12")) 12
                            else if (viewModel.accountMembershipTier.contains("7")) 7
                            else if (viewModel.accountMembershipTier.contains("5")) 5
                            else if (viewModel.accountMembershipTier.contains("3")) 3
                            else if (viewModel.accountMembershipTier.contains("1")) 1
                            else 0,
                        membershipEndDate = viewModel.accountMembershipEndDate,
                        membershipTier = viewModel.accountMembershipTier
                    )
                }
            }

            VipMembershipDetailDialog(
                user = viewModel.selectedMembershipUser ?: currentCloudUser,
                currentUserName = viewModel.profileName,
                currentUserEmail = currentFbUser?.email ?: "",
                adminPhoneNumber = viewModel.adminPhoneNumber,
                isViewerAdmin = isUserAdmin,
                onDismiss = {
                    showMembershipDialogInProfile = false
                    viewModel.closeMembershipDetail()
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

