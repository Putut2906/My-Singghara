package com.example.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sesi unduhan APK yang hidup selama proses aplikasi, bukan selama komposisi dialog.
 * Dialog pembaruan disembunyikan saat aplikasi terkunci; dengan scope aplikasi ini
 * unduhan tetap berjalan dan progresnya tersambung lagi tanpa mulai dari nol.
 */
class UpdateDownloadSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var assetKey: String = ""

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val _downloadedApk = MutableStateFlow<File?>(null)
    val downloadedApk: StateFlow<File?> = _downloadedApk.asStateFlow()

    private val _signatureConflict = MutableStateFlow(false)
    val signatureConflict: StateFlow<Boolean> = _signatureConflict.asStateFlow()

    val isBusy: Boolean
        get() = job?.isActive == true

    /**
     * Dipanggil setiap dialog tampil. State hanya direset bila aset yang dituju
     * berbeda, sehingga progres unduhan yang sedang berjalan tetap tersambung
     * setelah aplikasi dibuka kembali.
     */
    fun prepare(key: String) {
        if (key == assetKey) return
        job?.cancel()
        job = null
        assetKey = key
        clearState()
    }

    fun reset() {
        job?.cancel()
        job = null
        assetKey = ""
        clearState()
    }

    fun startGithubDownload(context: Context, asset: GitHubAsset, token: String) {
        if (isBusy) return
        val appContext = context.applicationContext
        val targetName = asset.name.ifBlank { "MySingghara-update.apk" }
        job = scope.launch {
            try {
                _downloadedApk.value = null
                _signatureConflict.value = false
                _state.value = DownloadState.Downloading(0f, 0L, asset.size)
                val file = GitHubUpdateManager.downloadApk(
                    context = appContext,
                    downloadUrl = asset.browserDownloadUrl,
                    fileName = targetName,
                    token = token,
                    expectedSize = asset.size
                ) { progress, downloaded, total ->
                    _state.value = DownloadState.Downloading(progress, downloaded, total)
                }

                when (val verdict = ApkSignatureChecker.prepareForInstall(
                    context = appContext,
                    apkFile = file,
                    displayName = ApkSignatureChecker.normalizeFileName(targetName)
                )) {
                    is ApkSignatureChecker.InstallVerdict.Conflict -> {
                        _signatureConflict.value = true
                        _state.value = DownloadState.Failed(conflictMessage(verdict))
                    }
                    ApkSignatureChecker.InstallVerdict.Compatible -> {
                        _downloadedApk.value = file
                        _state.value = DownloadState.Idle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(e.localizedMessage ?: "Gagal mengunduh file APK.")
            }
        }
    }

    fun startDriveDownload(context: Context, item: DriveApkItem) {
        if (isBusy || _downloadedApk.value != null || _state.value is DownloadState.Completed) return
        val appContext = context.applicationContext
        job = scope.launch {
            try {
                _signatureConflict.value = false
                _state.value = DownloadState.Downloading(0f, 0L, item.size)
                val file = GoogleDriveUpdateManager.downloadDriveApk(
                    context = appContext,
                    fileItem = item
                ) { progress, downloaded, total ->
                    _state.value = DownloadState.Downloading(progress, downloaded, total)
                }

                when (val verdict = ApkSignatureChecker.prepareForInstall(
                    context = appContext,
                    apkFile = file,
                    displayName = ApkSignatureChecker.normalizeFileName(item.name)
                )) {
                    is ApkSignatureChecker.InstallVerdict.Conflict -> {
                        _signatureConflict.value = true
                        _state.value = DownloadState.Failed(conflictMessage(verdict))
                    }
                    ApkSignatureChecker.InstallVerdict.Compatible -> {
                        _downloadedApk.value = file
                        // Saat layar terkunci pemasang ditunda dulu; dialog akan
                        // menawarkan "Pasang" begitu aplikasi dibuka kembali.
                        if (isDeviceLocked(appContext)) {
                            _state.value = DownloadState.Idle
                        } else {
                            openInstaller(appContext, file, item)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DownloadState.Failed(
                    e.localizedMessage ?: "Gagal mengunduh file APK dari Google Drive."
                )
            }
        }
    }

    /**
     * Membuka pemasang untuk APK yang sudah terunduh. [driveItem] mengisi riwayat
     * pemasangan Google Drive agar berkas yang sama tidak ditawarkan lagi.
     */
    fun installDownloaded(context: Context, driveItem: DriveApkItem? = null) {
        val file = _downloadedApk.value ?: return
        if (isBusy) return
        openInstaller(context.applicationContext, file, driveItem)
    }

    private fun openInstaller(appContext: Context, file: File, driveItem: DriveApkItem?) {
        _state.value = DownloadState.Installing(file)
        val success = if (driveItem != null) {
            GoogleDriveUpdateManager.installApk(appContext, file, driveItem)
        } else {
            GitHubUpdateManager.installApk(appContext, file)
        }
        _state.value = if (success) {
            DownloadState.Completed
        } else {
            DownloadState.Failed(
                "Gagal membuka installer APK. Pastikan izin instalasi aplikasi tidak dikenal diizinkan."
            )
        }
    }

    private fun conflictMessage(verdict: ApkSignatureChecker.InstallVerdict.Conflict): String {
        val savedInfo = verdict.savedFileName
            ?.let { "APK sudah disimpan di folder Download: $it. " }
            .orEmpty()
        return savedInfo +
            "APK memakai kunci tanda tangan berbeda dari aplikasi terpasang, jadi Android menolaknya sebagai pembaruan. Hapus aplikasi lama, lalu pasang APK tersebut dari folder Download."
    }

    private fun clearState() {
        _state.value = DownloadState.Idle
        _downloadedApk.value = null
        _signatureConflict.value = false
    }

    private fun isDeviceLocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return keyguard?.isKeyguardLocked == true
    }
}

object UpdateDownloadCenter {
    /** Unduhan dari pop up GitHub Releases. */
    val github = UpdateDownloadSession()

    /** Unduhan dari pop up Google Drive. */
    val drive = UpdateDownloadSession()
}
