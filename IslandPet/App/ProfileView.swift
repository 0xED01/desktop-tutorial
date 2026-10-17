import SwiftUI

struct ProfileView: View {
    @EnvironmentObject var store: PetStore
    @State private var page = 0
    @State private var renaming = false
    @State private var newName = ""
    @State private var confirmReset = false
    @State private var notifications = NotificationManager.enabled

    private var state: GameState { store.state }

    var body: some View {
        NavigationStack {
            ZStack {
                ThemedBackground()
                ScrollView {
                    VStack(spacing: 14) {
                        if let pet = state.pet { petCard(pet) }
                        Picker("", selection: $page) {
                            Text("成就").tag(0)
                            Text("日记").tag(1)
                            Text("设置").tag(2)
                        }
                        .pickerStyle(.segmented)

                        switch page {
                        case 0: achievements
                        case 1: diary
                        default: settings
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("档案")
            .alert("给宠物改名", isPresented: $renaming) {
                TextField("新名字", text: $newName)
                Button("确定") {
                    let name = String(newName.trimmingCharacters(in: .whitespaces).prefix(8))
                    guard !name.isEmpty else { return }
                    store.act { s in
                        s.pet?.name = name
                        return "以后就叫「\(name)」啦"
                    }
                }
                Button("取消", role: .cancel) {}
            } message: {
                Text("灵动岛会在下次打开 App 时同步新名字")
            }
            .confirmationDialog("确定要送走当前宠物、重新开始吗？金币和道具也会清空。", isPresented: $confirmReset, titleVisibility: .visible) {
                Button("重新开始", role: .destructive) { store.reset() }
            }
        }
    }

    private func petCard(_ pet: Pet) -> some View {
        Card {
            HStack(spacing: 14) {
                PetAvatarView(species: pet.species, stage: pet.stage, mood: pet.mood(),
                              accessory: ShopItem.find(pet.accessory)?.emoji, size: 80, eggCracks: pet.eggTaps)
                VStack(alignment: .leading, spacing: 4) {
                    Text(pet.name).font(.system(.title2, design: .rounded).weight(.bold))
                    Text("\(pet.species.name) · Lv.\(pet.level) · \(pet.formTitle)").font(.subheadline)
                    Text("出生于 \(pet.birthday.formatted(date: .abbreviated, time: .omitted))，已陪伴 \(pet.ageDays + 1) 天")
                        .font(.caption).foregroundStyle(.secondary)
                    Text("照顾评分 \(Int(pet.careScore)) · 喂食 \(state.counters.feeds) · 玩耍 \(state.counters.plays) · 摸摸 \(state.counters.pats)")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var achievements: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            ForEach(Achievement.allCases) { a in
                let done = state.achievements.contains(a.rawValue)
                VStack(spacing: 4) {
                    Text(a.emoji).font(.system(size: 34)).grayscale(done ? 0 : 1).opacity(done ? 1 : 0.4)
                    Text(a.title).font(.system(.subheadline, design: .rounded).weight(.bold))
                    Text(a.detail).font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Text(done ? "已达成" : "奖励 🪙\(a.reward)").font(.caption2.bold()).foregroundStyle(done ? .green : .yellow)
                }
                .frame(maxWidth: .infinity)
                .padding(10)
                .background(.white.opacity(done ? 0.12 : 0.05), in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    private var diary: some View {
        LazyVStack(alignment: .leading, spacing: 10) {
            if state.diary.isEmpty {
                Text("还没有日记").foregroundStyle(.secondary)
            }
            ForEach(state.diary.prefix(100)) { entry in
                HStack(alignment: .top, spacing: 10) {
                    Text(entry.emoji)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.text).font(.subheadline)
                        Text(entry.date.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(14)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18))
    }

    private var settings: some View {
        VStack(spacing: 12) {
            Card {
                Toggle("提醒通知（饿了、睡醒、心愿）", isOn: Binding(get: { notifications }, set: { on in
                    notifications = on
                    Task {
                        if on {
                            notifications = await NotificationManager.requestPermission()
                        } else {
                            NotificationManager.enabled = false
                        }
                        NotificationManager.reschedule(for: store.state)
                    }
                }))
            }
            Card {
                Button("给宠物改名") {
                    newName = state.pet?.name ?? ""
                    renaming = true
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 6) {
                    Text("玩法小贴士").font(.headline)
                    Text("""
                    • 属性会随真实时间变化，睡觉时消耗变慢、精力恢复
                    • 便便放着不管、太脏或太饿，宠物会生病
                    • 宠物偶尔会许愿，满足心愿有额外奖励
                    • 成年后的形态取决于你平时的照顾评分
                    • 灵动岛每次显示最长 8 小时，打开 App 会自动续上
                    """)
                    .font(.caption).foregroundStyle(.secondary)
                }
            }
            Button("送走宠物，重新开始", role: .destructive) { confirmReset = true }
                .padding(.top, 8)
        }
    }
}
