package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.PhotoRepositionDialog

@Composable
fun FirstInstallSetupScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(1) }
    val totalSteps = 5
    val scrollState = rememberScrollState()

    // Temporary setup values
    var pinEnabled by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf("") }
    var activePinField by remember { mutableStateOf(1) } // 1: Buat PIN, 2: Konfirmasi PIN

    var backupStatusMessage by remember { mutableStateOf("") }
    var isBackupStatusError by remember { mutableStateOf(false) }

    // Profile temporary state
    var tempProfileName by remember { mutableStateOf(viewModel.profileName) }
    var selectedAvatarIndex by remember { mutableStateOf(viewModel.profileAvatarIndex) }
    var photoToRepositionUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photoToRepositionUri = uri
        }
    }

    // SAF Picker specifically for this setup flow
    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.preventAutoLock = false
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.saveSelectedFolderUri(uri.toString())
                backupStatusMessage = "Folder penyimpanan SAF berhasil diaktifkan secara permanen!"
                isBackupStatusError = false
                viewModel.showToastPopup("Folder SAF Berhasil Dikonfigurasi!")
            } catch (e: Exception) {
                backupStatusMessage = "Gagal mengambil hak akses folder: ${e.message}"
                isBackupStatusError = true
            }
        }
    }

    // System Back Handler
    BackHandler {
        if (currentStep == 3 && pinEnabled && activePinField == 2) {
            activePinField = 1
        } else if (currentStep > 1) {
            currentStep--
        } else {
            val activity = context as? android.app.Activity
            activity?.finish()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Progress Bar & Stepper
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Langkah $currentStep dari $totalSteps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when (currentStep) {
                            1 -> "Selamat Datang"
                            2 -> "Profil Pengguna"
                            3 -> "Keamanan Aplikasi"
                            4 -> "Cadangan & SAF"
                            else -> "Siap Digunakan"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar Indicator
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Main Card Step Content
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    when (currentStep) {
                        1 -> WelcomeStepView()
                        2 -> ProfileStepView(
                            profileName = tempProfileName,
                            onNameChange = {
                                tempProfileName = it
                                viewModel.saveProfileName(it)
                            },
                            avatarIndex = selectedAvatarIndex,
                            onAvatarSelect = { idx ->
                                selectedAvatarIndex = idx
                                viewModel.saveProfileAvatarIndex(idx)
                            },
                            onCustomImagePick = {
                                viewModel.preventAutoLock = true
                                imagePickerLauncher.launch("image/*")
                            },
                            viewModel = viewModel
                        )
                        3 -> SecurityStepView(
                            pinEnabled = pinEnabled,
                            onPinEnabledChange = { enabled ->
                                pinEnabled = enabled
                                if (!enabled) {
                                    enteredPin = ""
                                    confirmPin = ""
                                    pinErrorMessage = ""
                                    activePinField = 1
                                    viewModel.saveAppPin("")
                                    viewModel.saveAppLockEnabled(false)
                                }
                            },
                            enteredPin = enteredPin,
                            onEnteredPinChange = { enteredPin = it },
                            confirmPin = confirmPin,
                            onConfirmPinChange = { confirmPin = it },
                            errorMessage = pinErrorMessage,
                            activeField = activePinField,
                            onActiveFieldChange = { activePinField = it },
                            onResetPin = {
                                enteredPin = ""
                                confirmPin = ""
                                pinErrorMessage = ""
                                activePinField = 1
                            }
                        )
                        4 -> BackupStepView(
                            viewModel = viewModel,
                            backupStatusMessage = backupStatusMessage,
                            isBackupStatusError = isBackupStatusError,
                            onSelectFolderClick = {
                                try {
                                    viewModel.preventAutoLock = true
                                    safFolderLauncher.launch(null)
                                } catch (e: Exception) {
                                    viewModel.preventAutoLock = false
                                    viewModel.showToastPopup("Gagal membuka picker folder SAF: ${e.message}")
                                }
                            },
                            onRestoreClick = {
                                viewModel.importBackupSAF(context) { msg, success ->
                                    backupStatusMessage = msg
                                    isBackupStatusError = !success
                                    if (success) {
                                        viewModel.showToastPopup("Data berhasil dipulihkan!")
                                        if (pinEnabled && enteredPin.length == 4) {
                                            viewModel.saveAppPin(enteredPin)
                                            viewModel.saveAppLockEnabled(true)
                                        }
                                    }
                                }
                            }
                        )
                        5 -> CompletionStepView(
                            viewModel = viewModel,
                            pinEnabled = pinEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Buttons Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = {
                            if (currentStep == 3 && pinEnabled && activePinField == 2) {
                                activePinField = 1
                            } else {
                                currentStep--
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("setup_prev_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kembali")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep == 3 && pinEnabled) {
                            if (activePinField == 1) {
                                if (enteredPin.length != 4) {
                                    pinErrorMessage = "PIN harus terdiri dari tepat 4 angka!"
                                } else {
                                    pinErrorMessage = ""
                                    activePinField = 2
                                }
                            } else {
                                if (confirmPin.length != 4) {
                                    pinErrorMessage = "PIN konfirmasi harus terdiri dari tepat 4 angka!"
                                } else if (enteredPin != confirmPin) {
                                    pinErrorMessage = "PIN konfirmasi tidak cocok!"
                                } else {
                                    pinErrorMessage = ""
                                    // Lock in PIN securely
                                    viewModel.saveAppPin(enteredPin)
                                    viewModel.saveAppLockEnabled(true)
                                    currentStep++
                                }
                            }
                        } else if (currentStep == 3 && !pinEnabled) {
                            viewModel.saveAppPin("")
                            viewModel.saveAppLockEnabled(false)
                            currentStep++
                        } else if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            // Completion Step: Finalize setup
                            if (pinEnabled && enteredPin.length == 4) {
                                viewModel.saveAppPin(enteredPin)
                                viewModel.saveAppLockEnabled(true)
                            } else if (!pinEnabled) {
                                viewModel.saveAppPin("")
                                viewModel.saveAppLockEnabled(false)
                            }
                            viewModel.completeFirstInstallSetup()
                            onComplete()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(1f, fill = false)
                        .padding(start = 12.dp)
                        .testTag("setup_next_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (currentStep == totalSteps) "Mulai Gunakan Aplikasi" else "Lanjut",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep == totalSteps) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        contentDescription = "Selanjutnya"
                    )
                }
            }
        }
    }

    if (photoToRepositionUri != null) {
        PhotoRepositionDialog(
            imageUri = photoToRepositionUri!!,
            onDismiss = { photoToRepositionUri = null },
            onConfirm = { croppedBitmap ->
                viewModel.saveProfileImageBitmap(croppedBitmap)
                selectedAvatarIndex = -1
                photoToRepositionUri = null
                viewModel.showToastPopup("Foto profil berhasil disesuaikan!")
            }
        )
    }
}

@Composable
fun WelcomeStepView() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .shadow(6.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val logoRes = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                com.example.R.drawable.img_app_icon_1785895772461
            } else {
                com.example.R.drawable.img_app_icon_light_1785896910266
            }
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = "Logo Aplikasi",
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Selamat Datang di My Singghara",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Fitur Unggulan Siap Digunakan:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureRow(
            icon = Icons.Default.AccountBalanceWallet,
            title = "Manajemen Keuangan & Kasir Usaha"
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureRow(
            icon = Icons.Default.EventNote,
            title = "Agenda, Kalender & Catatan"
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureRow(
            icon = Icons.Default.LibraryBooks,
            title = "Quran Digital & Pemutar Musik"
        )
    }
}

