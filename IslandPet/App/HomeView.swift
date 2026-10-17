import SwiftUI

struct HomeView: View {
    @EnvironmentObject var store: PetStore
    @State private var hop = false
    @State private var showFood = false
    @State private var showToys = false
    @State private var islandOn = false
    @State private var hearts: [UUID] = []

    private var state: GameState { store.state }

    var body: some View {
        NavigationStack {
            ZStack {
                ThemedBackground()
                if let pet = state.pet {
                    ScrollView {
                        VStack(spacing: 16) {
                            petStage(pet)
                            statsCard(pet)
                            actions(pet)
                            if let wish = state.wish, let item = ShopItem.find(wish.itemID) {
                                wishCard(item, wish: wish)
                            }
                            if !state.hasCheckedInToday { checkInCard }
                            islandCard
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle(state.pet?.name ?? "")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { CoinBadge(coins: state.coins) }
                ToolbarItem(placement: .topBarLeading) {
                    if let pet = state.pet {
                        Text("Lv.\(pet.level) · \(pet.formTitle)").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                    }
                }
            }
            .sheet(isPresented: $showFood) { ItemPickerSheet(category: .food) }
            .sheet(isPresented: $showToys) { ItemPickerSheet(category: .toy) }
            .task {
                // App 打开时每 30 秒结算一次，让界面上的属性缓慢变化
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(30))
                    await store.refresh(foreground: true)
                    islandOn = LiveActivityManager.isRunning
                }
            }
            .onAppear { islandOn = LiveActivityManager.isRunning }
        }
    }

    // MARK: 宠物舞台

    private func petStage(_ pet: Pet) -> some View {
        let mood = pet.mood()
        return VStack(spacing: 10) {
            Text(state.speech())
                .font(.system(.callout, design: .rounded))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(.white.opacity(0.14), in: Capsule())
                .id(state.lastMessageAt)
                .transition(.scale.combined(with: .opacity))

            ZStack {
                Ellipse().fill(.black.opacity(0.25)).frame(width: 130, height: 22).offset(y: 92)
                PetAvatarView(species: pet.species, stage: pet.stage, mood: mood,
                              accessory: ShopItem.find(pet.accessory)?.emoji, size: 190,
                              pose: hop ? 1 : 0, eggCracks: pet.eggTaps)
                    .offset(y: hop && mood != .sleeping ? -8 : 0)
                    .animation(.easeInOut(duration: mood == .sleeping ? 2 : 0.7).repeatForever(autoreverses: true), value: hop)
                    .onAppear { hop = true }
                    .onTapGesture {
                        store.act { $0.pat() }
                        spawnHeart()
                    }
                    .sensoryFeedback(.impact(weight: .light), trigger: state.counters.pats + pet.eggTaps)

                ForEach(hearts, id: \.self) { _ in
                    FloatingHeart()
                }

                // 便便，点击清理
                HStack(spacing: 18) {
                    ForEach(0..<pet.poops, id: \.self) { _ in
                        Text("💩").font(.system(size: 30))
                            .onTapGesture { store.act { $0.clean() } }
                    }
                }
                .offset(y: 88)
            }
            .frame(height: 220)

            Text(pet.isEgg ? "点击蛋蛋帮它孵化（\(pet.eggTaps)/6）" : "\(mood.emoji) \(mood.title) · \(pet.species.name) · 第 \(pet.ageDays + 1) 天")
                .font(.footnote)
                .foregroundStyle(.secondary)

            VStack(spacing: 4) {
                ProgressView(value: pet.isEgg ? 0 : pet.xpProgress)
                    .tint(.yellow)
                Text(pet.isEgg ? "孵化后开始成长" : "经验 \(pet.xp)/\(pet.xpToNext) · \(pet.stage.name)")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 40)
        }
        .animation(.spring(duration: 0.3), value: state.lastMessageAt)
    }

    private func spawnHeart() {
        let id = UUID()
        hearts.append(id)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { hearts.removeAll { $0 == id } }
    }

    // MARK: 属性

    private func statsCard(_ pet: Pet) -> some View {
        Card {
            VStack(spacing: 10) {
                StatBar(icon: "🍖", title: "饱腹", value: pet.hunger, color: .orange)
                StatBar(icon: "💗", title: "心情", value: pet.happiness, color: .pink)
                StatBar(icon: "⚡️", title: "精力", value: pet.energy, color: .yellow)
                StatBar(icon: "🫧", title: "清洁", value: pet.cleanliness, color: .cyan)
                StatBar(icon: "❤️‍🩹", title: "健康", value: pet.health, color: .red)
            }
        }
    }

    // MARK: 互动

    private func actions(_ pet: Pet) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
            ActionTile(emoji: "🍖", title: "喂食") { showFood = true }
            ActionTile(emoji: "🎾", title: "玩耍") { showToys = true }
            ActionTile(emoji: "🤲", title: "摸摸") { store.act { $0.pat() }; spawnHeart() }
            ActionTile(emoji: "🛁", title: "洗澡") { store.act { $0.clean() } }
            ActionTile(emoji: pet.isSleeping ? "☀️" : "🌙", title: pet.isSleeping ? "叫醒" : "睡觉") { store.act { $0.toggleSleep() } }
            ActionTile(emoji: "💊", title: "吃药", highlight: pet.isSick) { store.act { $0.heal() } }
        }
    }

    private func wishCard(_ item: ShopItem, wish: Wish) -> some View {
        Card {
            HStack {
                Text("💭").font(.largeTitle)
                VStack(alignment: .leading, spacing: 2) {
                    Text("心愿：想吃\(item.emoji)\(item.name)").font(.headline)
                    HStack(spacing: 0) {
                        Text("满足心愿可得 30 金币，剩余 ")
                        Text(wish.expires, style: .relative)
                    }
                    .font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if state.count(of: item.id) > 0 {
                    Button("喂它") { store.act { $0.feed(item.id) } }.buttonStyle(.borderedProminent).tint(.pink)
                } else {
                    Button("购买") { store.act { $0.buy(item.id) } }.buttonStyle(.bordered)
                }
            }
        }
    }

    private var checkInCard: some View {
        Card {
            HStack {
                Text("📅").font(.largeTitle)
                VStack(alignment: .leading, spacing: 2) {
                    Text("每日签到").font(.headline)
                    Text("连续签到奖励越来越多，已连续 \(state.counters.checkInStreak) 天").font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Button("签到") { store.act { $0.checkIn() } }.buttonStyle(.borderedProminent).tint(.orange)
            }
        }
    }

    private var islandCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                Toggle(isOn: Binding(get: { islandOn }, set: { islandOn = $0; toggleIsland($0) })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("在灵动岛显示").font(.headline)
                        Text("长按灵动岛可以直接喂食、玩耍、摸摸").font(.caption).foregroundStyle(.secondary)
                    }
                }
                if !LiveActivityManager.systemEnabled {
                    Text("⚠️ 系统已关闭实时活动，请到 设置 › 灵动岛宠物 › 实时活动 中开启")
                        .font(.caption).foregroundStyle(.orange)
                }
            }
        }
    }

    private func toggleIsland(_ on: Bool) {
        LiveActivityManager.userEnabled = on
        Task {
            if on {
                do {
                    try await LiveActivityManager.start(with: store.state)
                    store.show("宠物住进灵动岛啦 🏝️")
                } catch {
                    store.show("开启失败：\(error.localizedDescription)")
                    islandOn = false
                }
            } else {
                await LiveActivityManager.endAll()
            }
        }
    }
}

