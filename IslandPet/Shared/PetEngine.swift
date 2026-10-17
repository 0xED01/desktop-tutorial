import Foundation

/// 所有玩法规则都在这里，以 GameState 的 mutating 方法实现。
/// 返回值是给玩家看的一句反馈（会显示在 App 提示和灵动岛气泡里）。
extension GameState {

    // MARK: - 时间流逝

    mutating func tick(now: Date = .now) {
        guard var pet else { return }

        if pet.isEgg {
            pet.lastTick = now
            if now.timeIntervalSince(pet.birthday) >= 180 || pet.eggTaps >= 6 {
                hatch(&pet, now: now)
            }
            self.pet = pet
            return
        }

        let elapsed = now.timeIntervalSince(pet.lastTick)
        guard elapsed > 1 else { return }

        // 最多结算 3 天，按 10 分钟一段推进，保证阈值判断准确
        var remaining = min(elapsed, 3 * 24 * 3600)
        while remaining > 0 {
            let step = min(remaining, 600)
            decay(&pet, hours: step / 3600)
            remaining -= step
        }
        pet.lastTick = now
        self.pet = pet

        if let wish, wish.expires < now {
            self.wish = nil
            log("💭", "\(pet.name)的心愿过期了…")
        }
        rollRandomEvents(now: now)
    }

    private mutating func decay(_ p: inout Pet, hours h: Double) {
        let d = p.species.decay
        let sick = p.isSick ? 1.8 : 1.0

        if p.isSleeping {
            p.hunger -= 7 * d.hunger * 0.4 * h
            p.happiness -= 1 * h
            p.energy += 18 * h
            p.cleanliness -= 2 * d.cleanliness * h
        } else {
            p.hunger -= 7 * d.hunger * h
            p.happiness -= 5 * d.happiness * sick * h
            p.energy -= 5 * d.energy * sick * h
            p.cleanliness -= (3 * d.cleanliness + Double(p.poops) * 2) * h
        }

        // 便便：清醒时大约每 3 小时一次
        p.poopMeter += (p.isSleeping ? 0.1 : 0.33) * h
        if p.poopMeter >= 1 {
            p.poopMeter -= 1
            if p.poops < 3 {
                p.poops += 1
                log("💩", "\(p.name)拉了一坨便便")
            }
        }

        // 生病：脏、饿、便便堆积超过 2 小时就会生病
        if p.cleanliness < 20 || p.poops >= 3 || p.hunger < 5 {
            p.sickMeter += h
            if p.sickMeter >= 2, !p.isSick {
                p.isSick = true
                log("🤒", "\(p.name)生病了，快喂药！")
            }
        } else {
            p.sickMeter = max(0, p.sickMeter - h)
        }

        // 健康
        if p.isSick { p.health -= 4 * h }
        if p.hunger < 5 { p.health -= 3 * h }
        if !p.isSick, p.hunger > 40, p.cleanliness > 40 { p.health += 2 * h }

        clamp(&p)

        // 精力耗尽自动睡着，睡饱自动醒来
        if !p.isSleeping, p.energy < 3 {
            p.isSleeping = true
            log("😴", "\(p.name)累得直接睡着了")
        } else if p.isSleeping, p.energy >= 100 {
            p.isSleeping = false
            log("🌞", "\(p.name)睡饱醒来啦")
        }

        // 照顾评分：缓慢趋向当前状态平均值，决定成年形态
        let avg = (p.hunger + p.happiness + p.energy + p.cleanliness + p.health) / 5
        p.careScore += (avg - p.careScore) * min(1, h / 24)
    }

    private func clamp(_ p: inout Pet) {
        p.hunger = p.hunger.clamped
        p.happiness = p.happiness.clamped
        p.energy = p.energy.clamped
        p.cleanliness = p.cleanliness.clamped
        p.health = p.health.clamped
    }

