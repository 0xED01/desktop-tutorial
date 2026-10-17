import ActivityKit
import Foundation

struct PetActivityAttributes: ActivityAttributes {
    /// 灵动岛上展示的实时状态（每次互动或后台刷新时更新）
    struct ContentState: Codable, Hashable {
        var species: Species
        var stage: GrowthStage
        var mood: Mood
        var level: Int
        var xpProgress: Double
        var hunger: Int
        var happiness: Int
        var energy: Int
        var cleanliness: Int
        var health: Int
        var poops: Int
        var isSleeping: Bool
        var isSick: Bool
        var accessoryEmoji: String?
        var wishEmoji: String?
        var coins: Int
        var themeID: String
        var message: String
        /// 每次更新翻转一次，让宠物在灵动岛里换个姿势「动起来」
        var pose: Int
        /// 饱腹满格 → 饿到 0 的时间段，系统会自动走倒计时，不需要 App 刷新
        var hungerStart: Date
        var hungerEnd: Date
        /// 睡觉时预计睡饱的时间
        var wakeAt: Date?
    }

    var petName: String
}

extension PetActivityAttributes.ContentState {
    init?(_ s: GameState, pose: Int, now: Date = .now) {
        guard let p = s.pet else { return nil }
        let rate = max(p.hungerRate, 0.1) * (p.isEgg ? 0 : 1)
        let hoursLeft = rate > 0 ? p.hunger / rate : 48
        let hoursFull = rate > 0 ? 100 / rate : 48
        let end = now.addingTimeInterval(max(60, hoursLeft * 3600))

        self.init(
            species: p.species,
            stage: p.stage,
            mood: p.mood(at: now),
            level: p.level,
            xpProgress: p.xpProgress,
            hunger: Int(p.hunger.rounded()),
            happiness: Int(p.happiness.rounded()),
            energy: Int(p.energy.rounded()),
            cleanliness: Int(p.cleanliness.rounded()),
            health: Int(p.health.rounded()),
            poops: p.poops,
            isSleeping: p.isSleeping,
            isSick: p.isSick,
            accessoryEmoji: ShopItem.find(p.accessory)?.emoji,
            wishEmoji: s.wish.flatMap { ShopItem.find($0.itemID)?.emoji },
            coins: s.coins,
            themeID: s.theme,
            message: s.speech(now: now),
            pose: pose,
            hungerStart: end.addingTimeInterval(-max(120, hoursFull * 3600)),
            hungerEnd: end,
            wakeAt: p.isSleeping ? now.addingTimeInterval(max(60, (100 - p.energy) / 18 * 3600)) : nil
        )
    }

    var hungerRange: ClosedRange<Date> { hungerStart...max(hungerEnd, hungerStart.addingTimeInterval(1)) }

    /// 灵动岛紧凑视图右侧最需要提醒的状态
    var alertEmoji: String {
        if stage == .egg { return "🥚" }
        if isSick { return "🤒" }
        if isSleeping { return "💤" }
        if let wishEmoji { return wishEmoji }
        if poops > 0 { return "💩" }
        return mood.emoji
    }

    var theme: PetTheme { PetTheme.find(themeID) }
}
