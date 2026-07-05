import SwiftUI

/// Renders a bean as a 1080x1350 share card — the roastery-dark look with the
/// bag photo, serif name, roast stamp, and tasting notes. Written to tmp/share
/// for hand-off to the share sheet. Same layout as the Android Canvas renderer,
/// built with SwiftUI + ImageRenderer.
enum ShareCard {

    @MainActor
    static func render(bean: Bean) async -> URL? {
        let photo: UIImage? = await PhotoStore.loadScaled(file: bean.photoFile, targetWidth: 1000)
        let card = CardView(bean: bean, photo: photo, isCutout: BagCropper.isCutout(bean.photoFile))
        let renderer = ImageRenderer(content: card.frame(width: 1080, height: 1350))
        renderer.scale = 1
        guard let ui = renderer.uiImage, let data = ui.pngData() else { return nil }
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("share", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let out = dir.appendingPathComponent("beanshelf-card-\(bean.id.prefix(8)).png")
        do {
            try data.write(to: out, options: .atomic)
            return out
        } catch {
            return nil
        }
    }

    private struct CardView: View {
        let bean: Bean
        let photo: UIImage?
        let isCutout: Bool

        private let photoBottom: CGFloat = 800

        var body: some View {
            ZStack(alignment: .topLeading) {
                Palette.roast

                if let photo {
                    if isCutout {
                        // plank under the free-standing bag
                        LinearGradient(colors: [Palette.plankLight, Palette.plankDark], startPoint: .top, endPoint: .bottom)
                            .frame(width: 1080, height: 42)
                            .offset(y: photoBottom - 8)
                        let scale = min(760 / photo.size.width, 640 / photo.size.height)
                        let dw = photo.size.width * scale
                        let dh = photo.size.height * scale
                        Image(uiImage: photo)
                            .resizable()
                            .frame(width: dw, height: dh)
                            .offset(x: (1080 - dw) / 2, y: photoBottom - dh)
                    } else {
                        // cover-crop into a rounded frame
                        Image(uiImage: photo)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 1080 - 120, height: photoBottom - 60)
                            .clipShape(RoundedRectangle(cornerRadius: 28))
                            .offset(x: 60, y: 60)
                    }
                }

                if bean.rating > 0 {
                    RoastStamp(rating: bean.rating, size: 192)
                        .offset(x: 920 - 96, y: photoBottom - 40 - 96)
                }

                textBlock
                    .offset(x: 72, y: photoBottom + 40)

                Text("BEANSHELF")
                    .font(.system(size: 30))
                    .kerning(9.6)
                    .foregroundStyle(Palette.dim)
                    .frame(width: 1080)
                    .offset(y: 1350 - 82)
            }
            .frame(width: 1080, height: 1350)
            .clipped()
        }

        private var textBlock: some View {
            VStack(alignment: .leading, spacing: 0) {
                if !bean.roaster.isEmpty {
                    Text(bean.roaster.uppercased())
                        .font(.system(size: 34, weight: .bold))
                        .kerning(6)
                        .foregroundStyle(Palette.crema)
                        .padding(.bottom, 20)
                }
                Text(bean.name)
                    .font(.system(size: 76, weight: .bold, design: .serif))
                    .foregroundStyle(Palette.parchment)
                    .lineLimit(2)
                let meta = [bean.origin, bean.variety, bean.process]
                    .filter { !$0.isEmpty }
                    .joined(separator: "  ·  ")
                if !meta.isEmpty {
                    Text(meta)
                        .font(.system(size: 38))
                        .foregroundStyle(Palette.dim)
                        .padding(.top, 26)
                }
                if !bean.notes.isEmpty {
                    Text(bean.notes)
                        .font(.system(size: 42, design: .serif))
                        .italic()
                        .foregroundStyle(Palette.parchment.opacity(0.86))
                        .lineLimit(3)
                        .padding(.top, 26)
                }
            }
            .frame(width: 1080 - 144, alignment: .leading)
        }
    }
}
