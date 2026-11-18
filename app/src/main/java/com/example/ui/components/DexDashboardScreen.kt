package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.models.AppMetadata
import com.example.ui.models.AppRegistry
import com.example.ui.screens.HijriCalendarHelper
import com.example.ui.screens.JavaneseCalendarHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Dashboard native Samsung DeX: meniru tata letak halaman Web Connect "Akses PC"
 * (DashboardWebScript.loadDashboard) dengan 6 bagian — hero sapaan (gradien teal +
 * shadow) + tanggal Masehi/Jawa/Hijriah, jam digital + kartu cuaca, kartu waktu
 * shalat (sorot berikutnya), kalender grid bulanan, quick action cards, tile
 * aplikasi, dan baris statistik. Sumber data native (helper + viewModel), bukan
 * localhost/WebView.
 */
@Composable
fun DexDashboardScreen(viewModel: MainViewModel) {
    val colors = rememberDexWebColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgMain)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        DexHeroSection(viewModel = viewModel, colors = colors)
        Spacer(Modifier.height(20.dp))

        // SECTION 2: Jam digital + kartu cuaca (sebaris, responsif)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) { DexClockCard(viewModel = viewModel, colors = colors) }
            Box(modifier = Modifier.weight(1f)) { DexWeatherCard(viewModel = viewModel, colors = colors) }
        }
        Spacer(Modifier.height(20.dp))

        // SECTION 3: Waktu shalat
        DexPrayerCard(viewModel = viewModel, colors = colors)
        Spacer(Modifier.height(20.dp))

        // SECTION 4: Kalender bulanan interaktif
        DexCalendarCard(viewModel = viewModel, colors = colors)
        Spacer(Modifier.height(20.dp))

        // SECTION 5: Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DexQuickActionCard(
                colors = colors, modifier = Modifier.weight(1f),
                label = "Tabungan", icon = Icons.Default.AccountBalanceWallet, iconColor = Color(0xFF10B981)
            ) { viewModel.currentRoute = "tabungan" }
            DexQuickActionCard(
                colors = colors, modifier = Modifier.weight(1f),
                label = "Kasir", icon = Icons.Default.ShoppingCart, iconColor = Color(0xFF0EA5E9)
            ) { viewModel.currentRoute = "my_kasir" }
            DexQuickActionCard(
                colors = colors, modifier = Modifier.weight(1f),
                label = "Catatan", icon = Icons.Default.EditNote, iconColor = Color(0xFF6366F1)
            ) { viewModel.currentRoute = "catatan" }
            DexQuickActionCard(
                colors = colors, modifier = Modifier.weight(1f),
                label = "Agenda", icon = Icons.Default.DateRange, iconColor = Color(0xFFF59E0B)
            ) { viewModel.currentRoute = "agenda" }
        }
        Spacer(Modifier.height(20.dp))

        // SECTION 6: My Aplikasi (tile squircle)
        DexAppsCard(viewModel = viewModel, colors = colors)
        Spacer(Modifier.height(20.dp))

        // Baris statistik
        DexStatsRow(viewModel = viewModel, colors = colors)
    }
}

/** Data satu kartu statistik pada dashboard DeX. */
private data class DexStat(val label: String, val value: String, val icon: ImageVector, val iconBg: Color)

// ---- SECTION COMPOSABLES (diisi bertahap) ----

