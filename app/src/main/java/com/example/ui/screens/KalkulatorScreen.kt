package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.lightSurfaceShadow
import com.example.ui.theme.lightWhiteCard
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

private val AccentBlue = Color(0xFF0EA5E9)

private enum class CalcMode { BIASA, ILMIAH }

private enum class AngleUnit { DEG, RAD, GRAD }

private fun AngleUnit.next(): AngleUnit = when (this) {
    AngleUnit.DEG -> AngleUnit.RAD
    AngleUnit.RAD -> AngleUnit.GRAD
    AngleUnit.GRAD -> AngleUnit.DEG
}

// label = teks pada tombol, token = teks yang masuk ke ekspresi.
// altLabel/altToken dipakai ketika lapisan SHIFT aktif.
private data class SciKey(
    val label: String,
    val token: String,
    val altLabel: String? = null,
    val altToken: String = token
)

private data class KeyCap(val label: String, val token: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalkulatorScreen(
    viewModel: MainViewModel? = null,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var expression by remember { mutableStateOf("") }
    var prevFormula by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf("") }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var errorTick by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(CalcMode.BIASA) }
    var unit by remember { mutableStateOf(AngleUnit.DEG) }
    var shiftOn by remember { mutableStateOf(false) }

    var ansVal by remember { mutableStateOf(0.0) }
    var memVal by remember { mutableStateOf<Double?>(null) }
    var varA by remember { mutableStateOf(0.0) }
    var stats by remember { mutableStateOf(listOf<Double>()) }
    var hint by remember { mutableStateOf("") }
    // Setelah aksi yang memakai nilai (M+, M-, STO, D+, tombol statistik),
    // angka berikutnya harus memulai entri baru, bukan menempel.
    var freshEntry by remember { mutableStateOf(false) }

    val sciKeys = remember {
        listOf(
            listOf(
                SciKey("SHIFT", "SHIFT"),
                SciKey("x²", "²", "2ˣ", "2^("),
                SciKey("x³", "³", "3ˣ", "3^("),
                SciKey("xʸ", "^(", "logb(", "logb("),
                SciKey("√", "√(", "|x|", "abs(")
            ),
            listOf(
                SciKey("sin", "sin(", "sin⁻¹", "asin("),
                SciKey("cos", "cos(", "cos⁻¹", "acos("),
                SciKey("tan", "tan(", "tan⁻¹", "atan("),
                SciKey("log", "log(", "10ˣ", "10^("),
                SciKey("ln", "ln(", "eˣ", "e^(")
            ),
            listOf(
                SciKey("sinh", "sinh(", "asinh", "asinh("),
                SciKey("cosh", "cosh(", "acosh", "acosh("),
                SciKey("tanh", "tanh(", "atanh", "atanh("),
                SciKey("ⁿ√", "root("),
                SciKey("∛", "∛(")
            ),
            listOf(
                SciKey("(", "("),
                SciKey(")", ")"),
                SciKey("π", "π"),
                SciKey("e", "e"),
                SciKey("EXP", "×10^(")
            ),
            listOf(
                SciKey("1/x", "⁻¹"),
                SciKey("x!", "!"),
                SciKey(",", ","),
                SciKey("mod", "mod("),
                SciKey("ANS", "Ans")
            )
        )
    }

    // Nilai persis hasil terakhir, dipakai saat pengguna melanjutkan perhitungan
    // supaya tidak muncul galat pembulatan (1/3 lalu x3 tetap 1).
    fun carrySource(expr: String): String {
        val raw = lastRaw
        return if (raw != null && lastResult.isNotEmpty() && expr.startsWith(lastResult)) {
            raw + expr.removePrefix(lastResult)
        } else {
            expr
        }
    }

    fun evalNow(): Double? {
        val src = carrySource(expression)
        if (src.isBlank()) return null
        return runCatching { evaluateExpression(src, unit, ansVal, memVal ?: 0.0, varA) }
            .getOrNull()?.takeIf { it.isFinite() }
    }

    val live = remember(expression, unit, lastRaw, ansVal, memVal, varA) { evalNow() }
    val liveText = if (live != null) formatCalcResult(live) else ""
    val liveFraction = if (live != null) fractionHint(live) ?: "" else ""

    // 0 = tombol biasa, 1 = gagal, 2 = hasil
    fun handleKey(token: String): Int {
        when (token) {
            "C" -> {
                expression = ""
                prevFormula = ""
                lastResult = ""
                lastRaw = null
                error = false
                hint = ""
                freshEntry = false
            }
            "BS" -> {
                lastRaw = null
                hint = ""
                freshEntry = false
                if (error) {
                    expression = ""
                    error = false
                } else {
                    expression = dropOneToken(expression)
                }
            }
            "EQ" -> {
                val expr = prepareExpression(carrySource(expression))
                val result = if (expr.isBlank()) null
                else runCatching { evaluateExpression(expr, unit, ansVal, memVal ?: 0.0, varA) }.getOrNull()
                if (result != null && result.isFinite()) {
                    val formatted = formatCalcResult(result)
                    if (expression.isNotBlank()) prevFormula = "$expression ="
                    ansVal = result
                    expression = formatted
                    lastResult = formatted
                    lastRaw = result.toString()
                    error = false
                    hint = ""
                    shiftOn = false
                    freshEntry = false
                    return 2
                }
                if (expr.isNotBlank()) {
                    error = true
                    errorTick++
                }
                return 1
            }
            "MC" -> {
                memVal = null
                hint = "Memori kosong"
            }
            "M+" -> {
                val v = evalNow() ?: return 1
                val total = (memVal ?: 0.0) + v
                memVal = total
                hint = "M = ${formatCalcResult(total)}"
                freshEntry = true
            }
            "M-" -> {
                val v = evalNow() ?: return 1
                val total = (memVal ?: 0.0) - v
                memVal = total
                hint = "M = ${formatCalcResult(total)}"
                freshEntry = true
            }
            "STO" -> {
                val v = evalNow() ?: return 1
                varA = v
                hint = "Simpan A = ${formatCalcResult(v)}"
                freshEntry = true
            }
            "D+" -> {
                val v = evalNow() ?: return 1
                stats = stats + v
                hint = "Data n = ${stats.size}"
                freshEntry = true
            }
            "CL" -> {
                stats = emptyList()
                hint = "Data dihapus"
            }
            "SHIFT" -> shiftOn = !shiftOn
            else -> {
                if (error) {
                    expression = ""
                    error = false
                }
                hint = ""
                // ketikan baru setelah hasil: mulai dari nol, bukan menempel
                if (lastRaw != null && expression == lastResult && startsOperand(token)) {
                    expression = ""
                    lastResult = ""
                    lastRaw = null
                }
                if (freshEntry && startsOperand(token)) {
                    expression = ""
                    lastResult = ""
                    lastRaw = null
                }
                freshEntry = false
                if (token in statKeys) {
                    val v = statValue(token, stats) ?: return 1
                    expression += formatCalcResult(v)
                    freshEntry = true
                } else {
                    val tail = if (expression.isEmpty()) null else expression[expression.length - 1]
                    // "Ans" lalu fungsi tidak boleh menyatu jadi kata baru
                    if (tail != null && isAsciiLetter(tail) && isAsciiLetter(token.firstOrNull() ?: ' ')) {
                        expression += "×"
                    }
                    expression += token
                }
            }
        }
        if (token != "SHIFT") shiftOn = false
        return 0
    }

    val view = LocalView.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("calc_prefs", Context.MODE_PRIVATE) }
    var soundOn by remember { mutableStateOf(prefs.getBoolean("key_sound", true)) }
    LaunchedEffect(soundOn) { prefs.edit().putBoolean("key_sound", soundOn).apply() }

    fun feedback(code: Int) {
        if (soundOn) {
            val effect = when (code) {
                1 -> AudioManager.FX_KEYPRESS_INVALID
                2 -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEY_CLICK
            }
            view.playSoundEffect(effect)
        }
        view.performHapticFeedback(
            if (code == 1) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    fun press(token: String) {
        feedback(handleKey(token))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        // Putaran layar: lanskap -> ilmiah, kembali ke potret -> biasa
        var lastLandscape by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(isLandscape) {
            val prev = lastLandscape
            lastLandscape = isLandscape
            if (prev != null && prev != isLandscape) {
                mode = if (isLandscape) CalcMode.ILMIAH else CalcMode.BIASA
            }
        }

        val scientific = mode == CalcMode.ILMIAH

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Kalkulator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (stats.isNotEmpty()) {
                    Text(
                        "n=${stats.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                MiniToolButton(
                    onClick = { soundOn = !soundOn },
                    active = soundOn,
                    contentDescription = "Efek suara dan getar"
                ) { fg ->
                    Icon(
                        imageVector = if (soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isLandscape && scientific) {
                // Lanskap ilmiah: panel fungsi di kiri, papan angka di kanan
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MemStrip(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxWidth(),
                            memActive = memVal != null,
                            onKey = { press(it) }
                        )
                        SciKeyPanel(
                            modifier = Modifier
                                .weight(3.4f)
                                .fillMaxWidth(),
                            keys = sciKeys,
                            shiftOn = shiftOn,
                            onKey = { press(it) }
                        )
                        StatsStrip(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxWidth(),
                            hasData = stats.isNotEmpty(),
                            onKey = { press(it) }
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.4f)
                            .fillMaxHeight()
                            .padding(start = 8.dp)
                    ) {
                        DisplayCard(
                            expression = expression,
                            prevFormula = prevFormula,
                            liveResult = liveText,
                            fraction = liveFraction,
                            hint = hint,
                            lastResult = lastResult,
                            error = error,
                            errorTick = errorTick,
                            scientific = true,
                            unit = unit,
                            memActive = memVal != null,
                            varActive = varA != 0.0,
                            shiftOn = shiftOn,
                            onToggleMode = { mode = CalcMode.BIASA },
                            onCycleUnit = { unit = unit.next() }
                        )
                        MainKeypad(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 6.dp),
                            onKey = { press(it) }
                        )
                    }
                }
            } else {
                DisplayCard(
                    expression = expression,
                    prevFormula = prevFormula,
                    liveResult = liveText,
                    fraction = liveFraction,
                    hint = hint,
                    lastResult = lastResult,
                    error = error,
                    errorTick = errorTick,
                    scientific = scientific,
                    unit = unit,
                    memActive = memVal != null,
                    varActive = varA != 0.0,
                    shiftOn = shiftOn,
                    onToggleMode = {
                        mode = if (mode == CalcMode.BIASA) CalcMode.ILMIAH else CalcMode.BIASA
                    },
                    onCycleUnit = { unit = unit.next() }
                )
                if (scientific) {
                    Spacer(modifier = Modifier.height(6.dp))
                    MemStrip(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxWidth(),
                        memActive = memVal != null,
                        onKey = { press(it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SciKeyPanel(
                        modifier = Modifier
                            .weight(3.9f)
                            .fillMaxWidth(),
                        keys = sciKeys,
                        shiftOn = shiftOn,
                        onKey = { press(it) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    StatsStrip(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxWidth(),
                        hasData = stats.isNotEmpty(),
                        onKey = { press(it) }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                MainKeypad(
                    modifier = Modifier
                        .weight(if (scientific) 5f else 7.2f)
                        .fillMaxWidth(),
                    onKey = { press(it) }
                )
            }
        }
    }
}

@Composable
private fun DisplayCard(
    expression: String,
    prevFormula: String,
    liveResult: String,
    fraction: String,
    hint: String,
    lastResult: String,
    error: Boolean,
    errorTick: Int,
    scientific: Boolean,
    unit: AngleUnit,
    memActive: Boolean,
    varActive: Boolean,
    shiftOn: Boolean,
    onToggleMode: () -> Unit,
    onCycleUnit: () -> Unit
) {
    // Geser kiri-kanan singkat saat pernyataan salah
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorTick) {
        if (errorTick > 0) {
            shake.snapTo(0f)
            shake.animateTo(-9f, tween(45))
            shake.animateTo(9f, tween(70))
            shake.animateTo(-5f, tween(70))
            shake.animateTo(0f, tween(60))
        }
    }
    // Hasil muncul dengan efek pop kecil
    val pop = remember { Animatable(1f) }
    LaunchedEffect(lastResult) {
        if (lastResult.isNotEmpty()) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = lightWhiteCard(AccentBlue.copy(alpha = 0.08f)),
        shadowElevation = lightSurfaceShadow()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shiftOn) {
                    ChipText("SHIFT")
                    Spacer(modifier = Modifier.width(5.dp))
                }
                if (memActive) {
                    ChipText("M")
                    Spacer(modifier = Modifier.width(5.dp))
                }
                if (varActive) {
                    ChipText("A")
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                MiniToolButton(
                    onClick = onToggleMode,
                    active = scientific,
                    contentDescription = "Mode ilmiah"
                ) { fg ->
                    Icon(
                        Icons.Default.Functions,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (scientific) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MiniToolButton(
                        onClick = onCycleUnit,
                        active = unit != AngleUnit.RAD,
                        contentDescription = "Derajat, radian, atau gradian"
                    ) { fg ->
                        Text(unit.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }

            // Baris 1: rumus yang sudah dihitung
            if (prevFormula.isNotEmpty()) {
                val prevScroll = rememberScrollState()
                LaunchedEffect(prevFormula) { prevScroll.scrollTo(prevScroll.maxValue) }
                Text(
                    text = prevFormula,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(prevScroll)
                )
            }

            // Baris 2: ekspresi yang sedang diketik
            val scrollState = rememberScrollState()
            LaunchedEffect(expression) { scrollState.scrollTo(scrollState.maxValue) }
            Text(
                text = expression.ifBlank { lastResult.ifBlank { "0" } },
                fontSize = when {
                    expression.length > 26 -> 18.sp
                    expression.length > 18 -> 23.sp
                    else -> 29.sp
                },
                fontWeight = FontWeight.Bold,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .offset { IntOffset(shake.value.toInt(), 0) }
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            )

            // Baris 3: keterangan memori/statistik dan bentuk pecahan
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = AccentBlue,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (fraction.isNotEmpty()) {
                Text(
                    text = "= $fraction",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }

            // Baris terakhir: hasil
            val outScroll = rememberScrollState()
            LaunchedEffect(liveResult) { outScroll.scrollTo(outScroll.maxValue) }
            Text(
                text = when {
                    error -> "Pernyataan salah"
                    expression.isBlank() -> " "
                    liveResult.isNotBlank() && liveResult != expression -> "= $liveResult"
                    else -> " "
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .scale(pop.value)
                    .fillMaxWidth()
                    .horizontalScroll(outScroll)
            )
        }
    }
}

@Composable
private fun ChipText(text: String) {
    val fg = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fg.copy(alpha = 0.12f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun MiniToolButton(
    onClick: () -> Unit,
    active: Boolean,
    contentDescription: String,
    content: @Composable (Color) -> Unit
) {
    val fg = if (active) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AccentBlue.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClickLabel = contentDescription) { onClick() }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        content(fg)
    }
}

@Composable
private fun SciKeyPanel(
    modifier: Modifier = Modifier,
    keys: List<List<SciKey>>,
    shiftOn: Boolean,
    onKey: (String) -> Unit
) {
    val neutralBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { key ->
                    val label = if (shiftOn) key.altLabel ?: key.label else key.label
                    val token = if (shiftOn) key.altToken else key.token
                    val isShift = key.token == "SHIFT"
                    CalcKey(
                        label = label,
                        modifier = Modifier.weight(1f),
                        bg = when {
                            isShift && shiftOn -> AccentBlue
                            isShift -> AccentBlue.copy(alpha = 0.18f)
                            else -> neutralBg
                        },
                        fg = if (isShift && shiftOn) Color.White else AccentBlue,
                        fontSize = if (label.length > 4) 11 else 13,
                        onClick = { onKey(token) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemStrip(
    modifier: Modifier = Modifier,
    memActive: Boolean,
    onKey: (String) -> Unit
) {
    val keys = remember {
        listOf(
            KeyCap("MC", "MC"), KeyCap("MR", "M"), KeyCap("M+", "M+"),
            KeyCap("M−", "M-"), KeyCap("STO", "STO"), KeyCap("RCL", "A")
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        keys.forEach { cap ->
            CalcKey(
                label = cap.label,
                modifier = Modifier.weight(1f),
                bg = if (cap.token == "M" && memActive) AccentBlue.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                fg = AccentBlue,
                fontSize = 12,
                onClick = { onKey(cap.token) }
            )
        }
    }
}

@Composable
private fun StatsStrip(
    modifier: Modifier = Modifier,
    hasData: Boolean,
    onKey: (String) -> Unit
) {
    val keys = remember {
        listOf(
            KeyCap("D+", "D+"), KeyCap("n", "n"), KeyCap("Σx", "sum"),
            KeyCap("Σx²", "sumsq"), KeyCap("x̄", "mean"), KeyCap("σx", "sigp"),
            KeyCap("sx", "sigs"), KeyCap("CL", "CL")
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { cap ->
            CalcKey(
                label = cap.label,
                modifier = Modifier.weight(1f),
                bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                fg = if (hasData) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11,
                onClick = { onKey(cap.token) }
            )
        }
    }
}

@Composable
private fun MainKeypad(modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val opBg = AccentBlue.copy(alpha = 0.14f)
    val opFg = AccentBlue
    val numBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val numFg = MaterialTheme.colorScheme.onSurface
    val clearBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KeypadRow(modifier = Modifier.weight(1f)) {
            CalcKey("C", modifier = Modifier.weight(1f), bg = clearBg, fg = MaterialTheme.colorScheme.error, onClick = { onKey("C") })
            CalcKey("⌫", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("BS") })
            CalcKey("%", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("%") })
            CalcKey("÷", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("÷") })
        }
        KeypadRow(modifier = Modifier.weight(1f)) {
            CalcKey("7", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("7") })
            CalcKey("8", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("8") })
            CalcKey("9", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("9") })
            CalcKey("×", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("×") })
        }
        KeypadRow(modifier = Modifier.weight(1f)) {
            CalcKey("4", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("4") })
            CalcKey("5", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("5") })
            CalcKey("6", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("6") })
            CalcKey("−", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("−") })
        }
        KeypadRow(modifier = Modifier.weight(1f)) {
            CalcKey("1", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("1") })
            CalcKey("2", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("2") })
            CalcKey("3", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("3") })
            CalcKey("+", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("+") })
        }
        KeypadRow(modifier = Modifier.weight(1f)) {
            CalcKey("0", modifier = Modifier.weight(2f), bg = numBg, fg = numFg, onClick = { onKey("0") })
            CalcKey(".", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey(".") })
            CalcKey("=", modifier = Modifier.weight(1f), bg = AccentBlue, fg = Color.White, fontSize = 30, onClick = { onKey("EQ") })
        }
    }
}

