import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}

/// A full app palette. Every screen reads its colors through the dynamic
/// accessors below (Palette.roast, Palette.crema…), which resolve to the
/// ACTIVE palette — so switching themes restyles the whole app. The shelf
/// layout is untouched; only the hues change. Mirrors the Android Theme.kt.
struct ThemePalette: Identifiable, Equatable {
    let key: String
    let label: String
    let dark: Bool
    let background: Color
    let surface: Color
    let surfaceHigh: Color
    let textPrimary: Color
    let textMuted: Color
    let accent: Color
    let accentDeep: Color
    let onAccent: Color
    let stampInk: Color
    let plankLight: Color
    let plankDark: Color
    let plankEdge: Color

    var id: String { key }
}

enum Palettes {
    // Brighter default — warm cream paper, espresso ink, terracotta accent.
    static let cream = ThemePalette(
        key: "cream", label: "Cream", dark: false,
        background: Color(hex: 0xF4EADA), surface: Color(hex: 0xFCF7EE), surfaceHigh: Color(hex: 0xEADBC3),
        textPrimary: Color(hex: 0x2C1E10), textMuted: Color(hex: 0x8B7250),
        accent: Color(hex: 0xC2703A), accentDeep: Color(hex: 0xA1572A), onAccent: Color(hex: 0xFDF7EE),
        stampInk: Color(hex: 0xA1572A),
        plankLight: Color(hex: 0xC69A62), plankDark: Color(hex: 0x8A5E30), plankEdge: Color(hex: 0xE0BC85)
    )

    // The original dark roastery, now a choice.
    static let roastery = ThemePalette(
        key: "roastery", label: "Roastery", dark: true,
        background: Color(hex: 0x17100B), surface: Color(hex: 0x251A11), surfaceHigh: Color(hex: 0x322415),
        textPrimary: Color(hex: 0xF0E4D2), textMuted: Color(hex: 0xA38B72),
        accent: Color(hex: 0xD9A468), accentDeep: Color(hex: 0xB37E43), onAccent: Color(hex: 0x17100B),
        stampInk: Color(hex: 0xE2B679),
        plankLight: Color(hex: 0x5C3F22), plankDark: Color(hex: 0x2C1B0C), plankEdge: Color(hex: 0x7A5530)
    )

    // Light + cool: mocha ink on oat.
    static let latte = ThemePalette(
        key: "latte", label: "Latte", dark: false,
        background: Color(hex: 0xEFE7DA), surface: Color(hex: 0xFAF4EA), surfaceHigh: Color(hex: 0xE1D2BC),
        textPrimary: Color(hex: 0x33291B), textMuted: Color(hex: 0x8B7B63),
        accent: Color(hex: 0x9C6B45), accentDeep: Color(hex: 0x7E5537), onAccent: Color(hex: 0xFAF4EA),
        stampInk: Color(hex: 0x7E5537),
        plankLight: Color(hex: 0xBE976A), plankDark: Color(hex: 0x836039), plankEdge: Color(hex: 0xD9BD97)
    )

    // Light + fresh: a non-brown option for variety.
    static let mint = ThemePalette(
        key: "mint", label: "Mint", dark: false,
        background: Color(hex: 0xECF3EC), surface: Color(hex: 0xF6FBF5), surfaceHigh: Color(hex: 0xD7E6D4),
        textPrimary: Color(hex: 0x20302A), textMuted: Color(hex: 0x6E8579),
        accent: Color(hex: 0x3F7D5A), accentDeep: Color(hex: 0x2E5F43), onAccent: Color(hex: 0xF6FBF5),
        stampInk: Color(hex: 0x2E5F43),
        plankLight: Color(hex: 0xB0916A), plankDark: Color(hex: 0x7C5E38), plankEdge: Color(hex: 0xCEB088)
    )

