package com.hydro.water.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.water.data.DrinkTypes
import com.hydro.water.data.WaterRepo
import com.hydro.water.data.key
import com.hydro.water.ui.theme.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class StatsData(
    val days: List<LocalDate>,
    val values: List<Int>,
    val hourly: IntArray,
    val byType: Map<Int, Int>,
    val streak: Int,
    val bestStreak: Int,
    val lifetime: Int,
    val bestDay: Int,
    val reachedDaysAll: Int,
    val count: Int,
)

private data class Badge(val title: String, val desc: String, val unlocked: Boolean)

@Composable
fun StatsScreen() {
    val settings by WaterRepo.settings.collectAsStateWithLifecycle()
    val version by WaterRepo.version.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(0) }
    val span = if (range == 0) 7 else 30
    val goal = settings.goal
    val cs = MaterialTheme.colorScheme

    val data = remember(version, span, goal) {
        val today = LocalDate.now()
        val totals = WaterRepo.dailyTotals()
        val days = (span - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val records = WaterRepo.recordsBetween(days.first(), today)
        val hourly = IntArray(24)
        val byType = HashMap<Int, Int>()
        records.forEach {
            val h = Instant.ofEpochMilli(it.ts).atZone(ZoneId.systemDefault()).hour
            hourly[h] += it.effective
            byType[it.type] = (byType[it.type] ?: 0) + it.amount
        }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        totals.keys.sorted().forEach { k ->
            if ((totals[k] ?: 0) < goal) return@forEach
            val d = com.hydro.water.data.keyToDate(k)
            run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1
            prev = d
            if (run > best) best = run
        }
        StatsData(
            days, days.map { totals[it.key()] ?: 0 }, hourly, byType,
            WaterRepo.streak(totals), best, totals.values.sum(), totals.values.maxOrNull() ?: 0,
            totals.values.count { it >= goal }, WaterRepo.totalCount()
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("饮水统计", style = MaterialTheme.typography.headlineMedium)
                Text("了解你的喝水习惯", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("近 7 天", "近 30 天").forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = range == i, onClick = { range = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(s) }
                }
            }
        }
        item {
            val active = data.values.filter { it > 0 }
            val avg = if (active.isEmpty()) 0 else active.sum() / active.size
            val reached = data.values.count { it >= goal }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("日均饮水", "$avg", "ml", Modifier.weight(1f))
                BigStat("达标天数", "$reached", "/ $span 天", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStat("当前连续", "${data.streak}", "天", Modifier.weight(1f))
                BigStat("累计饮水", "%.1f".format(data.lifetime / 1000f), "L", Modifier.weight(1f))
            }
        }
        item {
            SectionCard(title = "每日饮水量") {
                BarChart(data.days, data.values, goal, span)
            }
        }
        item {
            SectionCard(title = "饮水时段分布") {
                HourChart(data.hourly)
                val peak = data.hourly.indices.maxByOrNull { data.hourly[it] } ?: 0
                Text(
                    if (data.hourly.sum() == 0) "暂无数据" else "你最常在 $peak:00 - ${peak + 1}:00 喝水",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard(title = "饮品构成") { TypeBreakdown(data.byType) }
        }
        item {
            val badges = listOf(
                Badge("初次补水", "记录第一杯水", data.count >= 1),
                Badge("首次达标", "完成一次每日目标", data.reachedDaysAll >= 1),
                Badge("三日坚持", "连续达标 3 天", data.bestStreak >= 3),
                Badge("一周习惯", "连续达标 7 天", data.bestStreak >= 7),
                Badge("月度水王", "连续达标 30 天", data.bestStreak >= 30),
                Badge("百杯达人", "累计记录 100 杯", data.count >= 100),
                Badge("小水库", "累计饮水 50 L", data.lifetime >= 50_000),
                Badge("大海洋", "累计饮水 500 L", data.lifetime >= 500_000),
                Badge("超额完成", "单日饮水达目标 150%", data.bestDay >= goal * 3 / 2),
            )
            SectionCard(title = "成就  ${badges.count { it.unlocked }}/${badges.size}") {
                badges.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { BadgeItem(it, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, unit: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall.merge(Mono), color = cs.onSurface)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun BarChart(days: List<LocalDate>, values: List<Int>, goal: Int, span: Int) {
    val cs = MaterialTheme.colorScheme
    val anim = remember(span) { Animatable(0f) }
    LaunchedEffect(span, values) { anim.snapTo(0f); anim.animateTo(1f, tween(700)) }
    val max = maxOf(values.maxOrNull() ?: 0, goal) * 1.15f
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val n = values.size
        val slot = size.width / n
        val barW = slot * if (n <= 7) 0.46f else 0.62f
        val goalY = size.height * (1 - goal / max)
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - barW) / 2
            drawRoundRect(
                cs.surfaceContainer, Offset(x, 0f), Size(barW, size.height),
                CornerRadius(barW / 2)
            )
            val h = size.height * (v / max) * anim.value
            if (h > 0) {
                val reached = v >= goal
                drawRoundRect(
                    if (reached) Brush.verticalGradient(listOf(cs.secondary, cs.primary))
                    else Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.55f), cs.primary.copy(alpha = 0.35f))),
                    Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2)
                )
            }
        }
        drawLine(
            cs.error.copy(alpha = 0.7f), Offset(0f, goalY), Offset(size.width, goalY), 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEachIndexed { i, d ->
            val show = span == 7 || i % 5 == 4 || i == days.lastIndex
            Text(
                if (!show) "" else if (span == 7) "周" + "一二三四五六日"[d.dayOfWeek.value - 1] else "${d.monthValue}/${d.dayOfMonth}",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = 2.dp).background(cs.error.copy(alpha = 0.7f)))
        Spacer(Modifier.width(6.dp))
        Text("目标 $goal ml", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun HourChart(hourly: IntArray) {
    val cs = MaterialTheme.colorScheme
    val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val slot = size.width / 24
        val w = slot * 0.6f
        hourly.forEachIndexed { i, v ->
            val h = (size.height * v / max).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                if (v > 0) cs.primary.copy(alpha = 0.3f + 0.7f * v / max) else cs.surfaceContainer,
                Offset(slot * i + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 2)
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("0时", "6时", "12时", "18时", "24时").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypeBreakdown(byType: Map<Int, Int>) {
    val cs = MaterialTheme.colorScheme
    val sum = byType.values.sum()
    if (sum == 0) {
        Text("暂无数据", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        return
    }
    val items = DrinkTypes.filter { (byType[it.id] ?: 0) > 0 }.sortedByDescending { byType[it.id] }
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        var x = 0f
        val gap = 3.dp.toPx()
        items.forEach { t ->
            val w = size.width * (byType[t.id] ?: 0) / sum
            drawRoundRect(Color(t.color), Offset(x, 0f), Size((w - gap).coerceAtLeast(2f), size.height), CornerRadius(size.height / 2))
            x += w
        }
    }
    items.forEach { t ->
        val v = byType[t.id] ?: 0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color(t.color), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(t.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$v ml", style = MaterialTheme.typography.bodyMedium.merge(Mono), color = cs.onSurfaceVariant)
            Text("%5.1f%%".format(v * 100f / sum), style = MaterialTheme.typography.bodyMedium.merge(Mono),
                modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun BadgeItem(b: Badge, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val gold = Color(0xFFF2B33D)
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = if (b.unlocked) gold.copy(alpha = 0.12f) else cs.surfaceContainer) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (b.unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock, null,
                tint = if (b.unlocked) gold else cs.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(b.title, style = MaterialTheme.typography.labelLarge,
                color = if (b.unlocked) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
            Text(b.desc, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
