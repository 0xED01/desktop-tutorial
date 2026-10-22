package com.muscleup.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.MainActivity
import com.muscleup.app.Reminders
import com.muscleup.app.data.*
import kotlinx.coroutines.delay

@Composable
fun MeTab(modifier: Modifier) {
    val p = Store.profile
    val t = p.targets(Store.currentWeight())
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(onClick = { Nav.go(Route.ProfileEdit) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(p.name.take(1), color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${if (p.male) "男" else "女"} · ${p.age}岁 · ${p.heightCm.fmt()}cm · ${Store.currentWeight().fmt()}kg", style = MaterialTheme.typography.bodySmall)
                    Text("每日 ${t.kcal} kcal · 蛋白质 ${t.protein}g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.Filled.Edit, "编辑")
            }
        }

        MenuGroup("训练工具", listOf(
            Triple(Icons.Filled.AvTimer, "悬浮训练计时器（记录训练时长）", Route.FloatTimerR),
            Triple(Icons.Filled.Timer, "组间休息计时器", Route.Timer),
            Triple(Icons.Filled.Calculate, "1RM 最大力量计算", Route.OneRmCalc),
            Triple(Icons.Filled.FitnessCenter, "杠铃片配重计算", Route.PlateCalc),
            Triple(Icons.Filled.EmojiEvents, "个人最佳 PR", Route.Records),
            Triple(Icons.AutoMirrored.Filled.List, "动作库（${Exercises.all.size} 个动作）", Route.ExerciseLibrary),
        ))
        MenuGroup("身体与营养", listOf(
            Triple(Icons.Filled.MonitorHeart, "代谢 / BMI / 体脂计算", Route.BodyCalc),
            Triple(Icons.Filled.Bedtime, "睡眠记录", Route.Sleep),
            Triple(Icons.Filled.Restaurant, "增肌食谱与奶昔", Route.Recipes),
            Triple(Icons.Filled.Medication, "补剂指南", Route.Supplements),
        ))
        MenuGroup("成长", listOf(
            Triple(Icons.AutoMirrored.Filled.MenuBook, "增肌知识库", Route.KnowledgeList),
            Triple(Icons.Filled.MilitaryTech, "成就徽章", Route.Achievements),
            Triple(Icons.Filled.Notifications, "提醒设置", Route.RemindersR),
            Triple(Icons.Filled.Storage, "数据备份与恢复", Route.DataManage),
        ))
        Text("增肌助手 v1.1 · 所有数据仅保存在本机\n本应用内容仅供参考，如有伤病请咨询医生或专业教练。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
    }
}

@Composable
private fun MenuGroup(title: String, items: List<Triple<ImageVector, String, Route>>) {
    SectionCard(title = title) {
        items.forEach { (icon, label, route) ->
            ListRow(label, leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) { Nav.go(route) }
        }
    }
}

// ===================== 计时器 =====================

@Composable
fun TimerScreen() {
    val ctx = LocalContext.current
    var total by remember { mutableIntStateOf(90) }
    var endMs by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (LiveTicker.enabled) {
            now = System.currentTimeMillis()
            if (endMs != 0L && now >= endMs) {
                endMs = 0; vibrate(ctx)
            }
            delay(100)
        }
    }
    val running = endMs > now
    val left = if (running) (endMs - now) / 1000.0 else total.toDouble()
    SubPage("组间休息计时器", Nav::back) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Ring((left / total).toFloat(), MaterialTheme.colorScheme.primary, 240, 20f) {
                val s = kotlin.math.ceil(left).toInt()
                Text("%d:%02d".format(s / 60, s % 60), fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120, 180).forEach { s ->
                    FilterChip(total == s, { total = s; endMs = 0 }, { Text(if (s < 60) "${s}s" else "${s / 60}${if (s % 60 != 0) ".5" else ""}分") })
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ if (running) endMs -= 15000 else total = (total - 15).coerceAtLeast(15) }) { Text("-15s") }
                Button({ endMs = if (running) 0 else System.currentTimeMillis() + total * 1000L }, Modifier.width(120.dp)) { Text(if (running) "停止" else "开始") }
                OutlinedButton({ if (running) endMs += 15000 else total += 15 }) { Text("+15s") }
            }
            Spacer(Modifier.height(24.dp))
            SectionCard(title = "休息时间建议") {
                Text("• 大重量复合动作（深蹲/硬拉/卧推）：2–3 分钟\n• 一般复合动作：90–120 秒\n• 孤立动作（弯举/侧平举）：45–75 秒\n休息充分才能在下一组保持重量和次数，增肌并不需要刻意缩短休息。")
            }
        }
    }
}

