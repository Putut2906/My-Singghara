package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Audio Synthesizer for Jigsaw Puzzle
 * Generates natural, soothing interactive sounds without requiring external assets.
 */
object JigsawSound {
    var isSoundEnabled = true

    fun playTone(freq: Double, durationMs: Int, volume: Float = 0.15f) {
        if (!isSoundEnabled) return
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            var track: AudioTrack? = null
            try {
                val sampleRate = 16000
                val numSamples = (durationMs * sampleRate / 1000)
                val buffer = ByteArray(2 * numSamples)
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                    val sample = (Math.sin(angle) * 32767 * volume).toInt().coerceIn(-32768, 32767).toShort()
                    val idx = 2 * i
                    buffer[idx] = (sample.toInt() and 0x00ff).toByte()
                    buffer[idx + 1] = ((sample.toInt() and 0xff00) ushr 8).toByte()
                }
                track = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size,
                    AudioTrack.MODE_STATIC
                )
                track.write(buffer, 0, buffer.size)
                track.play()
                delay(durationMs + 80L)
            } catch (_: Exception) {
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun playPickup() {
        playTone(620.0, 35, 0.12f)
    }

    fun playRotate() {
        playTone(740.0, 45, 0.14f)
    }

    fun playSnap() {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            playTone(587.33, 40, 0.16f) // D5
            delay(40)
            playTone(880.00, 70, 0.20f) // A5
        }
    }

    fun playError() {
        playTone(220.0, 90, 0.15f)
    }

    fun playVictory() {
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) {
            val notes = listOf(523.25, 659.25, 783.99, 1046.50)
            for (note in notes) {
                playTone(note, 90, 0.22f)
                delay(85)
            }
        }
    }
}

/**
 * Anatomi Sisi Kepingan Interlocking (Saling Mengunci)
 * 1. Tab (Tonjolan / Knob / Outie / Key): Bagian sisi yang menganjur keluar atau menonjol.
 * 2. Blank (Cekungan / Socket / Hole / Innie / Slot / Lubang): Bagian sisi yang berupa cekungan atau lubang tempat masuknya tab dari kepingan lain.
 * 3. Flat (Rata): Sisi bingkai luar puzzle.
 */
enum class EdgeType(
    val title: String,
    val alias: String,
    val definition: String,
    val accentColor: Color
) {
    FLAT(
        title = "Rata",
        alias = "Sisi Bingkai Tepi",
        definition = "Sisi lurus tanpa tonjolan atau lubang yang membentuk batas luar gambar puzzle.",
        accentColor = Color(0xFF757575)
    ),
    TAB(
        title = "Tab",
        alias = "Knob / Outie / Key / Tonjolan",
        definition = "Bagian sisi yang menganjur keluar atau menonjol untuk mengunci ke dalam kepingan tetangga.",
        accentColor = Color(0xFF2E7D32) // Emerald Green
    ),
    BLANK(
        title = "Blank",
        alias = "Socket / Hole / Innie / Slot / Lubang",
        definition = "Bagian sisi yang berupa cekungan atau lubang tempat masuknya tab dari kepingan lain.",
        accentColor = Color(0xFF8E24AA) // Purple
    )
}

/**
 * Menggambar kurva interlocking (Tab atau Blank) pada sebuah sisi kepingan.
 * Tab: menganjur keluar (dir = 1f)
 * Blank: mencekung ke dalam (dir = -1f)
 * Flat: garis lurus (dir = 0f)
 */
fun drawInterlockingEdge(
    path: Path,
    x0: Float, y0: Float,
    x1: Float, y1: Float,
    edgeType: EdgeType
) {
    if (edgeType == EdgeType.FLAT) {
        path.lineTo(x1, y1)
        return
    }

    val dx = x1 - x0
    val dy = y1 - y0
    val length = kotlin.math.hypot(dx, dy)
    if (length <= 0f) return

    val tx = dx / length
    val ty = dy / length

    // Outward normal vector (pointing left of movement vector)
    val nx = ty
    val ny = -tx

    val dir = if (edgeType == EdgeType.TAB) 1f else -1f
    val depth = length * 0.20f * dir

    // Base segment before neck
    val p1x = x0 + tx * (length * 0.36f)
    val p1y = y0 + ty * (length * 0.36f)
    path.lineTo(p1x, p1y)

    // Neck pinch outward to head base
    val c1x = x0 + tx * (length * 0.36f) + nx * (depth * 0.35f)
    val c1y = y0 + ty * (length * 0.36f) + ny * (depth * 0.35f)
    val c2x = x0 + tx * (length * 0.40f) + nx * (depth * 0.90f)
    val c2y = y0 + ty * (length * 0.40f) + ny * (depth * 0.90f)
    val head1x = x0 + tx * (length * 0.44f) + nx * depth
    val head1y = y0 + ty * (length * 0.44f) + ny * depth
    path.cubicTo(c1x, c1y, c2x, c2y, head1x, head1y)

    // Head apex
    val apex1x = x0 + tx * (length * 0.47f) + nx * (depth * 1.08f)
    val apex1y = y0 + ty * (length * 0.47f) + ny * (depth * 1.08f)
    val apex2x = x0 + tx * (length * 0.53f) + nx * (depth * 1.08f)
    val apex2y = y0 + ty * (length * 0.53f) + ny * (depth * 1.08f)
    val head2x = x0 + tx * (length * 0.56f) + nx * depth
    val head2y = y0 + ty * (length * 0.56f) + ny * depth
    path.cubicTo(apex1x, apex1y, apex2x, apex2y, head2x, head2y)

    // Neck return to base
    val c3x = x0 + tx * (length * 0.60f) + nx * (depth * 0.90f)
    val c3y = y0 + ty * (length * 0.60f) + ny * (depth * 0.90f)
    val c4x = x0 + tx * (length * 0.64f) + nx * (depth * 0.35f)
    val c4y = y0 + ty * (length * 0.64f) + ny * (depth * 0.35f)
    val p2x = x0 + tx * (length * 0.64f)
    val p2y = y0 + ty * (length * 0.64f)
    path.cubicTo(c3x, c3y, c4x, c4y, p2x, p2y)

    path.lineTo(x1, y1)
}

