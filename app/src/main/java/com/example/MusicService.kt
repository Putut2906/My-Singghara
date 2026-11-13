package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.example.ui.MainViewModel
import com.example.ui.MusicTrack
import kotlinx.coroutines.*
import java.util.Calendar

class MusicService : Service() {

    companion object {
        var instance: MusicService? = null
            private set

        // Shared states so VM can read/write them
        var isServiceRunning = false
        val musicPlaylist = ArrayList<MusicTrack>()
        var currentTrackIndex = -1
        var isMusicPlaying = false
        var musicPlaybackPosition = 0
        var musicTotalDuration = 0
        var isMusicShuffle = false
            set(value) {
                field = value
                if (value) {
                    resetShufflePool()
                } else {
                    shuffleRemainingIndices.clear()
                    shuffleHistoryIndices.clear()
                }
            }
        var musicRepeatMode = "ALL"

        val shuffleRemainingIndices = ArrayList<Int>()
        val shuffleHistoryIndices = ArrayList<Int>()

        fun resetShufflePool() {
            shuffleRemainingIndices.clear()
            shuffleHistoryIndices.clear()
            if (musicPlaylist.isEmpty()) return
            val candidates = if (currentTrackIndex in musicPlaylist.indices) {
                val currentTrack = musicPlaylist[currentTrackIndex]
                // Grup mengikuti tab Folder (folder asal, atau artis bila kosong).
                // Tag artis bukan kunci yang benar: satu folder bisa berisi MP3,
                // AAC, FLAC, WAV, dan AIFF dengan artis berbeda-beda.
                val folderIndices = musicPlaylist.indices.filter {
                    folderKeyOf(musicPlaylist[it]) == folderKeyOf(currentTrack)
                }
                // Folder satu trek tidak boleh membuat acak mengulang lagu yang sama.
                if (folderIndices.size > 1) folderIndices else musicPlaylist.indices.toList()
            } else {
                musicPlaylist.indices.toList()
            }
            // Satu berkas bisa muncul lebih dari sekali di pustaka (baris ganda
            // MediaStore). Acak tidak perlu memilih salinan lagu yang sama.
            val currentTrack = musicPlaylist.getOrNull(currentTrackIndex)
            val seenAudio = HashSet<String>()
            val remaining = candidates.filter { idx ->
                val track = musicPlaylist[idx]
                if (idx == currentTrackIndex) return@filter false
                if (currentTrack != null && isSameAudio(track, currentTrack)) return@filter false
                if (!seenAudio.add(audioIdentity(track))) return@filter false
                true
            }.toMutableList()
            remaining.shuffle()
            shuffleRemainingIndices.addAll(remaining)
        }

        /** Kunci grup yang sama dengan tab Folder: folder asal, atau artis bila kosong. */
        fun folderKeyOf(track: MusicTrack): String =
            track.folder?.takeIf { it.isNotBlank() } ?: track.artist

        fun normalizedAudioPath(path: String): String {
            val normalized = java.io.File(path).absolutePath
                .replace('\\', '/').trimEnd('/').lowercase(java.util.Locale.ROOT)
            return normalized.replace(
                Regex("^/(?:sdcard|mnt/sdcard|storage/self/primary|storage/emulated/legacy)(?=/|$)"),
                "/storage/emulated/0"
            )
        }

        private fun audioPath(track: MusicTrack): String? {
            val path = track.sourcePath?.takeIf { it.isNotBlank() } ?: runCatching {
                val uri = java.net.URI(track.uriStr)
                if (uri.scheme == "file") uri.path else null
            }.getOrNull()
            return path?.let(::normalizedAudioPath)
        }

        fun audioIdentity(track: MusicTrack): String {
            audioPath(track)?.let { return "path:$it" }
            return track.uriStr
        }

        fun isSameAudio(a: MusicTrack, b: MusicTrack): Boolean {
            if (a.uriStr == b.uriStr) return true
            val aPath = audioPath(a)
            val bPath = audioPath(b)
            if (aPath != null && bPath != null) return aPath == bPath
            val sameName = a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
                folderKeyOf(a).trim().equals(folderKeyOf(b).trim(), ignoreCase = true)
            if (!sameName) return false
            return a.durationMs <= 0L || b.durationMs <= 0L || a.durationMs == b.durationMs
        }
    }

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var mediaSession: MediaSession? = null