    private mutating func hatch(_ p: inout Pet, now: Date) {
        p.hatchedAt = now
        p.lastTick = now
        log("🐣", "\(p.name)破壳而出了！是一只\(p.species.name)")
        say("初次见面！请多关照～")
    }

    // MARK: - 随机事件

    private mutating func rollRandomEvents(now: Date) {
        guard let pet, !pet.isEgg else { return }
        let last = lastEventRoll ?? now
        let rolls = min(6, Int(now.timeIntervalSince(last) / 1800))
        if lastEventRoll == nil || rolls > 0 {
            lastEventRoll = now
        }
        guard rolls > 0 else { return }

        for _ in 0..<rolls where Double.random(in: 0..<1) < 0.3 {
            randomEvent()
        }
    }

    private mutating func randomEvent() {
        guard var p = pet else { return }
        if p.isSleeping {
            p.happiness = (p.happiness + 8).clamped
            pet = p
            log("🌙", "\(p.name)做了个美梦，醒来心情会很好")
            return
        }
        var roll = Int.random(in: 0..<5)
        if roll == 1, wish != nil { roll = 2 }
        switch roll {
        case 0:
            let n = Int.random(in: 5...20)
            coins += n
            log("🪙", "\(p.name)在角落捡到了 \(n) 枚金币")
            say("看我捡到了什么！")
        case 1:
            makeWish()
        case 2:
            let gifts = ShopItem.catalog.filter { $0.category == .food && $0.price > 0 && $0.price <= 25 }
            if let g = gifts.randomElement() {
                inventory[g.id, default: 0] += 1
                log("🎁", "\(p.name)不知从哪叼回来一个\(g.emoji)\(g.name)")
            }
        case 3:
            p.happiness = (p.happiness + 5).clamped
            p.energy = (p.energy - 3).clamped
            pet = p
            log("🦋", "\(p.name)追了一会儿蝴蝶")
        default:
            p.happiness = (p.happiness - 5).clamped
            pet = p
            log("🌧️", "外面下雨了，\(p.name)有点郁闷")
        }
    }

    mutating func makeWish(now: Date = .now) {
        let foods = ShopItem.catalog.filter { $0.category == .food && $0.price > 0 }
        guard let food = foods.randomElement(), let name = pet?.name else { return }
        wish = Wish(itemID: food.id, expires: now.addingTimeInterval(3 * 3600))
        log("💭", "\(name)许下心愿：想吃\(food.emoji)\(food.name)")
        say("好想吃\(food.emoji)…")
    }

    // MARK: - 互动

    mutating func feed(_ itemID: String) -> String {
        guard var p = pet else { return "还没有宠物" }
        guard let item = ShopItem.find(itemID), item.category == .food else { return "这个不能吃哦" }
        if p.isEgg { return say("蛋蛋还不会吃东西～") }
        if p.isSleeping { return say("嘘…它正在睡觉") }
        guard count(of: itemID) > 0 else { return "背包里没有\(item.name)了" }
        if p.hunger >= 95 {
            p.happiness = (p.happiness - 2).clamped
            pet = p
            return say("吃不下啦～肚子圆滚滚")
        }

        let favorite = item.id == p.species.favoriteFood
        p.hunger = (p.hunger + item.hunger).clamped
        p.happiness = (p.happiness + item.happiness * (favorite ? 1.5 : 1) + (favorite ? 8 : 0)).clamped
        p.energy = (p.energy + item.energy).clamped
        p.health = (p.health + item.health).clamped
        pet = p
        consume(itemID)
        counters.feeds += 1
        addXP(item.xp)

        if let wish, wish.itemID == itemID {
            self.wish = nil
            counters.wishesGranted += 1
            coins += 30
            addXP(15)
            log("🌠", "满足了\(p.name)的心愿！获得 30 金币")
            return say("就是这个！你最懂我了！")
        }
        return say(favorite ? "\(item.emoji) 是我的最爱！" : "\(item.emoji) 好吃～")
    }

