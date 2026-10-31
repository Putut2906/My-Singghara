package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebConnectScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isServerActive by remember { mutableStateOf(viewModel.webServer?.isServerRunning() == true) }
    var serverUrl by remember { mutableStateOf(viewModel.webServerUrl) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showDomainHelpDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshConnectedWebDevices()
    }

    // Intercept hardware/gesture Back Button on Android to return to Profile
    BackHandler {
        onBackClick()
    }

    // Help Dialog: Panduan Cara Menyambungkan ke Laptop / PC
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = null,
                        tint = Color(0xFF00A884),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Cara Menyambungkan ke Laptop / PC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InstructionStep(
                        number = "1",
                        icon = Icons.Default.Wifi,
                        title = "Sambungkan Jaringan yang Sama",
                        description = "Pastikan HP dan PC terhubung ke Wi-Fi yang sama, atau hubungkan PC ke Hotspot Seluler HP Anda.\nCatatan: Jangan ketik 'localhost' di PC (localhost hanya untuk di HP). Di PC harus ketik IP HP (contoh: http://192.168.x.x:8080). Jika Wi-Fi memblokir koneksi (AP Isolation), gunakan Hotspot HP."
                    )

                    InstructionStep(
                        number = "2",
                        icon = Icons.Default.Devices,
                        title = "Aktifkan Server Web & Uji di HP",
                        description = "Nyalakan sakelar hingga alamat Web muncul. Anda dapat menekan tombol 'Uji di HP' untuk mengetes langsung di browser smartphone Anda."
                    )

                    InstructionStep(
                        number = "3",
                        icon = Icons.Default.QrCodeScanner,
                        title = "Buka Browser di PC & Verifikasi QR",
                        description = "Buka link di PC, lalu scan kode QR barcode yang tampil di layar PC menggunakan tombol 'Scan QR Barcode PC' di HP untuk verifikasi otomatis."
                    )

                    InstructionStep(
                        number = "4",
                        icon = Icons.Default.Devices,
                        title = "Koneksi Kabel USB & Scanner Kasir HP",
                        description = "Gunakan perintah 'adb forward tcp:8080 tcp:8080' untuk akses kabel USB tanpa Wi-Fi. Untuk menggunakan HP sebagai Scanner Barcode Kasir PC, buka menu titik 3 di halaman Kasir lalu pilih 'Scanner HP'."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                ) {
                    Text("Mengerti")
                }
            }
        )
    }

    // Domain Help Dialog: Panduan Setup mysingghara.com di PC 100% Offline
    if (showDomainHelpDialog) {
        val detectedIp = remember(serverUrl) {
            if (serverUrl.contains("//")) serverUrl.substringAfter("//").substringBefore(":")
            else com.example.web.LocalHttpServer.getIpAddress(context)
        }
        val hostsLine = "$detectedIp  mysingghara.com"
        val powershellCmd = "Add-Content -Path C:\\Windows\\System32\\drivers\\etc\\hosts -Value \"$detectedIp  mysingghara.com\""

        AlertDialog(
            onDismissRequest = { showDomainHelpDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00A884).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color(0xFF00A884),
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Akses Domain mysingghara.com",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Agar browser PC dapat membuka http://mysingghara.com:8080 atau http://mysingghara.com secara 100% offline tanpa kuota/internet, PC hanya perlu memetakan domain ke IP HP ini ($detectedIp):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Box Baris Hosts
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Baris Konfigurasi Hosts PC:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = hostsLine,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Baris Hosts", hostsLine)
                                    clipboard.setPrimaryClip(clip)
                                    viewModel.showToastPopup("Baris hosts disalin!")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin Baris Hosts", fontSize = 11.sp)
                            }
                        }
                    }

                    // Pilihan Utama: Installer Windows (.exe) Progressive Web App (PWA)
                    InstructionStep(
                        number = "★",
                        icon = Icons.Default.Laptop,
                        title = "Pilihan Utama: Installer PWA Windows (.exe)",
                        description = "Buka http://$detectedIp:8080/installer.exe di browser PC untuk mengunduh installer. Cukup double-click file MySingghara_PWA_Setup.exe untuk otomatis memasang My Singghara sebagai Progressive Web App (PWA) di Desktop Windows dengan jendela mandiri dan domain offline!"
                    )

                    Button(
                        onClick = {
                            val installerUrl = "http://$detectedIp:8080/installer.exe"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Tautan Installer PWA .exe", installerUrl)
                            clipboard.setPrimaryClip(clip)
                            viewModel.showToastPopup("Tautan installer PWA (.exe) disalin: $installerUrl")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Tautan Installer PWA (.exe)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Pilihan 1: Script Otomatis (.bat)
                    InstructionStep(
                        number = "A",
                        icon = Icons.Default.FileDownload,
                        title = "Cara 2: Script Otomatis (.bat)",
                        description = "Buka $serverUrl di PC, lalu klik tombol 'Setup Domain .bat'. Setelah terunduh, klik kanan dan pilih 'Run as administrator'."
                    )

                    // Pilihan 2: Perintah PowerShell
                    InstructionStep(
                        number = "B",
                        icon = Icons.Default.Laptop,
                        title = "Cara 2: Lewat PowerShell (Windows)",
                        description = "Buka PowerShell di PC sebagai Administrator, lalu jalankan perintah di bawah ini:"
                    )

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("PowerShell Command", powershellCmd)
                            clipboard.setPrimaryClip(clip)
                            viewModel.showToastPopup("Perintah PowerShell disalin!")
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Perintah PowerShell", fontSize = 11.sp)
                    }

                    // Pilihan 3: Manual Notepad
                    InstructionStep(
                        number = "C",
                        icon = Icons.Default.HelpOutline,
                        title = "Cara 3: Manual via Notepad",
                        description = "1. Buka Notepad di PC dengan 'Run as administrator'.\n2. Buka berkas C:\\Windows\\System32\\drivers\\etc\\hosts\n3. Tambahkan baris: $hostsLine di paling bawah lalu Simpan (Ctrl+S)."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDomainHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    // Scanner Dialog (Auth QR)
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
                viewModel.approveWebSession(tokenOrPin)
                viewModel.showToastPopup("Verifikasi Sukses! Browser PC Terbuka.")
            }
        )
    }

    // Manual PIN Dialog
    if (showPinDialog) {
        var inputPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = Color(0xFF00A884),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Masukkan Kode PIN PC", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Ketikkan 6 digit kode PIN yang tertera pada layar My Singghara di PC:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { if (it.length <= 6) inputPin = it },
                        label = { Text("Kode PIN PC") },
                        placeholder = { Text("Contoh: 123456") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPin.isNotEmpty()) {
                            viewModel.approveWebSession(inputPin)
                            showPinDialog = false
                            viewModel.showToastPopup("Verifikasi PIN Sukses! Akses PC Diizinkan.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884))
                ) {
                    Text("Verifikasi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServerActive) Color(0xFF00A884).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isServerActive) Color(0xFF00A884) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Ikon Tanya (?) di pojok kanan atas dalam kartu
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Cara Menyambungkan ke Laptop / PC",
                            tint = Color(0xFF00A884),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isServerActive) Color(0xFF00A884) else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isServerActive) "Server Web PC Aktif" else "Server Web PC Nonaktif",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isServerActive) "Buka URL di bawah ini melalui Browser PC Anda" else "Aktifkan sakelar untuk mulai menghubungkan PC",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Status Server Web",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isServerActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.startWebServer(
                                        onStarted = { url ->
                                            isServerActive = true
                                            serverUrl = url
                                            viewModel.showToastPopup("Server Web Berhasil Diaktifkan!")
                                        },
                                        onError = { err ->
                                            isServerActive = false
                                            viewModel.showToastPopup(err)
                                        }
                                    )
                                } else {
                                    viewModel.stopWebServer()
                                    isServerActive = false
                                    serverUrl = ""
                                    viewModel.showToastPopup("Server Web Dihentikan")
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00A884)
                            )
                        )
                    }

                    if (isServerActive && serverUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // --- 1. DOMAIN OFFLINE RESMI (mysingghara.com) ---
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF00A884).copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = Color(0xFF00A884),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "DOMAIN OFFLINE RESMI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00A884)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "http://mysingghara.com:8080",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00A884),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Buka langsung di browser PC tanpa perlu mengingat nomor IP",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Domain Web Connect", "http://mysingghara.com:8080")
                                            clipboard.setPrimaryClip(clip)
                                            viewModel.showToastPopup("Domain http://mysingghara.com:8080 disalin!")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salin Domain", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { showDomainHelpDialog = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00A884)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Panduan PC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val detectedIp = if (serverUrl.contains("//")) serverUrl.substringAfter("//").substringBefore(":")
                                            else com.example.web.LocalHttpServer.getIpAddress(context)
                                        val installerUrl = "http://$detectedIp:8080/installer.exe"
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Link Installer PC", installerUrl)
                                        clipboard.setPrimaryClip(clip)
                                        viewModel.showToastPopup("Tautan Installer PC (.exe) disalin: $installerUrl")
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00A884)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Laptop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Installer PWA (.exe)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                // --- 2. ALAMAT IP LANGSUNG (Alternatif Tanpa Setup) ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ALAMAT IP LANGSUNG (Alternatif Tanpa Setup):",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = serverUrl,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("IP URL Web Connect", serverUrl)
                                                clipboard.setPrimaryClip(clip)
                                                viewModel.showToastPopup("Alamat IP disalin!")
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin IP", tint = Color(0xFF00A884), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val targetUri = if (serverUrl.isNotEmpty()) Uri.parse(serverUrl) else Uri.parse("http://localhost:8080")
                                                    val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    viewModel.showToastPopup("Gagal membuka browser: ${e.message}")
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = "Uji di HP", tint = Color(0xFF00A884), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                // --- 3. PETUNJUK KONEKSI HOTSPOT & ANTARMUKA JARINGAN OFFLINE ---
                                val availableIps = remember(serverUrl, isServerActive) {
                                    if (isServerActive) com.example.web.LocalHttpServer.getAllAvailableIps(context) else emptyList()
                                }

                                if (availableIps.size > 1) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Jalur Jaringan Terdeteksi (100% Offline):",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00A884)
                                            )
                                            availableIps.forEach { netInfo ->
                                                val ifaceUrl = "http://${netInfo.ip}:8080"
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = netInfo.type, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                        Text(text = ifaceUrl, fontSize = 12.sp, color = Color(0xFF00A884), fontWeight = FontWeight.Medium)
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            clipboard.setPrimaryClip(ClipData.newPlainText("URL ${netInfo.type}", ifaceUrl))
                                                            viewModel.showToastPopup("URL ${netInfo.type} disalin: $ifaceUrl")
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin", tint = Color(0xFF00A884), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WifiTethering,
                                            contentDescription = null,
                                            tint = Color(0xFF00A884),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                 text = "Jika tersambung via Hotspot Seluler HP (100% Offline):",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "http://192.168.43.1:8080",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00A884)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("URL Hotspot", "http://192.168.43.1:8080")
                                                clipboard.setPrimaryClip(clip)
                                                viewModel.showToastPopup("URL Hotspot disalin!")
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Salin URL Hotspot",
                                                tint = Color(0xFF00A884),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // QR & PIN VERIFICATION CARD (Displayed when server is active)
            if (isServerActive) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00A884).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF00A884),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Proteksi & Verifikasi Web Connect",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Pindai QR Barcode di layar PC untuk memberi izin akses",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Display Current Session PIN Code
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00A884).copy(alpha = 0.08f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A884).copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "KODE PIN AKSES PC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00A884)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = viewModel.getWebPinCode(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Gunakan kode ini jika memasukkan PIN di browser PC",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Primary Action: Scan QR Code
                        Button(
                            onClick = { showScannerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan QR Barcode PC", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick 1-Tap Approve Button
                            OutlinedButton(
                                onClick = {
                                    val success = viewModel.approveAllPendingWebSessions()
                                    if (success) {
                                        viewModel.showToastPopup("Akses PC Berhasil Disetujui!")
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF00A884))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Izinkan PC", fontSize = 12.sp, color = Color(0xFF00A884))
                            }

                            // Manual PIN Entry Button
                            OutlinedButton(
                                onClick = { showPinDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Input PIN", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Reset / Revoke Sessions Button
                        TextButton(
                            onClick = {
                                viewModel.revokeWebSessions()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kunci & Putuskan Semua Sesi PC", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // DAFTAR PERANGKAT TERHUBUNG / MEMINTA AKSES
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daftar Perangkat Web PC",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${viewModel.connectedWebDevices.size} perangkat terdeteksi",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.refreshConnectedWebDevices() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Muat Ulang Perangkat",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (viewModel.connectedWebDevices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Laptop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Belum ada perangkat PC yang tersambung",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Buka alamat web di browser laptop/PC untuk menghubungkan",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            viewModel.connectedWebDevices.forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (device.isRejected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        else if (device.isVerified) Color(0xFF00A884).copy(alpha = 0.08f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (device.isRejected) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                        else if (device.isVerified) Color(0xFF00A884).copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (device.isRejected) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                        else if (device.isVerified) Color(0xFF00A884).copy(alpha = 0.15f)
                                                        else Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (device.isRejected) Icons.Default.Lock
                                                    else if (device.isVerified) Icons.Default.CheckCircle
                                                    else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (device.isRejected) MaterialTheme.colorScheme.error
                                                    else if (device.isVerified) Color(0xFF00A884)
                                                    else Color(0xFFF59E0B),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.deviceName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "IP: ${device.ip} • PIN: ${device.pinCode}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (device.isRejected) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                                else if (device.isVerified) Color(0xFF00A884).copy(alpha = 0.12f)
                                                else Color(0xFFF59E0B).copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = if (device.isRejected) "Terkunci"
                                                    else if (device.isVerified) "Terverifikasi"
                                                    else "Menunggu Izin",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (device.isRejected) MaterialTheme.colorScheme.error
                                                    else if (device.isVerified) Color(0xFF00A884)
                                                    else Color(0xFFD97706),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (!device.isVerified || device.isRejected) {
                                                Button(
                                                    onClick = { viewModel.approveDeviceSession(device.id) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A884)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f).height(34.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Izinkan Akses", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            OutlinedButton(
                                                onClick = { viewModel.lockAndDisconnectDevice(device.id) },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Kunci & Putuskan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun InstructionStep(
    number: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF00A884).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF00A884)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
