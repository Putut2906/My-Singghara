package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Data model for Azan MP3 file hosted on Google Drive.
 */
data class DriveAzanItem(
    val id: String,
    val name: String,
    val expectedSizeBytes: Long = 0L
)

/**
 * Download state for Azan audio files.
 */
sealed class AzanDownloadState {
    object Idle : AzanDownloadState()
    data class Downloading(
        val currentFileName: String,
        val downloadedCount: Int,
        val totalCount: Int,
        val progressPercent: Int
    ) : AzanDownloadState()
    data class Success(val fileNames: List<String>, val totalFiles: Int) : AzanDownloadState()
    data class Error(val errorMessage: String) : AzanDownloadState()
}

/**
 * Downloader that automatically fetches Azan MP3 files from Google Drive
 * folder: https://drive.google.com/drive/folders/1WyXB_KVvAMqlcbmzs3m1LBPUPGsyY9me?usp=sharing
 * and stores them into the app's MP3 folder (My Singghara/Data/mp3).
 */
object AzanDownloader {

    private const val TAG = "AzanDownloader"
    const val DRIVE_FOLDER_URL = "https://drive.google.com/drive/folders/1WyXB_KVvAMqlcbmzs3m1LBPUPGsyY9me?usp=sharing"
    const val DRIVE_FOLDER_ID = "1WyXB_KVvAMqlcbmzs3m1LBPUPGsyY9me"

    /**
     * Fallback catalog of known Azan audio files in the target Google Drive folder.
     * Guarantees files are downloaded even if dynamic folder scraping is hindered.
     */
    val DEFAULT_AZAN_FILES: List<DriveAzanItem> = listOf(
        DriveAzanItem(
            id = "1WQPv9uQ2u2rn5WOEJLgy5iB-J-GVQ27u",
            name = "adzan_turkey-1.mp3",
            expectedSizeBytes = 1443377L
        ),
        DriveAzanItem(
            id = "1M7aBViNzt92mvxUgy82TfSQzVxkGhLox",
            name = "adzan_turkey-2.mp3",
            expectedSizeBytes = 1587449L
        ),
        DriveAzanItem(
            id = "1s2D1wFAXuZPI4Rc_g3hjQBl4WGYXv0Uu",
            name = "fajr_128_44.mp3",
            expectedSizeBytes = 2996349L
        ),
        DriveAzanItem(
            id = "1noh3oDPcEU5zwDUlJFpEXRA9IgnNgOtx",
            name = "Madina-Adzan-Fajr.mp3",
            expectedSizeBytes = 1792839L
        )
    )

