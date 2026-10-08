package dev.tevv.taverntales.audio

import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.hue.HueController
import dev.tevv.taverntales.model.LightSetup
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.findScene
import dev.tevv.taverntales.model.SoundLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Starting a scene means sound and lights together. Screens go through this instead of calling
 * [AmbienceMixer.startScene] directly, so the lights follow whichever scene becomes active.
 *
 * A light setup's gentle movement only runs while its scene is the active one; it stops when the
 * sound is stopped (and is replaced when another scene's lights are applied).
 */
class SceneLauncher(
    private val mixer: AmbienceMixer,
    private val hue: HueController,
    private val library: LibraryRepository,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            mixer.state.map { it.sceneId }.distinctUntilChanged().collect { if (it == null) hue.stopMotion() }
        }
    }

    fun start(scene: Scene) {
        mixer.startScene(scene)
        applyLights(scene)
    }

    /** Sets the lights for [scene]: its own light setup if it has one, else its linked Hue scene. */
    fun applyLights(scene: Scene) {
        scene.lighting?.let { previewLighting(scene.id, it) } ?: scene.lights?.let(hue::recall)
    }

    /** Shows [setup] on the lights, moving if [sceneId] is the scene currently playing. */
    fun previewLighting(sceneId: String, setup: LightSetup) {
        hue.apply(setup, animate = mixer.state.value.sceneId == sceneId)
    }

    /**
     * Plays an event's sound and, if it has one, its light flash. Afterwards the lights return to the
     * playing scene's light setup (moving again), or to how they were if there is none.
     */
    fun playEvent(event: SoundEvent) {
        mixer.playEvent(event)
        val flash = event.flash ?: return
        val lighting = mixer.state.value.sceneId?.let { library.library.value.findScene(it) }?.lighting
        hue.flash(flash, restoreTo = lighting, animate = lighting != null)
    }

    /** Toggles one layer; if that makes [scene] the active scene, its lights are switched too. */
    fun toggleLayer(scene: Scene, layer: SoundLayer) {
        val switching = mixer.state.value.sceneId != scene.id
        mixer.toggleLayer(scene.id, layer)
        if (switching) applyLights(scene)
    }
}
