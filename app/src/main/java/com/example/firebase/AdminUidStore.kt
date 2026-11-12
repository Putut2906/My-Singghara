package com.example.firebase

import android.content.Context

/**
 * Daftar UID Administrator yang disimpan lokal di perangkat (SharedPreferences).
 * Kosong pada instalasi pertama dan tidak disinkronkan ke perangkat lain.
 * Aturan Firestore dibangun otomatis dari daftar ini.
 */
object AdminUidStore {

    private const val PREFS_NAME = "admin_uid_prefs"
    private const val KEY_ADMIN_UIDS = "admin_uids"
    private const val SEPARATOR = "\n"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAdminUids(): List<String> {
        val raw = prefs()?.getString(KEY_ADMIN_UIDS, null) ?: return emptyList()
        return normalize(raw.split(SEPARATOR))
    }

    fun isConfigured(): Boolean = getAdminUids().isNotEmpty()

    fun isAdmin(uid: String?): Boolean {
        val clean = uid?.trim().orEmpty()
        return clean.isNotEmpty() && getAdminUids().contains(clean)
    }

    fun saveAdminUids(uids: Collection<String>): List<String> {
        val normalized = normalize(uids)
        prefs()?.edit()?.putString(KEY_ADMIN_UIDS, normalized.joinToString(SEPARATOR))?.apply()
        return normalized
    }

    fun addAdminUid(uid: String): List<String> = saveAdminUids(getAdminUids() + uid)

    fun removeAdminUid(uid: String): List<String> =
        saveAdminUids(getAdminUids().filterNot { it == uid.trim() })

    fun primaryAdminUid(): String = getAdminUids().firstOrNull().orEmpty()

    /** Potongan kode `isAdmin()` Firestore Rules yang mengikuti daftar UID saat ini. */
    fun rulesAdminCondition(): String {
        val uids = getAdminUids()
        if (uids.isEmpty()) return "return false;"
        val list = uids.joinToString(", ") { "'$it'" }
        return "return request.auth != null && request.auth.uid in [$list];"
    }

    /** Versi ringkas daftar UID untuk ditampilkan di UI. */
    fun adminUidsLabel(): String = getAdminUids().joinToString(", ").ifEmpty { "belum diatur" }

    private fun normalize(source: Collection<String>): List<String> =
        source.map { it.trim().replace("'", "").replace("\"", "").replace(SEPARATOR, "") }
            .filter { it.isNotEmpty() }
            .distinct()
}
