package com.shiji.footprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiji.footprint.data.DayStat
import com.shiji.footprint.data.Prefs
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.TrackPoint
import com.shiji.footprint.ui.MiniStat
import com.shiji.footprint.ui.ScreenTitle
import com.shiji.footprint.ui.SectionCard
import com.shiji.footprint.ui.map.FootprintMap
import com.shiji.footprint.ui.map.MapStyles
import com.shiji.footprint.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEK = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun TimelineScreen() {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now()) }
    val version by Repository.version.collectAsStateWithLifecycle()

    val days by produceState(emptyMap<LocalDate, DayStat>(), month, version) {
        value = withContext(Dispatchers.IO) { Repository.monthDays(month) }
    }
    val dayVersion = if (selected == LocalDate.now()) version else 0L
    val points by produceState<List<TrackPoint>?>(null, selected, dayVersion) {
        value = withContext(Dispatchers.IO) { Repository.pointsOfDay(selected) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        ScreenTitle("足迹日历", "每一天走过的路")
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上个月")
                }
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下个月")
                }
            }
            Spacer(Modifier.height(8.dp))
            CalendarGrid(month, days, selected) { selected = it }
            Spacer(Modifier.height(12.dp))
            val monthKm = days.values.sumOf { it.distanceKm }
            Text(
                "本月记录 ${days.size} 天 · 共 ${Fmt.km(monthKm)} km",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        DayDetail(selected, days[selected], points)
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    days: Map<LocalDate, DayStat>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val maxKm = (days.values.maxOfOrNull { it.distanceKm } ?: 1.0).coerceAtLeast(1.0)
    val lead = month.atDay(1).dayOfWeek.value - 1
    val total = month.lengthOfMonth()
    val rows = (lead + total + 6) / 7
    val primary = MaterialTheme.colorScheme.primary

    Row {
        WEEK.forEach {
            Text(
                it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    for (r in 0 until rows) {
        Row {
            for (c in 0 until 7) {
                val dayNum = r * 7 + c - lead + 1
                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (dayNum in 1..total) {
                        val date = month.atDay(dayNum)
                        val stat = days[date]
                        val isSel = date == selected
                        val future = date > today
                        val bg = when {
                            isSel -> primary
                            stat != null -> primary.copy(alpha = 0.12f + 0.38f * (stat.distanceKm / maxKm).toFloat())
                            else -> Color.Transparent
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(bg)
                                .then(
                                    if (date == today && !isSel) Modifier.border(1.5.dp, primary, CircleShape) else Modifier
                                )
                                .clickable(enabled = !future) { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$dayNum",
                                fontSize = 14.sp,
                                fontWeight = if (stat != null || isSel) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSel -> MaterialTheme.colorScheme.onPrimary
                                    future -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(date: LocalDate, stat: DayStat?, points: List<TrackPoint>?) {
    val week = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
    SectionCard(title = "${date.monthValue}月${date.dayOfMonth}日 · $week") {
        if (stat == null || points.isNullOrEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Rounded.EventBusy, null, Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Text("这一天没有留下足迹", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@SectionCard
        }
        val durMs = (stat.last - stat.first).coerceAtLeast(0)
        val avg = if (durMs > 60_000) stat.distanceKm / (durMs / 3_600_000.0) else 0.0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat(Fmt.km(stat.distanceKm), "公里", Modifier.weight(1f))
            MiniStat(Fmt.duration(durMs), "时长", Modifier.weight(1f))
            MiniStat(String.format(Locale.US, "%.1f", avg), "均速 km/h", Modifier.weight(1f))
            MiniStat("${stat.points}", "足迹点", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        DayMap(points, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp)))
    }
}

@Composable
private fun DayMap(points: List<TrackPoint>, modifier: Modifier) {
    val ctx = LocalContext.current
    val styleId by Prefs.mapStyle.collectAsStateWithLifecycle()
    val map = remember { FootprintMap(ctx, withFog = false) }
    DisposableEffect(Unit) {
        map.view.onResume()
        onDispose { map.view.onDetach() }
    }
    LaunchedEffect(points.firstOrNull()?.t, styleId) { map.fit(points) }
    AndroidView(
        factory = { map.view },
        modifier = modifier,
        update = { map.update(MapStyles.byId(styleId), false, 0f, points, null, false) },
    )
}
