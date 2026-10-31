package com.example.ht

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manager Handy Talky (Walkie Talkie) Jaringan Lokal My Singghara.
 * Bekerja 100% OFFLINE tanpa internet melalui Wi-Fi lokal atau Hotspot/Tethering antar ponsel.
 * Menggunakan arsitektur Audio Ultra-Low Latency (20ms frames, Direct UDP socket streaming,
 * Cached Target Resolution, dan AudioTrack Low Latency Mode).
 */
object HandyTalkyManager {

    private const val TAG = "HandyTalkyManager"
    private const val HT_PORT = 9876
    private const val SAMPLE_RATE = 16000
    // 20ms per packet pada 16kHz = 320 sampel = 640 bytes PCM16 (Standar industri Real-Time PTT)
    private const val BUFFER_SIZE_SAMPLES = 320
    private const val PREFS_NAME = "mysingghara_ht_prefs"

    // Magic Bytes: "HTS" (Handy Talky Singghara)
    private val MAGIC_BYTES = byteArrayOf(0x48, 0x54, 0x53)
    private const val TYPE_AUDIO: Byte = 0x01
    private const val TYPE_PTT_START: Byte = 0x02
    private const val TYPE_PTT_END: Byte = 0x03
    private const val TYPE_BEACON: Byte = 0x04

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var audioTrack: AudioTrack? = null
    private var appContext: Context? = null

    private var listenJob: Job? = null
    private var beaconJob: Job? = null
    private var recordJob: Job? = null
    private var rxTimeoutJob: Job? = null
    private var roipRelayJob: Job? = null

    private val isRecording = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    // Pre-warmed Audio Source untuk instan PTT (tidak re-instansiasi berulang)
    private var preferredAudioSource = MediaRecorder.AudioSource.MIC

    // Pre-cached broadcast & unicast targets untuk eliminasi DNS/Network interface delay
    @Volatile
    private var cachedTargets: List<InetAddress> = emptyList()

    // Identitas Perangkat
    private var myDeviceId: String = ""
    private var prefs: SharedPreferences? = null

    // UI & Audio States
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _rxCallsign = MutableStateFlow("")
    val rxCallsign: StateFlow<String> = _rxCallsign.asStateFlow()

    private val _currentFrequency = MutableStateFlow(144.200)
    val currentFrequency: StateFlow<Double> = _currentFrequency.asStateFlow()

    private val _currentChannel = MutableStateFlow(HtChannelPresets.CHANNELS[1])
    val currentChannel: StateFlow<HtChannel> = _currentChannel.asStateFlow()

    private val _selectedBand = MutableStateFlow(HtBand.VHF)
    val selectedBand: StateFlow<HtBand> = _selectedBand.asStateFlow()

    private val _callsign = MutableStateFlow("Echo-Singghara")
    val callsign: StateFlow<String> = _callsign.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    // Digital Audio Boosters (Penyelesaian masalah suara lemah)
    private val _micGain = MutableStateFlow(2.5f) // Penguat mikrofon (1.0x - 5.0x)
    val micGain: StateFlow<Float> = _micGain.asStateFlow()

    private val _speakerGain = MutableStateFlow(2.2f) // Penguat output speaker (1.0x - 4.0x)
    val speakerGain: StateFlow<Float> = _speakerGain.asStateFlow()

    private val _squelchLevel = MutableStateFlow(1) // Sensitivitas squelch (0-9, 0=terbuka)
    val squelchLevel: StateFlow<Int> = _squelchLevel.asStateFlow()

    private val _rogerBeepEnabled = MutableStateFlow(true)
    val rogerBeepEnabled: StateFlow<Boolean> = _rogerBeepEnabled.asStateFlow()

    private val _squelchTailEnabled = MutableStateFlow(true)
    val squelchTailEnabled: StateFlow<Boolean> = _squelchTailEnabled.asStateFlow()

    private val _handsfreeMode = MutableStateFlow(false)
    val handsfreeMode: StateFlow<Boolean> = _handsfreeMode.asStateFlow()

    // Mode Radio over IP (RoIP) untuk Gateway HT Fisik
    private val _roipGatewayMode = MutableStateFlow(false)
    val roipGatewayMode: StateFlow<Boolean> = _roipGatewayMode.asStateFlow()

    private val _txAudioLevel = MutableStateFlow(0f)
    val txAudioLevel: StateFlow<Float> = _txAudioLevel.asStateFlow()