// ===================== 1RM =====================

@Composable
fun OneRmScreen() {
    var w by remember { mutableStateOf("60") }
    var r by remember { mutableStateOf("8") }
    val weight = w.toDoubleOrNull() ?: 0.0
    val reps = r.toIntOrNull() ?: 0
    val epley = oneRm(weight, reps)
    val brzycki = if (reps in 1..36) weight * 36 / (37 - reps) else 0.0
    val est = if (reps <= 10) (epley + brzycki) / 2 else epley
    SubPage("1RM 计算器", Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("重量", w, { w = it }, Modifier.weight(1f), suffix = "kg")
                NumField("次数", r, { r = it }, Modifier.weight(1f), decimal = false)
            }
            SectionCard {
                Text("估算最大力量 1RM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${est.fmt(1)} kg", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Epley ${epley.fmt(1)} · Brzycki ${brzycki.fmt(1)}（次数 ≤10 时更准确）", style = MaterialTheme.typography.bodySmall)
            }
            if (est > 0) SectionCard(title = "训练重量参考") {
                listOf(
                    Triple(95, "2", "力量"), Triple(90, "3-4", "力量"), Triple(85, "5-6", "力量/增肌"), Triple(80, "7-8", "增肌"),
                    Triple(75, "9-10", "增肌"), Triple(70, "11-12", "增肌"), Triple(65, "13-15", "增肌/耐力"), Triple(60, "16-20", "耐力"),
                ).forEach { (pct, rr, goal) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("$pct%", Modifier.width(52.dp), fontWeight = FontWeight.Bold)
                        Text("${(est * pct / 100).fmt(1)} kg", Modifier.weight(1f))
                        Text("$rr 次", Modifier.width(64.dp))
                        Tag(goal)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("增肌主要使用 65–85% 1RM 的重量，每组做到力竭前 1–3 次。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ===================== 杠铃片 =====================

@Composable
fun PlateScreen() {
    var target by remember { mutableStateOf("60") }
    var bar by remember { mutableDoubleStateOf(20.0) }
    val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val t = target.toDoubleOrNull() ?: 0.0
    var perSide = ((t - bar) / 2).coerceAtLeast(0.0)
    val used = mutableListOf<Double>()
    for (p in plates) while (perSide >= p - 1e-6) { used.add(p); perSide -= p }
    val actual = bar + used.sum() * 2
    SubPage("杠铃片配重", Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumField("目标总重量", target, { target = it }, Modifier.fillMaxWidth(), suffix = "kg")
            Text("杠铃杆重量")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(20.0 to "标准杆 20kg", 15.0 to "女子杆 15kg", 10.0 to "短杆 10kg").forEach { (b, s) -> FilterChip(bar == b, { bar = b }, { Text(s) }) }
            }
            SectionCard(title = "每侧需要") {
                if (used.isEmpty()) Text("只需空杆")
                used.groupBy { it }.forEach { (p, l) -> ListRow("${p.fmt(2)} kg 片", trailing = { Text("× ${l.size}", fontWeight = FontWeight.Bold) }) }
                Spacer(Modifier.height(6.dp))
                Text("实际总重：${actual.fmt(2)} kg" + if (kotlin.math.abs(actual - t) > 0.01) "（无法精确凑出 ${t.fmt()} kg）" else "", fontWeight = FontWeight.Medium)
            }
            SectionCard(title = "热身组建议") {
                if (t > bar) {
                    listOf(0.0 to 10, 0.4 to 5, 0.6 to 3, 0.8 to 2).forEach { (pct, reps) ->
                        val ww = if (pct == 0.0) bar else (bar + ((t * pct - bar) / 2.5).toInt() * 2.5).coerceAtLeast(bar)
                        Text("${ww.fmt(1)} kg × $reps 次")
                    }
                    Text("然后进入正式组 ${t.fmt()} kg", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ===================== 成就 =====================

@Composable
fun AchievementsScreen() {
    val list = Knowledge.achievements.map { it to it.done() }
    SubPage("成就徽章", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("已解锁 ${list.count { it.second }} / ${list.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { list.count { it.second }.toFloat() / list.size }, modifier = Modifier.fillMaxWidth().height(8.dp), drawStopIndicator = {})
            }
            items(list) { (a, done) ->
                Card(colors = CardDefaults.cardColors(containerColor = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (done) "🏆" else "🔒", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.title, fontWeight = FontWeight.Bold)
                            Text(a.desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ===================== 提醒 =====================

@Composable
fun RemindersScreen() {
    val ctx = LocalContext.current
    SubPage("提醒设置", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("瘦子增肌最容易忘记的就是按时吃饭。开启提醒，让 App 帮你记住。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(Store.reminders, key = { it.key }) { r ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("%02d:%02d".format(r.hour, r.minute), fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickableNoRipple {
                                    TimePickerDialog(ctx, { _, h, m ->
                                        Store.updateReminder(r.copy(hour = h, minute = m)); Reminders.scheduleAll(ctx)
                                    }, r.hour, r.minute, true).show()
                                })
                            Text(r.title, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(r.enabled, { on ->
                            if (on) (ctx as? MainActivity)?.ensureNotificationPermission()
                            Store.updateReminder(r.copy(enabled = on)); Reminders.scheduleAll(ctx)
                        })
                    }
                }
            }
            item {
                OutlinedButton({ Reminders.notify(ctx, 1, "增肌助手", "这是一条测试提醒 💪") }, Modifier.fillMaxWidth()) { Text("发送测试通知") }
                Text("点击时间可修改。部分手机需要在系统设置中允许本应用后台运行/自启动，提醒才能准时送达。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ===================== 知识 =====================

@Composable
fun KnowledgeListScreen() {
    SubPage("增肌知识库", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Knowledge.articles.groupBy { it.category }.forEach { (cat, list) ->
                item { Text(cat, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                items(list) { a ->
                    Card(onClick = { Nav.go(Route.ArticleR(Knowledge.articles.indexOf(a))) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(a.title, fontWeight = FontWeight.Bold)
                            Text(a.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Card(onClick = { Nav.go(Route.Supplements) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("补剂指南", fontWeight = FontWeight.Bold)
                        Text("哪些值得买，哪些是智商税", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleScreen(index: Int) {
    val a = Knowledge.articles[index.coerceIn(0, Knowledge.articles.lastIndex)]
    SubPage(a.title, Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Tag(a.category)
            Spacer(Modifier.height(12.dp))
            Text(a.body, style = MaterialTheme.typography.bodyLarge, lineHeight = 26.sp)
        }
    }
}

@Composable
fun SupplementsScreen() {
    SubPage("补剂指南", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("补剂只是锦上添花：吃够、练对、睡好是 95%，补剂最多 5%。", fontWeight = FontWeight.Medium)
            }
            items(Knowledge.supplements) { s ->
                SectionCard(title = s.name) {
                    Text(s.rating, color = FatColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("作用：${s.what}")
                    Text("用法：${s.how}")
                    Text("注意：${s.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ===================== 数据 =====================

@Composable
fun DataScreen() {
    val ctx = LocalContext.current
    var importing by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    SubPage("数据备份与恢复", Nav::back) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "数据概况") {
                Text("训练 ${Store.workouts.size} 次 · 饮食 ${Store.foods.size} 条 · 体重 ${Store.weights.size} 条 · 围度 ${Store.measures.size} 条 · 睡眠 ${Store.sleeps.size} 条")
            }
            Button({
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Store.exportJson())
                    .putExtra(Intent.EXTRA_SUBJECT, "增肌助手数据备份")
                ctx.startActivity(Intent.createChooser(send, "导出备份到…"))
            }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Upload, null); Text(" 导出备份（分享为文本）") }
            OutlinedButton({ importing = true }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.Download, null); Text(" 从备份恢复（粘贴文本）") }
            OutlinedButton({ resetting = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.DeleteForever, null); Text(" 清除所有数据")
            }
            Text("数据只保存在本机，卸载应用会丢失。建议定期导出备份发送到微信「文件传输助手」或备忘录。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (importing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("粘贴备份内容") },
            text = { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(200.dp)) },
            confirmButton = {
                TextButton({
                    val ok = Store.importJson(text.trim())
                    Toast.makeText(ctx, if (ok) "恢复成功" else "格式不正确", Toast.LENGTH_SHORT).show()
                    if (ok) { importing = false; Reminders.scheduleAll(ctx) }
                }) { Text("恢复") }
            },
            dismissButton = { TextButton({ importing = false }) { Text("取消") } },
        )
    }
    if (resetting) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("确定清除所有数据？") },
            text = { Text("此操作不可撤销，建议先导出备份。") },
            confirmButton = { TextButton({ resetting = false; Store.resetAll(); Reminders.scheduleAll(ctx); Nav.stack.clear(); Nav.stack.add(Route.Main); Nav.tab = 0 }) { Text("清除") } },
            dismissButton = { TextButton({ resetting = false }) { Text("取消") } },
        )
    }
}
