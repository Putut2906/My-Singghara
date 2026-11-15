package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay

private enum class PinFlow { None, Create, Change }

@Composable
fun AppLockScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    var flow by rememberSaveable { mutableStateOf(PinFlow.None.name) }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var oldInput by rememberSaveable { mutableStateOf("") }
    var newInput by rememberSaveable { mutableStateOf("") }
    var confirmInput by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    val currentFlow = runCatching { PinFlow.valueOf(flow) }.getOrDefault(PinFlow.None)
    val hasPin = viewModel.appPin.isNotEmpty()
    val inFlow = currentFlow != PinFlow.None

    // Tombol back OS Android memicu pop-up konfirmasi sebelum keluar (menggantikan icon <- pada header).
    BackHandler(enabled = !showExitDialog) {
        showExitDialog = true
    }

    fun resetFlow() {
        flow = PinFlow.None.name; step = 1
        oldInput = ""; newInput = ""; confirmInput = ""; error = ""
    }

    fun finish(pin: String) {
        viewModel.saveAppPin(pin)
        viewModel.saveAppLockEnabled(true)
        viewModel.isAppUnlocked = true
        resetFlow()
        viewModel.showToastPopup("PIN berhasil disimpan & proteksi aktif!")
    }

    // Auto-advance per step (paritas dengan dialog lama).
    LaunchedEffect(oldInput) {
        if (currentFlow == PinFlow.Change && step == 1 && oldInput.length == 4) {
            delay(200)
            if (oldInput == viewModel.appPin) { error = ""; step = 2 }
            else { error = "PIN lama Anda salah!"; oldInput = "" }
        }
    }
    LaunchedEffect(newInput) {
        if (currentFlow == PinFlow.Create && step == 1 && newInput.length == 4) { delay(200); error = ""; step = 2 }
        if (currentFlow == PinFlow.Change && step == 2 && newInput.length == 4) { delay(200); error = ""; step = 3 }
    }
    LaunchedEffect(confirmInput) {
        if (inFlow && step == (if (currentFlow == PinFlow.Change) 3 else 2) && confirmInput.length == 4) {
            delay(200)
            if (newInput == confirmInput) finish(newInput)
            else { error = "PIN konfirmasi tidak cocok!"; confirmInput = "" }
        }
    }

    // Buffer aktif untuk step saat ini + label langkah.
    val activeText = when {
        currentFlow == PinFlow.Change && step == 1 -> oldInput
        step == (if (currentFlow == PinFlow.Change) 2 else 1) -> newInput
        else -> confirmInput
    }
    val stepLabel = when {
        currentFlow == PinFlow.Change && step == 1 -> "1. Masukkan PIN Lama"
        currentFlow == PinFlow.Change && step == 2 -> "2. Buat PIN Baru"
        currentFlow == PinFlow.Change && step == 3 -> "3. Konfirmasi PIN Baru"
        currentFlow == PinFlow.Create && step == 2 -> "2. Konfirmasi PIN Baru"
        else -> "1. Buat PIN Baru"
    }

    fun onDigit(num: String) {
        error = ""
        when {
            currentFlow == PinFlow.Change && step == 1 -> if (oldInput.length < 4) oldInput += num
            step == (if (currentFlow == PinFlow.Change) 2 else 1) -> if (newInput.length < 4) newInput += num
            else -> if (confirmInput.length < 4) confirmInput += num
        }
    }

    fun onBackspace() {
        error = ""
        when {
            currentFlow == PinFlow.Change && step == 1 -> if (oldInput.isNotEmpty()) oldInput = oldInput.dropLast(1)
            step == (if (currentFlow == PinFlow.Change) 2 else 1) -> {
                if (newInput.isNotEmpty()) newInput = newInput.dropLast(1)
                else if (currentFlow == PinFlow.Change) step = 1
            }
            else -> {
                if (confirmInput.isNotEmpty()) confirmInput = confirmInput.dropLast(1)
                else step = if (currentFlow == PinFlow.Change) 2 else 1
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Kartu status + toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(containerColor = cs.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (viewModel.isAppLockEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kunci Aplikasi", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        Text(
                            text = if (viewModel.isAppLockEnabled) "Proteksi PIN aktif" else "Proteksi tidak aktif",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.isAppLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (hasPin) {
                                    viewModel.saveAppLockEnabled(true)
                                    viewModel.isAppUnlocked = true
                                } else {
                                    flow = PinFlow.Create.name; step = 1
                                }
                            } else {
                                viewModel.saveAppLockEnabled(false)
                                viewModel.isAppUnlocked = true
                                resetFlow()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Kartu PIN
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(containerColor = cs.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasPin) "Ubah PIN Kunci Aplikasi" else "Buat PIN Kunci Aplikasi",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = cs.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gunakan 4 digit PIN untuk mengamankan aplikasi Anda.",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!inFlow) {
                        Button(
                            onClick = { flow = if (hasPin) PinFlow.Change.name else PinFlow.Create.name; step = 1; error = "" },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (hasPin) "Ubah PIN" else "Buat PIN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        // Indikator langkah + titik PIN
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(stepLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    for (i in 0 until 4) {
                                        val filled = i < activeText.length
                                        Box(
                                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                                .background(if (filled) cs.primary else cs.surface)
                                                .border(
                                                    width = if (filled) 0.dp else 1.5.dp,
                                                    color = if (filled) Color.Unspecified else cs.outline.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        if (error.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(error, color = cs.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Keypad
                        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
                        rows.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEach { num ->
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        AppLockKey(text = num, onClick = { onDigit(num) })
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                AppLockKey(text = "0", onClick = { onDigit("0") })
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                        .background(cs.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { onBackspace() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Backspace, contentDescription = "Hapus", tint = cs.onSurface, modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { resetFlow() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Batal", color = cs.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Keluar dari halaman ini?") },
            text = { Text("Perubahan PIN yang belum selesai tidak akan disimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onBack()
                }) { Text("Ya", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Batal", color = cs.error, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun AppLockKey(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(cs.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
    }
}
