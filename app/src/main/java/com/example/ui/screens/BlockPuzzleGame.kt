package com.example.ui.screens

import com.example.ui.MainViewModel

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

// Template untuk Shape
data class PuzzleShapeTemplate(
    val id: Int,
    val coords: List<Pair<Int, Int>>,
    val colorIndex: Int
)

// Shape Aktif Game
data class PuzzleShape(
    val templateId: Int,
    val relativeCoordinates: List<Pair<Int, Int>>,
    val color: Color,
    val colorIndex: Int,
    var rotationCount: Int = 0
)

// Sound Helper menggunakan modern pure sine wave synthesis via AudioTrack
object GameSound {
    var isSoundEnabled = true
    var isMusicEnabled = false
    private var musicJob: kotlinx.coroutines.Job? = null

    // Generates a pure, smooth sine wave sound asynchronously
    fun playSine(frequency: Double, durationMs: Int, volume: Float = 0.12f) {
        if (!isSoundEnabled) return
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var audioTrack: android.media.AudioTrack? = null
            try {
                val sampleRate = 16000
                val numSamples = (durationMs * sampleRate / 1000)
                val generatedSnd = ByteArray(2 * numSamples)
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / frequency)
                    val value = Math.sin(angle)
                    val valShort = (value * 32767 * volume).toInt().coerceIn(-32768, 32767).toShort()
                    val idx = 2 * i
                    generatedSnd[idx] = (valShort.toInt() and 0x00ff).toByte()
                    generatedSnd[idx + 1] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
                }
                audioTrack = android.media.AudioTrack(
                    android.media.AudioManager.STREAM_MUSIC,
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT,
                    generatedSnd.size,
                    android.media.AudioTrack.MODE_STATIC
                )
                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                
                // Wait for tone duration + safety margin, then release
                delay(durationMs + 100L)
            } catch (_: Exception) {
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun playTap() {
        playSine(523.25, 45, 0.12f) // C5
    }

    fun playDrop() {
        // Double sweet note for block placement click (G4 then C5 rapidly)
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                playSine(392.00, 45, 0.15f) // G4
                delay(40)
                playSine(523.25, 65, 0.15f) // C5
            } catch (_: Exception) {}
        }
    }

    fun playClear() {
        // Joyful clear chord arpeggio (C5 -> E5 -> G5 -> C6)
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val notes = listOf(523.25, 659.25, 783.99, 1046.50)
                for (note in notes) {
                    playSine(note, 80, 0.12f)
                    delay(60)
                }
            } catch (_: Exception) {}
        }
    }

    fun playPowerUp() {
        // Uplifting magic hover chime
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val notes = listOf(587.33, 783.99, 1174.66) // D5 -> G5 -> D6
                for (note in notes) {
                    playSine(note, 120, 0.1f)
                    delay(80)
                }
            } catch (_: Exception) {}
        }
    }

    fun playGameOver() {
        // Melancholic beautiful descending synth outro
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val notes = listOf(440.00, 392.00, 329.63, 261.63) // A4 -> G4 -> E4 -> C4
                for (note in notes) {
                    playSine(note, 220, 0.12f)
                    delay(180)
                }
            } catch (_: Exception) {}
        }
    }

    fun playFireworks() {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // Low rumble bass boom
                playSine(90.0, 180, 0.35f)
                delay(80)
                playSine(130.0, 150, 0.30f)
                delay(120)
                // Crackling fireworks sparkles
                val sparkles = listOf(1046.50, 1318.51, 1567.98, 2093.00, 1760.00)
                for (spark in sparkles) {
                    playSine(spark, 45, 0.15f)
                    delay(40)
                }
            } catch (_: Exception) {}
        }
    }

    fun playGG() {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // High-energy positive chiptune arpeggio for "GG"
                playSine(587.33, 90, 0.20f)  // D5
                delay(70)
                playSine(880.00, 90, 0.20f)  // A5
                delay(100)
                playSine(1174.66, 250, 0.22f) // D6 (triumphant sustain)
            } catch (_: Exception) {}
        }
    }

    fun startMusic(scope: kotlinx.coroutines.CoroutineScope) {
        if (musicJob != null && musicJob?.isActive == true) return
        isMusicEnabled = true
        musicJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // Delightful looping musical pentatonic progression for in-game music (very soft in background)
            val chords = listOf(
                listOf(261.63, 329.63, 392.00), // C Major
                listOf(220.00, 261.63, 329.63), // A minor
                listOf(174.61, 220.00, 261.63), // F Major
                listOf(196.00, 246.94, 293.66)  // G Major
            )
            try {
                while (isActive && isMusicEnabled) {
                    for (chord in chords) {
                        if (!isMusicEnabled || !isActive) break
                        for (note in chord) {
                            if (!isMusicEnabled) break
                            playSine(note, 450, 0.04f) // Extremely ambient and calming soft volume
                            delay(140)
                        }
                        delay(2400) // Beautiful atmospheric breathing pause
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun stopMusic() {
        isMusicEnabled = false
        musicJob?.cancel()
        musicJob = null
    }
}

// Daftar Template Shape Block Puzzle
val templates = listOf(
    PuzzleShapeTemplate(id = 0, coords = listOf(Pair(0, 0)), colorIndex = 1), // Dot (Golden yellow)
    PuzzleShapeTemplate(id = 1, coords = listOf(Pair(0, 0), Pair(0, 1)), colorIndex = 2), // 1x2 Horizontal (Cyan)
    PuzzleShapeTemplate(id = 2, coords = listOf(Pair(0, 0), Pair(1, 0)), colorIndex = 2), // 2x1 Vertical (Cyan)
    PuzzleShapeTemplate(id = 3, coords = listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2)), colorIndex = 3), // 1x3 Horizontal (Magenta)
    PuzzleShapeTemplate(id = 4, coords = listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0)), colorIndex = 3), // 3x1 Vertical (Magenta)
    PuzzleShapeTemplate(id = 5, coords = listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(0, 3)), colorIndex = 4), // 1x4 Horizontal (Green)
    PuzzleShapeTemplate(id = 6, coords = listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(3, 0)), colorIndex = 4), // 4x1 Vertical (Green)
    PuzzleShapeTemplate(id = 7, coords = listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0), Pair(1, 1)), colorIndex = 5), // Square 2x2 (Royal Blue)
    PuzzleShapeTemplate(id = 8, coords = listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 1)), colorIndex = 6), // T-shape Rotated (Purple)
    PuzzleShapeTemplate(id = 9, coords = listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1)), colorIndex = 7), // Corner 2x2 (Coral Red)
    PuzzleShapeTemplate(id = 10, coords = listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(2, 1), Pair(2, 2)), colorIndex = 8), // Corner 3x3 Large (Orange)
    PuzzleShapeTemplate(id = 11, coords = listOf(Pair(0, 0), Pair(0, 1), Pair(1, 1), Pair(1, 2)), colorIndex = 9), // Z-shape (Teal Accent)
    PuzzleShapeTemplate(id = 12, coords = listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, 2)), colorIndex = 6) // T-shape Normal (Purple)
)

// Peta Kode Warna
fun getColorForIndex(colorIdx: Int): Color {
    return when (colorIdx) {
        1 -> Color(0xFFFBC02D) // Amber / Golden Yellow
        2 -> Color(0xFF00BCD4) // Cyan / Blue-Green
        3 -> Color(0xFFEC407A) // Pink / Magenta
        4 -> Color(0xFF4CAF50) // Green
        5 -> Color(0xFF1E88E5) // Royal Blue
        6 -> Color(0xFFAB47BC) // Purple
        7 -> Color(0xFFFF7043) // Coral / Orange-Red
        8 -> Color(0xFFFF9800) // Orange
        9 -> Color(0xFF009688) // Teal
        else -> Color(0xFF9E9E9E)
    }
}

// Rotate helper function
fun rotateShape(shape: PuzzleShape): PuzzleShape {
    val rotatedCoords = shape.relativeCoordinates.map { (r, c) -> Pair(c, -r) }
    val minR = rotatedCoords.minOf { it.first }
    val minC = rotatedCoords.minOf { it.second }
    val normalized = rotatedCoords.map { (r, c) -> Pair(r - minR, c - minC) }
    return shape.copy(
        relativeCoordinates = normalized,
        rotationCount = (shape.rotationCount + 1) % 4
    )
}

