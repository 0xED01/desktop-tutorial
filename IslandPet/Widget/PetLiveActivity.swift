import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

@main
struct IslandPetWidgetBundle: WidgetBundle {
    var body: some Widget {
        PetLiveActivity()
    }
}

struct PetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PetActivityAttributes.self) { context in
            LockScreenPetView(name: context.attributes.petName, state: context.state)
                .activityBackgroundTint(context.state.theme.bottom.opacity(0.85))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            let state = context.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    VStack(spacing: 2) {
                        avatar(state, size: 54)
                        Text("Lv.\(state.level)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(state.theme.accent)
                    }
                    .padding(.leading, 4)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 4) {
                        Label("\(state.coins)", systemImage: "circle.hexagongrid.fill")
                            .font(.system(size: 12, weight: .semibold, design: .rounded))
                            .foregroundStyle(.yellow)
                            .contentTransition(.numericText())
                        HStack(spacing: 2) {
                            if let wish = state.wishEmoji { Text("💭\(wish)") }
                            if state.poops > 0 { Text(String(repeating: "💩", count: state.poops)) }
                        }
                        .font(.system(size: 13))
                    }
                    .padding(.trailing, 4)
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 3) {
                        HStack(spacing: 4) {
                            Text(context.attributes.petName).font(.system(size: 15, weight: .bold, design: .rounded))
                            Text(state.mood.emoji).font(.system(size: 13))
                        }
                        Text(state.message)
                            .font(.system(size: 12, design: .rounded))
                            .foregroundStyle(.white.opacity(0.85))
                            .lineLimit(1)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(.white.opacity(0.12), in: Capsule())
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        StatsRow(state: state, compact: true)
                        ActionButtons(state: state)
                    }
                    .padding(.horizontal, 4)
                }
            } compactLeading: {
                avatar(state, size: 24)
            } compactTrailing: {
                ZStack {
                    if state.stage != .egg && !state.isSleeping {
                        ProgressView(timerInterval: state.hungerRange, countsDown: true, label: { EmptyView() }, currentValueLabel: { EmptyView() })
                            .progressViewStyle(.circular)
                            .tint(hungerTint(state))
                    }
                    Text(state.alertEmoji).font(.system(size: 11))
                }
                .frame(width: 24, height: 24)
            } minimal: {
                avatar(state, size: 22)
            }
            .keylineTint(state.theme.accent)
            .widgetURL(URL(string: "islandpet://home"))
        }
    }

    private func avatar(_ state: PetActivityAttributes.ContentState, size: CGFloat) -> some View {
        PetAvatarView(species: state.species, stage: state.stage, mood: state.mood,
                      accessory: state.accessoryEmoji, size: size, pose: state.pose)
    }
}

func hungerTint(_ state: PetActivityAttributes.ContentState) -> Color {
    state.hunger < 25 ? .red : (state.hunger < 50 ? .orange : .green)
}

// MARK: - 属性条

struct StatsRow: View {
    var state: PetActivityAttributes.ContentState
    var compact = false

    var body: some View {
        HStack(spacing: compact ? 8 : 10) {
            StatPill(icon: "🍖", value: state.hunger, color: .orange)
            StatPill(icon: "💗", value: state.happiness, color: .pink)
            StatPill(icon: "⚡️", value: state.energy, color: .yellow)
            StatPill(icon: "🫧", value: state.cleanliness, color: .cyan)
            if !compact {
                StatPill(icon: "❤️‍🩹", value: state.health, color: .red)
            }
        }
    }
}

struct StatPill: View {
    var icon: String
    var value: Int
    var color: Color

    var body: some View {
        HStack(spacing: 3) {
            Text(icon).font(.system(size: 10))
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.15))
                    Capsule().fill(value < 25 ? .red : color)
                        .frame(width: max(3, geo.size.width * CGFloat(value) / 100))
                }
            }
            .frame(height: 5)
        }
    }
}

