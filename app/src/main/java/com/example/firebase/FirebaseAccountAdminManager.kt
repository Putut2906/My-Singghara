package com.example.firebase

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Model Data Pengguna yang terdaftar di Firestore
 */
data class CloudUserItem(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val fcmToken: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val providerId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastActiveAt: Long = 0L,
    val isActive: Boolean = true,
    val isAdmin: Boolean = false,
    val membershipDurationMonths: Int = 0,
    val membershipStartDate: Long = 0L,
    val membershipEndDate: Long = 0L,
    val membershipTier: String = ""
) {
    /**
     * Memeriksa apakah pengguna memiliki durasi membership
     */
    fun hasMembership(): Boolean = membershipEndDate > 0L

    /**
     * Memeriksa apakah membership telah kedaluwarsa
     */
    fun isMembershipExpired(): Boolean {
        if (isAdmin) return false // Admin tidak pernah kedaluwarsa
        return membershipEndDate > 0L && System.currentTimeMillis() >= membershipEndDate
    }

    /**
     * Status aktif efektif (aktif dan membership belum habis)
     */
    fun isEffectivelyActive(): Boolean {
        if (isAdmin) return true
        return isActive && !isMembershipExpired()
    }

    /**
     * Menghitung sisa hari masa membership
     */
    fun getRemainingMembershipDays(): Long {
        if (membershipEndDate <= 0L) return 0L
        val diff = membershipEndDate - System.currentTimeMillis()
        return if (diff > 0) (diff / (1000 * 60 * 60 * 24)) else 0L
    }

    /**
     * Format tanggal berakhir masa membership
     */
    fun getFormattedMembershipEndDate(): String {
        if (membershipEndDate <= 0L) return "Tidak diatur"
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
        return sdf.format(java.util.Date(membershipEndDate))
    }

    /**
     * Format durasi membership
     */
    fun getFormattedMembershipDuration(): String {
        return if (membershipDurationMonths > 0) "$membershipDurationMonths Bulan" else if (membershipTier.isNotBlank()) membershipTier else "Tanpa Membership"
    }

    /**
     * Format ringkas status & sisa membership
     */
    fun getFormattedMembershipPeriod(): String {
        if (membershipDurationMonths <= 0 && membershipTier.isBlank()) return "Tanpa Membership"
        val tierName = if (membershipDurationMonths > 0) "$membershipDurationMonths Bulan" else membershipTier
        return if (isMembershipExpired()) {
            "$tierName (Kedaluwarsa)"
        } else if (hasMembership()) {
            val days = getRemainingMembershipDays()
            "$tierName (Sisa $days hari)"
        } else {
            tierName
        }
    }
    /**
     * Memeriksa apakah pengguna baru saja aktif (dalam 5 menit terakhir)
     */
    fun isRecentlyActive(): Boolean {
        val effectiveTime = if (lastActiveAt > 0L) lastActiveAt else updatedAt
        if (effectiveTime <= 0L) return false
        val diff = System.currentTimeMillis() - effectiveTime
        return diff in 0..(5 * 60 * 1000)
    }

    /**
     * Mengembalikan format teks ramah kapan pengguna terakhir mengakses aplikasi
     */
    fun getFormattedLastActive(): String {
        val effectiveTime = if (lastActiveAt > 0L) lastActiveAt else updatedAt
        if (effectiveTime <= 0L) return "Belum pernah mengakses"
        val now = System.currentTimeMillis()
        val diff = now - effectiveTime
        if (diff < 0) return "Baru saja"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 2 -> "Baru saja (Online)"
            minutes < 60 -> "$minutes menit lalu"
            hours < 24 -> "$hours jam lalu"
            days == 1L -> "Kemarin"
            days < 7 -> "$days hari lalu"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
                sdf.format(java.util.Date(effectiveTime))
            }
        }
    }

    /**
     * Mengembalikan tanggal lengkap terakhir akses
     */
    fun getFullDateTimeLastActive(): String {
        val effectiveTime = if (lastActiveAt > 0L) lastActiveAt else updatedAt
        if (effectiveTime <= 0L) return "-"
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale("id", "ID"))
        return sdf.format(java.util.Date(effectiveTime))
    }
}

/**
 * Model Pesan Siaran / Pesan Admin di Firestore
 */
data class CloudMessageItem(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "PENGUMUMAN", // PENGUMUMAN, INFORMASI, PEMBARUAN, PERINGATAN
    val priority: String = "NORMAL",     // NORMAL, TINGGI, MENDESAK
    val target: String = "SEMUA",        // SEMUA atau UID spesifik
    val targetName: String = "Semua Pengguna",
    val authorUid: String = "",
    val authorName: String = "",
    val createdAt: Long = 0L
)

/**
 * Model Draf Pesan untuk Pembuatan Pesan Sekaligus Banyak (Batch) atau Kustom
 */
data class CloudMessageDraft(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var content: String = "",
    var category: String = "PENGUMUMAN",
    var priority: String = "NORMAL",
    var target: String = "SEMUA",
    var targetName: String = "Semua Pengguna",
    var isSelected: Boolean = true
)

/**
 * Validasi Hak Akses Akun Pengguna (Otentikasi & Membership)
 */
sealed class AccountAccessValidation {
    object Allowed : AccountAccessValidation()
    data class Deactivated(val reason: String) : AccountAccessValidation()
    data class MembershipExpired(val endDate: Long, val tier: String) : AccountAccessValidation()
}

