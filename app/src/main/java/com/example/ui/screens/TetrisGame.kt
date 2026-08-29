package com.example.ui.screens

import com.example.ui.MainViewModel

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// Tetromino representation
data class Tetromino(
    val type: Char,
    val coordinates: List<Pair<Int, Int>>, // relative to center (row, col)
    val colorIndex: Int // 1 to 7 corresponding to block colors
) {
    // Rotations around pivot (0,0)
    fun rotateClockwise(): Tetromino {
        if (type == 'O') return this // Square doesn't rotate
        val rotated = coordinates.map { Pair(it.second, -it.first) }
        return this.copy(coordinates = rotated)
    }

    fun rotateCounterClockwise(): Tetromino {
        if (type == 'O') return this
        val rotated = coordinates.map { Pair(-it.second, it.first) }
        return this.copy(coordinates = rotated)
    }
}

// Retro Game Boy Shades or Retro Colors
val GAMEBOY_COLOR_MAP = listOf(
    Color(0xFF9BBC0F), // 0: Empty (Soft Game Boy lcd background)
    Color(0xFF306230), // 1: Active Piece dark shade
    Color(0xFF0F380F), // 2: Tetris block color
    Color(0xFF8BAC0F), // 3: Medium background
    Color(0xFF4C5E03), // 4: Alternate block green
    Color(0xFF2C3E02), // 5: Green 5
    Color(0xFF1B2C01), // 6: Green 6
    Color(0xFF0A1B00)  // 7: Green 7
)

val RETRO_ACCENT_COLORS = listOf(
    Color(0xFFFF2D55), // Red
    Color(0xFFFF9500), // Orange
    Color(0xFFFFCC00), // Yellow
    Color(0xFF4CD964), // Green
    Color(0xFF30D158), // Emerald Green
    Color(0xFF0A84FF), // Blue
    Color(0xFFBF5AF2)  // Purple
)

val MODERN_BLOCK_COLORS = listOf(
    Color(0xFF00FFD2), // Cyan Neon Glow
    Color(0xFFFF007F), // Cyber Pink
    Color(0xFF7000FF), // Neon Amethyst
    Color(0xFF39FF14), // Lime Electric
    Color(0xFFFFE600), // Bright Yellow
    Color(0xFFFF5E00), // Active Orange
    Color(0xFF00FF66)  // Mint Sparkle
)

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPyramidBlock(
    color: Color,
    topLeft: Offset,
    size: Size,
    isGhost: Boolean = false
) {
    val w = size.width
    val h = size.height
    
    // Draw the Level 1 base block with large size
    drawRect(
        color = color,
        topLeft = topLeft,
        size = size
    )
    
    if (color != Color.Transparent) {
        val inset = w * 0.22f
        
        // Four corners of the base block (Level 1)
        val p1 = Offset(topLeft.x, topLeft.y)            // Top-Left
        val p2 = Offset(topLeft.x + w, topLeft.y)        // Top-Right
        val p3 = Offset(topLeft.x + w, topLeft.y + h)    // Bottom-Right
        val p4 = Offset(topLeft.x, topLeft.y + h)        // Bottom-Left
        
        // Four corners of the inner smaller block above it (Level 2)
        val q1 = Offset(topLeft.x + inset, topLeft.y + inset)            // Top-Left Peak
        val q2 = Offset(topLeft.x + w - inset, topLeft.y + inset)        // Top-Right Peak
        val q3 = Offset(topLeft.x + w - inset, topLeft.y + h - inset)    // Bottom-Right Peak
        val q4 = Offset(topLeft.x + inset, topLeft.y + h - inset)        // Bottom-Left Peak
        
        // top-left to left-bottom facets (lebih terang / highlights)
        
        // Left Facet
        val pathLeft = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p4.x, p4.y)
            lineTo(q4.x, q4.y)
            lineTo(q1.x, q1.y)
            close()
        }
        drawPath(
            path = pathLeft,
            color = Color.White.copy(alpha = if (isGhost) 0.16f else 0.42f)
        )
        
        // Top Facet
        val pathTop = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(q2.x, q2.y)
            lineTo(q1.x, q1.y)
            close()
        }
        drawPath(
            path = pathTop,
            color = Color.White.copy(alpha = if (isGhost) 0.12f else 0.30f)
        )
        
        // right-bottom to right-top facets (sedikit gelap / shadow)
        
        // Right Facet
        val pathRight = Path().apply {
            moveTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(q3.x, q3.y)
            lineTo(q2.x, q2.y)
            close()
        }
        drawPath(
            path = pathRight,
            color = Color.Black.copy(alpha = if (isGhost) 0.15f else 0.38f)
        )
        
        // Bottom Facet
        val pathBottom = Path().apply {
            moveTo(p4.x, p4.y)
            lineTo(p3.x, p3.y)
            lineTo(q3.x, q3.y)
            lineTo(q4.x, q4.y)
            close()
        }
        drawPath(
            path = pathBottom,
            color = Color.Black.copy(alpha = if (isGhost) 0.12f else 0.30f)
        )
        
        // Level 2: Flat top peak block (Puncak yang luas)
        drawRect(
            color = color,
            topLeft = q1,
            size = Size(w - 2f * inset, h - 2f * inset)
        )
        
        // Gemstone glossy refraction linear gradient overlay
        val peakBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isGhost) 0.18f else 0.58f),
                Color.White.copy(alpha = if (isGhost) 0.04f else 0.12f),
                Color.Transparent,
                Color.Black.copy(alpha = if (isGhost) 0.00f else 0.14f)
            ),
            start = q1,
            end = q3
        )
        drawRect(
            brush = peakBrush,
            topLeft = q1,
            size = Size(w - 2f * inset, h - 2f * inset)
        )
        
        // Connecting diagonal lines showing tapered/beveled slope and boundaries
        drawRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = q1,
            size = Size(w - 2f * inset, h - 2f * inset),
            style = Stroke(width = 0.8f)
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = topLeft,
            size = size,
            style = Stroke(width = 0.8f)
        )
        
        // lines of pyramid ridges connecting level 1 and 2 corners
        drawLine(
            color = Color.Black.copy(alpha = 0.15f),
            start = p1,
            end = q1,
            strokeWidth = 0.8f
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.25f),
            start = p2,
            end = q2,
            strokeWidth = 0.8f
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.28f),
            start = p3,
            end = q3,
            strokeWidth = 0.8f
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.15f),
            start = p4,
            end = q4,
            strokeWidth = 0.8f
        )
    }
}


