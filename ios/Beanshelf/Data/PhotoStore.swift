import UIKit
import ImageIO

/// Photo pipeline: import (downscale to <=1600px, orientation-normalised,
/// recompress) into Documents/photos, plus a cached downsampling decoder for
/// showing photos at grid/detail sizes. Stores and returns bare filenames —
/// the iOS app container moves between installs, so absolute paths would rot.
enum PhotoStore {

    static let maxImportDim: CGFloat = 1600
    static let jpegQuality: CGFloat = 0.88

    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.totalCostLimit = 128 * 1024 * 1024
        return c
    }()

    static var photosDir: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("photos", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func url(for file: String?) -> URL? {
        guard let file, !file.isEmpty else { return nil }
        return photosDir.appendingPathComponent(URL(fileURLWithPath: file).lastPathComponent)
    }

    /// Import a picked/captured image: returns the stored filename.
    static func importImage(_ image: UIImage) async -> String? {
        await Task.detached(priority: .userInitiated) {
            let pixelW = image.size.width * image.scale
            let pixelH = image.size.height * image.scale
            let scale = min(1, maxImportDim / max(pixelW, pixelH))
            let target = CGSize(width: pixelW * scale, height: pixelH * scale)

            // Drawing through a renderer both downscales and bakes the EXIF
            // orientation into the pixels (the Android decodeUpright equivalent).
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            format.opaque = true
            let upright = UIGraphicsImageRenderer(size: target, format: format).image { _ in
                image.draw(in: CGRect(origin: .zero, size: target))
            }
            guard let data = upright.jpegData(compressionQuality: jpegQuality) else { return nil }
            let name = "\(UUID().uuidString).jpg"
            do {
                try data.write(to: photosDir.appendingPathComponent(name), options: .atomic)
                return name
            } catch {
                return nil
            }
        }.value
    }

    static func delete(_ file: String?) {
        guard let url = url(for: file) else { return }
        try? FileManager.default.removeItem(at: url)
    }

    /// Decode a stored photo at roughly `targetWidth` pixels wide, memoised.
    static func loadScaled(file: String?, targetWidth: CGFloat) async -> UIImage? {
        guard let url = url(for: file) else { return nil }
        let key = "\(url.lastPathComponent)@\(Int(targetWidth))" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        return await Task.detached(priority: .userInitiated) {
            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            let options: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: max(targetWidth, 64) * 2,
                kCGImageSourceCreateThumbnailWithTransform: true,
            ]
            guard let cg = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
                return nil
            }
            let image = UIImage(cgImage: cg)
            cache.setObject(image, forKey: key, cost: cg.bytesPerRow * cg.height)
            return image
        }.value
    }
}
