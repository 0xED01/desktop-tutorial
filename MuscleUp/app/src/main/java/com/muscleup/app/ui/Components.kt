package com.muscleup.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.data.fmt

val Brand = Color(0xFFE8590C)
val BrandDark = Color(0xFFFF8A4C)
val ProteinColor = Color(0xFFD9480F)
val CarbColor = Color(0xFF1C7ED6)
val FatColor = Color(0xFFF59F00)
val GoodColor = Color(0xFF2F9E44)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF5A1E00),
    secondary = Color(0xFF495057),
    secondaryContainer = Color(0xFFEDE7E3),
    background = Color(0xFFF7F5F2),
    surface = Color(0xFFF7F5F2),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF1ECE8),
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF3A1300),
    primaryContainer = Color(0xFF6B2A05),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondaryContainer = Color(0xFF3A3532),
    background = Color(0xFF141211),
    surface = Color(0xFF141211),
    surfaceContainer = Color(0xFF211E1C),
    surfaceContainerLow = Color(0xFF211E1C),
    surfaceContainerHigh = Color(0xFF2C2826),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, trailing: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    trailing?.invoke()
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun Ring(progress: Float, color: Color, size: Int = 96, stroke: Float = 12f, center: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding((stroke / 2).dp)) {
            val st = Stroke(stroke.dp.toPx() / 1.5f, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = st)
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, style = st)
        }
        center()
    }
}

@Composable
fun MacroBar(label: String, value: Double, target: Int, color: Color, unit: String = "g") {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${value.fmt(0)} / $target $unit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (target <= 0) 0f else (value / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun StatBox(modifier: Modifier = Modifier, value: String, label: String, color: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = true, suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.isEmpty() || s.matches(Regex(if (decimal) "\\d*\\.?\\d*" else "\\d*"))) onChange(s) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** 简易折线图 */
@Composable
fun LineChart(points: List<Pair<String, Double>>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, target: Double? = null) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Text("至少记录 2 次数据后显示趋势图", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val values = points.map { it.second } + listOfNotNull(target)
    val min = values.min()
    val max = values.max()
    val pad = ((max - min) * 0.15).coerceAtLeast(0.5)
    val lo = min - pad
    val hi = max + pad
    Column(modifier) {
        Row {
            Column(Modifier.height(160.dp).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(hi.fmt(1), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text(((hi + lo) / 2).fmt(1), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text(lo.fmt(1), style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(160.dp)) {
                val w = size.width
                val h = size.height
                for (k in 0..2) {
                    val y = h * k / 2f
                    drawLine(grid, Offset(0f, y), Offset(w, y), 1f)
                }
                fun yOf(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
                if (target != null) {
                    val ty = yOf(target)
                    var x = 0f
                    while (x < w) {
                        drawLine(GoodColor, Offset(x, ty), Offset((x + 12f).coerceAtMost(w), ty), 3f)
                        x += 22f
                    }
                }
                val step = w / (points.size - 1)
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = i * step
                    val y = yOf(p.second)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(5f, cap = StrokeCap.Round))
                points.forEachIndexed { i, p ->
                    drawCircle(color, 7f, Offset(i * step, yOf(p.second)))
                }
            }
        }
        Row(Modifier.padding(start = 40.dp)) {
            Text(points.first().first, style = MaterialTheme.typography.labelSmall, color = labelColor, modifier = Modifier.weight(1f))
            Text(points.last().first, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
    }
}

/** 简易柱状图 */
@Composable
fun BarChart(bars: List<Pair<String, Double>>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, unit: String = "") {
    val max = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v > 0) v.fmt(0) + unit else "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.BottomCenter) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(track, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
                        val bh = (size.height * (v / max)).toFloat()
                        drawRoundRect(color, topLeft = Offset(0f, size.height - bh), size = Size(size.width, bh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
                    }
                }
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ListRow(title: String, subtitle: String? = null, leading: @Composable (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    val m = if (onClick != null) Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple(onClick) else Modifier.fillMaxWidth()
    Row(m.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
