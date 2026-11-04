package com.example.data

import android.content.Context
import android.os.Environment
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class LocalRepository(private val db: AppDatabase) {

    // DAOs
    val shoppingDao = db.shoppingDao()
    val noteDao = db.noteDao()
    val savingDao = db.savingDao()
    val agendaDao = db.agendaDao()
    val quranDao = db.quranDao()
    val mindMapDao = db.mindMapDao()
    val jadwalDao = db.jadwalDao()
    val anggotaDao = db.anggotaDao()
    val presensiDao = db.presensiDao()
    val gajiDao = db.gajiDao()
    val belanjaPasarDao = db.belanjaPasarDao()
    val belanjaOnlineDao = db.belanjaOnlineDao()
    val fcmNotificationDao = db.fcmNotificationDao()
    val arisanDao = db.arisanDao()

    // Flows
    val allShoppingItems: Flow<List<ShoppingItem>> = shoppingDao.getAllShoppingItems()
    val allNotes: Flow<List<NoteItem>> = noteDao.getAllNotes()
    val activeSavingTarget: Flow<SavingTarget?> = savingDao.getActiveTargetFlow()
    val allSavingTransactions: Flow<List<SavingTransaction>> = savingDao.getAllTransactionsFlow()
    val allAgendas: Flow<List<AgendaItem>> = agendaDao.getAllAgendas()
    val allQuranBookmarks: Flow<List<QuranBookmark>> = quranDao.getAllBookmarksFlow()
    val allQuranHistory: Flow<List<QuranHistory>> = quranDao.getAllHistoryFlow()
    val allMindMaps: Flow<List<MindMap>> = mindMapDao.getAllMindMaps()
    val allJadwal: Flow<List<JadwalKegiatan>> = jadwalDao.getAllJadwal()
    val allAnggota: Flow<List<AnggotaItem>> = anggotaDao.getAllAnggota()
    val allPresensi: Flow<List<RiwayatPresensi>> = presensiDao.getAllPresensi()
    val allGaji: Flow<List<GajiItem>> = gajiDao.getAllGaji()
    val allBelanjaPasarItems: Flow<List<BelanjaPasarItem>> = belanjaPasarDao.getAllBelanjaPasar()
    val allBelanjaOnlineItems: Flow<List<BelanjaOnlineItem>> = belanjaOnlineDao.getAllBelanjaOnline()
    val allFcmNotifications: Flow<List<FcmNotification>> = fcmNotificationDao.getAllNotifications()
    val unreadFcmCount: Flow<Int> = fcmNotificationDao.getUnreadCount()
    val allArisanKelompok: Flow<List<ArisanKelompok>> = arisanDao.getAllKelompok()

    // Arisan operations
    fun getPesertaByArisan(arisanId: Int): Flow<List<ArisanPeserta>> = arisanDao.getPesertaByArisan(arisanId)
    suspend fun getPesertaListByArisan(arisanId: Int): List<ArisanPeserta> = withContext(Dispatchers.IO) {
        arisanDao.getPesertaListByArisan(arisanId)
    }
    fun getIuranByPutaran(arisanId: Int, putaranKe: Int): Flow<List<ArisanIuran>> = arisanDao.getIuranByPutaran(arisanId, putaranKe)
    fun getAllIuranByArisan(arisanId: Int): Flow<List<ArisanIuran>> = arisanDao.getAllIuranByArisan(arisanId)
    fun getKasByArisan(arisanId: Int): Flow<List<ArisanKasTransaksi>> = arisanDao.getKasByArisan(arisanId)
    fun getPemenangByArisan(arisanId: Int): Flow<List<ArisanPemenangRiwayat>> = arisanDao.getPemenangByArisan(arisanId)

    suspend fun insertArisanKelompok(kelompok: ArisanKelompok): Long = withContext(Dispatchers.IO) {
        arisanDao.insertKelompok(kelompok)
    }
    suspend fun updateArisanKelompok(kelompok: ArisanKelompok) = withContext(Dispatchers.IO) {
        arisanDao.updateKelompok(kelompok)
    }
    suspend fun deleteArisanKelompok(kelompok: ArisanKelompok) = withContext(Dispatchers.IO) {
        arisanDao.deleteKelompok(kelompok)
        arisanDao.deletePesertaByArisan(kelompok.id)
        arisanDao.deleteIuranByArisan(kelompok.id)
        arisanDao.deleteKasByArisan(kelompok.id)
        arisanDao.deletePemenangByArisan(kelompok.id)
    }

    suspend fun insertArisanPeserta(peserta: ArisanPeserta): Long = withContext(Dispatchers.IO) {
        arisanDao.insertPeserta(peserta)
    }
    suspend fun updateArisanPeserta(peserta: ArisanPeserta) = withContext(Dispatchers.IO) {
        arisanDao.updatePeserta(peserta)
    }
    suspend fun deleteArisanPeserta(peserta: ArisanPeserta) = withContext(Dispatchers.IO) {
        arisanDao.deletePeserta(peserta)
    }

    suspend fun insertArisanIuran(iuran: ArisanIuran): Long = withContext(Dispatchers.IO) {
        arisanDao.insertIuran(iuran)
    }
    suspend fun updateArisanIuran(iuran: ArisanIuran) = withContext(Dispatchers.IO) {
        arisanDao.updateIuran(iuran)
    }
    suspend fun deleteArisanIuran(iuran: ArisanIuran) = withContext(Dispatchers.IO) {
        arisanDao.deleteIuran(iuran)
    }

    suspend fun insertArisanKas(kas: ArisanKasTransaksi): Long = withContext(Dispatchers.IO) {
        arisanDao.insertKas(kas)
    }
    suspend fun deleteArisanKas(kas: ArisanKasTransaksi) = withContext(Dispatchers.IO) {
        arisanDao.deleteKas(kas)
    }

    suspend fun insertArisanPemenang(pemenang: ArisanPemenangRiwayat): Long = withContext(Dispatchers.IO) {
        arisanDao.insertPemenang(pemenang)
    }
    suspend fun deleteArisanPemenang(pemenang: ArisanPemenangRiwayat) = withContext(Dispatchers.IO) {
        arisanDao.deletePemenang(pemenang)
    }

    // Belanja Pasar operations
    suspend fun insertBelanjaPasar(item: BelanjaPasarItem): Long = withContext(Dispatchers.IO) {
        belanjaPasarDao.insertBelanjaPasar(item)
    }

    suspend fun updateBelanjaPasar(item: BelanjaPasarItem) = withContext(Dispatchers.IO) {
        belanjaPasarDao.updateBelanjaPasar(item)
    }

    suspend fun deleteBelanjaPasar(item: BelanjaPasarItem) = withContext(Dispatchers.IO) {
        belanjaPasarDao.deleteBelanjaPasar(item)
    }

    suspend fun deleteBelanjaPasarById(id: Int) = withContext(Dispatchers.IO) {
        belanjaPasarDao.deleteById(id)
    }

    suspend fun setBelanjaPasarSudahDibeli(id: Int, sudahDibeli: Boolean) = withContext(Dispatchers.IO) {
        belanjaPasarDao.setSudahDibeli(id, sudahDibeli)
    }

    suspend fun clearCompletedBelanjaPasar() = withContext(Dispatchers.IO) {
        belanjaPasarDao.clearCompleted()
    }

    suspend fun getBelanjaPasarById(id: Int): BelanjaPasarItem? = withContext(Dispatchers.IO) {
        belanjaPasarDao.getById(id)
    }

    // Belanja Online operations
    suspend fun insertBelanjaOnline(item: BelanjaOnlineItem): Long = withContext(Dispatchers.IO) {
        belanjaOnlineDao.insertBelanjaOnline(item)
    }

    suspend fun updateBelanjaOnline(item: BelanjaOnlineItem) = withContext(Dispatchers.IO) {
        belanjaOnlineDao.updateBelanjaOnline(item)
    }

    suspend fun deleteBelanjaOnline(item: BelanjaOnlineItem) = withContext(Dispatchers.IO) {
        belanjaOnlineDao.deleteBelanjaOnline(item)
    }

    suspend fun deleteBelanjaOnlineById(id: Int) = withContext(Dispatchers.IO) {
        belanjaOnlineDao.deleteById(id)
    }

    suspend fun getBelanjaOnlineById(id: Int): BelanjaOnlineItem? = withContext(Dispatchers.IO) {
        belanjaOnlineDao.getById(id)
    }

    suspend fun deleteAllBelanjaOnline() = withContext(Dispatchers.IO) {
        belanjaOnlineDao.deleteAll()
    }

    // Gaji operations
    suspend fun insertGaji(gaji: GajiItem): Long = withContext(Dispatchers.IO) {
        gajiDao.insertGaji(gaji)
    }

    suspend fun updateGaji(gaji: GajiItem) = withContext(Dispatchers.IO) {
        gajiDao.updateGaji(gaji)
    }

    suspend fun deleteGaji(gaji: GajiItem) = withContext(Dispatchers.IO) {
        gajiDao.deleteGaji(gaji)
    }

    suspend fun deleteAllGaji() = withContext(Dispatchers.IO) {
        gajiDao.deleteAll()
    }

    suspend fun getGajiById(id: Int): GajiItem? = withContext(Dispatchers.IO) {
        gajiDao.getGajiById(id)
    }

    // Anggota & Presensi operations
    suspend fun insertAnggota(anggota: AnggotaItem): Long = withContext(Dispatchers.IO) {
        anggotaDao.insertAnggota(anggota)
    }

    suspend fun updateAnggota(anggota: AnggotaItem) = withContext(Dispatchers.IO) {
        anggotaDao.updateAnggota(anggota)
    }

    suspend fun deleteAnggota(anggota: AnggotaItem) = withContext(Dispatchers.IO) {
        anggotaDao.deleteAnggota(anggota)
    }

    suspend fun getAnggotaByNomorInduk(nomorInduk: String): AnggotaItem? = withContext(Dispatchers.IO) {
        anggotaDao.getAnggotaByNomorInduk(nomorInduk)
    }

    suspend fun insertPresensi(presensi: RiwayatPresensi): Long = withContext(Dispatchers.IO) {
        presensiDao.insertPresensi(presensi)
    }

    suspend fun updatePresensi(presensi: RiwayatPresensi) = withContext(Dispatchers.IO) {
        presensiDao.updatePresensi(presensi)
    }

    suspend fun deletePresensi(presensi: RiwayatPresensi) = withContext(Dispatchers.IO) {
        presensiDao.deletePresensi(presensi)
    }

    suspend fun deleteAllPresensi() = withContext(Dispatchers.IO) {
        presensiDao.deleteAll()
    }

    // Jadwal operations
    suspend fun insertJadwal(jadwal: JadwalKegiatan) = withContext(Dispatchers.IO) {
        jadwalDao.insertJadwal(jadwal)
    }

    suspend fun deleteJadwal(jadwal: JadwalKegiatan) = withContext(Dispatchers.IO) {
        jadwalDao.deleteJadwal(jadwal)
    }

    suspend fun deleteAllJadwal() = withContext(Dispatchers.IO) {
        jadwalDao.deleteAll()
    }

    // MindMap operations
    suspend fun insertMindMap(mindMap: MindMap): Long = withContext(Dispatchers.IO) {
        mindMapDao.insertMindMap(mindMap)
    }

    suspend fun updateMindMap(mindMap: MindMap) = withContext(Dispatchers.IO) {
        mindMapDao.updateMindMap(mindMap)
    }

    suspend fun deleteMindMap(mindMap: MindMap) = withContext(Dispatchers.IO) {
        mindMapDao.deleteMindMap(mindMap)
    }

    // Shopping operations
    suspend fun insertShopping(item: ShoppingItem) = withContext(Dispatchers.IO) {
        shoppingDao.insertShoppingItem(item)
    }

    suspend fun updateShopping(item: ShoppingItem) = withContext(Dispatchers.IO) {
        shoppingDao.updateShoppingItem(item)
    }

    suspend fun deleteShopping(item: ShoppingItem) = withContext(Dispatchers.IO) {
        shoppingDao.deleteShoppingItem(item)
    }

    // Note operations
    suspend fun insertNote(note: NoteItem): Long = withContext(Dispatchers.IO) {
        noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteItem) = withContext(Dispatchers.IO) {
        noteDao.updateNote(note)
    }

    suspend fun deleteNote(note: NoteItem) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(note)
    }

    // Saving operations
    suspend fun setSavingTarget(target: SavingTarget) = withContext(Dispatchers.IO) {
        savingDao.setTarget(target)
    }

    suspend fun insertSavingTransaction(trx: SavingTransaction) = withContext(Dispatchers.IO) {
        savingDao.insertTransaction(trx)
    }

    suspend fun updateSavingTransaction(trx: SavingTransaction) = withContext(Dispatchers.IO) {
        savingDao.updateTransaction(trx)
    }

    suspend fun deleteSavingTransaction(trx: SavingTransaction) = withContext(Dispatchers.IO) {
        savingDao.deleteTransaction(trx)
    }

    // Agenda operations
    suspend fun insertAgenda(agenda: AgendaItem) = withContext(Dispatchers.IO) {
        agendaDao.insertAgenda(agenda)
    }

    suspend fun deleteAgenda(agenda: AgendaItem) = withContext(Dispatchers.IO) {
        agendaDao.deleteAgenda(agenda)
    }

    // JSON Backup Structure Model
    data class FullBackupData(
        val shopping: List<ShoppingItem>,
        val notes: List<NoteItem>,
        val savingTarget: SavingTarget?,
        val savingTransactions: List<SavingTransaction>,
        val agendas: List<AgendaItem>,
        val quranBookmarks: List<QuranBookmark>? = null,
        val quranHistory: List<QuranHistory>? = null,
        val mindMaps: List<MindMap>? = null,
        val jadwalKegiatan: List<JadwalKegiatan>? = null,
        val anggotaList: List<AnggotaItem>? = null,
        val presensiList: List<RiwayatPresensi>? = null,
        val gajiList: List<GajiItem>? = null,
        val belanjaPasarList: List<BelanjaPasarItem>? = null,
        val belanjaOnlineList: List<BelanjaOnlineItem>? = null,
        val arisanKelompokList: List<ArisanKelompok>? = null,
        val arisanPesertaList: List<ArisanPeserta>? = null,
        val arisanIuranList: List<ArisanIuran>? = null,
        val arisanKasList: List<ArisanKasTransaksi>? = null,
        val arisanPemenangList: List<ArisanPemenangRiwayat>? = null,
        val mySinggharaPrefs: Map<String, Any?>? = null,
        val pembukuanUsahaPrefs: Map<String, Any?>? = null,
        val absensiPrefs: Map<String, Any?>? = null,
        val idCardPrefs: Map<String, Any?>? = null,
        val customAzanFiles: Map<String, String>? = null,
        val internalMediaFiles: Map<String, String>? = null,
        val arisanKeluargaPrefs: Map<String, Any?>? = null,
        val handyTalkyPrefs: Map<String, Any?>? = null,
        val telegramPrefs: Map<String, Any?>? = null,
        val appSettingsPrefs: Map<String, Any?>? = null,
        val puzzlePrefs: Map<String, Any?>? = null,
        val puzzleRecordsPrefs: Map<String, Any?>? = null,
        val fcmNotifications: List<FcmNotification>? = null
    )

    private companion object {
        const val PREFS_MAIN = "mysingghara_prefs"
        const val PREFS_PEMBUKUAN = "pembukuan_usaha_prefs"
        const val PREFS_ABSENSI = "mysingghara_absensi_prefs"
        const val PREFS_IDCARD = "singghara_idcard_prefs"
        const val PREFS_ARISAN = "arisan_keluarga_prefs"
        const val PREFS_HT = "mysingghara_ht_prefs"
        const val PREFS_TELEGRAM = "telegram_bot_prefs"
        const val PREFS_APP_SETTINGS = "app_settings_prefs"
        const val PREFS_PUZZLE = "sliding_puzzle_prefs"
        const val PREFS_PUZZLE_RECORDS = "sliding_puzzle_records"
    }

    // Moshi Instance
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val backupAdapter = moshi.adapter(FullBackupData::class.java)

    private fun getSharedPrefsMap(context: Context, prefsName: String): Map<String, Any?> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        // Kunci yang hanya berlaku untuk perangkat ini (PIN, folder SAF, ID radio) tidak diekspor;
        // pemulihan memang selalu mempertahankan nilai lokalnya.
        val localOnlyKeys = when (prefsName) {
            PREFS_MAIN -> setOf("app_pin", "app_lock_enabled", "first_install_setup_completed", "saf_folder_uri")
            PREFS_HT -> setOf("device_id")
            else -> emptySet()
        }
        return prefs.all.filterKeys { it !in localOnlyKeys }
    }

    private fun restoreSharedPrefsFromMap(context: Context, prefsName: String, dataMap: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        
        val oldSafUri = prefs.getString("saf_folder_uri", "") ?: ""
        val oldAppPin = prefs.getString("app_pin", "") ?: ""
        val oldAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val oldFirstInstallCompleted = prefs.getBoolean("first_install_setup_completed", false)
        val oldHtDeviceId = prefs.getString("device_id", "") ?: ""
        val oldValues = prefs.all
        
        val editor = prefs.edit()
        editor.clear()
        
        dataMap.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Number -> {
                    val d = value.toDouble()
                    // JSON tidak membedakan Int/Long/Float. Pertahankan tipe yang sudah dipakai
                    // kunci tersebut agar kode pembaca (getInt/getLong/getFloat) tidak ClassCastException.
                    when (oldValues[key]) {
                        is Long -> editor.putLong(key, value.toLong())
                        is Float -> editor.putFloat(key, value.toFloat())
                        is Int -> editor.putInt(key, value.toInt())
                        else -> when {
                            key == "modal_usaha" || key == "frequency" ||
                                    key == "mic_gain" || key == "speaker_gain" -> editor.putFloat(key, d.toFloat())
                            key == "key_last_update_id" -> editor.putLong(key, d.toLong())
                            d % 1.0 != 0.0 -> editor.putFloat(key, d.toFloat())
                            d > Int.MAX_VALUE || d < Int.MIN_VALUE -> editor.putLong(key, d.toLong())
                            else -> editor.putInt(key, d.toInt())
                        }
                    }
                }
                is List<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    if (stringSet.isNotEmpty()) {
                        editor.putStringSet(key, stringSet)
                    }
                }
            }
        }
        
        when (prefsName) {
            PREFS_MAIN -> {
                if (oldSafUri.isNotEmpty()) {
                    editor.putString("saf_folder_uri", oldSafUri)
                }
                if (oldAppPin.isNotEmpty()) {
                    editor.putString("app_pin", oldAppPin)
                    editor.putBoolean("app_lock_enabled", oldAppLockEnabled)
                } else if (oldAppLockEnabled) {
                    editor.putBoolean("app_lock_enabled", true)
                }
                if (oldFirstInstallCompleted) {
                    editor.putBoolean("first_install_setup_completed", true)
                }
            }
            PREFS_HT -> {
                // device_id identifies this physical device on the radio net; never import it.
                if (oldHtDeviceId.isNotEmpty()) {
                    editor.putString("device_id", oldHtDeviceId)
                } else {
                    editor.remove("device_id")
                }
            }
            PREFS_ARISAN -> {
                // Tanpa flag ini ArisanRepository akan menganggap data masih demo dan menghapusnya.
                editor.putBoolean("demo_data_purged_v2", true)
            }
        }
        
        editor.commit()
    }

    private fun getInternalFilesMap(context: Context): Map<String, String> {
        val filesMap = mutableMapOf<String, String>()
        val filesDir = context.filesDir

        fun collectFiles(dir: java.io.File, relativePrefix: String = "") {
            val list = dir.listFiles() ?: return
            for (file in list) {
                if (file.isDirectory) {
                    collectFiles(file, if (relativePrefix.isEmpty()) file.name else "$relativePrefix/${file.name}")
                } else if (file.isFile) {
                    val relPath = if (relativePrefix.isEmpty()) file.name else "$relativePrefix/${file.name}"
                    val nameLower = file.name.lowercase(java.util.Locale.ROOT)
                    val shouldInclude = nameLower.startsWith("azan_custom_") ||
                            nameLower.startsWith("custom_") ||
                            nameLower.startsWith("sig_") ||
                            nameLower.startsWith("company_id_card_") ||
                            nameLower.endsWith(".ttf") ||
                            nameLower.endsWith(".otf") ||
                            nameLower.endsWith(".mp3") ||
                            nameLower.endsWith(".png") ||
                            nameLower.endsWith(".jpg") ||
                            nameLower.endsWith(".jpeg") ||
                            nameLower.endsWith(".webp") ||
                            relativePrefix.contains("profile_photos")
                    if (shouldInclude) {
                        try {
                            val bytes = file.readBytes()
                            val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            filesMap[relPath] = base64Str
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        collectFiles(filesDir)
        return filesMap
    }

    private fun restoreInternalFilesFromMap(context: Context, filesMap: Map<String, String>) {
        val filesDir = context.filesDir
        val safeRoot = filesDir.canonicalPath + java.io.File.separator
        filesMap.forEach { (relPath, base64Str) ->
            try {
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val targetFile = java.io.File(filesDir, relPath)
                // Berkas cadangan bisa berasal dari luar (unggahan web); tolak path yang keluar folder aplikasi.
                if (!targetFile.canonicalPath.startsWith(safeRoot)) return@forEach
                targetFile.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                targetFile.writeBytes(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Snapshot seluruh data aplikasi: Room, SharedPreferences, dan media internal. */
    private suspend fun buildBackupData(context: Context): FullBackupData = FullBackupData(
        shopping = allShoppingItems.first(),
        notes = allNotes.first(),
        savingTarget = activeSavingTarget.first(),
        savingTransactions = allSavingTransactions.first(),
        agendas = allAgendas.first(),
        quranBookmarks = allQuranBookmarks.first(),
        quranHistory = allQuranHistory.first(),
        mindMaps = allMindMaps.first(),
        jadwalKegiatan = allJadwal.first(),
        anggotaList = allAnggota.first(),
        presensiList = allPresensi.first(),
        gajiList = allGaji.first(),
        belanjaPasarList = allBelanjaPasarItems.first(),
        belanjaOnlineList = allBelanjaOnlineItems.first(),
        mySinggharaPrefs = getSharedPrefsMap(context, PREFS_MAIN),
        pembukuanUsahaPrefs = getSharedPrefsMap(context, PREFS_PEMBUKUAN),
        absensiPrefs = getSharedPrefsMap(context, PREFS_ABSENSI),
        idCardPrefs = getSharedPrefsMap(context, PREFS_IDCARD),
        arisanKeluargaPrefs = getSharedPrefsMap(context, PREFS_ARISAN),
        handyTalkyPrefs = getSharedPrefsMap(context, PREFS_HT),
        telegramPrefs = getSharedPrefsMap(context, PREFS_TELEGRAM),
        appSettingsPrefs = getSharedPrefsMap(context, PREFS_APP_SETTINGS),
        puzzlePrefs = getSharedPrefsMap(context, PREFS_PUZZLE),
        puzzleRecordsPrefs = getSharedPrefsMap(context, PREFS_PUZZLE_RECORDS),
        fcmNotifications = allFcmNotifications.first(),
        internalMediaFiles = getInternalFilesMap(context)
    )

    /**
     * Membaca semua data dari SQLite Room dan mengekspornya ke folder Download publik sebagai JSON.
     */
    suspend fun exportDataToPublicDownload(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = backupAdapter.indent("  ").toJson(buildBackupData(context))

            // Simpan utama ke folder My Singghara/Back Up/mysingghara_backup.json
            val primaryBackupFile = com.example.util.SinggharaFolderManager.getPrimaryBackupFile()
            primaryBackupFile.writeText(jsonString, Charsets.UTF_8)

            // Simpan juga ke folder Download publik untuk kompatibilitas
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val backupFile = File(downloadDir, "mysingghara_backup.json")
            backupFile.writeText(jsonString, Charsets.UTF_8)

            Result.success("Berhasil ekspor ke ${primaryBackupFile.absolutePath}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menghasilkan string JSON backup lengkap dari seluruh data aplikasi (Room & SharedPreferences).
     */
    suspend fun getBackupJsonString(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(backupAdapter.indent("  ").toJson(buildBackupData(context)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memulihkan data aplikasi (Room & SharedPreferences) dari string JSON backup.
     */
    suspend fun restoreFromJsonString(context: Context, jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupObj = backupAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(Exception("Format data cadangan tidak valid."))

            applyBackupData(context, backupObj)
            Result.success("Berhasil memulihkan data aplikasi!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menimpa seluruh data lokal dengan isi cadangan. Penghapusan dan penyisipan Room berjalan
     * dalam satu transaksi supaya kegagalan di tengah tidak menghilangkan data lama.
     */
    private suspend fun applyBackupData(context: Context, backupObj: FullBackupData) {
        db.withTransaction {
            shoppingDao.deleteAll()
            noteDao.deleteAll()
            savingDao.deleteAllTargets()
            savingDao.deleteAllTransactions()
            agendaDao.deleteAll()
            quranDao.clearBookmarks()
            quranDao.clearHistory()
            mindMapDao.deleteAll()
            jadwalDao.deleteAll()
            anggotaDao.deleteAll()
            presensiDao.deleteAll()
            gajiDao.deleteAll()
            belanjaPasarDao.deleteAll()
            // Field baru dibuat opsional agar cadangan versi lama tidak menghapus tabel ini.
            if (backupObj.belanjaOnlineList != null) {
                belanjaOnlineDao.deleteAll()
            }
            // Field baru dibuat opsional agar berkas cadangan versi lama (tanpa field ini)
            // tidak menghapus data Arisan/Notifikasi yang sudah ada di perangkat.
            if (backupObj.arisanKelompokList != null) {
                arisanDao.deleteAllKelompok()
                arisanDao.deleteAllPeserta()
                arisanDao.deleteAllIuran()
                arisanDao.deleteAllKas()
                arisanDao.deleteAllPemenang()
            }
            if (backupObj.fcmNotifications != null) {
                fcmNotificationDao.clearAll()
            }

            backupObj.shopping.forEach { shoppingDao.insertShoppingItem(it) }
            backupObj.notes.forEach { noteDao.insertNote(it) }
            backupObj.savingTarget?.let { savingDao.setTarget(it) }
            backupObj.savingTransactions.forEach { savingDao.insertTransaction(it) }
            backupObj.agendas.forEach { agendaDao.insertAgenda(it) }
            backupObj.quranBookmarks?.forEach { quranDao.insertBookmark(it) }
            backupObj.quranHistory?.forEach { quranDao.insertHistory(it) }
            backupObj.mindMaps?.forEach { mindMapDao.insertMindMap(it) }
            backupObj.jadwalKegiatan?.forEach { jadwalDao.insertJadwal(it) }
            backupObj.anggotaList?.forEach { anggotaDao.insertAnggota(it) }
            backupObj.presensiList?.forEach { presensiDao.insertPresensi(it) }
            backupObj.gajiList?.forEach { gajiDao.insertGaji(it) }
            backupObj.belanjaPasarList?.forEach { belanjaPasarDao.insertBelanjaPasar(it) }
            backupObj.belanjaOnlineList?.forEach { belanjaOnlineDao.insertBelanjaOnline(it) }
            backupObj.arisanKelompokList?.forEach { arisanDao.insertKelompok(it) }
            backupObj.arisanPesertaList?.forEach { arisanDao.insertPeserta(it) }
            backupObj.arisanIuranList?.forEach { arisanDao.insertIuran(it) }
            backupObj.arisanKasList?.forEach { arisanDao.insertKas(it) }
            backupObj.arisanPemenangList?.forEach { arisanDao.insertPemenang(it) }
            backupObj.fcmNotifications?.forEach { fcmNotificationDao.insertNotification(it) }
        }

        val filesToRestore = backupObj.internalMediaFiles ?: backupObj.customAzanFiles
        filesToRestore?.let { restoreInternalFilesFromMap(context, it) }

        backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_MAIN, it) }
        backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_PEMBUKUAN, it) }
        backupObj.absensiPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_ABSENSI, it) }
        backupObj.idCardPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_IDCARD, it) }
        backupObj.arisanKeluargaPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_ARISAN, it) }
        backupObj.handyTalkyPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_HT, it) }
        backupObj.telegramPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_TELEGRAM, it) }
        backupObj.appSettingsPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_APP_SETTINGS, it) }
        backupObj.puzzlePrefs?.let { restoreSharedPrefsFromMap(context, PREFS_PUZZLE, it) }
        backupObj.puzzleRecordsPrefs?.let { restoreSharedPrefsFromMap(context, PREFS_PUZZLE_RECORDS, it) }
    }

    /**
     * Membaca file JSON dari folder Download publik dan menyinkronkannya kembali ke dalam database Room.
     */
    suspend fun importDataFromPublicDownload(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val primaryBackupFile = com.example.util.SinggharaFolderManager.getPrimaryBackupFile()
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadBackupFile = File(downloadDir, "mysingghara_backup.json")
            
            val backupFile = if (primaryBackupFile.exists()) {
                primaryBackupFile
            } else if (downloadBackupFile.exists()) {
                downloadBackupFile
            } else {
                return@withContext Result.failure(Exception("File backup 'mysingghara_backup.json' tidak ditemukan di folder 'My Singghara/Back Up' maupun 'Download'."))
            }

            val jsonString = backupFile.readText(Charsets.UTF_8)
            val backupObj = backupAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(Exception("Format file JSON backup tidak valid."))

            applyBackupData(context, backupObj)

            Result.success("Berhasil mengimpor data dari ${backupFile.name}!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengekspor seluruh database Room ke folder SAF yang dipilih pengguna sebagai file 'data_aplikasi.json'.
     */
    suspend fun exportDataToSAF(context: Context, folderUri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = backupAdapter.indent("  ").toJson(buildBackupData(context))

            // Dapatkan DocumentFile dari folder URI
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result.failure(Exception("Folder penyimpanan eksternal tidak dapat diakses."))

            if (!folder.exists() || !folder.canWrite()) {
                return@withContext Result.failure(Exception("Izin menulis ditolak atau folder tidak lagi ada."))
            }

            // Cari atau buat file data_aplikasi.json
            var file = folder.findFile("data_aplikasi.json")
            if (file == null) {
                file = folder.createFile("application/json", "data_aplikasi.json")
            }

            if (file == null) {
                return@withContext Result.failure(Exception("Gagal membuat file 'data_aplikasi.json' di folder terpilih."))
            }

            context.contentResolver.openOutputStream(file.uri)?.use { os ->
                os.write(jsonString.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.failure(Exception("Gagal membuka output stream ke file."))

            Result.success("Berhasil mengekspor cadangan data_aplikasi.json (termasuk Rekap Belanja Pasar) ke folder SAF terpilih!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Membaca file 'data_aplikasi.json' dari folder SAF dan memulihkannya ke dalam database Room.
     */
    suspend fun importDataFromSAF(context: Context, folderUri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result.failure(Exception("Folder penyimpanan eksternal tidak dapat diakses."))

            val file = folder.findFile("data_aplikasi.json")
                ?: return@withContext Result.failure(Exception("File backup 'data_aplikasi.json' tidak ditemukan di folder tersebut."))

            val inputStream = context.contentResolver.openInputStream(file.uri)
                ?: return@withContext Result.failure(Exception("Gagal membuka input stream dari file backup."))

            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val backupObj = backupAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(Exception("Format file JSON cadangan tidak valid."))

            applyBackupData(context, backupObj)

            Result.success("Berhasil memulihkan/mengimpor data aplikasi (termasuk Rekap Belanja Pasar) dari folder kustom Anda!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun String.escapeCsv(): String {
        val quote = "\""
        val escaped = this.replace(quote, "$quote$quote")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\r") || escaped.contains(quote)) {
            "$quote$escaped$quote"
        } else {
            escaped
        }
    }

    /**
     * Mengekspor data belanja ke file CSV.
     * Jika folderUri tidak null, simpan ke SAF folder.
     * Jika folderUri null, simpan ke public Download folder.
     */
    suspend fun exportShoppingToCSV(context: Context, folderUri: android.net.Uri?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val items = allShoppingItems.first()
            val sb = java.lang.StringBuilder()
            sb.append("\"ID\",\"Nama Barang\",\"Jenis\",\"Harga Satuan\",\"Kuantitas\",\"Pajak (%)\",\"Total Harga\",\"Tanggal\"\n")
            items.forEach { item ->
                sb.append("${item.id},")
                sb.append("${item.nama.escapeCsv()},")
                sb.append("${item.jenis.escapeCsv()},")
                sb.append("${item.hargaSatuan},")
                sb.append("${item.qty},")
                sb.append("${item.pajak},")
                sb.append("${item.total},")
                sb.append("${item.tanggal.escapeCsv()}\n")
            }
            val csvContent = sb.toString()
            val filename = "daftar_belanja.csv"

            if (folderUri != null) {
                val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                    ?: return@withContext Result.failure(Exception("Folder penyimpanan eksternal tidak dapat diakses."))
                if (!folder.exists() || !folder.canWrite()) {
                    return@withContext Result.failure(Exception("Izin menulis ditolak atau folder tidak lagi ada."))
                }
                var file = folder.findFile(filename)
                if (file == null) {
                    file = folder.createFile("text/csv", filename)
                }
                if (file == null) {
                    return@withContext Result.failure(Exception("Gagal membuat file '$filename' di folder terpilih."))
                }
                context.contentResolver.openOutputStream(file.uri)?.use { os ->
                    os.write(csvContent.toByteArray(Charsets.UTF_8))
                    os.flush()
                } ?: return@withContext Result.failure(Exception("Gagal membuka output stream."))
                Result.success("Berhasil ekspor ke folder SAF: $filename")
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && !downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val file = File(downloadDir, filename)
                file.writeText(csvContent, Charsets.UTF_8)
                Result.success("Berhasil ekspor ke folder Download: ${file.name}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengekspor data tabungan ke file CSV.
     * Jika folderUri tidak null, simpan ke SAF folder.
     * Jika folderUri null, simpan ke public Download folder.
     */
    suspend fun exportTabunganToCSV(context: Context, folderUri: android.net.Uri?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val target = activeSavingTarget.first()
            val transactions = allSavingTransactions.first()
            val sb = java.lang.StringBuilder()
            if (target != null) {
                sb.append("# Target Tabungan: ${target.nama.escapeCsv()} - Sasaran: Rp ${target.jumlah}\n")
            }
            sb.append("\"ID\",\"Jenis Transaksi\",\"Kategori\",\"Deskripsi\",\"Nominal\",\"Tanggal\"\n")
            transactions.forEach { trx ->
                sb.append("${trx.id},")
                sb.append("${if (trx.jenis == "masuk") "Masuk" else "Keluar"},")
                sb.append("${trx.kategori.escapeCsv()},")
                sb.append("${trx.deskripsi.escapeCsv()},")
                sb.append("${trx.nominal},")
                sb.append("${trx.tanggal.escapeCsv()}\n")
            }
            val csvContent = sb.toString()
            val filename = "catatan_tabungan.csv"

            if (folderUri != null) {
                val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                    ?: return@withContext Result.failure(Exception("Folder penyimpanan eksternal tidak dapat diakses."))
                if (!folder.exists() || !folder.canWrite()) {
                    return@withContext Result.failure(Exception("Izin menulis ditolak atau folder tidak lagi ada."))
                }
                var file = folder.findFile(filename)
                if (file == null) {
                    file = folder.createFile("text/csv", filename)
                }
                if (file == null) {
                    return@withContext Result.failure(Exception("Gagal membuat file '$filename' di folder terpilih."))
                }
                context.contentResolver.openOutputStream(file.uri)?.use { os ->
                    os.write(csvContent.toByteArray(Charsets.UTF_8))
                    os.flush()
                } ?: return@withContext Result.failure(Exception("Gagal membuka output stream."))
                Result.success("Berhasil ekspor ke folder SAF: $filename")
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && !downloadDir.exists()) {
                    downloadDir.mkdirs()
                }
                val file = File(downloadDir, filename)
                file.writeText(csvContent, Charsets.UTF_8)
                Result.success("Berhasil ekspor ke folder Download: ${file.name}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Quran operations
    suspend fun insertQuranBookmark(bookmark: QuranBookmark) = withContext(Dispatchers.IO) {
        quranDao.insertBookmark(bookmark)
    }

    suspend fun deleteQuranBookmark(bookmark: QuranBookmark) = withContext(Dispatchers.IO) {
        quranDao.deleteBookmark(bookmark)
    }

    suspend fun deleteQuranBookmarkByVerse(surahId: Int, verseNumber: Int) = withContext(Dispatchers.IO) {
        quranDao.deleteBookmarkByVerse(surahId, verseNumber)
    }

    suspend fun isQuranBookmarked(surahId: Int, verseNumber: Int): Boolean = withContext(Dispatchers.IO) {
        quranDao.isBookmarked(surahId, verseNumber)
    }

    suspend fun insertQuranHistory(history: QuranHistory) = withContext(Dispatchers.IO) {
        quranDao.insertHistory(history)
    }

    suspend fun clearQuranHistory() = withContext(Dispatchers.IO) {
        quranDao.clearHistory()
    }

    // FCM Notifications
    suspend fun insertFcmNotification(notification: FcmNotification): Long = withContext(Dispatchers.IO) {
        fcmNotificationDao.insertNotification(notification)
    }

    suspend fun markFcmAsRead(id: Long) = withContext(Dispatchers.IO) {
        fcmNotificationDao.markAsRead(id)
    }

    suspend fun markAllFcmAsRead() = withContext(Dispatchers.IO) {
        fcmNotificationDao.markAllAsRead()
    }

    suspend fun deleteFcmNotification(id: Long) = withContext(Dispatchers.IO) {
        fcmNotificationDao.deleteById(id)
    }

    suspend fun clearAllFcmNotifications() = withContext(Dispatchers.IO) {
        fcmNotificationDao.clearAll()
    }
}
