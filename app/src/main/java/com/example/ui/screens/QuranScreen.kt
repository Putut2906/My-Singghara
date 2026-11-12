package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var readerSurahId by remember { mutableStateOf<Int?>(null) }
    var arabicFontSize by remember { mutableStateOf(26) }
    var activeTafsirVerseNumber by remember { mutableStateOf<Int?>(null) }
    var showTajwidGuideDialog by remember { mutableStateOf(false) }
    var showSelectQariDialog by remember { mutableStateOf(false) }
    var showSurahDetailDialog by remember { mutableStateOf(false) }

    val bookmarks by viewModel.quranBookmarks.collectAsStateWithLifecycle()
    val history by viewModel.quranHistory.collectAsStateWithLifecycle()

    if (readerSurahId != null) {
        val surahId = readerSurahId!!
        val surah = QuranData.surahs.firstOrNull { it.id == surahId } ?: return
        
        val remoteVersesMap by viewModel.remoteQuranVerses.collectAsStateWithLifecycle()
        val isRemoteLoading = viewModel.isQuranLoadingRemote
        
        LaunchedEffect(surahId) {
            viewModel.fetchRemoteQuranVerses(surahId)
        }
        
        val verses = remember(surahId, remoteVersesMap) {
            remoteVersesMap[surahId] ?: QuranData.getVersesForSurah(surahId)
        }
        var showReaderSettingsMenu by remember { mutableStateOf(false) }
        var showVerseSearch by remember { mutableStateOf(false) }
        var verseSearchQuery by remember { mutableStateOf("") }

        val filteredVerses = remember(verses, isRemoteLoading, remoteVersesMap, verseSearchQuery) {
            val baseList = if (isRemoteLoading && !remoteVersesMap.containsKey(surahId)) {
                emptyList()
            } else {
                verses
            }
            if (verseSearchQuery.isBlank()) {
                baseList
            } else {
                val q = verseSearchQuery.trim().lowercase(Locale.getDefault())
                baseList.filter { verse ->
                    verse.verseNumber.toString() == q ||
                    verse.translation.lowercase(Locale.getDefault()).contains(q) ||
                    verse.textArabic.contains(q)
                }
            }
        }

        BackHandler {
            if (readerSurahId != null) {
                viewModel.stopQuranRecitation()
                readerSurahId = null
            } else {
                viewModel.stopQuranRecitation()
                viewModel.currentRoute = "home"
                viewModel.showAllAppsPage = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                // Header Surah Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                )
                                .padding(20.dp)
                                .fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 28.dp)
                            ) {
                                // Title Surah diklik untuk pop up detail surah
                                Surface(
                                    onClick = { showSurahDetailDialog = true },
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Surah ${surah.nameIndonesian}",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 20.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Detail Surah",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Kolom Pencarian Ayat (muncul saat ikon pencarian diklik)
                                AnimatedVisibility(
                                    visible = showVerseSearch || verseSearchQuery.isNotEmpty(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    OutlinedTextField(
                                        value = verseSearchQuery,
                                        onValueChange = { verseSearchQuery = it },
                                        placeholder = {
                                            Text(
                                                text = "Cari nomor ayat atau kata dalam Surah ${surah.nameIndonesian}...",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Cari Ayat",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            if (verseSearchQuery.isNotEmpty()) {
                                                IconButton(onClick = { verseSearchQuery = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Hapus Pencarian",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }
                            }

                            // Icon Zoom In, Zoom Out, Search & Titik 3 di Pojok Kanan Atas Kartu
                            Row(
                                modifier = Modifier.align(Alignment.TopEnd),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        arabicFontSize = (arabicFontSize + 2).coerceAtMost(42)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Perbesar Font Arab",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        arabicFontSize = (arabicFontSize - 2).coerceAtLeast(18)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = "Perkecil Font Arab",
                                        tint = Color.White
                                    )
                                }

                                // Icon Kaca Pembesar (Search) di sebelah kiri icon titik 3
                                IconButton(
                                    onClick = {
                                        showVerseSearch = !showVerseSearch
                                        if (!showVerseSearch) {
                                            verseSearchQuery = ""
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Cari Ayat",
                                        tint = Color.White
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showReaderSettingsMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu Pengaturan",
                                            tint = Color.White
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showReaderSettingsMenu,
                                        onDismissRequest = { showReaderSettingsMenu = false },
                                        modifier = Modifier
                                            .width(260.dp)
                                            .padding(vertical = 4.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.RecordVoiceOver,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(
                                                            text = "Pilih Qari & Murattal",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Qari: ${viewModel.activeQari}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showReaderSettingsMenu = false
                                                showSelectQariDialog = true
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = "Panduan Tajwid Warna",
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showReaderSettingsMenu = false
                                                showTajwidGuideDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isRemoteLoading && !remoteVersesMap.containsKey(surahId)) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Menyelaraskan dengan mushaf Al-Qur'an fisik...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Mengunduh teks terjemahan & tafsir Kemenag RI",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Basmalah (If not Al-Fatihah and not At-Taubah)
                    if (surahId != 1 && surahId != 9) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = "بِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    items(filteredVerses) { verse ->
                        val isBookmarked = bookmarks.any { it.surahId == surahId && it.verseNumber == verse.verseNumber }
                        val isPlayingThisVerse = viewModel.isQuranPlaying && viewModel.currentQuranVerseNumberForAudio == verse.verseNumber

                        // Verse Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPlayingThisVerse) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPlayingThisVerse) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Verse control header row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Verse Badge Number
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = verse.verseNumber.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Action buttons (Audio, Bookmark, Tafsir)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Play individual verse
                                        IconButton(
                                            onClick = {
                                                viewModel.addQuranHistory(surahId, surah.nameIndonesian, verse.verseNumber)
                                                if (isPlayingThisVerse) {
                                                    viewModel.stopQuranRecitation()
                                                } else {
                                                    viewModel.playQuranRecitation(context, surahId, verse.verseNumber)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThisVerse) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                                                contentDescription = "Dengarkan Ayat",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Bookmark
                                        IconButton(
                                            onClick = {
                                                viewModel.toggleQuranBookmark(surahId, surah.nameIndonesian, verse.verseNumber)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Simpan Bookmark",
                                                tint = if (isBookmarked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Tafsir Toggle button
                                        TextButton(
                                            onClick = {
                                                activeTafsirVerseNumber = if (activeTafsirVerseNumber == verse.verseNumber) null else verse.verseNumber
                                            },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = if (activeTafsirVerseNumber == verse.verseNumber) "Tutup Tafsir" else "Tafsir",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Arabic Text (With Tajwid Colors!)
                                val annotatedArabic = remember(verse.textArabic) {
                                    buildTajwidColoredString(verse.textArabic)
                                }
                                Text(
                                    text = annotatedArabic,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = arabicFontSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    textAlign = TextAlign.End,
                                    lineHeight = (arabicFontSize * 1.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Indonesian Translation
                                Text(
                                    text = verse.translation,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )

                                // Collapsible Tafsir content
                                AnimatedVisibility(
                                    visible = activeTafsirVerseNumber == verse.verseNumber,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Tafsir Ringkas Kemenag RI:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = verse.tafsir,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    } else {
        // Al-Qur'an Index View with 3 Tab Screens
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Tabs bar (Surah, Juz, Bookmark & History)
                TabRow(
                    selectedTabIndex = viewModel.selectedQuranTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Tab(
                        selected = viewModel.selectedQuranTab == 0,
                        onClick = { viewModel.selectedQuranTab = 0 },
                        text = { Text("Surah", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = viewModel.selectedQuranTab == 1,
                        onClick = { viewModel.selectedQuranTab = 1 },
                        text = { Text("Juz", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = viewModel.selectedQuranTab == 2,
                        onClick = { viewModel.selectedQuranTab = 2 },
                        text = { Text("Bookmarks", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }

                // Seamless search bar without container box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Cari",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                    )

                    TextField(
                        value = viewModel.quranSearchQuery,
                        onValueChange = { viewModel.quranSearchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Cari surah atau juz...", fontSize = 14.sp) },
                        trailingIcon = {
                            if (viewModel.quranSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.quranSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tab Content Switcher
                Box(modifier = Modifier.weight(1f)) {
                    when (viewModel.selectedQuranTab) {
                        0 -> {
                            // SURAH TAB VIEW
                            val filteredSurahs = remember(viewModel.quranSearchQuery) {
                                if (viewModel.quranSearchQuery.isEmpty()) QuranData.surahs
                                else QuranData.surahs.filter {
                                    it.nameIndonesian.contains(viewModel.quranSearchQuery, ignoreCase = true) ||
                                            it.nameArabic.contains(viewModel.quranSearchQuery) ||
                                            it.id.toString() == viewModel.quranSearchQuery
                                }
                            }

                            if (filteredSurahs.isEmpty()) {
                                EmptyStateView(text = "Surah tidak ditemukan.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    items(filteredSurahs) { surah ->
                                        SurahListItem(surah = surah) {
                                            viewModel.addQuranHistory(surah.id, surah.nameIndonesian, 1)
                                            readerSurahId = surah.id
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // JUZ TAB VIEW
                            val filteredJuzs = remember(viewModel.quranSearchQuery) {
                                if (viewModel.quranSearchQuery.isEmpty()) QuranData.juzs
                                else QuranData.juzs.filter {
                                    it.number.toString() == viewModel.quranSearchQuery ||
                                            it.startSurahName.contains(viewModel.quranSearchQuery, ignoreCase = true)
                                }
                            }

                            if (filteredJuzs.isEmpty()) {
                                EmptyStateView(text = "Juz tidak ditemukan.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    items(filteredJuzs) { juz ->
                                        JuzListItem(juz = juz) {
                                            viewModel.addQuranHistory(juz.startSurahId, juz.startSurahName, juz.startVerse)
                                            readerSurahId = juz.startSurahId
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // BOOKMARKS & HISTORY TAB VIEW
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (bookmarks.isEmpty() && history.isEmpty()) {
                                    EmptyStateView(text = "Belum ada bookmark atau riwayat bacaan.")
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        // History Section
                                        if (history.isNotEmpty()) {
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Riwayat Bacaan Terakhir",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    TextButton(
                                                        onClick = { viewModel.clearQuranHistory() },
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text("Hapus Riwayat", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }

                                            items(history.take(10)) { hist ->
                                                HistoryListItem(hist = hist) {
                                                    readerSurahId = hist.surahId
                                                }
                                            }
                                        }

                                        // Bookmarks Section
                                        if (bookmarks.isNotEmpty()) {
                                            item {
                                                Text(
                                                    text = "Daftar Penanda Buku",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(vertical = 12.dp)
                                                )
                                            }

                                            items(bookmarks) { bmk ->
                                                BookmarkListItem(bookmark = bmk, onDelete = {
                                                    viewModel.toggleQuranBookmark(bmk.surahId, bmk.surahName, bmk.verseNumber)
                                                }) {
                                                    readerSurahId = bmk.surahId
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
        }
    }

    if (showTajwidGuideDialog) {
        AlertDialog(
            onDismissRequest = { showTajwidGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Panduan Tajwid Warna",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Penjelasan kode warna tajwid pada bacaan Al-Qur'an:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Ghunnah
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(12.dp)
                                    .background(Color(0xFFE91E63), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Ghunnah (Dengung)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Dengung pada huruf Nun (ن) atau Mim (م) bertasydid.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Qalqalah
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(12.dp)
                                    .background(Color(0xFF1E88E5), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Qalqalah (Memantul)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Memantul pada huruf (ق, ط, ب, ج, د) saat sukun atau waqaf.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Ikhfa / Idgham
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(12.dp)
                                    .background(Color(0xFFEF6C00), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Ikhfa / Idgham (Samar / Dengung)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Samar atau memasukkan bacaan Nun sukun (نْ) / Tanwin.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Mad
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(12.dp)
                                    .background(Color(0xFF2E7D32), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Mad (Panjang)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Memanjangkan bacaan huruf mad (2, 4, atau 6 harakat).",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTajwidGuideDialog = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSelectQariDialog && readerSurahId != null) {
        val surah = QuranData.surahs.firstOrNull { it.id == readerSurahId }
        AlertDialog(
            onDismissRequest = { showSelectQariDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Pilih Qari & Murattal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Putar Murattal Lengkap
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Murattal Surah Lengkap",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (surah != null) "Surah ${surah.nameIndonesian}" else "Al-Qur'an",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (viewModel.isQuranPlaying) {
                                        viewModel.pauseQuranRecitation()
                                    } else {
                                        viewModel.playQuranRecitation(context, readerSurahId!!)
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isQuranPlaying && viewModel.currentQuranVerseNumberForAudio == null)
                                        Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause Murattal",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Section 2: List Pilihan Qari
                    Text(
                        text = "DAFTAR QARI (PEMBERI SUARA)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val qaris = listOf(
                        "Mishary Alafasy",
                        "Abdul Rahman Al-Sudais",
                        "Saad Al-Ghamdi"
                    )

                    qaris.forEach { qari ->
                        val isSelected = viewModel.activeQari == qari
                        Card(
                            onClick = {
                                viewModel.activeQari = qari
                                if (viewModel.isQuranPlaying) {
                                    viewModel.stopQuranRecitation()
                                    viewModel.playQuranRecitation(context, readerSurahId!!)
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = qari,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSelectQariDialog = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSurahDetailDialog && readerSurahId != null) {
        val detailSurah = QuranData.surahs.firstOrNull { it.id == readerSurahId }
        if (detailSurah != null) {
            AlertDialog(
                onDismissRequest = { showSurahDetailDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Detail Surah",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = detailSurah.nameArabic,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Surah ${detailSurah.nameIndonesian} (Surah ke-${detailSurah.id})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Arti Surah:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(detailSurah.translationName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tempat Diturunkan:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (detailSurah.revelationType.lowercase(Locale.getDefault()) == "makkiyah") "Makkiyah (Mekkah)" else "Madaniyah (Madinah)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Jumlah Ayat:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${detailSurah.verseCount} Ayat", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSurahDetailDialog = false }) {
                        Text("Tutup", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyStateView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SurahListItem(surah: Surah, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Surah Index Badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = surah.id.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Names
                Column {
                    Text(
                        text = surah.nameIndonesian,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${surah.revelationType} • ${surah.verseCount} Ayat",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Arabic calligraphy
            Text(
                text = surah.nameArabic,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun JuzListItem(juz: JuzInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Juz\n${juz.number}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Juz ${juz.number}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mulai dari Surah: ${juz.startSurahName} (Ayat ${juz.startVerse})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Mulai Membaca",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BookmarkListItem(bookmark: QuranBookmark, onDelete: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Surah ${bookmark.surahName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ayat ${bookmark.verseNumber}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun HistoryListItem(hist: QuranHistory, onClick: () -> Unit) {
    val dateText = remember(hist.timestamp) {
        val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        formatter.format(Date(hist.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Surah ${hist.surahName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ayat ${hist.verseNumber} • Dibaca: $dateText",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Custom Tajwid Colorizing Parser
fun buildTajwidColoredString(arabicText: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < arabicText.length) {
            val char = arabicText[i]

            // Qalqalah letters: ق ط ب ج د with Sukun explicitly (ْ - \u0652)
            if ((char == 'ق' || char == 'ط' || char == 'ب' || char == 'ج' || char == 'د') &&
                i + 1 < arabicText.length && arabicText[i + 1] == '\u0652') {
                pushStyle(SpanStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold)) // Blue
                append(char)
                append(arabicText[i + 1])
                pop()
                i += 2
                continue
            }

            // Ghunnah: Shaddah on Noon (نّ) or Meem (مّ) - Shaddah is \u0651
            if ((char == 'ن' || char == 'م') &&
                i + 1 < arabicText.length && arabicText[i + 1] == '\u0651') {
                pushStyle(SpanStyle(color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)) // Pink/Red
                append(char)
                append(arabicText[i + 1])
                pop()
                i += 2
                continue
            }

            // Mad letters: Alif, Waw, Ya with specific characteristics
            if (char == 'ا' || char == 'و' || char == 'ي') {
                pushStyle(SpanStyle(color = Color(0xFF2E7D32))) // Green
                append(char)
                pop()
                i++
                continue
            }

            // Ikhfa / Idgham rules or Tanween (ً - \u064B, ٌ - \u064C, ٍ - \u064D)
            if (char == '\u064B' || char == '\u064C' || char == '\u064D') {
                pushStyle(SpanStyle(color = Color(0xFFEF6C00))) // Orange
                append(char)
                pop()
                i++
                continue
            }

            append(char)
            i++
        }
    }
}
