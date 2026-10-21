package com.muscleup.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.data.*
import java.time.LocalDate

private fun md(date: Long): String = LocalDate.ofEpochDay(date).let { "${it.monthValue}/${it.dayOfMonth}" }

/** 近两周平均体重变化（kg/周） */
fun weeklyGainRate(): Double? {
    val ws = Store.weights
    if (ws.size < 2) return null
    val last = ws.last()
    val recent = ws.filter { it.date > last.date - 7 }
    val prev = ws.filter { it.date in (last.date - 21)..(last.date - 7) }
    return if (prev.isNotEmpty()) {
        val days = (recent.map { it.date }.average() - prev.map { it.date }.average()).coerceAtLeast(1.0)
        (recent.map { it.weight }.average() - prev.map { it.weight }.average()) / days * 7
    } else {
        val first = ws.first()
        val days = (last.date - first.date).coerceAtLeast(1)
        if (days < 3) null else (last.weight - first.weight) / days * 7
    }
}

fun gainAdvice(rate: Double?): String = when {
    rate == null -> "持续记录体重（建议每天早晨空腹称重），一周后给出调整建议。"
    rate < 0.1 -> "体重增长过慢（${rate.fmt(2)} kg/周）。建议每天再多吃 200-300 kcal，例如加一杯全脂牛奶 + 一把坚果。"
    rate <= 0.55 -> "增长速度 ${rate.fmt(2)} kg/周，处于理想的精益增肌区间，保持当前饮食！"
    rate <= 0.8 -> "增长速度 ${rate.fmt(2)} kg/周，略快。注意观察腰围，若腰围增长明显可减少 100-200 kcal。"
    else -> "增长速度 ${rate.fmt(2)} kg/周，偏快，脂肪可能增加较多。建议减少 200-300 kcal（新手第一个月水分糖原增加属正常）。"
}