    // Light + warm blush.
    static let rose = ThemePalette(
        key: "rose", label: "Rose", dark: false,
        background: Color(hex: 0xF6ECEC), surface: Color(hex: 0xFCF5F5), surfaceHigh: Color(hex: 0xEAD6D6),
        textPrimary: Color(hex: 0x331F22), textMuted: Color(hex: 0x9A7D80),
        accent: Color(hex: 0xB5566B), accentDeep: Color(hex: 0x8E3E51), onAccent: Color(hex: 0xFCF5F5),
        stampInk: Color(hex: 0x8E3E51),
        plankLight: Color(hex: 0xBE9670), plankDark: Color(hex: 0x875F3C), plankEdge: Color(hex: 0xD9B792)
    )

    // Dark + cool, for a modern alt to Roastery.
    static let slate = ThemePalette(
        key: "slate", label: "Slate", dark: true,
        background: Color(hex: 0x14181C), surface: Color(hex: 0x1E252B), surfaceHigh: Color(hex: 0x2B343C),
        textPrimary: Color(hex: 0xE7ECEF), textMuted: Color(hex: 0x8B98A1),
        accent: Color(hex: 0x6FB1C9), accentDeep: Color(hex: 0x4E8DA3), onAccent: Color(hex: 0x12181C),
        stampInk: Color(hex: 0x9BCADB),
        plankLight: Color(hex: 0x4A5560), plankDark: Color(hex: 0x232A30), plankEdge: Color(hex: 0x66757F)
    )

    static let all = [cream, roastery, latte, mint, rose, slate]

    static func byKey(_ key: String?) -> ThemePalette {
        all.first { $0.key == key } ?? cream
    }
}

/// The active palette, observable so changing it restyles the app. The saved
/// choice is restored before the first frame.
@MainActor
final class ThemeHolder: ObservableObject {
    static let shared = ThemeHolder()

    private static let prefsKey = "theme.palette"

    @Published var palette: ThemePalette

    private init() {
        palette = Palettes.byKey(UserDefaults.standard.string(forKey: Self.prefsKey))
    }

    func set(_ p: ThemePalette) {
        palette = p
        UserDefaults.standard.set(p.key, forKey: Self.prefsKey)
    }
}

// Dynamic color accessors — same names the whole app already uses, now themeable.
@MainActor
enum Palette {
    static var roast: Color { ThemeHolder.shared.palette.background }
    static var surface2: Color { ThemeHolder.shared.palette.surface }
    static var surfaceHigh: Color { ThemeHolder.shared.palette.surfaceHigh }
    static var parchment: Color { ThemeHolder.shared.palette.textPrimary }
    static var dim: Color { ThemeHolder.shared.palette.textMuted }
    static var crema: Color { ThemeHolder.shared.palette.accent }
    static var cremaDeep: Color { ThemeHolder.shared.palette.accentDeep }
    static var onAccent: Color { ThemeHolder.shared.palette.onAccent }
    static var stampInk: Color { ThemeHolder.shared.palette.stampInk }
    static var plankLight: Color { ThemeHolder.shared.palette.plankLight }
    static var plankDark: Color { ThemeHolder.shared.palette.plankDark }
    static var plankEdge: Color { ThemeHolder.shared.palette.plankEdge }
    static let danger = Color(hex: 0xCF6A4F)
}

// Serif display for names/headings (printed-label feel); default sans for body/UI.
enum Type {
    static let headlineLarge = Font.system(size: 34, weight: .bold, design: .serif)
    static let headlineMedium = Font.system(size: 28, weight: .bold, design: .serif)
    static let titleLarge = Font.system(size: 22, weight: .semibold, design: .serif)
    static let titleMedium = Font.system(size: 16, weight: .semibold, design: .serif)
    static let bodyLarge = Font.system(size: 16)
    static let bodyMedium = Font.system(size: 14)
    static let bodySmall = Font.system(size: 12)
    static let labelLarge = Font.system(size: 14, weight: .medium)
    static let labelSmall = Font.system(size: 11, weight: .medium)
}
