package com.example.update

import java.io.File

/**
 * Data model for GitHub Release Asset.
 */
data class GitHubAsset(
    val name: String,
    val size: Long,
    val browserDownloadUrl: String,
    val contentType: String
) {
    val sizeFormatted: String
        get() {
            if (size <= 0) return "0 MB"
            val mb = size.toDouble() / (1024.0 * 1024.0)
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}

/**
 * Data model for GitHub Release.
 */
data class GitHubReleaseResponse(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isDraft: Boolean = false,
    val isPrerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
) {
    val primaryApkAsset: GitHubAsset?
        get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    val cleanVersionName: String
        get() = tagName.removePrefix("v").removePrefix("V").trim()

    val formattedPublishedDate: String
        get() {
            if (publishedAt.isBlank()) return "Tidak diketahui"
            return try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(publishedAt)
                if (date != null) {
                    val outputFormat = java.text.SimpleDateFormat("dd MMMM yyyy, HH:mm 'WIB'", java.util.Locale("id", "ID"))
                    outputFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
                    outputFormat.format(date)
                } else {
                    publishedAt
                }
            } catch (e: Exception) {
                publishedAt
            }
        }
}

/**
 * State for update checking.
 */
sealed class UpdateCheckState {
    data class Idle(val lastCheckedMillis: Long = 0L) : UpdateCheckState()
    data class Loading(val message: String = "Memeriksa pembaruan di server GitHub...") : UpdateCheckState()
    data class UpToDate(
        val currentVersion: String,
        val latestRelease: GitHubReleaseResponse? = null,
        val customMessage: String? = null,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : UpdateCheckState()
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val release: GitHubReleaseResponse,
        val apkAsset: GitHubAsset
    ) : UpdateCheckState()
    data class Error(
        val errorMessage: String,
        val isAuthError: Boolean = false,
        val checkedAtMillis: Long = System.currentTimeMillis()
    ) : UpdateCheckState()
}

/**
 * State for APK download & install flow.
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progressPercent: Float, // 0.0f to 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState() {
        val progressText: String
            get() = "${(progressPercent * 100).toInt()}%"
        val downloadedText: String
            get() {
                val downloadedMb = bytesDownloaded.toDouble() / (1024 * 1024)
                val totalMb = totalBytes.toDouble() / (1024 * 1024)
                return if (totalBytes > 0) {
                    String.format(java.util.Locale.US, "%.1f / %.1f MB", downloadedMb, totalMb)
                } else {
                    String.format(java.util.Locale.US, "%.1f MB", downloadedMb)
                }
            }
    }
    data class Installing(val file: File) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val errorMessage: String) : DownloadState()
}

/**
 * GitHub repository configuration.
 */
data class GitHubRepoConfig(
    val owner: String = "Putut2906",
    val repo: String = "My-Singghara",
    val token: String = "",
    val autoCheckOnStartup: Boolean = true
)
