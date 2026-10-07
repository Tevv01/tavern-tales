package dev.tevv.pocketbard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app is used at the gaming table, often in dim light, so it is always dark.
private val PocketBardColors = darkColorScheme(
    primary = Color(0xFFE8B85C),
    onPrimary = Color(0xFF2A1E0E),
    primaryContainer = Color(0xFF4A3618),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFF9FC1A0),
    onSecondary = Color(0xFF0F2412),
    secondaryContainer = Color(0xFF2B3F2D),
    onSecondaryContainer = Color(0xFFCDE6CD),
    background = Color(0xFF1B1512),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF1B1512),
    onSurface = Color(0xFFEDE0D4),
    surfaceVariant = Color(0xFF3A302A),
    onSurfaceVariant = Color(0xFFD5C4B5),
    surfaceContainer = Color(0xFF261F1B),
    surfaceContainerHigh = Color(0xFF312923),
    outline = Color(0xFF9E8E80),
)

@Composable
fun PocketBardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PocketBardColors, content = content)
}
