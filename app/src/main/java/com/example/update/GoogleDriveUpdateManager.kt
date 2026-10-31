package com.example.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Data model for an APK file stored on Google Drive.
 */
data class DriveApkItem(
    val id: String,
    val name: String,
    val size: Long,
    val url: String,
    val createdTimeRaw: String = "",
    val timestampMillis: Long = 0L
) {
    val sizeFormatted: String
        get() {
            if (size <= 0) return "0 MB"
            val mb = size.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB", mb)
        }

    val formattedUploadTime: String
        get() {
            if (timestampMillis > 0L) {
                return try {
                    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
                    sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                    sdf.format(java.util.Date(timestampMillis))
                } catch (_: Exception) {
                    createdTimeRaw.ifBlank { "Waktu tidak tersedia" }
                }
            }
            if (createdTimeRaw.isNotBlank()) {
                val formatted = GoogleDriveUpdateManager.formatRawDate(createdTimeRaw)
                if (formatted != createdTimeRaw) return formatted
            }
            return "Waktu upload belum disertakan server"
        }
}

/**
 * Result evaluation when comparing an APK against local installation.
 */
data class DriveApkEvaluation(
    val isNewer: Boolean,
    val isNewerByTimestamp: Boolean,
    val reason: String
)

/**
 * State representation for Google Drive update checking.
 */
sealed class DriveUpdateCheckState {
    data class Idle(val lastCheckedMillis: Long = 0L) : DriveUpdateCheckState()
    data class Loading(val message: String = "Menghubungkan ke Google Drive via Apps Script...") : DriveUpdateCheckState()
    data class UpToDate(
        val latestApk: DriveApkItem?,
        val currentAppVersion: String = BuildConfig.VERSION_NAME,
        val allApks: List<DriveApkItem> = emptyList(),
        val lastCheckedMillis: Long = System.currentTimeMillis()
    ) : DriveUpdateCheckState()
    data class UpdateAvailable(
        val latestApk: DriveApkItem,
        val currentAppVersion: String = BuildConfig.VERSION_NAME,
        val allApks: List<DriveApkItem> = emptyList(),
        val isNewerByTimestamp: Boolean = true,
        val detectionReason: String = "Ditemukan berkas installer pembaruan di Google Drive."
    ) : DriveUpdateCheckState()
    data class NoApkFound(
        val totalFilesChecked: Int = 0,
        val message: String = "Belum ada file installer .apk yang dibagikan ke Google Drive."
    ) : DriveUpdateCheckState()
    data class Error(
        val errorMessage: String,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : DriveUpdateCheckState()
}

/**
 * Manager responsible for checking, downloading, and installing app updates
 * directly from Google Drive via Google Apps Script web app.
 */
object GoogleDriveUpdateManager {

    private const val PREFS_NAME = "mysingghara_prefs"
    const val DEFAULT_APPS_SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec"

    private const val KEY_APPS_SCRIPT_URL = "apps_script_url"
    private const val KEY_AUTO_CHECK = "drive_auto_check_startup"
    private const val KEY_AUTO_INSTALL = "drive_auto_install_latest"
    private const val KEY_LAST_CHECKED = "drive_last_checked_millis"
    private const val KEY_LAST_INSTALLED_TIMESTAMP = "drive_last_installed_apk_timestamp"
    private const val KEY_LAST_INSTALLED_FILE_ID = "drive_last_installed_file_id"
    private const val KEY_LAST_INSTALLED_FILE_SIZE = "drive_last_installed_file_size"
    private const val KEY_FIRST_LAUNCH_CHECKED = "drive_first_launch_checked"

    // Multi-domain CookieJar in memory to support Google Drive redirect & confirmation cookies
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
                if (c.matches(url)) {
                    matched.add(c)
                } else if (url.host.endsWith("google.com") && (c.domain.endsWith("google.com") || c.domain.contains("google"))) {
                    matched.add(c)
                }
            }
            return matched
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppsScriptUrl(context: Context): String {
        val saved = getPrefs(context).getString(KEY_APPS_SCRIPT_URL, "") ?: ""
        return if (saved.trim().isEmpty()) DEFAULT_APPS_SCRIPT_URL else saved.trim()
    }

