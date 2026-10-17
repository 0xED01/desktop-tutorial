import SwiftUI

/// 猜拳大战：五局三胜。每种宠物都有偏爱的手势，细心观察就能赢。
struct RPSGameView: View {
    @EnvironmentObject var store: PetStore
    @Environment(\.dismiss) private var dismiss

    private let hands = ["✊", "✌️", "🖐️"]
    private let names = ["石头", "剪刀", "布"]

    @State private var mine: Int?
    @State private var theirs: Int?
    @State private var wins = 0
    @State private var losses = 0
    @State private var log: [String] = []

    private var over: Bool { wins >= 3 || losses >= 3 }
    private var coins: Int { wins >= 3 ? 40 + max(0, 3 - losses) * 5 : 10 }

    var body: some View {
        ZStack {
            ThemedBackground()
            VStack(spacing: 20) {
                Text("你 \(wins) : \(losses) \(store.state.pet?.name ?? "")")
                    .font(.system(.title, design: .rounded).weight(.heavy).monospacedDigit())

                HStack(spacing: 40) {
                    VStack {
                        Text(mine.map { hands[$0] } ?? "❔").font(.system(size: 64))
                        Text("你").font(.caption)
                    }
                    Text("VS").font(.headline).foregroundStyle(.secondary)
                    VStack {
                        if let pet = store.state.pet {
                            PetAvatarView(species: pet.species, stage: pet.stage, mood: losses > wins ? .happy : .normal, size: 56)
                        }
                        Text(theirs.map { hands[$0] } ?? "❔").font(.system(size: 64))
                    }
                }

                if let last = log.last { Text(last).font(.headline) }

                HStack(spacing: 14) {
                    ForEach(0..<3) { i in
                        Button {
                            play(i)
                        } label: {
                            VStack {
                                Text(hands[i]).font(.system(size: 44))
                                Text(names[i]).font(.caption)
                            }
                            .frame(width: 88, height: 96)
                            .background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
                        }
                        .buttonStyle(.plain)
                        .disabled(over)
                    }
                }

                Text("小提示：观察它最常出什么～").font(.footnote).foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.top, 30)

            if over {
                GameResultView(title: wins >= 3 ? "你赢了！🎉" : "它赢了…", detail: "比分 \(wins) : \(losses)", coins: coins,
                               onAgain: reset, onExit: { dismiss() })
            }
        }
        .navigationTitle("猜拳大战")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func play(_ choice: Int) {
        let bias = store.state.pet?.species.rpsBias ?? 0
        let pet = Double.random(in: 0..<1) < 0.45 ? bias : Int.random(in: 0..<3)
        withAnimation(.spring) {
            mine = choice
            theirs = pet
        }
        // 0 石头 胜 1 剪刀，1 剪刀 胜 2 布，2 布 胜 0 石头
        if choice == pet {
            log.append("平局，再来！")
        } else if (choice + 1) % 3 == pet {
            wins += 1
            log.append("你赢了这一局！")
        } else {
            losses += 1
            log.append("它赢了这一局～")
        }
        if over {
            store.act { $0.finishGame(.rps, score: wins, coinsEarned: coins) }
        }
    }

    private func reset() {
        mine = nil
        theirs = nil
        wins = 0
        losses = 0
        log = []
    }
}
