package com.hydro.water.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydro.water.data.DrinkTypes
import com.hydro.water.data.Record
import com.hydro.water.data.WaterRepo
import com.hydro.water.data.drinkType
import com.hydro.water.ui.theme.Mono
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(snackbar: SnackbarHostState) {
    val settings by WaterRepo.settings.collectAsStateWithLifecycle()
    val records by WaterRepo.today.collectAsStateWithLifecycle()
    val next by WaterRepo.nextReminder.collectAsStateWithLifecycle()
    val version by WaterRepo.version.collectAsStateWithLifecycle()
    val streak = remember(version, settings.goal) { WaterRepo.streak() }
    val total = records.sumOf { it.effective }
    val goal = settings.goal
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showCustom by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    fun drink(ml: Int, type: Int = settings.defaultType) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        WaterRepo.add(ml, type)
    }

    fun remove(r: Record) {
        WaterRepo.delete(r)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar("已删除 ${r.amount} ml ${drinkType(r.type).name}", "撤销")
            if (res == SnackbarResult.ActionPerformed) WaterRepo.restore(r)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text(date, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(greeting(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WaterBall(
                    progress = if (goal > 0) total / goal.toFloat() else 0f,
                    modifier = Modifier.width(270.dp).padding(vertical = 8.dp)
                ) { underwater ->
                    val main = underwaterColor(underwater, cs.onSurface)
                    val sub = underwaterColor(underwater, cs.onSurfaceVariant)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val pct = if (goal > 0) total * 100 / goal else 0
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$pct", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = main, style = Mono)
                            Text("%", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = main,
                                modifier = Modifier.padding(bottom = 12.dp, start = 2.dp))
                        }
                        Text("$total / $goal ml", color = sub, style = MaterialTheme.typography.bodyMedium.merge(Mono))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (total >= goal) "🎉 今日目标已达成" else "还差 ${goal - total} ml",
                            color = main, style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val nextText = if (!settings.reminderOn || next == 0L) "已关闭" else
                    Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                MiniStat(
                    if (settings.reminderOn) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                    "下次提醒", nextText, cs.primary, Modifier.weight(1f)
                )
                MiniStat(Icons.Outlined.LocalDrink, "今日杯数", "${records.size} 杯", cs.secondary, Modifier.weight(1f))
                MiniStat(Icons.Outlined.LocalFireDepartment, "连续达标", "$streak 天", Color(0xFFF2853A), Modifier.weight(1f))
            }
        }

        item {
            SectionCard(title = "快速记录") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DrinkTypes) { t ->
                        FilterChip(
                            selected = settings.defaultType == t.id,
                            onClick = { WaterRepo.updateSettings { it.copy(defaultType = t.id) } },
                            label = { Text(t.name) },
                            leadingIcon = {
                                Box(Modifier.size(8.dp).background(Color(t.color), CircleShape))
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cs.primaryContainer,
                                selectedLabelColor = cs.onPrimaryContainer,
                            )
                        )
                    }
                }
                val type = drinkType(settings.defaultType)
                if (type.factor < 1f) {
                    Text(
                        "${type.name}按 ${(type.factor * 100).toInt()}% 计入有效饮水量",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    settings.cups.forEach { ml ->
                        CupButton(ml, Modifier.weight(1f)) { drink(ml) }
                    }
                }
                Surface(
                    onClick = { showCustom = true },
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("自定义饮水量", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            SectionCard(Modifier.animateContentSize(), title = "今日记录") {
                if (records.isEmpty()) {
                    Text(
                        "还没有记录，先来一杯水吧 💧",
                        color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    records.forEach { r -> RecordRow(r) { remove(r) } }
                }
            }
        }
    }

    if (showCustom) {
        CustomDialog(settings.defaultType, onDismiss = { showCustom = false }) { ml, type ->
            showCustom = false
            drink(ml, type)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "早上好，先喝杯水"
    in 11..13 -> "中午好，记得补水"
    in 14..17 -> "下午好，喝口水吧"
    in 18..21 -> "晚上好，适量饮水"
    else -> "夜深了，早点休息"
}

@Composable
private fun MiniStat(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(30.dp).background(tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.merge(Mono), maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CupButton(ml: Int, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = cs.primaryContainer) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CupIcon(fill = (ml / 600f).coerceIn(0.25f, 1f), color = cs.primary)
            Spacer(Modifier.height(8.dp))
            Text("$ml", style = MaterialTheme.typography.titleMedium.merge(Mono), color = cs.onPrimaryContainer)
            Text("ml", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun CupIcon(fill: Float, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 22.dp, height = 28.dp)) {
        val w = size.width
        val h = size.height
        val inset = w * 0.14f
        val cup = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        drawPath(cup, color.copy(alpha = 0.18f))
        val top = h * (1 - fill)
        val water = androidx.compose.ui.graphics.Path().apply {
            val l = inset * (top / h)
            moveTo(l, top); lineTo(w - l, top); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        drawPath(water, color)
    }
}

@Composable
private fun RecordRow(r: Record, onDelete: () -> Unit) {
    val t = drinkType(r.type)
    val cs = MaterialTheme.colorScheme
    val time = Instant.ofEpochMilli(r.ts).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(38.dp).background(Color(t.color).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.LocalDrink, null, tint = Color(t.color), modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${t.name} · ${r.amount} ml", style = MaterialTheme.typography.bodyLarge)
            val sub = if (r.effective != r.amount) "$time  ·  有效 ${r.effective} ml" else time
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, "删除", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CustomDialog(initialType: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var value by remember { mutableFloatStateOf(300f) }
    var type by remember { mutableStateOf(initialType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义饮水量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${value.toInt()} ml", style = MaterialTheme.typography.displaySmall.merge(Mono),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Slider(value = value, onValueChange = { value = (it / 10).toInt() * 10f }, valueRange = 10f..1500f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DrinkTypes) { t ->
                        FilterChip(selected = type == t.id, onClick = { type = t.id }, label = { Text(t.name) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.toInt(), type) }) { Text("记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
