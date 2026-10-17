import Foundation

// MARK: - 物种

enum Species: String, Codable, CaseIterable, Identifiable, Hashable {
    case cat, dog, bunny, dragon, slime

    var id: String { rawValue }

    var name: String {
        switch self {
        case .cat: "猫咪"
        case .dog: "小狗"
        case .bunny: "兔兔"
        case .dragon: "小龙"
        case .slime: "史莱姆"
        }
    }

    var intro: String {
        switch self {
        case .cat: "傲娇又黏人，爱吃小鱼干。心情掉得快，记得多摸摸。"
        case .dog: "精力旺盛的玩伴，最爱玩耍和肉骨头。饿得比较快。"
        case .bunny: "安静爱干净，喜欢胡萝卜。身体有点娇弱。"
        case .dragon: "稀有的小火龙，喜欢辣椒，体力超好但很能吃。"
        case .slime: "软糯糯的果冻生物，几乎不会脏，但容易无聊。"
        }
    }

    /// 各项属性的消耗倍率：饱腹 / 心情 / 精力 / 清洁
    var decay: (hunger: Double, happiness: Double, energy: Double, cleanliness: Double) {
        switch self {
        case .cat: (1.0, 1.3, 0.9, 0.8)
        case .dog: (1.25, 0.9, 1.1, 1.2)
        case .bunny: (0.9, 1.0, 1.0, 0.7)
        case .dragon: (1.4, 0.9, 0.7, 1.0)
        case .slime: (0.8, 1.4, 1.0, 0.4)
        }
    }

    var favoriteFood: String {
        switch self {
        case .cat: "fish"
        case .dog: "bone"
        case .bunny: "carrot"
        case .dragon: "pepper"
        case .slime: "jelly"
        }
    }

    /// 猜拳时的小偏好：0 石头 1 剪刀 2 布
    var rpsBias: Int {
        switch self {
        case .cat: 1
        case .dog: 0
        case .bunny: 2
        case .dragon: 0
        case .slime: 2
        }
    }
}

// MARK: - 成长阶段

enum GrowthStage: Int, Codable, Comparable, CaseIterable {
    case egg, baby, child, teen, adult

    static func < (lhs: GrowthStage, rhs: GrowthStage) -> Bool { lhs.rawValue < rhs.rawValue }

    var name: String {
        switch self {
        case .egg: "蛋蛋期"
        case .baby: "宝宝期"
        case .child: "幼年期"
        case .teen: "少年期"
        case .adult: "成年期"
        }
    }

    var scale: Double {
        switch self {
        case .egg: 0.85
        case .baby: 0.72
        case .child: 0.82
        case .teen: 0.92
        case .adult: 1.0
        }
    }

    static func forLevel(_ level: Int) -> GrowthStage {
        switch level {
        case ..<5: .baby
        case 5..<10: .child
        case 10..<18: .teen
        default: .adult
        }
    }
}

// MARK: - 心情

enum Mood: String, Codable, Hashable {
    case egg, happy, normal, hungry, sleepy, dirty, sick, sleeping, loved, bored, sad

    var emoji: String {
        switch self {
        case .egg: "🥚"
        case .happy: "😊"
        case .normal: "🙂"
        case .hungry: "🍽️"
        case .sleepy: "🥱"
        case .dirty: "🫧"
        case .sick: "🤒"
        case .sleeping: "💤"
        case .loved: "💗"
        case .bored: "😐"
        case .sad: "😢"
        }
    }

    var title: String {
        switch self {
        case .egg: "等待孵化"
        case .happy: "超开心"
        case .normal: "还不错"
        case .hungry: "饿了"
        case .sleepy: "困了"
        case .dirty: "脏兮兮"
        case .sick: "生病了"
        case .sleeping: "睡觉中"
        case .loved: "被爱着"
        case .bored: "好无聊"
        case .sad: "不开心"
        }
    }