@Composable
fun ProfileStepView(
    profileName: String,
    onNameChange: (String) -> Unit,
    avatarIndex: Int,
    onAvatarSelect: (Int) -> Unit,
    onCustomImagePick: () -> Unit,
    viewModel: MainViewModel
) {
    val avatarGradients = remember {
        listOf(
            Brush.linearGradient(listOf(Color(0xFF2A84EB), Color(0xFF1456A5))),
            Brush.linearGradient(listOf(Color(0xFFFF5E62), Color(0xFFFF9966))),
            Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
            Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF))),
            Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057)))
        )
    }

    val avatarIcons = remember {
        listOf(
            Icons.Default.Person,
            Icons.Default.AccountBalance,
            Icons.Default.Star,
            Icons.Default.Favorite,
            Icons.Default.Palette
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Kustomisasi Profil Anda",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar Preview
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .then(
                    if (avatarIndex >= 0) {
                        Modifier.background(avatarGradients.getOrElse(avatarIndex) { avatarGradients[0] })
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = avatarIcons.getOrElse(avatarIndex.coerceAtLeast(0)) { Icons.Default.Person },
                contentDescription = "Avatar Profil",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name Input
        OutlinedTextField(
            value = profileName,
            onValueChange = onNameChange,
            label = { Text("Nama Pengguna") },
            placeholder = { Text("Contoh: Singghara User") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Pilih Style Avatar:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            avatarGradients.forEachIndexed { idx, brush ->
                val isSelected = avatarIndex == idx
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(brush)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { onAvatarSelect(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = avatarIcons.getOrElse(idx) { Icons.Default.Person },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityStepView(
    pinEnabled: Boolean,
    onPinEnabledChange: (Boolean) -> Unit,
    enteredPin: String,
    onEnteredPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    errorMessage: String,
    activeField: Int,
    onActiveFieldChange: (Int) -> Unit,
    onResetPin: () -> Unit
) {
    // Auto advance field to confirm PIN when 4 digits entered in field 1
    LaunchedEffect(enteredPin) {
        if (activeField == 1 && enteredPin.length == 4) {
            kotlinx.coroutines.delay(150)
            onActiveFieldChange(2)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Keamanan Aplikasi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Switch(
                checked = pinEnabled,
                onCheckedChange = onPinEnabledChange,
                modifier = Modifier.testTag("setup_pin_switch")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!pinEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Aplikasi dapat dibuka langsung tanpa meminta PIN. Anda dapat mengaktifkan kunci PIN kapan saja lewat menu Profil.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))

            // Logo / Lock Box like Lock Screen
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (activeField == 1) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = "Gembok PIN",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (activeField == 1) "Buat 4 Digit PIN" else "Konfirmasi PIN Anda",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (activeField == 1) "Langkah 1 dari 2: Buat PIN Baru" else "Langkah 2 dari 2: Masukkan Ulang PIN",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                if (enteredPin.isNotEmpty() || confirmPin.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ulangi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onResetPin() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // PIN Bullet Dot Indicator (Matching Lock Screen)
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentText = if (activeField == 1) enteredPin else confirmPin
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

            Spacer(modifier = Modifier.height(12.dp))

            // Error Message
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Numeric Keypad Grid 3x4 Matching Lock Screen
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val buttonRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in buttonRows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (num in row) {
                            SetupKeypadButton(
                                text = num,
                                modifier = Modifier.size(64.dp)
                            ) {
                                if (activeField == 1) {
                                    if (enteredPin.length < 4) {
                                        onEnteredPinChange(enteredPin + num)
                                    }
                                } else {
                                    if (confirmPin.length < 4) {
                                        onConfirmPinChange(confirmPin + num)
                                    }
                                }
                            }
                        }
                    }
                }

                // Row Terakhir: Reset/Refresh, 0, Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Reset / Refresh
                    Surface(
                        onClick = {
                            if (enteredPin.isNotEmpty() || confirmPin.isNotEmpty()) {
                                onResetPin()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Ulangi PIN",
                                tint = if (enteredPin.isNotEmpty() || confirmPin.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Angka 0
                    SetupKeypadButton(
                        text = "0",
                        modifier = Modifier.size(64.dp)
                    ) {
                        if (activeField == 1) {
                            if (enteredPin.length < 4) {
                                onEnteredPinChange(enteredPin + "0")
                            }
                        } else {
                            if (confirmPin.length < 4) {
                                onConfirmPinChange(confirmPin + "0")
                            }
                        }
                    }

                    // Tombol Backspace
                    Surface(
                        onClick = {
                            if (activeField == 1) {
                                if (enteredPin.isNotEmpty()) {
                                    onEnteredPinChange(enteredPin.dropLast(1))
                                }
                            } else {
                                if (confirmPin.isNotEmpty()) {
                                    onConfirmPinChange(confirmPin.dropLast(1))
                                } else {
                                    onActiveFieldChange(1)
                                    if (enteredPin.isNotEmpty()) {
                                        onEnteredPinChange(enteredPin.dropLast(1))
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Hapus",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
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
fun BackupStepView(
    viewModel: MainViewModel,
    backupStatusMessage: String,
    isBackupStatusError: Boolean,
    onSelectFolderClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val hasFolderSelected = viewModel.safFolderUriStr.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Backup,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Cadangan & Pemulihan (SAF)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        val context = androidx.compose.ui.platform.LocalContext.current
        // Otomatis buat & verifikasi folder My Singghara di penyimpanan internal
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.example.util.SinggharaFolderManager.ensureFoldersExist(context)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Struktur Folder Otomatis (Internal)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Posisi: /storage/emulated/0/My Singghara (sejajar folder Android)\n" +
                           "• Back Up (file cadangan database)\n" +
                           "• Data/mp3 (suara adzan kustom & musik)\n" +
                           "• Data/font (font jam kustom .ttf: Technology fonts)",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "✓ Otomatis dibuat & dapat diakses langsung via File Manager",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Direktori Penyimpanan Utama",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasFolderSelected) "Folder aktif: ${Uri.parse(viewModel.safFolderUriStr).path ?: viewModel.safFolderUriStr}"
                           else "Folder backup SAF belum dikonfigurasi.",
                    fontSize = 12.sp,
                    color = if (hasFolderSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onSelectFolderClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_select_saf_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasFolderSelected) "Ubah Folder SAF" else "Pilih Folder Penyimpanan SAF", fontSize = 12.sp)
                }
            }
        }

        if (hasFolderSelected) {
            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Migrasi / Pulihkan Data Lama",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onRestoreClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_restore_saf_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Impor / Pulihkan Sekarang", fontSize = 12.sp)
                    }
                }
            }
        }

        if (backupStatusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isBackupStatusError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                     else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBackupStatusError) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isBackupStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = backupStatusMessage,
                        fontSize = 12.sp,
                        color = if (isBackupStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CompletionStepView(
    viewModel: MainViewModel,
    pinEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Pengaturan Selesai!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Ringkasan Setup Anda:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Profil: ${viewModel.profileName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (pinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (pinEnabled) "Kunci PIN: AKTIF (4 Digit)" else "Kunci PIN: NONAKTIF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (viewModel.safFolderUriStr.isNotEmpty()) "Folder SAF: Dikonfigurasi" else "Folder SAF: Belum Dikonfigurasi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (desc.isNotEmpty()) {
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
