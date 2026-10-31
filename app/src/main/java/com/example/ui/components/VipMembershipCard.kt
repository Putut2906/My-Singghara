package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.firebase.CloudUserItem
import com.example.firebase.FirebaseAuthManager
import com.example.utils.QrCodeHelper
import com.example.utils.VipMembershipCardGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sisi Tampilan Kartu VIP
 */
enum class VipCardSide {
    FRONT,   // Tampilan Depan: VIP EXCLUSIVE ACCESS (Badge Chamfered tengah + pola geometris)
    MEMBER   // Tampilan Member / Belakang: 5 Bintang Emas, Frame Chamfered Member ID, Nama Member, Garis Aksen Emas
}

/**
 * Tema Visual Kartu Membership untuk tiap rentang waktu:
 * 1 Bulan = Titanium / Silver VIP
 * 3 Bulan = Bronze / Amber VIP
 * 5 Bulan = Emerald / Jade VIP
 * 7 Bulan = Royal Ruby / Amethyst VIP
 * 12 Bulan = Pure 24K Gold Master VIP (Sesuai Desain Lampiran)
 */
data class MembershipTierTheme(
    val tierTitle: String,
    val durationLabel: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val lightAccentColor: Color,
    val metallicGradient: Brush,
    val backgroundColors: List<Color>,
    val plateColor: Color,
    val plateBorderColor: Color,
    val glowColor: Color,
    val starColor: Color,
    val badgeBorderColor: Color
) {
    companion object {
        fun forMonths(months: Int, isAdmin: Boolean = false): MembershipTierTheme {
            if (isAdmin) {
                // Admin: Diamond Cyber Gold
                return MembershipTierTheme(
                    tierTitle = "ADMINISTRATOR VIP",
                    durationLabel = "Akses Penuh Permanen",
                    primaryColor = Color(0xFFFFD700),
                    secondaryColor = Color(0xFF38BDF8),
                    lightAccentColor = Color(0xFFFEF08A),
                    metallicGradient = Brush.linearGradient(
                        listOf(Color(0xFFFEF08A), Color(0xFFFFD700), Color(0xFF38BDF8), Color(0xFFF59E0B))
                    ),
                    backgroundColors = listOf(Color(0xFF0C1017), Color(0xFF141C2B), Color(0xFF080B10)),
                    plateColor = Color(0xFF141C2B),
                    plateBorderColor = Color(0xFF38BDF8),
                    glowColor = Color(0x66FFD700),
                    starColor = Color(0xFFFFD700),
                    badgeBorderColor = Color(0xFFFFD700)
                )
            }

            return when (months) {
                1 -> {
                    // 1 Bulan: Platinum Silver / Titanium
                    MembershipTierTheme(
                        tierTitle = "SILVER VIP",
                        durationLabel = "1 Bulan (30 Hari)",
                        primaryColor = Color(0xFFE2E8F0),
                        secondaryColor = Color(0xFF94A3B8),
                        lightAccentColor = Color(0xFFF8FAFC),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFFCBD5E1))
                        ),
                        backgroundColors = listOf(Color(0xFF11141B), Color(0xFF1C2230), Color(0xFF0D0F14)),
                        plateColor = Color(0xFF1C2230),
                        plateBorderColor = Color(0xFF94A3B8),
                        glowColor = Color(0x55CBD5E1),
                        starColor = Color(0xFFE2E8F0),
                        badgeBorderColor = Color(0xFFCBD5E1)
                    )
                }
                3 -> {
                    // 3 Bulan: Bronze / Copper Prestige VIP
                    MembershipTierTheme(
                        tierTitle = "BRONZE VIP",
                        durationLabel = "3 Bulan (90 Hari)",
                        primaryColor = Color(0xFFF59E0B),
                        secondaryColor = Color(0xFFD97706),
                        lightAccentColor = Color(0xFFFDE68A),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFB45309))
                        ),
                        backgroundColors = listOf(Color(0xFF1A120D), Color(0xFF2B1C13), Color(0xFF120B07)),
                        plateColor = Color(0xFF2B1C13),
                        plateBorderColor = Color(0xFFD97706),
                        glowColor = Color(0x66F59E0B),
                        starColor = Color(0xFFFBBF24),
                        badgeBorderColor = Color(0xFFF59E0B)
                    )
                }
                5 -> {
                    // 5 Bulan: Emerald / Jade VIP
                    MembershipTierTheme(
                        tierTitle = "EMERALD VIP",
                        durationLabel = "5 Bulan (150 Hari)",
                        primaryColor = Color(0xFF34D399),
                        secondaryColor = Color(0xFF10B981),
                        lightAccentColor = Color(0xFFA7F3D0),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFD1FAE5), Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669))
                        ),
                        backgroundColors = listOf(Color(0xFF071B14), Color(0xFF0C2B20), Color(0xFF04120D)),
                        plateColor = Color(0xFF0C2B20),
                        plateBorderColor = Color(0xFF10B981),
                        glowColor = Color(0x6610B981),
                        starColor = Color(0xFF34D399),
                        badgeBorderColor = Color(0xFF34D399)
                    )
                }
                7 -> {
                    // 7 Bulan: Royal Ruby / Amethyst VIP
                    MembershipTierTheme(
                        tierTitle = "ROYAL VIP",
                        durationLabel = "7 Bulan (210 Hari)",
                        primaryColor = Color(0xFFC084FC),
                        secondaryColor = Color(0xFFA855F7),
                        lightAccentColor = Color(0xFFF5D0FE),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFFDF4FF), Color(0xFFC084FC), Color(0xFFA855F7), Color(0xFFEC4899))
                        ),
                        backgroundColors = listOf(Color(0xFF190C28), Color(0xFF28123F), Color(0xFF10071A)),
                        plateColor = Color(0xFF28123F),
                        plateBorderColor = Color(0xFFA855F7),
                        glowColor = Color(0x66A855F7),
                        starColor = Color(0xFFE879F9),
                        badgeBorderColor = Color(0xFFC084FC)
                    )
                }
                12 -> {
                    // 12 Bulan: 24K Pure Gold Master VIP (Sesuai Desain Asli Template Lampiran)
                    MembershipTierTheme(
                        tierTitle = "GOLD MASTER VIP",
                        durationLabel = "12 Bulan (1 Tahun)",
                        primaryColor = Color(0xFFFFD700),
                        secondaryColor = Color(0xFFF59E0B),
                        lightAccentColor = Color(0xFFFEF08A),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFFFFBEB), Color(0xFFFEF08A), Color(0xFFFFD700), Color(0xFFF59E0B), Color(0xFFD97706))
                        ),
                        backgroundColors = listOf(Color(0xFF0F0F12), Color(0xFF1A1A22), Color(0xFF0A0A0D)),
                        plateColor = Color(0xFF1A1A22),
                        plateBorderColor = Color(0xFFEAB308),
                        glowColor = Color(0x77FFD700),
                        starColor = Color(0xFFFFD700),
                        badgeBorderColor = Color(0xFFFFD700)
                    )
                }
                else -> {
                    // Default / Tanpa Batas / Lifetime Free
                    MembershipTierTheme(
                        tierTitle = "STANDARD VIP",
                        durationLabel = if (months > 0) "$months Bulan" else "Tanpa Batas",
                        primaryColor = Color(0xFFCBD5E1),
                        secondaryColor = Color(0xFF64748B),
                        lightAccentColor = Color(0xFFF1F5F9),
                        metallicGradient = Brush.linearGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1), Color(0xFF64748B))
                        ),
                        backgroundColors = listOf(Color(0xFF14161C), Color(0xFF20232D), Color(0xFF0D0F14)),
                        plateColor = Color(0xFF20232D),
                        plateBorderColor = Color(0xFF64748B),
                        glowColor = Color(0x44CBD5E1),
                        starColor = Color(0xFFCBD5E1),
                        badgeBorderColor = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

