import SwiftUI

/// 灵动岛 / 锁屏 / App 的配色主题
struct PetTheme {
    let id: String
    let name: String
    let top: Color
    let bottom: Color
    let accent: Color

    var gradient: LinearGradient {
        LinearGradient(colors: [top, bottom], startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static let all: [PetTheme] = [
        PetTheme(id: "theme.night", name: "夜空", top: Color(red: 0.12, green: 0.13, blue: 0.22), bottom: Color(red: 0.05, green: 0.05, blue: 0.1), accent: Color(red: 1.0, green: 0.75, blue: 0.4)),
        PetTheme(id: "theme.sakura", name: "樱花粉", top: Color(red: 0.55, green: 0.25, blue: 0.4), bottom: Color(red: 0.3, green: 0.1, blue: 0.22), accent: Color(red: 1.0, green: 0.7, blue: 0.82)),
        PetTheme(id: "theme.mint", name: "薄荷绿", top: Color(red: 0.12, green: 0.4, blue: 0.36), bottom: Color(red: 0.04, green: 0.2, blue: 0.18), accent: Color(red: 0.6, green: 1.0, blue: 0.85)),
        PetTheme(id: "theme.sunset", name: "落日橙", top: Color(red: 0.6, green: 0.3, blue: 0.15), bottom: Color(red: 0.32, green: 0.12, blue: 0.18), accent: Color(red: 1.0, green: 0.8, blue: 0.45)),
        PetTheme(id: "theme.ocean", name: "深海蓝", top: Color(red: 0.1, green: 0.25, blue: 0.5), bottom: Color(red: 0.03, green: 0.08, blue: 0.22), accent: Color(red: 0.5, green: 0.85, blue: 1.0)),
        PetTheme(id: "theme.galaxy", name: "星河紫", top: Color(red: 0.32, green: 0.16, blue: 0.55), bottom: Color(red: 0.08, green: 0.04, blue: 0.2), accent: Color(red: 0.85, green: 0.7, blue: 1.0)),
    ]

    static func find(_ id: String) -> PetTheme {
        all.first { $0.id == id } ?? all[0]
    }
}

extension Species {
    var bodyColor: Color {
        switch self {
        case .cat: Color(red: 1.0, green: 0.72, blue: 0.42)
        case .dog: Color(red: 0.86, green: 0.66, blue: 0.46)
        case .bunny: Color(red: 0.98, green: 0.92, blue: 0.95)
        case .dragon: Color(red: 0.45, green: 0.82, blue: 0.52)
        case .slime: Color(red: 0.45, green: 0.76, blue: 1.0)
        }
    }

    var shadeColor: Color {
        switch self {
        case .cat: Color(red: 0.9, green: 0.5, blue: 0.25)
        case .dog: Color(red: 0.6, green: 0.42, blue: 0.28)
        case .bunny: Color(red: 0.95, green: 0.7, blue: 0.8)
        case .dragon: Color(red: 0.25, green: 0.6, blue: 0.35)
        case .slime: Color(red: 0.25, green: 0.5, blue: 0.9)
        }
    }
}