@Composable
fun BodyTab(modifier: Modifier) {
    var showWeight by remember { mutableStateOf(false) }
    var showMeasure by remember { mutableStateOf(false) }
    var measureKey by rememberSaveable { mutableIntStateOf(1) }
    val p = Store.profile
    val w = Store.currentWeight()
    val rate = weeklyGainRate()

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(Modifier.weight(1f), w.fmt(), "当前体重 kg")
                StatBox(Modifier.weight(1f), bmi(w, p.heightCm).fmt(), "BMI · ${bmiLabel(bmi(w, p.heightCm))}")
                StatBox(Modifier.weight(1f), rate?.let { (if (it >= 0) "+" else "") + it.fmt(2) } ?: "--", "kg/周", if ((rate ?: 0.0) >= 0.1) GoodColor else MaterialTheme.colorScheme.error)
            }
        }
        item {
            SectionCard(title = "体重趋势", trailing = { FilledTonalButton({ showWeight = true }) { Text("记体重") } }) {
                LineChart(Store.weights.takeLast(30).map { md(it.date) to it.weight }, target = p.targetWeightKg)
                Spacer(Modifier.height(6.dp))
                Text("绿色虚线为目标体重 ${p.targetWeightKg.fmt()} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(gainAdvice(rate), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            val names = listOf("胸围", "臂围", "腰围", "大腿围", "肩宽")
            val values: (Measure) -> Double = { m -> listOf(m.chest, m.arm, m.waist, m.thigh, m.shoulder)[measureKey] }
            SectionCard(title = "围度变化", trailing = { FilledTonalButton({ showMeasure = true }) { Text("记围度") } }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEachIndexed { i, n -> FilterChip(measureKey == i, { measureKey = i }, { Text(n) }) }
                }
                val pts = Store.measures.map { md(it.date) to values(it) }.filter { it.second > 0 }
                LineChart(pts, color = CarbColor)
                val ms = Store.measures
                if (ms.size >= 2) {
                    Spacer(Modifier.height(8.dp))
                    val a = ms.first(); val b = ms.last()
                    Text("从 ${md(a.date)} 至今：" + names.indices.joinToString("  ") { i ->
                        val d = listOf(b.chest - a.chest, b.arm - a.arm, b.waist - a.waist, b.thigh - a.thigh, b.shoulder - a.shoulder)[i]
                        "${names[i]}${if (d >= 0) "+" else ""}${d.fmt()}"
                    }, style = MaterialTheme.typography.bodySmall)
                }
                Text("建议每 2 周测一次，早晨空腹放松状态下测量。理想的增肌：胸围、臂围、大腿围增加，腰围慢增。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Modifier.weight(1f), Icons.Filled.Calculate, "体脂/代谢计算") { Nav.go(Route.BodyCalc) }
                QuickAction(Modifier.weight(1f), Icons.Filled.Bedtime, "睡眠记录") { Nav.go(Route.Sleep) }
            }
        }
        item { Text("体重记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (Store.weights.isEmpty()) item { EmptyHint("暂无体重记录") }
        items(Store.weights.reversed().take(60), key = { "w${it.date}" }) { wl ->
            ListRow(
                "${wl.weight.fmt()} kg", dayLabel(wl.date),
                trailing = { IconButton({ Store.deleteWeight(wl.date) }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) } },
            )
            HorizontalDivider()
        }
        if (Store.measures.isNotEmpty()) {
            item { Text("围度记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(Store.measures.reversed(), key = { "m${it.date}" }) { m ->
                ListRow(
                    dayLabel(m.date),
                    "胸 ${m.chest.fmt()} · 臂 ${m.arm.fmt()} · 腰 ${m.waist.fmt()} · 腿 ${m.thigh.fmt()} · 肩 ${m.shoulder.fmt()} (cm)",
                    trailing = { IconButton({ Store.deleteMeasure(m.date) }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) } },
                )
                HorizontalDivider()
            }
        }
    }

    if (showWeight) {
        var v by remember { mutableStateOf(w.fmt()) }
        var daysAgo by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showWeight = false },
            title = { Text("记录体重") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("今天", "昨天", "前天").forEachIndexed { i, s -> FilterChip(daysAgo == i, { daysAgo = i }, { Text(s) }) }
                    }
                    NumField("体重", v, { v = it }, Modifier.fillMaxWidth(), suffix = "kg")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-0.5, -0.1, 0.1, 0.5).forEach { d ->
                            OutlinedButton({ v = ((v.toDoubleOrNull() ?: w) + d).fmt() }, contentPadding = PaddingValues(4.dp), modifier = Modifier.weight(1f)) {
                                Text((if (d > 0) "+" else "") + d.fmt())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    v.toDoubleOrNull()?.takeIf { it in 25.0..250.0 }?.let { Store.addWeight(today() - daysAgo, it) }
                    showWeight = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton({ showWeight = false }) { Text("取消") } },
        )
    }
    if (showMeasure) {
        val last = Store.measures.lastOrNull()
        var chest by remember { mutableStateOf(last?.chest?.fmt() ?: "") }
        var arm by remember { mutableStateOf(last?.arm?.fmt() ?: "") }
        var waist by remember { mutableStateOf(last?.waist?.fmt() ?: "") }
        var thigh by remember { mutableStateOf(last?.thigh?.fmt() ?: "") }
        var shoulder by remember { mutableStateOf(last?.shoulder?.fmt() ?: "") }
        AlertDialog(
            onDismissRequest = { showMeasure = false },
            title = { Text("记录围度（cm）") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("胸围（乳头水平）", chest, { chest = it }, suffix = "cm")
                    NumField("臂围（上臂最粗处，放松）", arm, { arm = it }, suffix = "cm")
                    NumField("腰围（肚脐水平）", waist, { waist = it }, suffix = "cm")
                    NumField("大腿围（最粗处）", thigh, { thigh = it }, suffix = "cm")
                    NumField("肩宽（肩峰间距离）", shoulder, { shoulder = it }, suffix = "cm")
                }
            },
            confirmButton = {
                TextButton({
                    Store.addMeasure(Measure(today(), chest.toDoubleOrNull() ?: 0.0, arm.toDoubleOrNull() ?: 0.0, waist.toDoubleOrNull() ?: 0.0,
                        thigh.toDoubleOrNull() ?: 0.0, shoulder.toDoubleOrNull() ?: 0.0))
                    showMeasure = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton({ showMeasure = false }) { Text("取消") } },
        )
    }
}