    fun saveAppsScriptUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_APPS_SCRIPT_URL, url.trim()).apply()
    }

    fun isAutoCheckOnStartup(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CHECK, true)
    }

    fun setAutoCheckOnStartup(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
    }

    fun isAutoInstallLatest(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_INSTALL, true)
    }

    fun setAutoInstallLatest(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_INSTALL, enabled).apply()
    }

    fun getLastCheckedMillis(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_CHECKED, 0L)
    }

    private fun saveLastCheckedMillis(context: Context, millis: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_CHECKED, millis).apply()
    }

    fun getLastInstalledTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_INSTALLED_TIMESTAMP, 0L)
    }

    fun getLastInstalledFileId(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_INSTALLED_FILE_ID, "") ?: ""
    }

    fun getLastInstalledFileSize(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_INSTALLED_FILE_SIZE, 0L)
    }

    fun saveInstalledApkInfo(context: Context, fileId: String, timestamp: Long, size: Long = 0L) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_INSTALLED_TIMESTAMP, timestamp)
            .putString(KEY_LAST_INSTALLED_FILE_ID, fileId)
            .putLong(KEY_LAST_INSTALLED_FILE_SIZE, size)
            .apply()
    }

    fun clearInstalledApkHistory(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_LAST_INSTALLED_TIMESTAMP)
            .remove(KEY_LAST_INSTALLED_FILE_ID)
            .remove(KEY_LAST_INSTALLED_FILE_SIZE)
            .apply()
    }

    /**
     * Checks if this is the first time the app is launched after installation.
     * Used to suppress automatic update popups on fresh installs.
     */
    fun isFirstLaunchAfterInstall(context: Context): Boolean {
        return !getPrefs(context).getBoolean(KEY_FIRST_LAUNCH_CHECKED, false)
    }

    /**
     * Marks the first launch as completed so future launches can perform normal update checks.
     */
    fun markFirstLaunchDone(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_LAUNCH_CHECKED, true).apply()
    }

    /**
     * Checks Google Drive folder via Apps Script, filters for .apk files,
     * sorts by upload timestamp (jam upload) descending, and evaluates update status.
     */
    suspend fun checkForDriveUpdate(context: Context): DriveUpdateCheckState = withContext(Dispatchers.IO) {
        val scriptUrl = getAppsScriptUrl(context)
        if (scriptUrl.isBlank()) {
            return@withContext DriveUpdateCheckState.Error("URL Google Apps Script belum dikonfigurasi.")
        }

        saveLastCheckedMillis(context, System.currentTimeMillis())

        try {
            val listUrl = if (scriptUrl.contains("?")) "$scriptUrl&action=list" else "$scriptUrl?action=list"
            val request = Request.Builder()
                .url(listUrl)
                .addHeader("User-Agent", "MySingghara-Android-App")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful || bodyString.isBlank()) {
                return@withContext DriveUpdateCheckState.Error("Server merespons kode HTTP ${response.code}")
            }

            val json = JSONObject(bodyString)
            if (json.optString("status") != "success") {
                val msg = json.optString("message", "Gagal membaca berkas dari Google Drive.")
                return@withContext DriveUpdateCheckState.Error(msg)
            }

            val filesArray = json.optJSONArray("files")
            if (filesArray == null || filesArray.length() == 0) {
                return@withContext DriveUpdateCheckState.NoApkFound(0)
            }

            val apkList = mutableListOf<DriveApkItem>()

            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                val fileName = obj.optString("name", "")
                val mimeType = obj.optString("mimeType", "")
                val isApk = fileName.endsWith(".apk", ignoreCase = true) ||
                        mimeType.contains("android.package-archive", ignoreCase = true)

                if (isApk) {
                    val id = obj.optString("id", "")
                    val size = obj.optLong("size", 0L)
                    val url = obj.optString("url", "")

                    // Try parsing numeric timestamp first
                    var timestamp = obj.optLong("timestamp", 0L)
                    if (timestamp <= 0L) timestamp = obj.optLong("updatedTimestamp", 0L)
                    if (timestamp <= 0L) timestamp = obj.optLong("createdTimestamp", 0L)
                    if (timestamp <= 0L) timestamp = obj.optLong("time", 0L)
                    if (timestamp <= 0L) timestamp = obj.optLong("ts", 0L)
                    if (timestamp in 1..9999999999L) timestamp *= 1000L

                    // Check all possible date string fields
                    val dateRaw = obj.optString("lastUpdated",
                        obj.optString("last_updated",
                            obj.optString("modifiedTime",
                                obj.optString("modified_time",
                                    obj.optString("dateModified",
                                        obj.optString("date",
                                            obj.optString("dateCreated",
                                                obj.optString("date_created",
                                                    obj.optString("createdTime",
                                                        obj.optString("created_time",
                                                            obj.optString("uploadDate", "")
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )

                    if (timestamp <= 0L && dateRaw.isNotBlank()) {
                        timestamp = parseDateToMillis(dateRaw)
                    }

                    // If timestamp could not be parsed from JSON, try parsing from file name
                    if (timestamp <= 0L) {
                        timestamp = parseTimestampFromFileName(fileName)
                    }

                    apkList.add(
                        DriveApkItem(
                            id = id,
                            name = fileName,
                            size = size,
                            url = url,
                            createdTimeRaw = dateRaw,
                            timestampMillis = timestamp
                        )
                    )
                }
            }

            if (apkList.isEmpty()) {
                return@withContext DriveUpdateCheckState.NoApkFound(filesArray.length())
            }

            // Size of current locally installed APK
            val localInstalledApkSize = try {
                val sourceDir = context.applicationInfo.sourceDir
                if (!sourceDir.isNullOrBlank()) File(sourceDir).length() else 0L
            } catch (_: Exception) { 0L }

            // Smart Sorting:
            // 1. Newest timestamp first
            // 2. If timestamps equal / 0: prioritize APK with different size from installed (newer upload candidate)
            // 3. Prioritize file ending with .apk
            // 4. Higher semantic version in name
            apkList.sortWith(Comparator { a, b ->
                if (a.timestampMillis > 0L || b.timestampMillis > 0L) {
                    val cmp = b.timestampMillis.compareTo(a.timestampMillis)
                    if (cmp != 0) return@Comparator cmp
                }
                if (localInstalledApkSize > 0L) {
                    val diffA = Math.abs(a.size - localInstalledApkSize)
                    val diffB = Math.abs(b.size - localInstalledApkSize)
                    if (diffA > 10240L && diffB <= 10240L) return@Comparator -1
                    if (diffB > 10240L && diffA <= 10240L) return@Comparator 1
                }
                val aIsApkExt = a.name.endsWith(".apk", ignoreCase = true)
                val bIsApkExt = b.name.endsWith(".apk", ignoreCase = true)
                if (aIsApkExt && !bIsApkExt) return@Comparator -1
                if (!aIsApkExt && bIsApkExt) return@Comparator 1

                val vA = extractVersionName(a.name)
                val vB = extractVersionName(b.name)
                if (vA.isNotBlank() && vB.isNotBlank()) {
                    val vCmp = compareVersionStrings(vB, vA)
                    if (vCmp != 0) return@Comparator vCmp
                }
                b.size.compareTo(a.size)
            })

            // Check if any APK is newer, prioritizing the top candidate
            var newestApk = apkList.first()
            var newestEvaluation = evaluateDriveApk(context, newestApk)

            // If the first wasn't evaluated as newer, test if another in the list is newer
            if (!newestEvaluation.isNewer && apkList.size > 1) {
                for (item in apkList.drop(1)) {
                    val eval = evaluateDriveApk(context, item)
                    if (eval.isNewer) {
                        newestApk = item
                        newestEvaluation = eval
                        break
                    }
                }
            }

            if (newestEvaluation.isNewer) {
                DriveUpdateCheckState.UpdateAvailable(
                    latestApk = newestApk,
                    currentAppVersion = BuildConfig.VERSION_NAME,
                    allApks = apkList,
                    isNewerByTimestamp = newestEvaluation.isNewerByTimestamp,
                    detectionReason = newestEvaluation.reason
                )
            } else {
                DriveUpdateCheckState.UpToDate(
                    latestApk = newestApk,
                    currentAppVersion = BuildConfig.VERSION_NAME,
                    allApks = apkList
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            DriveUpdateCheckState.Error("Gagal menghubungi Google Drive: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Tests connectivity to a specified Google Apps Script URL and checks for APK files.
     */
    suspend fun testConnection(context: Context, testUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (testUrl.isBlank()) {
            return@withContext Pair(false, "URL Google Apps Script tidak boleh kosong.")
        }
        try {
            val url = if (testUrl.contains("?")) "$testUrl&action=list" else "$testUrl?action=list"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Pair(false, "Server merespons kode HTTP ${response.code}")
            }

            val json = JSONObject(body)
            if (json.optString("status") != "success") {
                val msg = json.optString("message", "Status respons bukan success.")
                return@withContext Pair(false, msg)
            }

            val filesArray = json.optJSONArray("files")
            val totalFiles = filesArray?.length() ?: 0
            var apkCount = 0
            var latestApkName = ""
            var latestTimeStr = ""
            var maxTs = -1L
            var hasUploadTime = false

            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val name = obj.optString("name", "")
                    val mime = obj.optString("mimeType", "")
                    if (name.endsWith(".apk", ignoreCase = true) || mime.contains("android.package-archive", ignoreCase = true)) {
                        apkCount++
                        var ts = obj.optLong("timestamp", 0L)
                        if (ts <= 0L) ts = obj.optLong("updatedTimestamp", 0L)
                        if (ts in 1..9999999999L) ts *= 1000L

                        val dateRaw = obj.optString("lastUpdated",
                            obj.optString("last_updated",
                                obj.optString("modifiedTime",
                                    obj.optString("date",
                                        obj.optString("dateCreated",
                                            obj.optString("createdTime", "")
                                        )
                                    )
                                )
                            )
                        )
                        if (ts <= 0L && dateRaw.isNotBlank()) {
                            ts = parseDateToMillis(dateRaw)
                        }
                        if (ts <= 0L) {
                            ts = parseTimestampFromFileName(name)
                        }

                        if (ts > 0L) hasUploadTime = true

                        if (ts > maxTs || latestApkName.isBlank()) {
                            maxTs = ts
                            latestApkName = name
                            latestTimeStr = if (ts > 0L) {
                                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
                                sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
                                sdf.format(Date(ts))
                            } else if (dateRaw.isNotBlank()) dateRaw else "Waktu belum disertakan server"
                        }
                    }
                }
            }

            val msg = if (apkCount > 0) {
                "Terhubung! Ditemukan $totalFiles file di Drive ($apkCount file APK). APK terbaru: '$latestApkName'."
            } else {
                "Terhubung! Ditemukan $totalFiles file di Drive, tetapi belum ada berkas installer APK."
            }
            Pair(true, msg)
        } catch (e: Exception) {
            Pair(false, "Gagal terhubung: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Evaluates whether an APK from Google Drive is newer than the currently installed app.
     * Uses a multi-criteria approach:
     * 1. Already installed Google Drive file ID check
     * 2. Semantic version in file name (v1.1 vs v1.0)
     * 3. New uploaded file ID check
     * 4. Physical file size difference (> 10KB diff with local base.apk)
     * 5. Upload timestamp comparison
     */
    fun evaluateDriveApk(context: Context, apkItem: DriveApkItem): DriveApkEvaluation {
        val lastInstalledTimestamp = getLastInstalledTimestamp(context)
        val lastInstalledFileId = getLastInstalledFileId(context)
        val lastInstalledFileSize = getLastInstalledFileSize(context)

        // Local APK file size currently running
        val localInstalledApkSize = try {
            val sourceDir = context.applicationInfo.sourceDir
            if (!sourceDir.isNullOrBlank()) File(sourceDir).length() else 0L
        } catch (_: Exception) {
            0L
        }

        // Install / Update timestamp from PackageManager
        val installedAppTime = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.lastUpdateTime
        } catch (_: Exception) {
            0L
        }

        // 1. CHECK IF THIS EXACT FILE WAS ALREADY INSTALLED FROM DRIVE
        if (lastInstalledFileId.isNotBlank() && apkItem.id == lastInstalledFileId) {
            return DriveApkEvaluation(
                isNewer = false,
                isNewerByTimestamp = false,
                reason = "Berkas APK ini (${apkItem.name}) sudah terpasang."
            )
        }

        // 2. CHECK IF A NEW FILE ID HAS BEEN UPLOADED TO DRIVE SINCE LAST INSTALLATION
        if (lastInstalledFileId.isNotBlank() && apkItem.id.isNotBlank() && apkItem.id != lastInstalledFileId) {
            return DriveApkEvaluation(
                isNewer = true,
                isNewerByTimestamp = apkItem.timestampMillis > 0L,
                reason = "Berkas APK terbaru (${apkItem.name}) tersedia di Google Drive."
            )
        }

        // 3. EVALUATION BY SEMANTIC VERSION IN FILE NAME (e.g. MySingghara-v1.1.apk vs v1.0)
        val versionNameInApk = extractVersionName(apkItem.name)
        val currentVersion = BuildConfig.VERSION_NAME
        if (versionNameInApk.isNotBlank()) {
            val vCmp = compareVersionStrings(versionNameInApk, currentVersion)
            if (vCmp > 0) {
                return DriveApkEvaluation(
                    isNewer = true,
                    isNewerByTimestamp = false,
                    reason = "Versi berkas APK di Drive (v$versionNameInApk) lebih tinggi dari versi saat ini (v$currentVersion)."
                )
            } else if (vCmp <= 0 && lastInstalledFileId.isBlank()) {
                return DriveApkEvaluation(
                    isNewer = false,
                    isNewerByTimestamp = false,
                    reason = "Aplikasi Anda sudah versi terbaru (v$currentVersion)."
                )
            }
        }

        // 4. EVALUATION BY UPLOAD TIMESTAMP (if available from server)
        if (apkItem.timestampMillis > 0L) {
            if (lastInstalledTimestamp > 0L && apkItem.timestampMillis > lastInstalledTimestamp + 2000L) {
                return DriveApkEvaluation(
                    isNewer = true,
                    isNewerByTimestamp = true,
                    reason = "Berkas APK terbaru (${apkItem.name}) tersedia di Google Drive."
                )
            }

            if (installedAppTime > 0L && apkItem.timestampMillis > installedAppTime + 5000L) {
                return DriveApkEvaluation(
                    isNewer = true,
                    isNewerByTimestamp = true,
                    reason = "Berkas APK terbaru (${apkItem.name}) tersedia di Google Drive."
                )
            }
        }

        // 5. EVALUATION BY FILE SIZE DIFFERENCE (only if APK re-uploaded with same version AND was previously recorded)
        if (apkItem.size > 0L && localInstalledApkSize > 0L) {
            val sizeDiff = Math.abs(apkItem.size - localInstalledApkSize)
            if (sizeDiff > 10240L && lastInstalledFileId.isNotBlank() && apkItem.id != lastInstalledFileId) {
                return DriveApkEvaluation(
                    isNewer = true,
                    isNewerByTimestamp = false,
                    reason = "Terdeteksi berkas APK pembaruan (${apkItem.name}, ${apkItem.sizeFormatted})."
                )
            }
        }

        return DriveApkEvaluation(
            isNewer = false,
            isNewerByTimestamp = false,
            reason = "Aplikasi Anda sudah versi terbaru (v$currentVersion)."
        )
    }

    /**
     * Determines whether the given APK from Drive is newer than the currently installed app.
     */
    fun isDriveApkNewer(context: Context, apkItem: DriveApkItem): Boolean {
        return evaluateDriveApk(context, apkItem).isNewer
    }

    /**
     * Downloads the APK from Google Drive with redirect handling and progress callback.
     */
    suspend fun downloadDriveApk(
        context: Context,
        fileItem: DriveApkItem,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val downloadDir = File(context.cacheDir, "drive_updates")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val cleanName = if (fileItem.name.endsWith(".apk", ignoreCase = true)) {
            fileItem.name
        } else {
            "${fileItem.name}.apk"
        }

        val targetFile = File(downloadDir, cleanName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        // Step 1: Request Google Drive download with confirm=t
        val initialUrl = "https://drive.google.com/uc?export=download&id=${fileItem.id}&confirm=t"
        val userAgent = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        var request = Request.Builder()
            .url(initialUrl)
            .addHeader("User-Agent", userAgent)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/octet-stream,*/*;q=0.8")
            .get()
            .build()

        var response = okHttpClient.newCall(request).execute()

        var contentType = response.header("Content-Type", "") ?: ""

        // If Google Drive returns an HTML warning or confirmation page (e.g. file is >25MB / executable):
        if (contentType.contains("text/html", ignoreCase = true)) {
            val html = response.body?.string() ?: ""
            response.close()

            // 1. Try parsing <form id="download-form" action="..."> or any form with action
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

            // Extract all hidden and text input tags inside the form
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

            // Guarantee essential parameters for Google Drive bypass
            if (!formParams.containsKey("id")) formParams["id"] = fileItem.id
            if (!formParams.containsKey("export")) formParams["export"] = "download"
            if (!formParams.containsKey("confirm")) formParams["confirm"] = "t"

            // Construct final direct download query string
            val queryBuilder = StringBuilder()
            formParams.forEach { (k, v) ->
                if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
                queryBuilder.append(URLEncoder.encode(k, "UTF-8"))
                queryBuilder.append("=")
                queryBuilder.append(URLEncoder.encode(v, "UTF-8"))
            }

            var directDownloadUrl = "$baseActionUrl?$queryBuilder"

            // Fallback: If form didn't give uuid, also inspect any anchor tags for confirmation links
            if (!formParams.containsKey("uuid")) {
                val linkMatcher = Pattern.compile("href=[\"']([^\"']*(?:confirm|export=download)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                if (linkMatcher.find()) {
                    val rawLink = (linkMatcher.group(1) ?: "").replace("&amp;", "&")
                    if (rawLink.contains("confirm", ignoreCase = true)) {
                        directDownloadUrl = if (rawLink.startsWith("/")) "https://drive.google.com$rawLink" else rawLink
                    }
                }
            }

            request = Request.Builder()
                .url(directDownloadUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "application/octet-stream,*/*")
                .addHeader("Referer", "https://drive.google.com/")
                .get()
                .build()

            response = okHttpClient.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw Exception("Gagal mengunduh file dari Google Drive (HTTP $code).")
        }

        // If response is still HTML, Google Drive blocked automated download (e.g. login/captcha required)
        contentType = response.header("Content-Type", "") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            response.close()
            throw Exception("Google Drive memerlukan konfirmasi unduhan via web. Silakan klik 'Buka Drive' di bawah untuk mengunduh APK.")
        }

        val body = response.body ?: throw Exception("Respons server Google Drive kosong.")
        val contentLength = if (fileItem.size > 0) fileItem.size else body.contentLength()

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(64 * 1024)
            var totalRead: Long = 0
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                totalRead += read
                val progress = if (contentLength > 0) {
                    (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                onProgress(progress, totalRead, contentLength)
            }
            outputStream.flush()
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { response.close() } catch (_: Exception) {}
        }

        // Verify valid APK signature (ZIP header: 0x50, 0x4B, 0x03, 0x04)
        if (!isValidApkFile(targetFile)) {
            val isHtml = try {
                val headerBytes = ByteArray(100)
                FileInputStream(targetFile).use { it.read(headerBytes) }
                val headerStr = String(headerBytes).lowercase()
                headerStr.contains("<!doctype") || headerStr.contains("<html") || headerStr.contains("<body")
            } catch (_: Exception) {
                false
            }
            targetFile.delete()
            if (isHtml) {
                throw Exception("Google Drive mengembalikan halaman web proteksi unduhan alih-alih berkas APK. Silakan klik 'Buka Drive' untuk mengunduh berkas langsung lewat browser.")
            } else {
                throw Exception("File yang diunduh dari Google Drive bukan paket aplikasi Android (.apk) yang valid.")
            }
        }

        targetFile
    }

    /**
     * Checks if a file starts with standard PK\x03\x04 signature.
     */
    private fun isValidApkFile(file: File): Boolean {
        if (!file.exists() || file.length() < 1000) return false
        return try {
            FileInputStream(file).use { fis ->
                val b1 = fis.read()
                val b2 = fis.read()
                val b3 = fis.read()
                val b4 = fis.read()
                b1 == 0x50 && b2 == 0x4B && b3 == 0x03 && b4 == 0x04
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the system package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File, fileItem: DriveApkItem? = null): Boolean {
        return try {
            if (!apkFile.exists()) return false

            if (fileItem != null) {
                val effectiveTimestamp = if (fileItem.timestampMillis > 0L) fileItem.timestampMillis else System.currentTimeMillis()
                saveInstalledApkInfo(context, fileItem.id, effectiveTimestamp, fileItem.size)
            } else {
                saveInstalledApkInfo(context, "", System.currentTimeMillis(), apkFile.length())
            }

            // Check Unknown Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val packageManager = context.packageManager
                if (!packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Parses date strings (ISO, standard JS, or timestamp) into epoch millis.
     */
    fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val trimmed = dateStr.trim()

        // 1. Check if numeric epoch timestamp (milliseconds or seconds)
        trimmed.toLongOrNull()?.let {
            return if (it in 1..9999999999L) it * 1000L else it
        }

        // Clean out trailing timezone labels in brackets e.g. " (WIB)" or " (Western Indonesia Time)"
        val cleanStr = trimmed.replace(Regex("\\s*\\([^)]*\\)"), "").trim()

        // 2. Format variations (ISO-8601, JS Date.toString, RFC 822, Localized Indonesian/English)
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z",
            "EEE MMM dd yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.contains("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(cleanStr)
                if (parsed != null && parsed.time > 0L) return parsed.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    /**
     * Extracts date and time formatted in Indonesian.
     */
    fun formatRawDate(dateStr: String): String {
        val millis = parseDateToMillis(dateStr)
        if (millis > 0L) {
            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", Locale("id", "ID"))
            sdf.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
            return sdf.format(java.util.Date(millis))
        }
        return dateStr
    }

    /**
     * Extracts timestamp if file name contains date patterns like 20260914_123000 or 2026-09-14
     */
    private fun parseTimestampFromFileName(fileName: String): Long {
        val pattern1 = Pattern.compile("(\\d{4})[-_]?(\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})?(\\d{2})?(\\d{2})?")
        val matcher = pattern1.matcher(fileName)
        if (matcher.find()) {
            return try {
                val year = matcher.group(1)?.toIntOrNull() ?: 2026
                val month = matcher.group(2)?.toIntOrNull() ?: 1
                val day = matcher.group(3)?.toIntOrNull() ?: 1
                val hour = matcher.group(4)?.toIntOrNull() ?: 0
                val min = matcher.group(5)?.toIntOrNull() ?: 0
                val sec = matcher.group(6)?.toIntOrNull() ?: 0

                val cal = java.util.Calendar.getInstance()
                cal.set(year, month - 1, day, hour, min, sec)
                cal.timeInMillis
            } catch (_: Exception) {
                0L
            }
        }
        return 0L
    }

    /**
     * Extracts semantic version string from APK name (e.g. MySingghara-v1.2.apk -> 1.2).
     */
    private fun extractVersionName(fileName: String): String {
        val regex = Pattern.compile("[vV](\\d+(\\.\\d+)+)")
        val matcher = regex.matcher(fileName)
        if (matcher.find()) {
            return matcher.group(1) ?: ""
        }
        return ""
    }

    /**
     * Compares two semantic version strings (e.g., "1.2" vs "1.0").
     */
    private fun compareVersionStrings(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }
}
