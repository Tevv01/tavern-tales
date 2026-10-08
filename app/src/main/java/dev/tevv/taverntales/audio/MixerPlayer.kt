package dev.tevv.taverntales.audio

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import kotlinx.coroutines.flow.StateFlow

/**
 * The scenes the media controls step through: the collection holding [sceneId], and where that
 * scene is in it. Empty (and -1) if the scene isn't in the library.
 */
internal fun mediaPlaylist(library: Library, sceneId: String?): Pair<SceneCollection?, Int> {
    if (sceneId == null) return null to -1
    for (collection in library.collections) {
        val index = collection.scenes.indexOfFirst { it.id == sceneId }
        if (index >= 0) return collection to index
    }
    return null to -1
}

/**
 * Presents the mixer to Android as a media player, so the system media controls (notification,
 * lock screen, quick settings player, headset and Bluetooth buttons) work. The "playlist" is the
 * playing scene's collection: next and previous switch scenes, with the usual crossfade, wrapping
 * around at the ends. Pause and stop fade everything out; play starts the last scene again.
 *
 * Main thread only. Call [refresh] whenever the mixer, the library or the artwork changes.
 */
@OptIn(UnstableApi::class)
class MixerPlayer(
    private val mixer: AmbienceMixer,
    private val launcher: SceneLauncher,
    private val library: StateFlow<Library>,
    private val artwork: SceneArtwork,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** The scene the controls are about: the one playing, or the last one that played. */
    private var sceneId: String? = mixer.state.value.sceneId

    fun refresh() {
        mixer.state.value.sceneId?.let { sceneId = it }
        invalidateState()
    }

    override fun getState(): State {
        val (collection, index) = mediaPlaylist(library.value, sceneId)
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(mixer.state.value.playing.isNotEmpty(), Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
        if (collection == null) return builder.setPlaybackState(Player.STATE_IDLE).build()
        return builder
            .setPlaybackState(Player.STATE_READY)
            .setPlaylist(collection.scenes.map { item(it, collection.name) })
            .setCurrentMediaItemIndex(index)
            // A scene has no position. Kept at zero (not extrapolated while playing), so "previous"
            // always goes to the previous scene instead of restarting this one after 3 seconds.
            .setContentPositionMs(PositionSupplier.ZERO)
            .build()
    }

    private fun item(scene: Scene, collectionName: String): MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(scene.name)
            .setDisplayTitle(scene.name)
            .setArtist(collectionName)
            .apply { artwork.get(scene)?.let { setArtworkData(it.jpeg, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .build()
        return MediaItemData.Builder(scene.id)
            .setMediaItem(MediaItem.Builder().setMediaId(scene.id).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) currentScene()?.let(launcher::start) else mixer.stopAll()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        mixer.stopAll()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        mediaPlaylist(library.value, sceneId).first?.scenes?.getOrNull(mediaItemIndex)?.let { scene ->
            sceneId = scene.id
            launcher.start(scene)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun currentScene(): Scene? {
        val (collection, index) = mediaPlaylist(library.value, sceneId)
        return collection?.scenes?.getOrNull(index)
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}
