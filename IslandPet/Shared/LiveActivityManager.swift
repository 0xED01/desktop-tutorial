import ActivityKit
import Foundation

/// 负责开启 / 更新 / 关闭灵动岛上的宠物
@MainActor
enum LiveActivityManager {
    private static let enabledKey = "islandpet.liveActivityEnabled"
    private static var pose = 0
    private static var lastAlertMood: Mood?

    static var userEnabled: Bool {
        get { UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    static var systemEnabled: Bool { ActivityAuthorizationInfo().areActivitiesEnabled }

    static var current: Activity<PetActivityAttributes>? {
        Activity<PetActivityAttributes>.activities.first { $0.activityState == .active || $0.activityState == .stale }
    }

    static var isRunning: Bool { current != nil }

    /// 只能在 App 处于前台时调用（系统限制）
    static func start(with state: GameState) async throws {
        guard let pet = state.pet else { return }
        await endAll()
        pose += 1
        guard let content = PetActivityAttributes.ContentState(state, pose: pose) else { return }
        _ = try Activity.request(
            attributes: PetActivityAttributes(petName: pet.name),
            content: ActivityContent(state: content, staleDate: Date().addingTimeInterval(6 * 3600))
        )
    }

    /// 把最新状态推送到灵动岛。宠物刚变饿 / 生病 / 拉便便时会让灵动岛自动展开提醒一下。
    static func sync(with state: GameState, foreground: Bool = false) async {
        guard userEnabled else { return }
        guard let pet = state.pet else {
            await endAll()
            return
        }
        guard let activity = current else {
            // 灵动岛最长只能持续 8 小时，App 回到前台时自动续上
            if foreground, systemEnabled { try? await start(with: state) }
            return
        }
        if activity.attributes.petName != pet.name {
            if foreground { try? await start(with: state) }
            return
        }

        pose += 1
        guard let content = PetActivityAttributes.ContentState(state, pose: pose) else { return }
        let activityContent = ActivityContent(state: content, staleDate: Date().addingTimeInterval(6 * 3600))

        let urgent: Set<Mood> = [.hungry, .sick, .dirty, .sad]
        if urgent.contains(content.mood), content.mood != lastAlertMood, !foreground {
            lastAlertMood = content.mood
            let alert = AlertConfiguration(
                title: "\(pet.name)\(content.mood.title)",
                body: "\(content.message)",
                sound: .default
            )
            await activity.update(activityContent, alertConfiguration: alert)
        } else {
            if !urgent.contains(content.mood) { lastAlertMood = nil }
            await activity.update(activityContent)
        }
    }

    static func endAll() async {
        for activity in Activity<PetActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}
