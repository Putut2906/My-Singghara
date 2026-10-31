package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.print.PrintHelper
import com.example.data.arisan.*
import com.example.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class ArisanSection(val title: String) {
    RINGKASAN("Ringkasan & Kocokan"),
    ATURAN("Aturan & Kesepakatan"),
    IURAN("Iuran & Pembayaran"),
    KAS_SOSIAL("Buku Kas Sosial"),
    PESERTA("Daftar Peserta & Anggota"),
    RIWAYAT("Riwayat Pemenang & Kuitansi")
}

private fun formatRupiah(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

private fun triggerHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        }
    } catch (_: Exception) {}
}

private fun shareText(context: Context, text: String, targetPhone: String? = null) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!targetPhone.isNullOrBlank()) {
                val clean = targetPhone.replace("[^0-9]".toRegex(), "")
                val formatted = when {
                    clean.startsWith("0") -> "62" + clean.substring(1)
                    clean.startsWith("62") -> clean
                    else -> "62$clean"
                }
                setPackage("com.whatsapp")
                putExtra("jid", "$formatted@s.whatsapp.net")
            } else {
                setPackage("com.whatsapp")
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(fallback, "Bagikan ke WhatsApp"))
    }
}

private fun printReceiptBitmap(context: Context, riwayat: RiwayatPemenangArisan, config: ArisanConfig) {
    try {
        val width = 800
        val height = 1100
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val borderPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        canvas.drawRect(30f, 30f, width - 30f, height - 30f, borderPaint)

        val innerBorder = Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(40f, 40f, width - 40f, height - 40f, innerBorder)

        val headerPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KUITANSI PENYERAHAN UANG ARISAN", (width / 2).toFloat(), 90f, headerPaint)

        val subHeaderPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(config.namaArisan, (width / 2).toFloat(), 125f, subHeaderPaint)

        val linePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            strokeWidth = 2.5f
        }
        canvas.drawLine(60f, 145f, width - 60f, 145f, linePaint)

        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 20f
        }
        val labelPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#475569")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 190f
        fun drawRow(label: String, value: String) {
            canvas.drawText(label, 70f, y, labelPaint)
            canvas.drawText(":", 260f, y, labelPaint)
            canvas.drawText(value, 280f, y, textPaint)
            y += 42f
        }

        drawRow("Nomor Bukti", riwayat.nomorKuitansi)
        drawRow("Periode Putaran", "Putaran Ke-${riwayat.periode}")
        drawRow("Tanggal Pengundian", riwayat.tanggalPengundian)
        drawRow("Telah Diserahkan Kepada", riwayat.namaPemenang)
        drawRow("Hubungan Keluarga", riwayat.hubunganKeluarga)
        drawRow("Jumlah Tarikan", formatRupiah(riwayat.nominalTarikan))

        y += 10f
        canvas.drawText("Terbilang:", 70f, y, labelPaint)
        y += 30f

        val terbilangStr = TerbilangHelper.konversi(riwayat.nominalTarikan.toLong())
        val terbilangPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1E3A8A")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("\"$terbilangStr\"", 70f, y, terbilangPaint)

        y += 50f
        drawRow("Untuk Keperluan", "Tarikan Uang Arisan Keluarga Putaran ${riwayat.periode}")
        drawRow("Catatan", riwayat.catatan.ifBlank { "Lunas diserahkan kepada pemenang" })

        // Signatures area
        y += 120f
        val sigPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            textAlign = Paint.Align.CENTER
        }
        val sigTitlePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 16f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("Yang Menerima (Pemenang)", 200f, y, sigTitlePaint)
        canvas.drawText("Yang Menyerahkan (Bendahara)", width - 200f, y, sigTitlePaint)

        y += 120f
        canvas.drawText("(${riwayat.namaPemenang})", 200f, y, sigPaint)
        canvas.drawText("(${config.namaBendahara.ifBlank { "Bendahara Arisan" }})", width - 200f, y, sigPaint)

        val printHelper = PrintHelper(context)
        printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
        printHelper.printBitmap("Kuitansi_Arisan_${riwayat.nomorKuitansi}", bitmap)
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal memproses cetak: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArisanKeluargaScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val repo = viewModel.arisanRepository
    val dataState by repo.dataState.collectAsState()

    var currentSection by remember { mutableStateOf(ArisanSection.RINGKASAN) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // Dialogs state
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<AnggotaArisan?>(null) }
    var memberToDelete by remember { mutableStateOf<AnggotaArisan?>(null) }

    var showAddKasDialog by remember { mutableStateOf(false) }
    var kasToDelete by remember { mutableStateOf<KasSosialArisan?>(null) }

    var receiptToShow by remember { mutableStateOf<RiwayatPemenangArisan?>(null) }
    var showResetCycleDialog by remember { mutableStateOf(false) }
    var showMarkAllPaidDialog by remember { mutableStateOf(false) }
    var showClearDataConfirmDialog by remember { mutableStateOf(false) }

    // Financial summaries
    val config = dataState.config
    val totalSlots = dataState.anggotaList.sumOf { it.jumlahSlot }
    val totalTarikanPerPutaran = totalSlots * config.iuranPokokPerSlot
    val kasMasuk = dataState.kasSosialList.filter { it.jenis == "Masuk" }.sumOf { it.nominal }
    val kasKeluar = dataState.kasSosialList.filter { it.jenis == "Keluar" }.sumOf { it.nominal }
    val saldoKasSosial = kasMasuk - kasKeluar

    val totalSudahMenang = dataState.anggotaList.count { it.sudahMenang }
    val totalBelumMenang = dataState.anggotaList.count { !it.sudahMenang }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ----------------------------------------------------
        // TOP APP BAR: NAVIGATION & DRAWER ACCESS
        // ----------------------------------------------------
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("arisan_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali ke Beranda"
                    )
                }
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("arisan_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Buka Menu Drawer"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arisan Keluarga",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Aturan Rapi & Pengocokan Transparan",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { currentSection = ArisanSection.ATURAN },
                    modifier = Modifier.testTag("arisan_btn_aturan_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "Aturan & Kesepakatan",
                        tint = if (currentSection == ArisanSection.ATURAN) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showConfigDialog = true },
                    modifier = Modifier.testTag("arisan_btn_settings_top")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pengaturan Arisan",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ----------------------------------------------------
        // MODERN PREMIUM EXECUTIVE HERO CARD (DEEP BLUE GRADIENT)
        // ----------------------------------------------------
        val deepBlueGradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF071426), // Midnight Navy
                Color(0xFF0D254C), // Deep Navy
                Color(0xFF1E40AF), // Sapphire Blue
                Color(0xFF2563EB)  // Vibrant Royal Blue
            )
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(deepBlueGradient)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row: Pill & 3-dots Menu + Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E3A8A).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF38BDF8), CircleShape)
                                )
                                Text(
                                    text = "PUTARAN KE-${config.periodeAktif} • SIKLUS KE-${dataState.siklusKe}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDBEAFE),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Right action buttons: Settings and 3-dots overflow menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Pengaturan Arisan",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMenuDropdown = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Menu Arisan",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenuDropdown,
                                    onDismissRequest = { showMenuDropdown = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("📊 Ringkasan & Kocokan", fontWeight = if (currentSection == ArisanSection.RINGKASAN) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.RINGKASAN
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📋 Aturan & Kesepakatan", fontWeight = if (currentSection == ArisanSection.ATURAN) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.ATURAN
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("💰 Iuran & Pembayaran", fontWeight = if (currentSection == ArisanSection.IURAN) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.IURAN
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📖 Buku Catatan Kas Sosial", fontWeight = if (currentSection == ArisanSection.KAS_SOSIAL) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.KAS_SOSIAL
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("👥 Daftar Peserta (${dataState.anggotaList.size})", fontWeight = if (currentSection == ArisanSection.PESERTA) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.PESERTA
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("📜 Riwayat Pemenang & Kuitansi", fontWeight = if (currentSection == ArisanSection.RIWAYAT) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            currentSection = ArisanSection.RIWAYAT
                                            showMenuDropdown = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("⚙️ Pengaturan Arisan") },
                                        onClick = {
                                            showMenuDropdown = false
                                            showConfigDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🗑️ Reset / Kosongkan Data", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenuDropdown = false
                                            showClearDataConfirmDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                    )
                                }
                            }
                        }
                    }

                    // Main Total Tarikan Display
                    Column {
                        Text(
                            text = "TOTAL UANG TARIKAN PEMENANG",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF93C5FD),
                            letterSpacing = 1.1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatRupiah(totalTarikanPerPutaran),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Akumulasi tarikan putaran ini • ${totalSlots} Total Slot Terdaftar",
                            fontSize = 11.sp,
                            color = Color(0xFFDBEAFE).copy(alpha = 0.8f)
                        )
                    }

                    // Frosted Metrics Row inside the Deep Blue Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Saldo Kas Sosial
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                                    Text("Kas Sosial", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = formatRupiah(saldoKasSosial),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF38BDF8).copy(alpha = 0.3f)))

                            // Iuran / Slot
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(13.dp))
                                    Text("Iuran / Slot", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = formatRupiah(config.iuranPokokPerSlot + config.iuranKasPerSlot),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF38BDF8).copy(alpha = 0.3f)))

                            // Status Pemenang
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFCD34D), modifier = Modifier.size(13.dp))
                                    Text("Pemenang", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    text = "$totalSudahMenang / ${dataState.anggotaList.size} Peserta",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // QUICK CATEGORY NAVIGATION PILLS (MODERN LUXURY SWITCHER)
        // ----------------------------------------------------
        val navItems = listOf(
            ArisanSection.RINGKASAN to "🎲 Kocokan",
            ArisanSection.ATURAN to "📋 Aturan",
            ArisanSection.IURAN to "💳 Iuran",
            ArisanSection.KAS_SOSIAL to "📖 Kas Sosial",
            ArisanSection.PESERTA to "👥 Peserta (${dataState.anggotaList.size})",
            ArisanSection.RIWAYAT to "📜 Riwayat"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navItems.forEach { (section, label) ->
                val isSelected = currentSection == section
                Surface(
                    onClick = { currentSection = section },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF1E3A8A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF3B82F6) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = if (isSelected) {
                        Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB))),
                            RoundedCornerShape(20.dp)
                        )
                    } else Modifier
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (currentSection) {
                ArisanSection.RINGKASAN -> TabRingkasanDanKocokan(
                    dataState = dataState,
                    totalTarikanPerPutaran = totalTarikanPerPutaran,
                    saldoKasSosial = saldoKasSosial,
                    totalSudahMenang = totalSudahMenang,
                    totalBelumMenang = totalBelumMenang,
                    onWinnerSelected = { winner, nominal ->
                        val riwayat = repo.tetapkanPemenang(winner, nominal)
                        receiptToShow = riwayat
                    },
                    onResetCycleRequest = { showResetCycleDialog = true },
                    onShareInvite = {
                        val msg = buildWhatsAppInvitation(config, dataState.riwayatPemenangList.firstOrNull(), totalTarikanPerPutaran)
                        shareText(context, msg)
                    },
                    onOpenSettings = { showConfigDialog = true },
                    onAddMemberRequest = { showAddMemberDialog = true },
                    onOpenAturan = { currentSection = ArisanSection.ATURAN }
                )
                ArisanSection.ATURAN -> TabAturanDanPanduan(
                    config = config,
                    siklusKe = dataState.siklusKe,
                    saldoKasSosial = saldoKasSosial,
                    onOpenSettings = { showConfigDialog = true },
                    onGoToKocokan = { currentSection = ArisanSection.RINGKASAN },
                    onShareRules = { text -> shareText(context, text) }
                )
                ArisanSection.IURAN -> TabPencatatanIuran(
                    dataState = dataState,
                    onStatusChanged = { pId, isLunas, metode ->
                        repo.setPembayaranStatus(pId, isLunas, metode)
                    },
                    onMarkAllPaid = { showMarkAllPaidDialog = true },
                    onSendReminder = { phone, memberName, amount ->
                        val reminder = "Halo $memberName, mengingatkan untuk pembayaran Iuran Arisan ${config.namaArisan} Putaran ke-${config.periodeAktif} sebesar ${formatRupiah(amount)}. Terima kasih 🙏"
                        shareText(context, reminder, phone)
                    },
                    onAddMemberRequest = { showAddMemberDialog = true }
                )
                ArisanSection.KAS_SOSIAL -> TabBukuKasSosial(
                    kasList = dataState.kasSosialList,
                    saldoKas = saldoKasSosial,
                    totalMasuk = kasMasuk,
                    totalKeluar = kasKeluar,
                    onAddKasClick = { showAddKasDialog = true },
                    onDeleteKasClick = { kasToDelete = it }
                )
                ArisanSection.PESERTA -> TabDaftarPeserta(
                    members = dataState.anggotaList,
                    onAddMemberClick = { showAddMemberDialog = true },
                    onEditMemberClick = { memberToEdit = it },
                    onDeleteMemberClick = { memberToDelete = it },
                    onWhatsAppClick = { phone ->
                        if (phone.isNotBlank()) {
                            shareText(context, "Halo dari Arisan Keluarga ${config.namaArisan}", phone)
                        } else {
                            Toast.makeText(context, "Nomor WhatsApp belum diisi", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                ArisanSection.RIWAYAT -> TabRiwayatDanKuitansi(
                    riwayatList = dataState.riwayatPemenangList,
                    config = config,
                    onShowReceipt = { receiptToShow = it },
                    onShareInvite = {
                        val msg = buildWhatsAppInvitation(config, dataState.riwayatPemenangList.firstOrNull(), totalTarikanPerPutaran)
                        shareText(context, msg)
                    }
                )
            }
        }
    }


    // Dialogs
    if (showConfigDialog) {
        ArisanConfigDialog(
            currentConfig = config,
            onDismiss = { showConfigDialog = false },
            onSave = { updated ->
                repo.updateConfig(updated)
                showConfigDialog = false
                Toast.makeText(context, "Pengaturan arisan berhasil disimpan", Toast.LENGTH_SHORT).show()
            },
            onResetDataDefault = {
                repo.resetDataDefault()
                showConfigDialog = false
                Toast.makeText(context, "Data berhasil dikosongkan", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showClearDataConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Kosongkan Semua Data?") },
            text = { Text("Tindakan ini akan menghapus seluruh data anggota, iuran, kas sosial, dan riwayat pemenang. Data akan menjadi kosong.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.resetDataDefault()
                        showClearDataConfirmDialog = false
                        Toast.makeText(context, "Seluruh data arisan telah dikosongkan", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Kosongkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAddMemberDialog) {
        AddEditMemberDialog(
            member = null,
            onDismiss = { showAddMemberDialog = false },
            onSave = { nama, hub, wa, slot, catatan ->
                repo.tambahAnggota(nama, hub, wa, slot, catatan)
                showAddMemberDialog = false
                Toast.makeText(context, "Anggota $nama berhasil ditambahkan", Toast.LENGTH_SHORT).show()
            }
        )
    }

    memberToEdit?.let { currentMember ->
        AddEditMemberDialog(
            member = currentMember,
            onDismiss = { memberToEdit = null },
            onSave = { nama, hub, wa, slot, catatan ->
                repo.updateAnggota(currentMember.copy(nama = nama, hubunganKeluarga = hub, nomorWa = wa, jumlahSlot = slot, catatan = catatan))
                memberToEdit = null
                Toast.makeText(context, "Data anggota berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
        )
    }

    memberToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text("Hapus Anggota Arisan?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${target.nama}' (${target.hubunganKeluarga}) dari daftar arisan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.hapusAnggota(target.id)
                        memberToDelete = null
                        Toast.makeText(context, "Anggota telah dihapus", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showAddKasDialog) {
        AddKasEntryDialog(
            onDismiss = { showAddKasDialog = false },
            onSave = { tgl, jenis, kat, desc, nom ->
                repo.tambahKasSosial(tgl, jenis, kat, desc, nom)
                showAddKasDialog = false
                Toast.makeText(context, "Transaksi kas berhasil dicatat", Toast.LENGTH_SHORT).show()
            }
        )
    }

    kasToDelete?.let { kas ->
        AlertDialog(
            onDismissRequest = { kasToDelete = null },
            title = { Text("Hapus Catatan Kas?") },
            text = { Text("Hapus transaksi '${kas.deskripsi}' senilai ${formatRupiah(kas.nominal)}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.hapusKasSosial(kas.id)
                        kasToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { kasToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showResetCycleDialog) {
        AlertDialog(
            onDismissRequest = { showResetCycleDialog = false },
            icon = { Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Mulai Siklus Putaran Baru?", textAlign = TextAlign.Center) },
            text = {
                Text("Semua status menang anggota akan direset kembali menjadi 'Belum Menang', dan periode akan dimulai kembali dari Putaran 1 untuk siklus berikutnya.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        repo.resetSiklusArisan()
                        showResetCycleDialog = false
                        Toast.makeText(context, "Siklus baru dimulai! Semua anggota siap dikocok kembali.", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("Mulai Siklus Baru")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCycleDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showMarkAllPaidDialog) {
        var selectedMethod by remember { mutableStateOf("Tunai") }
        AlertDialog(
            onDismissRequest = { showMarkAllPaidDialog = false },
            title = { Text("Tandai Semua Lunas?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tandai seluruh tagihan anggota untuk Putaran ${config.periodeAktif} sebagai Lunas.")
                    Text("Pilih Metode Pembayaran:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMethod == "Tunai",
                            onClick = { selectedMethod = "Tunai" },
                            label = { Text("💵 Tunai") }
                        )
                        FilterChip(
                            selected = selectedMethod == "Transfer Bank",
                            onClick = { selectedMethod = "Transfer Bank" },
                            label = { Text("🏦 Transfer") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repo.tandaiSemuaLunas(config.periodeAktif, selectedMethod)
                        showMarkAllPaidDialog = false
                        Toast.makeText(context, "Semua anggota ditandai Lunas ($selectedMethod)", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Tandai Lunas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkAllPaidDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    receiptToShow?.let { riwayat ->
        ReceiptDetailDialog(
            riwayat = riwayat,
            config = config,
            onDismiss = { receiptToShow = null },
            onPrint = {
                printReceiptBitmap(context, riwayat, config)
            },
            onShare = {
                val receiptText = buildReceiptText(riwayat, config)
                shareText(context, receiptText, riwayat.nomorWa)
            }
        )
    }
}

// ----------------------------------------------------
// TAB 1: RINGKASAN & KOCOKAN ARISAN DIGITAL
// ----------------------------------------------------
@Composable
private fun TabRingkasanDanKocokan(
    dataState: ArisanDataState,
    totalTarikanPerPutaran: Double,
    saldoKasSosial: Double,
    totalSudahMenang: Int,
    totalBelumMenang: Int,
    onWinnerSelected: (AnggotaArisan, Double) -> Unit,
    onResetCycleRequest: () -> Unit,
    onShareInvite: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddMemberRequest: () -> Unit,
    onOpenAturan: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val candidates = remember(dataState.anggotaList) {
        dataState.anggotaList.filter { !it.sudahMenang }
    }

    var isSpinning by remember { mutableStateOf(false) }
    var displayedCandidateName by remember { mutableStateOf(candidates.firstOrNull()?.nama ?: "Belum ada peserta") }
    var displayedCandidateRelation by remember { mutableStateOf(candidates.firstOrNull()?.hubunganKeluarga ?: "") }
    var winnerCandidate by remember { mutableStateOf<AnggotaArisan?>(null) }
    var showWinnerCelebrationDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // If members list is completely empty, show helpful empty state prompt
        if (dataState.anggotaList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.GroupAdd,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Text(
                            text = "Belum Ada Anggota Keluarga",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Data arisan saat ini kosong. Tambahkan anggota keluarga untuk mulai mencatat iuran dan melakukan pengundian kocokan arisan.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = onAddMemberRequest,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Anggota Pertama", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 1. Sleek Status & Slot Detail Banner
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Rincian: Pokok ${formatRupiah(dataState.config.iuranPokokPerSlot)} + Kas ${formatRupiah(dataState.config.iuranKasPerSlot)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "$totalBelumMenang sisa undian",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }

        // 1.5. Ringkasan 4 Aturan Dasar & Kesepakatan Arisan Keluarga
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAturan() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Aturan Dasar & Kesepakatan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Manajemen Arisan Keluarga Praktis",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = onOpenAturan,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Buka Detail >",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    Text(
                        text = "Manajemen arisan keluarga yang baik membutuhkan aturan yang jelas, pencatatan rapi, dan sistem pengocokan yang transparan.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    // 4 Pillars Breakdown Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleSummaryRow(
                            icon = Icons.Default.Payments,
                            iconTint = Color(0xFF3B82F6),
                            title = "Nominal Setoran",
                            description = "Tentukan jumlah iuran yang wajar dan tidak memberatkan seluruh anggota keluarga (misalnya Rp50.000 – Rp100.000 per bulan)."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                        RuleSummaryRow(
                            icon = Icons.Default.LocationOn,
                            iconTint = Color(0xFF8B5CF6),
                            title = "Jadwal & Lokasi",
                            description = "Sepakati apakah arisan diadakan tiap bulan, dua bulan sekali, atau setahun sekali saat momen Lebaran/liburan. Buat sistem rotasi tempat tinggal secara bergiliran."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                        RuleSummaryRow(
                            icon = Icons.Default.ShoppingBasket,
                            iconTint = Color(0xFF10B981),
                            title = "Biaya Konsumsi",
                            description = "Pisahkan uang konsumsi dari uang arisan. Tuan rumah bisa menyiapkan sekadarnya, menggunakan sistem potluck (bawa makanan sendiri-sendiri), atau iuran konsumsi terpisah (misalnya Rp20.000 per orang)."
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                        RuleSummaryRow(
                            icon = Icons.Default.CalendarMonth,
                            iconTint = Color(0xFFEF4444),
                            title = "Ketentuan Pembayaran",
                            description = "Tentukan batas tanggal penyetoran setiap bulannya (misalnya tanggal 5 setiap bulan) melalui bendahara atau transfer rekening."
                        )
                    }
                }
            }
        }

        // 2. Interactive Digital Lottery Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Casino,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text("Kocokan Arisan Digital", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Pengundian acak otomatis", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "Putaran ${dataState.config.periodeAktif}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (dataState.anggotaList.isEmpty()) {
                        Text(
                            text = "Belum ada anggota yang terdaftar untuk dikocok.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (candidates.isEmpty()) {
                        // All members already won this cycle!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Semua Anggota Telah Menang! 🎉",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Siklus ke-${dataState.siklusKe} telah selesai secara lengkap. Mulai siklus baru untuk mengocok kembali semua anggota.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onResetCycleRequest
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mulai Siklus Putaran Baru")
                            }
                        }
                    } else {
                        // Digital Rotating Display Drum (Light/Dark Theme Synchronized)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF071426),
                                            Color(0xFF0F2B5C),
                                            Color(0xFF071426)
                                        )
                                    )
                                )
                                .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isSpinning) "⚡ SEDANG MENGOCOK NAMA PESERTA..." else "KANDIDAT PUTARAN KE-${dataState.config.periodeAktif}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = displayedCandidateName,
                                    fontSize = if (displayedCandidateName.length > 20) 19.sp else 23.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                if (displayedCandidateRelation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = Color(0xFF1E3A8A).copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = displayedCandidateRelation,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFDBEAFE),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "${candidates.size} anggota keluarga berhak diundi putaran ini",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Spin Button
                        Button(
                            onClick = {
                                if (!isSpinning && candidates.isNotEmpty()) {
                                    isSpinning = true
                                    coroutineScope.launch {
                                        val picked = candidates.random()
                                        val delays = listOf(50L, 50L, 60L, 70L, 80L, 100L, 130L, 170L, 220L, 290L, 380L, 500L, 700L)
                                        var spinIdx = 0
                                        for (stepDelay in delays) {
                                            val tempCand = candidates[spinIdx % candidates.size]
                                            displayedCandidateName = tempCand.nama
                                            displayedCandidateRelation = tempCand.hubunganKeluarga
                                            triggerHaptic(context)
                                            delay(stepDelay)
                                            spinIdx++
                                        }
                                        displayedCandidateName = picked.nama
                                        displayedCandidateRelation = picked.hubunganKeluarga
                                        triggerHaptic(context)
                                        delay(400)
                                        isSpinning = false
                                        winnerCandidate = picked
                                        showWinnerCelebrationDialog = true
                                    }
                                }
                            },
                            enabled = !isSpinning && candidates.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSpinning) "Mengundi Pemenang..." else "Kocok Arisan Sekarang!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Info Pertemuan & WhatsApp Share
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Pertemuan Arisan", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = onOpenSettings, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Ubah Jadwal", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hari & Tanggal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dataState.config.jadwalPertemuanBerikutnya.ifBlank { "Belum diatur" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pukul / Waktu", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dataState.config.waktuPertemuanBerikutnya.ifBlank { "Belum diatur" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Lokasi / Tuan Rumah", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dataState.config.lokasiPertemuanBerikutnya.ifBlank { "Belum diatur" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    if (dataState.config.catatanPertemuan.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 Catatan: ${dataState.config.catatanPertemuan}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = onShareInvite,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bagikan Undangan ke WhatsApp Keluarga", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Celebration Dialog when Winner is picked
    if (showWinnerCelebrationDialog && winnerCandidate != null) {
        val winner = winnerCandidate!!
        Dialog(
            onDismissRequest = { showWinnerCelebrationDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = "Pemenang",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🎉 SELAMAT KEPADA PEMENANG! 🎉",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Putaran Arisan Ke-${dataState.config.periodeAktif}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = winner.nama,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Hubungan: ${winner.hubunganKeluarga}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nominal Uang Tarikan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRupiah(totalTarikanPerPutaran),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "(${TerbilangHelper.konversi(totalTarikanPerPutaran.toLong())})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            showWinnerCelebrationDialog = false
                            onWinnerSelected(winner, totalTarikanPerPutaran)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tetapkan Pemenang & Buat Kuitansi")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showWinnerCelebrationDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batal / Kocok Ulang")
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// PANDUAN & ATURAN DASAR KESEPAKATAN ARISAN KELUARGA
// ----------------------------------------------------
fun buildWhatsAppRulesText(config: ArisanConfig, siklusKe: Int): String {
    val nominalStr = formatRupiah(config.iuranPokokPerSlot)
    val kasStr = if (config.iuranKasPerSlot > 0) " + Kas Sosial ${formatRupiah(config.iuranKasPerSlot)}" else ""
    return """
📋 *PANDUAN & ATURAN KESEPAKATAN ARISAN KELUARGA*
*${config.namaArisan.ifBlank { "Arisan Keluarga" }}* (Siklus ke-$siklusKe)

Assalamu'alaikum Wr. Wb. & Salam Hangat Keluarga,
Manajemen arisan keluarga yang baik membutuhkan aturan yang jelas, pencatatan rapi, dan sistem pengocokan yang transparan. Berikut adalah 4 kesepakatan bersama sejak awal:

1️⃣ *Nominal Setoran (Iuran Wajar)*
• Besaran iuran: $nominalStr$kasStr per slot / nomor per putaran.
• Jumlah iuran wajar dan disepakati bersama agar tidak memberatkan seluruh anggota keluarga (kisaran Rp50.000 – Rp100.000). Anggota diperbolehkan mengambil lebih dari 1 slot sesuai kemampuan.

2️⃣ *Jadwal & Lokasi Pertemuan (Rotasi Bergilir)*
• Frekuensi acara: Disepakati bulanan / 2 bulan sekali / setahun sekali (saat momen libur Lebaran/liburan keluarga).
• Sistem lokasi: Menerapkan rotasi tempat tinggal secara bergiliran bagi keluarga agar tali silaturahmi tetap erat dan adil.

3️⃣ *Biaya Konsumsi (Dipisahkan dari Uang Arisan)*
• Uang tarikan arisan murni hak pemenang dan dipisahkan dari uang konsumsi!
• Sistem jamuan: Tuan rumah menyiapkan sekadarnya, atau sistem potluck (bawa makanan sendiri-sendiri), atau iuran konsumsi terpisah (~Rp20.000/orang).

4️⃣ *Ketentuan Pembayaran & Transparansi*
• Batas waktu setor: Maksimal tanggal 5 setiap bulannya.
• Pembayaran diserahkan kepada Bendahara: *${config.namaBendahara.ifBlank { "Bendahara Arisan" }}*
• Rekening/E-Wallet: *${config.infoRekening.ifBlank { "Tunai / Hubungi Bendahara" }}*
• Pencatatan dilakukan otomatis dan pengocokan transparan melalui aplikasi My Singghara.

Mari kita jaga keterbukaan dan kebersamaan keluarga. Semoga arisan ini membawa berkah untuk kita semua! 🤲
""".trimIndent()
}

@Composable
private fun TabAturanDanPanduan(
    config: ArisanConfig,
    siklusKe: Int,
    saldoKasSosial: Double,
    onOpenSettings: () -> Unit,
    onGoToKocokan: () -> Unit,
    onShareRules: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val rulesText = remember(config, siklusKe) { buildWhatsAppRulesText(config, siklusKe) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
    ) {
        // Hero Card: Prinsip Utama Manajemen Arisan
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Panduan Manajemen Arisan",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Aturan Jelas • Catatan Rapi • Kocokan Transparan",
                                fontSize = 12.sp,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "Manajemen arisan keluarga yang baik membutuhkan aturan yang jelas, pencatatan rapi, dan sistem pengocokan yang transparan agar mempererat silaturahmi tanpa menimbulkan beban maupun kesalahpahaman.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Action buttons: Copy & Share WhatsApp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rulesText))
                                Toast.makeText(context, "Naskah aturan arisan berhasil disalin!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E3A8A)
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin Aturan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onShareRules(rulesText) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan WA", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Section Title: 4 Aturan Dasar & Kesepakatan (Semenjak Awal)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.size(width = 4.dp, height = 18.dp)
                ) {}
                Text(
                    text = "Aturan Dasar & Kesepakatan (Semenjak Awal)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Pilar 1: Nominal Setoran
        item {
            RuleCard(
                number = "1",
                badgeText = "IURAN WAJAR",
                badgeColor = Color(0xFF3B82F6),
                icon = Icons.Default.Payments,
                title = "Nominal Setoran",
                description = "Tentukan jumlah iuran yang wajar dan tidak memberatkan seluruh anggota keluarga (misalnya Rp50.000 – Rp100.000 per bulan).",
                details = listOf(
                    "Iuran Pokok Terdaftar: ${formatRupiah(config.iuranPokokPerSlot)} per slot",
                    "Iuran Kas Sosial: ${formatRupiah(config.iuranKasPerSlot)} per slot",
                    "Total Iuran per Slot: ${formatRupiah(config.iuranPokokPerSlot + config.iuranKasPerSlot)}"
                ),
                practicalTips = "Bagi anggota yang memiliki rezeki lebih, perbolehkan mengambil 2 slot atau lebih. Anggota lain tetap dapat berpartisipasi dengan 1 slot tanpa minder atau terbebani."
            )
        }

        // Pilar 2: Jadwal & Lokasi
        item {
            RuleCard(
                number = "2",
                badgeText = "ROTASI BERGILIR",
                badgeColor = Color(0xFF8B5CF6),
                icon = Icons.Default.LocationOn,
                title = "Jadwal & Lokasi Pertemuan",
                description = "Sepakati apakah arisan diadakan tiap bulan, dua bulan sekali, atau setahun sekali saat momen Lebaran/liburan. Buat sistem rotasi tempat tinggal secara bergiliran.",
                details = listOf(
                    "Putaran Aktif: Putaran ke-${config.periodeAktif} • Siklus ke-$siklusKe",
                    "Lokasi: Bergilir di rumah anggota yang disepakati bersama",
                    "Momentum: Menjadi sarana temu kangen & perekat silaturahmi keluarga"
                ),
                practicalTips = "Tuan rumah pertemuan berikutnya dapat langsung diumumkan saat pemenang undian putaran ini keluar, atau diatur giliran tempat tinggalnya agar persiapan tuan rumah leluasa."
            )
        }

        // Pilar 3: Biaya Konsumsi
        item {
            RuleCard(
                number = "3",
                badgeText = "KONSUMSI TERPISAH",
                badgeColor = Color(0xFF10B981),
                icon = Icons.Default.ShoppingBasket,
                title = "Biaya Konsumsi",
                description = "Pisahkan uang konsumsi dari uang arisan. Tuan rumah bisa menyiapkan sekadarnya, menggunakan sistem potluck (bawa makanan sendiri-sendiri), atau iuran konsumsi terpisah (misalnya Rp20.000 per orang).",
                details = listOf(
                    "Hak Uang Tarikan: Diberikan utuh 100% kepada pemenang arisan",
                    "Sistem Jamuan: Sederhana / Potluck bawaan masing-masing / Kas konsumsi",
                    "Saldo Kas Sosial Saat Ini: ${formatRupiah(saldoKasSosial)}"
                ),
                practicalTips = "Pemisahan ini penting agar arisan tidak menjadi beban finansial bagi tuan rumah. Manfaatkan fitur 'Buku Kas Sosial' di aplikasi ini untuk mencatat konsumsi bersama."
            )
        }

        // Pilar 4: Ketentuan Pembayaran
        item {
            RuleCard(
                number = "4",
                badgeText = "DISIPLIN TANGGAL 5",
                badgeColor = Color(0xFFEF4444),
                icon = Icons.Default.CalendarMonth,
                title = "Ketentuan Pembayaran",
                description = "Tentukan batas tanggal penyetoran setiap bulannya (misalnya tanggal 5 setiap bulan) melalui bendahara atau transfer rekening.",
                details = listOf(
                    "Batas Penyetoran: Maksimal tanggal 5 setiap bulannya",
                    "Bendahara Arisan: ${config.namaBendahara.ifBlank { "Belum diisi (Setel di Pengaturan)" }}",
                    "Rekening / E-Wallet: ${config.infoRekening.ifBlank { "Tunai / Transfer Bank (Setel di Pengaturan)" }}"
                ),
                practicalTips = "Disiplin tanggal penyetoran memastikan pengocokan dapat dilakukan tepat waktu. Gunakan fitur pengingat WhatsApp di tab 'Iuran' untuk menyapa anggota dengan sopan dan ramah."
            )
        }

        // Footer Card: Navigasi Cepat
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Kelola Pengaturan & Kocokan",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ubah Aturan", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onGoToKocokan,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ke Kocokan", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSummaryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.5.sp
            )
        }
    }
}

@Composable
private fun RuleCard(
    number: String,
    badgeText: String,
    badgeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    details: List<String>,
    practicalTips: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "$number. $title",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            // Details List
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    details.forEach { detail ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "•", fontSize = 12.sp, color = badgeColor, fontWeight = FontWeight.Bold)
                            Text(text = detail, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Practical Tip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF3C7).copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = practicalTips,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: PENCATATAN IURAN & STATUS PEMBAYARAN
// ----------------------------------------------------
@Composable
private fun TabPencatatanIuran(
    dataState: ArisanDataState,
    onStatusChanged: (pembayaranId: String, isLunas: Boolean, metode: String) -> Unit,
    onMarkAllPaid: () -> Unit,
    onSendReminder: (phone: String, memberName: String, amount: Double) -> Unit,
    onAddMemberRequest: () -> Unit
) {
    var selectedPeriode by remember { mutableIntStateOf(dataState.config.periodeAktif) }
    var filterStatus by remember { mutableStateOf("Semua") }

    val currentPayments = remember(dataState.pembayaranList, selectedPeriode) {
        dataState.pembayaranList.filter { it.periode == selectedPeriode }
    }

    val filteredList = remember(currentPayments, filterStatus) {
        when (filterStatus) {
            "Lunas" -> currentPayments.filter { it.isLunas }
            "Belum Bayar" -> currentPayments.filter { !it.isLunas }
            else -> currentPayments
        }
    }

    val totalDukungan = currentPayments.sumOf { it.totalTagihan }
    val totalTerkumpul = currentPayments.filter { it.isLunas }.sumOf { it.totalTagihan }
    val countLunas = currentPayments.count { it.isLunas }
    val countBelum = currentPayments.count { !it.isLunas }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        if (dataState.anggotaList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Belum Ada Data Iuran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Data tagihan iuran arisan akan otomatis dibuat ketika Anda menambahkan anggota keluarga.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onAddMemberRequest) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Anggota")
                        }
                    }
                }
            }
        } else {
            // Period selector & Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Rekap Pembayaran Iuran", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Pokok ${formatRupiah(dataState.config.iuranPokokPerSlot)} + Kas ${formatRupiah(dataState.config.iuranKasPerSlot)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { if (selectedPeriode > 1) selectedPeriode-- },
                                    enabled = selectedPeriode > 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Sebelumnya")
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "Putaran $selectedPeriode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { selectedPeriode++ },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Selanjutnya")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress collected
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Terkumpul: ${formatRupiah(totalTerkumpul)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Target: ${formatRupiah(totalDukungan)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val progress = if (totalDukungan > 0) (totalTerkumpul / totalDukungan).toFloat() else 0f
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$countLunas Lunas • $countBelum Belum",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onMarkAllPaid,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tandai Semua Lunas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterStatus == "Semua",
                        onClick = { filterStatus = "Semua" },
                        label = { Text("Semua (${currentPayments.size})") }
                    )
                    FilterChip(
                        selected = filterStatus == "Lunas",
                        onClick = { filterStatus = "Lunas" },
                        label = { Text("Lunas ($countLunas)") }
                    )
                    FilterChip(
                        selected = filterStatus == "Belum Bayar",
                        onClick = { filterStatus = "Belum Bayar" },
                        label = { Text("Belum Bayar ($countBelum)") }
                    )
                }
            }

            items(filteredList, key = { it.id }) { payment ->
                PaymentItemCard(
                    payment = payment,
                    onStatusToggle = { newLunas, metode ->
                        onStatusChanged(payment.id, newLunas, metode)
                    },
                    onSendReminder = {
                        val member = dataState.anggotaList.find { it.id == payment.anggotaId }
                        onSendReminder(member?.nomorWa ?: "", payment.namaAnggota, payment.totalTagihan)
                    }
                )
            }
        }
    }
}

@Composable
private fun PaymentItemCard(
    payment: PembayaranIuran,
    onStatusToggle: (isLunas: Boolean, metode: String) -> Unit,
    onSendReminder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (payment.isLunas) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(payment.namaAnggota, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${payment.jumlahSlot} Slot • ${formatRupiah(payment.totalTagihan)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (payment.isLunas) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (payment.isLunas) "LUNAS (${payment.metodePembayaran})" else "BELUM BAYAR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (payment.isLunas) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (payment.isLunas) {
                    Text(
                        text = "Dibayar: ${payment.tanggalBayar.ifBlank { "Tercatat" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { onStatusToggle(false, "Tunai") },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Ubah Belum Bayar", fontSize = 10.sp)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onStatusToggle(true, "Tunai") },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("💵 Tunai", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { onStatusToggle(true, "Transfer Bank") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("🏦 Transfer", fontSize = 10.sp)
                        }
                    }

                    IconButton(onClick = onSendReminder, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Send, contentDescription = "Ingatkan WhatsApp", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: BUKU CATATAN KAS SOSIAL ARISAN
// ----------------------------------------------------
@Composable
private fun TabBukuKasSosial(
    kasList: List<KasSosialArisan>,
    saldoKas: Double,
    totalMasuk: Double,
    totalKeluar: Double,
    onAddKasClick: () -> Unit,
    onDeleteKasClick: (KasSosialArisan) -> Unit
) {
    var filterType by remember { mutableStateOf("Semua") }

    val filteredList = remember(kasList, filterType) {
        when (filterType) {
            "Masuk" -> kasList.filter { it.jenis == "Masuk" }
            "Keluar" -> kasList.filter { it.jenis == "Keluar" }
            else -> kasList
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddKasClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Catat Kas")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
        ) {
            // Saldo Kas Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Saldo Kas Sosial Arisan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRupiah(saldoKas),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Kas Masuk", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatRupiah(totalMasuk), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                Text("Total Kas Keluar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatRupiah(totalKeluar), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterType == "Semua",
                        onClick = { filterType = "Semua" },
                        label = { Text("Semua Transaksi (${kasList.size})") }
                    )
                    FilterChip(
                        selected = filterType == "Masuk",
                        onClick = { filterType = "Masuk" },
                        label = { Text("Kas Masuk") }
                    )
                    FilterChip(
                        selected = filterType == "Keluar",
                        onClick = { filterType = "Keluar" },
                        label = { Text("Kas Keluar") }
                    )
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text("Belum ada catatan transaksi kas sosial.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Button(onClick = onAddKasClick, modifier = Modifier.padding(top = 6.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Catat Transaksi Kas")
                            }
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    KasItemCard(item = item, onDelete = { onDeleteKasClick(item) })
                }
            }
        }
    }
}

@Composable
private fun KasItemCard(item: KasSosialArisan, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (item.jenis == "Masuk") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.jenis == "Masuk") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = if (item.jenis == "Masuk") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(item.deskripsi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.kategori, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text(item.tanggal, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (item.jenis == "Masuk") "+" else "-"} ${formatRupiah(item.nominal)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (item.jenis == "Masuk") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 4: DAFTAR PESERTA & MANAJEMEN ANGGOTA
// ----------------------------------------------------
@Composable
private fun TabDaftarPeserta(
    members: List<AnggotaArisan>,
    onAddMemberClick: () -> Unit,
    onEditMemberClick: (AnggotaArisan) -> Unit,
    onDeleteMemberClick: (AnggotaArisan) -> Unit,
    onWhatsAppClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterWinStatus by remember { mutableStateOf("Semua") }

    val filteredMembers = remember(members, searchQuery, filterWinStatus) {
        members.filter {
            val matchesSearch = it.nama.contains(searchQuery, ignoreCase = true) ||
                    it.hubunganKeluarga.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterWinStatus) {
                "Belum Menang" -> !it.sudahMenang
                "Sudah Menang" -> it.sudahMenang
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMemberClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Tambah Anggota")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
        ) {
            // Search & Filter
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nama atau hubungan keluarga...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterWinStatus == "Semua",
                        onClick = { filterWinStatus = "Semua" },
                        label = { Text("Semua (${members.size})") }
                    )
                    FilterChip(
                        selected = filterWinStatus == "Belum Menang",
                        onClick = { filterWinStatus = "Belum Menang" },
                        label = { Text("Belum Menang (${members.count { !it.sudahMenang }})") }
                    )
                    FilterChip(
                        selected = filterWinStatus == "Sudah Menang",
                        onClick = { filterWinStatus = "Sudah Menang" },
                        label = { Text("Sudah Menang (${members.count { it.sudahMenang }})") }
                    )
                }
            }

            if (filteredMembers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                            Text(
                                text = if (members.isEmpty()) "Daftar peserta masih kosong." else "Tidak ada anggota keluarga yang cocok.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            if (members.isEmpty()) {
                                Button(onClick = onAddMemberClick, modifier = Modifier.padding(top = 6.dp)) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tambah Anggota Sekarang")
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredMembers, key = { it.id }) { member ->
                    MemberItemCard(
                        member = member,
                        onEdit = { onEditMemberClick(member) },
                        onDelete = { onDeleteMemberClick(member) },
                        onWhatsApp = { onWhatsAppClick(member.nomorWa) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberItemCard(
    member: AnggotaArisan,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.nama.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(member.nama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${member.jumlahSlot} Slot",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = member.hubunganKeluarga,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (member.sudahMenang) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "🏆 Menang Putaran ${member.periodeMenang ?: 1} (${member.tanggalMenang ?: ""})",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "⏳ Belum Menang",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (member.nomorWa.isNotBlank()) {
                    IconButton(onClick = onWhatsApp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = "WhatsApp", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 5: RIWAYAT PEMENANG & KUITANSI DIGITAL
// ----------------------------------------------------
@Composable
private fun TabRiwayatDanKuitansi(
    riwayatList: List<RiwayatPemenangArisan>,
    config: ArisanConfig,
    onShowReceipt: (RiwayatPemenangArisan) -> Unit,
    onShareInvite: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // Invite card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kirim Undangan WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Jadwal pertemuan, lokasi & info tarikan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onShareInvite,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Riwayat Pemenang & Kuitansi Penyerahan",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (riwayatList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Belum ada pemenang yang diundi.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(riwayatList, key = { it.id }) { riwayat ->
                WinnerHistoryCard(riwayat = riwayat, onShowReceipt = { onShowReceipt(riwayat) })
            }
        }
    }
}

@Composable
private fun WinnerHistoryCard(
    riwayat: RiwayatPemenangArisan,
    onShowReceipt: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "Putaran ${riwayat.periode}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = riwayat.tanggalPengundian,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(riwayat.namaPemenang, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = "${riwayat.hubunganKeluarga} • Bukti: ${riwayat.nomorKuitansi}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatRupiah(riwayat.nominalTarikan),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onShowReceipt,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lihat Kuitansi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// KUITANSI DETAIL DIALOG (AUTHENTIC INDONESIAN RECEIPT)
// ----------------------------------------------------
@Composable
private fun ReceiptDetailDialog(
    riwayat: RiwayatPemenangArisan,
    config: ArisanConfig,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Receipt Border Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    // Header
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "KUITANSI PENYERAHAN UANG",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = config.namaArisan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "No. Bukti: ${riwayat.nomorKuitansi}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Receipt Fields
                    ReceiptRow(label = "Telah Terima Dari", value = "Bendahara ${config.namaArisan}")
                    ReceiptRow(label = "Uang Sejumlah", value = formatRupiah(riwayat.nominalTarikan), isBold = true, valueColor = MaterialTheme.colorScheme.primary)

                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Terbilang: \"${TerbilangHelper.konversi(riwayat.nominalTarikan.toLong())}\"",
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ReceiptRow(label = "Untuk Pembayaran", value = "Uang Tarikan Pemenang Arisan Putaran Ke-${riwayat.periode}")
                    ReceiptRow(label = "Penerima (Pemenang)", value = "${riwayat.namaPemenang} (${riwayat.hubunganKeluarga})")
                    ReceiptRow(label = "Tanggal Penyerahan", value = riwayat.tanggalPengundian)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Signature block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Yang Menerima,", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(40.dp))
                            Text("(${riwayat.namaPemenang})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Pemenang Arisan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Yang Menyerahkan,", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(40.dp))
                            Text("(${config.namaBendahara.ifBlank { "Bendahara Arisan" }})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Bendahara Arisan", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPrint,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cetak")
                    }

                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = buildReceiptText(riwayat, config)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Kuitansi Arisan", text))
                            Toast.makeText(context, "Teks kuitansi berhasil disalin", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin Teks")
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

// ----------------------------------------------------
// DIALOG: TAMBAH & EDIT ANGGOTA
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditMemberDialog(
    member: AnggotaArisan?,
    onDismiss: () -> Unit,
    onSave: (nama: String, hubungan: String, wa: String, slot: Int, catatan: String) -> Unit
) {
    var nama by remember { mutableStateOf(member?.nama ?: "") }
    var hubungan by remember { mutableStateOf(member?.hubunganKeluarga ?: "Saudara Kandung") }
    var wa by remember { mutableStateOf(member?.nomorWa ?: "") }
    var slotStr by remember { mutableStateOf(member?.jumlahSlot?.toString() ?: "1") }
    var catatan by remember { mutableStateOf(member?.catatan ?: "") }

    val hubunganOptions = listOf(
        "Kepala Keluarga / Tuan Rumah",
        "Orang Tua",
        "Istri / Suami",
        "Anak",
        "Saudara Kandung",
        "Kakak",
        "Adik",
        "Paman / Om",
        "Bibi / Tante",
        "Sepupu",
        "Keponakan",
        "Pakde / Bude",
        "Ipar",
        "Menantu",
        "Kakek / Nenek",
        "Kerabat Lainnya"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "Tambah Anggota Arisan" else "Edit Data Anggota") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Anggota / Panggilan *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Hubungan Keluarga:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hubunganOptions.forEach { opt ->
                        FilterChip(
                            selected = hubungan == opt,
                            onClick = { hubungan = opt },
                            label = { Text(opt, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = wa,
                    onValueChange = { wa = it },
                    label = { Text("Nomor WhatsApp (Contoh: 08123456789)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = slotStr,
                    onValueChange = { slotStr = it },
                    label = { Text("Jumlah Slot Arisan (Default 1)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Catatan Tambahan (Opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.isNotBlank()) {
                        val slot = slotStr.toIntOrNull() ?: 1
                        onSave(nama, hubungan, wa, slot, catatan)
                    }
                },
                enabled = nama.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// ----------------------------------------------------
// DIALOG: TAMBAH CATATAN KAS SOSIAL
// ----------------------------------------------------
@Composable
private fun AddKasEntryDialog(
    onDismiss: () -> Unit,
    onSave: (tanggal: String, jenis: String, kategori: String, deskripsi: String, nominal: Double) -> Unit
) {
    val today = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date())
    var tanggal by remember { mutableStateOf(today) }
    var jenis by remember { mutableStateOf("Keluar") }
    var kategori by remember { mutableStateOf("Konsumsi Pertemuan") }
    var deskripsi by remember { mutableStateOf("") }
    var nominalStr by remember { mutableStateOf("") }

    val kategoriOptions = listOf(
        "Iuran Kas Anggota",
        "Konsumsi Pertemuan",
        "Snack & Minuman",
        "Santunan / Dana Darurat",
        "Sewa Perlengkapan & Tenda",
        "Donasi Sukarela",
        "Lain-lain"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Kas Sosial Arisan") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = jenis == "Masuk",
                        onClick = {
                            jenis = "Masuk"
                            kategori = "Iuran Kas Anggota"
                        },
                        label = { Text("🟢 Kas Masuk") }
                    )
                    FilterChip(
                        selected = jenis == "Keluar",
                        onClick = {
                            jenis = "Keluar"
                            kategori = "Konsumsi Pertemuan"
                        },
                        label = { Text("🔴 Kas Keluar") }
                    )
                }

                Text("Kategori:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kategoriOptions.forEach { opt ->
                        FilterChip(
                            selected = kategori == opt,
                            onClick = { kategori = opt },
                            label = { Text(opt, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { nominalStr = it },
                    label = { Text("Nominal (Rp) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deskripsi,
                    onValueChange = { deskripsi = it },
                    label = { Text("Keterangan / Rincian Pengeluaran *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tanggal,
                    onValueChange = { tanggal = it },
                    label = { Text("Tanggal Transaksi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val nom = nominalStr.toDoubleOrNull() ?: 0.0
                    if (nom > 0 && deskripsi.isNotBlank()) {
                        onSave(tanggal, jenis, kategori, deskripsi, nom)
                    }
                },
                enabled = deskripsi.isNotBlank() && (nominalStr.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// ----------------------------------------------------
// DIALOG: PENGATURAN ARISAN
// ----------------------------------------------------
@Composable
private fun ArisanConfigDialog(
    currentConfig: ArisanConfig,
    onDismiss: () -> Unit,
    onSave: (ArisanConfig) -> Unit,
    onResetDataDefault: () -> Unit
) {
    var namaArisan by remember { mutableStateOf(currentConfig.namaArisan) }
    var periodeAktifStr by remember { mutableStateOf(currentConfig.periodeAktif.toString()) }
    var iuranPokokStr by remember { mutableStateOf(currentConfig.iuranPokokPerSlot.toLong().toString()) }
    var iuranKasStr by remember { mutableStateOf(currentConfig.iuranKasPerSlot.toLong().toString()) }
    var jadwal by remember { mutableStateOf(currentConfig.jadwalPertemuanBerikutnya) }
    var waktu by remember { mutableStateOf(currentConfig.waktuPertemuanBerikutnya) }
    var lokasi by remember { mutableStateOf(currentConfig.lokasiPertemuanBerikutnya) }
    var ketua by remember { mutableStateOf(currentConfig.namaKetuaArisan) }
    var bendahara by remember { mutableStateOf(currentConfig.namaBendahara) }
    var infoRekening by remember { mutableStateOf(currentConfig.infoRekening) }
    var catatan by remember { mutableStateOf(currentConfig.catatanPertemuan) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Arisan Keluarga") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = namaArisan,
                    onValueChange = { namaArisan = it },
                    label = { Text("Nama Arisan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = periodeAktifStr,
                        onValueChange = { periodeAktifStr = it },
                        label = { Text("Putaran Ke") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waktu,
                        onValueChange = { waktu = it },
                        label = { Text("Waktu / Jam") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = iuranPokokStr,
                        onValueChange = { iuranPokokStr = it },
                        label = { Text("Iuran Pokok / Slot (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = iuranKasStr,
                        onValueChange = { iuranKasStr = it },
                        label = { Text("Kas Sosial / Slot (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = jadwal,
                    onValueChange = { jadwal = it },
                    label = { Text("Jadwal Hari / Tanggal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lokasi,
                    onValueChange = { lokasi = it },
                    label = { Text("Lokasi Pertemuan / Tuan Rumah") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ketua,
                        onValueChange = { ketua = it },
                        label = { Text("Ketua Arisan") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bendahara,
                        onValueChange = { bendahara = it },
                        label = { Text("Bendahara") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = infoRekening,
                    onValueChange = { infoRekening = it },
                    label = { Text("Rekening / No. Transfer / E-Wallet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Pengumuman / Catatan Pertemuan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onResetDataDefault,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kosongkan Semua Data Arisan")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentConfig.copy(
                        namaArisan = namaArisan.trim().ifBlank { "Arisan Keluarga besar singghara" },
                        periodeAktif = periodeAktifStr.toIntOrNull() ?: currentConfig.periodeAktif,
                        iuranPokokPerSlot = iuranPokokStr.toDoubleOrNull() ?: currentConfig.iuranPokokPerSlot,
                        iuranKasPerSlot = iuranKasStr.toDoubleOrNull() ?: currentConfig.iuranKasPerSlot,
                        jadwalPertemuanBerikutnya = jadwal.trim(),
                        waktuPertemuanBerikutnya = waktu.trim(),
                        lokasiPertemuanBerikutnya = lokasi.trim(),
                        namaKetuaArisan = ketua.trim(),
                        namaBendahara = bendahara.trim(),
                        infoRekening = infoRekening.trim(),
                        catatanPertemuan = catatan.trim()
                    )
                    onSave(updated)
                }
            ) {
                Text("Simpan Perubahan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

// ----------------------------------------------------
// HELPERS: TEXT TEMPLATES FOR WHATSAPP & RECEIPT
// ----------------------------------------------------
private fun buildWhatsAppInvitation(
    config: ArisanConfig,
    lastWinner: RiwayatPemenangArisan?,
    totalTarikan: Double
): String {
    val totalPerSlot = config.iuranPokokPerSlot + config.iuranKasPerSlot
    return buildString {
        appendLine("🌺 *UNDANGAN ARISAN ${config.namaArisan.uppercase()}* 🌺")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("Kepada Yth. Seluruh Anggota Keluarga Besar,")
        appendLine("Kami mengundang bapak/ibu/saudara untuk hadir pada pertemuan arisan:")
        appendLine()
        if (config.jadwalPertemuanBerikutnya.isNotBlank()) {
            appendLine("🗓️ *Hari/Tanggal* : ${config.jadwalPertemuanBerikutnya}")
        }
        if (config.waktuPertemuanBerikutnya.isNotBlank()) {
            appendLine("⏰ *Pukul*        : ${config.waktuPertemuanBerikutnya} WIB s.d. Selesai")
        }
        if (config.lokasiPertemuanBerikutnya.isNotBlank()) {
            appendLine("🏡 *Tempat*       : ${config.lokasiPertemuanBerikutnya}")
        }
        appendLine()
        appendLine("📌 *Rincian Iuran per Slot:*")
        appendLine("• Iuran Pokok   : ${formatRupiah(config.iuranPokokPerSlot)}")
        appendLine("• Kas Sosial    : ${formatRupiah(config.iuranKasPerSlot)}")
        appendLine("• *Total / Slot : ${formatRupiah(totalPerSlot)}*")
        if (totalTarikan > 0) {
            appendLine()
            appendLine("🎁 *Perkiraan Tarikan Pemenang Putaran Ini:*")
            appendLine("👉 *${formatRupiah(totalTarikan)}*")
        }
        if (lastWinner != null) {
            appendLine()
            appendLine("🏆 *Pemenang Putaran Lalu (${lastWinner.periode}):*")
            appendLine("${lastWinner.namaPemenang} (${lastWinner.hubunganKeluarga})")
        }
        if (config.catatanPertemuan.isNotBlank()) {
            appendLine()
            appendLine("💡 *Catatan:* ${config.catatanPertemuan}")
        }
        appendLine()
        appendLine("Mohon hadir tepat waktu. Kehadiran keluarga sangat kami nantikan!")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        if (config.namaKetuaArisan.isNotBlank() || config.namaBendahara.isNotBlank()) {
            appendLine("Ketua: ${config.namaKetuaArisan.ifBlank { "-" }} | Bendahara: ${config.namaBendahara.ifBlank { "-" }}")
        }
    }
}

private fun buildReceiptText(riwayat: RiwayatPemenangArisan, config: ArisanConfig): String {
    val terbilang = TerbilangHelper.konversi(riwayat.nominalTarikan.toLong())
    return buildString {
        appendLine("📄 *KUITANSI PENYERAHAN UANG ARISAN*")
        appendLine("🏢 *${config.namaArisan}*")
        appendLine("No. Bukti: ${riwayat.nomorKuitansi}")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("Telah diserahkan uang tarikan arisan:")
        appendLine("• *Putaran Ke*     : ${riwayat.periode}")
        appendLine("• *Penerima*       : ${riwayat.namaPemenang} (${riwayat.hubunganKeluarga})")
        appendLine("• *Nominal*        : ${formatRupiah(riwayat.nominalTarikan)}")
        appendLine("• *Terbilang*      : _\"$terbilang\"_")
        appendLine("• *Tanggal*        : ${riwayat.tanggalPengundian}")
        appendLine("• *Keperluan*      : Uang Tarikan Pemenang Arisan Keluarga Putaran ${riwayat.periode}")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("Diserahkan oleh Bendahara: *${config.namaBendahara.ifBlank { "Bendahara" }}*")
        appendLine("Diterima oleh Pemenang: *${riwayat.namaPemenang}*")
        appendLine("Status: *LUNAS & RESMI* ✅")
    }
}