    var lines: [String] {
        switch self {
        case .egg: ["(咚咚…)", "(蛋壳晃了一下)", "(里面好像有动静)"]
        case .happy: ["今天也好开心！", "最喜欢你啦～", "嘿嘿嘿", "要一直在一起哦"]
        case .normal: ["在干嘛呀？", "陪我玩一会嘛", "嗯哼～", "灵动岛的风景不错"]
        case .hungry: ["肚子咕咕叫…", "想吃好吃的！", "饭饭…饭饭…"]
        case .sleepy: ["好困哦…", "眼皮在打架", "想睡觉觉"]
        case .dirty: ["身上黏黏的…", "想洗澡澡", "有点臭臭的"]
        case .sick: ["头好晕…", "需要吃药药", "呜…不舒服"]
        case .sleeping: ["Zzz…", "呼噜噜…", "(梦到了小鱼干)"]
        case .loved: ["蹭蹭你～", "再摸摸嘛", "❤️❤️❤️"]
        case .bored: ["好无聊啊…", "来玩游戏吧！", "发呆中…"]
        case .sad: ["你是不是忘了我…", "呜呜…", "想要抱抱"]
        }
    }
}

// MARK: - 宠物

struct Pet: Codable, Hashable {
    var name: String
    var species: Species
    var birthday: Date
    var hatchedAt: Date?
    var eggTaps = 0

    var level = 1
    var xp = 0

    var hunger: Double = 80
    var happiness: Double = 80
    var energy: Double = 80
    var cleanliness: Double = 90
    var health: Double = 100

    var isSleeping = false
    var isSick = false
    var poops = 0
    var poopMeter: Double = 0
    var sickMeter: Double = 0

    var accessory: String?
    var lastPattedAt: Date?
    var lovedUntil: Date?
    var careScore: Double = 60
    var lastTick: Date

    init(name: String, species: Species, now: Date = .now) {
        self.name = name
        self.species = species
        self.birthday = now
        self.lastTick = now
    }

    var isEgg: Bool { hatchedAt == nil }

    var stage: GrowthStage { isEgg ? .egg : .forLevel(level) }

    var xpToNext: Int { 20 + level * 15 }

    var xpProgress: Double { Double(xp) / Double(xpToNext) }

    /// 成年后根据照顾质量决定形态称号
    var formTitle: String {
        guard stage == .adult else { return stage.name }
        switch careScore {
        case 80...: return "天使形态"
        case 55..<80: return "元气形态"
        default: return "调皮形态"
        }
    }

    var ageDays: Int {
        max(0, Calendar.current.dateComponents([.day], from: birthday, to: .now).day ?? 0)
    }

    func mood(at now: Date = .now) -> Mood {
        if isEgg { return .egg }
        if isSleeping { return .sleeping }
        if isSick { return .sick }
        if let lovedUntil, lovedUntil > now { return .loved }
        if hunger < 25 { return .hungry }
        if energy < 20 { return .sleepy }
        if cleanliness < 25 || poops >= 2 { return .dirty }
        if happiness < 25 { return .sad }
        if happiness < 45 { return .bored }
        if (hunger + happiness + energy + cleanliness) / 4 > 72 { return .happy }
        return .normal
    }

    /// 当前每小时饱腹消耗
    var hungerRate: Double { 7 * species.decay.hunger * (isSleeping ? 0.4 : 1) }
}

// MARK: - 物品

enum ItemCategory: String, Codable, CaseIterable, Identifiable {
    case food, toy, care, accessory, theme
    var id: String { rawValue }

    var name: String {
        switch self {
        case .food: "食物"
        case .toy: "玩具"
        case .care: "护理"
        case .accessory: "装扮"
        case .theme: "主题"
        }
    }

    /// 装扮和主题买一次永久拥有，其他是消耗品
    var isConsumable: Bool { self == .food || self == .toy || self == .care }
}

struct ShopItem: Identifiable, Hashable {
    let id: String
    let name: String
    let emoji: String
    let price: Int
    let category: ItemCategory
    var hunger: Double = 0
    var happiness: Double = 0
    var energy: Double = 0
    var cleanliness: Double = 0
    var health: Double = 0
    var xp = 0
    var cures = false
    var minLevel = 1
    var note = ""

