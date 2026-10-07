package dev.tevv.pocketbard.ui.scenes

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.pocketbard.audio.MixerState
import dev.tevv.pocketbard.model.Scene
import dev.tevv.pocketbard.ui.components.ConfirmDialog
import dev.tevv.pocketbard.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenesScreen(
    viewModel: ScenesViewModel,
    snackbar: SnackbarHostState,
    onOpenScene: (sceneId: String) -> Unit,
) {
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Scene?>(null) }
    var deleting by remember { mutableStateOf<Scene?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pocket Bard") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New scene") },
            )
        },
        bottomBar = {
            val active = scenes.find { it.id == mixer.sceneId }
            if (active != null && mixer.playing.isNotEmpty()) {
                NowPlayingBar(active, mixer, onOpen = { onOpenScene(active.id) }, onStop = viewModel::stopAll)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (scenes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No scenes yet. Create one, like \"Town\" or \"Forest\", then add sounds to it.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(scenes, key = { it.id }) { scene ->
                    SceneCard(
                        scene = scene,
                        isPlaying = scene.id == mixer.sceneId && mixer.playing.isNotEmpty(),
                        onOpen = { onOpenScene(scene.id) },
                        onTogglePlay = { viewModel.togglePlay(scene) },
                        onRename = { renaming = scene },
                        onDelete = { deleting = scene },
                    )
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New scene",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                creating = false
                onOpenScene(viewModel.createScene(name))
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { scene ->
        TextInputDialog(
            title = "Rename scene",
            initialValue = scene.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameScene(scene.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { scene ->
        ConfirmDialog(
            title = "Delete \"${scene.name}\"?",
            message = "The scene and its sound list are removed. Your audio files are not deleted.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteScene(scene.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SceneCard(
    scene: Scene,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        colors = if (isPlaying) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onTogglePlay, enabled = scene.layers.isNotEmpty()) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop ${scene.name}" else "Play ${scene.name}",
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(scene.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (scene.layers.size) {
                        0 -> "No sounds yet"
                        1 -> "1 sound"
                        else -> "${scene.layers.size} sounds"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isPlaying) Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
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
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(scene: Scene, mixer: MixerState, onOpen: () -> Unit, onStop: () -> Unit) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp).size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(scene.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (mixer.playing.size == 1) "1 sound playing" else "${mixer.playing.size} sounds playing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpen) { Icon(Icons.Default.Tune, contentDescription = "Open ${scene.name}") }
        FilledTonalButton(onClick = onStop, modifier = Modifier.padding(end = 16.dp)) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Text("Stop", Modifier.padding(start = 8.dp))
        }
    }
}
