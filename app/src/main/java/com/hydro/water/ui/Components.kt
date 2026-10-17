package com.hydro.water.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** 圆形水波进度；content 拿到当前水位 (0~1)，可按文字所在高度切换颜色 */
@Composable
fun WaterBall(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(level: Float) -> Unit,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(1100), label = "level")
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase"
    )
    val bob by inf.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.primaryContainer
    val inner = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val ring = 8.dp.toPx()
            drawCircle(track, radius = r - ring / 2, style = Stroke(ring))
            // 外圈进度
            drawArc(
                brush = Brush.sweepGradient(listOf(secondary, primary, secondary)),
                startAngle = -90f, sweepAngle = 360f * p, useCenter = false,
                topLeft = Offset(ring / 2, ring / 2),
                size = androidx.compose.ui.geometry.Size(size.width - ring, size.height - ring),
                style = Stroke(ring, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
            val ir = r - ring - 8.dp.toPx()
            val clip = Path().apply { addOval(Rect(center, ir)) }
            clipPath(clip) {
                drawCircle(inner, ir)
                val top = center.y - ir
                val level = top + 2 * ir * (1 - p) + bob * 2.dp.toPx()
                val amp = if (p <= 0.001f || p >= 0.999f) 0f else 7.dp.toPx()
                fun wave(shift: Float, a: Float): Path = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) {
                        val y = level + a * sin(x / size.width * 2 * PI.toFloat() * 1.3f + phase + shift)
                        lineTo(x, y)
                        x += 4f
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(wave(PI.toFloat(), amp * 0.8f), primary.copy(alpha = 0.28f))
                drawPath(
                    wave(0f, amp),
                    Brush.verticalGradient(
                        listOf(secondary.copy(alpha = 0.85f), primary),
                        startY = level - amp, endY = center.y + ir
                    )
                )
            }
        }
        content(p)
    }
}

@Composable
fun underwaterColor(underwater: Boolean, normal: Color): Color =
    animateColorAsState(if (underwater) Color.White else normal, label = "txt").value

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

fun fmtMin(m: Int) = "%02d:%02d".format(m / 60, m % 60)

fun fmtMl(ml: Int): String = if (ml >= 10000) "%.1f L".format(ml / 1000f) else "$ml ml"
