package com.islandpet.model

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** 全部玩法规则。每个互动返回一句给玩家看的反馈（显示在提示和灵动岛气泡里）。 */

private const val HOUR = 3_600_000.0

private fun Double.c() = coerceIn(0.0, 100.0)

private fun now() = System.currentTimeMillis()

// ---------- 时间流逝 ----------

fun GameState.tick(now: Long = now()) {
    val p = pet ?: return
    if (p.isEgg) {
        p.lastTick = now
        if (now - p.birthday >= 180_000 || p.eggTaps >= 6) hatch(p, now)
        return
    }
    val elapsed = now - p.lastTick
    if (elapsed < 1000) return
    // 最多结算 3 天，按 10 分钟一段推进，保证阈值判断准确
    var remaining = min(elapsed, 3 * 24 * 3_600_000L)
    while (remaining > 0) {
        val step = min(remaining, 600_000L)
        decay(p, step / HOUR)
        remaining -= step
    }
    p.lastTick = now

    wish?.let {
        if (it.expires < now) {
            wish = null
            log("💭", "${p.name}的心愿过期了…")
        }
    }
    rollRandomEvents(now)
}

private fun GameState.decay(p: Pet, h: Double) {
    val sp = p.species
    val sick = if (p.isSick) 1.8 else 1.0
    if (p.isSleeping) {
        p.hunger -= 7 * sp.hungerMul * 0.4 * h
        p.happiness -= 1 * h
        p.energy += 18 * h
        p.cleanliness -= 2 * sp.cleanMul * h
    } else {
        p.hunger -= 7 * sp.hungerMul * h
        p.happiness -= 5 * sp.happyMul * sick * h
        p.energy -= 5 * sp.energyMul * sick * h
        p.cleanliness -= (3 * sp.cleanMul + p.poops * 2) * h
    }

    // 便便：清醒时大约每 3 小时一次
    p.poopMeter += (if (p.isSleeping) 0.1 else 0.33) * h
    if (p.poopMeter >= 1) {
        p.poopMeter -= 1
        if (p.poops < 3) {
            p.poops++
            log("💩", "${p.name}拉了一坨便便")
        }
    }

    // 生病：脏、饿、便便堆积超过 2 小时就会生病
    if (p.cleanliness < 20 || p.poops >= 3 || p.hunger < 5) {
        p.sickMeter += h
        if (p.sickMeter >= 2 && !p.isSick) {
            p.isSick = true
            log("🤒", "${p.name}生病了，快喂药！")
        }
    } else {
        p.sickMeter = max(0.0, p.sickMeter - h)
    }

    if (p.isSick) p.health -= 4 * h
    if (p.hunger < 5) p.health -= 3 * h
    if (!p.isSick && p.hunger > 40 && p.cleanliness > 40) p.health += 2 * h

    p.hunger = p.hunger.c(); p.happiness = p.happiness.c(); p.energy = p.energy.c()
    p.cleanliness = p.cleanliness.c(); p.health = p.health.c()

    // 精力耗尽自动睡着，睡饱自动醒来
    if (!p.isSleeping && p.energy < 3) {
        p.isSleeping = true
        log("😴", "${p.name}累得直接睡着了")
    } else if (p.isSleeping && p.energy >= 100) {
        p.isSleeping = false
        log("🌞", "${p.name}睡饱醒来啦")
    }

    // 照顾评分：缓慢趋向当前状态平均值，决定成年形态
    val avg = (p.hunger + p.happiness + p.energy + p.cleanliness + p.health) / 5
    p.careScore += (avg - p.careScore) * min(1.0, h / 24)
}

private fun GameState.hatch(p: Pet, now: Long) {
    p.hatchedAt = now
    p.lastTick = now
    log("🐣", "${p.name}破壳而出了！是一只${p.species.label}")
    say("初次见面！请多关照～")
}

// ---------- 随机事件 ----------

private fun GameState.rollRandomEvents(now: Long) {
    val p = pet ?: return
    if (p.isEgg) return
    val last = lastEventRoll ?: now
    val rolls = min(6, ((now - last) / 1_800_000L).toInt())
    if (lastEventRoll == null || rolls > 0) lastEventRoll = now
    repeat(rolls) { if (Random.nextDouble() < 0.3) randomEvent(p) }
}