    // AppCrashGuard: tanpa handler, satu exception yang lolos dari launch di scope ini
    // langsung diteruskan ke uncaught handler utama dan seluruh aplikasi berhenti.
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + SupervisorJob() + com.example.util.AppCrashGuard.handler
    )
    private var progressJob: Job? = null

    // Jumlah kegagalan putar beruntun; dipakai untuk menghentikan lompat otomatis
    // ketika seluruh daftar lagu tidak bisa dibaca.
    private var playFailureStreak = 0

    // Cache sampul agar RemoteViews tidak mendekode bitmap setiap kali notifikasi dibangun.
    private var notificationArtUri: String? = null
    private var notificationArtBitmap: Bitmap? = null
    private var currentMusicNotification: Notification? = null

    private var audioManager: android.media.AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                pauseMusic()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseMusic()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pauseMusic()
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequestLocal = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
            focusRequest = focusRequestLocal
            audioManager?.requestAudioFocus(focusRequestLocal)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun pauseMusic() {
        val player = mediaPlayer
        try {
            if (player != null && player.isPlaying) {
                player.pause()
            }
        } catch (e: Exception) {
            // ignore
        }
        isMusicPlaying = false
        MainViewModel.instance?.isMusicPlaying = false
        progressJob?.cancel()
        updateMusicNotification()
        abandonAudioFocus()
    }

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Tombol notifikasi/media bisa ditekan saat layar mati. Exception di sini
            // mematikan seluruh proses, jadi seluruh aksi dibungkus.
            try {
                when (intent.action) {
                    "com.example.music.PREVIOUS" -> playPrevious()
                    "com.example.music.PLAY_PAUSE" -> togglePlayPause()
                    "com.example.music.NEXT" -> playNext()
                    "com.example.music.SHUFFLE" -> {
                        isMusicShuffle = !isMusicShuffle
                        MainViewModel.instance?.isMusicShuffle = isMusicShuffle
                        updateMusicNotification()
                    }
                    "com.example.music.STOP" -> stopPlaybackAndDismiss()
                }
            } catch (t: Throwable) {
                android.util.Log.e("MusicService", "Aksi kontrol musik gagal: ${t.message}", t)
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Bluetooth putus / headset dicabut sering terjadi saat aplikasi diam.
            try {
                val action = intent.action
                when (action) {
                android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    if (isMusicPlaying) {
                        pauseMusic()
                        MainViewModel.instance?.showToastPopup("Koneksi audio/Bluetooth terputus, pemutaran lagu di-pause")
                    }
                    MainViewModel.instance?.let { vm ->
                        if (vm.isQuranPlaying) {
                            vm.stopQuranRecitation()
                        }
                    }
                }
                android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (isMusicPlaying) {
                        pauseMusic()
                        MainViewModel.instance?.showToastPopup("Koneksi Bluetooth terputus, pemutaran lagu di-pause")
                    }
                    MainViewModel.instance?.let { vm ->
                        if (vm.isQuranPlaying) {
                            vm.stopQuranRecitation()
                        }
                    }
                }
                android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        android.bluetooth.BluetoothA2dp.EXTRA_STATE,
                        android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED
                    )
                    if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED ||
                        state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTING) {
                        if (isMusicPlaying) {
                            pauseMusic()
                            MainViewModel.instance?.showToastPopup("Perangkat Bluetooth terputus, pemutaran lagu di-pause")
                        }
                        MainViewModel.instance?.let { vm ->
                            if (vm.isQuranPlaying) {
                                vm.stopQuranRecitation()
                            }
                        }
                    }
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        android.bluetooth.BluetoothAdapter.EXTRA_STATE,
                        android.bluetooth.BluetoothAdapter.STATE_OFF
                    )
                    if (state == android.bluetooth.BluetoothAdapter.STATE_OFF ||
                        state == android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF) {
                        if (isMusicPlaying) {
                            pauseMusic()
                            MainViewModel.instance?.showToastPopup("Bluetooth dimatikan, pemutaran lagu di-pause")
                        }
                        MainViewModel.instance?.let { vm ->
                            if (vm.isQuranPlaying) {
                                vm.stopQuranRecitation()
                            }
                        }
                    }
                }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MusicService", "Callback audio/Bluetooth gagal: ${t.message}", t)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isServiceRunning = true

        val filter = IntentFilter().apply {
            addAction("com.example.music.PREVIOUS")
            addAction("com.example.music.PLAY_PAUSE")
            addAction("com.example.music.NEXT")
            addAction("com.example.music.SHUFFLE")
            addAction("com.example.music.STOP")
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                musicReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (t: Throwable) {
            android.util.Log.e("MusicService", "Receiver kontrol gagal didaftarkan: ${t.message}", t)
        }

        val noisyFilter = IntentFilter().apply {
            addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                audioBecomingNoisyReceiver,
                noisyFilter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (t: Throwable) {
            android.util.Log.e("MusicService", "Receiver audio gagal didaftarkan: ${t.message}", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY membuat sistem menyalakan ulang service dengan intent null.
        // Tanpa trek yang dimuat, service hanya menempel notifikasi kosong dan
        // startForeground() di titik itu bisa dilempar ForegroundServiceStartNotAllowedException.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService() requires startForeground() within a few seconds
        // even when playback has not started yet, otherwise the system kills us.
        startForegroundNotification()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Terang/gelap sistem berubah saat lagu berjalan: bangun ulang kartu
        // memakai warna dari kualifikasi resource yang baru.
        if (currentTrackIndex in musicPlaylist.indices) {
            updateMusicNotification()
        }
    }

    private fun startForegroundNotification() {
        try {
            startForegroundNotificationInternal()
        } catch (t: Throwable) {
            // onStartCommand tidak boleh melempar: proses aplikasi ikut mati.
            android.util.Log.e("MusicService", "Notifikasi awal pemutar gagal: ${t.message}", t)
            stopSelf()
        }
    }

    private fun startForegroundNotificationInternal() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "music_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Pemutar Musik",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Kontrol pemutaran musik di status bar dan lock screen"
                    setShowBadge(false)
                }
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "pemutar_musik")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        try {
            startForeground(
                1001,
                builder.setContentTitle("My Singghara")
                    .setContentText("Menyiapkan pemutar musik...")
                    .setSmallIcon(R.drawable.ic_stat_singghara)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .build()
            )
        } catch (e: Exception) {
            // Android 12+ menolak startForeground saat aplikasi di latar belakang
            // (ForegroundServiceStartNotAllowedException). Tidak boleh menjatuhkan app;
            // service juga harus berhenti agar sistem tidak membunuh proses karena
            // startForegroundService() tidak pernah mendapat startForeground().
            android.util.Log.w("MusicService", "startForeground ditolak: ${e.message}")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun syncPlaylist(newList: List<MusicTrack>) {
        val activeTrack = musicPlaylist.getOrNull(currentTrackIndex)
        val tracks = newList.toList()
        musicPlaylist.clear()
        musicPlaylist.addAll(tracks)
        currentTrackIndex = activeTrack?.let { active ->
            tracks.indexOfFirst { it.uriStr == active.uriStr }.takeIf { it >= 0 }
                ?: tracks.indexOfFirst { isSameAudio(it, active) }
        } ?: -1
        MainViewModel.instance?.currentTrackIndex = currentTrackIndex
        shuffleRemainingIndices.clear()
        shuffleHistoryIndices.clear()
        if (isMusicShuffle) {
            resetShufflePool()
        }
    }

    // Playback control methods
    fun playTrack(index: Int) {
        if (musicPlaylist.isEmpty()) return
        val safeIndex = index.coerceIn(0, musicPlaylist.lastIndex)
        if (isMusicShuffle) {
            shuffleRemainingIndices.remove(safeIndex)
        }
        currentTrackIndex = safeIndex
        MainViewModel.instance?.currentTrackIndex = safeIndex
        val track = musicPlaylist[safeIndex]

        try {
            isMusicPlaying = false
            MainViewModel.instance?.isMusicPlaying = false
            progressJob?.cancel()

            mediaPlayer?.release()
            // Instance lama sudah di-release; bila tetap tertinggal di field, setiap
            // panggilan currentPosition berikutnya melempar IllegalStateException.
            mediaPlayer = null

            val player = android.media.MediaPlayer()
            try {
                player.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                player.setOnErrorListener { _, _, _ ->
                    isMusicPlaying = false
                    MainViewModel.instance?.isMusicPlaying = false
                    MainViewModel.instance?.showToastPopup(
                        "Tidak bisa memutar ${track.title}. Berkas rusak atau formatnya tidak didukung perangkat ini."
                    )
                    if (musicPlaylist.size > 1) {
                        playNext()
                    }
                    true
                }
                player.setDataSource(applicationContext, Uri.parse(track.uriStr))
                player.prepare()
                requestAudioFocus()
                player.start()

                musicTotalDuration = player.duration
                MainViewModel.instance?.musicTotalDuration = player.duration
                musicPlaybackPosition = 0
                MainViewModel.instance?.musicPlaybackPosition = 0

                player.setOnCompletionListener {
                    handleTrackCompletion()
                }
                // Field baru diisi setelah player pasti siap.
                mediaPlayer = player
                isMusicPlaying = true
                MainViewModel.instance?.isMusicPlaying = true
                playFailureStreak = 0
            } catch (e: Exception) {
                try {
                    player.release()
                } catch (_: Exception) {
                }
                throw e
            }
            startProgressTracker()
            MainViewModel.instance?.recordPlayExternal(track)
            updateMusicNotification()
        } catch (e: Exception) {
            isMusicPlaying = false
            MainViewModel.instance?.isMusicPlaying = false
            android.util.Log.e("MusicService", "playTrack gagal: ${e.message}")
            if (playFailureStreak == 0) {
                MainViewModel.instance?.showToastPopup(
                    "Tidak bisa memutar ${track.title}. Berkas rusak atau formatnya tidak didukung perangkat ini."
                )
            }
            // Lompat otomatis ke trek berikutnya tanpa batas berbahaya bila folder musik
            // sudah dipindah/hilang: setiap trek melempar exception dan playNext()
            // memanggil ulang playTrack() secara rekursif sampai StackOverflowError.
            playFailureStreak++
            if (musicPlaylist.size > 1 && playFailureStreak < 5) {
                playNext()
            } else {
                playFailureStreak = 0
                updateMusicNotification()
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                pauseMusic()
            } else {
                requestAudioFocus()
                player.start()
                isMusicPlaying = true
                MainViewModel.instance?.isMusicPlaying = true
                startProgressTracker()
                updateMusicNotification()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            try {
                player.seekTo(positionMs)
                musicPlaybackPosition = positionMs
                MainViewModel.instance?.musicPlaybackPosition = positionMs
                refreshNotificationProgress()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun getSameFolderTrackIndices(): List<Int> {
        if (musicPlaylist.isEmpty() || currentTrackIndex !in musicPlaylist.indices) return emptyList()
        val key = folderKeyOf(musicPlaylist[currentTrackIndex])
        return musicPlaylist.indices.filter { folderKeyOf(musicPlaylist[it]) == key }
    }

    /** Indeks berikutnya/sebelumnya yang audionya berbeda; null bila semuanya lagu yang sama. */
    private fun distinctStepIndex(indices: List<Int>, step: Int): Int? {
        if (indices.isEmpty()) return null
        val current = musicPlaylist.getOrNull(currentTrackIndex)
        val start = indices.indexOf(currentTrackIndex)
        if (start == -1) {
            return indices.firstOrNull { current == null || !isSameAudio(musicPlaylist[it], current) }
                ?: indices.first()
        }
        for (offset in 1..indices.size) {
            val idx = indices[Math.floorMod(start + step * offset, indices.size)]
            if (current == null || !isSameAudio(musicPlaylist[idx], current)) return idx
        }
        return null
    }

    /** Kandidat setelah posisi saat ini tanpa memutar balik (mode A-Z / pengulangan mati). */
    private fun orderedNextIndex(indices: List<Int>): Int? {
        if (indices.isEmpty()) return null
        val current = musicPlaylist.getOrNull(currentTrackIndex)
        val start = indices.indexOf(currentTrackIndex)
        if (start == -1) return indices.firstOrNull()
        for (pos in start + 1 until indices.size) {
            val idx = indices[pos]
            if (current == null || !isSameAudio(musicPlaylist[idx], current)) return idx
        }
        return null
    }

    fun playNext() {
        if (musicPlaylist.isEmpty()) return
        val current = musicPlaylist.getOrNull(currentTrackIndex)
        val folderCandidates = getSameFolderTrackIndices()
        // Folder berisi satu trek tetap harus bisa Next: pakai seluruh antrean.
        val candidates = if (folderCandidates.size > 1) {
            folderCandidates
        } else {
            (0 until musicPlaylist.size).toList()
        }
        if (candidates.isEmpty()) return

        if (isMusicShuffle) {
            shuffleRemainingIndices.retainAll(candidates.toSet())
            // Antrean acak juga bisa memuat salinan lagu yang sedang diputar; buang.
            var nextIndex = -1
            while (shuffleRemainingIndices.isNotEmpty()) {
                val idx = shuffleRemainingIndices.removeAt(0)
                if (current == null || !isSameAudio(musicPlaylist[idx], current)) {
                    nextIndex = idx
                    break
                }
            }
            if (nextIndex == -1) {
                val remaining = candidates.filter { idx ->
                    idx != currentTrackIndex &&
                        (current == null || !isSameAudio(musicPlaylist[idx], current))
                }.toMutableList()
                if (remaining.isNotEmpty()) {
                    remaining.shuffle()
                    shuffleRemainingIndices.clear()
                    shuffleRemainingIndices.addAll(remaining)
                    nextIndex = shuffleRemainingIndices.removeAt(0)
                }
            }
            if (nextIndex == -1) {
                // Semua kandidat adalah salinan lagu ini: putar ulang daripada tombol diam.
                playTrack(if (currentTrackIndex in musicPlaylist.indices) currentTrackIndex else candidates.first())
                return
            }
            if (currentTrackIndex in candidates) {
                shuffleHistoryIndices.add(currentTrackIndex)
            }
            playTrack(nextIndex)
        } else {
            val nextIndex = distinctStepIndex(candidates, 1)
                ?: distinctStepIndex(musicPlaylist.indices.toList(), 1)
            // Seluruh pustaka berisi lagu yang sama: ulangi trek itu daripada diam.
            playTrack(nextIndex ?: candidates.first())
        }
    }

    fun playPrevious() {
        if (musicPlaylist.isEmpty()) return
        val current = musicPlaylist.getOrNull(currentTrackIndex)
        val folderCandidates = getSameFolderTrackIndices()
        // Sama seperti Next: folder satu trek memakai seluruh antrean.
        val candidates = if (folderCandidates.size > 1) {
            folderCandidates
        } else {
            (0 until musicPlaylist.size).toList()
        }
        if (candidates.isEmpty()) return

        if (isMusicShuffle) {
            shuffleHistoryIndices.retainAll(candidates.toSet())
            var prevIndex = -1
            while (shuffleHistoryIndices.isNotEmpty()) {
                val idx = shuffleHistoryIndices.removeAt(shuffleHistoryIndices.lastIndex)
                if (current == null || !isSameAudio(musicPlaylist[idx], current)) {
                    prevIndex = idx
                    break
                }
            }
            if (prevIndex != -1) {
                if (currentTrackIndex in candidates) {
                    shuffleRemainingIndices.add(0, currentTrackIndex)
                }
                playTrack(prevIndex)
            } else {
                val target = distinctStepIndex(candidates, -1)
                    ?: distinctStepIndex(musicPlaylist.indices.toList(), -1)
                playTrack(target ?: candidates.first())
            }
        } else {
            val target = distinctStepIndex(candidates, -1)
                ?: distinctStepIndex(musicPlaylist.indices.toList(), -1)
            playTrack(target ?: candidates.first())
        }
    }

    private fun handleTrackCompletion() {
        when (musicRepeatMode) {
            "ONE" -> {
                playTrack(currentTrackIndex)
            }
            "ALL" -> {
                playNext()
            }
            "AZ" -> {
                val indices = getSameFolderTrackIndices()
                if (indices.isNotEmpty()) {
                    val next = orderedNextIndex(indices)
                    if (next != null) playTrack(next) else stopPlaybackState()
                } else {
                    if (currentTrackIndex < musicPlaylist.lastIndex) {
                        playNext()
                    } else {
                        stopPlaybackState()
                    }
                }
            }
            else -> { // "NONE"
                val indices = getSameFolderTrackIndices()
                if (indices.isNotEmpty()) {
                    val next = orderedNextIndex(indices)
                    if (next != null) playTrack(next) else stopPlaybackState()
                } else {
                    if (currentTrackIndex < musicPlaylist.lastIndex) {
                        playNext()
                    } else {
                        stopPlaybackState()
                    }
                }
            }
        }
    }

    private fun stopPlaybackState() {
        isMusicPlaying = false
        MainViewModel.instance?.isMusicPlaying = false
        musicPlaybackPosition = 0
        MainViewModel.instance?.musicPlaybackPosition = 0
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            // MediaPlayer yang sudah di-release melempar IllegalStateException.
        }
        progressJob?.cancel()
        updateMusicNotification()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch(Dispatchers.Main) {
            var lastNotificationTick = 0L
            while (isMusicPlaying) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            // SETIAP tulis ke musicPlaybackPosition membuat SELURUH
                            // layar PemutarMusikScreen (ribuan baris) dikomposisi ulang.
                            // Dulu ini terjadi tiap 100ms, lalu 250ms; sekarang cukup
                            // sekali per detik, seirama dengan refresh notifikasi.
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastNotificationTick >= 1000L) {
                                lastNotificationTick = now
                                val position = player.currentPosition
                                musicPlaybackPosition = position
                                MainViewModel.instance?.musicPlaybackPosition = position
                                updateMediaSessionState()
                                refreshNotificationProgress()
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPlaybackAndDismiss() {
        isMusicPlaying = false
        MainViewModel.instance?.isMusicPlaying = false
        musicPlaybackPosition = 0
        MainViewModel.instance?.musicPlaybackPosition = 0
        progressJob?.cancel()
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            // ignore
        }
        abandonAudioFocus()
        currentMusicNotification = null
        notificationArtUri = null
        notificationArtBitmap = null
        stopForeground(true)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(1001)
        stopSelf()
    }

    private fun initMediaSession() {
        if (mediaSession == null) {
            mediaSession = MediaSession(applicationContext, "PemutarMusikSession").apply {
                isActive = true
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        togglePlayPause()
                    }
                    override fun onPause() {
                        togglePlayPause()
                    }
                    override fun onSkipToNext() {
                        playNext()
                    }
                    override fun onSkipToPrevious() {
                        playPrevious()
                    }
                    override fun onSeekTo(pos: Long) {
                        try {
                            mediaPlayer?.seekTo(pos.toInt())
                            updateMediaSessionState()
                            updateMusicNotification()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                })
            }
        }
    }

    /**
     * currentPosition/duration melempar IllegalStateException pada MediaPlayer yang
     * sudah di-release; notifikasi tidak boleh menjatuhkan service.
     */
    private fun safeCurrentPosition(): Int = try {
        mediaPlayer?.currentPosition ?: musicPlaybackPosition
    } catch (e: Exception) {
        musicPlaybackPosition
    }

    private fun safeDuration(): Int = try {
        mediaPlayer?.duration ?: musicTotalDuration
    } catch (e: Exception) {
        musicTotalDuration
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
        val state = if (isMusicPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        stateBuilder.setState(state, safeCurrentPosition().coerceAtLeast(0).toLong(), 1.0f)
        session.setPlaybackState(stateBuilder.build())
    }

    fun updateMusicNotification() {
        // Dipanggil tiap detik oleh pelacak progres dan oleh callback Bluetooth;
        // kegagalan membangun RemoteViews tidak boleh mematikan proses.
        try {
            updateMusicNotificationInternal()
        } catch (t: Throwable) {
            android.util.Log.e("MusicService", "Notifikasi musik gagal diperbarui: ${t.message}", t)
        }
    }

    private fun updateMusicNotificationInternal() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "music_playback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pemutar Musik",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutaran musik di status bar dan lock screen"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val track = if (currentTrackIndex in musicPlaylist.indices) {
            musicPlaylist[currentTrackIndex]
        } else {
            currentMusicNotification = null
            stopForeground(true)
            notificationManager.cancel(1001)
            return
        }

        initMediaSession()
        updateMediaSessionState()

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val prevIntent = PendingIntent.getBroadcast(
            this, 1, Intent("com.example.music.PREVIOUS"), pendingFlags
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            this, 2, Intent("com.example.music.PLAY_PAUSE"), pendingFlags
        )
        val nextIntent = PendingIntent.getBroadcast(
            this, 3, Intent("com.example.music.NEXT"), pendingFlags
        )
        val shuffleIntent = PendingIntent.getBroadcast(
            this, 4, Intent("com.example.music.SHUFFLE"), pendingFlags
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 5, Intent("com.example.music.STOP"), pendingFlags
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "pemutar_musik")
            },
            pendingFlags
        )

        val albumArt = getNotificationArt(track)

        val rawDuration = safeDuration().toLong()
        val duration = if (rawDuration > 0L) rawDuration else track.durationMs
        val position = safeCurrentPosition().coerceAtLeast(0).toLong()
        val progress = if (duration > 0L) {
            ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        } else {
            0
        }

        val metaBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(android.media.MediaMetadata.METADATA_KEY_ALBUM, "ponsel ini")
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, duration)

        if (albumArt != null) {
            metaBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        // Kartu kontrol media milik aplikasi, bukan MediaStyle bawaan Android.
        // Tampilan ringkas dipakai saat panel notifikasi belum dibentangkan,
        // tampilan penuh saat notifikasi dibuka.
        val compactViews = RemoteViews(packageName, R.layout.notification_music_player_compact).apply {
            setTextViewText(R.id.notif_title, track.title)
            setTextViewText(R.id.notif_artist, track.artist.ifBlank { "Folder Audio" })
            setImageViewResource(
                R.id.notif_play_pause,
                if (isMusicPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
            )
            setContentDescription(
                R.id.notif_play_pause,
                if (isMusicPlaying) "Jeda" else "Putar"
            )
            setOnClickPendingIntent(R.id.notif_play_pause, playPauseIntent)
            setOnClickPendingIntent(R.id.notif_close, stopIntent)
            if (albumArt != null) {
                setImageViewBitmap(R.id.notif_album_art, albumArt)
            } else {
                setImageViewResource(R.id.notif_album_art, R.drawable.ic_notif_music)
            }
        }

        val bigViews = RemoteViews(packageName, R.layout.notification_music_player).apply {
            setTextViewText(R.id.notif_title, track.title)
            setTextViewText(R.id.notif_artist, track.artist.ifBlank { "Folder Audio" })
            setTextViewText(R.id.notif_elapsed, formatPlaybackTime(position))
            setTextViewText(R.id.notif_duration, formatPlaybackTime(duration))
            setProgressBar(R.id.notif_progress, 1000, progress, false)
            setImageViewResource(
                R.id.notif_play_pause,
                if (isMusicPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
            )
            setContentDescription(
                R.id.notif_play_pause,
                if (isMusicPlaying) "Jeda" else "Putar"
            )
            setOnClickPendingIntent(R.id.notif_prev, prevIntent)
            setOnClickPendingIntent(R.id.notif_play_pause, playPauseIntent)
            setOnClickPendingIntent(R.id.notif_next, nextIntent)
            setOnClickPendingIntent(R.id.notif_shuffle, shuffleIntent)
            setOnClickPendingIntent(R.id.notif_close, stopIntent)
            setInt(
                R.id.notif_shuffle,
                "setColorFilter",
                getColor(if (isMusicShuffle) R.color.notif_accent else R.color.notif_text_secondary)
            )
            if (albumArt != null) {
                setImageViewBitmap(R.id.notif_album_art, albumArt)
            } else {
                setImageViewResource(R.id.notif_album_art, R.drawable.ic_notif_music)
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        builder.setContentTitle(track.title)
            .setContentText(track.artist)
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isMusicPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(compactViews)
            .setCustomBigContentView(bigViews)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_LOW)
        }

        // Ikon status bar tetap milik aplikasi. Large icon tidak dipasang agar
        // sampul hanya tampil sekali di kartu aplikasi, bukan lagi di sisi kanan header.
        builder.setSmallIcon(R.drawable.ic_stat_singghara)

        val notification = builder.build()
        currentMusicNotification = notification
        try {
            startForeground(1001, notification)
        } catch (e: Exception) {
            // Service yang sempat stopForeground lalu dipanggil ulang dari latar
            // belakang melempar ForegroundServiceStartNotAllowedException di sini.
            android.util.Log.w("MusicService", "updateMusicNotification startForeground gagal: ${e.message}")
        }
    }

    /** Perbarui hanya waktu dan bilah kemajuan, tanpa membangun ulang bitmap sampul. */
    private fun refreshNotificationProgress() {
        val notification = currentMusicNotification ?: return
        val duration = musicTotalDuration.toLong()
        val position = musicPlaybackPosition.coerceAtLeast(0).toLong()
        val progress = if (duration > 0L) {
            ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        val elapsed = formatPlaybackTime(position)
        val total = formatPlaybackTime(duration)
        // Tampilan ringkas tidak memuat bilah kemajuan; perbarui tampilan penuh.
        val views = notification.bigContentView ?: return
        views.setProgressBar(R.id.notif_progress, 1000, progress, false)
        views.setTextViewText(R.id.notif_elapsed, elapsed)
        views.setTextViewText(R.id.notif_duration, total)
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getNotificationArt(track: MusicTrack): Bitmap? {
        if (notificationArtUri == track.uriStr) return notificationArtBitmap
        notificationArtUri = track.uriStr
        val source = try {
            MainViewModel.instance?.getAlbumArtBitmap(this, track)
        } catch (e: Exception) {
            null
        }
        notificationArtBitmap = source?.let { buildRoundedArt(it) }
        return notificationArtBitmap
    }

    private fun buildRoundedArt(source: Bitmap): Bitmap {
        val size = 192
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 24f, 24f, paint)
        return output
    }

    private fun formatPlaybackTime(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        // Fokus audio harus dilepas lebih dulu: pendengarnya memanggil pauseMusic()
        // lalu updateMusicNotification()/startForeground() pada service yang sudah mati.
        try {
            abandonAudioFocus()
        } catch (e: Exception) {
        }
        progressJob?.cancel()
        mediaPlayer?.let { player ->
            try {
                player.release()
            } catch (e: Exception) {
            }
        }
        mediaPlayer = null
        serviceScope.cancel()
        try {
            mediaSession?.apply {
                isActive = false
                release()
            }
            mediaSession = null
        } catch (e: Exception) {}
        try {
            unregisterReceiver(musicReceiver)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(audioBecomingNoisyReceiver)
        } catch (e: Exception) {}
    }
}
