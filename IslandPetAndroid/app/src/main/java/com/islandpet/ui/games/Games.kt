package com.islandpet.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandpet.model.GameKind
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.PetRepo
import com.islandpet.model.finishGame
import com.islandpet.model.gameBlocker
import com.islandpet.ui.CardBox
import com.islandpet.ui.Dim
import com.islandpet.ui.Header
import com.islandpet.ui.Orange
import com.islandpet.ui.PetCanvas
import com.islandpet.ui.Red
import com.islandpet.ui.ResultPanel
import com.islandpet.ui.Yellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun GamesScreen(state: GameState) {
    var current by rememberSaveable { mutableStateOf<GameKind?>(null) }
    current?.let { game ->
        BackHandler { current = null }
        val exit = { current = null }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = exit) { Text("‹ 返回", color = Orange) }
                Text(game.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f)) {
                when (game) {
                    GameKind.CATCH -> CatchGame(state, exit)
                    GameKind.MEMORY -> MemoryGame(exit)
                    GameKind.REACTION -> ReactionGame(state, exit)
                    GameKind.RPS -> RpsGame(state, exit)
                }
            }
        }
        return
    }

    val blocker = state.gameBlocker
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("游乐场", state.coins)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            blocker?.let { CardBox { Text("⚠️ $it", color = Orange) } }
            GameKind.entries.forEach { g ->
                CardBox(
                    Modifier
                        .alpha(if (blocker == null) 1f else 0.5f)
                        .clickable(enabled = blocker == null) { current = g }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.emoji, fontSize = 38.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(g.label, fontWeight = FontWeight.Bold)
                            Text(g.intro, fontSize = 12.sp, color = Dim)
                            val best = state.counters.highScores[g.name]
                            if (best != null && g != GameKind.MEMORY) Text("最佳：$best", fontSize = 11.sp, color = Yellow)
                        }
                        Text("›", fontSize = 22.sp, color = Dim)
                    }
                }
            }
            Text("每局消耗宠物 10 点精力，赚到的金币可以去商店买零食和装扮。", fontSize = 12.sp, color = Dim)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------- 接零食 ----------

private class Drop(var x: Float, var y: Float, val speed: Float, val emoji: String, val bad: Boolean, val points: Int)