    /// 灵动岛上的一键喂食：优先心愿食物 > 最爱食物 > 背包里最顶饱的 > 小饼干
    mutating func quickFeed() -> String {
        guard let p = pet else { return "还没有宠物" }
        if let wish, count(of: wish.itemID) > 0 { return feed(wish.itemID) }
        if count(of: p.species.favoriteFood) > 0 { return feed(p.species.favoriteFood) }
        let best = ShopItem.catalog
            .filter { $0.category == .food && $0.price > 0 && count(of: $0.id) > 0 && $0.health >= 0 }
            .max { $0.hunger < $1.hunger }
        return feed(best?.id ?? ShopItem.freeSnackID)
    }

    mutating func play(toy toyID: String? = nil) -> String {
        guard var p = pet else { return "还没有宠物" }
        if p.isEgg { return say("蛋蛋滚来滚去～") }
        if p.isSleeping { return say("Zzz…等它睡醒再玩吧") }
        if p.isSick { return say("生病了玩不动…") }
        if p.energy < 12 { return say("太累了，想睡觉…") }

        var happy = 15.0, energy = -10.0, xp = 6
        var line = "好好玩！再来再来！"
        if let toyID, let toy = ShopItem.find(toyID), toy.category == .toy, count(of: toyID) > 0 {
            happy = toy.happiness
            energy = toy.energy
            xp = toy.xp
            consume(toyID)
            line = "\(toy.emoji) 太好玩啦！"
        }
        p.happiness = (p.happiness + happy).clamped
        p.energy = (p.energy + energy).clamped
        p.hunger = (p.hunger - 5).clamped
        p.cleanliness = (p.cleanliness - 4).clamped
        pet = p
        counters.plays += 1
        addXP(xp)
        return say(line)
    }

    mutating func pat(now: Date = .now) -> String {
        guard var p = pet else { return "还没有宠物" }
        if p.isEgg {
            p.eggTaps += 1
            pet = p
            tick(now: now)
            if pet?.isEgg == false { return say("出来啦！") }
            return say(["(蛋壳动了一下)", "(咔…出现了一道裂缝)", "(里面有声音！)"].randomElement()!)
        }
        if p.isSleeping {
            p.happiness = (p.happiness + 1).clamped
            pet = p
            return say("(翻了个身，继续睡)")
        }
        counters.pats += 1
        p.lovedUntil = now.addingTimeInterval(300)
        // 30 秒冷却，防止连点刷经验
        let cooled = p.lastPattedAt.map { now.timeIntervalSince($0) > 30 } ?? true
        p.lastPattedAt = now
        p.happiness = (p.happiness + (cooled ? 6 : 1)).clamped
        pet = p
        if cooled { addXP(2) }
        return say(["蹭蹭～", "呼噜呼噜…", "好舒服～", "再摸一下嘛", "❤️"].randomElement()!)
    }

    mutating func clean() -> String {
        guard var p = pet else { return "还没有宠物" }
        if p.isEgg { return say("蛋蛋被擦得亮晶晶") }
        if p.poops == 0, p.cleanliness > 90 { return say("已经很干净啦！") }
        let hadPoop = p.poops > 0
        let useSoap = count(of: "soap") > 0
        p.poops = 0
        p.cleanliness = useSoap ? 100 : (p.cleanliness + 35).clamped
        p.happiness = (p.happiness + 3).clamped
        pet = p
        if useSoap { consume("soap") }
        counters.cleans += 1
        addXP(3)
        return say(hadPoop ? "便便清理完毕，香香的！" : (useSoap ? "🧼 泡泡浴好舒服～" : "洗白白啦～"))
    }

    mutating func toggleSleep() -> String {
        guard var p = pet else { return "还没有宠物" }
        if p.isEgg { return say("蛋蛋一直在睡觉呀") }
        if p.isSleeping {
            p.isSleeping = false
            if p.energy < 60 {
                p.happiness = (p.happiness - 6).clamped
                pet = p
                return say("被吵醒了…有点起床气")
            }
            pet = p
            return say("早上好！")
        }
        if p.energy > 85 { return say("还不困！再玩会儿") }
        p.isSleeping = true
        pet = p
        return say("晚安…Zzz")
    }