    private val _rxAudioLevel = MutableStateFlow(0f)
    val rxAudioLevel: StateFlow<Float> = _rxAudioLevel.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<HtDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<HtDevice>> = _discoveredDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<HtLog>>(emptyList())
    val logs: StateFlow<List<HtLog>> = _logs.asStateFlow()

    private val _localIpAddress = MutableStateFlow("127.0.0.1")
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private val _isHotspotDetected = MutableStateFlow(false)
    val isHotspotDetected: StateFlow<Boolean> = _isHotspotDetected.asStateFlow()

    val webRxAudioQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()

    private val devicesMap = mutableMapOf<String, HtDevice>()
    private var packetSequence = 0

    fun init(context: Context) {
        if (isRunning.get()) return
        isRunning.set(true)

        val appCtx = context.applicationContext
        appContext = appCtx

        prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Muat atau buat Device ID unik
        var id = prefs?.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8)
            prefs?.edit()?.putString("device_id", id)?.apply()
        }
        myDeviceId = id

        // Muat Callsign tersimpan
        val savedCallsign = prefs?.getString("callsign", "Echo-$id")
        _callsign.value = savedCallsign ?: "Echo-$id"

        // Muat frekuensi & channel tersimpan
        val savedFreq = prefs?.getFloat("frequency", 144.200f)?.toDouble() ?: 144.200
        _currentFrequency.value = savedFreq
        _currentChannel.value = HtChannelPresets.getChannelByFreq(savedFreq) ?: HtChannelPresets.CHANNELS[1]
        _selectedBand.value = _currentChannel.value.band

        // Muat audio boosters & squelch
        _micGain.value = prefs?.getFloat("mic_gain", 2.5f) ?: 2.5f
        _speakerGain.value = prefs?.getFloat("speaker_gain", 2.2f) ?: 2.2f
        _squelchLevel.value = prefs?.getInt("squelch_level", 1) ?: 1
        _rogerBeepEnabled.value = prefs?.getBoolean("roger_beep", true) ?: true
        _squelchTailEnabled.value = prefs?.getBoolean("squelch_tail", true) ?: true
        _roipGatewayMode.value = prefs?.getBoolean("roip_mode", false) ?: false

        // Inisialisasi Audio Track Output & Loudspeaker
        initAudioTrack()
        configureSpeakerphone(appCtx)

        // Acquire Wi-Fi Multicast Lock
        acquireMulticastLock(appCtx)

        // Deteksi dan cache alamat broadcast awal
        updateNetworkAddresses()

        // Buka UDP Socket
        startSocket()

        // Mulai Beacon Discovery
        startBeaconSender()

        addLog(HtLogType.SYSTEM, "HT Siap (Ultra-Low Latency Mode)")
    }

    private fun acquireMulticastLock(context: Context) {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("HTMulticastLock")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal acquire multicast lock", e)
        }
    }

    /**
     * Memastikan audio diarahkan ke Loudspeaker eksternal ponsel dengan volume maksimal.
     */
    fun configureSpeakerphone(context: Context) {
        try {
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = true
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < maxVol * 0.5f) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt(), 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring speakerphone", e)
        }
    }

    /**
     * Memperbarui daftar IP dan meng-cache semua target broadcast & unicast.
     * Dijalankan di background setiap beberapa detik agar TIDAK memperlambat pengiriman paket audio real-time.
     */
    private fun updateNetworkAddresses() {
        scope.launch(Dispatchers.IO) {
            try {
                var foundIp = "127.0.0.1"
                var isHotspot = false
                val newTargets = mutableSetOf<InetAddress>()

                try {
                    newTargets.add(InetAddress.getByName("255.255.255.255"))
                    newTargets.add(InetAddress.getByName("192.168.43.255"))
                    newTargets.add(InetAddress.getByName("192.168.1.255"))
                    newTargets.add(InetAddress.getByName("192.168.0.255"))
                } catch (_: Exception) {}

                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue

                    val name = iface.name.lowercase()
                    if (name.contains("ap") || name.contains("rndis") || name.contains("tether")) {
                        isHotspot = true
                    }

                    for (ifaceAddr in iface.interfaceAddresses) {
                        val bcast = ifaceAddr.broadcast
                        if (bcast != null) {
                            newTargets.add(bcast)
                        }
                    }

                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr.hostAddress != null && !addr.hostAddress!!.contains(":")) {
                            val ip = addr.hostAddress ?: ""
                            if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                foundIp = ip
                                if (ip.startsWith("192.168.43.")) {
                                    isHotspot = true
                                }
                            }
                        }
                    }
                }

                // Tambahkan unicast IP dari perangkat rekan yang aktif (Bypass AP Isolation)
                synchronized(devicesMap) {
                    for (dev in devicesMap.values) {
                        try {
                            newTargets.add(InetAddress.getByName(dev.ip))
                        } catch (_: Exception) {}
                    }
                }

                cachedTargets = newTargets.toList()
                _localIpAddress.value = foundIp
                _isHotspotDetected.value = isHotspot
            } catch (_: Exception) {}
        }
    }

    /**
     * Inisialisasi AudioTrack dengan buffer minimal dan mode Low Latency.
     * Menghilangkan penumpukan audio (buffer bloat) yang menyebabkan delay pada receiver.
     */
    private fun initAudioTrack() {
        try {
            audioTrack?.release()

            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Buffer minimal agar audio langsung keluar seketika
            val bufferSize = maxOf(minBuf, BUFFER_SIZE_SAMPLES * 4)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            audioTrack = builder.build()
            audioTrack?.setVolume(1.0f)
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startSocket() {
        try {
            socket = DatagramSocket(HT_PORT).apply {
                broadcast = true
                reuseAddress = true
                receiveBufferSize = 64 * 1024
                sendBufferSize = 64 * 1024
                soTimeout = 0 // Blocking read di thread terpisah
            }
        } catch (e: Exception) {
            Log.e(TAG, "Port $HT_PORT sibuk, membuka port alternatif", e)
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    receiveBufferSize = 64 * 1024
                    sendBufferSize = 64 * 1024
                }
            } catch (_: Exception) {}
        }

        listenJob?.cancel()
        listenJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            while (isActive && socket != null && !socket!!.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    handleIncomingPacket(packet)
                } catch (e: Exception) {
                    if (isActive) delay(5)
                }
            }
        }
    }

    private fun handleIncomingPacket(packet: DatagramPacket) {
        val data = packet.data
        val len = packet.length
        if (len < 10) return

        // Validasi Magic Bytes "HTS"
        if (data[0] != MAGIC_BYTES[0] || data[1] != MAGIC_BYTES[1] || data[2] != MAGIC_BYTES[2]) {
            return
        }

        try {
            val bais = ByteArrayInputStream(data, 3, len - 3)
            val dis = DataInputStream(bais)

            val type = dis.readByte()
            val senderDeviceId = dis.readUTF()
            val senderCallsign = dis.readUTF()
            val senderFreq = dis.readDouble()

            // Abaikan paket dari perangkat sendiri
            if (senderDeviceId == myDeviceId) return

            // Periksa kesesuaian frekuensi (toleransi 0.005 MHz)
            val isFrequencyMatch = abs(senderFreq - _currentFrequency.value) < 0.005

            when (type) {
                TYPE_AUDIO -> {
                    if (!isFrequencyMatch) return
                    val seq = dis.readInt()
                    val audioLen = dis.readInt()
                    if (audioLen > 0 && audioLen <= len) {
                        val audioBytes = ByteArray(audioLen)
                        dis.readFully(audioBytes)

                        // Putar Audio langsung dengan Audio Booster & Zero-Latency Output
                        playIncomingAudio(senderCallsign, audioBytes)
                    }
                }
                TYPE_PTT_START -> {
                    if (isFrequencyMatch) {
                        _isReceiving.value = true
                        _rxCallsign.value = senderCallsign
                        resetRxTimeout()
                        try {
                            audioTrack?.flush()
                        } catch (_: Exception) {}
                        addLog(HtLogType.RX, "$senderCallsign mulai bicara di ${String.format(Locale.US, "%.3f", senderFreq)} MHz")
                    }
                }
                TYPE_PTT_END -> {
                    if (isFrequencyMatch) {
                        _isReceiving.value = false
                        _rxAudioLevel.value = 0f
                        if (_squelchTailEnabled.value) {
                            playSquelchTail()
                        }
                        if (_rogerBeepEnabled.value) {
                            playRogerBeep()
                        }
                    }
                }
                TYPE_BEACON -> {
                    val senderIp = packet.address.hostAddress ?: "127.0.0.1"
                    val channelObj = HtChannelPresets.getChannelByFreq(senderFreq)
                    val channelLabel = channelObj?.name ?: "${String.format(Locale.US, "%.3f", senderFreq)} MHz"
                    val isTx = dis.readBoolean()

                    val device = HtDevice(
                        id = senderDeviceId,
                        callsign = senderCallsign,
                        ip = senderIp,
                        frequencyMHz = senderFreq,
                        channelName = channelLabel,
                        lastSeen = System.currentTimeMillis(),
                        isTransmitting = isTx
                    )
                    synchronized(devicesMap) {
                        devicesMap[senderDeviceId] = device
                        val cutoff = System.currentTimeMillis() - 15000
                        devicesMap.entries.removeAll { it.value.lastSeen < cutoff }
                        _discoveredDevices.value = devicesMap.values.toList().sortedBy { it.callsign }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming packet", e)
        }
    }

    /**
     * Memutar audio yang diterima seketika tanpa antrean lama.
     */
    private fun playIncomingAudio(callsign: String, pcmBytes: ByteArray) {
        if (!_isReceiving.value) {
            _isReceiving.value = true
            try {
                audioTrack?.flush()
            } catch (_: Exception) {}
        }
        _rxCallsign.value = callsign
        resetRxTimeout()

        // Terapkan Digital Speaker Gain Booster + Volume Slider
        val effectiveGain = _speakerGain.value * _volume.value
        val boostedBytes = boostPcm16Audio(pcmBytes, pcmBytes.size, effectiveGain)

        // Hitung RMS Audio Level untuk VU Meter
        var sumSquares = 0.0
        val shortBuffer = ByteBuffer.wrap(boostedBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get()
            sumSquares += sample * sample
        }
        val rms = if (numSamples > 0) sqrt(sumSquares / numSamples) else 0.0
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _rxAudioLevel.value = normalized

        // Filter Squelch
        val squelchThreshold = (_squelchLevel.value * 0.015f)
        if (_squelchLevel.value > 0 && normalized < squelchThreshold) {
            return
        }

        try {
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }
            audioTrack?.write(boostedBytes, 0, boostedBytes.size, AudioTrack.WRITE_NON_BLOCKING)

            // Simpan ke antrean audio web untuk didengarkan oleh browser PC
            if (webRxAudioQueue.size > 25) {
                webRxAudioQueue.poll()
            }
            webRxAudioQueue.offer(boostedBytes.clone())
        } catch (_: Exception) {}
    }

    private fun resetRxTimeout() {
        rxTimeoutJob?.cancel()
        rxTimeoutJob = scope.launch(Dispatchers.IO) {
            delay(350)
            if (_isReceiving.value) {
                _isReceiving.value = false
                _rxAudioLevel.value = 0f
                if (_squelchTailEnabled.value) {
                    playSquelchTail()
                }
            }
        }
    }

    /**
     * Mengirim paket kontrol/sinyal ke alamat target broadcast yang sudah di-cache.
     */
    private fun sendSignalPacket(type: Byte) {
        scope.launch(Dispatchers.IO) {
            try {
                val baos = ByteArrayOutputStream()
                baos.write(MAGIC_BYTES)
                val dos = DataOutputStream(baos)
                dos.writeByte(type.toInt())
                dos.writeUTF(myDeviceId)
                dos.writeUTF(_callsign.value)
                dos.writeDouble(_currentFrequency.value)
                dos.flush()

                val bytes = baos.toByteArray()
                val targets = cachedTargets
                val activeSocket = socket ?: return@launch

                for (targetAddr in targets) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, targetAddr, HT_PORT)
                        activeSocket.send(packet)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Mengirim paket audio secara langsung (Zero Coroutine Allocation per Packet & Zero DNS Lag).
     * Dijalankan langsung di thread recording loop.
     */
    private fun sendAudioPacketDirect(pcmBytes: ByteArray, length: Int) {
        try {
            val baos = ByteArrayOutputStream(length + 64)
            baos.write(MAGIC_BYTES)
            val dos = DataOutputStream(baos)
            dos.writeByte(TYPE_AUDIO.toInt())
            dos.writeUTF(myDeviceId)
            dos.writeUTF(_callsign.value)
            dos.writeDouble(_currentFrequency.value)
            dos.writeInt(++packetSequence)
            dos.writeInt(length)
            dos.write(pcmBytes, 0, length)
            dos.flush()

            val packetBytes = baos.toByteArray()
            val targets = cachedTargets
            val activeSocket = socket ?: return

            for (targetAddr in targets) {
                try {
                    val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, HT_PORT)
                    activeSocket.send(packet)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Memulai transmisi suara saat tombol PTT ditekan.
     * Menggunakan low-latency AudioRecord buffer dan instant streaming.
     */
    @SuppressLint("MissingPermission")
    fun startTransmission(context: Context) {
        if (_isTransmitting.value) return
        _isTransmitting.value = true

        vibrateShort(context)
        // Suara klik PTT dijalankan di background agar mikrofon langsung aktif tanpa menunggu
        scope.launch(Dispatchers.Default) {
            playPttPressSound()
        }

        addLog(HtLogType.TX, "Mulai Bicara di ${String.format(Locale.US, "%.3f", _currentFrequency.value)} MHz")

        // Kirim sinyal PTT_START seketika
        sendSignalPacket(TYPE_PTT_START)

        // Mulai Perekaman Mikrofon Low-Latency
        recordJob?.cancel()
        recordJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                // Buffer minimal agar AudioRecord langsung mengembalikan sampel tanpa delay hardware
                val bufferSize = maxOf(minBuf, BUFFER_SIZE_SAMPLES * 2)

                // Coba preferredAudioSource terlebih dahulu (default MIC)
                try {
                    val rec = AudioRecord(
                        preferredAudioSource,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        recorder = rec
                    } else {
                        rec.release()
                    }
                } catch (_: Exception) {}

                // Fallback jika source utama gagal
                if (recorder == null) {
                    val fallbackSources = listOf(
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        MediaRecorder.AudioSource.DEFAULT
                    )
                    for (source in fallbackSources) {
                        try {
                            val rec = AudioRecord(
                                source,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize
                            )
                            if (rec.state == AudioRecord.STATE_INITIALIZED) {
                                recorder = rec
                                preferredAudioSource = source
                                break
                            } else {
                                rec.release()
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord gagal diinisialisasi")
                    _isTransmitting.value = false
                    return@launch
                }

                recorder.startRecording()
                isRecording.set(true)

                val rawBuffer = ByteArray(BUFFER_SIZE_SAMPLES * 2)

                while (isActive && _isTransmitting.value) {
                    val readBytes = recorder.read(rawBuffer, 0, rawBuffer.size)
                    if (readBytes > 0) {
                        // Terapkan Digital Microphone Pre-amp Booster
                        val boostedAudio = boostPcm16Audio(rawBuffer, readBytes, _micGain.value)

                        // Hitung RMS Audio Level untuk VU Meter
                        var sumSquares = 0.0
                        val shortBuffer = ByteBuffer.wrap(boostedAudio, 0, readBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val numSamples = shortBuffer.remaining()
                        while (shortBuffer.hasRemaining()) {
                            val sample = shortBuffer.get()
                            sumSquares += sample * sample
                        }
                        val rms = if (numSamples > 0) sqrt(sumSquares / numSamples) else 0.0
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        _txAudioLevel.value = normalized

                        // Kirim langsung ke jaringan (Zero Coroutine Allocation per Packet)
                        sendAudioPacketDirect(boostedAudio, readBytes)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dalam proses perekaman mikrofon", e)
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
                isRecording.set(false)
                _txAudioLevel.value = 0f
            }
        }
    }

    /**
     * Menghentikan transmisi suara saat tombol PTT dilepas.
     */
    fun stopTransmission(context: Context) {
        if (!_isTransmitting.value) return
        _isTransmitting.value = false
        recordJob?.cancel()

        vibrateShort(context)

        // Kirim sinyal PTT_END seketika
        sendSignalPacket(TYPE_PTT_END)

        if (_rogerBeepEnabled.value) {
            playRogerBeep()
        }

        addLog(HtLogType.TX, "Transmisi Selesai (Standby)")
    }

    private fun startBeaconSender() {
        beaconJob?.cancel()
        beaconJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    updateNetworkAddresses()
                    val baos = ByteArrayOutputStream()
                    baos.write(MAGIC_BYTES)
                    val dos = DataOutputStream(baos)
                    dos.writeByte(TYPE_BEACON.toInt())
                    dos.writeUTF(myDeviceId)
                    dos.writeUTF(_callsign.value)
                    dos.writeDouble(_currentFrequency.value)
                    dos.writeBoolean(_isTransmitting.value)
                    dos.flush()

                    val bytes = baos.toByteArray()
                    val targets = cachedTargets
                    val activeSocket = socket

                    if (activeSocket != null && !activeSocket.isClosed) {
                        for (targetAddr in targets) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, targetAddr, HT_PORT)
                                activeSocket.send(packet)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    // ==========================================
    // DIGITAL AUDIO AMPLIFIER & SOFT LIMITER
    // ==========================================

    /**
     * Memperkuat amplitudo sampel PCM 16-bit dengan soft-clipping limiter
     * agar suara tidak pecah (distorsi) meski diperkuat hingga 4x lipat.
     */
    private fun boostPcm16Audio(pcmBytes: ByteArray, length: Int, gainMultiplier: Float): ByteArray {
        if (gainMultiplier <= 1.01f && gainMultiplier >= 0.99f) return pcmBytes

        val boosted = ByteArray(length)
        val inBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val outBuffer = ByteBuffer.wrap(boosted).order(ByteOrder.LITTLE_ENDIAN)
        val numSamples = length / 2

        for (i in 0 until numSamples) {
            val originalSample = inBuffer.short.toInt()
            val amplified = (originalSample * gainMultiplier).toInt()

            // Soft-clipping limiter
            val limited = when {
                amplified > 32767 -> {
                    val excess = amplified - 32767
                    (32767 - (excess * 0.15f)).toInt().coerceIn(0, 32767)
                }
                amplified < -32768 -> {
                    val excess = -32768 - amplified
                    (-32768 + (excess * 0.15f)).toInt().coerceIn(-32768, 0)
                }
                else -> amplified
            }
            outBuffer.putShort(limited.toShort())
        }

        return boosted
    }

    // ==========================================
    // SOUND EFFECTS SYNTHESIZER (100% Offline)
    // ==========================================

    private fun playTone(freqHz: Double, durationMs: Int, gain: Float = 0.6f) {
        scope.launch(Dispatchers.Default) {
            try {
                val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val time = i.toDouble() / SAMPLE_RATE
                    val angle = 2.0 * Math.PI * freqHz * time
                    val sample = (sin(angle) * 32767 * gain).toInt().coerceIn(-32768, 32767).toShort()
                    samples[i] = sample
                }
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
                audioTrack?.write(samples, 0, numSamples, AudioTrack.WRITE_NON_BLOCKING)
            } catch (_: Exception) {}
        }
    }

    fun playPttPressSound() {
        playTone(1100.0, 25, 0.40f)
    }

    fun playRogerBeep() {
        // Nada Roger Beep khas Motorola (1200 Hz, 75 ms)
        playTone(1200.0, 75, 0.55f)
    }

    fun playSquelchTail() {
        scope.launch(Dispatchers.Default) {
            try {
                val numSamples = (SAMPLE_RATE * 0.04).toInt() // 40 ms static burst
                val noise = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val rand = (Math.random() * 65536 - 32768).toInt()
                    val decay = 1.0 - (i.toDouble() / numSamples)
                    noise[i] = (rand * 0.15 * decay).toInt().toShort()
                }
                if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.play()
                }
                audioTrack?.write(noise, 0, numSamples, AudioTrack.WRITE_NON_BLOCKING)
            } catch (_: Exception) {}
        }
    }

    private fun vibrateShort(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(25)
            }
        } catch (_: Exception) {}
    }

    // ==========================================
    // KONTROL PENGATURAN & KANAL
    // ==========================================

    fun selectChannel(channel: HtChannel) {
        _currentChannel.value = channel
        _currentFrequency.value = channel.frequencyMHz
        _selectedBand.value = channel.band
        prefs?.edit()?.putFloat("frequency", channel.frequencyMHz.toFloat())?.apply()
        addLog(HtLogType.SYSTEM, "Ganti ke kanal ${channel.code} (${String.format(Locale.US, "%.3f", channel.frequencyMHz)} MHz)")
    }

    fun setFrequency(freqMHz: Double) {
        val clamped = freqMHz.coerceIn(100.0, 500.0)
        _currentFrequency.value = clamped
        val matchedChannel = HtChannelPresets.getChannelByFreq(clamped)
        if (matchedChannel != null) {
            _currentChannel.value = matchedChannel
            _selectedBand.value = matchedChannel.band
        } else {
            val band = if (clamped < 300.0) HtBand.VHF else HtBand.UHF
            _selectedBand.value = band
            _currentChannel.value = HtChannel(
                id = 999,
                name = "Kustom Manual",
                code = "MANUAL",
                frequencyMHz = clamped,
                band = band,
                description = "Frekuensi Kustom Manual"
            )
        }
        prefs?.edit()?.putFloat("frequency", clamped.toFloat())?.apply()
        addLog(HtLogType.SYSTEM, "Tuning frekuensi ke ${String.format(Locale.US, "%.3f", clamped)} MHz")
    }

    fun stepFrequency(stepDeltaMHz: Double) {
        setFrequency(_currentFrequency.value + stepDeltaMHz)
    }

    fun setCallsign(newCallsign: String) {
        val clean = newCallsign.trim().take(16)
        if (clean.isNotBlank()) {
            _callsign.value = clean
            prefs?.edit()?.putString("callsign", clean)?.apply()
            addLog(HtLogType.SYSTEM, "Callsign diubah menjadi $clean")
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
    }

    fun setMicGain(gain: Float) {
        val clamped = gain.coerceIn(1.0f, 5.0f)
        _micGain.value = clamped
        prefs?.edit()?.putFloat("mic_gain", clamped)?.apply()
    }

    fun setSpeakerGain(gain: Float) {
        val clamped = gain.coerceIn(1.0f, 4.0f)
        _speakerGain.value = clamped
        prefs?.edit()?.putFloat("speaker_gain", clamped)?.apply()
    }

    fun setSquelchLevel(level: Int) {
        val clamped = level.coerceIn(0, 9)
        _squelchLevel.value = clamped
        prefs?.edit()?.putInt("squelch_level", clamped)?.apply()
    }

    fun toggleRogerBeep() {
        val next = !_rogerBeepEnabled.value
        _rogerBeepEnabled.value = next
        prefs?.edit()?.putBoolean("roger_beep", next)?.apply()
    }

    fun toggleSquelchTail() {
        val next = !_squelchTailEnabled.value
        _squelchTailEnabled.value = next
        prefs?.edit()?.putBoolean("squelch_tail", next)?.apply()
    }

    fun toggleHandsfreeMode() {
        _handsfreeMode.value = !_handsfreeMode.value
    }

    fun toggleRoipGatewayMode(context: Context) {
        val next = !_roipGatewayMode.value
        _roipGatewayMode.value = next
        prefs?.edit()?.putBoolean("roip_mode", next)?.apply()
        if (next) {
            configureSpeakerphone(context)
            addLog(HtLogType.SYSTEM, "Mode Gateway RoIP Aktif: Suara HT diteruskan ke Jack/Radio Fisik")
        } else {
            addLog(HtLogType.SYSTEM, "Mode Gateway RoIP Dinonaktifkan")
        }
    }

    private fun addLog(type: HtLogType, message: String) {
        val log = HtLog(
            callsign = _callsign.value,
            frequencyMHz = _currentFrequency.value,
            type = type,
            message = message
        )
        _logs.value = (listOf(log) + _logs.value).take(60)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * Mengambil chunk audio RX dari antrean untuk didengar di browser PC
     */
    fun getWebRxAudioChunk(): ByteArray? {
        return webRxAudioQueue.poll()
    }

    /**
     * Memulai transmisi dari browser Web PC
     */
    fun startWebTransmission(customCallsign: String? = null) {
        val txName = customCallsign?.trim()?.ifEmpty { null } ?: "${_callsign.value} (Web PC)"
        _isTransmitting.value = true
        _rxCallsign.value = ""
        sendSignalPacket(TYPE_PTT_START)
        playPttPressSound()
        addLog(HtLogType.TX, "[$txName] Mulai transmisi PTT via Web Connect (${_currentFrequency.value} MHz)")
    }

    /**
     * Mengakhiri transmisi dari browser Web PC
     */
    fun stopWebTransmission(customCallsign: String? = null) {
        if (_isTransmitting.value) {
            _isTransmitting.value = false
            _txAudioLevel.value = 0f
            sendSignalPacket(TYPE_PTT_END)
            val txName = customCallsign?.trim()?.ifEmpty { null } ?: "${_callsign.value} (Web PC)"
            addLog(HtLogType.TX, "[$txName] Transmisi PTT selesai")
            if (_rogerBeepEnabled.value) {
                playRogerBeep()
            }
        }
    }

    /**
     * Memancarkan audio PCM dari browser Web PC ke jaringan lokal
     */
    fun transmitAudioFromWeb(pcmBytes: ByteArray, length: Int, customCallsign: String? = null) {
        if (!_isTransmitting.value) {
            _isTransmitting.value = true
        }

        // Hitung RMS Audio Level untuk VU Meter
        var sumSquares = 0.0
        val shortBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shortBuffer.remaining()
        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get()
            sumSquares += sample * sample
        }
        val rms = if (numSamples > 0) sqrt(sumSquares / numSamples) else 0.0
        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        _txAudioLevel.value = normalized

        // Terapkan mic gain booster
        val boosted = boostPcm16Audio(pcmBytes, length, _micGain.value)

        // Siarkan langsung ke UDP targets
        try {
            val baos = ByteArrayOutputStream(length + 64)
            baos.write(MAGIC_BYTES)
            val dos = DataOutputStream(baos)
            dos.writeByte(TYPE_AUDIO.toInt())
            dos.writeUTF(myDeviceId)
            dos.writeUTF(customCallsign?.trim()?.ifEmpty { null } ?: _callsign.value)
            dos.writeDouble(_currentFrequency.value)
            dos.writeInt(++packetSequence)
            dos.writeInt(boosted.size)
            dos.write(boosted, 0, boosted.size)
            dos.flush()

            val packetBytes = baos.toByteArray()
            val targets = cachedTargets
            val activeSocket = socket ?: return

            for (targetAddr in targets) {
                try {
                    val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, HT_PORT)
                    activeSocket.send(packet)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Eksekusi aksi kontrol dari REST API Web Connect
     */
    fun executeWebAction(action: String, params: Map<String, String>): Boolean {
        return try {
            when (action) {
                "select_channel" -> {
                    val id = params["id"]?.toIntOrNull() ?: return false
                    val ch = HtChannelPresets.CHANNELS.find { it.id == id } ?: return false
                    selectChannel(ch)
                    true
                }
                "set_freq" -> {
                    val freq = params["freq"]?.replace(",", ".")?.toDoubleOrNull() ?: return false
                    setFrequency(freq)
                    true
                }
                "step_freq" -> {
                    val delta = params["delta"]?.replace(",", ".")?.toDoubleOrNull() ?: return false
                    stepFrequency(delta)
                    true
                }
                "set_callsign" -> {
                    val cs = params["callsign"]?.trim() ?: return false
                    if (cs.isNotEmpty()) setCallsign(cs)
                    true
                }
                "set_speaker_gain" -> {
                    val g = params["gain"]?.toFloatOrNull() ?: return false
                    setSpeakerGain(g)
                    true
                }
                "set_mic_gain" -> {
                    val g = params["gain"]?.toFloatOrNull() ?: return false
                    setMicGain(g)
                    true
                }
                "set_squelch" -> {
                    val sql = params["level"]?.toIntOrNull() ?: return false
                    setSquelchLevel(sql)
                    true
                }
                "toggle_roger_beep" -> {
                    toggleRogerBeep()
                    true
                }
                "toggle_squelch_tail" -> {
                    toggleSquelchTail()
                    true
                }
                "toggle_handsfree" -> {
                    toggleHandsfreeMode()
                    true
                }
                "toggle_roip" -> {
                    appContext?.let { toggleRoipGatewayMode(it) } ?: run {
                        _roipGatewayMode.value = !_roipGatewayMode.value
                    }
                    true
                }
                "test_sound" -> {
                    appContext?.let { configureSpeakerphone(it) }
                    playPttPressSound()
                    playRogerBeep()
                    true
                }
                "ptt_start" -> {
                    val cs = params["callsign"]?.trim()?.ifEmpty { null }
                    startWebTransmission(cs)
                    true
                }
                "ptt_end" -> {
                    val cs = params["callsign"]?.trim()?.ifEmpty { null }
                    stopWebTransmission(cs)
                    true
                }
                "clear_logs" -> {
                    clearLogs()
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun destroy() {
        isRunning.set(false)
        listenJob?.cancel()
        beaconJob?.cancel()
        recordJob?.cancel()
        rxTimeoutJob?.cancel()
        roipRelayJob?.cancel()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}

        try {
            socket?.close()
        } catch (_: Exception) {}

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
    }
}
