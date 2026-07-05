import SwiftUI

/// Full-screen photo viewer — tap anywhere to close. Just looking, no editing.
struct PhotoViewer: View {
    let file: String
    let onDismiss: () -> Void

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color.black.opacity(0.95).ignoresSafeArea()
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .padding(12)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onDismiss)
        .task {
            image = await PhotoStore.loadScaled(file: file, targetWidth: 1600)
        }
    }
}

/// Letter-spaced uppercase metadata label — the bag-label vernacular.
struct Eyebrow: View {
    let text: String
    var color: Color = Palette.dim

    var body: some View {
        Text(text.uppercased())
            .font(Type.labelSmall)
            .kerning(1.8)
            .foregroundStyle(color)
    }
}

/// The signature element: a rotated circular "roast stamp" holding the rating.
/// Double ring + serif numerals on a dark scrim, like an inked stamp on kraft paper.
struct RoastStamp: View {
    let rating: Double
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle()
                .fill(Palette.roast.opacity(0.88))
            Circle()
                .strokeBorder(Palette.stampInk, lineWidth: 2)
                .padding(0.5)
            Circle()
                .strokeBorder(Palette.stampInk.opacity(0.7), lineWidth: 1)
                .padding(5)
            Text(rating <= 0 ? "—" : formatRating(rating))
                .font(.system(size: size * 0.30, weight: .bold, design: .serif))
                .foregroundStyle(Palette.stampInk)
        }
        .frame(width: size, height: size)
        .rotationEffect(.degrees(-12))
    }
}

/// Quarter-step 0–5 rating slider.
struct RatingSlider: View {
    @Binding var value: Double

    var body: some View {
        Slider(value: $value, in: 0...5, step: 0.25)
            .tint(Palette.crema)
    }
}

/// A wooden shelf plank: thin lit top edge, gradient front face, soft shadow below.
/// Drawn full-bleed; bags sit on top of it.
struct ShelfPlank: View {
    var body: some View {
        Canvas { context, size in
            let w = size.width
            let edge: CGFloat = 3
            let face: CGFloat = 18
            // lit top edge
            context.fill(
                Path(CGRect(x: 0, y: 0, width: w, height: edge)),
                with: .color(Palette.plankEdge)
            )
            // front face
            context.fill(
                Path(CGRect(x: 0, y: edge, width: w, height: face)),
                with: .linearGradient(
                    Gradient(colors: [Palette.plankLight, Palette.plankDark]),
                    startPoint: CGPoint(x: 0, y: edge),
                    endPoint: CGPoint(x: 0, y: edge + face)
                )
            )
            // faint wood grain on the face
            for fy in [0.35, 0.62] {
                var line = Path()
                line.move(to: CGPoint(x: 0, y: edge + face * fy))
                line.addLine(to: CGPoint(x: w, y: edge + face * fy))
                context.stroke(line, with: .color(Palette.plankDark.opacity(0.5)), lineWidth: 0.5)
            }
            // shadow cast below the plank
            context.fill(
                Path(CGRect(x: 0, y: edge + face, width: w, height: size.height - edge - face)),
                with: .linearGradient(
                    Gradient(colors: [Color.black.opacity(0.45), .clear]),
                    startPoint: CGPoint(x: 0, y: edge + face),
                    endPoint: CGPoint(x: 0, y: size.height)
                )
            )
        }
        .frame(height: 30)
    }
}

/// Single-select chips that wrap onto multiple lines (Android FilterChip + FlowRow).
struct ChoiceChips: View {
    let options: [String]
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(options, id: \.self) { opt in
                let isSelected = selected == opt
                Button {
                    onSelect(opt)
                } label: {
                    Text(opt)
                        .font(Type.labelLarge)
                        .foregroundStyle(isSelected ? Palette.roast : Palette.dim)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(isSelected ? Palette.crema : Palette.surface2)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .strokeBorder(
                                    isSelected ? Palette.crema : Palette.dim.opacity(0.35),
                                    lineWidth: 1
                                )
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// Minimal wrapping layout (Compose FlowRow equivalent).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(in: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.last.map { $0.y + $0.height } ?? 0
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(in: bounds.width, subviews: subviews)
        for row in rows {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(
                    at: CGPoint(x: x, y: bounds.minY + row.y),
                    proposal: ProposedViewSize(size)
                )
                x += size.width + spacing
            }
        }
    }

    private struct Row {
        var indices: [Int] = []
        var y: CGFloat = 0
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(in maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        var x: CGFloat = 0
        var y: CGFloat = 0
        for (i, view) in subviews.enumerated() {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                rows.append(current)
                y += current.height + spacing
                current = Row(y: y)
                x = 0
            }
            current.indices.append(i)
            current.y = y
            current.width = x + size.width
            current.height = max(current.height, size.height)
            x += size.width + spacing
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}

/// Async photo view: loads a downsampled bitmap off the main thread, fills
/// (or fits — cutout PNGs must never be zoom-cropped) its frame, and shows
/// the given placeholder while empty.
struct PhotoImage<Placeholder: View>: View {
    let file: String?
    let targetWidth: CGFloat
    var contentMode: ContentMode = .fill
    var alignment: Alignment = .center
    @ViewBuilder var placeholder: () -> Placeholder

    @State private var image: UIImage?

    var body: some View {
        Color.clear
            .overlay(alignment: alignment) {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                } else {
                    placeholder()
                }
            }
            .clipped()
            .task(id: "\(file ?? "")@\(Int(targetWidth))") {
                image = await PhotoStore.loadScaled(file: file, targetWidth: targetWidth)
            }
    }
}

/// Rounded text-field style matching the Android OutlinedTextField theming.
struct OutlinedField: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(Palette.crema)
            .foregroundStyle(Palette.parchment)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .strokeBorder(Palette.dim.opacity(0.4), lineWidth: 1)
            )
    }
}

extension View {
    func outlinedField() -> some View { modifier(OutlinedField()) }
}
