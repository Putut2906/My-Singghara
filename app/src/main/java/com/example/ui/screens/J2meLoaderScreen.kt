package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import java.io.File

/**
 * Pustaka game J2ME sebagai layar Compose di dalam app (Jalur A).
 * Karena dirender di dalam Scaffold global, otomatis memakai top bar aplikasi
 * (hamburger membuka drawer + ikon notifikasi), tanpa judul, dan warna Slate
 * light/dark dari MaterialTheme. Daftar game dibaca dari folder 'converted'
 * engine; menjalankan game memakai Config.startApp (proses :midlet).
 */
@Composable
fun J2meLoaderScreen(
    viewModel: MainViewModel? = null,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // Scan ulang game terpasang (subfolder pada Config.getAppDir()).
    val installed = remember(refresh) { scanInstalledGames(context) }

    var query by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }

    val shown = remember(installed, query) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) installed else installed.filter { it.title.lowercase().contains(q) }
        filtered.sortedBy { it.title.lowercase() }
    }

    // Tambah game: pilih .jar/.jad lalu serahkan ke alur install engine (InstallerDialog).
    val addPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val launch = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/java-archive")
                .setClassName(context.packageName, "ru.playsoftware.j2meloader.MainActivity")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(launch)
            } catch (t: Throwable) {
                android.widget.Toast.makeText(context, "Gagal membuka installer: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Segarkan daftar saat layar kembali (mis. seusai install).
    DisposableEffect(Unit) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        // Bar kontrol: cari + urut + toggle kisi/list
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Cari game", fontSize = 14.sp) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions.Default
            )
            IconButton(onClick = { gridMode = !gridMode }) {
                Icon(
                    imageVector = if (gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (gridMode) "Tampilan daftar" else "Tampilan kisi",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { /* sudah diurutkan berdasarkan judul */ }) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Urutkan (judul)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Aksi tambah game
        Button(
            onClick = { addPicker.launch(arrayOf("*/*")) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Tambah Game (.jar / .jad)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (shown.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    if (query.isNotBlank()) "Tidak ada game yang cocok." else "Belum ada game terpasang. Tekan \"Tambah Game\" di atas.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = if (gridMode) GridCells.Adaptive(minSize = 96.dp) else GridCells.Fixed(1),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(shown, key = { it.path }) { g ->
                    if (gridMode) GameTile(game = g, onRun = { runGame(context, g) })
                    else GameRow(game = g, onRun = { runGame(context, g) })
                }
            }
        }
    }
}

private data class J2meGame(val title: String, val path: String)

private fun scanInstalledGames(context: Context): List<J2meGame> {
    return try {
        val dir = File(ru.playsoftware.j2meloader.config.Config.getAppDir())
        if (!dir.isDirectory) return emptyList()
        dir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { J2meGame(title = it.name, path = it.absolutePath) }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }
}

private fun runGame(context: Context, game: J2meGame) {
    try {
        // showSettings=false -> langsung jalankan suite terpasang di proses :midlet.
        ru.playsoftware.j2meloader.config.Config.startApp(context, game.title, game.path, false, null)
    } catch (t: Throwable) {
        android.widget.Toast.makeText(context, "Gagal menjalankan: ${t.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun GameTile(game: J2meGame, onRun: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onRun)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = game.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GameRow(game: J2meGame, onRun: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onRun)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = game.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
