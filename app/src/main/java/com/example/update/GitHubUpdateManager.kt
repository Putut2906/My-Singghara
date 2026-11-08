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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    private const val MAX_DOWNLOAD_ATTEMPTS = 5

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
                // 2. /releases/latest answers 404 while a release is still being published or
                //    when every release is a pre-release, so scan the full list as fallback.
                val allReleasesUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=20"
                var (allCode, allBody) = fetchGitHubJson(allReleasesUrl, useToken = true)

                if ((allCode == 401 || allCode == 403) && config.token.isNotBlank()) {
                    val unauthAll = fetchGitHubJson(allReleasesUrl, useToken = false)
                    allCode = unauthAll.first
                    allBody = unauthAll.second
                }

                if (allCode == 200 && allBody.isNotBlank()) {
                    val releasesArray = JSONArray(allBody)
                    var bestTag = ""
                    for (i in 0 until releasesArray.length()) {
                        val candidate = releasesArray.optJSONObject(i) ?: continue
                        if (candidate.optBoolean("draft", false)) continue
                        val candidateTag = candidate.optString("tag_name", "")
                        if (candidateTag.isBlank()) continue
                        if (targetReleaseJson == null || isRemoteVersionNewer(bestTag, candidateTag)) {
                            targetReleaseJson = candidate
                            bestTag = candidateTag
                        }
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
                } else {
                    // The GitHub API rate-limits anonymous clients (HTTP 403) and sometimes
                    // fails behind mobile proxies. The public release page is not rate-limited
                    // the same way, so read the tag from there before giving up.
                    val htmlTag = fetchLatestTagFromHtml(owner, repo)
                    if (htmlTag != null) {
                        val assetName = "MySingghara-$htmlTag.apk"
                        val htmlRelease = GitHubReleaseResponse(
                            tagName = htmlTag,
                            name = htmlTag,
                            body = "Catatan rilis lengkap tersedia di halaman GitHub Releases.",
                            publishedAt = "",
                            htmlUrl = "https://github.com/$owner/$repo/releases/tag/$htmlTag",
                            assets = listOf(
                                GitHubAsset(
                                    name = assetName,
                                    size = 0L,
                                    browserDownloadUrl = "https://github.com/$owner/$repo/releases/download/$htmlTag/$assetName",
                                    contentType = "application/vnd.android.package-archive"
                                )
                            )
                        )
                        val htmlAsset = htmlRelease.primaryApkAsset
                        return@withContext if (htmlAsset != null &&
                            isRemoteVersionNewer(currentVersion, htmlTag)
                        ) {
                            UpdateCheckState.UpdateAvailable(
                                currentVersion = currentVersion,
                                newVersion = htmlRelease.cleanVersionName,
                                release = htmlRelease,
                                apkAsset = htmlAsset
                            )
                        } else {
                            UpdateCheckState.UpToDate(
                                currentVersion = currentVersion,
                                latestRelease = htmlRelease
                            )
                        }
                    }
                    // Report the failure instead of pretending the app is up to date:
                    // a silent "up to date" hides a newer release when GitHub rate-limits
                    // the request (HTTP 403) or the network is temporarily down.
                    return@withContext UpdateCheckState.Error(
                        errorMessage = if (allCode == 403 || allCode == 429) {
                            "Batas permintaan GitHub API tercapai (HTTP $allCode). Coba periksa lagi beberapa saat lagi atau atur token GitHub di Pengaturan."
                        } else {
                            "Gagal memeriksa rilis GitHub (HTTP $allCode)."
                        }
                    )
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
     * Reads the newest tag from the public releases page. OkHttp follows the
     * /releases/latest redirect, so the resolved URL carries the tag name.
     */
    private fun fetchLatestTagFromHtml(owner: String, repo: String): String? {
        var response: okhttp3.Response? = null
        return try {
            val request = Request.Builder()
                .url("https://github.com/$owner/$repo/releases/latest")
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) MySingghara")
                .build()
            val resp = okHttpClient.newCall(request).execute()
            response = resp
            if (!resp.isSuccessful) {
                null
            } else {
                Regex("/releases/tag/([^/?#]+)").find(resp.request.url.toString())
                    ?.groupValues?.getOrNull(1)
                    ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            }
        } catch (_: Exception) {
            null
        } finally {
            try { response?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Downloads APK file directly to cache folder with real-time byte progress.
     *
     * GitHub CDN membalas 504/502 secara berkala untuk aset besar. Unduhan dicoba
     * ulang dengan jeda bertahap; byte yang sudah diterima dilanjutkan lewat
     * header Range sehingga APK 100+ MB tidak perlu diunduh dari awal.
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
        if (targetFile.exists() && targetFile.length() <= 0L) {
            targetFile.delete()
        }

        var lastError: Exception? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            try {
                downloadApkAttempt(downloadUrl, targetFile, token, onProgress)
                return@withContext targetFile
            } catch (e: RetryableDownloadException) {
                lastError = e
                if (attempt == MAX_DOWNLOAD_ATTEMPTS) break
                delay(1000L shl (attempt - 1))
            }
        }

        throw Exception(
            "Gagal mengunduh APK setelah $MAX_DOWNLOAD_ATTEMPTS percobaan" +
                (lastError?.message?.let { " ($it)" } ?: "") +
                ". Coba lagi beberapa saat lagi atau buka halaman rilis GitHub untuk mengunduh manual.",
            lastError
        )
    }

    private class RetryableDownloadException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private fun downloadApkAttempt(
        downloadUrl: String,
        targetFile: File,
        token: String,
        onProgress: (progressPercent: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ) {
        val existingBytes = if (targetFile.exists()) targetFile.length().coerceAtLeast(0L) else 0L
        val requestBuilder = Request.Builder()
            .url(downloadUrl)
            .addHeader("User-Agent", "MySingghara-Android-App")
        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        // In case it's a GitHub API asset url rather than public browser_download_url
        if (token.isNotBlank() && downloadUrl.contains("api.github.com")) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
            requestBuilder.addHeader("Accept", "application/octet-stream")
        }

        val response = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: java.io.IOException) {
            throw RetryableDownloadException("Koneksi gagal: ${e.message}", e)
        }

        response.use { resp ->
            val code = resp.code
            if (code == 416) {
                // Berkas parsial tidak lagi cocok dengan aset di server; mulai ulang.
                targetFile.delete()
                throw RetryableDownloadException("Permintaan lanjutan unduhan ditolak (HTTP 416)")
            }
            if (code == 408 || code == 429 || code >= 500) {
                throw RetryableDownloadException("HTTP $code")
            }
            if (!resp.isSuccessful) {
                throw Exception("Gagal mengunduh APK (HTTP $code): ${resp.message}")
            }

            val body = resp.body ?: throw Exception("Respons file APK kosong dari server.")
            val resuming = code == 206 && existingBytes > 0L
            val alreadyThere = if (resuming) existingBytes else 0L
            val remainingBytes = body.contentLength()
            val totalBytes = if (remainingBytes > 0) alreadyThere + remainingBytes else -1L
            var written = alreadyThere

            body.byteStream().use { input ->
                FileOutputStream(targetFile, resuming).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = try {
                            input.read(buffer)
                        } catch (e: java.io.IOException) {
                            throw RetryableDownloadException("Koneksi terputus di $written byte", e)
                        }
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val progress = if (totalBytes > 0) {
                            (written.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(progress, written, totalBytes)
                    }
                    output.flush()
                }
            }

            if (totalBytes > 0 && written < totalBytes) {
                throw RetryableDownloadException("Unduhan terputus di $written dari $totalBytes byte")
            }
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
