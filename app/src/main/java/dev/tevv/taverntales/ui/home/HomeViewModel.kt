package dev.tevv.taverntales.ui.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.audio.SceneLauncher
import dev.tevv.taverntales.data.BackgroundStore
import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.data.backup.BackupException
import dev.tevv.taverntales.data.backup.BackupManager
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.findScene
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: LibraryRepository,
    private val mixer: AmbienceMixer,
    private val launcher: SceneLauncher,
    private val backgrounds: BackgroundStore,
    private val backup: BackupManager,
) : ViewModel() {
    val library = repository.library
    val mixerState = mixer.state

    /** True while a backup or restore runs. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun backUp(target: Uri) = runBusy("Backing up...") {
        val summary = backup.backUp(target)
        buildString {
            append("Backed up ${plural(summary.scenes, "scene")}")
            if (summary.sounds > 0) append(", ${plural(summary.sounds, "own sound")}")
            if (summary.pictures > 0) append(" and ${plural(summary.pictures, "picture")}")
            if (summary.missingSounds > 0) append(". ${plural(summary.missingSounds, "sound")} couldn't be read and won't be in the backup")
        }
    }

    fun restore(source: Uri, replace: Boolean) = runBusy("Restoring...") {
        val scenes = backup.restore(source, replace)
        if (replace) "Restored ${plural(scenes, "scene")}" else "Added ${plural(scenes, "scene")} from the backup"
    }

    private fun runBusy(label: String, work: suspend () -> String) {
        if (_busy.value != null) return
        _busy.value = label
        viewModelScope.launch {
            val message = try {
                work()
            } catch (e: BackupException) {
                e.message ?: "Something went wrong"
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Backup or restore failed", e)
                "Something went wrong: ${e.message}"
            }
            _busy.value = null
            _messages.tryEmit(message)
        }
    }

    private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"

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
