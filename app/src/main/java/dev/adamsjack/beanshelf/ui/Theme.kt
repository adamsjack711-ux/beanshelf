package dev.adamsjack.beanshelf.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Dark roastery" palette — every hue derived from roasted coffee, not a neutral dark theme.
val Roast = Color(0xFF17100B)        // brown-black background
val Surface2 = Color(0xFF251A11)     // cards
val SurfaceHigh = Color(0xFF322415)  // raised cards / placeholders
val Parchment = Color(0xFFF0E4D2)    // primary text (bag-label paper)
val Dim = Color(0xFFA38B72)          // muted text
val Crema = Color(0xFFD9A468)        // accent
val CremaDeep = Color(0xFFB37E43)
val StampInk = Color(0xFFE2B679)     // roast-stamp ring + numerals
val PlankLight = Color(0xFF5C3F22)   // shelf front face, lit edge
val PlankDark = Color(0xFF2C1B0C)    // shelf front face, shadowed base
val PlankEdge = Color(0xFF7A5530)    // thin top-edge highlight

private val Scheme = darkColorScheme(
    primary = Crema,
    onPrimary = Roast,
    secondary = CremaDeep,
    onSecondary = Parchment,
    background = Roast,
    onBackground = Parchment,
    surface = Surface2,
    onSurface = Parchment,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Dim,
    secondaryContainer = Crema,
    onSecondaryContainer = Roast,
    outline = Dim.copy(alpha = 0.45f),
    error = Color(0xFFCF6A4F),
)

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
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
