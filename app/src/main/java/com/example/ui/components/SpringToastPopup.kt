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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.awaitCancellation

enum class ToastType {
    AUTO, SUCCESS, ERROR, WARNING, INFO
}

fun autoDetectToastType(msg: String): ToastType {
    val lower = msg.lowercase()
    // Kata penolakan dan validasi harus dicek lebih dulu dari daftar sukses:
    // "tidak berhasil disimpan" memuat "disimpan", "mohon lengkapi agar data
    // tersimpan" memuat "tersimpan", dan "mode tidak aktif" memuat "aktif" ->
    // ketiganya sempat tampil hijau sebagai kabar keberhasilan.
    val isRejected = lower.contains("gagal") || lower.contains("tidak berhasil") ||
        lower.contains("tidak bisa") || lower.contains("tidak dapat") ||
        lower.contains("ditolak") || lower.contains("error") || lower.contains("salah") ||
        lower.contains("tidak ada") || lower.contains("tidak valid") ||
        lower.contains("tidak ditemukan") || lower.contains("tidak boleh")
    // Campuran sukses + gagal (misal backup/restore sebagian) bukan kabar baik
    // maupun kegagalan total.
    val isPartial = (lower.contains("berhasil") || lower.contains("sukses") ||
        lower.contains("selesai")) && lower.contains("gagal")
    val isValidation = lower.contains("lengkapi") || lower.contains("isian") ||
        lower.contains("mohon isi") || lower.contains("wajib diisi") ||
        lower.contains("dengan valid") || lower.contains("kosong") ||
        lower.contains("belum diisi")
    val isTurnedOff = lower.contains("tidak aktif") || lower.contains("nonaktif") ||
        lower.contains("belum aktif") || lower.contains("belum diaktifkan")
    // "aktif" harus kata utuh: substring-nya juga ada di "dinonaktifkan",
    // "diaktifkan", dan "mengaktifkan" yang artinya justru bukan keberhasilan.
    val isActiveOn = Regex("[^a-z]+").split(lower).contains("aktif")
    return when {
        isPartial -> ToastType.WARNING

        isRejected || isValidation -> ToastType.ERROR

        isTurnedOff -> ToastType.WARNING

        lower.contains("berhasil") || lower.contains("sukses") || lower.contains("tersimpan") ||
        lower.contains("disimpan") || lower.contains("diizinkan") || isActiveOn ||
        lower.contains("dibuat") || lower.contains("disalin") ||
        lower.contains("diperbarui") || lower.contains("dirapikan") ||
        lower.contains("dipusatkan") || lower.contains("dikirim") || lower.contains("terhubung") ||
        lower.contains("selesai") -> ToastType.SUCCESS

        lower.contains("terkunci") || lower.contains("terputus") || lower.contains("hapus") ||
        lower.contains("dihapus") || lower.contains("batal") || lower.contains("peringatan") ||
        lower.contains("reset") || lower.contains("dimatikan") || lower.contains("terlepas") ->
            ToastType.WARNING

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

    // Popup Compose menempel pada window activity, sedangkan seluruh pop-up input
    // di aplikasi memakai Dialog (window terpisah) yang selalu digambar Android di
    // atas window activity -> pesan toast tertutup pop-up dan tidak terbaca.
    // key(message) membuat window baru tiap pesan baru sehingga toast selalu
    // berada di lapisan paling atas, juga terhadap pop-up yang sudah terbuka.
    key(message) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                // Window toast dibentangkan sampai ke tepi atas layar; jarak ke bawah
                // status bar murni dihitung content lewat statusBarsPadding(), sehingga
                // tidak dihitung dua kali (decor platform + padding Compose).
                decorFitsSystemWindows = false
            )
        ) {
            // Harus dibaca di dalam content: LocalView di sini adalah view tempat
            // content dialog ini dikomposisi (DialogLayout), bukan view activity induk.
            val toastView = LocalView.current

            // usePlatformDefaultWidth = false membuat Compose mengunci ukuran window
            // dialog sebesar content yang terukur pada SETIAP layout pass
            // (DialogLayout.internalOnLayout -> window.setLayout(measuredW, measuredH)).
            // Jadi ukuran window tidak bisa dipakai untuk menggeser toast; yang tersisa
            // hanyalah gravity-nya, dan tema Dialog masih memakai CENTER sehingga pita
            // setinggi kartu toast itu berada di tengah layar.
            fun applyToastWindow() {
                // DialogWindowProvider dipegang langsung oleh view content dialog,
                // sehingga window-nya tersedia tanpa menunggu view menempel ke window
                // manager (rootView masih null sebelum attach -> cara lama gagal).
                val window = (toastView as? DialogWindowProvider)?.window
                    ?: (toastView.parent as? DialogWindowProvider)?.window
                    ?: toastDialogOf(toastView)?.window
                    ?: return
                // NOT_FOCUSABLE: pop-up input di belakangnya tetap memegang fokus + keyboard.
                // NOT_TOUCH_MODAL: sentuhan di luar kartu toast diteruskan ke window di bawahnya.
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                )
                // Tanpa ini seluruh layar redup setiap kali toast muncul.
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
            }

            SideEffect {
                applyToastWindow()
            }

            LaunchedEffect(toastView) {
                if (toastView.isAttachedToWindow) {
                    applyToastWindow()
                } else {
                    val listener = object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            v.removeOnAttachStateChangeListener(this)
                            applyToastWindow()
                        }

                        override fun onViewDetachedFromWindow(v: View) = Unit
                    }
                    toastView.addOnAttachStateChangeListener(listener)
                    try {
                        awaitCancellation()
                    } finally {
                        toastView.removeOnAttachStateChangeListener(listener)
                    }
                }
            }

            var isVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                isVisible = true
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top)
                    // Window dialog di-lock sebesar content pada tiap layout pass,
                    // jadi gravity dikonfirmasi ulang setiap kali ukuran berubah; kalau
                    // tidak, toast kembali ke tengah layar setelah measure pertama.
                    .onSizeChanged {
                        applyToastWindow()
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = isVisible && !message.isNullOrBlank(),
                    enter = slideInVertically(
                        initialOffsetY = { -it / 3 },
                        animationSpec = tween(240, easing = FastOutLinearInEasing)
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it / 3 },
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
                                        maxLines = 3,
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
    }
}

// Cadangan bila view content dialog ternyata bukan pemegang DialogWindowProvider.
// Dialog yang ditemukan harus benar-benar window tempat view ini berada
// (rootView == decorView-nya); kalau tidak, flag NOT_FOCUSABLE malah menempel ke
// pop-up input induknya dan keyboardnya hilang.
private fun toastDialogOf(view: View?): android.app.Dialog? {
    val root = view?.rootView ?: return null
    var context: Context? = root.context
    var hops = 0
    while (context is ContextWrapper && hops < 12) {
        if (context is android.app.Dialog && context.window?.decorView === root) {
            return context
        }
        context = context.baseContext
        hops++
    }
    return null
}

