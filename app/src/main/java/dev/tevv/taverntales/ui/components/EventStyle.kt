package dev.tevv.taverntales.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons an event pad can show, keyed by the value stored in [dev.tevv.taverntales.model.SoundEvent.icon]. */
val EventIcons: Map<String, ImageVector> by lazy {
    linkedMapOf(
        "fire" to Icons.Default.LocalFireDepartment,
        "explosion" to Icons.Default.Flare,
        "light" to Icons.Default.WbSunny,
        "thunder" to Icons.Default.Thunderstorm,
        "sword" to Sword,
        "magic" to Icons.Default.AutoFixHigh,
        "beast" to Icons.Default.Pets,
        "arrow" to Arrow,
        "bell" to Icons.Default.NotificationsActive,
        "water" to Icons.Default.WaterDrop,
        "wind" to Icons.Default.Air,
        "heal" to Icons.Default.Favorite,
        "shield" to Icons.Default.Shield,
        "door" to Icons.Default.MeetingRoom,
        "music" to Icons.Default.MusicNote,
        "star" to Icons.Default.Star,
    )
}

fun eventIcon(key: String): ImageVector = EventIcons[key] ?: Icons.AutoMirrored.Filled.VolumeUp

/** Pad colours, keyed by the value stored in [dev.tevv.taverntales.model.SoundEvent.color]. */
val EventColors: Map<String, Color> = linkedMapOf(
    "ember" to Color(0xFFE0703A),
    "crimson" to Color(0xFFC0392B),
    "gold" to Color(0xFFE8B85C),
    "azure" to Color(0xFF4A86D8),
    "violet" to Color(0xFF9B6AE0),
    "emerald" to Color(0xFF2EA06C),
    "steel" to Color(0xFF8A9AAA),
    "umber" to Color(0xFF9A6A40),
)

fun eventColor(key: String): Color = EventColors[key] ?: EventColors.getValue("violet")

private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black), pathBuilder = block).build()

private val Sword: ImageVector = icon("Sword") {
    // blade
    moveTo(19f, 2f); lineTo(22f, 2f); lineTo(22f, 5f); lineTo(11.2f, 15.8f); lineTo(8.2f, 12.8f); close()
    // cross-guard
    moveTo(5.2f, 11.6f); lineTo(12.4f, 18.8f); lineTo(11f, 20.2f); lineTo(3.8f, 13f); close()
    // grip and pommel
    moveTo(6.9f, 15.7f); lineTo(8.3f, 17.1f); lineTo(4.6f, 20.8f); lineTo(3.2f, 19.4f); close()
    moveTo(2f, 21f); lineTo(3f, 20f); lineTo(4f, 21f); lineTo(3f, 22f); close()
}

private val Arrow: ImageVector = icon("Arrow") {
    // shaft
    moveTo(2.3f, 20.3f); lineTo(16.6f, 6f); lineTo(18f, 7.4f); lineTo(3.7f, 21.7f); close()
    // head
    moveTo(21.5f, 2.5f); lineTo(19.5f, 10f); lineTo(14f, 4.5f); close()
    // fletching
    moveTo(2f, 15f); lineTo(6f, 15f); lineTo(6.6f, 17.4f); lineTo(4.4f, 17.6f); close()
    moveTo(9f, 22f); lineTo(9f, 18f); lineTo(6.6f, 17.4f); lineTo(6.4f, 19.6f); close()
}