private fun GameState.randomEvent(p: Pet) {
    if (p.isSleeping) {
        p.happiness = (p.happiness + 8).c()
        log("🌙", "${p.name}做了个美梦，醒来心情会很好")
        return
    }
    var roll = Random.nextInt(5)
    if (roll == 1 && wish != null) roll = 2
    when (roll) {
        0 -> {
            val n = Random.nextInt(5, 21)
            coins += n
            log("💰", "${p.name}在角落捡到了 $n 枚金币")
            say("看我捡到了什么！")
        }
        1 -> makeWish()
        2 -> ShopItem.catalog.filter { it.category == ItemCategory.FOOD && it.price in 1..25 }.randomOrNull()?.let {
            inventory[it.id] = (inventory[it.id] ?: 0) + 1
            log("🎁", "${p.name}不知从哪叼回来一个${it.emoji}${it.name}")
        }
        3 -> {
            p.happiness = (p.happiness + 5).c(); p.energy = (p.energy - 3).c()
            log("🦋", "${p.name}追了一会儿蝴蝶")
        }
        else -> {
            p.happiness = (p.happiness - 5).c()
            log("🌧️", "外面下雨了，${p.name}有点郁闷")
        }
    }
}

fun GameState.makeWish(now: Long = now()) {
    val p = pet ?: return
    val food = ShopItem.catalog.filter { it.category == ItemCategory.FOOD && it.price > 0 }.random()
    wish = Wish(food.id, now + 3 * 3_600_000L)
    log("💭", "${p.name}许下心愿：想吃${food.emoji}${food.name}")
    say("好想吃${food.emoji}…")
}

// ---------- 互动 ----------

fun GameState.feed(itemId: String): String {
    val p = pet ?: return "还没有宠物"
    val item = ShopItem.find(itemId)?.takeIf { it.category == ItemCategory.FOOD } ?: return "这个不能吃哦"
    if (p.isEgg) return say("蛋蛋还不会吃东西～")
    if (p.isSleeping) return say("嘘…它正在睡觉")
    if (count(itemId) <= 0) return "背包里没有${item.name}了"
    if (p.hunger >= 95) {
        p.happiness = (p.happiness - 2).c()
        return say("吃不下啦～肚子圆滚滚")
    }
    val favorite = item.id == p.species.favoriteFood
    p.hunger = (p.hunger + item.hunger).c()
    p.happiness = (p.happiness + item.happiness * (if (favorite) 1.5 else 1.0) + (if (favorite) 8 else 0)).c()
    p.energy = (p.energy + item.energy).c()
    p.health = (p.health + item.health).c()
    consume(itemId)
    counters.feeds++
    addXp(item.xp)

    if (wish?.itemId == itemId) {
        wish = null
        counters.wishesGranted++
        coins += 30
        addXp(15)
        log("🌠", "满足了${p.name}的心愿！获得 30 金币")
        return say("就是这个！你最懂我了！")
    }
    return say(if (favorite) "${item.emoji} 是我的最爱！" else "${item.emoji} 好吃～")
}

/** 灵动岛上的一键喂食：心愿食物 > 最爱食物 > 背包里最顶饱的 > 小饼干 */
fun GameState.quickFeed(): String {
    val p = pet ?: return "还没有宠物"
    wish?.let { if (count(it.itemId) > 0) return feed(it.itemId) }
    if (count(p.species.favoriteFood) > 0) return feed(p.species.favoriteFood)
    val best = ShopItem.catalog
        .filter { it.category == ItemCategory.FOOD && it.price > 0 && count(it.id) > 0 && it.health >= 0 }
        .maxByOrNull { it.hunger }
    return feed(best?.id ?: ShopItem.FREE_SNACK)
}

fun GameState.play(toyId: String? = null): String {
    val p = pet ?: return "还没有宠物"
    if (p.isEgg) return say("蛋蛋滚来滚去～")
    if (p.isSleeping) return say("Zzz…等它睡醒再玩吧")
    if (p.isSick) return say("生病了玩不动…")
    if (p.energy < 12) return say("太累了，想睡觉…")
    var happy = 15.0
    var energy = -10.0
    var xp = 6
    var line = "好好玩！再来再来！"
    val toy = ShopItem.find(toyId)
    if (toy != null && toy.category == ItemCategory.TOY && count(toy.id) > 0) {
        happy = toy.happiness; energy = toy.energy; xp = toy.xp
        consume(toy.id)
        line = "${toy.emoji} 太好玩啦！"
    }
    p.happiness = (p.happiness + happy).c()
    p.energy = (p.energy + energy).c()
    p.hunger = (p.hunger - 5).c()
    p.cleanliness = (p.cleanliness - 4).c()
    counters.plays++
    addXp(xp)
    return say(line)
}