@Composable
private fun CatchGame(state: GameState, exit: () -> Unit) {
    val pet = state.pet ?: return
    val goodies = remember { listOf("🍪" to 1, "🐟" to 2, "🍖" to 2, "🥕" to 1, "🍮" to 2, "🍰" to 3, "🌟" to 5) }
    var phase by remember { mutableIntStateOf(0) } // 0 准备 1 进行 2 结束
    val drops = remember { mutableStateListOf<Drop>() }
    var playerX by remember { mutableFloatStateOf(0.5f) }
    var score by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var timeLeft by remember { mutableFloatStateOf(40f) }
    var frame by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<String?>(null) }
    val coins = score / 2 + 5

    LaunchedEffect(phase) {
        if (phase != 1) return@LaunchedEffect
        var last = withFrameNanos { it }
        var spawn = 0f
        while (phase == 1) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                timeLeft -= dt
                val progress = 1 - timeLeft / 40f
                spawn -= dt
                if (spawn <= 0) {
                    spawn = 0.75f - 0.4f * progress
                    val bad = Random.nextFloat() < 0.18f + 0.12f * progress
                    val g = goodies.random()
                    drops += Drop(Random.nextFloat() * 0.84f + 0.08f, -0.05f, (0.28f + Random.nextFloat() * 0.14f) * (1 + progress * 0.8f), if (bad) "💣" else g.first, bad, if (bad) 0 else g.second)
                }
                val it = drops.iterator()
                while (it.hasNext()) {
                    val d = it.next()
                    d.y += d.speed * dt
                    if (d.y in 0.82f..0.95f && abs(d.x - playerX) < 0.11f) {
                        if (d.bad) { lives--; flash = "💥" } else { score += d.points; flash = "+${d.points}" }
                        it.remove()
                    } else if (d.y > 1.05f) it.remove()
                }
                frame++
                if (timeLeft <= 0 || lives <= 0) {
                    phase = 2
                    PetRepo.act { s -> s.finishGame(GameKind.CATCH, score, score / 2 + 5) }
                }
            }
        }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(400); flash = null } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> playerX = (change.position.x / size.width).coerceIn(0.05f, 0.95f) }
            }
            .pointerInput(Unit) {
                detectTapGestures { playerX = (it.x / size.width).coerceIn(0.05f, 0.95f) }
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        frame.let { } // 每帧重组
        drops.forEach { d ->
            Text(d.emoji, fontSize = 32.sp, modifier = Modifier.offset { IntOffset((d.x * w).roundToInt() - 40, (d.y * h).roundToInt() - 40) })
        }
        val petSize = 80.dp
        val petPx = with(density) { petSize.toPx() }
        PetCanvas(
            pet.species, pet.stage, if (lives < 2) Mood.SAD else Mood.HAPPY, petSize,
            Modifier.offset { IntOffset((playerX * w - petPx / 2).roundToInt(), (h * 0.88f - petPx / 2).roundToInt()) },
        )
        flash?.let {
            Text(
                it, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (it == "💥") Red else Yellow,
                modifier = Modifier.offset { IntOffset((playerX * w).roundToInt() - 30, (h * 0.74f).roundToInt()) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("得分 $score", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("❤️".repeat(lives.coerceAtLeast(0)))
            Spacer(Modifier.weight(1f))
            Text("⏱ ${timeLeft.coerceAtLeast(0f).toInt()}s", fontWeight = FontWeight.Bold)
        }
        val start = {
            drops.clear(); score = 0; lives = 3; timeLeft = 40f; playerX = 0.5f; phase = 1
        }
        when (phase) {
            0 -> ResultPanel("接零食", "左右拖动接住零食，别碰💣！\n🌟 = 5 分", null, start, exit)
            2 -> ResultPanel("游戏结束", "得分 $score", coins, start, exit)
        }
    }
}

// ---------- 翻翻乐 ----------

private data class MemCard(val id: Int, val emoji: String, val faceUp: Boolean = false, val matched: Boolean = false)

@Composable
private fun MemoryGame(exit: () -> Unit) {
    val pool = remember { listOf("🐟", "🍖", "🥕", "🌶️", "🍮", "🍰", "🍣", "🍦", "🎾", "🧶", "🎀", "👑") }
    fun newDeck(): List<MemCard> {
        val picks = pool.shuffled().take(6)
        return (picks + picks).shuffled().mapIndexed { i, e -> MemCard(i, e) }
    }
    val cards = remember { mutableStateListOf<MemCard>().apply { addAll(newDeck()) } }
    var moves by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val coins = (60 - (moves - 6).coerceAtLeast(0) * 4).coerceAtLeast(10)

    fun flip(i: Int) {
        val c = cards[i]
        if (busy || c.faceUp || c.matched) return
        cards[i] = c.copy(faceUp = true)
        val open = cards.indices.filter { cards[it].faceUp && !cards[it].matched }
        if (open.size < 2) return
        moves++
        val (a, b) = open[0] to open[1]
        if (cards[a].emoji == cards[b].emoji) {
            cards[a] = cards[a].copy(matched = true)
            cards[b] = cards[b].copy(matched = true)
            if (cards.all { it.matched }) {
                finished = true
                val m = moves
                PetRepo.act { s -> s.finishGame(GameKind.MEMORY, if (m <= 10) 1 else 0, (60 - (m - 6).coerceAtLeast(0) * 4).coerceAtLeast(10)) }
            }
        } else {
            busy = true
            scope.launch {
                delay(800)
                cards[a] = cards[a].copy(faceUp = false)
                cards[b] = cards[b].copy(faceUp = false)
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("步数 $moves", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            cards.indices.chunked(3).forEach { row ->
                Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { i ->
                        val c = cards[i]
                        val up = c.faceUp || c.matched
                        Box(
                            Modifier
                                .weight(1f)
                                .height(92.dp)
                                .graphicsLayer { rotationY = if (up) 0f else 180f; alpha = if (c.matched) 0.45f else 1f }
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (up) Color.White.copy(alpha = 0.9f) else Orange.copy(alpha = 0.75f))
                                .clickable { flip(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (up) c.emoji else "?", fontSize = if (up) 38.sp else 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
        if (finished) {
            ResultPanel("全部配对！", "用了 $moves 步${if (moves <= 10) "，过目不忘！" else ""}", coins, {
                cards.clear(); cards.addAll(newDeck()); moves = 0; finished = false; busy = false
            }, exit)
        }
    }
}

// ---------- 反应力 ----------

@Composable
private fun ReactionGame(state: GameState, exit: () -> Unit) {
    val pet = state.pet ?: return
    val rounds = 5
    var phase by remember { mutableStateOf("intro") } // intro waiting go early result done
    val times = remember { mutableStateListOf<Int>() }
    var goAt by remember { mutableStateOf(0L) }
    var token by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val avg = if (times.isEmpty()) 0 else times.sum() / times.size
    val coins = (60 - (avg - 250) / 6).coerceIn(8, 60)
    val jump by animateDpAsState(if (phase == "go") (-50).dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 800f), label = "jump")

    fun startRound() {
        phase = "waiting"
        val t = ++token
        scope.launch {
            delay(1200L + Random.nextLong(2600))
            if (token == t && phase == "waiting") {
                goAt = System.nanoTime(); phase = "go"
            }
        }
    }

    fun tap() {
        when (phase) {
            "intro", "early", "result" -> startRound()
            "waiting" -> { token++; phase = "early" }
            "go" -> {
                times += ((System.nanoTime() - goAt) / 1_000_000).toInt()
                if (times.size >= rounds) {
                    phase = "done"
                    val a = times.sum() / times.size
                    PetRepo.act { s -> s.finishGame(GameKind.REACTION, (1000 - a).coerceAtLeast(0), (60 - (a - 250) / 6).coerceIn(8, 60)) }
                } else phase = "result"
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                when (phase) {
                    "go" -> Color(0x5534C759)
                    "early" -> Color(0x55FF3B30)
                    else -> Color.Transparent
                }
            )
            .clickable(remember { MutableInteractionSource() }, null) { tap() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("第 ${(times.size + 1).coerceAtMost(rounds)}/$rounds 轮", color = Dim)
            PetCanvas(pet.species, pet.stage, if (phase == "go") Mood.HAPPY else Mood.NORMAL, 160.dp, Modifier.offset(y = jump))
            Text(
                when (phase) {
                    "intro" -> "点击屏幕开始"
                    "waiting" -> "等它跳起来……"
                    "go" -> "就是现在！"
                    "early" -> "太早啦！点击重试本轮"
                    "result" -> "${times.lastOrNull() ?: 0} ms！点击继续"
                    else -> ""
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
            )
            if (times.isNotEmpty()) Text(times.joinToString("  ") { "${it}ms" }, fontSize = 12.sp, color = Dim)
        }
        if (phase == "done") {
            val rating = when {
                avg < 230 -> "闪电般的反应！⚡"
                avg < 300 -> "很厉害！"
                avg < 380 -> "还不错～"
                else -> "宠物都比你快哦 😝"
            }
            ResultPanel("平均 $avg ms", rating, coins, { times.clear(); phase = "intro" }, exit)
        }
    }
}

// ---------- 猜拳 ----------

@Composable
private fun RpsGame(state: GameState, exit: () -> Unit) {
    val pet = state.pet ?: return
    val hands = listOf("✊", "✌️", "🖐️")
    val names = listOf("石头", "剪刀", "布")
    var mine by remember { mutableStateOf<Int?>(null) }
    var theirs by remember { mutableStateOf<Int?>(null) }
    var wins by remember { mutableIntStateOf(0) }
    var losses by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf("") }
    val over = wins >= 3 || losses >= 3
    val coins = if (wins >= 3) 40 + (3 - losses) * 5 else 10

    fun play(choice: Int) {
        if (over) return
        val p = if (Random.nextFloat() < 0.45f) pet.species.rpsBias else Random.nextInt(3)
        mine = choice; theirs = p
        // 0 石头 胜 1 剪刀，1 剪刀 胜 2 布，2 布 胜 0 石头
        when {
            choice == p -> line = "平局，再来！"
            (choice + 1) % 3 == p -> { wins++; line = "你赢了这一局！" }
            else -> { losses++; line = "它赢了这一局～" }
        }
        if (wins >= 3 || losses >= 3) {
            val c = if (wins >= 3) 40 + (3 - losses) * 5 else 10
            PetRepo.act { s -> s.finishGame(GameKind.RPS, wins, c) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("你 $wins : $losses ${pet.name}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mine?.let { hands[it] } ?: "❔", fontSize = 60.sp)
                    Text("你", fontSize = 12.sp)
                }
                Text("VS", color = Dim, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PetCanvas(pet.species, pet.stage, if (losses > wins) Mood.HAPPY else Mood.NORMAL, 56.dp)
                    Text(theirs?.let { hands[it] } ?: "❔", fontSize = 60.sp)
                }
            }
            Text(line, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                hands.forEachIndexed { i, h ->
                    Column(
                        Modifier
                            .size(88.dp, 96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { play(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(h, fontSize = 40.sp)
                        Text(names[i], fontSize = 12.sp)
                    }
                }
            }
            Text("小提示：观察它最常出什么～", fontSize = 12.sp, color = Dim)
        }
        if (over) {
            ResultPanel(if (wins >= 3) "你赢了！🎉" else "它赢了…", "比分 $wins : $losses", coins, {
                mine = null; theirs = null; wins = 0; losses = 0; line = ""
            }, exit)
        }
    }
}
