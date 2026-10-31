package com.example.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object GitHubUpdateManager {

    private const val PREFS_NAME = "github_update_prefs"
    private const val KEY_OWNER = "github_owner"
    private const val KEY_REPO = "github_repo"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_AUTO_CHECK = "auto_check_startup"
    private const val KEY_LAST_CHECKED = "last_checked_millis"

    // Default configuration requested
    const val DEFAULT_OWNER = "Putut2906"
    const val DEFAULT_REPO = "My-Singghara"
    const val DEFAULT_TOKEN = ""

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(context: Context): GitHubRepoConfig {
        val prefs = getPrefs(context)
        val owner = prefs.getString(KEY_OWNER, DEFAULT_OWNER) ?: DEFAULT_OWNER
        val repo = prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
        val token = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN) ?: DEFAULT_TOKEN
        val autoCheck = prefs.getBoolean(KEY_AUTO_CHECK, true)
        return GitHubRepoConfig(
            owner = if (owner.isBlank()) DEFAULT_OWNER else owner.trim(),
            repo = if (repo.isBlank()) DEFAULT_REPO else repo.trim(),
            token = if (token.isBlank()) DEFAULT_TOKEN else token.trim(),
            autoCheckOnStartup = autoCheck
        )
    }

    fun saveConfig(context: Context, config: GitHubRepoConfig) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_OWNER, config.owner.trim())
            .putString(KEY_REPO, config.repo.trim())
            .putString(KEY_TOKEN, config.token.trim())
            .putBoolean(KEY_AUTO_CHECK, config.autoCheckOnStartup)
            .apply()
    }

    fun resetToDefaultConfig(context: Context): GitHubRepoConfig {
        val defaultConfig = GitHubRepoConfig(
            owner = DEFAULT_OWNER,
            repo = DEFAULT_REPO,
            token = DEFAULT_TOKEN,
            autoCheckOnStartup = true
        )
        saveConfig(context, defaultConfig)
        return defaultConfig
    }

    fun getLastCheckedMillis(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_CHECKED, 0L)
    }

    private fun saveLastCheckedMillis(context: Context, millis: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_CHECKED, millis).apply()
    }

    /**
     * Check latest release from GitHub API with graceful fallbacks.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckState = withContext(Dispatchers.IO) {
        val config = loadConfig(context)
        val currentVersion = BuildConfig.VERSION_NAME
        val owner = config.owner
        val repo = config.repo

        try {
            saveLastCheckedMillis(context, System.currentTimeMillis())

            // Helper to execute HTTP request with optional token and auto-fallback
            fun fetchGitHubJson(url: String, useToken: Boolean): Pair<Int, String> {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/vnd.github+json")
                    .addHeader("User-Agent", "MySingghara-Android-App")
                    .addHeader("X-GitHub-Api-Version", "2022-11-28")

                if (useToken && config.token.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer ${config.token}")
                }

                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val code = response.code
                val body = response.body?.string() ?: ""
                return Pair(code, body)
            }

            // 1. Try fetching latest published release
            val latestUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            var (code, body) = fetchGitHubJson(latestUrl, useToken = true)

            // If 401/403 (e.g. token expired or invalid scope), retry unauthenticated since repo is public
            if ((code == 401 || code == 403) && config.token.isNotBlank()) {
                val unauthResult = fetchGitHubJson(latestUrl, useToken = false)
                code = unauthResult.first
                body = unauthResult.second
            }

            var targetReleaseJson: JSONObject? = null

            if (code == 200 && body.isNotBlank()) {
                targetReleaseJson = JSONObject(body)
            } else {
                // 2. If /releases/latest returned 404, fallback to /releases (list all releases including pre-releases)
                val allReleasesUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=5"
                var (allCode, allBody) = fetchGitHubJson(allReleasesUrl, useToken = true)

                if ((allCode == 401 || allCode == 403) && config.token.isNotBlank()) {
                    val unauthAll = fetchGitHubJson(allReleasesUrl, useToken = false)
                    allCode = unauthAll.first
                    allBody = unauthAll.second
                }

                if (allCode == 200 && allBody.isNotBlank()) {
                    val releasesArray = JSONArray(allBody)
                    if (releasesArray.length() > 0) {
                        targetReleaseJson = releasesArray.getJSONObject(0)
                    }
                } else if (allCode == 404) {
                    // Check if repository exists
                    val repoInfoUrl = "https://api.github.com/repos/$owner/$repo"
                    val (repoCode, _) = fetchGitHubJson(repoInfoUrl, useToken = false)
                    if (repoCode == 404) {
                        return@withContext UpdateCheckState.Error(
                            errorMessage = "Repositori '$owner/$repo' tidak ditemukan di GitHub. Pastikan nama repositori sudah sesuai di Pengaturan."
                        )
                    }
                }
            }

            // If no release exists yet on GitHub
            if (targetReleaseJson == null) {
                return@withContext UpdateCheckState.UpToDate(
                    currentVersion = currentVersion,
                    latestRelease = null,
                    customMessage = "Repositori '$owner/$repo' terhubung aktif. Belum ada rilis APK yang dipublikasikan di GitHub Releases, versi v$currentVersion Anda saat ini adalah yang paling baru."
                )
            }

            val tagName = targetReleaseJson.optString("tag_name", "")
            val releaseName = targetReleaseJson.optString("name", tagName)
            val releaseBody = targetReleaseJson.optString("body", "Tidak ada catatan perubahan.")
            val publishedAt = targetReleaseJson.optString("published_at", "")
            val htmlUrl = targetReleaseJson.optString("html_url", "https://github.com/$owner/$repo/releases")
            val isDraft = targetReleaseJson.optBoolean("draft", false)
            val isPrerelease = targetReleaseJson.optBoolean("prerelease", false)

            val assetsJsonArray = targetReleaseJson.optJSONArray("assets") ?: JSONArray()
            val assetsList = mutableListOf<GitHubAsset>()
            for (i in 0 until assetsJsonArray.length()) {
                val assetObj = assetsJsonArray.getJSONObject(i)
                val assetName = assetObj.optString("name", "")
                val size = assetObj.optLong("size", 0L)
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val contentType = assetObj.optString("content_type", "")
                assetsList.add(
                    GitHubAsset(
                        name = assetName,
                        size = size,
                        browserDownloadUrl = downloadUrl,
                        contentType = contentType
                    )
                )
            }

            val release = GitHubReleaseResponse(
                tagName = tagName,
                name = releaseName,
                body = releaseBody,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                isDraft = isDraft,
                isPrerelease = isPrerelease,
                assets = assetsList
            )

            val apkAsset = release.primaryApkAsset

            // Semantic Versioning Comparator
            val isNewer = isRemoteVersionNewer(currentVersion, tagName)

            if (isNewer && apkAsset != null) {
                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersion,
                    newVersion = release.cleanVersionName,
                    release = release,
                    apkAsset = apkAsset
                )
            } else {
                UpdateCheckState.UpToDate(
                    currentVersion = currentVersion,
                    latestRelease = release,
                    customMessage = if (isNewer && apkAsset == null) {
                        "Rilis baru $tagName terdeteksi, namun paket APK belum selesai di-build atau diunggah oleh GitHub Actions."
                    } else null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            UpdateCheckState.Error(
                errorMessage = "Gagal menghubungi server: ${e.localizedMessage ?: e.message ?: "Periksa koneksi internet Anda"}"
            )
        }
    }

    /**
     * Compares local app version against remote tag name.
     * Returns true if remote version is newer than current local version.
     */
    fun isRemoteVersionNewer(localVer: String, remoteTag: String): Boolean {
        try {
            val cleanLocal = localVer.removePrefix("v").removePrefix("V").trim()
            val cleanRemote = remoteTag.removePrefix("v").removePrefix("V").trim()

            if (cleanLocal.equals(cleanRemote, ignoreCase = true)) {
                return false
            }

            val localParts = cleanLocal.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val remoteParts = cleanRemote.split(".", "-", "_").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(localParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val localPart = localParts.getOrElse(i) { 0 }
                val remotePart = remoteParts.getOrElse(i) { 0 }
                if (remotePart > localPart) return true
                if (remotePart < localPart) return false
            }

            // Fallback: If numeric comparison is identical but remote has extra text, check string comparison
            return cleanRemote > cleanLocal
        } catch (e: Exception) {
            return remoteTag.trim() != localVer.trim()
        }
    }

    /**
     * Downloads APK file directly to cache folder with real-time byte progress.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        token: String = "",
        onProgress: (progressPercent: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val downloadDir = File(context.cacheDir, "apk_updates")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val targetFile = File(downloadDir, fileName.ifBlank { "MySingghara-update.apk" })
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", "MySingghara-Android-App")

        // In case it's a GitHub API asset url rather than public browser_download_url
        if (token.isNotBlank() && downloadUrl.contains("api.github.com")) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
            requestBuilder.addHeader("Accept", "application/octet-stream")
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw Exception("Gagal mengunduh APK (HTTP ${response.code}): ${response.message}")
        }

        val body = response.body ?: throw Exception("Respons file APK kosong dari server.")
        val contentLength = body.contentLength()

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8 * 1024)
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
            targetFile
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Triggers Android package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                return false
            }

            // Check Unknown Sources permission on Android 8.0+ (Oreo)
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
     * Gets information about the installed APK file (size in bytes and path).
     */
    fun getInstalledApkInfo(context: Context): Pair<Long, String> {
        return try {
            val sourceDir = context.applicationInfo.sourceDir
            val file = File(sourceDir)
            if (file.exists()) {
                Pair(file.length(), file.absolutePath)
            } else {
                Pair(0L, "")
            }
        } catch (e: Exception) {
            Pair(0L, "")
        }
    }

    /**
     * Prepares and shares the installed APK file with custom file name (e.g. MySingghara-v1.0.apk)
     * using Android FileProvider and Intent.ACTION_SEND.
     */
    suspend fun shareInstalledApk(
        context: Context,
        customFileName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceDir = context.applicationInfo.sourceDir
            val sourceApk = File(sourceDir)
            if (!sourceApk.exists()) {
                return@withContext false
            }

            val shareDir = File(context.cacheDir, "shared_apk")
            if (!shareDir.exists()) {
                shareDir.mkdirs()
            }

            val versionName = com.example.BuildConfig.VERSION_NAME
            val targetFileName = if (!customFileName.isNullOrBlank()) {
                if (customFileName.endsWith(".apk", ignoreCase = true)) customFileName else "$customFileName.apk"
            } else {
                "MySingghara-v$versionName.apk"
            }

            val targetApk = File(shareDir, targetFileName)

            // Copy file in chunks to ensure fresh copy with proper name
            FileInputStream(sourceApk).use { input ->
                FileOutputStream(targetApk).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, apkUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Aplikasi My Singghara v$versionName")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Bagikan file installer aplikasi My Singghara (MySingghara-v$versionName.apk).\n" +
                        "Aplikasi Manajemen Usaha & Pencatatan Terpadu.\n" +
                        "Unduh dan buka file ini untuk memasang aplikasi di HP Android Anda."
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Bagikan File APK My Singghara").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
