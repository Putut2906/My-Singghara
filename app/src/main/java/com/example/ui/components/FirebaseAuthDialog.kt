package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.firebase.FirebaseAuthManager
import com.example.ui.MainViewModel
import com.example.ui.screens.BarcodeScannerDialog
import com.example.utils.VipMembershipCardGenerator
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD,
    VERIFY_OTP,
    RESET_NEW_PASSWORD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseAuthDialog(
    viewModel: MainViewModel,
    initialMode: AuthMode = AuthMode.LOGIN,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentMode by remember { mutableStateOf(initialMode) }

    // Form States
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    var showQuickLoginScanner by remember { mutableStateOf(false) }

    var showLinkGoogleDialog by remember { mutableStateOf(false) }
    var pendingLinkEmail by remember { mutableStateOf("") }
    var pendingGoogleAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    var linkPasswordInput by remember { mutableStateOf("") }
    var linkPasswordVisible by remember { mutableStateOf(false) }
    var linkIsLoading by remember { mutableStateOf(false) }
    var linkErrorMessage by remember { mutableStateOf<String?>(null) }

    var showGoogleAccountEmailNotice by remember { mutableStateOf(false) }
    var googleRegisteredEmail by remember { mutableStateOf("") }

    // Dialog Pembuatan Kata Sandi Setelah Daftar/Login Google
    var showSetPasswordAfterGoogleDialog by remember { mutableStateOf(false) }
    var pendingGoogleUser by remember { mutableStateOf<com.google.firebase.auth.FirebaseUser?>(null) }
    var pendingGoogleUserName by remember { mutableStateOf("") }
    var pendingGoogleUserEmail by remember { mutableStateOf("") }
    var googleAccountPasswordInput by remember { mutableStateOf("") }
    var googleAccountPasswordConfirm by remember { mutableStateOf("") }
    var googleAccountPasswordVisible by remember { mutableStateOf(false) }
    var googleAccountPasswordConfirmVisible by remember { mutableStateOf(false) }
    var googleAccountPasswordLoading by remember { mutableStateOf(false) }
    var googleAccountPasswordError by remember { mutableStateOf<String?>(null) }

    // Generated OTP info
    var generatedOtp by remember { mutableStateOf("") }
    var otpTargetEmailOrPhone by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val displayName = account.displayName ?: account.email ?: ""
                    val accountEmail = account.email ?: ""
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val authResult = viewModel.authManager.signInWithGoogleAccount(account, context)
                        isLoading = false
                        authResult.onSuccess { user ->
                            viewModel.refreshAuthUser()
                            val cleanAccEmail = VipMembershipCardGenerator.extractCleanEmail(user.email ?: accountEmail) ?: accountEmail.trim()
                            val savedPwd = FirebaseAuthManager.getSavedPasswordForEmail(context, cleanAccEmail)

                            // Jika pengguna belum memiliki kata sandi tersimpan, tawarkan pembuatan kata sandi akun
                            // agar bisa login langsung dengan email & kata sandi
                            if (savedPwd.isBlank()) {
                                pendingGoogleUser = user
                                pendingGoogleUserName = user.displayName ?: displayName
                                pendingGoogleUserEmail = cleanAccEmail
                                googleAccountPasswordInput = ""
                                googleAccountPasswordConfirm = ""
                                googleAccountPasswordError = null
                                showSetPasswordAfterGoogleDialog = true
                            } else {
                                viewModel.showAuthSuccessPopup(
                                    isRegister = (currentMode == AuthMode.REGISTER),
                                    userName = user.displayName ?: displayName,
                                    userEmail = cleanAccEmail,
                                    photoUrl = account.photoUrl?.toString() ?: user.photoUrl?.toString()
                                )
                                onDismissRequest()
                            }
                        }.onFailure { err ->
                            val msg = err.message ?: ""
                            if (msg.startsWith("LINK_REQUIRED_GOOGLE:")) {
                                val parts = msg.split(":")
                                val targetEmail = if (parts.size >= 2) parts[1] else accountEmail
                                pendingLinkEmail = targetEmail
                                pendingGoogleAccount = account
                                linkPasswordInput = ""
                                linkErrorMessage = null
                                showLinkGoogleDialog = true
                                errorMessage = null
                            } else {
                                errorMessage = err.message ?: "Gagal masuk dengan akun Google."
                            }
                        }
                    }
                } else {
                    errorMessage = "Akun Google tidak dapat dibaca. Silakan coba kembali."
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501 && e.statusCode != 16) {
                    val detail = when (e.statusCode) {
                        10 -> "Kode 10 (DEVELOPER_ERROR): Pastikan SHA-1 fingerprint sudah terdaftar di Firebase Console (Project Settings > Your apps) dan Authentication > Google provider telah diaktifkan."
                        12500 -> "Kode 12500 (SIGN_IN_FAILED): Akun Google Play Services pada perangkat memerlukan pembaruan atau verifikasi."
                        else -> "Kode ${e.statusCode}: ${e.localizedMessage ?: "Silakan periksa koneksi."}"
                    }
                    errorMessage = "Gagal memproses akun Google: $detail"
                }
            } catch (e: Exception) {
                errorMessage = "Terjadi kesalahan saat masuk dengan Google: ${e.localizedMessage ?: e.message}"
            }
        }
    }

    val launchGoogleSignIn: () -> Unit = {
        try {
            errorMessage = null
            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()

            val webClientIdResId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            val webClientId = if (webClientIdResId != 0) {
                context.getString(webClientIdResId)
            } else {
                "194078759561-1b27n44qm8r8r8j3bhj032d50faq4109.apps.googleusercontent.com"
            }
            if (webClientId.isNotBlank()) {
                gsoBuilder.requestIdToken(webClientId)
            }

            val gso = gsoBuilder.build()
            val client = GoogleSignIn.getClient(context, gso)
            client.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(client.signInIntent)
            }
        } catch (e: Exception) {
            errorMessage = "Gagal membuka jendela Google: ${e.localizedMessage ?: e.message}"
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon & Title
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when (currentMode) {
                                AuthMode.LOGIN -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                AuthMode.REGISTER -> Color(0xFF0F766E).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (currentMode) {
                            AuthMode.LOGIN -> Icons.Default.Login
                            AuthMode.REGISTER -> Icons.Default.PersonAdd
                            AuthMode.FORGOT_PASSWORD -> Icons.Default.LockReset
                            AuthMode.VERIFY_OTP -> Icons.Default.Security
                            AuthMode.RESET_NEW_PASSWORD -> Icons.Default.Key
                        },
                        contentDescription = null,
                        tint = when (currentMode) {
                            AuthMode.LOGIN -> MaterialTheme.colorScheme.primary
                            AuthMode.REGISTER -> Color(0xFF0F766E)
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (currentMode) {
                        AuthMode.LOGIN -> "Masuk ke Akun"
                        AuthMode.REGISTER -> "Pendaftaran Akun Baru"
                        AuthMode.FORGOT_PASSWORD -> "Lupa Kata Sandi"
                        AuthMode.VERIFY_OTP -> "Verifikasi Kode OTP"
                        AuthMode.RESET_NEW_PASSWORD -> "Buat Kata Sandi Baru"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = when (currentMode) {
                        AuthMode.LOGIN -> "Akses data cloud pribadi dan sinkronkan antar perangkat secara aman."
                        AuthMode.REGISTER -> "Buat akun terpisah untuk mencadangkan data tanpa tercampur dengan pengguna lain."
                        AuthMode.FORGOT_PASSWORD -> "Masukkan email akun Anda untuk menerima kode OTP verifikasi reset kata sandi."
                        AuthMode.VERIFY_OTP -> "Masukkan 6-digit kode OTP keamanan yang telah dikirimkan ke $otpTargetEmailOrPhone."
                        AuthMode.RESET_NEW_PASSWORD -> "Tentukan kata sandi baru untuk mengamankan akun Anda."
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Error / Success Banner
                if (!errorMessage.isNullOrEmpty()) {
                    val rawError = errorMessage!!
                    val isConfigError = rawError.contains("Firebase Console", ignoreCase = true) ||
                            rawError.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                            rawError.contains("CONFIGURATION_NOT_FOUNT", ignoreCase = true)
                    val isBlockedOrExpired = rawError.startsWith("AKUN_DINONAKTIFKAN:") ||
                            rawError.startsWith("MEMBERSHIP_HABIS:") ||
                            rawError.contains("dinonaktifkan", ignoreCase = true) ||
                            rawError.contains("membership", ignoreCase = true)

                    val displayMessage = rawError
                        .removePrefix("AKUN_DINONAKTIFKAN: ")
                        .removePrefix("MEMBERSHIP_HABIS: ")

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isBlockedOrExpired -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                isConfigError -> Color(0xFFFFFBEB)
                                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        border = when {
                            isBlockedOrExpired -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            isConfigError -> BorderStroke(1.dp, Color(0xFFF59E0B))
                            else -> null
                        }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isBlockedOrExpired -> Icons.Default.Shield
                                        isConfigError -> Icons.Default.Warning
                                        else -> Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = if (isConfigError) Color(0xFFD97706) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isBlockedOrExpired -> "Akses Akun Dinonaktifkan"
                                        isConfigError -> "Konfigurasi Firebase Diperlukan"
                                        else -> "Terjadi Kesalahan"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isConfigError) Color(0xFFB45309) else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayMessage,
                                color = if (isConfigError) Color(0xFF78350F) else MaterialTheme.colorScheme.error,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            if (isConfigError) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Panduan singkat aktivasi:\n1. Buka console.firebase.google.com\n2. Pilih proyek 'mobile-d08aa'\n3. Masuk menu Authentication > tab 'Sign-in method'\n4. Aktifkan 'Email/Password', 'Google', atau 'Facebook'",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                            if (isBlockedOrExpired) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.authManager.launchWhatsAppAdmin(
                                            context = context,
                                            userEmail = email.ifEmpty { "Akun Terdaftar" },
                                            userName = name.ifEmpty { "Pengguna" },
                                            uid = "-",
                                            reason = displayMessage
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Hubungi Admin via WhatsApp",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                if (!successMessage.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(successMessage!!, color = Color(0xFF047857), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CONTENT PER MODE
                when (currentMode) {
                    AuthMode.LOGIN -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = { Text("Alamat Email", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("Kata Sandi", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    successMessage = null
                                    showQuickLoginScanner = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Login Cepat",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Login Cepat",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            TextButton(
                                onClick = {
                                    errorMessage = null
                                    successMessage = null
                                    currentMode = AuthMode.FORGOT_PASSWORD
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Lupa Kata Sandi?", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Silakan masukkan email dan kata sandi Anda."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val cleanEmail = email.trim()
                                    val result = viewModel.authManager.loginWithEmail(cleanEmail, password, context)
                                    isLoading = false
                                    result.onSuccess { user ->
                                        FirebaseAuthManager.saveUserCredentials(context, cleanEmail, password)
                                        viewModel.refreshAuthUser()
                                        viewModel.showAuthSuccessPopup(
                                            isRegister = false,
                                            userName = user.displayName ?: "",
                                            userEmail = user.email ?: ""
                                        )
                                        onDismissRequest()
                                    }.onFailure { err ->
                                        val msg = err.message ?: ""
                                        if (msg.startsWith("GOOGLE_ACCOUNT_DETECTED:")) {
                                            googleRegisteredEmail = cleanEmail
                                            showGoogleAccountEmailNotice = true
                                        } else {
                                            scope.launch {
                                                val isGoogle = viewModel.authManager.checkIsGoogleAccount(cleanEmail, context)
                                                if (isGoogle) {
                                                    googleRegisteredEmail = cleanEmail
                                                    showGoogleAccountEmailNotice = true
                                                } else {
                                                    errorMessage = msg.ifBlank { "Gagal masuk." }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Masuk Sekarang", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Social Login Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text("  atau masuk dengan  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google & Facebook Login Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Google
                            OutlinedButton(
                                onClick = {
                                    launchGoogleSignIn()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFEA4335), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Facebook
                            OutlinedButton(
                                onClick = {
                                    handleFacebookSignIn(context, viewModel, scope) { loading, err, success ->
                                        isLoading = loading
                                        errorMessage = err
                                        if (success) {
                                            viewModel.refreshAuthUser()
                                            val u = viewModel.currentFirebaseUser
                                            viewModel.showAuthSuccessPopup(
                                                isRegister = false,
                                                userName = viewModel.authManager.extractDisplayName(u),
                                                userEmail = u?.email ?: "",
                                                photoUrl = viewModel.authManager.extractPhotoUrl(u)
                                            )
                                            onDismissRequest()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.Facebook, null, tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Facebook", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Belum punya akun? ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Daftar Akun Baru",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    errorMessage = null
                                    successMessage = null
                                    currentMode = AuthMode.REGISTER
                                }
                            )
                        }
                    }

                    AuthMode.REGISTER -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; errorMessage = null },
                            label = { Text("Nama Lengkap", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = { Text("Alamat Email", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("Kata Sandi (min. 6 karakter)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMessage = null },
                            label = { Text("Konfirmasi Kata Sandi", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Email dan kata sandi wajib diisi."
                                    return@Button
                                }
                                if (password.length < 6) {
                                    errorMessage = "Kata sandi minimal 6 karakter."
                                    return@Button
                                }
                                if (password != confirmPassword) {
                                    errorMessage = "Konfirmasi kata sandi tidak cocok."
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = viewModel.authManager.registerWithEmail(email, password, name)
                                    isLoading = false
                                    result.onSuccess { user ->
                                        FirebaseAuthManager.saveUserCredentials(context, email, password)
                                        viewModel.refreshAuthUser()
                                        viewModel.showAuthSuccessPopup(
                                            isRegister = true,
                                            userName = user.displayName ?: name,
                                            userEmail = user.email ?: email
                                        )
                                        onDismissRequest()
                                    }.onFailure { err ->
                                        val cleanEmail = email.trim()
                                        val rawErr = err.message ?: ""
                                        if (rawErr.contains("already in use", ignoreCase = true) || rawErr.contains("sudah terdaftar", ignoreCase = true)) {
                                            scope.launch {
                                                val isGoogle = viewModel.authManager.checkIsGoogleAccount(cleanEmail, context)
                                                if (isGoogle) {
                                                    googleRegisteredEmail = cleanEmail
                                                    showGoogleAccountEmailNotice = true
                                                } else {
                                                    errorMessage = rawErr
                                                }
                                            }
                                        } else {
                                            errorMessage = rawErr.ifBlank { "Gagal membuat akun." }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Daftar Akun Sekarang", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Social Register Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text("  atau daftar dengan  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google & Facebook Register Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Google
                            OutlinedButton(
                                onClick = {
                                    launchGoogleSignIn()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.AccountCircle, null, tint = Color(0xFFEA4335), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Facebook
                            OutlinedButton(
                                onClick = {
                                    handleFacebookSignIn(context, viewModel, scope) { loading, err, success ->
                                        isLoading = loading
                                        errorMessage = err
                                        if (success) {
                                            viewModel.refreshAuthUser()
                                            val u = viewModel.currentFirebaseUser
                                            viewModel.showAuthSuccessPopup(
                                                isRegister = true,
                                                userName = viewModel.authManager.extractDisplayName(u),
                                                userEmail = u?.email ?: "",
                                                photoUrl = viewModel.authManager.extractPhotoUrl(u)
                                            )
                                            onDismissRequest()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(Icons.Default.Facebook, null, tint = Color(0xFF1877F2), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Facebook", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sudah punya akun? ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "Masuk di Sini",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    errorMessage = null
                                    successMessage = null
                                    currentMode = AuthMode.LOGIN
                                }
                            )
                        }
                    }

                    AuthMode.FORGOT_PASSWORD -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockReset,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Masukkan alamat email akun Anda. Kami akan mengirimkan kode OTP keamanan dan tautan reset kata sandi langsung ke email Anda.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = { Text("Alamat Email Akun", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (email.isBlank()) {
                                    errorMessage = "Silakan masukkan alamat email akun Anda."
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = viewModel.authManager.sendPasswordResetOtpEmail(email)
                                    isLoading = false
                                    result.onSuccess { pair ->
                                        val code = pair.first
                                        otpTargetEmailOrPhone = email
                                        generatedOtp = code
                                        successMessage = "Kode OTP berhasil dikirim ke $email! Silakan periksa inbox/spam dan masukkan kode 6-digit di bawah."
                                        viewModel.showOtpSentSuccessPopup(
                                            email = email,
                                            otpCode = code,
                                            onAction = {
                                                currentMode = AuthMode.VERIFY_OTP
                                            }
                                        )
                                        currentMode = AuthMode.VERIFY_OTP
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Gagal mengirimkan kode OTP ke email."
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MailOutline, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kirim Kode OTP ke Email", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        TextButton(
                            onClick = {
                                errorMessage = null
                                successMessage = null
                                currentMode = AuthMode.LOGIN
                            }
                        ) {
                            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kembali ke Halaman Masuk", fontSize = 12.sp)
                        }
                    }

                    AuthMode.VERIFY_OTP -> {
                        Text(
                            text = "Target Akun: $otpTargetEmailOrPhone",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = {
                                if (it.length <= 6) {
                                    otpCode = it
                                    errorMessage = null
                                }
                            },
                            label = { Text("Kode OTP 6-Digit", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (otpCode.length != 6) {
                                    errorMessage = "Kode OTP harus terdiri dari 6 digit angka."
                                    return@Button
                                }

                                val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(otpTargetEmailOrPhone) ?: otpTargetEmailOrPhone.trim()
                                otpTargetEmailOrPhone = cleanEmail
                                val isValid = viewModel.authManager.verifyOtp(cleanEmail, otpCode)
                                if (isValid) {
                                    if (pendingGoogleAccount != null) {
                                        isLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val linkResult = viewModel.authManager.linkGoogleAccountWithOtp(
                                                email = otpTargetEmailOrPhone,
                                                otpCode = otpCode,
                                                account = pendingGoogleAccount!!,
                                                context = context
                                            )
                                            isLoading = false
                                            linkResult.onSuccess { user ->
                                                viewModel.refreshAuthUser()
                                                viewModel.showAuthSuccessPopup(
                                                    isRegister = false,
                                                    userName = user.displayName ?: pendingGoogleAccount?.displayName ?: "Pengguna",
                                                    userEmail = user.email ?: otpTargetEmailOrPhone,
                                                    photoUrl = pendingGoogleAccount?.photoUrl?.toString() ?: user.photoUrl?.toString()
                                                )
                                                pendingGoogleAccount = null
                                                onDismissRequest()
                                            }.onFailure { err ->
                                                currentMode = AuthMode.RESET_NEW_PASSWORD
                                                errorMessage = "OTP terverifikasi. Silakan masukkan kata sandi baru untuk menyelesaikan penautan akun Google Anda."
                                            }
                                        }
                                    } else {
                                        currentMode = AuthMode.RESET_NEW_PASSWORD
                                        errorMessage = null
                                        successMessage = "Kode OTP berhasil diverifikasi! Silakan tentukan kata sandi baru Anda."
                                    }
                                } else {
                                    errorMessage = "Kode OTP salah atau telah kadaluarsa (berlaku 10 menit). Silakan minta kode baru."
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "Verifikasi Kode OTP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                errorMessage = null
                                successMessage = null
                                isLoading = true
                                scope.launch {
                                    val res = viewModel.authManager.sendPasswordResetOtpEmail(otpTargetEmailOrPhone)
                                    isLoading = false
                                    res.onSuccess { pair ->
                                        viewModel.showOtpSentSuccessPopup(otpTargetEmailOrPhone, pair.first)
                                        successMessage = "Kode OTP baru telah dikirim ke $otpTargetEmailOrPhone."
                                    }.onFailure {
                                        val code = viewModel.authManager.requestOtpForReset(otpTargetEmailOrPhone)
                                        viewModel.showOtpSentSuccessPopup(otpTargetEmailOrPhone, code)
                                        successMessage = "Kode OTP baru telah dibuat untuk $otpTargetEmailOrPhone."
                                    }
                                }
                            }
                        ) {
                            Text("Kirim Ulang Kode OTP", fontSize = 12.sp)
                        }
                    }

                    AuthMode.RESET_NEW_PASSWORD -> {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; errorMessage = null },
                            label = { Text("Kata Sandi Baru (min. 6 karakter)", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; errorMessage = null },
                            label = { Text("Konfirmasi Kata Sandi Baru", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (newPassword.length < 6) {
                                    errorMessage = "Kata sandi baru minimal 6 karakter."
                                    return@Button
                                }
                                if (newPassword != confirmPassword) {
                                    errorMessage = "Konfirmasi kata sandi tidak cocok."
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(otpTargetEmailOrPhone) ?: otpTargetEmailOrPhone.trim()
                                    otpTargetEmailOrPhone = cleanEmail
                                    FirebaseAuthManager.saveUserCredentials(context, cleanEmail, newPassword)
                                    if (pendingGoogleAccount != null) {
                                        val linkRes = viewModel.authManager.linkGoogleAccountWithPassword(
                                            email = cleanEmail,
                                            password = newPassword,
                                            account = pendingGoogleAccount!!,
                                            context = context
                                        )
                                        isLoading = false
                                        linkRes.onSuccess { user ->
                                            viewModel.refreshAuthUser()
                                            viewModel.showAuthSuccessPopup(
                                                isRegister = false,
                                                userName = user.displayName ?: pendingGoogleAccount?.displayName ?: "Pengguna",
                                                userEmail = user.email ?: cleanEmail,
                                                photoUrl = pendingGoogleAccount?.photoUrl?.toString() ?: user.photoUrl?.toString()
                                            )
                                            pendingGoogleAccount = null
                                            onDismissRequest()
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Gagal menautkan akun Google dengan kata sandi baru."
                                        }
                                    } else {
                                        val result = viewModel.authManager.confirmPasswordReset(cleanEmail, newPassword, context)
                                        isLoading = false
                                        result.onSuccess { msg ->
                                            viewModel.showToastPopup(msg)
                                            currentMode = AuthMode.LOGIN
                                            successMessage = "Kata sandi berhasil diatur ulang! Silakan masuk dengan kata sandi baru Anda."
                                        }.onFailure { err ->
                                            errorMessage = err.message ?: "Gagal memperbarui kata sandi."
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Simpan Kata Sandi Baru", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Close Button
                OutlinedButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Tutup", fontSize = 12.sp)
                }
            }
        }
    }

    if (showQuickLoginScanner) {
        BarcodeScannerDialog(
            onDismissRequest = { showQuickLoginScanner = false },
            title = "Scan Kartu - Login Cepat",
            hintText = "Arahkan kamera ke QR Code di bagian depan kartu membership VIP Anda",
            isQrSquare = true,
            onBarcodeScanned = { scannedRaw ->
                showQuickLoginScanner = false
                isLoading = true
                errorMessage = null
                scope.launch {
                    val quickLoginResult = viewModel.authManager.quickLoginWithCard(scannedRaw, context)
                    isLoading = false
                    quickLoginResult.onSuccess { user ->
                        viewModel.refreshAuthUser()
                        val displayName = user.displayName ?: "Member VIP"
                        val userEmail = user.email ?: ""
                        viewModel.showAuthSuccessPopup(
                            isRegister = false,
                            userName = displayName,
                            userEmail = userEmail
                        )
                        viewModel.showToastPopup("Login Cepat Berhasil! Selamat datang, $displayName")
                        onDismissRequest()
                    }.onFailure { err ->
                        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(scannedRaw)
                        if (!cleanEmail.isNullOrBlank()) {
                            // Alihkan ke mode LOGIN dengan email terisi agar pengguna dapat langsung memasukkan kata sandinya tanpa OTP
                            currentMode = AuthMode.LOGIN
                            email = cleanEmail
                            password = ""
                            errorMessage = err.message ?: "Silakan masukkan kata sandi akun Anda untuk menyelesaikan masuk."
                        } else {
                            errorMessage = err.message ?: "QR Code tidak dikenali sebagai kartu membership untuk Login Cepat."
                        }
                    }
                }
            }
        )
    }

    if (showLinkGoogleDialog && pendingGoogleAccount != null) {
        Dialog(
            onDismissRequest = {
                if (!linkIsLoading) {
                    showLinkGoogleDialog = false
                    pendingGoogleAccount = null
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Hubungkan Akun Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Email $pendingLinkEmail sebelumnya telah terdaftar dengan kata sandi. Hubungkan dengan Akun Google agar Anda dapat login dengan Google maupun email & kata sandi secara bebas.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!linkErrorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = linkErrorMessage ?: "",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opsi 1: Masukkan Kata Sandi
                    Text(
                        text = "Opsi 1: Masukkan Kata Sandi Akun",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = linkPasswordInput,
                        onValueChange = { linkPasswordInput = it; linkErrorMessage = null },
                        label = { Text("Kata Sandi Akun", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { linkPasswordVisible = !linkPasswordVisible }) {
                                Icon(
                                    imageVector = if (linkPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (linkPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (linkPasswordInput.isBlank()) {
                                linkErrorMessage = "Silakan masukkan kata sandi akun Anda."
                                return@Button
                            }
                            linkIsLoading = true
                            linkErrorMessage = null
                            scope.launch {
                                val result = viewModel.authManager.linkGoogleAccountWithPassword(
                                    email = pendingLinkEmail,
                                    password = linkPasswordInput,
                                    account = pendingGoogleAccount!!,
                                    context = context
                                )
                                linkIsLoading = false
                                result.onSuccess { user ->
                                    viewModel.refreshAuthUser()
                                    viewModel.showAuthSuccessPopup(
                                        isRegister = false,
                                        userName = user.displayName ?: pendingGoogleAccount?.displayName ?: "",
                                        userEmail = user.email ?: pendingLinkEmail,
                                        photoUrl = pendingGoogleAccount?.photoUrl?.toString() ?: user.photoUrl?.toString()
                                    )
                                    showLinkGoogleDialog = false
                                    pendingGoogleAccount = null
                                    onDismissRequest()
                                }.onFailure { err ->
                                    linkErrorMessage = err.message ?: "Kata sandi salah. Silakan coba lagi atau gunakan verifikasi OTP."
                                }
                            }
                        },
                        enabled = !linkIsLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (linkIsLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Hubungkan dengan Kata Sandi", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text("  atau  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opsi 2: Verifikasi OTP Email
                    OutlinedButton(
                        onClick = {
                            linkIsLoading = true
                            linkErrorMessage = null
                            scope.launch {
                                val sendResult = viewModel.authManager.sendPasswordResetOtpEmail(pendingLinkEmail)
                                linkIsLoading = false
                                showLinkGoogleDialog = false
                                otpTargetEmailOrPhone = pendingLinkEmail
                                currentMode = AuthMode.VERIFY_OTP
                                sendResult.onSuccess { pair ->
                                    viewModel.showOtpSentSuccessPopup(pendingLinkEmail, pair.first)
                                    successMessage = "Kode OTP verifikasi telah dikirim ke $pendingLinkEmail. Masukkan kode untuk menautkan akun Google Anda."
                                }.onFailure {
                                    val code = viewModel.authManager.requestOtpForReset(pendingLinkEmail)
                                    viewModel.showOtpSentSuccessPopup(pendingLinkEmail, code)
                                    successMessage = "Kode OTP telah dibuat. Masukkan kode untuk verifikasi."
                                }
                            }
                        },
                        enabled = !linkIsLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.Mail, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kirim Kode OTP ke Email", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = {
                            showLinkGoogleDialog = false
                            pendingGoogleAccount = null
                        }
                    ) {
                        Text("Batal", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showGoogleAccountEmailNotice) {
        Dialog(
            onDismissRequest = { showGoogleAccountEmailNotice = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFE8F0FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4285F4)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Akun Terdaftar via Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Email $googleRegisteredEmail terdaftar menggunakan Akun Google.\n\nAnda dapat langsung masuk dengan Google, atau buat kata sandi akun ini via kode OTP email agar bisa login dengan email & kata sandi kapan saja.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            showGoogleAccountEmailNotice = false
                            launchGoogleSignIn()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Masuk Langsung dengan Google", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            showGoogleAccountEmailNotice = false
                            otpTargetEmailOrPhone = googleRegisteredEmail
                            isLoading = true
                            scope.launch {
                                val sendResult = viewModel.authManager.sendPasswordResetOtpEmail(googleRegisteredEmail)
                                isLoading = false
                                currentMode = AuthMode.VERIFY_OTP
                                sendResult.onSuccess { pair ->
                                    viewModel.showOtpSentSuccessPopup(googleRegisteredEmail, pair.first)
                                    successMessage = "Kode OTP telah dikirimkan ke $googleRegisteredEmail. Masukkan kode untuk membuat kata sandi Anda."
                                }.onFailure {
                                    val code = viewModel.authManager.requestOtpForReset(googleRegisteredEmail)
                                    viewModel.showOtpSentSuccessPopup(googleRegisteredEmail, code)
                                    successMessage = "Kode OTP telah dibuat untuk $googleRegisteredEmail."
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buat Kata Sandi Akun via OTP", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { showGoogleAccountEmailNotice = false }) {
                        Text("Tutup", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showSetPasswordAfterGoogleDialog && pendingGoogleUser != null) {
        Dialog(
            onDismissRequest = {
                showSetPasswordAfterGoogleDialog = false
                viewModel.showAuthSuccessPopup(
                    isRegister = (currentMode == AuthMode.REGISTER),
                    userName = pendingGoogleUserName,
                    userEmail = pendingGoogleUserEmail
                )
                pendingGoogleUser = null
                onDismissRequest()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFF0F766E).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Buat Kata Sandi Akun",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Halo, $pendingGoogleUserName!\nAkun Google Anda ($pendingGoogleUserEmail) berhasil masuk.\n\nBuat kata sandi akun sekarang agar Anda juga bisa masuk langsung menggunakan Email & Kata Sandi kapan saja di perangkat apa pun:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!googleAccountPasswordError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = googleAccountPasswordError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Field Kata Sandi Baru
                    OutlinedTextField(
                        value = googleAccountPasswordInput,
                        onValueChange = { googleAccountPasswordInput = it; googleAccountPasswordError = null },
                        label = { Text("Kata Sandi Baru (min. 6 karakter)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { googleAccountPasswordVisible = !googleAccountPasswordVisible }) {
                                Icon(
                                    imageVector = if (googleAccountPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (googleAccountPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Field Konfirmasi Kata Sandi
                    OutlinedTextField(
                        value = googleAccountPasswordConfirm,
                        onValueChange = { googleAccountPasswordConfirm = it; googleAccountPasswordError = null },
                        label = { Text("Konfirmasi Kata Sandi", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { googleAccountPasswordConfirmVisible = !googleAccountPasswordConfirmVisible }) {
                                Icon(
                                    imageVector = if (googleAccountPasswordConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (googleAccountPasswordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol Simpan Kata Sandi & Lanjutkan
                    Button(
                        onClick = {
                            if (googleAccountPasswordInput.length < 6) {
                                googleAccountPasswordError = "Kata sandi minimal harus 6 karakter."
                                return@Button
                            }
                            if (googleAccountPasswordInput != googleAccountPasswordConfirm) {
                                googleAccountPasswordError = "Konfirmasi kata sandi tidak cocok."
                                return@Button
                            }

                            googleAccountPasswordLoading = true
                            googleAccountPasswordError = null
                            scope.launch {
                                val setRes = viewModel.authManager.setUserPasswordForGoogleAccount(
                                    user = pendingGoogleUser!!,
                                    newPassword = googleAccountPasswordInput,
                                    context = context
                                )
                                googleAccountPasswordLoading = false
                                setRes.onSuccess { msg ->
                                    viewModel.showToastPopup("Kata sandi tersimpan! Anda sekarang bisa masuk dengan email & kata sandi.")
                                    viewModel.refreshAuthUser()
                                    viewModel.showAuthSuccessPopup(
                                        isRegister = (currentMode == AuthMode.REGISTER),
                                        userName = pendingGoogleUserName,
                                        userEmail = pendingGoogleUserEmail
                                    )
                                    showSetPasswordAfterGoogleDialog = false
                                    pendingGoogleUser = null
                                    onDismissRequest()
                                }.onFailure { err ->
                                    googleAccountPasswordError = err.message ?: "Gagal menyimpan kata sandi."
                                }
                            }
                        },
                        enabled = !googleAccountPasswordLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        if (googleAccountPasswordLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Simpan Kata Sandi & Selesai", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tombol Lewati
                    TextButton(
                        onClick = {
                            showSetPasswordAfterGoogleDialog = false
                            viewModel.showAuthSuccessPopup(
                                isRegister = (currentMode == AuthMode.REGISTER),
                                userName = pendingGoogleUserName,
                                userEmail = pendingGoogleUserEmail
                            )
                            pendingGoogleUser = null
                            onDismissRequest()
                        }
                    ) {
                        Text("Lewati (Nanti Saja)", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun handleFacebookSignIn(
    context: android.content.Context,
    viewModel: MainViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (loading: Boolean, error: String?, success: Boolean) -> Unit
) {
    try {
        val provider = com.google.firebase.auth.OAuthProvider.newBuilder("facebook.com")
        if (context is android.app.Activity) {
            onResult(true, null, false)
            com.google.firebase.auth.FirebaseAuth.getInstance()
                .startActivityForSignInWithProvider(context, provider.build())
                .addOnSuccessListener { authResult ->
                    onResult(false, null, true)
                }
                .addOnFailureListener { e ->
                    onResult(false, e.localizedMessage ?: "Gagal masuk Facebook", false)
                }
        } else {
            onResult(false, "Konteks aktivitas tidak valid untuk Facebook Sign-In", false)
        }
    } catch (e: Exception) {
        onResult(false, "Facebook Sign-In: ${e.localizedMessage ?: e.message}", false)
    }
}
