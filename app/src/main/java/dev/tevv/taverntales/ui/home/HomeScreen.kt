package dev.tevv.taverntales.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.audio.MixerState
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.collectionOf
import dev.tevv.taverntales.model.findScene
import dev.tevv.taverntales.ui.components.BottomScrim
import dev.tevv.taverntales.ui.components.ChoiceDialog
import dev.tevv.taverntales.ui.components.ConfirmDialog
import dev.tevv.taverntales.ui.components.SceneArt
import dev.tevv.taverntales.ui.components.TextInputDialog

private sealed interface HomeDialog {
    data object NewCollection : HomeDialog
    data class RenameCollection(val collection: SceneCollection) : HomeDialog
    data class DeleteCollection(val collection: SceneCollection) : HomeDialog
    data class NewScene(val collectionId: String) : HomeDialog
    data class RenameScene(val scene: Scene) : HomeDialog
    data class MoveScene(val scene: Scene) : HomeDialog
    data class DeleteScene(val scene: Scene) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    snackbar: SnackbarHostState,
    onOpenScene: (sceneId: String) -> Unit,
    onOpenHueSetup: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val activeScene = mixer.sceneId?.let { library.findScene(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Tavern Tales", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    IconButton(onClick = onOpenHueSetup) {
                        Icon(Icons.Default.Lightbulb, contentDescription = "Philips Hue")
                    }
                    IconButton(onClick = { dialog = HomeDialog.NewCollection }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New collection")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (activeScene != null && mixer.playing.isNotEmpty()) {
                NowPlayingBar(activeScene, mixer, onOpen = { onOpenScene(activeScene.id) }, onStop = viewModel::stopAll)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (library.collections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No collections yet. Tap the folder icon above to create one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(library.collections, key = { it.id }) { collection ->
                    CollectionSection(
                        collection = collection,
                        mixer = mixer,
                        canMoveScenes = library.collections.size > 1,
                        onOpenScene = onOpenScene,
                        onTogglePlay = viewModel::togglePlay,
                        onDialog = { dialog = it },
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        HomeDialog.NewCollection -> TextInputDialog(
            title = "New collection",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { viewModel.createCollection(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is HomeDialog.RenameCollection -> TextInputDialog(
            title = "Rename collection",
            initialValue = d.collection.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameCollection(d.collection.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is HomeDialog.DeleteCollection -> ConfirmDialog(
            title = "Delete \"${d.collection.name}\"?",
            message = when (d.collection.scenes.size) {
                0 -> "The collection is empty."
                1 -> "Its scene is deleted too. Your audio files are not."
                else -> "Its ${d.collection.scenes.size} scenes are deleted too. Your audio files are not."
            },
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteCollection(d.collection.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is HomeDialog.NewScene -> TextInputDialog(
            title = "New scene",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                dialog = null
                onOpenScene(viewModel.createScene(d.collectionId, name))
            },
            onDismiss = { dialog = null },
        )
        is HomeDialog.RenameScene -> TextInputDialog(
            title = "Rename scene",
            initialValue = d.scene.name,
            confirmLabel = "Rename",
            onConfirm = { viewModel.renameScene(d.scene.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is HomeDialog.MoveScene -> ChoiceDialog(
            title = "Move \"${d.scene.name}\" to",
            options = library.collections,
            label = { it.name },
            selected = library.collectionOf(d.scene.id),
            onPick = { viewModel.moveScene(d.scene.id, it.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is HomeDialog.DeleteScene -> ConfirmDialog(
            title = "Delete \"${d.scene.name}\"?",
            message = "The scene and its sound list are removed. Your audio files are not deleted.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteScene(d.scene.id); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun CollectionSection(
    collection: SceneCollection,
    mixer: MixerState,
    canMoveScenes: Boolean,
    onOpenScene: (String) -> Unit,
    onTogglePlay: (Scene) -> Unit,
    onDialog: (HomeDialog) -> Unit,
) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (collection.scenes.size == 1) "1 scene" else "${collection.scenes.size} scenes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Collection options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("New scene") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { menuOpen = false; onDialog(HomeDialog.NewScene(collection.id)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onDialog(HomeDialog.RenameCollection(collection)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDialog(HomeDialog.DeleteCollection(collection)) },
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collection.scenes, key = { it.id }) { scene ->
                SceneTile(
                    scene = scene,
                    isPlaying = scene.id == mixer.sceneId && mixer.playing.isNotEmpty(),
                    canMove = canMoveScenes,
                    onOpen = { onOpenScene(scene.id) },
                    onTogglePlay = { onTogglePlay(scene) },
                    onDialog = onDialog,
                )
            }
            item(key = "new-${collection.id}") {
                NewSceneTile(onClick = { onDialog(HomeDialog.NewScene(collection.id)) })
            }
        }
    }
}

private val TileShape = RoundedCornerShape(20.dp)
private val TileModifier = Modifier.size(width = 150.dp, height = 210.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneTile(
    scene: Scene,
    isPlaying: Boolean,
    canMove: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onDialog: (HomeDialog) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            TileModifier
                .clip(TileShape)
                .then(if (isPlaying) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), TileShape) else Modifier)
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
        ) {
            SceneArt(scene, Modifier.fillMaxSize(), fallbackIconSize = 56.dp)
            BottomScrim()
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    scene.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (scene.layers.size) {
                        0 -> "No sounds yet"
                        1 -> "1 sound"
                        else -> "${scene.layers.size} sounds"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                )
            }
            if (scene.layers.isNotEmpty()) {
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop ${scene.name}" else "Play ${scene.name}",
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onDialog(HomeDialog.RenameScene(scene)) },
            )
            if (canMove) {
                DropdownMenuItem(
                    text = { Text("Move to collection") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    onClick = { menuOpen = false; onDialog(HomeDialog.MoveScene(scene)) },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { menuOpen = false; onDialog(HomeDialog.DeleteScene(scene)) },
            )
        }
    }
}

@Composable
private fun NewSceneTile(onClick: () -> Unit) {
    Box(
        TileModifier
            .clip(TileShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Text("New scene", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun NowPlayingBar(scene: Scene, mixer: MixerState, onOpen: () -> Unit, onStop: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) {
                SceneArt(scene, Modifier.fillMaxSize(), fallbackIconSize = 24.dp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(scene.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (mixer.playing.size == 1) "1 sound playing" else "${mixer.playing.size} sounds playing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Stop", Modifier.padding(start = 8.dp))
            }
        }
    }
}
