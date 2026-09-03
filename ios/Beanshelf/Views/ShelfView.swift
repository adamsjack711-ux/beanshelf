import SwiftUI

struct ShelfView: View {
    let beans: [Bean]
    let onAdd: () -> Void
    let onOpen: (Bean) -> Void
    let onLeaderboard: () -> Void
    let onImport: () -> Void

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Palette.roast.ignoresSafeArea()

            if beans.isEmpty {
                EmptyShelf(onAdd: onAdd)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        header
                        ForEach(rows.indices, id: \.self) { i in
                            ShelfRow(rowBeans: rows[i], onOpen: onOpen)
                        }
                    }
                    .padding(.bottom, 96)
                }
            }

            // FAB
            Button(action: onAdd) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(Palette.roast)
                    .frame(width: 56, height: 56)
                    .background(Palette.crema, in: RoundedRectangle(cornerRadius: 16))
                    .shadow(color: .black.opacity(0.4), radius: 8, y: 4)
            }
            .padding(24)
            .accessibilityLabel("Add a bag")
        }
    }

    private var rows: [[Bean]] {
        stride(from: 0, to: beans.count, by: 3).map {
            Array(beans[$0..<min($0 + 3, beans.count)])
        }
    }

    private var subtitle: String {
        var sub = "\(beans.count) \(beans.count == 1 ? "bag" : "bags")"
        let rated = beans.filter { $0.rating > 0 }
        if !rated.isEmpty {
            sub += "  ·  avg \(formatRating(rated.map(\.rating).reduce(0, +) / Double(rated.count)))"
        }
        return sub
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 0) {
                Text("Beanshelf")
                    .font(Type.headlineLarge)
                    .foregroundStyle(Palette.parchment)
                Spacer()
                Button(action: onImport) {
                    Image(systemName: "tray.and.arrow.down")
                        .font(.system(size: 18))
                        .foregroundStyle(Palette.crema)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Import a bean from a friend")
                Button(action: onLeaderboard) {
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(Palette.crema)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Leaderboard")
            }
            Eyebrow(text: subtitle)
        }
        .padding(.leading, 24)
        .padding(.trailing, 16)
        .padding(.top, 28)
        .padding(.bottom, 4)
    }
}

/// One shelf: up to three bags standing on a plank.
private struct ShelfRow: View {
    let rowBeans: [Bean]
    let onOpen: (Bean) -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            ShelfPlank()
            HStack(alignment: .bottom, spacing: 14) {
                ForEach(rowBeans) { bean in
                    BagCard(bean: bean) { onOpen(bean) }
                }
                ForEach(rowBeans.count..<3, id: \.self) { _ in
                    Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 26)
        }
        .frame(height: 224)
    }
}

private struct BagCard: View {
    let bean: Bean
    let onClick: () -> Void

    // Seeded tilt per bag so the shelf reads as physical objects, not a grid.
    // (String.hashValue is per-launch random in Swift — derive a stable seed.)
    private var tilt: Double {
        let seed = bean.id.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) }
        return Double(abs(seed) % 100) / 100 * 3 - 1.5
    }

    private var cutout: Bool { BagCropper.isCutout(bean.photoFile) }

    var body: some View {
        Button(action: onClick) {
            Group {
                if cutout {
                    // Free-standing silhouette: no card chrome, just a soft
                    // ground shadow where the bag meets the plank.
                    PhotoImage(file: bean.photoFile, targetWidth: 400, contentMode: .fit, alignment: .bottom) {
                        placeholder
                    }
                    .padding(.bottom, 2)
                    .background(alignment: .bottom) { groundShadow }
                } else {
                    PhotoImage(file: bean.photoFile, targetWidth: 400) {
                        placeholder
                    }
                    .background(Palette.surfaceHigh)
                    .clipShape(RoundedRectangle(cornerRadius: 7))
                    .shadow(color: .black.opacity(0.5), radius: 10, y: 5)
                }
            }
            .aspectRatio(0.76, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay(alignment: .bottomTrailing) {
                if bean.rating > 0 {
                    RoastStamp(rating: bean.rating, size: 42)
                        .padding(5)
                }
            }
            .rotationEffect(.degrees(tilt), anchor: .bottom)
        }
        .buttonStyle(.plain)
    }

    private var groundShadow: some View {
        GeometryReader { geo in
            let ow = geo.size.width * 0.78
            let oh: CGFloat = 16
            Ellipse()
                .fill(
                    RadialGradient(
                        colors: [Color.black.opacity(0.5), .clear],
                        center: .center,
                        startRadius: 0,
                        endRadius: ow / 2
                    )
                )
                .frame(width: ow, height: oh)
                .position(x: geo.size.width / 2, y: geo.size.height - oh / 2)
        }
    }

    private var placeholder: some View {
        VStack(spacing: 8) {
            Image(systemName: "cup.and.saucer.fill")
                .font(.system(size: 24))
                .foregroundStyle(Palette.dim)
            Text(bean.name)
                .font(Type.titleMedium)
                .foregroundStyle(Palette.dim)
                .multilineTextAlignment(.center)
                .lineLimit(3)
        }
        .padding(10)
        // A photo-less bag shows its name in the middle of the card, where the
        // bottom-trailing roast stamp would otherwise clip the last few letters.
        // Lift the text clear of the stamp's corner when there is one.
        .padding(.bottom, bean.rating > 0 ? 34 : 0)
    }
}

private struct EmptyShelf: View {
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // one bare plank, waiting
            ShelfPlank()
            Text("The shelf is empty")
                .font(Type.headlineMedium)
                .foregroundStyle(Palette.parchment)
                .padding(.top, 36)
            Text("Photograph a bag of beans to start your collection.")
                .font(Type.bodyLarge)
                .foregroundStyle(Palette.dim)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
                .padding(.bottom, 28)
            Button(action: onAdd) {
                HStack(spacing: 8) {
                    Image(systemName: "camera.fill").font(.system(size: 15))
                    Text("Add your first bag")
                }
                .font(Type.labelLarge)
                .foregroundStyle(Palette.roast)
                .padding(.horizontal, 22)
                .padding(.vertical, 12)
                .background(Palette.crema, in: Capsule())
            }
        }
        .padding(.horizontal, 36)
        .frame(maxHeight: .infinity)
    }
}
