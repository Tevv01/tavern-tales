package dev.tevv.pocketbard.ui.scene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tevv.pocketbard.audio.AmbienceMixer
import dev.tevv.pocketbard.data.SceneRepository
import dev.tevv.pocketbard.model.Scene
import dev.tevv.pocketbard.model.SoundLayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SceneViewModel(
    private val sceneId: String,
    private val repository: SceneRepository,
    private val mixer: AmbienceMixer,
) : ViewModel() {
    /** Null once the scene has been deleted. */
    val scene: StateFlow<Scene?> = repository.scenes
        .map { scenes -> scenes.find { it.id == sceneId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.scenes.value.find { it.id == sceneId })

    val mixerState = mixer.state

    fun playScene() {
        scene.value?.let(mixer::startScene)
    }

    fun stopAll() = mixer.stopAll()

    fun toggleLayer(layer: SoundLayer) = mixer.toggleLayer(sceneId, layer)

    fun setLayerVolume(layerId: String, volume: Float) {
        mixer.setLayerVolume(layerId, volume)
        repository.updateLayer(sceneId, layerId) { it.copy(volume = volume) }
    }

    fun setMasterVolume(volume: Float) = mixer.setMasterVolume(volume)

    fun addLayers(layers: List<SoundLayer>) = repository.addLayers(sceneId, layers)

    fun renameLayer(layerId: String, name: String) =
        repository.updateLayer(sceneId, layerId) { it.copy(name = name) }

    fun setAutoPlay(layerId: String, autoPlay: Boolean) =
        repository.updateLayer(sceneId, layerId) { it.copy(autoPlay = autoPlay) }

    fun setLoop(layerId: String, loop: Boolean) {
        mixer.setLayerLoop(layerId, loop)
        repository.updateLayer(sceneId, layerId) { it.copy(loop = loop) }
    }

    fun removeLayer(layerId: String) {
        mixer.stopLayer(layerId)
        repository.removeLayer(sceneId, layerId)
    }

    fun renameScene(name: String) = repository.renameScene(sceneId, name)
}