    var effectText: String {
        var parts: [String] = []
        func add(_ label: String, _ v: Double) {
            if v != 0 { parts.append("\(label)\(v > 0 ? "+" : "")\(Int(v))") }
        }
        add("饱腹", hunger)
        add("心情", happiness)
        add("精力", energy)
        add("清洁", cleanliness)
        add("健康", health)
        if cures { parts.append("治愈疾病") }
        if !note.isEmpty { parts.append(note) }
        return parts.joined(separator: " · ")
    }

    static let freeSnackID = "snack"

    static let catalog: [ShopItem] = [
        // 食物
        ShopItem(id: "snack", name: "小饼干", emoji: "🍪", price: 0, category: .food, hunger: 12, happiness: 2, xp: 1, note: "无限供应"),
        ShopItem(id: "apple", name: "苹果", emoji: "🍎", price: 8, category: .food, hunger: 15, health: 5, xp: 2),
        ShopItem(id: "carrot", name: "胡萝卜", emoji: "🥕", price: 10, category: .food, hunger: 25, health: 3, xp: 3),
        ShopItem(id: "milk", name: "牛奶", emoji: "🥛", price: 12, category: .food, hunger: 15, energy: 10, xp: 3),
        ShopItem(id: "fish", name: "小鱼干", emoji: "🐟", price: 15, category: .food, hunger: 30, happiness: 5, xp: 4),
        ShopItem(id: "bone", name: "肉骨头", emoji: "🍖", price: 15, category: .food, hunger: 35, xp: 4),
        ShopItem(id: "jelly", name: "果冻", emoji: "🍮", price: 18, category: .food, hunger: 20, happiness: 15, xp: 4),
        ShopItem(id: "pepper", name: "辣椒", emoji: "🌶️", price: 20, category: .food, hunger: 20, energy: 15, xp: 4),
        ShopItem(id: "icecream", name: "冰淇淋", emoji: "🍦", price: 25, category: .food, hunger: 10, happiness: 20, xp: 5),
        ShopItem(id: "sushi", name: "寿司", emoji: "🍣", price: 35, category: .food, hunger: 40, happiness: 10, xp: 6),
        ShopItem(id: "cake", name: "草莓蛋糕", emoji: "🍰", price: 40, category: .food, hunger: 30, happiness: 25, health: -3, xp: 8),
        // 玩具
        ShopItem(id: "ball", name: "网球", emoji: "🎾", price: 30, category: .toy, happiness: 25, energy: -8, xp: 8),
        ShopItem(id: "yarn", name: "毛线球", emoji: "🧶", price: 25, category: .toy, happiness: 22, energy: -6, xp: 7),
        ShopItem(id: "kite", name: "风筝", emoji: "🪁", price: 45, category: .toy, happiness: 35, energy: -12, xp: 12),
        ShopItem(id: "console", name: "游戏机", emoji: "🎮", price: 60, category: .toy, happiness: 40, energy: -5, xp: 15),
        // 护理
        ShopItem(id: "soap", name: "香香泡泡", emoji: "🧼", price: 10, category: .care, cleanliness: 100, xp: 3, note: "洗得干干净净"),
        ShopItem(id: "medicine", name: "感冒药", emoji: "💊", price: 30, category: .care, health: 20, cures: true, xp: 5),
        ShopItem(id: "potion", name: "营养剂", emoji: "🧪", price: 40, category: .care, health: 35, xp: 5),
        ShopItem(id: "grape", name: "能量葡萄", emoji: "🍇", price: 25, category: .care, hunger: 5, energy: 35, xp: 4),
        // 装扮
        ShopItem(id: "flower", name: "小花", emoji: "🌸", price: 40, category: .accessory),
        ShopItem(id: "bow", name: "蝴蝶结", emoji: "🎀", price: 50, category: .accessory),
        ShopItem(id: "cap", name: "鸭舌帽", emoji: "🧢", price: 60, category: .accessory),
        ShopItem(id: "glasses", name: "墨镜", emoji: "🕶️", price: 70, category: .accessory, minLevel: 3),
        ShopItem(id: "tophat", name: "礼帽", emoji: "🎩", price: 80, category: .accessory, minLevel: 5),
        ShopItem(id: "headphones", name: "耳机", emoji: "🎧", price: 90, category: .accessory, minLevel: 6),
        ShopItem(id: "gradcap", name: "学士帽", emoji: "🎓", price: 120, category: .accessory, minLevel: 10),
        ShopItem(id: "crown", name: "皇冠", emoji: "👑", price: 200, category: .accessory, minLevel: 15),
        // 主题
        ShopItem(id: "theme.sakura", name: "樱花粉", emoji: "🌸", price: 80, category: .theme),
        ShopItem(id: "theme.mint", name: "薄荷绿", emoji: "🌿", price: 80, category: .theme),
        ShopItem(id: "theme.sunset", name: "落日橙", emoji: "🌇", price: 100, category: .theme),
        ShopItem(id: "theme.ocean", name: "深海蓝", emoji: "🌊", price: 100, category: .theme),
        ShopItem(id: "theme.galaxy", name: "星河紫", emoji: "🌌", price: 150, category: .theme, minLevel: 8),
    ]

