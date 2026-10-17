import ImageIO
import UIKit

/// 一张待加水印的照片：可能来自相机，也可能来自相册。
struct CapturedPhoto: Identifiable {
    let id = UUID()
    let image: UIImage
    let metadata: [String: Any]
    let info: PhotoInfo

    init?(data: Data, fallbackDate: Date = Date()) {
        guard let image = UIImage(data: data) else { return nil }
        let source = CGImageSourceCreateWithData(data as CFData, nil)
        let metadata = source.flatMap { CGImageSourceCopyPropertiesAtIndex($0, 0, nil) as? [String: Any] } ?? [:]
        self.image = image
        self.metadata = metadata
        self.info = PhotoInfo(metadata: metadata, fallbackDate: fallbackDate)
    }
}

/// 从 EXIF / TIFF 中读取的拍摄信息。
struct PhotoInfo {
    var date: Date
    var cameraMake: String?
    var cameraModel: String?
    var focalLength35mm: Double?
    var fNumber: Double?
    var exposureTime: Double?
    var iso: Double?

    init(metadata: [String: Any], fallbackDate: Date) {
        let exif = metadata[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        let tiff = metadata[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]

        focalLength35mm = (exif[kCGImagePropertyExifFocalLenIn35mmFilm as String] as? NSNumber)?.doubleValue
        fNumber = (exif[kCGImagePropertyExifFNumber as String] as? NSNumber)?.doubleValue
        exposureTime = (exif[kCGImagePropertyExifExposureTime as String] as? NSNumber)?.doubleValue
        iso = (exif[kCGImagePropertyExifISOSpeedRatings as String] as? [NSNumber])?.first?.doubleValue
        cameraMake = tiff[kCGImagePropertyTIFFMake as String] as? String
        cameraModel = tiff[kCGImagePropertyTIFFModel as String] as? String

        if let text = exif[kCGImagePropertyExifDateTimeOriginal as String] as? String,
           let date = Self.exifDateFormatter.date(from: text) {
            self.date = date
        } else {
            self.date = fallbackDate
        }
    }

    /// 例如 “24mm  ƒ/1.78  1/120s  ISO 64”
    var parameterText: String? {
        var parts: [String] = []
        if let focalLength35mm { parts.append("\(Int(focalLength35mm.rounded()))mm") }
        if let fNumber { parts.append("ƒ/" + Self.trimmed(fNumber)) }
        if let exposureTime, exposureTime > 0 {
            parts.append(exposureTime < 1 ? "1/\(Int((1 / exposureTime).rounded()))s" : Self.trimmed(exposureTime) + "s")
        }
        if let iso { parts.append("ISO \(Int(iso))") }
        return parts.isEmpty ? nil : parts.joined(separator: "  ")
    }

    private static func trimmed(_ value: Double) -> String {
        String(format: "%.2f", value)
            .replacingOccurrences(of: #"\.?0+$"#, with: "", options: .regularExpression)
    }

    private static let exifDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return formatter
    }()
}
