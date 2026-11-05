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

    fun handleKey(token: String) {
        when (token) {
            "C" -> { expression = ""; error = false }
            "BS" -> {
                val multiToken = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "log(", "ln(", "√(")
                    .firstOrNull { expression.endsWith(it) }
                expression = if (multiToken != null) expression.dropLast(multiToken.length)
                else expression.dropLast(1)
                error = false
            }
            "EQ" -> {
                runCatching { evaluateExpression(expression, useDegrees) }
                    .onSuccess {
                        expression = formatCalcResult(it)
                        error = false
                    }
                    .onFailure { error = true }
            }
            else -> {
                expression += token
                error = false
            }
        }
    }

    val liveResult = remember(expression, useDegrees) {
        if (expression.isBlank()) ""
        else runCatching { formatCalcResult(evaluateExpression(expression, useDegrees)) }.getOrDefault("")
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
                Column {
                    Text(
                        "Kalkulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (mode == CalcMode.ILMIAH) "Mode Ilmiah (Scientific)" else "Mode Biasa",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Ikon khusus untuk mengaktifkan mode ilmiah
                IconButton(onClick = {
                    mode = if (mode == CalcMode.BIASA) CalcMode.ILMIAH else CalcMode.BIASA
                }) {
                    Icon(
                        Icons.Default.Functions,
                        contentDescription = "Ganti mode kalkulator",
                        tint = if (mode == CalcMode.ILMIAH) AccentBlue
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chip pilihan mode + DEG/RAD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalcMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = {
                            Text(
                                if (m == CalcMode.BIASA) "Biasa" else "Ilmiah",
                                fontSize = 12.sp
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (mode == CalcMode.ILMIAH) {
                    OutlinedButton(
                        onClick = { useDegrees = !useDegrees },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text(
                            if (useDegrees) "DEG" else "RAD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                            error = error
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
                        error = error
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
private fun DisplayCard(expression: String, liveResult: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AccentBlue.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val scrollState = rememberScrollState()
            LaunchedEffect(expression) { scrollState.scrollTo(scrollState.maxValue) }
            Text(
                text = expression.ifBlank { "0" },
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
        KeypadRow {
            CalcKey("C", modifier = Modifier.weight(1f), bg = clearBg, fg = MaterialTheme.colorScheme.error, onClick = { onKey("C") })
            CalcKey("⌫", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("BS") })
            CalcKey("%", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("%") })
            CalcKey("÷", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("÷") })
        }
        KeypadRow {
            CalcKey("7", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("7") })
            CalcKey("8", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("8") })
            CalcKey("9", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("9") })
            CalcKey("×", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("×") })
        }
        KeypadRow {
            CalcKey("4", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("4") })
            CalcKey("5", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("5") })
            CalcKey("6", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("6") })
            CalcKey("−", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("−") })
        }
        KeypadRow {
            CalcKey("1", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("1") })
            CalcKey("2", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("2") })
            CalcKey("3", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey("3") })
            CalcKey("+", modifier = Modifier.weight(1f), bg = opBg, fg = opFg, onClick = { onKey("+") })
        }
        KeypadRow {
            CalcKey("0", modifier = Modifier.weight(2f), bg = numBg, fg = numFg, onClick = { onKey("0") })
            CalcKey(".", modifier = Modifier.weight(1f), bg = numBg, fg = numFg, onClick = { onKey(".") })
            CalcKey("=", modifier = Modifier.weight(1f), bg = AccentBlue, fg = Color.White, onClick = { onKey("EQ") })
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
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
    return if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()
}

private fun factorial(v: Double): Double {
    if (v < 0 || v != floor(v) || v > 170) throw IllegalArgumentException("faktorial tak valid")
    var r = 1.0
    var k = 2.0
    while (k <= v) { r *= k; k++ }
    return r
}

private fun evaluateExpression(raw: String, degrees: Boolean): Double {
    if (raw.isBlank()) throw IllegalArgumentException("kosong")
    val v = ExpressionParser(raw, degrees).parseAll()
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
            val word = s.substring(start)
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
        val token = s.substring(start)
        if (token.count { it == '.' } > 1) throw IllegalArgumentException("dua titik desimal")
        return token.toDoubleOrNull() ?: throw IllegalArgumentException("angka tak valid: $token")
    }
}