fun GameState.pat(now: Long = now()): String {
    val p = pet ?: return "还没有宠物"
    if (p.isEgg) {
        p.eggTaps++
        tick(now)
        if (!p.isEgg) return say("出来啦！")
        return say(listOf("(蛋壳动了一下)", "(咔…出现了一道裂缝)", "(里面有声音！)").random())
    }
    if (p.isSleeping) {
        p.happiness = (p.happiness + 1).c()
        return say("(翻了个身，继续睡)")
    }
    counters.pats++
    p.lovedUntil = now + 300_000
    // 30 秒冷却，防止连点刷经验
    val cooled = p.lastPattedAt?.let { now - it > 30_000 } ?: true
    p.lastPattedAt = now
    p.happiness = (p.happiness + if (cooled) 6 else 1).c()
    if (cooled) addXp(2)
    return say(listOf("蹭蹭～", "呼噜呼噜…", "好舒服～", "再摸一下嘛", "❤️").random())
}

fun GameState.clean(): String {
    val p = pet ?: return "还没有宠物"
    if (p.isEgg) return say("蛋蛋被擦得亮晶晶")
    if (p.poops == 0 && p.cleanliness > 90) return say("已经很干净啦！")
    val hadPoop = p.poops > 0
    val soap = count("soap") > 0
    p.poops = 0
    p.cleanliness = if (soap) 100.0 else (p.cleanliness + 35).c()
    p.happiness = (p.happiness + 3).c()
    if (soap) consume("soap")
    counters.cleans++
    addXp(3)
    return say(if (hadPoop) "便便清理完毕，香香的！" else if (soap) "🧼 泡泡浴好舒服～" else "洗白白啦～")
}

fun GameState.toggleSleep(): String {
    val p = pet ?: return "还没有宠物"
    if (p.isEgg) return say("蛋蛋一直在睡觉呀")
    if (p.isSleeping) {
        p.isSleeping = false
        if (p.energy < 60) {
            p.happiness = (p.happiness - 6).c()
            return say("被吵醒了…有点起床气")
        }
        return say("早上好！")
    }
    if (p.energy > 85) return say("还不困！再玩会儿")
    p.isSleeping = true
    return say("晚安…Zzz")
}

fun GameState.heal(): String {
    val p = pet ?: return "还没有宠物"
    if (!p.isSick) return say("我很健康哦！")
    if (count("medicine") <= 0) return "没有感冒药了，去商店买一盒吧"
    return use("medicine")
}

fun GameState.use(itemId: String): String {
    val item = ShopItem.find(itemId) ?: return "未知物品"
    return when (item.category) {
        ItemCategory.FOOD -> feed(itemId)
        ItemCategory.TOY -> play(itemId)
        ItemCategory.ACCESSORY -> equip(itemId)
        ItemCategory.THEME -> setTheme(itemId)
        ItemCategory.CARE -> {
            if (itemId == "soap") return clean()
            val p = pet?.takeIf { !it.isEgg } ?: return "等孵化后再用吧"
            if (count(itemId) <= 0) return "没有${item.name}了"
            p.hunger = (p.hunger + item.hunger).c()
            p.energy = (p.energy + item.energy).c()
            p.health = (p.health + item.health).c()
            if (item.cures && p.isSick) {
                p.isSick = false
                p.sickMeter = 0.0
                counters.cures++
                log("💊", "${p.name}吃了药，病好啦")
            }
            consume(itemId)
            addXp(item.xp)
            say(if (item.cures) "苦苦的…但是好多了！" else "${item.emoji} 感觉充满力量！")
        }
    }
}

fun GameState.equip(accessoryId: String?): String {
    val p = pet ?: return "还没有宠物"
    if (accessoryId != null && p.accessory != accessoryId) {
        val item = ShopItem.find(accessoryId)
        if (accessoryId !in ownedAccessories || item == null) return "还没有这件装扮"
        p.accessory = accessoryId
        return say("${item.emoji} 好看吗？")
    }
    p.accessory = null
    return say("摘下来啦")
}

fun GameState.setTheme(id: String): String {
    if (id !in ownedThemes) return "还没有这个主题"
    theme = id
    return "已切换主题：${PetTheme.find(id).name}"
}

// ---------- 商店 / 签到 / 游戏 ----------

fun GameState.buy(itemId: String): String {
    val item = ShopItem.find(itemId)?.takeIf { it.price > 0 } ?: return "这个不用买"
    if (item.minLevel > (pet?.level ?: 1)) return "需要宠物达到 ${item.minLevel} 级"
    if (!item.category.consumable && owns(item)) return "已经拥有了"
    if (coins < item.price) return "金币不够，去玩小游戏赚一点吧"
    coins -= item.price
    when (item.category) {
        ItemCategory.ACCESSORY -> ownedAccessories += item.id
        ItemCategory.THEME -> ownedThemes += item.id
        else -> inventory[item.id] = (inventory[item.id] ?: 0) + 1
    }
    return "购买了 ${item.emoji}${item.name}"
}

