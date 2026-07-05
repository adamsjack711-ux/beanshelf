import SwiftUI
import UIKit

/// Full-screen manual crop: drag inside the frame to move it, drag a corner to
/// resize, Rotate ⟳ turns in 90° steps (camera EXIF is sometimes wrong — the
/// button makes straightening deterministic). Crop writes a NEW file (old one
/// is deleted) so every cache and view refreshes off the filename change.
/// PNG stays PNG (cutout alpha kept).
struct CropEditorView: View {
    let file: String
    let onDone: (String?) -> Void

    @State private var loaded: UIImage?
    @State private var rotation = 0 // 0/90/180/270, clockwise
    @State private var crop: CGRect = .zero
    @State private var imageRect: CGRect = .zero
    @State private var dragMode: String?
    @State private var lastTranslation: CGSize = .zero
    @State private var saving = false

    private let touchRadius: CGFloat = 36
    private let minSide: CGFloat = 56

    private var bitmap: UIImage? {
        guard let loaded else { return nil }
        return rotation == 0 ? loaded : loaded.rotatedClockwise(steps: rotation / 90)
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.96).ignoresSafeArea()
            VStack(spacing: 0) {
                GeometryReader { geo in
                    ZStack {
                        if let bitmap {
                            Image(uiImage: bitmap)
                                .resizable()
                                .scaledToFit()
                                .frame(width: geo.size.width, height: geo.size.height)
                        }
                        Canvas { context, size in
                            guard imageRect != .zero else { return }
                            let dim = Color.black.opacity(0.55)
                            context.fill(Path(CGRect(x: 0, y: 0, width: size.width, height: crop.minY)), with: .color(dim))
                            context.fill(Path(CGRect(x: 0, y: crop.maxY, width: size.width, height: size.height - crop.maxY)), with: .color(dim))
                            context.fill(Path(CGRect(x: 0, y: crop.minY, width: crop.minX, height: crop.height)), with: .color(dim))
                            context.fill(Path(CGRect(x: crop.maxX, y: crop.minY, width: size.width - crop.maxX, height: crop.height)), with: .color(dim))
                            context.stroke(Path(crop), with: .color(Palette.crema), lineWidth: 2)
                            for corner in [
                                CGPoint(x: crop.minX, y: crop.minY), CGPoint(x: crop.maxX, y: crop.minY),
                                CGPoint(x: crop.minX, y: crop.maxY), CGPoint(x: crop.maxX, y: crop.maxY),
                            ] {
                                context.fill(
                                    Path(ellipseIn: CGRect(x: corner.x - 7, y: corner.y - 7, width: 14, height: 14)),
                                    with: .color(Palette.crema)
                                )
                            }
                        }
                        .contentShape(Rectangle())
                        .gesture(dragGesture)
                    }
                    .onAppear { recomputeImageRect(container: geo.size) }
                    .onChange(of: geo.size) { _, size in recomputeImageRect(container: size) }
                    .onChange(of: rotation) { _, _ in recomputeImageRect(container: geo.size) }
                    .onChange(of: loaded == nil) { _, _ in recomputeImageRect(container: geo.size) }
                }

                HStack(spacing: 4) {
                    Button("Cancel") { onDone(nil) }
                        .foregroundStyle(Palette.dim)
                    Button("Rotate ⟳") { rotation = (rotation + 90) % 360 }
                        .foregroundStyle(Palette.crema)
                    Button("Reset") { crop = imageRect; rotation = 0 }
                        .foregroundStyle(Palette.parchment)
                        .padding(.leading, 4)
                    Spacer()
                    Button {
                        guard !saving, let bitmap else { return }
                        saving = true
                        let cropRect = crop
                        let fitRect = imageRect
                        let force = rotation != 0
                        Task {
                            let newFile = await applyCrop(bitmap: bitmap, cropRect: cropRect, fitRect: fitRect, force: force)
                            onDone(newFile)
                        }
                    } label: {
                        Text("Save")
                            .font(Type.labelLarge)
                            .foregroundStyle(Palette.roast)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 10)
                            .background(Palette.crema, in: Capsule())
                    }
                }
                .font(Type.labelLarge)
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
        }
        .task {
            guard let url = PhotoStore.url(for: file) else { return }
            loaded = await Task.detached { UIImage(contentsOfFile: url.path) }.value
        }
    }

    /// Fit-rect of the displayed image inside the container.
    private func recomputeImageRect(container: CGSize) {
        guard let bitmap, container != .zero else { return }
        let scale = min(container.width / bitmap.size.width, container.height / bitmap.size.height)
        let w = bitmap.size.width * scale
        let h = bitmap.size.height * scale
        imageRect = CGRect(x: (container.width - w) / 2, y: (container.height - h) / 2, width: w, height: h)
        crop = imageRect
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if dragMode == nil {
                    lastTranslation = .zero
                    let pos = value.startLocation
                    let corners = [
                        "tl": CGPoint(x: crop.minX, y: crop.minY),
                        "tr": CGPoint(x: crop.maxX, y: crop.minY),
                        "bl": CGPoint(x: crop.minX, y: crop.maxY),
                        "br": CGPoint(x: crop.maxX, y: crop.maxY),
                    ]
                    dragMode = corners.first { hypot($0.value.x - pos.x, $0.value.y - pos.y) < touchRadius }?.key
                        ?? (crop.contains(pos) ? "move" : "")
                }
                let dx = value.translation.width - lastTranslation.width
                let dy = value.translation.height - lastTranslation.height
                lastTranslation = value.translation
                applyDrag(dx: dx, dy: dy)
            }
            .onEnded { _ in
                dragMode = nil
                lastTranslation = .zero
            }
    }

    private func applyDrag(dx: CGFloat, dy: CGFloat) {
        let r = crop
        switch dragMode {
        case "move":
            let cx = min(max(dx, imageRect.minX - r.minX), imageRect.maxX - r.maxX)
            let cy = min(max(dy, imageRect.minY - r.minY), imageRect.maxY - r.maxY)
            crop = r.offsetBy(dx: cx, dy: cy)
        case "tl":
            let nl = min(max(r.minX + dx, imageRect.minX), r.maxX - minSide)
            let nt = min(max(r.minY + dy, imageRect.minY), r.maxY - minSide)
            crop = CGRect(x: nl, y: nt, width: r.maxX - nl, height: r.maxY - nt)
        case "tr":
            let nr = min(max(r.maxX + dx, r.minX + minSide), imageRect.maxX)
            let nt = min(max(r.minY + dy, imageRect.minY), r.maxY - minSide)
            crop = CGRect(x: r.minX, y: nt, width: nr - r.minX, height: r.maxY - nt)
        case "bl":
            let nl = min(max(r.minX + dx, imageRect.minX), r.maxX - minSide)
            let nb = min(max(r.maxY + dy, r.minY + minSide), imageRect.maxY)
            crop = CGRect(x: nl, y: r.minY, width: r.maxX - nl, height: nb - r.minY)
        case "br":
            let nr = min(max(r.maxX + dx, r.minX + minSide), imageRect.maxX)
            let nb = min(max(r.maxY + dy, r.minY + minSide), imageRect.maxY)
            crop = CGRect(x: r.minX, y: r.minY, width: nr - r.minX, height: nb - r.minY)
        default:
            break
        }
    }

    /// Maps the display-space frame back to bitmap pixels and writes a new file.
    private func applyCrop(bitmap: UIImage, cropRect: CGRect, fitRect: CGRect, force: Bool) async -> String? {
        await Task.detached(priority: .userInitiated) { () -> String? in
            guard let cg = bitmap.cgImage else { return nil }
            let scale = CGFloat(cg.width) / fitRect.width
            let l = Int(((cropRect.minX - fitRect.minX) * scale).rounded())
            let t = Int(((cropRect.minY - fitRect.minY) * scale).rounded())
            let w = Int((cropRect.width * scale).rounded())
            let h = Int((cropRect.height * scale).rounded())
            let cl = min(max(l, 0), cg.width - 1)
            let ct = min(max(t, 0), cg.height - 1)
            let cw = min(max(w, 1), cg.width - cl)
            let ch = min(max(h, 1), cg.height - ct)
            // No-op crop (and no rotation) → keep the original file.
            if !force && cl == 0 && ct == 0 && abs(cw - cg.width) < 4 && abs(ch - cg.height) < 4 {
                return file
            }
            guard let cropped = cg.cropping(to: CGRect(x: cl, y: ct, width: cw, height: ch)) else { return nil }
            let isPng = file.hasSuffix(".png")
            let out = UIImage(cgImage: cropped)
            guard let data = isPng ? out.pngData() : out.jpegData(compressionQuality: 0.88) else { return nil }
            let newFile = "\(UUID().uuidString).\(isPng ? "png" : "jpg")"
            guard let newURL = PhotoStore.url(for: newFile) else { return nil }
            do {
                try data.write(to: newURL, options: .atomic)
                PhotoStore.delete(file)
                return newFile
            } catch {
                return nil
            }
        }.value
    }
}

extension UIImage {
    /// Bakes a clockwise rotation of 90° × steps into the pixels.
    func rotatedClockwise(steps: Int) -> UIImage {
        let n = ((steps % 4) + 4) % 4
        guard n != 0, let cg = cgImage else { return self }
        let orientation: UIImage.Orientation = [.up, .right, .down, .left][n]
        let oriented = UIImage(cgImage: cg, scale: 1, orientation: orientation)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: oriented.size, format: format).image { _ in
            oriented.draw(in: CGRect(origin: .zero, size: oriented.size))
        }
    }
}
