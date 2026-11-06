package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.components.AppUserAvatar
import com.example.ui.components.PhotoRepositionDialog
import com.example.ui.components.defaultAvatarGradients
import com.example.ui.components.defaultAvatarIcons
import com.example.util.SinggharaFolderManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOTAL_SETUP_STEPS = 6
private val SETUP_STEP_LABELS = listOf("Mulai", "Profil", "Keamanan", "Izin", "Cadangan", "Selesai")

@Composable
fun FirstInstallSetupScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    // Status navigasi disimpan di SaveableRegistry: tanpa ini rotasi / proses recreate mengembalikan
    // pengguna ke langkah 1 padahal nilai langkah sebelumnya sudah tertulis ke SharedPreferences.
    var currentStep by rememberSaveable { mutableStateOf(1) }
    var furthestStep by rememberSaveable { mutableStateOf(1) }
    var isAdvancing by rememberSaveable { mutableStateOf(true) }

    var tempProfileName by rememberSaveable { mutableStateOf(viewModel.profileName) }
    var selectedAvatarIndex by rememberSaveable { mutableStateOf(viewModel.profileAvatarIndex) }
    var profileNameError by rememberSaveable { mutableStateOf("") }

    var pinEnabled by rememberSaveable { mutableStateOf(viewModel.isAppLockEnabled) }
    var pinAlreadySaved by rememberSaveable { mutableStateOf(viewModel.appPin.length == 4) }
    var activePinField by rememberSaveable { mutableStateOf(1) }
    var pinErrorMessage by rememberSaveable { mutableStateOf("") }
    // Digit PIN sengaja tidak dipertahankan lintas recreate.
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    var backupStatusMessage by rememberSaveable { mutableStateOf("") }
    var isBackupStatusError by rememberSaveable { mutableStateOf(false) }

    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showSkipDialog by rememberSaveable { mutableStateOf(false) }
    var photoToRepositionUri by remember { mutableStateOf<Uri?>(null) }

    var rootFolderPath by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        SinggharaFolderManager.ensureFoldersExistAsync(context)
        rootFolderPath = withContext(Dispatchers.IO) {
            runCatching { SinggharaFolderManager.getRootFolder(context).absolutePath }.getOrDefault("")
        }
    }

    // Status izin dibaca dari file/sistem, jadi diambil di IO dispatcher dan disegarkan lagi setiap
    // Activity resumed; tanpa ini kembali dari Setelan Android membuat badge wizard tetap basi.
    var notificationsAllowed by rememberSaveable { mutableStateOf(false) }
    var mediaAccessAllowed by rememberSaveable { mutableStateOf(false) }
    var allFilesAllowed by rememberSaveable { mutableStateOf(false) }
    var safAccessValid by rememberSaveable { mutableStateOf(false) }
    val statusScope = rememberCoroutineScope()

    fun refreshPermissionStatuses() {
        statusScope.launch {
            withContext(Dispatchers.IO) {
                val notif = isNotificationAccessEnabled(context)
                val media = storagePermissionsGranted(context)
                val allFiles = hasAllFilesAccess()
                val safValid = viewModel.safFolderUriStr.isNotEmpty() && viewModel.isSafFolderConfigured(context)
                notificationsAllowed = notif
                mediaAccessAllowed = media
                allFilesAllowed = allFiles
                safAccessValid = safValid
            }
        }
    }

    LaunchedEffect(Unit) { refreshPermissionStatuses() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissionStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun goToStep(step: Int) {
        val target = step.coerceIn(1, TOTAL_SETUP_STEPS)
        isAdvancing = target > currentStep
        currentStep = target
        if (target > furthestStep) furthestStep = target
    }

    fun persistProfile() {
        val name = tempProfileName.trim()
        if (name.isNotEmpty() && name != viewModel.profileName) viewModel.saveProfileName(name)
        if (selectedAvatarIndex != viewModel.profileAvatarIndex) viewModel.saveProfileAvatarIndex(selectedAvatarIndex)
    }

    fun finishSetup() {
        persistProfile()
        viewModel.completeFirstInstallSetup()
        onComplete()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.preventAutoLock = false
        if (uri != null) photoToRepositionUri = uri
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.preventAutoLock = false
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.saveSelectedFolderUri(uri.toString())
                backupStatusMessage = "Folder cadangan SAF aktif dan hak aksesnya tersimpan permanen."
                isBackupStatusError = false
                refreshPermissionStatuses()
                viewModel.showToastPopup("Folder SAF berhasil dikonfigurasi!")
            } catch (e: Exception) {
                backupStatusMessage = "Gagal mengambil hak akses folder: ${e.message}"
                isBackupStatusError = true
                viewModel.showToastPopup("Gagal mengambil hak akses folder SAF.")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshPermissionStatuses()
        if (granted) viewModel.showToastPopup("Izin notifikasi berhasil diaktifkan!")
        else viewModel.showToastPopup("Izin notifikasi ditolak, pengingat sholat dan agenda tidak akan muncul.")
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        refreshPermissionStatuses()
        if (result.values.any { it }) viewModel.showToastPopup("Akses media berhasil diaktifkan!")
        else viewModel.showToastPopup("Akses media ditolak. Cadangan tetap berjalan di folder privat aplikasi.")
    }

    fun handleNext() {
        when (currentStep) {
            1 -> goToStep(2)
            2 -> {
                if (tempProfileName.isBlank()) {
                    profileNameError = "Nama pengguna wajib diisi."
                    viewModel.showToastPopup("Mohon isi nama pengguna Anda.")
                    return
                }
                profileNameError = ""
                persistProfile()
                goToStep(3)
            }
            3 -> {
                if (!pinEnabled) {
                    if (viewModel.appPin.isNotEmpty() || viewModel.isAppLockEnabled) {
                        viewModel.saveAppPin("")
                        viewModel.saveAppLockEnabled(false)
                    }
                    pinAlreadySaved = false
                    goToStep(4)
                    return
                }
                if (pinAlreadySaved) {
                    goToStep(4)
                    return
                }
                if (activePinField == 1) {
                    if (enteredPin.length != 4) {
                        pinErrorMessage = "PIN harus terdiri dari tepat 4 angka."
                        viewModel.showToastPopup("PIN harus terdiri dari tepat 4 angka.")
                    } else {
                        pinErrorMessage = ""
                        activePinField = 2
                    }
                } else {
                    when {
                        confirmPin.length != 4 -> {
                            pinErrorMessage = "PIN konfirmasi harus terdiri dari tepat 4 angka."
                            viewModel.showToastPopup("Mohon lengkapi konfirmasi PIN 4 angka.")
                        }
                        enteredPin != confirmPin -> {
                            pinErrorMessage = "PIN konfirmasi tidak cocok!"
                            confirmPin = ""
                            activePinField = 1
                            viewModel.showToastPopup("PIN konfirmasi tidak cocok, silakan ulangi.")
                        }
                        else -> {
                            viewModel.saveAppPin(enteredPin)
                            viewModel.saveAppLockEnabled(true)
                            pinAlreadySaved = true
                            pinErrorMessage = ""
                            goToStep(4)
                        }
                    }
                }
            }
            else -> {
                if (currentStep < TOTAL_SETUP_STEPS) goToStep(currentStep + 1) else finishSetup()
            }
        }
    }

    fun handleBack() {
        if (currentStep == 3 && pinEnabled && !pinAlreadySaved && activePinField == 2) {
            activePinField = 1
        } else if (currentStep > 1) {
            goToStep(currentStep - 1)
        } else {
            showExitDialog = true
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
            ) {
                SetupBrandHeader(
                    showSkip = currentStep < TOTAL_SETUP_STEPS,
                    onSkip = { showSkipDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SetupStepperRail(
                    currentStep = currentStep,
                    furthestStep = furthestStep,
                    onStepSelect = { target -> if (target < currentStep) goToStep(target) }
                )

                Spacer(modifier = Modifier.height(18.dp))

                AnimatedContent(
                    targetState = currentStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                    transitionSpec = {
                        val offset = if (isAdvancing) 1 else -1
                        val enter = slideInHorizontally(
                            animationSpec = tween(260),
                            initialOffsetX = { width -> offset * width / 5 }
                        ) + fadeIn(animationSpec = tween(220))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(200),
                            targetOffsetX = { width -> -offset * width / 5 }
                        ) + fadeOut(animationSpec = tween(150))
                        enter togetherWith exit
                    },
                    label = "setupStep"
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 6.dp)
                    ) {
                        when (step) {
                            1 -> SetupWelcomeStepView()
                            2 -> SetupProfileStepView(
                                profileImageUri = viewModel.profileImageUri,
                                profileName = tempProfileName,
                                nameError = profileNameError,
                                avatarIndex = selectedAvatarIndex,
                                onNameChange = {
                                    tempProfileName = it
                                    if (profileNameError.isNotEmpty()) profileNameError = ""
                                },
                                onAvatarSelect = { selectedAvatarIndex = it },
                                onPickPhoto = {
                                    viewModel.preventAutoLock = true
                                    imagePickerLauncher.launch("image/*")
                                }
                            )
                            3 -> SetupSecurityStepView(
                                pinEnabled = pinEnabled,
                                pinAlreadySaved = pinAlreadySaved,
                                enteredPin = enteredPin,
                                confirmPin = confirmPin,
                                activeField = activePinField,
                                errorMessage = pinErrorMessage,
                                onPinEnabledChange = { enabled ->
                                    pinEnabled = enabled
                                    if (!enabled) {
                                        enteredPin = ""
                                        confirmPin = ""
                                        pinErrorMessage = ""
                                        activePinField = 1
                                        pinAlreadySaved = false
                                    }
                                },
                                onDigit = { digit ->
                                    if (activePinField == 1) {
                                        if (enteredPin.length < 4) enteredPin += digit
                                    } else {
                                        if (confirmPin.length < 4) confirmPin += digit
                                    }
                                },
                                onBackspace = {
                                    if (activePinField == 1) {
                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                    } else if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                    } else {
                                        activePinField = 1
                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                    }
                                },
                                onResetPin = {
                                    enteredPin = ""
                                    confirmPin = ""
                                    pinErrorMessage = ""
                                    activePinField = 1
                                },
                                onFieldSelect = { activePinField = it },
                                onRedoPin = {
                                    enteredPin = ""
                                    confirmPin = ""
                                    activePinField = 1
                                    pinErrorMessage = ""
                                    pinAlreadySaved = false
                                }
                            )
                            4 -> SetupPermissionStepView(
                                notificationsEnabled = notificationsAllowed,
                                mediaGranted = mediaAccessAllowed,
                                allFilesGranted = allFilesAllowed,
                                onRequestNotification = {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onRequestStorage = {
                                    val missing = missingStoragePermissions(context)
                                    if (missing.isEmpty()) {
                                        viewModel.showToastPopup("Akses media sudah diizinkan.")
                                    } else {
                                        storagePermissionLauncher.launch(missing.toTypedArray())
                                    }
                                },
                                onOpenNotificationSettings = { openNotificationSettings(context, viewModel) },
                                onOpenAllFilesSettings = { openAllFilesAccessSettings(context, viewModel) }
                            )
                            5 -> SetupBackupStepView(
                                hasSafFolder = viewModel.safFolderUriStr.isNotEmpty(),
                                isSafValid = safAccessValid,
                                rootFolderPath = rootFolderPath,
                                backupStatusMessage = backupStatusMessage,
                                isBackupStatusError = isBackupStatusError,
                                onSelectFolderClick = {
                                    viewModel.preventAutoLock = true
                                    safFolderLauncher.launch(null)
                                },
                                onRestoreClick = {
                                    viewModel.importBackupSAF(context) { msg, success ->
                                        backupStatusMessage = msg
                                        isBackupStatusError = !success
                                        if (success) {
                                            // Hasil pemulihan sudah dimuat ulang ke ViewModel; samakan state
                                            // wizard agar ringkasan tidak lagi menampilkan data lama.
                                            tempProfileName = viewModel.profileName
                                            selectedAvatarIndex = viewModel.profileAvatarIndex
                                            pinEnabled = viewModel.isAppLockEnabled
                                            pinAlreadySaved = viewModel.appPin.length == 4
                                            if (pinEnabled && enteredPin.length == 4 && enteredPin == confirmPin) {
                                                viewModel.saveAppPin(enteredPin)
                                                viewModel.saveAppLockEnabled(true)
                                                pinAlreadySaved = true
                                            }
                                            refreshPermissionStatuses()
                                            viewModel.showToastPopup("Data lama berhasil dipulihkan!")
                                        } else {
                                            viewModel.showToastPopup(msg)
                                        }
                                    }
                                }
                            )
                            else -> SetupCompletionStepView(
                                profileName = tempProfileName,
                                isPinActive = viewModel.isAppLockEnabled && viewModel.appPin.length == 4,
                                notificationsEnabled = notificationsAllowed,
                                isSafValid = safAccessValid,
                                rootFolderPath = rootFolderPath
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        FilledTonalButton(
                            onClick = { handleBack() },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("setup_prev_button"),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", modifier = Modifier.size(18.dp))
                        }
                    }

                    Button(
                        onClick = { handleNext() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("setup_next_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = when {
                                currentStep == 1 -> "Mulai Pengaturan"
                                currentStep == TOTAL_SETUP_STEPS -> "Selesai & Buka Aplikasi"
                                else -> "Lanjut"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (currentStep == TOTAL_SETUP_STEPS) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    val pendingPhotoUri = photoToRepositionUri
    if (pendingPhotoUri != null) {
        PhotoRepositionDialog(
            imageUri = pendingPhotoUri,
            onDismiss = { photoToRepositionUri = null },
            onConfirm = { croppedBitmap ->
                viewModel.saveProfileImageBitmap(croppedBitmap)
                selectedAvatarIndex = -1
                photoToRepositionUri = null
                viewModel.showToastPopup("Foto profil berhasil disimpan!")
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Keluar dari pengaturan awal?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Pengaturan yang sudah tersimpan tetap aman. Buka kembali aplikasi untuk melanjutkan wizard.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? android.app.Activity)?.finish()
                }) { Text("Keluar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Lanjut Setup") }
            }
        )
    }

    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            icon = { Icon(Icons.Default.SkipNext, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Lewati pengaturan awal?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Profil, PIN, izin notifikasi, dan folder cadangan SAF akan memakai pengaturan bawaan. Semuanya tetap bisa diubah lewat menu Profil.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    finishSetup()
                }) { Text("Lewati & Selesaikan", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSkipDialog = false }) { Text("Kembali") }
            }
        )
    }
}

@Composable
private fun SetupBrandHeader(
    showSkip: Boolean,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AppLauncherIcon(modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "My Singghara",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pengaturan awal aplikasi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showSkip) {
            TextButton(onClick = onSkip, modifier = Modifier.testTag("setup_skip_button")) {
                Text("Lewati", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SetupStepperRail(
    currentStep: Int,
    furthestStep: Int,
    onStepSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until TOTAL_SETUP_STEPS) {
                val step = index + 1
                val railColor by animateColorAsState(
                    targetValue = when {
                        step == currentStep -> MaterialTheme.colorScheme.primary
                        step < currentStep -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(240),
                    label = "setupRail"
                )
                val canJumpBack = step < currentStep && step <= furthestStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (step == currentStep) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(railColor)
                        .then(if (canJumpBack) Modifier.clickable { onStepSelect(step) } else Modifier)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Langkah $currentStep dari $TOTAL_SETUP_STEPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = SETUP_STEP_LABELS.getOrElse(currentStep - 1) { "" },
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SetupStepHeading(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SetupStatusPill(
    text: String,
    icon: ImageVector,
    isPositive: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isPositive) colorScheme.primaryContainer.copy(alpha = 0.55f)
                else colorScheme.errorContainer.copy(alpha = 0.35f)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPositive) colorScheme.primary else colorScheme.error,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) colorScheme.primary else colorScheme.error
        )
    }
}

@Composable
private fun SetupWelcomeStepView() {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.24f),
                            colorScheme.tertiary.copy(alpha = 0.16f)
                        )
                    )
                )
                .border(1.dp, colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            AppLauncherIcon(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentDescription = "Logo My Singghara"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Selamat Datang di My Singghara",
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Black,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Satu aplikasi untuk keuangan, agenda, ibadah, dan usaha Anda. Wizard singkat ini menyiapkan profil, keamanan, izin, serta folder cadangan.",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SetupInfoChip(Icons.Default.Schedule, "Hanya ±1 menit")
            SetupInfoChip(Icons.Default.Restore, "Bisa diubah kapan saja")
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "FITUR UNGGULAN SIAP DIGUNAKAN",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        SetupFeatureItem(
            icon = Icons.Default.AccountBalanceWallet,
            title = "Keuangan, Kasir & Usaha",
            description = "Buku kas, tabungan, arisan, faktur, dan laporan penjualan."
        )
        Spacer(modifier = Modifier.height(10.dp))
        SetupFeatureItem(
            icon = Icons.Default.CalendarMonth,
            title = "Agenda, Kalender & Catatan",
            description = "Pengingat kegiatan harian, jurnal, dan catatan bertema."
        )
        Spacer(modifier = Modifier.height(10.dp))
        SetupFeatureItem(
            icon = Icons.Default.LibraryBooks,
            title = "Quran Digital & Audio",
            description = "Mushaf, pemutar musik, dan suara adzan dari folder Data/mp3."
        )
        Spacer(modifier = Modifier.height(10.dp))
        SetupFeatureItem(
            icon = Icons.Default.Shield,
            title = "Proteksi Perangkat",
            description = "Kunci PIN, cadangan SAF, dan pemulihan data penuh."
        )
    }
}

