import SwiftUI

struct StatBar: View {
    var icon: String
    var title: String
    var value: Double
    var color: Color

    var body: some View {
        HStack(spacing: 8) {
            Text(icon).frame(width: 24)
            Text(title).font(.system(.caption, design: .rounded)).foregroundStyle(.secondary).frame(width: 30, alignment: .leading)
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.1))
                    Capsule()
                        .fill(LinearGradient(colors: [value < 25 ? .red : color, (value < 25 ? Color.red : color).opacity(0.7)],
                                             startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(6, geo.size.width * value / 100))
                }
            }
            .frame(height: 10)
            Text("\(Int(value))")
                .font(.system(.caption, design: .rounded).monospacedDigit())
                .frame(width: 28, alignment: .trailing)
        }
        .animation(.easeOut(duration: 0.4), value: value)
    }
}

struct CoinBadge: View {
    var coins: Int
    var body: some View {
        HStack(spacing: 4) {
            Text("🪙")
            Text("\(coins)")
                .font(.system(.subheadline, design: .rounded).weight(.bold).monospacedDigit())
                .contentTransition(.numericText())
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(.yellow.opacity(0.18), in: Capsule())
        .animation(.default, value: coins)
    }
}

struct Card<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct ThemedBackground: View {
    @EnvironmentObject var store: PetStore
    var body: some View {
        PetTheme.find(store.state.theme).gradient.ignoresSafeArea()
    }
}

/// 小游戏结束后的结算面板
struct GameResultView: View {
    var title: String
    var detail: String
    var coins: Int?
    var onAgain: () -> Void
    var onExit: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            Text(title).font(.system(.title, design: .rounded).weight(.heavy))
            Text(detail).font(.system(.body, design: .rounded)).foregroundStyle(.secondary).multilineTextAlignment(.center)
            if let coins {
                Text("+\(coins) 🪙").font(.system(.title2, design: .rounded).weight(.bold)).foregroundStyle(.yellow)
            }
            HStack(spacing: 12) {
                Button(coins == nil ? "开始" : "再来一局", action: onAgain).buttonStyle(.borderedProminent)
                Button("返回", action: onExit).buttonStyle(.bordered)
            }
        }
        .padding(24)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .padding()
    }
}
