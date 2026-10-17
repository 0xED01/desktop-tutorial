import Foundation

enum Achievement: String, CaseIterable, Identifiable {
    case hatch, firstFeed, gourmet, playmate, patMaster, tidy, doctor
    case level5, level10, adult, rich, streak7, allGames, catchPro, memoryPro, wishGranter, fashion

    var id: String { rawValue }

    var title: String {
        switch self {
        case .hatch: "破壳而出"
        case .firstFeed: "第一口饭"
        case .gourmet: "小小美食家"
        case .playmate: "最佳玩伴"
        case .patMaster: "撸宠大师"
        case .tidy: "爱干净"
        case .doctor: "妙手回春"
        case .level5: "茁壮成长"
        case .level10: "少年初成"
        case .adult: "成年礼"
        case .rich: "小富翁"
        case .streak7: "七日之约"
        case .allGames: "全能玩家"
        case .catchPro: "接零食达人"
        case .memoryPro: "过目不忘"
        case .wishGranter: "心愿精灵"
        case .fashion: "时尚达人"
        }
    }

    var detail: String {
        switch self {
        case .hatch: "孵化你的第一只宠物"
        case .firstFeed: "第一次喂食"
        case .gourmet: "累计喂食 50 次"
        case .playmate: "累计玩耍 30 次"
        case .patMaster: "累计摸摸 100 次"
        case .tidy: "累计清洁 20 次"
        case .doctor: "治好一次生病"
        case .level5: "宠物达到 5 级"
        case .level10: "宠物达到 10 级"
        case .adult: "宠物长大成年（18 级）"
        case .rich: "同时拥有 500 金币"
        case .streak7: "连续签到 7 天"
        case .allGames: "玩过所有小游戏"
        case .catchPro: "接零食单局 40 分"
        case .memoryPro: "翻翻乐 10 步内完成"
        case .wishGranter: "满足宠物 5 个心愿"
        case .fashion: "收集 5 件装扮"
        }
    }

    var emoji: String {
        switch self {
        case .hatch: "🐣"
        case .firstFeed: "🍼"
        case .gourmet: "🍱"
        case .playmate: "🎈"
        case .patMaster: "🤲"
        case .tidy: "🛁"
        case .doctor: "🩺"
        case .level5: "🌱"
        case .level10: "🌿"
        case .adult: "🌳"
        case .rich: "💰"
        case .streak7: "📅"
        case .allGames: "🕹️"
        case .catchPro: "🧺"
        case .memoryPro: "🧠"
        case .wishGranter: "🌠"
        case .fashion: "👗"
        }
    }

    var reward: Int {
        switch self {
        case .hatch, .firstFeed: 20
        case .adult, .rich, .streak7: 150
        case .level10, .gourmet, .patMaster, .wishGranter: 100
        default: 50
        }
    }

    func isMet(_ s: GameState) -> Bool {
        let c = s.counters
        let level = s.pet?.level ?? 0
        switch self {
        case .hatch: return s.pet.map { !$0.isEgg } ?? false
        case .firstFeed: return c.feeds >= 1
        case .gourmet: return c.feeds >= 50
        case .playmate: return c.plays >= 30
        case .patMaster: return c.pats >= 100
        case .tidy: return c.cleans >= 20
        case .doctor: return c.cures >= 1
        case .level5: return level >= 5
        case .level10: return level >= 10
        case .adult: return level >= 18
        case .rich: return s.coins >= 500
        case .streak7: return c.checkInStreak >= 7
        case .allGames: return c.playedGames.count >= GameKind.allCases.count
        case .catchPro: return c.highScores[GameKind.catchFood.rawValue, default: 0] >= 40
        case .memoryPro: return c.highScores[GameKind.memory.rawValue, default: 0] >= 1
        case .wishGranter: return c.wishesGranted >= 5
        case .fashion: return s.ownedAccessories.count >= 5
        }
    }
}
