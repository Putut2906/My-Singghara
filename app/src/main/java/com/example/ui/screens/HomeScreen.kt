@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build

import com.example.ui.models.AppRegistry

import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import com.example.NotificationScheduler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.AgendaItem
import com.example.ui.MainViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.network.HolidayClient
import com.example.util.CalendarExportHelper
import com.example.ui.components.PrayerTimesHelper
import java.util.*
import java.text.SimpleDateFormat

// Helper Model untuk Hasil Kalender Jawa
data class JawaDate(val dina: String, val pasaran: String, val wuku: String)

private data class CalendarCellData(
    val dayNumber: Int,
    val targetCal: Calendar,
    val pasaran: String,
    val isToday: Boolean,
    val holidayName: String?,
    val observanceName: String?,
    val hasAgenda: Boolean,
    val isSunday: Boolean
)

private data class DayAgendaDetail(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateKey: String,
    val jDate: JawaDate,
    val hDate: String,
    val holiday: String?,
    val observance: String?,
    val dayAgendas: List<AgendaItem>
)

object JavaneseCalendarHelper {
    private val DINA_JAWA = listOf("Ahad", "Senen", "Selasa", "Rebo", "Kemis", "Jemuwah", "Setu")
    private val PASARAN = listOf("Legi", "Pahing", "Pon", "Wage", "Kliwon") // Legi, Pahing, Pon, Wage, Kliwon
    private val WUKU = listOf(
        "Sinta", "Landep", "Wukir", "Kurantil", "Tolu", "Gumbreg", "Warigalit",
        "Warigagung", "Julungwangi", "Sungsang", "Galungan", "Kuningan", "Langkir",
        "Mandhasiya", "Julungpujut", "Pahang", "Kuruwelut", "Marakeh", "Tambir",
        "Medhangkungan", "Maktal", "Wuye", "Manail", "Prangbakat", "Bala", "Wugu",
        "Wayang", "Kulawu", "Dukut", "Watugunung"
    )

    fun calculateJawa(year: Int, month: Int, day: Int): JawaDate {
        // High accuracy astronomical JD-based convertor
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val e = (365.25 * (y + 4716)).toLong()
        val f = (30.6001 * (m + 1)).toLong()
        val jdn = c + day + e + f - 1524

        // 2461205 % 5 = 0 (Legi) -> June 13, 2026 is sabato legi
        val pasaranIdx = ((jdn % 5 + 5) % 5).toInt()
        
        // (2461205 + 1) % 7 = 6 (Setu / Sabtu)
        val dinaIdx = (((jdn + 1) % 7 + 7) % 7).toInt()

        // Standard continuous Javanese 210-day pawukon cycle calculation
        val wukuIdx = (((jdn + 2) / 7) % 30 + 30).toInt() % 30

        val dina = DINA_JAWA[dinaIdx]
        val pasaran = PASARAN[pasaranIdx]
        val wuk = WUKU[wukuIdx]

        return JawaDate(dina, pasaran, wuk)
    }
}

// Simple Hijriah Converter (Highly accurate astronomical tabular helper)
object HijriCalendarHelper {
    private val MONTHS = listOf(
        "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir",
        "Jumadil Awwal", "Jumadil Akhir", "Rajab", "Sya'ban",
        "Ramadhan", "Syawwal", "Dzulqa'dah", "Dzulhijjah"
    )

