package dev.tevv.taverntales.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SoundLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What is currently audible. Only one scene is active at a time; [playing] holds the ids of its
 * layers that are on (layers that are fading out are already excluded).
 */
data class MixerState(
    val sceneId: String? = null,
    val playing: Set<String> = emptySet(),
    val masterVolume: Float = 1f,
)

/**
 * Plays any number of sound layers at once, one [ExoPlayer] per layer, with fades on start/stop.
 *
 * Lives for the whole process (owned by [dev.tevv.taverntales.AppContainer]), not by an Activity, so
 * audio continues when the UI is closed. [AmbienceService] is started while anything plays to keep
 * the process in the foreground. Must be called on the main thread; [scope] must use Dispatchers.Main.
 */
class AmbienceMixer(private val context: Context, private val scope: CoroutineScope) {

    private class Voice(val player: ExoPlayer, var layerVolume: Float) {
        /** Fade multiplier 0..1. */
        var fade = 0f
        var fadeJob: Job? = null
        /** False once a stop was requested, even while the fade-out is still running. */
        var on = true
    }

    private val voices = mutableMapOf<String, Voice>()
    private var serviceRunning = false

    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Makes [scene] the active scene and starts its auto-play layers. */
    fun startScene(scene: Scene) {
        switchTo(scene.id)
        scene.layers.filter { it.autoPlay }.forEach(::play)
    }

    fun toggleLayer(sceneId: String, layer: SoundLayer) {
        if (layer.id in _state.value.playing) {
            stopLayer(layer.id)
        } else {
            switchTo(sceneId)
            play(layer)
        }
    }

    fun stopLayer(layerId: String) {
        val voice = voices[layerId] ?: return
        if (!voice.on) return
        voice.on = false
        fade(voice, target = 0f, durationMs = FADE_OUT_MS) { release(layerId, voice) }
        publish()
    }

    fun stopAll() {
        voices.keys.toList().forEach(::stopLayer)
        _state.update { it.copy(sceneId = null) }
    }

    fun setLayerVolume(layerId: String, volume: Float) {
        val voice = voices[layerId] ?: return
        voice.layerVolume = volume
        applyVolume(voice)
    }

    fun setLayerLoop(layerId: String, loop: Boolean) {
        voices[layerId]?.player?.repeatMode = repeatModeFor(loop)
    }

    fun setMasterVolume(volume: Float) {
        _state.update { it.copy(masterVolume = volume) }
        voices.values.forEach(::applyVolume)
    }

    /** Fades out everything from the previously active scene. */
    private fun switchTo(sceneId: String) {
        if (_state.value.sceneId == sceneId) return
        voices.keys.toList().forEach(::stopLayer)
        _state.update { it.copy(sceneId = sceneId) }
    }

    private fun play(layer: SoundLayer) {
        val voice = voices[layer.id] ?: createVoice(layer).also { voices[layer.id] = it }
        voice.on = true
        voice.layerVolume = layer.volume
        // A one-shot that already finished is restarted from the beginning.
        if (voice.player.playbackState == Player.STATE_ENDED) voice.player.seekTo(0)
        voice.player.play()
        fade(voice, target = 1f, durationMs = FADE_IN_MS) {}
        publish()
    }

    private fun createVoice(layer: SoundLayer): Voice {
        val player = ExoPlayer.Builder(context)
            // Focus is not requested so layers don't pause each other, and a music app can play alongside.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val voice = Voice(player, layer.volume)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _errors.tryEmit("Couldn't play \"${layer.name}\" (${error.errorCodeName})")
                release(layer.id, voice)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // One-shots end by themselves; reflect that in the UI.
                if (playbackState == Player.STATE_ENDED && voice.on) {
                    voice.on = false
                    release(layer.id, voice)
                }
            }
        })
        player.repeatMode = repeatModeFor(layer.loop)
        player.volume = 0f
        player.setMediaItem(MediaItem.fromUri(layer.uri))
        player.prepare()
        return voice
    }

    private fun fade(voice: Voice, target: Float, durationMs: Long, onDone: () -> Unit) {
        voice.fadeJob?.cancel()
        voice.fadeJob = scope.launch {
            val start = voice.fade
            val steps = (durationMs / FADE_STEP_MS).coerceAtLeast(1)
            for (i in 1..steps) {
                voice.fade = start + (target - start) * i / steps
                applyVolume(voice)
                delay(FADE_STEP_MS)
            }
            onDone()
        }
    }

    private fun applyVolume(voice: Voice) {
        voice.player.volume = gain(voice.layerVolume) * gain(_state.value.masterVolume) * voice.fade
    }

    private fun release(layerId: String, voice: Voice) {
        voice.fadeJob?.cancel()
        voice.player.release()
        if (voices[layerId] === voice) voices.remove(layerId)
        publish()
    }

    private fun publish() {
        val playing = voices.filterValues { it.on }.keys.toSet()
        _state.update { it.copy(playing = playing) }
        // The service stays up until fade-outs have finished (voices released), which also keeps
        // stopService() from racing a startForegroundService() that hasn't reached startForeground yet.
        if (playing.isNotEmpty() && !serviceRunning) {
            serviceRunning = true
            AmbienceService.start(context)
        } else if (voices.isEmpty() && serviceRunning) {
            serviceRunning = false
            AmbienceService.stop(context)
        }
    }

    private fun repeatModeFor(loop: Boolean) = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

    companion object {
        private const val FADE_IN_MS = 1500L
        private const val FADE_OUT_MS = 1500L
        private const val FADE_STEP_MS = 40L

        /** Maps a 0..1 slider position to amplitude gain; squaring makes the slider feel even to the ear. */
        fun gain(slider: Float): Float = slider.coerceIn(0f, 1f).let { it * it }
    }
}