@Composable
fun TetrisGame(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator? }

    // Navigation and back behavior
    BackHandler {
        onBackClick()
    }

    // Shared preferences for high scores
    val prefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }
    
    // Core game selection models
    var gameType by remember { mutableStateOf(0) } // 0: Lobby Menu, 1: Tetris, 2: Shooter, 3: Racing, 4: Snake, 5: Breakout
    var selectedGameId by remember { mutableStateOf(1) } // 1 to 5 inside Lobby Menu

    // Real high score values
    var tetrisHighScore by remember { mutableStateOf(prefs.getInt("tetris_high_score", 0)) }
    var shooterHighScore by remember { mutableStateOf(prefs.getInt("brick_shooter_high_score", 0)) }
    var racingHighScore by remember { mutableStateOf(prefs.getInt("brick_racing_high_score", 0)) }
    var snakeHighScore by remember { mutableStateOf(prefs.getInt("brick_snake_high_score", 0)) }
    var breakoutHighScore by remember { mutableStateOf(prefs.getInt("brick_breakout_high_score", 0)) }

    // Gamepad OTG/Bluetooth support states
    var isGamepadConnectedState by remember { mutableStateOf(false) }
    var forceFullscreenGamepadMode by remember { mutableStateOf(false) }
    val isFullscreenMode = forceFullscreenGamepadMode

    // Screen shake state for game over effect
    var screenShakeOffset by remember { mutableStateOf(Offset.Zero) }

    // Focus requester for gamepad button listeners
    val focusRequester = remember { FocusRequester() }

    // Request keyboard focus when game state changes
    LaunchedEffect(gameType, isFullscreenMode) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // ignore
        }
    }

    // Dynamic Gamepad connection listener (via OTG / Bluetooth)
    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        
        fun updateGamepadStatus() {
            var connected = false
            val deviceIds = InputDevice.getDeviceIds()
            for (id in deviceIds) {
                val device = InputDevice.getDevice(id) ?: continue
                val sources = device.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (isGamepad || isJoystick) {
                    connected = true
                    break
                }
            }
            isGamepadConnectedState = connected
        }

        // Check initially
        updateGamepadStatus()

        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                updateGamepadStatus()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                updateGamepadStatus()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                updateGamepadStatus()
            }
        }

        if (inputManager != null) {
            try {
                inputManager.registerInputDeviceListener(listener, null)
            } catch (e: Exception) {
                // fallback
            }
        }

        onDispose {
            if (inputManager != null) {
                try {
                    inputManager.unregisterInputDeviceListener(listener)
                } catch (e: Exception) {
                    // fallback
                }
            }
        }
    }

    // Sound engine
    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (e: Exception) {
            null
        }
    }

    fun playBeep(type: Int) {
        try {
            toneGen?.startTone(type, 70)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun triggerVibration(duration: Long = 40) {
        // Disabled per user request (hilangkan efek getar saat menekan tombol)
    }

    // Grid details
    val rowsCount = 20
    val colsCount = 10

    // Monochrome vs Retro Theme (Modern colored is default)
    var isMonochromeMode by remember { mutableStateOf(false) }

    // Dynamic light/dark smartphone system check
    val systemIsDark = isSystemInDarkTheme()

    // Screen aesthetic styling variables for responsive LCD and OLED themes (adapted with user's customized colors!)
    val screenPanelBgColor = when {
        isMonochromeMode -> Color(0xFF8BAC0F) // Gameboy retro green
        systemIsDark -> Color(0xFF04060A)    // Modern OLED: pure black
        else -> Color(0xFFF4F7F6)            // Mint-white, requested by user
    }

    val screenPanelBorderColor = when {
        isMonochromeMode -> Color(0xFF306230)
        systemIsDark -> Color(0xFF00FFD2).copy(alpha = 0.5f) // Glowing cyan on OLED
        else -> Color(0xFFE9F0EE)            // Light sage bezel border, requested by user
    }

    val playingGridBgColor = when {
        isMonochromeMode -> Color(0xFF9BBC0F) // Retro Gameboy green Matrix
        systemIsDark -> Color(0xFF0D121B)    // OLED: pitch obsidian grid frame
        else -> Color(0xFFE9F0EE)            // Light sage playing background, requested by user
    }

    val playingGridBorderColor = when {
        isMonochromeMode -> Color(0xFF306230)
        systemIsDark -> Color(0xFF00FFD2).copy(alpha = 0.25f)
        else -> Color(0xFFBDC8C4)            // Muted sage border
    }

    val screenPrimaryTextColor = when {
        isMonochromeMode -> Color(0xFF0F380F) // Dark Gameboy ink
        systemIsDark -> Color(0xFFF8FAFC)    // Clear crisp porcelain text
        else -> Color(0xFF111827)            // Hitam pekat/Ink, requested by user
    }

    val screenSecondaryTextColor = when {
        isMonochromeMode -> Color(0xFF306230) // Medium Gameboy ink
        systemIsDark -> Color(0xFF94A3B8)    // Muted tech silver
        else -> Color(0xFF4B5563)            // Cool gray, requested by user
    }

    val accentCyanColor = when {
        isMonochromeMode -> Color(0xFF0F380F)
        systemIsDark -> Color(0xFF00FFD2)  // Cyberpunk cyan glow
        else -> Color(0xFF059669)          // Neo-mint / Hijau teal terang, requested by user
    }

    val accentYellowColor = when {
        isMonochromeMode -> Color(0xFF0F380F)
        systemIsDark -> Color(0xFFFFCC00)  // Gold neon spark
        else -> Color(0xFF3B82F6)          // Electric blue, requested by user
    }

    val accentPinkColor = when {
        isMonochromeMode -> Color(0xFF0F380F)
        systemIsDark -> Color(0xFFFF0055)  // Laser rose red
        else -> Color(0xFF059669)          // Neo-mint primary, requested by user
    }

    val nextBoxBgColor = when {
        isMonochromeMode -> Color(0xFF9BBC0F).copy(alpha = 0.5f)
        systemIsDark -> Color(0xFF0D121B)
        else -> Color(0xFFF1F5F9)
    }

    val nextBoxBorderColor = when {
        isMonochromeMode -> Color(0xFF306230)
        systemIsDark -> Color(0xFFFF007F).copy(alpha = 0.3f)
        else -> Color(0xFFCBD5E1)
    }

    val blockColors = if (isMonochromeMode) {
        MODERN_BLOCK_COLORS
    } else if (systemIsDark) {
        listOf(
            Color(0xFF00FFD2), // Cyan Neon
            Color(0xFFFF007F), // Cyber Pink
            Color(0xFF7000FF), // Pure Amethyst
            Color(0xFF39FF14), // Lime Electric
            Color(0xFFFFCC00), // Bright Yellow
            Color(0xFFFF5E00), // Saturated Orange
            Color(0xFF00FF66)  // Mint Sparkle
        )
    } else {
        listOf(
            Color(0xFF0284C7), // Rich Blue (Sky)
            Color(0xFFDB2777), // Deep Rose (Pink)
            Color(0xFF7C3AED), // royal purple
            Color(0xFF16A34A), // Forest emerald green
            Color(0xFFD97706), // Amber bronze
            Color(0xFFEA580C), // Vibrant rust red-orange
            Color(0xFF059669)  // Deep mint
        )
    }

    // ----------------------------------------
    // COMMON GAME STATE MACHINE Variables
    // ----------------------------------------
    var score by remember { mutableStateOf(0) }
    var level by remember { mutableStateOf(1) }
    var linesCleared by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var isGameOver by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // Game Over shake animation effect specifically for Tetris (gameType == 1)
    LaunchedEffect(isGameOver) {
        if (isGameOver && gameType == 1) {
            val shakeSequence = listOf(
                Offset(-12f, 8f), Offset(10f, -9f), Offset(-9f, -6f), Offset(7f, 7f),
                Offset(-6f, 4f), Offset(4f, -4f), Offset(-3f, 2f), Offset(2f, -1f),
                Offset.Zero
            )
            for (offset in shakeSequence) {
                screenShakeOffset = offset
                delay(40)
            }
        } else {
            screenShakeOffset = Offset.Zero
        }
    }

    // ----------------------------------------
    // 1. TETRIS SPECIFIC STATES
    // ----------------------------------------
    var tetrisGrid by remember { mutableStateOf(List(rowsCount) { List(colsCount) { 0 } }) }
    var currentPiece by remember { mutableStateOf<Tetromino?>(null) }
    var nextPiece by remember { mutableStateOf<Tetromino?>(null) }
    var curRow by remember { mutableStateOf(0) }
    var curCol by remember { mutableStateOf(4) }
    val tetrominoTemplates = remember {
        listOf(
            Tetromino('I', listOf(Pair(0, -1), Pair(0, 0), Pair(0, 1), Pair(0, 2)), 1),
            Tetromino('O', listOf(Pair(0, 0), Pair(0, 1), Pair(1, 0), Pair(1, 1)), 2),
            Tetromino('T', listOf(Pair(0, -1), Pair(0, 0), Pair(0, 1), Pair(1, 0)), 3),
            Tetromino('S', listOf(Pair(0, 0), Pair(0, 1), Pair(1, -1), Pair(1, 0)), 4),
            Tetromino('Z', listOf(Pair(0, -1), Pair(0, 0), Pair(1, 0), Pair(1, 1)), 5),
            Tetromino('J', listOf(Pair(0, -1), Pair(0, 0), Pair(0, 1), Pair(1, -1)), 6),
            Tetromino('L', listOf(Pair(0, -1), Pair(0, 0), Pair(0, 1), Pair(1, 1)), 7)
        )
    }

    fun generateRandomPiece(): Tetromino {
        return tetrominoTemplates[Random.nextInt(tetrominoTemplates.size)]
    }

    // ----------------------------------------
    // 2. SHOOTER SPECIFIC STATES
    // ----------------------------------------
    var shooterPlayerCol by remember { mutableStateOf(4) }
    val shooterBullets = remember { mutableStateListOf<Pair<Int, Int>>() } // list of (r, c)
    val shooterEnemies = remember { mutableStateListOf<Pair<Int, Int>>() } // list of (r, c)
    var shooterTicks by remember { mutableStateOf(0) }

    // ----------------------------------------
    // 3. RACING SPECIFIC STATES
    // ----------------------------------------
    var racingPlayerLane by remember { mutableStateOf(1) } // 0, 1, 2
    val racingObstacles = remember { mutableStateListOf<Pair<Int, Int>>() } // list of (topRow, lane)
    var racingTicks by remember { mutableStateOf(0) }
    var roadStripesOffset by remember { mutableStateOf(false) }

    fun getLaneCol(lane: Int): Int = when (lane) {
        0 -> 2
        1 -> 5
        else -> 8
    }

    // ----------------------------------------
    // 4. SNAKE SPECIFIC STATES
    // ----------------------------------------
    val snakeBody = remember { mutableStateListOf<Pair<Int, Int>>() } // Head is index 0
    var snakeDirection by remember { mutableStateOf(Pair(-1, 0)) } // rowDelta, colDelta
    var snakeFood by remember { mutableStateOf(Pair(5, 5)) }
    var snakeTicks by remember { mutableStateOf(0) }

    fun spawnSnakeFood() {
        var found = false
        var attempts = 0
        while (!found && attempts < 100) {
            val r = Random.nextInt(1, 19)
            val c = Random.nextInt(0, 10)
            val proposal = Pair(r, c)
            if (!snakeBody.contains(proposal)) {
                snakeFood = proposal
                found = true
            }
            attempts++
        }
    }

    // ----------------------------------------
    // 5. BREAKOUT SPECIFIC STATES
    // ----------------------------------------
    var breakoutPaddleCol by remember { mutableStateOf(4) }
    var ballX by remember { mutableStateOf(5.0f) }
    var ballY by remember { mutableStateOf(15.0f) }
    var ballVx by remember { mutableStateOf(0.35f) }
    var ballVy by remember { mutableStateOf(-0.45f) }
    val breakoutBricks = remember { mutableStateListOf<Pair<Int, Int>>() }

    // ----------------------------------------
    // DYNAMIC RESET AND START SYSTEM
    // ----------------------------------------
    fun resetAndStartGame(type: Int) {
        gameType = type
        score = 0
        level = 1
        linesCleared = 0
        lives = 3
        isGameOver = false
        isPaused = false
        triggerVibration(120)

        when (type) {
            1 -> { // Tetris
                tetrisGrid = List(rowsCount) { List(colsCount) { 0 } }
                currentPiece = generateRandomPiece()
                nextPiece = generateRandomPiece()
                curRow = 0
                curCol = 4
            }
            2 -> { // Shooter
                shooterPlayerCol = 4
                shooterBullets.clear()
                shooterEnemies.clear()
                shooterTicks = 0
                // Spawn rows of starting enemy blocks
                for (r in 0..2) {
                    for (c in 1..8 step 2) {
                        shooterEnemies.add(Pair(r, c))
                    }
                }
            }
            3 -> { // Racing
                racingPlayerLane = 1
                racingObstacles.clear()
                racingTicks = 0
                roadStripesOffset = false
                // Spawn first obstacle car
                racingObstacles.add(Pair(-6, 0))
            }
            4 -> { // Snake
                snakeBody.clear()
                // Center spawned snake (length 3)
                snakeBody.add(Pair(10, 5))
                snakeBody.add(Pair(11, 5))
                snakeBody.add(Pair(12, 5))
                snakeDirection = Pair(-1, 0) // Up
                snakeTicks = 0
                spawnSnakeFood()
            }
            5 -> { // Breakout
                breakoutPaddleCol = 4
                ballX = 5.0f
                ballY = 15.0f
                ballVx = 0.35f
                ballVy = -0.45f
                breakoutBricks.clear()
                // Spawn blocks rows
                for (r in 1..4) {
                    for (c in 0..9) {
                        breakoutBricks.add(Pair(r, c))
                    }
                }
            }
        }
    }

    // ----------------------------------------
    // TETRIS DETAILED HELPER LOGIC
    // ----------------------------------------
    fun checkTetrisCollision(piece: Tetromino, rowOff: Int, colOff: Int): Boolean {
        for (coordinate in piece.coordinates) {
            val targetRow = curRow + coordinate.first + rowOff
            val targetCol = curCol + coordinate.second + colOff

            if (targetCol < 0 || targetCol >= colsCount || targetRow >= rowsCount) {
                return true
            }

            if (targetRow >= 0) {
                if (tetrisGrid[targetRow][targetCol] != 0) {
                    return true
                }
            }
        }
        return false
    }

    fun mergeTetrisPiece() {
        val current = currentPiece ?: return
        val updatedGrid = tetrisGrid.map { it.toMutableList() }
        var inBounds = false

        for (coord in current.coordinates) {
            val targetR = curRow + coord.first
            val targetC = curCol + coord.second
            if (targetR in 0 until rowsCount && targetC in 0 until colsCount) {
                updatedGrid[targetR][targetC] = current.colorIndex
                inBounds = true
            }
        }

        if (!inBounds) {
            isGameOver = true
            playBeep(ToneGenerator.TONE_CDMA_ABBR_ALERT)
            return
        }

        // Clear completed full rows
        var cleared = 0
        val finalGrid = updatedGrid.filter { row ->
            val full = row.all { it != 0 }
            if (full) cleared++
            !full
        }.toMutableList()

        while (finalGrid.size < rowsCount) {
            finalGrid.add(0, MutableList(colsCount) { 0 })
        }

        tetrisGrid = finalGrid

        if (cleared > 0) {
            val merits = when (cleared) {
                1 -> 100 * level
                2 -> 300 * level
                3 -> 500 * level
                4 -> 800 * level
                else -> 1000 * level
            }
            score += merits
            linesCleared += cleared
            level = (linesCleared / 10) + 1

            if (score > tetrisHighScore) {
                tetrisHighScore = score
                prefs.edit().putInt("tetris_high_score", score).apply()
            }

            repeat(cleared) { playBeep(ToneGenerator.TONE_DTMF_S) }
            triggerVibration(150)
        } else {
            playBeep(ToneGenerator.TONE_PROP_BEEP)
            triggerVibration(40)
        }

        currentPiece = nextPiece ?: generateRandomPiece()
        nextPiece = generateRandomPiece()
        curRow = 0
        curCol = 4

        if (checkTetrisCollision(currentPiece!!, 0, 0)) {
            isGameOver = true
            playBeep(ToneGenerator.TONE_CDMA_ABBR_ALERT)
            triggerVibration(300)
        }
    }

    fun moveTetrisLeft() {
        if (isGameOver || isPaused) return
        val piece = currentPiece ?: return
        if (!checkTetrisCollision(piece, 0, -1)) {
            curCol -= 1
            playBeep(ToneGenerator.TONE_PROP_ACK)
        }
    }

    fun moveTetrisRight() {
        if (isGameOver || isPaused) return
        val piece = currentPiece ?: return
        if (!checkTetrisCollision(piece, 0, 1)) {
            curCol += 1
            playBeep(ToneGenerator.TONE_PROP_ACK)
        }
    }

    fun moveTetrisDown(): Boolean {
        if (isGameOver || isPaused) return false
        val piece = currentPiece ?: return false
        if (!checkTetrisCollision(piece, 1, 0)) {
            curRow += 1
            return true
        } else {
            mergeTetrisPiece()
            return false
        }
    }

    fun rotateTetrisPiece(clockwise: Boolean) {
        if (isGameOver || isPaused) return
        val piece = currentPiece ?: return
        val rotated = if (clockwise) piece.rotateClockwise() else piece.rotateCounterClockwise()
        if (!checkTetrisCollision(rotated, 0, 0)) {
            currentPiece = rotated
            playBeep(ToneGenerator.TONE_PROP_ACK)
        } else {
            // Kick test left/right
            if (!checkTetrisCollision(rotated, 0, -1)) {
                curCol -= 1
                currentPiece = rotated
                playBeep(ToneGenerator.TONE_PROP_ACK)
            } else if (!checkTetrisCollision(rotated, 0, 1)) {
                curCol += 1
                currentPiece = rotated
                playBeep(ToneGenerator.TONE_PROP_ACK)
            }
        }
    }

    fun hardDropTetris() {
        if (isGameOver || isPaused) return
        val piece = currentPiece ?: return
        var drop = 0
        while (!checkTetrisCollision(piece, drop + 1, 0)) {
            drop++
        }
        if (drop > 0) {
            curRow += drop
            score += drop * 2
            if (score > tetrisHighScore) {
                tetrisHighScore = score
                prefs.edit().putInt("tetris_high_score", score).apply()
            }
        }
        mergeTetrisPiece()
        triggerVibration(100)
    }

    // ----------------------------------------
    // 2. SHOOTER UPDATE MECHANICS
    // ----------------------------------------
    fun updateShooter() {
        shooterTicks++

        // A. Move bullets up
        val updatedBullets = shooterBullets.map { Pair(it.first - 1, it.second) }.filter { it.first >= 0 }
        shooterBullets.clear()
        shooterBullets.addAll(updatedBullets)

        // B. Creep enemies down
        val creepThreshold = (16 - level).coerceAtLeast(3)
        if (shooterTicks % creepThreshold == 0) {
            var breached = false
            val updatedEnemies = shooterEnemies.map {
                val nextRow = it.first + 1
                if (nextRow >= 17) breached = true
                Pair(nextRow, it.second)
            }

            if (breached) {
                lives--
                playBeep(ToneGenerator.TONE_CDMA_PIP)
                triggerVibration(200)
                shooterEnemies.clear()
                if (lives <= 0) {
                    isGameOver = true
                } else {
                    // Respawn
                    for (r in 0..2) {
                        for (c in 1..8 step 2) {
                            shooterEnemies.add(Pair(r, c))
                        }
                    }
                }
            } else {
                shooterEnemies.clear()
                shooterEnemies.addAll(updatedEnemies)
            }
        }

        // C. Check overlaps (bullet vs enemy)
        val bulletsToRemove = mutableSetOf<Pair<Int, Int>>()
        val enemiesToRemove = mutableSetOf<Pair<Int, Int>>()

        for (bullet in shooterBullets) {
            val hitIndex = shooterEnemies.firstOrNull { enemy ->
                enemy == bullet || (enemy.second == bullet.second && (enemy.first == bullet.first || enemy.first == bullet.first + 1))
            }
            if (hitIndex != null) {
                bulletsToRemove.add(bullet)
                enemiesToRemove.add(hitIndex)
                score += 15
                if (score > shooterHighScore) {
                    shooterHighScore = score
                    prefs.edit().putInt("brick_shooter_high_score", score).apply()
                }
                playBeep(ToneGenerator.TONE_PROP_ACK)
                triggerVibration(30)
            }
        }

        shooterBullets.removeAll(bulletsToRemove)
        shooterEnemies.removeAll(enemiesToRemove)

        // D. Replenish
        if (shooterEnemies.isEmpty()) {
            level++
            for (r in 0..2) {
                for (c in 0..9 step 2) {
                    shooterEnemies.add(Pair(r, c))
                    if (Random.nextBoolean()) {
                        shooterEnemies.add(Pair(r, (c + 1) % 10))
                    }
                }
            }
        } else if (shooterTicks % 60 == 0 && shooterEnemies.size < 8) {
            // periodic reinforcement
            for (c in 1..8 step 2) {
                if (!shooterEnemies.any { it.first == 0 && it.second == c }) {
                    shooterEnemies.add(Pair(0, c))
                }
            }
        }
    }

    // ----------------------------------------
    // 3. RACING UPDATE MECHANICS
    // ----------------------------------------
    fun updateRacing() {
        racingTicks++

        if (racingTicks % 3 == 0) {
            roadStripesOffset = !roadStripesOffset
        }

        val scrollSpeed = (4 - (level / 3)).coerceAtLeast(1)
        if (racingTicks % scrollSpeed == 0) {
            val survivedObstacles = mutableListOf<Pair<Int, Int>>()
            var collisionDetected = false

            for (obstacle in racingObstacles) {
                val nextR = obstacle.first + 1
                val obsLane = obstacle.second
                
                val obsCol = getLaneCol(obsLane)
                val playerCol = getLaneCol(racingPlayerLane)

                // Crash overlap formula
                val overlapR = (16..19).any { pr -> pr >= nextR && pr <= nextR + 3 }
                val overlapC = (Math.abs(playerCol - obsCol) <= 1)

                if (overlapR && overlapC) {
                    collisionDetected = true
                } else if (nextR < 20) {
                    survivedObstacles.add(Pair(nextR, obsLane))
                } else {
                    // Award points
                    score += 20
                    if (score > racingHighScore) {
                        racingHighScore = score
                        prefs.edit().putInt("brick_racing_high_score", score).apply()
                    }
                    playBeep(ToneGenerator.TONE_PROP_ACK)
                    if (score % 200 == 0) {
                        level++
                    }
                }
            }

            if (collisionDetected) {
                lives--
                playBeep(ToneGenerator.TONE_CDMA_PIP)
                triggerVibration(250)
                racingObstacles.clear()
                if (lives <= 0) {
                    isGameOver = true
                } else {
                    racingObstacles.add(Pair(-6, (racingPlayerLane + 1) % 3))
                }
            } else {
                racingObstacles.clear()
                racingObstacles.addAll(survivedObstacles)
            }
        }

        // Spawn cars
        val rate = (32 - level * 2).coerceAtLeast(15)
        if (racingTicks % rate == 0) {
            val busyLanes = racingObstacles.filter { it.first < 6 }.map { it.second }
            val allowed = (0..2).filter { !busyLanes.contains(it) }
            if (allowed.isNotEmpty()) {
                val spawnL = allowed[Random.nextInt(allowed.size)]
                racingObstacles.add(Pair(-6, spawnL))
            }
        }
    }

    // ----------------------------------------
    // 4. SNAKE UPDATE MECHANICS
    // ----------------------------------------
    fun updateSnake() {
        snakeTicks++
        val delayTicks = (5 - level / 3).coerceAtLeast(2)
        if (snakeTicks % delayTicks == 0) {
            if (snakeBody.isEmpty()) return
            val head = snakeBody[0]
            val nextH = Pair(head.first + snakeDirection.first, head.second + snakeDirection.second)

            // Board limits check
            if (nextH.first < 0 || nextH.first >= rowsCount || nextH.second < 0 || nextH.second >= colsCount) {
                playBeep(ToneGenerator.TONE_CDMA_PIP)
                triggerVibration(200)
                isGameOver = true
                return
            }

            // Self bite check
            if (snakeBody.contains(nextH)) {
                playBeep(ToneGenerator.TONE_CDMA_PIP)
                triggerVibration(200)
                isGameOver = true
                return
            }

            if (nextH == snakeFood) {
                // consume food
                snakeBody.add(0, nextH)
                score += 50
                if (score > snakeHighScore) {
                    snakeHighScore = score
                    prefs.edit().putInt("brick_snake_high_score", score).apply()
                }
                playBeep(ToneGenerator.TONE_DTMF_B)
                triggerVibration(60)

                if (score % 250 == 0) {
                    level++
                }
                spawnSnakeFood()
            } else {
                snakeBody.add(0, nextH)
                snakeBody.removeAt(snakeBody.size - 1)
            }
        }
    }

    // ----------------------------------------
    // 5. BREAKOUT UPDATE MECHANICS
    // ----------------------------------------
    fun updateBreakout() {
        ballX += ballVx
        ballY += ballVy

        val padCol = ballX.coerceIn(0f, 9f).toInt()
        val padRow = ballY.toInt()

        // Left / Right boundaries bounce
        if (ballX <= 0f) {
            ballX = 0f
            ballVx = -ballVx
            playBeep(ToneGenerator.TONE_PROP_ACK)
        } else if (ballX >= 9f) {
            ballX = 9f
            ballVx = -ballVx
            playBeep(ToneGenerator.TONE_PROP_ACK)
        }

        // Roof bounce
        if (ballY <= 0f) {
            ballY = 0f
            ballVy = -ballVy
            playBeep(ToneGenerator.TONE_PROP_ACK)
        }

        // Paddle strike reflection (paddle on row 19)
        if (ballY >= 18.5f && ballY <= 19.3f) {
            val padL = breakoutPaddleCol - 1
            val padR = breakoutPaddleCol + 1
            if (padCol in padL..padR) {
                ballY = 18.5f
                ballVy = -Math.abs(ballVy)

                // slight angle drift
                val offset = ballX - breakoutPaddleCol
                ballVx = (ballVx + offset * 0.18f).coerceIn(-0.6f, 0.6f)

                playBeep(ToneGenerator.TONE_PROP_BEEP)
                triggerVibration(45)
            }
        }

        // Brick Hit Detection (Rows 1..4)
        if (padRow in 1..4) {
            val brick = Pair(padRow, padCol)
            if (breakoutBricks.contains(brick)) {
                breakoutBricks.remove(brick)
                ballVy = -ballVy
                score += 20

                if (score > breakoutHighScore) {
                    breakoutHighScore = score
                    prefs.edit().putInt("brick_breakout_high_score", score).apply()
                }

                playBeep(ToneGenerator.TONE_DTMF_0)
                triggerVibration(30)

                if (breakoutBricks.isEmpty()) {
                    level++
                    ballVx *= 1.15f
                    ballVy *= 1.15f
                    ballX = 5.0f
                    ballY = 15.0f
                    // Repopulate
                    for (r in 1..4) {
                        for (c in 0..9) {
                            breakoutBricks.add(Pair(r, c))
                        }
                    }
                }
            }
        }

        // Bottom floor drop
        if (ballY >= 20f) {
            lives--
            playBeep(ToneGenerator.TONE_CDMA_PIP)
            triggerVibration(200)

            if (lives <= 0) {
                isGameOver = true
            } else {
                ballX = 5.0f
                ballY = 15.0f
                ballVx = 0.35f
                ballVy = -0.45f
            }
        }
    }

    // ----------------------------------------
    // RENDER MATRIX COMPU-ENGINE
    // ----------------------------------------
    fun computeRenderGrid(): List<List<Int>> {
        val result = List(rowsCount) { MutableList(colsCount) { 0 } }

        when (gameType) {
            1 -> { // Tetris
                for (r in 0 until rowsCount) {
                    for (c in 0 until colsCount) {
                        result[r][c] = tetrisGrid[r][c]
                    }
                }
                currentPiece?.let { piece ->
                    for (coord in piece.coordinates) {
                        val r = curRow + coord.first
                        val c = curCol + coord.second
                        if (r in 0 until rowsCount && c in 0 until colsCount) {
                            result[r][c] = piece.colorIndex
                        }
                    }
                }
            }
            2 -> { // Shooter
                // Player ship (Row 18-19 center col)
                if (shooterPlayerCol in 0 until colsCount) result[19][shooterPlayerCol] = 2
                if (shooterPlayerCol - 1 >= 0) result[18][shooterPlayerCol - 1] = 2
                if (shooterPlayerCol in 0 until colsCount) result[18][shooterPlayerCol] = 2
                if (shooterPlayerCol + 1 < colsCount) result[18][shooterPlayerCol + 1] = 2
                if (shooterPlayerCol in 0 until colsCount) result[17][shooterPlayerCol] = 2

                // Enemies
                for (enemy in shooterEnemies) {
                    if (enemy.first in 0 until rowsCount && enemy.second in 0 until colsCount) {
                        result[enemy.first][enemy.second] = 1
                    }
                }

                // Lasers
                for (b in shooterBullets) {
                    if (b.first in 0 until rowsCount && b.second in 0 until colsCount) {
                        result[b.first][b.second] = 3
                    }
                }
            }
            3 -> { // Racing
                // Road lines
                for (r in 0 until rowsCount) {
                    val active = if (roadStripesOffset) (r % 4 == 0 || r % 4 == 1) else (r % 4 == 2 || r % 4 == 3)
                    if (active) {
                        result[r][0] = 4
                        result[r][9] = 4
                    }
                }

                // Player F1 car shape
                val col = getLaneCol(racingPlayerLane)
                val F1Car = listOf(
                    Pair(19, col - 1), Pair(19, col + 1),
                    Pair(18, col),
                    Pair(17, col - 1), Pair(17, col), Pair(17, col + 1),
                    Pair(16, col)
                )
                for (cell in F1Car) {
                    if (cell.first in 0 until rowsCount && cell.second in 0 until colsCount) {
                        result[cell.first][cell.second] = 2
                    }
                }

                // Enemy Obstacles (3x4 Formula Cars)
                for (obs in racingObstacles) {
                    val r = obs.first
                    val enemyCol = getLaneCol(obs.second)
                    
                    val ec1 = Pair(r, enemyCol - 1)
                    val ec2 = Pair(r, enemyCol + 1)
                    val ec3 = Pair(r + 1, enemyCol)
                    val ec4 = Pair(r + 2, enemyCol - 1)
                    val ec5 = Pair(r + 2, enemyCol)
                    val ec6 = Pair(r + 2, enemyCol + 1)
                    val ec7 = Pair(r + 3, enemyCol)

                    val ecList = listOf(ec1, ec2, ec3, ec4, ec5, ec6, ec7)
                    for (cell in ecList) {
                        if (cell.first in 0 until rowsCount && cell.second in 0 until colsCount) {
                            result[cell.first][cell.second] = 1
                        }
                    }
                }
            }
            4 -> { // Snake
                // Food
                if (snakeFood.first in 0 until rowsCount && snakeFood.second in 0 until colsCount) {
                    result[snakeFood.first][snakeFood.second] = 5
                }

                // Snake segments
                for (i in snakeBody.indices) {
                    val part = snakeBody[i]
                    if (part.first in 0 until rowsCount && part.second in 0 until colsCount) {
                        result[part.first][part.second] = if (i == 0) 2 else 1
                    }
                }
            }
            5 -> { // Breakout
                // Targets
                for (brick in breakoutBricks) {
                    if (brick.first in 0 until rowsCount && brick.second in 0 until colsCount) {
                        result[brick.first][brick.second] = 1
                    }
                }

                // Paddle Row 19 (width 3)
                val padL = breakoutPaddleCol - 1
                val padR = breakoutPaddleCol + 1
                for (c in padL..padR) {
                    if (c in 0 until colsCount) result[19][c] = 2
                }

                // Ball
                val bCol = ballX.toInt().coerceIn(0, 9)
                val bRow = ballY.toInt().coerceIn(0, 19)
                result[bRow][bCol] = 3
            }
        }

        return result
    }

    // ----------------------------------------
    // CONSOLIDATED CLOCK ENGINE TICKER
    // ----------------------------------------
    var globalTicks by remember { mutableStateOf(0) }

    LaunchedEffect(gameType, isGameOver, isPaused) {
        if (gameType == 0 || isGameOver || isPaused) return@LaunchedEffect
        while (true) {
            delay(50)
            globalTicks++

            when (gameType) {
                1 -> { // Tetris tick threshold based on level
                    val limit = (16 - (level - 1) * 2).coerceAtLeast(2)
                    if (globalTicks % limit == 0) {
                        moveTetrisDown()
                    }
                }
                2 -> { // Shooter
                    updateShooter()
                }
                3 -> { // Racing
                    updateRacing()
                }
                4 -> { // Snake
                    updateSnake()
                }
                5 -> { // Breakout
                    updateBreakout()
                }
            }
        }
    }

    // ----------------------------------------
    // MASTER UI PHYSICAL CHASSIS LAYOUT
    // ----------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isMonochromeMode) Color(0xFFD4D4D4) else (if (systemIsDark) Color(0xFF0D0F14) else Color(0xFFF4F7F6)) // Adaptable master background
            )
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    var consumed = false
                    
                    if (isGameOver) {
                        if (keyCode == AndroidKeyEvent.KEYCODE_BUTTON_Y) {
                            triggerVibration(60)
                            resetAndStartGame(gameType)
                            return@onKeyEvent true
                        }
                    }
                    
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            triggerVibration(25)
                            if (gameType > 0) {
                                when (gameType) {
                                    1 -> moveTetrisLeft()
                                    2 -> shooterPlayerCol = (shooterPlayerCol - 1).coerceAtLeast(1)
                                    3 -> racingPlayerLane = (racingPlayerLane - 1).coerceAtLeast(0)
                                    4 -> { if (snakeDirection.second != 1) snakeDirection = Pair(0, -1) }
                                    5 -> breakoutPaddleCol = (breakoutPaddleCol - 1).coerceAtLeast(1)
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            triggerVibration(25)
                            if (gameType > 0) {
                                when (gameType) {
                                    1 -> moveTetrisRight()
                                    2 -> shooterPlayerCol = (shooterPlayerCol + 1).coerceAtMost(8)
                                    3 -> racingPlayerLane = (racingPlayerLane + 1).coerceAtMost(2)
                                    4 -> { if (snakeDirection.second != -1) snakeDirection = Pair(0, 1) }
                                    5 -> breakoutPaddleCol = (breakoutPaddleCol + 1).coerceAtMost(8)
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            triggerVibration(25)
                            if (gameType == 0) {
                                selectedGameId = if (selectedGameId == 1) 5 else selectedGameId - 1
                                playBeep(ToneGenerator.TONE_PROP_ACK)
                            } else {
                                when (gameType) {
                                    1 -> rotateTetrisPiece(true)
                                    4 -> { if (snakeDirection.first != 1) snakeDirection = Pair(-1, 0) }
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            triggerVibration(25)
                            if (gameType == 0) {
                                selectedGameId = if (selectedGameId == 5) 1 else selectedGameId + 1
                                playBeep(ToneGenerator.TONE_PROP_ACK)
                            } else {
                                when (gameType) {
                                    1 -> moveTetrisDown()
                                    4 -> { if (snakeDirection.first != -1) snakeDirection = Pair(1, 0) }
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_X, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                            triggerVibration(40)
                            if (gameType == 0) {
                                resetAndStartGame(selectedGameId)
                            } else {
                                when (gameType) {
                                    1 -> rotateTetrisPiece(true)
                                    2 -> { // Laser fire!
                                        if (shooterBullets.size < 4) {
                                            shooterBullets.add(Pair(16, shooterPlayerCol))
                                            playBeep(ToneGenerator.TONE_DTMF_1)
                                        }
                                    }
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_A, AndroidKeyEvent.KEYCODE_BUTTON_Y -> {
                            triggerVibration(40)
                            if (gameType == 0) {
                                resetAndStartGame(selectedGameId)
                            } else {
                                when (gameType) {
                                    1 -> rotateTetrisPiece(false)
                                    2 -> { // Fire!
                                        if (shooterBullets.size < 4) {
                                            shooterBullets.add(Pair(16, shooterPlayerCol))
                                            playBeep(ToneGenerator.TONE_DTMF_1)
                                        }
                                    }
                                }
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_B, AndroidKeyEvent.KEYCODE_SPACE, AndroidKeyEvent.KEYCODE_BUTTON_L1, AndroidKeyEvent.KEYCODE_BUTTON_R1 -> {
                            // Drop key / select key
                            if (gameType == 1) {
                                triggerVibration(50)
                                hardDropTetris()
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_START -> {
                            if (gameType > 0) {
                                triggerVibration(30)
                                isPaused = !isPaused
                                playBeep(ToneGenerator.TONE_PROP_ACK)
                            }
                            consumed = true
                        }
                        AndroidKeyEvent.KEYCODE_BUTTON_SELECT, AndroidKeyEvent.KEYCODE_ESCAPE -> {
                            if (gameType > 0) {
                                triggerVibration(30)
                                gameType = 0 // Go to Menu
                                playBeep(ToneGenerator.TONE_PROP_NACK)
                            }
                            consumed = true
                        }
                    }
                    return@onKeyEvent consumed
                }
                false
            }
            .testTag("tetris_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = if (isFullscreenMode) {
                Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            } else {
                Modifier
                    .widthIn(max = 450.dp)
                    .then(if (isGamepadConnectedState) Modifier.wrapContentHeight() else Modifier.fillMaxHeight())
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        if (isMonochromeMode) Color(0xFFC4C8C5) else (if (systemIsDark) Color(0xFF161924) else Color(0xFFE9F0EE)) // Modern Obsidian vs Light Sage
                    )
                    .border(
                        width = 2.dp,
                        color = if (isMonochromeMode) Color(0xFF909490) else (if (systemIsDark) Color(0xFF2E3545) else Color(0xFFBDC8C4)), // Charcoal vs Sage border
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(16.dp)
            },
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // 1. TOP MODULE: BEZEL & SYSTEM LOGO HEADER
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isFullscreenMode) {
                    // Slim header for Fullscreen gamepad mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .background(if (isMonochromeMode) Color(0xFFE5E7EB) else Color(0xFF1F2937), RoundedCornerShape(8.dp))
                            .border(1.dp, if (isMonochromeMode) Color(0xFF9CA3AF) else Color(0xFF374151), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Keluar",
                                    tint = if (isMonochromeMode) Color(0xFF333333) else Color(0xFF00FFD2),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "MODE GAMEPAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMonochromeMode) Color(0xFF1F2937) else Color(0xFF00FFD2),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    forceFullscreenGamepadMode = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Keluar Layar Penuh",
                                    fontSize = 11.sp,
                                    color = if (isMonochromeMode) Color(0xFF991B1B) else Color(0xFFFF007F),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMonochromeMode = !isMonochromeMode
                                    triggerVibration()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMonochromeMode) Icons.Default.Palette else Icons.Default.ColorLens,
                                    contentDescription = "Ganti Warna",
                                    tint = if (isMonochromeMode) Color(0xFF306230) else Color(0xFFFF007F),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideogameAsset,
                                    contentDescription = "Gamepad Icon",
                                    tint = if (isGamepadConnectedState) Color(0xFF10B981) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isGamepadConnectedState) Color(0xFF10B981) else Color.Gray)
                                )
                            }
                        }
                    }
                } else {
                    // Cleaner layout space with no back button & top title as requested by user
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // SCREEN RETRO BEZEL (Sig asymmetric cutouts / Responsive size)
                Box(
                    modifier = if (isFullscreenMode) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isMonochromeMode) {
                                    Color(0xFF5D6164)
                                } else {
                                    if (systemIsDark) Color(0xFF0A0D15) else Color(0xFFCBD5E1)
                                }
                            )
                            .padding(4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.92f)
                            .clip(RoundedCornerShape(12.dp, 12.dp, 40.dp, 12.dp))
                            .background(
                                if (isMonochromeMode) {
                                    Color(0xFF5D6164)
                                } else {
                                    if (systemIsDark) Color(0xFF0A0D15) else Color(0xFFCBD5E1) // Sleek light silver aluminum bezel / cyber glass
                                }
                            )
                            .border(
                                width = 4.dp,
                                color = if (isMonochromeMode) {
                                    Color(0xFF303233)
                                } else {
                                    if (systemIsDark) Color(0xFF1E2430) else Color(0xFF94A3B8)
                                    },
                                shape = RoundedCornerShape(12.dp, 12.dp, 40.dp, 12.dp)
                            )
                            .padding(12.dp)
                    }
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        
                        // LEFT BATTERY COLUMN indicator
                        if (!isFullscreenMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(20.dp)
                                    .padding(top = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isGameOver) {
                                                Color(0xFF4A4A4A)
                                            } else {
                                                if (isMonochromeMode) Color(0xFFFF2D55) else Color(0xFF39FF14) // Glowing neon-green power LED
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "BATT",
                                    fontSize = 6.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMonochromeMode) Color.LightGray else screenSecondaryTextColor,
                                    textAlign = TextAlign.Center
                                  )
                            }
                        }

                        // THE CLASSIC LCD SCREEN PANEL
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    translationX = screenShakeOffset.x
                                    translationY = screenShakeOffset.y
                                }
                                .clip(RoundedCornerShape(if (isFullscreenMode) 12.dp else 6.dp))
                                .background(screenPanelBgColor)
                                .border(
                                    width = if (isFullscreenMode) 3.dp else 2.dp,
                                    color = screenPanelBorderColor
                                )
                                .padding(if (isFullscreenMode) 12.dp else 6.dp)
                        ) {
                            
                            if (gameType == 0) {
                                // ----------------------------------------
                                // SUB-LOBBY: 9999 IN 1 LOBBY SELECTION ENGINE
                                // ----------------------------------------
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "★ 9999 IN 1 ★",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = accentCyanColor
                                        )
                                        Text(
                                            text = "BRICK GAME CONSOLE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = screenSecondaryTextColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Gamepad Support Toggle (moved below the title as requested)
                                            IconButton(
                                                onClick = {
                                                    forceFullscreenGamepadMode = !forceFullscreenGamepadMode
                                                    triggerVibration()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VideogameAsset,
                                                    contentDescription = "Mode Gamepad",
                                                    tint = if (isFullscreenMode) accentCyanColor else screenPrimaryTextColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Color Palette Toggle (moved below the title as requested)
                                            IconButton(
                                                onClick = {
                                                    isMonochromeMode = !isMonochromeMode
                                                    triggerVibration()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isMonochromeMode) Icons.Default.Palette else Icons.Default.ColorLens,
                                                    contentDescription = "Ganti Model Warna",
                                                    tint = if (isMonochromeMode) accentCyanColor else screenPrimaryTextColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(accentPinkColor.copy(alpha = 0.3f))
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            "01. TETRIS KLASIK",
                                            "02. TEMBAK BALOK (SHOOTER)",
                                            "03. BALAPAN MOBIL (RACING)",
                                            "04. GAME ULAR (SNAKE)",
                                            "05. BREAKOUT (BATU)"
                                        ).forEachIndexed { index, title ->
                                            val id = index + 1
                                            val active = (id == selectedGameId)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (active) {
                                                            if (isMonochromeMode) Color(0xFF306230).copy(alpha = 0.2f) else accentCyanColor.copy(alpha = 0.15f)
                                                        } else Color.Transparent
                                                    )
                                                    .clickable { 
                                                        selectedGameId = id
                                                        playBeep(ToneGenerator.TONE_PROP_ACK)
                                                    }
                                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (active) "▶ " else "  ",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp,
                                                    color = accentCyanColor
                                                )
                                                Text(
                                                    text = title,
                                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (active) screenPrimaryTextColor else screenSecondaryTextColor
                                                )
                                            }
                                        }
                                    }

                                    // Display High Score of focused game in lobby footer
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val selectedHiScore = when (selectedGameId) {
                                            1 -> tetrisHighScore
                                            2 -> shooterHighScore
                                            3 -> racingHighScore
                                            4 -> snakeHighScore
                                            else -> breakoutHighScore
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(accentPinkColor.copy(alpha = 0.3f))
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "HI-SKOR: $selectedHiScore",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = accentYellowColor
                                        )
                                    }
                                }

                            } else {
                                // ----------------------------------------
                                // GAMES RUNNING ACTIVE VIEW SCREEN
                                // ----------------------------------------
                                Row(modifier = Modifier.fillMaxSize()) {
                                    
                                    // A. PLAYING GRID BLOCK MATRIX Canvas (10x20)
                                    Box(
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .fillMaxHeight()
                                            .border(1.dp, playingGridBorderColor)
                                            .background(playingGridBgColor)
                                    ) {
                                        val gridMatrix = remember(globalTicks, gameType) { computeRenderGrid() }
                                        
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val w = size.width / colsCount
                                            val h = size.height / rowsCount
                                            
                                            // Draw refined, modern cell grid lines on OLED / LCD
                                            val guideColor = if (isMonochromeMode) {
                                                Color(0xFF306230).copy(alpha = 0.08f)
                                            } else {
                                                if (systemIsDark) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFFCBD5E1).copy(alpha = 0.5f)
                                            }
                                            for (r in 0 until rowsCount) {
                                                for (c in 0 until colsCount) {
                                                    drawRect(
                                                        color = guideColor,
                                                        topLeft = Offset(c * w, r * h),
                                                        size = Size(w, h),
                                                        style = Stroke(width = 0.5f)
                                                    )
                                                }
                                            }

                                            for (r in 0 until rowsCount) {
                                                for (c in 0 until colsCount) {
                                                    val type = gridMatrix[r][c]
                                                    if (type != 0) {
                                                        if (isMonochromeMode) {
                                                            val finalColor = if (type == 2) GAMEBOY_COLOR_MAP[1] else GAMEBOY_COLOR_MAP[2]
                                                            drawRect(
                                                                color = finalColor,
                                                                topLeft = Offset(c * w + 1f, r * h + 1f),
                                                                size = Size(w - 2f, h - 2f)
                                                            )
                                                            // nostalgic overlay panel bezel
                                                            drawRect(
                                                                color = Color.Black.copy(alpha = 0.25f),
                                                                topLeft = Offset(c * w + 3f, r * h + 3f),
                                                                size = Size(w - 6f, h - 6f),
                                                                style = Stroke(width = 1.3f)
                                                            )
                                                        } else {
                                                            val baseColor = blockColors.getOrElse(type - 1) { blockColors[0] }
                                                            drawPyramidBlock(
                                                                color = baseColor,
                                                                topLeft = Offset(c * w, r * h),
                                                                size = Size(w, h)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // PAUSED Screen Overlay
                                        if (isPaused) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (isMonochromeMode) Color(0xFF8BAC0F).copy(alpha = 0.9f) else screenPanelBgColor.copy(alpha = 0.85f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "PAUSED",
                                                    color = accentCyanColor,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 2.sp
                                                )
                                            }
                                        }

                                        // GAME OVER Screen Overlay
                                        if (isGameOver) {
                                            // Dynamic flashing border effect for Tetris game over
                                            val flashBorderColor = if (gameType == 1 && (globalTicks % 8 < 4)) accentPinkColor.copy(alpha = 0.5f) else Color.Transparent
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (isMonochromeMode) Color(0xFF0F380F).copy(alpha = 0.92f) else screenPanelBgColor.copy(alpha = 0.88f)
                                                    )
                                                    .then(
                                                        if (gameType == 1) {
                                                            Modifier.border(width = 3.dp, color = flashBorderColor)
                                                        } else Modifier
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = "GAME OVER",
                                                        color = accentPinkColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace,
                                                        letterSpacing = 1.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Skor: $score",
                                                        color = screenPrimaryTextColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    
                                                    if (isGamepadConnectedState) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = "TEKAN SEGITIGA\nUNTUK RESTART",
                                                            color = if (isMonochromeMode) Color(0xFF8BAC0F) else accentCyanColor,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace,
                                                            textAlign = TextAlign.Center,
                                                            lineHeight = 11.sp
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Button(
                                                        onClick = { resetAndStartGame(gameType) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = accentPinkColor
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = "MAINKAN",
                                                            color = if (isMonochromeMode) Color(0xFF0F380F) else Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // B. RIGHT STATUS CONTROL PANEL
                                    Column(
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Level / Game ID indicator
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "GAME ${"A0$gameType"}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentCyanColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "L$level",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = screenPrimaryTextColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // Lives display (irrelevant for tetris, snake)
                                        if (gameType == 2 || gameType == 3 || gameType == 5) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "LIVES",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = screenSecondaryTextColor,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                                    repeat(lives) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(accentPinkColor)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Next Preview (Only for Tetris)
                                            if (gameType == 1) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .border(1.dp, nextBoxBorderColor)
                                                        .background(nextBoxBgColor)
                                                        .padding(2.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "NEXT",
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = screenSecondaryTextColor,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(34.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Canvas(modifier = Modifier.size(24.dp)) {
                                                            nextPiece?.let { piece ->
                                                                val sz = 5.dp.toPx()
                                                                val offX = size.width / 2
                                                                val offY = size.height / 2

                                                                for (coord in piece.coordinates) {
                                                                    val blockX = offX + coord.second * sz
                                                                    val blockY = offY + coord.first * sz
                                                                    if (isMonochromeMode) {
                                                                        drawRect(
                                                                            color = GAMEBOY_COLOR_MAP[2],
                                                                            topLeft = Offset(blockX, blockY),
                                                                            size = Size(sz - 1f, sz - 1f)
                                                                        )
                                                                        drawRect(
                                                                            color = Color.Black,
                                                                            topLeft = Offset(blockX, blockY),
                                                                            size = Size(sz - 1f, sz - 1f),
                                                                            style = Stroke(width = 0.5f)
                                                                        )
                                                                    } else {
                                                                        val baseColor = blockColors.getOrElse(piece.colorIndex - 1) { blockColors[0] }
                                                                        drawPyramidBlock(
                                                                            color = baseColor,
                                                                            topLeft = Offset(blockX, blockY),
                                                                            size = Size(sz, sz)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // SCORE
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "SCORE",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = screenSecondaryTextColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "$score",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = screenPrimaryTextColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // HI-SCORE
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "HI-SC",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = screenSecondaryTextColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            val currentHi = when (gameType) {
                                                1 -> tetrisHighScore
                                                2 -> shooterHighScore
                                                3 -> racingHighScore
                                                4 -> snakeHighScore
                                                else -> breakoutHighScore
                                            }
                                            Text(
                                                text = "$currentHi",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = accentYellowColor,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isFullscreenMode) {
                // 2. MID LOGO & SPEAKER SLOTS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMonochromeMode) "Classic Retro Bot" else if (systemIsDark) "★ PREMIUM OLED BOT ★" else "★ SMART BRICK LCD ★",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        color = accentCyanColor
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(16.dp)
                                    .graphicsLayer(rotationZ = -28f)
                                    .background(if (isMonochromeMode) Color(0xFF8F9390) else Color(0xFFFF007F))
                            )
                        }
                    }
                }

                // 3. HARDWARE CONTROL KEYS (D-PAD & ACTION PILLS)
                if (!isGamepadConnectedState) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    // LEFT SIDE: SEAMLESS PLIANT RETRO CROSS D-PAD
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(if (isMonochromeMode) Color(0xFFB1B5B2) else (if (systemIsDark) Color(0xFF131924) else Color(0xFFCBD5D2)))
                        )

                        Box(modifier = Modifier.size(120.dp)) {
                            // Up D-pad
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (isMonochromeMode) Color(0xFF282B2A) else (if (systemIsDark) Color(0xFF1B2332) else Color(0xFF4B5563)))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        triggerVibration(25)
                                        if (gameType == 0) {
                                            selectedGameId = if (selectedGameId == 1) 5 else selectedGameId - 1
                                            playBeep(ToneGenerator.TONE_PROP_ACK)
                                        } else {
                                            when (gameType) {
                                                1 -> rotateTetrisPiece(true)
                                                4 -> { if (snakeDirection.first != 1) snakeDirection = Pair(-1, 0) }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropUp, 
                                    contentDescription = null, 
                                    tint = if (isMonochromeMode) Color.LightGray else (if (systemIsDark) Color(0xFF00FFD2) else Color.White),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Down D-pad
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                    .background(if (isMonochromeMode) Color(0xFF282B2A) else (if (systemIsDark) Color(0xFF1B2332) else Color(0xFF4B5563)))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        triggerVibration(25)
                                        if (gameType == 0) {
                                            selectedGameId = if (selectedGameId == 5) 1 else selectedGameId + 1
                                            playBeep(ToneGenerator.TONE_PROP_ACK)
                                        } else {
                                            when (gameType) {
                                                1 -> moveTetrisDown()
                                                4 -> { if (snakeDirection.first != -1) snakeDirection = Pair(1, 0) }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown, 
                                    contentDescription = null, 
                                    tint = if (isMonochromeMode) Color.LightGray else (if (systemIsDark) Color(0xFF00FFD2) else Color.White),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Left D-pad
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                    .background(if (isMonochromeMode) Color(0xFF282B2A) else (if (systemIsDark) Color(0xFF1B2332) else Color(0xFF4B5563)))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        triggerVibration(25)
                                        if (gameType > 0) {
                                            when (gameType) {
                                                1 -> moveTetrisLeft()
                                                2 -> shooterPlayerCol = (shooterPlayerCol - 1).coerceAtLeast(1)
                                                3 -> racingPlayerLane = (racingPlayerLane - 1).coerceAtLeast(0)
                                                4 -> { if (snakeDirection.second != 1) snakeDirection = Pair(0, -1) }
                                                5 -> breakoutPaddleCol = (breakoutPaddleCol - 1).coerceAtLeast(1)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowBack, null, tint = if (isMonochromeMode) Color.LightGray else (if (systemIsDark) Color(0xFF00FFD2) else Color.White), modifier = Modifier.size(20.dp))
                            }

                            // Right D-pad
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                    .background(if (isMonochromeMode) Color(0xFF282B2A) else (if (systemIsDark) Color(0xFF1B2332) else Color(0xFF4B5563)))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        triggerVibration(25)
                                        if (gameType > 0) {
                                            when (gameType) {
                                                1 -> moveTetrisRight()
                                                2 -> shooterPlayerCol = (shooterPlayerCol + 1).coerceAtMost(8)
                                                3 -> racingPlayerLane = (racingPlayerLane + 1).coerceAtMost(2)
                                                4 -> { if (snakeDirection.second != -1) snakeDirection = Pair(0, 1) }
                                                5 -> breakoutPaddleCol = (breakoutPaddleCol + 1).coerceAtMost(8)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ArrowForward, null, tint = if (isMonochromeMode) Color.LightGray else (if (systemIsDark) Color(0xFF00FFD2) else Color.White), modifier = Modifier.size(20.dp))
                            }

                            // Center core box
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(40.dp)
                                    .background(if (isMonochromeMode) Color(0xFF282B2A) else (if (systemIsDark) Color(0xFF1B2332) else Color(0xFF4B5563)))
                            )
                        }
                    }

                    // RIGHT SIDE: TILTED TACTILE B & A ACTION PILLS
                    Box(
                        modifier = Modifier
                            .width(170.dp)
                            .height(118.dp)
                            .graphicsLayer(rotationZ = -28f)
                            .background(if (isMonochromeMode) Color(0xFFB1B5B2) else (if (systemIsDark) Color(0xFF131924) else Color(0xFFCBD5D2)), RoundedCornerShape(20.dp))
                            .border(1.dp, if (isMonochromeMode) Color(0xFF9EA29F) else (if (systemIsDark) Color(0xFFFF007F).copy(alpha = 0.5f) else Color(0xFF059669).copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Button B: Rotate counter clockwise
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer(rotationZ = 28f)
                            ) {
                                Text(text = "B", fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (isMonochromeMode) Color(0xFF333333) else (if (systemIsDark) Color.White else Color(0xFF111827)))
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (isMonochromeMode) Color(0xFF8A1E3C) else (if (systemIsDark) Color(0xFFFF0055) else Color(0xFF3B82F6))) // Matte Ruby Red / Laser Red / Electric Blue
                                        .border(2.dp, if (isMonochromeMode) Color(0xFF550E22) else (if (systemIsDark) Color(0xFFFF007F) else Color(0xFF2563EB)), CircleShape)
                                        .clickable {
                                            triggerVibration(40)
                                            if (gameType == 0) {
                                                resetAndStartGame(selectedGameId)
                                            } else {
                                                when (gameType) {
                                                    1 -> rotateTetrisPiece(false)
                                                    2 -> { // Fire bullet!
                                                        if (shooterBullets.size < 4) {
                                                            shooterBullets.add(Pair(16, shooterPlayerCol))
                                                            playBeep(ToneGenerator.TONE_DTMF_1)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                )
                            }

                            // Button A: Rotate clockwise / Trigger main shooting action
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer(rotationZ = 28f)
                            ) {
                                Text(text = "A", fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (isMonochromeMode) Color(0xFF333333) else (if (systemIsDark) Color.White else Color(0xFF111827)))
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (isMonochromeMode) Color(0xFF8A1E3C) else (if (systemIsDark) Color(0xFFFF0055) else Color(0xFF059669))) // Neo-mint accent
                                        .border(2.dp, if (isMonochromeMode) Color(0xFF550E22) else (if (systemIsDark) Color(0xFFFF007F) else Color(0xFF047857)), CircleShape)
                                        .clickable {
                                            triggerVibration(40)
                                            if (gameType == 0) {
                                                resetAndStartGame(selectedGameId)
                                            } else {
                                                when (gameType) {
                                                    1 -> rotateTetrisPiece(true)
                                                    2 -> { // Laser fire!
                                                        if (shooterBullets.size < 4) {
                                                            shooterBullets.add(Pair(16, shooterPlayerCol))
                                                            playBeep(ToneGenerator.TONE_DTMF_1)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // BOTTOM AREA: PHYSICAL PILL STRAP CONTROLS (SELECT, START, RESET)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    // Column 1: hard drop key (select)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(18.dp)
                                .graphicsLayer(rotationZ = -28f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMonochromeMode) Color(0xFF7A7E81) else (if (systemIsDark) Color(0xFF00FFD2).copy(alpha = 0.25f) else Color(0xFF3B82F6).copy(alpha = 0.2f))) // Rubber grey / glowing cyan / Electric blue
                                .border(1.dp, if (isMonochromeMode) Color(0xFF5A5E61) else (if (systemIsDark) Color(0xFF00FFD2) else Color(0xFF3B82F6)), RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerVibration(30)
                                    if (gameType == 1) {
                                        hardDropTetris()
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "DROP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isMonochromeMode) Color(0xFF5D6164) else (if (systemIsDark) Color(0xFF00FFD2) else Color(0xFF1D4ED8)),
                            letterSpacing = 1.sp
                        )
                    }

                    // Column 2: START / PAUS button (toggle pause)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(18.dp)
                                .graphicsLayer(rotationZ = -28f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMonochromeMode) Color(0xFF7A7E81) else (if (systemIsDark) Color(0xFF00FFD2).copy(alpha = 0.25f) else Color(0xFF059669).copy(alpha = 0.2f))) // Neo-mint
                                .border(1.dp, if (isMonochromeMode) Color(0xFF5A5E61) else (if (systemIsDark) Color(0xFF00FFD2) else Color(0xFF059669)), RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerVibration(30)
                                    if (gameType == 0) {
                                        resetAndStartGame(selectedGameId)
                                    } else {
                                        isPaused = !isPaused
                                        playBeep(ToneGenerator.TONE_PROP_ACK)
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "START / PAUS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isMonochromeMode) Color(0xFF5D6164) else (if (systemIsDark) Color(0xFF00FFD2) else Color(0xFF047857)),
                            letterSpacing = 1.sp
                        )
                    }

                    // Column 3: RESET key (GOTO LOBBY MENU directly)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(18.dp)
                                .graphicsLayer(rotationZ = -28f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMonochromeMode) Color(0xFF901010) else (if (systemIsDark) Color(0xFFFF0055).copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.2f))) // Red / glowing crimson / Light crimson
                                .border(1.dp, if (isMonochromeMode) Color(0xFF600000) else (if (systemIsDark) Color(0xFFFF0055) else Color(0xFFEF4444)), RoundedCornerShape(8.dp))
                                .clickable {
                                    triggerVibration(80)
                                    if (gameType == 0) {
                                        MainViewModel.instance?.showToastPopup("Sudah di Menu Utama!")
                                    } else {
                                        gameType = 0
                                        isGameOver = false
                                        isPaused = false
                                        MainViewModel.instance?.showToastPopup("Kembali ke Menu Game Lobby!")
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MENU",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isMonochromeMode) Color(0xFF901010) else (if (systemIsDark) Color(0xFFFF0055) else Color(0xFFDC2626)),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            }
            } else {
                if (!isGamepadConnectedState) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isMonochromeMode) Color(0xFFE5E7EB) else Color(0xFF1F2937), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Petunjuk",
                            tint = if (isMonochromeMode) Color.DarkGray else Color(0xFF00FFD2),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PETUNJUK GAMEPAD:\nD-PAD = Arah | TOMBOL A/B = Putar/Tembak | START = Pause | SELECT = Menu Game",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonochromeMode) Color.DarkGray else Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