@Composable
private fun KeypadRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun CalcKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.surfaceVariant,
    fg: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: Int = 26
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 900f),
        label = "keyScale"
    )
    val tint = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .background(if (pressed) tint.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// --- mesin hitung -------------------------------------------------------

private fun formatCalcResult(v: Double): String {
    // Pembulatan 11 angka penting: menghilangkan noise seperti 0.49999999999999994
    val rounded = if (v == 0.0 || !v.isFinite() || abs(v) >= 1e15) {
        v
    } else {
        val magnitude = Math.log10(abs(v))
        val decimals = (11.0 - magnitude).coerceIn(0.0, 15.0).toInt()
        val factor = Math.pow(10.0, decimals.toDouble())
        Math.round(v * factor) / factor
    }
    return if (rounded == floor(rounded) && abs(rounded) < 1e15) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

// Bentuk pecahan untuk desimal yang memang rasional sederhana,
// mis. 1÷3 -> 1/3. Penyebut dijaga <= 999 supaya tidak menyesatkan.
private fun fractionHint(v: Double): String? {
    if (!v.isFinite()) return null
    val x = abs(v)
    if (x > 1e6) return null
    val fracPart = x - floor(x)
    if (fracPart < 1e-9 || 1.0 - fracPart < 1e-9) return null
    var bestDen = 0
    var bestErr = Double.MAX_VALUE
    var d = 2
    while (d <= 999) {
        val n = Math.round(x * d)
        val err = abs(x * d - n) / d
        if (err < bestErr) {
            bestErr = err
            bestDen = d
        }
        d++
    }
    if (bestErr > 1e-9 || bestDen < 2) return null
    val num = Math.round(x * bestDen)
    if (num % bestDen == 0L) return null
    val whole = num / bestDen
    val rest = num % bestDen
    val sign = if (v < 0) "-" else ""
    return if (whole > 0 && rest > 0) "$sign$whole $rest/$bestDen" else "$sign$num/$bestDen"
}

private val statKeys = setOf("n", "sum", "sumsq", "mean", "sigp", "sigs")

private fun statValue(kind: String, data: List<Double>): Double? = when (kind) {
    "n" -> data.size.toDouble()
    "sum" -> data.sum()
    "sumsq" -> data.sumOf { it * it }
    "mean" -> if (data.isEmpty()) null else data.average()
    "sigp" -> {
        if (data.isEmpty()) null
        else {
            val m = data.average()
            sqrt(data.sumOf { (it - m) * (it - m) } / data.size)
        }
    }
    "sigs" -> {
        if (data.size < 2) null
        else {
            val m = data.average()
            sqrt(data.sumOf { (it - m) * (it - m) } / (data.size - 1))
        }
    }
    else -> null
}

private val binaryOps = setOf('+', '−', '×', '÷', '^')

private fun isBinaryOp(c: Char?): Boolean = c != null && c in binaryOps

// π dan akar bukan bagian dari nama fungsi, jadi cukup huruf ASCII.
private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

// Kata yang bernilai sendiri: tidak boleh ditambahi kurung oleh normalizer.
private val constantWords = setOf("e", "M", "A", "Ans")

// Tombol yang membuka ekspresi baru (angka, kurung buka, fungsi, konstanta).
private fun startsOperand(token: String): Boolean {
    val first = token.firstOrNull() ?: return false
    return first.isDigit() || first == '(' || first == '.' ||
        first == 'π' || first == '√' || first == '∛' || isAsciiLetter(first)
}

// Hapus satu ketukan terakhir, termasuk nama fungsi yang panjang.
private val multiCharTokens = listOf(
    "×10^(", "asinh(", "acosh(", "atanh(", "asin(", "acos(", "atan(",
    "sinh(", "cosh(", "tanh(", "root(", "logb(", "sin(", "cos(", "tan(",
    "log(", "abs(", "mod(", "ln(", "√(", "∛(", "10^(", "e^(", "Ans", "⁻¹"
)

private fun dropOneToken(expr: String): String {
    val hit = multiCharTokens.firstOrNull { expr.endsWith(it) }
    return if (hit != null) expr.dropLast(hit.length) else expr.dropLast(1)
}

// Menerima variasi tanda yang mungkin muncul: x/X/* sebagai kali, : atau /
// sebagai bagi, minus unicode, dan kurung lebar penuh.
private fun normalizeExpression(raw: String): String {
    val mapped = StringBuilder(raw.length)
    for (ch in raw) {
        mapped.append(
            when (ch) {
                'x', 'X', '*', '∗', '·', '⋅', '＊' -> '×'
                '/', ':', '∕', '／' -> '÷'
                '–', '—', '－' -> '−'
                '＋' -> '+'
                '（' -> '('
                '）' -> ')'
                else -> ch
            }
        )
    }
    val s = mapped.toString()

    val out = StringBuilder(s.length + 4)
    var prev: Char? = null

    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (isAsciiLetter(c)) {
            val start = i
            while (i < s.length && isAsciiLetter(s[i])) i++
            val word = s.substring(start, i)
            out.append(word)
            prev = word[word.length - 1]
            if (word !in constantWords) {
                var j = i
                while (j < s.length && s[j] == ' ') j++
                if (j >= s.length || s[j] != '(') {
                    out.append('(')
                    prev = '('
                }
            }
            continue
        }
        // tanda titik ganda dalam satu angka ("6..6") cukup satu saja
        if (c == '.' && prev == '.') {
            i++
            continue
        }
        // dua operator berurutan: pakai yang terakhir ("6××6" -> "6×6")
        if (isBinaryOp(c) && isBinaryOp(prev) &&
            !((prev == '+' || prev == '−') && (c == '+' || c == '−'))
        ) {
            out.setCharAt(out.length - 1, c)
            prev = c
            i++
            continue
        }
        out.append(c)
        prev = c
        i++
    }

    var result = out.toString().replace("()", "")
    while (result.isNotEmpty() && (result[0] == '×' || result[0] == '÷' || result[0] == '^')) {
        result = result.drop(1)
    }
    return result
}

// Lengkapi ekspresi yang belum selesai: buang tanda menggantung, tutup kurung.
private fun prepareExpression(raw: String): String {
    if (raw.isBlank()) return ""
    var s = normalizeExpression(raw)
    while (s.isNotEmpty()) {
        val trimmed = s.trimEnd()
        val last = trimmed.lastOrNull()
        if (last == '(' || last == '.' || last == ',' || last == '√' ||
            last == '∛' || isBinaryOp(last)
        ) {
            s = trimmed.dropLast(1)
            continue
        }
        s = trimmed
        break
    }
    var open = s.count { it == '(' }
    var close = s.count { it == ')' }
    while (close > open && s.endsWith(")")) {
        s = s.dropLast(1)
        close--
    }
    open = s.count { it == '(' }
    close = s.count { it == ')' }
    if (open > close) s += ")".repeat(open - close)
    return s.trim()
}

private fun factorial(v: Double): Double {
    if (v < 0 || v != floor(v) || v > 170) throw IllegalArgumentException("faktorial tak valid")
    var r = 1.0
    var k = 2.0
    while (k <= v) { r *= k; k++ }
    return r
}

private fun sqrtOf(v: Double): Double {
    if (v < 0.0) throw IllegalArgumentException("akar bilangan negatif")
    return Math.sqrt(v)
}

private fun cubeRoot(v: Double): Double =
    if (v < 0) -Math.pow(-v, 1.0 / 3.0) else Math.pow(v, 1.0 / 3.0)

private fun nthRoot(v: Double, n: Double): Double {
    if (n == 0.0) throw IllegalArgumentException("indeks akar tidak boleh 0")
    if (v < 0.0) {
        val whole = floor(n)
        val oddInt = whole == n && whole.toInt() % 2 != 0
        if (!oddInt) throw IllegalArgumentException("akar bilangan negatif")
        return -Math.pow(-v, 1.0 / n)
    }
    return Math.pow(v, 1.0 / n)
}

private fun evaluateExpression(
    raw: String,
    unit: AngleUnit,
    ans: Double,
    mem: Double,
    varA: Double
): Double {
    val prepared = prepareExpression(raw)
    if (prepared.isBlank()) throw IllegalArgumentException("kosong")
    val v = ExpressionParser(prepared, unit, ans, mem, varA).parseAll()
    if (!v.isFinite()) throw ArithmeticException("hasil tak hingga")
    return v
}

private class ExpressionParser(
    private val s: String,
    private val unit: AngleUnit,
    private val ans: Double,
    private val mem: Double,
    private val varA: Double
) {
    private var pos = 0

    fun parseAll(): Double {
        val v = parseExpr()
        skipSpace()
        if (pos < s.length) throw IllegalArgumentException("sisa karakter")
        return v
    }

    private fun skipSpace() { while (pos < s.length && s[pos] == ' ') pos++ }

    private fun peek(): Char {
        skipSpace()
        return if (pos < s.length) s[pos] else ' '
    }

    private fun take(c: Char): Boolean {
        skipSpace()
        if (pos < s.length && s[pos] == c) { pos++; return true }
        return false
    }

    private fun parseExpr(): Double {
        var v = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { pos++; v += parseTerm() }
                '-', '−' -> { pos++; v -= parseTerm() }
                else -> return v
            }
        }
    }

    private fun parseTerm(): Double {
        var v = parseUnary()
        while (true) {
            when (peek()) {
                '*', '×' -> { pos++; v *= parseUnary() }
                '/', '÷' -> {
                    pos++
                    val d = parseUnary()
                    if (d == 0.0) throw ArithmeticException("pembagian nol")
                    v /= d
                }
                else -> {
                    if (startsPrimary()) v *= parseUnary() else return v
                }
            }
        }
    }

    private fun startsPrimary(): Boolean {
        skipSpace()
        if (pos >= s.length) return false
        val c = s[pos]
        return c.isDigit() || c == '.' || c == '(' || c == 'π' || c == '√' ||
            c == '∛' || c.isLetter()
    }

    private fun parseUnary(): Double {
        return when (peek()) {
            '-', '−' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            else -> parsePower()
        }
    }

    private fun parsePower(): Double {
        val base = parsePostfix()
        return if (take('^')) Math.pow(base, parseUnary()) else base
    }

    private fun parsePostfix(): Double {
        var v = parsePrimary()
        while (true) {
            when {
                peek() == '%' -> { pos++; v /= 100.0 }
                peek() == '!' -> { pos++; v = factorial(v) }
                peek() == '²' -> { pos++; v = v * v }
                peek() == '³' -> { pos++; v = v * v * v }
                peek() == '⁻' && pos + 1 < s.length && s[pos + 1] == '¹' -> {
                    pos += 2
                    if (v == 0.0) throw ArithmeticException("1/0 tidak terdefinisi")
                    v = 1.0 / v
                }
                else -> return v
            }
        }
    }

    private fun parsePrimary(): Double {
        skipSpace()
        if (pos >= s.length) throw IllegalArgumentException("ekspresi belum lengkap")
        val c = s[pos]
        if (c == '(') {
            pos++
            val v = parseExpr()
            if (!take(')')) throw IllegalArgumentException("kurung belum ditutup")
            return v
        }
        if (c == '√') { pos++; return sqrtOf(parsePostfix()) }
        if (c == '∛') { pos++; return cubeRoot(parsePostfix()) }
        if (c == 'π') { pos++; return Math.PI }
        if (c.isLetter()) {
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            val word = s.substring(start, pos)
            val args = readFunctionArgs(word)
            return applyFunction(word, args)
        }
        if (c.isDigit() || c == '.') return readNumber()
        throw IllegalArgumentException("karakter tak dikenal: $c")
    }

    private fun readFunctionArgs(word: String): List<Double> {
        if (word in constantWords) return emptyList()
        if (!take('(')) throw IllegalArgumentException("$word butuh ( )")
        val args = mutableListOf(parseExpr())
        while (take(',')) args.add(parseExpr())
        if (!take(')')) throw IllegalArgumentException("kurung $word belum ditutup")
        return args
    }

    private fun one(args: List<Double>, word: String): Double {
        if (args.size != 1) throw IllegalArgumentException("$word butuh 1 angka")
        return args[0]
    }

    private fun two(args: List<Double>, word: String): Pair<Double, Double> {
        if (args.size != 2) throw IllegalArgumentException("$word butuh 2 angka (pisahkan koma)")
        return Pair(args[0], args[1])
    }

    private fun toRad(a: Double): Double = when (unit) {
        AngleUnit.DEG -> Math.toRadians(a)
        AngleUnit.GRAD -> a * (Math.PI / 200.0)
        AngleUnit.RAD -> a
    }

    private fun fromRad(r: Double): Double = when (unit) {
        AngleUnit.DEG -> Math.toDegrees(r)
        AngleUnit.GRAD -> r * (200.0 / Math.PI)
        AngleUnit.RAD -> r
    }

    private fun applyFunction(word: String, args: List<Double>): Double {
        return when (word) {
            "e" -> Math.E
            "Ans" -> ans
            "M" -> mem
            "A" -> varA
            "sin" -> Math.sin(toRad(one(args, word)))
            "cos" -> Math.cos(toRad(one(args, word)))
            "tan" -> Math.tan(toRad(one(args, word)))
            "asin" -> {
                val a = one(args, word)
                if (a < -1.0 || a > 1.0) throw IllegalArgumentException("sin⁻¹ rentang -1..1")
                fromRad(Math.asin(a))
            }
            "acos" -> {
                val a = one(args, word)
                if (a < -1.0 || a > 1.0) throw IllegalArgumentException("cos⁻¹ rentang -1..1")
                fromRad(Math.acos(a))
            }
            "atan" -> fromRad(Math.atan(one(args, word)))
            "sinh" -> Math.sinh(one(args, word))
            "cosh" -> Math.cosh(one(args, word))
            "tanh" -> Math.tanh(one(args, word))
            "asinh" -> {
                val a = one(args, word)
                Math.log(a + sqrt(a * a + 1.0))
            }
            "acosh" -> {
                val a = one(args, word)
                if (a < 1.0) throw IllegalArgumentException("cosh⁻¹ butuh angka >= 1")
                Math.log(a + sqrt(a * a - 1.0))
            }
            "atanh" -> {
                val a = one(args, word)
                if (a <= -1.0 || a >= 1.0) throw IllegalArgumentException("tanh⁻¹ rentang -1..1")
                0.5 * Math.log((1.0 + a) / (1.0 - a))
            }
            "ln" -> {
                val a = one(args, word)
                if (a <= 0.0) throw IllegalArgumentException("ln butuh bilangan > 0")
                Math.log(a)
            }
            "log" -> {
                val a = one(args, word)
                if (a <= 0.0) throw IllegalArgumentException("log butuh bilangan > 0")
                Math.log10(a)
            }
            "logb" -> {
                val pair = two(args, word)
                val base = pair.first
                val value = pair.second
                if (base <= 0.0 || base == 1.0) throw IllegalArgumentException("basis log salah")
                if (value <= 0.0) throw IllegalArgumentException("log butuh bilangan > 0")
                Math.log(value) / Math.log(base)
            }
            "abs" -> abs(one(args, word))
            "mod" -> {
                val pair = two(args, word)
                if (pair.second == 0.0) throw ArithmeticException("mod 0 tidak terdefinisi")
                pair.first % pair.second
            }
            "root" -> {
                val pair = two(args, word)
                nthRoot(pair.first, pair.second)
            }
            else -> throw IllegalArgumentException("fungsi tak dikenal: $word")
        }
    }

    private fun readNumber(): Double {
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        val token = s.substring(start, pos)
        if (token.count { it == '.' } > 1) throw IllegalArgumentException("dua titik desimal")
        return token.toDoubleOrNull() ?: throw IllegalArgumentException("angka tak valid: $token")
    }
}
