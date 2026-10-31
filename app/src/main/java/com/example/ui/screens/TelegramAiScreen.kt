package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegram.TelegramAiEngine
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramLogItem
import com.example.telegram.TelegramLogType
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAiScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    var currentSection by remember { mutableIntStateOf(0) }

    BackHandler {
        if (currentSection != 0) {
            currentSection = 0
        } else {
            onBackClick()
        }
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isRunning by TelegramBotManager.isRunning.collectAsState()
    val botInfo by TelegramBotManager.botInfo.collectAsState()
    val registeredChatId by TelegramBotManager.registeredChatId.collectAsState()
    val allowAnyChat by TelegramBotManager.allowAnyChat.collectAsState()
    val botToken by TelegramBotManager.botToken.collectAsState()
    val logs by TelegramBotManager.logs.collectAsState()

    // In-App Chat Test State
    var inputQuery by remember { mutableStateOf("") }
    var isSendingInApp by remember { mutableStateOf(false) }
    val inAppChatMessages = remember {
        mutableStateListOf(
            Pair(
                false,
                "👋 Halo! Saya asisten AI resmi My Singghara yang terhubung dua arah dengan database ponsel Anda (100% Offline).\n\nKini selain membaca data & mencatat data baru, Anda juga dapat MENGEDIT DATA langsung (Edit Catatan, Ubah Agenda, Tandai/Edit Belanja Pasar, Ubah Jadwal Harian, Edit Stok/Harga Kasir, dan Target Tabungan).\n\n📌 Karakteristik Utama:\n• Sticky Bottom (Menempel di Bawah): Kotak input tetap diam di bawah layar saat scrolling riwayat ke atas.\n• Alur Baca Kronologis: Percakapan dibaca dari atas (pesan lama) ke bawah (pesan baru), mirip membaca buku atau komik.\n\nCoba pilih saran aksi atau ketik perintah Anda di bawah!"
            )
        )
    }

    // Editable form state for Settings tab
    var tokenInput by remember(botToken) { mutableStateOf(botToken) }
    var isTokenVisible by remember { mutableStateOf(false) }
    var chatIdInput by remember(registeredChatId) { mutableStateOf(registeredChatId) }
    var isDetectingChatId by remember { mutableStateOf(false) }
    var isTestingTelegramMessage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        var showMoreMenu by remember { mutableStateOf(false) }

        // Top Bar with Section Title / Back and 3-dots action menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (currentSection != 0) {
                IconButton(
                    onClick = { currentSection = 0 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali ke Uji Coba AI",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (currentSection) {
                        1 -> "Pengaturan & Chat ID"
                        2 -> "Log Aktivitas Bot"
                        else -> "Telegram AI Assistant"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isRunning) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Telegram AI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu Aksi Bot",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Uji Coba AI",
                                fontWeight = if (currentSection == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = if (currentSection == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (currentSection == 0) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            currentSection = 0
                            showMoreMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Pengaturan & Chat ID",
                                fontWeight = if (currentSection == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (currentSection == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (currentSection == 1) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            currentSection = 1
                            showMoreMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                if (logs.isNotEmpty()) "Log Aktivitas (${logs.size})" else "Log Aktivitas",
                                fontWeight = if (currentSection == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                tint = if (currentSection == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = if (currentSection == 2) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        onClick = {
                            currentSection = 2
                            showMoreMenu = false
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("Buka di Telegram") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = null,
                                tint = Color(0xFF0288D1)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            val botUsername = botInfo?.username ?: "My_Singghara_bot"
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/$botUsername")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Aplikasi Telegram belum terpasang", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text(if (isRunning) "Hentikan Bot (Stop)" else "Aktifkan Bot") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isRunning) Color(0xFFEF4444) else Color(0xFF10B981)
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            if (isRunning) {
                                TelegramBotManager.stop(context)
                                Toast.makeText(context, "Layanan Bot dihentikan", Toast.LENGTH_SHORT).show()
                            } else {
                                TelegramBotManager.start(context)
                                Toast.makeText(context, "Layanan Bot diaktifkan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (currentSection) {
                0 -> InAppChatTestTab(
                    messages = inAppChatMessages,
                    inputQuery = inputQuery,
                    onInputChange = { inputQuery = it },
                    isSending = isSendingInApp,
                    onSendMessage = { query ->
                        if (query.isNotBlank()) {
                            inAppChatMessages.add(Pair(true, query))
                            val targetQuery = query
                            inputQuery = ""
                            isSendingInApp = true
                            coroutineScope.launch {
                                val reply = TelegramAiEngine.processMessage(
                                    context = context,
                                    rawText = targetQuery,
                                    chatId = registeredChatId.toLongOrNull() ?: 999999L,
                                    senderName = "Anda (Uji Coba)"
                                )
                                inAppChatMessages.add(Pair(false, reply))
                                isSendingInApp = false
                            }
                        }
                    }
                )
                    1 -> SettingsTab(
                        token = tokenInput,
                        onTokenChange = { tokenInput = it },
                        isTokenVisible = isTokenVisible,
                        onToggleTokenVisible = { isTokenVisible = !isTokenVisible },
                        onResetToken = {
                            tokenInput = TelegramBotManager.DEFAULT_BOT_TOKEN
                            TelegramBotManager.setBotToken(context, TelegramBotManager.DEFAULT_BOT_TOKEN)
                            Toast.makeText(context, "Token bot direset ke token bawaan", Toast.LENGTH_SHORT).show()
                        },
                        onSaveToken = {
                            TelegramBotManager.setBotToken(context, tokenInput)
                            Toast.makeText(context, "Token bot berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        },
                        chatId = chatIdInput,
                        onChatIdChange = { chatIdInput = it },
                        onSaveChatId = {
                            TelegramBotManager.setRegisteredChatId(context, chatIdInput)
                            Toast.makeText(context, "Chat ID disimpan", Toast.LENGTH_SHORT).show()
                        },
                        allowAnyChat = allowAnyChat,
                        onToggleAllowAnyChat = {
                            TelegramBotManager.setAllowAnyChat(context, it)
                        },
                        isDetectingChatId = isDetectingChatId,
                        onAutoDetectChatId = {
                            isDetectingChatId = true
                            coroutineScope.launch {
                                val (success, msg) = TelegramBotManager.autoDetectChatId(context)
                                isDetectingChatId = false
                                if (success) {
                                    chatIdInput = TelegramBotManager.registeredChatId.value
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        isTestingTelegramMessage = isTestingTelegramMessage,
                        onSendTestMessage = {
                            isTestingTelegramMessage = true
                            coroutineScope.launch {
                                val (ok, resp) = TelegramBotManager.sendTestMessage(context)
                                isTestingTelegramMessage = false
                                Toast.makeText(context, resp, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    2 -> ActivityLogTab(
                        logs = logs,
                        onClearLogs = { TelegramBotManager.clearLogs() }
                    )
            }
        }
    }
}

@Composable
fun InAppChatTestTab(
    messages: List<Pair<Boolean, String>>,
    inputQuery: String,
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    onSendMessage: (String) -> Unit
) {
    val quickQuestions = listOf(
        "✏️ Edit catatan kegiatan besok: Tambah sound system dan konsumsi",
        "🛒 Tandai belanja telur sudah dibeli",
        "🛒 Ubah belanja minyak jadi 2 liter",
        "📅 Ubah agenda rapat ke besok jam 10:00",
        "⏰ Edit jadwal olahraga jam 06:30",
        "🏪 Edit stok beras jadi 30",
        "🏪 Ubah harga minyak goreng jadi 28000",
        "💰 Ubah target tabungan jadi 15000000",
        "🏢 Ubah divisi Budi jadi Marketing",
        "📌 Sematkan catatan penting",
        "Buatkan saya catatan penting untuk kegiatan besok",
        "Jadwalkan agenda besok rapat evaluasi jam 09:00",
        "Beli telur 1 kg dan beras 5 kg",
        "Catat tabungan masuk 100000 dari jualan",
        "Jadwal sholat hari ini",
        "Perkiraan cuaca hari ini",
        "Apakah hari ini libur?",
        "Cek penjualan kasir",
        "Stok barang kasir",
        "Data karyawan company",
        "Absensi hari ini",
        "Laporan laba rugi usaha",
        "Berapa saldo tabungan saya?",
        "Ada agenda apa hari ini?",
        "Apa yang perlu dibeli di pasar?"
    )

    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Sticky Bottom (Menempel di Bawah):
    // Kotak input tetap diam di bawah layar saat scrolling riwayat ke atas
    // Alur baca kronologis: atas ke bawah (pesan lama ke pesan baru)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Info Badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 6.dp)
        ) {
            Text(
                text = "Sticky Bottom Aktif • Input tetap di bawah saat scrolling riwayat",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        // Chat Messages List (Scrollable Kronologis)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { (isUser, text) ->
                ChatBubble(isUser = isUser, text = text)
            }
            if (isSending) {
                item {
                    SendingAiIndicator()
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Sticky Bottom Input Controls
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            "tulis pesan...",
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("telegram_chat_input"),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputQuery.isNotBlank() && !isSending) {
                                onSendMessage(inputQuery)
                            }
                        }
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = { onSendMessage(inputQuery) },
                    enabled = inputQuery.isNotBlank() && !isSending,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF229ED9),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("telegram_chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim Pertanyaan"
                    )
                }
            }

            AnimatedVisibility(
                visible = inputQuery.isBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    items(quickQuestions) { q ->
                        SuggestionChip(
                            onClick = { onSendMessage(q) },
                            label = { Text(q, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SendingAiIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF229ED9)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "AI membaca database My Singghara secara offline...",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatBubble(isUser: Boolean, text: String) {
    val context = LocalContext.current
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    val bgColor = if (isUser) {
        if (isDark) Color(0xFF2B5278) else Color(0xFFEFFDDE)
    } else {
        if (isDark) Color(0xFF212D3B) else MaterialTheme.colorScheme.surface
    }

    val textColor = if (isUser) {
        if (isDark) Color.White else Color(0xFF1E293B)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val borderStroke = if (!isUser && !isDark) {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    } else null

    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = shape,
            color = bgColor,
            border = borderStroke,
            shadowElevation = if (!isDark) 1.dp else 0.dp,
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Telegram AI Reply", text))
                    Toast.makeText(context, "Teks disalin ke clipboard", Toast.LENGTH_SHORT).show()
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 13.sp,
                    color = textColor,
                    lineHeight = 18.sp
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    if (isUser) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Terkirim",
                            tint = Color(0xFF229ED9),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = if (isUser) "Anda" else "My Singghara AI",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}


@Composable
fun SettingsTab(
    token: String,
    onTokenChange: (String) -> Unit,
    isTokenVisible: Boolean,
    onToggleTokenVisible: () -> Unit,
    onResetToken: () -> Unit,
    onSaveToken: () -> Unit,
    chatId: String,
    onChatIdChange: (String) -> Unit,
    onSaveChatId: () -> Unit,
    allowAnyChat: Boolean,
    onToggleAllowAnyChat: (Boolean) -> Unit,
    isDetectingChatId: Boolean,
    onAutoDetectChatId: () -> Unit,
    isTestingTelegramMessage: Boolean,
    onSendTestMessage: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Telegram Bot API Token
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telegram Bot API Token",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Token bot resmi dari @BotFather untuk berkomunikasi via Telegram Bot API.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = if (isTokenVisible) token else "•".repeat(token.length.coerceAtMost(35)),
                        onValueChange = onTokenChange,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = onToggleTokenVisible) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Tampilkan/Sembunyikan Token"
                                )
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onResetToken,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Token Bawaan", fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = onSaveToken,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simpan Token", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        // Section: Telegram Chat ID
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telegram ID Obrolan (Chat ID)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ID Obrolan unik Anda di Telegram untuk verifikasi dan pengiriman pesan langsung dari database My Singghara.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = onChatIdChange,
                        placeholder = { Text("Contoh: 123456789 (atau klik deteksi)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (chatId.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Chat ID", chatId))
                                        Toast.makeText(context, "Chat ID disalin", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin Chat ID"
                                    )
                                }
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onAutoDetectChatId,
                            enabled = !isDetectingChatId,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isDetectingChatId) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Deteksi Otomatis", fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = onSaveChatId,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simpan Chat ID", fontSize = 11.5.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Telegram Message
                    FilledTonalButton(
                        onClick = onSendTestMessage,
                        enabled = !isTestingTelegramMessage && chatId.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingTelegramMessage) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kirim Pesan Tes ke Telegram", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section: Akses & Hak Izin
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izinkan Semua Chat ID",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (allowAnyChat) "Bot membalas pertanyaan siapa saja yang menghubungi @My_Singghara_bot" else "Bot hanya melayani Chat ID yang terdaftar di atas",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowAnyChat,
                            onCheckedChange = onToggleAllowAnyChat
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityLogTab(
    logs: List<TelegramLogItem>,
    onClearLogs: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Log Aktivitas Bot (${logs.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onClearLogs) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bersihkan Log", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Belum ada pesan Telegram masuk atau keluar.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogItemCard(log = log, timeStr = sdf.format(Date(log.timestamp)))
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: TelegramLogItem, timeStr: String) {
    val (badgeColor, badgeText, badgeIcon) = when (log.type) {
        TelegramLogType.INCOMING -> Triple(Color(0xFF0288D1), "MASUK", Icons.Default.CallReceived)
        TelegramLogType.OUTGOING -> Triple(Color(0xFF10B981), "BALASAN", Icons.Default.CallMade)
        TelegramLogType.SYSTEM -> Triple(Color(0xFF8B5CF6), "SISTEM", Icons.Default.Settings)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (log.chatId.isNotBlank()) {
                Text(
                    text = "${log.senderName} (Chat ID: ${log.chatId})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = log.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
