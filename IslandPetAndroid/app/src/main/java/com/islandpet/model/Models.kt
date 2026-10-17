package com.islandpet.model

import kotlinx.serialization.Serializable

// ---------- 物种 ----------

@Serializable
enum class Species(
    val label: String,
    val intro: String,
    val hungerMul: Double,
    val happyMul: Double,
    val energyMul: Double,
    val cleanMul: Double,
    val favoriteFood: String,
    /** 猜拳偏好：0 石头 1 剪刀 2 布 */
    val rpsBias: Int,
    val body: Int,
    val shade: Int,
) {
    CAT("猫咪", "傲娇又黏人，爱吃小鱼干。心情掉得快，记得多摸摸。", 1.0, 1.3, 0.9, 0.8, "fish", 1, 0xFFFFB86B.toInt(), 0xFFE6803F.toInt()),
    DOG("小狗", "精力旺盛的玩伴，最爱玩耍和肉骨头。饿得比较快。", 1.25, 0.9, 1.1, 1.2, "bone", 0, 0xFFDBA875.toInt(), 0xFF996B47.toInt()),
    BUNNY("兔兔", "安静爱干净，喜欢胡萝卜。身体有点娇弱。", 0.9, 1.0, 1.0, 0.7, "carrot", 2, 0xFFFAEBF2.toInt(), 0xFFF2B3CC.toInt()),
    DRAGON("小龙", "稀有的小火龙，喜欢辣椒，体力超好但很能吃。", 1.4, 0.9, 0.7, 1.0, "pepper", 0, 0xFF73D185.toInt(), 0xFF409959.toInt()),
    SLIME("史莱姆", "软糯糯的果冻生物，几乎不会脏，但容易无聊。", 0.8, 1.4, 1.0, 0.4, "jelly", 2, 0xFF73C2FF.toInt(), 0xFF4080E6.toInt()),
}

// ---------- 成长阶段 ----------

enum class Stage(val label: String, val scale: Float) {
    EGG("蛋蛋期", 0.85f),
    BABY("宝宝期", 0.72f),
    CHILD("幼年期", 0.82f),
    TEEN("少年期", 0.92f),
    ADULT("成年期", 1.0f);

    companion object {
        fun forLevel(level: Int) = when {
            level < 5 -> BABY
            level < 10 -> CHILD
            level < 18 -> TEEN
            else -> ADULT
        }
    }
}

// ---------- 心情 ----------

enum class Mood(val emoji: String, val title: String, val lines: List<String>) {
    EGG("🥚", "等待孵化", listOf("(咚咚…)", "(蛋壳晃了一下)", "(里面好像有动静)")),
    HAPPY("😊", "超开心", listOf("今天也好开心！", "最喜欢你啦～", "嘿嘿嘿", "要一直在一起哦")),
    NORMAL("🙂", "还不错", listOf("在干嘛呀？", "陪我玩一会嘛", "嗯哼～", "灵动岛的风景不错")),
    HUNGRY("🍽️", "饿了", listOf("肚子咕咕叫…", "想吃好吃的！", "饭饭…饭饭…")),
    SLEEPY("🥱", "困了", listOf("好困哦…", "眼皮在打架", "想睡觉觉")),
    DIRTY("🧽", "脏兮兮", listOf("身上黏黏的…", "想洗澡澡", "有点臭臭的")),
    SICK("🤒", "生病了", listOf("头好晕…", "需要吃药药", "呜…不舒服")),
    SLEEPING("💤", "睡觉中", listOf("Zzz…", "呼噜噜…", "(梦到了小鱼干)")),
    LOVED("💗", "被爱着", listOf("蹭蹭你～", "再摸摸嘛", "❤️❤️❤️")),
    BORED("😐", "好无聊", listOf("好无聊啊…", "来玩游戏吧！", "发呆中…")),
    SAD("😢", "不开心", listOf("你是不是忘了我…", "呜呜…", "想要抱抱"));

    val urgent get() = this == HUNGRY || this == SICK || this == DIRTY || this == SAD
}

