import BackgroundTasks
import SwiftUI

@main
struct IslandPetApp: App {
    @StateObject private var store = PetStore.shared
    @Environment(\.scenePhase) private var scenePhase

    init() {
        PetStore.shared.onSave = { NotificationManager.reschedule(for: $0) }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .preferredColorScheme(.dark)
                .onOpenURL { _ in Task { await store.refresh(foreground: true) } }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                Task { await store.refresh(foreground: true) }
            case .background:
                BackgroundRefresh.schedule()
            default:
                break
            }
        }
        .backgroundTask(.appRefresh(BackgroundRefresh.identifier)) {
            await BackgroundRefresh.run()
        }
    }
}

/// 后台定时刷新：让灵动岛上的属性条和表情在不打开 App 的情况下也会变化。
/// 系统会根据使用习惯决定实际刷新频率（通常 15 分钟 ~ 数小时一次）。
enum BackgroundRefresh {
    static let identifier = "com.islandpet.refresh"

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date().addingTimeInterval(15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func run() async {
        schedule()
        await PetStore.shared.refresh(foreground: false)
    }
}

struct RootView: View {
    @EnvironmentObject var store: PetStore

    var body: some View {
        ZStack(alignment: .top) {
            if store.state.pet == nil {
                AdoptView()
            } else {
                MainTabView()
            }
            if let toast = store.toast {
                Text(toast)
                    .font(.system(.subheadline, design: .rounded).weight(.semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.ultraThinMaterial, in: Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .id(toast)
            }
        }
        .animation(.spring(duration: 0.35), value: store.toast)
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("家", systemImage: "house.fill") }
            GamesView()
                .tabItem { Label("游乐场", systemImage: "gamecontroller.fill") }
            ShopView()
                .tabItem { Label("商店", systemImage: "bag.fill") }
            BagView()
                .tabItem { Label("背包", systemImage: "shippingbox.fill") }
            ProfileView()
                .tabItem { Label("档案", systemImage: "book.closed.fill") }
        }
    }
}
