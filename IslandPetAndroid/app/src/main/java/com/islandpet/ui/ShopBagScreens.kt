package com.islandpet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandpet.model.GameState
import com.islandpet.model.ItemCategory
import com.islandpet.model.PetRepo
import com.islandpet.model.PetTheme
import com.islandpet.model.ShopItem
import com.islandpet.model.buy
import com.islandpet.model.equip
import com.islandpet.model.setTheme
import com.islandpet.model.use

@Composable
fun ShopScreen(state: GameState) {
    var category by rememberSaveable { mutableStateOf(ItemCategory.FOOD) }
    Column(Modifier.fillMaxSize()) {
        Header("商店", state.coins)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ItemCategory.entries.forEach { c ->
                Text(
                    c.label,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (c == category) Orange else Color.White.copy(alpha = 0.08f))
                        .clickable { category = c }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        val items = ShopItem.catalog.filter { it.category == category && it.price > 0 }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { ShopCell(state, it) }
        }
    }
}

@Composable
private fun ShopCell(state: GameState, item: ShopItem) {
    val level = state.pet?.level ?: 1
    val locked = item.minLevel > level
    val ownedOnce = !item.category.consumable && state.owns(item)
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(item.emoji, fontSize = 42.sp, modifier = Modifier.align(Alignment.Center))
            Box(Modifier.align(Alignment.TopEnd)) {
                when {
                    item.id == state.wish?.itemId -> Tag("心愿", Color(0xFF9C27B0))
                    item.id == state.pet?.species?.favoriteFood -> Tag("最爱", Pink)
                }
            }
        }
        Text(item.name, fontWeight = FontWeight.Bold)
        Text(
            when (item.category) {
                ItemCategory.THEME -> "灵动岛 / App 配色"
                ItemCategory.ACCESSORY -> "戴在宠物头上"
                else -> item.effectText
            },
            fontSize = 11.sp, color = Dim, textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
        )
        if (item.category.consumable) Text("已有 ${state.count(item.id)}", fontSize = 11.sp, color = Dim)
        Button(
            onClick = { PetRepo.act { it.buy(item.id) } },
            enabled = !ownedOnce && !locked,
            colors = ButtonDefaults.buttonColors(containerColor = Orange),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    ownedOnce -> "已拥有"
                    locked -> "🔒 Lv.${item.minLevel}"
                    else -> "💰 ${item.price}"
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun BagScreen(state: GameState) {
    val consumables = ShopItem.catalog.filter { it.category.consumable && it.id != ShopItem.FREE_SNACK && state.count(it.id) > 0 }
    val accessories = ShopItem.catalog.filter { it.category == ItemCategory.ACCESSORY && it.id in state.ownedAccessories }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Header("背包", state.coins)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("道具", fontWeight = FontWeight.Bold)
            CardBox {
                if (consumables.isEmpty()) Text("背包空空的，去商店逛逛吧", color = Dim)
                consumables.forEach { item ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.emoji, fontSize = 28.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.effectText, fontSize = 11.sp, color = Dim)
                        }
                        Text("×${state.count(item.id)}", color = Dim)
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = { PetRepo.act { it.use(item.id) } }) { Text("使用", color = Color.White) }
                    }
                }
            }

            Text("装扮", fontWeight = FontWeight.Bold)
            CardBox {
                if (accessories.isEmpty()) {
                    Text("还没有装扮，商店里有蝴蝶结、皇冠……", color = Dim)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        accessories.forEach { item ->
                            val on = state.pet?.accessory == item.id
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Orange.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f))
                                    .clickable { PetRepo.act { it.equip(item.id) } }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(item.emoji, fontSize = 32.sp)
                                Text(if (on) "已佩戴" else item.name, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Text("主题", fontWeight = FontWeight.Bold)
            CardBox {
                PetTheme.all.forEach { theme ->
                    val owned = theme.id in state.ownedThemes
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = owned) { PetRepo.act { it.setTheme(theme.id) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp, 28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(Color(theme.top), Color(theme.bottom))))
                                .border(1.dp, Color(theme.accent), RoundedCornerShape(8.dp))
                        )
                        Text(theme.name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        when {
                            state.theme == theme.id -> Text("✅")
                            !owned -> Text("商店购买", fontSize = 12.sp, color = Dim)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
