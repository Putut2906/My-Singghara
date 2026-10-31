package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.util.Locale
import kotlin.math.abs

/**
 * Palet warna gradient dinamis untuk Avatar inisial nama dan profil
 */
val defaultAvatarGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))), // Blue
    Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857))), // Emerald
    Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))), // Violet
    Brush.linearGradient(listOf(Color(0xFFEA580C), Color(0xFFC2410C))), // Orange Coral
    Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFFBE123C))), // Rose Pink
    Brush.linearGradient(listOf(Color(0xFF0891B2), Color(0xFF0E7490))), // Cyan Teal
    Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF3730A3)))  // Indigo
)

val defaultAvatarIcons: List<ImageVector> = listOf(
    Icons.Default.Person,
    Icons.Default.AccountBalance,
    Icons.Default.Star,
    Icons.Default.Favorite,
    Icons.Default.Palette
)

/**
 * Menghasilkan 1-2 huruf inisial kapital dari nama pengguna atau email.
 * Contoh:
 * - "Putut Singgih Pamungkas" -> "PS"
 * - "Putut" -> "PU"
 * - "ahmad" -> "AH"
 * - "user@gmail.com" -> "US"
 */
fun getInitialsFromName(name: String?): String {
    if (name.isNullOrBlank()) return "U"
    val clean = name.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
    val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "U"
        parts.size == 1 -> {
            val single = parts[0]
            if (single.length >= 2) {
                single.take(2).uppercase(Locale.getDefault())
            } else {
                single.uppercase(Locale.getDefault())
            }
        }
        else -> {
            val first = parts[0].firstOrNull()?.uppercaseChar() ?: 'U'
            val second = parts[1].firstOrNull()?.uppercaseChar() ?: ""
            "$first$second"
        }
    }
}

/**
 * Menghasilkan gradien warna unik yang konsisten berdasarkan hash nama pengguna.
 */
fun getAvatarGradientForName(name: String?): Brush {
    if (name.isNullOrBlank()) return defaultAvatarGradients[0]
    val hash = abs(name.hashCode())
    return defaultAvatarGradients[hash % defaultAvatarGradients.size]
}

/**
 * Komponen Avatar Pengguna Universal untuk My Singghara:
 * 1. Akun Google / Facebook / Foto Kustom: Menampilkan foto asli beresolusi tinggi dengan crop lingkaran.
 * 2. Akun Email / Tanpa Foto: Menampilkan foto inisial nama (1-2 huruf kapital) dengan background gradient elegan.
 * 3. Guest / Belum Login: Menampilkan icon avatar pilihan pengguna.
 */
@Composable
fun AppUserAvatar(
    name: String,
    imageUri: String,
    avatarIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    textSize: TextUnit = 16.sp,
    isLoggedIn: Boolean = false,
    contentDescription: String = "Foto Profil",
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    // Jika memiliki URI foto (URL Google/Facebook/File lokal galeri)
    val hasCustomImage = imageUri.isNotBlank() && (
        avatarIndex == -1 ||
        imageUri.startsWith("http://", ignoreCase = true) ||
        imageUri.startsWith("https://", ignoreCase = true) ||
        imageUri.startsWith("content://", ignoreCase = true) ||
        imageUri.startsWith("file://", ignoreCase = true)
    )

    // Jika menggunakan avatar inisial (didaftarkan via email, atau avatarIndex == -2, atau user login tanpa foto)
    val isInitialsAvatar = !hasCustomImage && (avatarIndex == -2 || isLoggedIn || (avatarIndex == -1 && imageUri.isBlank()))

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        when {
            hasCustomImage -> {
                SubcomposeAsyncImage(
                    model = imageUri,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(getAvatarGradientForName(name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getInitialsFromName(name),
                                color = Color.White,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    error = {
                        // Fallback ke inisial nama jika foto gagal dimuat (misal offline)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(getAvatarGradientForName(name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getInitialsFromName(name),
                                color = Color.White,
                                fontSize = textSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
            isInitialsAvatar -> {
                // Inisial Nama (Email User / No Photo)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(getAvatarGradientForName(name))
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getInitialsFromName(name),
                        color = Color.White,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            else -> {
                // Guest / Preset Icon Avatar
                val gradient = defaultAvatarGradients.getOrElse(avatarIndex.coerceAtLeast(0)) { defaultAvatarGradients[0] }
                val icon = defaultAvatarIcons.getOrElse(avatarIndex.coerceAtLeast(0)) { defaultAvatarIcons[0] }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradient)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = Color.White,
                        modifier = Modifier.size(size * 0.52f)
                    )
                }
            }
        }
    }
}
