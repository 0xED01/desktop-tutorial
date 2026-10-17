import SwiftUI

struct SettingsView: View {
    @ObservedObject var settings: WatermarkSettings
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    WatermarkBadge(settings: settings)
                        .listRowBackground(
                            LinearGradient(colors: [.orange, .pink, .purple], startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                }

                Section("样式") {
                    Picker("水印样式", selection: $settings.style) {
                        ForEach(WatermarkStyle.allCases) { Text($0.title).tag($0) }
                    }
                    Picker("日期格式", selection: $settings.dateStyle) {
                        ForEach(DateStyle.allCases) { style in
                            Text(WatermarkContent(info: PhotoInfo(metadata: [:], fallbackDate: Date()), options: settings.options)
                                .dateText(style))
                                .tag(style)
                        }
                    }
                    Toggle("显示时间", isOn: $settings.showTime)
                    Toggle("显示拍摄参数", isOn: $settings.showParameters)
                }

                Section {
                    TextField(DeviceModel.current, text: $settings.customModel)
                        .autocorrectionDisabled()
                } header: {
                    Text("型号")
                } footer: {
                    Text("留空则自动识别：优先读取照片 EXIF 中的机型，否则使用本机型号（\(DeviceModel.machineIdentifier)）。")
                }
            }
            .navigationTitle("水印设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
