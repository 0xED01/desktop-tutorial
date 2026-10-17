package com.islandpet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandpet.model.GameState
import com.islandpet.model.Mood
import com.islandpet.model.Pet
import com.islandpet.model.PetRenderer
import com.islandpet.model.ShopItem
import com.islandpet.model.Species
import com.islandpet.model.Stage

val Dim = Color.White.copy(alpha = 0.6f)
val Orange = Color(0xFFFF9F43)
val Pink = Color(0xFFFF6B9A)
val Yellow = Color(0xFFFFD54F)
val Cyan = Color(0xFF4DD0E1)
val Green = Color(0xFF4CD964)
val Red = Color(0xFFFF5252)

fun GameState.backgroundBrush(): Brush {
    val t = themeColors
    return Brush.verticalGradient(listOf(Color(t.top), Color(t.bottom)))
}

@Composable
fun rememberAnimTime(): Float {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time = (it - start) / 1_000_000_000f }
        }
    }
    return time
}

@Composable
fun PetCanvas(
    species: Species, stage: Stage, mood: Mood, size: Dp,
    modifier: Modifier = Modifier, accessory: String? = null, eggCracks: Int = 0, animate: Boolean = true,
) {
    val t = if (animate) rememberAnimTime() else 0f
    val blink = animate && (t % 3.7f) < 0.14f && mood != Mood.SLEEPING
    Canvas(modifier.size(size)) {
        drawIntoCanvas {
            PetRenderer.draw(it.nativeCanvas, center.x, center.y, this.size.minDimension, species, stage, mood, accessory, t, blink, eggCracks)
        }
    }
}

@Composable
fun PetCanvas(pet: Pet, size: Dp, modifier: Modifier = Modifier, mood: Mood = pet.mood()) =
    PetCanvas(pet.species, pet.stage, mood, size, modifier, ShopItem.find(pet.accessory)?.emoji, pet.eggTaps)

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun StatBar(icon: String, title: String, value: Double, color: Color) {
    val c = if (value < 25) Red else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, modifier = Modifier.width(26.dp))
        Text(title, color = Dim, fontSize = 12.sp, modifier = Modifier.width(34.dp))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((value / 100).toFloat().coerceAtLeast(0.04f))
                    .height(10.dp)
                    .background(Brush.horizontalGradient(listOf(c, c.copy(alpha = 0.7f))))
            )
        }
        Text("${value.toInt()}", fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun CoinBadge(coins: Int) {
    Text(
        "💰 $coins",
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Yellow.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun Header(title: String, coins: Int?, subtitle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, fontSize = 12.sp, color = Dim) }
        }
        coins?.let { CoinBadge(it) }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** 小游戏开始 / 结束面板 */
@Composable
fun ResultPanel(title: String, detail: String, coins: Int?, onAgain: () -> Unit, onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF26263A))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(detail, color = Dim, textAlign = TextAlign.Center)
            coins?.let { Text("+$it 💰", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Yellow) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAgain, colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                    Text(if (coins == null) "开始" else "再来一局")
                }
                OutlinedButton(onClick = onExit) { Text("返回", color = Color.White) }
            }
        }
    }
}