enum class PuzzleTheme(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val drawableRes: Int? = null
) {
    ALAM("Alam", Icons.Default.Landscape, "Pegunungan & Air Terjun Eksotis", R.drawable.img_puzzle_nature),
    HEWAN("Hewan", Icons.Default.Pets, "Harimau Rimba Tropis", R.drawable.img_puzzle_animal),
    SENI("Karya Seni", Icons.Default.Palette, "Stupa Borobudur Senja Emas", R.drawable.img_puzzle_art),
    FOTO_PRIBADI("Foto Pribadi", Icons.Default.AddPhotoAlternate, "Gunakan foto dari galeri HP Anda", null)
}

data class PuzzleDifficulty(
    val id: String,
    val label: String,
    val rows: Int,
    val cols: Int,
    val subtitle: String
) {
    val totalPieces: Int get() = rows * cols
}

val PUZZLE_DIFFICULTIES = listOf(
    PuzzleDifficulty("9_pieces", "9 Keping", 3, 3, "Pemula / Santai"),
    PuzzleDifficulty("16_pieces", "16 Keping", 4, 4, "Mudah"),
    PuzzleDifficulty("24_pieces", "24 Keping", 4, 6, "Standar"),
    PuzzleDifficulty("36_pieces", "36 Keping", 6, 6, "Menengah"),
    PuzzleDifficulty("48_pieces", "48 Keping", 6, 8, "Tantangan"),
    PuzzleDifficulty("64_pieces", "64 Keping", 8, 8, "Mahir"),
    PuzzleDifficulty("100_pieces", "100 Keping", 10, 10, "Master Puzzle")
)

data class JigsawPiece(
    val id: Int,
    val row: Int,
    val col: Int,
    val bitmap: Bitmap,
    var currentRotation: Int = 0, // 0, 90, 180, 270
    var isPlaced: Boolean = false,
    val isEdge: Boolean,
    val topEdge: EdgeType,
    val rightEdge: EdgeType,
    val bottomEdge: EdgeType,
    val leftEdge: EdgeType
) {
    val tabCount: Int get() = listOf(topEdge, rightEdge, bottomEdge, leftEdge).count { it == EdgeType.TAB }
    val blankCount: Int get() = listOf(topEdge, rightEdge, bottomEdge, leftEdge).count { it == EdgeType.BLANK }
}

enum class GameScreenState {
    THEME_SELECTION,
    PLAYING
}

enum class PieceFilterMode {
    ALL,
    EDGE_ONLY,
    TABS_DOMINANT,
    BLANKS_DOMINANT
}

