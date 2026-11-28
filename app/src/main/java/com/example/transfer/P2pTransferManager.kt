package com.example.transfer

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.util.AppCrashGuard
import com.example.web.LocalHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Transfer berkas langsung antar dua HP My Singghara (100% offline, tanpa internet).
 *
 * Sisi server TIDAK dibuat baru: HP tujuan tetap dilayani LocalHttpServer di port 8080
 * lewat endpoint /api/transfer_file yang sudah ada. Kelas ini berperan sebagai
 * PEMINDAI (siapa saja yang hidup di jaringan lokal) dan KLIEN (unduh / unggah
 * dengan stream, bukan base64, supaya berkas besar tidak menghabiskan RAM).
 */
data class P2pPeer(
    val id: String,
    val label: String,
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val reachable: Boolean = false,
    val fromHistory: Boolean = false
) {
    val url: String get() = "http://$ip:$port"
}

data class P2pRemoteFile(
    val id: String,
    val name: String,
    val size: Long,
    val formattedSize: String,
    val mimeType: String,
    val extension: String,
    val isSelfApk: Boolean = false
)

data class P2pJob(
    val id: String,
    val fileName: String,
    val peerLabel: String,
    val direction: String,
    val totalBytes: Long,
    val doneBytes: Long = 0L,
    val state: String = "running",
    val message: String = ""
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((doneBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

class P2pTransferManager private constructor(private val context: Context) {

    private val scope = AppCrashGuard.scope()

    private val _peers = MutableStateFlow<List<P2pPeer>>(emptyList())
    val peers: StateFlow<List<P2pPeer>> = _peers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _jobs = MutableStateFlow<List<P2pJob>>(emptyList())
    val jobs: StateFlow<List<P2pJob>> = _jobs.asStateFlow()

    private val _responderRunning = MutableStateFlow(false)
    val responderRunning: StateFlow<Boolean> = _responderRunning.asStateFlow()

    private val peerLock = Any()
    private val lastSeenMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private var recvSocket: DatagramSocket? = null
    private var recvJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val deviceId: String = "ms_" + System.identityHashCode(this).toString(16)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val api: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    // ------------------------------------------------------------------
    // Sisi respons: HP ini bisa ditemukan & bisa menerima berkas
    // ------------------------------------------------------------------

    fun startResponder() {
        if (recvJob != null || _responderRunning.value) return
        _responderRunning.value = true
        restoreHistory()
        recvJob = scope.launch {
            acquireMulticastLock()
            val sock = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal bind port penemuan $DISCOVERY_PORT", e)
                try {
                    DatagramSocket().apply { broadcast = true }
                } catch (e2: Exception) {
                    null
                }
            }
            if (sock == null) {
                _responderRunning.value = false
                return@launch
            }
            recvSocket = sock
            val buf = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    sock.receive(packet)
                    handleDiscoveryPacket(sock, packet)
                } catch (e: Exception) {
                    if (!isActive) break
                    delay(120L)
                }
            }
        }
    }

    fun stopResponder() {
        recvJob?.cancel()
        recvJob = null
        try {
            recvSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recvSocket = null
        _responderRunning.value = false
        releaseMulticastLock()
    }

    private fun handleDiscoveryPacket(sock: DatagramSocket, packet: DatagramPacket) {
        val bytes = packet.data
        val len = packet.length
        if (len < 8) return
        if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) return
        try {
            val type = bytes[4].toInt()
            val stream = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes, 5, len - 5))
            val senderId = stream.readUTF()
            val senderModel = stream.readUTF()
            val senderIp = stream.readUTF()
            val senderPort = stream.readInt()
            val senderAddress = packet.address?.hostAddress ?: senderIp

            if (senderId == deviceId) return

            if (type == TYPE_WHO) {
                val reply = buildPacket(TYPE_HERE)
                sock.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
            }
            if (type == TYPE_HERE || type == TYPE_WHO) {
                val useIp = if (type == TYPE_HERE) senderAddress else senderIp
                if (useIp.isNotBlank() && !useIp.startsWith("127.")) {
                    upsertPeer(
                        P2pPeer(
                            id = "$useIp:$senderPort",
                            label = senderModel.ifBlank { useIp },
                            ip = useIp,
                            port = senderPort,
                            lastSeen = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Paket bukan dari aplikasi ini (port bentrok) atau UTF rusak: abaikan diam-diam.
        }
    }

    private fun buildPacket(type: Int): ByteArray {
        val baos = java.io.ByteArrayOutputStream(96)
        baos.write(MAGIC)
        val dos = java.io.DataOutputStream(baos)
        dos.writeByte(type)
        dos.writeUTF(deviceId)
        dos.writeUTF(deviceLabel())
        dos.writeUTF(LocalHttpServer.getIpAddress(context))
        dos.writeInt(SERVER_PORT)
        dos.flush()
        return baos.toByteArray()
    }

    private fun deviceLabel(): String =
        (Build.MODEL ?: "HP Singghara").take(28)

    private fun broadcastTargets(): List<java.net.InetAddress> {
        val list = mutableListOf<java.net.InetAddress>()
        try {
            list.add(java.net.InetAddress.getByName("255.255.255.255"))
            for (cidr in FALLBACK_SUBNET_BCAST) {
                try {
                    list.add(java.net.InetAddress.getByName(cidr))
                } catch (e: Exception) {
                }
            }
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return list
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (cfg in iface.interfaceAddresses) {
                    val bcast = cfg.broadcast ?: continue
                    if (list.none { it.hostAddress == bcast.hostAddress }) list.add(bcast)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Kirim "siapa saja di sini?" ke seluruh target broadcast, lalu tunggu jawaban.
     * HP tujuan tidak perlu menekan apa pun selama Web Connect-nya menyala.
     */
    fun scan(waitMillis: Long = 2600L) {
        scope.launch {
            _isScanning.value = true
            try {
                val sock = recvSocket ?: DatagramSocket().apply { broadcast = true }
                val payload = buildPacket(TYPE_WHO)
                for (target in broadcastTargets()) {
                    try {
                        sock.send(DatagramPacket(payload, payload.size, target, DISCOVERY_PORT))
                    } catch (e: Exception) {
                    }
                }
                if (recvSocket == null) {
                    try {
                        sock.close()
                    } catch (e: Exception) {
                    }
                }
                delay(waitMillis)
                pruneStalePeers(STALE_PEER_MILLIS)
                verifyPeers()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    /** Konfirmasi lewat HTTP bahwa peer benar-benar bisa diajak bicara. */
    suspend fun verifyPeers(): List<P2pPeer> = withContext(Dispatchers.IO) {
        val snapshot = synchronized(peerLock) { _peers.value }
        val updated = snapshot.map { peer ->
            val ok = probe(peer)
            peer.copy(reachable = ok, lastSeen = if (ok) System.currentTimeMillis() else peer.lastSeen)
        }
        synchronized(peerLock) {
            _peers.value = updated
        }
        persistHistory()
        updated
    }

    private suspend fun probe(peer: P2pPeer): Boolean {
        return try {
            val req = Request.Builder().url("${peer.url}/api/status").get().build()
            api.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return false
                val body = res.body?.string() ?: return false
                val json = JSONObject(body)
                json.optString("app").contains("Singghara", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Cek satu alamat hasil QR atau ketikan tangan. Bila cocok dengan My Singghara,
     * alamat itu dijadikan peer aktif dan dikembalikan supaya UI bisa langsung pakai.
     */
    suspend fun verifyOne(candidate: P2pPeer): P2pPeer? = withContext(Dispatchers.IO) {
        if (!probe(candidate)) return@withContext null
        val named = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url("${candidate.url}/api/transfer_file?action=state").get().build()
                api.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        JSONObject(res.body?.string() ?: "{}").optJSONObject("info")?.optString("deviceName", candidate.label)
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }
        val peer = candidate.copy(
            label = named?.takeIf { it.isNotBlank() } ?: candidate.label,
            reachable = true,
            lastSeen = System.currentTimeMillis(),
            fromHistory = false
        )
        upsertPeer(peer)
        peer
    }

    private fun upsertPeer(peer: P2pPeer) {
        // Last-seen diperbarui diam-diam: paket broadcast WHO/HERE bisa datang puluhan kali
        // per detik, dan menulis SharedPreferences + SSE tiap kali akan membebani kedua HP.
        lastSeenMap[peer.id] = System.currentTimeMillis()
        var changed = false
        synchronized(peerLock) {
            val current = _peers.value
            val existing = current.firstOrNull { it.id == peer.id }
            if (existing == null) {
                _peers.value = (listOf(peer) + current).sortedByDescending { it.lastSeen }
                changed = true
            } else if (existing.label != peer.label || !existing.reachable) {
                _peers.value = current.map {
                    if (it.id == peer.id) it.copy(label = peer.label, reachable = true, lastSeen = System.currentTimeMillis()) else it
                }.sortedByDescending { it.lastSeen }
                changed = true
            }
        }
        if (changed) {
            persistHistory()
            publishChange()
        }
    }

    /** Buang perangkat yang sudah lama tidak menjawab supaya daftar tidak menumpuk. */
    private fun pruneStalePeers(maxIdleMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxIdleMillis
        var changed = false
        synchronized(peerLock) {
            val keep = _peers.value.filter { (lastSeenMap[it.id] ?: it.lastSeen) >= cutoff || it.fromHistory }
            if (keep.size != _peers.value.size) {
                _peers.value = keep
                changed = true
            }
        }
        if (changed) {
            persistHistory()
            publishChange()
        }
    }

    fun forgetPeer(peerId: String) {
        synchronized(peerLock) {
            _peers.value = _peers.value.filter { it.id != peerId }
        }
        persistHistory()
        publishChange()
    }

    fun peerById(id: String): P2pPeer? = synchronized(peerLock) { _peers.value.firstOrNull { it.id == id } }

    private fun persistHistory() {
        try {
            val arr = JSONArray()
            synchronized(peerLock) { _peers.value }.take(12).forEach { p ->
                arr.put(JSONObject().apply {
                    put("ip", p.ip)
                    put("port", p.port)
                    put("label", p.label)
                    put("lastSeen", p.lastSeen)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PEERS, arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreHistory() {
        try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PEERS, "") ?: ""
            if (raw.isBlank()) return
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ip = o.optString("ip")
                val port = o.optInt("port", SERVER_PORT)
                if (ip.isBlank()) continue
                upsertPeer(
                    P2pPeer(
                        id = "$ip:$port",
                        label = o.optString("label", ip),
                        ip = ip,
                        port = port,
                        lastSeen = o.optLong("lastSeen", 0L),
                        reachable = false,
                        fromHistory = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------------------------------------------------
    // Klien: daftar berkas & isi HP lain
    // ------------------------------------------------------------------

    suspend fun remoteState(peer: P2pPeer): Pair<String, List<P2pRemoteFile>> = withContext(Dispatchers.IO) {
        val files = mutableListOf<P2pRemoteFile>()
        var deviceName = peer.label
        try {
            val req = Request.Builder().url("${peer.url}/api/transfer_file?action=state").get().build()
            api.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val json = JSONObject(body)
                    deviceName = json.optJSONObject("info")?.optString("deviceName", peer.label) ?: peer.label
                    val shared = json.optJSONArray("shared") ?: JSONArray()
                    for (i in 0 until shared.length()) {
                        val o = shared.optJSONObject(i) ?: continue
                        files.add(
                            P2pRemoteFile(
                                id = o.optString("id"),
                                name = o.optString("name"),
                                size = o.optLong("size"),
                                formattedSize = o.optString("formattedSize", FileTransferManager.formatFileSize(o.optLong("size"))),
                                mimeType = o.optString("mimeType", "application/octet-stream"),
                                extension = o.optString("extension", ""),
                                isSelfApk = o.optBoolean("isSelfApk")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca state peer ${peer.url}", e)
        }
        peer.copy(label = deviceName).let { updated ->
            upsertPeer(updated.copy(reachable = files.isNotEmpty() || deviceName != peer.label))
        }
        deviceName to files
    }

    /**
     * Ambil satu berkas dari HP lain: stream langsung ke folder terima HP ini,
     * tanpa mampir ke memori penuh.
     */
    suspend fun takeFromPeer(
        peer: P2pPeer,
        remote: P2pRemoteFile,
        onProgress: (P2pJob) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val manager = FileTransferManager.getInstance(context)
        val jobId = "take_${peer.id}_${remote.id}"
        var job = P2pJob(id = jobId, fileName = remote.name, peerLabel = peer.label, direction = "in", totalBytes = remote.size)
        emitJob(job, onProgress)
        try {
            val req = Request.Builder().url("${peer.url}/api/transfer_file?action=download&id=${java.net.URLEncoder.encode(remote.id, "UTF-8")}").get().build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
                val body = res.body ?: throw java.io.IOException("Body kosong")
                val total = if (body.contentLength() > 0L) body.contentLength() else remote.size
                job = job.copy(totalBytes = if (total > 0) total else job.totalBytes)
                val target = uniqueTarget(manager.receivedDir, remote.name)
                val written = target.outputStream().use { out ->
                    copyThrottled(body.byteStream(), out) { done ->
                        job = job.copy(doneBytes = done)
                        updateJobSnapshot(job)
                        onProgress(job)
                    }
                }
                if (written <= 0L || target.length() <= 0L) throw java.io.IOException("Berkas kosong")
                manager.refreshReceivedFiles()
                manager.logActivity(
                    title = "Diterima dari HP ${peer.label}: ${target.name}",
                    desc = "${FileTransferManager.formatFileSize(target.length())} • Transfer antar HP (offline)",
                    isIncoming = true,
                    channel = "HP"
                )
                finishJob(job.copy(state = "done", doneBytes = target.length()), onProgress)
                target
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ambil berkas gagal", e)
            finishJob(job.copy(state = "error", message = e.message ?: "Gagal"), onProgress)
            null
        }
    }

    /** Kirim satu URI (pemilih berkas) langsung ke HP tujuan, tanpa salinan ke folder share. */
    suspend fun sendUriToPeer(
        peer: P2pPeer,
        uri: Uri,
        onProgress: (P2pJob) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        var name = "berkas_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return@withContext false

        // Server butuh Content-Length. Beberapa penyedia konten (mis. berkas cloud) tidak
        // melaporkan ukuran, jadi salin dulu ke cache supaya panjangnya diketahui.
        if (size <= 0L) {
            val spool = File(context.cacheDir, "p2p_spool_${System.currentTimeMillis()}")
            try {
                val written = input.use { src ->
                    FileOutputStream(spool).use { dst -> src.copyTo(dst) }
                    spool.length()
                }
                if (written <= 0L) return@withContext false
                return@withContext sendLocalFileToPeer(peer, spool, onProgress).also {
                    try {
                        spool.delete()
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
                try {
                    spool.delete()
                } catch (e2: Exception) {
                }
                return@withContext false
            }
        }

        input.use { stream ->
            sendStreamToPeer(peer, name, size, stream, onProgress)
        }
    }

    suspend fun sendLocalFileToPeer(
        peer: P2pPeer,
        file: File,
        onProgress: (P2pJob) -> Unit = {}
    ): Boolean {
        if (!file.exists()) return false
        return file.inputStream().use { sendStreamToPeer(peer, file.name, file.length(), it, onProgress) }
    }

    /**
     * Unggah stream ke endpoint upload_raw HP tujuan. Nama berkas dikirim lewat header
     * supaya body HTTP murni berisi byte berkas (tidak ada base64, tidak ada JSON).
     */
    suspend fun sendStreamToPeer(
        peer: P2pPeer,
        fileName: String,
        contentLength: Long,
        source: InputStream,
        onProgress: (P2pJob) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        val jobId = "send_${peer.id}_${fileName.hashCode()}"
        var job = P2pJob(id = jobId, fileName = fileName, peerLabel = peer.label, direction = "out", totalBytes = contentLength)
        emitJob(job, onProgress)
        val declaredLength = contentLength
        try {
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = if (declaredLength > 0L) declaredLength else -1L
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastTick = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        total += read
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 200L) {
                            lastTick = now
                            job = job.copy(doneBytes = total)
                            updateJobSnapshot(job)
                            onProgress(job)
                        }
                    }
                    sink.flush()
                }
            }
            val req = Request.Builder()
                .url("${peer.url}/api/transfer_file?action=upload_raw")
                .header("X-Filename", java.net.URLEncoder.encode(fileName, "UTF-8"))
                .post(body)
                .build()
            http.newCall(req).execute().use { res ->
                val ok = res.isSuccessful
                finishJob(job.copy(state = if (ok) "done" else "error", message = if (ok) "" else "HTTP ${res.code}"), onProgress)
                if (ok) {
                    FileTransferManager.getInstance(context).logActivity(
                        title = "Dikirim ke HP ${peer.label}: $fileName",
                        desc = "${FileTransferManager.formatFileSize(contentLength)} • Transfer antar HP (offline)",
                        isIncoming = false,
                        channel = "HP"
                    )
                }
                ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kirim ke peer gagal", e)
            finishJob(job.copy(state = "error", message = e.message ?: "Gagal"), onProgress)
            false
        }
    }

    /**
     * Jalur Web Connect: browser PC tidak boleh lintas-origin ke HP kedua, jadi HP ini
     * yang meneruskan bytes ke HP tujuan.
     */
    suspend fun relayStreamToPeer(
        peer: P2pPeer,
        fileName: String,
        contentLength: Long,
        source: InputStream
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = if (contentLength > 0L) contentLength else -1L
                override fun writeTo(sink: BufferedSink) {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                    }
                    sink.flush()
                }
            }
            val req = Request.Builder()
                .url("${peer.url}/api/transfer_file?action=upload_raw")
                .header("X-Filename", java.net.URLEncoder.encode(fileName, "UTF-8"))
                .post(body)
                .build()
            http.newCall(req).execute().use { res ->
                val ok = res.isSuccessful
                if (ok) {
                    FileTransferManager.getInstance(context).logActivity(
                        title = "Diteruskan ke HP ${peer.label}: $fileName",
                        desc = "${FileTransferManager.formatFileSize(contentLength)} • Dari Web Connect ke HP lain",
                        isIncoming = false,
                        channel = "HP"
                    )
                }
                ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "Relay ke peer gagal", e)
            false
        }
    }

    /** Stream langsung dari HP lain ke browser PC (tanpa menyimpan salinan di HP ini). */
    suspend fun streamPeerFileTo(
        peer: P2pPeer,
        remoteFileId: String,
        output: java.io.OutputStream
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${peer.url}/api/transfer_file?action=download&id=${java.net.URLEncoder.encode(remoteFileId, "UTF-8")}").get().build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use false
                val body = res.body ?: return@use false
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream peer ke PC gagal", e)
            false
        }
    }

    private fun uniqueTarget(dir: File, rawName: String): File {
        val clean = rawName.replace("[/\\\\:*?\"<>|]".toRegex(), "_").ifBlank { "berkas_hp_${System.currentTimeMillis()}" }
        var target = File(dir, clean)
        if (target.exists()) {
            val base = clean.substringBeforeLast(".")
            val ext = if (clean.contains(".")) ".${clean.substringAfterLast(".")}" else ""
            var count = 1
            while (target.exists()) {
                target = File(dir, "$base ($count)$ext")
                count++
            }
        }
        return target
    }

    private fun copyThrottled(input: InputStream, out: java.io.OutputStream, onTick: (Long) -> Unit): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var lastTick = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            out.write(buffer, 0, read)
            total += read
            val now = System.currentTimeMillis()
            if (now - lastTick >= 200L) {
                lastTick = now
                onTick(total)
            }
        }
        out.flush()
        onTick(total)
        return total
    }

    private fun updateJobSnapshot(job: P2pJob) {
        val list = _jobs.value.toMutableList()
        list.removeAll { it.id == job.id }
        list.add(0, job)
        _jobs.value = list.take(12)
    }

    private fun emitJob(job: P2pJob, onProgress: (P2pJob) -> Unit) {
        val list = _jobs.value.toMutableList()
        list.removeAll { it.id == job.id }
        list.add(0, job)
        _jobs.value = list.take(12)
        onProgress(job)
        publishChange()
    }

    private fun finishJob(job: P2pJob, onProgress: (P2pJob) -> Unit) {
        val list = _jobs.value.toMutableList()
        list.removeAll { it.id == job.id }
        list.add(0, job)
        _jobs.value = list.take(12)
        onProgress(job)
        scope.launch {
            delay(6_000L)
            _jobs.value = _jobs.value.filter { it.id != job.id }
            publishChange()
        }
        publishChange()
    }

    fun clearFinishedJobs() {
        _jobs.value = _jobs.value.filter { it.state == "running" }
        publishChange()
    }

    private fun publishChange() {
        scope.launch {
            try {
                com.example.web.WebEventHub.publish("transfer", "change", System.currentTimeMillis().toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("P2PMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            multicastLock?.let { old ->
                try {
                    if (old.isHeld) old.release()
                } catch (e: Exception) {
                }
            }
            multicastLock = lock
        } catch (e: Exception) {
            Log.e(TAG, "Gagal multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        multicastLock = null
    }

    companion object {
        private const val TAG = "P2pTransfer"
        private const val DISCOVERY_PORT = 9878
        private const val SERVER_PORT = 8080
        private const val TYPE_WHO = 1
        private const val TYPE_HERE = 2
        private val MAGIC = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), '2'.code.toByte())
        private val FALLBACK_SUBNET_BCAST = listOf("192.168.43.255", "192.168.1.255", "192.168.0.255", "10.0.0.255")
        private const val PREFS = "p2p_transfer_prefs"
        private const val KEY_PEERS = "p2p_known_peers"
        private const val STALE_PEER_MILLIS = 180_000L

        @Volatile
        private var instance: P2pTransferManager? = null

        fun getInstance(context: Context): P2pTransferManager {
            return instance ?: synchronized(this) {
                instance ?: P2pTransferManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
