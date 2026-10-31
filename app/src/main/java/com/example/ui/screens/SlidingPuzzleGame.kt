package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pilihan Layar dalam Permainan Puzzle Geser
 */
enum class SlidingPuzzleScreen {
    LOBBY,      // Lobi Utama (Main Lobby)
    GAMEPLAY    // Papan Permainan Aktif
}

/**
 * Pilihan Tema Puzzle Gambar & Angka
 */
enum class SlidingPuzzleTheme(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @DrawableRes val drawableRes: Int? = null
) {
    ALAM(
        title = "Alam",
        subtitle = "Pegunungan & Air Terjun Tropis",
        icon = Icons.Default.Landscape,
        drawableRes = R.drawable.img_puzzle_nature
    ),
    HEWAN(
        title = "Hewan",
        subtitle = "Harimau Rimba Tropis",
        icon = Icons.Default.Pets,
        drawableRes = R.drawable.img_puzzle_tiger
    ),
    KARYA_SENI(
        title = "Karya Seni",
        subtitle = "Stupa Borobudur Senja Keemasan",
        icon = Icons.Default.AccountBalance,
        drawableRes = R.drawable.img_puzzle_borobudur
    ),
    FOTO_PRIBADI(
        title = "Foto Galeri",
        subtitle = "Pilih Foto Pribadi dari Galeri HP",
        icon = Icons.Default.PhotoLibrary,
        drawableRes = null
    ),
    ANGKA_KLASIK(
        title = "Angka Klasik",
        subtitle = "15-Puzzle Ubin Kayu Tradisional",
        icon = Icons.Default.Grid4x4,
        drawableRes = null
    )
}

/**
 * Ukuran Papan / Tingkat Kesulitan
 */
enum class SlidingPuzzleDifficulty(val size: Int, val label: String, val badge: String, val desc: String) {
    SIZE_3X3(3, "3×3", "Santai (8 Ubin)", "Ideal untuk pemula & permainan santai."),
    SIZE_4X4(4, "4×4", "Klasik (15 Ubin)", "Standar 15-Puzzle legendaris internasional."),
    SIZE_5X5(5, "5×5", "Tantangan (24 Ubin)", "Tingkat lanjutan untuk menguji strategi."),
    SIZE_6X6(6, "6×6", "Master (35 Ubin)", "Tingkat tersulit bagi master logika.")
}

/**
 * Komponen Utama Permainan Puzzle Geser (Sliding Puzzle)
 * Tanpa icon <- (kembali menggunakan tombol back OS Android)
 * Tanpa icon info, icon piala, dan icon speaker pada Lobi Utama maupun Area Permainan
 */