// MARK: - 互动按钮

struct ActionButtons: View {
    var state: PetActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: 6) {
            if state.stage == .egg {
                IslandButton(intent: PatPetIntent(), emoji: "👆", title: "敲敲蛋", color: .orange)
            } else if state.isSleeping {
                IslandButton(intent: PatPetIntent(), emoji: "🤲", title: "摸摸", color: .pink)
                IslandButton(intent: SleepPetIntent(), emoji: "☀️", title: "叫醒", color: .yellow)
            } else {
                IslandButton(intent: FeedPetIntent(), emoji: "🍖", title: "喂食", color: .orange)
                if state.isSick {
                    IslandButton(intent: HealPetIntent(), emoji: "💊", title: "吃药", color: .red)
                } else {
                    IslandButton(intent: PlayPetIntent(), emoji: "🎾", title: "玩耍", color: .green)
                }
                IslandButton(intent: PatPetIntent(), emoji: "🤲", title: "摸摸", color: .pink)
                if state.poops > 0 || state.cleanliness < 50 {
                    IslandButton(intent: CleanPetIntent(), emoji: "🧼", title: "清洁", color: .cyan)
                } else {
                    IslandButton(intent: SleepPetIntent(), emoji: "🌙", title: "睡觉", color: .indigo)
                }
            }
        }
    }
}

struct IslandButton<I: AppIntent>: View {
    var intent: I
    var emoji: String
    var title: String
    var color: Color

    var body: some View {
        Button(intent: intent) {
            HStack(spacing: 3) {
                Text(emoji).font(.system(size: 13))
                Text(title).font(.system(size: 12, weight: .semibold, design: .rounded))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 7)
            .background(color.opacity(0.35), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 锁屏 / 通知中心

struct LockScreenPetView: View {
    var name: String
    var state: PetActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                PetAvatarView(species: state.species, stage: state.stage, mood: state.mood,
                              accessory: state.accessoryEmoji, size: 64, pose: state.pose)
                    .background(Circle().fill(.white.opacity(0.08)))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(name).font(.system(size: 17, weight: .bold, design: .rounded))
                        Text("Lv.\(state.level)")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(state.theme.accent.opacity(0.3), in: Capsule())
                        Spacer()
                        Text("🪙 \(state.coins)").font(.system(size: 12, weight: .semibold, design: .rounded))
                    }
                    Text("\(state.mood.emoji) \(state.message)")
                        .font(.system(size: 13, design: .rounded))
                        .foregroundStyle(.white.opacity(0.85))
                        .lineLimit(1)
                    statusLine
                }
            }
            StatsRow(state: state)
            ActionButtons(state: state)
        }
        .padding(14)
        .foregroundStyle(.white)
        .background(state.theme.gradient)
    }

    @ViewBuilder
    private var statusLine: some View {
        if state.stage == .egg {
            Text("轻敲蛋壳可以帮它更快孵化").font(.system(size: 11)).foregroundStyle(.white.opacity(0.7))
        } else if state.isSleeping, let wake = state.wakeAt, wake > .now {
            HStack(spacing: 4) {
                Text("预计睡饱还要").font(.system(size: 11))
                Text(timerInterval: Date.now...wake, countsDown: true)
                    .font(.system(size: 11, weight: .semibold).monospacedDigit())
                    .frame(maxWidth: 70, alignment: .leading)
            }
            .foregroundStyle(.white.opacity(0.7))
        } else {
            HStack(spacing: 4) {
                Text("距离饿肚子").font(.system(size: 11))
                Text(timerInterval: state.hungerRange, countsDown: true)
                    .font(.system(size: 11, weight: .semibold).monospacedDigit())
                    .foregroundStyle(hungerTint(state))
                    .frame(maxWidth: 70, alignment: .leading)
            }
            .foregroundStyle(.white.opacity(0.7))
        }
    }
}
