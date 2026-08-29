package com.example.web

import android.content.Context
import com.example.data.AgendaItem
import com.example.data.AnggotaItem
import com.example.data.JadwalKegiatan
import com.example.data.LocalRepository
import com.example.data.NoteItem
import com.example.data.RiwayatPresensi
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LocalHttpServer(
    private val context: Context,
    private val repository: LocalRepository,
    private val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Session Auth Management for QR Barcode Protection
    private val verifiedTokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile var latestPinCode: String = generateNewPin()
    @Volatile var latestToken: String = generateNewToken()
    @Volatile var isAccessRejected: Boolean = false
    @Volatile var lastAccessNotifyTime: Long = 0L
    @Volatile var lastAccessClientIp: String = ""
    @Volatile var lastLogoutTime: Long = 0L

    var onAccessRequested: ((clientIp: String, timestamp: Long) -> Unit)? = null

    fun generateNewPin(): String = (100000..999999).random().toString()
    fun generateNewToken(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    fun approveSession(tokenOrPin: String): Boolean {
        val trimmed = tokenOrPin.trim()
        if (trimmed.isEmpty()) return false
        isAccessRejected = false
        verifiedTokens.add(trimmed)
        verifiedTokens.add(latestToken)
        return true
    }

    fun approveAllPending(): Boolean {
        isAccessRejected = false
        verifiedTokens.add(latestToken)
        return true
    }

    fun rejectPendingAccess() {
        isAccessRejected = true
        verifiedTokens.clear()
        latestPinCode = generateNewPin()
        latestToken = generateNewToken()
    }

    fun resetAccessRejection() {
        isAccessRejected = false
    }

    fun triggerAccessNotification(clientIp: String) {
        val now = System.currentTimeMillis()
        if (isAccessRejected) return
        if (now - lastLogoutTime < 10000L) return
        if (now - lastAccessNotifyTime < 15000L && clientIp == lastAccessClientIp) return

        lastAccessNotifyTime = now
        lastAccessClientIp = clientIp
        scope.launch(Dispatchers.Main) {
            onAccessRequested?.invoke(clientIp, now)
        }
    }

    fun revokeAll() {
        verifiedTokens.clear()
        latestPinCode = generateNewPin()
        latestToken = generateNewToken()
        isAccessRejected = false
    }

    fun isTokenVerified(token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        return verifiedTokens.contains(token)
    }

    data class KasirScanEvent(
        val id: String = java.util.UUID.randomUUID().toString(),
        val barcode: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val kasirScanEvents = java.util.concurrent.ConcurrentLinkedQueue<KasirScanEvent>()

    fun pushBarcodeScan(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isNotEmpty()) {
            kasirScanEvents.add(KasirScanEvent(barcode = trimmed))
            while (kasirScanEvents.size > 100) {
                kasirScanEvents.poll()
            }
        }
    }

    fun start(onStarted: (String) -> Unit, onError: (String) -> Unit) {
        if (isRunning) return
        activeInstance = this
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                val ip = getIpAddress(context)
                val url = "http://$ip:$port"
                withContext(Dispatchers.Main) {
                    onStarted(url)
                }
                while (isRunning) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(socket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                isRunning = false
                if (activeInstance == this@LocalHttpServer) activeInstance = null
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Gagal menjalankan server Web PC")
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        if (activeInstance == this) activeInstance = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    fun isServerRunning(): Boolean = isRunning

    companion object {
        var activeInstance: LocalHttpServer? = null

        fun getIpAddress(context: Context): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                var fallbackIp: String? = null
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress
                            if (hostAddress != null && !hostAddress.startsWith("127.")) {
                                val name = networkInterface.name.lowercase()
                                if (name.contains("wlan") || name.contains("ap") || name.contains("swlan") || name.contains("rndis")) {
                                    return hostAddress
                                }
                                if (fallbackIp == null) {
                                    fallbackIp = hostAddress
                                }
                            }
                        }
                    }
                }
                if (fallbackIp != null) return fallbackIp
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "127.0.0.1"
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val headerLower = line!!.lowercase()
                    if (headerLower.startsWith("content-length:")) {
                        contentLength = headerLower.substringAfter("content-length:").trim().toIntOrNull() ?: 0
                    }
                }

                val body = if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val r = reader.read(charArray, read, contentLength - read)
                        if (r <= 0) break
                        read += r
                    }
                    String(charArray, 0, read)
                } else ""

                val path = fullPath.substringBefore("?")
                val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""
                val queryParams = parseQueryParams(queryString)

                if (method == "OPTIONS") {
                    sendCorsResponse(output)
                    return
                }

                val clientIp = s.inetAddress?.hostAddress ?: "127.0.0.1"

                when {
                    path == "/" || path == "/index.html" -> {
                        if (!isAccessRejected) {
                            triggerAccessNotification(clientIp)
                        }
                        sendHtmlResponse(output, getWebUiHtml())
                    }
                    path == "/app-logo.png" || path == "/logo.png" || path == "/favicon.ico" -> {
                        try {
                            val inputStream = context.resources.openRawResource(com.example.R.drawable.ic_app_logo)
                            val bytes = inputStream.readBytes()
                            inputStream.close()
                            sendBinaryResponse(output, 200, "OK", "image/png", bytes)
                        } catch (e: Exception) {
                            sendResponse(output, 404, "Not Found", "text/plain", "App Logo Not Found")
                        }
                    }
                    path == "/company-signature.png" -> {
                        try {
                            val sigFile = java.io.File(context.filesDir, "company_id_card_signature.png")
                            if (sigFile.exists() && sigFile.length() > 0) {
                                sendBinaryResponse(output, 200, "OK", "image/png", sigFile.readBytes())
                            } else {
                                sendResponse(output, 404, "Not Found", "text/plain", "Company Signature Not Found")
                            }
                        } catch (e: Exception) {
                            sendResponse(output, 404, "Not Found", "text/plain", "Signature Not Found")
                        }
                    }
                    path == "/api/status" -> {
                        val status = JSONObject().apply {
                            put("status", "online")
                            put("app", "Singghara Suite")
                            put("ip", getIpAddress(context))
                            put("port", port)
                        }
                        sendJsonResponse(output, status.toString())
                    }
                    path.startsWith("/api/notes") -> {
                        handleNotesRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/shopping") -> {
                        handleShoppingRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/agenda") -> {
                        handleAgendaRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/jadwal") -> {
                        handleJadwalRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/kasir") -> {
                        handleKasirRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/savings") -> {
                        handleSavingsRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/dashboard") -> {
                        handleDashboardRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/pembukuan") -> {
                        handlePembukuanRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/share") -> {
                        handleShareRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/quran") -> {
                        handleQuranRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/keuangan") -> {
                        handleKeuanganRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/anggota") -> {
                        handleAnggotaRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/idcard") -> {
                        handleIdCardRoute(queryParams, output)
                    }
                    path.startsWith("/api/presensi") -> {
                        handlePresensiRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/profile") -> {
                        handleProfileRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/auth") -> {
                        handleAuthRoute(method, queryParams, body, output, clientIp)
                    }
                    else -> {
                        sendResponse(output, 404, "Not Found", "text/plain", "404 Not Found")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (param in queryString.split("&")) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2) {
                map[URLDecoder.decode(pair[0], "UTF-8")] = URLDecoder.decode(pair[1], "UTF-8")
            }
        }
        return map
    }

    // --- ROUTE HANDLERS ---
    private fun handleAuthRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream, clientIp: String = "127.0.0.1") {
        val pathAction = queryParams["action"] ?: ""
        val token = queryParams["token"] ?: ""
        val response = JSONObject()
        val ip = getIpAddress(context)
        val url = "http://$ip:$port"

        if (method == "POST") {
            val jsonBody = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
            val reqToken = jsonBody.optString("token", "").ifEmpty { token }
            val reqPin = jsonBody.optString("pin", "")
            val action = jsonBody.optString("action", pathAction)

            if (action == "logout" || action == "revoke") {
                lastLogoutTime = System.currentTimeMillis()
                revokeAll()
                response.put("success", true)
                response.put("authenticated", false)
                response.put("rejected", false)
                response.put("token", latestToken)
                response.put("pin", latestPinCode)
                response.put("qrPayload", "SINGGHARA_AUTH:$latestToken:$latestPinCode")
                response.put("message", "Sesi PC berhasil direset")
            } else if (action == "request_retry" || action == "retry") {
                lastLogoutTime = 0L
                resetAccessRejection()
                triggerAccessNotification(clientIp)
                response.put("success", true)
                response.put("authenticated", false)
                response.put("rejected", false)
                response.put("token", latestToken)
                response.put("pin", latestPinCode)
                response.put("qrPayload", "SINGGHARA_AUTH:$latestToken:$latestPinCode")
                response.put("message", "Permintaan akses ulang dikirim ke smartphone")
            } else if (action == "approve" || reqToken.isNotEmpty() || reqPin.isNotEmpty()) {
                if (reqPin.isNotEmpty() && reqPin.equals(latestPinCode, ignoreCase = true)) {
                    isAccessRejected = false
                    verifiedTokens.add(latestToken)
                    response.put("success", true)
                    response.put("authenticated", true)
                    response.put("rejected", false)
                    response.put("token", latestToken)
                    response.put("message", "Akses PC berhasil diverifikasi!")
                } else if (reqToken.isNotEmpty()) {
                    approveSession(reqToken)
                    response.put("success", true)
                    response.put("authenticated", true)
                    response.put("rejected", false)
                    response.put("token", latestToken)
                    response.put("message", "Akses PC berhasil diverifikasi!")
                } else {
                    approveAllPending()
                    response.put("success", true)
                    response.put("authenticated", true)
                    response.put("rejected", false)
                    response.put("token", latestToken)
                    response.put("message", "Semua sesi PC berhasil diverifikasi!")
                }
            } else {
                response.put("success", false)
                response.put("message", "Aksi tidak dikenal")
            }
            sendJsonResponse(output, response.toString())
            return
        }

        // GET requests: session status or initial payload
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val profileName = mainPrefs.getString("profile_name", "Singghara User") ?: "Singghara User"
        val profileAvatarIndex = mainPrefs.getInt("profile_avatar_index", 0)
        val profileImageUri = mainPrefs.getString("profile_image_uri", "") ?: ""
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        val hasCustomImage = profileAvatarIndex == -1 && (profileImageUri.isNotEmpty() || (localFile.exists() && localFile.length() > 0))

        val isVerified = if (token.isNotEmpty()) verifiedTokens.contains(token) else false

        response.put("authenticated", isVerified)
        response.put("rejected", isAccessRejected)
        response.put("token", latestToken)
        response.put("pin", latestPinCode)
        response.put("ip", ip)
        response.put("port", port)
        response.put("url", url)
        response.put("qrPayload", "SINGGHARA_AUTH:$latestToken:$latestPinCode")
        response.put("profileName", profileName)
        response.put("profileAvatarIndex", profileAvatarIndex)
        response.put("hasCustomImage", hasCustomImage)
        response.put("profileImageUrl", if (hasCustomImage) "/api/profile/image?t=${System.currentTimeMillis()}" else "")
        sendJsonResponse(output, response.toString())
    }

    private suspend fun handleNotesRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val notes = repository.allNotes.first()
                val jsonArr = JSONArray()
                notes.forEach { note ->
                    jsonArr.put(JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("category", note.category)
                        put("isPinned", note.isPinned)
                        put("colorHex", note.colorHex)
                        put("lastSaved", note.lastSaved)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val title = json.optString("title", "Catatan Baru")
                val content = json.optString("content", "")
                val category = json.optString("category", "Umum")
                val isPinned = json.optBoolean("isPinned", false)
                val colorHex = json.optString("colorHex", "#6366F1")
                val newNote = NoteItem(
                    title = title,
                    content = content,
                    charCount = content.length,
                    lineCount = content.split("\n").size,
                    lastSaved = System.currentTimeMillis(),
                    category = category,
                    isPinned = isPinned,
                    colorHex = colorHex
                )
                val newId = repository.insertNote(newNote)
                sendJsonResponse(output, JSONObject().put("success", true).put("id", newId).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val id = json.getInt("id")
                val title = json.optString("title", "Catatan")
                val content = json.optString("content", "")
                val category = json.optString("category", "Umum")
                val isPinned = json.optBoolean("isPinned", false)
                val colorHex = json.optString("colorHex", "#6366F1")
                val updatedNote = NoteItem(
                    id = id,
                    title = title,
                    content = content,
                    charCount = content.length,
                    lineCount = content.split("\n").size,
                    lastSaved = System.currentTimeMillis(),
                    category = category,
                    isPinned = isPinned,
                    colorHex = colorHex
                )
                repository.updateNote(updatedNote)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                val id = idStr?.toIntOrNull()
                if (id != null) {
                    val notes = repository.allNotes.first()
                    val target = notes.find { it.id == id }
                    if (target != null) {
                        repository.deleteNote(target)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
                sendJsonResponse(output, JSONObject().put("success", false).put("message", "ID tidak ditemukan").toString(), 400)
            }
        }
    }

    private suspend fun handleShoppingRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val items = repository.allShoppingItems.first()
                val jsonArr = JSONArray()
                items.forEach { item ->
                    jsonArr.put(JSONObject().apply {
                        put("id", item.id)
                        put("nama", item.nama)
                        put("jenis", item.jenis)
                        put("hargaSatuan", item.hargaSatuan)
                        put("qty", item.qty)
                        put("pajak", item.pajak)
                        put("total", item.total)
                        put("tanggal", item.tanggal)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val nama = json.optString("nama", "Barang")
                val jenis = json.optString("jenis", "Lainnya")
                val harga = json.optDouble("hargaSatuan", 0.0)
                val qty = json.optInt("qty", 1)
                val pajak = json.optDouble("pajak", 0.0)
                val total = (harga * qty) * (1.0 + pajak / 100.0)
                val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))

                val newItem = ShoppingItem(
                    nama = nama,
                    jenis = jenis,
                    hargaSatuan = harga,
                    qty = qty,
                    pajak = pajak,
                    total = total,
                    tanggal = tanggal
                )
                repository.insertShopping(newItem)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val id = json.getInt("id")
                val nama = json.optString("nama", "Barang")
                val jenis = json.optString("jenis", "Lainnya")
                val harga = json.optDouble("hargaSatuan", 0.0)
                val qty = json.optInt("qty", 1)
                val pajak = json.optDouble("pajak", 0.0)
                val total = (harga * qty) * (1.0 + pajak / 100.0)
                val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))

                val updatedItem = ShoppingItem(
                    id = id,
                    nama = nama,
                    jenis = jenis,
                    hargaSatuan = harga,
                    qty = qty,
                    pajak = pajak,
                    total = total,
                    tanggal = tanggal
                )
                repository.updateShopping(updatedItem)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                val id = idStr?.toIntOrNull()
                if (id != null) {
                    val items = repository.allShoppingItems.first()
                    val target = items.find { it.id == id }
                    if (target != null) {
                        repository.deleteShopping(target)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
                sendJsonResponse(output, JSONObject().put("success", false).put("message", "ID tidak ditemukan").toString(), 400)
            }
        }
    }

    private suspend fun handleAgendaRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val agendas = repository.allAgendas.first()
                val jsonArr = JSONArray()
                agendas.forEach { agenda ->
                    jsonArr.put(JSONObject().apply {
                        put("id", agenda.id)
                        put("tanggal", agenda.tanggal)
                        put("judul", agenda.judul)
                        put("detail", agenda.detail)
                        put("tipe", agenda.tipe)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
                val judul = json.optString("judul", "Agenda Baru")
                val detail = json.optString("detail", "")
                val tipe = json.optString("tipe", "agenda")

                val newAgenda = AgendaItem(
                    tanggal = tanggal,
                    judul = judul,
                    detail = detail,
                    tipe = tipe
                )
                repository.insertAgenda(newAgenda)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val id = json.getInt("id")
                val tanggal = json.optString("tanggal", "")
                val judul = json.optString("judul", "")
                val detail = json.optString("detail", "")
                val tipe = json.optString("tipe", "agenda")

                val updatedAgenda = AgendaItem(
                    id = id,
                    tanggal = tanggal,
                    judul = judul,
                    detail = detail,
                    tipe = tipe
                )
                repository.insertAgenda(updatedAgenda)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                val id = idStr?.toIntOrNull()
                if (id != null) {
                    val agendas = repository.allAgendas.first()
                    val target = agendas.find { it.id == id }
                    if (target != null) {
                        repository.deleteAgenda(target)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
                sendJsonResponse(output, JSONObject().put("success", false).toString(), 400)
            }
        }
    }

    private suspend fun handleJadwalRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val list = repository.allJadwal.first()
                val jsonArr = JSONArray()
                list.forEach { item ->
                    jsonArr.put(JSONObject().apply {
                        put("id", item.id)
                        put("waktu", item.waktu)
                        put("kegiatan", item.kegiatan)
                        put("kategori", item.kategori)
                        put("isCompleted", item.isCompleted)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val waktu = json.optString("waktu", "08:00")
                val kegiatan = json.optString("kegiatan", "Kegiatan")
                val kategori = json.optString("kategori", "Umum")
                val newItem = JadwalKegiatan(
                    waktu = waktu,
                    kegiatan = kegiatan,
                    kategori = kategori,
                    isCompleted = false
                )
                repository.insertJadwal(newItem)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val id = json.getInt("id")
                val waktu = json.optString("waktu", "08:00")
                val kegiatan = json.optString("kegiatan", "")
                val kategori = json.optString("kategori", "Umum")
                val isCompleted = json.optBoolean("isCompleted", false)
                val updatedItem = JadwalKegiatan(
                    id = id,
                    waktu = waktu,
                    kegiatan = kegiatan,
                    kategori = kategori,
                    isCompleted = isCompleted
                )
                repository.insertJadwal(updatedItem)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                val id = idStr?.toIntOrNull()
                if (id != null) {
                    val list = repository.allJadwal.first()
                    val target = list.find { it.id == id }
                    if (target != null) {
                        repository.deleteJadwal(target)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
                sendJsonResponse(output, JSONObject().put("success", false).toString(), 400)
            }
        }
    }

    private suspend fun handleKasirRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)

        if (path == "/api/kasir/scan-events" || path.startsWith("/api/kasir/scan-events")) {
            if (method == "GET") {
                val since = queryParams["since"]?.toLongOrNull() ?: 0L
                val events = kasirScanEvents.filter { it.timestamp > since }
                val eventsArr = JSONArray()
                events.forEach { ev ->
                    eventsArr.put(JSONObject().apply {
                        put("id", ev.id)
                        put("barcode", ev.barcode)
                        put("timestamp", ev.timestamp)
                    })
                }
                val latestTs = kasirScanEvents.lastOrNull()?.timestamp ?: since
                val res = JSONObject().apply {
                    put("events", eventsArr)
                    put("latestTimestamp", latestTs)
                }
                sendJsonResponse(output, res.toString())
                return
            } else if (method == "POST") {
                val json = JSONObject(body)
                val barcode = json.optString("barcode", "")
                pushBarcodeScan(barcode)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
                return
            }
        }

        when (method) {
            "GET" -> {
                val stockStr = prefs.getString("list_stock", "") ?: ""
                val cashierStr = prefs.getString("list_cashier", "") ?: ""
                val namaUsaha = prefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya"

                val stocks = com.example.ui.screens.deserializeStock(stockStr)
                val cashiers = com.example.ui.screens.deserializeCashier(cashierStr)

                val stockArray = JSONArray()
                stocks.forEach { item ->
                    stockArray.put(JSONObject().apply {
                        put("id", item.id)
                        put("nama", item.nama)
                        put("kategori", item.kategori)
                        put("keterangan", item.keterangan)
                        put("harga", item.harga)
                        put("stok", item.stok)
                        put("barcode", item.barcode)
                    })
                }

                val cashierArray = JSONArray()
                cashiers.forEach { tx ->
                    cashierArray.put(JSONObject().apply {
                        put("id", tx.id)
                        put("waktu", tx.waktu)
                        put("totalHarga", tx.totalHarga)
                        put("tunai", tx.tunai)
                        put("taxEnabled", tx.taxEnabled)
                        put("taxRate", tx.taxRate)
                        val itemsArr = JSONArray()
                        tx.items.forEach { ci ->
                            itemsArr.put(JSONObject().apply {
                                put("id", ci.id)
                                put("namaBarang", ci.namaBarang)
                                put("kategori", ci.kategori)
                                put("qty", ci.qty)
                                put("harga", ci.harga)
                                put("totalHarga", ci.totalHarga)
                            })
                        }
                        put("items", itemsArr)
                    })
                }

                val resObj = JSONObject().apply {
                    put("namaUsaha", namaUsaha)
                    put("stock", stockArray)
                    put("cashier", cashierArray)
                }
                sendJsonResponse(output, resObj.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val action = json.optString("action", "save_product")
                val namaUsaha = prefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya"
                if (action == "checkout") {
                    val itemsArr = json.optJSONArray("items") ?: JSONArray()
                    val tunai = json.optDouble("tunai", 0.0)
                    val taxEnabled = json.optBoolean("taxEnabled", false)
                    val taxRate = json.optDouble("taxRate", 0.11)
                    val waktu = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

                    val cartList = mutableListOf<com.example.ui.screens.CartItem>()
                    val stockStr = prefs.getString("list_stock", "") ?: ""
                    val currentStocks = com.example.ui.screens.deserializeStock(stockStr).toMutableList()

                    for (i in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(i)
                        val prodName = itemObj.optString("namaBarang", "")
                        val qty = itemObj.optInt("qty", 1)
                        val harga = itemObj.optDouble("harga", 0.0)
                        val kat = itemObj.optString("kategori", "Umum")
                        cartList.add(com.example.ui.screens.CartItem(namaBarang = prodName, kategori = kat, qty = qty, harga = harga))

                        val stockIndex = currentStocks.indexOfFirst { it.nama.equals(prodName, ignoreCase = true) }
                        if (stockIndex != -1) {
                            val s = currentStocks[stockIndex]
                            val newQty = (s.stok - qty).coerceAtLeast(0)
                            currentStocks[stockIndex] = s.copy(stok = newQty)
                        }
                    }

                    val newTx = com.example.ui.screens.CashierTransaction(
                        items = cartList,
                        waktu = waktu,
                        tunai = tunai,
                        taxEnabled = taxEnabled,
                        taxRate = taxRate
                    )

                    val cashierStr = prefs.getString("list_cashier", "") ?: ""
                    val currentCashier = com.example.ui.screens.deserializeCashier(cashierStr).toMutableList()
                    currentCashier.add(0, newTx)

                    prefs.edit()
                        .putString("list_stock", com.example.ui.screens.serializeStock(currentStocks))
                        .putString("list_cashier", com.example.ui.screens.serializeCashier(currentCashier))
                        .apply()

                    val txJson = JSONObject().apply {
                        put("id", newTx.id)
                        put("waktu", newTx.waktu)
                        put("totalHarga", newTx.totalHarga)
                        put("tunai", newTx.tunai)
                        put("taxEnabled", newTx.taxEnabled)
                        put("taxRate", newTx.taxRate)
                        val outItems = JSONArray()
                        newTx.items.forEach { ci ->
                            outItems.put(JSONObject().apply {
                                put("namaBarang", ci.namaBarang)
                                put("kategori", ci.kategori)
                                put("qty", ci.qty)
                                put("harga", ci.harga)
                                put("totalHarga", ci.totalHarga)
                            })
                        }
                        put("items", outItems)
                        put("namaUsaha", namaUsaha)
                    }

                    sendJsonResponse(output, JSONObject().apply {
                        put("success", true)
                        put("transaction", txJson)
                    }.toString())
                } else {
                    val id = json.optString("id", java.util.UUID.randomUUID().toString())
                    val nama = json.optString("nama", "Produk Baru")
                    val kategori = json.optString("kategori", "Umum")
                    val keterangan = json.optString("keterangan", "")
                    val harga = json.optDouble("harga", 0.0)
                    val stok = json.optInt("stok", 0)
                    val barcode = json.optString("barcode", "")

                    val stockStr = prefs.getString("list_stock", "") ?: ""
                    val currentStocks = com.example.ui.screens.deserializeStock(stockStr).toMutableList()
                    val existingIndex = currentStocks.indexOfFirst { it.id == id || (id.isNotEmpty() && it.id == id) }

                    val newProduct = com.example.ui.screens.StockItem(
                        id = if (existingIndex != -1) currentStocks[existingIndex].id else id,
                        nama = nama,
                        kategori = kategori,
                        keterangan = keterangan,
                        harga = harga,
                        stok = stok,
                        barcode = barcode,
                        tanggal = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    )

                    if (existingIndex != -1) {
                        currentStocks[existingIndex] = newProduct
                    } else {
                        currentStocks.add(0, newProduct)
                    }

                    prefs.edit().putString("list_stock", com.example.ui.screens.serializeStock(currentStocks)).apply()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                }
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                if (idStr != null) {
                    val stockStr = prefs.getString("list_stock", "") ?: ""
                    val currentStocks = com.example.ui.screens.deserializeStock(stockStr).toMutableList()
                    currentStocks.removeAll { it.id == idStr }
                    prefs.edit().putString("list_stock", com.example.ui.screens.serializeStock(currentStocks)).apply()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                } else {
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "ID tidak ditemukan").toString(), 400)
                }
            }
        }
    }

    private suspend fun handleSavingsRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val target = repository.activeSavingTarget.first()
                val list = repository.allSavingTransactions.first()
                val jsonArr = JSONArray()
                list.forEach { item ->
                    jsonArr.put(JSONObject().apply {
                        put("id", item.id)
                        put("jenis", item.jenis)
                        put("kategori", item.kategori)
                        put("deskripsi", item.deskripsi)
                        put("nominal", item.nominal)
                        put("tanggal", item.tanggal)
                    })
                }

                val responseObj = JSONObject().apply {
                    put("success", true)
                    put("target", JSONObject().apply {
                        put("id", target?.id ?: 1)
                        put("nama", target?.nama ?: "Tabungan Utama")
                        put("jumlah", target?.jumlah ?: 0.0)
                    })
                    put("transactions", jsonArr)
                }
                sendJsonResponse(output, responseObj.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val action = json.optString("action", "")
                if (action == "set_target" || json.has("targetNama") || json.has("targetJumlah")) {
                    val targetNama = json.optString("targetNama", "Tabungan Utama").trim()
                    val targetJumlah = json.optDouble("targetJumlah", json.optDouble("jumlah", 0.0))
                    val curTarget = repository.activeSavingTarget.first()
                    val newTarget = SavingTarget(
                        id = curTarget?.id ?: 1,
                        nama = if (targetNama.isEmpty()) "Tabungan Utama" else targetNama,
                        jumlah = targetJumlah
                    )
                    repository.setSavingTarget(newTarget)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }

                val jenis = json.optString("jenis", "masuk")
                val kategori = json.optString("kategori", "Umum")
                val deskripsi = json.optString("deskripsi", "")
                val nominal = json.optDouble("nominal", 0.0)
                val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
                val newTrx = SavingTransaction(
                    jenis = jenis,
                    kategori = kategori,
                    deskripsi = deskripsi,
                    nominal = nominal,
                    tanggal = tanggal
                )
                repository.insertSavingTransaction(newTrx)
                sendJsonResponse(output, JSONObject().put("success", true).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val action = json.optString("action", "")
                if (action == "set_target" || json.has("targetNama")) {
                    val targetNama = json.optString("targetNama", "Tabungan Utama").trim()
                    val targetJumlah = json.optDouble("targetJumlah", json.optDouble("jumlah", 0.0))
                    val curTarget = repository.activeSavingTarget.first()
                    val newTarget = SavingTarget(
                        id = curTarget?.id ?: 1,
                        nama = if (targetNama.isEmpty()) "Tabungan Utama" else targetNama,
                        jumlah = targetJumlah
                    )
                    repository.setSavingTarget(newTarget)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }

                val id = json.optInt("id", -1)
                if (id != -1) {
                    val jenis = json.optString("jenis", "masuk")
                    val kategori = json.optString("kategori", "Umum")
                    val deskripsi = json.optString("deskripsi", "")
                    val nominal = json.optDouble("nominal", 0.0)
                    val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))
                    val updatedTrx = SavingTransaction(
                        id = id,
                        jenis = jenis,
                        kategori = kategori,
                        deskripsi = deskripsi,
                        nominal = nominal,
                        tanggal = tanggal
                    )
                    repository.updateSavingTransaction(updatedTrx)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }
                sendJsonResponse(output, JSONObject().put("success", false).toString(), 400)
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: if (body.isNotEmpty()) JSONObject(body).optString("id") else null
                val id = idStr?.toIntOrNull()
                if (id != null) {
                    val list = repository.allSavingTransactions.first()
                    val target = list.find { it.id == id }
                    if (target != null) {
                        repository.deleteSavingTransaction(target)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
                sendJsonResponse(output, JSONObject().put("success", false).toString(), 400)
            }
        }
    }

    private suspend fun handleDashboardRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)

        if (method == "POST") {
            try {
                val json = JSONObject(body)
                if (json.has("activeTimeZone")) {
                    val tz = json.getString("activeTimeZone")
                    mainPrefs.edit().putString("active_timezone", tz).apply()
                    sendJsonResponse(output, JSONObject().put("success", true).put("activeTimeZone", tz).toString())
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val notes = repository.allNotes.first()
        val shopping = repository.allShoppingItems.first()
        val agenda = repository.allAgendas.first()
        val jadwal = repository.allJadwal.first()
        val savings = repository.allSavingTransactions.first()

        val profileName = mainPrefs.getString("profile_name", "Singghara User") ?: "Singghara User"
        val profileAvatarIndex = mainPrefs.getInt("profile_avatar_index", 0)
        val profileImageUri = mainPrefs.getString("profile_image_uri", "") ?: ""
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        val hasCustomImage = profileAvatarIndex == -1 && (profileImageUri.isNotEmpty() || (localFile.exists() && localFile.length() > 0))
        val activeTimeZone = mainPrefs.getString("active_timezone", "WIB") ?: "WIB"

        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
        val stockStr = prefs.getString("list_stock", "") ?: ""
        val cashierStr = prefs.getString("list_cashier", "") ?: ""
        val stocks = com.example.ui.screens.deserializeStock(stockStr)
        val cashiers = com.example.ui.screens.deserializeCashier(cashierStr)

        val totalRevenue = cashiers.sumOf { it.totalHarga }
        val savingsBalance = savings.fold(0.0) { acc, item ->
            if (item.jenis == "masuk") acc + item.nominal else acc - item.nominal
        }
        val unpurchasedShopping = shopping.count { it.qty > 0 }

        val recentNotesArr = JSONArray()
        notes.take(5).forEach { note ->
            recentNotesArr.put(JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("category", note.category)
                put("content", if (note.content.length > 60) note.content.take(60) + "..." else note.content)
            })
        }

        val allAgendasArr = JSONArray()
        agenda.forEach { a ->
            allAgendasArr.put(JSONObject().apply {
                put("id", a.id)
                put("judul", a.judul)
                put("tanggal", a.tanggal)
                put("detail", a.detail)
                put("tipe", a.tipe)
            })
        }

        val recentAgendaArr = JSONArray()
        agenda.take(5).forEach { a ->
            recentAgendaArr.put(JSONObject().apply {
                put("id", a.id)
                put("judul", a.judul)
                put("tanggal", a.tanggal)
                put("tipe", a.tipe)
            })
        }

        val recentCashierArr = JSONArray()
        cashiers.take(5).forEach { c ->
            recentCashierArr.put(JSONObject().apply {
                put("id", c.id)
                put("waktu", c.waktu)
                put("totalHarga", c.totalHarga)
                put("itemsCount", c.items.size)
            })
        }

        val resObj = JSONObject().apply {
            put("notesCount", notes.size)
            put("shoppingCount", shopping.size)
            put("agendaCount", agenda.size)
            put("jadwalCount", jadwal.size)
            put("savingsCount", savings.size)
            put("productsCount", stocks.size)
            put("salesCount", cashiers.size)
            put("totalRevenue", totalRevenue)
            put("savingsBalance", savingsBalance)
            put("unpurchasedShopping", unpurchasedShopping)
            put("appName", "Singghara Suite")
            put("profileName", profileName)
            put("profileAvatarIndex", profileAvatarIndex)
            put("hasCustomImage", hasCustomImage)
            put("profileImageUrl", if (hasCustomImage) "/api/profile/image?t=${System.currentTimeMillis()}" else "")
            put("activeTimeZone", activeTimeZone)
            put("deviceIp", getIpAddress(context))
            put("status", "Aktif & Terhubung")
            put("recentNotes", recentNotesArr)
            put("recentAgenda", recentAgendaArr)
            put("allAgendas", allAgendasArr)
            put("recentCashier", recentCashierArr)
        }
        sendJsonResponse(output, resObj.toString())
    }

    private fun handlePembukuanRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)

        when (method) {
            "GET" -> {
                val namaUsaha = prefs.getString("nama_usaha", "Usaha Saya") ?: "Usaha Saya"
                val modalUsaha = prefs.getFloat("modal_usaha", 0f).toDouble()
                val kebutuhan = com.example.ui.screens.deserializeKebutuhan(prefs.getString("list_kebutuhan", "") ?: "")
                val pendapatan = com.example.ui.screens.deserializePendapatan(prefs.getString("list_pendapatan", "") ?: "")
                val operasional = com.example.ui.screens.deserializeOperasional(prefs.getString("list_operasional", "") ?: "")

                val modalArr = JSONArray()
                kebutuhan.forEachIndexed { index, item ->
                    modalArr.put(JSONObject().apply {
                        put("id", index)
                        put("nama", item.nama)
                        put("qty", item.qty)
                        put("harga", item.hargaSatuan)
                        put("subtotal", item.totalHarga)
                    })
                }

                val incomeArr = JSONArray()
                pendapatan.forEachIndexed { index, item ->
                    incomeArr.put(JSONObject().apply {
                        put("id", index)
                        put("nama", item.namaSewa)
                        put("tarif", item.hargaSewa)
                        put("qty", item.qty)
                        put("jam", item.durasiHari)
                        put("daily", item.totalPendapatanPerHari)
                        put("monthly", item.totalPendapatanPerBulan)
                    })
                }

                val expenseArr = JSONArray()
                operasional.forEachIndexed { index, item ->
                    expenseArr.put(JSONObject().apply {
                        put("id", index)
                        put("nama", item.jenis)
                        put("qty", item.qty)
                        put("harga", item.harga)
                        put("jumlahHari", item.jumlahHari)
                        put("biaya", item.totalPengeluaran)
                    })
                }

                val json = JSONObject().apply {
                    put("namaUsaha", namaUsaha)
                    put("modalUsaha", modalUsaha)
                    put("modalItems", modalArr)
                    put("incomeItems", incomeArr)
                    put("expenseItems", expenseArr)
                }
                sendJsonResponse(output, json.toString())
            }
            "POST" -> {
                try {
                    val json = JSONObject(body)
                    val action = json.optString("action", "")

                    val currentKebutuhan = com.example.ui.screens.deserializeKebutuhan(prefs.getString("list_kebutuhan", "") ?: "").toMutableList()
                    val currentPendapatan = com.example.ui.screens.deserializePendapatan(prefs.getString("list_pendapatan", "") ?: "").toMutableList()
                    val currentOperasional = com.example.ui.screens.deserializeOperasional(prefs.getString("list_operasional", "") ?: "").toMutableList()

                    when (action) {
                        "save_profile" -> {
                            val nama = json.optString("namaUsaha", "Usaha Saya")
                            val modal = json.optDouble("modalUsaha", 0.0).toFloat()
                            prefs.edit().putString("nama_usaha", nama).putFloat("modal_usaha", modal).apply()
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "add_modal" -> {
                            val nama = json.optString("nama", "")
                            val qty = json.optInt("qty", 1)
                            val harga = json.optDouble("harga", 0.0)
                            currentKebutuhan.add(com.example.ui.screens.KebutuhanBarang(nama, qty, harga))
                            prefs.edit().putString("list_kebutuhan", com.example.ui.screens.serializeKebutuhan(currentKebutuhan)).apply()
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "delete_modal" -> {
                            val index = json.optInt("index", -1)
                            if (index in 0 until currentKebutuhan.size) {
                                currentKebutuhan.removeAt(index)
                                prefs.edit().putString("list_kebutuhan", com.example.ui.screens.serializeKebutuhan(currentKebutuhan)).apply()
                            }
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "add_income" -> {
                            val nama = json.optString("nama", "")
                            val tarif = json.optDouble("tarif", 0.0)
                            val qty = json.optInt("qty", 1)
                            val jam = json.optInt("jam", 13)
                            currentPendapatan.add(com.example.ui.screens.EstimasiPendapatan(nama, qty, tarif, jam))
                            prefs.edit().putString("list_pendapatan", com.example.ui.screens.serializePendapatan(currentPendapatan)).apply()
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "delete_income" -> {
                            val index = json.optInt("index", -1)
                            if (index in 0 until currentPendapatan.size) {
                                currentPendapatan.removeAt(index)
                                prefs.edit().putString("list_pendapatan", com.example.ui.screens.serializePendapatan(currentPendapatan)).apply()
                            }
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "add_expense" -> {
                            val nama = json.optString("nama", "")
                            val biaya = json.optDouble("biaya", 0.0)
                            currentOperasional.add(com.example.ui.screens.PengeluaranOperasional(nama, 1, biaya, 1))
                            prefs.edit().putString("list_operasional", com.example.ui.screens.serializeOperasional(currentOperasional)).apply()
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        "delete_expense" -> {
                            val index = json.optInt("index", -1)
                            if (index in 0 until currentOperasional.size) {
                                currentOperasional.removeAt(index)
                                prefs.edit().putString("list_operasional", com.example.ui.screens.serializeOperasional(currentOperasional)).apply()
                            }
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                    }
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "Unknown action").toString(), 400)
                } catch (e: Exception) {
                    sendJsonResponse(output, JSONObject().put("success", false).put("error", e.message).toString(), 500)
                }
            }
        }
    }

    private suspend fun handleShareRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val sharedPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val defaultUrl = "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec"
        val appsScriptUrl = sharedPrefs.getString("apps_script_url", defaultUrl) ?: defaultUrl

        if (method == "POST") {
            try {
                val json = JSONObject(body)
                val action = json.optString("action", "")
                when (action) {
                    "save_url" -> {
                        val url = json.optString("url", defaultUrl)
                        sharedPrefs.edit().putString("apps_script_url", url).apply()
                        sendJsonResponse(output, JSONObject().put("status", "success").put("message", "URL disimpan").toString())
                        return
                    }
                    "upload" -> {
                        val filename = json.optString("filename", "file_${System.currentTimeMillis()}")
                        val mimeType = json.optString("mimeType", "application/octet-stream")
                        val base64Data = json.optString("base64Data", "")
                        
                        val uploadPayload = JSONObject().apply {
                            put("action", "upload")
                            put("filename", filename)
                            put("mimeType", mimeType)
                            put("base64Data", base64Data)
                        }
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val reqBody = uploadPayload.toString().toRequestBody(mediaType)
                        val req = Request.Builder().url(appsScriptUrl.trim()).post(reqBody).build()
                        val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
                        val respStr = resp.body?.string() ?: "{}"
                        sendJsonResponse(output, respStr)
                        return
                    }
                    "delete" -> {
                        val fileId = json.optString("fileId", "")
                        val delPayload = JSONObject().apply {
                            put("action", "delete")
                            put("fileId", fileId)
                        }
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val reqBody = delPayload.toString().toRequestBody(mediaType)
                        val req = Request.Builder().url(appsScriptUrl.trim()).post(reqBody).build()
                        val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
                        val respStr = resp.body?.string() ?: "{}"
                        sendJsonResponse(output, respStr)
                        return
                    }
                    "list" -> {
                        val req = Request.Builder().url("${appsScriptUrl.trim()}?action=list").get().build()
                        val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
                        val respStr = resp.body?.string() ?: "{}"
                        sendJsonResponse(output, respStr)
                        return
                    }
                }
            } catch (e: Exception) {
                sendJsonResponse(output, JSONObject().put("status", "error").put("message", e.message ?: "Server error").toString())
                return
            }
        }

        val actionParam = queryParams["action"]
        if (actionParam == "list") {
            try {
                val req = Request.Builder().url("${appsScriptUrl.trim()}?action=list").get().build()
                val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
                val respStr = resp.body?.string() ?: "{}"
                sendJsonResponse(output, respStr)
                return
            } catch (e: Exception) {
                sendJsonResponse(output, JSONObject().put("status", "error").put("message", e.message ?: "Gagal mengambil daftar file").toString())
                return
            }
        } else if (actionParam == "download_all") {
            try {
                val req = Request.Builder().url("${appsScriptUrl.trim()}?action=download_all").get().build()
                val resp = withContext(Dispatchers.IO) { httpClient.newCall(req).execute() }
                val respStr = resp.body?.string() ?: "{}"
                sendJsonResponse(output, respStr)
                return
            } catch (e: Exception) {
                sendJsonResponse(output, JSONObject().put("status", "error").put("message", e.message ?: "Gagal download zip").toString())
                return
            }
        }

        if (queryParams["export"] == "full_json") {
            val notes = repository.allNotes.first()
            val shopping = repository.allShoppingItems.first()
            val agenda = repository.allAgendas.first()
            val jadwal = repository.allJadwal.first()
            val savings = repository.allSavingTransactions.first()

            val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
            val stockStr = prefs.getString("list_stock", "") ?: ""
            val cashierStr = prefs.getString("list_cashier", "") ?: ""
            val stocks = com.example.ui.screens.deserializeStock(stockStr)
            val cashiers = com.example.ui.screens.deserializeCashier(cashierStr)
            val totalRevenue = cashiers.sumOf { it.totalHarga }

            val notesArr = JSONArray()
            notes.forEach { n ->
                notesArr.put(JSONObject().apply {
                    put("id", n.id); put("title", n.title); put("content", n.content); put("category", n.category)
                })
            }
            val shoppingArr = JSONArray()
            shopping.forEach { s ->
                shoppingArr.put(JSONObject().apply {
                    put("id", s.id); put("nama", s.nama); put("jenis", s.jenis); put("hargaSatuan", s.hargaSatuan); put("qty", s.qty); put("total", s.total)
                })
            }
            val agendaArr = JSONArray()
            agenda.forEach { a ->
                agendaArr.put(JSONObject().apply {
                    put("id", a.id); put("judul", a.judul); put("tanggal", a.tanggal); put("detail", a.detail); put("tipe", a.tipe)
                })
            }
            val jadwalArr = JSONArray()
            jadwal.forEach { j ->
                jadwalArr.put(JSONObject().apply {
                    put("id", j.id); put("waktu", j.waktu); put("kegiatan", j.kegiatan); put("kategori", j.kategori); put("isCompleted", j.isCompleted)
                })
            }
            val savingsArr = JSONArray()
            savings.forEach { sv ->
                savingsArr.put(JSONObject().apply {
                    put("id", sv.id); put("jenis", sv.jenis); put("kategori", sv.kategori); put("nominal", sv.nominal); put("tanggal", sv.tanggal)
                })
            }

            val fullData = JSONObject().apply {
                put("notes", notesArr)
                put("shopping", shoppingArr)
                put("agenda", agendaArr)
                put("jadwal", jadwalArr)
                put("savings", savingsArr)
                put("totalRevenue", totalRevenue)
                put("stocksCount", stocks.size)
                put("cashiersCount", cashiers.size)
                put("exportTime", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            }
            sendJsonResponse(output, fullData.toString())
            return
        }

        val notes = repository.allNotes.first()
        val shopping = repository.allShoppingItems.first()
        val agenda = repository.allAgendas.first()
        val jadwal = repository.allJadwal.first()
        val savings = repository.allSavingTransactions.first()

        val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
        val stockStr = prefs.getString("list_stock", "") ?: ""
        val cashierStr = prefs.getString("list_cashier", "") ?: ""
        val stocks = com.example.ui.screens.deserializeStock(stockStr)
        val cashiers = com.example.ui.screens.deserializeCashier(cashierStr)
        val totalRevenue = cashiers.sumOf { it.totalHarga }

        val resObj = JSONObject().apply {
            put("appsScriptUrl", appsScriptUrl)
            put("notesCount", notes.size)
            put("shoppingCount", shopping.size)
            put("agendaCount", agenda.size)
            put("jadwalCount", jadwal.size)
            put("savingsCount", savings.size)
            put("productsCount", stocks.size)
            put("salesCount", cashiers.size)
            put("totalRevenue", totalRevenue)
            put("appName", "Singghara Suite")
            put("deviceIp", getIpAddress(context))
            put("status", "Aktif & Terhubung")
        }
        sendJsonResponse(output, resObj.toString())
    }

    private fun handleQuranRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val surahIdStr = queryParams["surahId"] ?: queryParams["id"]
        if (surahIdStr != null) {
            val surahId = surahIdStr.toIntOrNull() ?: 1
            val verses = com.example.data.QuranData.getVersesForSurah(surahId)
            val surahMeta = com.example.data.QuranData.surahs.firstOrNull { it.id == surahId }
            val resObj = JSONObject().apply {
                if (surahMeta != null) {
                    put("id", surahMeta.id)
                    put("nameArabic", surahMeta.nameArabic)
                    put("nameLatin", surahMeta.nameIndonesian)
                    put("type", surahMeta.revelationType)
                    put("verseCount", surahMeta.verseCount)
                    put("translation", surahMeta.translationName)
                }
                val vArr = JSONArray()
                verses.forEach { v ->
                    vArr.put(JSONObject().apply {
                        put("surahId", v.surahId)
                        put("verseNumber", v.verseNumber)
                        put("textArabic", v.textArabic)
                        put("translation", v.translation)
                    })
                }
                put("verses", vArr)
            }
            sendJsonResponse(output, resObj.toString())
        } else {
            val surahsArr = JSONArray()
            com.example.data.QuranData.surahs.forEach { s ->
                surahsArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("nameArabic", s.nameArabic)
                    put("nameLatin", s.nameIndonesian)
                    put("type", s.revelationType)
                    put("verseCount", s.verseCount)
                    put("translation", s.translationName)
                })
            }
            sendJsonResponse(output, surahsArr.toString())
        }
    }

    private suspend fun handleKeuanganRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        when {
            path.startsWith("/api/keuangan/jurnal") -> {
                when (method) {
                    "GET" -> {
                        val serialized = prefs.getString("journal_entries_serialized", "") ?: ""
                        val entries = com.example.deserializeJournalEntries(serialized)
                        val arr = JSONArray()
                        entries.forEach { e ->
                            arr.put(JSONObject().apply {
                                put("tanggal", e.tanggal)
                                put("keterangan", e.keterangan)
                                put("debetAkun", e.debetAkun)
                                put("kreditAkun", e.kreditAkun)
                                put("nominal", e.nominal)
                            })
                        }
                        sendJsonResponse(output, arr.toString())
                    }
                    "POST" -> {
                        val json = JSONObject(body)
                        val newEntry = com.example.JournalEntry(
                            tanggal = json.optString("tanggal", "2026-08-15"),
                            keterangan = json.optString("keterangan", ""),
                            debetAkun = json.optString("debetAkun", "Kas"),
                            kreditAkun = json.optString("kreditAkun", "Pendapatan jasa"),
                            nominal = json.optDouble("nominal", 0.0)
                        )
                        val serialized = prefs.getString("journal_entries_serialized", "") ?: ""
                        val list = com.example.deserializeJournalEntries(serialized).toMutableList()
                        list.add(newEntry)
                        val updatedStr = com.example.serializeJournalEntries(list)
                        prefs.edit().putString("journal_entries_serialized", updatedStr).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    }
                    "DELETE" -> {
                        val index = queryParams["index"]?.toIntOrNull()
                        val serialized = prefs.getString("journal_entries_serialized", "") ?: ""
                        val list = com.example.deserializeJournalEntries(serialized).toMutableList()
                        if (index != null && index in 0 until list.size) {
                            list.removeAt(index)
                            val updatedStr = com.example.serializeJournalEntries(list)
                            prefs.edit().putString("journal_entries_serialized", updatedStr).apply()
                        }
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    }
                    else -> sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed")
                }
            }
            path.startsWith("/api/keuangan/penyesuaian") -> {
                when (method) {
                    "GET" -> {
                        val serialized = prefs.getString("adjustment_entries_serialized", "") ?: ""
                        val entries = com.example.deserializeAdjustmentEntries(serialized)
                        val arr = JSONArray()
                        entries.forEach { e ->
                            arr.put(JSONObject().apply {
                                put("tanggal", e.tanggal)
                                put("jenisPenyesuaian", e.jenisPenyesuaian)
                                put("debetAkun", e.debetAkun)
                                put("kreditAkun", e.kreditAkun)
                                put("keterangan", e.keterangan)
                                put("nominal", e.nominal)
                            })
                        }
                        sendJsonResponse(output, arr.toString())
                    }
                    "POST" -> {
                        val json = JSONObject(body)
                        val newEntry = com.example.AdjustmentEntry(
                            tanggal = json.optString("tanggal", "2026-08-15"),
                            jenisPenyesuaian = json.optString("jenisPenyesuaian", "Umum"),
                            debetAkun = json.optString("debetAkun", "Beban perlengkapan"),
                            kreditAkun = json.optString("kreditAkun", "Perlengkapan"),
                            keterangan = json.optString("keterangan", ""),
                            nominal = json.optDouble("nominal", 0.0)
                        )
                        val serialized = prefs.getString("adjustment_entries_serialized", "") ?: ""
                        val list = com.example.deserializeAdjustmentEntries(serialized).toMutableList()
                        list.add(newEntry)
                        val updatedStr = com.example.serializeAdjustmentEntries(list)
                        prefs.edit().putString("adjustment_entries_serialized", updatedStr).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    }
                    "DELETE" -> {
                        val index = queryParams["index"]?.toIntOrNull()
                        val serialized = prefs.getString("adjustment_entries_serialized", "") ?: ""
                        val list = com.example.deserializeAdjustmentEntries(serialized).toMutableList()
                        if (index != null && index in 0 until list.size) {
                            list.removeAt(index)
                            val updatedStr = com.example.serializeAdjustmentEntries(list)
                            prefs.edit().putString("adjustment_entries_serialized", updatedStr).apply()
                        }
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    }
                    else -> sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed")
                }
            }
            else -> sendResponse(output, 404, "Not Found", "text/plain", "404 Not Found")
        }
    }

    private fun handleProfileRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        if (path.startsWith("/api/profile/image") || path.startsWith("/api/profile/avatar")) {
            handleProfileImageRoute(output)
            return
        }
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val profileName = mainPrefs.getString("profile_name", "Singghara User") ?: "Singghara User"
        val profileAvatarIndex = mainPrefs.getInt("profile_avatar_index", 0)
        val profileImageUri = mainPrefs.getString("profile_image_uri", "") ?: ""
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        val hasCustomImage = profileAvatarIndex == -1 && (profileImageUri.isNotEmpty() || (localFile.exists() && localFile.length() > 0))

        val res = JSONObject().apply {
            put("profileName", profileName)
            put("profileAvatarIndex", profileAvatarIndex)
            put("hasCustomImage", hasCustomImage)
            put("profileImageUrl", if (hasCustomImage) "/api/profile/image?t=${System.currentTimeMillis()}" else "")
        }
        sendJsonResponse(output, res.toString())
    }

    private fun resolveFotoForWeb(fotoUri: String): String {
        if (fotoUri.isBlank()) return ""
        if (fotoUri.startsWith("data:image")) return fotoUri
        if (fotoUri.startsWith("http://") || fotoUri.startsWith("https://")) return fotoUri
        return try {
            val bitmap = if (fotoUri.startsWith("/")) {
                android.graphics.BitmapFactory.decodeFile(fotoUri)
            } else {
                val uri = android.net.Uri.parse(fotoUri)
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                stream?.close()
                bmp
            }
            if (bitmap != null) {
                val maxDim = 400
                val scale = (maxDim.toFloat() / Math.max(bitmap.width, bitmap.height)).coerceAtMost(1f)
                val scaledBmp = if (scale < 1f) {
                    android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap
                val baos = java.io.ByteArrayOutputStream()
                scaledBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else {
                fotoUri
            }
        } catch (e: Exception) {
            fotoUri
        }
    }

    private suspend fun handleAnggotaRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val anggotaList = repository.allAnggota.first()
                val jsonArr = JSONArray()
                anggotaList.forEach { item ->
                    jsonArr.put(JSONObject().apply {
                        put("id", item.id)
                        put("nomorInduk", item.nomorInduk)
                        put("nama", item.nama)
                        put("status", item.status)
                        put("divisiOrJurusan", item.divisiOrJurusan)
                        put("emailOrTelp", item.emailOrTelp)
                        put("fotoUri", resolveFotoForWeb(item.fotoUri))
                        put("qrCodeData", item.qrCodeData)
                        put("instansi", item.instansi)
                        put("tanggalDibuat", item.tanggalDibuat)
                        put("berlakuHingga", item.berlakuHingga)
                        put("kontak", item.kontak)
                        put("email", item.email)
                        put("catatanQr", item.catatanQr)
                        put("jenisKelamin", item.jenisKelamin)
                        put("alamat", item.alamat)
                        put("catatan", item.catatan)
                        put("tandaTanganUri", item.tandaTanganUri)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = JSONObject(body)
                val nomorInduk = json.optString("nomorInduk", "").trim()
                val nama = json.optString("nama", "").trim()
                val status = json.optString("status", "Karyawan")
                val divisi = json.optString("divisiOrJurusan", "").trim()
                val instansi = json.optString("instansi", "My Singghara").trim()
                val kontak = json.optString("kontak", "").trim()
                val email = json.optString("email", "").trim()
                val fotoUri = json.optString("fotoUri", "").trim()
                val tandaTanganUri = json.optString("tandaTanganUri", "").trim()
                val jenisKelamin = json.optString("jenisKelamin", "Laki-laki")
                val alamat = json.optString("alamat", "").trim()
                val catatan = json.optString("catatan", "").trim()
                val catatanQr = json.optString("catatanQr", "").trim()
                val berlakuHingga = json.optString("berlakuHingga", "Berlaku Selama Aktif").trim()
                val qrData = if (json.has("qrCodeData") && json.optString("qrCodeData").isNotEmpty()) {
                    json.optString("qrCodeData")
                } else {
                    "SINGGHARA_MEMBER:${if (nomorInduk.isNotEmpty()) nomorInduk else System.currentTimeMillis().toString()}:$nama:$status"
                }

                val item = AnggotaItem(
                    nomorInduk = if (nomorInduk.isNotEmpty()) nomorInduk else "ID-${System.currentTimeMillis().toString().takeLast(6)}",
                    nama = if (nama.isNotEmpty()) nama else "Anggota Baru",
                    status = status,
                    divisiOrJurusan = divisi,
                    emailOrTelp = if (kontak.isNotEmpty()) kontak else email,
                    fotoUri = fotoUri,
                    qrCodeData = qrData,
                    instansi = if (instansi.isNotEmpty()) instansi else "My Singghara",
                    tanggalDibuat = System.currentTimeMillis(),
                    berlakuHingga = if (berlakuHingga.isNotEmpty()) berlakuHingga else "Berlaku Selama Aktif",
                    kontak = kontak,
                    email = email,
                    catatanQr = catatanQr,
                    jenisKelamin = jenisKelamin,
                    alamat = alamat,
                    catatan = catatan,
                    tandaTanganUri = tandaTanganUri
                )
                val newId = repository.insertAnggota(item)
                sendJsonResponse(output, JSONObject().put("success", true).put("id", newId).toString())
            }
            "PUT" -> {
                val json = JSONObject(body)
                val id = json.getInt("id")
                val existing = repository.allAnggota.first().find { it.id == id }
                if (existing != null) {
                    val updated = existing.copy(
                        nomorInduk = json.optString("nomorInduk", existing.nomorInduk),
                        nama = json.optString("nama", existing.nama),
                        status = json.optString("status", existing.status),
                        divisiOrJurusan = json.optString("divisiOrJurusan", existing.divisiOrJurusan),
                        fotoUri = json.optString("fotoUri", existing.fotoUri),
                        tandaTanganUri = json.optString("tandaTanganUri", existing.tandaTanganUri),
                        qrCodeData = json.optString("qrCodeData", existing.qrCodeData),
                        instansi = json.optString("instansi", existing.instansi),
                        berlakuHingga = json.optString("berlakuHingga", existing.berlakuHingga),
                        kontak = json.optString("kontak", existing.kontak),
                        email = json.optString("email", existing.email),
                        catatanQr = json.optString("catatanQr", existing.catatanQr),
                        jenisKelamin = json.optString("jenisKelamin", existing.jenisKelamin),
                        alamat = json.optString("alamat", existing.alamat),
                        catatan = json.optString("catatan", existing.catatan)
                    )
                    repository.updateAnggota(updated)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                } else {
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "Anggota not found").toString(), 404)
                }
            }
            "DELETE" -> {
                val id = queryParams["id"]?.toIntOrNull()
                if (id != null) {
                    val item = repository.allAnggota.first().find { it.id == id }
                    if (item != null) {
                        repository.deleteAnggota(item)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    } else {
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "Not found").toString(), 404)
                    }
                } else {
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "Invalid ID").toString(), 400)
                }
            }
        }
    }

    private suspend fun handleIdCardRoute(queryParams: Map<String, String>, output: OutputStream) {
        try {
            val id = queryParams["id"]?.toIntOrNull()
            val side = queryParams["side"] ?: "front"
            if (id == null) {
                sendResponse(output, 400, "Bad Request", "text/plain", "Missing id parameter")
                return
            }
            val anggota = repository.allAnggota.first().find { it.id == id }
            if (anggota == null) {
                sendResponse(output, 404, "Not Found", "text/plain", "Anggota not found")
                return
            }

            val isFront = (side.lowercase() != "back" && side != "1")
            val qrData = if (anggota.qrCodeData.isNotBlank()) {
                anggota.qrCodeData
            } else {
                "SINGGHARA_MEMBER:${anggota.nomorInduk}:${anggota.nama}:${anggota.status}"
            }
            val qrBitmap = try {
                com.example.utils.QrCodeHelper.generateQrBitmap(qrData, 450)
            } catch (e: Exception) {
                null
            }

            val cardBitmap = com.example.utils.IdCardBitmapGenerator.createVerticalIdCardBitmap(
                context = context,
                anggota = anggota,
                qrBitmap = qrBitmap,
                isFrontSide = isFront
            )

            val stream = java.io.ByteArrayOutputStream()
            cardBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            sendBinaryResponse(output, 200, "OK", "image/png", bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            sendResponse(output, 500, "Internal Server Error", "text/plain", "Error generating ID card: ${e.message}")
        }
    }

    private suspend fun handlePresensiRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val presensiList = repository.allPresensi.first()
                val jsonArr = JSONArray()
                presensiList.forEach { p ->
                    jsonArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("anggotaId", p.anggotaId)
                        put("nomorInduk", p.nomorInduk)
                        put("nama", p.nama)
                        put("status", p.status)
                        put("divisi", p.divisi)
                        put("statusPresensi", p.statusPresensi)
                        put("timestamp", p.timestamp)
                        put("waktuPulang", if (p.waktuPulang != null) p.waktuPulang else JSONObject.NULL)
                        put("batasWaktuMasuk", p.batasWaktuMasuk)
                        put("batasWaktuPulang", p.batasWaktuPulang)
                        put("statusPelanggaran", p.statusPelanggaran)
                        put("catatanPelanggaran", p.catatanPelanggaran)
                        put("metode", p.metode)
                        put("qrPayload", p.qrPayload)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = try { JSONObject(body) } catch(e: Exception) { JSONObject() }
                val action = json.optString("action", "")
                if (action == "clear_all") {
                    repository.deleteAllPresensi()
                    sendJsonResponse(output, JSONObject().put("success", true).put("message", "Semua riwayat presensi dibersihkan").toString())
                    return
                }
                if (action == "record_pulang" || action == "update_pulang") {
                    val id = json.optInt("id", 0)
                    val existing = repository.allPresensi.first().find { it.id == id }
                    if (existing != null) {
                        val waktuPulang = json.optLong("waktuPulang", System.currentTimeMillis())
                        val statusPelanggaran = json.optString("statusPelanggaran", existing.statusPelanggaran)
                        val catatanPelanggaran = json.optString("catatanPelanggaran", existing.catatanPelanggaran)
                        val updated = existing.copy(
                            waktuPulang = waktuPulang,
                            statusPelanggaran = statusPelanggaran,
                            catatanPelanggaran = catatanPelanggaran
                        )
                        repository.updatePresensi(updated)
                        sendJsonResponse(output, JSONObject().put("success", true).put("message", "Waktu pulang berhasil dicatat").toString())
                    } else {
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "Data presensi tidak ditemukan").toString(), 404)
                    }
                    return
                }

                val anggotaId = json.optLong("anggotaId", 0L)
                val nomorInduk = json.optString("nomorInduk", "")
                val nama = json.optString("nama", "")
                val status = json.optString("status", "Umum")
                val divisi = json.optString("divisi", "")
                val statusPresensi = json.optString("statusPresensi", "Hadir")
                val metode = json.optString("metode", "Web QR Scanner")
                val qrPayload = json.optString("qrPayload", "")
                val ts = if (json.has("timestamp") && json.optLong("timestamp", 0L) > 0L) json.optLong("timestamp") else System.currentTimeMillis()
                val wp = if (json.has("waktuPulang") && !json.isNull("waktuPulang")) json.optLong("waktuPulang") else null
                val batasMasuk = json.optString("batasWaktuMasuk", "08:00")
                val batasPulang = json.optString("batasWaktuPulang", "17:00")
                val statusPelanggaran = json.optString("statusPelanggaran", "Tepat Waktu")
                val catatanPelanggaran = json.optString("catatanPelanggaran", "")

                val presensi = RiwayatPresensi(
                    anggotaId = anggotaId,
                    nomorInduk = if (nomorInduk.isNotEmpty()) nomorInduk else "SCAN-WEB",
                    nama = if (nama.isNotEmpty()) nama else "Anggota / Tamu",
                    status = status,
                    divisi = divisi,
                    statusPresensi = statusPresensi,
                    timestamp = ts,
                    waktuPulang = wp,
                    batasWaktuMasuk = batasMasuk,
                    batasWaktuPulang = batasPulang,
                    statusPelanggaran = statusPelanggaran,
                    catatanPelanggaran = catatanPelanggaran,
                    metode = metode,
                    qrPayload = qrPayload
                )
                val newId = repository.insertPresensi(presensi)
                sendJsonResponse(output, JSONObject().put("success", true).put("id", newId).toString())
            }
            "PUT" -> {
                val json = try { JSONObject(body) } catch(e: Exception) { JSONObject() }
                val id = json.optInt("id", 0)
                val existing = repository.allPresensi.first().find { it.id == id }
                if (existing != null) {
                    val statusPresensi = json.optString("statusPresensi", existing.statusPresensi)
                    val waktuPulang = if (json.has("waktuPulang") && !json.isNull("waktuPulang")) json.optLong("waktuPulang") else existing.waktuPulang
                    val statusPelanggaran = json.optString("statusPelanggaran", existing.statusPelanggaran)
                    val catatanPelanggaran = json.optString("catatanPelanggaran", existing.catatanPelanggaran)
                    val updated = existing.copy(
                        statusPresensi = statusPresensi,
                        waktuPulang = waktuPulang,
                        statusPelanggaran = statusPelanggaran,
                        catatanPelanggaran = catatanPelanggaran
                    )
                    repository.updatePresensi(updated)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                } else {
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "Not found").toString(), 404)
                }
            }
            "DELETE" -> {
                val action = queryParams["action"] ?: ""
                if (action == "clear_all") {
                    repository.deleteAllPresensi()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }
                val id = queryParams["id"]?.toIntOrNull()
                if (id != null) {
                    val p = repository.allPresensi.first().find { it.id == id }
                    if (p != null) {
                        repository.deletePresensi(p)
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    } else {
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "Not found").toString(), 404)
                    }
                } else {
                    sendJsonResponse(output, JSONObject().put("success", false).put("message", "Invalid ID").toString(), 400)
                }
            }
        }
    }

    private fun handleProfileImageRoute(output: OutputStream) {
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        if (localFile.exists() && localFile.length() > 0) {
            try {
                val bytes = localFile.readBytes()
                sendBinaryResponse(output, 200, "OK", "image/jpeg", bytes)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val uriStr = mainPrefs.getString("profile_image_uri", "") ?: ""
        if (uriStr.isNotEmpty()) {
            try {
                val uri = android.net.Uri.parse(uriStr)
                val mimeType = if (uriStr.startsWith("file://")) "image/jpeg" else (context.contentResolver.getType(uri) ?: "image/jpeg")
                val inputStream = if (uri.scheme == "file") {
                    java.io.File(uri.path ?: "").inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                if (inputStream != null) {
                    val bytes = inputStream.use { it.readBytes() }
                    sendBinaryResponse(output, 200, "OK", mimeType, bytes)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        sendResponse(output, 404, "Not Found", "text/plain", "Profile image not found")
    }

    private fun sendBinaryResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, bytes: ByteArray) {
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCorsResponse(output: OutputStream) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n"
        output.write(headers.toByteArray())
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, json: String, code: Int = 200) {
        sendResponse(output, code, if (code == 200) "OK" else "Bad Request", "application/json", json)
    }

    private fun sendHtmlResponse(output: OutputStream, html: String) {
        sendResponse(output, 200, "OK", "text/html; charset=utf-8", html)
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, content: String) {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${contentBytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n" +
                "\r\n"
        output.write(response.toByteArray(Charsets.UTF_8))
        output.write(contentBytes)
        output.flush()
    }

    private fun getWebUiHtml(): String {
        val dol = "${'$'}"
        return """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Singghara Web - Dashboard Profesional</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.0/font/bootstrap-icons.css">
    <script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js"></script>
    <script src="https://unpkg.com/html5-qrcode"></script>
    <style>
        :root {
            --bg-sidebar: #f8fafc;
            --bg-navbar: #ffffff;
            --bg-hover: #e2e8f0;
            --text-color: #141414;
            --bg-main: #ffffff;
            --shadow-light: 0 2px 12px rgba(0, 0, 0, 0.1);
            --border-light: #cbd5e1;
            --primary-color: #3674b5;
            --primary-hover: #2c5e91;
            --danger-color: #e74c3c;

            --sidebar-width: 250px;
            --sidebar-collapsed-width: 72px;

            /* Component level variables mapping to palette */
            --brand-green: var(--primary-color);
            --brand-green-hover: var(--primary-hover);
            --navbar-bg: var(--bg-navbar);
            --sidebar-bg: var(--bg-sidebar);
            --sidebar-hover: var(--bg-hover);
            --card-bg: var(--bg-navbar);
            --border-color: var(--border-light);
            --text-main: var(--text-color);
            --text-sub: #475569;
            --input-bg: var(--bg-navbar);
            --input-color: var(--text-color);
            --modal-bg: var(--bg-navbar);
            --table-th-bg: #f1f5f9;
            --table-hover: var(--bg-hover);
            --badge-bg: rgba(54, 116, 181, 0.12);
            --btn-close-filter: none;
            --card-shadow: var(--shadow-light);
        }

        body.dark, [data-theme="dark"] {
            --bg-sidebar: #0f172a;
            --bg-navbar: #1e293b;
            --bg-hover: #334155;
            --text-color: #f8fafc;
            --bg-main: #0f172aef;
            --shadow-dark: 0 2px 10px rgba(0, 0, 0, 0.4);
            --border-dark: #334155;

            /* High contrast dark mode typography & surfaces */
            --brand-green: #38bdf8;
            --brand-green-hover: #60a5fa;
            --navbar-bg: var(--bg-navbar);
            --sidebar-bg: var(--bg-sidebar);
            --sidebar-hover: var(--bg-hover);
            --card-bg: var(--bg-navbar);
            --border-color: var(--border-dark);
            --text-main: #ffffff;
            --text-sub: #cbd5e1;
            --input-bg: #0f172a;
            --input-color: #ffffff;
            --modal-bg: var(--bg-navbar);
            --table-th-bg: rgba(15, 23, 42, 0.8);
            --table-hover: var(--bg-hover);
            --badge-bg: rgba(56, 189, 248, 0.2);
            --btn-close-filter: invert(1) grayscale(100%) brightness(200%);
            --card-shadow: var(--shadow-dark);
        }

        @media (prefers-color-scheme: dark) {
            :root:not([data-theme="light"]):not(.dark) {
                --bg-sidebar: #0f172a;
                --bg-navbar: #1e293b;
                --bg-hover: #334155;
                --text-color: #f8fafc;
                --bg-main: #0f172aef;
                --shadow-dark: 0 2px 10px rgba(0, 0, 0, 0.4);
                --border-dark: #334155;

                --brand-green: #38bdf8;
                --brand-green-hover: #60a5fa;
                --navbar-bg: var(--bg-navbar);
                --sidebar-bg: var(--bg-sidebar);
                --sidebar-hover: var(--bg-hover);
                --card-bg: var(--bg-navbar);
                --border-color: var(--border-dark);
                --text-main: #ffffff;
                --text-sub: #cbd5e1;
                --input-bg: #0f172a;
                --input-color: #ffffff;
                --modal-bg: var(--bg-navbar);
                --table-th-bg: rgba(15, 23, 42, 0.8);
                --table-hover: var(--bg-hover);
                --badge-bg: rgba(56, 189, 248, 0.2);
                --btn-close-filter: invert(1) grayscale(100%) brightness(200%);
                --card-shadow: var(--shadow-dark);
            }
        }

        /* Dark Mode Global Support for Bootstrap Components & Modals */
        body.dark .modal-content, [data-theme="dark"] .modal-content,
        :root:not([data-theme="light"]):not(.dark) .modal-content {
            background-color: var(--modal-bg) !important;
            color: var(--text-main) !important;
            border: 1px solid var(--border-color) !important;
        }

        body.dark .form-control, [data-theme="dark"] .form-control,
        body.dark .form-select, [data-theme="dark"] .form-select,
        :root:not([data-theme="light"]):not(.dark) .form-control,
        :root:not([data-theme="light"]):not(.dark) .form-select {
            background-color: var(--input-bg) !important;
            color: var(--text-main) !important;
            border: 1px solid var(--border-color) !important;
        }

        body.dark .form-control:focus, [data-theme="dark"] .form-control:focus,
        body.dark .form-select:focus, [data-theme="dark"] .form-select:focus,
        :root:not([data-theme="light"]):not(.dark) .form-control:focus,
        :root:not([data-theme="light"]):not(.dark) .form-select:focus {
            background-color: var(--input-bg) !important;
            color: var(--text-main) !important;
            border-color: #0F766E !important;
            box-shadow: 0 0 0 0.25rem rgba(15, 118, 110, 0.25) !important;
        }

        body.dark .form-control::placeholder, [data-theme="dark"] .form-control::placeholder,
        :root:not([data-theme="light"]):not(.dark) .form-control::placeholder {
            color: var(--text-sub) !important;
            opacity: 0.7;
        }

        body.dark .input-group-text, [data-theme="dark"] .input-group-text,
        :root:not([data-theme="light"]):not(.dark) .input-group-text {
            background-color: var(--sidebar-bg) !important;
            color: var(--text-sub) !important;
            border: 1px solid var(--border-color) !important;
        }

        body.dark .dropdown-menu, [data-theme="dark"] .dropdown-menu,
        :root:not([data-theme="light"]):not(.dark) .dropdown-menu {
            background-color: var(--card-bg) !important;
            color: var(--text-main) !important;
            border: 1px solid var(--border-color) !important;
            box-shadow: var(--card-shadow) !important;
        }

        body.dark .dropdown-item, [data-theme="dark"] .dropdown-item,
        :root:not([data-theme="light"]):not(.dark) .dropdown-item {
            color: var(--text-main) !important;
        }

        body.dark .dropdown-item:hover, [data-theme="dark"] .dropdown-item:hover,
        body.dark .dropdown-item:focus, [data-theme="dark"] .dropdown-item:focus,
        :root:not([data-theme="light"]):not(.dark) .dropdown-item:hover,
        :root:not([data-theme="light"]):not(.dark) .dropdown-item:focus {
            background-color: var(--bg-hover) !important;
            color: var(--text-main) !important;
        }

        body.dark .dropdown-divider, [data-theme="dark"] .dropdown-divider,
        :root:not([data-theme="light"]):not(.dark) .dropdown-divider {
            border-top-color: var(--border-color) !important;
        }

        body.dark .table, [data-theme="dark"] .table,
        :root:not([data-theme="light"]):not(.dark) .table {
            color: var(--text-main) !important;
            border-color: var(--border-color) !important;
            --bs-table-bg: transparent !important;
            --bs-table-color: var(--text-main) !important;
        }

        body.dark .table > :not(caption) > * > *, [data-theme="dark"] .table > :not(caption) > * > *,
        :root:not([data-theme="light"]):not(.dark) .table > :not(caption) > * > * {
            background-color: transparent !important;
            color: var(--text-main) !important;
            border-bottom-color: var(--border-color) !important;
        }

        body.dark .table-hover > tbody > tr:hover > *, [data-theme="dark"] .table-hover > tbody > tr:hover > *,
        :root:not([data-theme="light"]):not(.dark) .table-hover > tbody > tr:hover > * {
            background-color: var(--bg-hover) !important;
            color: var(--text-main) !important;
        }

        body.dark .bg-light, [data-theme="dark"] .bg-light,
        :root:not([data-theme="light"]):not(.dark) .bg-light {
            background-color: var(--sidebar-bg) !important;
            color: var(--text-main) !important;
        }

        body.dark .bg-white, [data-theme="dark"] .bg-white,
        :root:not([data-theme="light"]):not(.dark) .bg-white {
            background-color: var(--card-bg) !important;
            color: var(--text-main) !important;
        }

        body.dark .btn-close, body.dark .btn-close-custom,
        [data-theme="dark"] .btn-close, [data-theme="dark"] .btn-close-custom,
        :root:not([data-theme="light"]):not(.dark) .btn-close,
        :root:not([data-theme="light"]):not(.dark) .btn-close-custom {
            filter: invert(1) grayscale(100%) brightness(200%) !important;
        }

        body.dark .card-custom, [data-theme="dark"] .card-custom,
        :root:not([data-theme="light"]):not(.dark) .card-custom {
            background-color: var(--card-bg) !important;
            color: var(--text-main) !important;
            border-color: var(--border-color) !important;
        }

        body.dark .form-text, body.dark .text-muted,
        [data-theme="dark"] .form-text, [data-theme="dark"] .text-muted,
        :root:not([data-theme="light"]):not(.dark) .form-text,
        :root:not([data-theme="light"]):not(.dark) .text-muted {
            color: var(--text-sub) !important;
        }

        body {
            margin: 0;
            font-family: Arial, sans-serif;
            display: flex;
            flex-direction: column;
            background: var(--bg-main);
            color: var(--text-color);
            transition: background-color 0.3s, color 0.3s;
            overflow-x: hidden;
        }

        .navbar-main {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            height: 64px;
            background-color: var(--navbar-bg);
            border-bottom: 1px solid var(--border-color);
            z-index: 1030;
            transition: background-color 0.25s ease, border-color 0.25s ease;
        }

        /* Sidebar Styling & Responsive Transitions */
        .sidebar-wrapper {
            position: fixed;
            top: 64px;
            bottom: 0;
            left: 0;
            width: var(--sidebar-width);
            background-color: var(--sidebar-bg);
            border-right: 1px solid var(--border-color);
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            z-index: 1020;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            overflow: hidden;
            padding: 0;
        }

        .sidebar-menu-scrollable {
            flex: 1 1 auto;
            overflow-y: auto;
            overflow-x: hidden;
            padding: 16px 12px 8px 12px;
        }

        .sidebar-footer {
            flex-shrink: 0;
            padding: 12px 14px;
            border-top: 1px solid var(--border-color);
            background-color: var(--sidebar-bg);
            transition: all 0.25s ease;
        }

        .sidebar-user-section {
            width: 100%;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
        }

        .sidebar-profile-info {
            display: flex;
            align-items: center;
            gap: 10px;
            cursor: pointer;
            border-radius: 10px;
            padding: 6px 8px;
            transition: background-color 0.2s ease;
            flex: 1;
            min-width: 0;
            user-select: none;
        }

        .sidebar-profile-info:hover {
            background-color: var(--sidebar-hover);
        }

        .sidebar-avatar-icon {
            width: 38px;
            height: 38px;
            border-radius: 50%;
            background: linear-gradient(135deg, #2A84EB, #1456A5);
            color: #ffffff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.15rem;
            flex-shrink: 0;
            transition: all 0.2s ease;
            overflow: hidden;
            border: 2px solid rgba(255, 255, 255, 0.2);
            box-shadow: 0 2px 5px rgba(0,0,0,0.12);
        }

        .sidebar-avatar-icon img,
        .sidebar-avatar-img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 50%;
            display: block;
        }

        #profileModalAvatarContainer img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 50%;
            display: block;
        }

        body.dark .sidebar-avatar-icon,
        [data-theme="dark"] .sidebar-avatar-icon {
            border-color: rgba(255, 255, 255, 0.15);
        }

        .sidebar-user-meta {
            min-width: 0;
            flex: 1;
        }

        .sidebar-user-name {
            font-size: 0.85rem;
            color: var(--text-main);
            line-height: 1.2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .sidebar-user-status {
            font-size: 0.72rem;
            color: var(--text-sub);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .btn-sidebar-logout {
            width: 38px;
            height: 38px;
            border-radius: 10px;
            border: 1px solid var(--border-color);
            background-color: transparent;
            color: var(--text-sub);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 1.2rem;
            cursor: pointer;
            transition: all 0.2s ease;
            flex-shrink: 0;
            padding: 0;
        }

        .btn-sidebar-logout:hover {
            background-color: rgba(239, 68, 68, 0.12);
            color: #ef4444;
            border-color: rgba(239, 68, 68, 0.4);
            transform: scale(1.05);
        }

        .btn-sidebar-logout:active {
            transform: scale(0.95);
        }

        .content-wrapper {
            margin-left: var(--sidebar-width);
            margin-top: 64px;
            transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            padding: 24px;
            min-height: calc(100vh - 64px);
        }

        /* Collapsed Sidebar Mode (Desktop Default) */
        body.sidebar-collapsed .sidebar-wrapper {
            width: var(--sidebar-collapsed-width);
            overflow: visible !important;
        }

        body.sidebar-collapsed .sidebar-menu-scrollable {
            overflow: visible !important;
            padding: 16px 8px 8px 8px;
        }

        body.sidebar-collapsed .sidebar-footer {
            padding: 12px 6px;
        }

        body.sidebar-collapsed .sidebar-user-section {
            justify-content: center !important;
        }

        body.sidebar-collapsed .sidebar-profile-info {
            justify-content: center;
            padding: 6px 0;
            max-width: 100%;
            position: relative;
        }

        body.sidebar-collapsed .btn-sidebar-logout {
            display: none !important;
        }

        /* Tooltip on hover when collapsed for profile */
        body.sidebar-collapsed .sidebar-profile-info[data-title]:hover::after {
            content: attr(data-title);
            position: absolute;
            left: 100%;
            top: 50%;
            transform: translateY(-50%);
            margin-left: 14px;
            background-color: var(--card-bg);
            color: var(--text-main);
            border: 1px solid var(--border-color);
            box-shadow: var(--card-shadow);
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 0.85rem;
            font-weight: 600;
            white-space: nowrap;
            z-index: 1060;
            pointer-events: none;
        }

        body.sidebar-collapsed .content-wrapper {
            margin-left: var(--sidebar-collapsed-width);
        }

        body.sidebar-collapsed .nav-text,
        body.sidebar-collapsed .sidebar-header-text,
        body.sidebar-collapsed .group-chevron,
        body.sidebar-collapsed .sidebar-divider {
            display: none !important;
        }

        /* Single Nav Link in Collapsed mode */
        body.sidebar-collapsed .nav-link-item {
            justify-content: center;
            padding: 12px 0;
            margin-bottom: 6px;
            position: relative;
        }

        /* Tooltip on hover for single items when collapsed */
        body.sidebar-collapsed .nav-link-item[data-title]:hover::after {
            content: attr(data-title);
            position: absolute;
            left: 100%;
            top: 50%;
            transform: translateY(-50%);
            margin-left: 10px;
            background-color: var(--card-bg);
            color: var(--text-main);
            border: 1px solid var(--border-color);
            box-shadow: var(--card-shadow);
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 0.85rem;
            font-weight: 600;
            white-space: nowrap;
            z-index: 1060;
            pointer-events: none;
        }

        /* Sidebar Groups in Collapsed Mode */
        body.sidebar-collapsed .sidebar-group {
            position: relative;
            margin-bottom: 6px;
        }

        body.sidebar-collapsed .sidebar-group-header {
            justify-content: center !important;
            padding: 12px 0;
            margin-bottom: 0 !important;
            border-radius: 10px;
        }

        body.sidebar-collapsed .sidebar-group-header span {
            justify-content: center;
            gap: 0 !important;
        }

        /* Submenu items hidden by default in collapsed mode */
        body.sidebar-collapsed .sidebar-sub-items {
            display: none !important;
            padding-left: 0 !important;
        }

        /* Submenu Flyout Popover on Hover when Collapsed */
        body.sidebar-collapsed .sidebar-group:hover .sidebar-sub-items {
            display: block !important;
            position: absolute;
            top: 0;
            left: 100%;
            margin-left: 6px;
            width: 220px;
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            box-shadow: var(--card-shadow);
            padding: 10px !important;
            z-index: 1060;
        }

        body.sidebar-collapsed .sidebar-group:hover .sidebar-popover-title {
            display: block !important;
        }

        body.sidebar-collapsed .sidebar-group:hover .sidebar-sub-items .nav-link-item {
            justify-content: flex-start !important;
            padding: 8px 12px !important;
            margin-bottom: 4px;
        }

        body.sidebar-collapsed .sidebar-group:hover .sidebar-sub-items .nav-text {
            display: inline-block !important;
        }

        .sidebar-group-header {
            color: var(--text-sub);
            font-size: 0.72rem;
            letter-spacing: 0.6px;
            padding: 8px 10px;
            border-radius: 8px;
            cursor: pointer;
            user-select: none;
            transition: background-color 0.2s ease, color 0.2s ease;
        }

        .sidebar-group-header:hover {
            background-color: var(--sidebar-hover);
            color: var(--text-main);
        }

        .sidebar-sub-items {
            transition: all 0.25s ease;
        }

        /* Nav Link Items */
        .nav-link-item {
            color: var(--text-sub);
            border-radius: 10px;
            padding: 12px 16px;
            margin-bottom: 6px;
            font-weight: 500;
            display: flex;
            align-items: center;
            gap: 14px;
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;
        }

        .nav-link-item:hover {
            background-color: var(--sidebar-hover);
            color: var(--text-main);
        }

        .nav-link-item.active {
            background-color: var(--badge-bg);
            color: var(--brand-green);
            border-left: 4px solid var(--brand-green);
        }

        /* Responsive Mobile Behavior */
        @media (max-width: 768px) {
            .sidebar-wrapper {
                transform: translateX(-100%);
                width: 260px !important;
            }

            .content-wrapper {
                margin-left: 0 !important;
                padding: 16px;
            }

            body.sidebar-mobile-open .sidebar-wrapper {
                transform: translateX(0);
            }

            body.sidebar-mobile-open::after {
                content: '';
                position: fixed;
                inset: 0;
                background: rgba(0,0,0,0.5);
                z-index: 1010;
            }
        }

        /* Cards & Buttons */
        .card-custom {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            color: var(--text-main);
            box-shadow: var(--card-shadow);
            transition: background-color 0.25s ease, border-color 0.25s ease;
        }

        .dashboard-clock-box {
            background-color: var(--sidebar-bg);
            border: 1px solid var(--border-color);
            border-radius: 16px;
            color: var(--text-main);
            transition: background-color 0.25s ease, border-color 0.25s ease;
        }

        .dashboard-subcard {
            background-color: var(--sidebar-bg);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            border-radius: 10px;
            transition: background-color 0.25s ease, border-color 0.25s ease;
        }

        .dashboard-subcard:hover {
            background-color: var(--bg-hover);
        }

        .today-calendar-cell {
            background-color: rgba(34, 197, 94, 0.15) !important;
            border: 1.5px solid #22c55e !important;
            color: #22c55e !important;
            border-radius: 10px;
        }

        .holiday-calendar-cell {
            background-color: rgba(239, 68, 68, 0.12) !important;
            border: 1px solid rgba(239, 68, 68, 0.35) !important;
            color: #ef4444 !important;
            border-radius: 10px;
        }

        .calendar-cell-hover {
            transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.22s ease, border-color 0.22s ease;
            border-radius: 10px;
            cursor: pointer;
            user-select: none;
        }

        .calendar-cell-hover:hover {
            transform: translateY(-4px) scale(1.04);
            z-index: 10;
            box-shadow: 0 10px 22px rgba(0, 0, 0, 0.35);
            border-color: #22c55e !important;
            background-color: var(--bg-hover) !important;
        }

        .calendar-hover-popover {
            position: fixed;
            z-index: 9999;
            pointer-events: auto;
            width: 320px;
            max-width: 92vw;
            background: rgba(255, 255, 255, 0.98);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1.5px solid rgba(34, 197, 94, 0.45);
            box-shadow: 0 16px 36px rgba(0, 0, 0, 0.12), 0 0 20px rgba(34, 197, 94, 0.12);
            border-radius: 16px;
            padding: 14px 16px;
            color: var(--text-main, #1e293b);
            transform: translateY(8px) scale(0.96);
            opacity: 0;
            visibility: hidden;
            transition: opacity 0.2s cubic-bezier(0.16, 1, 0.3, 1), transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.2s;
        }

        .calendar-hover-popover.active {
            opacity: 1;
            visibility: visible;
            transform: translateY(0) scale(1);
        }

        body.dark .calendar-hover-popover,
        [data-theme="dark"] .calendar-hover-popover {
            background: rgba(15, 23, 42, 0.97);
            border-color: rgba(34, 197, 94, 0.45);
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6), 0 0 20px rgba(34, 197, 94, 0.15);
            color: #ffffff;
        }

        @media (prefers-color-scheme: dark) {
            :root:not([data-theme="light"]):not(.dark) .calendar-hover-popover {
                background: rgba(15, 23, 42, 0.97);
                border-color: rgba(34, 197, 94, 0.45);
                box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6), 0 0 20px rgba(34, 197, 94, 0.15);
                color: #ffffff;
            }
        }

        /* Popover Item Theming for Light & Dark */
        .popover-card-jawa {
            background: rgba(245, 158, 11, 0.08);
            border: 1px solid rgba(245, 158, 11, 0.3);
            border-radius: 10px;
            padding: 8px 10px;
            margin-bottom: 8px;
        }
        body.dark .popover-card-jawa,
        [data-theme="dark"] .popover-card-jawa {
            background: rgba(245, 158, 11, 0.12);
            border: 1px solid rgba(245, 158, 11, 0.25);
        }

        .popover-card-islam {
            background: rgba(16, 185, 129, 0.08);
            border: 1px solid rgba(16, 185, 129, 0.3);
            border-radius: 10px;
            padding: 8px 10px;
            margin-bottom: 8px;
        }
        body.dark .popover-card-islam,
        [data-theme="dark"] .popover-card-islam {
            background: rgba(16, 185, 129, 0.12);
            border: 1px solid rgba(16, 185, 129, 0.25);
        }

        .popover-card-holiday {
            background: rgba(239, 68, 68, 0.08);
            border: 1px solid rgba(239, 68, 68, 0.3);
            border-radius: 10px;
            padding: 8px 10px;
            margin-bottom: 8px;
        }
        body.dark .popover-card-holiday,
        [data-theme="dark"] .popover-card-holiday {
            background: rgba(239, 68, 68, 0.18);
            border: 1px solid rgba(239, 68, 68, 0.4);
        }

        .popover-card-observance {
            background: rgba(14, 165, 233, 0.08);
            border: 1px solid rgba(14, 165, 233, 0.3);
            border-radius: 10px;
            padding: 8px 10px;
            margin-bottom: 8px;
        }
        body.dark .popover-card-observance,
        [data-theme="dark"] .popover-card-observance {
            background: rgba(14, 165, 233, 0.15);
            border: 1px solid rgba(14, 165, 233, 0.3);
        }

        .popover-agenda-item {
            background: rgba(241, 245, 249, 0.95);
            border: 1px solid rgba(203, 213, 225, 0.9);
            border-radius: 6px;
            padding: 4px 8px;
            color: var(--text-main, #1e293b);
        }
        body.dark .popover-agenda-item,
        [data-theme="dark"] .popover-agenda-item {
            background: rgba(30, 41, 59, 0.7);
            border: 1px solid rgba(255, 255, 255, 0.1);
            color: #ffffff;
        }

        .popover-text-bold {
            color: var(--text-main, #0f172a);
            font-weight: 700;
        }
        body.dark .popover-text-bold,
        [data-theme="dark"] .popover-text-bold {
            color: #ffffff;
        }

        .popover-text-sub {
            color: var(--text-sub, #64748b);
        }
        body.dark .popover-text-sub,
        [data-theme="dark"] .popover-text-sub {
            color: #cbd5e1;
        }

        .text-light, .text-body {
            color: var(--text-main) !important;
        }

        .text-secondary, .text-muted {
            color: var(--text-sub) !important;
        }

        .btn-brand {
            background-color: var(--brand-green);
            color: #ffffff;
            font-weight: 600;
            border: none;
            border-radius: 8px;
            padding: 8px 18px;
        }

        .btn-brand:hover {
            background-color: var(--brand-green-hover);
            color: #ffffff;
        }

        .form-control-dark, .form-select-dark, .input-group-text-dark {
            background-color: var(--input-bg);
            border: 1px solid var(--border-color);
            color: var(--input-color);
            border-radius: 8px;
        }

        .form-control-dark:focus, .form-select-dark:focus {
            background-color: var(--input-bg);
            border-color: var(--brand-green);
            color: var(--input-color);
            box-shadow: none;
        }

        .table-dark-custom {
            --bs-table-bg: transparent;
            --bs-table-accent-bg: transparent;
            --bs-table-striped-color: var(--text-main);
            --bs-table-striped-bg: var(--table-hover);
            --bs-table-active-color: var(--text-main);
            --bs-table-active-bg: var(--table-hover);
            --bs-table-hover-color: var(--text-main);
            --bs-table-hover-bg: var(--table-hover);
            --bs-table-color: var(--text-main);
            --bs-table-border-color: var(--border-color);
            color: var(--text-main) !important;
            background-color: transparent !important;
        }

        .table-dark-custom > :not(caption) > * > * {
            background-color: transparent;
            color: var(--text-main);
            border-bottom-color: var(--border-color);
        }

        .table-dark-custom thead th {
            border-bottom: 2px solid var(--border-color) !important;
            color: var(--text-sub) !important;
            font-weight: 600;
            background-color: var(--table-th-bg) !important;
            padding: 12px 14px;
        }

        .table-dark-custom tbody td {
            border-bottom: 1px solid var(--border-color) !important;
            vertical-align: middle;
            color: var(--text-main) !important;
            background-color: transparent !important;
            padding: 12px 14px;
        }

        .table-dark-custom tbody tr:hover > * {
            background-color: var(--table-hover) !important;
            color: var(--text-main) !important;
        }

        .btn-close-custom {
            filter: var(--btn-close-filter);
        }

        .status-badge {
            background-color: var(--badge-bg);
            color: var(--brand-green);
            border: 1px solid var(--brand-green);
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 0.85rem;
        }

        /* App Squircle & My Aplikasi Card Styles */
        .app-icon-squircle {
            width: 54px;
            height: 54px;
            border-radius: 16px;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #ffffff;
            box-shadow: 0 4px 10px rgba(0, 0, 0, 0.18);
            transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.25s ease;
        }

        .app-icon-wrapper {
            transition: transform 0.2s ease;
        }

        .app-icon-wrapper:hover .app-icon-squircle {
            transform: translateY(-4px) scale(1.08);
            box-shadow: 0 8px 20px rgba(0, 0, 0, 0.3);
        }

        .app-icon-wrapper:active .app-icon-squircle {
            transform: scale(0.95);
        }

        /* Quran Arabic Text */
        .quran-arabic-text {
            font-family: 'Amiri', 'Traditional Arabic', 'Scheherazade New', serif;
            font-size: 1.75rem;
            line-height: 2.2;
            direction: rtl;
            text-align: right;
            color: var(--text-main);
        }

        /* Retro Gimbot Brick Game LCD casing - Synchronized with Web Connect */
        .brick-game-casing {
            background: linear-gradient(160deg, #1E293B 0%, #0F172A 60%, #020617 100%);
            border: 2px solid #334155;
            border-radius: 36px;
            box-shadow: inset 0 2px 6px rgba(255,255,255,0.08), 0 20px 48px rgba(0,0,0,0.65);
            max-width: 430px;
            width: 100%;
            margin: 0 auto;
            position: relative;
            user-select: none;
            -webkit-user-select: none;
            transition: all 0.3s ease;
        }

        /* Web Connect Light Theme Synchronization */
        .brick-game-casing.theme-light,
        [data-theme="light"] .brick-game-casing.theme-auto {
            background: linear-gradient(160deg, #FFFFFF 0%, #F1F5F9 60%, #E2E8F0 100%);
            border: 2px solid #CBD5E1;
            box-shadow: inset 0 2px 6px rgba(255,255,255,0.9), 0 16px 40px rgba(0,0,0,0.12);
        }

        /* Web Connect Dark Theme Synchronization */
        .brick-game-casing.theme-dark,
        [data-theme="dark"] .brick-game-casing.theme-auto {
            background: linear-gradient(160deg, #1E293B 0%, #0F172A 60%, #020617 100%);
            border: 2px solid #334155;
            box-shadow: inset 0 2px 6px rgba(255,255,255,0.08), 0 20px 48px rgba(0,0,0,0.65);
        }

        .brick-game-casing.theme-classic {
            background: linear-gradient(160deg, #C4C8C5 0%, #B8BCB9 60%, #A8ACA9 100%);
            border: 2px solid #909490;
            box-shadow: inset 0 2px 4px rgba(255,255,255,0.6), 0 16px 40px rgba(0,0,0,0.3);
        }

        .brick-game-casing.theme-neon {
            background: linear-gradient(160deg, #161924 0%, #11141E 60%, #0A0D15 100%);
            border: 2px solid #2E3545;
            box-shadow: inset 0 2px 4px rgba(255,255,255,0.08), 0 16px 40px rgba(0,0,0,0.6);
        }

        .brick-game-lcd-frame {
            background: #0B1120;
            border: 4px solid #1E293B;
            border-radius: 16px 16px 44px 16px;
            padding: 14px;
            box-shadow: inset 0 4px 12px rgba(0,0,0,0.8), 0 2px 6px rgba(56,189,248,0.1);
            transition: all 0.3s ease;
        }

        .theme-light .brick-game-lcd-frame,
        [data-theme="light"] .theme-auto .brick-game-lcd-frame {
            background: #E2E8F0;
            border: 4px solid #CBD5E1;
            box-shadow: inset 0 4px 10px rgba(0,0,0,0.15), 0 2px 6px rgba(255,255,255,0.8);
        }

        .theme-dark .brick-game-lcd-frame,
        [data-theme="dark"] .theme-auto .brick-game-lcd-frame {
            background: #0B1120;
            border: 4px solid #1E293B;
            box-shadow: inset 0 4px 12px rgba(0,0,0,0.8), 0 2px 6px rgba(56,189,248,0.1);
        }

        .theme-classic .brick-game-lcd-frame {
            background: #5D6164;
            border: 4px solid #303233;
            box-shadow: inset 0 4px 10px rgba(0,0,0,0.6), 0 2px 4px rgba(255,255,255,0.3);
        }

        .theme-neon .brick-game-lcd-frame {
            background: #0A0D15;
            border: 4px solid #1E2430;
            box-shadow: inset 0 4px 10px rgba(0,0,0,0.8), 0 2px 4px rgba(255,255,255,0.05);
        }

        .brick-game-lcd {
            background-color: #0F172A;
            color: #F8FAFC;
            border: 2.5px solid #38BDF8;
            border-radius: 10px;
            box-shadow: inset 0 4px 12px rgba(0,0,0,0.7), 0 0 16px rgba(56,189,248,0.25);
            font-family: 'Courier New', 'VT323', monospace;
            position: relative;
            transition: all 0.3s ease;
        }

        .theme-light .brick-game-lcd,
        [data-theme="light"] .theme-auto .brick-game-lcd {
            background-color: #F8FAFC;
            color: #0F172A;
            border: 2.5px solid #0284C7;
            box-shadow: inset 0 3px 10px rgba(0,0,0,0.1), 0 0 14px rgba(2,132,199,0.22);
        }

        .theme-dark .brick-game-lcd,
        [data-theme="dark"] .theme-auto .brick-game-lcd {
            background-color: #0F172A;
            color: #F8FAFC;
            border: 2.5px solid #38BDF8;
            box-shadow: inset 0 4px 12px rgba(0,0,0,0.7), 0 0 16px rgba(56,189,248,0.25);
        }

        .theme-classic .brick-game-lcd {
            background-color: #9BBC0F;
            color: #0F380F;
            border: 2.5px solid #306230;
            box-shadow: inset 0 3px 8px rgba(0,0,0,0.45);
        }

        .theme-neon .brick-game-lcd {
            background-color: #0F121C;
            color: #E2E8F0;
            border: 2.5px solid #00FFD2;
            box-shadow: inset 0 3px 8px rgba(0,0,0,0.6), 0 0 12px rgba(0,255,210,0.15);
        }

        .brick-game-canvas-responsive {
            max-width: 100%;
            height: auto;
            border-radius: 8px;
            display: block;
        }

        .brick-dpad-well {
            width: 152px;
            height: 152px;
            border-radius: 50%;
            background: #131E31;
            display: flex;
            align-items: center;
            justify-content: center;
            box-shadow: inset 0 4px 10px rgba(0,0,0,0.6), 0 2px 4px rgba(255,255,255,0.06);
            position: relative;
        }

        .theme-light .brick-dpad-well,
        [data-theme="light"] .theme-auto .brick-dpad-well {
            background: #E2E8F0;
            box-shadow: inset 0 4px 8px rgba(0,0,0,0.15), 0 2px 4px rgba(255,255,255,0.9);
        }

        .theme-dark .brick-dpad-well,
        [data-theme="dark"] .theme-auto .brick-dpad-well {
            background: #131E31;
            box-shadow: inset 0 4px 10px rgba(0,0,0,0.6), 0 2px 4px rgba(255,255,255,0.06);
        }

        .theme-classic .brick-dpad-well {
            background: #B1B5B2;
            box-shadow: inset 0 4px 8px rgba(0,0,0,0.35), 0 2px 4px rgba(255,255,255,0.5);
        }

        .theme-neon .brick-dpad-well {
            background: #131924;
            box-shadow: inset 0 4px 8px rgba(0,0,0,0.5), 0 2px 4px rgba(255,255,255,0.05);
        }

        .brick-dpad-cross {
            width: 130px;
            height: 130px;
            position: relative;
        }

        .brick-dpad-arm {
            position: absolute;
            background: #1E293B;
            cursor: pointer;
            user-select: none;
            display: flex;
            align-items: center;
            justify-content: center;
            color: #38BDF8;
            transition: all 0.08s ease;
            touch-action: manipulation;
            border: none;
            outline: none;
            padding: 0;
        }

        .theme-light .brick-dpad-arm,
        [data-theme="light"] .theme-auto .brick-dpad-arm {
            background: #334155;
            color: #FFFFFF;
        }

        .theme-dark .brick-dpad-arm,
        [data-theme="dark"] .theme-auto .brick-dpad-arm {
            background: #1E293B;
            color: #38BDF8;
        }

        .theme-classic .brick-dpad-arm {
            background: #282B2A;
            color: #D1D5DB;
        }

        .theme-neon .brick-dpad-arm {
            background: #1B2332;
            color: #00FFD2;
        }

        .brick-dpad-arm:active {
            filter: brightness(1.3);
            transform: scale(0.96);
        }

        .brick-dpad-up {
            top: 0; left: 44px; width: 42px; height: 46px;
            border-radius: 9px 9px 0 0;
            box-shadow: 0 -3px 0 rgba(0,0,0,0.4);
        }
        .brick-dpad-down {
            bottom: 0; left: 44px; width: 42px; height: 46px;
            border-radius: 0 0 9px 9px;
            box-shadow: 0 3px 0 rgba(0,0,0,0.4);
        }
        .brick-dpad-left {
            top: 44px; left: 0; width: 46px; height: 42px;
            border-radius: 9px 0 0 9px;
            box-shadow: -3px 0 0 rgba(0,0,0,0.4);
        }
        .brick-dpad-right {
            top: 44px; right: 0; width: 46px; height: 42px;
            border-radius: 0 9px 9px 0;
            box-shadow: 3px 0 0 rgba(0,0,0,0.4);
        }
        .brick-dpad-center {
            top: 44px; left: 44px; width: 42px; height: 42px;
            position: absolute;
            background: #1E293B;
        }
        .theme-light .brick-dpad-center, [data-theme="light"] .theme-auto .brick-dpad-center { background: #334155; }
        .theme-dark .brick-dpad-center, [data-theme="dark"] .theme-auto .brick-dpad-center { background: #1E293B; }
        .theme-classic .brick-dpad-center { background: #282B2A; }
        .theme-neon .brick-dpad-center { background: #1B2332; }

        .brick-action-pill-well {
            width: 168px;
            height: 104px;
            transform: rotate(-25deg);
            background: #131E31;
            border-radius: 24px;
            border: 1px solid rgba(56, 189, 248, 0.4);
            display: flex;
            align-items: center;
            justify-content: space-around;
            padding: 0 10px;
            box-shadow: inset 0 4px 8px rgba(0,0,0,0.6);
        }

        .theme-light .brick-action-pill-well,
        [data-theme="light"] .theme-auto .brick-action-pill-well {
            background: #E2E8F0;
            border: 1px solid #CBD5E1;
            box-shadow: inset 0 3px 6px rgba(0,0,0,0.18);
        }

        .theme-dark .brick-action-pill-well,
        [data-theme="dark"] .theme-auto .brick-action-pill-well {
            background: #131E31;
            border: 1px solid rgba(56, 189, 248, 0.4);
            box-shadow: inset 0 4px 8px rgba(0,0,0,0.6);
        }

        .theme-classic .brick-action-pill-well {
            background: #B1B5B2;
            border: 1px solid #9EA29F;
            box-shadow: inset 0 3px 6px rgba(0,0,0,0.3);
        }

        .theme-neon .brick-action-pill-well {
            background: #131924;
            border: 1px solid rgba(255, 0, 127, 0.5);
            box-shadow: inset 0 3px 6px rgba(0,0,0,0.5);
        }

        .brick-action-btn-circle {
            width: 58px;
            height: 58px;
            border-radius: 50%;
            display: inline-flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            user-select: none;
            border: none;
            outline: none;
            padding: 0;
            transition: transform 0.08s ease, box-shadow 0.08s ease, filter 0.08s ease;
            touch-action: manipulation;
        }

        .brick-action-btn-circle .brick-btn-label {
            font-size: 16px;
            font-weight: 900;
            line-height: 1;
        }

        .brick-action-btn-circle .brick-btn-sub {
            font-size: 8.5px;
            font-weight: 800;
            letter-spacing: 0.6px;
            opacity: 0.92;
            margin-top: 2px;
            text-transform: uppercase;
            line-height: 1;
        }

        .brick-action-btn-circle.btn-a {
            background: radial-gradient(circle at 35% 35%, #22C55E, #15803D);
            color: #FFF;
            border: 2px solid #4ADE80;
            box-shadow: 0 5px 0 #14532D, 0 8px 16px rgba(0,0,0,0.45);
        }

        .brick-action-btn-circle.btn-b {
            background: radial-gradient(circle at 35% 35%, #38BDF8, #0284C7);
            color: #FFF;
            border: 2px solid #7DD3FC;
            box-shadow: 0 5px 0 #075985, 0 8px 16px rgba(0,0,0,0.45);
        }

        .theme-light .brick-action-btn-circle.btn-a,
        [data-theme="light"] .theme-auto .brick-action-btn-circle.btn-a {
            background: radial-gradient(circle at 35% 35%, #10B981, #059669);
            border: 2px solid #059669;
            box-shadow: 0 5px 0 #065F46, 0 8px 14px rgba(0,0,0,0.2);
            color: #FFF;
        }

        .theme-light .brick-action-btn-circle.btn-b,
        [data-theme="light"] .theme-auto .brick-action-btn-circle.btn-b {
            background: radial-gradient(circle at 35% 35%, #0284C7, #0369A1);
            border: 2px solid #0369A1;
            box-shadow: 0 5px 0 #075985, 0 8px 14px rgba(0,0,0,0.2);
            color: #FFF;
        }

        .theme-classic .brick-action-btn-circle {
            background: radial-gradient(circle at 35% 35%, #8A1E3C, #5C1226);
            border: 2px solid #550E22;
            box-shadow: 0 5px 0 #3A0815, 0 8px 14px rgba(0,0,0,0.35);
            color: #FFF;
        }

        .theme-neon .brick-action-btn-circle.btn-a {
            background: radial-gradient(circle at 35% 35%, #FF0055, #D00040);
            color: #FFF;
            border: 2px solid #FF007F;
            box-shadow: 0 5px 0 #900030, 0 8px 14px rgba(0,0,0,0.4);
        }

        .theme-neon .brick-action-btn-circle.btn-b {
            background: radial-gradient(circle at 35% 35%, #7000FF, #4D00B3);
            color: #FFF;
            border: 2px solid #9D4EDD;
            box-shadow: 0 5px 0 #300073, 0 8px 14px rgba(0,0,0,0.4);
        }

        .brick-action-btn-circle:active {
            transform: translateY(4px);
            box-shadow: 0 1px 0 rgba(0,0,0,0.5), 0 3px 6px rgba(0,0,0,0.3);
        }

        /* Pill Buttons with Title Moved Inside */
        .brick-pill-btn-wrapper {
            display: inline-flex;
            align-items: center;
            justify-content: center;
        }

        .brick-pill-btn {
            width: 86px;
            height: 28px;
            border-radius: 14px;
            transform: rotate(-25deg);
            cursor: pointer;
            user-select: none;
            background: linear-gradient(135deg, rgba(56, 189, 248, 0.32), rgba(14, 165, 233, 0.45));
            border: 1.5px solid #38BDF8;
            color: #FFFFFF;
            font-family: system-ui, -apple-system, sans-serif;
            font-size: 10px;
            font-weight: 800;
            letter-spacing: 0.8px;
            text-transform: uppercase;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 0 8px;
            transition: all 0.1s ease;
            touch-action: manipulation;
            box-shadow: 0 3px 6px rgba(0,0,0,0.35);
            outline: none;
        }

        .brick-pill-btn:hover {
            filter: brightness(1.15);
        }

        .brick-pill-btn:active {
            transform: rotate(-25deg) translateY(2px);
            box-shadow: 0 1px 2px rgba(0,0,0,0.25);
            filter: brightness(1.3);
        }

        .brick-pill-btn.btn-reset {
            background: linear-gradient(135deg, rgba(239, 68, 68, 0.35), rgba(220, 38, 38, 0.5));
            border: 1.5px solid #EF4444;
            color: #FFFFFF;
        }

        .theme-light .brick-pill-btn,
        [data-theme="light"] .theme-auto .brick-pill-btn {
            background: linear-gradient(135deg, #0284C7, #0369A1);
            border: 1.5px solid #0284C7;
            color: #FFFFFF;
        }

        .theme-light .brick-pill-btn.btn-reset,
        [data-theme="light"] .theme-auto .brick-pill-btn.btn-reset {
            background: linear-gradient(135deg, #EF4444, #DC2626);
            border: 1.5px solid #EF4444;
            color: #FFFFFF;
        }

        .theme-classic .brick-pill-btn {
            background: #5D6164;
            border: 1.5px solid #303233;
            color: #E5E7EB;
        }

        .theme-classic .brick-pill-btn.btn-reset {
            background: #901010;
            border: 1.5px solid #600000;
            color: #FFFFFF;
        }

        .theme-neon .brick-pill-btn {
            background: rgba(0, 255, 210, 0.22);
            border: 1.5px solid #00FFD2;
            color: #00FFD2;
        }

        .theme-neon .brick-pill-btn.btn-reset {
            background: rgba(255, 0, 85, 0.25);
            border: 1.5px solid #FF0055;
            color: #FF0055;
        }

        .brick-speaker-grille {
            display: flex;
            gap: 4px;
            transform: rotate(-25deg);
        }
        .brick-speaker-line {
            width: 4px;
            height: 20px;
            background: #38BDF8;
            border-radius: 2px;
            box-shadow: inset 0 1px 2px rgba(0,0,0,0.4);
        }
        .theme-light .brick-speaker-line, [data-theme="light"] .theme-auto .brick-speaker-line { background: #0284C7; }
        .theme-dark .brick-speaker-line, [data-theme="dark"] .theme-auto .brick-speaker-line { background: #38BDF8; }
        .theme-classic .brick-speaker-line { background: #8F9390; }
        .theme-neon .brick-speaker-line { background: #FF007F; }

        .brick-game-lobby-item {
            cursor: pointer;
            border-radius: 8px;
            padding: 6px 10px;
            transition: background 0.15s ease, transform 0.1s ease;
            font-family: monospace;
            font-size: 11.5px;
            display: flex;
            align-items: center;
            user-select: none;
        }
        .brick-game-lobby-item:hover, .brick-game-lobby-item.active {
            background: rgba(56, 189, 248, 0.22);
            transform: translateX(2px);
        }
        .theme-light .brick-game-lobby-item:hover, .theme-light .brick-game-lobby-item.active,
        [data-theme="light"] .theme-auto .brick-game-lobby-item:hover, [data-theme="light"] .theme-auto .brick-game-lobby-item.active {
            background: rgba(2, 132, 199, 0.18);
        }
        .theme-classic .brick-game-lobby-item:hover, .theme-classic .brick-game-lobby-item.active {
            background: rgba(48, 98, 48, 0.25);
        }
        .theme-neon .brick-game-lobby-item:hover, .theme-neon .brick-game-lobby-item.active {
            background: rgba(0, 255, 210, 0.18);
        }

        /* Block Puzzle Styles - 1:1 Match to My Singghara App */
        .puzzle-game-wrapper {
            max-width: 1080px;
            margin: 0 auto;
            user-select: none;
        }
        .puzzle-stats-card {
            background: linear-gradient(135deg, #183842 0%, #1F4450 100%);
            border: 1.5px solid #2C5E6A;
            border-radius: 16px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.25);
            color: #E0F2F1;
            height: 100%;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
        }
        .puzzle-board-container {
            background-color: #152A33;
            border: 3.5px solid #2C5E6A;
            border-radius: 18px;
            box-shadow: 0 14px 40px rgba(0,0,0,0.35);
            position: relative;
            user-select: none;
            touch-action: none;
            width: 100%;
            max-width: 520px;
            padding: 10px;
        }
        .puzzle-grid {
            display: grid;
            grid-template-columns: repeat(8, 1fr);
            gap: 4px;
        }
        .puzzle-cell {
            aspect-ratio: 1;
            border-radius: 6px;
            transition: transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.1s ease;
            position: relative;
            box-sizing: border-box;
            cursor: pointer;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .puzzle-cell-empty {
            background-color: #1C2D37;
            border: none;
        }
        .puzzle-cell-empty:hover {
            background-color: #243D4A;
        }
        .puzzle-cell-filled {
            box-shadow: 0 2px 6px rgba(0,0,0,0.35);
        }
        .puzzle-preview-valid {
            opacity: 0.90 !important;
            animation: pulsePreview 0.6s infinite alternate ease-in-out;
            z-index: 5;
        }
        .puzzle-preview-invalid {
            background-color: rgba(244, 67, 54, 0.45) !important;
            border: 1.5px solid #F44336 !important;
        }
        .puzzle-preview-target {
            background-color: rgba(255, 152, 0, 0.4) !important;
            border: 2px dashed #FF9800 !important;
            animation: pulsePreview 0.5s infinite alternate ease-in-out;
        }
        @keyframes pulsePreview {
            from { transform: scale(0.96); opacity: 0.75; }
            to { transform: scale(1.04); opacity: 1; }
        }
        @keyframes lineClearFlash {
            0% { transform: scale(1); filter: brightness(2.5); }
            50% { transform: scale(1.2); filter: brightness(3.5); opacity: 1; }
            100% { transform: scale(0); opacity: 0; }
        }
        .puzzle-clearing {
            animation: lineClearFlash 0.35s forwards ease-out;
        }
        .puzzle-tray-container {
            background-color: #14303B;
            border: 1.5px solid #285664;
            border-radius: 16px;
            padding: 10px 8px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.25);
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            align-items: center;
            height: 100%;
            width: 100%;
        }
        .puzzle-tray-card {
            background-color: rgba(0,0,0,0.20);
            border: 1.5px solid rgba(255,255,255,0.08);
            border-radius: 12px;
            transition: transform 0.15s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.15s ease, border-color 0.15s ease;
            user-select: none;
            -webkit-user-select: none;
            touch-action: none;
            flex: 1;
            min-height: 80px;
            max-height: 115px;
            padding: 6px 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: grab;
            width: 100%;
        }
        .puzzle-tray-card:hover {
            transform: scale(1.04);
            border-color: rgba(0, 188, 212, 0.45);
        }
        .puzzle-tray-card:active {
            cursor: grabbing;
        }
        .puzzle-tray-card.selected {
            border-color: #FBC02D !important;
            background-color: rgba(251, 192, 45, 0.16) !important;
            box-shadow: 0 0 12px rgba(251, 192, 45, 0.5);
            transform: scale(1.05);
        }
        .puzzle-tray-card.is-dragging {
            opacity: 0 !important;
            visibility: hidden !important;
            pointer-events: none !important;
        }
        .puzzle-floating-drag-piece {
            position: fixed;
            z-index: 10000;
            pointer-events: none;
            touch-action: none;
            filter: drop-shadow(0 14px 28px rgba(0,0,0,0.75));
            will-change: left, top;
        }
        .puzzle-booster-btn {
            background-color: #183842;
            border: 1.5px solid #2C5E6A;
            color: #E0F2F1;
            border-radius: 10px;
            transition: all 0.18s ease;
            font-size: 11.5px;
            font-weight: 600;
            padding: 6px 9px;
        }
        .puzzle-booster-btn:hover {
            background-color: #1F4450;
            border-color: #00BCD4;
            color: #FFFFFF;
        }
        .puzzle-booster-btn.active {
            border-color: #FBC02D !important;
            background-color: rgba(251, 192, 45, 0.22) !important;
            box-shadow: 0 0 14px rgba(251, 192, 45, 0.6);
            color: #FFD54F !important;
            transform: scale(1.04);
        }
        .puzzle-progress-bar {
            background-color: #102229;
            border-radius: 999px;
            height: 8px;
            overflow: hidden;
            border: 1px solid rgba(44, 94, 106, 0.4);
        }
        .puzzle-progress-fill {
            background: linear-gradient(90deg, #00BCD4, #FBC02D);
            height: 100%;
            border-radius: 999px;
            transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .puzzle-combo-toast {
            position: absolute;
            top: 40%;
            left: 50%;
            transform: translate(-50%, -50%) scale(0.5);
            opacity: 0;
            pointer-events: none;
            z-index: 100;
            animation: comboAnim 1.2s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
        }
        @keyframes comboAnim {
            0% { transform: translate(-50%, -20%) scale(0.5); opacity: 0; }
            30% { transform: translate(-50%, -50%) scale(1.2); opacity: 1; }
            70% { transform: translate(-50%, -60%) scale(1); opacity: 1; }
            100% { transform: translate(-50%, -90%) scale(0.8); opacity: 0; }
        }

        /* Pull To Refresh Indicator */
        #pullToRefreshIndicator {
            position: fixed;
            top: 56px;
            left: 50%;
            transform: translateX(-50%) translateY(-100%);
            z-index: 1040;
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            box-shadow: 0 4px 16px rgba(0,0,0,0.18);
            border-radius: 30px;
            padding: 8px 20px;
            display: flex;
            align-items: center;
            gap: 10px;
            pointer-events: none;
            opacity: 0;
            transition: opacity 0.2s ease, transform 0.2s ease;
        }
        #ptrArrow {
            font-size: 1.2rem;
            color: #2E7D32;
            transition: transform 0.2s ease;
        }

        /* Background blur and smooth transition for main content when locked */
        .navbar-main,
        .sidebar-wrapper,
        .content-wrapper {
            transition: filter 0.45s cubic-bezier(0.16, 1, 0.3, 1), transform 0.45s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.45s ease;
        }

        body.auth-locked .navbar-main,
        body.auth-locked .sidebar-wrapper,
        body.auth-locked .content-wrapper {
            filter: blur(14px) saturate(0.85);
            transform: scale(0.985);
            pointer-events: none !important;
            user-select: none !important;
        }

        /* Auth QR Overlay */
        #authOverlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100vw;
            height: 100vh;
            background: rgba(241, 245, 249, 0.88);
            backdrop-filter: blur(18px);
            -webkit-backdrop-filter: blur(18px);
            z-index: 99999;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
            overflow-y: auto;
            opacity: 1;
            visibility: visible;
            transition: opacity 0.4s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.4s cubic-bezier(0.16, 1, 0.3, 1), background 0.3s ease;
        }

        body.dark #authOverlay,
        [data-theme="dark"] #authOverlay {
            background: rgba(10, 15, 29, 0.92);
        }

        #authOverlay.hidden {
            opacity: 0;
            visibility: hidden;
            pointer-events: none;
        }

        .auth-card {
            background: #ffffff;
            color: #1e293b;
            border-radius: 28px;
            box-shadow: 0 24px 60px rgba(0,0,0,0.1), 0 4px 20px rgba(0,0,0,0.05);
            border: 1px solid #e2e8f0;
            max-width: 780px;
            width: 100%;
            overflow: hidden;
            transform: scale(1) translateY(0);
            opacity: 1;
            transition: transform 0.45s cubic-bezier(0.175, 0.885, 0.32, 1.2), opacity 0.4s ease, background 0.3s ease, border-color 0.3s ease, color 0.3s ease, box-shadow 0.3s ease;
        }

        #authOverlay.hidden .auth-card {
            transform: scale(0.9) translateY(24px);
            opacity: 0;
        }

        body.dark .auth-card,
        [data-theme="dark"] .auth-card {
            background: #1e293b;
            color: #f8fafc;
            border: 1px solid rgba(255, 255, 255, 0.12);
            box-shadow: 0 25px 60px rgba(0,0,0,0.6);
        }

        .auth-card-header-badge {
            background: rgba(22, 163, 74, 0.12);
            color: #16a34a;
            border: 1px solid rgba(22, 163, 74, 0.2);
            transition: all 0.3s ease;
        }
        body.dark .auth-card-header-badge,
        [data-theme="dark"] .auth-card-header-badge {
            background: rgba(34, 197, 94, 0.15);
            color: #4ade80;
            border-color: rgba(34, 197, 94, 0.3);
        }

        .auth-card-header-badge.auth-badge-disconnected {
            background: rgba(239, 68, 68, 0.15) !important;
            color: #ef4444 !important;
            border-color: rgba(239, 68, 68, 0.3) !important;
            animation: lockPulseDisconn 0.8s ease-in-out;
        }

        @keyframes lockPulseDisconn {
            0% { transform: scale(1); }
            50% { transform: scale(1.08); }
            100% { transform: scale(1); }
        }

        @keyframes lockShake {
            0%, 100% { transform: rotate(0deg); }
            20%, 60% { transform: rotate(-10deg) scale(1.1); }
            40%, 80% { transform: rotate(10deg) scale(1.1); }
        }

        .auth-lock-animated-icon {
            display: inline-block;
            animation: lockShake 0.75s ease-in-out;
        }

        /* --- ACCESS REJECTED ANIMATIONS & STYLES --- */
        .auth-rejected-container {
            text-align: center;
            padding: 30px 20px;
            animation: fadeInRejection 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }

        @keyframes fadeInRejection {
            from { opacity: 0; transform: scale(0.94); }
            to { opacity: 1; transform: scale(1); }
        }

        .auth-rejected-shield-wrapper {
            position: relative;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 110px;
            height: 110px;
            margin-bottom: 24px;
        }

        .auth-rejected-pulse-ring {
            position: absolute;
            width: 100%;
            height: 100%;
            border-radius: 50%;
            background: rgba(239, 68, 68, 0.2);
            animation: rejectRingPulse 2s infinite cubic-bezier(0.215, 0.61, 0.355, 1);
        }

        .auth-rejected-pulse-ring-2 {
            position: absolute;
            width: 100%;
            height: 100%;
            border-radius: 50%;
            background: rgba(239, 68, 68, 0.15);
            animation: rejectRingPulse 2s infinite cubic-bezier(0.215, 0.61, 0.355, 1) 0.65s;
        }

        @keyframes rejectRingPulse {
            0% { transform: scale(0.85); opacity: 0.9; }
            60% { transform: scale(1.65); opacity: 0.15; }
            100% { transform: scale(1.9); opacity: 0; }
        }

        .auth-rejected-icon-badge {
            position: relative;
            z-index: 2;
            width: 84px;
            height: 84px;
            border-radius: 50%;
            background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #ffffff;
            font-size: 2.5rem;
            box-shadow: 0 10px 25px rgba(239, 68, 68, 0.45);
            animation: rejectShieldShake 3s infinite ease-in-out;
        }

        @keyframes rejectShieldShake {
            0%, 100% { transform: scale(1) rotate(0deg); }
            5%, 15% { transform: scale(1.05) rotate(-6deg); }
            10%, 20% { transform: scale(1.05) rotate(6deg); }
            25% { transform: scale(1) rotate(0deg); }
            50% { transform: scale(1.03); }
        }

        .auth-rejected-title {
            color: #dc2626;
            font-size: 1.75rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 8px;
        }
        body.dark .auth-rejected-title,
        [data-theme="dark"] .auth-rejected-title {
            color: #f87171;
        }

        .auth-rejected-desc {
            color: #64748b;
            max-width: 480px;
            margin: 0 auto 24px auto;
            font-size: 0.95rem;
            line-height: 1.5;
        }
        body.dark .auth-rejected-desc,
        [data-theme="dark"] .auth-rejected-desc {
            color: #cbd5e1;
        }

        .auth-rejected-box {
            background: rgba(239, 68, 68, 0.06);
            border: 1px dashed rgba(239, 68, 68, 0.35);
            border-radius: 16px;
            padding: 16px;
            margin-bottom: 24px;
            display: inline-block;
            max-width: 460px;
            width: 100%;
            text-align: left;
        }
        body.dark .auth-rejected-box,
        [data-theme="dark"] .auth-rejected-box {
            background: rgba(239, 68, 68, 0.12);
            border-color: rgba(239, 68, 68, 0.4);
        }

        .btn-retry-auth {
            background: linear-gradient(135deg, #16a34a 0%, #15803d 100%);
            color: #ffffff;
            border: none;
            padding: 12px 28px;
            border-radius: 14px;
            font-weight: 700;
            font-size: 0.95rem;
            box-shadow: 0 4px 15px rgba(22, 163, 74, 0.3);
            transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
            display: inline-flex;
            align-items: center;
            gap: 8px;
        }
        .btn-retry-auth:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(22, 163, 74, 0.45);
            color: #ffffff;
        }
        .btn-retry-auth:active {
            transform: translateY(0);
        }

        .auth-title {
            color: #15803d;
            letter-spacing: -0.5px;
            transition: color 0.2s ease;
        }
        body.dark .auth-title,
        [data-theme="dark"] .auth-title {
            color: #4ade80;
        }

        .auth-subtitle {
            color: #64748b;
            transition: color 0.2s ease;
        }
        body.dark .auth-subtitle,
        [data-theme="dark"] .auth-subtitle {
            color: #94a3b8;
        }

        .auth-step-circle {
            width: 32px;
            height: 32px;
            font-size: 13px;
            font-weight: 700;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
            background: #16a34a;
            color: #ffffff;
            box-shadow: 0 2px 6px rgba(22, 163, 74, 0.3);
            transition: all 0.2s ease;
        }
        body.dark .auth-step-circle,
        [data-theme="dark"] .auth-step-circle {
            background: #22c55e;
            color: #0f172a;
            box-shadow: 0 2px 8px rgba(34, 197, 94, 0.4);
        }

        .auth-step-title {
            color: #0f172a;
            font-weight: 700;
            transition: color 0.2s ease;
        }
        body.dark .auth-step-title,
        [data-theme="dark"] .auth-step-title {
            color: #f8fafc;
        }

        .auth-step-desc {
            color: #64748b;
            transition: color 0.2s ease;
        }
        body.dark .auth-step-desc,
        [data-theme="dark"] .auth-step-desc {
            color: #94a3b8;
        }

        .auth-step-item {
            padding: 10px 14px;
            border-radius: 14px;
            background: #f8fafc;
            border: 1px solid #e2e8f0;
            transition: all 0.2s ease;
        }
        body.dark .auth-step-item,
        [data-theme="dark"] .auth-step-item {
            background: rgba(15, 23, 42, 0.6);
            border-color: rgba(255, 255, 255, 0.08);
        }

        .qr-box {
            background: #ffffff;
            padding: 18px;
            border-radius: 22px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            box-shadow: 0 8px 25px rgba(0,0,0,0.06);
            border: 2px solid #e2e8f0;
            min-width: 220px;
            min-height: 220px;
            transition: all 0.3s ease;
        }
        body.dark .qr-box,
        [data-theme="dark"] .qr-box {
            background: #ffffff;
            border-color: rgba(255, 255, 255, 0.18);
            box-shadow: 0 12px 35px rgba(0,0,0,0.45);
        }

        .qr-pin-badge {
            background: #f8fafc;
            color: #0f172a;
            font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
            letter-spacing: 4px;
            font-size: 1.4rem;
            font-weight: 800;
            padding: 9px 20px;
            border-radius: 14px;
            border: 2px dashed #cbd5e1;
            box-shadow: inset 0 1px 3px rgba(0,0,0,0.04);
            display: inline-block;
            transition: all 0.3s ease;
        }
        body.dark .qr-pin-badge,
        [data-theme="dark"] .qr-pin-badge {
            background: #0f172a;
            color: #38bdf8;
            border: 2px dashed #334155;
            box-shadow: inset 0 2px 6px rgba(0,0,0,0.3);
        }

        .auth-theme-toggle {
            background: #f1f5f9;
            color: #475569;
            border: 1px solid #cbd5e1;
            border-radius: 20px;
            padding: 6px 14px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: all 0.2s ease;
        }
        .auth-theme-toggle:hover {
            background: #e2e8f0;
            color: #1e293b;
        }
        body.dark .auth-theme-toggle,
        [data-theme="dark"] .auth-theme-toggle {
            background: rgba(15, 23, 42, 0.8);
            color: #cbd5e1;
            border-color: rgba(255, 255, 255, 0.15);
        }
        body.dark .auth-theme-toggle:hover,
        [data-theme="dark"] .auth-theme-toggle:hover {
            background: #334155;
            color: #ffffff;
        }

        /* ==================================================== */
        /* --- TOGGLE MENU ICON (LIGHT & DARK - NO HOVER JUMP) --- */
        /* ==================================================== */
        .btn-toggle-menu {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 42px;
            height: 42px;
            border-radius: 12px;
            background-color: transparent;
            border: 1px solid var(--border-color);
            color: var(--text-main);
            font-size: 1.45rem;
            cursor: pointer;
            outline: none;
            padding: 0;
            position: relative;
            user-select: none;
            overflow: hidden;
            transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
        }

        .btn-toggle-menu i {
            display: inline-block;
            transition: color 0.2s ease;
        }

        /* Light Mode Hover & Active (Clean, No Scale/Rotate Animation) */
        .btn-toggle-menu:hover {
            background-color: var(--sidebar-hover);
            color: var(--primary-color);
            border-color: var(--primary-color);
        }

        .btn-toggle-menu:active {
            background-color: rgba(54, 116, 181, 0.18) !important;
        }

        /* Dark Mode Hover & Active (Clean, No Scale/Rotate Animation) */
        body.dark .btn-toggle-menu,
        [data-theme="dark"] .btn-toggle-menu {
            background-color: rgba(255, 255, 255, 0.04);
            border-color: var(--border-color);
            color: var(--text-main);
        }

        body.dark .btn-toggle-menu:hover,
        [data-theme="dark"] .btn-toggle-menu:hover {
            background-color: rgba(56, 189, 248, 0.14);
            color: #38bdf8;
            border-color: #38bdf8;
        }

        body.dark .btn-toggle-menu:active,
        [data-theme="dark"] .btn-toggle-menu:active {
            background-color: rgba(56, 189, 248, 0.28) !important;
        }

        @media (prefers-color-scheme: dark) {
            :root:not([data-theme="light"]):not(.dark) .btn-toggle-menu {
                background-color: rgba(255, 255, 255, 0.04);
                border-color: var(--border-color);
                color: var(--text-main);
            }
            :root:not([data-theme="light"]):not(.dark) .btn-toggle-menu:hover {
                background-color: rgba(56, 189, 248, 0.14);
                color: #38bdf8;
                border-color: #38bdf8;
            }
            :root:not([data-theme="light"]):not(.dark) .btn-toggle-menu:active {
                background-color: rgba(56, 189, 248, 0.28) !important;
            }
        }

        /* ==================================================== */
        /* --- CALENDAR NAVIGATION ICONS < > (NO HOVER SHIFT) --- */
        /* ==================================================== */
        .btn-cal-nav {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 38px;
            height: 38px;
            border-radius: 50%;
            background-color: var(--sidebar-bg);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            font-size: 1.15rem;
            cursor: pointer;
            outline: none;
            padding: 0;
            position: relative;
            user-select: none;
            transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
            box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
        }

        .btn-cal-nav i {
            display: inline-block;
            transition: color 0.2s ease;
        }

        /* Light Mode Hover (Clean Static, No Translate/Scale Animation) */
        .btn-cal-nav:hover {
            background-color: var(--sidebar-hover);
            color: var(--primary-color);
            border-color: var(--primary-color);
        }

        /* Light Mode Active / Click */
        .btn-cal-nav:active {
            background-color: var(--primary-color) !important;
            color: #ffffff !important;
            border-color: var(--primary-color) !important;
        }

        /* Dark Mode Styling (Clean Static, No Translate/Scale Animation) */
        body.dark .btn-cal-nav,
        [data-theme="dark"] .btn-cal-nav {
            background-color: rgba(255, 255, 255, 0.05);
            border-color: var(--border-color);
            color: var(--text-main);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
        }

        body.dark .btn-cal-nav:hover,
        [data-theme="dark"] .btn-cal-nav:hover {
            background-color: rgba(56, 189, 248, 0.15);
            color: #38bdf8;
            border-color: #38bdf8;
        }

        body.dark .btn-cal-nav:active,
        [data-theme="dark"] .btn-cal-nav:active {
            background-color: #38bdf8 !important;
            color: #0f172a !important;
            border-color: #38bdf8 !important;
        }

        @media (prefers-color-scheme: dark) {
            :root:not([data-theme="light"]):not(.dark) .btn-cal-nav {
                background-color: rgba(255, 255, 255, 0.05);
                border-color: var(--border-color);
                color: var(--text-main);
            }
            :root:not([data-theme="light"]):not(.dark) .btn-cal-nav:hover {
                background-color: rgba(56, 189, 248, 0.15);
                color: #38bdf8;
                border-color: #38bdf8;
            }
            :root:not([data-theme="light"]):not(.dark) .btn-cal-nav:active {
                background-color: #38bdf8 !important;
                color: #0f172a !important;
                border-color: #38bdf8 !important;
            }
        }

        /* ==================================================== */
        /* --- KASIR 3-DOTS MENU (LIGHT & DARK ADAPTIVE) --- */
        /* ==================================================== */
        .btn-kasir-dots {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 36px;
            height: 36px;
            border-radius: 10px;
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            font-size: 1.1rem;
            padding: 0;
            cursor: pointer;
            outline: none;
            transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
        }

        .btn-kasir-dots::after {
            display: none !important;
        }

        .btn-kasir-dots:hover,
        .btn-kasir-dots:focus {
            background-color: var(--sidebar-hover);
            border-color: var(--brand-green);
            color: var(--brand-green);
        }

        .btn-kasir-dots:active,
        .btn-kasir-dots[aria-expanded="true"],
        .btn-kasir-dots.show {
            background-color: var(--badge-bg) !important;
            border-color: var(--brand-green) !important;
            color: var(--brand-green) !important;
            box-shadow: 0 0 0 2px rgba(54, 116, 181, 0.25);
        }

        body.dark .btn-kasir-dots[aria-expanded="true"],
        [data-theme="dark"] .btn-kasir-dots[aria-expanded="true"],
        body.dark .btn-kasir-dots.show,
        [data-theme="dark"] .btn-kasir-dots.show {
            box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.35);
        }

        .kasir-dropdown-menu {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            box-shadow: var(--card-shadow);
            color: var(--text-main);
            z-index: 1050;
        }

        .kasir-dropdown-menu .dropdown-item {
            color: var(--text-main);
            font-weight: 500;
            transition: background-color 0.15s ease, color 0.15s ease;
        }

        .kasir-dropdown-menu .dropdown-item:hover,
        .kasir-dropdown-menu .dropdown-item:focus {
            background-color: var(--table-hover);
            color: var(--brand-green);
        }

        .kasir-dropdown-menu .dropdown-item.active,
        .kasir-dropdown-menu .dropdown-item:active {
            background-color: var(--brand-green) !important;
            color: #ffffff !important;
            font-weight: 600;
        }

        .kasir-dropdown-menu .dropdown-item.active i.bi-check-lg {
            color: #ffffff !important;
        }

        /* ==================================================== */
        /* --- KASIR & ACTION BUTTONS (RELOAD, PRINT, ACTIONS) --- */
        /* ==================================================== */
        .btn-kasir-action,
        .btn-action-icon {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 38px;
            height: 38px;
            border-radius: 10px;
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            font-size: 1.05rem;
            padding: 0;
            cursor: pointer;
            outline: none;
            user-select: none;
            transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
        }

        .btn-kasir-action:hover,
        .btn-action-icon:hover {
            background-color: var(--sidebar-hover);
            border-color: var(--primary-color);
            color: var(--primary-color);
        }

        /* Reload Icon Button */
        .btn-kasir-reload,
        .btn-reload-icon {
            color: var(--text-main);
            border-color: var(--border-color);
        }

        .btn-kasir-reload:hover,
        .btn-reload-icon:hover {
            background-color: rgba(16, 185, 129, 0.12) !important;
            border-color: #10b981 !important;
            color: #10b981 !important;
        }

        .btn-kasir-reload:active,
        .btn-reload-icon:active {
            background-color: #10b981 !important;
            color: #ffffff !important;
            border-color: #10b981 !important;
        }

        /* Print Icon Button */
        .btn-kasir-print,
        .btn-print-icon {
            color: var(--text-main);
            border-color: var(--border-color);
        }

        .btn-kasir-print:hover,
        .btn-print-icon:hover {
            background-color: rgba(59, 130, 246, 0.12) !important;
            border-color: #3b82f6 !important;
            color: #2563eb !important;
        }

        .btn-kasir-print:active,
        .btn-print-icon:active {
            background-color: #3b82f6 !important;
            color: #ffffff !important;
            border-color: #3b82f6 !important;
        }

        /* Dark Mode Reload & Print States */
        body.dark .btn-kasir-reload:hover,
        [data-theme="dark"] .btn-kasir-reload:hover {
            background-color: rgba(16, 185, 129, 0.2) !important;
            border-color: #34d399 !important;
            color: #34d399 !important;
            box-shadow: 0 0 10px rgba(52, 211, 153, 0.25);
        }

        body.dark .btn-kasir-reload:active,
        [data-theme="dark"] .btn-kasir-reload:active {
            background-color: #059669 !important;
            color: #ffffff !important;
            border-color: #34d399 !important;
        }

        body.dark .btn-kasir-print:hover,
        [data-theme="dark"] .btn-kasir-print:hover {
            background-color: rgba(56, 189, 248, 0.2) !important;
            border-color: #38bdf8 !important;
            color: #38bdf8 !important;
            box-shadow: 0 0 10px rgba(56, 189, 248, 0.25);
        }

        body.dark .btn-kasir-print:active,
        [data-theme="dark"] .btn-kasir-print:active {
            background-color: #0284c7 !important;
            color: #ffffff !important;
            border-color: #38bdf8 !important;
        }

        /* Light and Dark mode adaptation for outline buttons (e.g. btn-outline-secondary, btn-outline-info, btn-outline-warning, btn-outline-danger) */
        .btn-outline-secondary {
            color: var(--text-main);
            border-color: var(--border-color);
        }
        .btn-outline-secondary:hover {
            background-color: var(--sidebar-hover) !important;
            border-color: var(--primary-color) !important;
            color: var(--primary-color) !important;
        }
        .btn-outline-secondary:active {
            background-color: var(--primary-color) !important;
            color: #ffffff !important;
            border-color: var(--primary-color) !important;
        }

        body.dark .btn-outline-secondary:hover,
        [data-theme="dark"] .btn-outline-secondary:hover {
            background-color: rgba(255, 255, 255, 0.08) !important;
            border-color: rgba(255, 255, 255, 0.3) !important;
            color: #f1f5f9 !important;
        }

        /* Legend chips in Savings Dashboard */
        .savings-legend-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
            border: 1.5px solid transparent;
            transition: all 0.2s ease;
            user-select: none;
        }

        .savings-legend-chip.chip-sisa {
            background-color: rgba(46, 125, 50, 0.1);
            color: #2e7d32;
            border-color: rgba(46, 125, 50, 0.3);
        }
        .savings-legend-chip.chip-sisa:hover,
        .savings-legend-chip.chip-sisa.active {
            background-color: #2e7d32;
            color: #ffffff;
            border-color: #2e7d32;
            box-shadow: 0 2px 8px rgba(46, 125, 50, 0.35);
        }

        .savings-legend-chip.chip-masuk {
            background-color: rgba(245, 158, 11, 0.12);
            color: #d97706;
            border-color: rgba(245, 158, 11, 0.3);
        }
        .savings-legend-chip.chip-masuk:hover,
        .savings-legend-chip.chip-masuk.active {
            background-color: #f59e0b;
            color: #ffffff;
            border-color: #f59e0b;
            box-shadow: 0 2px 8px rgba(245, 158, 11, 0.35);
        }

        .savings-legend-chip.chip-keluar {
            background-color: rgba(198, 40, 40, 0.1);
            color: #c62828;
            border-color: rgba(198, 40, 40, 0.3);
        }
        .savings-legend-chip.chip-keluar:hover,
        .savings-legend-chip.chip-keluar.active {
            background-color: #c62828;
            color: #ffffff;
            border-color: #c62828;
            box-shadow: 0 2px 8px rgba(198, 40, 40, 0.35);
        }

        body.dark .savings-legend-chip.chip-sisa {
            color: #4ade80;
            border-color: rgba(74, 222, 128, 0.35);
            background-color: rgba(74, 222, 128, 0.12);
        }
        body.dark .savings-legend-chip.chip-masuk {
            color: #fbbf24;
            border-color: rgba(251, 191, 36, 0.35);
            background-color: rgba(251, 191, 36, 0.12);
        }
        body.dark .savings-legend-chip.chip-keluar {
            color: #f87171;
            border-color: rgba(248, 113, 113, 0.35);
            background-color: rgba(248, 113, 113, 0.12);
        }

        /* ==================================================== */
        /* --- SHARE SCREEN (GOOGLE DRIVE CLOUD SYNC) --- */
        /* ==================================================== */
        .share-header-banner {
            background-color: rgba(54, 116, 181, 0.08);
            border: 1px solid rgba(54, 116, 181, 0.2);
            border-radius: 12px;
            padding: 10px 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
            transition: all 0.2s ease;
        }
        body.dark .share-header-banner,
        [data-theme="dark"] .share-header-banner {
            background-color: rgba(56, 189, 248, 0.1);
            border-color: rgba(56, 189, 248, 0.25);
        }
        .share-icon-circle {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background-color: rgba(54, 116, 181, 0.15);
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--primary-color);
            font-size: 1.05rem;
            flex-shrink: 0;
        }
        body.dark .share-icon-circle,
        [data-theme="dark"] .share-icon-circle {
            background-color: rgba(56, 189, 248, 0.2);
            color: #38bdf8;
        }
        .share-btn-action {
            height: 44px;
            border-radius: 10px;
            font-weight: 700;
            font-size: 13px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            transition: transform 0.15s ease, box-shadow 0.15s ease;
        }
        .share-btn-action:active {
            transform: scale(0.98);
        }
        .share-filter-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 14px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
            border: 1px solid var(--border-color);
            background-color: var(--card-bg);
            color: var(--text-muted);
            transition: all 0.15s ease;
            user-select: none;
            white-space: nowrap;
        }
        .share-filter-chip:hover {
            border-color: var(--primary-color);
            color: var(--text-main);
        }
        .share-filter-chip.active {
            background-color: rgba(54, 116, 181, 0.15);
            color: var(--primary-color);
            border-color: var(--primary-color);
            font-weight: 700;
        }
        body.dark .share-filter-chip.active,
        [data-theme="dark"] .share-filter-chip.active {
            background-color: rgba(56, 189, 248, 0.2);
            color: #38bdf8;
            border-color: #38bdf8;
        }
        .share-selection-bar {
            background-color: rgba(54, 116, 181, 0.15);
            border: 1px solid rgba(54, 116, 181, 0.3);
            border-radius: 10px;
            padding: 8px 14px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 8px;
            animation: fadeIn 0.2s ease;
        }
        body.dark .share-selection-bar,
        [data-theme="dark"] .share-selection-bar {
            background-color: rgba(56, 189, 248, 0.18);
            border-color: rgba(56, 189, 248, 0.35);
        }
        .share-file-list-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            overflow: hidden;
            box-shadow: var(--card-shadow);
        }
        .share-file-row {
            display: flex;
            align-items: center;
            padding: 10px 14px;
            border-bottom: 1px solid var(--border-color);
            transition: background-color 0.15s ease;
            gap: 12px;
            user-select: none;
        }
        .share-file-row:last-child {
            border-bottom: none;
        }
        .share-file-row:hover {
            background-color: var(--table-hover);
        }
        .share-file-row.selected {
            background-color: rgba(54, 116, 181, 0.08);
        }
        body.dark .share-file-row.selected,
        [data-theme="dark"] .share-file-row.selected {
            background-color: rgba(56, 189, 248, 0.12);
        }
        .share-file-icon-badge {
            width: 38px;
            height: 38px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.15rem;
            flex-shrink: 0;
            border: 1px solid transparent;
        }
        .share-row-btn {
            width: 36px;
            height: 36px;
            border-radius: 8px;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-size: 1.1rem;
            cursor: pointer;
            transition: all 0.15s ease;
        }
        .share-row-btn:hover {
            background-color: var(--sidebar-hover);
            color: var(--text-main);
        }
        .share-row-btn.btn-open:hover {
            color: var(--primary-color);
            background-color: rgba(54, 116, 181, 0.1);
        }
        .share-row-btn.btn-delete:hover {
            color: #ef4444;
            background-color: rgba(239, 68, 68, 0.1);
        }

        /* ==================================================== */
        /* --- KASIR SCANNER & THERMAL RECEIPT PRINTING --- */
        /* ==================================================== */
        .scanner-viewport-wrapper {
            position: relative;
            width: 100%;
            max-width: 480px;
            margin: 0 auto;
            border-radius: 16px;
            overflow: hidden;
            background: #000;
            aspect-ratio: 4/3;
            box-shadow: 0 8px 24px rgba(0,0,0,0.4);
        }
        .scanner-video-feed {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
        }
        .scanner-laser-line {
            position: absolute;
            top: 50%;
            left: 6%;
            width: 88%;
            height: 3px;
            background: #ef4444;
            box-shadow: 0 0 12px #ef4444, 0 0 24px #ef4444;
            animation: scannerSweep 2s infinite alternate ease-in-out;
            z-index: 10;
        }
        @keyframes scannerSweep {
            0% { top: 18%; }
            100% { top: 82%; }
        }
        .scanner-target-box {
            position: absolute;
            top: 18%;
            left: 6%;
            width: 88%;
            height: 64%;
            border: 2px solid #22c55e;
            border-radius: 12px;
            pointer-events: none;
            box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
            z-index: 5;
        }
        .scanner-status-pill {
            position: absolute;
            top: 12px;
            left: 50%;
            transform: translateX(-50%);
            background: rgba(0,0,0,0.8);
            color: #4ade80;
            padding: 5px 16px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: bold;
            z-index: 12;
            border: 1px solid rgba(74, 222, 128, 0.4);
            white-space: nowrap;
        }

        /* Thermal Struk Belanja Print Styling */
        @media print {
            body * {
                visibility: hidden !important;
            }
            #thermal-receipt-printable, #thermal-receipt-printable * {
                visibility: visible !important;
            }
            #thermal-receipt-printable {
                position: fixed !important;
                left: 0 !important;
                top: 0 !important;
                width: 58mm !important;
                margin: 0 !important;
                padding: 4mm !important;
                background: #ffffff !important;
                color: #000000 !important;
                font-family: 'Courier New', Courier, monospace !important;
                font-size: 11px !important;
                line-height: 1.3 !important;
                display: block !important;
                z-index: 999999 !important;
            }
            @page {
                size: 58mm auto;
                margin: 0;
            }
        }
        .struk-preview-box {
            background: #ffffff;
            color: #000000;
            font-family: 'Courier New', Courier, monospace;
            font-size: 12px;
            line-height: 1.35;
            padding: 20px;
            border-radius: 8px;
            max-width: 320px;
            margin: 0 auto;
            box-shadow: 0 6px 25px rgba(0,0,0,0.3);
            border: 1px dashed #bbb;
        }
        .struk-divider {
            border-top: 1px dashed #000000;
            margin: 8px 0;
        }
        .struk-item-row {
            display: flex;
            justify-content: space-between;
            margin-bottom: 2px;
        }
        .struk-bold {
            font-weight: bold;
        }

        /* ==================================================== */
        /* --- PEMBUKUAN (LIGHT & DARK THEME & ANIMATIONS) --- */
        /* ==================================================== */
        .pembukuan-menu-btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 6px 12px;
            border-radius: 10px;
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            font-size: 0.85rem;
            font-weight: 600;
            cursor: pointer;
            outline: none;
            box-shadow: var(--card-shadow);
            transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);
            user-select: none;
        }

        .pembukuan-menu-btn .bi-three-dots-vertical {
            font-size: 1.05rem;
            transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), color 0.25s ease;
            display: inline-block;
        }

        .pembukuan-menu-btn:hover {
            background-color: var(--sidebar-hover);
            border-color: var(--brand-green);
            color: var(--brand-green);
            transform: translateY(-2px);
            box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
        }

        .pembukuan-menu-btn:hover .bi-three-dots-vertical {
            transform: rotate(90deg) scale(1.2);
            color: var(--brand-green);
        }

        .pembukuan-menu-btn:active,
        .pembukuan-menu-btn.show,
        .pembukuan-menu-btn[aria-expanded="true"] {
            background-color: var(--badge-bg) !important;
            border-color: var(--brand-green) !important;
            color: var(--brand-green) !important;
            transform: scale(0.96);
        }

        .pembukuan-dropdown-menu {
            background-color: var(--card-bg) !important;
            border: 1px solid var(--border-color) !important;
            box-shadow: var(--card-shadow), 0 12px 28px rgba(0,0,0,0.18) !important;
            border-radius: 14px !important;
            min-width: 250px !important;
            padding: 8px !important;
            color: var(--text-main) !important;
            z-index: 1050;
        }

        .pembukuan-dropdown-menu .dropdown-header {
            color: var(--text-sub) !important;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 0.6px;
            padding: 6px 12px;
        }

        .pembukuan-dropdown-menu .dropdown-item {
            color: var(--text-main) !important;
            border-radius: 8px;
            padding: 8px 12px;
            font-size: 0.85rem;
            font-weight: 500;
            transition: all 0.2s ease;
        }

        .pembukuan-dropdown-menu .dropdown-item:hover,
        .pembukuan-dropdown-menu .dropdown-item:focus {
            background-color: var(--sidebar-hover) !important;
            color: var(--brand-green) !important;
            transform: translateX(4px);
        }

        .pembukuan-dropdown-menu .dropdown-item.active {
            background-color: var(--badge-bg) !important;
            color: var(--brand-green) !important;
            font-weight: 700;
        }

        .pembukuan-dropdown-menu .dropdown-divider {
            border-top: 1px solid var(--border-color) !important;
            opacity: 0.6;
            margin: 6px 0;
        }

        .pembukuan-quick-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 20px;
            padding: 22px;
            box-shadow: var(--card-shadow);
            transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.25s ease, border-color 0.25s ease;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            height: 100%;
        }

        .pembukuan-quick-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 12px 28px rgba(0, 0, 0, 0.14);
            border-color: var(--brand-green);
        }

        .pembukuan-stat-box {
            background-color: var(--table-hover);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 12px 14px;
            margin-bottom: 16px;
            transition: background-color 0.25s ease, border-color 0.25s ease;
        }

        .pembukuan-metric-card {
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            padding: 16px;
            box-shadow: var(--card-shadow);
            transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.22s ease, border-color 0.22s ease;
        }

        .pembukuan-metric-card.clickable {
            cursor: pointer;
        }

        .pembukuan-metric-card.clickable:hover {
            transform: translateY(-4px);
            box-shadow: 0 10px 22px rgba(0, 0, 0, 0.12);
            border-color: var(--brand-green);
        }
    </style>
</head>
<body>
    <!-- PULL TO REFRESH INDICATOR -->
    <div id="pullToRefreshIndicator">
        <i class="bi bi-arrow-down-circle-fill text-success fs-5" id="ptrArrow"></i>
        <div class="spinner-border spinner-border-sm text-success d-none" id="ptrSpinner" role="status"></div>
        <span id="ptrText" class="fw-semibold small text-secondary">Tarik ke bawah untuk memuat ulang...</span>
    </div>

    <!-- AUTH QR CODE VERIFICATION & SECURITY OVERLAY -->
    <div id="authOverlay" class="d-none">
        <div class="auth-card p-4 p-md-5">
            <div class="d-flex align-items-center justify-content-between mb-4">
                <span class="auth-card-header-badge px-3 py-2 rounded-pill fw-bold small d-inline-flex align-items-center" id="authBadgeStatus">
                    <i class="bi bi-shield-lock-fill me-1" id="authBadgeIcon"></i> <span id="authBadgeText">Proteksi Web Connect</span>
                </span>
                <button type="button" class="auth-theme-toggle" onclick="toggleTheme()" id="authThemeToggleBtn" title="Ganti Mode Terang / Gelap" aria-label="Ganti Tema">
                    <i class="bi bi-sun-fill text-warning" id="authThemeIcon"></i>
                    <span id="authThemeLabel">Mode Terang</span>
                </button>
            </div>

            <!-- 1. PENDING VERIFICATION VIEW (PIN & QR CODE) -->
            <div id="authPendingView">
                <div class="row g-4 align-items-center">
                    <div class="col-12 col-md-7">
                        <h3 class="fw-black mb-2 auth-title">Verifikasi Akses PC</h3>
                        <p class="auth-subtitle small mb-4">Untuk membuka dashboard Singghara Web, silakan verifikasi melalui notifikasi / aplikasi <b>My Singghara</b> di smartphone Anda.</p>
                        
                        <div class="d-flex flex-column gap-3 mb-4">
                            <div class="auth-step-item d-flex align-items-start gap-3">
                                <div class="auth-step-circle">1</div>
                                <div>
                                    <span class="auth-step-title small d-block">Konfirmasi Notifikasi di Smartphone</span>
                                    <small class="auth-step-desc">Ketuk notifikasi status bar atau buka pop-up verifikasi di HP.</small>
                                </div>
                            </div>
                            <div class="auth-step-item d-flex align-items-start gap-3">
                                <div class="auth-step-circle">2</div>
                                <div>
                                    <span class="auth-step-title small d-block">Pindai QR Barcode / Cek PIN</span>
                                    <small class="auth-step-desc">Pilih <b>"Ya"</b> di HP untuk melihat PIN atau langsung memindai QR Code di samping.</small>
                                </div>
                            </div>
                            <div class="auth-step-item d-flex align-items-start gap-3">
                                <div class="auth-step-circle">3</div>
                                <div>
                                    <span class="auth-step-title small d-block">Dashboard Otomatis Terbuka</span>
                                    <small class="auth-step-desc">Browser PC akan mendeteksi persetujuan dan membuka dashboard secara otomatis.</small>
                                </div>
                            </div>
                        </div>

                        <div class="d-flex align-items-center gap-2 auth-subtitle small" id="authPollStatus">
                            <div class="spinner-grow spinner-grow-sm text-success" role="status"></div>
                            <span id="authPollStatusText">Menunggu konfirmasi dari smartphone...</span>
                        </div>

                        <div class="mt-4 p-3 rounded-4 bg-success bg-opacity-10 border border-success border-opacity-25 text-start">
                            <div class="d-flex align-items-center gap-2 mb-1 text-success fw-bold small">
                                <i class="bi bi-hdd-network"></i> Bebas Kuota Internet (Akses Jaringan Lokal)
                            </div>
                            <small class="auth-subtitle d-block" style="font-size: 11px; line-height: 1.4;">
                                Web Connect terhubung langsung melalui jaringan lokal Wi-Fi / Hotspot tanpa menyedot kuota data. Data tersimpan secara otomatis di LocalStorage browser untuk akses offline.
                            </small>
                        </div>
                    </div>

                    <div class="col-12 col-md-5 text-center">
                        <div class="qr-box mb-3" id="authQrContainer">
                            <div id="authQrCode">
                                <div class="spinner-border text-success"></div>
                            </div>
                        </div>
                        <div>
                            <small class="auth-subtitle d-block mb-1 fw-semibold">Atau Gunakan Kode PIN di Aplikasi:</small>
                            <span class="qr-pin-badge d-inline-block" id="authPinDisplay">------</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 2. ACCESS REJECTED VIEW (ANIMASI BERGERAK AKSES DITOLAK) -->
            <div id="authRejectedView" class="auth-rejected-container d-none">
                <div class="auth-rejected-shield-wrapper">
                    <div class="auth-rejected-pulse-ring"></div>
                    <div class="auth-rejected-pulse-ring-2"></div>
                    <div class="auth-rejected-icon-badge">
                        <i class="bi bi-shield-x"></i>
                    </div>
                </div>

                <h3 class="auth-rejected-title">Akses Ditolak</h3>
                <p class="auth-rejected-desc">
                    Permintaan akses Singghara Web PC telah <b>ditolak oleh pemilik smartphone</b>. Akses ke data dan sistem diblokir untuk menjaga privasi dan keamanan Anda.
                </p>

                <div class="auth-rejected-box">
                    <div class="d-flex align-items-center gap-2 text-danger fw-bold small mb-1">
                        <i class="bi bi-exclamation-octagon-fill"></i> Proteksi Keamanan Aktif
                    </div>
                    <small class="text-secondary d-block" style="font-size: 12px; line-height: 1.4;">
                        Jika Anda adalah pemilik perangkat ini, Anda dapat meminta izin verifikasi kembali ke smartphone dengan menekan tombol di bawah.
                    </small>
                </div>

                <div class="d-flex justify-content-center gap-2 mt-2">
                    <button type="button" class="btn btn-retry-auth" onclick="requestRetryAuth()">
                        <i class="bi bi-arrow-clockwise"></i> Minta Izin Ulang ke Smartphone
                    </button>
                </div>
            </div>
        </div>
    </div>
    <!-- HEADER NAVBAR -->
    <nav class="navbar navbar-main sticky-top">
        <div class="container-fluid px-3 py-1">
            <div class="d-flex align-items-center gap-3">
                <button class="btn-toggle-menu" id="toggleSidebarBtn" onclick="toggleSidebar()" title="Toggle Menu" aria-label="Toggle Menu">
                    <i class="bi bi-list"></i>
                </button>
                <div>
                    <h5 class="mb-0 fw-bold">Singghara Web</h5>
                    <small class="text-secondary d-none d-sm-inline">Dashboard Web Synchronized PC</small>
                </div>
            </div>
        </div>
    </nav>

    <!-- SIDEBAR -->
    <div class="sidebar-wrapper" id="appSidebar">
        <div class="sidebar-menu-scrollable">
            <div class="nav flex-column gap-1">
                <!-- DASHBOARD MENU -->
                <div class="nav-link-item active" onclick="switchTab('dashboard', this)" data-title="Dashboard">
                    <i class="bi bi-grid-1x2 fs-5"></i>
                    <span class="nav-text">Dashboard</span>
                </div>

                <!-- MENU COMPANY -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-company')" title="Company">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-building fs-5 sidebar-group-icon" style="color: #0F766E;"></i>
                            <span class="nav-text">Company</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-company"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-company" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small" style="color: #0F766E;">
                            <i class="bi bi-building me-1"></i> Company
                        </div>
                        <div class="nav-link-item" onclick="switchTab('karyawan_murid', this)" data-title="Data">
                            <i class="bi bi-person-badge-fill fs-5" style="color: #0F766E;"></i>
                            <span class="nav-text">Data</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('absensi', this)" data-title="Absensi">
                            <i class="bi bi-calendar-check-fill fs-5" style="color: #0F766E;"></i>
                            <span class="nav-text">Absensi</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU MY APLIKASI -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-my-apps')" title="My Aplikasi">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-grid-3x3-gap-fill text-warning fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">My Aplikasi</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-my-apps"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-my-apps" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-warning">
                            <i class="bi bi-grid-3x3-gap-fill me-1"></i> My Aplikasi
                        </div>
                        <div class="nav-link-item" onclick="switchTab('my_apps', this)">
                            <i class="bi bi-grid-3x3-gap-fill fs-5 text-warning"></i>
                            <span class="nav-text">Semua Aplikasi</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('karyawan_murid', this)">
                            <i class="bi bi-person-badge-fill fs-5" style="color: #0F766E;"></i>
                            <span class="nav-text">Company</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('block', this)">
                            <i class="bi bi-grid-fill fs-5" style="color: #2C5E6A;"></i>
                            <span class="nav-text">Block Puzzle</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('brick_game', this)">
                            <i class="bi bi-controller fs-5" style="color: #306230;"></i>
                            <span class="nav-text">Brick Game</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('quran', this)">
                            <i class="bi bi-journal-bookmark-fill fs-5" style="color: #00796B;"></i>
                            <span class="nav-text">Al-Qur\'an</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('pembukuan', this)">
                            <i class="bi bi-graph-up-arrow fs-5" style="color: #E65100;"></i>
                            <span class="nav-text">Pembukuan</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('musik', this)">
                            <i class="bi bi-music-note-beamed fs-5" style="color: #E91E63;"></i>
                            <span class="nav-text">Musik</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('spinner', this)">
                            <i class="bi bi-arrow-repeat fs-5" style="color: #8338EC;"></i>
                            <span class="nav-text">Spinner</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU MANAJEMEN KEUANGAN -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-manajemen')" title="Manajemen Keuangan">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-wallet2 text-success fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">Manajemen Keuangan</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-manajemen"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-manajemen" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-success">
                            <i class="bi bi-wallet2 me-1"></i> Manajemen Keuangan
                        </div>
                        <div class="nav-link-item" onclick="switchTab('kasir', this)">
                            <i class="bi bi-shop fs-5"></i>
                            <span class="nav-text">Halaman Kasir</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('shopping', this)">
                            <i class="bi bi-cart3 fs-5"></i>
                            <span class="nav-text">Daftar Belanja</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('savings', this)">
                            <i class="bi bi-wallet2 fs-5"></i>
                            <span class="nav-text">Catatan Tabungan</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU LAPORAN KEUANGAN -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-laporan-keuangan')" title="Laporan Keuangan">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-file-earmark-spreadsheet text-info fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">Laporan Keuangan</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-laporan-keuangan"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-laporan-keuangan" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-info">
                            <i class="bi bi-file-earmark-spreadsheet me-1"></i> Laporan Keuangan
                        </div>
                        <div class="nav-link-item" onclick="switchTab('jurnal_umum', this)">
                            <i class="bi bi-journal-text fs-5" style="color: #4CAF50;"></i>
                            <span class="nav-text">Jurnal Umum</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('buku_besar', this)">
                            <i class="bi bi-book fs-5" style="color: #2196F3;"></i>
                            <span class="nav-text">Buku Besar</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('neraca_saldo', this)">
                            <i class="bi bi-calculator fs-5" style="color: #FF9800;"></i>
                            <span class="nav-text">Neraca Saldo</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('jurnal_penyesuaian', this)">
                            <i class="bi bi-pencil-square fs-5" style="color: #9C27B0;"></i>
                            <span class="nav-text">Jurnal Penyesuaian</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('neraca_saldo_disesuaikan', this)">
                            <i class="bi bi-table fs-5" style="color: #00BCD4;"></i>
                            <span class="nav-text">Neraca S. Disesuaikan</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('laba_rugi', this)">
                            <i class="bi bi-graph-up fs-5" style="color: #E91E63;"></i>
                            <span class="nav-text">Laba Rugi</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('jurnal_penutup', this)">
                            <i class="bi bi-lock-fill fs-5" style="color: #795548;"></i>
                            <span class="nav-text">Jurnal Penutup</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('neraca_saldo_setelah_tutup', this)">
                            <i class="bi bi-file-earmark-ruled fs-5" style="color: #607D8B;"></i>
                            <span class="nav-text">Neraca S. Setelah Tutup</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU NOTE -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-rekap')" title="Note">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-journal-text text-primary fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">Note</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-rekap"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-rekap" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-primary">
                            <i class="bi bi-journal-text me-1"></i> Note
                        </div>
                        <div class="nav-link-item" onclick="switchTab('notes', this)">
                            <i class="bi bi-journal-text fs-5"></i>
                            <span class="nav-text">Catatan</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('jadwal', this)">
                            <i class="bi bi-clock-history fs-5"></i>
                            <span class="nav-text">Jadwal Harian</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- SHARE MENU -->
                <div class="nav-link-item" onclick="switchTab('share', this)" data-title="Share">
                    <i class="bi bi-share fs-5"></i>
                    <span class="nav-text">Share</span>
                </div>
            </div>
        </div>

        <!-- SIDEBAR BOTTOM FOOTER (PROFIL & LOGOUT) -->
        <div class="sidebar-footer" id="sidebarFooter">
            <div class="sidebar-user-section">
                <div class="sidebar-profile-info" onclick="showProfileModal()" role="button" title="Profil Pengguna" data-title="Profil Pengguna">
                    <div class="sidebar-avatar-icon" id="sidebarAvatarContainer">
                        <i class="bi bi-person-fill"></i>
                    </div>
                    <div class="sidebar-user-meta nav-text">
                        <span class="sidebar-user-name fw-bold" id="sidebarUserName">Pengguna</span>
                        <small class="sidebar-user-status"><i class="bi bi-circle-fill text-success" style="font-size: 7px;"></i> Terhubung</small>
                    </div>
                </div>
                <button type="button" class="btn-sidebar-logout" onclick="handleWebLogout()" title="Keluar / Putuskan Sesi PC" aria-label="Keluar">
                    <i class="bi bi-box-arrow-right"></i>
                </button>
            </div>
        </div>
    </div>

    <!-- MAIN CONTENT CONTAINER -->
    <div class="content-wrapper">
        <div id="tab-content"></div>
    </div>

    <!-- USER PROFILE MODAL -->
    <div class="modal fade" id="profileModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-sm">
            <div class="modal-content card-custom p-3" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: 0 12px 35px rgba(0,0,0,0.35);">
                <div class="modal-header border-bottom-0 pb-0">
                    <h5 class="modal-title fw-bold text-main">Profil Web Connect</h5>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body text-center pt-2">
                    <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3 shadow-sm" id="profileModalAvatarContainer" style="width: 72px; height: 72px; background: linear-gradient(135deg, #2A84EB, #1456A5); color: #ffffff; font-size: 2.2rem; overflow: hidden; border: 2px solid rgba(255,255,255,0.25);">
                        <i class="bi bi-person-fill"></i>
                    </div>
                    <h6 class="fw-bold mb-1 text-main" id="profileModalName">Pengguna Singghara</h6>
                    <span class="badge bg-success bg-opacity-25 text-success rounded-pill px-3 py-1 mb-3">
                        <i class="bi bi-shield-check me-1"></i> Sesi PC Terverifikasi
                    </span>
                    
                    <div class="p-2 mb-3 rounded-3 text-start small" style="background-color: var(--sidebar-bg); border: 1px solid var(--border-color);">
                        <div class="d-flex justify-content-between mb-1">
                            <span class="text-secondary">Aplikasi:</span>
                            <span class="fw-semibold text-main">My Singghara Suite</span>
                        </div>
                        <div class="d-flex justify-content-between mb-1">
                            <span class="text-secondary">Koneksi:</span>
                            <span class="fw-semibold text-success"><i class="bi bi-wifi me-1"></i> Sinkron Otomatis</span>
                        </div>
                        <div class="d-flex justify-content-between">
                            <span class="text-secondary">Tema:</span>
                            <span class="fw-semibold text-main" id="profileModalTheme">Terang / Gelap</span>
                        </div>
                    </div>

                    <div class="d-flex flex-column gap-2">
                        <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3 fw-semibold d-flex align-items-center justify-content-center gap-2" onclick="toggleTheme(); updateProfileModalTheme();">
                            <i class="bi bi-circle-half"></i> Ganti Tema Tampilan
                        </button>
                        <button type="button" class="btn btn-outline-danger w-100 py-2 rounded-3 fw-bold d-flex align-items-center justify-content-center gap-2" onclick="closeProfileModalAndLogout()">
                            <i class="bi bi-box-arrow-right"></i> Keluar / Putuskan Sesi
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- GLOBAL CONFIRMATION MODAL -->
    <div class="modal fade" id="customConfirmModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-sm">
            <div class="modal-content card-custom text-center p-3" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: 0 12px 35px rgba(0,0,0,0.35);">
                <div class="modal-body p-3">
                    <div id="confirmIconContainer" class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 64px; height: 64px; background: rgba(220, 53, 69, 0.12); color: #dc3545; font-size: 1.85rem; transition: transform 0.2s ease;">
                        <i class="bi bi-exclamation-triangle-fill" id="confirmIcon"></i>
                    </div>
                    <h5 class="fw-bold mb-2 text-main" id="confirmTitle">Konfirmasi Hapus</h5>
                    <p class="text-secondary small mb-4 px-1" id="confirmMessage" style="line-height: 1.5;"></p>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-outline-secondary w-50 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal" id="confirmBtnCancel">
                            Batal
                        </button>
                        <button type="button" class="btn btn-danger w-50 py-2 rounded-3 fw-bold" id="confirmBtnAction">
                            <i class="bi bi-trash-fill me-1"></i> Hapus
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- GLOBAL MODAL -->
    <div class="modal fade" id="itemModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered modal-lg">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0">
                    <h5 class="modal-title fw-bold" id="modalTitle">Form Data</h5>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body" id="modalBody"></div>
                <div class="modal-footer border-top-0">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                    <button type="button" class="btn btn-brand" id="btnSaveModal" onclick="submitModalForm()">Simpan Data</button>
                </div>
            </div>
        </div>
    </div>

    <!-- POS CAMERA SCANNER MODAL -->
    <div class="modal fade" id="posScannerModal" tabindex="-1" data-bs-backdrop="static">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <div class="d-flex align-items-center gap-2">
                        <i class="bi bi-camera-fill text-success fs-5"></i>
                        <h5 class="modal-title fw-bold">Scan QR / Barcode Kasir</h5>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal" onclick="closeCameraScanner()"></button>
                </div>
                <div class="modal-body pt-2 text-center">
                    <div class="mb-3 d-flex justify-content-between align-items-center gap-2">
                        <select id="posCameraSelect" class="form-select form-select-sm form-select-dark" onchange="onScannerCameraChange(this.value)">
                            <option value="">Pilih Kamera (Webcam / Belakang)...</option>
                        </select>
                        <button class="btn btn-sm btn-outline-secondary" onclick="restartCameraScanner()"><i class="bi bi-arrow-repeat"></i></button>
                    </div>

                    <div class="scanner-viewport-wrapper">
                        <div class="scanner-status-pill"><i class="bi bi-broadcast me-1"></i> KAMERA AKTIF (MEMINDAI...)</div>
                        <video id="posScannerVideo" class="scanner-video-feed" playsinline autoplay muted></video>
                        <canvas id="posScannerCanvas" style="display: none;"></canvas>
                        <div class="scanner-target-box"></div>
                        <div class="scanner-laser-line"></div>
                    </div>

                    <div class="mt-3 text-secondary small">
                        Arahkan kamera ke barcode produk (EAN, UPC, Code 128, QR Code). Item akan otomatis ditambahkan ke keranjang belanja.
                    </div>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary w-100" data-bs-dismiss="modal" onclick="closeCameraScanner()">Tutup Scanner</button>
                </div>
            </div>
        </div>
    </div>

    <!-- POS STRUK RECEIPT MODAL -->
    <div class="modal fade" id="posReceiptModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <div class="d-flex align-items-center gap-2">
                        <i class="bi bi-receipt-cutoff text-success fs-5"></i>
                        <h5 class="modal-title fw-bold">Struk Belanja Kasir</h5>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-2">
                    <div class="alert alert-success d-flex align-items-center py-2 px-3 mb-3 small" role="alert">
                        <i class="bi bi-check-circle-fill me-2 fs-5"></i>
                        <div>Transaksi berhasil disimpan dan siap dicetak ke printer kasir (Kabel / Bluetooth).</div>
                    </div>

                    <div id="posReceiptPreviewContainer"></div>
                </div>
                <div class="modal-footer border-top-0 flex-column flex-sm-row gap-2">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Tutup</button>
                    <button type="button" class="btn btn-outline-info" onclick="copyReceiptText()"><i class="bi bi-clipboard me-1"></i> Salin Teks</button>
                    <button type="button" class="btn btn-brand" onclick="triggerThermalPrint()"><i class="bi bi-printer me-1"></i> Cetak Struk Belanja</button>
                </div>
            </div>
        </div>
    </div>

    <!-- THERMAL RECEIPT PRINTABLE CONTAINER (FOR @media print) -->
    <div id="thermal-receipt-printable" style="display: none;"></div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // ==========================================
        // --- OFFLINE LOCALSTORAGE CACHE ENGINE ---
        // ==========================================
        const StorageCache = {
            PREFIX: 'singghara_offline_cache_',
            set: function(key, data) {
                try {
                    const payload = {
                        timestamp: Date.now(),
                        timeStr: new Date().toLocaleTimeString('id-ID'),
                        data: data
                    };
                    localStorage.setItem(this.PREFIX + key, JSON.stringify(payload));
                    const badge = document.getElementById('cacheStorageBadge');
                    if (badge) badge.classList.remove('d-none');
                } catch(e) {
                    console.warn('LocalStorage write failed:', e);
                }
            },
            get: function(key) {
                try {
                    const raw = localStorage.getItem(this.PREFIX + key);
                    if (!raw) return null;
                    const parsed = JSON.parse(raw);
                    return (parsed && parsed.data !== undefined) ? parsed.data : parsed;
                } catch(e) {
                    return null;
                }
            },
            getTimestamp: function(key) {
                try {
                    const raw = localStorage.getItem(this.PREFIX + key);
                    if (!raw) return null;
                    const parsed = JSON.parse(raw);
                    return (parsed && parsed.timeStr) ? parsed.timeStr : null;
                } catch(e) {
                    return null;
                }
            },
            has: function(key) {
                return localStorage.getItem(this.PREFIX + key) !== null;
            }
        };

        function updateConnectionStatus(isOnline, fromCache = false) {
            const badge = document.getElementById('liveSyncStatusBadge');
            if (badge) {
                if (isOnline) {
                    badge.className = 'status-badge';
                    badge.innerHTML = '<i class="bi bi-wifi me-1 text-success"></i> Saling Terhubung';
                } else {
                    badge.className = 'status-badge bg-warning bg-opacity-25 text-warning border-warning';
                    badge.innerHTML = '<i class="bi bi-device-ssd me-1 text-warning"></i> ' + (fromCache ? 'Data Offline (LocalStorage)' : 'Mode Lokal (Offline)');
                }
            }
        }

        async function fetchApiWithCache(url, cacheKey, options = {}) {
            const key = cacheKey || url.replace(/[^a-zA-Z0-9_-]/g, '_');
            try {
                const res = await fetch(url, options);
                if (!res.ok) throw new Error('HTTP ' + res.status);
                const data = await res.json();
                StorageCache.set(key, data);
                updateConnectionStatus(true);
                return { data: data, fromCache: false };
            } catch(err) {
                console.warn('Mengakses data dari LocalStorage browser (' + key + ')...', err);
                const cached = StorageCache.get(key);
                if (cached !== null) {
                    updateConnectionStatus(false, true);
                    return { data: cached, fromCache: true };
                }
                updateConnectionStatus(false, false);
                throw err;
            }
        }

        // Safe Modal Helper for Offline & CDN-Free Operation
        function createSafeModal(element) {
            if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                try {
                    return new bootstrap.Modal(element);
                } catch(e) {}
            }
            return {
                show: function() {
                    if (element) {
                        element.classList.add('show');
                        element.style.display = 'block';
                        element.removeAttribute('aria-hidden');
                        element.setAttribute('aria-modal', 'true');
                        document.body.classList.add('modal-open');
                        let backdrop = document.getElementById('custom-modal-backdrop');
                        if (!backdrop) {
                            backdrop = document.createElement('div');
                            backdrop.id = 'custom-modal-backdrop';
                            backdrop.className = 'modal-backdrop fade show';
                            document.body.appendChild(backdrop);
                        }
                    }
                },
                hide: function() {
                    if (element) {
                        element.classList.remove('show');
                        element.style.display = 'none';
                        element.setAttribute('aria-hidden', 'true');
                        element.removeAttribute('aria-modal');
                        document.body.classList.remove('modal-open');
                        const backdrop = document.getElementById('custom-modal-backdrop');
                        if (backdrop) backdrop.remove();
                    }
                }
            };
        }

        let currentTab = 'dashboard';
        let modalInstance = createSafeModal(document.getElementById('itemModal'));
        let confirmModalInstance = null;
        let cartItems = [];

        function getConfirmModal() {
            if (!confirmModalInstance) {
                const el = document.getElementById('customConfirmModal');
                if (el) confirmModalInstance = createSafeModal(el);
            }
            return confirmModalInstance;
        }

        /**
         * Global Attractive Confirmation Modal for Web Connect
         * @param {Object} options - { title, message, confirmText, cancelText, confirmBtnClass, icon, iconColor, iconBg, onConfirm }
         */
        function showCustomConfirm(options) {
            if (typeof options === 'string') {
                options = { message: options };
            }
            const opts = Object.assign({
                title: 'Konfirmasi Tindakan',
                message: 'Apakah Anda yakin ingin melanjutkan tindakan ini?',
                confirmText: 'Hapus',
                cancelText: 'Batal',
                confirmBtnClass: 'btn-danger',
                icon: 'bi-exclamation-triangle-fill',
                iconColor: '#dc3545',
                iconBg: 'rgba(220, 53, 69, 0.12)',
                onConfirm: null
            }, options);

            const titleEl = document.getElementById('confirmTitle');
            const msgEl = document.getElementById('confirmMessage');
            const btnAction = document.getElementById('confirmBtnAction');
            const btnCancel = document.getElementById('confirmBtnCancel');
            const iconEl = document.getElementById('confirmIcon');
            const iconContainer = document.getElementById('confirmIconContainer');

            if (titleEl) titleEl.innerText = opts.title;
            if (msgEl) msgEl.innerText = opts.message;
            if (btnCancel) btnCancel.innerText = opts.cancelText;
            
            if (iconEl) iconEl.className = 'bi ' + opts.icon;
            if (iconContainer) {
                iconContainer.style.color = opts.iconColor;
                iconContainer.style.backgroundColor = opts.iconBg;
            }

            if (btnAction) {
                btnAction.className = 'btn w-50 py-2 rounded-3 fw-bold ' + opts.confirmBtnClass;
                const iconName = opts.confirmBtnClass && opts.confirmBtnClass.indexOf('danger') !== -1 ? 'bi-trash-fill' : 'bi-check-lg';
                btnAction.innerHTML = '<i class="bi ' + iconName + ' me-1"></i> ' + opts.confirmText;
                
                // Remove old listeners by replacing element or assigning onclick
                btnAction.onclick = async () => {
                    const modal = getConfirmModal();
                    if (modal) modal.hide();
                    if (typeof opts.onConfirm === 'function') {
                        try {
                            await opts.onConfirm();
                        } catch (err) {
                            console.error('Error executing confirm action:', err);
                        }
                    }
                };
            }

            const modal = getConfirmModal();
            if (modal) modal.show();
        }

        // Theme Initialization & Switching
        function initTheme() {
            const savedTheme = localStorage.getItem('theme') || 'auto';
            applyTheme(savedTheme);

            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
                if ((localStorage.getItem('theme') || 'auto') === 'auto') {
                    applyTheme('auto');
                }
            });
        }

        function applyTheme(theme) {
            let actualTheme = theme;
            if (theme === 'auto') {
                actualTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
            }
            document.documentElement.setAttribute('data-theme', actualTheme);

            const themeIcon = document.getElementById('themeIcon');
            const themeLabel = document.getElementById('themeLabel');
            if (themeIcon) {
                if (actualTheme === 'dark') {
                    themeIcon.className = 'bi bi-moon-stars-fill text-warning';
                    if (themeLabel) themeLabel.innerText = 'Gelap';
                } else {
                    themeIcon.className = 'bi bi-sun-fill text-warning';
                    if (themeLabel) themeLabel.innerText = 'Terang';
                }
            }

            const authThemeIcon = document.getElementById('authThemeIcon');
            const authThemeLabel = document.getElementById('authThemeLabel');
            if (authThemeIcon) {
                if (actualTheme === 'dark') {
                    authThemeIcon.className = 'bi bi-moon-stars-fill text-warning';
                    if (authThemeLabel) authThemeLabel.innerText = 'Mode Gelap';
                } else {
                    authThemeIcon.className = 'bi bi-sun-fill text-warning';
                    if (authThemeLabel) authThemeLabel.innerText = 'Mode Terang';
                }
            }

            if (typeof syncBrickGameWithAppTheme === 'function') {
                syncBrickGameWithAppTheme(actualTheme);
            }
        }

        function toggleTheme() {
            const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            localStorage.setItem('theme', newTheme);
            applyTheme(newTheme);
        }

        initTheme();

        // Profile Modal & Web Logout Functions
        const AVATAR_PRESETS = [
            { bg: 'linear-gradient(135deg, #2A84EB, #1456A5)', icon: 'bi-person-fill' },
            { bg: 'linear-gradient(135deg, #FF5E62, #FF9966)', icon: 'bi-emoji-smile-fill' },
            { bg: 'linear-gradient(135deg, #11998E, #38EF7D)', icon: 'bi-star-fill' },
            { bg: 'linear-gradient(135deg, #00C6FF, #0072FF)', icon: 'bi-heart-fill' },
            { bg: 'linear-gradient(135deg, #8A2387, #E94057)', icon: 'bi-palette-fill' }
        ];

        function applyUserProfileData(data) {
            if (!data) return;
            const profName = data.profileName || 'Singghara User';
            const sNameEl = document.getElementById('sidebarUserName');
            if (sNameEl) sNameEl.textContent = profName;
            const pNameEl = document.getElementById('profileModalName');
            if (pNameEl) pNameEl.textContent = profName;

            const sAvatar = document.getElementById('sidebarAvatarContainer');
            const mAvatar = document.getElementById('profileModalAvatarContainer');

            const hasCustomImage = !!(data.hasCustomImage || (data.profileAvatarIndex === -1 && data.profileImageUrl));
            const imgUrl = data.profileImageUrl || ('/api/profile/image?t=' + Date.now());

            if (hasCustomImage) {
                const imgHtmlSidebar = `<img src="${dol}{imgUrl}" alt="Avatar" class="sidebar-avatar-img" onerror="this.onerror=null; this.parentElement.innerHTML='<i class=\\'bi bi-person-fill text-white\\'></i>'; this.parentElement.style.background='linear-gradient(135deg, #2A84EB, #1456A5)';">`;
                const imgHtmlModal = `<img src="${dol}{imgUrl}" alt="Avatar" style="width:100%;height:100%;object-fit:cover;border-radius:50%;" onerror="this.onerror=null; this.parentElement.innerHTML='<i class=\\'bi bi-person-fill text-white\\'></i>'; this.parentElement.style.background='linear-gradient(135deg, #2A84EB, #1456A5)';">`;
                if (sAvatar) {
                    sAvatar.style.background = 'transparent';
                    sAvatar.innerHTML = imgHtmlSidebar;
                }
                if (mAvatar) {
                    mAvatar.style.background = 'transparent';
                    mAvatar.innerHTML = imgHtmlModal;
                }
            } else {
                const idx = (typeof data.profileAvatarIndex === 'number' && data.profileAvatarIndex >= 0) ? data.profileAvatarIndex : 0;
                const preset = AVATAR_PRESETS[idx] || AVATAR_PRESETS[0];
                if (sAvatar) {
                    sAvatar.style.background = preset.bg;
                    sAvatar.innerHTML = `<i class="bi ${dol}{preset.icon} text-white" style="font-size: 1.15rem;"></i>`;
                }
                if (mAvatar) {
                    mAvatar.style.background = preset.bg;
                    mAvatar.innerHTML = `<i class="bi ${dol}{preset.icon} text-white" style="font-size: 2.2rem;"></i>`;
                }
            }
        }

        async function refreshUserProfile() {
            try {
                const res = await fetch('/api/profile');
                if (res.ok) {
                    const data = await res.json();
                    applyUserProfileData(data);
                }
            } catch (e) {
                console.error('Failed to refresh profile:', e);
            }
        }

        let profileModalInstance = null;
        function getProfileModal() {
            if (!profileModalInstance) {
                const el = document.getElementById('profileModal');
                if (el) profileModalInstance = new bootstrap.Modal(el);
            }
            return profileModalInstance;
        }

        function showProfileModal() {
            updateProfileModalTheme();
            refreshUserProfile();
            const modal = getProfileModal();
            if (modal) modal.show();
        }

        function updateProfileModalTheme() {
            const curTheme = document.documentElement.getAttribute('data-theme') || 'light';
            const el = document.getElementById('profileModalTheme');
            if (el) el.innerText = curTheme === 'dark' ? 'Mode Gelap (Aktif)' : 'Mode Terang (Aktif)';
        }

        function closeProfileModalAndLogout() {
            const modal = getProfileModal();
            if (modal) modal.hide();
            setTimeout(() => {
                handleWebLogout();
            }, 300);
        }

        function handleWebLogout() {
            showCustomConfirm({
                title: 'Keluar dari Web Connect',
                message: 'Apakah Anda yakin ingin keluar dan memutuskan sesi akses PC ini?',
                confirmText: 'Keluar',
                cancelText: 'Batal',
                confirmBtnClass: 'btn-danger',
                icon: 'bi-box-arrow-right',
                iconColor: '#ef4444',
                iconBg: 'rgba(239, 68, 68, 0.12)',
                onConfirm: async () => {
                    let authData = null;
                    try {
                        const token = sessionStorage.getItem('singghara_auth_token') || activeAuthToken || '';
                        const res = await fetch('/api/auth', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: 'logout', token: token })
                        });
                        authData = await res.json();
                    } catch(e) {
                        console.warn('Logout request failed:', e);
                    }
                    lockWebSession(authData);
                }
            });
        }

        // Default desktop sidebar to collapsed state
        if (window.innerWidth > 768) {
            document.body.classList.add('sidebar-collapsed');
        }

        function toggleSidebar() {
            if (window.innerWidth <= 768) {
                document.body.classList.toggle('sidebar-mobile-open');
            } else {
                document.body.classList.toggle('sidebar-collapsed');
            }
        }

        function toggleSidebarGroup(grpId) {
            if (document.body.classList.contains('sidebar-collapsed')) return;
            const targetEl = document.getElementById(grpId);
            if (!targetEl) return;

            const isCurrentlyOpen = (targetEl.style.display !== 'none' && targetEl.style.display !== '');

            // Automatically close all other submenu groups (Accordion behavior)
            document.querySelectorAll('#appSidebar .sidebar-sub-items').forEach(subItem => {
                if (subItem.id !== grpId) {
                    subItem.style.display = 'none';
                    const otherChevron = document.getElementById('chevron-' + subItem.id);
                    if (otherChevron) {
                        otherChevron.classList.remove('bi-chevron-down');
                        otherChevron.classList.add('bi-chevron-right');
                    }
                }
            });

            const chevron = document.getElementById('chevron-' + grpId);
            if (isCurrentlyOpen) {
                targetEl.style.display = 'none';
                if (chevron) {
                    chevron.classList.remove('bi-chevron-down');
                    chevron.classList.add('bi-chevron-right');
                }
            } else {
                targetEl.style.display = 'block';
                if (chevron) {
                    chevron.classList.remove('bi-chevron-right');
                    chevron.classList.add('bi-chevron-down');
                }
            }
        }

        // Close mobile sidebar on backdrop tap
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768 && document.body.classList.contains('sidebar-mobile-open')) {
                const sidebar = document.getElementById('appSidebar');
                const toggleBtn = document.getElementById('toggleSidebarBtn');
                if (!sidebar.contains(e.target) && !toggleBtn.contains(e.target)) {
                    document.body.classList.remove('sidebar-mobile-open');
                }
            }
        });

        function switchTab(tab, element) {
            currentTab = tab;
            document.querySelectorAll('.nav-link-item').forEach(el => el.classList.remove('active'));
            if(element) {
                element.classList.add('active');
                const parentGroup = element.closest('.sidebar-sub-items');
                if (parentGroup && !document.body.classList.contains('sidebar-collapsed')) {
                    document.querySelectorAll('#appSidebar .sidebar-sub-items').forEach(subItem => {
                        if (subItem.id !== parentGroup.id) {
                            subItem.style.display = 'none';
                            const otherChevron = document.getElementById('chevron-' + subItem.id);
                            if (otherChevron) {
                                otherChevron.classList.remove('bi-chevron-down');
                                otherChevron.classList.add('bi-chevron-right');
                            }
                        }
                    });
                    parentGroup.style.display = 'block';
                    const parentChevron = document.getElementById('chevron-' + parentGroup.id);
                    if (parentChevron) {
                        parentChevron.classList.remove('bi-chevron-right');
                        parentChevron.classList.add('bi-chevron-down');
                    }
                }
            }
            if (window.innerWidth <= 768) {
                document.body.classList.remove('sidebar-mobile-open');
            }
            loadCurrentTab();
        }

        function switchTabByName(tabName) {
            const el = document.querySelector(`.nav-link-item[onclick*="'${dol}{tabName}'"]`);
            switchTab(tabName, el);
        }

        function loadCurrentTab() {
            if (currentTab === 'dashboard') loadDashboard();
            else if (currentTab === 'karyawan_murid') {
                if (window.setAnggotaActiveTab) window.setAnggotaActiveTab('anggota');
                else window.anggotaActiveTab = 'anggota';
                loadKaryawanMurid();
            }
            else if (currentTab === 'absensi') {
                if (window.setAnggotaActiveTab) window.setAnggotaActiveTab('presensi');
                else window.anggotaActiveTab = 'presensi';
                loadKaryawanMurid();
            }
            else if (currentTab === 'my_apps') loadMyApps();
            else if (currentTab === 'quran') loadQuran();
            else if (currentTab === 'pembukuan') loadPembukuan();
            else if (currentTab === 'musik') loadMusik();
            else if (currentTab === 'block') loadBlock();
            else if (currentTab === 'puzzle') loadPuzzle();
            else if (currentTab === 'brick_game') loadBrickGame();
            else if (currentTab === 'spinner') loadSpinner();
            else if (currentTab === 'notes') loadNotes();
            else if (currentTab === 'shopping') loadShopping();
            else if (currentTab === 'agenda') loadAgenda();
            else if (currentTab === 'jadwal') loadJadwal();
            else if (currentTab === 'kasir') loadKasir();
            else if (currentTab === 'savings') loadSavings();
            else if (currentTab === 'share') loadShare();
            // Laporan Keuangan Sub-Menus
            else if (currentTab === 'laporan_keuangan' || currentTab === 'jurnal_umum') loadJurnalUmum();
            else if (currentTab === 'buku_besar') loadBukuBesar();
            else if (currentTab === 'neraca_saldo') loadNeracaSaldo();
            else if (currentTab === 'jurnal_penyesuaian') loadJurnalPenyesuaian();
            else if (currentTab === 'neraca_saldo_disesuaikan') loadNeracaSaldoDisesuaikan();
            else if (currentTab === 'laba_rugi') loadLabaRugi();
            else if (currentTab === 'jurnal_penutup') loadJurnalPenutup();
            else if (currentTab === 'neraca_saldo_setelah_tutup') loadNeracaSaldoSetelahTutup();
        }

        function escapeHtml(text) {
            if (!text) return '';
            return text.toString()
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }

        function escapeJs(text) {
            if (!text) return '';
            return text.toString().replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n');
        }

        function submitModalForm() {
            if (window.submitAction) window.submitAction();
        }

        // ==========================================
        // --- MY APLIKASI HUB & SEMUA APLIKASI ---
        // ==========================================
        function loadMyApps() {
            const apps = [
                { id: 'karyawan_murid', title: "Company", desc: "Manajemen data karyawan & murid, generator ID Card profesional otomatis dengan QR Code, dan sistem presensi terintegrasi.", icon: "bi-person-badge-fill", color: "#0F766E", badge: "ID Card & Presensi" },
                { id: 'block', title: "Block Puzzle", desc: "Game susun balok 8x8 asah otak seru dengan combo eliminasi, power-up palu/bom/refresh, dan high score.", icon: "bi-grid-fill", color: "#2C5E6A", badge: "Game Hiburan" },
                { id: 'brick_game', title: "Brick Game", desc: "Game retro arcade klasik LCD 9999-in-1 dengan 4 mode seru (Tetris, Mobil Balap, Ular Snake, Tembak Tank) dan kontrol gimbot.", icon: "bi-controller", color: "#306230", badge: "Game Retro" },
                { id: 'quran', title: "Al-Qur'an", desc: "Kitab suci 114 Surah lengkap dengan teks Arab indah, terjemahan Indonesia, dan audio murattal.", icon: "bi-journal-bookmark-fill", color: "#00796B", badge: "Ibadah & Spiritual" },
                { id: 'pembukuan', title: "Pembukuan", desc: "Kalkulator modal, estimasi pendapatan harian/bulanan, dan pengeluaran operasional usaha.", icon: "bi-graph-up-arrow", color: "#E65100", badge: "Manajemen Usaha" },
                { id: 'jurnal_umum', title: "Laporan Keuangan", desc: "Siklus akuntansi lengkap: Jurnal Umum, Buku Besar, Neraca Saldo, Penyesuaian, Laba Rugi, hingga Jurnal Penutup.", icon: "bi-file-earmark-spreadsheet", color: "#00838F", badge: "Laporan Keuangan" },
                { id: 'musik', title: "Musik", desc: "Pemutar file musik audio MP3/WAV/AAC lokal dengan kontrol pemutaran lengkap dan daftar putar.", icon: "bi-music-note-beamed", color: "#E91E63", badge: "Pemutar Audio" },
                { id: 'spinner', title: "Spinner", desc: "Roda putar keberuntungan serbaguna untuk undian, arisan, doorprize, atau pembagian kelompok.", icon: "bi-arrow-repeat", color: "#8338EC", badge: "Undian Acak" },
                { id: 'kasir', title: "Halaman Kasir", desc: "Sistem kasir Point of Sale (POS) cepat dengan pemindai barcode kamera, manajemen stok, dan struk belanja.", icon: "bi-shop", color: "#00897B", badge: "Point of Sale" },
                { id: 'shopping', title: "Daftar Belanja", desc: "Daftar belanja kebutuhan harian dan pencatatan riwayat transaksi belanja.", icon: "bi-cart3", color: "#3949AB", badge: "Manajemen Belanja" },
                { id: 'savings', title: "Catatan Tabungan", desc: "Tracking target tabungan, impian finansial, dan riwayat setoran/penarikan dana.", icon: "bi-wallet2", color: "#1E88E5", badge: "Tabungan" },
                { id: 'notes', title: "Catatan", desc: "Catatan harian, ide penting, dan dokumen teks pribadi.", icon: "bi-journal-text", color: "#43A047", badge: "Produktivitas" },
                { id: 'jadwal', title: "Jadwal Harian", desc: "Agenda aktivitas, pengingat jadwal, dan rutinitas harian terstruktur.", icon: "bi-clock-history", color: "#FB8C00", badge: "Perencana Waktu" }
            ];

            let html = `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-grid-3x3-gap-fill"></i> Semua Aplikasi
                        </h4>
                        <small class="text-secondary">Koleksi fitur terpadu, utilitas harian, dan game Block Puzzle & Brick Game Singghara Suite</small>
                    </div>
                    <button class="btn btn-outline-secondary btn-sm" onclick="switchTabByName('dashboard')">
                        <i class="bi bi-arrow-left me-1"></i> Kembali ke Dashboard
                    </button>
                </div>

                <div class="row g-3">
            `;

            apps.forEach(app => {
                html += `
                    <div class="col-12 col-md-6 col-lg-4">
                        <div class="card card-custom h-100 p-4 d-flex flex-column cursor-pointer" onclick="switchTabByName('${dol}{app.id}')" style="border-radius: 20px; transition: transform 0.2s ease, box-shadow 0.2s ease;">
                            <div class="d-flex align-items-center gap-3 mb-3">
                                <div class="app-icon-squircle flex-shrink-0" style="background-color: ${dol}{app.color};">
                                    <i class="bi ${dol}{app.icon} fs-4"></i>
                                </div>
                                <div class="overflow-hidden">
                                    <h5 class="fw-bold mb-1">${dol}{escapeHtml(app.title)}</h5>
                                    <span class="badge bg-secondary bg-opacity-25 text-secondary">${dol}{app.badge}</span>
                                </div>
                            </div>
                            <p class="text-secondary small mb-4 flex-grow-1">${dol}{escapeHtml(app.desc)}</p>
                            <button class="btn btn-sm btn-brand w-100 mt-auto">
                                <i class="bi bi-box-arrow-in-right me-1"></i> Buka ${dol}{escapeHtml(app.title)}
                            </button>
                        </div>
                    </div>
                `;
            });

            html += `</div>`;
            document.getElementById('tab-content').innerHTML = html;
        }

        // ==========================================
        // --- 1. AL-QUR'AN (114 SURAH & READER) ---
        // ==========================================
        window.quranSurahsCache = null;
        window.quranAudioPlayer = new Audio();
        window.currentPlayingSurah = null;
        window.quranFontSize = 28;

        async function loadQuran(surahId = null) {
            if (surahId) {
                await loadQuranSurahDetail(surahId);
                return;
            }

            try {
                if (!window.quranSurahsCache) {
                    const fetchRes = await fetchApiWithCache('/api/quran', 'quran_list');
                    window.quranSurahsCache = fetchRes.data;
                }
                const surahs = window.quranSurahsCache || [];

                let html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                        <div>
                            <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                                <i class="bi bi-journal-bookmark-fill" style="color: #00796B;"></i> Al-Qur'an Al-Karim
                            </h4>
                            <small class="text-secondary">Daftar 114 Surah Lengkap beserta Terjemahan Bahasa Indonesia</small>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <input type="text" id="quranSearchInput" class="form-control form-control-sm form-control-dark" placeholder="Cari Surah / Arti / No..." oninput="filterQuranSurahs()" style="max-width: 240px;">
                            <button class="btn btn-sm btn-brand" onclick="window.quranSurahsCache=null;loadQuran()"><i class="bi bi-arrow-clockwise"></i></button>
                        </div>
                    </div>

                    <div class="row g-3" id="quranSurahGrid">
                `;

                surahs.forEach(s => {
                    html += `
                        <div class="col-12 col-md-6 col-lg-4 quran-surah-item" data-search="${dol}{s.id} ${dol}{s.nameLatin.toLowerCase()} ${dol}{s.translation.toLowerCase()} ${dol}{s.nameArabic}">
                            <div class="card card-custom h-100 p-3 cursor-pointer" onclick="loadQuran(${dol}{s.id})" style="border-radius: 16px;">
                                <div class="d-flex justify-content-between align-items-center">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="d-flex align-items-center justify-content-center fw-bold text-success border border-success border-opacity-25 rounded-circle" style="width: 42px; height: 42px; background: rgba(0, 121, 107, 0.1);">
                                            ${dol}{s.id}
                                        </div>
                                        <div>
                                            <h6 class="fw-bold mb-0">${dol}{escapeHtml(s.nameLatin)}</h6>
                                            <small class="text-secondary">${dol}{escapeHtml(s.translation)} &bull; ${dol}{s.verseCount} Ayat</small>
                                        </div>
                                    </div>
                                    <div class="text-end">
                                        <h5 class="fw-bold text-success mb-0" style="font-family: serif;">${dol}{s.nameArabic}</h5>
                                        <span class="badge bg-secondary bg-opacity-25 text-secondary" style="font-size: 10px;">${dol}{s.type}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                });

                html += `</div>`;
                document.getElementById('tab-content').innerHTML = html;
            } catch (e) {
                document.getElementById('tab-content').innerHTML = `<div class="alert alert-danger">Gagal memuat Al-Qur'an: ${dol}{e.message}</div>`;
            }
        }

        function filterQuranSurahs() {
            const query = (document.getElementById('quranSearchInput').value || '').toLowerCase().trim();
            document.querySelectorAll('.quran-surah-item').forEach(el => {
                const searchData = el.getAttribute('data-search') || '';
                el.style.display = searchData.includes(query) ? '' : 'none';
            });
        }

        async function loadQuranSurahDetail(surahId) {
            document.getElementById('tab-content').innerHTML = `
                <div class="text-center py-5 text-secondary">
                    <div class="spinner-border text-success mb-2" role="status"></div>
                    <div>Memuat Surah...</div>
                </div>
            `;

            try {
                const fetchRes = await fetchApiWithCache('/api/quran?surahId=' + surahId, 'quran_surah_' + surahId);
                const data = fetchRes.data || {};

                let html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <button class="btn btn-outline-secondary btn-sm" onclick="loadQuran()">
                            <i class="bi bi-arrow-left me-1"></i> Daftar Surah
                        </button>
                        <div class="text-center">
                            <h4 class="fw-bold mb-0 text-success">${dol}{surahId}. ${dol}{escapeHtml(data.nameLatin || '')} (${dol}{data.nameArabic || ''})</h4>
                            <small class="text-secondary">${dol}{escapeHtml(data.translation || '')} &bull; ${dol}{data.verseCount || 0} Ayat &bull; ${dol}{data.type || ''}</small>
                        </div>
                        <div class="d-flex align-items-center gap-1">
                            <button class="btn btn-sm btn-outline-secondary" onclick="adjustQuranFontSize(-2)" title="Perkecil Font Arab">A-</button>
                            <button class="btn btn-sm btn-outline-secondary" onclick="adjustQuranFontSize(2)" title="Perbesar Font Arab">A+</button>
                            <button class="btn btn-sm btn-brand ms-2" onclick="playSurahAudio(${dol}{surahId})" id="btnPlaySurahAudio">
                                <i class="bi bi-play-fill me-1"></i> Audio Murattal
                            </button>
                        </div>
                    </div>

                    <div class="card card-custom p-4 mb-4 text-center" style="border-radius: 20px;">
                        <h3 class="mb-0 text-success" style="font-family: serif; letter-spacing: 1px;">ÿ®Ÿêÿ≥ŸíŸÖŸê ÿßŸÑŸÑŸéŸëŸáŸê ÿßŸÑÿ±ŸéŸëÿ≠ŸíŸÖŸéŸ∞ŸÜŸê ÿßŸÑÿ±ŸéŸëÿ≠ŸêŸäŸÖŸê</h3>
                        <small class="text-secondary mt-1">Dengan nama Allah Yang Maha Pengasih, Maha Penyayang</small>
                    </div>

                    <div class="d-flex flex-column gap-3" id="quranVersesContainer">
                `;

                (data.verses || []).forEach(v => {
                    html += `
                        <div class="card card-custom p-3 p-md-4" style="border-radius: 16px;">
                            <div class="d-flex justify-content-between align-items-center mb-3 pb-2 border-bottom border-secondary border-opacity-25">
                                <span class="badge bg-success bg-opacity-25 text-success fs-6 fw-bold px-3 py-1 rounded-pill">
                                    Ayat ${dol}{v.verseNumber}
                                </span>
                                <button class="btn btn-sm btn-outline-secondary" onclick="copyAyahText('${dol}{escapeJs(v.textArabic)} - ${dol}{escapeJs(v.translation)}')">
                                    <i class="bi bi-copy me-1"></i> Salin
                                </button>
                            </div>
                            <div class="quran-arabic-text mb-3" style="font-size: ${dol}{window.quranFontSize}px;">
                                ${dol}{v.textArabic}
                            </div>
                            <div class="text-secondary small">
                                <strong class="text-main d-block mb-1">Artinya:</strong>
                                ${dol}{escapeHtml(v.translation)}
                            </div>
                        </div>
                    `;
                });

                html += `</div>`;
                document.getElementById('tab-content').innerHTML = html;
            } catch (e) {
                document.getElementById('tab-content').innerHTML = `<div class="alert alert-danger">Gagal memuat ayat: ${dol}{e.message}</div>`;
            }
        }

        function adjustQuranFontSize(delta) {
            window.quranFontSize = Math.max(18, Math.min(48, window.quranFontSize + delta));
            document.querySelectorAll('.quran-arabic-text').forEach(el => {
                el.style.fontSize = window.quranFontSize + 'px';
            });
        }

        function playSurahAudio(surahId) {
            const btn = document.getElementById('btnPlaySurahAudio');
            const paddedId = String(surahId).padStart(3, '0');
            const audioUrl = `https://cdn.islamic.network/quran/audio-surah/128/ar.alafasy/${dol}{surahId}.mp3`;

            if (window.currentPlayingSurah === surahId && !window.quranAudioPlayer.paused) {
                window.quranAudioPlayer.pause();
                if (btn) btn.innerHTML = `<i class="bi bi-play-fill me-1"></i> Audio Murattal`;
                return;
            }

            window.quranAudioPlayer.src = audioUrl;
            window.quranAudioPlayer.play().then(() => {
                window.currentPlayingSurah = surahId;
                if (btn) btn.innerHTML = `<i class="bi bi-pause-fill me-1"></i> Jeda Audio`;
            }).catch(e => {
                alert('Audio sedang dimuat atau periksa koneksi internet untuk streaming murattal.');
            });

            window.quranAudioPlayer.onended = () => {
                if (btn) btn.innerHTML = `<i class="bi bi-play-fill me-1"></i> Audio Murattal`;
            };
        }

        function copyAyahText(text) {
            navigator.clipboard.writeText(text).then(() => {
                alert('Ayat berhasil disalin ke clipboard');
            }).catch(() => {
                prompt('Salin ayat:', text);
            });
        }

        // ==========================================
        // --- 2. PEMBUKUAN USAHA & MODAL (LIVE SYNC DENGAN APLIKASI ANDROID) ---
        // ==========================================
        window.pembukuanData = null;
        window.pembukuanSubTab = 'dashboard'; // 'dashboard', 'modal', 'income', 'expense'

        function switchPembukuanSubTab(tab) {
            window.pembukuanSubTab = tab;
            loadPembukuan();
        }

        async function fetchPembukuanData() {
            try {
                const fetchRes = await fetchApiWithCache('/api/pembukuan', 'pembukuan');
                window.pembukuanData = fetchRes.data;
                return window.pembukuanData;
            } catch(e) {
                console.error('Error fetching pembukuan data:', e);
                return StorageCache.get('pembukuan') || {
                    namaUsaha: "Usaha Saya",
                    modalUsaha: 0,
                    modalItems: [],
                    incomeItems: [],
                    expenseItems: []
                };
            }
        }

        async function loadPembukuan() {
            const data = await fetchPembukuanData();
            const currentTab = window.pembukuanSubTab || 'dashboard';

            const totalModalBarang = data.modalItems.reduce((acc, x) => acc + ((x.qty || 1) * (x.harga || 0)), 0);
            const totalModal = totalModalBarang + (data.modalUsaha || 0);
            const totalDailyIncome = data.incomeItems.reduce((acc, x) => acc + (x.daily !== undefined ? x.daily : ((x.tarif || 0) * (x.qty || 0))), 0);
            const totalMonthlyIncome = data.incomeItems.reduce((acc, x) => acc + (x.monthly !== undefined ? x.monthly : ((x.tarif || 0) * (x.qty || 0) * 30)), 0);
            const totalMonthlyExpense = data.expenseItems.reduce((acc, x) => acc + (x.biaya || 0), 0);
            const netProfit = totalMonthlyIncome - totalMonthlyExpense;

            // Menu Titik 3 (Dropdown Menu - Adaptive Light/Dark with Hover Animation)
            const menuDropdownHtml = `
                <div class="dropdown">
                    <button class="pembukuan-menu-btn dropdown-toggle d-flex align-items-center gap-1 shadow-sm" type="button" data-bs-toggle="dropdown" aria-expanded="false" title="Menu Pembukuan">
                        <i class="bi bi-three-dots-vertical"></i>
                        <span class="d-none d-sm-inline fw-semibold small">Menu</span>
                    </button>
                    <ul class="dropdown-menu dropdown-menu-end pembukuan-dropdown-menu shadow">
                        <li><h6 class="dropdown-header text-uppercase">Navigasi Pembukuan</h6></li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{currentTab === 'dashboard' ? 'active' : ''}" href="javascript:void(0)" onclick="switchPembukuanSubTab('dashboard')">
                                <i class="bi bi-speedometer2 text-success"></i> 1. Dashboard
                            </a>
                        </li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{currentTab === 'modal' ? 'active' : ''}" href="javascript:void(0)" onclick="switchPembukuanSubTab('modal')">
                                <i class="bi bi-box-seam text-primary"></i> 2. Kebutuhan Barang &amp; Modal Awal
                            </a>
                        </li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{currentTab === 'income' ? 'active' : ''}" href="javascript:void(0)" onclick="switchPembukuanSubTab('income')">
                                <i class="bi bi-cash-stack text-info"></i> 3. Estimasi Pendapatan Usaha
                            </a>
                        </li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 ${dol}{currentTab === 'expense' ? 'active' : ''}" href="javascript:void(0)" onclick="switchPembukuanSubTab('expense')">
                                <i class="bi bi-receipt text-warning"></i> 4. Beban &amp; Biaya Operasional
                            </a>
                        </li>
                        <li><hr class="dropdown-divider"></li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 text-info" href="javascript:void(0)" onclick="openEditNamaUsahaModal()">
                                <i class="bi bi-pencil-square"></i> Edit Nama &amp; Modal Usaha
                            </a>
                        </li>
                        <li>
                            <a class="dropdown-item d-flex align-items-center gap-2 py-2 text-secondary" href="javascript:void(0)" onclick="printLaporanPembukuan()">
                                <i class="bi bi-printer"></i> Cetak Laporan
                            </a>
                        </li>
                    </ul>
                </div>
            `;

            let html = '';

            if (currentTab === 'dashboard') {
                // ==================== 1. DASHBOARD ====================
                html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <div>
                            <div class="d-flex align-items-center gap-2">
                                <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                                    <i class="bi bi-graph-up-arrow" style="color: #E65100;"></i> Pembukuan Usaha - Dashboard
                                </h4>
                                <span class="badge bg-success bg-opacity-25 text-success rounded-pill px-3 py-1 fw-bold">Live Sync</span>
                            </div>
                            <small class="text-secondary">Ringkasan Laba Rugi, Modal, Pendapatan, dan Beban Usaha (${dol}{escapeHtml(data.namaUsaha || 'Usaha Saya')})</small>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            ${dol}{menuDropdownHtml}
                        </div>
                    </div>

                    <!-- CARD PROFIL USAHA -->
                    <div class="card card-custom p-4 mb-4" style="border-radius: 20px; border-left: 5px solid #10B981;">
                        <div class="d-flex flex-wrap justify-content-between align-items-center gap-3">
                            <div>
                                <small class="text-secondary text-uppercase fw-bold" style="font-size: 11px; letter-spacing: 0.5px;">Profil Usaha Terdaftar</small>
                                <h4 class="fw-bold mb-1" style="color: var(--text-main);">${dol}{escapeHtml(data.namaUsaha || 'Usaha Saya')}</h4>
                                <div class="d-flex flex-wrap align-items-center gap-3 small text-secondary mt-2">
                                    <span><i class="bi bi-wallet2 text-success me-1"></i> Modal Tunai Awal: <strong style="color: var(--text-main);">Rp ${dol}{Math.round(data.modalUsaha || 0).toLocaleString('id-ID')}</strong></span>
                                    <span><i class="bi bi-boxes text-primary me-1"></i> Nilai Aset/Barang: <strong style="color: var(--text-main);">Rp ${dol}{Math.round(totalModalBarang).toLocaleString('id-ID')}</strong></span>
                                </div>
                            </div>
                            <div>
                                <button class="btn btn-outline-success btn-sm px-3" onclick="openEditNamaUsahaModal()">
                                    <i class="bi bi-pencil-square me-1"></i> Edit Nama Usaha & Modal
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- 4 RINGKASAN EKSEKUTIF -->
                    <div class="row g-3 mb-4">
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="pembukuan-metric-card clickable h-100" onclick="switchPembukuanSubTab('modal')" title="Klik untuk kelola Kebutuhan Modal Awal">
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <small class="text-secondary">Total Modal Awal</small>
                                    <i class="bi bi-box-seam text-primary fs-5"></i>
                                </div>
                                <h4 class="fw-bold text-primary mb-0">Rp ${dol}{Math.round(totalModal).toLocaleString('id-ID')}</h4>
                                <small class="text-secondary mt-1 d-block">${dol}{data.modalItems.length} Barang/Aset ${dol}{data.modalUsaha > 0 ? `+ Modal Tunai` : ''}</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="pembukuan-metric-card clickable h-100" onclick="switchPembukuanSubTab('income')" title="Klik untuk kelola Estimasi Pendapatan">
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <small class="text-secondary">Estimasi Omset / Bulan</small>
                                    <i class="bi bi-cash-stack text-info fs-5"></i>
                                </div>
                                <h4 class="fw-bold text-info mb-0">Rp ${dol}{Math.round(totalMonthlyIncome).toLocaleString('id-ID')}</h4>
                                <small class="text-secondary mt-1 d-block">Rp ${dol}{Math.round(totalDailyIncome).toLocaleString('id-ID')} / hari</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="pembukuan-metric-card clickable h-100" onclick="switchPembukuanSubTab('expense')" title="Klik untuk kelola Beban Operasional">
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <small class="text-secondary">Biaya Operasional / Bln</small>
                                    <i class="bi bi-receipt text-warning fs-5"></i>
                                </div>
                                <h4 class="fw-bold text-warning mb-0">Rp ${dol}{Math.round(totalMonthlyExpense).toLocaleString('id-ID')}</h4>
                                <small class="text-secondary mt-1 d-block">${dol}{data.expenseItems.length} Pos Pengeluaran</small>
                            </div>
                        </div>
                        <div class="col-12 col-sm-6 col-lg-3">
                            <div class="pembukuan-metric-card h-100">
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <small class="text-secondary">Estimasi Laba Bersih / Bln</small>
                                    <i class="bi bi-graph-up text-success fs-5"></i>
                                </div>
                                <h4 class="fw-bold ${dol}{netProfit >= 0 ? 'text-success' : 'text-danger'} mb-0">Rp ${dol}{Math.round(netProfit).toLocaleString('id-ID')}</h4>
                                <small class="text-secondary mt-1 d-block">Margin: ${dol}{totalMonthlyIncome > 0 ? ((netProfit / totalMonthlyIncome) * 100).toFixed(1) : 0}%</small>
                            </div>
                        </div>
                    </div>

                    <!-- 3 QUICK ACTION CARDS -->
                    <div class="row g-3">
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-quick-card">
                                <div>
                                    <div class="d-flex align-items-center gap-2 mb-2">
                                        <div class="p-2 rounded-3 bg-primary bg-opacity-10 text-primary">
                                            <i class="bi bi-box-seam fs-5"></i>
                                        </div>
                                        <h6 class="fw-bold mb-0" style="color: var(--text-main);">Kebutuhan Barang Modal</h6>
                                    </div>
                                    <p class="text-secondary small mb-3">Kelola daftar aset &amp; kebutuhan perlengkapan operasional usaha.</p>
                                    <div class="pembukuan-stat-box">
                                        <div class="d-flex justify-content-between small text-secondary mb-1">
                                            <span>Jumlah Barang</span>
                                            <strong style="color: var(--text-main);">${dol}{data.modalItems.length} Item</strong>
                                        </div>
                                        <div class="d-flex justify-content-between small text-secondary">
                                            <span>Total Nilai</span>
                                            <strong class="text-primary">Rp ${dol}{Math.round(totalModalBarang).toLocaleString('id-ID')}</strong>
                                        </div>
                                    </div>
                                </div>
                                <button class="btn btn-outline-primary btn-sm w-100" onclick="switchPembukuanSubTab('modal')">
                                    <i class="bi bi-arrow-right-circle me-1"></i> Buka Menu Barang Modal
                                </button>
                            </div>
                        </div>
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-quick-card">
                                <div>
                                    <div class="d-flex align-items-center gap-2 mb-2">
                                        <div class="p-2 rounded-3 bg-info bg-opacity-10 text-info">
                                            <i class="bi bi-cash-stack fs-5"></i>
                                        </div>
                                        <h6 class="fw-bold mb-0" style="color: var(--text-main);">Estimasi Pendapatan</h6>
                                    </div>
                                    <p class="text-secondary small mb-3">Kelola proyeksi omset produk &amp; layanan harian dan bulanan.</p>
                                    <div class="pembukuan-stat-box">
                                        <div class="d-flex justify-content-between small text-secondary mb-1">
                                            <span>Jumlah Layanan</span>
                                            <strong style="color: var(--text-main);">${dol}{data.incomeItems.length} Item</strong>
                                        </div>
                                        <div class="d-flex justify-content-between small text-secondary">
                                            <span>Omset / Bulan</span>
                                            <strong class="text-info">Rp ${dol}{Math.round(totalMonthlyIncome).toLocaleString('id-ID')}</strong>
                                        </div>
                                    </div>
                                </div>
                                <button class="btn btn-outline-info btn-sm w-100" onclick="switchPembukuanSubTab('income')">
                                    <i class="bi bi-arrow-right-circle me-1"></i> Buka Menu Pendapatan
                                </button>
                            </div>
                        </div>
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-quick-card">
                                <div>
                                    <div class="d-flex align-items-center gap-2 mb-2">
                                        <div class="p-2 rounded-3 bg-warning bg-opacity-10 text-warning">
                                            <i class="bi bi-receipt fs-5"></i>
                                        </div>
                                        <h6 class="fw-bold mb-0" style="color: var(--text-main);">Beban &amp; Biaya Operasional</h6>
                                    </div>
                                    <p class="text-secondary small mb-3">Kelola pengeluaran rutin bulanan seperti sewa, listrik, dan gaji.</p>
                                    <div class="pembukuan-stat-box">
                                        <div class="d-flex justify-content-between small text-secondary mb-1">
                                            <span>Jumlah Pos Beban</span>
                                            <strong style="color: var(--text-main);">${dol}{data.expenseItems.length} Pos</strong>
                                        </div>
                                        <div class="d-flex justify-content-between small text-secondary">
                                            <span>Total Biaya / Bulan</span>
                                            <strong class="text-warning">Rp ${dol}{Math.round(totalMonthlyExpense).toLocaleString('id-ID')}</strong>
                                        </div>
                                    </div>
                                </div>
                                <button class="btn btn-outline-warning btn-sm w-100" onclick="switchPembukuanSubTab('expense')">
                                    <i class="bi bi-arrow-right-circle me-1"></i> Buka Menu Beban Biaya
                                </button>
                            </div>
                        </div>
                    </div>
                `;
            } else if (currentTab === 'modal') {
                // ==================== 2. KEBUTUHAN BARANG & MODAL AWAL ====================
                html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-outline-secondary" onclick="switchPembukuanSubTab('dashboard')" title="Kembali ke Dashboard">
                                <i class="bi bi-arrow-left"></i>
                            </button>
                            <div>
                                <h4 class="fw-bold mb-0 text-primary d-flex align-items-center gap-2">
                                    <i class="bi bi-box-seam"></i> 2. Kebutuhan Barang & Modal Awal
                                </h4>
                                <small class="text-secondary">Daftar perlengkapan, aset awal, dan modal usaha</small>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-brand" onclick="openAddModalItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Barang</button>
                            ${dol}{menuDropdownHtml}
                        </div>
                    </div>

                    <div class="row g-3 mb-4">
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Modal Barang / Aset</small>
                                <h5 class="fw-bold text-primary mb-0">Rp ${dol}{Math.round(totalModalBarang).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Modal Tunai</small>
                                <h5 class="fw-bold text-success mb-0">Rp ${dol}{Math.round(data.modalUsaha || 0).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                        <div class="col-12 col-md-4">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Total Keseluruhan Modal</small>
                                <h5 class="fw-bold mb-0" style="color: var(--text-main);">Rp ${dol}{Math.round(totalModal).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                    </div>

                    <div class="card card-custom p-4 mb-4" style="border-radius: 20px;">
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <h6 class="fw-bold mb-0 text-primary"><i class="bi bi-list-check me-2"></i>Tabel Rincian Kebutuhan Modal Awal</h6>
                            <button class="btn btn-sm btn-brand" onclick="openAddModalItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Barang</button>
                        </div>
                        <div class="table-responsive">
                            <table class="table table-dark-custom align-middle mb-0 small">
                                <thead>
                                    <tr>
                                        <th>Nama Barang / Aset</th>
                                        <th class="text-center">Jumlah (Qty)</th>
                                        <th class="text-end">Harga Satuan</th>
                                        <th class="text-end">Subtotal</th>
                                        <th class="text-end">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                `;

                if (data.modalItems.length === 0) {
                    html += `<tr><td colspan="5" class="text-center text-secondary py-3">Belum ada daftar kebutuhan barang modal. Klik '+ Tambah Barang' untuk menambahkan.</td></tr>`;
                } else {
                    data.modalItems.forEach((m, idx) => {
                        const subtotal = m.subtotal !== undefined ? m.subtotal : (m.qty * m.harga);
                        html += `
                            <tr>
                                <td class="fw-semibold">${dol}{escapeHtml(m.nama)}</td>
                                <td class="text-center">${dol}{m.qty}</td>
                                <td class="text-end">Rp ${dol}{m.harga.toLocaleString('id-ID')}</td>
                                <td class="text-end fw-bold text-success">Rp ${dol}{subtotal.toLocaleString('id-ID')}</td>
                                <td class="text-end">
                                    <button class="btn btn-sm text-danger p-0" onclick="deleteModalItem(${dol}{idx})" title="Hapus"><i class="bi bi-trash"></i></button>
                                </td>
                            </tr>
                        `;
                    });
                }

                html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
            } else if (currentTab === 'income') {
                // ==================== 3. ESTIMASI PENDAPATAN USAHA ====================
                html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-outline-secondary" onclick="switchPembukuanSubTab('dashboard')" title="Kembali ke Dashboard">
                                <i class="bi bi-arrow-left"></i>
                            </button>
                            <div>
                                <h4 class="fw-bold mb-0 text-info d-flex align-items-center gap-2">
                                    <i class="bi bi-cash-stack"></i> 3. Estimasi Pendapatan Usaha
                                </h4>
                                <small class="text-secondary">Proyeksi penjualan harian dan estimasi omset bulanan</small>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-brand" onclick="openAddIncomeItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Layanan</button>
                            ${dol}{menuDropdownHtml}
                        </div>
                    </div>

                    <div class="row g-3 mb-4">
                        <div class="col-12 col-md-6">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Total Estimasi Per Hari</small>
                                <h5 class="fw-bold text-info mb-0">Rp ${dol}{Math.round(totalDailyIncome).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                        <div class="col-12 col-md-6">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Total Estimasi Per Bulan (30 Hari)</small>
                                <h5 class="fw-bold text-success mb-0">Rp ${dol}{Math.round(totalMonthlyIncome).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                    </div>

                    <div class="card card-custom p-4 mb-4" style="border-radius: 20px;">
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <h6 class="fw-bold mb-0 text-info"><i class="bi bi-list-check me-2"></i>Tabel Estimasi Produk & Layanan</h6>
                            <button class="btn btn-sm btn-brand" onclick="openAddIncomeItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Layanan</button>
                        </div>
                        <div class="table-responsive">
                            <table class="table table-dark-custom align-middle mb-0 small">
                                <thead>
                                    <tr>
                                        <th>Nama Produk / Layanan</th>
                                        <th class="text-end">Tarif / Harga</th>
                                        <th class="text-center">Estimasi Qty/Hari</th>
                                        <th class="text-end">Total Per Hari</th>
                                        <th class="text-end">Total Per Bulan (30 Hari)</th>
                                        <th class="text-end">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                `;

                if (data.incomeItems.length === 0) {
                    html += `<tr><td colspan="6" class="text-center text-secondary py-3">Belum ada estimasi layanan / produk. Klik '+ Tambah Layanan' untuk menambahkan.</td></tr>`;
                } else {
                    data.incomeItems.forEach((inc, idx) => {
                        const daily = inc.daily !== undefined ? inc.daily : (inc.tarif * inc.qty);
                        const monthly = inc.monthly !== undefined ? inc.monthly : (daily * 30);
                        html += `
                            <tr>
                                <td class="fw-semibold">${dol}{escapeHtml(inc.nama)}</td>
                                <td class="text-end">Rp ${dol}{inc.tarif.toLocaleString('id-ID')}</td>
                                <td class="text-center">${dol}{inc.qty} unit</td>
                                <td class="text-end fw-bold text-info">Rp ${dol}{Math.round(daily).toLocaleString('id-ID')}</td>
                                <td class="text-end fw-bold text-success">Rp ${dol}{Math.round(monthly).toLocaleString('id-ID')}</td>
                                <td class="text-end">
                                    <button class="btn btn-sm text-danger p-0" onclick="deleteIncomeItem(${dol}{idx})" title="Hapus"><i class="bi bi-trash"></i></button>
                                </td>
                            </tr>
                        `;
                    });
                }

                html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
            } else if (currentTab === 'expense') {
                // ==================== 4. BEBAN & BIAYA OPERASIONAL ====================
                html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-outline-secondary" onclick="switchPembukuanSubTab('dashboard')" title="Kembali ke Dashboard">
                                <i class="bi bi-arrow-left"></i>
                            </button>
                            <div>
                                <h4 class="fw-bold mb-0 text-warning d-flex align-items-center gap-2">
                                    <i class="bi bi-receipt"></i> 4. Beban & Biaya Operasional
                                </h4>
                                <small class="text-secondary">Kelola pengeluaran rutin operasional setiap bulan</small>
                            </div>
                        </div>
                        <div class="d-flex align-items-center gap-2">
                            <button class="btn btn-sm btn-brand" onclick="openAddExpenseItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Biaya</button>
                            ${dol}{menuDropdownHtml}
                        </div>
                    </div>

                    <div class="row g-3 mb-4">
                        <div class="col-12 col-md-6">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Total Beban Operasional / Bulan</small>
                                <h5 class="fw-bold text-warning mb-0">Rp ${dol}{Math.round(totalMonthlyExpense).toLocaleString('id-ID')}</h5>
                            </div>
                        </div>
                        <div class="col-12 col-md-6">
                            <div class="pembukuan-metric-card">
                                <small class="text-secondary d-block mb-1">Jumlah Pos Beban</small>
                                <h5 class="fw-bold mb-0" style="color: var(--text-main);">${dol}{data.expenseItems.length} Pos Pengeluaran</h5>
                            </div>
                        </div>
                    </div>

                    <div class="card card-custom p-4 mb-4" style="border-radius: 20px;">
                        <div class="d-flex justify-content-between align-items-center mb-3">
                            <h6 class="fw-bold mb-0 text-warning"><i class="bi bi-list-check me-2"></i>Tabel Beban Biaya Operasional</h6>
                            <button class="btn btn-sm btn-brand" onclick="openAddExpenseItem()"><i class="bi bi-plus-lg me-1"></i> Tambah Biaya</button>
                        </div>
                        <div class="table-responsive">
                            <table class="table table-dark-custom align-middle mb-0 small">
                                <thead>
                                    <tr>
                                        <th>Jenis Pengeluaran Operasional</th>
                                        <th class="text-end">Nominal / Bulan</th>
                                        <th class="text-end">Aksi</th>
                                    </tr>
                                </thead>
                                <tbody>
                `;

                if (data.expenseItems.length === 0) {
                    html += `<tr><td colspan="3" class="text-center text-secondary py-3">Belum ada pos beban operasional. Klik '+ Tambah Biaya' untuk menambahkan.</td></tr>`;
                } else {
                    data.expenseItems.forEach((exp, idx) => {
                        html += `
                            <tr>
                                <td class="fw-semibold">${dol}{escapeHtml(exp.nama)}</td>
                                <td class="text-end fw-bold text-warning">Rp ${dol}{exp.biaya.toLocaleString('id-ID')}</td>
                                <td class="text-end">
                                    <button class="btn btn-sm text-danger p-0" onclick="deleteExpenseItem(${dol}{idx})" title="Hapus"><i class="bi bi-trash"></i></button>
                                </td>
                            </tr>
                        `;
                    });
                }

                html += `
                                </tbody>
                            </table>
                        </div>
                    </div>
                `;
            }

            document.getElementById('tab-content').innerHTML = html;
        }

        function openEditNamaUsahaModal() {
            const data = window.pembukuanData || { namaUsaha: "Usaha Saya", modalUsaha: 0 };
            document.getElementById('modalTitle').innerText = 'Edit Profil & Modal Usaha';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Nama Usaha / Toko</label>
                    <input type="text" id="editNamaUsahaInput" class="form-control form-control-dark" value="${dol}{escapeHtml(data.namaUsaha || 'Usaha Saya')}" placeholder="Nama Usaha...">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Modal Tunai Awal (Rp)</label>
                    <input type="number" id="editModalUsahaInput" class="form-control form-control-dark" value="${dol}{data.modalUsaha || 0}" placeholder="0">
                </div>
            `;
            window.submitAction = async () => {
                const namaUsaha = document.getElementById('editNamaUsahaInput').value.trim() || 'Usaha Saya';
                const modalUsaha = parseFloat(document.getElementById('editModalUsahaInput').value) || 0;
                try {
                    await fetch('/api/pembukuan', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'save_profile', namaUsaha, modalUsaha })
                    });
                    modalInstance.hide();
                    await loadPembukuan();
                } catch(e) {
                    alert('Gagal menyimpan profil usaha: ' + e.message);
                }
            };
            modalInstance.show();
        }

        function openAddModalItem() {
            document.getElementById('modalTitle').innerText = 'Tambah Kebutuhan Modal Awal';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Nama Barang / Aset</label>
                    <input type="text" id="modalItemNama" class="form-control form-control-dark" placeholder="Contoh: Meja Kasir, Kompor...">
                </div>
                <div class="row g-2 mb-3">
                    <div class="col-6">
                        <label class="form-label text-secondary">Jumlah (Qty)</label>
                        <input type="number" id="modalItemQty" class="form-control form-control-dark" value="1" min="1">
                    </div>
                    <div class="col-6">
                        <label class="form-label text-secondary">Harga Satuan (Rp)</label>
                        <input type="number" id="modalItemHarga" class="form-control form-control-dark" placeholder="500000">
                    </div>
                </div>
            `;
            window.submitAction = async () => {
                const nama = document.getElementById('modalItemNama').value.trim();
                const qty = parseInt(document.getElementById('modalItemQty').value) || 1;
                const harga = parseFloat(document.getElementById('modalItemHarga').value) || 0;
                if (!nama) return alert('Nama barang harus diisi');
                try {
                    await fetch('/api/pembukuan', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'add_modal', nama, qty, harga })
                    });
                    modalInstance.hide();
                    await loadPembukuan();
                } catch(e) {
                    alert('Gagal menambah barang: ' + e.message);
                }
            };
            modalInstance.show();
        }

        function deleteModalItem(index) {
            showCustomConfirm({
                title: 'Hapus Item Modal',
                message: 'Apakah Anda yakin ingin menghapus barang/kebutuhan modal ini dari perhitungan?',
                confirmText: 'Hapus Item',
                onConfirm: async () => {
                    try {
                        await fetch('/api/pembukuan', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: 'delete_modal', index })
                        });
                        await loadPembukuan();
                    } catch(e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function openAddIncomeItem() {
            document.getElementById('modalTitle').innerText = 'Tambah Estimasi Layanan / Penjualan';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Nama Layanan / Produk</label>
                    <input type="text" id="incNama" class="form-control form-control-dark" placeholder="Contoh: Cuci Mobil, Kopi Susu...">
                </div>
                <div class="row g-2 mb-3">
                    <div class="col-6">
                        <label class="form-label text-secondary">Tarif / Harga Satuan (Rp)</label>
                        <input type="number" id="incTarif" class="form-control form-control-dark" placeholder="25000">
                    </div>
                    <div class="col-6">
                        <label class="form-label text-secondary">Estimasi Transaksi / Hari</label>
                        <input type="number" id="incQty" class="form-control form-control-dark" value="10" min="1">
                    </div>
                </div>
            `;
            window.submitAction = async () => {
                const nama = document.getElementById('incNama').value.trim();
                const tarif = parseFloat(document.getElementById('incTarif').value) || 0;
                const qty = parseInt(document.getElementById('incQty').value) || 1;
                if (!nama) return alert('Nama layanan harus diisi');
                try {
                    await fetch('/api/pembukuan', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'add_income', nama, tarif, qty, jam: 13 })
                    });
                    modalInstance.hide();
                    await loadPembukuan();
                } catch(e) {
                    alert('Gagal menambah estimasi layanan: ' + e.message);
                }
            };
            modalInstance.show();
        }

        function deleteIncomeItem(index) {
            showCustomConfirm({
                title: 'Hapus Estimasi Pendapatan',
                message: 'Apakah Anda yakin ingin menghapus estimasi pendapatan / layanan ini?',
                confirmText: 'Hapus',
                onConfirm: async () => {
                    try {
                        await fetch('/api/pembukuan', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: 'delete_income', index })
                        });
                        await loadPembukuan();
                    } catch(e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function openAddExpenseItem() {
            document.getElementById('modalTitle').innerText = 'Tambah Beban Operasional';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Jenis Pengeluaran</label>
                    <input type="text" id="expNama" class="form-control form-control-dark" placeholder="Contoh: Tagihan Listrik, Internet, Kebersihan...">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Biaya Per Bulan (Rp)</label>
                    <input type="number" id="expBiaya" class="form-control form-control-dark" placeholder="500000">
                </div>
            `;
            window.submitAction = async () => {
                const nama = document.getElementById('expNama').value.trim();
                const biaya = parseFloat(document.getElementById('expBiaya').value) || 0;
                if (!nama) return alert('Nama biaya harus diisi');
                try {
                    await fetch('/api/pembukuan', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ action: 'add_expense', nama, biaya })
                    });
                    modalInstance.hide();
                    await loadPembukuan();
                } catch(e) {
                    alert('Gagal menambah beban operasional: ' + e.message);
                }
            };
            modalInstance.show();
        }

        function deleteExpenseItem(index) {
            showCustomConfirm({
                title: 'Hapus Beban Operasional',
                message: 'Apakah Anda yakin ingin menghapus beban biaya operasional ini?',
                confirmText: 'Hapus',
                onConfirm: async () => {
                    try {
                        await fetch('/api/pembukuan', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ action: 'delete_expense', index })
                        });
                        await loadPembukuan();
                    } catch(e) {
                        alert('Gagal menghapus: ' + e.message);
                    }
                }
            });
        }

        function printLaporanPembukuan() {
            const data = window.pembukuanData || {
                namaUsaha: "Usaha Saya",
                modalUsaha: 0,
                modalItems: [],
                incomeItems: [],
                expenseItems: []
            };

            const formatRupiah = (amount) => {
                return 'Rp ' + Math.round(amount || 0).toLocaleString('id-ID');
            };

            const modalItems = data.modalItems || [];
            const incomeItems = data.incomeItems || [];
            const expenseItems = data.expenseItems || [];

            const totalKebutuhan = modalItems.reduce((acc, x) => acc + ((x.qty || 1) * (x.harga || 0)), 0);
            const totalMonthlyIncome = incomeItems.reduce((acc, x) => acc + (x.monthly !== undefined ? x.monthly : ((x.tarif || 0) * (x.qty || 0) * 30)), 0);
            const totalBebanOperasional = expenseItems.reduce((acc, x) => acc + (x.biaya || 0), 0);
            const totalBeban = totalKebutuhan + totalBebanOperasional;
            const labaBersih = totalMonthlyIncome - totalBeban;

            let roi = "Laba belum positif";
            if (labaBersih > 0) {
                const modalUsahaVal = data.modalUsaha || 0;
                const roiVal = modalUsahaVal > 0 ? (modalUsahaVal / labaBersih) : 0;
                roi = (roiVal > 0) ? (roiVal.toFixed(1) + ' Bulan') : '< 1 Bulan';
            }

            const now = new Date();
            const dateStr = now.toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' +
                            now.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' });

            let kebutuhanRows = '';
            if (modalItems.length === 0) {
                kebutuhanRows = `<tr><td colspan="5" class="text-center" style="color: #666; font-style: italic;">Belum ada data kebutuhan investasi barang.</td></tr>`;
            } else {
                modalItems.forEach((item, i) => {
                    const subtotal = (item.qty || 1) * (item.harga || 0);
                    kebutuhanRows += `
                        <tr>
                            <td class="text-center">${dol}{i + 1}</td>
                            <td>${dol}{escapeHtml(item.nama)}</td>
                            <td class="text-center">${dol}{item.qty || 1}</td>
                            <td class="text-right">${dol}{formatRupiah(item.harga || 0)}</td>
                            <td class="text-right">${dol}{formatRupiah(subtotal)}</td>
                        </tr>
                    `;
                });
            }

            let pendapatanRows = '';
            if (incomeItems.length === 0) {
                pendapatanRows = `<tr><td colspan="6" class="text-center" style="color: #666; font-style: italic;">Belum ada data estimasi proyeksi pendapatan.</td></tr>`;
            } else {
                incomeItems.forEach((item, i) => {
                    const jam = item.jam || 13;
                    const monthly = item.monthly !== undefined ? item.monthly : ((item.tarif || 0) * (item.qty || 0) * 30);
                    pendapatanRows += `
                        <tr>
                            <td class="text-center">${dol}{i + 1}</td>
                            <td>${dol}{escapeHtml(item.nama)}</td>
                            <td class="text-center">${dol}{item.qty || 1}</td>
                            <td class="text-right">${dol}{formatRupiah(item.tarif || 0)}</td>
                            <td class="text-center">${dol}{jam} Jam</td>
                            <td class="text-right">${dol}{formatRupiah(monthly)}</td>
                        </tr>
                    `;
                });
            }

            let operasionalRows = '';
            if (expenseItems.length === 0) {
                operasionalRows = `<tr><td colspan="6" class="text-center" style="color: #666; font-style: italic;">Belum ada data rincian beban operasional.</td></tr>`;
            } else {
                expenseItems.forEach((item, i) => {
                    const qty = item.qty || 1;
                    const hari = item.hari || 30;
                    const totalBiaya = item.biaya || 0;
                    const hargaSatuan = (qty > 0 && hari > 0) ? (totalBiaya / (qty * (hari == 30 ? 1 : hari))) : totalBiaya;
                    operasionalRows += `
                        <tr>
                            <td class="text-center">${dol}{i + 1}</td>
                            <td>${dol}{escapeHtml(item.nama)}</td>
                            <td class="text-center">${dol}{qty}</td>
                            <td class="text-right">${dol}{formatRupiah(hargaSatuan)}</td>
                            <td class="text-center">${dol}{hari} Hari</td>
                            <td class="text-right">${dol}{formatRupiah(totalBiaya)}</td>
                        </tr>
                    `;
                });
            }

            const printHtml = `
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Laporan Analisis Kelayakan Usaha - ${dol}{escapeHtml(data.namaUsaha || 'Usaha Saya')}</title>
                    <style>
                        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; padding: 25px; color: #333; line-height: 1.4; background: #fff; margin: 0; }
                        .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #333; padding-bottom: 15px; }
                        .header h1 { margin: 0 0 5px 0; font-size: 24px; color: #111; text-transform: uppercase; }
                        .header p { margin: 2px 0; color: #666; font-size: 12px; }
                        .biz-info { margin-bottom: 25px; padding: 15px; background-color: #f8f9fa; border-left: 4px solid #007bff; border-radius: 4px; }
                        .biz-info table { margin-top: 0; }
                        .biz-info td { border: none; padding: 4px 8px; }
                        .section-title { font-size: 15px; font-weight: bold; color: #007bff; margin-top: 25px; margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 5px; }
                        table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 11px; }
                        th, td { border: 1px solid #e0e0e0; padding: 10px 8px; text-align: left; }
                        th { background-color: #f5f5f5; font-weight: bold; color: #444; }
                        .text-right { text-align: right; }
                        .text-center { text-align: center; }
                        .total-row { font-weight: bold; background-color: #fafafa; }
                        .summary-card { margin-top: 35px; border: 2px solid #28a745; border-radius: 8px; padding: 20px; background-color: #f4fbf6; }
                        .summary-card.negative { border-color: #dc3545; background-color: #fff8f8; }
                        .summary-title { font-size: 16px; font-weight: bold; color: #28a745; margin-top: 0; margin-bottom: 15px; border-bottom: 1px solid #c3e6cb; padding-bottom: 8px; }
                        .summary-card.negative .summary-title { color: #dc3545; border-bottom-color: #f5c6cb; }
                        .summary-flex { display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
                        .summary-item { display: flex; justify-content: space-between; align-items: center; }
                        .summary-divider { height: 1px; background-color: #ddd; margin: 8px 0; }
                        .bold-text { font-weight: bold; }
                        .laba-value { color: #28a745; font-size: 16px; font-weight: bold; }
                        .summary-card.negative .laba-value { color: #dc3545; }
                        .roi-box { margin-top: 15px; padding: 10px 15px; background-color: #e2f0d9; border-radius: 6px; display: flex; justify-content: space-between; font-weight: bold; }
                        .summary-card.negative .roi-box { background-color: #fcdbd9; }
                        .footer-meta { text-align: center; font-size: 10px; color: #888; margin-top: 40px; }
                        tr { page-break-inside: avoid; }
                        @media print {
                            body { padding: 10px; }
                        }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>Laporan Analisis Kelayakan Usaha</h1>
                        <p>Aplikasi Smart Financial System - My Singghara</p>
                        <p>Dicetak pada: ${dol}{dateStr}</p>
                    </div>

                    <div class="biz-info">
                        <table style="width: auto; border:none; margin: 0;">
                            <tr>
                                <td class="bold-text" style="width: 140px; font-size: 12px; padding: 2px 0;">Nama Usaha</td>
                                <td style="font-size: 12px; padding: 2px 0;">: ${dol}{escapeHtml(data.namaUsaha || 'Usaha Saya')}</td>
                            </tr>
                            <tr>
                                <td class="bold-text" style="font-size: 12px; padding: 2px 0;">Modal Awal Usaha</td>
                                <td style="font-size: 12px; padding: 2px 0;">: ${dol}{formatRupiah(data.modalUsaha || 0)}</td>
                            </tr>
                        </table>
                    </div>

                    <!-- KEBUTUHAN BARANG -->
                    <div class="section-title">I. Kebutuhan Investasi Barang &amp; Peralatan</div>
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 5%;">No</th>
                                <th>Nama Barang / Perlengkapan</th>
                                <th class="text-center" style="width: 10%;">Qty</th>
                                <th class="text-right" style="width: 25%;">Harga Satuan</th>
                                <th class="text-right" style="width: 25%;">Total Biaya</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${dol}{kebutuhanRows}
                            <tr class="total-row">
                                <td colspan="4" class="text-right">Total Anggaran Investasi Barang:</td>
                                <td class="text-right" style="color: #007bff;">${dol}{formatRupiah(totalKebutuhan)}</td>
                            </tr>
                        </tbody>
                    </table>

                    <!-- ESTIMASI PENDAPATAN -->
                    <div class="section-title">II. Estimasi Proyeksi Pendapatan (Per Bulan)</div>
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 5%;">No</th>
                                <th>Nama Sewa Produk / Layanan</th>
                                <th class="text-center" style="width: 10%;">Qty</th>
                                <th class="text-right" style="width: 25%;">Tarif / Harga Sewa</th>
                                <th class="text-center" style="width: 15%;">Operasional / Hari</th>
                                <th class="text-right" style="width: 25%;">Total Pendapatan (Bulan)</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${dol}{pendapatanRows}
                            <tr class="total-row">
                                <td colspan="5" class="text-right">Total Estimasi Pendapatan Kotor Per Bulan:</td>
                                <td class="text-right" style="color: #28a745;">${dol}{formatRupiah(totalMonthlyIncome)}</td>
                            </tr>
                        </tbody>
                    </table>

                    <!-- PENGELUARAN OPERASIONAL -->
                    <div class="section-title">III. Rincian Pengeluaran Operasional</div>
                    <table>
                        <thead>
                            <tr>
                                <th style="width: 5%;">No</th>
                                <th>Jenis Pengeluaran / Beban Usaha</th>
                                <th class="text-center" style="width: 10%;">Qty</th>
                                <th class="text-right" style="width: 25%;">Biaya Satuan</th>
                                <th class="text-center" style="width: 15%;">Jumlah Hari Kerja</th>
                                <th class="text-right" style="width: 25%;">Total Pengeluaran</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${dol}{operasionalRows}
                            <tr class="total-row">
                                <td colspan="5" class="text-right">Total Beban Operasional:</td>
                                <td class="text-right" style="color: #dc3545;">${dol}{formatRupiah(totalBebanOperasional)}</td>
                            </tr>
                        </tbody>
                    </table>

                    <!-- KALKULASI KELAYAKAN DAN SUMMARY CARD -->
                    <div class="summary-card ${dol}{labaBersih >= 0 ? '' : 'negative'}">
                        <div class="summary-title">IV. Analisis Kelayakan Finansial &amp; Payback Period (ROI)</div>
                        <div class="summary-flex">
                            <div class="summary-item">
                                <span>Estimasi Pendapatan Kotor (Per Bulan)</span>
                                <span class="bold-text">${dol}{formatRupiah(totalMonthlyIncome)}</span>
                            </div>
                            <div class="summary-item">
                                <span>Total Beban Biaya (Investasi + Operasional)</span>
                                <span class="bold-text" style="color: #dc3545;">-${dol}{formatRupiah(totalBeban)}</span>
                            </div>
                            <div class="summary-divider"></div>
                            <div class="summary-item">
                                <span class="bold-text">Estimasi Laba Bersih (Per Bulan)</span>
                                <span class="laba-value">${dol}{formatRupiah(labaBersih)}</span>
                            </div>
                            
                            <div class="roi-box">
                                <span>PROYEKSI BALIK MODAL (ROI)</span>
                                <span>${dol}{roi}</span>
                            </div>
                        </div>
                    </div>

                    <div class="footer-meta">
                        Laporan ini dibuat secara otomatis melalui modul analisis kelayakan bisnis "My Singghara".<br/>
                        Pastikan parameter dan data diinput secara realistis untuk mendukung keputusan perencanaan bisnis Anda.
                    </div>
                </body>
                </html>
            `;

            const printFrame = document.createElement('iframe');
            printFrame.style.position = 'fixed';
            printFrame.style.right = '0';
            printFrame.style.bottom = '0';
            printFrame.style.width = '0';
            printFrame.style.height = '0';
            printFrame.style.border = '0';
            document.body.appendChild(printFrame);

            const doc = printFrame.contentWindow.document;
            doc.open();
            doc.write(printHtml);
            doc.close();

            setTimeout(() => {
                printFrame.contentWindow.focus();
                printFrame.contentWindow.print();
                setTimeout(() => {
                    if (document.body.contains(printFrame)) {
                        document.body.removeChild(printFrame);
                    }
                }, 2000);
            }, 500);
        }

        ${KeuanganWebScript.getScript(dol)}
        ${KaryawanMuridWebScript.getScript(dol)}

        // ==========================================
        // --- 3. PEMUTAR MUSIK AUDIO ---
        // ==========================================
        window.musicPlaylist = window.musicPlaylist || [];
        window.currentMusicIndex = window.currentMusicIndex || 0;
        window.musicPlayerAudio = window.musicPlayerAudio || new Audio();
        window.isMusicShuffle = window.isMusicShuffle || false;
        window.isMusicRepeat = window.isMusicRepeat || false;
        window.isSeekingMusic = false;

        // Setup audio event listeners once
        if (!window.musicAudioEventsBound) {
            window.musicAudioEventsBound = true;
            
            window.musicPlayerAudio.addEventListener('timeupdate', () => {
                if (window.isSeekingMusic) return;
                const cur = window.musicPlayerAudio.currentTime || 0;
                const dur = window.musicPlayerAudio.duration || 0;
                
                const seekEl = document.getElementById('musicSeekBar');
                if (seekEl && dur > 0) {
                    seekEl.max = dur;
                    seekEl.value = cur;
                }
                const curText = document.getElementById('musicCurrentTimeText');
                if (curText) curText.innerText = formatAudioTime(cur);
            });

            window.musicPlayerAudio.addEventListener('loadedmetadata', () => {
                const dur = window.musicPlayerAudio.duration || 0;
                const durText = document.getElementById('musicDurationText');
                if (durText) durText.innerText = formatAudioTime(dur);
                const seekEl = document.getElementById('musicSeekBar');
                if (seekEl) {
                    seekEl.max = dur;
                    seekEl.value = 0;
                }
            });

            window.musicPlayerAudio.addEventListener('ended', () => {
                if (window.isMusicRepeat) {
                    window.musicPlayerAudio.currentTime = 0;
                    window.musicPlayerAudio.play().catch(e => {});
                } else {
                    nextMusicTrack(false);
                }
            });

            window.musicPlayerAudio.addEventListener('play', () => {
                updateMusicPlayPauseUI(true);
            });

            window.musicPlayerAudio.addEventListener('pause', () => {
                updateMusicPlayPauseUI(false);
            });
        }

        function formatAudioTime(secs) {
            if (isNaN(secs) || secs < 0) return '00:00';
            const m = Math.floor(secs / 60);
            const s = Math.floor(secs % 60);
            return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
        }

        function updateMusicPlayPauseUI(isPlaying) {
            const btn = document.getElementById('btnPlayPauseMusic');
            if (btn) {
                btn.innerHTML = `<i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'} fs-3"></i>`;
            }
            const badge = document.getElementById('musicPlayingStatusBadge');
            if (badge) {
                badge.innerText = isPlaying ? 'Memutar' : 'Dijeda';
                badge.className = isPlaying ? 'badge bg-success' : 'badge bg-secondary';
            }
        }

        function loadMusik() {
            const hasTracks = window.musicPlaylist.length > 0;
            const currentTrack = hasTracks ? (window.musicPlaylist[window.currentMusicIndex] || window.musicPlaylist[0]) : null;
            const isPlaying = !window.musicPlayerAudio.paused && !window.musicPlayerAudio.ended && window.musicPlayerAudio.currentTime > 0;

            let html = `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-music-note-beamed" style="color: #E91E63;"></i> Pemutar Musik
                        </h4>
                        <small class="text-secondary">Putar dan kelola file musik audio (MP3, WAV, M4A, AAC, FLAC)</small>
                    </div>
                    <div class="d-flex align-items-center gap-2">
                        <input type="file" id="localAudioInput" accept="audio/*" multiple class="d-none" onchange="handleMusicFilesUpload(event)">
                        <button class="btn btn-sm btn-brand" onclick="document.getElementById('localAudioInput').click()">
                            <i class="bi bi-folder-plus me-1"></i> Tambah File Musik
                        </button>
                    </div>
                </div>
            `;

            if (!hasTracks) {
                html += `
                    <div class="card card-custom p-5 text-center my-4" style="border-radius: 20px;">
                        <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 80px; height: 80px; background-color: rgba(233, 30, 99, 0.15); color: #E91E63;">
                            <i class="bi bi-music-note-beamed fs-1"></i>
                        </div>
                        <h5 class="fw-bold mb-2">Belum Ada File Musik Dimuat</h5>
                        <p class="text-secondary small mb-4" style="max-width: 480px; margin-inline: auto;">
                            Pilih satu atau beberapa file audio dari komputer/perangkat Anda untuk mulai memutar musik langsung di web connect.
                        </p>
                        <div>
                            <button class="btn btn-brand px-4 py-2" onclick="document.getElementById('localAudioInput').click()">
                                <i class="bi bi-file-earmark-music me-2"></i> Pilih File Musik (MP3 / WAV / M4A)
                            </button>
                        </div>
                    </div>
                `;
            } else {
                html += `
                    <div class="row g-4">
                        <!-- DECK PEMUTAR UTAMA -->
                        <div class="col-12 col-lg-5">
                            <div class="card card-custom p-4 text-center h-100 d-flex flex-column justify-content-between" style="border-radius: 20px;">
                                <div>
                                    <div class="d-flex justify-content-between align-items-center mb-3">
                                        <span id="musicPlayingStatusBadge" class="badge ${dol}{isPlaying ? 'bg-success' : 'bg-secondary'}">${dol}{isPlaying ? 'Memutar' : 'Dijeda'}</span>
                                        <small class="text-secondary">Trek ${dol}{window.currentMusicIndex + 1} dari ${dol}{window.musicPlaylist.length}</small>
                                    </div>

                                    <!-- ICON BANNER MUSIK -->
                                    <div class="my-4 d-flex align-items-center justify-content-center mx-auto rounded-4 shadow-sm" 
                                         style="width: 140px; height: 140px; background: linear-gradient(135deg, #E91E63, #880e4f); color: white;">
                                        <i class="bi bi-music-note-beamed" style="font-size: 64px;"></i>
                                    </div>

                                    <h5 class="fw-bold mb-1 text-truncate" id="musicTitleText" title="${dol}{escapeHtml(currentTrack ? currentTrack.title : '')}">
                                        ${dol}{escapeHtml(currentTrack ? currentTrack.title : 'Pilih Lagu')}
                                    </h5>
                                    <small class="text-secondary d-block mb-3" id="musicArtistText">
                                        ${dol}{escapeHtml(currentTrack ? currentTrack.artist : '')}
                                    </small>
                                </div>

                                <div>
                                    <!-- PROGRESS / SEEK BAR -->
                                    <div class="mb-3">
                                        <input type="range" class="form-range" id="musicSeekBar" min="0" max="${dol}{window.musicPlayerAudio.duration || 100}" value="${dol}{window.musicPlayerAudio.currentTime || 0}"
                                               onmousedown="window.isSeekingMusic = true"
                                               ontouchstart="window.isSeekingMusic = true"
                                               onchange="handleMusicSeek(this.value)"
                                               oninput="handleMusicSeekInput(this.value)">
                                        <div class="d-flex justify-content-between text-secondary" style="font-size: 12px;">
                                            <span id="musicCurrentTimeText">${dol}{formatAudioTime(window.musicPlayerAudio.currentTime || 0)}</span>
                                            <span id="musicDurationText">${dol}{formatAudioTime(window.musicPlayerAudio.duration || 0)}</span>
                                        </div>
                                    </div>

                                    <!-- KONTROL PEMUTAR -->
                                    <div class="d-flex align-items-center justify-content-center gap-3 my-2">
                                        <button class="btn btn-sm ${dol}{window.isMusicShuffle ? 'btn-success' : 'btn-outline-secondary'} rounded-circle" 
                                                style="width: 38px; height: 38px;" onclick="toggleMusicShuffle()" title="Acak / Shuffle">
                                            <i class="bi bi-shuffle"></i>
                                        </button>
                                        <button class="btn btn-outline-secondary rounded-circle" style="width: 44px; height: 44px;" onclick="prevMusicTrack()" title="Lagu Sebelumnya">
                                            <i class="bi bi-skip-start-fill fs-5"></i>
                                        </button>
                                        <button class="btn btn-success rounded-circle shadow" style="width: 56px; height: 56px;" onclick="togglePlayMusic()" id="btnPlayPauseMusic" title="Putar / Jeda">
                                            <i class="bi ${dol}{isPlaying ? 'bi-pause-fill' : 'bi-play-fill'} fs-3"></i>
                                        </button>
                                        <button class="btn btn-outline-secondary rounded-circle" style="width: 44px; height: 44px;" onclick="nextMusicTrack(true)" title="Lagu Berikutnya">
                                            <i class="bi bi-skip-end-fill fs-5"></i>
                                        </button>
                                        <button class="btn btn-sm ${dol}{window.isMusicRepeat ? 'btn-success' : 'btn-outline-secondary'} rounded-circle" 
                                                style="width: 38px; height: 38px;" onclick="toggleMusicRepeat()" title="Ulangi / Repeat">
                                            <i class="bi bi-repeat"></i>
                                        </button>
                                    </div>

                                    <!-- VOLUME CONTROL -->
                                    <div class="d-flex align-items-center justify-content-center gap-2 mt-3 px-3">
                                        <i class="bi bi-volume-down text-secondary fs-5"></i>
                                        <input type="range" class="form-range" style="max-width: 160px;" min="0" max="1" step="0.05" value="${dol}{window.musicPlayerAudio.volume}" oninput="setMusicVolume(this.value)">
                                        <i class="bi bi-volume-up text-secondary fs-5"></i>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- DAFTAR PUTAR / PLAYLIST -->
                        <div class="col-12 col-lg-7">
                            <div class="card card-custom p-4 h-100" style="border-radius: 20px;">
                                <div class="d-flex justify-content-between align-items-center mb-3">
                                    <h6 class="fw-bold mb-0 d-flex align-items-center gap-2">
                                        <i class="bi bi-music-note-list text-primary"></i>
                                        <span>Daftar Putar Musik</span>
                                        <span class="badge bg-secondary bg-opacity-25 text-secondary">${dol}{window.musicPlaylist.length} Trek</span>
                                    </h6>
                                    <div class="d-flex gap-1">
                                        <button class="btn btn-sm btn-outline-secondary" onclick="document.getElementById('localAudioInput').click()">
                                            <i class="bi bi-plus-lg me-1"></i> Tambah
                                        </button>
                                        <button class="btn btn-sm btn-outline-danger" onclick="clearMusicPlaylist()" title="Kosongkan Daftar Putar">
                                            <i class="bi bi-trash"></i>
                                        </button>
                                    </div>
                                </div>

                                <div class="d-flex flex-column gap-2 overflow-auto" style="max-height: 480px;" id="musicPlaylistContainer">
                `;

                window.musicPlaylist.forEach((item, idx) => {
                    const isActive = (idx === window.currentMusicIndex);
                    html += `
                        <div class="p-3 rounded-3 d-flex align-items-center justify-content-between cursor-pointer ${dol}{isActive ? 'border border-success bg-success bg-opacity-10' : 'dashboard-subcard'}" onclick="selectMusicTrack(${dol}{idx})">
                            <div class="d-flex align-items-center gap-3 overflow-hidden">
                                <div class="text-success fw-bold flex-shrink-0" style="width: 24px; text-align: center;">
                                    ${dol}{isActive && isPlaying ? '<i class="bi bi-play-circle-fill fs-5"></i>' : (idx + 1)}
                                </div>
                                <div class="overflow-hidden">
                                    <h6 class="fw-bold mb-0 text-truncate ${dol}{isActive ? 'text-success' : ''}" title="${dol}{escapeHtml(item.title)}">${dol}{escapeHtml(item.title)}</h6>
                                    <small class="text-secondary text-truncate d-block">${dol}{escapeHtml(item.artist || 'File Audio')} &bull; ${dol}{item.size || ''}</small>
                                </div>
                            </div>
                            <div class="d-flex align-items-center gap-2 flex-shrink-0 ms-2">
                                <button class="btn btn-sm ${dol}{isActive ? 'btn-success' : 'btn-outline-secondary'} rounded-circle" style="width: 34px; height: 34px;" onclick="event.stopPropagation(); selectMusicTrack(${dol}{idx})">
                                    <i class="bi ${dol}{(isActive && isPlaying) ? 'bi-pause-fill' : 'bi-play-fill'}"></i>
                                </button>
                                <button class="btn btn-sm text-danger p-1" onclick="event.stopPropagation(); removeMusicTrack(${dol}{idx})" title="Hapus dari daftar putar">
                                    <i class="bi bi-x-lg"></i>
                                </button>
                            </div>
                        </div>
                    `;
                });

                html += `
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            }

            document.getElementById('tab-content').innerHTML = html;
        }

        function handleMusicFilesUpload(event) {
            const files = event.target.files;
            if (!files || files.length === 0) return;

            const startIndex = window.musicPlaylist.length;
            for (let i = 0; i < files.length; i++) {
                const file = files[i];
                const url = URL.createObjectURL(file);
                const sizeMb = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
                window.musicPlaylist.push({
                    id: Date.now() + '_' + i,
                    title: file.name.replace(/\.[^/.]+$/, ""),
                    artist: file.type || "Berkas Audio",
                    size: sizeMb,
                    url: url
                });
            }

            if (startIndex === 0 && window.musicPlaylist.length > 0) {
                window.currentMusicIndex = 0;
                playTrackByIndex(0);
            } else {
                loadMusik();
            }
            event.target.value = '';
        }

        function playTrackByIndex(idx) {
            if (idx < 0 || idx >= window.musicPlaylist.length) return;
            window.currentMusicIndex = idx;
            const track = window.musicPlaylist[idx];
            if (track && track.url) {
                window.musicPlayerAudio.src = track.url;
                window.musicPlayerAudio.play().then(() => {
                    loadMusik();
                }).catch(e => {
                    loadMusik();
                });
            }
        }

        function togglePlayMusic() {
            if (window.musicPlaylist.length === 0) return;
            if (window.musicPlayerAudio.paused) {
                if (!window.musicPlayerAudio.src && window.musicPlaylist[window.currentMusicIndex]) {
                    playTrackByIndex(window.currentMusicIndex);
                } else {
                    window.musicPlayerAudio.play().catch(e => {});
                }
            } else {
                window.musicPlayerAudio.pause();
            }
            loadMusik();
        }

        function selectMusicTrack(idx) {
            if (idx === window.currentMusicIndex && window.musicPlayerAudio.src) {
                togglePlayMusic();
            } else {
                playTrackByIndex(idx);
            }
        }

        function prevMusicTrack() {
            if (window.musicPlaylist.length === 0) return;
            let prevIdx = window.currentMusicIndex - 1;
            if (prevIdx < 0) prevIdx = window.musicPlaylist.length - 1;
            playTrackByIndex(prevIdx);
        }

        function nextMusicTrack(manualClick = true) {
            if (window.musicPlaylist.length === 0) return;
            let nextIdx;
            if (window.isMusicShuffle && window.musicPlaylist.length > 1) {
                do {
                    nextIdx = Math.floor(Math.random() * window.musicPlaylist.length);
                } while (nextIdx === window.currentMusicIndex);
            } else {
                nextIdx = (window.currentMusicIndex + 1) % window.musicPlaylist.length;
            }
            playTrackByIndex(nextIdx);
        }

        function toggleMusicShuffle() {
            window.isMusicShuffle = !window.isMusicShuffle;
            loadMusik();
        }

        function toggleMusicRepeat() {
            window.isMusicRepeat = !window.isMusicRepeat;
            loadMusik();
        }

        function handleMusicSeek(val) {
            window.isSeekingMusic = false;
            window.musicPlayerAudio.currentTime = parseFloat(val);
        }

        function handleMusicSeekInput(val) {
            const curText = document.getElementById('musicCurrentTimeText');
            if (curText) curText.innerText = formatAudioTime(parseFloat(val));
        }

        function setMusicVolume(val) {
            window.musicPlayerAudio.volume = parseFloat(val);
        }

        function removeMusicTrack(idx) {
            if (idx < 0 || idx >= window.musicPlaylist.length) return;
            const isCurrentlyPlaying = (idx === window.currentMusicIndex);
            window.musicPlaylist.splice(idx, 1);

            if (window.musicPlaylist.length === 0) {
                window.musicPlayerAudio.pause();
                window.musicPlayerAudio.src = '';
                window.currentMusicIndex = 0;
            } else if (isCurrentlyPlaying) {
                window.currentMusicIndex = Math.min(idx, window.musicPlaylist.length - 1);
                playTrackByIndex(window.currentMusicIndex);
            } else if (idx < window.currentMusicIndex) {
                window.currentMusicIndex--;
            }
            loadMusik();
        }

        function clearMusicPlaylist() {
            if (!confirm('Kosongkan seluruh daftar lagu?')) return;
            window.musicPlayerAudio.pause();
            window.musicPlayerAudio.src = '';
            window.musicPlaylist = [];
            window.currentMusicIndex = 0;
            loadMusik();
        }

        // ==========================================
        // --- 4. BLOCK PUZZLE CLASSIC (8x8) ---
        // ==========================================
        window.PUZZLE_TEMPLATES = [
            { id: 0, coords: [[0, 0]], colorIndex: 1 }, // Dot 1x1 (Amber)
            { id: 1, coords: [[0, 0], [0, 1]], colorIndex: 2 }, // 1x2 Horizontal (Cyan)
            { id: 2, coords: [[0, 0], [1, 0]], colorIndex: 2 }, // 2x1 Vertical (Cyan)
            { id: 3, coords: [[0, 0], [0, 1], [0, 2]], colorIndex: 3 }, // 1x3 Horizontal (Magenta)
            { id: 4, coords: [[0, 0], [1, 0], [2, 0]], colorIndex: 3 }, // 3x1 Vertical (Magenta)
            { id: 5, coords: [[0, 0], [0, 1], [0, 2], [0, 3]], colorIndex: 4 }, // 1x4 Horizontal (Green)
            { id: 6, coords: [[0, 0], [1, 0], [2, 0], [3, 0]], colorIndex: 4 }, // 4x1 Vertical (Green)
            { id: 7, coords: [[0, 0], [0, 1], [1, 0], [1, 1]], colorIndex: 5 }, // Square 2x2 (Royal Blue)
            { id: 8, coords: [[0, 0], [0, 1], [0, 2], [1, 1]], colorIndex: 6 }, // T-shape Rotated (Purple)
            { id: 9, coords: [[0, 0], [1, 0], [1, 1]], colorIndex: 7 }, // Corner 2x2 (Coral)
            { id: 10, coords: [[0, 0], [1, 0], [2, 0], [2, 1], [2, 2]], colorIndex: 8 }, // Corner 3x3 Large (Orange)
            { id: 11, coords: [[0, 0], [0, 1], [1, 1], [1, 2]], colorIndex: 9 }, // Z-shape (Teal)
            { id: 12, coords: [[0, 1], [1, 0], [1, 1], [1, 2]], colorIndex: 6 } // T-shape Normal (Purple)
        ];

        window.PUZZLE_COLORS = {
            1: '#FBC02D', // Amber / Golden Yellow
            2: '#00BCD4', // Cyan
            3: '#EC407A', // Pink / Magenta
            4: '#4CAF50', // Green
            5: '#1E88E5', // Royal Blue
            6: '#AB47BC', // Purple
            7: '#FF7043', // Coral / Orange-Red
            8: '#FF9800', // Orange
            9: '#009688'  // Teal
        };

        window.puzzleBoard = Array(8).fill(null).map(() => Array(8).fill(0));
        window.puzzleScore = parseInt(localStorage.getItem('singghara_puzzle_score') || '0');
        window.puzzleHighScore = parseInt(localStorage.getItem('singghara_block_puzzle_highscore') || '0');
        window.puzzleCandidates = [];
        window.puzzleSelectedCandIdx = null;
        window.puzzleActiveMode = null; // null | 'singleClear' | 'hammer'
        window.puzzleSoundEnabled = (localStorage.getItem('singghara_puzzle_sound') !== 'false');
        window.puzzleBoosters = {
            rotate: 3,
            clear: 2,
            hammer: 2,
            refresh: 3
        };
        window.puzzleComboStreak = 0;

        // Vector 3D Beveled Jewel Generator matching Android drawBeveledCell
        function getJewelBlockSvg(color, isGhost = false) {
            const c = color || '#2C5E6A';
            const cClean = c.replace(/[^a-zA-Z0-9]/g, '');
            const id = 'jg_' + cClean + (isGhost ? '_g' : '_s');
            const opacity = isGhost ? 0.45 : 1.0;
            const glossA1 = isGhost ? 0.25 : 0.65;
            const glossA2 = isGhost ? 0.05 : 0.18;
            const leftA = isGhost ? 0.20 : 0.45;
            const topA = isGhost ? 0.15 : 0.32;
            const rightA = isGhost ? 0.15 : 0.35;
            const botA = isGhost ? 0.20 : 0.45;
            const diagWhite = isGhost ? 0.35 : 0.85;
            const diagBlack = isGhost ? 0.30 : 0.65;
            const peakWhite = isGhost ? 0.40 : 0.85;
            const peakBlack = isGhost ? 0.30 : 0.55;

            return `<svg viewBox="0 0 100 100" width="100%" height="100%" style="display:block; border-radius:4px; overflow:hidden; opacity:${dol}{opacity}; pointer-events:none;">
                <defs>
                    <linearGradient id="${dol}{id}" x1="22%" y1="22%" x2="78%" y2="78%">
                        <stop offset="0%" stop-color="#ffffff" stop-opacity="${dol}{glossA1}"/>
                        <stop offset="42%" stop-color="#ffffff" stop-opacity="${dol}{glossA2}"/>
                        <stop offset="60%" stop-color="#000000" stop-opacity="0"/>
                        <stop offset="100%" stop-color="#000000" stop-opacity="${dol}{isGhost ? 0 : 0.22}"/>
                    </linearGradient>
                </defs>
                <rect width="100" height="100" rx="4" fill="${dol}{c}"/>
                <polygon points="0,0 22,22 22,78 0,100" fill="rgba(255,255,255,${dol}{leftA})"/>
                <polygon points="0,0 100,0 78,22 22,22" fill="rgba(255,255,255,${dol}{topA})"/>
                <polygon points="100,0 100,100 78,78 78,22" fill="rgba(0,0,0,${dol}{rightA})"/>
                <polygon points="0,100 100,100 78,78 22,78" fill="rgba(0,0,0,${dol}{botA})"/>
                <rect x="22" y="22" width="56" height="56" fill="${dol}{c}"/>
                <rect x="22" y="22" width="56" height="56" fill="url(#${dol}{id})"/>
                <line x1="0" y1="0" x2="22" y2="22" stroke="rgba(255,255,255,${dol}{diagWhite})" stroke-width="2"/>
                <line x1="0" y1="100" x2="22" y2="78" stroke="rgba(255,255,255,${dol}{isGhost ? 0.25 : 0.60})" stroke-width="2"/>
                <line x1="100" y1="0" x2="78" y2="22" stroke="rgba(0,0,0,${dol}{isGhost ? 0.15 : 0.40})" stroke-width="2"/>
                <line x1="100" y1="100" x2="78" y2="78" stroke="rgba(0,0,0,${dol}{diagBlack})" stroke-width="2"/>
                <line x1="22" y1="22" x2="78" y2="22" stroke="rgba(255,255,255,${dol}{peakWhite})" stroke-width="1.5"/>
                <line x1="22" y1="22" x2="22" y2="78" stroke="rgba(255,255,255,${dol}{peakWhite})" stroke-width="1.5"/>
                <line x1="22" y1="78" x2="78" y2="78" stroke="rgba(0,0,0,${dol}{peakBlack})" stroke-width="1.5"/>
                <line x1="78" y1="22" x2="78" y2="78" stroke="rgba(0,0,0,${dol}{peakBlack})" stroke-width="1.5"/>
                <line x1="0" y1="0" x2="100" y2="0" stroke="rgba(255,255,255,${dol}{isGhost ? 0.20 : 0.45})" stroke-width="1.2"/>
                <line x1="0" y1="0" x2="0" y2="100" stroke="rgba(255,255,255,${dol}{isGhost ? 0.20 : 0.45})" stroke-width="1.2"/>
                <line x1="0" y1="100" x2="100" y2="100" stroke="rgba(0,0,0,${dol}{isGhost ? 0.25 : 0.50})" stroke-width="1.2"/>
                <line x1="100" y1="0" x2="100" y2="100" stroke="rgba(0,0,0,${dol}{isGhost ? 0.25 : 0.50})" stroke-width="1.2"/>
            </svg>`;
        }

        // Sound Synthesis helper using Web Audio API (Sine wave)
        function playPuzzleSine(freq, durationMs, vol = 0.12, type = 'sine') {
            if (!window.puzzleSoundEnabled) return;
            try {
                if (!window.puzzleAudioCtx) {
                    window.puzzleAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
                }
                if (window.puzzleAudioCtx.state === 'suspended') {
                    window.puzzleAudioCtx.resume();
                }
                const osc = window.puzzleAudioCtx.createOscillator();
                const gain = window.puzzleAudioCtx.createGain();
                osc.type = type;
                osc.frequency.setValueAtTime(freq, window.puzzleAudioCtx.currentTime);
                gain.gain.setValueAtTime(vol, window.puzzleAudioCtx.currentTime);
                gain.gain.exponentialRampToValueAtTime(0.0001, window.puzzleAudioCtx.currentTime + (durationMs / 1000));
                osc.connect(gain);
                gain.connect(window.puzzleAudioCtx.destination);
                osc.start();
                osc.stop(window.puzzleAudioCtx.currentTime + (durationMs / 1000));
            } catch(e) {}
        }

        function playPuzzleTap() { playPuzzleSine(523.25, 45, 0.12); }
        function playPuzzleDrop() {
            playPuzzleSine(392.00, 45, 0.15);
            setTimeout(() => playPuzzleSine(523.25, 65, 0.15), 40);
        }
        function playPuzzleClear() {
            const notes = [523.25, 659.25, 783.99, 1046.50];
            notes.forEach((n, idx) => {
                setTimeout(() => playPuzzleSine(n, 80, 0.12), idx * 60);
            });
        }
        function playPuzzlePowerUp() {
            const notes = [587.33, 783.99, 1174.66];
            notes.forEach((n, idx) => {
                setTimeout(() => playPuzzleSine(n, 120, 0.1), idx * 80);
            });
        }
        function playPuzzleBombSound() {
            playPuzzleSine(110.0, 250, 0.35, 'triangle');
            setTimeout(() => playPuzzleSine(75.0, 300, 0.4, 'sawtooth'), 60);
        }
        function playPuzzleGameOverSound() {
            const notes = [440.00, 392.00, 329.63, 261.63];
            notes.forEach((n, idx) => {
                setTimeout(() => playPuzzleSine(n, 220, 0.12), idx * 180);
            });
        }
        function playPuzzleComboSound() {
            playPuzzleSine(587.33, 90, 0.20);
            setTimeout(() => playPuzzleSine(880.00, 90, 0.20), 70);
            setTimeout(() => playPuzzleSine(1174.66, 250, 0.22), 170);
        }

        function rotatePuzzleCoords(coords) {
            const rotated = coords.map(([r, c]) => [c, -r]);
            const minR = Math.min(...rotated.map(p => p[0]));
            const minC = Math.min(...rotated.map(p => p[1]));
            return rotated.map(([r, c]) => [r - minR, c - minC]);
        }

        function generatePuzzleCandidates() {
            window.puzzleCandidates = [];
            for (let i = 0; i < 3; i++) {
                const t = window.PUZZLE_TEMPLATES[Math.floor(Math.random() * window.PUZZLE_TEMPLATES.length)];
                window.puzzleCandidates.push({
                    id: i,
                    templateId: t.id,
                    coords: JSON.parse(JSON.stringify(t.coords)),
                    colorIndex: t.colorIndex,
                    used: false
                });
            }
        }

        function togglePuzzleSound() {
            window.puzzleSoundEnabled = !window.puzzleSoundEnabled;
            localStorage.setItem('singghara_puzzle_sound', window.puzzleSoundEnabled.toString());
            loadBlock();
        }

        function getPuzzleMilestoneData(score) {
            let nextMilestone = 500;
            let prevMilestone = 0;
            if (score < 500) { nextMilestone = 500; prevMilestone = 0; }
            else if (score < 1000) { nextMilestone = 1000; prevMilestone = 500; }
            else if (score < 2500) { nextMilestone = 2500; prevMilestone = 1000; }
            else if (score < 5000) { nextMilestone = 5000; prevMilestone = 2500; }
            else if (score < 10000) { nextMilestone = 10000; prevMilestone = 5000; }
            else {
                nextMilestone = (Math.floor(score / 5000) + 1) * 5000;
                prevMilestone = Math.floor(score / 5000) * 5000;
            }
            const percent = Math.min(100, Math.max(0, Math.round(((score - prevMilestone) / (nextMilestone - prevMilestone)) * 100)));
            return { nextMilestone, prevMilestone, percent };
        }

        function loadBlock() {
            if (!window.puzzleCandidates || window.puzzleCandidates.length === 0 || window.puzzleCandidates.every(c => c.used)) {
                generatePuzzleCandidates();
            }

            const milestone = getPuzzleMilestoneData(window.puzzleScore);

            let html = `
                <div class="puzzle-game-wrapper">
                    <!-- MAIN 3-COLUMN LAYOUT: 
                         LEFT: COMPACT VERTICAL STATS CARD
                         CENTER: GAME BOARD & BOOSTERS
                         RIGHT: COMPACT VERTICAL BLOCKS TRAY (KARTU BLOCK)
                    -->
                    <div class="row g-2 g-md-3 justify-content-center align-items-stretch">
                        <!-- LEFT COLUMN: COMPACT VERTICAL STATS & PROGRESS CARD -->
                        <div class="col-12 col-sm-6 col-md-3 col-lg-3 d-flex flex-column" style="max-width: 210px;">
                            <div class="puzzle-stats-card p-2.5 d-flex flex-column justify-content-between h-100" style="border-radius: 16px;">
                                <!-- TOP SECTION: HEADER & STATS & PROGRESS -->
                                <div class="d-flex flex-column gap-2">
                                    <!-- HEADER BADGE -->
                                    <div class="d-flex align-items-center justify-content-between pb-1 border-bottom border-white border-opacity-10">
                                        <span class="fw-bold text-warning d-flex align-items-center gap-1" style="font-size: 10px;">
                                            <i class="bi bi-bar-chart-fill"></i> STATISTIK
                                        </span>
                                        <span class="badge bg-dark bg-opacity-50 text-white-50 px-1 py-0.5" style="font-size: 8px;">8x8</span>
                                    </div>

                                    <!-- 1. REKOR TERTINGGI (COMPACT) -->
                                    <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(251, 192, 45, 0.3);">
                                        <div class="d-flex align-items-center gap-1 mb-0.5">
                                            <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 22px; height: 22px; background: rgba(251, 192, 45, 0.18); border: 1px solid #FBC02D;">
                                                <i class="bi bi-trophy-fill text-warning" style="font-size: 10px;"></i>
                                            </div>
                                            <small class="text-white-50" style="font-size: 9px; font-weight: 700; text-transform: uppercase;">Rekor Tertinggi</small>
                                        </div>
                                        <h4 class="fw-black text-warning mb-0 text-end pe-1" id="puzzleHighScoreText" style="font-size: 18px; letter-spacing: -0.5px; line-height: 1.2;">${dol}{window.puzzleHighScore}</h4>
                                    </div>

                                    <!-- 2. SKOR SEKARANG (COMPACT) -->
                                    <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(0, 188, 212, 0.3);">
                                        <div class="d-flex align-items-center gap-1 mb-0.5">
                                            <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 22px; height: 22px; background: rgba(0, 188, 212, 0.18); border: 1px solid #00BCD4;">
                                                <i class="bi bi-lightning-charge-fill" style="color: #81D4FA; font-size: 10px;"></i>
                                            </div>
                                            <small class="text-white-50" style="font-size: 9px; font-weight: 700; text-transform: uppercase;">Skor Sekarang</small>
                                        </div>
                                        <h4 class="fw-black mb-0 text-end pe-1" style="color: #81D4FA; font-size: 18px; letter-spacing: -0.5px; line-height: 1.2;" id="puzzleScoreText">${dol}{window.puzzleScore}</h4>
                                    </div>

                                    <!-- 3. PROGRESS TARGET (COMPACT) -->
                                    <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(255, 255, 255, 0.1);">
                                        <div class="d-flex justify-content-between align-items-center mb-1">
                                            <small class="text-white-50 fw-bold" style="font-size: 8.5px; text-transform: uppercase;">Progress Target</small>
                                            <span class="badge" id="puzzleProgressPercentText" style="background: rgba(0, 188, 212, 0.2); color: #81D4FA; border: 1px solid #00BCD4; font-size: 8.5px; padding: 1px 4px;">${dol}{milestone.percent}%</span>
                                        </div>
                                        <div class="puzzle-progress-bar mb-1" style="height: 5px; background: rgba(0,0,0,0.45); border-radius: 4px; overflow: hidden; border: 1px solid rgba(255,255,255,0.08);">
                                            <div class="puzzle-progress-fill" id="puzzleProgressFillBar" style="width: ${dol}{milestone.percent}%; height: 100%; background: linear-gradient(90deg, #00BCD4, #4CAF50); border-radius: 4px; transition: width 0.3s ease;"></div>
                                        </div>
                                        <div class="d-flex justify-content-between text-white-50" style="font-size: 8.5px;">
                                            <span id="puzzleCurrentScoreStatText">Skor: ${dol}{window.puzzleScore}</span>
                                            <span id="puzzleTargetScoreText" style="color: #81D4FA; font-weight: 600;">Target: ${dol}{milestone.nextMilestone}</span>
                                        </div>
                                    </div>
                                </div>

                                <!-- BOTTOM SECTION: 4 BOOSTER ITEM / POWER-UPS -->
                                <div class="pt-2 mt-2 border-top border-white border-opacity-10">
                                    <div class="d-flex align-items-center justify-content-between mb-1.5 px-0.5">
                                        <small class="text-white-50 fw-bold" style="font-size: 8.5px; text-transform: uppercase; letter-spacing: 0.5px;">Booster Power-Up</small>
                                        <span class="badge bg-dark bg-opacity-50 text-white-50" style="font-size: 7.5px;">Item</span>
                                    </div>
                                    <div class="row g-1.5">
                                        <div class="col-6">
                                            <button class="btn puzzle-booster-btn w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleRotate()" title="Putar 90¬∞ Balok yang Dipilih">
                                                <i class="bi bi-arrow-repeat text-warning" style="font-size: 13.5px;"></i>
                                                <span style="font-size: 8.5px; margin-top: 1px;">Putar</span>
                                                <span class="badge rounded-pill bg-warning text-dark position-absolute top-0 end-0 m-0.5" id="puzzleBoosterRotateCount" style="font-size: 7.5px; padding: 1px 3.5px;">${dol}{window.puzzleBoosters.rotate}</span>
                                            </button>
                                        </div>
                                        <div class="col-6">
                                            <button class="btn puzzle-booster-btn ${dol}{window.puzzleActiveMode === 'singleClear' ? 'active' : ''} w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleSingleClear()" title="Hapus 1 Balok di Kotak Papan">
                                                <i class="bi bi-eraser-fill text-info" style="font-size: 13.5px;"></i>
                                                <span style="font-size: 8.5px; margin-top: 1px;">Hapus 1x1</span>
                                                <span class="badge rounded-pill bg-info text-dark position-absolute top-0 end-0 m-0.5" id="puzzleBoosterClearCount" style="font-size: 7.5px; padding: 1px 3.5px;">${dol}{window.puzzleBoosters.clear}</span>
                                            </button>
                                        </div>
                                        <div class="col-6">
                                            <button class="btn puzzle-booster-btn ${dol}{window.puzzleActiveMode === 'hammer' ? 'active' : ''} w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleHammer()" title="Palu: Hancurkan Area 3x3">
                                                <i class="bi bi-hammer text-danger" style="font-size: 13.5px;"></i>
                                                <span style="font-size: 8.5px; margin-top: 1px;">Palu 3x3</span>
                                                <span class="badge rounded-pill bg-danger text-white position-absolute top-0 end-0 m-0.5" id="puzzleBoosterHammerCount" style="font-size: 7.5px; padding: 1px 3.5px;">${dol}{window.puzzleBoosters.hammer}</span>
                                            </button>
                                        </div>
                                        <div class="col-6">
                                            <button class="btn puzzle-booster-btn w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleRefresh()" title="Acak Ulang 3 Pilihan Balok">
                                                <i class="bi bi-shuffle text-success" style="font-size: 13.5px;"></i>
                                                <span style="font-size: 8.5px; margin-top: 1px;">Acak</span>
                                                <span class="badge rounded-pill bg-success text-white position-absolute top-0 end-0 m-0.5" id="puzzleBoosterRefreshCount" style="font-size: 7.5px; padding: 1px 3.5px;">${dol}{window.puzzleBoosters.refresh}</span>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- CENTER COLUMN: PUZZLE BOARD & GAMEPLAY AREA -->
                        <div class="col-12 col-md-6 col-lg-6 d-flex flex-column align-items-center" style="max-width: 560px;">
                            <div class="card card-custom p-2.5 p-md-3 position-relative h-100 w-100 d-flex flex-column justify-content-center align-items-center" style="border-radius: 18px;">
                                
                                <!-- CENTER TITLE & CONTROLS (DIATAS PAPAN GAME) -->
                                <div class="d-flex align-items-center justify-content-between w-100 mb-2.5 px-1">
                                    <div class="d-none d-sm-block" style="width: 76px;"></div>
                                    <div class="text-center flex-grow-1">
                                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center justify-content-center gap-2">
                                            <i class="bi bi-grid-fill" style="color: #4CAF50;"></i> Block Puzzle Classic
                                        </h4>
                                        <small class="text-white-50" style="font-size: 11.5px;">Susun balok 8x8, eliminasi baris &amp; kolom!</small>
                                    </div>
                                    <div class="d-flex align-items-center justify-content-end gap-1.5" style="min-width: 76px;">
                                        <button class="btn btn-sm ${dol}{window.puzzleSoundEnabled ? 'btn-outline-success' : 'btn-outline-secondary'} rounded-circle d-flex align-items-center justify-content-center" style="width: 32px; height: 32px; padding: 0;" onclick="togglePuzzleSound()" title="Efek Suara">
                                            <i class="bi ${dol}{window.puzzleSoundEnabled ? 'bi-volume-up-fill' : 'bi-volume-mute-fill'}" style="font-size: 14px;"></i>
                                        </button>
                                        <button class="btn btn-sm btn-outline-danger rounded-circle d-flex align-items-center justify-content-center" style="width: 32px; height: 32px; padding: 0;" onclick="restartBlockPuzzle()" title="Mulai Ulang Game">
                                            <i class="bi bi-arrow-clockwise" style="font-size: 14px;"></i>
                                        </button>
                                    </div>
                                </div>

                                <!-- COMBO TOAST OVERLAY -->
                                <div id="puzzleComboToast" class="puzzle-combo-toast"></div>

                                <!-- FIREWORKS CANVAS -->
                                <canvas id="puzzleFireworksCanvas" width="500" height="500" class="position-absolute top-0 start-0 w-100 h-100 pointer-events-none" style="z-index: 50; display: none;"></canvas>

                                <!-- MODE INDICATOR BANNER IF POWERUP ACTIVE -->
                                ${dol}{window.puzzleActiveMode === 'singleClear' ? `
                                    <div class="alert alert-info py-1.5 px-2.5 mb-2 d-flex align-items-center justify-content-between rounded-pill small" style="font-size: 11px;">
                                        <span><i class="bi bi-eraser-fill me-1 fs-6"></i> <strong>Hapus 1x1:</strong> Klik 1 balok pada kotak papan!</span>
                                        <button class="btn btn-sm btn-link text-dark p-0 fw-bold" onclick="cancelPuzzlePowerup()">Batal</button>
                                    </div>
                                ` : ''}

                                ${dol}{window.puzzleActiveMode === 'hammer' ? `
                                    <div class="alert alert-warning py-1.5 px-2.5 mb-2 d-flex align-items-center justify-content-between rounded-pill small" style="font-size: 11px;">
                                        <span><i class="bi bi-hammer me-1 fs-6"></i> <strong>Palu 3x3:</strong> Klik kotak di papan untuk hancurkan 3x3!</span>
                                        <button class="btn btn-sm btn-link text-dark p-0 fw-bold" onclick="cancelPuzzlePowerup()">Batal</button>
                                    </div>
                                ` : ''}

                                <!-- 8x8 BOARD GRID (Identical Deep Slate Jewel Theme) -->
                                <div class="puzzle-board-container mx-auto" 
                                     id="puzzleBoardContainer"
                                     ondragover="handlePuzzleBoardDragOver(event)"
                                     ondragleave="handlePuzzleBoardDragLeave(event)"
                                     ondrop="handlePuzzleBoardDrop(event)">
                                    <div class="puzzle-grid" id="puzzleBoardGrid">
                    `;

                    for (let r = 0; r < 8; r++) {
                        for (let c = 0; c < 8; c++) {
                            const colorIdx = window.puzzleBoard[r][c];
                            const filled = colorIdx !== 0;
                            const bgCol = filled ? (window.PUZZLE_COLORS[colorIdx] || '#2C5E6A') : '';
                            
                            html += `
                                <div class="puzzle-cell ${dol}{filled ? 'puzzle-cell-filled' : 'puzzle-cell-empty'}"
                                     id="puzzle-cell-${dol}{r}-${dol}{c}"
                                     data-r="${dol}{r}" data-c="${dol}{c}"
                                     onclick="handlePuzzleCellClick(${dol}{r}, ${dol}{c})"
                                     onmouseenter="handlePuzzleCellMouseEnter(${dol}{r}, ${dol}{c})"
                                     onmouseleave="handlePuzzleCellMouseLeave(${dol}{r}, ${dol}{c})">
                                    ${dol}{filled ? getJewelBlockSvg(bgCol) : ''}
                                </div>
                            `;
                        }
                    }

                    html += `
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- RIGHT COLUMN: COMPACT VERTICAL BLOCKS TRAY (KARTU BLOCK) -->
                        <div class="col-12 col-sm-6 col-md-3 col-lg-3 d-flex flex-column" style="max-width: 195px;">
                            <div class="puzzle-tray-container p-2.5 d-flex flex-column justify-content-between h-100 w-100" id="puzzleTrayArea" style="border-radius: 16px;">
                                <div class="d-flex align-items-center justify-content-between pb-1 border-bottom border-white border-opacity-10 w-100 px-1">
                                    <span class="fw-bold text-info d-flex align-items-center gap-1" style="font-size: 10px;">
                                        <i class="bi bi-boxes"></i> PILIHAN BALOK
                                    </span>
                                    <span class="badge bg-dark bg-opacity-50 text-white-50 px-1 py-0.5" style="font-size: 8px;">3 OPSI</span>
                                </div>

                                <div class="d-flex flex-column justify-content-around flex-grow-1 my-2 gap-2 w-100" id="puzzlePiecesTray">
                    `;

                    window.puzzleCandidates.forEach((cand, idx) => {
                        const isSelected = (window.puzzleSelectedCandIdx === idx);
                        html += `
                            <div class="puzzle-tray-card ${dol}{isSelected ? 'selected' : ''} ${dol}{cand.used ? 'opacity-0 pointer-events-none' : ''}"
                                 id="puzzle-cand-card-${dol}{idx}"
                                 onpointerdown="startPuzzlePointerDrag(event, ${dol}{idx})"
                                 title="Klik atau drag balok ini ke papan">
                                ${dol}{renderPuzzleShapeHtml(cand)}
                            </div>
                        `;
                    });

                    html += `
                                </div>

                                <small class="text-white-50 text-center d-block mt-0.5" style="font-size: 8.5px; opacity: 0.7;">
                                    <i class="bi bi-arrows-move me-0.5"></i> Drag / klik ke papan
                                </small>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- GAME OVER MODAL -->
                <div class="modal fade" id="puzzleGameOverModal" tabindex="-1" data-bs-backdrop="static">
                    <div class="modal-dialog modal-dialog-centered modal-sm">
                        <div class="modal-content card-custom text-center p-4" style="border-radius: 24px; background: #13262F; border: 2px solid #2C5E6A; color: #E0F2F1;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 70px; height: 70px; background: rgba(233, 30, 99, 0.18); color: #E91E63; border: 2px solid #E91E63;">
                                <i class="bi bi-emoji-frown fs-1"></i>
                            </div>
                            <h4 class="fw-bold mb-1 text-white">Game Over!</h4>
                            <p class="text-secondary small mb-3">Tidak ada lagi ruang yang cukup untuk balok berikutnya.</p>
                            
                            <div class="p-3 rounded-3 mb-3" style="background: #183842; border: 1.5px solid #2C5E6A;">
                                <small class="text-white-50 d-block">Skor Akhir</small>
                                <h3 class="fw-black text-warning mb-1" id="puzzleFinalScoreVal">${dol}{window.puzzleScore}</h3>
                                <small style="color: #81D4FA;">High Score: ${dol}{window.puzzleHighScore}</small>
                            </div>

                            <button class="btn btn-brand w-100 py-2 fw-bold" onclick="restartBlockPuzzle(true)">
                                <i class="bi bi-play-circle me-1"></i> Main Lagi
                            </button>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
            checkPuzzleGameOver();
        }

        function renderPuzzleShapeHtml(cand) {
            if (!cand || cand.used) return '';
            const coords = cand.coords;
            const maxR = Math.max(...coords.map(p => p[0]));
            const maxC = Math.max(...coords.map(p => p[1]));
            const color = window.PUZZLE_COLORS[cand.colorIndex] || '#2C5E6A';

            let grid = Array(maxR + 1).fill(null).map(() => Array(maxC + 1).fill(false));
            coords.forEach(([r, c]) => grid[r][c] = true);

            const svgBlock = getJewelBlockSvg(color);

            let html = '<div class="d-inline-flex flex-column gap-1 pointer-events-none">';
            for (let r = 0; r <= maxR; r++) {
                html += '<div class="d-flex gap-1">';
                for (let c = 0; c <= maxC; c++) {
                    if (grid[r][c]) {
                        html += `<div style="width: 20px; height: 20px; display: flex; align-items: center; justify-content: center;">${dol}{svgBlock}</div>`;
                    } else {
                        html += `<div style="width: 20px; height: 20px; opacity: 0;"></div>`;
                    }
                }
                html += '</div>';
            }
            html += '</div>';
            return html;
        }

        function renderDragShapeHtml(cand, cellW, cellH) {
            if (!cand || cand.used) return '';
            const coords = cand.coords;
            const maxR = Math.max(...coords.map(p => p[0]));
            const maxC = Math.max(...coords.map(p => p[1]));
            const color = window.PUZZLE_COLORS[cand.colorIndex] || '#2C5E6A';

            let grid = Array(maxR + 1).fill(null).map(() => Array(maxC + 1).fill(false));
            coords.forEach(([r, c]) => grid[r][c] = true);

            const svgBlock = getJewelBlockSvg(color);

            let html = `<div style="display: inline-flex; flex-direction: column; gap: 0px; pointer-events: none;">`;
            for (let r = 0; r <= maxR; r++) {
                html += '<div style="display: flex; gap: 0px;">';
                for (let c = 0; c <= maxC; c++) {
                    if (grid[r][c]) {
                        html += `<div style="width: ${dol}{cellW}px; height: ${dol}{cellH}px; padding: 1px; box-sizing: border-box;">${dol}{svgBlock}</div>`;
                    } else {
                        html += `<div style="width: ${dol}{cellW}px; height: ${dol}{cellH}px; opacity: 0;"></div>`;
                    }
                }
                html += '</div>';
            }
            html += '</div>';
            return html;
        }

        function selectPuzzleCandidate(idx) {
            if (!window.puzzleCandidates[idx] || window.puzzleCandidates[idx].used) return;
            playPuzzleTap();
            window.puzzleActiveMode = null;
            if (window.puzzleSelectedCandIdx === idx) {
                window.puzzleSelectedCandIdx = null;
            } else {
                window.puzzleSelectedCandIdx = idx;
            }
            loadBlock();
        }

        // Pointer-based Drag & Drop Engine (Touch & Mouse Native Support)
        window.puzzleActiveDrag = null;

        function startPuzzlePointerDrag(e, idx) {
            if (e.button !== undefined && e.button !== 0) return;
            const cand = window.puzzleCandidates[idx];
            if (!cand || cand.used) return;

            e.preventDefault();
            const cardEl = document.getElementById(`puzzle-cand-card-${dol}{idx}`);
            const boardEl = document.getElementById('puzzleBoardGrid');
            if (!boardEl) return;

            const boardRect = boardEl.getBoundingClientRect();
            const cellW = boardRect.width / 8;
            const cellH = boardRect.height / 8;

            const coords = cand.coords;
            const maxR = Math.max(...coords.map(p => p[0]));
            const maxC = Math.max(...coords.map(p => p[1]));
            const shapeW = (maxC + 1) * cellW;
            const shapeH = (maxR + 1) * cellH;

            // Lift above finger/pointer so placement area is clearly visible
            const liftOffsetY = -(shapeH + 30);

            window.puzzleActiveDrag = {
                idx: idx,
                cand: cand,
                startX: e.clientX,
                startY: e.clientY,
                hasMoved: false,
                cardEl: cardEl,
                cellW: cellW,
                cellH: cellH,
                shapeW: shapeW,
                shapeH: shapeH,
                liftOffsetY: liftOffsetY,
                floatingEl: null,
                targetRow: null,
                targetCol: null,
                canFit: false
            };

            window.addEventListener('pointermove', handlePuzzlePointerMove, { passive: false });
            window.addEventListener('pointerup', handlePuzzlePointerUp);
            window.addEventListener('pointercancel', handlePuzzlePointerCancel);
        }

        function handlePuzzlePointerMove(e) {
            const drag = window.puzzleActiveDrag;
            if (!drag) return;
            e.preventDefault();

            const dx = e.clientX - drag.startX;
            const dy = e.clientY - drag.startY;

            if (!drag.hasMoved && Math.hypot(dx, dy) > 5) {
                drag.hasMoved = true;
                // Sembunyikan balok dari kontainer tempatnya di nampan bawah
                if (drag.cardEl) {
                    drag.cardEl.classList.add('is-dragging');
                }
                playPuzzleTap();

                const floating = document.createElement('div');
                floating.id = 'puzzleFloatingPiece';
                floating.className = 'puzzle-floating-drag-piece';
                floating.innerHTML = renderDragShapeHtml(drag.cand, drag.cellW, drag.cellH);
                document.body.appendChild(floating);
                drag.floatingEl = floating;
            }

            if (drag.hasMoved && drag.floatingEl) {
                const posX = e.clientX - (drag.shapeW / 2);
                const posY = e.clientY + drag.liftOffsetY;

                drag.floatingEl.style.left = `${dol}{posX}px`;
                drag.floatingEl.style.top = `${dol}{posY}px`;

                const boardEl = document.getElementById('puzzleBoardGrid');
                if (boardEl) {
                    const boardRect = boardEl.getBoundingClientRect();
                    const relX = posX - boardRect.left;
                    const relY = posY - boardRect.top;

                    const col = Math.round(relX / drag.cellW);
                    const row = Math.round(relY / drag.cellH);

                    if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                        drag.targetRow = row;
                        drag.targetCol = col;
                        drag.canFit = checkPuzzleFit(row, col, drag.cand);
                        previewPuzzlePlacement(row, col, drag.idx);
                    } else {
                        drag.targetRow = null;
                        drag.targetCol = null;
                        drag.canFit = false;
                        clearPuzzlePreviews();
                    }
                }
            }
        }

        function handlePuzzlePointerUp(e) {
            const drag = window.puzzleActiveDrag;
            if (!drag) return;
            window.puzzleActiveDrag = null;

            window.removeEventListener('pointermove', handlePuzzlePointerMove);
            window.removeEventListener('pointerup', handlePuzzlePointerUp);
            window.removeEventListener('pointercancel', handlePuzzlePointerCancel);

            if (drag.floatingEl) drag.floatingEl.remove();
            if (drag.cardEl) drag.cardEl.classList.remove('is-dragging');
            clearPuzzlePreviews();

            if (drag.hasMoved) {
                if (drag.targetRow !== null && drag.targetCol !== null && drag.canFit) {
                    tryPlacePuzzleCandidate(drag.targetRow, drag.targetCol, drag.idx);
                } else {
                    window.puzzleSelectedCandIdx = null;
                    loadBlock();
                }
            } else {
                selectPuzzleCandidate(drag.idx);
            }
        }

        function handlePuzzlePointerCancel(e) {
            const drag = window.puzzleActiveDrag;
            if (!drag) return;
            window.puzzleActiveDrag = null;

            window.removeEventListener('pointermove', handlePuzzlePointerMove);
            window.removeEventListener('pointerup', handlePuzzlePointerUp);
            window.removeEventListener('pointercancel', handlePuzzlePointerCancel);

            if (drag.floatingEl) drag.floatingEl.remove();
            if (drag.cardEl) drag.cardEl.classList.remove('is-dragging');
            clearPuzzlePreviews();
            loadBlock();
        }

        function checkPuzzleFit(startR, startC, cand) {
            if (!cand || cand.used) return false;
            for (const [dr, dc] of cand.coords) {
                const tr = startR + dr;
                const tc = startC + dc;
                if (tr < 0 || tr >= 8 || tc < 0 || tc >= 8 || window.puzzleBoard[tr][tc] !== 0) {
                    return false;
                }
            }
            return true;
        }

        function previewPuzzlePlacement(startR, startC, candIdx) {
            clearPuzzlePreviews();
            if (candIdx === null || candIdx === undefined) return;
            const cand = window.puzzleCandidates[candIdx];
            if (!cand || cand.used) return;

            const canFit = checkPuzzleFit(startR, startC, cand);
            const candColor = window.PUZZLE_COLORS[cand.colorIndex] || '#2C5E6A';

            cand.coords.forEach(([dr, dc]) => {
                const tr = startR + dr;
                const tc = startC + dc;
                if (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {
                    const cell = document.getElementById(`puzzle-cell-${dol}{tr}-${dol}{tc}`);
                    if (cell) {
                        cell.classList.add(canFit ? 'puzzle-preview-valid' : 'puzzle-preview-invalid');
                        if (canFit && window.puzzleBoard[tr][tc] === 0) {
                            cell.innerHTML = getJewelBlockSvg(candColor, true);
                        }
                    }
                }
            });
        }

        function clearPuzzlePreviews() {
            for (let r = 0; r < 8; r++) {
                for (let c = 0; c < 8; c++) {
                    const cell = document.getElementById(`puzzle-cell-${dol}{r}-${dol}{c}`);
                    if (cell) {
                        cell.classList.remove('puzzle-preview-valid', 'puzzle-preview-invalid', 'puzzle-preview-target');
                        if (window.puzzleBoard[r][c] === 0) {
                            cell.innerHTML = '';
                        } else {
                            const bgCol = window.PUZZLE_COLORS[window.puzzleBoard[r][c]] || '#2C5E6A';
                            cell.innerHTML = getJewelBlockSvg(bgCol);
                        }
                    }
                }
            }
        }

        function handlePuzzleCellClick(r, c) {
            if (window.puzzleActiveMode === 'singleClear') {
                if (window.puzzleBoard[r][c] === 0) return alert('Pilih kotak yang berisi balok untuk dihapus!');
                window.puzzleBoard[r][c] = 0;
                playPuzzleClear();
                window.puzzleActiveMode = null;
                window.puzzleBoosters.clear = Math.max(0, window.puzzleBoosters.clear - 1);
                showPuzzleComboToast('ü™Ñ Balok Dihapus!');
                loadBlock();
                return;
            }

            if (window.puzzleActiveMode === 'hammer') {
                let cleared = 0;
                for (let dr = -1; dr <= 1; dr++) {
                    for (let dc = -1; dc <= 1; dc++) {
                        const tr = r + dr, tc = c + dc;
                        if (tr >= 0 && tr < 8 && tc >= 0 && tc < 8) {
                            if (window.puzzleBoard[tr][tc] !== 0) {
                                window.puzzleBoard[tr][tc] = 0;
                                cleared++;
                            }
                        }
                    }
                }
                playPuzzleBombSound();
                window.puzzleActiveMode = null;
                window.puzzleBoosters.hammer = Math.max(0, window.puzzleBoosters.hammer - 1);
                window.puzzleScore += (cleared * 20);
                showPuzzleComboToast(`üî® Palu 3x3! (+${dol}{cleared * 20})`);
                updatePuzzleScoreUI();
                loadBlock();
                return;
            }

            if (window.puzzleSelectedCandIdx === null) {
                return;
            }

            tryPlacePuzzleCandidate(r, c, window.puzzleSelectedCandIdx);
        }

        function tryPlacePuzzleCandidate(startR, startC, candIdx) {
            const cand = window.puzzleCandidates[candIdx];
            if (!cand || cand.used) return;

            for (const [dr, dc] of cand.coords) {
                const tr = startR + dr;
                const tc = startC + dc;
                if (tr < 0 || tr >= 8 || tc < 0 || tc >= 8 || window.puzzleBoard[tr][tc] !== 0) {
                    playPuzzleSine(220, 100, 0.1, 'sawtooth');
                    return;
                }
            }

            // Valid placement!
            cand.coords.forEach(([dr, dc]) => {
                window.puzzleBoard[startR + dr][startC + dc] = cand.colorIndex;
            });
            cand.used = true;
            window.puzzleSelectedCandIdx = null;

            // Score for placing shape
            const addedPoints = cand.coords.length * 10;
            window.puzzleScore += addedPoints;
            playPuzzleDrop();

            // Check and clear lines
            checkAndClearPuzzleLines();

            if (window.puzzleScore > window.puzzleHighScore) {
                window.puzzleHighScore = window.puzzleScore;
                localStorage.setItem('singghara_block_puzzle_highscore', window.puzzleHighScore.toString());
            }
            localStorage.setItem('singghara_puzzle_score', window.puzzleScore.toString());

            if (window.puzzleCandidates.every(c => c.used)) {
                generatePuzzleCandidates();
            }

            loadBlock();
        }

        function checkAndClearPuzzleLines() {
            let fullRows = [];
            let fullCols = [];

            for (let r = 0; r < 8; r++) {
                if (window.puzzleBoard[r].every(v => v !== 0)) fullRows.push(r);
            }
            for (let c = 0; c < 8; c++) {
                let isFull = true;
                for (let r = 0; r < 8; r++) {
                    if (window.puzzleBoard[r][c] === 0) { isFull = false; break; }
                }
                if (isFull) fullCols.push(c);
            }

            const totalLines = fullRows.length + fullCols.length;
            if (totalLines > 0) {
                window.puzzleComboStreak++;
                let bonusMultiplier = window.puzzleComboStreak >= 2 ? (window.puzzleComboStreak * 50) : 0;
                let lineScore = (totalLines * 100) + bonusMultiplier;
                window.puzzleScore += lineScore;

                // Animate clearing cells
                fullRows.forEach(r => {
                    for (let c = 0; c < 8; c++) {
                        const cell = document.getElementById(`puzzle-cell-${dol}{r}-${dol}{c}`);
                        if (cell) cell.classList.add('puzzle-clearing');
                        window.puzzleBoard[r][c] = 0;
                    }
                });
                fullCols.forEach(c => {
                    for (let r = 0; r < 8; r++) {
                        const cell = document.getElementById(`puzzle-cell-${dol}{r}-${dol}{c}`);
                        if (cell) cell.classList.add('puzzle-clearing');
                        window.puzzleBoard[r][c] = 0;
                    }
                });

                playPuzzleClear();

                // Bonus chance to reward extra booster!
                if (Math.random() < 0.45) {
                    const boosterTypes = ['rotate', 'clear', 'hammer', 'refresh'];
                    const rewarded = boosterTypes[Math.floor(Math.random() * boosterTypes.length)];
                    window.puzzleBoosters[rewarded]++;
                }

                // Feedback celebration
                let comboMsg = '';
                if (totalLines >= 4) comboMsg = 'üî• UNBELIEVABLE! +' + lineScore;
                else if (totalLines === 3) comboMsg = '‚ú® AMAZING! +' + lineScore;
                else if (totalLines === 2) comboMsg = '‚ö° GREAT COMBO! +' + lineScore;
                else if (window.puzzleComboStreak >= 2) comboMsg = `üéâ STREAK x${dol}{window.puzzleComboStreak}! +${dol}{lineScore}`;
                else comboMsg = `+${dol}{lineScore} Pts!`;

                showPuzzleComboToast(comboMsg);
                if (totalLines >= 2) {
                    playPuzzleComboSound();
                    triggerPuzzleFireworks();
                }
            } else {
                window.puzzleComboStreak = 0;
            }
        }

        function showPuzzleComboToast(msg) {
            const toast = document.getElementById('puzzleComboToast');
            if (!toast) return;
            toast.innerHTML = `<div class="badge bg-warning text-dark px-4 py-2 fs-5 shadow-lg border border-white">${dol}{escapeHtml(msg)}</div>`;
            toast.style.animation = 'none';
            void toast.offsetWidth;
            toast.style.animation = 'comboAnim 1.2s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards';
        }

        function triggerPuzzleFireworks() {
            const canvas = document.getElementById('puzzleFireworksCanvas');
            if (!canvas) return;
            canvas.style.display = 'block';
            const ctx = canvas.getContext('2d');
            const particles = [];
            const colors = ['#FF1744', '#FFEB3B', '#00E676', '#29B6F6', '#FF00E6', '#FF9100', '#D500F9'];

            for (let i = 0; i < 40; i++) {
                const angle = Math.random() * 2 * Math.PI;
                const speed = 2 + Math.random() * 5;
                particles.push({
                    x: canvas.width / 2,
                    y: canvas.height / 2,
                    vx: Math.cos(angle) * speed,
                    vy: Math.sin(angle) * speed,
                    color: colors[Math.floor(Math.random() * colors.length)],
                    life: 1.0,
                    size: 3 + Math.random() * 4
                });
            }

            let frame = 0;
            function anim() {
                ctx.clearRect(0, 0, canvas.width, canvas.height);
                let alive = false;
                particles.forEach(p => {
                    p.x += p.vx;
                    p.y += p.vy;
                    p.life -= 0.03;
                    if (p.life > 0) {
                        alive = true;
                        ctx.fillStyle = p.color;
                        ctx.globalAlpha = p.life;
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
                        ctx.fill();
                    }
                });
                ctx.globalAlpha = 1.0;
                if (alive && frame < 40) {
                    frame++;
                    requestAnimationFrame(anim);
                } else {
                    canvas.style.display = 'none';
                }
            }
            anim();
        }

        function updatePuzzleScoreUI() {
            const sEl = document.getElementById('puzzleScoreText');
            if (sEl) sEl.innerText = window.puzzleScore;
            const hsEl = document.getElementById('puzzleHighScoreText');
            if (hsEl) hsEl.innerText = window.puzzleHighScore;
            const milestone = getPuzzleMilestoneData(window.puzzleScore);
            const pPercentEl = document.getElementById('puzzleProgressPercentText');
            if (pPercentEl) pPercentEl.innerText = milestone.percent + '%';
            const pFillEl = document.getElementById('puzzleProgressFillBar');
            if (pFillEl) pFillEl.style.width = milestone.percent + '%';
            const pTargetEl = document.getElementById('puzzleTargetScoreText');
            if (pTargetEl) pTargetEl.innerText = 'Target: ' + milestone.nextMilestone;
            const pCurrentEl = document.getElementById('puzzleCurrentScoreStatText');
            if (pCurrentEl) pCurrentEl.innerText = 'Skor: ' + window.puzzleScore;

            // Also update booster badges if rendered
            const bRotate = document.getElementById('puzzleBoosterRotateCount');
            if (bRotate) bRotate.innerText = window.puzzleBoosters.rotate;
            const bClear = document.getElementById('puzzleBoosterClearCount');
            if (bClear) bClear.innerText = window.puzzleBoosters.clear;
            const bHammer = document.getElementById('puzzleBoosterHammerCount');
            if (bHammer) bHammer.innerText = window.puzzleBoosters.hammer;
            const bRefresh = document.getElementById('puzzleBoosterRefreshCount');
            if (bRefresh) bRefresh.innerText = window.puzzleBoosters.refresh;
        }

        // Booster Powerup functions
        function usePuzzleRotate() {
            if (window.puzzleSelectedCandIdx === null) {
                return alert('Pilih salah satu balok pada pilihan balok terlebih dahulu untuk diputar!');
            }
            const cand = window.puzzleCandidates[window.puzzleSelectedCandIdx];
            if (!cand || cand.used) return;
            cand.coords = rotatePuzzleCoords(cand.coords);
            playPuzzlePowerUp();
            window.puzzleBoosters.rotate = Math.max(0, window.puzzleBoosters.rotate - 1);
            loadBlock();
        }

        function usePuzzleSingleClear() {
            if (window.puzzleActiveMode === 'singleClear') {
                window.puzzleActiveMode = null;
            } else {
                window.puzzleActiveMode = 'singleClear';
                window.puzzleSelectedCandIdx = null;
                playPuzzleTap();
            }
            loadBlock();
        }

        function usePuzzleHammer() {
            if (window.puzzleActiveMode === 'hammer') {
                window.puzzleActiveMode = null;
            } else {
                window.puzzleActiveMode = 'hammer';
                window.puzzleSelectedCandIdx = null;
                playPuzzleTap();
            }
            loadBlock();
        }

        function usePuzzleRefresh() {
            playPuzzlePowerUp();
            generatePuzzleCandidates();
            window.puzzleSelectedCandIdx = null;
            window.puzzleActiveMode = null;
            window.puzzleBoosters.refresh = Math.max(0, window.puzzleBoosters.refresh - 1);
            loadBlock();
        }

        function cancelPuzzlePowerup() {
            window.puzzleActiveMode = null;
            loadBlock();
        }

        function checkPuzzleGameOver() {
            const remaining = window.puzzleCandidates.filter(c => !c.used);
            if (remaining.length === 0) return;

            let anyFits = false;
            for (const cand of remaining) {
                for (let r = 0; r < 8; r++) {
                    for (let c = 0; c < 8; c++) {
                        let fits = true;
                        for (const [dr, dc] of cand.coords) {
                            const tr = r + dr;
                            const tc = c + dc;
                            if (tr < 0 || tr >= 8 || tc < 0 || tc >= 8 || window.puzzleBoard[tr][tc] !== 0) {
                                fits = false;
                                break;
                            }
                        }
                        if (fits) {
                            anyFits = true;
                            break;
                        }
                    }
                    if (anyFits) break;
                }
                if (anyFits) break;
            }

            if (!anyFits) {
                playPuzzleGameOverSound();
                const modalEl = document.getElementById('puzzleGameOverModal');
                if (modalEl) {
                    const finalVal = document.getElementById('puzzleFinalScoreVal');
                    if (finalVal) finalVal.innerText = window.puzzleScore;
                    const bsModal = new bootstrap.Modal(modalEl);
                    bsModal.show();
                }
            }
        }

        function restartBlockPuzzle(fromModal = false) {
            if (fromModal) {
                const modalEl = document.getElementById('puzzleGameOverModal');
                const instance = bootstrap.Modal.getInstance(modalEl);
                if (instance) instance.hide();
            }
            window.puzzleBoard = Array(8).fill(null).map(() => Array(8).fill(0));
            window.puzzleScore = 0;
            window.puzzleSelectedCandIdx = null;
            window.puzzleActiveMode = null;
            window.puzzleComboStreak = 0;
            localStorage.setItem('singghara_puzzle_score', '0');
            generatePuzzleCandidates();
            loadBlock();
        }

        // ==========================================
        // --- 5. PUZZLE GESER (15-PUZZLE) ---
        // ==========================================
        window.slidePuzzleBoard = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0];
        window.slidePuzzleMoves = 0;

        function shufflePuzzle() {
            window.slidePuzzleBoard = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0];
            window.slidePuzzleMoves = 0;
            for (let i = 0; i < 200; i++) {
                const zeroIdx = window.slidePuzzleBoard.indexOf(0);
                const neighbors = [];
                const zr = Math.floor(zeroIdx / 4);
                const zc = zeroIdx % 4;
                if (zr > 0) neighbors.push(zeroIdx - 4);
                if (zr < 3) neighbors.push(zeroIdx + 4);
                if (zc > 0) neighbors.push(zeroIdx - 1);
                if (zc < 3) neighbors.push(zeroIdx + 1);
                const randomSwap = neighbors[Math.floor(Math.random() * neighbors.length)];
                window.slidePuzzleBoard[zeroIdx] = window.slidePuzzleBoard[randomSwap];
                window.slidePuzzleBoard[randomSwap] = 0;
            }
        }

        function loadPuzzle() {
            let html = `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-puzzle-fill" style="color: #0288D1;"></i> Teka-Teki Geser Angka 15
                        </h4>
                        <small class="text-secondary">Geser ubin untuk mengurutkan angka 1 hingga 15!</small>
                    </div>
                    <div class="d-flex align-items-center gap-3">
                        <span class="badge bg-secondary bg-opacity-25 text-secondary fs-6 px-3 py-2">Langkah: <b class="text-success">${dol}{window.slidePuzzleMoves}</b></span>
                        <button class="btn btn-sm btn-brand" onclick="shufflePuzzle(); loadPuzzle();"><i class="bi bi-shuffle me-1"></i> Acak Ulang</button>
                    </div>
                </div>

                <div class="row justify-content-center">
                    <div class="col-12 col-md-7 col-lg-5">
                        <div class="card card-custom p-3 p-md-4 text-center mx-auto" style="max-width: 400px; border-radius: 20px;">
                            <div class="d-grid gap-2 mb-2" style="grid-template-columns: repeat(4, 1fr);" id="puzzleGridContainer">
            `;

            window.slidePuzzleBoard.forEach((num, idx) => {
                if (num === 0) {
                    html += `<div class="rounded-3" style="aspect-ratio: 1; background: transparent;"></div>`;
                } else {
                    html += `
                        <button class="btn btn-outline-info fw-black fs-4 rounded-3 d-flex align-items-center justify-content-center shadow-sm" 
                                onclick="movePuzzleTile(${dol}{idx})" style="aspect-ratio: 1; background-color: var(--card-bg); color: var(--text-main); border: 2px solid #0288D1;">
                            ${dol}{num}
                        </button>
                    `;
                }
            });

            html += `
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
        }

        function movePuzzleTile(idx) {
            const zeroIdx = window.slidePuzzleBoard.indexOf(0);
            const r1 = Math.floor(idx / 4), c1 = idx % 4;
            const r2 = Math.floor(zeroIdx / 4), c2 = zeroIdx % 4;

            if (Math.abs(r1 - r2) + Math.abs(c1 - c2) === 1) {
                window.slidePuzzleBoard[zeroIdx] = window.slidePuzzleBoard[idx];
                window.slidePuzzleBoard[idx] = 0;
                window.slidePuzzleMoves++;
                loadPuzzle();

                // Check win
                const isWon = window.slidePuzzleBoard.every((val, i) => i === 15 ? val === 0 : val === i + 1);
                if (isWon) {
                    setTimeout(() => alert('üéâ Selamat! Anda berhasil menyelesaikan teka-teki geser dalam ' + window.slidePuzzleMoves + ' langkah!'), 100);
                }
            }
        }

        // ==========================================
        // --- 6. RETRO GIMBOT BRICK GAME (9999-in-1) ---
        // Authentic 1:1 Match to Android My Singghara App (TetrisGame.kt)
        // Modes: 1: Tetris, 2: Shooter, 3: Racing, 4: Snake, 5: Breakout, 0: Lobby Menu
        // ==========================================
        window.brickGameType = 0; // 0: Lobby, 1: Tetris, 2: Shooter, 3: Racing, 4: Snake, 5: Breakout
        window.brickSelectedLobbyId = 1;
        window.brickTheme = 'auto'; // 'auto' (Sync with Web Connect), 'dark', 'light', 'classic', 'neon'
        window.brickScore = 0;
        window.brickHighScore = parseInt(localStorage.getItem('brick_game_highscore_all') || '0', 10);
        window.brickLevel = 1;
        window.brickSpeed = 1;
        window.brickLines = 0;
        window.brickLives = 3;
        window.brickGameOver = false;
        window.brickIsPaused = false;
        window.brickSoundEnabled = true;
        window.brickInterval = null;
        window.brickGlobalTicks = 0;

        // Mode 1: Tetris
        window.brickTetrisGrid = Array(20).fill(null).map(() => Array(10).fill(0));
        window.brickCurrentPiece = null;
        window.brickNextPiece = null;
        window.brickCurRow = 0;
        window.brickCurCol = 4;

        // Mode 2: Shooter
        window.brickShooterCol = 4;
        window.brickShooterBullets = []; // Array of {r, c}
        window.brickShooterEnemies = []; // Array of {r, c}
        window.brickShooterTicks = 0;

        // Mode 3: Racing
        window.brickRacingLane = 1; // Lane 0, 1, 2
        window.brickRacingObstacles = []; // Array of {r, lane}
        window.brickRacingTicks = 0;
        window.brickRoadStripesOffset = false;

        // Mode 4: Snake
        window.brickSnakeBody = [{ r: 10, c: 5 }, { r: 11, c: 5 }, { r: 12, c: 5 }];
        window.brickSnakeDir = { r: -1, c: 0 };
        window.brickSnakeFood = { r: 5, c: 5 };
        window.brickSnakeTicks = 0;

        // Mode 5: Breakout
        window.brickBreakoutPaddleCol = 4;
        window.brickBallX = 5.0;
        window.brickBallY = 15.0;
        window.brickBallVx = 0.35;
        window.brickBallVy = -0.45;
        window.brickBreakoutBricks = []; // Array of {r, c}

        const BRICK_MODES_INFO = [
            { id: 1, name: "GAME 1: TETRIS", desc: "Susun balok hingga baris penuh", icon: "bi-grid-fill" },
            { id: 2, name: "GAME 2: SHOOTER", desc: "Tembak jatuh invasi balok musuh", icon: "bi-crosshair" },
            { id: 3, name: "GAME 3: RACING", desc: "Hindari mobil musuh di jalan raya", icon: "bi-car-front-fill" },
            { id: 4, name: "GAME 4: SNAKE", desc: "Makan umpan dan hindari menabrak diri", icon: "bi-bounding-box" },
            { id: 5, name: "GAME 5: BREAKOUT", desc: "Pantulkan bola hancurkan semua bata", icon: "bi-circle-half" }
        ];

        // 7 standard Tetris pieces with colors (1 to 7) - normalized with non-negative row coords
        const BRICK_PIECE_DEFS = [
            { type: 'I', color: 1, coords: [[0, 0], [0, 1], [0, 2], [0, 3]] },
            { type: 'O', color: 2, coords: [[0, 0], [0, 1], [1, 0], [1, 1]] },
            { type: 'T', color: 3, coords: [[0, 1], [1, 0], [1, 1], [1, 2]] },
            { type: 'L', color: 4, coords: [[0, 0], [1, 0], [2, 0], [2, 1]] },
            { type: 'J', color: 5, coords: [[0, 1], [1, 1], [2, 1], [2, 0]] },
            { type: 'S', color: 6, coords: [[0, 1], [0, 2], [1, 0], [1, 1]] },
            { type: 'Z', color: 7, coords: [[0, 0], [0, 1], [1, 1], [1, 2]] }
        ];

        // Theme Block Colors (Index 1..7) with Dark / Light / Classic / Neon modes
        const BRICK_THEME_PALETTES = {
            dark: {
                bg: '#0F172A',
                border: '#38BDF8',
                grid: '#1E293B',
                textColor: '#F8FAFC',
                accentColor: '#38BDF8',
                colors: [
                    '#0F172A', // 0: empty
                    '#38BDF8', // 1: Sky Blue
                    '#22C55E', // 2: Emerald Green
                    '#A855F7', // 3: Purple
                    '#F59E0B', // 4: Amber
                    '#EF4444', // 5: Rose Red
                    '#06B6D4', // 6: Cyan
                    '#EC4899'  // 7: Pink
                ]
            },
            light: {
                bg: '#F8FAFC',
                border: '#0284C7',
                grid: '#E2E8F0',
                textColor: '#0F172A',
                accentColor: '#0284C7',
                colors: [
                    '#F8FAFC', // 0: empty
                    '#0284C7', // 1: Brand Blue
                    '#10B981', // 2: Emerald
                    '#7C3AED', // 3: Violet
                    '#D97706', // 4: Amber
                    '#DC2626', // 5: Red
                    '#0891B2', // 6: Cyan
                    '#DB2777'  // 7: Pink
                ]
            },
            classic: {
                bg: '#9BBC0F',
                border: '#306230',
                grid: '#8BAC0F',
                textColor: '#0F380F',
                accentColor: '#306230',
                colors: [
                    '#9BBC0F', // 0: Empty
                    '#306230', // 1: Active Piece dark
                    '#0F380F', // 2: Tetris block color
                    '#4C5E03', // 3: Medium green
                    '#2C3E02', // 4: Dark green
                    '#1B2C01', // 5: Deep green
                    '#0A1B00', // 6: Extra dark
                    '#0F380F'  // 7
                ]
            },
            neon: {
                bg: '#0F121C',
                border: '#00FFD2',
                grid: '#182030',
                textColor: '#E2E8F0',
                accentColor: '#00FFD2',
                colors: [
                    '#0F121C', // 0: empty
                    '#00FFD2', // 1: Cyan Neon
                    '#FF007F', // 2: Cyber Pink
                    '#7000FF', // 3: Neon Amethyst
                    '#39FF14', // 4: Lime Electric
                    '#FFE600', // 5: Bright Yellow
                    '#FF5E00', // 6: Active Orange
                    '#00FF66'  // 7: Mint Sparkle
                ]
            }
        };

        function getEffectiveBrickTheme() {
            if (!window.brickTheme || window.brickTheme === 'auto') {
                const appTheme = document.documentElement.getAttribute('data-theme') || 'light';
                return appTheme === 'dark' ? 'dark' : 'light';
            }
            if (window.brickTheme === 'smart') return 'light';
            return window.brickTheme;
        }

        function getBrickThemePalette() {
            const eff = getEffectiveBrickTheme();
            return BRICK_THEME_PALETTES[eff] || BRICK_THEME_PALETTES.dark || BRICK_THEME_PALETTES.neon;
        }

        function syncBrickGameWithAppTheme(appTheme) {
            if (window.brickTheme === 'auto' || !window.brickTheme) {
                const effectiveTheme = appTheme === 'dark' ? 'dark' : 'light';
                const casing = document.getElementById('brickConsoleCasing');
                if (casing) {
                    casing.className = 'brick-game-casing theme-auto theme-' + effectiveTheme + ' p-3 p-sm-4 text-center';
                }
                const label = document.getElementById('brickThemeLabel');
                if (label) {
                    label.textContent = 'Auto (' + (effectiveTheme === 'dark' ? 'Gelap' : 'Terang') + ')';
                }
                const logo = document.getElementById('brickLogoTitle');
                if (logo) {
                    logo.style.color = effectiveTheme === 'dark' ? '#38BDF8' : '#0284C7';
                }
                drawBrickGame();
                drawBrickNext();
            }
        }

        // Web Audio Synthesizer
        window.brickAudioCtx = null;
        function getBrickAudioCtx() {
            if (!window.brickAudioCtx) {
                const AudioCtx = window.AudioContext || window.webkitAudioContext;
                if (AudioCtx) window.brickAudioCtx = new AudioCtx();
            }
            if (window.brickAudioCtx && window.brickAudioCtx.state === 'suspended') {
                window.brickAudioCtx.resume();
            }
            return window.brickAudioCtx;
        }

        function playBrickBeep(type) {
            if (!window.brickSoundEnabled) return;
            try {
                const ctx = getBrickAudioCtx();
                if (!ctx) return;
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.connect(gain);
                gain.connect(ctx.destination);
                const now = ctx.currentTime;

                if (type === 'move') {
                    osc.type = 'square';
                    osc.frequency.setValueAtTime(440, now);
                    gain.gain.setValueAtTime(0.06, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04);
                    osc.start(now);
                    osc.stop(now + 0.04);
                } else if (type === 'rotate') {
                    osc.type = 'square';
                    osc.frequency.setValueAtTime(580, now);
                    osc.frequency.exponentialRampToValueAtTime(740, now + 0.06);
                    gain.gain.setValueAtTime(0.08, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.06);
                    osc.start(now);
                    osc.stop(now + 0.06);
                } else if (type === 'drop') {
                    osc.type = 'triangle';
                    osc.frequency.setValueAtTime(320, now);
                    osc.frequency.exponentialRampToValueAtTime(140, now + 0.07);
                    gain.gain.setValueAtTime(0.09, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.07);
                    osc.start(now);
                    osc.stop(now + 0.07);
                } else if (type === 'clear') {
                    osc.type = 'square';
                    osc.frequency.setValueAtTime(523, now);
                    osc.frequency.setValueAtTime(659, now + 0.06);
                    osc.frequency.setValueAtTime(783, now + 0.12);
                    osc.frequency.setValueAtTime(1046, now + 0.18);
                    gain.gain.setValueAtTime(0.1, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
                    osc.start(now);
                    osc.stop(now + 0.28);
                } else if (type === 'shoot') {
                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(880, now);
                    osc.frequency.exponentialRampToValueAtTime(220, now + 0.08);
                    gain.gain.setValueAtTime(0.08, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.08);
                    osc.start(now);
                    osc.stop(now + 0.08);
                } else if (type === 'hit') {
                    osc.type = 'sine';
                    osc.frequency.setValueAtTime(600, now);
                    osc.frequency.exponentialRampToValueAtTime(900, now + 0.06);
                    gain.gain.setValueAtTime(0.08, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.06);
                    osc.start(now);
                    osc.stop(now + 0.06);
                } else if (type === 'crash') {
                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(160, now);
                    osc.frequency.exponentialRampToValueAtTime(40, now + 0.25);
                    gain.gain.setValueAtTime(0.15, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
                    osc.start(now);
                    osc.stop(now + 0.25);
                } else if (type === 'gameover') {
                    osc.type = 'sawtooth';
                    osc.frequency.setValueAtTime(400, now);
                    osc.frequency.setValueAtTime(320, now + 0.15);
                    osc.frequency.setValueAtTime(250, now + 0.30);
                    osc.frequency.setValueAtTime(160, now + 0.45);
                    gain.gain.setValueAtTime(0.12, now);
                    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.65);
                    osc.start(now);
                    osc.stop(now + 0.65);
                }
            } catch (e) {
                console.warn('Audio error', e);
            }
        }

        function loadBrickGame() {
            window.brickHighScore = parseInt(localStorage.getItem('brick_game_highscore_all') || '0', 10);
            const effTheme = getEffectiveBrickTheme();
            const themeClass = 'theme-' + window.brickTheme + ' theme-' + effTheme;

            let html = `
                <div class="row justify-content-center">
                    <div class="col-12 col-md-10 col-lg-8 col-xl-7">
                        <!-- CONSOLE CASING (Enlarged & Adaptive Theme with My Singghara App) -->
                        <div id="brickConsoleCasing" class="brick-game-casing ${dol}{themeClass} p-3 p-sm-4 text-center">
                            
                            <!-- TOP HEADER WITH LOGO, SOUND & THEME CONTROLS -->
                            <div class="d-flex justify-content-between align-items-center mb-3 px-1">
                                <div class="text-start">
                                    <span id="brickLogoTitle" class="fw-bold d-block lh-1" style="font-size: 18px; font-style: italic; letter-spacing: 1.5px; color: ${dol}{effTheme === 'classic' ? '#282B2A' : (effTheme === 'light' ? '#0284C7' : (effTheme === 'dark' ? '#38BDF8' : '#00FFD2'))};">SINGGHARA</span>
                                </div>

                                <div class="d-flex align-items-center gap-2">
                                    <button class="btn btn-sm btn-dark py-0 px-2 rounded-pill border-0 shadow-sm" onclick="cycleBrickTheme()" title="Ganti Tema Warna (Auto / Gelap / Terang / Classic / Neon)" style="font-size: 11px;">
                                        <i class="bi bi-palette-fill text-warning me-1"></i> <span id="brickThemeLabel" class="text-capitalize">${dol}{window.brickTheme === 'auto' ? 'Auto (' + (effTheme === 'dark' ? 'Gelap' : 'Terang') + ')' : (window.brickTheme === 'dark' ? 'Gelap' : (window.brickTheme === 'light' ? 'Terang' : window.brickTheme))}</span>
                                    </button>
                                    <div class="brick-speaker-grille">
                                        <div class="brick-speaker-line"></div>
                                        <div class="brick-speaker-line"></div>
                                        <div class="brick-speaker-line"></div>
                                    </div>
                                </div>
                            </div>

                            <!-- LCD SCREEN HOUSING (ENLARGED GAME AREA) -->
                            <div class="brick-game-lcd-frame mb-3">
                                <div class="brick-game-lcd p-2 p-sm-3 d-flex justify-content-center align-items-start gap-2 gap-sm-3">
                                    
                                    <!-- 10x20 MAIN MATRIX CANVAS (ENLARGED 240x480) -->
                                    <div class="position-relative d-flex align-items-center justify-content-center flex-shrink-0">
                                        <canvas id="brickGameCanvas" width="240" height="480" class="brick-game-canvas-responsive"></canvas>
                                    </div>

                                    <!-- RIGHT LCD STATS & PREVIEW PANEL (FIT TO HI-SCORE CONTENT WIDTH) -->
                                    <div class="text-start ps-1 d-flex flex-column justify-content-between h-100 flex-shrink-0" style="min-height: 480px; width: max-content; font-size: 11px;">
                                        <div>
                                            <!-- HIGH SCORE -->
                                            <div class="mb-2">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">HI-SCORE</b>
                                                <span class="fs-6 fw-bold" id="brickHighScoreVal">${dol}{window.brickHighScore}</span>
                                            </div>

                                            <!-- CURRENT SCORE -->
                                            <div class="mb-2">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">SCORE</b>
                                                <span class="fs-5 fw-bold" id="brickScoreVal">${dol}{window.brickScore}</span>
                                            </div>

                                            <!-- SPEED -->
                                            <div class="mb-2">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">SPEED</b>
                                                <span class="fw-bold fs-6" id="brickSpeedVal">${dol}{window.brickSpeed}</span>
                                            </div>

                                            <!-- LEVEL (DI BAWAH SPEED) -->
                                            <div class="mb-2">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">LEVEL</b>
                                                <span class="fw-bold fs-6" id="brickLevelVal">${dol}{window.brickLevel}</span>
                                            </div>

                                            <!-- LIVES (DI BAWAH LEVEL / SPEED) -->
                                            <div id="brickLivesBox" class="mb-2" style="display: ${dol}{window.brickGameType === 2 || window.brickGameType === 5 ? 'block' : 'none'};">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">LIVES</b>
                                                <span class="fw-bold fs-6 text-danger" id="brickLivesVal">${dol}{window.brickLives}</span>
                                            </div>

                                            <!-- NEXT PIECE PREVIEW (TETRIS MODE) -->
                                            <div id="brickNextContainer" class="mb-2" style="display: ${dol}{window.brickGameType === 1 ? 'block' : 'none'};">
                                                <b class="d-block text-secondary" style="font-size: 9.5px; letter-spacing: 1px;">NEXT</b>
                                                <canvas id="brickNextCanvas" width="56" height="56" style="border-radius: 6px; display: block;"></canvas>
                                            </div>
                                        </div>

                                        <!-- SOUND & PAUSE STATUS -->
                                        <div class="d-flex justify-content-between align-items-center pt-1 border-top mt-auto" style="border-color: rgba(0,0,0,0.15) !important;">
                                            <span id="brickSoundStatusIcon" style="font-size: 15px; cursor: pointer;" onclick="toggleBrickSound()" title="Klik untuk Suara On/Off">
                                                <i class="bi ${dol}{window.brickSoundEnabled ? 'bi-volume-up-fill' : 'bi-volume-mute-fill'}"></i>
                                            </span>
                                            <span id="brickPauseStatusBadge" class="badge bg-danger text-white px-2" style="font-size: 9.5px; display: ${dol}{window.brickIsPaused ? 'inline-block' : 'none'};">PAUSE</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- SLANTED PILL FUNCTION BUTTONS (TITLES INSIDE BUTTONS) -->
                            <div class="d-flex justify-content-center align-items-center gap-3 gap-sm-4 mb-4 mt-2">
                                <div class="brick-pill-btn-wrapper">
                                    <button type="button" class="brick-pill-btn btn-reset" onclick="returnToBrickLobby()" title="Reset / Menu Utama">
                                        <span>RESET</span>
                                    </button>
                                </div>
                                <div class="brick-pill-btn-wrapper">
                                    <button type="button" class="brick-pill-btn btn-sound" onclick="toggleBrickSound()" title="Suara On/Off">
                                        <span>SOUND</span>
                                    </button>
                                </div>
                                <div class="brick-pill-btn-wrapper">
                                    <button type="button" class="brick-pill-btn btn-pause" onclick="toggleBrickPause()" title="Start / Pause Game">
                                        <span>PAUSE</span>
                                    </button>
                                </div>
                            </div>

                            <!-- CONTROLLER WELLS: TACTILE D-PAD (LEFT) + DUAL ACTION BUTTONS WITH INTERNAL TITLES (RIGHT) -->
                            <div class="d-flex justify-content-between align-items-center px-2 py-1 mb-2">
                                
                                <!-- D-PAD WELL -->
                                <div class="d-flex flex-column align-items-center">
                                    <div class="brick-dpad-well">
                                        <div class="brick-dpad-cross">
                                            <button type="button" class="brick-dpad-arm brick-dpad-up" onclick="brickInputUp()" title="Atas">
                                                <i class="bi bi-caret-up-fill fs-5"></i>
                                            </button>
                                            <button type="button" class="brick-dpad-arm brick-dpad-left" onclick="brickInputLeft()" title="Kiri">
                                                <i class="bi bi-caret-left-fill fs-5"></i>
                                            </button>
                                            <div class="brick-dpad-center"></div>
                                            <button type="button" class="brick-dpad-arm brick-dpad-right" onclick="brickInputRight()" title="Kanan">
                                                <i class="bi bi-caret-right-fill fs-5"></i>
                                            </button>
                                            <button type="button" class="brick-dpad-arm brick-dpad-down" onclick="brickInputDown()" title="Bawah">
                                                <i class="bi bi-caret-down-fill fs-5"></i>
                                            </button>
                                        </div>
                                    </div>
                                    <span class="text-muted fw-bold mt-2" style="font-size: 9px; letter-spacing: 1px;">DIRECTION</span>
                                </div>

                                <!-- DUAL ACTION BUTTONS WELL WITH INTERNAL LABELS -->
                                <div class="d-flex flex-column align-items-center pe-1">
                                    <div class="brick-action-pill-well">
                                        <button type="button" class="brick-action-btn-circle btn-b" onclick="brickInputSecondaryAction()" title="Tombol B (Hard Drop / Aksi)">
                                            <span class="brick-btn-label">B</span>
                                            <span class="brick-btn-sub">DROP</span>
                                        </button>
                                        <button type="button" class="brick-action-btn-circle btn-a" onclick="brickInputPrimaryAction()" title="Tombol A (Putar / Tembak / Mulai)">
                                            <span class="brick-btn-label">A</span>
                                            <span class="brick-btn-sub">PUTAR</span>
                                        </button>
                                    </div>
                                    <span class="text-muted fw-bold mt-2" style="font-size: 9px; letter-spacing: 1px;">ACTION (A & B)</span>
                                </div>
                            </div>
                        </div>

                        <!-- CONTROLS GUIDE CARD (KEYBOARD & GAMEPAD) -->
                        <div class="card card-custom p-3 mt-3 text-start" style="border-radius: 16px;">
                            <div class="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-1">
                                <h6 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                                    <i class="bi bi-controller"></i> Kontrol Gamepad & Keyboard
                                </h6>
                                <span id="brickGamepadStatusBadge" class="badge bg-secondary text-light px-2 py-1" style="font-size: 11px;">
                                    <i class="bi bi-controller me-1"></i> Gamepad Siap (USB / Bluetooth)
                                </span>
                            </div>

                            <div class="row g-3 small text-secondary" style="font-size: 12px;">
                                <!-- GAMEPAD CONTROLS -->
                                <div class="col-12 col-md-6 border-end-md">
                                    <div class="fw-bold text-dark mb-1 d-flex align-items-center gap-1">
                                        <i class="bi bi-joystick text-primary"></i> Gamepad (USB / Bluetooth):
                                    </div>
                                    <ul class="list-unstyled mb-0 d-flex flex-column gap-1">
                                        <li><span class="badge bg-primary me-1">Start</span> Pause / Play / Mulai Game</li>
                                        <li><span class="badge bg-secondary me-1">Select</span> Pilihan Game (1-5) / Menu</li>
                                        <li><span class="badge bg-dark me-1">D-Pad / Stick</span> Arah Kiri/Kanan & Pilih Game ‚Üë/‚Üì</li>
                                        <li><span class="badge bg-danger me-1">Tombol X / ‚úñ</span> Drop Block (B)</li>
                                        <li><span class="badge bg-success me-1">Tombol O / ‚≠ï</span> Putar Balok (A) / Tembak</li>
                                    </ul>
                                </div>

                                <!-- KEYBOARD CONTROLS -->
                                <div class="col-12 col-md-6">
                                    <div class="fw-bold text-dark mb-1 d-flex align-items-center gap-1">
                                        <i class="bi bi-keyboard text-success"></i> Keyboard Komputer / Laptop:
                                    </div>
                                    <ul class="list-unstyled mb-0 d-flex flex-column gap-1">
                                        <li><span class="badge bg-secondary me-1">‚Üê / ‚Üí (A / D)</span> Kiri / Kanan</li>
                                        <li><span class="badge bg-secondary me-1">‚Üë / ‚Üì (W / S)</span> Pilih Game (1-5) / Putar / Turun</li>
                                        <li><span class="badge bg-secondary me-1">Enter / X</span> Tombol A (Putar / Tembak / Mulai)</li>
                                        <li><span class="badge bg-secondary me-1">Spasi / Z</span> Tombol B (Drop Block)</li>
                                        <li><span class="badge bg-secondary me-1">P / Esc</span> Pause / Menu Utama | <span class="badge bg-secondary">M / T</span> Suara / Tema</li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
            setupBrickKeyboardListeners();
            setupBrickGamepadListeners();
            resetAndDrawBrickGame();
        }

        function cycleBrickTheme() {
            const themes = ['auto', 'dark', 'light', 'classic', 'neon'];
            let idx = themes.indexOf(window.brickTheme);
            if (idx === -1) idx = 0;
            idx = (idx + 1) % themes.length;
            window.brickTheme = themes[idx];
            const casing = document.getElementById('brickConsoleCasing');
            const eff = getEffectiveBrickTheme();
            if (casing) {
                casing.className = 'brick-game-casing theme-' + window.brickTheme + ' theme-' + eff + ' p-3 p-sm-4 text-center';
            }
            const label = document.getElementById('brickThemeLabel');
            if (label) {
                if (window.brickTheme === 'auto') {
                    label.textContent = 'Auto (' + (eff === 'dark' ? 'Gelap' : 'Terang') + ')';
                } else if (window.brickTheme === 'dark') {
                    label.textContent = 'Gelap';
                } else if (window.brickTheme === 'light') {
                    label.textContent = 'Terang';
                } else if (window.brickTheme === 'classic') {
                    label.textContent = 'Classic';
                } else if (window.brickTheme === 'neon') {
                    label.textContent = 'Neon';
                }
            }
            const logo = document.getElementById('brickLogoTitle');
            if (logo) {
                logo.style.color = eff === 'classic' ? '#282B2A' : (eff === 'light' ? '#0284C7' : (eff === 'dark' ? '#38BDF8' : '#00FFD2'));
            }
            playBrickBeep('rotate');
            drawBrickGame();
            drawBrickNext();
        }

        function selectLobbyGame(id) {
            window.brickSelectedLobbyId = id;
            startSelectedGame(id);
        }

        function startSelectedGame(type) {
            window.brickGameType = type;
            window.brickScore = 0;
            window.brickLevel = 1;
            window.brickLines = 0;
            window.brickLives = 3;
            window.brickGameOver = false;
            window.brickIsPaused = false;
            window.brickGlobalTicks = 0;

            if (type === 1) { // Tetris
                window.brickTetrisGrid = Array(20).fill(null).map(() => Array(10).fill(0));
                window.brickCurrentPiece = generateRandomPiece();
                window.brickNextPiece = generateRandomPiece();
                window.brickCurRow = 0;
                window.brickCurCol = 3;
            } else if (type === 2) { // Shooter
                window.brickShooterCol = 4;
                window.brickShooterBullets = [];
                window.brickShooterEnemies = [];
                window.brickShooterTicks = 0;
                for (let r = 0; r <= 2; r++) {
                    for (let c = 1; c <= 8; c += 2) {
                        window.brickShooterEnemies.push({ r, c });
                    }
                }
            } else if (type === 3) { // Racing
                window.brickRacingLane = 1;
                window.brickRacingObstacles = [{ r: -6, lane: 0 }];
                window.brickRacingTicks = 0;
                window.brickRoadStripesOffset = false;
            } else if (type === 4) { // Snake
                window.brickSnakeBody = [{ r: 10, c: 5 }, { r: 11, c: 5 }, { r: 12, c: 5 }];
                window.brickSnakeDir = { r: -1, c: 0 };
                window.brickSnakeTicks = 0;
                spawnSnakeFood();
            } else if (type === 5) { // Breakout
                window.brickBreakoutPaddleCol = 4;
                window.brickBallX = 4.0;
                window.brickBallY = 18.0;
                window.brickBallVx = (Math.random() > 0.5 ? 0.35 : -0.35);
                window.brickBallVy = -0.45;
                window.brickBreakoutBricks = [];
                for (let r = 1; r <= 4; r++) {
                    for (let c = 0; c <= 9; c++) {
                        window.brickBreakoutBricks.push({ r, c });
                    }
                }
            }

            playBrickBeep('rotate');
            updateBrickHUD();
            startBrickGameLoop();
        }

        function returnToBrickLobby() {
            if (window.brickInterval) clearInterval(window.brickInterval);
            window.brickInterval = null;
            window.brickGameType = 0;
            window.brickGameOver = false;
            window.brickIsPaused = false;
            window.brickCurrentPiece = null;
            playBrickBeep('move');
            updateBrickHUD();
            drawBrickGame();
            drawBrickNext();
        }

        function setupBrickKeyboardListeners() {
            if (window.brickKeydownAttached) return;
            window.brickKeydownAttached = true;
            window.addEventListener('keydown', (e) => {
                if (currentTab !== 'brick_game') return;
                const activeTag = document.activeElement ? document.activeElement.tagName : '';
                if (activeTag === 'INPUT' || activeTag === 'TEXTAREA' || activeTag === 'SELECT') return;

                if (e.key === 'ArrowLeft' || e.key === 'a' || e.key === 'A') {
                    e.preventDefault();
                    brickInputLeft();
                } else if (e.key === 'ArrowRight' || e.key === 'd' || e.key === 'D') {
                    e.preventDefault();
                    brickInputRight();
                } else if (e.key === 'ArrowDown' || e.key === 's' || e.key === 'S') {
                    e.preventDefault();
                    brickInputDown();
                } else if (e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W') {
                    e.preventDefault();
                    brickInputUp();
                } else if (e.key === 'Enter' || e.key === 'x' || e.key === 'X') {
                    e.preventDefault();
                    brickInputPrimaryAction();
                } else if (e.key === ' ' || e.code === 'Space' || e.key === 'z' || e.key === 'Z') {
                    e.preventDefault();
                    brickInputSecondaryAction();
                } else if (e.key === 'p' || e.key === 'P') {
                    e.preventDefault();
                    toggleBrickPause();
                } else if (e.key === 'Escape') {
                    e.preventDefault();
                    returnToBrickLobby();
                } else if (e.key === 'm' || e.key === 'M') {
                    e.preventDefault();
                    toggleBrickSound();
                } else if (e.key === 't' || e.key === 'T') {
                    e.preventDefault();
                    cycleBrickTheme();
                }
            });
        }

        // ==========================================
        // --- GAMEPAD ENGINE (USB & BLUETOOTH) ---
        // ==========================================
        function setupBrickGamepadListeners() {
            if (window.brickGamepadPollActive) return;
            window.brickGamepadPollActive = true;
            window.brickGamepadPrevState = {};
            window.brickGamepadHoldTimers = {};

            window.addEventListener("gamepadconnected", (e) => {
                updateBrickGamepadStatus();
                playBrickBeep('rotate');
            });

            window.addEventListener("gamepaddisconnected", (e) => {
                updateBrickGamepadStatus();
            });

            function pollGamepad() {
                if (currentTab === 'brick_game') {
                    handleGamepadInput();
                }
                requestAnimationFrame(pollGamepad);
            }
            requestAnimationFrame(pollGamepad);
        }

        function updateBrickGamepadStatus() {
            const el = document.getElementById('brickGamepadStatusBadge');
            if (!el) return;
            const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
            let connectedCount = 0;
            for (let i = 0; i < gamepads.length; i++) {
                if (gamepads[i] && gamepads[i].connected) {
                    connectedCount++;
                }
            }
            if (connectedCount > 0) {
                el.className = 'badge bg-success text-white px-2 py-1 shadow-sm';
                el.innerHTML = '<i class="bi bi-controller me-1"></i> Gamepad Terkoneksi (' + connectedCount + ')';
            } else {
                el.className = 'badge bg-secondary text-light px-2 py-1';
                el.innerHTML = '<i class="bi bi-controller me-1"></i> Gamepad Siap (USB / Bluetooth)';
            }
        }

        function handleGamepadInput() {
            const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
            if (!gamepads || gamepads.length === 0) return;

            let gp = null;
            for (let i = 0; i < gamepads.length; i++) {
                if (gamepads[i] && gamepads[i].connected) {
                    gp = gamepads[i];
                    break;
                }
            }
            if (!gp) return;

            const now = Date.now();
            const prev = window.brickGamepadPrevState || {};
            const hold = window.brickGamepadHoldTimers || {};

            function isBtnDown(idx) {
                return gp.buttons && gp.buttons[idx] && (gp.buttons[idx].pressed || gp.buttons[idx].value > 0.4);
            }

            // Start Button: Button 9 (Start / Options) -> Pause / Play / Mulai
            const startDown = isBtnDown(9);
            if (startDown && !prev.start) {
                toggleBrickPause();
            }
            prev.start = startDown;

            // Select Button: Button 8 (Select / Share / Back / Touchpad 17) -> Pilihan Game / Menu
            const selectDown = isBtnDown(8) || isBtnDown(17);
            if (selectDown && !prev.select) {
                if (window.brickGameType === 0) {
                    brickInputDown();
                } else {
                    returnToBrickLobby();
                }
            }
            prev.select = selectDown;

            // Button X -> Drop Block (B)
            // Button 0 (PS Cross / Xbox A) or Button 2 (PS Square / Xbox X) or L1 (Btn 4) / L2 (Btn 6)
            const btnXDown = isBtnDown(0) || isBtnDown(2) || isBtnDown(4) || isBtnDown(6);
            if (btnXDown && !prev.btnX) {
                brickInputSecondaryAction();
            }
            prev.btnX = btnXDown;

            // Button O -> Putar (A) / Tembak / Mulai
            // Button 1 (PS Circle / Xbox B) or Button 3 (PS Triangle / Xbox Y) or R1 (Btn 5) / R2 (Btn 7)
            const btnODown = isBtnDown(1) || isBtnDown(3) || isBtnDown(5) || isBtnDown(7);
            if (btnODown && !prev.btnO) {
                brickInputPrimaryAction();
            }
            prev.btnO = btnODown;

            // Directional Inputs: D-Pad (Buttons 12-15) + Left/Right Analog Stick Axes
            const axisLX = gp.axes && gp.axes.length > 0 ? gp.axes[0] : 0;
            const axisLY = gp.axes && gp.axes.length > 1 ? gp.axes[1] : 0;
            const axisRX = gp.axes && gp.axes.length > 2 ? gp.axes[2] : 0;
            const axisRY = gp.axes && gp.axes.length > 3 ? gp.axes[3] : 0;

            const upRaw = isBtnDown(12) || axisLY < -0.45 || axisRY < -0.45;
            const downRaw = isBtnDown(13) || axisLY > 0.45 || axisRY > 0.45;
            const leftRaw = isBtnDown(14) || axisLX < -0.45 || axisRX < -0.45;
            const rightRaw = isBtnDown(15) || axisLX > 0.45 || axisRX > 0.45;

            function processDirection(dirName, rawActive, actionFn, repeatDelay = 220, repeatInterval = 100) {
                if (rawActive) {
                    if (!prev[dirName]) {
                        actionFn();
                        hold[dirName] = { start: now, last: now };
                    } else {
                        const h = hold[dirName] || { start: now, last: now };
                        if (now - h.start >= repeatDelay && now - h.last >= repeatInterval) {
                            actionFn();
                            h.last = now;
                            hold[dirName] = h;
                        }
                    }
                } else {
                    delete hold[dirName];
                }
                prev[dirName] = rawActive;
            }

            processDirection('up', upRaw, brickInputUp, 240, 140);
            processDirection('down', downRaw, brickInputDown, 180, 80);
            processDirection('left', leftRaw, brickInputLeft, 200, 90);
            processDirection('right', rightRaw, brickInputRight, 200, 90);

            window.brickGamepadPrevState = prev;
            window.brickGamepadHoldTimers = hold;
        }

        function toggleBrickSound() {
            window.brickSoundEnabled = !window.brickSoundEnabled;
            const icon = document.getElementById('brickSoundStatusIcon');
            if (icon) {
                icon.innerHTML = `<i class="bi ${dol}{window.brickSoundEnabled ? 'bi-volume-up-fill' : 'bi-volume-mute-fill'}"></i>`;
            }
            if (window.brickSoundEnabled) playBrickBeep('move');
        }

        function toggleBrickPause() {
            if (window.brickGameType === 0) {
                startSelectedGame(window.brickSelectedLobbyId || 1);
                return;
            }
            if (window.brickGameOver) {
                startSelectedGame(window.brickGameType);
                return;
            }
            window.brickIsPaused = !window.brickIsPaused;
            const badge = document.getElementById('brickPauseStatusBadge');
            if (badge) badge.style.display = window.brickIsPaused ? 'inline-block' : 'none';
            playBrickBeep(window.brickIsPaused ? 'move' : 'rotate');
        }

        function resetAndDrawBrickGame() {
            updateBrickHUD();
            drawBrickGame();
            drawBrickNext();
        }

        function updateBrickHUD() {
            const scoreEl = document.getElementById('brickScoreVal');
            const levelEl = document.getElementById('brickLevelVal');
            const speedEl = document.getElementById('brickSpeedVal');
            const hiEl = document.getElementById('brickHighScoreVal');
            const livesEl = document.getElementById('brickLivesVal');
            const livesBox = document.getElementById('brickLivesBox');
            const pauseBadge = document.getElementById('brickPauseStatusBadge');
            const nextContainer = document.getElementById('brickNextContainer');

            if (scoreEl) scoreEl.textContent = window.brickScore;
            if (levelEl) levelEl.textContent = window.brickLevel;
            if (speedEl) speedEl.textContent = window.brickSpeed;
            if (hiEl) hiEl.textContent = window.brickHighScore;
            if (livesEl) livesEl.textContent = window.brickLives;
            if (livesBox) livesBox.style.display = (window.brickGameType === 2 || window.brickGameType === 5) ? 'block' : 'none';
            if (pauseBadge) pauseBadge.style.display = window.brickIsPaused ? 'inline-block' : 'none';
            if (nextContainer) nextContainer.style.display = window.brickGameType === 1 ? 'block' : 'none';
        }

        function checkAndUpdateHighScore() {
            if (window.brickScore > window.brickHighScore) {
                window.brickHighScore = window.brickScore;
                localStorage.setItem('brick_game_highscore_all', window.brickHighScore.toString());
                const hiEl = document.getElementById('brickHighScoreVal');
                if (hiEl) hiEl.textContent = window.brickHighScore;
            }
        }

        function startBrickGameLoop() {
            if (window.brickInterval) clearInterval(window.brickInterval);
            window.brickGameOver = false;
            window.brickIsPaused = false;
            updateBrickHUD();

            window.brickInterval = setInterval(() => {
                if (window.brickGameOver || window.brickIsPaused || window.brickGameType === 0) return;
                window.brickGlobalTicks++;

                if (window.brickGameType === 1) { // Tetris
                    const limit = Math.max(2, 16 - (window.brickLevel - 1) * 2);
                    if (window.brickGlobalTicks % limit === 0) {
                        moveTetrisDown();
                    }
                } else if (window.brickGameType === 2) { // Shooter
                    updateShooter();
                } else if (window.brickGameType === 3) { // Racing
                    updateRacing();
                } else if (window.brickGameType === 4) { // Snake
                    updateSnake();
                } else if (window.brickGameType === 5) { // Breakout
                    updateBreakout();
                }

                drawBrickGame();
                drawBrickNext();
            }, 50);
        }

        function handleBrickGameOver() {
            window.brickGameOver = true;
            if (window.brickInterval) clearInterval(window.brickInterval);
            window.brickInterval = null;
            window.brickCurrentPiece = null;
            checkAndUpdateHighScore();
            playBrickBeep('gameover');
            drawBrickGame();
        }

        // ==========================================
        // --- 3D BEVELED PYRAMID BLOCK RENDERING ---
        // (1:1 Port of drawPyramidBlock from TetrisGame.kt)
        // ==========================================
        function drawPyramidBlock(ctx, x, y, size, colorHex, isGhost) {
            if (!colorHex || colorHex === 'transparent') return;
            const w = size;
            const h = size;

            // Base level 1
            ctx.fillStyle = colorHex;
            ctx.fillRect(x, y, w, h);

            const inset = w * 0.22;

            // Left Facet (highlight)
            ctx.fillStyle = isGhost ? 'rgba(255,255,255,0.16)' : 'rgba(255,255,255,0.42)';
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x, y + h);
            ctx.lineTo(x + inset, y + h - inset);
            ctx.lineTo(x + inset, y + inset);
            ctx.closePath();
            ctx.fill();

            // Top Facet (highlight)
            ctx.fillStyle = isGhost ? 'rgba(255,255,255,0.12)' : 'rgba(255,255,255,0.30)';
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x + w, y);
            ctx.lineTo(x + w - inset, y + inset);
            ctx.lineTo(x + inset, y + inset);
            ctx.closePath();
            ctx.fill();

            // Right Facet (shadow)
            ctx.fillStyle = isGhost ? 'rgba(0,0,0,0.15)' : 'rgba(0,0,0,0.38)';
            ctx.beginPath();
            ctx.moveTo(x + w, y);
            ctx.lineTo(x + w, y + h);
            ctx.lineTo(x + w - inset, y + h - inset);
            ctx.lineTo(x + w - inset, y + inset);
            ctx.closePath();
            ctx.fill();

            // Bottom Facet (shadow)
            ctx.fillStyle = isGhost ? 'rgba(0,0,0,0.12)' : 'rgba(0,0,0,0.30)';
            ctx.beginPath();
            ctx.moveTo(x, y + h);
            ctx.lineTo(x + w, y + h);
            ctx.lineTo(x + w - inset, y + h - inset);
            ctx.lineTo(x + inset, y + h - inset);
            ctx.closePath();
            ctx.fill();

            // Level 2: Flat top peak block
            ctx.fillStyle = colorHex;
            ctx.fillRect(x + inset, y + inset, w - 2 * inset, h - 2 * inset);

            // Gemstone glossy linear gradient overlay
            const grad = ctx.createLinearGradient(x + inset, y + inset, x + w - inset, y + h - inset);
            grad.addColorStop(0, isGhost ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.58)');
            grad.addColorStop(0.3, isGhost ? 'rgba(255,255,255,0.04)' : 'rgba(255,255,255,0.12)');
            grad.addColorStop(0.7, 'transparent');
            grad.addColorStop(1, isGhost ? 'rgba(0,0,0,0)' : 'rgba(0,0,0,0.14)');
            ctx.fillStyle = grad;
            ctx.fillRect(x + inset, y + inset, w - 2 * inset, h - 2 * inset);

            // Stroke edges for bevel structure
            ctx.strokeStyle = 'rgba(255,255,255,0.15)';
            ctx.lineWidth = 0.8;
            ctx.strokeRect(x + inset, y + inset, w - 2 * inset, h - 2 * inset);

            ctx.strokeStyle = 'rgba(0,0,0,0.22)';
            ctx.strokeRect(x, y, w, h);
        }

        // ==========================================
        // --- COMPUTE RENDER GRID (5 GAME MODES) ---
        // ==========================================
        function computeRenderGrid() {
            const grid = Array(20).fill(null).map(() => Array(10).fill(0));

            if (window.brickGameType === 1) { // Tetris
                for (let r = 0; r < 20; r++) {
                    for (let c = 0; c < 10; c++) {
                        grid[r][c] = window.brickTetrisGrid[r][c];
                    }
                }
                if (window.brickCurrentPiece) {
                    for (let coord of window.brickCurrentPiece.coords) {
                        const r = window.brickCurRow + coord[0];
                        const c = window.brickCurCol + coord[1];
                        if (r >= 0 && r < 20 && c >= 0 && c < 10) {
                            grid[r][c] = window.brickCurrentPiece.color;
                        }
                    }
                }
            } else if (window.brickGameType === 2) { // Shooter
                // Player ship (Row 17..19)
                const pCol = window.brickShooterCol;
                if (pCol >= 0 && pCol < 10) grid[19][pCol] = 2;
                if (pCol - 1 >= 0) grid[18][pCol - 1] = 2;
                if (pCol >= 0 && pCol < 10) grid[18][pCol] = 2;
                if (pCol + 1 < 10) grid[18][pCol + 1] = 2;
                if (pCol >= 0 && pCol < 10) grid[17][pCol] = 2;

                // Enemies
                for (let enemy of window.brickShooterEnemies) {
                    if (enemy.r >= 0 && enemy.r < 20 && enemy.c >= 0 && enemy.c < 10) {
                        grid[enemy.r][enemy.c] = 1;
                    }
                }
                // Lasers
                for (let b of window.brickShooterBullets) {
                    if (b.r >= 0 && b.r < 20 && b.c >= 0 && b.c < 10) {
                        grid[b.r][b.c] = 3;
                    }
                }
            } else if (window.brickGameType === 3) { // Racing
                // Road lines
                for (let r = 0; r < 20; r++) {
                    const active = window.brickRoadStripesOffset ? (r % 4 === 0 || r % 4 === 1) : (r % 4 === 2 || r % 4 === 3);
                    if (active) {
                        grid[r][0] = 4;
                        grid[r][9] = 4;
                    }
                }

                // Player F1 car
                const col = getLaneCol(window.brickRacingLane);
                const F1Car = [
                    [19, col - 1], [19, col + 1],
                    [18, col],
                    [17, col - 1], [17, col], [17, col + 1],
                    [16, col]
                ];
                for (let cell of F1Car) {
                    if (cell[0] >= 0 && cell[0] < 20 && cell[1] >= 0 && cell[1] < 10) {
                        grid[cell[0]][cell[1]] = 2;
                    }
                }

                // Enemy Obstacles
                for (let obs of window.brickRacingObstacles) {
                    const r = obs.r;
                    const enemyCol = getLaneCol(obs.lane);
                    const ecList = [
                        [r, enemyCol - 1], [r, enemyCol + 1],
                        [r + 1, enemyCol],
                        [r + 2, enemyCol - 1], [r + 2, enemyCol], [r + 2, enemyCol + 1],
                        [r + 3, enemyCol]
                    ];
                    for (let cell of ecList) {
                        if (cell[0] >= 0 && cell[0] < 20 && cell[1] >= 0 && cell[1] < 10) {
                            grid[cell[0]][cell[1]] = 1;
                        }
                    }
                }
            } else if (window.brickGameType === 4) { // Snake
                // Food
                if (window.brickSnakeFood.r >= 0 && window.brickSnakeFood.r < 20 && window.brickSnakeFood.c >= 0 && window.brickSnakeFood.c < 10) {
                    grid[window.brickSnakeFood.r][window.brickSnakeFood.c] = 5;
                }
                // Body
                for (let i = 0; i < window.brickSnakeBody.length; i++) {
                    const part = window.brickSnakeBody[i];
                    if (part.r >= 0 && part.r < 20 && part.c >= 0 && part.c < 10) {
                        grid[part.r][part.c] = i === 0 ? 2 : 1;
                    }
                }
            } else if (window.brickGameType === 5) { // Breakout
                // Targets
                for (let brick of window.brickBreakoutBricks) {
                    if (brick.r >= 0 && brick.r < 20 && brick.c >= 0 && brick.c < 10) {
                        grid[brick.r][brick.c] = 1;
                    }
                }
                // Paddle (Row 19, width 3)
                const padL = window.brickBreakoutPaddleCol - 1;
                const padR = window.brickBreakoutPaddleCol + 1;
                for (let c = padL; c <= padR; c++) {
                    if (c >= 0 && c < 10) grid[19][c] = 2;
                }
                // Ball
                const bCol = Math.max(0, Math.min(9, Math.floor(window.brickBallX)));
                const bRow = Math.max(0, Math.min(19, Math.floor(window.brickBallY)));
                grid[bRow][bCol] = 3;
            }

            return grid;
        }

        function getLaneCol(lane) {
            if (lane === 0) return 2;
            if (lane === 1) return 5;
            return 7;
        }

        function drawBrickGame() {
            const canvas = document.getElementById('brickGameCanvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const palette = getBrickThemePalette();
            const blockSize = 24; // 10 cols x 24px = 240px, 20 rows x 24px = 480px

            // Background LCD fill (polos)
            ctx.fillStyle = palette.bg;
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            // Draw background cell bevel grids ONLY during active gameplay (polos / tanpa grid saat menu pilihan game)
            if (window.brickGameType !== 0) {
                for (let r = 0; r < 20; r++) {
                    for (let c = 0; c < 10; c++) {
                        ctx.strokeStyle = palette.grid;
                        ctx.lineWidth = 0.5;
                        ctx.strokeRect(c * blockSize, r * blockSize, blockSize, blockSize);
                    }
                }
            }

            // LOBBY GAME SELECTOR MENU (GAME TYPE 0) - SINGLE SELECTED GAME VIEW
            if (window.brickGameType === 0) {
                const gamesList = [
                    { id: 1, title: 'TETRIS', sub: 'SUSUN BALOK', icon: 'bi-grid-3x3', hint: 'Atur & susun balok jatuh' },
                    { id: 2, title: 'SHOOTER', sub: 'TEMBAK LASER', icon: 'bi-crosshair', hint: 'Tembak jatuh invasi musuh' },
                    { id: 3, title: 'RACING', sub: 'BALAP MOBIL', icon: 'bi-car-front-fill', hint: 'Hindari tabrakan di jalan' },
                    { id: 4, title: 'SNAKE', sub: 'ULAR KLASIK', icon: 'bi-bounding-box', hint: 'Makan umpan & terus tumbuh' },
                    { id: 5, title: 'BREAKOUT', sub: 'PANTUL BATA', icon: 'bi-circle-half', hint: 'Pantulkan bola hancurkan bata' }
                ];

                const currentSelected = gamesList.find(g => g.id === window.brickSelectedLobbyId) || gamesList[0];

                // LCD Top Brand Header
                ctx.fillStyle = palette.colors[1] || '#00FFD2';
                ctx.font = 'bold 16px monospace';
                ctx.textAlign = 'center';
                ctx.fillText('SINGGHARA', 120, 42);

                ctx.fillStyle = palette.colors[2] || '#FF007F';
                ctx.font = 'bold 11px monospace';
                ctx.fillText('RETRO BRICK GAME', 120, 64);

                ctx.strokeStyle = palette.grid || 'rgba(255,255,255,0.2)';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(25, 78);
                ctx.lineTo(215, 78);
                ctx.stroke();

                // Game Number / Selector Carousel Indicator (e.g. < GAME 1/5 >)
                ctx.fillStyle = palette.colors[3] || '#FBBF24';
                ctx.font = 'bold 13px monospace';
                ctx.textAlign = 'center';
                ctx.fillText('‚óÑ  GAME ' + currentSelected.id + ' / 5  ‚ñ∫', 120, 115);

                // Highlighted Card for the Selected Game
                ctx.fillStyle = palette.colors[1] || '#00FFD2';
                ctx.globalAlpha = 0.12;
                ctx.fillRect(16, 135, 208, 140);
                ctx.globalAlpha = 1.0;

                ctx.strokeStyle = palette.colors[1] || '#00FFD2';
                ctx.lineWidth = 2;
                ctx.strokeRect(16, 135, 208, 140);

                // Game Title
                ctx.fillStyle = palette.colors[1] || '#00FFD2';
                ctx.font = 'bold 22px monospace';
                ctx.textAlign = 'center';
                ctx.fillText(currentSelected.title, 120, 185);

                // Subtitle
                ctx.fillStyle = palette.colors[2] || '#FF007F';
                ctx.font = 'bold 12px monospace';
                ctx.fillText(currentSelected.sub, 120, 215);

                // Short Hint / Description
                ctx.fillStyle = palette.textColor || '#FFFFFF';
                ctx.font = '10.5px monospace';
                ctx.fillText(currentSelected.hint, 120, 248);

                // Animated Mini Game Graphic Preview in Lobby Box (Row 13..15)
                const animTicks = Math.floor(Date.now() / 250) % 4;
                if (currentSelected.id === 1) { // Tetris preview piece
                    drawPyramidBlock(ctx, 4 * blockSize, 12 * blockSize, blockSize, palette.colors[1], false);
                    drawPyramidBlock(ctx, 5 * blockSize, 12 * blockSize, blockSize, palette.colors[1], false);
                    drawPyramidBlock(ctx, 4 * blockSize, 13 * blockSize, blockSize, palette.colors[1], false);
                    drawPyramidBlock(ctx, 5 * blockSize, 13 * blockSize, blockSize, palette.colors[1], false);
                } else if (currentSelected.id === 2) { // Shooter preview
                    drawPyramidBlock(ctx, 4 * blockSize, 12 * blockSize, blockSize, palette.colors[5], false);
                    drawPyramidBlock(ctx, 5 * blockSize, 12 * blockSize, blockSize, palette.colors[5], false);
                    drawPyramidBlock(ctx, (4.5 + ((animTicks % 2 === 0) ? -0.5 : 0.5)) * blockSize, 14 * blockSize, blockSize, palette.colors[2], false);
                } else if (currentSelected.id === 3) { // Racing car preview
                    drawPyramidBlock(ctx, 4 * blockSize, 12 * blockSize, blockSize, palette.colors[4], false);
                    drawPyramidBlock(ctx, 5 * blockSize, 12 * blockSize, blockSize, palette.colors[4], false);
                    drawPyramidBlock(ctx, 4.5 * blockSize, 13 * blockSize, blockSize, palette.colors[4], false);
                } else if (currentSelected.id === 4) { // Snake preview
                    drawPyramidBlock(ctx, (3 + animTicks) * blockSize, 12.5 * blockSize, blockSize, palette.colors[4], false);
                    drawPyramidBlock(ctx, 7 * blockSize, 12.5 * blockSize, blockSize, palette.colors[5], false);
                } else if (currentSelected.id === 5) { // Breakout preview
                    drawPyramidBlock(ctx, 4 * blockSize, 13.5 * blockSize, blockSize, palette.colors[2], false);
                    drawPyramidBlock(ctx, 5 * blockSize, 13.5 * blockSize, blockSize, palette.colors[2], false);
                    drawPyramidBlock(ctx, 4.5 * blockSize, 12 * blockSize, blockSize, palette.colors[3], false);
                }

                // Bottom Controls Hint on LCD
                ctx.strokeStyle = palette.grid || 'rgba(255,255,255,0.2)';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(25, 385);
                ctx.lineTo(215, 385);
                ctx.stroke();

                ctx.fillStyle = palette.colors[4] || '#39FF14';
                ctx.font = 'bold 11.5px monospace';
                ctx.textAlign = 'center';
                ctx.fillText('‚Üë / ‚Üì  ATAU  ‚óÑ / ‚ñ∫', 120, 412);
                ctx.fillText('PILIH GAME (1 - 5)', 120, 430);

                ctx.fillStyle = palette.colors[1] || '#00FFD2';
                ctx.font = 'bold 13px monospace';
                ctx.fillText('TEKAN START / A / X', 120, 458);
                return;
            }

            const matrix = computeRenderGrid();

            // Render all active beveled cells
            for (let r = 0; r < 20; r++) {
                for (let c = 0; c < 10; c++) {
                    const colorIdx = matrix[r][c];
                    if (colorIdx > 0) {
                        const colorHex = palette.colors[colorIdx % palette.colors.length] || palette.colors[1];
                        drawPyramidBlock(ctx, c * blockSize, r * blockSize, blockSize, colorHex, false);
                    }
                }
            }

            // GAME OVER OVERLAY
            if (window.brickGameOver) {
                ctx.fillStyle = 'rgba(15, 23, 42, 0.92)';
                ctx.fillRect(20, 160, 200, 150);
                ctx.strokeStyle = palette.colors[2] || '#EF4444';
                ctx.lineWidth = 2;
                ctx.strokeRect(20, 160, 200, 150);

                ctx.fillStyle = palette.colors[2] || '#EF4444';
                ctx.font = 'bold 20px monospace';
                ctx.textAlign = 'center';
                ctx.fillText('GAME OVER', 120, 205);

                ctx.fillStyle = '#FFFFFF';
                ctx.font = 'bold 15px monospace';
                ctx.fillText('SCORE: ' + window.brickScore, 120, 240);

                ctx.fillStyle = palette.colors[1] || '#38BDF8';
                ctx.font = 'bold 12px monospace';
                ctx.fillText('TEKAN START / A', 120, 275);
            }
        }

        function drawBrickNext() {
            const canvas = document.getElementById('brickNextCanvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const palette = getBrickThemePalette();
            const blockSize = 13;

            ctx.fillStyle = palette.bg;
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            if (window.brickGameType === 1 && window.brickNextPiece) {
                const piece = window.brickNextPiece;
                const colorHex = palette.colors[piece.color % palette.colors.length];
                let minR = Math.min(...piece.coords.map(c => c[0]));
                let maxR = Math.max(...piece.coords.map(c => c[0]));
                let minC = Math.min(...piece.coords.map(c => c[1]));
                let maxC = Math.max(...piece.coords.map(c => c[1]));
                let pieceW = (maxC - minC + 1) * blockSize;
                let pieceH = (maxR - minR + 1) * blockSize;
                let startX = Math.round((canvas.width - pieceW) / 2);
                let startY = Math.round((canvas.height - pieceH) / 2);

                for (let coord of piece.coords) {
                    const px = startX + (coord[1] - minC) * blockSize;
                    const py = startY + (coord[0] - minR) * blockSize;
                    drawPyramidBlock(ctx, px, py, blockSize, colorHex, false);
                }
            }
        }

        // ==========================================
        // --- INPUT DISPATCHERS ---
        // ==========================================
        function brickInputLeft() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickGameType === 0) {
                window.brickSelectedLobbyId = window.brickSelectedLobbyId <= 1 ? 5 : window.brickSelectedLobbyId - 1;
                updateBrickHUD();
                playBrickBeep('move');
                drawBrickGame();
            } else if (window.brickGameType > 0) {
                if (window.brickGameType === 1) moveTetrisLeft();
                else if (window.brickGameType === 2) window.brickShooterCol = Math.max(1, window.brickShooterCol - 1);
                else if (window.brickGameType === 3) window.brickRacingLane = Math.max(0, window.brickRacingLane - 1);
                else if (window.brickGameType === 4) {
                    if (window.brickSnakeDir.c !== 1) window.brickSnakeDir = { r: 0, c: -1 };
                } else if (window.brickGameType === 5) window.brickBreakoutPaddleCol = Math.max(1, window.brickBreakoutPaddleCol - 1);
                playBrickBeep('move');
                drawBrickGame();
            }
        }

        function brickInputRight() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickGameType === 0) {
                window.brickSelectedLobbyId = window.brickSelectedLobbyId >= 5 ? 1 : window.brickSelectedLobbyId + 1;
                updateBrickHUD();
                playBrickBeep('move');
                drawBrickGame();
            } else if (window.brickGameType > 0) {
                if (window.brickGameType === 1) moveTetrisRight();
                else if (window.brickGameType === 2) window.brickShooterCol = Math.min(8, window.brickShooterCol + 1);
                else if (window.brickGameType === 3) window.brickRacingLane = Math.min(2, window.brickRacingLane + 1);
                else if (window.brickGameType === 4) {
                    if (window.brickSnakeDir.c !== -1) window.brickSnakeDir = { r: 0, c: 1 };
                } else if (window.brickGameType === 5) window.brickBreakoutPaddleCol = Math.min(8, window.brickBreakoutPaddleCol + 1);
                playBrickBeep('move');
                drawBrickGame();
            }
        }

        function brickInputUp() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickGameType === 0) {
                window.brickSelectedLobbyId = window.brickSelectedLobbyId <= 1 ? 5 : window.brickSelectedLobbyId - 1;
                updateBrickHUD();
                playBrickBeep('move');
                drawBrickGame();
            } else if (window.brickGameType === 1) {
                rotateTetrisPiece(true);
            } else if (window.brickGameType === 4) {
                if (window.brickSnakeDir.r !== 1) window.brickSnakeDir = { r: -1, c: 0 };
                playBrickBeep('move');
            }
        }

        function brickInputDown() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickGameType === 0) {
                window.brickSelectedLobbyId = window.brickSelectedLobbyId >= 5 ? 1 : window.brickSelectedLobbyId + 1;
                updateBrickHUD();
                playBrickBeep('move');
                drawBrickGame();
            } else if (window.brickGameType === 1) {
                moveTetrisDown();
            } else if (window.brickGameType === 4) {
                if (window.brickSnakeDir.r !== -1) window.brickSnakeDir = { r: 1, c: 0 };
                playBrickBeep('move');
            }
        }

        function brickInputPrimaryAction() {
            if (window.brickGameOver || window.brickGameType === 0) {
                startSelectedGame(window.brickGameType || window.brickSelectedLobbyId || 1);
                return;
            }
            if (window.brickIsPaused) {
                toggleBrickPause();
                return;
            }
            if (window.brickGameType === 1) {
                rotateTetrisPiece(true);
            } else if (window.brickGameType === 2) {
                // Shooter Fire Laser
                if (window.brickShooterBullets.length < 4) {
                    window.brickShooterBullets.push({ r: 16, c: window.brickShooterCol });
                    playBrickBeep('shoot');
                    drawBrickGame();
                }
            } else if (window.brickGameType === 3) {
                // Boost speed in racing
                updateRacing();
                drawBrickGame();
                playBrickBeep('move');
            }
        }

        function brickInputSecondaryAction() {
            if (window.brickGameOver || window.brickIsPaused) {
                if (window.brickGameOver) {
                    startSelectedGame(window.brickGameType || window.brickSelectedLobbyId || 1);
                }
                return;
            }
            if (window.brickGameType === 1) {
                hardDropTetris();
            } else {
                brickInputPrimaryAction();
            }
        }

        // ==========================================
        // --- 1. TETRIS ENGINE MECHANICS ---
        // ==========================================
        function generateRandomPiece() {
            const def = BRICK_PIECE_DEFS[Math.floor(Math.random() * BRICK_PIECE_DEFS.length)];
            return {
                type: def.type,
                color: def.color,
                coords: def.coords.map(c => [...c])
            };
        }

        function rotateTetrisPiece(clockwise) {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (!window.brickCurrentPiece || window.brickCurrentPiece.type === 'O') return;
            const piece = window.brickCurrentPiece;
            const oldCoords = piece.coords;
            
            let centerR = 1, centerC = 1;
            if (piece.type === 'I') {
                centerR = 1.5;
                centerC = 1.5;
            }
            
            let newCoords = oldCoords.map(c => {
                const dr = c[0] - centerR;
                const dc = c[1] - centerC;
                const nr = clockwise ? centerR + dc : centerR - dc;
                const nc = clockwise ? centerC - dr : centerC + dr;
                return [Math.round(nr), Math.round(nc)];
            });

            // Normalize so min row and min col are >= 0
            let minR = Math.min(...newCoords.map(c => c[0]));
            let minC = Math.min(...newCoords.map(c => c[1]));
            if (minR < 0 || minC < 0) {
                newCoords = newCoords.map(c => [c[0] - (minR < 0 ? minR : 0), c[1] - (minC < 0 ? minC : 0)]);
            }

            const kicks = [[0, 0], [0, -1], [0, 1], [0, -2], [0, 2], [-1, 0]];
            for (let kick of kicks) {
                if (canPlaceTetrisPiece(newCoords, window.brickCurRow + kick[0], window.brickCurCol + kick[1])) {
                    window.brickCurRow += kick[0];
                    window.brickCurCol += kick[1];
                    window.brickCurrentPiece.coords = newCoords;
                    playBrickBeep('rotate');
                    drawBrickGame();
                    return;
                }
            }
        }

        function canPlaceTetrisPiece(coords, r, c) {
            for (let coord of coords) {
                const targetR = r + coord[0];
                const targetC = c + coord[1];
                if (targetC < 0 || targetC >= 10 || targetR >= 20) return false;
                if (targetR >= 0) {
                    if (window.brickTetrisGrid[targetR][targetC] !== 0) return false;
                }
            }
            return true;
        }

        function moveTetrisLeft() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickCurrentPiece && canPlaceTetrisPiece(window.brickCurrentPiece.coords, window.brickCurRow, window.brickCurCol - 1)) {
                window.brickCurCol--;
            }
        }

        function moveTetrisRight() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (window.brickCurrentPiece && canPlaceTetrisPiece(window.brickCurrentPiece.coords, window.brickCurRow, window.brickCurCol + 1)) {
                window.brickCurCol++;
            }
        }

        function moveTetrisDown() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (!window.brickCurrentPiece) return;
            if (canPlaceTetrisPiece(window.brickCurrentPiece.coords, window.brickCurRow + 1, window.brickCurCol)) {
                window.brickCurRow++;
            } else {
                lockTetrisPiece();
            }
        }

        function hardDropTetris() {
            if (window.brickGameOver || window.brickIsPaused) return;
            if (!window.brickCurrentPiece) return;
            while (canPlaceTetrisPiece(window.brickCurrentPiece.coords, window.brickCurRow + 1, window.brickCurCol)) {
                window.brickCurRow++;
            }
            lockTetrisPiece();
            drawBrickGame();
        }

        function lockTetrisPiece() {
            if (window.brickGameOver) return;
            const piece = window.brickCurrentPiece;
            if (!piece) return;

            let lockedAny = false;
            for (let coord of piece.coords) {
                const r = window.brickCurRow + coord[0];
                const c = window.brickCurCol + coord[1];
                if (r >= 0 && r < 20 && c >= 0 && c < 10) {
                    window.brickTetrisGrid[r][c] = piece.color;
                    lockedAny = true;
                }
            }

            // Line clearing
            let cleared = 0;
            for (let r = 19; r >= 0; r--) {
                if (window.brickTetrisGrid[r].every(v => v !== 0)) {
                    window.brickTetrisGrid.splice(r, 1);
                    window.brickTetrisGrid.unshift(Array(10).fill(0));
                    cleared++;
                    r++;
                }
            }

            if (cleared > 0) {
                window.brickLines += cleared;
                const points = cleared === 1 ? 100 : (cleared === 2 ? 300 : (cleared === 3 ? 600 : 1000));
                window.brickScore += points;
                if (window.brickLines >= window.brickLevel * 10 && window.brickLevel < 10) {
                    window.brickLevel++;
                }
                checkAndUpdateHighScore();
                playBrickBeep('clear');
                updateBrickHUD();
            } else {
                playBrickBeep('drop');
            }

            // Prepare next piece
            window.brickCurrentPiece = window.brickNextPiece || generateRandomPiece();
            window.brickNextPiece = generateRandomPiece();
            window.brickCurRow = 0;
            window.brickCurCol = 3;

            // Check if new piece can be placed. If blocked by top blocks, GAME OVER!
            if (!canPlaceTetrisPiece(window.brickCurrentPiece.coords, window.brickCurRow, window.brickCurCol)) {
                handleBrickGameOver();
            }
        }

        // ==========================================
        // --- 2. SHOOTER ENGINE MECHANICS ---
        // ==========================================
        function updateShooter() {
            window.brickShooterTicks++;

            // Move bullets up
            for (let i = 0; i < window.brickShooterBullets.length; i++) {
                window.brickShooterBullets[i].r -= 1;
            }
            window.brickShooterBullets = window.brickShooterBullets.filter(b => b.r >= 0);

            // Move enemies down
            const moveThreshold = Math.max(4, 18 - window.brickLevel * 2);
            if (window.brickShooterTicks % moveThreshold === 0) {
                for (let i = 0; i < window.brickShooterEnemies.length; i++) {
                    window.brickShooterEnemies[i].r += 1;
                }
            }

            // Bullet vs Enemy Collision
            window.brickShooterEnemies = window.brickShooterEnemies.filter(enemy => {
                let hit = false;
                window.brickShooterBullets = window.brickShooterBullets.filter(bullet => {
                    if (bullet.c === enemy.c && (bullet.r === enemy.r || bullet.r === enemy.r - 1)) {
                        hit = true;
                        return false;
                    }
                    return true;
                });
                if (hit) {
                    window.brickScore += 50;
                    checkAndUpdateHighScore();
                    updateBrickHUD();
                    playBrickBeep('hit');
                    return false;
                }
                return true;
            });

            // If wave cleared, respawn next wave
            if (window.brickShooterEnemies.length === 0) {
                window.brickLevel++;
                updateBrickHUD();
                for (let r = 0; r <= 2; r++) {
                    for (let c = 1; c <= 8; c += 2) {
                        window.brickShooterEnemies.push({ r, c });
                    }
                }
            }

            // Check enemy breach on player row 17
            for (let enemy of window.brickShooterEnemies) {
                if (enemy.r >= 17) {
                    window.brickLives--;
                    updateBrickHUD();
                    playBrickBeep('crash');
                    if (window.brickLives <= 0) {
                        handleBrickGameOver();
                    } else {
                        // Reset current wave to top
                        window.brickShooterEnemies = [];
                        for (let r = 0; r <= 2; r++) {
                            for (let c = 1; c <= 8; c += 2) {
                                window.brickShooterEnemies.push({ r, c });
                            }
                        }
                    }
                    return;
                }
            }
        }

        // ==========================================
        // --- 3. RACING ENGINE MECHANICS ---
        // ==========================================
        function updateRacing() {
            window.brickRacingTicks++;
            const speedLimit = Math.max(1, 6 - Math.floor(window.brickLevel / 2));
            if (window.brickRacingTicks % speedLimit !== 0) return;

            window.brickRoadStripesOffset = !window.brickRoadStripesOffset;

            // Move enemy cars down
            for (let obs of window.brickRacingObstacles) {
                obs.r += 1;
            }

            // Check collision with player car (player is at row 16..19, lane: window.brickRacingLane)
            for (let obs of window.brickRacingObstacles) {
                if (obs.lane === window.brickRacingLane) {
                    // Enemy spans row obs.r to obs.r + 3
                    if (obs.r + 3 >= 16 && obs.r <= 19) {
                        playBrickBeep('crash');
                        handleBrickGameOver();
                        return;
                    }
                }
            }

            // Remove passed cars & award score
            window.brickRacingObstacles = window.brickRacingObstacles.filter(obs => {
                if (obs.r > 20) {
                    window.brickScore += 50;
                    if (window.brickScore % 500 === 0) {
                        window.brickLevel++;
                    }
                    checkAndUpdateHighScore();
                    updateBrickHUD();
                    return false;
                }
                return true;
            });

            // Spawn new obstacle
            if (window.brickRacingObstacles.length === 0 || (window.brickRacingObstacles[window.brickRacingObstacles.length - 1].r >= 8)) {
                if (Math.random() < 0.6) {
                    const newLane = Math.floor(Math.random() * 3);
                    window.brickRacingObstacles.push({ r: -4, lane: newLane });
                }
            }
        }

        // ==========================================
        // --- 4. SNAKE ENGINE MECHANICS ---
        // ==========================================
        function spawnSnakeFood() {
            const emptySpots = [];
            for (let r = 0; r < 20; r++) {
                for (let c = 0; c < 10; c++) {
                    const isBody = window.brickSnakeBody.some(b => b.r === r && b.c === c);
                    if (!isBody) emptySpots.push({ r, c });
                }
            }
            if (emptySpots.length > 0) {
                window.brickSnakeFood = emptySpots[Math.floor(Math.random() * emptySpots.length)];
            }
        }

        function updateSnake() {
            window.brickSnakeTicks++;
            const speedLimit = Math.max(2, 8 - window.brickLevel);
            if (window.brickSnakeTicks % speedLimit !== 0) return;

            const head = window.brickSnakeBody[0];
            const nextH = {
                r: head.r + window.brickSnakeDir.r,
                c: head.c + window.brickSnakeDir.c
            };

            // Wall collision
            if (nextH.r < 0 || nextH.r >= 20 || nextH.c < 0 || nextH.c >= 10) {
                playBrickBeep('crash');
                handleBrickGameOver();
                return;
            }

            // Self collision
            if (window.brickSnakeBody.some(b => b.r === nextH.r && b.c === nextH.c)) {
                playBrickBeep('crash');
                handleBrickGameOver();
                return;
            }

            // Food eating
            if (nextH.r === window.brickSnakeFood.r && nextH.c === window.brickSnakeFood.c) {
                window.brickSnakeBody.unshift(nextH);
                window.brickScore += 100;
                window.brickLines += 1;
                if (window.brickLines % 5 === 0 && window.brickLevel < 10) {
                    window.brickLevel++;
                }
                checkAndUpdateHighScore();
                updateBrickHUD();
                playBrickBeep('hit');
                spawnSnakeFood();
            } else {
                window.brickSnakeBody.unshift(nextH);
                window.brickSnakeBody.pop();
            }
        }

        // ==========================================
        // --- 5. BREAKOUT ENGINE MECHANICS ---
        // ==========================================
        function updateBreakout() {
            if (window.brickGameOver || window.brickIsPaused) return;

            window.brickBallX += window.brickBallVx;
            window.brickBallY += window.brickBallVy;

            // Left / Right wall bounce
            if (window.brickBallX <= 0) {
                window.brickBallX = 0;
                window.brickBallVx = Math.abs(window.brickBallVx);
                playBrickBeep('move');
            } else if (window.brickBallX >= 9) {
                window.brickBallX = 9;
                window.brickBallVx = -Math.abs(window.brickBallVx);
                playBrickBeep('move');
            }

            // Roof bounce
            if (window.brickBallY <= 0) {
                window.brickBallY = 0;
                window.brickBallVy = Math.abs(window.brickBallVy);
                playBrickBeep('move');
            }

            // Paddle bounce (Paddle is at Row 19, width 3: padCol - 1, padCol, padCol + 1)
            // The ball hits paddle top only when moving downwards (Vy > 0) and in row 18..19
            if (window.brickBallVy > 0 && window.brickBallY >= 18.0 && window.brickBallY <= 19.0) {
                const ballCol = Math.round(window.brickBallX);
                const padL = window.brickBreakoutPaddleCol - 1;
                const padR = window.brickBreakoutPaddleCol + 1;
                if (ballCol >= padL && ballCol <= padR) {
                    window.brickBallY = 18.0;
                    window.brickBallVy = -Math.abs(window.brickBallVy);

                    const offset = window.brickBallX - window.brickBreakoutPaddleCol;
                    window.brickBallVx = Math.max(-0.6, Math.min(0.6, window.brickBallVx + offset * 0.22));
                    playBrickBeep('hit');
                }
            }

            // Brick Hit Detection (Rows 1..4)
            const padCol = Math.max(0, Math.min(9, Math.round(window.brickBallX)));
            const padRow = Math.floor(window.brickBallY);
            if (padRow >= 1 && padRow <= 4) {
                const hitIdx = window.brickBreakoutBricks.findIndex(b => b.r === padRow && b.c === padCol);
                if (hitIdx !== -1) {
                    window.brickBreakoutBricks.splice(hitIdx, 1);
                    window.brickBallVy = -window.brickBallVy;
                    window.brickScore += 20;
                    checkAndUpdateHighScore();
                    updateBrickHUD();
                    playBrickBeep('hit');

                    if (window.brickBreakoutBricks.length === 0) {
                        window.brickLevel++;
                        window.brickBallVx = (Math.random() > 0.5 ? 0.38 : -0.38);
                        window.brickBallVy = -0.48;
                        window.brickBallX = window.brickBreakoutPaddleCol;
                        window.brickBallY = 18.0;
                        for (let r = 1; r <= 4; r++) {
                            for (let c = 0; c <= 9; c++) {
                                window.brickBreakoutBricks.push({ r, c });
                            }
                        }
                    }
                }
            }

            // Bottom floor drop - ball missed paddle and fell off screen
            if (window.brickBallY >= 20.0) {
                window.brickLives--;
                updateBrickHUD();
                playBrickBeep('crash');

                if (window.brickLives <= 0) {
                    handleBrickGameOver();
                } else {
                    window.brickBallX = window.brickBreakoutPaddleCol;
                    window.brickBallY = 18.0;
                    window.brickBallVx = (Math.random() > 0.5 ? 0.35 : -0.35);
                    window.brickBallVy = -0.45;
                }
            }
        }

        // ==========================================
        // --- 7. SPINNER RODA KEBERUNTUNGAN ---
        // ==========================================
        window.spinnerOptions = ["Peserta 1", "Peserta 2", "Peserta 3", "Peserta 4", "Peserta 5", "Peserta 6"];
        window.spinnerAngle = 0;
        window.isSpinning = false;

        function loadSpinner() {
            let html = `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-arrow-repeat" style="color: #8338EC;"></i> Spinner Roda Keberuntungan
                        </h4>
                        <small class="text-secondary">Undian Acak, Arisan, Doorprize, dan Pemilihan Pemenang</small>
                    </div>
                </div>

                <div class="row g-4 justify-content-center align-items-center">
                    <!-- RODA SPINNER -->
                    <div class="col-12 col-md-6 text-center">
                        <div class="card card-custom p-4 d-inline-block shadow" style="border-radius: 24px; position: relative;">
                            <div style="position: relative; display: inline-block;">
                                <!-- POINTER ARROW -->
                                <div style="position: absolute; top: -14px; left: 50%; transform: translateX(-50%); width: 0; height: 0; border-left: 14px solid transparent; border-right: 14px solid transparent; border-top: 26px solid #e11d48; z-index: 10;"></div>
                                <canvas id="spinnerWheelCanvas" width="320" height="320"></canvas>
                            </div>
                            <div class="mt-4">
                                <button class="btn btn-lg btn-brand px-5 fw-bold shadow-lg" onclick="spinWheel()" id="btnSpinWheel" style="border-radius: 30px;">
                                    <i class="bi bi-play-fill me-1"></i> PUTAR SEKARANG
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- PENGATURAN PESERTA / DAFTAR PILIHAN -->
                    <div class="col-12 col-md-6">
                        <div class="card card-custom p-4" style="border-radius: 20px;">
                            <h6 class="fw-bold mb-3 d-flex align-items-center justify-content-between">
                                <span><i class="bi bi-people me-2 text-warning"></i> Daftar Pilihan (${dol}{window.spinnerOptions.length})</span>
                                <button class="btn btn-sm btn-outline-secondary" onclick="addSpinnerItem()"><i class="bi bi-plus-lg me-1"></i> Tambah</button>
                            </h6>

                            <div class="d-flex flex-column gap-2 mb-3" style="max-height: 280px; overflow-y: auto;" id="spinnerOptionsList">
            `;

            window.spinnerOptions.forEach((opt, idx) => {
                html += `
                    <div class="d-flex align-items-center gap-2 dashboard-subcard p-2 rounded-3">
                        <input type="text" class="form-control form-control-sm form-control-dark" value="${dol}{escapeHtml(opt)}" onchange="updateSpinnerItem(${dol}{idx}, this.value)">
                        <button class="btn btn-sm text-danger p-0 px-2" onclick="deleteSpinnerItem(${dol}{idx})"><i class="bi bi-trash"></i></button>
                    </div>
                `;
            });

            html += `
                            </div>
                        </div>
                    </div>
                </div>
            `;

            document.getElementById('tab-content').innerHTML = html;
            drawSpinnerWheel();
        }

        function drawSpinnerWheel() {
            const canvas = document.getElementById('spinnerWheelCanvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const centerX = canvas.width / 2;
            const centerY = canvas.height / 2;
            const radius = centerX - 10;
            const options = window.spinnerOptions;
            const sliceAngle = (2 * Math.PI) / options.length;
            const colors = ['#E91E63', '#9C27B0', '#673AB7', '#3F51B5', '#2196F3', '#009688', '#4CAF50', '#FF9800', '#FF5722'];

            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.save();
            ctx.translate(centerX, centerY);
            ctx.rotate(window.spinnerAngle);

            options.forEach((opt, i) => {
                const angle = i * sliceAngle;
                ctx.beginPath();
                ctx.moveTo(0, 0);
                ctx.arc(0, 0, radius, angle, angle + sliceAngle);
                ctx.fillStyle = colors[i % colors.length];
                ctx.fill();
                ctx.lineWidth = 2;
                ctx.strokeStyle = '#ffffff';
                ctx.stroke();

                ctx.save();
                ctx.rotate(angle + sliceAngle / 2);
                ctx.textAlign = 'right';
                ctx.fillStyle = '#ffffff';
                ctx.font = 'bold 13px sans-serif';
                ctx.fillText(opt, radius - 18, 5);
                ctx.restore();
            });

            ctx.restore();

            // Draw center peg
            ctx.beginPath();
            ctx.arc(centerX, centerY, 18, 0, 2 * Math.PI);
            ctx.fillStyle = '#ffffff';
            ctx.fill();
            ctx.lineWidth = 3;
            ctx.strokeStyle = '#333333';
            ctx.stroke();
        }

        function spinWheel() {
            if (window.isSpinning) return;
            window.isSpinning = true;
            const btn = document.getElementById('btnSpinWheel');
            if (btn) btn.disabled = true;

            const extraRotations = 5 + Math.random() * 5;
            const totalRadian = extraRotations * 2 * Math.PI + Math.random() * 2 * Math.PI;
            const duration = 4000;
            const startTime = performance.now();
            const startAngle = window.spinnerAngle;

            function animate(now) {
                const elapsed = now - startTime;
                const progress = Math.min(elapsed / duration, 1);
                // Ease-out cubic
                const easeOut = 1 - Math.pow(1 - progress, 3);
                window.spinnerAngle = startAngle + totalRadian * easeOut;
                drawSpinnerWheel();

                if (progress < 1) {
                    requestAnimationFrame(animate);
                } else {
                    window.isSpinning = false;
                    if (btn) btn.disabled = false;
                    // Determine winner (pointer is at top: -PI/2)
                    const normalized = ( (3 * Math.PI / 2) - (window.spinnerAngle % (2 * Math.PI)) + 2 * Math.PI ) % (2 * Math.PI);
                    const slice = (2 * Math.PI) / window.spinnerOptions.length;
                    const winningIndex = Math.floor(normalized / slice) % window.spinnerOptions.length;
                    const winner = window.spinnerOptions[winningIndex];

                    setTimeout(() => {
                        alert('üéâ SELAMAT! Pemenang undian adalah:\n\n‚ú® ' + winner + ' ‚ú®');
                    }, 100);
                }
            }
            requestAnimationFrame(animate);
        }

        function addSpinnerItem() {
            const name = prompt('Masukkan nama pilihan / peserta baru:');
            if (name && name.trim()) {
                window.spinnerOptions.push(name.trim());
                loadSpinner();
            }
        }

        function updateSpinnerItem(idx, val) {
            if (val && val.trim()) {
                window.spinnerOptions[idx] = val.trim();
                drawSpinnerWheel();
            }
        }

        function deleteSpinnerItem(idx) {
            if (window.spinnerOptions.length <= 2) return alert('Minimal harus ada 2 pilihan!');
            const item = window.spinnerOptions[idx] || 'pilihan';
            showCustomConfirm({
                title: 'Hapus Pilihan Spinner',
                message: 'Apakah Anda yakin ingin menghapus "' + item + '" dari daftar roda putar?',
                confirmText: 'Hapus',
                onConfirm: () => {
                    window.spinnerOptions.splice(idx, 1);
                    loadSpinner();
                }
            });
        }


        // --- CATATAN (NOTES) ---
        async function loadNotes() {
            try {
                const fetchRes = await fetchApiWithCache('/api/notes', 'notes');
                const data = fetchRes.data || [];
                let html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                        <h4 class="fw-bold mb-0"><i class="bi bi-journal-text me-2 text-success"></i> Kelola Catatan</h4>
                        <button class="btn btn-brand" onclick="openAddNoteModal()"><i class="bi bi-plus-lg me-1"></i> Catatan Baru</button>
                    </div>
                    <div class="row g-3">
                `;
                if (data.length === 0) {
                    html += `<div class="text-center py-5 text-secondary">Belum ada catatan. Klik tombol + untuk membuat catatan baru.</div>`;
                } else {
                    data.forEach(note => {
                        html += `
                            <div class="col-12 col-md-6 col-lg-4">
                                <div class="card card-custom h-100 p-3 position-relative">
                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                        <span class="badge bg-secondary">${dol}{escapeHtml(note.category || 'Umum')}</span>
                                        <div>
                                            <button class="btn btn-sm text-info p-0 me-2" onclick="openEditNoteModal(${dol}{note.id}, '${dol}{escapeJs(note.title)}', '${dol}{escapeJs(note.content)}', '${dol}{escapeJs(note.category)}')"><i class="bi bi-pencil-square"></i> Edit</button>
                                            <button class="btn btn-sm text-danger p-0" onclick="deleteNote(${dol}{note.id})"><i class="bi bi-trash"></i> Hapus</button>
                                        </div>
                                    </div>
                                    <h6 class="fw-bold text-light">${dol}{escapeHtml(note.title)}</h6>
                                    <p class="text-secondary small mb-2" style="white-space: pre-line; max-height: 120px; overflow: hidden;">${dol}{escapeHtml(note.content)}</p>
                                    <small class="text-muted text-end mt-auto">${dol}{new Date(note.lastSaved).toLocaleString('id-ID')}</small>
                                </div>
                            </div>
                        `;
                    });
                }
                html += `</div>`;
                document.getElementById('tab-content').innerHTML = html;
            } catch(e) {
                document.getElementById('tab-content').innerHTML = `<div class="alert alert-danger">Gagal memuat catatan: ${dol}{e.message}</div>`;
            }
        }

        function openAddNoteModal() {
            document.getElementById('modalTitle').innerText = 'Tambah Catatan Baru';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Judul Catatan</label>
                    <input type="text" id="noteTitle" class="form-control form-control-dark" placeholder="Judul catatan...">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Kategori</label>
                    <input type="text" id="noteCategory" class="form-control form-control-dark" value="Umum">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Isi Catatan</label>
                    <textarea id="noteContent" class="form-control form-control-dark" rows="5" placeholder="Tulis catatan di sini..."></textarea>
                </div>
            `;
            window.submitAction = async () => {
                const title = document.getElementById('noteTitle').value.trim();
                const category = document.getElementById('noteCategory').value.trim();
                const content = document.getElementById('noteContent').value;
                if (!title) return alert('Judul tidak boleh kosong');
                await fetch('/api/notes', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({title, category, content})
                });
                modalInstance.hide();
                loadNotes();
            };
            modalInstance.show();
        }

        function openEditNoteModal(id, title, content, category) {
            document.getElementById('modalTitle').innerText = 'Edit Catatan';
            document.getElementById('modalBody').innerHTML = `
                <div class="mb-3">
                    <label class="form-label text-secondary">Judul Catatan</label>
                    <input type="text" id="noteTitle" class="form-control form-control-dark" value="${dol}{escapeHtml(title)}">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Kategori</label>
                    <input type="text" id="noteCategory" class="form-control form-control-dark" value="${dol}{escapeHtml(category)}">
                </div>
                <div class="mb-3">
                    <label class="form-label text-secondary">Isi Catatan</label>
                    <textarea id="noteContent" class="form-control form-control-dark" rows="5">${dol}{escapeHtml(content)}</textarea>
                </div>
            `;
            window.submitAction = async () => {
                const newTitle = document.getElementById('noteTitle').value.trim();
                const newCat = document.getElementById('noteCategory').value.trim();
                const newCont = document.getElementById('noteContent').value;
                await fetch('/api/notes', {
                    method: 'PUT',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({id, title: newTitle, category: newCat, content: newCont})
                });
                modalInstance.hide();
                loadNotes();
            };
            modalInstance.show();
        }

        async function deleteNote(id) {
            showCustomConfirm({
                title: 'Hapus Catatan',
                message: 'Apakah Anda yakin ingin menghapus catatan ini secara permanen?',
                confirmText: 'Hapus Catatan',
                onConfirm: async () => {
                    await fetch('/api/notes?id=' + id, {method: 'DELETE'});
                    loadNotes();
                }
            });
        }

        // --- DAFTAR BELANJA ---
        const SHOPPING_CATEGORIES = [
            { name: "Pakaian & Fashion", icon: "bi-tag-fill", color: "#9C27B0" },
            { name: "Perlengkapan Sekolah", icon: "bi-mortarboard-fill", color: "#2196F3" },
            { name: "Supermarket", icon: "bi-cart3", color: "#4CAF50" },
            { name: "Perawatan & Kecantikan", icon: "bi-stars", color: "#E91E63" },
            { name: "Elektronik", icon: "bi-laptop", color: "#FF9800" },
            { name: "Mainan & Anak", icon: "bi-controller", color: "#00BCD4" },
            { name: "Perabotan Rumah", icon: "bi-house-door-fill", color: "#795548" },
            { name: "Lainnya", icon: "bi-box-seam", color: "#607D8B" }
        ];

        function getShoppingCategoryBadge(jenis) {
            const found = SHOPPING_CATEGORIES.find(c => (c.name || '').toLowerCase() === (jenis || '').toLowerCase());
            const icon = found ? found.icon : 'bi-bag-check';
            const color = found ? found.color : '#607D8B';
            return `<span class="badge" style="background-color: ${dol}{color}18; color: ${dol}{color}; border: 1px solid ${dol}{color}40; font-weight: 600; font-size: 0.75rem;"><i class="bi ${dol}{icon} me-1"></i>${dol}{escapeHtml(jenis || 'Umum')}</span>`;
        }

        async function loadShopping() {
            try {
                const fetchRes = await fetchApiWithCache('/api/shopping', 'shopping');
                const data = fetchRes.data;
                window.rawShoppingList = Array.isArray(data) ? data : [];
            } catch(e) {
                const cached = StorageCache.get('shopping');
                window.rawShoppingList = Array.isArray(cached) ? cached : [];
            }
            renderShoppingView();
        }

        function renderShoppingView() {
            let html = `
                <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                    <div>
                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center gap-2">
                            <i class="bi bi-cart3"></i> Daftar Belanja
                        </h4>
                        <small class="text-secondary">Kelola daftar kebutuhan barang, estimasi pengeluaran, dan cetak daftar belanja</small>
                    </div>
                    <div class="d-flex gap-2 flex-wrap">
                        <button class="btn btn-outline-secondary btn-sm" onclick="printShoppingListReport()"><i class="bi bi-printer me-1"></i> Cetak Daftar Belanja</button>
                        <button class="btn btn-brand btn-sm" onclick="openAddShoppingModal()"><i class="bi bi-plus-lg me-1"></i> Tambah Barang</button>
                    </div>
                </div>

                <!-- 1. KARTU RINGKASAN JUMLAH ITEM & TOTAL BELANJA -->
                <div class="row g-3 mb-4" id="shopping-summary-cards">
                    <!-- Dynamic Summary Cards Container -->
                </div>

                <!-- 2. FILTER & SEARCH BAR -->
                <div class="card card-custom p-3 mb-4">
                    <div class="row g-2 align-items-center">
                        <div class="col-12 col-md-5">
                            <div class="input-group input-group-sm">
                                <span class="input-group-text bg-transparent text-secondary border-secondary border-opacity-25"><i class="bi bi-search"></i></span>
                                <input type="text" id="shopSearchInput" class="form-control form-control-dark form-control-sm" placeholder="Cari nama barang..." oninput="onShoppingFilterChange()">
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <select id="shopCategoryFilter" class="form-select form-select-dark form-select-sm" onchange="onShoppingFilterChange()">
                                <option value="Semua">Semua Kategori</option>
                                ${dol}{SHOPPING_CATEGORIES.map(c => `<option value="${dol}{c.name}">${dol}{c.name}</option>`).join('')}
       xúÏΩ˚v€FÚ0¯ˇ>E[Ÿ1…X§DŸrY≤ó∫ÿñ/≤Gî'g÷?ü$@&	0∏X“dtŒ>ƒwŒ˜@˚&˚$[U›4Än §(«IÃÃX$–◊ÍÍÍ™Í∫0¶˘ÏnÑŒ‘FO˛›[^¬vøîºÜ∑l8µ¬pomËO€˛;≥€˜◊ÃïÚ]oGÌÛ¿èÁL˘ﬁgçPC·‹Ú4-µ#Á2bÉÛvXî	/b¯¨:Cﬂ≥≠‡ä¸¿vÇ‚n›Ë™ΩµΩˆd◊ïç\6p€CkÍ`aò‡ÓÜˇ«˛kìÜ∆¢´π≥∑f[ë≥∆\{o-˚ÛC¯ıÃùFN∞&ª˘¡¨Éä ‘mËyí}Pbæ7[ﬁ94Ì{}hrÓzÁº…zﬁl≠±»ç¶PÄ?f'∞≠‡EP‰‹öV≠X&,Ü(›-â)]æ* ‘™‚(ÚìµDÉˇ√¸ÈèGS◊sî≈ºhw77	6Sw8Ÿ[ú–â≤–	¿ú‚k&¢∞ÏV¯ ÛÿÉ◊√©?ú\∏°#qÄèl)‡^â«≈Áw⁄mvø√Œz˚GØŸaÔŸYÔî¡◊ﬁ…Àk∑5©Ä∑õ·?ÌaF˛åÕç˚T≠f∑GSÁí}Ü:ÓËä6S{‡Dé„1kÍû{m7rfa{Ë |ÿlPJ 2{6∑'√ô5úøh¸©ùn\≥vd¶∞¬ë≈·⁄ì7Œ,∂"õ…Ít:e±¸ <yÛÄ&sﬂ›/NŸ®l¶"„’qÉJr»Ã\€Ü◊ ìÕ*è∆ée◊ &QP]H4»¬Ë
—˚¬µ£Ò€˛«„µ‰˘ä≠=˘aw#/€Í6˚‰ƒöYlﬂz{~ì∆∫è∞±óéÁÜlÉΩÍxÓÓçZ,Ãö®Õ+8∑X∞…Ún“¸#Lˇ]›hÿõ⁄aø≥>[ìµ˚@€ÓôY”µªÖw∆Oìïî∆◊õÑ5± JUllßr´ÌFﬂæ“ë |^á7Ab}xÂY3w»N˝ãPK°s„¬∂ÀN¡2gâ˙Ë”„ÃO€∆3@⁄ŒπM¸∫ul7–ô§˜çV«ı<'xqˆÊ5€c„h6Õ6bb=“R◊Èy6äΩa‰¬i=Ú‚é-´„Q“l±ﬂ3Õ√0¬à÷t~·z∂—Å≤Œk^˛˜øÏ√««öZ°c√1Tlgä+‹ßb«»©5ZO;_¨iÏ`£ò{‰øˆ/`RVSÍDÅ;Sgñv5Ñ#iœPÏÊÄì¥+>ÔlO}<‘∫Üëi¨j9e%s„úÂ$'äÅŸQÒ&ﬂlÔIÊiÔ3+é˚åw@°m-"RsœÉªwYÚ#D8≥ÿv¬&o≠’™hÔ3	≤A˙U’‚„≤È–R5i≈ˆˆ$Ïi5;§zP?˚¥º◊Cæéwh=ìÆût&~wÄŸ	¢77±ÇÆm±òÍ
A+…ÂwÏ7[˘∫bkö6¥vcéƒ&Ü©ô∑t∂±ãáqÄ¬Yæ
4$€ÃaÔ¯Ù´}gjyü-(π˘X_ÇŒ í˜pBﬂ&ôvÈÚ¸!î˙˝:79º»cG÷p\πâ∆ƒpÏ1êFCÁŸ‘∑"æ¯ÙúÛ!Ñ}õ&˙çÜK’è=QûQ•Æ©“a§îÄãù”ÎÚn©»	0¥{b
?“H~dÕL€¿±lëü‡≠ÈüûÛMß6~/Èµı∏(eñˇﬁˇ]ÏY¡Å{{IÉÜràP
:◊t®wÖ6 q}?ãgÕ¨3¯Û~~‘¶aﬂÇwã˚3Û`cÉXÅÀ&Ç'…9Xﬁï5)`˜Ã∫î«Ù⁄¯ˇ˛üˇ’(n,Ñ√(n¿h*Õ˘√pùë‹˚ë˘#ˆvŸFXÓ¿:õùM+O„é∞%ÏÂI“°Æ~îQï"Te©tn√b°Î,@|?'‚€èg39¶v®• ¸ ∏ÀæßÍÏ%ˆ0‰M∂©j#∑ô6up»ñ»\ü*EXEµŒÑ6nz^.åW(ÿòÙ(ı’yK©∫µT}§–Í@`ågSkÃ§Ÿ›†íu¥rY-œ¿øÑ&≠WIπﬁ»g£∞ΩÕ’<7—éQëÒÉDø«’J7\;˛n˚”ÎﬂìÉeÍxÁ—¯öë0ºª1~P—C	Äÿ,jwô›†Ó*ÈIækˆﬁs#F" tµäãkµËU5∫>¯†Îb†N⁄Ã!Ï‘r¢-°ÿéáC'ø  û8÷ûŒôäNbNƒ£∂ºGÖwﬁl∏v˚¯∞—∫^)¡™Õ¨–Ï≈6å3çQı«°Ô÷ﬂ}Ö*jI‰ú°„Œ≈≠ÃÖx»ıØy∑µ»+{“"/Õ®u∑Wè∫ÛZPº5t˝;0RãÃŒ$«º4÷F÷9«£y‡˚∏Jå}®≈XŸb, §”-¢∏Ω8ÏÑCkÓºàf”¶¬%∑ÆìﬂT 0˚·™ôãÑõ¬6Ÿ”îπø«$C±?)láK(◊∑∞	röN£0pFw8˚æ}•ë∏"∏é êÍáÛ‹ø–D“Â’k¶æ@{ºñNûP⁄”	 k.Iú·≠8HoœöÕ›)ﬁˇö∏NeMÒ≤Y¶rm%U‘e-á<û¿jís√!U€¶Ip§ñ≤≤^íÌF6R9º‹[˚YwI√ÊWÌmñ'˘=Ôz ‡Fø/ë˜¢`ªfq‰ÿú úπ∂5añm—≠§ª≤‡∏˙C¬lòÄ¯†§.|€j§ŒÓFd?°õãOâñ9”–1LE˘¿øqW£∞ØQF‡ß†Æ"˝À:sÌÀñ^k%?7÷^eZPãï≠|mñ¶•ÂµZŸ∆n¢›*oõy˙4˛≤ˇˆ§Oá'u–ÍŒ|jùÊ∆⁄∆˘:k‹˝-ˆ£«ù“L~\πgR)»O≠ãf⁄d≈çïﬂSÇê ∂°¶Îö–}ë∂•IÄÊ$K.ZıõïÕúß6!Ú4‹∑ÏsGπX†’‚≠e _“˙ó∞ñÀt!oö3?ö>ë±∑i™¥u⁄ﬁdÛK i@ª	åUØÖa≈“ »]—	üÙêŸjúO»n?`˛¡ö ≈Ü≤iLPƒ“-¯ìÚÀZ'ÓÑÖ/¶	FyLówE®|n/›[µ¡URS·ﬂ—‘ä[Í%ﬂÍŸÈ%çÈ≠∏§˘*√Rõ≠ÜÂëÌ&[Ùço[”¶$%Ç2^∑í∆≤¬•xä„°ÎNÎ⁄l-8pÔ∆≈‹Ãv‡\v‰¿’1w\˚:µ;{aÕ„–8Ê(∞¬Ò2CÆ!nbU∏SnL°·Usm8÷ä|ö<å™µ¯È∑‚U•ﬁ∂O{Qôÿ‘7X—=ˇ*nı5 øÍÛó˛E.ê_>D-∫ÆN
ÈÃ∞$›5”‡å.gÓ¡ÌçiK‡∞N¨ô≥éC;∞–óÍÓG#Í⁄¿FWAçtÊz†O+≈/•cÀ!í e¿“Ö¸XÛy˘/([t ß◊”L‡/†T,?5	”TÏKÑ"ïºí$†~ÕfN{kMöb˛;JZêÎ«ú•èqL◊QØÄy*î¥∑^jÅç°cª!∞∂W‚÷;‚vØ-ò”Zj<eí∆HÉ‡˘û”0Dkﬂ Bœ∂≥gãÂ"xwß<å≈s.ÓH¥Úè˚o≈ŸﬂÍ¿`›®Ÿ8k¥>lÊ¨êåH1√>œ êÊUB@oúY≥Å∏Œí∑π…Èâaô[q∞ˇ‚Ìªw«'œ=Ëù={z|‘ÔÃ¨ysà2_ë˝ﬂùÙXªV¢√¸πÌ_p==çÊXI¿ßq‡åˆ÷>[_¨p∏ÛhÁãÔ⁄ÕÕñrTÍàBC¢2ˆŒuDõ‰âãËöyBÿz›hï©2u®œõ*≈|—ˆc&m]7Áóè+Ù}ƒé?…Œ†‹@Ÿ“ôÌÂó‚S´ÛŸwΩf£ ﬁï#ÿ>©£*ÆíU.Øƒ~{óO’QÅ?—≥∞äM0˚fÑE´ÿXj¸çç‘ÙçXc$èÅwwÇΩ5ºG˜a—ˆ‡£ÿˆ$‰7.™Ÿs?p<¯iáÕL‹”WZﬁˆπ
r™~]ÏÕ≤çPdd+¸MW⁄'$"Úœœ—6ûÊâ∂lÇ*eÊ#IZÓ˛CQ}?Çó®JkB—î2EÜ¥∂s	€ v†Öë5ÀåˆiÚ$(S∫3^ÁÔD˜s∑ûH-à Ùc8≠f "'™r™·$O6rÃ2ø¯äêo«<%`ñL{pUPAÁf÷e{Ï∏Á„à⁄#§jÃˇ‚£©—æ⁄aV˘è+†ô≤ŸcÁ∫±	ãKT˚*i‡lQä3¥Y÷±I{k äîy∂`e-CñêôY§	ÇŸa»cî•ÑàPwb¥K˙yeh€Y<*¡Ì–ã∞	ı“ÈtL≥_–›H!- ©±ÛˆVôsO¡∞Tã—D’YıA≠
¢XX/ûp}ÂzPÉµOÅ^õklÊz¯˜∆7±+Ñç0}j˛ïË7¥±(P∫(¶k⁄?-ŒqÀ—Ê?Z7)5≥<¶rÊ•≥ |o8¬œ'5aZDYX—‘¢ JT±\6ª^
˝‘Gür\µ∏Û„¡Ãçz\h‹cVxÂY”pg∆1r}®PP W\9MFÎPÇº€„$æû∫£¢9î≈EsYIZ˘ß™8*µ]È˘ñ8ü«!«¬õn%S‰ø´tÅÈ–îª«ÏÂ[ÈPâÙKX-dq_⁄,êŒL£Â˘åóHΩÒ ªÅä≈;N∂âM m§π8ƒï∫C˜k“Ÿƒö:A‘lêt7‡“›ÖıŸ0€uC˜N^≠ï4¬Ô”r≠$bélbÓN›ÒÌ¶uaπ9—p‹llXswCöJ4÷MFÂN4ˆÌ÷x˜∂÷X◊ñAg@'wÿÔç!›ú°j@-`ß.p∏∞˘7>áæ◊∏÷7ÅöÌù¸ÌÔµuéﬁÎ*∂Æ#f≠sLXó´v›*"æí$”√j[ﬁ–È ?Ïh)áo%™≠¸˚ÎÏœlÉ –ã*5Æ˛^Ü.§s´∞ÑÍã.qT|q¢ùÜ"hÎF~ÏŸº¶NW6ÇCÄ+À∏"GÁñÙ^Í∆ª≥ Ç_‘∏¢áìÏ˚iÚïTR®œD{tÎº=;√âv‡º%“Àö"’∂ı√ÉÉﬁ≥ÌMm3n¯ —öÿ˜˝©cyMY?øø+ø+ø+ã®ŒE¯Îò“Ô ≈Ô ≈î<§ÁA)âPà}ÓN¡æÙ jïíÏwÖ$√Ì*$•›á8dü™¨ N*ÅÖ◊õh+EﬂwdÁ@íTFIï 5W©◊ﬂ≤¢5ú9ﬂIa¨ﬂ833†ˇ¨J™oC1™ö7©B9 ™◊ﬂï¶Â C%ÍÆø+RWØHUÕ/Yú¸Æ^≠8’‰tI™>˙Æh’NÊª¢ıÔ†hU7‘™î≠´’ìæˇÍjR˙¶—ÎÏØ•3ÂT,—úÊÃ¬];O∞)æ≈ƒ#7ò5ãÀE¶„ tn;ûQï°>s¬–:«‚Ωπ5ñßª≤&.»™ JxÁcjJ†óp\Ca÷sπ†mç@¬~™i»áâ⁄€Ãê4E}OÃißÇ∏‡å(¸N6tAd˘=A€√£◊GgGìÈyŸ⁄Ú%À¸™PÄœuÌ7zBû:sv©ﬁ|î‘i‹:Sƒ+ ãF∂ÅÁú‡VQË≠}ÁcyáC$K’‘d5O*n#îV·uâo"¶‡êËŸé…%Ò{ ≠øD -jÅ€S7Û.`h÷˝I„vÕ˝¿‘7%Æ`◊≠O“L3äj»R◊GìûdôÅkx9*nàFrµÉWI+È%X≠f2s
P∑*ˇ≈
Äq?√%Fß"öÄ,”àTõçÄ£i_˝#:ˇ=^ï√_…
’˜Â”˚aÂ=/Ú{πINd´‹Ë*]Ùù§p,!7∑l¿!Æ≠òP\ˇË—# ·!1â/¸ƒÊ‹ÏÌ~≠ªrƒ`≥Ç·‘_+›9|{pˆÔwGTR#Çöc Ô[a±·il¥∑G£ˆ#ìÿK‹òúß§•mˆÊäı*¿Y æÛB˙åfÙ°‡ø3¬œë5sß¿@áñ∂C'pGèô¸˜ÔﬂÃf∞i]oé$‡ÌÜtÿÙÄY˙a4Çäf≠¯∏“R”˝À„+û6æµµ%o¸∏’∂çΩ”(C˜?ééπìyE>L˙à¸y±Én∑ºó˝wñƒ”ﬁ¸«cÈ˜ùL≠y≠»oŸæ¯‡Èl2âΩÒ†¬¸íÖ˛‘µŸ∂m+ÀÙR'>uFQy”ÿl≤|m	†—O¯üeÜrï4÷II_˛Ù¨∫™∏Ö”Ø]Ye§†mTD˛Æ±n~˛W÷Ëˇ5sl◊‚‹xI ¸à]î¨¬&ª„ŒêO∂º“IS7s††≤‹M›≠Ÿåu∑g≥«å#›Mï4¢≥ªaÿ˘ªz ¥kæ;ÓVë (°Ø:rËÅﬁM:Vro§ p0ÕM‰œêúøØiΩVúâU'3XY"C Å'"n„ö–1=ãÁ-∏çúÜ¯ˇ∫/ê±@”Í}C´‰+∏qÆÇr'¯ lªQ˝Ò#6ùd√ 	lò$í¨÷	í¶Út˜µP=z1±ÄΩ‡¡@wñ.!ÈÎfqM
aJÈFˇFcZ0‡ﬂÚ=ÂÏ8~ÿ‹¸i \◊Ò2ÎÜ¥©ÓJT.9cL…'v7Ùï‡Ã)0ﬁ˘ª:ªπ∆ÍPh“õ
øØ◊√ml∞vªÕzœèN{Ï.{Ÿ;¸•˜üôÙÆ®˛ÎùcÍÆÇé.
Æåö&R?û:®ºSÙëΩπã!˘¨·ÿ∫Iã⁄F{AÒÕ®ø°–`{IÀ˙≠Q·’gc!îÈó§[ˇi_÷|¡(ãÿπ5/^ZÀÉhöí§µù/(≤°YS.í⁄/1æ∞|gÒπUﬂÿ~e á¥≠Xy
ü~ﬁ≤Ë◊DÇô∆a{zéÎä±{^±‹6™ÊÖ0ør◊›∂kÇ¶à†V≠hwc©π dØ™
jt&ûQp:é°=>ÿÚ:|Fã∆ù£ÒVÁ>(å∫ú<≈’
≠mfŸ¿•∫∆ñ
`Z∂©
ùiCy%ß\èTmˆWËŒòUèáá5àmB	jZlT√à—ı–≠D2¢¢Ü ™”å∂∞†%çÌxZ'j“Õº,nW6ñ{æ3‰,óz3»SNΩ¢»ÒôägJgLÙa%â£Æ—• Hà££wKÙê!ãtyŒØ–eåÆ'œ-TπŒx˛@N°◊Èà˚“kÌ§kEî…ú>Êx2_=òå8oA≤çã¥˘òˆ+≤Y]s˛‘Ë…:_ÅGÈñ˚´û˙K$H	+≤∏;ÉuN-,2Â‘ö[x˘ 9w·Ä˜Ù±æ$âZ≤HÑ é2X`]+p,^µ6PÓ&ô8◊bî^N$ âéÍö∫©?ó∑t´apî‡Ω472â t‚ï∑DTiÊ&$æJ•ÌÒÂ®e7wá»9Û'æ)Î™ÿ?È≠¢ÈH"û}É>¢be◊˘≤¨pÆ≥»ù£’é˙ ù§4|ãfN¢ãU9ıƒ'ºÄyì¿ûâ mh›T◊¢…ÿˇmö¯êñ∂h“/"_õÃØZ´Ü-æ¥Ïﬂ≤%…gjï$‚€ﬂZIÇÓ6Ì±ª 3(t$|9@n¬hÒ´TëÜóPëä+Që,êê<_uÅd›TıVvÛñÎ%ÌÊek&Ó?˘≈öDq˝T ≤ö‰›ñ®πp∫mQ≥[∫~æfj¢2g≥(U7E∫^#Ω:’[ﬁDÊÅ)ΩƒÉ"œ˜ÙògÄ¿@„Jª¸¸⁄∏ Ë√¶Áiã5µÇôFœ†*π@‰_‚¶*SÄ<˛πZ1hÂ
È
}â˝‘∫q˝∫∫J£>ØF¥rqˆ/§“´ØÃ˚ä±∆KuYú4»À,~T˝ï5\ú◊Z©Ü+√<‹<‚KÜ≥ºŒ∑Ø°¢√ô5_Z≥rø∆¨ñ∆ù	-’gõX80‹£Ì≈≈W“»§¨≈‚z©œ∂¨ΩújäÏr&ﬂÇ^*eìñÇÇ®ΩË“c¶Èzs^ô÷âŒ‡2Õé¿‚*çS"ró6%—£¶ﬁ)âzZﬁ®(UO˜î0˘ er´û°˜T=—ÚÆ'´≥ûÄtÂ⁄&©V∏EmìËb⁄¶ÏIt3•_êvø∏ë.¢uz)pg’Z'>¶•µN˙≈‰kî˘UjΩÛ™◊?>egßΩì~ÔUˇò5a∞ª¨ˆˆUãù<Ôù∞˛AÔ‰‰Ëùı^¡À”˜Øÿ€≥∑ozg«˝å¡™ÅÊ~ÿZ»ºQQ	o⁄‚©¬îâ≤ß<m≠≤÷Ã	¨~`~pCÏ˚ù?Ö∂@ˆ˚B ˙b9 ˘—ª±Ô9X˙ÿËw6á¢xÿÒ≤8/+YŸ˚V0Ùmke]Ò∞úp+|„à¸·’q™∫L)ÚZ<,/¥‰YP¶+!Ÿ£»C®å√Hè5L!ÊPS†^‡â∞ñF«ó¨¢sîÃèÄÑYÛÔÕH˙sÕ4ÚÒ¯≤∏*»Ω°ÔroPËYE%# t¯f Ä	5Ä⁄Lm Ë˙6m‰˛ïç¥Çg∏}˜gŒ˙h≈_Ñ%∞≈bÕQ‡¸SÍ>ÿ‹\gvX7;õ?/¢~Fó22Ki/∂]üN ÀHÒ™Ωp‡îó-3s·áCåì]vÜ∞Â#Ám8tßS+ÚsùsÀı2ïû√]qhºÉ|!
?°[»[#ã Ñb«^uB'˙2/Ω˜>Ånù˜√w4>’ÙÉÍ–?π6;˜´Ô\ŒÅdyëkMOÅ\ù˘Ÿ÷6ªÖÊ‡`ëãjÄ  ÕsÜQ;0u.À`„6êJ◊+kë¢¿ô ¿ºY{êBfgz°0?Édû±0û£´c3? í¬vr’rl√æÀÌ%‡Sÿ=z—|¿i=6ÌéãW‹T»€‡¶:QJ5g·˘:pë∞b∂O\òÜ¥DÅ≥@e†9‚
:“lÄhëgs©JáDûîâ}‚:à$÷ôÏ¯)ß§ó™@
.∆üà,Ïçk$8.é∫=r/v¥Ñòé2¬
6ú#ÚÌK_Ø%ÃAÈ~Êìnƒ<´‘0•⁄ÅΩ~"GØˇ¥…ióúƒ‡ì∏âñÌ∆¿xﬂßRâÛP~≤≠âãX÷ï…Ú‹-:óÕ~~≥ng;DŸ&náè∫V¶Í @Øê06l{ËC œ»SCéu.°.CÊıµ∏ …ƒUsÇáÁâ¶2√A(ÖÜüÛÏÉ±;µõ4¯é π¡ÌÂ«Qìs¶|ÜÅ3ÛøÄå∞Œ∫3.Óπç@¡Sè0¿ë)`Ç/Ÿ0h ¯3ˆ‚]∆”â5ø∏{ﬂﬂgÏ˜ô€“ÏÏ a¬êe#≥´º‹ R,îãPÕÕ±6€Fú ‘”sã 6˘´YŒ◊ÎD¸}‰ØcõüàÛüX°l ∏qv¯Œü°≥'pA?ùÎO%°ÖÓ@W"f}¡Ã≠.VCâµ,l]jq≤ªwôÚSﬁ÷<1ﬂ’‡G≠!/Gú/ÂW#≤sÁK'JÙâa•À:ó#é®=ËÅ†~∆ñdPà á bÑQ•ÿÎ 5ÊwÏÇ⁄:]Ö˙)Ò]4‘,÷HèÃ ì¸ÂdAÿÛ[¶=ØlL›ñ”∞”ö˝£Õ+8u¨ ŸR∫J≈…’‰≤ »RØÁS Uê â≈bœcï	 5K πb’≈jœöòy¨B<9AJ≠P
Kı˜Èm.Üä·=¥ìäÖI¡˘¬πuÑõ@v€lLú+åø⁄X'ß∞íË2π4-cª£Çä∂sc)R Ã8°JŒIˇƒù£òò-å2,,˝áFÏé"4#9‡jR¸⁄õ“ì7ND^8÷<|çÒL◊ÒzaÄéËúk|Ñsv8çÅôl:Ë†ïRÆBWTÄáp;Ú(´«∞`~ùÚ¥«ÓÂB(Ì‚(r9†Sh±,ƒ[ö‰G+z)£]jà˘£åmü|F¢ô®`?IuÄÄ¸ﬁqmPÿfb{¸3váëk∑Çúñù1¯1ì1ß3Ë 8tFV<’J	Ú£ß¥jÑkVƒêF∞gnËNXS›Á∞Ôç‘VG3KÓÙk.k±UòÆ{ÌxD.`eú dV†ûÁãúE ¨Ç=ΩbÕ]ˆp{"S<FJ P0r9≠*ÏÅÊ∑M˚Ñ6îji—5≠xaÓ˜‡ºƒ⁄ïJGDoÿøπ˚ï/°r≤®]¯û$µ©íh’|ô⁄k˚ 0{cM,˛ﬁÖgG1>„
Ìµ¨>yÌôé·Õ?~¯v<Å⁄ÄËë;MÖŒònrÖÅLÄ{Ó§–å º'ZPú∆3kÃ∏ôræ‡w ¢∏∑#w˘¿iüQ¯Á|ŸóVh±æsÅﬁÉß∞©≠iæ]ñÂûâ0úk…”è:u&ﬂ˝ØêQñÒ∂Erkì°FÓVv5x À(~°Kˇ|'iKœQc‚H™‚ä&ÌpQè~)5Ï6Ö¯÷)≤íZ#òGX∏∞1·∫Æ7‡:L]iyßlx<ZÄ~<¯óÎ “∞@~}ådãæ±ˇ≤FHjn˙•⁄Ï∞ëo9jÌêÀúï”0üN¶”f»ˇÊÅ[:LQ';[(g;ï|\ÔJØY®ÇF*’ Æ9Ö¬*ÃuIVDŒâ—`ã¿≥›LaÕı#?∞ŒÓ™¶ÍvgÑ;Ï√GLîÉÔ‡å°ˆ>Z∏√Ô˚Ω=÷Ô˝ª◊»_:Êµ‡’Kï,RÆ∞ñ4ôW;µU_çÚ∆á^˙e)∫Iäãﬂ⁄
≈;%ﬁïÆ›©ë+’QåÀfàFà„ö∞]`âµíECË[S¬L9F Yv<tö¿K◊i8$≠¿/vèQtXàE¡‡ã¿®‰É'
%õ¥dc 6¢4≈8ñ_‡=£õb$Há0ƒÅè&ÃÔ#¥LÈ£p5c‘\>Œ%2›≤{$‹ÇtÈ6AæõW„8åC8=Î3û&p‚â@?}ß8•7D¿öÜñ^˙œ–ÀFqÍ^XWp(ü ÃÒRñ¢Ωüb–ÿ~¿8ﬁ9ﬁÁÿöÏ∑Æã4⁄Ï5{}MG&çrôY˙ùvõQBØ3¯$¯ﬁ`ñîwÄdò`‡–F√ﬂçŸ+7pEI‚∑Xª]œÿΩF‚™R∞˝l°l€~Ê”ÊË“‰Pi¸aíg¡9‹éboç pb}qœ°I`;QÌÒ≠Q«¶>on:áf—Ò$rá÷¥"9µRÀ2’òÂFÄ.Û¨<¡ç|2µçÎµeà©átïêª_ËnQ¬Øj»ò2ß
.úIÕ¥±p≤ŸË“¿∫N⁄54Â¨4ﬁU¯qîxú‘öMEµΩÆ‹¶∫Ó‚¢Ø@t>6‹bàJ#MËQ¥Ö*î≈o¿Ø˛‹∫ u;7ñwo˚ãt¶ÕÁV,Ta´v«AcÄπ6“+ÓT∑≠?C≈FÊî#A/!I˜‘	ÄÍeÃä‘‘◊C⁄pÓ8∂?s†àÿ{Æ7Ú∫&|«¢®Y¢¬∂°3=Á}
–˝x˘≠‡í6WÉNíˇ˚j5/€°cÕ"(Æ‡¨*îwì à7«3|ﬂQ≠’2Ã˘ä.À5¥∏)Y{G˛h§;|•Dë¬â±
º+¿Úõ√>sB˛Vü‹+y_!HÔê\eB≥õé˘9tKúaˇY≥hvBZàDﬂ—“ügARÊM%x &mê¶ƒUOïp—Ê´TÆ√ÇØÙÂQ^^˚$°Ñ¨ô„A◊Ÿ©É*@†¡î«√,+. √¶RÙb\/rÈJX’œ¸≤}_ka:«ji ë„©Âñ3‰UBpˇ?
1M[µ®íΩƒ£ÙFíBçå´&^ÿiß¬≤äæ8fﬂ9∑º∑!ec·ª‡©∞Ñ©‡¬≈¸∑Â©W8A
™ÃØÇß†‚¸ƒ√áx˝–l!1/Q{eÎ)ˆ/xLﬁÄ°È÷uféK√ò⁄GßÕ¿v˘‘{‹⁄.
Y?ûÕ0Ü›©¡öoΩÈyd—û’KÖ∂¡
Dñ^îçTD£c,˜A◊ƒ“$ç^Õ îèQ∆é,ã*Q®®9åÉ–¨Ù˘Z’·Ï%zºö∫{0‘SwlëÆ~¬Êƒb/…rÅ‡ìtKT◊gºÙ∏§8–R$@Ò†Ú¨Ã¥mêCÚRmF™VH∑3ãPÖ!©øú‡f ◊ÏΩÁF5òäÚTΩ˛Û†Ê$p#7¸3†&	´Øh∏¨yw=∆´•°ßj8°≠Dí`^∫÷-dÕˆ*ÕsÂ[Çª|_˛∞7'¡q8e$yµ?&s"õ∞ó7¬a!skî<_ï»&7∏UÒŒ;¢¶à˙ßB–∑3ògF5≥<Ç"f‡–Üç∂‡Î—÷§€\B2A(I«p{lxUû"9¯£É≥„∑'lsG·”O—⁄5v–£öÃA∆ÄpN¢óô«.∆∫™ãÄ•ÍEná°GΩ«æ¿a››aœk‰íΩÄçgAJ≠£·XRQ‰ßÁ•)⁄5çÅ„≈ı≠º≈M#Äã _™πµ]ûö;TQ9µ£T¿≈∞Í˙êÜ+h£Eq§‹2 ãØƒ˙“¨˚tW°\Fu∞”†J…’OdÍ Œú¿öå›`1rµ@¿yÌe/MYs”Àüg0Gy§Á÷∞ŒìÈïÿ∆ƒ ÏcíY‡S‹a@Ω6Îj Î“NƒCô‹Q∏gÓ∞¿ôZxÒòçÖª„÷6Ÿ2®˘ıÍ"‡–Úæ`,iuÚã:Y"Uà/º‘JÊµZæ‚;A)vtÂ<∞Ê„vúΩU(ß&˚ÒÕﬁWILŒÄO¬˙¿∑Ä˜í\e(a¸œùËY<ù˛€±ÇfÎzAaÙ¶Ñß⁄ $ègôß
4óê†3k{
Ù&3Ÿoãï›*l’V¬VëfÑﬂﬁHæURÖ˜ùÕ˙≥S≈¨UH¯™® ¡*…·´˝Å0íuÄ⁄S6¿Hﬂê&§VÍk?	ÒK^e–)˚¥@¸H[JtgfNS»rÕÒüùÚë|¯ˆã†Ë˜m>z˝ù TvtT&≤ŒCEéõ.›…LÊtπÀN\º¬ÔÖN¥J¢sËbÿ¬A∫¢ÉcùyÈÿk˙ˇ7#'êØÃsI∏h»NÚ*É_Ÿß≤CÉ√®bï«Ö`˝´0\Ô¯L	}æ-™sS=\wáâ…ãå©Ñ“ÌÌ…Îì“MZ.ÑÒ†]CﬂV‚-Tˇ]K `nËâsøñYY=Z∂8ßî¶◊»Róòº÷!]Q·K/ùEágN`”0ÍÏÁ:H_\±˙∂_I#µÇ»$yE‚ó'·1©4…E$„X;¶|≠ë K∞Ï`OZ„Hy∏îÎµ—–W/?Ù∏9ƒjõzÈpµ6HÿrS"uú“8)¡Ùö^Q+„ÿ˛âZ(4πDﬁØñtÂ	˜z}◊…aüØ€w1Ñaƒ¢UE∏ìøpíò¬3:ìU
ælûòáıÛƒ§õπµ7œQ”6/õ,Fzgãàha4)â∆®›5ÑÇ‚©+wIù€L1£À∏Ç„B+Ú[NÏÇ›$°Á—_Cç4ñœ¢Oöì^JSá„‘øΩ‰j˙≥NBL‚cx…sa|ƒcJ& eå!ôp4ÆYúÍADA‡õ≈ëcœµx>TŒ4˝ä»∏ê∏ mæ$~	w%]7±<ÒüL∞#%L 3_ LÕÜ≤Æ}›01Z·m-fÆKgå≈Xñ˙3î˜ºÈÙél7 Õeƒ¸<÷Y#≥º/CeWõﬁ¶Iñ†Ñ“ ÌôÎÃ#BWC3ÉZzh:ﬁ–ù.öÚ®>¯dF&;
ˇ_»œ¥¿Íˇ=24ôE≠ÜaÃ=ÈX∆Õ±‚x÷-†¶P^B„ªh^&öK+≈T2œ˙£ñ˙Æ\87+ﬁ™Hæ2Aº\b‘9∂, :÷·ª Y„Ûµ2}£ÎÒô|^J‰:Â}Xﬂ`Ò ÔÒn˘,ˆ¨U$Õ∏—}{‚`˛9í±¨<∏Ωå<òò≥π$ä7êìôÑ@;úÊÊ:ªRÜõ—Â:sÌÀíC‘1) ÜÇä:jx`±VgfÕõ.∂‚C'ÃÓ±kR¢Œo—˛ºl5ZùœæÎ5ë„+ã”∫R	sÅ•/\ÓXZ*õ íD¶J$∫MÒ∆înîC% [ãÏ¬P•l
”À«_[Å|öÎ âÃ™⁄˛bûrù¨}Îãc+'qíc’æº^(CÕcª&˝¸ª+…‚öÑpå@&?4UÒJ∏BmçπèâRxps1âv¿5Hi|GMÚZ∂˜ÖåzRÌz.gU;∑09Ã÷ÖjP∑0Îl;X—ØˆG≠ÖI·{˝˚o{ßáÏÙË_G'ÔèxF∏ÉWÏ‡EÔÙ,õÚmπû“ÑTVÚ8øgódæyTEõªãø≤äô 0ö°-rP•ib˚∫Ä'M—ê'µí‡ÛÃ÷Ö¨úa¨‘ø9ƒòï–dá~77öˇcˇ˛‡∫ı°ΩÒøv◊∑r?64ùÀ∂t»#g&«Oê9ˆ¢§“áÓ«÷:+>ﬁ˙àÈM∫∫W˜?ñÁSfx¯Êﬂöß%<(ô!¥µ¯°é7?|lò!æÍVÃPã/∫8≤u7•6¿,w8‡∆%·ßfˆy˛J,7Ü≠Ÿ™∆tVﬂ,Õ£f£E´4}õ¬`¶f´£]nååÈè∏«ß∫±g;#8·ÌFI∂AÓ@Seô˛*™P ≥¿ø“%+®®Zë≤D7f±åZWßÓ®uuó6! BÉ.÷®;‰bÕ%,pp°!ÎÍ‘¥ÆÆaÿö˝ÂÜáV0Q7e #6î¬º#ˇdπ^ÿl†ÜÖGÂN  /b'‚¶ÏEd…azå.‚˘ÃÅj¥—®	mth‡◊¸©èƒêû≤∆√ÅΩÌtÈéÍá?mo?¸Y[˚<pÌbÌ‡|`5∑∂∑◊Y˙œfgÛ¸BoAˇÎl>li€bªÔ@&â"Îá∆£ÌüùM 9Û√˝G{Ùàæv7??Í“WÎ—ˆˆË'˙Í<˙˘g˙:˙˘ß˚›áÙı·˝áGºl˜¡‡ëı∞Ò±∏˘•nX¶≥{s|Ú¸˘˚ﬁ	k˛ƒÄQ:fgGßΩW/éO[é)AGÂ––&±ÀØ§0ˇ°Ò∆≈lAçæ#˛P÷ùSûZÁï5√?/c˙”ááã(©WØ1ln>C≠Æ •ô,/»≥•>8≈r»üÒÊ∏∆+õ#I~0ª\ìRﬂBÅáè·œ,»‹vª<iO≤ØaÁÍ8`XYo†u&›S~Ûaó·tºõ+L‰òtïmª¨ﬁ¨PO†¨íjIËñ‘¢h»”[à∑Å>‰[öı1Ωô≥£ÎÒu∆Ó±ÓµA^Tqß3è√qì:4çâÿrÎ™œ≤…í’O¬dI%ïT{aRŸj}Wt)ñΩ K$züÚI¢ÅªwESŸe%öxï}+è^Õ≤Ø"·;™J•$ sè+ÍÚJúES,ô≤ƒ©ªò/Ô∑¥8ﬂ•˜ˆƒuJ— kÈ2.–‡U´S3°§∑-—0◊»¸°?\‘b*30)´4ËZïŒq]rÂ$Öv„ÙD⁄fcÀ6)Kkä@∂ËY3ÈjΩÅêCF≥+–$ßó<›w*∞ê6p∏ìŸ⁄Êe´°AùÂM'Õ√@Qûâ5OÁ≠í±gÁ†bquù4q)±íïÿ~∏Œ∫è~>„¡#‰'~⁄Æ3 Ó& J¯âöı~v˙ùÌ∫5NED˙á’∆>Hg˚Ö… 1ñ÷ø˛h&0Êû˝9]∑W!UzY∏C©èÀß2Êÿ^8wÜ—)⁄·…ÓÀÎÕßÒπ[=¸Lùs@@()ÉÍâ Ê*?ëÔO#w^ß¸ Ωô"÷W2>æM§B¢9È—h?B·—P¸±∫(€Ï÷Eπ.ﬂ|Ù‰?ı‚ÙVó*/Q±F!N°¥/Î.	J+à,Cæ£R·•∫‡'r˘˙ÀŸiù“U„õ–˝lénó]◊ÅOu∑WuÁ6p`”Ù¢ˇ€	¸:õS~Ví⁄∏U ]ÌöE◊Ø+wØ≤ø‡©≥ËNY‚beû”|3Q5ã7…ÿâœ0˘z≥€¢Îÿœë!yhÌÓÙ}•mÚéÇ¡ÇâV©—oéN¨ÄeV”£ÎÁOùé~–lêÏ0z∂“∂”z\»©Sl	E¡˛˚◊Ω‘t∑¯wv÷{Ò˛ÑÌùæÏ¡OΩæ £6+®X™2xiëÆ‡ôCJÇ7%ìÎÕû®◊Â*èˇ·I|œcÆ\ò„ü∑JÈ{‚¡?áNh÷*àÒ%jÖt 5˝2¿Ω  …¥ª’¬ºe”¶N
™´rêy!‰x≤„*â≈U1™25≈RÎ•’öí™ònu^aRI‰%⁄rAyF⁄Ï~Fâî´(bf}?Ã>..Ë*≠$ÈJDÂ‚Æ◊âÑRï≠)HãI¢ù	âvfîhë]ìòT:a·T`¨V∂7r„ﬁ@∫5›)®‚≠(SCæE£é	∏
uªÆÅ¥òàõŸ/KÀ∏˜Äå˚ÛOÎÏÁ(‚vóq∑∂Ü€€e`œ÷"ÓV…z]Æ3r@ÇD'¯ÕŒ˝MS`Pç0\{6ºÅ,(FÙY¨Ú""?UìˇÉöÂ_†ÿü®	æK˘…Áªîˇ] ˇ.Â”Áªî_⁄Îw)øÙÛwñÚ%+º§òø√ûüˆûøB´…Wløw⁄;yŒöØﬁæy˜∂‹?Ü5:{˚é^Í%}≈DÀ,Áá2
EíÈ[ó<<-”%Àô7÷j¸~≠~£$ÿöAÙM∫M=◊´D^Ë]¬‘c$»ÙW•Y≥’bOã—Ñ\ªÀÓ…—ea/µ‰9Œ…∆p·¨Ä‚|ˇà£+<£ìˆ¥ÆoEH•⁄bZ°¡Ã°(5%oüÅÍLú´PáA?Auùâ®K˚4_ªPÙ•;±X≤ œZ¨ÀÄàYËÿÓƒç÷YdÕÊÓìVaHA ¨(ÇD˛ú=“J¥ Ñ§3Œ.(ZÈcƒã'fù,\“öâWÃ#Ó¬"íŸ£7oÛ∞&é&±äVµx§5íØàRØ§Ïå?4∏ÉQœñ°Lt˙∂‚ú>ljäoË√JÖÜ.¢Iô
eFhTfPç»õ¶{∆ƒì“iÖ%ÚV¯¯©°∆‡Åˆ˜T3◊%TzCUÅA%æÍıºÇ)∑†ª»Em‚‘◊\(ÿ∏ÑﬁB›3∏õø¶ﬁn™eﬂx»˛ëy$ˆÿGDõ·∞—ZP”±Çûkwy;ÄÔ~Ú˘ª	¯Ià®ùÖ{ïê÷ÌıªòØˇ|ÛüïÉ‰ï¯8◊∞§º˜ ë˜éOﬁΩ?cÔNﬂæÖaxè_˜éYØto˛utr÷;=Ók%æåÕJe>
b[CÊ£rôœ¬·˛ã.hõzéù˝(ﬁ<õ˙æ£PDFvûöN¡• ÖŸgR.î#(9LA84ó5JàT≈$! $DŸùABTjE	Qô–M$ƒ,\V%!f!V—Í2W_Ä‘¿©TÄÃ§æ ôùÚb‰ºRÄ‘á–6âês!BŒç"dt;ë"©È’^ÅÀÄ—{Yá∆%‰Gìªó*Aä25dH€èœ«^≠HêT0Ê…4ˆ¸bóﬂ
>.!B™ªÊÎäêä#\◊Ÿ˙˘˛Ä;¬’æ6Œﬁ†◊¥¯~;—Ü¯.¶üD2¨≈ø¶ˆ∆s©±R‘âÿC_„zk‡_JùAºêü"˜Ω*°ÓØ)`ì•ï™πÔújoıóΩL∆ë¢4n˛√Ï&≤ÈJû´5‰´Ù[ÚıF±Lˆ{ßoèXˇ†wrr|Ú§≤Ω”√_zßG ìùÅ‡F±*W“dlyˆ‘ÒπùËùc7ƒ£u˙q0t–4ú˛–B~ßQÜ≥%2¶.à"iƒ—6ZB` .9ëqTıÅTZ2 ÖØ∫f∞D6\Ò1⁄¢{åõ¬vŸ√Ùˆ˘1Õvp"ùL;Ü⁄dçõjÛ0ˆgﬁÏ>ÿ‰Nﬂ?Á˘9Ë¨€N
¡@„•ê„àÆ…Ñ[1kÊ”;¥÷Eº’¿"‡!~≈è¶e¨m∂¨.vEæ5}0îl.üAøbÈä;å„ïF‹w†ÿ∏)◊Ÿhªi Â3Ç≥P¿ù
/zí∂Pfì?¥Q¬±—hôh2ÙØˆ◊lu∆Æ≠5·¶âL˝–9ÄÕX¢ñÆdëRI‘œñ „—ñèÜq p¶WÑQô¥ÑZkÃ≈VOÕà%àbùT,¥TÇSŸÜtêÕñ®∆'E•Ñ†òÙ;¿Öö%Áé?ú¢åÍå@"Yªê KjF  ÒCP1:W/Ä≠ëCÿ≤'‘7(Põö'iü⁄∆oã6\hU∑≠p-‘…Ôua ∫û¯ë;ráÒ*ü$˝cP*íîâÖŒ¿:±iMTz†π\ØuÿêS¨¿£ﬁeÇô[º8…/w:ùOÎƒà∂fæp·à.MR6C¥e3!ÚcW˙Ÿ‘ãlÜüBZóîFÈä_Ø3 ÛZÜ¢,\&?¡ 0’õ
„æôÿ≥dL™€APõ⁄°”íè–Ôˇ⁄©4(+ÄUT¡8BåN—›2È*xÑ…¡DOå˝îÛ ºA9är∆Vîù)9eÖØKg≥ƒí{E‡◊ıà<÷úŸ.ﬂS∏K‰∂ …ÓÛ.—®ª`”èÄ≥≈√/¶ÙõŒ	Dıu~>17˙~Áü‘;ÅÔwÿ;¥ig˛àı-O2 +€‡—S[Ï Íà¢Êò]Ë„UF®=™st›‚±µ⁄#+\!∆–,ÕJÚú9∂/)Çfa4kçcœv.õCä<T√kFG›àd®«cµ€;PΩ›5F˜ëC˙†‘˘»£Ô±Ó¢«F2A
Éb¶ÇÈdwîπô%jy]"Ks”òÎ¬\vX◊¸ûÆd√Ù£æP^x¢'©d2ãD0Ω˛»Óuel†◊®”Õ>¶c∑Rrk Æ¥√Y≤Âsƒ5¿∞ÛûM∑L∆Îß$6)f"Æj;∞!ïüKX>höõú⁄˚„¶9∞kñFöp∏Ù@‹—úà∫J˘L€•ÖtØFßYË(û⁄=	$<π¡¨Ï‡ç‡Ãù@„ ≤œ]Ëï˙†‚ÏÌ¸è˜?^onMÄÌÌy∂í’πãŸ¸ºÙ¥VNif#ØÎ%⁄◊sü~2är¨¶µ–dE¸≈ç∆b:%¸i=Â‘kké∑ûÏ›„á$ßdR 
Øºa™!¬ÂéS≠ÓáÔÖΩ‚iù*Î n‰ﬂX&˜Á1FLÊ]:_‹°S‡Îx—ñ" 6¥ceîL ÏsÛ2t§ùg+x"1*™äÔVZZNÉøÀînÏÚÀÓ÷∞∑∂ˆ‰`¨Ö! h{L®?•v7x¡'9#˝%¢◊∆Á›pXz÷˜‹ä¸†3slWÇ£Rv9ÜÖH¸,ôò¥*⁄Gøxÿ–Mœe8i\‰b0l#wQ˙7Q8µ¡z∆…u@¯ä„¸°3≤‚iƒöø8`ò[& èn…S<≥–§A\J¡ 2ì†´>{ùπ%RM6,û-î¸p0#óq‡)Ïùá«ã¡‘á”ÿÜUj‡EÖ¨Yx[ı∑O1ç¬æ3*ÁùÛ ñ¨yËÃ-oC •a
´'Æ#>Â†õåÜ/˛±}≠À;¿#]'◊%üêŸt∏°YfÔÔŒu¸¿ø⁄ K†ã	K¥ƒ6OF=A£8ô8c˛Æ¶ÑT~Ä.©Vá–°˙$ﬂŒm—πÑ∏ì¥¬õ{*aÕµ|;ö@≤’Û≠9ŸJêâ8∂Ö˘+Rc·IgF˚πûl¸/,™=®ë“€˙7¿[h$"E‹•vX2ßßha,~‡u™si#Â˝5¸áœG÷Xô7ƒ˝Œ†ç Ôã¯N¢ÅEã‹Ω€…xÛs√◊9)“h´ÄÈ˚ÿ|÷T@°!àNπ›√d˚2V…Ñ∆Àú–ıxÙÃ‘k≠¯®yXEw∫r˝{Äê_Pßç≠À±‡™ûáﬂº!Éú_A≈‰Â%&i6û[Á®!ùÑpsˆÄÆ.É|nÛR·ÿƒg‚≤’Ø¿ªCÅ˘Èjhkﬁd°óXMˆî3]b˘ÒSgÒSw-ÒSs=Òì¨ÈH@˘Hø∂4‘©DÕ∆óY–y≈êsuâÅ[@û~„›AáΩ≥¬»EÒƒ˝,‚}á¸ƒúÅ–ª9fÑ˛¿æ£√Û¢DBp5Ñ3ü£ EZ‘"aÃ≠ù$π"î#@£å¶÷ áE_–ËqÆ’Ìëµ,.{’‹MÁjŸπRrfõ∞I{lﬂÙTíV–U	“f∏≠°˘tCŒÛéhøÊp¿p¯·IÆ7˚c`Y2s÷{wÏ™jß‡√¶qC∆ÛπDé]J#WºÅ7§‹Qá[U6óÄ_3+JE¶\Î≠æÀ3^∂"d∂®)l-sÌA=Ó»/ä≈1˘r<;ÏC„∑‡W∫ãÖ√À±º_ª˜Â7ä$èØ~Ìn•ﬂÔS¸¯x>¸’í_ú∆G#ôUá˚⁄˜—<ü„k’˝å»acX˚≤Zú8√Ü±Ø˙≈ €“TÁEÔ_Gøùº}ˇ¸≈Ø°Æ ò ºÆ≈9
ıJ∫»rç:¸WÛ)Â<Dù¸^√h\?2aW#€˘∞˘≠º˛U?‚• $3õ˙∂gzÎ n‘≤—ky˙‘µ*ª±≠X¬¡Tıµ¡ÈOÛHYî¿˘-1™Áπ3“cYn¶;bÒòn&…z©~åÍ	3®äW[	œG‚@V»√–úSMRcœH	£ ˙È´]ÂŸ„ÏœÖÍØ jØ€agΩ7˚Ω“ójøw˙Œ™£◊Ôﬂ`Vç√ﬁ≥≥^.≠∆≠ˆj*âsÎ°©ï?†j‹ΩBIñ;KöIƒ‡çòˆ[mî√kåôæ©_Ê2¯ô4"÷g?Ú &:à<Ã÷®ΩÆ!ï?oB´É‚Ø≤J®æ;√D‘ä	óF))k“eè4ŒîI&–ÌÇéJÀÚ—Õﬂ’€Da˘™◊?>˝ı†wvÙ¸ÌÈÒQüîñ‰˜ÑaP * >®Ÿ∫^cI“lëkﬂÇìÖΩA’#¸ΩÀ^9 á«„$ÿ~µì\i‰ÿ§öl\kTâ‘z™HÃåOU!f^îƒ≈Ñ<s£©#3;&^=£∫pÀ€‹˜Ì´\≤»bZ5É5	}\Ùkªﬁ»«Ïæ[l~…ìºﬂg<À´H^L—nHöOä-∑án0ú:mås»3´è¬ˆ6O™osg"wÀ&„8åC{Q<…^çÌbí)Ô¸…ÄÁî›PÔ v7ƒKvÖÔIÜbÂÆ√Ω˙DıúıŸ:;è=DnR∂k±q‡åˆ÷>[ kwÌ|Ò]%µñg{Ízûñ÷vÜ~@ÁC”¥®ˆQ2∞l¬ZkOÀµ›ÎI«êå‹ÙXYY\>”ÚpUøÃ£ nÛ'˘¨Œh"&q1ì≥ûOı Ä?ädÙª‘ä	%àä£ÁØæ∆\s¡sC¥µÃh69ßL˝A9“◊–zËå˝) soΩœ¸Ò{Âœ]÷$a[€õ≥©f⁄_	`Ø§}Z9DÑŒZÇA÷ÕÇBRæ@ ëboç≥Œ¸N]PY°Ûè∆n(¨ÌJ˘¶$S•–zfñö±"ÀŸPí˚öÀÕf–	ªÌ˘∏m2KOA"'+°ƒ®d¿Ì=ó[˛¿ø`Á–[(≈á0ƒá%@≠ç2<ZÛÀò`ÙºÈ>+,ÉœP;YÍØˆv«-≈ÈÀ≠ fÄîﬁ∑ƒóÒèD;{ FW∑KÏoqP‡∏ø)C-Ä4mŸ`ˇ<eÕ∑så:lM[ÂtMØç©Û≤e6ì1ÄÂéáWN‰NÑã>HÖÃÛg~êX°(—º˛!%†î$¿√]zÃM	 Ê£O».3±À\ıb¬ºpé+»©zÅM ±T¬ˆVIÆéÖÀ”¶óÌJŒË©[E›^ÇIRlÙså‘:›„H~È
Y*¸NVM∞kÄykÃÛabœµ–<w/À√x0s£∑™‘Ö©Å|=Ûxæ¯Zá2¸†	Fr¸T4(O@Ÿ®^PM[CÈÂµ¨¸ï•Õ™éüE˚ÊßonZ©1f´A≤nî—%y`á“–©#˚5≈†LΩ‘∏ï5úÍ4õ:÷”#T¨<iõèí@≥‚˛åxf!Ü\Xü›l7tµ±∏‚w‰†Éhc√öª%°aä}0s¢±ocD˝∑˝3ÉÈÏÿ±Ä¯°ÎpÉÇ3xQ˚ËLjYÛ˘TXGn|}ØaÖ≈$∫;ÏeˇÌI'$ªUwt’¸›
øpT¸*"A¿P…8¡ƒuéî|≤.°~]t"“› êõ§6ôﬁ∂ŸƒÌÍ^ÍMAÖ#’‹s©“⁄ô¢Ö&…›¬É(±—ƒ9‹—;F‰‘ÅŸëÎlkÎªJ5@eé;‰l”bûﬁAÂzùmm¥U7RJnuíxæÔYS(&˚Ô{/V´íDZ+‹˙B_#dÁ4>	øV’k·TA˚ô/Cë»SE√®¢pæ‰Çrâ©ñÛ¨îﬁ‚IÆ±lØ‹:QquL¶®WÈÁh…a)Ö  ¥†X∫ûπ˝FÂÜ¡œ˚–äX-º(Í´√≈µßƒÀ…XTwäÜ4@\Ö¶≈›—E{ ºk}Íbû=ãmõ¥Å©“Vx%1üÍ*j√é´∑—‰˛=≠k÷$8ˆ-+€›aIE$¬◊,Ñaˇe©óÖ$ıﬂˇöcO\∑æÇ ñ_Oî˘◊=ÆD©≈¥∏)÷‘Á¸À∂˛E€ı⁄¿É{ı∫oê˝á›üUÍJ=.¿Ç+¿â	‹‡˛∏ìTì+µWmmáƒìxŒ%=…ÏëÏ7wQÙ‡,Ÿ:„≤Ê?£+6≥B|0DÈ˜rÖ∫ıñen‹˛°3së¨=Èvÿ;p,W¬ﬂ—LÚ¯µå4éx±µ∏HûçŒ∞úTé‹_ò	…Á™<nº›…ûx—„{4D)πg„?‘S|˛qr∫\”»Î©ÚX«;,©DŒ25°ò∑dû∞áÀÀ±ı–ÒÙê˚ëÃmJ⁄Œnßrùne‰¬]•∂ŒQãj≠Kwv∏áNdπ”Ä{ªôÔøîM7o?‡˚\•µÈûÁaŒƒü¬Û∂?∑Ün‘|õQX7«r>8ÁÏ¸MﬂónÊ,N∆Ûaõ»$Ï.É)≈< ä¿G'OŸ¯OeT=?_ÖàÒ÷¶FlÌ…~rˇ&Ñc"‚∂K‘π”©}Æ#Íâ “·D5IËEõ
Ω'è8/»ïs'<åﬂg÷<)xQ„Òß±åVäî	•LöŸüR’=Ù]Ú±Hü9(‰-Óê•Á≠we’∞—≈Oí_Ñ{BV´jƒ.˙'’h¥û÷QÿxŒ≈ã¥L¢°hR{(,g"Mc†àö»⁄ı&c7HπmC[ƒµ‹Ä1IP€C]Nƒ√ØYuïﬂ ﬂÇ´˜ôﬂz^	YZYòπ´ç∆©	‰ß“@ÈÉA5e,è!æÀW“µÕµë≤T’ØÎ_ﬁ "ÓÚ¬^Ós¡êÇÊg1€\z ù®ÀG*)`”6fP~= Ihíµ¨^=Æ‰Òû®∆˜)´}7R≥¿‰¬jÀ¨8¨°9,(1r∞÷À∞V0ªúk\C9÷Yw;§∆Ïé_o$©yﬂÚaÂTaK-jœ¢ß†≠¢á•∫´í¯rˇåù‡JØ∑˙_°µ™Z4ü0D„ﬂõÆy¸(
Y§=∞ªI˛Ü:cW]¿ô‚†ø©p1bˆ7√…Í~s{‹ÇÆñq5™Z„¢Û¡◊‹õ•®≥ŒL¡,}ÎE¥»«£XhwÂîœyˇ.‹∆∑ï”˚6vC](!ßI…^#oN5s3ãô„¸¶®X∫˛∆¶8Î<(Óõ\CP°÷∆&¥ÓgR|ØøüÛÇø÷-i◊‡tF!ûƒÑKÀÎWhÒc“ s'Eú—˝~éC`âÆH=á|˘¿â.«´oŸ+ª.ËπúK¯…]!⁄Vü´∂æ\PTÎ%–∏Æ“ ËUu·,£±„™Ü¨ÜÆƒy°ˆÓ“§8æãJæ˘45DEcÓ•Ù}ö∞5"»%¥rïòˆ]ôıRf-áWÇÑ
“+lu0+©!ä
È—IaM∏æ®[VÑÁ5ŸÀÙpO©_ºß¨Ö…≥S8_0Êù›í=õ#fpWbMs∑7À.N‘> ∏H§ÚB¶ß®ÑjÏ:†D4∞ß“Mtu7∞¥xEMíd2ë-ÌTQ¬'L√SˆI?æœ4 —-RVÿ›
E5^:°…‚n<Â–o+õ<∑¡Võh|nøµﬁ›oó¨Y!P–Ö˙V®¯õÕ4%Ì.$Ú&Ωî2§ÌÍ÷kR›KÜÌáúô@∂"weIÛqaM`){ó+C™P©Ï¶ÀpCCÔ∆A2Ë<>)áÕÏä∂ñæâ“ªYI$äM∫’aâµÆ¸ú'˜≤æ∆Öæ -\Ì´Ÿﬁ˘*U∆Û’≥KÅE(≤ˆD0,8'ûÌˆ^π©w“ç…H>£˜ØÎÙë#¡ˆh]êYï¸uqzFÄ†:sΩΩµÆrMœAPïÇÊÅ5bH>Tjp∂Ù∫≥˛¶æÖRºE0≈Ê1%∑ëï,”RÆ!Í	¿≠Çë∏^Ç•õ=√ánÈG8ûcπ +∫R—®‘å˛ï5ù ÷Ö.O\[IôsúhzH	À"ÃôT∫œf‘kùÛ;C∞Ô±‚ÜûJ⁄Ëd_ªñy^‚+_„q˛¬µ∂Êo—U-ûº•‘ÎL®”EΩ,œãò„¬≠+∑™¨+ç§-≥AE}˜∑B]∆ëØ∏ã8´5‚î-öubëê‰¨ïõÿ|Ö§µ¨‘ê⁄•P Vê?ÖmiˇSÿçLÆÔwÿ—·qíQC”¨:Êfq…-pÌz¶˛ﬂåØn‡1qg∑⁄¿˚ëÖ$›ltÅíÄ®rÜ†XãDH0måPáBŸë=eM!HJ…√‘€»§X8É2öe¬,‰ÄøJ´]%Kù∆∑k£ªbg¸Ø‚io\b.˙~w≤ˇK9Ÿß6C1‹˘^Ïÿ<RJc9S2s]ÌΩˇgw‹ˇºÚ≈*–)l∏˛HO|≤I˚˙ˆ
∑ T˛ ◊˙o◊ïﬁHˇuÎ7iZø‘€≠:õ;'´Ù7áˆ8üπosŸV_Û≈˙]ôß˘Çf¿:õCÎÚk≈˛Ê–2Ï∏e›Õç˛©uMÆ‚RÜ‹ëõb]1ÖÂ¯µ^0d´b•÷SãT·Ø„Zæêª∑9ÿ≤|]‰H◊c∑p7è-Òùs ≤W7Bq`˚¬ö«°‡Å5<˙jùcA5Ω–ï5qΩ4…–˘òŒËñg£ªéÃæâ;É–Ï©¶ëˇ™Í—¯ûò–Nçxæ\
'ÊG,˙=¡Ô√£◊GgGS@·RC·úÆÌú„IÑg/é^Ω}∆Ó≤≥GßozØŸÈ—¡—Òª≥®m4Iî2˘µ1'e 7†Pz˚µ@âM\§;˙lVd%u†~Iá>ΩEbPáu:<Î™Ÿs95∑ìˆt⁄”yÓ£«zZcK6Ì%ËÈ˜^ÙXø˜Ô^„Ê:û¢Ì,∞º–öÑÆ.\cEæ≥LYäIé"%C=T‰≥µ'≤PMƒìµoCπV∞Ä
(îobÒ%92e‰ãÍﬂèXÆÅ[÷€*ÕxòÕ˜îı¥y¸•˜YDèƒoË3åaå|¿ÎsùûLh¢cœrÎ2,~xÜ≈Îqëuy‰YÉ)©˜J€¥.	·(˝RΩÇ
t -Ô»¥	ÜA¿Œû√ßÑ<|ìlâQZI¯ï°ÿ£eæCà5;)˝4ó$,ÿ·JJ%+∫£|/-
<”€Ïtª⁄Ru=mí≈ƒÙd
’¶µ’ín™Urp‡Gœhä±;uBg$ôCö
R(Û£±Ã%j∑n	©¬OŸ]Ä!JáZ≠ˆU Åµ8Øø°?8p˙xl‚ìºÚ√ÔµFgiiX±	˝§l7ƒ§3¿˘ı‚»á4˙‘:Ó<“ç¢í?ZíŒŸzC˝µQ<ê∑z)≈‡ûùfDÉëxò¬Ê@X¸»ò∑µŒÚn>ÚÇ(ﬁ◊ û≈ÆaÎˆf~ÏEt5É√x ﬁX—∏CFÕd≤?“ﬁ∆ªÌÂjrœ)ÀﬂK[Œ·Í∏.Ÿ¬eÈà-m%KXëÔñáw…GuQ¬≥Ôvp
cËW«rxÏ∆ÓpíD…‡·XÓ \£L0(ã∑€Â∫=4µ≠p<—6¿KÜ¢A%gN"Ï/JÏökh¢•‚za„{Ui€›b¯gf∑Z÷“à€¨hL∏Õhi≥‘¥…&€ˆòSÏ“Ñ ®Ω©≈ìô√ÎÁ±◊⁄)üN-ãßÖ®IM’"R©⁄¶ÉJ⁄eÄï∑5“Z≥rh‘1≠ÂÄ’´s˝p_˙eÓ /åEZ°'¡=≈Æ°†8Gàã"NÍƒπ¬@?Ú"2ÍóÎïs’tæ ‚ñ›%3∫ôbò3˙‘µÙ‚–hÜyl"ŒÀ∑ßŒ( 8dîkàìÅVÿ˘-fòn”ƒ‡Ï‹öÁ6˚ﬂZ–cÌ.ÜkæπrΩ œ—ˆÈxﬁTCriã/=©!x•ã7.®§ÎK‹÷#´oÚÄ8b≈∏8’7eÍe∂/L+,Í]hW`ó&íO&‰Tù∞<¯ëßº yòÕÒ+Æª∏¿îkVÍH±ÿö›ÏS.¥≤púƒÜyupπØãWNuá$¢
:cŸ∂¥˛9ÛÄ}◊”ô "ˇ9ï HÎ˙õ‹ÂÈ	é±+©FÑ•⁄B∆líƒ.l_º˜’‡3≥.€c›Kvÿ÷÷Ê¸Ú1Ûø8¡h
G’’≥@B{lZkKf`åQFz1ÒEùπ∂=•¡ö‚ ÛQÀRA¢†∆yçü†X∆ˆπÜlw‘©%¡r·⁄—xá˝Ñ €≠fOÑãE›‚}!/-;ƒ|à’’°D	‰∞~)Ïw#T_ï=óÈ0ã íüÓÅ ÖK∫ŸHê›[€N»íÍ÷9øí~û%Ã,Ì‹K¥AøØ∫]ä=\Æ:ÌÏ` ‹¸˝πH≤#YœBÃ∆Çì#@–~B@Œ[ö¢§†í©[õCÔ#/À"•H®ï¨T≈!Xg¯uı–%ëzü¸;—˛1™‹ô®P—Q˙π–:"µXÀ	;•ÖÛÀvqi3s(†£íaÿÍ“˜÷·zùµªp>¥ÎqÁ…êT1Mä∞ÿoÍãÊ¢:®ñ3„Wö'NÛ^˝iVHºHåIµ5 ìruM≠≥n.“%E¢π©Wv«~d|5Ã{ÔòÿπbYN§|˝D*òt…gÚ≥¿ü≥¢¨ôÜoâ+Ka^,e˘‡ÕßÇ∆;ùù”4◊ MöSDyâ‹√Çº˜ÃæÀﬁYW®e"&∆M‘jãsS+ıº4∞ ı∫êEH9%S¡xŸ©Ùs’ê+u´Hïn…˛0wPS"X=|T75ígP^%krô©P≠Ìreﬂ…Ùh‡_&‚îT∏+\èﬁ˙wa©¨XnÎoÚÜU∫+çÿ»«["<¢tóıìwÔN0é≥ıŸö∞f∑˚èf√U—$4`ISK.
Véa©+6∫[∑ÚÌÓ·∫€óúàìç«)⁄;g6∞(_—Ç˚7.Yß6œ |’€πN,r°ZX◊µp©7yè¨?6∞CX(å$pS3ya(Q◊≠A]ÍÏÚ‰n∫∏ü‚*+5|ÖH1u≠ƒ?úó2ëâ¥1∑Ód∏K 9sØê4¥<ò˜¸ ƒ\ﬂõ^Ix√Æ∏yÆªº$º∞ßÚNπk’Ãz”B/õÎÂ∑Íd’öª£Ø±»ZÕˇ.û◊'eOi£â±G/:Áä[Ü”=ê÷.úúÙ˜ê{Ñk#≤jÆmâß´Ø5pW~ûIûÉ^âwıÖ.·›4s∏÷j€˙RÙ¿∆è>Â ^g∫ï∑•œcOã!ô§Â∫8}n	⁄î¬;+ΩÇ¥é¶Àë•»ò(W>@¡èêe †à‚µYÑó¶ÆA≥ï*vB¥s¯Xª∆…‚ß&CXæ£t4CãëÀŸVzÿOÇø«mHf÷ess]t◊f:*&j÷!Q…‘t{c"»Rq9¯õ'Å‚£≈v2X#¥nÆÙ∂C¸¨|ûô;4ΩQ3†_à%=˙VPïyL·@©úŒ)'ÈNıFëΩ+¯–’P+ﬁº!Ó‰-á0]&~©sÈkÔù€ó€.Ï‚∂ÁesËbG’®&æ/W c ´/™⁄Ì®ﬁÓmÔ§‘I¸—ËlL⁄ÁL{ùyéKlQ”ÈÓ(≥´Kûó^$f<Lcˇ©å*œõ¶⁄¢:Ôã‚ÖoÖ1ºæ≤ﬁê0£©#5æ¡Ω°Ù\®w˝qŒ!ÔNèOŒéOû≥ªâ;»È—…·—)>[a<èAÏNma¯IQ^I^\È<Ö≤æ:ãúKÍuﬂœÁ˙êÊºãk≈–<¥ŒøBûsÅ¶πE>ÓøáCÕ˙π˘6ûl›á≠N‡êıR≥q÷ &âÈI≠+¨ì°˛’Hﬂ§-Ëù;P65æ÷G¥I,Q›R+T*ıB¬*ò•ÌRÎá6˘`—
úÿ¥≠4≈:ŸëÈÃGı#6A_°6’,·K*∏íLE¥$òæ‡fûæhüÍR®ìÚ–!˙ñê.®¯&ÒR’€¶À87 ]∆È545\≈s#ì∑…s`` •….má=D;Ä°?ıÉˆ√˝˚˜ç& 4©ÙåËNã]&W,n§_+ª^)—7©=`É9|I∫¯ëÒ˛[ã˜£ìÁçñø2ÊL £Í:®Ñ∏,Szâ -ÈwWö†ûØ9ÚΩ®∫ˇqvXwWé\´D©«àŒË	¢æÒÕŒ6ŸË©≠m√¶ä=€€€èŸñ»ı⁄ë?ﬂa[‘oﬂü∆°À8¡æÀ^91 6ÏÜL∑.`«»A\Æ7≤∫∏íq¸(ÚgrËÂyÏ˛ƒáâü¸´-µtó»ô>%⁄ogœèGH#ÒòÈxË™ ∏´9gV.E“ı/x|%Ì ≤“πVøb?vXÔÕÒ	{ÛÔ>˛œ_ÙN{´3@≠øpïÒ[&\-¢^pÏTáΩI»ª)‡MΩ∞è˙â‘¨VqDP%¢Z˝x¿Ô:Î]|ÖÎª‹]Wuúˆgå∑RtµåWqüTÆŸ<)¶„::sÈåƒ”Ux Áuˆˆ¨˜öÌΩÓùºÏ-ã€æπ]pUœﬁüÙéü≤j_oÍ2VÚ’—õ˝ﬁÎ≈ß$x“õLjÂ§…Ãä0„èÏaÊÄX~J“ÒÇÜ QæÈQ,Ç„uÔ‡¯©@s2Í^Ê$ˇ9›`|4˜i4˚ÖÕ∂;p@"©É¨πa°xb–/¨Õˇ  ˇˇÏΩ[w9“ ¯æø•>mímë"©´eÀ5‘•lY÷Âì‰ˆ©Ò‘©…îòVÚ“y±¨™÷9Û∞3Û<`wœûŸ›oØgˆÏÎ¸äÓ◊ÔólD »DfôIJˆW›]¨nãÃ ‚ÇsC∆æèV}5)Ùìô«›	∫Úˇv˛jg\€aTµ¸Øu¸)†√Oe™ï7…òja=4√Rø%√â	Âæ†F±\n≠∏0éiyï®ÕéûM(µVhñ|r›î9Æk	j™¿'ﬂ‰pû«>=¿∂√≤:»b4ﬂ‰QÈi7ﬂ\VÎπX®äB˛` Uï*Ï†pÛœÇ*‹⁄Ì¥ ∂›®≤-ıÒ…7µâı.{ÏË›…õw'Øz'¨w≤üßL©>Ä˙≈{BÅìºac∞π‰áüÕ1tb•+î1ÈaMµ*Â'ÉçGmdB˘QòõLKMfŒò£ajº=¢ /{U√¯cÓ5%Dı™&º“`ôfÊ¬êÏ=†‡#FF>2Ñ-0ÁY]YaHò≈h⁄ªc@‘◊îM±B·ﬁÙöÇT@ã÷Xˆñ… 8xy]]Ÿı"'úN√Q:G;ŸÊ•ÄK‰h0˘ÿ(5˘0¬1\ﬂõ)⁄~s˜ab»-%K7äÏ îób„uˇtv'õ#ë—laJªâê;ÃRi•M¯'˜öáSø5‹YX∑n}ÿ[•`Ç9r&VjìÅé.ùõ@úƒ0ÈÒ6˘"p¯	&&ãaìª™o¥Éˇî4ä_st$û‹Ã0n/∞,ÜÕ≥j√bºîa’ÉëÎ¯(À	˜@∂F„Ò¸≠Ö¨"q(ÆßEQ2G1\æ„6aö„‚ùÂ›'≤ÛÉ≥”ÛÀV?˛û≈r€?∏8|u“xÃkE¥‰–£Ÿ‘©sth…]˙«°·ú”˛G‰È3éi‘«∞ÊG`®ˆÜOlÛù”«?«CK’z3˙sÏ∏¯ÁM4<zw·ügÜNo0ÓdÌd˙	ˇÏ;AÕ8ÕCéFRàë®"?(6Z3>º¿,mı.@iõœYÑ4@»–†„ìz√ÿÚù#ÇÑ&•øã<Ô{xjö—4"ZŒ„˙ﬂTF÷ùò°ªìñG9u;£“¡p±ÂwÅ¯ˆ0˛AËﬂo´ÇÄ≈}…ë}&ñŸæ‹iÍh¬)CÉYÃ Ø|¬)ﬁúæùî˚»©T /ë‡”È:dÔJ·ÆvÂ~v≤†RÖ}J∑É„TPJ‹nî#”“R¬ó∏Bõ‰Zc/Üñ<¨Ê”ã=∫)ﬁ4—t+π0…65∞Ëe‘‚≥ô3Óç`´ã YŸ*ÙÔ¨"¿BïÉ@G⁄˙øºZ5ì‡M#YòÃMÒÌÇ)J1óx”¿¢¨b{sœ‘«ÿ´¬A÷Z9AEˇ‹3⁄áY=óÄEˇ‡xOÅ((ÔnΩv(ñâoÈŸ6∞Iß 1©µJ6SöÇ2◊˚ü‰ˆÛtØX˝∂=¸$snù^KÒ*3n©Aq˘“y√Oél‚ô[f´Ì¨bLˇòØ@ÓŸï;°„à}‡*í™˙…fñ∞∞©J/aEUòßÊÕ¬CúH¨ùÀWÑ
´ÎŸ‚È÷åÃB#ﬁ¸É1ãCó£}û c"`®üGd!“-$Ÿä§∫ âV%Õíúü3ˇ wfRíù
“6S‹˘MOu›{UªU€¨BUJV)!]ö¿X≥)bŸv˛=·È·º'm¥ÖH/ÄQˇ£4Ängs’ICZ©ıJ“’ÈÜ¿"d^qtXòëÅì∂5óÅ˘lı¥~€ÃZe“;iÇüàßO-0µa ¿‡?√Åœ4ˇ”€ÔË|!2Yôœ…)¬§‘˜ß∑ §,Îëôá:*£ã¬Ag+-6)T≥p^b˚D8v:CW∂˛“^ñ≤ˆÓ·e§0 Tøe5z’ÂÚêèFºÔf,'ÉêáQ É]ß!^Í∞^BπqByü‘ÇØ/9Ñäû%I£Îµ]Ÿü¢ºko—rßˆ/ˇ˛?ÁLƒO≥’æ0LG6ûÄÚ'M‹ˇôNI`¢ﬂê¶,¬ÃVÜ–; Ñå¨Y˘ „7X†òSwœfßB≈‘X—©G˜v%Z_0∏C…Dà ã HJh…jIbú&ÙÆ,ÏÌôÉ9Óár⁄¢‘FdŸ£1Ø|‘ûMS‘û¨ÅÈ6Î¶¨∂∂∂zﬁÒ¢1„C.ÇıSäfße´cb–öc	Õ∫∫w˘˝Ÿï4\ﬁ€[c#Ω;ÄÒ`Ñ|:áWÕ-k¿,å◊ˇÚ-á˝ÊûRºH√Ñ¶¡b/eà)™ö¡“€)i¨â\Ò±Î›m≥&∆,wö¡]@{ÃÆÁNné˘‡Ç~%óŸ“,`áΩ;\ZfÁ”˛4ú.≥ûÔ¢#`¿'A3p|˜Í9≥Ã´fÇ6‡∞nÆ…h^^]AEª‹>Í ∫&º€ÌÊl3√Wa"–)h§ki$gM´ı"e‘já›BöhÜ ‰ñtdcc#m≠¢5òXùfª÷Èw≠%…¢·q…ôKËa ‰Ï3Écå;døöR”ÊÎ4}>t£@ò˚ñè)ÑÜ≥tL%M5DWœø‚jŸkóµC°vTÿªNkcﬁ”/~R£<ZÖôé	˛€"€æx¢€ÌÕ>Ω>ãk8ãr0ÍôyPcøﬂòcÊg‚”MöïÒaå‡⁄⁄ZªnIK◊> ˘ã∫Mÿf„y66∆6CÉsGÖ»(à≥é¨
`
E≈E∏¡_òåÇ◊i∑& Ò¯,Ä^®oÈE÷ôwAÖ£ebo-À%!Ò-≥yhk ˚M¸œh üb±öCB†gÂUeú3+™å'-≤¢¸≈Ñæ©≥ˇ+ö>- dY˜⁄'∫™÷é≥q5pÙÈ"K*√”hi∆§ÌNà_P0¡
H“˘%¡–«+é√ï”wû}1¸˛Õò∆N(VÀˆíbD⁄Ïwå«\>)§jf∆Øçv∫„1¨πÒ¯9ÀçÇä ±Êü5À9/VÃ“ŸãÇPc£éQ{=Üñö›2ü(aÆ:ãc)Ò`ÈÂ>M0å4èÕ‡t5 úô·ô—≠ìÕ†»w¥˙Ú&˘ÜÉX«'4n+42–ù’j˛»ø+8«Ë≈ë´Dgü…òHoú	¨,Âál yOäµ{Q	Ü
XG–ÿÍù
€ ≈‘=ï´ﬁDEøõ≈FGR/©<DN8æmKˇB´@1(Ÿ∑¨2Ò%,˙oÒ
ND/s'#¯√⁄}¢9»‘HÜ]úRÁÙ0+;q=Ó≤ú•‚¡W=»»RπxVârØ»¶˛qÊkQo%KÑ√‰˝„@Œƒ	^ˇ˝s„˘¸ÂÔç<‹Eê"º$¿ïò}.±CU≤÷ÖÒjîX≠˜ lLö%aè+÷ˇÇ†€∂Ÿ∏p.Ô£É&M“øJ†ËÏG.[•N*ñ'Ä‡„˛(—2V+ô§<Ájù¯CÖ—≥ùsùÁ=Î}tá60, ~ª<XlÆÈÓMÁœ-ÍåËÀ„¨pÏ]Ñ9ñFπ-£åÖÇªö+Å‡ëS≈e£¨ïY·T∞·°õBÕdÔµÄÙsgπ2|òŸﬁ#‹Œ`û)Ô'eês∆gÁÓ-ø„aíÛ”~Òx˘Y›™Æ/ÕóàˇD7M∂+∆ãƒO«VDƒµ»^?∆ò®ã/4o¥ﬂwUÒ¬O5W¸ƒù√;AÌ"ç,DÏ√ù¥ı2_sX“n◊RﬁEÖüÒ@>›±∏ %m©à;©¯DC_¡±p»û<aôwì»Û¬]=ô≠Õ|Só
HÇ∑cZ 	…Û#Óy∫#†U∫o•ZÁ«è.Æ3√5.∂/zUõ£Y˛Ú´⁄P∆ﬂÿt+8Ø0#À•!5`ûíj3~áUıí‹‰dØË˚Kºú˙6˘Ωùj»UD^ÓçC}—B3S3W5∆œ∞ûÊDçò&T∏';ÁI@áM◊∆ï;ZSïÙ¡UœÎ ±“|÷¥Î^Yç© ≥qiºË«0o˜üŸøaz}U†J§ó|ÿ_˚Å™B zKäè©¥ëû∂Œ¯eíõ6˝+,õo?øí´{√‘È˛∏Û]œ'°.xó^!òƒÉ‡cxT‡Â
êﬂuùÕ·j7-4k|·q˚Z∏l/EvfÕvAp*+
UéY	Á:ùüí<7ÈC›GjaõuDb E JÑS9É∆Âÿ/d§ŸM_J„aÃnfŒ‰cƒ=>˘;6f ñä˙sgB¡‡—o6⁄g!õÜ’/a”∞ˆh”0ÿr6%Fô+∂≠/l÷–π⁄r˛ÊÕ‰∆9èY_nÕØhŸ@´ˆ7ÀKc_ﬂ≤¡Ÿ∫Z«Knıª”_w∫m„˘ﬂáqÉXÈÂv3NtWa\
FıKô6ƒë±~1nRÜıé…µú!™„ù¸j/[É™πm«`≤–ˇ'&“»d:q~≥wHûW≥w»Kj_œ⁄·å‘◊ì∏‰Ó◊5ı∂¶ã¯3˚µYIú)â>÷ìËò7~UF±⁄√lÕ oÓìrs€MêéÎ“ÒqHÃç¸Ü:˙LàëQö‰ÁÃ•ŸEÖ»ôãòò»Lkgg'k€≈jê≈öç©vé Ó»jaaÎVM∑äR5ˇõΩ≈◊∞∑÷0mQ´B§»§sé»ŒEFO,”∆¬ÿu,÷Mpm¨≠¢Öaw-∞3¨Û—q?vB‹WÑ¬ÔÔƒ&cN´á’*VR
N≤"∆Í•£i8ıÕ˚Ô<6’1òs¯ÚH,Æ,7NI%Cî‘¡‰aª√?Ñ¡âåÉtŸ€qÎΩ?ûº∫h∞„ﬁIÔ’¡Ò¡…% „*ÔäIöâ¯¸ˆÇÇ¡ˆ—?nœ≠p⁄@#…_ò;Ñ	X&ÎímVª‰˝àúYﬂÖ†∂Ã>FcèèHsø,ï“dÈg¡?∞˚ÁŸ∂§7ºlÔ;◊)PÀ/,p∏?°nÄ¢•Á∂4@ÅﬂIæü¯¨—ÎkÆj5C+ÅLñ%õŸq?‹@–»§∏Ø=«±_·P£}}ªqºà˚µ(^”§√I ÕìÉ3^~k≥(4Îr~‹Î]º:=?<∏¯Ò#;-Ω‚›•e∂¥;ùD~yÁf>¬oh`z/,vVô
æÂÓdr«ó4kk{ßÔ.©¡#ßÖ—”õÈÇπ‰◊ÓH@§àŒ°¢Y‰Ó„ØW¸é≥◊Ó0ö·Ø#'pF»3®ƒ∏‡n2HÏüº)W√ü3w*
åsÂÑÉ—πÉ68¸ñªÚAoÊæw√—åúzmÖœ‹9#ÀB}µÕ¯NyxÕ°†∑–‘|À€Û}~◊r˙[5÷p∂T†Ä[`eŸ°•Vú@÷X¯ﬁpÕ-rwaüÂ¯<y"A¥t∏Ûˆ]Ä∞∂7Á@~V#îøø£‰XÉwáπ îÖá“±ZñT¯?‡W°p<%∆€KÊÄC 
p0"£¢ê@¯µC4èa Í	ù„˝ó€l‹ÚÂÀ∆Õ¥qi·3ïm¡Ù4À∆f_p8îi¬AÎxK˛¢V´∑eÑ´µC◊üIÛC+J'"Êõ¯ëDW»‡aÔRúK7¿Í&ì14…»D±—z–∫¢=æNí îúl]n∏ç$‚˙`∞L—rDÿı¡ Økh·´÷d:∆»P“p≤0Ë˙m„ï—ëª˛¡'ëzheß‚¥'◊‘Ò6¬Òù1Ïº@ˆóäS†Ùm¶µÂÁºfú9˛@X§ s—Ä;©w⁄–Ññù≠ûÍ‘J™nÉ˝ØñTä∂T€ t|Ü)¿öæÇu,ö∏ädÆ$Ê¡Êèá≈LôXÅ®∂óﬂ°‡öN.TúQKLËpbs–Z|8¨≤È4ÕB÷B†“Ù®Ãí…éèv„0éo“∞Ä‘J<∑Y∑ —º;ùQC“,EH7W˛t\œÅnP:®á®‰x¸O/¶)ìI>e˚6ÓMë_Ë≈ãQˇe⁄‘ƒö
•Óë¥•…ßæÅ3œÎÉﬁ˛¡yrÙi6Á.Mã;l‚)›í6o}>Àﬁ∫™ÎVFvîÆ%PWy„~sç]ÛYsµ ŸZëÇ-Œ-/Û∆ƒ6ì(ƒ∂∫e {WÌª¨Ô6oπ£÷∑éA4¿‚∆º,ÓK≥Lzµ1{GkEif»ÆK?∆ﬂê˚wh⁄ÂM=Æ6ÀÄhˇπÃ∆0ÿÃ!éèOfŒ‰ö∏`ÙÑâ€Oﬁ Gª ⁄´H'kõÖÃt”@&ì^tg•páÕÈáˇoN£êÆ1„é“”`ºƒ¶ìÅÁnvñT|fHƒæﬁXbdÅ¥≥¥G›SdŸâÄ˘…Ã¢
´>vö5{"[ı≈ä@x·AKcc0¥ﬂ1üÄÙ)6U>Ó“ª>1©÷=; %Û›È√9pÓΩ?íPDïÖª—GJ3ÚΩ°:|fQø‰cD˛X–§ÿxWòÿss·EA”ª÷qO}wπ#_úƒ«Ã’:-v‘;ø||Ì¸’¡Â2;;?}u~pq¡v{Á0ﬁ{Ø·mu¶7‡˛Sõ‡v”1õ##n&£‰ koç∫≈É)&sÔö›uå8›8 Â´oÒMÏ¨πV¥Ú∞„GS–;rQœs8!ó
d)È<a=º5v◊ÿÌ©◊ÏtQ˜ÿõõs‹Çñ∞dªNe¥ÅãL0XˇZ1e¸™∆∑”NÒÎÏ¥§ûÍì2˚»Ã†><YÓ+P√è .S*«‰\ëNÂTbL}.IRó3ØÁ`L–≤í•∑ ⁄ˆºÏ…∆è†Ó¿ıö¡ü`¡;bMós —ªÚ;„Q◊∞ÕØ¶t◊öò\†ªF√á‚¶p…úI	YQ·“†y"üO öõÏ’•)≈ÛÀ“ÀK«øâ∆≥(πOãJt—ƒµ√Fï·äÑXÇ≠‹ cô6sb∫ˇ={Î [öUipNõ’1Î¯ö$∂øÏ¨Ø»ä.oæÂ_˜yΩªææ¨˛ﬂnµ∑ï\ò¯4˚@k¿øî†?obÆ∂S˘JÜŒpâ˘S2uó/·›RÊJ”6Œœó˜]ﬁ§≥«dz?≤%ır óÏ,µSO¯Áù%8òñfÚ’Ê´|‚¡Û‹'Ë·⁄°do‰Œ0X˙€ÀÏ√k˜#è~XyV•‰Ò·(¬„|¸TÍ,>¿—çE÷trº˚U~-œ9ÒRÎÿÙ®kh.7c¯OØN‚‹jU.^v‘’Ü˛VÓs $ 1úuQ´QqoÀr¸ÅÎ<ßyÂzû)mÍ›.‚>P∫HòõÃ¨Ó«íQ–,î~3’X9ôVû.:}Õ=_ÚZlﬁ	ì Ω_—åiÀ.ókéû˛™&Kh#Áû-©ƒú{∫îÚÛW7_Ç!Ê&L<˛3VÕt¨“…$÷…6€üF◊£I2ö>ˆG7àÄEˇÏpˆ≥Õ:≥sRpõõˇÎπoUBº[Ù8‘Ûÿu‡˘≈	˝¨≤µ¯‰ò;å…_…ΩY-¿F˝6o_¸ ï+.≤\'x?òzQùæCË¸ÄûÚ+)fÒ‡π-æb¯çz^-3¸∑Ò‹hœ?õíæ¶âˆ(a†«uã™´™@π¶éw0ŸdNø§èü`ÑŒ[ﬁwåkP§UN˚†Qç≤:.‘∏Â15»Õ±|Øpd„ŒD)Zå—n∆⁄ß™ËˇuW¸Ø
’=›˚ÓÌÂ™v.zÁ{ØIœ≥ÄZgUú™©hÆÁïÌ®H±ì≠ÈNfQÿƒ„…åiﬂÌ™• ùÍÙ⁄Hyx$°E;„H-YBπñ+Ø)&IJEPëæ/ﬁÕd¶Ûò˛/⁄!æéÌ‡ê◊øÜÉ”4«õÙ‘qRÍÎ,6«ﬂY⁄És”Ú¨ƒvR≠V7|Begi:IY[Ìë´uΩl´_|–Îµl(b±]ü$sL‚â¥KGÅvzÃd)Ìª6bÚÅT
SwB)}ÔEhK/Ep$3≤.sO§¥“àØ∫ÊÖMR3:≤»õ	yqÄ7RØ}¶]∏‘Á¬üÜÛÒI°Ùv+M
J–˝5ìC\∞Í|≈ÅPróµ%&“è=-œ*OãŒ·0ãbÃ·0÷ëivÊ`pÂ§tº“t¥Oô„∂¥	-⁄ƒÊìU5&±ù¿Q©lp
5€˘õƒ€&j£í√ôÔN⁄86–˙|éoô‚‹v≈}ÿÿa4#‹…(Ä›∫Ae˜#ã5´-º¢:xÀéﬂ]ˆ.t_UtÂ˛ Öv—fî62¬D˙¬EpõdﬁiU–É¨ˇ‰8YwµZE¢D≈´m—
Àò ä˙!<æıä¬	úV†d1>cw8Ñ◊hôPFÁ\õDπ
ÓM‡#ª8ô°
7ßK¡)ù{^ òƒ ≥5PZ3§Ó÷"Åi-X≠eüXüºÍ‰-#‡^u/®BüY¢ä€˘dÁW.>Æ" è€óÊkÁ”€†\S_‰N"ÿ›	ãY∞˛(ÎTbOUœ˚äM÷©DÛhuï∂X√åì∫“±û—sÎfåÀVåñMΩ8íjÆqºXfc—òêp-U)ïπuå⁄ß|~dÃrØß;œΩ6˘ÜòƒE
RSÈ÷–¡õÀªzíTôgsS:©b>“mÃS1,Wåã…Ï∑°?r/P!/•°Á√∞˛¿∂ŸáŒ2k√ˇåAg˚◊{x«òÉïÎ{Ìw›ÉÕ˝’nm@È*,u=ñè›W˚›wª{ÌÓæ®!Ó…X]”‚õ™Ïmt∑∫[5≤2•;4UCíc™
ıÆf∏Ì4–´D”&df-≤mﬁAŒ≠–Ú÷¿”@ Do≥⁄PR`-ÔËÅìµmÒôP◊áFÎ5m Umêíübj?òùI∞.ö›wE £Ë«Óïí\AÔâhE&UHÛ^neeÂ$ÿt6_⁄U0F2Âêˆ‘k(„wâ∏¨˙"YrÖ^˙.»V¿p#•ã+Å*øÎ8›g´}∫Q¡Ä\1Lò˙å¶üˇÙÍärlã›ˇê{lpı'…¿FâàJÎ»1cÑ‹á§Ã‡–xŒ‰6ªÇI¥îD!V†£õ›ﬂ[:9Û¢k◊é~ƒ≠÷v*‹∂YË—4ùz°;+KÇédVÿ~å.îÌxG©”∂˜9,KÀ∑E¨ÓqYYP,±tM±u∞ü‚„æÑBà›Î∑[ü
\~°J≥üb•¡ºAb9¨”˚Q…Œm∫jTöéÏ‡WÛ‹Tµm˚ZU°Ö]ÄL˛2öO–∑^3Ûy¸j˛ÊbÛ+w±1ÄÛ˙^4{ÅóBu4,∫cÀ9V†»äh=yÇMLPƒ<ÕYÌÿ¯% Áô79%éòyV°Éaãl‘∞íÕê/Ím¸4øÕçÖ}"‰ ®h>≥ÉôLÂ“%¥Í#4|∂πŸﬁòÑ*ÿ∏Ã18V,¨É£÷˝|£#÷Í√#Öˇ≈Üßƒ¢déÒ±¢Qxòãóıü"«øª†mpÍ˜<Ø^k¨ÑjI x«CÊÈxö Kƒœ∫≤ :†	v"^¢•¢ŒURÕ◊jë5RÊñ‚˛'√!9◊–@kH°„ZåQ±‡ ÀXhbA@K¢íè' 97àdñÌˇ€ä[¨∏Ò≈‘ E\Vª…≠5æm—Ey›÷Dl˙[e2≠∫„¨K¢‹>·‰W¬Ãìªœt+"Üq‰a	ÃÙ%ZU∏Cë¢pr˜ìíÃÊ#%aò%&∏÷¢Ò1jC.‘ƒ|#ßËœ6ÆGë~Ë7æ;(±C˙IfÇ‹…¿ã‡e]Äm4ä«yÁ‹¯∆Ω¨ÕŸü:w)h°.hbGeﬁÁV—∞¢T´âHÖUäÅÔMØN#Ëä}¢⁄ %˝¥∏Ì}A7ﬂ˝ƒ k∫∏=˘¯(.Ü˘@â⁄1¡ñ‘£ì@IQ˝¿N´ÔàD>∆{¸›¿},ZJ#”πíl
∫Y∆¥“H§„·d*b§˘ƒÏ˚O⁄ÎrYPSjõEJqQE2gup¢í Ì•*TΩ23nƒâ¢Ú⁄’Eì °ΩNÔÔ«ŒÑègÆá7√rˇRc*ª∆tØK2Z•º∞S;ä√ÈìÀ6±MîŒ4kOÄÇîR«WÃ$Äæ`Î,ÎíõΩ$v'‚˙*hÆ&Vz˝¶tÜn;Cqa|È˘e»åª√î.ÉÈ`z√Ü=Aè*Ù±@$—W{*ø¢@0%©ë‘'”8EíKq˘ÌIí‘GÊ˘
‘I:√Q•$oWµ$y¬ﬁ¿,vÿõã”ìV@Ú¢{uGx‡	ólµÍ+K+◊À¨ˆ‰O—4|n
Pîg›¥nÅ∞KùÄ9e‘ß<∑å˙T;i 5ì#±πRœh∞ÅR˚Cµ44:¿J∫8Éc¶ÍÅ§âoY-q’$u/¸‚&‡◊Óu◊ÕÓ∫°™Ó¡FïÈAR]∏wöL9|äf≈£L]£hEc ù%Pñ$¿π'M¥é∏®«—LDÓ|ØË<q∂≈öÚWÓ>J»U∂„≠JfG\2?™ËΩﬁ÷«ŸBµ±Ey«—81J~äH‰Y˛/ˇ˛?/ˇ}”îΩˆº§[ëƒÚ`ü¨&LrÊñ∂X÷!Í’¢}ØjàØô⁄†›ñ∞WéøU≥VéÅ«`p'W”ƒ ¨ÜÆÕ√¯¬[x6ki∂pœπo‘îEï±lûŒÛ∏8œÅ∑†
ÕtmË¿÷Ì®ê -wxüÿ∞ΩÊ≥(∞¢ía0Z„™æW
jQô2∆·'/Ã©-∫\;î|ÀüPåfÇ∆ÛI¨—®¨–xm≈„Ë&Aë◊R…π©®»ãô$”„Ÿö∫û3ÈH§æ≥Aß„“ít¥•~X_»J∆¡l#ùÒáÌò¶Öπõ;æùuä∆àÖRVfGJÌõä
Gπ©ÕwóN∫È„Sn•ÈÏΩ¿
Ù›Œ¶,ô≈ì5®LU!b©>¸·≈
U±4`ˆâpyúEñ
‚rÈ”mçÿ∏œ˙ó Ù)LÌÆ„πæ(úŒ(‰Õr<5Ï5ˇË.≥a–7°ÕdÌã˛"C5ÏıÛYcû±üD„>Óe…ËxˇÙ†ãeBÁ7fò`!{>uOı«nÿkGﬂ≠ÊNË™àÉf®”'ë◊ùTŸÂ¡%≥Ì…ôI_ò7ÆæjﬁzÙ’bÁ‚õu c'Má¿ÀŒN/.-∂-h@
{Í6˚•&Éo7q´A-LË–Äf≤ÚÑ±ö≈ñw˛Ì¨n¿Æï⁄0Ä˚è°¥‰≤ñN&i[˚^V˛ç‰’˙/ã8ìóNq‹ÿÑq‰ù¨í?©@–EZ˙g^0öﬁñmz—7R⁄Íˆ“4¥{ˇëR/Jãí=û3¯Q"8(SÄ*mq7f4Uê[—|k@:ÅRˆˇ/ÙS§ﬂ⁄ÇyoõCY¶[PÌßˆ?lPvDÊ`â‚8 Æ§æJ⁄®·N˝î·^∆’í®éz G)ê^Üû£üg f5ô¶∞ÍóõK%$°gƒ{§R"∑RAEXafñ=c˙@fˆMﬂL.BZï*EE§{›xõû¿î∑““Ó•+RˆfòµU[˝€≥pJE”\@>„ÙÖÓÓ"Ù•Ì,äÚxã:=º8ïJÇFp√zÌ≤÷¯êµI^@ÊLá¸[ì9Ö˚©DæD‡I{SRÕJNî∫S^øπÅ ”eV√<Ö¡DJ˝^ﬂ·-ÉäŒxB”R¡aµä˜+AñÜk+(1∫e˛µv_Kãå˚ïf_q±˘f>Õ˚Ê&ÅBû\0„r«0dÆ∞=o9÷$áN≤4) πÿƒ√„±áÕn™"6e¶8E4n ZSÒ8— .#XA¨Öæ°&0¿„¯t¬Gr÷ÁªhòÊ<fÀp≥ŒxπI–œy0‘ººv¡Æ`hø∏HóFπÃ7.)/n	h¡cØ⁄¡m∑†ªGdúz–å}uπ≤àÆıç™~Â3jY‚Cı5Ïù¨y-jôı˘ÏSw‡¬Ûr˛¢Jy1^( ì,°NÍÜ†&|i>π‹e„dôTúÙFz;WëÁ*±9õ—òOGH oË}è t˝	~Ú¢ø¢∂$aaY5â∫∫X/Q‰ãR
$BTã⁄8πü,QÕ´r≠íAØÉ™F„ôÀH€/Ó9>àˇjq?
Äƒ˙ÓHXÛ¥sˆÓ¯˘S—™KÚû-+¬àÛû-'c˛7£¨1^míµLf
8∆i˜†ˇ(á8GÂπ9ècLø√ÕHô¥B_Ë¸fCDvô«‰˙ƒgV$≈ÃyÓ(∏X≤æÿ•îÄìÖÊO:≠´∞Ï⁄HﬂØ<fèsÍê#ˆ∞ìGŒv–j±Ùwvë£7ˇ9§x]ßçŒ
µ˛Ë+=Ù–~V¿™@2¸—tıI¸Õ÷∆|“{öM<öü^Ke√RIrœÿﬂîÙ˛Ó´ÔòR⁄Ω…T∆'í|&√o⁄‡œf≈xÑ$ı™”…ïÎè◊ﬂd#Æ[&|~‘«Nk¨—õÒ>b=`™Ïéﬂ∏'Ë˛≤Ω¥ÅLóïu`p'Ó∑–Å$û(2
O'≤O€%¨å∆ÕFæﬂ˚Æ±ß	Âóòd˜ﬁ\‘l6Ö+Ê+ı´0i¯ﬁ¡eÔàΩÌùùû˜Nÿ^Ô˛;—RâÓú\1¯Éø˜.zá'¨wˆˆc˙œ.O^Ωz›;Ô5Râ≈c1%F{pz◊Øí◊ıq∫{yaXöﬂ2æ˛é‚Ô<„´íénS◊˙6Û^y°LπøáxœVÎ6áÓµãë≠∆∞Qè–Hjú~ﬂ9ò¢ß/«w`4ç¸twÖéˆ»ºlπÔ0·\“t¡œÿ;±M{(,Îxö“ñ¯Øsø÷CˇÛ2ÏKC«ÊºñuZÉ
U|÷dê6tzŸ√3∫ªiû>¯º9éuƒÔ[ÿM*ój∑"wü!9√{†+enÂémÖmfG∂ƒsæÇÔöŸCÒüœe-Áï$Å&Ûî\w«√]Õô»é¢Ÿ?i1XÛ˚#egÄ<»“ŸneV
ÙMJÂ(r≤∑jˆ„»zÚöb„§ñhôÀÆ∂‘Û~∫∆ ∂qZ>Bg∂YóRs»dmø€⁄⁄¬µ±ˇ-IF÷Ó_kâ=Â«íMyˇtÔÚ˚≥*i8Ê€[„¨æ iñ≥¡óy∏≥ÖWÕ-õﬁÑŒ /≥…ma;;æc0@ Œ˚˙J≈,jqQ;ıë∑¸/å»Ïäè]vï &π¿∂qıúYfbuuı9√&ÁNpS”¬¬À´+®hwUuPd5dãQ¿ª›ÆﬁÏOC8%m≥ul]KÖ"–±7“¬QnÜ.Ñ≈çmllƒçâÃ78”z7€|ßS“|ç1`] Ì¶Õb–£@Œ>≥`ÍπCˆª·ph˜ÜNÍƒ…7 ô¬ÚÒ,v÷ÀäÊs$˛ÓÍ˛W\-;`Ì≤v“ÈvZˆ“Gy¥
3ì$¸∑⁄N&∫›ﬁÏ#aÍ≥∏¶eäT®gÊAç]¸~céôüeà]Üìu≥ñFpmm-ç]∑§•kê‘HzT»ƒyáÂ<„GÖ{/à≥é`a§Ç‚"`˙/©tNj0à«gÙB}K/≤Œº*ÅHèΩµ,óÑƒ)”g~–ã@#XŸo‚À‘\4Ü…ûô{zV^UF03≠¢ xZj‚5—/&åM˝„¯_!PM∞≤"ìêé™5‚l\}zÄ®≈ 0Zä1)ª‚dò]Cô0FÉ√TÖ+ßÔ<˚b˛õ±3tπPˆîn¥«à¥Ÿ7ÓıB|RH7‘Ãå_;ˇÎt«cXd„Òs&÷è¬i´ï®Y|x±bp^ÿπøu %(cÆ<ãØô‘ææÙrﬂ¿è1æùàƒ…qd‡ô^Èe†∆≈ãÆG´/s˘ÿUÁ†7´’™ ﬂù#Sòb”UÇÒœÑÛ•J«*SJõ/±-ï,⁄2~ô6R>ÜI+sßˆû≥ŸT¶Ÿ|£sdú≠ÿÓúy™Áõ(⁄1üˇ{·TﬂLÖ»ÂånTaÅSb±≤-cƒ»sZŸéê Ó^9õÏ»©\®&Ï,õA∑jâSJ#Â…Ñ,GA{ÒæJfèJëç;€Ã£dö±ô#Àå!/L{˛3(Îãdó±`cPUœ-S†£4˚Àãí4,¯Q¨UÍvJ2±Ö~‹%ûV·Ö∫¢h›8$bÈÁín/˛&≠rK1µπ9ÿ√Ç®|ùQÀ'<|ÃÅC~;?s˝B„¶p3Mú´ı´´JyôKá63øk∑◊7˙´ ˚Ç{√)ª‡<dáó’„Ì˝Qá\µó;Q/ê,¯aQòÀ…ö«È≈äπà–9ıj÷ääéV‚}_öÈ‘5≠n·-ˇõﬁ{'ÀÏı·õÛ√evvﬁ˚ûÚø?Ë]æÜoØﬁûú_∞ÔNœ·Ÿ.€Ô]ºﬁ=ÌùÔß.ıÖ.yˇ§˜„õﬁ˚€añz#>\ZfKŒƒôà/0çøù;˝)˛=r∆nÄ_ﬁ8„ËñèD©0Z“úw5–'góÔˆ˙2[[f´Àlsôm-≥çeˆ,W„¨w—;Ôù$¯ºuÆ]l‡åè`∆È€îzGG¬∆so·âíﬁ¸3jêÿ ˇÓË]‹jj¶ñ.0”
6ıñOÜŒåèn\üZèÄÖÆáﬂ/ß^Ñ_E„æÔ\$ÅπÁÜ…èÎªën·M‰¡”[ÿá©Ø#‡¢∑Ø∏G«2—ƒw¿ÂZ‚phç‡$wGH
X≥ËcfõÅ1îPÌËx[(Í˚¸∆°iDÁi	‘b·õ∏Òc~‹It˛ŒëÌrÏy¶'˙¸ÜÏ]ÓqQÁ:cp£‡Ò[z∂›DrÄB(6¡äaÊ&j˜›[ç>23≥A»g\Ùc(∏rˇÑì∫ÄÄËë˙y3¬Œßaû;ù>ëø¢ı3π.Tı˝c“^ªCÒ~◊	†u{Nz«Ω/{ØﬂidﬁÛ\"¨ÉÌw„Fˆ≈è]Gåì8,Ö4ê'Ê√ì˝Ñöën‡$+qÜ˘åø∏†ôﬁ–Õy›;?¸Ò‚mÔ8?–=ØâåÇ’ˇÚ_˛˙ﬂˇÂ˙ÀˇÒóˇ´AÄºÊaL0eºxı?ˇÂüˇ˙ˇ˙ü˛˙≈Î0h^0Ò!úÄEÅÜˇˇóø¸3¸ˇhdá[Ò˚Óø´Ò∏°ˇÁ/ˇÀ_˛?Yò
ç¯6”˚ˇÛØˇ·ØˇÈ/ˇUΩÉiç†ÚHæ˝ﬂˇ˙†Óˇ*ﬁÕﬁÂõˇ
Pˇ∑F¡îü˝Ò¯Ù‰ÚıE~4é£˜}>£z%HÔú˜›Z‰±ﬁ≠ú»¯Åâ $&•„¢4Vˇ»â/Óx≠èk2Cß*åƒZÖ"
Œ˛œë˜'^ì§âøFÓ«èKÔl¸]è◊ˇ(ª»ÛˆùñyPG”i∞Åˆπ‡»h'Å·$ÍT≠Dª˛´±&Î∞m˙˙<WgúØ#~=eù.‘¢˘j\YŒ\y”©_øc+dÆí/ÿO‰PpÕPl ≈∫Ä(¶‹ÎÁ_;i(´Î≠Ó:˚ÉÜü≤µÕŒF6∫9V∫ Tj∑6⁄Ì÷cÔLU>—öÌî`à·_˛Ö„∑ﬁ]3¶ÃDx≤√·ÁtSı:˙=[o@}¯øÂ¬ÕPïÍ"~PmläoôˆAÅ}0¬Ã]\”H7ıÕ&‚=∏xN¿( <f˘ªÚßcF˚h#áƒ-¿∞#±A»w;mƒÉ˛–/òD>Ø›èæÀ∆|6£Vß>{√?Òâ8í¶ûn‘›¿„cÛ˝ø∫ÉB⁄œèÂËﬂäé∞÷X>0î˝ﬁ!´6QÅüDcC±}äœ"JA´T(OﬂËYåãä&õ„áüD.‹k˘6B>äTmÅ–Kßõ/àvâóXÿ4W	î¶ õ-úÆ-Ò≠   ÏÜÙvÍªŸ‡4D5Ódh$õ*´]ƒˇ≈Ô@8&å¿JvŒ™ÅÑ‹C≥Ÿ/‘*:qf!÷O$ﬁrq˝êoL.ZU'%©~HV¥°&ùUΩ§›ß)êÊ˘[¨øùàˇ1¬y„h	};%ûÎòÊ´‡™ﬁN§Írïã¡í£)9¶Í“vÚ’äé,®ˇ óMFl[˚nJÀâÀkõ÷\˛mº∆∂ìØÜ∂Èm'_Û•bﬂNæ∆©o[¸IΩ‘¨-âv∞Y≤»,€±ï«Ò8¡˚J7˝ÁÛ–ñ›¬_T•ä…sv∑ª^fS €	sã˝lv~ì]æåÏb]‡k2&*f @˘gk[k´œ›D©"ç’√—€XÌòkú”Ö∆ÖﬂÌ¸^î7†Å≥|8ŸÀ∑˝áÆg¿¡0 %Ÿ[W€º¿˚`<M5 Ãcqzı}
ìL√Õj:ƒ?dÚ¢∂j¢*! ªÀÙ1◊ê§Wÿ˙≥Ü@5H(È˘HÉH£ZOö¶›˘ [Hö+–T√2 Wxè†âW∫¡|b€ﬁYfiË&¢FH“ÎÌg3œƒ{«π—Ì‹5ÊÇÕ∂à.v $k/Å‡˝Ù°0ìcıáºÿÂ°⁄í`À¿cUıÌ‰´ycSÕoß~Ÿ7A5Lw-9€ÈI±ƒ¶∂SáÁ©jπA
√€bÒ‰ﬂ_õ⁄ﬂmˆSúH†{Øt∆-∆e˛={˝”ú€1å€kÈ.≥»ICjﬁ|≈Ò õÊxo:û˘Œ»°‘“Ïp2ú¬πs¸ºF{4ƒ˘	;Ìéˇ	›Vç˛õo›~‰√õ3aìÃ	`,∏◊0éœ	π'°ˆ&√®°wÑΩy†∆"n§‘ıS•»√<U÷ªÀ¨÷∂ƒîOU§ÂY•⁄x<ƒLQ5xØ2%îl HQÎ>ôöÿÀ∏"ˆ"ÅÇ?≤WƒI≈hIW∞	LfûëLµ…Ÿ…ªî¡wZÏ;˜≥3djÿY‘˜‹A<≈¸Ø∞BL;>≥‘ivñ∂Q9å'6éVŒ»] /œ•uQñhg7Ú£PZË¯I3¶*Zï∑|‰˙ÏUìÅã™„|Ò≠fg3.‰åË‹p†ÀsgÜΩΩIh€TΩ”mv◊„˙Á¸é„hô1√≤Xv/
°;é†UR∂¢çÅ‡Gd”F¯“ &«É4	*˙¿Ïw[lˇnÇÖI∆í–A<¡}òπ!É•*T%»»r◊$~§Û¡ësßQªÆûà	=≠!WC¶lL@€Ÿd÷»ÉNtb≠wò“∑6ñÚ „Ñµ‹ns5¯òGhÜ{¬˚.è˘ê]ÙﬁC]ñRﬁVl¶ªŸ‹Ã7s¯úª5ü4µ’›d§ÿ≠⁄ïfßùo#!»√! ˝ùå«I™Å´‚?ÏÓú∞;Ìfß[
º7q¿ªÕ5u~ûÍW™M8ÓUS˛hˇé}o÷˛|[Ã
ªÌÓz≥›ivüe8‚·ÿ1±ªæπ¡é¶ìÎü]œ»E˝UYü˙\vrÁÃû=x¡o8Ö6≥◊"úµfg·ºÁW<ÑæQ¿é|7·ooB÷√˚∆B›6Ç8r˙t£à|–np&¨0÷qrSı=wé√±ÒåÌ’q‰L∏{ìm⁄Ro'†C|πpuŸ´wÂböøÕ≤˘€¿˘ÎTûø-€¿1¯∆E÷Y[€dØ+wÆºıB9ƒ˙É(gÉ(gmë˘∫Ÿ4ÙùHY◊◊õ´µn∂Ì‘
57$’Âˆ$hr´®…-)rX˜úB õH∞Ì\Àä`∑ vi•]ô`üŸÊã‡t
	∂∞Pπê`K*IlQÇ ∂D∞õH<b"Ê"X™'H«B∞Ö]_œqâÑ`;vÇ›DÇÕìç"ÿgEMn…uYD∞†LÙ5Ï´‘Å©Ç\XΩL0Xk	I›pr!u*auÌ‡õæ°ÚÅvÙ:¶Ù∆S“j<KΩ1 ›ÒõPQ∞1<j˜Æ· a<i4ì3…—‘ü0«†P÷J%‹ﬂÚ(dı£”˜Ω∑F˙ÏËKÛï„s§ŒFÏMpŒ¶£i÷f@’Köﬁ¡öí^ç£°„[0’éOØ‹ü]8«O‡@xÉ·ç„6íV3·A0)A2ı◊g'ÊE'È0;ëgFÁÉ”ıÚÏ‡≤gÆ≠Ìêg,8ª6„£<üHzXTzµ©∑1qÆ	•#g»ÒNáƒAãVøP—·–Wü¡)Ü˚NhÏıjsKÔ¥3ûEP≠ÙΩ™çÔq¿A∏Ôn"¥ı‡–}41üô°¨qv˘Í\m"uœå[–js5°ÃÔ\o\à◊ZS?›ŒàZ*Ì¸ö>◊Gdv`DfMÔˆn4vã˙Ω¶S¬Y‰≈§ åZ'Ö°;§-B#sÓ√#ËÜq⁄◊u≈∆.ZVî4÷÷*Ÿ“RÕ¡#{s⁄!‰-ü|˛Ú.pya´üzÎ*˚@ˆ⁄F≥¢ë‹HMè¨È–usñL™|BqªÓ∞ÑµlË}F€◊<=Ä¶e 6uï’àoh≈ûM=ﬂµTIÜAâ iS?Mg¿ÄíJ™hdz‰|‰7AuT÷∂¢	/f[˙∆ÒG·¢Ôî9ªtn&Soz]º∂R¨€áÂ¶ò¥∂‰aVélª.àTn≈>oÈG≤}g ˜]ˆ=lÅ;(;öj>”8H–§‹{ë n˘ƒHœö:GÙ»)®òFüiÏ˘‘„#ügHtÍ€(ÙYZr∫S√®∞˙˘˘°≠z¬XŒ∏G~q0Õ#ΩÚŸ±≠≤∂/a`J:©ÃŒ¶	óU¶c‡7H¯0¨AËx∂÷4"ÄΩE†YYõ⁄˛b^≠∂/VŒéå$ÄÍ)}”Aπ©°°é∂ë∞dªÖ‚	À∏t»ò(ô}m.OÃÛÄ8nh≥H|∑Ä„"~	Ç|Çß¨µôﬁüﬁ†CBüá˚Vº¥)Îb‹Ã“°–&˘""Å&+⁄:≤™ÔtëËza)a¯åè–“ªºäŒqï‡ëeÑΩªÈ≠i|eyı@Òå}ó˜ÅœÖ”óñ≥#?J°ÚÓíùΩ2Û3¨ôÔ1T·%‰ˇíõëNj==?≥4–’VKÔpˇ¢∞]ÌXBÏÆòh∆ ÄC•l6(Ó H2§=êÌ<ßåòı√Fo“LD‡™Wçl^√æŸêaÛIDkÛuÔÿº†b“Àì(‡“‡ﬁ\4ô◊7π£∏
ª#,}÷€∑’J˙≥˚ç<Vÿn≠R>ÏGπczÓúû>ﬂ⁄Ô®¥r≠YåÃOÊVs7êg¸mˆÕ7ö ﬂCÌÂ6+,©°∑≠ˇ∞È-ÚæVØOˇxpŒŒNœËÔﬁÈ…Â˘È€∑Uw§"£ŒÈl˙…ÒAv.›±3ç¬¯"7.ﬂì;ì Úù=Ó9B¸LTÕRï`ã"9$àÊ!4%∆\ïŒ4•ôæ√CG∂QØ›OY`≤RÀ≈+oÊ*‰(u;Û’‰√·¡'¿˜8d˚ò‰#
R¨‘ñÉıbÁÛìcK+ÑüÅÁp_ñ3Uµ}* SÒº≤)FpAˇ—ONIˇ1û¥¢¥◊àê[43F6<ã	][òEo2‹πﬁ&∑“ı_r°C±bõä*ù«œA9èú;¥»êµ˙œ?µß‘¶O¨-Àb7ö†`‹•…qÎl<¿√—sÁ;ßÔ´Ô§9"/¶ô/\èW8M‰_è˛ˆÆ#°ñFÕYHÚ0˛8Ω	ßÚÎ	 £ÔÉ‘Cﬂ0·Ü⁄◊≥c~IGQ7Î( ˚ÙÍà+GQ‘œÔ®~⁄O?Ü∂D‡4N8Ì,5æ≥`õÓ•†Y,ı‚Zd'∫©z¨Í·~ÓÛõO?ÇÎâ4…õ ıÄ≤¬2vé{^Ô)óí\À◊k‘ürH—ºü<a¶Á≠§*Fóa€d“dúìÅ§ºäÕ)≤s∞Õ$ﬂ0Yt¬äT¡=s+˝≈7∏ﬂÙˆawøÏùºz’{ÀNzáß'EFLø<=ﬁ=}Àû¬ˆo˚÷ÇËõãéQ$¥&&Tå¥L§;‚éÕ˙"QN7⁄O˝JÚç»”∏·ÏYb™Er∫∫mbpôÿ$ÄÑ∞´ Û ºpU°æÀ˙nSÌ⁄´lÏ¿1‡Âã˜%Kl‰‘Íπ_÷∆6ó	{˚/·ƒ"ìÃÊ ‚–P≤Öí‰ÕMâw?6P ŒkCñkî¬Jú^¨»Â‡±Oˆ5ü·(Y˚£åQìı€äÂ`X3?•¢
S‘8÷øn‚	
Ä#+™å’\dÊ
›Ôö∞L<}Æ»†SF˙I$◊≤èv?ú»5‚GG¢N¸õ«?}NvåbU8√Ê¿ı—˙ª˙*êè„.-ôÉùÈüÈd‡πÉõù%%£VNgg>¨äk‚}¿‡)A›+o⁄Á^o8|C$©´≈ƒIú‚æ÷®–≈´›Yí	∆8L?]kíråHÏ6·rhô(:ªOøíêzœ1Íy3Ut˘∆–òM]
≤Xà-C3/
öﬁµq¡¨“ÇAb±∆ä ÜE“1Èè≤¶∞Ë¬Ÿ;?dowﬂù«Ã7'ÎóãÂpSe\[≈äõ`sj &ä¢ËiUâ…U©ò©Lï˘+è_õeÃí∂Àò†i¬ ióç√f{…0O:ÌË‹(bºñW≈Å™’Tûú¬÷âyH‚ÕtÖû\úWö_›ÍÀ+÷˚•9®˜‚tê¥1)∏(à•	¡æ5e)§?ùﬁåπcÊﬂY-˜
£xe4cÿhÙ·√|Ì[dlØF^s¥˜ıv˙Ù/ˇ˛dy‚É°UHıæ—ùúŸé"`º…Ì®wñ*WHVZ@9øBCπÈŸ‰<√fR_ëêêh&nπ?!˜Il0œ0{ÑFWg®(∆˝ö¬cå™!K&
1…1…Ω@—à\õê>Ã*=4›JuÔã–®(ùYh8…»ÄM¢ãÍ}
	ÈçzüÓiV‰ƒ˛M0í*¿´gä.¿i¡ WºkƒÒÁÔ
∂Æ1{˚Ï[Á=9i€˚gù8WÔ˙w´…b⁄Íc!úRœb?ÍÙ„ÿq˙û®PY(ÍEbØÈ{8?º'«˝&¯ƒêy≠Äb™I&:gqqVÑo€˜Ω◊è¿]Ë˛Î`/ÒpAˆ2ûN'¥{πÌKg3§ÌXåœ(·krNù_πæ‹Ò—W‡-)ÁñwéAŸ+Û\z@ﬁ≥◊_î)†ÒµüÙOyïﬁs¥jKmø˜›eÔúı^úÏ˜åıÓUÔÇùı‡á“ÃûsËùú¡£√À∑™Y¯®ˇ˛¡yöêQj¨PzT©1L47p]\mìå¢ñ:H„?	2OJU¯It0™«ôtú^mê–«ØIÊ+=Æ>P∑%)ãTßB Kj∑ê@%©”Ô–è& @,âvÃ?´3sÁY€òπ°≤‰ Ø[£a‰5
íB‰ﬁ•H¬¥ºûÈ´+ç@ËŒr£X´éKÖ ‡•Ú∏˙H‘å´hµ!tVE::1YP±ÄA?Õ”≠l£…VÔô«›…‰é∑Z-•º+Ud=Ú·ñÓ?'«}y¸≥ü	NëgdΩ¡ ÚÅ(cµ≥i‡¢z
ÑdÎΩ«ÍßïcÚ2ŒaÈ»|ñ&-πÔíÏﬁrÏ¢.⁄ŸÛ\cçBÛ• ˇóI»`uXÑ‘ÿSGEâ&Îf„è‡P–D∂n%Â™‡ﬁœ>õ\ûC∫Cº[¯µ…LQß0ä*Eﬂü≤:˝ça‘®YèªÄíõ†AˆöV:à#o‰Z”“ïOn‡íu
:∫÷FÓpËLj∂~ÀÏXíkSu"Öà÷âZZ¬´_<2dh√í‘ƒ!ÇU÷1nVπ·L’ÀdˇºNöÖP‰ı√”|Â<∫45/ ΩÜö±Ïd∆•û&Ùˆ“Ha$èòk™√ÀìêaŸä9˝Tî
øëöÀ∂Ui¸YT<M<ÙÀÀÜ0ã≠!Bo˜Î5LP˜…—≠-L∑Â∂€¸ä7‚Û›ÔæÅlÄ‚tÔèb∫¢u∆f≤ëKX◊ «4ú…∞¶~-≥ŒVªd‘7w
&R:^»≥8©Ù¿≤‰]ÁÔù[0h¢ñ≠¿µ*ÄApËl¥˝ÛﬁÈ+,≤Ã\PoÂæ	 [“«›3¿6F‰¿Ã Ö…Í„IIDœÃ¡6D≤ˆä¿Ë®‘ƒÉQS‘38IÏ`£¸FAWiÍ≤/„Ë'å%;/≠8√ÌùàÒî √n‡>z≠Xy‰Í»È7¢˙Î¥Q£L)¿¥tîñ€ﬁÃMoV&P·H¸ Ì3)Ö´ŸÚV¨-‡íñ;ZÜ·1öé<E”ë∂"f÷fH€z1P©Ôq:ÒK˘ÏS±cΩ|<Ûñ¬˚z·|ø≤©FNXô3$c÷:æw·e˚9¸yëûfxÙÙ©—Tùæı”'f&äèîÔ”&v÷Z›≠ﬂc∫æ‰–'-IUD±D#)¶ºÛúæºÿIhâûòÒÃõ’”t,B‚…Ä§õ˘-I]ÄƒNÌ◊5`î©÷^ÁRRi]p¢ùEë0◊í”®g«¯åJ´'˚YJJ ¬/Q®∂íXJ⁄xÃÜIJ∫ó_<¶R™íqñ`yÎ*}ú+iïÉíwpÛj≈`c-tH”ÑîµT0;sÿJ·á(¬æ{çã:fxàGÖµ¡Róéu¥xSﬂXÙ∑¨¶]ëSVq©√ﬁ‡%@9l≈˛41Œ≤Jl¥Dâ°=¸Ëx◊˜˘Üj˜˘1·ìhIñ£G+pA‰2*˙jÿ…≈5wIµ£K÷÷h&ıMﬁ¶^ííömÖ·ﬂY≥£ﬂˆ	’¶“pztËL[iJ9ëÍëåLîIMJ-d÷öiR…⁄ü¢ºë#å ù°¯‰Iw‰Ñ?¥7V£ã¥¡EÆ´ˆÌØT˘<ˇûXIı<kvôûﬁ5Gﬂ¡xÔüñ"^qíã˜ù«2_L¬˜K÷∂È3ù$¶˝;K6Îp4˘Ç=$k«µú1óSÜáÖ,eóÿ®Ü
YŸÔ,Ÿéﬁ•@§ôZ£©˙˚±f3oã¶wÀn;ôë¶µZ%˜ŸZ…›N’ :Æ÷à;â3fØo_H„'m©Kºí>Z1µí‘n:WP∑õCëó^ÒÆ4±õ¬ÒØ¢cOkº<¡ß„˚Û%ÖMıüö≤BUB‡ëı€‚(ùRqèHøm(ø≤¬NÅ≤—´—õ^3!—yâ=aˇÈ+[…ﬁp∏≤´I9´ ,ã#v¡¯¶ÑΩeíÈË—$|ÀÓµ’|^YÛˆÅÄ≈“P+òynXØ5Õ∫y‰üq?p'a=Ü°ùu~K¯5:ÊB>4îÔ˛`>˚˝j|˛Ó¸¯£9†¥[Ï}∞ﬂª<`Ôéè{Áﬂó∫Ãö´»ÆV-ˆùv,µ¨fdö∏Ùûõü&¢ﬁ(tAÃÿÊÇ–å`.cßŸM˚!hºXÁh£™Udu”˚ylÌuOÉ2£˚ä„P	fB:ï6'ˆqñ©ï)7Eíhπ¢˝[Ω–@ØA÷^î(gˆHÜ`)ª∫ƒ˙k.ØÏfGOX¥ñ°à£ûCÉÃr˛›R÷º},§Ë*Î8]≥Ÿ∫¡ÂßÏê#Uo$üAÿlÓm1ı6välØı9-¥Ø6Ù…‘¶∞ç …π¨K∂lÿíΩÎfM9s¶ä>eÛY˛um<=œ{ë›jl%?rßëåLcº¢¯Ãw«hœ"ÜQJ{O§gåZΩ<e…ÅñØ&Œ\¡>u,i`RGjP∏ƒ[nVãåõß~¥ÕÎÇ~àﬂ˚7+√dW≈›wÈÂÆ„Ec∆c¡"b!u~Üèïx‡N‹ñYmªé∑¢ÔπAÿƒÃ¿3!†1Ã¥!∆∂©´©¿#!≤ò5S©÷*)4àÓXNI»pú≠b»B∂qYÊX§Ô©í9π‘\*.i:P‹(]_¿†åAªvU&,∑*√èZ€-ë√âÚ,mKúYSƒdc6°#‡ ≥R´ÆxT Õ€®òÙf4˚/N£–s'éµÕêN¨È{u:÷∆å≠ÂÔç∫®öA Õ(j2è‘Åñ<•>Ò5üEÅ‰™U≠&3å;Ùaañ¯Ù%£Y‰€WqÃ^xî)DöıU∏¡[Y¡t◊«∞£Ôˆ^€6ŸˇŒ⁄FÈYÀ∞¨ú±ÏÃ…µ˙∂ú=≠Ê7frÁî>∑ö@ìvUMª®⁄F©ìÜ]Ä†¢Ô;±‚∂òqS<±-}¿ÏMDëèSÓ≥/V®ZACÓdÖ“Å/1w∏≥Ù'\á∞4	ÊR
‹¸©«Ù“#IÇû∆3èê˙Ü®pF;ÜÈhõùÛŸ—&œ≈ÿTÄÍªè.9‹ùÅ–¡Òªm´A¸È-ªñ>ˇÚ¿x£lo\`NéÄ9]O}∑t®‘à¬‘\¬.íYH˚Æçæ|T±1ùëÜ˜"ò|uO$	Ë»πv—Ú≈ä(67<í”ñ^
YuÖÌ;|à˚¿¬|§õ•óÇ|Vÿ±„`‹Ö¡Åh˚ºªÙÚL|AﬂY¯ãŒ+U ¬^NCΩß¶◊_Å˙$W™F|yF∞‡™óúŸµóò0ùxw¿/ﬁ˜ú·c™›≥C*ÿ˘£ÚW©ÄﬂÉUÅ˛¡ı”ôL◊UŒd*á˛ßñ∂Ä∑0óÉZ›,óïÿÅXÎ5˙„eqnÚ›… Xk´’¬MLa4ˇ õÖ=•Bä}∑ÕNª≠Izpfª·?…~k‚^—›b≤]—NóQ,ÚO©=˝¬„‡∆aGÑ£„[Ü∆&≈U8a[-2«=„≈“Z#kıß≤ƒj€§çÕHp≈ÌÏ¢qH#u	•6Û%‘9Ño®±ëV{˝È4@¯ùµD‹ª©Èt<ûN®P-õ82∏Ö“{Z‹MZ`3+9-x´%ozP÷)6úMâEÊ;,<ˆUÇ[ª%œqÜjP1⁄Ì ⁄ÍRL+ ∂ày∑Bﬂ[Ï&ÒTª£z˘≠¸"™·©RvÌΩ¬>©Ó}Mµk2€H:d“F¯aΩv·zíxÃÉËøàﬁq%’QË¯û”wG6˚»ãZ&k95b˙≥Ã±(ÑÌ¡f±ËS&A ;5'åÍµ>sW‰Ë,[ÙDc'Má–˚≥”ãKCo¸€‡`õ˝Rì<¢y	{v«~6~KÑ+ÉÈ§voÅã}õΩπ8=idCË^›’Q⁄¥Ì$< ﬂ≤ú§e1˜Ü€”)3˜ NëÁŒÃày”ïbï∂«-âÖŒ-É(2wFtì©ÍKx-S§F„5ËN«`KTÍ>ê*u!PüﬁŸgu†@0hN¡¬y≈Ò
ÃÂNlÇÇNÅ§0€tkÁcxbJiÁ‘vè;‘¬PŒ«≠…r)
¬È¿ïÎèÎw+‹J]≈cXP≤gP∞7Œ'zdw¸∆ù0\ìÎë\ƒù`bﬂe7r?˛÷ w ªÑçºâÈDˆc[_Q–Q3◊âáﬂ¬qæπÁG˝óòÀÏº=∏<®ôñØ÷ëá¨¬<òπV£˙Ãø*’ß“ÍLÆºJ’G∏æ„ÑÇπ¡œ£.l˝c÷Â.}ı…≤Äπ≥ {ÎW0≥X'#Aø?‹upôπ√S—.Œœz˚ÔÑ∫QÖ»≈àñΩÈ©˚éLI–∞óì˝o\#$cï∫0B…IâÖçà:èÏ9f©j_Òl»›Ieﬂ/2Änön	∞Qà‘¿
íˇÍÊDøNØ3¥ç5VZ4∑§BÊ;WÓÁBg≥3ÿXœƒ°7Áä∑'˚íıæ2˚∂Xk	tLÆÓb—9√îYûàWÓ0ˇöâK[∞X©}«?Zt%
+Ü ?eŸì}í€∑W©„;*∆‹ıœªÓP>õ°1ñïLÿˇòı¸)r¸ªRìN˝ûÁ’k∏ „	yøŸ'µF|Éé∫$£‡/é»§l∑7®≤˚%ß´ìO∏~1k˚≤u≥Çëdt‘Œ.‚jÂ≤?i˝ƒ¶îÂæ}nÔ20ﬂ∏)£ËÆ^ZÜ¶¥{6 Ò ïÆ!`Å¢¡Rm_Ii¢ }2RéwÕµ¨Œ∂Ã∏‰1l%¨¶ìü—.°õæ£Êò%óöe—_u+i{xÈ˘çn£©1qZ#[π8Ö)Ã‹·∑w«T ¿.¶~à˘øâA ¶eòç¿ìD eÎuæÃ˙t¿©'‹Õ$Äêb¿Ÿ√äæSÔg^õ,•gÏOÜÎøú—NåGl±s]eAzõı–£å¢ïsŒ§≤†•=/ g=Ë[VK
ì√^9!Z›µ»RÖ|ﬁ“ç\ß–,YÏ’Ït˛Ø¨RµP…C°¯tl'€•ﬁ(Uv<\˜yW®/c±T-Ç_‹Hﬁ<Ha.f‡æ¿öö\™äM<Úö#ˆ‘£ƒI/ÈeÃK-<3ÕzÁ≥êzH‡-B◊jv§€‡¢;¢∞ﬁö√Ë-èd(D>¿XàÍW2%Kµ™±ò»kn±Tä#∏π‡'ﬂπÃ⁄,ÕŒc“ˆpGÆØöPŸ§´≥W _ÀZÜ'°2+ íÛß8≥ipë’ì√^+ú^ú ìWC9e]÷y?´j_’Ó5i P'ä:c¥ˇ⁄<`◊ò≈∂f6sŸ$I/ˆÖ˜ˇ)ªä!y(¢ øì,!U¥»W§¶¸ﬁ–7[∞–/2*s†Yåœ‘ÿÃc|VhlvÊOÔú@È¬Ò?π;"rÊF;≥Ø:bïÃ√t≥∞òl™öÖâhèdˇUl˜uÊ‹8˛Gæ †å¡¶µv∆—Ä4ı≤õx}UYÃÏ(mr§»f.ì#eb¥öY[Á¬êÔ£eÊM1˝∞¥5z^©âëŸRFˇ)ı∑¬“£'v∆ùíÎ1µ cö}˜»^ÿCàPlá,e"Q
OZÖÿPhh
;är…H§¡ƒ∞¢ö2)AQ7∂êŸÂ-—›†‚ñt˚lË∫Å[3≈π˘€≤êÑc¥z∞=ÿÊ„Wo¥ê∂ºBœnµPpyzü˛YŸû+vØE»À√	˛˚Ût‚‘√üÕ¬≠Î:ç|∫_
 ˜á µ¬)‚àˇ˙√=›Çßõ9¡†M¶∑•∑Q88¶8w0¥O∫Ã®kÂS¬^˝Åm¥·ìãIÎ'nKÄ0zè˛¿V©nªQ2`Ó"É¯¸Œ°Ä£A=îòã
S≤[MAΩ…ı≤«º™Y¡†\¬\2FãÂ(º7◊[ùµÕÕÁc∫uûµ÷Vª[@áG[-s`\cSE-≠∂6û≠Ø≈-u∑Zù≠Œö÷“3KKVà≠n{k+Üÿﬁhm≠≠oh7sMWhtø£—0wY∏Ã⁄ÀπsrçªWW2,k
X¶b0J˚"l–ò˙ßªfânqzıΩ∏¬§ê”WﬁtÍ◊©µ	Õ)Ω€B»TÔÏæ’pM÷iµ cucΩ’]7õ˜<w¬ÂV›nµ€œ:[P≥›Z}ˆÏYß´`¶A=¿ïe6€›ıMı"p'¯¢)*oÆoÈ5r™^ª˝¨ù™øµªœ6ı⁄´È⁄ùµ-Ω≤zkÍñC°∞∫›g@x8&‘x{s]A⁄⁄ÿ tå0XÌ∂7çÎ¨mt÷ç£◊kÌ≠µgko¥ª∆é…◊[Î¶éY¬p˙|∏"®4|™ñ@ì’qq¨∞Œ:N:¸ƒAX¬kµ≥êí¯…¿»zìkœ©s¸◊Õëüs∫°ƒRπ≠`¥‡Ï Kj¡‚ıpWÆ#Ù∑ãx@∞qÏQ¸@¿mËÉ¶rIæv¶0>J6KRÑƒK‡<ùVÔàÈ˜ÙV« N`ÔÒÒåÆsE¯x˛π0Äßƒ—‰È¬3µçπ˚à.pDX≈N––°+çÃÜúqÉ°Âràmî∞ ò:p¡≈
#Êé1Äl|ÊÄ‰2ŒÏ±;âB£A∫¿K’&^hË8]IIA„Ç)†HÕ34ÀGU˝q‹x™⁄Ô°öyñEÉ/≤S|}∫céep*≠~@yl5É
~É3,µó`πäG€‚/≤∂•m|¨Å◊
´á€Í[Ò]Æ=ÙˇËS$@µ∏õ]¡eÉOóÌlŸ ^D±ŸÍ•Û‹EÓMQ?Ì;ú…≤ö5l7∏„È¬OnF3~=Ú›~Æº¿œ∏Õ˝ç"?[Åÿ¥ôC∆¸kva¡√ƒjÏCAˆ™où˘eŸ(„w  +Ïæﬁ »·»!êÖuàø”c|ö`”»réÑq¥4ÔTdhøÍ¸¥†n	{ïî$±Tg¯,>R¨ˆ[ùñrT®ŸhpIXH´ñâ ZÀ‚i§÷q¿oD·òÃÅ":m+=IÜ!«)~a>v∂Ÿ“!¬\¬ì+˛‘XØj$Söá¢xOâÜ
KÃpVé^ f&Ä
Â¿æè˜E'ö@2Õ¸*øOK ?^l©ÇhR∞á3`Çß¶∆Üo9Æ«ÇaòÄ'º$ã-∞íb®á¿≥å&xY
^ín)≥1kfë7ó⁄Å[8∆„L‚µ'RêM‡§Ú<[N%«˘e-¥«∏ß>ø¶£ÕaËåÎ5X”◊◊0é¸GuíÉ√ˆüˇåGπ›•<º¨	f˙‹ù?‚“ah_È2gcKød*ı”ZÃ&«FdÎfn?Rt˛Y32çGØ≤a¨ÊBÌ`TzX%XD§ÿ¢◊s?9Õ°{ÌÜAàÂX¡]˚tﬂ¿'@∆eõ8"vòhßNôˇ*¿ƒ≤∞ †p!é‰⁄]n4äTîí`uê¬ÜY¢≈~9iyD"£ºïjí4M©6Ùa…CëÚ„<pgêásA∑U·àn[≤H@gÔ∑ï©0‡ˇ¿Ü‚æ£ƒñ®≈‰òÖh5=P|È¬ÅÉÏ˜«≥q⁄eh‹¡◊EÑ2¯",Ï‹&8g¸⁄5ÄâµL
Vß£[7ª *øÆ-Ö⁄ñ⁄‘w,=Lhª°—yfÜ‘ã3e‡¯∞tå¨8ÈF&ÓD|ÒxÄõ…“9Ô”´#>vë•∞£2ºFŸ§#	xô˝ä¢§∫éHwv|êTH	K
ÍÑŒI4Nà÷§ÙÕt_6"ÜB5#-⁄≠…ËÇIC∫„Äç?∂È¥"ñï,«D¡MNá∏NECTqM1É™%|àSø òeñ ïSÅ∞∞'T¿m@ §Ì£2ƒeÒ-∫m/≤çßV$m;R5c¿WﬂÛø˝øq‰ClØ‚`ã[Møˇ wáπõótΩî–bºI%“b’w®®’™+‰FgcÒJ´æLZÍJ¸"ÌW¡•èAN„óNÉ·‰Íüπw˘‡ÊöîJåêªu√ŸL9–‰ „”`vª4_Ï˝*o7••?˘øÖAŸF	÷x´Ÿ.ÉÚí=kı¶{ﬁÁÕ±… AZx·qÄ’ﬂ]Ó=›ldvC∫˙9u’î©yŸU∑J™jääœ∞¢Q•+™·œ¬(√‰'a4∞[é2"ìÙ~ú›“Ó´' "22¶	ù,ƒFÆçä&û>C:Éı7aË»»†Xy¸êÖo™Zëø`Ø"≤I÷<}(¶•—àﬂÃïtPé*≤Ë√]=à.*”ƒÉ¥-b•ë Ã:∆õ:ÒVw:Ü8§€˝Ö‘8)ªæü[·ÙÌÙ=@U
ºÉtªãê*Qû”>¶H_bt’T[8ÔjT∏≥ôÜ£∞Bnáµ]#W‹üàgú{s L9#8M‹ÔJYêƒ±8o„Jç9¢∏¿Of;ÜüIÏ30!≠<ô8¿"F{,é„È„™∏E ≤ßsõRàÚE\NV=4Ñ§–∫(^`)eXΩmè0ìd¢ò©<
∑Ãõ\>√ˆ;û~8¸úLÎ
è2Â’g»oı~ÃZH1ˆR)Âƒ@wPµﬁ–iWOU„#7Ä˙lôπ√œÖñ|np"vı∫+õóM€ΩÆÆ˜êùê;’M˘Be3)å…ø–©†J~6ÕÎí_ß⁄∑DVQGÉfGzÓ:æ{Öì;.Ω`jèíø™πF˛Z¡∏r0LÃNï∏KÍÓñ ÈIåÛΩp‚*s–“èU*Éy7ä{Ùï\O˘JVéIÁ Ê*M‘ÈË˛H3L{‡îÁgBR{\%7äªpß˘»àV…•Iw&˙än,Û$$ ‚ËÙÛﬁÈøwx8rl©çq¢ã˜Ø[QüdáÊòª9BÓNŒxVñ¥V’``IK ‡’``I3å+Ñãj@®®%’s4Æ
∫C+2∏¡VÉÉ%Õ0‡‹TÏç|'ßÆ'1EÃ≤ELô3‰⁄(g¡ˆ
œ[ËV"vËL[ÉÈxÂSg∂"g¿É[t3£°≥#MüxS84”e¯Dnë? NÏ áqû†ÿª€Qé™jîÏéó°ÉW|‡¸®w÷å∑Ñò2¯ı5c_£(≠W¬®*…œV”#ﬂ
jõçäíıàí‹mø9>á	3ò®kçMáé® °„É¢
Hn≤|fé3¥Ñz¢¸¬∞bQ∞®åÍwUŸå‰˝,Â≤T\Ä9jˇÖY¸x±√Vah”ç2ÿ¡a'Kœsçºi4‘ö≤lJ¢Ä»◊÷ı&◊∂Ù6°µ#ﬁèBks#˛≥3g{Î©.nlÍÌΩé>¬˛˜
§ºO∞5:Ù›üˆÊmw´≠∑ª’Õ∑ªcko’Áîë∫õ≥·gÎ˘¶Œú–ı≠Më7,fgç∆Õ€ÏÃ¢∑«ópŒˇŒ˝ÏÎù∆˝˚ø˜*b¸à}+ZñmQô‚Ko¯ú$∏ÖŒÂñîEıáù≥`*!•Nˇ)Î¥∫çjÿSÓ^R«~y«ÚS"∆#n“ï„á∂ºl«6∑~oÈîÿ⁄,√è/ÔŸÕxediLnjŸÊ:ÌNóçŒLô	{V~ÕHK´Ì÷:§¥ë≤|'ø«ÃI	f*X“®`µ€jõ14œÕ∫inåÛ≤¥⁄Í≤ÒJ`(oüâéa&å lnøwÒz˜¥wæœﬁ]ˆé{©Hr˘÷õÚa¨% I∑E≤…ÁNyaZ&ÈÕ\åµG`«poP´‰DÇÇ÷Dã˝˘œÏó{[%78Ω∫¬h@‘r¶˙ï?”sÛ<=ãBû/,„ ÚV˙™°a∫Ã1î39êÕfﬁ›ª UeS‡≥6OMXGg„˚ahb&*“#Td]®Î!Ü`≠'˘«1#∫c¢!Ré?A»«≥zzær∞Ù 5F«/OÕŒ∑Œı‰é'úÚD^∂Ÿ⁄güUÊJ{@[pîl|$6üb•-ﬂ§b#SLqÈGÓ’‚‰ƒ’#ôi0RÊ
wË|rN3Ü"Ödí«5Uî’ íU-Ω<FCŒ,´ø’Æ+Ÿ.z÷Çp^9÷ãﬁ@∆MXÍ
t«Œ®’ı0"6Ò
N~3r}v«'◊Z ) äK˝y=≤,Y	ﬂ≤,pWùän"è¢ÉjEû≤ZCh≠Ó[ÏΩ”g¿¨'Ëñ:!ü1~§á∫3Œn¢ià—t°ıâ∆¨äGa·‘HÙ∫Rñ*"µ(“-™*ví2ô≠!¸á˚0√¬Ô÷“yúkÓﬂ†{Qtó¢<O2‘âπ¶C|u~ppyxÚä=aØz"`jÂ¥¯OÛ÷áÈö/é“ZÈ
-_Li›^Ü–S1i¢9·¯Ó$ÙèéÓy´¿•ó ,wLSE’7ZÕ)˙í$éRykäJ£ˆ
Ü≥˙ê'ˆŸ]¢À6{”;f˚áØ/{oa¶œŒèœ{Ω∂˜Æ∑◊´4Â"TÌkEáÕıŒ/ﬂ±N∂YïÃ÷\∂I-Ñòw]ûı¶8åÿö#¶È®+f!¸"©?sÍ˛”◊¶˚CP1°fN∏â6ÛƒŒ™Ü>N˚€√ãKv|pÚéÌüüûÌüæ?aˇˆÙ§«ﬁ˜éÄäf=Jri\0O$≠Jº[çûj†NØ—…∞$:Zí¶7bDH—™‡,·œ˚,ﬁÇ/—F⁄Ï≤≠Wå˚.o:üa.`¬Ω πâêïœánl≥≠b…»8í∏ˆ¶}G%Íá¯ÊmGb~Uœ‰)∑˘önÉ€πΩB\^Y›–ÓØY˛‘Ê
ÈF˝´ı,ÆyY⁄"k
ñ˙’t&jäÁ»(à—Ûƒåí’åÁ˚wybêì;v'J;m≥Ó∆ΩcôÈÓtÒ·œ eùœoΩ=˜Ù{nÆ≥¬j,ª1ö∑œÇ}gÆÁéú≈8{¢Zƒa÷'N‡bÑ´ä1Î44Á´@ïxÆkd)§[≈¬YWúy hz∑Üøo≤Øân•åêßñÿ»wÆvñ>ÚO<¯Ó,‹˛4uáıvC1 Åƒ≠€◊$¡å%#5XîÖ™∞˚ŸólSµªxmÇ` ÷´3Û&÷π∞âcj]ÿÃ2r±2çâÃÀBÃ(àñEg(±62ÇÌ¬A)dı4©á”‘2CõËeL“’Á>˝Ö˘∫nTí6—ÆñÇ◊Z=;Ùd•%öjGb-ßÚ:EÀd)ãÆ-2aD!å∫ÓÁà~˘H#Òb≈í„Œ^a^˜b‰Áx‡Î"_V√<T…ÚÀê2LÑ˛«|éôàmøBñyâŸ}G»3=~Î`¿∏]òîevrπãˇ\◊OpOw,˛ü˝G·¢jô˝∆1ˇˆ9Ê√f1ø¸ç]>ªTß÷_#∑t«ëè+ºË&bOÿÖ„E~4bg|˝„pƒÜ¯b%™8ëÒØXl.=)pd∫YMÚv e÷ü~÷4g;2Íƒ|H(ß pJøS.îÒt2Ö:pD4x‚ñ˘®K/€Ìm˙ﬂãïQÁAZ=óLNGøåoÜ“F„≥ƒ8*O≤X˝fı∞∏ü˜º[tUêÓı,Ô$cÎ…t¨º2Gpæïr[≠È˙|öuÜG÷ìw⁄9æ≤”çöéÆ=◊èÇòÀÙìåD°‚ôÏÆ√*ã7È~[‚£äÀ.≤(-Va√íƒlΩZÜÓ‹Ä8∏¨Ñ>,”±‡fÖÜ4o—]‚. ¢⁄•[•Î›*óT›Ì ∑aTÛ_O}ç+)[∞Ò
-hkëÇö¿Ñπk⁄‚bmŒΩ{~π—ê¬D$9s¸C˛≥Ωàxy™í‰Ç’Î ç§Y´ø:ª`!«w√˘Eµ9§îyäŒo˛ ƒ9Ì,íÛ¯(5|>w"õjÎyvÏkAO,ï≈ó¬<kÓnæRy›U]…&»òXÀl†•Êt-Ω"´¶ÜJ5òôÚÿ™=5!±7œÚ˙r˙bGÃ£nÅùR∫Sh¥ªÙRÈÈ.–§È$õM£•∑âÜøK/SÜæãWÁ”yãgÛ3Çê3/[≤O©&røÓ\µÙR≤‹GfÁ©Ü»$zÈeﬁ˙+0ˆπ˘òÃ}àb»¬ ª)Y7úŒ™\wœgHc¬Ä<Nƒ¬∫,Ár˚\
u{ZXß©é¶CZD©@ùœë„9„>Ô”ä^Lücg'‘G·Zú¢gÂ¶∂Ùrs˝˜ønNÇ¥Ùˇ  ˇˇÏ}˚w⁄F”Ôœ_±qO<5¯Ó$nùåIB„€∏y€~9© ™©ípÏ>Øˇ˜wfv%≠§ï¥¬v.ﬂ)=çπ¨ˆ2;;;˜˘qâ”»üCÅæ\j.&÷c†Q∞B&!‡6‰Qˇ`íVüIﬁ	ÉmåKu∑ce+ø<BıÕ+cÒ(îI,QÅQA∏Ï⁄À ¥ÁÎB´áS¿ÆÓÀ^ËÃu ßø≤Ê≈IÁ]≥◊a’~ÛÙ¢s“<c=åy∫hw{êP€¶ﬂa«ù®%<’ÎúΩyÛ∂Ÿm≤∑ÁßÌöñ±≤¬ØÉ5ÇwπoqÜ˚„ˆfÈ»êáUãîPá`öœµ)hAãΩO»≈}cpDy´ï˘ÌG√q<-;ùZ7õÙ4?ΩeMgfaùµbM¨˙Âjf÷‚*ÆhuÇçÄÇ∏îÑéÃ1zPÍËûXSLèä∫Âp9: JõÆ]¨Mui5ıJ≈:à∞i!i¿O˜„πSíàÀï∂≥“ﬂj∞#Rœ^,1ÿ∫–;3%Ãé®Â]REô>´«e~qÚœ«Ä'©Ò‚hyeƒ¥Ç'ﬂ˚kIz+6ø©KﬂÊ˚!
≥>¬FÃl˜Ä}∑›⁄kÔ7À¯·¶\∞]KòïTrñπz‰Î4–ŒáûÎt”ÁæŸ&r+C˙m
:2+aÔ6`Øò¡ﬁ`XÊ∑Üª8ÛèxüB‡pMü}w6˜∑wJπëßË)/˙9H|¯˙K`ÔNÉ5gıˇ,›ˇWÅ}cÿ˚◊“5IƒçñÛYwsÛŸã˝£˚ Óüˆ“]3¿!˚jn∏Wﬂé`˛%px∑¡.Ã˘`yµ¸ˆPÿ	&ûD„pEüã€˚{[õ˜"øXÛ¥æt∏ÌÏ€@ﬂ÷_{˜ÏtÈYWﬂÊŒq“
¨]Ç$¿WÙy1˜≈V{Á>òãÇËõ Ü√GﬂÚ®ø‚Ó7Xœ°TãﬂÍz|⁄I‰VsπYüôÓ>ﬂŸyﬁn›{π´Çk:¶°≠E˚˙"à+¿˝%P˜YÉùé‹#{g›ü|{å√üƒ,~\ŒóÛ$'óˆô˘‡Á;œ_ﬂì1≈Q›4\‚Å=«5çë75Õo´†	¥~ﬁH®#ø5§N+Y9a.´cMNkut~Ω˜¢Ωy/±é‘i;7;u‘ª;"] sÁ·yÖü5ÕD;òœÂ¯}ÛÑıŒOö˝U≠=ªÖ)d.—–é^*∞utÕè0"q\kNyÙqUﬂi-”ØïáıÏô·Î;A¯≤b“fc¬∫XíWÈ‡çû›⁄v·˚c95—tiW;£Ê~J«`(G⁄Ωub⁄X‘VˆË?±ëƒPæÇÉK.£Y)ÀìÏ¡E∂ıTySóÇ@†‹≤ü{˘ﬁÛ˜¢A'Ì3ÃO÷9Î∑ªÕw˝ŒkVΩhˆö›Ê–•˜MåêË¸‹Ì<îö2„¡ËùZ/p‰¯÷ÈV Œã±)ıÖqÕÇ˜¿.^ß‚M[Fº¸^}Kf/fpƒNåŸRŒã˚>è)∂3sÏóˆœ≥èÁ§‰æ‚§ÎXIPd∆Y{)ÍP≤ÌÕÌ}ÕP¶b»a9í»• wlQPAéˇê	:≤uóÅù&M}LTì]
 ^ç$`a>˛73{`Ãö£QsÇ ;µG∆LS‡Ãñ^}6ëânﬂòå)„>@¥¬ãﬁ∑€Ôéõøä¨ät1Eú∑b˛ 
&3rœ“H2óDd◊™àIùaö©M¯ok∑±˝¸˚Ÿ‹∏	RTâØ÷^û¢„`)lÅÒJé—3?«≥G£k}åw∆¸—«¯y˘¯cÙ
a•uËZƒ$4ªÏM∑s|Ø#EÑw…öCsÜ.˛üô-J~≠*πùYM≈7€Q©) ˜§z⁄ÿ`ùÖÂ[@·ˇ6YÀvMˆﬁŸ˜RM…3)^OU-Y´æRacY}Aõ‡˙Ló„À…•øî˛HE¨!`/FKS◊ëy3§Ñ≤ËÁcÃ4Êò‰±Tt=;/ﬂc◊≈PáÄ<`È|±f‡ÊJÎº‰òë,ŸÙØË]¯6ÃRü‘¡í)≠≤l,ÅHÆ”ØÂ¯«Ô‚ä˛˜·Z%®S¡ªº´¨}H÷°G∆]«Pi¯ﬂ6OößËè˙∂Ÿm≥ÍõÛÛ7'mv‹Ì¸“f≠ìÛÀc÷˚ı¨‹ˇÎ|-õo⁄›Z,[?sxS√5è]G_[3* ÙªTú;l—Vp8˝.4QõNjÇ 0GXm€ê2GŸ–v˝sá@Õ˙¶;0‹e^èù’4?±ûÈÀh∂Ïx'∂1‚E‰)[®≤Õ•3+l≈€\∏ˆ©yâæåF=ﬂ@Ü8M«Òzî£Ê“M÷Û]¿IÖ˘√B}⁄ \Ár&Sév¥Àq+?ˆΩh˜püÑ◊YÂ‚¯5˛yÉ,ùãÔéÌ+DM|€t=À¡7' ´-nçä¥ΩrﬂΩÛnˇ„˘Eøs~∆{6fùˆœ+æ5X≥˛[¯˛∑zﬂ_^°_√áLèO"˙Í Z38}¯∆∂Ñ†á¿¿ı˜Ä¥V∑æÈ%è’∂¢_0€ÉÂùgAC¯Ã¯	∂+(|≈*õL]rÛ
ñ∑µπΩ´˙’ët*G∏ÑwÙÔ)˝˚Ü˛ÌU>®ƒ2Ïß@ú„ôDÄﬁŒÏI0≈~sUKP 1_ﬁ4hÈÿü™WÎÃ™…b0;<%åßi˛n}Pà∂@DB¿∂oÄÑ{mM}# G?PDúSÇ1®”<‘Íl$+oDu:}Øm'˛îΩd[Ïï¯ ±ù*ÆÚ5ıºZ/f´I\¥@¨Ö€\åZ®C◊∏ŒÊp≠"ERSs^≥ NÒB¬*PÃyôãj0Â#©‰>à–¶—1Mè3S˙G|Óÿ·l92Ω*}]S›÷¢ˇ•Z@ ™§$≠? ¨∞)TæÌlè∑«Õ`›…¿®nom≠≥›g¸ˇÕ∆÷v¶¯Çå&;µ
ªÀ∫[Ée˝^˘õSó”Åg„ø>ˇ0˘ªÚ!Z"@†¶X8>Ø˙~hœ)æ≈)∆!VÇåóÇ◊≥¡÷ÿÿé¡kk{gùÌ @∂ˆ∑≥ ñl£	±õôá–Å?7¯wË]kÄâ,îJH ÉÖÖ◊U ÑûWÇuôÇ“∂˘+•›}X¸ˆﬁ:€€ÃR¢â&åÄﬂ¢
?ˆF8ülWç%€∂∞€∂^<€≈°Ä´€⁄zæŒ∂∑2¡êl£	á?ù	¬·O«§øŒÇ˛|2tË&ÚäwMﬂÊé¨¨9œ´¡ÉMdº˜l∏π».Æv{ö	èxMp¯7>ÆÆVÇäg€s3«	UÊ#çÂ£¨¢DÍl%∞ê¯ìÑ Óﬁû±øÉ ˛ãuˆVªµô	ïDìB®M-9´ùÕùÒã¯≈∞8πtlkÔEÊé7IÃJuY√∏ ¬ç¸xﬂz©≤`»èY§—h$$î	…ƒ°-tA@˘ÑqÂ5çîBïî ÛÙiJ∏iÄ8:Ø*∑ú_ı	(˝†|◊ãnÇºX˝mpêT«X√ª:¶B J÷!BI>…@ﬁ• ≤›`ƒá¥≥íÅD‹ûÜ¢õ
ŸÛœ+íöÀf5
ô¨xg≥5nî`∑ÚML
Q!€´≈öïBh·(èHÀKœ-•…….gNd≈q|Òá>
2å¸≈:rÄ˛Xc —ç\ä…AVû"5“
ÊÇ±¥‹œ¡µñûe µgU!éŒÖÂµÏ˘úî*ø/É^˜ÿÌ‰¿RØ_¯OÇ…´[™+—äg±ê¥¢éh!ç‹Å[n"k1â˝¬)õøT´‹z55qã‘M ¯Ÿr> Bh4DÅÕÃ˙äÿ‚(zd†ÒH˙œº"Úû≈·`†“œb"K◊h]”√"v®â;Üœ’ÿµ†≤#âÔõyù‚ÑYùr;M´⁄IZMh3ãvcYóPüuPÑóU⁄VÁè≤6+Í?Tí@[€òaeCNÄã®“ñ˜èä7≠˛è˝7s˚O*Û
á·H]ÁÁAØgÆ‘Íπ)z> Î9PW™{ôcc9Ûı6¸(æ·Õ¨O‘*N0]AQ≤∆äjπƒ ï™îÎ?&U»UqÈ@¡ÂœAîuT§@©ÃÊ≈gc_aQúOnó¢uAc”TBÔ≈˝Ø…~ï0HΩ5ê|,Õ¯qåR
∞)5òS√„L7ß$Ì&¸vyô$«¢»2±ÔÊËuƒ‘´%¥ƒ∂ÊVÂ%+ÔÈ˘qÛ‰ÄΩ;?{›ÈûbZî∑ÕãÀÎ5˚óÏ®›}◊Tõ~cŸè—ôÖççë…Œ\$K	÷ ûô‰Ì≤∆`á©ÿ·ZXhljçFÊvXÉ£tj§˙»2fˆÑ…Fò∫Óm¸YÅn≤]"{À€rnògœ¨ª6‹jΩ.≤∫êP^+Ú¸N9∞G∑‰CºzG/wX¶‹’w¬Ö=*√65—ß*¯îˆdÁZùùÎlˇ9ˇ≥±µW˚1P>|géw·ı#ì]¡˚Æ9◊u~OZñ}◊¶;<ô¶¶±fB"•›ˆ⁄À∑Ü≥Ù0≠‰ï·Ω“tås2RJqG&Óﬁtå+ÙœÇ_ÿ≠qe-òÖ˘…Ä-&Ss@c≤ü»´xÎí≤Ü&OÕkQ∆√üy	Ñ‹µÿ€F<ëﬁ†Ç±c∫@·Ã≈+§_•)#À–
yë∏x)…§wúúL~}¬<snÒ:}AÖ…ëÂÕ≠¥¨Ω<ÇÔgÂ™$jÕE§ÂJO$t⁄3oÃ·2 `ÇòÑVÂ∏«ÒYˆ€#|+ù¯M ª·d˘$Â^∂óQWÛV8ZŒÆ˛π˛πæÆ;eÀÇ~ﬁÀAu+Æ≠äs”Ç-Ú◊^näK%∫ |1˚nä/sS‡Ê$Ó	ØƒE¡√ø·Î‚mÁ¯∏}∆ùÿ:góÍ¯∫ü¨Ö≥Ù»Q‡ñêA∆∂:ÿhçÕó3ﬂr ç˘ıs¬l|Öp∏f/"ê∑¶∂2òy∏â9	sÔ(9 ®⁄ræ‡iÈô3®Ôe›=")˜ãgΩ>HJ=÷jv≥˝u”îö«j‘äπÂBrVL•ü{[¨∏â˙Vö˚·Bﬂ‘ÇL‚®ë´ª"õÀ‚÷ö;òÓïµp¨yDM/†PL8á£Ø.4Ω-RÀ∫”Â`	˛ åQÂ¨ì˘s:Æ« ˝ØHÜü]œ}NøHiy¬ûû91‡ÚY'5*¬?ù‚9Õ£ãÖ±w€÷l°√$;∫Ï˜œœzÏÚ‚‰ºyÃû≤„Û˜gÙˆ∑ŒÖ÷	—áößÂKÎ®√â(éà}¬≤:\]TèvP⁄,ﬂµ&°Å‚‰≥™ïåUy†˘∂,*c'Ó'ﬁ)ñÅ^ Î=”é>‹"ˆ°vX◊úÕ~øYŒΩÅá} ‡;c Ç=¸
œ¿N#¿˘ãÓ˘õnª◊cÕìv∑ >ºú„Ó÷-†s@g•+°ZM9pøbï‡⁄fîq‡G^·0¯ÔÚ±“°V®¢^„Öœ¬Ët·≥ÃD$ÍÏÌ¢ì:º=ﬂBYgsøî,∑RŒf≠`CUrq)íN,XYêr…OØƒoÚ;À}‰˚Ê˘2≠&O;”£·Ñò%VÕõ6ç
j–)\;'Dn{,8=Öá¶ã@<¬á¬˘*ÙN´ÍC…Ë0Gt"T IÔì O˚Ωõ'\ÔÌ≠ˇÉl]ànY>$ÈCÑBÀ1GÒ/ç@÷«àÆ i¿äÄÿØ¡‡ÍÇ>ÜõT$‰˛«µó_6È«nÉı⁄ÕnÎ-;jvµnv«ˆ,bèÄ+2¸|û&U›Ä˚µÖ]
ﬂdæÌ‘˜6y†Ú®p©{–ªYü[£—Ã¨ﬂb±Xë>;ŒL˛]'’ÿ€˚±à=í•*üBµ√}ÂÆo$QΩ∂Lî\RbªÛ .ì?ÄÃÎ^1Îÿ:&\c(ı–úŸ1›√µ™PrÉ3ø∆ÆçŸ2¢˛Iä!π›’Ó⁄•yÜˇπER\’üZ^É∫´e•–ﬂäÈ≠vÈh…˜N~Ü}[eÔ¬W
„X∫j6É2«gÓ∑	g]Ω€fêπÇv[ôjs–À—ˆ3ò≠±jMÖ#L ÌÁ2îË”
ÕM}6yêTÛä‡´ù·=8√Á¿£Pp\ƒÌ÷€ŒEèuœﬂkË’#˝it˙∏{~Å2Çv4nPYù´º©xR/*åôÅc≤∏Vl˜Ì	JëSkdbJN{\Öù}€ıè≈cG˛"“ûÒ.¢π
’ªyw%\∫@0Í.¯.›•èÚ
Ç+‡˙h[¸ E≠ñ≥$∏©†<√¸4n=˛Øÿ,'¢òô£¡må4ñ7≥^J*àp0Æ‘ëœ6Á“œ∂ˆÚ≤{Ÿ◊<Cª˙1&~Å˜§w–´°-»W:∞17ú™Ì¯ËˇñO…‰h5§∆q‘óãÁ)NS•p—√CÜ3ÓQ¯⁄¨p*¿Js…I‹é“√	'Âh‘jƒ#ﬂi$â-ì¯D©áU ÒÉC]M3~xHê|Tr(¶rÊÂ¥ﬁZ»ˆG≠ÒßmHÅ≠/8Øy›µíƒH{ıÌy∑Û€˘Y”¥µ∫Á''Ÿ˘ê®á4πG⁄æ≈Ïk”œ@Ü0´LIû†îæ^°ﬁ É,.PI∞'v£h_:ä>˘Îú≈¥Rñ{ˆ VXNÇ_I¯” …„•8<û è>™>¥fëÖ’Â:vi¶òbP´Ô#s`÷„ˆIõÎï#H’ÊYÁ¥Ÿo≥ˆKß◊9:i≥˜o€g¢u˚∏ ÈóƒÕ≤&˝{d§401?3Y˚Ç∏ü°|â„†<éR„É'
Û˚hñhZâcãëÙ◊d+D~Ê¶Z)pUÏ1*˘ˆé•…¨!áπÇ\Ï(í=8¶∂Qzﬁë»âãŒT	EÌÁŸ˘@+§‹˚∏RÎwë∂KRëªÀa®zC≠.„•ˆÿD*sœÁü>Uˇím‚±$‰F_:ìÑä‚s—ç´ÖN≈∂Ä)∞^‹Ù◊ÎÏƒm˛I¥7/ˆ»Õ#û„,ÄMº±TΩ˝–G¡b}c@&Öﬂ¿æ	r∂∏œ6.boïô»V"å0∂‹yÜ'A IÓ.Ärïs≥y\´ƒ≥˜8ÈÙ˙‹àﬁBﬁØs÷÷SŸÖ\ì	ƒ¬ÛyäÀ∏K¡	|ﬂ“»|ôz®S`ú«ó†íx4i¡b‡ëxéÁÄŸ…IÛÇ≥Õ‡¢ÅsÓ\ÙñØ;o.ªMbDVN+7∞‰’‘û˚⁄Ü—ÚˆídQ §2clº·†⁄"¸ÖÚ3Æ}E@¥åMZaS•ﬁ£¥’Á”-O0ó'eóT¬öﬁp	"pVí≠E‰ÒÚAaÈb≤c·îÄë#å√à]vO níë„ìdIMI°@Î1Ì/áÁÙ 
˜Úìı:‹~◊7∏n`∆ﬁõÑ£
§¿•-Ø–Ìè{ü ä.¯ß≈¿„äæÒ≤-ù¿cœÜ|éóÙiz…¿ •;JΩ–Ò*6GÈyJó¿í_pÉA2∑vrÎëô´ÔÓçÃIê€USÆS7bAM¬aLÀ≤Qd¥àÖP°Ÿ"f˛ò˙æ„llx‘¢1°]iÌ˘∆‹∫∂∑·m4çt
\=ël2É¨g\«ß•r,ƒF≤ô∑GàV¬£P={È√Œô“ŒJ|µÈ˘ÖÛ˙dç≠X>[S”+^◊,«nüéDZÈ®Æñ'8 ÅÕk#;>.eUp
…&Ä™µ€1_R«v˝◊ÀŸÏ®¡œÈóÜh‡µ"CµMOGMÙ„Á‹˜ò≠]”%ˆ±\EÂØí9?WŒ˜tPçódƒ‘ôÍ‚Y	s2»®µy≈°â‚◊∫∂DÈ)P¿‚ub≤0ØN¬çÙ«˙"è∆Ø\t‚0Êk–´≥ë¢ ÂDDm•4ØÚ«Dhò«éºI∆≤êåRıÊóﬁåºòäÌ§¥∑≥∫ÚOZnõRH'âfZKWÏiW§
&—≈µ]G	˝ûñ“ÌvKÊ+˚÷»∏bÕëA¶¬|ß⁄à≥íŸS‚ØxΩﬁôùâ ∫[ê≥Å5ÚñÜ≈F&’ÂÇEëÖÅ¡7ñB„í≤cRÃE6£¥∆ Å‚§ÍœH®£ùL'/˜ƒkJ∏â‡8T&ﬂì*ªDœ\/â9ƒ”/6xÚ≈(EOfñËwT—ÀS√ÉŒ¨Q2YÇZÒôïÑyt≤¸ô4+VÒƒ˚¥≈C(˘BÖäﬂJö—£©ﬁU÷Y'†öÕè“røm∑ﬁùˇè∂%ä3ùhaÛ˙f¬@∫ÓqÙ»ŸŒÖRÙƒäÍ¢Nqj®∫≈π`Œ)”W˚fMG˙9£LÒH`‘4∂YßE6ì„7mmhKxC‰ö|	s‹=ÖnpBÉ…]Ë∏óŸÜ~æiu™}Wä<ß˙¿ÈﬂÈQiPûΩ>◊GŸ»4 Ê÷¢˛©ÿgE~Z“6ƒäàepó-f}º+…≤ß4
™1∑4=¢ôh™7°É {:¿D–qÀeåÄkô0¢|@<»°¨›<Câµı¿‰K@h‹X∫≥Bﬂ;ö™¡¶Æ9V	Ï‘	 ËÅ¶Ô„`f,ÆMDÍPúE6(fî≈›cWW≤IPGFÅ—Z√zû6”Àë=!mkP«∑ÁëzÂo¸∞FA‹V¡√ÑV∂T|&#EÚÎ$+˘Ë‰RáP%x≈TJ¸tä4Œ|ÉxπöARnN/íYÖP‚
ª Hù…åâ›YBıÔÚs∑≠úÈ∑Æ¢IÁ2#·≤©Ò”ß<!:·S”uç€ÜÂ—ﬂjÙõ2Øh∏c±*—CäºSÃúy¶vWøPtp^j•‚—`◊¢j[ä 3ØxF¢¸dD…gÒOPÉâf˝¬◊“¡4aqøêÀN*UÆÜInßL¯ƒπtùÛ*=´N,u§Ç˝¿}Êu+‚-à‰k≠2Aƒ2´ïRŒr∂Äﬂ»™ìQ¨˜·cBîT‘IìÀ¬“ —tùd,[·^eƒ¿%˜,ﬁ˝ëQ∂„##´K)ÍI∑OÈuß^¯•Ÿe)VIJ˚∏≥)∞÷“ênWﬁ~hàªòÃJ'
˘ÿË)sCî¯B®JÀr8§!ñüÔx¨	H…¨äÌ,˙˛¸ƒ;Ü∑?¸êùEzÃKΩ–ÿ±2Òf√3%|£¬ $rV´€‡#’ÿø15xYå∂°&mIöñHIs»˛à"ÓX5ÎŸlÎn#òƒëÓj°xJÅ:»ù›˝°W∆©Z√ícãÂ•ﬁ}ü’kÑ 5˘$àç'ùi^ü©N76*ΩÿÃö√eœ5+’πq√∂7Oèj 9–“EVΩÌM˛ˆÆ¯ìu˜3”ı´à∏ˆŸnçÅ5Ên}è-yíÃπqÂY n‚0ßGçd·(	U|k°ÃÅõ^∞öw›å€ﬂ=ñY LÃ
Pú[ûY≠(ÌŸµπ¥ÙO∏Ë2í„∆˚ty Ø‡ÑDøK_d•Ë∆§as{‘!´$Ê—µNœ√áÂÃœ&πnz“ìR7ØWj K‚w¢∏|Ò˚÷ù\c_A™ {ŒYÎùL]öBΩ¸”Ùp/ª'Ñ±„ßrñ∆°kpríãÆÁÏ»‹Ùßˆ=œ{˝ zf;›‚∞ˇ≤ä†
uTW‡Q√†I¸ﬂ†RÏ.ª#Lwu@ Ü©.&÷¯∂öè1\ÄÅx0sŒ,Ò‘:`·…Õ –}ã¸ SΩº,{Ëõ>∆’ö∆º`¸ËhHÔ≥—*Mæ¯◊yπıq√—<,À?N¶ Ñ/$â·S ˙Ñû(èÜYÃ≈5JôÙ√÷(ˆU.yYŒÂ´¯átÖá¢çõ'¿Q5t»™ëóbl¿¶çËÜ«ä[—û3Ï™P¢Rp0∫WbÆÛÔ\wâ€/Îq=V œ∞"E¥ı;nøçÒúïòÓ‘–ÿkq>‚IÚ∂ıxo{ÈW≥àyqï>4 1ÑËÿ≠h¥Úcmö£ï-·(ÄîHÍå˙5!Òºjó.à©~ü$50†}¬•É	–\äπèû$X≈GêÌW–ó+Lƒ›òœë‹˙ffsÇ{ÚÌAU~<è†·”<”~PBíÎd‚Ω
ˆ?£'ìØ|>ìÈ^“lÄÎ«$¯ÒnÜ∂eèÃ¶_µ≤nÏÇ≈“ÚƒR/≠Öˇ<ZØ9^3{ û>Ç∑’ﬂ√>?¿mÔ”m∂&_`[ŒZ˛ùB—í»≥˚1∞Z1≤äb‡s‘–√√ü¨ c∏Ò¯˘ !‰np∆y=Ñ^MálÌÙˆ#ùÔè‹üÈ„ˆ∞Ó EËÙŒ{ƒ<¿'¡ÒıÅ„€¸ ◊∏÷úëî˙⁄3ÆØK°?ﬁxÿ"åfÖj–vùU∏B±\ù ó?*AÛ≈H $<»ùÉ]Ÿpv'©
é4Äæ∆0o∏∫M£¸Ë˙öD™ò®†à5°ä})B≈%íØãSÂë∏6R{ï ¯pà.†J=;∆çMœWÖà!Esµf•ºéÙ1ï˝¢V-V†™∂)ùø¢xÉí⁄Ã2:’d ì•jæ
ıü}åÌ£"¶ÿWTlUÑ
ìOJ|
πÂ&ÚFó˛=˛V=πxµhxœd∫∆X£¿ÚôÂaöÙï≤FJ*’ñ€m´Vr3oëT∆h§x¸ÅOucõá
8z(y\V?'®3À<QÚ+w/ı©¯îb=µvY	˝-V>N∞
Êúq€ ¬K˛äJ!˚˛#`I÷S^#e†agIGœ´oÓaT≠Ÿ£Ü≠¢ﬁ‘å¥îõå’GGÚ»SHø¸£‘¢)f®Èu¢¿ì¢ˇˇó◊ùíoÃàV÷°ﬁ·˘óÏîi`n™Å%>aõè‰Ãv¶MÄ&÷i¸L´πpÓìQ∆π ]ÕœÛ¬[P(ﬁ≤ÓBÒCÜ„ò¬î{ÉEuQ ñˇe¡HIï|TIØ=+ƒ¬x≠Úéj¢√ƒA√/U£S>zÌ·•I™àŒ≤Ììº ◊l€˜|◊p‘_¯p˙^†_ﬁ‘˛¥äW@~i≈Æ?Qoeﬁ˘¡=Ü0îÑÎÀ@>u≤øã°ô≥·5±òÕ-Ωz»≤6{œÉÿz‘vû»ñ√Y[ò'ﬁIòÓx⁄¢“Yi≥…¯∫w…∫§ñDÓ¿i(Ith©:€√√›Dƒ(hüä∞ƒà˙Z¶ÆjAüÂXƒÛ«˝ifX>Ë[£òZ[-#ØoÛ∂0WÓ¶8vÌy
T™çËYø™/∑üètíe Ä,≥«2|UÛÃˆæ¯ÜMÏ•I/R©¬Œ°ñ¯Jjsﬂà¿NK:ì≤ÊY=—∑à‚gTE†ΩÚx.›‹ìíŒMP©q%c∂cÆ“3<!N]Ñ âßgÕÃay•óHßïHøÿá2®ZÑ¢jc¶1’‚#Ç˝#  ¶ˇ&01âÅÈ$∞òR#Ûy>YK¶Pë¥†üZÿcL≠ƒbÖx"néWdÂßPΩ ¢8
‡Wo∞l∞˜(°√LTy(úW$q¯™q’‰Ö«F∆t9[ÊûÅ/çîﬂ·e©ˇ '0(Öë√Áüª≤ÊÄ‰*

 WaÉÜ‹êÂ^Ä€¸´Æ0Ω…Ö`<“!‚…í§ìÙN¿JÅ:TÎâ=≈à$:Uá)˜Ç[m)üw†L/≈ºbçj 1Ác$˚$MFÜc¯!h∞j‡|“kùw{±MáÊ‹fÜíSÜ!À√\O3Ëﬁ[.&µåukÒ≤È%JzëÔπ¬ª9C?È÷PÍµã7≈_z>*◊©Ñ:›G?¢≥Üg˙áK\æéÆÊbhèÃÀnßeœ¿ñÖ_M‹Mÿ≈:[¿Ï÷ŸvR'Ê6k.ÜS€E*R∆mB~ÆÛj˙0Í ƒÉÍ:Q¨≠K—.∞€√≥k∞ﬂì	z™|$ÿVr†¿¨Âçür°PK∏ŒÌ{sÄWä¬Éba\[√∑]ÏÃÿÜ;j|r-ﬂD≠6=–ßÊ"”M\'ËµÇâ∑Ä/Y _ª1çôµ‡2:ˆó‘u6¯)œ¿yÀ¡+	{·ﬁ1Ú8"G◊‹X,çŸg]Ù‘©(êiæ‰ßÍı:ª∏<9a˝s÷møÓ∂{oŸõvØŸm≥∑Õ≥„ öXøÁ@’p≠ÖÂ_ ıÌÆ9Ü„ó∂‹£∑ïo/Åı¿˙&ø¶cm¬-Óıë—ƒÚpúL_BﬁDL"≥ïàôb#D?dœíq "ê∆wÛôG^tg1B˛÷Œä Ò›&˛Êˆ'⁄dˆ–„l@A¢Uf/E&+—$∑#∆h‘FÕ<öSLßZ°ù£“5ÄﬂxföD<ô˙ØÏßC∂á—'ÚÆeâãq~?õ#$à4iÜÇ∆ïê&
ÜØÑ0	ís‡Ó˘ÄûH˚’ÁÉbn_õEêxMÎˇó≈◊ØR”ÖêNàƒ=dç—Û.ﬁE]™ö£Á01lìb˜Úù=ÒÄlÇ`ßπµ‡˛õm6v˜∞íö*“)õ`û&5ˆR*¢ÉiNP«V‘˘üj}oÛ˚ZTbÁ◊ "*ò€ùsSSƒ'©Ü)LÂ’Ñ+‹à(
,´î∑'-5ËÁÂa‘Qﬁ“|à^‘BÍ¢ÄE≈µ}ºŒ∑ûoéÃIM·-ùËí[é≈Œ∞
äQ91·\†‹Â©E>±¥[∆öÛ<WZ…˝◊—7\Î
•§å”p5Û%p¬K‰~sñÛË‘√\åÄxp~:ãÈXïÇ\†¯Zù\§Œºt‘3‹«ïh_í‘$n|5â\ï§TÚH ˛ëåTë ñ6éI”OûÖ»Aß2¢d˜YNÿ¢¡!‘$ûBÍ«5Ò”Ï*Áîùös«tÜª¥∏É4*ı›û©I«ÏPtiN¡É¨ &ç !|≈uœÍ ÍN=_çó‡•Öu¯Z	Û∞tÌ˜y®óÏYBøÕú«≤…îX∫¥|Åµ˘m≈\ìx≠áè NVøDïüMGÎ,iú…DèsmÖÉäG1 ¨≥e,∫¯qwOıcÓçˇxD∏(î<•®pyDKwü+!á≤%‰ò°Ÿ∫ó.·?]÷:?n≥ß¨◊Óı0uÿÈ˘YßﬁÌúΩaΩ_{˝ˆÈË(=ÉÈaﬁÀS{aÅ ›¡‰b◊‹èπ”bMyE´Ê“üˆÌ+sëpÀ«"Äõ\X™ñ˜ﬁ‡œ–
EwUt	∂õ^&d3ÎostlxSRCEç”:-Óy¯ä˛™ö\∏ÓÄ`‡”·VÈAu¸"√éTV…2√ÕC·!YI^»Íã@ﬂ|{»ÄkbO0K/è°œQ%Î0£2ë?ykÊŸ¿>Ùo∂ŸZ§“›z±ôg#ÁvsQ*∆c_XÂªÕÒ÷≥m#«ÏN≠Ox∑ïÔ∆Ù mÿ7ÙOÃksv `–hI_6N’T'É⁄fqÙ%‚∏ûç’äÿ»ô5p1M4†Úµ¿®¢L{()÷öOrÙŸk 46ü4<wàX§«7´ÒóÎ¡)«K”ûo\oméÍaéÕëπÒ
›Çao‡ˇß»FV‘∫y:[È!ç›ùHƒpΩøòÆ5∂∞:O“sßóàº~±È‹d6„8V‹é€Ï∫Tù[?OµçŒ¶·8@9ZSk6™BE±.S˚SóRHò£ 6EI\Ó˘ãeÊÍ$ïQSµ—Ít÷ï⁄™{£$¨òl∏®´£†aN?EäÕ∞ïæ‘OÅ	8Ë%≤ ß®±ö}=Œê'aà@Wã=ó3Nı∏¡ö˜Ä˛Ãàè¢.ƒc‡Í¿Ky”»V™à.!]ãÄgSõWpè≥c∏¬g∆UVjP¸îÇúÁÎ≠è,o»M+f*b/AßÄi/º•kJ)÷—{Î1Ã#◊Ω_f»'åbOÜ{è) …RÁHÑEEyËF.ùÛf™›=®@$~*%]€Ëœ&¥«cê·ﬁ#ëK∑ÃÓ}jçFfÍÙÂ;Ø"ÅE–'$‡ˇ–®ØÇFiQò*•AÁûF·Ÿª'â∫ ·åº?$SÌ})U∞PmbΩﬂ&º(\ÛØ•È˘]ÿrTVH:¢»_2Õù"—€≤Å˘~]Ï´ULUT#ÃD¢Ê◊ï˘I≤f˚ΩËx:QUûF]ù,◊ö≥”ù[¿7eŸöª|è√W!P‚#m]E7¢§VÍãBûa2D]ØèRyfΩî=£∆°î0O ÆP
Æ:Ω2Ù8‚z±«aDe˛Ì∂ÃU}á≠jRﬂâXá¯¨Û¶˜Whï«u˝Â^∑îbVW≠Ù:goﬁºmvõõó˝∑(ˇDB7<˙*>X¶))&hπ¸= >üÇ“◊:ü¥&q:ˆl¶vó†y ©FÈ?óù^ÂIâEò¸!¨î¯ñ,íDDå™ä≈ñ»´,G‘âç¨+$XOêta6≠%∫ibL∫⁄Q.on∏æ3ÖÎ1r’Té´∞S+6ß›ŒQß	w¬+ëß≤‚Ö¸…¬"â‰ãxägùø·p^¢V5÷ΩpâÖ™P°√`Y&úDÿ¿kûÿÕ∑ëDÜªAJ•MÁã\ç_àsπˆÂGŒ®«’±J¶]©<MÛ ©$Iô˜Bπ;Ahä{∞a∆ƒDG«éoŒ´ï»Ø∑Ó#=» ≠°⁄∫ü¸ˆP¥±à.ñtèÎüãzÙe|•,¨%}*œêöÕX#‘ág∫ôß≈Ú.± $zì.ƒqQó#ÿb"ªiÃg∞ÇŸ≠ñê„√3‰tò√[kdÜ;˝…Úß0kNàLfœ¬˝sû‘r3m†û%ïÇıRß-œ|ñ1ì|3§¬±eùÌ<O¯ô0U‰_»Ûﬁ^z¶“Üê°'∏äoT¸À∞π®Ü…6œ®E‰´¬V⁄ÈT$H‹ÅgDàg¶%§ÿ{°â÷ –ú}2º$ëN„RÇÓhò≈ï ¡¸ÿÂPaï‰ˇ–WCö‘4	ejBDÁ)ÛÜ^ŒH¢§Ê√( 1π16aH∏≤ë0õ@ı¥wáó”‚X[πeõíËÄ-–8å´’5ÊâŸÕbr„ÊâPsùÊ ¿\æóÍ	ààx‚Ù-©}ê}ﬁH√" ≈Ã“Ä4‘íl˛À ‘·i~Í‘Si√RzlïI2âõ°7HöKJ ≈.:gƒkùJMl-~!óÄ¯j‡kLÌj2î)=ÔO≈¡=Ñ∆@[SLC1èP5_q®#◊i‘Jﬁ¡+Yypê$ÆµPü¿ßîXêé˛ coEq+àì¯zòfçö£è†D»W /ı‘˝˝äUàøhQÇÆ%<–`A8lƒüG˛`xBÕÉ‘ ≠y®dV ÃØw[‹ÁÂéÕìÆ2ò6ø_IÆÿÓ5¨î6à$—&ö±"Õ)9Üz cP	RÖú~?(æ4•ä{€/î†Kp‘Y™{m´‡ÄxÖé	rﬁnﬁ)gÒË: ÎERµ 1W iH3\Ìõq∆(“âi?I'¿xπÊ(`◊¢¬$W[SC•wíÂG-‚∏¨R7áyE√dyIOÙF›óœëüÀnQCM©+xÈ±h¯ ˆÀû≠°£jA(e4j’bÜB¢(p®ºIUO§¿WY±/ÄãeÙ¡KÂ¯*´’(\æ¥Õx¡Ëñ∫:∫uë™ïqˆ—vëMD@ch~J∏—$a)ÒùXZF˙à¬ï‚»áÏ<˛û∞oî«é‡ïik,xÏ¡Ìå2Ï¥§}£h|“`}•∞¸ÒV±?/µC~a@ûæK¨8ö¢pú»≈°vàçﬁ•ïs	›‚e‹Ï•◊ã9µ´ìˆ)˝ﬁk<yv1´UÇ#Œ∏•ï∫ª¥£=RÇÒ‘]ÂïÕAÌ.>ËüÕ˘ lBZKâœ*ÄÔJXÚÚ_?m†*ˇN˝˘,Q÷÷÷(sP
ÆoÓq˜Øª˝   ˇˇ ;ñã