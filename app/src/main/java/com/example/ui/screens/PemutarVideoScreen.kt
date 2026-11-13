package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.MainViewModel
import com.example.ui.video.PrivateVideoStore
import com.example.ui.video.VideoFolder
import com.example.ui.video.VideoItem
import com.example.ui.video.VideoLibraryScanner
import com.example.ui.video.VideoSort
import com.example.ui.video.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ALL_VIDEOS_BUCKET_ID = -1L

private fun videoReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun hasVideoReadPermission(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(context, videoReadPermission()) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return true
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
}

private val VideoSort.label: String
    get() = when (this) {
        VideoSort.DATE_DESC -> "Terbaru"
        VideoSort.NAME_ASC -> "Nama (A-Z)"
        VideoSort.SIZE_DESC -> "Ukuran terbesar"
        VideoSort.DURATION_DESC -> "Durasi terpanjang"
    }

@Composable
fun PemutarVideoScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }
    val current = activeVideo
    if (current == null) {
        VideoLibraryView(
            viewModel = viewModel,
            onBack = onBack,
            onPlay = { activeVideo = it }
        )
    } else {
        VideoPlayerView(
            item = current,
            onClose = { activeVideo = null }
        )
    }
}

@Composable
private fun VideoLibraryView(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onPlay: (VideoItem) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasVideoReadPermission(context)) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var scanToken by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(VideoSort.DATE_DESC) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var gridView by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showPrivateFolder by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        scanToken++
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(videoReadPermission())
        }
    }

    LaunchedEffect(scanToken) {
        isScanning = true
        videos = VideoLibraryScanner.scan(context)
        isScanning = false
    }

    val filtered = remember(videos, searchQuery, sort) {
        val base = if (searchQuery.isBlank()) {
            videos
        } else {
            videos.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        VideoLibraryScanner.sort(base, sort)
    }
    val folders = remember(filtered) { VideoLibraryScanner.groupIntoFolders(filtered) }
    val allVideosFolder = VideoFolder(ALL_VIDEOS_BUCKET_ID, "Semua Video", filtered)
    val currentFolder = selectedFolder
    val visibleVideos = when {
        currentFolder == null -> emptyList()
        currentFolder.bucketId == ALL_VIDEOS_BUCKET_ID -> filtered
        else -> filtered.filter { it.bucketId == currentFolder.bucketId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pemutar Video",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isScanning -> "Memindai penyimpanan..."
                        else -> "${videos.size} video • ${folders.size} folder"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showPrivateFolder = true }) {
                Icon(Icons.Default.Lock, contentDescription = "Folder privat", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showUrlDialog = true }) {
                Icon(Icons.Default.Link, contentDescription = "Putar tautan URL", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { scanToken++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Pindai ulang", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Cari video...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus pencarian", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
            IconButton(onClick = { gridView = !gridView }) {
                Icon(
                    imageVector = if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (gridView) "Tampilan daftar terperinci" else "Tampilan kisi",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Urutkan")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    VideoSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == sort) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                sort = option
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        when {
            isScanning -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Memindai seluruh penyimpanan...", fontSize = 12.sp)
                    }
                }
            }
            videos.isEmpty() -> {
                EmptyVideoLibrary(
                    onGrantAccess = { permissionLauncher.launch(videoReadPermission()) },
                    onRescan = { scanToken++ }
                )
            }
            currentFolder == null -> {
                if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(
                            items = listOf(allVideosFolder) + folders,
                            key = { "${it.bucketId}-${it.name}" }
                        ) { folder ->
                            FolderGridCard(
                                folder = folder,
                                label = if (folder.bucketId == ALL_VIDEOS_BUCKET_ID) "Semua Video" else folder.name,
                                onClick = { selectedFolder = folder }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FolderRow(
                                folder = allVideosFolder,
                                label = "Semua Video",
                                onClick = { selectedFolder = allVideosFolder }
                            )
                        }
                        items(folders, key = { "${it.bucketId}-${it.name}" }) { folder ->
                            FolderRow(
                                folder = folder,
                                label = folder.name,
                                onClick = { selectedFolder = folder }
                            )
                        }
                    }
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedFolder = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali ke folder")
                        }
                        Text(
                            text = currentFolder.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${visibleVideos.size} video",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { gridView = !gridView }) {
                            Icon(
                                imageVector = if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (gridView) "Tampilan daftar terperinci" else "Tampilan kisi",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (visibleVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ada video di folder ini.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (gridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            gridItems(visibleVideos, key = { it.uri.toString() }) { video ->
                                VideoGridCard(item = video, onClick = { onPlay(video) })
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visibleVideos, key = { it.uri.toString() }) { video ->
                                VideoRow(item = video, onClick = { onPlay(video) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        var urlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Putar Tautan (Streaming)") },
            text = {
                Column {
                    Text(
                        "Masukkan tautan video langsung (MP4, MKV, HLS .m3u8, atau DASH .mpd).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        placeholder = { Text("https://...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = urlInput.trim()
                        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            val guessedName = Uri.parse(trimmed).lastPathSegment
                                ?.substringBefore('?')
                                ?.takeIf { it.isNotBlank() }
                                ?: "Video Streaming"
                            showUrlDialog = false
                            onPlay(
                                VideoItem(
                                    uri = Uri.parse(trimmed),
                                    name = guessedName,
                                    durationMs = 0L,
                                    sizeBytes = 0L,
                                    dateAddedSec = 0L,
                                    bucketName = "Streaming",
                                    bucketId = ALL_VIDEOS_BUCKET_ID
                                )
                            )
                        } else {
                            viewModel.showToastPopup("Tautan harus diawali http:// atau https://")
                        }
                    }
                ) { Text("Putar") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showPrivateFolder) {
        PrivateFolderDialog(
            onDismiss = { showPrivateFolder = false },
            onPlay = { item ->
                showPrivateFolder = false
                onPlay(item)
            },
            onToast = { viewModel.showToastPopup(it) }
        )
    }
}

@Composable
private fun EmptyVideoLibrary(onGrantAccess: () -> Unit, onRescan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Belum ada video ditemukan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Izinkan akses penyimpanan agar aplikasi dapat memindai video di memori internal dan kartu SD.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrantAccess) { Text("Izinkan Akses") }
                TextButton(onClick = onRescan) { Text("Pindai Ulang") }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: VideoFolder, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.count} video • ${formatSize(folder.totalSize)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VideoRow(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnail(item = item, modifier = Modifier.width(106.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        item.durationLabel.takeIf { item.durationMs > 0 },
                        item.sizeLabel.takeIf { item.sizeBytes > 0 },
                        item.bucketName
                    ).joinToString(" • "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Putar",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderGridCard(folder: VideoFolder, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 4.dp else 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${folder.count} video • ${formatSize(folder.totalSize)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VideoGridCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 4.dp else 1.dp
        )
    ) {
        Column {
            Box {
                VideoThumbnail(item = item, modifier = Modifier.fillMaxWidth())
                if (item.durationMs > 0L) {
                    Text(
                        text = item.durationLabel,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        item.sizeLabel.takeIf { item.sizeBytes > 0 },
                        item.bucketName
                    ).joinToString(" • "),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnail(item: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = VideoLibraryScanner.loadThumbnail(context, item)
    }
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val thumb = bitmap
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun PrivateFolderDialog(
    onDismiss: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPin = remember { PrivateVideoStore.hasPin(context) }
    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(unlocked, refresh) {
        if (unlocked) videos = PrivateVideoStore.list(context)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val copied = PrivateVideoStore.import(context, uri)
                onToast(if (copied) "Video disimpan ke folder privat" else "Gagal menyalin video")
                if (copied) refresh++
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Folder Privat", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Video di folder ini tidak muncul di galeri atau pemindai.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (!unlocked) {
                    Text(
                        text = if (hasPin) "Masukkan PIN untuk membuka folder privat." else "Buat PIN (minimal 4 angka) untuk mengamankan folder ini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }.take(8)
                            pinError = null
                        },
                        singleLine = true,
                        label = { Text(if (hasPin) "PIN" else "PIN Baru") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    pinError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (pinInput.length < 4) {
                                pinError = "PIN minimal 4 angka."
                                onToast(if (pinInput.isEmpty()) (if (hasPin) "Mohon isi PIN!" else "Mohon isi PIN Baru!") else "PIN minimal 4 angka!")
                            } else if (hasPin) {
                                if (PrivateVideoStore.verifyPin(context, pinInput)) {
                                    unlocked = true
                                } else {
                                    pinError = "PIN salah. Coba lagi."
                                }
                            } else {
                                PrivateVideoStore.setPin(context, pinInput)
                                unlocked = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasPin) "Buka Folder" else "Simpan PIN")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${videos.size} video privat",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { importLauncher.launch(arrayOf("video/*")) }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Impor")
                        }
                    }
                    HorizontalDivider()
                    if (videos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Belum ada video privat. Tekan Impor untuk menyalin video ke folder ini (file asli tetap ada di galeri).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(videos, key = { it.uri.toString() }) { video ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlay(video) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = video.sizeLabel,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val deleted = PrivateVideoStore.delete(video.uri)
                                                withContext(Dispatchers.Main) {
                                                    onToast(if (deleted) "Video privat dihapus" else "Gagal menghapus video")
                                                    if (deleted) refresh++
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
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
    }
}
