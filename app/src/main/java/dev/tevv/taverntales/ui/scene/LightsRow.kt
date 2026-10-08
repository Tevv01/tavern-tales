package dev.tevv.taverntales.ui.scene

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tevv.taverntales.model.HueSceneRef
import dev.tevv.taverntales.model.LightSetup
import dev.tevv.taverntales.model.LightSlot
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/** Shows how this scene sets the lights, with buttons to change it or apply it now. */
@Composable
fun LightsRow(
    lighting: LightSetup?,
    lights: HueSceneRef?,
    connected: Boolean,
    onEdit: () -> Unit,
    onApply: () -> Unit,
) {
    val hasLights = lighting != null || lights != null
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Icon(
                if (hasLights) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = if (hasLights && connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("Lights", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    lighting != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        lighting.slots.forEach { Swatch(it.color, 16) }
                        Text(
                            if (lighting.brightness <= 0f) "  Off" else "  ${(lighting.brightness * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> Text(
                        when {
                            lights != null -> "Hue scene: " + listOfNotNull(lights.name, lights.room).joinToString(" · ")
                            connected -> "Not changed"
                            else -> "Hue not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasLights && connected) TextButton(onClick = onApply) { Text("Set now") }
            TextButton(onClick = onEdit) { Text(if (connected) "Edit" else "Connect") }
        }
    }
}

private enum class LightsMode(val label: String) { Setup("Colours"), HueScene("Hue scene"), None("None") }

/**
 * Editor for a scene's lights: a light setup made here, a scene from the Hue app, or nothing.
 * Changes are saved as they're made; "Try on lights" previews the setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightsSheet(
    lighting: LightSetup?,
    lights: HueSceneRef?,
    room: String?,
    hueScenes: Result<List<HueSceneRef>>?,
    onLoadHueScenes: () -> Unit,
    onSetLighting: (LightSetup?) -> Unit,
    onTryLighting: (LightSetup) -> Unit,
    onLinkHueScene: (HueSceneRef?) -> Unit,
    onChooseRoom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            when {
                lighting != null -> LightsMode.Setup
                lights != null -> LightsMode.HueScene
                else -> LightsMode.None
            },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Lights for this scene", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                LightsMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = {
                            mode = entry
                            when (entry) {
                                LightsMode.Setup -> if (lighting == null) onSetLighting(LightSetup())
                                LightsMode.HueScene -> onLoadHueScenes()
                                LightsMode.None -> {
                                    onSetLighting(null)
                                    onLinkHueScene(null)
                                }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, LightsMode.entries.size),
                    ) { Text(entry.label) }
                }
            }
            when (mode) {
                LightsMode.Setup -> lighting?.let {
                    LightSetupEditor(it, room, onChange = onSetLighting, onTry = onTryLighting, onChooseRoom = onChooseRoom)
                }
                LightsMode.HueScene -> HueSceneList(lights, hueScenes, onLoadHueScenes, onLinkHueScene)
                LightsMode.None -> Text(
                    "The lights are left as they are when this scene plays.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LightSetupEditor(
    setup: LightSetup,
    room: String?,
    onChange: (LightSetup) -> Unit,
    onTry: (LightSetup) -> Unit,
    onChooseRoom: () -> Unit,
) {
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (room != null) "Applied to: $room" else "No room chosen for scene lighting yet",
            style = MaterialTheme.typography.bodySmall,
            color = if (room != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChooseRoom) { Text(if (room != null) "Change" else "Choose") }
    }

    Text("Colours", style = MaterialTheme.typography.labelLarge)
    Text(
        "Spread over the room's lights in turn. Tap a colour to change it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        setup.slots.forEachIndexed { index, slot ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { editingSlot = index }) { Swatch(slot.color, 52) }
                Text(
                    slot.effect?.let { EFFECTS[it] } ?: " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (setup.slots.size < MAX_SLOTS) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                        onChange(setup.copy(slots = setup.slots + setup.slots.last().copy(effect = null)))
                        editingSlot = setup.slots.size
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = "Add colour") }
        }
    }

    Text("Brightness: " + if (setup.brightness <= 0f) "off" else "${(setup.brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
    Slider(value = setup.brightness, onValueChange = { onChange(setup.copy(brightness = it)) })

    OutlinedButton(onClick = { onTry(setup) }, enabled = room != null, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Lightbulb, contentDescription = null)
        Text("Try on lights", Modifier.padding(start = 8.dp))
    }

    editingSlot?.let { index ->
        setup.slots.getOrNull(index)?.let { slot ->
            SlotDialog(
                slot = slot,
                canRemove = setup.slots.size > 1,
                onSave = { updated ->
                    onChange(setup.copy(slots = setup.slots.toMutableList().also { it[index] = updated }))
                    editingSlot = null
                },
                onRemove = {
                    onChange(setup.copy(slots = setup.slots.toMutableList().also { it.removeAt(index) }))
                    editingSlot = null
                },
                onDismiss = { editingSlot = null },
            )
        }
    }
}

/** Colour picker for one slot: presets, hue and saturation sliders, and a flicker effect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotDialog(
    slot: LightSlot,
    canRemove: Boolean,
    onSave: (LightSlot) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember(slot.color) { FloatArray(3).also { AndroidColor.colorToHSV(parseColor(slot.color), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var effect by remember { mutableStateOf(slot.effect) }
    val color = toHex(hue, saturation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(parseColor(color))),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { preset ->
                        Box(
                            Modifier.clickable {
                                val p = FloatArray(3).also { AndroidColor.colorToHSV(parseColor(preset), it) }
                                hue = p[0]
                                saturation = p[1]
                            },
                        ) { Swatch(preset, 32) }
                    }
                }
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient((0..6).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) })),
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text("Effect", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Only on bulbs that support it (newer Hue lights); others just show the colour.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = effect == null, onClick = { effect = null }, label = { Text("None") })
                    EFFECTS.forEach { (key, label) ->
                        FilterChip(selected = effect == key, onClick = { effect = key }, label = { Text(label) })
                    }
                }
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("Remove this colour", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(LightSlot(color, effect)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HueSceneList(
    current: HueSceneRef?,
    scenes: Result<List<HueSceneRef>>?,
    onLoad: () -> Unit,
    onPick: (HueSceneRef?) -> Unit,
) {
    LaunchedEffect(Unit) { if (scenes == null) onLoad() }
    Text(
        "Use a scene you made in the Hue app. It's applied to whichever room it belongs to.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        scenes == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        scenes.isFailure -> Column {
            Text("Couldn't load your Hue scenes: ${scenes.exceptionOrNull()?.message}", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onLoad) { Text("Try again") }
        }
        else -> scenes.getOrThrow().groupBy { it.room ?: "Other" }.forEach { (room, roomScenes) ->
            Text(room, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            roomScenes.forEach { scene ->
                ListItem(
                    headlineContent = { Text(scene.name) },
                    trailingContent = if (scene.id == current?.id) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(scene) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, sizeDp: Int) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(parseColor(hex)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
    )
}

private fun parseColor(hex: String): Int = runCatching { AndroidColor.parseColor(hex) }.getOrDefault(AndroidColor.WHITE)

private fun toHex(hue: Float, saturation: Float): String =
    "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)) and 0xFFFFFF)

private const val MAX_SLOTS = 6

/** Hue light effects offered in the editor (the bridge's names), with display labels. */
private val EFFECTS = linkedMapOf("candle" to "Candle", "fire" to "Fire", "sparkle" to "Sparkle", "glisten" to "Glisten", "prism" to "Prism")

private val PRESETS = listOf(
    "#FFE3B8", "#FFC27A", "#FFB054", "#FF8A2B", "#FF5A14", "#FF2A1A", "#C0141E", "#FF5FA2",
    "#B03AFF", "#6A3D9A", "#2B3A8C", "#1E64FF", "#2FD8FF", "#2FA44A", "#86D660", "#F0F4FF",
)
