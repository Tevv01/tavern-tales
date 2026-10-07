package dev.tevv.taverntales.ui.scene

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.audio.ImportedAudio
import dev.tevv.taverntales.data.BackgroundStore
import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.model.collectionOf
import dev.tevv.taverntales.model.findScene
import dev.tevv.taverntales.model.newId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SceneViewModel(
    private val sceneId: String,
    private val repository: LibraryRepository,
    private val mixer: AmbienceMixer,
    private val backgrounds: BackgroundStore,
) : ViewModel() {
    /** Null once the scene has been deleted. */
    val scene: StateFlow<Scene?> = repository.library
        .map { it.findScene(sceneId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.library.value.findScene(sceneId))

    val collection: StateFlow<SceneCollection?> = repository.library
        .map { it.collectionOf(sceneId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.library.value.collectionOf(sceneId))

    val collections: StateFlow<List<SceneCollection>> = repository.library
        .map { it.collections }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.library.value.collections)

    val events: StateFlow<List<SoundEvent>> = repository.library
        .map { it.events }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.library.value.events)

    val mixerState = mixer.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Playback

    fun playScene() {
        scene.value?.let(mixer::startScene)
    }

    fun stopAll() = mixer.stopAll()

    fun toggleLayer(layer: SoundLayer) = mixer.toggleLayer(sceneId, layer)

    fun setMasterVolume(volume: Float) = mixer.setMasterVolume(volume)

    fun playEvent(event: SoundEvent) = mixer.playEvent(event)

    // Scene

    fun renameScene(name: String) = repository.renameScene(sceneId, name)

    fun moveScene(collectionId: String) = repository.moveScene(sceneId, collectionId)

    fun deleteScene() {
        if (mixer.state.value.sceneId == sceneId) mixer.stopAll()
        backgrounds.delete(scene.value?.background)
        repository.deleteScene(sceneId)
    }

    fun setBackground(uri: Uri) {
        viewModelScope.launch {
            try {
                val background = backgrounds.import(uri)
                backgrounds.delete(scene.value?.background)
                repository.setBackground(sceneId, background)
            } catch (e: Exception) {
                _messages.tryEmit("Couldn't use that image")
            }
        }
    }

    fun removeBackground() {
        backgrounds.delete(scene.value?.background)
        repository.setBackground(sceneId, null)
    }

    // Layers

    fun addLayers(audio: List<ImportedAudio>) =
        repository.addLayers(sceneId, audio.map { SoundLayer(id = newId(), name = it.name, uri = it.uri) })

    fun setLayerVolume(layerId: String, volume: Float) {
        mixer.setLayerVolume(layerId, volume)
        repository.updateLayer(sceneId, layerId) { it.copy(volume = volume) }
    }

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

    // Events (shared by all scenes)

    /** Adds an event for the picked file and returns its id so the editor can open on it. */
    fun addEvent(audio: ImportedAudio): String {
        val id = newId()
        repository.addEvent(SoundEvent(id = id, name = audio.name, uri = audio.uri))
        return id
    }

    fun updateEvent(eventId: String, transform: (SoundEvent) -> SoundEvent) = repository.updateEvent(eventId, transform)

    fun removeEvent(eventId: String) = repository.removeEvent(eventId)
}
