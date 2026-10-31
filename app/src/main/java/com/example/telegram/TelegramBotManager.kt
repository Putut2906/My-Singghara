package com.example.telegram

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TelegramBotManager {

    private const val TAG = "TelegramBotManager"
    private const val PREFS_NAME = "telegram_bot_prefs"

    const val DEFAULT_BOT_TOKEN = "8670152772:AAHEVL05UN-e7HiD233MME1eTvx-3Lo--0k"
    private const val KEY_BOT_TOKEN = "key_bot_token"
    private const val KEY_CHAT_ID = "key_chat_id"
    private const val KEY_ALLOW_ANY_CHAT = "key_allow_any_chat"
    private const val KEY_BOT_ENABLED = "key_bot_enabled"
    private const val KEY_LAST_UPDATE_ID = "key_last_update_id"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _botInfo = MutableStateFlow<TelegramBotInfo?>(null)
    val botInfo: StateFlow<TelegramBotInfo?> = _botInfo.asStateFlow()

    private val _registeredChatId = MutableStateFlow("")
    val registeredChatId: StateFlow<String> = _registeredChatId.asStateFlow()

    private val _allowAnyChat = MutableStateFlow(true)
    val allowAnyChat: StateFlow<Boolean> = _allowAnyChat.asStateFlow()

    private val _botToken = MutableStateFlow(DEFAULT_BOT_TOKEN)
    val botToken: StateFlow<String> = _botToken.asStateFlow()

    private val _logs = MutableStateFlow<List<TelegramLogItem>>(emptyList())
    val logs: StateFlow<List<TelegramLogItem>> = _logs.asStateFlow()

    private val _lastActiveTimestamp = MutableStateFlow(0L)
    val lastActiveTimestamp: StateFlow<Long> = _lastActiveTimestamp.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_BOT_TOKEN, DEFAULT_BOT_TOKEN) ?: DEFAULT_BOT_TOKEN
        val chatId = prefs.getString(KEY_CHAT_ID, "") ?: ""
        val anyChat = prefs.getBoolean(KEY_ALLOW_ANY_CHAT, true)
        val enabled = prefs.getBoolean(KEY_BOT_ENABLED, true)

        _botToken.value = token
        _registeredChatId.value = chatId
        _allowAnyChat.value = anyChat

        // Auto fetch bot details
        scope.launch {
            fetchBotInfo(token)
            if (enabled) {
                startPolling(context)
            }
        }
    }

    fun setBotToken(context: Context, token: String) {
        val clean = token.trim()
        _botToken.value = clean
        getPrefs(context).edit().putString(KEY_BOT_TOKEN, clean).apply()
        scope.launch {
            fetchBotInfo(clean)
            if (_isRunning.value) {
                restart(context)
            }
        }
    }

    fun setRegisteredChatId(context: Context, chatId: String) {
        val clean = chatId.trim()
        _registeredChatId.value = clean
        getPrefs(context).edit().putString(KEY_CHAT_ID, clean).apply()
        addLog(
            TelegramLogType.SYSTEM,
            chatId = clean,
            senderName = "Sistem",
            message = "Chat ID berhasil disimpan: $clean"
        )
    }

    fun setAllowAnyChat(context: Context, allow: Boolean) {
        _allowAnyChat.value = allow
        getPrefs(context).edit().putBoolean(KEY_ALLOW_ANY_CHAT, allow).apply()
    }

    fun start(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_BOT_ENABLED, true).apply()
        startPolling(context)
        try {
            val serviceIntent = Intent(context, TelegramBotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai service latar belakang: ${e.message}")
        }
    }

    fun stop(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_BOT_ENABLED, false).apply()
        stopPolling()
        try {
            val serviceIntent = Intent(context, TelegramBotService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghentikan service latar belakang: ${e.message}")
        }
    }

    fun restart(context: Context) {
        stopPolling()
        startPolling(context)
    }

    suspend fun fetchBotInfo(token: String = _botToken.value): TelegramBotInfo? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        try {
            val url = "https://api.telegram.org/bot$token/getMe"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    if (root.optBoolean("ok")) {
                        val result = root.getJSONObject("result")
                        val info = TelegramBotInfo(
                            id = result.optLong("id"),
                            firstName = result.optString("first_name", "My Singghara"),
                            username = result.optString("username", "My_Singghara_bot"),
                            isBot = result.optBoolean("is_bot", true)
                        )
                        _botInfo.value = info
                        return@withContext info
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengambil data bot getMe: ${e.message}")
        }
        null
    }

    fun startPolling(context: Context) {
        if (pollingJob?.isActive == true) return
        val token = _botToken.value
        if (token.isBlank()) return

        _isRunning.value = true
        addLog(
            TelegramLogType.SYSTEM,
            chatId = "",
            senderName = "Sistem",
            message = "Layanan Telegram Bot AI aktif dan mulai mendengarkan pesan..."
        )

        pollingJob = scope.launch {
            val prefs = getPrefs(context)
            var lastUpdateId = prefs.getLong(KEY_LAST_UPDATE_ID, 0L)

            while (isActive) {
                try {
                    val currentToken = _botToken.value
                    if (currentToken.isBlank()) {
                        delay(3000)
                        continue
                    }

                    val offsetParam = if (lastUpdateId > 0) "?offset=${lastUpdateId + 1}&timeout=15" else "?timeout=15"
                    val url = "https://api.telegram.org/bot$currentToken/getUpdates$offsetParam"
                    val request = Request.Builder().url(url).get().build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val root = JSONObject(body)
                            if (root.optBoolean("ok")) {
                                val updates = root.optJSONArray("result")
                                if (updates != null && updates.length() > 0) {
                                    for (i in 0 until updates.length()) {
                                        val upd = updates.getJSONObject(i)
                                        val updateId = upd.optLong("update_id")
                                        if (updateId > lastUpdateId) {
                                            lastUpdateId = updateId
                                            prefs.edit().putLong(KEY_LAST_UPDATE_ID, lastUpdateId).apply()
                                        }

                                        val msg = upd.optJSONObject("message")
                                        if (msg != null) {
                                            handleIncomingMessage(context, currentToken, msg, updateId)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        delay(4000)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.w(TAG, "Polling update loop: ${e.message}")
                        delay(4000)
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(
        context: Context,
        token: String,
        msgObj: JSONObject,
        updateId: Long
    ) {
        val messageId = msgObj.optLong("message_id")
        val chatObj = msgObj.optJSONObject("chat") ?: return
        val chatId = chatObj.optLong("id")
        val fromObj = msgObj.optJSONObject("from")
        val fromName = fromObj?.optString("first_name", "Pengguna") ?: "Pengguna"
        val username = fromObj?.optString("username", "") ?: ""
        val text = msgObj.optString("text", "").trim()

        if (text.isBlank()) return

        _lastActiveTimestamp.value = System.currentTimeMillis()

        // Auto-save chat ID if not set yet
        val currentRegistered = _registeredChatId.value
        if (currentRegistered.isBlank()) {
            setRegisteredChatId(context, chatId.toString())
        }

        // Catat log pesan masuk
        addLog(
            TelegramLogType.INCOMING,
            chatId = chatId.toString(),
            senderName = fromName,
            message = text
        )

        // Verifikasi izin akses jika allowAnyChat tidak aktif
        val isAllowed = _allowAnyChat.value || currentRegistered == chatId.toString()
        if (!isAllowed) {
            val rejectedMsg = "⚠️ *Akses Dibatasi*\n\nBot ini saat ini hanya melayani ID Obrolan pemilik aplikasi My Singghara. Silakan hubungi pemilik untuk mendaftarkan Chat ID Anda (`$chatId`)."
            sendMessage(token, chatId, rejectedMsg)
            return
        }

        // Kirim indikator typing...
        sendChatAction(token, chatId, "typing")

        // Proses respon AI menggunakan database My Singghara
        val replyText = TelegramAiEngine.processMessage(
            context = context,
            rawText = text,
            chatId = chatId,
            senderName = fromName
        )

        // Kirim balasan ke Telegram
        val sendSuccess = sendMessage(token, chatId, replyText)

        // Catat log balasan
        addLog(
            TelegramLogType.OUTGOING,
            chatId = chatId.toString(),
            senderName = "AI My Singghara",
            message = replyText,
            status = if (sendSuccess) "Terkirim" else "Gagal Kirim"
        )
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isRunning.value = false
    }

    suspend fun sendMessage(
        token: String = _botToken.value,
        chatId: Long,
        text: String,
        parseMode: String = "Markdown"
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId == 0L || text.isBlank()) return@withContext false
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", parseMode)
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful && parseMode == "Markdown") {
                // Fallback to plain text if markdown formatting has an unescaped symbol
                val fallbackPayload = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text.replace("*", "").replace("_", ""))
                }
                val fallbackReq = Request.Builder()
                    .url(url)
                    .post(fallbackPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                return@withContext httpClient.newCall(fallbackReq).execute().isSuccessful
            }
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Gagal kirim pesan ke Telegram chat $chatId: ${e.message}")
            false
        }
    }

    suspend fun sendChatAction(
        token: String = _botToken.value,
        chatId: Long,
        action: String = "typing"
    ) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$token/sendChatAction"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("action", action)
            }
            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute()
        } catch (_: Exception) {}
    }

    suspend fun sendTestMessage(context: Context, testText: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val targetChatId = _registeredChatId.value.toLongOrNull()
        if (targetChatId == null || targetChatId == 0L) {
            return@withContext false to "Chat ID belum diatur. Masukkan Chat ID atau klik 'Deteksi Otomatis' setelah mengirim pesan di bot Telegram."
        }

        val textToSend = testText ?: "🔔 *Pesan Tes Terhubung!*\n\nAplikasi *My Singghara* berhasil terhubung dengan ID Obrolan Telegram Anda (`$targetChatId`).\n\nCoba ketik: *\"Apakah hari ini libur?\"* untuk menguji respons database!"
        val success = sendMessage(_botToken.value, targetChatId, textToSend)

        if (success) {
            addLog(
                TelegramLogType.OUTGOING,
                chatId = targetChatId.toString(),
                senderName = "AI My Singghara",
                message = textToSend,
                status = "Tes Terkirim"
            )
            return@withContext true to "Pesan tes berhasil dikirim ke Chat ID $targetChatId!"
        } else {
            return@withContext false to "Gagal mengirim pesan. Pastikan bot token valid dan Anda sudah menekan 'Start' di @My_Singghara_bot."
        }
    }

    suspend fun autoDetectChatId(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val token = _botToken.value
        if (token.isBlank()) return@withContext false to "Bot token belum diisi."

        try {
            val url = "https://api.telegram.org/bot$token/getUpdates?limit=10"
            val req = Request.Builder().url(url).get().build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    if (root.optBoolean("ok")) {
                        val arr = root.optJSONArray("result")
                        if (arr != null && arr.length() > 0) {
                            // Ambil update paling baru yang memiliki chat id
                            for (i in (arr.length() - 1) downTo 0) {
                                val item = arr.getJSONObject(i)
                                val msg = item.optJSONObject("message")
                                val chat = msg?.optJSONObject("chat")
                                if (chat != null) {
                                    val detectedId = chat.optLong("id")
                                    val detectedName = chat.optString("first_name", "Pengguna")
                                    if (detectedId != 0L) {
                                        setRegisteredChatId(context, detectedId.toString())
                                        return@withContext true to "Berhasil mendeteksi Chat ID: $detectedId ($detectedName)"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@withContext false to "Belum ada pesan masuk di @My_Singghara_bot. Buka Telegram, cari @My_Singghara_bot dan klik Start/kirim pesan terlebih dahulu."
        } catch (e: Exception) {
            return@withContext false to "Error: ${e.message}"
        }
    }

    fun addLog(
        type: TelegramLogType,
        chatId: String,
        senderName: String,
        message: String,
        status: String = "OK"
    ) {
        val item = TelegramLogItem(
            type = type,
            chatId = chatId,
            senderName = senderName,
            message = message,
            status = status
        )
        val current = _logs.value.toMutableList()
        current.add(0, item)
        if (current.size > 50) {
            _logs.value = current.take(50)
        } else {
            _logs.value = current
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
