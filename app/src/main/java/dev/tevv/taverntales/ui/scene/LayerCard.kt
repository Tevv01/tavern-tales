package dev.tevv.taverntales.ui.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tevv.taverntales.model.SoundLayer

@Composable
fun LayerCard(
    layer: SoundLayer,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    onRename: () -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onLoop: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    // Slightly see-through so the scene's picture shows behind the list.
    val colors = if (isPlaying) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    Card(shape = RoundedCornerShape(18.dp), colors = colors) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconToggleButton(checked = isPlaying, onCheckedChange = { onToggle() }) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop ${layer.name}" else "Play ${layer.name}",
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(layer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val tags = listOfNotNull(if (layer.loop) "Loop" else "One-shot", if (layer.autoPlay) "Starts with scene" else null)
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = layer.volume,
                    onValueChange = onVolume,
                    modifier = Modifier.semantics { contentDescription = "Volume of ${layer.name}" },
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Start with scene") },
                        leadingIcon = { CheckMark(layer.autoPlay) },
                        modifier = Modifier.onOff(layer.autoPlay),
                        onClick = { menuOpen = false; onAutoPlay(!layer.autoPlay) },
                    )
                    DropdownMenuItem(
                        text = { Text("Loop") },
                        leadingIcon = { CheckMark(layer.loop) },
                        modifier = Modifier.onOff(layer.loop),
                        onClick = { menuOpen = false; onLoop(!layer.loop) },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(Modifier.size(24.dp)) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null)
    }
}

/** Screen readers say "On" or "Off" for a menu item that toggles a setting. */
fun Modifier.onOff(on: Boolean): Modifier = semantics { stateDescription = if (on) "On" else "Off" }
