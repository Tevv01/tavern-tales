package dev.tevv.taverntales.audio

import dev.tevv.taverntales.hue.HueController
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SoundLayer

/**
 * Starting a scene means sound and lights together. Screens go through this instead of calling
 * [AmbienceMixer.startScene] directly, so the linked Hue scene follows whichever scene becomes active.
 */
class SceneLauncher(private val mixer: AmbienceMixer, private val hue: HueController) {
    fun start(scene: Scene) {
        mixer.startScene(scene)
        applyLights(scene)
    }

    /** Sets the lights for [scene]: its own light setup if it has one, else its linked Hue scene. */
    fun applyLights(scene: Scene) {
        scene.lighting?.let(hue::apply) ?: scene.lights?.let(hue::recall)
    }

    /** Toggles one layer; if that makes [scene] the active scene, its lights are switched too. */
    fun toggleLayer(scene: Scene, layer: SoundLayer) {
        val switching = mixer.state.value.sceneId != scene.id
        mixer.toggleLayer(scene.id, layer)
        if (switching) applyLights(scene)
    }
}
