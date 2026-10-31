package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Pengelola Struktur Folder Otomatis Aplikasi My Singghara.
 *
 * Mendukung penyimpanan publik (/storage/emulated/0/My Singghara/) saat izin tersedia,
 * dan secara otomatis menggunakan fallback aman (App External / Internal Storage)
 * jika izin akses storage belum diberikan, sehingga tidak menyebabkan crash ENOENT.
 */
object SinggharaFolderManager {

    private const val TAG = "SinggharaFolderManager"

    const val ROOT_FOLDER_NAME = "My Singghara"
    const val SUB_BACKUP = "Back Up"
    const val SUB_DATA = "Data"
    const val SUB_FONT = "font"
    const val SUB_MP3 = "mp3"

    @Volatile
    private var appContext: Context? = null

    val DISALLOWED_MP3_FILES = setOf(
        "azan_madinah_default.mp3",
        "adzan_madinah_default.mp3",
        "azan_isya.mp3",
        "adzan_isya.mp3",
        "azan_madinah.mp3",
        "adzan_madinah.mp3"
    )

    val OBSOLETE_BUNDLED_MP3_FILES = setOf(
        "adzan_makkah.mp3",
        "adzan_merdu.mp3",
        "adzan-shubuh-abu-hazim.mp3",
        "adzan_subuh.mp3",
        "alaqsa1_64_22.mp3",
        "mecca_56_22.mp3",
        "azan_madina.mp3",
        "azan_makkah.mp3",
        "azan_merdu.mp3",
        "azan_subuh.mp3"
    )

