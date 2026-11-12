package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Progress bar berbentuk ular yang merayap zig-zag antar baris.
 * Kepala ular bergerak mengikuti [progress] dan tubuh yang sudah dilalui
 * terisi warna, sedangkan jalur sisanya tetap sebagai trek samar.
 */
@Composable
fun ZigZagSnakeProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    rows: Int = 3,
    height: Dp = 42.dp,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headDotColor: Color = Color.White
) {
    val transition = rememberInfiniteTransition(label = "zigZagSnake")
    val slither by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "slither"
    )
    val headPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "headPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val sw = strokeWidth.toPx()
        val inset = sw / 2f + 1.dp.toPx()
        val left = inset
        val right = size.width - inset
        val top = inset
        val bottom = size.height - inset
        val safeRows = rows.coerceAtLeast(1)
        val rowGap = if (safeRows > 1) (bottom - top) / (safeRows - 1) else 0f

        if (right <= left) return@Canvas

        val points = ArrayList<Offset>(safeRows * 2)
        for (r in 0 until safeRows) {
            val rowY = top + rowGap * r
            val startX = if (r % 2 == 0) left else right
            val endX = if (r % 2 == 0) right else left
            points.add(Offset(startX, rowY))
            points.add(Offset(endX, rowY))
        }

        val track = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }

        drawPath(
            path = track,
            color = trackColor.copy(alpha = 0.85f),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Garis putus-putus yang bergerak memberi kesan tubuh ular merayap.
        val dashPhase = -slither * sw * 3.3f
        drawPath(
            path = track,
            color = color.copy(alpha = 0.22f),
            style = Stroke(
                width = sw,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(sw * 0.9f, sw * 2.4f), dashPhase)
            )
        )

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0f) return@Canvas

        val segLens = FloatArray(points.size - 1)
        var total = 0f
        for (i in segLens.indices) {
            segLens[i] = hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
            total += segLens[i]
        }
        if (total <= 0f) return@Canvas

        var remaining = total * clamped
        var head = points[0]
        val body = Path().apply { moveTo(points[0].x, points[0].y) }
        for (i in segLens.indices) {
            val len = segLens[i]
            if (remaining >= len) {
                body.lineTo(points[i + 1].x, points[i + 1].y)
                head = points[i + 1]
                remaining -= len
            } else {
                val t = if (len <= 0f) 0f else remaining / len
                head = Offset(
                    points[i].x + (points[i + 1].x - points[i].x) * t,
                    points[i].y + (points[i + 1].y - points[i].y) * t
                )
                body.lineTo(head.x, head.y)
                break
            }
        }

        drawPath(
            path = body,
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val headRadius = sw * (0.85f + 0.3f * headPulse)
        drawCircle(color = color.copy(alpha = 0.25f), radius = headRadius * 1.5f, center = head)
        drawCircle(color = color, radius = headRadius, center = head)
        drawCircle(color = headDotColor, radius = headRadius * 0.34f, center = head)
    }
}