@Composable
private fun DexHeroSection(viewModel: MainViewModel, colors: DexWebColors) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val (greeting, greetingIcon) = remember(hour) {
        when (hour) {
            in 5..10 -> "Selamat Pagi" to Icons.Default.WbSunny
            in 11..14 -> "Selamat Siang" to Icons.Default.SettingsBrightness
            in 15..17 -> "Selamat Sore" to Icons.Default.WbTwilight
            else -> "Selamat Malam" to Icons.Default.Bedtime
        }
    }
    val cal = remember { Calendar.getInstance() }
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    val masehi = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date()) }
    val jawa = remember { JavaneseCalendarHelper.calculateJawa(y, m, d) }
    val hijri = remember { HijriCalendarHelper.getHijriDate(y, m, d, viewModel.hijriOffset) }

    val heroShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, heroShape, ambientColor = Color(0x470F766E), spotColor = Color(0x470F766E))
            .clip(heroShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6), Color(0xFF06B6D4)))
            )
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(greetingIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(greeting, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = viewModel.profileName.ifEmpty { "Pengguna" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(masehi, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Jawa: ${jawa.dina} ${jawa.pasaran} \u2022 Wuku ${jawa.wuku} \u2022 ${hijri}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DexClockCard(viewModel: MainViewModel, colors: DexWebColors) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(1000)
        }
    }
    val hh = now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = now.get(Calendar.MINUTE).toString().padStart(2, '0')
    val ss = now.get(Calendar.SECOND).toString().padStart(2, '0')
    val weekday = remember { SimpleDateFormat("EEEE", Locale("id", "ID")).format(now.time) }

    val shape = RoundedCornerShape(24.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color(0xFFF3F7FE)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .shadow(6.dp, shape, ambientColor = colors.border, spotColor = colors.border)
            .clip(shape)
            .background(cardBg)
            .border(1.dp, Color(0x2E0D6EFD), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Waktu Sekarang", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSub)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$hh:$mm",
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = colors.accent,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = ss,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxHeight(),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(weekday, fontSize = 12.5.sp, color = colors.textSub)
    }
}

@Composable
private fun DexWeatherCard(viewModel: MainViewModel, colors: DexWebColors) {
    LaunchedEffect(Unit) { viewModel.fetchWeather() }
    val state by viewModel.weatherState.collectAsState()

    val shape = RoundedCornerShape(24.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .shadow(6.dp, shape, ambientColor = colors.border, spotColor = colors.border)
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x26F59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.WbCloudy, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Perkiraan Cuaca", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textMain)
                    Text("Harian", fontSize = 11.sp, color = colors.textSub)
                }
            }
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Perbarui Cuaca",
                tint = colors.accent,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { viewModel.fetchWeather() }.padding(2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is MainViewModel.WeatherUIState.Loading -> {
                Text("Memperbarui data cuaca...", fontSize = 12.5.sp, color = colors.textSub)
            }
            is MainViewModel.WeatherUIState.Error -> {
                Text(s.message, fontSize = 12.5.sp, color = colors.danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            is MainViewModel.WeatherUIState.Success -> {
                val w = s.response
                val group = w.weather?.firstOrNull()?.main ?: "Clouds"
                val cond = w.weather?.firstOrNull()?.description ?: "Berawan"
                val icon = if (group == "Clear") Icons.Default.WbSunny else Icons.Default.WbCloudy
                val loc = w.name ?: viewModel.userLocationName ?: "Lokasi Anda"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(40.dp))
                    Column {
                        val temp = w.main?.temp
                        Text(
                            text = if (temp != null) "%.1f\u00B0C".format(temp) else "--\u00B0C",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMain
                        )
                        Text(cond.replaceFirstChar { it.uppercase(Locale("id", "ID")) }, fontSize = 12.5.sp, color = colors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(loc, fontSize = 11.sp, color = colors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val hum = w.main?.humidity?.toInt()
                    val wind = w.wind?.speed
                    Text("Kelembaban ${hum?.toString() ?: "--"}%", fontSize = 11.sp, color = colors.textSub)
                    Text("Angin ${wind?.let { "%.0f".format(it) } ?: "--"} m/s", fontSize = 11.sp, color = colors.textSub)
                }
            }
        }
    }
}

@Composable
private fun DexPrayerCard(viewModel: MainViewModel, colors: DexWebColors) {
    val sched = viewModel.prayerSchedule
    val items = listOf(
        "Imsak" to sched.imsak,
        "Subuh" to sched.subuh,
        "Dzuhur" to sched.dzuhur,
        "Ashar" to sched.ashar,
        "Maghrib" to sched.maghrib,
        "Isya" to sched.isya
    )
    val nowCal = remember { Calendar.getInstance() }
    val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    fun toMin(s: String): Int = runCatching { val p = s.split(":"); p[0].toInt() * 60 + p[1].toInt() }.getOrDefault(-1)
    val nextLabel = items.firstOrNull { toMin(it.second) >= nowMin }?.first ?: "Imsak"

    // Klik salah satu waktu -> popup detail jadwal + hitung mundur ke waktu berikutnya.
    var selectedPrayer by remember { mutableStateOf<Pair<String, String>?>(null) }

    DexCard(colors = colors, shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Text("Waktu Shalat", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textMain)
            Spacer(Modifier.weight(1f))
            Text(viewModel.activeTimeZone, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (label, time) ->
                val isNext = label == nextLabel
                val cellShape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(cellShape)
                        .background(if (isNext) colors.selectedBg else colors.hover.copy(alpha = 0.4f))
                        .clickable { selectedPrayer = label to time }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 11.sp, color = if (isNext) colors.onSelectedBg else colors.textSub, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(time, fontSize = 13.sp, fontWeight = if (isNext) FontWeight.Bold else FontWeight.SemiBold, color = if (isNext) colors.onSelectedBg else colors.textMain, maxLines = 1)
                }
            }
        }
    }

    selectedPrayer?.let { (label, time) ->
        AlertDialog(
            onDismissRequest = { selectedPrayer = null },
            containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White,
            title = { Text("Waktu Shalat - $label", color = colors.textMain) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pukul $time \u2022 Zona ${viewModel.activeTimeZone}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textMain
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Jadwal hari ini:", fontSize = 12.sp, color = colors.textSub)
                    Spacer(Modifier.height(4.dp))
                    items.forEach { (l, t) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                l, fontSize = 13.sp,
                                color = if (l == label) colors.accent else colors.textMain,
                                fontWeight = if (l == label) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                t, fontSize = 13.sp,
                                color = if (l == label) colors.accent else colors.textSub,
                                fontWeight = if (l == label) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DexPrayerCountdown(items = items, nextLabel = nextLabel, colors = colors)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPrayer = null }) { Text("Tutup") }
            }
        )
    }
}

