package com.example.ui

import android.app.Application
import com.example.ui.models.AppRegistry
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.HolidayClient
import com.example.network.AladhanClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.text.SimpleDateFormat
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.LinearGradient
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.example.NotificationScheduler

enum class AuthFeedbackType {
    CONFIRM_LOGOUT,
    SUCCESS_LOGIN,
    SUCCESS_REGISTER,
    SUCCESS_SEND_OTP_EMAIL,
    SUCCESS_LOGOUT
}

data class AuthFeedbackState(
    val type: AuthFeedbackType,
    val userName: String = "",
    val userEmail: String = "",
    val otpCode: String = "",
    val onConfirmLogout: (() -> Unit)? = null,
    val onActionClick: (() -> Unit)? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        @Volatile
        var instance: MainViewModel? = null
    }

    private val db = AppDatabase.getDatabase(application)
    val repository = LocalRepository(db)
    val arisanRepository = com.example.data.arisan.ArisanRepository(application)
    private val sharedPrefs = application.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)

    // State Berbagi Bukti Transaksi E-Wallet / Bank (Android Share Sheet: GoPay, DANA, OVO, dll.)
    var sharedReceiptUri by mutableStateOf<Uri?>(null)
    var sharedReceiptText by mutableStateOf<String?>(null)
    var showSharedReceiptDialog by mutableStateOf(false)

    // Firebase Auth & Cloud Backup Managers
    val authManager = com.example.firebase.FirebaseAuthManager()
    val cloudBackupManager = com.example.firebase.FirebaseCloudBackupManager(repository)
    val adminManager = com.example.firebase.FirebaseAccountAdminManager.getInstance()

    var currentFirebaseUser by mutableStateOf(authManager.currentUser)
    var isCloudBackupLoading by mutableStateOf(false)
    var cloudBackupMetadata by mutableStateOf<com.example.firebase.CloudBackupInfo?>(null)

    // Status Blokir Akses Akun & Proteksi Membership
    var isAccountBlocked by mutableStateOf(sharedPrefs.getBoolean("cached_account_blocked", false))
    var accountBlockReason by mutableStateOf(sharedPrefs.getString("cached_account_block_reason", "") ?: "")
    var accountMembershipEndDate by mutableStateOf(sharedPrefs.getLong("cached_membership_end_date", 0L))
    var accountMembershipTier by mutableStateOf(sharedPrefs.getString("cached_membership_tier", "") ?: "")
    var isCheckingAccountStatus by mutableStateOf(false)

    // Nomor Telepon Kontak Admin (Default: 088806643415, dapat diganti oleh Admin)
    var adminPhoneNumber by mutableStateOf(sharedPrefs.getString("admin_phone_number", "088806643415") ?: "088806643415")
        private set

    val defaultAdminPhoneNumber: String
        get() = adminPhoneNumber

    fun saveDefaultAdminPhoneNumber(newNumber: String) {
        updateAdminPhoneNumber(newNumber)
    }

    fun updateAdminPhoneNumber(newNumber: String) {
        val trimmed = newNumber.trim()
        val finalNum = if (trimmed.isNotEmpty()) trimmed else "088806643415"
        adminPhoneNumber = finalNum
        sharedPrefs.edit().putString("admin_phone_number", finalNum).apply()
        showToastPopup("Nomor Admin berhasil diperbarui: $finalNum")
    }

    // State Dialog Detail Membership VIP
    var selectedMembershipUser by mutableStateOf<com.example.firebase.CloudUserItem?>(null)
    var showMembershipDetailDialog by mutableStateOf(false)

    fun openMembershipDetail(user: com.example.firebase.CloudUserItem? = null) {
        selectedMembershipUser = user
        showMembershipDetailDialog = true
    }

    fun closeMembershipDetail() {
        showMembershipDetailDialog = false
        selectedMembershipUser = null
    }

    // Helper Akses Layar Akun Terblokir
    val accountBlockedIsExpired: Boolean
        get() = accountMembershipEndDate > 0L && System.currentTimeMillis() >= accountMembershipEndDate
    val accountBlockedPlan: String
        get() = accountMembershipTier.ifEmpty { "Paket Standar" }
    val accountBlockedReason: String
        get() = accountBlockReason.ifEmpty { if (accountBlockedIsExpired) "Masa membership Anda telah habis." else "Akun dinonaktifkan oleh administrator." }

    fun verifyCurrentAccountAccess(onComplete: ((Boolean) -> Unit)? = null) {
        checkAccountAccessStatus(onComplete)
    }

    fun directLogout() {
        authManager.logout()
        currentFirebaseUser = null
        isAccountBlocked = false
        sharedPrefs.edit().putBoolean("cached_account_blocked", false).apply()
    }

    // Status Jaringan Online
    var isNetworkAvailable by mutableStateOf(true)
        private set

    fun refreshNetworkStatus() {
        isNetworkAvailable = checkInitialNetworkState()
    }

    /**
     * Memeriksa status akses akun pengguna secara berkala / saat aplikasi dibuka.
     * Jika akun dinonaktifkan oleh Admin atau masa membership habis, akses aplikasi diblokir total.
     */
    fun checkAccountAccessStatus(onComplete: ((Boolean) -> Unit)? = null) {
        val user = currentFirebaseUser ?: authManager.currentUser
        if (user == null || adminManager.isAdmin(user.uid)) {
            isAccountBlocked = false
            sharedPrefs.edit().putBoolean("cached_account_blocked", false).apply()
            onComplete?.invoke(true)
            return
        }

        val uid = user.uid
        val now = System.currentTimeMillis()

        // 1. Cek batas membership lokal jika tersimpan
        if (accountMembershipEndDate > 0L && now >= accountMembershipEndDate) {
            isAccountBlocked = true
            accountBlockReason = "Masa membership akun Anda telah habis."
            sharedPrefs.edit()
                .putBoolean("cached_account_blocked", true)
                .putString("cached_account_block_reason", accountBlockReason)
                .apply()
        }

        // 2. Sinkronkan dengan Cloud Firestore
        viewModelScope.launch {
            isCheckingAccountStatus = true
            val validation = adminManager.validateAccountAccess(uid)
            isCheckingAccountStatus = false
            when (validation) {
                is com.example.firebase.AccountAccessValidation.Allowed -> {
                    isAccountBlocked = false
                    accountBlockReason = ""
                    sharedPrefs.edit()
                        .putBoolean("cached_account_blocked", false)
                        .putString("cached_account_block_reason", "")
                        .apply()
                    onComplete?.invoke(true)
                }
                is com.example.firebase.AccountAccessValidation.Deactivated -> {
                    isAccountBlocked = true
                    accountBlockReason = validation.reason
                    sharedPrefs.edit()
                        .putBoolean("cached_account_blocked", true)
                        .putString("cached_account_block_reason", validation.reason)
                        .apply()
                    onComplete?.invoke(false)
                }
                is com.example.firebase.AccountAccessValidation.MembershipExpired -> {
                    isAccountBlocked = true
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
                    val dateFormatted = sdf.format(java.util.Date(validation.endDate))
                    accountBlockReason = "Masa membership Anda (${validation.tier}) telah habis pada $dateFormatted. Akun dinonaktifkan secara otomatis."
                    accountMembershipEndDate = validation.endDate
                    accountMembershipTier = validation.tier
                    sharedPrefs.edit()
                        .putBoolean("cached_account_blocked", true)
                        .putString("cached_account_block_reason", accountBlockReason)
                        .putLong("cached_membership_end_date", validation.endDate)
                        .putString("cached_membership_tier", validation.tier)
                        .apply()
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun checkInitialNetworkState(): Boolean {
        return try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Menyimpan Token FCM secara otomatis ke koleksi Firestore 'tokens' & 'users'
     */
    fun syncFcmTokenToFirestore() {
        val user = currentFirebaseUser ?: authManager.currentUser ?: return
        val token = fcmToken.ifEmpty { sharedPrefs.getString("fcm_token", "") ?: "" }
        viewModelScope.launch {
            if (token.isNotEmpty()) {
                try {
                    adminManager.saveFcmTokenToFirestore(user, token, getApplication())
                    android.util.Log.d("MainViewModel", "Token FCM tersimpan ke Firestore koleksi tokens & users untuk UID: ${user.uid}")
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Gagal sync token ke Firestore: ${e.message}")
                }
            } else {
                try {
                    adminManager.recordUserAccess()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    // Pesan dari Administrator Resmi (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2)
    private val _adminMessages = MutableStateFlow<List<com.example.firebase.CloudMessageItem>>(emptyList())
    val adminMessages: StateFlow<List<com.example.firebase.CloudMessageItem>> = _adminMessages.asStateFlow()

    var unreadAdminMessagesCount by mutableStateOf(0)
        private set

    private var adminMessagesListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun setupAdminMessagesListener() {
        try {
            adminMessagesListener?.remove()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            adminMessagesListener = firestore.collection("messages")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("MainViewModel", "Error snapshot admin messages: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<com.example.firebase.CloudMessageItem>()
                        val currentUid = currentFirebaseUser?.uid ?: authManager.currentUser?.uid
                        for (doc in snapshot.documents) {
                            val id = doc.id
                            val title = doc.getString("title") ?: ""
                            val content = doc.getString("content") ?: ""
                            val category = doc.getString("category") ?: "PENGUMUMAN"
                            val priority = doc.getString("priority") ?: "NORMAL"
                            val target = doc.getString("target") ?: "SEMUA"
                            val targetName = doc.getString("targetName") ?: "Semua Pengguna"
                            val authorUid = doc.getString("authorUid") ?: ""
                            val authorName = doc.getString("authorName") ?: "Admin"
                            val createdAt = doc.getLong("createdAt") ?: 0L

                            // Tampilkan jika ditujukan untuk SEMUA, cocok dengan UID akun ini, atau jika akun adalah Admin
                            if (target == "SEMUA" || (currentUid != null && target == currentUid) || currentUid == com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID) {
                                list.add(
                                    com.example.firebase.CloudMessageItem(
                                        id = id,
                                        title = title,
                                        content = content,
                                        category = category,
                                        priority = priority,
                                        target = target,
                                        targetName = targetName,
                                        authorUid = authorUid,
                                        authorName = authorName,
                                        createdAt = createdAt
                                    )
                                )
                            }
                        }
                        _adminMessages.value = list
                        calculateUnreadAdminMessages()
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateUnreadAdminMessages() {
        val readIds = sharedPrefs.getStringSet("read_admin_msg_ids", emptySet()) ?: emptySet()
        val list = _adminMessages.value
        unreadAdminMessagesCount = list.count { it.id !in readIds }
    }

    fun isMessageRead(messageId: String): Boolean {
        val readIds = sharedPrefs.getStringSet("read_admin_msg_ids", emptySet()) ?: emptySet()
        return messageId in readIds
    }

    fun markAdminMessageAsRead(messageId: String) {
        val readIds = (sharedPrefs.getStringSet("read_admin_msg_ids", emptySet()) ?: emptySet()).toMutableSet()
        readIds.add(messageId)
        sharedPrefs.edit().putStringSet("read_admin_msg_ids", readIds).apply()
        calculateUnreadAdminMessages()
    }

    fun markAllAdminMessagesAsRead() {
        val allIds = _adminMessages.value.map { it.id }.toSet()
        sharedPrefs.edit().putStringSet("read_admin_msg_ids", allIds).apply()
        calculateUnreadAdminMessages()
    }

    fun deleteAdminMessagePermanently(messageId: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val adminManager = com.example.firebase.FirebaseAccountAdminManager.getInstance()
            val res = adminManager.deleteMessage(messageId)
            if (res.isSuccess) {
                val readIds = (sharedPrefs.getStringSet("read_admin_msg_ids", emptySet()) ?: emptySet()).toMutableSet()
                readIds.remove(messageId)
                sharedPrefs.edit().putStringSet("read_admin_msg_ids", readIds).apply()
                showToastPopup("Pesan berhasil dihapus permanen dari Firestore!")
                onComplete?.invoke(true, null)
            } else {
                val errorMsg = res.exceptionOrNull()?.message ?: "Gagal menghapus pesan"
                showToastPopup("Gagal menghapus pesan: $errorMsg")
                onComplete?.invoke(false, errorMsg)
            }
        }
    }

    // Permintaan Kode OTP Sandi Email ke Admin Resmi (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2)
    private val _otpRequests = MutableStateFlow<List<com.example.firebase.AdminOtpRequest>>(emptyList())
    val otpRequests: StateFlow<List<com.example.firebase.AdminOtpRequest>> = _otpRequests.asStateFlow()

    val activeOtpCount: Int
        get() = _otpRequests.value.count { it.status == "MENUNGGU" && !it.isExpired }

    private var otpRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun setupOtpRequestsListener() {
        try {
            otpRequestsListener?.remove()
            loadCachedOtpRequests()

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            otpRequestsListener = firestore.collection("otp_requests")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.w("MainViewModel", "Error snapshot OTP requests: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = mutableListOf<com.example.firebase.AdminOtpRequest>()
                        for (doc in snapshot.documents) {
                            try {
                                val id = doc.id
                                val requesterUid = doc.getString("requesterUid") ?: ""
                                val requesterEmail = doc.getString("requesterEmail") ?: ""
                                val requesterName = doc.getString("requesterName") ?: ""
                                val otpCode = doc.getString("otpCode") ?: ""
                                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                val expiresAt = doc.getLong("expiresAt") ?: (createdAt + 15 * 60 * 1000L)
                                val status = doc.getString("status") ?: "MENUNGGU"
                                val actionType = doc.getString("actionType") ?: "SET_PASSWORD_EMAIL"
                                val adminUid = doc.getString("adminUid") ?: com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID

                                list.add(
                                    com.example.firebase.AdminOtpRequest(
                                        id = id,
                                        requesterUid = requesterUid,
                                        requesterEmail = requesterEmail,
                                        requesterName = requesterName,
                                        otpCode = otpCode,
                                        createdAt = createdAt,
                                        expiresAt = expiresAt,
                                        status = status,
                                        actionType = actionType,
                                        adminUid = adminUid
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                        if (list.isNotEmpty()) {
                            _otpRequests.value = list
                            saveCachedOtpRequests(list)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCachedOtpRequests() {
        val sp = getApplication<android.app.Application>().getSharedPreferences("admin_otp_prefs", android.content.Context.MODE_PRIVATE)
        val jsonStr = sp.getString("otp_list_json", "[]") ?: "[]"
        val list = mutableListOf<com.example.firebase.AdminOtpRequest>()
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    com.example.firebase.AdminOtpRequest(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        requesterUid = obj.optString("requesterUid", ""),
                        requesterEmail = obj.optString("requesterEmail", ""),
                        requesterName = obj.optString("requesterName", ""),
                        otpCode = obj.optString("otpCode", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        expiresAt = obj.optLong("expiresAt", System.currentTimeMillis() + 15 * 60 * 1000L),
                        status = obj.optString("status", "MENUNGGU"),
                        actionType = obj.optString("actionType", "SET_PASSWORD_EMAIL"),
                        adminUid = obj.optString("adminUid", com.example.firebase.FirebaseAccountAdminManager.ADMIN_UID)
                    )
                )
            }
        } catch (_: Exception) {}
        if (list.isNotEmpty()) {
            _otpRequests.value = list.sortedByDescending { it.createdAt }
        }
    }

    private fun saveCachedOtpRequests(list: List<com.example.firebase.AdminOtpRequest>) {
        val sp = getApplication<android.app.Application>().getSharedPreferences("admin_otp_prefs", android.content.Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("requesterUid", item.requesterUid)
            obj.put("requesterEmail", item.requesterEmail)
            obj.put("requesterName", item.requesterName)
            obj.put("otpCode", item.otpCode)
            obj.put("createdAt", item.createdAt)
            obj.put("expiresAt", item.expiresAt)
            obj.put("status", item.status)
            obj.put("actionType", item.actionType)
            obj.put("adminUid", item.adminUid)
            arr.put(obj)
        }
        sp.edit().putString("otp_list_json", arr.toString()).apply()
    }

    fun requestEmailPasswordOtp(
        onComplete: (Boolean, com.example.firebase.AdminOtpRequest?, String?) -> Unit
    ) {
        val user = currentFirebaseUser ?: authManager.currentUser
        if (user == null) {
            onComplete(false, null, "Anda harus masuk ke akun terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            val email = user.email ?: ""
            val name = profileName.ifBlank { user.displayName ?: "Pengguna Singghara" }
            val res = adminManager.createEmailPasswordOtpRequest(
                requesterUid = user.uid,
                requesterEmail = email,
                requesterName = name,
                context = getApplication()
            )
            res.onSuccess { req ->
                loadCachedOtpRequests()
                onComplete(true, req, null)
            }.onFailure { err ->
                onComplete(false, null, err.message ?: "Gagal mengirim permintaan OTP ke Admin.")
            }
        }
    }

    fun verifyEmailPasswordOtp(
        code: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val user = currentFirebaseUser ?: authManager.currentUser
        if (user == null) {
            onComplete(false, "Sesi login tidak valid.")
            return
        }
        viewModelScope.launch {
            val res = adminManager.verifyEmailPasswordOtp(
                requesterUid = user.uid,
                enteredCode = code,
                context = getApplication()
            )
            res.onSuccess {
                loadCachedOtpRequests()
                onComplete(true, null)
            }.onFailure { err ->
                onComplete(false, err.message ?: "Kode OTP salah.")
            }
        }
    }

    fun deleteAdminOtpRequest(otpId: String) {
        viewModelScope.launch {
            adminManager.deleteOtpRequest(otpId, getApplication())
            loadCachedOtpRequests()
            showToastPopup("Permintaan kode OTP dihapus.")
        }
    }

    fun markAdminOtpUsed(otpId: String) {
        viewModelScope.launch {
            adminManager.markOtpAsUsed(otpId, getApplication())
            loadCachedOtpRequests()
            showToastPopup("Kode OTP ditandai selesai/digunakan.")
        }
    }

    fun refreshOtpRequests() {
        viewModelScope.launch {
            val res = adminManager.getAllOtpRequests(getApplication())
            if (res.isSuccess) {
                val list = res.getOrDefault(emptyList())
                _otpRequests.value = list
                saveCachedOtpRequests(list)
            } else {
                loadCachedOtpRequests()
            }
        }
    }

    // Animated Auth Feedback State (Pop up animasi bergerak untuk login, register, confirm logout)
    var authFeedbackState by mutableStateOf<AuthFeedbackState?>(null)

    fun showAuthSuccessPopup(isRegister: Boolean, userName: String, userEmail: String, photoUrl: String? = null) {
        val user = authManager.currentUser ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        currentFirebaseUser = user
        syncUserProfileFromFirebase(user, photoUrl)
        syncFcmTokenToFirestore()
        authFeedbackState = AuthFeedbackState(
            type = if (isRegister) AuthFeedbackType.SUCCESS_REGISTER else AuthFeedbackType.SUCCESS_LOGIN,
            userName = profileName.ifEmpty { userName },
            userEmail = userEmail
        )
    }

    fun showOtpSentSuccessPopup(email: String, otpCode: String = "", onAction: (() -> Unit)? = null) {
        authFeedbackState = AuthFeedbackState(
            type = AuthFeedbackType.SUCCESS_SEND_OTP_EMAIL,
            userEmail = email,
            otpCode = otpCode,
            onActionClick = onAction
        )
    }

    fun requestLogoutConfirmation(onConfirmed: (() -> Unit)? = null) {
        val user = currentFirebaseUser
        authFeedbackState = AuthFeedbackState(
            type = AuthFeedbackType.CONFIRM_LOGOUT,
            userName = profileName.ifEmpty { user?.displayName ?: "" },
            userEmail = user?.email ?: "",
            onConfirmLogout = {
                authManager.logout()
                syncUserProfileFromFirebase(null)
                currentFirebaseUser = null
                cloudBackupMetadata = null
                isAccountBlocked = false
                accountBlockReason = ""
                sharedPrefs.edit()
                    .putBoolean("cached_account_blocked", false)
                    .putString("cached_account_block_reason", "")
                    .apply()
                onConfirmed?.invoke()
                authFeedbackState = AuthFeedbackState(
                    type = AuthFeedbackType.SUCCESS_LOGOUT,
                    userName = "",
                    userEmail = ""
                )
            }
        )
    }

    fun dismissAuthFeedback() {
        authFeedbackState = null
    }

    /**
     * Menyinkronkan profil lokal aplikasi (nama, foto/inisial avatar) dengan akun Firebase (Google, Facebook, atau Email).
     * - Akun Google: Mengikuti nama & foto profil Google.
     * - Akun Facebook: Mengikuti nama & foto profil Facebook.
     * - Akun Email: Mengikuti nama pengguna pendaftaran, dan foto profil otomatis menggunakan avatar inisial nama.
     */
    fun syncUserProfileFromFirebase(user: com.google.firebase.auth.FirebaseUser?, overridePhotoUrl: String? = null) {
        if (user != null) {
            currentFirebaseUser = user
            val name = authManager.extractDisplayName(user)
            val photoUrl = overridePhotoUrl ?: authManager.extractPhotoUrl(user)

            // Simpan nama profil & UID akun
            profileName = name
            sharedPrefs.edit()
                .putString("profile_name", name)
                .putString("cached_user_uid", user.uid)
                .putString("user_email", user.email ?: "")
                .apply()

            if (!photoUrl.isNullOrBlank()) {
                // Ada foto profil dari Google / Facebook / Kustom
                profileImageUri = photoUrl
                profileAvatarIndex = -1
                sharedPrefs.edit()
                    .putString("profile_image_uri", photoUrl)
                    .putInt("profile_avatar_index", -1)
                    .apply()

                // Simpan cache foto profil secara offline agar Web Connect PC 100% offline dapat menampilkannya
                if (photoUrl.startsWith("http://") || photoUrl.startsWith("https://")) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val url = java.net.URL(photoUrl)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 8000
                            conn.readTimeout = 8000
                            conn.instanceFollowRedirects = true
                            if (conn.responseCode == 200) {
                                val bytes = conn.inputStream.use { it.readBytes() }
                                val cachedFile = java.io.File(getApplication<Application>().filesDir, "cached_network_avatar.jpg")
                                val customFile = java.io.File(getApplication<Application>().filesDir, "custom_profile_avatar.jpg")
                                cachedFile.writeBytes(bytes)
                                customFile.writeBytes(bytes)
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            } else {
                // Pengguna Email / tanpa foto: Gunakan avatar Inisial Nama (-2)
                profileImageUri = ""
                profileAvatarIndex = -2
                sharedPrefs.edit()
                    .putString("profile_image_uri", "")
                    .putInt("profile_avatar_index", -2)
                    .apply()
            }
        } else {
            // Pengguna logout / guest default
            val defaultName = "Pengguna Singghara"
            profileName = defaultName
            profileImageUri = ""
            profileAvatarIndex = 0
            sharedPrefs.edit()
                .putString("profile_name", defaultName)
                .putString("profile_image_uri", "")
                .putString("cached_user_uid", "")
                .putString("user_email", "")
                .putInt("profile_avatar_index", 0)
                .apply()
        }
    }

    fun refreshAuthUser() {
        currentFirebaseUser = authManager.currentUser
        syncUserProfileFromFirebase(currentFirebaseUser)
        syncFcmTokenToFirestore()
        setupAdminMessagesListener()
        setupOtpRequestsListener()
        checkCloudBackupMetadata()
        checkAccountAccessStatus()
    }

    fun checkCloudBackupMetadata() {
        if (!authManager.isUserLoggedIn) {
            cloudBackupMetadata = null
            return
        }
        viewModelScope.launch {
            val result = cloudBackupManager.getLatestBackupMetadata()
            result.onSuccess {
                cloudBackupMetadata = it
            }
        }
    }

    fun backupToFirebaseCloud(context: Context, onResult: (String, Boolean) -> Unit) {
        if (!authManager.isUserLoggedIn) {
            onResult("Silakan masuk (login) terlebih dahulu untuk mencadangkan ke Firebase Cloud.", false)
            return
        }
        isCloudBackupLoading = true
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val result = cloudBackupManager.backupToCloud(context)
                result.onSuccess { msg ->
                    checkCloudBackupMetadata()
                    onResult(msg, true)
                }.onFailure { err ->
                    onResult(err.message ?: "Gagal mencadangkan ke cloud.", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                isCloudBackupLoading = false
                preventAutoLock = false
            }
        }
    }

    fun restoreFromFirebaseCloud(context: Context, onResult: (String, Boolean) -> Unit) {
        if (!authManager.isUserLoggedIn) {
            onResult("Silakan masuk (login) terlebih dahulu untuk memulihkan data dari Firebase Cloud.", false)
            return
        }
        isCloudBackupLoading = true
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val result = cloudBackupManager.restoreFromCloud(context)
                result.onSuccess { msg ->
                    reloadFromSharedPrefs()
                    checkCloudBackupMetadata()
                    onResult(msg, true)
                }.onFailure { err ->
                    onResult(err.message ?: "Gagal memulihkan data dari cloud.", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                isCloudBackupLoading = false
                preventAutoLock = false
            }
        }
    }

    // Firebase Cloud Messaging (FCM) State
    val fcmNotifications: StateFlow<List<FcmNotification>> = repository.allFcmNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadFcmCount: StateFlow<Int> = repository.unreadFcmCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var fcmToken by mutableStateOf("")
    var isFcmConnected by mutableStateOf(false)
    var fcmTokenError by mutableStateOf<String?>(null)
    var isFetchingFcmToken by mutableStateOf(false)
    var fcmTokenSource by mutableStateOf("Firebase Cloud")
    var activeInAppMessage by mutableStateOf<FcmNotification?>(null)

    fun showInAppMessage(notification: FcmNotification) {
        viewModelScope.launch {
            activeInAppMessage = notification
        }
    }

    fun dismissInAppMessage() {
        val current = activeInAppMessage
        if (current != null) {
            viewModelScope.launch {
                repository.markFcmAsRead(current.id)
            }
        }
        activeInAppMessage = null
    }

    fun openInboxFromInAppMessage() {
        val current = activeInAppMessage
        if (current != null) {
            viewModelScope.launch {
                repository.markFcmAsRead(current.id)
            }
        }
        activeInAppMessage = null
        showFcmNotificationsDialog = true
    }

    private fun generateOrGetFallbackToken(): String {
        val existing = sharedPrefs.getString("fcm_device_registration_token", "") ?: ""
        if (existing.isNotEmpty()) return existing

        val context = getApplication<Application>()
        val androidId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) { "" }

        val uniqueSeed = (androidId.ifEmpty { "singghara_device" }) + "_" + context.packageName
        val hash = try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(uniqueSeed.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            UUID.randomUUID().toString().replace("-", "")
        }
        val generatedToken = "d_${hash.take(32)}:${UUID.randomUUID().toString().replace("-", "").take(22)}"
        sharedPrefs.edit().putString("fcm_device_registration_token", generatedToken).apply()
        return generatedToken
    }

    fun fetchFcmToken(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                isFetchingFcmToken = true
                fcmTokenError = null
                val context = getApplication<Application>()

                // 1. Muat token yang sudah ada agar UI tidak stuck
                val savedReal = sharedPrefs.getString("fcm_real_token", "") ?: ""
                val saved = sharedPrefs.getString("fcm_token", "") ?: ""
                if (savedReal.isNotEmpty()) {
                    fcmToken = savedReal
                    isFcmConnected = true
                    fcmTokenSource = "Google Play Services"
                } else if (saved.isNotEmpty()) {
                    fcmToken = saved
                    isFcmConnected = true
                    fcmTokenSource = "Pendaftaran Perangkat"
                } else {
                    fcmToken = generateOrGetFallbackToken()
                    fcmTokenSource = "Pendaftaran Perangkat"
                }

                // 2. Pastikan FirebaseApp terinisialisasi
                if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                    try {
                        com.google.firebase.FirebaseApp.initializeApp(context)
                    } catch (e: Throwable) {
                        android.util.Log.w("MainViewModel", "FirebaseApp init warning: ${e.message}")
                    }
                }

                // Periksa ketersediaan Google Play Services sebelum memanggil FCM SDK
                val gmsAvailability = try {
                    com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
                } catch (e: Throwable) {
                    com.google.android.gms.common.ConnectionResult.SERVICE_MISSING
                }

                if (gmsAvailability != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    isFetchingFcmToken = false
                    fcmTokenError = "Google Play Services tidak tersedia pada perangkat/emulator ini"
                    android.util.Log.i("MainViewModel", "GMS tidak aktif (code: $gmsAvailability), menggunakan token pendaftaran perangkat.")
                    return@launch
                }

                val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()

                // Jika force refresh, coba bersihkan token lama terlebih dahulu
                if (forceRefresh) {
                    try {
                        messaging.deleteToken()
                    } catch (e: Throwable) {
                        android.util.Log.w("MainViewModel", "FCM deleteToken warning: ${e.message}")
                    }
                }

                // 3. Dapatkan Token Pendaftaran Resmi dari Firebase SDK
                messaging.token
                    .addOnCompleteListener { task ->
                        isFetchingFcmToken = false
                        if (task.isSuccessful && !task.result.isNullOrEmpty()) {
                            val token = task.result
                            fcmToken = token
                            isFcmConnected = true
                            fcmTokenError = null
                            fcmTokenSource = "Google Play Services"
                            sharedPrefs.edit()
                                .putString("fcm_token", token)
                                .putString("fcm_real_token", token)
                                .apply()
                            android.util.Log.d("MainViewModel", "FCM Token berhasil diambil: $token")
                            syncFcmTokenToFirestore()
                            if (forceRefresh) {
                                showToastPopup("Token FCM berhasil diperbarui!")
                            }
                            // Berlangganan topik siaran "all" HANYA jika token pendaftaran berhasil diperoleh
                            try {
                                messaging.subscribeToTopic("all")
                            } catch (e: Throwable) {
                                android.util.Log.w("MainViewModel", "FCM subscribeToTopic note: ${e.message}")
                            }
                        } else {
                            val exc = task.exception
                            val rawMsg = exc?.localizedMessage ?: exc?.message ?: "Layanan pendaftaran Google Play tidak tersedia"
                            android.util.Log.w("MainViewModel", "FCM Token fetch note: $rawMsg")
                            fcmTokenError = if (rawMsg.contains("TOO_MANY_REGISTRATIONS", ignoreCase = true)) {
                                "Batas pendaftaran Google Play tercapai pada emulator/perangkat ini. Menggunakan identitas perangkat lokal."
                            } else {
                                rawMsg
                            }
                            if (fcmToken.isEmpty()) {
                                fcmToken = generateOrGetFallbackToken()
                                fcmTokenSource = "Pendaftaran Perangkat"
                            }
                            if (forceRefresh) {
                                showToastPopup("Info: Token pendaftaran perangkat aktif")
                            }
                        }
                    }
            } catch (e: Throwable) {
                isFetchingFcmToken = false
                fcmTokenError = e.localizedMessage ?: e.message ?: "Kesalahan inisialisasi Firebase"
                android.util.Log.w("MainViewModel", "FCM fetch note: ${e.message}")
                if (fcmToken.isEmpty()) {
                    fcmToken = generateOrGetFallbackToken()
                    fcmTokenSource = "Pendaftaran Perangkat"
                }
            }
        }
    }

    fun refreshFcmToken() {
        showToastPopup("Memeriksa dan memperbarui token perangkat...")
        fetchFcmToken(forceRefresh = true)
    }

    fun markFcmAsRead(id: Long) {
        viewModelScope.launch {
            repository.markFcmAsRead(id)
        }
    }

    fun markAllFcmAsRead() {
        viewModelScope.launch {
            repository.markAllFcmAsRead()
            showToastPopup("Semua notifikasi Cloud Messaging ditandai telah dibaca")
        }
    }

    fun deleteFcmNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteFcmNotification(id)
        }
    }

    fun clearAllFcmNotifications() {
        viewModelScope.launch {
            repository.clearAllFcmNotifications()
            showToastPopup("Riwayat notifikasi Cloud Messaging telah dikosongkan")
        }
    }

    fun simulateTestInAppMessage() {
        viewModelScope.launch {
            val testNotification = FcmNotification(
                title = "In-App Messaging: Pengumuman",
                body = "Halo! Ini adalah contoh pesan In-App Messaging yang muncul langsung sebagai pop-up di layar aplikasi My Singghara saat aplikasi sedang dibuka.",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                dataPayload = "source: firebase_in_app, topic: all"
            )
            val id = repository.insertFcmNotification(testNotification)
            activeInAppMessage = testNotification.copy(id = id)
            showToastPopup("Pop-up In-App Messaging ditampilkan!")
        }
    }

    fun simulateTestFcmNotification() {
        viewModelScope.launch {
            val testNotification = FcmNotification(
                title = "Pengumuman Cloud Messaging",
                body = "Halo dari Firebase Cloud Messaging! Notifikasi push ini dikirim dari konsol pesan Firebase dan berhasil diterima di sistem OS Android.",
                timestamp = System.currentTimeMillis(),
                isRead = false,
                dataPayload = "source: firebase_console, topic: all"
            )
            val id = repository.insertFcmNotification(testNotification)

            // Kirim notifikasi langsung ke bilah status / sistem OS Android
            try {
                com.example.NotificationScheduler.showFcmNotification(
                    getApplication(),
                    testNotification.title,
                    testNotification.body,
                    testNotification.dataPayload
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error posting test FCM notification to OS", e)
            }

            showToastPopup("Pesan uji coba telah dikirim ke bilah notifikasi sistem OS Android!")
        }
    }

    // Storage Access Framework (SAF) URI persistence
    var safFolderUriStr by mutableStateOf(sharedPrefs.getString("saf_folder_uri", "") ?: "")

    // Global popup toast message state for dialog replacements
    var toastPopupMessage by mutableStateOf<String?>(null)
    var toastPopupType by mutableStateOf(com.example.ui.components.ToastType.AUTO)

    fun showToastPopup(msg: String, type: com.example.ui.components.ToastType = com.example.ui.components.ToastType.AUTO) {
        toastPopupType = type
        toastPopupMessage = msg
    }

    // Custom Profile state
    var profileName by mutableStateOf(sharedPrefs.getString("profile_name", "Singghara User") ?: "Singghara User")
    var profileImageUri by mutableStateOf(sharedPrefs.getString("profile_image_uri", "") ?: "")
    var profileAvatarIndex by mutableStateOf(sharedPrefs.getInt("profile_avatar_index", 0))

    // Custom Clock Font state
    var customClockFontPath by mutableStateOf(sharedPrefs.getString("custom_clock_font_path", "") ?: "")
    var customClockFontName by mutableStateOf(sharedPrefs.getString("custom_clock_font_name", "") ?: "")
    var clockFontSizeSp by mutableStateOf(sharedPrefs.getFloat("clock_font_size_sp", 52f))

    fun saveClockFontSize(sizeSp: Float) {
        val clamped = sizeSp.coerceIn(24f, 96f)
        clockFontSizeSp = clamped
        sharedPrefs.edit().putFloat("clock_font_size_sp", clamped).apply()
    }

    fun saveCustomClockFont(path: String, fileName: String) {
        customClockFontPath = path
        customClockFontName = fileName
        sharedPrefs.edit()
            .putString("custom_clock_font_path", path)
            .putString("custom_clock_font_name", fileName)
            .apply()
    }

    fun resetCustomClockFont() {
        customClockFontPath = ""
        customClockFontName = ""
        sharedPrefs.edit()
            .remove("custom_clock_font_path")
            .remove("custom_clock_font_name")
            .apply()
    }

    // Azan sound setting: 0 = Mute, 1 = Hanya Notifikasi Sistem, 2 = Azan Madinah
    var azanSoundSetting by mutableStateOf(sharedPrefs.getInt("azan_sound_setting", 2))

    fun saveAzanSoundSetting(setting: Int) {
        sharedPrefs.edit().putInt("azan_sound_setting", setting).apply()
        azanSoundSetting = setting
    }

    val prayerSoundSettings = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    fun loadPrayerSoundSettings() {
        val prayerList = listOf("Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
        prayerList.forEach { prayer ->
            val defaultVal = if (prayer == "Imsak") 1 else 2
            prayerSoundSettings[prayer] = sharedPrefs.getInt("azan_sound_setting_${prayer.lowercase(Locale.getDefault())}", defaultVal)
        }
    }

    fun savePrayerSoundSetting(prayerName: String, setting: Int) {
        sharedPrefs.edit().putInt("azan_sound_setting_${prayerName.lowercase(Locale.getDefault())}", setting).apply()
        prayerSoundSettings[prayerName] = setting
    }

    fun saveCustomAzanSound(prayerName: String, uri: android.net.Uri, context: android.content.Context): Boolean {
        return try {
            val prayerKey = prayerName.lowercase(Locale.getDefault())
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return false
            val destFile = java.io.File(context.filesDir, "azan_custom_$prayerKey.mp3")
            if (destFile.exists()) {
                destFile.delete()
            }
            java.io.FileOutputStream(destFile).use { outputStream ->
                inputStream.use { input ->
                    input.copyTo(outputStream)
                }
            }

            // Simpan path ke file internal yang valid dan bersihkan file yang dihilangkan
            try {
                sharedPrefs.edit().putString("azan_custom_path_$prayerKey", destFile.absolutePath).apply()
                com.example.util.SinggharaFolderManager.removeDisallowedMp3Files()
            } catch (ignored: Exception) {}

            savePrayerSoundSetting(prayerName, 3)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setCustomAzanFromFile(prayerName: String, file: java.io.File, context: android.content.Context): Boolean {
        return try {
            val prayerKey = prayerName.lowercase(Locale.getDefault())
            val destFile = java.io.File(context.filesDir, "azan_custom_$prayerKey.mp3")
            if (destFile.exists()) destFile.delete()
            file.copyTo(destFile, overwrite = true)
            sharedPrefs.edit().putString("azan_custom_path_$prayerKey", file.absolutePath).apply()
            savePrayerSoundSetting(prayerName, 3)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Notification manual delete confirmation state
    var showConfirmDeleteNotificationDialog by mutableStateOf(false)
    var showFcmNotificationsDialog by mutableStateOf(false)
    var agendaNotificationDismissedDate by mutableStateOf(sharedPrefs.getString("dismissed_agenda_date", "") ?: "")

    fun setAgendaNotificationDismissedToday(dismissed: Boolean) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val dateValue = if (dismissed) todayStr else ""
        sharedPrefs.edit().putString("dismissed_agenda_date", dateValue).apply()
        agendaNotificationDismissedDate = dateValue
    }

    fun reShowDailyAgendaNotification() {
        viewModelScope.launch(Dispatchers.IO) {
            NotificationScheduler.checkAndShowDailyAgenda(getApplication())
        }
    }

    // Keuangan states
    var journalEntriesStr by mutableStateOf(sharedPrefs.getString("journal_entries_serialized", "") ?: "")
    var adjustmentEntriesStr by mutableStateOf(sharedPrefs.getString("adjustment_entries_serialized", "") ?: "")

    fun saveJournalEntriesStr(serialized: String) {
        sharedPrefs.edit().putString("journal_entries_serialized", serialized).apply()
        journalEntriesStr = serialized
    }

    fun saveAdjustmentEntriesStr(serialized: String) {
        sharedPrefs.edit().putString("adjustment_entries_serialized", serialized).apply()
        adjustmentEntriesStr = serialized
    }

    fun reloadFromSharedPrefs() {
        safFolderUriStr = sharedPrefs.getString("saf_folder_uri", "") ?: ""
        profileName = sharedPrefs.getString("profile_name", "Singghara User") ?: "Singghara User"
        profileImageUri = sharedPrefs.getString("profile_image_uri", "") ?: ""
        profileAvatarIndex = sharedPrefs.getInt("profile_avatar_index", 0)
        customClockFontPath = sharedPrefs.getString("custom_clock_font_path", "") ?: ""
        customClockFontName = sharedPrefs.getString("custom_clock_font_name", "") ?: ""
        clockFontSizeSp = sharedPrefs.getFloat("clock_font_size_sp", 52f)
        isAppLockEnabled = sharedPrefs.getBoolean("app_lock_enabled", false)
        if (!isAppLockEnabled) {
            isAppUnlocked = true
        }
        appPin = sharedPrefs.getString("app_pin", "") ?: ""
        journalEntriesStr = sharedPrefs.getString("journal_entries_serialized", "") ?: ""
        adjustmentEntriesStr = sharedPrefs.getString("adjustment_entries_serialized", "") ?: ""
        agendaNotificationDismissedDate = sharedPrefs.getString("dismissed_agenda_date", "") ?: ""
        azanSoundSetting = sharedPrefs.getInt("azan_sound_setting", 2)
        isFirstInstallSetupCompleted = sharedPrefs.getBoolean("first_install_setup_completed", false)
        activeTimeZone = sharedPrefs.getString("active_timezone", "WIB") ?: "WIB"
        musicFolderUriStr = sharedPrefs.getString("music_folder_uri", "") ?: ""

        favoriteTrackUris.clear()
        favoriteTrackUris.addAll(sharedPrefs.getStringSet("music_favorites", emptySet()) ?: emptySet())

        myPlaylistTrackUris.clear()
        myPlaylistTrackUris.addAll(sharedPrefs.getStringSet("music_my_playlist", emptySet()) ?: emptySet())

        playCounts.clear()
        val savedCounts = sharedPrefs.getStringSet("music_play_counts", emptySet()) ?: emptySet()
        savedCounts.forEach { entry ->
            val parts = entry.split(":::")
            if (parts.size == 2) {
                val uri = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                playCounts[uri] = count
            }
        }

        loadPrayerSoundSettings()
    }

    fun saveProfileName(newName: String) {
        sharedPrefs.edit().putString("profile_name", newName).apply()
        profileName = newName
    }

    fun saveProfileImageUri(uriStr: String) {
        if (uriStr.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(uriStr)
                val timestamp = System.currentTimeMillis()
                val avatarFile = java.io.File(getApplication<Application>().filesDir, "custom_profile_avatar_${timestamp}.jpg")
                getApplication<Application>().filesDir.listFiles { _, name ->
                    name.startsWith("custom_profile_avatar") && name != avatarFile.name
                }?.forEach { it.delete() }

                val stream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                }
                stream?.use { input ->
                    avatarFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val localUriStr = android.net.Uri.fromFile(avatarFile).toString()
                sharedPrefs.edit().putString("profile_image_uri", localUriStr).apply()
                profileImageUri = localUriStr
            } catch (e: Exception) {
                sharedPrefs.edit().putString("profile_image_uri", uriStr).apply()
                profileImageUri = uriStr
            }
            profileAvatarIndex = -1
            sharedPrefs.edit().putInt("profile_avatar_index", -1).apply()
        } else {
            sharedPrefs.edit().putString("profile_image_uri", "").apply()
            profileImageUri = ""
        }
    }

    fun saveProfileImageBitmap(bitmap: android.graphics.Bitmap) {
        try {
            val timestamp = System.currentTimeMillis()
            val avatarFile = java.io.File(getApplication<Application>().filesDir, "custom_profile_avatar_${timestamp}.jpg")
            getApplication<Application>().filesDir.listFiles { _, name ->
                name.startsWith("custom_profile_avatar") && name != avatarFile.name
            }?.forEach { it.delete() }

            java.io.FileOutputStream(avatarFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            val localUriStr = android.net.Uri.fromFile(avatarFile).toString()
            sharedPrefs.edit().putString("profile_image_uri", localUriStr).apply()
            profileImageUri = localUriStr
            profileAvatarIndex = -1
            sharedPrefs.edit().putInt("profile_avatar_index", -1).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveProfileAvatarIndex(index: Int) {
        sharedPrefs.edit().putInt("profile_avatar_index", index).apply()
        profileAvatarIndex = index
        if (index >= 0) {
            profileImageUri = ""
            sharedPrefs.edit().putString("profile_image_uri", "").apply()
            try {
                val avatarFile = java.io.File(getApplication<Application>().filesDir, "custom_profile_avatar.jpg")
                if (avatarFile.exists()) avatarFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // App Lock (Biometrik / PIN / Pola)
    var isAppLockEnabled by mutableStateOf(sharedPrefs.getBoolean("app_lock_enabled", false))
    var isAppUnlocked by mutableStateOf(!sharedPrefs.getBoolean("app_lock_enabled", false))
    var appPin by mutableStateOf(sharedPrefs.getString("app_pin", "") ?: "")
    var preventAutoLock by mutableStateOf(false)
    var lastBackgroundTime: Long = 0L

    fun saveAppLockEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("app_lock_enabled", enabled).commit()
        isAppLockEnabled = enabled
        // Tetap unlocked saat diaktifkan di sesi aktif agar aplikasi tidak langsung terkunci secara mengejutkan
        isAppUnlocked = true
    }

    fun saveAppPin(pin: String) {
        sharedPrefs.edit().putString("app_pin", pin).commit()
        appPin = pin
    }

    var isFirstInstallSetupCompleted by mutableStateOf(sharedPrefs.getBoolean("first_install_setup_completed", false))

    fun completeFirstInstallSetup() {
        sharedPrefs.edit().putBoolean("first_install_setup_completed", true).commit()
        isFirstInstallSetupCompleted = true
    }

    fun saveSelectedFolderUri(uriStr: String) {
        sharedPrefs.edit().putString("saf_folder_uri", uriStr).apply()
        safFolderUriStr = uriStr
    }

    fun clearSelectedFolderUri() {
        sharedPrefs.edit().remove("saf_folder_uri").apply()
        safFolderUriStr = ""
    }

    fun isSafFolderConfigured(context: Context): Boolean {
        if (safFolderUriStr.isEmpty()) return false
        return try {
            val uri = android.net.Uri.parse(safFolderUriStr)
            val hasPerm = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission && it.isReadPermission
            }
            if (!hasPerm) return false
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            folder != null && folder.exists() && folder.canWrite() && folder.canRead()
        } catch (e: Exception) {
            false
        }
    }

    // Navigator & UI State Management
    val recentAppIds = mutableStateListOf<String>()

    fun recordAppOpen(appId: String) {
        if (appId.isBlank() || !AppRegistry.allAppsMap.containsKey(appId)) return
        recentAppIds.remove(appId)
        recentAppIds.add(0, appId)
        val strToSave = recentAppIds.joinToString(",")
        sharedPrefs.edit().putString("recent_app_ids", strToSave).apply()
    }

    private var _currentRoute by mutableStateOf("home")
    var currentRoute: String
        get() = _currentRoute
        set(value) {
            _currentRoute = value
            if (AppRegistry.allAppsMap.containsKey(value)) {
                recordAppOpen(value)
            }
        }
    var showAllAppsPage by mutableStateOf(false)
    var notePreviewHtml by mutableStateOf("")
    var hideGlobalTopAppBar by mutableStateOf(false)
    var isBottomBarVisible by mutableStateOf(true)
    var showTabunganFabDialog by mutableStateOf(false)
    var activeGameSectionInGameScreen by mutableStateOf<String?>(null)

    // WEB SERVER PC CONNECT & SECURITY
    enum class WebAccessSecurityState { NONE, PROMPT, VERIFY_PIN_QR }
    var webAccessSecurityState by mutableStateOf(WebAccessSecurityState.NONE)
    var webAccessClientIp by mutableStateOf("")

    var webServer: com.example.web.LocalHttpServer? = null
    var webServerUrl by mutableStateOf("")
    val connectedWebDevices = mutableStateListOf<com.example.web.LocalHttpServer.ConnectedDevice>()

    fun refreshConnectedWebDevices() {
        val list = webServer?.getConnectedDevices() ?: emptyList()
        connectedWebDevices.clear()
        connectedWebDevices.addAll(list)
    }

    fun approveDeviceSession(sessionId: String) {
        webServer?.approveDeviceSession(sessionId)
        refreshConnectedWebDevices()
        NotificationScheduler.cancelWebAccessNotification(getApplication())
        webAccessSecurityState = WebAccessSecurityState.NONE
        showToastPopup("Sesi perangkat berhasil diizinkan!", com.example.ui.components.ToastType.SUCCESS)
    }

    fun lockAndDisconnectDevice(sessionId: String) {
        webServer?.rejectDeviceSession(sessionId)
        refreshConnectedWebDevices()
        showToastPopup("Sesi perangkat berhasil dikunci & diputuskan!", com.example.ui.components.ToastType.SUCCESS)
    }

    fun startWebServer(onStarted: (String) -> Unit, onError: (String) -> Unit) {
        if (webServer == null) {
            webServer = com.example.web.LocalHttpServer(getApplication(), repository).apply {
                onAccessRequested = { ip, _ ->
                    webAccessClientIp = ip
                    refreshConnectedWebDevices()
                    NotificationScheduler.showWebAccessNotification(getApplication(), ip)
                    if (webAccessSecurityState == WebAccessSecurityState.NONE) {
                        webAccessSecurityState = WebAccessSecurityState.PROMPT
                    }
                }
                onDevicesChanged = { list ->
                    connectedWebDevices.clear()
                    connectedWebDevices.addAll(list)
                }
            }
        } else {
            webServer?.onAccessRequested = { ip, _ ->
                webAccessClientIp = ip
                refreshConnectedWebDevices()
                NotificationScheduler.showWebAccessNotification(getApplication(), ip)
                if (webAccessSecurityState == WebAccessSecurityState.NONE) {
                    webAccessSecurityState = WebAccessSecurityState.PROMPT
                }
            }
            webServer?.onDevicesChanged = { list ->
                connectedWebDevices.clear()
                connectedWebDevices.addAll(list)
            }
        }
        webServer?.start(
            onStarted = { url ->
                webServerUrl = url
                refreshConnectedWebDevices()
                onStarted(url)
            },
            onError = { err ->
                onError(err)
            }
        )
    }

    fun stopWebServer() {
        webServer?.stop()
        webServerUrl = ""
        connectedWebDevices.clear()
        webAccessSecurityState = WebAccessSecurityState.NONE
        NotificationScheduler.cancelWebAccessNotification(getApplication())
    }

    fun onApproveWebAccessPrompt() {
        webAccessSecurityState = WebAccessSecurityState.VERIFY_PIN_QR
    }

    fun onDirectApproveWebAccess() {
        approveAllPendingWebSessions()
        NotificationScheduler.cancelWebAccessNotification(getApplication())
        webAccessSecurityState = WebAccessSecurityState.NONE
        showToastPopup("Akses PC berhasil diizinkan!", com.example.ui.components.ToastType.SUCCESS)
    }

    fun onRejectWebAccess() {
        webServer?.rejectPendingAccess()
        refreshConnectedWebDevices()
        NotificationScheduler.cancelWebAccessNotification(getApplication())
        webAccessSecurityState = WebAccessSecurityState.NONE
        showToastPopup("Akses Web PC ditolak", com.example.ui.components.ToastType.ERROR)
    }

    fun dismissWebAccessDialog() {
        webAccessSecurityState = WebAccessSecurityState.NONE
    }

    fun openWebAuthPromptFromNotification() {
        webAccessSecurityState = WebAccessSecurityState.PROMPT
    }

    fun approveWebSession(tokenOrPin: String): Boolean {
        val res = webServer?.approveSession(tokenOrPin) ?: false
        if (res) {
            refreshConnectedWebDevices()
            NotificationScheduler.cancelWebAccessNotification(getApplication())
            webAccessSecurityState = WebAccessSecurityState.NONE
        }
        return res
    }

    fun approveAllPendingWebSessions(): Boolean {
        val res = webServer?.approveAllPending() ?: false
        if (res) {
            refreshConnectedWebDevices()
            NotificationScheduler.cancelWebAccessNotification(getApplication())
            webAccessSecurityState = WebAccessSecurityState.NONE
        }
        return res
    }

    fun revokeWebSessions() {
        webServer?.revokeAll()
        refreshConnectedWebDevices()
        NotificationScheduler.cancelWebAccessNotification(getApplication())
        webAccessSecurityState = WebAccessSecurityState.NONE
        showToastPopup("Semua sesi PC berhasil dikunci & diputuskan!", com.example.ui.components.ToastType.SUCCESS)
    }

    fun getWebPinCode(): String {
        return webServer?.latestPinCode ?: "------"
    }

    fun pushKasirBarcodeScan(barcode: String) {
        webServer?.pushBarcodeScan(barcode)
    }

    // IMMERSIVE FILE VIEWER STATES
    var immersiveFileUriStr by mutableStateOf<String?>(null)
    var immersiveFileName by mutableStateOf("")
    var immersiveFileContent by mutableStateOf("")
    var immersiveFileIsPdf by mutableStateOf(false)

    // REAL-TIME CLOCK STATE
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _currentDay = MutableStateFlow("")
    val currentDay: StateFlow<String> = _currentDay.asStateFlow()

    private var _activeTimeZone by mutableStateOf(sharedPrefs.getString("active_timezone", "WIB") ?: "WIB")
    var activeTimeZone: String
        get() = _activeTimeZone
        set(value) {
            _activeTimeZone = value
            sharedPrefs.edit().putString("active_timezone", value).apply()
            NotificationScheduler.scheduleAlarms(getApplication())
        }

    // GPS LOCATION AND EXTENDED DETECTOR STATE FOR ACCURATE REALTIME CALENDAR / PRAYER
    private var _userLatitude by mutableStateOf<Double?>(
        if (sharedPrefs.contains("user_latitude")) sharedPrefs.getString("user_latitude", "0")?.toDoubleOrNull() else null
    )
    var userLatitude: Double?
        get() = _userLatitude
        set(value) {
            _userLatitude = value
            if (value != null) {
                sharedPrefs.edit().putString("user_latitude", value.toString()).apply()
            } else {
                sharedPrefs.edit().remove("user_latitude").apply()
            }
            NotificationScheduler.scheduleAlarms(getApplication())
        }

    private var _userLongitude by mutableStateOf<Double?>(
        if (sharedPrefs.contains("user_longitude")) sharedPrefs.getString("user_longitude", "0")?.toDoubleOrNull() else null
    )
    var userLongitude: Double?
        get() = _userLongitude
        set(value) {
            _userLongitude = value
            if (value != null) {
                sharedPrefs.edit().putString("user_longitude", value.toString()).apply()
            } else {
                sharedPrefs.edit().remove("user_longitude").apply()
            }
            NotificationScheduler.scheduleAlarms(getApplication())
        }

    private var _userLocationName by mutableStateOf<String?>(
        sharedPrefs.getString("user_location_name", null)
    )
    var userLocationName: String?
        get() = _userLocationName
        set(value) {
            _userLocationName = value
            if (value != null) {
                sharedPrefs.edit().putString("user_location_name", value).apply()
            } else {
                sharedPrefs.edit().remove("user_location_name").apply()
            }
        }
    var isFetchingLocation by mutableStateOf(false)

    val prayerSchedule: com.example.ui.components.PrayerSchedule
        get() {
            val calendar = java.util.Calendar.getInstance()
            val lat = userLatitude
            val lon = userLongitude
            return if (lat != null && lon != null) {
                val offsetHours = calendar.timeZone.getOffset(System.currentTimeMillis()) / 3600000.0
                com.example.ui.components.PrayerTimesHelper.getPrayerTimesForLocation(lat, lon, offsetHours, calendar)
            } else {
                com.example.ui.components.PrayerTimesHelper.getPrayerTimesForTimezone(activeTimeZone, calendar)
            }
        }

    // LIVE HIJRI DATE STATE FROM ALADHAN API
    private val _apiHijriDate = MutableStateFlow<String?>(null)
    val apiHijriDate: StateFlow<String?> = _apiHijriDate.asStateFlow()
    var isHijriApiLoading by mutableStateOf(false)

    // Adjustable lunar sighting offset (Default: -1 converts perfectly to Kemenag RI 27 Dzulhijjah 1447 H on June 13, 2026)
    var hijriOffset by mutableStateOf(-1)

    // DATABASE FLOWS EXPOSED TO UI
    val shoppingItems: StateFlow<List<ShoppingItem>> = repository.allShoppingItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteItem>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSavingTarget: StateFlow<SavingTarget?> = repository.activeSavingTarget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savingTransactions: StateFlow<List<SavingTransaction>> = repository.allSavingTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agendaItems: StateFlow<List<AgendaItem>> = repository.allAgendas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quranBookmarks: StateFlow<List<QuranBookmark>> = repository.allQuranBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quranHistory: StateFlow<List<QuranHistory>> = repository.allQuranHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mindMaps: StateFlow<List<MindMap>> = repository.allMindMaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val jadwalList: StateFlow<List<JadwalKegiatan>> = repository.allJadwal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAnggota: StateFlow<List<AnggotaItem>> = repository.allAnggota
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPresensi: StateFlow<List<RiwayatPresensi>> = repository.allPresensi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGaji: StateFlow<List<GajiItem>> = repository.allGaji
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBelanjaPasar: StateFlow<List<BelanjaPasarItem>> = repository.allBelanjaPasarItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Belanja Pasar Management
    fun saveBelanjaPasar(item: BelanjaPasarItem, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = if (item.id == 0) {
                repository.insertBelanjaPasar(item)
            } else {
                repository.updateBelanjaPasar(item)
                item.id.toLong()
            }
            onComplete(id)
        }
    }

    fun deleteBelanjaPasar(item: BelanjaPasarItem) {
        viewModelScope.launch {
            repository.deleteBelanjaPasar(item)
        }
    }

    fun toggleBelanjaPasarStatus(id: Int, sudahDibeli: Boolean) {
        viewModelScope.launch {
            repository.setBelanjaPasarSudahDibeli(id, sudahDibeli)
        }
    }

    fun clearCompletedBelanjaPasar() {
        viewModelScope.launch {
            repository.clearCompletedBelanjaPasar()
        }
    }

    // Gaji Management
    fun saveGaji(gaji: GajiItem, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertGaji(gaji)
            onComplete(id)
        }
    }

    fun updateGaji(gaji: GajiItem, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateGaji(gaji)
            onComplete()
        }
    }

    fun deleteGaji(gaji: GajiItem) {
        viewModelScope.launch {
            repository.deleteGaji(gaji)
        }
    }

    fun deleteAllGaji() {
        viewModelScope.launch {
            repository.deleteAllGaji()
        }
    }

    // Anggota & Presensi Management
    var selectedAnggotaForIdCard by mutableStateOf<AnggotaItem?>(null)

    fun saveAnggota(anggota: AnggotaItem, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertAnggota(anggota)
            onComplete(id)
        }
    }

    fun updateAnggota(anggota: AnggotaItem) {
        viewModelScope.launch {
            repository.updateAnggota(anggota)
        }
    }

    fun deleteAnggota(anggota: AnggotaItem) {
        viewModelScope.launch {
            repository.deleteAnggota(anggota)
        }
    }

    /**
     * Memeriksa dan otomatis menghapus data ID Card yang masa berlakunya telah habis.
     */
    fun checkAndCleanupExpiredAnggota() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = repository.allAnggota.first()
                val now = System.currentTimeMillis()
                val expired = list.filter {
                    com.example.utils.MasaBerlakuHelper.isExpired(it.berlakuHingga, it.tanggalDibuat, now)
                }
                if (expired.isNotEmpty()) {
                    expired.forEach { item ->
                        repository.deleteAnggota(item)
                    }
                    withContext(Dispatchers.Main) {
                        val names = expired.joinToString(", ") { it.nama }
                        showToastPopup("${expired.size} data ID Card yang habis masa berlaku otomatis dihapus: $names")
                    }
                }
            } catch (e: Exception) {
                // Ignore background check failure
            }
        }
    }

    fun recordPresensi(presensi: RiwayatPresensi, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertPresensi(presensi)
            onComplete(id)
        }
    }

    fun updatePresensi(presensi: RiwayatPresensi, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updatePresensi(presensi)
            onComplete()
        }
    }

    fun deletePresensi(presensi: RiwayatPresensi) {
        viewModelScope.launch {
            repository.deletePresensi(presensi)
        }
    }

    fun deleteAllPresensi() {
        viewModelScope.launch {
            repository.deleteAllPresensi()
        }
    }

    fun saveJadwal(jadwal: JadwalKegiatan) {
        viewModelScope.launch {
            repository.insertJadwal(jadwal)
        }
    }

    fun deleteJadwal(jadwal: JadwalKegiatan) {
        viewModelScope.launch {
            repository.deleteJadwal(jadwal)
        }
    }

    fun insertMindMap(mindMap: MindMap, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertMindMap(mindMap)
            onComplete(id)
        }
    }

    fun updateMindMap(mindMap: MindMap) {
        viewModelScope.launch {
            repository.updateMindMap(mindMap)
        }
    }

    fun deleteMindMap(mindMap: MindMap) {
        viewModelScope.launch {
            repository.deleteMindMap(mindMap)
        }
    }

    // QURAN STATE MANAGEMENT
    var selectedQuranSurahId by mutableStateOf(1)
    var selectedQuranJuzId by mutableStateOf<Int?>(null)
    var quranSearchQuery by mutableStateOf("")
    var quranShowBookmarksOnly by mutableStateOf(false)
    var selectedQuranTab by mutableStateOf(0) // 0 = Surah, 1 = Juz, 2 = Bookmark & Riwayat
    var currentQuranVerseNumberForAudio by mutableStateOf<Int?>(null)
    var isQuranPlaying by mutableStateOf(false)

    // MediaPlayer for offline recitation / online streaming
    private var quranMediaPlayer: android.media.MediaPlayer? = null
    var activeQari by mutableStateOf("Mishary Alafasy")

    fun playQuranRecitation(context: Context, surahId: Int, verseNumber: Int? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                quranMediaPlayer?.stop()
                quranMediaPlayer?.release()
                quranMediaPlayer = null
                isQuranPlaying = false
                currentQuranVerseNumberForAudio = verseNumber

                val url = if (verseNumber != null) {
                    val surahStr = String.format("%03d", surahId)
                    val verseStr = String.format("%03d", verseNumber)
                    val qariFolder = when (activeQari) {
                        "Abdul Rahman Al-Sudais" -> "Abdurrahmeen_As-Sudais_64kbps"
                        "Saad Al-Ghamdi" -> "Saood_ash-Shuraym_64kbps"
                        else -> "Alafasy_64kbps"
                    }
                    "https://everyayah.com/data/$qariFolder/$surahStr$verseStr.mp3"
                } else {
                    val surahStr = String.format("%03d", surahId)
                    "https://server8.mp3quran.net/afs/$surahStr.mp3"
                }

                // Check network connectivity first
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetworkInfo
                val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting

                if (!isConnected) {
                    showToastPopup("Mode Pesawat: Teks & Tafsir aktif offline.")
                    currentQuranVerseNumberForAudio = null
                    return@launch
                }

                showToastPopup("Memutar Murattal $activeQari...")
                quranMediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(url)
                    setOnPreparedListener {
                        start()
                        isQuranPlaying = true
                    }
                    setOnCompletionListener {
                        isQuranPlaying = false
                        currentQuranVerseNumberForAudio = null
                        release()
                        quranMediaPlayer = null
                    }
                    setOnErrorListener { _, _, _ ->
                        isQuranPlaying = false
                        currentQuranVerseNumberForAudio = null
                        showToastPopup("Gagal memutar audio. Pastikan koneksi internet stabil.")
                        false
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isQuranPlaying = false
                currentQuranVerseNumberForAudio = null
                showToastPopup("Terjadi kesalahan memutar audio.")
            }
        }
    }

    fun pauseQuranRecitation() {
        quranMediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isQuranPlaying = false
            } else {
                it.start()
                isQuranPlaying = true
            }
        }
    }

    fun stopQuranRecitation() {
        quranMediaPlayer?.stop()
        quranMediaPlayer?.release()
        quranMediaPlayer = null
        isQuranPlaying = false
        currentQuranVerseNumberForAudio = null
    }

    // Bookmarking and History database modifiers
    fun toggleQuranBookmark(surahId: Int, surahName: String, verseNumber: Int) {
        viewModelScope.launch {
            val isBookmarked = repository.isQuranBookmarked(surahId, verseNumber)
            if (isBookmarked) {
                repository.deleteQuranBookmarkByVerse(surahId, verseNumber)
            } else {
                repository.insertQuranBookmark(QuranBookmark(surahId = surahId, surahName = surahName, verseNumber = verseNumber))
            }
        }
    }

    fun addQuranHistory(surahId: Int, surahName: String, verseNumber: Int) {
        viewModelScope.launch {
            repository.insertQuranHistory(QuranHistory(surahId = surahId, surahName = surahName, verseNumber = verseNumber))
        }
    }

    fun clearQuranHistory() {
        viewModelScope.launch {
            repository.clearQuranHistory()
        }
    }

    // CACHE FOR REMOTE QURAN TEXTS
    val remoteQuranVerses = MutableStateFlow<Map<Int, List<Verse>>>(emptyMap())
    var isQuranLoadingRemote by mutableStateOf(false)

    fun fetchRemoteQuranVerses(surahId: Int) {
        if (remoteQuranVerses.value.containsKey(surahId)) return // already loaded and cached
        
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                isQuranLoadingRemote = true
            }
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://equran.id/api/v2/surat/$surahId")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val jsonStr = response.body?.string() ?: ""
                    if (response.isSuccessful && jsonStr.isNotEmpty()) {
                        val jsonObject = org.json.JSONObject(jsonStr)
                        if (jsonObject.optInt("code") == 200) {
                            val dataObj = jsonObject.getJSONObject("data")
                            val ayatArray = dataObj.getJSONArray("ayat")
                            val versesList = mutableListOf<Verse>()
                            for (i in 0 until ayatArray.length()) {
                                val ayatObj = ayatArray.getJSONObject(i)
                                val nomorAyat = ayatObj.getInt("nomorAyat")
                                val teksArab = ayatObj.getString("teksArab")
                                val teksIndonesia = ayatObj.getString("teksIndonesia")
                                val tafsirVal = "Tafsir Ayat $nomorAyat: Tersedia lengkap di Tafsir Kemenag (Kementerian Agama RI)."
                                
                                versesList.add(
                                    Verse(
                                        surahId = surahId,
                                        verseNumber = nomorAyat,
                                        textArabic = teksArab,
                                        translation = teksIndonesia,
                                        tafsir = tafsirVal,
                                        juz = QuranData.getJuzNumber(surahId, nomorAyat)
                                    )
                                )
                            }
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                val currentMap = remoteQuranVerses.value.toMutableMap()
                                currentMap[surahId] = versesList
                                remoteQuranVerses.value = currentMap
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    isQuranLoadingRemote = false
                }
            }
        }
    }

    // KEMENAG RI PRAYER SCHEDULE (BIMAS ISLAM)
    var kemenagPrayerSchedule by mutableStateOf<com.example.ui.components.PrayerSchedule?>(null)
    var isKemenagPrayerLoading by mutableStateOf(false)
    var activeKemenagCityName by mutableStateOf<String?>(null)

    fun fetchKemenagPrayerSchedule(context: Context) {
        val locName = userLocationName ?: return
        if (locName == "Koordinat Aktif" || locName.isEmpty()) return
        
        val cityName = sanitizeCityName(locName)
        if (cityName.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                isKemenagPrayerLoading = true
            }
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                // Step 1: Search for city ID in Kemenag/MyQuran database
                val searchUrl = "https://api.myquran.com/v2/sholat/kota/cari/${java.net.URLEncoder.encode(cityName, "UTF-8")}"
                val searchRequest = okhttp3.Request.Builder().url(searchUrl).get().build()
                
                var cityId: String? = null
                var matchedCityName: String? = null
                
                client.newCall(searchRequest).execute().use { response ->
                    val jsonStr = response.body?.string() ?: ""
                    if (response.isSuccessful && jsonStr.isNotEmpty()) {
                        val jsonObject = org.json.JSONObject(jsonStr)
                        if (jsonObject.optBoolean("status", false)) {
                            val dataArray = jsonObject.optJSONArray("data")
                            if (dataArray != null && dataArray.length() > 0) {
                                val firstCity = dataArray.getJSONObject(0)
                                cityId = firstCity.optString("id")
                                matchedCityName = firstCity.optString("lokasi")
                            }
                        }
                    }
                }

                // Step 2: If city ID is found, get the official Kemenag schedule for today
                if (!cityId.isNullOrEmpty()) {
                    val today = java.util.Calendar.getInstance()
                    val year = today.get(java.util.Calendar.YEAR)
                    val month = String.format("%02d", today.get(java.util.Calendar.MONTH) + 1)
                    val day = String.format("%02d", today.get(java.util.Calendar.DAY_OF_MONTH))
                    
                    val scheduleUrl = "https://api.myquran.com/v2/sholat/jadwal/$cityId/$year/$month/$day"
                    val scheduleRequest = okhttp3.Request.Builder().url(scheduleUrl).get().build()
                    
                    client.newCall(scheduleRequest).execute().use { response ->
                        val jsonStr = response.body?.string() ?: ""
                        if (response.isSuccessful && jsonStr.isNotEmpty()) {
                            val jsonObject = org.json.JSONObject(jsonStr)
                            if (jsonObject.optBoolean("status", false)) {
                                val dataObj = jsonObject.optJSONObject("data")
                                if (dataObj != null) {
                                    val jadwalObj = dataObj.optJSONObject("jadwal")
                                    if (jadwalObj != null) {
                                        val imsak = jadwalObj.optString("imsak")
                                        val subuh = jadwalObj.optString("subuh")
                                        val dzuhur = jadwalObj.optString("dzuhur")
                                        val ashar = jadwalObj.optString("ashar")
                                        val maghrib = jadwalObj.optString("maghrib")
                                        val isya = jadwalObj.optString("isya")
                                        
                                        if (imsak.isNotEmpty() && subuh.isNotEmpty()) {
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                kemenagPrayerSchedule = com.example.ui.components.PrayerSchedule(
                                                    imsak = imsak,
                                                    subuh = subuh,
                                                    dzuhur = dzuhur,
                                                    ashar = ashar,
                                                    maghrib = maghrib,
                                                    isya = isya
                                                )
                                                activeKemenagCityName = matchedCityName
                                                
                                                // Save cache to shared preferences for seamless offline experience
                                                sharedPrefs.edit().apply {
                                                    putString("kemenag_imsak", imsak)
                                                    putString("kemenag_subuh", subuh)
                                                    putString("kemenag_dzuhur", dzuhur)
                                                    putString("kemenag_ashar", ashar)
                                                    putString("kemenag_maghrib", maghrib)
                                                    putString("kemenag_isya", isya)
                                                    putString("kemenag_city", matchedCityName)
                                                    putString("kemenag_cache_date", "$year-$month-$day")
                                                    apply()
                                                }
                                                // Sync prayer alarms immediately with updated Kemenag times
                                                com.example.NotificationScheduler.schedulePrayerAlarmsForToday(getApplication())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    isKemenagPrayerLoading = false
                }
            }
        }
    }

    private fun sanitizeCityName(locationName: String): String {
        var clean = locationName.uppercase(Locale.getDefault())
        clean = clean.replace("KOTA ", "")
            .replace("KABUPATEN ", "")
            .replace("KAB. ", "")
            .replace("KECAMATAN ", "")
            .replace("KELURAHAN ", "")
            .trim()
        if (clean.contains("JAKARTA")) {
            return "JAKARTA"
        }
        return clean
    }

    // HOLIDAYS STATE (FROM RETROFIT)
    private val _apiHolidays = MutableStateFlow<Map<String, String>>(emptyMap())
    val apiHolidays: StateFlow<Map<String, String>> = _apiHolidays.asStateFlow()
    var isHolidayLoading by mutableStateOf(false)

    // ACTIVE NOTE EDITING STATE (Master-Detail Notepads)
    var selectedNote by mutableStateOf<NoteItem?>(null)
    var noteAutoSaveStatus by mutableStateOf("Tersimpan") // "Mengetik...", "Menyimpan...", "Tersimpan"

    // WEATHER STATE MANAGEMENT
    sealed interface WeatherUIState {
        object Loading : WeatherUIState
        data class Success(val response: com.example.network.WeatherResponse) : WeatherUIState
        data class Error(val message: String) : WeatherUIState
    }

    private val _weatherState = MutableStateFlow<WeatherUIState>(WeatherUIState.Loading)
    val weatherState: StateFlow<WeatherUIState> = _weatherState.asStateFlow()

    private var weatherJob: kotlinx.coroutines.Job? = null
    private var lastFetchTime = 0L
    private var connectivityManager: android.net.ConnectivityManager? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    fun fetchWeather() {
        if (weatherJob?.isActive == true) return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime < 10000 && _weatherState.value is WeatherUIState.Success) {
            return
        }

        weatherJob = viewModelScope.launch(Dispatchers.IO) {
            _weatherState.value = WeatherUIState.Loading
            try {
                val lat = userLatitude ?: -6.2088
                val lon = userLongitude ?: 106.8456
                val key = com.example.BuildConfig.OPENWEATHER_API_KEY
                val apiKey = if (key.isNotEmpty() && key != "MY_OPENWEATHER_API_KEY") key else "372412b80fd742017cb552b78ca79807"
                
                val response = com.example.network.WeatherClient.service.getCurrentWeather(
                    lat = lat,
                    lon = lon,
                    apiKey = apiKey
                )
                
                // Cache successful weather response
                try {
                    val moshi = com.squareup.moshi.Moshi.Builder().build()
                    val adapter = moshi.adapter(com.example.network.WeatherResponse::class.java)
                    val json = adapter.toJson(response)
                    sharedPrefs.edit().putString("cached_weather_response", json).apply()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

                lastFetchTime = System.currentTimeMillis()
                _weatherState.value = WeatherUIState.Success(response)
            } catch (e: Exception) {
                // Try to load cached weather when network is offline
                val cachedJson = sharedPrefs.getString("cached_weather_response", null)
                if (cachedJson != null) {
                    try {
                        val moshi = com.squareup.moshi.Moshi.Builder().build()
                        val adapter = moshi.adapter(com.example.network.WeatherResponse::class.java)
                        val cachedResponse = adapter.fromJson(cachedJson)
                        if (cachedResponse != null) {
                            _weatherState.value = WeatherUIState.Success(cachedResponse)
                            return@launch
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                _weatherState.value = WeatherUIState.Error(e.message ?: "Gagal memuat cuaca")
            }
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            isNetworkAvailable = checkInitialNetworkState()
            connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    isNetworkAvailable = true
                    fetchWeather()
                    syncFcmTokenToFirestore()
                    setupAdminMessagesListener()
                    setupOtpRequestsListener()
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    isNetworkAvailable = checkInitialNetworkState()
                }

                override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    isNetworkAvailable = networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }
            networkCallback?.let { callback ->
                connectivityManager?.registerDefaultNetworkCallback(callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load Live Hijri from Aladhan REST API
    fun loadLiveAladhanHijri(dateStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isHijriApiLoading = true
            try {
                val response = AladhanClient.service.convertGregorianToHijri(dateStr)
                if (response.code == 200) {
                    val hijri = response.data.hijri
                    val day = hijri.day
                    val monthIndo = AladhanClient.mapMonthToIndonesian(hijri.month.en)
                    val year = hijri.year
                    _apiHijriDate.value = "$day $monthIndo $year H"
                } else {
                    _apiHijriDate.value = null
                }
            } catch (e: Exception) {
                _apiHijriDate.value = null // Forces template / dynamic local fallback
            } finally {
                isHijriApiLoading = false
            }
        }
    }

    // Dynamic Current GPS Location fetcher with Geocoder and Timezone auto-sync
    fun fetchCurrentLocation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            isFetchingLocation = true
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val providers = locationManager.getProviders(true)
                var bestLocation: android.location.Location? = null
                
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                
                if (hasFine || hasCoarse) {
                    for (provider in providers) {
                        val loc = locationManager.getLastKnownLocation(provider) ?: continue
                        if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                            bestLocation = loc
                        }
                    }
                    
                    bestLocation?.let { loc ->
                        userLatitude = loc.latitude
                        userLongitude = loc.longitude
                        
                        // Automatically update standard timezone option depending on system zone offset
                        val mTimezone = TimeZone.getDefault()
                        val offsetHours = mTimezone.getOffset(System.currentTimeMillis()) / 3600000.0
                        
                        activeTimeZone = when {
                            offsetHours >= 9.0 -> "WIT"
                            offsetHours >= 8.0 -> "WITA"
                            else -> "WIB"
                        }
                        
                        // Address string search
                        try {
                            val geocoder = android.location.Geocoder(context, Locale("id", "ID"))
                            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                userLocationName = address.locality ?: address.subAdminArea ?: address.adminArea
                            } else {
                                userLocationName = "Koordinat Aktif"
                            }
                        } catch (e: Exception) {
                            userLocationName = "Koordinat Aktif"
                        }
                        
                        fetchKemenagPrayerSchedule(context)
                        fetchWeather()
                    }
                }
            } catch (e: Exception) {
                // Silently handle
            } finally {
                isFetchingLocation = false
            }
        }
    }

    // Tick Digital Clock
    private fun startClock() {
        viewModelScope.launch {
            while (true) {
                val calendar = Calendar.getInstance()
                // Set offset based on selected timezone
                val hourOffset = when (activeTimeZone) {
                    "WITA" -> 1
                    "WIT" -> 2
                    else -> 0 // WIB is default
                }
                
                // standard formatted time
                val hour = calendar.get(Calendar.HOUR_OF_DAY) + hourOffset
                val minute = calendar.get(Calendar.MINUTE)
                val second = calendar.get(Calendar.SECOND)
                
                val formattedTime = String.format("%02d:%02d:%02d", hour % 24, minute, second)
                _currentTime.value = formattedTime

                val year = calendar.get(Calendar.YEAR)
                val monthName = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("id", "ID"))
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                _currentDate.value = "$day $monthName $year"

                val dayOfWeekName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale("id", "ID"))
                _currentDay.value = dayOfWeekName ?: ""

                delay(1000)
            }
        }
    }

    // Load Holidays via Retrofit Boilerplate
    fun loadRetrofitHolidays(year: Int = Calendar.getInstance().get(Calendar.YEAR)) {
        viewModelScope.launch(Dispatchers.IO) {
            isHolidayLoading = true
            try {
                // Mendeteksi kode negara pengguna secara real-time berdasarkan locale lokasi saat ini
                val locale = Locale.getDefault()
                var countryCode = locale.country
                if (countryCode.isNullOrEmpty()) {
                    countryCode = "ID" // Fallback default ke Indonesia
                }

                val responseList = HolidayClient.service.getHolidays(year, countryCode)
                val holidayMap = mutableMapOf<String, String>()
                responseList.forEach {
                    val date = it.date
                    val name = it.localName ?: it.name
                    if (date != null && name != null) {
                        holidayMap[date] = name
                    }
                }
                val currentMap = _apiHolidays.value.toMutableMap()
                // Always populate accurate Indonesian national holidays first as base
                currentMap.putAll(HolidayClient.getFallbackHolidays(year))
                if (holidayMap.isNotEmpty()) {
                    currentMap.putAll(holidayMap)
                }
                _apiHolidays.value = currentMap
            } catch (e: Exception) {
                // Gunakan mapping static/fallback jika gagal request (misal offline)
                val currentMap = _apiHolidays.value.toMutableMap()
                currentMap.putAll(HolidayClient.getFallbackHolidays(year))
                _apiHolidays.value = currentMap
            } finally {
                isHolidayLoading = false
            }
        }
    }

    // EXPORT & IMPORT BACKUP TO USER PUBLIC DOWNLOAD DIRECTORY
    fun exportBackup(context: Context, onResult: (String, Boolean) -> Unit) {
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val result = repository.exportDataToPublicDownload(context)
                result.onSuccess {
                    onResult(it, true)
                }.onFailure {
                    onResult(it.message ?: "Gagal mengekspor data", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                kotlinx.coroutines.delay(1000)
                preventAutoLock = false
            }
        }
    }

    fun importBackup(context: Context, onResult: (String, Boolean) -> Unit) {
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val result = repository.importDataFromPublicDownload(context)
                result.onSuccess {
                    reloadFromSharedPrefs()
                    onResult(it, true)
                }.onFailure {
                    onResult(it.message ?: "Gagal mengimpor data", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                kotlinx.coroutines.delay(1000)
                preventAutoLock = false
            }
        }
    }

    // STORAGE ACCESS FRAMEWORK (SAF) EXPORT & IMPORT BACKUP METHODS
    fun exportBackupSAF(context: Context, onResult: (String, Boolean) -> Unit) {
        if (safFolderUriStr.isEmpty()) {
            onResult("Silakan tentukan folder utama Anda terlebih dahulu via tab Profil.", false)
            return
        }
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val folderUri = android.net.Uri.parse(safFolderUriStr)
                val result = repository.exportDataToSAF(context, folderUri)
                result.onSuccess {
                    onResult(it, true)
                }.onFailure {
                    onResult(it.message ?: "Gagal mengekspor data", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                kotlinx.coroutines.delay(1000)
                preventAutoLock = false
            }
        }
    }

    fun importBackupSAF(context: Context, onResult: (String, Boolean) -> Unit) {
        if (safFolderUriStr.isEmpty()) {
            onResult("Silakan tentukan folder utama Anda terlebih dahulu via tab Profil.", false)
            return
        }
        preventAutoLock = true
        viewModelScope.launch {
            try {
                val folderUri = android.net.Uri.parse(safFolderUriStr)
                val result = repository.importDataFromSAF(context, folderUri)
                result.onSuccess {
                    reloadFromSharedPrefs()
                    onResult(it, true)
                }.onFailure {
                    onResult(it.message ?: "Gagal mengimpor data", false)
                }
            } catch (e: Exception) {
                onResult("Terjadi kesalahan: ${e.message}", false)
            } finally {
                kotlinx.coroutines.delay(1000)
                preventAutoLock = false
            }
        }
    }

    // EXPORT TO CSV METHODS
    fun exportShoppingCSV(context: Context, onResult: (String, Boolean) -> Unit) {
        viewModelScope.launch {
            val folderUri = if (safFolderUriStr.isNotEmpty()) android.net.Uri.parse(safFolderUriStr) else null
            val result = repository.exportShoppingToCSV(context, folderUri)
            result.onSuccess {
                onResult(it, true)
            }.onFailure {
                onResult(it.message ?: "Gagal mengekspor data belanja", false)
            }
        }
    }

    fun exportTabunganCSV(context: Context, onResult: (String, Boolean) -> Unit) {
        viewModelScope.launch {
            val folderUri = if (safFolderUriStr.isNotEmpty()) android.net.Uri.parse(safFolderUriStr) else null
            val result = repository.exportTabunganToCSV(context, folderUri)
            result.onSuccess {
                onResult(it, true)
            }.onFailure {
                onResult(it.message ?: "Gagal mengekspor data tabungan", false)
            }
        }
    }

    // CRUD - Shopping
    fun saveShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            if (item.id == 0) {
                repository.insertShopping(item)
            } else {
                repository.updateShopping(item)
            }
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteShopping(item)
        }
    }

    // CRUD - Notes Auto Save
    fun saveNote(note: NoteItem) {
        viewModelScope.launch {
            noteAutoSaveStatus = "Menyimpan..."
            if (note.id == 0) {
                val newId = repository.insertNote(note)
                selectedNote = note.copy(id = newId.toInt())
            } else {
                repository.updateNote(note)
                selectedNote = note
            }
            delay(500) // visual feedback
            noteAutoSaveStatus = "Tersimpan"
        }
    }

    fun deleteNoteItem(note: NoteItem) {
        viewModelScope.launch {
            repository.deleteNote(note)
            if (selectedNote?.id == note.id) {
                selectedNote = null
            }
        }
    }

    fun togglePinNote(note: NoteItem) {
        viewModelScope.launch {
            val updated = note.copy(isPinned = !note.isPinned)
            repository.updateNote(updated)
            if (selectedNote?.id == note.id) {
                selectedNote = updated
            }
        }
    }

    // CRUD - Saving Target & Transactions
    fun setSavingTarget(target: SavingTarget) {
        viewModelScope.launch {
            repository.setSavingTarget(target)
        }
    }

    fun addSavingTransaction(trx: SavingTransaction) {
        viewModelScope.launch {
            repository.insertSavingTransaction(trx)
        }
    }

    fun updateSavingTransaction(trx: SavingTransaction) {
        viewModelScope.launch {
            repository.updateSavingTransaction(trx)
        }
    }

    fun deleteSavingTransaction(trx: SavingTransaction) {
        viewModelScope.launch {
            repository.deleteSavingTransaction(trx)
        }
    }

    // CRUD - Calendar Agenda
    fun addAgendaItem(agenda: AgendaItem) {
        viewModelScope.launch {
            repository.insertAgenda(agenda)
            setAgendaNotificationDismissedToday(false)
            NotificationScheduler.scheduleAlarms(getApplication())
        }
    }

    fun deleteAgendaItem(agenda: AgendaItem) {
        viewModelScope.launch {
            repository.deleteAgenda(agenda)
            NotificationScheduler.scheduleAlarms(getApplication())
        }
    }

    // ==========================================
    // PEMUTAR MUSIK STATE & CONTROLLER
    // ==========================================
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    var musicFolderUriStr by mutableStateOf(sharedPrefs.getString("music_folder_uri", "") ?: "")
    val musicPlaylist = mutableStateListOf<MusicTrack>()
    var currentTrackIndex by mutableStateOf(-1)
    var isMusicPlaying by mutableStateOf(false)
    var musicPlaybackPosition by mutableStateOf(0)
    var musicTotalDuration by mutableStateOf(0)
    var musicRepeatMode by mutableStateOf("ALL") // "NONE", "ONE", "ALL", "AZ"
    var isMusicShuffle by mutableStateOf(false)
    var isMusicLoading by mutableStateOf(false)

    val favoriteTrackUris = mutableStateListOf<String>().apply {
        addAll(sharedPrefs.getStringSet("music_favorites", emptySet()) ?: emptySet())
    }

    val myPlaylistTrackUris = mutableStateListOf<String>().apply {
        addAll(sharedPrefs.getStringSet("music_my_playlist", emptySet()) ?: emptySet())
    }

    val recentlyPlayed = mutableStateListOf<MusicTrack>()

    val playCounts = mutableMapOf<String, Int>().apply {
        val savedCounts = sharedPrefs.getStringSet("music_play_counts", emptySet()) ?: emptySet()
        savedCounts.forEach { entry ->
            val parts = entry.split(":::")
            if (parts.size == 2) {
                val uri = parts[0]
                val count = parts[1].toIntOrNull() ?: 0
                put(uri, count)
            }
        }
    }

    init {
        instance = this
        try {
            com.example.util.SinggharaFolderManager.ensureFoldersExist(application)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val initUser = authManager.currentUser ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (initUser != null) {
            currentFirebaseUser = initUser
            syncUserProfileFromFirebase(initUser)
        }
        try {
            authManager.addAuthStateListener { fbAuth ->
                val u = fbAuth.currentUser
                if (u != null) {
                    currentFirebaseUser = u
                    syncUserProfileFromFirebase(u)
                }
            }
        } catch (_: Exception) {}
        loadPrayerSoundSettings()
        fetchFcmToken()
        syncFcmTokenToFirestore()
        setupAdminMessagesListener()
        setupOtpRequestsListener()
        checkAccountAccessStatus()
        
        // Initialize Recent Apps List
        val savedRecentStr = sharedPrefs.getString("recent_app_ids", null)
        val loadedIds = if (!savedRecentStr.isNullOrEmpty()) {
            savedRecentStr.split(",").map { it.trim() }.filter { AppRegistry.allAppsMap.containsKey(it) }
        } else emptyList()
        val combinedRecent = (listOf("sliding_puzzle") + loadedIds + AppRegistry.defaultOrder).distinct()
        recentAppIds.clear()
        recentAppIds.addAll(combinedRecent)
        
        // Sync state from MusicService if it is running
        com.example.MusicService.instance?.let { service ->
            this.currentTrackIndex = com.example.MusicService.currentTrackIndex
            this.isMusicPlaying = com.example.MusicService.isMusicPlaying
            this.musicPlaybackPosition = com.example.MusicService.musicPlaybackPosition
            this.musicTotalDuration = com.example.MusicService.musicTotalDuration
            this.isMusicShuffle = com.example.MusicService.isMusicShuffle
            this.musicRepeatMode = com.example.MusicService.musicRepeatMode
            if (com.example.MusicService.musicPlaylist.isNotEmpty()) {
                this.musicPlaylist.clear()
                this.musicPlaylist.addAll(com.example.MusicService.musicPlaylist)
            }
        }

        startClock()
        loadRetrofitHolidays()
        checkAndCleanupExpiredAnggota()
        
        // Load cached Kemenag schedule if date matches today
        val cachedDate = sharedPrefs.getString("kemenag_cache_date", null)
        val today = Calendar.getInstance()
        val year = today.get(Calendar.YEAR)
        val month = String.format("%02d", today.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", today.get(Calendar.DAY_OF_MONTH))
        val todayStr = "$year-$month-$day"
        
        if (cachedDate == todayStr) {
            val imsak = sharedPrefs.getString("kemenag_imsak", null)
            val subuh = sharedPrefs.getString("kemenag_subuh", null)
            val dzuhur = sharedPrefs.getString("kemenag_dzuhur", null)
            val ashar = sharedPrefs.getString("kemenag_ashar", null)
            val maghrib = sharedPrefs.getString("kemenag_maghrib", null)
            val isya = sharedPrefs.getString("kemenag_isya", null)
            val city = sharedPrefs.getString("kemenag_city", null)
            
            if (imsak != null && subuh != null && dzuhur != null && ashar != null && maghrib != null && isya != null) {
                kemenagPrayerSchedule = com.example.ui.components.PrayerSchedule(imsak, subuh, dzuhur, ashar, maghrib, isya)
                activeKemenagCityName = city
            }
        }
        
        // Trigger Aladhan live API for today on initialization
        val formatStr = String.format("%02d-%02d-%04d", today.get(Calendar.DAY_OF_MONTH), today.get(Calendar.MONTH) + 1, today.get(Calendar.YEAR))
        loadLiveAladhanHijri(formatStr)

        if (musicFolderUriStr.isNotEmpty()) {
            scanMusicFolder(application)
        }
        
        fetchKemenagPrayerSchedule(application)
        fetchWeather()
        setupNetworkMonitoring()
    }

    fun toggleFavorite(track: MusicTrack) {
        if (favoriteTrackUris.contains(track.uriStr)) {
            favoriteTrackUris.remove(track.uriStr)
        } else {
            favoriteTrackUris.add(track.uriStr)
        }
        sharedPrefs.edit().putStringSet("music_favorites", favoriteTrackUris.toSet()).apply()
    }

    fun isFavorite(track: MusicTrack): Boolean {
        return favoriteTrackUris.contains(track.uriStr)
    }

    fun toggleMyPlaylist(track: MusicTrack) {
        if (myPlaylistTrackUris.contains(track.uriStr)) {
            myPlaylistTrackUris.remove(track.uriStr)
        } else {
            myPlaylistTrackUris.add(track.uriStr)
        }
        sharedPrefs.edit().putStringSet("music_my_playlist", myPlaylistTrackUris.toSet()).apply()
    }

    fun isInMyPlaylist(track: MusicTrack): Boolean {
        return myPlaylistTrackUris.contains(track.uriStr)
    }

    fun saveManualLyrics(uriStr: String, lyricsText: String) {
        sharedPrefs.edit().putString("lyrics_$uriStr", lyricsText).apply()
    }

    fun getManualLyrics(uriStr: String): String {
        return sharedPrefs.getString("lyrics_$uriStr", "") ?: ""
    }

    fun exportManualLyricsJson(): String {
        val allPrefs = sharedPrefs.all
        val lyricsMap = mutableMapOf<String, String>()
        for ((key, value) in allPrefs) {
            if (key.startsWith("lyrics_") && value is String) {
                lyricsMap[key] = value
            }
        }
        return org.json.JSONObject(lyricsMap as Map<*, *>).toString(4)
    }

    fun importManualLyricsJson(jsonStr: String): Boolean {
        return try {
            val jsonObject = org.json.JSONObject(jsonStr)
            val editor = sharedPrefs.edit()
            val keys = jsonObject.keys()
            var count = 0
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("lyrics_")) {
                    val value = jsonObject.optString(key, "")
                    editor.putString(key, value)
                    count++
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun saveCustomAlbumArt(uriStr: String, artUriStr: String) {
        sharedPrefs.edit().putString("album_art_$uriStr", artUriStr).apply()
    }

    fun getCustomAlbumArt(uriStr: String): String? {
        return sharedPrefs.getString("album_art_$uriStr", null)
    }

    private fun recordPlay(track: MusicTrack) {
        recentlyPlayed.removeAll { it.uriStr == track.uriStr }
        recentlyPlayed.add(0, track)
        if (recentlyPlayed.size > 20) {
            recentlyPlayed.removeAt(recentlyPlayed.lastIndex)
        }

        val currentCount = playCounts[track.uriStr] ?: 0
        playCounts[track.uriStr] = currentCount + 1

        val countSet = playCounts.map { "${it.key}:::${it.value}" }.toSet()
        sharedPrefs.edit().putStringSet("music_play_counts", countSet).apply()
    }

    fun clearPlayHistory() {
        recentlyPlayed.clear()
        playCounts.clear()
        sharedPrefs.edit().remove("music_play_counts").apply()
    }

    private var progressJob: kotlinx.coroutines.Job? = null

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (isMusicPlaying) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            musicPlaybackPosition = player.currentPosition
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                delay(500)
            }
        }
    }

    fun saveMusicFolderUri(uriStr: String) {
        sharedPrefs.edit().putString("music_folder_uri", uriStr).apply()
        musicFolderUriStr = uriStr
    }

    fun scanMusicFolder(context: Context) {
        isMusicLoading = musicPlaylist.isEmpty()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tracks = mutableListOf<MusicTrack>()
                val idCounterRef = arrayOf(1)

                // 1. Scan folder SAF jika pengguna telah memilih folder musik
                if (musicFolderUriStr.isNotEmpty()) {
                    val treeUri = Uri.parse(musicFolderUriStr)
                    val folder = DocumentFile.fromTreeUri(context, treeUri)
                    if (folder != null && folder.exists()) {
                        scanDirectoryRecursively(folder, tracks, idCounterRef)
                    }
                }

                // 2. Otomatis scan folder internal: /storage/emulated/0/My Singghara/Data/mp3
                try {
                    val mp3Folder = com.example.util.SinggharaFolderManager.getMp3Folder()
                    if (mp3Folder.exists()) {
                        val files = mp3Folder.listFiles { f -> f.isFile && f.name.endsWith(".mp3", ignoreCase = true) }
                        files?.forEach { f ->
                            val currentId = idCounterRef[0]
                            idCounterRef[0] = currentId + 1
                            tracks.add(
                                MusicTrack(
                                    id = "mysingghara_$currentId",
                                    title = f.nameWithoutExtension,
                                    artist = "My Singghara MP3",
                                    uriStr = Uri.fromFile(f).toString()
                                )
                            )
                        }
                    }
                } catch (ignored: Exception) {}

                viewModelScope.launch(Dispatchers.Main) {
                    musicPlaylist.clear()
                    musicPlaylist.addAll(tracks)
                    isMusicLoading = false
                }
            } catch (e: Exception) {
                viewModelScope.launch(Dispatchers.Main) {
                    isMusicLoading = false
                }
            }
        }
    }

    private fun scanDirectoryRecursively(
        folder: DocumentFile,
        tracks: MutableList<MusicTrack>,
        idCounterRef: Array<Int>
    ) {
        if (!folder.exists()) return
        try {
            val files = folder.listFiles()
            files.forEach { file ->
                if (file.isDirectory) {
                    scanDirectoryRecursively(file, tracks, idCounterRef)
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    val isAudio = name.endsWith(".mp3", ignoreCase = true) ||
                                  name.endsWith(".wav", ignoreCase = true) ||
                                  name.endsWith(".m4a", ignoreCase = true) ||
                                  name.endsWith(".aac", ignoreCase = true) ||
                                  name.endsWith(".ogg", ignoreCase = true) ||
                                  file.type?.startsWith("audio/") == true
                    
                    if (isAudio) {
                        val currentId = idCounterRef[0]
                        idCounterRef[0] = currentId + 1
                        tracks.add(
                            MusicTrack(
                                id = currentId.toString(),
                                title = name.substringBeforeLast("."),
                                artist = folder.name ?: "Folder Audio",
                                uriStr = file.uri.toString()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Safely skip folders with issues or no permissions
        }
    }

    private fun startMusicService(context: Context) {
        val intent = Intent(context, com.example.MusicService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun recordPlayExternal(track: MusicTrack) {
        recordPlay(track)
    }

    fun playTrack(context: Context, index: Int) {
        if (musicPlaylist.isEmpty()) return
        val safeIndex = index.coerceIn(0, musicPlaylist.lastIndex)
        currentTrackIndex = safeIndex

        startMusicService(context)
        
        com.example.MusicService.musicPlaylist.clear()
        com.example.MusicService.musicPlaylist.addAll(this.musicPlaylist)
        com.example.MusicService.musicRepeatMode = this.musicRepeatMode
        com.example.MusicService.isMusicShuffle = this.isMusicShuffle

        viewModelScope.launch {
            var retries = 0
            while (com.example.MusicService.instance == null && retries < 10) {
                delay(100)
                retries++
            }
            com.example.MusicService.instance?.let { service ->
                service.syncPlaylist(this@MainViewModel.musicPlaylist)
                service.playTrack(safeIndex)
            }
        }
    }

    fun playTrackDirect(context: Context, track: MusicTrack) {
        val index = musicPlaylist.indexOfFirst { it.uriStr == track.uriStr }
        if (index != -1) {
            playTrack(context, index)
        } else {
            musicPlaylist.add(track)
            playTrack(context, musicPlaylist.lastIndex)
        }
    }

    fun togglePlayPause() {
        com.example.MusicService.instance?.togglePlayPause() ?: run {
            if (musicPlaylist.isNotEmpty() && currentTrackIndex in musicPlaylist.indices) {
                val context = getApplication<Application>()
                startMusicService(context)
                viewModelScope.launch {
                    var retries = 0
                    while (com.example.MusicService.instance == null && retries < 10) {
                        delay(100)
                        retries++
                    }
                    com.example.MusicService.instance?.let { service ->
                        service.syncPlaylist(this@MainViewModel.musicPlaylist)
                        service.playTrack(currentTrackIndex)
                    }
                }
            }
        }
    }

    fun seekTo(positionMs: Int) {
        com.example.MusicService.instance?.seekTo(positionMs) ?: run {
            musicPlaybackPosition = positionMs
        }
    }

    fun playNext(context: Context) {
        com.example.MusicService.instance?.playNext()
    }

    fun playPrevious(context: Context) {
        com.example.MusicService.instance?.playPrevious()
    }

    fun getAlbumArtBitmap(context: Context, track: MusicTrack): Bitmap? {
        val customArt = getCustomAlbumArt(track.uriStr)
        if (customArt != null) {
            try {
                val uri = Uri.parse(customArt)
                context.contentResolver.openInputStream(uri).use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                // fallback
            }
        }
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(track.uriStr))
            val artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes != null) {
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            }
        } catch (e: Exception) {
            // fallback
        }
        try {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint()
            val shader = LinearGradient(0f, 0f, 200f, 200f, Color.rgb(33, 150, 243), Color.rgb(156, 39, 176), Shader.TileMode.CLAMP)
            paint.shader = shader
            canvas.drawRect(0f, 0f, 200f, 200f, paint)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    fun updateMusicNotification() {
        com.example.MusicService.instance?.updateMusicNotification()
    }

    override fun onCleared() {
        super.onCleared()
        instance = null
        try {
            networkCallback?.let { callback ->
                connectivityManager?.unregisterNetworkCallback(callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String = "Folder Audio",
    val uriStr: String,
    val durationMs: Long = 0L
)