fun GameState.checkIn(now: Long = now()): String {
    if (hasCheckedInToday) return "今天已经签到过啦"
    val last = counters.lastCheckIn
    counters.checkInStreak = if (last != null && sameDay(last, now - 86_400_000L)) counters.checkInStreak + 1 else 1
    counters.lastCheckIn = now
    val reward = 20 + min(counters.checkInStreak, 7) * 5
    coins += reward
    log("📅", "签到第 ${counters.checkInStreak} 天，获得 $reward 金币")
    if (wish == null && counters.checkInStreak % 3 == 0) makeWish(now)
    return "签到成功！连续 ${counters.checkInStreak} 天 +$reward 金币"
}

/** 能否开始小游戏，不能则返回原因 */
val GameState.gameBlocker: String?
    get() {
        val p = pet ?: return "还没有宠物"
        return when {
            p.isEgg -> "等宠物孵化后才能玩"
            p.isSleeping -> "宠物在睡觉"
            p.isSick -> "宠物生病了，先治病吧"
            p.energy < 12 -> "宠物太累了，让它睡一会儿"
            else -> null
        }
    }

fun GameState.finishGame(game: GameKind, score: Int, coinsEarned: Int): String {
    val p = pet ?: return ""
    coins += coinsEarned
    p.happiness = (p.happiness + 12).c()
    p.energy = (p.energy - 10).c()
    p.hunger = (p.hunger - 5).c()
    counters.gamesPlayed++
    counters.playedGames += game.name
    if (score > (counters.highScores[game.name] ?: 0)) counters.highScores[game.name] = score
    addXp(8 + coinsEarned / 5)
    log(game.emoji, "和${p.name}玩了${game.label}，赚到 $coinsEarned 金币")
    return say("和你一起玩最开心了！+$coinsEarned 金币")
}

// ---------- 成长 / 成就 ----------

fun GameState.addXp(amount: Int) {
    val p = pet ?: return
    if (p.isEgg || amount <= 0) return
    val oldStage = p.stage
    p.xp += amount
    while (p.xp >= p.xpToNext) {
        p.xp -= p.xpToNext
        p.level++
        coins += p.level * 10
        log("⭐", "${p.name}升到了 ${p.level} 级！获得 ${p.level * 10} 金币")
    }
    if (p.stage != oldStage) {
        log("✨", "${p.name}进入了${if (p.stage == Stage.ADULT) p.formTitle else p.stage.label}！")
    }
}

fun GameState.checkAchievements(): List<Achievement> {
    val fresh = Achievement.entries.filter { it.name !in achievements && it.isMet(this) }
    for (a in fresh) {
        achievements += a.name
        coins += a.reward
        log("🏆", "解锁成就「${a.title}」+${a.reward} 金币")
    }
    return fresh
}

// ---------- 工具 ----------

fun GameState.adopt(name: String, species: Species, now: Long = now()) {
    pet = Pet(name = name, species = species, birthday = now)
    wish = null
    lastEventRoll = null
    log("🥚", "领养了一颗${species.label}蛋，取名「$name」")
    say("(蛋蛋在轻轻晃动)")
}

private fun GameState.consume(itemId: String) {
    if (itemId == ShopItem.FREE_SNACK) return
    val left = (inventory[itemId] ?: 0) - 1
    if (left > 0) inventory[itemId] = left else inventory.remove(itemId)
}

fun GameState.log(emoji: String, text: String, now: Long = now()) {
    diary.add(0, DiaryEntry(now, emoji, text))
    while (diary.size > 200) diary.removeAt(diary.lastIndex)
}

fun GameState.say(line: String, now: Long = now()): String {
    lastMessage = line
    lastMessageAt = now
    return line
}

/** 气泡文字：最近 2 分钟的互动反馈优先，否则随机说一句符合心情的话 */
fun GameState.speech(now: Long = now()): String {
    val msg = lastMessage
    val at = lastMessageAt
    if (msg != null && at != null && now - at < 120_000) return msg
    val p = pet ?: return ""
    val w = wish
    if (w != null && !p.isSleeping) ShopItem.find(w.itemId)?.let { return "想吃${it.emoji}${it.name}！" }
    return p.mood(now).lines.random()
}

/** 灵动岛最需要提醒的状态 */
fun GameState.alertEmoji(): String {
    val p = pet ?: return "🥚"
    return when {
        p.isEgg -> "🥚"
        p.isSick -> "🤒"
        p.isSleeping -> "💤"
        wish != null -> ShopItem.find(wish!!.itemId)?.emoji ?: "💭"
        p.poops > 0 -> "💩"
        else -> p.mood().emoji
    }
}