@Composable
fun JigsawPuzzleGame(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }

    // Navigation & Configuration States
    var screenState by remember { mutableStateOf(GameScreenState.THEME_SELECTION) }
    var selectedTheme by remember { mutableStateOf(PuzzleTheme.ALAM) }
    var customPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDifficulty by remember { mutableStateOf(PUZZLE_DIFFICULTIES[2]) } // Default 24 keping
    var allowRotation by remember { mutableStateOf(false) }
    var isSoundOn by remember { mutableStateOf(prefs.getBoolean("jigsaw_sound", true)) }

    // Active Game States
    var fullBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pieces by remember { mutableStateOf<List<JigsawPiece>>(emptyList()) }
    var isLoadingPieces by remember { mutableStateOf(false) }

    // Playback and interaction
    var selectedPieceId by remember { mutableStateOf<Int?>(null) }
    var draggedPieceId by remember { mutableStateOf<Int?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var boardRootOffset by remember { mutableStateOf(Offset.Zero) }
    var boardSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Helpers & Modals
    var filterMode by remember { mutableStateOf(PieceFilterMode.ALL) }
    var ghostOpacity by remember { mutableStateOf(0.18f) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var showVictoryDialog by remember { mutableStateOf(false) }
    var showInterlockingInfoDialog by remember { mutableStateOf(false) }
    var showHintFlashId by remember { mutableStateOf<Int?>(null) }

    // Stats
    var elapsedSeconds by remember { mutableStateOf(0) }
    var movesCount by remember { mutableStateOf(0) }
    var isTimerActive by remember { mutableStateOf(false) }

    // Gallery Picker Contract
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customPhotoUri = uri
            selectedTheme = PuzzleTheme.FOTO_PRIBADI
        }
    }

    // Sound effect preference sync
    LaunchedEffect(isSoundOn) {
        JigsawSound.isSoundEnabled = isSoundOn
        prefs.edit().putBoolean("jigsaw_sound", isSoundOn).apply()
    }

    // Timer Loop
    LaunchedEffect(isTimerActive, screenState) {
        if (isTimerActive && screenState == GameScreenState.PLAYING) {
            while (isTimerActive) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Function to load, slice image, and calculate complementary interlocking edges (Tabs & Blanks)
    fun startPuzzleGame() {
        isLoadingPieces = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val loadedBitmap: Bitmap? = if (selectedTheme == PuzzleTheme.FOTO_PRIBADI && customPhotoUri != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = ImageDecoder.createSource(context.contentResolver, customPhotoUri!!)
                            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                decoder.isMutableRequired = true
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(context.contentResolver, customPhotoUri)
                        }
                    } catch (e: Exception) {
                        BitmapFactory.decodeResource(context.resources, R.drawable.img_puzzle_nature)
                    }
                } else {
                    val resId = selectedTheme.drawableRes ?: R.drawable.img_puzzle_nature
                    BitmapFactory.decodeResource(context.resources, resId)
                }

                if (loadedBitmap == null) {
                    withContext(Dispatchers.Main) {
                        isLoadingPieces = false
                    }
                    return@launch
                }

                // Crop & scale to crisp square dimensions (720x720)
                val targetDim = 720
                val minSide = kotlin.math.min(loadedBitmap.width, loadedBitmap.height)
                val xStart = (loadedBitmap.width - minSide) / 2
                val yStart = (loadedBitmap.height - minSide) / 2
                val squareBitmap = Bitmap.createBitmap(loadedBitmap, xStart, yStart, minSide, minSide)
                val scaledFullBitmap = Bitmap.createScaledBitmap(squareBitmap, targetDim, targetDim, true)

                // Grid configuration
                val rows = selectedDifficulty.rows
                val cols = selectedDifficulty.cols
                val pieceW = targetDim / cols
                val pieceH = targetDim / rows

                // Generate complementary interlocking edges for every interior boundary:
                // Horizontal boundaries: (rows - 1) x cols
                // If true: top piece has TAB at bottom, bottom piece has BLANK at top.
                val hEdges = Array(rows - 1) { BooleanArray(cols) { Random.nextBoolean() } }

                // Vertical boundaries: rows x (cols - 1)
                // If true: left piece has TAB at right, right piece has BLANK at left.
                val vEdges = Array(rows) { BooleanArray(cols - 1) { Random.nextBoolean() } }

                val pieceList = mutableListOf<JigsawPiece>()
                var pieceId = 0

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val pieceBitmap = Bitmap.createBitmap(scaledFullBitmap, c * pieceW, r * pieceH, pieceW, pieceH)
                        val isEdge = (r == 0 || r == rows - 1 || c == 0 || c == cols - 1)
                        val initRotation = if (allowRotation) (Random.nextInt(4) * 90) else 0

                        // Calculate Tab and Blank for each of the 4 edges
                        val topEdge = if (r == 0) EdgeType.FLAT else if (hEdges[r - 1][c]) EdgeType.BLANK else EdgeType.TAB
                        val bottomEdge = if (r == rows - 1) EdgeType.FLAT else if (hEdges[r][c]) EdgeType.TAB else EdgeType.BLANK
                        val leftEdge = if (c == 0) EdgeType.FLAT else if (vEdges[r][c - 1]) EdgeType.BLANK else EdgeType.TAB
                        val rightEdge = if (c == cols - 1) EdgeType.FLAT else if (vEdges[r][c]) EdgeType.TAB else EdgeType.BLANK

                        pieceList.add(
                            JigsawPiece(
                                id = pieceId++,
                                row = r,
                                col = c,
                                bitmap = pieceBitmap,
                                currentRotation = initRotation,
                                isPlaced = false,
                                isEdge = isEdge,
                                topEdge = topEdge,
                                rightEdge = rightEdge,
                                bottomEdge = bottomEdge,
                                leftEdge = leftEdge
                            )
                        )
                    }
                }

                // Shuffle tray order initially
                val shuffledPieces = pieceList.shuffled()

                withContext(Dispatchers.Main) {
                    fullBitmap = scaledFullBitmap
                    pieces = shuffledPieces
                    selectedPieceId = null
                    draggedPieceId = null
                    filterMode = PieceFilterMode.ALL
                    elapsedSeconds = 0
                    movesCount = 0
                    showVictoryDialog = false
                    isLoadingPieces = false
                    isTimerActive = true
                    screenState = GameScreenState.PLAYING
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoadingPieces = false
                }
            }
        }
    }

    // Check completion condition
    fun verifyCompletion(currentPieces: List<JigsawPiece>) {
        val allPlaced = currentPieces.all { it.isPlaced }
        if (allPlaced) {
            isTimerActive = false
            JigsawSound.playVictory()
            showVictoryDialog = true

            // Save High Score (least seconds & least moves)
            val scoreKey = "jigsaw_best_${selectedDifficulty.id}"
            val prevBest = prefs.getInt(scoreKey, Int.MAX_VALUE)
            if (elapsedSeconds < prevBest) {
                prefs.edit().putInt(scoreKey, elapsedSeconds).apply()
            }
        }
    }

    // Try placing a piece on the board
    fun attemptPlacePiece(piece: JigsawPiece, targetRow: Int, targetCol: Int) {
        movesCount++
        if (piece.row == targetRow && piece.col == targetCol) {
            if (allowRotation && piece.currentRotation != 0) {
                // Must be rotated upright!
                JigsawSound.playError()
                return
            }
            // SUCCESSFUL SNAP!
            JigsawSound.playSnap()
            pieces = pieces.map {
                if (it.id == piece.id) it.copy(isPlaced = true) else it
            }
            if (selectedPieceId == piece.id) {
                selectedPieceId = null
            }
            verifyCompletion(pieces)
        } else {
            JigsawSound.playError()
        }
    }

    // Hint function: snaps one random unplaced piece into place
    fun useHint() {
        val unplaced = pieces.filter { !it.isPlaced }
        if (unplaced.isNotEmpty()) {
            val target = unplaced.random()
            JigsawSound.playSnap()
            pieces = pieces.map {
                if (it.id == target.id) it.copy(isPlaced = true, currentRotation = 0) else it
            }
            showHintFlashId = target.id
            coroutineScope.launch {
                delay(600)
                showHintFlashId = null
            }
            verifyCompletion(pieces)
        }
    }

    // Back button handling
    BackHandler {
        if (screenState == GameScreenState.PLAYING) {
            screenState = GameScreenState.THEME_SELECTION
            isTimerActive = false
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            JigsawTopBar(
                screenState = screenState,
                title = if (screenState == GameScreenState.PLAYING) selectedTheme.title else "Jigsaw Puzzle",
                isSoundOn = isSoundOn,
                onToggleSound = { isSoundOn = !isSoundOn },
                onOpenInterlockingInfo = { showInterlockingInfoDialog = true },
                onBack = {
                    if (screenState == GameScreenState.PLAYING) {
                        screenState = GameScreenState.THEME_SELECTION
                        isTimerActive = false
                    } else {
                        onBackClick()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (screenState == GameScreenState.THEME_SELECTION) {
                JigsawThemeSelectionView(
                    selectedTheme = selectedTheme,
                    onSelectTheme = { selectedTheme = it },
                    customPhotoUri = customPhotoUri,
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    selectedDifficulty = selectedDifficulty,
                    onSelectDifficulty = { selectedDifficulty = it },
                    allowRotation = allowRotation,
                    onToggleRotation = { allowRotation = it },
                    isLoading = isLoadingPieces,
                    onOpenInterlockingInfo = { showInterlockingInfoDialog = true },
                    onStartGame = { startPuzzleGame() }
                )
            } else {
                JigsawGameplayView(
                    fullBitmap = fullBitmap,
                    pieces = pieces,
                    selectedDifficulty = selectedDifficulty,
                    selectedPieceId = selectedPieceId,
                    onSelectPiece = { id ->
                        selectedPieceId = if (selectedPieceId == id) null else id
                        if (selectedPieceId != null) JigsawSound.playPickup()
                    },
                    onRotatePiece = { id ->
                        pieces = pieces.map {
                            if (it.id == id) {
                                val nextRot = (it.currentRotation + 90) % 360
                                it.copy(currentRotation = nextRot)
                            } else it
                        }
                        JigsawSound.playRotate()
                    },
                    filterMode = filterMode,
                    onChangeFilterMode = { filterMode = it },
                    ghostOpacity = ghostOpacity,
                    onChangeGhostOpacity = { ghostOpacity = it },
                    elapsedSeconds = elapsedSeconds,
                    movesCount = movesCount,
                    allowRotation = allowRotation,
                    onShuffleTray = {
                        val unplaced = pieces.filter { !it.isPlaced }.shuffled()
                        val placed = pieces.filter { it.isPlaced }
                        pieces = placed + unplaced
                        JigsawSound.playPickup()
                    },
                    onOpenPreview = { showPreviewModal = true },
                    onOpenInterlockingInfo = { showInterlockingInfoDialog = true },
                    onUseHint = { useHint() },
                    showHintFlashId = showHintFlashId,
                    onCellClicked = { r, c ->
                        if (selectedPieceId != null) {
                            val piece = pieces.firstOrNull { it.id == selectedPieceId }
                            if (piece != null && !piece.isPlaced) {
                                attemptPlacePiece(piece, r, c)
                            }
                        }
                    },
                    onBoardPositioned = { rootOffset, size ->
                        boardRootOffset = rootOffset
                        boardSizePx = size
                    },
                    draggedPieceId = draggedPieceId,
                    dragPosition = dragPosition,
                    onDragStart = { id, startPos ->
                        draggedPieceId = id
                        dragPosition = startPos
                        JigsawSound.playPickup()
                    },
                    onDragDelta = { delta ->
                        dragPosition += delta
                    },
                    onDragEnd = {
                        val draggedId = draggedPieceId
                        draggedPieceId = null
                        if (draggedId != null && boardSizePx.width > 0 && boardSizePx.height > 0) {
                            val piece = pieces.firstOrNull { it.id == draggedId }
                            if (piece != null && !piece.isPlaced) {
                                val relX = dragPosition.x - boardRootOffset.x
                                val relY = dragPosition.y - boardRootOffset.y

                                if (relX in 0f..boardSizePx.width.toFloat() && relY in 0f..boardSizePx.height.toFloat()) {
                                    val cellW = boardSizePx.width.toFloat() / selectedDifficulty.cols
                                    val cellH = boardSizePx.height.toFloat() / selectedDifficulty.rows
                                    val col = (relX / cellW).toInt().coerceIn(0, selectedDifficulty.cols - 1)
                                    val row = (relY / cellH).toInt().coerceIn(0, selectedDifficulty.rows - 1)
                                    attemptPlacePiece(piece, row, col)
                                }
                            }
                        }
                    }
                )
            }

            // Preview modal dialog
            if (showPreviewModal && fullBitmap != null) {
                Dialog(onDismissRequest = { showPreviewModal = false }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gambar Utuh Target",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { showPreviewModal = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                bitmap = fullBitmap!!.asImageBitmap(),
                                contentDescription = "Pratinjau Gambar",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showPreviewModal = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lanjutkan Menyusun")
                            }
                        }
                    }
                }
            }

            // Anatomi Kepingan Interlocking (Tab & Blank) Info Dialog
            if (showInterlockingInfoDialog) {
                JigsawInterlockingInfoDialog(
                    onDismiss = { showInterlockingInfoDialog = false }
                )
            }

            // Victory Dialog
            if (showVictoryDialog && fullBitmap != null) {
                JigsawVictoryDialog(
                    fullBitmap = fullBitmap!!,
                    elapsedSeconds = elapsedSeconds,
                    movesCount = movesCount,
                    difficulty = selectedDifficulty,
                    onPlayAgain = {
                        startPuzzleGame()
                    },
                    onChooseOther = {
                        screenState = GameScreenState.THEME_SELECTION
                    }
                )
            }
        }
    }
}

