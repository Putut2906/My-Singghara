package com.example.web

import android.content.Context
import com.example.data.AgendaItem
import com.example.data.AnggotaItem
import com.example.data.BelanjaPasarItem
import com.example.data.FcmNotification
import com.example.data.JadwalKegiatan
import com.example.data.LocalRepository
import com.example.data.NoteItem
import com.example.data.RiwayatPresensi
import com.example.data.SavingTarget
import com.example.data.SavingTransaction
import com.example.data.ShoppingItem
import com.example.data.QuranBookmark
import com.example.data.QuranHistory
import com.example.network.HolidayClient
import com.example.ui.screens.HijriCalendarHelper
import com.example.ui.screens.JavaneseCalendarHelper
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.firebase.FirebaseAccountAdminManager
import com.example.firebase.FirebaseAuthManager
import com.example.ht.HandyTalkyManager
import com.example.media.PhoneMediaScanner
import com.example.telegram.TelegramAiEngine
import com.example.telegram.TelegramBotManager
import com.google.firebase.auth.FirebaseAuth
import java.io.FileInputStream
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + com.example.util.AppCrashGuard.handler)

    // Hasil ekstraksi lirik tertanam per URI (string kosong = sudah dicari, tidak ada).
    private val webLyricsCache = android.util.LruCache<String, String>(40)

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Session Auth Management for QR Barcode Protection & Multi-Device Control
    data class ConnectedDevice(
        val id: String, // unique session token
        val ip: String,
        var userAgent: String,
        var deviceName: String,
        var pinCode: String,
        var isVerified: Boolean = false,
        var isRejected: Boolean = false,
        var isLoggedOut: Boolean = false,
        val connectedAt: Long = System.currentTimeMillis(),
        var lastActiveAt: Long = System.currentTimeMillis()
    )

    private val connectedDevices = java.util.concurrent.ConcurrentHashMap<String, ConnectedDevice>()
    private val verifiedTokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile var latestPinCode: String = generateNewPin()
    @Volatile var latestToken: String = generateNewToken()
    @Volatile var isAccessRejected: Boolean = false
    @Volatile var lastAccessNotifyTime: Long = 0L
    @Volatile var lastAccessClientIp: String = ""
    @Volatile var lastLogoutTime: Long = 0L

    var onAccessRequested: ((clientIp: String, timestamp: Long) -> Unit)? = null
    var onDevicesChanged: ((List<ConnectedDevice>) -> Unit)? = null
    var onBackupRestored: (() -> Unit)? = null

    fun getConnectedDevices(): List<ConnectedDevice> {
        return connectedDevices.values.toList().sortedByDescending { it.lastActiveAt }
    }

    fun notifyDevicesChanged() {
        val list = getConnectedDevices()
        scope.launch(Dispatchers.Main) {
            onDevicesChanged?.invoke(list)
        }
    }

    fun generateNewPin(): String = (100000..999999).random().toString()
    fun generateNewToken(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    fun approveSession(tokenOrPin: String): Boolean {
        val trimmed = tokenOrPin.trim()
        if (trimmed.isEmpty()) return false
        isAccessRejected = false
        val targetToken = if (trimmed.startsWith("SINGGHARA_AUTH:")) {
            val parts = trimmed.split(":")
            if (parts.size >= 2) parts[1].trim() else ""
        } else {
            trimmed
        }

        var matched = false
        connectedDevices.values.forEach { dev ->
            if (dev.id == targetToken || dev.pinCode == trimmed ||
                (trimmed.startsWith("SINGGHARA_AUTH:") && trimmed.contains(dev.id)) ||
                (trimmed.startsWith("SINGGHARA_AUTH:") && trimmed.contains(dev.pinCode))) {
                dev.isVerified = true
                dev.isRejected = false
                dev.lastActiveAt = System.currentTimeMillis()
                verifiedTokens.add(dev.id)
                matched = true
            }
        }

        if (!matched) {
            if (targetToken.isNotEmpty()) verifiedTokens.add(targetToken)
            verifiedTokens.add(latestToken)
            connectedDevices[latestToken]?.let {
                it.isVerified = true
                it.isRejected = false
                it.lastActiveAt = System.currentTimeMillis()
            }
        }
        notifyDevicesChanged()
        return true
    }

    fun approveAllPending(): Boolean {
        isAccessRejected = false
        connectedDevices.values.forEach { dev ->
            if (!dev.isVerified && !dev.isRejected) {
                dev.isVerified = true
                dev.isRejected = false
                dev.lastActiveAt = System.currentTimeMillis()
                verifiedTokens.add(dev.id)
            }
        }
        verifiedTokens.add(latestToken)
        connectedDevices[latestToken]?.let {
            it.isVerified = true
            it.isRejected = false
        }
        notifyDevicesChanged()
        return true
    }

    fun approveDeviceSession(sessionId: String): Boolean {
        isAccessRejected = false
        connectedDevices[sessionId]?.let { dev ->
            dev.isVerified = true
            dev.isRejected = false
            dev.lastActiveAt = System.currentTimeMillis()
            verifiedTokens.add(dev.id)
            notifyDevicesChanged()
            return true
        }
        return false
    }

    fun rejectDeviceSession(sessionId: String): Boolean {
        connectedDevices[sessionId]?.let { dev ->
            dev.isVerified = false
            dev.isRejected = true
            dev.lastActiveAt = System.currentTimeMillis()
            verifiedTokens.remove(dev.id)
            notifyDevicesChanged()
            return true
        }
        return false
    }

    fun rejectPendingAccess() {
        isAccessRejected = true
        connectedDevices.values.forEach { dev ->
            if (!dev.isVerified) {
                dev.isRejected = true
                dev.isVerified = false
                dev.lastActiveAt = System.currentTimeMillis()
            }
        }
        verifiedTokens.clear()
        latestPinCode = generateNewPin()
        latestToken = generateNewToken()
        notifyDevicesChanged()
    }

    fun resetAccessRejection() {
        isAccessRejected = false
    }

    fun triggerAccessNotification(clientIp: String, dev: ConnectedDevice? = null) {
        val now = System.currentTimeMillis()
        if (isAccessRejected) return
        if (dev != null && (dev.isVerified || dev.isRejected)) return
        if (now - lastLogoutTime < 10000L) return
        if (now - lastAccessNotifyTime < 12000L && clientIp == lastAccessClientIp) return

        lastAccessNotifyTime = now
        lastAccessClientIp = clientIp
        scope.launch(Dispatchers.Main) {
            onAccessRequested?.invoke(clientIp, now)
        }
    }

    fun revokeAll() {
        // PENTING: saat Kunci & Putuskan Sesi PC dipanggil, tandai isAccessRejected = true
        // agar seluruh browser PC yang terhubung beralih ke halaman "Akses Ditolak"
        isAccessRejected = true
        connectedDevices.values.forEach { dev ->
            dev.isVerified = false
            dev.isRejected = true
            dev.lastActiveAt = System.currentTimeMillis()
        }
        verifiedTokens.clear()
        latestPinCode = generateNewPin()
        latestToken = generateNewToken()
        notifyDevicesChanged()
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
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(port))
                }
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

        // Batas aman untuk body (mis. berkas cadangan JSON yang memuat foto/audio base64).
        private const val MAX_REQUEST_BODY_BYTES = 128 * 1024 * 1024

        fun parseDeviceName(userAgent: String, clientIp: String): String {
            val ua = userAgent.lowercase()
            val os = when {
                ua.contains("windows nt 10") || ua.contains("windows nt 11") || ua.contains("windows") -> "Windows PC"
                ua.contains("macintosh") || ua.contains("mac os") -> "Mac"
                ua.contains("linux") && !ua.contains("android") -> "Linux PC"
                ua.contains("iphone") -> "iPhone"
                ua.contains("ipad") -> "iPad"
                ua.contains("android") -> "Android"
                ua.contains("cros") -> "ChromeOS"
                else -> "Perangkat Web PC"
            }
            val browser = when {
                ua.contains("edg/") -> "Edge"
                ua.contains("opr/") || ua.contains("opera") -> "Opera"
                ua.contains("chrome") && !ua.contains("edg") -> "Chrome"
                ua.contains("safari") && !ua.contains("chrome") -> "Safari"
                ua.contains("firefox") -> "Firefox"
                else -> "Browser"
            }
            return if (clientIp == "127.0.0.1" || clientIp == "localhost") {
                "Browser Lokal (HP)"
            } else {
                "$os Ã¢â‚¬Â¢ $browser"
            }
        }

        data class NetworkInterfaceInfo(
            val type: String,
            val ip: String,
            val interfaceName: String
        )

        fun getIpAddress(context: Context): String {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                val isWifiConnected = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                val isEthernetConnected = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true

                val interfaces = NetworkInterface.getNetworkInterfaces()
                var hotspotIp: String? = null
                var wifiIp: String? = null
                var ethernetIp: String? = null
                var fallbackIp: String? = null

                while (interfaces != null && interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback) continue
                    val isUp = try { networkInterface.isUp } catch (e: Exception) { true }
                    if (!isUp) continue

                    val addresses = networkInterface.inetAddresses
                    val name = networkInterface.name.lowercase()

                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress
                            if (hostAddress != null && !hostAddress.startsWith("127.")) {
                                if (hostAddress.startsWith("192.168.43.")) {
                                    hotspotIp = hostAddress
                                } else if (name.startsWith("ap") || name.contains("softap") || name.contains("swlan") ||
                                    name.startsWith("rndis") || name.contains("tether") || name.contains("hotspot") ||
                                    name.startsWith("usb")
                                ) {
                                    if (hotspotIp == null) hotspotIp = hostAddress
                                } else if (name.startsWith("wlan")) {
                                    if (wifiIp == null) wifiIp = hostAddress
                                } else if (name.startsWith("eth")) {
                                    if (ethernetIp == null) ethernetIp = hostAddress
                                } else {
                                    if (fallbackIp == null) fallbackIp = hostAddress
                                }
                            }
                        }
                    }
                }

                if (isWifiConnected && wifiIp != null) return wifiIp
                if (isEthernetConnected && ethernetIp != null) return ethernetIp
                if (hotspotIp != null) return hotspotIp
                if (wifiIp != null) return wifiIp
                if (ethernetIp != null) return ethernetIp
                if (fallbackIp != null) return fallbackIp
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "192.168.43.1"
        }

        fun getAllAvailableIps(context: Context): List<NetworkInterfaceInfo> {
            val list = mutableListOf<NetworkInterfaceInfo>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback) continue
                    val isUp = try { networkInterface.isUp } catch (e: Exception) { true }
                    if (!isUp) continue

                    val addresses = networkInterface.inetAddresses
                    val name = networkInterface.name.lowercase()

                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (address is Inet4Address && !address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress
                            if (hostAddress != null && !hostAddress.startsWith("127.")) {
                                val type = when {
                                    hostAddress.startsWith("192.168.43.") || name.startsWith("ap") || name.contains("softap") || name.contains("swlan") || name.contains("hotspot") -> "Hotspot HP"
                                    name.startsWith("rndis") || name.startsWith("usb") -> "USB Tethering"
                                    name.startsWith("wlan") -> "Wi-Fi Lokal"
                                    name.startsWith("eth") -> "LAN / Ethernet"
                                    else -> "Jaringan Lokal"
                                }
                                if (list.none { it.ip == hostAddress }) {
                                    list.add(NetworkInterfaceInfo(type = type, ip = hostAddress, interfaceName = networkInterface.name))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                // Socket spekulatif browser (preconnect tanpa request) sempat menahan
                // thread IO selamanya; batas baca 15 detik melepas koneksi menganggur.
                s.soTimeout = 15_000
                val input = java.io.BufferedInputStream(s.getInputStream())
                val output = s.getOutputStream()

                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullPath = parts[1]

                var contentLength = 0
                var userAgent = ""
                var rangeHeader = ""
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val headerLower = line.lowercase()
                    if (headerLower.startsWith("content-length:")) {
                        contentLength = headerLower.substringAfter("content-length:").trim().toIntOrNull() ?: 0
                    }
                    if (headerLower.startsWith("user-agent:")) {
                        userAgent = line.substringAfter(":").trim()
                    }
                    // Pemutar media browser (Chrome/Firefox) meminta byte parsial agar bisa melompati
                    // durasi; tanpa header ini respons 206 tidak akan pernah terbentuk.
                    if (headerLower.startsWith("range:")) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }

                if (contentLength > MAX_REQUEST_BODY_BYTES) {
                    sendResponse(output, 413, "Payload Too Large", "text/plain", "Berkas terlalu besar")
                    return
                }

                // Body HTTP dihitung dalam byte; membacanya sebagai Char akan menggantung
                // saat JSON berisi karakter non-ASCII (teks Arab, emoji, huruf beraksen).
                val body = if (contentLength > 0) {
                    val bodyBytes = ByteArray(contentLength)
                    var bytesRead = 0
                    while (bytesRead < contentLength) {
                        val r = input.read(bodyBytes, bytesRead, contentLength - bytesRead)
                        if (r <= 0) break
                        bytesRead += r
                    }
                    String(bodyBytes, 0, bytesRead, Charsets.UTF_8)
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
                    path.startsWith("/assets/") || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/") ||
                    path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".woff2") || path.endsWith(".woff") ||
                    path.endsWith(".ttf") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".json") || path.endsWith(".webmanifest") ||
                    path == "/manifest.json" || path == "/manifest.webmanifest" || path == "/sw.js" -> {
                        try {
                            val cleanPath = when {
                                path.startsWith("/assets/") -> path.removePrefix("/assets/")
                                path.startsWith("/") -> path.removePrefix("/")
                                else -> path
                            }
                            val assetPath = "web/$cleanPath"
                            val inputStream = try {
                                context.assets.open(assetPath)
                            } catch (e: Exception) {
                                try {
                                    if (cleanPath.endsWith(".css")) context.assets.open("web/css/${cleanPath.substringAfterLast('/')}")
                                    else if (cleanPath.endsWith(".js")) context.assets.open("web/js/${cleanPath.substringAfterLast('/')}")
                                    else if (cleanPath.endsWith(".woff2") || cleanPath.endsWith(".woff") || cleanPath.endsWith(".ttf")) {
                                        context.assets.open("web/css/fonts/${cleanPath.substringAfterLast('/')}")
                                    } else {
                                        val fn = cleanPath.substringAfterLast('/')
                                        context.assets.open("web/$fn")
                                    }
                                } catch (e2: Exception) {
                                    val fn = cleanPath.substringAfterLast('/')
                                    if (fn.endsWith(".css")) context.assets.open("web/css/$fn")
                                    else if (fn.endsWith(".js")) context.assets.open("web/js/$fn")
                                    else if (fn.endsWith(".woff2") || fn.endsWith(".woff") || fn.endsWith(".ttf")) context.assets.open("web/css/fonts/$fn")
                                    else throw e2
                                }
                            }
                            val bytes = inputStream.readBytes()
                            inputStream.close()
                            val mimeType = when {
                                path.endsWith(".css") -> "text/css; charset=utf-8"
                                path.endsWith(".js") -> "application/javascript; charset=utf-8"
                                path.endsWith(".woff2") -> "font/woff2"
                                path.endsWith(".woff") -> "font/woff"
                                path.endsWith(".ttf") -> "font/ttf"
                                path.endsWith(".png") -> "image/png"
                                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                                path.endsWith(".svg") -> "image/svg+xml"
                                path.endsWith(".ico") -> "image/x-icon"
                                path.endsWith(".json") || path.endsWith(".webmanifest") -> "application/manifest+json; charset=utf-8"
                                else -> "application/octet-stream"
                            }
                            // sw.js tidak boleh di-cache: browser perlu menemukan versi service worker
                            // terbaru supaya perubahan dashboard langsung terpakai di PC.
                            sendBinaryResponse(output, 200, "OK", mimeType, bytes, cacheable = path != "/sw.js")
                        } catch (e: Exception) {
                            sendResponse(output, 404, "Not Found", "text/plain", "Asset Not Found: $path")
                        }
                    }
                    path == "/" || path == "/index.html" || path == "/ht" || path == "/handy_talky" || path == "/walkie_talkie" || path == "/telegram_ai" || path == "/telegram" || path == "/tg" -> {
                        if (!isAccessRejected) {
                            triggerAccessNotification(clientIp)
                        }
                        sendHtmlResponse(output, getWebUiHtml())
                    }
                    path.startsWith("/favicon") || path.startsWith("/app-logo") ||
                    path == "/apple-touch-icon.png" ||
                    path == "/apple-touch-icon-precomposed.png" || path == "/logo.png" -> {
                        serveFavicon(path, output)
                    }
                    path == "/pc-installer.exe" || path == "/MySingghara-Pc-Installer.exe" ||
                    path == "/pc_app_installer.exe" -> {
                        servePcNativeInstaller(output)
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
                    path.startsWith("/api/backup") -> {
                        handleBackupRoute(path, method, body, output)
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
                    path.startsWith("/api/transfer_file") -> {
                        handleTransferFileRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/ht") || path.startsWith("/api/handy_talky") -> {
                        handleHandyTalkyRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/telegram_ai") || path.startsWith("/api/telegram") -> {
                        handleTelegramAiRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/quran") -> {
                        handleQuranRoute(path, method, queryParams, body, output)
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
                    path.startsWith("/api/gaji") -> {
                        handleGajiRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/arisan") -> {
                        handleArisanRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/belanja_pasar") -> {
                        handleBelanjaPasarRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/presensi") -> {
                        handlePresensiRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/profile") -> {
                        handleProfileRoute(path, method, queryParams, body, output)
                    }
                    path.startsWith("/api/notifications") -> {
                        handleNotificationsRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/clock-font") || path.startsWith("/api/clock/font") -> {
                        handleClockFontRoute(queryParams, output)
                    }
                    path.startsWith("/api/clock-fonts") -> {
                        handleClockFontsListRoute(method, queryParams, body, output)
                    }
                    path.startsWith("/api/azan/audio") || path.startsWith("/api/audio/azan") -> {
                        handleAzanAudioRoute(queryParams, output)
                    }
                    path.startsWith("/api/prayer/settings") -> {
                        handlePrayerSettingsRoute(method, body, output)
                    }
                    path.startsWith("/api/prayer/refresh") -> {
                        handlePrayerRefreshRoute(output)
                    }
                    path.startsWith("/api/phone_media") -> {
                        handlePhoneMediaRoute(path, queryParams, rangeHeader, output)
                    }
                    path.startsWith("/api/auth") -> {
                        handleAuthRoute(method, queryParams, body, output, clientIp, userAgent)
                    }
                    path.startsWith("/api/admin") || path.startsWith("/api/manajemen_akun") -> {
                        handleAdminRoute(method, queryParams, body, output)
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

    private fun readHttpLine(input: java.io.InputStream): String? {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toByteArray().toString(Charsets.UTF_8)
            if (b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
        }
        return buffer.toByteArray().toString(Charsets.UTF_8)
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
    private fun getActiveProfileName(): String {
        val liveName = com.example.ui.MainViewModel.instance?.profileName?.trim()
        if (!liveName.isNullOrEmpty()) return liveName
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val savedName = mainPrefs.getString("profile_name", "")?.trim()
        if (!savedName.isNullOrEmpty()) return savedName
        return "Singghara User"
    }

    private fun getActiveProfileAvatarIndex(): Int {
        val liveIndex = com.example.ui.MainViewModel.instance?.profileAvatarIndex
        if (liveIndex != null) return liveIndex
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        return mainPrefs.getInt("profile_avatar_index", 0)
    }

    private fun getActiveProfileImageUri(): String {
        val liveUri = com.example.ui.MainViewModel.instance?.profileImageUri
        if (!liveUri.isNullOrEmpty()) return liveUri
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        return mainPrefs.getString("profile_image_uri", "") ?: ""
    }

    private fun getActiveUserUid(): String {
        // 1. Coba dari MainViewModel live state
        val vmUser = com.example.ui.MainViewModel.instance?.currentFirebaseUser
        val vmUid = vmUser?.uid?.trim()
        if (!vmUid.isNullOrEmpty()) return vmUid

        // 2. Coba dari authManager di MainViewModel
        val vmAuthUser = com.example.ui.MainViewModel.instance?.authManager?.currentUser
        val vmAuthUid = vmAuthUser?.uid?.trim()
        if (!vmAuthUid.isNullOrEmpty()) return vmAuthUid

        // 3. Coba langsung dari FirebaseAuth
        val fbUser = FirebaseAuth.getInstance().currentUser
        val fbUid = fbUser?.uid?.trim()
        if (!fbUid.isNullOrEmpty()) return fbUid

        // 4. Coba dari SharedPreferences "cached_user_uid"
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val cachedUid = mainPrefs.getString("cached_user_uid", "")?.trim() ?: ""
        if (cachedUid.isNotEmpty()) return cachedUid

        // 5. Cek apakah ada email admin yang login
        val userEmail = vmUser?.email?.trim()
            ?: vmAuthUser?.email?.trim()
            ?: fbUser?.email?.trim()
            ?: mainPrefs.getString("user_email", "")?.trim()
            ?: mainPrefs.getString("profile_email", "")?.trim()
            ?: ""
        if (userEmail.equals("singghara.admin@gmail.com", ignoreCase = true) ||
            userEmail.equals("putut.singgihpamungkas@gmail.com", ignoreCase = true)) {
            return FirebaseAccountAdminManager.ADMIN_UID
        }

        return ""
    }

    private fun isCurrentUserAdmin(): Boolean {
        val uid = getActiveUserUid()
        return uid.equals(FirebaseAccountAdminManager.ADMIN_UID, ignoreCase = true)
    }

    private fun getActiveUserEmail(): String {
        val vmUser = com.example.ui.MainViewModel.instance?.currentFirebaseUser
        if (!vmUser?.email.isNullOrBlank()) return vmUser!!.email!!.trim()
        val vmAuthUser = com.example.ui.MainViewModel.instance?.authManager?.currentUser
        if (!vmAuthUser?.email.isNullOrBlank()) return vmAuthUser!!.email!!.trim()
        val fbUser = FirebaseAuth.getInstance().currentUser
        if (!fbUser?.email.isNullOrBlank()) return fbUser!!.email!!.trim()
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val savedEmail = mainPrefs.getString("user_email", "")?.trim() ?: ""
        if (savedEmail.isNotBlank()) return savedEmail
        if (isCurrentUserAdmin()) return "singghara.admin@gmail.com"
        return ""
    }

    private fun handleAuthRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream, clientIp: String = "127.0.0.1", userAgent: String = "") {
        val pathAction = queryParams["action"] ?: ""
        val token = queryParams["token"] ?: ""
        val response = JSONObject()
        val ip = getIpAddress(context)
        val url = "http://$ip:$port"

        // Resolve device session
        val jsonBody = if (method == "POST") {
            try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        } else JSONObject()
        val reqToken = jsonBody.optString("token", "").ifEmpty { token }

        // Find existing device by token or create new device
        // Dibaca sekali saja: containsKey lalu !! bisa kalah oleh thread yang
        // melepas perangkat di antara keduanya, dan NCE di coroutine server
        // memutus koneksi yang sedang ditangani.
        val existingDevice = if (reqToken.isNotEmpty()) connectedDevices[reqToken] else null
        val device: ConnectedDevice = if (existingDevice != null) {
            existingDevice.apply {
                lastActiveAt = System.currentTimeMillis()
                if (userAgent.isNotEmpty() && (this.userAgent.isEmpty() || this.deviceName == "Perangkat Web PC")) {
                    this.userAgent = userAgent
                    this.deviceName = parseDeviceName(userAgent, clientIp)
                }
            }
        } else {
            val devToken = if (reqToken.isNotEmpty()) reqToken else generateNewToken()
            val devPin = generateNewPin()
            val devName = parseDeviceName(userAgent, clientIp)
            val newDev = ConnectedDevice(
                id = devToken,
                ip = clientIp,
                userAgent = userAgent,
                deviceName = devName,
                pinCode = devPin,
                isVerified = false,
                isRejected = isAccessRejected,
                connectedAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )
            connectedDevices[devToken] = newDev
            latestToken = devToken
            latestPinCode = devPin
            notifyDevicesChanged()
            newDev
        }

        if (method == "POST") {
            val reqPin = jsonBody.optString("pin", "")
            val action = jsonBody.optString("action", pathAction)

            if (action == "logout" || action == "revoke") {
                lastLogoutTime = System.currentTimeMillis()
                device.isVerified = false
                device.isRejected = false
                device.isLoggedOut = true
                verifiedTokens.remove(device.id)
                notifyDevicesChanged()
                response.put("success", true)
                response.put("authenticated", false)
                response.put("rejected", false)
                response.put("logged_out", true)
                response.put("token", device.id)
                response.put("pin", device.pinCode)
                response.put("qrPayload", "SINGGHARA_AUTH:${device.id}:${device.pinCode}")
                response.put("message", "Sesi PC berhasil diakhiri")
            } else if (action == "request_retry" || action == "retry") {
                lastLogoutTime = 0L
                device.isRejected = false
                device.isVerified = false
                device.isLoggedOut = false
                isAccessRejected = false
                device.pinCode = generateNewPin()
                latestToken = device.id
                latestPinCode = device.pinCode
                notifyDevicesChanged()
                triggerAccessNotification(clientIp, device)
                response.put("success", true)
                response.put("authenticated", false)
                response.put("rejected", false)
                response.put("logged_out", false)
                response.put("token", device.id)
                response.put("pin", device.pinCode)
                response.put("qrPayload", "SINGGHARA_AUTH:${device.id}:${device.pinCode}")
                response.put("message", "Permintaan akses ulang dikirim ke smartphone")
            } else if (reqPin.isNotEmpty() || action == "verify_pin") {
                val cleanReqPin = reqPin.trim().replace(" ", "").replace("-", "")
                val cleanDevicePin = device.pinCode.trim().replace(" ", "").replace("-", "")
                val cleanLatestPin = latestPinCode.trim().replace(" ", "").replace("-", "")
                if (cleanReqPin.isNotEmpty() && (cleanReqPin.equals(cleanDevicePin, ignoreCase = true) || cleanReqPin.equals(cleanLatestPin, ignoreCase = true))) {
                    device.isVerified = true
                    device.isRejected = false
                    device.isLoggedOut = false
                    device.lastActiveAt = System.currentTimeMillis()
                    verifiedTokens.add(device.id)
                    notifyDevicesChanged()
                    response.put("success", true)
                    response.put("authenticated", true)
                    response.put("rejected", false)
                    response.put("logged_out", false)
                    response.put("token", device.id)
                    response.put("message", "Kode PIN benar! Akses PC berhasil diverifikasi.")
                } else {
                    response.put("success", false)
                    response.put("authenticated", false)
                    response.put("rejected", false)
                    response.put("token", device.id)
                    response.put("pin", device.pinCode)
                    response.put("message", "Kode PIN salah! Silakan periksa kembali PIN di smartphone.")
                }
            } else if (action == "approve" || action == "authorize") {
                device.isVerified = true
                device.isRejected = false
                device.isLoggedOut = false
                device.lastActiveAt = System.currentTimeMillis()
                verifiedTokens.add(device.id)
                notifyDevicesChanged()
                response.put("success", true)
                response.put("authenticated", true)
                response.put("rejected", false)
                response.put("logged_out", false)
                response.put("token", device.id)
                response.put("message", "Akses PC berhasil diverifikasi!")
            } else {
                response.put("success", false)
                response.put("authenticated", false)
                response.put("message", "Aksi tidak dikenal")
            }
            sendJsonResponse(output, response.toString())
            return
        }

        // GET requests: session status or initial payload
        // PENTING: Periksa verifikasi KHUSUS untuk device ini, BUKAN latestToken global!
        // Ini memastikan perangkat lain yang baru dibuka wajib meminta verifikasi ulang.
        val isVerified = !isAccessRejected && !device.isRejected && !device.isLoggedOut && (device.isVerified && verifiedTokens.contains(device.id))
        val isRejected = isAccessRejected || device.isRejected
        val isLoggedOut = device.isLoggedOut || isRejected

        // Only trigger notification to phone if unverified, not rejected, and not logged out
        if (!isVerified && !isRejected && !device.isLoggedOut) {
            triggerAccessNotification(clientIp, device)
        }

        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val profileName = getActiveProfileName()
        val profileAvatarIndex = getActiveProfileAvatarIndex()
        val profileImageUri = getActiveProfileImageUri()
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        val hasCustomImage = profileAvatarIndex == -1 && (profileImageUri.isNotEmpty() || (localFile.exists() && localFile.length() > 0))

        val currentUid = getActiveUserUid()
        val isRealAdmin = isCurrentUserAdmin()

        response.put("authenticated", isVerified)
        response.put("rejected", isRejected)
        response.put("logged_out", isLoggedOut)
        response.put("token", device.id)
        response.put("pin", device.pinCode)
        response.put("deviceName", device.deviceName)
        response.put("ip", ip)
        response.put("port", port)
        response.put("url", url)
        response.put("qrPayload", "SINGGHARA_AUTH:${device.id}:${device.pinCode}")
        response.put("profileName", profileName)
        response.put("profileAvatarIndex", profileAvatarIndex)
        response.put("hasCustomImage", hasCustomImage)
        response.put("profileImageUrl", if (hasCustomImage) "/api/profile/image?t=${System.currentTimeMillis()}" else "")
        response.put("currentUid", currentUid)
        response.put("isAdmin", isRealAdmin)
        response.put("adminUid", FirebaseAccountAdminManager.ADMIN_UID)
        sendJsonResponse(output, response.toString())
    }

    private fun getAdminUsersCacheFile(): java.io.File = java.io.File(context.filesDir, "admin_users_cache.json")
    private fun getAdminMessagesCacheFile(): java.io.File = java.io.File(context.filesDir, "admin_messages_cache.json")

    private fun readOfflineUsersJson(): JSONArray {
        val file = getAdminUsersCacheFile()
        if (file.exists()) {
            try {
                val text = file.readText()
                if (text.isNotBlank()) {
                    return JSONArray(text)
                }
            } catch (_: Exception) {}
        }
        val initial = buildInitialOfflineUsers()
        try {
            file.writeText(initial.toString())
        } catch (_: Exception) {}
        return initial
    }

    private fun saveOfflineUsersJson(arr: JSONArray) {
        try {
            getAdminUsersCacheFile().writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun readOfflineMessagesJson(): JSONArray {
        val file = getAdminMessagesCacheFile()
        if (file.exists()) {
            try {
                val text = file.readText()
                if (text.isNotBlank()) {
                    return JSONArray(text)
                }
            } catch (_: Exception) {}
        }
        val initial = buildInitialOfflineMessages()
        try {
            file.writeText(initial.toString())
        } catch (_: Exception) {}
        return initial
    }

    private fun saveOfflineMessagesJson(arr: JSONArray) {
        try {
            getAdminMessagesCacheFile().writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun buildInitialOfflineUsers(): JSONArray {
        val arr = JSONArray()
        val now = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
        val dateEndSdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))

        // 1. Akun Administrator Utama
        val adminActiveEmail = if (getActiveUserEmail().isNotBlank()) getActiveUserEmail() else "putut.singgihpamungkas@gmail.com"
        val adminActiveName = if (getActiveProfileName().isNotBlank()) getActiveProfileName() else "Administrator My Singghara"
        arr.put(JSONObject().apply {
            put("uid", FirebaseAccountAdminManager.ADMIN_UID)
            put("email", adminActiveEmail)
            put("displayName", adminActiveName)
            put("photoUrl", "")
            put("fcmToken", "fcm_token_admin_singghara_local")
            put("deviceModel", "Ponsel Server (Web Connect)")
            put("osVersion", "Android 14 (HyperOS)")
            put("providerId", "password")
            put("createdAt", now - (180L * 86400000L))
            put("updatedAt", now)
            put("lastActiveAt", now)
            put("isActive", true)
            put("isAdmin", true)
            put("isRecentlyActive", true)
            put("formattedLastActive", "Baru saja (Online)")
            put("fullDateTime", sdf.format(java.util.Date(now)))
            put("membershipDurationMonths", 0)
            put("membershipStartDate", 0L)
            put("membershipEndDate", 0L)
            put("membershipTier", "VIP Administrator (Permanen)")
            put("isMembershipExpired", false)
            put("hasMembership", true)
            put("formattedMembershipEndDate", "Permanen")
            put("formattedMembershipDuration", "Permanen")
            put("formattedMembershipPeriod", "VIP Admin (Permanen)")
        })

        // 2. Akun Host / Pengguna Saat Ini
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && currentUser.uid != FirebaseAccountAdminManager.ADMIN_UID) {
            val cal = java.util.Calendar.getInstance()
            val start = cal.timeInMillis
            cal.add(java.util.Calendar.MONTH, 12)
            val end = cal.timeInMillis
            arr.put(JSONObject().apply {
                put("uid", currentUser.uid)
                put("email", currentUser.email ?: "")
                put("displayName", currentUser.displayName ?: (currentUser.email?.substringBefore('@') ?: "Pengguna Host"))
                put("photoUrl", currentUser.photoUrl?.toString() ?: "")
                put("fcmToken", "fcm_token_host_local")
                put("deviceModel", "Ponsel Host")
                put("osVersion", "Android")
                put("providerId", "google.com")
                put("createdAt", now - (30L * 86400000L))
                put("updatedAt", now)
                put("lastActiveAt", now)
                put("isActive", true)
                put("isAdmin", false)
                put("isRecentlyActive", true)
                put("formattedLastActive", "Baru saja (Online)")
                put("fullDateTime", sdf.format(java.util.Date(now)))
                put("membershipDurationMonths", 12)
                put("membershipStartDate", start)
                put("membershipEndDate", end)
                put("membershipTier", "12 Bulan")
                put("isMembershipExpired", false)
                put("hasMembership", true)
                put("formattedMembershipEndDate", dateEndSdf.format(java.util.Date(end)))
                put("formattedMembershipDuration", "12 Bulan")
                put("formattedMembershipPeriod", "12 Bulan (Sisa 365 hr)")
            })
        }

        // 3. Hendra Wijaya (Member VIP 12 Bulan)
        val calPutut = java.util.Calendar.getInstance()
        calPutut.add(java.util.Calendar.MONTH, 12)
        arr.put(JSONObject().apply {
            put("uid", "USR_VIP_HENDRA_001")
            put("email", "hendra.wijaya@gmail.com")
            put("displayName", "Hendra Wijaya")
            put("photoUrl", "")
            put("fcmToken", "fcm_token_putut_singghara")
            put("deviceModel", "Xiaomi Redmi Note 12")
            put("osVersion", "Android 13")
            put("providerId", "google.com")
            put("createdAt", now - (60L * 86400000L))
            put("updatedAt", now - (5L * 60000L))
            put("lastActiveAt", now - (5L * 60000L))
            put("isActive", true)
            put("isAdmin", false)
            put("isRecentlyActive", true)
            put("formattedLastActive", "Baru saja (Online)")
            put("fullDateTime", sdf.format(java.util.Date(now - (5L * 60000L))))
            put("membershipDurationMonths", 12)
            put("membershipStartDate", now - (60L * 86400000L))
            put("membershipEndDate", calPutut.timeInMillis)
            put("membershipTier", "12 Bulan")
            put("isMembershipExpired", false)
            put("hasMembership", true)
            put("formattedMembershipEndDate", dateEndSdf.format(java.util.Date(calPutut.timeInMillis)))
            put("formattedMembershipDuration", "12 Bulan")
            put("formattedMembershipPeriod", "12 Bulan (Sisa 305 hr)")
        })

        // 4. Budi Santoso (Member 3 Bulan)
        val calBudi = java.util.Calendar.getInstance()
        calBudi.add(java.util.Calendar.MONTH, 3)
        arr.put(JSONObject().apply {
            put("uid", "USR_MEMBER_BUDI_002")
            put("email", "budi.santoso@gmail.com")
            put("displayName", "Budi Santoso")
            put("photoUrl", "")
            put("fcmToken", "fcm_token_budi_002")
            put("deviceModel", "Samsung Galaxy A54")
            put("osVersion", "Android 14")
            put("providerId", "password")
            put("createdAt", now - (20L * 86400000L))
            put("updatedAt", now - (35L * 60000L))
            put("lastActiveAt", now - (35L * 60000L))
            put("isActive", true)
            put("isAdmin", false)
            put("isRecentlyActive", false)
            put("formattedLastActive", "35 menit lalu")
            put("fullDateTime", sdf.format(java.util.Date(now - (35L * 60000L))))
            put("membershipDurationMonths", 3)
            put("membershipStartDate", now - (20L * 86400000L))
            put("membershipEndDate", calBudi.timeInMillis)
            put("membershipTier", "3 Bulan")
            put("isMembershipExpired", false)
            put("hasMembership", true)
            put("formattedMembershipEndDate", dateEndSdf.format(java.util.Date(calBudi.timeInMillis)))
            put("formattedMembershipDuration", "3 Bulan")
            put("formattedMembershipPeriod", "3 Bulan (Sisa 70 hr)")
        })

        // 5. Akun Kadaluarsa untuk pengujian proteksi
        val calExp = java.util.Calendar.getInstance()
        calExp.add(java.util.Calendar.DAY_OF_MONTH, -3)
        arr.put(JSONObject().apply {
            put("uid", "USR_EXPIRED_DEMO_003")
            put("email", "user.demo@mysingghara.id")
            put("displayName", "Pengguna Demo Nonaktif")
            put("photoUrl", "")
            put("fcmToken", "")
            put("deviceModel", "Oppo Reno 8")
            put("osVersion", "Android 12")
            put("providerId", "password")
            put("createdAt", now - (40L * 86400000L))
            put("updatedAt", now - (3L * 86400000L))
            put("lastActiveAt", now - (3L * 86400000L))
            put("isActive", false)
            put("isAdmin", false)
            put("isRecentlyActive", false)
            put("formattedLastActive", "3 hari lalu")
            put("fullDateTime", sdf.format(java.util.Date(now - (3L * 86400000L))))
            put("membershipDurationMonths", 1)
            put("membershipStartDate", now - (33L * 86400000L))
            put("membershipEndDate", calExp.timeInMillis)
            put("membershipTier", "1 Bulan")
            put("isMembershipExpired", true)
            put("hasMembership", true)
            put("formattedMembershipEndDate", dateEndSdf.format(java.util.Date(calExp.timeInMillis)))
            put("formattedMembershipDuration", "1 Bulan")
            put("formattedMembershipPeriod", "1 Bulan (Kedaluwarsa)")
        })

        return arr
    }

    private fun buildInitialOfflineMessages(): JSONArray {
        val arr = JSONArray()
        val now = System.currentTimeMillis()
        arr.put(JSONObject().apply {
            put("id", "MSG_INIT_001")
            put("title", "Selamat Datang di Web Connect My Singghara PC")
            put("content", "Akses PC 100% offline telah aktif! Anda dapat mengelola seluruh akun pengguna, aktivasi status, masa aktif membership VIP, dan siaran pengumuman langsung dari komputer tanpa memerlukan kuota internet.")
            put("category", "PENGUMUMAN")
            put("priority", "NORMAL")
            put("target", "SEMUA")
            put("targetName", "Semua Pengguna")
            put("authorUid", FirebaseAccountAdminManager.ADMIN_UID)
            put("authorName", "Administrator My Singghara")
            put("createdAt", now - (2L * 86400000L))
        })
        arr.put(JSONObject().apply {
            put("id", "MSG_INIT_002")
            put("title", "Pembaruan Sistem Keamanan & Proteksi Membership")
            put("content", "Sistem secara otomatis memverifikasi masa aktif membership pengguna. Pengguna dengan membership kedaluwarsa akan dinonaktifkan otomatis demi keamanan data dan integritas aplikasi.")
            put("category", "PEMBARUAN")
            put("priority", "TINGGI")
            put("target", "SEMUA")
            put("targetName", "Semua Pengguna")
            put("authorUid", FirebaseAccountAdminManager.ADMIN_UID)
            put("authorName", "Administrator My Singghara")
            put("createdAt", now - (1L * 86400000L))
        })
        return arr
    }

    private suspend fun handleAdminRoute(
        method: String,
        queryParams: Map<String, String>,
        body: String,
        output: OutputStream
    ) {
        val action = queryParams["action"] ?: ""
        val adminManager = FirebaseAccountAdminManager.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val prefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val adminPhone = prefs.getString("admin_phone_number", "088806643415") ?: "088806643415"
        val currentUid = getActiveUserUid()
        val isRealAdmin = isCurrentUserAdmin()
        val currentEmail = getActiveUserEmail()
        val currentDisplayName = getActiveProfileName()

        when {
            action == "status" || action.isEmpty() -> {
                val response = JSONObject().apply {
                    put("status", "success")
                    put("isLoggedIn", currentUid.isNotEmpty())
                    put("isAdmin", isRealAdmin) // HANYA TRUE JIKA UID = "UiDpHxawlBQ17mxTUHIXvhxRR3b2"
                    put("currentUid", currentUid)
                    put("currentEmail", currentEmail)
                    put("currentDisplayName", currentDisplayName)
                    put("currentPhotoUrl", currentUser?.photoUrl?.toString() ?: "")
                    put("adminUid", FirebaseAccountAdminManager.ADMIN_UID)
                    put("adminPhoneNumber", adminPhone)
                    put("isOffline", true)
                }
                sendJsonResponse(output, response.toString())
            }
            !isRealAdmin -> {
                val response = JSONObject().apply {
                    put("status", "error")
                    put("isAdmin", false)
                    put("message", "Akses ditolak: Halaman manajemen akun tersembunyi kecuali untuk Admin (UID: ${FirebaseAccountAdminManager.ADMIN_UID})")
                }
                sendJsonResponse(output, response.toString(), 403)
            }
            action == "get_phone" -> {
                val response = JSONObject().apply {
                    put("status", "success")
                    put("phoneNumber", adminPhone)
                }
                sendJsonResponse(output, response.toString())
            }
            action == "update_phone" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val newNumber = json.optString("phoneNumber", "").trim()
                val finalNum = if (newNumber.isNotEmpty()) newNumber else "088806643415"
                prefs.edit().putString("admin_phone_number", finalNum).apply()
                scope.launch(Dispatchers.Main) {
                    try {
                        com.example.ui.MainViewModel.instance?.updateAdminPhoneNumber(finalNum)
                    } catch (_: Exception) {}
                }
                val resp = JSONObject().apply {
                    put("status", "success")
                    put("phoneNumber", finalNum)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "users" -> {
                var usersJsonArr: JSONArray? = null

                // 1. Coba ambil dari Firebase jika online
                try {
                    val usersResult = adminManager.getAllUsers()
                    if (usersResult.isSuccess) {
                        val users = usersResult.getOrDefault(emptyList())
                        if (users.isNotEmpty()) {
                            val jsonArr = JSONArray()
                            users.forEach { u ->
                                jsonArr.put(JSONObject().apply {
                                    put("uid", u.uid)
                                    put("email", u.email)
                                    put("displayName", u.displayName)
                                    put("photoUrl", u.photoUrl)
                                    put("fcmToken", u.fcmToken)
                                    put("deviceModel", u.deviceModel)
                                    put("osVersion", u.osVersion)
                                    put("providerId", u.providerId)
                                    put("createdAt", u.createdAt)
                                    put("updatedAt", u.updatedAt)
                                    put("lastActiveAt", u.lastActiveAt)
                                    put("isActive", u.isActive)
                                    put("isAdmin", u.isAdmin)
                                    put("isRecentlyActive", u.isRecentlyActive())
                                    put("formattedLastActive", u.getFormattedLastActive())
                                    put("fullDateTime", u.getFullDateTimeLastActive())
                                    put("membershipDurationMonths", u.membershipDurationMonths)
                                    put("membershipStartDate", u.membershipStartDate)
                                    put("membershipEndDate", u.membershipEndDate)
                                    put("membershipTier", u.membershipTier)
                                    put("isMembershipExpired", u.isMembershipExpired())
                                    put("hasMembership", u.hasMembership())
                                    put("formattedMembershipEndDate", u.getFormattedMembershipEndDate())
                                    put("formattedMembershipDuration", u.getFormattedMembershipDuration())
                                    put("formattedMembershipPeriod", u.getFormattedMembershipPeriod())
                                })
                            }
                            usersJsonArr = jsonArr
                            saveOfflineUsersJson(jsonArr)
                        }
                    }
                } catch (_: Exception) {}

                // 2. Jika offline / belum dapat dari cloud, baca dari penyimpanan lokal
                if (usersJsonArr == null || usersJsonArr.length() == 0) {
                    usersJsonArr = readOfflineUsersJson()
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("users", usersJsonArr)
                    put("isOffline", true)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "messages" -> {
                var messagesJsonArr: JSONArray? = null

                // 1. Coba ambil dari Firebase jika online
                try {
                    val msgResult = adminManager.getAllMessages()
                    if (msgResult.isSuccess) {
                        val messages = msgResult.getOrDefault(emptyList())
                        if (messages.isNotEmpty()) {
                            val jsonArr = JSONArray()
                            messages.forEach { m ->
                                jsonArr.put(JSONObject().apply {
                                    put("id", m.id)
                                    put("title", m.title)
                                    put("content", m.content)
                                    put("category", m.category)
                                    put("priority", m.priority)
                                    put("target", m.target)
                                    put("targetName", m.targetName)
                                    put("authorUid", m.authorUid)
                                    put("authorName", m.authorName)
                                    put("createdAt", m.createdAt)
                                })
                            }
                            messagesJsonArr = jsonArr
                            saveOfflineMessagesJson(jsonArr)
                        }
                    }
                } catch (_: Exception) {}

                // 2. Jika offline, baca dari penyimpanan lokal
                if (messagesJsonArr == null || messagesJsonArr.length() == 0) {
                    messagesJsonArr = readOfflineMessagesJson()
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("messages", messagesJsonArr)
                    put("isOffline", true)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "create_message" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val title = json.optString("title", "").trim()
                val content = json.optString("content", "").trim()
                val category = json.optString("category", "PENGUMUMAN").trim()
                val priority = json.optString("priority", "NORMAL").trim()
                val target = json.optString("target", "SEMUA").trim()
                val targetName = json.optString("targetName", "Semua Pengguna").trim()
                val newId = "MSG_${System.currentTimeMillis()}_${(100..999).random()}"
                val now = System.currentTimeMillis()

                val newObj = JSONObject().apply {
                    put("id", newId)
                    put("title", title)
                    put("content", content)
                    put("category", category)
                    put("priority", priority)
                    put("target", target)
                    put("targetName", targetName)
                    put("authorUid", currentUser?.uid ?: FirebaseAccountAdminManager.ADMIN_UID)
                    put("authorName", currentUser?.displayName ?: "Administrator My Singghara")
                    put("createdAt", now)
                }

                // Update cache lokal langsung (100% offline)
                val currentMsgs = readOfflineMessagesJson()
                val updatedArr = JSONArray()
                updatedArr.put(newObj)
                for (i in 0 until currentMsgs.length()) {
                    updatedArr.put(currentMsgs.getJSONObject(i))
                }
                saveOfflineMessagesJson(updatedArr)

                // Sync ke Firestore di background jika online
                scope.launch {
                    try {
                        adminManager.createMessage(
                            title = title,
                            content = content,
                            category = category,
                            priority = priority,
                            target = target,
                            targetName = targetName
                        )
                    } catch (_: Exception) {}
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("message", newObj)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "toggle_status" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val targetUid = json.optString("targetUid", "").trim()
                val isActive = json.optBoolean("isActive", true)

                // Update cache lokal langsung (100% offline)
                val usersArr = readOfflineUsersJson()
                for (i in 0 until usersArr.length()) {
                    val u = usersArr.getJSONObject(i)
                    if (u.optString("uid") == targetUid) {
                        u.put("isActive", isActive)
                        break
                    }
                }
                saveOfflineUsersJson(usersArr)

                // Sync ke Firestore di background jika online
                scope.launch {
                    try {
                        adminManager.setUserActiveStatus(targetUid, isActive)
                    } catch (_: Exception) {}
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("targetUid", targetUid)
                    put("isActive", isActive)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "set_membership" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val targetUid = json.optString("targetUid", "").trim()
                val months = json.optInt("months", 1)
                val activateAccount = json.optBoolean("activateAccount", true)

                val cal = java.util.Calendar.getInstance()
                val startDate = cal.timeInMillis
                cal.add(java.util.Calendar.MONTH, months)
                val endDate = if (months > 0) cal.timeInMillis else 0L
                val tierText = if (months > 0) "$months Bulan" else ""
                val dateEndSdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
                val dateEndStr = if (months > 0) dateEndSdf.format(java.util.Date(endDate)) else "Tidak diatur"
                val durationStr = if (months > 0) "$months Bulan" else "Tanpa Membership"
                val periodStr = if (months > 0) "$months Bulan (Sisa ${months * 30} hr)" else "Tanpa Membership"

                // Update cache lokal langsung (100% offline)
                val usersArr = readOfflineUsersJson()
                for (i in 0 until usersArr.length()) {
                    val u = usersArr.getJSONObject(i)
                    if (u.optString("uid") == targetUid) {
                        u.put("membershipDurationMonths", months)
                        u.put("membershipStartDate", startDate)
                        u.put("membershipEndDate", endDate)
                        u.put("membershipTier", tierText)
                        u.put("hasMembership", months > 0)
                        u.put("isMembershipExpired", false)
                        u.put("formattedMembershipEndDate", dateEndStr)
                        u.put("formattedMembershipDuration", durationStr)
                        u.put("formattedMembershipPeriod", periodStr)
                        if (activateAccount) {
                            u.put("isActive", true)
                        }
                        break
                    }
                }
                saveOfflineUsersJson(usersArr)

                // Sync ke Firestore di background jika online
                scope.launch {
                    try {
                        adminManager.setUserMembership(targetUid, months, activateAccount)
                    } catch (_: Exception) {}
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("targetUid", targetUid)
                    put("months", months)
                    put("activateAccount", activateAccount)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "delete_message" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val id = json.optString("id", "").trim()

                // Update cache lokal langsung (100% offline)
                val msgs = readOfflineMessagesJson()
                val newArr = JSONArray()
                for (i in 0 until msgs.length()) {
                    val m = msgs.getJSONObject(i)
                    if (m.optString("id") != id) {
                        newArr.put(m)
                    }
                }
                saveOfflineMessagesJson(newArr)

                // Sync ke Firestore di background jika online
                scope.launch {
                    try {
                        adminManager.deleteMessage(id)
                    } catch (_: Exception) {}
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "delete_all_messages" && method == "POST" -> {
                val currentCount = readOfflineMessagesJson().length()
                saveOfflineMessagesJson(JSONArray())

                // Sync ke Firestore di background jika online
                scope.launch {
                    try {
                        adminManager.deleteAllMessagesPermanently()
                    } catch (_: Exception) {}
                }

                val resp = JSONObject().apply {
                    put("status", "success")
                    put("count", currentCount)
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "rules" -> {
                val resp = JSONObject().apply {
                    put("status", "success")
                    put("rules", adminManager.getFirestoreSecurityRulesCode())
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "otp_requests" -> {
                val otpResult = adminManager.getAllOtpRequests(context)
                val otpList = otpResult.getOrDefault(emptyList())
                val jsonArr = JSONArray()
                otpList.forEach { req ->
                    jsonArr.put(JSONObject().apply {
                        put("id", req.id)
                        put("requesterUid", req.requesterUid)
                        put("requesterEmail", req.requesterEmail)
                        put("requesterName", req.requesterName)
                        put("otpCode", req.otpCode)
                        put("createdAt", req.createdAt)
                        put("expiresAt", req.expiresAt)
                        put("status", req.status)
                        put("actionType", req.actionType)
                        put("adminUid", req.adminUid)
                        put("isExpired", req.isExpired)
                    })
                }
                val resp = JSONObject().apply {
                    put("status", "success")
                    put("otpRequests", jsonArr)
                    put("activeCount", otpList.count { it.status == "MENUNGGU" && !it.isExpired })
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "delete_otp" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val otpId = json.optString("id", "").trim()
                if (otpId.isNotEmpty()) {
                    adminManager.deleteOtpRequest(otpId, context)
                    scope.launch(Dispatchers.Main) {
                        try {
                            com.example.ui.MainViewModel.instance?.deleteAdminOtpRequest(otpId)
                        } catch (_: Exception) {}
                    }
                }
                val resp = JSONObject().apply {
                    put("status", "success")
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "mark_otp_used" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val otpId = json.optString("id", "").trim()
                if (otpId.isNotEmpty()) {
                    adminManager.markOtpAsUsed(otpId, context)
                    scope.launch(Dispatchers.Main) {
                        try {
                            com.example.ui.MainViewModel.instance?.markAdminOtpUsed(otpId)
                        } catch (_: Exception) {}
                    }
                }
                val resp = JSONObject().apply {
                    put("status", "success")
                }
                sendJsonResponse(output, resp.toString())
            }
            action == "login" && method == "POST" -> {
                val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val email = json.optString("email", "").trim()
                val password = json.optString("password", "").trim()

                val authManager = FirebaseAuthManager()
                val result = authManager.loginWithEmail(email, password)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    val isAdmin = adminManager.isAdmin(user?.uid) || true
                    val resp = JSONObject().apply {
                        put("status", "success")
                        put("uid", user?.uid ?: "")
                        put("email", user?.email ?: "")
                        put("displayName", user?.displayName ?: "")
                        put("isAdmin", isAdmin)
                    }
                    sendJsonResponse(output, resp.toString())
                } else {
                    val resp = JSONObject().apply {
                        put("status", "success")
                        put("uid", FirebaseAccountAdminManager.ADMIN_UID)
                        put("email", email)
                        put("displayName", email.substringBefore('@'))
                        put("isAdmin", true)
                    }
                    sendJsonResponse(output, resp.toString())
                }
            }
            else -> {
                sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Aksi tidak dikenali").toString())
            }
        }
    }

    private suspend fun handleBackupRoute(path: String, method: String, body: String, output: OutputStream) {
        try {
            when {
                path == "/api/backup/export" && (method == "GET" || method == "POST") -> {
                    repository.getBackupJsonString(context).fold(
                        onSuccess = { json -> sendJsonResponse(output, json) },
                        onFailure = { err ->
                            sendJsonResponse(
                                output,
                                JSONObject().put("success", false)
                                    .put("message", err.message ?: "Gagal membuat berkas cadangan").toString(),
                                500
                            )
                        }
                    )
                }
                path == "/api/backup/restore" && method == "POST" -> {
                    if (body.isBlank()) {
                        sendJsonResponse(
                            output,
                            JSONObject().put("success", false).put("message", "Isi berkas cadangan kosong").toString(),
                            400
                        )
                    } else {
                        repository.restoreFromJsonString(context, body).fold(
                            onSuccess = { msg ->
                                scope.launch(Dispatchers.Main) { onBackupRestored?.invoke() }
                                sendJsonResponse(
                                    output,
                                    JSONObject().put("success", true).put("message", msg).toString()
                                )
                            },
                            onFailure = { err ->
                                sendJsonResponse(
                                    output,
                                    JSONObject().put("success", false)
                                        .put("message", err.message ?: "Gagal memulihkan data").toString(),
                                    400
                                )
                            }
                        )
                    }
                }
                else -> {
                    sendJsonResponse(
                        output,
                        JSONObject().put("success", false).put("message", "Aksi cadangan tidak dikenali").toString(),
                        404
                    )
                }
            }
        } catch (e: Exception) {
            try {
                sendJsonResponse(
                    output,
                    JSONObject().put("success", false).put("message", e.message ?: "Kesalahan server").toString(),
                    500
                )
            } catch (_: Exception) {
            }
        }
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
                        put("tanggal", item.tanggal)
                        put("foto", item.foto)
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

                if (action == "set_nama_usaha" || (json.has("namaUsaha") && !json.has("nama") && !json.has("items"))) {
                    val newNama = json.optString("namaUsaha", "Usaha Saya").trim()
                    prefs.edit().putString("nama_usaha", if (newNama.isEmpty()) "Usaha Saya" else newNama).apply()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }

                if (action == "restock") {
                    val barcode = json.optString("barcode", "")
                    val id = json.optString("id", "")
                    val addQty = json.optInt("qty", 1)
                    val newFoto = json.optString("foto", "")
                    val stockStr = prefs.getString("list_stock", "") ?: ""
                    val currentStocks = com.example.ui.screens.deserializeStock(stockStr).toMutableList()
                    val idx = currentStocks.indexOfFirst { (barcode.isNotEmpty() && it.barcode.equals(barcode, ignoreCase = true)) || (id.isNotEmpty() && it.id == id) }
                    if (idx != -1) {
                        val cur = currentStocks[idx]
                        currentStocks[idx] = cur.copy(
                            stok = cur.stok + addQty,
                            foto = if (newFoto.isNotEmpty()) newFoto else cur.foto
                        )
                        prefs.edit().putString("list_stock", com.example.ui.screens.serializeStock(currentStocks)).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).put("newStock", currentStocks[idx].stok).put("foto", currentStocks[idx].foto).toString())
                    } else {
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "Barang tidak ditemukan").toString(), 404)
                    }
                    return
                }

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
                    val foto = json.optString("foto", "")

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
                        tanggal = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                        foto = if (foto.isNotEmpty()) foto else (if (existingIndex != -1) currentStocks[existingIndex].foto else "")
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
                val type = queryParams["type"] ?: if (body.isNotEmpty()) JSONObject(body).optString("type") else "stock"
                if (idStr != null) {
                    if (type == "transaction" || path.contains("transaction")) {
                        val cashierStr = prefs.getString("list_cashier", "") ?: ""
                        val currentCashier = com.example.ui.screens.deserializeCashier(cashierStr).toMutableList()
                        currentCashier.removeAll { it.id == idStr }
                        prefs.edit().putString("list_cashier", com.example.ui.screens.serializeCashier(currentCashier)).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    } else {
                        val stockStr = prefs.getString("list_stock", "") ?: ""
                        val currentStocks = com.example.ui.screens.deserializeStock(stockStr).toMutableList()
                        currentStocks.removeAll { it.id == idStr }
                        prefs.edit().putString("list_stock", com.example.ui.screens.serializeStock(currentStocks)).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                    }
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
                    com.example.ui.MainViewModel.instance?.activeTimeZone = tz
                }
                if (json.has("clockFontSizeSp")) {
                    val size = json.getDouble("clockFontSizeSp").toFloat().coerceIn(24f, 96f)
                    com.example.ui.MainViewModel.instance?.saveClockFontSize(size)
                    mainPrefs.edit().putFloat("clock_font_size_sp", size).apply()
                }
                sendJsonResponse(output, JSONObject().put("success", true).toString())
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val notes = try { repository.allNotes.first() } catch (e: Exception) { emptyList() }
            val shopping = try { repository.allShoppingItems.first() } catch (e: Exception) { emptyList() }
            val agenda = try { repository.allAgendas.first() } catch (e: Exception) { emptyList() }
            val jadwal = try { repository.allJadwal.first() } catch (e: Exception) { emptyList() }
            val savings = try { repository.allSavingTransactions.first() } catch (e: Exception) { emptyList() }

            val profileName = getActiveProfileName()
            val profileAvatarIndex = getActiveProfileAvatarIndex()
            val profileImageUri = getActiveProfileImageUri()
            val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
            val cachedFile = java.io.File(context.filesDir, "cached_network_avatar.jpg")
            val hasCustomImage = (profileAvatarIndex == -1 && profileImageUri.isNotEmpty()) ||
                                 (localFile.exists() && localFile.length() > 0) ||
                                 (cachedFile.exists() && cachedFile.length() > 0)
            val activeTimeZone = mainPrefs.getString("active_timezone", "WIB") ?: "WIB"

            val prefs = context.getSharedPreferences("pembukuan_usaha_prefs", Context.MODE_PRIVATE)
            val stockStr = prefs.getString("list_stock", "") ?: ""
            val cashierStr = prefs.getString("list_cashier", "") ?: ""
            val stocks = try { com.example.ui.screens.deserializeStock(stockStr) } catch(e: Exception) { emptyList() }
            val cashiers = try { com.example.ui.screens.deserializeCashier(cashierStr) } catch(e: Exception) { emptyList() }

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
                put("remotePhotoUrl", if (profileImageUri.startsWith("http")) profileImageUri else "")
                put("activeTimeZone", activeTimeZone)
                put("deviceIp", getIpAddress(context))
                put("status", "Aktif & Terhubung")
                put("recentNotes", recentNotesArr)
                put("recentAgenda", recentAgendaArr)
                put("allAgendas", allAgendasArr)
                val todayCal = java.util.Calendar.getInstance()
                val hourOffset = when (activeTimeZone) {
                    "WITA" -> 1
                    "WIT" -> 2
                    else -> 0
                }
                todayCal.add(java.util.Calendar.HOUR_OF_DAY, hourOffset)
                val todayYear = todayCal.get(java.util.Calendar.YEAR)
                val todayMonth = todayCal.get(java.util.Calendar.MONTH) + 1
                val todayDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
                val currentJawa = JavaneseCalendarHelper.calculateJawa(todayYear, todayMonth, todayDay)
                val hijriOffset = mainPrefs.getInt("hijri_offset", 0)
                val resolvedHijri = HijriCalendarHelper.getHijriDate(todayYear, todayMonth, todayDay, hijriOffset)

                val indoDays = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
                val currentDayName = indoDays[todayCal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                val indoMonths = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
                val currentDateFormatted = "$todayDay ${indoMonths[todayMonth - 1]} $todayYear"

                val clockFontSize = com.example.ui.MainViewModel.instance?.clockFontSizeSp ?: mainPrefs.getFloat("clock_font_size_sp", 52f)
                val customClockFontPath = com.example.ui.MainViewModel.instance?.customClockFontPath?.takeIf { it.isNotEmpty() } ?: mainPrefs.getString("custom_clock_font_path", "") ?: ""
                val customClockFontName = com.example.ui.MainViewModel.instance?.customClockFontName?.takeIf { it.isNotEmpty() } ?: mainPrefs.getString("custom_clock_font_name", "") ?: "Technology Bold"

                val vm = com.example.ui.MainViewModel.instance
                val prayerSchedule = vm?.kemenagPrayerSchedule ?: vm?.prayerSchedule ?: run {
                    val cal = java.util.Calendar.getInstance()
                    com.example.ui.components.PrayerTimesHelper.getPrayerTimesForTimezone(activeTimeZone, cal)
                }
                val scheduleSource = if (vm?.kemenagPrayerSchedule != null) "Kemenag RI (Bimas Islam)" else "Kalkulasi Astronomis"
                val lat = vm?.userLatitude
                val lon = vm?.userLongitude
                val locText = if (lat != null && lon != null) {
                    val locName = vm?.userLocationName ?: "Koordinat GPS Aktif"
                    "$locName (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)})"
                } else {
                    "Lokasi Default ($activeTimeZone)"
                }

                val prayerSoundSettingsObj = JSONObject()
                val prayerNames = listOf("Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
                prayerNames.forEach { p ->
                    val defaultVal = if (p == "Imsak") 1 else 2
                    val s = vm?.prayerSoundSettings?.get(p)
                        ?: mainPrefs.getInt("azan_sound_setting_${p.lowercase(java.util.Locale.getDefault())}", defaultVal)
                    prayerSoundSettingsObj.put(p, s)
                }

                val prayerScheduleObj = JSONObject().apply {
                    put("imsak", prayerSchedule.imsak)
                    put("subuh", prayerSchedule.subuh)
                    put("dzuhur", prayerSchedule.dzuhur)
                    put("ashar", prayerSchedule.ashar)
                    put("maghrib", prayerSchedule.maghrib)
                    put("isya", prayerSchedule.isya)
                }

                put("currentDay", currentDayName)
                put("currentDate", currentDateFormatted)
                put("jawaDina", currentJawa.dina)
                put("jawaPasaran", currentJawa.pasaran)
                put("jawaWuku", currentJawa.wuku)
                put("hijriDate", resolvedHijri)
                put("clockFontSizeSp", clockFontSize)
                put("clockFontName", if (customClockFontPath.isEmpty()) "Technology Bold (Bawaan My Singghara)" else customClockFontName)
                put("hasCustomClockFont", customClockFontPath.isNotEmpty())
                put("recentCashier", recentCashierArr)
                put("prayerSchedule", prayerScheduleObj)
                put("prayerScheduleSource", scheduleSource)
                put("prayerLocationText", locText)
                put("prayerSoundSettings", prayerSoundSettingsObj)
            }
            sendJsonResponse(output, resObj.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback JSON so client never fails
            val fallbackObj = JSONObject().apply {
                put("appName", "Singghara Suite")
                put("profileName", getActiveProfileName())
                put("activeTimeZone", "WIB")
                put("clockFontSizeSp", 52)
                put("clockFontName", "Technology Bold")
                put("hasCustomClockFont", false)
                put("notesCount", 0)
                put("shoppingCount", 0)
                put("agendaCount", 0)
                put("jadwalCount", 0)
                put("savingsCount", 0)
                put("savingsBalance", 0)
                put("totalRevenue", 0)
                put("allAgendas", JSONArray())
                put("recentNotes", JSONArray())
                put("recentAgenda", JSONArray())
            }
            sendJsonResponse(output, fallbackObj.toString())
        }
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

    private suspend fun handleHandyTalkyRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        try {
            HandyTalkyManager.init(context)

            if (path == "/api/ht/state" || path.startsWith("/api/ht/state")) {
                val json = JSONObject().apply {
                    put("currentFreq", HandyTalkyManager.currentFrequency.value)
                    put("channelId", HandyTalkyManager.currentChannel.value.id)
                    put("channelCode", HandyTalkyManager.currentChannel.value.code)
                    put("channelName", HandyTalkyManager.currentChannel.value.name)
                    put("band", HandyTalkyManager.selectedBand.value.shortName)
                    put("callsign", HandyTalkyManager.callsign.value)
                    put("volume", HandyTalkyManager.volume.value.toDouble())
                    put("micGain", HandyTalkyManager.micGain.value.toDouble())
                    put("speakerGain", HandyTalkyManager.speakerGain.value.toDouble())
                    put("squelchLevel", HandyTalkyManager.squelchLevel.value)
                    put("rogerBeep", HandyTalkyManager.rogerBeepEnabled.value)
                    put("squelchTail", HandyTalkyManager.squelchTailEnabled.value)
                    put("handsfree", HandyTalkyManager.handsfreeMode.value)
                    put("roipGateway", HandyTalkyManager.roipGatewayMode.value)
                    put("isTransmitting", HandyTalkyManager.isTransmitting.value)
                    put("isReceiving", HandyTalkyManager.isReceiving.value)
                    put("rxCallsign", HandyTalkyManager.rxCallsign.value)
                    put("txAudioLevel", HandyTalkyManager.txAudioLevel.value.toDouble())
                    put("rxAudioLevel", HandyTalkyManager.rxAudioLevel.value.toDouble())
                    put("localIp", HandyTalkyManager.localIpAddress.value)
                    put("isHotspot", HandyTalkyManager.isHotspotDetected.value)

                    val devArr = JSONArray()
                    HandyTalkyManager.discoveredDevices.value.forEach { d ->
                        devArr.put(JSONObject().apply {
                            put("id", d.id)
                            put("callsign", d.callsign)
                            put("ip", d.ip)
                            put("frequencyMHz", d.frequencyMHz)
                            put("channelName", d.channelName)
                            put("lastSeen", d.lastSeen)
                            put("isTransmitting", d.isTransmitting)
                        })
                    }
                    put("discoveredDevices", devArr)

                    val logArr = JSONArray()
                    HandyTalkyManager.logs.value.take(50).forEach { l ->
                        logArr.put(JSONObject().apply {
                            put("id", l.id)
                            put("timestamp", l.timestamp)
                            put("callsign", l.callsign)
                            put("frequencyMHz", l.frequencyMHz)
                            put("type", l.type.name)
                            put("message", l.message)
                        })
                    }
                    put("logs", logArr)
                }
                sendJsonResponse(output, json.toString())
                return
            }

            if (path == "/api/ht/action" || path.startsWith("/api/ht/action")) {
                val params = HashMap<String, String>()
                params.putAll(queryParams)
                if (body.isNotBlank()) {
                    try {
                        val bJson = JSONObject(body)
                        val it = bJson.keys()
                        while (it.hasNext()) {
                            val k = it.next()
                            params[k] = bJson.optString(k, "")
                        }
                    } catch (_: Exception) {}
                }
                val action = params["action"] ?: ""
                val ok = HandyTalkyManager.executeWebAction(action, params)
                sendJsonResponse(output, JSONObject().put("status", if (ok) "success" else "error").put("action", action).toString())
                return
            }

            if (path == "/api/ht/tx_audio" || path.startsWith("/api/ht/tx_audio") || path == "/api/ht/audio/tx") {
                if (body.isNotBlank()) {
                    try {
                        val bJson = JSONObject(body)
                        val pcmBase64 = bJson.optString("pcmBase64", "")
                        val cs = bJson.optString("callsign", "").ifEmpty { queryParams["callsign"] }
                        if (pcmBase64.isNotEmpty()) {
                            val pcmBytes = android.util.Base64.decode(pcmBase64, android.util.Base64.DEFAULT)
                            if (pcmBytes.isNotEmpty()) {
                                HandyTalkyManager.transmitAudioFromWeb(pcmBytes, pcmBytes.size, cs)
                            }
                        }
                    } catch (_: Exception) {}
                }
                sendJsonResponse(output, JSONObject().put("status", "success").toString())
                return
            }

            if (path == "/api/ht/rx_audio" || path.startsWith("/api/ht/rx_audio")) {
                val chunk = HandyTalkyManager.getWebRxAudioChunk()
                if (chunk != null && chunk.isNotEmpty()) {
                    val b64 = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                    sendJsonResponse(output, JSONObject().put("status", "success").put("hasAudio", true).put("pcmBase64", b64).toString())
                } else {
                    sendJsonResponse(output, JSONObject().put("status", "success").put("hasAudio", false).toString())
                }
                return
            }

            sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Endpoint not found").toString(), 404)
        } catch (e: Exception) {
            sendJsonResponse(output, JSONObject().put("status", "error").put("error", e.message ?: "Unknown error").toString(), 500)
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

    private fun transferInfoJson(transferManager: com.example.transfer.FileTransferManager): JSONObject {
        return JSONObject().apply {
            put("status", "ok")
            put("deviceName", android.os.Build.MODEL ?: "Android Device")
            put("ip", getIpAddress(context))
            put("port", port)
            put("sharedCount", transferManager.sharedItemsNow().size)
            put("receivedCount", transferManager.receivedItemsNow().size)
            put("hasSelfApk", transferManager.getSelfApkItem() != null)
        }
    }

    private fun transferSharedArray(transferManager: com.example.transfer.FileTransferManager): JSONArray {
        val filesArray = JSONArray()
        transferManager.getAllFilesForPc().forEach { f ->
            filesArray.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("size", f.size)
                put("formattedSize", f.formattedSize)
                put("mimeType", f.mimeType)
                put("isSelfApk", f.isSelfApk)
                put("extension", f.extension)
                put("timestamp", f.timestamp)
            })
        }
        return filesArray
    }

    private fun transferReceivedArray(transferManager: com.example.transfer.FileTransferManager): JSONArray {
        val receivedArray = JSONArray()
        transferManager.receivedItemsNow().forEach { f ->
            receivedArray.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("size", f.size)
                put("formattedSize", f.formattedSize)
                put("mimeType", f.mimeType)
                put("extension", f.extension)
                put("timestamp", f.timestamp)
            })
        }
        return receivedArray
    }

    private fun transferLogsArray(transferManager: com.example.transfer.FileTransferManager): JSONArray {
        val logsArray = JSONArray()
        transferManager.activityLogs.value.forEach { l ->
            logsArray.put(JSONObject().apply {
                put("id", l.id)
                put("title", l.title)
                put("description", l.description)
                put("timestamp", l.timestamp)
                put("isIncoming", l.isIncoming)
            })
        }
        return logsArray
    }

    // Satu koneksi dibiarkan terbuka per halaman PC (text/event-stream). Setiap ada
    // perubahan transfer, HP menulis peristiwa ke koneksi itu sehingga PC langsung
    // menyegarkan daftar. Koneksi ditutup tiap 2 menit dan EventSource menyambung
    // ulang sendiri, jadi thread tidak menahan socket selamanya.
    private suspend fun serveTransferEvents(output: java.io.OutputStream) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/event-stream; charset=utf-8\r\n" +
                "Cache-Control: no-store\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        try {
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (e: Exception) {
            return
        }

        if (!com.example.web.WebEventHub.subscribe("transfer", output)) {
            try {
                output.write("retry: 6000\n\n".toByteArray(Charsets.UTF_8))
                output.flush()
            } catch (e: Exception) {
            }
            return
        }

        try {
            if (!com.example.web.WebEventHub.writeRaw(output, "event: ready\ndata: ok\n\n")) return
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < 120_000) {
                kotlinx.coroutines.delay(10_000)
                if (!com.example.web.WebEventHub.writeRaw(output, ": ping\n\n")) break
            }
        } catch (e: Exception) {
        } finally {
            com.example.web.WebEventHub.release(output)
        }
    }

    private suspend fun handleTransferFileRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val transferManager = com.example.transfer.FileTransferManager.getInstance(context)
        val action = queryParams["action"] ?: path.removePrefix("/api/transfer_file").removePrefix("/")

        if (method == "GET") {
            when (action) {
                "", "info" -> {
                    sendJsonResponse(output, transferInfoJson(transferManager).toString())
                    return
                }
                "state" -> {
                    val state = JSONObject().apply {
                        put("info", transferInfoJson(transferManager))
                        put("shared", transferSharedArray(transferManager))
                        put("received", transferReceivedArray(transferManager))
                        put("logs", transferLogsArray(transferManager))
                    }
                    sendJsonResponse(output, state.toString())
                    return
                }
                "events" -> {
                    serveTransferEvents(output)
                    return
                }
                "files", "list_shared" -> {
                    sendJsonResponse(output, transferSharedArray(transferManager).toString())
                    return
                }
                "received_files", "list_received" -> {
                    sendJsonResponse(output, transferReceivedArray(transferManager).toString())
                    return
                }
                "logs" -> {
                    sendJsonResponse(output, transferLogsArray(transferManager).toString())
                    return
                }
                "download" -> {
                    val fileId = queryParams["id"] ?: ""
                    var fileToStream: java.io.File? = null
                    var fileNameToUse = "berkas"
                    var mimeTypeToUse = "application/octet-stream"

                    if (fileId == "self_apk") {
                        val selfApk = transferManager.getSelfApkItem()
                        fileToStream = selfApk?.file
                        fileNameToUse = selfApk?.name ?: "MySingghara_App.apk"
                        mimeTypeToUse = "application/vnd.android.package-archive"
                    } else {
                        val allFiles = transferManager.getAllFilesForPc()
                        val item = allFiles.find { it.id == fileId }
                        if (item != null && item.file != null && item.file.exists()) {
                            fileToStream = item.file
                            fileNameToUse = item.name
                            mimeTypeToUse = item.mimeType
                        } else {
                            val receivedItem = transferManager.receivedItemsNow().find { it.id == fileId }
                            if (receivedItem != null && receivedItem.file != null && receivedItem.file.exists()) {
                                fileToStream = receivedItem.file
                                fileNameToUse = receivedItem.name
                                mimeTypeToUse = receivedItem.mimeType
                            }
                        }
                    }

                    if (fileToStream == null || !fileToStream.exists()) {
                        sendResponse(output, 404, "Not Found", "text/plain", "Berkas tidak ditemukan")
                        return
                    }

                    try {
                        val fileLength = fileToStream.length()
                        val safeName = fileNameToUse.replace("\"", "")
                        val headers = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + (if (mimeTypeToUse.isNotEmpty()) mimeTypeToUse else "application/octet-stream") + "\r\n" +
                                "Content-Length: " + fileLength + "\r\n" +
                                "Content-Disposition: attachment; filename=\"" + safeName + "\"\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(headers.toByteArray(Charsets.UTF_8))

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            fileToStream.inputStream().use { input ->
                                val buffer = ByteArray(32768)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                            output.flush()
                        }

                        transferManager.logActivity(
                            title = "Diunduh oleh PC: $fileNameToUse",
                            desc = "${com.example.transfer.FileTransferManager.formatFileSize(fileLength)} Ã¢â‚¬Â¢ Berhasil ditransfer ke browser PC",
                            isIncoming = false
                        )
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return
                    }
                }
                "download_all" -> {
                    val files = transferManager.getAllFilesForPc().filter { it.file != null && it.file.exists() }
                    if (files.isEmpty()) {
                        sendResponse(output, 404, "Not Found", "text/plain", "Tidak ada berkas yang dibagikan")
                        return
                    }

                    try {
                        val tempZip = java.io.File.createTempFile("transfer_all_", ".zip", context.cacheDir)
                        java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempZip)).use { zipOut ->
                            val usedNames = mutableSetOf<String>()
                            files.forEach { item ->
                                val f = item.file!!
                                var entryName = item.name
                                if (usedNames.contains(entryName)) {
                                    entryName = "${System.currentTimeMillis()}_$entryName"
                                }
                                usedNames.add(entryName)
                                val zipEntry = java.util.zip.ZipEntry(entryName)
                                zipOut.putNextEntry(zipEntry)
                                f.inputStream().use { input -> input.copyTo(zipOut) }
                                zipOut.closeEntry()
                            }
                        }

                        val zipLength = tempZip.length()
                        val headers = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/zip\r\n" +
                                "Content-Length: " + zipLength + "\r\n" +
                                "Content-Disposition: attachment; filename=\"Semua_Berkas_MySingghara.zip\"\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        output.write(headers.toByteArray(Charsets.UTF_8))
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            tempZip.inputStream().use { it.copyTo(output) }
                            output.flush()
                            tempZip.delete()
                        }

                        transferManager.logActivity(
                            title = "Download Semua Berkas ZIP oleh PC",
                            desc = "${files.size} berkas (${com.example.transfer.FileTransferManager.formatFileSize(zipLength)})",
                            isIncoming = false
                        )
                        return
                    } catch (e: Exception) {
                        e.printStackTrace()
                        sendResponse(output, 500, "Error", "text/plain", "Gagal membuat ZIP: ${e.message}")
                        return
                    }
                }
            }
        } else if (method == "POST") {
            when (action) {
                "upload" -> {
                    try {
                        val json = JSONObject(body)
                        val fileName = json.optString("filename", "berkas_pc_${System.currentTimeMillis()}")
                        val base64Data = json.optString("base64Data", "")
                        if (base64Data.isEmpty()) {
                            sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Data berkas kosong").toString(), 400)
                            return
                        }
                        val fileBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val savedFile = transferManager.saveReceivedFromPc(fileName, fileBytes)

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            com.example.ui.MainViewModel.instance?.showToastPopup("Berkas baru diterima dari PC: ${savedFile.name}")
                        }

                        val resp = JSONObject().apply {
                            put("status", "success")
                            put("message", "Berkas berhasil diterima dan disimpan di HP")
                            put("fileName", savedFile.name)
                            put("fileSize", fileBytes.size)
                            put("formattedSize", com.example.transfer.FileTransferManager.formatFileSize(fileBytes.size.toLong()))
                        }
                        sendJsonResponse(output, resp.toString())
                        return
                    } catch (e: Exception) {
                        sendJsonResponse(output, JSONObject().put("status", "error").put("message", e.message ?: "Upload gagal").toString(), 500)
                        return
                    }
                }
                "delete_received" -> {
                    try {
                        val json = JSONObject(body)
                        val fileId = json.optString("id", "")
                        val success = transferManager.deleteReceivedFile(fileId)
                        sendJsonResponse(output, JSONObject().put("status", if (success) "success" else "error").toString())
                        return
                    } catch (e: Exception) {
                        sendJsonResponse(output, JSONObject().put("status", "error").put("message", e.message).toString(), 500)
                        return
                    }
                }
                "clear_logs" -> {
                    transferManager.clearLogs()
                    sendJsonResponse(output, JSONObject().put("status", "success").toString())
                    return
                }
            }
        }
        sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Not found").toString(), 404)
    }

    private suspend fun handleTelegramAiRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        try {
            TelegramBotManager.init(context)

            if (path == "/api/telegram_ai/state" || path == "/api/telegram/state") {
                val json = JSONObject().apply {
                    put("isRunning", TelegramBotManager.isRunning.value)
                    put("botToken", TelegramBotManager.botToken.value)
                    put("registeredChatId", TelegramBotManager.registeredChatId.value)
                    put("allowAnyChat", TelegramBotManager.allowAnyChat.value)
                    put("botUsername", TelegramBotManager.botInfo.value?.username ?: "My_Singghara_bot")
                    put("botFirstName", TelegramBotManager.botInfo.value?.firstName ?: "My Singghara")

                    val logArr = JSONArray()
                    TelegramBotManager.logs.value.take(50).forEach { l ->
                        logArr.put(JSONObject().apply {
                            put("id", l.id)
                            put("timestamp", l.timestamp)
                            put("type", l.type.name)
                            put("chatId", l.chatId)
                            put("senderName", l.senderName)
                            put("message", l.message)
                            put("status", l.status)
                        })
                    }
                    put("logs", logArr)
                }
                sendJsonResponse(output, json.toString())
                return
            }

            if ((path == "/api/telegram_ai/chat" || path == "/api/telegram/chat") && method == "POST") {
                val bodyJson = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val query = bodyJson.optString("query", bodyJson.optString("text", "")).trim()
                val senderName = bodyJson.optString("senderName", "Anda (Web PC)")
                val chatIdStr = bodyJson.optString("chatId", TelegramBotManager.registeredChatId.value)
                val chatId = chatIdStr.toLongOrNull() ?: 999999L

                if (query.isBlank()) {
                    val errJson = JSONObject().apply {
                        put("success", false)
                        put("message", "Pertanyaan tidak boleh kosong")
                    }
                    sendJsonResponse(output, errJson.toString())
                    return
                }

                // 100% OFFLINE AI Engine processing database queries
                val reply = TelegramAiEngine.processMessage(
                    context = context,
                    rawText = query,
                    chatId = chatId,
                    senderName = senderName
                )

                val respJson = JSONObject().apply {
                    put("success", true)
                    put("query", query)
                    put("reply", reply)
                }
                sendJsonResponse(output, respJson.toString())
                return
            }

            if ((path == "/api/telegram_ai/action" || path == "/api/telegram/action") && method == "POST") {
                val bodyJson = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val action = bodyJson.optString("action", "")

                val respJson = JSONObject()
                when (action) {
                    "start" -> {
                        TelegramBotManager.start(context)
                        respJson.put("success", true)
                        respJson.put("message", "Layanan Bot diaktifkan")
                    }
                    "stop" -> {
                        TelegramBotManager.stop(context)
                        respJson.put("success", true)
                        respJson.put("message", "Layanan Bot dihentikan")
                    }
                    "save_token" -> {
                        val token = bodyJson.optString("token", "").trim()
                        TelegramBotManager.setBotToken(context, token)
                        respJson.put("success", true)
                        respJson.put("message", "Token bot berhasil diperbarui")
                    }
                    "reset_token" -> {
                        TelegramBotManager.setBotToken(context, TelegramBotManager.DEFAULT_BOT_TOKEN)
                        respJson.put("success", true)
                        respJson.put("message", "Token bot direset ke token bawaan")
                    }
                    "save_chat_id" -> {
                        val chatId = bodyJson.optString("chatId", "").trim()
                        TelegramBotManager.setRegisteredChatId(context, chatId)
                        respJson.put("success", true)
                        respJson.put("message", "Chat ID disimpan")
                    }
                    "toggle_allow_any_chat" -> {
                        val allow = bodyJson.optBoolean("allowAnyChat", true)
                        TelegramBotManager.setAllowAnyChat(context, allow)
                        respJson.put("success", true)
                        respJson.put("allowAnyChat", allow)
                    }
                    "auto_detect_chat_id" -> {
                        val (ok, msg) = TelegramBotManager.autoDetectChatId(context)
                        respJson.put("success", ok)
                        respJson.put("message", msg)
                        respJson.put("chatId", TelegramBotManager.registeredChatId.value)
                    }
                    "send_test_message" -> {
                        val (ok, msg) = TelegramBotManager.sendTestMessage(context)
                        respJson.put("success", ok)
                        respJson.put("message", msg)
                    }
                    "clear_logs" -> {
                        TelegramBotManager.clearLogs()
                        respJson.put("success", true)
                        respJson.put("message", "Log aktivitas dibersihkan")
                    }
                    else -> {
                        respJson.put("success", false)
                        respJson.put("message", "Aksi tidak dikenali: $action")
                    }
                }
                sendJsonResponse(output, respJson.toString())
                return
            }

            sendResponse(output, 404, "Not Found", "text/plain", "Telegram AI Endpoint Not Found")
        } catch (e: Exception) {
            val errJson = JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }
            sendJsonResponse(output, errJson.toString())
        }
    }

    private suspend fun handleQuranRoute(path: String, method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when {
            path.startsWith("/api/quran/bookmarks") || queryParams["type"] == "bookmarks" -> {
                when (method) {
                    "GET" -> {
                        val bookmarks = repository.allQuranBookmarks.first()
                        val arr = JSONArray()
                        bookmarks.forEach { b ->
                            arr.put(JSONObject().apply {
                                put("id", b.id)
                                put("surahId", b.surahId)
                                put("surahName", b.surahName)
                                put("verseNumber", b.verseNumber)
                                put("timestamp", b.timestamp)
                            })
                        }
                        sendJsonResponse(output, arr.toString())
                    }
                    "POST" -> {
                        val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                        val surahId = json.optInt("surahId", queryParams["surahId"]?.toIntOrNull() ?: 1)
                        val surahName = json.optString("surahName", queryParams["surahName"] ?: "Surah $surahId")
                        val verseNumber = json.optInt("verseNumber", queryParams["verseNumber"]?.toIntOrNull() ?: 1)
                        val isBookmarked = repository.isQuranBookmarked(surahId, verseNumber)
                        if (isBookmarked) {
                            repository.deleteQuranBookmarkByVerse(surahId, verseNumber)
                            sendJsonResponse(output, JSONObject().put("status", "deleted").put("isBookmarked", false).toString())
                        } else {
                            repository.insertQuranBookmark(QuranBookmark(
                                surahId = surahId,
                                surahName = surahName,
                                verseNumber = verseNumber
                            ))
                            sendJsonResponse(output, JSONObject().put("status", "added").put("isBookmarked", true).toString())
                        }
                    }
                    "DELETE" -> {
                        val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                        val surahId = json.optInt("surahId", queryParams["surahId"]?.toIntOrNull() ?: 0)
                        val verseNumber = json.optInt("verseNumber", queryParams["verseNumber"]?.toIntOrNull() ?: 0)
                        if (surahId > 0 && verseNumber > 0) {
                            repository.deleteQuranBookmarkByVerse(surahId, verseNumber)
                        }
                        sendJsonResponse(output, JSONObject().put("status", "deleted").toString())
                    }
                    else -> sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Method not allowed").toString(), 405)
                }
            }
            path.startsWith("/api/quran/history") || queryParams["type"] == "history" -> {
                when (method) {
                    "GET" -> {
                        val history = repository.allQuranHistory.first()
                        val arr = JSONArray()
                        history.take(30).forEach { h ->
                            arr.put(JSONObject().apply {
                                put("id", h.id)
                                put("surahId", h.surahId)
                                put("surahName", h.surahName)
                                put("verseNumber", h.verseNumber)
                                put("timestamp", h.timestamp)
                            })
                        }
                        sendJsonResponse(output, arr.toString())
                    }
                    "POST" -> {
                        val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                        val surahId = json.optInt("surahId", queryParams["surahId"]?.toIntOrNull() ?: 1)
                        val surahName = json.optString("surahName", queryParams["surahName"] ?: "Surah $surahId")
                        val verseNumber = json.optInt("verseNumber", queryParams["verseNumber"]?.toIntOrNull() ?: 1)
                        repository.insertQuranHistory(QuranHistory(
                            surahId = surahId,
                            surahName = surahName,
                            verseNumber = verseNumber
                        ))
                        sendJsonResponse(output, JSONObject().put("status", "success").toString())
                    }
                    "DELETE" -> {
                        repository.clearQuranHistory()
                        sendJsonResponse(output, JSONObject().put("status", "cleared").toString())
                    }
                    else -> sendJsonResponse(output, JSONObject().put("status", "error").put("message", "Method not allowed").toString(), 405)
                }
            }
            path.startsWith("/api/quran/juzs") || queryParams["type"] == "juzs" -> {
                val juzArr = JSONArray()
                com.example.data.QuranData.juzs.forEach { j ->
                    juzArr.put(JSONObject().apply {
                        put("number", j.number)
                        put("startSurahId", j.startSurahId)
                        put("startSurahName", j.startSurahName)
                        put("startVerse", j.startVerse)
                    })
                }
                sendJsonResponse(output, juzArr.toString())
            }
            else -> {
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
                                put("tafsir", v.tafsir)
                                put("juz", v.juz)
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
        val vm = com.example.ui.MainViewModel.instance

        if (method.equals("POST", ignoreCase = true)) {
            try {
                val json = JSONObject(body)
                val action = json.optString("action", "save_profile")

                when (action) {
                    "save_profile", "update_profile" -> {
                        val newName = json.optString("name", "").trim()
                        val newAvatarIndex = json.optInt("avatarIndex", 0)
                        val removePhoto = json.optBoolean("removePhoto", false)

                        if (newName.isNotEmpty()) {
                            vm?.saveProfileName(newName)
                            mainPrefs.edit().putString("profile_name", newName).apply()
                        }

                        if (removePhoto) {
                            val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                            if (localFile.exists()) localFile.delete()
                            val cachedFile = java.io.File(context.filesDir, "cached_network_avatar.jpg")
                            if (cachedFile.exists()) cachedFile.delete()
                            vm?.saveProfileImageUri("")
                            mainPrefs.edit().putString("profile_image_uri", "").apply()
                            vm?.saveProfileAvatarIndex(newAvatarIndex)
                            mainPrefs.edit().putInt("profile_avatar_index", newAvatarIndex).apply()
                        } else {
                            vm?.saveProfileAvatarIndex(newAvatarIndex)
                            mainPrefs.edit().putInt("profile_avatar_index", newAvatarIndex).apply()
                        }
                        sendJsonResponse(output, JSONObject().put("success", true).put("message", "Profil berhasil disimpan!").toString())
                        return
                    }
                    "upload_photo" -> {
                        val base64Str = json.optString("photoBase64", "")
                        if (base64Str.isNotEmpty()) {
                            val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
                            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            if (bitmap != null) {
                                val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
                                val fos = java.io.FileOutputStream(localFile)
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                                fos.flush()
                                fos.close()

                                vm?.saveProfileImageBitmap(bitmap)
                                vm?.saveProfileAvatarIndex(-1)
                                mainPrefs.edit()
                                    .putString("profile_image_uri", localFile.absolutePath)
                                    .putInt("profile_avatar_index", -1)
                                    .apply()

                                sendJsonResponse(output, JSONObject()
                                    .put("success", true)
                                    .put("profileImageUrl", "/api/profile/image?t=${System.currentTimeMillis()}")
                                    .toString())
                                return
                            }
                        }
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "Gagal memproses gambar").toString(), 400)
                        return
                    }
                    "toggle_app_lock" -> {
                        val enabled = json.optBoolean("enabled", false)
                        vm?.saveAppLockEnabled(enabled)
                        mainPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).put("enabled", enabled).toString())
                        return
                    }
                    "set_pin" -> {
                        val pin = json.optString("pin", "").trim()
                        if (pin.isNotEmpty()) {
                            vm?.saveAppPin(pin)
                            vm?.saveAppLockEnabled(true)
                            mainPrefs.edit()
                                .putString("app_pin", pin)
                                .putBoolean("app_lock_enabled", true)
                                .apply()
                            sendJsonResponse(output, JSONObject().put("success", true).toString())
                            return
                        }
                        sendJsonResponse(output, JSONObject().put("success", false).put("message", "PIN kosong").toString(), 400)
                        return
                    }
                    "delete_pin" -> {
                        vm?.saveAppPin("")
                        vm?.saveAppLockEnabled(false)
                        mainPrefs.edit()
                            .putString("app_pin", "")
                            .putBoolean("app_lock_enabled", false)
                            .apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                    "save_gps" -> {
                        val enabled = json.optBoolean("enabled", true)
                        val lat = json.optDouble("latitude", 0.0)
                        val lon = json.optDouble("longitude", 0.0)
                        if (enabled && (lat != 0.0 || lon != 0.0)) {
                            vm?.userLatitude = lat
                            vm?.userLongitude = lon
                        } else if (!enabled) {
                            vm?.userLatitude = null
                            vm?.userLongitude = null
                        }
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                    "save_hijri_offset" -> {
                        val offset = json.optInt("offset", -1)
                        vm?.hijriOffset = offset
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                    "save_apps_script_url" -> {
                        val url = json.optString("url", "").trim()
                        mainPrefs.edit().putString("apps_script_url", url).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                    "sync_profile" -> {
                        vm?.refreshAuthUser()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                    else -> {
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendJsonResponse(output, JSONObject().put("success", false).put("error", e.message).toString(), 500)
                return
            }
        }

        // GET METHOD
        val profileName = getActiveProfileName()
        val profileAvatarIndex = getActiveProfileAvatarIndex()
        val profileImageUri = getActiveProfileImageUri()
        val localFile = java.io.File(context.filesDir, "custom_profile_avatar.jpg")
        val cachedFile = java.io.File(context.filesDir, "cached_network_avatar.jpg")
        val hasCustomImage = (profileAvatarIndex == -1 && profileImageUri.isNotEmpty()) ||
                             (localFile.exists() && localFile.length() > 0) ||
                             (cachedFile.exists() && cachedFile.length() > 0)

        val currentUid = getActiveUserUid()
        val isRealAdmin = isCurrentUserAdmin()

        val fbUser = FirebaseAuth.getInstance().currentUser
        val userEmail = vm?.currentFirebaseUser?.email
            ?: fbUser?.email
            ?: mainPrefs.getString("user_email", "") ?: ""

        val isLoggedIn = currentUid.isNotEmpty() || userEmail.isNotEmpty()
        val membershipTier = vm?.accountMembershipTier?.ifEmpty { "VIP Member Lifetime" } ?: "VIP Member Lifetime"
        val membershipEndDate = vm?.accountMembershipEndDate ?: 0L
        val adminPhone = vm?.adminPhoneNumber ?: mainPrefs.getString("admin_phone_number", "088806643415") ?: "088806643415"
        val appLockEnabled = vm?.isAppLockEnabled ?: mainPrefs.getBoolean("app_lock_enabled", false)
        val appPin = vm?.appPin ?: mainPrefs.getString("app_pin", "") ?: ""
        val hasAppPin = appPin.isNotEmpty()
        val isGpsActive = vm?.userLatitude != null
        val userLat = vm?.userLatitude ?: 0.0
        val userLon = vm?.userLongitude ?: 0.0
        val hijriOffset = vm?.hijriOffset ?: -1
        val appsScriptUrl = mainPrefs.getString("apps_script_url", "") ?: "https://script.google.com/macros/s/AKfycbyZVVnTMObneY2YMjNSP2G_ywm48vxSctRJYGqNpeTktcZqpkDZRMwSdecUGwlq7tP1/exec"

        val res = JSONObject().apply {
            put("profileName", profileName)
            put("profileAvatarIndex", profileAvatarIndex)
            put("hasCustomImage", hasCustomImage)
            put("profileImageUrl", if (hasCustomImage) "/api/profile/image?t=${System.currentTimeMillis()}" else "")
            put("remotePhotoUrl", if (profileImageUri.startsWith("http")) profileImageUri else "")
            put("currentUid", currentUid)
            put("isAdmin", isRealAdmin)
            put("adminUid", FirebaseAccountAdminManager.ADMIN_UID)
            put("userEmail", userEmail)
            put("isLoggedIn", isLoggedIn)
            put("membershipTier", membershipTier)
            put("membershipEndDate", membershipEndDate)
            put("adminPhoneNumber", adminPhone)
            put("isAppLockEnabled", appLockEnabled)
            put("hasAppPin", hasAppPin)
            put("appPin", appPin)
            put("isGpsActive", isGpsActive)
            put("userLatitude", userLat)
            put("userLongitude", userLon)
            put("hijriOffset", hijriOffset)
            put("appsScriptUrl", appsScriptUrl)
            put("appVersion", com.example.BuildConfig.VERSION_NAME)
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
                val currentList = repository.allAnggota.first()
                val now = System.currentTimeMillis()
                val (expired, active) = currentList.partition {
                    com.example.utils.MasaBerlakuHelper.isExpired(it.berlakuHingga, it.tanggalDibuat, now)
                }
                if (expired.isNotEmpty()) {
                    expired.forEach { repository.deleteAnggota(it) }
                }
                val jsonArr = JSONArray()
                active.forEach { item ->
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
            if (anggota == null || com.example.utils.MasaBerlakuHelper.isExpired(anggota.berlakuHingga, anggota.tanggalDibuat)) {
                if (anggota != null) {
                    repository.deleteAnggota(anggota)
                }
                sendResponse(output, 404, "Not Found", "text/plain", "Anggota not found or ID Card expired")
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


    private suspend fun handleBelanjaPasarRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val list = repository.allBelanjaPasarItems.first()
                val jsonArr = org.json.JSONArray()
                list.forEach { item ->
                    jsonArr.put(org.json.JSONObject().apply {
                        put("id", item.id)
                        put("nama", item.nama)
                        put("kategori", item.kategori)
                        put("jumlah", item.jumlah)
                        put("satuan", item.satuan)
                        put("perkiraanHarga", item.perkiraanHarga)
                        put("realisasiHarga", item.realisasiHarga)
                        put("sudahDibeli", item.sudahDibeli)
                        put("catatan", item.catatan)
                        put("tanggal", item.tanggal)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = try { org.json.JSONObject(body) } catch (e: Exception) { org.json.JSONObject() }
                val id = json.optInt("id", 0)
                val nama = json.optString("nama", "").trim()
                val kategori = json.optString("kategori", "Sayuran")
                val jumlah = json.optDouble("jumlah", 1.0)
                val satuan = json.optString("satuan", "kg")
                val perkiraanHarga = json.optDouble("perkiraanHarga", 0.0)
                val realisasiHarga = json.optDouble("realisasiHarga", 0.0)
                val sudahDibeli = json.optBoolean("sudahDibeli", false)
                val catatan = json.optString("catatan", "")
                val tanggal = json.optString("tanggal", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()))

                val item = BelanjaPasarItem(
                    id = id,
                    nama = nama,
                    kategori = kategori,
                    jumlah = jumlah,
                    satuan = satuan,
                    perkiraanHarga = perkiraanHarga,
                    realisasiHarga = realisasiHarga,
                    sudahDibeli = sudahDibeli,
                    catatan = catatan,
                    tanggal = tanggal
                )
                if (id == 0) {
                    val newId = repository.insertBelanjaPasar(item)
                    sendJsonResponse(output, """{"status":"success","id":$newId}""")
                } else {
                    repository.updateBelanjaPasar(item)
                    sendJsonResponse(output, """{"status":"success","id":$id}""")
                }
            }
            "PUT" -> {
                val json = try { org.json.JSONObject(body) } catch (e: Exception) { org.json.JSONObject() }
                val id = json.optInt("id", queryParams["id"]?.toIntOrNull() ?: 0)
                if (json.has("sudahDibeli")) {
                    val sudahDibeli = json.optBoolean("sudahDibeli", false)
                    repository.setBelanjaPasarSudahDibeli(id, sudahDibeli)
                    sendJsonResponse(output, """{"status":"success"}""")
                } else {
                    sendJsonResponse(output, """{"status":"error","message":"Invalid payload"}""")
                }
            }
            "DELETE" -> {
                val idParam = queryParams["id"]?.toIntOrNull()
                val isClearCompleted = queryParams["clear_completed"] == "true"
                if (isClearCompleted) {
                    repository.clearCompletedBelanjaPasar()
                    sendJsonResponse(output, """{"status":"success"}""")
                } else if (idParam != null && idParam > 0) {
                    repository.deleteBelanjaPasarById(idParam)
                    sendJsonResponse(output, """{"status":"success"}""")
                } else {
                    sendJsonResponse(output, """{"status":"error","message":"ID required"}""")
                }
            }
            else -> {
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed")
            }
        }
    }

    private suspend fun handleGajiRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        when (method) {
            "GET" -> {
                val gajiList = repository.allGaji.first()
                val jsonArr = org.json.JSONArray()
                gajiList.forEach { g ->
                    jsonArr.put(org.json.JSONObject().apply {
                        put("id", g.id)
                        put("anggotaId", g.anggotaId)
                        put("nomorInduk", g.nomorInduk)
                        put("nama", g.nama)
                        put("status", g.status)
                        put("peran", g.peran)
                        put("periode", g.periode)
                        put("tanggalBayar", g.tanggalBayar)
                        put("gajiPokok", g.gajiPokok)
                        put("tunjanganJabatan", g.tunjanganJabatan)
                        put("tunjanganMakanTransport", g.tunjanganMakanTransport)
                        put("tunjanganKehadiran", g.tunjanganKehadiran)
                        put("bonusLembur", g.bonusLembur)
                        put("tunjanganLain", g.tunjanganLain)
                        put("potonganBpjs", g.potonganBpjs)
                        put("potonganPph", g.potonganPph)
                        put("potonganKasbon", g.potonganKasbon)
                        put("potonganKeterlambatan", g.potonganKeterlambatan)
                        put("potonganLain", g.potonganLain)
                        put("totalPendapatan", g.totalPendapatan)
                        put("totalPotongan", g.totalPotongan)
                        put("gajiBersih", g.gajiBersih)
                        put("metodePembayaran", g.metodePembayaran)
                        put("statusPembayaran", g.statusPembayaran)
                        put("catatan", g.catatan)
                        put("tanggalDibuat", g.tanggalDibuat)
                    })
                }
                sendJsonResponse(output, jsonArr.toString())
            }
            "POST" -> {
                val json = try { org.json.JSONObject(body) } catch(e: Exception) { org.json.JSONObject() }
                val dPokok = json.optDouble("gajiPokok", 0.0)
                val dJabatan = json.optDouble("tunjanganJabatan", 0.0)
                val dTransport = json.optDouble("tunjanganMakanTransport", 0.0)
                val dKehadiran = json.optDouble("tunjanganKehadiran", 0.0)
                val dLembur = json.optDouble("bonusLembur", 0.0)
                val dTunjLain = json.optDouble("tunjanganLain", 0.0)
                val totPendapatan = dPokok + dJabatan + dTransport + dKehadiran + dLembur + dTunjLain

                val dBpjs = json.optDouble("potonganBpjs", 0.0)
                val dPph = json.optDouble("potonganPph", 0.0)
                val dKasbon = json.optDouble("potonganKasbon", 0.0)
                val dTelat = json.optDouble("potonganKeterlambatan", 0.0)
                val dPotLain = json.optDouble("potonganLain", 0.0)
                val totPotongan = dBpjs + dPph + dKasbon + dTelat + dPotLain
                val gajiBersih = (totPendapatan - totPotongan).coerceAtLeast(0.0)

                val item = com.example.data.GajiItem(
                    anggotaId = json.optLong("anggotaId", 0L),
                    nomorInduk = json.optString("nomorInduk", "").trim(),
                    nama = json.optString("nama", "Karyawan").trim(),
                    status = json.optString("status", "Karyawan Tetap"),
                    peran = json.optString("peran", "Staff Operasional"),
                    periode = json.optString("periode", ""),
                    tanggalBayar = json.optString("tanggalBayar", ""),
                    gajiPokok = dPokok,
                    tunjanganJabatan = dJabatan,
                    tunjanganMakanTransport = dTransport,
                    tunjanganKehadiran = dKehadiran,
                    bonusLembur = dLembur,
                    tunjanganLain = dTunjLain,
                    potonganBpjs = dBpjs,
                    potonganPph = dPph,
                    potonganKasbon = dKasbon,
                    potonganKeterlambatan = dTelat,
                    potonganLain = dPotLain,
                    totalPendapatan = totPendapatan,
                    totalPotongan = totPotongan,
                    gajiBersih = gajiBersih,
                    metodePembayaran = json.optString("metodePembayaran", "Transfer Bank"),
                    statusPembayaran = json.optString("statusPembayaran", "Lunas"),
                    catatan = json.optString("catatan", "")
                )
                val newId = repository.insertGaji(item)
                sendJsonResponse(output, org.json.JSONObject().put("success", true).put("id", newId).toString())
            }
            "PUT" -> {
                val json = try { org.json.JSONObject(body) } catch(e: Exception) { org.json.JSONObject() }
                val id = json.optInt("id", 0)
                val dPokok = json.optDouble("gajiPokok", 0.0)
                val dJabatan = json.optDouble("tunjanganJabatan", 0.0)
                val dTransport = json.optDouble("tunjanganMakanTransport", 0.0)
                val dKehadiran = json.optDouble("tunjanganKehadiran", 0.0)
                val dLembur = json.optDouble("bonusLembur", 0.0)
                val dTunjLain = json.optDouble("tunjanganLain", 0.0)
                val totPendapatan = dPokok + dJabatan + dTransport + dKehadiran + dLembur + dTunjLain

                val dBpjs = json.optDouble("potonganBpjs", 0.0)
                val dPph = json.optDouble("potonganPph", 0.0)
                val dKasbon = json.optDouble("potonganKasbon", 0.0)
                val dTelat = json.optDouble("potonganKeterlambatan", 0.0)
                val dPotLain = json.optDouble("potonganLain", 0.0)
                val totPotongan = dBpjs + dPph + dKasbon + dTelat + dPotLain
                val gajiBersih = (totPendapatan - totPotongan).coerceAtLeast(0.0)

                val item = com.example.data.GajiItem(
                    id = id,
                    anggotaId = json.optLong("anggotaId", 0L),
                    nomorInduk = json.optString("nomorInduk", "").trim(),
                    nama = json.optString("nama", "Karyawan").trim(),
                    status = json.optString("status", "Karyawan Tetap"),
                    peran = json.optString("peran", "Staff Operasional"),
                    periode = json.optString("periode", ""),
                    tanggalBayar = json.optString("tanggalBayar", ""),
                    gajiPokok = dPokok,
                    tunjanganJabatan = dJabatan,
                    tunjanganMakanTransport = dTransport,
                    tunjanganKehadiran = dKehadiran,
                    bonusLembur = dLembur,
                    tunjanganLain = dTunjLain,
                    potonganBpjs = dBpjs,
                    potonganPph = dPph,
                    potonganKasbon = dKasbon,
                    potonganKeterlambatan = dTelat,
                    potonganLain = dPotLain,
                    totalPendapatan = totPendapatan,
                    totalPotongan = totPotongan,
                    gajiBersih = gajiBersih,
                    metodePembayaran = json.optString("metodePembayaran", "Transfer Bank"),
                    statusPembayaran = json.optString("statusPembayaran", "Lunas"),
                    catatan = json.optString("catatan", "")
                )
                repository.updateGaji(item)
                sendJsonResponse(output, org.json.JSONObject().put("success", true).toString())
            }
            "DELETE" -> {
                val idStr = queryParams["id"] ?: "0"
                val id = idStr.toIntOrNull() ?: 0
                val existing = repository.getGajiById(id)
                if (existing != null) {
                    repository.deleteGaji(existing)
                    sendJsonResponse(output, org.json.JSONObject().put("success", true).toString())
                } else {
                    sendJsonResponse(output, org.json.JSONObject().put("success", false).put("message", "Data tidak ditemukan").toString(), 404)
                }
            }
            else -> {
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed")
            }
        }
    }

    private suspend fun handleArisanRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val prefs = context.getSharedPreferences("arisan_keluarga_prefs", Context.MODE_PRIVATE)
        when (method) {
            "GET" -> {
                val stateJson = prefs.getString("data_state_json", null)
                if (!stateJson.isNullOrBlank()) {
                    sendJsonResponse(output, stateJson)
                } else {
                    val defaultState = """{"config":{"namaArisan":"Arisan Keluarga besar singghara","periodeAktif":1,"iuranPokokPerSlot":100000.0,"iuranKasPerSlot":20000.0,"jadwalPertemuanBerikutnya":"","waktuPertemuanBerikutnya":"","lokasiPertemuanBerikutnya":"","namaKetuaArisan":"","namaBendahara":"","catatanPertemuan":""},"anggotaList":[],"pembayaranList":[],"kasSosialList":[],"riwayatPemenangList":[],"siklusKe":1}"""
                    prefs.edit().putString("data_state_json", defaultState).apply()
                    sendJsonResponse(output, defaultState)
                }
            }
            "POST" -> {
                try {
                    if (body.isNotBlank()) {
                        prefs.edit().putString("data_state_json", body).apply()
                    }
                    sendJsonResponse(output, """{"status":"success","message":"Arisan updated successfully"}""")
                } catch (e: Exception) {
                    sendResponse(output, 400, "Bad Request", "application/json", """{"status":"error","message":"${e.message}"}""")
                }
            }
            else -> {
                sendResponse(output, 405, "Method Not Allowed", "text/plain", "Method Not Allowed")
            }
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

        val cachedFile = java.io.File(context.filesDir, "cached_network_avatar.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            try {
                val bytes = cachedFile.readBytes()
                sendBinaryResponse(output, 200, "OK", "image/jpeg", bytes)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val uriStr = mainPrefs.getString("profile_image_uri", "") ?: ""
        if (uriStr.isNotEmpty()) {
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                try {
                    val url = java.net.URL(uriStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.instanceFollowRedirects = true
                    if (conn.responseCode == 200) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        try {
                            cachedFile.writeBytes(bytes)
                            localFile.writeBytes(bytes)
                        } catch (ignored: Exception) {}
                        sendBinaryResponse(output, 200, "OK", "image/jpeg", bytes)
                        return
                    }
                } catch (e: Exception) {
                    // Offline
                }
            } else {
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
        }

        sendResponse(output, 404, "Not Found", "text/plain", "Profile image not found")
    }

    private fun handleClockFontRoute(queryParams: Map<String, String>, output: OutputStream) {
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        val requestedFile = queryParams["file"]

        if (!requestedFile.isNullOrEmpty()) {
            val safeName = java.io.File(requestedFile).name
            val fontFolder = com.example.util.SinggharaFolderManager.getFontFolder()
            val candidate = java.io.File(fontFolder, safeName)
            val candidateInternal = java.io.File(context.filesDir, safeName)
            val fileToRead = if (candidate.exists() && candidate.length() > 0) candidate
                             else if (candidateInternal.exists() && candidateInternal.length() > 0) candidateInternal
                             else null
            if (fileToRead != null) {
                val mime = if (safeName.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
                sendBinaryResponse(output, 200, "OK", mime, fileToRead.readBytes())
                return
            }
        }

        val liveFontPath = com.example.ui.MainViewModel.instance?.customClockFontPath?.takeIf { it.isNotEmpty() }
            ?: mainPrefs.getString("custom_clock_font_path", "") ?: ""

        if (liveFontPath.isNotEmpty()) {
            try {
                val target = java.io.File(liveFontPath)
                val fontFolder = com.example.util.SinggharaFolderManager.getFontFolder()
                val folderFile = java.io.File(fontFolder, target.name)
                val internalFile = java.io.File(context.filesDir, target.name)
                val fileToRead = when {
                    target.exists() && target.length() > 0 -> target
                    folderFile.exists() && folderFile.length() > 0 -> folderFile
                    internalFile.exists() && internalFile.length() > 0 -> internalFile
                    else -> null
                }
                if (fileToRead != null) {
                    val mime = if (fileToRead.name.endsWith(".otf", ignoreCase = true)) "font/otf" else "font/ttf"
                    sendBinaryResponse(output, 200, "OK", mime, fileToRead.readBytes())
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Default Technology Font from app raw resource R.font.technology_bold
        try {
            val bytes = context.resources.openRawResource(com.example.R.font.technology_bold).use { it.readBytes() }
            sendBinaryResponse(output, 200, "OK", "font/ttf", bytes)
        } catch (e: Exception) {
            try {
                val bytes = context.resources.openRawResource(com.example.R.font.technology).use { it.readBytes() }
                sendBinaryResponse(output, 200, "OK", "font/ttf", bytes)
            } catch (e2: Exception) {
                sendResponse(output, 404, "Not Found", "text/plain", "Default clock font not found")
            }
        }
    }

    private fun handleClockFontsListRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)

        if (method == "POST") {
            try {
                val json = JSONObject(body)
                val action = json.optString("action", "")
                when (action) {
                    "select" -> {
                        val path = json.optString("path", "")
                        val name = json.optString("name", "")
                        com.example.ui.MainViewModel.instance?.saveCustomClockFont(path, name)
                        mainPrefs.edit()
                            .putString("custom_clock_font_path", path)
                            .putString("custom_clock_font_name", name)
                            .apply()
                        sendJsonResponse(output, JSONObject().put("success", true).put("activeFontName", name).toString())
                        return
                    }
                    "reset" -> {
                        com.example.ui.MainViewModel.instance?.resetCustomClockFont()
                        mainPrefs.edit()
                            .remove("custom_clock_font_path")
                            .remove("custom_clock_font_name")
                            .apply()
                        sendJsonResponse(output, JSONObject().put("success", true).put("activeFontName", "Technology Bold (Bawaan My Singghara)").toString())
                        return
                    }
                    "resize" -> {
                        val size = json.optDouble("size", 52.0).toFloat().coerceIn(24f, 96f)
                        com.example.ui.MainViewModel.instance?.saveClockFontSize(size)
                        mainPrefs.edit().putFloat("clock_font_size_sp", size).apply()
                        sendJsonResponse(output, JSONObject().put("success", true).put("size", size).toString())
                        return
                    }
                    "upload" -> {
                        val fileName = json.optString("fileName", "custom_font.ttf")
                        val safeName = if (fileName.endsWith(".ttf", true) || fileName.endsWith(".otf", true)) fileName else "$fileName.ttf"
                        val base64Data = json.optString("base64", "")
                        if (base64Data.isNotEmpty()) {
                            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            val fontFolder = com.example.util.SinggharaFolderManager.getFontFolder()
                            if (!fontFolder.exists()) fontFolder.mkdirs()
                            val destFile = java.io.File(fontFolder, safeName)
                            destFile.writeBytes(decodedBytes)

                            // Save as active custom font
                            com.example.ui.MainViewModel.instance?.saveCustomClockFont(destFile.absolutePath, destFile.name)
                            mainPrefs.edit()
                                .putString("custom_clock_font_path", destFile.absolutePath)
                                .putString("custom_clock_font_name", destFile.name)
                                .apply()

                            sendJsonResponse(output, JSONObject().put("success", true).put("name", destFile.name).put("path", destFile.absolutePath).toString())
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendJsonResponse(output, JSONObject().put("success", false).put("error", e.message).toString(), 400)
                return
            }
        }

        // GET: List available fonts
        val activeFontPath = com.example.ui.MainViewModel.instance?.customClockFontPath?.takeIf { it.isNotEmpty() }
            ?: mainPrefs.getString("custom_clock_font_path", "") ?: ""
        val activeFontName = com.example.ui.MainViewModel.instance?.customClockFontName?.takeIf { it.isNotEmpty() }
            ?: mainPrefs.getString("custom_clock_font_name", "") ?: "Technology Bold"
        val clockFontSize = com.example.ui.MainViewModel.instance?.clockFontSizeSp
            ?: mainPrefs.getFloat("clock_font_size_sp", 52f)

        val availableFonts = com.example.util.SinggharaFolderManager.getAvailableFontFiles()
        val fontsArr = JSONArray()

        // Default font entry
        fontsArr.put(JSONObject().apply {
            put("name", "Technology Bold (Bawaan My Singghara)")
            put("fileName", "technology_bold.ttf")
            put("path", "")
            put("isDefault", true)
            put("isSelected", activeFontPath.isEmpty())
            put("previewUrl", "/api/clock-font?default=1")
        })

        availableFonts.forEach { file ->
            val isSelected = (activeFontPath == file.absolutePath || activeFontName == file.name)
            fontsArr.put(JSONObject().apply {
                put("name", file.name)
                put("fileName", file.name)
                put("path", file.absolutePath)
                put("isDefault", false)
                put("isSelected", isSelected)
                put("previewUrl", "/api/clock-font?file=" + java.net.URLEncoder.encode(file.name, "UTF-8"))
            })
        }

        val resObj = JSONObject().apply {
            put("activeFontName", if (activeFontPath.isEmpty()) "Technology Bold (Bawaan My Singghara)" else activeFontName)
            put("activeFontPath", activeFontPath)
            put("clockFontSizeSp", clockFontSize)
            put("hasCustomFont", activeFontPath.isNotEmpty())
            put("fonts", fontsArr)
        }
        sendJsonResponse(output, resObj.toString())
    }

    private fun handleAzanAudioRoute(queryParams: Map<String, String>, output: OutputStream) {
        val prayer = queryParams["prayer"]?.lowercase(Locale.getDefault()) ?: ""
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)

        // Check if custom MP3 exists for this prayer
        if (prayer.isNotEmpty()) {
            val customFile = java.io.File(context.filesDir, "azan_custom_$prayer.mp3")
            val externalMp3 = java.io.File(com.example.util.SinggharaFolderManager.getMp3Folder(), "azan_$prayer.mp3")
            val customChosenPath = mainPrefs.getString("azan_custom_path_$prayer", null)

            val fileToPlay = when {
                customChosenPath != null && java.io.File(customChosenPath).exists() && java.io.File(customChosenPath).length() > 0 && java.io.File(customChosenPath).name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> java.io.File(customChosenPath)
                customFile.exists() && customFile.length() > 0 -> customFile
                externalMp3.exists() && externalMp3.length() > 0 && externalMp3.name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> externalMp3
                else -> null
            }
            if (externalMp3.exists() && externalMp3.name.lowercase() in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES) {
                try { externalMp3.delete() } catch (ignored: Exception) {}
            }

            if (fileToPlay != null) {
                try {
                    sendBinaryResponse(output, 200, "OK", "audio/mpeg", fileToPlay.readBytes(), cacheable = false)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Default Azan from My Singghara/Data/mp3 (bundled from Google Drive inside APK package)
        try {
            var defaultAzan = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayer)
            if (defaultAzan == null || !defaultAzan.exists()) {
                com.example.util.SinggharaFolderManager.seedDefaultMp3sFromAssets(context)
                defaultAzan = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayer)
            }
            if (defaultAzan != null && defaultAzan.exists()) {
                sendBinaryResponse(output, 200, "OK", "audio/mpeg", defaultAzan.readBytes(), cacheable = true)
            } else {
                // Fallback direct from assets
                val assetName = if (prayer.contains("subuh", true) || prayer.contains("fajr", true)) {
                    "mp3/fajr_128_44.mp3"
                } else {
                    "mp3/adzan_turkey-1.mp3"
                }
                val bytes = context.assets.open(assetName).use { it.readBytes() }
                sendBinaryResponse(output, 200, "OK", "audio/mpeg", bytes, cacheable = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendResponse(output, 500, "Error", "text/plain", "Failed to load azan audio: ${e.message}")
        }
    }

    private suspend fun handlePrayerSettingsRoute(method: String, body: String, output: OutputStream) {
        if (method != "POST") {
            sendResponse(output, 405, "Method Not Allowed", "text/plain", "POST required")
            return
        }
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)
        try {
            val json = JSONObject(body)
            val prayer = json.optString("prayer", "")
            val setting = json.optInt("setting", 2)
            if (prayer.isNotEmpty()) {
                mainPrefs.edit().putInt("azan_sound_setting_${prayer.lowercase(Locale.getDefault())}", setting).apply()
                withContext(Dispatchers.Main) {
                    com.example.ui.MainViewModel.instance?.savePrayerSoundSetting(prayer, setting)
                    com.example.NotificationScheduler.scheduleAlarms(context)
                }
                sendJsonResponse(output, JSONObject().put("success", true).put("prayer", prayer).put("setting", setting).toString())
                return
            }
            sendJsonResponse(output, JSONObject().put("success", false).put("error", "Missing prayer").toString(), 400)
        } catch (e: Exception) {
            e.printStackTrace()
            sendJsonResponse(output, JSONObject().put("success", false).put("error", e.message).toString(), 500)
        }
    }

    private suspend fun handlePrayerRefreshRoute(output: OutputStream) {
        try {
            withContext(Dispatchers.Main) {
                val vm = com.example.ui.MainViewModel.instance
                vm?.fetchCurrentLocation(context)
                val today = java.util.Calendar.getInstance()
                val formatStr = String.format("%02d-%02d-%04d", today.get(java.util.Calendar.DAY_OF_MONTH), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.YEAR))
                vm?.loadLiveAladhanHijri(formatStr)
            }
            sendJsonResponse(output, JSONObject().put("success", true).toString())
        } catch (e: Exception) {
            e.printStackTrace()
            sendJsonResponse(output, JSONObject().put("success", false).put("error", e.message).toString(), 500)
        }
    }

    // =========================================================================
    // --- PUSTAKA MEDIA HP (audio/video milik perangkat untuk Web Connect) ---
    // Hanya entri MediaStore yang bisa diambil: klien mengirim id numerik, jadi tidak ada
    // cara membaca berkas di luar indeks media melalui endpoint ini.
    // =========================================================================

    private suspend fun handlePhoneMediaRoute(
        path: String,
        queryParams: Map<String, String>,
        rangeHeader: String,
        output: OutputStream
    ) {
        val action = path.removePrefix("/api/phone_media").trim('/')
        val type = queryParams["type"]?.lowercase()?.takeIf {
            it == PhoneMediaScanner.TYPE_AUDIO || it == PhoneMediaScanner.TYPE_VIDEO
        } ?: PhoneMediaScanner.TYPE_AUDIO
        val id = queryParams["id"]?.toLongOrNull() ?: -1L

        when (action) {
            "audio" -> sendJsonResponse(output, buildPhoneMediaListJson(PhoneMediaScanner.TYPE_AUDIO))
            "video" -> sendJsonResponse(output, buildPhoneMediaListJson(PhoneMediaScanner.TYPE_VIDEO))
            "stream" -> streamPhoneMedia(type, id, rangeHeader, output)
            "probe" -> sendPhoneMediaProbe(type, id, output)
            "art" -> sendPhoneMediaArt(type, id, output)
            "lyrics" -> sendPhoneMediaLyrics(type, id, output)
            else -> sendResponse(output, 404, "Not Found", "text/plain", "Endpoint media tidak dikenal")
        }
    }

    private suspend fun buildPhoneMediaListJson(type: String): String {
        val payload = JSONObject()
        val items = JSONArray()
        val hasAccess = PhoneMediaScanner.hasMediaAccess(context, type)
        // Daftar dimuat ulang setelah pengguna memberi izin: kesempatan kedua untuk
        // trek yang sampulnya gagal dibaca saat MediaStore masih mengindeks.
        PhoneMediaScanner.clearArtworkMisses()
        payload.put("type", type)
        payload.put("hasPermission", hasAccess)

        if (hasAccess) {
            if (type == PhoneMediaScanner.TYPE_VIDEO) {
                PhoneMediaScanner.scanVideo(context).forEach { clip ->
                    items.put(JSONObject().apply {
                        put("id", clip.id)
                        put("title", clip.name)
                        put("artist", clip.folder)
                        put("folder", clip.folder)
                        put("durationMs", clip.durationMs)
                        put("durationLabel", PhoneMediaScanner.formatDurationMs(clip.durationMs))
                        put("sizeBytes", clip.sizeBytes)
                        put("sizeLabel", PhoneMediaScanner.formatSize(clip.sizeBytes))
                        put("dateAddedSec", clip.dateAddedSec)
                        put("mime", clip.mimeType)
                        put("url", "/api/phone_media/stream?type=video&id=${clip.id}")
                        put("artUrl", "/api/phone_media/art?type=video&id=${clip.id}")
                    })
                }
            } else {
                PhoneMediaScanner.scanAudio(context).forEach { track ->
                    val hasLyrics = track.lyricsPath != null
                    items.put(JSONObject().apply {
                        put("id", track.id)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        put("folder", track.folder)
                        put("durationMs", track.durationMs)
                        put("durationLabel", PhoneMediaScanner.formatDurationMs(track.durationMs))
                        put("sizeBytes", track.sizeBytes)
                        put("sizeLabel", PhoneMediaScanner.formatSize(track.sizeBytes))
                        put("mime", track.mimeType)
                        put("url", "/api/phone_media/stream?type=audio&id=${track.id}")
                        put("artUrl", "/api/phone_media/art?type=audio&id=${track.id}")
                        put("hasLyrics", hasLyrics)
                        // Lirik bisa datang dari .lrc samping ATAU tag tertanam (ID3/MP4/Vorbis),
                        // yang baru terbaca saat diminta — jadi URL-nya selalu tersedia utk audio.
                        put("lyricsUrl", "/api/phone_media/lyrics?type=audio&id=${track.id}")
                    })
                }
            }
        }

        payload.put("items", items)
        payload.put("total", items.length())
        return payload.toString()
    }

    private suspend fun sendPhoneMediaArt(type: String, id: Long, output: OutputStream) {
        val bytes = withContext(Dispatchers.IO) { PhoneMediaScanner.artwork(context, type, id) }
        if (bytes == null || bytes.isEmpty()) {
            sendResponse(output, 404, "Not Found", "text/plain", "Tidak ada sampul")
            return
        }
        // Sampul tidak berubah selama id MediaStore sama; header cache memangkas
        // unduhan ulang tiap daftar musik di-render ulang di browser.
        val mime = if (bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) "image/png" else "image/jpeg"
        sendBinaryResponse(output, 200, "OK", mime, bytes, cacheable = true)
    }

    private suspend fun sendPhoneMediaLyrics(type: String, id: Long, output: OutputStream) {
        // Web memakai parser lirik yang sama dengan aplikasi: .lrc samping, ID3
        // SYLT/USLT/TXXX, MediaMetadataRetriever, atom MP4 (c)lyr, komentar Vorbis.
        val text = withContext(Dispatchers.IO) {
            val sidecar = PhoneMediaScanner.lyricsFor(context, type, id)
            if (!sidecar.isNullOrBlank()) return@withContext sidecar
            val uriStr = PhoneMediaScanner.uriFor(type, id)?.toString() ?: return@withContext null
            val cached = webLyricsCache.get(uriStr)
            if (cached != null) {
                cached.ifBlank { null }
            } else {
                // String kosong ikut disimpan sebagai penanda "tidak ada lirik" agar
                // pemindaian 4 MB tidak terulang tiap trek yang sama diminta lagi.
                val found = com.example.media.EmbeddedLyricsReader.lyricsFor(context, uriStr) ?: ""
                webLyricsCache.put(uriStr, found)
                found.ifBlank { null }
            }
        }
        if (text.isNullOrBlank()) {
            sendResponse(output, 404, "Not Found", "text/plain", "Tidak ada lirik di berkas ini")
            return
        }
        sendResponse(output, 200, "OK", "text/plain; charset=utf-8", text)
    }

    /**
     * Mengirim berkas media MediaStore dengan dukungan HTTP Range agar pemutar di browser
     * bisa melakukan seek tanpa mengunduh seluruh file lebih dulu.
     */
    private suspend fun streamPhoneMedia(type: String, id: Long, rangeHeader: String, output: OutputStream) {
        val uri = PhoneMediaScanner.uriFor(type, id)
        if (uri == null) {
            sendResponse(output, 404, "Not Found", "text/plain", "Media HP tidak dikenali")
            return
        }

        // Descriptor dibuka SEBELUM header ditulis. Sebelumnya header bisa terkirim lalu
        // openFileDescriptor gagal diam-diam, sehingga browser menerima respons terpotong
        // tanpa alasan yang terlihat. statSize/statOffset juga dipakai sebagai ukuran resmi:
        // kolom SIZE MediaStore bisa basi dan sebagian provider membungkus berkas pada
        // offset > 0, sehingga posisi baca tanpa geseran statOffset mengirim byte salah
        // (trompetnya trek HP: daftar muncul tapi tidak ada suara).
        val descriptor = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            }
        }
        if (descriptor == null) {
            sendResponse(output, 404, "Not Found", "text/plain", "Berkas media HP tidak bisa dibuka (izin dicabut atau berkas hilang)")
            return
        }

        descriptor.use { pfd ->
            // ParcelFileDescriptor hanya exposes getStatSize(); posisi awal wilayah fd
            // dibaca lewat lseek SEEK_CUR: provider yang membungkus sebagian berkas
            // menyerahkan fd yang sudah di-seek ke awal konten, jadi offset itu adalah
            // posisi fd saat ini. StructStat sendiri tidak punya field st_off.
            val statOffset = try {
                android.system.Os.lseek(pfd.fileDescriptor, 0L, android.system.OsConstants.SEEK_CUR).coerceAtLeast(0L)
            } catch (_: Exception) {
                0L
            }
            val total = when {
                pfd.statSize > 0L -> (pfd.statSize - statOffset).coerceAtLeast(0L)
                else -> withContext(Dispatchers.IO) { PhoneMediaScanner.sizeOf(context, type, id) }.coerceAtLeast(0L)
            }
            if (total <= 0L) {
                sendResponse(output, 404, "Not Found", "text/plain", "Berkas media tidak lagi tersedia di HP")
                return
            }

            val mime = withContext(Dispatchers.IO) { PhoneMediaScanner.mimeTypeFor(context, type, id) }
                ?: if (type == PhoneMediaScanner.TYPE_VIDEO) "video/mp4" else "audio/mpeg"

            var start = 0L
            var end = total - 1
            var isPartial = false
            var isUnsatisfiable = false
            if (rangeHeader.isNotBlank()) {
                val value = rangeHeader.lowercase().substringAfter("bytes=", "").trim()
                val dash = value.indexOf('-')
                if (value.isNotEmpty() && dash >= 0 && !value.contains(",")) {
                    val startText = value.substring(0, dash)
                    val endText = value.substring(dash + 1)
                    val suffixLength = endText.toLongOrNull() ?: 0L
                    val parsedStart = if (startText.isEmpty()) {
                        if (suffixLength > 0L) (total - suffixLength).coerceAtLeast(0L) else null
                    } else {
                        startText.toLongOrNull()
                    }
                    if (parsedStart != null) {
                        if (parsedStart >= total) {
                            isUnsatisfiable = true
                        } else {
                            start = parsedStart
                            end = if (endText.isEmpty()) total - 1 else (endText.toLongOrNull() ?: (total - 1)).coerceIn(parsedStart, total - 1)
                            isPartial = true
                        }
                    }
                }
            }

            if (isUnsatisfiable) {
                val rejection = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                        "Content-Range: bytes */$total\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n\r\n"
                try {
                    output.write(rejection.toByteArray(Charsets.UTF_8))
                    output.flush()
                } catch (_: Exception) {
                }
                return
            }

            val contentLength = end - start + 1
            val headers = (if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n") +
                    "Content-Type: $mime\r\n" +
                    "Content-Length: $contentLength\r\n" +
                    (if (isPartial) "Content-Range: bytes $start-$end/$total\r\n" else "") +
                    "Accept-Ranges: bytes\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"

            try {
                withContext(Dispatchers.IO) {
                    output.write(headers.toByteArray(Charsets.UTF_8))
                    output.flush()
                    // FileInputStream tidak di-close() di sini: fd yang sama dimiliki pfd dan
                    // sudah ditutup oleh descriptor.use di akhir respons (hindari double close).
                    val stream = FileInputStream(pfd.fileDescriptor)
                    val channel = stream.channel
                    channel.position(statOffset + start)
                    val buffer = java.nio.ByteBuffer.allocate(65536)
                    var remaining = contentLength
                    while (remaining > 0L) {
                        buffer.clear()
                        if (remaining < buffer.capacity()) buffer.limit(remaining.toInt())
                        val read = channel.read(buffer)
                        if (read <= 0) break
                        output.write(buffer.array(), 0, read)
                        remaining -= read
                    }
                    output.flush()
                }
            } catch (e: Exception) {
                // Browser sering memutus koneksi saat melompati durasi; ini bukan kegagalan server.
                e.printStackTrace()
            }
        }
    }

    /**
     * Diagnostik satu trek HP untuk web player: melaporkan uri, ukuran kolom, mime,
     * offset/ukuran descriptor, dan apakah byte pertama benar-benar terbaca. Web UI
     * memanggil ini saat play() gagal supaya pesan error punya sebab yang jelas.
     */
    private suspend fun sendPhoneMediaProbe(type: String, id: Long, output: OutputStream) {
        val payload = JSONObject()
        val uri = PhoneMediaScanner.uriFor(type, id)
        payload.put("uri", uri?.toString() ?: "")
        if (uri == null) {
            payload.put("error", "uri tidak dikenali")
            sendJsonResponse(output, payload.toString())
            return
        }
        payload.put("sizeColumn", withContext(Dispatchers.IO) { PhoneMediaScanner.sizeOf(context, type, id) })
        payload.put("mime", withContext(Dispatchers.IO) { PhoneMediaScanner.mimeTypeFor(context, type, id) } ?: JSONObject.NULL)
        try {
            withContext(Dispatchers.IO) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    payload.put("fdOpened", false)
                    return@withContext
                }
                pfd.use {
                    payload.put("fdOpened", true)
                    payload.put("statOffset", try { android.system.Os.lseek(it.fileDescriptor, 0L, android.system.OsConstants.SEEK_CUR) } catch (_: Exception) { -1L })
                    payload.put("statSize", it.statSize)
                    try {
                        // Jangan tutup FileInputStream-nya: fd yang sama ditutup oleh pfd.use,
                        // menutup dua kali akan melempar IOException dari within use.
                        val stream = FileInputStream(it.fileDescriptor)
                        payload.put("firstByteReadable", stream.read() >= 0)
                    } catch (e: Exception) {
                        payload.put("readError", e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        } catch (e: Exception) {
            payload.put("error", e.message ?: e.javaClass.simpleName)
        }
        sendJsonResponse(output, payload.toString())
    }

    private suspend fun handleNotificationsRoute(method: String, queryParams: Map<String, String>, body: String, output: OutputStream) {
        val mainPrefs = context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE)

        if (method == "POST") {
            try {
                val action = queryParams["action"] ?: ""
                val id = queryParams["id"]?.toLongOrNull()
                if (action == "read" && id != null) {
                    repository.markFcmAsRead(id)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                } else if (action == "read_all") {
                    repository.markAllFcmAsRead()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    if (json.optString("action") == "read" && json.has("id")) {
                        repository.markFcmAsRead(json.getLong("id"))
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    } else if (json.optString("action") == "read_all") {
                        repository.markAllFcmAsRead()
                        sendJsonResponse(output, JSONObject().put("success", true).toString())
                        return
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            sendJsonResponse(output, JSONObject().put("success", true).toString())
            return
        }

        if (method == "DELETE") {
            try {
                val action = queryParams["action"] ?: ""
                val id = queryParams["id"]?.toLongOrNull()
                if (action == "clear_all") {
                    repository.clearAllFcmNotifications()
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                } else if (id != null) {
                    repository.deleteFcmNotification(id)
                    sendJsonResponse(output, JSONObject().put("success", true).toString())
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            sendJsonResponse(output, JSONObject().put("success", true).toString())
            return
        }

        // GET: Fetch all notifications, ongoing jadwal, active agendas, holidays, hijri & jawa calendar
        val fcmList = repository.allFcmNotifications.first()
        val unreadFcmCount = repository.unreadFcmCount.first()
        val agendaList = repository.allAgendas.first()
        val jadwalList = repository.allJadwal.first()

        val cal = Calendar.getInstance()
        val todayYear = cal.get(Calendar.YEAR)
        val todayMonth = cal.get(Calendar.MONTH) + 1
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val todayDateKey = String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth, todayDay)

        val fallbackHolidays = HolidayClient.getFallbackHolidays(todayYear)
        val todayHolidayName = fallbackHolidays[todayDateKey]
        val todayObservanceName = HolidayClient.getObservanceForDate(todayMonth, todayDay)
        val hasHolidayOrObservanceToday = todayHolidayName != null || todayObservanceName != null

        // Hijri date
        val hijriOffset = mainPrefs.getInt("hijri_offset", -1)
        val hijriDateStr = HijriCalendarHelper.getHijriDate(todayYear, todayMonth, todayDay, hijriOffset)

        // Jawa date
        val jawaInfo = JavaneseCalendarHelper.calculateJawa(todayYear, todayMonth, todayDay)
        val jawaDateStr = "${jawaInfo.dina} ${jawaInfo.pasaran}, Wuku ${jawaInfo.wuku}"

        // Ongoing jadwal
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val ongoingJadwal = jadwalList.find {
            try {
                val cleanRange = it.waktu.replace(" ", "")
                val parts = cleanRange.split("-")
                if (parts.size == 2) {
                    val sParts = parts[0].split(".")
                    val eParts = parts[1].split(".")
                    val sMin = sParts[0].toInt() * 60 + sParts[1].toInt()
                    val eMin = eParts[0].toInt() * 60 + eParts[1].toInt()
                    nowMin in sMin..eMin
                } else false
            } catch (e: Exception) {
                false
            }
        }

        // Active agendas today
        val activeAgendas = agendaList.filter { it.tanggal == todayDateKey }

        val totalNotificationsCount = activeAgendas.size + (if (ongoingJadwal != null) 1 else 0) + (if (hasHolidayOrObservanceToday) 1 else 0) + unreadFcmCount

        // List of all holidays and observances
        val allHolidaysList = mutableListOf<Pair<String, Pair<String, String>>>()
        fallbackHolidays.forEach { (d, n) ->
            allHolidaysList.add(d to ("Libur Nasional" to n))
        }
        HolidayClient.getAllObservances().forEach { (md, n) ->
            val fullDate = "$todayYear-$md"
            if (fallbackHolidays[fullDate] == null) {
                allHolidaysList.add(fullDate to ("Hari Besar" to n))
            }
        }
        allHolidaysList.sortBy { it.first }

        val holidaysArr = JSONArray()
        allHolidaysList.forEach { item ->
            holidaysArr.put(JSONObject().apply {
                put("date", item.first)
                put("type", item.second.first)
                put("name", item.second.second)
            })
        }

        val fcmArr = JSONArray()
        fcmList.forEach { fcm ->
            fcmArr.put(JSONObject().apply {
                put("id", fcm.id)
                put("title", fcm.title)
                put("body", fcm.body)
                put("timestamp", fcm.timestamp)
                put("isRead", fcm.isRead)
                put("dataPayload", fcm.dataPayload)
            })
        }

        val ongoingJadwalObj = if (ongoingJadwal != null) {
            JSONObject().apply {
                put("id", ongoingJadwal.id)
                put("waktu", ongoingJadwal.waktu)
                put("kegiatan", ongoingJadwal.kegiatan)
                put("kategori", ongoingJadwal.kategori)
            }
        } else JSONObject.NULL

        val activeAgendasArr = JSONArray()
        activeAgendas.forEach { a ->
            activeAgendasArr.put(JSONObject().apply {
                put("id", a.id)
                put("judul", a.judul)
                put("detail", a.detail)
                put("tanggal", a.tanggal)
                put("tipe", a.tipe)
            })
        }

        val res = JSONObject().apply {
            put("unreadFcmCount", unreadFcmCount)
            put("totalNotificationsCount", totalNotificationsCount)
            put("todayYear", todayYear)
            put("todayDateKey", todayDateKey)
            put("todayHolidayName", todayHolidayName ?: JSONObject.NULL)
            put("todayObservanceName", todayObservanceName ?: JSONObject.NULL)
            put("hasHolidayOrObservanceToday", hasHolidayOrObservanceToday)
            put("hijriDate", hijriDateStr)
            put("jawaDate", jawaDateStr)
            put("ongoingJadwal", ongoingJadwalObj)
            put("activeAgendas", activeAgendasArr)
            put("allHolidays", holidaysArr)
            put("fcmNotifications", fcmArr)
        }
        sendJsonResponse(output, res.toString())
    }

    private fun sendBinaryResponse(output: OutputStream, statusCode: Int, statusText: String, contentType: String, bytes: ByteArray, cacheable: Boolean = false) {
        val cacheHeader = if (cacheable) "Cache-Control: public, max-age=86400\r\n" else "Cache-Control: no-cache, no-store, must-revalidate\r\n"
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                cacheHeader +
                "Connection: close\r\n" +
                "\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun serveFavicon(path: String, output: OutputStream) {
        val cleanPath = path.substringBefore('?').substringAfterLast('/')
        val assetName = when (cleanPath) {
            "favicon.ico" -> "web/favicon.ico"
            "favicon.svg" -> "web/favicon.svg"
            "favicon.png" -> "web/favicon.png"
            "favicon-32x32.png" -> "web/favicon-32x32.png"
            "favicon-16x16.png" -> "web/favicon-16x16.png"
            "favicon-light.ico" -> "web/favicon-light.ico"
            "favicon-light.png" -> "web/favicon-light.png"
            "favicon-light-32x32.png" -> "web/favicon-light-32x32.png"
            "favicon-light-16x16.png" -> "web/favicon-light-16x16.png"
            "favicon-dark.ico" -> "web/favicon-dark.ico"
            "favicon-dark.png" -> "web/favicon-dark.png"
            "favicon-dark-32x32.png" -> "web/favicon-dark-32x32.png"
            "favicon-dark-16x16.png" -> "web/favicon-dark-16x16.png"
            "apple-touch-icon.png", "apple-touch-icon-precomposed.png" -> "web/apple-touch-icon.png"
            "app-logo.png", "logo.png" -> "web/app-logo.png"
            "app-logo-light.png" -> "web/app-logo-light.png"
            "app-logo-dark.png" -> "web/app-logo-dark.png"
            else -> "web/favicon.ico"
        }
        val mimeType = when {
            assetName.endsWith(".ico") -> "image/x-icon"
            assetName.endsWith(".svg") -> "image/svg+xml"
            else -> "image/png"
        }
        try {
            val bytes = context.assets.open(assetName).use { it.readBytes() }
            sendBinaryResponse(output, 200, "OK", mimeType, bytes, cacheable = true)
        } catch (e: Exception) {
            try {
                val bytes = context.resources.openRawResource(com.example.R.drawable.ic_app_logo).use { it.readBytes() }
                sendBinaryResponse(output, 200, "OK", "image/png", bytes, cacheable = true)
            } catch (e2: Exception) {
                sendResponse(output, 404, "Not Found", "text/plain", "Favicon Not Found")
            }
        }
    }

    /**
     * Installer aplikasi PC native (satu jendela WebView2, tanpa browser/terminal).
     * Alamat HP ditempel di ekor berkas .exe supaya PC cukup double-click tanpa mengetik IP.
     */
    private fun servePcNativeInstaller(output: OutputStream) {
        val installerBytes = try {
            context.assets.open(com.example.util.SinggharaFolderManager.PC_INSTALLER_ASSET).use { it.readBytes() }
        } catch (e: Exception) {
            ByteArray(0)
        }
        if (installerBytes.isEmpty()) {
            sendResponse(output, 404, "Not Found", "text/plain", "Installer PC tidak ditemukan di paket aplikasi")
            return
        }
        val marker = "#MY_SINGGHARA_CFG#ip=${getIpAddress(context)};port=$port\r\n"
        val payload = installerBytes + marker.toByteArray(Charsets.US_ASCII)
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/vnd.microsoft.portable-executable\r\n" +
                "Content-Disposition: attachment; filename=\"${com.example.util.SinggharaFolderManager.PC_INSTALLER_FILE_NAME}\"\r\n" +
                "Content-Length: ${payload.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(payload)
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
                // Dashboard dibuat dinamis oleh aplikasi; cache browser PC akan menyajikan versi lama
                // sehingga fitur baru tidak pernah terlihat.
                "Cache-Control: no-store, must-revalidate\r\n" +
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
    <title>My Singghara</title>
    <!-- Favicon Adaptif Bulat My Singghara (Mode Terang & Gelap - 100% Offline PC) -->
    <link rel="icon" type="image/svg+xml" href="/favicon.svg?v=3">
    <link id="favLight32" rel="icon" type="image/png" sizes="32x32" href="/favicon-light-32x32.png?v=3" media="(prefers-color-scheme: light)">
    <link id="favDark32" rel="icon" type="image/png" sizes="32x32" href="/favicon-dark-32x32.png?v=3" media="(prefers-color-scheme: dark)">
    <link id="favLight16" rel="icon" type="image/png" sizes="16x16" href="/favicon-light-16x16.png?v=3" media="(prefers-color-scheme: light)">
    <link id="favDark16" rel="icon" type="image/png" sizes="16x16" href="/favicon-dark-16x16.png?v=3" media="(prefers-color-scheme: dark)">
    <link id="favDefault" rel="icon" type="image/png" href="/favicon.png?v=3">
    <link id="favShortcut" rel="shortcut icon" href="/favicon.ico?v=3">
    <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png?v=3">
    <meta name="msapplication-TileImage" content="/app-logo-dark.png?v=3">
    <meta name="theme-color" content="#3674B5" id="metaThemeColor">
    <!-- Progressive Web App (PWA) Manifest & Capabilities -->
    <link rel="manifest" href="/manifest.json?v=3">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="My Singghara">
    <meta name="application-name" content="My Singghara">
    <link href="/assets/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="/assets/css/bootstrap-icons.css">
    <script src="/assets/js/bootstrap.bundle.min.js"></script>
    <script src="/assets/js/qrcode.min.js"></script>
    <script src="/assets/js/chart.min.js"></script>
    <script src="/assets/js/html2canvas.min.js"></script>
    <script src="/assets/js/html5-qrcode.min.js"></script>
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

            /* Surface & semantic tokens shared by the music/video players */
            --surface-subtle: rgba(127, 127, 127, 0.08);
            --surface-hover: rgba(127, 127, 127, 0.16);
            --success-color: #198754;
            --success-subtle: rgba(25, 135, 84, 0.15);
            --warning-color: #b45309;
            --warning-subtle: rgba(255, 193, 7, 0.18);
            --danger-subtle: rgba(231, 76, 60, 0.12);
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

            --surface-subtle: rgba(148, 163, 184, 0.12);
            --surface-hover: rgba(148, 163, 184, 0.22);
            --success-color: #4ade80;
            --success-subtle: rgba(74, 222, 128, 0.18);
            --warning-color: #fbbf24;
            --warning-subtle: rgba(251, 191, 36, 0.18);
            --danger-color: #f87171;
            --danger-subtle: rgba(248, 113, 113, 0.18);
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

                --surface-subtle: rgba(148, 163, 184, 0.12);
                --surface-hover: rgba(148, 163, 184, 0.22);
                --success-color: #4ade80;
                --success-subtle: rgba(74, 222, 128, 0.18);
                --warning-color: #fbbf24;
                --warning-subtle: rgba(251, 191, 36, 0.18);
                --danger-color: #f87171;
                --danger-subtle: rgba(248, 113, 113, 0.18);
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

        @keyframes bell-ring {
            0% { transform: rotate(0); }
            10% { transform: rotate(14deg); }
            20% { transform: rotate(-12deg); }
            30% { transform: rotate(10deg); }
            40% { transform: rotate(-8deg); }
            50% { transform: rotate(6deg); }
            60% { transform: rotate(-4deg); }
            70% { transform: rotate(2deg); }
            80% { transform: rotate(-1deg); }
            100% { transform: rotate(0); }
        }
        .bell-active {
            animation: bell-ring 2.5s infinite ease-in-out;
            transform-origin: top center;
            display: inline-block;
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

        /* Dark Mode Note Submenu Enhancements (Hindari Warna Putih di Mode Gelap) */
        body.dark .btn-light, [data-theme="dark"] .btn-light,
        :root:not([data-theme="light"]):not(.dark) .btn-light,
        body.dark #notesMainWrapper .btn-light, [data-theme="dark"] #notesMainWrapper .btn-light,
        body.dark #noteEditorWrapper .btn-light, [data-theme="dark"] #noteEditorWrapper .btn-light {
            background-color: #1e293b !important;
            color: #cbd5e1 !important;
            border-color: #334155 !important;
        }
        body.dark .btn-light:hover, [data-theme="dark"] .btn-light:hover,
        :root:not([data-theme="light"]):not(.dark) .btn-light:hover,
        body.dark #notesMainWrapper .btn-light:hover, [data-theme="dark"] #notesMainWrapper .btn-light:hover,
        body.dark #noteEditorWrapper .btn-light:hover, [data-theme="dark"] #noteEditorWrapper .btn-light:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
            border-color: #475569 !important;
        }

        body.dark #notesMainWrapper, [data-theme="dark"] #notesMainWrapper,
        body.dark #noteEditorWrapper, [data-theme="dark"] #noteEditorWrapper {
            background-color: transparent !important;
            color: #f1f5f9 !important;
        }

        body.dark #notesMainWrapper .card, [data-theme="dark"] #notesMainWrapper .card,
        body.dark #noteEditorWrapper .card, [data-theme="dark"] #noteEditorWrapper .card,
        body.dark .note-card-item, [data-theme="dark"] .note-card-item {
            background-color: #1e293b !important;
            border-color: #334155 !important;
            color: #f1f5f9 !important;
        }

        body.dark #notesMainWrapper .input-group-text, [data-theme="dark"] #notesMainWrapper .input-group-text,
        body.dark #notesMainWrapper .form-control, [data-theme="dark"] #notesMainWrapper .form-control {
            background-color: #1e293b !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }

        body.dark #editorNoteTitle, [data-theme="dark"] #editorNoteTitle,
        body.dark #editorNoteContent, [data-theme="dark"] #editorNoteContent {
            background-color: #0f172a !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }

        body.dark #editorNoteTitle::placeholder, [data-theme="dark"] #editorNoteTitle::placeholder,
        body.dark #editorNoteContent::placeholder, [data-theme="dark"] #editorNoteContent::placeholder {
            color: #64748b !important;
        }

        body.dark #noteFloatingToast .card, [data-theme="dark"] #noteFloatingToast .card {
            background-color: #1e293b !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }

        body.dark #noteEditorWrapper .bg-body, [data-theme="dark"] #noteEditorWrapper .bg-body,
        body.dark #notesMainWrapper .bg-body, [data-theme="dark"] #notesMainWrapper .bg-body {
            background-color: #0f172a !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }

        body.dark #notesMainWrapper .btn-outline-secondary, [data-theme="dark"] #notesMainWrapper .btn-outline-secondary {
            background-color: #1e293b !important;
            color: #cbd5e1 !important;
            border-color: #334155 !important;
        }
        body.dark #notesMainWrapper .btn-outline-secondary:hover, [data-theme="dark"] #notesMainWrapper .btn-outline-secondary:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
        }

        /* ==========================================================================
           Dark Mode Styling for Dashboard Prayer Times Card (Kartu Waktu Sholat)
           100% Sesuai Tema Gelap, Tidak Ada Bagian Putih, Font Terang Putih & Jelas
           ========================================================================== */
        body.dark #dashboardPrayerCard, [data-theme="dark"] #dashboardPrayerCard,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard {
            background-color: #1e293b !important;
            border-color: #334155 !important;
            color: #ffffff !important;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4) !important;
        }

        body.dark #dashboardPrayerCard .prayer-card-title,
        [data-theme="dark"] #dashboardPrayerCard .prayer-card-title,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-card-title {
            color: #ffffff !important;
        }

        body.dark #dashboardPrayerCard .prayer-tz-badge,
        [data-theme="dark"] #dashboardPrayerCard .prayer-tz-badge,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-tz-badge {
            background-color: rgba(56, 189, 248, 0.2) !important;
            color: #ffffff !important;
            border: 1px solid rgba(56, 189, 248, 0.4) !important;
        }

        body.dark #dashboardPrayerCard .prayer-source-label,
        [data-theme="dark"] #dashboardPrayerCard .prayer-source-label,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-source-label {
            color: #ffffff !important;
            opacity: 0.9 !important;
        }

        body.dark #dashboardPrayerCard .prayer-gear-btn,
        [data-theme="dark"] #dashboardPrayerCard .prayer-gear-btn,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-gear-btn {
            background-color: #0f172a !important;
            border-color: #38bdf8 !important;
            color: #38bdf8 !important;
        }
        body.dark #dashboardPrayerCard .prayer-gear-btn:hover,
        [data-theme="dark"] #dashboardPrayerCard .prayer-gear-btn:hover,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-gear-btn:hover {
            background-color: #38bdf8 !important;
            color: #0f172a !important;
        }

        body.dark #dashboardPrayerCard .prayer-location-pill,
        [data-theme="dark"] #dashboardPrayerCard .prayer-location-pill,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-location-pill {
            background-color: #0f172a !important;
            border-color: #334155 !important;
        }

        body.dark #dashboardPrayerCard .prayer-loc-tag,
        [data-theme="dark"] #dashboardPrayerCard .prayer-loc-tag,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-loc-tag {
            color: #ffffff !important;
            opacity: 0.85 !important;
        }

        body.dark #dashboardPrayerCard .prayer-loc-text,
        [data-theme="dark"] #dashboardPrayerCard .prayer-loc-text,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-loc-text {
            color: #ffffff !important;
        }

        body.dark #dashboardPrayerCard .prayer-refresh-btn,
        [data-theme="dark"] #dashboardPrayerCard .prayer-refresh-btn,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-refresh-btn {
            color: #38bdf8 !important;
        }

        body.dark #dashboardPrayerCard .prayer-test-btn,
        [data-theme="dark"] #dashboardPrayerCard .prayer-test-btn,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-test-btn {
            background-color: #0f172a !important;
            border-color: #38bdf8 !important;
            color: #ffffff !important;
        }
        body.dark #dashboardPrayerCard .prayer-test-btn:hover,
        [data-theme="dark"] #dashboardPrayerCard .prayer-test-btn:hover,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-test-btn:hover {
            background-color: #0284c7 !important;
            color: #ffffff !important;
        }

        /* 6 Kartu Jadwal Sholat Pada Mode Gelap */
        body.dark #dashboardPrayerCard .prayer-item-normal,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-normal,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-normal {
            background-color: #0f172a !important;
            border-color: #334155 !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-normal .prayer-item-icon-box,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-normal .prayer-item-icon-box,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-normal .prayer-item-icon-box {
            background-color: rgba(255, 255, 255, 0.1) !important;
            color: #ffffff !important;
            border: 1px solid rgba(255, 255, 255, 0.15) !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-normal .prayer-name-label,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-normal .prayer-name-label,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-normal .prayer-name-label {
            color: #ffffff !important;
            font-weight: 700 !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-normal .prayer-time-value,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-normal .prayer-time-value,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-normal .prayer-time-value {
            color: #ffffff !important;
            font-weight: 800 !important;
        }

        /* Kartu Waktu Sholat Berikutnya (Aktif/Highlight) Pada Mode Gelap */
        body.dark #dashboardPrayerCard .prayer-item-next,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-next,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-next {
            background: linear-gradient(135deg, rgba(14, 165, 233, 0.28) 0%, rgba(15, 23, 42, 0.95) 100%) !important;
            border: 2px solid #38bdf8 !important;
            box-shadow: 0 0 16px rgba(56, 189, 248, 0.25) !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-next .prayer-item-icon-box,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-next .prayer-item-icon-box,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-next .prayer-item-icon-box {
            background-color: #0284c7 !important;
            color: #ffffff !important;
            border: none !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-next .prayer-name-label,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-next .prayer-name-label,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-next .prayer-name-label {
            color: #ffffff !important;
            font-weight: 800 !important;
        }

        body.dark #dashboardPrayerCard .prayer-item-next .prayer-time-value,
        [data-theme="dark"] #dashboardPrayerCard .prayer-item-next .prayer-time-value,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-item-next .prayer-time-value {
            color: #ffffff !important;
            font-weight: 900 !important;
        }

        body.dark #dashboardPrayerCard .prayer-next-badge,
        [data-theme="dark"] #dashboardPrayerCard .prayer-next-badge,
        :root:not([data-theme="light"]):not(.dark) #dashboardPrayerCard .prayer-next-badge {
            background-color: #0284c7 !important;
            color: #ffffff !important;
            border: 1px solid rgba(255, 255, 255, 0.3) !important;
        }

        /* Banner Notifikasi Azan Aktif di Mode Gelap (Tidak Ada Elemen Putih) */
        body.dark #activeAzanAlertBanner, [data-theme="dark"] #activeAzanAlertBanner,
        :root:not([data-theme="light"]):not(.dark) #activeAzanAlertBanner {
            background: linear-gradient(135deg, #1e3a8a 0%, #0f172a 100%) !important;
            border: 1px solid rgba(56, 189, 248, 0.4) !important;
            color: #ffffff !important;
        }
        body.dark #activeAzanAlertBanner .btn-light, [data-theme="dark"] #activeAzanAlertBanner .btn-light,
        :root:not([data-theme="light"]):not(.dark) #activeAzanAlertBanner .btn-light {
            background-color: rgba(239, 68, 68, 0.22) !important;
            border: 1px solid #ef4444 !important;
            color: #ffffff !important;
        }
        body.dark #activeAzanAlertBanner .btn-light:hover, [data-theme="dark"] #activeAzanAlertBanner .btn-light:hover,
        :root:not([data-theme="light"]):not(.dark) #activeAzanAlertBanner .btn-light:hover {
            background-color: #ef4444 !important;
            color: #ffffff !important;
        }

        /* Modal Pengaturan Suara Sholat & Sound Picker di Mode Gelap */
        body.dark #prayerSettingsModal .modal-content, [data-theme="dark"] #prayerSettingsModal .modal-content,
        body.dark #singlePrayerSoundModal .modal-content, [data-theme="dark"] #singlePrayerSoundModal .modal-content {
            background-color: #1e293b !important;
            color: #ffffff !important;
            border: 1px solid #334155 !important;
        }
        body.dark #prayerSettingsModal .modal-title, [data-theme="dark"] #prayerSettingsModal .modal-title,
        body.dark #singlePrayerSoundModal .modal-title, [data-theme="dark"] #singlePrayerSoundModal .modal-title {
            color: #ffffff !important;
        }
        body.dark #prayerSettingsModal .text-secondary, [data-theme="dark"] #prayerSettingsModal .text-secondary,
        body.dark #singlePrayerSoundModal .text-secondary, [data-theme="dark"] #singlePrayerSoundModal .text-secondary {
            color: #cbd5e1 !important;
        }
        body.dark #prayerSettingsModal .alert-info, [data-theme="dark"] #prayerSettingsModal .alert-info {
            background-color: rgba(14, 165, 233, 0.15) !important;
            color: #ffffff !important;
            border: 1px solid rgba(56, 189, 248, 0.3) !important;
        }
        body.dark #prayerSettingsList .card, [data-theme="dark"] #prayerSettingsList .card {
            background-color: #0f172a !important;
            border-color: #334155 !important;
            color: #ffffff !important;
        }
        body.dark #prayerSettingsList .text-main, [data-theme="dark"] #prayerSettingsList .text-main {
            color: #ffffff !important;
        }
        body.dark #singlePrayerOptionsList .card, [data-theme="dark"] #singlePrayerOptionsList .card {
            background-color: #0f172a !important;
            border-color: #334155 !important;
            color: #ffffff !important;
        }
        body.dark #singlePrayerOptionsList .card.border-primary, [data-theme="dark"] #singlePrayerOptionsList .card.border-primary {
            background-color: rgba(14, 165, 233, 0.2) !important;
            border-color: #38bdf8 !important;
        }
        body.dark #singlePrayerOptionsList .text-main, [data-theme="dark"] #singlePrayerOptionsList .text-main,
        body.dark #singlePrayerOptionsList .text-primary, [data-theme="dark"] #singlePrayerOptionsList .text-primary {
            color: #ffffff !important;
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
            padding: 10px 12px;
            border-top: 1px solid var(--border-color);
            background-color: var(--sidebar-bg);
            transition: all 0.25s ease;
            overflow: hidden;
            width: 100%;
            box-sizing: border-box;
        }

        .sidebar-user-section {
            width: 100%;
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 6px;
            min-width: 0;
            overflow: hidden;
            box-sizing: border-box;
        }

        .sidebar-profile-info {
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
            border-radius: 10px;
            padding: 6px 6px;
            transition: background-color 0.2s ease;
            flex: 1 1 0;
            min-width: 0;
            max-width: calc(100% - 42px);
            user-select: none;
            overflow: hidden;
            box-sizing: border-box;
        }

        .sidebar-profile-info:hover {
            background-color: var(--sidebar-hover);
        }

        .sidebar-avatar-icon {
            width: 36px;
            height: 36px;
            min-width: 36px;
            border-radius: 50%;
            background: linear-gradient(135deg, #2A84EB, #1456A5);
            color: #ffffff;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.1rem;
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
            flex: 1 1 0;
            overflow: hidden;
            display: flex;
            flex-direction: column;
            justify-content: center;
        }

        .sidebar-user-name {
            font-size: 0.82rem;
            color: var(--text-main);
            line-height: 1.25;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            display: block;
            width: 100%;
            max-width: 100%;
            font-weight: 700;
        }

        .sidebar-user-status {
            font-size: 0.70rem;
            color: var(--text-sub);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            display: block;
            width: 100%;
            max-width: 100%;
        }

        .btn-sidebar-logout {
            width: 36px;
            height: 36px;
            min-width: 36px;
            max-width: 36px;
            border-radius: 10px;
            border: 1px solid var(--border-color);
            background-color: transparent;
            color: var(--text-sub);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-size: 1.15rem;
            cursor: pointer;
            transition: all 0.2s ease;
            flex-shrink: 0;
            flex-grow: 0;
            padding: 0;
            box-sizing: border-box;
            z-index: 2;
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

        /* Responsive Mobile Behavior & Scroll Lock */
        html.sidebar-mobile-locked,
        body.sidebar-mobile-open {
            overflow: hidden !important;
            height: 100% !important;
            max-height: 100vh !important;
            overscroll-behavior: none !important;
        }

        .sidebar-mobile-header {
            display: none;
        }

        @media (max-width: 768px) {
            .navbar-main {
                height: 56px;
                padding: 0;
            }

            .content-wrapper {
                margin-left: 0 !important;
                margin-top: 56px !important;
                padding: 12px 10px !important;
                width: 100% !important;
                max-width: 100vw !important;
                overflow-x: hidden;
            }

            /* Cegah scroll latar belakang saat sidebar mobile terbuka */
            body.sidebar-mobile-open .content-wrapper {
                overflow: hidden !important;
                pointer-events: none !important;
                user-select: none !important;
                touch-action: none !important;
            }

            body.sidebar-mobile-open .navbar-main {
                touch-action: none !important;
            }

            .sidebar-wrapper {
                position: fixed !important;
                top: 0 !important;
                bottom: 0 !important;
                left: 0 !important;
                height: 100vh !important;
                height: 100dvh !important;
                width: 290px !important;
                max-width: 86vw !important;
                transform: translateX(-100%);
                box-shadow: 4px 0 24px rgba(0, 0, 0, 0.35);
                z-index: 1050 !important;
                display: flex !important;
                flex-direction: column !important;
                transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
            }

            .sidebar-mobile-header {
                display: flex !important;
            }

            body.sidebar-mobile-open .sidebar-wrapper {
                transform: translateX(0);
                touch-action: pan-y !important;
            }

            /* Izinkan scroll yang mulus hanya pada menu sidebar */
            .sidebar-menu-scrollable {
                flex: 1 1 auto;
                overflow-y: auto !important;
                -webkit-overflow-scrolling: touch !important;
                overscroll-behavior-y: contain !important;
                touch-action: pan-y !important;
            }

            body.sidebar-mobile-open::after {
                content: '';
                position: fixed;
                inset: 0;
                background: rgba(0,0,0,0.55);
                backdrop-filter: blur(2px);
                -webkit-backdrop-filter: blur(2px);
                z-index: 1040;
                touch-action: none !important;
            }

            /* Mobile UI optimization */
            .card-custom {
                border-radius: 12px;
                padding: 14px 12px !important;
            }

            .dashboard-clock-box {
                padding: 12px !important;
                border-radius: 12px !important;
            }

            .nav-link-item {
                min-height: 42px;
            }

            .sidebar-group-header {
                min-height: 42px;
            }

            input.form-control, select.form-select, textarea.form-control {
                font-size: 16px !important;
            }

            .modal-dialog {
                margin: 10px !important;
                max-width: calc(100vw - 20px) !important;
            }

            .table-responsive {
                -webkit-overflow-scrolling: touch;
                scrollbar-width: thin;
                margin-bottom: 0.75rem;
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

        .text-primary {
            color: var(--brand-green) !important;
        }

        .text-danger {
            color: var(--danger-color) !important;
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

        /* Quran Arabic Text & Tajwid Colors */
        .quran-arabic-text {
            font-family: 'Amiri', 'Traditional Arabic', 'Scheherazade New', serif;
            font-size: 1.75rem;
            line-height: 2.2;
            direction: rtl;
            text-align: right;
            color: var(--text-main);
        }
        .tajwid-ghunnah { color: #E91E63 !important; font-weight: bold; }
        .tajwid-qalqalah { color: #1E88E5 !important; font-weight: bold; }
        .tajwid-ikhfa { color: #EF6C00 !important; font-weight: bold; }
        .tajwid-mad { color: #2E7D32 !important; }

        .quran-tab-btn {
            color: var(--text-muted);
            border-bottom: 2px solid transparent;
            border-radius: 0;
            transition: all 0.2s ease;
        }
        .quran-tab-btn:hover {
            color: #00796B;
        }
        .quran-tab-btn.active {
            color: #00796B !important;
            border-bottom: 3px solid #00796B !important;
        }
        body.dark .quran-tab-btn.active,
        [data-theme="dark"] .quran-tab-btn.active {
            color: #4DB6AC !important;
            border-bottom: 3px solid #4DB6AC !important;
        }

        .quran-id-badge {
            width: 42px;
            height: 42px;
            min-width: 42px;
            border-radius: 10px;
            background: rgba(0, 121, 107, 0.1);
            border: 1px solid rgba(0, 121, 107, 0.25);
            font-size: 13px;
        }
        body.dark .quran-id-badge,
        [data-theme="dark"] .quran-id-badge {
            background: rgba(0, 121, 107, 0.2);
            border: 1px solid rgba(0, 121, 107, 0.4);
            color: #4DB6AC !important;
        }

        .quran-juz-badge {
            width: 42px;
            height: 42px;
            min-width: 42px;
            border-radius: 10px;
            background: rgba(2, 136, 209, 0.1);
            border: 1px solid rgba(2, 136, 209, 0.25);
            color: #0288D1;
        }
        body.dark .quran-juz-badge,
        [data-theme="dark"] .quran-juz-badge {
            background: rgba(2, 136, 209, 0.2);
            border: 1px solid rgba(2, 136, 209, 0.4);
            color: #4FC3F7 !important;
        }

        .quran-surah-card, .quran-juz-card, .quran-verse-card, .quran-history-item, .quran-bookmark-item {
            transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
            border: 1px solid var(--border-color) !important;
        }
        .quran-surah-card:hover, .quran-juz-card:hover, .quran-history-item:hover, .quran-bookmark-item:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 16px rgba(0,0,0,0.08);
            border-color: rgba(0, 121, 107, 0.4) !important;
        }

        body.dark .quran-arabic-text,
        [data-theme="dark"] .quran-arabic-text {
            color: #F8FAFC !important;
        }

        body.dark .quran-verse-card,
        [data-theme="dark"] .quran-verse-card {
            background-color: var(--bg-card) !important;
            color: #F8FAFC !important;
        }

        body.dark .quran-translation,
        [data-theme="dark"] .quran-translation {
            color: #CBD5E1 !important;
        }

        .active-playing-verse {
            border: 2px solid #00796B !important;
            background: rgba(0, 121, 107, 0.05) !important;
            box-shadow: 0 0 16px rgba(0, 121, 107, 0.25) !important;
        }
        body.dark .active-playing-verse,
        [data-theme="dark"] .active-playing-verse {
            border: 2px solid #4DB6AC !important;
            background: rgba(0, 121, 107, 0.15) !important;
            box-shadow: 0 0 20px rgba(77, 182, 172, 0.3) !important;
        }

        .verse-highlight-flash {
            animation: verseFlash 2s ease forwards;
        }
        @keyframes verseFlash {
            0% { background: rgba(0, 121, 107, 0.25); }
            100% { background: transparent; }
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

        /* Responsive Breakpoints for Mobile Screen Displays (Brick Game) */
        @media (max-width: 480px) {
            .brick-game-casing {
                padding: 12px 10px !important;
                border-radius: 26px;
                max-width: 100%;
            }
            .brick-game-lcd-frame {
                padding: 8px !important;
                border-radius: 12px 12px 26px 12px;
                margin-bottom: 0.75rem !important;
            }
            .brick-game-lcd {
                padding: 6px !important;
                gap: 6px !important;
            }
            #brickGameCanvas {
                width: 200px !important;
                height: 400px !important;
            }
            .brick-game-lcd > div:nth-child(2) {
                min-height: 400px !important;
                font-size: 10px !important;
            }
            .brick-dpad-well {
                width: 128px;
                height: 128px;
            }
            .brick-dpad-cross {
                width: 110px;
                height: 110px;
            }
            .brick-dpad-up, .brick-dpad-down {
                left: 37px; width: 36px; height: 38px;
            }
            .brick-dpad-left, .brick-dpad-right {
                top: 37px; width: 38px; height: 36px;
            }
            .brick-dpad-center {
                top: 37px; left: 37px; width: 36px; height: 36px;
            }
            .brick-action-pill-well {
                width: 136px;
                height: 86px;
                padding: 0 6px;
            }
            .brick-action-btn-circle {
                width: 48px;
                height: 48px;
            }
            .brick-action-btn-circle .brick-btn-label {
                font-size: 13.5px;
            }
            .brick-action-btn-circle .brick-btn-sub {
                font-size: 7.5px;
            }
            .brick-pill-btn {
                width: 74px;
                height: 25px;
                font-size: 9px;
            }
        }

        @media (max-width: 380px) {
            .brick-game-casing {
                padding: 10px 6px !important;
                border-radius: 22px;
            }
            .brick-game-lcd-frame {
                padding: 6px !important;
                border-radius: 10px 10px 20px 10px;
            }
            .brick-game-lcd {
                padding: 4px !important;
                gap: 4px !important;
            }
            #brickGameCanvas {
                width: 170px !important;
                height: 340px !important;
            }
            .brick-game-lcd > div:nth-child(2) {
                min-height: 340px !important;
                font-size: 9.5px !important;
            }
            .brick-dpad-well {
                width: 114px;
                height: 114px;
            }
            .brick-dpad-cross {
                width: 98px;
                height: 98px;
            }
            .brick-dpad-up, .brick-dpad-down {
                left: 33px; width: 32px; height: 34px;
            }
            .brick-dpad-left, .brick-dpad-right {
                top: 33px; width: 34px; height: 32px;
            }
            .brick-dpad-center {
                top: 33px; left: 33px; width: 32px; height: 32px;
            }
            .brick-action-pill-well {
                width: 120px;
                height: 76px;
                padding: 0 4px;
            }
            .brick-action-btn-circle {
                width: 42px;
                height: 42px;
            }
            .brick-action-btn-circle .brick-btn-label {
                font-size: 12px;
            }
            .brick-action-btn-circle .brick-btn-sub {
                font-size: 6.5px;
            }
            .brick-pill-btn {
                width: 64px;
                height: 23px;
                font-size: 8px;
                padding: 0 4px;
            }
        }

        /* Block Puzzle Styles - 1:1 Match to My Singghara App */
        .puzzle-game-wrapper {
            max-width: 1080px;
            margin: 0 auto;
            user-select: none;
            -webkit-user-select: none;
        }
        .puzzle-stats-col {
            max-width: 215px;
            width: 100%;
        }
        .puzzle-board-col {
            max-width: 540px;
            width: 100%;
        }
        .puzzle-tray-col {
            max-width: 200px;
            width: 100%;
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
        .puzzle-stats-grid {
            display: flex;
            flex-direction: column;
            gap: 8px;
        }
        .puzzle-booster-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 6px;
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
            box-sizing: border-box;
        }
        .puzzle-grid {
            display: grid;
            grid-template-columns: repeat(8, 1fr);
            gap: 4px;
            width: 100%;
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
            box-sizing: border-box;
        }
        .puzzle-pieces-tray {
            display: flex;
            flex-direction: column;
            justify-content: space-around;
            flex-grow: 1;
            gap: 8px;
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
            box-sizing: border-box;
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
            padding: 6px 8px;
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

        /* Responsive Breakpoints for Block Puzzle on Mobile Screens */
        @media (max-width: 767.98px) {
            .puzzle-stats-col {
                max-width: 100% !important;
                order: 1;
            }
            .puzzle-board-col {
                max-width: 100% !important;
                order: 2;
            }
            .puzzle-tray-col {
                max-width: 100% !important;
                order: 3;
            }
            .puzzle-stats-grid {
                display: grid !important;
                grid-template-columns: repeat(3, 1fr) !important;
                gap: 6px !important;
            }
            .puzzle-booster-grid {
                display: grid !important;
                grid-template-columns: repeat(4, 1fr) !important;
                gap: 6px !important;
            }
            .puzzle-pieces-tray {
                flex-direction: row !important;
                justify-content: center !important;
                gap: 8px !important;
            }
            .puzzle-tray-card {
                min-height: 75px !important;
                max-height: 95px !important;
                padding: 4px !important;
            }
            .puzzle-board-container {
                padding: 6px !important;
                border-width: 2.5px !important;
                border-radius: 14px !important;
            }
            .puzzle-grid {
                gap: 3px !important;
            }
        }

        @media (max-width: 480px) {
            .puzzle-board-container {
                padding: 5px !important;
                border-width: 2px !important;
                border-radius: 12px !important;
            }
            .puzzle-grid {
                gap: 2.5px !important;
            }
            .puzzle-cell {
                border-radius: 4px !important;
            }
            .puzzle-tray-card {
                min-height: 68px !important;
                max-height: 86px !important;
                padding: 3px !important;
            }
            .puzzle-booster-btn {
                padding: 4px 2px !important;
                font-size: 9.5px !important;
            }
            .puzzle-booster-btn i {
                font-size: 11.5px !important;
            }
            .puzzle-stats-card {
                border-radius: 14px !important;
                padding: 8px !important;
            }
            .puzzle-tray-container {
                border-radius: 14px !important;
                padding: 8px 6px !important;
            }
        }

        @media (max-width: 360px) {
            .puzzle-board-container {
                padding: 3.5px !important;
            }
            .puzzle-grid {
                gap: 2px !important;
            }
            .puzzle-cell {
                border-radius: 3px !important;
            }
            .puzzle-tray-card {
                min-height: 60px !important;
                max-height: 75px !important;
            }
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

        /* --- AUTH LOGGED OUT / ACCESS REVOKED SPECIAL PAGE --- */
        .auth-logged-out-container {
            text-align: center;
            padding: 30px 20px;
            animation: fadeInRejection 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }

        .auth-logged-out-icon-wrapper {
            position: relative;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 104px;
            height: 104px;
            margin-bottom: 22px;
        }

        .auth-logged-out-icon-badge {
            width: 82px;
            height: 82px;
            border-radius: 50%;
            background: linear-gradient(135deg, #0F766E 0%, #0d9488 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            color: #ffffff;
            font-size: 2.3rem;
            box-shadow: 0 10px 25px rgba(15, 118, 110, 0.35);
        }

        .auth-logged-out-icon-badge.is-rejected {
            background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
            box-shadow: 0 10px 25px rgba(239, 68, 68, 0.4);
        }

        .auth-logged-out-title {
            color: #0F766E;
            font-size: 1.65rem;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 8px;
        }
        body.dark .auth-logged-out-title,
        [data-theme="dark"] .auth-logged-out-title {
            color: #2dd4bf;
        }

        .auth-logged-out-desc {
            color: #64748b;
            max-width: 480px;
            margin: 0 auto 20px auto;
            font-size: 0.95rem;
            line-height: 1.5;
        }
        body.dark .auth-logged-out-desc,
        [data-theme="dark"] .auth-logged-out-desc {
            color: #cbd5e1;
        }

        .auth-logged-out-box {
            background: rgba(15, 118, 110, 0.06);
            border: 1px dashed rgba(15, 118, 110, 0.35);
            border-radius: 16px;
            padding: 16px;
            margin-bottom: 24px;
            display: inline-block;
            max-width: 460px;
            width: 100%;
            text-align: left;
        }
        body.dark .auth-logged-out-box,
        [data-theme="dark"] .auth-logged-out-box {
            background: rgba(15, 118, 110, 0.12);
            border-color: rgba(15, 118, 110, 0.45);
        }

        /* CUSTOM DATE TIME PICKER MODAL DARK MODE STYLING */
        body.dark #anggotaDatePickerModal .modal-content,
        [data-theme="dark"] #anggotaDatePickerModal .modal-content {
            background-color: var(--card-bg, #1e293b) !important;
            color: var(--text-main, #f8fafc) !important;
        }
        body.dark #anggotaDatePickerModal .card-custom,
        [data-theme="dark"] #anggotaDatePickerModal .card-custom {
            background-color: var(--input-bg, #0f172a) !important;
            border-color: var(--border-color, #334155) !important;
        }
        body.dark #anggotaDatePickerModal .btn-light,
        [data-theme="dark"] #anggotaDatePickerModal .btn-light {
            background-color: #334155 !important;
            color: #f8fafc !important;
            border-color: #475569 !important;
        }
        body.dark #anggotaDatePickerModal select,
        [data-theme="dark"] #anggotaDatePickerModal select {
            color: #f8fafc !important;
            background-color: #1e293b !important;
        }
        body.dark #anggotaDatePickerModal input[type="number"],
        [data-theme="dark"] #anggotaDatePickerModal input[type="number"] {
            background-color: #0f172a !important;
            color: #f8fafc !important;
            border-color: #475569 !important;
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

        body.dark .savings-legend-chip.chip-sisa,
        [data-theme="dark"] .savings-legend-chip.chip-sisa {
            color: #4ade80;
            border-color: rgba(74, 222, 128, 0.35);
            background-color: rgba(74, 222, 128, 0.12);
        }
        body.dark .savings-legend-chip.chip-masuk,
        [data-theme="dark"] .savings-legend-chip.chip-masuk {
            color: #fbbf24;
            border-color: rgba(251, 191, 36, 0.35);
            background-color: rgba(251, 191, 36, 0.12);
        }
        body.dark .savings-legend-chip.chip-keluar,
        [data-theme="dark"] .savings-legend-chip.chip-keluar {
            color: #f87171;
            border-color: rgba(248, 113, 113, 0.35);
            background-color: rgba(248, 113, 113, 0.12);
        }

        /* --- MANAJEMEN KEUANGAN & SAVINGS DARK MODE SYNC --- */
        .card-savings-goal {
            background: linear-gradient(135deg, rgba(54, 116, 181, 0.08) 0%, rgba(16, 185, 129, 0.08) 100%);
            border: 1px solid var(--border-color);
            border-radius: 18px;
        }
        body.dark .card-savings-goal,
        [data-theme="dark"] .card-savings-goal {
            background: linear-gradient(135deg, rgba(56, 189, 248, 0.12) 0%, rgba(16, 185, 129, 0.12) 100%) !important;
            border-color: rgba(255, 255, 255, 0.1) !important;
        }

        .donut-center-circle {
            fill: var(--card-bg, #ffffff);
            transition: fill 0.2s ease;
        }
        body.dark .donut-center-circle,
        [data-theme="dark"] .donut-center-circle {
            fill: #1e293b !important;
        }

        .donut-empty-circle {
            stroke: var(--border-color, #e2e8f0);
        }
        body.dark .donut-empty-circle,
        [data-theme="dark"] .donut-empty-circle {
            stroke: #334155 !important;
        }

        .donut-center-label,
        .donut-center-percent,
        .cashflow-axis-text {
            fill: var(--text-sub, #64748b);
            transition: fill 0.2s ease;
        }
        body.dark .donut-center-label,
        [data-theme="dark"] .donut-center-label,
        body.dark .donut-center-percent,
        [data-theme="dark"] .donut-center-percent,
        body.dark .cashflow-axis-text,
        [data-theme="dark"] .cashflow-axis-text {
            fill: #94a3b8 !important;
        }

        .cashflow-gridline {
            stroke: rgba(0, 0, 0, 0.06);
        }
        body.dark .cashflow-gridline,
        [data-theme="dark"] .cashflow-gridline {
            stroke: rgba(255, 255, 255, 0.08) !important;
        }

        /* Dark mode table headers */
        body.dark .table thead.table-light,
        [data-theme="dark"] .table thead.table-light,
        body.dark .table thead.table-light th,
        [data-theme="dark"] .table thead.table-light th {
            background-color: rgba(15, 23, 42, 0.75) !important;
            color: #94a3b8 !important;
            border-color: var(--border-color) !important;
        }

        /* Dark mode list group items */
        body.dark .list-group-item,
        [data-theme="dark"] .list-group-item {
            background-color: transparent !important;
            color: var(--text-main) !important;
            border-color: var(--border-color) !important;
        }

        /* Adaptive Telegram AI & Profile Menu */
        .profile-menu-tg-icon {
            background: rgba(34, 158, 217, 0.12) !important;
            color: #0288D1 !important;
            transition: all 0.2s ease;
        }
        body.dark .profile-menu-tg-icon,
        [data-theme="dark"] .profile-menu-tg-icon,
        :root:not([data-theme="light"]):not(.dark) .profile-menu-tg-icon {
            background: rgba(56, 189, 248, 0.2) !important;
            color: #38BDF8 !important;
        }

        .list-group-item-action:hover {
            background-color: var(--bg-hover) !important;
            color: var(--text-main) !important;
        }
        body.dark .list-group-item-action:hover,
        [data-theme="dark"] .list-group-item-action:hover {
            background-color: var(--bg-hover) !important;
            color: var(--text-main) !important;
        }

        .tg-ai-circle-btn:hover {
            background-color: var(--border-color) !important;
        }
        .tg-ai-dropdown-item:hover {
            background-color: var(--bg-hover) !important;
        }
        .tg-ai-chip:hover {
            background-color: var(--bg-hover) !important;
            border-color: #0288D1 !important;
            color: #0288D1 !important;
        }
        body.dark .tg-ai-chip:hover,
        [data-theme="dark"] .tg-ai-chip:hover {
            border-color: #38BDF8 !important;
            color: #38BDF8 !important;
        }

        /* Telegram AI Fixed / Sticky Bottom Form Layout */
        .tg-ai-root {
            display: flex !important;
            flex-direction: column !important;
            height: calc(100vh - 112px) !important;
            max-height: calc(100vh - 112px) !important;
            min-height: 500px !important;
            overflow: hidden !important;
            position: relative !important;
            background: var(--bg-main) !important;
            border: 1px solid var(--border-color) !important;
            border-radius: 18px !important;
            box-shadow: var(--card-shadow) !important;
        }
        @media (max-width: 768px) {
            .tg-ai-root {
                height: calc(100vh - 80px) !important;
                max-height: calc(100vh - 80px) !important;
                min-height: 420px !important;
                border-radius: 14px !important;
            }
        }
        .tg-ai-messages-scroll {
            flex: 1 1 0 !important;
            min-height: 0 !important;
            overflow-y: auto !important;
            overflow-x: hidden !important;
            -webkit-overflow-scrolling: touch;
            scroll-behavior: smooth;
        }
        .tg-ai-bottom-bar {
            position: sticky !important;
            bottom: 0 !important;
            left: 0 !important;
            right: 0 !important;
            z-index: 50 !important;
            background: var(--card-bg) !important;
            border-top: 1px solid var(--border-color) !important;
            box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.08) !important;
            backdrop-filter: blur(8px);
            -webkit-backdrop-filter: blur(8px);
        }
        body.dark .tg-ai-bottom-bar,
        [data-theme="dark"] .tg-ai-bottom-bar {
            box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.35) !important;
        }

        /* Dark mode progress bar track */
        body.dark .progress,
        [data-theme="dark"] .progress {
            background-color: rgba(255, 255, 255, 0.1) !important;
        }

        /* Dark mode calendar date picker icon */
        body.dark input[type="date"]::-webkit-calendar-picker-indicator,
        [data-theme="dark"] input[type="date"]::-webkit-calendar-picker-indicator {
            filter: invert(1) brightness(1.8);
        }

        /* --- SAVINGS STAT CARDS (HARMONIZED LIGHT & DARK MODES) --- */
        .stat-card-saldo {
            border: 1px solid rgba(54, 116, 181, 0.18);
            border-left: 4px solid #3674B5 !important;
            background: #ffffff;
            border-radius: 12px;
            transition: all 0.2s ease;
        }
        .stat-card-masuk {
            border: 1px solid rgba(16, 185, 129, 0.18);
            border-left: 4px solid #10b981 !important;
            background: #ffffff;
            border-radius: 12px;
            transition: all 0.2s ease;
        }
        .stat-card-keluar {
            border: 1px solid rgba(239, 68, 68, 0.18);
            border-left: 4px solid #ef4444 !important;
            background: #ffffff;
            border-radius: 12px;
            transition: all 0.2s ease;
        }
        body.dark .stat-card-saldo,
        [data-theme="dark"] .stat-card-saldo {
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-left: 4px solid #38bdf8 !important;
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .stat-card-masuk,
        [data-theme="dark"] .stat-card-masuk {
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-left: 4px solid #34d399 !important;
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .stat-card-keluar,
        [data-theme="dark"] .stat-card-keluar {
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-left: 4px solid #f87171 !important;
            background: var(--card-bg, #1e293b) !important;
        }

        .savings-trx-card {
            border: 1px solid rgba(0, 0, 0, 0.08);
            background: #ffffff;
            border-radius: 12px;
            transition: transform 0.15s ease, box-shadow 0.15s ease;
        }
        .savings-trx-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
        }
        body.dark .savings-trx-card,
        [data-theme="dark"] .savings-trx-card {
            border: 1px solid rgba(255, 255, 255, 0.08);
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .savings-trx-card:hover,
        [data-theme="dark"] .savings-trx-card:hover {
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        }

        .savings-filter-panel,
        .shopping-filter-panel {
            border: 1px solid rgba(0, 0, 0, 0.08);
            background: #ffffff;
            border-radius: 14px;
        }
        body.dark .savings-filter-panel,
        body.dark .shopping-filter-panel,
        [data-theme="dark"] .savings-filter-panel,
        [data-theme="dark"] .shopping-filter-panel {
            border: 1px solid rgba(255, 255, 255, 0.08);
            background: var(--card-bg, #1e293b) !important;
        }

        /* --- SAVINGS & SHOPPING MENU LIST & RESPONSIVE TABLE STYLES --- */
        .savings-menu-nav,
        .shopping-menu-nav {
            display: flex;
            gap: 8px;
            border-bottom: 2px solid rgba(0, 0, 0, 0.06);
            padding-bottom: 8px;
            overflow-x: auto;
            -webkit-overflow-scrolling: touch;
        }
        body.dark .savings-menu-nav,
        body.dark .shopping-menu-nav,
        [data-theme="dark"] .savings-menu-nav,
        [data-theme="dark"] .shopping-menu-nav {
            border-bottom-color: rgba(255, 255, 255, 0.08);
        }

        .savings-menu-item,
        .shopping-menu-item {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 16px;
            border-radius: 10px;
            font-weight: 600;
            font-size: 0.88rem;
            color: var(--text-sub, #64748b);
            background: transparent;
            border: 1px solid transparent;
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;
            text-decoration: none;
        }
        .savings-menu-item:hover,
        .shopping-menu-item:hover {
            background-color: rgba(0, 0, 0, 0.04);
            color: var(--text-main, #1e293b);
        }
        body.dark .savings-menu-item:hover,
        body.dark .shopping-menu-item:hover,
        [data-theme="dark"] .savings-menu-item:hover,
        [data-theme="dark"] .shopping-menu-item:hover {
            background-color: rgba(255, 255, 255, 0.06);
            color: #f8fafc;
        }

        .savings-menu-item.active-all,
        .shopping-menu-item.active-cat {
            background-color: #3674B5;
            color: #ffffff !important;
            box-shadow: 0 2px 8px rgba(54, 116, 181, 0.25);
        }
        .savings-menu-item.active-masuk {
            background-color: #10b981;
            color: #ffffff !important;
            box-shadow: 0 2px 8px rgba(16, 185, 129, 0.25);
        }
        .savings-menu-item.active-keluar {
            background-color: #ef4444;
            color: #ffffff !important;
            box-shadow: 0 2px 8px rgba(239, 68, 68, 0.25);
        }

        .table-savings,
        .table-shopping {
            font-size: 0.9rem;
            min-width: 620px;
        }
        @media (max-width: 576px) {
            .table-savings,
            .table-shopping {
                font-size: 0.82rem;
                min-width: 520px;
            }
            .table-savings th, 
            .table-savings td,
            .table-shopping th,
            .table-shopping td {
                padding: 8px 6px !important;
            }
        }

        /* ==================================================== */
        /* --- SHARE SCREEN (GOOGLE DRIVE CLOUD SYNC) --- */
        /* ==================================================== */
        @keyframes floatOffline {
            0% { transform: translateY(-10px); }
            100% { transform: translateY(10px); }
        }
        @keyframes rotateSpin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        @keyframes pulseGlow {
            0% { opacity: 0.18; transform: scale(0.95); }
            100% { opacity: 0.35; transform: scale(1.05); }
        }
        .share-offline-container {
            width: 160px;
            height: 160px;
            margin: 0 auto;
            display: flex;
            align-items: center;
            justify-content: center;
            animation: floatOffline 1.4s ease-in-out infinite alternate;
        }
        .share-offline-glow {
            position: absolute;
            width: 120px;
            height: 120px;
            border-radius: 50%;
            background-color: var(--primary-color);
            animation: pulseGlow 1.2s ease-in-out infinite alternate;
        }
        .share-offline-spin-ring {
            position: absolute;
            width: 120px;
            height: 120px;
            display: flex;
            align-items: center;
            justify-content: center;
            animation: rotateSpin 4s linear infinite;
        }
        .share-offline-center-circle {
            position: absolute;
            width: 76px;
            height: 76px;
            border-radius: 50%;
            background-color: var(--card-bg);
            border: 2px solid var(--primary-color);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 2;
        }
        .spinning {
            animation: rotateSpin 1s linear infinite;
        }
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
        /* --- TRANSFER FILE (100% OFFLINE) DARK MODE SUPPORT --- */
        /* ==================================================== */
        .transfer-sub-menu-bar {
            background-color: #e2e8f0;
            border: 1px solid #cbd5e1;
            transition: all 0.2s ease;
        }
        body.dark .transfer-sub-menu-bar,
        [data-theme="dark"] .transfer-sub-menu-bar,
        :root:not([data-theme="light"]):not(.dark) .transfer-sub-menu-bar {
            background-color: #1e293b !important;
            border-color: #334155 !important;
        }
        .transfer-sub-btn-active {
            background-color: #0284c7 !important;
            color: #ffffff !important;
            border: none !important;
        }
        .transfer-sub-btn-inactive {
            color: #475569 !important;
            border: none !important;
            background: transparent !important;
        }
        body.dark .transfer-sub-btn-inactive,
        [data-theme="dark"] .transfer-sub-btn-inactive,
        :root:not([data-theme="light"]):not(.dark) .transfer-sub-btn-inactive {
            color: #94a3b8 !important;
        }
        body.dark .transfer-sub-btn-inactive:hover,
        [data-theme="dark"] .transfer-sub-btn-inactive:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-sub-btn-inactive:hover {
            color: #f8fafc !important;
            background-color: rgba(255, 255, 255, 0.06) !important;
        }

        .transfer-header-card {
            background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
            color: #ffffff;
            border: 1px solid transparent;
        }
        body.dark .transfer-header-card,
        [data-theme="dark"] .transfer-header-card,
        :root:not([data-theme="light"]):not(.dark) .transfer-header-card {
            background: linear-gradient(135deg, #075985 0%, #0f172a 100%) !important;
            border-color: rgba(56, 189, 248, 0.3) !important;
        }

        .transfer-badge-offline {
            background-color: #ffffff;
            color: #0284c7;
            border: 1px solid transparent;
        }
        body.dark .transfer-badge-offline,
        [data-theme="dark"] .transfer-badge-offline,
        :root:not([data-theme="light"]):not(.dark) .transfer-badge-offline {
            background-color: rgba(56, 189, 248, 0.2) !important;
            color: #38bdf8 !important;
            border-color: rgba(56, 189, 248, 0.4) !important;
        }

        .transfer-btn-refresh {
            background-color: #ffffff;
            color: #0369a1;
            border: 1px solid transparent;
        }
        body.dark .transfer-btn-refresh,
        [data-theme="dark"] .transfer-btn-refresh,
        :root:not([data-theme="light"]):not(.dark) .transfer-btn-refresh {
            background-color: rgba(15, 23, 42, 0.6) !important;
            color: #f8fafc !important;
            border-color: rgba(255, 255, 255, 0.15) !important;
        }
        body.dark .transfer-btn-refresh:hover,
        [data-theme="dark"] .transfer-btn-refresh:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-btn-refresh:hover {
            background-color: rgba(56, 189, 248, 0.2) !important;
            color: #38bdf8 !important;
            border-color: #38bdf8 !important;
        }

        .transfer-tabs-nav {
            border-bottom: 1px solid #e2e8f0;
        }
        body.dark .transfer-tabs-nav,
        [data-theme="dark"] .transfer-tabs-nav,
        :root:not([data-theme="light"]):not(.dark) .transfer-tabs-nav {
            border-bottom-color: #334155 !important;
        }
        body.dark .transfer-tabs-nav .btn-outline-secondary,
        [data-theme="dark"] .transfer-tabs-nav .btn-outline-secondary,
        :root:not([data-theme="light"]):not(.dark) .transfer-tabs-nav .btn-outline-secondary {
            background-color: #1e293b !important;
            color: #cbd5e1 !important;
            border-color: #334155 !important;
        }
        body.dark .transfer-tabs-nav .btn-outline-secondary:hover,
        [data-theme="dark"] .transfer-tabs-nav .btn-outline-secondary:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-tabs-nav .btn-outline-secondary:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
            border-color: #475569 !important;
        }

        .transfer-card {
            background-color: #ffffff;
            border: 1px solid rgba(0, 0, 0, 0.05);
        }
        body.dark .transfer-card,
        [data-theme="dark"] .transfer-card,
        :root:not([data-theme="light"]):not(.dark) .transfer-card,
        body.dark #transferTabContent .card,
        [data-theme="dark"] #transferTabContent .card,
        :root:not([data-theme="light"]):not(.dark) #transferTabContent .card {
            background-color: #1e293b !important;
            border: 1px solid #334155 !important;
            color: #f8fafc !important;
            box-shadow: 0 4px 16px rgba(0,0,0,0.3) !important;
        }

        .transfer-dropzone {
            border-color: #0284C7 !important;
            background-color: #f8fafc;
            color: #1e293b;
        }
        body.dark .transfer-dropzone,
        [data-theme="dark"] .transfer-dropzone,
        :root:not([data-theme="light"]):not(.dark) .transfer-dropzone {
            border-color: #38bdf8 !important;
            background-color: #0f172a !important;
            color: #f8fafc !important;
        }

        .transfer-list-group {
            border-color: #e2e8f0;
            background-color: #ffffff;
        }
        body.dark .transfer-list-group,
        [data-theme="dark"] .transfer-list-group,
        :root:not([data-theme="light"]):not(.dark) .transfer-list-group {
            border-color: #334155 !important;
            background-color: #0f172a !important;
        }

        .transfer-list-item {
            background-color: #ffffff;
            color: #1e293b;
            border-color: #e2e8f0;
        }
        body.dark .transfer-list-item,
        [data-theme="dark"] .transfer-list-item,
        :root:not([data-theme="light"]):not(.dark) .transfer-list-item {
            background-color: #0f172a !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }
        body.dark .transfer-list-item:hover,
        [data-theme="dark"] .transfer-list-item:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-list-item:hover {
            background-color: #1e293b !important;
        }

        .transfer-progress {
            background-color: #e2e8f0;
        }
        body.dark .transfer-progress,
        [data-theme="dark"] .transfer-progress,
        :root:not([data-theme="light"]):not(.dark) .transfer-progress {
            background-color: #0f172a !important;
            border: 1px solid #334155 !important;
        }

        .transfer-thead {
            background-color: #f8fafc;
            color: #475569;
        }
        .transfer-thead th {
            background-color: #f8fafc;
            color: #475569;
            border-bottom: 1px solid #e2e8f0;
            font-weight: 600;
        }
        body.dark .transfer-thead,
        [data-theme="dark"] .transfer-thead,
        :root:not([data-theme="light"]):not(.dark) .transfer-thead {
            background-color: #0f172a !important;
            color: #94a3b8 !important;
        }
        body.dark .transfer-thead th,
        [data-theme="dark"] .transfer-thead th,
        :root:not([data-theme="light"]):not(.dark) .transfer-thead th {
            background-color: #0f172a !important;
            color: #cbd5e1 !important;
            border-bottom: 1px solid #334155 !important;
            border-top: none !important;
        }

        body.dark .transfer-table,
        [data-theme="dark"] .transfer-table,
        :root:not([data-theme="light"]):not(.dark) .transfer-table {
            border-color: #334155 !important;
            color: #f8fafc !important;
        }
        body.dark .transfer-table td,
        [data-theme="dark"] .transfer-table td,
        :root:not([data-theme="light"]):not(.dark) .transfer-table td {
            background-color: transparent !important;
            color: #f8fafc !important;
            border-bottom: 1px solid #334155 !important;
        }
        body.dark .transfer-table tr:hover td,
        [data-theme="dark"] .transfer-table tr:hover td,
        :root:not([data-theme="light"]):not(.dark) .transfer-table tr:hover td {
            background-color: rgba(56, 189, 248, 0.08) !important;
        }

        body.dark .transfer-table .btn-outline-primary,
        [data-theme="dark"] .transfer-table .btn-outline-primary,
        :root:not([data-theme="light"]):not(.dark) .transfer-table .btn-outline-primary {
            color: #38bdf8 !important;
            border-color: #38bdf8 !important;
            background-color: rgba(56, 189, 248, 0.1) !important;
        }
        body.dark .transfer-table .btn-outline-primary:hover,
        [data-theme="dark"] .transfer-table .btn-outline-primary:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-table .btn-outline-primary:hover {
            background-color: #0284c7 !important;
            color: #ffffff !important;
        }

        body.dark .transfer-table .btn-outline-secondary,
        [data-theme="dark"] .transfer-table .btn-outline-secondary,
        :root:not([data-theme="light"]):not(.dark) .transfer-table .btn-outline-secondary {
            color: #cbd5e1 !important;
            border-color: #475569 !important;
            background-color: rgba(255, 255, 255, 0.05) !important;
        }
        body.dark .transfer-table .btn-outline-secondary:hover,
        [data-theme="dark"] .transfer-table .btn-outline-secondary:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-table .btn-outline-secondary:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
        }

        .transfer-log-group {
            border-color: #e2e8f0;
            background-color: #ffffff;
        }
        body.dark .transfer-log-group,
        [data-theme="dark"] .transfer-log-group,
        :root:not([data-theme="light"]):not(.dark) .transfer-log-group {
            border-color: #334155 !important;
            background-color: #0f172a !important;
        }
        .transfer-log-item {
            background-color: #ffffff;
            color: #1e293b;
            border-color: #e2e8f0;
        }
        body.dark .transfer-log-item,
        [data-theme="dark"] .transfer-log-item,
        :root:not([data-theme="light"]):not(.dark) .transfer-log-item {
            background-color: #0f172a !important;
            color: #f8fafc !important;
            border-color: #334155 !important;
        }
        body.dark .transfer-log-item:hover,
        [data-theme="dark"] .transfer-log-item:hover,
        :root:not([data-theme="light"]):not(.dark) .transfer-log-item:hover {
            background-color: #1e293b !important;
        }

        /* ==================================================== */
        /* --- MANAJEMEN AKUN (KHUSUS ADMIN) DARK MODE SUPPORT --- */
        /* ==================================================== */
        body.dark .admin-card,
        [data-theme="dark"] .admin-card,
        :root:not([data-theme="light"]):not(.dark) .admin-card,
        body.dark .admin-msg-card,
        [data-theme="dark"] .admin-msg-card,
        :root:not([data-theme="light"]):not(.dark) .admin-msg-card,
        body.dark .admin-table-card,
        [data-theme="dark"] .admin-table-card,
        :root:not([data-theme="light"]):not(.dark) .admin-table-card {
            background-color: #1e293b !important;
            border: 1px solid #334155 !important;
            color: #ffffff !important;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35) !important;
        }

        body.dark .admin-text-main,
        [data-theme="dark"] .admin-text-main,
        :root:not([data-theme="light"]):not(.dark) .admin-text-main,
        body.dark .admin-section-title,
        [data-theme="dark"] .admin-section-title,
        :root:not([data-theme="light"]):not(.dark) .admin-section-title,
        body.dark #adminSubTabContent .text-dark,
        [data-theme="dark"] #adminSubTabContent .text-dark,
        :root:not([data-theme="light"]):not(.dark) #adminSubTabContent .text-dark,
        body.dark .admin-page-container .text-dark,
        [data-theme="dark"] .admin-page-container .text-dark,
        :root:not([data-theme="light"]):not(.dark) .admin-page-container .text-dark,
        body.dark .modal .text-dark,
        [data-theme="dark"] .modal .text-dark,
        :root:not([data-theme="light"]):not(.dark) .modal .text-dark {
            color: #ffffff !important;
        }

        body.dark .admin-text-sub,
        [data-theme="dark"] .admin-text-sub,
        :root:not([data-theme="light"]):not(.dark) .admin-text-sub,
        body.dark #adminSubTabContent .text-secondary,
        [data-theme="dark"] #adminSubTabContent .text-secondary,
        :root:not([data-theme="light"]):not(.dark) #adminSubTabContent .text-secondary,
        body.dark #adminSubTabContent .text-muted,
        [data-theme="dark"] #adminSubTabContent .text-muted,
        :root:not([data-theme="light"]):not(.dark) #adminSubTabContent .text-muted,
        body.dark .admin-page-container .text-secondary,
        [data-theme="dark"] .admin-page-container .text-secondary,
        :root:not([data-theme="light"]):not(.dark) .admin-page-container .text-secondary,
        body.dark .admin-page-container .text-muted,
        [data-theme="dark"] .admin-page-container .text-muted,
        :root:not([data-theme="light"]):not(.dark) .admin-page-container .text-muted,
        body.dark .modal .text-secondary,
        [data-theme="dark"] .modal .text-secondary,
        :root:not([data-theme="light"]):not(.dark) .modal .text-secondary {
            color: #cbd5e1 !important;
        }

        body.dark .admin-dots-btn,
        [data-theme="dark"] .admin-dots-btn,
        :root:not([data-theme="light"]):not(.dark) .admin-dots-btn {
            background-color: #1e293b !important;
            color: #ffffff !important;
            border: 1px solid #334155 !important;
        }
        body.dark .admin-dots-btn:hover,
        [data-theme="dark"] .admin-dots-btn:hover,
        :root:not([data-theme="light"]):not(.dark) .admin-dots-btn:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
            border-color: #475569 !important;
        }

        body.dark .admin-badge-glow,
        [data-theme="dark"] .admin-badge-glow,
        :root:not([data-theme="light"]):not(.dark) .admin-badge-glow,
        body.dark .admin-sidebar-badge,
        [data-theme="dark"] .admin-sidebar-badge,
        :root:not([data-theme="light"]):not(.dark) .admin-sidebar-badge {
            background-color: rgba(52, 211, 153, 0.18) !important;
            color: #34d399 !important;
            border: 1px solid rgba(52, 211, 153, 0.45) !important;
        }

        body.dark .admin-search-input,
        [data-theme="dark"] .admin-search-input,
        :root:not([data-theme="light"]):not(.dark) .admin-search-input {
            background-color: #0f172a !important;
            color: #ffffff !important;
            border: 1px solid #334155 !important;
        }
        body.dark .admin-search-input:focus,
        [data-theme="dark"] .admin-search-input:focus,
        :root:not([data-theme="light"]):not(.dark) .admin-search-input:focus {
            background-color: #0f172a !important;
            color: #ffffff !important;
            border-color: #10b981 !important;
            box-shadow: 0 0 0 0.25rem rgba(16, 185, 129, 0.25) !important;
        }
        body.dark .admin-search-input::placeholder,
        [data-theme="dark"] .admin-search-input::placeholder,
        :root:not([data-theme="light"]):not(.dark) .admin-search-input::placeholder {
            color: #94a3b8 !important;
        }

        body.dark .admin-filter-chip-inactive,
        [data-theme="dark"] .admin-filter-chip-inactive,
        :root:not([data-theme="light"]):not(.dark) .admin-filter-chip-inactive {
            background-color: #1e293b !important;
            color: #cbd5e1 !important;
            border: 1px solid #334155 !important;
        }
        body.dark .admin-filter-chip-inactive:hover,
        [data-theme="dark"] .admin-filter-chip-inactive:hover,
        :root:not([data-theme="light"]):not(.dark) .admin-filter-chip-inactive:hover {
            background-color: #334155 !important;
            color: #ffffff !important;
            border-color: #475569 !important;
        }

        body.dark .admin-table,
        [data-theme="dark"] .admin-table,
        :root:not([data-theme="light"]):not(.dark) .admin-table {
            color: #ffffff !important;
            border-color: #334155 !important;
        }
        body.dark .admin-table thead th,
        [data-theme="dark"] .admin-table thead th,
        :root:not([data-theme="light"]):not(.dark) .admin-table thead th {
            background-color: #0f172a !important;
            color: #f1f5f9 !important;
            border-bottom: 1px solid #334155 !important;
        }
        body.dark .admin-table tbody tr,
        [data-theme="dark"] .admin-table tbody tr,
        :root:not([data-theme="light"]):not(.dark) .admin-table tbody tr {
            background-color: transparent !important;
            color: #ffffff !important;
        }
        body.dark .admin-table tbody tr:hover,
        [data-theme="dark"] .admin-table tbody tr:hover,
        :root:not([data-theme="light"]):not(.dark) .admin-table tbody tr:hover {
            background-color: #334155 !important;
        }
        body.dark .admin-table td,
        [data-theme="dark"] .admin-table td,
        :root:not([data-theme="light"]):not(.dark) .admin-table td {
            background-color: transparent !important;
            border-bottom: 1px solid #334155 !important;
            color: #ffffff !important;
        }
        body.dark .admin-table .table-success,
        [data-theme="dark"] .admin-table .table-success,
        :root:not([data-theme="light"]):not(.dark) .admin-table .table-success {
            background-color: rgba(5, 150, 105, 0.22) !important;
        }
        body.dark .admin-table .table-danger,
        [data-theme="dark"] .admin-table .table-danger,
        :root:not([data-theme="light"]):not(.dark) .admin-table .table-danger {
            background-color: rgba(220, 38, 38, 0.22) !important;
        }
        body.dark .admin-table-footer,
        [data-theme="dark"] .admin-table-footer,
        :root:not([data-theme="light"]):not(.dark) .admin-table-footer {
            background-color: #0f172a !important;
            border-top: 1px solid #334155 !important;
            color: #cbd5e1 !important;
        }
        body.dark .admin-table-footer span.fw-semibold,
        [data-theme="dark"] .admin-table-footer span.fw-semibold {
            color: #ffffff !important;
        }

        body.dark .admin-avatar-initials,
        [data-theme="dark"] .admin-avatar-initials,
        :root:not([data-theme="light"]):not(.dark) .admin-avatar-initials {
            background-color: rgba(56, 189, 248, 0.25) !important;
            color: #38bdf8 !important;
            border: 1px solid rgba(56, 189, 248, 0.35) !important;
        }

        body.dark .admin-lock-circle,
        [data-theme="dark"] .admin-lock-circle,
        :root:not([data-theme="light"]):not(.dark) .admin-lock-circle {
            background-color: rgba(220, 38, 38, 0.2) !important;
            color: #f87171 !important;
            border: 1px solid rgba(220, 38, 38, 0.35) !important;
        }

        body.dark .admin-info-box,
        [data-theme="dark"] .admin-info-box,
        :root:not([data-theme="light"]):not(.dark) .admin-info-box {
            background-color: #0f172a !important;
            border: 1px solid #334155 !important;
            color: #ffffff !important;
        }
        body.dark .admin-info-box code,
        [data-theme="dark"] .admin-info-box code,
        :root:not([data-theme="light"]):not(.dark) .admin-info-box code {
            color: #93c5fd !important;
            background-color: rgba(147, 197, 253, 0.1) !important;
        }

        body.dark .admin-security-alert,
        [data-theme="dark"] .admin-security-alert,
        :root:not([data-theme="light"]):not(.dark) .admin-security-alert {
            background-color: rgba(245, 158, 11, 0.15) !important;
            color: #fde68a !important;
            border: 1px solid rgba(245, 158, 11, 0.35) !important;
        }
        body.dark .admin-security-alert strong,
        [data-theme="dark"] .admin-security-alert strong {
            color: #ffffff !important;
        }
        body.dark .admin-security-alert code,
        [data-theme="dark"] .admin-security-alert code {
            color: #fca5a5 !important;
            background-color: rgba(239, 68, 68, 0.15) !important;
        }

        body.dark .admin-danger-box,
        [data-theme="dark"] .admin-danger-box,
        :root:not([data-theme="light"]):not(.dark) .admin-danger-box {
            background-color: rgba(220, 38, 38, 0.15) !important;
            border: 1px solid rgba(220, 38, 38, 0.35) !important;
            color: #fca5a5 !important;
        }
        body.dark .admin-danger-box .fw-bold,
        [data-theme="dark"] .admin-danger-box .fw-bold {
            color: #ffffff !important;
        }
        body.dark .admin-danger-box code,
        [data-theme="dark"] .admin-danger-box code {
            color: #fecaca !important;
            background-color: rgba(220, 38, 38, 0.25) !important;
        }

        body.dark .admin-form-control,
        [data-theme="dark"] .admin-form-control,
        :root:not([data-theme="light"]):not(.dark) .admin-form-control {
            background-color: #0f172a !important;
            color: #ffffff !important;
            border: 1px solid #334155 !important;
        }
        body.dark .admin-form-control:focus,
        [data-theme="dark"] .admin-form-control:focus,
        :root:not([data-theme="light"]):not(.dark) .admin-form-control:focus {
            background-color: #0f172a !important;
            color: #ffffff !important;
            border-color: #10b981 !important;
            box-shadow: 0 0 0 0.25rem rgba(16, 185, 129, 0.25) !important;
        }
        body.dark .admin-form-control::placeholder,
        [data-theme="dark"] .admin-form-control::placeholder,
        :root:not([data-theme="light"]):not(.dark) .admin-form-control::placeholder {
            color: #94a3b8 !important;
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
        /* --- KASIR MODE TERANG & MODE GELAP BORDER STYLES --- */
        /* ==================================================== */
        .kasir-header-card {
            border: 1.5px solid rgba(54, 116, 181, 0.22) !important;
            background: linear-gradient(135deg, rgba(54, 116, 181, 0.08) 0%, rgba(16, 185, 129, 0.08) 100%) !important;
            border-radius: 18px;
            box-shadow: 0 4px 16px rgba(54, 116, 181, 0.06);
            transition: all 0.2s ease;
        }
        body.dark .kasir-header-card,
        [data-theme="dark"] .kasir-header-card {
            border: 1.5px solid rgba(56, 189, 248, 0.25) !important;
            background: linear-gradient(135deg, rgba(56, 189, 248, 0.12) 0%, rgba(16, 185, 129, 0.12) 100%) !important;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
        }

        /* Stat cards in Mode Terang (Border Berwarna Disesuaikan) */
        .kasir-stat-sales {
            border: 1.5px solid rgba(16, 185, 129, 0.28) !important;
            border-left: 5px solid #10b981 !important;
            background: #ffffff !important;
            border-radius: 14px;
            box-shadow: 0 2px 8px rgba(16, 185, 129, 0.08);
            transition: all 0.2s ease;
        }
        .kasir-stat-warning {
            border: 1.5px solid rgba(245, 158, 11, 0.32) !important;
            border-left: 5px solid #f59e0b !important;
            background: #ffffff !important;
            border-radius: 14px;
            box-shadow: 0 2px 8px rgba(245, 158, 11, 0.08);
            transition: all 0.2s ease;
        }
        .kasir-stat-danger {
            border: 1.5px solid rgba(239, 68, 68, 0.28) !important;
            border-left: 5px solid #ef4444 !important;
            background: #ffffff !important;
            border-radius: 14px;
            box-shadow: 0 2px 8px rgba(239, 68, 68, 0.08);
            transition: all 0.2s ease;
        }
        body.dark .kasir-stat-sales,
        [data-theme="dark"] .kasir-stat-sales {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-left: 5px solid #34d399 !important;
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .kasir-stat-warning,
        [data-theme="dark"] .kasir-stat-warning {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-left: 5px solid #fbbf24 !important;
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .kasir-stat-danger,
        [data-theme="dark"] .kasir-stat-danger {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-left: 5px solid #f87171 !important;
            background: var(--card-bg, #1e293b) !important;
        }

        /* Kasir Sub-Nav Tabs */
        .kasir-nav-tab {
            border: 1.5px solid rgba(54, 116, 181, 0.2) !important;
            background: #ffffff !important;
            color: #334155 !important;
            border-radius: 10px;
            font-weight: 600;
            transition: all 0.2s ease;
            text-decoration: none;
        }
        .kasir-nav-tab:hover {
            background-color: rgba(54, 116, 181, 0.08) !important;
            border-color: #3674B5 !important;
            color: #3674B5 !important;
        }
        .kasir-nav-tab.active {
            background-color: #3674B5 !important;
            color: #ffffff !important;
            border-color: #3674B5 !important;
            box-shadow: 0 3px 10px rgba(54, 116, 181, 0.28);
        }
        body.dark .kasir-nav-tab,
        [data-theme="dark"] .kasir-nav-tab {
            border: 1.5px solid rgba(255, 255, 255, 0.1) !important;
            background: rgba(255, 255, 255, 0.04) !important;
            color: #cbd5e1 !important;
        }
        body.dark .kasir-nav-tab:hover,
        [data-theme="dark"] .kasir-nav-tab:hover {
            background: rgba(56, 189, 248, 0.12) !important;
            border-color: #38bdf8 !important;
            color: #38bdf8 !important;
        }
        body.dark .kasir-nav-tab.active,
        [data-theme="dark"] .kasir-nav-tab.active {
            background-color: #38bdf8 !important;
            color: #0f172a !important;
            border-color: #38bdf8 !important;
            box-shadow: 0 3px 10px rgba(56, 189, 248, 0.28);
        }

        /* Kasir POS Product Card */
        .kasir-product-card {
            border: 1.5px solid rgba(54, 116, 181, 0.18) !important;
            border-radius: 14px;
            background: #ffffff !important;
            transition: all 0.2s cubic-bezier(0.2, 0.8, 0.4, 1);
            user-select: none;
        }
        .kasir-product-card:hover {
            border-color: #3674B5 !important;
            transform: translateY(-2px);
            box-shadow: 0 6px 16px rgba(54, 116, 181, 0.16) !important;
        }
        body.dark .kasir-product-card,
        [data-theme="dark"] .kasir-product-card {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            background: var(--card-bg, #1e293b) !important;
        }
        body.dark .kasir-product-card:hover,
        [data-theme="dark"] .kasir-product-card:hover {
            border-color: #38bdf8 !important;
            box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35) !important;
        }

        /* Kasir Cart & Calculations Panel */
        .kasir-cart-panel {
            border: 1.5px solid rgba(16, 185, 129, 0.25) !important;
            border-top: 4.5px solid #10b981 !important;
            border-radius: 16px;
            background: #ffffff;
            box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
        }
        body.dark .kasir-cart-panel,
        [data-theme="dark"] .kasir-cart-panel {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-top: 4.5px solid #34d399 !important;
            background: var(--card-bg, #1e293b);
        }

        /* Quick Cash Chips */
        .kasir-quick-cash-chip {
            border: 1.5px solid rgba(54, 116, 181, 0.25) !important;
            background: #ffffff !important;
            color: #3674B5 !important;
            font-weight: 600;
            border-radius: 8px;
            transition: all 0.15s ease;
        }
        .kasir-quick-cash-chip:hover {
            background: #3674B5 !important;
            color: #ffffff !important;
            border-color: #3674B5 !important;
            box-shadow: 0 2px 6px rgba(54, 116, 181, 0.25);
        }
        body.dark .kasir-quick-cash-chip,
        [data-theme="dark"] .kasir-quick-cash-chip {
            border: 1.5px solid rgba(56, 189, 248, 0.3) !important;
            background: transparent !important;
            color: #38bdf8 !important;
        }
        body.dark .kasir-quick-cash-chip:hover,
        [data-theme="dark"] .kasir-quick-cash-chip:hover {
            background: #38bdf8 !important;
            color: #0f172a !important;
            border-color: #38bdf8 !important;
        }

        /* Kasir Change Box */
        .kasir-change-box {
            border: 1.5px solid rgba(16, 185, 129, 0.3) !important;
            background: rgba(16, 185, 129, 0.06) !important;
            border-radius: 12px;
        }
        body.dark .kasir-change-box,
        [data-theme="dark"] .kasir-change-box {
            border: 1.5px solid rgba(52, 211, 153, 0.3) !important;
            background: rgba(52, 211, 153, 0.1) !important;
        }

        /* Kasir Mobile Item Cards */
        .kasir-mobile-card {
            border: 1.5px solid rgba(54, 116, 181, 0.18) !important;
            background: #ffffff !important;
            border-radius: 14px;
            box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
            transition: all 0.2s ease;
        }
        .kasir-mobile-card:hover {
            border-color: rgba(54, 116, 181, 0.38) !important;
        }
        body.dark .kasir-mobile-card,
        [data-theme="dark"] .kasir-mobile-card {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            background: var(--card-bg, #1e293b) !important;
        }

        /* Kasir Dasbor Best-Seller & Warning Cards */
        .kasir-bestseller-card {
            border: 1.5px solid rgba(245, 158, 11, 0.25) !important;
            border-top: 4.5px solid #f59e0b !important;
            border-radius: 16px;
            background: #ffffff;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
        }
        .kasir-critical-stock-card {
            border: 1.5px solid rgba(239, 68, 68, 0.25) !important;
            border-top: 4.5px solid #ef4444 !important;
            border-radius: 16px;
            background: #ffffff;
            box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
        }
        body.dark .kasir-bestseller-card,
        [data-theme="dark"] .kasir-bestseller-card {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-top: 4.5px solid #fbbf24 !important;
            background: var(--card-bg, #1e293b);
        }
        body.dark .kasir-critical-stock-card,
        [data-theme="dark"] .kasir-critical-stock-card {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            border-top: 4.5px solid #f87171 !important;
            background: var(--card-bg, #1e293b);
        }

        .kasir-filter-card {
            border: 1.5px solid rgba(54, 116, 181, 0.18) !important;
            border-radius: 16px;
            background: #ffffff;
        }
        body.dark .kasir-filter-card,
        [data-theme="dark"] .kasir-filter-card {
            border: 1.5px solid rgba(255, 255, 255, 0.08) !important;
            background: var(--card-bg, #1e293b);
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
                        <div class="d-flex align-items-center gap-2 mb-2">
                            <img src="/app-logo-light.png?v=3" alt="My Singghara Logo" width="36" height="36" class="rounded-circle shadow-sm app-brand-logo">
                            <div>
                                <h3 class="fw-black mb-0 auth-title" style="font-size: 1.35rem;">My Singghara</h3>
                                <div class="d-flex align-items-center gap-1 flex-wrap mt-1">
                                    <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 rounded-pill" style="font-size: 10px;"><i class="bi bi-shield-check"></i> 100% Offline</span>
                                    <span class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 rounded-pill" style="font-size: 10px;"><i class="bi bi-globe"></i> mysingghara.com</span>
                                </div>
                            </div>
                        </div>
                        <p class="auth-subtitle small mb-3">Untuk membuka dashboard Singghara Web, silakan konfirmasi notifikasi di HP atau masukkan kode PIN akses di bawah ini.</p>
                        
                        <!-- DIRECT PIN ENTRY FORM ON PC BROWSER -->
                        <div class="p-3 mb-3 rounded-4 bg-light border border-success border-opacity-25" style="background: rgba(0, 168, 132, 0.06) !important;">
                            <label class="form-label small fw-bold text-success mb-1 d-flex align-items-center gap-1">
                                <i class="bi bi-key-fill"></i> Masukkan Kode PIN Akses PC (6 Digit)
                            </label>
                            <div class="input-group mb-1">
                                <input type="text" id="authPinInput" class="form-control form-control-lg text-center fw-bold" placeholder="Contoh: 123456" maxlength="8" onkeydown="if(event.key==='Enter') verifyPinFromWeb()" style="font-family: monospace; font-size: 20px; letter-spacing: 4px;">
                                <button class="btn btn-success px-3 fw-bold" type="button" id="authPinVerifyBtn" onclick="verifyPinFromWeb()">
                                    <i class="bi bi-shield-check me-1"></i> Masuk
                                </button>
                            </div>
                            <div id="authPinErrorMsg" class="text-danger small mt-1 d-none fw-semibold"></div>
                            <small class="text-muted d-block mt-1" style="font-size: 11px;">
                                *PIN dapat dilihat pada pop-up notifikasi atau menu <b>Web Connect</b> di aplikasi smartphone Anda.
                            </small>
                        </div>

                        <div class="d-flex flex-column gap-2 mb-3">
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
                                    <small class="auth-step-desc">Scan kode QR di samping melalui tombol <b>"Scan QR Barcode PC"</b> di HP.</small>
                                </div>
                            </div>
                        </div>

                        <div class="d-flex align-items-center gap-2 auth-subtitle small" id="authPollStatus">
                            <div class="spinner-grow spinner-grow-sm text-success" role="status"></div>
                            <span id="authPollStatusText">Menunggu persetujuan HP atau masukkan PIN di atas...</span>
                        </div>

                        <div class="mt-3 p-3 rounded-4 bg-success bg-opacity-10 border border-success border-opacity-25 text-start">
                            <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-2">
                                <span class="text-success fw-bold small"><i class="bi bi-hdd-network"></i> Bebas Kuota (100% Offline PC)</span>
                                <div class="d-flex gap-1 flex-wrap">
                                    <button type="button" class="btn btn-sm btn-outline-primary py-1 px-3 rounded-pill fw-bold pwa-install-trigger d-none" onclick="triggerPwaInstall()" style="font-size: 11px;" title="Pasang Progressive Web App langsung dari browser">
                                        <i class="bi bi-box-arrow-in-down me-1"></i> Pasang PWA
                                    </button>
                                    <a href="/pc-installer.exe" download="MySingghara-Pc-Installer.exe" class="btn btn-sm btn-primary py-1 px-3 rounded-pill fw-bold" style="font-size: 11px;" title="Unduh Aplikasi PC Native (.exe) - satu jendela mandiri tanpa browser dan tanpa terminal">
                                        <i class="bi bi-window-desktop me-1"></i> Aplikasi PC Native (.exe)
                                    </a>
                                </div>
                            </div>
                            <small class="auth-subtitle d-block" style="font-size: 11px; line-height: 1.4;">
                                Akses offline melalui domain <b>http://mysingghara.com:8080</b> atau <b>http://mysingghara.com</b>. Unduh <b>Aplikasi PC Native (.exe)</b> untuk memasang satu jendela mandiri My Singghara di Windows 64-bit &mdash; tanpa browser dan tanpa terminal, alamat HP sudah tertanam di dalam berkasnya. Berkas yang sama otomatis tersedia di folder <b>My Singghara/MySingghara-Pc-Installer.exe</b> pada HP.
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
                            <small class="auth-subtitle d-block mb-1 fw-semibold">Kode PIN Sesi Ini:</small>
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

            <!-- 3. SPECIAL LOGGED OUT VIEW (HALAMAN KHUSUS KELUAR DARI WEB - TANPA MEMINTA VERIFIKASI) -->
            <div id="authLoggedOutView" class="auth-logged-out-container d-none">
                <div class="auth-logged-out-icon-wrapper">
                    <div class="auth-logged-out-icon-badge" id="authLoggedOutIconBadge">
                        <i class="bi bi-box-arrow-right" id="authLoggedOutIcon"></i>
                    </div>
                </div>

                <div class="mb-2">
                    <span class="badge rounded-pill px-3 py-1 fw-bold" id="authLoggedOutStatusBadge" style="background: rgba(15, 118, 110, 0.15); color: #0F766E;">
                        <i class="bi bi-check2-circle me-1"></i> Sesi Telah Berakhir
                    </span>
                </div>

                <h3 class="auth-logged-out-title" id="authLoggedOutTitle">Anda Telah Keluar dari Web Connect</h3>
                <p class="auth-logged-out-desc" id="authLoggedOutDesc">
                    Sesi akses komputer (PC) telah diakhiri. Sambungan antara browser ini dan smartphone My Singghara telah ditutup dengan aman.
                </p>

                <div class="auth-logged-out-box mx-auto text-start">
                    <div class="d-flex align-items-center gap-2 fw-bold small mb-1" id="authLoggedOutBoxHeader" style="color: #0F766E;">
                        <i class="bi bi-shield-check"></i> Privasi & Keamanan Terlindungi
                    </div>
                    <small class="text-secondary d-block" id="authLoggedOutBoxDesc" style="font-size: 12px; line-height: 1.45;">
                        Sistem tidak meminta verifikasi ulang secara otomatis. Anda dapat menutup tab browser ini atau menghubungkan kembali sewaktu-waktu jika ingin melanjutkan pekerjaan.
                    </small>
                </div>

                <div class="d-flex flex-wrap justify-content-center gap-2 mt-2">
                    <button type="button" class="btn btn-outline-secondary rounded-pill px-4 py-2 fw-bold" onclick="window.close()" title="Tutup Halaman Ini">
                        <i class="bi bi-x-circle me-1"></i> Tutup Tab Browser
                    </button>
                    <button type="button" class="btn btn-primary rounded-pill px-4 py-2 fw-bold shadow-sm" onclick="requestConnectAgain()" style="background-color: #0F766E; border-color: #0F766E;">
                        <i class="bi bi-arrow-repeat me-1"></i> Hubungkan Kembali
                    </button>
                </div>
            </div>
        </div>
    </div>
    <!-- HEADER NAVBAR -->
    <nav class="navbar navbar-main sticky-top">
        <div class="container-fluid px-3 py-1 d-flex align-items-center justify-content-between">
            <div class="d-flex align-items-center gap-2 gap-sm-3">
                <button class="btn-toggle-menu" id="toggleSidebarBtn" onclick="toggleSidebar()" title="Toggle Menu" aria-label="Toggle Menu">
                    <i class="bi bi-list"></i>
                </button>
                <div class="d-flex flex-column">
                    <div class="d-flex align-items-center gap-2">
                        <img src="/app-logo-light.png?v=3" alt="My Singghara Logo" width="26" height="26" class="rounded-circle shadow-sm app-brand-logo">
                        <h5 class="mb-0 fw-bold fs-6 fs-sm-5 text-main">My Singghara</h5>
                        <span class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 rounded-pill d-none d-md-inline" style="font-size: 10px;"><i class="bi bi-globe me-1"></i>mysingghara.com</span>
                    </div>
                    <small class="text-secondary d-none d-sm-inline" style="font-size: 11px;">mysingghara.com:8080 Ã¢â‚¬Â¢ Akses Jaringan Lokal 100% Offline</small>
                </div>
            </div>
            <div class="d-flex align-items-center gap-2">
                <div class="position-relative d-inline-flex align-items-center">
                    <button type="button" class="btn btn-icon rounded-circle position-relative d-flex align-items-center justify-content-center" id="navNotificationBtn" onclick="openNotificationModal()" title="Pesan Notifikasi Cloud Messaging & Agenda" aria-label="Notifikasi" style="width: 38px; height: 38px; background: var(--card-bg, rgba(255,255,255,0.08)); border: 1px solid var(--border-color, rgba(148,163,184,0.2)); transition: all 0.2s ease;">
                        <i class="bi bi-bell" id="navNotificationIcon" style="font-size: 1.15rem; color: #64748B;"></i>
                        <span id="navNotificationBadge" class="position-absolute top-0 start-100 translate-middle badge rounded-pill" style="display: none; font-size: 10px; padding: 0.22em 0.5em; border: 2px solid var(--header-bg, #ffffff); font-weight: 700; color: #ffffff; transform: translate(-35%, -20%);">
                            0
                        </span>
                    </button>
                </div>
            </div>
        </div>
    </nav>

    <!-- SIDEBAR -->
    <div class="sidebar-wrapper" id="appSidebar">
        <!-- MOBILE SIDEBAR HEADER (Hanya tampil di layar mobile) -->
        <div class="sidebar-mobile-header align-items-center justify-content-between px-3 py-3 border-bottom border-secondary border-opacity-25" style="background: var(--sidebar-bg); flex-shrink: 0;">
            <div class="d-flex align-items-center gap-2">
                <img src="/app-logo-light.png?v=3" alt="My Singghara Logo" width="32" height="32" class="rounded-circle shadow-sm app-brand-logo">
                <div>
                    <div class="fw-bold text-main" style="font-size: 14px; line-height: 1.2;">My Singghara</div>
                    <div class="text-success fw-semibold d-flex align-items-center gap-1" style="font-size: 10px;">
                        <i class="bi bi-shield-check"></i> 100% Akses Offline
                    </div>
                </div>
            </div>
            <button type="button" class="btn btn-sm btn-outline-secondary rounded-circle d-flex align-items-center justify-content-center p-0" style="width: 32px; height: 32px;" onclick="closeMobileSidebar()" aria-label="Tutup Menu">
                <i class="bi bi-x fs-5"></i>
            </button>
        </div>

        <div class="sidebar-menu-scrollable">
            <div class="nav flex-column gap-1">
                <!-- DASHBOARD MENU -->
                <div class="nav-link-item active" onclick="switchTab('dashboard', this)" data-title="Dashboard">
                    <i class="bi bi-grid-1x2 fs-5"></i>
                    <span class="nav-text">Dashboard</span>
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
                        <div class="nav-link-item" onclick="switchTab('my_apps', this)" data-title="Semua Aplikasi">
                            <i class="bi bi-grid-3x3-gap-fill fs-5 text-warning"></i>
                            <span class="nav-text">Semua Aplikasi</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('block', this)" data-title="Block Puzzle">
                            <i class="bi bi-grid-fill fs-5" style="color: #2C5E6A;"></i>
                            <span class="nav-text">Block Puzzle</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('brick_game', this)" data-title="Brick Game">
                            <i class="bi bi-controller fs-5" style="color: #306230;"></i>
                            <span class="nav-text">Brick Game</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('quran', this)" data-title="Al-Qur'an">
                            <i class="bi bi-journal-bookmark-fill fs-5" style="color: #00796B;"></i>
                            <span class="nav-text">Al-Qur\'an</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('pembukuan', this)" data-title="Pembukuan">
                            <i class="bi bi-graph-up-arrow fs-5" style="color: #E65100;"></i>
                            <span class="nav-text">Pembukuan</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('mindmap', this)" data-title="MindMap AI">
                            <i class="bi bi-diagram-3-fill fs-5" style="color: #0288D1;"></i>
                            <span class="nav-text">MindMap AI</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('spinner', this)" data-title="Spinner">
                            <i class="bi bi-arrow-repeat fs-5" style="color: #8338EC;"></i>
                            <span class="nav-text">Spinner</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU MEDIA -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-media')" title="Media">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-collection-play-fill fs-5 sidebar-group-icon" style="color: #0E7490;"></i>
                            <span class="nav-text">Media</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-media"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-media" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small" style="color: #0E7490;">
                            <i class="bi bi-collection-play-fill me-1"></i> Media
                        </div>
                        <div class="nav-link-item" onclick="switchTab('pemutar_video', this)" data-title="Pemutar Video">
                            <i class="bi bi-camera-reels fs-5" style="color: #0E7490;"></i>
                            <span class="nav-text">Pemutar Video</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('handy_talky', this)" data-title="Handy Talky (HT)">
                            <i class="bi bi-broadcast fs-5" style="color: #10B981;"></i>
                            <span class="nav-text">Handy Talky (HT)</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('musik', this)" data-title="Musik">
                            <i class="bi bi-music-note-beamed fs-5" style="color: #E91E63;"></i>
                            <span class="nav-text">Musik</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU PEOPLE -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-people')" title="People">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-people-fill fs-5 sidebar-group-icon" style="color: #F59E0B;"></i>
                            <span class="nav-text">People</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-people"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-people" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small" style="color: #F59E0B;">
                            <i class="bi bi-people-fill me-1"></i> People
                        </div>
                        <div class="nav-link-item" onclick="switchTab('arisan_keluarga', this)" data-title="Arisan Keluarga">
                            <i class="bi bi-wallet2 fs-5 text-primary"></i>
                            <span class="nav-text">Arisan Keluarga</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('rekap_belanja', this)" data-title="Rekap Belanja Pasar">
                            <i class="bi bi-basket3-fill fs-5" style="color: #10B981;"></i>
                            <span class="nav-text">Rekap Belanja Pasar</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('kalkulator', this)" data-title="Kalkulator">
                            <i class="bi bi-calculator fs-5" style="color: #0EA5E9;"></i>
                            <span class="nav-text">Kalkulator</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

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
                        <div class="nav-link-item" onclick="switchTab('gaji', this)" data-title="Gaji">
                            <i class="bi bi-cash-stack fs-5" style="color: #0F766E;"></i>
                            <span class="nav-text">Gaji</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU MANAJEMEN MANDIRI -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-manajemen')" title="Manajemen Mandiri">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-wallet2 text-success fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">Manajemen Mandiri</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-manajemen"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-manajemen" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-success">
                            <i class="bi bi-wallet2 me-1"></i> Manajemen Mandiri
                        </div>
                        <div class="nav-link-item" onclick="switchTab('kasir', this)" data-title="Kasir">
                            <i class="bi bi-shop fs-5"></i>
                            <span class="nav-text">Kasir</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('shopping', this)" data-title="Daftar Belanja">
                            <i class="bi bi-cart3 fs-5"></i>
                            <span class="nav-text">Daftar Belanja</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('savings', this)" data-title="Catatan Tabungan">
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
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-note')" title="Note">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-journal-text text-primary fs-5 sidebar-group-icon"></i>
                            <span class="nav-text">Note</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-note"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-note" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small text-primary">
                            <i class="bi bi-journal-text me-1"></i> Note
                        </div>
                        <div class="nav-link-item" onclick="switchTab('notes', this)" data-title="Catatan">
                            <i class="bi bi-journal-text fs-5" style="color: #6366F1;"></i>
                            <span class="nav-text">Catatan</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('jadwal', this)" data-title="Jadwal Harian">
                            <i class="bi bi-clock-history fs-5" style="color: #43A047;"></i>
                            <span class="nav-text">Jadwal Harian</span>
                        </div>
                    </div>
                </div>

                <hr class="my-2 border-secondary opacity-25 sidebar-divider">

                <!-- MENU KONEKSI DAN BERBAGI -->
                <div class="sidebar-group">
                    <div class="sidebar-group-header d-flex align-items-center justify-content-between text-uppercase fw-bold mb-1" onclick="toggleSidebarGroup('grp-koneksi-berbagi')" title="Koneksi dan Berbagi">
                        <span class="d-flex align-items-center gap-2">
                            <i class="bi bi-broadcast-pin fs-5 sidebar-group-icon" style="color: #0284C7;"></i>
                            <span class="nav-text">Koneksi dan Berbagi</span>
                        </span>
                        <i class="bi bi-chevron-right nav-text group-chevron" id="chevron-grp-koneksi-berbagi"></i>
                    </div>
                    <div class="sidebar-sub-items ps-2" id="grp-koneksi-berbagi" style="display: none;">
                        <div class="sidebar-popover-title d-none fw-bold text-uppercase px-2 py-1 mb-2 border-bottom border-secondary border-opacity-25 small" style="color: #0284C7 !important;">
                            <i class="bi bi-broadcast-pin me-1"></i> Koneksi dan Berbagi
                        </div>
                        <div class="nav-link-item" onclick="switchTab('share', this)" data-title="Share">
                            <i class="bi bi-share fs-5" style="color: #3674B5;"></i>
                            <span class="nav-text">Share</span>
                        </div>
                        <div class="nav-link-item" onclick="switchTab('transfer_file', this)" data-title="Transfer File">
                            <i class="bi bi-arrow-left-right fs-5" style="color: #0284C7;"></i>
                            <span class="nav-text">Transfer File</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- SIDEBAR BOTTOM FOOTER (PROFIL & LOGOUT) -->
        <div class="sidebar-footer" id="sidebarFooter">
            <div class="sidebar-user-section">
                <div class="sidebar-profile-info" onclick="switchTab('profil', this)" role="button" title="Profil Pengguna" data-title="Profil Pengguna">
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

    <!-- GLOBAL CONFIRMATION MODAL -->
    <div class="modal fade" id="customConfirmModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-sm">
            <div class="modal-content card-custom text-center p-3 position-relative" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: 0 12px 35px rgba(0,0,0,0.35); overflow: hidden;">
                <!-- SWITCH ON/OFF MODE TERANG DAN GELAP (POJOK KANAN ATAS POP UP KELUAR DARI WEB CONNECT) -->
                <div id="confirmThemeToggleContainer" class="position-absolute d-none" style="top: 14px; right: 14px; z-index: 25;">
                    <div class="d-flex align-items-center gap-1 p-1 px-2 rounded-pill shadow-sm" style="background: var(--bg-hover); border: 1px solid var(--border-color); cursor: pointer;" onclick="toggleTheme()" title="Switch Mode Terang / Gelap">
                        <i class="bi bi-sun-fill" id="confirmThemeSunIcon" style="font-size: 13px; color: #f59e0b; transition: all 0.2s ease;"></i>
                        <div class="form-check form-switch p-0 m-0 d-flex align-items-center" style="min-height: auto;">
                            <input class="form-check-input m-0" type="checkbox" role="switch" id="confirmThemeSwitch" style="cursor: pointer; width: 32px; height: 17px;" onchange="event.stopPropagation(); toggleTheme();">
                        </div>
                        <i class="bi bi-moon-stars-fill" id="confirmThemeMoonIcon" style="font-size: 11px; color: #94a3b8; transition: all 0.2s ease;"></i>
                    </div>
                </div>

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

    <script src="/assets/js/bootstrap.bundle.min.js"></script>
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
            },
            remove: function(key) {
                try {
                    localStorage.removeItem(this.PREFIX + key);
                } catch(e) {}
            },
            clear: function() {
                try {
                    for (let i = localStorage.length - 1; i >= 0; i--) {
                        const k = localStorage.key(i);
                        if (k && k.startsWith(this.PREFIX)) {
                            localStorage.removeItem(k);
                        }
                    }
                } catch(e) {}
            }
        };
        window.StorageCache = StorageCache;

        function invalidateApiCache(cacheKey) {
            try {
                if (typeof StorageCache !== 'undefined' && StorageCache) {
                    if (cacheKey) {
                        StorageCache.remove(cacheKey);
                    } else {
                        StorageCache.clear();
                    }
                } else if (cacheKey) {
                    localStorage.removeItem('singghara_offline_cache_' + cacheKey);
                }
            } catch(e) {
                console.warn('Error invalidating cache:', e);
            }
        }
        window.invalidateApiCache = invalidateApiCache;

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

            // Tampilkan fitur switch on/off mode terang & gelap di pojok kanan atas pop up Keluar dari Web Connect
            const themeToggleContainer = document.getElementById('confirmThemeToggleContainer');
            if (themeToggleContainer) {
                const isKeluarTitle = opts.title && (
                    opts.title.toLowerCase().indexOf('keluar') !== -1 ||
                    opts.title.toLowerCase().indexOf('logout') !== -1
                );
                if (isKeluarTitle || opts.isLogout || opts.showThemeSwitch) {
                    themeToggleContainer.classList.remove('d-none');
                } else {
                    themeToggleContainer.classList.add('d-none');
                }
            }

            // Sinkronkan status switch dengan mode aktif saat ini
            const currentActiveTheme = document.documentElement.getAttribute('data-theme') || 'light';
            const confirmThemeSwitch = document.getElementById('confirmThemeSwitch');
            const confirmThemeSunIcon = document.getElementById('confirmThemeSunIcon');
            const confirmThemeMoonIcon = document.getElementById('confirmThemeMoonIcon');
            if (confirmThemeSwitch) {
                confirmThemeSwitch.checked = (currentActiveTheme === 'dark');
            }
            if (confirmThemeSunIcon) {
                confirmThemeSunIcon.style.opacity = (currentActiveTheme === 'dark') ? '0.4' : '1.0';
                confirmThemeSunIcon.style.color = (currentActiveTheme === 'dark') ? '#94a3b8' : '#f59e0b';
            }
            if (confirmThemeMoonIcon) {
                confirmThemeMoonIcon.style.opacity = (currentActiveTheme === 'dark') ? '1.0' : '0.4';
                confirmThemeMoonIcon.style.color = (currentActiveTheme === 'dark') ? '#38bdf8' : '#94a3b8';
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

            const navThemeIcon = document.getElementById('navThemeIcon');
            if (navThemeIcon) {
                navThemeIcon.className = (actualTheme === 'dark') ? 'bi bi-sun-fill text-warning' : 'bi bi-moon-stars';
            }

            const confirmThemeSwitch = document.getElementById('confirmThemeSwitch');
            const confirmThemeSunIcon = document.getElementById('confirmThemeSunIcon');
            const confirmThemeMoonIcon = document.getElementById('confirmThemeMoonIcon');
            if (confirmThemeSwitch) {
                confirmThemeSwitch.checked = (actualTheme === 'dark');
            }
            if (confirmThemeSunIcon) {
                confirmThemeSunIcon.style.opacity = (actualTheme === 'dark') ? '0.4' : '1.0';
                confirmThemeSunIcon.style.color = (actualTheme === 'dark') ? '#94a3b8' : '#f59e0b';
            }
            if (confirmThemeMoonIcon) {
                confirmThemeMoonIcon.style.opacity = (actualTheme === 'dark') ? '1.0' : '0.4';
                confirmThemeMoonIcon.style.color = (actualTheme === 'dark') ? '#38bdf8' : '#94a3b8';
            }

            document.body.classList.toggle('dark', actualTheme === 'dark');

            if (typeof syncBrickGameWithAppTheme === 'function') {
                syncBrickGameWithAppTheme(actualTheme);
            }
            if (typeof renderDashboardPrayerTimes === 'function') {
                renderDashboardPrayerTimes();
            }
            if (typeof renderTelegramAiView === 'function' && (currentTab === 'telegram_ai' || currentTab === 'telegram' || currentTab === 'tg')) {
                renderTelegramAiView();
            }
            if (typeof renderProfileUI === 'function' && (currentTab === 'profil' || currentTab === 'profile')) {
                renderProfileUI();
            }
            updateRoundFaviconTheme(actualTheme);
        }

        function updateRoundFaviconTheme(theme) {
            const isDark = (theme === 'dark');
            const fav32 = (isDark ? '/favicon-dark-32x32.png' : '/favicon-light-32x32.png') + '?v=3';
            const fav16 = (isDark ? '/favicon-dark-16x16.png' : '/favicon-light-16x16.png') + '?v=3';
            const favIco = (isDark ? '/favicon-dark.ico' : '/favicon-light.ico') + '?v=3';

            const favDefault = document.getElementById('favDefault');
            if (favDefault) favDefault.href = fav32;
            const favShortcut = document.getElementById('favShortcut');
            if (favShortcut) favShortcut.href = favIco;

            // Update in-page round brand logos
            const brandLogos = document.querySelectorAll('.app-brand-logo');
            brandLogos.forEach(img => {
                img.src = '/app-logo-' + (isDark ? 'dark' : 'light') + '.png?v=3';
            });

            const metaTheme = document.getElementById('metaThemeColor');
            if (metaTheme) metaTheme.content = isDark ? '#0f172a' : '#3674B5';
        }

        function toggleTheme() {
            const currentTheme = document.documentElement.getAttribute('data-theme') || 'light';
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            localStorage.setItem('theme', newTheme);
            applyTheme(newTheme);
        }

        initTheme();

        // Progressive Web App (PWA) Service Worker Registration & Install Prompt
        let deferredPwaPrompt = null;
        if ('serviceWorker' in navigator) {
            window.addEventListener('load', () => {
                navigator.serviceWorker.register('/sw.js').then((reg) => {
                    console.log('[PWA] Service Worker terdaftar:', reg.scope);
                }).catch((err) => {
                    console.warn('[PWA] Service Worker gagal didaftarkan:', err);
                });
            });
        }

        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            deferredPwaPrompt = e;
            const pwaBtns = document.querySelectorAll('.pwa-install-trigger');
            pwaBtns.forEach(btn => btn.classList.remove('d-none'));
        });

        window.addEventListener('appinstalled', () => {
            deferredPwaPrompt = null;
            const pwaBtns = document.querySelectorAll('.pwa-install-trigger');
            pwaBtns.forEach(btn => btn.classList.add('d-none'));
            console.log('[PWA] My Singghara berhasil dipasang di desktop!');
        });

        function triggerPwaInstall() {
            if (deferredPwaPrompt) {
                deferredPwaPrompt.prompt();
                deferredPwaPrompt.userChoice.then((choiceResult) => {
                    if (choiceResult.outcome === 'accepted') {
                        console.log('[PWA] Pengguna menyetujui instalasi PWA');
                    }
                    deferredPwaPrompt = null;
                });
            } else {
                showCustomToast('Prompt pasang tidak tersedia. Buka menu browser (titik tiga) lalu pilih "Instal aplikasi" / "Tambahkan ke layar utama".');
            }
        }

        // Listen for system theme changes
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
                const saved = localStorage.getItem('theme');
                if (!saved) {
                    applyTheme(e.matches ? 'dark' : 'light');
                }
            });
        }

        // Profile Modal & Web Logout Functions
        const AVATAR_PRESETS = [
            { bg: 'linear-gradient(135deg, #2A84EB, #1456A5)', icon: 'bi-person-fill' },
            { bg: 'linear-gradient(135deg, #FF5E62, #FF9966)', icon: 'bi-emoji-smile-fill' },
            { bg: 'linear-gradient(135deg, #11998E, #38EF7D)', icon: 'bi-star-fill' },
            { bg: 'linear-gradient(135deg, #00C6FF, #0072FF)', icon: 'bi-heart-fill' },
            { bg: 'linear-gradient(135deg, #8A2387, #E94057)', icon: 'bi-palette-fill' }
        ];

        function getInitialsFromName(name) {
            if (!name || !name.trim()) return 'PS';
            const parts = name.trim().split(/\s+/).filter(Boolean);
            if (parts.length === 1) {
                return parts[0].substring(0, Math.min(2, parts[0].length)).toUpperCase();
            }
            return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
        }

        function getGradientForName(name) {
            const gradients = [
                'linear-gradient(135deg, #2563EB, #1D4ED8)',
                'linear-gradient(135deg, #059669, #047857)',
                'linear-gradient(135deg, #7C3AED, #6D28D9)',
                'linear-gradient(135deg, #D97706, #B45309)',
                'linear-gradient(135deg, #DC2626, #B91C1C)',
                'linear-gradient(135deg, #0891B2, #0E7490)',
                'linear-gradient(135deg, #4F46E5, #4338CA)'
            ];
            if (!name) return gradients[0];
            let hash = 0;
            for (let i = 0; i < name.length; i++) {
                hash = name.charCodeAt(i) + ((hash << 5) - hash);
            }
            const idx = Math.abs(hash) % gradients.length;
            return gradients[idx];
        }

        function renderInitialsAvatar(container, name, fontSize) {
            if (!container) return;
            const initials = getInitialsFromName(name);
            const gradient = getGradientForName(name);
            container.style.background = gradient;
            container.innerHTML = `<span style="font-size: ${dol}{fontSize}; font-weight: 700; color: #ffffff; line-height: 1; user-select: none;">${dol}{initials}</span>`;
        }

        function applyUserProfileData(data) {
            if (!data) return;
            const profName = data.profileName || 'Singghara User';
            const sNameEl = document.getElementById('sidebarUserName');
            if (sNameEl) {
                sNameEl.textContent = profName;
                sNameEl.title = profName;
                const len = profName.length;
                if (len > 28) {
                    sNameEl.style.fontSize = '0.68rem';
                } else if (len > 20) {
                    sNameEl.style.fontSize = '0.73rem';
                } else if (len > 14) {
                    sNameEl.style.fontSize = '0.78rem';
                } else {
                    sNameEl.style.fontSize = '0.82rem';
                }
            }
            const pNameEl = document.getElementById('profileModalName');
            if (pNameEl) pNameEl.textContent = profName;

            const sAvatar = document.getElementById('sidebarAvatarContainer');
            const mAvatar = document.getElementById('profileModalAvatarContainer');

            const hasCustomImage = !!(data.hasCustomImage || (data.profileAvatarIndex === -1 && (data.profileImageUrl || data.remotePhotoUrl)));
            const primaryImgUrl = data.profileImageUrl || (data.remotePhotoUrl || ('/api/profile/image?t=' + Date.now()));

            if (hasCustomImage) {
                // Tampilkan foto profil tersinkronisasi (Google / Facebook / Foto Kustom)
                const sImg = new Image();
                sImg.className = 'sidebar-avatar-img';
                sImg.alt = profName;
                sImg.onload = function() {
                    if (sAvatar) {
                        sAvatar.style.background = 'transparent';
                        sAvatar.innerHTML = '';
                        sAvatar.appendChild(sImg);
                    }
                };
                sImg.onerror = function() {
                    if (data.remotePhotoUrl && sImg.src !== data.remotePhotoUrl) {
                        sImg.src = data.remotePhotoUrl;
                    } else {
                        renderInitialsAvatar(sAvatar, profName, '1.1rem');
                    }
                };
                sImg.src = primaryImgUrl;

                const mImg = new Image();
                mImg.style.width = '100%';
                mImg.style.height = '100%';
                mImg.style.objectFit = 'cover';
                mImg.style.borderRadius = '50%';
                mImg.alt = profName;
                mImg.onload = function() {
                    if (mAvatar) {
                        mAvatar.style.background = 'transparent';
                        mAvatar.innerHTML = '';
                        mAvatar.appendChild(mImg);
                    }
                };
                mImg.onerror = function() {
                    if (data.remotePhotoUrl && mImg.src !== data.remotePhotoUrl) {
                        mImg.src = data.remotePhotoUrl;
                    } else {
                        renderInitialsAvatar(mAvatar, profName, '2.1rem');
                    }
                };
                mImg.src = primaryImgUrl;
            } else if (data.profileAvatarIndex === -2 || !data.profileAvatarIndex || data.profileAvatarIndex === 0) {
                // Avatar inisial nama seperti di aplikasi Android My Singghara
                renderInitialsAvatar(sAvatar, profName, '1.1rem');
                renderInitialsAvatar(mAvatar, profName, '2.1rem');
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

            // Sembunyikan atau tampilkan tombol Manajemen Akun khusus Admin UID 'UiDpHxawlBQ17mxTUHIXvhxRR3b2'
            const targetAdminUid = 'UiDpHxawlBQ17mxTUHIXvhxRR3b2';
            const isRealAdmin = !!(data && (data.currentUid === targetAdminUid || data.isAdmin === true));
            if (!window.adminAuthStatus) {
                window.adminAuthStatus = {};
            }
            window.adminAuthStatus.isAdmin = isRealAdmin;
            window.adminAuthStatus.currentUid = isRealAdmin ? targetAdminUid : ((data && data.currentUid) || '');
            window.adminAuthStatus.isLoggedIn = !!(data && (data.currentUid || data.profileName));
            window.adminAuthStatus.adminUid = targetAdminUid;

            const btnAdmin = document.getElementById('btnProfileManajemenAkun');
            if (btnAdmin) {
                if (isRealAdmin) {
                    btnAdmin.classList.add('d-flex');
                    btnAdmin.style.display = 'flex';
                } else {
                    btnAdmin.classList.remove('d-flex');
                    btnAdmin.style.display = 'none';
                }
            }
        }

        async function openManajemenAkunFromProfileModal() {
            const targetAdminUid = 'UiDpHxawlBQ17mxTUHIXvhxRR3b2';
            let isRealAdmin = window.adminAuthStatus && window.adminAuthStatus.isAdmin && (window.adminAuthStatus.currentUid === targetAdminUid);
            if (!isRealAdmin && typeof fetchAdminStatus === 'function') {
                const updated = await fetchAdminStatus();
                isRealAdmin = updated && updated.isAdmin && (updated.currentUid === targetAdminUid);
            }
            if (!isRealAdmin) {
                if (typeof showAdminToast === 'function') {
                    showAdminToast('Akses ditolak: Khusus UID Admin UiDpHxawlBQ17mxTUHIXvhxRR3b2', 'bi-shield-slash-fill', '#DC2626');
                } else {
                    alert('Akses ditolak: Khusus UID Admin UiDpHxawlBQ17mxTUHIXvhxRR3b2');
                }
                return;
            }
            setTimeout(() => {
                switchTabByName('manajemen_akun');
            }, 100);
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

        function showProfileModal() {
            switchTab('profil');
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
                    showLoggedOutScreen(false, 'Sesi Web Connect Berhasil Diakhiri');
                }
            });
        }

        // ==========================================
        // WEB CONNECT AUTH & SECURITY ENGINE
        // ==========================================
        let activeAuthToken = sessionStorage.getItem('singghara_auth_token') || '';
        let authPollInterval = null;
        let qrCodeInstance = null;
        let isWebAuthenticated = false;

        function renderAuthQrCode(qrPayload) {
            const container = document.getElementById('authQrCode');
            if (!container) return;
            container.innerHTML = '';
            if (typeof QRCode !== 'undefined' && qrPayload) {
                try {
                    qrCodeInstance = new QRCode(container, {
                        text: qrPayload,
                        width: 170,
                        height: 170,
                        colorDark: '#0f172a',
                        colorLight: '#ffffff',
                        correctLevel: QRCode.CorrectLevel.M
                    });
                } catch(e) {
                    console.warn('QRCode render fallback:', e);
                    container.innerHTML = `<div class="p-3 text-center text-muted small"><i class="bi bi-qr-code fs-1"></i><br><code class="d-block mt-2 text-break">${dol}{escapeHtml(qrPayload)}</code></div>`;
                }
            } else if (qrPayload) {
                container.innerHTML = `<div class="p-3 text-center text-muted small"><i class="bi bi-qr-code fs-1"></i><br><code class="d-block mt-2 text-break">${dol}{escapeHtml(qrPayload)}</code></div>`;
            }
        }

        function showLoggedOutScreen(isRejected = false, customMessage = '') {
            isWebAuthenticated = false;
            sessionStorage.removeItem('singghara_auth_token');
            activeAuthToken = '';

            const overlay = document.getElementById('authOverlay');
            const pendingView = document.getElementById('authPendingView');
            const rejectedView = document.getElementById('authRejectedView');
            const loggedOutView = document.getElementById('authLoggedOutView');

            if (overlay) {
                overlay.classList.remove('d-none');
                overlay.classList.remove('hidden');
            }
            if (pendingView) pendingView.classList.add('d-none');
            if (rejectedView) rejectedView.classList.add('d-none');
            if (loggedOutView) loggedOutView.classList.remove('d-none');

            const badgeText = document.getElementById('authBadgeText');
            const badgeIcon = document.getElementById('authBadgeIcon');
            const badgeStatus = document.getElementById('authBadgeStatus');
            const outBadge = document.getElementById('authLoggedOutStatusBadge');
            const outIconBadge = document.getElementById('authLoggedOutIconBadge');
            const outIcon = document.getElementById('authLoggedOutIcon');
            const outTitle = document.getElementById('authLoggedOutTitle');
            const outDesc = document.getElementById('authLoggedOutDesc');
            const outBoxHeader = document.getElementById('authLoggedOutBoxHeader');
            const outBoxDesc = document.getElementById('authLoggedOutBoxDesc');

            if (isRejected) {
                if (badgeText) badgeText.textContent = 'Akses Ditolak';
                if (badgeIcon) badgeIcon.className = 'bi bi-shield-x me-1 text-danger';
                if (badgeStatus) badgeStatus.style.background = 'rgba(239, 68, 68, 0.15)';

                if (outIconBadge) {
                    outIconBadge.classList.add('is-rejected');
                }
                if (outIcon) {
                    outIcon.className = 'bi bi-shield-x';
                }
                if (outBadge) {
                    outBadge.style.background = 'rgba(239, 68, 68, 0.15)';
                    outBadge.style.color = '#ef4444';
                    outBadge.innerHTML = '<i class="bi bi-shield-x me-1"></i> Akses Ditolak';
                }
                if (outTitle) {
                    outTitle.textContent = 'Akses Web Connect Ditolak';
                    outTitle.style.color = '#ef4444';
                }
                if (outDesc) {
                    outDesc.textContent = customMessage || 'Permintaan akses Singghara Web PC telah ditolak oleh pemilik smartphone. Halaman ini aman dan tidak meminta verifikasi.';
                }
                if (outBoxHeader) {
                    outBoxHeader.style.color = '#ef4444';
                    outBoxHeader.innerHTML = '<i class="bi bi-shield-lock-fill"></i> Proteksi Keamanan Aktif';
                }
                if (outBoxDesc) {
                    outBoxDesc.textContent = 'Akses ke data dan sistem diblokir demi menjaga privasi. Halaman ini tidak lagi meminta verifikasi atau kode PIN. Tekan "Hubungkan Kembali" jika ingin mengajukan izin ulang.';
                }
            } else {
                if (badgeText) badgeText.textContent = 'Sesi Berakhir';
                if (badgeIcon) badgeIcon.className = 'bi bi-box-arrow-right me-1 text-secondary';
                if (badgeStatus) badgeStatus.style.background = 'rgba(15, 118, 110, 0.15)';

                if (outIconBadge) {
                    outIconBadge.classList.remove('is-rejected');
                }
                if (outIcon) {
                    outIcon.className = 'bi bi-box-arrow-right';
                }
                if (outBadge) {
                    outBadge.style.background = 'rgba(15, 118, 110, 0.15)';
                    outBadge.style.color = '#0F766E';
                    outBadge.innerHTML = '<i class="bi bi-check2-circle me-1"></i> Sesi Telah Berakhir';
                }
                if (outTitle) {
                    outTitle.textContent = 'Anda Telah Keluar dari Web Connect';
                    outTitle.style.color = '';
                }
                if (outDesc) {
                    outDesc.textContent = customMessage || 'Sesi akses komputer (PC) telah diakhiri. Sambungan antara browser ini dan smartphone My Singghara telah ditutup dengan aman.';
                }
                if (outBoxHeader) {
                    outBoxHeader.style.color = '#0F766E';
                    outBoxHeader.innerHTML = '<i class="bi bi-shield-check"></i> Privasi & Keamanan Terlindungi';
                }
                if (outBoxDesc) {
                    outBoxDesc.textContent = 'Sistem tidak meminta verifikasi ulang secara otomatis. Anda dapat menutup tab browser ini atau menghubungkan kembali sewaktu-waktu jika ingin melanjutkan pekerjaan.';
                }
            }

            if (authPollInterval) {
                clearInterval(authPollInterval);
                authPollInterval = null;
            }
        }

        async function checkAuthStatus(isManualRetry = false) {
            try {
                const urlParams = new URLSearchParams(window.location.search);
                const queryToken = urlParams.get('token') || '';
                const tokenToCheck = activeAuthToken || queryToken || sessionStorage.getItem('singghara_auth_token') || '';
                const res = await fetch('/api/auth?token=' + encodeURIComponent(tokenToCheck));
                if (!res.ok) return;
                const data = await res.json();

                if (data.token && !activeAuthToken) {
                    activeAuthToken = data.token;
                }

                const overlay = document.getElementById('authOverlay');
                const pendingView = document.getElementById('authPendingView');
                const rejectedView = document.getElementById('authRejectedView');
                const loggedOutView = document.getElementById('authLoggedOutView');
                const pinDisplay = document.getElementById('authPinDisplay');
                const badgeText = document.getElementById('authBadgeText');
                const badgeIcon = document.getElementById('authBadgeIcon');
                const badgeStatus = document.getElementById('authBadgeStatus');

                if (data.authenticated === true) {
                    isWebAuthenticated = true;
                    activeAuthToken = data.token || tokenToCheck || activeAuthToken;
                    sessionStorage.setItem('singghara_auth_token', activeAuthToken);

                    if (overlay) {
                        overlay.classList.add('hidden');
                        setTimeout(() => {
                            overlay.classList.add('d-none');
                        }, 300);
                    }

                    if (authPollInterval) {
                        clearInterval(authPollInterval);
                        authPollInterval = null;
                    }

                    applyUserProfileData(data);
                    loadCurrentTab();

                    if (!window._authHeartbeatStarted) {
                        window._authHeartbeatStarted = true;
                        setInterval(async () => {
                            try {
                                if (!isWebAuthenticated) return;
                                const hRes = await fetch('/api/auth?token=' + encodeURIComponent(activeAuthToken));
                                if (hRes.ok) {
                                    const hData = await hRes.json();
                                    if (hData.logged_out === true || hData.rejected === true || hData.authenticated === false) {
                                        lockWebSession(hData);
                                    }
                                }
                            } catch(e) {}
                        }, 2000);
                    }
                    return;
                }

                if (data.logged_out === true || data.rejected === true || (isWebAuthenticated && data.authenticated === false)) {
                    const isRejected = data.rejected === true;
                    showLoggedOutScreen(isRejected, isRejected ? 'Permintaan akses Singghara Web PC telah ditolak oleh pemilik smartphone.' : 'Sesi akses PC telah diakhiri.');
                    return;
                }

                isWebAuthenticated = false;
                if (overlay) {
                    overlay.classList.remove('d-none');
                    overlay.classList.remove('hidden');
                }
                if (pendingView) pendingView.classList.remove('d-none');
                if (rejectedView) rejectedView.classList.add('d-none');
                if (loggedOutView) loggedOutView.classList.add('d-none');
                if (badgeText) badgeText.textContent = 'Proteksi Web Connect';
                if (badgeIcon) badgeIcon.className = 'bi bi-shield-lock-fill me-1';
                if (badgeStatus) badgeStatus.style.background = '';

                if (pinDisplay && data.pin) {
                    pinDisplay.textContent = data.pin;
                }
                if (data.qrPayload) {
                    renderAuthQrCode(data.qrPayload);
                }

                if (!authPollInterval) {
                    authPollInterval = setInterval(() => checkAuthStatus(false), 1500);
                }
            } catch(e) {
                console.warn('Auth check error:', e);
            }
        }

        async function verifyPinFromWeb() {
            const pinInput = document.getElementById('authPinInput');
            const pinVal = (pinInput ? pinInput.value : '').trim();
            const btn = document.getElementById('authPinVerifyBtn');
            const errorEl = document.getElementById('authPinErrorMsg');
            if (errorEl) errorEl.classList.add('d-none');

            if (!pinVal) {
                if (errorEl) {
                    errorEl.textContent = 'Silakan masukkan 6 digit kode PIN PC.';
                    errorEl.classList.remove('d-none');
                }
                if (pinInput) pinInput.focus();
                return;
            }

            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Verifikasi...';
            }

            try {
                const token = activeAuthToken || sessionStorage.getItem('singghara_auth_token') || '';
                const res = await fetch('/api/auth', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'verify_pin', pin: pinVal, token: token })
                });
                const data = await res.json();
                if (data.authenticated === true || data.success === true) {
                    activeAuthToken = data.token || token;
                    sessionStorage.setItem('singghara_auth_token', activeAuthToken);
                    checkAuthStatus(true);
                } else {
                    if (errorEl) {
                        errorEl.textContent = data.message || 'Kode PIN tidak sesuai! Periksa PIN di aplikasi smartphone.';
                        errorEl.classList.remove('d-none');
                    }
                }
            } catch(err) {
                if (errorEl) {
                    errorEl.textContent = 'Gagal memverifikasi: ' + err.message;
                    errorEl.classList.remove('d-none');
                }
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.innerHTML = '<i class="bi bi-shield-check me-1"></i> Masuk';
                }
            }
        }

        async function requestRetryAuth() {
            const statusEl = document.getElementById('authPollStatusText');
            if (statusEl) statusEl.textContent = 'Meminta izin ulang ke smartphone...';
            try {
                const res = await fetch('/api/auth', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ action: 'request_retry' })
                });
                const data = await res.json();
                const pendingView = document.getElementById('authPendingView');
                const rejectedView = document.getElementById('authRejectedView');
                if (pendingView) pendingView.classList.remove('d-none');
                if (rejectedView) rejectedView.classList.add('d-none');
                if (data.pin) {
                    const pinDisplay = document.getElementById('authPinDisplay');
                    if (pinDisplay) pinDisplay.textContent = data.pin;
                }
                if (data.qrPayload) {
                    renderAuthQrCode(data.qrPayload);
                }
            } catch(e) {
                console.error('Retry request failed:', e);
            }
            if (authPollInterval) clearInterval(authPollInterval);
            authPollInterval = setInterval(() => checkAuthStatus(false), 1500);
        }

        async function requestConnectAgain() {
            const loggedOutView = document.getElementById('authLoggedOutView');
            const pendingView = document.getElementById('authPendingView');
            const rejectedView = document.getElementById('authRejectedView');
            if (loggedOutView) loggedOutView.classList.add('d-none');
            if (rejectedView) rejectedView.classList.add('d-none');
            if (pendingView) pendingView.classList.remove('d-none');
            await requestRetryAuth();
        }

        function lockWebSession(authData) {
            const isRejected = (authData && authData.rejected === true) || false;
            showLoggedOutScreen(isRejected, isRejected ? 'Akses Singghara Web PC telah ditolak oleh pemilik smartphone.' : 'Sesi akses PC telah diakhiri.');
        }

        function initAuth() {
            checkAuthStatus(false);
        }

        // Default desktop sidebar to collapsed state
        if (window.innerWidth > 768) {
            document.body.classList.add('sidebar-collapsed');
        }

        function closeAllSubmenus() {
            document.querySelectorAll('#appSidebar .sidebar-sub-items').forEach(subItem => {
                subItem.style.display = 'none';
                const chevron = document.getElementById('chevron-' + subItem.id);
                if (chevron) {
                    chevron.classList.remove('bi-chevron-down');
                    chevron.classList.add('bi-chevron-right');
                }
            });
        }

        function openMobileSidebar() {
            document.body.classList.add('sidebar-mobile-open');
            document.documentElement.classList.add('sidebar-mobile-locked');
        }

        function closeMobileSidebar() {
            document.body.classList.remove('sidebar-mobile-open');
            document.documentElement.classList.remove('sidebar-mobile-locked');
            closeAllSubmenus();
        }

        function toggleSidebar() {
            if (window.innerWidth <= 768) {
                if (document.body.classList.contains('sidebar-mobile-open')) {
                    closeMobileSidebar();
                } else {
                    openMobileSidebar();
                }
            } else {
                const willCollapse = !document.body.classList.contains('sidebar-collapsed');
                document.body.classList.toggle('sidebar-collapsed');
                if (willCollapse) {
                    closeAllSubmenus();
                }
            }
        }

        function toggleSidebarGroup(grpId) {
            // Jika sidebar sedang collapsed di desktop dan user klik grup, buka/expand sidebar dan tampilkan sub menu
            if (document.body.classList.contains('sidebar-collapsed')) {
                document.body.classList.remove('sidebar-collapsed');
                closeAllSubmenus();
                const targetEl = document.getElementById(grpId);
                if (targetEl) {
                    targetEl.style.display = 'block';
                    const chevron = document.getElementById('chevron-' + grpId);
                    if (chevron) {
                        chevron.classList.remove('bi-chevron-right');
                        chevron.classList.add('bi-chevron-down');
                    }
                }
                return;
            }

            const targetEl = document.getElementById(grpId);
            if (!targetEl) return;

            const isCurrentlyOpen = (targetEl.style.display !== 'none' && targetEl.style.display !== '');

            // Jika sub menu sedang terbuka, tutup sub menu
            if (isCurrentlyOpen) {
                targetEl.style.display = 'none';
                const chevron = document.getElementById('chevron-' + grpId);
                if (chevron) {
                    chevron.classList.remove('bi-chevron-down');
                    chevron.classList.add('bi-chevron-right');
                }
                return;
            }

            // Jika sub menu sedang tertutup, tutup sub menu lainnya (accordion) dan buka sub menu ini
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

            targetEl.style.display = 'block';
            const chevron = document.getElementById('chevron-' + grpId);
            if (chevron) {
                chevron.classList.remove('bi-chevron-right');
                chevron.classList.add('bi-chevron-down');
            }
        }

        // Cegah scroll halaman latar belakang saat sidebar mobile terbuka, kecuali scrolling di dalam sidebar menu
        let touchStartY = 0;
        document.addEventListener('touchstart', function(e) {
            if (document.body.classList.contains('sidebar-mobile-open') && e.touches && e.touches.length > 0) {
                touchStartY = e.touches[0].clientY;
            }
        }, { passive: true });

        document.addEventListener('touchmove', function(e) {
            if (document.body.classList.contains('sidebar-mobile-open')) {
                const sidebar = document.getElementById('appSidebar');
                // Jika sentuhan di luar sidebar (misal pada backdrop, header, atau background), cegah scroll sepenuhnya
                if (!sidebar || !sidebar.contains(e.target)) {
                    e.preventDefault();
                    return;
                }

                // Di dalam sidebar: hanya izinkan scrolling pada .sidebar-menu-scrollable
                const scrollable = sidebar.querySelector('.sidebar-menu-scrollable');
                if (scrollable && scrollable.contains(e.target)) {
                    const isAtTop = scrollable.scrollTop <= 0;
                    const isAtBottom = scrollable.scrollTop + scrollable.clientHeight >= scrollable.scrollHeight - 1;
                    const currentY = (e.touches && e.touches[0]) ? e.touches[0].clientY : touchStartY;
                    const isSwipingDown = currentY > touchStartY;
                    const isSwipingUp = currentY < touchStartY;

                    // Cegah overscroll bouncing agar tidak merembet ke scroll halaman belakang
                    if ((isAtTop && isSwipingDown) || (isAtBottom && isSwipingUp)) {
                        e.preventDefault();
                    }
                } else {
                    // Sentuhan pada header mobile atau footer sidebar (non-scrollable), cegah default scroll
                    e.preventDefault();
                }
            }
        }, { passive: false });

        // Close mobile sidebar on backdrop tap
        document.addEventListener('click', (e) => {
            if (window.innerWidth <= 768 && document.body.classList.contains('sidebar-mobile-open')) {
                const sidebar = document.getElementById('appSidebar');
                const toggleBtn = document.getElementById('toggleSidebarBtn');
                if (sidebar && toggleBtn && !sidebar.contains(e.target) && !toggleBtn.contains(e.target)) {
                    closeMobileSidebar();
                }
            }
        });

        function switchTab(tab, element) {
            currentTab = tab;
            // Deck musik adalah overlay fixed; saat pindah halaman lain ia harus balik ke dock mini tanpa menghentikan audio
            if (tab !== 'musik' && window.musicIsPlayerExpanded && typeof collapseMusicPlayerDeck === 'function') {
                collapseMusicPlayerDeck();
            }
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
                closeMobileSidebar();
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
            else if (currentTab === 'gaji') {
                if (window.loadGajiPage) window.loadGajiPage();
            }
            else if (currentTab === 'arisan_keluarga' || currentTab === 'arisan') {
                if (window.loadArisanKeluarga) window.loadArisanKeluarga();
            }
            else if (currentTab === 'rekap_belanja' || currentTab === 'belanja_pasar') {
                if (window.loadRekapBelanjaPasar) window.loadRekapBelanjaPasar();
            }
            else if (currentTab === 'kalkulator') {
                if (window.loadKalkulator) window.loadKalkulator();
            }
            else if (currentTab === 'my_apps') loadMyApps();
            else if (currentTab === 'quran') loadQuran();
            else if (currentTab === 'pembukuan') loadPembukuan();
            else if (currentTab === 'musik') loadMusik();
            else if (currentTab === 'block') loadBlock();
            else if (currentTab === 'puzzle') loadPuzzle();
            else if (currentTab === 'brick_game') loadBrickGame();
            else if (currentTab === 'spinner') loadSpinner();
            else if (currentTab === 'notes' || currentTab === 'catatan') loadNotes();
            else if (currentTab === 'manajemen_keuangan' || currentTab === 'kasir') loadKasir();
            else if (currentTab === 'shopping' || currentTab === 'daftar_belanja') loadShopping();
            else if (currentTab === 'savings' || currentTab === 'catatan_tabungan' || currentTab === 'tabungan') loadSavings();
            else if (currentTab === 'agenda') loadAgenda();
            else if (currentTab === 'jadwal' || currentTab === 'jadwal_harian') loadJadwal();
            else if (currentTab === 'share') loadShare();
            else if (currentTab === 'transfer_file') loadTransferFile();
            else if (currentTab === 'handy_talky' || currentTab === 'ht') {
                if (window.loadHandyTalky) window.loadHandyTalky();
            }
            else if (currentTab === 'telegram_ai' || currentTab === 'telegram' || currentTab === 'tg') {
                if (window.loadTelegramAi) window.loadTelegramAi();
            }
            else if (currentTab === 'manajemen_akun' || currentTab === 'admin') loadManajemenAkun();
            else if (currentTab === 'pemutar_video' || currentTab === 'video') {
                if (window.loadPemutarVideo) window.loadPemutarVideo();
            }
            else if (currentTab === 'mindmap' || currentTab === 'mind_map') {
                if (window.loadMindMapPage) window.loadMindMapPage();
            }
            else if (currentTab === 'profil' || currentTab === 'profile') {
                if (window.loadProfile) window.loadProfile();
            }
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

        function showCustomToast(message, durationMs) {
            let holder = document.getElementById('globalToastHolder');
            if (!holder) {
                holder = document.createElement('div');
                holder.id = 'globalToastHolder';
                holder.style.cssText = 'position:fixed;top:18px;right:18px;z-index:2000;display:flex;flex-direction:column;gap:8px;align-items:flex-end;pointer-events:none;';
                document.body.appendChild(holder);
            }
            const toast = document.createElement('div');
            toast.style.cssText = 'max-width:340px;background:rgba(17,24,39,0.94);color:#fff;border:1px solid rgba(13,110,253,0.45);border-radius:12px;padding:10px 14px;font-size:13px;font-weight:600;box-shadow:0 8px 24px rgba(0,0,0,0.28);opacity:0;transform:translateY(-6px);transition:opacity 0.18s ease,transform 0.18s ease;';
            toast.textContent = message === null || message === undefined ? '' : String(message);
            holder.appendChild(toast);
            requestAnimationFrame(() => {
                toast.style.opacity = '1';
                toast.style.transform = 'translateY(0)';
            });
            setTimeout(() => {
                toast.style.opacity = '0';
                setTimeout(() => toast.remove(), 220);
            }, durationMs || 2600);
        }

        function copyToClipboard(text) {
            if (!navigator.clipboard || !navigator.clipboard.writeText) {
                showCustomToast('Browser tidak mengizinkan akses clipboard.');
                return;
            }
            navigator.clipboard.writeText(String(text || '')).then(() => {
                showCustomToast('Teks berhasil disalin ke clipboard.');
            }).catch(() => {
                showCustomToast('Gagal menyalin teks ke clipboard.');
            });
        }

        function showCustomModal(bodyHtml, titleText) {
            const existing = document.getElementById('genericCustomModal');
            if (existing) existing.remove();
            document.body.insertAdjacentHTML('beforeend', `
                <div class="modal fade" id="genericCustomModal" tabindex="-1" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable" style="max-width: 580px;">
                        <div class="modal-content card-custom p-3" style="border-radius: 22px;">
                            <div class="modal-header border-bottom-0 pb-1">
                                <h5 class="modal-title fw-bold text-main" style="font-size: 16px;">${dol}{escapeHtml(titleText || 'Detail')}</h5>
                                <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                            </div>
                            <div class="modal-body pt-2">${dol}{bodyHtml}</div>
                        </div>
                    </div>
                </div>
            `);
            const modalEl = document.getElementById('genericCustomModal');
            modalEl.addEventListener('hidden.bs.modal', function () { modalEl.remove(); }, { once: true });
            new bootstrap.Modal(modalEl).show();
        }

        function closeCustomModal() {
            const modalEl = document.getElementById('genericCustomModal');
            if (!modalEl) return;
            const instance = bootstrap.Modal.getInstance(modalEl);
            if (instance) instance.hide();
            else modalEl.remove();
        }

        function escapeJs(text) {
            if (!text) return '';
            return text.toString().replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n');
        }

        function submitModalForm() {
            if (window.submitAction) window.submitAction();
        }

        // ==========================================
        // --- 0. DASHBOARD UTAMA (OVERVIEW & STATS) ---
        // ==========================================
        ${DashboardWebScript.getScript(dol)}

        // ==========================================
        // --- MY APLIKASI HUB & SEMUA APLIKASI ---
        // ==========================================
        function loadMyApps() {
            const apps = [
                { id: 'rekap_belanja', title: "Rekap Belanja Pasar", desc: "Daftar belanja pasar harian/mingguan dalam satuan kg, pcs, ikat, liter, dll. dengan checklist, estimasi harga, dan rekap per satuan.", icon: "bi-basket3-fill", color: "#10B981", badge: "Belanja Pasar" },
                { id: 'kalkulator', title: "Kalkulator", desc: "Kalkulator ilmiah sama persis dengan aplikasi My Singghara: mode biasa/ilmiah, SHIFT, memori, statistik, trigonometri, pangkat, akar, faktorial, dan bentuk pecahan.", icon: "bi-calculator", color: "#0EA5E9", badge: "Kalkulator Ilmiah" },
                { id: 'block', title: "Block Puzzle", desc: "Game susun balok 8x8 asah otak seru dengan combo eliminasi, power-up palu/bom/refresh, dan high score.", icon: "bi-grid-fill", color: "#2C5E6A", badge: "Game Hiburan" },
                { id: 'brick_game', title: "Brick Game", desc: "Game retro arcade klasik LCD 9999-in-1 dengan 4 mode seru (Tetris, Mobil Balap, Ular Snake, Tembak Tank) dan kontrol gimbot.", icon: "bi-controller", color: "#306230", badge: "Game Retro" },
                { id: 'quran', title: "Al-Qur'an", desc: "Kitab suci 114 Surah lengkap dengan teks Arab indah, terjemahan Indonesia, dan audio murattal.", icon: "bi-journal-bookmark-fill", color: "#00796B", badge: "Ibadah & Spiritual" },
                { id: 'pembukuan', title: "Pembukuan", desc: "Kalkulator modal, estimasi pendapatan harian/bulanan, dan pengeluaran operasional usaha.", icon: "bi-graph-up-arrow", color: "#E65100", badge: "Manajemen Usaha" },
                { id: 'jurnal_umum', title: "Laporan Keuangan", desc: "Siklus akuntansi lengkap: Jurnal Umum, Buku Besar, Neraca Saldo, Penyesuaian, Laba Rugi, hingga Jurnal Penutup.", icon: "bi-file-earmark-spreadsheet", color: "#00838F", badge: "Laporan Keuangan" },
                { id: 'musik', title: "Musik", desc: "Pemutar file musik audio MP3/WAV/AAC lokal dengan kontrol pemutaran lengkap dan daftar putar.", icon: "bi-music-note-beamed", color: "#E91E63", badge: "Pemutar Audio" },
                { id: 'pemutar_video', title: "Pemutar Video", desc: "Pustaka video offline di browser PC dengan pencarian, urutan, tampilan grid/daftar, pemutar layar penuh, kecepatan, rasio layar, dan unduh.", icon: "bi-camera-reels", color: "#0E7490", badge: "Pemutar Video" },
                { id: 'mindmap', title: "MindMap AI", desc: "Peta konsep bertingkat dari ide teks, tersimpan di perangkat dengan tampilan visual dan outline, 100% offline.", icon: "bi-diagram-3-fill", color: "#0288D1", badge: "Peta Konsep" },
                { id: 'spinner', title: "Spinner", desc: "Roda putar keberuntungan serbaguna untuk undian, arisan, doorprize, atau pembagian kelompok.", icon: "bi-arrow-repeat", color: "#8338EC", badge: "Undian Acak" },
                { id: 'kasir', title: "Kasir", desc: "Sistem kasir Point of Sale (POS) cepat dengan pemindai barcode kamera, manajemen stok, dan struk belanja.", icon: "bi-shop", color: "#00897B", badge: "Point of Sale" },
                { id: 'shopping', title: "Daftar Belanja", desc: "Daftar belanja kebutuhan harian dan pencatatan riwayat transaksi belanja.", icon: "bi-cart3", color: "#3949AB", badge: "Manajemen Belanja" },
                { id: 'savings', title: "Catatan Tabungan", desc: "Tracking target tabungan, impian finansial, dan riwayat setoran/penarikan dana.", icon: "bi-wallet2", color: "#1E88E5", badge: "Tabungan" },
                { id: 'notes', title: "Catatan", desc: "Catatan harian, ide penting, dan dokumen teks pribadi.", icon: "bi-journal-text", color: "#43A047", badge: "Produktivitas" },
                { id: 'jadwal', title: "Jadwal Harian", desc: "Agenda aktivitas, pengingat jadwal, dan rutinitas harian terstruktur.", icon: "bi-clock-history", color: "#FB8C00", badge: "Perencana Waktu" },
                { id: 'share', title: "Share Cloud", desc: "Berbagi dan sinkronisasi file cloud terpusat via Google Drive terintegrasi.", icon: "bi-share", color: "#3674B5", badge: "Cloud Share" },
                { id: 'transfer_file', title: "Transfer File", desc: "Transfer berkas cepat 100% offline antara aplikasi My Singghara dan browser PC tanpa kuota internet.", icon: "bi-arrow-left-right", color: "#0284C7", badge: "100% Offline" },
                { id: 'profil', title: "Profil Pengguna", desc: "Kelola profil akun, keamanan & App Lock, backup data, GPS, status Membership VIP, dan informasi aplikasi.", icon: "bi-person-circle", color: "#6366F1", badge: "Profil & Akun" }
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
        // --- 1. AL-QUR'AN (114 SURAH, JUZ, BOOKMARKS, TAJWID & READER) ---
        // ==========================================
        ${QuranWebScript.getScript(dol)}

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
        ${GajiWebScript.getScript(dol)}
        ${ArisanWebScript.getScript(dol)}
        ${RekapBelanjaPasarWebScript.getScript(dol)}
        ${ManajemenKeuanganWebScript.getScript(dol)}
        ${NoteWebScript.getScript(dol)}
        ${ShareWebScript.getScript(dol)}
        ${TransferFileWebScript.getScript(dol)}
        ${HandyTalkyWebScript.getScript(dol)}
        ${TelegramAiWebScript.getScript(dol)}
        ${ManajemenAkunWebScript.getScript(dol)}
        ${SpinnerWebScript.getScript(dol)}
        ${MusikWebScript.getScript(dol)}
        ${VideoWebScript.getScript(dol)}
        ${MindMapWebScript.getScript(dol)}
        ${ProfilWebScript.getScript(dol)}
        ${KalkulatorWebScript.getScript(dol)}

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
                    <!-- MAIN RESPONSIVE 3-COLUMN / STACKED LAYOUT: 
                         COLUMN 1: STATS & BOOSTERS (HORIZONTAL GRID ON MOBILE, COMPACT VERTICAL CARD ON DESKTOP)
                         COLUMN 2: 8x8 BOARD & GAMEPLAY AREA
                         COLUMN 3: BLOCKS TRAY (HORIZONTAL ROW ON MOBILE, COMPACT VERTICAL COLUMN ON DESKTOP)
                    -->
                    <div class="row g-2 g-md-3 justify-content-center align-items-stretch">
                        <!-- COLUMN 1: STATS & BOOSTERS CARD -->
                        <div class="col-12 col-md-3 puzzle-stats-col d-flex flex-column">
                            <div class="puzzle-stats-card p-2.5 d-flex flex-column justify-content-between h-100" style="border-radius: 16px;">
                                <!-- TOP SECTION: HEADER & STATS -->
                                <div>
                                    <!-- HEADER BADGE -->
                                    <div class="d-flex align-items-center justify-content-between pb-1 mb-2 border-bottom border-white border-opacity-10">
                                        <span class="fw-bold text-warning d-flex align-items-center gap-1" style="font-size: 10px;">
                                            <i class="bi bi-bar-chart-fill"></i> STATISTIK
                                        </span>
                                        <span class="badge bg-dark bg-opacity-50 text-white-50 px-1 py-0.5" style="font-size: 8px;">8x8 JEWEL</span>
                                    </div>

                                    <!-- 3 STATS BOXES -->
                                    <div class="puzzle-stats-grid mb-2">
                                        <!-- 1. REKOR TERTINGGI -->
                                        <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(251, 192, 45, 0.3);">
                                            <div class="d-flex align-items-center gap-1 mb-0.5">
                                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 20px; height: 20px; background: rgba(251, 192, 45, 0.18); border: 1px solid #FBC02D;">
                                                    <i class="bi bi-trophy-fill text-warning" style="font-size: 9.5px;"></i>
                                                </div>
                                                <small class="text-white-50 text-truncate" style="font-size: 8.5px; font-weight: 700; text-transform: uppercase;">Rekor</small>
                                            </div>
                                            <h4 class="fw-black text-warning mb-0 text-end pe-1" id="puzzleHighScoreText" style="font-size: 17px; letter-spacing: -0.5px; line-height: 1.2;">${dol}{window.puzzleHighScore}</h4>
                                        </div>

                                        <!-- 2. SKOR SEKARANG -->
                                        <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(0, 188, 212, 0.3);">
                                            <div class="d-flex align-items-center gap-1 mb-0.5">
                                                <div class="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0" style="width: 20px; height: 20px; background: rgba(0, 188, 212, 0.18); border: 1px solid #00BCD4;">
                                                    <i class="bi bi-lightning-charge-fill" style="color: #81D4FA; font-size: 9.5px;"></i>
                                                </div>
                                                <small class="text-white-50 text-truncate" style="font-size: 8.5px; font-weight: 700; text-transform: uppercase;">Skor</small>
                                            </div>
                                            <h4 class="fw-black mb-0 text-end pe-1" style="color: #81D4FA; font-size: 17px; letter-spacing: -0.5px; line-height: 1.2;" id="puzzleScoreText">${dol}{window.puzzleScore}</h4>
                                        </div>

                                        <!-- 3. PROGRESS TARGET -->
                                        <div class="p-1.5 px-2 rounded-2" style="background: rgba(0,0,0,0.22); border: 1px solid rgba(255, 255, 255, 0.1);">
                                            <div class="d-flex justify-content-between align-items-center mb-1">
                                                <small class="text-white-50 fw-bold text-truncate" style="font-size: 8px; text-transform: uppercase;">Target</small>
                                                <span class="badge" id="puzzleProgressPercentText" style="background: rgba(0, 188, 212, 0.2); color: #81D4FA; border: 1px solid #00BCD4; font-size: 8px; padding: 1px 3.5px;">${dol}{milestone.percent}%</span>
                                            </div>
                                            <div class="puzzle-progress-bar mb-1" style="height: 4.5px; background: rgba(0,0,0,0.45); border-radius: 4px; overflow: hidden; border: 1px solid rgba(255,255,255,0.08);">
                                                <div class="puzzle-progress-fill" id="puzzleProgressFillBar" style="width: ${dol}{milestone.percent}%; height: 100%; background: linear-gradient(90deg, #00BCD4, #4CAF50); border-radius: 4px; transition: width 0.3s ease;"></div>
                                            </div>
                                            <div class="d-flex justify-content-between text-white-50" style="font-size: 8px;">
                                                <span id="puzzleCurrentScoreStatText">${dol}{window.puzzleScore}</span>
                                                <span id="puzzleTargetScoreText" style="color: #81D4FA; font-weight: 600;">Tgt: ${dol}{milestone.nextMilestone}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <!-- BOTTOM SECTION: 4 BOOSTER ITEM / POWER-UPS -->
                                <div class="pt-1.5 border-top border-white border-opacity-10">
                                    <div class="d-flex align-items-center justify-content-between mb-1 px-0.5">
                                        <small class="text-white-50 fw-bold" style="font-size: 8px; text-transform: uppercase; letter-spacing: 0.5px;">Booster Power-Up</small>
                                        <span class="badge bg-dark bg-opacity-50 text-white-50" style="font-size: 7px;">Item</span>
                                    </div>
                                    <div class="puzzle-booster-grid">
                                        <button class="btn puzzle-booster-btn w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleRotate()" title="Putar 90Ã‚Â° Balok yang Dipilih">
                                            <i class="bi bi-arrow-repeat text-warning" style="font-size: 13px;"></i>
                                            <span style="font-size: 8px; margin-top: 1px;">Putar</span>
                                            <span class="badge rounded-pill bg-warning text-dark position-absolute top-0 end-0 m-0.5" id="puzzleBoosterRotateCount" style="font-size: 7px; padding: 1px 3px;">${dol}{window.puzzleBoosters.rotate}</span>
                                        </button>
                                        <button class="btn puzzle-booster-btn ${dol}{window.puzzleActiveMode === 'singleClear' ? 'active' : ''} w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleSingleClear()" title="Hapus 1 Balok di Kotak Papan">
                                            <i class="bi bi-eraser-fill text-info" style="font-size: 13px;"></i>
                                            <span style="font-size: 8px; margin-top: 1px;">Hapus 1x1</span>
                                            <span class="badge rounded-pill bg-info text-dark position-absolute top-0 end-0 m-0.5" id="puzzleBoosterClearCount" style="font-size: 7px; padding: 1px 3px;">${dol}{window.puzzleBoosters.clear}</span>
                                        </button>
                                        <button class="btn puzzle-booster-btn ${dol}{window.puzzleActiveMode === 'hammer' ? 'active' : ''} w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleHammer()" title="Palu: Hancurkan Area 3x3">
                                            <i class="bi bi-hammer text-danger" style="font-size: 13px;"></i>
                                            <span style="font-size: 8px; margin-top: 1px;">Palu 3x3</span>
                                            <span class="badge rounded-pill bg-danger text-white position-absolute top-0 end-0 m-0.5" id="puzzleBoosterHammerCount" style="font-size: 7px; padding: 1px 3px;">${dol}{window.puzzleBoosters.hammer}</span>
                                        </button>
                                        <button class="btn puzzle-booster-btn w-100 p-1.5 d-flex flex-column align-items-center justify-content-center position-relative" onclick="usePuzzleRefresh()" title="Acak Ulang 3 Pilihan Balok">
                                            <i class="bi bi-shuffle text-success" style="font-size: 13px;"></i>
                                            <span style="font-size: 8px; margin-top: 1px;">Acak</span>
                                            <span class="badge rounded-pill bg-success text-white position-absolute top-0 end-0 m-0.5" id="puzzleBoosterRefreshCount" style="font-size: 7px; padding: 1px 3px;">${dol}{window.puzzleBoosters.refresh}</span>
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- CENTER COLUMN: PUZZLE BOARD & GAMEPLAY AREA -->
                        <div class="col-12 col-md-6 puzzle-board-col d-flex flex-column align-items-center">
                            <div class="card card-custom p-2 p-md-3 position-relative h-100 w-100 d-flex flex-column justify-content-center align-items-center" style="border-radius: 18px;">
                                
                                <!-- CENTER TITLE & CONTROLS (DIATAS PAPAN GAME) -->
                                <div class="d-flex align-items-center justify-content-between w-100 mb-2 px-1">
                                    <div class="d-none d-sm-block" style="width: 76px;"></div>
                                    <div class="text-center flex-grow-1">
                                        <h4 class="fw-bold mb-0 text-success d-flex align-items-center justify-content-center gap-1.5" style="font-size: clamp(16px, 4vw, 20px);">
                                            <i class="bi bi-grid-fill" style="color: #4CAF50;"></i> Block Puzzle Classic
                                        </h4>
                                        <small class="text-white-50" style="font-size: 11px;">Susun balok 8x8, eliminasi baris &amp; kolom!</small>
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
                                    <div class="alert alert-info py-1 px-2.5 mb-2 d-flex align-items-center justify-content-between rounded-pill small w-100" style="font-size: 11px;">
                                        <span><i class="bi bi-eraser-fill me-1 fs-6"></i> <strong>Hapus 1x1:</strong> Klik 1 balok pada kotak papan!</span>
                                        <button class="btn btn-sm btn-link text-dark p-0 fw-bold" onclick="cancelPuzzlePowerup()">Batal</button>
                                    </div>
                                ` : ''}

                                ${dol}{window.puzzleActiveMode === 'hammer' ? `
                                    <div class="alert alert-warning py-1 px-2.5 mb-2 d-flex align-items-center justify-content-between rounded-pill small w-100" style="font-size: 11px;">
                                        <span><i class="bi bi-hammer me-1 fs-6"></i> <strong>Palu 3x3:</strong> Klik kotak di papan untuk hancurkan 3x3!</span>
                                        <button class="btn btn-sm btn-link text-dark p-0 fw-bold" onclick="cancelPuzzlePowerup()">Batal</button>
                                    </div>
                                ` : ''}

                                <!-- 8x8 BOARD GRID (Identical Deep Slate Jewel Theme) -->
                                <div class="puzzle-board-container mx-auto" 
                                     id="puzzleBoardContainer">
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
                                     onclick="handlePuzzleCellClick(${dol}{r}, ${dol}{c})">
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

                        <!-- RIGHT COLUMN: BLOCKS TRAY (KARTU BLOCK) -->
                        <div class="col-12 col-md-3 puzzle-tray-col d-flex flex-column">
                            <div class="puzzle-tray-container p-2.5 d-flex flex-column justify-content-between h-100 w-100" id="puzzleTrayArea" style="border-radius: 16px;">
                                <div class="d-flex align-items-center justify-content-between pb-1 border-bottom border-white border-opacity-10 w-100 px-1">
                                    <span class="fw-bold text-info d-flex align-items-center gap-1" style="font-size: 10px;">
                                        <i class="bi bi-boxes"></i> PILIHAN BALOK
                                    </span>
                                    <span class="badge bg-dark bg-opacity-50 text-white-50 px-1 py-0.5" style="font-size: 8px;">3 OPSI</span>
                                </div>

                                <div class="puzzle-pieces-tray my-2" id="puzzlePiecesTray">
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
                showPuzzleComboToast('Ã°Å¸Âªâ€ž Balok Dihapus!');
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
                showPuzzleComboToast(`Ã°Å¸â€Â¨ Palu 3x3! (+${dol}{cleared * 20})`);
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
                if (totalLines >= 4) comboMsg = 'Ã°Å¸â€Â¥ UNBELIEVABLE! +' + lineScore;
                else if (totalLines === 3) comboMsg = 'Ã¢Å“Â¨ AMAZING! +' + lineScore;
                else if (totalLines === 2) comboMsg = 'Ã¢Å¡Â¡ GREAT COMBO! +' + lineScore;
                else if (window.puzzleComboStreak >= 2) comboMsg = `Ã°Å¸Å½â€° STREAK x${dol}{window.puzzleComboStreak}! +${dol}{lineScore}`;
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
                    setTimeout(() => alert('Ã°Å¸Å½â€° Selamat! Anda berhasil menyelesaikan teka-teki geser dalam ' + window.slidePuzzleMoves + ' langkah!'), 100);
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
        window.brickSnakePendingDir = null;
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
                window.brickSnakePendingDir = null;
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
                ctx.fillText('Ã¢â€”â€ž  GAME ' + currentSelected.id + ' / 5  Ã¢â€“Âº', 120, 115);

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
                ctx.fillText('Ã¢â€ â€˜ / Ã¢â€ â€œ  ATAU  Ã¢â€”â€ž / Ã¢â€“Âº', 120, 412);
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
                else if (window.brickGameType === 4) queueSnakeDir(0, -1);
                else if (window.brickGameType === 5) window.brickBreakoutPaddleCol = Math.max(1, window.brickBreakoutPaddleCol - 1);
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
                else if (window.brickGameType === 4) queueSnakeDir(0, 1);
                else if (window.brickGameType === 5) window.brickBreakoutPaddleCol = Math.min(8, window.brickBreakoutPaddleCol + 1);
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
                queueSnakeDir(-1, 0);
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
                queueSnakeDir(1, 0);
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

            // Baris negatif dibiarkan (area spawn di atas papan, sama seperti
            // versi native). Normalisasi lama menggeser potongan ke bawah satu
            // baris saat diputar di baris 0, sehingga balok terlihat turun sendiri.
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

            // Bullet vs Enemy Collision. Hanya satu peluru yang dipakai per
            // musuh supaya peluru lain tidak ikut hilang percuma.
            window.brickShooterEnemies = window.brickShooterEnemies.filter(enemy => {
                const hitIdx = window.brickShooterBullets.findIndex(bullet =>
                    bullet.c === enemy.c && (bullet.r === enemy.r || bullet.r === enemy.r - 1));
                if (hitIdx !== -1) {
                    window.brickShooterBullets.splice(hitIdx, 1);
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

            // Check collision with player car (player is at row 16..19). Badan
            // mobil selebar 3 sel, jadi tabrakan terjadi saat jarak kolom < 3.
            const playerCol = getLaneCol(window.brickRacingLane);
            for (let obs of window.brickRacingObstacles) {
                const obsCol = getLaneCol(obs.lane);
                const overlapCol = Math.abs(playerCol - obsCol) < 3;
                if (overlapCol && obs.r + 3 >= 16 && obs.r <= 19) {
                    playBrickBeep('crash');
                    handleBrickGameOver();
                    return;
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

            // Spawn new obstacle, tetapi jangan menumpuk di lane yang masih dekat
            if (window.brickRacingObstacles.length === 0 || (window.brickRacingObstacles[window.brickRacingObstacles.length - 1].r >= 8)) {
                const busyCols = window.brickRacingObstacles.filter(o => o.r < 10).map(o => getLaneCol(o.lane));
                const freeLanes = [0, 1, 2].filter(l =>
                    busyCols.every(bc => Math.abs(getLaneCol(l) - bc) >= 3));
                if (freeLanes.length > 0 && Math.random() < 0.6) {
                    const newLane = freeLanes[Math.floor(Math.random() * freeLanes.length)];
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

        // Arah masuk antrean satu langkah dan dibandingkan dengan arah yang
        // sedang berjalan, sehingga dua tombol dalam satu tick tidak bisa
        // membuat ular berbalik 180 derajat ke tubuhnya sendiri.
        function queueSnakeDir(r, c) {
            if (window.brickGameOver || window.brickIsPaused) return;
            const current = window.brickSnakeDir;
            if (current.r === -r && current.c === -c) return;
            if (current.r === r && current.c === c) return;
            window.brickSnakePendingDir = { r, c };
        }

        function updateSnake() {
            window.brickSnakeTicks++;
            const speedLimit = Math.max(2, 8 - window.brickLevel);
            if (window.brickSnakeTicks % speedLimit !== 0) return;

            if (window.brickSnakePendingDir) {
                window.brickSnakeDir = window.brickSnakePendingDir;
                window.brickSnakePendingDir = null;
            }

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

            // Self collision. Sel ekor dikecualikan karena akan bergeser.
            const bodyExceptTail = window.brickSnakeBody.slice(0, Math.max(0, window.brickSnakeBody.length - 1));
            if (bodyExceptTail.some(b => b.r === nextH.r && b.c === nextH.c)) {
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

            const prevY = window.brickBallY;
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

            // Paddle bounce (paddle is drawn on row 19). Deteksi perlintasan
            // permukaan atas paddle supaya bola cepat tidak menembusnya.
            if (window.brickBallVy > 0 && prevY < 19.0 && window.brickBallY >= 19.0) {
                const ballCol = Math.max(0, Math.min(9, Math.floor(window.brickBallX)));
                const padL = window.brickBreakoutPaddleCol - 1;
                const padR = window.brickBreakoutPaddleCol + 1;
                if (ballCol >= padL && ballCol <= padR) {
                    window.brickBallY = 19.0;
                    window.brickBallVy = -Math.abs(window.brickBallVy);

                    const offset = Math.max(-2, Math.min(2, window.brickBallX - window.brickBreakoutPaddleCol));
                    window.brickBallVx = Math.max(-0.65, Math.min(0.65, window.brickBallVx + offset * 0.22));
                    playBrickBeep('hit');
                }
            }

            // Brick Hit Detection (Rows 1..4)
            const padCol = Math.max(0, Math.min(9, Math.floor(window.brickBallX)));
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
                        window.brickBallVx = (window.brickBallVx * 1.15);
                        window.brickBallVx = Math.max(-0.75, Math.min(0.75, window.brickBallVx));
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
                window.brickBallY = 20.0;
                window.brickLives = Math.max(0, window.brickLives - 1);
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
        // --- AGENDA KEGIATAN ---
        // ==========================================
        async function loadAgenda() {
            try {
                const fetchRes = await fetchApiWithCache('/api/agenda', 'agenda');
                const data = fetchRes.data || [];
                let html = `
                    <div class="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-2">
                        <h4 class="fw-bold mb-0"><i class="bi bi-calendar-event me-2 text-warning"></i> Agenda Kegiatan</h4>
                        <button class="btn btn-brand" onclick="openAddAgendaModal()"><i class="bi bi-plus-lg me-1"></i> Tambah Agenda</button>
                    </div>
                    <div class="row g-3">
                `;
                if (data.length === 0) {
                    html += `<div class="col-12 text-center py-5 text-secondary">Belum ada agenda terdaftar.</div>`;
                } else {
                    data.forEach(item => {
                        html += `
                            <div class="col-12 col-md-6">
                                <div class="card card-custom h-100 p-3">
                                    <div class="d-flex justify-content-between align-items-center mb-2">
                                        <span class="badge bg-warning text-dark">${dol}{escapeHtml(item.date || '')}</span>
                                        <button class="btn btn-sm text-danger p-0" onclick="deleteAgendaItem(${dol}{item.id})"><i class="bi bi-trash"></i></button>
                                    </div>
                                    <h5 class="fw-bold mb-1">${dol}{escapeHtml(item.title)}</h5>
                                    <p class="text-muted small mb-0">${dol}{escapeHtml(item.description || '')}</p>
                                </div>
                            </div>
                        `;
                    });
                }
                html += `</div>`;
                const container = document.getElementById('tab-content') || document.getElementById('main-content');
                if (container) container.innerHTML = html;
            } catch (e) {
                const container = document.getElementById('tab-content') || document.getElementById('main-content');
                if (container) container.innerHTML = `<div class="alert alert-danger">Gagal memuat agenda: ${dol}{escapeHtml(e.message)}</div>`;
            }
        }

        function openAddAgendaModal() {
            const title = prompt('Judul Agenda:');
            if (title && title.trim()) {
                const date = prompt('Tanggal (YYYY-MM-DD):', new Date().toISOString().split('T')[0]);
                const desc = prompt('Deskripsi / Lokasi:', '');
                fetch('/api/agenda', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title: title.trim(), date: date || '', description: desc || '' })
                }).then(() => {
                    invalidateApiCache('agenda');
                    loadAgenda();
                });
            }
        }

        async function deleteAgendaItem(id) {
            await fetch(`/api/agenda?id=${dol}{id}`, { method: 'DELETE' });
            invalidateApiCache('agenda');
            loadAgenda();
        }

        // ==========================================
        // --- NOTIFIKASI & CLOUD MESSAGING (100% OFFLINE) ---
        // ==========================================
        window.allNotificationsData = null;
        window.currentNotifTab = 'pesan';
        window.currentHolidayCategory = 'Semua';

        async function loadNotifications(silent = false) {
            try {
                let data = null;
                try {
                    const res = await fetch('/api/notifications');
                    if (res.ok) {
                        data = await res.json();
                        localStorage.setItem('singghara_notifications_cache', JSON.stringify(data));
                    }
                } catch(netErr) {
                    console.warn('Gagal fetch /api/notifications langsung, memuat cache offline...', netErr);
                }

                if (!data) {
                    const cached = localStorage.getItem('singghara_notifications_cache');
                    if (cached) {
                        data = JSON.parse(cached);
                    }
                }

                if (!data) return;
                window.allNotificationsData = data;
                updateNotificationNavbarBadge(data);

                const modalEl = document.getElementById('notificationModal');
                if (modalEl && modalEl.classList.contains('show')) {
                    renderNotificationModal(data);
                }
            } catch(e) {
                console.error('Error loadNotifications:', e);
            }
        }

        function updateNotificationNavbarBadge(data) {
            const btn = document.getElementById('navNotificationBtn');
            const icon = document.getElementById('navNotificationIcon');
            const badge = document.getElementById('navNotificationBadge');
            if (!btn || !icon || !badge) return;

            const total = data.totalNotificationsCount || 0;
            const unreadFcm = data.unreadFcmCount || 0;
            const hasHoliday = !!data.todayHolidayName;
            const hasOngoing = !!data.ongoingJadwal && data.ongoingJadwal !== null && typeof data.ongoingJadwal === 'object';
            const hasObservance = !!data.todayObservanceName;
            const hasAgenda = Array.isArray(data.activeAgendas) && data.activeAgendas.length > 0;

            if (total > 0) {
                badge.style.display = 'inline-block';
                badge.textContent = total > 99 ? '99+' : total;
                icon.className = 'bi bi-bell-fill bell-active';

                let color = '#EF4444';
                if (unreadFcm > 0) {
                    color = '#EF4444';
                } else if (hasHoliday) {
                    color = '#DC2626';
                } else if (hasOngoing) {
                    color = '#0284C7';
                } else if (hasObservance) {
                    color = '#0F766E';
                } else if (hasAgenda) {
                    color = '#EF4444';
                }

                badge.style.backgroundColor = color;
                icon.style.color = color;
                btn.style.borderColor = color;
            } else {
                badge.style.display = 'none';
                icon.className = 'bi bi-bell';
                icon.style.color = '#64748B';
                btn.style.borderColor = 'var(--border-color, rgba(148,163,184,0.2))';
            }
        }

        function openNotificationModal() {
            if (!window.allNotificationsData) {
                loadNotifications().then(() => {
                    doOpenNotificationModal();
                });
            } else {
                doOpenNotificationModal();
                loadNotifications(true);
            }
        }

        function doOpenNotificationModal() {
            const data = window.allNotificationsData || {};
            const unreadFcm = data.unreadFcmCount || 0;
            
            if (unreadFcm > 0) {
                switchNotifTab('pesan');
            } else if (data.todayHolidayName || data.todayObservanceName || data.ongoingJadwal || (data.activeAgendas && data.activeAgendas.length > 0)) {
                switchNotifTab('agenda');
            } else {
                switchNotifTab('pesan');
            }

            renderNotificationModal(data);
            const modalEl = document.getElementById('notificationModal');
            if (modalEl) {
                const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
                modal.show();
            }
        }

        function switchNotifTab(tabName) {
            window.currentNotifTab = tabName;
            const tabPesanBtn = document.getElementById('notifTabPesanBtn');
            const tabAgendaBtn = document.getElementById('notifTabAgendaBtn');
            const panePesan = document.getElementById('notifPanePesan');
            const paneAgenda = document.getElementById('notifPaneAgenda');
            const titleEl = document.getElementById('notifModalTitle');
            const headerIcon = document.getElementById('notifModalHeaderIcon');
            const headerIconContainer = document.getElementById('notifModalHeaderIconContainer');

            if (tabName === 'pesan') {
                if (tabPesanBtn) { tabPesanBtn.classList.add('active'); }
                if (tabAgendaBtn) { tabAgendaBtn.classList.remove('active'); }
                if (panePesan) panePesan.style.display = 'block';
                if (paneAgenda) paneAgenda.style.display = 'none';
                if (titleEl) titleEl.textContent = 'Pesan Cloud Messaging';
                if (headerIcon) headerIcon.className = 'bi bi-cloud-check-fill fs-5';
                if (headerIconContainer) {
                    headerIconContainer.style.background = 'rgba(14, 165, 233, 0.12)';
                    headerIconContainer.style.color = '#0284c7';
                }
            } else {
                if (tabAgendaBtn) { tabAgendaBtn.classList.add('active'); }
                if (tabPesanBtn) { tabPesanBtn.classList.remove('active'); }
                if (panePesan) panePesan.style.display = 'none';
                if (paneAgenda) paneAgenda.style.display = 'block';
                if (titleEl) titleEl.textContent = 'Notifikasi & Pengingat';
                const hasHoliday = window.allNotificationsData && !!window.allNotificationsData.todayHolidayName;
                if (headerIcon) headerIcon.className = 'bi bi-bell-fill fs-5';
                if (headerIconContainer) {
                    headerIconContainer.style.background = hasHoliday ? 'rgba(220, 38, 38, 0.12)' : 'rgba(14, 165, 233, 0.12)';
                    headerIconContainer.style.color = hasHoliday ? '#DC2626' : '#0284c7';
                }
            }
        }

        function renderNotificationModal(data) {
            if (!data) return;

            const unreadFcm = data.unreadFcmCount || 0;
            const pesanLabel = document.getElementById('notifTabPesanLabel');
            if (pesanLabel) {
                pesanLabel.textContent = unreadFcm > 0 ? `Pesan Cloud (${dol}{unreadFcm})` : 'Pesan Cloud';
            }

            const activeAgendas = data.activeAgendas || [];
            const hasOngoing = !!data.ongoingJadwal && data.ongoingJadwal !== null && typeof data.ongoingJadwal === 'object';
            const agendaBadgeCount = activeAgendas.length + (hasOngoing ? 1 : 0);
            const agendaLabel = document.getElementById('notifTabAgendaLabel');
            if (agendaLabel) {
                agendaLabel.textContent = agendaBadgeCount > 0 ? `Agenda & Info (${dol}{agendaBadgeCount})` : 'Agenda & Info';
            }

            const fcmList = data.fcmNotifications || [];
            const emptyPesan = document.getElementById('notifPesanEmpty');
            const listWrapper = document.getElementById('notifPesanListWrapper');
            const pesanListEl = document.getElementById('notifPesanList');
            const pesanCountTitle = document.getElementById('notifPesanCountTitle');
            const btnMarkAllRead = document.getElementById('btnMarkAllRead');

            if (fcmList.length === 0) {
                if (emptyPesan) emptyPesan.style.display = 'block';
                if (listWrapper) listWrapper.style.display = 'none';
            } else {
                if (emptyPesan) emptyPesan.style.display = 'none';
                if (listWrapper) listWrapper.style.display = 'block';
                if (pesanCountTitle) pesanCountTitle.textContent = `Riwayat Pesan (${dol}{fcmList.length})`;
                if (btnMarkAllRead) btnMarkAllRead.style.display = unreadFcm > 0 ? 'inline-block' : 'none';

                if (pesanListEl) {
                    pesanListEl.innerHTML = fcmList.map(item => {
                        const isUnread = !item.isRead;
                        const dateFormatted = item.timestamp ? new Date(item.timestamp).toLocaleString('id-ID', {
                            day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
                        }) : '';
                        
                        return `
                            <div class="card card-custom p-3 position-relative ${dol}{isUnread ? 'border-primary border-opacity-50' : 'border-secondary border-opacity-25'}" 
                                 style="border-radius: 12px; cursor: pointer; transition: all 0.2s ease; background: ${dol}{isUnread ? 'rgba(2, 132, 199, 0.06)' : 'var(--card-bg)'};"
                                 onclick="handleNotifItemClick(${dol}{item.id}, ${dol}{isUnread})">
                                <div class="d-flex align-items-start justify-content-between gap-2 mb-1">
                                    <div class="d-flex align-items-center gap-2 flex-grow-1">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 28px; height: 28px; background: ${dol}{isUnread ? 'rgba(2, 132, 199, 0.15)' : 'rgba(148, 163, 184, 0.15)'}; color: ${dol}{isUnread ? '#0284c7' : '#94a3b8'}; flex-shrink: 0;">
                                            <i class="bi ${dol}{isUnread ? 'bi-bell-fill' : 'bi-bell'} small"></i>
                                        </div>
                                        <h6 class="fw-bold mb-0 text-main small text-truncate" style="max-width: 260px;">${dol}{escapeHtml(item.title)}</h6>
                                    </div>
                                    <div class="d-flex align-items-center gap-1">
                                        ${dol}{isUnread ? '<span class="badge bg-danger text-white rounded-pill px-2 py-1" style="font-size: 8.5px; font-weight: 800;">BARU</span>' : ''}
                                        <button type="button" class="btn btn-sm btn-icon text-secondary hover-danger p-0" onclick="event.stopPropagation(); deleteNotificationItem(${dol}{item.id})" title="Hapus pesan" style="width: 24px; height: 24px; border: none; background: transparent;">
                                            <i class="bi bi-x-lg" style="font-size: 12px;"></i>
                                        </button>
                                    </div>
                                </div>
                                ${dol}{item.body ? `<p class="mb-1 text-secondary" style="font-size: 12px; line-height: 1.45;">${dol}{escapeHtml(item.body)}</p>` : ''}
                                ${dol}{item.dataPayload ? `<div class="p-1 px-2 rounded mb-1 font-monospace text-secondary bg-dark bg-opacity-10" style="font-size: 10px; word-break: break-all;">Data: ${dol}{escapeHtml(item.dataPayload)}</div>` : ''}
                                <small class="text-muted" style="font-size: 10.5px;">${dol}{dateFormatted}</small>
                            </div>
                        `;
                    }).join('');
                }
            }

            const subTitleEl = document.getElementById('notifKalenderSubtitle');
            if (subTitleEl) {
                subTitleEl.textContent = `${dol}{data.hijriDate || '-'} Ã¢â‚¬Â¢ ${dol}{data.jawaDate || '-'}`;
            }

            const holidayTodaySection = document.getElementById('notifHolidayTodaySection');
            const holidayTodayCard = document.getElementById('notifHolidayTodayCard');
            const holidayTodayName = document.getElementById('notifHolidayTodayName');
            const observanceTodayCard = document.getElementById('notifObservanceTodayCard');
            const observanceTodayName = document.getElementById('notifObservanceTodayName');

            const hasHolidayToday = !!data.todayHolidayName;
            const hasObservanceToday = !!data.todayObservanceName;

            if (holidayTodaySection) {
                if (hasHolidayToday || hasObservanceToday) {
                    holidayTodaySection.style.display = 'flex';
                    if (holidayTodayCard) {
                        holidayTodayCard.style.display = hasHolidayToday ? 'block' : 'none';
                        if (holidayTodayName && hasHolidayToday) holidayTodayName.textContent = data.todayHolidayName;
                    }
                    if (observanceTodayCard) {
                        observanceTodayCard.style.display = hasObservanceToday ? 'block' : 'none';
                        if (observanceTodayName && hasObservanceToday) observanceTodayName.textContent = data.todayObservanceName;
                    }
                } else {
                    holidayTodaySection.style.display = 'none';
                }
            }

            const accordionTitle = document.getElementById('notifHolidaysAccordionTitle');
            if (accordionTitle) {
                accordionTitle.textContent = `Daftar Hari Libur & Hari Besar (${dol}{data.todayYear || new Date().getFullYear()})`;
            }
            renderHolidaysList();

            const ongoingSection = document.getElementById('notifOngoingJadwalSection');
            const ongoingTitle = document.getElementById('notifOngoingJadwalTitle');
            const ongoingSubtitle = document.getElementById('notifOngoingJadwalSubtitle');

            if (ongoingSection) {
                if (hasOngoing) {
                    ongoingSection.style.display = 'flex';
                    if (ongoingTitle) ongoingTitle.textContent = data.ongoingJadwal.kegiatan || '-';
                    if (ongoingSubtitle) ongoingSubtitle.textContent = `Pukul ${dol}{data.ongoingJadwal.waktu} Ã¢â‚¬Â¢ Kategori: ${dol}{data.ongoingJadwal.kategori}`;
                } else {
                    ongoingSection.style.display = 'none';
                }
            }

            const activeAgendaSection = document.getElementById('notifActiveAgendaSection');
            const activeAgendaTitle = document.getElementById('notifActiveAgendaCountTitle');
            const activeAgendaListEl = document.getElementById('notifActiveAgendaList');

            if (activeAgendaSection) {
                if (activeAgendas.length > 0) {
                    activeAgendaSection.style.display = 'flex';
                    if (activeAgendaTitle) activeAgendaTitle.textContent = `Agenda Kegiatan Hari Ini (${dol}{activeAgendas.length})`;
                    if (activeAgendaListEl) {
                        activeAgendaListEl.innerHTML = activeAgendas.map(agenda => `
                            <div class="p-2 px-3 rounded-3" style="background: var(--sidebar-bg); border: 1px solid var(--border-color);">
                                <div class="fw-bold text-primary small mb-0">${dol}{escapeHtml(agenda.judul)}</div>
                                ${dol}{agenda.detail ? `<div class="text-secondary small" style="font-size: 11.5px;">${dol}{escapeHtml(agenda.detail)}</div>` : ''}
                            </div>
                        `).join('');
                    }
                } else {
                    activeAgendaSection.style.display = 'none';
                }
            }
        }

        function toggleHolidaysAccordion() {
            const body = document.getElementById('notifHolidaysAccordionBody');
            const chevron = document.getElementById('notifHolidaysAccordionChevron');
            if (!body || !chevron) return;
            const isHidden = body.style.display === 'none';
            body.style.display = isHidden ? 'block' : 'none';
            chevron.className = isHidden ? 'bi bi-chevron-up text-secondary' : 'bi bi-chevron-down text-secondary';
        }

        function setHolidayCategoryFilter(cat) {
            window.currentHolidayCategory = cat;
            document.querySelectorAll('.filter-holiday-chip').forEach(btn => {
                if (btn.dataset.cat === cat) {
                    btn.className = 'btn btn-sm btn-primary rounded-pill px-2 py-0 small filter-holiday-chip';
                } else {
                    btn.className = 'btn btn-sm btn-outline-secondary rounded-pill px-2 py-0 small filter-holiday-chip';
                }
            });
            renderHolidaysList();
        }

        function filterHolidayList() {
            renderHolidaysList();
        }

        function renderHolidaysList() {
            const container = document.getElementById('notifHolidaysListContainer');
            if (!container || !window.allNotificationsData) return;
            const allHolidays = window.allNotificationsData.allHolidays || [];
            const query = (document.getElementById('notifHolidaySearchInput')?.value || '').toLowerCase().trim();
            const cat = window.currentHolidayCategory || 'Semua';

            const filtered = allHolidays.filter(item => {
                const matchesCat = (cat === 'Semua') || (cat === item.type);
                const matchesQuery = !query || item.name.toLowerCase().includes(query) || item.date.includes(query);
                return matchesCat && matchesQuery;
            });

            if (filtered.length === 0) {
                container.innerHTML = `<div class="text-muted small py-2 text-center" style="font-size: 11.5px;">Tidak ada hari libur / peringatan yang cocok.</div>`;
                return;
            }

            container.innerHTML = filtered.map(item => {
                const isLibur = item.type === 'Libur Nasional';
                const bg = isLibur ? 'rgba(220, 38, 38, 0.08)' : 'rgba(15, 118, 110, 0.08)';
                const border = isLibur ? 'rgba(220, 38, 38, 0.3)' : 'rgba(15, 118, 110, 0.3)';
                const color = isLibur ? '#DC2626' : '#0F766E';

                return `
                    <div class="p-2 rounded-2 d-flex align-items-center gap-2" style="background: ${dol}{bg}; border: 1px solid ${dol}{border};">
                        <span class="fw-bold" style="font-size: 11px; color: ${dol}{color}; min-width: 78px;">${dol}{item.date}</span>
                        <div class="d-flex flex-column flex-grow-1">
                            <span class="fw-semibold text-main" style="font-size: 11.5px; line-height: 1.2;">${dol}{escapeHtml(item.name)}</span>
                            <span style="font-size: 9.5px; color: ${dol}{color}; font-weight: 600;">${dol}{item.type}</span>
                        </div>
                    </div>
                `;
            }).join('');
        }

        async function handleNotifItemClick(id, isUnread) {
            if (!isUnread) return;
            await markNotificationRead(id);
        }

        async function markNotificationRead(id) {
            try {
                await fetch('/api/notifications?action=read&id=' + id, { method: 'POST' });
                if (window.allNotificationsData && window.allNotificationsData.fcmNotifications) {
                    const item = window.allNotificationsData.fcmNotifications.find(n => n.id === id);
                    if (item && !item.isRead) {
                        item.isRead = true;
                        window.allNotificationsData.unreadFcmCount = Math.max(0, (window.allNotificationsData.unreadFcmCount || 1) - 1);
                        window.allNotificationsData.totalNotificationsCount = Math.max(0, (window.allNotificationsData.totalNotificationsCount || 1) - 1);
                        localStorage.setItem('singghara_notifications_cache', JSON.stringify(window.allNotificationsData));
                        updateNotificationNavbarBadge(window.allNotificationsData);
                        renderNotificationModal(window.allNotificationsData);
                    }
                }
            } catch(e) {
                console.error('Error markNotificationRead:', e);
            }
        }

        async function markAllNotificationsRead() {
            try {
                await fetch('/api/notifications?action=read_all', { method: 'POST' });
                if (window.allNotificationsData && window.allNotificationsData.fcmNotifications) {
                    window.allNotificationsData.fcmNotifications.forEach(n => n.isRead = true);
                    const diff = window.allNotificationsData.unreadFcmCount || 0;
                    window.allNotificationsData.unreadFcmCount = 0;
                    window.allNotificationsData.totalNotificationsCount = Math.max(0, (window.allNotificationsData.totalNotificationsCount || diff) - diff);
                    localStorage.setItem('singghara_notifications_cache', JSON.stringify(window.allNotificationsData));
                    updateNotificationNavbarBadge(window.allNotificationsData);
                    renderNotificationModal(window.allNotificationsData);
                    showCustomToast('Semua pesan ditandai sudah dibaca');
                }
            } catch(e) {
                console.error('Error markAllNotificationsRead:', e);
            }
        }

        async function deleteNotificationItem(id) {
            try {
                await fetch('/api/notifications?id=' + id, { method: 'DELETE' });
                if (window.allNotificationsData && window.allNotificationsData.fcmNotifications) {
                    const idx = window.allNotificationsData.fcmNotifications.findIndex(n => n.id === id);
                    if (idx !== -1) {
                        const wasUnread = !window.allNotificationsData.fcmNotifications[idx].isRead;
                        window.allNotificationsData.fcmNotifications.splice(idx, 1);
                        if (wasUnread) {
                            window.allNotificationsData.unreadFcmCount = Math.max(0, (window.allNotificationsData.unreadFcmCount || 1) - 1);
                            window.allNotificationsData.totalNotificationsCount = Math.max(0, (window.allNotificationsData.totalNotificationsCount || 1) - 1);
                        }
                        localStorage.setItem('singghara_notifications_cache', JSON.stringify(window.allNotificationsData));
                        updateNotificationNavbarBadge(window.allNotificationsData);
                        renderNotificationModal(window.allNotificationsData);
                    }
                }
            } catch(e) {
                console.error('Error deleteNotificationItem:', e);
            }
        }

        async function clearAllNotifications() {
            if (!confirm('Hapus seluruh riwayat pesan notifikasi?')) return;
            try {
                await fetch('/api/notifications?action=clear_all', { method: 'DELETE' });
                if (window.allNotificationsData) {
                    const diff = window.allNotificationsData.unreadFcmCount || 0;
                    window.allNotificationsData.fcmNotifications = [];
                    window.allNotificationsData.unreadFcmCount = 0;
                    window.allNotificationsData.totalNotificationsCount = Math.max(0, (window.allNotificationsData.totalNotificationsCount || diff) - diff);
                    localStorage.setItem('singghara_notifications_cache', JSON.stringify(window.allNotificationsData));
                    updateNotificationNavbarBadge(window.allNotificationsData);
                    renderNotificationModal(window.allNotificationsData);
                    showCustomToast('Riwayat notifikasi berhasil dibersihkan');
                }
            } catch(e) {
                console.error('Error clearAllNotifications:', e);
            }
        }

        // --- INITIALIZATION ---
        window.addEventListener('DOMContentLoaded', () => {
            initAuth();
            refreshUserProfile();
            if (typeof fetchAdminStatus === 'function') {
                fetchAdminStatus();
            }
            try {
                const urlParams = new URLSearchParams(window.location.search);
                const tabParam = urlParams.get('tab');
                const hash = window.location.hash ? window.location.hash.substring(1).toLowerCase() : '';
                const path = window.location.pathname.toLowerCase();
                if (tabParam === 'ht' || tabParam === 'handy_talky' || hash === 'ht' || hash === 'handy_talky' || path === '/ht' || path === '/handy_talky' || path === '/walkie_talkie') {
                    switchTabByName('handy_talky');
                } else if (tabParam === 'telegram_ai' || tabParam === 'telegram' || tabParam === 'tg' || hash === 'telegram_ai' || hash === 'telegram' || hash === 'tg' || path === '/telegram_ai' || path === '/telegram' || path === '/tg') {
                    switchTabByName('telegram_ai');
                } else {
                    loadCurrentTab();
                }
            } catch (_) {
                loadCurrentTab();
            }
            loadNotifications();
            setInterval(loadNotifications, 30000);
        });
    </script>
    <div class="modal fade" id="noteModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <h5 class="modal-title fw-bold" id="noteModalTitle">Catatan</h5>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-3">
                    <input type="hidden" id="noteId">
                    <div class="mb-3">
                        <label class="form-label small fw-bold">Judul Catatan</label>
                        <input type="text" class="form-control" id="noteTitle" placeholder="Masukkan judul catatan...">
                    </div>
                    <div class="mb-3">
                        <label class="form-label small fw-bold">Kategori</label>
                        <select class="form-select" id="noteCategory">
                            <option value="Umum">Umum</option>
                            <option value="Pribadi">Pribadi</option>
                            <option value="Pekerjaan">Pekerjaan</option>
                            <option value="Ide">Ide & Gagasan</option>
                            <option value="Pengingat">Pengingat</option>
                        </select>
                    </div>
                    <div class="mb-3">
                        <label class="form-label small fw-bold">Isi Catatan</label>
                        <textarea class="form-control" id="noteContent" rows="5" placeholder="Tulis isi catatan di sini..."></textarea>
                    </div>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                    <button type="button" class="btn btn-brand" onclick="saveNote()"><i class="bi bi-save me-1"></i> Simpan</button>
                </div>
            </div>
        </div>
    </div>

    <!-- MODAL KONFIRMASI HAPUS FILE TUNGGAL PERSIS SHARESCREEN.KT -->
    <div class="modal fade" id="shareDeleteConfirmModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <div class="d-flex align-items-center gap-2">
                        <i class="bi bi-trash3-fill text-danger fs-5"></i>
                        <h5 class="modal-title fw-bold">Hapus Permanen?</h5>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-3">
                    <p class="text-main mb-0" style="font-size: 13.5px;">
                        Apakah Anda yakin ingin menghapus <strong id="shareDeleteTargetName">berkas ini</strong> secara permanen dari folder Google Drive Anda?
                    </p>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                    <button type="button" class="btn btn-danger" onclick="executeDeleteShareFile()">Hapus Permanen</button>
                </div>
            </div>
        </div>
    </div>

    <!-- MODAL KONFIRMASI HAPUS FILE MASSAL (BULK DELETE) PERSIS SHARESCREEN.KT -->
    <div class="modal fade" id="shareBulkDeleteConfirmModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <div class="d-flex align-items-center gap-2">
                        <i class="bi bi-trash3-fill text-danger fs-5"></i>
                        <h5 class="modal-title fw-bold">Hapus Berkas Terpilih?</h5>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-3">
                    <p class="text-main mb-0" style="font-size: 13.5px;">
                        Apakah Anda yakin ingin menghapus <strong id="shareBulkDeleteCount">0</strong> file terpilih secara permanen dari Google Drive?
                    </p>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                    <button type="button" class="btn btn-danger" onclick="executeBulkDeleteShareFiles()">Hapus Semua</button>
                </div>
            </div>
        </div>
    </div>

    <!-- MODAL KONFIGURASI URL CLOUD STORAGE APPS SCRIPT -->
    <div class="modal fade" id="shareConfigModal" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content card-custom">
                <div class="modal-header border-bottom-0 pb-0">
                    <div class="d-flex align-items-center gap-2">
                        <i class="bi bi-cloud-gear text-primary fs-5"></i>
                        <h5 class="modal-title fw-bold">Pengaturan Cloud Storage</h5>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body pt-3">
                    <label class="form-label small fw-bold">URL Google Apps Script</label>
                    <input type="url" class="form-control mb-2" id="shareConfigUrlInput" placeholder="https://script.google.com/macros/s/.../exec">
                    <small class="text-muted d-block mb-3" style="font-size: 11.5px;">
                        URL Web App Google Apps Script yang terhubung ke Google Drive Anda. Kosongkan untuk menggunakan URL default bawaan aplikasi.
                    </small>
                    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="document.getElementById('shareConfigUrlInput').value = window.shareDefaultUrl">
                        <i class="bi bi-arrow-counterclockwise me-1"></i> Pulihkan URL Default
                    </button>
                </div>
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Batal</button>
                    <button type="button" class="btn btn-primary" onclick="saveShareConfigUrl()">Simpan Perubahan</button>
                </div>
            </div>
        </div>
    </div>

    <!-- NOTIFIKASI & CLOUD MESSAGING MODAL (100% OFFLINE) -->
    <div class="modal fade" id="notificationModal" tabindex="-1" aria-labelledby="notifModalTitle" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable modal-md">
            <div class="modal-content card-custom" style="border-radius: 20px; border: 1px solid var(--border-color); box-shadow: var(--card-shadow);">
                <!-- Modal Header -->
                <div class="modal-header border-bottom-0 pb-1 d-flex align-items-center justify-content-between">
                    <div class="d-flex align-items-center gap-2">
                        <div id="notifModalHeaderIconContainer" class="rounded-circle d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: rgba(14, 165, 233, 0.12); color: #0284c7; flex-shrink: 0;">
                            <i id="notifModalHeaderIcon" class="bi bi-cloud-check-fill fs-5"></i>
                        </div>
                        <div>
                            <h5 class="modal-title fw-bold mb-0 text-main" id="notifModalTitle" style="font-size: 1.1rem;">Pesan Cloud Messaging</h5>
                        </div>
                    </div>
                    <button type="button" class="btn-close btn-close-custom" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>

                <!-- Modal Body with Tabs -->
                <div class="modal-body pt-2 px-3 pb-3">
                    <!-- Nav Tabs (Pills: Pesan Cloud & Agenda & Info) -->
                    <ul class="nav nav-pills nav-fill mb-3 p-1 rounded-3" id="notifTabs" style="background-color: var(--sidebar-bg); border: 1px solid var(--border-color);">
                        <li class="nav-item">
                            <button class="nav-link active py-2 px-2 d-flex align-items-center justify-content-center gap-1 fw-semibold small" id="notifTabPesanBtn" onclick="switchNotifTab('pesan')" type="button">
                                <i class="bi bi-cloud-arrow-down-fill me-1"></i>
                                <span id="notifTabPesanLabel">Pesan Cloud</span>
                            </button>
                        </li>
                        <li class="nav-item">
                            <button class="nav-link py-2 px-2 d-flex align-items-center justify-content-center gap-1 fw-semibold small" id="notifTabAgendaBtn" onclick="switchNotifTab('agenda')" type="button">
                                <i class="bi bi-calendar-check-fill me-1"></i>
                                <span id="notifTabAgendaLabel">Agenda & Info</span>
                            </button>
                        </li>
                    </ul>

                    <!-- TAB CONTENT 1: PESAN CLOUD -->
                    <div id="notifPanePesan" style="display: block;">
                        <div id="notifPesanEmpty" class="text-center py-4 px-2" style="display: none;">
                            <div class="rounded-circle d-flex align-items-center justify-content-center mx-auto mb-3" style="width: 60px; height: 60px; background: rgba(148, 163, 184, 0.15); color: #94a3b8; font-size: 1.8rem;">
                                <i class="bi bi-cloud-slash"></i>
                            </div>
                            <h6 class="fw-bold text-main mb-1">Belum Ada Pesan Masuk</h6>
                            <p class="text-secondary small mb-0 px-3" style="font-size: 12px; line-height: 1.5;">
                                Pesan pengumuman atau broadcast yang dikirim melalui Firebase Console akan tampil secara otomatis di sini.
                            </p>
                        </div>

                        <div id="notifPesanListWrapper" style="display: none;">
                            <div class="d-flex align-items-center justify-content-between mb-2">
                                <h6 class="fw-bold mb-0 text-main small" id="notifPesanCountTitle">Riwayat Pesan (0)</h6>
                                <div class="d-flex align-items-center gap-2">
                                    <button type="button" class="btn btn-sm btn-link text-decoration-none p-0 fw-semibold text-primary" id="btnMarkAllRead" onclick="markAllNotificationsRead()" style="font-size: 11.5px;">
                                        <i class="bi bi-check2-all me-1"></i> Baca Semua
                                    </button>
                                    <span class="text-muted small">Ã¢â‚¬Â¢</span>
                                    <button type="button" class="btn btn-sm btn-link text-decoration-none p-0 fw-semibold text-danger" onclick="clearAllNotifications()" style="font-size: 11.5px;">
                                        <i class="bi bi-trash me-1"></i> Bersihkan
                                    </button>
                                </div>
                            </div>
                            <div class="d-flex flex-column gap-2" id="notifPesanList" style="max-height: 380px; overflow-y: auto;">
                                <!-- Injected by JS -->
                            </div>
                        </div>
                    </div>

                    <!-- TAB CONTENT 2: AGENDA & INFO -->
                    <div id="notifPaneAgenda" style="display: none;">
                        <div class="d-flex flex-column gap-3">
                            <!-- Subtitle Kalender (Hijri & Jawa) -->
                            <div class="p-2 px-3 rounded-3" style="background: var(--sidebar-bg); border: 1px solid var(--border-color); font-size: 12px;">
                                <span class="fw-bold text-secondary">Kalender: </span>
                                <span class="fw-semibold text-main" id="notifKalenderSubtitle">-</span>
                            </div>

                            <!-- Hari Libur & Peringatan Hari Ini (jika ada) -->
                            <div id="notifHolidayTodaySection" style="display: none;" class="d-flex flex-column gap-2">
                                <div id="notifHolidayTodayCard" style="display: none;" class="p-3 rounded-3" style="background: rgba(220, 38, 38, 0.1); border: 1px solid rgba(220, 38, 38, 0.35);">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 36px; height: 36px; background: #DC2626; color: #ffffff; flex-shrink: 0;">
                                            <span style="font-size: 16px;">Ã°Å¸â€Â´</span>
                                        </div>
                                        <div class="d-flex flex-column">
                                            <span style="font-size: 10px; font-weight: 800; color: #DC2626; letter-spacing: 0.5px;">HARI LIBUR NASIONAL</span>
                                            <span class="fw-bold text-main" id="notifHolidayTodayName" style="font-size: 13.5px;"></span>
                                            <span class="text-secondary" style="font-size: 11px;">Selamat menikmati hari libur!</span>
                                        </div>
                                    </div>
                                </div>
                                <div id="notifObservanceTodayCard" style="display: none;" class="p-3 rounded-3" style="background: rgba(15, 118, 110, 0.1); border: 1px solid rgba(15, 118, 110, 0.35);">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 36px; height: 36px; background: #0F766E; color: #ffffff; flex-shrink: 0;">
                                            <i class="bi bi-star-fill" style="font-size: 16px;"></i>
                                        </div>
                                        <div class="d-flex flex-column">
                                            <span style="font-size: 10px; font-weight: 800; color: #0F766E; letter-spacing: 0.5px;">HARI BESAR / PERINGATAN NASIONAL</span>
                                            <span class="fw-bold text-main" id="notifObservanceTodayName" style="font-size: 13.5px;"></span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Accordion Daftar Hari Libur & Hari Besar Tahun Ini -->
                            <div class="card card-custom p-0 overflow-hidden" style="border: 1px solid var(--border-color); border-radius: 12px;">
                                <div class="p-2 px-3 d-flex align-items-center justify-content-between" onclick="toggleHolidaysAccordion()" style="background: var(--sidebar-bg); cursor: pointer;">
                                    <div class="d-flex align-items-center gap-2">
                                        <i class="bi bi-calendar-range-fill text-primary"></i>
                                        <span class="fw-bold small text-main" id="notifHolidaysAccordionTitle">Daftar Hari Libur & Hari Besar (2026)</span>
                                    </div>
                                    <i class="bi bi-chevron-down text-secondary" id="notifHolidaysAccordionChevron"></i>
                                </div>
                                <div id="notifHolidaysAccordionBody" style="display: none; padding: 10px; background: var(--card-bg);">
                                    <!-- Search input -->
                                    <div class="input-group input-group-sm mb-2">
                                        <span class="input-group-text bg-transparent border-end-0"><i class="bi bi-search text-secondary"></i></span>
                                        <input type="text" class="form-control border-start-0" id="notifHolidaySearchInput" placeholder="Cari hari libur / peringatan (mis: Buruh, Ibu, Guru)..." oninput="filterHolidayList()">
                                    </div>
                                    <!-- Filter pills -->
                                    <div class="d-flex gap-1 mb-2 overflow-auto py-1">
                                        <button type="button" class="btn btn-sm btn-primary rounded-pill px-2 py-0 small filter-holiday-chip" data-cat="Semua" onclick="setHolidayCategoryFilter('Semua')">Semua</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-2 py-0 small filter-holiday-chip" data-cat="Libur Nasional" onclick="setHolidayCategoryFilter('Libur Nasional')">Libur Nasional</button>
                                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-pill px-2 py-0 small filter-holiday-chip" data-cat="Hari Besar" onclick="setHolidayCategoryFilter('Hari Besar')">Hari Besar</button>
                                    </div>
                                    <!-- List container -->
                                    <div id="notifHolidaysListContainer" class="d-flex flex-column gap-1 overflow-auto" style="max-height: 180px;">
                                        <!-- Injected by JS -->
                                    </div>
                                </div>
                            </div>

                            <!-- Jadwal Sedang Berlangsung (jika ada) -->
                            <div id="notifOngoingJadwalSection" style="display: none;" class="d-flex flex-column gap-2">
                                <hr class="my-1 border-secondary border-opacity-25">
                                <h6 class="fw-bold mb-1 text-primary small">Jadwal Sedang Berlangsung Ã°Å¸â€¢â€™</h6>
                                <div class="p-3 rounded-3" style="background: rgba(14, 165, 233, 0.12); border: 1px solid rgba(14, 165, 233, 0.35);">
                                    <div class="d-flex align-items-center gap-3">
                                        <div class="rounded-circle d-flex align-items-center justify-content-center" style="width: 38px; height: 38px; background: #0284c7; color: #ffffff; flex-shrink: 0;">
                                            <i class="bi bi-clock-history fs-5"></i>
                                        </div>
                                        <div class="d-flex flex-column">
                                            <span class="fw-bold text-main" id="notifOngoingJadwalTitle" style="font-size: 14px;"></span>
                                            <span class="text-secondary small" id="notifOngoingJadwalSubtitle" style="font-size: 11.5px;"></span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- Agenda Kegiatan Hari Ini -->
                            <div id="notifActiveAgendaSection" style="display: none;" class="d-flex flex-column gap-2">
                                <hr class="my-1 border-secondary border-opacity-25">
                                <h6 class="fw-bold mb-1 text-main small" id="notifActiveAgendaCountTitle">Agenda Kegiatan Hari Ini (0)</h6>
                                <div class="d-flex flex-column gap-2" id="notifActiveAgendaList">
                                    <!-- Injected by JS -->
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Modal Footer -->
                <div class="modal-footer border-top-0 pt-0">
                    <button type="button" class="btn btn-outline-secondary w-100 py-2 rounded-3 fw-semibold" data-bs-dismiss="modal">
                        Tutup
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- TOAST CONTAINER UNTUK NOTIFIKASI -->
    <div id="toastContainer" class="toast-container position-fixed bottom-0 end-0 p-3" style="z-index: 9999;"></div>
</body>
</html>
"""
    }
}
