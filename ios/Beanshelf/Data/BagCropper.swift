import UIKit
import Vision
import CoreImage
import os

/// Isolates the bag in a photo. Preferred path: Vision foreground-instance
/// segmentation — removes the background entirely and saves a transparent PNG
/// cutout (filename changes .jpg → .png). Fallback (segmentation failed or
/// implausible): rectangular crop to the salient object's box, same JPEG file.
///
/// Returns the filename to display — callers must use the returned name, and
/// must call this BEFORE handing any filename to the UI so the decode cache
/// stays clean.
enum BagCropper {

    private static let log = Logger(subsystem: "dev.adamsjack.beanshelf", category: "BagCropper")
    private static let padding: CGFloat = 0.05
    private static let jpegQuality: CGFloat = 0.88

    /// Cutout PNGs render shelf-style (no card, ground shadow); JPEGs get the card look.
    static func isCutout(_ file: String?) -> Bool { file?.hasSuffix(".png") == true }

    static func cutOutBag(file: String) async -> String {
        guard let url = PhotoStore.url(for: file) else { return file }
        return await Task.detached(priority: .userInitiated) {
            guard let image = UIImage(contentsOfFile: url.path),
                  let cg = image.cgImage else { return file }

            if let cutout = segmentSubject(cg) {
                // Plausibility: a bag fills a decent chunk of a deliberate photo.
                if CGFloat(cutout.width) >= CGFloat(cg.width) * 0.2
                    || CGFloat(cutout.height) >= CGFloat(cg.height) * 0.2 {
                    let pngName = (file as NSString).deletingPathExtension + ".png"
                    if let pngURL = PhotoStore.url(for: pngName),
                       let data = UIImage(cgImage: cutout).pngData() {
                        do {
                            try data.write(to: pngURL, options: .atomic)
                            if pngName != file { PhotoStore.delete(file) }
                            log.debug("cutout saved \(cutout.width)x\(cutout.height)")
                            return pngName
                        } catch {
                            log.debug("cutout write failed: \(error)")
                        }
                    }
                }
            }
            log.debug("segmentation unavailable/implausible — rectangular fallback")
            rectangleCrop(cg, url: url)
            return file
        }.value
    }

    /// Foreground-with-alpha image from instance segmentation, already cropped
    /// to the subject's extent, or nil if unavailable.
    private static func segmentSubject(_ cg: CGImage) -> CGImage? {
        let request = VNGenerateForegroundInstanceMaskRequest()
        let handler = VNImageRequestHandler(cgImage: cg, options: [:])
        guard (try? handler.perform([request])) != nil,
              let result = request.results?.first,
              !result.allInstances.isEmpty,
              let buffer = try? result.generateMaskedImage(
                ofInstances: result.allInstances,
                from: handler,
                croppedToInstancesExtent: true
              )
        else { return nil }
        let ci = CIImage(cvPixelBuffer: buffer)
        return CIContext().createCGImage(ci, from: ci.extent)
    }

    /// Fallback: crop the JPEG in place to the salient object's bounding box.
    private static func rectangleCrop(_ cg: CGImage, url: URL) {
        let request = VNGenerateObjectnessBasedSaliencyImageRequest()
        let handler = VNImageRequestHandler(cgImage: cg, options: [:])
        guard (try? handler.perform([request])) != nil,
              let observation = request.results?.first,
              let salient = observation.salientObjects,
              let box = salient.max(by: { $0.confidence < $1.confidence })
        else { return }

        // Vision boundingBox is normalised, origin bottom-left → pixel rect.
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        let raw = CGRect(
            x: box.boundingBox.minX * w,
            y: (1 - box.boundingBox.maxY) * h,
            width: box.boundingBox.width * w,
            height: box.boundingBox.height * h
        )
        let padX = raw.width * padding
        let padY = raw.height * padding
        let rect = CGRect(
            x: max(0, raw.minX - padX),
            y: max(0, raw.minY - padY),
            width: min(w, raw.maxX + padX) - max(0, raw.minX - padX),
            height: min(h, raw.maxY + padY) - max(0, raw.minY - padY)
        )

        if rect.width < w * 0.25 || rect.height < h * 0.25 { return }
        if rect.width > w * 0.95 && rect.height > h * 0.95 { return }

        guard let croppedCG = cg.cropping(to: rect),
              let data = UIImage(cgImage: croppedCG).jpegData(compressionQuality: jpegQuality)
        else { return }
        try? data.write(to: url, options: .atomic)
    }
}
