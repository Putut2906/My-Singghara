package com.example.ui.screens

import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.j2me.J2meFiles
import com.example.j2me.J2meInstaller
import com.example.j2me.J2meLauncher
import com.example.j2me.J2meProfile
import com.example.j2me.J2meProfiles
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
    var selectedGame by remember { mutableStateOf<J2meStore.Game?>(null) }

    BackHandler { onBack() }

    val jarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val jarName = J2meFiles.queryDisplayName(context, uri)
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

    val currentGame = selectedGame
    if (currentGame != null) {
        J2meGameSettingsScreen(
            game = currentGame,
            onBack = { selectedGame = null },
            onStart = { profile ->
                J2meProfiles.save(currentGame, profile)
                J2meLauncher.launchGame(context, currentGame.id)
            }
        )
        return
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
                onClick = { jarPicker.launch(J2meFiles.JAR_MIME_TYPES) },
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
                        onPlay = { selectedGame = game },
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
                text = "Menjalankan game MIDP 2D (dan sebagian game 3D berbasis Java)."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(16.dp)) },
                text = "Keyboard virtual, pemetaan tombol, dan pengaturan tersimpan per game."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.ZoomOutMap, null, modifier = Modifier.size(16.dp)) },
                text = "Opsi layar lengkap: skala, orientasi, gravitasi, filter, FPS, dan shader."
            )
            FeatureRow(
                icon = { Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(16.dp)) },
                text = "Game disalin ke penyimpanan internal aplikasi, berjalan sepenuhnya offline."
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
            GameIcon(game = game, size = 48)

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
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ketuk untuk pengaturan & main",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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

@Composable
private fun GameIcon(game: J2meStore.Game, size: Int) {
    val bitmap = remember(game.id, game.iconFile?.length()) {
        game.iconFile?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Ikon ${game.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size((size * 0.52f).dp)
            )
        }
    }
}

