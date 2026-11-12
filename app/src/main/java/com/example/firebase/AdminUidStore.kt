package com.example.firebase

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Daftar UID Administrator My Singghara.
 *
 * Daftar tersimpan di-cache lokal (SharedPreferences) dan disinkronkan dari
 * cloud (`app_settings/admin_uids`). Selama cache masih kosong, UID admin
 * bawaan dipakai untuk deteksi otomatis agar panel admin hanya terlihat oleh
 * akun admin tanpa perlu pengaturan manual di setiap perangkat.
 */
object AdminUidStore {

    private const val PREFS_NAME = "admin_uid_prefs"
    private const val KEY_ADMIN_UIDS = "admin_uids"
    private const val KEY_UPDATED_AT = "admin_uids_updated_at"
    private const val SEPARATOR = "\n"

    private val DEFAULT_ADMIN_UIDS = listOf("UiDpHxawlBQ17mxTUHIXvhxRR3b2")

    @Volatile
    private var appContext: Context? = null

    private val _adminUids = MutableStateFlow<List<String>>(emptyList())

    /** Waktu perubahan terakhir daftar lokal, dipakai saat menggabungkan daftar dari cloud. */
    @Volatile
    private var localUpdatedAt: Long = 0L

    /** Daftar UID tersimpan yang dapat diobservasi Compose agar UI ikut berubah. */
    val adminUids: StateFlow<List<String>> = _adminUids.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        _adminUids.value = loadFromPrefs()
        localUpdatedAt = prefs()?.getLong(KEY_UPDATED_AT, 0L) ?: 0L
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadFromPrefs(): List<String> {
        val raw = prefs()?.getString(KEY_ADMIN_UIDS, null) ?: return emptyList()
        return normalize(raw.split(SEPARATOR))
    }

    /** UID admin yang tersimpan di perangkat ini (kosong pada instalasi baru). */
    fun getAdminUids(): List<String> = _adminUids.value

    /** UID admin bawaan yang selalu dikenali sebagai administrator. */
    fun defaultAdminUids(): List<String> = DEFAULT_ADMIN_UIDS

    /** Daftar UID yang benar-benar berlaku untuk deteksi admin (bawaan + tersimpan). */
    fun effectiveAdminUids(): List<String> = (DEFAULT_ADMIN_UIDS + _adminUids.value).distinct()

    fun isAdmin(uid: String?): Boolean = isAdminIn(_adminUids.value, uid)

    /**
     * Deteksi admin memakai daftar hasil observasi Compose sehingga tampilan
     * otomatis berubah begitu daftar dari cloud selesai disinkronkan.
     */
    fun isAdminIn(adminUids: List<String>, uid: String?): Boolean {
        val clean = uid?.trim().orEmpty()
        if (clean.isEmpty()) return false
        return DEFAULT_ADMIN_UIDS.contains(clean) || adminUids.contains(clean)
    }

    fun saveAdminUids(uids: Collection<String>): List<String> =
        persist(uids, System.currentTimeMillis())

    /**
     * Terapkan daftar dari cloud. Daftar lokal yang lebih baru tidak ditimpa,
     * dan cache lokal tidak dikosongkan bila cloud belum punya daftar.
     */
    fun applyCloudAdminUids(uids: Collection<String>, cloudUpdatedAt: Long): List<String> {
        val normalized = normalize(uids)
        if (normalized.isEmpty()) return _adminUids.value
        if (normalized == _adminUids.value) return _adminUids.value
        if (_adminUids.value.isNotEmpty() && cloudUpdatedAt <= localUpdatedAt) return _adminUids.value
        return persist(normalized, cloudUpdatedAt)
    }

    private fun persist(uids: Collection<String>, updatedAt: Long): List<String> {
        val normalized = normalize(uids)
        prefs()?.edit()
            ?.putString(KEY_ADMIN_UIDS, normalized.joinToString(SEPARATOR))
            ?.putLong(KEY_UPDATED_AT, updatedAt)
            ?.apply()
        localUpdatedAt = updatedAt
        _adminUids.value = normalized
        return normalized
    }

    fun primaryAdminUid(): String = effectiveAdminUids().firstOrNull().orEmpty()

    /** Potongan kode `isAdmin()` Firestore Rules yang mengikuti daftar UID tersimpan. */
    fun rulesAdminCondition(): String {
        val uids = _adminUids.value
        if (uids.isEmpty()) return "return false;"
        val list = uids.joinToString(", ") { "'$it'" }
        return "return request.auth != null && request.auth.uid in [$list];"
    }

    /** Label UID tersimpan untuk komentar di teks aturan rules. */
    fun storedAdminUidsLabel(): String = _adminUids.value.joinToString(", ").ifEmpty { "belum diatur" }

    /** Versi ringkas daftar UID aktif untuk ditampilkan di UI. */
    fun adminUidsLabel(): String = effectiveAdminUids().joinToString(", ")

    private fun normalize(source: Collection<String>): List<String> =
        source.map { it.trim().replace("'", "").replace("\"", "").replace(SEPARATOR, "") }
            .filter { it.isNotEmpty() }
            .distinct()
}