    fun getHijriDate(year: Int, month: Int, day: Int, offsetDays: Int = -1): String {
        // Calculate Gregorian to JDN
        val y = if (month <= 2) year - 1 else year
        val m = if (month <= 2) month + 12 else month
        val a = y / 100
        val b = a / 4
        val c = 2 - a + b
        val e = (365.25 * (y + 4716)).toLong()
        val f = (30.6001 * (m + 1)).toLong()
        
        // Apply customizable offset to align with local lunar sightings (Kemenag RI)
        val jd = c + day + e + f - 1524 + offsetDays

        val days = jd - 1948439
        val cycle = days / 10631
        val cycleRemainder = days % 10631

        val yearInCycle = ((30 * cycleRemainder + 11) / 10631).toInt()
        val hYear = cycle * 30 + yearInCycle + 1

        val dayOfYearInCycle = cycleRemainder - ((yearInCycle * 10631 + 14) / 30)
        val hMonthNum = ((dayOfYearInCycle * 2) / 59).toInt() + 1
        val hDay = (dayOfYearInCycle - ((hMonthNum - 1) * 59 + 1) / 2).toInt() + 1

        val finalMonthIdx = (hMonthNum - 1).coerceIn(0, 11)
        val finalDay = hDay.coerceAtLeast(1)

        return "$finalDay ${MONTHS[finalMonthIdx]} ${hYear} H"
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val currentTime by viewModel.currentTime.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val currentDay by viewModel.currentDay.collectAsState()
    val apiHolidays by viewModel.apiHolidays.collectAsState()
    val agendaItems by viewModel.agendaItems.collectAsState()
    val liveHijri by viewModel.apiHijriDate.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()

    val context = LocalContext.current

    var activeGameSection by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeGameSection) {
        viewModel.isBottomBarVisible = (activeGameSection == null)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.isBottomBarVisible = true
        }
    }

    if (viewModel.showAllAppsPage) {
        androidx.activity.compose.BackHandler {
            viewModel.showAllAppsPage = false
        }
        AllAppsScreen(
            onBackClick = { viewModel.showAllAppsPage = false },
            onLaunchApp = { appRoute ->
                viewModel.currentRoute = appRoute
                viewModel.showAllAppsPage = false
            },
            onLaunchGame = { gameSection ->
                activeGameSection = gameSection
                viewModel.showAllAppsPage = false
            }
        )
        return
    }

    if (activeGameSection == "sliding_puzzle") {
        androidx.activity.compose.BackHandler {
            activeGameSection = null
            viewModel.showAllAppsPage = true
        }
        SlidingPuzzleGame(
            onBackClick = {
                activeGameSection = null
                viewModel.showAllAppsPage = true
            }
        )
        return
    }

    if (activeGameSection == "block_puzzle") {
        androidx.activity.compose.BackHandler {
            activeGameSection = null
            viewModel.showAllAppsPage = true
        }
        BlockPuzzleGame(
            onBackClick = {
                activeGameSection = null
                viewModel.showAllAppsPage = true
            }
        )
        return
    }

    if (activeGameSection == "tetris") {
        androidx.activity.compose.BackHandler {
            activeGameSection = null
            viewModel.showAllAppsPage = true
        }
        TetrisGame(
            onBackClick = {
                activeGameSection = null
                viewModel.showAllAppsPage = true
            }
        )
        return
    }

    if (activeGameSection == "undian_spinner") {
        androidx.activity.compose.BackHandler {
            activeGameSection = null
            viewModel.showAllAppsPage = true
        }
        UndianSpinnerScreen(
            onBackClick = {
                activeGameSection = null
                viewModel.showAllAppsPage = true
            }
        )
        return
    }

    var popupToastMessage by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.fetchCurrentLocation(context)
            // Re-trigger live Aladhan API for today based on new parameters
            val today = Calendar.getInstance()
            val formatStr = String.format("%02d-%02d-%04d", today.get(Calendar.DAY_OF_MONTH), today.get(Calendar.MONTH) + 1, today.get(Calendar.YEAR))
            viewModel.loadLiveAladhanHijri(formatStr)
            popupToastMessage = "Izin lokasi aktif! Mengambil koordinat GPS & sinkronisasi jadwal sholat..."
        } else {
            popupToastMessage = "Izin ditolak. Menggunakan kota standar WIB/WITA/WIT."
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            viewModel.fetchCurrentLocation(context)
            val today = Calendar.getInstance()
            val formatStr = String.format("%02d-%02d-%04d", today.get(Calendar.DAY_OF_MONTH), today.get(Calendar.MONTH) + 1, today.get(Calendar.YEAR))
            viewModel.loadLiveAladhanHijri(formatStr)
        } else {
            if (viewModel.userLatitude == null) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    var viewCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val viewYear = viewCalendar.get(Calendar.YEAR)
    LaunchedEffect(viewYear) {
        viewModel.loadRetrofitHolidays(viewYear)
    }

    var selectedDayAgenda by remember { mutableStateOf<Calendar?>(null) }
    var showAddAgendaPopup by remember { mutableStateOf(false) }
    var showSafWarningDialog by remember { mutableStateOf(false) }
    var showTimezoneMenu by remember { mutableStateOf(false) }
    var showClockFontDialog by remember { mutableStateOf(false) }
    var isClockResizing by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName = "font_custom.ttf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) fileName = name
                    }
                }

                val fontFile = java.io.File(context.filesDir, "custom_clock_font.ttf")
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.io.FileOutputStream(fontFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (fontFile.exists() && fontFile.length() > 0) {
                    val testTf = android.graphics.Typeface.createFromFile(fontFile)
                    if (testTf != null) {
                        viewModel.saveCustomClockFont(fontFile.absolutePath, fileName)
                        viewModel.showToastPopup("Font jam digital berhasil diubah ke: $fileName")
                    } else {
                        viewModel.showToastPopup("Gagal membaca file font.")
                    }
                } else {
                    viewModel.showToastPopup("Gagal membaca file font.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                viewModel.showToastPopup("File font tidak valid atau tidak didukung.")
            }
        }
    }

    val clockFontFamily: FontFamily = remember(viewModel.customClockFontPath) {
        if (viewModel.customClockFontPath.isNotEmpty()) {
            try {
                val targetFile = java.io.File(viewModel.customClockFontPath)
                val fontFolderFile = java.io.File(com.example.util.SinggharaFolderManager.getFontFolder(), targetFile.name)
                val fontFile = when {
                    targetFile.exists() -> targetFile
                    fontFolderFile.exists() -> fontFolderFile
                    else -> java.io.File(context.filesDir, targetFile.name)
                }
                if (fontFile.exists() && fontFile.length() > 0) {
                    val tf = android.graphics.Typeface.createFromFile(fontFile)
                    FontFamily(Typeface(tf))
                } else {
                    com.example.ui.theme.DsDigitalFontFamily
                }
            } catch (e: Exception) {
                e.printStackTrace()
                com.example.ui.theme.DsDigitalFontFamily
            }
        } else {
            com.example.ui.theme.DsDigitalFontFamily
        }
    }
    var showMonthEventsPopup by remember { mutableStateOf(false) }
    var showMonthAddAgendaForm by remember { mutableStateOf(false) }
    var monthAgendaSelectedDay by remember { mutableIntStateOf(1) }
    var monthAgendaTitle by remember { mutableStateOf("") }
    var monthAgendaDesc by remember { mutableStateOf("") }
    var monthAgendaType by remember { mutableStateOf("agenda") }
    var selectedExportScope by remember { mutableStateOf("12_MONTHS") }
    var selectedExportFormat by remember { mutableStateOf("PDF") }
    var selectedPaperType by remember { mutableStateOf("Art Paper (120–150 gsm)") }
    var isExportingCalendar by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var agendaToDelete by remember { mutableStateOf<AgendaItem?>(null) }
    var feedbackBannerMessage by remember { mutableStateOf<String?>(null) }
    var feedbackBannerIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var feedbackBannerColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(feedbackBannerMessage) {
        if (feedbackBannerMessage != null) {
            kotlinx.coroutines.delay(2800)
            feedbackBannerMessage = null
        }
    }
    var showPrayerSettingsDialog by remember { mutableStateOf(false) }
    var selectedPrayerForSoundSettings by remember { mutableStateOf<String?>(null) }
    var showAzanSourceSelectionForPrayer by remember { mutableStateOf<String?>(null) }
    var selectedPrayerForFilePicker by remember { mutableStateOf<String?>(null) }

    var previewMediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var previewPlayingMode by remember { mutableStateOf<Int?>(null) } // 2 = default, 3 = custom mp3

    val audioPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val prayer = selectedPrayerForFilePicker
        if (uri != null && prayer != null) {
            val success = viewModel.saveCustomAzanSound(prayer, uri, context)
            if (success) {
                popupToastMessage = "Berhasil memuat suara kustom untuk $prayer"
                com.example.NotificationScheduler.scheduleAlarms(context)
            } else {
                popupToastMessage = "Gagal memuat file suara kustom"
            }
        }
        selectedPrayerForFilePicker = null
    }

    fun playPreview(mode: Int, prayerName: String) {
        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            previewMediaPlayer = null
            if (previewPlayingMode == mode) {
                previewPlayingMode = null
                return
            }

            val mp: android.media.MediaPlayer? = if (mode == 1) {
                try {
                    val notifUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    android.media.MediaPlayer().apply {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, notifUri)
                        isLooping = false
                        prepare()
                    }
                } catch (e: Exception) {
                    popupToastMessage = "Gagal memutar nada notifikasi: ${e.message}"
                    null
                }
            } else if (mode == 3) {
                val prayerKey = prayerName.lowercase(java.util.Locale.getDefault())
                val customFile = java.io.File(context.filesDir, "azan_custom_$prayerKey.mp3")
                val externalFile = java.io.File(com.example.util.SinggharaFolderManager.getMp3Folder(), "azan_$prayerKey.mp3")
                val customPath = context.getSharedPreferences("mysingghara_prefs", android.content.Context.MODE_PRIVATE).getString("azan_custom_path_$prayerKey", null)
                val target = when {
                    customPath != null && java.io.File(customPath).exists() && java.io.File(customPath).name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> java.io.File(customPath)
                    customFile.exists() -> customFile
                    externalFile.exists() && externalFile.name.lowercase() !in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES -> externalFile
                    else -> null
                }
                if (externalFile.exists() && externalFile.name.lowercase() in com.example.util.SinggharaFolderManager.DISALLOWED_MP3_FILES) {
                    try { externalFile.delete() } catch (ignored: Exception) {}
                }
                if (target != null && target.exists()) {
                    android.media.MediaPlayer().apply {
                        setDataSource(target.absolutePath)
                        prepare()
                    }
                } else {
                    popupToastMessage = "Belum ada file kustom. Silakan pilih file terlebih dahulu."
                    return
                }
            } else {
                var defaultFile = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayerName)
                if (defaultFile == null || !defaultFile.exists()) {
                    com.example.util.SinggharaFolderManager.seedDefaultMp3sFromAssets(context)
                    defaultFile = com.example.util.SinggharaFolderManager.getDefaultAzanFile(context, prayerName)
                }
                if (defaultFile != null && defaultFile.exists()) {
                    android.media.MediaPlayer().apply {
                        setDataSource(defaultFile.absolutePath)
                        prepare()
                    }
                } else {
                    try {
                        val assetName = if (prayerName.contains("subuh", true) || prayerName.contains("fajr", true)) {
                            "mp3/fajr_128_44.mp3"
                        } else {
                            "mp3/adzan_turkey-1.mp3"
                        }
                        val afd = context.assets.openFd(assetName)
                        android.media.MediaPlayer().apply {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            prepare()
                        }
                    } catch (e: Exception) {
                        popupToastMessage = "Audio adzan sedang disiapkan..."
                        null
                    }
                }
            }

            if (mp != null) {
                mp.start()
                previewMediaPlayer = mp
                previewPlayingMode = mode
                mp.setOnCompletionListener {
                    previewPlayingMode = null
                    previewMediaPlayer = null
                    try {
                        mp.release()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error releasing media player: ${e.message}")
                    }
                }
            } else {
                popupToastMessage = "Gagal membuat pemutar suara default"
            }
        } catch (e: Exception) {
            popupToastMessage = "Gagal memutar uji coba: ${e.message}"
        }
    }

    // local state form
    var popupAgendaTitle by remember { mutableStateOf("") }
    var popupAgendaDesc by remember { mutableStateOf("") }
    var popupAgendaType by remember { mutableStateOf("agenda") }

    // Dialog States
    var showAllAppsDialog by remember { mutableStateOf(false) }

    // Compute Javanese and Hijri for today (memoized to prevent re-computation on scroll)
    val todayCal = remember { Calendar.getInstance() }
    val currentJawa = remember(todayCal) {
        JavaneseCalendarHelper.calculateJawa(
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )
    }
    val resolvedHijri = remember(todayCal, liveHijri, viewModel.hijriOffset) {
        if (liveHijri != null) {
            liveHijri!!
        } else {
            HijriCalendarHelper.getHijriDate(
                todayCal.get(Calendar.YEAR),
                todayCal.get(Calendar.MONTH) + 1,
                todayCal.get(Calendar.DAY_OF_MONTH),
                viewModel.hijriOffset
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("home_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // SECTION 1: HEADER & PROFILE GREETING
        item {
            val calendarNow = Calendar.getInstance()
            val hour = calendarNow.get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..10 -> "Selamat Pagi"
                in 11..14 -> "Selamat Siang"
                in 15..17 -> "Selamat Sore"
                else -> "Selamat Malam"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = greeting,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = viewModel.profileName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        // SECTION 2: PREMIUM DIGITAL CLOCK & CALENDAR CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Real-time digital clock display (clickable to change font, long click to resize)
                                        val timeParts = currentTime.split(":")
                    val clockFontSize = viewModel.clockFontSizeSp.sp

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .padding(if (isClockResizing) 16.dp else 4.dp)
                                    .then(
                                        if (isClockResizing) {
                                            Modifier.border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (!isClockResizing) {
                                                showClockFontDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            isClockResizing = !isClockResizing
                                            if (isClockResizing) {
                                                viewModel.showToastPopup("Mode Ubah Ukuran Jam Aktif. Ketuk atau geser titik untuk mengatur ukuran.")
                                            }
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("digital_clock_display"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (timeParts.size >= 2) {
                                    val hours = timeParts[0]
                                    val minutes = timeParts[1]
                                    val seconds = if (timeParts.size >= 3) timeParts[2].toIntOrNull() ?: 0 else 0
                                    val isColonVisible = seconds % 2 == 0
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = hours,
                                            fontFamily = clockFontFamily,
                                            fontSize = clockFontSize,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = ":",
                                            fontFamily = clockFontFamily,
                                            fontSize = clockFontSize,
                                            color = if (isColonVisible) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = minutes,
                                            fontFamily = clockFontFamily,
                                            fontSize = clockFontSize,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = currentTime,
                                        fontFamily = clockFontFamily,
                                        fontSize = clockFontSize,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = (-1).sp
                                    )
                                }

                                if (isClockResizing) {
                                    // Top Dot (Atas)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-14).dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val delta = -dragAmount.y / 4f
                                                    if (delta != 0f) viewModel.saveClockFontSize(viewModel.clockFontSizeSp + delta)
                                                }
                                            }
                                            .clickable { viewModel.saveClockFontSize(viewModel.clockFontSizeSp + 3f) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Perbesar Atas",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Bottom Dot (Bawah)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 14.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val delta = dragAmount.y / 4f
                                                    if (delta != 0f) viewModel.saveClockFontSize(viewModel.clockFontSizeSp - delta)
                                                }
                                            }
                                            .clickable { viewModel.saveClockFontSize(viewModel.clockFontSizeSp - 3f) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Perkecil Bawah",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Left Dot (Kiri)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = (-14).dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val delta = -dragAmount.x / 4f
                                                    if (delta != 0f) viewModel.saveClockFontSize(viewModel.clockFontSizeSp + delta)
                                                }
                                            }
                                            .clickable { viewModel.saveClockFontSize(viewModel.clockFontSizeSp - 3f) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Perkecil Kiri",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Right Dot (Kanan)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = 14.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .pointerInput(Unit) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val delta = dragAmount.x / 4f
                                                    if (delta != 0f) viewModel.saveClockFontSize(viewModel.clockFontSizeSp + delta)
                                                }
                                            }
                                            .clickable { viewModel.saveClockFontSize(viewModel.clockFontSizeSp + 3f) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Perbesar Kanan",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            if (isClockResizing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Ukuran: ${viewModel.clockFontSizeSp.toInt()} sp",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.saveClockFontSize(52f) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Reset (52)", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { isClockResizing = false },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Selesai", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Timezone indicator moved BELOW digital clock (no clock icon)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showTimezoneMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (viewModel.activeTimeZone) {
                                    "WIB" -> "WIB (UTC+7)"
                                    "WITA" -> "WITA (UTC+8)"
                                    "WIT" -> "WIT (UTC+9)"
                                    else -> viewModel.activeTimeZone
                                },
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Ganti",
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // POPUP DIALOG: UBAH FONT JAM DIGITAL
                    if (showClockFontDialog) {
                        AlertDialog(
                            onDismissRequest = { showClockFontDialog = false },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = "Ubah Font Jam Digital",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Center
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Pilih dari Folder My Singghara/Data/font/ (Google Drive):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    val availableFonts = remember(showClockFontDialog) {
                                        com.example.util.SinggharaFolderManager.getAvailableFontFiles()
                                    }

                                    if (availableFonts.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            availableFonts.forEach { fontFile ->
                                                val isSelected = viewModel.customClockFontPath == fontFile.absolutePath ||
                                                        viewModel.customClockFontName == fontFile.name

                                                val itemFontFamily = remember(fontFile.absolutePath) {
                                                    try {
                                                        val tf = android.graphics.Typeface.createFromFile(fontFile)
                                                        FontFamily(Typeface(tf))
                                                    } catch (e: Exception) {
                                                        FontFamily.Default
                                                    }
                                                }

                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            viewModel.saveCustomClockFont(fontFile.absolutePath, fontFile.name)
                                                            showClockFontDialog = false
                                                            viewModel.showToastPopup("Font diubah ke: ${fontFile.name}")
                                                        },
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = fontFile.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            )
                                                            Text(
                                                                text = "12:34:56 • Subuh 04:30",
                                                                fontFamily = itemFontFamily,
                                                                fontSize = 14.sp,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Terpilih",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            showClockFontDialog = false
                                            viewModel.preventAutoLock = true
                                            fontPickerLauncher.launch("*/*")
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("pick_clock_font_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pilih File Font Lain (.ttf / .otf)")
                                    }

                                    if (viewModel.customClockFontPath.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.resetCustomClockFont()
                                                showClockFontDialog = false
                                                viewModel.showToastPopup("Font jam dikembalikan ke default.")
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("reset_clock_font_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Gunakan Font Default")
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(
                                    onClick = { showClockFontDialog = false }
                                ) {
                                    Text("Batal")
                                }
                            }
                        )
                    }

                    // Interactive Pop Dialog (AlertDialog) for Indonesian Timezone selection
                    if (showTimezoneMenu) {
                        AlertDialog(
                            onDismissRequest = { showTimezoneMenu = false },
                            title = {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("🇮🇩", fontSize = 20.sp)
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "Pilih Zona Waktu",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )
                                        }
                                    }
                                }
                            },
                            text = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    data class TzOption(
                                        val code: String,
                                        val fullName: String,
                                        val offset: String,
                                        val regions: String
                                    )

                                    val options = listOf(
                                        TzOption(
                                            code = "WIB",
                                            fullName = "Waktu Indonesia Barat",
                                            offset = "UTC+7",
                                            regions = "Sumatra, Jawa, Kalbar, Kalteng"
                                        ),
                                        TzOption(
                                            code = "WITA",
                                            fullName = "Waktu Indonesia Tengah",
                                            offset = "UTC+8",
                                            regions = "Sulawesi, Bali, NTB, NTT, Kalsel, Kaltim, Kaltara"
                                        ),
                                        TzOption(
                                            code = "WIT",
                                            fullName = "Waktu Indonesia Timur",
                                            offset = "UTC+9",
                                            regions = "Maluku, Maluku Utara, Seluruh Papua"
                                        )
                                    )

                                    options.forEach { option ->
                                        val isSelected = viewModel.activeTimeZone == option.code
                                        Card(
                                            onClick = {
                                                viewModel.activeTimeZone = option.code
                                                showTimezoneMenu = false
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected)
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = {
                                                        viewModel.activeTimeZone = option.code
                                                        showTimezoneMenu = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = option.code,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 16.sp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                                        ) {
                                                            Text(
                                                                text = option.offset,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = option.fullName,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Wilayah: ${option.regions}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { showTimezoneMenu = false }
                                ) {
                                    Text("Batal", fontWeight = FontWeight.Bold)
                                }
                            },
                            shape = RoundedCornerShape(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$currentDay, $currentDate",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentJawa.dina} ${currentJawa.pasaran}  ·  $resolvedHijri",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val isApi = liveHijri != null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isApi) MaterialTheme.colorScheme.tertiaryContainer 
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isApi) "Aladhan API Aktif" else "Kalkulasi Lokal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isApi) MaterialTheme.colorScheme.onTertiaryContainer 
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
        // SECTION 2B: MY APLIKASI
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_aplikasi_card"),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Title Row: "My Aplikasi" with clean Chevron-Right button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.showAllAppsPage = true }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Aplikasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { viewModel.showAllAppsPage = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Lihat Semua Aplikasi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Minimalist Aesthetic Horizontal List of 4 Most Recent Applications
                    val recent4AppIds = viewModel.recentAppIds.take(4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        recent4AppIds.forEach { appId ->
                            val appMeta = AppRegistry.allAppsMap[appId]
                            if (appMeta != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (appMeta.route != null) {
                                                viewModel.currentRoute = appMeta.route
                                            } else if (appMeta.gameSection != null) {
                                                activeGameSection = appMeta.gameSection
                                                viewModel.recordAppOpen(appMeta.gameSection)
                                            }
                                        }
                                        .testTag("app_$appId")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(appMeta.color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = appMeta.icon,
                                            contentDescription = appMeta.title,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = appMeta.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }

        // SECTION 3: WEATHER FORECAST (PERKIRAAN CUACA)
        item {
            val state = weatherState
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_forecast_card"),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row: Icon, Title, and Refresh button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterDrama,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Perkiraan Cuaca",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (viewModel.userLatitude != null) "GPS: ${viewModel.userLocationName ?: "Koordinat Aktif"}" else "Kota Jakarta (Standard)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.fetchWeather() },
                            modifier = Modifier.size(32.dp).testTag("weather_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Perbarui Cuaca",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (state) {
                        is MainViewModel.WeatherUIState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        is MainViewModel.WeatherUIState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = state.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.fetchWeather() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Coba Lagi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is MainViewModel.WeatherUIState.Success -> {
                            val response = state.response
                            val temp = response.main?.temp ?: 0.0
                            val feelsLike = response.main?.feels_like ?: 0.0
                            val humidity = response.main?.humidity ?: 0.0
                            val pressure = response.main?.pressure ?: 0.0
                            val windSpeed = response.wind?.speed ?: 0.0
                            val cityName = response.name ?: "Unknown"
                            val weatherDesc = response.weather?.firstOrNull()?.description?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "Unknown"
                            val weatherId = response.weather?.firstOrNull()?.id ?: 800

                            // Map weather code to corresponding Material Icon (no emojis!)
                            val weatherIcon = when {
                                weatherId in 200..232 -> Icons.Default.FlashOn
                                weatherId in 300..321 -> Icons.Default.CloudQueue
                                weatherId in 500..531 -> Icons.Default.Grain
                                weatherId in 600..622 -> Icons.Default.AcUnit
                                weatherId in 701..781 -> Icons.Default.Dehaze
                                weatherId == 800 -> Icons.Default.WbSunny
                                weatherId in 801..804 -> Icons.Default.Cloud
                                else -> Icons.Default.WbSunny
                            }

                            val weatherColor = when {
                                weatherId == 800 -> Color(0xFFFFB300)
                                weatherId in 801..804 -> Color(0xFF78909C)
                                weatherId in 500..531 -> Color(0xFF1E88E5)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Temperature & General description
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(weatherColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = weatherIcon,
                                            contentDescription = null,
                                            tint = weatherColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.1f°C", temp),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = weatherDesc,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // City & feels like
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = cityName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = String.format(Locale.getDefault(), "Terasa spt %.1f°C", feelsLike),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Spacer(modifier = Modifier.height(12.dp))

                            // Three detailed cards (no emojis!)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Humidity Box
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Opacity,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Kelembaban",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.0f%%", humidity),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Wind Box
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Air,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Kecepatan Angin",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.1f m/s", windSpeed),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                // Pressure Box
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Tekanan Udara",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(Locale.getDefault(), "%.0f hPa", pressure),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 4: JADWAL SHOLAT
        item {
            val schedule = viewModel.kemenagPrayerSchedule ?: viewModel.prayerSchedule
            val parsedCurrentTime = currentTime.split(":")
            val currentMinutes = if (parsedCurrentTime.size >= 2) {
                val h = parsedCurrentTime[0].toIntOrNull() ?: 0
                val m = parsedCurrentTime[1].toIntOrNull() ?: 0
                h * 60 + m
            } else {
                0
            }

            fun parseToMinutes(timeStr: String): Int {
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    return h * 60 + m
                }
                return 0
            }

            val nextPrayerName = remember(schedule, currentMinutes) {
                val items = listOf(
                    "Imsak" to parseToMinutes(schedule.imsak),
                    "Subuh" to parseToMinutes(schedule.subuh),
                    "Dzuhur" to parseToMinutes(schedule.dzuhur),
                    "Ashar" to parseToMinutes(schedule.ashar),
                    "Maghrib" to parseToMinutes(schedule.maghrib),
                    "Isya" to parseToMinutes(schedule.isya)
                )
                val next = items.firstOrNull { it.second > currentMinutes }
                next?.first ?: "Imsak" // fallback to Imsak (of next day) if all passed
            }

            val triggerLocationRefresh = {
                val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    viewModel.fetchCurrentLocation(context)
                    val today = java.util.Calendar.getInstance()
                    val formatStr = String.format("%02d-%02d-%04d", today.get(java.util.Calendar.DAY_OF_MONTH), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.YEAR))
                    viewModel.loadLiveAladhanHijri(formatStr)
                    popupToastMessage = "Memperbarui lokasi GPS & jadwal sholat..."
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prayer_schedule_card")
                    .clickable { triggerLocationRefresh() },
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val lat = viewModel.userLatitude
                    val lon = viewModel.userLongitude
                    val locText = if (lat != null && lon != null) {
                        val locName = viewModel.userLocationName ?: "Koordinat GPS Aktif"
                        "$locName (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)})"
                    } else {
                        "Lokasi Default (WIB / WITA / WIT)"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1.1f), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showPrayerSettingsDialog = true },
                                modifier = Modifier.size(28.dp).testTag("prayer_settings_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Pengaturan Azan",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = "Waktu Solat",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 4.dp, end = 4.dp, top = 2.dp)
                                )
                                Text(
                                    text = if (viewModel.kemenagPrayerSchedule != null) "Kemenag RI (Bimas Islam)" else "Kalkulasi Astronomis",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Lokasi GPS",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Lokasi Sinkronisasi",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = locText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (fineGranted || coarseGranted) {
                                        viewModel.fetchCurrentLocation(context)
                                        val today = java.util.Calendar.getInstance()
                                        val formatStr = String.format("%02d-%02d-%04d", today.get(java.util.Calendar.DAY_OF_MONTH), today.get(java.util.Calendar.MONTH) + 1, today.get(java.util.Calendar.YEAR))
                                        viewModel.loadLiveAladhanHijri(formatStr)
                                        popupToastMessage = "Memperbarui lokasi GPS & jadwal sholat..."
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Perbarui Lokasi dan Jadwal Sholat",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val items = listOf(
                        Triple("Imsak", schedule.imsak, Icons.Default.AccessTime),
                        Triple("Subuh", schedule.subuh, Icons.Default.Brightness4),
                        Triple("Dzuhur", schedule.dzuhur, Icons.Default.WbSunny),
                        Triple("Ashar", schedule.ashar, Icons.Default.Brightness5),
                        Triple("Maghrib", schedule.maghrib, Icons.Default.Brightness6),
                        Triple("Isya", schedule.isya, Icons.Default.NightsStay)
                    )

                    // Single Row containing all 6 items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items.forEach { (name, time, icon) ->
                            val isNext = nextPrayerName == name
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isNext) MaterialTheme.colorScheme.primaryContainer 
                                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        color = if (isNext) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = time,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    val mode = viewModel.prayerSoundSettings[name] ?: (if (name == "Imsak") 1 else 2)
                                    val modeIcon = when (mode) {
                                        0 -> Icons.Default.VolumeOff
                                        1 -> Icons.Default.Notifications
                                        else -> Icons.Default.VolumeUp
                                    }
                                    Icon(
                                        imageVector = modeIcon,
                                        contentDescription = null,
                                        tint = if (isNext) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(10.dp).padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 5: INTERACTIVE CALENDAR WITH JAVANESE AND HIJRI LABELS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Navigation calendar Month
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val newCal = viewCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            viewCalendar = newCal
                        }) {
                            Icon(Icons.Default.ChevronLeft, "Bulan Sebelumnya", tint = MaterialTheme.colorScheme.primary)
                        }

                        val calendarMonthName = viewCalendar.getDisplayName(
                            Calendar.MONTH, Calendar.LONG, Locale("id", "ID")
                        ) ?: ""
                        val calendarYear = viewCalendar.get(Calendar.YEAR)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showMonthEventsPopup = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("calendar_month_year_header")
                        ) {
                            Text(
                                text = "$calendarMonthName $calendarYear",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = {
                            val newCal = viewCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, 1)
                            viewCalendar = newCal
                        }) {
                            Icon(Icons.Default.ChevronRight, "Bulan Berikutnya", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Calendar Table headers (Days)
                    val daysHeader = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        daysHeader.forEach { d ->
                            Text(
                                text = d,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (d == "Min") Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Compute Calendar days (memoized to prevent frame drops during scrolling)
                    val calendarGridRows = remember(viewCalendar, agendaItems, apiHolidays, todayCal) {
                        val tempCal = viewCalendar.clone() as Calendar
                        tempCal.set(Calendar.DAY_OF_MONTH, 1)
                        val firstDayDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
                        val totalDaysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

                        val todayYear = todayCal.get(Calendar.YEAR)
                        val todayMonth = todayCal.get(Calendar.MONTH) + 1
                        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

                        val cellList = mutableListOf<CalendarCellData?>()
                        for (i in 0 until firstDayDayOfWeek) {
                            cellList.add(null)
                        }

                        for (day in 1..totalDaysInMonth) {
                            val targetCal = (viewCalendar.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            val mYear = targetCal.get(Calendar.YEAR)
                            val mMonth = targetCal.get(Calendar.MONTH) + 1
                            val mDay = targetCal.get(Calendar.DAY_OF_MONTH)
                            val dateKey = String.format("%04d-%02d-%02d", mYear, mMonth, mDay)

                            val jDate = JavaneseCalendarHelper.calculateJawa(mYear, mMonth, mDay)
                            val isToday = mYear == todayYear && mMonth == todayMonth && mDay == todayDay
                            val holidayName = apiHolidays[dateKey]
                            val observanceName = HolidayClient.getObservanceForDate(mMonth, mDay)
                            val hasAgenda = agendaItems.any { it.tanggal == dateKey }
                            val dayOfWeekIdx = (firstDayDayOfWeek + day - 1) % 7

                            cellList.add(
                                CalendarCellData(
                                    dayNumber = day,
                                    targetCal = targetCal,
                                    pasaran = jDate.pasaran,
                                    isToday = isToday,
                                    holidayName = holidayName,
                                    observanceName = observanceName,
                                    hasAgenda = hasAgenda,
                                    isSunday = (dayOfWeekIdx == 0)
                                )
                            )
                        }
                        cellList.chunked(7)
                    }

                    val isDarkTheme = isSystemInDarkTheme()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        calendarGridRows.forEach { weekRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (i in 0..6) {
                                    val cell = weekRow.getOrNull(i)
                                    if (cell == null) {
                                        Box(modifier = Modifier.weight(1f))
                                    } else {
                                        val cellBorderColor = when {
                                            cell.isToday -> MaterialTheme.colorScheme.primary
                                            cell.holidayName != null -> Color(0xFFEF4444).copy(alpha = if (isDarkTheme) 0.55f else 0.35f)
                                            cell.observanceName != null -> Color(0xFF10B981).copy(alpha = if (isDarkTheme) 0.55f else 0.35f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.3f else 0.55f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1.0f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(
                                                    width = if (cell.isToday) 1.5.dp else 1.dp,
                                                    color = cellBorderColor,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .background(
                                                    when {
                                                        cell.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.45f else 0.35f)
                                                        cell.holidayName != null -> Color(0xFFEF4444).copy(alpha = 0.08f)
                                                        cell.observanceName != null -> Color(0xFF10B981).copy(alpha = 0.08f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .clickable { selectedDayAgenda = cell.targetCal }
                                                .padding(2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = cell.dayNumber.toString(),
                                                    fontSize = 13.sp,
                                                    fontWeight = if (cell.isToday) FontWeight.Black else FontWeight.Bold,
                                                    color = when {
                                                        cell.holidayName != null -> Color(0xFFEF4444)
                                                        cell.isSunday -> Color(0xFFEF4444)
                                                        cell.observanceName != null -> Color(0xFF10B981) // Teks hijau hari peringatan
                                                        cell.isToday -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = cell.pasaran,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = when {
                                                        cell.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                        cell.holidayName != null -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                                        cell.observanceName != null -> Color(0xFF10B981).copy(alpha = 0.7f)
                                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                    }
                                                )
                                                if (cell.hasAgenda || cell.holidayName != null || cell.observanceName != null) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (cell.holidayName != null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFFEF4444))
                                                            )
                                                        }
                                                        if (cell.observanceName != null) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF10B981))
                                                            )
                                                        }
                                                        if (cell.hasAgenda) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.secondary)
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
                }
            }
        }
    }

    // Toast Pop-Up Dialog using SpringToastPopup
    if (popupToastMessage != null) {
        com.example.ui.components.SpringToastPopup(
            message = popupToastMessage,
            onDismiss = { popupToastMessage = null }
        )
    }

    // Floating Animated Feedback Banner / Toast using SpringToastPopup
    if (feedbackBannerMessage != null) {
        com.example.ui.components.SpringToastPopup(
            message = feedbackBannerMessage,
            onDismiss = { feedbackBannerMessage = null }
        )
    }
    }

    // DIALOG: DETAIL ACARA KALENDER POPUP (Redesigned with Material 3 & Zero Emojis)
    selectedDayAgenda?.let { cal ->
        val dayDetail = remember(cal, agendaItems, apiHolidays, viewModel.hijriOffset) {
            val mYear = cal.get(Calendar.YEAR)
            val mMonth = cal.get(Calendar.MONTH) + 1
            val mDay = cal.get(Calendar.DAY_OF_MONTH)
            val dateKey = String.format("%04d-%02d-%02d", mYear, mMonth, mDay)
            DayAgendaDetail(
                year = mYear,
                month = mMonth,
                day = mDay,
                dateKey = dateKey,
                jDate = JavaneseCalendarHelper.calculateJawa(mYear, mMonth, mDay),
                hDate = HijriCalendarHelper.getHijriDate(mYear, mMonth, mDay, viewModel.hijriOffset),
                holiday = apiHolidays[dateKey],
                observance = HolidayClient.getObservanceForDate(mMonth, mDay),
                dayAgendas = agendaItems.filter { it.tanggal == dateKey }
            )
        }

        Dialog(onDismissRequest = { selectedDayAgenda = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "${dayDetail.day} ${viewCalendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale("id", "ID"))} ${dayDetail.year}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Kalender Jawa: ${dayDetail.jDate.dina} ${dayDetail.jDate.pasaran}, Wuku ${dayDetail.jDate.wuku}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Kalender Hijriah: ${dayDetail.hDate}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    if (dayDetail.holiday == null && dayDetail.observance == null && dayDetail.dayAgendas.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Tidak ada hari libur, peringatan, atau kegiatan pada tanggal ini.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        dayDetail.holiday?.let {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.06f)),
                                modifier = Modifier.padding(bottom = 10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = "Libur",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Libur Resmi: $it",
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        dayDetail.observance?.let {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.06f)),
                                modifier = Modifier.padding(bottom = 10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Hari Peringatan",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Hari Peringatan: $it",
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        dayDetail.dayAgendas.forEach { agenda ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Agenda",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                agenda.judul,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (agenda.detail.isNotEmpty()) {
                                                Text(
                                                    agenda.detail,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { agendaToDelete = agenda },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { selectedDayAgenda = null },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tutup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSafWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSafWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Penyimpanan SAF Diperlukan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text("Untuk menambahkan agenda kegiatan atau mengelola acara lokal kustom Anda, " +
                     "Anda harus mengaktifkan/memilih Folder Penyimpanan utama (SAF) terlebih dahulu di tab Profil " +
                     "agar seluruh data dicadangkan dengan aman.")
            },
            confirmButton = {
                Button(onClick = {
                    showSafWarningDialog = false
                    viewModel.currentRoute = "profil"
                }) {
                    Text("Ke Pengaturan Profil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafWarningDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG: MONTH EVENT & CALENDAR DOWNLOAD POPUP (With + Icon and PDF/PNG/JPG Art Paper/Art Carton Export)
    if (showMonthEventsPopup) {
        val calendarMonthName = viewCalendar.getDisplayName(
            Calendar.MONTH, Calendar.LONG, Locale("id", "ID")
        ) ?: ""
        val calendarYear = viewCalendar.get(Calendar.YEAR)
        val currentMonthNumber = viewCalendar.get(Calendar.MONTH) + 1
        val totalDaysInCurrentMonth = viewCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val activeMonthAgendas = agendaItems.filter {
            val dateParts = it.tanggal.split("-")
            if (dateParts.size == 3) {
                dateParts[0].toInt() == calendarYear && dateParts[1].toInt() == currentMonthNumber
            } else false
        }

        val activeMonthHolidays = apiHolidays.filter { (key, _) ->
            val dateParts = key.split("-")
            if (dateParts.size == 3) {
                dateParts[0].toInt() == calendarYear && dateParts[1].toInt() == currentMonthNumber
            } else false
        }.toList()

        val activeMonthObservances = (1..totalDaysInCurrentMonth).mapNotNull { d ->
            val obs = HolidayClient.getObservanceForDate(currentMonthNumber, d)
            if (obs != null) Pair(d, obs) else null
        }

        val context = LocalContext.current

        Dialog(onDismissRequest = {
            showMonthEventsPopup = false
            showMonthAddAgendaForm = false
        }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("month_events_popup")
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    // Header with Month Title and '+' Icon Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kalender $calendarMonthName $calendarYear",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Acara, Hari Libur & Unduh Kalender",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // '+' Icon button (without title text) as requested
                        IconButton(
                            onClick = {
                                if (viewModel.safFolderUriStr.isEmpty()) {
                                    showSafWarningDialog = true
                                } else {
                                    showMonthAddAgendaForm = !showMonthAddAgendaForm
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .size(36.dp)
                                .testTag("month_add_agenda_icon_button")
                        ) {
                            Icon(
                                imageVector = if (showMonthAddAgendaForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = "Tambah Agenda",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Expandable Form Tambah Kegiatan
                    AnimatedVisibility(
                        visible = showMonthAddAgendaForm,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Tambah Kegiatan Bulan $calendarMonthName",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Day Selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tanggal:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedTextField(
                                    value = monthAgendaSelectedDay.toString(),
                                    onValueChange = { str ->
                                        val v = str.filter { it.isDigit() }.toIntOrNull()
                                        if (v != null && v in 1..totalDaysInCurrentMonth) {
                                            monthAgendaSelectedDay = v
                                        } else if (str.isEmpty()) {
                                            monthAgendaSelectedDay = 1
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(80.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Text(
                                    text = "$calendarMonthName $calendarYear",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = monthAgendaTitle,
                                onValueChange = { monthAgendaTitle = it },
                                label = { Text("Nama kegiatan") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = monthAgendaDesc,
                                onValueChange = { monthAgendaDesc = it },
                                label = { Text("Detail kegiatan (opsional)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    showMonthAddAgendaForm = false
                                    monthAgendaTitle = ""
                                    monthAgendaDesc = ""
                                }) {
                                    Text("Batal")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (monthAgendaTitle.isNotBlank()) {
                                            val dateKey = String.format("%04d-%02d-%02d", calendarYear, currentMonthNumber, monthAgendaSelectedDay)
                                            val savedTitle = monthAgendaTitle
                                            viewModel.addAgendaItem(
                                                AgendaItem(
                                                    tanggal = dateKey,
                                                    judul = monthAgendaTitle,
                                                    detail = monthAgendaDesc,
                                                    tipe = monthAgendaType
                                                )
                                            )
                                            popupToastMessage = "Kegiatan \"$savedTitle\" berhasil disimpan!"
                                            feedbackBannerMessage = "Kegiatan \"$savedTitle\" berhasil disimpan!"
                                            feedbackBannerIcon = Icons.Default.CheckCircle
                                            feedbackBannerColor = Color(0xFF10B981)
                                            monthAgendaTitle = ""
                                            monthAgendaDesc = ""
                                            showMonthAddAgendaForm = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Simpan")
                                }
                            }
                        }
                    }

                    // Section: Unduh Kalender (12 Bulan / 1 Bulan + PDF / PNG / JPG + Art Paper / Art Carton)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Unduh Kalender",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Unduh & Cetak Kalender",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Periode Cetak (12 Bulan / 1 Bulan)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Periode Cetak:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(
                                        Pair("12_MONTHS", "12 Bulan (1 Tahun)"),
                                        Pair("1_MONTH", "1 Bulan ($calendarMonthName)")
                                    ).forEach { (scopeKey, label) ->
                                        val isSelected = selectedExportScope == scopeKey
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { selectedExportScope = scopeKey }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Format Options (PDF, PNG, JPG)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Format:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                listOf("PDF", "PNG", "JPG").forEach { fmt ->
                                    val isSelected = selectedExportFormat == fmt
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedExportFormat = fmt }
                                    ) {
                                        Text(
                                            text = fmt,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Paper Specifications (Art Paper / Art Carton / Kartu ATM)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Bahan & Bentuk Cetak:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf("Art Paper (120–150 gsm)", "Art Carton (190–260 gsm)").forEach { paper ->
                                            val isSelected = selectedPaperType == paper
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { selectedPaperType = paper }
                                            ) {
                                                Text(
                                                    text = paper,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Kartu ATM / IC Card (Ukuran Dompet) Option
                                    val isAtmCard = selectedPaperType.contains("ATM", true) || selectedPaperType.contains("Dompet", true)
                                    val atmPaperValue = "Kartu ATM / IC Card (Ukuran Dompet 85.6 x 54 mm)"
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAtmCard) Color(0xFF0F766E).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isAtmCard) Color(0xFF0F766E) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedPaperType = atmPaperValue }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmark,
                                                contentDescription = null,
                                                tint = if (isAtmCard) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "💳 Kartu ATM / IC Card (Ukuran Dompet 85.6 x 54 mm)",
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isAtmCard) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isAtmCard) Color(0xFF0F766E) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Format description badge
                            val isAtmSelected = selectedPaperType.contains("ATM", true) || selectedPaperType.contains("Dompet", true)
                            val scopeDesc = when {
                                isAtmSelected && selectedExportScope == "12_MONTHS" ->
                                    "Format Kartu ATM Dompet (85.6 x 54 mm) • Muka Depan (Semester I) & Belakang (Semester II)"
                                isAtmSelected ->
                                    "Format Kartu ATM Dompet (85.6 x 54 mm) • Bulan $calendarMonthName $calendarYear Lengkap Chip IC"
                                selectedExportScope == "12_MONTHS" && selectedExportFormat.equals("PDF", true) ->
                                    "12 Halaman Dokumen A4 (Januari – Desember) Lengkap"
                                selectedExportScope == "12_MONTHS" ->
                                    "Poster Dinding 12 Bulan Resolusi Tinggi (300 DPI)"
                                else ->
                                    "1 Lembar Kalender $calendarMonthName $calendarYear"
                            }
                            Text(
                                text = "Keterangan: $scopeDesc",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (!isExportingCalendar) {
                                        isExportingCalendar = true
                                        coroutineScope.launch {
                                            val result = CalendarExportHelper.exportCalendar(
                                                context = context,
                                                year = calendarYear,
                                                month = currentMonthNumber,
                                                hijriOffset = viewModel.hijriOffset,
                                                holidays = apiHolidays,
                                                agendas = agendaItems,
                                                format = selectedExportFormat,
                                                paperType = selectedPaperType,
                                                scope = selectedExportScope
                                            )
                                            isExportingCalendar = false
                                            result.onSuccess { exportedFile ->
                                                val mime = if (selectedExportFormat.equals("PDF", true)) "application/pdf"
                                                else if (selectedExportFormat.equals("PNG", true)) "image/png" else "image/jpeg"
                                                val scopeLabel = if (selectedExportScope == "12_MONTHS") "12 Bulan $calendarYear" else "$calendarMonthName $calendarYear"
                                                CalendarExportHelper.shareCalendarFile(
                                                    context = context,
                                                    file = exportedFile,
                                                    mimeType = mime,
                                                    title = "Unduh Kalender $scopeLabel"
                                                )
                                                popupToastMessage = "Kalender $scopeLabel ($selectedExportFormat) berhasil dibuat!"
                                            }.onFailure { err ->
                                                popupToastMessage = "Gagal membuat kalender: ${err.message}"
                                            }
                                        }
                                    }
                                },
                                enabled = !isExportingCalendar,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isExportingCalendar) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Memproses...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val btnLabel = if (selectedExportScope == "12_MONTHS") "Unduh Kalender 12 Bulan ($selectedExportFormat)" else "Unduh Kalender 1 Bulan ($selectedExportFormat)"
                                    Text(btnLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Content: Holidays, Observances, and Agendas list
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                    ) {
                        if (activeMonthAgendas.isEmpty() && activeMonthHolidays.isEmpty() && activeMonthObservances.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tidak ada acara atau hari besar di bulan ini.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(activeMonthHolidays) { (date, name) ->
                                    val dateParts = date.split("-")
                                    val d = dateParts.lastOrNull()?.toIntOrNull() ?: 1
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.05f)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.15f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = d.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFEF4444),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bookmark,
                                                        contentDescription = null,
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFEF4444)
                                                    )
                                                }
                                                Text(
                                                    text = "Hari Libur Nasional (SKB 3 Menteri)",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFEF4444).copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }

                                items(activeMonthObservances) { (d, name) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.05f)),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.15f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = d.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981)
                                                    )
                                                }
                                                Text(
                                                    text = "Hari Peringatan / Hari Besar",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF10B981).copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }

                                items(activeMonthAgendas) { agenda ->
                                    val dateParts = agenda.tanggal.split("-")
                                    val d = dateParts.lastOrNull()?.toIntOrNull() ?: 1
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = d.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.PushPin,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = agenda.judul,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (agenda.detail.isNotEmpty()) {
                                                    Text(
                                                        text = agenda.detail,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { agendaToDelete = agenda },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Hapus",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showMonthEventsPopup = false
                            showMonthAddAgendaForm = false
                        }) {
                            Text("Tutup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DIALOG: KONFIRMASI HAPUS AGENDA
    agendaToDelete?.let { agenda ->
        AlertDialog(
            onDismissRequest = { agendaToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Hapus Agenda?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus agenda \"" + agenda.judul + "\"? Tindakan ini tidak dapat dibatalkan.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                val errColor = MaterialTheme.colorScheme.error
                Button(
                    onClick = {
                        val deletedTitle = agenda.judul
                        viewModel.deleteAgendaItem(agenda)
                        agendaToDelete = null
                        popupToastMessage = "Agenda \"$deletedTitle\" berhasil dihapus."
                        feedbackBannerMessage = "Agenda \"$deletedTitle\" berhasil dihapus."
                        feedbackBannerIcon = Icons.Default.Delete
                        feedbackBannerColor = errColor
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = errColor,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { agendaToDelete = null }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    if (showPrayerSettingsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPrayerSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Pengaturan Pengingat Sholat")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pilih waktu sholat di bawah untuk mengubah tipe pengingat:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val prayerNames = listOf("Imsak", "Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya")
                    
                    prayerNames.forEach { prayer ->
                        val currentMode = viewModel.prayerSoundSettings[prayer] ?: (if (prayer == "Imsak") 1 else 2)
                        
                        val modeText = when (currentMode) {
                            0 -> "Senyap"
                            1 -> "Suara Notifikasi"
                            2 -> "Azan (Default Aplikasi)"
                            3 -> "Azan (MP3 Kustom)"
                            else -> "Azan"
                        }

                        val modeIcon = when (currentMode) {
                            0 -> Icons.Default.VolumeOff
                            1 -> Icons.Default.Notifications
                            else -> Icons.Default.VolumeUp
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .clickable {
                                    selectedPrayerForSoundSettings = prayer
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = prayer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = modeText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPrayerSettingsDialog = false }
                ) {
                    Text("Tutup")
                }
            }
        )
    }

    if (selectedPrayerForSoundSettings != null) {
        val prayer = selectedPrayerForSoundSettings!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                previewMediaPlayer?.let {
                    if (it.isPlaying) { it.stop() }
                    it.release()
                }
                previewMediaPlayer = null
                previewPlayingMode = null
                selectedPrayerForSoundSettings = null
            },
            title = { Text(text = "Pengingat Sholat $prayer") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Senyap
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.savePrayerSoundSetting(prayer, 0)
                                com.example.NotificationScheduler.scheduleAlarms(context)
                                previewMediaPlayer?.let {
                                    if (it.isPlaying) { it.stop() }
                                    it.release()
                                }
                                previewMediaPlayer = null
                                previewPlayingMode = null
                                selectedPrayerForSoundSettings = null
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Senyap (Tanpa Pengingat)", fontSize = 13.sp)
                            Text(text = "Hanya notifikasi visual tanpa bunyi", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    // Option 2: Suara Notifikasi Saja
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.savePrayerSoundSetting(prayer, 1)
                                    com.example.NotificationScheduler.scheduleAlarms(context)
                                    previewMediaPlayer?.let {
                                        if (it.isPlaying) { it.stop() }
                                        it.release()
                                    }
                                    previewMediaPlayer = null
                                    previewPlayingMode = null
                                    selectedPrayerForSoundSettings = null
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Suara Notifikasi Saja",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Bunyi nada notifikasi standar sekali (tidak berulang)",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isPlayingNotif = previewPlayingMode == 1
                        IconButton(
                            onClick = {
                                playPreview(1, prayer)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingNotif) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Uji Coba Nada Notifikasi",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Option 3: Suara Azan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                previewMediaPlayer?.let {
                                    if (it.isPlaying) { it.stop() }
                                    it.release()
                                }
                                previewMediaPlayer = null
                                previewPlayingMode = null
                                showAzanSourceSelectionForPrayer = prayer
                                selectedPrayerForSoundSettings = null
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Suara Azan", fontSize = 13.sp)
                            Text(text = "Lantunan azan lengkap (Default / MP3)", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    previewMediaPlayer?.let {
                        if (it.isPlaying) { it.stop() }
                        it.release()
                    }
                    previewMediaPlayer = null
                    previewPlayingMode = null
                    selectedPrayerForSoundSettings = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAzanSourceSelectionForPrayer != null) {
        val prayer = showAzanSourceSelectionForPrayer!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                previewMediaPlayer?.let {
                    if (it.isPlaying) { it.stop() }
                    it.release()
                }
                previewMediaPlayer = null
                previewPlayingMode = null
                showAzanSourceSelectionForPrayer = null
            },
            title = { Text(text = "Pilih Suara Azan ($prayer)") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Gunakan tombol play di kanan untuk uji coba suara sebelum memilih.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Option A: Default Aplikasi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    viewModel.savePrayerSoundSetting(prayer, 2)
                                    com.example.NotificationScheduler.scheduleAlarms(context)
                                    previewMediaPlayer?.let {
                                        if (it.isPlaying) { it.stop() }
                                        it.release()
                                    }
                                    previewMediaPlayer = null
                                    previewPlayingMode = null
                                    showAzanSourceSelectionForPrayer = null
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Default Aplikasi", fontSize = 13.sp)
                        }

                        val isPlayingDefault = previewPlayingMode == 2
                        IconButton(
                            onClick = {
                                playPreview(2, prayer)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingDefault) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Test Putar Default",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Option B: File dari Folder My Singghara/Data/mp3
                    val availableMp3s = remember(showAzanSourceSelectionForPrayer) {
                        com.example.util.SinggharaFolderManager.getAvailableMp3Files()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Folder My Singghara/Data/mp3",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Posisi sejajar folder Android. Kelola file via File Manager ponsel.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                        )

                        if (availableMp3s.isEmpty()) {
                            Text(
                                text = "Belum ada file .mp3 di folder Data/mp3.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else {
                            availableMp3s.forEach { mp3File ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                viewModel.setCustomAzanFromFile(prayer, mp3File, context)
                                                com.example.NotificationScheduler.scheduleAlarms(context)
                                                previewMediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
                                                previewMediaPlayer = null
                                                previewPlayingMode = null
                                                showAzanSourceSelectionForPrayer = null
                                                popupToastMessage = "Suara $prayer diatur ke: ${mp3File.name}"
                                            }
                                    ) {
                                        Text(text = mp3File.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Ketuk untuk memilih", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }

                                    IconButton(
                                        onClick = {
                                            try {
                                                previewMediaPlayer?.let {
                                                    if (it.isPlaying) it.stop()
                                                    it.release()
                                                }
                                                previewMediaPlayer = null
                                                if (previewPlayingMode == 3) {
                                                    previewPlayingMode = null
                                                } else {
                                                    val mp = android.media.MediaPlayer().apply {
                                                        setDataSource(mp3File.absolutePath)
                                                        prepare()
                                                        start()
                                                    }
                                                    previewMediaPlayer = mp
                                                    previewPlayingMode = 3
                                                    mp.setOnCompletionListener {
                                                        previewPlayingMode = null
                                                        previewMediaPlayer = null
                                                        try { mp.release() } catch (ignored: Exception) {}
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                popupToastMessage = "Gagal memutar preview MP3: ${e.message}"
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (previewPlayingMode == 3) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = "Preview",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Option C: Pilih MP3 dari Perangkat (File Picker Lain)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    previewMediaPlayer?.let {
                                        if (it.isPlaying) { it.stop() }
                                        it.release()
                                    }
                                    previewMediaPlayer = null
                                    previewPlayingMode = null
                                    selectedPrayerForFilePicker = prayer
                                    viewModel.preventAutoLock = true
                                    audioPickerLauncher.launch("audio/*")
                                    showAzanSourceSelectionForPrayer = null
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Pilih MP3 dari Lokasi Lain (SAF)", fontSize = 13.sp)
                                val customFile = java.io.File(context.filesDir, "azan_custom_${prayer.lowercase(java.util.Locale.getDefault())}.mp3")
                                if (customFile.exists()) {
                                    Text(text = "File kustom termuat", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                                } else {
                                    Text(text = "Belum ada file kustom", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        val customFile = java.io.File(context.filesDir, "azan_custom_${prayer.lowercase(java.util.Locale.getDefault())}.mp3")
                        if (customFile.exists()) {
                            val isPlayingCustom = previewPlayingMode == 3
                            IconButton(
                                onClick = {
                                    playPreview(3, prayer)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingCustom) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Test Putar Kustom",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    previewMediaPlayer?.let {
                        if (it.isPlaying) { it.stop() }
                        it.release()
                    }
                    previewMediaPlayer = null
                    previewPlayingMode = null
                    showAzanSourceSelectionForPrayer = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

}

@Composable
fun AllAppsScreen(
    onBackClick: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onLaunchGame: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Beautiful, Clean, Spaced Grid with exactly 4 columns per row, and no descriptions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Al-Qur'an, Pembukuan Usaha, MindMap AI, Musik (4 items)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllAppGridItem(
                    title = "Al-Qur'an",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    color = Color(0xFF00796B),
                    onClick = { onLaunchApp("quran") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Pembukuan",
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFFE65100),
                    onClick = { onLaunchApp("pembukuan_usaha") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "MindMap AI",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    color = Color(0xFF0288D1),
                    onClick = { onLaunchApp("mindmap") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Musik",
                    icon = Icons.Default.MusicNote,
                    color = Color(0xFFE91E63),
                    onClick = { onLaunchApp("pemutar_musik") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Puzzle Geser, Block Puzzle, Brick Game, Spinner (4 items)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllAppGridItem(
                    title = "Puzzle Geser",
                    icon = Icons.Default.DashboardCustomize,
                    color = Color(0xFFE65100),
                    onClick = { onLaunchGame("sliding_puzzle") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Block Puzzle",
                    icon = Icons.Default.GridOn,
                    color = Color(0xFF2C5E6A),
                    onClick = { onLaunchGame("block_puzzle") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Brick Game",
                    icon = Icons.Default.Gamepad,
                    color = Color(0xFF306230),
                    onClick = { onLaunchGame("tetris") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Spinner",
                    icon = Icons.Default.Refresh,
                    color = Color(0xFF8338EC),
                    onClick = { onLaunchGame("undian_spinner") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Jadwal Harian, Telegram AI & Handy Talky
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AllAppGridItem(
                    title = "Jadwal Harian",
                    icon = Icons.Default.AccessTime,
                    color = Color(0xFF43A047),
                    onClick = { onLaunchApp("jadwal_harian") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Telegram AI",
                    icon = Icons.AutoMirrored.Filled.Send,
                    color = Color(0xFF229ED9),
                    onClick = { onLaunchApp("telegram_ai") },
                    modifier = Modifier.weight(1f)
                )
                AllAppGridItem(
                    title = "Handy Talky",
                    icon = Icons.Default.SettingsVoice,
                    color = Color(0xFF0F766E),
                    onClick = { onLaunchApp("handy_talky") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AllAppGridItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
