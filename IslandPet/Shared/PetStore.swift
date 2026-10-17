import Combine
import Foundation

/// 全局唯一的存档。App 界面和灵动岛按钮（App Intent）都通过它读写，
/// 二者运行在同一个 App 进程里，所以用普通 UserDefaults 即可。
@MainActor
final class PetStore: ObservableObject {
    static let shared = PetStore()

    @Published private(set) var state: GameState
    @Published var toast: String?

    /// App 侧注入的回调（例如重新安排本地通知）
    var onSave: ((GameState) -> Void)?

    private let key = "islandpet.state.v1"
    private var toastTask: Task<Void, Never>?

    init() {
        if let data = UserDefaults.standard.data(forKey: key),
           let saved = try? JSONDecoder().decode(GameState.self, from: data) {
            state = saved
        } else {
            state = GameState()
        }
        state.tick()
    }

    /// App 内的操作：修改存档 → 弹提示 → 异步刷新灵动岛
    @discardableResult
    func act(_ change: (inout GameState) -> String?) -> String? {
        let message = apply(change)
        if let message, !message.isEmpty { show(message) }
        Task { await LiveActivityManager.sync(with: state, foreground: true) }
        return message
    }

    /// 灵动岛按钮的操作：等灵动岛刷新完成后再返回
    func actFromIsland(_ change: (inout GameState) -> String?) async {
        apply(change)
        await LiveActivityManager.sync(with: state)
    }

    func refresh(foreground: Bool) async {
        apply { _ in nil }
        await LiveActivityManager.sync(with: state, foreground: foreground)
    }

    func reset() {
        state = GameState()
        save()
        Task { await LiveActivityManager.endAll() }
    }

    func show(_ message: String) {
        toast = message
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(for: .seconds(2.2))
            if !Task.isCancelled { toast = nil }
        }
    }

    @discardableResult
    private func apply(_ change: (inout GameState) -> String?) -> String? {
        var s = state
        s.tick()
        let message = change(&s)
        s.tick()
        let unlocked = s.checkAchievements()
        state = s
        save()
        if let first = unlocked.first {
            show("🏆 解锁成就「\(first.title)」+\(first.reward) 金币")
            return nil
        }
        return message
    }

    private func save() {
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: key)
        }
        onSave?(state)
    }
}
