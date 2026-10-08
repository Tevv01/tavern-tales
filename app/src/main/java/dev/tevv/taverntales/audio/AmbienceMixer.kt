package dev.tevv.taverntales.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import kotlin.math.PI
import kotlin.math.sin
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
 * layers that are on (layers that are fading out are already excluded). [events] holds the ids of
 * event one-shots still sounding.
 */
data class MixerState(
    val sceneId: String? = null,
    val playing: Set<String> = emptySet(),
    val events: Set<String> = emptySet(),
    val masterVolume: Float = 1f,
    /** Silences everything without losing [masterVolume]. */
    val muted: Boolean = false,
) {
    /** The master volume actually applied. */
    val effectiveMaster: Float get() = if (muted) 0f else masterVolume
}

/**
 * Plays any number of sound layers at once, one [ExoPlayer] per layer, with fades on start/stop.
 * Switching from one playing scene to another crossfades them over the time the caller passes
 * (the user's scene change setting); fades follow an equal-power curve ([fadeCurve]).
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

    private class EventVoice(val eventId: String, val player: ExoPlayer, val volume: Float)

    private val voices = mutableMapOf<String, Voice>()
    private val eventVoices = mutableListOf<EventVoice>()

    /** Events shown as sounding for a moment without a sound (event sounds switched off). */
    private val silentEvents = mutableSetOf<String>()
    private var serviceRunning = false

    private val _state = MutableStateFlow(MixerState())
    val state: StateFlow<MixerState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /**
     * Makes [scene] the active scene and starts its auto-play layers. If another scene is audible,
     * the two crossfade over [crossfadeMs].
     */
    fun startScene(scene: Scene, crossfadeMs: Long = FADE_IN_MS) {
        val fadeMs = fadeFor(scene.id, crossfadeMs)
        switchTo(scene.id, fadeMs)
        scene.layers.filter { it.autoPlay }.forEach { play(it, fadeMs) }
    }

    /** Turns one layer on or off; turning on a layer of another scene crossfades over [crossfadeMs]. */
    fun toggleLayer(sceneId: String, layer: SoundLayer, crossfadeMs: Long = FADE_IN_MS) {
        if (layer.id in _state.value.playing) {
            stopLayer(layer.id)
        } else {
            val fadeMs = fadeFor(sceneId, crossfadeMs)
            switchTo(sceneId, fadeMs)
            play(layer, fadeMs)
        }
    }

    /** True if starting [sceneId] would replace another scene that is playing (a crossfade, not a start). */
    fun isSwitching(sceneId: String): Boolean = _state.value.sceneId != sceneId && _state.value.playing.isNotEmpty()

    private fun fadeFor(sceneId: String, crossfadeMs: Long) = if (isSwitching(sceneId)) crossfadeMs else FADE_IN_MS

    fun stopLayer(layerId: String) = stopLayer(layerId, FADE_OUT_MS)

    private fun stopLayer(layerId: String, fadeMs: Long) {
        val voice = voices[layerId] ?: return
        if (!voice.on) return
        voice.on = false
        fade(voice, target = 0f, durationMs = fadeMs) { release(layerId, voice) }
        publish()
    }

    fun stopAll() {
        voices.keys.toList().forEach(::stopLayer)
        eventVoices.toList().forEach(::releaseEvent)
        _state.update { it.copy(sceneId = null) }
    }

    /** Plays an event one-shot on top of whatever is playing. Tapping again overlaps another copy. */
    fun playEvent(event: SoundEvent) {
        val player = newPlayer(longBuffer = false)
        val voice = EventVoice(event.id, player, event.volume)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _errors.tryEmit("Couldn't play \"${event.name}\" (${error.errorCodeName})")
                releaseEvent(voice)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) releaseEvent(voice)
            }
        })
        player.volume = gain(event.volume) * gain(_state.value.effectiveMaster)
        player.setMediaItem(MediaItem.fromUri(event.uri))
        player.prepare()
        player.play()
        eventVoices += voice
        publishEvents()
    }

    fun setLayerVolume(layerId: String, volume: Float) {
        val voice = voices[layerId] ?: return
        voice.layerVolume = volume
        applyVolume(voice)
    }

    fun setLayerLoop(layerId: String, loop: Boolean) {
        voices[layerId]?.player?.repeatMode = repeatModeFor(loop)
    }

    /** Moving the master volume also unmutes. */
    fun setMasterVolume(volume: Float) {
        _state.update { it.copy(masterVolume = volume, muted = false) }
        applyMaster()
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
        applyMaster()
    }

    private fun applyMaster() {
        voices.values.forEach(::applyVolume)
        val master = gain(_state.value.effectiveMaster)
        eventVoices.forEach { it.player.volume = gain(it.volume) * master }
    }

    /** Shows [event] as sounding for a moment without playing it (its light flash still runs). */
    fun markEvent(event: SoundEvent) {
        silentEvents += event.id
        publishEvents()
        scope.launch {
            delay(SILENT_EVENT_MS)
            silentEvents -= event.id
            publishEvents()
        }
    }

    /** Fades out everything from the previously active scene over [fadeMs]. */
    private fun switchTo(sceneId: String, fadeMs: Long) {
        if (_state.value.sceneId == sceneId) return
        voices.keys.toList().forEach { stopLayer(it, fadeMs) }
        _state.update { it.copy(sceneId = sceneId) }
    }

    private fun play(layer: SoundLayer, fadeMs: Long) {
        val voice = voices[layer.id] ?: createVoice(layer).also { voices[layer.id] = it }
        voice.on = true
        voice.layerVolume = layer.volume
        // A one-shot that already finished is restarted from the beginning.
        if (voice.player.playbackState == Player.STATE_ENDED) voice.player.seekTo(0)
        voice.player.play()
        fade(voice, target = 1f, durationMs = fadeMs) {}
        publish()
    }

    private fun createVoice(layer: SoundLayer): Voice {
        val player = newPlayer(longBuffer = true)
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

    /**
     * With [longBuffer] (ambience layers, which play for hours) the audio output buffer holds 2 s
     * instead of the default 0.5 s, so the player wakes up to refill it a quarter as often. Volume
     * and fades are applied at the output, so they still react immediately. Event one-shots keep
     * the default buffer for the quickest start.
     */
    @OptIn(UnstableApi::class)
    private fun newPlayer(longBuffer: Boolean): ExoPlayer = ExoPlayer.Builder(context, renderers(longBuffer))
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

    // Output buffer sizing is only available through Media3's unstable API.
    @OptIn(UnstableApi::class)
    private fun renderers(longBuffer: Boolean) = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink {
            val builder = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            if (longBuffer) {
                val bufferSize = DefaultAudioTrackBufferSizeProvider.Builder().setTargetPcmBufferDurationUs(LAYER_OUTPUT_BUFFER_US).build()
                builder.setAudioOutputProvider(
                    AudioTrackAudioOutputProvider.Builder(context).setAudioTrackBufferSizeProvider(bufferSize).build(),
                )
            }
            return builder.build()
        }
    }

    private fun releaseEvent(voice: EventVoice) {
        if (eventVoices.remove(voice)) {
            voice.player.release()
            publishEvents()
        }
    }

    private fun publishEvents() {
        _state.update { it.copy(events = eventVoices.map { v -> v.eventId }.toSet() + silentEvents) }
    }

    private fun fade(voice: Voice, target: Float, durationMs: Long, onDone: () -> Unit) {
        voice.fadeJob?.cancel()
        voice.fadeJob = scope.launch {
            // A new player is still loading its file: start fading in once it can be heard, so a
            // crossfade isn't half over before the new scene is audible.
            if (target > voice.fade) {
                var waited = 0L
                while (voice.player.playbackState.let { it == Player.STATE_IDLE || it == Player.STATE_BUFFERING } && waited < MAX_LOAD_WAIT_MS) {
                    delay(FADE_STEP_MS)
                    waited += FADE_STEP_MS
                }
            }
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
        voice.player.volume = gain(voice.layerVolume) * gain(_state.value.effectiveMaster) * fadeCurve(voice.fade)
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
        private const val MAX_LOAD_WAIT_MS = 3_000L
        private const val SILENT_EVENT_MS = 700L
        private const val LAYER_OUTPUT_BUFFER_US = 2_000_000

        /** Maps a 0..1 slider position to amplitude gain; squaring makes the slider feel even to the ear. */
        fun gain(slider: Float): Float = slider.coerceIn(0f, 1f).let { it * it }

        /**
         * Amplitude for fade progress 0..1, on an equal-power curve: while one scene fades out and
         * another fades in, the total loudness stays even instead of dipping in the middle
         * (sin² + cos² = 1).
         */
        fun fadeCurve(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI / 2).toFloat()
    }
}
