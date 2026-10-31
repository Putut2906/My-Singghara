package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NoteItem
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// COLOR PALETTE & HELPERS FOR NOTES REDESIGN
val NOTE_COLORS = listOf(
    "#6366F1" to "Indigo",
    "#10B981" to "Emerald",
    "#F59E0B" to "Amber",
    "#EF4444" to "Coral",
    "#8B5CF6" to "Violet",
    "#06B6D4" to "Cyan",
    "#EC4899" to "Pink"
)

fun parseHexColor(hex: String?, defaultColor: Color = Color(0xFF6366F1)): Color {
    if (hex.isNullOrEmpty()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        defaultColor
    }
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffMinutes = diffMs / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Baru saja"
        diffMinutes < 60 -> "$diffMinutes mnt lalu"
        diffHours < 24 -> "$diffHours jam lalu"
        diffDays == 1L -> "Kemarin"
        diffDays < 7 -> "$diffDays hari lalu"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
            sdf.format(Date(timestamp))
        }
    }
}

fun shareNoteText(context: Context, title: String, content: String, viewModel: MainViewModel? = null) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Bagikan Catatan")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        (viewModel ?: MainViewModel.instance)?.showToastPopup("Gagal membagikan catatan")
    }
}

// DOCUMENT EXTRACTION UTILS
fun isValidDocTextLine(str: String): Boolean {
    val trimmed = str.trim()
    if (trimmed.length < 3) return false
    val letterCount = trimmed.count { it.isLetter() }
    return (letterCount.toFloat() / trimmed.length) >= 0.3f
}

fun isMetadataOrJunk(str: String): Boolean {
    val s = str.lowercase(Locale.getDefault())
    if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("uuid:") || s.startsWith("urn:")) return true
    val fonts = listOf("calibri", "arial", "times new roman", "courier new", "symbol", "wingdings", "cambria", "tahoma", "verdana", "georgia", "trebuchet", "garamond", "bookman", "consolas", "segoe", "helvetica", "ms gothic", "marlett")
    if (fonts.any { s == it }) return true
    val junkKeywords = listOf(
        "normal.dotm", "normal.dot", "microsoft word", "word.document", "msworddoc", 
        "title", "subject", "author", "keywords", "template", "last saved by", 
        "revision number", "total editing time", "create time", "last saved time", 
        "number of pages", "number of words", "number of characters", "security", 
        "company", "hyperlink base", "document parts", "heading pairs", 
        "excel.sheet", "powerpoint.show", "office", "content_types", "rels", "theme"
    )
    if (junkKeywords.any { s == it || s.contains(it) }) return true
    if (s.contains(":\\") || s.contains(":/") || s.contains("xml") || s.contains("xmlns") || s.contains(".dll") || s.contains(".exe") || s.contains(".dot")) return true
    return false
}

fun extractTextFromDocBytes(bytes: ByteArray): String {
    val list = mutableListOf<String>()
    var i = 0
    val n = bytes.size
    
    while (i < n) {
        var utf16Len = 0
        while (i + 2 * utf16Len + 1 < n) {
            val b1 = bytes[i + 2 * utf16Len].toInt() and 0xFF
            val b2 = bytes[i + 2 * utf16Len + 1].toInt() and 0xFF
            val isPrintable = (b1 in 32..126 || b1 == 10 || b1 == 13 || b1 == 9) && b2 == 0
            if (isPrintable) {
                utf16Len++
            } else {
                break
            }
        }
        
        if (utf16Len >= 4) {
            val strBytes = ByteArray(utf16Len * 2)
            System.arraycopy(bytes, i, strBytes, 0, utf16Len * 2)
            val str = String(strBytes, Charsets.UTF_16LE).trim()
            if (isValidDocTextLine(str)) {
                list.add(str)
            }
            i += utf16Len * 2
            continue
        }
        
        var ansiLen = 0
        while (i + ansiLen < n) {
            val b = bytes[i + ansiLen].toInt() and 0xFF
            val isPrintable = (b in 32..126) || (b in 160..255) || b == 10 || b == 13 || b == 9
            if (isPrintable) {
                ansiLen++
            } else {
                break
            }
        }
        
        if (ansiLen >= 6) {
            val strBytes = ByteArray(ansiLen)
            System.arraycopy(bytes, i, strBytes, 0, ansiLen)
            val str = String(strBytes, Charsets.ISO_8859_1).trim()
            if (isValidDocTextLine(str)) {
                list.add(str)
            }
            i += ansiLen
            continue
        }
        i++
    }
    
    val paragraphs = mutableListOf<String>()
    var currentParagraph = StringBuilder()
    
    for (line in list) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        if (isMetadataOrJunk(trimmed)) continue
        
        if (trimmed.length > 1) {
            if (currentParagraph.isNotEmpty() && (trimmed.startsWith(" ") || currentParagraph.endsWith(" "))) {
                currentParagraph.append(trimmed)
            } else {
                if (currentParagraph.isNotEmpty()) {
                    paragraphs.add(currentParagraph.toString())
                    currentParagraph = StringBuilder()
                }
                currentParagraph.append(trimmed)
            }
        }
    }
    if (currentParagraph.isNotEmpty()) {
        paragraphs.add(currentParagraph.toString())
    }
    
    return paragraphs.joinToString("\n\n")
}