data class FireworkParticle(
    val vx: Float,
    val vy: Float,
    val color: Color,
    val size: Float,
    val speed: Float,
    val angle: Double
)

data class ActiveFirework(
    val id: Long,
    val x: Float, // horizontal center fraction (0f - 1f)
    val y: Float, // vertical center fraction (0f - 1f)
    val particles: List<FireworkParticle>,
    val startTime: Long,
    val durationMs: Long = 1800L
)

fun createFirework(id: Long, x: Float, y: Float): ActiveFirework {
    val particleCount = 50
    val particles = mutableListOf<FireworkParticle>()
    val colors = listOf(
        Color(0xFFFF1744), // Neon Red
        Color(0xFFFFEB3B), // Vibrant Yellow
        Color(0xFF00E676), // Bright Green
        Color(0xFF29B6F6), // Sky Blue
        Color(0xFFFF00E6), // Hot Pink
        Color(0xFFFF9100), // Vibrant Orange
        Color(0xFFD500F9)  // Electric Purple
    )
    for (i in 0 until particleCount) {
        val angle = Math.random() * 2.0 * Math.PI
        val speed = 0.2f + (Math.random().toFloat() * 0.8f) // varied speeds
        val vx = (Math.cos(angle) * speed).toFloat()
        val vy = (Math.sin(angle) * speed).toFloat()
        val color = colors.random()
        val size = 4f + (Math.random().toFloat() * 6f) // 4 to 10 pixels
        particles.add(FireworkParticle(vx, vy, color, size, speed, angle))
    }
    return ActiveFirework(id, x, y, particles, System.currentTimeMillis())
}


