package com.muscleup.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.data.*
import kotlinx.coroutines.delay
import java.time.LocalDate

// ===================== 训练会话状态 =====================

class SessionSet(weight: String, reps: String, done: Boolean = false) {
    var weight by mutableStateOf(weight)
    var reps by mutableStateOf(reps)
    var done by mutableStateOf(done)
}

class SessionExercise(val exId: String, val target: String, val restSec: Int, val sets: SnapshotStateList<SessionSet>)

object Session {
    var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var startMs by mutableLongStateOf(0L)
    var fromPlan by mutableStateOf(false)
    val exercises = mutableStateListOf<SessionExercise>()
    var restEndMs by mutableLongStateOf(0L)
    var restTotal by mutableIntStateOf(0)

    fun startFromPlan(plan: Plan, dayIdx: Int) {
        if (active) return
        val day = plan.days[dayIdx.coerceIn(0, plan.days.lastIndex)]
        exercises.clear()
        day.items.forEach { addExercise(it.exId, "${it.sets}×${it.reps}", it.restSec, it.sets, it.reps) }
        title = "${plan.name.substringBefore("（")} · ${day.name}"
        fromPlan = plan.id == Store.activePlanId && dayIdx == Store.planCursor
        startMs = System.currentTimeMillis()
        active = true
    }

    fun startEmpty() {
        if (active) return
        exercises.clear()
        title = "自由训练"
        fromPlan = false
        startMs = System.currentTimeMillis()
        active = true
    }

    fun addExercise(exId: String, target: String = "3×8-12", rest: Int = 90, setCount: Int = 3, reps: String = "8-12") {
        val last = Store.lastEntry(exId)
        val lowReps = Regex("\\d+").find(reps)?.value ?: ""
        val sets = (0 until setCount).map { i ->
            val prev = last?.sets?.getOrNull(i) ?: last?.sets?.lastOrNull()
            SessionSet(prev?.weight?.fmt() ?: "", lowReps.ifEmpty { prev?.reps?.toString() ?: "" })
        }
        exercises.add(SessionExercise(exId, target, rest, mutableStateListOf(*sets.toTypedArray())))
    }

    fun startRest(sec: Int) {
        restTotal = sec
        restEndMs = System.currentTimeMillis() + sec * 1000L
    }

    fun finish(note: String): WorkoutLog? {
        val entries = exercises.mapNotNull { e ->
            val sets = e.sets.filter { it.done }.mapNotNull { s ->
                val r = s.reps.toIntOrNull() ?: return@mapNotNull null
                SetRec(s.weight.toDoubleOrNull() ?: 0.0, r)
            }
            if (sets.isEmpty()) null else ExerciseEntry(e.exId, sets)
        }
        if (entries.isEmpty()) return null
        val log = WorkoutLog(
            System.currentTimeMillis(), today(), title, entries,
            ((System.currentTimeMillis() - startMs) / 60000).toInt().coerceAtLeast(1), note,
        )
        Store.addWorkout(log, fromPlan)
        discard()
        return log
    }

    fun discard() {
        active = false
        exercises.clear()
        restEndMs = 0
    }
}

/** 根据上次表现给出渐进超负荷建议 */
fun overloadAdvice(exId: String, target: String): String? {
    val last = Store.lastEntry(exId) ?: return null
    val ex = Exercises.byId(exId)
    val nums = Regex("\\d+").findAll(target.substringAfter("×")).map { it.value.toInt() }.toList()
    val top = nums.lastOrNull()
    val lastText = last.sets.joinToString(" ") { "${it.weight.fmt()}×${it.reps}" }
    if (top == null || last.sets.all { it.weight == 0.0 }) return "上次：$lastText · 争取多做 1-2 次"
    val inc = if (ex.equipment == "杠铃" && ex.compound) 2.5 else if (ex.compound) 2.0 else 1.0
    return if (last.sets.all { it.reps >= top }) {
        "上次：$lastText · 全部达到 $top 次，建议加重 ${inc.fmt()} kg 💪"
    } else {
        "上次：$lastText · 保持重量，争取每组多做 1 次"
    }
}

