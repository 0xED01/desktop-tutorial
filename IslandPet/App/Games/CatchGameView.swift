import Combine
import SwiftUI

/// 接零食：拖动宠物左右移动，接住零食得分，碰到炸弹扣命
@MainActor
final class CatchGameModel: ObservableObject {
    struct Drop: Identifiable {
        let id = UUID()
        var x: CGFloat
        var y: CGFloat
        var speed: CGFloat
        let emoji: String
        let bad: Bool
        let points: Int
    }

    enum Phase { case ready, playing, over }

    @Published var drops: [Drop] = []
    @Published var playerX: CGFloat = 0.5
    @Published var score = 0
    @Published var lives = 3
    @Published var timeLeft: Double = 40
    @Published var phase: Phase = .ready
    @Published var flash: String?

    private var spawnTimer = 0.0
    private let goodies: [(String, Int)] = [("🍪", 1), ("🐟", 2), ("🍖", 2), ("🥕", 1), ("🍮", 2), ("🍰", 3), ("🌟", 5)]

    func start() {
        drops = []
        score = 0
        lives = 3
        timeLeft = 40
        playerX = 0.5
        spawnTimer = 0
        phase = .playing
    }

    func step(_ dt: Double) {
        guard phase == .playing else { return }
        timeLeft -= dt
        if timeLeft <= 0 || lives <= 0 {
            phase = .over
            return
        }
        // 越到后面掉得越快、越密
        let progress = 1 - timeLeft / 40
        spawnTimer -= dt
        if spawnTimer <= 0 {
            spawnTimer = 0.75 - 0.4 * progress
            let bad = Double.random(in: 0..<1) < 0.18 + 0.12 * progress
            let goodie = goodies.randomElement()!
            drops.append(Drop(x: CGFloat.random(in: 0.08...0.92), y: -0.05,
                              speed: CGFloat.random(in: 0.28...0.42) * CGFloat(1 + progress * 0.8),
                              emoji: bad ? "💣" : goodie.0, bad: bad, points: bad ? 0 : goodie.1))
        }

        for i in drops.indices { drops[i].y += drops[i].speed * CGFloat(dt) }

        drops.removeAll { drop in
            if drop.y > 0.82, drop.y < 0.95, abs(drop.x - playerX) < 0.11 {
                if drop.bad {
                    lives -= 1
                    flash = "💥"
                } else {
                    score += drop.points
                    flash = "+\(drop.points)"
                }
                return true
            }
            return drop.y > 1.05
        }
    }

    var coinsEarned: Int { score / 2 + 5 }
}

struct CatchGameView: View {
    @EnvironmentObject var store: PetStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var game = CatchGameModel()
    private let ticker = Timer.publish(every: 1.0 / 60, on: .main, in: .common).autoconnect()

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ThemedBackground()

                ForEach(game.drops) { drop in
                    Text(drop.emoji)
                        .font(.system(size: 34))
                        .position(x: drop.x * geo.size.width, y: drop.y * geo.size.height)
                }

                if let pet = store.state.pet {
                    PetAvatarView(species: pet.species, stage: pet.stage, mood: game.lives < 2 ? .sad : .happy,
                                  accessory: ShopItem.find(pet.accessory)?.emoji, size: 80)
                        .position(x: game.playerX * geo.size.width, y: geo.size.height * 0.88)
                }

                if let flash = game.flash {
                    Text(flash)
                        .font(.system(.title, design: .rounded).weight(.heavy))
                        .foregroundStyle(flash == "💥" ? .red : .yellow)
                        .position(x: game.playerX * geo.size.width, y: geo.size.height * 0.74)
                        .id(UUID())
                        .transition(.opacity)
                }

                VStack {
                    HStack {
                        Text("得分 \(game.score)").font(.system(.headline, design: .rounded).monospacedDigit())
                        Spacer()
                        Text(String(repeating: "❤️", count: max(0, game.lives)))
                        Spacer()
                        Text("⏱ \(Int(max(0, game.timeLeft)))s").font(.system(.headline, design: .rounded).monospacedDigit())
                    }
                    .padding()
                    Spacer()
                }

                switch game.phase {
                case .ready:
                    GameResultView(title: "接零食", detail: "左右拖动接住零食，别碰💣！\n🌟 = 5 分", coins: nil,
                                   onAgain: { game.start() }, onExit: { dismiss() })
                case .over:
                    GameResultView(title: "游戏结束", detail: "得分 \(game.score)", coins: game.coinsEarned,
                                   onAgain: { game.start() }, onExit: { dismiss() })
                case .playing:
                    EmptyView()
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                game.playerX = min(0.95, max(0.05, v.location.x / geo.size.width))
            })
        }
        .navigationTitle("接零食")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(ticker) { _ in game.step(1.0 / 60) }
        .onChange(of: game.phase) { _, phase in
            if phase == .over {
                store.act { $0.finishGame(.catchFood, score: game.score, coinsEarned: game.coinsEarned) }
            }
        }
        .onChange(of: game.flash) { _, v in
            guard v != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { game.flash = nil }
        }
    }
}
