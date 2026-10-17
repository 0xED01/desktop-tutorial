package com.hydro.water.data

import java.time.LocalDate

data class DrinkType(val id: Int, val name: String, val factor: Float, val color: Long)

val DrinkTypes = listOf(
    DrinkType(0, "白水", 1.0f, 0xFF2E7CF6),
    DrinkType(1, "茶", 0.9f, 0xFF3BB27A),
    DrinkType(2, "咖啡", 0.8f, 0xFF9A6B4F),
    DrinkType(3, "牛奶", 0.9f, 0xFFB9A88F),
    DrinkType(4, "果汁", 0.85f, 0xFFF29D38),
    DrinkType(5, "运动饮料", 1.0f, 0xFF16B6C9),
    DrinkType(6, "汤", 0.8f, 0xFFE2675A),
)

fun drinkType(id: Int) = DrinkTypes.firstOrNull { it.id == id } ?: DrinkTypes[0]

data class Record(
    val id: Long,
    val ts: Long,
    val amount: Int,
    val type: Int,
    val effective: Int,
)

data class Settings(
    val goal: Int = 2000,
    val weight: Int = 60,
    val activity: Int = 1,
    val cups: List<Int> = listOf(150, 250, 350, 500),
    val defaultType: Int = 0,
    val reminderOn: Boolean = true,
    val interval: Int = 60,
    val startMin: Int = 8 * 60,
    val endMin: Int = 22 * 60,
    val stopWhenDone: Boolean = false,
    val smartDelay: Boolean = true,
    val notifyAmount: Int = 250,
    val themeMode: Int = 0,
    val dynamicColor: Boolean = false,
)

val ActivityLevels = listOf("久坐", "轻度", "中度", "高强度")

/** 体重(kg) × 33ml + 运动量加成，取整到 50ml */
fun recommendedGoal(weight: Int, activity: Int): Int {
    val raw = weight * 33 + activity * 350
    return ((raw + 25) / 50 * 50).coerceIn(1000, 5000)
}

fun LocalDate.key(): Int = year * 10000 + monthValue * 100 + dayOfMonth
fun keyToDate(key: Int): LocalDate = LocalDate.of(key / 10000, key / 100 % 100, key % 100)
