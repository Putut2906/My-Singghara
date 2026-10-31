package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import okio.buffer

// Model data file untuk Google Drive
data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val url: String,
    val createdTime: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Simpan Web App URL di SharedPreferences agar tidak hilang saat restart
    val sharedPrefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }
    val defaultUrl = "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec"
    var appsScriptUrl by remember {
        val saved = sharedPrefs.getString("apps_script_url", "") ?: ""
        mutableStateOf(if (saved.trim().isEmpty()) defaultUrl else saved)
    }
    
    var fileList by remember { mutableStateOf<List<DriveFileItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter type & Sort States
    var selectedTypeFilter by remember { mutableStateOf("Semua") }
    var selectedSortOption by remember { mutableStateOf("Terbaru") }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    
    // Selection State
    var selectedFileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var uploadStatusText by remember { mutableStateOf("") }
    var isBulkDeleting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isOnline by remember { mutableStateOf(true) }

    LaunchedEffect(viewModel.currentRoute) {
        val saved = sharedPrefs.getString("apps_script_url", "") ?: ""
        if (saved.isNotEmpty()) {
            appsScriptUrl = saved
        }
    }
    
    var fileToDelete by remember { mutableStateOf<DriveFileItem?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    
    // Cek koneksi internet
    fun checkInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // Inisialisasi pengecekan koneksi
    LaunchedEffect(Unit) {
        isOnline = checkInternetConnection()
    }

    // OkHttp Client configured for 30s timeout and follow redirects
    val client = remember {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Fungsi fetch file list dari Google Drive
    fun fetchFileList() {
        if (!checkInternetConnection()) {
            isOnline = false
            return
        }
        isOnline = true
        
        if (appsScriptUrl.trim().isEmpty()) {
            statusText = "URL Google Apps Script belum dikonfigurasi."
            return
        }
        
        isLoading = true
        statusText = "Mengambil daftar file..."
        selectedFileIds = emptySet() // Reset seleksi
        
        scope.launch(Dispatchers.IO) {
            try {
                // Endpoint list: url?action=list
                val url = "${appsScriptUrl.trim()}?action=list"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && bodyString.isNotEmpty()) {
                        val json = JSONObject(bodyString)
                        if (json.optString("status") == "success") {
                            val array = json.getJSONArray("files")
                            val temp = mutableListOf<DriveFileItem>()
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                temp.add(
                                    DriveFileItem(
                                        id = obj.getString("id"),
                                        name = obj.getString("name"),
                                        mimeType = obj.getString("mimeType"),
                                        size = obj.getLong("size"),
                                        url = obj.optString("url", ""),
                                        createdTime = obj.optString("createdTime", obj.optString("date", ""))
                                    )
                                )
                            }
                            withContext(Dispatchers.Main) {
                                fileList = temp
                                statusText = "Berhasil memuat ${temp.size} file."
                                isLoading = false
                            }
                        } else {
                            val errMsg = json.optString("message", "Gagal memuat file.")
                            withContext(Dispatchers.Main) {
                                statusText = "Gagal: $errMsg"
                                isLoading = false
                                viewModel.showToastPopup("Kesalahan: $errMsg")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            statusText = "Server merespons kode: ${response.code}"
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText = "Koneksi Bermasalah: ${e.message}"
                    isLoading = false
                    viewModel.showToastPopup("Tidak dapat menghubungi server. Periksa URL atau koneksi Anda.")
                }
            }
        }
    }

    // Fungsi upload file sekaligus banyak (Base64) dengan progress tracking %
    fun uploadMultipleFiles(uris: List<Uri>) {
        if (!checkInternetConnection()) {
            isOnline = false
            return
        }
        isOnline = true
        
        if (appsScriptUrl.trim().isEmpty()) {
            viewModel.showToastPopup("Harap konfigurasikan URL Apps Script terlebih dahulu!")
            return
        }
        
        isUploading = true
        uploadProgress = 0
        uploadStatusText = "Mempersiapkan pengunggahan..."
        
        scope.launch(Dispatchers.IO) {
            val totalFiles = uris.size
            var successCount = 0
            var failCount = 0
            
            for ((index, uri) in uris.withIndex()) {
                val currentFileNumber = index + 1
                try {
                    val resolver = context.contentResolver
                    var name = "file_${System.currentTimeMillis()}"
                    var mimeType = "application/octet-stream"
                    var size: Long = -1
                    
                    try {
                        resolver.getType(uri)?.let { mimeType = it }
                    } catch (e: Exception) {
                        android.util.Log.e("ShareScreen", "Gagal mendapatkan tipe mime: ${e.message}")
                    }

                    try {
                        val cursor = resolver.query(uri, null, null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIdx != -1) {
                                    val cursorName = it.getString(nameIdx)
                                    if (!cursorName.isNullOrEmpty()) {
                                        name = cursorName
                                    }
                                }
                                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeIdx != -1) {
                                    size = it.getLong(sizeIdx)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ShareScreen", "Gagal query metadata file: ${e.message}")
                    }
                    
                    if (size == -1L) {
                        try {
                            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                size = pfd.statSize
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ShareScreen", "Gagal mendapatkan ukuran file descriptor: ${e.message}")
                        }
                    }
                    
                    val maxLimit = 20 * 1024 * 1024 // 20 MB
                    if (size > maxLimit) {
                        withContext(Dispatchers.Main) {
                            viewModel.showToastPopup("Berkas $name terlalu besar (${String.format("%.1f", size / (1024f * 1024f))} MB). Batas maksimum adalah 20 MB.")
                        }
                        failCount++
                        continue
                    }
                    
                    withContext(Dispatchers.Main) {
                        uploadStatusText = "Mengunggah $currentFileNumber/$totalFiles: $name"
                        uploadProgress = 0
                    }
                    
                    val inputStream = resolver.openInputStream(uri)
                    val rawBytes = inputStream?.readBytes()
                    inputStream?.close()
                    
                    if (rawBytes != null) {
                        val base64String = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                        
                        val jsonPayload = JSONObject().apply {
                            put("action", "upload")
                            put("filename", name)
                            put("mimeType", mimeType)
                            put("base64Data", base64String)
                        }
                        
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val rawBody = jsonPayload.toString().toRequestBody(mediaType)
                        
                        val progressBody = ProgressRequestBody(rawBody) { bytesWritten, contentLength ->
                            val pct = if (contentLength > 0) {
                                (bytesWritten * 100 / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            scope.launch(Dispatchers.Main) {
                                uploadProgress = pct
                            }
                        }
                        
                        val request = Request.Builder()
                            .url(appsScriptUrl.trim())
                            .post(progressBody)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            val bodyString = response.body?.string() ?: ""
                            if (response.isSuccessful && bodyString.isNotEmpty()) {
                                val json = JSONObject(bodyString)
                                if (json.optString("status") == "success") {
                                    successCount++
                                } else {
                                    val errMsg = json.optString("message", "Gagal mengunggah berkas ke Google Drive.")
                                    withContext(Dispatchers.Main) {
                                        viewModel.showToastPopup("Gagal mengunggah $name: $errMsg")
                                    }
                                    failCount++
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    viewModel.showToastPopup("Gagal mengunggah $name. Server merespons kode: ${response.code}")
                                }
                                failCount++
                            }
                        }
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShareScreen", "Exception during upload", e)
                    withContext(Dispatchers.Main) {
                        viewModel.showToastPopup("Kesalahan mengunggah: ${e.localizedMessage ?: e.message}")
                    }
                    failCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                isUploading = false
                uploadProgress = 0
                uploadStatusText = ""
                if (failCount == 0) {
                    viewModel.showToastPopup("Berhasil mengunggah semua $successCount berkas!")
                } else {
                    viewModel.showToastPopup("Selesai mengunggah. Sukses: $successCount, Gagal: $failCount")
                }
                fetchFileList()
            }
        }
    }

    // Fungsi hapus file tunggal
    fun deleteFileFromDrive(fileId: String, fileName: String) {
        if (!checkInternetConnection()) {
            isOnline = false
            return
        }
        isOnline = true
        
        if (appsScriptUrl.trim().isEmpty()) {
            return
        }
        
        isLoading = true
        statusText = "Menghapus file..."
        
        scope.launch(Dispatchers.IO) {
            try {
                val jsonPayload = JSONObject().apply {
                    put("action", "delete")
                    put("fileId", fileId)
                }
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonPayload.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url(appsScriptUrl.trim())
                    .post(requestBody)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && bodyString.isNotEmpty()) {
                        val json = JSONObject(bodyString)
                        if (json.optString("status") == "success") {
                            withContext(Dispatchers.Main) {
                                viewModel.showToastPopup("Berhasil menghapus: $fileName")
                                fetchFileList()
                            }
                        } else {
                            val errMsg = json.optString("message", "Gagal menghapus file.")
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                viewModel.showToastPopup("Hapus gagal: $errMsg")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            viewModel.showToastPopup("Respons server bermasalah: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    viewModel.showToastPopup("Kesalahan Hapus: ${e.message}")
                }
            }
        }
    }

    // Fungsi hapus banyak file secara massal (Bulk Delete)
    fun bulkDeleteFiles(ids: Set<String>) {
        if (!checkInternetConnection()) {
            isOnline = false
            return
        }
        isOnline = true
        
        if (appsScriptUrl.trim().isEmpty() || ids.isEmpty()) {
            return
        }
        
        isBulkDeleting = true
        isLoading = true
        statusText = "Menghapus ${ids.size} file..."
        
        scope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            val listToDelete = fileList.filter { it.id in ids }
            
            for ((index, item) in listToDelete.withIndex()) {
                withContext(Dispatchers.Main) {
                    statusText = "Menghapus (${index + 1}/${ids.size}): ${item.name}..."
                }
                try {
                    val jsonPayload = JSONObject().apply {
                        put("action", "delete")
                        put("fileId", item.id)
                    }
                    
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = jsonPayload.toString().toRequestBody(mediaType)
                    
                    val request = Request.Builder()
                        .url(appsScriptUrl.trim())
                        .post(requestBody)
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (response.isSuccessful && bodyString.isNotEmpty()) {
                            val json = JSONObject(bodyString)
                            if (json.optString("status") == "success") {
                                successCount++
                            } else {
                                failCount++
                            }
                        } else {
                            failCount++
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                }
            }
            
            withContext(Dispatchers.Main) {
                isLoading = false
                isBulkDeleting = false
                selectedFileIds = emptySet()
                viewModel.showToastPopup("Berhasil menghapus $successCount file. Gagal: $failCount.")
                fetchFileList()
            }
        }
    }

    // Fungsi download semua isi folder sebagai zip
    fun downloadAllAsZip() {
        if (!checkInternetConnection()) {
            isOnline = false
            return
        }
        isOnline = true
        
        if (appsScriptUrl.trim().isEmpty()) {
            viewModel.showToastPopup("Konfigurasikan URL Apps Script terlebih dahulu!")
            return
        }
        
        isLoading = true
        statusText = "Mengompres seluruh isi folder menjadi ZIP..."
        
        scope.launch(Dispatchers.IO) {
            try {
                val url = "${appsScriptUrl.trim()}?action=download_all"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string() ?: ""
                    if (response.isSuccessful && bodyString.isNotEmpty()) {
                        val json = JSONObject(bodyString)
                        if (json.optString("status") == "success") {
                            val zipBase64 = json.getString("base64")
                            val bytes = Base64.decode(zipBase64, Base64.DEFAULT)
                            
                            val resolver = context.contentResolver
                            var success = false
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "My_Drive_Backup_${System.currentTimeMillis()}.zip")
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val saveUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (saveUri != null) {
                                    resolver.openOutputStream(saveUri)?.use { outputStream ->
                                        outputStream.write(bytes)
                                    }
                                    success = true
                                }
                            } else {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                    val file = java.io.File(downloadsDir, "My_Drive_Backup_${System.currentTimeMillis()}.zip")
                                    java.io.FileOutputStream(file).use { outputStream ->
                                        outputStream.write(bytes)
                                    }
                                    success = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            if (success) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    statusText = "Berhasil mengunduh seluruh folder sebagai ZIP!"
                                    viewModel.showToastPopup("ZIP berhasil disimpan di folder 'Downloads'!")
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    viewModel.showToastPopup("Gagal membuat berkas unduhan di perangkat.")
                                }
                            }
                        } else {
                            val errMsg = json.optString("message", "Proses kompresi/unduh ZIP gagal.")
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                statusText = "Gagal ZIP: $errMsg"
                                viewModel.showToastPopup("Server Error: $errMsg")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            statusText = "Koneksi gagal (Kode: ${response.code})"
                            viewModel.showToastPopup("Kode respons HTTP: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusText = "Terjadi kegagalan unduh: ${e.message}"
                    viewModel.showToastPopup("Kesalahan ZIP: ${e.message}")
                }
            }
        }
    }

    // File picker contract launcher for multiple files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uploadMultipleFiles(uris)
        }
    }

    // Helper untuk kategorisasi file berdasarkan nama atau mimeType
    fun getFileTypeGroup(item: DriveFileItem): String {
        val name = item.name.lowercase()
        val mime = item.mimeType.lowercase()
        return when {
            name.endsWith(".pdf") || mime.contains("pdf") -> "PDF"
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".webp") || mime.contains("image") -> "Gambar"
            name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".txt") || name.endsWith(".ppt") || name.endsWith(".pptx") || mime.contains("document") || mime.contains("sheet") || mime.contains("text") || mime.contains("presentation") -> "Dokumen"
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") || mime.contains("zip") || mime.contains("compressed") || mime.contains("archive") -> "Arsip"
            else -> "Lainnya"
        }
    }

    // Filter daftar berdasarkan input search bar & kategori tipe
    val filteredAndCategorizedList = remember(searchQuery, fileList, selectedTypeFilter) {
        var result = fileList
        
        // 1. Filter Search Query
        if (searchQuery.trim().isNotEmpty()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.mimeType.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // 2. Filter Kategori Tipe File
        if (selectedTypeFilter != "Semua") {
            result = result.filter { getFileTypeGroup(it) == selectedTypeFilter }
        }
        
        result
    }

    // Sort daftar yang telah difilter
    val sortedList = remember(filteredAndCategorizedList, selectedSortOption) {
        when (selectedSortOption) {
            "Terbaru" -> filteredAndCategorizedList // Urutan default API (biasanya terbaru di atas)
            "Terlama" -> filteredAndCategorizedList.reversed()
            "Nama A-Z" -> filteredAndCategorizedList.sortedBy { it.name.lowercase() }
            "Nama Z-A" -> filteredAndCategorizedList.sortedByDescending { it.name.lowercase() }
            "Ukuran Terbesar" -> filteredAndCategorizedList.sortedByDescending { it.size }
            "Ukuran Terkecil" -> filteredAndCategorizedList.sortedBy { it.size }
            else -> filteredAndCategorizedList
        }
    }

    // Auto load jika URL ada
    LaunchedEffect(appsScriptUrl) {
        if (appsScriptUrl.trim().isNotEmpty() && checkInternetConnection()) {
            fetchFileList()
        }
    }

    // Render Offline atau Online States
    if (!isOnline) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SUB-MENU SWITCHER (Share Cloud vs Transfer File)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { /* Already on Share */ },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share (Google Drive)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    Surface(
                        onClick = { viewModel.currentRoute = "transfer_file" },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transfer File (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AnimatedOfflineState(onRetry = {
                        isOnline = checkInternetConnection()
                        if (isOnline && appsScriptUrl.trim().isNotEmpty()) {
                            fetchFileList()
                        }
                    })
                }
            }
        }
    } else if (appsScriptUrl.trim().isEmpty()) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Penyimpanan Cloud Belum Aktif",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "URL Google Apps Script belum dikonfigurasi di aplikasi ini. Anda dapat mengaktifkannya melalui halaman Profil pada menu Pengaturan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.currentRoute = "profil" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Konfigurasi di Halaman Profil")
                }
            }
        }
    } else {
        // ONLINE MODE
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = paddingValues.calculateBottomPadding() + 12.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SUB-MENU SWITCHER (Share Cloud vs Transfer File)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { /* Currently active */ },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share (Google Drive)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    Surface(
                        onClick = { viewModel.currentRoute = "transfer_file" },
                        modifier = Modifier.weight(1f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transfer File (Offline)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Header Status Terhubung yang Elegan (Tanpa Emojis)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Penyimpanan Cloud Aktif",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isLoading) "Mensinkronkan berkas..." else "Terhubung ke Google Drive",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { fetchFileList() },
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Segarkan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Baris Tombol Utama: Upload + Download Semua ZIP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = uploadProgress / 100f,
                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                        label = "upload_progress"
                    )

                    Button(
                        onClick = { 
                            if (!isUploading) {
                                MainViewModel.instance?.preventAutoLock = true
                                filePickerLauncher.launch("*/*") 
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("upload_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUploading) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                progress = animatedProgress,
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unggah $uploadProgress%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = "Upload", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Baru", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { downloadAllAsZip() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("download_all_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = "Download", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download ZIP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Detailed Upload Status Row
                AnimatedVisibility(
                    visible = isUploading && uploadStatusText.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uploadStatusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Search Bar Filter
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari file...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari", modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                )

                // Row combining Sort Button (pinned on left) and scrollable chips
                val typeOptions = listOf("Semua", "PDF", "Gambar", "Dokumen", "Arsip", "Lainnya")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Filter (Urutan) Icon Button on the left
                    Box {
                        IconButton(
                            onClick = { isSortMenuExpanded = true },
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Urutkan: $selectedSortOption",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            val sortOptions = listOf(
                                "Terbaru",
                                "Terlama",
                                "Nama A-Z",
                                "Nama Z-A",
                                "Ukuran Terbesar",
                                "Ukuran Terkecil"
                            )
                            sortOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 12.sp) },
                                    onClick = {
                                        selectedSortOption = option
                                        isSortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Horizontal Type Filter List (Scrollable Row of Chips)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        typeOptions.forEach { type ->
                            val isSelected = selectedTypeFilter == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTypeFilter = type },
                                label = { Text(type, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // ANIMATED SELECTION ACTION BAR (Contextual top bar)
                AnimatedVisibility(
                    visible = selectedFileIds.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(onClick = { selectedFileIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Batal", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Text(
                                text = "${selectedFileIds.size} file terpilih",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Select/Deselect All
                            val allIds = sortedList.map { it.id }.toSet()
                            val isAllSelected = selectedFileIds.size == allIds.size && allIds.isNotEmpty()
                            
                            TextButton(
                                onClick = {
                                    selectedFileIds = if (isAllSelected) emptySet() else allIds
                                }
                            ) {
                                Text(
                                    text = if (isAllSelected) "Batal Semua" else "Pilih Semua",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                             // Open Selected (sequential browser view)
                             IconButton(
                                 onClick = {
                                     val selectedList = fileList.filter { it.id in selectedFileIds }
                                     selectedList.forEach { item ->
                                         if (item.url.isNotEmpty()) {
                                             try {
                                                 val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                     selector = Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
                                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                                     }
                                                 }
                                                 val chooser = Intent.createChooser(intent, "Buka dengan Browser")
                                                 context.startActivity(chooser)
                                             } catch (e: Exception) {
                                                 // ignore individual launches
                                             }
                                         }
                                     }
                                     viewModel.showToastPopup("Membuka ${selectedList.size} file di penjelajah web...")
                                 }
                             ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Buka Terpilih",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // Delete Selected
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus Terpilih",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // TABLE BODY (List / Scrollable grid)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = statusText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (sortedList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tidak Ada File",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tidak ada berkas yang sesuai dengan pencarian atau filter tipe Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sortedList, key = { it.id }) { item ->
                                val isSelected = selectedFileIds.contains(item.id)
                                TableFileRow(
                                    item = item,
                                    isSelected = isSelected,
                                    isSelectionModeActive = selectedFileIds.isNotEmpty(),
                                    onSelectToggle = {
                                        selectedFileIds = if (isSelected) {
                                            selectedFileIds - item.id
                                        } else {
                                            selectedFileIds + item.id
                                        }
                                    },
                                    onOpen = { link ->
                                        if (link.isNotEmpty()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                                                    selector = Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
                                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                                    }
                                                }
                                                val chooser = Intent.createChooser(intent, "Buka dengan Browser")
                                                context.startActivity(chooser)
                                            } catch (e: Exception) {
                                                try {
                                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                                    context.startActivity(fallback)
                                                } catch (err: Exception) {
                                                    viewModel.showToastPopup("Tidak ada browser yang tersedia.")
                                                }
                                            }
                                        } else {
                                            viewModel.showToastPopup("URL file tidak tersedia.")
                                        }
                                    },
                                    onDelete = { fileToDelete = item }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Hapus Permanen (Tunggal)
    fileToDelete?.let { item ->
         AlertDialog(
             onDismissRequest = { fileToDelete = null },
             icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
             title = { Text("Hapus Permanen?") },
             text = {
                 Text(
                     "Apakah Anda yakin ingin menghapus '${item.name}' secara permanen dari folder Google Drive Anda?",
                     fontSize = 13.sp
                 )
             },
             confirmButton = {
                 Button(
                     onClick = {
                         val fileId = item.id
                         val fileName = item.name
                         fileToDelete = null
                         deleteFileFromDrive(fileId, fileName)
                     },
                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                 ) {
                     Text("Hapus Permanen")
                 }
             },
             dismissButton = {
                 TextButton(onClick = { fileToDelete = null }) {
                     Text("Batal")
                 }
             }
         )
    }

    // Dialog Konfirmasi Hapus Massal (Bulk Delete)
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus Berkas Terpilih?") },
            text = {
                Text(
                    "Apakah Anda yakin ingin menghapus ${selectedFileIds.size} file terpilih secara permanen dari Google Drive?",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteConfirm = false
                        bulkDeleteFiles(selectedFileIds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// Komponen Baris File di dalam Tabel (Modern look, no emojis)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableFileRow(
    item: DriveFileItem,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    onSelectToggle: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: () -> Unit
) {
    val fileExtension = remember(item.name) {
        val lastDot = item.name.lastIndexOf('.')
        if (lastDot != -1 && lastDot < item.name.length - 1) {
            item.name.substring(lastDot + 1).uppercase()
        } else {
            "DATA"
        }
    }

    val extensionColor = remember(fileExtension) {
        when (fileExtension) {
            "PDF" -> Color(0xFFD32F2F)
            "ZIP", "RAR", "7Z" -> Color(0xFF7B1FA2)
            "XLS", "XLSX" -> Color(0xFF388E3C)
            "DOC", "DOCX" -> Color(0xFF1976D2)
            "JPG", "PNG", "JPEG", "WEBP" -> Color(0xFFF57C00)
            "TXT" -> Color(0xFF455A64)
            else -> Color(0xFF303F9F)
        }
    }

    val formattedSize = remember(item.size) {
        val bytes = item.size
        if (bytes <= 0) return@remember "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionModeActive) {
                        onSelectToggle()
                    } else {
                        onOpen(item.url)
                    }
                },
                onLongClick = {
                    onSelectToggle()
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox untuk multi-selection (Tampil hanya jika mode seleksi aktif)
        AnimatedVisibility(
            visible = isSelectionModeActive,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectToggle() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Lingkaran Ikon File
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(extensionColor.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, extensionColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (fileExtension) {
                    "ZIP", "RAR", "7Z" -> Icons.Outlined.FolderZip
                    "JPG", "PNG", "JPEG", "WEBP" -> Icons.Default.Image
                    "PDF" -> Icons.Default.PictureAsPdf
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = extensionColor,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))

        // Detail File (Nama, Ekstensi & Ukuran)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$fileExtension • $formattedSize",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Tombol Aksi Mandiri: Buka & Hapus (Tanpa lingkaran latar belakang)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onOpen(item.url) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Buka File",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus File",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// State Animasi Melayang untuk penanda Offline (Tanpa Emojis)
@Composable
fun AnimatedOfflineState(onRetry: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "offline_loop")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "oy"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ra"
    )

    val opacityState by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "op"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(y = offsetY.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = opacityState * 0.18f))
            )

            Icon(
                imageVector = Icons.Default.Autorenew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(110.dp)
                    .rotate(rotateAngle)
            )

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline Mode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Koneksi Terputus",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Halaman Share memerlukan koneksi internet aktif. Silakan aktifkan Wi-Fi atau Data Seluler Anda, lalu segarkan halaman.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Segarkan")
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Segarkan Jaringan", fontSize = 13.sp)
        }
    }
}

// Custom RequestBody to track upload progress
class ProgressRequestBody(
    private val delegate: okhttp3.RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit
) : okhttp3.RequestBody() {

    override fun contentType(): okhttp3.MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: okio.BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class CountingSink(delegate: okio.Sink) : okio.ForwardingSink(delegate) {
        private var bytesWritten = 0L

        override fun write(source: okio.Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            onProgress(bytesWritten, contentLength())
        }
    }
}
