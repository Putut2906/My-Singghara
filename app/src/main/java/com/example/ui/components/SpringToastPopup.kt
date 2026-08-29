package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

enum class ToastType {
    AUTO, SUCCESS, ERROR, WARNING, INFO
}

fun autoDetectToastType(msg: String): ToastType {
    val lower = msg.lowercase()
    return when {
        lower.contains("berhasil") || lower.contains("sukses") || lower.contains("disimpan") ||
        lower.contains("diizinkan") || lower.contains("aktif") || lower.contains("dibuat") ||
        lower.contains("disalin") || lower.contains("diperbarui") || lower.contains("dirapikan") ||
        lower.contains("dipusatkan") || lower.contains("dikirim") || lower.contains("terhubung") ||
        lower.contains("selesai") -> ToastType.SUCCESS

        lower.contains("gagal") || lower.contains("ditolak") || lower.contains("terputus") ||
        lower.contains("terkunci") || lower.contains("salah") || lower.contains("error") ||
        lower.contains("tidak ada") || lower.contains("tidak valid") || lower.contains("tidak ditemukan") ||
        lower.contains("kosong") -> ToastType.ERROR

        lower.contains("hapus") || lower.contains("dihapus") || lower.contains("batal") ||
        lower.contains("peringatan") || lower.contains("reset") || lower.contains("dimatikan") ||
        lower.contains("terlepas") -> ToastType.WARNING

        else -> ToastType.INFO
    }
}

private data class ToastStyleConfig(
    val primaryColor: Color,
    val icon: ImageVector,
    val title: String
)

@Composable
fun SpringToastPopup(
    message: String?,
    onDismiss: () -> Unit,
    type: ToastType = ToastType.AUTO,
    durationMs: Long = 3200L,
    modifier: Modifier = Modifier
) {
    if (message.isNullOrBlank()) return

    val isDark = isSystemInDarkTheme()
    val resolvedType = if (type == ToastType.AUTO) autoDetectToastType(message) else type

    // Use M3 Theme colors for adaptive dark & light mode support
    val config = when (resolvedType) {
        ToastType.SUCCESS -> ToastStyleConfig(
            primaryColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
            icon = Icons.Default.CheckCircle,
            title = "Sukses"
        )
        ToastType.ERROR -> ToastStyleConfig(
            primaryColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
            icon = Icons.Default.Error,
            title = "Perhatian"
        )
        ToastType.WARNING -> ToastStyleConfig(
            primaryColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
            icon = Icons.Default.Warning,
            title = "Peringatan"
        )
        ToastType.INFO -> ToastStyleConfig(
            primaryColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.Info,
            title = "Info"
        )
        else -> ToastStyleConfig(
            primaryColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.Info,
            title = "Info"
        )
    }

    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textPrimaryColor = MaterialTheme.colorScheme.onSurface
    val closeIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackBackgroundColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val iconInnerBgColor = config.primaryColor.copy(alpha = if (isDark) 0.2f else 0.12f)

    // Progress bar for countdown
    val progress = remember { Animatable(1f) }

    LaunchedEffect(message) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs.toInt(), easing = LinearEasing)
        )
        onDismiss()
    }

    Popup(
        alignment = Alignment.TopCenter,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isVisible = true
        }

        AnimatedVisibility(
            visible = isVisible && !message.isNullOrBlank(),
            enter = slideInVertically(
                initialOffsetY = { -it * 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { -it * 2 },
                animationSpec = tween(250, easing = FastOutLinearInEasing)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = modifier
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(0.92f)
        ) {
            Surface(
                modifier = Modifier
                    .shadow(
                        elevation = if (isDark) 10.dp else 6.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = config.primaryColor.copy(alpha = if (isDark) 0.4f else 0.25f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.dp,
                        color = config.primaryColor.copy(alpha = if (isDark) 0.5f else 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                color = surfaceContainerColor,
                contentColor = textPrimaryColor
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Compact Icon Badge
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(iconInnerBgColor, CircleShape)
                                .border(1.dp, config.primaryColor.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = config.icon,
                                contentDescription = config.title,
                                tint = config.primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Concise Toast Text
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = config.title,
                                color = config.primaryColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                            Text(
                                text = message,
                                color = textPrimaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(26.dp)
                                .testTag("toast_popup_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = closeIconColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Smooth Auto-close Countdown Indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(trackBackgroundColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.value)
                                .fillMaxHeight()
                                .background(
                                    color = config.primaryColor,
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

