package com.example

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.ToastType
import com.example.ui.screens.BarcodeScannerDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.web.LocalHttpServer
import java.text.SimpleDateFormat
import java.util.*

class WebAuthPromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure screen turns on and shows over other active apps (e.g. TikTok, YouTube, Games)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val clientIp = intent.getStringExtra("WEB_AUTH_CLIENT_IP") ?: "127.0.0.1"
        val timestamp = intent.getLongExtra("WEB_AUTH_TIMESTAMP", System.currentTimeMillis())

        var localToastMessage by mutableStateOf<String?>(null)

        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    WebAuthPromptScreen(
                        clientIp = clientIp,
                        timestamp = timestamp,
                        onDismiss = { finish() },
                        onApprove = { tokenOrPin ->
                            val server = LocalHttpServer.activeInstance ?: MainViewModel.instance?.webServer
                            val vm = MainViewModel.instance
                            val ok = if (tokenOrPin != null) {
                                server?.approveSession(tokenOrPin) ?: false
                            } else {
                                server?.approveAllPending() ?: false
                            }
                            if (ok) {
                                vm?.webAccessSecurityState = MainViewModel.WebAccessSecurityState.NONE
                                NotificationScheduler.cancelWebAccessNotification(applicationContext)
                                vm?.showToastPopup("Akses Web PC Berhasil Diizinkan!")
                                finish()
                            } else {
                                localToastMessage = "Gagal memverifikasi akses"
                                vm?.showToastPopup("Gagal memverifikasi akses")
                            }
                        },
                        onReject = {
                            val server = LocalHttpServer.activeInstance ?: MainViewModel.instance?.webServer
                            val vm = MainViewModel.instance
                            server?.rejectPendingAccess()
                            vm?.onRejectWebAccess()
                            NotificationScheduler.cancelWebAccessNotification(applicationContext)
                            vm?.showToastPopup("Akses Web PC Ditolak")
                            finish()
                        },
                        onShowToast = { msg ->
                            localToastMessage = msg
                            MainViewModel.instance?.showToastPopup(msg)
                        }
                    )

                    if (localToastMessage != null) {
                        com.example.ui.components.SpringToastPopup(
                            message = localToastMessage,
                            onDismiss = { localToastMessage = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebAuthPromptScreen(
    clientIp: String,
    timestamp: Long,
    onDismiss: () -> Unit,
    onApprove: (String?) -> Unit,
    onReject: () -> Unit,
    onShowToast: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var isVerificationStep by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }

    val server = LocalHttpServer.activeInstance ?: MainViewModel.instance?.webServer
    val pin = server?.latestPinCode ?: (100000..999999).random().toString()
    val formattedTime = remember(timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    if (showScannerDialog) {
        BarcodeScannerDialog(
            onDismissRequest = { showScannerDialog = false },
            onBarcodeScanned = { scannedCode ->
                showScannerDialog = false
                val raw = scannedCode.trim()
                val tokenOrPin = if (raw.startsWith("SINGGHARA_AUTH:")) {
                    val parts = raw.split(":")
                    if (parts.size >= 2) parts[1] else raw
                } else {
                    raw
                }
                onApprove(tokenOrPin)
            }
        )
    }

    // Outer full-screen dismissable overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume click */ }
                .shadow(32.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with icon and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF00A884).copy(alpha = 0.2f),
                                        Color(0xFF00C9A7).copy(alpha = 0.35f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Keamanan Web PC",
                            tint = Color(0xFF00A884),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedContent(
                    targetState = isVerificationStep,
                    transitionSpec = {
                        fadeIn() + slideInHorizontally { width -> width / 3 } togetherWith
                        fadeOut() + slideOutHorizontally { width -> -width / 3 }
                    },
                    label = "WebAuthStepAnimation"
                ) { stepVerified ->
                    if (!stepVerified) {
                        // --- STEP 1: INTERACTIVE PROMPT (APAKAH ANDA AKSES WEB PC?) ---
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Apakah anda akses web pc?",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Terdeteksi permintaan sambungan browser PC ke My Singghara melalui jaringan lokal.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // IP & Time Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "IP: $clientIp",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formattedTime,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // TWO BUTTONS: TIDAK vs YA
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // TOMBOL TIDAK (Tolak Akses PC)
                                Button(
                                    onClick = onReject,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Tidak", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                // TOMBOL YA (Buka Verifikasi PIN / QR)
                                Button(
                                    onClick = { isVerificationStep = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00A884),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ya", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    } else {
                        // --- STEP 2: VERIFICATION VIEW (PIN & QR SCANNER) ---
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Verifikasi Akses PC",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // PIN Code Box
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        1.dp,
                                        Color(0xFF00A884).copy(alpha = 0.4f),
                                        RoundedCornerShape(18.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF00A884).copy(alpha = 0.08f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "KODE PIN VERIFIKASI PC",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00A884),
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = pin,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = 6.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("PIN PC", pin)
                                            clipboard.setPrimaryClip(clip)
                                            onShowToast("Kode PIN disalin: $pin")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salin PIN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Scanner button
                            Button(
                                onClick = { showScannerDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00A884),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Buka Pemindai QR Code PC",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Direct approve button
                            OutlinedButton(
                                onClick = { onApprove(null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00A884),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Setujui Akses Langsung",
                                    color = Color(0xFF00A884),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = onReject,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tolak Sambungan Ini",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
