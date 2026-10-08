package dev.tevv.taverntales.ui.scene

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.audio.MixerState
import dev.tevv.taverntales.audio.importAudio
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.ui.components.ChoiceDialog
import dev.tevv.taverntales.ui.components.ConfirmDialog
import dev.tevv.taverntales.ui.components.SceneArt
import dev.tevv.taverntales.ui.components.TextInputDialog
import dev.tevv.taverntales.ui.components.isWideWindow

private sealed interface SceneDialog {
    data object RenameScene : SceneDialog
    data object MoveScene : SceneDialog
    data object DeleteScene : SceneDialog
    data class RenameLayer(val layer: SoundLayer) : SceneDialog
    data class RemoveLayer(val layer: SoundLayer) : SceneDialog
    data class DeleteEvent(val event: SoundEvent) : SceneDialog
}

private const val TAB_AMBIENCE = 0
private const val TAB_EVENTS = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(
    viewModel: SceneViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenHueSetup: () -> Unit,
) {
    val scene by viewModel.scene.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    val hueBridge by viewModel.hueBridge.collectAsStateWithLifecycle()
    val hueScenes by viewModel.hueScenes.collectAsStateWithLifecycle()
    val eventSounds by viewModel.eventSounds.collectAsStateWithLifecycle()
    var pickingLights by rememberSaveable { mutableStateOf(false) }
    val current = scene ?: run {
        // Deleted (from this screen's menu or elsewhere); nothing to show.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val isActive = mixer.sceneId == current.id

    var tab by rememberSaveable { mutableIntStateOf(TAB_AMBIENCE) }
    var dialog by remember { mutableStateOf<SceneDialog?>(null) }
    var editingEventId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val context = LocalContext.current
    val pickLayerAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addLayers(uris.map { importAudio(context, it) })
    }
    val pickEventAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) editingEventId = viewModel.addEvent(importAudio(context, uri))
    }
    val replaceEventAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = editingEventId
        if (uri != null && id != null) {
            val audio = importAudio(context, uri)
            viewModel.updateEvent(id) { it.copy(uri = audio.uri) }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setBackground(uri)
    }

    // Tablets and phones in landscape show the sounds and the event pads side by side.
    val wide = isWideWindow()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(current, fullScreen = wide)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        SceneMenu(
                            hasBackground = current.background != null,
                            canMove = collections.size > 1,
                            onRename = { dialog = SceneDialog.RenameScene },
                            onChangeBackground = {
                                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onRemoveBackground = viewModel::removeBackground,
                            onMove = { dialog = SceneDialog.MoveScene },
                            onDelete = { dialog = SceneDialog.DeleteScene },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            floatingActionButton = {
                if (!wide && tab == TAB_AMBIENCE) {
                    ExtendedFloatingActionButton(
                        onClick = { pickLayerAudio.launch(arrayOf("audio/*")) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add sounds") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val controls = @Composable {
                SceneControls(
                    mixer = mixer,
                    isPlaying = isActive && mixer.playing.isNotEmpty(),
                    hasLayers = current.layers.isNotEmpty(),
                    onPlay = viewModel::playScene,
                    onStop = viewModel::stopAll,
                    onMasterVolume = viewModel::setMasterVolume,
                    onMuted = viewModel::setMuted,
                )
            }
            val eventSoundsSwitch = @Composable { EventSoundsSwitch(eventSounds, viewModel::setEventSounds) }
            val lightsRow = @Composable {
                LightsRow(
                    lighting = current.lighting,
                    lights = current.lights,
                    connected = hueBridge != null,
                    onEdit = { if (hueBridge == null) onOpenHueSetup() else pickingLights = true },
                    onApply = viewModel::applyLights,
                )
            }
            val layerCard = @Composable { layer: SoundLayer ->
                LayerCard(
                    layer = layer,
                    isPlaying = isActive && layer.id in mixer.playing,
                    onToggle = { viewModel.toggleLayer(layer) },
                    onVolume = { viewModel.setLayerVolume(layer.id, it) },
                    onRename = { dialog = SceneDialog.RenameLayer(layer) },
                    onAutoPlay = { viewModel.setAutoPlay(layer.id, it) },
                    onLoop = { viewModel.setLoop(layer.id, it) },
                    onRemove = { dialog = SceneDialog.RemoveLayer(layer) },
                )
            }
            val eventPad = @Composable { event: SoundEvent ->
                EventPad(
                    event = event,
                    isPlaying = event.id in mixer.events,
                    onPlay = { viewModel.playEvent(event) },
                    onEdit = { editingEventId = event.id },
                )
            }
            val addEventPad = @Composable { AddEventPad(onClick = { pickEventAudio.launch(arrayOf("audio/*")) }) }

            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    LazyColumn(
                        Modifier.weight(1.1f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { SceneTitle(current, collection, topPadding = 0.dp) }
                        item { controls() }
                        item { lightsRow() }
                        item {
                            PaneHeader(Icons.Default.GraphicEq, "Ambience") {
                                TextButton(onClick = { pickLayerAudio.launch(arrayOf("audio/*")) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text("Add sounds", Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                        if (current.layers.isEmpty()) item { Hint(LAYERS_HINT) }
                        items(current.layers, key = { it.id }) { layerCard(it) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) { PaneHeader(Icons.Default.Bolt, "Events") }
                        item(span = { GridItemSpan(maxLineSpan) }) { eventSoundsSwitch() }
                        items(events, key = { it.id }) { eventPad(it) }
                        item(key = "add-event") { addEventPad() }
                        item(span = { GridItemSpan(maxLineSpan) }) { Hint(EVENTS_HINT) }
                    }
                }
            } else {
                val layoutDirection = LocalLayoutDirection.current
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SceneTitle(current, collection, topPadding = 150.dp) }
                    item(span = { GridItemSpan(maxLineSpan) }) { controls() }
                    item(span = { GridItemSpan(maxLineSpan) }) { lightsRow() }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                            Tab(
                                selected = tab == TAB_AMBIENCE,
                                onClick = { tab = TAB_AMBIENCE },
                                text = { Text("Ambience") },
                                icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                            )
                            Tab(
                                selected = tab == TAB_EVENTS,
                                onClick = { tab = TAB_EVENTS },
                                text = { Text("Events") },
                                icon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                            )
                        }
                    }
                    if (tab == TAB_AMBIENCE) {
                        if (current.layers.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { Hint(LAYERS_HINT) }
                        }
                        items(current.layers, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { layerCard(it) }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) { eventSoundsSwitch() }
                        items(events, key = { it.id }) { eventPad(it) }
                        item(key = "add-event") { addEventPad() }
                        item(span = { GridItemSpan(maxLineSpan) }) { Hint(EVENTS_HINT) }
                    }
                }
            }
        }
    }

    if (pickingLights) {
        LightsSheet(
            lighting = current.lighting,
            lights = current.lights,
            room = hueBridge?.group?.name,
            hueScenes = hueScenes,
            onLoadHueScenes = viewModel::loadHueScenes,
            onSetLighting = viewModel::setLighting,
            onLinkHueScene = viewModel::linkLights,
            onChooseRoom = {
                pickingLights = false
                onOpenHueSetup()
            },
            onDismiss = { pickingLights = false },
        )
    }

    events.find { it.id == editingEventId }?.let { event ->
        EventEditorSheet(
            event = event,
            onUpdate = { transform -> viewModel.updateEvent(event.id, transform) },
            onPreview = { viewModel.previewEvent(event) },
            onReplaceSound = { replaceEventAudio.launch(arrayOf("audio/*")) },
            onDelete = { dialog = SceneDialog.DeleteEvent(event) },
            onDismiss = { editingEventId = null },
        )
    }

    when (val d = dialog) {
        null -> Unit
        SceneDialog.RenameScene -> TextInputDialog(
            title = "Rename scene",
            initialValue = current.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameScene(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SceneDialog.MoveScene -> ChoiceDialog(
            title = "Move to collection",
            options = collections,
            label = { it.name },
            selected = collection,
            onPick = { viewModel.moveScene(it.id); dialog = null },
            onDismiss = { dialog = null },
        )
        SceneDialog.DeleteScene -> ConfirmDialog(
            title = "Delete \"${current.name}\"?",
            message = "The scene and its sound list are removed. Your audio files are not deleted.",
            confirmLabel = "Delete",
            onConfirm = { dialog = null; viewModel.deleteScene() },
            onDismiss = { dialog = null },
        )
        is SceneDialog.RenameLayer -> TextInputDialog(
            title = "Rename sound",
            initialValue = d.layer.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameLayer(d.layer.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is SceneDialog.RemoveLayer -> ConfirmDialog(
            title = "Remove \"${d.layer.name}\"?",
            message = "It is removed from this scene. The audio file on your phone is not deleted.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeLayer(d.layer.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is SceneDialog.DeleteEvent -> ConfirmDialog(
            title = "Delete \"${d.event.name}\"?",
            message = "The pad is removed from the Events tab in every scene.",
            confirmLabel = "Delete",
            onConfirm = {
                editingEventId = null
                viewModel.removeEvent(d.event.id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

/**
 * The scene's picture: across the top of the screen, fading into the background colour, or with
 * [fullScreen] (side-by-side panes) filling the screen, dimmed so the panes stay readable.
 */
@Composable
private fun Backdrop(scene: Scene, fullScreen: Boolean) {
    val background = MaterialTheme.colorScheme.background
    Box(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(520.dp)) {
        SceneArt(scene, Modifier.fillMaxSize(), fallbackIconSize = 160.dp)
        Box(
            Modifier.fillMaxSize().background(
                if (fullScreen) {
                    Brush.verticalGradient(0f to background.copy(alpha = 0.55f), 1f to background.copy(alpha = 0.85f))
                } else {
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.35f),
                        0.4f to background.copy(alpha = 0.25f),
                        0.75f to background.copy(alpha = 0.8f),
                        1f to background,
                    )
                },
            ),
        )
    }
}

/** A pane's title, with an optional action at the end (side-by-side layout). */
@Composable
private fun PaneHeader(icon: ImageVector, title: String, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { heading() },
        )
        action()
    }
}

private const val LAYERS_HINT =
    "Add audio files from your phone (crowd chatter, rain, tavern music...). OGG or WAV files loop most smoothly."
private const val EVENTS_HINT =
    "Tap to play. Long-press a pad to change its name, icon, colour, volume or sound. Events are the same in every scene."

@Composable
private fun SceneTitle(scene: Scene, collection: SceneCollection?, topPadding: Dp) {
    Column(Modifier.padding(top = topPadding, bottom = 4.dp)) {
        collection?.let {
            Text(
                it.name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            scene.name,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun SceneControls(
    mixer: MixerState,
    isPlaying: Boolean,
    hasLayers: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onMasterVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, enabled = hasLayers, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Play scene", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onStop,
                enabled = isPlaying || mixer.events.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Stop all", Modifier.padding(start = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            // Tap the speaker to mute everything; the slider keeps its place for unmuting.
            IconToggleButton(
                checked = mixer.muted,
                onCheckedChange = onMuted,
                modifier = Modifier.semantics { stateDescription = if (mixer.muted) "Muted" else "On" },
            ) {
                Icon(
                    if (mixer.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute",
                    tint = if (mixer.muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Master", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
            Slider(
                value = mixer.masterVolume,
                onValueChange = onMasterVolume,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (mixer.muted) 0.4f else 1f)
                    .semantics {
                        contentDescription = "Master volume"
                        if (mixer.muted) stateDescription = "Muted"
                    },
            )
        }
    }
}

@Composable
private fun SceneMenu(
    hasBackground: Boolean,
    canMove: Boolean,
    onRename: () -> Unit,
    onChangeBackground: () -> Unit,
    onRemoveBackground: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        var open by remember { mutableStateOf(false) }
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Scene options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(if (hasBackground) "Change picture" else "Add picture") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = { open = false; onChangeBackground() },
            )
            if (hasBackground) {
                DropdownMenuItem(
                    text = { Text("Remove picture") },
                    leadingIcon = { Icon(Icons.Default.HideImage, contentDescription = null) },
                    onClick = { open = false; onRemoveBackground() },
                )
            }
            if (canMove) {
                DropdownMenuItem(
                    text = { Text("Move to collection") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    onClick = { open = false; onMove() },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete scene") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** Switches event sounds on and off for all scenes; off, the pads only flash the lights. */
@Composable
private fun EventSoundsSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Event sounds", style = MaterialTheme.typography.titleSmall)
            Text(
                if (on) "Pads play their sound and flash the lights." else "Off: pads only flash the lights.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