@Composable
fun SlidingPuzzleGame(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("sliding_puzzle_records", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        SlidingPuzzleAudio.init(context)
    }

    // State Navigasi Antar-Layar Internal Game
    var currentScreen by remember { mutableStateOf(SlidingPuzzleScreen.LOBBY) }

    // State Konfigurasi Game (Dipilih di Lobi)
    var difficulty by remember { mutableStateOf(SlidingPuzzleDifficulty.SIZE_4X4) }
    var currentTheme by remember { mutableStateOf(SlidingPuzzleTheme.ALAM) }
    var customPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var customPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // State Pop-up Menu List
    var showDifficultyPopup by remember { mutableStateOf(false) }
    var showThemePopup by remember { mutableStateOf(false) }

    // State Bantuan & Dialog
    var showTileNumbers by remember { mutableStateOf(false) }
    var showPreviewModal by remember { mutableStateOf(false) }
    var showVictoryDialog by remember { mutableStateOf(false) }

    // Papan Permainan
    val gridSize = difficulty.size
    val totalSlots = gridSize * gridSize
    var boardState by remember(gridSize) {
        mutableStateOf(generateSolvableBoard(gridSize))
    }

    // Statistik Permainan
    var moveCount by remember(gridSize, currentTheme) { mutableStateOf(0) }
    var elapsedSeconds by remember(gridSize, currentTheme) { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isGameSolved by remember { mutableStateOf(false) }
    var isNewBestRecord by remember { mutableStateOf(false) }

    // Rekor Tersimpan
    val recordKeyPrefix = "${difficulty.size}_${currentTheme.name}"
    var bestTime by remember(recordKeyPrefix) {
        mutableStateOf(prefs.getInt("best_time_$recordKeyPrefix", 0))
    }
    var bestMoves by remember(recordKeyPrefix) {
        mutableStateOf(prefs.getInt("best_moves_$recordKeyPrefix", 0))
    }

    // Android Photo Picker zero-permission launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            customPhotoUri = uri
            currentTheme = SlidingPuzzleTheme.FOTO_PRIBADI
            coroutineScope.launch {
                val bmp = loadAndCropSquareBitmap(context, uri)
                customPhotoBitmap = bmp
                boardState = generateSolvableBoard(gridSize)
                moveCount = 0
                elapsedSeconds = 0
                isTimerRunning = false
                isGameSolved = false
                SlidingPuzzleAudio.playShuffleSound()
            }
        }
    }

    // ImageBitmap untuk tema gambar
    val presetImageBitmap: ImageBitmap? = remember(currentTheme, customPhotoBitmap) {
        when (currentTheme) {
            SlidingPuzzleTheme.ALAM, SlidingPuzzleTheme.HEWAN, SlidingPuzzleTheme.KARYA_SENI -> {
                currentTheme.drawableRes?.let { resId ->
                    loadResourceSquareBitmap(context, resId)
                }
            }
            SlidingPuzzleTheme.FOTO_PRIBADI -> customPhotoBitmap
            SlidingPuzzleTheme.ANGKA_KLASIK -> null
        }
    }

    // Helper Acak Ulang Papan
    fun restartAndShuffle(newGridSize: Int = difficulty.size) {
        boardState = generateSolvableBoard(newGridSize)
        moveCount = 0
        elapsedSeconds = 0
        isTimerRunning = false
        isGameSolved = false
        showVictoryDialog = false
        isNewBestRecord = false
        SlidingPuzzleAudio.playShuffleSound()
    }

    // Memulai Game dari Lobi
    fun startGame() {
        restartAndShuffle(difficulty.size)
        currentScreen = SlidingPuzzleScreen.GAMEPLAY
    }

    // Timer Loop
    LaunchedEffect(isTimerRunning, isGameSolved) {
        if (isTimerRunning && !isGameSolved) {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    // Logika Pergeseran Multi-tile Slide
    fun handleTileClick(clickedPosition: Int) {
        if (isGameSolved) return
        val currentBoard = boardState.toMutableList()
        val blankIndex = currentBoard.indexOf(0)
        if (blankIndex == -1 || clickedPosition == blankIndex) return

        val blankRow = blankIndex / gridSize
        val blankCol = blankIndex % gridSize
        val clickedRow = clickedPosition / gridSize
        val clickedCol = clickedPosition % gridSize

        var moved = false

        if (clickedRow == blankRow) {
            if (clickedCol < blankCol) {
                for (c in blankCol downTo clickedCol + 1) {
                    val targetIdx = blankRow * gridSize + c
                    val sourceIdx = blankRow * gridSize + (c - 1)
                    currentBoard[targetIdx] = currentBoard[sourceIdx]
                }
                currentBoard[clickedPosition] = 0
                moved = true
            } else if (clickedCol > blankCol) {
                for (c in blankCol until clickedCol) {
                    val targetIdx = blankRow * gridSize + c
                    val sourceIdx = blankRow * gridSize + (c + 1)
                    currentBoard[targetIdx] = currentBoard[sourceIdx]
                }
                currentBoard[clickedPosition] = 0
                moved = true
            }
        } else if (clickedCol == blankCol) {
            if (clickedRow < blankRow) {
                for (r in blankRow downTo clickedRow + 1) {
                    val targetIdx = r * gridSize + blankCol
                    val sourceIdx = (r - 1) * gridSize + blankCol
                    currentBoard[targetIdx] = currentBoard[sourceIdx]
                }
                currentBoard[clickedPosition] = 0
                moved = true
            } else if (clickedRow > blankRow) {
                for (r in blankRow until clickedRow) {
                    val targetIdx = r * gridSize + blankCol
                    val sourceIdx = (r + 1) * gridSize + blankCol
                    currentBoard[targetIdx] = currentBoard[sourceIdx]
                }
                currentBoard[clickedPosition] = 0
                moved = true
            }
        }

        if (moved) {
            boardState = currentBoard
            moveCount++
            if (!isTimerRunning) {
                isTimerRunning = true
            }
            SlidingPuzzleAudio.playWoodClick()

            if (checkIfSolved(currentBoard)) {
                isGameSolved = true
                isTimerRunning = false
                SlidingPuzzleAudio.playVictoryFanfare()

                val isNewBest = (bestTime == 0 || elapsedSeconds < bestTime) ||
                        (bestMoves == 0 || moveCount < bestMoves)
                if (isNewBest) {
                    isNewBestRecord = true
                    val updatedBestTime = if (bestTime == 0 || elapsedSeconds < bestTime) elapsedSeconds else bestTime
                    val updatedBestMoves = if (bestMoves == 0 || moveCount < bestMoves) moveCount else bestMoves
                    prefs.edit()
                        .putInt("best_time_$recordKeyPrefix", updatedBestTime)
                        .putInt("best_moves_$recordKeyPrefix", updatedBestMoves)
                        .apply()
                    bestTime = updatedBestTime
                    bestMoves = updatedBestMoves
                } else {
                    isNewBestRecord = false
                }

                coroutineScope.launch {
                    delay(500L)
                    showVictoryDialog = true
                }
            }
        }
    }

    // Tombol Back Hardware / Gesture OS Android
    BackHandler {
        if (currentScreen == SlidingPuzzleScreen.GAMEPLAY) {
            currentScreen = SlidingPuzzleScreen.LOBBY
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (currentScreen == SlidingPuzzleScreen.LOBBY) {
            // ==================== LOBI UTAMA (MAIN LOBBY) ====================
            SlidingPuzzleLobbyView(
                selectedDifficulty = difficulty,
                onOpenDifficultyPopup = { showDifficultyPopup = true },
                selectedTheme = currentTheme,
                onOpenThemePopup = { showThemePopup = true },
                customPhotoBitmap = customPhotoBitmap,
                bestMoves = bestMoves,
                bestTime = bestTime,
                onStartGame = { startGame() }
            )
        } else {
            // ==================== AREA PERMAINAN BERSIH (GAMEPLAY) ====================
            SlidingPuzzleGameplayCleanView(
                difficulty = difficulty,
                currentTheme = currentTheme,
                presetImageBitmap = presetImageBitmap,
                boardState = boardState,
                moveCount = moveCount,
                elapsedSeconds = elapsedSeconds,
                bestMoves = bestMoves,
                bestTime = bestTime,
                isGameSolved = isGameSolved,
                showTileNumbers = showTileNumbers,
                onToggleTileNumbers = { showTileNumbers = !showTileNumbers },
                onTileClick = { handleTileClick(it) },
                onRestartAndShuffle = { restartAndShuffle() },
                onOpenPreview = { showPreviewModal = true }
            )
        }

        // ==================== POP-UP MENU LIST: PILIH KESULITAN ====================
        if (showDifficultyPopup) {
            DifficultySelectionDialog(
                currentDifficulty = difficulty,
                onSelectDifficulty = { selected ->
                    difficulty = selected
                    showDifficultyPopup = false
                },
                onDismiss = { showDifficultyPopup = false }
            )
        }

        // ==================== POP-UP MENU LIST: PILIH TEMA ====================
        if (showThemePopup) {
            ThemeSelectionDialog(
                currentTheme = currentTheme,
                customPhotoBitmap = customPhotoBitmap,
                onSelectTheme = { selected ->
                    if (selected == SlidingPuzzleTheme.FOTO_PRIBADI) {
                        showThemePopup = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } else {
                        currentTheme = selected
                        showThemePopup = false
                    }
                },
                onDismiss = { showThemePopup = false }
            )
        }

        // ==================== DIALOG PRATINJAU GAMBAR ASLI ====================
        if (showPreviewModal) {
            Dialog(
                onDismissRequest = { showPreviewModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(22.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Gambar Asli",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { showPreviewModal = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (presetImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            ) {
                                Image(
                                    bitmap = presetImageBitmap,
                                    contentDescription = "Target Gambar Utuh",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Mode Angka Tradisional\n(Susun angka berurutan 1 sampai ${totalSlots - 1})",
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showPreviewModal = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Kembali Bermain")
                        }
                    }
                }
            }
        }

        // ==================== DIALOG KEMENANGAN ====================
        if (showVictoryDialog) {
            Dialog(
                onDismissRequest = { showVictoryDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(26.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(22.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Luar Biasa! Puzzle Selesai!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        if (isNewBestRecord) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFE082),
                                border = BorderStroke(1.dp, Color(0xFFFFB300))
                            ) {
                                Text(
                                    text = "★ REKOR BARU TERCIPTA! ★",
                                    color = Color(0xFF5D4037),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (presetImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    bitmap = presetImageBitmap,
                                    contentDescription = "Gambar Utuh Sempurna",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Total Waktu", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = formatTime(elapsedSeconds),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "Jumlah Langkah", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "$moveCount",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showVictoryDialog = false
                                    currentScreen = SlidingPuzzleScreen.LOBBY
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ke Lobi")
                            }

                            Button(
                                onClick = {
                                    showVictoryDialog = false
                                    restartAndShuffle()
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Main Lagi")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TAMPILAN LOBI UTAMA (MAIN LOBBY)
 * Rapi, sistematis, kompak dengan tombol pemicu Pop-up Menu List
 * Tanpa icon <- (kembali menggunakan tombol back OS Android)
 * Tanpa icon info, icon piala, dan icon speaker
 */
@Composable
private fun SlidingPuzzleLobbyView(
    selectedDifficulty: SlidingPuzzleDifficulty,
    onOpenDifficultyPopup: () -> Unit,
    selectedTheme: SlidingPuzzleTheme,
    onOpenThemePopup: () -> Unit,
    customPhotoBitmap: ImageBitmap?,
    bestMoves: Int,
    bestTime: Int,
    onStartGame: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sliding_puzzle_lobby"),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = onStartGame,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("lobby_start_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Mulai Main (${selectedDifficulty.label} • ${selectedTheme.title})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. HERO SHOWCASE CARD (BANNER)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFE65100),
                                    Color(0xFFF57C00),
                                    Color(0xFFFFB300)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0x33000000)
                            ) {
                                Text(
                                    text = "SLIDING PUZZLE NUSANTARA",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Permainan Puzzle Geser",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tantang logika dengan geseran beruntun multi-tile dan susun puzzle hingga sempurna!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. KARTU PENGATURAN SISTEMATIS DENGAN POP-UP MENU LIST
            Text(
                text = "Pengaturan Permainan",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Card 1: Pilihan Tingkat Kesulitan / Ukuran Papan
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedDifficulty.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Tingkat Kesulitan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${selectedDifficulty.label} (${selectedDifficulty.badge})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedDifficulty.desc,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenDifficultyPopup,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("lobby_change_difficulty_button")
                    ) {
                        Text("Ganti", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card 2: Pilihan Tema Gambar & Mode Angka
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedTheme == SlidingPuzzleTheme.FOTO_PRIBADI && customPhotoBitmap != null) {
                                Image(
                                    bitmap = customPhotoBitmap,
                                    contentDescription = selectedTheme.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = selectedTheme.icon,
                                    contentDescription = selectedTheme.title,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Tema Puzzle",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedTheme.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = selectedTheme.subtitle,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = onOpenThemePopup,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("lobby_change_theme_button")
                    ) {
                        Text("Pilih", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Card 3: Status Rekor Terbaik Saat Ini (Rapi & Tanpa Icon Piala)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Rekor Terbaik (${selectedDifficulty.label})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tema: ${selectedTheme.title}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (bestMoves > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$bestMoves Langkah",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatTime(bestTime),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Text(
                            text = "Belum Ada Rekor",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * POP-UP MENU LIST: DIALOG PEMILIHAN TINGKAT KESULITAN
 */
@Composable
private fun DifficultySelectionDialog(
    currentDifficulty: SlidingPuzzleDifficulty,
    onSelectDifficulty: (SlidingPuzzleDifficulty) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Tingkat Kesulitan",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tentukan ukuran papan permainan",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SlidingPuzzleDifficulty.entries.forEach { diff ->
                    val isSelected = currentDifficulty == diff
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectDifficulty(diff) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = diff.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = diff.badge,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = diff.desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectDifficulty(diff) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

/**
 * POP-UP MENU LIST: DIALOG PEMILIHAN TEMA GAMBAR & MODE ANGKA
 */
@Composable
private fun ThemeSelectionDialog(
    currentTheme: SlidingPuzzleTheme,
    customPhotoBitmap: ImageBitmap?,
    onSelectTheme: (SlidingPuzzleTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pilih Tema Gambar",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tentukan visual kepingan puzzle",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                SlidingPuzzleTheme.entries.forEach { theme ->
                    val isSelected = currentTheme == theme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectTheme(theme) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (theme == SlidingPuzzleTheme.FOTO_PRIBADI && customPhotoBitmap != null) {
                                    Image(
                                        bitmap = customPhotoBitmap,
                                        contentDescription = theme.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = theme.icon,
                                        contentDescription = theme.title,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = theme.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectTheme(theme) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}

/**
 * TAMPILAN AREA PERMAINAN BERSIH (CLEAN GAMEPLAY VIEW)
 * Pilihan tingkat kesulitan dan tema gambar DIHILANGKAN agar fokus dan rapi
 * Tanpa icon <- (kembali menggunakan tombol back OS Android)
 * Tanpa icon info, icon piala, dan icon speaker
 */
@Composable
private fun SlidingPuzzleGameplayCleanView(
    difficulty: SlidingPuzzleDifficulty,
    currentTheme: SlidingPuzzleTheme,
    presetImageBitmap: ImageBitmap?,
    boardState: List<Int>,
    moveCount: Int,
    elapsedSeconds: Int,
    bestMoves: Int,
    bestTime: Int,
    isGameSolved: Boolean,
    showTileNumbers: Boolean,
    onToggleTileNumbers: () -> Unit,
    onTileClick: (Int) -> Unit,
    onRestartAndShuffle: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val gridSize = difficulty.size
    val totalSlots = gridSize * gridSize

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sliding_puzzle_clean_gameplay"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Bilah Kontrol Terintegrasi (Tanpa icon <-, info, piala, speaker)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge Ukuran & Tema Aktif
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${difficulty.label} • ${currentTheme.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Aksi Cepat In-Game: Intip Asli & Acak Ulang
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (currentTheme != SlidingPuzzleTheme.ANGKA_KLASIK) {
                        IconButton(
                            onClick = onOpenPreview,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Intip Asli",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onRestartAndShuffle,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Acak Ulang",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 1. HUD METRIK TERTATA (Langkah, Waktu, Rekor & Saklar Nomor)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "Langkah", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "$moveCount",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "Waktu", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatTime(elapsedSeconds),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(text = "Rekor", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (bestMoves > 0) "${bestMoves}L • ${formatTime(bestTime)}" else "--:--",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onToggleTileNumbers),
                            shape = RoundedCornerShape(8.dp),
                            color = if (showTileNumbers) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (showTileNumbers) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = null,
                                    tint = if (showTileNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (showTileNumbers) "No. ON" else "No. OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (showTileNumbers) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. PAPAN PERMAINAN PUZZLE GESER (PUSAT FOKUS PERMAINAN)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF231810),
                                    Color(0xFF160E08)
                                )
                            )
                        )
                        .border(3.dp, Color(0xFF65442A), RoundedCornerShape(20.dp))
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .padding(6.dp)
                ) {
                    val boardWidth = maxWidth
                    val tileSize = boardWidth / gridSize

                    for (index in 0 until totalSlots) {
                        val tileId = boardState[index]
                        val row = index / gridSize
                        val col = index % gridSize

                        val offsetX = tileSize * col
                        val offsetY = tileSize * row

                        val animatedOffsetX by animateDpAsState(
                            targetValue = offsetX,
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                            label = "tileX_$index"
                        )
                        val animatedOffsetY by animateDpAsState(
                            targetValue = offsetY,
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                            label = "tileY_$index"
                        )

                        if (tileId != 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = animatedOffsetX, y = animatedOffsetY)
                                    .size(tileSize)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onTileClick(index)
                                    }
                                    .testTag("tile_${tileId}")
                            ) {
                                if (currentTheme == SlidingPuzzleTheme.ANGKA_KLASIK || presetImageBitmap == null) {
                                    ClassicNumberTileView(
                                        number = tileId,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ImageTileView(
                                        tileId = tileId,
                                        gridSize = gridSize,
                                        imageBitmap = presetImageBitmap,
                                        showNumberBadge = showTileNumbers,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else if (isGameSolved) {
                            Box(
                                modifier = Modifier
                                    .offset(x = animatedOffsetX, y = animatedOffsetY)
                                    .size(tileSize)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (currentTheme == SlidingPuzzleTheme.ANGKA_KLASIK || presetImageBitmap == null) {
                                    ClassicNumberTileView(
                                        number = totalSlots,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ImageTileView(
                                        tileId = totalSlots,
                                        gridSize = gridSize,
                                        imageBitmap = presetImageBitmap,
                                        showNumberBadge = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .offset(x = animatedOffsetX, y = animatedOffsetY)
                                    .size(tileSize)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF100905))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. BILAH KONTROL CEPAT DI BAWAH PAPAN (Untuk kembali gunakan tombol back OS Android)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRestartAndShuffle,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Acak Ulang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (currentTheme != SlidingPuzzleTheme.ANGKA_KLASIK) {
                    FilledTonalButton(
                        onClick = onOpenPreview,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Intip Asli", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Tampilan Ubin Angka Klasik Kayu Elegan (15-Puzzle)
 */
@Composable
fun ClassicNumberTileView(
    number: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE5A75B),
                        Color(0xFFC78540),
                        Color(0xFF985A20)
                    )
                )
            )
            .border(1.5.dp, Color(0xFFF9D6A0), RoundedCornerShape(8.dp))
            .shadow(2.dp, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF2C1608),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Tampilan Ubin Potongan Gambar Presisi Tinggi
 */
@Composable
fun ImageTileView(
    tileId: Int,
    gridSize: Int,
    imageBitmap: ImageBitmap,
    showNumberBadge: Boolean,
    modifier: Modifier = Modifier
) {
    val targetIndex = tileId - 1
    val origRow = targetIndex / gridSize
    val origCol = targetIndex % gridSize

    Box(
        modifier = modifier
            .background(Color.Black)
            .border(0.75.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bmpW = imageBitmap.width
            val bmpH = imageBitmap.height

            val srcX = (origCol * bmpW) / gridSize
            val srcY = (origRow * bmpH) / gridSize
            val srcW = ((origCol + 1) * bmpW / gridSize) - srcX
            val srcH = ((origRow + 1) * bmpH / gridSize) - srcY

            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }

        if (showNumberBadge) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(1.dp, Color(0xFFFFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$tileId",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Format detik menjadi tampilan MM:SS
 */
private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

/**
 * Pembangkit Papan Puzzle yang Dijamin 100% Solvable.
 */
private fun generateSolvableBoard(size: Int): List<Int> {
    val total = size * size
    val board = MutableList(total) { it + 1 }
    board[total - 1] = 0

    var blankIdx = total - 1
    var lastBlankIdx = -1

    val walkSteps = max(200, size * 75)

    for (step in 0 until walkSteps) {
        val blankRow = blankIdx / size
        val blankCol = blankIdx % size

        val neighbors = mutableListOf<Int>()
        if (blankRow > 0) neighbors.add((blankRow - 1) * size + blankCol)
        if (blankRow < size - 1) neighbors.add((blankRow + 1) * size + blankCol)
        if (blankCol > 0) neighbors.add(blankRow * size + (blankCol - 1))
        if (blankCol < size - 1) neighbors.add(blankRow * size + (blankCol + 1))

        val candidates = neighbors.filter { it != lastBlankIdx }.ifEmpty { neighbors }
        val nextIdx = candidates[Random.nextInt(candidates.size)]

        board[blankIdx] = board[nextIdx]
        board[nextIdx] = 0

        lastBlankIdx = blankIdx
        blankIdx = nextIdx
    }

    if (checkIfSolved(board)) {
        val blankRow = blankIdx / size
        val blankCol = blankIdx % size
        val nextIdx = if (blankRow > 0) (blankRow - 1) * size + blankCol else (blankRow + 1) * size + blankCol
        board[blankIdx] = board[nextIdx]
        board[nextIdx] = 0
    }

    return board
}

/**
 * Memeriksa apakah susunan papan sudah solved
 */
private fun checkIfSolved(board: List<Int>): Boolean {
    val total = board.size
    for (i in 0 until total - 1) {
        if (board[i] != i + 1) return false
    }
    return board[total - 1] == 0
}

/**
 * Decode dan crop bitmap dari drawable resource
 */
private fun loadResourceSquareBitmap(context: Context, @DrawableRes resId: Int): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeResource(context.resources, resId, options) ?: return null
        val side = min(raw.width, raw.height)
        val x = (raw.width - side) / 2
        val y = (raw.height - side) / 2
        val cropped = Bitmap.createBitmap(raw, x, y, side, side)
        cropped.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * Decode dan crop bitmap dari URI foto galeri
 */
private fun loadAndCropSquareBitmap(context: Context, uri: Uri): ImageBitmap? {
    return try {
        val rawBitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val side = min(rawBitmap.width, rawBitmap.height)
        val x = (rawBitmap.width - side) / 2
        val y = (rawBitmap.height - side) / 2
        val cropped = Bitmap.createBitmap(rawBitmap, x, y, side, side)
        cropped.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
