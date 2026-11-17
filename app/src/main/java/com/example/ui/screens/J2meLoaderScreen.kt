package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

/**
 * J2ME Loader - antarmuka native emulator Java ME (game jadul feature phone) di dalam My Singghara.
 *
 * Layar ini adalah "rumah" bagi engine emulator: mengelola pustaka game .jar/.jad milik pengguna.
 * Catatan integrasi: eksekusi bytecode game (runtime JVM + MicroEmulator + dexlib/ASM + engine 3D
 * native Mascot Capsule) merupakan fase berikutnya. Titik pemanggilan runtime ditandai jelas pada
 * [launchGame] di bawah, sehingga saat engine sudah di-vendor, cukup menyambungkannya di satu tempat.
 */
@Composable
fun J2meLoaderScreen(
    viewModel: MainViewModel? = null,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mysingghara_prefs", Context.MODE_PRIVATE) }

    val accent = MaterialTheme.colorScheme.primary
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    // Pustaka game tersimpan sebagai kumpulan URI persistable di SharedPreferences.
    var games by remember {
        mutableStateOf(prefs.getStringSet(KEY_J2ME_GAMES, emptySet())?.toList().orEmpty())
    }

    fun persist(list: List<String>) {
        prefs.edit().putStringSet(KEY_J2ME_GAMES, list.toSet()).apply()
        games = list
    }

    fun displayName(uriStr: String): String {
        val seg = Uri.parse(uriStr).lastPathSegment ?: uriStr.substringAfterLast('/')
        return seg.substringBeforeLast('.', seg)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Beberapa provider tidak mengizinkan persistable; tetap tambahkan untuk sesi ini.
            }
            val u = uri.toString()
            if (!games.contains(u)) persist(games + u)
        }
    }

    // Titik integrasi runtime native: serahkan Uri .jar/.jad ke alur import engine (MainActivity),
    // yang membuka InstallerDialog (AppInstaller men-dex & ekstrak ke emulatorDir) lalu
    // menjalankan MicroActivity. Ini memakai jalur install engine yang sudah teruji.
    var statusMsg by remember { mutableStateOf<String?>(null) }
    fun launchGame(uriStr: String) {
        val launch = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uriStr), "application/java-archive")
            .setClassName(context.packageName, "ru.playsoftware.j2meloader.MainActivity")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(launch)
            statusMsg = null
        } catch (t: Throwable) {
            statusMsg = "Gagal menjalankan ${displayName(uriStr)}: ${t.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "J2ME Loader",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Emulator Game Jadul (Java ME)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${games.size} game",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }

        // Kartu info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = cardBorder
        ) {
            Text(
                text = "Jalankan game/aplikasi .jar/.jad lawas (Nokia, Samsung, Sony Ericsson, dll) " +
                    "secara native di dalam aplikasi. Tambahkan game dari penyimpanan perangkat.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Aksi tambah game
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tambah Game (.jar / .jad)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (statusMsg != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
            ) {
                Text(
                    text = statusMsg!!,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "PUSTAKA GAME",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (games.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Belum ada game. Tekan tombol di atas untuk menambahkan file .jar/.jad.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it }) { uriStr ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Gamepad,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = displayName(uriStr),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { launchGame(uriStr) }) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Jalankan",
                                    tint = accent
                                )
                            }
                            IconButton(onClick = { persist(games.filter { it != uriStr }) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val KEY_J2ME_GAMES = "j2me_loader_games"
