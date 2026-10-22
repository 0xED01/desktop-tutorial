package com.muscleup.app.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.FloatTimer
import com.muscleup.app.FloatTimerService
import com.muscleup.app.MainActivity
import com.muscleup.app.data.Store
import com.muscleup.app.data.formatDuration
import com.muscleup.app.data.today
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 开启悬浮计时；没有悬浮窗权限时先询问 */
fun startFloatTimer(ctx: Context, label: String? = null, askPermission: () -> Unit) {
    if (!FloatTimer.running && label != null) FloatTimer.label = label
    if (!FloatTimer.canDrawOverlays(ctx)) {
        askPermission()
        return
    }
    (ctx as? MainActivity)?.ensureNotificationPermission()
    FloatTimer.launch(ctx)
}

@Composable
fun OverlayPermissionDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要悬浮窗权限") },
        text = { Text("悬浮计时器需要「显示在其他应用上层」权限，才能在你听音乐、看视频或切换 App 时一直显示训练时间。\n\n点击「去开启」后，在列表中找到「增肌助手」并打开开关，然后返回本页。") },
        confirmButton = {
            TextButton({
                onDismiss()
                runCatching { ctx.startActivity(FloatTimer.overlayPermissionIntent(ctx)) }
            }) { Text("去开启") }
        },
        dismissButton = {
            TextButton({
                onDismiss()
                // 不开悬浮窗也能计时：只在通知栏显示
                (ctx as? MainActivity)?.ensureNotificationPermission()
                FloatTimer.launch(ctx)
            }) { Text("仅通知栏计时") }
        },
    )
}

private fun md(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

private fun minutesText(sec: Long): String = when {
    sec >= 3600 -> "${sec / 3600}小时${sec % 3600 / 60}分"
    else -> "${sec / 60}分钟"
}

@Composable
fun FloatTimerScreen() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var askPerm by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (LiveTicker.enabled) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val canOverlay = remember(now) { FloatTimer.canDrawOverlays(ctx) }
    val logs = Store.timerLogs
    val t = today()
    val weekStart = t - LocalDate.now().dayOfWeek.value + 1
    val monthStart = LocalDate.now().withDayOfMonth(1).toEpochDay()

    SubPage("悬浮训练计时器", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    val running = FloatTimer.running
                    Text(
                        if (!running) "准备开始" else if (FloatTimer.paused) "已暂停" else "计时中 · ${FloatTimer.label}",
                        color = if (running && !FloatTimer.paused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        formatDuration(FloatTimer.elapsedMs(now) / 1000),
                        fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (running) {
                        Text("开始于 ${md(FloatTimer.sessionStart).let { "${it.monthValue}/${it.dayOfMonth}" }} " +
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(FloatTimer.sessionStart)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (!running) {
                        OutlinedTextField(
                            FloatTimer.label, { FloatTimer.label = it.take(20) }, label = { Text("训练名称") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("训练", "胸+三头", "背+二头", "腿", "肩+手臂", "全身", "有氧").forEach { l ->
                                FilterChip(FloatTimer.label == l, { FloatTimer.label = l }, { Text(l) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            { startFloatTimer(ctx) { askPerm = true } },
                            Modifier.fillMaxWidth().height(50.dp),
                        ) { Icon(Icons.Filled.PlayArrow, null); Text(" 开始计时（显示悬浮窗）") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton({
                                FloatTimer.togglePause(ctx); FloatTimer.send(ctx, FloatTimerService.ACTION_REFRESH)
                            }, Modifier.weight(1f)) { Text(if (FloatTimer.paused) "继续" else "暂停") }
                            Button({
                                if (FloatTimerService.alive) FloatTimer.send(ctx, FloatTimerService.ACTION_STOP) else FloatTimer.stop(ctx, true)
                            }, Modifier.weight(1f)) { Text("结束并保存") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ startFloatTimer(ctx) { askPerm = true } }, Modifier.weight(1f)) { Text("显示悬浮窗") }
                            OutlinedButton({ FloatTimer.send(ctx, FloatTimerService.ACTION_HIDE) }, Modifier.weight(1f)) { Text("隐藏悬浮窗") }
                        }
                        TextButton({ confirmDiscard = true }, Modifier.align(Alignment.CenterHorizontally)) {
                            Text("放弃本次计时", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                SectionCard(title = "悬浮窗「休」按钮的休息时长") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60, 90, 120, 180).forEach { s ->
                            FilterChip(FloatTimer.restSec == s, { FloatTimer.setRest(ctx, s) }, { Text(if (s % 60 == 0) "${s / 60}分" else "${s}秒") })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "悬浮窗用法：\n• 拖动时间数字可移动位置\n• 轻点时间 = 暂停/继续\n• 长按时间 = 收起/展开按钮\n• 「休」开始组间休息倒计时，结束时震动\n• 「■」结束并保存本次训练时长",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!canOverlay) {
                        Spacer(Modifier.height(6.dp))
                        TextButton({ askPerm = true }) { Text("⚠️ 尚未开启悬浮窗权限，点此开启") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(Modifier.weight(1f), minutesText(logs.filter { it.date == t }.sumOf { it.seconds.toLong() }), "今天")
                    StatBox(Modifier.weight(1f), minutesText(logs.filter { it.date >= weekStart }.sumOf { it.seconds.toLong() }), "本周")
                    StatBox(Modifier.weight(1f), minutesText(logs.filter { it.date >= monthStart }.sumOf { it.seconds.toLong() }), "本月")
                }
            }
            item {
                val bars = (6 downTo 0).map { k ->
                    val d = t - k
                    val ld = LocalDate.ofEpochDay(d)
                    (if (k == 0) "今天" else "${ld.monthValue}/${ld.dayOfMonth}") to logs.filter { it.date == d }.sumOf { it.seconds } / 60.0
                }
                SectionCard(title = "近 7 天训练时长（分钟）") {
                    BarChart(bars)
                    if (logs.isNotEmpty()) {
                        val avg = logs.map { it.seconds }.average() / 60
                        Text("共 ${logs.size} 次 · 平均每次 ${avg.toInt()} 分钟" +
                            if (avg > 90) " · 偏长，高质量训练 60–75 分钟就够了" else "",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("计时记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (logs.isEmpty()) item { EmptyHint("还没有计时记录\n点击上方「开始计时」，训练结束后按「■」保存") }
            items(logs, key = { it.id }) { l ->
                val start = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(l.startMs))
                val end = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(l.startMs + l.seconds * 1000L))
                ListRow(
                    "${l.label}  ${formatDuration(l.seconds.toLong())}",
                    "${dayLabel(l.date)} $start – $end",
                    leading = { Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary) },
                    trailing = { IconButton({ Store.deleteTimerLog(l.id) }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) } },
                )
                HorizontalDivider()
            }
        }
    }
    if (askPerm) OverlayPermissionDialog { askPerm = false }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃本次计时？") },
            text = { Text("本次时长不会被保存。") },
            confirmButton = {
                TextButton({
                    confirmDiscard = false
                    if (FloatTimerService.alive) FloatTimer.send(ctx, FloatTimerService.ACTION_DISCARD) else FloatTimer.stop(ctx, false)
                }) { Text("放弃") }
            },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text("取消") } },
        )
    }
}
