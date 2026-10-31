package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Compares the signing certificate of a downloaded APK with the installed app.
 *
 * Android only accepts an update when both are signed with the same key,
 * otherwise the installer reports INSTALL_FAILED_UPDATE_INCOMPATIBLE
 * ("aplikasi bentrok").
 */
object ApkSignatureChecker {

    enum class Result {
        SAME_SIGNER,
        SIGNATURE_MISMATCH,
        DIFFERENT_PACKAGE,
        UNREADABLE
    }

    sealed interface InstallVerdict {
        /** The APK can be handed to the package installer. */
        data object Compatible : InstallVerdict

        /**
         * Android will refuse to replace the installed app. A copy of the APK
         * was kept in the public Download folder when [savedFileName] is set.
         */
        data class Conflict(
            val differentPackage: Boolean,
            val savedFileName: String?
        ) : InstallVerdict
    }

    fun check(context: Context, apkFile: File): Result {
        if (!apkFile.exists() || apkFile.length() == 0L) return Result.UNREADABLE
        val pm = context.packageManager
        val archive = readApkInfo(pm, apkFile) ?: return Result.UNREADABLE
        if (archive.packageName != context.packageName) return Result.DIFFERENT_PACKAGE
        val installed = readInstalledInfo(pm, context.packageName) ?: return Result.UNREADABLE

        val archiveCerts = certificateDigests(archive)
        val installedCerts = certificateDigests(installed)
        if (archiveCerts.isEmpty() || installedCerts.isEmpty()) return Result.UNREADABLE

        return if (archiveCerts.any { it in installedCerts }) Result.SAME_SIGNER else Result.SIGNATURE_MISMATCH
    }

    /**
     * Verifies the APK and, when it cannot replace the installed app, keeps a
     * copy in /storage/Download so the user can reinstall it after uninstalling.
     */
    suspend fun prepareForInstall(context: Context, apkFile: File, displayName: String): InstallVerdict {
        return when (val result = check(context, apkFile)) {
            Result.SIGNATURE_MISMATCH, Result.DIFFERENT_PACKAGE -> {
                val savedName = try {
                    ApkDownloadsSaver.saveToDownloads(context, apkFile, displayName)
                    normalizeFileName(displayName)
                } catch (_: Exception) {
                    null
                }
                InstallVerdict.Conflict(
                    differentPackage = result == Result.DIFFERENT_PACKAGE,
                    savedFileName = savedName
                )
            }
            // UNREADABLE means the file could not be inspected; let the system
            // installer show its own error instead of blocking the user.
            Result.SAME_SIGNER, Result.UNREADABLE -> InstallVerdict.Compatible
        }
    }

    fun normalizeFileName(fileName: String): String =
        if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"

    @Suppress("DEPRECATION")
    private fun readApkInfo(pm: PackageManager, apkFile: File): PackageInfo? = try {
        pm.getPackageArchiveInfo(apkFile.absolutePath, signatureFlags())
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun readInstalledInfo(pm: PackageManager, packageName: String): PackageInfo? = try {
        pm.getPackageInfo(packageName, signatureFlags())
    } catch (_: Exception) {
        null
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { signingInfo ->
                when {
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                    else -> signingInfo.signingCertificateHistory ?: signingInfo.apkContentsSigners
                }
            }
        } else {
            info.signatures
        }
        return signatures.orEmpty().mapNotNull { signature ->
            try {
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }
            } catch (_: Exception) {
                null
            }
        }.toSet()
    }
}
