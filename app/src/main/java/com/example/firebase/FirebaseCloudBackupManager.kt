package com.example.firebase

import android.content.Context
import com.example.data.LocalRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Manager untuk Cloud Backup & Restore Firebase Firestore per akun user terautentikasi.
 *
 * Keamanan Data Akun:
 * - Setiap akun tersimpan di koleksi terisolasi: `users/{userId}/backups/latest`
 *   dengan potongan data di subkoleksi `users/{userId}/backups/latest/chunks`.
 * - Akses dibatasi ketat berdasarkan UID akun yang sedang login (FirebaseAuth.currentUser.uid).
 * - Tidak ada akun yang dapat membaca, merubah, menimpa, ataupun menghapus data akun lain.
 *
 * Batas 1 MiB per dokumen Firestore diatasi dengan memecah JSON ke beberapa dokumen chunk.
 * Metadata hanya ditukar setelah semua chunk baru selesai ditulis, sehingga cadangan lama
 * tetap utuh bila proses di tengah gagal.
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
            val dataBytes = jsonString.toByteArray(Charsets.UTF_8)
            val sizeLabel = if (dataBytes.size >= 1024 * 1024) {
                String.format(Locale.US, "%.1f MB", dataBytes.size / 1048576.0)
            } else {
                String.format(Locale.US, "%.1f KB", dataBytes.size / 1024.0)
            }

            val backupDoc = userBackupDoc(user.uid)
            val chunksRef = backupDoc.collection("chunks")
            val previousVersion = backupDoc.get().await().let { doc ->
                if (doc.exists()) doc.getLong("chunkVersion") else null
            }

            val chunks = splitIntoChunks(jsonString, MAX_CHUNK_BYTES)

            // 1. Tulis seluruh chunk versi baru terlebih dahulu.
            writeChunks(chunksRef, timestamp, chunks)

            // 2. Tukar metadata setelah chunk lengkap, agar cadangan lama tidak rusak saat gagal.
            val backupPayload = hashMapOf(
                "userId" to user.uid,
                "userEmail" to (user.email ?: ""),
                "userName" to (user.displayName ?: ""),
                "timestamp" to timestamp,
                "dateTimeFormatted" to dateStr,
                "appVersion" to com.example.BuildConfig.VERSION_NAME,
                "sizeKb" to sizeLabel,
                "dataSizeBytes" to dataBytes.size,
                "chunkCount" to chunks.size,
                "chunkVersion" to timestamp
            )
            backupDoc.set(backupPayload).await()

            // 3. Bersihkan chunk versi lama (best effort; kegagalan tidak membatalkan cadangan baru).
            if (previousVersion != null && previousVersion != timestamp) {
                try {
                    deleteChunks(chunksRef, previousVersion)
                } catch (_: Exception) {
                }
            }

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

            Result.success("Cadangan data berhasil disimpan ke Cloud Firebase untuk akun ${user.email ?: user.displayName} ($dateStr, $sizeLabel).")
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
            val doc = userBackupDoc(user.uid).get().await()

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
            val doc = userBackupDoc(user.uid).get().await()

            if (!doc.exists()) {
                return@withContext Result.failure(Exception("Belum ada data cadangan cloud yang ditemukan untuk akun ini."))
            }

            val chunkVersion = doc.getLong("chunkVersion")
            val dataJson = if (chunkVersion != null) {
                readChunks(doc.reference.collection("chunks"), chunkVersion)
                    ?: return@withContext Result.failure(Exception("Data cadangan cloud tidak lengkap atau rusak. Silakan cadangkan ulang dari perangkat yang datanya masih utuh."))
            } else {
                // Kompatibilitas cadangan lama yang masih memakai satu dokumen.
                doc.getString("dataJson")
                    ?: return@withContext Result.failure(Exception("Data cadangan cloud kosong atau rusak."))
            }

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
            val doc = userBackupDoc(user.uid)
            peekDeleteChunks(doc.collection("chunks"))
            doc.delete().await()
            Result.success("Cadangan cloud akun Anda berhasil dihapus.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun userBackupDoc(userId: String) =
        firestore.collection("users")
            .document(userId)
            .collection("backups")
            .document("latest")

    private fun chunkId(version: Long, index: Int): String =
        String.format(Locale.US, "%d_%05d", version, index)

    private suspend fun writeChunks(collection: CollectionReference, version: Long, chunks: List<String>) {
        var index = 0
        while (index < chunks.size) {
            val batch = firestore.batch()
            var inBatch = 0
            var batchBytes = 0L
            while (index < chunks.size && inBatch < MAX_BATCH_WRITES) {
                val chunk = chunks[index]
                val chunkBytes = chunk.toByteArray(Charsets.UTF_8).size.toLong()
                // Batch Firestore juga dibatasi total ukuran (~10 MiB), bukan hanya jumlah tulis.
                if (inBatch > 0 && batchBytes + chunkBytes > MAX_BATCH_BYTES) break
                batch.set(
                    collection.document(chunkId(version, index)),
                    mapOf("index" to index, "version" to version, "data" to chunk)
                )
                batchBytes += chunkBytes
                index++
                inBatch++
            }
            batch.commit().await()
        }
    }

    private suspend fun readChunks(collection: CollectionReference, version: Long): String? {
        // Filter satu field saja agar tidak butuh composite index; urutkan di klien.
        val snapshot = collection.whereEqualTo("version", version).get().await()
        if (snapshot.isEmpty) return null
        val docs = snapshot.documents.sortedBy { it.getLong("index") ?: Long.MAX_VALUE }
        val builder = StringBuilder()
        for (chunk in docs) {
            builder.append(chunk.getString("data") ?: return null)
        }
        return builder.toString()
    }

    private suspend fun deleteChunks(collection: CollectionReference, version: Long) {
        val snapshot = collection.whereEqualTo("version", version).get().await()
        deleteAll(snapshot.documents.map { it.reference })
    }

    private suspend fun peekDeleteChunks(collection: CollectionReference) {
        val snapshot = collection.get().await()
        deleteAll(snapshot.documents.map { it.reference })
    }

    private suspend fun deleteAll(refs: List<com.google.firebase.firestore.DocumentReference>) {
        var index = 0
        while (index < refs.size) {
            val batch = firestore.batch()
            var inBatch = 0
            while (index < refs.size && inBatch < MAX_BATCH_WRITES) {
                batch.delete(refs[index])
                index++
                inBatch++
            }
            batch.commit().await()
        }
    }

    /** Pecah teks menjadi potongan yang aman di bawah batas 1 MiB per dokumen Firestore. */
    private fun splitIntoChunks(text: String, maxBytes: Int): List<String> {
        val chunks = ArrayList<String>()
        val builder = StringBuilder()
        var currentBytes = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            val codePointBytes = utf8Length(codePoint)
            if (currentBytes + codePointBytes > maxBytes && builder.isNotEmpty()) {
                chunks.add(builder.toString())
                builder.setLength(0)
                currentBytes = 0
            }
            builder.appendRange(text, index, index + charCount)
            currentBytes += codePointBytes
            index += charCount
        }
        if (builder.isNotEmpty()) chunks.add(builder.toString())
        return chunks
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private companion object {
        // Margin di bawah batas keras Firestore 1 MiB per dokumen.
        const val MAX_CHUNK_BYTES = 700_000
        const val MAX_BATCH_WRITES = 400
        // Margin di bawah batas total satu batch tulis (~10 MiB).
        const val MAX_BATCH_BYTES = 8L * 1024 * 1024
    }
}

data class CloudBackupInfo(
    val timestamp: Long,
    val dateTimeFormatted: String,
    val sizeKb: String,
    val userEmail: String
)