@Composable
fun JigsawTopBar(
    screenState: GameScreenState,
    title: String,
    isSoundOn: Boolean,
    onToggleSound: () -> Unit,
    onOpenInterlockingInfo: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("jigsaw_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (screenState == GameScreenState.PLAYING) "Susun kepingan dengan sistem interlocking" else "Pilih gambar & tingkat kesulitan",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Tombol Informasi Anatomi Interlocking (Tab & Blank)
                IconButton(
                    onClick = onOpenInterlockingInfo,
                    modifier = Modifier.testTag("jigsaw_interlocking_info_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Istilah Interlocking (Tab & Blank)",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier.testTag("jigsaw_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (isSoundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Suara",
                        tint = if (isSoundOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun JigsawThemeSelectionView(
    selectedTheme: PuzzleTheme,
    onSelectTheme: (PuzzleTheme) -> Unit,
    customPhotoUri: Uri?,
    onPickPhoto: () -> Unit,
    selectedDifficulty: PuzzleDifficulty,
    onSelectDifficulty: (PuzzleDifficulty) -> Unit,
    allowRotation: Boolean,
    onToggleRotation: (Boolean) -> Unit,
    isLoading: Boolean,
    onOpenInterlockingInfo: () -> Unit,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Header Banner with Interlocking Accents
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Teka-Teki Jigsaw Puzzle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Setiap keping memiliki sisi Tab (tonjolan) dan Blank (lubang) yang saling mengunci secara presisi.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Edukasi Interlocking Mini Card Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenInterlockingInfo() }
                .testTag("jigsaw_banner_interlocking_info")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Istilah Sisi Kepingan (Interlocking)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1. Tab (Tonjolan/Knob) • 2. Blank (Cekungan/Lubang)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "Pelajari",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // STEP 1: PILIH GAMBAR (THEME SELECTION)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("1", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Pilih Gambar & Tema",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Grid of 4 Themes: Alam, Hewan, Karya Seni, Foto Pribadi
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        theme = PuzzleTheme.ALAM,
                        isSelected = selectedTheme == PuzzleTheme.ALAM,
                        onClick = { onSelectTheme(PuzzleTheme.ALAM) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        theme = PuzzleTheme.HEWAN,
                        isSelected = selectedTheme == PuzzleTheme.HEWAN,
                        onClick = { onSelectTheme(PuzzleTheme.HEWAN) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        theme = PuzzleTheme.SENI,
                        isSelected = selectedTheme == PuzzleTheme.SENI,
                        onClick = { onSelectTheme(PuzzleTheme.SENI) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeOptionCard(
                        theme = PuzzleTheme.FOTO_PRIBADI,
                        isSelected = selectedTheme == PuzzleTheme.FOTO_PRIBADI,
                        onClick = {
                            onSelectTheme(PuzzleTheme.FOTO_PRIBADI)
                            if (customPhotoUri == null) {
                                onPickPhoto()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        badgeText = if (customPhotoUri != null) "Foto Dipilih ✓" else "Pilih Foto"
                    )
                }
            }

            if (selectedTheme == PuzzleTheme.FOTO_PRIBADI) {
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.fillMaxWidth().testTag("jigsaw_pick_photo_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (customPhotoUri == null) "Buka Galeri Foto" else "Ganti Foto dari Galeri")
                }
            }
        }

        // STEP 2: TENTUKAN TINGKAT KESULITAN
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("2", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Tentukan Tingkat Kesulitan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PUZZLE_DIFFICULTIES.chunked(2).forEach { rowDiffs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowDiffs.forEach { diff ->
                            val isSelected = selectedDifficulty.id == diff.id
                            Card(
                                onClick = { onSelectDifficulty(diff) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("jigsaw_diff_${diff.id}")
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = diff.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${diff.rows}x${diff.cols} • ${diff.subtitle}",
                                        fontSize = 11.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (rowDiffs.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // STEP 3: OPSI TAMBAHAN
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Opsi Permainan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode Putar Potongan (Rotasi)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Potongan teracak arahnya dan harus diputar ke orientasi tepat",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowRotation,
                        onCheckedChange = onToggleRotation,
                        modifier = Modifier.testTag("jigsaw_rotation_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // START BUTTON
        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("jigsaw_start_game_button"),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Memotong Kepingan Interlocking...")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mulai Susun Puzzle (${selectedDifficulty.totalPieces} Keping)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ThemeOptionCard(
    theme: PuzzleTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag("jigsaw_theme_${theme.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = theme.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Terpilih",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = theme.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = badgeText ?: theme.description,
                fontSize = 11.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun JigsawGameplayView(
    fullBitmap: Bitmap?,
    pieces: List<JigsawPiece>,
    selectedDifficulty: PuzzleDifficulty,
    selectedPieceId: Int?,
    onSelectPiece: (Int) -> Unit,
    onRotatePiece: (Int) -> Unit,
    filterMode: PieceFilterMode,
    onChangeFilterMode: (PieceFilterMode) -> Unit,
    ghostOpacity: Float,
    onChangeGhostOpacity: (Float) -> Unit,
    elapsedSeconds: Int,
    movesCount: Int,
    allowRotation: Boolean,
    onShuffleTray: () -> Unit,
    onOpenPreview: () -> Unit,
    onOpenInterlockingInfo: () -> Unit,
    onUseHint: () -> Unit,
    showHintFlashId: Int?,
    onCellClicked: (Int, Int) -> Unit,
    onBoardPositioned: (Offset, IntSize) -> Unit,
    draggedPieceId: Int?,
    dragPosition: Offset,
    onDragStart: (Int, Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val unplacedPieces = remember(pieces, filterMode) {
        val base = pieces.filter { !it.isPlaced }
        when (filterMode) {
            PieceFilterMode.ALL -> base
            PieceFilterMode.EDGE_ONLY -> base.filter { it.isEdge }
            PieceFilterMode.TABS_DOMINANT -> base.filter { it.tabCount >= it.blankCount }
            PieceFilterMode.BLANKS_DOMINANT -> base.filter { it.blankCount > it.tabCount }
        }
    }

    val placedCount = pieces.count { it.isPlaced }
    val totalCount = pieces.size
    val progressPercent = if (totalCount > 0) (placedCount * 100) / totalCount else 0

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerStr = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP STATS BAR: Timer, Progress %, Moves, Actions
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = timerStr,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Progress Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$placedCount / $totalCount ($progressPercent%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Action buttons: Interlocking Info, Preview, & Hint
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onOpenInterlockingInfo,
                        modifier = Modifier.size(34.dp).testTag("jigsaw_tray_info_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Info Tab & Blank",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenPreview,
                        modifier = Modifier.size(34.dp).testTag("jigsaw_peek_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Intip Gambar Asli",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onUseHint,
                        modifier = Modifier.size(34.dp).testTag("jigsaw_hint_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Bantuan / Hint",
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // PUZZLE BOARD CONTAINER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .onGloballyPositioned { layoutCoords ->
                    onBoardPositioned(layoutCoords.positionInRoot(), layoutCoords.size)
                }
                .testTag("jigsaw_board_container")
        ) {
            // Background Ghost Image (semi-transparent guide)
            if (fullBitmap != null && ghostOpacity > 0f) {
                Image(
                    bitmap = fullBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    alpha = ghostOpacity,
                    contentScale = ContentScale.Crop
                )
            }

            // Grid Slots on the board
            Column(modifier = Modifier.fillMaxSize()) {
                val rows = selectedDifficulty.rows
                val cols = selectedDifficulty.cols

                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        for (c in 0 until cols) {
                            val placedPiece = pieces.firstOrNull { it.row == r && it.col == c && it.isPlaced }
                            val isHintFlashing = (placedPiece?.id == showHintFlashId)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    .clickable { onCellClicked(r, c) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (placedPiece != null) {
                                    Image(
                                        bitmap = placedPiece.bitmap.asImageBitmap(),
                                        contentDescription = "Kepingan r$r c$c",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(placedPiece.currentRotation.toFloat()),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isHintFlashing) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x66FFD54F))
                                        )
                                    }
                                } else {
                                    // Subtle target dot
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // BOARD INTERLOCKING CUTLINES OVERLAY (Renders Tabs and Blanks between cells)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rows = selectedDifficulty.rows
                val cols = selectedDifficulty.cols
                val cellW = size.width / cols
                val cellH = size.height / rows

                // Draw interlocking horizontal internal lines
                for (r in 0 until rows - 1) {
                    val y = (r + 1) * cellH
                    for (c in 0 until cols) {
                        val piece = pieces.firstOrNull { it.row == r && it.col == c }
                        if (piece != null) {
                            val x0 = c * cellW
                            val x1 = (c + 1) * cellW
                            val path = Path()
                            path.moveTo(x0, y)
                            // Draw horizontal boundary with the piece's bottom edge type
                            drawInterlockingEdge(path, x0, y, x1, y, piece.bottomEdge)

                            val isBothPlaced = piece.isPlaced && (pieces.firstOrNull { it.row == r + 1 && it.col == c }?.isPlaced == true)
                            drawPath(
                                path = path,
                                color = if (isBothPlaced) Color(0x33000000) else Color(0x667C4DFF),
                                style = Stroke(
                                    width = if (isBothPlaced) 1f else 1.5f,
                                    pathEffect = if (isBothPlaced) null else PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                            )
                        }
                    }
                }

                // Draw interlocking vertical internal lines
                for (r in 0 until rows) {
                    val y0 = r * cellH
                    val y1 = (r + 1) * cellH
                    for (c in 0 until cols - 1) {
                        val x = (c + 1) * cellW
                        val piece = pieces.firstOrNull { it.row == r && it.col == c }
                        if (piece != null) {
                            val path = Path()
                            path.moveTo(x, y0)
                            // Draw vertical boundary with the piece's right edge type
                            drawInterlockingEdge(path, x, y0, x, y1, piece.rightEdge)

                            val isBothPlaced = piece.isPlaced && (pieces.firstOrNull { it.row == r && it.col == c + 1 }?.isPlaced == true)
                            drawPath(
                                path = path,
                                color = if (isBothPlaced) Color(0x33000000) else Color(0x667C4DFF),
                                style = Stroke(
                                    width = if (isBothPlaced) 1f else 1.5f,
                                    pathEffect = if (isBothPlaced) null else PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // TOOLBAR & TRAY CONTROLS: Filter Tabs & Blanks Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = filterMode == PieceFilterMode.ALL,
                        onClick = { onChangeFilterMode(PieceFilterMode.ALL) },
                        label = { Text("Semua (${pieces.count { !it.isPlaced }})", fontSize = 11.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PieceFilterMode.EDGE_ONLY,
                        onClick = { onChangeFilterMode(PieceFilterMode.EDGE_ONLY) },
                        label = { Text("Tepi (${pieces.count { !it.isPlaced && it.isEdge }})", fontSize = 11.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PieceFilterMode.TABS_DOMINANT,
                        onClick = { onChangeFilterMode(PieceFilterMode.TABS_DOMINANT) },
                        label = { Text("Tab 凸", fontSize = 11.sp, color = Color(0xFF2E7D32)) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PieceFilterMode.BLANKS_DOMINANT,
                        onClick = { onChangeFilterMode(PieceFilterMode.BLANKS_DOMINANT) },
                        label = { Text("Blank 凹", fontSize = 11.sp, color = Color(0xFF8E24AA)) }
                    )
                }
            }

            // Ghost Opacity & Shuffle buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val next = when {
                            ghostOpacity < 0.1f -> 0.20f
                            ghostOpacity < 0.3f -> 0.45f
                            else -> 0f
                        }
                        onChangeGhostOpacity(next)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Opasitas Bayangan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onShuffleTray,
                    modifier = Modifier.size(32.dp).testTag("jigsaw_shuffle_tray")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Acak Kepingan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // FLOATING ACTION BAR FOR SELECTED PIECE WITH INTERLOCKING ANATOMY BREAKDOWN
        if (selectedPieceId != null) {
            val selPiece = pieces.firstOrNull { it.id == selectedPieceId }
            if (selPiece != null && !selPiece.isPlaced) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Keping Terpilih • Ketuk petak di papan untuk meletakkan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )

                            if (allowRotation) {
                                Button(
                                    onClick = { onRotatePiece(selPiece.id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("jigsaw_rotate_piece_btn")
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Putar (${selPiece.currentRotation}°)", fontSize = 11.sp)
                                }
                            }
                        }

                        // Sisi-sisi Interlocking Profil: Atas, Kanan, Bawah, Kiri
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Sisi Atas: ${selPiece.topEdge.title}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selPiece.topEdge.accentColor
                            )
                            Text(
                                text = "Kanan: ${selPiece.rightEdge.title}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selPiece.rightEdge.accentColor
                            )
                            Text(
                                text = "Bawah: ${selPiece.bottomEdge.title}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selPiece.bottomEdge.accentColor
                            )
                            Text(
                                text = "Kiri: ${selPiece.leftEdge.title}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = selPiece.leftEdge.accentColor
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM TRAY / CAROUSEL OF UNPLACED PIECES
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .testTag("jigsaw_pieces_tray")
        ) {
            if (unplacedPieces.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Semua keping telah tersusun rapi!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(unplacedPieces, key = { it.id }) { piece ->
                        val isSelected = (piece.id == selectedPieceId)
                        val isDragged = (piece.id == draggedPieceId)

                        JigsawPieceTrayItem(
                            piece = piece,
                            isSelected = isSelected,
                            isDragged = isDragged,
                            allowRotation = allowRotation,
                            onClick = { onSelectPiece(piece.id) },
                            onRotate = { onRotatePiece(piece.id) },
                            onDragStart = { offset -> onDragStart(piece.id, offset) },
                            onDragDelta = onDragDelta,
                            onDragEnd = onDragEnd
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JigsawPieceTrayItem(
    piece: JigsawPiece,
    isSelected: Boolean,
    isDragged: Boolean,
    allowRotation: Boolean,
    onClick: () -> Unit,
    onRotate: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var itemRootOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .size(78.dp)
            .onGloballyPositioned { itemRootOffset = it.positionInRoot() }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                if (isSelected) 2.5.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(piece.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { if (allowRotation) onRotate() }
                )
            }
            .pointerInput(piece.id) {
                detectDragGestures(
                    onDragStart = { localOffset ->
                        onDragStart(itemRootOffset + localOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .testTag("jigsaw_piece_${piece.id}"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = piece.bitmap.asImageBitmap(),
            contentDescription = "Kepingan ${piece.id}",
            modifier = Modifier
                .fillMaxSize(0.82f)
                .rotate(piece.currentRotation.toFloat())
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        // Custom canvas drawing interlocking Tab and Blank profile indicator around the piece
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = 4f
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            // Top edge accent
            if (piece.topEdge == EdgeType.TAB) {
                drawCircle(color = Color(0xFF2E7D32), radius = 3.5f, center = Offset(size.width / 2, pad + 2f))
            } else if (piece.topEdge == EdgeType.BLANK) {
                drawCircle(color = Color(0xFF8E24AA), radius = 3f, center = Offset(size.width / 2, pad + 4f), style = Stroke(1.5f))
            }

            // Right edge accent
            if (piece.rightEdge == EdgeType.TAB) {
                drawCircle(color = Color(0xFF2E7D32), radius = 3.5f, center = Offset(size.width - pad - 2f, size.height / 2))
            } else if (piece.rightEdge == EdgeType.BLANK) {
                drawCircle(color = Color(0xFF8E24AA), radius = 3f, center = Offset(size.width - pad - 4f, size.height / 2), style = Stroke(1.5f))
            }

            // Bottom edge accent
            if (piece.bottomEdge == EdgeType.TAB) {
                drawCircle(color = Color(0xFF2E7D32), radius = 3.5f, center = Offset(size.width / 2, size.height - pad - 2f))
            } else if (piece.bottomEdge == EdgeType.BLANK) {
                drawCircle(color = Color(0xFF8E24AA), radius = 3f, center = Offset(size.width / 2, size.height - pad - 4f), style = Stroke(1.5f))
            }

            // Left edge accent
            if (piece.leftEdge == EdgeType.TAB) {
                drawCircle(color = Color(0xFF2E7D32), radius = 3.5f, center = Offset(pad + 2f, size.height / 2))
            } else if (piece.leftEdge == EdgeType.BLANK) {
                drawCircle(color = Color(0xFF8E24AA), radius = 3f, center = Offset(pad + 4f, size.height / 2), style = Stroke(1.5f))
            }
        }

        // Interlocking Badges: Tab count (Knob) & Blank count (Socket)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (piece.tabCount > 0) {
                Text(
                    text = "T:${piece.tabCount}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF81C784) // Light green for Tab
                )
            }
            if (piece.blankCount > 0) {
                Text(
                    text = "B:${piece.blankCount}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCE93D8) // Light purple for Blank
                )
            }
        }

        // Rotation Indicator Badge if rotated
        if (allowRotation && piece.currentRotation != 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${piece.currentRotation}°",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Dialog Edukasi & Panduan Istilah Anatomi Kepingan Interlocking (Tab & Blank)
 */
@Composable
fun JigsawInterlockingInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight()
                .padding(8.dp)
                .testTag("jigsaw_interlocking_info_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Anatomi Kepingan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sistem Saling Mengunci (Interlocking)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Bagian 1: TAB
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    border = BorderStroke(1.5.dp, Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "凸",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "1. Tab",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Surface(
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Tonjolan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Istilah lain: knob, outie, key, atau tonjolan.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Bagian sisi yang menganjur keluar atau menonjol. Berfungsi mengunci kepingan ke dalam lubang kepingan di sebelahnya.",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                // Bagian 2: BLANK
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                    border = BorderStroke(1.5.dp, Color(0xFF8E24AA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8E24AA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "凹",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "2. Blank",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF4A148C)
                                )
                                Surface(
                                    color = Color(0xFF8E24AA),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Lubang / Cekungan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Istilah lain: socket, hole, innie, slot, atau lubang.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8E24AA)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Bagian sisi yang berupa cekungan atau lubang tempat masuknya tab dari kepingan lain.",
                                fontSize = 11.sp,
                                color = Color(0xFF4A148C)
                            )
                        }
                    }
                }

                // Bagian 3: CARA KERJA SALING MENGUNCI (INTERLOCKING)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cara Kerja Saling Mengunci (Interlocking):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Kepingan dengan sisi Tab (凸) hanya bisa dipasangkan dengan sisi Blank (凹) dari kepingan pasangannya.\n• Sisi luar tepi gambar bertipe Flat (rata) tanpa tonjolan.\n• Indikator pada setiap keping di rak menunjukkan jumlah Tab (T) dan Blank (B) untuk mempermudah Anda mencari pasangan kepingan yang tepat!",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Saya Mengerti, Ayo Bermain!")
                }
            }
        }
    }
}

@Composable
fun JigsawVictoryDialog(
    fullBitmap: Bitmap,
    elapsedSeconds: Int,
    movesCount: Int,
    difficulty: PuzzleDifficulty,
    onPlayAgain: () -> Unit,
    onChooseOther: () -> Unit
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Dialog(onDismissRequest = { /* Require deliberate button click */ }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
                .testTag("jigsaw_victory_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Celebration Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD54F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Piala Kemenangan",
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "Selamat! Teka-Teki Selesai!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Seluruh ${difficulty.totalPieces} kepingan dengan sisi Tab & Blank berhasil saling mengunci menjadi gambar utuh!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Completed Full Picture Display
                Image(
                    bitmap = fullBitmap.asImageBitmap(),
                    contentDescription = "Hasil Gambar Utuh",
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )

                // Stats Cards (Waktu & Langkah)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Waktu Selesai", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(timeFormatted, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Jumlah Percobaan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("$movesCount Langkah", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Star Rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Bintang",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action Buttons
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().testTag("jigsaw_play_again_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mainkan Lagi Level Ini", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onChooseOther,
                    modifier = Modifier.fillMaxWidth().testTag("jigsaw_choose_other_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Gambar / Kesulitan Lain")
                }
            }
        }
    }
}
