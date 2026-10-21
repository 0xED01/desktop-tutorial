package com.muscleup.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muscleup.app.data.*
import java.time.LocalDate
import java.time.LocalTime

val ACTIVITY_LEVELS = listOf(
    1.2 to "久坐（几乎不运动）",
    1.375 to "轻度（每周训练 1-3 天）",
    1.55 to "中度（每周训练 3-5 天）",
    1.725 to "高度（每周训练 6-7 天）",
    1.9 to "极高（体力劳动+训练）",
)
val LEVELS = listOf("新手（<6个月）", "进阶（6个月-2年）", "熟练（>2年）")

@Composable
fun ProfileForm(initial: Profile, buttonText: String, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var male by remember { mutableStateOf(initial.male) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var height by remember { mutableStateOf(initial.heightCm.fmt()) }
    var weight by remember { mutableStateOf(initial.weightKg.fmt()) }
    var target by remember { mutableStateOf(initial.targetWeightKg.fmt()) }
    var activity by remember { mutableDoubleStateOf(initial.activity) }
    var surplus by remember { mutableIntStateOf(initial.surplus) }
    var level by remember { mutableIntStateOf(initial.level) }

    val p = Profile(
        name.ifBlank { "健身新人" }, male, age.toIntOrNull() ?: 0, height.toDoubleOrNull() ?: 0.0,
        weight.toDoubleOrNull() ?: 0.0, target.toDoubleOrNull() ?: 0.0, activity, surplus, level, true,
    )
    val valid = p.age in 12..90 && p.heightCm in 100.0..230.0 && p.weightKg in 30.0..200.0 && p.targetWeightKg in 30.0..200.0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(male, { male = true }, { Text("男") })
            FilterChip(!male, { male = false }, { Text("女") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("年龄", age, { age = it }, Modifier.weight(1f), decimal = false)
            NumField("身高", height, { height = it }, Modifier.weight(1f), suffix = "cm")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("当前体重", weight, { weight = it }, Modifier.weight(1f), suffix = "kg")
            NumField("目标体重", target, { target = it }, Modifier.weight(1f), suffix = "kg")
        }
        Text("日常活动水平", style = MaterialTheme.typography.titleSmall)
        ACTIVITY_LEVELS.forEach { (v, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(activity == v, { activity = v })
                Text(label)
            }
        }
        Text("训练经验", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LEVELS.forEachIndexed { i, s -> FilterChip(level == i, { level = i }, { Text(s.substringBefore("（")) }) }
        }
        Text("每日热量盈余：+$surplus kcal", style = MaterialTheme.typography.titleSmall)
        Slider(surplus.toFloat(), { surplus = (it / 50).toInt() * 50 }, valueRange = 200f..800f, steps = 11)
        Text(
            when {
                surplus <= 300 -> "精益增肌：脂肪增长少，速度较慢"
                surplus <= 500 -> "标准增肌：推荐瘦子使用"
                else -> "快速增重：适合极难长肉的体质，注意腰围变化"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (valid) {
            val t = p.targets()
            SectionCard(title = "你的每日目标") {
                Text("基础代谢 ${t.bmr} kcal · 每日消耗 ${t.tdee} kcal", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text("${t.kcal} kcal", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("蛋白质 ${t.protein}g · 碳水 ${t.carbs}g · 脂肪 ${t.fat}g · 饮水 ${t.waterMl}ml")
            }
        }
        Button(onClick = { onSave(p) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(buttonText) }
    }
}

@Composable
fun Onboarding() {
    Scaffold { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Icon(Icons.Filled.FitnessCenter, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text("欢迎使用增肌助手", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("填写基础信息，为你定制热量、营养和训练方案。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            ProfileForm(Profile(), "开始增肌之旅") { p ->
                Store.updateProfile(p)
                Store.addWeight(today(), p.weightKg)
                Store.setActivePlan(if (p.level == 0) "beginner_fullbody" else "upper_lower")
            }
        }
    }
}

@Composable
fun ProfileEditScreen() {
    SubPage("个人资料", Nav::back) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ProfileForm(Store.profile.copy(weightKg = Store.currentWeight()), "保存") { p ->
                Store.updateProfile(p)
                if (p.weightKg != Store.currentWeight()) Store.addWeight(today(), p.weightKg)
                Nav.back()
            }
        }
    }
}

@Composable
fun HomeTab(modifier: Modifier) {
    val p = Store.profile
    val w = Store.currentWeight()
    val t = p.targets(w)
    val todayFoods = Store.foodsOn(today())
    val kcal = todayFoods.sumOf { it.kcal }
    val protein = todayFoods.sumOf { it.protein }
    val water = Store.water[today()] ?: 0
    val plan = Plans.byId(Store.activePlanId)
    val day = plan.days[Store.planCursor.coerceIn(0, plan.days.lastIndex)]
    val trainedToday = Store.workouts.any { it.date == today() }
    val hour = LocalTime.now().hour
    val greet = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..18 -> "下午好"
        else -> "晚上好"
    }
    val tip = Knowledge.tips[(today() % Knowledge.tips.size).toInt()]

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("$greet，${p.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(LocalDate.now().let { "${it.monthValue}月${it.dayOfMonth}日 · 增肌第 ${(today() - (Store.weights.firstOrNull()?.date ?: today()) + 1)} 天" },
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 体重进度
        SectionCard(title = "体重进度") {
            val start = Store.weights.firstOrNull()?.weight ?: w
            val gained = w - start
            val total = (p.targetWeightKg - start).coerceAtLeast(0.1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(w.fmt(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(" kg", modifier = Modifier.padding(bottom = 5.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("目标 ${p.targetWeightKg.fmt()} kg", style = MaterialTheme.typography.bodySmall)
                    Text((if (gained >= 0) "+" else "") + gained.fmt() + " kg", color = if (gained >= 0) GoodColor else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (gained / total).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(6.dp))
            Text("BMI ${bmi(w, p.heightCm).fmt()}（${bmiLabel(bmi(w, p.heightCm))}）· 还差 ${(p.targetWeightKg - w).coerceAtLeast(0.0).fmt()} kg，按每周 0.4 kg 约需 ${((p.targetWeightKg - w).coerceAtLeast(0.0) / 0.4).toInt()} 周",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 今日营养
        SectionCard(title = "今日营养", trailing = { TextButton({ Nav.tab = 2 }) { Text("记录饮食") } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring((kcal / t.kcal).toFloat(), MaterialTheme.colorScheme.primary, 110) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kcal.fmt(0), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("/${t.kcal}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    MacroBar("蛋白质", protein, t.protein, ProteinColor)
                    MacroBar("碳水", todayFoods.sumOf { it.carbs }, t.carbs, CarbColor)
                    MacroBar("脂肪", todayFoods.sumOf { it.fat }, t.fat, FatColor)
                }
            }
            val left = t.kcal - kcal
            Spacer(Modifier.height(6.dp))
            Text(
                if (left > 0) "还需摄入 ${left.fmt(0)} kcal，约等于 ${suggestFill(left)}" else "🎉 今日热量已达标！",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 今日训练
        SectionCard(title = "今日训练", trailing = { TextButton({ Nav.tab = 1 }) { Text("更多计划") } }) {
            if (trainedToday) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = GoodColor)
                    Spacer(Modifier.width(8.dp))
                    Text("今天已完成训练，好好吃饭、好好睡觉！", fontWeight = FontWeight.Medium)
                }
            } else {
                Text(plan.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${day.name} · ${day.focus}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(day.items.joinToString("、") { Exercises.byId(it.exId).name }, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { Session.startFromPlan(plan, Store.planCursor); Nav.go(Route.Session) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (Session.active) "继续训练" else "开始训练")
                }
            }
        }

        // 饮水
        SectionCard(title = "饮水 ${water} / ${t.waterMl} ml") {
            LinearProgressIndicator(
                progress = { (water.toFloat() / t.waterMl).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp), color = CarbColor, drawStopIndicator = {},
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 300, 500).forEach { ml ->
                    OutlinedButton({ Store.addWater(today(), ml) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+$ml") }
                }
                OutlinedButton({ Store.addWater(today(), -200) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("-200") }
            }
        }

        // 数据统计
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val weekStart = today() - LocalDate.now().dayOfWeek.value + 1
            StatBox(Modifier.weight(1f), "${Store.workouts.filter { it.date >= weekStart }.map { it.date }.distinct().size}", "本周训练")
            StatBox(Modifier.weight(1f), "${Store.weekStreak()}", "连续达标周")
            StatBox(Modifier.weight(1f), "${Store.trainingDays()}", "累计训练天")
        }

        SectionCard(title = "💡 今日小贴士") { Text(tip) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Modifier.weight(1f), Icons.Filled.Timer, "休息计时") { Nav.go(Route.Timer) }
            QuickAction(Modifier.weight(1f), Icons.AutoMirrored.Filled.MenuBook, "增肌知识") { Nav.go(Route.KnowledgeList) }
            QuickAction(Modifier.weight(1f), Icons.Filled.LocalDrink, "增肌食谱") { Nav.go(Route.Recipes) }
            QuickAction(Modifier.weight(1f), Icons.Filled.EmojiEvents, "成就") { Nav.go(Route.Achievements) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

fun suggestFill(kcal: Double): String {
    val shakes = kcal / 820
    return if (kcal > 600) "${shakes.fmt(1)} 杯增肌奶昔" else "${(kcal / 180).fmt(1)} 把坚果"
}

@Composable
fun QuickAction(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
