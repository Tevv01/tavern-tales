package dev.tevv.taverntales.ui.home

import androidx.lifecycle.ViewModel
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.audio.SceneLauncher
import dev.tevv.taverntales.data.BackgroundStore
import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.findScene

class HomeViewModel(
    private val repository: LibraryRepository,
    private val mixer: AmbienceMixer,
    private val launcher: SceneLauncher,
    private val backgrounds: BackgroundStore,
) : ViewModel() {
    val library = repository.library
    val mixerState = mixer.state

    fun createCollection(name: String) = repository.createCollection(name)

    fun renameCollection(collectionId: String, name: String) = repository.renameCollection(collectionId, name)

    fun deleteCollection(collectionId: String) {
        val scenes = library.value.collections.find { it.id == collectionId }?.scenes.orEmpty()
        if (scenes.any { it.id == mixer.state.value.sceneId }) mixer.stopAll()
        scenes.forEach { backgrounds.delete(it.background) }
        repository.deleteCollection(collectionId)
    }

    fun createScene(collectionId: String, name: String): String = repository.createScene(collectionId, name)

    fun renameScene(sceneId: String, name: String) = repository.renameScene(sceneId, name)

    fun moveScene(sceneId: String, toCollectionId: String) = repository.moveScene(sceneId, toCollectionId)

    fun deleteScene(sceneId: String) {
        if (mixer.state.value.sceneId == sceneId) mixer.stopAll()
        backgrounds.delete(library.value.findScene(sceneId)?.background)
        repository.deleteScene(sceneId)
    }

    /** Stops [scene] if it is what's playing, otherwise starts it (fading out any other scene). */
    fun togglePlay(scene: Scene) {
        val state = mixer.state.value
        if (state.sceneId == scene.id && state.playing.isNotEmpty()) mixer.stopAll() else launcher.start(scene)
    }

    fun stopAll() = mixer.stopAll()
}