@Composable
fun BlockPuzzleGame(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val liftOffsetPx = remember(density) { with(density) { -80.dp.toPx() } }
    val prefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var isPaused by remember { mutableStateOf(false) }

    // Sound status
    var isSoundEnabled by remember { mutableStateOf(prefs.getBoolean("puzzle_sound", true)) }
    LaunchedEffect(isSoundEnabled, isPaused) {
        GameSound.isSoundEnabled = isSoundEnabled && !isPaused
        prefs.edit().putBoolean("puzzle_sound", isSoundEnabled).apply()
    }

    // Music status
    var isMusicEnabled by remember { mutableStateOf(prefs.getBoolean("puzzle_music", true)) }
    LaunchedEffect(isMusicEnabled, isPaused) {
        prefs.edit().putBoolean("puzzle_music", isMusicEnabled).apply()
        if (isMusicEnabled && !isPaused) {
            GameSound.startMusic(scope)
        } else {
            GameSound.stopMusic()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            GameSound.stopMusic()
        }
    }

    // High Score
    var highScore by remember { mutableStateOf(prefs.getInt("puzzle_high_score", 0)) }

    // Active board (8x8)
    val board = remember { mutableStateListOf<MutableList<Int>>().apply {
        val loaded = loadBoard(prefs)
        for (r in 0 until 8) {
            val rowList = mutableStateListOf<Int>()
            for (c in 0 until 8) {
                rowList.add(loaded[r][c])
            }
            add(rowList)
        }
    }}

    // Current Score
    var score by remember { mutableStateOf(prefs.getInt("puzzle_current_score", 0)) }

    // Animated score scaling and milestone tracking on increments
    val scoreScaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    var lastScore by remember { mutableStateOf(score) }
    val scoreMilestones = remember { listOf(250, 720, 2000, 3500, 5000, 7000) }
    var activeMilestoneCelebration by remember { mutableStateOf<Int?>(null) }
    var activeFireworks by remember { mutableStateOf<List<ActiveFirework>>(emptyList()) }
    var animationTick by remember { mutableStateOf(0L) }

    LaunchedEffect(activeFireworks) {
        if (activeFireworks.isNotEmpty()) {
            while (true) {
                val now = System.currentTimeMillis()
                val updated = activeFireworks.filter { now - it.startTime < it.durationMs }
                if (updated.size != activeFireworks.size) {
                    activeFireworks = updated
                }
                if (activeFireworks.isEmpty()) break
                animationTick = now
                kotlinx.coroutines.delay(16)
            }
        }
    }

    var addedScoreAmount by remember { mutableStateOf(0) }
    var addedScoreTrigger by remember { mutableStateOf(0) }

    val floatAnim = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(score) {
        if (score > lastScore) {
            val increment = score - lastScore
            addedScoreAmount = increment
            addedScoreTrigger += 1

            // Trigger score pulse animation
            scoreScaleAnim.animateTo(
                targetValue = 1.35f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                )
            )
            scoreScaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )

            // Check milestone targets reached!
            for (milestone in scoreMilestones) {
                if (lastScore < milestone && score >= milestone) {
                    activeMilestoneCelebration = milestone
                    GameSound.playFireworks()
                    
                    // Launch beautiful staggered fireworks
                    scope.launch {
                        val positions = listOf(
                            Pair(0.3f, 0.22f),
                            Pair(0.7f, 0.26f),
                            Pair(0.5f, 0.16f),
                            Pair(0.25f, 0.38f),
                            Pair(0.75f, 0.42f)
                        )
                        positions.forEachIndexed { index, (fx, fy) ->
                            val now = System.currentTimeMillis()
                            val fw = createFirework(id = now + index, x = fx, y = fy)
                            activeFireworks = activeFireworks + fw
                            if (index == 2) {
                                GameSound.playFireworks()
                            }
                            kotlinx.coroutines.delay(280) // elegant stagger delay
                        }
                    }
                    break
                }
            }
        }
        lastScore = score
    }

    LaunchedEffect(addedScoreTrigger) {
        if (addedScoreTrigger > 0 && addedScoreAmount > 0) {
            floatAnim.snapTo(0f)
            floatAnim.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 800)
            )
        }
    }

    // Active candidates (List of 3)
    val candidates = remember { mutableStateListOf<PuzzleShape?>().apply {
        val loaded = loadCandidates(prefs)
        addAll(loaded)
        if (all { it == null }) {
            generateAndSaveNewCandidates(prefs, this)
        }
    }}

    // Active powerups
    var boosterRotate by remember { mutableStateOf(prefs.getInt("puzzle_booster_rotate", 3)) }
    var boosterClear by remember { mutableStateOf(prefs.getInt("puzzle_booster_clear", 3)) }
    var boosterHammer by remember { mutableStateOf(prefs.getInt("puzzle_booster_hammer", 2)) }

    // Selected powerup mode / Selected candidate
    var selectedCandidateIndex by remember { mutableStateOf<Int?>(null) }
    var isSingleClearModeActive by remember { mutableStateOf(false) }
    var isHammerModeActive by remember { mutableStateOf(false) }

    // Drag-and-drop state variables
    var activeDragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val candidateCoordinates = remember { mutableStateListOf<LayoutCoordinates?>(null, null, null) }
    var boardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val currentDragPlacingCoords by remember {
        derivedStateOf {
            val dragIdx = activeDragIndex
            val shape = dragIdx?.let { candidates.getOrNull(it) }
            val bCoord = boardCoordinates
            val cCoord = dragIdx?.let { candidateCoordinates.getOrNull(it) }

            if (dragIdx != null && shape != null && bCoord != null && cCoord != null) {
                val boardPos = bCoord.positionInWindow()
                val boardSize = bCoord.size
                val candPos = cCoord.positionInWindow()
                
                val visualX = candPos.x + dragOffset.x
                val visualY = candPos.y + dragOffset.y + liftOffsetPx
                
                val relativeX = visualX - boardPos.x
                val relativeY = visualY - boardPos.y
                
                val cellWidth = boardSize.width.toFloat() / 8f
                val cellHeight = boardSize.height.toFloat() / 8f
                
                val colIdx = (relativeX / cellWidth).roundToInt()
                val rowIdx = (relativeY / cellHeight).roundToInt()
                
                var fits = true
                for ((dr, dc) in shape.relativeCoordinates) {
                    val tr = rowIdx + dr
                    val tc = colIdx + dc
                    if (tr !in 0..7 || tc !in 0..7 || board[tr][tc] != 0) {
                        fits = false
                        break
                    }
                }
                if (fits) {
                    Pair(rowIdx, colIdx)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    // Game Over Dialog status
    var isGameOver by remember { mutableStateOf(false) }
    var checkedGameOverOnce by remember { mutableStateOf(false) }

    // Lifecycle Observer for auto-pausing the game
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                isPaused = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Manual Save Check State
    var hasManualSave by remember { mutableStateOf(prefs.contains("manual_puzzle_board")) }

    // Save Game State helper
    fun saveGameState() {
        // Save score & highscore
        prefs.edit().putInt("puzzle_current_score", score).apply()
        if (score > highScore) {
            highScore = score
            prefs.edit().putInt("puzzle_high_score", highScore).apply()
        }

        // Save board
        val sb = StringBuilder()
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                sb.append(board[r][c])
            }
        }
        prefs.edit().putString("puzzle_board", sb.toString()).apply()

        // Save candidates
        val cIds = candidates.map { it?.templateId ?: -1 }.joinToString(",")
        val cRots = candidates.map { it?.rotationCount ?: 0 }.joinToString(",")
        prefs.edit().putString("puzzle_candidate_ids", cIds).apply()
        prefs.edit().putString("puzzle_candidate_rotations", cRots).apply()

        // Save powerups
        prefs.edit().putInt("puzzle_booster_rotate", boosterRotate).apply()
        prefs.edit().putInt("puzzle_booster_clear", boosterClear).apply()
        prefs.edit().putInt("puzzle_booster_hammer", boosterHammer).apply()
    }

    // Clear auto-save game status (to restart a new game next time the user enters)
    fun clearTempGameState() {
        prefs.edit()
            .remove("puzzle_board")
            .remove("puzzle_candidate_ids")
            .remove("puzzle_candidate_rotations")
            .remove("puzzle_current_score")
            .apply()
    }

    // Intercept hardware/system back button
    BackHandler {
        clearTempGameState()
        onBackClick()
    }

    // Manual Save Slot helper
    fun manualSaveGame() {
        val sb = StringBuilder()
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                sb.append(board[r][c])
            }
        }
        prefs.edit()
            .putString("manual_puzzle_board", sb.toString())
            .putString("manual_puzzle_candidate_ids", candidates.map { it?.templateId ?: -1 }.joinToString(","))
            .putString("manual_puzzle_candidate_rotations", candidates.map { it?.rotationCount ?: 0 }.joinToString(","))
            .putInt("manual_puzzle_current_score", score)
            .putInt("manual_puzzle_booster_rotate", boosterRotate)
            .putInt("manual_puzzle_booster_clear", boosterClear)
            .putInt("manual_puzzle_booster_hammer", boosterHammer)
            .apply()
        hasManualSave = true
        GameSound.playPowerUp()
        MainViewModel.instance?.showToastPopup("Game berhasil disimpan secara manual!")
    }

    // Manual Load Slot helper
    fun manualLoadGame() {
        if (!prefs.contains("manual_puzzle_board")) {
            MainViewModel.instance?.showToastPopup("Belum ada save data manual!")
            return
        }

        val boardStr = prefs.getString("manual_puzzle_board", "") ?: ""
        if (boardStr.length == 64) {
            for (r in 0 until 8) {
                for (c in 0 until 8) {
                    board[r][c] = boardStr[r * 8 + c] - '0'
                }
            }
        }

        val loadedCandidates = loadManualCandidates(prefs)
        candidates.clear()
        candidates.addAll(loadedCandidates)

        score = prefs.getInt("manual_puzzle_current_score", 0)
        boosterRotate = prefs.getInt("manual_puzzle_booster_rotate", 3)
        boosterClear = prefs.getInt("manual_puzzle_booster_clear", 3)
        boosterHammer = prefs.getInt("manual_puzzle_booster_hammer", 2)

        isGameOver = false
        checkedGameOverOnce = false
        selectedCandidateIndex = null
        isSingleClearModeActive = false
        isHammerModeActive = false

        GameSound.playPowerUp()
        MainViewModel.instance?.showToastPopup("Game manual berhasil dimuat!")
    }

    // Reset Game helper
    fun resetGame() {
        score = 0
        isGameOver = false
        checkedGameOverOnce = false
        selectedCandidateIndex = null
        isSingleClearModeActive = false
        isHammerModeActive = false

        // Clear Board
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                board[r][c] = 0
            }
        }

        // Regen Candidates
        candidates.clear()
        generateAndSaveNewCandidates(prefs, candidates)

        // Give basic boosters
        boosterRotate = 3
        boosterClear = 3
        boosterHammer = 2

        saveGameState()
        GameSound.playPowerUp()
        MainViewModel.instance?.showToastPopup("Game Baru Dimulai!")
    }

    // Check if a specific block can fit anywhere on the board
    fun canPlaceShapeSomewhere(shape: PuzzleShape): Boolean {
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                var fits = true
                for ((dr, dc) in shape.relativeCoordinates) {
                    val tr = r + dr
                    val tc = c + dc
                    if (tr !in 0..7 || tc !in 0..7 || board[tr][tc] != 0) {
                        fits = false
                        break
                    }
                }
                if (fits) return true
            }
        }
        return false
    }

    // Check General Game Over State
    fun verifyGameOverState() {
        val activeCount = candidates.count { it != null }
        if (activeCount == 0) return

        var hasMoves = false
        for (cIndex in 0 until 3) {
            val candidate = candidates[cIndex] ?: continue
            if (canPlaceShapeSomewhere(candidate)) {
                hasMoves = true
                break
            }
        }

        if (!hasMoves) {
            isGameOver = true
            GameSound.playGameOver()
        }
    }

    // Trigger Game Over Verification when board or candidates change
    LaunchedEffect(board.toList(), candidates.toList()) {
        if (!isGameOver) {
            verifyGameOverState()
        }
    }

    val isDark = isSystemInDarkTheme()
    val backgroundGradient = if (isDark) {
        listOf(
            Color(0xFF0F172A), // Deep Slate Dark
            Color(0xFF1E293B),
            Color(0xFF334155)
        )
    } else {
        listOf(
            Color(0xFFF8FAFC), // Fresh Soft Light Slate
            Color(0xFFE2E8F0),
            Color(0xFFCBD5E1)
        )
    }

    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val labelTextColor = if (isDark) Color.White.copy(alpha = 0.8f) else Color(0xFF334155)
    val scoreTextColor = if (isDark) Color.White else Color(0xFF0F172A)

    // Main Game view scaffold
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(backgroundGradient)
            )
            .systemBarsPadding()
            .onGloballyPositioned { rootCoordinates = it },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .widthIn(max = 450.dp)
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SCORE & HIGHSCORE CONTAINER (Redesigned modern full-width dashboard layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Highscore and Current Score Board (Full Width)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(16.dp))
                        .border(1.5.dp, cardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Highscore Trophy Star Label with integrated adaptive sound/music buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "High Score",
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format("%,d", highScore).replace(',', '.'),
                                color = Color(0xFFFFD54F),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Sound & Music Toggle Icons (Compact, modern, adaptive)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Music Toggle Icon
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF265A66) else Color(0xFFE0F2FE))
                                    .clickable {
                                        isMusicEnabled = !isMusicEnabled
                                        GameSound.playTap()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMusicEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                                    contentDescription = "Musik",
                                    tint = if (isMusicEnabled) (if (isDark) Color(0xFF00ACC1) else Color(0xFF0284C7)) else (if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF64748B)),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Sound Toggle Icon
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF3C2E1F) else Color(0xFFFEF3C7))
                                    .clickable {
                                        isSoundEnabled = !isSoundEnabled
                                        GameSound.playTap()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Suara",
                                    tint = if (isSoundEnabled) (if (isDark) Color(0xFFF57C00) else Color(0xFFD97706)) else (if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF64748B)),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Score Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Skor:",
                            color = labelTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Cute white cat drawing to the left of the score points
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Color(0xFF1E4C56).copy(alpha = 0.5f) else Color(0xFFF1F5F9))
                                    .border(1.dp, cardBorder.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CuteKittenWithGlasses(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }

                            Box(contentAlignment = Alignment.CenterEnd) {
                                val animatedColor = remember(scoreScaleAnim.value) {
                                    if (scoreScaleAnim.value > 1.05f) {
                                        Color(0xFFFFD54F) // Golden pop
                                    } else {
                                        scoreTextColor
                                    }
                                }
                                Text(
                                    text = score.toString(),
                                    color = animatedColor,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier
                                        .testTag("block_puzzle_current_score")
                                        .graphicsLayer {
                                            scaleX = scoreScaleAnim.value
                                            scaleY = scoreScaleAnim.value
                                        }
                                )
                                if (addedScoreTrigger > 0 && floatAnim.value < 1f) {
                                    Text(
                                        text = "+$addedScoreAmount",
                                        color = Color(0xFFFFD54F),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .offset(y = (-24 * floatAnim.value).dp, x = (-12).dp)
                                            .graphicsLayer {
                                                alpha = 1f - floatAnim.value
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Milestone-based progress bar showing targets (250, 720, 2000, 3500, 5000, 7000)
                    val milZero = remember { listOf(0) + scoreMilestones }
                    val currentMilestone = remember(score) {
                        milZero.lastOrNull { score >= it } ?: 0
                    }
                    val nextMilestone = remember(score) {
                        scoreMilestones.firstOrNull { score < it } ?: (currentMilestone + 2000)
                    }
                    val progressFraction = remember(score, currentMilestone, nextMilestone) {
                        val den = nextMilestone - currentMilestone
                        if (den > 0) {
                            ((score - currentMilestone).toFloat() / den.toFloat()).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(if (isDark) Color(0xFF0F262C) else Color(0xFFE2E8F0), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFFB300), Color(0xFFFFD54F))
                                    ),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sekarang: $score",
                            color = labelTextColor.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Target: $nextMilestone",
                            color = if (isDark) Color(0xFF81D4FA) else Color(0xFF0284C7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // MAIN 8x8 GAME BOARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFF152A33), RoundedCornerShape(14.dp))
                    .border(3.dp, Color(0xFF2C5E6A), RoundedCornerShape(14.dp))
                    .padding(8.dp)
                    .onGloballyPositioned { boardCoordinates = it }
            ) {
                // Layout 8 Rows x 8 Columns
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until 8) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (c in 0 until 8) {
                                val cellVal = board[r][c]
                                val isGhostPreview = currentDragPlacingCoords?.let { (anchorR, anchorC) ->
                                    val dragIdx = activeDragIndex
                                    val shape = dragIdx?.let { candidates.getOrNull(it) }
                                    shape?.relativeCoordinates?.any { (dr, dc) ->
                                        r == anchorR + dr && c == anchorC + dc
                                    } ?: false
                                } ?: false

                                val dragShapeColor = activeDragIndex?.let { candidates[it]?.color }

                                // Custom smooth spring-pop animation for placed blocks
                                val cellScale = remember(cellVal) { androidx.compose.animation.core.Animatable(if (cellVal > 0) 0.5f else 0.0f) }
                                LaunchedEffect(cellVal) {
                                    if (cellVal > 0) {
                                        cellScale.animateTo(
                                            targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.spring(
                                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF1C2D37))
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            // Handle cell Tapped!
                                            GameSound.playTap()

                                            if (isSingleClearModeActive) {
                                                if (board[r][c] > 0) {
                                                    board[r][c] = 0
                                                    boosterClear--
                                                    isSingleClearModeActive = false
                                                    saveGameState()
                                                    GameSound.playClear()
                                                    MainViewModel.instance?.showToastPopup("Satu balok berhasil dihapus!")
                                                } else {
                                                    MainViewModel.instance?.showToastPopup("Pilih kotak yang berisi balok!")
                                                }
                                            } else if (isHammerModeActive) {
                                                // Clear 3x3
                                                var clearedCount = 0
                                                for (dr in -1..1) {
                                                    for (dc in -1..1) {
                                                        val tr = r + dr
                                                        val tc = c + dc
                                                        if (tr in 0..7 && tc in 0..7 && board[tr][tc] > 0) {
                                                            board[tr][tc] = 0
                                                            clearedCount++
                                                        }
                                                    }
                                                }
                                                if (clearedCount > 0) {
                                                    boosterHammer--
                                                    isHammerModeActive = false
                                                    saveGameState()
                                                    GameSound.playClear()
                                                    MainViewModel.instance?.showToastPopup("Palu menghancurkan balok!")
                                                } else {
                                                    MainViewModel.instance?.showToastPopup("Pilih area yang memiliki balok!")
                                                }
                                            } else {
                                                // General Candidate placement mode
                                                val selIndex = selectedCandidateIndex
                                                if (selIndex != null) {
                                                    val activeShape = candidates[selIndex]
                                                    if (activeShape != null) {
                                                        // Check if shape fits starting at anchor row r, col c
                                                        var fits = true
                                                        for ((dr, dc) in activeShape.relativeCoordinates) {
                                                            val tr = r + dr
                                                            val tc = c + dc
                                                            if (tr !in 0..7 || tc !in 0..7 || board[tr][tc] != 0) {
                                                                fits = false
                                                                break
                                                            }
                                                        }

                                                        if (fits) {
                                                            // Place shape!
                                                            for ((dr, dc) in activeShape.relativeCoordinates) {
                                                                val tr = r + dr
                                                                val tc = c + dc
                                                                board[tr][tc] = activeShape.colorIndex
                                                            }
                                                            GameSound.playDrop()

                                                            // Update points
                                                            score += activeShape.relativeCoordinates.size

                                                            // Remove this candidate
                                                            candidates[selIndex] = null
                                                            selectedCandidateIndex = null

                                                            // Check for full rows and columns concurrently
                                                            val rowsToClear = mutableListOf<Int>()
                                                            for (rowIdx in 0 until 8) {
                                                                if ((0 until 8).all { board[rowIdx][it] > 0 }) {
                                                                    rowsToClear.add(rowIdx)
                                                                }
                                                            }

                                                            val colsToClear = mutableListOf<Int>()
                                                            for (colIdx in 0 until 8) {
                                                                if ((0 until 8).all { board[it][colIdx] > 0 }) {
                                                                    colsToClear.add(colIdx)
                                                                }
                                                            }

                                                            // Clear them
                                                            val totalClears = rowsToClear.size + colsToClear.size
                                                            if (totalClears > 0) {
                                                                if (totalClears >= 2) {
                                                                    GameSound.playGG()
                                                                    scope.launch {
                                                                        val positions = listOf(
                                                                            Pair(0.32f, 0.24f),
                                                                            Pair(0.68f, 0.28f),
                                                                            Pair(0.5f, 0.18f)
                                                                        )
                                                                        positions.forEachIndexed { index, (fx, fy) ->
                                                                            val now = System.currentTimeMillis()
                                                                            val fw = createFirework(id = now + index + 2000, x = fx, y = fy)
                                                                            activeFireworks = activeFireworks + fw
                                                                            kotlinx.coroutines.delay(200)
                                                                        }
                                                                    }
                                                                } else {
                                                                    GameSound.playClear()
                                                                }

                                                                for (rowIdx in rowsToClear) {
                                                                    for (colIdx in 0 until 8) {
                                                                        board[rowIdx][colIdx] = 0
                                                                    }
                                                                }
                                                                for (colIdx in colsToClear) {
                                                                    for (rowIdx in 0 until 8) {
                                                                        board[rowIdx][colIdx] = 0
                                                                    }
                                                                }

                                                                // Combo score calculation
                                                                score += totalClears * 100 + (totalClears - 1) * 50

                                                                // Random chance to award a booster on row/col completed
                                                                if (Random.nextFloat() < 0.4f) {
                                                                    val rnd = Random.nextInt(3)
                                                                    if (rnd == 0) boosterRotate++
                                                                    else if (rnd == 1) boosterClear++
                                                                    else boosterHammer++
                                                                    GameSound.playPowerUp()
                                                                    MainViewModel.instance?.showToastPopup("Bonus Booster Didapat!")
                                                                }
                                                            }

                                                            // If all candidates are used, generate 3 new ones
                                                            if (candidates.all { it == null }) {
                                                                generateAndSaveNewCandidates(prefs, candidates)
                                                            }

                                                            saveGameState()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    if (cellVal > 0) {
                                        BeveledBlockCell(
                                            color = getColorForIndex(cellVal),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = cellScale.value
                                                    scaleY = cellScale.value
                                                }
                                        )
                                    } else if (isGhostPreview && dragShapeColor != null) {
                                        BeveledBlockCell(
                                            color = dragShapeColor,
                                            isGhost = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ACTIVE THREE CANDIDATE PIECES (Bottom selection tray)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
                    .background(Color(0xFF14303B), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF285664), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 3) {
                        val shape = candidates[i]
                        val isSelected = selectedCandidateIndex == i
                        val isDragging = activeDragIndex == i

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                                .onGloballyPositioned { coords ->
                                    candidateCoordinates[i] = coords
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Transparent)
                                .pointerInput(shape, i) {
                                    if (shape != null) {
                                        detectDragGestures(
                                            onDragStart = {
                                                activeDragIndex = i
                                                dragOffset = Offset.Zero
                                                selectedCandidateIndex = i
                                                isSingleClearModeActive = false
                                                isHammerModeActive = false
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                            },
                                            onDragEnd = {
                                                val bCoord = boardCoordinates
                                                val cCoord = candidateCoordinates[i]
                                                if (bCoord != null && cCoord != null) {
                                                    val boardPos = bCoord.positionInWindow()
                                                    val boardSize = bCoord.size
                                                    val candPos = cCoord.positionInWindow()
                                                    
                                                    val visualX = candPos.x + dragOffset.x
                                                    val visualY = candPos.y + dragOffset.y + liftOffsetPx
                                                    
                                                    val relativeX = visualX - boardPos.x
                                                    val relativeY = visualY - boardPos.y
                                                    
                                                    val cellWidth = boardSize.width.toFloat() / 8f
                                                    val cellHeight = boardSize.height.toFloat() / 8f
                                                    
                                                    val colIdx = (relativeX / cellWidth).roundToInt()
                                                    val rowIdx = (relativeY / cellHeight).roundToInt()
                                                    
                                                    if (rowIdx in 0..7 && colIdx in 0..7) {
                                                        var fits = true
                                                        for ((dr, dc) in shape.relativeCoordinates) {
                                                            val tr = rowIdx + dr
                                                            val tc = colIdx + dc
                                                            if (tr !in 0..7 || tc !in 0..7 || board[tr][tc] != 0) {
                                                                fits = false
                                                                break
                                                            }
                                                        }
                                                        
                                                        if (fits) {
                                                            for ((dr, dc) in shape.relativeCoordinates) {
                                                                val tr = rowIdx + dr
                                                                val tc = colIdx + dc
                                                                board[tr][tc] = shape.colorIndex
                                                            }
                                                            GameSound.playDrop()
                                                            
                                                            score += shape.relativeCoordinates.size
                                                            candidates[i] = null
                                                            selectedCandidateIndex = null
                                                            
                                                            val rowsToClear = mutableListOf<Int>()
                                                            for (rowIdx in 0 until 8) {
                                                                if ((0 until 8).all { board[rowIdx][it] > 0 }) {
                                                                    rowsToClear.add(rowIdx)
                                                                }
                                                            }
                                                            
                                                            val colsToClear = mutableListOf<Int>()
                                                            for (colIdx in 0 until 8) {
                                                                if ((0 until 8).all { board[it][colIdx] > 0 }) {
                                                                    colsToClear.add(colIdx)
                                                                }
                                                            }
                                                            
                                                            val totalClears = rowsToClear.size + colsToClear.size
                                                            if (totalClears > 0) {
                                                                if (totalClears >= 2) {
                                                                    GameSound.playGG()
                                                                    scope.launch {
                                                                        val positions = listOf(
                                                                            Pair(0.32f, 0.24f),
                                                                            Pair(0.68f, 0.28f),
                                                                            Pair(0.5f, 0.18f)
                                                                        )
                                                                        positions.forEachIndexed { index, (fx, fy) ->
                                                                            val now = System.currentTimeMillis()
                                                                            val fw = createFirework(id = now + index + 3000, x = fx, y = fy)
                                                                            activeFireworks = activeFireworks + fw
                                                                            kotlinx.coroutines.delay(200)
                                                                        }
                                                                    }
                                                                } else {
                                                                    GameSound.playClear()
                                                                }
                                                                
                                                                for (rowIdx in rowsToClear) {
                                                                    for (colIdx in 0 until 8) {
                                                                        board[rowIdx][colIdx] = 0
                                                                    }
                                                                }
                                                                for (colIdx in colsToClear) {
                                                                    for (rowIdx in 0 until 8) {
                                                                        board[rowIdx][colIdx] = 0
                                                                    }
                                                                }
                                                                
                                                                score += totalClears * 100 + (totalClears - 1) * 50
                                                                
                                                                if (Random.nextFloat() < 0.4f) {
                                                                    val rnd = Random.nextInt(3)
                                                                    if (rnd == 0) boosterRotate++
                                                                    else if (rnd == 1) boosterClear++
                                                                    else boosterHammer++
                                                                    GameSound.playPowerUp()
                                                                    MainViewModel.instance?.showToastPopup("Bonus Booster Didapat!")
                                                                }
                                                            }
                                                            
                                                            if (candidates.all { it == null }) {
                                                                generateAndSaveNewCandidates(prefs, candidates)
                                                            }
                                                            
                                                            saveGameState()
                                                        }
                                                    }
                                                }
                                                activeDragIndex = null
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                activeDragIndex = null
                                                dragOffset = Offset.Zero
                                            }
                                        )
                                    }
                                }
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    if (shape != null) {
                                        GameSound.playTap()
                                        isSingleClearModeActive = false
                                        isHammerModeActive = false
                                        selectedCandidateIndex = if (isSelected) null else i
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (shape != null) {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        alpha = if (isDragging) 0.25f else if (isSelected) 1.0f else 0.75f
                                        scaleX = if (isSelected) 1.15f else 1.0f
                                        scaleY = if (isSelected) 1.15f else 1.0f
                                    }
                                ) {
                                    MiniShapePreview(shape)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ACTION CONTROLS / POWER-UPS VIEW LAYER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // POWER-UP 1: ROTATE SELECTED
                val rotateActive = boosterRotate > 0 && selectedCandidateIndex != null
                BoosterButton(
                    icon = Icons.Default.Autorenew,
                    count = boosterRotate,
                    label = "Putar",
                    active = rotateActive,
                    highlighted = false,
                    onClick = {
                        val selIndex = selectedCandidateIndex
                        if (selIndex != null) {
                            val activeCandidate = candidates[selIndex]
                            if (activeCandidate != null) {
                                GameSound.playTap()
                                candidates[selIndex] = rotateShape(activeCandidate)
                                boosterRotate--
                                saveGameState()
                                MainViewModel.instance?.showToastPopup("Satu bagian berhasil diputar!")
                            }
                        } else {
                            MainViewModel.instance?.showToastPopup("Pilih salah satu balok di wadah bawah dahulu untuk diputar!")
                        }
                    }
                )

                // POWER-UP 2: SINGLE CELL CHOP/ERASER
                val clearActive = boosterClear > 0
                BoosterButton(
                    icon = Icons.Default.AutoFixHigh,
                    count = boosterClear,
                    label = "Hapus 1x1",
                    active = clearActive,
                    highlighted = isSingleClearModeActive,
                    onClick = {
                        GameSound.playTap()
                        selectedCandidateIndex = null
                        isHammerModeActive = false
                        isSingleClearModeActive = !isSingleClearModeActive
                        if (isSingleClearModeActive) {
                            MainViewModel.instance?.showToastPopup("Mode Hapus: Ketuk kotak di papan untuk menghapus satu balok!")
                        }
                    }
                )

                // POWER-UP 3: AREA CHOP/HAMMER
                val hammerActive = boosterHammer > 0
                BoosterButton(
                    icon = Icons.Default.Handyman,
                    count = boosterHammer,
                    label = "Palu 3x3",
                    active = hammerActive,
                    highlighted = isHammerModeActive,
                    onClick = {
                        GameSound.playTap()
                        selectedCandidateIndex = null
                        isSingleClearModeActive = false
                        isHammerModeActive = !isHammerModeActive
                        if (isHammerModeActive) {
                            MainViewModel.instance?.showToastPopup("Mode Palu: Ketuk kotak di papan untuk menghancurkan area 3x3!")
                        }
                    }
                )
            }
        }

        // GAME OVER DIALOG OVERLAY (Beautiful premium overlay instead of blocking dialog)
        if (isGameOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(cardBg, RoundedCornerShape(20.dp))
                        .border(2.dp, Color(0xFFFBC02D), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "Game Selesai",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(72.dp)
                    )

                    Text(
                        text = "GAME OVER!",
                        color = scoreTextColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Tidak ada ruang tersisa untuk meletakkan balok di papan.",
                        color = labelTextColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    // Final Score Stats inside Game Over card
                    val innerCardBg = if (isDark) Color(0xFF143036) else Color(0xFFF1F5F9)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(innerCardBg, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Skor Akhir:",
                            color = labelTextColor,
                            fontSize = 12.sp
                        )
                        Text(
                            text = score.toString(),
                            color = Color(0xFFFFD54F),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )

                        if (score >= highScore) {
                            Text(
                                text = "REKOR BARU!",
                                color = Color(0xFF4CAF50),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Text(
                                text = "Rekor Tertinggi: $highScore",
                                color = labelTextColor.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clearTempGameState()
                                onBackClick()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF536DFE))
                        ) {
                            Text("Profil", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { resetGame() },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color.Black)
                        ) {
                            Text("Main Lagi", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // FIREWORKS CELEBRATION LAYER (No popup overlay!)
        if (activeFireworks.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {}
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val now = System.currentTimeMillis()

                activeFireworks.forEach { firework ->
                    val elapsed = now - firework.startTime
                    val progress = (elapsed.toFloat() / firework.durationMs).coerceIn(0f, 1f)
                    if (progress < 1f) {
                        val cx = firework.x * canvasWidth
                        val cy = firework.y * canvasHeight
                        val maxRadius = canvasWidth * 0.38f // vibrant dispersion size
                        val gravity = 0.15f // natural looking gravity pulling sparks downward

                        firework.particles.forEach { p ->
                            val dx = p.vx * progress * maxRadius
                            val dy = (p.vy * progress + 0.5f * gravity * progress * progress) * maxRadius

                            // Fade out opacity and shrink sizes naturally
                            val alpha = (1f - progress).coerceIn(0f, 1f)
                            val paintColor = p.color.copy(alpha = alpha)
                            val currentSize = p.size * (1f - progress * 0.4f)

                            drawCircle(
                                color = paintColor,
                                radius = currentSize,
                                center = Offset(cx + dx, cy + dy)
                            )
                        }
                    }
                }
            }
        }

        // GAME PAUSE OVERLAY
        if (isPaused) {
            val pauseGradient = if (isDark) {
                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
            } else {
                listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .background(
                            Brush.verticalGradient(pauseGradient),
                            RoundedCornerShape(24.dp)
                        )
                        .border(
                            1.5.dp,
                            cardBorder,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(if (isDark) Color(0xFF1E3A47) else Color(0xFFE2E8F0), CircleShape)
                            .border(2.dp, if (isDark) Color(0xFF00ACC1) else Color(0xFF0284C7), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Game Di-pause",
                            tint = if (isDark) Color(0xFF00ACC1) else Color(0xFF0284C7),
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    Text(
                        text = "PERMAINAN JEDA",
                        color = scoreTextColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Layar terkunci atau Anda beralih aplikasi. Progress permainan Anda telah disimpan dengan aman.",
                        color = labelTextColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // BUTTONS
                    Button(
                        onClick = {
                            GameSound.playTap()
                            isPaused = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF00ACC1) else Color(0xFF0284C7),
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Lanjutkan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            GameSound.playTap()
                            resetGame()
                            isPaused = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Mulai Ulang",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            GameSound.playTap()
                            clearTempGameState()
                            onBackClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Keluar Game",
                            color = Color.Red.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Floating Drag-and-Drop Shape Preview Overlay
        val dIdx = activeDragIndex
        if (dIdx != null) {
            val draggedShape = candidates[dIdx]
            if (draggedShape != null) {
                val rootPos = rootCoordinates?.positionInWindow() ?: Offset.Zero
                val candCoord = candidateCoordinates[dIdx]
                if (candCoord != null) {
                    val candPos = candCoord.positionInWindow()
                    val originInRoot = candPos - rootPos
                    val activeTranslation = originInRoot + dragOffset + Offset(0f, liftOffsetPx)

                    val boardSize = boardCoordinates?.size
                    val boardCellWidthPx = if (boardSize != null) (boardSize.width.toFloat() / 8f) else 100f
                    val boardCellHeightPx = if (boardSize != null) (boardSize.height.toFloat() / 8f) else 100f
                    val cellPx = minOf(boardCellWidthPx, boardCellHeightPx)

                    val widthDp = with(density) { candCoord.size.width.toDp() }
                    val heightDp = with(density) { candCoord.size.height.toDp() }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        activeTranslation.x.roundToInt(),
                                        activeTranslation.y.roundToInt()
                                    )
                                }
                                .size(widthDp, heightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            DragShapePreview(draggedShape, cellPx)
                        }
                    }
                }
            }
        }
    }
}

// Draw visual preview of shape specifically configured to match 1-to-1 with board cell sizes during drag
@Composable
fun DragShapePreview(shape: PuzzleShape, cellPx: Float) {
    val rows = shape.relativeCoordinates.maxOf { it.first } + 1
    val cols = shape.relativeCoordinates.maxOf { it.second } + 1

    val density = LocalDensity.current
    val widthDp = with(density) { (cols * cellPx).toDp() }
    val heightDp = with(density) { (rows * cellPx).toDp() }

    Box(
        modifier = Modifier.size(widthDp, heightDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for ((r, c) in shape.relativeCoordinates) {
                drawBeveledCell(
                    color = shape.color,
                    topLeft = Offset(c * cellPx + 1f, r * cellPx + 1f),
                    size = Size(cellPx - 2f, cellPx - 2f)
                )
            }
        }
    }
}

// Draw mini visual preview of shape
@Composable
fun MiniShapePreview(shape: PuzzleShape) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Calculate bounding box size
        val rows = shape.relativeCoordinates.maxOf { it.first } + 1
        val cols = shape.relativeCoordinates.maxOf { it.second } + 1

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val availableW = size.width
            val availableH = size.height

            // Calculate cell size based on rows and columns
            val cellSize = (minOf(availableW / cols, availableH / rows)).coerceIn(16f, 48f)

            // Center coordinates
            val offsetX = (availableW - (cols * cellSize)) / 2f
            val offsetY = (availableH - (rows * cellSize)) / 2f

            for ((r, c) in shape.relativeCoordinates) {
                drawBeveledCell(
                    color = shape.color,
                    topLeft = Offset(offsetX + c * cellSize + 1f, offsetY + r * cellSize + 1f),
                    size = Size(cellSize - 2f, cellSize - 2f)
                )
            }
        }
    }
}

// Global reuseable 3D bevel and glossy drawing engine
fun DrawScope.drawBeveledCell(
    color: Color,
    topLeft: Offset,
    size: Size,
    isGhost: Boolean = false,
    alpha: Float = 1.0f
) {
    val w = size.width
    val h = size.height
    val actualAlpha = alpha

    val baseColor = if (isGhost) color.copy(alpha = 0.45f * actualAlpha) else color.copy(alpha = actualAlpha)
    val cornerRad = 4f
    
    // 1. Draw base rounded rect of Level 1 (big flat square)
    drawRoundRect(
        color = baseColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(cornerRad, cornerRad)
    )

    // Only draw bevel details if we have active solid color
    if (color != Color.Transparent) {
        val inset = w * 0.22f

        // Corners for Level 1 (Base/Dasar)
        val p1 = Offset(topLeft.x, topLeft.y) // Top-Left
        val p2 = Offset(topLeft.x + w, topLeft.y) // Top-Right
        val p3 = Offset(topLeft.x + w, topLeft.y + h) // Bottom-Right
        val p4 = Offset(topLeft.x, topLeft.y + h) // Bottom-Left

        // Corners for Level 2 (Puncak)
        val q1 = Offset(topLeft.x + inset, topLeft.y + inset) // Top-Left Peak
        val q2 = Offset(topLeft.x + w - inset, topLeft.y + inset) // Top-Right Peak
        val q3 = Offset(topLeft.x + w - inset, topLeft.y + h - inset) // Bottom-Right Peak
        val q4 = Offset(topLeft.x + inset, topLeft.y + h - inset) // Bottom-Left Peak

        // 2. Slanted faces (Trapezoidal Facets) to create the 2-level pyramid slopes with a jewel style
        
        // Left Facet (Kiri) - Highlight (lebih terang)
        val pLeft = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p4.x, p4.y)
            lineTo(q4.x, q4.y)
            lineTo(q1.x, q1.y)
            close()
        }
        drawPath(
            path = pLeft,
            color = Color.White.copy(alpha = if (isGhost) 0.18f else 0.45f)
        )

        // Top Facet (Atas) - Highlight (lebih terang)
        val pTop = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(q2.x, q2.y)
            lineTo(q1.x, q1.y)
            close()
        }
        drawPath(
            path = pTop,
            color = Color.White.copy(alpha = if (isGhost) 0.14f else 0.32f)
        )

        // Right Facet (Kanan) - Shadow (gelap/shadow)
        val pRight = Path().apply {
            moveTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(q3.x, q3.y)
            lineTo(q2.x, q2.y)
            close()
        }
        drawPath(
            path = pRight,
            color = Color.Black.copy(alpha = if (isGhost) 0.15f else 0.35f)
        )

        // Bottom Facet (Bawah) - Shadow (gelap/shadow)
        val pBottom = Path().apply {
            moveTo(p4.x, p4.y)
            lineTo(p3.x, p3.y)
            lineTo(q3.x, q3.y)
            lineTo(q4.x, q4.y)
            close()
        }
        drawPath(
            path = pBottom,
            color = Color.Black.copy(alpha = if (isGhost) 0.18f else 0.42f)
        )

        // 3. Draw Level 2 (Puncak) - Broad flat top
        drawRect(
            color = baseColor,
            topLeft = q1,
            size = Size(w - 2 * inset, h - 2 * inset)
        )

        // Glossy gemstone reflection gradient across Level 2
        val topLevelGloss = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isGhost) 0.22f else 0.65f),
                Color.White.copy(alpha = if (isGhost) 0.05f else 0.20f),
                Color.Transparent,
                Color.Black.copy(alpha = if (isGhost) 0.0f else 0.18f)
            ),
            start = q1,
            end = q3
        )
        drawRect(
            brush = topLevelGloss,
            topLeft = q1,
            size = Size(w - 2 * inset, h - 2 * inset)
        )

        // 4. Draw Connecting Lines ("setiap sudut persegi dasar terdapat garis penghubung...")
        // Top-Left diagonal
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.30f else 0.85f),
            start = p1,
            end = q1,
            strokeWidth = 2f
        )
        // Bottom-Left diagonal
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.20f else 0.60f),
            start = p4,
            end = q4,
            strokeWidth = 2f
        )
        // Top-Right diagonal
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.15f else 0.40f),
            start = p2,
            end = q2,
            strokeWidth = 2f
        )
        // Bottom-Right diagonal
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.25f else 0.60f),
            start = p3,
            end = q3,
            strokeWidth = 2f
        )

        // 5. Crisp bevel borders around the 2nd level peak and base square edges
        // Highlight on peak top-left edges
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.35f else 0.82f),
            start = q1,
            end = q2,
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.35f else 0.82f),
            start = q1,
            end = q4,
            strokeWidth = 1.5f
        )
        // Shadow on peak bottom-right edges
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.25f else 0.55f),
            start = q4,
            end = q3,
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.25f else 0.55f),
            start = q2,
            end = q3,
            strokeWidth = 1.5f
        )

        // Outer base subtle frame highlights/shadows
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.18f else 0.45f),
            start = p1,
            end = p2,
            strokeWidth = 1.2f
        )
        drawLine(
            color = Color.White.copy(alpha = if (isGhost) 0.18f else 0.45f),
            start = p1,
            end = p4,
            strokeWidth = 1.2f
        )
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.22f else 0.50f),
            start = p4,
            end = p3,
            strokeWidth = 1.2f
        )
        drawLine(
            color = Color.Black.copy(alpha = if (isGhost) 0.22f else 0.50f),
            start = p2,
            end = p3,
            strokeWidth = 1.2f
        )
    }
}