/**
 * Format ID Member menjadi 4-4-4 digit seperti pada gambar contoh (e.g. 0219-2129-1284)
 */
fun formatMemberId(rawUid: String): String {
    if (rawUid.isBlank()) return "0219-2129-1284"
    val clean = rawUid.filter { it.isLetterOrDigit() }.uppercase()
    if (clean.length < 12) {
        val padded = clean.padEnd(12, 'X')
        return "${padded.substring(0, 4)}-${padded.substring(4, 8)}-${padded.substring(8, 12)}"
    }
    return "${clean.substring(0, 4)}-${clean.substring(4, 8)}-${clean.substring(8, 12)}"
}

/**
 * Menghitung jumlah icon bintang sesuai masa berlaku membership:
 * - 1 Bulan: 1 bintang
 * - 3 Bulan: 2 bintang
 * - 5 Bulan: 3 bintang
 * - 7 Bulan: 4 bintang
 * - 12 Bulan: 5 bintang
 * - Tanpa Batas / Admin / Lifetime: 6 bintang
 */
fun getVipStarCount(durationMonths: Int, isAdmin: Boolean = false): Int {
    if (isAdmin || durationMonths <= 0) return 6
    return when (durationMonths) {
        1 -> 1
        3 -> 2
        5 -> 3
        7 -> 4
        12 -> 5
        else -> when {
            durationMonths < 3 -> 1
            durationMonths < 5 -> 2
            durationMonths < 7 -> 3
            durationMonths < 12 -> 4
            durationMonths == 12 -> 5
            else -> 6
        }
    }
}

