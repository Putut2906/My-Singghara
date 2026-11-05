package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import kotlin.math.abs
import kotlin.math.floor

private val AccentBlue = Color(0xFF0EA5E9)

private enum class CalcMode { BIASA, ILMIAH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalkulatorScreen(
    viewModel: MainViewModel? = null,
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var expression by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf("") }
    var lastRaw by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(CalcMode.BIASA) }
    var useDegrees by remember { mutableStateOf(true) }

    val sciKeyRows = remember {
        listOf(
            listOf("sin(", "cos(", "tan(", "√(", "^"),
            listOf("asin(", "acos(", "atan(", "ln(", "log("),
            listOf("(", ")", "π", "e", "!")
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

    fun handleKey(token: String) {
        when (token) {
            "C" -> { expression = ""; lastResult = ""; lastRaw = null; error = false }
            "BS" -> {
                lastRaw = null
                if (error) {
                    expression = ""
                    error = false
                } else {
                    val multiToken = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "log(", "ln(", "√(")
                        .firstOrNull { expression.endsWith(it) }
                    expression = if (multiToken != null) expression.dropLast(multiToken.length)
                    else expression.dropLast(1)
                }
            }
            "EQ" -> {
                val expr = prepareExpression(carrySource(expression))
                val result = if (expr.isBlank()) null
                else runCatching { evaluateExpression(expr, useDegrees) }.getOrNull()
                if (result != null && result.isFinite()) {
                    val formatted = formatCalcResult(result)
                    expression = formatted
                    lastResult = formatted
                    lastRaw = result.toString()
                    error = false
                } else if (expr.isNotBlank()) {
                    error = true
                }
            }
            else -> {
                if (error) {
                    expression = ""
                    error = false
                }
                // ketikan baru setelah hasil: mulai dari nol, bukan menempel
                if (lastRaw != null && expression == lastResult && startsOperand(token)) {
                    expression = ""
                    lastResult = ""
                    lastRaw = null
                }
                expression += token
            }
        }
    }

    val liveResult = remember(expression, useDegrees, lastRaw) {
        val src = carrySource(expression)
        if (src.isBlank()) ""
        else runCatching { formatCalcResult(evaluateExpression(src, useDegrees)) }.getOrDefault("")
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Kalkulator",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isLandscape && mode == CalcMode.ILMIAH) {
                // Lanskap ilmiah: panel fungsi di kiri, keypad di kanan
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SciKeyPanel(sciKeyRows) { handleKey(it) }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1.5f)
                            .fillMaxHeight()
                            .padding(start = 8.dp)
                    ) {
                        DisplayCard(
                            expression = expression,
                            liveResult = liveResult,
                            error = error,
                            lastResult = lastResult,
                            scientific = true,
                            useDegrees = useDegrees,
                            onToggleMode = { mode = CalcMode.BIASA },
                            onToggleUnit = { useDegrees = !useDegrees }
                        )
                        MainKeypad(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                            onKey = { handleKey(it) }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    DisplayCard(
                        expression = expression,
                        liveResult = liveResult,
                        error = error,
                        lastResult = lastResult,
                        scientific = mode == CalcMode.ILMIAH,
                        useDegrees = useDegrees,
                        onToggleMode = {
                            mode = if (mode == CalcMode.BIASA) CalcMode.ILMIAH else CalcMode.BIASA
                        },
                        onToggleUnit = { useDegrees = !useDegrees }
                    )
                    if (mode == CalcMode.ILMIAH) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SciKeyPanel(sciKeyRows) { handleKey(it) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MainKeypad(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        onKey = { handleKey(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayCard(
    expression: String,
    liveResult: String,
    lastResult: String,
    error: Boolean,
    scientific: Boolean,
    useDegrees: Boolean,
    onToggleMode: () -> Unit,
    onToggleUnit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AccentBlue.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Alat kecil di dalam papan: mode ilmiah + DEG/RAD
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        onClick = onToggleUnit,
                        active = false,
                        contentDescription = "Derajat atau radian"
                    ) { fg ->
                        Text(
                            if (useDegrees) "DEG" else "RAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }
                }
            }
            val scrollState = rememberScrollState()
            LaunchedEffect(expression) { scrollState.scrollTo(scrollState.maxValue) }
            Text(
                text = expression.ifBlank { lastResult.ifBlank { "0" } },
                fontSize = if (expression.length > 18) 22.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    error -> "Pernyataan salah"
                    expression.isBlank() -> " "
                    liveResult.isNotBlank() && liveResult != expression -> "= $liveResult"
                    else -> " "
                },
                fontSize = 14.sp,
                color = if (error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
private fun SciKeyPanel(rows: List<List<String>>, onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { key ->
                    CalcKey(
                        label = key,
                        modifier = Modifier.weight(1f),
                        bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        fg = AccentBlue,
                        fontSize = 13,
                        onClick = { onKey(key) }
                    )
                }
            }
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
            CalcKey("=", modifier = Modifier.weight(1f), bg = AccentBlue, fg = Color.White, onClick = { onKey("EQ") })
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
    fontSize: Int = 20
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

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

private val binaryOps = setOf('+', '−', '×', '÷', '^')

private fun isBinaryOp(c: Char?): Boolean = c != null && c in binaryOps

// π dan √ bukan bagian dari nama fungsi, jadi cukup huruf ASCII.
private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

// Tombol yang membuka ekspresi baru (angka, kurung buka, fungsi, konstanta).
private fun startsOperand(token: String): Boolean =
    token.endsWith("(") || token == "." || token == "π" || token == "e" ||
        (token.firstOrNull()?.isDigit() == true)

// Menerima variasi tanda yang mungkin muncul: x/X/* sebagai kali, : atau /
// sebagai bagi, minus unicode, koma desimal, dan kurung lebar penuh.
private fun normalizeExpression(raw: String): String {
    val mapped = StringBuilder(raw.length)
    for (ch in raw) {
        mapped.append(
            when (ch) {
                'x', 'X', '*', '∗', '·', '⋅', '＊' -> '×'
                '/', ':', '∕', '／' -> '÷'
                '–', '—', '－' -> '−'
                '＋' -> '+'
                ',' -> '.'
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
            if (word != "e") {
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
        if (last == '(' || last == '.' || isBinaryOp(last) || last == '√') {
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

private fun evaluateExpression(raw: String, degrees: Boolean): Double {
    val prepared = prepareExpression(raw)
    if (prepared.isBlank()) throw IllegalArgumentException("kosong")
    val v = ExpressionParser(prepared, degrees).parseAll()
    if (!v.isFinite()) throw ArithmeticException("hasil tak hingga")
    return v
}

private class ExpressionParser(private val s: String, private val degrees: Boolean) {
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
        return c.isDigit() || c == '.' || c == '(' || c == 'π' || c == '√' || c.isLetter()
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
            when (peek()) {
                '%' -> { pos++; v /= 100.0 }
                '!' -> { pos++; v = factorial(v) }
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
        if (c == '√') { pos++; return Math.sqrt(parsePostfix()) }
        if (c == 'π') { pos++; return Math.PI }
        if (c.isLetter()) {
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            val word = s.substring(start, pos)
            val arg = readFunctionArg(word)
            return applyFunction(word, arg)
        }
        if (c.isDigit() || c == '.') return readNumber()
        throw IllegalArgumentException("karakter tak dikenal: $c")
    }

    private fun readFunctionArg(word: String): Double {
        if (word == "e") return Math.E
        if (!take('(')) throw IllegalArgumentException("$word butuh ( )")
        val v = parseExpr()
        if (!take(')')) throw IllegalArgumentException("kurung $word belum ditutup")
        return v
    }

    private fun applyFunction(word: String, arg: Double): Double {
        val a = if (degrees) Math.toRadians(arg) else arg
        val r = when (word) {
            "sin" -> Math.sin(a)
            "cos" -> Math.cos(a)
            "tan" -> Math.tan(a)
            "asin" -> Math.asin(arg)
            "acos" -> Math.acos(arg)
            "atan" -> Math.atan(arg)
            "ln" -> if (arg <= 0.0) throw IllegalArgumentException("ln butuh bilangan > 0") else Math.log(arg)
            "log" -> if (arg <= 0.0) throw IllegalArgumentException("log butuh bilangan > 0") else Math.log10(arg)
            "e" -> Math.E
            else -> throw IllegalArgumentException("fungsi tak dikenal: $word")
        }
        return when (word) {
            "asin", "acos", "atan" -> if (degrees) Math.toDegrees(r) else r
            else -> r
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