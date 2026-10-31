package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ht.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyTalkyScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // Dynamic Theme Adaptive Colors
    val screenBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val bottomBarBg = if (isDark) Color(0xFF090D11) else Color(0xFFFFFFFF)
    val bottomBarBorder = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val sheetContainerColor = if (isDark) Color(0xFF1E242B) else Color(0xFFFFFFFF)

    // Inisialisasi HandyTalkyManager jika belum
    LaunchedEffect(Unit) {
        HandyTalkyManager.init(context)
    }

    // Permission Check
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Izin mikrofon diperlukan untuk menggunakan Handy Talky", Toast.LENGTH_SHORT).show()
        }
    }

    // States from HandyTalkyManager
    val isTransmitting by HandyTalkyManager.isTransmitting.collectAsState()
    val isReceiving by HandyTalkyManager.isReceiving.collectAsState()
    val rxCallsign by HandyTalkyManager.rxCallsign.collectAsState()
    val currentFreq by HandyTalkyManager.currentFrequency.collectAsState()
    val currentChannel by HandyTalkyManager.currentChannel.collectAsState()
    val selectedBand by HandyTalkyManager.selectedBand.collectAsState()
    val callsign by HandyTalkyManager.callsign.collectAsState()
    val volume by HandyTalkyManager.volume.collectAsState()
    val micGain by HandyTalkyManager.micGain.collectAsState()
    val speakerGain by HandyTalkyManager.speakerGain.collectAsState()
    val squelchLevel by HandyTalkyManager.squelchLevel.collectAsState()
    val rogerBeepEnabled by HandyTalkyManager.rogerBeepEnabled.collectAsState()
    val squelchTailEnabled by HandyTalkyManager.squelchTailEnabled.collectAsState()
    val handsfreeMode by HandyTalkyManager.handsfreeMode.collectAsState()
    val roipGatewayMode by HandyTalkyManager.roipGatewayMode.collectAsState()
    val txAudioLevel by HandyTalkyManager.txAudioLevel.collectAsState()
    val rxAudioLevel by HandyTalkyManager.rxAudioLevel.collectAsState()
    val discoveredDevices by HandyTalkyManager.discoveredDevices.collectAsState()
    val logs by HandyTalkyManager.logs.collectAsState()
    val localIp by HandyTalkyManager.localIpAddress.collectAsState()
    val isHotspot by HandyTalkyManager.isHotspotDetected.collectAsState()

    // Dialog & Sheet States
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCallsignDialog by remember { mutableStateOf(false) }
    var showRadarSheet by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showManualFreqDialog by remember { mutableStateOf(false) }
    var showPhysicalHtDialog by remember { mutableStateOf(false) }

    var channelFilterBand by remember { mutableStateOf<HtBand?>(null) }

    // Screen Container (Tanpa TopAppBar & Tanpa Header Terintegrasi terpisah)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Permission Warning Banner if not granted
            if (!hasAudioPermission) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Izin Mikrofon Diperlukan",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Untuk berbicara di HT, izinkan akses audio perangkat.",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Izinkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Banner info RoIP Gateway Aktif
            if (roipGatewayMode) {
                Surface(
                    color = Color(0xFF047857).copy(alpha = if (isDark) 0.25f else 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radio, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MODE GATEWAY HT FISIK AKTIF (RoIP Bridge)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                            )
                        }
                        TextButton(
                            onClick = { showPhysicalHtDialog = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Info", fontSize = 11.sp, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                        }
                    }
                }
            }

            // ==========================================
            // KONSOL RADIO TRANSCEIVER (OLED / LCD MODE)
            // Lengkap dengan Ikon Pintasan di bawah teks status
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                TacticalTransceiverConsole(
                    isDark = isDark,
                    channel = currentChannel,
                    frequencyMHz = currentFreq,
                    band = selectedBand,
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    rxCallsign = rxCallsign,
                    callsign = callsign,
                    txAudioLevel = txAudioLevel,
                    rxAudioLevel = rxAudioLevel,
                    volume = volume,
                    micGain = micGain,
                    speakerGain = speakerGain,
                    squelchLevel = squelchLevel,
                    rogerBeep = rogerBeepEnabled,
                    localIp = localIp,
                    isHotspot = isHotspot,
                    roipGatewayMode = roipGatewayMode,
                    discoveredDevicesCount = discoveredDevices.size,
                    onNavigateBack = onNavigateBack,
                    onOpenPhysicalHt = { showPhysicalHtDialog = true },
                    onOpenRadar = { showRadarSheet = true },
                    onOpenLogs = { showLogsSheet = true },
                    onOpenSettings = { showSettingsDialog = true },
                    onEditCallsign = { showCallsignDialog = true },
                    onManualFreq = { showManualFreqDialog = true }
                )
            }

            // ==========================================
            // KANAL DAN TUNER FREKUENSI
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Header Pemilihan Kanal & Filter Band
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAFTAR KANAL & FREKUENSI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 1.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BandFilterChip(
                            isDark = isDark,
                            label = "SEMUA",
                            isSelected = channelFilterBand == null,
                            onClick = { channelFilterBand = null }
                        )
                        BandFilterChip(
                            isDark = isDark,
                            label = "VHF",
                            isSelected = channelFilterBand == HtBand.VHF,
                            onClick = { channelFilterBand = HtBand.VHF }
                        )
                        BandFilterChip(
                            isDark = isDark,
                            label = "UHF",
                            isSelected = channelFilterBand == HtBand.UHF,
                            onClick = { channelFilterBand = HtBand.UHF }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable Channel Selector Chips
                val filteredChannels = remember(channelFilterBand) {
                    if (channelFilterBand == null) HtChannelPresets.CHANNELS
                    else HtChannelPresets.CHANNELS.filter { it.band == channelFilterBand }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredChannels) { ch ->
                        val isSelected = currentChannel.id == ch.id
                        ChannelCard(
                            isDark = isDark,
                            channel = ch,
                            isSelected = isSelected,
                            onClick = { HandyTalkyManager.selectChannel(ch) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tombol Step Tuner Frekuensi (-25kHz dan +25kHz) & Kustom Freq (Membuka Keypad Bawaan)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { HandyTalkyManager.stepFrequency(-0.025) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White,
                            contentColor = textSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-25 kHz", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showManualFreqDialog = true },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE0F2FE),
                            contentColor = if (isDark) Color.White else Color(0xFF0369A1)
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFBAE6FD)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kustom Freq", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { HandyTalkyManager.stepFrequency(0.025) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White,
                            contentColor = textSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("+25 kHz", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ==========================================
            // TOMBOL PTT (PUSH-TO-TALK) UTAMA
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BigPttTacticalButton(
                    isDark = isDark,
                    isTransmitting = isTransmitting,
                    isReceiving = isReceiving,
                    rxCallsign = rxCallsign,
                    hasPermission = hasAudioPermission,
                    handsfreeMode = handsfreeMode,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onPressStart = {
                        if (hasAudioPermission && !isReceiving) {
                            HandyTalkyManager.startTransmission(context)
                        }
                    },
                    onPressEnd = {
                        if (isTransmitting) {
                            HandyTalkyManager.stopTransmission(context)
                        }
                    },
                    onToggleHandsfree = {
                        if (hasAudioPermission && !isReceiving) {
                            if (isTransmitting) {
                                HandyTalkyManager.stopTransmission(context)
                            } else {
                                HandyTalkyManager.startTransmission(context)
                            }
                        }
                    }
                )
            }

            // ==========================================
            // BOTTOM STATUS BAR (Ringkas & Terintegrasi)
            // ==========================================
            Surface(
                color = bottomBarBg,
                border = BorderStroke(1.dp, bottomBarBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showPhysicalHtDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hubung HT Fisik?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Handsfree toggle
                        FilterChip(
                            selected = handsfreeMode,
                            onClick = { HandyTalkyManager.toggleHandsfreeMode() },
                            label = {
                                Text(
                                    text = if (handsfreeMode) "VOX/LOCK ON" else "VOX OFF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                labelColor = textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = handsfreeMode,
                                borderColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // KEYPAD NUMERIK BAWAAN APLIKASI (Bukan keyboard bawaan OS Android)
    // =========================================================================
    if (showManualFreqDialog) {
        TacticalNumericKeypadDialog(
            isDark = isDark,
            currentFrequency = currentFreq,
            onFrequencySelected = { newFreq ->
                HandyTalkyManager.setFrequency(newFreq)
                showManualFreqDialog = false
            },
            onDismissRequest = { showManualFreqDialog = false }
        )
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS (THEME AWARE)
    // ==========================================

    // 1. Callsign Edit Dialog
    if (showCallsignDialog) {
        var tempCallsign by remember { mutableStateOf(callsign) }
        AlertDialog(
            onDismissRequest = { showCallsignDialog = false },
            title = { Text("Ubah Callsign HT", color = textPrimary) },
            text = {
                Column {
                    Text(
                        text = "Callsign adalah nama panggilan radio Anda yang akan tampil di HT pengguna lain pada frekuensi ini.",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempCallsign,
                        onValueChange = { tempCallsign = it },
                        label = { Text("Callsign Radio") },
                        placeholder = { Text("Contoh: Echo-01, Komandan, Bravo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempCallsign.isNotBlank()) {
                            HandyTalkyManager.setCallsign(tempCallsign)
                        }
                        showCallsignDialog = false
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallsignDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // 2. Settings & Audio Booster Modal Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pengaturan Audio & Booster HT", color = textPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Audio Booster: Speaker Gain (Penyelesaian Suara Lemah)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🔊 Penguat Speaker (Speaker Boost)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Text(
                                "${String.format(Locale.US, "%.1f", speakerGain)}x",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF10B981) else Color(0xFF047857)
                            )
                        }
                        Text(
                            text = "Memperkuat volume suara lawan bicara agar terdengar kencang & lantang di loudspeaker.",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                        Slider(
                            value = speakerGain,
                            onValueChange = { HandyTalkyManager.setSpeakerGain(it) },
                            valueRange = 1.0f..4.0f,
                            steps = 5
                        )
                    }

                    // Audio Booster: Mic Gain
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🎙️ Kepekaan Mic (Mic Pre-Amp)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                            Text(
                                "${String.format(Locale.US, "%.1f", micGain)}x",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                        }
                        Text(
                            text = "Memperkuat tangkapan suara mic ponsel Anda saat PTT ditekan.",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                        Slider(
                            value = micGain,
                            onValueChange = { HandyTalkyManager.setMicGain(it) },
                            valueRange = 1.0f..5.0f,
                            steps = 7
                        )
                    }

                    // Tes Suara Speaker Button
                    OutlinedButton(
                        onClick = {
                            HandyTalkyManager.configureSpeakerphone(context)
                            HandyTalkyManager.playPttPressSound()
                            HandyTalkyManager.playRogerBeep()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF0F766E) else Color(0xFF0D9488))
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF14B8A6) else Color(0xFF0F766E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Tes Bunyi Loudspeaker (Roger Beep)",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF14B8A6) else Color(0xFF0F766E),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))

                    // Roger Beep Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Roger Beep (Nada Akhir)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            Text("Bunyi nada 'tit' khas HT radio saat selesai bicara", fontSize = 11.sp, color = textSecondary)
                        }
                        Switch(
                            checked = rogerBeepEnabled,
                            onCheckedChange = { HandyTalkyManager.toggleRogerBeep() }
                        )
                    }

                    // Squelch Noise Tail Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Squelch Tail Click", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            Text("Efek desis statis realistis penutup transmisi", fontSize = 11.sp, color = textSecondary)
                        }
                        Switch(
                            checked = squelchTailEnabled,
                            onCheckedChange = { HandyTalkyManager.toggleSquelchTail() }
                        )
                    }

                    // Squelch Level
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Squelch Sensitivity (SQL)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            Text("Level $squelchLevel (0 = Terbuka)", fontSize = 12.sp, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7))
                        }
                        Slider(
                            value = squelchLevel.toFloat(),
                            onValueChange = { HandyTalkyManager.setSquelchLevel(it.toInt()) },
                            valueRange = 0f..9f,
                            steps = 8
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // 3. Physical HT (RoIP Gateway & Compatibility) Dialog
    if (showPhysicalHtDialog) {
        AlertDialog(
            onDismissRequest = { showPhysicalHtDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Koneksi ke HT Fisik", color = textPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Apakah HP bisa langsung ke HT Fisik lewat udara?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Secara perangkat keras (hardware), HP tidak memiliki antena pemancar radio RF VHF (144 MHz) atau UHF (430 MHz). HP hanya memiliki pemancar Wi-Fi (2.4/5GHz) dan Seluler.",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    Text(
                        text = "3 Cara Menghubungkan ke HT Fisik (Baofeng, Motorola, Icom):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = textPrimary
                    )

                    // Cara 1: RoIP Gateway
                    Row(verticalAlignment = Alignment.Top) {
                        Text("1. ", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 12.sp)
                        Column {
                            Text(
                                text = "Metode RoIP Gateway (Radio over IP)",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Gunakan 1 HT fisik (misal Baofeng UV-5R) yang dihubungkan ke HP kedua via kabel audio jack 3.5mm K-Type (mode VOX aktif). HP kedua mengaktifkan 'Mode Gateway RoIP'. Siapapun yang bicara di aplikasi HP akan otomatis dipancarkan oleh HT fisik tersebut ke udara!",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // Cara 2: Kabel Audio Interface Langsung
                    Row(verticalAlignment = Alignment.Top) {
                        Text("2. ", fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), fontSize = 12.sp)
                        Column {
                            Text(
                                text = "Kabel Audio Patch (BTECH APRS-K1 / K-Type)",
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Menghubungkan jack audio 3.5mm HP langsung ke port Mic/Spk HT fisik dengan fitur VOX (Voice Operated Exchange) pada radio fisik.",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // Cara 3: Wireless Bluetooth PTT Mic
                    Row(verticalAlignment = Alignment.Top) {
                        Text("3. ", fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706), fontSize = 12.sp)
                        Column {
                            Text(
                                text = "Handheld Wireless Bluetooth PTT Mic",
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Menggunakan speaker mic fisik Bluetooth (seperti Dellking / B01 PTT Mic) yang memiliki tombol fisik PTT dan speaker keras layaknya mic HT sungguhan.",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    HorizontalDivider(color = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))

                    // Switch Mode RoIP Gateway
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aktifkan Mode RoIP Gateway di HP Ini",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "Jadikan ponsel ini sebagai jembatan relay ke HT fisik via kabel audio.",
                                fontSize = 10.sp,
                                color = textSecondary
                            )
                        }
                        Switch(
                            checked = roipGatewayMode,
                            onCheckedChange = { HandyTalkyManager.toggleRoipGatewayMode(context) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPhysicalHtDialog = false }) {
                    Text("Mengerti")
                }
            }
        )
    }

    // 4. Radar / Nearby Devices Modal
    if (showRadarSheet) {
        ModalBottomSheet(
            onDismissRequest = { showRadarSheet = false },
            containerColor = sheetContainerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Radar HT di Jaringan Lokal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Terdeteksi ${discoveredDevices.size} perangkat walkie-talkie aktif",
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                    }
                    IconButton(onClick = { showRadarSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (discoveredDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.WifiFind,
                                contentDescription = null,
                                tint = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada HT lain terdeteksi",
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "100% Offline: Hubungkan ponsel rekan ke Wi-Fi yang sama atau nyalakan Hotspot HP tanpa perlu kuota internet.",
                                color = textSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(discoveredDevices) { dev ->
                            val isSameFreq = kotlin.math.abs(dev.frequencyMHz - currentFreq) < 0.005
                            Surface(
                                color = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSameFreq) Color(0xFF10B981) else (if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isSameFreq) Color(0xFF10B981) else Color(0xFFF59E0B))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = dev.callsign,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = textPrimary
                                            )
                                            Text(
                                                text = "IP: ${dev.ip} • Kanal: ${dev.channelName}",
                                                fontSize = 11.sp,
                                                color = textSecondary
                                            )
                                        }
                                    }

                                    if (!isSameFreq) {
                                        Button(
                                            onClick = {
                                                HandyTalkyManager.setFrequency(dev.frequencyMHz)
                                                showRadarSheet = false
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Ikut Freq", fontSize = 11.sp)
                                        }
                                    } else {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SATU FREQ",
                                                color = if (isDark) Color(0xFF10B981) else Color(0xFF047857),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // 5. Logs Sheet
    if (showLogsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLogsSheet = false },
            containerColor = sheetContainerColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Aktivitas HT",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    TextButton(onClick = { HandyTalkyManager.clearLogs() }) {
                        Text("Bersihkan", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada log transmisi.", color = textSecondary, fontSize = 13.sp)
                    }
                } else {
                    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(logs) { log ->
                            val (badgeColor, badgeText) = when (log.type) {
                                HtLogType.TX -> Pair(Color(0xFFEF4444), "TX")
                                HtLogType.RX -> Pair(Color(0xFF10B981), "RX")
                                HtLogType.BEACON -> Pair(Color(0xFF38BDF8), "BEACON")
                                HtLogType.SYSTEM -> Pair(Color(0xFFF59E0B), "SYS")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = badgeColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, badgeColor)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sdf.format(Date(log.timestamp)),
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = log.message,
                                    color = textPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * Konsol Display Tactical Walkie-Talkie OLED / LCD Transceiver
 * Ikon pintasan cepat ditempatkan tepat di bawah teks status "STANDBY • MONITORING FREKUENSI"
 */
@Composable
fun TacticalTransceiverConsole(
    isDark: Boolean,
    channel: HtChannel,
    frequencyMHz: Double,
    band: HtBand,
    isTransmitting: Boolean,
    isReceiving: Boolean,
    rxCallsign: String,
    callsign: String,
    txAudioLevel: Float,
    rxAudioLevel: Float,
    volume: Float,
    micGain: Float,
    speakerGain: Float,
    squelchLevel: Int,
    rogerBeep: Boolean,
    localIp: String,
    isHotspot: Boolean,
    roipGatewayMode: Boolean,
    discoveredDevicesCount: Int,
    onNavigateBack: () -> Unit,
    onOpenPhysicalHt: () -> Unit,
    onOpenRadar: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditCallsign: () -> Unit,
    onManualFreq: () -> Unit
) {
    val frameBg = if (isDark) Color(0xFF181E24) else Color(0xFFFFFFFF)
    val frameBorder = if (isDark) Color(0xFF2C353F) else Color(0xFFCBD5E1)
    val screenBg = if (isDark) Color(0xFF070B0E) else Color(0xFFF1F5F9)
    val screenBorder = if (isTransmitting) Color(0xFFEF4444)
        else if (isReceiving) Color(0xFF10B981)
        else if (isDark) Color(0xFF0E7490)
        else Color(0xFF94A3B8)

    val freqColor = if (isTransmitting) Color(0xFFEF4444)
        else if (isReceiving) Color(0xFF10B981)
        else if (isDark) Color(0xFF38BDF8)
        else Color(0xFF0284C7)

    val subTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        color = frameBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, frameBorder),
        shadowElevation = if (isDark) 6.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Callsign dan LED Status (TX / RX / STBY)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Callsign
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onEditCallsign() }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = callsign,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        fontSize = 12.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = (if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)).copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(12.dp)
                            .padding(start = 2.dp)
                    )
                }

                // Status LEDs: TX (Merah), RX (Hijau), STBY (Cyan / Blue)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusLed(isDark = isDark, label = "TX", isActive = isTransmitting, activeColor = Color(0xFFEF4444))
                    StatusLed(isDark = isDark, label = "RX", isActive = isReceiving, activeColor = Color(0xFF10B981))
                    StatusLed(isDark = isDark, label = "STBY", isActive = !isTransmitting && !isReceiving, activeColor = if (isDark) Color(0xFF0284C7) else Color(0xFF0284C7))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OLED MATRIX / LCD SCREEN
            Surface(
                color = screenBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, screenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    // Top Bar Screen: Band & Kanal Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when (band) {
                                HtBand.VHF -> Color(0xFF0284C7).copy(alpha = if (isDark) 0.25f else 0.15f)
                                HtBand.UHF -> Color(0xFFD97706).copy(alpha = if (isDark) 0.25f else 0.15f)
                                else -> Color(0xFF7C3AED).copy(alpha = if (isDark) 0.25f else 0.15f)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = band.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (band) {
                                    HtBand.VHF -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                    HtBand.UHF -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                    else -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "${channel.code} • ${channel.name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = subTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Main Frequency Big Readout (Klik untuk buka Keypad Numerik)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManualFreq() },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        val freqText = String.format(Locale.US, "%.3f", frequencyMHz)
                        Text(
                            text = freqText,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = freqColor,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MHz",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Status Text Live Transmisi
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTransmitting) {
                            Text(
                                text = "🔴 MENTRANSMISIKAN SUARA ANDA...",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        } else if (isReceiving) {
                            Text(
                                text = "🔊 MENERIMA DARI: $rxCallsign",
                                color = if (isDark) Color(0xFF10B981) else Color(0xFF047857),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        } else {
                            Text(
                                text = "STANDBY • MONITORING FREKUENSI",
                                color = subTextColor,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // =========================================================================
                    // IKON PINTASAN CEPAT (Tepat di bawah title "STANDBY • MONITORING FREKUENSI")
                    // =========================================================================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFFE2E8F0).copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 0. Tombol Kembali / Keluar HT
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Beranda",
                                tint = subTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 1. Panduan & Mode HT Fisik
                        IconButton(
                            onClick = onOpenPhysicalHt,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = "HT Fisik & Gateway",
                                tint = if (roipGatewayMode) Color(0xFF10B981) else (if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 2. Radar Perangkat Sekitar
                        IconButton(
                            onClick = onOpenRadar,
                            modifier = Modifier.size(34.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (discoveredDevicesCount > 0) {
                                        Badge(
                                            containerColor = Color(0xFF10B981),
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Text("$discoveredDevicesCount", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = "Radar Perangkat Sekitar",
                                    tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 3. Riwayat Log Transmisi
                        IconButton(
                            onClick = onOpenLogs,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Riwayat Log",
                                tint = subTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 4. Pengaturan Audio & Booster
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Pengaturan Audio & Booster",
                                tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // DUAL S-METER / VU AUDIO LEVEL BARS
                    val currentAudioLevel = if (isTransmitting) txAudioLevel else if (isReceiving) rxAudioLevel else 0f
                    VuMeterDisplay(
                        isDark = isDark,
                        level = currentAudioLevel,
                        isTx = isTransmitting,
                        isRx = isReceiving
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Status Bar: SQL, Vol, Boosters, IP & Hotspot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SQL:$squelchLevel • SPK:${String.format(Locale.US, "%.1f", speakerGain)}x • MIC:${String.format(Locale.US, "%.1f", micGain)}x • BEEP:${if (rogerBeep) "ON" else "OFF"}",
                            fontSize = 9.sp,
                            color = subTextColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isHotspot) "HOTSPOT: $localIp" else "LAN: $localIp",
                            fontSize = 9.sp,
                            color = if (isHotspot) (if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)) else (if (isDark) Color(0xFF0F766E) else Color(0xFF0D9488)),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Keypad Numerik Bawaan Aplikasi (Bukan Keyboard Bawaan OS Android)
 * Menyediakan pengalaman tuning layaknya radio transceiver sungguhan
 */
@Composable
fun TacticalNumericKeypadDialog(
    isDark: Boolean,
    currentFrequency: Double,
    onFrequencySelected: (Double) -> Unit,
    onDismissRequest: () -> Unit
) {
    var inputBuffer by remember {
        mutableStateOf(String.format(Locale.US, "%.3f", currentFrequency))
    }

    val parsedFreq = inputBuffer.replace(",", ".").toDoubleOrNull()
    val isValid = parsedFreq != null && parsedFreq in 100.0..500.0

    val bandLabel = when {
        parsedFreq != null && parsedFreq in 136.0..174.0 -> "JALUR VHF (136.000 - 174.000 MHz)"
        parsedFreq != null && parsedFreq in 400.0..470.0 -> "JALUR UHF (400.000 - 470.000 MHz)"
        parsedFreq != null && parsedFreq in 100.0..500.0 -> "FREKUENSI KUSTOM (100.000 - 500.000 MHz)"
        else -> "DILUAR JANGKAUAN (Harus 100.000 - 500.000 MHz)"
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = if (isDark) Color(0xFF181E24) else Color(0xFFFFFFFF),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, if (isDark) Color(0xFF2C353F) else Color(0xFFCBD5E1)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Keypad
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dialpad,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Keypad Frekuensi HT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Layar Tampilan Frekuensi LCD
                Surface(
                    color = if (isDark) Color(0xFF070B0E) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (isValid) (if (isDark) Color(0xFF0E7490) else Color(0xFF0284C7)) else Color(0xFFEF4444)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = bandLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isValid) {
                                if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            } else Color(0xFFEF4444)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (inputBuffer.isEmpty()) "---.---" else inputBuffer,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = if (isValid) {
                                    if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                } else Color(0xFFEF4444),
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MHz",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preset Cepat Frekuensi Populer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("144.100", "145.000", "433.075", "446.006")
                    presets.forEach { p ->
                        Surface(
                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    inputBuffer = p
                                    HandyTalkyManager.playPttPressSound()
                                }
                        ) {
                            Text(
                                text = p,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Grid Tombol Numerik Taktis Bawaan Aplikasi (1-9, ., 0, DEL)
                val keyRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(".", "0", "DEL")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keyRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { key ->
                                KeypadButton(
                                    modifier = Modifier.weight(1f),
                                    isDark = isDark,
                                    label = key,
                                    onClick = {
                                        HandyTalkyManager.playPttPressSound()
                                        when (key) {
                                            "DEL" -> {
                                                if (inputBuffer.isNotEmpty()) {
                                                    inputBuffer = inputBuffer.dropLast(1)
                                                }
                                            }
                                            "." -> {
                                                if (!inputBuffer.contains(".") && inputBuffer.length < 7) {
                                                    inputBuffer += if (inputBuffer.isEmpty()) "0." else "."
                                                }
                                            }
                                            else -> {
                                                if (inputBuffer.length < 7) {
                                                    inputBuffer += key
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Baris Tombol Aksi: [Reset / C] & [Terapkan Frekuensi]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            inputBuffer = ""
                            HandyTalkyManager.playPttPressSound()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    ) {
                        Text("Reset / C", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (isValid && parsedFreq != null) {
                                onFrequencySelected(parsedFreq)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White,
                            disabledContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0),
                            disabledContentColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    ) {
                        Text("Terapkan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val isAction = label == "DEL" || label == "."
    val buttonBg = if (isDark) {
        if (isAction) Color(0xFF1E293B) else Color(0xFF263238)
    } else {
        if (isAction) Color(0xFFE2E8F0) else Color(0xFFFFFFFF)
    }
    val buttonBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val textColor = if (isDark) {
        if (isAction) Color(0xFF38BDF8) else Color.White
    } else {
        if (isAction) Color(0xFF0284C7) else Color(0xFF0F172A)
    }

    Surface(
        modifier = modifier
            .height(50.dp)
            .clickable { onClick() },
        color = buttonBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, buttonBorder),
        shadowElevation = if (!isDark) 1.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label == "DEL") {
                Icon(
                    imageVector = Icons.Default.Backspace,
                    contentDescription = "Hapus",
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = label,
                    fontSize = if (label == ".") 24.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatusLed(
    isDark: Boolean,
    label: String,
    isActive: Boolean,
    activeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else (if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)))
                .then(
                    if (isActive) {
                        Modifier.shadow(4.dp, CircleShape, spotColor = activeColor)
                    } else Modifier
                )
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) activeColor else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
        )
    }
}

/**
 * Animated VU Meter Bar Display (Green, Yellow, Red segments)
 */
@Composable
fun VuMeterDisplay(
    isDark: Boolean,
    level: Float,
    isTx: Boolean,
    isRx: Boolean
) {
    val totalSegments = 16
    val activeSegments = (level * totalSegments).toInt().coerceIn(0, totalSegments)
    val inactiveColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val meterLabelColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("S-METER / VU", fontSize = 8.sp, color = meterLabelColor, fontWeight = FontWeight.Bold)
            Text(if (isTx) "TX-LEVEL" else if (isRx) "RX-LEVEL" else "0 dB", fontSize = 8.sp, color = meterLabelColor)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 0 until totalSegments) {
                val isLit = i < activeSegments
                val segmentColor = when {
                    i >= 13 -> Color(0xFFEF4444) // Red
                    i >= 9 -> Color(0xFFF59E0B)  // Yellow
                    else -> Color(0xFF10B981)    // Green
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isLit) segmentColor else inactiveColor)
                )
            }
        }
    }
}

/**
 * Filter Chip Band VHF / UHF
 */
@Composable
fun BandFilterChip(
    isDark: Boolean,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val chipBg = if (isSelected) {
        Color(0xFF0284C7)
    } else {
        if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    }
    val textColor = if (isSelected) {
        Color.White
    } else {
        if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    }

    Surface(
        color = chipBg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Channel Selector Card (Theme Aware)
 */
@Composable
fun ChannelCard(
    isDark: Boolean,
    channel: HtChannel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isSelected) {
        if (isDark) Color(0xFF1E293B) else Color(0xFFE0F2FE)
    } else {
        if (isDark) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    }
    val borderColor = if (isSelected) {
        if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    } else {
        if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
    }
    val titleColor = if (isSelected) {
        if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    } else {
        if (isDark) Color.White else Color(0xFF0F172A)
    }
    val freqColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = if (!isDark && isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .width(135.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.code,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = titleColor
                )
                Text(
                    text = channel.band.shortName,
                    fontSize = 9.sp,
                    color = if (channel.band == HtBand.VHF) Color(0xFF0284C7) else (if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${String.format(Locale.US, "%.3f", channel.frequencyMHz)} MHz",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = freqColor,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.name,
                fontSize = 10.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Big Tactical PTT Push-To-Talk Button
 * Memiliki respons warna dinamis pada Mode Terang dan Mode Gelap
 */
@Composable
fun BigPttTacticalButton(
    isDark: Boolean,
    isTransmitting: Boolean,
    isReceiving: Boolean,
    rxCallsign: String,
    hasPermission: Boolean,
    handsfreeMode: Boolean,
    onRequestPermission: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onToggleHandsfree: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTransmitting) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val standByGradient = if (isDark) {
        Brush.radialGradient(
            listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A)
            )
        )
    } else {
        Brush.radialGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFE2E8F0)
            )
        )
    }

    val pttGradient = when {
        isTransmitting -> Brush.radialGradient(listOf(Color(0xFFDC2626), Color(0xFF991B1B)))
        isReceiving -> Brush.radialGradient(listOf(Color(0xFF047857), Color(0xFF064E3B)))
        else -> standByGradient
    }

    val pttBorderColor = when {
        isTransmitting -> Color(0xFFEF4444)
        isReceiving -> Color(0xFF10B981)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    }

    val pttContentColor = when {
        isTransmitting || isReceiving -> Color.White
        isDark -> Color.White
        else -> Color(0xFF0F172A)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .scale(if (isTransmitting) pulseScale else 1f),
            contentAlignment = Alignment.Center
        ) {
            // Ripple glow ring around PTT
            if (isTransmitting) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                )
            }

            // Outer tactile bezel ring
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .clip(CircleShape)
                    .background(pttGradient)
                    .border(
                        width = 4.dp,
                        color = pttBorderColor,
                        shape = CircleShape
                    )
                    .shadow(
                        elevation = if (isTransmitting) 16.dp else (if (isDark) 6.dp else 4.dp),
                        shape = CircleShape,
                        spotColor = if (isTransmitting) Color(0xFFEF4444) else (if (isDark) Color.Transparent else Color(0x33000000))
                    )
                    .then(
                        if (!hasPermission) {
                            Modifier.clickable { onRequestPermission() }
                        } else if (handsfreeMode) {
                            Modifier.clickable { onToggleHandsfree() }
                        } else {
                            Modifier.pointerInput(isReceiving) {
                                if (!isReceiving) {
                                    detectTapGestures(
                                        onPress = {
                                            onPressStart()
                                            tryAwaitRelease()
                                            onPressEnd()
                                        }
                                    )
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isTransmitting) Icons.Default.Mic else if (isReceiving) Icons.Default.VolumeUp else Icons.Default.MicNone,
                        contentDescription = "PTT",
                        tint = pttContentColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isTransmitting) "BICARA" else if (isReceiving) "MENERIMA" else "PTT",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = pttContentColor,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (handsfreeMode) {
                            if (isTransmitting) "KLIK STOP" else "KLIK BICARA"
                        } else {
                            if (isReceiving) "TERKUNCI" else "TEKAN & TAHAN"
                        },
                        fontSize = 9.sp,
                        color = pttContentColor.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status caption below PTT
        Text(
            text = if (isReceiving) {
                "Sedang menerima suara dari $rxCallsign... Jangan bicara sekarang."
            } else if (isTransmitting) {
                "Lepaskan tombol untuk mendengar balasan lawan bicara."
            } else if (handsfreeMode) {
                "Mode VOX / Handsfree aktif. Sentuh sekali untuk bicara, sentuh lagi untuk selesai."
            } else {
                "Tekan dan tahan tombol untuk mengirim suara ke semua HT di frekuensi ini."
            },
            color = if (isReceiving) (if (isDark) Color(0xFF10B981) else Color(0xFF047857))
                else if (isTransmitting) Color(0xFFEF4444)
                else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