// ---------- 宠物 ----------

@Serializable
data class Pet(
    var name: String,
    var species: Species,
    var birthday: Long,
    var hatchedAt: Long? = null,
    var eggTaps: Int = 0,
    var level: Int = 1,
    var xp: Int = 0,
    var hunger: Double = 80.0,
    var happiness: Double = 80.0,
    var energy: Double = 80.0,
    var cleanliness: Double = 90.0,
    var health: Double = 100.0,
    var isSleeping: Boolean = false,
    var isSick: Boolean = false,
    var poops: Int = 0,
    var poopMeter: Double = 0.0,
    var sickMeter: Double = 0.0,
    var accessory: String? = null,
    var lastPattedAt: Long? = null,
    var lovedUntil: Long? = null,
    var careScore: Double = 60.0,
    var lastTick: Long = birthday,
) {
    val isEgg get() = hatchedAt == null
    val stage get() = if (isEgg) Stage.EGG else Stage.forLevel(level)
    val xpToNext get() = 20 + level * 15
    val xpProgress get() = xp.toFloat() / xpToNext

    val formTitle: String
        get() = if (stage != Stage.ADULT) stage.label else when {
            careScore >= 80 -> "天使形态"
            careScore >= 55 -> "元气形态"
            else -> "调皮形态"
        }

    val ageDays get() = ((System.currentTimeMillis() - birthday) / 86_400_000L).toInt().coerceAtLeast(0)

    /** 每小时饱腹消耗 */
    val hungerRate get() = 7 * species.hungerMul * (if (isSleeping) 0.4 else 1.0)

    fun mood(now: Long = System.currentTimeMillis()): Mood = when {
        isEgg -> Mood.EGG
        isSleeping -> Mood.SLEEPING
        isSick -> Mood.SICK
        (lovedUntil ?: 0) > now -> Mood.LOVED
        hunger < 25 -> Mood.HUNGRY
        energy < 20 -> Mood.SLEEPY
        cleanliness < 25 || poops >= 2 -> Mood.DIRTY
        happiness < 25 -> Mood.SAD
        happiness < 45 -> Mood.BORED
        (hunger + happiness + energy + cleanliness) / 4 > 72 -> Mood.HAPPY
        else -> Mood.NORMAL
    }

    /** 距离饿到 0 还有多少毫秒 */
    fun msUntilStarving(): Long = if (isEgg) Long.MAX_VALUE else (hunger / hungerRate.coerceAtLeast(0.1) * 3_600_000).toLong()
}

// ---------- 物品 ----------

enum class ItemCategory(val label: String, val consumable: Boolean) {
    FOOD("食物", true), TOY("玩具", true), CARE("护理", true), ACCESSORY("装扮", false), THEME("主题", false)
}

