package dev.tevv.taverntales.ui.scene

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tevv.taverntales.model.HueSceneRef

/** Shows which Hue scene this scene switches to, with buttons to change it or apply it now. */
@Composable
fun LightsRow(lights: HueSceneRef?, connected: Boolean, onPick: () -> Unit, onApply: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Icon(
                if (lights != null) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = if (lights != null && connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Lights", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        lights != null -> listOfNotNull(lights.name, lights.room).joinToString(" · ")
                        connected -> "No Hue scene linked"
                        else -> "Hue not connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lights != null && connected) TextButton(onClick = onApply) { Text("Set now") }
            TextButton(onClick = onPick) {
                Text(
                    when {
                        !connected -> "Connect"
                        lights == null -> "Link"
                        else -> "Change"
                    },
                )
            }
        }
    }
}

/** Bottom sheet listing the bridge's scenes by room; picking one links it (and previews it). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueScenePicker(
    current: HueSceneRef?,
    scenes: Result<List<HueSceneRef>>?,
    onLoad: () -> Unit,
    onPick: (HueSceneRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Lights for this scene",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        when {
            scenes == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            scenes.isFailure -> Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    "Couldn't load your Hue scenes: ${scenes.exceptionOrNull()?.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onLoad) { Text("Try again") }
            }
            else -> LazyColumn(Modifier.navigationBarsPadding()) {
                item {
                    PickerItem("None", "Don't change the lights", selected = current == null) { onPick(null) }
                }
                val byRoom = scenes.getOrThrow().groupBy { it.room ?: "Other" }
                byRoom.forEach { (room, roomScenes) ->
                    item(key = "room-$room") {
                        Text(
                            room,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(roomScenes, key = { it.id }) { scene ->
                        PickerItem(scene.name, null, selected = scene.id == current?.id) { onPick(scene) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}
