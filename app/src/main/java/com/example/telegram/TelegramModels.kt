package com.example.telegram

data class TelegramBotInfo(
    val id: Long,
    val firstName: String,
    val username: String,
    val isBot: Boolean = true
)

data class TelegramIncomingMessage(
    val updateId: Long,
    val messageId: Long,
    val chatId: Long,
    val fromId: Long,
    val fromName: String,
    val username: String,
    val text: String,
    val date: Long
)

enum class TelegramLogType {
    INCOMING,
    OUTGOING,
    SYSTEM
}

data class TelegramLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: TelegramLogType,
    val chatId: String,
    val senderName: String,
    val message: String,
    val status: String = "OK"
)
