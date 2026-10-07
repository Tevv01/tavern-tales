package dev.tevv.taverntales.ui.scenes

import androidx.lifecycle.ViewModel
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.data.SceneRepository
import dev.tevv.taverntales.model.Scene

class ScenesViewModel(
    private val repository: SceneRepository,
    private val mixer: AmbienceMixer,
) : ViewModel() {
    val scenes = repository.scenes
    val mixerState = mixer.state

    fun createScene(name: String): String = repository.createScene(name)

    fun renameScene(sceneId: String, name: String) = repository.renameScene(sceneId, name)

    fun deleteScene(sceneId: String) {
        if (mixer.state.value.sceneId == sceneId) mixer.stopAll()
        repository.deleteScene(sceneId)
    }

    /** Stops [scene] if it is what's playing, otherwise starts it (fading out any other scene). */
    fun togglePlay(scene: Scene) {
        val state = mixer.state.value
        if (state.sceneId == scene.id && state.playing.isNotEmpty()) mixer.stopAll() else mixer.startScene(scene)
    }

    fun stopAll() = mixer.stopAll()
}