    mutating func heal() -> String {
        guard let p = pet else { return "还没有宠物" }
        if !p.isSick { return say("我很健康哦！") }
        guard count(of: "medicine") > 0 else { return "没有感冒药了，去商店买一盒吧" }
        return use("medicine")
    }

    /// 使用背包中的道具（食物 / 玩具 / 护理用品）
    mutating func use(_ itemID: String) -> String {
        guard let item = ShopItem.find(itemID) else { return "未知物品" }
        switch item.category {
        case .food: return feed(itemID)
        case .toy: return play(toy: itemID)
        case .care:
            if itemID == "soap" { return clean() }
            guard var p = pet, !p.isEgg else { return "等孵化后再用吧" }
            guard count(of: itemID) > 0 else { return "没有\(item.name)了" }
            p.hunger = (p.hunger + item.hunger).clamped
            p.energy = (p.energy + item.energy).clamped
            p.health = (p.health + item.health).clamped
            if item.cures, p.isSick {
                p.isSick = false
                p.sickMeter = 0
                counters.cures += 1
                log("💊", "\(p.name)吃了药，病好啦")
            }
            pet = p
            consume(itemID)
            addXP(item.xp)
            return say(item.cures ? "苦苦的…但是好多了！" : "\(item.emoji) 感觉充满力量！")
        case .accessory:
            return equip(itemID)
        case .theme:
            return setTheme(itemID)
        }
    }

    mutating func setTheme(_ id: String) -> String {
        guard ownedThemes.contains(id) else { return "还没有这个主题" }
        theme = id
        return "已切换主题：\(PetTheme.find(id).name)"
    }

    mutating func equip(_ accessoryID: String?) -> String {
        guard var p = pet else { return "还没有宠物" }
        if let accessoryID, p.accessory != accessoryID {
            guard ownedAccessories.contains(accessoryID), let item = ShopItem.find(accessoryID) else { return "还没有这件装扮" }
            p.accessory = accessoryID
            pet = p
            return say("\(item.emoji) 好看吗？")
        }
        p.accessory = nil
        pet = p
        return say("摘下来啦")
    }

    // MARK: - 商店 / 签到 / 游戏

    mutating func buy(_ itemID: String) -> String {
        guard let item = ShopItem.find(itemID), item.price > 0 else { return "这个不用买" }
        if item.minLevel > (pet?.level ?? 1) { return "需要宠物达到 \(item.minLevel) 级" }
        if !item.category.isConsumable, owns(item) { return "已经拥有了" }
        guard coins >= item.price else { return "金币不够，去玩小游戏赚一点吧" }
        coins -= item.price
        switch item.category {
        case .accessory: ownedAccessories.insert(item.id)
        case .theme: ownedThemes.insert(item.id)
        default: inventory[item.id, default: 0] += 1
        }
        return "购买了 \(item.emoji)\(item.name)"
    }

    mutating func checkIn(now: Date = .now) -> String {
        guard !hasCheckedInToday else { return "今天已经签到过啦" }
        let cal = Calendar.current
        if let last = counters.lastCheckIn,
           let yesterday = cal.date(byAdding: .day, value: -1, to: now),
           cal.isDate(last, inSameDayAs: yesterday) {
            counters.checkInStreak += 1
        } else {
            counters.checkInStreak = 1
        }
        counters.lastCheckIn = now
        let reward = 20 + min(counters.checkInStreak, 7) * 5
        coins += reward
        log("📅", "签到第 \(counters.checkInStreak) 天，获得 \(reward) 金币")
        if wish == nil, counters.checkInStreak % 3 == 0 { makeWish(now: now) }
        return "签到成功！连续 \(counters.checkInStreak) 天 +\(reward) 金币"
    }

