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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
    var lyricTabSelected by remember { mutableStateOf(1) } // Default to 1 (Input Manual), removing 0 (Lirik File)
    var manualLyricsText by remember { mutableStateOf("") }
    var isLyricsFullScreen by remember { mutableStateOf(false) }
    var fileLyricsText by remember { mutableStateOf("") }

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
            fileLyricsText = withContext(Dispatchers.IO) {
                getEmbeddedLyrics(context, activeTrack.uriStr) ?: ""
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
                                val currentLyrics = remember(activeTrack, lyricTabSelected, manualLyricsText, viewModel.musicTotalDuration) {
                                    if (activeTrack == null) emptyList()
                                    else {
                                        if (lyricTabSelected == 0) {
                                            if (fileLyricsText.isBlank()) emptyList() else parseLyrics(fileLyricsText, viewModel.musicTotalDuration.toLong())
                                        } else {
                                            if (manualLyricsText.isBlank()) {
                                                emptyList()
                                            } else {
                                                parseLyrics(manualLyricsText, viewModel.musicTotalDuration.toLong())
                                            }
                                        }
                                    }
                                }

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
                                            // Local state for cursor tracking & text input
                                            var editorValue by remember(manualLyricsText) {
                                                mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(manualLyricsText))
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
                                                        Text("Sisipkan Waktu", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // 2. Load LRC Template
                                                    OutlinedButton(
                                                        onClick = {
                                                            val template = "[ti:Contoh Lagu]\n" +
                                                                    "[ar:Artis Hebat]\n" +
                                                                    "[00:00.00] ~ Intro Instrumental ~\n" +
                                                                    "[00:08.50] \n" +
                                                                    "[00:10.00] Ini adalah contoh baris lirik pertama\n" +
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
                                                            "Tulis/tempel lirik di sini. Putar lagu, ketuk kursor pada baris lirik lalu ketuk 'Sisipkan Waktu' untuk merekam ketukan lagu secara real-time!",
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
                                                            text = "Lirik Lagu",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (lyricTabSelected == 1) {
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
                                                        }
                                                        
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
                                                            imageVector = if (lyricTabSelected == 0) Icons.Default.Lyrics else Icons.Default.BorderColor,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            modifier = Modifier.size(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = if (lyricTabSelected == 0) "Tidak ada lirik tertanam" else "Belum ada lirik manual",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = if (lyricTabSelected == 0) "Gunakan tab 'Input Manual' jika ingin menambahkan lirik secara manual." else "Ketuk ikon pensil di atas untuk menulis/menempelkan lirik Anda.",
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
                                                                    val words = remember(line.text) { line.text.split("\\s+".toRegex()).filter { it.isNotEmpty() } }
                                                                    val lineDuration = remember(currentLyrics, index, viewModel.musicTotalDuration) {
                                                                        if (index < currentLyrics.size - 1) {
                                                                            currentLyrics[index + 1].timeMs - line.timeMs
                                                                        } else {
                                                                            (viewModel.musicTotalDuration.toLong() - line.timeMs).coerceAtLeast(3000L)
                                                                        }
                                                                    }
                                                                    val elapsed = (viewModel.musicPlaybackPosition.toLong() - line.timeMs).coerceIn(0L, lineDuration)
                                                                    val elapsedRatio = if (lineDuration > 0) elapsed.toFloat() / lineDuration.toFloat() else 1f
                                                                    val wordCount = words.size

                                                                    buildAnnotatedString {
                                                                        words.forEachIndexed { i, word ->
                                                                            val wordStartProgress = i.toFloat() / wordCount
                                                                            val wordEndProgress = (i + 1).toFloat() / wordCount

                                                                            val isWordSung = elapsedRatio >= wordEndProgress
                                                                            val isWordActive = elapsedRatio >= wordStartProgress && elapsedRatio < wordEndProgress

                                                                            val wordColor = if (isWordSung) {
                                                                                MaterialTheme.colorScheme.primary
                                                                            } else if (isWordActive) {
                                                                                MaterialTheme.colorScheme.secondary
                                                                            } else {
                                                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                            }

                                                                            val wordWeight = if (isWordSung || isWordActive) {
                                                                                FontWeight.ExtraBold
                                                                            } else {
                                                                                FontWeight.Normal
                                                                            }

                                                                            withStyle(SpanStyle(color = wordColor, fontWeight = wordWeight)) {
                                                                                append(word)
                                                                            }
                                                                            if (i < words.size - 1) {
                                                                                append(" ")
                                                                            }
                                                                        }
                                                                    }
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
                        lyricTabSelected = lyricTabSelected,
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

data class LyricLine(val timeMs: Long, val text: String)

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

fun parseLyrics(manualText: String, durationMs: Long): List<LyricLine> {
    if (manualText.isBlank()) return emptyList()
    
    val lines = manualText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    
    // Support standard [mm:ss.xx] or [mm:ss:xx] or [mm:ss] format, allowing multiple timestamps on one line
    val singleTagRegex = Regex("\\[(\\d+):(\\d+)(?:[:.](\\d+))?\\]")
    var hasTimestamps = false
    
    val parsed = mutableListOf<LyricLine>()
    
    lines.forEach { line ->
        val matches = singleTagRegex.findAll(line).toList()
        if (matches.isNotEmpty()) {
            hasTimestamps = true
            // Extract the lyric text (everything that remains after removing all timestamps)
            var lyricText = line
            matches.forEach { match ->
                lyricText = lyricText.replace(match.value, "")
            }
            lyricText = lyricText.trim()
            
            // Add a separate entry for each timestamp found on this line (supports duplication/repetition)
            matches.forEach { match ->
                val min = match.groups[1]?.value?.toLongOrNull() ?: 0L
                val sec = match.groups[2]?.value?.toLongOrNull() ?: 0L
                val msStr = match.groups[3]?.value ?: "0"
                
                // Convert millisecond part accurately (e.g., .4 -> 400ms, .45 -> 450ms, .456 -> 456ms)
                val msPart = when (msStr.length) {
                    1 -> msStr.toLongOrNull()?.let { it * 100 } ?: 0L
                    2 -> msStr.toLongOrNull()?.let { it * 10 } ?: 0L
                    else -> msStr.take(3).toLongOrNull() ?: 0L
                }
                
                val timeMs = (min * 60 + sec) * 1000L + msPart
                parsed.add(LyricLine(timeMs, lyricText))
            }
        } else {
            // Check if it's a metadata header e.g. [ar:Artist] - if so, ignore it. Otherwise, add as normal text.
            if (!line.startsWith("[") || !line.contains(":")) {
                val lastTime = parsed.lastOrNull()?.timeMs ?: 0L
                parsed.add(LyricLine(lastTime + 1000L, line))
            }
        }
    }
    
    if (hasTimestamps) {
        return parsed.sortedBy { it.timeMs }
    } else {
        val duration = if (durationMs > 0) durationMs else 180000L
        val interval = duration / (lines.size.coerceAtLeast(1))
        return lines.mapIndexed { index, text ->
            LyricLine(
                timeMs = index * interval,
                text = text
            )
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

fun getEmbeddedLyrics(context: android.content.Context, uriStr: String): String? {
    // 1. Try finding a matching .lrc file in the same folder as the MP3
    try {
        val uri = Uri.parse(uriStr)
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val parent = docFile?.parentFile
        if (parent != null) {
            val baseName = docFile.name?.substringBeforeLast(".") ?: ""
            if (baseName.isNotEmpty()) {
                val lrcFile = parent.findFile("$baseName.lrc") ?: parent.findFile("${baseName.lowercase()}.lrc") ?: parent.findFile("${baseName.uppercase()}.lrc")
                if (lrcFile != null && lrcFile.exists() && lrcFile.isFile) {
                    context.contentResolver.openInputStream(lrcFile.uri)?.use { stream ->
                        val text = stream.bufferedReader().use { it.readText() }
                        if (text.isNotBlank()) {
                            return text
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        // ignore
    }

    // 2. Try MediaMetadataRetriever (API 31+)
    val retriever = MediaMetadataRetriever()
    try {
        val uri = Uri.parse(uriStr)
        retriever.setDataSource(context, uri)
        
        val lyrics = retriever.extractMetadata(1000)
        if (!lyrics.isNullOrBlank()) {
            return lyrics
        }
    } catch (e: Exception) {
        // ignore
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }

    // 3. Fallback: Parse USLT frame from MP3 file bytes directly
    try {
        val uri = Uri.parse(uriStr)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(512 * 1024)
            val bytesRead = inputStream.read(buffer)
            if (bytesRead > 10) {
                if (buffer[0] == 'I'.toByte() && buffer[1] == 'D'.toByte() && buffer[2] == '3'.toByte()) {
                    var index = 10
                    while (index < bytesRead - 10) {
                        if (buffer[index] == 'U'.toByte() && 
                            buffer[index + 1] == 'S'.toByte() && 
                            buffer[index + 2] == 'L'.toByte() && 
                            buffer[index + 3] == 'T'.toByte()
                        ) {
                            val b1 = buffer[index + 4].toInt() and 0xFF
                            val b2 = buffer[index + 5].toInt() and 0xFF
                            val b3 = buffer[index + 6].toInt() and 0xFF
                            val b4 = buffer[index + 7].toInt() and 0xFF
                            
                            var frameSize = (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
                            val synchsafeSize = ((b1 and 0x7F) shl 21) or ((b2 and 0x7F) shl 14) or ((b3 and 0x7F) shl 7) or (b4 and 0x7F)
                            
                            if (frameSize > 100000 || frameSize <= 0) {
                                frameSize = synchsafeSize
                            }
                            
                            if (frameSize > 0 && frameSize < 100000 && index + 10 + frameSize <= bytesRead) {
                                val textEncoding = buffer[index + 10].toInt() and 0xFF
                                var payloadOffset = index + 14
                                
                                if (textEncoding == 1 || textEncoding == 2) {
                                    while (payloadOffset < index + 10 + frameSize - 1 && 
                                           !(buffer[payloadOffset] == 0.toByte() && buffer[payloadOffset + 1] == 0.toByte())) {
                                        payloadOffset += 2
                                    }
                                    payloadOffset += 2
                                } else {
                                    while (payloadOffset < index + 10 + frameSize && buffer[payloadOffset] != 0.toByte()) {
                                        payloadOffset++
                                    }
                                    payloadOffset++
                                }
                                
                                val textBytesLength = (index + 10 + frameSize) - payloadOffset
                                if (textBytesLength > 0) {
                                    val charset = when (textEncoding) {
                                        1 -> Charsets.UTF_16
                                        2 -> Charsets.UTF_16BE
                                        3 -> Charsets.UTF_8
                                        else -> Charsets.ISO_8859_1
                                    }
                                    val rawText = String(buffer, payloadOffset, textBytesLength, charset).trim()
                                    if (rawText.isNotBlank()) {
                                        return rawText
                                    }
                                }
                            }
                        }
                        index++
                    }
                }
            }
        }
    } catch (e: Exception) {
        // ignore
    }
    return null
}

@Composable
fun FullScreenLyricsView(
    activeTrack: MusicTrack,
    viewModel: MainViewModel,
    fileLyricsText: String,
    manualLyricsText: String,
    lyricTabSelected: Int,
    onDismiss: () -> Unit
) {
    val currentLyrics = remember(activeTrack, lyricTabSelected, fileLyricsText, manualLyricsText, viewModel.musicTotalDuration) {
        if (lyricTabSelected == 0) {
            if (fileLyricsText.isBlank()) emptyList()
            else parseLyrics(fileLyricsText, viewModel.musicTotalDuration.toLong())
        } else {
            if (manualLyricsText.isBlank()) emptyList()
            else parseLyrics(manualLyricsText, viewModel.musicTotalDuration.toLong())
        }
    }

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
                            text = if (lyricTabSelected == 0) "Lagu ini tidak memiliki lirik tertanam." else "Belum ada lirik manual.",
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
                                val words = remember(line.text) { line.text.split("\\s+".toRegex()).filter { it.isNotEmpty() } }
                                val lineDuration = remember(currentLyrics, index, viewModel.musicTotalDuration) {
                                    if (index < currentLyrics.size - 1) {
                                        currentLyrics[index + 1].timeMs - line.timeMs
                                    } else {
                                        (viewModel.musicTotalDuration.toLong() - line.timeMs).coerceAtLeast(3000L)
                                    }
                                }
                                val elapsed = (viewModel.musicPlaybackPosition.toLong() - line.timeMs).coerceIn(0L, lineDuration)
                                val elapsedRatio = if (lineDuration > 0) elapsed.toFloat() / lineDuration.toFloat() else 1f
                                val wordCount = words.size

                                buildAnnotatedString {
                                    words.forEachIndexed { i, word ->
                                        val wordStartProgress = i.toFloat() / wordCount
                                        val wordEndProgress = (i + 1).toFloat() / wordCount

                                        val isWordSung = elapsedRatio >= wordEndProgress
                                        val isWordActive = elapsedRatio >= wordStartProgress && elapsedRatio < wordEndProgress

                                        val wordColor = if (isWordSung) {
                                            MaterialTheme.colorScheme.primary
                                        } else if (isWordActive) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        }

                                        val wordWeight = if (isWordSung || isWordActive) {
                                            FontWeight.ExtraBold
                                        } else {
                                            FontWeight.Bold
                                        }

                                        withStyle(SpanStyle(color = wordColor, fontWeight = wordWeight)) {
                                            append(word)
                                        }
                                        if (i < words.size - 1) {
                                            append(" ")
                                        }
                                    }
                                }
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
