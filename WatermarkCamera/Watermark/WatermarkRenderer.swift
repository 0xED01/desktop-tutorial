import CoreImage
import UIKit

/// 把“ iPhone 型号 | 年月日”以 iOS 26 液态玻璃（Liquid Glass）质感合成到照片底部。
///
/// 玻璃效果的做法：取胶囊区域下面的画面做高斯模糊 + 提高饱和度，
/// 再叠一层白色薄雾、顶部高光、底部内发光和一圈上亮下暗的高光描边，最后加一层柔和投影。
enum WatermarkRenderer {
    private static let context = CIContext(options: [.cacheIntermediates: false])
    private static let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!
    /// 导入超大照片时先缩到约 2400 万像素以内，防止内存暴涨
    private static let maxPixelCount: CGFloat = 24_000_000

    static func render(_ image: UIImage, content: WatermarkContent, options: WatermarkOptions) -> UIImage {
        guard let photo = upright(image) else { return image }
        let width = CGFloat(photo.width)
        let photoHeight = CGFloat(photo.height)
        // 以短边作为尺寸基准，横拍竖拍水印比例一致
        let unit = min(width, photoHeight)
        let margin = unit * 0.04

        let canvasSize: CGSize
        let panel: CGRect
        let base: CGImage
        switch options.style {
        case .floating:
            let height = unit * 0.15
            canvasSize = CGSize(width: width, height: photoHeight)
            panel = CGRect(x: margin, y: photoHeight - margin - height, width: width - margin * 2, height: height)
            base = photo
        case .bar:
            let barHeight = unit * 0.22
            let height = barHeight * 0.68
            guard let extended = extendedBase(photo, barHeight: barHeight, unit: unit) else { return image }
            canvasSize = CGSize(width: width, height: photoHeight + barHeight)
            panel = CGRect(x: margin, y: photoHeight + (barHeight - height) / 2, width: width - margin * 2, height: height)
            base = extended
        }

        guard let glass = makeBackdrop(for: panel, in: base, unit: unit) else { return image }
        let isLightGlass = glass.luminance > 0.62

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: canvasSize, format: format).image { context in
            UIImage(cgImage: base).draw(in: CGRect(origin: .zero, size: canvasSize))
            drawGlass(in: context.cgContext, panel: panel, backdrop: glass.image, isLight: isLightGlass)
            drawContent(in: panel, content: content, options: options, isLight: isLightGlass)
        }
    }

    // MARK: - 底图

    /// 把方向矫正成 .up 并转成 CGImage（像素 = 点）。
    private static func upright(_ image: UIImage) -> CGImage? {
        let pixelSize = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
        let downscale = min(1, sqrt(maxPixelCount / (pixelSize.width * pixelSize.height)))
        if image.imageOrientation == .up, downscale == 1, let cgImage = image.cgImage {
            return cgImage
        }
        let target = CGSize(width: (pixelSize.width * downscale).rounded(), height: (pixelSize.height * downscale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }.cgImage
    }

    /// 底栏样式：在照片下方接一段“倒影 + 重度模糊”的区域，像磨砂玻璃延伸出去。
    private static func extendedBase(_ photo: CGImage, barHeight: CGFloat, unit: CGFloat) -> CGImage? {
        let source = CIImage(cgImage: photo)
        let width = source.extent.width
        let height = source.extent.height
        let band = CGRect(x: 0, y: 0, width: width, height: barHeight)

        // Core Image 坐标原点在左下角：y∈[0, barHeight] 是照片最下面一条，垂直翻转后贴在接缝处
        let strip = source.cropped(to: CGRect(x: 0, y: 0, width: width, height: min(barHeight, height)))
        let mirrored = strip.transformed(by: CGAffineTransform(scaleX: 1, y: -1).translatedBy(x: 0, y: -barHeight))
        let frosted = mirrored.clampedToExtent()
            .applyingGaussianBlur(sigma: unit * 0.035)
            .applyingFilter("CIColorControls", parameters: [
                kCIInputSaturationKey: 1.3,
                kCIInputBrightnessKey: -0.04,
            ])
            .cropped(to: band)

        let lifted = source.transformed(by: CGAffineTransform(translationX: 0, y: barHeight))
        let extent = CGRect(x: 0, y: 0, width: width, height: height + barHeight)
        return context.createCGImage(lifted.composited(over: frosted), from: extent, format: .RGBA8, colorSpace: sRGB)
    }

    /// 返回玻璃背后的模糊画面，以及它的平均亮度（决定用深色还是浅色文字）。
    private static func makeBackdrop(for panel: CGRect, in base: CGImage, unit: CGFloat) -> (image: CGImage, luminance: CGFloat)? {
        let sigma = unit * 0.022
        let canvasHeight = CGFloat(base.height)
        let rect = CGRect(x: panel.minX, y: canvasHeight - panel.maxY, width: panel.width, height: panel.height)

        let blurred = CIImage(cgImage: base)
            .cropped(to: rect.insetBy(dx: -sigma * 3, dy: -sigma * 3))
            .clampedToExtent()
            .applyingGaussianBlur(sigma: sigma)
            .applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 1.6])
            .cropped(to: rect)

        guard let image = context.createCGImage(blurred, from: rect, format: .RGBA8, colorSpace: sRGB) else { return nil }

        var pixel = [UInt8](repeating: 0, count: 4)
        if let average = CIFilter(name: "CIAreaAverage", parameters: [
            kCIInputImageKey: blurred,
            kCIInputExtentKey: CIVector(cgRect: rect),
        ])?.outputImage {
            context.render(average, toBitmap: &pixel, rowBytes: 4, bounds: average.extent, format: .RGBA8, colorSpace: sRGB)
        }
        let luminance = (0.2126 * CGFloat(pixel[0]) + 0.7152 * CGFloat(pixel[1]) + 0.0722 * CGFloat(pixel[2])) / 255
        return (image, luminance)
    }

    // MARK: - 玻璃

    private static func drawGlass(in cg: CGContext, panel: CGRect, backdrop: CGImage, isLight: Bool) {
        let radius = panel.height * 0.46
        let shape = UIBezierPath(roundedRect: panel, cornerRadius: radius).cgPath

        // 投影，让玻璃“浮”起来
        cg.saveGState()
        cg.setShadow(offset: CGSize(width: 0, height: panel.height * 0.08), blur: panel.height * 0.4,
                     color: UIColor.black.withAlphaComponent(isLight ? 0.18 : 0.3).cgColor)
        cg.addPath(shape)
        cg.setFillColor(UIColor.black.cgColor)
        cg.fillPath()
        cg.restoreGState()

        // 玻璃本体
        cg.saveGState()
        cg.addPath(shape)
        cg.clip()
        UIImage(cgImage: backdrop).draw(in: panel)
        cg.setFillColor(UIColor(white: 1, alpha: isLight ? 0.24 : 0.12).cgColor)
        cg.fill(panel)
        // 顶部高光
        linearGradient(cg, colors: [UIColor(white: 1, alpha: 0.32), UIColor(white: 1, alpha: 0)],
                       from: CGPoint(x: panel.midX, y: panel.minY),
                       to: CGPoint(x: panel.midX, y: panel.minY + panel.height * 0.55))
        // 底部内发光，模拟玻璃厚度
        linearGradient(cg, colors: [UIColor(white: 1, alpha: 0), UIColor(white: 1, alpha: 0.14)],
                       from: CGPoint(x: panel.midX, y: panel.minY + panel.height * 0.6),
                       to: CGPoint(x: panel.midX, y: panel.maxY))
        cg.restoreGState()

        // 边缘高光描边：上亮、中间淡、下方再亮一点
        let lineWidth = max(2, panel.height * 0.014)
        let rim = UIBezierPath(roundedRect: panel.insetBy(dx: lineWidth / 2, dy: lineWidth / 2),
                               cornerRadius: radius - lineWidth / 2).cgPath
        cg.saveGState()
        cg.addPath(rim.copy(strokingWithWidth: lineWidth, lineCap: .round, lineJoin: .round, miterLimit: 10))
        cg.clip()
        linearGradient(cg, colors: [UIColor(white: 1, alpha: 0.9), UIColor(white: 1, alpha: 0.18), UIColor(white: 1, alpha: 0.5)],
                       locations: [0, 0.5, 1],
                       from: CGPoint(x: panel.midX, y: panel.minY),
                       to: CGPoint(x: panel.midX, y: panel.maxY))
        cg.restoreGState()
    }

    private static func linearGradient(_ cg: CGContext, colors: [UIColor], locations: [CGFloat]? = nil,
                                       from start: CGPoint, to end: CGPoint) {
        guard let gradient = CGGradient(colorsSpace: sRGB, colors: colors.map(\.cgColor) as CFArray,
                                        locations: locations) else { return }
        cg.drawLinearGradient(gradient, start: start, end: end,
                              options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    // MARK: - 文字

    private static func drawContent(in panel: CGRect, content: WatermarkContent, options: WatermarkOptions, isLight: Bool) {
        let h = panel.height
        let primary = isLight ? UIColor(white: 0.08, alpha: 0.9) : UIColor.white
        let secondary = primary.withAlphaComponent(isLight ? 0.6 : 0.75)
        let paddingX = h * 0.42
        let available = panel.width - paddingX * 2

        let dateText = content.dateText(options.dateStyle)
        let timeText = options.showTime ? content.timeText : nil
        let parameters = content.parameters

        // 先按 1 倍排版，放不下就整体缩小
        var scale: CGFloat = 1
        var layout = Layout(h: h, scale: scale, model: content.model, parameters: parameters, date: dateText, time: timeText)
        for _ in 0..<3 where layout.requiredWidth > available {
            scale *= available / layout.requiredWidth
            layout = Layout(h: h, scale: scale, model: content.model, parameters: parameters, date: dateText, time: timeText)
        }

        let leftTwoLines = parameters != nil
        let rightTwoLines = timeText != nil
        let upperY = panel.midY - h * 0.13 * max(scale, 0.8)
        let lowerY = panel.midY + h * 0.17 * max(scale, 0.8)

        // 左侧： + 型号，下方拍摄参数
        var x = panel.minX + paddingX
        let firstLineY = leftTwoLines ? upperY : panel.midY
        if let logo = layout.logo?.withTintColor(primary, renderingMode: .alwaysOriginal) {
            let size = logo.size
            logo.draw(in: CGRect(x: x, y: firstLineY - size.height / 2 - size.height * 0.06, width: size.width, height: size.height))
            x += size.width + layout.logoGap
        }
        drawLine(content.model, font: layout.modelFont, color: primary, x: x, centerY: firstLineY)
        if let parameters {
            drawLine(parameters, font: layout.parameterFont, color: secondary, x: panel.minX + paddingX, centerY: lowerY)
        }

        // 右侧：日期，下方时间（右对齐）
        let rightX = panel.maxX - paddingX
        drawLine(dateText, font: layout.dateFont, color: primary, x: rightX, centerY: rightTwoLines ? upperY : panel.midY, alignRight: true)
        if let timeText {
            drawLine(timeText, font: layout.timeFont, color: secondary, x: rightX, centerY: lowerY, alignRight: true)
        }

        // 分隔竖线
        let dividerX = rightX - layout.rightWidth - layout.dividerGap
        let dividerHeight = h * 0.42 * scale
        primary.withAlphaComponent(0.3).setFill()
        UIRectFill(CGRect(x: dividerX - max(1, h * 0.006) / 2, y: panel.midY - dividerHeight / 2,
                          width: max(1, h * 0.006), height: dividerHeight))
    }

    /// 让文字的大写字母高度在 centerY 处垂直居中。
    private static func drawLine(_ text: String, font: UIFont, color: UIColor, x: CGFloat, centerY: CGFloat, alignRight: Bool = false) {
        let string = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: color])
        let width = string.size().width
        let baseline = centerY + font.capHeight / 2
        string.draw(at: CGPoint(x: alignRight ? x - width : x, y: baseline - font.ascender))
    }

    private struct Layout {
        let modelFont: UIFont
        let parameterFont: UIFont
        let dateFont: UIFont
        let timeFont: UIFont
        let logo: UIImage?
        let logoGap: CGFloat
        let dividerGap: CGFloat
        let leftWidth: CGFloat
        let rightWidth: CGFloat

        var requiredWidth: CGFloat { leftWidth + rightWidth + dividerGap * 2 }

        init(h: CGFloat, scale s: CGFloat, model: String, parameters: String?, date: String, time: String?) {
            modelFont = .systemFont(ofSize: h * 0.25 * s, weight: .semibold)
            parameterFont = .monospacedDigitSystemFont(ofSize: h * 0.15 * s, weight: .medium)
            dateFont = .monospacedDigitSystemFont(ofSize: h * 0.21 * s, weight: .semibold)
            timeFont = .monospacedDigitSystemFont(ofSize: h * 0.15 * s, weight: .medium)
            logo = UIImage(systemName: "apple.logo",
                           withConfiguration: UIImage.SymbolConfiguration(pointSize: h * 0.27 * s, weight: .semibold))
            logoGap = h * 0.1 * s
            dividerGap = h * 0.3 * s

            func width(_ text: String?, _ font: UIFont) -> CGFloat {
                guard let text else { return 0 }
                return NSAttributedString(string: text, attributes: [.font: font]).size().width
            }
            let firstLine = (logo?.size.width ?? 0) + logoGap + width(model, modelFont)
            leftWidth = max(firstLine, width(parameters, parameterFont))
            rightWidth = max(width(date, dateFont), width(time, timeFont))
        }
    }
}
