package com.islandpet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandpet.island.IslandPrefs
import com.islandpet.island.IslandService
import com.islandpet.model.Achievement
import com.islandpet.model.GameState
import com.islandpet.model.PetRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(state: GameState, onOpenSetup: () -> Unit) {
    val pet = state.pet ?: return
    var page by rememberSaveable { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("档案", state.coins)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PetCanvas(pet, 84.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(pet.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("${pet.species.label} · Lv.${pet.level} · ${pet.formTitle}")
                        val fmt = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
                        Text("出生于 ${fmt.format(Date(pet.birthday))}，已陪伴 ${pet.ageDays + 1} 天", fontSize = 12.sp, color = Dim)
                        Text(
                            "照顾评分 ${pet.careScore.toInt()} · 喂食 ${state.counters.feeds} · 玩耍 ${state.counters.plays} · 摸摸 ${state.counters.pats}",
                            fontSize = 12.sp, color = Dim,
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
            ) {
                listOf("成就", "日记", "设置").forEachIndexed { i, label ->
                    Text(
                        label, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (page == i) Orange.copy(alpha = 0.6f) else Color.Transparent)
                            .clickable { page = i }
                            .padding(vertical = 8.dp),
                    )
                }
            }

            when (page) {
                0 -> Achievement.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { a ->
                            val done = a.name in state.achievements
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = if (done) 0.12f else 0.05f))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(a.emoji, fontSize = 32.sp, modifier = Modifier.alpha(if (done) 1f else 0.35f))
                                Text(a.title, fontWeight = FontWeight.Bold)
                                Text(a.detail, fontSize = 11.sp, color = Dim, textAlign = TextAlign.Center)
                                Text(
                                    if (done) "已达成" else "奖励 💰${a.reward}", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (done) Green else Yellow,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                1 -> CardBox {
                    if (state.diary.isEmpty()) Text("还没有日记", color = Dim)
                    val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
                    state.diary.take(100).forEach { e ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Text(e.emoji, modifier = Modifier.padding(end = 10.dp))
                            Column {
                                Text(e.text, fontSize = 14.sp)
                                Text(fmt.format(Date(e.date)), fontSize = 11.sp, color = Dim)
                            }
                        }
                    }
                }
                else -> {
                    CardBox(Modifier.clickable(onClick = onOpenSetup)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏝️ 灵动岛设置", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(if (IslandPrefs.enabled(ctx)) "已开启 ›" else "未开启 ›", color = Orange)
                        }
                    }
                    CardBox(Modifier.clickable { renaming = true }) {
                        Text("✏️ 给宠物改名", fontWeight = FontWeight.Bold)
                    }
                    CardBox {
                        Text("玩法小贴士", fontWeight = FontWeight.Bold)
                        Text(
                            "• 属性会随真实时间变化，睡觉时消耗变慢、精力恢复\n" +
                                "• 便便放着不管、太脏或太饿，宠物会生病\n" +
                                "• 宠物偶尔会许愿，满足心愿有额外奖励\n" +
                                "• 成年后的形态取决于你平时的照顾评分\n" +
                                "• 每种宠物猜拳都有自己偏爱的手势",
                            fontSize = 12.sp, color = Dim,
                        )
                    }
                    TextButton(onClick = { confirmReset = true }) { Text("送走宠物，重新开始", color = Red) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(pet.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("给宠物改名") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(8) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) PetRepo.act { s -> s.pet?.name = n; "以后就叫「$n」啦" }
                    renaming = false
                }) { Text("确定", color = Orange) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重新开始？") },
            text = { Text("当前宠物、金币和道具都会清空，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    PetRepo.reset()
                    IslandService.stop(ctx)
                    IslandPrefs.setEnabled(ctx, false)
                    confirmReset = false
                }) { Text("确定送走", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }
}
