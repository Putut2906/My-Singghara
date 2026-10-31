package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

/**
 * Tampilan Layar Penuh Blokir Akses Aplikasi.
 * Ditampilkan saat akun pengguna dinonaktifkan oleh Administrator atau masa aktif membership telah habis.
 * Mencegah pengguna masuk atau mengakses fitur apa pun di aplikasi.
 */
@Composable
fun AccountBlockedOverlay(
    viewModel: MainViewModel,
    onContactAdmin: () -> Unit,
    onCheckStatus: () -> Unit,
    onLogout: () -> Unit
) {
    // Kunci tombol kembali fisik agar tidak bisa melewati blokir
    BackHandler(enabled = true) {
        // Abaikan tombol kembali atau tetap di layar ini
    }

    val isDark = isSystemInDarkTheme()
    val isExpired = viewModel.accountBlockReason.contains("membership", ignoreCase = true) ||
            viewModel.accountBlockReason.contains("habis", ignoreCase = true) ||
            (viewModel.accountMembershipEndDate > 0L && System.currentTimeMillis() >= viewModel.accountMembershipEndDate)

    val infiniteTransition = rememberInfiniteTransition(label = "blocked_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val currentUser = viewModel.currentFirebaseUser
    val userEmail = currentUser?.email ?: ""
    val userName = viewModel.profileName.ifEmpty { currentUser?.displayName ?: "Pengguna" }
    val userUid = currentUser?.uid ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDark) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E1111), Color(0xFF120808), Color(0xFF0D0D0D))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFF1F2), Color(0xFFFFF5F5), Color(0xFFFAFAFA))
                    )
                }
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1C1917) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            border = BorderStroke(
                1.5.dp,
                if (isExpired) Color(0xFFF59E0B).copy(alpha = 0.6f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Warning Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isExpired) {
                                Color(0xFFFEF3C7)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            }
                        )
                        .border(
                            2.dp,
                            if (isExpired) Color(0xFFF59E0B) else MaterialTheme.colorScheme.error,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Default.HourglassBottom else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = if (isExpired) Color(0xFFD97706) else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tag Status
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (isExpired) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isExpired) Color(0xFFF59E0B) else MaterialTheme.colorScheme.error
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isExpired) Color(0xFFB45309) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isExpired) "MEMBERSHIP BERAKHIR" else "AKUN DINONAKTIFKAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpired) Color(0xFFB45309) else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isExpired) "Masa Membership Telah Habis" else "Akses Akun Dinonaktifkan",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isExpired) {
                        "Masa aktif membership akun Anda telah habis sehingga sistem menonaktifkan akun secara otomatis. Akses ke aplikasi diblokir sementara sampai membership diperpanjang."
                    } else {
                        "Akun Anda telah dinonaktifkan oleh Administrator. Akses aplikasi My Singghara diblokir agar data dan privasi tetap aman. Silakan hubungi admin untuk aktivasi kembali."
                    },
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // User Info Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF262626) else Color(0xFFF8FAFC)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppUserAvatar(
                                name = userName,
                                imageUri = currentUser?.photoUrl?.toString() ?: "",
                                avatarIndex = -2,
                                size = 42.dp,
                                textSize = 14.sp,
                                isLoggedIn = true,
                                contentDescription = userName
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (userEmail.isNotBlank()) {
                                    Text(
                                        text = userEmail,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // Detail Membership / Blokir
                        if (viewModel.accountMembershipTier.isNotBlank() || viewModel.accountMembershipEndDate > 0L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Paket Membership", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = viewModel.accountMembershipTier.ifEmpty { "1/3/5/7/12 Bulan" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                                )
                            }
                            if (viewModel.accountMembershipEndDate > 0L) {
                                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID"))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Berakhir Pada", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(
                                        text = sdf.format(java.util.Date(viewModel.accountMembershipEndDate)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Status Akses", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "Diblokir (Nonaktif)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (userUid.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("UID Akun", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = userUid.take(12) + "...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action 1: Hubungi Admin via WhatsApp
                Button(
                    onClick = onContactAdmin,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hubungi Admin via WhatsApp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action 2: Periksa Ulang Status
                OutlinedButton(
                    onClick = onCheckStatus,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !viewModel.isCheckingAccountStatus,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    if (viewModel.isCheckingAccountStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Memeriksa Server...", fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Periksa Ulang Status Akun",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action 3: Ganti Akun / Keluar (Logout)
                TextButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ganti Akun / Keluar (Logout)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Pilihan durasi membership: 1, 3, 5, 7, atau 12 bulan.\nSetelah diaktifkan oleh admin, tekan 'Periksa Ulang Status' untuk masuk langsung.",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
