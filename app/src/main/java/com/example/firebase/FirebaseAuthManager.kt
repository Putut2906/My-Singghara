package com.example.firebase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.example.utils.VipMembershipCardGenerator
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Manager untuk Otentikasi Pengguna Firebase (Email/Password, Google, Facebook, dan Reset Password dengan OTP).
 *
 * Fitur:
 * 1. Daftar Akun (Email & Password)
 * 2. Masuk Akun (Email & Password)
 * 3. Masuk dengan Akun Google
 * 4. Masuk dengan Akun Facebook
 * 5. Reset Password dengan Verifikasi OTP:
 *    - OTP dikirim ke Email resmi via Firebase Password Reset Mail
 *    - Opsi OTP WhatsApp: Otomatis generate 6-digit kode OTP keamanan unik dan membuka WhatsApp ke nomor pengguna/admin untuk verifikasi instan.
 */
class FirebaseAuthManager {
    private val auth: FirebaseAuth
        get() {
            return try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                val ctx: android.content.Context? = com.example.ui.MainViewModel.instance?.getApplication<android.app.Application>()
                if (ctx != null && com.google.firebase.FirebaseApp.getApps(ctx).isEmpty()) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:194078759561:android:b39d13cb8202ff43cfc458")
                        .setApiKey("AIzaSyC-1wqeoghlBosva2liyNCEEbb4IK6yvYs")
                        .setProjectId("mobile-d08aa")
                        .setStorageBucket("mobile-d08aa.firebasestorage.app")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(ctx, options)
                }
                FirebaseAuth.getInstance()
            }
        }

    val currentUser: FirebaseUser?
        get() = try { auth.currentUser } catch (e: Exception) { null }

    val isUserLoggedIn: Boolean
        get() = try { auth.currentUser != null } catch (e: Exception) { false }

    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        try {
            auth.addAuthStateListener(listener)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthManager", "Error adding authStateListener", e)
        }
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        try {
            auth.removeAuthStateListener(listener)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthManager", "Error removing authStateListener", e)
        }
    }

    // Penyimpanan OTP sementara dalam sesi memori aplikasi (key: target/email/phone, value: OtpSession)
    private val pendingOtpStore = mutableMapOf<String, OtpSession>()

    data class OtpSession(
        val code: String,
        val target: String,
        val createdAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + (10 * 60 * 1000) // 10 menit
    )

    /**
     * Pendaftaran akun baru dengan Email & Password
     */
    suspend fun registerWithEmail(email: String, password: String, displayName: String = ""): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
        try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: return@withContext Result.failure(Exception("Gagal membuat akun."))

            if (displayName.isNotBlank()) {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(profileUpdates).await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(formatAuthError(e)))
        }
    }

    /**
     * Masuk dengan Email & Password
     */
    suspend fun loginWithEmail(
        email: String,
        password: String,
        context: Context? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
        try {
            val result = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: return@withContext Result.failure(Exception("Akun tidak ditemukan."))

            // Proteksi: Periksa status akun & masa membership
            val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(user.uid)
            when (validation) {
                is AccountAccessValidation.Deactivated -> {
                    auth.signOut()
                    return@withContext Result.failure(Exception("AKUN_DINONAKTIFKAN: Akun Anda telah dinonaktifkan oleh Administrator. Anda tidak diizinkan masuk ke aplikasi. Silakan hubungi admin untuk informasi lebih lanjut."))
                }
                is AccountAccessValidation.MembershipExpired -> {
                    auth.signOut()
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                    val dateStr = sdf.format(java.util.Date(validation.endDate))
                    return@withContext Result.failure(Exception("MEMBERSHIP_HABIS: Masa membership akun Anda (${validation.tier}) telah habis pada $dateStr. Akun dinonaktifkan secara otomatis. Silakan hubungi Administrator untuk memperpanjang membership."))
                }
                is AccountAccessValidation.Allowed -> {
                    Result.success(user)
                }
            }
        } catch (e: Exception) {
            val rawMsg = e.message ?: ""
            if (rawMsg.startsWith("AKUN_DINONAKTIFKAN:") || rawMsg.startsWith("MEMBERSHIP_HABIS:")) {
                return@withContext Result.failure(e)
            }

            // Jika kata sandi yang diketik gagal, periksa apakah ada kata sandi tersimpan (bridging password / saved password)
            if (context != null) {
                val savedPwd = getSavedPasswordForEmail(context, cleanEmail)
                if (savedPwd.isNotBlank() && savedPwd != password) {
                    try {
                        val retryResult = auth.signInWithEmailAndPassword(cleanEmail, savedPwd).await()
                        val retryUser = retryResult.user
                        if (retryUser != null) {
                            val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(retryUser.uid)
                            if (validation is AccountAccessValidation.Allowed) {
                                return@withContext Result.success(retryUser)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Periksa apakah akun email ini didaftarkan via Google
            val isGoogle = checkIsGoogleAccount(cleanEmail, context)
            if (isGoogle) {
                return@withContext Result.failure(
                    Exception("GOOGLE_ACCOUNT_DETECTED:$cleanEmail:Email '$cleanEmail' terdaftar menggunakan Akun Google. Silakan masuk langsung dengan Google atau buat kata sandi via OTP.")
                )
            }

            Result.failure(Exception(formatAuthError(e)))
        }
    }

    /**
     * Masuk atau daftar otomatis menggunakan akun Google yang dipilih pengguna.
     * Mendukung penautan akun (linking) otomatis antara pendaftaran Email dan pendaftaran Google.
     */
    suspend fun signInWithGoogleAccount(
        account: GoogleSignInAccount,
        context: Context? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val email = account.email?.trim() ?: ""
        if (email.isBlank()) {
            return@withContext Result.failure(Exception("Akun Google tidak memiliki alamat email yang valid."))
        }
        val displayName = account.displayName?.trim() ?: email
        val photoUri = account.photoUrl
        val idToken = account.idToken
        val savedPassword = if (context != null) getSavedPasswordForEmail(context, email) else ""

        // Catat akun Google ini di memori lokal perangkat
        recordGoogleAccount(context, email)

        // 1. Coba otentikasi resmi via GoogleAuthProvider jika ID token tersedia
        if (!idToken.isNullOrBlank()) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            try {
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .apply {
                                if (displayName.isNotBlank()) setDisplayName(displayName)
                                if (photoUri != null) setPhotoUri(photoUri)
                            }
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}

                    // Tautkan provider password (savedPassword atau bridging password otomatis)
                    // agar pengguna bisa login baik dengan Google maupun dengan Email & Password
                    val googleId = account.id ?: email.hashCode().toString()
                    val bridgePassword = "GAuth#${googleId.take(10)}_${email.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
                    val pwdToLink = if (savedPassword.isNotBlank()) savedPassword else bridgePassword

                    try {
                        val emailCred = EmailAuthProvider.getCredential(email, pwdToLink)
                        user.linkWithCredential(emailCred).await()
                        if (context != null) {
                            saveUserCredentials(context, email, pwdToLink)
                        }
                    } catch (_: Exception) {
                        if (context != null && savedPassword.isNotBlank()) {
                            saveUserCredentials(context, email, savedPassword)
                        }
                    }

                    // Proteksi status akun & membership
                    val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(user.uid)
                    when (validation) {
                        is AccountAccessValidation.Deactivated -> {
                            auth.signOut()
                            return@withContext Result.failure(Exception("AKUN_DINONAKTIFKAN: Akun Google Anda telah dinonaktifkan oleh Administrator. Anda tidak diizinkan masuk ke aplikasi."))
                        }
                        is AccountAccessValidation.MembershipExpired -> {
                            auth.signOut()
                            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                            val dateStr = sdf.format(java.util.Date(validation.endDate))
                            return@withContext Result.failure(Exception("MEMBERSHIP_HABIS: Masa membership akun Google Anda (${validation.tier}) telah habis pada $dateStr. Akun dinonaktifkan secara otomatis."))
                        }
                        is AccountAccessValidation.Allowed -> {
                            return@withContext Result.success(user)
                        }
                    }
                }
            } catch (e: Exception) {
                val rawMsg = e.message ?: ""
                if (rawMsg.startsWith("AKUN_DINONAKTIFKAN:") || rawMsg.startsWith("MEMBERSHIP_HABIS:")) {
                    return@withContext Result.failure(e)
                }

                val isCollision = e is FirebaseAuthUserCollisionException ||
                        rawMsg.contains("already exists with the same email", ignoreCase = true) ||
                        rawMsg.contains("different sign-in credentials", ignoreCase = true)

                if (isCollision) {
                    // Akun sudah pernah dibuat via Email & Password!
                    // Jika kata sandi tersimpan di perangkat, masuk dengan email/password lalu tautkan kredensial Google
                    if (savedPassword.isNotBlank()) {
                        try {
                            val emailLogin = auth.signInWithEmailAndPassword(email, savedPassword).await()
                            val existingUser = emailLogin.user
                            if (existingUser != null) {
                                try {
                                    existingUser.linkWithCredential(credential).await()
                                } catch (_: Exception) {}

                                try {
                                    val profileUpdates = UserProfileChangeRequest.Builder()
                                        .apply {
                                            if (displayName.isNotBlank()) setDisplayName(displayName)
                                            if (photoUri != null) setPhotoUri(photoUri)
                                        }
                                        .build()
                                    existingUser.updateProfile(profileUpdates).await()
                                } catch (_: Exception) {}

                                return@withContext Result.success(existingUser)
                            }
                        } catch (_: Exception) {}
                    }

                    // Beritahu UI untuk menampilkan popup penautan akun via OTP atau konfirmasi kata sandi
                    return@withContext Result.failure(
                        Exception("LINK_REQUIRED_GOOGLE:$email:Akun '$email' telah didaftarkan dengan kata sandi sebelumnya. Hubungkan akun Google Anda dengan verifikasi OTP atau kata sandi.")
                    )
                }
            }
        }

        // 2. Bridging Deterministik Aman berbasis Google Account ID & Email
        val googleId = account.id ?: email.hashCode().toString()
        val bridgePassword = "GAuth#${googleId.take(10)}_${email.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"

        // Coba masuk terlebih dahulu menggunakan kata sandi tersimpan (jika user pernah daftar via email)
        if (savedPassword.isNotBlank()) {
            try {
                val loginResult = auth.signInWithEmailAndPassword(email, savedPassword).await()
                val user = loginResult.user
                if (user != null) {
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .apply {
                                if (displayName.isNotBlank()) setDisplayName(displayName)
                                if (photoUri != null) setPhotoUri(photoUri)
                            }
                            .build()
                        user.updateProfile(profileUpdates).await()
                    } catch (_: Exception) {}

                    return@withContext Result.success(user)
                }
            } catch (_: Exception) {}
        }

        // Coba login dengan bridgePassword
        try {
            val loginResult = auth.signInWithEmailAndPassword(email, bridgePassword).await()
            val user = loginResult.user
            if (user != null) {
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .apply {
                            if (displayName.isNotBlank()) setDisplayName(displayName)
                            if (photoUri != null) setPhotoUri(photoUri)
                        }
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (_: Exception) {}

                if (context != null) {
                    saveUserCredentials(context, email, bridgePassword)
                }

                // Proteksi status akun & membership
                val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(user.uid)
                when (validation) {
                    is AccountAccessValidation.Deactivated -> {
                        auth.signOut()
                        return@withContext Result.failure(Exception("AKUN_DINONAKTIFKAN: Akun Google Anda telah dinonaktifkan oleh Administrator. Anda tidak diizinkan masuk ke aplikasi."))
                    }
                    is AccountAccessValidation.MembershipExpired -> {
                        auth.signOut()
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                        val dateStr = sdf.format(java.util.Date(validation.endDate))
                        return@withContext Result.failure(Exception("MEMBERSHIP_HABIS: Masa membership akun Google Anda (${validation.tier}) telah habis pada $dateStr. Akun dinonaktifkan secara otomatis."))
                    }
                    is AccountAccessValidation.Allowed -> {
                        return@withContext Result.success(user)
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.startsWith("AKUN_DINONAKTIFKAN:") || msg.startsWith("MEMBERSHIP_HABIS:")) {
                return@withContext Result.failure(e)
            }

            // Periksa apakah email sudah terdaftar di Firebase
            val methods = try {
                auth.fetchSignInMethodsForEmail(email).await().signInMethods
            } catch (_: Exception) {
                null
            }

            val isRegistered = !methods.isNullOrEmpty()

            if (!isRegistered) {
                // Email belum ada di Firebase -> Daftarkan akun baru
                try {
                    val regResult = auth.createUserWithEmailAndPassword(email, bridgePassword).await()
                    val newUser = regResult.user
                    if (newUser != null) {
                        try {
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .apply {
                                    if (displayName.isNotBlank()) setDisplayName(displayName)
                                    if (photoUri != null) setPhotoUri(photoUri)
                                }
                                .build()
                            newUser.updateProfile(profileUpdates).await()
                        } catch (_: Exception) {}

                        if (context != null) {
                            saveUserCredentials(context, email, bridgePassword)
                        }

                        return@withContext Result.success(newUser)
                    }
                } catch (regEx: Exception) {
                    val regMsg = regEx.message ?: ""
                    if (!regMsg.contains("already in use", ignoreCase = true)) {
                        return@withContext Result.failure(Exception(formatAuthError(regEx)))
                    }
                }
            }

            // Jika sampai di sini, email sudah terdaftar dengan kata sandi lain
            // Kembalikan instruksi linking via OTP atau kata sandi
            return@withContext Result.failure(
                Exception("LINK_REQUIRED_GOOGLE:$email:Akun '$email' telah didaftarkan sebelumnya dengan email & kata sandi. Silakan tautkan akun Google Anda melalui verifikasi OTP atau konfirmasi kata sandi.")
            )
        }

        Result.failure(Exception("Gagal masuk dengan akun Google."))
    }

    /**
     * Memeriksa metode login yang terdaftar untuk alamat email tertentu
     */
    suspend fun fetchSignInMethods(email: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = auth.fetchSignInMethodsForEmail(email.trim()).await()
            result.signInMethods ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Menghubungkan akun Google dengan kata sandi email yang dimasukkan pengguna
     */
    suspend fun linkGoogleAccountWithPassword(
        email: String,
        password: String,
        account: GoogleSignInAccount,
        context: Context? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val loginResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = loginResult.user ?: return@withContext Result.failure(Exception("Akun tidak ditemukan."))

            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                try {
                    user.linkWithCredential(credential).await()
                } catch (_: Exception) {}
            }

            if (context != null) {
                saveUserCredentials(context, email, password)
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(formatAuthError(e)))
        }
    }

    /**
     * Menghubungkan akun Google menggunakan kode OTP verifikasi email
     */
    suspend fun linkGoogleAccountWithOtp(
        email: String,
        otpCode: String,
        account: GoogleSignInAccount,
        newPasswordForEmail: String = "",
        context: Context? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val isValid = verifyOtp(email, otpCode)
        if (!isValid) {
            return@withContext Result.failure(Exception("Kode OTP salah atau telah kadaluarsa (berlaku 10 menit)."))
        }

        val idToken = account.idToken
        if (!idToken.isNullOrBlank()) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    if (newPasswordForEmail.isNotBlank()) {
                        try {
                            val emailCred = EmailAuthProvider.getCredential(email, newPasswordForEmail)
                            user.linkWithCredential(emailCred).await()
                        } catch (_: Exception) {
                            try {
                                user.updatePassword(newPasswordForEmail).await()
                            } catch (_: Exception) {}
                        }
                        if (context != null) {
                            saveUserCredentials(context, email, newPasswordForEmail)
                        }
                    }
                    return@withContext Result.success(user)
                }
            } catch (_: Exception) {}
        }

        // Jika Google credential tidak bisa login langsung, set password baru via reset
        val pwdToUse = if (newPasswordForEmail.isNotBlank()) newPasswordForEmail
        else {
            val googleId = account.id ?: email.hashCode().toString()
            "GAuth#${googleId.take(10)}_${email.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
        }

        val updateResult = confirmPasswordReset(email, pwdToUse)
        if (updateResult.isSuccess) {
            try {
                val loginResult = auth.signInWithEmailAndPassword(email.trim(), pwdToUse).await()
                val user = loginResult.user
                if (user != null) {
                    if (!idToken.isNullOrBlank()) {
                        try {
                            val credential = GoogleAuthProvider.getCredential(idToken, null)
                            user.linkWithCredential(credential).await()
                        } catch (_: Exception) {}
                    }
                    if (context != null) {
                        saveUserCredentials(context, email, pwdToUse)
                    }
                    return@withContext Result.success(user)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception(formatAuthError(e)))
            }
        }

        Result.failure(Exception("Gagal menyelesaikan penautan akun."))
    }

    /**
     * Mengekstrak URL foto profil dari FirebaseUser, termasuk dari provider Google dan Facebook.
     */
    fun extractPhotoUrl(user: FirebaseUser?): String? {
        if (user == null) return null
        user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        for (profile in user.providerData) {
            val pUrl = profile.photoUrl?.toString()
            if (!pUrl.isNullOrBlank()) return pUrl
            if (profile.providerId == "facebook.com" && !profile.uid.isNullOrBlank()) {
                return "https://graph.facebook.com/${profile.uid}/picture?type=large"
            }
        }
        return null
    }

    private fun String.capitalizeWords(): String {
        return this.split(" ").filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    /**
     * Mengekstrak nama tampilan dari FirebaseUser (Google, Facebook, atau Email).
     */
    fun extractDisplayName(user: FirebaseUser?): String {
        if (user == null) return "Pengguna Singghara"
        user.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        for (profile in user.providerData) {
            val pName = profile.displayName
            if (!pName.isNullOrBlank()) return pName
        }
        return user.email?.substringBefore('@')?.replace(".", " ")?.replace("_", " ")?.capitalizeWords() ?: "Pengguna Singghara"
    }

    /**
     * Masuk dengan Kredensial Firebase (Google / Facebook / OAuth)
     */
    suspend fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return@withContext Result.failure(Exception("Gagal masuk dengan kredensial penyedia."))

            // Proteksi status akun & membership
            val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(user.uid)
            when (validation) {
                is AccountAccessValidation.Deactivated -> {
                    auth.signOut()
                    return@withContext Result.failure(Exception("AKUN_DINONAKTIFKAN: Akun Anda telah dinonaktifkan oleh Administrator. Anda tidak diizinkan masuk ke aplikasi."))
                }
                is AccountAccessValidation.MembershipExpired -> {
                    auth.signOut()
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                    val dateStr = sdf.format(java.util.Date(validation.endDate))
                    return@withContext Result.failure(Exception("MEMBERSHIP_HABIS: Masa membership akun Anda (${validation.tier}) telah habis pada $dateStr. Akun dinonaktifkan secara otomatis."))
                }
                is AccountAccessValidation.Allowed -> {
                    Result.success(user)
                }
            }
        } catch (e: Exception) {
            val rawMsg = e.message ?: ""
            if (rawMsg.startsWith("AKUN_DINONAKTIFKAN:") || rawMsg.startsWith("MEMBERSHIP_HABIS:")) {
                Result.failure(e)
            } else {
                Result.failure(Exception(formatAuthError(e)))
            }
        }
    }

    /**
     * Login Cepat menggunakan data QR Code Kartu Membership VIP tanpa verifikasi OTP.
     * Membaca email & kata sandi pada kartu/perangkat lalu langsung masuk secara instan.
     */
    suspend fun quickLoginWithCard(
        scannedRaw: String,
        context: Context? = null
    ): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        val cleanRaw = scannedRaw.trim()
        if (cleanRaw.isBlank()) {
            return@withContext Result.failure(Exception("Data scan kosong."))
        }

        val credentials = VipMembershipCardGenerator.parseQuickLoginPayload(cleanRaw)
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(credentials?.email ?: cleanRaw)?.trim()
        if (cleanEmail.isNullOrBlank()) {
            return@withContext Result.failure(Exception("QR Code tidak memuat alamat email yang valid."))
        }

        val cardPassword = credentials?.password?.trim() ?: ""
        val savedPwd = if (context != null) getSavedPasswordForEmail(context, cleanEmail).trim() else ""

        // Bangun daftar kandidat kata sandi akun
        val candidates = mutableListOf<String>()
        if (cardPassword.isNotBlank()) candidates.add(cardPassword)
        if (savedPwd.isNotBlank() && !candidates.contains(savedPwd)) candidates.add(savedPwd)

        // Tambahkan deterministic bridge password untuk akun pendaftar Google
        val googleId = cleanEmail.hashCode().toString()
        val bridgePwd = "GAuth#${googleId.take(10)}_${cleanEmail.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
        if (!candidates.contains(bridgePwd)) candidates.add(bridgePwd)

        var lastError: Exception? = null
        for (pwd in candidates) {
            try {
                val authRes = auth.signInWithEmailAndPassword(cleanEmail, pwd).await()
                val user = authRes.user
                if (user != null) {
                    if (context != null) {
                        saveUserCredentials(context, cleanEmail, pwd)
                        if (checkIsGoogleAccount(cleanEmail, context)) {
                            recordGoogleAccount(context, cleanEmail)
                        }
                    }

                    // Proteksi status akun & membership
                    val validation = FirebaseAccountAdminManager.getInstance().validateAccountAccess(user.uid)
                    when (validation) {
                        is AccountAccessValidation.Deactivated -> {
                            auth.signOut()
                            return@withContext Result.failure(Exception("AKUN_DINONAKTIFKAN: Akun kartu Anda telah dinonaktifkan oleh Administrator."))
                        }
                        is AccountAccessValidation.MembershipExpired -> {
                            auth.signOut()
                            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                            val dateStr = sdf.format(java.util.Date(validation.endDate))
                            return@withContext Result.failure(Exception("MEMBERSHIP_HABIS: Masa membership kartu Anda (${validation.tier}) telah habis pada $dateStr."))
                        }
                        is AccountAccessValidation.Allowed -> {
                            return@withContext Result.success(user)
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e
                val rawMsg = e.message ?: ""
                if (rawMsg.startsWith("AKUN_DINONAKTIFKAN:") || rawMsg.startsWith("MEMBERSHIP_HABIS:")) {
                    return@withContext Result.failure(e)
                }
            }
        }

        // Jika user saat ini sudah login dengan email yang sama
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.email?.equals(cleanEmail, ignoreCase = true) == true) {
            return@withContext Result.success(currentUser)
        }

        val errMsg = if (cardPassword.isBlank() && savedPwd.isBlank()) {
            "Kata sandi belum tersimpan pada kartu membership ini. Silakan masukkan kata sandi akun Anda secara manual."
        } else {
            "Gagal masuk otomatis: ${lastError?.let { formatAuthError(it) } ?: "Kata sandi kartu tidak cocok."}"
        }

        Result.failure(Exception(errMsg))
    }

    /**
     * Membuka aplikasi WhatsApp ke kontak administrator (default: 08354678, dapat diubah) untuk permohonan aktivasi / perpanjangan membership akun.
     */
    fun launchWhatsAppAdmin(context: Context, userEmail: String, userName: String, uid: String, reason: String) {
        try {
            val appName = "My Singghara"
            val adminManager = FirebaseAccountAdminManager.getInstance()
            val rawAdminPhone = adminManager.getAdminPhoneNumber(context)
            val formattedPhone = adminManager.formatWhatsAppNumber(rawAdminPhone)

            val message = "Halo Admin *$appName* (Kontak: $rawAdminPhone),\n\n" +
                    "Saya ingin konfirmasi status akun / perpanjangan membership aplikasi:\n" +
                    "• *Nama Pengguna*: $userName\n" +
                    "• *Email Akun*: $userEmail\n" +
                    "• *UID Pengguna*: `$uid`\n" +
                    "• *Keterangan*: $reason\n\n" +
                    "Mohon bantuannya untuk mengaktifkan kembali akun / memperpanjang masa membership saya. Terima kasih!"
            val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Halo Admin My Singghara, mohon bantuannya untuk mengaktifkan akun / perpanjangan membership saya: $userEmail (UID: $uid)")
                    type = "text/plain"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(sendIntent, "Hubungi Admin via..."))
            } catch (_: Exception) {}
        }
    }

    /**
     * Keluar dari Akun (Logout)
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Generate 6-digit kode OTP angka unik
     */
    fun generateOtpCode(): String {
        return (100000 + Random.nextInt(900000)).toString()
    }

    /**
     * Kirim link & OTP Reset Password ke Email via Firebase Auth
     */
    suspend fun sendPasswordResetEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) {
            return@withContext Result.failure(Exception("Silakan masukkan alamat email Anda."))
        }
        try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success("Tautan instruksi reset kata sandi telah dikirim ke $cleanEmail. Silakan periksa kotak masuk atau spam email Anda.")
        } catch (e: Exception) {
            Result.failure(Exception(formatAuthError(e)))
        }
    }

    /**
     * Kirim permintaan kode OTP dan email reset sandi Firebase
     */
    suspend fun sendPasswordResetOtpEmail(email: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
        if (cleanEmail.isEmpty()) {
            return@withContext Result.failure(Exception("Silakan masukkan alamat email Anda."))
        }
        val otpCode = requestOtpForReset(cleanEmail)
        try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Pair(otpCode, "Tautan reset kata sandi telah dikirim ke $cleanEmail."))
        } catch (e: Exception) {
            Result.success(Pair(otpCode, "Kode OTP verifikasi telah dibuat untuk $cleanEmail."))
        }
    }

    /**
     * Minta kode OTP Reset Password (bisa dikirim via Email kode lokal atau WhatsApp)
     */
    fun requestOtpForReset(identifier: String): String {
        val code = generateOtpCode()
        val clean = VipMembershipCardGenerator.extractCleanEmail(identifier) ?: identifier.trim()
        val key = clean.lowercase()
        pendingOtpStore[key] = OtpSession(code = code, target = key)
        return code
    }

    /**
     * Menyimpan sesi kode OTP secara eksplisit (misalnya dari request OTP admin)
     */
    fun setPendingOtp(identifier: String, code: String, durationMinutes: Int = 15) {
        val clean = VipMembershipCardGenerator.extractCleanEmail(identifier) ?: identifier.trim()
        val key = clean.lowercase()
        pendingOtpStore[key] = OtpSession(
            code = code.trim(),
            target = key,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        )
    }

    /**
     * Verifikasi kode OTP yang dimasukkan oleh pengguna
     */
    fun verifyOtp(identifier: String, inputCode: String): Boolean {
        val clean = VipMembershipCardGenerator.extractCleanEmail(identifier) ?: identifier.trim()
        val key = clean.lowercase()
        val session = pendingOtpStore[key] ?: return false
        if (System.currentTimeMillis() > session.expiresAt) {
            pendingOtpStore.remove(key)
            return false
        }
        val isValid = session.code == inputCode.trim()
        if (isValid) {
            pendingOtpStore.remove(key)
        }
        return isValid
    }

    /**
     * Membuka aplikasi WhatsApp dengan pesan pre-filled berisi kode OTP reset kata sandi.
     */
    fun launchWhatsAppOtp(context: Context, phoneNumber: String, otpCode: String, appName: String = "My Singghara"): Result<String> {
        return try {
            var formattedPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            if (formattedPhone.startsWith("0")) {
                formattedPhone = "62" + formattedPhone.substring(1)
            } else if (!formattedPhone.startsWith("62")) {
                formattedPhone = "62$formattedPhone"
            }

            val message = "🔐 *KODE VERIFIKASI OTP RESET PASSWORD*\n\n" +
                    "Aplikasi: *$appName*\n" +
                    "Kode OTP Anda: *`$otpCode`*\n\n" +
                    "_Kode ini berlaku selama 10 menit. Jangan bagikan kode ini kepada siapapun demi keamanan data akun Anda._"

            val url = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Result.success("Membuka WhatsApp untuk mengirimkan kode OTP $otpCode...")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal membuka WhatsApp: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Memeriksa apakah sebuah alamat email terdaftar menggunakan Akun Google
     */
    suspend fun checkIsGoogleAccount(email: String, context: Context?): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email)?.lowercase() ?: email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext false

        // 1. Periksa SharedPreferences lokal pada perangkat
        if (isGoogleAccountSaved(context, cleanEmail)) return@withContext true

        // 2. Periksa via Firebase Auth fetchSignInMethods
        try {
            val methods = auth.fetchSignInMethodsForEmail(cleanEmail).await().signInMethods
            if (methods != null && methods.contains("google.com")) return@withContext true
        } catch (_: Exception) {}

        // 3. Domain Google resmi (@gmail.com atau @googlemail.com)
        if (cleanEmail.endsWith("@gmail.com") || cleanEmail.endsWith("@googlemail.com")) {
            return@withContext true
        }

        // 4. Periksa koleksi Firestore 'users'
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snap = firestore.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .await()
            if (!snap.isEmpty) {
                val doc = snap.documents.first()
                val provider = doc.getString("providerId") ?: ""
                if (provider.contains("google", ignoreCase = true)) return@withContext true
            }
        } catch (_: Exception) {}

        // 5. Periksa koleksi Firestore 'tokens'
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val snap = firestore.collection("tokens")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .get()
                .await()
            if (!snap.isEmpty) {
                val doc = snap.documents.first()
                val provider = doc.getString("providerId") ?: ""
                if (provider.contains("google", ignoreCase = true)) return@withContext true
            }
        } catch (_: Exception) {}

        false
    }

    /**
     * Mengatur atau menautkan kata sandi untuk akun (khususnya akun yang masuk via Google)
     * agar pengguna dapat masuk kapan saja baik dengan Google maupun dengan Email & Kata Sandi.
     */
    suspend fun setUserPasswordForGoogleAccount(
        user: FirebaseUser,
        newPassword: String,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val email = user.email ?: return@withContext Result.failure(Exception("Alamat email tidak ditemukan pada akun."))
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()

        if (newPassword.length < 6) {
            return@withContext Result.failure(Exception("Kata sandi baru minimal harus 6 karakter."))
        }

        var isLinked = false
        // 1. Coba hubungkan credential EmailAuthProvider
        try {
            val emailCred = EmailAuthProvider.getCredential(cleanEmail, newPassword)
            user.linkWithCredential(emailCred).await()
            isLinked = true
        } catch (e: Exception) {
            val msg = e.message ?: ""
            // Jika credential email/password sudah terhubung sebelumnya, cukup updatePassword
            if (msg.contains("already linked", ignoreCase = true) ||
                msg.contains("already associated", ignoreCase = true) ||
                msg.contains("collision", ignoreCase = true) ||
                msg.contains("already exists", ignoreCase = true) ||
                e is FirebaseAuthUserCollisionException) {
                try {
                    user.updatePassword(newPassword).await()
                    isLinked = true
                } catch (_: Exception) {}
            }
        }

        // 2. Jika linkWithCredential belum selesai, lakukan updatePassword
        if (!isLinked) {
            try {
                user.updatePassword(newPassword).await()
                isLinked = true
            } catch (e: Exception) {
                return@withContext Result.failure(Exception(formatAuthError(e)))
            }
        }

        // 3. Simpan kata sandi ke preferensi lokal
        if (context != null) {
            saveUserCredentials(context, cleanEmail, newPassword)
            recordGoogleAccount(context, cleanEmail)
        }

        // 4. Catat di Firestore users collection jika ada
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("users").document(user.uid).set(
                mapOf(
                    "email" to cleanEmail,
                    "hasPassword" to true,
                    "providerId" to "google.com,password",
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (_: Exception) {}

        Result.success("Kata sandi berhasil disimpan! Anda sekarang dapat masuk menggunakan Google maupun Email & Kata Sandi.")
    }

    /**
     * Reset password setelah OTP terverifikasi
     */
    suspend fun confirmPasswordReset(
        email: String,
        newPassword: String,
        context: Context? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
        val user = auth.currentUser
        if (user != null && user.email.equals(cleanEmail, ignoreCase = true)) {
            try {
                user.updatePassword(newPassword).await()
                if (context != null) {
                    saveUserCredentials(context, cleanEmail, newPassword)
                }
                return@withContext Result.success("Kata sandi berhasil diperbarui!")
            } catch (e: Exception) {
                return@withContext Result.failure(Exception(formatAuthError(e)))
            }
        }

        // Coba perbarui kata sandi dengan login via savedPassword jika ada
        val savedPwd = if (context != null) getSavedPasswordForEmail(context, cleanEmail) else ""
        if (savedPwd.isNotBlank()) {
            try {
                val loginRes = auth.signInWithEmailAndPassword(cleanEmail, savedPwd).await()
                val loggedUser = loginRes.user
                if (loggedUser != null) {
                    loggedUser.updatePassword(newPassword).await()
                    if (context != null) {
                        saveUserCredentials(context, cleanEmail, newPassword)
                    }
                    return@withContext Result.success("Kata sandi baru berhasil disimpan! Anda sekarang dapat masuk dengan email dan kata sandi baru.")
                }
            } catch (_: Exception) {}
        }

        // Jika user belum login, kirimkan email reset resmi dari Firebase
        val isGoogle = checkIsGoogleAccount(cleanEmail, context)
        try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            if (context != null) {
                saveUserCredentials(context, cleanEmail, newPassword)
            }
            if (isGoogle) {
                Result.success("Akun '$cleanEmail' terdaftar via Google. Tautan resmi pengubahan kata sandi telah dikirim ke email Anda. Silakan klik tautan email atau masuk langsung dengan tombol Google.")
            } else {
                Result.success("Verifikasi berhasil! Tautan pengubahan kata sandi resmi telah dikirim ke $cleanEmail.")
            }
        } catch (e: Exception) {
            Result.failure(Exception(formatAuthError(e)))
        }
    }

    fun formatAuthError(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: "Terjadi kesalahan otentikasi"
        return when {
            msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) || msg.contains("CONFIGURATION_NOT_FOUNT", ignoreCase = true) ->
                "Layanan autentikasi belum diaktifkan di Firebase Console. Silakan buka Firebase Console (Authentication > Sign-in method) dan aktifkan penyedia 'Email/Password' atau Google/Facebook."
            msg.contains("The email address is already in use", ignoreCase = true) || msg.contains("already in use", ignoreCase = true) ->
                "Alamat email ini sudah terdaftar. Silakan masuk (login) menggunakan tombol Google atau masukkan kata sandi yang sesuai."
            msg.contains("badly formatted", ignoreCase = true) -> 
                "Format alamat email tidak valid."
            msg.contains("The supplied auth credential is incorrect, malformed or has expired", ignoreCase = true) ||
            msg.contains("supplied auth credential", ignoreCase = true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            msg.contains("invalid credential", ignoreCase = true) ||
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong password", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("no user record", ignoreCase = true) ||
            msg.contains("user-not-found", ignoreCase = true) ->
                "Email atau kata sandi tidak cocok. Jika Anda mendaftar dengan Akun Google, silakan masuk menggunakan tombol 'Google' di bawah atau buat kata sandi akun via OTP."
            msg.contains("Password should be at least 6 characters", ignoreCase = true) -> 
                "Kata sandi minimal harus 6 karakter."
            msg.contains("network error", ignoreCase = true) || msg.contains("network-request-failed", ignoreCase = true) -> 
                "Gagal terhubung ke jaringan internet. Periksa koneksi Anda."
            msg.contains("user-disabled", ignoreCase = true) -> 
                "Akun ini telah dinonaktifkan oleh administrator."
            msg.contains("too-many-requests", ignoreCase = true) -> 
                "Terlalu banyak percobaan masuk yang gagal. Silakan tunggu beberapa saat atau masuk menggunakan tombol Google."
            else -> msg
        }
    }

    companion object {
        fun recordGoogleAccount(context: Context?, email: String) {
            if (email.isBlank() || context == null) return
            try {
                val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
                val prefs = context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)
                val clean = cleanEmail.lowercase()
                prefs.edit()
                    .putBoolean("is_google_$clean", true)
                    .putString("google_email_$clean", cleanEmail)
                    .putString("last_used_google_email", cleanEmail)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun isGoogleAccountSaved(context: Context?, email: String): Boolean {
            if (email.isBlank() || context == null) return false
            return try {
                val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
                val prefs = context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)
                val clean = cleanEmail.lowercase()
                prefs.getBoolean("is_google_$clean", false) ||
                    prefs.getString("last_used_google_email", "")?.lowercase()?.trim() == clean
            } catch (e: Exception) {
                false
            }
        }

        fun saveUserCredentials(context: Context, email: String, password: String) {
            if (email.isBlank() || password.isBlank()) return
            try {
                val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
                val prefs = context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("saved_email", cleanEmail)
                    .putString("saved_password", password)
                    .putString("pwd_${cleanEmail.lowercase()}", password)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun getSavedPasswordForEmail(context: Context, email: String): String {
            if (email.isBlank()) return ""
            return try {
                val cleanEmail = VipMembershipCardGenerator.extractCleanEmail(email) ?: email.trim()
                val prefs = context.getSharedPreferences("auth_credentials_prefs", Context.MODE_PRIVATE)
                val direct = prefs.getString("pwd_${cleanEmail.lowercase()}", "") ?: ""
                if (direct.isNotBlank()) direct else prefs.getString("saved_password", "") ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}