/**
 * Komponen Desain Kartu VIP Membership Modern & Mewah Sesuai Lampiran Pengguna
 * Mendukung 2 Tampilan: FRONT (VIP Exclusive Access) & MEMBER (5 Bintang Emas, Member ID, Nama Member)
 */
@Composable
fun VipMembershipCard(
    userName: String,
    memberId: String,
    durationMonths: Int,
    startDate: Long = 0L,
    endDate: Long = 0L,
    isActive: Boolean = true,
    isAdmin: Boolean = false,
    adminPhoneNumber: String = "088806643415",
    userEmail: String = "",
    userPassword: String = "",
    cardSide: VipCardSide = VipCardSide.MEMBER,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = remember(durationMonths, isAdmin) {
        MembershipTierTheme.forMonths(durationMonths, isAdmin)
    }

    val formattedId = remember(memberId) {
        formatMemberId(memberId)
    }

    val effectivePassword = remember(userPassword, userEmail) {
        (if (userPassword.isNotBlank()) userPassword
        else FirebaseAuthManager.getSavedPasswordForEmail(context, userEmail)).ifBlank {
            val googleId = userEmail.hashCode().toString()
            "GAuth#${googleId.take(10)}_${userEmail.take(6).replace(Regex("[^a-zA-Z0-9]"), "x")}!9"
        }
    }

    val quickLoginPayload = remember(userEmail, effectivePassword) {
        VipMembershipCardGenerator.buildQuickLoginPayload(userEmail, effectivePassword)
    }

    val isExpired = !isAdmin && endDate > 0L && System.currentTimeMillis() >= endDate

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.58f) // Rasio aspek kartu standar (Kartu Kredit/Member)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = theme.glowColor,
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = onCardClick != null) { onCardClick?.invoke() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.backgroundColors.first())
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // LAYER 1: Background Gradient & Tekstur Mesh Carbon / Diamond Geometris
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Base Gradient
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(theme.backgroundColors[1], theme.backgroundColors[0], theme.backgroundColors[2]),
                        center = Offset(width * 0.5f, height * 0.4f),
                        radius = width * 0.75f
                    )
                )

                // Pola Carbon Weave / Diamond Grid Dots Mewah
                val spacing = 14.dp.toPx()
                val dotSize = 1.3.dp.toPx()
                var y = 0f
                var row = 0
                while (y < height) {
                    val xOffset = if (row % 2 == 0) 0f else spacing / 2f
                    var x = xOffset
                    while (x < width) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = dotSize,
                            center = Offset(x, y)
                        )
                        x += spacing
                    }
                    y += spacing * 0.75f
                    row++
                }

                // LAYER 2: Plat Geometris Bersudut Potong 45 Derajat (Angular Cut Plates)
                // Plat Atas Kiri (Top-Left Beveled Dark Plate)
                val topPlatePath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(width * 0.44f, 0f)
                    lineTo(width * 0.34f, height * 0.22f)
                    lineTo(width * 0.12f, height * 0.22f)
                    lineTo(0f, height * 0.38f)
                    close()
                }
                drawPath(path = topPlatePath, color = theme.plateColor)

                // Plat Bawah (Bottom-Right Beveled Plate)
                val bottomPlatePath = Path().apply {
                    moveTo(0f, height * 0.74f)
                    lineTo(width * 0.38f, height * 0.74f)
                    lineTo(width * 0.58f, height)
                    lineTo(0f, height)
                    close()
                }
                drawPath(path = bottomPlatePath, color = theme.plateColor)

                // Plat Pojok Kanan Bawah
                val cornerPlate = Path().apply {
                    moveTo(width * 0.72f, height)
                    lineTo(width * 0.58f, height * 0.52f)
                    lineTo(width * 0.82f, height * 0.52f)
                    lineTo(width, height * 0.74f)
                    lineTo(width, height)
                    close()
                }
                drawPath(path = cornerPlate, color = theme.plateColor.copy(alpha = 0.85f))

                // Border Aksen Kartu Luar (Sleek Outer Metallic Border)
                drawRoundRect(
                    brush = theme.metallicGradient,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // LAYER 3: KONTEN TEKS & BADGE (FRONT ATAU MEMBER SIDE)
            if (cardSide == VipCardSide.FRONT) {
                // ================= TAMPILAN DEPAN: "VIP" & QR CODE LOGIN CEPAT =================
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Badge Chamfered Persegi Panjang Tengah (Cut Corner Frame)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(86.dp)
                    ) {
                        // Gambar Frame Chamfered di Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cut = 16.dp.toPx()

                            val chamferedPath = Path().apply {
                                moveTo(cut, 0f)
                                lineTo(w, 0f)
                                lineTo(w, h - cut)
                                lineTo(w - cut, h)
                                lineTo(0f, h)
                                lineTo(0f, cut)
                                close()
                            }

                            // Fill semi transparan
                            drawPath(
                                path = chamferedPath,
                                color = Color.Black.copy(alpha = 0.55f),
                                style = Fill
                            )
                            // Stroke Emas/Metallic
                            drawPath(
                                path = chamferedPath,
                                brush = theme.metallicGradient,
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                        }

                        // Isi Tengah: "VIP" di Kiri & QR Code Login Cepat di Kanan (Menggantikan "EXCLUSIVE ACCESS")
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "VIP",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    style = TextStyle(brush = theme.metallicGradient),
                                    letterSpacing = 2.sp
                                )
                                Text(
                                    text = "LOGIN CEPAT",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = TextStyle(brush = theme.metallicGradient),
                                    letterSpacing = 1.2.sp
                                )
                            }

                            // QR Code Login Cepat
                            val quickLoginQrBitmap = remember(quickLoginPayload) {
                                QrCodeHelper.generateQrBitmap(quickLoginPayload, 220)
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "SCAN QR",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "UNTUK MASUK",
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = theme.primaryColor,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                if (quickLoginQrBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = quickLoginQrBitmap.asImageBitmap(),
                                            contentDescription = "QR Code Login Cepat",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Label Durasi di Sudut Kanan Atas
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = theme.durationLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.primaryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Tanda Brand Singghara di Bawah
                    Text(
                        text = "MY SINGGHARA OFFICIAL MEMBERSHIP",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                    )
                }
            } else {
                // ================= TAMPILAN MEMBER: 5 BINTANG, MEMBER ID, NAMA MEMBER =================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Baris Atas: 5 Bintang Emas & Frame Member ID di Kanan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Sisi Kiri: Bintang Emas & Logo Aplikasi My Singghara di Bawahnya
                        Column(
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            // Icon Bintang Emas Sesuai Masa Berlaku (1: 1★, 3: 2★, 5: 3★, 7: 4★, 12: 5★, Tanpa Batas: 6★)
                            val starCount = remember(durationMonths, isAdmin) {
                                getVipStarCount(durationMonths, isAdmin)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(starCount) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Bintang VIP ($starCount Bintang)",
                                        tint = theme.starColor,
                                        modifier = Modifier.size(if (starCount >= 6) 12.5.dp else 14.dp)
                                    )
                                }
                            }

                            // Logo Aplikasi My Singghara di Bawah Icon Bintang
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.2.dp,
                                        brush = theme.metallicGradient,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.ic_app_logo),
                                    contentDescription = "Logo My Singghara",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // Frame Chamfered Member ID (Kotak Sudut Terpotong Khas)
                        Box(
                            modifier = Modifier
                                .width(170.dp)
                                .height(58.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val cut = 12.dp.toPx()

                                val idBoxPath = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(w - cut, 0f)
                                    lineTo(w, cut)
                                    lineTo(w, h)
                                    lineTo(cut, h)
                                    lineTo(0f, h - cut)
                                    close()
                                }

                                drawPath(
                                    path = idBoxPath,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    style = Fill
                                )
                                drawPath(
                                    path = idBoxPath,
                                    brush = theme.metallicGradient,
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = formattedId,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(brush = theme.metallicGradient),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = adminPhoneNumber.ifBlank { "088806643415" },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = theme.primaryColor.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Bagian Bawah: Nama Member Besar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = userName.ifEmpty { "MEMBER NAME SURNAME" }.uppercase(),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Black,
                            style = TextStyle(brush = theme.metallicGradient),
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${theme.tierTitle} • ${theme.durationLabel}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.primaryColor.copy(alpha = 0.9f)
                            )
                            if (endDate > 0L) {
                                val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                                Text(
                                    text = "Exp: ${sdf.format(Date(endDate))}",
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Indikator Status Akun (Aktif / Expired) Pojok Kiri Bawah/Atas Tipis
            if (isExpired) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDC2626),
                        contentColor = Color.White
                    ) {
                        Text(
                            text = "KEDALUWARSA",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog Popup Detail Membership Lengkap
 * Ditampilkan saat pengguna mengklik Profil User.
 * Menampilkan:
 * 1. Desain Kartu VIP Membership interaktif (bisa dibalik Depan & Belakang).
 * 2. Status durasi (1, 3, 5, 7, atau 12 Bulan) dengan warna khusus.
 * 3. Detail lengkap: Nama, Member ID, Tanggal Mulai, Tanggal Selesai, Sisa Hari.
 * 4. Tombol Hubungi Admin via WhatsApp langsung ke nomor admin (default: 088806643415 / terkini).
 * 5. Jika akun Admin, tombol langsung untuk mengatur durasi membership.
 */
@Composable
fun VipMembershipDetailDialog(
    user: CloudUserItem?,
    currentUserName: String,
    currentUserEmail: String,
    adminPhoneNumber: String = "088806643415",
    isViewerAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onAdminManageMembership: ((CloudUserItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // Data Pengguna
    val effectiveName = user?.displayName?.ifEmpty { currentUserName } ?: currentUserName.ifEmpty { "Pengguna Setia" }
    val effectiveEmail = user?.email?.ifEmpty { currentUserEmail } ?: currentUserEmail
    val effectiveUid = user?.uid ?: "VIP-MEMBER-USER"
    val durationMonths = user?.membershipDurationMonths ?: 0
    val isUserAdmin = user?.isAdmin ?: false
    val startDate = user?.membershipStartDate ?: 0L
    val endDate = user?.membershipEndDate ?: 0L
    val isActive = user?.isActive ?: true
    val isExpired = user?.isMembershipExpired() ?: (!isUserAdmin && endDate > 0L && System.currentTimeMillis() >= endDate)

    val savedPassword = remember(effectiveEmail) {
        FirebaseAuthManager.getSavedPasswordForEmail(context, effectiveEmail)
    }

    val theme = remember(durationMonths, isUserAdmin) {
        MembershipTierTheme.forMonths(durationMonths, isUserAdmin)
    }

    var cardSide by remember { mutableStateOf(VipCardSide.MEMBER) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Dialog: Judul & Tombol Tutup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(theme.primaryColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = theme.primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Kartu Membership VIP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = theme.tierTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.primaryColor
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // KARTU VIP SESUAI DESAIN TEMPLATE LAMPIRAN
                VipMembershipCard(
                    userName = effectiveName,
                    memberId = effectiveUid,
                    durationMonths = durationMonths,
                    startDate = startDate,
                    endDate = endDate,
                    isActive = isActive,
                    isAdmin = isUserAdmin,
                    adminPhoneNumber = adminPhoneNumber,
                    userEmail = effectiveEmail,
                    userPassword = savedPassword,
                    cardSide = cardSide,
                    modifier = Modifier.fillMaxWidth(),
                    onCardClick = {
                        // Ketuk kartu untuk membalik sisi depan / belakang
                        cardSide = if (cardSide == VipCardSide.MEMBER) VipCardSide.FRONT else VipCardSide.MEMBER
                    }
                )

                // Tombol Pembalik Kartu (Flip Front / Back)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (cardSide == VipCardSide.MEMBER) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (cardSide == VipCardSide.MEMBER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { cardSide = VipCardSide.MEMBER }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Kartu Member", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (cardSide == VipCardSide.FRONT) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (cardSide == VipCardSide.FRONT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { cardSide = VipCardSide.FRONT }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Tampak Depan (VIP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // INFORMASI RINCIAN STATUS MEMBERSHIP
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Informasi Hak Akses",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Baris 1: Status Akun
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Status Membership:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isUserAdmin || (!isExpired && isActive)) (if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)) else Color(0xFFFEE2E2),
                                border = BorderStroke(1.dp, if (isUserAdmin || (!isExpired && isActive)) (if (isDark) Color(0xFF059669) else Color(0xFF86EFAC)) else Color(0xFFFCA5A5))
                            ) {
                                Text(
                                    text = if (isUserAdmin) "AKTIF (ADMIN)" else if (!isActive) "NONAKTIF" else if (isExpired) "KEDALUWARSA" else "AKTIF",
                                    color = if (isUserAdmin || (!isExpired && isActive)) (if (isDark) Color(0xFF6EE7B7) else Color(0xFF15803D)) else Color(0xFFDC2626),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Baris 2: Paket Durasi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Paket Durasi:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = theme.durationLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Baris 3: Member ID & Tombol Salin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Member ID:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = formatMemberId(effectiveUid),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { copyToClipboard("Member ID", formatMemberId(effectiveUid)) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin ID", modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        // Baris 4: Masa Berlaku
                        if (endDate > 0L) {
                            val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("id", "ID"))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Berlaku Hingga:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = sdf.format(Date(endDate)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Baris 5: Sisa Hari
                            val diff = endDate - System.currentTimeMillis()
                            val remainingDays = if (diff > 0) (diff / (1000 * 60 * 60 * 24)) else 0L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Sisa Waktu:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (remainingDays > 0) "$remainingDays Hari Lagi" else "Habis",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingDays > 0) theme.primaryColor else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // TOMBOL TINDAKAN:
                // 1. Tombol Hubungi Admin via WhatsApp (Menggunakan Nomor Admin Default / Baru)
                Button(
                    onClick = {
                        try {
                            // Konversi nomor 0888... menjadi format internasional 62888...
                            val cleanNumber = adminPhoneNumber.trim().replace("+", "").replace("-", "").replace(" ", "")
                            val internationalNumber = if (cleanNumber.startsWith("0")) "62" + cleanNumber.substring(1) else cleanNumber

                            val memberIdFmt = formatMemberId(effectiveUid)
                            val message = "Halo Admin My Singghara,%0ASaya ingin menanyakan / mengajukan perpanjangan membership VIP:%0A- Nama: $effectiveName%0A- Member ID: $memberIdFmt%0A- Paket Saat Ini: ${theme.tierTitle} (${theme.durationLabel})%0A- UID: $effectiveUid%0AMohon bantuan informasinya. Terima kasih!"
                            val waUrl = "https://wa.me/$internationalNumber?text=$message"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            copyToClipboard("Nomor WhatsApp Admin", adminPhoneNumber)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "WhatsApp",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hubungi Admin via WhatsApp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // FITUR PRATINJAU & BAGIKAN SUPAYA KARTU MEMBERSHIP BISA DICETAK
                var showPrintPreviewDialog by remember { mutableStateOf(false) }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, theme.primaryColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(theme.primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Cetak Kartu",
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fitur Pratinjau & Bagikan",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pratinjau, cetak langsung via Wi-Fi/Bluetooth printer, atau bagikan file cetak",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showPrintPreviewDialog = true },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.primaryColor
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pratinjau & Cetak",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val bitmap = VipMembershipCardGenerator.createCombinedDualSideFromUser(
                                        context = context,
                                        userName = effectiveName,
                                        memberId = effectiveUid,
                                        durationMonths = durationMonths,
                                        isAdmin = isUserAdmin,
                                        adminPhoneNumber = adminPhoneNumber,
                                        userEmail = effectiveEmail,
                                        userPassword = savedPassword
                                    )
                                    VipMembershipCardGenerator.saveAndShareVipCard(
                                        context = context,
                                        bitmap = bitmap,
                                        fileName = "${effectiveName}_Kartu_VIP_Dual"
                                    )
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, theme.primaryColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = theme.primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bagikan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primaryColor
                                )
                            }
                        }
                    }
                }

                if (showPrintPreviewDialog) {
                    VipCardPrintPreviewDialog(
                        userName = effectiveName,
                        memberId = effectiveUid,
                        durationMonths = durationMonths,
                        isAdmin = isUserAdmin,
                        adminPhoneNumber = adminPhoneNumber,
                        onDismiss = { showPrintPreviewDialog = false }
                    )
                }

                // 3. Tombol Khusus Admin untuk Atur Masa Membership
                if (isViewerAdmin && user != null && onAdminManageMembership != null) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onAdminManageMembership(user)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ubah Masa Membership (1, 3, 5, 7, 12 Bulan)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog Pratinjau & Cetak Kartu VIP Membership
 * Menampilkan pratinjau lembar cetak siap potong (Depan & Belakang),
 * opsi cetak langsung via sistem Android (Print Spooler),
 * ekspor gambar resolusi tinggi (PNG), dan simpan ke galeri perangkat.
 */
@Composable
fun VipCardPrintPreviewDialog(
    userName: String,
    memberId: String,
    durationMonths: Int,
    isAdmin: Boolean = false,
    adminPhoneNumber: String = "088806643415",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val theme = remember(durationMonths, isAdmin) {
        MembershipTierTheme.forMonths(durationMonths, isAdmin)
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Siap Cetak (A4 Dual), 1: Tampak Depan, 2: Tampak Belakang
    var frontBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var combinedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(userName, memberId, durationMonths, isAdmin, adminPhoneNumber) {
        isLoading = true
        withContext(Dispatchers.Default) {
            val f = VipMembershipCardGenerator.createVipCardFromDetails(
                context = context,
                userName = userName,
                memberId = memberId,
                durationMonths = durationMonths,
                isAdmin = isAdmin,
                isFrontSide = true,
                adminPhoneNumber = adminPhoneNumber
            )
            val b = VipMembershipCardGenerator.createVipCardFromDetails(
                context = context,
                userName = userName,
                memberId = memberId,
                durationMonths = durationMonths,
                isAdmin = isAdmin,
                isFrontSide = false,
                adminPhoneNumber = adminPhoneNumber
            )
            val c = VipMembershipCardGenerator.combineFrontAndBackBitmaps(f, b, userName)
            frontBitmap = f
            backBitmap = b
            combinedBitmap = c
        }
        isLoading = false
    }

    val currentBitmap = when (selectedTab) {
        1 -> frontBitmap
        2 -> backBitmap
        else -> combinedBitmap
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(theme.primaryColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = theme.primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pratinjau Cetak Kartu VIP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${theme.tierTitle} • ${userName.ifEmpty { "Member" }}",
                                fontSize = 11.5.sp,
                                color = theme.primaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Pilihan Tampilan Cetak
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = theme.primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Siap Cetak (A4)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Tampak Depan", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Tampak Belakang", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Area Gambar Pratinjau
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = theme.primaryColor,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "Menyiapkan pratinjau kartu resolusi tinggi...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (currentBitmap != null) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "Pratinjau Kartu VIP",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Keterangan Panduan Cetak
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = theme.primaryColor
                        )
                        Text(
                            text = "Gunakan kertas foto tebal (230-260 gsm) untuk hasil cetak kartu yang kaku dan mengkilap.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tombol Aksi: CETAK, BAGIKAN, SIMPAN GALERI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tombol 1: Cetak Langsung (Printer Wi-Fi / Bluetooth / Simpan PDF)
                        Button(
                            onClick = {
                                currentBitmap?.let { bmp ->
                                    VipMembershipCardGenerator.printVipCard(
                                        context = context,
                                        bitmap = bmp,
                                        jobName = "Kartu_VIP_${userName.ifEmpty { "Member" }}"
                                    )
                                }
                            },
                            enabled = !isLoading && currentBitmap != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.primaryColor
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Cetak",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cetak Sekarang",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        // Tombol 2: Bagikan File Gambar (PNG)
                        Button(
                            onClick = {
                                currentBitmap?.let { bmp ->
                                    val tag = when (selectedTab) {
                                        1 -> "Depan"
                                        2 -> "Belakang"
                                        else -> "SiapCetak_A4"
                                    }
                                    VipMembershipCardGenerator.saveAndShareVipCard(
                                        context = context,
                                        bitmap = bmp,
                                        fileName = "${userName.ifEmpty { "Member" }}_Kartu_VIP_$tag"
                                    )
                                }
                            },
                            enabled = !isLoading && currentBitmap != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bagikan (PNG)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Tombol 3: Simpan ke Galeri Foto Perangkat
                    OutlinedButton(
                        onClick = {
                            currentBitmap?.let { bmp ->
                                val tag = when (selectedTab) {
                                    1 -> "Depan"
                                    2 -> "Belakang"
                                    else -> "SiapCetak_A4"
                                }
                                VipMembershipCardGenerator.saveCardToGallery(
                                    context = context,
                                    bitmap = bmp,
                                    fileName = "${userName.ifEmpty { "Member" }}_Kartu_VIP_$tag"
                                )
                            }
                        },
                        enabled = !isLoading && currentBitmap != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Simpan Galeri",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Simpan Gambar ke Galeri",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Komponen Input Nomor Admin (Default: 088806643415, bisa diganti)
 * Diletakkan tepat di sebelah kiri icon titik 3 di Header Halaman Manajemen Akun.
 */
@Composable
fun AdminPhoneNumberInputField(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    var isEditing by remember { mutableStateOf(false) }
    var tempNumber by remember(phoneNumber) { mutableStateOf(phoneNumber) }
    var showEditDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Komponen Tampilan di Sebelah Kiri Icon Titik 3
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
        border = BorderStroke(
            1.dp,
            if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .height(34.dp)
            .clickable {
                showEditDialog = true
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Icon Telepon / WhatsApp
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Nomor Admin",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(11.dp)
                )
            }

            // Input / Teks Nomor Telepon Admin
            Text(
                text = phoneNumber.ifEmpty { "088806643415" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Icon Pensil / Edit
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Ubah Nomor Admin",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
    }

    // Dialog Pengubahan Nomor Admin
    if (showEditDialog) {
        var inputVal by remember { mutableStateOf(phoneNumber.ifEmpty { "088806643415" }) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Ubah Nomor Kontak Admin",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nomor ini akan digunakan sebagai tujuan pesan WhatsApp saat anggota menghubungi admin untuk pendaftaran atau perpanjangan membership.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it },
                        label = { Text("Nomor Admin (WhatsApp)") },
                        placeholder = { Text("088806643415") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onPhoneNumberChange(inputVal.trim())
                                showEditDialog = false
                            }
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (inputVal != "088806643415") {
                                IconButton(onClick = { inputVal = "088806643415" }) {
                                    Text("Reset", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Default pabrik: 088806643415",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalNum = inputVal.trim().ifEmpty { "088806643415" }
                        onPhoneNumberChange(finalNum)
                        showEditDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Simpan Nomor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
