import UIKit
import CoreImage

/// QR encoder for the shareable profile link (CoreImage — no dependency).
/// Roast-on-parchment to match the app, same as the Android zxing version.
enum Qr {
    static func encode(_ text: String, sizePx: CGFloat = 640) -> UIImage? {
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(text.data(using: .utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }
        let colored = output.applyingFilter("CIFalseColor", parameters: [
            "inputColor0": CIColor(red: 0x17 / 255, green: 0x10 / 255, blue: 0x0B / 255), // Roast
            "inputColor1": CIColor(red: 0xF0 / 255, green: 0xE4 / 255, blue: 0xD2 / 255), // Parchment
        ])
        let scale = sizePx / colored.extent.width
        let scaled = colored.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        guard let cg = CIContext().createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
