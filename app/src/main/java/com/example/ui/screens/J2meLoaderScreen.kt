package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.j2me.J2meGameActivity
import com.example.j2me.J2meInstaller
import com.example.j2me.J2meStore
import kotlinx.coroutines.launch

@Composable
fun J2meLoaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf(J2meStore.listGames(context)) }
    var installing by remember { mutableStateOf(false) }
    var installProgress by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<J2meStore.Game?>(null) }

    BackHandler { onBack() }

    val jarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val jarName = queryDisplayName(context, uri)
            installing = true
            installProgress = 0
            scope.launch {
                try {
                    val game = J2meInstaller.install(context, uri, jarName) { installProgress = it }
                    games = J2meStore.listGames(context)
                    Toast.makeText(
                        context,
                        "Game \"${game.title}\" berhasil ditambahkan.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        e.message ?: "Gagal menambahkan game.",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    installing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("j2me_loader_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
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
                    text = "J2ME-Loader",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FeatureCard()

            Button(
                onClick = {
                    jarPicker.launch(
                        arrayOf(
                            "application/java-archive",
                            "application/x-java-archive",
                            "application/zip",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                enabled = !installing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("j2me_add_jar_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tambah Game (.jar)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            if (installing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Menyiapkan game (${installProgress}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { installProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            Text(
                text = "KOLEKSI GAME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (games.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum ada game",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tambahkan berkas .jar dari penyimpanan untuk mulai bermain.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                games.forEach { game ->
                    GameCard(
                        game = game,
                        onPlay = {
                            context.startActivity(
                                Intent(context, J2meGameActivity::class.java)
                                    .putExtra(J2meGameActivity.EXTRA_GAME_ID, game.id)
                            )
                        },
                        onDelete = { pendingDelete = game }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    pendingDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus Game", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Hapus \"${game.title}\" beserta data yang tersimpan di dalam aplikasi?",
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        J2meStore.delete(game)
                        games = J2meStore.listGames(context)
                        pendingDelete = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun FeatureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Emulator J2ME",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Jalankan game .jar langsung di dalam aplikasi",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FeatureRow(
                icon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)) },
                text = "Mendukung sebagian besar game 2D dan 3D, termasuk Mascot Capsule 3D."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(16.dp)) },
                text = "Keyboard virtual dengan tata letak yang bisa disesuaikan per game."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.ZoomOutMap, null, modifier = Modifier.size(16.dp)) },
                text = "Pengaturan layar individual: ukuran, skala, dan rasio aspek."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(16.dp)) },
                text = "Berdasarkan J2ME-Loader (fork J2meLoader), berjalan sepenuhnya offline."
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: @Composable () -> Unit, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GameCard(game: J2meStore.Game, onPlay: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("j2me_game_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1F2937)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = Color(0xFF93C5FD),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                val details = buildString {
                    if (game.version.isNotBlank()) append("v${game.version}")
                    if (game.vendor.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(game.vendor)
                    }
                    if (isNotEmpty()) append(" • ")
                    append(formatSize(game.sizeBytes))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus ${game.title}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Mainkan ${game.title}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}