    static func find(_ id: String?) -> ShopItem? {
        guard let id else { return nil }
        return catalog.first { $0.id == id }
    }
}

// MARK: - 小游戏

enum GameKind: String, Codable, CaseIterable, Identifiable {
    case catchFood, memory, reaction, rps
    var id: String { rawValue }

    var name: String {
        switch self {
        case .catchFood: "接零食"
        case .memory: "翻翻乐"
        case .reaction: "反应力挑战"
        case .rps: "猜拳大战"
        }
    }

    var emoji: String {
        switch self {
        case .catchFood: "🧺"
        case .memory: "🃏"
        case .reaction: "⚡️"
        case .rps: "✊"
        }
    }

    var intro: String {
        switch self {
        case .catchFood: "左右拖动宠物接住掉落的零食，小心炸弹！"
        case .memory: "翻开卡片找出所有成对的图案，步数越少奖励越多。"
        case .reaction: "宠物跳起来的瞬间立刻点击，测测你的反应速度。"
        case .rps: "和宠物五局三胜猜拳，它可是有小习惯的哦。"
        }
    }
}

// MARK: - 日记 / 心愿 / 统计

struct DiaryEntry: Codable, Identifiable, Hashable {
    var id = UUID()
    var date: Date
    var emoji: String
    var text: String
}

struct Wish: Codable, Hashable {
    var itemID: String
    var expires: Date
}

struct Counters: Codable, Hashable {
    var feeds = 0
    var plays = 0
    var pats = 0
    var cleans = 0
    var cures = 0
    var gamesPlayed = 0
    var wishesGranted = 0
    var checkInStreak = 0
    var lastCheckIn: Date?
    var playedGames: Set<String> = []
    var highScores: [String: Int] = [:]
}

// MARK: - 总存档

struct GameState: Codable {
    var pet: Pet?
    var coins = 100
    var inventory: [String: Int] = ["fish": 2, "soap": 1, "ball": 1]
    var ownedAccessories: Set<String> = []
    var ownedThemes: Set<String> = ["theme.night"]
    var theme = "theme.night"
    var achievements: Set<String> = []
    var diary: [DiaryEntry] = []
    var counters = Counters()
    var wish: Wish?
    var lastMessage: String?
    var lastMessageAt: Date?
    var lastEventRoll: Date?

    func count(of itemID: String) -> Int {
        itemID == ShopItem.freeSnackID ? .max : inventory[itemID, default: 0]
    }

    func owns(_ item: ShopItem) -> Bool {
        switch item.category {
        case .accessory: ownedAccessories.contains(item.id)
        case .theme: ownedThemes.contains(item.id)
        default: count(of: item.id) > 0
        }
    }

    var hasCheckedInToday: Bool {
        guard let last = counters.lastCheckIn else { return false }
        return Calendar.current.isDateInToday(last)
    }
}