    /// 能否开始小游戏，不能则返回原因
    var gameBlocker: String? {
        guard let p = pet else { return "还没有宠物" }
        if p.isEgg { return "等宠物孵化后才能玩" }
        if p.isSleeping { return "宠物在睡觉" }
        if p.isSick { return "宠物生病了，先治病吧" }
        if p.energy < 12 { return "宠物太累了，让它睡一会儿" }
        return nil
    }

    mutating func finishGame(_ game: GameKind, score: Int, coinsEarned: Int) -> String {
        guard var p = pet else { return "" }
        coins += coinsEarned
        p.happiness = (p.happiness + 12).clamped
        p.energy = (p.energy - 10).clamped
        p.hunger = (p.hunger - 5).clamped
        pet = p
        counters.gamesPlayed += 1
        counters.playedGames.insert(game.rawValue)
        let key = game.rawValue
        if score > counters.highScores[key, default: 0] {
            counters.highScores[key] = score
        }
        addXP(8 + coinsEarned / 5)
        log(game.emoji, "和\(p.name)玩了\(game.name)，赚到 \(coinsEarned) 金币")
        return say("和你一起玩最开心了！")
    }

    // MARK: - 成长 / 成就

    mutating func addXP(_ amount: Int) {
        guard var p = pet, !p.isEgg, amount > 0 else { return }
        let oldStage = p.stage
        p.xp += amount
        while p.xp >= p.xpToNext {
            p.xp -= p.xpToNext
            p.level += 1
            coins += p.level * 10
            log("⭐️", "\(p.name)升到了 \(p.level) 级！获得 \(p.level * 10) 金币")
        }
        if p.stage != oldStage {
            log("✨", "\(p.name)进入了\(p.stage == .adult ? p.formTitle : p.stage.name)！")
        }
        pet = p
    }

    /// 返回本次新解锁的成就
    @discardableResult
    mutating func checkAchievements() -> [Achievement] {
        let fresh = Achievement.allCases.filter { !achievements.contains($0.rawValue) && $0.isMet(self) }
        for a in fresh {
            achievements.insert(a.rawValue)
            coins += a.reward
            log("🏆", "解锁成就「\(a.title)」+\(a.reward) 金币")
        }
        return fresh
    }

    // MARK: - 工具

    mutating func adopt(name: String, species: Species, now: Date = .now) {
        pet = Pet(name: name, species: species, now: now)
        wish = nil
        lastEventRoll = nil
        log("🥚", "领养了一颗\(species.name)蛋，取名「\(name)」")
        say("(蛋蛋在轻轻晃动)")
    }

    private mutating func consume(_ itemID: String) {
        guard itemID != ShopItem.freeSnackID else { return }
        let left = inventory[itemID, default: 0] - 1
        inventory[itemID] = left > 0 ? left : nil
    }

    mutating func log(_ emoji: String, _ text: String, now: Date = .now) {
        diary.insert(DiaryEntry(date: now, emoji: emoji, text: text), at: 0)
        if diary.count > 200 { diary.removeLast(diary.count - 200) }
    }

    @discardableResult
    mutating func say(_ line: String, now: Date = .now) -> String {
        lastMessage = line
        lastMessageAt = now
        return line
    }

    /// 气泡文字：最近 2 分钟内的互动反馈优先，否则随机说一句符合心情的话
    func speech(now: Date = .now) -> String {
        if let lastMessage, let at = lastMessageAt, now.timeIntervalSince(at) < 120 {
            return lastMessage
        }
        guard let pet else { return "" }
        if let wish, let item = ShopItem.find(wish.itemID), !pet.isSleeping {
            return "想吃\(item.emoji)\(item.name)！"
        }
        return pet.mood(at: now).lines.randomElement() ?? ""
    }
}

extension Double {
    var clamped: Double { Swift.min(100, Swift.max(0, self)) }
}
