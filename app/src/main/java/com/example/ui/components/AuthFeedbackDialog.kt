package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.AuthFeedbackState
import com.example.ui.AuthFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthFeedbackDialog(
    feedbackState: AuthFeedbackState,
    onDismiss: () -> Unit
) {
    // Entrance spring animation for the dialog card
    val enterScale = remember { Animatable(0.78f) }
    val enterAlpha = remember { Animatable(0f) }

    LaunchedEffect(feedbackState.type) {
        launch {
            enterScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            enterAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(280, easing = LinearOutSlowInEasing)
            )
        }
    }

    // Infinite animations for radiant pulsing, floating, rotation, and dispatch waves
    val infiniteTransition = rememberInfiniteTransition(label = "auth_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )
    val wiggleAngle by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wiggle_angle"
    )
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_scale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha"
    )

    // Auto-dismiss timer for non-confirm and non-OTP-sent types
    LaunchedEffect(feedbackState.type) {
        if (feedbackState.type != AuthFeedbackType.CONFIRM_LOGOUT &&
            feedbackState.type != AuthFeedbackType.SUCCESS_SEND_OTP_EMAIL
        ) {
            delay(3500)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (feedbackState.type != AuthFeedbackType.CONFIRM_LOGOUT) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = feedbackState.type == AuthFeedbackType.CONFIRM_LOGOUT || feedbackState.type == AuthFeedbackType.SUCCESS_SEND_OTP_EMAIL,
            dismissOnClickOutside = feedbackState.type != AuthFeedbackType.CONFIRM_LOGOUT,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .padding(16.dp)
                .scale(enterScale.value)
                .alpha(enterAlpha.value),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            border = BorderStroke(
                width = 1.2.dp,
                color = when (feedbackState.type) {
                    AuthFeedbackType.CONFIRM_LOGOUT -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    AuthFeedbackType.SUCCESS_LOGIN -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    AuthFeedbackType.SUCCESS_REGISTER -> Color(0xFF0F766E).copy(alpha = 0.45f)
                    AuthFeedbackType.SUCCESS_SEND_OTP_EMAIL -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                    AuthFeedbackType.SUCCESS_LOGOUT -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (feedbackState.type) {
                    AuthFeedbackType.CONFIRM_LOGOUT -> {
                        // Animasi Icon Logout Berdenyut
                        Box(
                            modifier = Modifier.size(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = ringAlpha * 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.errorContainer,
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Konfirmasi Keluar",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (feedbackState.userName.isNotEmpty() || feedbackState.userEmail.isNotEmpty()) {
                                "Apakah Anda yakin ingin keluar dari akun\n${feedbackState.userName.ifEmpty { feedbackState.userEmail }}?"
                            } else {
                                "Apakah Anda yakin ingin keluar dari akun Anda?"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Data lokal tetap tersimpan di perangkat Anda.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text("Batal", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    feedbackState.onConfirmLogout?.invoke()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Icon(Icons.Default.Logout, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ya, Keluar", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AuthFeedbackType.SUCCESS_LOGIN -> {
                        // Animasi Masuk Berhasil dengan Perisai Melayang & Cincin Berputar
                        Box(
                            modifier = Modifier.size(108.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Gelombang Sinar Luar
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .scale(waveScale)
                                    .alpha(waveAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            )
                            // Cincin Rotasi Bersinar Gradien
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .rotate(rotationAngle)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                                Color(0xFF38BDF8).copy(alpha = 0.25f),
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            )
                                        )
                                    )
                            )
                            // Lingkaran Utama Perisai dengan Efek Melayang Vertikal
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .offset(y = floatY.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                Color(0xFF0369A1)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Badge Bergerak
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sesi Aktif & Terverifikasi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Selamat Datang Kembali!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (feedbackState.userName.isNotEmpty() || feedbackState.userEmail.isNotEmpty()) {
                            Text(
                                text = feedbackState.userName.ifEmpty { feedbackState.userEmail },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Berhasil masuk ke akun Anda. Seluruh riwayat dan data disinkronkan secara aman.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Lanjutkan ke Aplikasi", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthFeedbackType.SUCCESS_REGISTER -> {
                        // Animasi Pendaftaran Berhasil dengan Efek Goyang Selebrasi & Cincin Ganda
                        Box(
                            modifier = Modifier.size(108.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Gelombang Sinar Luar
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .scale(waveScale)
                                    .alpha(waveAlpha)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F766E).copy(alpha = 0.35f))
                            )
                            // Cincin Rotasi Gradien Lawan Arah
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .rotate(-rotationAngle)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFF0F766E).copy(alpha = 0.5f),
                                                Color(0xFF2DD4BF).copy(alpha = 0.25f),
                                                Color.Transparent,
                                                Color(0xFF0F766E).copy(alpha = 0.5f)
                                            )
                                        )
                                    )
                            )
                            // Lingkaran Ikon dengan Efek Goyang Selebrasi (Wiggle)
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .rotate(wiggleAngle)
                                    .offset(y = floatY.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF0F766E),
                                                Color(0xFF14B8A6)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Celebration,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F766E).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Stars, null, tint = Color(0xFF0F766E), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Akun Baru Berhasil Dibuat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F766E)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Pendaftaran Berhasil!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (feedbackState.userName.isNotEmpty() || feedbackState.userEmail.isNotEmpty()) {
                            Text(
                                text = feedbackState.userName.ifEmpty { feedbackState.userEmail },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F766E),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Akun pribadi Anda siap digunakan. Ruang data Anda telah terisolasi aman.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Mulai Sekarang", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthFeedbackType.SUCCESS_SEND_OTP_EMAIL -> {
                        // Animasi Kirim OTP via Email: Surat Melayang, Gelombang Sinar Dispatched & Cincin Emas-Biru
                        Box(
                            modifier = Modifier.size(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Gelombang Sinar Dispatch (Sonic Wave Pulse)
                            Box(
                                modifier = Modifier
                                    .size(106.dp)
                                    .scale(waveScale)
                                    .alpha(waveAlpha)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.45f))
                            )
                            // Cincin Rotasi Bersinar Amber / Sky Blue
                            Box(
                                modifier = Modifier
                                    .size(94.dp)
                                    .rotate(rotationAngle)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.sweepGradient(
                                            listOf(
                                                Color(0xFFF59E0B).copy(alpha = 0.6f),
                                                Color(0xFF38BDF8).copy(alpha = 0.4f),
                                                Color.Transparent,
                                                Color(0xFFF59E0B).copy(alpha = 0.6f)
                                            )
                                        )
                                    )
                            )
                            // Ikon Surat / Email dengan Efek Melayang Lembut (Float Motion)
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .offset(y = floatY.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFF59E0B),
                                                Color(0xFFD97706)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Status Badge Live
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kode OTP Berhasil Terkirim",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Kode OTP Berhasil Dikirim!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (feedbackState.userEmail.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = feedbackState.userEmail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Kotak Keterangan / Petunjuk Email
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Silakan periksa Kotak Masuk (Inbox) atau folder Spam pada aplikasi email Anda.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        // Jika OTP Code tersedia di sesi lokal, tampilkan kemudahan salin/lihat
                        if (feedbackState.otpCode.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Kode Keamanan Anda (Berlaku 10 Menit):",
                                        fontSize = 11.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = feedbackState.otpCode,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 4.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                feedbackState.onActionClick?.invoke()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Key, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lanjut Masukkan Kode OTP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    AuthFeedbackType.SUCCESS_LOGOUT -> {
                        // Animasi Keluar Berhasil
                        Box(
                            modifier = Modifier.size(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Sampai Jumpa!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Anda telah berhasil keluar dari akun dengan aman.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text("Selesai", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

