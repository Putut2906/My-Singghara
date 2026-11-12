package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Indikator unduhan: cincin progres yang mengisi sesuai [progress],
 * titik orbit yang terus berputar sebagai spinner, dan panah unduh
 * yang bergerak naik-turun selama proses berjalan.
 */
@Composable
fun DownloadProgressSpinner(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    strokeWidth: Dp = 7.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val safeProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "downloadProgress"
    )

    val transition = rememberInfiniteTransition(label = "downloadSpinner")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "orbit"
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val diameter = minOf(this.size.width, this.size.height) - sw
            if (diameter <= 0f) return@Canvas
            val radius = diameter / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 359.9f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }

            // Titik orbit sebagai penanda spinner tetap berputar.
            val angle = Math.toRadians((orbit - 90f).toDouble())
            val dot = Offset(
                center.x + radius * cos(angle).toFloat(),
                center.y + radius * sin(angle).toFloat()
            )
            drawCircle(color = color.copy(alpha = 0.22f), radius = sw * 1.25f, center = dot)
            drawCircle(color = color, radius = sw * 0.68f, center = dot)

            // Panah unduh di tengah, bergerak halus naik-turun.
            drawCircle(color = color.copy(alpha = 0.08f), radius = radius * 0.72f, center = center)
            val shaft = sw * 0.55f
            val shift = (bob * 4f - 2f) * sw * 0.45f
            val topY = center.y - radius * 0.36f + shift
            val tipY = center.y + radius * 0.30f + shift
            val wing = radius * 0.30f
            drawLine(
                color = color,
                start = Offset(center.x, topY),
                end = Offset(center.x, tipY),
                strokeWidth = shaft,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x - wing, tipY - wing * 0.95f),
                end = Offset(center.x, tipY),
                strokeWidth = shaft,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(center.x + wing, tipY - wing * 0.95f),
                end = Offset(center.x, tipY),
                strokeWidth = shaft,
                cap = StrokeCap.Round
            )
        }
    }
}
