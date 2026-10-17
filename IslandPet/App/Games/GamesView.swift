import SwiftUI

struct GamesView: View {
    @EnvironmentObject var store: PetStore

    var body: some View {
        NavigationStack {
            ZStack {
                ThemedBackground()
                ScrollView {
                    VStack(spacing: 12) {
                        if let blocker = store.state.gameBlocker {
                            Card {
                                Label(blocker, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                            }
                        }
                        ForEach(GameKind.allCases) { game in
                            NavigationLink {
                                destination(game)
                            } label: {
                                Card {
                                    HStack(spacing: 14) {
                                        Text(game.emoji).font(.system(size: 40))
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(game.name).font(.system(.headline, design: .rounded))
                                            Text(game.intro).font(.caption).foregroundStyle(.secondary)
                                            if let best = store.state.counters.highScores[game.rawValue], game != .memory {
                                                Text("最佳：\(best)").font(.caption2.bold()).foregroundStyle(.yellow)
                                            }
                                        }
                                        Spacer()
                                        Image(systemName: "chevron.right").foregroundStyle(.secondary)
                                    }
                                }
                            }
                            .buttonStyle(.plain)
                            .disabled(store.state.gameBlocker != nil)
                            .opacity(store.state.gameBlocker != nil ? 0.5 : 1)
                        }
                        Text("每局消耗宠物 10 点精力，赚到的金币可以去商店买零食和装扮。")
                            .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }
                    .padding()
                }
            }
            .navigationTitle("游乐场")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { CoinBadge(coins: store.state.coins) } }
        }
    }

    @ViewBuilder
    private func destination(_ game: GameKind) -> some View {
        switch game {
        case .catchFood: CatchGameView()
        case .memory: MemoryGameView()
        case .reaction: ReactionGameView()
        case .rps: RPSGameView()
        }
    }
}