@Composable
fun BodyCalcScreen() {
    val p = Store.profile
    var waist by remember { mutableStateOf(Store.measures.lastOrNull()?.waist?.takeIf { it > 0 }?.fmt() ?: "") }
    var neck by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    val w = Store.currentWeight()
    val t = p.targets(w)
    SubPage("身体指标计算", Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "代谢与热量（Mifflin-St Jeor 公式）") {
                ListRow("基础代谢 BMR", "躺着不动一天消耗的热量", trailing = { Text("${t.bmr} kcal", fontWeight = FontWeight.Bold) })
                ListRow("每日总消耗 TDEE", "BMR × 活动系数 ${p.activity}", trailing = { Text("${t.tdee} kcal", fontWeight = FontWeight.Bold) })
                ListRow("增肌摄入目标", "TDEE + ${p.surplus} kcal 盈余", trailing = { Text("${t.kcal} kcal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) })
                ListRow("蛋白质", "2.0 g/kg 体重", trailing = { Text("${t.protein} g", color = ProteinColor, fontWeight = FontWeight.Bold) })
                ListRow("脂肪", "占总热量 25%", trailing = { Text("${t.fat} g", color = FatColor, fontWeight = FontWeight.Bold) })
                ListRow("碳水", "剩余热量", trailing = { Text("${t.carbs} g", color = CarbColor, fontWeight = FontWeight.Bold) })
                TextButton({ Nav.go(Route.ProfileEdit) }) { Text("修改个人资料 / 盈余 →") }
            }
            SectionCard(title = "BMI 与理想体重") {
                val b = bmi(w, p.heightCm)
                Text("BMI = ${b.fmt()}（${bmiLabel(b)}）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val h = p.heightCm / 100
                Text("健康体重范围（BMI 18.5–24）：${(18.5 * h * h).fmt()} – ${(24 * h * h).fmt()} kg")
                Text("增肌后的健美体型常见 BMI 在 22–26 之间，BMI 不能区分肌肉和脂肪，仅供参考。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "体脂率估算（美国海军公式）") {
                NumField("腰围（肚脐水平）", waist, { waist = it }, Modifier.fillMaxWidth(), suffix = "cm")
                NumField("颈围（喉结下方）", neck, { neck = it }, Modifier.fillMaxWidth(), suffix = "cm")
                if (!p.male) NumField("臀围（最宽处）", hip, { hip = it }, Modifier.fillMaxWidth(), suffix = "cm")
                val bf = navyBodyFat(p.male, p.heightCm, waist.toDoubleOrNull() ?: 0.0, neck.toDoubleOrNull() ?: 0.0, hip.toDoubleOrNull() ?: 0.0)
                Spacer(Modifier.height(8.dp))
                if (bf != null) {
                    Text("体脂率约 ${bf.fmt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    val lean = w * (1 - bf / 100)
                    Text("瘦体重约 ${lean.fmt()} kg · 脂肪约 ${(w - lean).fmt()} kg")
                    val ranges = if (p.male) listOf(6.0 to "竞技", 14.0 to "健美", 18.0 to "健康", 25.0 to "一般") else listOf(14.0 to "竞技", 21.0 to "健美", 25.0 to "健康", 32.0 to "一般")
                    Text("评级：" + (ranges.firstOrNull { bf < it.first }?.second ?: "偏高"))
                } else {
                    Text("填写数据后自动计算", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SleepScreen() {
    var hours by remember { mutableStateOf("8") }
    var quality by remember { mutableIntStateOf(3) }
    var daysAgo by remember { mutableIntStateOf(0) }
    val sleeps = Store.sleeps
    SubPage("睡眠记录", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(title = "记录昨晚睡眠") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("今天", "昨天", "前天").forEachIndexed { i, s -> FilterChip(daysAgo == i, { daysAgo = i }, { Text(s) }) }
                    }
                    NumField("睡眠时长", hours, { hours = it }, Modifier.fillMaxWidth(), suffix = "小时")
                    Spacer(Modifier.height(6.dp))
                    Text("睡眠质量")
                    Row { (1..5).forEach { i -> IconButton({ quality = i }) { Icon(if (i <= quality) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = FatColor) } } }
                    Button({
                        hours.toDoubleOrNull()?.takeIf { it in 0.0..24.0 }?.let { Store.addSleep(SleepLog(today() - daysAgo, it, quality)) }
                    }, Modifier.fillMaxWidth()) { Text("保存") }
                }
            }
            item {
                val last7 = (6 downTo 0).map { k -> val d = today() - k; md(d) to (sleeps.firstOrNull { it.date == d }?.hours ?: 0.0) }
                SectionCard(title = "近 7 天睡眠（小时）") {
                    BarChart(last7, color = androidx.compose.ui.graphics.Color(0xFF7048E8))
                    val avg = sleeps.filter { it.date > today() - 7 }.map { it.hours }.average()
                    if (!avg.isNaN()) {
                        Text("平均 ${avg.fmt()} 小时 · " + if (avg >= 7) "睡眠充足，有利于肌肉恢复 👍" else "睡眠不足 7 小时，会影响增肌效果和训练表现", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionCard(title = "好睡眠小技巧") {
                    Text("• 固定作息，周末也别熬夜\n• 睡前 1 小时远离手机\n• 下午 2 点后不喝咖啡/浓茶\n• 卧室保持黑暗、凉爽\n• 睡前一杯牛奶有助于夜间蛋白质供应")
                }
            }
            items(sleeps.reversed(), key = { it.date }) { s ->
                ListRow("${dayLabel(s.date)}  ${s.hours.fmt()} 小时", "质量 " + "★".repeat(s.quality),
                    trailing = { IconButton({ Store.deleteSleep(s.date) }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) } })
                HorizontalDivider()
            }
        }
    }
}