@Composable
private fun SetupInfoChip(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SetupFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    SetupCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SetupProfileStepView(
    profileImageUri: String,
    profileName: String,
    nameError: String,
    avatarIndex: Int,
    onNameChange: (String) -> Unit,
    onAvatarSelect: (Int) -> Unit,
    onPickPhoto: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupStepHeading(
            icon = Icons.Default.Person,
            title = "Siapkan Profil Anda",
            description = "Nama ini tampil di sapaan utama, laporan, dan struk kasir."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                AppUserAvatar(
                    name = profileName.ifBlank { "Pengguna" },
                    imageUri = profileImageUri,
                    avatarIndex = avatarIndex,
                    size = 92.dp,
                    textSize = 30.sp
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column {
                Text(
                    text = profileName.ifBlank { "Belum ada nama" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        avatarIndex == -1 && profileImageUri.isNotBlank() -> "Foto kustom dari galeri"
                        avatarIndex in 0..4 -> "Avatar gaya ${avatarIndex + 1}"
                        else -> "Avatar bawaan"
                    },
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.testTag("setup_photo_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pakai Foto", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = profileName,
            onValueChange = onNameChange,
            label = { Text("Nama Pengguna") },
            placeholder = { Text("Contoh: Singghara User") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            isError = nameError.isNotEmpty(),
            supportingText = {
                Text(if (nameError.isNotEmpty()) nameError else "Nama ini dapat diubah kembali di menu Profil.")
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "GAYA AVATAR",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0..4) {
                val isSelected = avatarIndex == index
                val brush = defaultAvatarGradients.getOrElse(index) { defaultAvatarGradients[0] }
                val icon = defaultAvatarIcons.getOrElse(index) { defaultAvatarIcons[0] }
                Box(modifier = Modifier.size(52.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(brush)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onAvatarSelect(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Avatar ${index + 1}",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Foto hanya dipakai untuk avatar; berkas asli tetap tersimpan di galeri Anda.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SetupSecurityStepView(
    pinEnabled: Boolean,
    pinAlreadySaved: Boolean,
    enteredPin: String,
    confirmPin: String,
    activeField: Int,
    errorMessage: String,
    onPinEnabledChange: (Boolean) -> Unit,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onResetPin: () -> Unit,
    onFieldSelect: (Int) -> Unit,
    onRedoPin: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(enteredPin, activeField) {
        if (activeField == 1 && enteredPin.length == 4) {
            delay(160)
            onFieldSelect(2)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupStepHeading(
            icon = Icons.Default.Shield,
            title = "Keamanan Aplikasi",
            description = "Kunci aplikasi dengan PIN 4 digit agar data keuangan tidak dibuka orang lain."
        )

        Spacer(modifier = Modifier.height(18.dp))

        SetupCard(
            containerColor = if (pinEnabled) colorScheme.primaryContainer.copy(alpha = 0.28f) else colorScheme.surface
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (pinEnabled) "Kunci PIN AKTIF" else "Kunci PIN NONAKTIF",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Aplikasi otomatis terkunci saat layar mati atau aplikasi ditutup.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pinEnabled,
                    onCheckedChange = onPinEnabledChange,
                    modifier = Modifier.testTag("setup_pin_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!pinEnabled) {
            SetupCard(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                borderColor = Color.Transparent
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Anda bisa mengaktifkan PIN dan biometrik kapan saja lewat menu Profil.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (pinAlreadySaved) {
            SetupCard(borderColor = colorScheme.primary.copy(alpha = 0.35f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PIN 4 digit sudah tersimpan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "PIN tidak ditampilkan kembali. Tekan Lanjut untuk meneruskan.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRedoPin,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Atur Ulang PIN", fontSize = 12.sp)
                }
            }
        } else {
            val shownPin = if (activeField == 1) enteredPin else confirmPin

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SetupPinFieldTab(
                    label = "Buat PIN",
                    selected = activeField == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { onFieldSelect(1) }
                )
                SetupPinFieldTab(
                    label = "Konfirmasi PIN",
                    selected = activeField == 2,
                    enabled = enteredPin.length == 4,
                    modifier = Modifier.weight(1f),
                    onClick = { onFieldSelect(2) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until 4) {
                    val isFilled = index < shownPin.length
                    val dotColor by animateColorAsState(
                        targetValue = if (isFilled) colorScheme.primary
                        else colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        animationSpec = tween(180),
                        label = "setupPinDot"
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.dp, colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                    )
                    if (index < 3) Spacer(modifier = Modifier.width(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("123", "456", "789").forEach { rowDigits ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowDigits.forEach { digit ->
                            SetupKeypadButton(text = digit, modifier = Modifier.size(64.dp)) { onDigit(digit) }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onResetPin,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                        shape = CircleShape,
                        color = colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Ulangi PIN",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    SetupKeypadButton(text = "0", modifier = Modifier.size(64.dp)) { onDigit("0") }
                    Surface(
                        onClick = onBackspace,
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                        shape = CircleShape,
                        color = colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Hapus",
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "PIN disimpan hanya di perangkat ini dan tidak ikut terekspor ke berkas cadangan SAF.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SetupPinFieldTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.border(
            width = 1.dp,
            color = when {
                !enabled -> colorScheme.outlineVariant.copy(alpha = 0.4f)
                selected -> colorScheme.primary
                else -> colorScheme.outlineVariant
            },
            shape = RoundedCornerShape(14.dp)
        ),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colorScheme.primaryContainer.copy(alpha = 0.45f) else colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (!enabled) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SetupKeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = modifier.shadow(elevation = 6.dp, shape = CircleShape),
        shape = CircleShape,
        color = if (isPressed) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPressed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SetupPermissionStepView(
    notificationsEnabled: Boolean,
    mediaGranted: Boolean,
    allFilesGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestStorage: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAllFilesSettings: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val canPromptNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupStepHeading(
            icon = Icons.Default.NotificationsActive,
            title = "Izin Penting",
            description = "Dua izin ini menentukan apakah pengingat harian dan ekspor data bekerja penuh."
        )

        Spacer(modifier = Modifier.height(18.dp))

        SetupCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "NOTIFIKASI & PENGINGAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = colorScheme.primary
                )
                SetupStatusPill(
                    text = if (notificationsEnabled) "Aktif" else "Belum aktif",
                    icon = if (notificationsEnabled) Icons.Default.CheckCircle else Icons.Default.Notifications,
                    isPositive = notificationsEnabled
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Diperlukan untuk suara adzan, pengingat agenda, dan notifikasi usaha di status bar.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (notificationsEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notifikasi sudah diizinkan di perangkat ini.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary
                    )
                }
            } else {
                if (canPromptNotifications) {
                    Button(
                        onClick = onRequestNotification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_notification_permission_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Izinkan Notifikasi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                TextButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Buka pengaturan notifikasi aplikasi", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SetupCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AKSES MEDIA & FILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = colorScheme.primary
                )
                SetupStatusPill(
                    text = if (mediaGranted) "Media terbuka" else "Media terkunci",
                    icon = if (mediaGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    isPositive = mediaGranted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Dipakai untuk memutar musik dari folder Data/mp3, memasang font jam digital, dan mengekspor laporan.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onRequestStorage,
                enabled = !mediaGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_storage_permission_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    disabledContainerColor = colorScheme.surfaceVariant,
                    disabledContentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = if (mediaGranted) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (mediaGranted) "Akses media sudah diberikan" else "Izinkan Akses Media",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Akses semua file",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Opsional. Tanpa izin ini, folder My Singghara memakai penyimpanan privat aplikasi.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    SetupStatusPill(
                        text = if (allFilesGranted) "Aktif" else "Nonaktif",
                        icon = if (allFilesGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        isPositive = allFilesGranted
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenAllFilesSettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Buka setelan akses file", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SetupCard(
            containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.18f),
            borderColor = colorScheme.tertiary.copy(alpha = 0.3f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Semua izin dapat dicabut atau diberikan ulang lewat Setelan Android > Aplikasi > My Singghara.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SetupBackupStepView(
    hasSafFolder: Boolean,
    isSafValid: Boolean,
    rootFolderPath: String,
    backupStatusMessage: String,
    isBackupStatusError: Boolean,
    onSelectFolderClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        SetupStepHeading(
            icon = Icons.Default.Backup,
            title = "Cadangan & Pemulihan",
            description = "Struktur folder dibuat otomatis. Folder SAF opsional untuk memindahkan cadangan ke file manager atau kartu SD."
        )

        Spacer(modifier = Modifier.height(18.dp))

        SetupCard(
            containerColor = colorScheme.primaryContainer.copy(alpha = 0.2f),
            borderColor = colorScheme.primary.copy(alpha = 0.3f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STRUKTUR FOLDER OTOMATIS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (rootFolderPath.isNotEmpty()) rootFolderPath else "Sedang menyiapkan folder My Singghara...",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
                color = colorScheme.onSurface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(10.dp))
            SetupBullet("Back Up - berkas cadangan database")
            SetupBullet("Data/mp3 - suara adzan kustom & musik")
            SetupBullet("Data/font - font jam digital (.ttf)")
        }

        Spacer(modifier = Modifier.height(14.dp))

        SetupCard {
            Text(
                text = "FOLDER CADANGAN SAF (OPSIONAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        !hasSafFolder -> "Belum dipilih. Cadangan memakai struktur folder di atas."
                        isSafValid -> "Folder SAF aktif dengan hak akses permanen."
                        else -> "Folder SAF terdaftar, tetapi izin aksesnya sudah hilang."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = if (hasSafFolder && !isSafValid) colorScheme.error else colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                SetupStatusPill(
                    text = if (isSafValid) "Aktif" else "Belum",
                    icon = if (isSafValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                    isPositive = isSafValid
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSelectFolderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_select_saf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasSafFolder) "Ubah Folder SAF" else "Pilih Folder Penyimpanan SAF",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SetupCard {
            Text(
                text = "Pulihkan Data Dari Perangkat Lama",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Impor berkas mysingghara_backup.json. PIN dan folder perangkat ini tetap dipertahankan.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRestoreClick,
                enabled = isSafValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.tertiary,
                    disabledContainerColor = colorScheme.surfaceVariant,
                    disabledContentColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup_restore_saf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSafValid) "Impor / Pulihkan Sekarang" else "Pilih folder SAF lebih dulu",
                    fontSize = 12.sp
                )
            }
        }

        if (backupStatusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupCard(
                containerColor = if (isBackupStatusError) colorScheme.errorContainer.copy(alpha = 0.25f)
                else colorScheme.primaryContainer.copy(alpha = 0.25f),
                borderColor = if (isBackupStatusError) colorScheme.error.copy(alpha = 0.35f)
                else colorScheme.primary.copy(alpha = 0.35f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isBackupStatusError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isBackupStatusError) colorScheme.error else colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = backupStatusMessage,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = "\u2022  ",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SetupCompletionStepView(
    profileName: String,
    isPinActive: Boolean,
    notificationsEnabled: Boolean,
    isSafValid: Boolean,
    rootFolderPath: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colorScheme.primary.copy(alpha = 0.38f),
                                colorScheme.tertiary.copy(alpha = 0.18f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pengaturan Selesai!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tekan tombol di bawah untuk membuka aplikasi. Semua pilihan ini bisa diubah kembali di menu Profil.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "RINGKASAN KONFIGURASI",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        SetupCard {
            SetupSummaryRow(
                icon = Icons.Default.Person,
                label = "Profil",
                value = profileName.ifBlank { "Pengguna" },
                isPositive = profileName.isNotBlank()
            )
            SetupSummaryDivider()
            SetupSummaryRow(
                icon = Icons.Default.Lock,
                label = "Kunci PIN",
                value = if (isPinActive) "Aktif (4 digit)" else "Nonaktif",
                isPositive = isPinActive
            )
            SetupSummaryDivider()
            SetupSummaryRow(
                icon = Icons.Default.NotificationsActive,
                label = "Notifikasi",
                value = if (notificationsEnabled) "Diizinkan" else "Belum diizinkan",
                isPositive = notificationsEnabled
            )
            SetupSummaryDivider()
            SetupSummaryRow(
                icon = Icons.Default.Folder,
                label = "Folder data",
                value = rootFolderPath.ifBlank { "Penyimpanan privat aplikasi" },
                isPositive = rootFolderPath.isNotEmpty()
            )
            SetupSummaryDivider()
            SetupSummaryRow(
                icon = Icons.Default.Backup,
                label = "Folder cadangan SAF",
                value = if (isSafValid) "Aktif & permanen" else "Belum dikonfigurasi",
                isPositive = isSafValid
            )
        }

        if (!isSafValid || !notificationsEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            SetupCard(
                containerColor = colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                borderColor = colorScheme.tertiary.copy(alpha = 0.3f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when {
                            !notificationsEnabled && !isSafValid ->
                                "Pengingat belum aktif dan folder SAF belum dipilih. Lengkapi keduanya lewat menu Profil agar notifikasi dan cadangan berjalan penuh."
                            !notificationsEnabled ->
                                "Pengingat sholat dan agenda butuh izin notifikasi. Aktifkan lewat menu Profil."
                            else ->
                                "Cadangan masih memakai folder privat. Pilih folder SAF lewat menu Profil bila ingin memindahkannya."
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupSummaryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SetupSummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    isPositive: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                color = colorScheme.onSurface
            )
        }
        Icon(
            imageVector = if (isPositive) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isPositive) colorScheme.primary else colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun AppLauncherIcon(
    modifier: Modifier = Modifier,
    contentDescription: String = "Logo Aplikasi"
) {
    val context = LocalContext.current
    // Adaptive-icon XML (mipmap-anydpi-v26) tidak bisa didecode oleh painterResource,
    // jadi ikon launcher diambil dari PackageManager.
    val iconBitmap = remember {
        runCatching {
            val sizePx = 288
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(android.graphics.Canvas(bitmap))
            bitmap.asImageBitmap()
        }.getOrNull()
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = com.example.R.drawable.ic_app_logo),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    return runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
}

private fun hasAllFilesAccess(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
    return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
}

private fun storagePermissionCandidates(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

private fun storagePermissionsGranted(context: Context): Boolean {
    return storagePermissionCandidates().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun missingStoragePermissions(context: Context): List<String> {
    return storagePermissionCandidates().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

private fun openNotificationSettings(context: Context, viewModel: MainViewModel) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        viewModel.showToastPopup("Gagal membuka pengaturan notifikasi: ${e.message}")
    }
}

private fun openAllFilesAccessSettings(context: Context, viewModel: MainViewModel) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        viewModel.showToastPopup("Gagal membuka setelan akses file: ${e.message}")
    }
}
