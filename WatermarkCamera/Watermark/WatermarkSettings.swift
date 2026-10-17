import Foundation

enum WatermarkStyle: String, CaseIterable, Identifiable {
    /// 玻璃胶囊悬浮在照片底部
    case floating
    /// 照片下方延伸出一条模糊底栏，玻璃胶囊放在底栏里
    case bar

    var id: String { rawValue }
    var title: String {
        switch self {
        case .floating: "悬浮玻璃"
        case .bar: "底部玻璃栏"
        }
    }
}

enum DateStyle: String, CaseIterable, Identifiable {
    case chinese, dotted, dashed

    var id: String { rawValue }
    var format: String {
        switch self {
        case .chinese: "yyyy年M月d日"
        case .dotted: "yyyy.MM.dd"
        case .dashed: "yyyy-MM-dd"
        }
    }
}

/// 渲染一次水印所需的全部选项（值类型，便于在后台线程渲染）。
struct WatermarkOptions: Equatable {
    var style: WatermarkStyle = .floating
    var dateStyle: DateStyle = .chinese
    var showTime = true
    var showParameters = true
    var customModel = ""
}

final class WatermarkSettings: ObservableObject {
    private let defaults = UserDefaults.standard

    @Published var style: WatermarkStyle { didSet { defaults.set(style.rawValue, forKey: "wm.style") } }
    @Published var dateStyle: DateStyle { didSet { defaults.set(dateStyle.rawValue, forKey: "wm.dateStyle") } }
    @Published var showTime: Bool { didSet { defaults.set(showTime, forKey: "wm.showTime") } }
    @Published var showParameters: Bool { didSet { defaults.set(showParameters, forKey: "wm.showParameters") } }
    @Published var customModel: String { didSet { defaults.set(customModel, forKey: "wm.customModel") } }

    init() {
        style = WatermarkStyle(rawValue: defaults.string(forKey: "wm.style") ?? "") ?? .floating
        dateStyle = DateStyle(rawValue: defaults.string(forKey: "wm.dateStyle") ?? "") ?? .chinese
        showTime = defaults.object(forKey: "wm.showTime") as? Bool ?? true
        showParameters = defaults.object(forKey: "wm.showParameters") as? Bool ?? true
        customModel = defaults.string(forKey: "wm.customModel") ?? ""
    }

    var options: WatermarkOptions {
        WatermarkOptions(style: style, dateStyle: dateStyle, showTime: showTime,
                         showParameters: showParameters, customModel: customModel)
    }
}

/// 水印上要写的文字。
struct WatermarkContent {
    var model: String
    var date: Date
    var parameters: String?

    init(info: PhotoInfo, options: WatermarkOptions) {
        let custom = options.customModel.trimmingCharacters(in: .whitespacesAndNewlines)
        if !custom.isEmpty {
            model = custom
        } else if info.cameraMake == "Apple", let name = info.cameraModel, !name.isEmpty {
            model = name
        } else {
            model = DeviceModel.current
        }
        date = info.date
        parameters = options.showParameters ? info.parameterText : nil
    }

    func dateText(_ style: DateStyle) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = style.format
        return formatter.string(from: date)
    }

    var timeText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }
}
