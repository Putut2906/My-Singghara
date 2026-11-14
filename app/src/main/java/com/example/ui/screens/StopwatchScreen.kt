package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay

// Aksen stopwatch: teal/indigo lembut yang konsisten pada mode terang & gelap.
private val SwAccent = Color(0xFF4F46E5)
private val SwAccentSoft = Color(0xFFEEF2FF)
private val SwBest = Color(0xFF059669)
private val SwWorst = Color(0xFFDC2626)

@Composable
private fun swIsLight(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() > 0.5f

@Composable
private fun swAccent(): Color = if (swIsLight()) SwAccent else Color(0xFF818CF8)

@Composable
private fun swSurface(): Color =
    if (swIsLight()) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

@Composable
private fun swBorder(): Color =
    if (swIsLight()) Color(0xFFE2E8F0) else swAccent().copy(alpha = 0.28f)

/**
 * Stopwatch presisi: mengukur durasi dari titik mulai (start) hingga berhenti (stop)
 * memakai System.nanoTime() agar tidak terpengaruh perubahan jam sistem.
 * Mendukung Start/Stop, Reset, dan Lap dengan pemecah waktu, penanda terbaik/terburuk.
 */
@Composable
fun StopwatchScreen(
    viewModel: MainViewModel? = null,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val view = LocalView.current
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // Akumulator waktu (nanodetik) saat berhenti, dan titik awal saat berjalan.
    var baseNanos by remember { mutableLongStateOf(0L) }
    var startMark by remember { mutableLongStateOf(0L) } // 0 == tidak berjalan
    var isRunning by remember { mutableStateOf(false) }
    // Lap menyimpan total waktu (nanodetik) pada saat tombol Lap ditekan.
    var lapTotals by remember { mutableStateOf(listOf<Long>()) }
    // Memicu recomposition berkala saat stopwatch berjalan.
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning) {
        while (isRunning) {
            tick = System.nanoTime()
            delay(16L) // ~60 fps untuk tampilan sentidetik
        }
    }

    val totalNanos: Long = if (isRunning) {
        // Membaca `tick` membuat tampilan ikut ter-recomposition tiap frame.
        baseNanos + (tick - startMark)
    } else {
        baseNanos
    }

    fun start() {
        if (!isRunning) {
            val now = System.nanoTime()
            startMark = now
            tick = now
            isRunning = true
        }
    }

    fun stop() {
        if (isRunning) {
            baseNanos += System.nanoTime() - startMark
            startMark = 0L
            isRunning = false
        }
    }

    fun reset() {
        baseNanos = 0L
        startMark = 0L
        isRunning = false
        lapTotals = emptyList()
    }

    fun lap() {
        // Lap hanya bermakna jika ada waktu berjalan/tercatat.
        if (totalNanos > 0L) {
            lapTotals = lapTotals + totalNanos
        }
    }

    // Durasi tiap lap (split) dari daftar total.
    val lapDurations = remember(lapTotals) {
        lapTotals.mapIndexed { index, total ->
            if (index == 0) total else total - lapTotals[index - 1]
        }
    }
    val bestLap = lapDurations.filter { it > 0L }.minOrNull()
    val worstLap = lapDurations.filter { it > 0L }.maxOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (swIsLight()) SwAccentSoft else swAccent().copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = swAccent(),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Stopwatch",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (isRunning) "Berjalan" else if (totalNanos > 0L) "Berhenti" else "Siap",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRunning) swAccent() else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Kartu tampilan waktu
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = swSurface(),
            border = BorderStroke(1.dp, swBorder()),
            shadowElevation = if (swIsLight()) 3.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatStopwatch(totalNanos),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (totalNanos >= 3_600_000_000_000L) 40.sp else 48.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "mulai hingga berhenti secara presisi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Baris kontrol utama: Start/Stop + Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tombol Start / Stop (toggle)
            val primaryLabel = if (isRunning) "Stop" else if (totalNanos > 0L) "Lanjut" else "Start"
            val primaryColor = if (isRunning) SwWorst else swAccent()
            Button(
                onClick = {
                    tap()
                    if (isRunning) stop() else start()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(primaryLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // Tombol Reset
            OutlinedButton(
                onClick = {
                    tap()
                    reset()
                },
                enabled = totalNanos > 0L || lapTotals.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                border = BorderStroke(1.dp, swBorder()),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tombol Lap
        Button(
            onClick = {
                tap()
                if (isRunning) lap() else start()
            },
            enabled = isRunning || totalNanos > 0L,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (swIsLight()) SwAccentSoft else swAccent().copy(alpha = 0.16f),
                contentColor = swAccent(),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRunning) "Lap" else "Mulai & Lap", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (lapDurations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Belum ada lap. Tekan Lap saat berjalan untuk mencatat waktu per putaran.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            // Header daftar lap
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lap",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp)
                )
                Text(
                    "Durasi Lap",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Total",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                    textAlign = TextAlign.End
                )
            }
            HorizontalDivider(color = swBorder())

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Daftar dibalik manual agar lap terbaru tampil di atas.
                items(lapDurations.indices.toList().reversed()) { index ->
                    val duration = lapDurations[index]
                    val total = lapTotals[index]
                    val labelColor = when {
                        lapDurations.size >= 2 && duration == bestLap -> SwBest
                        lapDurations.size >= 2 && duration == worstLap -> SwWorst
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lap ${index + 1}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(56.dp)
                        )
                        Text(
                            text = formatStopwatch(duration),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = formatStopwatch(total),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    HorizontalDivider(color = swBorder().copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * Format nanodetik menjadi HH:MM:SS.CC (sentidetik).
 * Bagian jam hanya ditampilkan bila total >= 1 jam.
 */
private fun formatStopwatch(nanos: Long): String {
    val safe = if (nanos < 0L) 0L else nanos
    val totalCentis = safe / 10_000_000L
    val centis = totalCentis % 100L
    val totalSecs = totalCentis / 100L
    val secs = totalSecs % 60L
    val totalMins = totalSecs / 60L
    val mins = totalMins % 60L
    val hours = totalMins / 60L

    val cs = centis.toString().padStart(2, '0')
    val ss = secs.toString().padStart(2, '0')
    val mm = mins.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:$mm:$ss.$cs"
    } else {
        "$mm:$ss.$cs"
    }
}
