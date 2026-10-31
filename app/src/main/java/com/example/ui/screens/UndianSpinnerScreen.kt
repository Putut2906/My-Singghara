package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Robust, ultra-low latency synthetic sound player for realistic "tek tek" sounds
object TickSoundPlayer {
    private var audioTrack: AudioTrack? = null

    fun initialize() {
        if (audioTrack != null) return
        try {
            val sampleRate = 22050
            val durationMs = 15
            val numSamples = (sampleRate * durationMs / 1000)
            val samples = ShortArray(numSamples)
            
            // Generate a crisp, realistic click sound waveform (wood block tick sound)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Fast swept sine frequency starting at 1500Hz decaying downwards
                val frequency = 1500.0 - (t * 70000.0)
                val envelope = Math.exp(-t * 300.0) // very sharp decay
                val value = Math.sin(2 * Math.PI * frequency * t) * 32767.0 * envelope
                samples[i] = value.toInt().coerceIn(-32768, 32767).toShort()
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(samples, 0, numSamples)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playClick() {
        try {
            audioTrack?.let { track ->
                track.stop()
                track.reloadStaticData()
                track.play()
            }
        } catch (e: Exception) {
            // Fallback initialization if first attempt failed
            initialize()
            try {
                audioTrack?.let { track ->
                    track.stop()
                    track.reloadStaticData()
                    track.play()
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun release() {
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Robust, ultra-low latency synthetic sound player for festive fireworks celebration
object FireworksSoundPlayer {
    private var celebrationTrack: AudioTrack? = null
    private var burstTrack1: AudioTrack? = null
    private var burstTrack2: AudioTrack? = null
    private var isBurstTrack1 = true

    fun initialize() {
        if (celebrationTrack != null) return
        try {
            val sampleRate = 22050
            val rng = kotlin.random.Random(12345)

            // 1. GRAND FESTIVE FIREWORKS CELEBRATION (Durasi ~2.7 detik)
            // Multi-layered: Rocket launch whistle -> Massive boom -> Cascading crackles & pops -> Festive chime harmonics
            val celebrationDurationMs = 2700
            val numCelebrationSamples = (sampleRate * celebrationDurationMs / 1000)
            val celebrationSamples = ShortArray(numCelebrationSamples)

            val popTimes = doubleArrayOf(0.48, 0.62, 0.78, 0.95, 1.15, 1.38, 1.62, 1.88, 2.15)
            val popPitches = doubleArrayOf(260.0, 190.0, 310.0, 220.0, 280.0, 170.0, 340.0, 200.0, 240.0)

            val chordTimes = doubleArrayOf(0.65, 0.95, 1.30, 1.65)
            val chordFreqs = doubleArrayOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6 (Fanfare kemeriahan)

            for (i in 0 until numCelebrationSamples) {
                val t = i.toDouble() / sampleRate
                var sample = 0.0

                // Layer 1: Rocket Launch Whistle & Whoosh (0.00s - 0.38s)
                if (t < 0.38) {
                    val progress = t / 0.38
                    val freq = 420.0 + (progress * progress * 2100.0)
                    val env = Math.sin(progress * Math.PI * 0.5) * (1.0 - progress * 0.15)
                    val whooshNoise = (rng.nextDouble() * 2.0 - 1.0) * 0.18
                    sample += (Math.sin(2.0 * Math.PI * freq * t) * 0.45 + whooshNoise) * env
                }

                // Layer 2: Grand Boom Explosion (0.38s onwards)
                if (t >= 0.38) {
                    val boomT = t - 0.38
                    val boomEnv = Math.exp(-boomT * 3.0)
                    // Sub-bass resonant punch
                    val boomFreq = 92.0 * Math.exp(-boomT * 2.2) + 36.0
                    val bass = Math.sin(2.0 * Math.PI * boomFreq * boomT) * boomEnv * 0.65

                    // Noise explosion rumble
                    val noiseEnv = Math.exp(-boomT * 5.8)
                    val noise = (rng.nextDouble() * 2.0 - 1.0) * noiseEnv * 0.50

                    // Low ambient trail
                    val rumble = Math.sin(2.0 * Math.PI * 48.0 * boomT) * Math.exp(-boomT * 1.5) * 0.22

                    sample += bass + noise + rumble
                }

                // Layer 3: Cascading Crackles & Sparkle Pops (Mercon & Kembang Api Beruntun)
                for (pIdx in popTimes.indices) {
                    val pTime = popTimes[pIdx]
                    if (t >= pTime) {
                        val pT = t - pTime
                        if (pT < 0.25) {
                            val popEnv = Math.exp(-pT * 24.0)
                            val popFreq = popPitches[pIdx] * Math.exp(-pT * 10.0)
                            val popTone = Math.sin(2.0 * Math.PI * popFreq * pT) * 0.45
                            val popNoise = (rng.nextDouble() * 2.0 - 1.0) * 0.55
                            val sparkShimmer = Math.sin(2.0 * Math.PI * (2200.0 + pIdx * 180.0) * pT) * 0.25
                            sample += (popTone + popNoise + sparkShimmer) * popEnv * 0.42
                        }
                    }
                }

                // Layer 4: Celebration Fanfare Harmonics
                for (cIdx in chordTimes.indices) {
                    val cTime = chordTimes[cIdx]
                    if (t >= cTime) {
                        val cT = t - cTime
                        if (cT < 0.75) {
                            val bellEnv = Math.exp(-cT * 4.2)
                            val f = chordFreqs[cIdx]
                            val chime = (Math.sin(2.0 * Math.PI * f * cT) + 0.3 * Math.sin(4.0 * Math.PI * f * cT)) * bellEnv
                            sample += chime * 0.25
                        }
                    }
                }

                val clamped = (sample * 27000.0).coerceIn(-32767.0, 32767.0)
                celebrationSamples[i] = clamped.toInt().toShort()
            }

            celebrationTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numCelebrationSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            celebrationTrack?.write(celebrationSamples, 0, numCelebrationSamples)

            // 2. CRISP FIREWORK BURST POP (Durasi ~420ms untuk letupan kembang api berkala)
            val burstDurationMs = 420
            val numBurstSamples = (sampleRate * burstDurationMs / 1000)
            val burstSamples = ShortArray(numBurstSamples)
            for (i in 0 until numBurstSamples) {
                val t = i.toDouble() / sampleRate
                val boomEnv = Math.exp(-t * 11.0)
                val freq = 175.0 * Math.exp(-t * 8.5) + 52.0
                val bass = Math.sin(2.0 * Math.PI * freq * t) * boomEnv * 0.52
                val noise = (rng.nextDouble() * 2.0 - 1.0) * Math.exp(-t * 20.0) * 0.48
                val crackle = if (t > 0.07 && t < 0.26) (rng.nextDouble() * 2.0 - 1.0) * Math.exp(-(t - 0.07) * 15.0) * 0.38 else 0.0
                val s = (bass + noise + crackle) * 25000.0
                burstSamples[i] = s.coerceIn(-32767.0, 32767.0).toInt().toShort()
            }

            fun createBurstTrack(): AudioTrack {
                val trk = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numBurstSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                trk.write(burstSamples, 0, numBurstSamples)
                return trk
            }

            burstTrack1 = createBurstTrack()
            burstTrack2 = createBurstTrack()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCelebration() {
        try {
            if (celebrationTrack == null) initialize()
            celebrationTrack?.let { track ->
                track.stop()
                track.reloadStaticData()
                track.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playBurst() {
        try {
            if (burstTrack1 == null) initialize()
            val track = if (isBurstTrack1) burstTrack1 else burstTrack2
            isBurstTrack1 = !isBurstTrack1
            track?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            celebrationTrack?.stop()
            burstTrack1?.stop()
            burstTrack2?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            celebrationTrack?.release()
            burstTrack1?.release()
            burstTrack2?.release()
            celebrationTrack = null
            burstTrack1 = null
            burstTrack2 = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// Particle class for Fireworks and Confetti animations
class RaffleParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var alpha: Float = 1.0f,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var type: Int = 0 // 0: Confetti, 1: Firework Sparkle
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UndianSpinnerScreen(
    onBackClick: () -> Unit
) {
    BackHandler {
        onBackClick()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    // Setup sound players on compose entry
    LaunchedEffect(Unit) {
        TickSoundPlayer.initialize()
        FireworksSoundPlayer.initialize()
    }

    DisposableEffect(Unit) {
        onDispose {
            TickSoundPlayer.release()
            FireworksSoundPlayer.release()
        }
    }

    // Vibrant colors for wheel segments
    val segmentColors = listOf(
        Color(0xFFFF5964), // Coral Red
        Color(0xFF35A7FF), // Bright Blue
        Color(0xFF38B000), // Lime Green
        Color(0xFFF9C80E), // Vivid Yellow
        Color(0xFF8338EC), // Purple
        Color(0xFFFF70A6), // Pink
        Color(0xFF06D6A0), // Teal
        Color(0xFFF77F00), // Orange
        Color(0xFF48CAE4), // Sky Blue
        Color(0xFF7209B7)  // Deep Violet
    )

    var popupToastMessage by remember { mutableStateOf<String?>(null) }

    // Option list state: starts empty as requested for manual input
    var optionsList by remember {
        mutableStateOf(emptyList<String>())
    }

    // Modal dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }
    var showWinnerDialog by remember { mutableStateOf(false) }
    var winnerName by remember { mutableStateOf("") }

    // Spin animation states
    val rotationAngle = remember { Animatable(0f) }
    var isSpinning by remember { mutableStateOf(false) }

    // Particle manager state
    val particles = remember { mutableStateListOf<RaffleParticle>() }

    // Calculate current winner index helper
    fun getWinningIndex(angle: Float, count: Int): Int {
        if (count == 0) return 0
        val sweep = 360f / count
        // Indicator is at top center (270 degrees)
        val normalizedAngle = (angle % 360f + 360f) % 360f
        val pointerTarget = (270f - normalizedAngle + 360f) % 360f
        val index = (pointerTarget / sweep).toInt()
        return index.coerceIn(0, count - 1)
    }

    // Trigger tick sound when spinner crosses sector boundaries
    var lastIndexedByAngle by remember { mutableStateOf(0) }
    LaunchedEffect(rotationAngle.value) {
        if (isSpinning && optionsList.isNotEmpty()) {
            val currentIndex = getWinningIndex(rotationAngle.value, optionsList.size)
            if (currentIndex != lastIndexedByAngle) {
                lastIndexedByAngle = currentIndex
                TickSoundPlayer.playClick() // "tek tek" sound effect
            }
        }
    }

    // Particle updater loop when winner dialog is displayed
    LaunchedEffect(showWinnerDialog) {
        if (showWinnerDialog) {
            FireworksSoundPlayer.playCelebration()
            particles.clear()
            
            // Seed initial beautiful falling confetti (kertas warna-warni)
            for (i in 0..100) {
                particles.add(
                    RaffleParticle(
                        x = Random.nextFloat() * 1000f,
                        y = -100f - Random.nextFloat() * 500f,
                        vx = (Random.nextFloat() - 0.5f) * 6f,
                        vy = Random.nextFloat() * 8f + 5f,
                        color = listOf(
                            Color(0xFFFF5964), Color(0xFF35A7FF), Color(0xFF38B000),
                            Color(0xFFF9C80E), Color(0xFF8338EC), Color(0xFFFF70A6),
                            Color(0xFF00F5D4), Color(0xFFF77F00)
                        ).random(),
                        size = Random.nextFloat() * 18f + 10f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = (Random.nextFloat() - 0.5f) * 12f,
                        type = 0 // Confetti
                    )
                )
            }

            // Launch first fireworks explosions immediately
            for (f in 0..2) {
                val fX = 150f + Random.nextFloat() * 700f
                val fY = 150f + Random.nextFloat() * 500f
                val fColor = listOf(
                    Color(0xFFFFD700), Color(0xFFFF007F), Color(0xFF00F5D4),
                    Color(0xFF9D4EDD), Color(0xFF00E5FF), Color(0xFFFF9E00)
                ).random()
                for (j in 0..30) {
                    val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                    val speed = Random.nextFloat() * 12f + 4f
                    particles.add(
                        RaffleParticle(
                            x = fX,
                            y = fY,
                            vx = cos(angle) * speed,
                            vy = sin(angle) * speed,
                            color = fColor,
                            size = Random.nextFloat() * 8f + 4f,
                            alpha = 1.0f,
                            type = 1 // Fireworks
                        )
                    )
                }
            }

            // Continuous animation frame loop
            var lastTime = System.currentTimeMillis()
            while (showWinnerDialog) {
                withFrameMillis { frameTime ->
                    val now = System.currentTimeMillis()
                    val dt = (now - lastTime) / 1000f
                    lastTime = now

                    // Spawn occasional new fireworks in the background
                    if (Random.nextFloat() < 0.05f && particles.filter { it.type == 1 }.size < 90) {
                        FireworksSoundPlayer.playBurst()
                        val fireworkX = 100f + Random.nextFloat() * 800f
                        val fireworkY = 200f + Random.nextFloat() * 600f
                        val fireworkColor = listOf(
                            Color(0xFFFFD700), Color(0xFFE0115F), Color(0xFF00FFCC),
                            Color(0xFF9D4EDD), Color(0xFFFF3366), Color(0xFF33CCFF)
                        ).random()
                        for (j in 0..25) {
                            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                            val speed = Random.nextFloat() * 14f + 3f
                            particles.add(
                                RaffleParticle(
                                    x = fireworkX,
                                    y = fireworkY,
                                    vx = cos(angle) * speed,
                                    vy = sin(angle) * speed,
                                    color = fireworkColor,
                                    size = Random.nextFloat() * 7f + 3f,
                                    alpha = 1.0f,
                                    type = 1 // Fireworks
                                )
                            )
                        }
                    }

                    // Update particle trajectories
                    val iterator = particles.iterator()
                    while (iterator.hasNext()) {
                        val p = iterator.next()
                        p.x += p.vx
                        p.y += p.vy

                        if (p.type == 0) {
                            p.vy += 0.05f // low gravity for paper
                            p.vx += sin(now.toDouble() / 150.0 + p.size).toFloat() * 0.12f // air sway
                            p.rotation += p.rotationSpeed
                            // Wrap or respawn paper at top if it hits the bottom
                            if (p.y > 2000f) {
                                p.y = -50f
                                p.x = Random.nextFloat() * 1000f
                                p.vy = Random.nextFloat() * 7f + 4f
                            }
                        } else {
                            p.vy += 0.08f // gravity on sparkles
                            p.alpha -= 0.018f
                            p.size *= 0.97f
                            if (p.alpha <= 0f || p.size < 1f) {
                                iterator.remove()
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!isSpinning) {
                ExtendedFloatingActionButton(
                    text = { Text("Tambah Opsi", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Tambah") },
                    onClick = {
                        newItemText = ""
                        showAddDialog = true
                    },
                    modifier = Modifier.testTag("add_undian_fab")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        )
                    )
                )
        ) {
            // Main content layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive Spinner Wheel Container
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Background Wheel Frame & Radial Shadows
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(16.dp, CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF2C3E50), Color(0xFF1A252F))
                                ),
                                CircleShape
                            )
                            .border(8.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )

                    // Rotatable Wheel Canvas drawing actual parts or a placeholder
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // If empty, render 4 beautiful clean placeholder sectors to maintain visual layout
                        val count = if (optionsList.isEmpty()) 4 else optionsList.size
                        val sweepAngle = 360f / count
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        withTransform({
                            rotate(degrees = rotationAngle.value, pivot = center)
                        }) {
                            for (i in 0 until count) {
                                val startAngle = i * sweepAngle
                                drawArc(
                                    color = if (optionsList.isEmpty()) {
                                        // Dynamic elegant greys/pastels when list is empty
                                        listOf(
                                            Color(0xFF34495E),
                                            Color(0xFF2C3E50),
                                            Color(0xFF5D6D7E),
                                            Color(0xFF7F8C8D)
                                        )[i % 4]
                                    } else {
                                        segmentColors[i % segmentColors.size]
                                    },
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    size = Size(size.width, size.height)
                                )

                                // Radial Sector Divider line
                                val lineRad = Math.toRadians(startAngle.toDouble())
                                drawLine(
                                    color = Color.White.copy(alpha = 0.35f),
                                    start = center,
                                    end = Offset(
                                        (center.x + cos(lineRad) * radius).toFloat(),
                                        (center.y + sin(lineRad) * radius).toFloat()
                                    ),
                                    strokeWidth = 2.dp.toPx()
                                )

                                // Render Sector labels if populated
                                if (optionsList.isNotEmpty()) {
                                    val textAngle = startAngle + (sweepAngle / 2f)
                                    withTransform({
                                        rotate(degrees = textAngle, pivot = center)
                                    }) {
                                        val rawText = optionsList[i]
                                        val maxLength = 10
                                        val displayText = if (rawText.length > maxLength) {
                                            rawText.take(maxLength - 2) + ".."
                                        } else rawText

                                        val style = TextStyle(
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        )

                                        val textLayoutResult = textMeasurer.measure(
                                            text = displayText,
                                            style = style,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val textHeight = textLayoutResult.size.height

                                        // Center text nicely in outer half of the sector
                                        val textOffset = Offset(
                                            x = center.x + (radius * 0.40f),
                                            y = center.y - (textHeight / 2f)
                                        )

                                        drawText(
                                            textLayoutResult = textLayoutResult,
                                            topLeft = textOffset
                                        )
                                    }
                                } else {
                                    // Placeholder "?" symbols in segments
                                    val textAngle = startAngle + (sweepAngle / 2f)
                                    withTransform({
                                        rotate(degrees = textAngle, pivot = center)
                                    }) {
                                        val style = TextStyle(
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        val textLayoutResult = textMeasurer.measure("?", style = style)
                                        val textHeight = textLayoutResult.size.height
                                        val textOffset = Offset(
                                            x = center.x + (radius * 0.45f),
                                            y = center.y - (textHeight / 2f)
                                        )
                                        drawText(textLayoutResult, topLeft = textOffset)
                                    }
                                }
                            }
                        }
                    }

                    // Downward-pointing Pointer Arrow (Top Center outside rotating wheel)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 2.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(width = 32.dp, height = 28.dp)
                        ) {
                            val path = Path().apply {
                                moveTo(size.width / 2f, size.height) // Tip pointing down
                                lineTo(0f, 0f)                        // Top left
                                lineTo(size.width, 0f)               // Top right
                                close()
                            }
                            drawPath(path, color = Color(0xFFFF5964))
                            drawPath(
                                path,
                                color = Color.White,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // CRITICAL: Play arrow / Spin button moved directly to the central hub!
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .shadow(8.dp, CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isSpinning) {
                                        listOf(Color(0xFFBFC9CA), Color(0xFF7F8C8D))
                                    } else {
                                        listOf(Color(0xFFFFD700), Color(0xFFFFA000))
                                    }
                                ),
                                CircleShape
                            )
                            .border(3.dp, Color.White, CircleShape)
                            .clickable {
                                if (optionsList.isEmpty()) {
                                    // Let users know they should add options, and open popup immediately
                                    popupToastMessage = "Tambahkan opsi terlebih dahulu!"
                                    newItemText = ""
                                    showAddDialog = true
                                } else if (!isSpinning) {
                                    scope.launch {
                                        isSpinning = true

                                        // Decelerating Physics Spin Algorithm
                                        val randomTurns = 7 + Random.nextInt(5)
                                        val finalOffset = Random.nextFloat() * 360f
                                        val targetRotation =
                                            rotationAngle.value + (360f * randomTurns) + finalOffset

                                        rotationAngle.animateTo(
                                            targetValue = targetRotation,
                                            animationSpec = tween(
                                                durationMillis = 5200,
                                                easing = CubicBezierEasing(0.12f, 0.88f, 0.18f, 1f)
                                            )
                                        )

                                        val winIdx = getWinningIndex(rotationAngle.value, optionsList.size)
                                        winnerName = optionsList[winIdx]
                                        isSpinning = false
                                        FireworksSoundPlayer.playCelebration()
                                        showWinnerDialog = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, // Melingkar arrow icon
                            contentDescription = "Putar",
                            tint = Color(0xFF1B1D28),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Subtitle helper
                Text(
                    text = if (optionsList.isEmpty()) "Klik tombol emas tengah atau tambah opsi!" else "Tekan tombol emas tengah untuk mengundi!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                // Quick List Title Header
                Text(
                    text = "Daftar Opsi Undian (${optionsList.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                // Options list container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                ) {
                    if (optionsList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Daftar kosong.\nSilakan klik + Tambah Opsi di bawah!",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(optionsList) { index, option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            segmentColors[index % segmentColors.size].copy(alpha = 0.4f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(segmentColors[index % segmentColors.size])
                                        )
                                        Text(
                                            text = option,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!isSpinning) {
                                        IconButton(
                                            onClick = {
                                                optionsList =
                                                    optionsList.filterIndexed { idx, _ -> idx != index }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // High Performance Fireworks & Confetti Overlay Layer
            if (showWinnerDialog) {
                var actualCanvasSize by remember { mutableStateOf(Size(1080f, 1920f)) }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    actualCanvasSize = size
                    particles.forEach { p ->
                        if (p.type == 0) {
                            // Render rotating, glittering paper confetti (kertas kecil warna-warni)
                            withTransform({
                                translate(left = p.x.coerceIn(0f, size.width), top = p.y)
                                rotate(degrees = p.rotation)
                            }) {
                                drawRect(
                                    color = p.color.copy(alpha = p.alpha),
                                    topLeft = Offset(-p.size / 2f, -p.size / 4f),
                                    size = Size(p.size, p.size / 2f)
                                )
                            }
                        } else {
                            // Render exploding sparkling firework sparks (kembang api)
                            drawCircle(
                                color = p.color.copy(alpha = p.alpha),
                                radius = p.size,
                                center = Offset(p.x.coerceIn(0f, size.width), p.y)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to Add a Raffle Option
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah Opsi Undian", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Masukkan teks pilihan atau nama yang ingin dimasukkan ke dalam roda undian:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newItemText,
                        onValueChange = { newItemText = it },
                        placeholder = { Text("Contoh: Nasi Goreng") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_option_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanText = newItemText.trim()
                        if (cleanText.isEmpty()) {
                            popupToastMessage = "Input tidak boleh kosong!"
                        } else if (optionsList.contains(cleanText)) {
                            popupToastMessage = "Opsi sudah ada!"
                        } else {
                            optionsList = optionsList + cleanText
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_option")
                ) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Beautiful winner announcement dialog with elegant confetti particles falling behind
    if (showWinnerDialog) {
        AlertDialog(
            onDismissRequest = {
                FireworksSoundPlayer.stop()
                showWinnerDialog = false
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            modifier = Modifier
                .padding(24.dp)
                .wrapContentSize(),
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            FireworksSoundPlayer.stop()
                            showWinnerDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(44.dp)
                            .testTag("winner_ok_button")
                    ) {
                        Text("MANTAP!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Pemenang",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "HASIL UNDIAN",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Selamat! Roda spinner berhenti pada pilihan:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, Color(0xFFFFD700), RoundedCornerShape(16.dp))
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = winnerName,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                style = TextStyle(lineHeight = 32.sp)
                            )
                        }
                    }

                    Text(
                        text = "Pilihan di atas ditentukan secara acak & adil.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }

    if (popupToastMessage != null) {
        com.example.ui.components.SpringToastPopup(
            message = popupToastMessage,
            onDismiss = { popupToastMessage = null }
        )
    }
}
