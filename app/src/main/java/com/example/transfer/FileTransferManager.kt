package com.example.transfer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LocalTransferItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val size: Long,
    val formattedSize: String,
    val mimeType: String,
    val isFromPhone: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val file: File? = null,
    val uri: Uri? = null,
    val isSelfApk: Boolean = false,
    val extension: String = ""
)

data class TransferActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean // true = dari PC ke HP, false = dari HP ke PC
)

class FileTransferManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.Job() + com.example.util.AppCrashGuard.handler)

    val receivedDir: File by lazy {
        val dir = context.getExternalFilesDir("Transfer_PC") ?: File(context.filesDir, "Transfer_PC")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val sharedDir: File by lazy {
        val dir = File(context.cacheDir, "Transfer_Shared")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val _sharedFromPhone = MutableStateFlow<List<LocalTransferItem>>(emptyList())
    val sharedFromPhone: StateFlow<List<LocalTransferItem>> = _sharedFromPhone.asStateFlow()

    private val _receivedFromPc = MutableStateFlow<List<LocalTransferItem>>(emptyList())
    val receivedFromPc: StateFlow<List<LocalTransferItem>> = _receivedFromPc.asStateFlow()

    private val _activityLogs = MutableStateFlow<List<TransferActivityLog>>(emptyList())
    val activityLogs: StateFlow<List<TransferActivityLog>> = _activityLogs.asStateFlow()

    init {
        refreshReceivedFiles()
        refreshSharedFiles()
    }

    // Isi folder dibaca langsung dari disk setiap kali daftar dibutuhkan. Daftar yang
    // hanya hidup di memori membuat PC harus menekan F5 (atau menunggu HP mengirim ulang)
    // sebelum berkas lama muncul lagi setelah aplikasi dijalankan kembali.
    fun receivedItemsNow(): List<LocalTransferItem> = transferItemsFrom(receivedDir, false)

    fun sharedItemsNow(): List<LocalTransferItem> = transferItemsFrom(sharedDir, true)

    private fun transferItemsFrom(dir: File, fromPhone: Boolean): List<LocalTransferItem> {
        val files = try {
            dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return files.map { file ->
            LocalTransferItem(
                id = (if (fromPhone) "shared_" else "rec_") + file.name.hashCode().toString(),
                name = if (fromPhone) displayShareName(file.name) else file.name,
                size = file.length(),
                formattedSize = formatFileSize(file.length()),
                mimeType = getMimeType(file.name),
                isFromPhone = fromPhone,
                timestamp = file.lastModified(),
                file = file,
                extension = file.extension.uppercase(Locale.getDefault())
            )
        }
    }

    // Berkas bersama disimpan sebagai "<milis>_<nama>"; prefiks itu hanya dipakai supaya
    // nama tidak bentrok saat berkas dengan nama sama dibagikan dua kali.
    private fun displayShareName(storedName: String): String =
        storedName.replaceFirst(Regex("^\\d{10,}_"), "")

    fun refreshReceivedFiles() {
        scope.launch {
            try {
                _receivedFromPc.value = receivedItemsNow()
                notifyPcClients()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshSharedFiles() {
        scope.launch {
            try {
                _sharedFromPhone.value = sharedItemsNow()
                notifyPcClients()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getSelfApkItem(): LocalTransferItem? {
        return try {
            val apkFile = File(context.applicationInfo.sourceDir)
            if (apkFile.exists()) {
                LocalTransferItem(
                    id = "self_apk",
                    name = "MySingghara_App.apk",
                    size = apkFile.length(),
                    formattedSize = formatFileSize(apkFile.length()),
                    mimeType = "application/vnd.android.package-archive",
                    isFromPhone = true,
                    timestamp = apkFile.lastModified(),
                    file = apkFile,
                    isSelfApk = true,
                    extension = "APK"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getAllFilesForPc(): List<LocalTransferItem> {
        val list = mutableListOf<LocalTransferItem>()
        getSelfApkItem()?.let { list.add(it) }
        list.addAll(sharedItemsNow())
        return list
    }

    suspend fun addFilesFromPhone(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var addedCount = 0
        for (uri in uris) {
            try {
                var fileName = "berkas_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }

                val safeFileName = fileName.replace("[/\\\\:*?\"<>|]".toRegex(), "_")
                val targetFile = File(sharedDir, "${System.currentTimeMillis()}_$safeFileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0) addedCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (addedCount > 0) {
            // Daftar diambil ulang dari disk, lalu logActivity membangunkan jalur SSE
            // sehingga halaman PC langsung menampilkan berkas baru tanpa F5.
            _sharedFromPhone.value = sharedItemsNow()
            logActivity(
                title = "Berbagi $addedCount Berkas dari HP",
                desc = "Siap diunduh oleh Web Connect di PC",
                isIncoming = false
            )
        }
        addedCount
    }

    fun removeSharedFile(id: String) {
        val item = sharedItemsNow().firstOrNull { it.id == id } ?: return
        try {
            item.file?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _sharedFromPhone.value = sharedItemsNow()
        notifyPcClients()
    }

    fun clearSharedFiles() {
        try {
            sharedDir.listFiles()?.forEach { file ->
                try { file.delete() } catch (e: Exception) { e.printStackTrace() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _sharedFromPhone.value = sharedItemsNow()
        notifyPcClients()
    }

    suspend fun saveReceivedFromPc(fileName: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val cleanName = fileName.replace("[/\\\\:*?\"<>|]".toRegex(), "_").ifBlank { "berkas_pc_${System.currentTimeMillis()}" }
        var targetFile = File(receivedDir, cleanName)

        if (targetFile.exists()) {
            val baseName = cleanName.substringBeforeLast(".")
            val ext = if (cleanName.contains(".")) ".${cleanName.substringAfterLast(".")}" else ""
            var count = 1
            while (targetFile.exists()) {
                targetFile = File(receivedDir, "$baseName ($count)$ext")
                count++
            }
        }

        FileOutputStream(targetFile).use { it.write(bytes) }

        refreshReceivedFiles()

        logActivity(
            title = "Diterima dari PC: ${targetFile.name}",
            desc = "${formatFileSize(bytes.size.toLong())} • Berhasil disimpan di HP",
            isIncoming = true
        )

        targetFile
    }

    suspend fun deleteReceivedFile(id: String): Boolean = withContext(Dispatchers.IO) {
        val item = _receivedFromPc.value.find { it.id == id } ?: return@withContext false
        val deleted = item.file?.delete() ?: false
        if (deleted) {
            _receivedFromPc.value = _receivedFromPc.value.filter { it.id != id }
            notifyPcClients()
        }
        deleted
    }

    suspend fun saveToPublicDownloads(item: LocalTransferItem): Boolean = withContext(Dispatchers.IO) {
        val sourceFile = item.file ?: return@withContext false
        if (!sourceFile.exists()) return@withContext false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, item.mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDir.exists()) publicDir.mkdirs()
                var destFile = File(publicDir, sourceFile.name)
                if (destFile.exists()) {
                    destFile = File(publicDir, "${System.currentTimeMillis()}_${sourceFile.name}")
                }
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { out ->
                        input.copyTo(out)
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun openFile(context: Context, item: LocalTransferItem) {
        val file = item.file ?: return
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, item.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Buka Berkas: ${item.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    setDataAndType(contentUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Buka Berkas: ${item.name}"))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun shareFile(context: Context, item: LocalTransferItem) {
        val file = item.file ?: return
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType.ifEmpty { "*/*" }
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, item.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Bagikan Berkas: ${item.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logActivity(title: String, desc: String, isIncoming: Boolean) {
        val log = TransferActivityLog(
            title = title,
            description = desc,
            timestamp = System.currentTimeMillis(),
            isIncoming = isIncoming
        )
        _activityLogs.value = (listOf(log) + _activityLogs.value).take(50)
        notifyPcClients()
    }

    fun clearLogs() {
        _activityLogs.value = emptyList()
        notifyPcClients()
    }

    // Beri tahu halaman Web Connect bahwa ada perubahan, jadi PC tidak perlu F5.
    // Tulisan ke socket browser tidak boleh terjadi di thread UI.
    private fun notifyPcClients() {
        scope.launch {
            try {
                com.example.web.WebEventHub.publish("transfer", "change", System.currentTimeMillis().toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: FileTransferManager? = null

        fun getInstance(context: Context): FileTransferManager {
            return instance ?: synchronized(this) {
                instance ?: FileTransferManager(context.applicationContext).also { instance = it }
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
        }

        fun getMimeType(fileName: String): String {
            val ext = fileName.substringAfterLast(".", "").lowercase(Locale.getDefault())
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
                "apk" -> "application/vnd.android.package-archive"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "rar" -> "application/x-rar-compressed"
                "7z" -> "application/x-7z-compressed"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp3" -> "audio/mpeg"
                "mp4" -> "video/mp4"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
        }

        fun formatTimestamp(millis: Long): String {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            return sdf.format(Date(millis))
        }
    }
}
