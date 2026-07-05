package dev.adamsjack.beanshelf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A full app palette. Every screen reads its colors through the dynamic accessors
 * below (Roast, Crema, Parchment…), which resolve to the ACTIVE palette — so
 * switching themes recomposes the whole app. The shelf layout is untouched; only
 * the hues change.
 */
data class Palette(
    val key: String,
    val label: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentDeep: Color,
    val onAccent: Color,
    val stampInk: Color,
    val plankLight: Color,
    val plankDark: Color,
    val plankEdge: Color,
)

object Palettes {
    // Brighter default — warm cream paper, espresso ink, terracotta accent.
    val Cream = Palette(
        key = "cream", label = "Cream", dark = false,
        background = Color(0xFFF4EADA), surface = Color(0xFFFCF7EE), surfaceHigh = Color(0xFFEADBC3),
        textPrimary = Color(0xFF2C1E10), textMuted = Color(0xFF8B7250),
        accent = Color(0xFFC2703A), accentDeep = Color(0xFFA1572A), onAccent = Color(0xFFFDF7EE),
        stampInk = Color(0xFFA1572A),
        plankLight = Color(0xFFC69A62), plankDark = Color(0xFF8A5E30), plankEdge = Color(0xFFE0BC85),
    )

    // The original dark roastery, now a choice.
    val Roastery = Palette(
        key = "roastery", label = "Roastery", dark = true,
        background = Color(0xFF17100B), surface = Color(0xFF251A11), surfaceHigh = Color(0xFF322415),
        textPrimary = Color(0xFFF0E4D2), textMuted = Color(0xFFA38B72),
        accent = Color(0xFFD9A468), accentDeep = Color(0xFFB37E43), onAccent = Color(0xFF17100B),
        stampInk = Color(0xFFE2B679),
        plankLight = Color(0xFF5C3F22), plankDark = Color(0xFF2C1B0C), plankEdge = Color(0xFF7A5530),
    )

    // Light + cool: mocha ink on oat.
    val Latte = Palette(
        key = "latte", label = "Latte", dark = false,
        background = Color(0xFFEFE7DA), surface = Color(0xFFFAF4EA), surfaceHigh = Color(0xFFE1D2BC),
        textPrimary = Color(0xFF33291B), textMuted = Color(0xFF8B7B63),
        accent = Color(0xFF9C6B45), accentDeep = Color(0xFF7E5537), onAccent = Color(0xFFFAF4EA),
        stampInk = Color(0xFF7E5537),
        plankLight = Color(0xFFBE976A), plankDark = Color(0xFF836039), plankEdge = Color(0xFFD9BD97),
    )

    // Light + fresh: a non-brown option for variety.
    val Mint = Palette(
        key = "mint", label = "Mint", dark = false,
        background = Color(0xFFECF3EC), surface = Color(0xFFF6FBF5), surfaceHigh = Color(0xFFD7E6D4),
        textPrimary = Color(0xFF20302A), textMuted = Color(0xFF6E8579),
        accent = Color(0xFF3F7D5A), accentDeep = Color(0xFF2E5F43), onAccent = Color(0xFFF6FBF5),
        stampInk = Color(0xFF2E5F43),
        plankLight = Color(0xFFB0916A), plankDark = Color(0xFF7C5E38), plankEdge = Color(0xFFCEB088),
    )

    // Light + warm blush.
    val Rose = Palette(
        key = "rose", label = "Rose", dark = false,
        background = Color(0xFFF6ECEC), surface = Color(0xFFFCF5F5), surfaceHigh = Color(0xFFEAD6D6),
        textPrimary = Color(0xFF331F22), textMuted = Color(0xFF9A7D80),
        accent = Color(0xFFB5566B), accentDeep = Color(0xFF8E3E51), onAccent = Color(0xFFFCF5F5),
        stampInk = Color(0xFF8E3E51),
        plankLight = Color(0xFFBE9670), plankDark = Color(0xFF875F3C), plankEdge = Color(0xFFD9B792),
    )

    // Dark + cool, for a modern alt to Roastery.
    val Slate = Palette(
        key = "slate", label = "Slate", dark = true,
        background = Color(0xFF14181C), surface = Color(0xFF1E252B), surfaceHigh = Color(0xFF2B343C),
        textPrimary = Color(0xFFE7ECEF), textMuted = Color(0xFF8B98A1),
        accent = Color(0xFF6FB1C9), accentDeep = Color(0xFF4E8DA3), onAccent = Color(0xFF12181C),
        stampInk = Color(0xFF9BCADB),
        plankLight = Color(0xFF4A5560), plankDark = Color(0xFF232A30), plankEdge = Color(0xFF66757F),
    )

    val all = listOf(Cream, Roastery, Latte, Mint, Rose, Slate)
    fun byKey(key: String?): Palette = all.firstOrNull { it.key == key } ?: Cream
}

/** The active palette, backed by snapshot state so changes recompose the app. */
object ThemeHolder {
    var palette by mutableStateOf(Palettes.Cream)
}

fun setPalette(p: Palette) { ThemeHolder.palette = p }

// Dynamic color accessors — same names the whole app already uses, now themeable.
val Roast: Color get() = ThemeHolder.palette.background
val Surface2: Color get() = ThemeHolder.palette.surface
val SurfaceHigh: Color get() = ThemeHolder.palette.surfaceHigh
val Parchment: Color get() = ThemeHolder.palette.textPrimary
val Dim: Color get() = ThemeHolder.palette.textMuted
val Crema: Color get() = ThemeHolder.palette.accent
val CremaDeep: Color get() = ThemeHolder.palette.accentDeep
val StampInk: Color get() = ThemeHolder.palette.stampInk
val PlankLight: Color get() = ThemeHolder.palette.plankLight
val PlankDark: Color get() = ThemeHolder.palette.plankDark
val PlankEdge: Color get() = ThemeHolder.palette.plankEdge

// Serif display for names/headings (printed-label feel); default sans for body/UI.
private val Type = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(letterSpacing = 1.8.sp, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun BeanshelfTheme(content: @Composable () -> Unit) {
    val p = ThemeHolder.palette // subscribe: rebuild scheme when theme changes

    // Status/nav bar icons flip to dark on light themes so they stay legible.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as android.app.Activity).window
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !p.dark
                isAppearanceLightNavigationBars = !p.dark
            }
        }
    }

    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        secondary = p.accentDeep,
        onSecondary = p.textPrimary,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textMuted,
        secondaryContainer = p.accent,
        onSecondaryContainer = p.onAccent,
        outline = p.textMuted.copy(alpha = 0.45f),
        error = Color(0xFFCF6A4F),
    )
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