data class ShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val category: ItemCategory,
    val hunger: Double = 0.0,
    val happiness: Double = 0.0,
    val energy: Double = 0.0,
    val cleanliness: Double = 0.0,
    val health: Double = 0.0,
    val xp: Int = 0,
    val cures: Boolean = false,
    val minLevel: Int = 1,
    val note: String = "",
) {
    val effectText: String
        get() {
            val parts = mutableListOf<String>()
            fun add(label: String, v: Double) {
                if (v != 0.0) parts += "$label${if (v > 0) "+" else ""}${v.toInt()}"
            }
            add("饱腹", hunger); add("心情", happiness); add("精力", energy); add("清洁", cleanliness); add("健康", health)
            if (cures) parts += "治愈疾病"
            if (note.isNotEmpty()) parts += note
            return parts.joinToString(" · ")
        }

    companion object {
        const val FREE_SNACK = "snack"

        val catalog = listOf(
            ShopItem("snack", "小饼干", "🍪", 0, ItemCategory.FOOD, hunger = 12.0, happiness = 2.0, xp = 1, note = "无限供应"),
            ShopItem("apple", "苹果", "🍎", 8, ItemCategory.FOOD, hunger = 15.0, health = 5.0, xp = 2),
            ShopItem("carrot", "胡萝卜", "🥕", 10, ItemCategory.FOOD, hunger = 25.0, health = 3.0, xp = 3),
            ShopItem("milk", "牛奶", "🥛", 12, ItemCategory.FOOD, hunger = 15.0, energy = 10.0, xp = 3),
            ShopItem("fish", "小鱼干", "🐟", 15, ItemCategory.FOOD, hunger = 30.0, happiness = 5.0, xp = 4),
            ShopItem("bone", "肉骨头", "🍖", 15, ItemCategory.FOOD, hunger = 35.0, xp = 4),
            ShopItem("jelly", "果冻", "🍮", 18, ItemCategory.FOOD, hunger = 20.0, happiness = 15.0, xp = 4),
            ShopItem("pepper", "辣椒", "🌶️", 20, ItemCategory.FOOD, hunger = 20.0, energy = 15.0, xp = 4),
            ShopItem("icecream", "冰淇淋", "🍦", 25, ItemCategory.FOOD, hunger = 10.0, happiness = 20.0, xp = 5),
            ShopItem("sushi", "寿司", "🍣", 35, ItemCategory.FOOD, hunger = 40.0, happiness = 10.0, xp = 6),
            ShopItem("cake", "草莓蛋糕", "🍰", 40, ItemCategory.FOOD, hunger = 30.0, happiness = 25.0, health = -3.0, xp = 8),

            ShopItem("ball", "网球", "🎾", 30, ItemCategory.TOY, happiness = 25.0, energy = -8.0, xp = 8),
            ShopItem("yarn", "毛线球", "🧶", 25, ItemCategory.TOY, happiness = 22.0, energy = -6.0, xp = 7),
            ShopItem("kite", "风筝", "🪁", 45, ItemCategory.TOY, happiness = 35.0, energy = -12.0, xp = 12),
            ShopItem("console", "游戏机", "🎮", 60, ItemCategory.TOY, happiness = 40.0, energy = -5.0, xp = 15),

            ShopItem("soap", "香香泡泡", "🧼", 10, ItemCategory.CARE, cleanliness = 100.0, xp = 3, note = "洗得干干净净"),
            ShopItem("medicine", "感冒药", "💊", 30, ItemCategory.CARE, health = 20.0, xp = 5, cures = true),
            ShopItem("potion", "营养剂", "🧪", 40, ItemCategory.CARE, health = 35.0, xp = 5),
            ShopItem("grape", "能量葡萄", "🍇", 25, ItemCategory.CARE, hunger = 5.0, energy = 35.0, xp = 4),

            ShopItem("flower", "小花", "🌸", 40, ItemCategory.ACCESSORY),
            ShopItem("bow", "蝴蝶结", "🎀", 50, ItemCategory.ACCESSORY),
            ShopItem("cap", "鸭舌帽", "🧢", 60, ItemCategory.ACCESSORY),
            ShopItem("glasses", "墨镜", "🕶️", 70, ItemCategory.ACCESSORY, minLevel = 3),
            ShopItem("tophat", "礼帽", "🎩", 80, ItemCategory.ACCESSORY, minLevel = 5),
            ShopItem("headphones", "耳机", "🎧", 90, ItemCategory.ACCESSORY, minLevel = 6),
            ShopItem("gradcap", "学士帽", "🎓", 120, ItemCategory.ACCESSORY, minLevel = 10),
            ShopItem("crown", "皇冠", "👑", 200, ItemCategory.ACCESSORY, minLevel = 15),

            ShopItem("theme.sakura", "樱花粉", "🌸", 80, ItemCategory.THEME),
            ShopItem("theme.mint", "薄荷绿", "🌿", 80, ItemCategory.THEME),
            ShopItem("theme.sunset", "落日橙", "🌇", 100, ItemCategory.THEME),
            ShopItem("theme.ocean", "深海蓝", "🌊", 100, ItemCategory.THEME),
            ShopItem("theme.galaxy", "星河紫", "🌌", 150, ItemCategory.THEME, minLevel = 8),
        )

        fun find(id: String?) = catalog.firstOrNull { it.id == id }
    }
}

