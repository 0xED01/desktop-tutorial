package com.hydro.water.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SysSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.water.data.ActivityLevels
import com.hydro.water.data.WaterRepo
import com.hydro.water.data.recommendedGoal
import com.hydro.water.reminder.Notifications
import com.hydro.water.reminder.ReminderScheduler
import com.hydro.water.ui.theme.Mono
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(snackbar: SnackbarHostState) {
    val s by WaterRepo.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var editCup by remember { mutableIntStateOf(-1) }
    var editNotify by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // 本地拖动值，松手再保存
    var goalDrag by remember(s.goal) { mutableFloatStateOf(s.goal.toFloat()) }
    var weightDrag by remember(s.weight) { mutableFloatStateOf(s.weight.toFloat()) }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    LazyColumn(
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineMedium)
                Text("打造适合你的喝水计划", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }

        item {
            SectionCard(title = "每日目标") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${goalDrag.toInt()}", style = MaterialTheme.typography.displaySmall.merge(Mono),
                        color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(" ml", color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Slider(
                    value = goalDrag, valueRange = 1000f..5000f,
                    onValueChange = { goalDrag = (it / 50).toInt() * 50f },
                    onValueChangeFinished = { WaterRepo.updateSettings { it.copy(goal = goalDrag.toInt()) } }
                )
                HorizontalDivider(color = cs.outlineVariant)
                Text("根据身体情况推荐", style = MaterialTheme.typography.labelLarge)
                LabeledValue("体重", "${weightDrag.toInt()} kg")
                Slider(
                    value = weightDrag, valueRange = 30f..150f,
                    onValueChange = { weightDrag = it.toInt().toFloat() },
                    onValueChangeFinished = { WaterRepo.updateSettings { it.copy(weight = weightDrag.toInt()) } }
                )
                Text("运动量", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ActivityLevels.forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = s.activity == i, onClick = { WaterRepo.updateSettings { it.copy(activity = i) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ActivityLevels.size)
                        ) { Text(l, maxLines = 1) }
                    }
                }
                val rec = recommendedGoal(weightDrag.toInt(), s.activity)
                Surface(
                    onClick = {
                        WaterRepo.updateSettings { it.copy(goal = rec) }
                        toast("已将目标设为 $rec ml")
                    },
                    shape = RoundedCornerShape(14.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("推荐目标 $rec ml", color = cs.onPrimaryContainer, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge)
                        Text("应用", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            SectionCard(title = "快捷杯量") {
                Text("点击修改首页的 4 个快捷按钮（第二个用于桌面小部件）",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.cups.forEachIndexed { i, ml ->
                        Surface(
                            onClick = { editCup = i }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer
                        ) {
                            Text("$ml", style = MaterialTheme.typography.titleMedium.merge(Mono),
                                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "喝水提醒") {
                SwitchRow("开启提醒", "在设定时间段内定时通知", s.reminderOn) { v ->
                    WaterRepo.updateSettings { it.copy(reminderOn = v) }
                }
                if (s.reminderOn) {
                    Text("提醒间隔", style = MaterialTheme.typography.bodyMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(30, 45, 60, 90, 120, 180)) { m ->
                            FilterChip(
                                selected = s.interval == m,
                                onClick = { WaterRepo.updateSettings { it.copy(interval = m) } },
                                label = { Text(if (m < 60) "$m 分钟" else if (m % 60 == 0) "${m / 60} 小时" else "${m / 60}.5 小时") },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeBox("开始", s.startMin, Modifier.weight(1f)) { m ->
                            if (m < s.endMin) WaterRepo.updateSettings { it.copy(startMin = m) } else toast("开始时间需早于结束时间")
                        }
                        TimeBox("结束", s.endMin, Modifier.weight(1f)) { m ->
                            if (m > s.startMin) WaterRepo.updateSettings { it.copy(endMin = m) } else toast("结束时间需晚于开始时间")
                        }
                    }
                    SwitchRow("喝水后重新计时", "记录饮水后从此刻重新计算下次提醒", s.smartDelay) { v ->
                        WaterRepo.updateSettings { it.copy(smartDelay = v) }
                    }
                    SwitchRow("达标后不再提醒", "完成今日目标后当天静默", s.stopWhenDone) { v ->
                        WaterRepo.updateSettings { it.copy(stopWhenDone = v) }
                    }
                    NavRow("通知中快捷记录", "${s.notifyAmount} ml") { editNotify = true }
                    NavRow("发送测试通知", "") { Notifications.showReminder(ctx) }
                    if (!ReminderScheduler.canExact(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        NavRow("允许精确闹钟", "让提醒准时送达") {
                            ctx.startActivity(
                                Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                            )
                        }
                    }
                    NavRow("提醒不准时？", "关闭电池优化") {
                        runCatching {
                            ctx.startActivity(Intent(SysSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                    NavRow("通知权限与声音", "") {
                        ctx.startActivity(
                            Intent(SysSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(SysSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "外观") {
                Text("主题", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = s.themeMode == i, onClick = { WaterRepo.updateSettings { it.copy(themeMode = i) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3)
                        ) { Text(l) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("动态取色", "使用壁纸颜色 (Material You)", s.dynamicColor) { v ->
                        WaterRepo.updateSettings { it.copy(dynamicColor = v) }
                    }
                }
            }
        }

        item {
            SectionCard(title = "数据") {
                NavRow("清空所有饮水记录", "") { confirmClear = true }
            }
        }

        item {
            Text(
                "喝水提醒 v1.0.0\n每天八杯水，健康常相伴 💧",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
        }
    }

    if (editCup >= 0) {
        AmountDialog("修改快捷杯量", s.cups[editCup], onDismiss = { editCup = -1 }) { v ->
            val idx = editCup
            WaterRepo.updateSettings { it.copy(cups = it.cups.toMutableList().also { l -> l[idx] = v }) }
            editCup = -1
        }
    }
    if (editNotify) {
        AmountDialog("通知中快捷记录量", s.notifyAmount, onDismiss = { editNotify = false }) { v ->
            WaterRepo.updateSettings { it.copy(notifyAmount = v) }
            editNotify = false
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？") },
            text = { Text("所有饮水历史和统计将被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    WaterRepo.clearAll()
                    confirmClear = false
                    toast("已清空")
                }) { Text("清空", color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(Mono), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeBox(label: String, minutes: Int, modifier: Modifier, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = { TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, minutes / 60, minutes % 60, true).show() },
        modifier = modifier, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text(fmtMin(minutes), style = MaterialTheme.typography.titleLarge.merge(Mono))
        }
    }
}

@Composable
private fun AmountDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var v by remember { mutableFloatStateOf(initial.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("${v.toInt()} ml", style = MaterialTheme.typography.displaySmall.merge(Mono),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Slider(value = v, onValueChange = { v = (it / 10).toInt() * 10f }, valueRange = 50f..1000f)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v.toInt()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
