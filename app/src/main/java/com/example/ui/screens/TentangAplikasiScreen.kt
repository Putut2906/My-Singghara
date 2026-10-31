package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.ui.MainViewModel
import com.example.update.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class DriveDownloadProgressState {
    object Idle : DriveDownloadProgressState()
    data class Downloading(
        val fileName: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DriveDownloadProgressState()
    data class Installing(val fileName: String) : DriveDownloadProgressState()
    data class Failed(val error: String) : DriveDownloadProgressState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TentangAplikasiScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Google Drive Apps Script Update States
    var driveUpdateState by remember {
        val lastChecked = GoogleDriveUpdateManager.getLastCheckedMillis(context)
        mutableStateOf<DriveUpdateCheckState>(DriveUpdateCheckState.Idle(lastChecked))
    }
    var inlineDownloadState by remember { mutableStateOf<DriveDownloadProgressState>(DriveDownloadProgressState.Idle) }
    var showDriveConfigDialog by remember { mutableStateOf(false) }
    var isDriveAutoCheckStartup by remember { mutableStateOf(GoogleDriveUpdateManager.isAutoCheckOnStartup(context)) }
    var isDriveAutoInstall by remember { mutableStateOf(GoogleDriveUpdateManager.isAutoInstallLatest(context)) }

    var showShareNameDialog by remember { mutableStateOf(false) }
    var isSharingApk by remember { mutableStateOf(false) }
    var customApkName by remember { mutableStateOf("MySingghara-v${BuildConfig.VERSION_NAME}.apk") }
    var showFirestoreRulesDialog by remember { mutableStateOf(false) }

    val apkInfo = remember { GitHubUpdateManager.getInstalledApkInfo(context) }
    val apkSizeFormatted = remember(apkInfo) {
        if (apkInfo.first > 0) {
            String.format(Locale.getDefault(), "%.1f MB", apkInfo.first / (1024.0 * 1024.0))
        } else {
            "~15 MB"
        }
    }

    fun downloadAndInstall(apkItem: DriveApkItem) {
        scope.launch {
            try {
                inlineDownloadState = DriveDownloadProgressState.Downloading(apkItem.name, 0f, 0L, apkItem.size)
                val downloadedFile = GoogleDriveUpdateManager.downloadDriveApk(
                    context = context,
                    fileItem = apkItem
                ) { progress, downloaded, total ->
                    inlineDownloadState = DriveDownloadProgressState.Downloading(apkItem.name, progress, downloaded, total)
                }

                inlineDownloadState = DriveDownloadProgressState.Installing(apkItem.name)
                val success = GoogleDriveUpdateManager.installApk(context, downloadedFile, apkItem)
                if (success) {
                    inlineDownloadState = DriveDownloadProgressState.Idle
                    viewModel.showToastPopup("Membuka installer paket APK...")
                } else {
                    inlineDownloadState = DriveDownloadProgressState.Failed("Gagal membuka installer APK. Pastikan izin instalasi aplikasi tidak dikenal telah diizinkan.")
                }
            } catch (e: Exception) {
                inlineDownloadState = DriveDownloadProgressState.Failed(e.localizedMessage ?: "Gagal mengunduh berkas APK dari Google Drive.")
            }
        }
    }

    fun executeShareApk(targetName: String = customApkName) {
        scope.launch {
            isSharingApk = true
            viewModel.showToastPopup("Menyiapkan file APK ($targetName)...")
            val success = GitHubUpdateManager.shareInstalledApk(context, targetName)
            isSharingApk = false
            if (!success) {
                viewModel.showToastPopup("Gagal menyiapkan file APK untuk dibagikan.")
            }
        }
    }

    // Function to perform Google Drive Apps Script update check
    fun triggerDriveCheck() {
        scope.launch {
            driveUpdateState = DriveUpdateCheckState.Loading()
            val result = GoogleDriveUpdateManager.checkForDriveUpdate(context)
            driveUpdateState = result
            // Suppress popup dialog completely on this screen.
            // If auto-install is enabled, start inline download directly.
            if (result is DriveUpdateCheckState.UpdateAvailable && isDriveAutoInstall) {
                downloadAndInstall(result.latestApk)
            }
        }
    }

    // Automatically check Google Drive on entry if idle
    LaunchedEffect(Unit) {
        if (driveUpdateState is DriveUpdateCheckState.Idle) {
            triggerDriveCheck()
        }
    }

    // Modal Dialog for Customizing APK File Name before Sharing
    if (showShareNameDialog) {
        ShareApkCustomNameDialog(
            currentName = customApkName,
            versionName = BuildConfig.VERSION_NAME,
            onDismiss = { showShareNameDialog = false },
            onSaveAndShare = { chosenName, immediatelyShare ->
                val normalized = if (chosenName.endsWith(".apk", ignoreCase = true)) chosenName else "$chosenName.apk"
                customApkName = normalized
                showShareNameDialog = false
                if (immediatelyShare) {
                    executeShareApk(normalized)
                } else {
                    viewModel.showToastPopup("Nama file APK diatur menjadi: $normalized")
                }
            }
        )
    }

    // Modal Dialog for Google Drive Apps Script Configuration
    if (showDriveConfigDialog) {
        DriveScriptConfigDialog(
            currentUrl = GoogleDriveUpdateManager.getAppsScriptUrl(context),
            onDismiss = { showDriveConfigDialog = false },
            onSave = { newUrl ->
                GoogleDriveUpdateManager.saveAppsScriptUrl(context, newUrl)
                showDriveConfigDialog = false
                viewModel.showToastPopup("URL Google Apps Script berhasil disimpan!")
                triggerDriveCheck()
            },
            onReset = {
                GoogleDriveUpdateManager.saveAppsScriptUrl(context, GoogleDriveUpdateManager.DEFAULT_APPS_SCRIPT_URL)
                showDriveConfigDialog = false
                viewModel.showToastPopup("URL Google Apps Script di-reset ke default!")
                triggerDriveCheck()
            }
        )
    }

    // Modal Dialog Aturan Firestore Rules Helper (agar tidak lupa aturan keamanan Firebase)
    if (showFirestoreRulesDialog) {
        com.example.ui.components.FirestoreRulesHelperDialog(
            onDismissRequest = { showFirestoreRulesDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("tentang_aplikasi_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            // -----------------------------------------------------------------
            // 1. HEADER IDENTITAS APLIKASI
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Icon Info di pojok kanan atas kartu My Singghara untuk membuka Aturan Rules Firestore
                    IconButton(
                        onClick = { showFirestoreRulesDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Aturan Rules Firestore",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App Logo Badge
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF0284C7))
                                    )
                                )
                                .shadow(6.dp, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "My Singghara Logo",
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "My Singghara",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Indikator status pembaruan (dipindahkan dari kartu pembaruan aplikasi)
                        val (statusLabel, statusColor) = when (driveUpdateState) {
                            is DriveUpdateCheckState.Loading -> Pair("Memeriksa...", Color(0xFF0F766E))
                            is DriveUpdateCheckState.UpdateAvailable -> Pair("Ada APK Baru!", Color(0xFF0F766E))
                            is DriveUpdateCheckState.UpToDate -> Pair("Versi Terbaru ✨", Color(0xFF16A34A))
                            is DriveUpdateCheckState.NoApkFound -> Pair("Belum Ada APK", Color(0xFF64748B))
                            is DriveUpdateCheckState.Error -> Pair("Perlu Perhatian", MaterialTheme.colorScheme.error)
                            is DriveUpdateCheckState.Idle -> Pair("Siap Periksa", MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (driveUpdateState is DriveUpdateCheckState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp,
                                        color = statusColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = statusLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 2. KARTU PEMBARUAN APLIKASI (GOOGLE DRIVE APPS SCRIPT)
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth().testTag("app_update_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Pembaruan Aplikasi",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Icon "Konfigurasi URL", "Buka Share Drive", dan "Reset Riwayat Deteksi Lokal" tanpa title di sebelah kanan
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { showDriveConfigDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Konfigurasi URL",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.currentRoute = "share" },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Buka Share Drive",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    GoogleDriveUpdateManager.clearInstalledApkHistory(context)
                                    viewModel.showToastPopup("Riwayat pembaruan lokal berhasil di-reset. Memeriksa ulang...")
                                    triggerDriveCheck()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Riwayat Deteksi Lokal",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // -----------------------------------------------------
                    // GOOGLE DRIVE APPS SCRIPT UPDATE SECTION
                    // -----------------------------------------------------

                        // Dynamic State for Drive
                        when (val state = driveUpdateState) {
                            is DriveUpdateCheckState.Idle -> {
                                val lastCheckedStr = if (state.lastCheckedMillis > 0L) {
                                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
                                    sdf.format(Date(state.lastCheckedMillis))
                                } else {
                                    "Belum pernah diperiksa"
                                }
                                Text(
                                    text = "Terakhir diperiksa: $lastCheckedStr\nEndpoint: Google Apps Script Web App",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is DriveUpdateCheckState.Loading -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F766E).copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFF0F766E)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = state.message,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            is DriveUpdateCheckState.UpdateAvailable -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF0F766E).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.NewReleases,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0F766E),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "APK Baru Tersedia di Drive! 🚀",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 13.5.sp,
                                                    color = Color(0xFF0F766E)
                                                )
                                            }
                                            Text(
                                                text = state.latestApk.sizeFormatted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // File details
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Nama File: ${state.latestApk.name}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Ukuran: ${state.latestApk.sizeFormatted}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F766E)
                                            )
                                        }

                                        Text(
                                            text = state.detectionReason,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // Inline Download Progress / Action Button
                                        when (val dState = inlineDownloadState) {
                                            is DriveDownloadProgressState.Downloading -> {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0F766E).copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.3f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Mengunduh APK (${(dState.progress * 100).toInt()}%)...",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF0F766E)
                                                            )
                                                            val mbDown = String.format(Locale.US, "%.1f", dState.downloadedBytes / (1024.0 * 1024.0))
                                                            val mbTotal = String.format(Locale.US, "%.1f MB", dState.totalBytes / (1024.0 * 1024.0))
                                                            Text(
                                                                text = "$mbDown / $mbTotal",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        LinearProgressIndicator(
                                                            progress = { dState.progress },
                                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                            color = Color(0xFF0F766E)
                                                        )
                                                    }
                                                }
                                            }
                                            is DriveDownloadProgressState.Installing -> {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color(0xFF0F766E).copy(alpha = 0.12f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color(0xFF0F766E)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = "Membuka penginstal paket APK...",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0F766E)
                                                        )
                                                    }
                                                }
                                            }
                                            is DriveDownloadProgressState.Failed -> {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = dState.error,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        Button(
                                                            onClick = { downloadAndInstall(state.latestApk) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                                                        ) {
                                                            Text("Coba Unduh Lagi", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                            is DriveDownloadProgressState.Idle -> {
                                                // Icon unduh telah dipindahkan ke samping icon periksa pembaruan
                                            }
                                        }

                                        // List all APKs if multiple found in Drive
                                        if (state.allApks.size > 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Text(
                                                text = "Semua Berkas APK di Google Drive (${state.allApks.size} file):",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            state.allApks.forEach { apk ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                            Text(
                                                                text = apk.name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 11.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = apk.sizeFormatted,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        FilledTonalButton(
                                                            onClick = { downloadAndInstall(apk) },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("Pilih", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is DriveUpdateCheckState.UpToDate -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Aplikasi Anda Sudah Versi Terbaru! ✨",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF16A34A)
                                                )
                                                val detailText = if (state.latestApk != null) {
                                                    "Berkas di Drive: ${state.latestApk.name} (${state.latestApk.sizeFormatted})"
                                                } else {
                                                    "Versi v${state.currentAppVersion} adalah yang teranyar."
                                                }
                                                Text(
                                                    text = detailText,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }


                                        // List all APKs if multiple found in Drive
                                        if (state.allApks.size > 1) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Text(
                                                text = "Berkas APK Lain di Drive (${state.allApks.size} file):",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            state.allApks.forEach { apk ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                            Text(
                                                                text = apk.name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 11.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = apk.sizeFormatted,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        FilledTonalButton(
                                                            onClick = { downloadAndInstall(apk) },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("Pilih", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is DriveUpdateCheckState.NoApkFound -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Belum Ada File APK di Google Drive",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "Folder Google Drive Anda belum memiliki file installer berekstensi .apk. Bagikan file APK aplikasi ini sekarang:",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            onClick = { executeShareApk() },
                                            enabled = !isSharingApk,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Bagikan APK Aplikasi Ini ke Drive", fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }

                            is DriveUpdateCheckState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Gagal Memeriksa Google Drive",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = state.errorMessage,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Row aksi: Icon Unduh & Pasang APK Sekarang (kiri) & Icon Periksa Pembaruan Google Drive Sekarang (kanan)
                        val latestApkFromDrive: DriveApkItem? = when (val s = driveUpdateState) {
                            is DriveUpdateCheckState.UpdateAvailable -> s.latestApk
                            is DriveUpdateCheckState.UpToDate -> s.latestApk
                            else -> null
                        }
                        val isDriveLoading = driveUpdateState is DriveUpdateCheckState.Loading
                        val isDownloading = inlineDownloadState is DriveDownloadProgressState.Downloading || inlineDownloadState is DriveDownloadProgressState.Installing
                        val canDownload = latestApkFromDrive != null && !isDownloading

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Unduh & Pasang APK Sekarang tanpa title (sebelah kiri)
                            FilledIconButton(
                                onClick = {
                                    latestApkFromDrive?.let { downloadAndInstall(it) }
                                },
                                enabled = canDownload,
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF0F766E),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Unduh & Pasang APK Sekarang",
                                    tint = if (canDownload) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Icon Periksa Pembaruan Google Drive Sekarang tanpa title (sebelah kanan)
                            FilledIconButton(
                                onClick = { triggerDriveCheck() },
                                enabled = !isDriveLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color(0xFF0F766E),
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDriveLoading) Icons.Default.HourglassTop else Icons.Default.Refresh,
                                    contentDescription = "Periksa Pembaruan Google Drive Sekarang",
                                    tint = if (!isDriveLoading) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switch: Periksa Otomatis saat Startup
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Periksa Otomatis saat Startup",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Switch(
                                checked = isDriveAutoCheckStartup,
                                onCheckedChange = { isChecked ->
                                    isDriveAutoCheckStartup = isChecked
                                    GoogleDriveUpdateManager.setAutoCheckOnStartup(context, isChecked)
                                }
                            )
                        }

                        // Switch: Instal Otomatis jika Ditemukan APK Baru
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Instal Otomatis jika Ada APK Baru",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Switch(
                                checked = isDriveAutoInstall,
                                onCheckedChange = { isChecked ->
                                    isDriveAutoInstall = isChecked
                                    GoogleDriveUpdateManager.setAutoInstallLatest(context, isChecked)
                                }
                            )
                        }
                    }
                }

            // -----------------------------------------------------------------
            // 3. KARTU FIREBASE CLOUD MESSAGING (FCM) & NOTIFIKASI SISTEM OS
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth().testTag("fcm_cloud_messaging_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Firebase Cloud Messaging Siap",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Token Information Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Registration Token Perangkat:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (viewModel.isFetchingFcmToken) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                val statusLabel = when {
                                    viewModel.isFetchingFcmToken -> "Memeriksa..."
                                    viewModel.fcmToken.isNotEmpty() -> "Siap (${viewModel.fcmTokenSource})"
                                    else -> "Memuat..."
                                }
                                val statusColor = if (viewModel.fcmToken.isNotEmpty()) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary

                                Text(
                                    text = statusLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }

                            val displayToken = viewModel.fcmToken.ifEmpty { "Token pendaftaran sedang dimuat..." }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = displayToken,
                                    fontSize = 10.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }

                            if (viewModel.fcmTokenError != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Status: ${viewModel.fcmTokenError}. Token perangkat di atas tetap aktif & siap digunakan.",
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons - Row 1 (Token management)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val tokenToCopy = viewModel.fcmToken.ifEmpty { "Token belum siap" }
                                clipboardManager.setText(AnnotatedString(tokenToCopy))
                                viewModel.showToastPopup("Token FCM berhasil disalin ke clipboard!")
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salin Token", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.refreshFcmToken()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Segarkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }


                }
            }

            // -----------------------------------------------------------------
            // 4. KARTU BAGIKAN APLIKASI (BERBAGI FILE .APK LANGSUNG)
            // -----------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth().testTag("bagikan_aplikasi_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Bagikan Aplikasi (.apk)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // File Info Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = customApkName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Ukuran Paket: $apkSizeFormatted • Android Package (.apk)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showShareNameDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Ubah Nama File APK",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Share Button
                    Button(
                        onClick = { executeShareApk() },
                        enabled = !isSharingApk,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F766E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("share_apk_main_button")
                    ) {
                        if (isSharingApk) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Mempersiapkan File APK...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bagikan File $customApkName", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Action Buttons: Rename APK & Share
                    OutlinedButton(
                        onClick = { showShareNameDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ganti Nama File APK", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Modal Dialog to customize and set the .apk file name before sharing.
 */
@Composable
fun ShareApkCustomNameDialog(
    currentName: String,
    versionName: String,
    onDismiss: () -> Unit,
    onSaveAndShare: (name: String, immediatelyShare: Boolean) -> Unit
) {
    var fileNameInput by remember {
        mutableStateOf(
            if (currentName.endsWith(".apk", ignoreCase = true)) {
                currentName.substringBeforeLast(".apk")
            } else {
                currentName
            }
        )
    }

    val presets = listOf(
        "MySingghara-v$versionName",
        "My-Singghara",
        "MySingghara-Installer",
        "Aplikasi-MySingghara"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Kustomisasi Nama File APK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sesuaikan nama file saat dibagikan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("Nama File (.apk)") },
                    placeholder = { Text("contoh: MySingghara-v$versionName") },
                    trailingIcon = {
                        Text(
                            text = ".apk",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pilihan Cepat Nama File:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(2).forEach { preset ->
                        AssistChip(
                            onClick = { fileNameInput = preset },
                            label = { Text(preset, fontSize = 10.5.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.drop(2).forEach { preset ->
                        AssistChip(
                            onClick = { fileNameInput = preset },
                            label = { Text(preset, fontSize = 10.5.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("Batal", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val finalName = fileNameInput.trim().ifBlank { "MySingghara-v$versionName" }
                            onSaveAndShare(finalName, false)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simpan", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val finalName = fileNameInput.trim().ifBlank { "MySingghara-v$versionName" }
                            onSaveAndShare(finalName, true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dialog for configuring Google Drive Apps Script URL and testing connectivity.
 */
@Composable
fun DriveScriptConfigDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Konfigurasi Google Drive",
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
                    text = "URL Web App Google Apps Script digunakan untuk memeriksa dan mengunduh berkas installer .apk terbaru di Google Drive.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL Web App Google Apps Script") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            if (clip.isNotBlank()) url = clip
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                        }
                    }
                )

                testResult?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) Color(0xFF16A34A).copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isSuccess) Color(0xFF16A34A).copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 11.5.sp,
                            color = if (isSuccess) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = "Menguji koneksi ke Google Apps Script & memindai APK..."
                            val result = GoogleDriveUpdateManager.testConnection(context, url.trim())
                            isTesting = false
                            if (result.first) {
                                isSuccess = true
                                testResult = "✅ ${result.second}"
                            } else {
                                isSuccess = false
                                testResult = "❌ ${result.second}"
                            }
                        }
                    },
                    enabled = !isTesting && url.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menguji...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Uji Koneksi & Pindai File APK", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = { onSave(url.trim()) },
                        enabled = url.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Text("Simpan URL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
