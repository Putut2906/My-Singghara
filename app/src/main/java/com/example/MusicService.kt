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
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
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
                val folderIndices = musicPlaylist.indices.filter { musicPlaylist[it].artist == currentTrack.artist }
                if (folderIndices.isNotEmpty()) folderIndices else musicPlaylist.indices.toList()
            } else {
                musicPlaylist.indices.toList()
            }
            val remaining = candidates.filter { it != currentTrackIndex }.toMutableList()
            remaining.shuffle()
            shuffleRemainingIndices.addAll(remaining)
        }
    }

    private var mediaPlayer: android.media.MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

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
            when (intent.action) {
                "com.example.music.PREVIOUS" -> playPrevious()
                "com.example.music.PLAY_PAUSE" -> togglePlayPause()
                "com.example.music.NEXT" -> playNext()
                "com.example.music.SHUFFLE" -> {
                    isMusicShuffle = !isMusicShuffle
                    MainViewModel.instance?.isMusicShuffle = isMusicShuffle
                    updateMusicNotification()
                }
            }
        }
    }

    private val audioBecomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            musicReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val noisyFilter = IntentFilter().apply {
            addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            audioBecomingNoisyReceiver,
            noisyFilter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() requires startForeground() within a few seconds
        // even when playback has not started yet, otherwise the system kills us.
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
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

        startForeground(
            1001,
            builder.setContentTitle("My Singghara")
                .setContentText("Menyiapkan pemutar musik...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun syncPlaylist(newList: List<MusicTrack>) {
        musicPlaylist.clear()
        musicPlaylist.addAll(newList)
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
            mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnErrorListener { _, _, _ ->
                    isMusicPlaying = false
                    MainViewModel.instance?.isMusicPlaying = false
                    if (musicPlaylist.size > 1) {
                        playNext()
                    }
                    true
                }
                setDataSource(applicationContext, Uri.parse(track.uriStr))
                prepare()
                requestAudioFocus()
                start()

                musicTotalDuration = duration
                MainViewModel.instance?.musicTotalDuration = duration
                musicPlaybackPosition = 0
                MainViewModel.instance?.musicPlaybackPosition = 0
                isMusicPlaying = true
                MainViewModel.instance?.isMusicPlaying = true

                setOnCompletionListener {
                    handleTrackCompletion()
                }
            }
            startProgressTracker()
            MainViewModel.instance?.recordPlayExternal(track)
            updateMusicNotification()
        } catch (e: Exception) {
            isMusicPlaying = false
            MainViewModel.instance?.isMusicPlaying = false
            if (musicPlaylist.size > 1) {
                playNext()
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
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun getSameFolderTrackIndices(): List<Int> {
        if (musicPlaylist.isEmpty() || currentTrackIndex !in musicPlaylist.indices) return emptyList()
        val currentTrack = musicPlaylist[currentTrackIndex]
        return musicPlaylist.indices.filter { musicPlaylist[it].artist == currentTrack.artist }
    }

    fun playNext() {
        if (musicPlaylist.isEmpty()) return
        val candidates = getSameFolderTrackIndices().ifEmpty { (0 until musicPlaylist.size).toList() }
        if (candidates.isEmpty()) return

        if (candidates.size == 1) {
            playTrack(candidates[0])
            return
        }

        if (isMusicShuffle) {
            shuffleRemainingIndices.retainAll(candidates.toSet())
            if (shuffleRemainingIndices.isEmpty()) {
                val remaining = candidates.filter { it != currentTrackIndex }.toMutableList()
                if (remaining.isEmpty()) remaining.addAll(candidates)
                remaining.shuffle()
                shuffleRemainingIndices.clear()
                shuffleRemainingIndices.addAll(remaining)
            }
            val nextIndex = shuffleRemainingIndices.removeAt(0)
            if (currentTrackIndex in candidates) {
                shuffleHistoryIndices.add(currentTrackIndex)
            }
            playTrack(nextIndex)
        } else {
            val currentPosInFolder = candidates.indexOf(currentTrackIndex)
            if (currentPosInFolder != -1) {
                val nextPosInFolder = (currentPosInFolder + 1) % candidates.size
                playTrack(candidates[nextPosInFolder])
            } else {
                playTrack(candidates[0])
            }
        }
    }

    fun playPrevious() {
        if (musicPlaylist.isEmpty()) return
        val candidates = getSameFolderTrackIndices().ifEmpty { (0 until musicPlaylist.size).toList() }
        if (candidates.isEmpty()) return

        if (candidates.size == 1) {
            playTrack(candidates[0])
            return
        }

        if (isMusicShuffle) {
            shuffleHistoryIndices.retainAll(candidates.toSet())
            if (shuffleHistoryIndices.isNotEmpty()) {
                val prevIndex = shuffleHistoryIndices.removeAt(shuffleHistoryIndices.lastIndex)
                if (currentTrackIndex in candidates) {
                    shuffleRemainingIndices.add(0, currentTrackIndex)
                }
                playTrack(prevIndex)
            } else {
                val currentPosInFolder = candidates.indexOf(currentTrackIndex)
                if (currentPosInFolder != -1) {
                    var prevPosInFolder = currentPosInFolder - 1
                    if (prevPosInFolder < 0) prevPosInFolder = candidates.lastIndex
                    playTrack(candidates[prevPosInFolder])
                } else {
                    playTrack(candidates[0])
                }
            }
        } else {
            val currentPosInFolder = candidates.indexOf(currentTrackIndex)
            if (currentPosInFolder != -1) {
                var prevPosInFolder = currentPosInFolder - 1
                if (prevPosInFolder < 0) prevPosInFolder = candidates.lastIndex
                playTrack(candidates[prevPosInFolder])
            } else {
                playTrack(candidates[0])
            }
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
                    val currentPosInFolder = indices.indexOf(currentTrackIndex)
                    if (currentPosInFolder != -1 && currentPosInFolder < indices.lastIndex) {
                        playTrack(indices[currentPosInFolder + 1])
                    } else {
                        stopPlaybackState()
                    }
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
                    val currentPosInFolder = indices.indexOf(currentTrackIndex)
                    if (currentPosInFolder != -1 && currentPosInFolder < indices.lastIndex) {
                        playTrack(indices[currentPosInFolder + 1])
                    } else {
                        stopPlaybackState()
                    }
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
        mediaPlayer?.pause()
        progressJob?.cancel()
        updateMusicNotification()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch(Dispatchers.Main) {
            while (isMusicPlaying) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            musicPlaybackPosition = player.currentPosition
                            MainViewModel.instance?.musicPlaybackPosition = player.currentPosition
                            updateMediaSessionState()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                delay(100)
            }
        }
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
        stateBuilder.setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
        session.setPlaybackState(stateBuilder.build())
    }

    fun updateMusicNotification() {
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
            stopForeground(true)
            notificationManager.cancel(1001)
            return
        }

        initMediaSession()
        updateMediaSessionState()

        val prevIntent = PendingIntent.getBroadcast(
            this, 1, Intent("com.example.music.PREVIOUS"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            this, 2, Intent("com.example.music.PLAY_PAUSE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            this, 3, Intent("com.example.music.NEXT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val shuffleIntent = PendingIntent.getBroadcast(
            this, 4, Intent("com.example.music.SHUFFLE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "pemutar_musik")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val albumArt = MainViewModel.instance?.getAlbumArtBitmap(this, track)

        val duration = if (mediaPlayer != null) {
            try {
                mediaPlayer!!.duration.toLong()
            } catch (e: Exception) {
                track.durationMs
            }
        } else {
            track.durationMs
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

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }

        val playPauseIcon = if (isMusicPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        builder.setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText("ponsel ini")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isMusicPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && albumArt != null) {
            builder.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(albumArt))
        } else {
            builder.setSmallIcon(android.R.drawable.ic_media_play)
        }

        if (albumArt != null) {
            builder.setLargeIcon(albumArt)
        }

        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Previous",
                prevIntent
            ).build()
        )
        builder.addAction(
            Notification.Action.Builder(
                playPauseIcon,
                "Play/Pause",
                playPauseIntent
            ).build()
        )
        builder.addAction(
            Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Next",
                nextIntent
            ).build()
        )

        val shuffleIcon = if (isMusicShuffle) {
            android.R.drawable.ic_popup_sync
        } else {
            android.R.drawable.ic_menu_rotate
        }
        val shuffleLabel = if (isMusicShuffle) "Shuffle: Aktif" else "Shuffle: Mati"
        builder.addAction(
            Notification.Action.Builder(
                shuffleIcon,
                shuffleLabel,
                shuffleIntent
            ).build()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mediaStyle = Notification.MediaStyle()
                .setShowActionsInCompactView(3, 0, 1, 2)
            mediaSession?.let {
                mediaStyle.setMediaSession(it.sessionToken)
            }
            builder.setStyle(mediaStyle)
        }

        val notification = builder.build()
        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        mediaPlayer?.release()
        mediaPlayer = null
        progressJob?.cancel()
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
