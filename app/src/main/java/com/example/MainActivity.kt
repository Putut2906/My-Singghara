package com.example

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import java.util.Calendar
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Palette as OutlinedPalette
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MainViewModel
import com.example.ui.components.BottomNavBar
import com.example.NotificationScheduler
import com.example.network.HolidayClient
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.update.*
import com.example.ui.components.AppUpdateAvailableDialog
import com.example.ui.components.DriveUpdateAvailableDialog
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
    }

    private val viewModel: MainViewModel by viewModels()

    // Activity launcher untuk meminta storage permissions secara eksplisit pada rilis pertama
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false

        if (readGranted || writeGranted) {
            com.example.util.SinggharaFolderManager.ensureFoldersExist(this)
            viewModel.showToastPopup("Akses file diizinkan.")
        } else {
            viewModel.showToastPopup("Izin penyimpanan ditolak.")
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                if (viewModel.isAppLockEnabled && !viewModel.preventAutoLock) {
                    viewModel.isAppUnlocked = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenOffReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Memicu check & request permissions secara langsung saat pertamakali dibuka
        checkAndRequestStoragePermissions()

        // Otomatis buat folder My Singghara (Back Up, Data/font, Data/mp3) di penyimpanan internal
        com.example.util.SinggharaFolderManager.ensureFoldersExist(this)

        // Request POST_NOTIFICATIONS permission for Android 13+ to ensure user gets sholat and agenda reminders
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Initialize notification channels and schedule daily / sholat alarms
        NotificationScheduler.scheduleAlarms(this)

        // Pastikan FirebaseApp terinisialisasi dengan benar
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                try {
                    com.google.firebase.FirebaseApp.initializeApp(this)
                } catch (e: Exception) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:194078759561:android:b39d13cb8202ff43cfc458")
                        .setApiKey("AIzaSyC-1wqeoghlBosva2liyNCEEbb4IK6yvYs")
                        .setProjectId("mobile-d08aa")
                        .setStorageBucket("mobile-d08aa.firebasestorage.app")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing FirebaseApp", e)
        }

        // Tangani hasil tertunda (pending auth) dari OAuth Google / Facebook jika Activity sempat di-recreate
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance().pendingAuthResult
                    ?.addOnSuccessListener { authResult ->
                        val user = authResult.user
                        if (user != null) {
                            viewModel.refreshAuthUser()
                            val isNew = authResult.additionalUserInfo?.isNewUser == true
                            viewModel.showAuthSuccessPopup(
                                isRegister = isNew,
                                userName = viewModel.authManager.extractDisplayName(user),
                                userEmail = user.email ?: "",
                                photoUrl = viewModel.authManager.extractPhotoUrl(user)
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking pendingAuthResult", e)
        }

        try {
            com.example.telegram.TelegramBotManager.init(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error initializing TelegramBotManager", e)
        }

        intent?.let { handleIntent(it) }

        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val isAccountBlocked = viewModel.isAccountBlocked &&
                            viewModel.currentFirebaseUser != null &&
                            !viewModel.adminManager.isAdmin(viewModel.currentFirebaseUser?.uid)

                    if (isAccountBlocked) {
                        com.example.ui.components.AccountBlockedOverlay(
                            viewModel = viewModel,
                            onContactAdmin = {
                                val user = viewModel.currentFirebaseUser
                                viewModel.authManager.launchWhatsAppAdmin(
                                    context = this@MainActivity,
                                    userEmail = user?.email ?: "",
                                    userName = viewModel.profileName.ifEmpty { user?.displayName ?: "Pengguna" },
                                    uid = user?.uid ?: "",
                                    reason = viewModel.accountBlockReason.ifEmpty { "Akun dinonaktifkan atau masa membership telah habis" }
                                )
                            },
                            onCheckStatus = {
                                viewModel.checkAccountAccessStatus { isAllowed ->
                                    if (isAllowed) {
                                        viewModel.showToastPopup("Status akun telah aktif! Selamat datang kembali.")
                                    } else {
                                        viewModel.showToastPopup("Akun masih nonaktif / membership belum diperpanjang.")
                                    }
                                }
                            },
                            onLogout = {
                                viewModel.requestLogoutConfirmation()
                            }
                        )
                    } else if (!viewModel.isFirstInstallSetupCompleted) {
                        FirstInstallSetupScreen(
                            viewModel = viewModel,
                            onComplete = {
                                viewModel.isAppUnlocked = true
                            }
                        )
                    } else {
                        val isLocked = viewModel.isAppLockEnabled && !viewModel.isAppUnlocked
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isLocked) Modifier.blur(24.dp) else Modifier
                                )
                        ) {
                            MainAppContent(viewModel)
                        }

                        if (isLocked) {
                            LockScreenLayout(
                                viewModel = viewModel,
                                onAuthenticate = { onBiometricSuccess ->
                                    showBiometricPrompt(
                                        onSuccess = {
                                            onBiometricSuccess()
                                        },
                                        onError = { errCode, errStr ->
                                            when (errCode) {
                                                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                                    viewModel.showToastPopup("Masukkan PIN Anda.")
                                                }
                                                BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                                                    viewModel.showToastPopup("Biometrik terkunci. Gunakan PIN Anda.")
                                                }
                                                BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> {
                                                    viewModel.showToastPopup("Biometrik dibatalkan. Masukkan PIN.")
                                                }
                                                else -> {
                                                    viewModel.showToastPopup("Masukkan PIN Anda untuk masuk.")
                                                }
                                            }
                                        }
                                    )
                                },
                                onExit = {
                                    finishAffinity()
                                }
                            )
                        }
                    }

                    if (viewModel.isAppUnlocked && viewModel.showConfirmDeleteNotificationDialog) {
                        AlertDialog(
                            onDismissRequest = { viewModel.showConfirmDeleteNotificationDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Hapus Notifikasi?", fontWeight = FontWeight.Bold)
                                }
                            },
                            text = {
                                Text("Apakah Anda yakin ingin menghapus notifikasi agenda hari ini dari status bar?")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        NotificationScheduler.cancelAgendaNotification(this@MainActivity)
                                        viewModel.setAgendaNotificationDismissedToday(true)
                                        viewModel.showConfirmDeleteNotificationDialog = false
                                        viewModel.showToastPopup("Notifikasi agenda berhasil dihapus")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.onError)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { viewModel.showConfirmDeleteNotificationDialog = false }) {
                                    Text("Batal")
                                }
                            }
                        )
                    }

                    if (viewModel.toastPopupMessage != null) {
                        com.example.ui.components.SpringToastPopup(
                            message = viewModel.toastPopupMessage,
                            onDismiss = { viewModel.toastPopupMessage = null },
                            type = viewModel.toastPopupType
                        )
                    }

                    // Web PC Connect Security & Authentication Dialog
                    com.example.ui.components.WebSecurityDialog(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val navigateTo = intent.getStringExtra("NAVIGATE_TO")
        if (navigateTo != null) {
            viewModel.currentRoute = navigateTo
        }
        if (intent.getBooleanExtra("CONFIRM_DELETE_NOTIFICATION", false)) {
            viewModel.showConfirmDeleteNotificationDialog = true
        }
        if (intent.getBooleanExtra("OPEN_WEB_AUTH_POPUP", false)) {
            viewModel.openWebAuthPromptFromNotification()
        }
        if (intent.action == "ACTION_REJECT_WEB_ACCESS") {
            viewModel.onRejectWebAccess()
        }
        if (intent.getBooleanExtra("open_fcm_notifications", false) || intent.hasExtra("google.message_id")) {
            val fcmTitle = intent.getStringExtra("fcm_title") ?: intent.getStringExtra("gcm.notification.title")
            val fcmBody = intent.getStringExtra("fcm_body") ?: intent.getStringExtra("gcm.notification.body")
            if (!fcmTitle.isNullOrEmpty() || !fcmBody.isNullOrEmpty()) {
                val notif = com.example.data.FcmNotification(
                    title = fcmTitle ?: "Pesan Cloud Messaging",
                    body = fcmBody ?: "",
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val id = viewModel.repository.insertFcmNotification(notif)
                    val itemWithId = notif.copy(id = id)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        viewModel.showInAppMessage(itemWithId)
                    }
                }
            }
        }
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uri: Uri? = when {
                intent.action == Intent.ACTION_SEND_MULTIPLE -> {
                    val list: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    list?.firstOrNull() ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: intent.data
                }
            }

            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

            if (uri != null || !sharedText.isNullOrBlank()) {
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }
                viewModel.sharedReceiptUri = uri
                viewModel.sharedReceiptText = sharedText
                viewModel.showSharedReceiptDialog = true
                viewModel.currentRoute = "tabungan"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            com.example.ht.HandyTalkyManager.destroy()
        } catch (_: Exception) {}
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        viewModel.lastBackgroundTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        com.example.util.SinggharaFolderManager.ensureFoldersExist(this)
        viewModel.checkAccountAccessStatus()
        // Kunci aplikasi jika sistem OS / keyguard dalam keadaan terkunci
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager != null && (keyguardManager.isKeyguardLocked || keyguardManager.isDeviceLocked)) {
            if (viewModel.isAppLockEnabled) {
                viewModel.isAppUnlocked = false
            }
        }
        window.decorView.postDelayed({
            viewModel.preventAutoLock = false
        }, 1500)
    }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (errCode: Int, errString: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                viewModel.showToastPopup("Sidik jari/wajah tidak dikenali. Silakan coba lagi.")
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kunci Aplikasi")
            .setSubtitle("Gunakan Sidik Jari atau Wajah Anda untuk masuk")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Gunakan PIN")
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(-99, "Gagal meluncurkan biometric: ${e.message}")
        }
    }

    fun canUseBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        val status = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) menggunakan MANAGE_EXTERNAL_STORAGE untuk akses file langsung di folder publik
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                    viewModel.showToastPopup("Harap izinkan akses semua file agar fitur Ekspor/Impor berfungsi.")
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        viewModel.showToastPopup("Gagal membuka pengaturan izin file.")
                    }
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) mendukung scoped storage, namun kita tetap meminta izin baca/tulis
            val hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val permissionsList = mutableListOf<String>()
            if (!hasRead) {
                permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (!hasWrite) {
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissionsList.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsList.toTypedArray())
            }
        } else {
            // Android 9 ke bawah mewajibkan write/read eksplisit
            val permissionsList = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsList.isNotEmpty()) {
                requestPermissionLauncher.launch(permissionsList.toTypedArray())
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var showInitialSafDialog by remember { mutableStateOf(false) }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val activity = androidx.activity.compose.LocalActivity.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Firebase Auth State
    var showAuthDialog by remember { mutableStateOf(false) }
    var authDialogInitialMode by remember { mutableStateOf(com.example.ui.components.AuthMode.LOGIN) }
    
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var tempProfileName by remember { mutableStateOf(viewModel.profileName) }
    var selectedAvatarIndex by remember { mutableStateOf(viewModel.profileAvatarIndex) }
    var photoToRepositionUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photoToRepositionUri = uri
        }
    }

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

    var showSearchDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var isCompanyExpanded by remember { mutableStateOf(false) }
    var isManajemenExpanded by remember { mutableStateOf(false) }
    var isKeuanganExpanded by remember { mutableStateOf(false) }
    var isKasirExpanded by remember { mutableStateOf(false) }
    var isKoneksiBerbagiExpanded by remember { mutableStateOf(false) }

    // Otomatis tutup semua sub menu saat sidebar/drawer di-collapse (tertutup)
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == androidx.compose.material3.DrawerValue.Closed) {
            isCompanyExpanded = false
            isManajemenExpanded = false
            isKeuanganExpanded = false
            isKasirExpanded = false
            isKoneksiBerbagiExpanded = false
        }
    }
    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed) {
            isCompanyExpanded = false
            isManajemenExpanded = false
            isKeuanganExpanded = false
            isKasirExpanded = false
            isKoneksiBerbagiExpanded = false
        }
    }
    
    val notesList by viewModel.notes.collectAsState()
    val productsList by viewModel.shoppingItems.collectAsState()
    val agendaList by viewModel.agendaItems.collectAsState()
    
    val journalEntriesStr = viewModel.journalEntriesStr
    val journalEntries = remember(journalEntriesStr) {
        deserializeJournalEntries(journalEntriesStr)
    }

    val adjustmentEntriesStr = viewModel.adjustmentEntriesStr
    val adjustmentEntries = remember(adjustmentEntriesStr) {
        deserializeAdjustmentEntries(adjustmentEntriesStr)
    }

    var startupUpdateResult by remember { mutableStateOf<UpdateCheckState.UpdateAvailable?>(null) }
    var startupDriveUpdateResult by remember { mutableStateOf<DriveUpdateCheckState.UpdateAvailable?>(null) }

    LaunchedEffect(Unit) {
        try {
            // Cegah munculnya pop up pembaruan otomatis saat pertama kali instal membuka aplikasi
            if (GoogleDriveUpdateManager.isFirstLaunchAfterInstall(context)) {
                GoogleDriveUpdateManager.markFirstLaunchDone(context)
                return@LaunchedEffect
            }

            // Check Google Drive update first (based on latest upload time via Apps Script)
            if (GoogleDriveUpdateManager.isAutoCheckOnStartup(context)) {
                val driveResult = GoogleDriveUpdateManager.checkForDriveUpdate(context)
                if (driveResult is DriveUpdateCheckState.UpdateAvailable) {
                    startupDriveUpdateResult = driveResult
                }
            }
            // Fallback to GitHub update check if no Drive update detected
            if (startupDriveUpdateResult == null) {
                val cfg = GitHubUpdateManager.loadConfig(context)
                if (cfg.autoCheckOnStartup) {
                    val result = GitHubUpdateManager.checkForUpdate(context)
                    if (result is UpdateCheckState.UpdateAvailable) {
                        startupUpdateResult = result
                    }
                }
            }
        } catch (_: Exception) {}
    }

    startupDriveUpdateResult?.let { updateData ->
        if (viewModel.currentRoute != "tentang_aplikasi") {
            DriveUpdateAvailableDialog(
                latestApk = updateData.latestApk,
                currentVersion = updateData.currentAppVersion,
                autoStartDownload = GoogleDriveUpdateManager.isAutoInstallLatest(context),
                onDismiss = { startupDriveUpdateResult = null }
            )
        }
    }

    startupUpdateResult?.let { updateData ->
        val cfg = remember(context) { GitHubUpdateManager.loadConfig(context) }
        AppUpdateAvailableDialog(
            currentVersion = updateData.currentVersion,
            newVersion = updateData.newVersion,
            release = updateData.release,
            apkAsset = updateData.apkAsset,
            token = cfg.token,
            onDismiss = { startupUpdateResult = null }
        )
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (viewModel.currentRoute == "preview_catatan") {
            viewModel.currentRoute = "catatan"
        } else if (viewModel.currentRoute == "catatan" && viewModel.selectedNote != null) {
            // Handled inside CatatanScreen to allow showing the unsaved changes warning popup
        } else if (viewModel.currentRoute == "web_connect" || viewModel.currentRoute == "tentang_aplikasi" || viewModel.currentRoute == "telegram_ai") {
            viewModel.currentRoute = "profil"
        } else if (viewModel.currentRoute != "home") {
            val showAllApps = viewModel.currentRoute in listOf("quran", "pembukuan_usaha", "mindmap", "pemutar_musik", "jadwal_harian", "handy_talky")
            viewModel.currentRoute = "home"
            if (showAllApps) {
                viewModel.showAllAppsPage = true
            } else {
                viewModel.showAllAppsPage = false
            }
        } else if (viewModel.showAllAppsPage) {
            viewModel.showAllAppsPage = false
        } else {
            showExitConfirmDialog = true
        }
    }

    // Registrasi Launcher SAF Open Document Tree untuk inisialisasi awal
    val initialFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.saveSelectedFolderUri(uri.toString())
                showInitialSafDialog = false
                viewModel.showToastPopup("Folder Penyimpanan Utama SAF berhasil dikonfigurasi secara permanen!")
            } catch (e: Exception) {
                viewModel.showToastPopup("Gagal mengeset izin folder: ${e.message}")
            }
        }
    }

    if (showInitialSafDialog) {
        AlertDialog(
            onDismissRequest = { showInitialSafDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aktivasi Folder SAF Lokal", fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    text = "Aplikasi membutuhkan folder eksternal kustom (Storage Access Framework) pilihan Anda sendiri " +
                           "(misal folder 'Pictures', 'Documents', atau folder buatan baru) untuk menyimpan file cadangan 'data_aplikasi.json' serta dokumen kustom Anda " +
                           "secara permanen tanpa bisa terhapus saat pembersihan cache.",
                    lineHeight = 20.sp,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            viewModel.preventAutoLock = true
                            initialFolderPicker.launch(null)
                        } catch (e: Exception) {
                            viewModel.preventAutoLock = false
                            viewModel.showToastPopup("Gagal membuka file manager: ${e.message}")
                        }
                    }
                ) {
                    Text("Pilih/Buat Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInitialSafDialog = false }) {
                    Text("Nanti Saja")
                }
            }
        )
    }

    // Daftar navigasi menu dalam Drawer (dibuat kosong sesuai permintaan user)
    val drawerItems = emptyList<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>()

    // Logika dan Tampilan untuk Dialog Pencarian Menyeluruh (Global Search)
    if (showSearchDialog) {
        var searchQuery by remember { mutableStateOf("") }

        val filteredNotes = remember(searchQuery, notesList) {
            if (searchQuery.isBlank()) emptyList()
            else notesList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }

        val filteredShopping = remember(searchQuery, productsList) {
            if (searchQuery.isBlank()) emptyList()
            else productsList.filter {
                it.nama.contains(searchQuery, ignoreCase = true) ||
                it.jenis.contains(searchQuery, ignoreCase = true)
            }
        }

        val filteredAgendas = remember(searchQuery, agendaList) {
            if (searchQuery.isBlank()) emptyList()
            else agendaList.filter {
                it.judul.contains(searchQuery, ignoreCase = true) ||
                it.detail.contains(searchQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pencarian Menyeluruh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showSearchDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Ketik kata kunci...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (searchQuery.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Silakan ketik untuk mencari catatan, belanjaan, atau agenda harian Anda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (filteredNotes.isEmpty() && filteredShopping.isEmpty() && filteredAgendas.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Tidak ada hasil ditemukan untuk \"" + searchQuery + "\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredNotes.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "CATATAN HARIAN (" + filteredNotes.size + ")",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredNotes) { note ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.currentRoute = "catatan"
                                                viewModel.selectedNote = note
                                                showSearchDialog = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                note.content,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredShopping.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "BELANJA MANDIRI (" + filteredShopping.size + ")",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredShopping) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.currentRoute = "belanja"
                                                showSearchDialog = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(item.nama, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Kategori: " + item.jenis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                text = "Rp %,.0f".format(item.total),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            if (filteredAgendas.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "AGENDA & JADWAL (" + filteredAgendas.size + ")",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                items(filteredAgendas) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.currentRoute = "home"
                                                showSearchDialog = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(item.judul, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(item.tanggal + " - " + item.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showNotificationsDialog) {
        val kasirPrefs = remember(context) { context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE) }
        val serializedStock = kasirPrefs.getString("list_stock", "") ?: ""
        val listStock = remember(serializedStock) { deserializeStock(serializedStock) }
        val lowStockItems = listStock.filter { it.stok <= 3 }

        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notifikasi Sistem", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = { showNotificationsDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    val notifications = remember(notesList, productsList, agendaList, lowStockItems) {
                        val list = mutableListOf<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>()
                        
                        // 1. Low Stock
                        if (lowStockItems.isNotEmpty()) {
                            list.add(
                                Triple(
                                    "Peringatan Stok Tipis!",
                                    "Ada ${lowStockItems.size} barang dengan stok hampir habis (<= 3 unit). Segera isi ulang di menu My Kasir.",
                                    Icons.Default.Info
                                )
                            )
                        }

                        // 2. Notes Summary
                        if (notesList.isNotEmpty()) {
                            list.add(
                                Triple(
                                    "Catatan Tersimpan",
                                    "Anda memiliki ${notesList.size} catatan penting yang tersimpan aman secara lokal.",
                                    Icons.Default.Edit
                                )
                            )
                        }

                        // 3. Agendas / Tasks
                        if (agendaList.isNotEmpty()) {
                            list.add(
                                Triple(
                                    "Agenda Kegiatan",
                                    "Ada ${agendaList.size} agenda kegiatan atau pengingat penting yang aktif saat ini.",
                                    Icons.Default.List
                                )
                            )
                        }

                        // 4. Shopping list count
                        if (productsList.isNotEmpty()) {
                            list.add(
                                Triple(
                                    "Rencana Belanja Mandiri",
                                    "Terdapat ${productsList.size} item belanja barang usaha yang siap untuk dibeli.",
                                    Icons.Default.ShoppingCart
                                )
                            )
                        }

                        // Fallback
                        if (list.isEmpty()) {
                            list.add(
                                Triple(
                                    "Sistem Berjalan Lancar",
                                    "Semua operasional toko, pencatatan keuangan, dan agenda usaha terpantau aman.",
                                    Icons.Default.Info
                                )
                            )
                        }

                        list
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(notifications) { item ->
                            val isWarning = item.first.contains("Stok Tipis") || item.first.contains("Peringatan")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isWarning) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = item.third,
                                        contentDescription = null,
                                        tint = if (isWarning) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.first,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isWarning) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.second,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = {
                Text(
                    text = "Konfirmasi Keluar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin keluar dari aplikasi?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        activity?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Ya", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tidak", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showProfileEditDialog) {
        AlertDialog(
            onDismissRequest = { showProfileEditDialog = false },
            title = {
                Text(
                    "Kustomisasi Profil",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Current Avatar Preview
                    com.example.ui.components.AppUserAvatar(
                        name = tempProfileName.ifEmpty { viewModel.profileName },
                        imageUri = if (selectedAvatarIndex == -1) viewModel.profileImageUri else "",
                        avatarIndex = selectedAvatarIndex,
                        size = 80.dp,
                        textSize = 28.sp,
                        isLoggedIn = viewModel.currentFirebaseUser != null,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    // Input Name
                    OutlinedTextField(
                        value = tempProfileName,
                        onValueChange = { tempProfileName = it },
                        label = { Text("Nama Anda") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        "Pilih Avatar:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Avatar Gradients Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        avatarGradients.forEachIndexed { idx, brush ->
                            val isSelected = selectedAvatarIndex == idx
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(brush)
                                    .clickable {
                                        selectedAvatarIndex = idx
                                    }
                                    .then(
                                        if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(2.dp) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val icon = avatarIcons[idx]
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Gallery Option
                    Button(
                        onClick = {
                            try {
                                viewModel.preventAutoLock = true
                                imagePickerLauncher.launch("image/*")
                                selectedAvatarIndex = -1
                            } catch (e: Exception) {
                                viewModel.preventAutoLock = false
                                viewModel.showToastPopup("Gagal membuka galeri: ${e.message}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Foto dari Galeri")
                    }

                    OutlinedButton(
                        onClick = {
                            selectedAvatarIndex = -2
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gunakan Avatar Inisial Nama")
                    }

                    if (selectedAvatarIndex == -1 && viewModel.profileImageUri.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    photoToRepositionUri = android.net.Uri.parse(viewModel.profileImageUri)
                                } catch (e: Exception) {
                                    viewModel.showToastPopup("Gagal membuka foto saat ini: ${e.message}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Atur Ulang Posisi Foto")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempProfileName.isNotBlank()) {
                            viewModel.saveProfileName(tempProfileName.trim())
                            viewModel.saveProfileAvatarIndex(selectedAvatarIndex)
                            showProfileEditDialog = false
                            viewModel.showToastPopup("Profil berhasil disimpan!")
                        } else {
                            viewModel.showToastPopup("Nama tidak boleh kosong!")
                        }
                    }
                ) {
                    Text("Simpan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (photoToRepositionUri != null) {
        com.example.ui.components.PhotoRepositionDialog(
            imageUri = photoToRepositionUri!!,
            onDismiss = { photoToRepositionUri = null },
            onConfirm = { croppedBitmap ->
                viewModel.saveProfileImageBitmap(croppedBitmap)
                selectedAvatarIndex = -1
                photoToRepositionUri = null
                viewModel.showToastPopup("Posisi foto berhasil disimpan!")
            }
        )
    }

    // Popup Detail Membership VIP (Muncul saat klik Profil User)
    if (viewModel.showMembershipDetailDialog) {
        val currentFbUser = viewModel.currentFirebaseUser
        val isUserAdmin = currentFbUser?.let { viewModel.adminManager.isAdmin(it.uid) } ?: false
        val currentCloudUser = remember(currentFbUser, viewModel.accountMembershipTier, viewModel.accountMembershipEndDate) {
            currentFbUser?.let { fb ->
                com.example.firebase.CloudUserItem(
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

        com.example.ui.components.VipMembershipDetailDialog(
            user = viewModel.selectedMembershipUser ?: currentCloudUser,
            currentUserName = viewModel.profileName,
            currentUserEmail = currentFbUser?.email ?: "",
            adminPhoneNumber = viewModel.adminPhoneNumber,
            isViewerAdmin = isUserAdmin,
            onDismiss = { viewModel.closeMembershipDetail() }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || (viewModel.currentRoute != "handy_talky" && viewModel.currentRoute != "immersive_file_viewer"),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = if (viewModel.currentFirebaseUser != null) 68.dp else 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Profil Header (Salinan dari halaman profil: Foto Avatar & Nama Pengguna untuk akses cepat)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    viewModel.currentRoute = "profil"
                                    scope.launch { drawerState.close() }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar Box
                            com.example.ui.components.AppUserAvatar(
                                name = viewModel.profileName,
                                imageUri = viewModel.profileImageUri,
                                avatarIndex = viewModel.profileAvatarIndex,
                                size = 76.dp,
                                textSize = 26.sp,
                                isLoggedIn = viewModel.currentFirebaseUser != null,
                                contentDescription = "Foto Profil",
                                onClick = {
                                    viewModel.openMembershipDetail()
                                    scope.launch { drawerState.close() }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Nama Pengguna
                            Text(
                                text = viewModel.profileName.ifEmpty { "Pengguna Setia" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    viewModel.openMembershipDetail()
                                    scope.launch { drawerState.close() }
                                }
                            )

                            // Status login pengguna (jika ada akun firebase)
                            val headerUser = viewModel.currentFirebaseUser
                            if (headerUser != null) {
                                Text(
                                    text = headerUser.email ?: headerUser.displayName ?: "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Group 1: BERANDA (Boxed Group Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        // Item 1.1: Beranda
                        val isHomeSelected = viewModel.currentRoute == "home"
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (isHomeSelected) Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = "Beranda"
                                )
                            },
                            label = { Text(text = "Beranda", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            selected = isHomeSelected,
                            onClick = {
                                viewModel.currentRoute = "home"
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        )

                        // Item 1.2: Arisan Keluarga (Menu Utama Drawer)
                        val isArisanKeluargaMainSelected = viewModel.currentRoute == "arisan_keluarga"
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (isArisanKeluargaMainSelected) Icons.Default.Groups else Icons.Outlined.Groups,
                                    contentDescription = "Arisan Keluarga",
                                    tint = if (isArisanKeluargaMainSelected) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B)
                                )
                            },
                            label = { Text(text = "Arisan Keluarga", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            badge = {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Baru",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            },
                            selected = isArisanKeluargaMainSelected,
                            onClick = {
                                viewModel.currentRoute = "arisan_keluarga"
                                scope.launch { drawerState.close() }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    }

                    // Group: COMPANY (Boxed Group Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        // Header Clickable to Expand/Collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !isCompanyExpanded
                                    if (next) {
                                        isManajemenExpanded = false
                                        isKeuanganExpanded = false
                                        isKoneksiBerbagiExpanded = false
                                    }
                                    isCompanyExpanded = next
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = "Company",
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "COMPANY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = if (isCompanyExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isCompanyExpanded) "Sembunyikan" else "Tampilkan",
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isCompanyExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val isDataKaryawanSelected = viewModel.currentRoute == "data_karyawan_murid" || viewModel.currentRoute == "tabel_anggota_lengkap"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Badge,
                                            contentDescription = "Data"
                                        )
                                    },
                                    label = {
                                        Text(text = "Data", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    },
                                    selected = isDataKaryawanSelected,
                                    onClick = {
                                        viewModel.currentRoute = "data_karyawan_murid"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                val isAbsensiSelected = viewModel.currentRoute == "absensi_karyawan" || viewModel.currentRoute == "riwayat_absensi_lengkap"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "Absensi"
                                        )
                                    },
                                    label = {
                                        Text(text = "Absensi", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    },
                                    selected = isAbsensiSelected,
                                    onClick = {
                                        viewModel.currentRoute = "absensi_karyawan"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                val isGajiSelected = viewModel.currentRoute == "gaji_karyawan"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = "Gaji"
                                        )
                                    },
                                    label = {
                                        Text(text = "Gaji", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    },
                                    selected = isGajiSelected,
                                    onClick = {
                                        viewModel.currentRoute = "gaji_karyawan"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }

                    // Group 2: MANAJEMEN MANDIRI (Boxed Group Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        // Header Clickable to Expand/Collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !isManajemenExpanded
                                    if (next) {
                                        isCompanyExpanded = false
                                        isKeuanganExpanded = false
                                        isKoneksiBerbagiExpanded = false
                                    }
                                    isManajemenExpanded = next
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isManajemenExpanded) Icons.Default.Folder else Icons.Outlined.Folder,
                                    contentDescription = "Manajemen",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "MANAJEMEN MANDIRI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = if (isManajemenExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isManajemenExpanded) "Sembunyikan" else "Tampilkan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isManajemenExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Sub-item: Tabungan
                                val isTabunganSelected = viewModel.currentRoute == "tabungan"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (isTabunganSelected) Icons.Default.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                            contentDescription = "Tabungan"
                                        )
                                    },
                                    label = { Text(text = "Tabungan Mandiri", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    selected = isTabunganSelected,
                                    onClick = {
                                        viewModel.currentRoute = "tabungan"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                // Sub-item: Belanja
                                val isBelanjaSelected = viewModel.currentRoute == "belanja"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (isBelanjaSelected) Icons.Default.ShoppingCart else Icons.Outlined.ShoppingCart,
                                            contentDescription = "Belanja"
                                        )
                                    },
                                    label = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = "Daftar Belanja", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            if (productsList.isNotEmpty()) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "${productsList.size}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    selected = isBelanjaSelected,
                                    onClick = {
                                        viewModel.currentRoute = "belanja"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                // Sub-item: Catatan
                                val isCatatanSelected = viewModel.currentRoute == "catatan"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (isCatatanSelected) Icons.Default.Edit else Icons.Outlined.Edit,
                                            contentDescription = "Catatan"
                                        )
                                    },
                                    label = { Text(text = "Catatan Penting", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    selected = isCatatanSelected,
                                    onClick = {
                                        viewModel.currentRoute = "catatan"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                // Sub-item 4: Rekap Belanja Pasar
                                val isRekapBelanjaSelected = viewModel.currentRoute == "rekap_belanja_pasar"
                                val belanjaPasarNavList by viewModel.allBelanjaPasar.collectAsState()
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (isRekapBelanjaSelected) Icons.Default.ShoppingCart else Icons.Outlined.ShoppingCart,
                                            contentDescription = "Rekap Belanja Pasar",
                                            tint = Color(0xFF10B981)
                                        )
                                    },
                                    label = { Text(text = "Rekap Belanja Pasar", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    selected = isRekapBelanjaSelected,
                                    onClick = {
                                        viewModel.currentRoute = "rekap_belanja_pasar"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                // Sub-item 5: Arisan Keluarga (Manajemen, Aturan & Pengocokan)
                                val isArisanKeluargaSelected = viewModel.currentRoute == "arisan_keluarga"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = if (isArisanKeluargaSelected) Icons.Default.Groups else Icons.Outlined.Groups,
                                            contentDescription = "Arisan Keluarga",
                                            tint = Color(0xFFF59E0B)
                                        )
                                    },
                                    label = { Text(text = "Arisan Keluarga", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    badge = {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Baru",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD97706),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    },
                                    selected = isArisanKeluargaSelected,
                                    onClick = {
                                        viewModel.currentRoute = "arisan_keluarga"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }

                    // Group 3: AKUNTANSI & KEUANGAN (Boxed Group Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        // Header Clickable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !isKeuanganExpanded
                                    if (next) {
                                        isCompanyExpanded = false
                                        isManajemenExpanded = false
                                        isKoneksiBerbagiExpanded = false
                                    }
                                    isKeuanganExpanded = next
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isKeuanganExpanded) Icons.Default.AccountBalance else Icons.Outlined.AccountBalance,
                                    contentDescription = "Keuangan",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "LAPORAN KEUANGAN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = if (isKeuanganExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isKeuanganExpanded) "Sembunyikan" else "Tampilkan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isKeuanganExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val reports = listOf(
                                    Triple("jurnal_umum", "Jurnal Umum", if (viewModel.currentRoute == "jurnal_umum") Icons.Default.List else Icons.Outlined.List),
                                    Triple("buku_besar", "Buku Besar", if (viewModel.currentRoute == "buku_besar") Icons.Default.List else Icons.Outlined.List),
                                    Triple("neraca_saldo", "Neraca Saldo", if (viewModel.currentRoute == "neraca_saldo") Icons.Default.AccountBalance else Icons.Outlined.AccountBalance),
                                    Triple("jurnal_penyesuaian", "Jurnal Penyesuaian", if (viewModel.currentRoute == "jurnal_penyesuaian") Icons.Default.Edit else Icons.Outlined.Edit),
                                    Triple("neraca_saldo_disesuaikan", "Neraca Saldo Disesuaikan", if (viewModel.currentRoute == "neraca_saldo_disesuaikan") Icons.Default.List else Icons.Outlined.List),
                                    Triple("laba_rugi", "Laba Rugi", if (viewModel.currentRoute == "laba_rugi") Icons.Default.Search else Icons.Outlined.Search),
                                    Triple("jurnal_penutup", "Jurnal Penutup", if (viewModel.currentRoute == "jurnal_penutup") Icons.Default.Info else Icons.Outlined.Info),
                                    Triple("neraca_saldo_setelah_tutup", "Neraca S. Setelah Tutup", if (viewModel.currentRoute == "neraca_saldo_setelah_tutup") Icons.Default.AccountBalance else Icons.Outlined.AccountBalance)
                                )

                                reports.forEach { (route, title, iconVec) ->
                                    val isSelected = viewModel.currentRoute == route
                                    NavigationDrawerItem(
                                        icon = { Icon(imageVector = iconVec, contentDescription = title) },
                                        label = { Text(text = title, fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.currentRoute = route
                                            scope.launch { drawerState.close() }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Group: KONEKSI & BERBAGI (Boxed Group Card)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        // Header Clickable to Expand/Collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !isKoneksiBerbagiExpanded
                                    if (next) {
                                        isCompanyExpanded = false
                                        isManajemenExpanded = false
                                        isKeuanganExpanded = false
                                    }
                                    isKoneksiBerbagiExpanded = next
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isKoneksiBerbagiExpanded) Icons.Default.Share else Icons.Outlined.Share,
                                    contentDescription = "Koneksi & Berbagi",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "KONEKSI & BERBAGI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                            }
                            Icon(
                                imageVector = if (isKoneksiBerbagiExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isKoneksiBerbagiExpanded) "Sembunyikan" else "Tampilkan",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isKoneksiBerbagiExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val isShareSelected = viewModel.currentRoute == "share"
                                NavigationDrawerItem(
                                    icon = { Icon(imageVector = Icons.Default.Share, contentDescription = "Share Cloud") },
                                    label = { Text(text = "Share (Google Drive)", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    selected = isShareSelected,
                                    onClick = {
                                        viewModel.currentRoute = "share"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                                val isTransferFileSelected = viewModel.currentRoute == "transfer_file"
                                NavigationDrawerItem(
                                    icon = { Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Transfer File") },
                                    label = { Text(text = "Transfer File (Offline PC)", fontWeight = FontWeight.Medium, fontSize = 13.sp) },
                                    selected = isTransferFileSelected,
                                    onClick = {
                                        viewModel.currentRoute = "transfer_file"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                val isHtSelected = viewModel.currentRoute == "handy_talky"
                                NavigationDrawerItem(
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.SettingsVoice,
                                            contentDescription = "Handy Talky (HT)",
                                            tint = if (isHtSelected) MaterialTheme.colorScheme.primary else Color(0xFF0F766E)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "HT (Handy Talky)",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    },
                                    selected = isHtSelected,
                                    onClick = {
                                        viewModel.currentRoute = "handy_talky"
                                        scope.launch { drawerState.close() }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )

                                val isUserAdmin = viewModel.currentFirebaseUser?.uid == com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID
                                if (isUserAdmin) {
                                    val isManajemenAkunSelected = viewModel.currentRoute == "manajemen_akun"
                                    NavigationDrawerItem(
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Default.AdminPanelSettings,
                                                contentDescription = "Manajemen Akun",
                                                tint = Color(0xFF047857)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "Manajemen Akun",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        },
                                        selected = isManajemenAkunSelected,
                                        onClick = {
                                            viewModel.currentRoute = "manajemen_akun"
                                            scope.launch { drawerState.close() }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedContainerColor = Color.Transparent,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        }
                    }







                    }

                    // Bottom Drawer: Masuk, Daftar (tanpa title) disebelah kiri tombol Keluar jika belum login
                    val currentFirebaseUser = viewModel.currentFirebaseUser
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tampilkan tombol Masuk & Daftar hanya jika pengguna belum login
                        if (currentFirebaseUser == null) {
                            // Tombol Masuk tanpa title
                            Button(
                                onClick = {
                                    authDialogInitialMode = com.example.ui.components.AuthMode.LOGIN
                                    showAuthDialog = true
                                    scope.launch { drawerState.close() }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Login, contentDescription = "Masuk", modifier = Modifier.size(16.dp))
                            }

                            // Tombol Daftar tanpa title
                            Button(
                                onClick = {
                                    authDialogInitialMode = com.example.ui.components.AuthMode.REGISTER
                                    showAuthDialog = true
                                    scope.launch { drawerState.close() }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Daftar", modifier = Modifier.size(16.dp))
                            }
                        }

                        // Tombol Keluar: sama seperti pada halaman profil di bawah title "my singghara"
                        if (currentFirebaseUser != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.requestLogoutConfirmation {
                                        scope.launch { drawerState.close() }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Logout, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keluar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (viewModel.currentRoute != "semua_transaksi_belanja" && viewModel.currentRoute != "semua_catatan_tabungan" && viewModel.currentRoute != "preview_catatan" && viewModel.currentRoute != "immersive_file_viewer" && viewModel.currentRoute != "quran" && viewModel.currentRoute != "transfer_file" && viewModel.currentRoute != "handy_talky" && !viewModel.hideGlobalTopAppBar) {
                    Box(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
                    ) {
                        CenterAlignedTopAppBar(
                            title = {},
                            modifier = Modifier.height(48.dp),
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    },
                                    modifier = Modifier.testTag("drawer_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Menu Navigasi"
                                    )
                                }
                            },
                            actions = {
                                val context = LocalContext.current; val agendaItems by viewModel.agendaItems.collectAsState()
                                val jadwalList by viewModel.jadwalList.collectAsState()
                                val liveHijri by viewModel.apiHijriDate.collectAsState()
                                val fcmNotifications by viewModel.fcmNotifications.collectAsState()
                                val unreadFcmCount by viewModel.unreadFcmCount.collectAsState()
                                val adminMessagesList by viewModel.adminMessages.collectAsState()
                                val unreadAdminMessagesCount = viewModel.unreadAdminMessagesCount
                                val isUserAdmin = viewModel.currentFirebaseUser?.uid == com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID
                                var selectedNotificationTab by remember { mutableIntStateOf(0) }
                                var showTodayNotificationsDialog by remember { mutableStateOf(false) }
                                var showAllHolidaysInDialog by remember { mutableStateOf(false) }
                                var holidaySearchQuery by remember { mutableStateOf("") }
                                var selectedHolidayCategory by remember { mutableStateOf("Semua") }

                                LaunchedEffect(viewModel.showFcmNotificationsDialog) {
                                    if (viewModel.showFcmNotificationsDialog) {
                                        selectedNotificationTab = 1
                                        showTodayNotificationsDialog = true
                                        viewModel.showFcmNotificationsDialog = false
                                    }
                                }

                                val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
                                val todayAgendas = remember(agendaItems) { agendaItems.filter { it.tanggal == todayStr } }
                                val isDismissed = viewModel.agendaNotificationDismissedDate == todayStr
                                val activeAgendas = remember(todayAgendas, isDismissed) {
                                    todayAgendas
                                }

                                val todayCal = remember { Calendar.getInstance() }
                                val todayYear = todayCal.get(Calendar.YEAR)
                                val todayMonth = todayCal.get(Calendar.MONTH) + 1
                                val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)
                                val todayDateKey = remember { String.format("%04d-%02d-%02d", todayYear, todayMonth, todayDay) }
                                val fallbackHolidays = remember(todayYear) { HolidayClient.getFallbackHolidays(todayYear) }
                                val todayHolidayName = remember(todayDateKey, fallbackHolidays) { fallbackHolidays[todayDateKey] }
                                val todayObservanceName = remember(todayMonth, todayDay) { HolidayClient.getObservanceForDate(todayMonth, todayDay) }
                                val hasHolidayOrObservanceToday = todayHolidayName != null || todayObservanceName != null

                                val currentJawa = remember {
                                    JavaneseCalendarHelper.calculateJawa(
                                        todayCal.get(Calendar.YEAR),
                                        todayCal.get(Calendar.MONTH) + 1,
                                        todayCal.get(Calendar.DAY_OF_MONTH)
                                    )
                                }
                                val resolvedHijri = remember(liveHijri) {
                                    if (liveHijri != null) {
                                        liveHijri!!
                                    } else {
                                        HijriCalendarHelper.getHijriDate(
                                            todayCal.get(Calendar.YEAR),
                                            todayCal.get(Calendar.MONTH) + 1,
                                            todayCal.get(Calendar.DAY_OF_MONTH),
                                            viewModel.hijriOffset
                                        )
                                    }
                                }

                                val ongoingJadwal = remember(jadwalList) {
                                    jadwalList.find {
                                        try {
                                            val cleanRange = it.waktu.replace(" ", "")
                                            val parts = cleanRange.split("-")
                                            if (parts.size == 2) {
                                                val startStr = parts[0]
                                                val endStr = parts[1]
                                                val now = Calendar.getInstance()
                                                val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                                                val sParts = startStr.split(".")
                                                val eParts = endStr.split(".")
                                                val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
                                                val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
                                                nowMin in sMin..eMin
                                            } else false
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }
                                }

                                val totalNotificationsCount = activeAgendas.size + (if (ongoingJadwal != null) 1 else 0) + (if (hasHolidayOrObservanceToday) 1 else 0) + unreadFcmCount + unreadAdminMessagesCount

                                BadgedBox(
                                    badge = {
                                        if (totalNotificationsCount > 0) {
                                            Badge(
                                                containerColor = if (unreadAdminMessagesCount > 0) Color(0xFF047857) else if (unreadFcmCount > 0) MaterialTheme.colorScheme.error else if (todayHolidayName != null) Color(0xFFDC2626) else if (ongoingJadwal != null) MaterialTheme.colorScheme.primary else if (todayObservanceName != null) Color(0xFF0F766E) else MaterialTheme.colorScheme.error,
                                                contentColor = Color.White
                                            ) {
                                                Text(totalNotificationsCount.toString())
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (unreadAdminMessagesCount > 0) {
                                                selectedNotificationTab = 0
                                            } else if (unreadFcmCount > 0) {
                                                selectedNotificationTab = 1
                                            }
                                            showTodayNotificationsDialog = true
                                        },
                                        modifier = Modifier.testTag("top_notification_button")
                                    ) {
                                        Icon(
                                            imageVector = if (totalNotificationsCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                            contentDescription = "Pesan Notifikasi Admin, Cloud Messaging & Agenda",
                                            tint = when {
                                                unreadAdminMessagesCount > 0 -> Color(0xFF047857)
                                                unreadFcmCount > 0 -> MaterialTheme.colorScheme.error
                                                todayHolidayName != null -> Color(0xFFDC2626)
                                                ongoingJadwal != null -> MaterialTheme.colorScheme.primary
                                                todayObservanceName != null -> Color(0xFF0F766E)
                                                activeAgendas.isNotEmpty() -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            }
                                        )
                                    }
                                }

                                // Pop-up In-App Messaging saat aplikasi sedang dibuka
                                viewModel.activeInAppMessage?.let { activeMsg ->
                                    com.example.ui.components.InAppMessageDialog(
                                        notification = activeMsg,
                                        onDismiss = {
                                            viewModel.dismissInAppMessage()
                                        },
                                        onOpenInbox = {
                                            viewModel.openInboxFromInAppMessage()
                                            selectedNotificationTab = 1
                                            showTodayNotificationsDialog = true
                                        }
                                    )
                                }

                                if (showTodayNotificationsDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showTodayNotificationsDialog = false },
                                        title = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = when (selectedNotificationTab) {
                                                        0 -> Icons.Default.AdminPanelSettings
                                                        1 -> Icons.Default.CloudQueue
                                                        else -> Icons.Default.NotificationsActive
                                                    },
                                                    contentDescription = null,
                                                    tint = when (selectedNotificationTab) {
                                                        0 -> Color(0xFF047857)
                                                        1 -> MaterialTheme.colorScheme.primary
                                                        else -> if (todayHolidayName != null) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = when (selectedNotificationTab) {
                                                        0 -> "Pesan Administrator"
                                                        1 -> "Pesan Cloud Messaging"
                                                        else -> "Notifikasi & Pengingat"
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }
                                        },
                                        text = {
                                            val sharedDateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                // TAB SELECTOR: 3 TABS (Admin, Cloud FCM, Agenda & Info)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                        .padding(4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Tab 0: Pesan Admin (UID Terdaftar)
                                                    Surface(
                                                        onClick = { selectedNotificationTab = 0 },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (selectedNotificationTab == 0) Color(0xFF047857) else Color.Transparent,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AdminPanelSettings,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(15.dp),
                                                                tint = if (selectedNotificationTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Admin" + if (unreadAdminMessagesCount > 0) " ($unreadAdminMessagesCount)" else "",
                                                                fontSize = 11.sp,
                                                                fontWeight = if (selectedNotificationTab == 0) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (selectedNotificationTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // Tab 1: Cloud Messaging (FCM)
                                                    Surface(
                                                        onClick = { selectedNotificationTab = 1 },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (selectedNotificationTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CloudQueue,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(15.dp),
                                                                tint = if (selectedNotificationTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Cloud" + if (unreadFcmCount > 0) " ($unreadFcmCount)" else "",
                                                                fontSize = 11.sp,
                                                                fontWeight = if (selectedNotificationTab == 1) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (selectedNotificationTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    // Tab 2: Agenda & Info
                                                    Surface(
                                                        onClick = { selectedNotificationTab = 2 },
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = if (selectedNotificationTab == 2) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(vertical = 8.dp),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.DateRange,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(15.dp),
                                                                tint = if (selectedNotificationTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            val agendaBadge = activeAgendas.size + (if (ongoingJadwal != null) 1 else 0)
                                                            Text(
                                                                text = "Agenda" + if (agendaBadge > 0) " ($agendaBadge)" else "",
                                                                fontSize = 11.sp,
                                                                fontWeight = if (selectedNotificationTab == 2) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (selectedNotificationTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                if (selectedNotificationTab == 0) {
                                                    // ============================================
                                                    // DAFTAR PESAN DARI ADMIN (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2)
                                                    // ============================================
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF047857).copy(alpha = 0.09f)),
                                                        border = BorderStroke(1.dp, Color(0xFF047857).copy(alpha = 0.3f)),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.VerifiedUser,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF047857),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                                Text(
                                                                    text = "Pesan Resmi Administrator",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp,
                                                                    color = Color(0xFF047857)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Dipublikasikan oleh Administrator terdaftar (UID: ${com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID.take(12)}...)",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                if (unreadAdminMessagesCount > 0) {
                                                                    OutlinedButton(
                                                                        onClick = { viewModel.markAllAdminMessagesAsRead() },
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(30.dp)
                                                                    ) {
                                                                        Text("Tandai Semua Dibaca", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                                    }
                                                                } else {
                                                                    Text(
                                                                        text = "Semua pesan telah dibaca",
                                                                        fontSize = 10.sp,
                                                                        color = Color(0xFF047857),
                                                                        fontWeight = FontWeight.Medium
                                                                    )
                                                                }

                                                                if (isUserAdmin) {
                                                                    Button(
                                                                        onClick = {
                                                                            showTodayNotificationsDialog = false
                                                                            viewModel.currentRoute = "manajemen_akun"
                                                                        },
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                        modifier = Modifier.height(30.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(12.dp))
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text("Panel Admin", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (adminMessagesList.isEmpty()) {
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.AdminPanelSettings,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF047857).copy(alpha = 0.6f),
                                                                    modifier = Modifier.size(36.dp)
                                                                )
                                                                Text(
                                                                    text = "Belum Ada Pesan dari Administrator",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                Text(
                                                                    text = "Pesan pengumuman atau informasi resmi dari akun Admin (UID: ${com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID}) akan tampil di sini.",
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Daftar Pesan Admin (${adminMessagesList.size})",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (isUserAdmin) {
                                                                TextButton(
                                                                    onClick = {
                                                                        adminMessagesList.forEach { msg ->
                                                                            viewModel.deleteAdminMessagePermanently(msg.id)
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(0.dp)
                                                                ) {
                                                                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(13.dp))
                                                                    Spacer(modifier = Modifier.width(3.dp))
                                                                    Text("Hapus Semua (Admin)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                                                }
                                                            }
                                                        }

                                                        adminMessagesList.forEach { msg ->
                                                            val isRead = viewModel.isMessageRead(msg.id)
                                                            val msgCategoryColor = when (msg.category) {
                                                                "PERINGATAN" -> Color(0xFFDC2626)
                                                                "PEMBARUAN" -> Color(0xFF0F766E)
                                                                "INFORMASI" -> Color(0xFF2563EB)
                                                                else -> Color(0xFF047857)
                                                            }

                                                            Card(
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = if (!isRead) {
                                                                        Color(0xFF047857).copy(alpha = 0.08f)
                                                                    } else {
                                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                                    }
                                                                ),
                                                                border = if (!isRead) BorderStroke(1.dp, Color(0xFF047857).copy(alpha = 0.35f)) else null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                onClick = {
                                                                    if (!isRead) {
                                                                        viewModel.markAdminMessageAsRead(msg.id)
                                                                    }
                                                                }
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                                    ) {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Box(
                                                                                modifier = Modifier
                                                                                    .clip(RoundedCornerShape(4.dp))
                                                                                    .background(msgCategoryColor.copy(alpha = 0.15f))
                                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                            ) {
                                                                                Text(
                                                                                    text = msg.category,
                                                                                    color = msgCategoryColor,
                                                                                    fontSize = 9.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                            }

                                                                            if (msg.priority != "NORMAL") {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .clip(RoundedCornerShape(4.dp))
                                                                                        .background(Color(0xFFFEF2F2))
                                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                                ) {
                                                                                    Text(
                                                                                        text = msg.priority,
                                                                                        color = Color(0xFFDC2626),
                                                                                        fontSize = 9.sp,
                                                                                        fontWeight = FontWeight.Bold
                                                                                    )
                                                                                }
                                                                            }

                                                                            if (!isRead) {
                                                                                Surface(
                                                                                    shape = CircleShape,
                                                                                    color = Color(0xFF047857),
                                                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                                                ) {
                                                                                    Text(
                                                                                        text = "BARU",
                                                                                        color = Color.White,
                                                                                        fontSize = 8.sp,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }

                                                                        // Tombol Hapus Permanen dari Firestore (Khusus Admin UID Terdaftar)
                                                                        if (isUserAdmin) {
                                                                            IconButton(
                                                                                onClick = {
                                                                                    viewModel.deleteAdminMessagePermanently(msg.id)
                                                                                },
                                                                                modifier = Modifier.size(24.dp)
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Default.DeleteForever,
                                                                                    contentDescription = "Hapus Pesan Permanen dari Firestore",
                                                                                    tint = MaterialTheme.colorScheme.error,
                                                                                    modifier = Modifier.size(16.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }

                                                                    Spacer(modifier = Modifier.height(6.dp))

                                                                    Text(
                                                                        text = msg.title,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )

                                                                    Spacer(modifier = Modifier.height(4.dp))

                                                                    Text(
                                                                        text = msg.content,
                                                                        fontSize = 12.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        lineHeight = 16.sp
                                                                    )

                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                                    Spacer(modifier = Modifier.height(6.dp))

                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(
                                                                            text = sharedDateFmt.format(Date(msg.createdAt)),
                                                                            fontSize = 10.sp,
                                                                            color = MaterialTheme.colorScheme.outline
                                                                        )

                                                                        Text(
                                                                            text = "Admin: ${msg.authorUid.take(8)}...",
                                                                            fontSize = 10.sp,
                                                                            color = Color(0xFF047857),
                                                                            fontWeight = FontWeight.Medium
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else if (selectedNotificationTab == 1) {
                                                    // ============================================
                                                    // DAFTAR PESAN CLOUD MESSAGING (FIREBASE FCM)
                                                    // ============================================
                                                    if (fcmNotifications.isEmpty()) {
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.CloudQueue,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                                    modifier = Modifier.size(36.dp)
                                                                )
                                                                Text(
                                                                    text = "Belum Ada Pesan Masuk",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 13.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                Text(
                                                                    text = "Pesan pengumuman atau broadcast yang dikirim melalui Firebase Console akan tampil secara otomatis di sini.",
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Riwayat Pesan (${fcmNotifications.size})",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                if (unreadFcmCount > 0) {
                                                                    TextButton(
                                                                        onClick = { viewModel.markAllFcmAsRead() },
                                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text("Baca Semua", fontSize = 11.sp)
                                                                    }
                                                                }
                                                                TextButton(
                                                                    onClick = { viewModel.clearAllFcmNotifications() },
                                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                    Spacer(modifier = Modifier.width(4.dp))
                                                                    Text("Bersihkan", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                                }
                                                            }
                                                        }

                                                        val dateFmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
                                                        fcmNotifications.forEach { notif ->
                                                            Card(
                                                                colors = CardDefaults.cardColors(
                                                                    containerColor = if (!notif.isRead) {
                                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                                    } else {
                                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                                    }
                                                                ),
                                                                border = if (!notif.isRead) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                                                                modifier = Modifier.fillMaxWidth(),
                                                                onClick = {
                                                                    if (!notif.isRead) {
                                                                        viewModel.markFcmAsRead(notif.id)
                                                                    }
                                                                }
                                                            ) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = if (!notif.isRead) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                                                            contentDescription = null,
                                                                            tint = if (!notif.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(
                                                                            text = notif.title,
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 13.sp,
                                                                            color = MaterialTheme.colorScheme.onSurface,
                                                                            modifier = Modifier.weight(1f)
                                                                        )
                                                                        if (!notif.isRead) {
                                                                            Surface(
                                                                                shape = CircleShape,
                                                                                color = MaterialTheme.colorScheme.error,
                                                                                modifier = Modifier.padding(horizontal = 4.dp)
                                                                            ) {
                                                                                Text(
                                                                                    text = "BARU",
                                                                                    color = Color.White,
                                                                                    fontSize = 9.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                        IconButton(
                                                                            onClick = { viewModel.deleteFcmNotification(notif.id) },
                                                                            modifier = Modifier.size(24.dp)
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Close,
                                                                                contentDescription = "Hapus",
                                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                        }
                                                                    }

                                                                    if (notif.body.isNotEmpty()) {
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            text = notif.body,
                                                                            fontSize = 12.sp,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            lineHeight = 16.sp
                                                                        )
                                                                    }

                                                                    if (notif.dataPayload.isNotEmpty()) {
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Surface(
                                                                            shape = RoundedCornerShape(6.dp),
                                                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        ) {
                                                                            Text(
                                                                                text = "Data: ${notif.dataPayload}",
                                                                                fontSize = 10.sp,
                                                                                color = MaterialTheme.colorScheme.outline,
                                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                                            )
                                                                        }
                                                                    }

                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Text(
                                                                        text = dateFmt.format(Date(notif.timestamp)),
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.outline
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    // ============================================
                                                    // PANEL AGENDA, KALENDER & HARI LIBUR
                                                    // ============================================
                                                    Text(
                                                        text = "Kalender: $resolvedHijri • ${currentJawa.dina} ${currentJawa.pasaran}, Wuku ${currentJawa.wuku}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )

                                                // HARI LIBUR & PERINGATAN NASIONAL HARI INI
                                                if (todayHolidayName != null || todayObservanceName != null) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                    Text(
                                                        text = "Hari Libur & Peringatan Hari Ini",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (todayHolidayName != null) {
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.1f)),
                                                            border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Surface(
                                                                    shape = CircleShape,
                                                                    color = Color(0xFFDC2626),
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Text("🔴", fontSize = 16.sp)
                                                                    }
                                                                }
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column {
                                                                    Text(
                                                                        text = "HARI LIBUR NASIONAL",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFFDC2626)
                                                                    )
                                                                    Text(
                                                                        text = todayHolidayName,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 14.sp,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                    Text(
                                                                        text = "Selamat menikmati hari libur!",
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    if (todayObservanceName != null) {
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.1f)),
                                                            border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.4f)),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Surface(
                                                                    shape = CircleShape,
                                                                    color = Color(0xFF0F766E),
                                                                    modifier = Modifier.size(36.dp)
                                                                ) {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Star,
                                                                            contentDescription = null,
                                                                            tint = Color.White,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column {
                                                                    Text(
                                                                        text = "HARI BESAR / PERINGATAN NASIONAL",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = Color(0xFF0F766E)
                                                                    )
                                                                    Text(
                                                                        text = todayObservanceName,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 14.sp,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                // EXPANDABLE DAFTAR HARI LIBUR & PERINGATAN TAHUN INI
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp)) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable { showAllHolidaysInDialog = !showAllHolidaysInDialog },
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DateRange,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "Daftar Hari Libur & Hari Besar ($todayYear)",
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    fontSize = 13.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = if (showAllHolidaysInDialog) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }

                                                        if (showAllHolidaysInDialog) {
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            OutlinedTextField(
                                                                value = holidaySearchQuery,
                                                                onValueChange = { holidaySearchQuery = it },
                                                                placeholder = { Text("Cari hari libur / peringatan (mis: Buruh, Ibu, Guru)...", fontSize = 12.sp) },
                                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                                singleLine = true,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )

                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                                            ) {
                                                                listOf("Semua", "Libur Nasional", "Hari Besar").forEach { category ->
                                                                    FilterChip(
                                                                        selected = selectedHolidayCategory == category,
                                                                        onClick = { selectedHolidayCategory = category },
                                                                        label = { Text(category, fontSize = 11.sp) }
                                                                    )
                                                                }
                                                            }

                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            val allHolidaysList = remember(todayYear, fallbackHolidays) {
                                                                val list = mutableListOf<Pair<String, Pair<String, String>>>() // date, type, name
                                                                fallbackHolidays.forEach { (d, n) ->
                                                                    list.add(d to ("Libur Nasional" to n))
                                                                }
                                                                HolidayClient.getAllObservances().forEach { (md, n) ->
                                                                    val fullDate = "$todayYear-$md"
                                                                    if (fallbackHolidays[fullDate] == null) {
                                                                        list.add(fullDate to ("Hari Besar" to n))
                                                                    }
                                                                }
                                                                list.sortedBy { it.first }
                                                            }

                                                            val filteredHolidays = allHolidaysList.filter { item ->
                                                                val matchesCategory = when (selectedHolidayCategory) {
                                                                    "Libur Nasional" -> item.second.first == "Libur Nasional"
                                                                    "Hari Besar" -> item.second.first == "Hari Besar"
                                                                    else -> true
                                                                }
                                                                val matchesQuery = holidaySearchQuery.isBlank() ||
                                                                        item.second.second.contains(holidaySearchQuery, ignoreCase = true) ||
                                                                        item.first.contains(holidaySearchQuery, ignoreCase = true)
                                                                matchesCategory && matchesQuery
                                                            }

                                                            Column(
                                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .heightIn(max = 200.dp)
                                                                    .verticalScroll(rememberScrollState())
                                                            ) {
                                                                if (filteredHolidays.isEmpty()) {
                                                                    Text("Tidak ada hari libur / peringatan yang cocok.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                } else {
                                                                    filteredHolidays.forEach { (date, typeAndName) ->
                                                                        val isLibur = typeAndName.first == "Libur Nasional"
                                                                        Surface(
                                                                            shape = RoundedCornerShape(8.dp),
                                                                            color = if (isLibur) Color(0xFFDC2626).copy(alpha = 0.08f) else Color(0xFF0F766E).copy(alpha = 0.08f),
                                                                            border = BorderStroke(0.5.dp, if (isLibur) Color(0xFFDC2626).copy(alpha = 0.3f) else Color(0xFF0F766E).copy(alpha = 0.3f)),
                                                                            modifier = Modifier.fillMaxWidth()
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(8.dp),
                                                                                verticalAlignment = Alignment.CenterVertically
                                                                            ) {
                                                                                Text(
                                                                                    text = date,
                                                                                    fontSize = 11.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = if (isLibur) Color(0xFFDC2626) else Color(0xFF0F766E),
                                                                                    modifier = Modifier.width(80.dp)
                                                                                )
                                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                                Column {
                                                                                    Text(
                                                                                        text = typeAndName.second,
                                                                                        fontSize = 12.sp,
                                                                                        fontWeight = FontWeight.SemiBold,
                                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                                    )
                                                                                    Text(
                                                                                        text = typeAndName.first,
                                                                                        fontSize = 10.sp,
                                                                                        color = if (isLibur) Color(0xFFDC2626) else Color(0xFF0F766E)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (ongoingJadwal != null) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                                    Text(
                                                        text = "Jadwal Sedang Berlangsung 🕒",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    Card(
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                        ),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Schedule,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column {
                                                                Text(
                                                                    text = ongoingJadwal.kegiatan,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 14.sp,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                                Text(
                                                                    text = "Pukul ${ongoingJadwal.waktu} • Kategori: ${ongoingJadwal.kategori}",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (activeAgendas.isNotEmpty()) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                                    Text(
                                                        text = "Agenda Kegiatan Hari Ini (${activeAgendas.size})",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        activeAgendas.forEach { agenda ->
                                                            Card(
                                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Column(modifier = Modifier.padding(10.dp)) {
                                                                    Text(
                                                                        text = agenda.judul,
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 13.sp,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                    if (agenda.detail.isNotEmpty()) {
                                                                        Text(
                                                                            text = agenda.detail,
                                                                            fontSize = 12.sp,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (activeAgendas.isNotEmpty()) {
                                                    Button(
                                                        onClick = {
                                                            showTodayNotificationsDialog = false; if (isDismissed) { viewModel.setAgendaNotificationDismissedToday(false); viewModel.reShowDailyAgendaNotification(); viewModel.showToastPopup("Notifikasi agenda ditampilkan kembali di status bar") } else
                                                            viewModel.showConfirmDeleteNotificationDialog = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isDismissed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                            contentColor = if (isDismissed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isDismissed) Icons.Default.NotificationsActive else Icons.Default.Close,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(if (isDismissed) "Tampilkan Kembali di Status Bar" else "Hapus dari Status Bar")
                                                    }
                                                }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            TextButton(onClick = { showTodayNotificationsDialog = false }) {
                                                Text("Tutup")
                                            }
                                        }
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            },
            bottomBar = {
                val hideBottomBarRoutes = listOf("jurnal_umum", "buku_besar", "neraca_saldo", "jurnal_penyesuaian", "neraca_saldo_disesuaikan", "laba_rugi", "jurnal_penutup", "neraca_saldo_setelah_tutup", "pemutar_musik", "pembukuan_usaha", "semua_transaksi_belanja", "semua_catatan_tabungan", "preview_catatan", "immersive_file_viewer", "quran", "handy_talky", "telegram_ai")
                val isEditingNote = viewModel.currentRoute == "catatan" && viewModel.selectedNote != null
                if (viewModel.isBottomBarVisible && !hideBottomBarRoutes.contains(viewModel.currentRoute) && !isEditingNote) {
                    BottomNavBar(viewModel = viewModel)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 0.dp,
                        end = 0.dp,
                        top = if (innerPadding.calculateTopPadding() > 0.dp) (innerPadding.calculateTopPadding() - 12.dp).coerceAtLeast(0.dp) else 0.dp,
                        bottom = innerPadding.calculateBottomPadding()
                    )
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Animated screen navigation
                AnimatedContent(
                    targetState = viewModel.currentRoute,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) with fadeOut(animationSpec = tween(220))
                    },
                    label = "ScreenTransition"
                ) { route ->
                    when (route) {
                        "home" -> HomeScreen(viewModel = viewModel)
                        "rekap_belanja_pasar" -> RekapBelanjaPasarScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.currentRoute = "home" },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                        "arisan_keluarga" -> ArisanKeluargaScreen(
                            viewModel = viewModel,
                            onNavigateBack = { viewModel.currentRoute = "home" },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                        "quran" -> QuranScreen(viewModel = viewModel)
                        "game_list" -> GameScreen(viewModel = viewModel)
                        "sliding_puzzle" -> com.example.ui.screens.SlidingPuzzleGame(
                            onBackClick = { viewModel.currentRoute = "home" }
                        )
                        "jigsaw_puzzle" -> com.example.ui.screens.JigsawPuzzleGame(
                            onBackClick = { viewModel.currentRoute = "home" }
                        )
                        "belanja" -> BelanjaScreen(viewModel = viewModel)
                        "semua_transaksi_belanja" -> SemuaTransaksiBelanjaScreen(viewModel = viewModel)
                        "tabungan" -> TabunganScreen(viewModel = viewModel)
                        "semua_catatan_tabungan" -> SemuaCatatanTabunganScreen(viewModel = viewModel)
                        "catatan" -> CatatanScreen(viewModel = viewModel)
                        "immersive_file_viewer" -> ImmersiveFileViewerScreen(viewModel = viewModel)
                        "preview_catatan" -> {
                            val rawHtml = viewModel.notePreviewHtml
                            val formattedHtml = if (!rawHtml.contains("<meta name=\"viewport\"") && !rawHtml.contains("<meta name='viewport'")) {
                                """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta charset="UTF-8">
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                                    <style>
                                        body {
                                            margin: 16px;
                                            padding: 0;
                                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                            line-height: 1.6;
                                            color: #1C1B1F;
                                            background-color: #FFFFFF;
                                            word-wrap: break-word;
                                            overflow-wrap: break-word;
                                            overflow-x: hidden;
                                        }
                                        img, video, iframe, canvas, svg {
                                            max-width: 100% !important;
                                            height: auto !important;
                                        }
                                        table {
                                            border-collapse: collapse;
                                            width: 100% !important;
                                            margin-bottom: 16px;
                                            overflow-x: auto;
                                            display: block;
                                        }
                                        th, td {
                                            border: 1px solid #E0E0E0;
                                            padding: 8px;
                                            text-align: left;
                                        }
                                        pre, code {
                                            font-family: monospace;
                                            background-color: #F5F5F5;
                                            padding: 4px 8px;
                                            border-radius: 4px;
                                            white-space: pre-wrap;
                                            word-wrap: break-word;
                                        }
                                    </style>
                                </head>
                                <body>
                                    $rawHtml
                                </body>
                                </html>
                                """.trimIndent()
                            } else {
                                val styleOverride = """
                                    <style>
                                        img, video, iframe, canvas, svg {
                                            max-width: 100% !important;
                                            height: auto !important;
                                        }
                                        pre, code {
                                            white-space: pre-wrap !important;
                                            word-wrap: break-word !important;
                                        }
                                        table {
                                            max-width: 100% !important;
                                            display: block !important;
                                            overflow-x: auto !important;
                                        }
                                    </style>
                                """.trimIndent()
                                if (rawHtml.contains("</head>")) {
                                    rawHtml.replace("</head>", "$styleOverride</head>")
                                } else {
                                    styleOverride + rawHtml
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.useWideViewPort = true
                                            settings.loadWithOverviewMode = true
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            settings.supportZoom()
                                            webViewClient = WebViewClient()
                                            loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
                                        }
                                    },
                                    update = { webView ->
                                        webView.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        "data_karyawan_murid" -> DataKaryawanMuridScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.currentRoute = "home" },
                            onOpenFullTable = { viewModel.currentRoute = "tabel_anggota_lengkap" }
                        )
                        "tabel_anggota_lengkap" -> DataKaryawanMuridScreen(
                            viewModel = viewModel,
                            isFullTableOnly = true,
                            onBack = { viewModel.currentRoute = "data_karyawan_murid" }
                        )
                        "absensi_karyawan" -> AbsensiScreen(
                            viewModel = viewModel,
                            isFullPage = false,
                            onOpenFullHistory = { viewModel.currentRoute = "riwayat_absensi_lengkap" },
                            onNavigateBack = { viewModel.currentRoute = "data_karyawan_murid" },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                        "gaji_karyawan" -> GajiScreen(
                            viewModel = viewModel,
                            onNavigateBack = { viewModel.currentRoute = "data_karyawan_murid" },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                        "riwayat_absensi_lengkap" -> AbsensiScreen(
                            viewModel = viewModel,
                            isFullPage = true,
                            onNavigateBack = { viewModel.currentRoute = "absensi_karyawan" },
                            onOpenDrawer = { scope.launch { drawerState.open() } }
                        )
                        "profil" -> ProfilScreen(
                            viewModel = viewModel,
                            onEditProfileClick = {
                                tempProfileName = viewModel.profileName
                                selectedAvatarIndex = viewModel.profileAvatarIndex
                                showProfileEditDialog = true
                            }
                        )
                        "share" -> ShareScreen(viewModel = viewModel)
                        "transfer_file" -> com.example.ui.screens.TransferFileScreen(viewModel = viewModel)
                        "manajemen_akun" -> com.example.ui.screens.ManajemenAkunScreen(
                            viewModel = viewModel,
                            onBack = { viewModel.currentRoute = "profil" },
                            onOpenAuthDialog = {
                                authDialogInitialMode = com.example.ui.components.AuthMode.LOGIN
                                showAuthDialog = true
                            }
                        )
                        "pemutar_musik" -> PemutarMusikScreen(viewModel = viewModel, onOpenDrawer = { scope.launch { drawerState.open() } })
                        "pembukuan_usaha" -> PembukuanScreen(viewModel = viewModel)
                        "my_kasir" -> MyKasirScreen(viewModel = viewModel)
                        "jurnal_umum" -> JurnalUmumScreen(
                            journalEntries = journalEntries,
                            onSaveEntry = { newEntry ->
                                val updatedList = journalEntries + newEntry
                                val serialized = serializeJournalEntries(updatedList)
                                viewModel.saveJournalEntriesStr(serialized)
                            },
                            onDeleteEntry = { index ->
                                val updatedList = journalEntries.toMutableList().apply {
                                    removeAt(index)
                                }
                                val serialized = serializeJournalEntries(updatedList)
                                viewModel.saveJournalEntriesStr(serialized)
                            }
                        )
                        "buku_besar" -> BukuBesarScreen(
                            journalEntries = journalEntries,
                            viewModel = viewModel
                        )
                        "neraca_saldo" -> NeracaSaldoScreen(
                            journalEntries = journalEntries,
                            viewModel = viewModel
                        )
                        "jurnal_penyesuaian" -> JurnalPenyesuaianScreen(
                            journalEntries = journalEntries,
                            adjustmentEntries = adjustmentEntries,
                            onSaveEntry = { newEntry ->
                                val updatedList = adjustmentEntries + newEntry
                                val serialized = serializeAdjustmentEntries(updatedList)
                                viewModel.saveAdjustmentEntriesStr(serialized)
                            },
                            onDeleteEntry = { index ->
                                val updatedList = adjustmentEntries.toMutableList().apply {
                                    removeAt(index)
                                }
                                val serialized = serializeAdjustmentEntries(updatedList)
                                viewModel.saveAdjustmentEntriesStr(serialized)
                            },
                            viewModel = viewModel
                        )
                        "neraca_saldo_disesuaikan" -> NeracaSaldoDisesuaikanScreen(
                            journalEntries = journalEntries,
                            adjustmentEntries = adjustmentEntries,
                            viewModel = viewModel
                        )
                        "laba_rugi" -> LabaRugiScreen(
                            journalEntries = journalEntries,
                            adjustmentEntries = adjustmentEntries,
                            viewModel = viewModel
                        )
                        "jurnal_penutup" -> JurnalPenutupScreen(
                            journalEntries = journalEntries,
                            adjustmentEntries = adjustmentEntries,
                            viewModel = viewModel
                        )
                        "neraca_saldo_setelah_tutup" -> NeracaSaldoSetelahTutupScreen(
                            journalEntries = journalEntries,
                            adjustmentEntries = adjustmentEntries,
                            viewModel = viewModel
                        )
                        "mindmap" -> MindMapScreen(
                            viewModel = viewModel,
                            onBackClick = {
                                viewModel.currentRoute = "home"
                                viewModel.showAllAppsPage = true
                            }
                        )
                        "jadwal_harian" -> JadwalHarianScreen(
                            viewModel = viewModel,
                            onBackClick = {
                                viewModel.currentRoute = "home"
                                viewModel.showAllAppsPage = true
                            }
                        )
                        "web_connect" -> WebConnectScreen(
                            viewModel = viewModel,
                            onBackClick = {
                                viewModel.currentRoute = "profil"
                            }
                        )
                        "tentang_aplikasi" -> TentangAplikasiScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                viewModel.currentRoute = "profil"
                            }
                        )
                        "telegram_ai" -> com.example.ui.screens.TelegramAiScreen(
                            viewModel = viewModel,
                            onBackClick = {
                                viewModel.currentRoute = "profil"
                            }
                        )
                        "handy_talky" -> com.example.ui.screens.HandyTalkyScreen(
                            onNavigateBack = {
                                viewModel.currentRoute = "home"
                            }
                        )
                        else -> HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Firebase Auth Dialog (Login, Register, Forgot Password, OTP Verification)
        if (showAuthDialog) {
            com.example.ui.components.FirebaseAuthDialog(
                viewModel = viewModel,
                initialMode = authDialogInitialMode,
                onDismissRequest = { showAuthDialog = false }
            )
        }

        // Animated Auth Feedback Dialog (Masuk, Daftar, Konfirmasi Keluar, Keluar Berhasil)
        val currentAuthFeedback = viewModel.authFeedbackState
        if (currentAuthFeedback != null) {
            com.example.ui.components.AuthFeedbackDialog(
                feedbackState = currentAuthFeedback,
                onDismiss = { viewModel.dismissAuthFeedback() }
            )
        }
    }
}

@Composable
fun LockScreenLayout(
    viewModel: MainViewModel,
    onAuthenticate: (onSuccess: () -> Unit) -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var isUnlocking by remember { mutableStateOf(false) }
    var showBiometricEnrollDialog by remember { mutableStateOf(false) }
    val unlockProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    val triggerUnlockAnimation: () -> Unit = {
        isUnlocking = true
        errorMessage = ""
        coroutineScope.launch {
            unlockProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1100,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
            kotlinx.coroutines.delay(120)
            viewModel.isAppUnlocked = true
            viewModel.lastBackgroundTime = 0L
        }
    }

    // Ketika 4 digit telah dimasukkan
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4 && !isUnlocking) {
            // Tunda 200ms agar titik indikator ke-4 sempat muncul penuh di layar secara jelas
            kotlinx.coroutines.delay(200)
            if (enteredPin == viewModel.appPin) {
                failedAttempts = 0
                triggerUnlockAnimation()
            } else {
                errorMessage = "PIN salah! Silakan coba lagi."
                enteredPin = ""
                failedAttempts++
                if (failedAttempts >= 2) {
                    val biometricManager = BiometricManager.from(context)
                    val canAuth = biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                        viewModel.showToastPopup("Lupa PIN? Mengaktifkan Biometrik (Sidik Jari / Wajah)...")
                        onAuthenticate {
                            enteredPin = "****"
                            triggerUnlockAnimation()
                        }
                    } else if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                        showBiometricEnrollDialog = true
                    }
                }
            }
        }
    }

    // Cegah bypass back button: jika back ditekan, aplikasi langsung keluar secara aman
    BackHandler {
        onExit()
    }

    // Pemicu otomatis biometric saat dimuat pertama kali
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            onAuthenticate {
                enteredPin = "****"
                triggerUnlockAnimation()
            }
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Intercept clicks on lock screen overlay to prevent touch leakage to bottom nav bar or underlying views
                }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
        if (isUnlocking) {
            val infiniteTransition = rememberInfiniteTransition(label = "unlockRing")
            val rotationAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            val currentProgress = unlockProgress.value
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val surfaceColor = MaterialTheme.colorScheme.surface

            val stageStatusText = when {
                currentProgress < 0.35f -> "Mengautentikasi Kunci Keamanan..."
                currentProgress < 0.70f -> "Dekripsi Data & Sesi Pengguna..."
                currentProgress < 0.95f -> "Menyiapkan Ruang Kerja..."
                else -> "Akses Diberikan! Selamat Datang"
            }

            val currentIcon = when {
                currentProgress < 0.40f -> Icons.Default.Lock
                currentProgress < 0.85f -> Icons.Default.LockOpen
                else -> Icons.Default.Check
            }

            val iconTint = if (currentProgress < 0.85f) {
                primaryColor
            } else {
                androidx.compose.ui.graphics.Color(0xFF10B981)
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Interactive Background Pulsing Waves
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val centerPt = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.26f * (1f + currentProgress * 0.35f)
                    
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.12f * pulseAlpha),
                        radius = baseRadius * 1.5f,
                        center = centerPt
                    )
                    drawCircle(
                        color = tertiaryColor.copy(alpha = 0.18f * pulseAlpha),
                        radius = baseRadius * 1.15f,
                        center = centerPt
                    )
                }

                // Main Interactive Card
                Card(
                    modifier = Modifier
                        .padding(28.dp)
                        .fillMaxWidth()
                        .shadow(20.dp, RoundedCornerShape(32.dp)),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = surfaceColor
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(32.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Rotating Outer Cyber Ring & Glowing Badge
                        Box(
                            modifier = Modifier.size(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { rotationZ = rotationAngle }
                            ) {
                                val strokeWidth = 5.dp.toPx()
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            primaryColor,
                                            tertiaryColor,
                                            primaryColor.copy(alpha = 0.2f),
                                            primaryColor
                                        )
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = 280f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }

                            Icon(
                                imageVector = currentIcon,
                                contentDescription = "Status Kunci",
                                tint = iconTint,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = if (currentProgress >= 0.95f) "Verifikasi Sukses!" else "Otentikasi Berhasil",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stageStatusText,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Large Percentage Display
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${(currentProgress * 100).toInt()}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "%",
                                color = primaryColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Futuristic Neon Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = currentProgress.coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                primaryColor,
                                                tertiaryColor,
                                                primaryColor
                                            )
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sistem Kriptografi Aktif • My Singghara",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo / Gembok Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Fitur Terkunci",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "My Singghara Terkunci",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Masukkan 4 digit PIN",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN Bullet Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
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

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message
                Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Numeric Keypad 3x4 Grid
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
                                KeypadButton(text = num) {
                                    if (enteredPin.length < 4 && !isUnlocking) {
                                        enteredPin += num
                                        errorMessage = ""
                                    }
                                }
                            }
                        }
                    }

                    // Row terakhir: Biometric, 0, Backspace
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Biometrik
                        Surface(
                            onClick = {
                                if (!isUnlocking) {
                                    val biometricManager = BiometricManager.from(context)
                                    val canAuth = biometricManager.canAuthenticate(
                                        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                                    )
                                    when (canAuth) {
                                        BiometricManager.BIOMETRIC_SUCCESS -> {
                                            onAuthenticate {
                                                enteredPin = "****"
                                                triggerUnlockAnimation()
                                            }
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                            showBiometricEnrollDialog = true
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                                            viewModel.showToastPopup("Biometrik tidak tersedia. Gunakan PIN.")
                                        }
                                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                                            viewModel.showToastPopup("Biometrik tidak aktif. Gunakan PIN.")
                                        }
                                        else -> {
                                            viewModel.showToastPopup("Masukkan PIN 4 digit Anda.")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .shadow(elevation = 6.dp, shape = CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Sidik Jari / Wajah",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Angka 0
                        KeypadButton(text = "0") {
                            if (enteredPin.length < 4 && !isUnlocking) {
                                enteredPin += "0"
                                errorMessage = ""
                            }
                        }

                        // Tombol Backspace
                        Surface(
                            onClick = {
                                if (enteredPin.isNotEmpty() && !isUnlocking) {
                                    enteredPin = enteredPin.dropLast(1)
                                    errorMessage = ""
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
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

                Spacer(modifier = Modifier.height(28.dp))

                // Tombol Keluar Aplikasi secara Aman
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Keluar Aplikasi",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (showBiometricEnrollDialog) {
            AlertDialog(
                onDismissRequest = { showBiometricEnrollDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Biometrik Belum Terdaftar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = "Sidik Jari atau Pengenalan Wajah belum terdaftar di Pengaturan HP Anda.\n\nPerangkat Android Anda mendukung biometrik, namun Anda perlu mendaftarkan sidik jari/wajah terlebih dahulu di Pengaturan Keamanan HP agar fitur ini dapat digunakan.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBiometricEnrollDialog = false
                            try {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                        putExtra(
                                            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                                        )
                                    }
                                } else {
                                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                } catch (ex: Exception) {
                                    viewModel.showToastPopup("Gagal membuka Pengaturan HP. Silakan buka Pengaturan > Keamanan HP Anda.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Buka Pengaturan HP", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBiometricEnrollDialog = false }) {
                        Text("Gunakan PIN")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .shadow(elevation = 6.dp, shape = CircleShape),
        shape = CircleShape,
        color = if (isPressed) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPressed) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Interactive Glowing/Lighting Dot Indicator
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPressed) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

data class JournalEntry(
    val tanggal: String,
    val keterangan: String,
    val debetAkun: String,
    val kreditAkun: String,
    val nominal: Double
)

fun serializeJournalEntries(entries: List<JournalEntry>): String {
    return entries.joinToString("&&") { entry ->
        val safeKet = entry.keterangan.replace("##", " ").replace("&&", " ")
        "${entry.tanggal}##${safeKet}##${entry.debetAkun}##${entry.kreditAkun}##${entry.nominal}"
    }
}

fun deserializeJournalEntries(serialized: String): List<JournalEntry> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull { item ->
            val parts = item.split("##")
            if (parts.size == 5) {
                JournalEntry(
                    tanggal = parts[0],
                    keterangan = parts[1],
                    debetAkun = parts[2],
                    kreditAkun = parts[3],
                    nominal = parts[4].toDoubleOrNull() ?: 0.0
                )
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun getAccountRef(accountName: String): String {
    if (accountName.isBlank()) return "-"
    val clean = accountName.trim().lowercase(java.util.Locale.ROOT)
    return when {
        clean.contains("kas") -> "101"
        clean.contains("piutang") -> "102"
        clean.contains("perlengkapan") -> "103"
        clean.contains("sewa dibayar") -> "104"
        clean.contains("asuransi dibayar") -> "105"
        clean.contains("peralatan") && !clean.contains("penyusutan") && !clean.contains("beban") -> "121"
        clean.contains("akumulasi") && clean.contains("peralatan") -> "122"
        clean.contains("kendaraan") && !clean.contains("penyusutan") && !clean.contains("beban") -> "123"
        clean.contains("akumulasi") && clean.contains("kendaraan") -> "124"
        clean.contains("utang usaha") -> "201"
        clean.contains("utang gaji") -> "202"
        clean.contains("pendapatan diterima") -> "203"
        clean.contains("utang bank") -> "211"
        clean.contains("utang") -> "201"
        clean.contains("modal") -> "301"
        clean.contains("prive") -> "302"
        clean.contains("ikhtisar") -> "303"
        clean.contains("pendapatan jasa") -> "401"
        clean.contains("pendapatan penjualan") || clean.contains("penjualan") -> "402"
        clean.contains("pendapatan lain") -> "403"
        clean.contains("pendapatan") -> "401"
        clean.contains("harga pokok") || clean.contains("hpp") -> "501"
        clean.contains("beban gaji") -> "502"
        clean.contains("beban sewa") -> "503"
        clean.contains("beban perlengkapan") -> "504"
        clean.contains("beban asuransi") -> "505"
        clean.contains("beban penyusutan peralatan") -> "506"
        clean.contains("beban penyusutan kendaraan") -> "507"
        clean.contains("beban listrik") || clean.contains("air") || clean.contains("telepon") -> "508"
        clean.contains("beban iklan") -> "509"
        clean.contains("beban") || clean.contains("biaya") -> "510"
        else -> "100"
    }
}

data class AdjustmentEntry(
    val tanggal: String,
    val jenisPenyesuaian: String,
    val debetAkun: String,
    val kreditAkun: String,
    val keterangan: String,
    val nominal: Double
)

fun serializeAdjustmentEntries(entries: List<AdjustmentEntry>): String {
    return entries.joinToString("&&") { entry ->
        val safeKet = entry.keterangan.replace("##", " ").replace("&&", " ")
        val safeJenis = entry.jenisPenyesuaian.replace("##", " ").replace("&&", " ")
        "${entry.tanggal}##${safeJenis}##${entry.debetAkun}##${entry.kreditAkun}##${safeKet}##${entry.nominal}"
    }
}

fun deserializeAdjustmentEntries(serialized: String): List<AdjustmentEntry> {
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("&&").mapNotNull { item ->
            val parts = item.split("##")
            if (parts.size == 6) {
                AdjustmentEntry(
                    tanggal = parts[0],
                    jenisPenyesuaian = parts[1],
                    debetAkun = parts[2],
                    kreditAkun = parts[3],
                    keterangan = parts[4],
                    nominal = parts[5].toDoubleOrNull() ?: 0.0
                )
            } else null
        }
    } catch (e: Exception) {
        emptyList()
    }
}

@Composable
fun DialogAccountPicker(
    title: String,
    onDismiss: () -> Unit,
    onSelectAccount: (String) -> Unit
) {
    val groupedAccounts = listOf(
        "Kas" to listOf("Kas"),
        "Aset" to listOf(
            "Piutang Usaha", "Perlengkapan", "Sewa dibayar di muka", 
            "Asuransi dibayar di muka", "Peralatan", "Akumulasi penyusutan peralatan", 
            "Kendaraan", "Akumulasi penyusutan kendaraan"
        ),
        "Liabilitas" to listOf("Utang usaha", "Utang gaji", "Utang bank", "Pendapatan diterima di muka"),
        "Ekuitas" to listOf("Modal pemilik", "Prive"),
        "Pendapatan" to listOf("Pendapatan jasa", "Pendapatan penjualan", "Pendapatan lain-lain"),
        "Beban" to listOf(
            "Harga Pokok Penjualan", "Beban gaji", "Beban sewa", "Beban perlengkapan", 
            "Beban asuransi", "Beban penyusutan peralatan", "Beban penyusutan kendaraan", 
            "Beban listrik air telepon", "Beban iklan", "Beban lain-lain"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedAccounts.forEach { (category, subAccounts) ->
                        item {
                            Text(
                                text = category.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(subAccounts) { subAcc ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAccount(subAcc) },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info, 
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subAcc,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatToIndonesianDate(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    return try {
        val sdfInput = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val date = sdfInput.parse(dateStr)
        val sdfOutput = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("id", "ID"))
        sdfOutput.format(date ?: java.util.Date())
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
fun DialogInputTransaksi(
    onDismiss: () -> Unit,
    onSave: (JournalEntry) -> Unit
) {
    val currentDate = remember {
        val cal = Calendar.getInstance()
        val yr = cal.get(Calendar.YEAR)
        val mo = cal.get(Calendar.MONTH) + 1
        val dy = cal.get(Calendar.DAY_OF_MONTH)
        val moStr = if (mo < 10) "0$mo" else "$mo"
        val dyStr = if (dy < 10) "0$dy" else "$dy"
        "$yr-$moStr-$dyStr"
    }
    var tanggalInput by remember { mutableStateOf(currentDate) }
    var keteranganInput by remember { mutableStateOf("") }
    var nominalInput by remember { mutableStateOf("") }
    
    var debetAkun by remember { mutableStateOf("Kas") }
    var kreditAkun by remember { mutableStateOf("Pendapatan jasa") }
    
    var showDebetPicker by remember { mutableStateOf(false) }
    var showKreditPicker by remember { mutableStateOf(false) }
    
    if (showDebetPicker) {
        DialogAccountPicker(
            title = "Pilih Akun Debit",
            onDismiss = { showDebetPicker = false },
            onSelectAccount = { 
                debetAkun = it
                showDebetPicker = false
            }
        )
    }
    
    if (showKreditPicker) {
        DialogAccountPicker(
            title = "Pilih Akun Kredit",
            onDismiss = { showKreditPicker = false },
            onSelectAccount = { 
                kreditAkun = it
                showKreditPicker = false
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Input Transaksi Baru",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tanggal
                val context = androidx.compose.ui.platform.LocalContext.current
                var showDatePickerDialog by remember { mutableStateOf(false) }
                val showDatePicker = { showDatePickerDialog = true }

                if (showDatePickerDialog) {
                    com.example.ui.components.CustomDatePickerDialog(
                        initialDateStr = tanggalInput,
                        onDismissRequest = { showDatePickerDialog = false },
                        onDateSelected = { selectedDate ->
                            tanggalInput = selectedDate
                            showDatePickerDialog = false
                        }
                    )
                }

                OutlinedTextField(
                    value = tanggalInput,
                    onValueChange = { tanggalInput = it },
                    label = { Text("Tanggal Transaksi (YYYY-MM-DD)", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: 2026-07-25") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker() }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Pilih Tanggal Dari Kalender")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Keterangan
                OutlinedTextField(
                    value = keteranganInput,
                    onValueChange = { keteranganInput = it },
                    label = { Text("Keterangan", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: Menerima modal awal") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Card Pemilihan Debit/Kredit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Debit
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDebetPicker = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Akun Debit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                debetAkun,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    // Kredit
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showKreditPicker = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Akun Kredit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                kreditAkun,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Nominal
                OutlinedTextField(
                    value = nominalInput,
                    onValueChange = { nominalInput = it },
                    label = { Text("Nominal Transaksi (Rp)", fontSize = 12.sp) },
                    placeholder = { Text("Contoh: 150000") },
                    leadingIcon = {
                        Text(
                            "Rp", 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nominalVal = nominalInput.replace(",", "").replace(".", "").toDoubleOrNull() ?: 0.0
                    if (keteranganInput.isBlank()) {
                        return@Button
                    }
                    if (nominalVal <= 0.0) {
                        return@Button
                    }
                    val entry = JournalEntry(
                        tanggal = tanggalInput,
                        keterangan = keteranganInput,
                        debetAkun = debetAkun,
                        kreditAkun = kreditAkun,
                        nominal = nominalVal
                    )
                    onSave(entry)
                },
                enabled = keteranganInput.isNotBlank() && nominalInput.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Simpan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun DialogJurnalUmum(
    entries: List<JournalEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Jurnal Umum", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "${entries.size} Entri Transaksi", 
                        fontSize = 11.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Belum ada entri transaksi di Jurnal Umum.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Row 1: Tanggal & Nominal
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = entry.tanggal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Rp %,.0f".format(entry.nominal),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Row 2: Keterangan
                                    Text(
                                        text = entry.keterangan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Row 3: Debit & Kredit accounts layout
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "(D) " + entry.debetAkun,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Rp %,.0f".format(entry.nominal),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "    (K) " + entry.kreditAkun,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Rp %,.0f".format(entry.nominal),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
