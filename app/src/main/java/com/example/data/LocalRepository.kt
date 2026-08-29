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
        val mySinggharaPrefs: Map<String, Any?>? = null,
        val pembukuanUsahaPrefs: Map<String, Any?>? = null,
        val customAzanFiles: Map<String, String>? = null
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
        
        editor.commit()
    }

    private fun getCustomAzanFilesMap(context: Context): Map<String, String> {
        val filesMap = mutableMapOf<String, String>()
        val filesDir = context.filesDir
        val files = filesDir.listFiles { file ->
            file.isFile && (
                file.name.startsWith("azan_custom_") ||
                file.name.startsWith("custom_") ||
                file.name.endsWith(".ttf") ||
                file.name.endsWith(".otf") ||
                file.name.endsWith(".mp3") ||
                file.name.endsWith(".png") ||
                file.name.endsWith(".jpg")
            )
        }
        files?.forEach { file ->
            try {
                val bytes = file.readBytes()
                val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                filesMap[file.name] = base64Str
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return filesMap
    }

    private fun restoreCustomAzanFilesFromMap(context: Context, filesMap: Map<String, String>) {
        val filesDir = context.filesDir
        filesMap.forEach { (filename, base64Str) ->
            try {
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val file = java.io.File(filesDir, filename)
                if (file.exists()) {
                    file.delete()
                }
                file.writeBytes(bytes)
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
                mySinggharaPrefs = getSharedPrefsMap(context, "mysingghara_prefs"),
                pembukuanUsahaPrefs = getSharedPrefsMap(context, "pembukuan_usaha_prefs"),
                customAzanFiles = getCustomAzanFilesMap(context)
            )

            val jsonString = backupAdapter.indent("  ").toJson(backupObj)

            // Tentukan target file eksternal publik (Download/MySingghara_Backup.json)
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && !downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val backupFile = File(downloadDir, "mysingghara_backup.json")

            backupFile.writeText(jsonString, Charsets.UTF_8)
            Result.success("Berhasil ekspor ke ${backupFile.absolutePath}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Membaca file JSON dari folder Download publik dan menyinkronkannya kembali ke dalam database Room.
     */
    suspend fun importDataFromPublicDownload(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupFile = File(downloadDir, "mysingghara_backup.json")
            
            if (!backupFile.exists()) {
                return@withContext Result.failure(Exception("File backup 'mysingghara_backup.json' tidak ditemukan di folder Download."))
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

            // Pulihkan custom Azan files jika ada
            backupObj.customAzanFiles?.let { restoreCustomAzanFilesFromMap(context, it) }

            // Pulihkan preferences if present
            backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_prefs", it) }
            backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, "pembukuan_usaha_prefs", it) }

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
                mySinggharaPrefs = getSharedPrefsMap(context, "mysingghara_prefs"),
                pembukuanUsahaPrefs = getSharedPrefsMap(context, "pembukuan_usaha_prefs"),
                customAzanFiles = getCustomAzanFilesMap(context)
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

            Result.success("Berhasil mengekspor cadangan data_aplikasi.json ke folder SAF terpilih!")
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

            // Pulihkan custom Azan files jika ada
            backupObj.customAzanFiles?.let { restoreCustomAzanFilesFromMap(context, it) }

            // Pulihkan preferences if present
            backupObj.mySinggharaPrefs?.let { restoreSharedPrefsFromMap(context, "mysingghara_prefs", it) }
            backupObj.pembukuanUsahaPrefs?.let { restoreSharedPrefsFromMap(context, "pembukuan_usaha_prefs", it) }

            Result.success("Berhasil memulihkan/mengimpor data dari folder kustom Anda!")
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
}
