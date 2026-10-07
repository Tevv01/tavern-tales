package dev.tevv.taverntales.ui.scene

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.audio.importAudioLayer
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.ui.components.ConfirmDialog
import dev.tevv.taverntales.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(
    viewModel: SceneViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scene by viewModel.scene.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    val current = scene ?: run {
        // Deleted from elsewhere (e.g. another screen); nothing to show.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val isActive = mixer.sceneId == current.id
    val isPlaying = isActive && mixer.playing.isNotEmpty()

    val context = LocalContext.current
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addLayers(uris.map { importAudioLayer(context, it) })
    }
    var renamingScene by remember { mutableStateOf(false) }
    var renamingLayer by remember { mutableStateOf<SoundLayer?>(null) }
    var removingLayer by remember { mutableStateOf<SoundLayer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { renamingScene = true }) { Icon(Icons.Default.Edit, contentDescription = "Rename scene") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickAudio.launch(arrayOf("audio/*")) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add sounds") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SceneControls(
                    isPlaying = isPlaying,
                    hasLayers = current.layers.isNotEmpty(),
                    masterVolume = mixer.masterVolume,
                    onPlay = viewModel::playScene,
                    onStop = viewModel::stopAll,
                    onMasterVolume = viewModel::setMasterVolume,
                )
            }
            if (current.layers.isEmpty()) {
                item {
                    Text(
                        "Add audio files from your phone (crowd chatter, rain, tavern music...). " +
                            "OGG or WAV files loop most smoothly.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(current.layers, key = { it.id }) { layer ->
                LayerCard(
                    layer = layer,
                    isPlaying = isActive && layer.id in mixer.playing,
                    onToggle = { viewModel.toggleLayer(layer) },
                    onVolume = { viewModel.setLayerVolume(layer.id, it) },
                    onRename = { renamingLayer = layer },
                    onAutoPlay = { viewModel.setAutoPlay(layer.id, it) },
                    onLoop = { viewModel.setLoop(layer.id, it) },
                    onRemove = { removingLayer = layer },
                )
            }
        }
    }

    if (renamingScene) {
        TextInputDialog(
            title = "Rename scene",
            initialValue = current.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameScene(it); renamingScene = false },
            onDismiss = { renamingScene = false },
        )
    }
    renamingLayer?.let { layer ->
        TextInputDialog(
            title = "Rename sound",
            initialValue = layer.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameLayer(layer.id, it); renamingLayer = null },
            onDismiss = { renamingLayer = null },
        )
    }
    removingLayer?.let { layer ->
        ConfirmDialog(
            title = "Remove \"${layer.name}\"?",
            message = "It is removed from this scene. The audio file on your phone is not deleted.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeLayer(layer.id); removingLayer = null },
            onDismiss = { removingLayer = null },
        )
    }
}

@Composable
private fun SceneControls(
    isPlaying: Boolean,
    hasLayers: Boolean,
    masterVolume: Float,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onMasterVolume: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPlay, enabled = hasLayers, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Play scene", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onStop, enabled = isPlaying, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Stop all", Modifier.padding(start = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text("Master", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
            Slider(value = masterVolume, onValueChange = onMasterVolume, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LayerCard(
    layer: SoundLayer,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    onRename: () -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onLoop: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = if (isPlaying) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        },
    ) {
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
                Slider(value = layer.volume, onValueChange = onVolume)
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
                        onClick = { menuOpen = false; onAutoPlay(!layer.autoPlay) },
                    )
                    DropdownMenuItem(
                        text = { Text("Loop") },
                        leadingIcon = { CheckMark(layer.loop) },
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
        if (checked) Icon(Icons.Default.Check, contentDescription = "On")
    }
}
