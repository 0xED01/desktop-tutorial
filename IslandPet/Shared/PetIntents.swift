import AppIntents

// 灵动岛展开后的按钮。LiveActivityIntent 由系统在 App 进程中执行（App 未运行时会在后台拉起），
// 因此可以直接修改 PetStore 并刷新灵动岛。

struct FeedPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "喂宠物"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.quickFeed() }
        return .result()
    }
}

struct PlayPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "陪宠物玩"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.play() }
        return .result()
    }
}

struct PatPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "摸摸宠物"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.pat() }
        return .result()
    }
}

struct CleanPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "给宠物清洁"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.clean() }
        return .result()
    }
}

struct SleepPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "哄睡 / 叫醒宠物"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.toggleSleep() }
        return .result()
    }
}

struct HealPetIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "给宠物喂药"
    init() {}

    @MainActor
    func perform() async throws -> some IntentResult {
        await PetStore.shared.actFromIsland { $0.heal() }
        return .result()
    }
}
