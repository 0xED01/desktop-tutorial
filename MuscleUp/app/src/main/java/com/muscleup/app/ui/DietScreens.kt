package com.muscleup.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muscleup.app.data.*
import java.time.LocalDate

fun dayLabel(date: Long): String {
    val t = today()
    val d = LocalDate.ofEpochDay(date)
    return when (date) {
        t -> "今天"
        t - 1 -> "昨天"
        t - 2 -> "前天"
        else -> "${d.monthValue}月${d.dayOfMonth}日"
    }
}

@Composable
fun DietTab(modifier: Modifier) {
    var date by rememberSaveable { mutableLongStateOf(today()) }
    var addMeal by remember { mutableStateOf<Int?>(null) }
    val list = Store.foodsOn(date)
    val t = Store.profile.targets(Store.currentWeight())
    val kcal = list.sumOf { it.kcal }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ date-- }) { Icon(Icons.Filled.ChevronLeft, "前一天") }
                Text(dayLabel(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ if (date < today()) date++ }, enabled = date < today()) { Icon(Icons.Filled.ChevronRight, "后一天") }
            }
        }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring((kcal / t.kcal).toFloat(), MaterialTheme.colorScheme.primary, 120) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("已摄入", style = MaterialTheme.typography.labelSmall)
                            Text(kcal.fmt(0), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("目标 ${t.kcal}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        MacroBar("蛋白质", list.sumOf { it.protein }, t.protein, ProteinColor)
                        MacroBar("碳水", list.sumOf { it.carbs }, t.carbs, CarbColor)
                        MacroBar("脂肪", list.sumOf { it.fat }, t.fat, FatColor)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val left = t.kcal - kcal
                val pLeft = t.protein - list.sumOf { it.protein }
                Text(
                    when {
                        left <= 0 && pLeft <= 0 -> "✅ 热量和蛋白质都达标了，继续保持！"
                        left <= 0 -> "热量达标，蛋白质还差 ${pLeft.fmt(0)}g，来点鸡蛋/牛奶/蛋白粉"
                        else -> "还差 ${left.fmt(0)} kcal${if (pLeft > 0) "、蛋白质 ${pLeft.fmt(0)}g" else ""}"
                    },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
            }
        }
        items(MEALS.size) { m ->
            val mealFoods = list.filter { it.meal == m }
            SectionCard(
                title = "${MEALS[m]}  ${mealFoods.sumOf { it.kcal }.fmt(0)} kcal",
                trailing = { IconButton({ addMeal = m }) { Icon(Icons.Filled.AddCircle, "添加", tint = MaterialTheme.colorScheme.primary) } },
            ) {
                if (mealFoods.isEmpty()) Text("点右上角 + 添加食物", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mealFoods.forEach { f ->
                    ListRow(
                        f.name,
                        "${f.grams.fmt(0)}g · 蛋白 ${f.protein.fmt(1)}g 碳水 ${f.carbs.fmt(1)}g 脂肪 ${f.fat.fmt(1)}g",
                        trailing = {
                            Text("${f.kcal.fmt(0)}", fontWeight = FontWeight.Bold)
                            IconButton({ Store.deleteFood(f.id) }) { Icon(Icons.Filled.Close, "删除", Modifier.size(18.dp)) }
                        },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val yesterday = Store.foodsOn(date - 1)
                OutlinedButton(
                    {
                        var id = System.currentTimeMillis()
                        yesterday.forEach { Store.addFood(it.copy(id = id++, date = date)) }
                    },
                    Modifier.weight(1f), enabled = yesterday.isNotEmpty(),
                ) { Text("复制前一天饮食") }
                OutlinedButton({ Nav.go(Route.Recipes) }, Modifier.weight(1f)) { Text("增肌食谱") }
            }
        }
        item {
            SectionCard(title = "增肌饮食小建议") {
                Text("• 每餐都要有蛋白质：蛋、奶、肉、鱼、豆腐\n• 吃不下就喝：牛奶、奶昔、豆浆\n• 多用高热量密度食物：坚果、花生酱、橄榄油\n• 训练前后各安排一餐碳水+蛋白", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    addMeal?.let { meal ->
        AddFoodDialog(date, meal) { addMeal = null }
    }
}

@Composable
fun AddFoodDialog(date: Long, initialMeal: Int, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<Food?>(null) }
    var creating by remember { mutableStateOf(false) }
    val recentNames = remember { Store.foods.sortedByDescending { it.id }.map { it.name }.distinct().take(8) }
    val all = Foods.all()
    val list = all.filter { (cat == null || it.category == cat) && (query.isBlank() || it.name.contains(query, true)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text("添加到${MEALS[initialMeal]}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ creating = true }) { Text("自定义食物") }
                }
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("搜索食物，如 鸡蛋、米饭") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(cat == null, { cat = null }, { Text("全部") })
                    FOOD_CATS.forEach { c -> FilterChip(cat == c, { cat = if (cat == c) null else c }, { Text(c) }) }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    if (query.isBlank() && cat == null && recentNames.isNotEmpty()) {
                        item { Text("最近吃过", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        items(recentNames.mapNotNull { n -> all.firstOrNull { it.name == n } }) { f -> FoodRow(f) { picked = f } }
                        item { HorizontalDivider(Modifier.padding(vertical = 6.dp)); Text("全部食物", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    }
                    items(list) { f -> FoodRow(f) { picked = f } }
                    if (list.isEmpty()) item { EmptyHint("没有找到「$query」\n可以点击右上角添加自定义食物") }
                }
            }
        }
    }

    picked?.let { f ->
        AmountDialog(f, initialMeal, onCancel = { picked = null }) { meal, grams ->
            val r = grams / 100.0
            Store.addFood(FoodLog(System.currentTimeMillis(), date, meal, f.name, grams, f.kcal * r, f.protein * r, f.carbs * r, f.fat * r))
            picked = null
            onDismiss()
        }
    }
    if (creating) CustomFoodDialog(query) { creating = false }
}

@Composable
private fun FoodRow(f: Food, onClick: () -> Unit) {
    ListRow(
        f.name,
        "${f.kcal.fmt(0)} kcal/100g · 蛋白 ${f.protein.fmt(1)}g · ${f.portionName}≈${f.portionG.fmt(0)}g",
        trailing = {
            if (f.category == "自定义") IconButton({ Store.deleteCustomFood(f.name) }) { Icon(Icons.Filled.Delete, "删除", Modifier.size(18.dp)) }
            else Text("${(f.kcal * f.portionG / 100).fmt(0)}/${f.portionName}", style = MaterialTheme.typography.labelMedium)
        },
        onClick = onClick,
    )
}

@Composable
private fun AmountDialog(f: Food, initialMeal: Int, onCancel: () -> Unit, onConfirm: (Int, Double) -> Unit) {
    var grams by remember { mutableStateOf(f.portionG.fmt(0)) }
    var meal by remember { mutableIntStateOf(initialMeal) }
    val g = grams.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(f.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5, 1.0, 1.5, 2.0).forEach { k ->
                        FilterChip(g == f.portionG * k, { grams = (f.portionG * k).fmt(0) }, { Text("${k.fmt()}${f.portionName.filter { !it.isDigit() }}") })
                    }
                }
                NumField("重量", grams, { grams = it }, Modifier.fillMaxWidth(), suffix = "克")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEALS.forEachIndexed { i, m -> FilterChip(meal == i, { meal = i }, { Text(m) }) }
                }
                val r = g / 100
                Text("${(f.kcal * r).fmt(0)} kcal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("蛋白质 ${(f.protein * r).fmt(1)}g · 碳水 ${(f.carbs * r).fmt(1)}g · 脂肪 ${(f.fat * r).fmt(1)}g")
            }
        },
        confirmButton = { TextButton({ onConfirm(meal, g) }, enabled = g > 0) { Text("添加") } },
        dismissButton = { TextButton(onCancel) { Text("取消") } },
    )
}

@Composable
private fun CustomFoodDialog(initialName: String, onDone: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("自定义食物（每100克）") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                NumField("热量", kcal, { kcal = it }, suffix = "kcal")
                NumField("蛋白质", p, { p = it }, suffix = "g")
                NumField("碳水", c, { c = it }, suffix = "g")
                NumField("脂肪", fat, { fat = it }, suffix = "g")
                NumField("常用一份的重量", portion, { portion = it }, suffix = "g")
                Text("提示：可参考食品包装上的营养成分表（注意 kJ ÷ 4.184 = kcal）", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({
                Store.addCustomFood(
                    Food(name.trim(), "自定义", kcal.toDoubleOrNull() ?: 0.0, p.toDoubleOrNull() ?: 0.0, c.toDoubleOrNull() ?: 0.0,
                        fat.toDoubleOrNull() ?: 0.0, "一份", portion.toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0),
                )
                onDone()
            }, enabled = name.isNotBlank() && kcal.toDoubleOrNull() != null) { Text("保存") }
        },
        dismissButton = { TextButton(onDone) { Text("取消") } },
    )
}

@Composable
fun RecipesScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SubPage("增肌食谱", Nav::back) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("一日食谱") })
                Tab(tab == 1, { tab = 1 }, text = { Text("奶昔 & 快手餐") })
            }
            val target = Store.profile.targets(Store.currentWeight()).kcal
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    item {
                        Text("你的每日目标约 $target kcal，选择最接近的一套参考：", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(Knowledge.mealPlans) { mp ->
                        val near = Knowledge.mealPlans.minBy { kotlin.math.abs(it.kcal - target) } == mp
                        SectionCard(title = mp.title, trailing = { if (near) Tag("推荐", GoodColor) }) {
                            Text("约 ${mp.kcal} kcal · 蛋白质 ${mp.protein}g", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            mp.meals.forEach { (m, desc) ->
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(m, Modifier.width(56.dp), fontWeight = FontWeight.Bold)
                                    Text(desc)
                                }
                            }
                        }
                    }
                } else {
                    items(Knowledge.recipes) { r ->
                        SectionCard(title = r.name, trailing = { Tag("${r.kcal} kcal") }) {
                            Text("蛋白质 ${r.protein}g", color = ProteinColor, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("食材：${r.ingredients}")
                            Spacer(Modifier.height(4.dp))
                            Text("做法：${r.steps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