    private val _downloadState = MutableStateFlow<AzanDownloadState>(AzanDownloadState.Idle)
    val downloadState: StateFlow<AzanDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isDownloading = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory cookie jar to handle Google Drive consent/confirmation redirects
    private val cookieJar = object : CookieJar {
        private val cookieStore = CopyOnWriteArrayList<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (newCookie in cookies) {
                cookieStore.removeAll {
                    it.name == newCookie.name &&
                    it.domain == newCookie.domain &&
                    it.path == newCookie.path
                }
                cookieStore.add(newCookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookieStore.removeAll { it.expiresAt < now }
            val matched = mutableListOf<Cookie>()
            for (c in cookieStore) {
                if (c.matches(url) || (url.host.endsWith("google.com") && c.domain.contains("google"))) {
                    matched.add(c)
                }
            }
            return matched
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Start background download if not already in progress.
     */
    fun startDownloadAzanMp3s(context: Context, force: Boolean = false) {
        if (isDownloading) {
            Log.d(TAG, "Pengunduhan Azan MP3 sudah sedang berjalan.")
            return
        }
        scope.launch {
            try {
                downloadAzanMp3s(context.applicationContext, force)
            } catch (e: Exception) {
                Log.e(TAG, "Error dalam pengunduhan Azan MP3: ${e.message}", e)
                _downloadState.value = AzanDownloadState.Error(e.localizedMessage ?: "Gagal mengunduh berkas audio azan")
            }
        }
    }

    /**
     * Downloads Azan MP3s into the app's target MP3 folder.
     */
    suspend fun downloadAzanMp3s(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (isDownloading) return@withContext false
        isDownloading = true

        try {
            val targetDir = SinggharaFolderManager.getMp3Folder(context)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // 0. Ekstrak file MP3 bawaan dari paket aplikasi (assets) secara offline terlebih dahulu
            SinggharaFolderManager.seedDefaultMp3sFromAssets(context)

            // 1. Fetch file list dynamically from Google Drive folder, merged with fallback list
            val targetFiles = discoverDriveFiles()
            Log.d(TAG, "Daftar berkas azan Google Drive yang akan disinkronkan (${targetFiles.size} berkas): ${targetFiles.map { it.name }}")

            val downloadedNames = mutableListOf<String>()
            var completedCount = 0

            for ((index, item) in targetFiles.withIndex()) {
                val destFile = File(targetDir, item.name)

                // Skip download if file already exists with valid size and not forced
                if (!force && destFile.exists() && destFile.length() > 50_000L) {
                    Log.d(TAG, "Berkas ${item.name} sudah ada (${destFile.length()} bytes). Lewati download.")
                    completedCount++
                    downloadedNames.add(item.name)
                    continue
                }

                _downloadState.value = AzanDownloadState.Downloading(
                    currentFileName = item.name,
                    downloadedCount = completedCount,
                    totalCount = targetFiles.size,
                    progressPercent = ((completedCount.toFloat() / targetFiles.size.toFloat()) * 100).toInt()
                )

                var success = downloadSingleFile(item, destFile)
                if (!success && (!destFile.exists() || destFile.length() < 10_000L)) {
                    // Fallback ke paket pemasangan aplikasi (assets) jika file Google Drive dihapus atau offline
                    SinggharaFolderManager.seedDefaultMp3sFromAssets(context)
                    if (destFile.exists() && destFile.length() > 0L) {
                        success = true
                    }
                }

                if (destFile.exists() && destFile.length() > 0L) {
                    completedCount++
                    downloadedNames.add(item.name)
                    Log.d(TAG, "Berkas ${item.name} siap (${destFile.length()} bytes)")
                } else {
                    Log.w(TAG, "Berkas ${item.name} tidak dapat diunduh dan tidak ada di aset")
                }
            }

            val availableFiles = SinggharaFolderManager.getAvailableMp3Files()
            _downloadState.value = AzanDownloadState.Success(
                fileNames = if (downloadedNames.isNotEmpty()) downloadedNames else availableFiles.map { it.name },
                totalFiles = if (downloadedNames.isNotEmpty()) targetFiles.size else availableFiles.size
            )
            Log.d(TAG, "Selesai sinkronisasi Azan MP3. Tersedia: ${availableFiles.size} berkas di folder MP3.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal dalam proses sinkronisasi Azan: ${e.message}", e)
            val availableFiles = SinggharaFolderManager.getAvailableMp3Files()
            if (availableFiles.isNotEmpty()) {
                _downloadState.value = AzanDownloadState.Success(
                    fileNames = availableFiles.map { it.name },
                    totalFiles = availableFiles.size
                )
                true
            } else {
                _downloadState.value = AzanDownloadState.Error(e.localizedMessage ?: "Terjadi kesalahan saat memproses audio")
                false
            }
        } finally {
            isDownloading = false
        }
    }

    /**
     * Discovers Azan MP3 files from the Google Drive folder.
     * Combines dynamic folder scraping with default catalog.
     */
    private fun discoverDriveFiles(): List<DriveAzanItem> {
        val discoveredMap = LinkedHashMap<String, DriveAzanItem>()

        // 1. Put default items first
        for (item in DEFAULT_AZAN_FILES) {
            discoveredMap[item.name.lowercase()] = item
        }

        // 2. Try scraping folder HTML
        try {
            val url = "https://drive.google.com/drive/folders/$DRIVE_FOLDER_ID"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                parseDriveItemsFromHtml(html, discoveredMap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal query dinamis Google Drive folder, gunakan katalog bawaan: ${e.message}")
        }

        return discoveredMap.values.toList()
    }

    private fun parseDriveItemsFromHtml(html: String, map: MutableMap<String, DriveAzanItem>) {
        try {
            // Find _DRIVE_ivd block or general patterns
            val driveIvdMatcher = Pattern.compile("window\\[['_A-Za-z0-9]+\\]\\s*=\\s*'([^']+)'").matcher(html)
            val searchTexts = mutableListOf<String>()
            searchTexts.add(html)
            while (driveIvdMatcher.find()) {
                val rawGroup = driveIvdMatcher.group(1) ?: ""
                try {
                    // Unescape unicode
                    val unescaped = rawGroup.replace("\\\"", "\"").replace("\\/", "/")
                    searchTexts.add(unescaped)
                } catch (_: Exception) {}
            }

            for (text in searchTexts) {
                val pattern = Pattern.compile("([a-zA-Z0-9_-]{28,35}).{1,120}?([a-zA-Z0-9_\\-\\.]+\\.mp3)", Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    val fileId = matcher.group(1) ?: ""
                    val fileName = matcher.group(2) ?: ""
                    if (fileId.isNotBlank() && fileName.endsWith(".mp3", ignoreCase = true) && !fileId.startsWith("data-")) {
                        val key = fileName.lowercase()
                        if (!map.containsKey(key)) {
                            map[key] = DriveAzanItem(id = fileId, name = fileName)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Downloads a single Azan file from Google Drive, handling direct downloads
     * and confirmation pages when needed.
     */
    private fun downloadSingleFile(item: DriveAzanItem, destFile: File): Boolean {
        val tempFile = File(destFile.parentFile, "${destFile.name}.download.tmp")
        if (tempFile.exists()) tempFile.delete()

        val downloadUrls = listOf(
            "https://drive.usercontent.google.com/download?id=${item.id}&export=download&confirm=t",
            "https://drive.google.com/uc?export=download&id=${item.id}&confirm=t"
        )

        for (url in downloadUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                    .addHeader("Accept", "*/*")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val contentType = response.header("Content-Type", "") ?: ""

                // Check if Google Drive returned HTML confirmation page
                if (contentType.contains("text/html", ignoreCase = true)) {
                    val html = response.body?.string() ?: ""
                    response.close()

                    // Try bypass via confirmation form
                    val bypassed = downloadViaConfirmationPage(html, item.id, tempFile)
                    if (bypassed && tempFile.exists() && tempFile.length() > 50_000L) {
                        if (destFile.exists()) destFile.delete()
                        return tempFile.renameTo(destFile)
                    }
                    continue
                }

                if (response.isSuccessful) {
                    val body = response.body ?: continue
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempFile.exists() && tempFile.length() > 10_000L) {
                        if (destFile.exists()) destFile.delete()
                        val renamed = tempFile.renameTo(destFile)
                        if (renamed) return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengunduh ${item.name} dari $url: ${e.message}")
            }
        }
        return false
    }

    private fun downloadViaConfirmationPage(html: String, fileId: String, tempFile: File): Boolean {
        try {
            val formActionMatcher = Pattern.compile("<form\\b[^>]*action=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            val rawAction = if (formActionMatcher.find()) {
                formActionMatcher.group(1) ?: "https://drive.usercontent.google.com/download"
            } else {
                "https://drive.usercontent.google.com/download"
            }

            val baseActionUrl = if (rawAction.startsWith("/")) {
                "https://drive.usercontent.google.com$rawAction"
            } else {
                rawAction.replace("&amp;", "&")
            }

            val inputPattern = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE)
            val inputMatcher = inputPattern.matcher(html)
            val formParams = LinkedHashMap<String, String>()

            while (inputMatcher.find()) {
                val tag = inputMatcher.group()
                val nameMatcher = Pattern.compile("name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag)
                val valMatcher = Pattern.compile("value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(tag)
                if (nameMatcher.find() && valMatcher.find()) {
                    val k = nameMatcher.group(1) ?: ""
                    val v = valMatcher.group(1) ?: ""
                    if (k.isNotBlank()) {
                        formParams[k] = v
                    }
                }
            }

            if (!formParams.containsKey("id")) formParams["id"] = fileId
            if (!formParams.containsKey("export")) formParams["export"] = "download"
            if (!formParams.containsKey("confirm")) formParams["confirm"] = "t"

            val queryBuilder = StringBuilder()
            formParams.forEach { (k, v) ->
                if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
                queryBuilder.append(URLEncoder.encode(k, "UTF-8"))
                queryBuilder.append("=")
                queryBuilder.append(URLEncoder.encode(v, "UTF-8"))
            }

            val directUrl = "$baseActionUrl?$queryBuilder"
            val req = Request.Builder()
                .url(directUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
                .get()
                .build()

            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body ?: return false
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return tempFile.exists() && tempFile.length() > 50_000L
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal download lewat confirmation page: ${e.message}")
        }
        return false
    }
}