@Composable
fun BeveledBlockCell(
    color: Color,
    modifier: Modifier = Modifier,
    isGhost: Boolean = false,
    alpha: Float = 1.0f
) {
    Canvas(modifier = modifier) {
        drawBeveledCell(
            color = color,
            topLeft = Offset.Zero,
            size = this.size,
            isGhost = isGhost,
            alpha = alpha
        )
    }
}

// Custom Premium booster/action button with quantity counters
@Composable
fun BoosterButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    active: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(90.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (highlighted) Color(0xFFFFB300)
                    else if (active) Color(0xFF1F4C57)
                    else Color(0xFF14303B)
                )
                .border(
                    1.5.dp,
                    if (highlighted) Color.White
                    else if (active) Color(0xFF337E8F)
                    else Color(0xFF1D4552),
                    RoundedCornerShape(14.dp)
                )
                .clickable(enabled = active || label == "Putar") { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) Color.Black else if (active) Color.White else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(24.dp)
            )

            // Orange circle badge on top right for count (matching screenshot)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF57C00))
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text(
            text = label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// DRAWING CUTE WHITE KITTEN CARTOON WITH GLASSES (Matching layout perfectly)
@Composable
fun CuteKittenWithGlasses(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Colors
        val catBackground = Color(0xFFFFFFFF)
        val outerStripes = Color(0xFF039BE5) // Cute blue cat stripes
        val glassFrame = Color(0xFF1E1E1E) // Black hipster glasses
        val cheekPink = Color(0xFFFF8A80)
        val nosePink = Color(0xFFFF5252)
        val strapBlue = Color(0xFF00ACC1)

        // Draw Ears Left and Right
        val earLeft = Path().apply {
            moveTo(w * 0.15f, h * 0.45f)
            lineTo(w * 0.12f, h * 0.15f)
            lineTo(w * 0.42f, h * 0.35f)
            close()
        }
        drawPath(earLeft, catBackground)

        val earLeftPink = Path().apply {
            moveTo(w * 0.20f, h * 0.40f)
            lineTo(w * 0.16f, h * 0.20f)
            lineTo(w * 0.36f, h * 0.34f)
            close()
        }
        drawPath(earLeftPink, cheekPink)

        val earRight = Path().apply {
            moveTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.88f, h * 0.15f)
            lineTo(w * 0.58f, h * 0.35f)
            close()
        }
        drawPath(earRight, catBackground)

        val earRightPink = Path().apply {
            moveTo(w * 0.80f, h * 0.40f)
            lineTo(w * 0.84f, h * 0.20f)
            lineTo(w * 0.64f, h * 0.34f)
            close()
        }
        drawPath(earRightPink, cheekPink)

        // Draw cute little Blue Stripes at top of ears
        val stripe1 = Path().apply {
            moveTo(w * 0.14f, h * 0.17f)
            lineTo(w * 0.16f, h * 0.23f)
            lineTo(w * 0.19f, h * 0.20f)
            close()
        }
        drawPath(stripe1, outerStripes)

        val stripe2 = Path().apply {
            moveTo(w * 0.86f, h * 0.17f)
            lineTo(w * 0.84f, h * 0.23f)
            lineTo(w * 0.81f, h * 0.20f)
            close()
        }
        drawPath(stripe2, outerStripes)

        // Main Head Round Circle
        drawCircle(
            color = catBackground,
            radius = w * 0.38f,
            center = Offset(w * 0.5f, h * 0.58f)
        )

        // 3 Cute stripes on forehead
        val foreheadStripeCenter = Path().apply {
            moveTo(w * 0.48f, h * 0.26f)
            lineTo(w * 0.52f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.36f)
            close()
        }
        drawPath(foreheadStripeCenter, outerStripes)

        val foreheadStripeLeft = Path().apply {
            moveTo(w * 0.42f, h * 0.28f)
            lineTo(w * 0.45f, h * 0.28f)
            lineTo(w * 0.44f, h * 0.35f)
            close()
        }
        drawPath(foreheadStripeLeft, outerStripes)

        val foreheadStripeRight = Path().apply {
            moveTo(w * 0.58f, h * 0.28f)
            lineTo(w * 0.55f, h * 0.28f)
            lineTo(w * 0.56f, h * 0.35f)
            close()
        }
        drawPath(foreheadStripeRight, outerStripes)

        // Cute big cartoon hipster black glasses (outer)
        val glassW = w * 0.28f
        val glassH = h * 0.22f
        val bridgeW = w * 0.08f

        // Left glass frame
        drawRoundRect(
            color = glassFrame,
            topLeft = Offset(w * 0.18f, h * 0.42f),
            size = Size(glassW, glassH),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = w * 0.05f)
        )

        // Right glass frame
        drawRoundRect(
            color = glassFrame,
            topLeft = Offset(w * 0.54f, h * 0.42f),
            size = Size(glassW, glassH),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = w * 0.05f)
        )

        // Middle Bridge
        drawLine(
            color = glassFrame,
            start = Offset(w * 0.46f, h * 0.51f),
            end = Offset(w * 0.54f, h * 0.51f),
            strokeWidth = w * 0.06f
        )

        // Big Eyes inside glasses
        drawCircle(
            color = Color.Black,
            radius = w * 0.09f,
            center = Offset(w * 0.32f, h * 0.53f)
        )
        drawCircle(
            color = Color.Black,
            radius = w * 0.09f,
            center = Offset(w * 0.68f, h * 0.53f)
        )

        // White reflection spots inside eyes
        drawCircle(
            color = Color.White,
            radius = w * 0.032f,
            center = Offset(w * 0.30f, h * 0.50f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.015f,
            center = Offset(w * 0.35f, h * 0.55f)
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.032f,
            center = Offset(w * 0.66f, h * 0.50f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.015f,
            center = Offset(w * 0.71f, h * 0.55f)
        )

        // Rosy pink cheeks
        drawCircle(
            color = cheekPink.copy(alpha = 0.5f),
            radius = w * 0.05f,
            center = Offset(w * 0.22f, h * 0.68f)
        )
        drawCircle(
            color = cheekPink.copy(alpha = 0.5f),
            radius = w * 0.05f,
            center = Offset(w * 0.78f, h * 0.68f)
        )

        // Cute small nose
        val nosePath = Path().apply {
            moveTo(w * 0.47f, h * 0.64f)
            lineTo(w * 0.53f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.68f)
            close()
        }
        drawPath(nosePath, nosePink)

        // Smile line
        drawLine(
            color = Color.DarkGray,
            start = Offset(w * 0.50f, h * 0.68f),
            end = Offset(w * 0.50f, h * 0.73f),
            strokeWidth = 3f
        )
        // curve left smile
        drawArc(
            color = Color.DarkGray,
            startAngle = 18f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.44f, h * 0.69f),
            size = Size(w * 0.06f, h * 0.06f),
            style = Stroke(width = 3.5f)
        )
        // curve right smile
        drawArc(
            color = Color.DarkGray,
            startAngle = 10f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(w * 0.50f, h * 0.69f),
            size = Size(w * 0.06f, h * 0.06f),
            style = Stroke(width = 3.5f)
        )

        // Blue collar strap at bottom of Head
        drawArc(
            color = strapBlue,
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.65f),
            size = Size(w * 0.6f, h * 0.34f),
            style = Stroke(width = w * 0.045f)
        )

        // Small yellow bell tag
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = w * 0.04f,
            center = Offset(w * 0.5f, h * 0.94f)
        )
        drawCircle(
            color = Color(0xFFF57C00),
            radius = w * 0.015f,
            center = Offset(w * 0.5f, h * 0.94f)
        )
    }
}