/**
 * Model Permintaan Kode OTP untuk Proteksi Ganti/Atur Sandi Email
 * Kode OTP dikirimkan ke Administrator Resmi (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2)
 */
data class AdminOtpRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val requesterUid: String = "",
    val requesterEmail: String = "",
    val requesterName: String = "",
    val otpCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (15 * 60 * 1000L), // 15 menit
    val status: String = "MENUNGGU", // MENUNGGU, DIGUNAKAN, KADALUARSA
    val actionType: String = "SET_PASSWORD_EMAIL",
    val adminUid: String = FirebaseAccountAdminManager.ADMIN_UID
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}

/**
 * Manager Manajemen Akun & Pesan Firestore khusus Administrator
 * UID Admin Terdaftar: UiDpHxawlBQ17mxTUHIXvhxRR3b2
 */
class FirebaseAccountAdminManager {

    companion object {
        /**
         * UID Administrator Resmi yang memiliki wewenang penuh
         */
        const val ADMIN_UID = "UiDpHxawlBQ17mxTUHIXvhxRR3b2"

        @Volatile
        private var instance: FirebaseAccountAdminManager? = null

        fun getInstance(): FirebaseAccountAdminManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAccountAdminManager().also { instance = it }
            }
        }
    }

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun getAdminPhoneNumber(context: Context): String {
        val sp = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
        return sp.getString("admin_phone_number", "088806643415") ?: "088806643415"
    }

    fun formatWhatsAppNumber(phone: String): String {
        val clean = phone.trim().replace("+", "").replace("-", "").replace(" ", "")
        return if (clean.startsWith("0")) "62" + clean.substring(1) else clean
    }

    /**
     * Memeriksa apakah pengguna tertentu merupakan Administrator resmi
     */
    fun isAdmin(uid: String?): Boolean {
        return uid != null && uid.trim() == ADMIN_UID
    }

    /**
     * Memeriksa apakah pengguna yang sedang login saat ini adalah Administrator
     */
    fun isCurrentSessionAdmin(): Boolean {
        return isAdmin(auth.currentUser?.uid)
    }

    /**
     * Menyimpan atau memperbarui Token FCM secara otomatis ke koleksi `tokens` dan `users`
     * Setiap kali pengguna mendaftar, masuk, atau token FCM baru di-generate.
     */
    suspend fun saveFcmTokenToFirestore(
        user: FirebaseUser,
        token: String,
        context: Context? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = user.uid.trim()
        val cleanToken = token.trim()
        if (uid.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("UID Pengguna kosong."))
        }

        try {
            val email = user.email ?: ""
            val displayName = user.displayName ?: email.substringBefore('@')
            val photoUrl = user.photoUrl?.toString() ?: ""
            val provider = user.providerData.firstOrNull()?.providerId ?: "firebase"
            val now = System.currentTimeMillis()
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val isUserAdmin = (uid == ADMIN_UID)

            // 1. Simpan ke koleksi 'tokens' (dokumen: tokens/{uid})
            val tokenDocData = hashMapOf(
                "uid" to uid,
                "token" to cleanToken,
                "email" to email,
                "displayName" to displayName,
                "photoUrl" to photoUrl,
                "providerId" to provider,
                "deviceModel" to deviceModel,
                "osVersion" to osVersion,
                "updatedAt" to now,
                "isAdmin" to isUserAdmin
            )

            firestore.collection("tokens")
                .document(uid)
                .set(tokenDocData, SetOptions.merge())
                .await()

            // 2. Simpan juga ke koleksi 'users' agar profil & token tersinkronisasi lengkap
            val userDocData = hashMapOf(
                "uid" to uid,
                "email" to email,
                "displayName" to displayName,
                "photoUrl" to photoUrl,
                "fcmToken" to cleanToken,
                "providerId" to provider,
                "deviceModel" to deviceModel,
                "osVersion" to osVersion,
                "lastActiveAt" to now,
                "updatedAt" to now,
                "isAdmin" to isUserAdmin
            )

            firestore.collection("users")
                .document(uid)
                .set(userDocData, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Mengambil daftar seluruh pengguna terdaftar dari koleksi `tokens` & `users` (Khusus Admin)
     */
    suspend fun getAllUsers(): Result<List<CloudUserItem>> = withContext(Dispatchers.IO) {
        val currentUid = auth.currentUser?.uid
        if (!isAdmin(currentUid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator (UID: $ADMIN_UID) yang dapat melihat daftar pengguna.")
            )
        }

        try {
            val userMap = mutableMapOf<String, CloudUserItem>()

            // 1. Ambil dari koleksi 'tokens'
            try {
                val tokensSnapshot = firestore.collection("tokens")
                    .get()
                    .await()

                val now = System.currentTimeMillis()

                for (doc in tokensSnapshot.documents) {
                    val uid = doc.getString("uid") ?: doc.id
                    val email = doc.getString("email") ?: ""
                    val displayName = doc.getString("displayName") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val token = doc.getString("token") ?: ""
                    val deviceModel = doc.getString("deviceModel") ?: ""
                    val osVersion = doc.getString("osVersion") ?: ""
                    val providerId = doc.getString("providerId") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val updatedAt = doc.getLong("updatedAt") ?: 0L
                    val lastActiveAt = doc.getLong("lastActiveAt") ?: updatedAt
                    val membershipDurationMonths = doc.getLong("membershipDurationMonths")?.toInt() ?: 0
                    val membershipStartDate = doc.getLong("membershipStartDate") ?: 0L
                    val membershipEndDate = doc.getLong("membershipEndDate") ?: 0L
                    val membershipTier = doc.getString("membershipTier") ?: ""
                    var isActive = doc.getBoolean("isActive") ?: !(doc.getBoolean("disabled") ?: false)
                    val isUserAdmin = doc.getBoolean("isAdmin") ?: (uid == ADMIN_UID)

                    // Otomatis nonaktifkan jika masa membership habis dan bukan admin
                    if (!isUserAdmin && membershipEndDate > 0L && now >= membershipEndDate && isActive) {
                        isActive = false
                        try {
                            val autoDisableData = hashMapOf<String, Any>(
                                "isActive" to false,
                                "disabled" to true,
                                "membershipExpired" to true,
                                "statusUpdatedAt" to now
                            )
                            firestore.collection("tokens").document(uid).set(autoDisableData, SetOptions.merge())
                            firestore.collection("users").document(uid).set(autoDisableData, SetOptions.merge())
                        } catch (_: Exception) {}
                    }

                    userMap[uid] = CloudUserItem(
                        uid = uid,
                        email = email,
                        displayName = displayName.ifEmpty { email.substringBefore('@') },
                        photoUrl = photoUrl,
                        fcmToken = token,
                        deviceModel = deviceModel,
                        osVersion = osVersion,
                        providerId = providerId,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        lastActiveAt = lastActiveAt,
                        isActive = isActive,
                        isAdmin = isUserAdmin,
                        membershipDurationMonths = membershipDurationMonths,
                        membershipStartDate = membershipStartDate,
                        membershipEndDate = membershipEndDate,
                        membershipTier = membershipTier
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Ambil dari koleksi 'users' untuk melengkapi jika ada yang belum di tokens
            try {
                val usersSnapshot = firestore.collection("users")
                    .get()
                    .await()

                val now = System.currentTimeMillis()

                for (doc in usersSnapshot.documents) {
                    val uid = doc.getString("uid") ?: doc.id
                    val existing = userMap[uid]

                    val email = doc.getString("email") ?: existing?.email ?: ""
                    val displayName = doc.getString("displayName") ?: existing?.displayName ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: existing?.photoUrl ?: ""
                    val fcmToken = doc.getString("fcmToken") ?: existing?.fcmToken ?: ""
                    val deviceModel = doc.getString("deviceModel") ?: existing?.deviceModel ?: ""
                    val osVersion = doc.getString("osVersion") ?: existing?.osVersion ?: ""
                    val providerId = doc.getString("providerId") ?: existing?.providerId ?: ""
                    val updatedAt = doc.getLong("updatedAt") ?: existing?.updatedAt ?: 0L
                    val lastActiveAt = doc.getLong("lastActiveAt") ?: doc.getLong("updatedAt") ?: existing?.lastActiveAt ?: 0L
                    val membershipDurationMonths = doc.getLong("membershipDurationMonths")?.toInt() ?: existing?.membershipDurationMonths ?: 0
                    val membershipStartDate = doc.getLong("membershipStartDate") ?: existing?.membershipStartDate ?: 0L
                    val membershipEndDate = doc.getLong("membershipEndDate") ?: existing?.membershipEndDate ?: 0L
                    val membershipTier = doc.getString("membershipTier") ?: existing?.membershipTier ?: ""
                    var isActive = doc.getBoolean("isActive") ?: existing?.isActive ?: !(doc.getBoolean("disabled") ?: false)
                    val isUserAdmin = doc.getBoolean("isAdmin") ?: (uid == ADMIN_UID)

                    // Otomatis nonaktifkan jika masa membership habis dan bukan admin
                    if (!isUserAdmin && membershipEndDate > 0L && now >= membershipEndDate && isActive) {
                        isActive = false
                        try {
                            val autoDisableData = hashMapOf<String, Any>(
                                "isActive" to false,
                                "disabled" to true,
                                "membershipExpired" to true,
                                "statusUpdatedAt" to now
                            )
                            firestore.collection("users").document(uid).set(autoDisableData, SetOptions.merge())
                            firestore.collection("tokens").document(uid).set(autoDisableData, SetOptions.merge())
                        } catch (_: Exception) {}
                    }

                    userMap[uid] = CloudUserItem(
                        uid = uid,
                        email = email,
                        displayName = displayName.ifEmpty { email.substringBefore('@') },
                        photoUrl = photoUrl,
                        fcmToken = fcmToken,
                        deviceModel = deviceModel,
                        osVersion = osVersion,
                        providerId = providerId,
                        createdAt = existing?.createdAt ?: updatedAt,
                        updatedAt = updatedAt,
                        lastActiveAt = lastActiveAt,
                        isActive = isActive,
                        isAdmin = isUserAdmin,
                        membershipDurationMonths = membershipDurationMonths,
                        membershipStartDate = membershipStartDate,
                        membershipEndDate = membershipEndDate,
                        membershipTier = membershipTier
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Jika koleksi cloud masih kosong, minimal cantumkan akun yang sedang login saat ini
            if (userMap.isEmpty() && auth.currentUser != null) {
                val u = auth.currentUser!!
                val nowTime = System.currentTimeMillis()
                userMap[u.uid] = CloudUserItem(
                    uid = u.uid,
                    email = u.email ?: "",
                    displayName = u.displayName ?: (u.email ?: "Administrator"),
                    photoUrl = u.photoUrl?.toString() ?: "",
                    fcmToken = "Aktif di sesi perangkat",
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    osVersion = "Android ${Build.VERSION.RELEASE}",
                    providerId = u.providerData.firstOrNull()?.providerId ?: "firebase",
                    createdAt = nowTime,
                    updatedAt = nowTime,
                    lastActiveAt = nowTime,
                    isActive = true,
                    isAdmin = (u.uid == ADMIN_UID),
                    membershipTier = if (u.uid == ADMIN_UID) "Akses Penuh Admin" else "Bebas"
                )
            }

            // Urutkan: Admin di paling atas, lalu berdasarkan akses terakhir / pembaruan terbaru
            val sortedList = userMap.values.sortedWith(
                compareByDescending<CloudUserItem> { it.isAdmin }
                    .thenByDescending { if (it.lastActiveAt > 0L) it.lastActiveAt else it.updatedAt }
            )

            Result.success(sortedList)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Mengaktifkan atau menonaktifkan akun pengguna di Cloud Firestore (Khusus Admin)
     */
    suspend fun setUserActiveStatus(targetUid: String, isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUid = auth.currentUser?.uid
        if (!isAdmin(currentUid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator (UID: $ADMIN_UID) yang dapat mengubah status akun pengguna.")
            )
        }

        if (targetUid == ADMIN_UID && !isActive) {
            return@withContext Result.failure(
                IllegalArgumentException("Akun Administrator Utama tidak dapat dinonaktifkan.")
            )
        }

        try {
            val now = System.currentTimeMillis()
            val updateData = hashMapOf<String, Any>(
                "isActive" to isActive,
                "disabled" to !isActive,
                "statusUpdatedAt" to now
            )
            // Jika admin mengaktifkan akun kembali, reset flag membershipExpired
            if (isActive) {
                updateData["membershipExpired"] = false
            }

            // Update di koleksi 'users'
            firestore.collection("users").document(targetUid)
                .set(updateData, SetOptions.merge())
                .await()

            // Update di koleksi 'tokens'
            firestore.collection("tokens").document(targetUid)
                .set(updateData, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Mengatur paket membership untuk pengguna (pilihan rentan waktu: 1, 3, 5, 7, atau 12 bulan, atau custom tanggal berakhir)
     * Jika waktu habis, akun akan otomatis dinonaktifkan di sistem.
     */
    suspend fun setUserMembership(
        targetUid: String,
        months: Int,
        activateAccount: Boolean = true,
        customEndDate: Long? = null,
        customTierName: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUid = auth.currentUser?.uid
        if (!isAdmin(currentUid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator (UID: $ADMIN_UID) yang dapat mengatur masa membership pengguna.")
            )
        }

        try {
            val now = System.currentTimeMillis()
            val updateData = hashMapOf<String, Any>(
                "updatedAt" to now,
                "statusUpdatedAt" to now
            )

            if (customEndDate != null && customEndDate > 0L) {
                val endDate = customEndDate
                val diff = endDate - now
                val remainingDays = if (diff > 0) (diff / (1000 * 60 * 60 * 24)) else 0L
                val approxMonths = ((remainingDays + 15) / 30).toInt().coerceAtLeast(1)
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id", "ID"))
                val tier = customTierName ?: "Custom (s.d ${sdf.format(java.util.Date(endDate))})"

                updateData["membershipDurationMonths"] = approxMonths
                updateData["membershipStartDate"] = now
                updateData["membershipEndDate"] = endDate
                updateData["membershipTier"] = tier
                updateData["membershipExpired"] = (now >= endDate)
                updateData["isActive"] = activateAccount
                updateData["disabled"] = !activateAccount
            } else if (months > 0) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    add(java.util.Calendar.MONTH, months)
                }
                val endDate = cal.timeInMillis
                val tier = "$months Bulan"

                updateData["membershipDurationMonths"] = months
                updateData["membershipStartDate"] = now
                updateData["membershipEndDate"] = endDate
                updateData["membershipTier"] = tier
                updateData["membershipExpired"] = false
                updateData["isActive"] = activateAccount
                updateData["disabled"] = !activateAccount
            } else {
                // Non-member / reset
                updateData["membershipDurationMonths"] = 0
                updateData["membershipStartDate"] = 0L
                updateData["membershipEndDate"] = 0L
                updateData["membershipTier"] = "Bebas / Lifetime"
                updateData["membershipExpired"] = false
                updateData["isActive"] = activateAccount
                updateData["disabled"] = !activateAccount
            }

            // Simpan di koleksi 'users'
            firestore.collection("users").document(targetUid)
                .set(updateData, SetOptions.merge())
                .await()

            // Simpan di koleksi 'tokens'
            firestore.collection("tokens").document(targetUid)
                .set(updateData, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Memvalidasi status aktif akun & masa membership pengguna secara real-time.
     * Digunakan saat proses login dan saat aplikasi dibuka.
     * Jika akun dinonaktifkan atau masa membership habis, akses langsung diblokir.
     */
    suspend fun validateAccountAccess(uid: String): AccountAccessValidation = withContext(Dispatchers.IO) {
        if (isAdmin(uid)) {
            return@withContext AccountAccessValidation.Allowed
        }

        try {
            var isActive = true
            var membershipEndDate = 0L
            var membershipTier = ""
            var durationMonths = 0

            // 1. Cek di koleksi 'users'
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                isActive = userDoc.getBoolean("isActive") ?: !(userDoc.getBoolean("disabled") ?: false)
                membershipEndDate = userDoc.getLong("membershipEndDate") ?: 0L
                membershipTier = userDoc.getString("membershipTier") ?: ""
                durationMonths = userDoc.getLong("membershipDurationMonths")?.toInt() ?: 0
            } else {
                // Fallback cek di koleksi 'tokens'
                val tokenDoc = firestore.collection("tokens").document(uid).get().await()
                if (tokenDoc.exists()) {
                    isActive = tokenDoc.getBoolean("isActive") ?: !(tokenDoc.getBoolean("disabled") ?: false)
                    membershipEndDate = tokenDoc.getLong("membershipEndDate") ?: 0L
                    membershipTier = tokenDoc.getString("membershipTier") ?: ""
                    durationMonths = tokenDoc.getLong("membershipDurationMonths")?.toInt() ?: 0
                }
            }

            val now = System.currentTimeMillis()

            // A. Cek apakah masa membership telah habis
            if (membershipEndDate > 0L && now >= membershipEndDate) {
                // Otomatis nonaktifkan akun di Firestore jika belum dinonaktifkan
                if (isActive) {
                    try {
                        val autoDisableData = hashMapOf<String, Any>(
                            "isActive" to false,
                            "disabled" to true,
                            "membershipExpired" to true,
                            "statusUpdatedAt" to now
                        )
                        firestore.collection("users").document(uid).set(autoDisableData, SetOptions.merge())
                        firestore.collection("tokens").document(uid).set(autoDisableData, SetOptions.merge())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val tierStr = if (membershipTier.isNotBlank()) membershipTier else if (durationMonths > 0) "$durationMonths Bulan" else "Membership"
                return@withContext AccountAccessValidation.MembershipExpired(
                    endDate = membershipEndDate,
                    tier = tierStr
                )
            }

            // B. Cek apakah akun dinonaktifkan oleh Administrator
            if (!isActive) {
                return@withContext AccountAccessValidation.Deactivated(
                    "Akun Anda telah dinonaktifkan oleh Administrator. Silakan hubungi admin untuk aktivasi kembali."
                )
            }

            AccountAccessValidation.Allowed
        } catch (e: Exception) {
            e.printStackTrace()
            // Jika terjadi kesalahan jaringan atau offline, kembalikan Allowed agar tidak memblokir pengguna normal saat offline
            AccountAccessValidation.Allowed
        }
    }

    /**
     * Mencatat waktu akses aktif pengguna saat membuka aplikasi
     */
    suspend fun recordUserAccess(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.success(Unit)
        try {
            val now = System.currentTimeMillis()
            val data = hashMapOf<String, Any>(
                "lastActiveAt" to now,
                "updatedAt" to now
            )
            firestore.collection("users").document(user.uid)
                .set(data, SetOptions.merge())
                .await()
            firestore.collection("tokens").document(user.uid)
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil seluruh pesan dari koleksi `messages`
     */
    suspend fun getAllMessages(): Result<List<CloudMessageItem>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val list = mutableListOf<CloudMessageItem>()
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

                list.add(
                    CloudMessageItem(
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
            Result.success(list)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Menulis pesan baru ke koleksi `messages`
     * ATURAN: HANYA ADMIN (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2) yang dapat menulis pesan!
     */
    suspend fun createMessage(
        title: String,
        content: String,
        category: String,
        priority: String,
        target: String = "SEMUA",
        targetName: String = "Semua Pengguna"
    ): Result<CloudMessageItem> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null || !isAdmin(user.uid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator (UID: $ADMIN_UID) yang dapat menulis pesan.")
            )
        }

        if (title.isBlank() || content.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Judul dan isi pesan tidak boleh kosong."))
        }

        try {
            val messageId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val now = System.currentTimeMillis()
            val authorName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: "Admin Singghara"

            val messageData = hashMapOf(
                "id" to messageId,
                "title" to title.trim(),
                "content" to content.trim(),
                "category" to category.uppercase(),
                "priority" to priority.uppercase(),
                "target" to target.trim(),
                "targetName" to targetName.trim(),
                "authorUid" to user.uid,
                "authorName" to authorName,
                "createdAt" to now
            )

            firestore.collection("messages")
                .document(messageId)
                .set(messageData)
                .await()

            val created = CloudMessageItem(
                id = messageId,
                title = title.trim(),
                content = content.trim(),
                category = category.uppercase(),
                priority = priority.uppercase(),
                target = target.trim(),
                targetName = targetName.trim(),
                authorUid = user.uid,
                authorName = authorName,
                createdAt = now
            )
            Result.success(created)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Menulis banyak pesan sekaligus (Batch Broadcast) ke koleksi `messages` Firestore
     * ATURAN: HANYA ADMIN (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2) yang dapat menulis pesan!
     */
    suspend fun createBatchMessages(
        drafts: List<CloudMessageDraft>
    ): Result<Int> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null || !isAdmin(user.uid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator (UID: $ADMIN_UID) yang dapat menulis pesan.")
            )
        }

        val validDrafts = drafts.filter { it.title.isNotBlank() && it.content.isNotBlank() }
        if (validDrafts.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Tidak ada draf pesan valid yang dapat dikirim."))
        }

        try {
            val authorName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: "Admin Singghara"
            val baseTime = System.currentTimeMillis()
            var sentCount = 0

            // Firestore batch maksimal 500 operasi per batch
            validDrafts.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEachIndexed { index, draft ->
                    val messageId = "msg_${baseTime + index}_${UUID.randomUUID().toString().take(6)}"
                    val messageData = hashMapOf(
                        "id" to messageId,
                        "title" to draft.title.trim(),
                        "content" to draft.content.trim(),
                        "category" to draft.category.uppercase(),
                        "priority" to draft.priority.uppercase(),
                        "target" to draft.target.trim(),
                        "targetName" to draft.targetName.trim(),
                        "authorUid" to user.uid,
                        "authorName" to authorName,
                        "createdAt" to (baseTime + index)
                    )
                    val docRef = firestore.collection("messages").document(messageId)
                    batch.set(docRef, messageData)
                    sentCount++
                }
                batch.commit().await()
            }

            Result.success(sentCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Menyediakan daftar template pengumuman / informasi resmi bawaan yang siap pakai (Custom / Batch)
     */
    fun getDefaultBroadcastTemplates(): List<CloudMessageDraft> {
        return listOf(
            CloudMessageDraft(
                title = "Selamat Datang di Aplikasi My Singghara",
                content = "Selamat datang di aplikasi My Singghara! Aplikasi ini siap membantu pengelolaan absensi barcode, inventaris, kalender, agenda, catatan, serta manajemen data harian Anda dengan mudah dan aman.",
                category = "PENGUMUMAN",
                priority = "NORMAL",
                isSelected = true
            ),
            CloudMessageDraft(
                title = "Pembaruan Fitur Terbaru & Performa",
                content = "Sistem telah diperbarui dengan peningkatan kestabilan sinkronisasi cloud, responsivitas navigasi, dan optimalisasi tampilan antarmuka untuk pengalaman pengguna yang lebih lancar.",
                category = "PEMBARUAN",
                priority = "NORMAL",
                isSelected = true
            ),
            CloudMessageDraft(
                title = "Pengingat Cadangkan & Sinkronkan Data",
                content = "Demi menjaga keamanan data Anda, silakan lakukan pencadangan (backup) berkala ke Google Drive atau ekspor arsip ZIP melalui menu Share / Pengaturan.",
                category = "INFORMASI",
                priority = "NORMAL",
                isSelected = true
            ),
            CloudMessageDraft(
                title = "Jadwal Pemeliharaan Server Cloud",
                content = "Diberitahukan bahwa akan dilakukan pemeliharaan server secara berkala pada akhir pekan. Fitur sinkronisasi cloud mungkin mengalami jeda singkat, namun seluruh fitur offline tetap berfungsi 100%.",
                category = "INFORMASI",
                priority = "NORMAL",
                isSelected = true
            ),
            CloudMessageDraft(
                title = "Himbauan Keamanan Kata Sandi & Akun",
                content = "Pastikan Anda tidak membagikan kata sandi akun Firebase kepada pihak manapun. Gunakan kata sandi yang kuat dan perbarui secara berkala demi menjaga privasi data Anda.",
                category = "PERINGATAN",
                priority = "TINGGI",
                isSelected = true
            ),
            CloudMessageDraft(
                title = "Layanan Bantuan & Kontak Administrator",
                content = "Apabila Anda membutuhkan bantuan teknis atau menemukan kendala dalam penggunaan aplikasi, silakan hubungi Administrator melalui kontak resmi yang tertera di menu Profil.",
                category = "INFORMASI",
                priority = "NORMAL",
                isSelected = true
            )
        )
    }

    /**
     * Menghapus pesan secara permanen dari koleksi `messages` Firestore
     * ATURAN: HANYA ADMIN TERDAFTAR (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2) yang dapat menghapus pesan permanen!
     */
    suspend fun deleteMessage(messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null || !isAdmin(user.uid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator Resmi (UID: $ADMIN_UID) yang dapat menghapus pesan secara permanen.")
            )
        }

        try {
            firestore.collection("messages")
                .document(messageId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Menghapus seluruh pesan secara permanen dari koleksi `messages` Firestore sekaligus
     * ATURAN: HANYA ADMIN TERDAFTAR (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2) yang dapat menghapus semua pesan!
     */
    suspend fun deleteAllMessagesPermanently(): Result<Int> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null || !isAdmin(user.uid)) {
            return@withContext Result.failure(
                SecurityException("Akses Ditolak: Hanya Administrator Resmi (UID: $ADMIN_UID) yang dapat menghapus seluruh pesan secara permanen.")
            )
        }

        try {
            val snapshot = firestore.collection("messages").get().await()
            val batch = firestore.batch()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(snapshot.size())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Membuat permintaan kode OTP untuk perubahan sandi email.
     * Kode 6-digit dikirimkan ke Admin (UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2).
     */
    suspend fun createEmailPasswordOtpRequest(
        requesterUid: String,
        requesterEmail: String,
        requesterName: String,
        context: Context? = null
    ): Result<AdminOtpRequest> = withContext(Dispatchers.IO) {
        try {
            val randomCode = String.format(java.util.Locale.US, "%06d", (100000..999999).random())
            val now = System.currentTimeMillis()
            val expiry = now + (15 * 60 * 1000L) // 15 menit
            val id = java.util.UUID.randomUUID().toString()

            val request = AdminOtpRequest(
                id = id,
                requesterUid = requesterUid,
                requesterEmail = requesterEmail,
                requesterName = requesterName,
                otpCode = randomCode,
                createdAt = now,
                expiresAt = expiry,
                status = "MENUNGGU",
                actionType = "SET_PASSWORD_EMAIL",
                adminUid = ADMIN_UID
            )

            val docData = hashMapOf(
                "id" to request.id,
                "requesterUid" to request.requesterUid,
                "requesterEmail" to request.requesterEmail,
                "requesterName" to request.requesterName,
                "otpCode" to request.otpCode,
                "createdAt" to request.createdAt,
                "expiresAt" to request.expiresAt,
                "status" to request.status,
                "actionType" to request.actionType,
                "adminUid" to request.adminUid
            )

            // Simpan ke Firestore koleksi otp_requests
            try {
                firestore.collection("otp_requests").document(request.id).set(docData).await()
            } catch (e: Exception) {
                // Cadangkan ke subkoleksi users jika aturan spesifik
                try {
                    firestore.collection("users").document(requesterUid).collection("otp_requests").document(request.id).set(docData).await()
                } catch (_: Exception) {}
            }

            // Simpan juga ke cache lokal agar langsung sinkron di perangkat yang sama / offline
            if (context != null) {
                saveOtpRequestToLocal(context, request)
            }

            Result.success(request)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Memverifikasi kode OTP yang dimasukkan oleh pengguna dengan OTP yang dikirimkan ke Admin.
     */
    suspend fun verifyEmailPasswordOtp(
        requesterUid: String,
        enteredCode: String,
        context: Context? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanCode = enteredCode.trim()
        if (cleanCode.length != 6) {
            return@withContext Result.failure(IllegalArgumentException("Kode OTP harus 6 digit angka."))
        }

        try {
            // Cek di Firestore otp_requests
            var matchedRequest: AdminOtpRequest? = null
            try {
                val snapshot = firestore.collection("otp_requests")
                    .whereEqualTo("requesterUid", requesterUid)
                    .whereEqualTo("status", "MENUNGGU")
                    .get()
                    .await()

                for (doc in snapshot.documents) {
                    val code = doc.getString("otpCode") ?: ""
                    val expiresAt = doc.getLong("expiresAt") ?: Long.MAX_VALUE
                    if (code == cleanCode && System.currentTimeMillis() <= expiresAt) {
                        matchedRequest = AdminOtpRequest(
                            id = doc.id,
                            requesterUid = requesterUid,
                            requesterEmail = doc.getString("requesterEmail") ?: "",
                            requesterName = doc.getString("requesterName") ?: "",
                            otpCode = code,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            expiresAt = expiresAt,
                            status = "MENUNGGU",
                            actionType = doc.getString("actionType") ?: "SET_PASSWORD_EMAIL",
                            adminUid = doc.getString("adminUid") ?: ADMIN_UID
                        )
                        break
                    }
                }
            } catch (_: Exception) {}

            // Cek fallback ke local cache
            if (matchedRequest == null && context != null) {
                val localList = getLocalOtpRequests(context)
                matchedRequest = localList.find {
                    it.requesterUid == requesterUid && it.otpCode == cleanCode && it.status == "MENUNGGU" && !it.isExpired
                }
            }

            if (matchedRequest != null) {
                // Tandai sebagai DIGUNAKAN
                markOtpAsUsed(matchedRequest.id, context)
                Result.success(true)
            } else {
                Result.failure(IllegalArgumentException("Kode OTP salah atau sudah kadaluarsa. Pastikan Anda memasukkan kode verifikasi 6-digit dari Admin."))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Mengambil semua daftar permintaan kode OTP (Khusus Admin UID: UiDpHxawlBQ17mxTUHIXvhxRR3b2).
     */
    suspend fun getAllOtpRequests(context: Context? = null): Result<List<AdminOtpRequest>> = withContext(Dispatchers.IO) {
        val listMap = mutableMapOf<String, AdminOtpRequest>()
        // 1. Ambil dari lokal cache terlebih dahulu
        if (context != null) {
            getLocalOtpRequests(context).forEach { listMap[it.id] = it }
        }

        // 2. Ambil dari Firestore
        try {
            val snapshot = firestore.collection("otp_requests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val id = doc.id
                val req = AdminOtpRequest(
                    id = id,
                    requesterUid = doc.getString("requesterUid") ?: "",
                    requesterEmail = doc.getString("requesterEmail") ?: "",
                    requesterName = doc.getString("requesterName") ?: "",
                    otpCode = doc.getString("otpCode") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    expiresAt = doc.getLong("expiresAt") ?: (System.currentTimeMillis() + 15 * 60 * 1000L),
                    status = doc.getString("status") ?: "MENUNGGU",
                    actionType = doc.getString("actionType") ?: "SET_PASSWORD_EMAIL",
                    adminUid = doc.getString("adminUid") ?: ADMIN_UID
                )
                listMap[id] = req
            }
        } catch (_: Exception) {}

        val resultList = listMap.values.sortedByDescending { it.createdAt }
        Result.success(resultList)
    }

    /**
     * Menghapus permintaan OTP
     */
    suspend fun deleteOtpRequest(otpId: String, context: Context? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("otp_requests").document(otpId).delete().await()
        } catch (_: Exception) {}

        if (context != null) {
            val list = getLocalOtpRequests(context).filter { it.id != otpId }
            saveAllOtpRequestsToLocal(context, list)
        }
        Result.success(Unit)
    }

    /**
     * Menandai permintaan OTP sebagai telah digunakan
     */
    suspend fun markOtpAsUsed(otpId: String, context: Context? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("otp_requests").document(otpId)
                .update("status", "DIGUNAKAN")
                .await()
        } catch (_: Exception) {}

        if (context != null) {
            val list = getLocalOtpRequests(context).map {
                if (it.id == otpId) it.copy(status = "DIGUNAKAN") else it
            }
            saveAllOtpRequestsToLocal(context, list)
        }
        Result.success(Unit)
    }

    private fun getLocalOtpRequests(context: Context): List<AdminOtpRequest> {
        val sp = context.getSharedPreferences("admin_otp_prefs", Context.MODE_PRIVATE)
        val jsonStr = sp.getString("otp_list_json", "[]") ?: "[]"
        val list = mutableListOf<AdminOtpRequest>()
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AdminOtpRequest(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        requesterUid = obj.optString("requesterUid", ""),
                        requesterEmail = obj.optString("requesterEmail", ""),
                        requesterName = obj.optString("requesterName", ""),
                        otpCode = obj.optString("otpCode", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        expiresAt = obj.optLong("expiresAt", System.currentTimeMillis() + 15 * 60 * 1000L),
                        status = obj.optString("status", "MENUNGGU"),
                        actionType = obj.optString("actionType", "SET_PASSWORD_EMAIL"),
                        adminUid = obj.optString("adminUid", ADMIN_UID)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveOtpRequestToLocal(context: Context, request: AdminOtpRequest) {
        val list = getLocalOtpRequests(context).toMutableList()
        val index = list.indexOfFirst { it.id == request.id }
        if (index >= 0) {
            list[index] = request
        } else {
            list.add(0, request)
        }
        saveAllOtpRequestsToLocal(context, list)
    }

    private fun saveAllOtpRequestsToLocal(context: Context, list: List<AdminOtpRequest>) {
        val sp = context.getSharedPreferences("admin_otp_prefs", Context.MODE_PRIVATE)
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

    /**
     * Teks Aturan Rules Firebase Firestore resmi yang membatasi hak akses:
     * - Hanya Admin (UiDpHxawlBQ17mxTUHIXvhxRR3b2) yang dapat menulis dan menghapus di koleksi messages.
     * - Koleksi tokens untuk penyimpanan otomatis token FCM.
     * - Koleksi otp_requests untuk proteksi verifikasi sandi email.
     */
    fun getFirestoreSecurityRulesCode(): String {
        return """
// =========================================================================
// ATURAN KEAMANAN FIREBASE FIRESTORE (MY SINGGHARA)
// UID Administrator Resmi: UiDpHxawlBQ17mxTUHIXvhxRR3b2
// =========================================================================

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Verifikasi apakah akun adalah Administrator Resmi
    function isAdmin() {
      return request.auth != null && request.auth.uid == '$ADMIN_UID';
    }

    // Verifikasi apakah pengguna telah terautentikasi (login)
    function isAuthenticated() {
      return request.auth != null;
    }

    // 1. KOLEKSI PESAN (messages):
    // - Seluruh pengguna yang login dapat membaca pesan.
    // - HANYA ADMIN ($ADMIN_UID) yang dapat MENULIS & MENGHAPUS pesan!
    match /messages/{messageId} {
      allow read: if isAuthenticated();
      allow create, update, delete: if isAdmin();
    }

    // 2. KOLEKSI TOKEN FCM (tokens):
    // - Pengguna dapat menyimpan/memperbarui token FCM perangkat mereka sendiri saat login/daftar.
    // - Admin memiliki hak baca/tulis penuh ke seluruh token untuk manajemen siaran notifikasi.
    match /tokens/{userId} {
      allow read: if isAdmin() || (isAuthenticated() && request.auth.uid == userId);
      allow write: if isAuthenticated() && (request.auth.uid == userId || isAdmin());
    }

    // 3. KOLEKSI DATA PENGGUNA (users):
    // - Pengguna dapat mengelola profil & backup cloud mereka sendiri.
    // - Admin dapat membaca daftar pengguna di menu Manajemen Akun.
    match /users/{userId} {
      allow read: if isAdmin() || (isAuthenticated() && request.auth.uid == userId);
      allow write: if isAuthenticated() && (request.auth.uid == userId || isAdmin());

      // Cadangan Cloud Room & Prefs pengguna
      match /backups/{document=**} {
        allow read, write: if isAuthenticated() && (request.auth.uid == userId || isAdmin());
      }
      match /backups_history/{document=**} {
        allow read, write: if isAuthenticated() && (request.auth.uid == userId || isAdmin());
      }
    }

    // 4. KOLEKSI PERMINTAAN KODE OTP (otp_requests):
    // - Pengguna dapat mengajukan permintaan verifikasi sandi email.
    // - Pengguna dan Admin dapat membaca & memverifikasi kode OTP.
    match /otp_requests/{otpId} {
      allow read: if isAuthenticated() && (resource.data.requesterUid == request.auth.uid || isAdmin());
      allow create: if isAuthenticated();
      allow update, delete: if isAuthenticated() && (resource.data.requesterUid == request.auth.uid || isAdmin());
    }
  }
}
        """.trimIndent()
    }
}
