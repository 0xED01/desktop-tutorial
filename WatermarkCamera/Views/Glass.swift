import SwiftUI

extension View {
    /// iOS 26 上使用系统 Liquid Glass，旧系统退回到毛玻璃材质。
    @ViewBuilder
    func liquidGlass<S: Shape>(in shape: S, interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            self.glassEffect(interactive ? .regular.interactive() : .regular, in: shape)
        } else {
            self.background(.ultraThinMaterial, in: shape)
                .overlay(shape.stroke(.white.opacity(0.25), lineWidth: 0.5))
        }
    }
}

/// 圆形玻璃按钮
struct GlassIconButton: View {
    let systemName: String
    var size: CGFloat = 44
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .liquidGlass(in: Circle(), interactive: true)
    }
}

/// 取景框上的实时水印预览（使用系统玻璃效果，最终成片由 WatermarkRenderer 绘制）
struct WatermarkBadge: View {
    @ObservedObject var settings: WatermarkSettings
    var date = Date()

    private var content: WatermarkContent {
        WatermarkContent(info: PhotoInfo(metadata: [:], fallbackDate: date), options: settings.options)
    }

    var body: some View {
        HStack(spacing: 10) {
            HStack(spacing: 4) {
                Image(systemName: "apple.logo")
                Text(content.model)
            }
            .font(.system(size: 15, weight: .semibold))
            Spacer(minLength: 8)
            Rectangle()
                .fill(.white.opacity(0.35))
                .frame(width: 1, height: 16)
            VStack(alignment: .trailing, spacing: 1) {
                Text(content.dateText(settings.dateStyle))
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
                if settings.showTime {
                    Text(content.timeText)
                        .font(.system(size: 10, weight: .medium).monospacedDigit())
                        .opacity(0.75)
                }
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 18)
        .frame(height: 48)
        .liquidGlass(in: Capsule())
    }
}