// ---------------- LOCAL PERSISTENCE HELPERS ----------------

fun loadBoard(prefs: SharedPreferences): Array<IntArray> {
    val boardStr = prefs.getString("puzzle_board", "") ?: ""
    val board = Array(10) { IntArray(10) }
    if (boardStr.length == 100) {
        var idx = 0
        for (r in 0 until 10) {
            for (c in 0 until 10) {
                board[r][c] = boardStr[idx] - '0'
                idx++
            }
        }
    }
    return board
}

fun loadCandidates(prefs: SharedPreferences): List<PuzzleShape?> {
    val idsStr = prefs.getString("puzzle_candidate_ids", "") ?: ""
    val rotsStr = prefs.getString("puzzle_candidate_rotations", "") ?: ""

    val ids = idsStr.split(",").map { it.toIntOrNull() ?: -1 }
    val rots = rotsStr.split(",").map { it.toIntOrNull() ?: 0 }

    val loaded = mutableListOf<PuzzleShape?>()
    for (i in 0 until 3) {
        val id = ids.getOrNull(i) ?: -1
        if (id == -1) {
            loaded.add(null)
        } else {
            val template = templates.find { it.id == id }
            if (template != null) {
                var shape = PuzzleShape(
                    templateId = template.id,
                    relativeCoordinates = template.coords,
                    color = getColorForIndex(template.colorIndex),
                    colorIndex = template.colorIndex
                )
                val rot = rots.getOrNull(i) ?: 0
                for (r in 0 until rot) {
                    shape = rotateShape(shape)
                }
                loaded.add(shape)
            } else {
                loaded.add(null)
            }
        }
    }
    return loaded
}

