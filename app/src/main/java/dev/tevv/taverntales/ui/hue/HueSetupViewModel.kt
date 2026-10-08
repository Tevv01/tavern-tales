package dev.tevv.taverntales.ui.hue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tevv.taverntales.hue.FoundBridge
import dev.tevv.taverntales.hue.HueController
import dev.tevv.taverntales.hue.HueGroup
import dev.tevv.taverntales.model.HueSceneRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PairingState {
    data object Idle : PairingState
    /** Waiting for the link button; [secondsLeft] is null until the bridge has answered once. */
    data class Waiting(val ip: String, val secondsLeft: Int?) : PairingState
    data class Failed(val message: String) : PairingState
}

class HueSetupViewModel(private val hue: HueController) : ViewModel() {
    val bridge = hue.bridge

    private val _found = MutableStateFlow<List<FoundBridge>>(emptyList())
    val found: StateFlow<List<FoundBridge>> = _found.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    /** Scenes on the connected bridge (null while loading), shown as a connection check. */
    private val _scenes = MutableStateFlow<Result<List<HueSceneRef>>?>(null)
    val scenes: StateFlow<Result<List<HueSceneRef>>?> = _scenes.asStateFlow()

    /** Rooms and zones for the lighting target picker (null while loading). */
    private val _groups = MutableStateFlow<Result<List<HueGroup>>?>(null)
    val groups: StateFlow<Result<List<HueGroup>>?> = _groups.asStateFlow()

    fun loadGroups() {
        _groups.value = null
        viewModelScope.launch { _groups.value = runCatching { hue.groups() } }
    }

    fun setGroup(group: HueGroup) {
        viewModelScope.launch { hue.setGroup(group) }
    }

    private var searchJob: Job? = null
    private var pairJob: Job? = null

    init {
        if (bridge.value == null) search() else loadScenes()
    }

    fun search() {
        searchJob?.cancel()
        _found.value = emptyList()
        _searching.value = true
        searchJob = viewModelScope.launch {
            withTimeoutOrNull(SEARCH_MS) {
                hue.discover().collect { bridge ->
                    _found.update { list -> if (list.any { it.ip == bridge.ip }) list else list + bridge }
                }
            }
            _searching.value = false
        }
    }

    fun pair(ip: String) {
        searchJob?.cancel()
        _searching.value = false
        pairJob?.cancel()
        _pairing.value = PairingState.Waiting(ip, null)
        pairJob = viewModelScope.launch {
            hue.pair(ip.trim()) { secondsLeft -> _pairing.value = PairingState.Waiting(ip, secondsLeft) }
                .onSuccess {
                    _pairing.value = PairingState.Idle
                    loadScenes()
                }
                .onFailure { _pairing.value = PairingState.Failed(it.message ?: "Pairing failed") }
        }
    }

    fun cancelPairing() {
        pairJob?.cancel()
        _pairing.value = PairingState.Idle
    }

    fun dismissError() {
        _pairing.value = PairingState.Idle
    }

    fun loadScenes() {
        _scenes.value = null
        viewModelScope.launch { _scenes.value = runCatching { hue.scenes() } }
    }

    fun disconnect() {
        hue.disconnect()
        _scenes.value = null
        search()
    }

    private companion object {
        const val SEARCH_MS = 15_000L
    }
}