/** Hitung mundur langsung (detik) menuju waktu shalat berikutnya; berhenti saat popup ditutup. */
@Composable
private fun DexPrayerCountdown(items: List<Pair<String, String>>, nextLabel: String, colors: DexWebColors) {
    var secsLeft by remember { mutableStateOf(0L) }
    LaunchedEffect(items, nextLabel) {
        fun toSec(s: String): Int = runCatching {
            val p = s.split(":"); p[0].toInt() * 3600 + p[1].toInt() * 60
        }.getOrDefault(0)
        while (true) {
            val now = Calendar.getInstance()
            val nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND)
            val nextTime = items.firstOrNull { it.first == nextLabel }?.second ?: "00:00"
            var diff = toSec(nextTime) - nowSec
            if (diff < 0) diff += 24 * 3600
            secsLeft = diff.toLong()
            delay(1000)
        }
    }
    val h = secsLeft / 3600
    val m = (secsLeft % 3600) / 60
    val s = secsLeft % 60
    Text(
        text = "Menuju $nextLabel: %02d:%02d:%02d".format(h, m, s),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = colors.accent
    )
}

@Composable
private fun DexCalendarCard(viewModel: MainViewModel, colors: DexWebColors) {
    val today = remember { Calendar.getInstance() }
    val tY = today.get(Calendar.YEAR)
    val tM = today.get(Calendar.MONTH)
    val tD = today.get(Calendar.DAY_OF_MONTH)
    var viewY by remember { mutableStateOf(tY) }
    var viewM by remember { mutableStateOf(tM) } // 0-based
    val agendas by viewModel.agendaItems.collectAsState()

    val dayTipe = remember(viewY, viewM, agendas) {
        val map = HashMap<Int, String>()
        agendas.forEach { a ->
            val p = a.tanggal.split("-")
            if (p.size == 3) {
                val ay = p[0].toIntOrNull()
                val am = p[1].toIntOrNull()
                val ad = p[2].toIntOrNull()
                if (ay == viewY && am == viewM + 1 && ad != null) map[ad] = a.tipe
            }
        }
        map
    }

    val cells = remember(viewY, viewM) {
        val c = Calendar.getInstance()
        c.set(viewY, viewM, 1)
        val firstDow = c.get(Calendar.DAY_OF_WEEK)
        val dim = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val list = ArrayList<Int?>()
        repeat(firstDow - 1) { list.add(null) }
        (1..dim).forEach { list.add(it) }
        while (list.size % 7 != 0) list.add(null)
        list
    }

    val monthLabel = remember(viewY, viewM) {
        val c = Calendar.getInstance()
        c.set(viewY, viewM, 1)
        SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(c.time)
    }
    val weekdays = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    DexCard(colors = colors, shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Text("Kalender", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textMain)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(monthLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSub)
                Icon(Icons.Default.ChevronLeft, contentDescription = "Sebelumnya", tint = colors.accent,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                        if (viewM == 0) { viewM = 11; viewY -= 1 } else viewM -= 1
                    })
                Icon(Icons.Default.ChevronRight, contentDescription = "Berikutnya", tint = colors.accent,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable {
                        if (viewM == 11) { viewM = 0; viewY += 1 } else viewM += 1
                    })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { wd ->
                Text(
                    text = wd,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSub
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val isToday = (viewY == tY && viewM == tM && day == tD)
                            val tipe = dayTipe[day]
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isToday) colors.accent else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = day.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday) Color.White else colors.textMain
                                    )
                                    if (tipe != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (tipe == "libur") Color(0xFFE74C3C) else colors.accent)
                                        )
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
private fun DexQuickActionCard(
    colors: DexWebColors,
    label: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .shadow(6.dp, shape, ambientColor = colors.border, spotColor = colors.border)
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DexAppsCard(viewModel: MainViewModel, colors: DexWebColors) {
    val apps = remember { AppRegistry.defaultOrder.mapNotNull { AppRegistry.allAppsMap[it] } }
    val columnsPerRow = 6
    DexCard(colors = colors, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("My Aplikasi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable {
                        viewModel.currentRoute = "home"
                        viewModel.showAllAppsPage = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Lihat Semua Aplikasi", tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        apps.chunked(columnsPerRow).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowApps.forEach { app ->
                    DexAppTile(
                        app = app,
                        colors = colors,
                        modifier = Modifier.weight(1f),
                        onOpen = { viewModel.currentRoute = app.route ?: "game_list" }
                    )
                }
                repeat(columnsPerRow - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DexStatsRow(viewModel: MainViewModel, colors: DexWebColors) {
    val notes by viewModel.notes.collectAsState()
    val agendas by viewModel.agendaItems.collectAsState()
    val shopping by viewModel.shoppingItems.collectAsState()
    val stats = listOf(
        DexStat("Catatan", notes.size.toString(), Icons.Default.EditNote, Color(0xFF6366F1)),
        DexStat("Agenda", agendas.size.toString(), Icons.Default.DateRange, Color(0xFFF59E0B)),
        DexStat("Item Belanja", shopping.size.toString(), Icons.Default.ShoppingCart, Color(0xFF10B981)),
        DexStat("Aplikasi", AppRegistry.allAppsMap.size.toString(), Icons.Default.GridOn, Color(0xFF0EA5E9))
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        stats.forEach { (label, value, icon, iconBg) ->
            DexStatCard(label = label, value = value, icon = icon, iconBg = iconBg, colors = colors, modifier = Modifier.weight(1f))
        }
    }
}

// ---- KARTU DASAR (dipakai beberapa section) ----

/** Kontainer kartu ala Web Connect: radius besar, border tipis, shadow lembut. */
@Composable
private fun DexCard(
    colors: DexWebColors,
    shape: androidx.compose.foundation.shape.RoundedCornerShape = RoundedCornerShape(20.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = colors.border, spotColor = colors.border)
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun DexAppTile(
    app: AppMetadata,
    colors: DexWebColors,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onOpen).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(app.color),
            contentAlignment = Alignment.Center
        ) {
            Icon(app.icon, contentDescription = app.title, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DexStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconBg: Color,
    colors: DexWebColors,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val cardBg = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
    Row(
        modifier = modifier
            .shadow(6.dp, shape, ambientColor = colors.border, spotColor = colors.border)
            .clip(shape)
            .background(cardBg)
            .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 11.5.sp, color = colors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
