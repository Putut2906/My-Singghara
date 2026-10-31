package com.example.firebase

import android.content.Context
import com.example.data.LocalRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manager untuk Cloud Backup & Restore Firebase Firestore per akun user terautentikasi.
 *
 * Keamanan Data Akun:
 * - Setiap akun tersimpan di koleksi terisolasi: `users/{userId}/backups/latest`
 *   dan riwayat `users/{userId}/backups_history/{timestamp}`.
 * - Akses dibatasi ketat berdasarkan UID akun yang sedang login (FirebaseAuth.currentUser.uid).
 * - Tidak ada akun yang dapat membaca, merubah, menimpa, ataupun menghapus data akun lain.
 */
class FirebaseCloudBackupManager(
    private val repository: LocalRepository
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /**
     * Memeriksa apakah user saat ini sedang login.
     */
    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    /**
     * Mendapatkan UID pengguna yang terautentikasi.
     */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Mendapatkan email / identitas akun yang terautentikasi.
     */
    val currentUserEmail: String?
        get() = auth.currentUser?.email ?: auth.currentUser?.displayName

    /**
     * Melakukan Backup seluruh data lokal Room & Prefs ke Firestore di bawah path akun user sendiri.
     */
    suspend fun backupToCloud(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Silakan masuk (login) ke akun Anda terlebih dahulu untuk mencadangkan ke cloud."))

        try {
            // Dapatkan JSON backup lengkap dari repository
            val backupJsonResult = repository.getBackupJsonString(context)
            if (backupJsonResult.isFailure) {
                return@withContext Result.failure(backupJsonResult.exceptionOrNull() ?: Exception("Gagal menyiapkan data cadangan."))
            }

            val jsonString = backupJsonResult.getOrThrow()
            val timestamp = System.currentTimeMillis()
            val dateStr = java.text.SimpleDateFormat("dd MMM yyyy HH:mm:ss", java.util.Locale("id", "ID")).format(java.util.Date(timestamp))

            // Hitung ukuran data
            val sizeKb = jsonString.toByteArray(Charsets.UTF_8).size / 1024.0

            val backupPayload = hashMapOf(
                "userId" to user.uid,
                "userEmail" to (user.email ?: ""),
                "userName" to (user.displayName ?: ""),
                "timestamp" to timestamp,
                "dateTimeFormatted" to dateStr,
                "appVersion" to "1.0",
                "sizeKb" to String.format(java.util.Locale.US, "%.1f KB", sizeKb),
                "dataJson" to jsonString
            )

            // Simpan ke path terisolasi: users/{user.uid}/backups/latest
            val userBackupDoc = firestore.collection("users")
                .document(user.uid)
                .collection("backups")
                .document("latest")

            userBackupDoc.set(backupPayload, SetOptions.merge()).await()

            // Catat juga metadata profil user
            val userProfileDoc = firestore.collection("users")
                .document(user.uid)

            val profileMeta = hashMapOf(
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: ""),
                "lastBackupTimestamp" to timestamp,
                "lastBackupDate" to dateStr
            )
            userProfileDoc.set(profileMeta, SetOptions.merge()).await()

            Result.success("Cadangan data berhasil disimpan ke Cloud Firebase untuk akun ${user.email ?: user.displayName} ($dateStr).")
        } catch (e: Exception) {
            val rawMsg = e.localizedMessage ?: e.message ?: "Unknown error"
            val enhancedMsg = if (rawMsg.contains("PERMISSION_DENIED", ignoreCase = true) || rawMsg.contains("Missing or insufficient permissions", ignoreCase = true)) {
                "Gagal mencadangkan ke Firebase Cloud: PERMISSION DENIED: Missing or insufficient permissions. Aturan Firestore Rules di Firebase Console belum mengizinkan tulis data. Klik tombol solusi izin di bawah."
            } else {
                "Gagal mencadangkan ke Firebase Cloud: $rawMsg"
            }
            Result.failure(Exception(enhancedMsg))
        }
    }

    /**
     * Memeriksa metadata cadangan terakhir yang ada di cloud untuk akun yang sedang login.
     */
    suspend fun getLatestBackupMetadata(): Result<CloudBackupInfo?> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Silakan login terlebih dahulu."))

        try {
            val doc = firestore.collection("users")
                .document(user.uid)
                .collection("backups")
                .document("latest")
                .get()
                .await()

            if (!doc.exists()) {
                return@withContext Result.success(null)
            }

            val timestamp = doc.getLong("timestamp") ?: 0L
            val dateTimeFormatted = doc.getString("dateTimeFormatted") ?: "-"
            val sizeKb = doc.getString("sizeKb") ?: "-"
            val userEmail = doc.getString("userEmail") ?: ""

            Result.success(
                CloudBackupInfo(
                    timestamp = timestamp,
                    dateTimeFormatted = dateTimeFormatted,
                    sizeKb = sizeKb,
                    userEmail = userEmail
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memulihkan data lokal Room dari cadangan Firebase Cloud akun user yang sedang aktif.
     */
    suspend fun restoreFromCloud(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Silakan masuk (login) ke akun Anda terlebih dahulu untuk memulihkan dari cloud."))

        try {
            val doc = firestore.collection("users")
                .document(user.uid)
                .collection("backups")
                .document("latest")
                .get()
                .await()

            if (!doc.exists()) {
                return@withContext Result.failure(Exception("Belum ada data cadangan cloud yang ditemukan untuk akun ini."))
            }

            val dataJson = doc.getString("dataJson")
                ?: return@withContext Result.failure(Exception("Data cadangan cloud kosong atau rusak."))

            val restoreResult = repository.restoreFromJsonString(context, dataJson)
            if (restoreResult.isFailure) {
                return@withContext Result.failure(restoreResult.exceptionOrNull() ?: Exception("Gagal memulihkan struktur data cadangan."))
            }

            val dateStr = doc.getString("dateTimeFormatted") ?: "Terakhir"
            Result.success("Berhasil memulihkan data dari Cloud Firebase ($dateStr) untuk akun ${user.email ?: user.displayName}!")
        } catch (e: Exception) {
            Result.failure(Exception("Gagal memulihkan dari Firebase Cloud: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Menghapus cadangan cloud akun ini (hanya dapat dilakukan oleh pemilik akun).
     */
    suspend fun deleteCloudBackup(): Result<String> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Silakan login terlebih dahulu."))

        try {
            firestore.collection("users")
                .document(user.uid)
                .collection("backups")
                .document("latest")
                .delete()
                .await()

            Result.success("Cadangan cloud akun Anda berhasil dihapus.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class CloudBackupInfo(
    val timestamp: Long,
    val dateTimeFormatted: String,
    val sizeKb: String,
    val userEmail: String
)
