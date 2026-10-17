import SwiftUI

/// 反应力挑战：等宠物跳起来的瞬间点击，共 5 轮，取平均反应时间
struct ReactionGameView: View {
    @EnvironmentObject var store: PetStore
    @Environment(\.dismiss) private var dismiss

    enum Phase { case intro, waiting, go, tooEarly, result, done }

    @State private var phase: Phase = .intro
    @State private var round = 0
    @State private var times: [Int] = []
    @State private var goAt = Date()
    @State private var token = UUID()

    private let rounds = 5

    private var average: Int { times.isEmpty ? 0 : times.reduce(0, +) / times.count }
    /// 平均 250ms 以内拿满 60 金币
    private var coins: Int { max(8, min(60, 60 - (average - 250) / 6)) }

    var body: some View {
        ZStack {
            (phase == .go ? Color.green.opacity(0.35) : (phase == .tooEarly ? Color.red.opacity(0.35) : Color.clear))
                .background(ThemedBackground())
                .ignoresSafeArea()

            VStack(spacing: 24) {
                Text("第 \(min(round + 1, rounds))/\(rounds) 轮").font(.headline).foregroundStyle(.secondary)
                if let pet = store.state.pet {
                    PetAvatarView(species: pet.species, stage: pet.stage, mood: phase == .go ? .happy : .normal,
                                  accessory: ShopItem.find(pet.accessory)?.emoji, size: 160)
                        .offset(y: phase == .go ? -50 : 0)
                        .animation(.spring(response: 0.15, dampingFraction: 0.5), value: phase)
                }
                Text(prompt).font(.system(.title2, design: .rounded).weight(.bold)).multilineTextAlignment(.center)
                if !times.isEmpty {
                    Text(times.map { "\($0)ms" }.joined(separator: "  ")).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
            }

            if phase == .done {
                GameResultView(title: "平均 \(average) ms", detail: rating, coins: coins,
                               onAgain: restart, onExit: { dismiss() })
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: tap)
        .navigationTitle("反应力挑战")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var prompt: String {
        switch phase {
        case .intro: "点击屏幕开始"
        case .waiting: "等它跳起来……"
        case .go: "就是现在！"
        case .tooEarly: "太早啦！点击重试本轮"
        case .result: "\(times.last ?? 0) ms！点击继续"
        case .done: ""
        }
    }

    private var rating: String {
        switch average {
        case ..<230: "闪电般的反应！⚡️"
        case ..<300: "很厉害！"
        case ..<380: "还不错～"
        default: "宠物都比你快哦 😝"
        }
    }

    private func tap() {
        switch phase {
        case .intro, .tooEarly, .result:
            startRound()
        case .waiting:
            token = UUID()
            phase = .tooEarly
        case .go:
            times.append(Int(Date().timeIntervalSince(goAt) * 1000))
            round += 1
            if round >= rounds {
                phase = .done
                store.act { $0.finishGame(.reaction, score: max(0, 1000 - average), coinsEarned: coins) }
            } else {
                phase = .result
            }
        case .done:
            break
        }
    }

    private func startRound() {
        phase = .waiting
        let t = UUID()
        token = t
        DispatchQueue.main.asyncAfter(deadline: .now() + .random(in: 1.2...3.8)) {
            guard token == t, phase == .waiting else { return }
            goAt = Date()
            phase = .go
        }
    }

    private func restart() {
        times = []
        round = 0
        phase = .intro
    }
}