// ---------- 主题 ----------

data class PetTheme(val id: String, val name: String, val top: Int, val bottom: Int, val accent: Int) {
    companion object {
        val all = listOf(
            PetTheme("theme.night", "夜空", 0xFF1F2138.toInt(), 0xFF0D0D1A.toInt(), 0xFFFFBF66.toInt()),
            PetTheme("theme.sakura", "樱花粉", 0xFF8C4066.toInt(), 0xFF4D1A38.toInt(), 0xFFFFB3D1.toInt()),
            PetTheme("theme.mint", "薄荷绿", 0xFF1F665C.toInt(), 0xFF0A332E.toInt(), 0xFF99FFD9.toInt()),
            PetTheme("theme.sunset", "落日橙", 0xFF994D26.toInt(), 0xFF521F2E.toInt(), 0xFFFFCC73.toInt()),
            PetTheme("theme.ocean", "深海蓝", 0xFF1A4080.toInt(), 0xFF081438.toInt(), 0xFF80D9FF.toInt()),
            PetTheme("theme.galaxy", "星河紫", 0xFF52298C.toInt(), 0xFF140A33.toInt(), 0xFFD9B3FF.toInt()),
        )

        fun find(id: String) = all.firstOrNull { it.id == id } ?: all[0]
    }
}

// ---------- 小游戏 ----------

enum class GameKind(val label: String, val emoji: String, val intro: String) {
    CATCH("接零食", "🧺", "左右拖动宠物接住掉落的零食，小心炸弹！"),
    MEMORY("翻翻乐", "🃏", "翻开卡片找出所有成对的图案，步数越少奖励越多。"),
    REACTION("反应力挑战", "⚡", "宠物跳起来的瞬间立刻点击，测测你的反应速度。"),
    RPS("猜拳大战", "✊", "和宠物五局三胜猜拳，它可是有小习惯的哦。"),
}

// ---------- 存档 ----------

@Serializable
data class DiaryEntry(val date: Long, val emoji: String, val text: String)

@Serializable
data class Wish(val itemId: String, val expires: Long)

@Serializable
data class Counters(
    var feeds: Int = 0,
    var plays: Int = 0,
    var pats: Int = 0,
    var cleans: Int = 0,
    var cures: Int = 0,
    var gamesPlayed: Int = 0,
    var wishesGranted: Int = 0,
    var checkInStreak: Int = 0,
    var lastCheckIn: Long? = null,
    var playedGames: MutableSet<String> = mutableSetOf(),
    var highScores: MutableMap<String, Int> = mutableMapOf(),
)

@Serializable
data class GameState(
    var pet: Pet? = null,
    var coins: Int = 100,
    var inventory: MutableMap<String, Int> = mutableMapOf("fish" to 2, "soap" to 1, "ball" to 1),
    var ownedAccessories: MutableSet<String> = mutableSetOf(),
    var ownedThemes: MutableSet<String> = mutableSetOf("theme.night"),
    var theme: String = "theme.night",
    var achievements: MutableSet<String> = mutableSetOf(),
    var diary: MutableList<DiaryEntry> = mutableListOf(),
    var counters: Counters = Counters(),
    var wish: Wish? = null,
    var lastMessage: String? = null,
    var lastMessageAt: Long? = null,
    var lastEventRoll: Long? = null,
) {
    fun count(itemId: String) = if (itemId == ShopItem.FREE_SNACK) Int.MAX_VALUE else inventory[itemId] ?: 0

    fun owns(item: ShopItem) = when (item.category) {
        ItemCategory.ACCESSORY -> item.id in ownedAccessories
        ItemCategory.THEME -> item.id in ownedThemes
        else -> count(item.id) > 0
    }

    val hasCheckedInToday get() = counters.lastCheckIn?.let { sameDay(it, System.currentTimeMillis()) } ?: false

    val themeColors get() = PetTheme.find(theme)
}

fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}
