package dev.tevv.taverntales.audio

import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.data.Preferences
import dev.tevv.taverntales.hue.DEFAULT_LIGHT_TRANSITION_MS
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
 *
 * Switching from one playing scene to another crossfades sound and lights over the same time, the
 * user's [Preferences.sceneChange]; starting from silence uses the quick default fades.
 */
class SceneLauncher(
    private val mixer: AmbienceMixer,
    private val hue: HueController,
    private val library: LibraryRepository,
    private val preferences: Preferences,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            mixer.state.map { it.sceneId }.distinctUntilChanged().collect { if (it == null) hue.stopMotion() }
        }
    }

    fun start(scene: Scene) {
        val transitionMs = lightTransitionFor(scene.id)
        mixer.startScene(scene, crossfadeMs)
        applyLights(scene, transitionMs)
    }

    /**
     * Sets the lights for [scene]: its own light setup if it has one, else its linked Hue scene,
     * changing over [transitionMs].
     */
    fun applyLights(scene: Scene, transitionMs: Long = DEFAULT_LIGHT_TRANSITION_MS) {
        val animate = mixer.state.value.sceneId == scene.id
        scene.lighting?.let { hue.apply(it, animate, transitionMs) } ?: scene.lights?.let { hue.recall(it, transitionMs) }
    }

    private val crossfadeMs get() = preferences.sceneChange.value.durationMs

    /** The scene change time if [sceneId] replaces a playing scene, else the usual quick change. */
    private fun lightTransitionFor(sceneId: String) =
        if (mixer.isSwitching(sceneId)) crossfadeMs else DEFAULT_LIGHT_TRANSITION_MS

    /** Shows [setup] on the lights, moving if [sceneId] is the scene currently playing. */
    fun previewLighting(sceneId: String, setup: LightSetup) {
        hue.apply(setup, animate = mixer.state.value.sceneId == sceneId)
    }

    /**
     * Plays an event's sound and, if it has one, its light flash. Afterwards the lights return to the
     * playing scene's light setup (moving again), or to how they were if there is none. With event
     * sounds switched off ([Preferences.eventSounds]) only the lights flash, unless [withSound]
     * forces the sound (previewing an event).
     */
    fun playEvent(event: SoundEvent, withSound: Boolean = preferences.eventSounds.value) {
        if (withSound) mixer.playEvent(event) else mixer.markEvent(event)
        val flash = event.flash ?: return
        val lighting = mixer.state.value.sceneId?.let { library.library.value.findScene(it) }?.lighting
        hue.flash(flash, restoreTo = lighting, animate = lighting != null)
    }

    /** Toggles one layer; if that makes [scene] the active scene, its lights are switched too. */
    fun toggleLayer(scene: Scene, layer: SoundLayer) {
        val switching = mixer.state.value.sceneId != scene.id
        val transitionMs = lightTransitionFor(scene.id)
        mixer.toggleLayer(scene.id, layer, crossfadeMs)
        if (switching) applyLights(scene, transitionMs)
    }
}
