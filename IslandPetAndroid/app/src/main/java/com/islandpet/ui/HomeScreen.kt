package com.islandpet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandpet.island.IslandPrefs
import com.islandpet.island.OverlayHost
import com.islandpet.model.GameState
import com.islandpet.model.ItemCategory
import com.islandpet.model.Mood
import com.islandpet.model.PetRepo
import com.islandpet.model.ShopItem
import com.islandpet.model.Species
import com.islandpet.model.Stage
import com.islandpet.model.adopt
import com.islandpet.model.buy
import com.islandpet.model.checkIn
import com.islandpet.model.clean
import com.islandpet.model.feed
import com.islandpet.model.heal
import com.islandpet.model.pat
import com.islandpet.model.play
import com.islandpet.model.speech
import com.islandpet.model.toggleSleep
import com.islandpet.model.use
import kotlin.random.Random

@Composable
fun HomeScreen(state: GameState, onOpenSetup: () -> Unit) {
    val pet = state.pet ?: return
    val mood = pet.mood()
    val haptic = LocalHapticFeedback.current
    val hearts = remember { mutableStateListOf<Long>() }
    var sheet by remember { mutableStateOf<ItemCategory?>(null) }
    val ctx = LocalContext.current

    fun patWithHeart() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        PetRepo.act { it.pat() }
        hearts += System.nanoTime()
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Header(pet.name, state.coins, "Lv.${pet.level} · ${pet.formTitle} · ${pet.species.label}")

        // 舞台
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                state.speech(),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.height(230.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .offset(y = 92.dp)
                        .size(130.dp, 20.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.25f))
                )
                PetCanvas(
                    pet, 200.dp,
                    Modifier.clickable(remember { MutableInteractionSource() }, null) { patWithHeart() },
                    mood,
                )
                hearts.forEach { id -> key(id) { FloatingHeart { hearts.remove(id) } } }
                Row(Modifier.offset(y = 92.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    repeat(pet.poops) {
                        Text("💩", fontSize = 30.sp, modifier = Modifier.clickable { PetRepo.act { s -> s.clean() } })
                    }
                }
            }
            Text(
                if (pet.isEgg) "点击蛋蛋帮它孵化（${pet.eggTaps}/6）" else "${mood.emoji} ${mood.title} · 陪伴第 ${pet.ageDays + 1} 天",
                fontSize = 12.sp, color = Dim,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (pet.isEgg) 0f else pet.xpProgress },
                color = Yellow, trackColor = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.width(240.dp).clip(RoundedCornerShape(50)),
            )
            Text(
                if (pet.isEgg) "孵化后开始成长" else "经验 ${pet.xp}/${pet.xpToNext} · ${pet.stage.label}",
                fontSize = 11.sp, color = Dim,
            )
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!IslandPrefs.enabled(ctx) || !OverlayHost.isShowing) {
                CardBox(Modifier.clickable(onClick = onOpenSetup)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏝️", fontSize = 32.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("让宠物住进灵动岛", fontWeight = FontWeight.Bold)
                            Text("开启后宠物会待在前置摄像头旁边，点一下就能展开互动", fontSize = 12.sp, color = Dim)
                        }
                        Text("去设置 ›", color = Orange)
                    }
                }
            }

            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBar("🍖", "饱腹", pet.hunger, Orange)
                    StatBar("💗", "心情", pet.happiness, Pink)
                    StatBar("⚡", "精力", pet.energy, Yellow)
                    StatBar("🧽", "清洁", pet.cleanliness, Cyan)
                    StatBar("🩺", "健康", pet.health, Red)
                }
            }

            // 互动
            val actions = listOf(
                Triple("🍖", "喂食") { sheet = ItemCategory.FOOD },
                Triple("🎾", "玩耍") { sheet = ItemCategory.TOY },
                Triple("🤲", "摸摸") { patWithHeart() },
                Triple("🛁", "洗澡") { PetRepo.act { it.clean() }; Unit },
                Triple(if (pet.isSleeping) "☀️" else "🌙", if (pet.isSleeping) "叫醒" else "睡觉") { PetRepo.act { it.toggleSleep() }; Unit },
                Triple("💊", "吃药") { PetRepo.act { it.heal() }; Unit },
            )
            actions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (emoji, label, onClick) ->
                        val highlight = label == "吃药" && pet.isSick
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (highlight) Red.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f))
                                .clickable(onClick = onClick)
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(emoji, fontSize = 28.sp)
                            Text(label, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            state.wish?.let { wish ->
                ShopItem.find(wish.itemId)?.let { item ->
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💭", fontSize = 32.sp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("心愿：想吃${item.emoji}${item.name}", fontWeight = FontWeight.Bold)
                                val mins = ((wish.expires - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
                                Text("满足心愿可得 30 金币，还剩 ${mins / 60} 小时 ${mins % 60} 分", fontSize = 12.sp, color = Dim)
                            }
                            if (state.count(item.id) > 0) {
                                Button(onClick = { PetRepo.act { it.feed(item.id) } }, colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Text("喂它") }
                            } else {
                                Button(onClick = { PetRepo.act { it.buy(item.id) } }) { Text("💰${item.price}") }
                            }
                        }
                    }
                }
            }

            if (!state.hasCheckedInToday) {
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 32.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("每日签到", fontWeight = FontWeight.Bold)
                            Text("连续签到奖励越来越多，已连续 ${state.counters.checkInStreak} 天", fontSize = 12.sp, color = Dim)
                        }
                        Button(onClick = { PetRepo.act { it.checkIn() } }) { Text("签到") }
                    }
                }
            }
        }
    }

    sheet?.let { ItemSheet(state, it) { sheet = null } }
}

