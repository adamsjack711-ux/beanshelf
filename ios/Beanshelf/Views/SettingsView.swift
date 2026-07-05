import SwiftUI

/// Appearance settings — pick a palette; applies live and persists.
struct SettingsView: View {
    @ObservedObject private var theme = ThemeHolder.shared

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ZStack {
            Palette.roast.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Appearance")
                        .font(Type.headlineMedium)
                        .foregroundStyle(Palette.parchment)
                        .padding(.top, 12)
                    Eyebrow(text: "Theme").padding(.top, 8)
                    Text("Pick a look. Your shelf and everything else stay the same — just the colors change.")
                        .font(Type.bodyMedium)
                        .foregroundStyle(Palette.dim)
                        .padding(.bottom, 8)

                    LazyVGrid(columns: columns, spacing: 14) {
                        ForEach(Palettes.all) { palette in
                            PaletteCard(palette: palette, selected: palette.key == theme.palette.key) {
                                theme.set(palette)
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
    }
}

private struct PaletteCard: View {
    let palette: ThemePalette
    let selected: Bool
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                // mini mock: a bag card + a shelf plank + swatches, in the palette's own colors
                ZStack(alignment: .bottom) {
                    palette.surface
                    palette.plankLight.frame(height: 6)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(palette.surfaceHigh)
                        .frame(width: 30, height: 40)
                        .overlay(Circle().fill(palette.accent).frame(width: 16, height: 16))
                        .padding(.bottom, 8)
                }
                .aspectRatio(1.5, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 8))

                HStack {
                    Text(palette.label)
                        .font(.system(size: 15, weight: .semibold, design: .serif))
                        .foregroundStyle(palette.textPrimary)
                    Spacer()
                    if selected {
                        Image(systemName: "checkmark")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(palette.onAccent)
                            .frame(width: 22, height: 22)
                            .background(palette.accent, in: Circle())
                    } else {
                        HStack(spacing: 4) {
                            ForEach([palette.accent, palette.plankLight, palette.textPrimary], id: \.self) {
                                Circle().fill($0).frame(width: 10, height: 10)
                            }
                        }
                    }
                }
                .padding(.top, 10)
            }
            .padding(14)
            .background(palette.background, in: RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        selected ? Palette.crema : palette.textMuted.opacity(0.3),
                        lineWidth: selected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
    }
}