fun loadManualCandidates(prefs: SharedPreferences): List<PuzzleShape?> {
    val idsStr = prefs.getString("manual_puzzle_candidate_ids", "") ?: ""
    val rotsStr = prefs.getString("manual_puzzle_candidate_rotations", "") ?: ""

    val ids = idsStr.split(",").map { it.toIntOrNull() ?: -1 }
    val rots = rotsStr.split(",").map { it.toIntOrNull() ?: 0 }

    val loaded = mutableListOf<PuzzleShape?>()
    for (i in 0 until 3) {
        val id = ids.getOrNull(i) ?: -1
        if (id == -1) {
            loaded.add(null)
        } else {
            val template = templates.find { it.id == id }
            if (template != null) {
                var shape = PuzzleShape(
                    templateId = template.id,
                    relativeCoordinates = template.coords,
                    color = getColorForIndex(template.colorIndex),
                    colorIndex = template.colorIndex
                )
                val rot = rots.getOrNull(i) ?: 0
                for (r in 0 until rot) {
                    shape = rotateShape(shape)
                }
                loaded.add(shape)
            } else {
                loaded.add(null)
            }
        }
    }
    return loaded
}

fun generateAndSaveNewCandidates(prefs: SharedPreferences, activeList: MutableList<PuzzleShape?>) {
    activeList.clear()
    for (i in 0 until 3) {
        val template = templates[Random.nextInt(templates.size)]
        activeList.add(
            PuzzleShape(
                templateId = template.id,
                relativeCoordinates = template.coords,
                color = getColorForIndex(template.colorIndex),
                colorIndex = template.colorIndex
            )
        )
    }

    val idsStr = activeList.map { it?.templateId ?: -1 }.joinToString(",")
    val rotsStr = activeList.map { it?.rotationCount ?: 0 }.joinToString(",")
    prefs.edit().putString("puzzle_candidate_ids", idsStr).apply()
    prefs.edit().putString("puzzle_candidate_rotations", rotsStr).apply()
}
