package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.video.VideoAspectRatio
import com.example.ui.video.VideoItem
import com.example.ui.video.VideoPrefs
import com.example.ui.video.buildVideoPlayer
import com.example.ui.video.createShiftedSubtitle
import com.example.ui.video.formatDuration
import com.example.ui.video.videoMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerView(item: VideoItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var decoderMode by remember { mutableStateOf(VideoPrefs.loadDecoder(context)) }
    var subtitleSize by remember { mutableStateOf(VideoPrefs.loadSubtitleSize(context)) }
    var backgroundPlay by remember { mutableStateOf(VideoPrefs.loadBackgroundPlay(context)) }
    var aspectRatio by remember { mutableStateOf(VideoPrefs.loadAspectRatio(context)) }

    var externalSubtitle by remember { mutableStateOf<Uri?>(null) }
    var appliedSubtitle by remember { mutableStateOf<Uri?>(null) }
    var subtitleOffsetMs by remember { mutableStateOf(0L) }
    var recreateKey by remember { mutableIntStateOf(0) }
    var pendingSeekMs by remember { mutableStateOf(0L) }
    var pendingPlay by remember { mutableStateOf(true) }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(item.durationMs) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var textTrackGroups by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var sliderDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(0f) }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var hudText by remember { mutableStateOf<String?>(null) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }

    val player = remember(recreateKey, decoderMode) {
        buildVideoPlayer(context, decoderMode, true).apply {
            setMediaItem(videoMediaItem(item.uri, appliedSubtitle))
            if (pendingSeekMs > 0L) seekTo(pendingSeekMs)
            playWhenReady = pendingPlay
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    DisposableEffect(lifecycleOwner, backgroundPlay, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_RESUME) {
                val pip = activity?.isInPictureInPictureMode == true
                inPip = pip
                if (event == Lifecycle.Event.ON_PAUSE && !backgroundPlay && !pip) {
                    player.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousLightStatusIcons = controller?.isAppearanceLightStatusBars
        val previousLightNavIcons = controller?.isAppearanceLightNavigationBars
        // Ikon status bar dibuat putih agar terbaca di atas video gelap.
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = originalBrightness
                w.attributes = attrs
                val c = WindowInsetsControllerCompat(w, w.decorView)
                if (previousLightStatusIcons != null) {
                    c.isAppearanceLightStatusBars = previousLightStatusIcons
                }
                if (previousLightNavIcons != null) {
                    c.isAppearanceLightNavigationBars = previousLightNavIcons
                }
                c.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Fullscreen immersive: status/navigation bar tampil bersama kontrol dan
    // tersembunyi saat kontrol auto-hide (usap dari tepi atas memunculkannya lagi).
    DisposableEffect(activity, controlsVisible, locked) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!locked && controlsVisible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    val playerListener = remember {
        object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) controlsVisible = true
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage ?: "Video tidak dapat diputar."
            }

            override fun onTracksChanged(tracks: Tracks) {
                textTrackGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            }
        }
    }

    DisposableEffect(player) {
        player.addListener(playerListener)
        onDispose { player.removeListener(playerListener) }
    }

    LaunchedEffect(player, speed) {
        player.setPlaybackSpeed(speed)
    }

    // Status subtitle (dan track pilihan) harus diterapkan ulang setiap player dibuat.
    LaunchedEffect(player, subtitlesEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val reportedDuration = player.duration
            if (reportedDuration > 0L) durationMs = reportedDuration
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            delay(400)
        }
    }

    val anyDialogOpen = showSpeedDialog || showSubtitleDialog || showEqualizerDialog ||
        showDecoderDialog || showAspectDialog || showMoreDialog

    LaunchedEffect(controlsVisible, isPlaying, locked, inPip, anyDialogOpen) {
        if (controlsVisible && isPlaying && !locked && !inPip && !anyDialogOpen) {
            delay(3800)
            controlsVisible = false
        }
    }

    LaunchedEffect(hudText) {
        if (hudText != null) {
            delay(900)
            hudText = null
            hudIcon = null
        }
    }

    fun seekToSafely(target: Long) {
        if (durationMs > 0L) {
            player.seekTo(target.coerceIn(0L, durationMs))
        } else {
            player.seekTo(target.coerceAtLeast(0L))
        }
    }

    fun recreateAtCurrentPosition() {
        pendingSeekMs = player.currentPosition.coerceAtLeast(0L)
        pendingPlay = player.isPlaying
        errorMessage = null
        recreateKey++
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val position = player.currentPosition
            val playing = player.isPlaying
            externalSubtitle = uri
            subtitleOffsetMs = 0L
            scope.launch {
                val shifted = withContext(Dispatchers.IO) {
                    createShiftedSubtitle(context, uri, 0L)
                }
                appliedSubtitle = shifted
                pendingSeekMs = position
                pendingPlay = playing
                errorMessage = null
                recreateKey++
            }
        }
    }

    fun toggleOrientation() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act.isInPictureInPictureMode) return
        act.requestedOrientation =
            if (act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
    }

    fun enterPip() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val videoSize = player.videoSize
                val width = videoSize.width.takeIf { it > 0 } ?: 16
                val height = videoSize.height.takeIf { it > 0 } ?: 9
                act.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(width, height))
                        .build()
                )
            } catch (_: Exception) {
            }
        }
    }

    BackHandler(enabled = locked) { }
    BackHandler(enabled = !locked && !inPip) { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val forcedRatio = aspectRatio.ratio
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = isPlaying
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                    view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize)
                    view.resizeMode = when {
                        aspectRatio == VideoAspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        forcedRatio != null -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    // Jangan cegah layar tidur saat video dijeda; buffering aktif tetap menyala.
                    view.keepScreenOn = isPlaying || (isBuffering && player.playWhenReady)
                },
                modifier = if (forcedRatio != null) {
                    Modifier.aspectRatio(forcedRatio)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked, inPip, durationMs) {
                    if (locked || inPip) return@pointerInput
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (durationMs > 0L) {
                                val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                                seekToSafely(player.currentPosition + delta)
                                hudIcon = if (delta > 0L) Icons.Default.FastForward else Icons.Default.FastRewind
                                hudText = if (delta > 0L) "+10 detik" else "-10 detik"
                                controlsVisible = true
                            }
                        }
                    )
                }
                .pointerInput(locked, inPip, durationMs) {
                    if (locked || inPip) return@pointerInput
                    var mode = 0
                    var accumulatedSeekPx = 0f
                    var brightness = 0.5f
                    var volume = 0
                    var startVolume = 0
                    var volumeAccumPx = 0f
                    var startNearTop = false
                    val revealThresholdPx = 56.dp.toPx()
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    detectDragGestures(
                        onDragStart = { start ->
                            mode = 0
                            accumulatedSeekPx = 0f
                            volumeAccumPx = 0f
                            startNearTop = start.y < revealThresholdPx
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            brightness = if (currentBrightness < 0f) 0.5f else currentBrightness
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            volume = startVolume
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            if (mode == 0) {
                                mode = when {
                                    startNearTop && dragAmount.y > 0f && abs(dragAmount.y) > abs(dragAmount.x) -> 4
                                    abs(dragAmount.y) >= abs(dragAmount.x) ->
                                        if (change.position.x < size.width / 2f) 2 else 3
                                    else -> 1
                                }
                            }
                            when (mode) {
                                4 -> {
                                    controlsVisible = true
                                    hudText = null
                                    hudIcon = null
                                }
                                1 -> {
                                    accumulatedSeekPx += dragAmount.x
                                    val deltaMs = (accumulatedSeekPx / width * 90_000f).toLong()
                                    hudIcon = if (deltaMs >= 0L) Icons.Default.FastForward else Icons.Default.FastRewind
                                    hudText = (if (deltaMs >= 0L) "+" else "-") + formatDuration(abs(deltaMs))
                                }
                                2 -> {
                                    brightness = (brightness - dragAmount.y / height).coerceIn(0.02f, 1f)
                                    activity?.window?.let { window ->
                                        val attrs = window.attributes
                                        attrs.screenBrightness = brightness
                                        window.attributes = attrs
                                    }
                                    hudIcon = Icons.Default.BrightnessHigh
                                    hudText = "Kecerahan ${(brightness * 100).toInt()}%"
                                }
                                else -> {
                                    // Akumulasi delta agar usapan pendek tidak terpotong ke 0.
                                    volumeAccumPx += dragAmount.y
                                    val next = (startVolume - (volumeAccumPx / height * maxVolume).toInt())
                                        .coerceIn(0, maxVolume)
                                    if (next != volume) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                        volume = next
                                    }
                                    hudIcon = if (volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp
                                    val percent = if (maxVolume > 0) volume * 100 / maxVolume else 0
                                    hudText = if (volume == 0) "Suara mati" else "Volume $percent%"
                                }
                            }
                        },
                        onDragEnd = {
                            if (mode == 1) {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val deltaMs = (accumulatedSeekPx / width * 90_000f).toLong()
                                seekToSafely(player.currentPosition + deltaMs)
                            }
                            hudText = null
                            hudIcon = null
                        },
                        onDragCancel = {
                            hudText = null
                            hudIcon = null
                        }
                    )
                }
        )

        if (!inPip) {
            if (isBuffering && !controlsVisible && errorMessage == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(42.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }

            hudText?.let { text ->
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        hudIcon?.let {
                            Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (controlsVisible && !locked) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                externalSubtitle != null -> "Subtitle eksternal • offset ${subtitleOffsetMs} ms"
                                subtitlesEnabled && textTrackGroups.isNotEmpty() -> "Subtitle aktif"
                                else -> "Ketuk layar untuk kontrol"
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { toggleOrientation() },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = if (isLandscape) "Mode potret" else "Mode lanskap"
                        )
                    }
                    IconButton(
                        onClick = {
                            locked = true
                            controlsVisible = false
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Kunci layar")
                    }
                    IconButton(
                        onClick = { showMoreDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    IconButton(
                        onClick = {
                            seekToSafely(player.currentPosition - 10_000L)
                            hudIcon = Icons.Default.FastRewind
                            hudText = "-10 detik"
                        },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Mundur 10 detik", modifier = Modifier.size(34.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f))
                            .clickable {
                                if (player.isPlaying) player.pause() else player.play()
                                controlsVisible = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            seekToSafely(player.currentPosition + 10_000L)
                            hudIcon = Icons.Default.FastForward
                            hudText = "+10 detik"
                        },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "Maju 10 detik", modifier = Modifier.size(34.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDuration(if (sliderDragging) sliderValue.toLong() else positionMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        CompactSeekBar(
                            value = if (sliderDragging) sliderValue else positionMs.toFloat(),
                            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                            onValueChange = {
                                sliderDragging = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                seekToSafely(sliderValue.toLong())
                                sliderDragging = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )
                        Text(
                            text = formatDuration(durationMs),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable { }
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.45f)
                    ) {
                        IconButton(onClick = {
                            locked = false
                            controlsVisible = true
                        }) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = "Buka kunci layar",
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Layar terkunci", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }

            errorMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Video gagal diputar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                errorMessage = null
                                player.prepare()
                                player.play()
                            }) { Text("Coba Lagi") }
                            TextButton(onClick = onClose) { Text("Tutup") }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        SpeedDialog(
            currentSpeed = speed,
            onSelect = {
                speed = it
                player.setPlaybackSpeed(it)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleSettingsDialog(
            subtitleSize = subtitleSize,
            subtitlesEnabled = subtitlesEnabled,
            textTrackGroups = textTrackGroups,
            externalSubtitleName = externalSubtitle?.lastPathSegment?.substringBefore('?'),
            offsetMs = subtitleOffsetMs,
            onToggleSubtitles = { enabled ->
                subtitlesEnabled = enabled
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                    .build()
            },
            onSelectTrack = { index ->
                textTrackGroups.getOrNull(index)?.let { group ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
                        .build()
                    subtitlesEnabled = true
                }
            },
            onSizeChange = { subtitleSize = it },
            onSizeCommit = { VideoPrefs.saveSubtitleSize(context, it) },
            onOffsetChange = { newOffset ->
                val source = externalSubtitle
                if (source != null) {
                    val position = player.currentPosition
                    val playing = player.isPlaying
                    subtitleOffsetMs = newOffset
                    scope.launch {
                        val shifted = withContext(Dispatchers.IO) {
                            createShiftedSubtitle(context, source, newOffset)
                        }
                        appliedSubtitle = shifted
                        pendingSeekMs = position
                        pendingPlay = playing
                        errorMessage = null
                        recreateKey++
                    }
                }
            },
            onPickFile = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) },
            onSearchOnline = {
                openSubtitleSearch(context, item.name)
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            player = player,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showDecoderDialog) {
        DecoderModeDialog(
            current = decoderMode,
            onSelect = { mode ->
                decoderMode = mode
                VideoPrefs.saveDecoder(context, mode)
                showDecoderDialog = false
                recreateAtCurrentPosition()
            },
            onDismiss = { showDecoderDialog = false }
        )
    }

    if (showAspectDialog) {
        AspectRatioDialog(
            current = aspectRatio,
            onSelect = {
                aspectRatio = it
                VideoPrefs.saveAspectRatio(context, it)
                showAspectDialog = false
            },
            onDismiss = { showAspectDialog = false }
        )
    }

    if (showMoreDialog) {
        MoreOptionsDialog(
            backgroundPlay = backgroundPlay,
            speed = speed,
            subtitlesEnabled = subtitlesEnabled,
            decoderLabel = decoderMode.label,
            aspectLabel = aspectRatio.label,
            isLandscape = isLandscape,
            onSpeed = {
                showMoreDialog = false
                showSpeedDialog = true
            },
            onSubtitle = {
                showMoreDialog = false
                showSubtitleDialog = true
            },
            onEqualizer = {
                showMoreDialog = false
                showEqualizerDialog = true
            },
            onDecoder = {
                showMoreDialog = false
                showDecoderDialog = true
            },
            onAspectRatio = {
                showMoreDialog = false
                showAspectDialog = true
            },
            onToggleOrientation = {
                showMoreDialog = false
                toggleOrientation()
            },
            onBackgroundPlayChange = {
                backgroundPlay = it
                VideoPrefs.saveBackgroundPlay(context, it)
            },
            onLock = {
                showMoreDialog = false
                locked = true
                controlsVisible = false
            },
            onPip = {
                showMoreDialog = false
                enterPip()
            },
            onOpenExternal = {
                showMoreDialog = false
                openWithAnotherApp(context, item.uri)
            },
            onShare = {
                showMoreDialog = false
                shareVideo(context, item.uri)
            },
            onDismiss = { showMoreDialog = false }
        )
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = title, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(text = value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeedDialog(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kecepatan Putar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(3).forEach { rowOptions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowOptions.forEach { value ->
                            FilterChip(
                                selected = currentSpeed == value,
                                onClick = { onSelect(value) },
                                label = {
                                    Text(
                                        text = if (value == 1f) "Normal" else "${value}x",
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun SubtitleSettingsDialog(
    subtitleSize: Float,
    subtitlesEnabled: Boolean,
    textTrackGroups: List<Tracks.Group>,
    externalSubtitleName: String?,
    offsetMs: Long,
    onToggleSubtitles: (Boolean) -> Unit,
    onSelectTrack: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onSizeCommit: (Float) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onPickFile: () -> Unit,
    onSearchOnline: () -> Unit,
    onDismiss: () -> Unit
) {
    var size by remember { mutableStateOf(subtitleSize) }
    var offset by remember { mutableStateOf(offsetMs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Subtitle") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan subtitle", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = subtitlesEnabled, onCheckedChange = onToggleSubtitles)
                }

                if (textTrackGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Track bawaan video", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    textTrackGroups.forEachIndexed { index, group ->
                        val format = group.getTrackFormat(0)
                        val label = format.label ?: format.language ?: "Track ${index + 1}"
                        TextButton(onClick = { onSelectTrack(index) }) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Ukuran teks: ${size.toInt()} sp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = size,
                    onValueChange = {
                        size = it
                        onSizeChange(it)
                    },
                    onValueChangeFinished = { onSizeCommit(size) },
                    valueRange = 10f..32f
                )

                if (externalSubtitleName != null) {
                    Text(
                        "Offset subtitle: ${offset} ms",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = offset.toFloat(),
                        onValueChange = { offset = it.toLong() },
                        onValueChangeFinished = { onOffsetChange(offset) },
                        valueRange = -5000f..5000f
                    )
                    Text(
                        "Offset hanya berlaku untuk subtitle eksternal (.srt).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onPickFile) { Text("Pilih file subtitle (.srt)") }
                TextButton(onClick = onSearchOnline) { Text("Cari subtitle online") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun EqualizerDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoost by remember { mutableStateOf<BassBoost?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var bassEnabled by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentPreset by remember { mutableIntStateOf(0) }
    var bassStrength by remember { mutableStateOf(500f) }
    var effectError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        // getAudioSessionId() melempar IllegalStateException begitu player dilepas.
        // Panggilannya sempat berada di luar try, sehingga membuka dialog ini
        // sesudah video terakhir ditutup membuat aplikasi langsung mati.
        val sessionId = try {
            player.audioSessionId
        } catch (_: Exception) {
            0
        }
        if (sessionId > 0) {
            try {
                val eq = Equalizer(0, sessionId)
                eq.enabled = true
                equalizer = eq
                presets = (0 until eq.numberOfPresets.toInt()).map { index ->
                    eq.getPresetName(index.toShort()) ?: "Preset $index"
                }
                currentPreset = eq.currentPreset.toInt().coerceAtLeast(0)
                val boost = try {
                    BassBoost(0, sessionId)
                } catch (_: Exception) {
                    null
                }
                bassBoost = boost
            } catch (_: Exception) {
                effectError = "Equalizer tidak tersedia untuk perangkat ini."
            }
        } else {
            effectError = "Audio belum siap, coba lagi saat video berjalan."
        }
        onDispose {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equalizer & Suara") },
        text = {
            Column(modifier = Modifier.heightIn(max = 430.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aktifkan equalizer", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            equalizer?.enabled = value
                        },
                        enabled = equalizer != null
                    )
                }
                effectError?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (presets.isNotEmpty()) {
                    LazyPresetList(
                        presets = presets,
                        currentPreset = currentPreset,
                        onSelect = { index ->
                            currentPreset = index
                            equalizer?.usePreset(index.toShort())
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bass Boost", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = bassEnabled,
                        onCheckedChange = { value ->
                            bassEnabled = value
                            bassBoost?.enabled = value
                        },
                        enabled = bassBoost != null
                    )
                }
                Slider(
                    value = bassStrength,
                    onValueChange = {
                        bassStrength = it
                        runCatching { bassBoost?.setStrength(it.toInt().toShort()) }
                    },
                    onValueChangeFinished = {
                        runCatching { bassBoost?.setStrength(bassStrength.toInt().toShort()) }
                    },
                    valueRange = 0f..1000f,
                    enabled = bassBoost != null && bassEnabled
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun LazyPresetList(
    presets: List<String>,
    currentPreset: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
    ) {
        presets.forEachIndexed { index, name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = index == currentPreset,
                    onClick = { onSelect(index) }
                )
                Text(name, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AspectRatioDialog(
    current: VideoAspectRatio,
    onSelect: (VideoAspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aspek Rasio") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Pilih bentuk tampilan video. Rasio tertentu meregangkan video agar tepat memenuhi bingkai; \"Isi layar\" memotong tepi video.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                VideoAspectRatio.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) }
                        )
                        Text(
                            text = option.label,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun DecoderModeDialog(
    current: VideoPrefs.DecoderMode,
    onSelect: (VideoPrefs.DecoderMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mesin Dekoder") },
        text = {
            Column {
                Text(
                    "HW memakai GPU perangkat, SW memakai CPU. Pilih SW bila video MKV/4K terasa lagging.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                VideoPrefs.DecoderMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(mode.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                mode.description,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun MoreOptionsDialog(
    backgroundPlay: Boolean,
    speed: Float,
    subtitlesEnabled: Boolean,
    decoderLabel: String,
    aspectLabel: String,
    isLandscape: Boolean,
    onSpeed: () -> Unit,
    onSubtitle: () -> Unit,
    onEqualizer: () -> Unit,
    onDecoder: () -> Unit,
    onAspectRatio: () -> Unit,
    onToggleOrientation: () -> Unit,
    onBackgroundPlayChange: (Boolean) -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opsi Pemutaran") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OptionRow(
                    icon = Icons.Default.Speed,
                    title = "Kecepatan putar",
                    value = if (speed == 1f) "1x" else "${speed}x",
                    onClick = onSpeed
                )
                OptionRow(
                    icon = Icons.Default.Subtitles,
                    title = "Subtitle",
                    value = if (subtitlesEnabled) "Aktif" else "Nonaktif",
                    onClick = onSubtitle
                )
                OptionRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer & suara",
                    onClick = onEqualizer
                )
                OptionRow(
                    icon = Icons.Default.Memory,
                    title = "Mesin dekoder",
                    value = decoderLabel,
                    onClick = onDecoder
                )
                OptionRow(
                    icon = Icons.Default.AspectRatio,
                    title = "Aspek rasio",
                    value = aspectLabel,
                    onClick = onAspectRatio
                )
                OptionRow(
                    icon = Icons.Default.ScreenRotation,
                    title = "Orientasi layar",
                    value = if (isLandscape) "Lanskap" else "Potret",
                    onClick = onToggleOrientation
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Putar di latar belakang", fontSize = 13.sp)
                        Text(
                            "Suara tetap berjalan saat aplikasi diminimalkan.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = backgroundPlay, onCheckedChange = onBackgroundPlayChange)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                OptionRow(
                    icon = Icons.Default.Lock,
                    title = "Kunci layar (Kids Lock)",
                    onClick = onLock
                )
                OptionRow(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = "Popup Play (Picture-in-Picture)",
                    onClick = onPip
                )
                OptionRow(
                    icon = Icons.Default.OpenInNew,
                    title = "Buka dengan aplikasi lain",
                    onClick = onOpenExternal
                )
                OptionRow(
                    icon = Icons.Default.Share,
                    title = "Bagikan video",
                    onClick = onShare
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@Composable
private fun CompactSeekBar(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.White.copy(alpha = 0.32f)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    fun fractionAt(x: Float, width: Float): Float =
        (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(span) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(valueRange.start + fractionAt(offset.x, size.width.toFloat()) * span)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onValueChange(valueRange.start + fractionAt(change.position.x, size.width.toFloat()) * span)
                    },
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() }
                )
            }
            .pointerInput(span) {
                detectTapGestures { offset ->
                    onValueChange(valueRange.start + fractionAt(offset.x, size.width.toFloat()) * span)
                    onValueChangeFinished()
                }
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = 2.5.dp.toPx()
        val thumbRadius = 6.dp.toPx()
        val corner = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
        )
        if (fraction > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width * fraction, trackHeight),
                cornerRadius = corner
            )
        }
        val maxThumbX = (size.width - thumbRadius).coerceAtLeast(thumbRadius)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset((size.width * fraction).coerceIn(thumbRadius, maxThumbX), centerY)
        )
    }
}

private fun openSubtitleSearch(context: Context, videoName: String) {
    try {
        val query = Uri.encode(videoName.substringBeforeLast('.'))
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.opensubtitles.org/en/search/sublanguageid-all/moviename-$query")
            )
        )
    } catch (_: Exception) {
    }
}

private fun openWithAnotherApp(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Buka video dengan"))
    } catch (_: Exception) {
    }
}

private fun shareVideo(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan video"))
    } catch (_: Exception) {
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
