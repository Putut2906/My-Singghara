package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun GameScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }
    val activeGameSection = viewModel.activeGameSectionInGameScreen

    LaunchedEffect(activeGameSection) {
        viewModel.isBottomBarVisible = (activeGameSection != "block_puzzle" && activeGameSection != "tetris" && activeGameSection != "sliding_puzzle")
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.isBottomBarVisible = true
        }
    }

    BackHandler {
        if (activeGameSection != null) {
            viewModel.activeGameSectionInGameScreen = null
        } else {
            viewModel.currentRoute = "home"
            viewModel.showAllAppsPage = true
        }
    }

    if (activeGameSection == "block_puzzle") {
        BlockPuzzleGame(
            onBackClick = { viewModel.activeGameSectionInGameScreen = null }
        )
    } else if (activeGameSection == "tetris") {
        TetrisGame(
            onBackClick = { viewModel.activeGameSectionInGameScreen = null }
        )
    } else if (activeGameSection == "sliding_puzzle") {
        SlidingPuzzleGame(
            onBackClick = { viewModel.activeGameSectionInGameScreen = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("game_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BACK BUTTON HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.currentRoute = "home"
                        viewModel.showAllAppsPage = true
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali ke Beranda",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Kembali ke Beranda",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Game & Hiburan",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header List Game
                Text(
                    text = "Daftar Koleksi Retro",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    letterSpacing = 0.5.sp
                )

                // Opsi Unggulan: Permainan Puzzle Geser (Sliding Puzzle)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.recordAppOpen("sliding_puzzle")
                            viewModel.activeGameSectionInGameScreen = "sliding_puzzle"
                        }
                        .testTag("game_sliding_puzzle_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFE65100), Color(0xFFFF9800))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Puzzle Geser",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "BARU",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "3x3 hingga 6x6 • Alam, Harimau, Borobudur, Angka & Foto Pribadi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Mainkan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Opsi 1: Block Puzzle Classic
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.recordAppOpen("block_puzzle")
                            viewModel.activeGameSectionInGameScreen = "block_puzzle"
                        }
                        .testTag("game_block_puzzle_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2C5E6A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Block Puzzle Classic",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            var localHighScore by remember { mutableStateOf(prefs.getInt("puzzle_high_score", 0)) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Skor Teratas: $localHighScore",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (localHighScore > 0) {
                                    Text(
                                        text = "Reset",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                prefs.edit().putInt("puzzle_high_score", 0).apply()
                                                localHighScore = 0
                                                viewModel.showToastPopup("Skor tertinggi berhasil direset!")
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Mainkan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Opsi 2: Gimbot Brick Game 9999 In 1 Classic
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.recordAppOpen("tetris")
                            viewModel.activeGameSectionInGameScreen = "tetris"
                        }
                        .testTag("game_tetris_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF306230)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = Color(0xFF9BBC0F),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gimbot Brick Game",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Koleksi: Tetris, Shooter, Racing, Snake, Breakout",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            var localTetrisScore by remember { mutableStateOf(prefs.getInt("tetris_high_score", 0)) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Skor Teratas: $localTetrisScore",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (localTetrisScore > 0) {
                                    Text(
                                        text = "Reset",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                prefs.edit().putInt("tetris_high_score", 0).apply()
                                                localTetrisScore = 0
                                                viewModel.showToastPopup("Skor tertinggi Tetris berhasil direset!")
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Mainkan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