    fun initContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    /**
     * Memeriksa apakah penyimpanan publik /storage/emulated/0/My Singghara dapat ditulis
     */
    fun isPublicStorageWritable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    return false
                }
            }
            val root = File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME)
            if (!root.exists()) {
                val created = root.mkdirs()
                if (!created && !root.exists()) return false
            }
            val testFile = File(root, ".write_test_${System.currentTimeMillis()}")
            if (testFile.createNewFile()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menghapus file audio mp3 yang dilarang/tidak diinginkan dan file mp3 bawaan lama dari seluruh folder yang terdeteksi
     */
    fun removeDisallowedMp3Files() {
        val folders = getAllPossibleMp3Folders()
        for (folder in folders) {
            try {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        val lower = file.name.lowercase(Locale.getDefault())
                        if (file.isFile && (lower in DISALLOWED_MP3_FILES || lower in OBSOLETE_BUNDLED_MP3_FILES)) {
                            val deleted = file.delete()
                            Log.d(TAG, "Menghapus file mp3 usang/terlarang: ${file.name}, sukses=$deleted")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membersihkan file mp3 yang dihilangkan: ${e.message}")
            }
        }
    }

    /**
     * Mendapatkan direktori root My Singghara.
     * Mengutamakan /storage/emulated/0/My Singghara jika writable,
     * jika tidak, menggunakan lokasi aman app-specific external files dir / internal files.
     */
    fun getRootFolder(context: Context? = null): File {
        val ctx = context ?: appContext

        // 1. Coba public storage jika writable
        if (isPublicStorageWritable()) {
            val publicRoot = File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME)
            if (!publicRoot.exists()) publicRoot.mkdirs()
            if (publicRoot.exists() && publicRoot.canWrite()) {
                return publicRoot
            }
        }

        // 2. Fallback ke App-Specific External Storage (bebas izin di Android 10+)
        if (ctx != null) {
            val appExt = ctx.getExternalFilesDir(null)
            if (appExt != null) {
                val fallbackRoot = File(appExt, ROOT_FOLDER_NAME)
                if (!fallbackRoot.exists()) fallbackRoot.mkdirs()
                if (fallbackRoot.exists() && fallbackRoot.canWrite()) {
                    return fallbackRoot
                }
            }

            // 3. Fallback ke Internal Storage
            val internalRoot = File(ctx.filesDir, ROOT_FOLDER_NAME)
            if (!internalRoot.exists()) internalRoot.mkdirs()
            return internalRoot
        }

        val publicRoot = File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME)
        if (!publicRoot.exists()) {
            try { publicRoot.mkdirs() } catch (_: Exception) {}
        }
        return publicRoot
    }

    fun getBackupFolder(context: Context? = null): File {
        val folder = File(getRootFolder(context), SUB_BACKUP)
        if (!folder.exists()) {
            try { folder.mkdirs() } catch (_: Exception) {}
        }
        return folder
    }

    fun getDataFolder(context: Context? = null): File {
        val folder = File(getRootFolder(context), SUB_DATA)
        if (!folder.exists()) {
            try { folder.mkdirs() } catch (_: Exception) {}
        }
        return folder
    }

    fun getFontFolder(context: Context? = null): File {
        val folder = File(getDataFolder(context), SUB_FONT)
        if (!folder.exists()) {
            try { folder.mkdirs() } catch (_: Exception) {}
        }
        return folder
    }

    fun getMp3Folder(context: Context? = null): File {
        val folder = File(getDataFolder(context), SUB_MP3)
        if (!folder.exists()) {
            try { folder.mkdirs() } catch (_: Exception) {}
        }
        return folder
    }

    private fun getAllPossibleMp3Folders(): List<File> {
        val list = mutableListOf<File>()
        list.add(getMp3Folder())

        val publicFolder = File(Environment.getExternalStorageDirectory(), "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
        if (!list.contains(publicFolder)) list.add(publicFolder)

        val ctx = appContext
        if (ctx != null) {
            ctx.getExternalFilesDir(null)?.let {
                val f = File(it, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
                if (!list.contains(f)) list.add(f)
            }
            val internal = File(ctx.filesDir, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
            if (!list.contains(internal)) list.add(internal)
        }
        return list
    }

    private fun getAllPossibleFontFolders(): List<File> {
        val list = mutableListOf<File>()
        list.add(getFontFolder())

        val publicFolder = File(Environment.getExternalStorageDirectory(), "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_FONT")
        if (!list.contains(publicFolder)) list.add(publicFolder)

        val ctx = appContext
        if (ctx != null) {
            ctx.getExternalFilesDir(null)?.let {
                val f = File(it, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_FONT")
                if (!list.contains(f)) list.add(f)
            }
            val internal = File(ctx.filesDir, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_FONT")
            if (!list.contains(internal)) list.add(internal)
        }
        return list
    }

    /**
     * Memastikan seluruh struktur direktori My Singghara dibuat otomatis saat aplikasi dibuka.
     * Secara offline menyalin file MP3 azan bawaan dari paket aplikasi (assets/mp3) ke My Singghara/Data/mp3.
     */
    fun ensureFoldersExist(context: Context): Boolean {
        initContext(context)
        return try {
            val root = getRootFolder(context)
            if (!root.exists()) {
                root.mkdirs()
            }

            val backup = getBackupFolder(context)
            if (!backup.exists()) {
                backup.mkdirs()
            }

            val data = getDataFolder(context)
            if (!data.exists()) {
                data.mkdirs()
            }

            val font = getFontFolder(context)
            if (!font.exists()) {
                font.mkdirs()
            }

            val mp3 = getMp3Folder(context)
            if (!mp3.exists()) {
                mp3.mkdirs()
            }

            removeDisallowedMp3Files()
            writeReadmeFiles(context)
            seedDefaultFontsIfEmpty(context)
            // Salin file MP3 azan dari paket pemasangan aplikasi (assets) ke My Singghara/Data/mp3 (100% offline)
            seedDefaultMp3sFromAssets(context)
            removeDisallowedMp3Files()

            // Jika ada koneksi, periksa apakah ada pembaruan file di latar belakang
            AzanDownloader.startDownloadAzanMp3s(context)

            Log.d(TAG, "Struktur folder My Singghara siap di: ${root.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat struktur folder My Singghara: ${e.message}", e)
            tryFallbackDirs(context)
        }
    }

    private fun writeReadmeFiles(context: Context) {
        try {
            val root = getRootFolder(context)
            if (root.exists() && root.canWrite()) {
                val rootReadme = File(root, "BACA_PANDUAN_FOLDER.txt")
                if (!rootReadme.exists()) {
                    rootReadme.writeText(
                        """
                        ==============================================
                        STRUKTUR FOLDER RESMI APLIKASI MY SINGGHARA
                        ==============================================
                        Folder ini digunakan untuk data aplikasi My Singghara.

                        1. Sub-folder "Back Up":
                           Tempat berkas cadangan database (mysingghara_backup.json).

                        2. Sub-folder "Data/mp3":
                           Tempat file audio .mp3 (Adzan & Pemutar Audio).
                           Berkas MP3 adzan diunduh otomatis dari Google Drive folder.

                        3. Sub-folder "Data/font":
                           Tempat file font kustom (.ttf, .otf) jam digital.
                        ==============================================
                        """.trimIndent(),
                        Charsets.UTF_8
                    )
                }
            }

            val mp3Folder = getMp3Folder(context)
            if (mp3Folder.exists() && mp3Folder.canWrite()) {
                val mp3Readme = File(mp3Folder, "PANDUAN_FOLDER_MP3.txt")
                if (!mp3Readme.exists()) {
                    mp3Readme.writeText(
                        """
                        Folder MP3 - My Singghara
                        Letakkan file audio .mp3 adzan kustom atau musik di sini.
                        Aplikasi otomatis mendeteksi seluruh berkas .mp3 di folder ini.
                        Berkas MP3 adzan resmi diunduh otomatis dari Google Drive.
                        """.trimIndent(),
                        Charsets.UTF_8
                    )
                }
            }

            val fontFolder = getFontFolder(context)
            if (fontFolder.exists() && fontFolder.canWrite()) {
                val fontReadme = File(fontFolder, "PANDUAN_FOLDER_FONT.txt")
                if (!fontReadme.exists()) {
                    fontReadme.writeText(
                        """
                        Folder Font (.ttf) - My Singghara
                        Letakkan file font TrueType (.ttf) atau OpenType (.otf) di sini.
                        """.trimIndent(),
                        Charsets.UTF_8
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun seedDefaultFontsIfEmpty(context: Context) {
        try {
            val fontFolder = getFontFolder(context)
            val effectiveFontFolder = if (fontFolder.exists() && fontFolder.canWrite()) {
                fontFolder
            } else {
                val fallback = context.getExternalFilesDir(null)?.let { File(it, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_FONT") }
                    ?: File(context.filesDir, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_FONT")
                fallback.mkdirs()
                fallback
            }

            if (!effectiveFontFolder.exists()) {
                effectiveFontFolder.mkdirs()
            }

            val fontFiles = listOf(
                "Technology.ttf",
                "Technology-Bold.ttf",
                "Technology-Italic.ttf",
                "Technology-BoldItalic.ttf"
            )

            fontFiles.forEach { fontName ->
                val targetFont = File(effectiveFontFolder, fontName)
                if (!targetFont.exists() || targetFont.length() == 0L) {
                    try {
                        targetFont.parentFile?.mkdirs()
                        context.assets.open("fonts/$fontName").use { input ->
                            FileOutputStream(targetFont).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Berhasil menyalin font $fontName ke: ${targetFont.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Lewati penyalinan font $fontName: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal seed font default: ${e.message}")
        }
    }

    /**
     * Menyalin file MP3 azan bawaan dari paket aplikasi (assets/mp3) ke direktori My Singghara/Data/mp3.
     * Berjalan sepenuhnya offline tanpa memerlukan koneksi internet atau ketersediaan file di Google Drive.
     */
    fun seedDefaultMp3sFromAssets(context: Context) {
        try {
            val targets = mutableListOf<File>()
            val primaryMp3Folder = getMp3Folder(context)
            targets.add(primaryMp3Folder)

            // Public storage folder /storage/emulated/0/My Singghara/Data/mp3
            val publicMp3Folder = File(Environment.getExternalStorageDirectory(), "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
            if (!targets.contains(publicMp3Folder)) {
                targets.add(publicMp3Folder)
            }

            // Fallback storage folders
            context.getExternalFilesDir(null)?.let {
                val f = File(it, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
                if (!targets.contains(f)) targets.add(f)
            }
            val internalMp3Folder = File(context.filesDir, "$ROOT_FOLDER_NAME/$SUB_DATA/$SUB_MP3")
            if (!targets.contains(internalMp3Folder)) targets.add(internalMp3Folder)

            val mp3Files = listOf(
                "adzan_turkey-1.mp3",
                "adzan_turkey-2.mp3",
                "fajr_128_44.mp3",
                "Madina-Adzan-Fajr.mp3"
            )

            for (targetFolder in targets) {
                try {
                    if (!targetFolder.exists()) {
                        targetFolder.mkdirs()
                    }
                    if (!targetFolder.exists() || !targetFolder.canWrite()) {
                        continue
                    }

                    for (fileName in mp3Files) {
                        val targetFile = File(targetFolder, fileName)
                        if (!targetFile.exists() || targetFile.length() < 10_000L) {
                            try {
                                context.assets.open("mp3/$fileName").use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.d(TAG, "Berhasil menyalin mp3 bawaan $fileName ke: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                            } catch (e: Exception) {
                                Log.w(TAG, "Lewati penyalinan mp3 bawaan $fileName ke ${targetFolder.absolutePath}: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal seed ke folder ${targetFolder.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal seed default MP3 dari assets: ${e.message}")
        }
    }

    /**
     * Mengambil file audio azan default dari folder mp3 (My Singghara/Data/mp3).
     * Jika folder kosong, otomatis mengekstrak file dari paket aplikasi (assets/mp3) terlebih dahulu.
     * Mengutamakan file adzan yang cocok dengan nama sholat (misal: Subuh/Fajr -> fajr_128_44 / Madina-Adzan-Fajr).
     */
    fun getDefaultAzanFile(context: Context? = null, prayerName: String? = null): File? {
        var mp3Files = getAvailableMp3Files()
        if (mp3Files.isEmpty() && context != null) {
            seedDefaultMp3sFromAssets(context)
            mp3Files = getAvailableMp3Files()
        }
        if (mp3Files.isEmpty()) return null

        val pName = prayerName?.lowercase(Locale.getDefault()) ?: ""
        if (pName.contains("subuh") || pName.contains("fajr")) {
            val fajrCandidate = mp3Files.firstOrNull {
                val n = it.name.lowercase(Locale.getDefault())
                n.contains("fajr") || n.contains("subuh") || n.contains("shubuh")
            }
            if (fajrCandidate != null) return fajrCandidate
        }

        val adzanCandidate = mp3Files.firstOrNull {
            val n = it.name.lowercase(Locale.getDefault())
            n.contains("adzan") || n.contains("azan") || n.contains("turkey") || n.contains("madina")
        }
        if (adzanCandidate != null) return adzanCandidate

        return mp3Files.firstOrNull()
    }

    private fun tryFallbackDirs(context: Context): Boolean {
        return try {
            val fallbackParent = context.getExternalFilesDir(null) ?: context.filesDir
            val fallbackRoot = File(fallbackParent, ROOT_FOLDER_NAME)
            fallbackRoot.mkdirs()
            File(fallbackRoot, SUB_BACKUP).mkdirs()
            val data = File(fallbackRoot, SUB_DATA).apply { mkdirs() }
            File(data, SUB_FONT).mkdirs()
            File(data, SUB_MP3).mkdirs()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengambil daftar semua file .mp3 yang tersedia
     */
    fun getAvailableMp3Files(): List<File> {
        return try {
            removeDisallowedMp3Files()
            val result = mutableListOf<File>()
            val seen = mutableSetOf<String>()

            for (folder in getAllPossibleMp3Folders()) {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".mp3", ignoreCase = true)) {
                            val lower = file.name.lowercase()
                            if (lower in DISALLOWED_MP3_FILES) {
                                try { file.delete() } catch (_: Exception) {}
                            } else if (!seen.contains(lower) && file.length() > 0L) {
                                seen.add(lower)
                                result.add(file)
                            }
                        }
                    }
                }
            }
            result.sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAvailablePngFiles(): List<File> {
        return try {
            val result = mutableListOf<File>()
            val seen = mutableSetOf<String>()

            for (folder in getAllPossibleFontFolders()) {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.endsWith(".png", ignoreCase = true)) {
                            val lower = file.name.lowercase()
                            if (!seen.contains(lower) && file.length() > 0L) {
                                seen.add(lower)
                                result.add(file)
                            }
                        }
                    }
                }
            }
            result.sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAvailableFontFiles(): List<File> {
        return try {
            val result = mutableListOf<File>()
            val seen = mutableSetOf<String>()

            for (folder in getAllPossibleFontFolders()) {
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.forEach { file ->
                        if (file.isFile && (file.name.endsWith(".ttf", ignoreCase = true) || file.name.endsWith(".otf", ignoreCase = true))) {
                            val lower = file.name.lowercase()
                            if (!seen.contains(lower) && file.length() > 0L) {
                                seen.add(lower)
                                result.add(file)
                            }
                        }
                    }
                }
            }
            result.sortedBy { it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveUriToMp3Folder(context: Context, uri: Uri, customName: String? = null): File? {
        return try {
            ensureFoldersExist(context)
            var fileName = customName ?: "audio_${System.currentTimeMillis()}.mp3"
            if (customName == null) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrEmpty()) fileName = name
                    }
                }
            }
            if (!fileName.endsWith(".mp3", ignoreCase = true)) {
                fileName += ".mp3"
            }

            val targetFile = File(getMp3Folder(context), fileName)
            targetFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan ke folder MP3: ${e.message}", e)
            null
        }
    }

    fun getPrimaryBackupFile(context: Context? = null): File {
        val backupDir = getBackupFolder(context)
        if (!backupDir.exists()) {
            try { backupDir.mkdirs() } catch (_: Exception) {}
        }
        return File(backupDir, "mysingghara_backup.json")
    }
}
