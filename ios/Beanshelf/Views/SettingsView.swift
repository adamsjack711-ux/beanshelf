import SwiftUI

/// Appearance settings — pick a palette; applies live and persists. Also the
/// home for feedback, since there's nowhere else it naturally belongs.
struct SettingsView: View {
    var beanCount: Int = 0

    @ObservedObject private var theme = ThemeHolder.shared
    @State private var showReport = false

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

                    feedback
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
        }
        .sheet(isPresented: $showReport) {
            ReportBugView(beanCount: beanCount) { showReport = false }
        }
    }

    private var feedback: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Feedback")
                .font(Type.headlineMedium)
                .foregroundStyle(Palette.parchment)
                .padding(.top, 36)
            Eyebrow(text: "Something broken?").padding(.top, 4)
            Text("Found a bug, or want Beanshelf to do something it doesn't? Tell me and I'll fix it.")
                .font(Type.bodyMedium)
                .foregroundStyle(Palette.dim)
                .padding(.bottom, 8)

            Button {
                showReport = true
            } label: {
                Text("Report a bug")
                    .font(Type.labelLarge)
                    .foregroundStyle(Palette.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Palette.crema))
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
