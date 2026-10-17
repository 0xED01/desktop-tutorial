import SwiftUI

struct PhotoReviewView: View {
    let photo: CapturedPhoto
    @ObservedObject var settings: WatermarkSettings
    @Environment(\.dismiss) private var dismiss

    @State private var rendered: UIImage?
    @State private var isSaving = false
    @State private var saved = false
    @State private var errorMessage: String?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let rendered {
                Image(uiImage: rendered)
                    .resizable()
                    .scaledToFit()
                    .padding(.horizontal, 8)
                    .transition(.opacity)
            } else {
                ProgressView().tint(.white)
            }
        }
        .safeAreaInset(edge: .top) {
            HStack {
                GlassIconButton(systemName: "xmark") { dismiss() }
                Spacer()
                Picker("样式", selection: $settings.style) {
                    ForEach(WatermarkStyle.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .frame(width: 210)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 16) {
                if let rendered {
                    ShareLink(item: Image(uiImage: rendered), preview: SharePreview("水印照片", image: Image(uiImage: rendered))) {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(.white)
                            .frame(width: 56, height: 56)
                    }
                    .liquidGlass(in: Circle(), interactive: true)
                }

                Button(action: save) {
                    HStack(spacing: 8) {
                        if isSaving {
                            ProgressView().tint(.white)
                        } else {
                            Image(systemName: saved ? "checkmark" : "square.and.arrow.down")
                        }
                        Text(saved ? "已保存到相册" : "保存到相册")
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .liquidGlass(in: Capsule(), interactive: true)
                .disabled(rendered == nil || isSaving || saved)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .preferredColorScheme(.dark)
        .task(id: settings.options) {
            saved = false
            let image = photo.image
            let options = settings.options
            let content = WatermarkContent(info: photo.info, options: options)
            let result = await Task.detached(priority: .userInitiated) {
                WatermarkRenderer.render(image, content: content, options: options)
            }.value
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.2)) { rendered = result }
        }
        .alert("保存失败", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("好", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
        }
    }

    private func save() {
        guard let rendered else { return }
        isSaving = true
        Task {
            do {
                try await PhotoSaver.save(rendered, metadata: photo.metadata)
                saved = true
            } catch {
                errorMessage = error.localizedDescription
            }
            isSaving = false
        }
    }
}
