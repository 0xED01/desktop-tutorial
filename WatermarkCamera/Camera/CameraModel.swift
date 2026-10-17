import AVFoundation
import UIKit

/// 管理 AVCaptureSession：权限、前后摄切换、变焦、对焦、闪光灯和拍照。
final class CameraModel: NSObject, ObservableObject {
    enum Status { case idle, running, denied, failed }

    @Published private(set) var status: Status = .idle
    @Published private(set) var isCapturing = false
    @Published private(set) var isFrontCamera = false
    @Published private(set) var zoomOptions: [CGFloat] = [1]
    @Published private(set) var zoom: CGFloat = 1
    @Published private(set) var shutterBlink = false
    @Published var flashMode: AVCaptureDevice.FlashMode = .off
    @Published var captured: CapturedPhoto?

    let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "WatermarkCamera.session")
    private let photoOutput = AVCapturePhotoOutput()
    private var videoInput: AVCaptureDeviceInput?
    private var zoomBase: CGFloat = 1
    private var isConfigured = false

    // 以下属性只在主线程访问
    private weak var previewLayer: AVCaptureVideoPreviewLayer?
    private var activeDevice: AVCaptureDevice?
    private var rotationCoordinator: AVCaptureDevice.RotationCoordinator?
    private var previewRotationObservation: NSKeyValueObservation?

    // MARK: - 生命周期

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted { self.startSession() } else { self.status = .denied }
                }
            }
        default:
            status = .denied
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    func attach(_ layer: AVCaptureVideoPreviewLayer) {
        previewLayer = layer
        if let activeDevice { makeRotationCoordinator(for: activeDevice) }
    }

    private func startSession() {
        sessionQueue.async {
            if !self.isConfigured { self.configureSession() }
            guard self.isConfigured else { return }
            if !self.session.isRunning { self.session.startRunning() }
            DispatchQueue.main.async {
                self.status = .running
                self.updatePreviewRotation()
            }
        }
    }

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .photo
        guard let device = Self.camera(for: .back),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input),
              session.canAddOutput(photoOutput) else {
            session.commitConfiguration()
            DispatchQueue.main.async { self.status = .failed }
            return
        }
        session.addInput(input)
        session.addOutput(photoOutput)
        photoOutput.maxPhotoQualityPrioritization = .quality
        videoInput = input
        session.commitConfiguration()

        // 提交后 activeFormat 才确定，再按它设置照片尺寸
        applyDeviceSettings(device)
        isConfigured = true
    }

    private static func camera(for position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let types: [AVCaptureDevice.DeviceType] = position == .back
            ? [.builtInTripleCamera, .builtInDualWideCamera, .builtInDualCamera, .builtInWideAngleCamera]
            : [.builtInTrueDepthCamera, .builtInWideAngleCamera]
        let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: types, mediaType: .video, position: position)
        for type in types {
            if let device = discovery.devices.first(where: { $0.deviceType == type }) { return device }
        }
        return nil
    }

    /// 在 sessionQueue 上、commitConfiguration 之后调用：设置照片尺寸、对焦模式和变焦档位。
    private func applyDeviceSettings(_ device: AVCaptureDevice) {
        // 选不超过约 1200 万像素的最大尺寸，避免 4800 万像素合成水印时内存过高
        let dimensions = device.activeFormat.supportedMaxPhotoDimensions
        let pixels = { (d: CMVideoDimensions) in Int(d.width) * Int(d.height) }
        if let best = dimensions.filter({ pixels($0) <= 13_000_000 }).max(by: { pixels($0) < pixels($1) }) ?? dimensions.first {
            photoOutput.maxPhotoDimensions = best
        }

        // 虚拟多摄设备的 videoZoomFactor = 1 对应超广角，换算成用户看到的 0.5x / 1x / 2x
        let switchOver = device.virtualDeviceSwitchOverVideoZoomFactors.map { CGFloat($0.doubleValue) }
        let constituents = device.constituentDevices.map(\.deviceType)
        let hasUltraWide = constituents.contains(.builtInUltraWideCamera)
        let base: CGFloat = hasUltraWide ? (switchOver.first ?? 1) : 1
        var options: [CGFloat] = hasUltraWide ? [0.5, 1, 2] : [1, 2]
        if constituents.contains(.builtInTelephotoCamera), let tele = switchOver.last {
            let value = (tele / base).rounded()
            if value > 2 { options.append(value) }
        }
        if device.position == .front { options = [1] }

        if (try? device.lockForConfiguration()) != nil {
            device.videoZoomFactor = min(max(base, device.minAvailableVideoZoomFactor), device.maxAvailableVideoZoomFactor)
            if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
            device.unlockForConfiguration()
        }
        zoomBase = base

        DispatchQueue.main.async {
            self.zoomOptions = options
            self.zoom = 1
            self.isFrontCamera = device.position == .front
            self.makeRotationCoordinator(for: device)
        }
    }

    // MARK: - 方向

    private func makeRotationCoordinator(for device: AVCaptureDevice) {
        activeDevice = device
        let coordinator = AVCaptureDevice.RotationCoordinator(device: device, previewLayer: previewLayer)
        rotationCoordinator = coordinator
        previewRotationObservation = coordinator.observe(\.videoRotationAngleForHorizonLevelPreview, options: [.new]) { [weak self] _, _ in
            DispatchQueue.main.async { self?.updatePreviewRotation() }
        }
        updatePreviewRotation()
    }

    private func updatePreviewRotation() {
        guard let coordinator = rotationCoordinator, let connection = previewLayer?.connection else { return }
        let angle = coordinator.videoRotationAngleForHorizonLevelPreview
        if connection.isVideoRotationAngleSupported(angle) { connection.videoRotationAngle = angle }
    }

    // MARK: - 操作

    func switchCamera() {
        sessionQueue.async {
            guard let current = self.videoInput else { return }
            let position: AVCaptureDevice.Position = current.device.position == .back ? .front : .back
            guard let device = Self.camera(for: position),
                  let input = try? AVCaptureDeviceInput(device: device) else { return }

            self.session.beginConfiguration()
            self.session.removeInput(current)
            if self.session.canAddInput(input) {
                self.session.addInput(input)
                self.videoInput = input
            } else {
                self.session.addInput(current)
            }
            self.session.commitConfiguration()
            self.applyDeviceSettings(self.videoInput?.device ?? current.device)
        }
    }

    func setZoom(_ value: CGFloat) {
        zoom = value
        sessionQueue.async {
            guard let device = self.videoInput?.device else { return }
            let factor = min(max(value * self.zoomBase, device.minAvailableVideoZoomFactor), device.maxAvailableVideoZoomFactor)
            guard (try? device.lockForConfiguration()) != nil else { return }
            device.ramp(toVideoZoomFactor: factor, withRate: 12)
            device.unlockForConfiguration()
        }
    }

    /// layerPoint 为预览图层坐标。
    func focus(at layerPoint: CGPoint) {
        guard let point = previewLayer?.captureDevicePointConverted(fromLayerPoint: layerPoint) else { return }
        sessionQueue.async {
            guard let device = self.videoInput?.device, (try? device.lockForConfiguration()) != nil else { return }
            if device.isFocusPointOfInterestSupported, device.isFocusModeSupported(.continuousAutoFocus) {
                device.focusPointOfInterest = point
                device.focusMode = .continuousAutoFocus
            }
            if device.isExposurePointOfInterestSupported, device.isExposureModeSupported(.continuousAutoExposure) {
                device.exposurePointOfInterest = point
                device.exposureMode = .continuousAutoExposure
            }
            device.unlockForConfiguration()
        }
    }

    func cycleFlash() {
        switch flashMode {
        case .off: flashMode = .auto
        case .auto: flashMode = .on
        default: flashMode = .off
        }
    }

    func capturePhoto() {
        guard !isCapturing, status == .running else { return }
        isCapturing = true
        let angle = rotationCoordinator?.videoRotationAngleForHorizonLevelCapture
        let flash = flashMode

        sessionQueue.async {
            if let connection = self.photoOutput.connection(with: .video),
               let angle, connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
            let settings = AVCapturePhotoSettings()
            settings.maxPhotoDimensions = self.photoOutput.maxPhotoDimensions
            settings.photoQualityPrioritization = .balanced
            if self.photoOutput.supportedFlashModes.contains(flash) { settings.flashMode = flash }
            self.photoOutput.capturePhoto(with: settings, delegate: self)
        }
    }
}

extension CameraModel: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, willCapturePhotoFor resolvedSettings: AVCaptureResolvedPhotoSettings) {
        DispatchQueue.main.async {
            self.shutterBlink = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) { self.shutterBlink = false }
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let result = error == nil ? photo.fileDataRepresentation().flatMap { CapturedPhoto(data: $0) } : nil
        DispatchQueue.main.async {
            if let result { self.captured = result }
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishCaptureFor resolvedSettings: AVCaptureResolvedPhotoSettings, error: Error?) {
        DispatchQueue.main.async { self.isCapturing = false }
    }
}
