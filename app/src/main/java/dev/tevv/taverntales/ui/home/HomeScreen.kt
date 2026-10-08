package dev.tevv.taverntales.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.audio.MixerState
import dev.tevv.taverntales.data.SceneChange
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.collectionOf
import dev.tevv.taverntales.model.findScene
import dev.tevv.taverntales.ui.components.BottomScrim
import dev.tevv.taverntales.ui.components.ChoiceDialog
import dev.tevv.taverntales.ui.components.ConfirmDialog
import dev.tevv.taverntales.ui.components.SceneArt
import dev.tevv.taverntales.ui.components.TextInputDialog
import java.time.LocalDate

private sealed interface HomeDialog {
    data object NewCollection : HomeDialog
    data class RenameCollection(val collection: SceneCollection) : HomeDialog
    data class DeleteCollection(val collection: SceneCollection) : HomeDialog
    data class NewScene(val collectionId: String) : HomeDialog
    data class RenameScene(val scene: Scene) : HomeDialog
    data class MoveScene(val scene: Scene) : HomeDialog
    data class DeleteScene(val scene: Scene) : HomeDialog
    data object ChooseSceneChange : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenScene: (sceneId: String) -> Unit,
    onOpenHueSetup: () -> Unit,
    onReportBug: () -> Unit,
    /** Null when this build can't report crashes (debug builds): the menu item is shown greyed out. */
    crashReportsEnabled: Boolean?,
    onCrashReportsChange: (Boolean) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val mixer by viewModel.mixerState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val activeScene = mixer.sceneId?.let { library.findScene(it) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val sceneChange by viewModel.sceneChange.collectAsStateWithLifecycle()
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val backUpTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.backUp(uri)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreFrom = uri
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Scenes",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the menu") }
                },
                actions = {
                    IconButton(onClick = onOpenHueSetup) {
                        Icon(Icons.Default.Lightbulb, contentDescription = "Philips Hue")
                    }
                    IconButton(onClick = { dialog = HomeDialog.NewCollection }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New collection")
                    }
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Back up library") },
                                leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    backUpTo.launch("Tavern Tales backup ${LocalDate.now()}.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Restore from backup") },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    pickBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Scene changes: ${sceneChange.label}") },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    dialog = HomeDialog.ChooseSceneChange
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Report a bug") },
                                leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onReportBug()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (crashReportsEnabled != null) "Send crash reports" else "Crash reports (off in debug builds)") },
                                leadingIcon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                                trailingIcon = {
                                    if (crashReportsEnabled == true) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                enabled = crashReportsEnabled != null,
                                modifier = Modifier.semantics { stateDescription = if (crashReportsEnabled == true) "On" else "Off" },
                                onClick = {
                                    menuOpen = false
                                    crashReportsEnabled?.let { onCrashReportsChange(!it) }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                        onToggleCollapsed = { viewModel.setCollapsed(collection.id, !collection.collapsed) },
                        onDialog = { dialog = it },
                    )
                }
            }
        }
    }

    restoreFrom?.let { source ->
        AlertDialog(
            onDismissRequest = { restoreFrom = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "Replace your library with the backup (for a new phone or a reinstall), or add the backup's " +
                        "collections and events to what you have now?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(source, replace = true)
                    restoreFrom = null
                }) { Text("Replace") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { restoreFrom = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        viewModel.restore(source, replace = false)
                        restoreFrom = null
                    }) { Text("Add") }
                }
            },
        )
    }
    busy?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(label) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
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
        HomeDialog.ChooseSceneChange -> ChoiceDialog(
            title = "Fade between scenes",
            options = SceneChange.entries,
            label = { it.description },
            selected = sceneChange,
            onPick = { viewModel.setSceneChange(it); dialog = null },
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
    onToggleCollapsed: () -> Unit,
    onDialog: (HomeDialog) -> Unit,
) {
    val playingHere = collection.scenes.any { it.id == mixer.sceneId } && mixer.playing.isNotEmpty()
    val arrow by animateFloatAsState(if (collection.collapsed) -90f else 0f, label = "collapse arrow")
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
            // The whole title area folds and unfolds the collection.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        onClickLabel = if (collection.collapsed) "Show scenes" else "Hide scenes",
                        role = Role.Button,
                        onClick = onToggleCollapsed,
                    )
                    .semantics {
                        heading()
                        stateDescription = if (collection.collapsed) "Collapsed" else "Expanded"
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrow),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(collection.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (collection.scenes.size == 1) "1 scene" else "${collection.scenes.size} scenes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (collection.collapsed && playingHere) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp).size(14.dp),
                            )
                            Text("Playing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            IconButton(onClick = { onDialog(HomeDialog.NewScene(collection.id)) }) {
                Icon(Icons.Default.Add, contentDescription = "New scene in ${collection.name}")
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
        AnimatedVisibility(visible = !collection.collapsed, enter = expandVertically(), exit = shrinkVertically()) {
            // Every scene at once, wrapping into rows: three columns on phones, more on wider screens.
            BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // With large text, fewer and wider tiles so scene names still fit (at least two columns).
                val fontScale = LocalDensity.current.fontScale
                val columns = minOf(maxOf(3, (maxWidth / 150.dp).toInt()), (maxWidth / (110.dp * fontScale)).toInt()).coerceAtLeast(2)
                Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
                    if (collection.scenes.isEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            NewSceneTile(Modifier.weight(1f), onClick = { onDialog(HomeDialog.NewScene(collection.id)) })
                            repeat(columns - 1) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    collection.scenes.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            row.forEach { scene ->
                                key(scene.id) {
                                    SceneTile(
                                        scene = scene,
                                        isPlaying = scene.id == mixer.sceneId && mixer.playing.isNotEmpty(),
                                        canMove = canMoveScenes,
                                        onOpen = { onOpenScene(scene.id) },
                                        onTogglePlay = { onTogglePlay(scene) },
                                        onDialog = onDialog,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(20.dp)
private val TileGap = 10.dp

/** Tiles fill their grid cell's width and keep the scene art's tall shape. */
private val TileModifier = Modifier.fillMaxWidth().aspectRatio(5f / 7f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneTile(
    scene: Scene,
    isPlaying: Boolean,
    canMove: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onDialog: (HomeDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            TileModifier
                .clip(TileShape)
                .then(if (isPlaying) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), TileShape) else Modifier)
                .combinedClickable(
                    onClick = onOpen,
                    onClickLabel = "Open",
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = "Rename, move or delete",
                ),
        ) {
            SceneArt(scene, Modifier.fillMaxSize(), fallbackIconSize = 56.dp)
            BottomScrim()
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                )
            }
            if (scene.layers.isNotEmpty()) {
                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp),
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
private fun NewSceneTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .then(TileModifier)
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
                .clickable(onClickLabel = "Open scene", onClick = onOpen)
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
