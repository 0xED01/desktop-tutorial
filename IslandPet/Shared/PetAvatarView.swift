import SwiftUI

/// 纯 SwiftUI 图形绘制的宠物，不依赖图片素材，灵动岛和 App 共用。
/// 表情会随心情变化：开心眯眼、睡觉闭眼、生病晕眼、难过流泪……
struct PetAvatarView: View {
    var species: Species
    var stage: GrowthStage
    var mood: Mood
    var accessory: String? = nil
    var size: CGFloat = 60
    var pose: Int = 0
    var eggCracks: Int = 0

    var body: some View {
        ZStack {
            if stage == .egg {
                EggShape(species: species, cracks: eggCracks, size: size)
                    .rotationEffect(.degrees(pose % 2 == 0 ? -6 : 6))
            } else {
                creature
                    .scaleEffect(stage.scale)
                    .rotationEffect(.degrees(mood == .sleeping ? 0 : (pose % 2 == 0 ? -3 : 3)))
                    .offset(y: size * (1 - stage.scale) * 0.25)
            }
        }
        .frame(width: size, height: size)
    }

    private var s: CGFloat { size }

    private var creature: some View {
        ZStack {
            ears
            torso(species)
            face
                .offset(y: s * 0.06)
            if mood == .dirty {
                dirtSpots
            }
            if let accessory {
                Text(accessory)
                    .font(.system(size: s * 0.32))
                    .offset(x: s * 0.12, y: -s * 0.36)
            }
            overlayMark
        }
    }

    // MARK: 身体

    @ViewBuilder
    private func torso(_ sp: Species) -> some View {
        let fill = LinearGradient(colors: [sp.bodyColor, sp.bodyColor.opacity(0.85), sp.shadeColor.opacity(0.9)],
                                  startPoint: .top, endPoint: .bottom)
        switch sp {
        case .slime:
            ZStack {
                Circle().fill(sp.bodyColor).frame(width: s * 0.22).offset(y: -s * 0.3)
                UnevenRoundedRectangle(topLeadingRadius: s * 0.4, bottomLeadingRadius: s * 0.12,
                                       bottomTrailingRadius: s * 0.12, topTrailingRadius: s * 0.4)
                    .fill(fill.opacity(0.92))
                    .frame(width: s * 0.86, height: s * 0.66)
                    .offset(y: s * 0.1)
                Ellipse().fill(.white.opacity(0.45))
                    .frame(width: s * 0.16, height: s * 0.1)
                    .offset(x: -s * 0.24, y: -s * 0.08)
            }
        default:
            ZStack {
                Ellipse()
                    .fill(fill)
                    .frame(width: s * 0.82, height: s * 0.72)
                    .offset(y: s * 0.08)
                Ellipse()
                    .fill(.white.opacity(sp == .bunny ? 0.5 : 0.35))
                    .frame(width: s * 0.42, height: s * 0.3)
                    .offset(y: s * 0.26)
                if sp == .dragon {
                    // 背上的小刺
                    ForEach(0..<3) { i in
                        Triangle()
                            .fill(Color(red: 1.0, green: 0.85, blue: 0.4))
                            .frame(width: s * 0.1, height: s * 0.1)
                            .offset(x: s * (0.36 + CGFloat(i) * 0.04), y: s * (-0.08 + CGFloat(i) * 0.14))
                            .rotationEffect(.degrees(70))
                    }
                }
            }
        }
    }

    // MARK: 耳朵 / 角

