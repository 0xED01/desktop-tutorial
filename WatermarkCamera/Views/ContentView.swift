import AVFoundation
import PhotosUI
import SwiftUI

struct ContentView: View {
    @StateObject private var camera = CameraModel()
    @StateObject private var settings = WatermarkSettings()
    @State private var review: CapturedPhoto?
    @State private var showSettings = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var focusPoint: CGPoint?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            switch camera.status {
            case .denied:
                PermissionView()
            case .failed:
                ContentUnavailableView("相机不可用", systemImage: "camera.fill",
                                       description: Text("当前设备没有可用的摄像头（模拟器里可以用左下角从相册导入）。"))
                    .overlay(alignment: .bottom) { bottomControls.padding(.bottom, 24) }
            default:
                cameraScreen
            }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden()
        .onAppear { camera.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { camera.start() } else if phase == .background { camera.stop() }
        }
        .onReceive(camera.$captured.compactMap { $0 }) { review = $0 }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self),
                   let photo = CapturedPhoto(data: data) {
                    review = photo
                }
                pickerItem = nil
            }
        }
        .fullScreenCover(item: $review) { photo in
            PhotoReviewView(photo: photo, settings: settings)
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(settings: settings)
                .presentationDetents([.medium, .large])
        }
    }

    private var cameraScreen: some View {
        VStack(spacing: 0) {
            topBar
                .padding(.horizontal, 20)
                .frame(height: 60)

            viewfinder

            if settings.style == .bar {
                WatermarkBadge(settings: settings)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
            }

            Spacer(minLength: 0)

            if camera.zoomOptions.count > 1 {
                zoomPicker.padding(.bottom, 18)
            }
            bottomControls.padding(.bottom, 20)
        }
    }

    private var topBar: some View {
        HStack {
            GlassIconButton(systemName: flashIcon) { camera.cycleFlash() }
                .opacity(camera.isFrontCamera ? 0.4 : 1)
            Spacer()
            Text(settings.style.title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .liquidGlass(in: Capsule())
                .onTapGesture {
                    withAnimation(.snappy) { settings.style = settings.style == .floating ? .bar : .floating }
                }
            Spacer()
            GlassIconButton(systemName: "slider.horizontal.3") { showSettings = true }
        }
    }

    private var viewfinder: some View {
        ZStack(alignment: .bottom) {
            CameraPreview(camera: camera)
                .onTapGesture(coordinateSpace: .local) { location in
                    camera.focus(at: location)
                    withAnimation(.easeOut(duration: 0.2)) { focusPoint = location }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        withAnimation { if focusPoint == location { focusPoint = nil } }
                    }
                }
                .overlay {
                    if let focusPoint {
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(.yellow, lineWidth: 1.5)
                            .frame(width: 72, height: 72)
                            .position(focusPoint)
                            .allowsHitTesting(false)
                    }
                }

            if settings.style == .floating {
                WatermarkBadge(settings: settings)
                    .padding(12)
                    .allowsHitTesting(false)
            }
        }
        .aspectRatio(3 / 4, contentMode: .fit)
        .clipped()
        .overlay {
            Color.black.opacity(camera.shutterBlink ? 1 : 0)
                .animation(.easeOut(duration: 0.12), value: camera.shutterBlink)
                .allowsHitTesting(false)
        }
    }

    private var zoomPicker: some View {
        HStack(spacing: 6) {
            ForEach(camera.zoomOptions, id: \.self) { value in
                let selected = camera.zoom == value
                Button {
                    withAnimation(.snappy) { camera.setZoom(value) }
                } label: {
                    Text(zoomLabel(value) + (selected ? "×" : ""))
                        .font(.system(size: selected ? 13 : 11, weight: .bold))
                        .foregroundStyle(selected ? .yellow : .white)
                        .frame(width: 38, height: 38)
                        .background(Circle().fill(.black.opacity(selected ? 0.35 : 0.2)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(5)
        .liquidGlass(in: Capsule())
    }

    private var bottomControls: some View {
        HStack {
            PhotosPicker(selection: $pickerItem, matching: .images) {
                Image(systemName: "photo.on.rectangle")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .liquidGlass(in: Circle(), interactive: true)

            Spacer()

            ShutterButton(isBusy: camera.isCapturing) { camera.capturePhoto() }
                .disabled(camera.status != .running)

            Spacer()

            GlassIconButton(systemName: "arrow.triangle.2.circlepath", size: 56) { camera.switchCamera() }
                .disabled(camera.status != .running)
        }
        .padding(.horizontal, 32)
    }

    private var flashIcon: String {
        switch camera.flashMode {
        case .auto: "bolt.badge.automatic.fill"
        case .on: "bolt.fill"
        default: "bolt.slash.fill"
        }
    }

    private func zoomLabel(_ value: CGFloat) -> String {
        value < 1 ? String(format: "%.1f", value).replacingOccurrences(of: "0.", with: ".") : "\(Int(value))"
    }
}

struct ShutterButton: View {
    let isBusy: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().stroke(.white, lineWidth: 4).frame(width: 78, height: 78)
                Circle().fill(.white).frame(width: 64, height: 64)
                    .scaleEffect(isBusy ? 0.85 : 1)
                if isBusy { ProgressView().tint(.black) }
            }
            .animation(.snappy(duration: 0.15), value: isBusy)
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.impact(weight: .medium), trigger: isBusy) { _, new in new }
    }
}

struct PermissionView: View {
    var body: some View {
        ContentUnavailableView {
            Label("需要相机权限", systemImage: "camera.fill")
        } description: {
            Text("请在“设置 > 水印相机”中允许访问相机。")
        } actions: {
            Button("打开设置") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