/** 计时页面的实时刷新循环；UI 测试中关闭以便 Compose 进入空闲 */
object LiveTicker {
    var enabled = true
}

fun vibrate(ctx: Context, ms: Long = 600) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, ms, 200, ms), -1))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    }
}

// ===================== 训练主页 =====================

@Composable
fun TrainTab(modifier: Modifier) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        TabRow(selectedTabIndex = sub) {
            listOf("训练计划", "动作库", "训练记录").forEachIndexed { i, s ->
                Tab(sub == i, { sub = i }, text = { Text(s) })
            }
        }
        when (sub) {
            0 -> PlansPane()
            1 -> ExerciseLibraryContent(pickMode = false)
            else -> HistoryPane()
        }
    }
}

@Composable
private fun PlansPane() {
    val active = Plans.byId(Store.activePlanId)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = "当前计划：${active.name}") {
                val day = active.days[Store.planCursor.coerceIn(0, active.days.lastIndex)]
                Text("下一次：${day.name}（${day.focus}）", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ Session.startFromPlan(active, Store.planCursor); Nav.go(Route.Session) }, Modifier.weight(1f)) {
                        Text(if (Session.active) "继续训练" else "开始训练")
                    }
                    OutlinedButton({ Session.startEmpty(); Nav.go(Route.Session) }, Modifier.weight(1f)) { Text("自由训练") }
                }
            }
        }
        item {
            Text("所有计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(Plans.all) { plan ->
            Card(onClick = { Nav.go(Route.PlanDetail(plan.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (plan.id == Store.activePlanId) Tag("使用中", GoodColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(plan.level); Tag(plan.perWeek, CarbColor); Tag(plan.place.substringBefore("（"), FatColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(plan.desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun PlanDetailScreen(id: String) {
    val plan = Plans.byId(id)
    SubPage(plan.name, Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(plan.level); Tag(plan.perWeek, CarbColor); Tag(plan.place, FatColor) }
                    Spacer(Modifier.height(8.dp))
                    Text(plan.desc)
                    Spacer(Modifier.height(12.dp))
                    if (plan.id == Store.activePlanId) {
                        OutlinedButton({}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("当前正在使用此计划") }
                    } else {
                        Button({ Store.setActivePlan(plan.id) }, Modifier.fillMaxWidth()) { Text("设为我的计划") }
                    }
                }
            }
            itemsIndexed(plan.days) { idx, day ->
                SectionCard(title = "${day.name} · ${day.focus}", trailing = {
                    TextButton({ Session.startFromPlan(plan, idx); Nav.go(Route.Session) }) { Text("练这天") }
                }) {
                    day.items.forEach { it ->
                        val ex = Exercises.byId(it.exId)
                        ListRow(ex.name, "${it.sets} 组 × ${it.reps} · 休息 ${it.restSec}s", trailing = { Tag(ex.muscle) }) {
                            Nav.go(Route.ExerciseDetail(ex.id))
                        }
                    }
                    if (plan.id == Store.activePlanId && idx != Store.planCursor) {
                        TextButton({ Store.setCursor(idx) }) { Text("将下次训练设为这天") }
                    }
                }
            }
        }
    }
}

// ===================== 训练进行中 =====================

@Composable
fun SessionScreen() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showFinish by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<WorkoutLog?>(null) }
    LaunchedEffect(Unit) {
        var wasResting = false
        while (LiveTicker.enabled) {
            now = System.currentTimeMillis()
            val resting = Session.restEndMs > now
            if (wasResting && !resting && Session.restEndMs != 0L) {
                vibrate(ctx)
                Session.restEndMs = 0
            }
            wasResting = resting
            delay(250)
        }
    }
    if (!Session.active && saved == null) {
        LaunchedEffect(Unit) { Nav.back() }
        return
    }
    saved?.let { log ->
        WorkoutSummary(log)
        return
    }

    SubPage(Session.title, Nav::back, actions = {
        IconButton({ showDiscard = true }) { Icon(Icons.Filled.Delete, "放弃") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            val elapsed = (now - Session.startMs) / 1000
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp))
                Text(" %02d:%02d".format(elapsed / 60, elapsed % 60), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                val doneSets = Session.exercises.sumOf { e -> e.sets.count { it.done } }
                val volume = Session.exercises.sumOf { e -> e.sets.filter { it.done }.sumOf { (it.weight.toDoubleOrNull() ?: 0.0) * (it.reps.toIntOrNull() ?: 0) } }
                Text("已完成 $doneSets 组 · 容量 ${volume.fmt(0)} kg", style = MaterialTheme.typography.bodySmall)
            }
            if (Session.restEndMs > now) {
                val left = ((Session.restEndMs - now) / 1000 + 1).toInt()
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("组间休息", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        Text("%d:%02d".format(left / 60, left % 60), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton({ Session.restEndMs += 15000 }) { Text("+15s") }
                        TextButton({ Session.restEndMs = 0 }) { Text("跳过") }
                    }
                    LinearProgressIndicator(
                        progress = { ((Session.restEndMs - now).toFloat() / (Session.restTotal * 1000)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), drawStopIndicator = {},
                    )
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(Session.exercises, key = { i, e -> "$i-${e.exId}" }) { idx, e ->
                    SessionExerciseCard(idx, e)
                }
                item {
                    OutlinedButton({ Nav.go(Route.ExercisePicker) }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null); Text(" 添加动作")
                    }
                }
            }
            Button({ showFinish = true }, Modifier.fillMaxWidth().padding(16.dp).height(50.dp)) { Text("完成训练") }
        }
    }

    if (showFinish) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text("完成训练") },
            text = {
                Column {
                    Text("只有勾选完成的组会被记录。")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(note, { note = it }, label = { Text("训练感受（可选）") })
                }
            },
            confirmButton = {
                TextButton({
                    showFinish = false
                    val log = Session.finish(note)
                    if (log != null) saved = log
                }) { Text("保存") }
            },
            dismissButton = { TextButton({ showFinish = false }) { Text("继续练") } },
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("放弃本次训练？") },
            text = { Text("本次训练数据将不会保存。") },
            confirmButton = { TextButton({ showDiscard = false; Session.discard(); Nav.back() }) { Text("放弃") } },
            dismissButton = { TextButton({ showDiscard = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SessionExerciseCard(idx: Int, e: SessionExercise) {
    val ex = Exercises.byId(e.exId)
    val advice = remember(e.exId) { overloadAdvice(e.exId, e.target) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("目标 ${e.target} · ${ex.muscle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({ Nav.go(Route.ExerciseDetail(ex.id)) }) { Icon(Icons.Filled.Info, "说明") }
            IconButton({ Session.exercises.removeAt(idx) }) { Icon(Icons.Filled.Close, "移除") }
        }
        if (advice != null) {
            Text(advice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("组", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
            Text("重量 kg", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("次数", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("完成", Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        e.sets.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(28.dp), fontWeight = FontWeight.Bold)
                CompactField(s.weight, { s.weight = it }, Modifier.weight(1f).padding(end = 6.dp), decimal = true)
                CompactField(s.reps, { s.reps = it }, Modifier.weight(1f).padding(end = 6.dp), decimal = false)
                Checkbox(s.done, { checked ->
                    s.done = checked
                    if (checked) {
                        // 自动填充下一组
                        e.sets.getOrNull(i + 1)?.let { n -> if (n.weight.isEmpty()) n.weight = s.weight; if (n.reps.isEmpty()) n.reps = s.reps }
                        Session.startRest(e.restSec)
                    }
                }, Modifier.width(48.dp))
            }
        }
        Row {
            TextButton({
                val l = e.sets.lastOrNull()
                e.sets.add(SessionSet(l?.weight ?: "", l?.reps ?: ""))
            }) { Text("+ 加一组") }
            if (e.sets.size > 1) TextButton({ e.sets.removeAt(e.sets.lastIndex) }) { Text("- 减一组") }
            Spacer(Modifier.weight(1f))
            val best = e.sets.filter { it.done }.maxOfOrNull { oneRm(it.weight.toDoubleOrNull() ?: 0.0, it.reps.toIntOrNull() ?: 0) } ?: 0.0
            if (best > 0) Text("估算1RM ${best.fmt(0)}kg", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }
}

@Composable
private fun CompactField(value: String, onChange: (String) -> Unit, modifier: Modifier, decimal: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.isEmpty() || s.matches(Regex(if (decimal) "\\d{0,4}\\.?\\d{0,2}" else "\\d{0,3}"))) onChange(s) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (decimal) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Number,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun WorkoutSummary(log: WorkoutLog) {
    SubPage("训练完成", Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 64.sp)
            Text("太棒了！又向目标迈进了一步", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(Modifier.weight(1f), "${log.durationMin}", "分钟")
                StatBox(Modifier.weight(1f), "${log.setCount}", "组数")
                StatBox(Modifier.weight(1f), log.volume.fmt(0), "总容量 kg")
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "训练后营养") {
                Text("训练后 1-2 小时内吃一顿含 30-40g 蛋白质 + 充足碳水的正餐，或来一杯增肌奶昔（蛋白粉+燕麦+牛奶+香蕉）。")
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "本次记录") {
                log.entries.forEach { en ->
                    ListRow(Exercises.byId(en.exerciseId).name, en.sets.joinToString("  ") { "${it.weight.fmt()}×${it.reps}" })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button({ Nav.back() }, Modifier.fillMaxWidth()) { Text("返回") }
        }
    }
}

// ===================== 动作库 =====================

@Composable
fun ExerciseLibraryScreen(pickMode: Boolean) {
    SubPage(if (pickMode) "选择动作" else "动作库", Nav::back) { pad ->
        Box(Modifier.padding(pad)) { ExerciseLibraryContent(pickMode) }
    }
}

@Composable
fun ExerciseLibraryContent(pickMode: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equip by rememberSaveable { mutableStateOf<String?>(null) }
    val list = Exercises.all.filter {
        (muscle == null || it.muscle == muscle) && (equip == null || it.equipment == equip) &&
            (query.isBlank() || it.name.contains(query) || it.secondary.contains(query))
    }
    Column {
        OutlinedTextField(
            query, { query = it }, placeholder = { Text("搜索动作") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(muscle == null, { muscle = null }, { Text("全部部位") })
            MUSCLES.forEach { m -> FilterChip(muscle == m, { muscle = if (muscle == m) null else m }, { Text(m) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(equip == null, { equip = null }, { Text("全部器械") })
            EQUIPMENT.forEach { m -> FilterChip(equip == m, { equip = if (equip == m) null else m }, { Text(m) }) }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item { Text("共 ${list.size} 个动作", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.id }) { ex ->
                ListRow(
                    ex.name,
                    "${ex.muscle}${if (ex.secondary.isNotEmpty()) "（${ex.secondary}）" else ""} · ${ex.equipment} · ${"★".repeat(ex.level)}",
                    leading = { MuscleBadge(ex.muscle) },
                    trailing = {
                        if (pickMode) Icon(Icons.Filled.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                        else if (ex.compound) Tag("复合")
                    },
                ) {
                    if (pickMode) {
                        Session.addExercise(ex.id, if (ex.compound) "3×6-10" else "3×10-15", if (ex.compound) 120 else 60, 3, if (ex.compound) "6-10" else "10-15")
                        Nav.back()
                    } else {
                        Nav.go(Route.ExerciseDetail(ex.id))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun MuscleBadge(muscle: String) {
    val colors = listOf(Brand, CarbColor, FatColor, GoodColor, ProteinColor, androidx.compose.ui.graphics.Color(0xFF7048E8), androidx.compose.ui.graphics.Color(0xFF0CA678), androidx.compose.ui.graphics.Color(0xFFAE3EC9))
    val c = colors[MUSCLES.indexOf(muscle).coerceAtLeast(0) % colors.size]
    Surface(shape = MaterialTheme.shapes.medium, color = c.copy(alpha = 0.15f), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(muscle.take(2), color = c, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ExerciseDetailScreen(id: String) {
    val ex = Exercises.byId(id)
    SubPage(ex.name, Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("主练：${ex.muscle}")
                if (ex.secondary.isNotEmpty()) Tag("辅助：${ex.secondary}", CarbColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(ex.equipment, FatColor); Tag(if (ex.compound) "复合动作" else "孤立动作", GoodColor); Tag("难度 ${"★".repeat(ex.level)}", ProteinColor)
            }
            SectionCard(title = "动作步骤") {
                ex.steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                        Text(s)
                    }
                }
            }
            SectionCard(title = "✅ 要点") { Text(ex.tips) }
            SectionCard(title = "⚠️ 常见错误") { Text(ex.mistakes) }
            val best = Store.bestOneRm(id)
            val last = Store.lastEntry(id)
            SectionCard(title = "我的数据") {
                if (last == null) Text("还没有这个动作的记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text("最近一次：" + last.sets.joinToString("  ") { "${it.weight.fmt()}kg×${it.reps}" })
                    Text("最佳估算 1RM：${best.fmt(1)} kg", fontWeight = FontWeight.Bold)
                    TextButton({ Nav.go(Route.ExerciseProgress(id)) }) { Text("查看进步曲线 →") }
                }
            }
            if (Session.active) {
                Button({ Session.addExercise(id); Nav.back() }, Modifier.fillMaxWidth()) { Text("添加到当前训练") }
            }
        }
    }
}

@Composable
fun ExerciseProgressScreen(id: String) {
    val ex = Exercises.byId(id)
    val history = Store.workouts.filter { w -> w.entries.any { it.exerciseId == id } }.sortedBy { it.date }
    val points = history.map { w ->
        val d = LocalDate.ofEpochDay(w.date)
        "${d.monthValue}/${d.dayOfMonth}" to w.entries.filter { it.exerciseId == id }.flatMap { it.sets }.maxOf { oneRm(it.weight, it.reps) }
    }
    SubPage("${ex.name} 进步曲线", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionCard(title = "估算 1RM 趋势（kg）") { LineChart(points.takeLast(20)) } }
            items(history.reversed()) { w ->
                val d = LocalDate.ofEpochDay(w.date)
                SectionCard {
                    Text("${d.year}-${d.monthValue}-${d.dayOfMonth}", fontWeight = FontWeight.Bold)
                    Text(w.entries.filter { it.exerciseId == id }.flatMap { it.sets }.joinToString("  ") { "${it.weight.fmt()}×${it.reps}" })
                }
            }
        }
    }
}

// ===================== 训练记录 =====================

@Composable
private fun HistoryPane() {
    val ws = Store.workouts
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // 近 8 周训练次数
            val thisWeek = (today() + 3) / 7
            val bars = (7 downTo 0).map { k ->
                val wk = thisWeek - k
                (if (k == 0) "本周" else "-$k") to ws.filter { (it.date + 3) / 7 == wk }.map { it.date }.distinct().size.toDouble()
            }
            SectionCard(title = "近 8 周训练天数") { BarChart(bars) }
        }
        item {
            // 近 30 天各肌群训练组数
            val since = today() - 30
            val counts = MUSCLES.map { m ->
                m to ws.filter { it.date > since }.flatMap { it.entries }.filter { Exercises.byId(it.exerciseId).muscle == m }.sumOf { it.sets.size }.toDouble()
            }
            SectionCard(title = "近 30 天各部位组数") {
                BarChart(counts, color = CarbColor)
                val weak = counts.filter { it.second < 20 && it.first != "核心" }.map { it.first }
                if (ws.isNotEmpty() && weak.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("提示：${weak.joinToString("、")} 训练量偏少（建议每部位每周 10+ 组）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            OutlinedButton({ Nav.go(Route.Records) }, Modifier.fillMaxWidth()) { Icon(Icons.Filled.EmojiEvents, null); Text(" 个人最佳记录 PR") }
        }
        if (ws.isEmpty()) item { EmptyHint("还没有训练记录\n完成第一次训练后会显示在这里") }
        items(ws, key = { it.id }) { w ->
            val d = LocalDate.ofEpochDay(w.date)
            Card(onClick = { Nav.go(Route.WorkoutDetail(w.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text(w.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${d.monthValue}月${d.dayOfMonth}日", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${w.durationMin} 分钟 · ${w.entries.size} 个动作 · ${w.setCount} 组 · ${w.volume.fmt(0)} kg",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WorkoutDetailScreen(id: Long) {
    val w = Store.workouts.firstOrNull { it.id == id }
    if (w == null) {
        LaunchedEffect(Unit) { Nav.back() }; return
    }
    var confirm by remember { mutableStateOf(false) }
    val d = LocalDate.ofEpochDay(w.date)
    SubPage(w.title, Nav::back, actions = { IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "删除") } }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${d.year}年${d.monthValue}月${d.dayOfMonth}日", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(Modifier.weight(1f), "${w.durationMin}", "分钟")
                    StatBox(Modifier.weight(1f), "${w.setCount}", "组数")
                    StatBox(Modifier.weight(1f), w.volume.fmt(0), "容量 kg")
                }
                if (w.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp)); Text("备注：${w.note}")
                }
            }
            items(w.entries) { e ->
                val ex = Exercises.byId(e.exerciseId)
                SectionCard(title = ex.name, trailing = { TextButton({ Nav.go(Route.ExerciseProgress(ex.id)) }) { Text("曲线") } }) {
                    e.sets.forEachIndexed { i, s ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("第 ${i + 1} 组", Modifier.width(64.dp))
                            Text("${s.weight.fmt()} kg × ${s.reps}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("1RM≈${oneRm(s.weight, s.reps).fmt(0)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("删除这次训练记录？") },
            confirmButton = { TextButton({ confirm = false; Store.deleteWorkout(id); Nav.back() }) { Text("删除") } },
            dismissButton = { TextButton({ confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
fun RecordsScreen() {
    val prs = Store.workouts.flatMap { w -> w.entries.map { w to it } }
        .groupBy { it.second.exerciseId }
        .map { (exId, list) ->
            val best = list.flatMap { (w, e) -> e.sets.map { w to it } }.maxBy { oneRm(it.second.weight, it.second.reps) }
            Triple(exId, best.first, best.second)
        }
        .sortedByDescending { oneRm(it.third.weight, it.third.reps) }
    val bw = Store.currentWeight()
    SubPage("个人最佳 PR", Nav::back) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                SectionCard(title = "力量标准参考（估算 1RM / 体重）") {
                    listOf("bench_press" to listOf(0.75, 1.0, 1.25, 1.5), "squat" to listOf(1.0, 1.25, 1.5, 2.0), "deadlift" to listOf(1.25, 1.5, 2.0, 2.5), "ohp" to listOf(0.5, 0.65, 0.8, 1.0))
                        .forEach { (exId, std) ->
                            val v = Store.bestOneRm(exId)
                            val ratio = v / bw
                            val lv = listOf("入门", "新手", "中级", "进阶", "高级")[std.count { ratio >= it }]
                            ListRow(Exercises.byId(exId).name, "当前 ${v.fmt(0)} kg（${ratio.fmt(2)}×体重） · 中级标准 ${(std[1] * bw).fmt(0)} kg", trailing = { Tag(lv) })
                        }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (prs.isEmpty()) item { EmptyHint("暂无记录") }
            items(prs) { (exId, w, s) ->
                val d = LocalDate.ofEpochDay(w.date)
                ListRow(Exercises.byId(exId).name, "${s.weight.fmt()} kg × ${s.reps} · ${d.monthValue}/${d.dayOfMonth}",
                    trailing = { Text("${oneRm(s.weight, s.reps).fmt(0)} kg", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }) {
                    Nav.go(Route.ExerciseProgress(exId))
                }
                HorizontalDivider()
            }
        }
    }
}
