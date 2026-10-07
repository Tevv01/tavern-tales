package dev.tevv.taverntales.ui.scene

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.ui.components.EventColors
import dev.tevv.taverntales.ui.components.EventIcons
import dev.tevv.taverntales.ui.components.eventColor
import dev.tevv.taverntales.ui.components.eventIcon

private val PadShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventPad(event: SoundEvent, isPlaying: Boolean, onPlay: () -> Unit, onEdit: () -> Unit) {
    val color = eventColor(event.color)
    val scale = if (isPlaying) {
        val pulse = rememberInfiniteTransition(label = "pulse")
        pulse.animateFloat(1f, 1.05f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "scale").value
    } else {
        1f
    }
    Box(
        Modifier
            .aspectRatio(0.9f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(PadShape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.4f))))
            .border(if (isPlaying) 2.dp else 1.dp, if (isPlaying) Color.White else color.copy(alpha = 0.7f), PadShape)
            .combinedClickable(onClick = onPlay, onLongClick = onEdit, onLongClickLabel = "Edit ${event.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Icon(eventIcon(event.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text(
                event.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun AddEventPad(onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.9f)
            .clip(PadShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PadShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text("Add event", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(
    event: SoundEvent,
    onUpdate: ((SoundEvent) -> SoundEvent) -> Unit,
    onPreview: () -> Unit,
    onReplaceSound: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit event", style = MaterialTheme.typography.titleLarge)
            Text(
                "Events are shared by all scenes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var name by remember(event.id) { mutableStateOf(event.name) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (it.isNotBlank()) onUpdate { e -> e.copy(name = it.trim()) }
                },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EventIcons.forEach { (key, icon) ->
                    val selected = key == event.icon
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (selected) eventColor(event.color) else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onUpdate { it.copy(icon = key) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = key, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("Colour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EventColors.forEach { (key, color) ->
                    val selected = key == event.color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { onUpdate { it.copy(color = key) } },
                    )
                }
            }

            Text("Volume", style = MaterialTheme.typography.labelLarge)
            Slider(value = event.volume, onValueChange = { v -> onUpdate { it.copy(volume = v) } })

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Preview", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onReplaceSound, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AudioFile, contentDescription = null)
                    Text("Change sound", Modifier.padding(start = 8.dp))
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Delete event", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
