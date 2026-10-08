package dev.tevv.taverntales.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import dev.tevv.taverntales.data.BuiltinBackgrounds
import dev.tevv.taverntales.model.Scene
import kotlin.math.absoluteValue

/** Coil model for a [Scene.background] value: a drawable id for `builtin:` keys, else the file URI. */
fun backgroundModel(background: String?): Any? =
    if (BuiltinBackgrounds.isBuiltin(background)) BuiltinBackgrounds.drawableFor(background) else background

private val fallbackPalettes = listOf(
    Color(0xFF3A2440) to Color(0xFF14100D),
    Color(0xFF1F3A34) to Color(0xFF0D1412),
    Color(0xFF3A2A18) to Color(0xFF14100D),
    Color(0xFF1E2A40) to Color(0xFF0D1014),
    Color(0xFF402020) to Color(0xFF140D0D),
)

/** The scene's picture, or a gradient chosen from its name when it has none. */
@Composable
fun SceneArt(scene: Scene, modifier: Modifier = Modifier, fallbackIconSize: Dp) {
    val model = backgroundModel(scene.background)
    if (model != null) {
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        val (top, bottom) = fallbackPalettes[scene.name.hashCode().absoluteValue % fallbackPalettes.size]
        Box(modifier.background(Brush.verticalGradient(listOf(top, bottom))), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Landscape,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(fallbackIconSize),
            )
        }
    }
}

/** Darkens the lower part of an image so text on top stays readable. */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, color: Color = Color.Black, startAlpha: Float = 0f, endAlpha: Float = 0.85f) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.35f to color.copy(alpha = startAlpha), 1f to color.copy(alpha = endAlpha))),
    )
}
