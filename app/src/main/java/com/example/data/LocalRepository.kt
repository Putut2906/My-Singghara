package com.example.data

import android.content.Context
import android.os.Environment
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
        val internalMediaFiles: Map<String, String>? = null
    )

    // Moshi Instance
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val backupAdapter = moshi.adapter(FullBackupData::class.java)

    private fun getSharedPrefsMap(context: Context, prefsName: String): Map<String, Any?> {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.all
    }

    private fun restoreSharedPrefsFromMap(context: Context, prefsName: String, dataMap: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        
        val oldSafUri = prefs.getString("saf_folder_uri", "") ?: ""
        val oldAppPin = prefs.getString("app_pin", "") ?: ""
        val oldAppLockEnabled = prefs.getBoolean("app_lock_enabled", false)
        val oldFirstInstallCompleted = prefs.getBoolean("first_install_setup_completed", false)
        
        val editor = prefs.edit()
        editor.clear()
        
        dataMap.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Number -> {
                    val d = value.toDouble()
                    if (key == "modal_usaha") {
                        editor.putFloat(key, d.toFloat())
                    } else if (d % 1.0 == 0.0) {
                        if (d > Int.MAX_VALUE || d < Int.MIN_VALUE) {
                            editor.putLong(key, d.toLong())
                        } else {
                            editor.putInt(key, d.toInt())
                        }
                    } else {
                        editor.putFloat(key, d.toFloat())
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
        
        if (prefsName == "mysingghara_prefs") {
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
        filesMap.forEach { (relPath, base64Str) ->
            try {
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val targetFile = java.io.File(filesDir, relPath)
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

    /**
     * Membaca semua data dari SQLite Room dan mengekspornya ke folder Download publik sebagai JSON.
     */
    suspend fun exportDataToPublicDownload(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Ambil snapshot data terakhir
            val shoppingList = allShoppingItems.first()
            val notesList = allNotes.first()
            val target = activeSavingTarget.first()
            val transactionsList = allSavingTransactions.first()
            val agendasList = allAgendas.first()
            val quranBookmarksList = allQuranBookmarks.first()
            val quranHistoryList = allQuranHistory.first()
            val mindMapsList = allMindMaps.first()
            val jadwalList = allJadwal.first()
            val anggotaList = allAnggota.first()
            val presensiList = allPresensi.first()
            val gajiList = allGaji.first()
            val belanjaPasarList = allBelanjaPasarItems.first()

            val mediaFiles = getInternalFilesMap(context)

            val backupObj = FullBackupData(
                shopping = shoppingList,
                notes = notesList,
                savingTarget = target,
                savingTransactions = transactionsList,
                agendas = agendasList,
                quranBookmarks = quranBookmarksList,
                quranHistory = quranHistoryList,
                mindMaps = mindMapsList,
                jadwalKegiatan = jadwalList,
                anggotaList = anggotaList,
                presensiList = presensiList,
                gajiList = gajiList,
                belanjaPasarList = belanjaPasarList,
                mySinggharaPrefs = getSharedPrefsMap(context, "mysingghara_prefs"),
                pembukuanUsahaPrefs = getSharedPrefsMap(context, "pembukuan_usaha_prefs"),
                absensiPrefs = getSharedPrefsMap(context, "mysingghara_absensi_prefs"),
                idCardPrefs = getSharedPrefsMap(context, "singghara_idcard_prefs"),
                customAzanFiles = mediaFiles,
                internalMediaFiles = mediaFiles
            )

            val jsonString = backupAdapter.indent("  ").toJson(backupObj)

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
            val shoppingList = allShoppingItems.first()
            val notesList = allNotes.first()
            val target = activeSavingTarget.first()
            val transactionsList = allSavingTransactions.first()
            val agendasList = allAgendas.first()
            val quranBookmarksList = allQuranBookmarks.first()
            val quranHistoryList = allQuranHistory.first()
            val mindMapsList = allMindMaps.first()
            val jadwalList = allJadwal.first()
            val anggotaList = allAnggota.first()
            val presensiList = allPresensi.first()
            val gajiList = allGaji.first()
            val belanjaPasarList = allBelanjaPasarItems.first()

            val mediaFiles = getInternalFilesMap(context)

            val backupObj = FullBackupData(
                shopping = shoppingList,
                notes = notesList,
                savingTarget = target,
                savingTransactions = transactionsList,
                agendas = agendasList,
                quranBookmarks = quranBookmarksList,
                quranHistory = quranHistoryList,
                mindMaps = mindMapsList,
                jadwalKegiatan = jadwalList,
                anggotaList = anggotaList,
                presensiList = presensiList,
                gajiList = gajiList,
                belanjaPasarList = belanjaPasarList,
                mySinggharaPrefs = getSharedPrefsMap(context, "mysingghara_prefs"),
                pembukuanUsahaPrefs = getSharedPrefsMap(context, "pembukuan_usaha_prefs"),
                absensiPrefs = getSharedPrefsMap(context, "mysingghara_absensi_prefs"),
                idCardPrefs = getSharedPrefsMap(context, "singghara_idcard_prefs"),
                customAzanFiles = mediaFiles,
                internalMediaFiles = mediaFiles
            )

            val jsonString = backupAdapter.indent("  ").toJson(backupObj)
            Result.success(jsonString)
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

            // Bersihkan data lama
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
            arisanDao.deleteAllKelompok()
            arisanDao.deleteAllPeserta()
            arisanDao.deleteAllIuran()
            arisanDao.deleteAllKas()
            arisanDao.deleteAllPemenang()

            // Memasukkan data baru secara sekuensial
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
            backupObj.arisanKelompokList?.forEach { arisanDao.insertKelompok(it) }
            backupObj.arisanPesertaList?.forEach { arisanDao.insertPeserta(it) }
            backupObj.arisanIuranList?.forEach { arisanDao.insertIuran(it) }
            backupObj.arisanKasList?.forEach { arisanDao.insertKas(it) }
            backupObj.arisanPemenangList?.forEach { arisanDao.insertPemenang(it) }

            // Pulihkan internal media files
            val filesToRestore = backupObj.internalMediaFiles ?: backupObj.customAzanFiles
            filesToRestore?.let { restoreInternalFilesFromMap(context, it) }

            // Pulihkan preferences
            backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_prefs", it) }
            backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, "pembukuan_usaha_prefs", it) }
            backupObj.absensiPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_absensi_prefs", it) }
            backupObj.idCardPrefs?.let { restoreSharedPrefsFromMap(context, "singghara_idcard_prefs", it) }

            Result.success("Berhasil memulihkan data aplikasi!")
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            // Bersihkan data lama
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

            // Memasukkan data baru secara sekuensial
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

            // Pulihkan internal media files (foto karyawan, audio azan, tanda tangan, avatar, font)
            val filesToRestore = backupObj.internalMediaFiles ?: backupObj.customAzanFiles
            filesToRestore?.let { restoreInternalFilesFromMap(context, it) }

            // Pulihkan preferences if present
            backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_prefs", it) }
            backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, "pembukuan_usaha_prefs", it) }
            backupObj.absensiPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_absensi_prefs", it) }
            backupObj.idCardPrefs?.let { restoreSharedPrefsFromMap(context, "singghara_idcard_prefs", it) }

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
            // Ambil snapshot data terakhir
            val shoppingList = allShoppingItems.first()
            val notesList = allNotes.first()
            val target = activeSavingTarget.first()
            val transactionsList = allSavingTransactions.first()
            val agendasList = allAgendas.first()
            val quranBookmarksList = allQuranBookmarks.first()
            val quranHistoryList = allQuranHistory.first()
            val mindMapsList = allMindMaps.first()
            val jadwalList = allJadwal.first()
            val anggotaList = allAnggota.first()
            val presensiList = allPresensi.first()
            val gajiList = allGaji.first()
            val belanjaPasarList = allBelanjaPasarItems.first()

            val mediaFiles = getInternalFilesMap(context)

            val backupObj = FullBackupData(
                shopping = shoppingList,
                notes = notesList,
                savingTarget = target,
                savingTransactions = transactionsList,
                agendas = agendasList,
                quranBookmarks = quranBookmarksList,
                quranHistory = quranHistoryList,
                mindMaps = mindMapsList,
                jadwalKegiatan = jadwalList,
                anggotaList = anggotaList,
                presensiList = presensiList,
                gajiList = gajiList,
                belanjaPasarList = belanjaPasarList,
                mySinggharaPrefs = getSharedPrefsMap(context, "mysingghara_prefs"),
                pembukuanUsahaPrefs = getSharedPrefsMap(context, "pembukuan_usaha_prefs"),
                absensiPrefs = getSharedPrefsMap(context, "mysingghara_absensi_prefs"),
                idCardPrefs = getSharedPrefsMap(context, "singghara_idcard_prefs"),
                customAzanFiles = mediaFiles,
                internalMediaFiles = mediaFiles
            )

            val jsonString = backupAdapter.indent("  ").toJson(backupObj)

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

            // Bersihkan data lama
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

            // Masukkan data baru dari backup
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

            // Pulihkan internal media files (foto karyawan, audio azan, tanda tangan, avatar, font)
            val filesToRestore = backupObj.internalMediaFiles ?: backupObj.customAzanFiles
            filesToRestore?.let { restoreInternalFilesFromMap(context, it) }

            // Pulihkan preferences if present
            backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_prefs", it) }
            backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, "pembukuan_usaha_prefs", it) }
            backupObj.absensiPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_absensi_prefs", it) }
            backupObj.idCardPrefs?.let { restoreSharedPrefsFromMap(context, "singghara_idcard_prefs", it) }

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