@Composable
private fun J2meGameSettingsScreen(
    game: J2meStore.Game,
    onBack: () -> Unit,
    onStart: (J2meProfile) -> Unit
) {
    var profile by remember(game.id) { mutableStateOf(J2meProfiles.load(game)) }
    var widthText by remember(game.id) { mutableStateOf(profile.screenWidth.toString()) }
    var heightText by remember(game.id) { mutableStateOf(profile.screenHeight.toString()) }
    var scaleText by remember(game.id) { mutableStateOf(profile.scaleRatio.toString()) }
    var fpsText by remember(game.id) { mutableStateOf(profile.fpsLimit.toString()) }
    var fontSmallText by remember(game.id) { mutableStateOf(profile.fontSizeSmall.toString()) }
    var fontMediumText by remember(game.id) { mutableStateOf(profile.fontSizeMedium.toString()) }
    var fontLargeText by remember(game.id) { mutableStateOf(profile.fontSizeLarge.toString()) }
    var keyboardAlpha by remember(game.id) { mutableFloatStateOf(profile.virtualKeyboardAlpha.toFloat()) }

    fun syncTexts(value: J2meProfile) {
        widthText = value.screenWidth.toString()
        heightText = value.screenHeight.toString()
        scaleText = value.scaleRatio.toString()
        fpsText = value.fpsLimit.toString()
        fontSmallText = value.fontSizeSmall.toString()
        fontMediumText = value.fontSizeMedium.toString()
        fontLargeText = value.fontSizeLarge.toString()
        keyboardAlpha = value.virtualKeyboardAlpha.toFloat()
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("j2me_settings_screen")
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
                contentDescription = "Kembali",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Kembali ke Koleksi",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Pengaturan Game",
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(
                title = "Identitas Game",
                subtitle = "Diisi otomatis dari META-INF/MANIFEST.MF pada berkas .jar."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(game = game, size = 52)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = buildString {
                                if (game.vendor.isNotBlank()) append(game.vendor)
                                if (game.version.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append("v${game.version}")
                                }
                            }.ifBlank { "Informasi vendor tidak tersedia" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SettingsSection(title = "Opsi Layar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingNumberField(
                            label = "Lebar virtual",
                            value = widthText,
                            onValueChange = { widthText = it }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        SettingNumberField(
                            label = "Tinggi virtual",
                            value = heightText,
                            onValueChange = { heightText = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(128 to 160, 176 to 220, 240 to 320, 360 to 640).forEach { (w, h) ->
                        OutlinedButton(
                            onClick = {
                                widthText = w.toString()
                                heightText = h.toString()
                                profile = profile.copy(screenWidth = w, screenHeight = h)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$w×$h", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                SettingNumberField(
                    label = "Skala (%)",
                    value = scaleText,
                    onValueChange = { scaleText = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingDropdown(
                    label = "Orientasi layar",
                    selected = profile.orientation,
                    options = listOf(
                        J2meProfile.ORIENTATION_AUTO to "Otomatis (ikut perangkat)",
                        J2meProfile.ORIENTATION_PORTRAIT to "Potret",
                        J2meProfile.ORIENTATION_LANDSCAPE to "Lanskap"
                    ),
                    onSelect = { profile = profile.copy(orientation = it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingDropdown(
                    label = "Gravitasi layar",
                    selected = profile.gravity,
                    options = listOf(
                        J2meProfile.GRAVITY_LEFT to "Kiri",
                        J2meProfile.GRAVITY_TOP to "Atas",
                        J2meProfile.GRAVITY_CENTER to "Tengah",
                        J2meProfile.GRAVITY_RIGHT to "Kanan",
                        J2meProfile.GRAVITY_BOTTOM to "Bawah"
                    ),
                    onSelect = { profile = profile.copy(gravity = it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingDropdown(
                    label = "Jenis skala",
                    selected = profile.scaleType,
                    options = listOf(
                        J2meProfile.SCALE_FIT to "Sesuaikan layar (fit)",
                        J2meProfile.SCALE_STRETCH to "Rentangkan penuh",
                        J2meProfile.SCALE_NONE to "Tanpa penskalaan"
                    ),
                    onSelect = { profile = profile.copy(scaleType = it) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SettingSwitch(
                    label = "Pertahankan aspek rasio",
                    description = "Mencegah gambar gepeng saat diskalakan.",
                    checked = profile.keepAspectRatio,
                    onCheckedChange = { profile = profile.copy(keepAspectRatio = it) }
                )
                SettingSwitch(
                    label = "Filter gambar",
                    description = "Penghalusan bilinear saat layar diperbesar.",
                    checked = profile.filter,
                    onCheckedChange = { profile = profile.copy(filter = it) }
                )
                SettingSwitch(
                    label = "Mode pemrosesan seketika",
                    description = "Menggambar langsung tanpa buffer ganda.",
                    checked = profile.immediateMode,
                    onCheckedChange = { profile = profile.copy(immediateMode = it) }
                )
                SettingSwitch(
                    label = "Paksa layar penuh",
                    description = "Menyembunyikan status bar dan navigasi.",
                    checked = profile.forceFullscreen,
                    onCheckedChange = { profile = profile.copy(forceFullscreen = it) }
                )
                SettingSwitch(
                    label = "Tampilkan FPS",
                    description = "Menampilkan penghitung FPS di sudut layar.",
                    checked = profile.showFps,
                    onCheckedChange = { profile = profile.copy(showFps = it) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                SettingDropdown(
                    label = "Mode grafik",
                    selected = profile.graphicsMode,
                    options = listOf(
                        J2meProfile.GRAPHICS_STANDARD to "Standar (perangkat keras)",
                        J2meProfile.GRAPHICS_SOFTWARE to "Kompatibilitas (software)"
                    ),
                    onSelect = { profile = profile.copy(graphicsMode = it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingDropdown(
                    label = "Shader",
                    selected = profile.shader,
                    options = listOf(
                        J2meProfile.SHADER_NONE to "Tanpa shader",
                        J2meProfile.SHADER_BILINEAR to "Bilinear halus",
                        J2meProfile.SHADER_NEAREST to "Nearest (piksel tajam)"
                    ),
                    onSelect = { profile = profile.copy(shader = it) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SettingNumberField(
                    label = "Batas FPS (0 = tanpa batas)",
                    value = fpsText,
                    onValueChange = { fpsText = it }
                )
            }

            SettingsSection(title = "Opsi Font") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingNumberField(
                            label = "Kecil",
                            value = fontSmallText,
                            onValueChange = { fontSmallText = it }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        SettingNumberField(
                            label = "Medium",
                            value = fontMediumText,
                            onValueChange = { fontMediumText = it }
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        SettingNumberField(
                            label = "Besar",
                            value = fontLargeText,
                            onValueChange = { fontLargeText = it }
                        )
                    }
                }
                SettingSwitch(
                    label = "Ukuran dalam SP",
                    description = "Mengikuti skala font sistem perangkat.",
                    checked = profile.fontDimensions,
                    onCheckedChange = { profile = profile.copy(fontDimensions = it) }
                )
                SettingSwitch(
                    label = "Anti-aliasing font",
                    description = "Tepi huruf lebih halus.",
                    checked = profile.fontAntialias,
                    onCheckedChange = { profile = profile.copy(fontAntialias = it) }
                )
            }

            SettingsSection(title = "Perangkat Masukan") {
                SettingSwitch(
                    label = "Input sentuh",
                    description = "Meneruskan sentuhan layar ke game.",
                    checked = profile.touchInput,
                    onCheckedChange = { profile = profile.copy(touchInput = it) }
                )
                SettingSwitch(
                    label = "Keyboard virtual",
                    description = "Menampilkan tombol virtual di atas game.",
                    checked = profile.showKeyboard,
                    onCheckedChange = { profile = profile.copy(showKeyboard = it) }
                )
                if (profile.showKeyboard) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Opasitas keyboard: ${keyboardAlpha.toInt()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = keyboardAlpha,
                        onValueChange = {
                            keyboardAlpha = it
                            profile = profile.copy(virtualKeyboardAlpha = it.toInt())
                        },
                        valueRange = 0f..255f
                    )
                }
            }

            SettingsSection(
                title = "Pemetaan Tombol",
                subtitle = "Tombol fisik dan gamepad dipetakan ke aksi MIDP."
            ) {
                KeyMappingDropdown(
                    label = "Atas",
                    selected = profile.keyUp,
                    onSelect = { profile = profile.copy(keyUp = it) }
                )
                KeyMappingDropdown(
                    label = "Bawah",
                    selected = profile.keyDown,
                    onSelect = { profile = profile.copy(keyDown = it) }
                )
                KeyMappingDropdown(
                    label = "Kiri",
                    selected = profile.keyLeft,
                    onSelect = { profile = profile.copy(keyLeft = it) }
                )
                KeyMappingDropdown(
                    label = "Kanan",
                    selected = profile.keyRight,
                    onSelect = { profile = profile.copy(keyRight = it) }
                )
                KeyMappingDropdown(
                    label = "Aksi / tembak",
                    selected = profile.keyFire,
                    onSelect = { profile = profile.copy(keyFire = it) }
                )
                KeyMappingDropdown(
                    label = "Tombol lunak kiri",
                    selected = profile.keySoftLeft,
                    onSelect = { profile = profile.copy(keySoftLeft = it) }
                )
                KeyMappingDropdown(
                    label = "Tombol lunak kanan",
                    selected = profile.keySoftRight,
                    onSelect = { profile = profile.copy(keySoftRight = it) }
                )
            }

            SettingsSection(
                title = "Properti Sistem",
                subtitle = "Nilai ini dibaca game lewat MIDlet.getAppProperty dan menimpa manifest."
            ) {
                OutlinedTextField(
                    value = profile.systemProperties,
                    onValueChange = { profile = profile.copy(systemProperties = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    label = { Text("Properti (format KEY=VALUE per baris)", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val reset = J2meProfiles.reset(game)
                    profile = reset
                    syncTexts(reset)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Atur Ulang", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    val parsed = profile.copy(
                        screenWidth = widthText.toIntOrNull()?.coerceIn(64, 2048)
                            ?: profile.screenWidth,
                        screenHeight = heightText.toIntOrNull()?.coerceIn(64, 2048)
                            ?: profile.screenHeight,
                        scaleRatio = scaleText.toIntOrNull()?.coerceIn(10, 200)
                            ?: profile.scaleRatio,
                        fpsLimit = fpsText.toIntOrNull()?.coerceIn(0, 240)
                            ?: profile.fpsLimit,
                        fontSizeSmall = fontSmallText.toIntOrNull()?.coerceIn(6, 96)
                            ?: profile.fontSizeSmall,
                        fontSizeMedium = fontMediumText.toIntOrNull()?.coerceIn(6, 96)
                            ?: profile.fontSizeMedium,
                        fontSizeLarge = fontLargeText.toIntOrNull()?.coerceIn(6, 96)
                            ?: profile.fontSizeLarge,
                        virtualKeyboardAlpha = keyboardAlpha.toInt().coerceIn(0, 255)
                    )
                    onStart(parsed)
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(46.dp)
                    .testTag("j2me_start_game_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Simpan & Mulai", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= 5 && input.all { it.isDigit() }) onValueChange(input)
        },
        label = { Text(label, fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = 13.sp)
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> SettingDropdown(
    label: String,
    selected: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected.toString()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(5.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = selectedLabel,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text, fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyMappingDropdown(
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    SettingDropdown(
        label = label,
        selected = selected,
        options = KEY_MAPPING_OPTIONS,
        onSelect = onSelect
    )
    Spacer(modifier = Modifier.height(8.dp))
}

private val KEY_MAPPING_OPTIONS: List<Pair<Int, String>> = buildList {
    add(KeyEvent.KEYCODE_DPAD_UP to "D-pad Atas")
    add(KeyEvent.KEYCODE_DPAD_DOWN to "D-pad Bawah")
    add(KeyEvent.KEYCODE_DPAD_LEFT to "D-pad Kiri")
    add(KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad Kanan")
    add(KeyEvent.KEYCODE_DPAD_CENTER to "D-pad Tengah")
    add(KeyEvent.KEYCODE_ENTER to "Enter")
    add(KeyEvent.KEYCODE_SPACE to "Spasi")
    add(KeyEvent.KEYCODE_SOFT_LEFT to "Tombol lunak kiri")
    add(KeyEvent.KEYCODE_SOFT_RIGHT to "Tombol lunak kanan")
    add(KeyEvent.KEYCODE_TAB to "Tab")
    add(KeyEvent.KEYCODE_DEL to "Backspace")
    add(KeyEvent.KEYCODE_A to "A")
    add(KeyEvent.KEYCODE_S to "S")
    add(KeyEvent.KEYCODE_D to "D")
    add(KeyEvent.KEYCODE_W to "W")
    add(KeyEvent.KEYCODE_J to "J")
    add(KeyEvent.KEYCODE_K to "K")
    add(KeyEvent.KEYCODE_L to "L")
    add(KeyEvent.KEYCODE_U to "U")
    add(KeyEvent.KEYCODE_I to "I")
    add(KeyEvent.KEYCODE_O to "O")
    for (code in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
        add(code to "Angka ${code - KeyEvent.KEYCODE_0}")
    }
    add(KeyEvent.KEYCODE_BUTTON_A to "Gamepad A")
    add(KeyEvent.KEYCODE_BUTTON_B to "Gamepad B")
    add(KeyEvent.KEYCODE_BUTTON_X to "Gamepad X")
    add(KeyEvent.KEYCODE_BUTTON_Y to "Gamepad Y")
    add(KeyEvent.KEYCODE_BUTTON_L1 to "Gamepad L1")
    add(KeyEvent.KEYCODE_BUTTON_R1 to "Gamepad R1")
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