@Composable
private fun FloatingHeart(onDone: () -> Unit) {
    val anim = remember { Animatable(0f) }
    val dx = remember { Random.nextInt(-50, 50) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, androidx.compose.animation.core.tween(1100))
        onDone()
    }
    Text(
        "💗", fontSize = 28.sp,
        modifier = Modifier
            .offset(x = dx.dp, y = (-30 - 90 * anim.value).dp)
            .alpha(1 - anim.value),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(state: GameState, category: ItemCategory, onDismiss: () -> Unit) {
    val items = ShopItem.catalog.filter { it.category == category && state.count(it.id) > 0 }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1C1C2E)) {
        Text(
            if (category == ItemCategory.FOOD) "喂点什么？" else "玩点什么？",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            if (category == ItemCategory.TOY) {
                item {
                    ItemRow("🤸", "徒手陪玩", "心情+15 · 精力-10", null, false) { PetRepo.act { it.play() }; onDismiss() }
                }
            }
            items(items, key = { it.id }) { item ->
                ItemRow(
                    item.emoji, item.name, item.effectText,
                    if (item.id == ShopItem.FREE_SNACK) null else state.count(item.id),
                    item.id == state.pet?.species?.favoriteFood,
                ) { PetRepo.act { it.use(item.id) }; onDismiss() }
            }
            if (items.isEmpty() && category != ItemCategory.TOY) {
                item { Text("背包里没有${category.label}，去商店看看吧", color = Dim, modifier = Modifier.padding(20.dp)) }
            }
        }
    }
}

@Composable
private fun ItemRow(emoji: String, name: String, detail: String, count: Int?, favorite: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 32.sp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, fontWeight = FontWeight.Bold)
                if (favorite) Tag("最爱", Pink)
            }
            Text(detail, fontSize = 12.sp, color = Dim)
        }
        count?.let { Text("×$it", color = Dim) }
    }
}

// ---------- 领养 ----------

@Composable
fun AdoptScreen() {
    var species by remember { mutableStateOf(Species.CAT) }
    var name by remember { mutableStateOf("") }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("领养一只灵动岛宠物", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("它会住在你的前置摄像头旁边，随时陪着你", color = Dim)
        PetCanvas(species, Stage.ADULT, Mood.HAPPY, 160.dp)
        Text(species.intro, color = Dim, textAlign = TextAlign.Center, modifier = Modifier.height(44.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Species.entries.forEach { sp ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = if (sp == species) 0.2f else 0.06f))
                        .border(1.5.dp, if (sp == species) Color.White.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { species = sp }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PetCanvas(sp, Stage.BABY, Mood.NORMAL, 48.dp, animate = false)
                    Text(sp.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(8) },
            placeholder = { Text("给它取个名字") }, singleLine = true,
        )
        Button(
            onClick = {
                val finalName = name.trim().ifEmpty { "小${species.label.first()}" }
                PetRepo.act { it.adopt(finalName, species); "领养成功！敲敲蛋壳帮它孵化吧" }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Orange),
            modifier = Modifier.width(240.dp),
        ) { Text("领养这颗蛋 🥚", fontSize = 16.sp) }
    }
}