    @ViewBuilder
    private var ears: some View {
        switch species {
        case .cat:
            HStack(spacing: s * 0.3) {
                Triangle().fill(species.shadeColor).frame(width: s * 0.24, height: s * 0.26).rotationEffect(.degrees(-12))
                Triangle().fill(species.shadeColor).frame(width: s * 0.24, height: s * 0.26).rotationEffect(.degrees(12))
            }
            .offset(y: -s * 0.27)
        case .dog:
            HStack(spacing: s * 0.6) {
                Ellipse().fill(species.shadeColor).frame(width: s * 0.2, height: s * 0.36).rotationEffect(.degrees(20))
                Ellipse().fill(species.shadeColor).frame(width: s * 0.2, height: s * 0.36).rotationEffect(.degrees(-20))
            }
            .offset(y: -s * 0.08)
        case .bunny:
            HStack(spacing: s * 0.14) {
                Capsule().fill(species.bodyColor)
                    .overlay(Capsule().fill(species.shadeColor).padding(s * 0.035))
                    .frame(width: s * 0.15, height: s * 0.42)
                    .rotationEffect(.degrees(mood == .sad || mood == .sick ? -35 : -8))
                Capsule().fill(species.bodyColor)
                    .overlay(Capsule().fill(species.shadeColor).padding(s * 0.035))
                    .frame(width: s * 0.15, height: s * 0.42)
                    .rotationEffect(.degrees(mood == .sad || mood == .sick ? 35 : 8))
            }
            .offset(y: -s * 0.36)
        case .dragon:
            HStack(spacing: s * 0.34) {
                Triangle().fill(Color(red: 1.0, green: 0.88, blue: 0.5)).frame(width: s * 0.12, height: s * 0.2).rotationEffect(.degrees(-18))
                Triangle().fill(Color(red: 1.0, green: 0.88, blue: 0.5)).frame(width: s * 0.12, height: s * 0.2).rotationEffect(.degrees(18))
            }
            .offset(y: -s * 0.3)
        case .slime:
            EmptyView()
        }
    }

    // MARK: 表情

    private var face: some View {
        let eyeGap = s * 0.24
        return VStack(spacing: s * 0.05) {
            HStack(spacing: eyeGap) {
                eye(left: true)
                eye(left: false)
            }
            ZStack {
                HStack(spacing: s * 0.34) {
                    if [Mood.happy, .loved, .normal].contains(mood) {
                        Ellipse().fill(Color.pink.opacity(0.45)).frame(width: s * 0.1, height: s * 0.06)
                        Ellipse().fill(Color.pink.opacity(0.45)).frame(width: s * 0.1, height: s * 0.06)
                    }
                }
                mouth
            }
            .frame(height: s * 0.1)
        }
    }

    private var lineWidth: CGFloat { max(1.2, s * 0.035) }
    private var ink: Color { Color(red: 0.18, green: 0.14, blue: 0.16) }