fun extractTextFromUri(context: Context, uri: android.net.Uri, fileName: String): String {
    val extension = fileName.substringAfterLast(".", "").lowercase(Locale.getDefault())
    return when (extension) {
        "docx" -> {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var extractedText = ""
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val bytes = zipInputStream.readBytes()
                        val xml = String(bytes, Charsets.UTF_8)
                        val regex = "<w:t[^>]*>(.*?)</w:t>".toRegex()
                        val matches = regex.findAll(xml)
                        extractedText = matches.map { it.groupValues[1] }.joinToString(" ")
                        break
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
                extractedText
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        "odt" -> {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var extractedText = ""
                while (entry != null) {
                    if (entry.name == "content.xml") {
                        val bytes = zipInputStream.readBytes()
                        val xml = String(bytes, Charsets.UTF_8)
                        val regex = "<text:p[^>]*>(.*?)</text:p>".toRegex()
                        val matches = regex.findAll(xml)
                        extractedText = matches.map { it.groupValues[1].replace("<[^>]*>".toRegex(), "") }.joinToString("\n")
                        break
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.close()
                extractedText
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        "rtf" -> {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
                val rawText = inputStream.bufferedReader().use { it.readText() }
                rawText.replace("\\\\([a-z0-9*-]+)?".toRegex(), "").replace("[{}]".toRegex(), "").trim()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        "doc" -> {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
                val bytes = inputStream.readBytes()
                extractTextFromDocBytes(bytes)
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
        else -> {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }
}

fun openDocumentUri(context: Context, viewModel: MainViewModel, uri: android.net.Uri) {
    try {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var fileName = "Dokumen"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val extension = fileName.substringAfterLast(".", "").lowercase(Locale.getDefault())
        val supportedExtensions = listOf(
            "txt", "rtf", "docx", "doc", "odt", "csv", "json", "xml", "html", 
            "md", "py", "js", "java", "cpp", "c", "css", "pdf"
        )

        if (extension !in supportedExtensions) {
            viewModel.showToastPopup("Format file .$extension tidak didukung!")
            return
        }

        if (extension == "pdf") {
            viewModel.immersiveFileUriStr = uri.toString()
            viewModel.immersiveFileName = fileName
            viewModel.immersiveFileIsPdf = true
            viewModel.immersiveFileContent = ""
            viewModel.currentRoute = "immersive_file_viewer"
        } else {
            val content = extractTextFromUri(context, uri, fileName)
            viewModel.immersiveFileUriStr = uri.toString()
            viewModel.immersiveFileName = fileName
            viewModel.immersiveFileIsPdf = false
            viewModel.immersiveFileContent = content
            viewModel.currentRoute = "immersive_file_viewer"
        }
    } catch (e: Exception) {
        viewModel.showToastPopup("Gagal membuka file: ${e.localizedMessage}")
    }
}

fun writeToFileUri(context: Context, uriString: String?, content: String) {
    if (uriString.isNullOrEmpty()) return
    try {
        val uri = android.net.Uri.parse(uriString)
        context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(content)
            }
        }
    } catch (e: Exception) {
        try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    writer.write(content)
                }
            }
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

@Composable
fun CatatanScreen(viewModel: MainViewModel) {
    val notes by viewModel.notes.collectAsState()
    val activeNote = viewModel.selectedNote
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var editTitle by remember { mutableStateOf("") }
    var contentTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val editContent = contentTextFieldValue.text
    var editCategory by remember { mutableStateOf("Umum") }
    var editColorHex by remember { mutableStateOf("#6366F1") }
    var editIsPinned by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showSaveWarningDialog by remember { mutableStateOf(false) }

    var showNotesPopup by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var isGridView by remember { mutableStateOf(true) }

    var showNewNoteTemplateDialog by remember { mutableStateOf(false) }
    var showFileTypeDialog by remember { mutableStateOf(false) }
    var showColorPaletteDialog by remember { mutableStateOf(false) }
    var showMoreOptionsMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showFormatTextDialog by remember { mutableStateOf(false) }

    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    var feedbackBannerIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var feedbackBannerColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(feedbackBannerMessage) {
        if (feedbackBannerMessage != null) {
            delay(2800)
            feedbackBannerMessage = null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            openDocumentUri(context, viewModel, it)
        }
    }

    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    var isUndoingOrRedoing by remember { mutableStateOf(false) }
    var lastTypingTimestamp by remember { mutableLongStateOf(0L) }

    val onContentValueChange: (TextFieldValue) -> Unit = { newTfv ->
        if (!isUndoingOrRedoing) {
            val prevText = contentTextFieldValue.text
            val newText = newTfv.text

            if (prevText != newText) {
                hasUnsavedChanges = true
                viewModel.noteAutoSaveStatus = "Belum disimpan"

                val now = System.currentTimeMillis()
                val isTimeGap = (now - lastTypingTimestamp) > 600L
                val isBoundary = newText.isNotEmpty() && (newText.endsWith(" ") || newText.endsWith("\n") || newText.endsWith("."))
                val isMultiChar = Math.abs(newText.length - prevText.length) > 1

                if (undoStack.isEmpty() || isTimeGap || isBoundary || isMultiChar) {
                    if (undoStack.isEmpty() || undoStack.last().text != prevText) {
                        undoStack.add(contentTextFieldValue)
                        if (undoStack.size > 150) undoStack.removeAt(0)
                    }
                }
                lastTypingTimestamp = now
                redoStack.clear()
            }
        }
        contentTextFieldValue = newTfv
    }

    // Helper untuk menyisipkan teks tepat di posisi kursor
    val insertTextAtCursor: (String, Int) -> Unit = { insertion, selectionOffset ->
        val currentText = contentTextFieldValue.text
        val sel = contentTextFieldValue.selection
        val start = sel.min.coerceIn(0, currentText.length)
        val end = sel.max.coerceIn(0, currentText.length)

        if (undoStack.isEmpty() || undoStack.last().text != currentText) {
            undoStack.add(contentTextFieldValue)
        }
        redoStack.clear()

        val newText = currentText.substring(0, start) + insertion + currentText.substring(end)
        val newCursor = (start + selectionOffset).coerceIn(0, newText.length)

        contentTextFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
        hasUnsavedChanges = true
        viewModel.noteAutoSaveStatus = "Belum disimpan"
    }

    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    val performUndo = {
        if (undoStack.isNotEmpty()) {
            isUndoingOrRedoing = true
            val target = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(contentTextFieldValue)
            contentTextFieldValue = target
            hasUnsavedChanges = true
            viewModel.noteAutoSaveStatus = "Belum disimpan"
            isUndoingOrRedoing = false
        }
    }

    val performRedo = {
        if (redoStack.isNotEmpty()) {
            isUndoingOrRedoing = true
            val target = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(contentTextFieldValue)
            contentTextFieldValue = target
            hasUnsavedChanges = true
            viewModel.noteAutoSaveStatus = "Belum disimpan"
            isUndoingOrRedoing = false
        }
    }

    val saveCurrentNote: (() -> Unit) -> Unit = { onSuccess ->
        val current = activeNote
        if (current != null) {
            val currentContent = contentTextFieldValue.text
            val charCount = currentContent.length
            val lineCount = currentContent.split('\n').size
            val updatedNote = current.copy(
                title = editTitle.ifEmpty { "Catatan Tanpa Judul" },
                content = currentContent,
                charCount = charCount,
                lineCount = lineCount,
                lastSaved = System.currentTimeMillis(),
                category = editCategory,
                colorHex = editColorHex,
                isPinned = editIsPinned
            )
            viewModel.saveNote(updatedNote)
            if (!updatedNote.fileUri.isNullOrEmpty()) {
                writeToFileUri(context, updatedNote.fileUri, currentContent)
            }
            hasUnsavedChanges = false
            viewModel.noteAutoSaveStatus = "Tersimpan"
            feedbackBannerMessage = "Catatan \"${updatedNote.title}\" berhasil disimpan!"
            feedbackBannerIcon = Icons.Default.CheckCircle
            feedbackBannerColor = Color(0xFF10B981)
            onSuccess()
        }
    }

    // Intercept back OS Android saat sedang aktif membuka catatan
    BackHandler(enabled = activeNote != null) {
        if (hasUnsavedChanges) {
            showSaveWarningDialog = true
        } else {
            viewModel.selectedNote = null
        }
    }

    // Sync input when active note changes
    LaunchedEffect(activeNote?.id) {
        if (activeNote != null) {
            editTitle = activeNote.title
            contentTextFieldValue = TextFieldValue(
                text = activeNote.content,
                selection = TextRange(activeNote.content.length)
            )
            editCategory = activeNote.category.ifEmpty { "Umum" }
            editColorHex = activeNote.colorHex.ifEmpty { "#6366F1" }
            editIsPinned = activeNote.isPinned
            hasUnsavedChanges = false
            undoStack.clear()
            redoStack.clear()
            viewModel.noteAutoSaveStatus = "Tersimpan"
        } else {
            editTitle = ""
            contentTextFieldValue = TextFieldValue("")
            editCategory = "Umum"
            editColorHex = "#6366F1"
            editIsPinned = false
            hasUnsavedChanges = false
            undoStack.clear()
            redoStack.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("catatan_screen")
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeNote == null) {
                // HERO STATS & METRICS HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Total Notes Card with Quick Create Action
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showNewNoteTemplateDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                                    .testTag("add_note_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Buat Catatan Baru",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Catatan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${notes.size} Catatan", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // Pinned Notes Counter & Quick Filter Button
                    val pinnedCount = notes.count { it.isPinned }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedCategoryFilter = if (selectedCategoryFilter == "📌 Disematkan") "Semua" else "📌 Disematkan"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedCategoryFilter == "📌 Disematkan")
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selectedCategoryFilter == "📌 Disematkan") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Penting",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Sangat Penting", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$pinnedCount Disematkan", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECONDARY UTILITY BAR: BUKA DOKUMEN (PDF/TXT) & TATA LETAK TOGGLE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open document button
                    OutlinedButton(
                        onClick = { 
                            viewModel.preventAutoLock = true
                            filePickerLauncher.launch(arrayOf("application/pdf", "text/plain", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) 
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Dokumen / PDF", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showFileTypeDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Buat File Kode / Script",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // View mode toggle (Grid vs List)
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Ubah Tampilan",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // SEARCH BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari judul, kategori, atau isi...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Hapus", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                )

                // CATEGORY FILTER CHIPS SCROLLABLE ROW
                val categories = listOf("Semua", "📌 Disematkan", "📝 Umum", "💡 Ide", "💼 Pekerjaan", "☑️ Checklist", "💻 Kode")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategoryFilter == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryFilter = category },
                            label = { Text(category, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // MAIN CONTENT AREA: LIST / GRID VIEW OR EDITOR
            if (activeNote == null) {
                val filteredNotes = notes.filter { note ->
                    val matchesSearch = note.title.contains(searchQuery, ignoreCase = true) ||
                            note.content.contains(searchQuery, ignoreCase = true) ||
                            note.category.contains(searchQuery, ignoreCase = true)

                    val matchesCategory = when (selectedCategoryFilter) {
                        "Semua" -> true
                        "📌 Disematkan" -> note.isPinned
                        "📝 Umum" -> note.category == "Umum" || note.category.isEmpty()
                        "💡 Ide" -> note.category == "Ide"
                        "💼 Pekerjaan" -> note.category == "Pekerjaan"
                        "☑️ Checklist" -> note.category == "Checklist"
                        "💻 Kode" -> note.category == "Kode" || note.title.endsWith(".py") || note.title.endsWith(".js") || note.title.endsWith(".html") || note.title.endsWith(".json")
                        else -> note.category == selectedCategoryFilter
                    }
                    matchesSearch && matchesCategory
                }

                if (filteredNotes.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.StickyNote2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Catatan Tidak Ditemukan" else "Mulai Menulis Ide Pertama Anda!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Coba gunakan kata kunci pencarian atau kategori yang berbeda."
                                else "Belum ada catatan di kategori ini. Tekan tombol '+' di atas atau gunakan template siap pakai.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showNewNoteTemplateDialog = true },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buat Catatan Baru", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredNotes) { note ->
                                NoteCardGrid(
                                    note = note,
                                    onClick = {
                                        viewModel.selectedNote = note
                                        editTitle = note.title
                                        contentTextFieldValue = TextFieldValue(note.content, selection = TextRange(note.content.length))
                                        editCategory = note.category
                                        editColorHex = note.colorHex
                                        editIsPinned = note.isPinned
                                        hasUnsavedChanges = false
                                    },
                                    onPinToggle = {
                                        viewModel.togglePinNote(note)
                                        val statusStr = if (!note.isPinned) "Disematkan ke atas" else "Batal disematkan"
                                        feedbackBannerMessage = "\"${note.title}\" $statusStr"
                                        feedbackBannerIcon = Icons.Default.Bookmark
                                        feedbackBannerColor = Color(0xFFF59E0B)
                                    },
                                    onDelete = {
                                        noteToDelete = note
                                    },
                                    onShare = {
                                        shareNoteText(context, note.title, note.content, viewModel)
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredNotes) { note ->
                                NoteCardList(
                                    note = note,
                                    onClick = {
                                        viewModel.selectedNote = note
                                        editTitle = note.title
                                        contentTextFieldValue = TextFieldValue(note.content, selection = TextRange(note.content.length))
                                        editCategory = note.category
                                        editColorHex = note.colorHex
                                        editIsPinned = note.isPinned
                                        hasUnsavedChanges = false
                                    },
                                    onPinToggle = {
                                        viewModel.togglePinNote(note)
                                        val statusStr = if (!note.isPinned) "Disematkan ke atas" else "Batal disematkan"
                                        feedbackBannerMessage = "\"${note.title}\" $statusStr"
                                        feedbackBannerIcon = Icons.Default.Bookmark
                                        feedbackBannerColor = Color(0xFFF59E0B)
                                    },
                                    onDelete = {
                                        noteToDelete = note
                                    },
                                    onShare = {
                                        shareNoteText(context, note.title, note.content, viewModel)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // REDESIGNED IMMERSIVE EDITOR
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, parseHexColor(editColorHex).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        // EDITOR TOP BAR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (hasUnsavedChanges) {
                                        showSaveWarningDialog = true
                                    } else {
                                        viewModel.selectedNote = null
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("catatan_editor_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali ke Daftar",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            TextField(
                                value = editTitle,
                                onValueChange = {
                                    if (editTitle != it) {
                                        editTitle = it
                                        hasUnsavedChanges = true
                                        viewModel.noteAutoSaveStatus = "Belum disimpan"
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Beri judul catatan...",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                textStyle = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = true,
                                maxLines = 1,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // ACTION ICONS: SIMPAN, FILTER KATEGORI (SEBELAH KIRI ICON TITIK 3) & OVERFLOW MENU
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = { saveCurrentNote {} },
                                    modifier = Modifier.size(36.dp).testTag("catatan_save_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Simpan Catatan",
                                        tint = if (hasUnsavedChanges) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // 1. MENU LIST KATEGORI DENGAN ICON FILTER
                                Box {
                                    IconButton(
                                        onClick = { showCategoryMenu = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FilterList,
                                            contentDescription = "Pilih Kategori",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showCategoryMenu,
                                        onDismissRequest = { showCategoryMenu = false }
                                    ) {
                                        Text(
                                            text = "Kategori Catatan",
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        HorizontalDivider()

                                        val categoryList = listOf(
                                            Pair("Umum", "📝"),
                                            Pair("Ide", "💡"),
                                            Pair("Pekerjaan", "💼"),
                                            Pair("Checklist", "☑️"),
                                            Pair("Kode", "💻")
                                        )

                                        categoryList.forEach { (catName, emoji) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("$emoji $catName", fontWeight = if (editCategory == catName) FontWeight.Bold else FontWeight.Normal)
                                                        if (editCategory == catName) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    editCategory = catName
                                                    showCategoryMenu = false
                                                    feedbackBannerMessage = "Kategori diubah ke \"$catName\""
                                                    feedbackBannerIcon = Icons.Default.FilterList
                                                    feedbackBannerColor = Color(0xFF6366F1)
                                                }
                                            )
                                        }
                                    }
                                }

                                // 2. MENU ICON TITIK 3
                                Box {
                                    IconButton(
                                        onClick = { showMoreOptionsMenu = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Menu Pilihan Catatan",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMoreOptionsMenu,
                                        onDismissRequest = { showMoreOptionsMenu = false }
                                    ) {
                                        // A. SIMPAN CATATAN
                                        DropdownMenuItem(
                                            text = { Text("Simpan Catatan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Save,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                saveCurrentNote {}
                                            }
                                        )

                                        // B. SEMATKAN / BATAL SEMATKAN
                                        DropdownMenuItem(
                                            text = { Text(if (editIsPinned) "Batal Sematkan" else "Sematkan Catatan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (editIsPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    contentDescription = null,
                                                    tint = if (editIsPinned) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                editIsPinned = !editIsPinned
                                                feedbackBannerMessage = if (editIsPinned) "Catatan disematkan!" else "Batal disematkan"
                                                feedbackBannerIcon = Icons.Default.Bookmark
                                                feedbackBannerColor = Color(0xFFF59E0B)
                                            }
                                        )

                                        // C. UBAH WARNA
                                        DropdownMenuItem(
                                            text = { Text("Ubah Warna Catatan") },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(parseHexColor(editColorHex))
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                showColorPaletteDialog = true
                                            }
                                        )

                                        HorizontalDivider()

                                        // FORMAT TEKS LENGKAP POPUP MENU ITEM
                                        DropdownMenuItem(
                                            text = { Text("Format Teks / Sisipkan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.TextFields,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                showFormatTextDialog = true
                                            }
                                        )

                                        HorizontalDivider()

                                        // D. PRATINJAU HTML
                                        DropdownMenuItem(
                                            text = { Text("Pratinjau HTML") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                viewModel.notePreviewHtml = editContent
                                                viewModel.currentRoute = "preview_catatan"
                                            }
                                        )

                                        // E. BAGIKAN
                                        DropdownMenuItem(
                                            text = { Text("Bagikan Catatan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                shareNoteText(context, editTitle.ifEmpty { "Catatan" }, editContent, viewModel)
                                            }
                                        )

                                        // F. ARSIP CATATAN
                                        DropdownMenuItem(
                                            text = { Text("Arsip Catatan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                showNotesPopup = true
                                            }
                                        )

                                        // G. KEMBALI / TUTUP CATATAN
                                        DropdownMenuItem(
                                            text = { Text("Tutup Catatan") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                if (hasUnsavedChanges) {
                                                    showSaveWarningDialog = true
                                                } else {
                                                    viewModel.selectedNote = null
                                                }
                                            }
                                        )

                                        HorizontalDivider()

                                        // H. HAPUS CATATAN
                                        DropdownMenuItem(
                                            text = { Text("Hapus Catatan", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                showMoreOptionsMenu = false
                                                noteToDelete = activeNote
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // WRITING CANVAS FIELD
                        TextField(
                            value = contentTextFieldValue,
                            onValueChange = onContentValueChange,
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("catatan_content_field"),
                            placeholder = {
                                Text(
                                    text = "Tulis ide, draf jurnal, daftar tugas [ ], atau kode program Anda di sini...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                fontFamily = if (editCategory == "Kode" || editTitle.contains(".")) FontFamily.Monospace else FontFamily.Default
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // REALTIME FOOTER STATUS & UNDO/REDO/CLIPBOARD
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (hasUnsavedChanges) "● Belum disimpan" else "✓ Tersimpan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasUnsavedChanges) Color(0xFFD97706) else Color(0xFF059669)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // UNDO
                                IconButton(
                                    onClick = { performUndo() },
                                    enabled = canUndo,
                                    modifier = Modifier.size(28.dp).testTag("catatan_undo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Undo",
                                        tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // REDO
                                IconButton(
                                    onClick = { performRedo() },
                                    enabled = canRedo,
                                    modifier = Modifier.size(28.dp).testTag("catatan_redo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Redo,
                                        contentDescription = "Redo",
                                        tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // COPY
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(editContent))
                                        viewModel.showToastPopup("Catatan berhasil disalin!")
                                    },
                                    modifier = Modifier.size(28.dp).testTag("catatan_copy_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin Catatan",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // PASTE
                                IconButton(
                                    onClick = {
                                        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val primaryClip = sysClipboard?.primaryClip
                                        val osClipboardText = if (primaryClip != null && primaryClip.itemCount > 0) {
                                            primaryClip.getItemAt(0).text?.toString()
                                        } else null

                                        val finalPasteText = osClipboardText ?: clipboardManager.getText()?.text

                                        if (!finalPasteText.isNullOrEmpty()) {
                                            insertTextAtCursor(finalPasteText, finalPasteText.length)
                                            viewModel.showToastPopup("Teks berhasil ditempel di posisi kursor!")
                                        } else {
                                            viewModel.showToastPopup("Clipboard kosong!")
                                        }
                                    },
                                    modifier = Modifier.size(28.dp).testTag("catatan_paste_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Tempel Catatan",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            val wordsCount = if (editContent.trim().isEmpty()) 0 else editContent.trim().split("\\s+".toRegex()).size
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "$wordsCount kata",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${editContent.length} karakter",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // DIALOG POPUP: FORMAT TEKS / SISIPKAN
        if (showFormatTextDialog) {
            AlertDialog(
                onDismissRequest = { showFormatTextDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Format Teks & Sisipkan",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val formatOptions = listOf(
                            Triple("Judul (#)", "#", "Satu baris judul utama"),
                            Triple("Cetak Tebal (**)", "B", "Teks tebal"),
                            Triple("Cetak Miring (*)", "I", "Teks miring"),
                            Triple("Daftar Checklist ([ ])", "☑️", "Kotak centang tugas"),
                            Triple("Poin Bulat (•)", "•", "Daftar poin"),
                            Triple("Blok Kode (```)", "</>", "Blok format kode"),
                            Triple("Kutipan (>)", "”", "Blok teks kutipan"),
                            Triple("Cap Waktu (🕒)", "🕒", "Waktu & tanggal saat ini")
                        )

                        formatOptions.forEach { (title, symbol, desc) ->
                            Card(
                                onClick = {
                                    showFormatTextDialog = false
                                    when (symbol) {
                                        "#" -> insertTextAtCursor("\n# ", 3)
                                        "B" -> insertTextAtCursor("**teks**", 6)
                                        "I" -> insertTextAtCursor("*teks*", 5)
                                        "☑️" -> insertTextAtCursor("\n[ ] ", 5)
                                        "•" -> insertTextAtCursor("\n• ", 3)
                                        "</>" -> insertTextAtCursor("\n```\n\n```\n", 5)
                                        "”" -> insertTextAtCursor("\n> ", 3)
                                        "🕒" -> {
                                            val nowStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("id", "ID")).format(java.util.Date())
                                            val insertion = " $nowStr "
                                            insertTextAtCursor(insertion, insertion.length)
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = symbol,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFormatTextDialog = false }) {
                        Text("Tutup")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // DIALOG POPUP: PERINGATAN SIMPAN CATATAN SAAT KEMBALI
        if (showSaveWarningDialog) {
            AlertDialog(
                onDismissRequest = { showSaveWarningDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Simpan Catatan?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Anda memiliki perubahan catatan yang belum disimpan. Apakah Anda ingin menyimpan catatan sebelum kembali?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            saveCurrentNote {
                                showSaveWarningDialog = false
                                viewModel.selectedNote = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                showSaveWarningDialog = false
                            }
                        ) {
                            Text("Batal")
                        }
                        TextButton(
                            onClick = {
                                showSaveWarningDialog = false
                                hasUnsavedChanges = false
                                viewModel.selectedNote = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Buang")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // DIALOG: TEMPLATE CATATAN BARU
        if (showNewNoteTemplateDialog) {
            val templates = listOf(
                Triple("Catatan Polos", "Mulai menulis catatan kosong polos.", "Umum"),
                Triple("Daftar Tugas (Checklist)", "[ ] Tugas 1\n[ ] Tugas 2\n[ ] Tugas 3", "Checklist"),
                Triple("Jurnal Harian", "📅 Tanggal: ${SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())}\n\n🌟 Hal yang disyukuri hari ini:\n1. \n\n🎯 Target utama:\n- \n\n📝 Catatan refleksi:\n", "Ide"),
                Triple("Catatan Rapat", "📌 Agenda Rapat:\n- \n\n👥 Peserta:\n1. \n\n💬 Hasil Diskusi:\n- \n\n✅ Action Items:\n[ ] ", "Pekerjaan"),
                Triple("Ide & Inovasi", "💡 Deskripsi Ide:\n\n🎯 Target / Dampak:\n\n🚀 Langkah Awal:\n1. ", "Ide")
            )

            AlertDialog(
                onDismissRequest = { showNewNoteTemplateDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pilih Template Catatan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { (title, defaultText, cat) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showNewNoteTemplateDialog = false
                                        val newDraft = NoteItem(
                                            id = 0,
                                            title = if (title == "Catatan Polos") "" else title,
                                            content = defaultText,
                                            charCount = defaultText.length,
                                            lineCount = defaultText.split('\n').size,
                                            category = cat
                                        )
                                        viewModel.selectedNote = newDraft
                                        editTitle = newDraft.title
                                        contentTextFieldValue = TextFieldValue(defaultText, selection = TextRange(defaultText.length))
                                        editCategory = cat
                                        hasUnsavedChanges = false
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (cat) {
                                            "Checklist" -> Icons.Default.CheckBox
                                            "Pekerjaan" -> Icons.Default.Work
                                            "Ide" -> Icons.Default.Lightbulb
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(cat, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNewNoteTemplateDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // DIALOG: COLOR PALETTE SELECTOR
        if (showColorPaletteDialog) {
            AlertDialog(
                onDismissRequest = { showColorPaletteDialog = false },
                title = { Text("Pilih Warna Tema Catatan", fontWeight = FontWeight.Bold) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NOTE_COLORS.forEach { (hex, name) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .border(
                                        width = if (editColorHex == hex) 3.dp else 0.dp,
                                        color = if (editColorHex == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        editColorHex = hex
                                        showColorPaletteDialog = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (editColorHex == hex) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showColorPaletteDialog = false }) {
                        Text("Selesai")
                    }
                }
            )
        }

        // DIALOG: PILIH JENIS FILE KODE BARU
        if (showFileTypeDialog) {
            val fileTypes = listOf(
                "Plain Text (.txt)" to "txt",
                "Rich Text Format (.rtf)" to "rtf",
                "Word Document (.docx)" to "docx",
                "Word 97-2003 (.doc)" to "doc",
                "OpenDocument Text (.odt)" to "odt",
                "CSV (.csv)" to "csv",
                "JSON (.json)" to "json",
                "XML (.xml)" to "xml",
                "HTML (.html)" to "html",
                "Markdown (.md)" to "md",
                "Python (.py)" to "py",
                "JavaScript (.js)" to "js",
                "Java (.java)" to "java",
                "C++ (.cpp)" to "cpp",
                "C (.c)" to "c",
                "CSS (.css)" to "css"
            )

            AlertDialog(
                onDismissRequest = { showFileTypeDialog = false },
                title = {
                    Text(
                        text = "Pilih Jenis Dokumen Baru",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(fileTypes) { type ->
                            val extension = type.second
                            val label = type.first

                            val defaultContent = when (extension) {
                                "json" -> "{\n  \"key\": \"value\"\n}"
                                "xml" -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n  \n</root>"
                                "html" -> "<!DOCTYPE html>\n<html>\n<head>\n    <title>Kertas Baru</title>\n</head>\n<body>\n    \n</body>\n</html>"
                                "css" -> "body {\n    margin: 0;\n}"
                                "py" -> "#!/usr/bin/env python3\n\ndef main():\n    print('Hello World')\n\nif __name__ == '__main__':\n    main()"
                                "js" -> "function main() {\n    console.log('Hello');\n}"
                                "java" -> "public class KertasBaru {\n    public static void main(String[] args) {\n        \n    }\n}"
                                "cpp" -> "#include <iostream>\n\nint main() {\n    return 0;\n}"
                                "c" -> "#include <stdio.h>\n\nint main() {\n    return 0;\n}"
                                else -> ""
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showFileTypeDialog = false
                                        val newDraft = NoteItem(
                                            id = 0,
                                            title = "Kertas Baru.$extension",
                                            content = defaultContent,
                                            charCount = defaultContent.length,
                                            lineCount = defaultContent.split('\n').size,
                                            category = "Kode"
                                        )
                                        viewModel.selectedNote = newDraft
                                        editTitle = "Kertas Baru.$extension"
                                        contentTextFieldValue = TextFieldValue(defaultContent, selection = TextRange(defaultContent.length))
                                        editCategory = "Kode"
                                        hasUnsavedChanges = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (extension) {
                                    "html", "xml", "css", "js", "py", "java", "cpp", "c" -> Icons.Default.Code
                                    "json" -> Icons.Default.Settings
                                    "md" -> Icons.Default.Article
                                    else -> Icons.Default.Description
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFileTypeDialog = false }) {
                        Text("Batal")
                    }
                }
            )
        }

        // ARCHIVE NOTES POPUP / DIALOG
        if (showNotesPopup) {
            AlertDialog(
                onDismissRequest = { showNotesPopup = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Arsip Catatan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showNotesPopup = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                },
                text = {
                    if (notes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Belum ada catatan tersimpan.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notes) { note ->
                                val isActive = activeNote?.id == note.id
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.selectedNote = note
                                            editTitle = note.title
                                            contentTextFieldValue = TextFieldValue(note.content, selection = TextRange(note.content.length))
                                            editCategory = note.category
                                            editColorHex = note.colorHex
                                            editIsPinned = note.isPinned
                                            hasUnsavedChanges = false
                                            showNotesPopup = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (note.isPinned) {
                                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = note.title.ifEmpty { "Catatan Tanpa Judul" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = note.content.ifEmpty { "Kosong..." },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(onClick = { noteToDelete = note }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus Catatan",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotesPopup = false }) {
                        Text("Tutup", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Floating Animated Feedback Banner
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (feedbackBannerMessage != null) {
                com.example.ui.components.SpringToastPopup(
                    message = feedbackBannerMessage,
                    onDismiss = { feedbackBannerMessage = null }
                )
            }
        }

        // CONFIRM DELETE DIALOG
        if (noteToDelete != null) {
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text(text = "Hapus Catatan?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = { Text(text = "Apakah Anda yakin ingin menghapus catatan \"${noteToDelete?.title}\"? Tindakan ini tidak dapat dibatalkan.", fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            val deletedTitle = noteToDelete?.title ?: "Catatan"
                            noteToDelete?.let { note ->
                                if (note.id > 0) {
                                    viewModel.deleteNoteItem(note)
                                }
                                if (activeNote?.id == note.id || note.id == 0) {
                                    viewModel.selectedNote = null
                                }
                            }
                            noteToDelete = null
                            feedbackBannerMessage = "Catatan \"$deletedTitle\" berhasil dihapus."
                            feedbackBannerIcon = Icons.Default.Delete
                            feedbackBannerColor = Color(0xFFEF4444)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { noteToDelete = null }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

// GRID NOTE CARD COMPONENT
@Composable
fun NoteCardGrid(
    note: NoteItem,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val cardColor = parseHexColor(note.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (note.isPinned) 2.dp else 1.dp,
            color = if (note.isPinned) Color(0xFFF59E0B) else cardColor.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // TOP BADGE & PIN ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Chip
                Text(
                    text = note.category.ifEmpty { "Umum" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(cardColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                IconButton(
                    onClick = { onPinToggle() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Sematkan",
                        tint = if (note.isPinned) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TITLE
            Text(
                text = note.title.ifEmpty { "Tanpa Judul" },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // CONTENT PREVIEW
            Text(
                text = note.content.ifEmpty { "Kosong..." },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                fontFamily = if (note.category == "Kode" || note.title.contains(".")) FontFamily.Monospace else FontFamily.Default
            )

            Spacer(modifier = Modifier.height(10.dp))

            // FOOTER RELATIVE TIME & ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatRelativeTime(note.lastSaved),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row {
                    IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// LIST NOTE CARD COMPONENT
@Composable
fun NoteCardList(
    note: NoteItem,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    val cardColor = parseHexColor(note.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("note_card_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (note.isPinned) 1.5.dp else 1.dp,
            color = if (note.isPinned) Color(0xFFF59E0B) else cardColor.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(cardColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (note.category) {
                            "Checklist" -> Icons.Default.CheckBox
                            "Pekerjaan" -> Icons.Default.Work
                            "Ide" -> Icons.Default.Lightbulb
                            "Kode" -> Icons.Default.Code
                            else -> Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = cardColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isPinned) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = note.title.ifEmpty { "Tanpa Judul" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = note.content.ifEmpty { "Kosong..." },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = note.category.ifEmpty { "Umum" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(cardColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                        Text(
                            text = "${note.charCount} Karakter • ${formatRelativeTime(note.lastSaved)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPinToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Sematkan",
                        tint = if (note.isPinned) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Bagikan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// IMMERSIVE PDF & BOOK VIEWERS
@Composable
fun PdfViewer(uriString: String, fileName: String, scale: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uriString) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                val renderer = PdfRenderer(parcelFileDescriptor)
                val pageCount = renderer.pageCount
                val list = mutableListOf<Bitmap>()
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val width = 1080
                    val height = (width * page.height) / page.width
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    list.add(bitmap)
                    page.close()
                }
                renderer.close()
                parcelFileDescriptor.close()
                bitmaps = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMsg = e.localizedMessage
        }
    }

    if (errorMsg != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Gagal menampilkan PDF: $errorMsg", color = MaterialTheme.colorScheme.error)
        }
    } else if (bitmaps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { bitmaps.size })
        var isIndicatorVisible by remember { mutableStateOf(true) }
        var touchTrigger by remember { mutableStateOf(0) }

        LaunchedEffect(pagerState.currentPage, touchTrigger) {
            isIndicatorVisible = true
            delay(5000)
            isIndicatorVisible = false
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            touchTrigger++
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = scale == 1f,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                pageSpacing = 16.dp
            ) { page ->
                Card(
                    modifier = Modifier.fillMaxSize(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmaps[page].asImageBitmap(),
                            contentDescription = "Halaman ${page + 1}",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = isIndicatorVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Halaman ${pagerState.currentPage + 1} dari ${bitmaps.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = fileName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

fun splitTextIntoPages(text: String, charsPerPage: Int = 1800): List<String> {
    if (text.isEmpty()) return listOf("Dokumen kosong")
    val pages = mutableListOf<String>()
    val lines = text.split('\n')
    var currentChunk = StringBuilder()
    var lineCount = 0
    
    for (line in lines) {
        if (currentChunk.length + line.length > charsPerPage || lineCount >= 35) {
            pages.add(currentChunk.toString())
            currentChunk = StringBuilder()
            lineCount = 0
        }
        currentChunk.append(line).append('\n')
        lineCount++
    }
    if (currentChunk.isNotEmpty()) {
        pages.add(currentChunk.toString())
    }
    return if (pages.isEmpty()) listOf(text) else pages
}

@Composable
fun TextFileBookViewer(
    content: String,
    fileName: String,
    scale: Float,
    isMonospace: Boolean,
    modifier: Modifier = Modifier
) {
    val pages = remember(content) { splitTextIntoPages(content) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var isIndicatorVisible by remember { mutableStateOf(true) }
    var touchTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState.currentPage, touchTrigger) {
        isIndicatorVisible = true
        delay(5000)
        isIndicatorVisible = false
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        touchTrigger++
                    }
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = scale == 1f,
            contentPadding = PaddingValues(16.dp),
            pageSpacing = 16.dp
        ) { page ->
            Card(
                modifier = Modifier.fillMaxSize(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = pages[page],
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        AnimatedVisibility(
            visible = isIndicatorVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Halaman ${pagerState.currentPage + 1} dari ${pages.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = fileName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ImmersiveFileViewerScreen(viewModel: MainViewModel) {
    val uriString = viewModel.immersiveFileUriStr ?: return
    val fileName = viewModel.immersiveFileName
    val isPdf = viewModel.immersiveFileIsPdf
    
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val textContent by remember { mutableStateOf(viewModel.immersiveFileContent) }
    
    BackHandler {
        viewModel.currentRoute = "catatan"
    }
    
    val extension = fileName.substringAfterLast(".", "").lowercase(Locale.getDefault())
    val isMonospace = extension in listOf("py", "js", "java", "cpp", "c", "css", "xml", "json", "html")
    
    Scaffold(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 5.0f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
        ) {
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
                if (isPdf) {
                    PdfViewer(uriString = uriString, fileName = fileName, scale = scale, modifier = Modifier.fillMaxSize())
                } else {
                    TextFileBookViewer(
                        content = textContent,
                        fileName = fileName,
                        scale = scale,
                        isMonospace = isMonospace,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
