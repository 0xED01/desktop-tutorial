import ImageIO
import Photos
import UIKit
import UniformTypeIdentifiers

enum PhotoSaver {
    enum SaveError: LocalizedError {
        case denied, encoding
        var errorDescription: String? {
            switch self {
            case .denied: "没有相册写入权限，请在“设置”中允许添加照片。"
            case .encoding: "照片编码失败。"
            }
        }
    }

    /// 保存到相册，并保留原图的 EXIF（拍摄参数、时间、GPS 等）。
    static func save(_ image: UIImage, metadata: [String: Any]) async throws {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { throw SaveError.denied }
        guard let data = jpegData(image, metadata: metadata) else { throw SaveError.encoding }
        try await PHPhotoLibrary.shared().performChanges {
            PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
        }
    }

    static func jpegData(_ image: UIImage, metadata: [String: Any], quality: Double = 0.95) -> Data? {
        guard let cgImage = image.cgImage else { return nil }
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }

        var properties = metadata
        for key in [kCGImagePropertyPixelWidth, kCGImagePropertyPixelHeight, kCGImagePropertyDepth] {
            properties.removeValue(forKey: key as String)
        }
        // 渲染出来的图已经是正向的
        properties[kCGImagePropertyOrientation as String] = 1
        if var tiff = properties[kCGImagePropertyTIFFDictionary as String] as? [String: Any] {
            tiff[kCGImagePropertyTIFFOrientation as String] = 1
            properties[kCGImagePropertyTIFFDictionary as String] = tiff
        }
        if var exif = properties[kCGImagePropertyExifDictionary as String] as? [String: Any] {
            exif[kCGImagePropertyExifPixelXDimension as String] = cgImage.width
            exif[kCGImagePropertyExifPixelYDimension as String] = cgImage.height
            properties[kCGImagePropertyExifDictionary as String] = exif
        }
        properties[kCGImageDestinationLossyCompressionQuality as String] = quality

        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return data as Data
    }
}