    @ViewBuilder
    private func eye(left: Bool) -> some View {
        let w = s * 0.11
        switch mood {
        case .happy, .loved:
            ArcShape(up: true).stroke(ink, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .frame(width: w, height: w * 0.55)
        case .sleeping:
            ArcShape(up: false).stroke(ink, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .frame(width: w, height: w * 0.45)
        case .sleepy:
            Capsule().fill(ink).frame(width: w, height: max(1.5, w * 0.28))
        case .sick:
            CrossShape().stroke(ink, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .frame(width: w * 0.8, height: w * 0.8)
        case .sad:
            ZStack(alignment: .bottom) {
                Circle().fill(ink).frame(width: w * 0.9)
                if left {
                    Ellipse().fill(Color.cyan.opacity(0.8))
                        .frame(width: w * 0.4, height: w * 0.6)
                        .offset(x: -w * 0.1, y: w * 0.7)
                }
            }
        default:
            Circle().fill(ink)
                .overlay(Circle().fill(.white).frame(width: w * 0.35).offset(x: w * 0.15, y: -w * 0.15))
                .frame(width: w, height: w)
        }
    }

    @ViewBuilder
    private var mouth: some View {
        let w = s * 0.12
        switch mood {
        case .happy, .loved:
            HalfCircle().fill(Color(red: 0.85, green: 0.3, blue: 0.35))
                .frame(width: w, height: w * 0.55)
        case .hungry:
            Ellipse().fill(Color(red: 0.6, green: 0.2, blue: 0.25)).frame(width: w * 0.6, height: w * 0.7)
        case .sad, .sick:
            ArcShape(up: true).stroke(ink, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .frame(width: w * 0.7, height: w * 0.3)
        case .sleeping:
            Circle().stroke(ink, lineWidth: lineWidth * 0.8).frame(width: w * 0.3)
        default:
            ArcShape(up: false).stroke(ink, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .frame(width: w * 0.7, height: w * 0.3)
        }
    }

    private var dirtSpots: some View {
        ZStack {
            Circle().fill(Color.brown.opacity(0.5)).frame(width: s * 0.07).offset(x: -s * 0.25, y: s * 0.22)
            Circle().fill(Color.brown.opacity(0.5)).frame(width: s * 0.05).offset(x: s * 0.2, y: s * 0.3)
            Circle().fill(Color.brown.opacity(0.4)).frame(width: s * 0.06).offset(x: s * 0.28, y: s * 0.08)
        }
    }

    @ViewBuilder
    private var overlayMark: some View {
        switch mood {
        case .sleeping:
            Text("z").font(.system(size: s * 0.22, weight: .heavy, design: .rounded))
                .foregroundStyle(.white.opacity(0.9))
                .offset(x: s * 0.38, y: -s * 0.3)
        case .loved:
            Text("♥").font(.system(size: s * 0.24, weight: .bold))
                .foregroundStyle(.pink)
                .offset(x: s * 0.38, y: -s * 0.28)
        case .hungry:
            Text("!").font(.system(size: s * 0.24, weight: .black, design: .rounded))
                .foregroundStyle(.orange)
                .offset(x: s * 0.38, y: -s * 0.28)
        default:
            EmptyView()
        }
    }
}

// MARK: - 蛋

private struct EggShape: View {
    var species: Species
    var cracks: Int
    var size: CGFloat

    var body: some View {
        ZStack {
            Ellipse()
                .fill(LinearGradient(colors: [Color(red: 1, green: 0.98, blue: 0.92), Color(red: 0.93, green: 0.88, blue: 0.78)],
                                     startPoint: .top, endPoint: .bottom))
                .frame(width: size * 0.62, height: size * 0.8)
            Circle().fill(species.bodyColor).frame(width: size * 0.14).offset(x: -size * 0.12, y: size * 0.08)
            Circle().fill(species.bodyColor).frame(width: size * 0.1).offset(x: size * 0.14, y: -size * 0.12)
            Circle().fill(species.bodyColor).frame(width: size * 0.08).offset(x: size * 0.08, y: size * 0.24)
            if cracks > 0 {
                CrackShape()
                    .stroke(Color(red: 0.45, green: 0.35, blue: 0.25), style: StrokeStyle(lineWidth: max(1, size * 0.025), lineJoin: .round))
                    .frame(width: size * 0.4, height: size * 0.12)
                    .offset(y: -size * 0.04)
                    .opacity(min(1, Double(cracks) / 3))
            }
        }
    }
}

// MARK: - 基础形状

struct Triangle: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.midX, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
            p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
            p.closeSubpath()
        }
    }
}

/// up = ^ 形（开心眯眼），否则 ‿ 形
struct ArcShape: Shape {
    var up: Bool
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.minX, y: up ? r.maxY : r.minY))
            p.addQuadCurve(to: CGPoint(x: r.maxX, y: up ? r.maxY : r.minY),
                           control: CGPoint(x: r.midX, y: up ? r.minY - r.height * 0.6 : r.maxY + r.height * 0.6))
        }
    }
}

struct HalfCircle: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.minX, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
            p.addQuadCurve(to: CGPoint(x: r.minX, y: r.minY), control: CGPoint(x: r.midX, y: r.maxY * 1.6))
            p.closeSubpath()
        }
    }
}

struct CrossShape: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.minX, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
            p.move(to: CGPoint(x: r.maxX, y: r.minY))
            p.addLine(to: CGPoint(x: r.minX, y: r.maxY))
        }
    }
}

struct CrackShape: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            let steps = 6
            p.move(to: CGPoint(x: r.minX, y: r.midY))
            for i in 1...steps {
                let x = r.minX + r.width * CGFloat(i) / CGFloat(steps)
                p.addLine(to: CGPoint(x: x, y: i % 2 == 0 ? r.midY : (i % 4 == 1 ? r.minY : r.maxY)))
            }
        }
    }
}
