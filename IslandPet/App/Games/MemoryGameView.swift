import SwiftUI

/// 翻翻乐：6 对卡片，找出所有配对
struct MemoryGameView: View {
    @EnvironmentObject var store: PetStore
    @Environment(\.dismiss) private var dismiss

    struct CardItem: Identifiable {
        let id = UUID()
        let emoji: String
        var faceUp = false
        var matched = false
    }

    @State private var cards: [CardItem] = []
    @State private var moves = 0
    @State private var busy = false
    @State private var finished = false

    private let pool = ["🐟", "🍖", "🥕", "🌶️", "🍮", "🍰", "🍣", "🍦", "🎾", "🧶", "🎀", "👑"]

    private var coins: Int { max(10, 60 - max(0, moves - 6) * 4) }

    var body: some View {
        ZStack {
            ThemedBackground()
            VStack(spacing: 16) {
                Text("步数 \(moves)").font(.system(.title3, design: .rounded).weight(.bold).monospacedDigit())
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(cards) { card in
                        cardView(card)
                            .onTapGesture { flip(card) }
                    }
                }
                .padding(.horizontal)
                Spacer()
            }
            .padding(.top)

            if finished {
                GameResultView(title: "全部配对！", detail: "用了 \(moves) 步\(moves <= 10 ? "，过目不忘！" : "")", coins: coins,
                               onAgain: newGame, onExit: { dismiss() })
            }
        }
        .navigationTitle("翻翻乐")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if cards.isEmpty { newGame() } }
    }

    private func cardView(_ card: CardItem) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(card.faceUp || card.matched ? Color.white.opacity(0.9) : Color.orange.opacity(0.7))
            if card.faceUp || card.matched {
                Text(card.emoji).font(.system(size: 40))
            } else {
                Text("?").font(.system(size: 32, weight: .heavy, design: .rounded)).foregroundStyle(.white)
            }
        }
        .frame(height: 90)
        .opacity(card.matched ? 0.45 : 1)
        .rotation3DEffect(.degrees(card.faceUp || card.matched ? 0 : 180), axis: (x: 0, y: 1, z: 0))
        .animation(.easeInOut(duration: 0.25), value: card.faceUp)
    }

    private func newGame() {
        let picks = pool.shuffled().prefix(6)
        cards = (picks + picks).map { CardItem(emoji: $0) }.shuffled()
        moves = 0
        finished = false
        busy = false
    }

    private func flip(_ card: CardItem) {
        guard !busy, let i = cards.firstIndex(where: { $0.id == card.id }), !cards[i].faceUp, !cards[i].matched else { return }
        cards[i].faceUp = true
        let open = cards.indices.filter { cards[$0].faceUp && !cards[$0].matched }
        guard open.count == 2 else { return }
        moves += 1
        let (a, b) = (open[0], open[1])
        if cards[a].emoji == cards[b].emoji {
            cards[a].matched = true
            cards[b].matched = true
            if cards.allSatisfy(\.matched) {
                finished = true
                // 记录用 1 表示「10 步内完成」，用于成就判断
                store.act { $0.finishGame(.memory, score: moves <= 10 ? 1 : 0, coinsEarned: coins) }
            }
        } else {
            busy = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                cards[a].faceUp = false
                cards[b].faceUp = false
                busy = false
            }
        }
    }
}
