package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.MindMap
import com.example.ui.MainViewModel
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

// --- RECURSIVE NODE MODEL ---
@JsonClass(generateAdapter = true)
data class MindMapNode(
    var id: String,
    var title: String,
    var description: String,
    var children: List<MindMapNode> = emptyList(),
    var colorTheme: String? = null,
    var isCollapsed: Boolean = false,
    var note: String? = null,
    var iconName: String? = null,
    var x: Float? = null,
    var y: Float? = null,
    var secondaryParentIds: List<String> = emptyList(),
    var customWidth: Float? = null,
    var customHeight: Float? = null,
    var textAlign: String? = null
)

data class MindMapHistoryState(
    val rootNode: MindMapNode,
    val nodeCoords: Map<String, Offset>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindMapScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mindMaps by viewModel.mindMaps.collectAsStateWithLifecycle()

    // Screen navigation/state
    var activeMindMap by remember { mutableStateOf<MindMap?>(null) }
    var activeNodeTree by remember { mutableStateOf<MindMapNode?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Visual Canvas, 1: Textual Outline
    var menuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    var isFreeform by remember { mutableStateOf(false) }
    var selectedHierarchyTheme by remember { mutableStateOf("Default") }
    var showAllSavedMapsScreen by remember { mutableStateOf(false) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var result: String? = null
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    cursor?.close()
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/') ?: -1
                if (cut != -1) {
                    result = result?.substring(cut + 1)
                }
            }
            selectedFileName = result ?: "File referensi"
            MainViewModel.instance?.showToastPopup("File berhasil diunggah: $selectedFileName")
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                inputText = if (inputText.isEmpty()) spokenText else "$inputText $spokenText"
            }
        }
    }

    BackHandler {
        if (showAllSavedMapsScreen) {
            showAllSavedMapsScreen = false
        } else if (activeMindMap != null) {
            activeMindMap = null
            activeNodeTree = null
        } else {
            onBackClick()
        }
    }

    // Background autosave service running every 30 seconds to prevent data loss
    LaunchedEffect(activeMindMap, activeNodeTree) {
        while (true) {
            delay(30000)
            val currentMap = activeMindMap
            val currentTree = activeNodeTree
            if (currentMap != null && currentTree != null) {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val jsonStr = moshi.adapter(MindMapNode::class.java).toJson(currentTree)
                val updatedMap = currentMap.copy(jsonStructure = jsonStr)
                viewModel.updateMindMap(updatedMap)
            }
        }
    }

    // UI Feedback phrases during analysis
    var analysisTip by remember { mutableStateOf("Membaca teks...") }
    var showExportOrientationDialog by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf("png") } // "png" or "pdf"
    var isExporting by remember { mutableStateOf(false) }
    val exportCoroutineScope = rememberCoroutineScope()
    val tips = listOf(
        "Menganalisis materi teks...",
        "Mengekstrak kata kunci utama...",
        "Menyusun struktur hierarki logis...",
        "Menggambar diagram visual...",
        "Mengamankan data peta konsep..."
    )

    // LaunchedEffect to rotate tips
    LaunchedEffect(isAnalyzing) {
        if (isAnalyzing) {
            var idx = 0
            while (isAnalyzing) {
                analysisTip = tips[idx % tips.size]
                delay(2000)
                idx++
            }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = Pair(activeMindMap, showAllSavedMapsScreen),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500), initialOffsetY = { it / 4 }))
                        .togetherWith(fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400), targetOffsetY = { -it / 4 }))
                },
                label = "MindMapTransition"
            ) { (currentMap, showAll) ->
                if (showAll) {
                    AllSavedMapsView(
                        mindMaps = mindMaps,
                        onBackClick = { showAllSavedMapsScreen = false },
                        onSelectMap = { map ->
                            activeMindMap = map
                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                            activeNodeTree = try {
                                moshi.adapter(MindMapNode::class.java).fromJson(map.jsonStructure)
                            } catch (e: Exception) {
                                null
                            }
                            showAllSavedMapsScreen = false
                        },
                        onDeleteMap = { map ->
                            viewModel.deleteMindMap(map)
                            MainViewModel.instance?.showToastPopup("Peta Konsep berhasil dihapus")
                        }
                    )
                } else if (currentMap == null) {
                    // DASHBOARD VIEW (Redesigned MindMap AI Dashboard)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Input Form Card with AI Actions integrated in Header
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                        )
                                    )
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Minta ke AI",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        IconButton(
                                            onClick = { showAllSavedMapsScreen = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Lihat Semua Peta Konsep",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                // Custom Unified Outlined Border Container
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        // The TextField without borders
                                        OutlinedTextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            placeholder = {
                                                Text(
                                                    text = "Ketik topik, materi pelajaran, atau rangkuman ide untuk dipetakan...",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    lineHeight = 16.sp
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(95.dp)
                                                .testTag("mindmap_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color.Transparent,
                                                unfocusedBorderColor = Color.Transparent,
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent
                                            )
                                        )

                                        // Optional: Display Selected/Uploaded File name
                                        if (selectedFileName != null) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = selectedFileName ?: "",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Hapus File",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clickable { selectedFileName = null }
                                                )
                                            }
                                        }

                                        // Bottom Action Row (Upload + Speech to Text + AI Submit Button)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left side icons (Upload and Voice)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Icon "+" to upload file
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            MainViewModel.instance?.preventAutoLock = true
                                                             filePickerLauncher.launch("*/*")
                                                        } catch (e: Exception) {
                                                            MainViewModel.instance?.showToastPopup("Tidak ada file manager pendukung")
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "Unggah File",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                // Icon Mic for voice to text
                                                IconButton(
                                                    onClick = {
                                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Silakan bicara...")
                                                        }
                                                        try {
                                                            speechRecognizerLauncher.launch(intent)
                                                        } catch (e: Exception) {
                                                            MainViewModel.instance?.showToastPopup("Speech recognizer tidak didukung di perangkat ini")
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = "Konversi Suara ke Teks",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            // Right side: Submit AI Button
                                            FilledIconButton(
                                                onClick = {
                                                    val promptText = if (selectedFileName != null) {
                                                        "$inputText (Referenced file: $selectedFileName)"
                                                    } else {
                                                        inputText
                                                    }
                                                    if (promptText.trim().isEmpty()) {
                                                        MainViewModel.instance?.showToastPopup("Materi atau topik tidak boleh kosong!")
                                                    } else {
                                                        isAnalyzing = true
                                                        scope.launch {
                                                            generateMindMapFromAI(context, promptText) { resultNode ->
                                                                isAnalyzing = false
                                                                if (resultNode != null) {
                                                                    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                                                    val jsonStr = moshi.adapter(MindMapNode::class.java).toJson(resultNode)
                                                                    val newMap = MindMap(
                                                                        title = resultNode.title,
                                                                        originalText = promptText,
                                                                        jsonStructure = jsonStr
                                                                    )
                                                                    viewModel.insertMindMap(newMap) { savedId ->
                                                                        activeMindMap = newMap.copy(id = savedId)
                                                                        activeNodeTree = resultNode
                                                                        inputText = ""
                                                                        selectedFileName = null
                                                                    }
                                                                    MainViewModel.instance?.showToastPopup("Peta Konsep berhasil dibuat!")
                                                                } else {
                                                                    MainViewModel.instance?.showToastPopup("Gagal membuat peta konsep. Periksa kunci API Gemini Anda.")
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).testTag("btn_build_mindmap"),
                                                colors = IconButtonDefaults.filledIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "Buat Peta Konsep AI",
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // My Saved Maps Section Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Peta Konsep Saya",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (mindMaps.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${mindMaps.size} Tersimpan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Saved list or empty state illustration
                    if (mindMaps.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_mindmap_empty),
                                            contentDescription = "Belum Ada Peta Konsep",
                                            modifier = Modifier
                                                .size(240.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Belum Ada Peta Konsep",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Gunakan kolom di atas untuk membuat peta konsep pertamamu dengan AI luring.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val limitMindMaps = mindMaps.take(5)
                        items(limitMindMaps) { map ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeMindMap = map
                                        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                        activeNodeTree = try {
                                            moshi.adapter(MindMapNode::class.java).fromJson(map.jsonStructure)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    .testTag("saved_mindmap_${map.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountTree,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = map.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = formatTimestamp(map.createdAt),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteMindMap(map)
                                            MainViewModel.instance?.showToastPopup("Peta Konsep berhasil dihapus")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (mindMaps.size > 5) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TextButton(onClick = { showAllSavedMapsScreen = true }) {
                                        Text(
                                            text = "Lihat Semua (${mindMaps.size})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                    // ACTIVE MIND MAP DETAILS VIEW (Interactive Canvas / Textual Outline)
                    Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar with Title on the Left and Minimal Overflow Menu on the Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentMap.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Peta Konsep • ${if (activeTab == 0) "Diagram Visual" else "Garis Besar Teks"}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("mindmap_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu Opsi",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Diagram Visual", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountTree,
                                            contentDescription = null,
                                            tint = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        activeTab = 0
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Garis Besar Teks", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = null,
                                            tint = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        activeTab = 1
                                        menuExpanded = false
                                    }
                                )
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                
                                // Mode Layout Sub-list inside dropdown menu
                                DropdownMenuItem(
                                    text = { Text("Mode: Otomatis", fontWeight = if (!isFreeform) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (!isFreeform) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        isFreeform = false
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mode: Bebas (Geser)", fontWeight = if (isFreeform) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.OpenWith,
                                            contentDescription = null,
                                            tint = if (isFreeform) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        isFreeform = true
                                        menuExpanded = false
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                                // Tema Level Sub-list nested dropdown
                                DropdownMenuItem(
                                    text = { Text("Tema Level: $selectedHierarchyTheme") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        themeMenuExpanded = true
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                                DropdownMenuItem(
                                    text = { Text("Simpan Gambar (PNG)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        pendingExportFormat = "png"
                                        showExportOrientationDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Simpan Dokumen (PDF)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        pendingExportFormat = "pdf"
                                        showExportOrientationDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bagikan Teks") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        currentMap.let { map ->
                                            val textToShare = buildString {
                                                appendLine("PETA KONSEP: ${map.title}")
                                                activeNodeTree?.let { root ->
                                                    appendLine(formatTextOutline(root, 0))
                                                }
                                            }
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Bagikan Peta Konsep"))
                                        }
                                    }
                                )
                            }

                            // Nested DropdownMenu for Hierarchy Level Themes
                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false }
                            ) {
                                val themesList = listOf("Default", "Sunset Glow", "Forest Fresh", "Ocean Breeze", "Lavender Fields")
                                themesList.forEach { themeName ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val previewColor = when (themeName) {
                                                    "Sunset Glow" -> Color(0xFFD84315)
                                                    "Forest Fresh" -> Color(0xFF388E3C)
                                                    "Ocean Breeze" -> Color(0xFF1976D2)
                                                    "Lavender Fields" -> Color(0xFF7B1FA2)
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(previewColor)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = themeName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selectedHierarchyTheme == themeName) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedHierarchyTheme = themeName
                                            themeMenuExpanded = false
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Content Viewport
                    Box(modifier = Modifier.weight(1f)) {
                        if (activeTab == 0) {
                            // Interactive Canvas
                            activeNodeTree?.let { root ->
                                MindMapCanvasView(
                                    rootNode = root,
                                    onTreeChanged = { updatedTree ->
                                        activeNodeTree = updatedTree
                                        // Save back to db
                                        currentMap.let { map ->
                                            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                                            val jsonStr = moshi.adapter(MindMapNode::class.java).toJson(updatedTree)
                                            val updatedMap = map.copy(jsonStructure = jsonStr)
                                            viewModel.updateMindMap(updatedMap)
                                            activeMindMap = updatedMap
                                        }
                                    },
                                    selectedHierarchyTheme = selectedHierarchyTheme,
                                    onThemeChanged = { selectedHierarchyTheme = it },
                                    isFreeform = isFreeform,
                                    onIsFreeformChanged = { isFreeform = it }
                                )
                            }
                        } else {
                            // Textual Outline View
                            activeNodeTree?.let { root ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Struktur Hierarki Peta Konsep",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.DarkGray
                                                )
                                                Text(
                                                    text = "Salin",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1976D2),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clickable {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            val clip = android.content.ClipData.newPlainText(
                                                                "MindMap Outline",
                                                                formatTextOutline(root, 0)
                                                            )
                                                            clipboard.setPrimaryClip(clip)
                                                            MainViewModel.instance?.showToastPopup("Garis Besar disalin ke papan klip!")
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = formatTextOutline(root, 0),
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                color = Color.Black
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

            if (showExportOrientationDialog) {
                ExportOrientationDialog(
                    format = pendingExportFormat,
                    onDismiss = { showExportOrientationDialog = false },
                    onConfirm = { orientation ->
                        showExportOrientationDialog = false
                        activeNodeTree?.let { root ->
                            isExporting = true
                            exportCoroutineScope.launch(Dispatchers.Default) {
                                try {
                                    executeExport(
                                        context = context,
                                        rootNode = root,
                                        format = pendingExportFormat,
                                        orientation = orientation,
                                        hierarchyTheme = selectedHierarchyTheme
                                    )
                                } finally {
                                    withContext(Dispatchers.Main) {
                                        isExporting = false
                                    }
                                }
                            }
                        }
                    }
                )
            }

            if (isExporting) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Mengekspor Peta Konsep...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Menyiapkan berkas ${pendingExportFormat.uppercase()} berkualitas tinggi",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // LOADING ANALYZER OVERLAY
            if (isAnalyzing) {
                ModernAILoader(analysisTip = analysisTip)
            }
        }
    }
}

private data class ResolvedNodeColors(
    val containerColor: Color,
    val textColor: Color,
    val borderColor: Color
)

private fun resolveNodeColors(
    theme: String?,
    depth: Int,
    primary: Color,
    secondaryContainer: Color,
    tertiaryContainer: Color,
    onPrimary: Color,
    onSecondaryContainer: Color,
    onTertiaryContainer: Color,
    outline: Color,
    onSurface: Color,
    hierarchyTheme: String = "Default"
): ResolvedNodeColors {
    val nodeTheme = theme ?: "Default"
    if (nodeTheme == "Default" || nodeTheme.isEmpty()) {
        return when (hierarchyTheme) {
            "Sunset Glow" -> {
                val container = when (depth) {
                    0 -> Color(0xFFD84315)
                    1 -> Color(0xFFFF8F00)
                    2 -> Color(0xFFFFC107)
                    3 -> Color(0xFFFFECB3)
                    else -> Color(0xFFFFF8E1)
                }
                val text = if (depth <= 1) Color.White else Color(0xFF3E2723)
                val border = when (depth) {
                    0 -> Color(0xFFBF360C)
                    1 -> Color(0xFFE65100)
                    2 -> Color(0xFFFF8F00)
                    else -> Color(0xFFFFCC80)
                }
                ResolvedNodeColors(container, text, border)
            }
            "Forest Fresh" -> {
                val container = when (depth) {
                    0 -> Color(0xFF1B5E20)
                    1 -> Color(0xFF388E3C)
                    2 -> Color(0xFF81C784)
                    3 -> Color(0xFFC8E6C9)
                    else -> Color(0xFFE8F5E9)
                }
                val text = if (depth <= 1) Color.White else Color(0xFF1B5E20)
                val border = when (depth) {
                    0 -> Color(0xFF0D330D)
                    1 -> Color(0xFF2E7D32)
                    2 -> Color(0xFF66BB6A)
                    else -> Color(0xFFA5D6A7)
                }
                ResolvedNodeColors(container, text, border)
            }
            "Ocean Breeze" -> {
                val container = when (depth) {
                    0 -> Color(0xFF0D47A1)
                    1 -> Color(0xFF1976D2)
                    2 -> Color(0xFF03A9F4)
                    3 -> Color(0xFFB3E5FC)
                    else -> Color(0xFFE1F5FE)
                }
                val text = if (depth <= 1) Color.White else Color(0xFF0D47A1)
                val border = when (depth) {
                    0 -> Color(0xFF0A2F6B)
                    1 -> Color(0xFF1565C0)
                    2 -> Color(0xFF29B6F6)
                    else -> Color(0xFF81D4FA)
                }
                ResolvedNodeColors(container, text, border)
            }
            "Lavender Fields" -> {
                val container = when (depth) {
                    0 -> Color(0xFF4A148C)
                    1 -> Color(0xFF7B1FA2)
                    2 -> Color(0xFF9C27B0)
                    3 -> Color(0xFFE1BEE7)
                    else -> Color(0xFFF3E5F5)
                }
                val text = if (depth <= 1) Color.White else Color(0xFF4A148C)
                val border = when (depth) {
                    0 -> Color(0xFF310A5D)
                    1 -> Color(0xFF6A1B9A)
                    2 -> Color(0xFFBA68C8)
                    else -> Color(0xFFE1BEE7)
                }
                ResolvedNodeColors(container, text, border)
            }
            else -> {
                val container = when (depth) {
                    0 -> primary
                    1 -> secondaryContainer
                    else -> tertiaryContainer
                }
                val text = when (depth) {
                    0 -> onPrimary
                    1 -> onSecondaryContainer
                    else -> onTertiaryContainer
                }
                val border = when (depth) {
                    0 -> primary
                    1 -> secondaryContainer
                    else -> outline.copy(alpha = 0.4f)
                }
                ResolvedNodeColors(container, text, border)
            }
        }
    } else if (nodeTheme.startsWith("#")) {
        val baseColor = try {
            Color(android.graphics.Color.parseColor(nodeTheme))
        } catch (e: Exception) {
            Color.Gray
        }
        
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
        
        val textHsv = hsv.clone()
        textHsv[2] = 0.25f // force very dark for high contrast text readability
        textHsv[1] = (textHsv[1] * 1.3f).coerceAtMost(1f)
        val text = Color(android.graphics.Color.HSVToColor(textHsv))
        
        val container = if (depth == 0) {
            baseColor
        } else {
            // Blend 12% of baseColor with 88% of White to get an opaque light pastel background
            Color(
                red = baseColor.red * 0.12f + 1f * 0.88f,
                green = baseColor.green * 0.12f + 1f * 0.88f,
                blue = baseColor.blue * 0.12f + 1f * 0.88f,
                alpha = 1f
            )
        }
        
        val textCol = if (depth == 0) {
            val r = baseColor.red
            val g = baseColor.green
            val b = baseColor.blue
            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            if (luminance > 0.5f) Color.Black else Color.White
        } else {
            text
        }
        
        return ResolvedNodeColors(container, textCol, baseColor)
    } else {
        val container = when (nodeTheme) {
            "Blue" -> Color(0xFFE3F2FD)
            "Green" -> Color(0xFFE8F5E9)
            "Orange" -> Color(0xFFFFF3E0)
            "Purple" -> Color(0xFFF3E5F5)
            "Red" -> Color(0xFFFFEBEE)
            "Teal" -> Color(0xFFE0F2F1)
            else -> Color(0xFFF5F5F5)
        }
        val text = when (nodeTheme) {
            "Blue" -> Color(0xFF0D47A1)
            "Green" -> Color(0xFF1B5E20)
            "Orange" -> Color(0xFFE65100)
            "Purple" -> Color(0xFF4A148C)
            "Red" -> Color(0xFFB71C1C)
            "Teal" -> Color(0xFF004D40)
            else -> onSurface
        }
        val border = when (nodeTheme) {
            "Blue" -> Color(0xFF2196F3)
            "Green" -> Color(0xFF4CAF50)
            "Orange" -> Color(0xFFFF9800)
            "Purple" -> Color(0xFF9C27B0)
            "Red" -> Color(0xFFF44336)
            "Teal" -> Color(0xFF009688)
            else -> outline
        }
        return ResolvedNodeColors(container, text, border)
    }
}

private fun colorToHexString(color: Color): String {
    return String.format("#%06X", 0xFFFFFF and color.toArgb())
}

private fun getNodeWidthAndHeight(node: MindMapNode, depth: Int): Pair<Float, Float> {
    val paint = android.graphics.Paint()
    
    val titleSizeDp = if (depth == 0) 12f else 11f
    val descSizeDp = 8f
    
    // Determine target width limits in DP
    val minWidthDp = 120f
    val maxWidthDp = 360f
    
    // Wrap title lines
    val maxTitleChars = ((maxWidthDp - 16f) / 5.5f).toInt().coerceAtLeast(10)
    val titleLines = splitTextIntoWrappedLines(node.title, maxTitleChars)
    
    paint.textSize = titleSizeDp
    paint.isFakeBoldText = true
    var maxTitleLineWidth = 0f
    titleLines.forEach { line ->
        val w = paint.measureText(line)
        if (w > maxTitleLineWidth) {
            maxTitleLineWidth = w
        }
    }
    
    // Measure description lines
    val maxDescChars = ((maxWidthDp - 16f) / 4.2f).toInt().coerceAtLeast(15)
    val descLines = splitTextIntoWrappedLines(node.description, maxDescChars)
    
    paint.textSize = descSizeDp
    paint.isFakeBoldText = false
    var maxDescLineWidth = 0f
    descLines.forEach { line ->
        val w = paint.measureText(line)
        if (w > maxDescLineWidth) {
            maxDescLineWidth = w
        }
    }
    
    val rawTextWidth = maxOf(maxTitleLineWidth, maxDescLineWidth)
    val horizontalPadding = 24f
    var finalWidth = rawTextWidth + horizontalPadding
    finalWidth = finalWidth.coerceIn(minWidthDp, maxWidthDp)
    if (node.customWidth != null) {
        finalWidth = node.customWidth!!
    }
    
    // Now calculate height in DP
    val titleLineHeight = if (depth == 0) 14f else 13f
    val descLineHeight = 10.5f
    val spacing = 6f
    
    var totalHeight = 20f // Top & bottom padding
    
    if (node.iconName != null) {
        totalHeight += 22f
    }
    
    totalHeight += titleLines.size * titleLineHeight
    
    if (descLines.isNotEmpty()) {
        totalHeight += spacing + (descLines.size * descLineHeight)
    }
    
    if (!node.note.isNullOrBlank()) {
        totalHeight += 20f
    }
    
    if (node.children.isNotEmpty()) {
        totalHeight += 24f
    }
    
    totalHeight = totalHeight.coerceAtLeast(50f)
    if (node.customHeight != null) {
        totalHeight = node.customHeight!!
    }
    
    return Pair(finalWidth, totalHeight)
}

// --- INTERACTIVE CANVAS COMPOSABLE ---
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MindMapCanvasView(
    rootNode: MindMapNode,
    onTreeChanged: (MindMapNode) -> Unit,
    selectedHierarchyTheme: String,
    onThemeChanged: (String) -> Unit,
    isFreeform: Boolean,
    onIsFreeformChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Undo / Redo stacks
    val undoStack = remember { mutableStateListOf<MindMapHistoryState>() }
    val redoStack = remember { mutableStateListOf<MindMapHistoryState>() }

    // Map to hold computed positions
    val nodeCoords = remember { mutableStateMapOf<String, Offset>() }
    val subtreeWidths = remember { mutableStateMapOf<String, Float>() }

    // Dialog state
    var selectedNodeForAction by remember { mutableStateOf<MindMapNode?>(null) }
    var showAddChildDialog by remember { mutableStateOf(false) }
    var showMoveParentDialog by remember { mutableStateOf(false) }
    var showSecondaryParentDialog by remember { mutableStateOf(false) }

    // Double tap note popup dialog state
    var doubleTappedNodeForNote by remember { mutableStateOf<MindMapNode?>(null) }
    var noteDialogText by remember { mutableStateOf("") }

    // Temporary variables for editing or adding
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editColorTheme by remember { mutableStateOf<String?>("Default") }
    var customHexInput by remember { mutableStateOf("#FF5722") }
    var editNote by remember { mutableStateOf("") }
    var editIconName by remember { mutableStateOf<String?>(null) }
    var editSecondaryParentIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var editCustomWidth by remember { mutableStateOf("") }
    var editCustomHeight by remember { mutableStateOf("") }
    var editTextAlign by remember { mutableStateOf<String?>("Center") }
    var editTextAlignDropdownExpanded by remember { mutableStateOf(false) }
    var editColorDropdownExpanded by remember { mutableStateOf(false) }
    var activeViewportWidthDp by remember { mutableStateOf(360f) }
    var activeViewportHeightDp by remember { mutableStateOf(640f) }
    var resizingNodeId by remember { mutableStateOf<String?>(null) }
    var addChildTitle by remember { mutableStateOf("") }
    var addChildDesc by remember { mutableStateOf("") }

    fun commitTree(updatedTree: MindMapNode) {
        val mergedTree = mergeCoordsIntoTree(updatedTree, nodeCoords.toMap())
        onTreeChanged(mergedTree)
    }

    fun saveToHistory() {
        val mergedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
        undoStack.add(MindMapHistoryState(mergedTree, nodeCoords.toMap()))
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val currentState = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
            redoStack.add(MindMapHistoryState(currentState, nodeCoords.toMap()))
            val previousState = undoStack.removeAt(undoStack.lastIndex)
            nodeCoords.clear()
            nodeCoords.putAll(previousState.nodeCoords)
            onTreeChanged(previousState.rootNode)
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val currentState = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
            undoStack.add(MindMapHistoryState(currentState, nodeCoords.toMap()))
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            nodeCoords.clear()
            nodeCoords.putAll(nextState.nodeCoords)
            onTreeChanged(nextState.rootNode)
        }
    }

    LaunchedEffect(selectedNodeForAction?.id) {
        selectedNodeForAction?.let { node ->
            editTitle = node.title
            editDesc = node.description
            val theme = node.colorTheme ?: "Default"
            editColorTheme = theme
            if (theme.startsWith("#")) {
                customHexInput = theme
            } else {
                customHexInput = "#FF5722"
            }
            editNote = node.note ?: ""
            editIconName = node.iconName
            editSecondaryParentIds = node.secondaryParentIds
            editCustomWidth = node.customWidth?.toString() ?: ""
            editCustomHeight = node.customHeight?.toString() ?: ""
            editTextAlign = node.textAlign ?: "Center"
        }
    }

    LaunchedEffect(doubleTappedNodeForNote?.id) {
        doubleTappedNodeForNote?.let { node ->
            noteDialogText = node.note ?: ""
        }
    }

    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current

    fun measureNodeSize(node: MindMapNode, depth: Int): Pair<Float, Float> {
        val densityVal = density.density
        
        val minWidthDp = 120f
        val maxWidthDp = 360f
        
        val minWidthPx = minWidthDp * densityVal
        val maxWidthPx = maxWidthDp * densityVal
        
        val titleSizeSp = if (depth == 0) 12.sp else 11.sp
        val descSizeSp = 8.sp
        
        val titleLayout = textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(node.title),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = titleSizeSp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            constraints = androidx.compose.ui.unit.Constraints(
                minWidth = 0,
                maxWidth = maxWidthPx.toInt()
            )
        )
        
        val descLayout = if (node.description.isNotEmpty()) {
            textMeasurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(node.description),
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = descSizeSp,
                    lineHeight = 10.sp
                ),
                constraints = androidx.compose.ui.unit.Constraints(
                    minWidth = 0,
                    maxWidth = maxWidthPx.toInt()
                )
            )
        } else null
        
        val titleWidthPx = titleLayout.size.width.toFloat()
        val descWidthPx = descLayout?.size?.width?.toFloat() ?: 0f
        
        val rawTextWidthPx = maxOf(titleWidthPx, descWidthPx)
        val horizontalPaddingPx = 24f * densityVal
        var finalWidthPx = rawTextWidthPx + horizontalPaddingPx
        
        finalWidthPx = finalWidthPx.coerceIn(minWidthPx, maxWidthPx)
        var finalWidthDp = finalWidthPx / densityVal
        if (node.customWidth != null) {
            finalWidthDp = node.customWidth!!
        }
        
        val textWidthBudgetPx = if (node.customWidth != null) {
            (node.customWidth!! * densityVal - horizontalPaddingPx).coerceAtLeast(1f).toInt()
        } else {
            (finalWidthPx - horizontalPaddingPx).coerceAtLeast(1f).toInt()
        }
        
        val exactTitleLayout = textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(node.title),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = titleSizeSp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            constraints = androidx.compose.ui.unit.Constraints(
                minWidth = 0,
                maxWidth = textWidthBudgetPx
            )
        )
        
        val exactDescLayout = if (node.description.isNotEmpty()) {
            textMeasurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(node.description),
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = descSizeSp,
                    lineHeight = 10.sp
                ),
                constraints = androidx.compose.ui.unit.Constraints(
                    minWidth = 0,
                    maxWidth = textWidthBudgetPx
                )
            )
        } else null
        
        val titleHeightPx = exactTitleLayout.size.height.toFloat()
        val descHeightPx = exactDescLayout?.size?.height?.toFloat() ?: 0f
        
        var totalHeightPx = 20f * densityVal
        
        if (node.iconName != null) {
            totalHeightPx += 22f * densityVal
        }
        
        totalHeightPx += titleHeightPx
        
        if (exactDescLayout != null) {
            totalHeightPx += 2f * densityVal
            totalHeightPx += descHeightPx
        }
        
        if (!node.note.isNullOrBlank()) {
            totalHeightPx += 20f * densityVal
        }
        
        if (node.children.isNotEmpty()) {
            totalHeightPx += 24f * densityVal
        }
        
        var finalHeightDp = (totalHeightPx / densityVal).coerceAtLeast(50f)
        if (node.customHeight != null) {
            finalHeightDp = node.customHeight!!
        }
        
        return Pair(finalWidthDp, finalHeightDp)
    }

    // Subtree layout width computation (handles collapsed subtrees with dynamic spacing)
    fun computeWidths(node: MindMapNode): Float {
        val nodeW = measureNodeSize(node, 0).first
        val horizontalMargin = 40f + (node.description.length * 0.8f).coerceAtMost(120f)
        val defaultW = nodeW + horizontalMargin
        val w = if (node.children.isEmpty() || node.isCollapsed) {
            defaultW
        } else {
            var sum = 0f
            node.children.forEach { sum += computeWidths(it) }
            sum.coerceAtLeast(defaultW)
        }
        subtreeWidths[node.id] = w
        return w
    }

    // Coordinates assigned dynamically with vertical layout adjustments based on parent heights and dynamic gaps
    fun layoutTree(node: MindMapNode, depth: Int, leftBoundary: Float, parentY: Float = 80f, parentHeight: Float = 0f) {
        val (nodeW, nodeH) = measureNodeSize(node, depth)
        val horizontalMargin = 40f + (node.description.length * 0.8f).coerceAtMost(120f)
        val defaultW = nodeW + horizontalMargin
        val width = subtreeWidths[node.id] ?: defaultW
        
        val baseGap = 120f
        val descExtraGap = (node.description.length * 0.8f).coerceAtMost(100f)
        val verticalGap = baseGap + descExtraGap
        
        val y = if (depth == 0) {
            80f
        } else {
            parentY + (parentHeight / 2f) + verticalGap + (nodeH / 2f)
        }

        if (node.children.isEmpty() || node.isCollapsed) {
            val x = leftBoundary + width / 2
            nodeCoords[node.id] = Offset(x, y)
        } else {
            var currentLeft = leftBoundary
            val childXCoords = mutableListOf<Float>()
            node.children.forEach { child ->
                val childW = measureNodeSize(child, depth + 1).first
                val childMargin = 40f + (child.description.length * 0.8f).coerceAtMost(120f)
                val childWidth = subtreeWidths[child.id] ?: (childW + childMargin)
                layoutTree(child, depth + 1, currentLeft, parentY = y, parentHeight = nodeH)
                val childCoord = nodeCoords[child.id]!!
                childXCoords.add(childCoord.x)
                currentLeft += childWidth
            }
            val parentX = (childXCoords.first() + childXCoords.last()) / 2
            nodeCoords[node.id] = Offset(parentX, y)
        }
    }

    // Dynamic initial centering & Freeform layout management based on Density
    LaunchedEffect(rootNode, isFreeform) {
        if (!isFreeform) {
            subtreeWidths.clear()
            nodeCoords.clear()
            computeWidths(rootNode)
            layoutTree(rootNode, 0, 100f)

            // Center the root node horizontally in the typical viewport width on load
            val rootX = nodeCoords[rootNode.id]?.x ?: 200f
            val targetCenterDp = 180f
            val targetOffsetDp = targetCenterDp - rootX
            offset = Offset(targetOffsetDp * density.density, 60f * density.density)
        } else {
            // Restore custom coordinates from the persistent tree structure
            fun restoreCoords(n: MindMapNode) {
                if (n.x != null && n.y != null) {
                    nodeCoords[n.id] = Offset(n.x!!, n.y!!)
                }
                n.children.forEach { restoreCoords(it) }
            }
            restoreCoords(rootNode)

            // Freeform mode: compute layouts for any newly added nodes dynamically while preserving existing drags
            val tempCoords = mutableMapOf<String, Offset>()
            subtreeWidths.clear()
            computeWidths(rootNode)
            
            fun layoutTemp(node: MindMapNode, depth: Int, leftBoundary: Float, parentY: Float = 80f, parentHeight: Float = 0f) {
                val (nodeW, nodeH) = measureNodeSize(node, depth)
                val horizontalMargin = 40f + (node.description.length * 0.8f).coerceAtMost(120f)
                val defaultW = nodeW + horizontalMargin
                val width = subtreeWidths[node.id] ?: defaultW
                
                val baseGap = 120f
                val descExtraGap = (node.description.length * 0.8f).coerceAtMost(100f)
                val verticalGap = baseGap + descExtraGap
                
                val y = if (depth == 0) {
                    80f
                } else {
                    parentY + (parentHeight / 2f) + verticalGap + (nodeH / 2f)
                }
                
                if (node.children.isEmpty() || node.isCollapsed) {
                    val x = leftBoundary + width / 2
                    tempCoords[node.id] = Offset(x, y)
                } else {
                    var currentLeft = leftBoundary
                    val childXCoords = mutableListOf<Float>()
                    node.children.forEach { child ->
                        val childW = measureNodeSize(child, depth + 1).first
                        val childMargin = 40f + (child.description.length * 0.8f).coerceAtMost(120f)
                        val childWidth = subtreeWidths[child.id] ?: (childW + childMargin)
                        layoutTemp(child, depth + 1, currentLeft, parentY = y, parentHeight = nodeH)
                        val childCoord = tempCoords[child.id]!!
                        childXCoords.add(childCoord.x)
                        currentLeft += childWidth
                    }
                    val parentX = (childXCoords.first() + childXCoords.last()) / 2
                    tempCoords[node.id] = Offset(parentX, y)
                }
            }
            layoutTemp(rootNode, 0, 100f)

            // Inject missing coordinates
            tempCoords.forEach { (id, coord) ->
                if (!nodeCoords.containsKey(id)) {
                    nodeCoords[id] = coord
                }
            }

            // Remove coordinates for deleted nodes
            val allNodeIds = mutableSetOf<String>()
            fun collectIds(n: MindMapNode) {
                allNodeIds.add(n.id)
                if (!n.isCollapsed) {
                    n.children.forEach { collectIds(it) }
                }
            }
            collectIds(rootNode)
            val keysToRemove = nodeCoords.keys.filter { !allNodeIds.contains(it) }
            keysToRemove.forEach { nodeCoords.remove(it) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
            .pointerInput(Unit) {
                detectTransformGestures { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                    scale = (scale * zoom).coerceIn(0.1f, 3.0f)
                    offset += pan
                }
            }
    ) {
        val viewportWidthDp = maxWidth.value
        val viewportHeightDp = maxHeight.value

        LaunchedEffect(viewportWidthDp, viewportHeightDp) {
            activeViewportWidthDp = viewportWidthDp
            activeViewportHeightDp = viewportHeightDp
        }

        fun zoomToFitAllNodes() {
            if (nodeCoords.isEmpty()) return
            
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            
            nodeCoords.forEach { (nodeId, relativeOffset) ->
                val node = findNodeById(rootNode, nodeId)
                if (node != null) {
                    val depth = getNodeDepth(rootNode, nodeId)
                    val (nodeW, nodeH) = measureNodeSize(node, depth)
                    
                    val left = relativeOffset.x - nodeW / 2f
                    val right = relativeOffset.x + nodeW / 2f
                    val top = relativeOffset.y - nodeH / 2f
                    val bottom = relativeOffset.y + nodeH / 2f
                    
                    if (left < minX) minX = left
                    if (right > maxX) maxX = right
                    if (top < minY) minY = top
                    if (bottom > maxY) maxY = bottom
                }
            }
            
            val mindMapWidthDp = maxX - minX
            val mindMapHeightDp = maxY - minY
            
            if (mindMapWidthDp <= 0 || mindMapHeightDp <= 0) return
            
            val paddingDp = 48f
            val availableWidthDp = (viewportWidthDp - paddingDp * 2f).coerceAtLeast(50f)
            val availableHeightDp = (viewportHeightDp - paddingDp * 2f).coerceAtLeast(50f)
            
            val newScale = minOf(
                availableWidthDp / mindMapWidthDp,
                availableHeightDp / mindMapHeightDp
            ).coerceIn(0.15f, 1.2f)
            
            scale = newScale
            
            val midXDp = (minX + maxX) / 2f
            val midYDp = (minY + maxY) / 2f
            
            val densityVal = density.density
            val mPx = Offset(midXDp * densityVal, midYDp * densityVal)
            val vPx = Offset((viewportWidthDp / 2f) * densityVal, (viewportHeightDp / 2f) * densityVal)
            
            offset = (vPx - mPx) * scale
        }
        // GPU-Accelerated container layer for connectors and nodes
        val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        val secondaryLineColor = Color(0xFF009688).copy(alpha = 0.6f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            // Connector lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.dp.toPx()

                nodeCoords.forEach { (nodeId, startOffset) ->
                    val node = findNodeById(rootNode, nodeId)
                    node?.children?.forEach { child ->
                        val endOffset = nodeCoords[child.id]
                        if (endOffset != null) {
                            val startX = startOffset.x.dp.toPx()
                            val startY = startOffset.y.dp.toPx()
                            val endX = endOffset.x.dp.toPx()
                            val endY = endOffset.y.dp.toPx()
                            val verticalMid = (startY + endY) / 2

                            val path = Path().apply {
                                moveTo(startX, startY)
                                cubicTo(
                                    startX, verticalMid,
                                    endX, verticalMid,
                                    endX, endY
                                )
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Render secondary parent connection dashed lines
                    node?.secondaryParentIds?.forEach { parentId ->
                        val parentOffset = nodeCoords[parentId]
                        if (parentOffset != null) {
                            val startX = parentOffset.x.dp.toPx()
                            val startY = parentOffset.y.dp.toPx()
                            val endX = startOffset.x.dp.toPx()
                            val endY = startOffset.y.dp.toPx()
                            val verticalMid = (startY + endY) / 2

                            val path = Path().apply {
                                moveTo(startX, startY)
                                cubicTo(
                                    startX, verticalMid,
                                    endX, verticalMid,
                                    endX, endY
                                )
                            }
                            drawPath(
                                path = path,
                                color = secondaryLineColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }

            // Node Composables
            nodeCoords.forEach { (nodeId, relativeOffset) ->
                val node = findNodeById(rootNode, nodeId)
                if (node != null) {
                    val depth = getNodeDepth(rootNode, nodeId)

                    val resolved = resolveNodeColors(
                        theme = node.colorTheme,
                        depth = depth,
                        primary = MaterialTheme.colorScheme.primary,
                        secondaryContainer = MaterialTheme.colorScheme.secondaryContainer,
                        tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer,
                        onPrimary = MaterialTheme.colorScheme.onPrimary,
                        onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        outline = MaterialTheme.colorScheme.outline,
                        onSurface = MaterialTheme.colorScheme.onSurface,
                        hierarchyTheme = selectedHierarchyTheme
                    )
                    val containerColor = resolved.containerColor
                    val textColor = resolved.textColor
                    val borderColor = resolved.borderColor

                    val cornerShape = when (depth) {
                        0 -> RoundedCornerShape(16.dp)
                        1 -> RoundedCornerShape(12.dp)
                        else -> RoundedCornerShape(8.dp)
                    }

                    val (nodeW, nodeH) = measureNodeSize(node, depth)
                    val isResizingThisNode = resizingNodeId == node.id
                    val outerWidth = if (isResizingThisNode) (nodeW + 32).dp else nodeW.dp
                    val outerHeight = if (isResizingThisNode) (nodeH + 32).dp else nodeH.dp
                    val outerOffsetX = if (isResizingThisNode) (relativeOffset.x - nodeW / 2 - 16).dp else (relativeOffset.x - nodeW / 2).dp
                    val outerOffsetY = if (isResizingThisNode) (relativeOffset.y - nodeH / 2 - 16).dp else (relativeOffset.y - nodeH / 2).dp

                    Box(
                        modifier = Modifier
                            .offset(x = outerOffsetX, y = outerOffsetY)
                            .width(outerWidth)
                            .height(outerHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(nodeW.dp)
                                .height(nodeH.dp)
                                .clip(cornerShape)
                                .background(containerColor)
                                .border(
                                    BorderStroke(
                                        width = if (isResizingThisNode) 2.5.dp else 1.5.dp,
                                        color = if (isResizingThisNode) MaterialTheme.colorScheme.primary else borderColor
                                    ),
                                    cornerShape
                                )
                                .then(
                                    if (isFreeform) {
                                        Modifier.pointerInput(node.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    saveToHistory()
                                                },
                                                onDragEnd = {
                                                    val mergedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                                    onTreeChanged(mergedTree)
                                                },
                                                onDragCancel = {
                                                    val mergedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                                    onTreeChanged(mergedTree)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val current = nodeCoords[node.id] ?: Offset.Zero
                                                    val dragAmountDp = Offset(dragAmount.x / density.density, dragAmount.y / density.density)
                                                    nodeCoords[node.id] = current + (dragAmountDp / scale)
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                                .combinedClickable(
                                    onClick = {
                                        selectedNodeForAction = node
                                    },
                                    onDoubleClick = {
                                        doubleTappedNodeForNote = node
                                    },
                                    onLongClick = {
                                        saveToHistory()
                                        resizingNodeId = node.id
                                        MainViewModel.instance?.showToastPopup("Mode Ubah Ukuran Aktif! Seret bulatan di tepi node untuk mengubah ukuran.")
                                    }
                                )
                                .padding(10.dp)
                                .testTag("node_${node.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            val resolvedAlignment = when (node.textAlign?.lowercase()) {
                                "left" -> TextAlign.Left
                                "right" -> TextAlign.Right
                                "justify" -> TextAlign.Justify
                                else -> TextAlign.Center
                            }
                            val resolvedHorizontalAlignment = when (node.textAlign?.lowercase()) {
                                "left" -> Alignment.Start
                                "right" -> Alignment.End
                                else -> Alignment.CenterHorizontally
                            }
                            Column(
                                horizontalAlignment = resolvedHorizontalAlignment,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Selected icon render
                                node.iconName?.let { iconName ->
                                    val nodeIcon = when (iconName) {
                                        "Star" -> Icons.Default.Star
                                        "Idea" -> Icons.Default.Lightbulb
                                        "Task" -> Icons.Default.CheckCircle
                                        "Alert" -> Icons.Default.Warning
                                        "Info" -> Icons.Default.Info
                                        "Heart" -> Icons.Default.Favorite
                                        "Flag" -> Icons.Default.Flag
                                        "Bookmark" -> Icons.Default.Bookmark
                                        else -> null
                                    }
                                    if (nodeIcon != null) {
                                        Icon(
                                            imageVector = nodeIcon,
                                            contentDescription = iconName,
                                            tint = textColor,
                                            modifier = Modifier.size(16.dp).padding(bottom = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = node.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (depth == 0) 12.sp else 11.sp,
                                    color = textColor,
                                    textAlign = resolvedAlignment,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (node.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = node.description,
                                        fontSize = 8.sp,
                                        color = textColor.copy(alpha = 0.8f),
                                        textAlign = resolvedAlignment,
                                        lineHeight = 10.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Render Note indicator badge
                                if (!node.note.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .background(textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = "Memiliki Catatan",
                                            tint = textColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Catatan",
                                            fontSize = 7.sp,
                                            color = textColor.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Interactive Expand/Collapse Toggle Button for parent nodes
                                if (node.children.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(borderColor.copy(alpha = 0.15f))
                                            .clickable {
                                                saveToHistory()
                                                val updatedTree = toggleCollapseInTree(rootNode, node.id)
                                                commitTree(updatedTree)
                                            }
                                            .testTag("collapse_node_${node.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (node.isCollapsed) Icons.Default.Add else Icons.Default.Remove,
                                            contentDescription = if (node.isCollapsed) "Expand" else "Collapse",
                                            tint = borderColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Resize drag handles shown ONLY when resizing this node
                        if (isResizingThisNode) {
                            // RIGHT HANDLE
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(node.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.x / density.density
                                            val currentWidth = node.customWidth ?: nodeW
                                            val newWidth = (currentWidth + dragAmountDp * 2).coerceIn(80f, 400f)
                                            node.customWidth = newWidth
                                            val updatedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                            onTreeChanged(updatedTree)
                                        }
                                    }
                            )
                            // LEFT HANDLE
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(node.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.x / density.density
                                            val currentWidth = node.customWidth ?: nodeW
                                            val newWidth = (currentWidth - dragAmountDp * 2).coerceIn(80f, 400f)
                                            node.customWidth = newWidth
                                            val updatedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                            onTreeChanged(updatedTree)
                                        }
                                    }
                            )
                            // BOTTOM HANDLE
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(node.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.y / density.density
                                            val currentHeight = node.customHeight ?: nodeH
                                            val newHeight = (currentHeight + dragAmountDp * 2).coerceIn(40f, 400f)
                                            node.customHeight = newHeight
                                            val updatedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                            onTreeChanged(updatedTree)
                                        }
                                    }
                            )
                            // TOP HANDLE
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(node.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dragAmountDp = dragAmount.y / density.density
                                            val currentHeight = node.customHeight ?: nodeH
                                            val newHeight = (currentHeight - dragAmountDp * 2).coerceIn(40f, 400f)
                                            node.customHeight = newHeight
                                            val updatedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                                            onTreeChanged(updatedTree)
                                        }
                                    }
                            )
                            // DONE/CLOSE BUTTON
                            IconButton(
                                onClick = { resizingNodeId = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-10).dp)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selesai",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // TOP CONTROL BAR (Undo, Redo, Auto-Align)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo Button
            IconButton(
                onClick = { performUndo() },
                enabled = undoStack.isNotEmpty(),
                modifier = Modifier.size(28.dp).testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Redo Button
            IconButton(
                onClick = { performRedo() },
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.size(28.dp).testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .height(16.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            // Auto-Align Button
            IconButton(
                onClick = {
                    saveToHistory()
                    subtreeWidths.clear()
                    nodeCoords.clear()
                    computeWidths(rootNode)
                    layoutTree(rootNode, 0, 100f)
                    val alignedTree = mergeCoordsIntoTree(rootNode, nodeCoords.toMap())
                    onTreeChanged(alignedTree)
                    MainViewModel.instance?.showToastPopup("Peta konsep berhasil dirapikan secara otomatis!")
                },
                modifier = Modifier.size(28.dp).testTag("auto_align_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = "Rapikan Node (Auto-Align)",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // FLOATING CANVAS CONTROLS (Bottom Right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { scale = (scale + 0.15f).coerceAtMost(3.0f) },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = {
                    val oldScale = scale
                    val newScale = (scale - 0.15f).coerceAtLeast(0.1f)
                    if (nodeCoords.isNotEmpty()) {
                        var minX = Float.MAX_VALUE
                        var maxX = Float.MIN_VALUE
                        var minY = Float.MAX_VALUE
                        var maxY = Float.MIN_VALUE
                        nodeCoords.forEach { (_, relativeOffset) ->
                            val left = relativeOffset.x
                            val right = relativeOffset.x
                            val top = relativeOffset.y
                            val bottom = relativeOffset.y
                            if (left < minX) minX = left
                            if (right > maxX) maxX = right
                            if (top < minY) minY = top
                            if (bottom > maxY) maxY = bottom
                        }
                        val midXDp = (minX + maxX) / 2f
                        val midYDp = (minY + maxY) / 2f
                        val densityVal = density.density
                        val mPx = Offset(midXDp * densityVal, midYDp * densityVal)
                        val vPx = Offset((viewportWidthDp / 2f) * densityVal, (viewportHeightDp / 2f) * densityVal)
                        
                        scale = newScale
                        offset = (vPx - mPx) * newScale
                    } else {
                        scale = newScale
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
            }
            FloatingActionButton(
                onClick = {
                    zoomToFitAllNodes()
                    MainViewModel.instance?.showToastPopup("Peta konsep dipusatkan dan disesuaikan dengan layar!")
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.ZoomOutMap, contentDescription = "Sesuaikan Layar", modifier = Modifier.size(20.dp))
            }
        }
    }

    // --- NODE ACTION DIALOG (UNIFIED EDIT, THEME, ADD, DELETE) ---
    selectedNodeForAction?.let { node ->
        Dialog(onDismissRequest = { selectedNodeForAction = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Kelola & Edit Node",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Field 1: Title Input (Direct manual text change)
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Judul Label") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_node_title_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Field 2: Description Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Definisi/Deskripsi Singkat",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    val textToAppend = "• "
                                    editDesc = if (editDesc.isEmpty() || editDesc.endsWith("\n")) {
                                        editDesc + textToAppend
                                    } else {
                                        editDesc + "\n" + textToAppend
                                    }
                                },
                                label = { Text("• Bullet", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp).testTag("bullet_desc_button")
                            )
                            AssistChip(
                                onClick = {
                                    val lines = editDesc.split("\n")
                                    val nextNum = lines.count { it.trim().matches(Regex("^\\d+\\..*")) } + 1
                                    val textToAppend = "$nextNum. "
                                    editDesc = if (editDesc.isEmpty() || editDesc.endsWith("\n")) {
                                        editDesc + textToAppend
                                    } else {
                                        editDesc + "\n" + textToAppend
                                    }
                                },
                                label = { Text("1. Angka", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp).testTag("number_desc_button")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Tulis deskripsi atau daftar list di sini...") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_node_desc_input"),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 2,
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Alignment Options
                    Text(
                        text = "Rataan Teks (Alignment)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { editTextAlignDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_alignment_dropdown_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentAlign = editTextAlign ?: "Center"
                                    val (icon, label) = when (currentAlign) {
                                        "Left" -> Pair(Icons.Default.FormatAlignLeft, "Rata Kiri")
                                        "Right" -> Pair(Icons.Default.FormatAlignRight, "Rata Kanan")
                                        "Justify" -> Pair(Icons.Default.FormatAlignJustify, "Rata Kanan & Kiri")
                                        else -> Pair(Icons.Default.FormatAlignCenter, "Rata Tengah")
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Pilih Rataan Teks",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = editTextAlignDropdownExpanded,
                            onDismissRequest = { editTextAlignDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            val alignOptions = listOf(
                                Triple("Left", Icons.Default.FormatAlignLeft, "Rata Kiri"),
                                Triple("Center", Icons.Default.FormatAlignCenter, "Rata Tengah"),
                                Triple("Right", Icons.Default.FormatAlignRight, "Rata Kanan"),
                                Triple("Justify", Icons.Default.FormatAlignJustify, "Rata Kanan & Kiri")
                            )
                            alignOptions.forEach { (alignName, icon, label) ->
                                val isSelected = editTextAlign == alignName
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        editTextAlign = alignName
                                        editTextAlignDropdownExpanded = false
                                    }
                                )
                            }
                        }
                                     // Field 3: Color Theme Choice (For dynamic visual organization)
                    Text(
                        text = "Skema Warna Node",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { editColorDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_color_dropdown_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentTheme = editColorTheme ?: "Default"
                                    val themesList = listOf("Default", "Blue", "Green", "Orange", "Purple", "Red", "Teal")
                                    val themeLabels = mapOf(
                                        "Default" to "Bawaan (Default)",
                                        "Blue" to "Biru (Blue)",
                                        "Green" to "Hijau (Green)",
                                        "Orange" to "Oranye (Orange)",
                                        "Purple" to "Ungu (Purple)",
                                        "Red" to "Merah (Red)",
                                        "Teal" to "Teal"
                                    )
                                    val themeColors = mapOf(
                                        "Default" to MaterialTheme.colorScheme.outline,
                                        "Blue" to Color(0xFF2196F3),
                                        "Green" to Color(0xFF4CAF50),
                                        "Orange" to Color(0xFFFF9800),
                                        "Purple" to Color(0xFF9C27B0),
                                        "Red" to Color(0xFFF44336),
                                        "Teal" to Color(0xFF009688)
                                    )

                                    val presetColors = mapOf(
                                        "#E91E63" to Pair("Merah Muda (Pink)", Color(0xFFE91E63)),
                                        "#FF8A65" to Pair("Koral (Coral)", Color(0xFFFF8A65)),
                                        "#FBC02D" to Pair("Kuning (Yellow)", Color(0xFFFBC02D)),
                                        "#81C784" to Pair("Hijau Terang (Light Green)", Color(0xFF81C784)),
                                        "#26A69A" to Pair("Mint", Color(0xFF26A69A)),
                                        "#29B6F6" to Pair("Biru Terang (Light Blue)", Color(0xFF29B6F6)),
                                        "#5C6BC0" to Pair("Indigo", Color(0xFF5C6BC0)),
                                        "#AB47BC" to Pair("Ungu Terang (Light Purple)", Color(0xFFAB47BC)),
                                        "#8D6E63" to Pair("Cokelat (Brown)", Color(0xFF8D6E63)),
                                        "#78909C" to Pair("Abu-abu (Grey)", Color(0xFF78909C))
                                    )

                                    val (label, color) = when {
                                        themesList.contains(currentTheme) -> {
                                            Pair(themeLabels[currentTheme] ?: currentTheme, themeColors[currentTheme] ?: MaterialTheme.colorScheme.outline)
                                        }
                                        presetColors.containsKey(currentTheme) -> {
                                            presetColors[currentTheme]!!
                                        }
                                        else -> {
                                            val parsedColor = try {
                                                Color(android.graphics.Color.parseColor(currentTheme))
                                            } catch (e: Exception) {
                                                MaterialTheme.colorScheme.outline
                                            }
                                            Pair("Warna Kustom ($currentTheme)", parsedColor)
                                        }
                                    }

                                    // Color preview circle
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.2f))
                                            .border(2.dp, color, CircleShape)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Pilih Skema Warna",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = editColorDropdownExpanded,
                            onDismissRequest = { editColorDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 350.dp)
                        ) {
                            val themesList = listOf(
                                Triple("Default", "Bawaan (Default)", MaterialTheme.colorScheme.outline),
                                Triple("Blue", "Biru (Blue)", Color(0xFF2196F3)),
                                Triple("Green", "Hijau (Green)", Color(0xFF4CAF50)),
                                Triple("Orange", "Oranye (Orange)", Color(0xFFFF9800)),
                                Triple("Purple", "Ungu (Purple)", Color(0xFF9C27B0)),
                                Triple("Red", "Merah (Red)", Color(0xFFF44336)),
                                Triple("Teal", "Teal", Color(0xFF009688))
                            )
                            
                            Text(
                                text = "Tema Standar",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            themesList.forEach { (themeName, label, color) ->
                                val isSelected = editColorTheme == themeName
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(color.copy(alpha = 0.2f))
                                                .border(1.5.dp, color, CircleShape)
                                        )
                                    },
                                    onClick = {
                                        editColorTheme = themeName
                                        editColorDropdownExpanded = false
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                            val presetColorsList = listOf(
                                Triple("#E91E63", "Merah Muda (Pink)", Color(0xFFE91E63)),
                                Triple("#FF8A65", "Koral (Coral)", Color(0xFFFF8A65)),
                                Triple("#FBC02D", "Kuning (Yellow)", Color(0xFFFBC02D)),
                                Triple("#81C784", "Hijau Terang (Light Green)", Color(0xFF81C784)),
                                Triple("#26A69A", "Mint", Color(0xFF26A69A)),
                                Triple("#29B6F6", "Biru Terang (Light Blue)", Color(0xFF29B6F6)),
                                Triple("#5C6BC0", "Indigo", Color(0xFF5C6BC0)),
                                Triple("#AB47BC", "Ungu Terang (Light Purple)", Color(0xFFAB47BC)),
                                Triple("#8D6E63", "Cokelat (Brown)", Color(0xFF8D6E63)),
                                Triple("#78909C", "Abu-abu (Grey)", Color(0xFF78909C))
                            )

                            Text(
                                text = "Preset Kustom",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            presetColorsList.forEach { (hexStr, label, color) ->
                                val isSelected = editColorTheme == hexStr
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(color.copy(alpha = 0.2f))
                                                .border(1.5.dp, color, CircleShape)
                                        )
                                    },
                                    onClick = {
                                        editColorTheme = hexStr
                                        customHexInput = hexStr
                                        editColorDropdownExpanded = false
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                            val themesNames = themesList.map { it.first }
                            val presetHexes = presetColorsList.map { it.first }
                            val isCustomHexSelected = !themesNames.contains(editColorTheme) && !presetHexes.contains(editColorTheme)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Warna Kustom (Kode HEX)...",
                                        fontWeight = if (isCustomHexSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = "Custom HEX",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    val currentHex = if (customHexInput.startsWith("#") && customHexInput.length >= 4) {
                                        customHexInput
                                    } else {
                                        "#3F51B5"
                                    }
                                    editColorTheme = currentHex
                                    customHexInput = currentHex
                                    editColorDropdownExpanded = false
                                }
                            )
                        }
                    }

                    // HEX Input Field (Shown only if a custom non-theme color is selected)
                    val themesNames = listOf("Default", "Blue", "Green", "Orange", "Purple", "Red", "Teal")
                    val isThemeSelected = themesNames.contains(editColorTheme)
                    if (!isThemeSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customHexInput,
                                onValueChange = { input ->
                                    customHexInput = input
                                    val cleanInput = if (input.startsWith("#")) input else "#$input"
                                    try {
                                        android.graphics.Color.parseColor(cleanInput)
                                        editColorTheme = cleanInput
                                    } catch (e: Exception) {
                                        // keep input text, but do not set editColorTheme yet if invalid
                                    }
                                },
                                label = { Text("Kode HEX Warna Kustom") },
                                placeholder = { Text("#RRGGBB") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            
                            // Live color preview circle
                            val previewColor = try {
                                val cleanInput = if (customHexInput.startsWith("#")) customHexInput else "#$customHexInput"
                                Color(android.graphics.Color.parseColor(cleanInput))
                            } catch (e: Exception) {
                                Color.Transparent
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (previewColor != Color.Transparent) previewColor else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (previewColor != Color.Transparent) {
                                        val r = previewColor.red
                                        val g = previewColor.green
                                        val b = previewColor.blue
                                        if (0.299f * r + 0.587f * g + 0.114f * b > 0.5f) Color.Black else Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }     }

                    // Field 4: Icon Selection
                    Text(
                        text = "Kategori / Simbol Visual (Ikon)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Option for No Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (editIconName == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { editIconName = null }
                                .testTag("icon_none"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "X",
                                color = if (editIconName == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        val availableIcons = listOf(
                            "Star" to Icons.Default.Star,
                            "Idea" to Icons.Default.Lightbulb,
                            "Task" to Icons.Default.CheckCircle,
                            "Alert" to Icons.Default.Warning,
                            "Info" to Icons.Default.Info,
                            "Heart" to Icons.Default.Favorite,
                            "Flag" to Icons.Default.Flag,
                            "Bookmark" to Icons.Default.Bookmark
                        )

                        availableIcons.forEach { (name, icon) ->
                            val isSelected = editIconName == name
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { editIconName = name }
                                    .testTag("icon_$name"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }



                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Expand/Collapse toggle in Manage Dialog
                    if (node.children.isNotEmpty()) {
                        Button(
                            onClick = {
                                saveToHistory()
                                val updatedTree = toggleCollapseInTree(rootNode, node.id)
                                commitTree(updatedTree)
                                selectedNodeForAction = null
                                MainViewModel.instance?.showToastPopup(if (node.isCollapsed) "Anak cabang berhasil dibuka!" else "Anak cabang berhasil disembunyikan!")
                            },
                            modifier = Modifier.fillMaxWidth().testTag("toggle_collapse_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (node.isCollapsed) Icons.Default.UnfoldMore else Icons.Default.UnfoldLess,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (node.isCollapsed) "Buka Semua Anak (Expand)" else "Sembunyikan Anak (Collapse)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Buttons Layout
                    OutlinedButton(
                        onClick = {
                            addChildTitle = ""
                            addChildDesc = ""
                            showAddChildDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("add_child_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Tambah Sub-Anak", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (node.id != rootNode.id) {
                        OutlinedButton(
                            onClick = {
                                showMoveParentDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("move_node_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CompareArrows,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Pindahkan Node (Ubah Induk / Level)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showSecondaryParentDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("manage_secondary_parents_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Hubungkan ke Induk Tambahan (Multi-Parent)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Simpan Perubahan Button (On the left)
                        Button(
                            onClick = {
                                if (editTitle.trim().isEmpty()) {
                                    MainViewModel.instance?.showToastPopup("Judul tidak boleh kosong!")
                                } else {
                                    saveToHistory()
                                    val updatedTree = editNodeInTree(
                                        rootNode,
                                        node.id,
                                        editTitle,
                                        editDesc,
                                        editColorTheme,
                                        newNote = node.note, // Preserve the existing note!
                                        newIconName = editIconName,
                                        newX = node.x,
                                        newY = node.y,
                                        newSecondaryParentIds = editSecondaryParentIds,
                                        newCustomWidth = node.customWidth, // Preserve existing customWidth!
                                        newCustomHeight = node.customHeight, // Preserve existing customHeight!
                                        newTextAlign = editTextAlign
                                    )
                                    commitTree(updatedTree)

                                    // Focus/Center viewport on the edited node!
                                    val targetCoord = nodeCoords[node.id]
                                    if (targetCoord != null) {
                                        val densityVal = density.density
                                        val nodePx = Offset(targetCoord.x * densityVal, targetCoord.y * densityVal)
                                        val vPx = Offset((activeViewportWidthDp / 2f) * densityVal, (activeViewportHeightDp / 2f) * densityVal)
                                        offset = (vPx - nodePx) * scale
                                    }

                                    selectedNodeForAction = null
                                    MainViewModel.instance?.showToastPopup("Perubahan berhasil disimpan!")
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("save_node_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Simpan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Batal Button (On the right)
                        OutlinedButton(
                            onClick = { selectedNodeForAction = null },
                            modifier = Modifier.weight(1f).testTag("cancel_node_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Batal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Hapus Button (Full width at the bottom)
                    val isRoot = node.id == rootNode.id
                    Button(
                        onClick = {
                            saveToHistory()
                            val updatedTree = deleteNodeRecursively(rootNode, node.id)
                            commitTree(updatedTree)
                            selectedNodeForAction = null
                            MainViewModel.instance?.showToastPopup("Node berhasil dihapus!")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("delete_node_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        enabled = !isRoot,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Hapus Node", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- NODE ADD CHILD MODAL DIALOG ---
    if (showAddChildDialog && selectedNodeForAction != null) {
        Dialog(onDismissRequest = { showAddChildDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val parentNode = selectedNodeForAction!!
                    val parentDepth = getNodeDepth(rootNode, parentNode.id)
                    val targetLevel = parentDepth + 1
                    val levelLabel = when (targetLevel) {
                        1 -> "Level 1 (Topik Utama)"
                        2 -> "Level 2 (Sub-Topik)"
                        3 -> "Level 3 (Detail Cabang)"
                        4 -> "Level 4 (Sub-Rincian)"
                        else -> "Level $targetLevel"
                    }

                    Column {
                        Text(
                            text = "Tambah Cabang Baru ($levelLabel)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Menambahkan sub-topik di bawah cabang: \"${parentNode.title}\"",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    OutlinedTextField(
                        value = addChildTitle,
                        onValueChange = { addChildTitle = it },
                        label = { Text("Judul Sub-Topik / Cabang Baru") },
                        modifier = Modifier.fillMaxWidth().testTag("add_child_title_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    OutlinedTextField(
                        value = addChildDesc,
                        onValueChange = { addChildDesc = it },
                        label = { Text("Keterangan / Definisi Singkat") },
                        modifier = Modifier.fillMaxWidth().testTag("add_child_desc_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddChildDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                if (addChildTitle.trim().isEmpty()) {
                                    MainViewModel.instance?.showToastPopup("Judul tidak boleh kosong!")
                                } else {
                                    saveToHistory()
                                    val newId = "node_${System.currentTimeMillis()}"
                                    val newChildNode = MindMapNode(
                                        id = newId,
                                        title = addChildTitle,
                                        description = addChildDesc
                                    )
                                    val updatedTree = addChildToNodeInTree(rootNode, selectedNodeForAction!!.id, newChildNode)
                                    commitTree(updatedTree)
                                    showAddChildDialog = false
                                    selectedNodeForAction = null
                                    MainViewModel.instance?.showToastPopup("Berhasil menambahkan sub-elemen!")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Tambah")
                        }
                    }
                }
            }
        }
    }

    // --- NODE MOVE PARENT DIALOG ---
    if (showMoveParentDialog && selectedNodeForAction != null) {
        val targetNode = selectedNodeForAction!!
        Dialog(onDismissRequest = { showMoveParentDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Pindahkan Hubungan Node",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Pilih topik/cabang baru yang akan menjadi induk dari \"${targetNode.title}\". Tindakan ini akan menggeser seluruh sub-cabang di bawahnya.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Get list of possible parent nodes (excluding itself and its descendants)
                    val candidates = remember(rootNode, targetNode) {
                        getPossibleParents(rootNode, targetNode.id)
                    }

                    if (candidates.isEmpty()) {
                        Text(
                            text = "Tidak ada cabang induk alternatif yang valid.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(candidates) { candidate ->
                                val depth = getNodeDepth(rootNode, candidate.id)
                                val levelText = when (depth) {
                                    0 -> "Root / Pusat"
                                    1 -> "Level 1 (Topik Utama)"
                                    2 -> "Level 2 (Sub-Topik)"
                                    3 -> "Level 3"
                                    4 -> "Level 4"
                                    else -> "Level $depth"
                                }
                                
                                val isCurrentParent = candidate.children.any { it.id == targetNode.id }
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isCurrentParent) {
                                            saveToHistory()
                                            val updatedTree = moveNodeInTree(rootNode, targetNode.id, candidate.id)
                                            commitTree(updatedTree)
                                            showMoveParentDialog = false
                                            selectedNodeForAction = null
                                             MainViewModel.instance?.showToastPopup("Node \"${targetNode.title}\" berhasil dipindahkan ke bawah \"${candidate.title}\"!")
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrentParent) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isCurrentParent) {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (isCurrentParent) {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = levelText,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        if (isCurrentParent) {
                                            Text(
                                                text = "Induk Aktif",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showMoveParentDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Batal")
                        }
                    }
                }
            }
        }
    }

    // --- NODE MULTI-PARENT / SECONDARY CONNECTIONS DIALOG ---
    if (showSecondaryParentDialog && selectedNodeForAction != null) {
        val targetNode = selectedNodeForAction!!
        // Keep a temporary local copy for unsaved dialog changes
        var tempParentIds by remember(targetNode) { 
            mutableStateOf(editSecondaryParentIds) 
        }

        Dialog(onDismissRequest = { showSecondaryParentDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = "Hubungan Induk Tambahan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hubungkan \"${targetNode.title}\" ke lebih dari satu induk. Garis putus-putus sekunder akan menghubungkan mereka di kanvas.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                    // Get possible secondary parents
                    val candidates = remember(rootNode, targetNode) {
                        getPossibleSecondaryParents(rootNode, targetNode)
                    }

                    if (candidates.isEmpty()) {
                        Text(
                            text = "Tidak ada cabang induk tambahan yang valid di peta konsep ini.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(candidates) { candidate ->
                                val depth = getNodeDepth(rootNode, candidate.id)
                                val levelText = when (depth) {
                                    0 -> "Root / Pusat"
                                    1 -> "Level 1 (Topik Utama)"
                                    2 -> "Level 2 (Sub-Topik)"
                                    3 -> "Level 3"
                                    4 -> "Level 4"
                                    else -> "Level $depth"
                                }
                                
                                val isSelected = tempParentIds.contains(candidate.id)
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            tempParentIds = if (isSelected) {
                                                tempParentIds.filter { it != candidate.id }
                                            } else {
                                                tempParentIds + candidate.id
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.title,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = levelText,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                tempParentIds = if (checked) {
                                                    tempParentIds + candidate.id
                                                } else {
                                                    tempParentIds.filter { it != candidate.id }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSecondaryParentDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                editSecondaryParentIds = tempParentIds
                                showSecondaryParentDialog = false
                                MainViewModel.instance?.showToastPopup("Hubungan berhasil diperbarui! Tekan 'Simpan Perubahan' untuk menyimpan.")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }

    // --- DOUBLE-TAP NOTE DIALOG ---
    if (doubleTappedNodeForNote != null) {
        Dialog(onDismissRequest = { doubleTappedNodeForNote = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Catatan Detail: ${doubleTappedNodeForNote!!.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {
                                    val textToAppend = "• "
                                    noteDialogText = if (noteDialogText.isEmpty() || noteDialogText.endsWith("\n")) {
                                        noteDialogText + textToAppend
                                    } else {
                                        noteDialogText + "\n" + textToAppend
                                    }
                                },
                                label = { Text("• Bullet", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp).testTag("bullet_popup_button")
                            )
                            AssistChip(
                                onClick = {
                                    val lines = noteDialogText.split("\n")
                                    val nextNum = lines.count { it.trim().matches(Regex("^\\d+\\..*")) } + 1
                                    val textToAppend = "$nextNum. "
                                    noteDialogText = if (noteDialogText.isEmpty() || noteDialogText.endsWith("\n")) {
                                        noteDialogText + textToAppend
                                    } else {
                                        noteDialogText + "\n" + textToAppend
                                    }
                                },
                                label = { Text("1. Angka", fontSize = 10.sp) },
                                modifier = Modifier.height(24.dp).testTag("number_popup_button")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteDialogText,
                        onValueChange = { noteDialogText = it },
                        label = { Text("Tulis catatan lengkap di sini...") },
                        modifier = Modifier.fillMaxWidth().height(180.dp).testTag("note_popup_input"),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 3,
                        maxLines = 15,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { doubleTappedNodeForNote = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                saveToHistory()
                                val updatedTree = editNodeInTree(
                                    rootNode,
                                    doubleTappedNodeForNote!!.id,
                                    doubleTappedNodeForNote!!.title,
                                    doubleTappedNodeForNote!!.description,
                                    doubleTappedNodeForNote!!.colorTheme,
                                    newNote = if (noteDialogText.isBlank()) null else noteDialogText,
                                    newIconName = doubleTappedNodeForNote!!.iconName,
                                    newX = doubleTappedNodeForNote!!.x,
                                    newY = doubleTappedNodeForNote!!.y,
                                    newSecondaryParentIds = doubleTappedNodeForNote!!.secondaryParentIds,
                                    newCustomWidth = doubleTappedNodeForNote!!.customWidth,
                                    newCustomHeight = doubleTappedNodeForNote!!.customHeight,
                                    newTextAlign = doubleTappedNodeForNote!!.textAlign
                                )
                                commitTree(updatedTree)
                                doubleTappedNodeForNote = null
                                MainViewModel.instance?.showToastPopup("Catatan berhasil diperbarui!")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

// --- TREE OPERATIONS HELPERS ---

private fun findNodeById(root: MindMapNode, id: String): MindMapNode? {
    if (root.id == id) return root
    root.children.forEach { child ->
        val found = findNodeById(child, id)
        if (found != null) return found
    }
    return null
}

private fun getNodeDepth(root: MindMapNode, targetId: String, currentDepth: Int = 0): Int {
    if (root.id == targetId) return currentDepth
    root.children.forEach { child ->
        val depth = getNodeDepth(child, targetId, currentDepth + 1)
        if (depth != -1) return depth
    }
    return -1
}

// Recursive edit node
private fun editNodeInTree(
    root: MindMapNode,
    targetId: String,
    newTitle: String,
    newDesc: String,
    newColorTheme: String?,
    newNote: String? = null,
    newIconName: String? = null,
    newX: Float? = null,
    newY: Float? = null,
    newSecondaryParentIds: List<String>? = null,
    newCustomWidth: Float? = null,
    newCustomHeight: Float? = null,
    newTextAlign: String? = null
): MindMapNode {
    if (root.id == targetId) {
        return root.copy(
            title = newTitle,
            description = newDesc,
            colorTheme = newColorTheme,
            note = newNote,
            iconName = newIconName,
            x = newX,
            y = newY,
            secondaryParentIds = newSecondaryParentIds ?: root.secondaryParentIds,
            customWidth = newCustomWidth,
            customHeight = newCustomHeight,
            textAlign = newTextAlign
        )
    }
    return root.copy(
        children = root.children.map { editNodeInTree(it, targetId, newTitle, newDesc, newColorTheme, newNote, newIconName, newX, newY, newSecondaryParentIds, newCustomWidth, newCustomHeight, newTextAlign) }
    )
}

private fun mergeCoordsIntoTree(root: MindMapNode, nodeCoords: Map<String, Offset>): MindMapNode {
    val coord = nodeCoords[root.id]
    val updatedChildren = root.children.map { mergeCoordsIntoTree(it, nodeCoords) }
    return root.copy(
        x = coord?.x,
        y = coord?.y,
        children = updatedChildren
    )
}

// Recursive add child
private fun addChildToNodeInTree(root: MindMapNode, targetId: String, newChild: MindMapNode): MindMapNode {
    if (root.id == targetId) {
        return root.copy(children = root.children + newChild)
    }
    return root.copy(
        children = root.children.map { addChildToNodeInTree(it, targetId, newChild) }
    )
}

// Recursive delete node
private fun deleteNodeRecursively(root: MindMapNode, targetId: String): MindMapNode {
    return root.copy(
        children = root.children
            .filter { it.id != targetId }
            .map { deleteNodeRecursively(it, targetId) }
    )
}

private fun getPossibleParents(root: MindMapNode, excludeNodeId: String): List<MindMapNode> {
    val list = mutableListOf<MindMapNode>()
    fun traverse(node: MindMapNode) {
        if (node.id == excludeNodeId) return
        list.add(node)
        node.children.forEach { traverse(it) }
    }
    traverse(root)
    return list
}

private fun getPossibleSecondaryParents(root: MindMapNode, node: MindMapNode): List<MindMapNode> {
    val list = mutableListOf<MindMapNode>()
    // Helper to find primary parent ID
    fun findPrimaryParentId(current: MindMapNode, targetId: String): String? {
        if (current.children.any { it.id == targetId }) return current.id
        current.children.forEach { child ->
            val found = findPrimaryParentId(child, targetId)
            if (found != null) return found
        }
        return null
    }
    val primaryParentId = findPrimaryParentId(root, node.id)

    // Helper to collect non-descendant candidates
    fun traverse(current: MindMapNode) {
        if (current.id == node.id) return // skip itself and descendants
        if (current.id != primaryParentId) {
            list.add(current)
        }
        current.children.forEach { traverse(it) }
    }
    traverse(root)
    return list
}

private fun moveNodeInTree(root: MindMapNode, nodeId: String, newParentId: String): MindMapNode {
    val treeWithoutNode = removeNodeFromTreeOnly(root, nodeId) ?: return root
    val nodeToMove = findNodeById(root, nodeId) ?: return root
    return addChildToNodeInTree(treeWithoutNode, newParentId, nodeToMove)
}

private fun removeNodeFromTreeOnly(root: MindMapNode, targetId: String): MindMapNode? {
    if (root.id == targetId) return null
    return root.copy(
        children = root.children
            .filter { it.id != targetId }
            .mapNotNull { removeNodeFromTreeOnly(it, targetId) }
    )
}

private fun toggleCollapseInTree(root: MindMapNode, targetId: String): MindMapNode {
    if (root.id == targetId) {
        return root.copy(isCollapsed = !root.isCollapsed)
    }
    return root.copy(
        children = root.children.map { toggleCollapseInTree(it, targetId) }
    )
}

// Format outlines with beautiful bullet points
private fun formatTextOutline(node: MindMapNode, level: Int): String {
    val indent = "   ".repeat(level)
    val bullet = when (level) {
        0 -> "📌"
        1 -> "🔹"
        else -> "▪️"
    }
    val details = if (node.description.isNotEmpty()) " - ${node.description}" else ""
    val currentLine = "$indent$bullet **${node.title}**$details\n"
    
    val childrenLines = node.children.joinToString("") { child ->
        formatTextOutline(child, level + 1)
    }
    return currentLine + childrenLines
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(date)
}

// --- GEMINI 3.5 API REST CALL WITH OKHTTP AND MOSHI ---
private suspend fun generateMindMapFromAI(
    context: Context,
    textMaterial: String,
    onComplete: (MindMapNode?) -> Unit
) {
    withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            withContext(Dispatchers.Main) {
                onComplete(null)
            }
            return@withContext
        }

        // Using gemini-3.5-flash as specified in skill guidance for basic/medium tasks
        val modelName = "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val prompt = """
            Analyze the following study material or topic and build a well-structured mind map representing a logical hierarchy of concepts.
            You MUST return a JSON object representing the root of the tree, matching EXACTLY the following recursive structure:
            {
              "title": "Main Topic Name",
              "id": "root",
              "description": "Short explanation of the main topic",
              "children": [
                {
                  "title": "Subtopic Title",
                  "id": "node_1",
                  "description": "Brief explanation",
                  "children": []
                }
              ]
            }
            Rules:
            - The maximum depth of the hierarchy is 3 levels.
            - Ensure every element has a unique 'id' (e.g. root, node_1, node_1_1, node_1_2, node_2).
            - Do not restrict word or sentence counts in node titles or descriptions, allowing for comprehensive definitions, clear details, and rich information.
            - Keep description fields detailed, educational, and complete.
            - You can use formatting elements like bullet list points (•) and numbering (e.g. 1., 2.) inside descriptions to present lists or structured sub-points cleanly.
            - Output valid JSON. Do not wrap the JSON inside markdown format (```json ... ```). Output the RAW JSON string directly.
            - The language of title and description should match the input language (Indonesian or English).

            Study material or Topic:
            $textMaterial
        """.trimIndent()

        val requestBodyJson = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": ${escapeJsonString(prompt)}
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val mediaType = "application/json".toMediaType()
        val requestBody = requestBodyJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
                return@withContext
            }

            val bodyString = response.body?.string() ?: ""
            // Parse response body
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            
            // Extract text from Gemini structure
            val responseMap = moshi.adapter(Map::class.java).fromJson(bodyString)
            val candidates = responseMap?.get("candidates") as? List<*>
            val firstCandidate = candidates?.getOrNull(0) as? Map<*, *>
            val content = firstCandidate?.get("content") as? Map<*, *>
            val parts = content?.get("parts") as? List<*>
            val firstPart = parts?.getOrNull(0) as? Map<*, *>
            var rawText = firstPart?.get("text") as? String ?: ""

            // Sanitize in case Gemini wrapped it in ```json ... ``` despite instructions
            rawText = rawText.trim()
            if (rawText.startsWith("```")) {
                rawText = rawText.substringAfter("```json").substringAfter("```")
                rawText = rawText.substringBeforeLast("```")
            }
            rawText = rawText.trim()

            val nodeResult = moshi.adapter(MindMapNode::class.java).fromJson(rawText)
            withContext(Dispatchers.Main) {
                onComplete(nodeResult)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onComplete(null)
            }
        }
    }
}

// Simple JSON escaper
private fun escapeJsonString(string: String): String {
    val builder = StringBuilder()
    builder.append("\"")
    for (element in string) {
        when (element) {
            '\\' -> builder.append("\\\\")
            '\"' -> builder.append("\\\"")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> {
                if (element < ' ') {
                    val code = "000${Integer.toHexString(element.code)}"
                    builder.append("\\u").append(code.substring(code.length - 4))
                } else {
                    builder.append(element)
                }
            }
        }
    }
    builder.append("\"")
    return builder.toString()
}

private fun splitTextIntoWrappedLines(text: String, maxCharsPerLine: Int): List<String> {
    if (text.isEmpty()) return emptyList()
    val paragraphs = text.split("\n")
    val allLines = mutableListOf<String>()
    for (paragraph in paragraphs) {
        val words = paragraph.split(" ")
        var currentLine = StringBuilder()
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                currentLine.append(" ").append(word)
            } else {
                allLines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            allLines.add(currentLine.toString())
        } else if (paragraphs.size > 1) {
            allLines.add("") // preserve empty lines inside paragraphs
        }
    }
    return allLines
}

private suspend fun executeExport(
    context: Context,
    rootNode: MindMapNode,
    format: String,
    orientation: String,
    hierarchyTheme: String
) = withContext(Dispatchers.Default) {
    try {
        val densityVal = context.resources.displayMetrics.density
        val bitmap = captureMindMapToBitmap(
            context = context,
            rootNode = rootNode,
            baseDensity = densityVal,
            hierarchyTheme = hierarchyTheme,
            orientation = orientation
        )

        if (format == "png") {
            val file = File(context.cacheDir, "peta_konsep_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(intent, "Bagikan Gambar Peta Konsep (PNG)").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                MainViewModel.instance?.showToastPopup("Gambar PNG ($orientation) berhasil dibuat!")
            }
        } else {
            val pdfDocument = android.graphics.pdf.PdfDocument()

            val pageWidthPt: Int
            val pageHeightPt: Int

            when (orientation.lowercase()) {
                "landscape" -> {
                    pageWidthPt = 842
                    pageHeightPt = 595
                }
                "portrait" -> {
                    pageWidthPt = 595
                    pageHeightPt = 842
                }
                else -> {
                    if (bitmap.width >= bitmap.height) {
                        pageWidthPt = 842
                        pageHeightPt = (842f * bitmap.height / bitmap.width).toInt().coerceAtLeast(300)
                    } else {
                        pageHeightPt = 842
                        pageWidthPt = (842f * bitmap.width / bitmap.height).toInt().coerceAtLeast(300)
                    }
                }
            }

            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val marginPt = 24f
            val availableWidth = pageWidthPt - (marginPt * 2f)
            val availableHeight = pageHeightPt - (marginPt * 2f)

            val scaleX = availableWidth / bitmap.width
            val scaleY = availableHeight / bitmap.height
            val scale = minOf(scaleX, scaleY)

            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale

            val left = (pageWidthPt - drawW) / 2f
            val top = (pageHeightPt - drawH) / 2f

            val destRect = RectF(left, top, left + drawW, top + drawH)

            val renderPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                isDither = true
            }

            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.drawBitmap(bitmap, null, destRect, renderPaint)
            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "peta_konsep_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) {
                val chooser = Intent.createChooser(intent, "Bagikan PDF Peta Konsep").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                MainViewModel.instance?.showToastPopup("Dokumen PDF ($orientation) berhasil dibuat!")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            MainViewModel.instance?.showToastPopup("Gagal mengekspor: ${e.localizedMessage}")
        }
    }
}

@Composable
private fun ExportOrientationDialog(
    format: String,
    onDismiss: () -> Unit,
    onConfirm: (orientation: String) -> Unit
) {
    var selectedOrientation by remember { mutableStateOf("landscape") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (format == "pdf") Icons.Default.PictureAsPdf else Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (format == "pdf") "Ekspor Dokumen (PDF)" else "Ekspor Gambar (PNG)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pilih orientasi halaman agar peta konsep pas, tidak terpotong, dan tajam (HD):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val options = listOf(
                    Triple(
                        "landscape",
                        "Lansekap (Mendatar)",
                        "Sangat direkomendasikan untuk peta konsep bercabang lebar agar tidak terpotong."
                    ),
                    Triple(
                        "portrait",
                        "Potret (Tegak)",
                        "Format tegak untuk tampilan dokumen vertikal atau cetak standar."
                    ),
                    Triple(
                        "auto",
                        "Otomatis (Sesuai Ukuran)",
                        "Menyesuaikan rasio lebar dan tinggi asli dari peta konsep."
                    )
                )

                options.forEach { (key, title, desc) ->
                    val isSelected = selectedOrientation == key
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOrientation = key },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedOrientation = key }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedOrientation) },
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(text = "Simpan & Bagikan", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Batal")
            }
        }
    )
}

private fun captureMindMapToBitmap(
    context: Context,
    rootNode: MindMapNode,
    baseDensity: Float,
    hierarchyTheme: String = "Default",
    orientation: String = "landscape"
): Bitmap {
    var density = (baseDensity * 2.0f).coerceIn(2.5f, 4.0f)
    val subtreeWidths = mutableMapOf<String, Float>()
    val nodeCoords = mutableMapOf<String, Offset>()

    fun computeWidths(node: MindMapNode): Float {
        val nodeW = getNodeWidthAndHeight(node, 0).first
        val horizontalMargin = 40f + (node.description.length * 0.8f).coerceAtMost(120f)
        val defaultW = nodeW + horizontalMargin
        val w = if (node.children.isEmpty() || node.isCollapsed) {
            defaultW
        } else {
            var sum = 0f
            node.children.forEach { sum += computeWidths(it) }
            sum.coerceAtLeast(defaultW)
        }
        subtreeWidths[node.id] = w
        return w
    }

    fun layoutTree(node: MindMapNode, depth: Int, leftBoundary: Float, parentY: Float = 80f, parentHeight: Float = 0f) {
        val (nodeW, nodeH) = getNodeWidthAndHeight(node, depth)
        val horizontalMargin = 40f + (node.description.length * 0.8f).coerceAtMost(120f)
        val defaultW = nodeW + horizontalMargin
        val width = subtreeWidths[node.id] ?: defaultW
        
        val baseGap = 120f
        val descExtraGap = (node.description.length * 0.8f).coerceAtMost(100f)
        val verticalGap = baseGap + descExtraGap
        
        val y = if (depth == 0) {
            80f
        } else {
            parentY + (parentHeight / 2f) + verticalGap + (nodeH / 2f)
        }

        if (node.children.isEmpty() || node.isCollapsed) {
            val x = leftBoundary + width / 2
            nodeCoords[node.id] = Offset(x, y)
        } else {
            var currentLeft = leftBoundary
            val childXCoords = mutableListOf<Float>()
            node.children.forEach { child ->
                val childW = getNodeWidthAndHeight(child, depth + 1).first
                val childMargin = 40f + (child.description.length * 0.8f).coerceAtMost(120f)
                val childWidth = subtreeWidths[child.id] ?: (childW + childMargin)
                layoutTree(child, depth + 1, currentLeft, parentY = y, parentHeight = nodeH)
                val childCoord = nodeCoords[child.id]!!
                childXCoords.add(childCoord.x)
                currentLeft += childWidth
            }
            val parentX = (childXCoords.first() + childXCoords.last()) / 2
            nodeCoords[node.id] = Offset(parentX, y)
        }
    }

    computeWidths(rootNode)
    layoutTree(rootNode, 0, 50f)

    fun findNodeById(root: MindMapNode, id: String): MindMapNode? {
        if (root.id == id) return root
        root.children.forEach { child ->
            val found = findNodeById(child, id)
            if (found != null) return found
        }
        return null
    }

    fun getNodeDepth(root: MindMapNode, targetId: String, currentDepth: Int = 0): Int {
        if (root.id == targetId) return currentDepth
        root.children.forEach { child ->
            val depth = getNodeDepth(child, targetId, currentDepth + 1)
            if (depth != -1) return depth
        }
        return -1
    }

    var minNodeLeft = Float.MAX_VALUE
    var maxNodeRight = -Float.MAX_VALUE
    var minNodeTop = Float.MAX_VALUE
    var maxNodeBottom = -Float.MAX_VALUE

    nodeCoords.forEach { (nodeId, centerOffset) ->
        val node = findNodeById(rootNode, nodeId)
        if (node != null) {
            val depth = getNodeDepth(rootNode, nodeId)
            val (nodeW, nodeH) = getNodeWidthAndHeight(node, depth)
            val left = centerOffset.x - nodeW / 2f
            val right = centerOffset.x + nodeW / 2f
            val top = centerOffset.y - nodeH / 2f
            val bottom = centerOffset.y + nodeH / 2f

            if (left < minNodeLeft) minNodeLeft = left
            if (right > maxNodeRight) maxNodeRight = right
            if (top < minNodeTop) minNodeTop = top
            if (bottom > maxNodeBottom) maxNodeBottom = bottom
        }
    }

    if (nodeCoords.isEmpty() || minNodeLeft == Float.MAX_VALUE) {
        minNodeLeft = 0f; maxNodeRight = 400f; minNodeTop = 0f; maxNodeBottom = 300f
    }

    val paddingX = 80f
    val paddingY = 80f

    val rawWidthDp = (maxNodeRight - minNodeLeft) + (paddingX * 2f)
    val rawHeightDp = (maxNodeBottom - minNodeTop) + (paddingY * 2f)

    var extraPaddingX = 0f
    var extraPaddingY = 0f

    when (orientation.lowercase()) {
        "landscape" -> {
            val targetRatio = 1.414f
            val currentRatio = rawWidthDp / rawHeightDp
            if (currentRatio < targetRatio) {
                val neededWidth = rawHeightDp * targetRatio
                extraPaddingX = (neededWidth - rawWidthDp) / 2f
            }
        }
        "portrait" -> {
            val targetRatio = 0.707f
            val currentRatio = rawWidthDp / rawHeightDp
            if (currentRatio > targetRatio) {
                val neededHeight = rawWidthDp / targetRatio
                extraPaddingY = (neededHeight - rawHeightDp) / 2f
            }
        }
        else -> {
            // Auto aspect ratio
        }
    }

    val finalPaddingX = paddingX + extraPaddingX
    val finalPaddingY = paddingY + extraPaddingY

    val totalWidthDp = (maxNodeRight - minNodeLeft) + (finalPaddingX * 2f)
    val totalHeightDp = (maxNodeBottom - minNodeTop) + (finalPaddingY * 2f)

    var widthPx = (totalWidthDp * density).toInt().coerceAtLeast(600)
    var heightPx = (totalHeightDp * density).toInt().coerceAtLeast(400)

    val maxDimensionPx = 3840
    if (widthPx > maxDimensionPx || heightPx > maxDimensionPx) {
        val maxCurr = maxOf(widthPx, heightPx).toFloat()
        val scale = maxDimensionPx.toFloat() / maxCurr
        widthPx = (widthPx * scale).toInt().coerceAtLeast(600)
        heightPx = (heightPx * scale).toInt().coerceAtLeast(400)
        density *= scale
    }

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    canvas.drawColor(android.graphics.Color.parseColor("#FAFBFD"))

    val linePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#1976D2")
        strokeWidth = 3f * density
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    fun toPixelOffset(offset: Offset): Offset {
        val relativeX = offset.x - minNodeLeft + finalPaddingX
        val relativeY = offset.y - minNodeTop + finalPaddingY
        return Offset(relativeX * density, relativeY * density)
    }

    nodeCoords.forEach { (nodeId, startOffset) ->
        val node = findNodeById(rootNode, nodeId)
        node?.children?.forEach { child ->
            val endOffset = nodeCoords[child.id]
            if (endOffset != null) {
                val startPx = toPixelOffset(startOffset)
                val endPx = toPixelOffset(endOffset)
                val verticalMid = (startPx.y + endPx.y) / 2

                val path = android.graphics.Path().apply {
                    moveTo(startPx.x, startPx.y)
                    cubicTo(
                        startPx.x, verticalMid,
                        endPx.x, verticalMid,
                        endPx.x, endPx.y
                    )
                }
                canvas.drawPath(path, linePaint)
            }
        }

        // Draw secondary parent lines on exported image/PDF
        node?.secondaryParentIds?.forEach { parentId ->
            val parentOffset = nodeCoords[parentId]
            if (parentOffset != null) {
                val parentPx = toPixelOffset(parentOffset)
                val childPx = toPixelOffset(startOffset)
                val verticalMid = (parentPx.y + childPx.y) / 2

                val path = android.graphics.Path().apply {
                    moveTo(parentPx.x, parentPx.y)
                    cubicTo(
                        parentPx.x, verticalMid,
                        childPx.x, verticalMid,
                        childPx.x, childPx.y
                    )
                }
                val secondaryPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#009688")
                    strokeWidth = 2f * density
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawPath(path, secondaryPaint)
            }
        }
    }

    nodeCoords.forEach { (nodeId, relativeOffset) ->
        val node = findNodeById(rootNode, nodeId)
        if (node != null) {
            val depth = getNodeDepth(rootNode, nodeId)
            val pxOffset = toPixelOffset(relativeOffset)

            val resolved = resolveNodeColors(
                theme = node.colorTheme,
                depth = depth,
                primary = Color(0xFF1976D2),
                secondaryContainer = Color(0xFFE3F2FD),
                tertiaryContainer = Color(0xFFF5F5F5),
                onPrimary = Color(0xFFFFFFFF),
                onSecondaryContainer = Color(0xFF0D47A1),
                onTertiaryContainer = Color(0xFF333333),
                outline = Color(0xFFBDBDBD),
                onSurface = Color(0xFF212121),
                hierarchyTheme = hierarchyTheme
            )
            val containerColorInt = resolved.containerColor.toArgb()
            val textColorInt = resolved.textColor.toArgb()
            val borderColorInt = resolved.borderColor.toArgb()

            val (nodeW, nodeH) = getNodeWidthAndHeight(node, depth)
            val cardWidthPx = nodeW * density
            val cardHeightPx = nodeH * density
            val left = pxOffset.x - cardWidthPx / 2
            val top = pxOffset.y - cardHeightPx / 2
            val right = pxOffset.x + cardWidthPx / 2
            val bottom = pxOffset.y + cardHeightPx / 2

            val cardRect = RectF(left, top, right, bottom)
            val cornerRadius = when (depth) {
                0 -> 16f * density
                1 -> 12f * density
                else -> 8f * density
            }

            val bgPaint = Paint().apply {
                color = containerColorInt
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint)

            val borderPaint = Paint().apply {
                color = borderColorInt
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

            val resolvedPaintAlign = when (node.textAlign?.lowercase()) {
                "left" -> Paint.Align.LEFT
                "right" -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            
            val drawTextX = when (resolvedPaintAlign) {
                Paint.Align.LEFT -> left + 12f * density
                Paint.Align.RIGHT -> right - 12f * density
                else -> pxOffset.x
            }

            val textPaint = Paint().apply {
                color = textColorInt
                isAntiAlias = true
                isSubpixelText = true
                textAlign = resolvedPaintAlign
                textSize = if (depth == 0) 12f * density else 11f * density
                isFakeBoldText = true
            }

            val titleLines = splitTextIntoWrappedLines(node.title, ((nodeW - 16f) / 5.5f).toInt().coerceAtLeast(10))
            val descLines = splitTextIntoWrappedLines(node.description, ((nodeW - 16f) / 4.2f).toInt().coerceAtLeast(15))
            
            val titleLineHeight = (if (depth == 0) 14f else 13f) * density
            val descLineHeight = 10.5f * density
            val spacing = 6f * density
            
            val totalTextHeight = (titleLines.size * titleLineHeight) + 
                                  (if (descLines.isNotEmpty()) spacing + (descLines.size * descLineHeight) else 0f)
            
            var currentY = pxOffset.y - (totalTextHeight / 2f) + (titleLineHeight / 2f) + (1.5f * density)
            
            titleLines.forEach { line ->
                canvas.drawText(line, drawTextX, currentY, textPaint)
                currentY += titleLineHeight
            }
            
            if (descLines.isNotEmpty()) {
                currentY -= titleLineHeight
                currentY += spacing + descLineHeight
                val descPaint = Paint().apply {
                    color = textColorInt
                    alpha = 180
                    isAntiAlias = true
                    isSubpixelText = true
                    textAlign = resolvedPaintAlign
                    textSize = 8.5f * density
                    isFakeBoldText = false
                }
                descLines.forEach { line ->
                    canvas.drawText(line, drawTextX, currentY, descPaint)
                    currentY += descLineHeight
                }
            }
        }
    }

    val footerPaint = Paint().apply {
        color = android.graphics.Color.GRAY
        alpha = 120
        textSize = 10f * density
        isAntiAlias = true
        isSubpixelText = true
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("Peta Konsep AI - ${rootNode.title}", widthPx - 16f * density, heightPx - 16f * density, footerPaint)

    return bitmap
}

@Composable
fun AllSavedMapsView(
    mindMaps: List<MindMap>,
    onBackClick: () -> Unit,
    onSelectMap: (MindMap) -> Unit,
    onDeleteMap: (MindMap) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMaps = remember(searchQuery, mindMaps) {
        if (searchQuery.trim().isEmpty()) {
            mindMaps
        } else {
            mindMaps.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Simple and elegant TopAppBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Semua Peta Konsep Saya",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${filteredMaps.size} Item",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari peta konsep...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus Pencarian",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(52.dp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        // List
        if (filteredMaps.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tidak Ada Hasil Ditemukan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Coba gunakan kata kunci pencarian yang lain.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredMaps) { map ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMap(map) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = map.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                val formattedDate = try {
                                    sdf.format(Date(map.createdAt))
                                } catch (e: Exception) {
                                    ""
                                }
                                Text(
                                    text = formattedDate,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeleteMap(map) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernAILoader(
    analysisTip: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AILoader")

    // 1. Rotation animation for outer orbit arcs
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 2. Counter-rotation for inner constellation structure
    val counterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterRotation"
    )

    // 3. Pulse animation for a breathing core and auras
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 4. Glow indicator fade cycle
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        // Futuristic radial ambient backing aura
        Box(
            modifier = Modifier
                .size(360.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * pulseScale),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f * pulseScale),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        Card(
            modifier = Modifier
                .width(300.dp)
                .padding(16.dp)
                .graphicsLayer {
                    shadowElevation = 24f
                    clip = true
                    shape = RoundedCornerShape(24.dp)
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.primary
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive Neural Map Orbit Centerpiece
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Static deep space ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.LightGray.copy(alpha = 0.12f),
                            radius = size.width / 2 - 4.dp.toPx(),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Rotating glowing orbits (Outer Structure)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = rotationAngle
                            }
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width / 2 - 4.dp.toPx()

                        // Cyan sweep active connection path
                        drawArc(
                            color = Color(0xFF03DAC6).copy(alpha = 0.8f),
                            startAngle = 0f,
                            sweepAngle = 75f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Deep violet/purple stream path
                        drawArc(
                            color = Color(0xFF6200EE).copy(alpha = 0.8f),
                            startAngle = 180f,
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Orbit satellites (simulating idea nodes)
                        drawCircle(
                            color = Color(0xFF6200EE),
                            radius = 4.5.dp.toPx(),
                            center = Offset(
                                x = center.x + radius * kotlin.math.cos(0.0).toFloat(),
                                y = center.y + radius * kotlin.math.sin(0.0).toFloat()
                            )
                        )

                        drawCircle(
                            color = Color(0xFF03DAC6),
                            radius = 3.5.dp.toPx(),
                            center = Offset(
                                x = center.x + radius * kotlin.math.cos(Math.PI).toFloat(),
                                y = center.y + radius * kotlin.math.sin(Math.PI).toFloat()
                            )
                        )
                    }

                    // Inner counter-rotating core structure
                    Canvas(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer {
                                rotationZ = counterRotationAngle
                            }
                    ) {
                        val radius = size.width / 2

                        // Inner dashed alignment guide
                        drawCircle(
                            color = Color(0xFFBB86FC).copy(alpha = 0.35f),
                            radius = radius - 6.dp.toPx(),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10f, 10f),
                                    0f
                                )
                            )
                        )

                        // Orbiting inner core idea node
                        drawCircle(
                            color = Color(0xFFBB86FC),
                            radius = 3.dp.toPx(),
                            center = Offset(
                                x = size.width / 2 + (radius - 6.dp.toPx()) * kotlin.math.cos(Math.PI / 4).toFloat(),
                                y = size.height / 2 + (radius - 6.dp.toPx()) * kotlin.math.sin(Math.PI / 4).toFloat()
                            )
                        )
                    }

                    // Pulsing Core AI Orb
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Modern AI Activation Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(100.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = "AI GENERATOR ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Membangun Peta Konsep...",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Smooth fade transition for status tip
                AnimatedContent(
                    targetState = analysisTip,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "TipTextTransition"
                ) { tipText ->
                    Text(
                        text = tipText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minimalist glow track indicator
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}
