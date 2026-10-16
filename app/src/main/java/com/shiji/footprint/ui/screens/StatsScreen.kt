package com.shiji.footprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiji.footprint.data.Repository
import com.shiji.footprint.data.Stats
import com.shiji.footprint.ui.IconBadge
import com.shiji.footprint.ui.ScreenTitle
import com.shiji.footprint.ui.SectionCard
import com.shiji.footprint.ui.StatTile
import com.shiji.footprint.ui.theme.Amber
import com.shiji.footprint.ui.theme.Coral
import com.shiji.footprint.ui.theme.Indigo
import com.shiji.footprint.ui.theme.Mint
import com.shiji.footprint.ui.theme.NumberStyle
import com.shiji.footprint.ui.theme.Rose
import com.shiji.footprint.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

private data class Level(val lv: Int, val title: String, val minArea: Double)

private val LEVELS = listOf(
    Level(1, "初出茅庐", 0.0),
    Level(2, "街巷漫步者", 0.5),
    Level(3, "城市行者", 5.0),
    Level(4, "远方旅人", 30.0),
    Level(5, "山河过客", 150.0),
    Level(6, "四海游侠", 600.0),
    Level(7, "天涯行者", 2500.0),
    Level(8, "环球探险家", 10000.0),
)

private const val EARTH_LAND_KM2 = 148_940_000.0

private data class Badge(val icon: ImageVector, val title: String, val desc: String, val color: Color, val done: Boolean)

private fun badges(s: Stats) = listOf(
    Badge(Icons.Rounded.Flag, "初次启程", "记录第一个足迹", Mint, s.points > 0),
    Badge(Icons.Rounded.DirectionsWalk, "十公里", "累计 10 km", Indigo, s.totalKm >= 10),
    Badge(Icons.Rounded.Hiking, "百里之行", "累计 50 km", Indigo, s.totalKm >= 50),
    Badge(Icons.Rounded.Route, "千里之行", "累计 500 km", Coral, s.totalKm >= 500),
    Badge(Icons.Rounded.TravelExplore, "万里行者", "累计 5000 km", Rose, s.totalKm >= 5000),
    Badge(Icons.Rounded.CalendarMonth, "坚持一周", "记录 7 天", Mint, s.days >= 7),
    Badge(Icons.Rounded.Star, "百日足迹", "记录 100 天", Amber, s.days >= 100),
    Badge(Icons.Rounded.LocationCity, "城市漫游", "点亮 5 座城市", Indigo, s.cities >= 5),
    Badge(Icons.Rounded.Map, "省际旅人", "点亮 5 个省份", Coral, s.provinces >= 5),
    Badge(Icons.Rounded.Public, "走出国门", "去过 2 个国家/地区", Rose, s.countries >= 2),
    Badge(Icons.Rounded.Explore, "开拓者", "探索 10 km²", Mint, s.areaKm2 >= 10),
    Badge(Icons.Rounded.EmojiEvents, "版图大师", "探索 500 km²", Amber, s.areaKm2 >= 500),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen() {
    val stats by Repository.stats.collectAsStateWithLifecycle()
    val places by Repository.places.collectAsStateWithLifecycle()
    val version by Repository.version.collectAsStateWithLifecycle()
    val recent by produceState(emptyMap<LocalDate, Double>(), version) {
        value = withContext(Dispatchers.IO) { Repository.recentDays(7).mapValues { it.value.distanceKm } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        ScreenTitle("我的版图", "丈量走过的世界")
        LevelCard(stats)
        Spacer(Modifier.height(14.dp))

        val tiles = listOf(
            Triple(Icons.Rounded.Route, Coral, Triple(Fmt.km(stats.totalKm), "km", "总里程")),
            Triple(Icons.Rounded.CalendarMonth, Indigo, Triple("${stats.days}", "天", "记录天数")),
            Triple(Icons.Rounded.Timeline, Mint, Triple(Fmt.count(stats.points), "", "足迹点")),
            Triple(Icons.Rounded.Public, Rose, Triple("${stats.countries}", "个", "国家/地区")),
            Triple(Icons.Rounded.Map, Amber, Triple("${stats.provinces}", "个", "省份")),
            Triple(Icons.Rounded.LocationCity, Indigo, Triple("${stats.cities}", "座", "城市")),
        )
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, color, v) ->
                    StatTile(icon, color, v.first, v.second, v.third, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(4.dp))

        SectionCard(title = "近 7 天里程") { WeekChart(recent) }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "成就徽章") {
            val list = badges(stats)
            Text(
                "已解锁 ${list.count { it.done }} / ${list.size}",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            list.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { b -> BadgeItem(b, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "点亮的地方") {
            if (places.isEmpty()) {
                Text(
                    "开始记录后，经过的城市会在这里被点亮（需联网解析地名）",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val groups = places.groupBy { if (it.country == "中国") it.province else "${it.country} · ${it.province}" }
                groups.entries.forEachIndexed { i, (province, list) ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Place, Coral, 28)
                        Spacer(Modifier.width(8.dp))
                        Text(province, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        list.map { it.city }.distinct().forEach { city ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    city, fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelCard(s: Stats) {
    val idx = LEVELS.indexOfLast { s.areaKm2 >= it.minArea }.coerceAtLeast(0)
    val level = LEVELS[idx]
    val next = LEVELS.getOrNull(idx + 1)
    val progress = if (next == null) 1f
    else ((s.areaKm2 - level.minArea) / (next.minArea - level.minArea)).toFloat().coerceIn(0f, 1f)
    val percent = s.areaKm2 / EARTH_LAND_KM2 * 100

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Indigo, Color(0xFF6A5AE0), Mint)))
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f)) {
                    Text(
                        "Lv.${level.lv}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(level.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("已探索面积", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Fmt.area(s.areaKm2), style = NumberStyle.copy(fontSize = 40.sp), color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("km²", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
            Text(
                "约占地球陆地的 ${String.format(Locale.US, "%.6f", percent)}%",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (next != null) "距离「${next.title}」还需探索 ${Fmt.area(next.minArea - s.areaKm2)} km²"
                else "已达到最高等级",
                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WeekChart(data: Map<LocalDate, Double>) {
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val max = (days.maxOf { data[it] ?: 0.0 }).coerceAtLeast(0.1)
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            val v = data[d] ?: 0.0
            val isToday = d == LocalDate.now()
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    if (v > 0) Fmt.km(v) else "", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(20.dp)
                        .height((6 + 94 * (v / max)).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (v <= 0) MaterialTheme.colorScheme.surfaceVariant
                            else if (isToday) Coral else MaterialTheme.colorScheme.primary
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isToday) "今天" else "周" + labels[d.dayOfWeek.value - 1],
                    fontSize = 11.sp,
                    color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun BadgeItem(b: Badge, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (b.done) Brush.linearGradient(listOf(b.color, b.color.copy(alpha = 0.7f)))
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                b.icon, null,
                tint = if (b.done) Color.White else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            b.title, fontSize = 12.sp, maxLines = 1,
            fontWeight = if (b.done) FontWeight.SemiBold else FontWeight.Normal,
            color = if (b.done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(b.desc, fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
