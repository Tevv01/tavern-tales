package dev.tevv.taverntales.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.tevv.taverntales.R

// The app is used at the gaming table, often in dim light, so it is always dark.
private val TavernTalesColors = darkColorScheme(
    primary = Color(0xFFE8B85C),
    onPrimary = Color(0xFF2A1E0E),
    primaryContainer = Color(0xFF4A3618),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9FC1A0),
    onSecondary = Color(0xFF0F2412),
    // Also the unfilled part of sliders, so it is kept a warm neutral.
    secondaryContainer = Color(0xFF41352B),
    onSecondaryContainer = Color(0xFFEBDCC8),
    background = Color(0xFF14100D),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF14100D),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF3A302A),
    onSurfaceVariant = Color(0xFFCDBCAD),
    surfaceContainerLow = Color(0xFF1C1612),
    surfaceContainer = Color(0xFF241D18),
    surfaceContainerHigh = Color(0xFF2E2620),
    surfaceContainerHighest = Color(0xFF3A302A),
    outline = Color(0xFF9E8E80),
    outlineVariant = Color(0xFF4A3F37),
)

/** Cinzel (variable font, OFL) for headings; body text stays in the system font for legibility. */
private val Cinzel = FontFamily(
    Font(R.font.cinzel, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cinzel, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cinzel, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val TavernTalesTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Cinzel, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = Cinzel, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = Cinzel, fontWeight = FontWeight.Bold, fontSize = 34.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Cinzel, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Cinzel, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun TavernTalesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TavernTalesColors, typography = TavernTalesTypography, content = content)
}
