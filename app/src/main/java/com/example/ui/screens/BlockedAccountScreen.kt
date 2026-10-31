package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TimerOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.AppUserAvatar
import java.net.URLEncoder

/**
 * Layar Pemblokiran Akses Aplikasi (Saat Akun Dinonaktifkan atau Masa Membership Habis)
 * Pengguna dicegah masuk atau mengakses fitur aplikasi sampai admin mengaktifkan / memperpanjang membership.
 */
@Composable
fun BlockedAccountScreen(
    viewModel: MainViewModel,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val user = viewModel.currentFirebaseUser
    val isExpired = viewModel.accountBlockedIsExpired
    val plan = viewModel.accountBlockedPlan
    val reason = viewModel.accountBlockedReason

    var isChecking by remember { mutableStateOf(false) }

    // Cegah tombol back keluar dari layar blokir untuk mengakses aplikasi
    BackHandler {
        onExitApp()
    }

    val primaryAlertColor = if (isExpired) Color(0xFFE11D48) else Color(0xFFDC2626)
    val alertBgColor = if (isDark) Color(0xFF1E1014) else Color(0xFFFFF1F2)
    val cardBgColor = if (isDark) Color(0xFF27131A) else Color(0xFFFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(alertBgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Icon Peringatan Utama
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                primaryAlertColor.copy(alpha = 0.2f),
                                primaryAlertColor.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(2.dp, primaryAlertColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpired) Icons.Default.TimerOff else Icons.Default.Block,
                    contentDescription = null,
                    tint = primaryAlertColor,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badge Proteksi
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = primaryAlertColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, primaryAlertColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = primaryAlertColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isExpired) "MEMBERSHIP BERAKHIR" else "AKUN DINONAKTIFKAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAlertColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Judul
            Text(
                text = if (isExpired) "Masa Aktif Membership Telah Habis" else "Akses Akun Dinonaktifkan",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color.White else Color(0xFF881337),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sub judul
            Text(
                text = "Akses Anda ke seluruh fitur aplikasi My Singghara saat ini diblokir untuk mematuhi kebijakan akun.",
                fontSize = 12.5.sp,
                color = if (isDark) Color(0xFFFDA4AF) else Color(0xFF9F1239),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Kartu Rincian Akun & Paket
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.dp, primaryAlertColor.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profil Pengguna Terblokir
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppUserAvatar(
                            name = viewModel.profileName.ifEmpty { user?.displayName ?: "Pengguna" },
                            imageUri = viewModel.profileImageUri,
                            avatarIndex = viewModel.profileAvatarIndex,
                            size = 46.dp,
                            textSize = 16.sp,
                            isLoggedIn = true
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.profileName.ifEmpty { user?.displayName ?: "Pengguna Singghara" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!user?.email.isNullOrBlank()) {
                                Text(
                                    text = user!!.email!!,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (user?.uid != null) {
                                Text(
                                    text = "UID: ${user.uid.take(12)}...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Detail Paket & Alasan Pemblokiran
                    if (plan.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Paket Membership:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = primaryAlertColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = plan,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAlertColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryAlertColor.copy(alpha = 0.08f))
                            .border(1.dp, primaryAlertColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = reason.ifEmpty {
                                if (isExpired) {
                                    "Masa aktif membership Anda telah berakhir. Akun dinonaktifkan secara otomatis. Silakan lakukan perpanjangan (1, 3, 5, 7, atau 12 bulan) ke admin."
                                } else {
                                    "Akun Anda telah dinonaktifkan oleh Administrator. Akses aplikasi My Singghara diblokir."
                                }
                            },
                            fontSize = 11.5.sp,
                            color = if (isDark) Color(0xFFFECDD3) else Color(0xFF881337),
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // TOMBOL AKSI

            // 1. Hubungi Admin via WhatsApp
            Button(
                onClick = {
                    try {
                        val displayName = viewModel.profileName.ifEmpty { user?.displayName ?: "Pengguna" }
                        val email = user?.email ?: ""
                        val uid = user?.uid ?: ""
                        val textMsg = "Halo Admin My Singghara,%0ASaya ingin mengajukan ${if (isExpired) "perpanjangan paket membership (1, 3, 5, 7, atau 12 bulan)" else "aktivasi kembali akun saya"}:%0A- Nama: $displayName%0A- Email: $email%0A- UID: $uid"
                        val cleanPhone = viewModel.adminPhoneNumber.trim().replace("+", "").replace("-", "").replace(" ", "")
                        val intlPhone = if (cleanPhone.startsWith("0")) "62" + cleanPhone.substring(1) else cleanPhone
                        val waUrl = "https://wa.me/$intlPhone?text=$textMsg"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        viewModel.showToastPopup("Gagal membuka WhatsApp. Silakan hubungi admin secara manual.")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpired) "Perpanjang Membership via WhatsApp" else "Hubungi Admin via WhatsApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Periksa Status Ulang
            Button(
                onClick = {
                    if (isChecking) return@Button
                    isChecking = true
                    viewModel.verifyCurrentAccountAccess { isUnlocked ->
                        isChecking = false
                        if (isUnlocked) {
                            viewModel.showToastPopup("Akun Anda telah aktif kembali! Selamat datang kembali.")
                        } else {
                            viewModel.showToastPopup("Akun masih belum diaktifkan. Silakan hubungi admin.")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAlertColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memeriksa...", color = Color.White, fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Periksa Status Ulang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Keluar Akun (Ganti Akun Lain)
            OutlinedButton(
                onClick = {
                    viewModel.directLogout()
                    viewModel.showToastPopup("Berhasil keluar dari akun yang terblokir.")
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Keluar & Ganti Akun", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Tutup Aplikasi
            TextButton(
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tutup Aplikasi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