struct ActionTile: View {
    var emoji: String
    var title: String
    var highlight = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Text(emoji).font(.system(size: 30))
                Text(title).font(.system(.subheadline, design: .rounded).weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(highlight ? Color.red.opacity(0.35) : .white.opacity(0.08)))
        }
        .buttonStyle(.plain)
    }
}

struct FloatingHeart: View {
    @State private var up = false
    private let dx = CGFloat.random(in: -50...50)

    var body: some View {
        Text("💗")
            .font(.title)
            .offset(x: dx, y: up ? -120 : -30)
            .opacity(up ? 0 : 1)
            .onAppear { withAnimation(.easeOut(duration: 1.1)) { up = true } }
    }
}

/// 选择食物 / 玩具的弹窗
struct ItemPickerSheet: View {
    @EnvironmentObject var store: PetStore
    @Environment(\.dismiss) private var dismiss
    var category: ItemCategory

    private var items: [ShopItem] {
        ShopItem.catalog.filter { $0.category == category && store.state.count(of: $0.id) > 0 }
    }

    var body: some View {
        NavigationStack {
            List {
                if category == .toy {
                    Button {
                        store.act { $0.play() }
                        dismiss()
                    } label: {
                        row(emoji: "🤸", name: "徒手陪玩", detail: "心情+15 · 精力-10", count: nil)
                    }
                }
                ForEach(items) { item in
                    Button {
                        store.act { $0.use(item.id) }
                        dismiss()
                    } label: {
                        row(emoji: item.emoji, name: item.name, detail: item.effectText,
                            count: item.id == ShopItem.freeSnackID ? nil : store.state.count(of: item.id),
                            favorite: item.id == store.state.pet?.species.favoriteFood)
                    }
                }
                if items.isEmpty && category != .toy {
                    Text("背包里没有\(category.name)，去商店看看吧").foregroundStyle(.secondary)
                }
            }
            .navigationTitle(category == .food ? "喂点什么？" : "玩点什么？")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("关闭") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(emoji: String, name: String, detail: String, count: Int?, favorite: Bool = false) -> some View {
        HStack {
            Text(emoji).font(.largeTitle)
            VStack(alignment: .leading) {
                HStack {
                    Text(name).font(.headline)
                    if favorite { Text("最爱").font(.caption2.bold()).padding(.horizontal, 6).background(.pink.opacity(0.4), in: Capsule()) }
                }
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if let count { Text("×\(count)").foregroundStyle(.secondary) }
        }
        .contentShape(Rectangle())
    }
}
