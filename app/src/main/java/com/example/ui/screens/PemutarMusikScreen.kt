@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.MusicTrack
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PemutarMusikScreen(viewModel: MainViewModel, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Folder, 1: Favorit, 2: Daftar Putar
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Baru Ditambahkan, 1: Paling Sering, 2: Baru Saja
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    LaunchedEffect(showQueueSheet) {
        if (!showQueueSheet) {
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            try {
                delay(100)
                focusRequester.requestFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Panel state: Expanded (Full screen deck) vs Collapsed (Mini player at bottom)
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // Use system/OS back button to exit selected folder or collapse player
    BackHandler(enabled = isPlayerExpanded || selectedFolder != null) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (selectedFolder != null) {
            selectedFolder = null
        }
    }

    // Sync global top app bar with full screen player visibility
    LaunchedEffect(isPlayerExpanded) {
        viewModel.hideGlobalTopAppBar = isPlayerExpanded
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.hideGlobalTopAppBar = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateMusicNotification()
        }
    }

    // Auto-scan folder on entry if already selected but playlist is empty
    LaunchedEffect(Unit) {
        if (viewModel.musicFolderUriStr.isNotEmpty() && viewModel.musicPlaylist.isEmpty()) {
            viewModel.scanMusicFolder(context)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val check = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (check != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(viewModel.musicFolderUriStr) {
        selectedFolder = null
    }

    LaunchedEffect(selectedTab, selectedFolder, selectedSubTab) {
        searchQuery = ""
    }

    // SAF Directory picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.saveMusicFolderUri(uri.toString())
                viewModel.scanMusicFolder(context)
                viewModel.showToastPopup("Folder musik berhasil diatur!")
            } catch (e: Exception) {
                viewModel.showToastPopup("Gagal memproses folder: ${e.message}")
            }
        }
    }

    // Infinite disk rotation animation for Vinyl
    val infiniteTransition = rememberInfiniteTransition(label = "DiskRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val activeRotation = if (viewModel.isMusicPlaying) rotationAngle else 0f

    val activeTrack = if (viewModel.currentTrackIndex in viewModel.musicPlaylist.indices) {
        viewModel.musicPlaylist[viewModel.currentTrackIndex]
    } else null

    // Lyrics and Custom Album Art state
    var isLyricsMode by remember { mutableStateOf(false) }
    var lyricsEditMode by remember { mutableStateOf(false) }
    var manualLyricsText by remember { mutableStateOf("") }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    var fileLyricsText by remember { mutableStateOf("") }

    // Lirik manual (bila pengguna menyimpannya) menjadi koreksi; selain itu lirik
    // yang tertanam di file audio ditampilkan otomatis saat lagu diputar.
    val activeLyricsText = manualLyricsText.ifBlank { fileLyricsText }
    val lyricsSourceLabel = when {
        manualLyricsText.isNotBlank() -> "Lirik manual"
        fileLyricsText.isNotBlank() -> "Lirik tertanam"
        else -> "Lirik"
    }

    val backupLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = viewModel.exportManualLyricsJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonStr.toByteArray())
                }
                viewModel.showToastPopup("Backup lirik manual berhasil disimpan!")
            } catch (e: Exception) {
                viewModel.showToastPopup("Gagal mencadangkan lirik: ${e.message}")
            }
        }
    }

    val restoreLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (jsonStr != null) {
                    val success = viewModel.importManualLyricsJson(jsonStr)
                    if (success) {
                        viewModel.showToastPopup("Pemulihan lirik manual berhasil!")
                        if (activeTrack != null) {
                            manualLyricsText = viewModel.getManualLyrics(activeTrack.uriStr)
                        }
                    } else {
                        viewModel.showToastPopup("Format file tidak valid untuk lirik manual")
                    }
                }
            } catch (e: Exception) {
                viewModel.showToastPopup("Gagal memulihkan lirik: ${e.message}")
            }
        }
    }

    LaunchedEffect(activeTrack) {
        if (activeTrack != null) {
            manualLyricsText = viewModel.getManualLyrics(activeTrack.uriStr)
            isLyricsMode = false // Reset lyrics mode when changing tracks
            lyricsEditMode = false
            isLyricsFullScreen = false // Reset full screen lyrics when changing tracks
            
            // Extract lyrics from the file in background thread
            val embeddedLyrics = withContext(Dispatchers.IO) {
                getEmbeddedLyrics(context, activeTrack.uriStr) ?: ""
            }
            fileLyricsText = embeddedLyrics

            // Lagu yang membawa lirik tertanam langsung menampilkan lirik saat diputar.
            if (embeddedLyrics.isNotBlank()) {
                isLyricsMode = true
            }
        } else {
            fileLyricsText = ""
        }
    }

    // Prevent screen from turning off when lyrics are active
    val isLyricsActive = isLyricsMode || isLyricsFullScreen
    DisposableEffect(isLyricsActive) {
        if (isLyricsActive) {
            val activity = findActivity(context)
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            val activity = findActivity(context)
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val customArtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            activeTrack?.let { track ->
                viewModel.saveCustomAlbumArt(track.uriStr, uri.toString())
                viewModel.showToastPopup("Sampul album berhasil diperbarui!")
            }
        }
    }

    val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var lastListScrollEndTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(mainListState.isScrollInProgress) {
        if (!mainListState.isScrollInProgress) {
            lastListScrollEndTime = System.currentTimeMillis()
        }
    }

    fun canHandleItemClick(): Boolean {
        if (mainListState.isScrollInProgress) return false
        val now = System.currentTimeMillis()
        return (now - lastListScrollEndTime) >= 500
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
            // MAIN SCROLLABLE LIST OF TRACKS (with padding at bottom to avoid overlapping mini player)
            LazyColumn(
                state = mainListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
            ) {
                // Section: Tab Menu Selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isSearchExpanded) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val tabLabels = listOf("Folder", "Favorit", "Daftar Putar")

                                tabLabels.forEachIndexed { index, title ->
                                    val isSelected = selectedTab == index
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        label = "TabLinkColorAnim"
                                    )

                                    Text(
                                        text = title,
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = if (isSelected) {
                                            androidx.compose.ui.text.TextStyle(
                                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                            )
                                        } else {
                                            androidx.compose.ui.text.TextStyle.Default
                                        },
                                        modifier = Modifier
                                            .clickable { selectedTab = index }
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .focusRequester(focusRequester)
                                        .weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Cari...",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    searchQuery = ""
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = if (isSearchExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showSettingsScreen = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opsi",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Render Active Tab Contents
                when (selectedTab) {
                    0 -> { // FOLDER TAB
                        // Removed folder card as it has been moved to 3-dots menu

                        if (viewModel.isMusicLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (viewModel.musicPlaylist.isEmpty()) {
                            item {
                                EmptyStatePlaceholder(
                                    message = if (viewModel.musicFolderUriStr.isNotEmpty()) "Tidak ada file musik ditemukan di folder ini." else "Pilih folder musik SAF untuk memuat lagu."
                                )
                            }
                        } else {
                            val groupedByFolder = viewModel.musicPlaylist.groupBy { it.artist }
                            
                            if (selectedFolder == null) {
                                // Render folder list view as requested
                                groupedByFolder.forEach { (folderName, tracksInFolder) ->
                                    item {
                                        Card(
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { if (canHandleItemClick()) selectedFolder = folderName }
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(
                                                            text = folderName,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${tracksInFolder.size} Lagu",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = "Buka folder",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Folder selected -> Managed via OS back handler
                                
                                val folderTracks = (groupedByFolder[selectedFolder] ?: emptyList())
                                    .sortedBy { it.title.lowercase() }
                                
                                val filteredFolderTracks = if (searchQuery.isEmpty()) {
                                    folderTracks
                                } else {
                                    folderTracks.filter {
                                        it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }

                                if (filteredFolderTracks.isEmpty()) {
                                    item {
                                        EmptyStatePlaceholder(message = "Tidak ada lagu yang cocok dengan pencarian.")
                                    }
                                } else {
                                    itemsIndexed(filteredFolderTracks, key = { _, track -> track.uriStr }) { index, track ->
                                        TrackListItem(
                                            index = index,
                                            track = track,
                                            viewModel = viewModel,
                                            isActive = viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isFavorite = viewModel.isFavorite(track),
                                            onPlayClick = { if (canHandleItemClick()) viewModel.playTrackDirect(context, track) },
                                            onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> { // FAVORIT TAB
                        val favTracks = viewModel.musicPlaylist
                            .filter { viewModel.isFavorite(it) }
                            .sortedBy { it.title.lowercase() }

                        if (favTracks.isEmpty()) {
                            item {
                                EmptyStatePlaceholder(
                                    message = "Belum ada lagu yang ditambahkan ke favorit. Ketuk ikon hati pada daftar lagu untuk menambahkannya."
                                )
                            }
                        } else {
                            val filteredFavTracks = if (searchQuery.isEmpty()) {
                                favTracks
                            } else {
                                favTracks.filter {
                                    it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.artist.contains(searchQuery, ignoreCase = true)
                                }
                            }

                            if (filteredFavTracks.isEmpty()) {
                                item {
                                    EmptyStatePlaceholder(message = "Tidak ada lagu favorit yang cocok dengan pencarian.")
                                }
                            } else {
                                itemsIndexed(filteredFavTracks, key = { _, track -> track.uriStr }) { index, track ->
                                    TrackListItem(
                                        index = index,
                                        track = track,
                                        viewModel = viewModel,
                                        isActive = viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                        isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                        isFavorite = true,
                                        onPlayClick = { if (canHandleItemClick()) viewModel.playTrackDirect(context, track) },
                                        onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                    )
                                }
                            }
                        }
                    }

                    2 -> { // DAFTAR PUTAR TAB
                        item {
                            // Sub-selection Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val subTabs = listOf("Baru Ditambahkan", "Paling Sering", "Baru Saja", "Daftar Putar Saya")
                                subTabs.forEachIndexed { idx, subTitle ->
                                    val isSubSelected = selectedSubTab == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSubSelected) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            )
                                            .clickable { selectedSubTab = idx }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = subTitle,
                                            fontSize = 9.sp, // Slightly smaller to fit 4 tabs nicely
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSubSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Render Playlist Category Sub-lists
                        when (selectedSubTab) {
                            0 -> { // Newly Added
                                if (viewModel.musicPlaylist.isEmpty()) {
                                    item {
                                        EmptyStatePlaceholder(message = "Tidak ada lagu yang baru ditambahkan.")
                                    }
                                } else {
                                    itemsIndexed(viewModel.musicPlaylist, key = { _, track -> track.uriStr }) { index, track ->
                                        TrackListItem(
                                            index = index,
                                            track = track,
                                            viewModel = viewModel,
                                            isActive = viewModel.currentTrackIndex == index,
                                            isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex == index,
                                            isFavorite = viewModel.isFavorite(track),
                                            onPlayClick = { if (canHandleItemClick()) viewModel.playTrack(context, index) },
                                            onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                        )
                                    }
                                }
                            }
                            1 -> { // Most Played
                                val playedTracks = viewModel.musicPlaylist
                                    .filter { (viewModel.playCounts[it.uriStr] ?: 0) > 0 }
                                    .sortedByDescending { viewModel.playCounts[it.uriStr] ?: 0 }

                                if (playedTracks.isEmpty()) {
                                    item {
                                        EmptyStatePlaceholder(message = "Belum ada lagu yang sering diputar.")
                                    }
                                } else {
                                    itemsIndexed(playedTracks, key = { _, track -> track.uriStr }) { index, track ->
                                        val count = viewModel.playCounts[track.uriStr] ?: 0
                                        TrackListItem(
                                            index = index,
                                            track = track,
                                            viewModel = viewModel,
                                            subtitle = "Diputar $count kali",
                                            isActive = viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isFavorite = viewModel.isFavorite(track),
                                            onPlayClick = { if (canHandleItemClick()) viewModel.playTrackDirect(context, track) },
                                            onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                        )
                                    }
                                }
                            }
                            2 -> { // Recently Played
                                val recentTracks = viewModel.recentlyPlayed

                                if (recentTracks.isEmpty()) {
                                    item {
                                        EmptyStatePlaceholder(message = "Belum ada lagu yang baru saja diputar.")
                                    }
                                } else {
                                    itemsIndexed(recentTracks, key = { _, track -> track.uriStr }) { index, track ->
                                        TrackListItem(
                                            index = index,
                                            track = track,
                                            viewModel = viewModel,
                                            isActive = viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isFavorite = viewModel.isFavorite(track),
                                            onPlayClick = { if (canHandleItemClick()) viewModel.playTrackDirect(context, track) },
                                            onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                        )
                                    }
                                }
                            }
                            3 -> { // Daftar Putar Saya
                                val myPlaylistTracks = viewModel.musicPlaylist.filter { viewModel.isInMyPlaylist(it) }

                                if (myPlaylistTracks.isEmpty()) {
                                    item {
                                        EmptyStatePlaceholder(message = "Belum ada lagu di Daftar Putar Anda. Klik tombol Plus (+) di pemutar musik untuk menambahkan.")
                                    }
                                } else {
                                    itemsIndexed(myPlaylistTracks, key = { _, track -> track.uriStr }) { index, track ->
                                        TrackListItem(
                                            index = index,
                                            track = track,
                                            viewModel = viewModel,
                                            isActive = viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isPlaying = viewModel.isMusicPlaying && viewModel.currentTrackIndex in viewModel.musicPlaylist.indices && viewModel.musicPlaylist[viewModel.currentTrackIndex].uriStr == track.uriStr,
                                            isFavorite = viewModel.isFavorite(track),
                                            onPlayClick = { if (canHandleItemClick()) viewModel.playTrackDirect(context, track) },
                                            onFavoriteClick = { if (canHandleItemClick()) viewModel.toggleFavorite(track) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MINI MUSIC PLAYER PANEL (Sleek, persistent glassmorphic dock at bottom)
            if (activeTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { isPlayerExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rounded Square Album Art Cover as requested
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                TrackAlbumArt(track = activeTrack, viewModel = viewModel, modifier = Modifier.fillMaxSize())
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Titles
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeTrack.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Text(
                                    text = activeTrack.artist,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Controls (Previous, Play/Pause, Next, Queue) as requested
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.playPrevious(context) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Sebelumnya",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.togglePlayPause() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Main/Jeda",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.playNext(context) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Selanjutnya",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { showQueueSheet = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = "Antrean",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FULL IMMERSIVE PLAYER DECK OVERLAY (Slides up smoothly)
            AnimatedVisibility(
                visible = isPlayerExpanded && activeTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeTrack != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Top Bar inside full deck
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isPlayerExpanded = false }) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Perkecil",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = "Kini Memutar",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IconButton(onClick = { showSettingsScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Opsi",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.3f))

                            // BAGIAN TENGAH (INFORMASI LAGU) REDESIGNED
                            // 1. Sampul Album: Gambar visual dari album atau single lagu tersebut
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(horizontal = 16.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                val currentLyrics = remember(activeTrack, activeLyricsText, viewModel.musicTotalDuration) {
                                    if (activeTrack == null || activeLyricsText.isBlank()) emptyList()
                                    else parseLyrics(activeLyricsText, viewModel.musicTotalDuration.toLong())
                                }

                                // Posisi audio dihaluskan per frame agar sorotan teks bergerak
                                // mengikuti ketukan tanpa tersendat oleh pembaruan 100 ms dari service.
                                val smoothPosition by rememberSmoothMusicPosition(
                                    viewModel.musicPlaybackPosition,
                                    viewModel.isMusicPlaying,
                                    enabled = isLyricsMode && !lyricsEditMode && !isLyricsFullScreen
                                )

                                val activeLyricIndex = remember(currentLyrics, viewModel.musicPlaybackPosition) {
                                    val pos = viewModel.musicPlaybackPosition.toLong()
                                    val index = currentLyrics.indexOfLast { it.timeMs <= pos }
                                    if (index == -1 && currentLyrics.isNotEmpty()) 0 else index
                                }

                                val lazyListState = rememberLazyListState()
                                LaunchedEffect(activeLyricIndex, isLyricsMode) {
                                    if (activeLyricIndex >= 0 && activeLyricIndex < currentLyrics.size && isLyricsMode && !lyricsEditMode) {
                                        lazyListState.animateScrollToItem(activeLyricIndex)
                                    }
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (isLyricsMode) {
                                        // LYRICS VIEW
                                        if (lyricsEditMode) {
                                            // Sesi editor dibuat sekali per lagu agar posisi kursor
                                            // tidak melompat saat teks diketik.
                                            var editorValue by remember(activeTrack, lyricsEditMode) {
                                                mutableStateOf(
                                                    androidx.compose.ui.text.input.TextFieldValue(
                                                        manualLyricsText.ifBlank { fileLyricsText }
                                                    )
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                                                    .padding(16.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Tulis / Tempel Lirik (Format LRC)",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    
                                                    // Quick info badge about the player position
                                                    val posMs = viewModel.musicPlaybackPosition
                                                    val minutes = posMs / 60000
                                                    val seconds = (posMs % 60000) / 1000
                                                    Text(
                                                        text = String.format("Posisi: %02d:%02d", minutes, seconds),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                
                                                // Helper Buttons Row
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // 1. Insert timestamp at current cursor
                                                    Button(
                                                        onClick = {
                                                            val currentPos = viewModel.musicPlaybackPosition
                                                            val min = currentPos / 60000
                                                            val sec = (currentPos % 60000) / 1000
                                                            val hundredths = (currentPos % 1000) / 10
                                                            val timestamp = String.format("[%02d:%02d.%02d] ", min, sec, hundredths)

                                                            val text = editorValue.text
                                                            val selection = editorValue.selection
                                                            val start = selection.start
                                                            val end = selection.end
                                                            val newText = text.substring(0, start) + timestamp + text.substring(end)
                                                            editorValue = androidx.compose.ui.text.input.TextFieldValue(
                                                                text = newText,
                                                                selection = androidx.compose.ui.text.TextRange(start + timestamp.length)
                                                            )
                                                            manualLyricsText = newText
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                        ),
                                                        modifier = Modifier.weight(1.2f),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Waktu Baris", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // 1b. Insert word (karaoke) timestamp at current cursor
                                                    Button(
                                                        onClick = {
                                                            val currentPos = viewModel.musicPlaybackPosition
                                                            val min = currentPos / 60000
                                                            val sec = (currentPos % 60000) / 1000
                                                            val hundredths = (currentPos % 1000) / 10
                                                            val timestamp = String.format("<%02d:%02d.%02d>", min, sec, hundredths)

                                                            val text = editorValue.text
                                                            val selection = editorValue.selection
                                                            val start = selection.start
                                                            val end = selection.end
                                                            val newText = text.substring(0, start) + timestamp + text.substring(end)
                                                            editorValue = androidx.compose.ui.text.input.TextFieldValue(
                                                                text = newText,
                                                                selection = androidx.compose.ui.text.TextRange(start + timestamp.length)
                                                            )
                                                            manualLyricsText = newText
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                        ),
                                                        modifier = Modifier.weight(1.3f),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Schedule,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Waktu Kata", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // 2. Load LRC Template
                                                    OutlinedButton(
                                                        onClick = {
                                                            val template = "[ti:Contoh Lagu]\n" +
                                                                    "[ar:Artis Hebat]\n" +
                                                                    "[00:00.00] ~ Intro Instrumental ~\n" +
                                                                    "[00:08.50] \n" +
                                                                    "[00:10.00] <00:10.00>Ini <00:10.55>adalah <00:11.10>contoh <00:11.80>lirik <00:12.40>pertama\n" +
                                                                    "[00:13.60] Tanpa tag kata, lirik tetap tersinkron per baris\n" +
                                                                    "[00:15.20] Yang dinyanyikan dengan tempo teratur\n" +
                                                                    "[00:20.00] \n" +
                                                                    "[00:22.00] Masuk ke bait berikutnya di sini\n" +
                                                                    "[00:27.50] Pastikan jeda antar bait diberikan jeda kosong\n" +
                                                                    "[00:32.00] \n" +
                                                                    "[00:34.00] ~ Reff / Chorus ~\n" +
                                                                    "[00:36.00] Dan ini adalah bagian chorus yang indah\n" +
                                                                    "[00:42.00] Lirik terbagi rapi sesuai ketukan asli\n" +
                                                                    "[00:48.00] \n" +
                                                                    "[00:50.00] ~ Interlude Musik ~\n" +
                                                                    "[01:05.00] \n" +
                                                                    "[01:06.00] Kembali ke bait penutup setelah interlude\n" +
                                                                    "[01:12.00] ~ Outro Instrumental ~\n" +
                                                                    "[01:20.00] "
                                                            editorValue = androidx.compose.ui.text.input.TextFieldValue(
                                                                text = template,
                                                                selection = androidx.compose.ui.text.TextRange(template.length)
                                                            )
                                                            manualLyricsText = template
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lyrics,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Templat LRC", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                
                                                OutlinedTextField(
                                                    value = editorValue,
                                                    onValueChange = { 
                                                        editorValue = it
                                                        manualLyricsText = it.text
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    placeholder = {
                                                        Text(
                                                            "Tulis/tempel lirik di sini. Gunakan [mm:ss.cc] untuk baris dan <mm:ss.cc> untuk kata agar sorotan warna mengikuti vokal secara presisi.",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                    },
                                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                )
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            lyricsEditMode = false
                                                            activeTrack?.let {
                                                                manualLyricsText = viewModel.getManualLyrics(it.uriStr)
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Batal", fontSize = 12.sp)
                                                    }
                                                    
                                                    Button(
                                                        onClick = {
                                                            activeTrack?.let {
                                                                viewModel.saveManualLyrics(it.uriStr, manualLyricsText)
                                                                lyricsEditMode = false
                                                                viewModel.showToastPopup("Lirik manual disimpan!")
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text("Simpan", fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        } else {
                                            // DISPLAY LYRICS MODE
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                                                    .padding(12.dp)
                                            ) {
                                                // Header
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lyrics,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = lyricsSourceLabel,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = { lyricsEditMode = true },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit Lirik",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        
                                                        IconButton(
                                                            onClick = { isLyricsMode = false },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Tutup Lirik",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(8.dp))
                                                
                                                if (currentLyrics.isEmpty()) {
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxWidth()
                                                            .clickable { isLyricsMode = false },
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lyrics,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Lirik tertanam tidak ditemukan",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Tempelkan file .lrc di samping lagu, simpan tag SYLT/USLT di file audio, atau ketuk ikon pensil untuk menulis lirik manual beserta kode waktu.",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(horizontal = 16.dp)
                                                        )
                                                    }
                                                } else {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        LazyColumn(
                                                            state = lazyListState,
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentPadding = PaddingValues(vertical = 40.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            itemsIndexed(currentLyrics) { index, line ->
                                                                val isActive = index == activeLyricIndex
                                                                val scale by animateFloatAsState(
                                                                    targetValue = if (isActive) 1.15f else 0.9f,
                                                                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                                    label = "scale"
                                                                )
                                                                val color by animateColorAsState(
                                                                    targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                    label = "color"
                                                                )
                                                                
                                                                val textContent = if (isActive) {
                                                                    buildKaraokeLine(
                                                                        line = line,
                                                                        positionMs = smoothPosition,
                                                                        pendingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                        sungColor = MaterialTheme.colorScheme.primary,
                                                                        pendingWeight = FontWeight.Medium,
                                                                        sungWeight = FontWeight.ExtraBold
                                                                    )
                                                                } else {
                                                                    buildAnnotatedString {
                                                                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)) {
                                                                            append(line.text)
                                                                        }
                                                                    }
                                                                }
                                                                
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 6.dp, horizontal = 12.dp)
                                                                        .clickable {
                                                                            viewModel.seekTo(line.timeMs.toInt())
                                                                        },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Text(
                                                                        text = textContent,
                                                                        fontSize = 12.sp,
                                                                        textAlign = TextAlign.Center,
                                                                        modifier = Modifier.graphicsLayer(
                                                                            scaleX = scale,
                                                                            scaleY = scale
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        
                                                        // Fading edges for smooth reading
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(24.dp)
                                                                .align(Alignment.TopCenter)
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        colors = listOf(
                                                                            MaterialTheme.colorScheme.surfaceVariant,
                                                                            Color.Transparent
                                                                        )
                                                                    )
                                                                )
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(24.dp)
                                                                .align(Alignment.BottomCenter)
                                                                .background(
                                                                    Brush.verticalGradient(
                                                                        colors = listOf(
                                                                            Color.Transparent,
                                                                            MaterialTheme.colorScheme.surfaceVariant
                                                                        )
                                                                    )
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // ALBUM ART VIEW
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    var dragAmountAccumulated = 0f
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { dragAmountAccumulated = 0f },
                                                        onDragEnd = {
                                                            if (dragAmountAccumulated > 100f) {
                                                                viewModel.playPrevious(context)
                                                            } else if (dragAmountAccumulated < -100f) {
                                                                viewModel.playNext(context)
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            dragAmountAccumulated = 0f
                                                        },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragAmountAccumulated += dragAmount
                                                        }
                                                    )
                                                }
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onTap = { isLyricsMode = true }
                                                    )
                                                }
                                        ) {
                                            TrackAlbumArt(
                                                track = activeTrack,
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            
                                            // 2. Ganti Sampul Button (Top right corner)
                                            IconButton(
                                                onClick = {
                                                    viewModel.preventAutoLock = true
                                                    customArtPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(12.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                    .size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PhotoCamera,
                                                    contentDescription = "Ganti Sampul",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.3f))

                            // Song Metadata & Side Action Row (Redesigned)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = activeTrack.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                                        .clickable { isLyricsFullScreen = true }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeTrack.artist,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Middle Action Row containing Queue, Favorite, and Plus (+) Playlist
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 2. Ikon Garis dengan Nada Musik (Kiri): Tombol untuk melihat antrean lagu (Play Queue)
                                    IconButton(
                                        onClick = { showQueueSheet = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.QueueMusic,
                                            contentDescription = "Lihat Antrean",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // 3. Ikon Hati (Tengah): Menambahkan lagu ini ke daftar "Favorit" kamu
                                    val isFav = viewModel.isFavorite(activeTrack)
                                    IconButton(
                                        onClick = { viewModel.toggleFavorite(activeTrack) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorit",
                                            tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // 4. Ikon Plus (+) (Kanan): Memasukkan lagu ini ke dalam Playlist (Daftar Putar)
                                    val isInPlaylist = viewModel.isInMyPlaylist(activeTrack)
                                    IconButton(
                                        onClick = {
                                            viewModel.toggleMyPlaylist(activeTrack)
                                            val message = if (isInPlaylist) "Dihapus dari Daftar Putar Saya" else "Ditambahkan ke Daftar Putar Saya"
                                            viewModel.showToastPopup(message)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isInPlaylist) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Daftar Putar",
                                            tint = if (isInPlaylist) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.4f))

                            // BARIS DURASI & TOMBOL PEMUTAR UTAMA (Paling Bawah)
                            // 1. Garis Durasi (Progress Bar): Menunjukkan posisi menit lagu
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Slider(
                                    value = viewModel.musicPlaybackPosition.toFloat(),
                                    onValueChange = { viewModel.seekTo(it.toInt()) },
                                    valueRange = 0f..viewModel.musicTotalDuration.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    thumb = {
                                        Box(
                                            modifier = Modifier.size(0.dp)
                                        )
                                    },
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                            ),
                                            enabled = true,
                                            sliderState = sliderState,
                                            modifier = Modifier.height(2.dp)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatDuration(viewModel.musicPlaybackPosition),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatDuration(viewModel.musicTotalDuration),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Premium Media Control Deck (Bottom Buttons)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 2. Ikon Dua Panah Silang: Tombol Shuffle (Acak)
                                IconButton(onClick = {
                                    viewModel.isMusicShuffle = !viewModel.isMusicShuffle
                                    com.example.MusicService.isMusicShuffle = viewModel.isMusicShuffle
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Acak",
                                        tint = if (viewModel.isMusicShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // 3. Ikon Dua Segitiga ke Kiri: Tombol untuk kembali ke awal lagu atau memutar lagu sebelumnya
                                IconButton(
                                    onClick = { viewModel.playPrevious(context) },
                                    enabled = viewModel.musicPlaylist.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Sebelumnya",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // 4. Ikon Segitiga Putih Besar: Tombol Play untuk memulai lagu (jika lagu sedang dijeda/pause)
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { viewModel.togglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isMusicPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Putar/Jeda",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                 // 5. Ikon Dua Segitiga ke Kanan: Tombol Next untuk langsung melompat ke lagu berikutnya
                                IconButton(
                                    onClick = { viewModel.playNext(context) },
                                    enabled = viewModel.musicPlaylist.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Selanjutnya",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // 6. Tombol Repeat / Ulang Mode
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            val nextMode = when (viewModel.musicRepeatMode) {
                                                "NONE" -> "ONE"
                                                "ONE" -> "ALL"
                                                "ALL" -> "AZ"
                                                else -> "NONE"
                                            }
                                            viewModel.musicRepeatMode = nextMode
                                            com.example.MusicService.musicRepeatMode = nextMode
                                        }
                                ) {
                                    val isActive = viewModel.musicRepeatMode != "NONE"
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Mode Pengulangan",
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    when (viewModel.musicRepeatMode) {
                                        "ONE" -> {
                                            Text(
                                                text = "1",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                        "ALL" -> {
                                            Text(
                                                text = "A",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                        "AZ" -> {
                                            Text(
                                                text = "A-Z",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.3f))
                        }
                    }
                }
            }

            // IMMERSIVE FULL-SCREEN LYRICS VIEW OVERLAY
            AnimatedVisibility(
                visible = isLyricsFullScreen && activeTrack != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeTrack != null) {
                    FullScreenLyricsView(
                        activeTrack = activeTrack,
                        viewModel = viewModel,
                        fileLyricsText = fileLyricsText,
                        manualLyricsText = manualLyricsText,
                        onDismiss = { isLyricsFullScreen = false }
                    )
                }
            }

            if (showQueueSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showQueueSheet = false
                        focusManager.clearFocus()
                    },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Antrean Lagu",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (activeTrack != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Folder: ${activeTrack.artist}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        viewModel.isMusicShuffle = !viewModel.isMusicShuffle
                                        com.example.MusicService.instance?.let { service ->
                                            com.example.MusicService.isMusicShuffle = viewModel.isMusicShuffle
                                            service.updateMusicNotification()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Toggle Shuffle",
                                        tint = if (viewModel.isMusicShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = {
                                    showQueueSheet = false
                                    focusManager.clearFocus()
                                }) {
                                    Text("Tutup", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val currentFolderTracks = if (activeTrack != null) {
                            viewModel.musicPlaylist.filter { it.artist == activeTrack.artist }
                        } else {
                            viewModel.musicPlaylist
                        }

                        if (currentFolderTracks.isEmpty()) {
                            Text(
                                text = "Antrean kosong",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(currentFolderTracks) { _, track ->
                                    val isPlaying = activeTrack != null && activeTrack.uriStr == track.uriStr
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                val globalIndex = viewModel.musicPlaylist.indexOfFirst { it.uriStr == track.uriStr }
                                                if (globalIndex != -1) {
                                                    viewModel.playTrack(context, globalIndex)
                                                }
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        ) {
                                            TrackAlbumArt(track = track, viewModel = viewModel, modifier = Modifier.fillMaxSize())
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isPlaying) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Sedang diputar",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
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

        if (showSettingsScreen) {
            BackHandler {
                showSettingsScreen = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = true, onClick = {}) // block click through
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Section Header
                    Text(
                        text = "Pengaturan",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Kelola folder penyimpanan musik dan preferensi pemutar audio Anda.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    // Cards for Settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column {
                            // 1. Folder Musik SAF selection
                            val isConnected = viewModel.musicFolderUriStr.isNotEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.preventAutoLock = true
                                        folderPickerLauncher.launch(null)
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Folder Musik",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isConnected) "Terkoneksi ke penyimpanan lokal SAF" else "Folder musik belum dipilih (Klik untuk memilih)",
                                            fontSize = 12.sp,
                                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // 2. Pindai Ulang Folder
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.scanMusicFolder(context)
                                        viewModel.showToastPopup("Memindai ulang folder musik...")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Pindai Ulang Folder",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Segarkan daftar lagu dari folder aktif",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // 3. Bersihkan Riwayat Putar
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.clearPlayHistory()
                                        viewModel.showToastPopup("Riwayat putar dibersihkan")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bersihkan Riwayat Putar",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Menghapus riwayat putar lagu baru-baru ini",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // 5. Backup Lirik Manual
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.preventAutoLock = true
                                        backupLyricsLauncher.launch("singghara_backup_lirik.json")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backup,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Backup Lirik Manual",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Ekspor semua lirik manual ke file JSON",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // 6. Restore Lirik Manual
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.preventAutoLock = true
                                        restoreLyricsLauncher.launch("application/json")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Restore Lirik Manual",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Impor file lirik manual dari penyimpanan",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // 4. Reset Path Folder
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.saveMusicFolderUri("")
                                        viewModel.musicPlaylist.clear()
                                        viewModel.showToastPopup("Folder musik telah di-reset")
                                    }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Reset Path Folder",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Putus koneksi folder musik saat ini",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Info footer helper
                    Text(
                        text = "Gunakan tombol kembali pada ponsel Anda untuk keluar dari pengaturan.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }

@Composable
fun TrackListItem(
    index: Int,
    track: MusicTrack,
    viewModel: MainViewModel,
    subtitle: String? = null,
    isActive: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val itemBgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(itemBgColor)
            .clickable { onPlayClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail box (Real or procedural album art)
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            TrackAlbumArt(track = track, viewModel = viewModel, modifier = Modifier.fillMaxSize())
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Titles
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isActive && isPlaying) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
            )
            Text(
                text = subtitle ?: track.artist,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isActive && isPlaying) {
                EqualizerAnimation(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun EqualizerAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    val heightScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val heightScale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val heightScale3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val heightScale4 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Row(
        modifier = modifier
            .height(14.dp)
            .width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val barWidth = 2.dp
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(heightScale1)
                .background(color, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(heightScale2)
                .background(color, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(heightScale3)
                .background(color, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(heightScale4)
                .background(color, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
        )
    }
}

@Composable
fun EmptyStatePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun TrackAlbumArt(
    track: MusicTrack,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var embeddedBitmap by remember(track.uriStr) { mutableStateOf<Bitmap?>(null) }
    
    // Attempt to load embedded album art in a separate thread
    LaunchedEffect(track.uriStr) {
        val bitmap = withContext(Dispatchers.IO) {
            getEmbeddedPicture(context, track.uriStr)
        }
        embeddedBitmap = bitmap
    }
    
    val customArtUri = viewModel.getCustomAlbumArt(track.uriStr)
    
    Box(modifier = modifier) {
        if (customArtUri != null) {
            AsyncImage(
                model = customArtUri,
                contentDescription = "Custom Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (embeddedBitmap != null) {
            Image(
                bitmap = embeddedBitmap!!.asImageBitmap(),
                contentDescription = "Embedded Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Procedural gradient fallback
            val title = track.title
            val hash = title.hashCode()
            val colors = remember(title) {
                val hue1 = (hash and 0xFF) * 360f / 256f
                val hue2 = ((hash shr 8) and 0xFF) * 360f / 256f
                listOf(
                    Color.hsv(hue1, 0.65f, 0.8f),
                    Color.hsv(hue2, 0.5f, 0.6f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize(0.5f)
                )
            }
        }
    }
}

@Composable
fun TrackAlbumArt(title: String, modifier: Modifier = Modifier) {
    val hash = title.hashCode()
    val colors = remember(title) {
        val hue1 = (hash and 0xFF) * 360f / 256f
        val hue2 = ((hash shr 8) and 0xFF) * 360f / 256f
        listOf(
            Color.hsv(hue1, 0.65f, 0.8f),
            Color.hsv(hue2, 0.5f, 0.6f)
        )
    }
    Box(
        modifier = modifier
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.5f)
        )
    }
}

private val embeddedArtCache = android.util.LruCache<String, Bitmap>(100)
private val nullEmbeddedArtUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())

fun getEmbeddedPicture(context: android.content.Context, uriStr: String): Bitmap? {
    if (nullEmbeddedArtUris.contains(uriStr)) return null
    val cached = embeddedArtCache.get(uriStr)
    if (cached != null) return cached

    val retriever = MediaMetadataRetriever()
    try {
        val uri = Uri.parse(uriStr)
        retriever.setDataSource(context, uri)
        val art = retriever.embeddedPicture
        if (art != null) {
            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
            if (bitmap != null) {
                embeddedArtCache.put(uriStr, bitmap)
                return bitmap
            }
        }
    } catch (e: Exception) {
        // ignore
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {}
    }
    nullEmbeddedArtUris.add(uriStr)
    return null
}

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

private val lyricLineTagRegex = Regex("\\[(\\d+):(\\d+)(?:[:.](\\d+))?\\]")
private val lyricWordTagRegex = Regex("<(\\d+):(\\d+)(?:[:.](\\d+))?>")
private val lyricOffsetTagRegex = Regex("\\[offset:\\s*([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
private val lyricWordTokenRegex = Regex("\\S+\\s*")

private fun parseLyricTimestamp(minutes: Long, seconds: Long, fraction: String?): Long {
    val frac = fraction ?: "0"
    val msPart = when (frac.length) {
        0 -> 0L
        1 -> (frac.toLongOrNull() ?: 0L) * 100L
        2 -> (frac.toLongOrNull() ?: 0L) * 10L
        else -> frac.take(3).toLongOrNull() ?: 0L
    }
    return (minutes * 60L + seconds) * 1000L + msPart
}

fun generateAutomaticLyrics(title: String, durationMs: Long): List<LyricLine> {
    val cleanTitle = title.replace(Regex("[^a-zA-Z0-9 ]"), "")
    val words = cleanTitle.split(" ").filter { it.isNotBlank() }
    val topic = words.firstOrNull() ?: "Melodi"
    
    // Create poetic lines procedurally using the song title
    val templates = listOf(
        "🎵 (Instrumen Pembuka - Menikmati alunan melodi '$title')",
        "Langkah pertama dimulai, mengiringi sunyinya hati...",
        "Ketika senandung '$topic' mulai bergema di udara...",
        "Ada rasa yang tercipta, mengalir perlahan di dalam dada...",
        "Reff: Dan biarkan melodi '$title' ini membawamu terbang...",
        "Menembus batas mimpi, menggapai bintang yang terang...",
        "Tak ada lagi keraguan, hanya ada kita dan harmoni...",
        "Setiap ketukan membawa cerita yang takkan mati...",
        "Reff: Dan biarkan melodi '$title' ini membawamu terbang...",
        "Menembus batas mimpi, menggapai bintang yang terang...",
        "Perlahan nada mereda, meninggalkan kenangan indah...",
        "🎵 (Instrumen Penutup - Melodi '$title' selesai diputar)"
    )
    
    val duration = if (durationMs > 0) durationMs else 180000L
    val interval = duration / (templates.size)
    return templates.mapIndexed { index, text ->
        LyricLine(
            timeMs = index * interval,
            text = text
        )
    }
}

private data class LyricDraft(
    val timeMs: Long,
    val text: String,
    val wordStarts: List<Pair<Long, String>>
)

private fun buildLyricDraft(timeMs: Long, content: String, offsetMs: Long): LyricDraft {
    val cleanedContent = content.replace("<>", "")
    val wordTags = lyricWordTagRegex.findAll(cleanedContent).toList()
    if (wordTags.isEmpty()) return LyricDraft(timeMs, cleanedContent.trim(), emptyList())

    val prefix = cleanedContent.substring(0, wordTags.first().range.first)
    val segments = mutableListOf<Pair<Long, String>>()

    wordTags.forEachIndexed { index, tag ->
        val textStart = tag.range.last + 1
        val textEnd = if (index < wordTags.lastIndex) wordTags[index + 1].range.first else cleanedContent.length
        if (textStart >= textEnd) return@forEachIndexed
        val text = cleanedContent.substring(textStart, textEnd)
        val startMs = parseLyricTimestamp(
            tag.groupValues[1].toLongOrNull() ?: 0L,
            tag.groupValues[2].toLongOrNull() ?: 0L,
            tag.groupValues.getOrNull(3)
        ) + offsetMs

        if (text.isNotBlank()) {
            segments.add(startMs to text)
        } else if (segments.isNotEmpty()) {
            // Pertahankan spasi antar kata agar hasil render tidak menempel.
            val last = segments.removeAt(segments.lastIndex)
            segments.add(last.first to last.second + text)
        }
    }

    if (segments.isEmpty()) return LyricDraft(timeMs, cleanedContent.trim(), emptyList())
    if (prefix.isNotEmpty()) {
        val first = segments[0]
        segments[0] = first.first to (prefix + first.second)
    }

    val fullText = segments.joinToString("") { it.second }.trim()
    return LyricDraft(timeMs, fullText, segments)
}

private fun countLyricSyllables(word: String): Int {
    val vowels = "aeiouyAEIOUY"
    var count = 0
    var previousWasVowel = false
    word.forEach { char ->
        val isVowel = char in vowels
        if (isVowel && !previousWasVowel) count++
        previousWasVowel = isVowel
    }
    return count.coerceAtLeast(1)
}

/**
 * Membagi durasi satu baris ke kata-katanya secara proporsional terhadap jumlah suku kata.
 * Dipakai saat file hanya menyediakan timestamp per baris; baris yang punya tag kata
 * (<mm:ss.xxx>) memakai waktu aslinya sehingga cocok dengan vokal.
 */
private fun autoTimeLyricWords(text: String, startMs: Long, endMs: Long): List<LyricWord> {
    val tokens = lyricWordTokenRegex.findAll(text).map { it.value }.filter { it.isNotBlank() }.toList()
    if (tokens.isEmpty()) return emptyList()

    val weights = tokens.map { countLyricSyllables(it) }
    val totalWeight = weights.sum().coerceAtLeast(1)
    val span = (endMs - startMs).coerceAtLeast(300L)
    var cursor = startMs

    return tokens.mapIndexed { index, token ->
        val weightDuration = span * weights[index] / totalWeight
        val wordStart = cursor
        val wordEnd = if (index == tokens.lastIndex) startMs + span else cursor + weightDuration
        cursor = wordEnd
        LyricWord(wordStart, wordEnd.coerceAtLeast(wordStart + 60L), token)
    }
}

fun parseLyrics(manualText: String, durationMs: Long): List<LyricLine> {
    if (manualText.isBlank()) return emptyList()

    val normalizedText = manualText.removePrefix("\uFEFF")
    val rawLines = normalizedText.replace("\r\n", "\n").replace('\r', '\n').lines()

    var offsetMs = 0L
    rawLines.forEach { line ->
        lyricOffsetTagRegex.find(line)?.let { match ->
            offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
        }
    }

    val drafts = mutableListOf<LyricDraft>()
    var hasLineTimestamps = false

    rawLines.forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach

        val lineTags = lyricLineTagRegex.findAll(line).toList()
        if (lineTags.isEmpty()) {
            if (lyricWordTagRegex.containsMatchIn(line)) {
                // Tag kata tanpa tag baris: menempel ke baris sebelumnya.
                val anchor = drafts.lastOrNull()?.timeMs ?: 0L
                drafts.add(buildLyricDraft(anchor, line, offsetMs))
            } else if (!lyricOffsetTagRegex.containsMatchIn(line) && !(line.startsWith("[") && line.contains(":"))) {
                val lastTime = drafts.lastOrNull()?.timeMs ?: 0L
                drafts.add(LyricDraft(lastTime + 1000L, line, emptyList()))
            }
            return@forEach
        }

        hasLineTimestamps = true
        var content = line
        lineTags.forEach { content = content.replace(it.value, "") }
        content = content.trim()

        lineTags.forEach { tag ->
            val timeMs = parseLyricTimestamp(
                tag.groupValues[1].toLongOrNull() ?: 0L,
                tag.groupValues[2].toLongOrNull() ?: 0L,
                tag.groupValues.getOrNull(3)
            ) + offsetMs
            drafts.add(buildLyricDraft(timeMs, content, offsetMs))
        }
    }

    if (drafts.isEmpty()) return emptyList()

    val fallbackDuration = drafts.maxOf { it.timeMs } + 5000L
    val duration = if (durationMs > 0) durationMs else fallbackDuration

    if (!hasLineTimestamps) {
        val interval = (duration / drafts.size.coerceAtLeast(1)).coerceAtLeast(500L)
        return drafts.mapIndexed { index, draft ->
            val start = index * interval
            val end = if (index < drafts.lastIndex) (index + 1) * interval else duration.coerceAtLeast(start + 3000L)
            LyricLine(start, draft.text, autoTimeLyricWords(draft.text, start, end))
        }
    }

    val sorted = drafts.sortedBy { it.timeMs }
    return sorted.mapIndexed { index, draft ->
        val naturalEnd = when {
            index < sorted.lastIndex -> sorted[index + 1].timeMs
            else -> duration
        }
        // Batasi rentang sorotan agar satu baris tidak "menyeret" sepanjang jeda instrumental.
        val lineEnd = naturalEnd
            .coerceAtMost(draft.timeMs + 10000L)
            .coerceAtLeast(draft.timeMs + 500L)

        val words = if (draft.wordStarts.isEmpty()) {
            autoTimeLyricWords(draft.text, draft.timeMs, lineEnd)
        } else {
            val starts = draft.wordStarts.sortedBy { it.first }
            starts.mapIndexed { wordIndex, (start, text) ->
                val end = if (wordIndex < starts.lastIndex) starts[wordIndex + 1].first else lineEnd
                LyricWord(start, end.coerceAtLeast(start + 60L), text)
            }
        }
        LyricLine(draft.timeMs, draft.text, words)
    }
}

/**
 * Posisi audio yang diperhalus per frame (60 fps) dengan koreksi drift terhadap
 * posisi resmi dari service setiap kali nilai baru dikirim (interval 100 ms).
 */
@Composable
fun rememberSmoothMusicPosition(reportedMs: Int, isPlaying: Boolean, enabled: Boolean = true): State<Long> {
    val smoothPosition = remember { mutableLongStateOf(reportedMs.toLong()) }

    LaunchedEffect(reportedMs, isPlaying, enabled) {
        val reported = reportedMs.toLong()
        val current = smoothPosition.longValue
        smoothPosition.longValue = when {
            !isPlaying || !enabled -> reported
            kotlin.math.abs(current - reported) > 400L -> reported
            current < reported -> reported
            // Nilai interpolasi per frame dipertahankan; hanya koreksi bila menyimpang jauh.
            else -> current
        }
    }

    LaunchedEffect(isPlaying, enabled) {
        if (!isPlaying || !enabled) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // Batasi delta agar layar yang sempat tertidur tidak melompat jauh.
                val deltaMs = ((now - lastFrameNanos) / 1_000_000L).coerceIn(0L, 100L)
                lastFrameNanos = now
                smoothPosition.longValue += deltaMs
            }
        }
    }

    return smoothPosition
}

/** Membangun teks lirik dengan sorotan warna bertahap per huruf (karaoke). */
fun buildKaraokeLine(
    line: LyricLine,
    positionMs: Long,
    pendingColor: Color,
    sungColor: Color,
    pendingWeight: FontWeight,
    sungWeight: FontWeight
): AnnotatedString = buildAnnotatedString {
    if (line.words.isEmpty()) {
        withStyle(SpanStyle(color = pendingColor, fontWeight = pendingWeight)) {
            append(line.text)
        }
        return@buildAnnotatedString
    }

    line.words.forEach { word ->
        val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1L)
        val wordProgress = ((positionMs - word.startMs).toFloat() / wordDuration.toFloat()).coerceIn(0f, 1f)
        val chars = word.text

        when {
            wordProgress <= 0f -> withStyle(SpanStyle(color = pendingColor, fontWeight = pendingWeight)) {
                append(chars)
            }
            wordProgress >= 1f -> withStyle(SpanStyle(color = sungColor, fontWeight = sungWeight)) {
                append(chars)
            }
            else -> {
                val charCount = chars.length.coerceAtLeast(1)
                for (index in chars.indices) {
                    val charProgress = ((wordProgress * charCount) - index).coerceIn(0f, 1f)
                    withStyle(
                        SpanStyle(
                            color = lerp(pendingColor, sungColor, charProgress),
                            fontWeight = if (charProgress > 0f) sungWeight else pendingWeight
                        )
                    ) {
                        append(chars[index])
                    }
                }
            }
        }
    }
}

fun findActivity(context: android.content.Context): android.app.Activity? {
    var currentContext = context
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

private const val MAX_ID3_TAG_BYTES = 16 * 1024 * 1024
private const val MAX_RAW_LYRIC_SCAN_BYTES = 4 * 1024 * 1024
private const val MP3_SIZE_PLAIN = 0
private const val MP3_SIZE_SYNCSAFE = 1

private data class Mp3TagLyrics(val synced: String?, val plain: String?)

private fun sanitizeLyricText(raw: String?): String? {
    if (raw == null) return null
    val cut = raw.indexOf('\u0000')
    val base = if (cut >= 0) raw.substring(0, cut) else raw
    return base.replace("\uFEFF", "").trim().ifBlank { null }
}

private fun isPlausibleLyricText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    var printable = 0
    var letters = 0
    var total = 0
    for (char in text) {
        if (char == '\u0000') return false
        total++
        if (!char.isISOControl() || char == '\n' || char == '\r' || char == '\t') printable++
        if (char.isLetter()) letters++
    }
    return total >= 2 && printable * 100 / total >= 85 && letters > 0
}

private fun bigEndianInt32(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

private fun bigEndianInt24(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)

private fun syncSafeInt32(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)

private fun indexOfBytes(data: ByteArray, needle: ByteArray, from: Int, limit: Int = data.size): Int {
    if (needle.isEmpty()) return -1
    val last = limit - needle.size
    var index = from
    while (index <= last) {
        var matched = true
        for (n in needle.indices) {
            if (data[index + n] != needle[n]) {
                matched = false
                break
            }
        }
        if (matched) return index
        index++
    }
    return -1
}

/** Menghapus byte 0x00 yang disisipkan setelah 0xFF pada ID3 unsynchronisation. */
private fun deUnsynchronise(source: ByteArray): ByteArray {
    val out = ByteArray(source.size)
    var writeIndex = 0
    var index = 0
    while (index < source.size) {
        val byte = source[index]
        out[writeIndex++] = byte
        if ((byte.toInt() and 0xFF) == 0xFF && index + 1 < source.size && source[index + 1] == 0.toByte()) {
            index += 2
        } else {
            index++
        }
    }
    return out.copyOf(writeIndex)
}

/** Durasi satu frame MPEG audio (ms) untuk mengubah timestamp SYLT berbasis frame. */
private fun detectMpegFrameDurationMs(probe: ByteArray, length: Int): Double? {
    var index = 0
    while (index + 4 <= length) {
        val b1 = probe[index].toInt() and 0xFF
        val b2 = probe[index + 1].toInt() and 0xFF
        if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) {
            val versionBits = (b2 shr 3) and 0x03
            val layerBits = (b2 shr 1) and 0x03
            val b3 = probe[index + 2].toInt() and 0xFF
            val sampleRateIndex = (b3 shr 2) and 0x03
            val bitrateIndex = (b3 shr 4) and 0x0F
            if (versionBits != 1 && layerBits != 0 && sampleRateIndex != 3 &&
                bitrateIndex != 0 && bitrateIndex != 15
            ) {
                val sampleRate = when (versionBits) {
                    3 -> intArrayOf(44100, 48000, 32000)[sampleRateIndex]
                    2 -> intArrayOf(22050, 24000, 16000)[sampleRateIndex]
                    else -> intArrayOf(11025, 12000, 8000)[sampleRateIndex]
                }
                val samplesPerFrame = when (layerBits) {
                    3 -> 384
                    2 -> 1152
                    else -> if (versionBits == 3) 1152 else 576
                }
                return samplesPerFrame * 1000.0 / sampleRate
            }
        }
        index++
    }
    return null
}

private fun decodeLyricTagText(data: ByteArray, start: Int, end: Int, encoding: Int): String {
    if (start >= end) return ""
    val charset = when (encoding) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }
    return String(data, start, end - start, charset)
}

private fun readTerminatedLyricString(
    data: ByteArray,
    start: Int,
    limit: Int,
    encoding: Int
): Pair<String, Int> {
    val isUtf16 = encoding == 1 || encoding == 2
    var cursor = start
    while (cursor < limit) {
        if (isUtf16) {
            if (cursor + 1 >= limit) { cursor = limit; break }
            if (data[cursor] == 0.toByte() && data[cursor + 1] == 0.toByte()) {
                return decodeLyricTagText(data, start, cursor, encoding) to (cursor + 2).coerceAtMost(limit)
            }
            cursor += 2
        } else {
            if (data[cursor] == 0.toByte()) {
                return decodeLyricTagText(data, start, cursor, encoding) to (cursor + 1).coerceAtMost(limit)
            }
            cursor++
        }
    }
    return decodeLyricTagText(data, start, limit, encoding) to limit
}

private fun formatLyricTag(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L
    return String.format("%02d:%02d.%03d", totalSeconds / 60L, totalSeconds % 60L, timeMs.coerceAtLeast(0L) % 1000L)
}

/** USLT: encoding(1) + language(3) + descriptor + lyric text. */
private fun parseUsltFrame(data: ByteArray, offset: Int, size: Int): String? {
    val end = offset + size
    if (size <= 4) return null
    val encoding = data[offset].toInt() and 0xFF
    val (_, afterDescriptor) = readTerminatedLyricString(data, offset + 4, end, encoding)
    if (afterDescriptor >= end) return null
    return sanitizeLyricText(decodeLyricTagText(data, afterDescriptor, end, encoding))
}

/** TXXX: encoding(1) + description + value; dipakai tagger yang menyimpan lirik sebagai text frame. */
private fun parseTxxxFrameText(data: ByteArray, offset: Int, size: Int): String? {
    val end = offset + size
    if (size <= 2) return null
    val encoding = data[offset].toInt() and 0xFF
    val (description, afterDescriptor) = readTerminatedLyricString(data, offset + 1, end, encoding)
    val key = description.uppercase()
    if (!key.contains("LYRIC") && !key.contains("LIRIK") && !key.contains("UNSYNC")) return null
    if (afterDescriptor >= end) return null
    return sanitizeLyricText(decodeLyricTagText(data, afterDescriptor, end, encoding))
}

private fun syltEntriesToLyricText(entries: List<Pair<Long, String>>): String? {
    if (entries.isEmpty()) return null

    // Ubah entri SYLT menjadi Enhanced LRC agar memakai parser & renderer yang sama.
    // Baris baru dimulai saat ada karakter newline atau jeda antar frasa (> 900 ms).
    val builder = StringBuilder()
    var lineHasWords = false
    var lastTimeMs = -1L
    var breakAfterPrevious = false

    entries.forEach { (timeMs, rawText) ->
        if (lineHasWords && (breakAfterPrevious || timeMs - lastTimeMs > 900L)) {
            builder.append('\n')
            lineHasWords = false
        }
        val stamp = formatLyricTag(timeMs)
        if (!lineHasWords) builder.append('[').append(stamp).append(']')

        val hasLineBreak = rawText.contains('\n') || rawText.contains('\r')
        val cleaned = rawText.replace("\r", " ").replace("\n", " ").trim()
        if (cleaned.isNotEmpty()) {
            if (lineHasWords) builder.append(' ')
            builder.append('<').append(stamp).append('>').append(cleaned)
            lineHasWords = true
        }
        breakAfterPrevious = hasLineBreak
        lastTimeMs = timeMs
    }
    return builder.toString().ifBlank { null }
}

/**
 * SYLT: encoding(1) + language(3) + timestamp format(1) + content type(1) + descriptor,
 * lalu pasangan teks + timestamp 4 byte. Format 2 = milidetik, format 1 = jumlah frame
 * MPEG (dikonversi memakai durasi frame audio).
 */
private fun parseSyltFrame(
    data: ByteArray,
    offset: Int,
    size: Int,
    mpegFrameDurationMs: Double?
): String? {
    val end = offset + size
    if (size < 8) return null
    val encoding = data[offset].toInt() and 0xFF
    val timestampFormat = data[offset + 4].toInt() and 0xFF
    if (timestampFormat != 1 && timestampFormat != 2) return null
    if (timestampFormat == 1 && mpegFrameDurationMs == null) return null

    val (_, afterDescriptor) = readTerminatedLyricString(data, offset + 6, end, encoding)
    var cursor = afterDescriptor
    val entries = mutableListOf<Pair<Long, String>>()

    while (cursor < end - 1) {
        val (text, afterText) = readTerminatedLyricString(data, cursor, end, encoding)
        if (afterText + 4 > end) break
        val rawTime = bigEndianInt32(data, afterText).toLong() and 0xFFFFFFFFL
        cursor = afterText + 4
        val timeMs = if (timestampFormat == 2) {
            rawTime
        } else {
            (rawTime * (mpegFrameDurationMs ?: return null)).toLong()
        }
        if (text.isNotEmpty()) entries.add(timeMs to text)
    }
    return syltEntriesToLyricText(entries)
}

private fun readFully(stream: java.io.InputStream, buffer: ByteArray, offset: Int, count: Int): Int {
    var read = 0
    while (read < count) {
        val r = stream.read(buffer, offset + read, count - read)
        if (r <= 0) break
        read += r
    }
    return read
}

private val syncedLyricMarkerRegex = Regex("\\[\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?\\]")

private fun looksLikeSyncedLyrics(text: String?): Boolean =
    text != null && syncedLyricMarkerRegex.containsMatchIn(text)

private fun mergeMp3Lyrics(base: Mp3TagLyrics, extra: Mp3TagLyrics): Mp3TagLyrics {
    fun pick(a: String?, b: String?): String? = when {
        a.isNullOrBlank() -> b
        b.isNullOrBlank() -> a
        b.length > a.length -> b
        else -> a
    }
    return Mp3TagLyrics(pick(base.synced, extra.synced), pick(base.plain, extra.plain))
}

/**
 * Menelusuri frame ID3 secara berurutan. Mendukung v2.2 (header 6 byte, ID 3 huruf)
 * serta v2.3/v2.4 dengan ukuran syncsafe maupun ukuran biasa.
 */
private fun collectLyricsFromTag(
    tag: ByteArray,
    startOffset: Int,
    readLimit: Int,
    version: Int,
    sizeMode: Int,
    mpegFrameDurationMs: Double?
): Mp3TagLyrics {
    var synced: String? = null
    var plain: String? = null
    val headerSize = if (version == 2) 6 else 10
    val idLength = if (version == 2) 3 else 4
    var offset = startOffset

    while (offset + headerSize <= readLimit) {
        val frameId = String(tag, offset, idLength, Charsets.ISO_8859_1)
        if (frameId.isEmpty() || !frameId.all { it.isLetterOrDigit() }) break

        val frameSize = when {
            version == 2 -> bigEndianInt24(tag, offset + 3)
            sizeMode == MP3_SIZE_SYNCSAFE -> syncSafeInt32(tag, offset + 4)
            else -> bigEndianInt32(tag, offset + 4)
        }
        val dataStart = offset + headerSize
        if (frameSize <= 0 || dataStart + frameSize > readLimit) break

        when (frameId) {
            "SYLT", "SLT" -> {
                val parsed = parseSyltFrame(tag, dataStart, frameSize, mpegFrameDurationMs)
                if (parsed != null && isPlausibleLyricText(parsed) && (synced?.length ?: -1) < parsed.length) {
                    synced = parsed
                }
            }
            "USLT", "ULT" -> {
                val parsed = parseUsltFrame(tag, dataStart, frameSize)
                if (parsed != null && isPlausibleLyricText(parsed) && (plain?.length ?: -1) < parsed.length) {
                    plain = parsed
                }
            }
            "TXXX", "TXX" -> {
                val parsed = parseTxxxFrameText(tag, dataStart, frameSize)
                if (parsed != null && isPlausibleLyricText(parsed)) {
                    if (looksLikeSyncedLyrics(parsed)) {
                        if ((synced?.length ?: -1) < parsed.length) synced = parsed
                    } else if ((plain?.length ?: -1) < parsed.length) {
                        plain = parsed
                    }
                }
            }
        }
        offset = dataStart + frameSize
    }
    return Mp3TagLyrics(synced, plain)
}

/** Cadangan saat header frame rusak: cari tanda tangan ID frame di seluruh isi tag. */
private fun scanLyricFrameSignatures(
    tag: ByteArray,
    version: Int,
    mpegFrameDurationMs: Double?
): Mp3TagLyrics {
    var synced: String? = null
    var plain: String? = null
    val headerSize = if (version == 2) 6 else 10
    val ids = if (version == 2) listOf("SLT", "ULT", "TXX") else listOf("SYLT", "USLT", "TXXX")

    ids.forEach { frameId ->
        val needle = frameId.toByteArray(Charsets.ISO_8859_1)
        var searchFrom = 0
        var hits = 0
        while (hits < 24) {
            val index = indexOfBytes(tag, needle, searchFrom)
            if (index < 0) break
            hits++
            searchFrom = index + needle.size

            val dataStart = index + headerSize
            if (dataStart >= tag.size) continue
            val remaining = tag.size - dataStart
            val sizes = if (version == 2) {
                listOf(bigEndianInt24(tag, index + 3))
            } else {
                listOf(bigEndianInt32(tag, index + 4), syncSafeInt32(tag, index + 4))
            }.filter { it in 1..remaining }
            if (sizes.isEmpty()) continue

            sizes.forEach { frameSize ->
                when (frameId) {
                    "SYLT", "SLT" -> {
                        val parsed = parseSyltFrame(tag, dataStart, frameSize, mpegFrameDurationMs)
                        if (parsed != null && isPlausibleLyricText(parsed) && (synced?.length ?: -1) < parsed.length) {
                            synced = parsed
                        }
                    }
                    "USLT", "ULT" -> {
                        val parsed = parseUsltFrame(tag, dataStart, frameSize)
                        if (parsed != null && isPlausibleLyricText(parsed) && (plain?.length ?: -1) < parsed.length) {
                            plain = parsed
                        }
                    }
                    else -> {
                        val parsed = parseTxxxFrameText(tag, dataStart, frameSize)
                        if (parsed != null && isPlausibleLyricText(parsed)) {
                            if (looksLikeSyncedLyrics(parsed)) {
                                if ((synced?.length ?: -1) < parsed.length) synced = parsed
                            } else if ((plain?.length ?: -1) < parsed.length) {
                                plain = parsed
                            }
                        }
                    }
                }
            }
        }
    }
    return Mp3TagLyrics(synced, plain)
}

/**
 * Pembaca tag ID3 yang toleran: header "ID3" dicari pada 1 KB pertama, versi 2.2-2.4,
 * ukuran syncsafe maupun biasa, extended header, unsynchronisation, lalu pemindaian
 * tanda tangan frame sebagai cadangan.
 */
private fun readMp3TagLyrics(context: android.content.Context, uriStr: String): Mp3TagLyrics {
    try {
        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
            val probe = ByteArray(64 * 1024)
            val probeRead = readFully(stream, probe, 0, probe.size)
            if (probeRead < 10) return Mp3TagLyrics(null, null)

            val headerPos = indexOfBytes(
                probe,
                byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte()),
                0,
                minOf(probeRead, 1024)
            )
            if (headerPos < 0 || headerPos + 10 > probeRead) return Mp3TagLyrics(null, null)

            val version = probe[headerPos + 3].toInt() and 0xFF
            if (version !in 2..4) return Mp3TagLyrics(null, null)
            val flags = probe[headerPos + 5].toInt() and 0xFF
            val tagSize = syncSafeInt32(probe, headerPos + 6)
            if (tagSize <= 10 || tagSize > MAX_ID3_TAG_BYTES) return Mp3TagLyrics(null, null)

            val tag = ByteArray(tagSize)
            val dataStart = headerPos + 10
            val fromProbe = minOf(tagSize, (probeRead - dataStart).coerceAtLeast(0))
            if (fromProbe > 0) probe.copyInto(tag, 0, dataStart, dataStart + fromProbe)
            val tagRead = fromProbe + readFully(stream, tag, fromProbe, tagSize - fromProbe)
            if (tagRead < 12) return Mp3TagLyrics(null, null)

            val raw = tag.copyOf(tagRead)
            val isUnsynchronised = (flags and 0x80) != 0
            val body = if (isUnsynchronised) deUnsynchronise(raw) else raw

            var bodyStart = 0
            if ((flags and 0x40) != 0 && body.size >= 4) {
                bodyStart = when (version) {
                    // ID3v2.4: ukuran extended header sudah termasuk medan ukurannya.
                    4 -> syncSafeInt32(body, 0).coerceIn(0, body.size)
                    // ID3v2.3: ukuran belum termasuk 4 byte medan ukuran itu sendiri.
                    3 -> (bigEndianInt32(body, 0) + 4).coerceIn(0, body.size)
                    else -> 0
                }
            }

            // Deteksi durasi frame MPEG untuk SYLT yang memakai satuan frame (format 1).
            val audioProbe = ByteArray(16 * 1024)
            val audioStart = dataStart + tagSize
            var audioRead = 0
            if (audioStart < probeRead) {
                audioRead = minOf(audioProbe.size, probeRead - audioStart)
                probe.copyInto(audioProbe, 0, audioStart, audioStart + audioRead)
            }
            audioRead += readFully(stream, audioProbe, audioRead, audioProbe.size - audioRead)
            val mpegFrameDurationMs = detectMpegFrameDurationMs(audioProbe, audioRead)

            val sizeModes = if (version == 4) {
                intArrayOf(MP3_SIZE_SYNCSAFE, MP3_SIZE_PLAIN)
            } else {
                intArrayOf(MP3_SIZE_PLAIN, MP3_SIZE_SYNCSAFE)
            }

            var result = Mp3TagLyrics(null, null)
            sizeModes.forEach { mode ->
                result = mergeMp3Lyrics(
                    result,
                    collectLyricsFromTag(body, bodyStart, body.size, version, mode, mpegFrameDurationMs)
                )
            }
            result = mergeMp3Lyrics(result, scanLyricFrameSignatures(body, version, mpegFrameDurationMs))

            if (isUnsynchronised && result.synced == null) {
                // Sebagian file menyalakan flag unsync tanpa benar-benar menyisipkan 0x00.
                result = mergeMp3Lyrics(
                    result,
                    collectLyricsFromTag(raw, bodyStart, raw.size, version, sizeModes[0], mpegFrameDurationMs)
                )
            }
            return result
        }
    } catch (e: Exception) {
        // ignore
    }
    return Mp3TagLyrics(null, null)
}

private fun normalizeNameForLyrics(name: String): String =
    name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

private fun readTextFromUri(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        stream.bufferedReader().use { it.readText() }.ifBlank { null }
    }
} catch (e: Exception) {
    null
}

/** MP4/M4A: atom ©lyr berisi sub-atom "data" dengan teks lirik. */
private fun findMp4LyricAtom(data: ByteArray, length: Int): String? {
    val needle = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
    var searchFrom = 0
    var hits = 0
    while (hits < 8) {
        val index = indexOfBytes(data, needle, searchFrom, length)
        if (index < 0) break
        hits++
        searchFrom = index + needle.size

        val dataAtom = index + 4
        if (dataAtom + 16 > length) continue
        if (String(data, dataAtom + 4, 4, Charsets.ISO_8859_1) != "data") continue

        val dataSize = bigEndianInt32(data, dataAtom)
        val dataType = bigEndianInt32(data, dataAtom + 8)
        val payloadStart = dataAtom + 16
        val payloadEnd = minOf(
            length,
            if (dataSize in 17..(length - dataAtom)) dataAtom + dataSize else payloadStart + 8192
        )
        if (payloadEnd <= payloadStart) continue

        val text = if (dataType == 2) {
            String(data, payloadStart, payloadEnd - payloadStart, Charsets.UTF_16BE)
        } else {
            String(data, payloadStart, payloadEnd - payloadStart, Charsets.UTF_8)
        }
        val sanitized = sanitizeLyricText(text) ?: continue
        if (isPlausibleLyricText(sanitized) && sanitized.length >= 8) return sanitized
    }
    return null
}

/** Ogg/Opus/FLAC: komentar Vorbis "LYRICS=" atau "UNSYNCEDLYRICS=". */
private fun findVorbisLyricComment(data: ByteArray, length: Int): String? {
    val keys = listOf("LYRICS=", "UNSYNCEDLYRICS=", "UNSYNCED LYRICS=")
    keys.forEach { key ->
        val needle = key.toByteArray(Charsets.ISO_8859_1)
        var searchFrom = 0
        var hits = 0
        while (hits < 4) {
            val index = indexOfBytes(data, needle, searchFrom, length)
            if (index < 0) break
            hits++
            val valueStart = index + needle.size
            searchFrom = valueStart
            var valueEnd = valueStart
            while (valueEnd < length && valueEnd - valueStart < 128 * 1024) {
                val b = data[valueEnd].toInt() and 0xFF
                if (b == 0 || b < 0x09) break
                valueEnd++
            }
            val text = sanitizeLyricText(String(data, valueStart, valueEnd - valueStart, Charsets.UTF_8)) ?: return@forEach
            if (isPlausibleLyricText(text) && text.length >= 8) return text
        }
    }
    return null
}

private fun scanRawMediaForLyrics(context: android.content.Context, uriStr: String): String? {
    try {
        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
            val buffer = ByteArray(MAX_RAW_LYRIC_SCAN_BYTES)
            val read = readFully(stream, buffer, 0, buffer.size)
            if (read < 32) return null
            findMp4LyricAtom(buffer, read)?.let { return it }
            findVorbisLyricComment(buffer, read)?.let { return it }
        }
    } catch (e: Exception) {
        // ignore
    }
    return null
}

fun getEmbeddedLyrics(context: android.content.Context, uriStr: String): String? {
    // 1. File .lrc di folder yang sama (nama sama, lalu nama yang mirip)
    try {
        val uri = Uri.parse(uriStr)
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val parent = docFile?.parentFile
        val rawName = docFile?.name ?: uri.lastPathSegment?.substringAfterLast("/") ?: ""
        val baseName = rawName.substringBeforeLast(".", rawName)
        val normalizedBase = normalizeNameForLyrics(baseName)
        if (parent != null && normalizedBase.isNotEmpty()) {
            val lrcFiles = try {
                parent.listFiles().filter { file ->
                    file.isFile &&
                        file.name?.substringAfterLast(".", "")?.equals("lrc", ignoreCase = true) == true
                }
            } catch (e: Exception) {
                emptyList()
            }
            val siblingLrc = lrcFiles.firstOrNull {
                normalizeNameForLyrics(it.name?.substringBeforeLast(".", "") ?: "") == normalizedBase
            } ?: lrcFiles.firstOrNull {
                val normalized = normalizeNameForLyrics(it.name?.substringBeforeLast(".", "") ?: "")
                normalized.isNotEmpty() &&
                    (normalized.contains(normalizedBase) || normalizedBase.contains(normalized))
            } ?: parent.findFile("$baseName.lrc")
                ?: parent.findFile("${baseName.lowercase()}.lrc")

            if (siblingLrc != null && siblingLrc.exists() && siblingLrc.isFile) {
                readTextFromUri(context, siblingLrc.uri)?.let { return it }
            }
        }
    } catch (e: Exception) {
        // ignore
    }

    // 2. Tag ID3: SYLT (lirik tersinkron per kata) lebih diprioritaskan daripada USLT
    val tagged = readMp3TagLyrics(context, uriStr)
    if (!tagged.synced.isNullOrBlank()) return tagged.synced

    // 3. MediaMetadataRetriever (MP4/FLAC/format lain yang menyimpan lirik)
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, Uri.parse(uriStr))
        val lyrics = retriever.extractMetadata(1000)
        if (!lyrics.isNullOrBlank()) return lyrics
    } catch (e: Exception) {
        // ignore
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }

    // 4. Pemindaian mentah untuk atom ©lyr (MP4/M4A) dan komentar Vorbis (OGG/FLAC)
    scanRawMediaForLyrics(context, uriStr)?.let { return it }

    // 5. USLT/TXXX (lirik tanpa timestamp) sebagai upaya terakhir
    if (!tagged.plain.isNullOrBlank()) return tagged.plain
    return null
}

@Composable
fun FullScreenLyricsView(
    activeTrack: MusicTrack,
    viewModel: MainViewModel,
    fileLyricsText: String,
    manualLyricsText: String,
    onDismiss: () -> Unit
) {
    val activeLyricsText = manualLyricsText.ifBlank { fileLyricsText }
    val currentLyrics = remember(activeTrack, activeLyricsText, viewModel.musicTotalDuration) {
        if (activeLyricsText.isBlank()) emptyList()
        else parseLyrics(activeLyricsText, viewModel.musicTotalDuration.toLong())
    }

    val smoothPosition by rememberSmoothMusicPosition(
        viewModel.musicPlaybackPosition,
        viewModel.isMusicPlaying
    )

    val activeLyricIndex = remember(currentLyrics, viewModel.musicPlaybackPosition) {
        val pos = viewModel.musicPlaybackPosition.toLong()
        val index = currentLyrics.indexOfLast { it.timeMs <= pos }
        if (index == -1 && currentLyrics.isNotEmpty()) 0 else index
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex >= 0 && activeLyricIndex < currentLyrics.size) {
            lazyListState.animateScrollToItem(activeLyricIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Immersive clean song header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = activeTrack.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = activeTrack.artist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (currentLyrics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Lagu ini tidak memiliki lirik tertanam maupun lirik manual.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ketuk layar untuk kembali.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 140.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(currentLyrics) { index, line ->
                            val isActive = index == activeLyricIndex
                            val scale by animateFloatAsState(
                                targetValue = if (isActive) 1.25f else 0.85f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "scale"
                            )
                            val color by animateColorAsState(
                                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                label = "color"
                            )

                            val textContent = if (isActive) {
                                buildKaraokeLine(
                                    line = line,
                                    positionMs = smoothPosition,
                                    pendingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    sungColor = MaterialTheme.colorScheme.primary,
                                    pendingWeight = FontWeight.Bold,
                                    sungWeight = FontWeight.ExtraBold
                                )
                            } else {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)) {
                                        append(line.text)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 24.dp)
                                    .clickable {
                                        viewModel.seekTo(line.timeMs.toInt())
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = textContent,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 26.sp,
                                    modifier = Modifier.graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale
                                    )
                                )
                            }
                        }
                    }

                    // Fading edges
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                }
            }

            // Tutor hint / Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Lirik Fullscreen",
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Tutup Lirik", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
