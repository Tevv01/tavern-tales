package dev.tevv.taverntales.ui.hue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tevv.taverntales.hue.FoundBridge
import dev.tevv.taverntales.hue.HueBridge
import dev.tevv.taverntales.model.HueSceneRef
import dev.tevv.taverntales.ui.components.ChoiceDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HueSetupScreen(viewModel: HueSetupViewModel, onBack: () -> Unit) {
    val bridge by viewModel.bridge.collectAsStateWithLifecycle()
    val found by viewModel.found.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var choosingRoom by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Philips Hue") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = bridge
            if (current != null) {
                Connected(current, scenes, onRetry = viewModel::loadScenes, onDisconnect = viewModel::disconnect)
                LightingRoom(current, onChoose = {
                    viewModel.loadGroups()
                    choosingRoom = true
                })
            } else {
                NotConnected(found, searching, onSearch = viewModel::search, onPair = viewModel::pair)
            }
        }
    }

    if (choosingRoom) {
        val loaded = groups
        when {
            loaded == null -> AlertDialog(
                onDismissRequest = { choosingRoom = false },
                title = { Text("Room for scene lighting") },
                text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
                confirmButton = {},
            )
            loaded.isFailure -> AlertDialog(
                onDismissRequest = { choosingRoom = false },
                title = { Text("Couldn't load rooms") },
                text = { Text(loaded.exceptionOrNull()?.message.orEmpty()) },
                confirmButton = { TextButton(onClick = { choosingRoom = false }) { Text("OK") } },
            )
            else -> ChoiceDialog(
                title = "Room for scene lighting",
                options = loaded.getOrThrow(),
                label = { "${it.name} (${it.type})" },
                selected = loaded.getOrThrow().find { it.id == bridge?.group?.id },
                onPick = {
                    viewModel.setGroup(it)
                    choosingRoom = false
                },
                onDismiss = { choosingRoom = false },
            )
        }
    }

    when (val state = pairing) {
        PairingState.Idle -> Unit
        is PairingState.Waiting -> AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Router, contentDescription = null) },
            title = { Text("Press the link button") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Press the big round button on top of your Hue Bridge.")
                    CircularProgressIndicator(Modifier.padding(top = 20.dp, bottom = 8.dp))
                    Text(
                        state.secondsLeft?.let { "Waiting... $it s" } ?: "Contacting the bridge at ${state.ip}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::cancelPairing) { Text("Cancel") } },
        )
        is PairingState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Couldn't connect") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun Connected(
    bridge: HueBridge,
    scenes: Result<List<HueSceneRef>>?,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Connected to ${bridge.name}", style = MaterialTheme.typography.titleMedium)
                    Text(bridge.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                scenes == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                scenes.isSuccess -> Text(
                    "${scenes.getOrThrow().size} Hue scenes available. Link one to each scene from the Lights row on the scene's page.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Column {
                    Text(
                        "Couldn't reach the bridge: ${scenes.exceptionOrNull()?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
    OutlinedButton(onClick = onDisconnect) { Text("Disconnect this bridge") }
}

@Composable
private fun LightingRoom(bridge: HueBridge, onChoose: () -> Unit) {
    Text("Room for scene lighting", style = MaterialTheme.typography.titleMedium)
    Text(
        "Light setups made in Tavern Tales (like the ones the built-in scenes come with) are applied to the lights in this room or zone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ListItem(
        headlineContent = { Text(bridge.group?.name ?: "Not chosen yet") },
        supportingContent = { Text(bridge.group?.type?.replaceFirstChar { it.uppercase() } ?: "Choose where your table is") },
        leadingContent = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
        trailingContent = { Button(onClick = onChoose) { Text(if (bridge.group == null) "Choose" else "Change") } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun NotConnected(
    found: List<FoundBridge>,
    searching: Boolean,
    onSearch: () -> Unit,
    onPair: (String) -> Unit,
) {
    Text(
        "Connect your Hue Bridge to switch the lights along with your scenes. Your phone needs to be on the same Wi-Fi as the bridge.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Bridges on this Wi-Fi", style = MaterialTheme.typography.titleMedium)
    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
    found.forEach { bridge ->
        ListItem(
            headlineContent = { Text(bridge.name) },
            supportingContent = { Text(bridge.ip) },
            leadingContent = { Icon(Icons.Default.Router, contentDescription = null) },
            trailingContent = { Button(onClick = { onPair(bridge.ip) }) { Text("Connect") } },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
    if (!searching && found.isEmpty()) {
        Text("No bridge found.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!searching) TextButton(onClick = onSearch) { Text("Search again") }

    Text("Or enter its IP address", style = MaterialTheme.typography.titleMedium)
    Text(
        "You can find it in the Hue app under the bridge's settings.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    var ip by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = { Text("192.168.1.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onPair(ip) }, enabled = IP_PATTERN.matches(ip)) { Text("Connect") }
    }
}

private val IP_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")
