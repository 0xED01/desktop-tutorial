package com.islandpet.model

enum class Achievement(val title: String, val detail: String, val emoji: String, val reward: Int, val isMet: (GameState) -> Boolean) {
    HATCH("破壳而出", "孵化你的第一只宠物", "🐣", 20, { s -> s.pet?.isEgg == false }),
    FIRST_FEED("第一口饭", "第一次喂食", "🍼", 20, { it.counters.feeds >= 1 }),
    GOURMET("小小美食家", "累计喂食 50 次", "🍱", 100, { it.counters.feeds >= 50 }),
    PLAYMATE("最佳玩伴", "累计玩耍 30 次", "🎈", 50, { it.counters.plays >= 30 }),
    PAT_MASTER("撸宠大师", "累计摸摸 100 次", "🤲", 100, { it.counters.pats >= 100 }),
    TIDY("爱干净", "累计清洁 20 次", "🛁", 50, { it.counters.cleans >= 20 }),
    DOCTOR("妙手回春", "治好一次生病", "🩺", 50, { it.counters.cures >= 1 }),
    LEVEL5("茁壮成长", "宠物达到 5 级", "🌱", 50, { (it.pet?.level ?: 0) >= 5 }),
    LEVEL10("少年初成", "宠物达到 10 级", "🌿", 100, { (it.pet?.level ?: 0) >= 10 }),
    ADULT("成年礼", "宠物长大成年（18 级）", "🌳", 150, { (it.pet?.level ?: 0) >= 18 }),
    RICH("小富翁", "同时拥有 500 金币", "💰", 150, { it.coins >= 500 }),
    STREAK7("七日之约", "连续签到 7 天", "📅", 150, { it.counters.checkInStreak >= 7 }),
    ALL_GAMES("全能玩家", "玩过所有小游戏", "🕹️", 50, { it.counters.playedGames.size >= GameKind.entries.size }),
    CATCH_PRO("接零食达人", "接零食单局 40 分", "🧺", 50, { (it.counters.highScores[GameKind.CATCH.name] ?: 0) >= 40 }),
    MEMORY_PRO("过目不忘", "翻翻乐 10 步内完成", "🧠", 50, { (it.counters.highScores[GameKind.MEMORY.name] ?: 0) >= 1 }),
    WISH_GRANTER("心愿精灵", "满足宠物 5 个心愿", "🌠", 100, { it.counters.wishesGranted >= 5 }),
    FASHION("时尚达人", "收集 5 件装扮", "👗", 50, { it.ownedAccessories.size >= 5 }),
}
