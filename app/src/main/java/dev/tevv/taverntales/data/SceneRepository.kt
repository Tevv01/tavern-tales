package dev.tevv.taverntales.data

import android.util.AtomicFile
import android.util.Log
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.model.newId
import dev.tevv.taverntales.model.removeLayer
import dev.tevv.taverntales.model.updateLayer
import dev.tevv.taverntales.model.updateScene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds all scenes in memory and persists them to a JSON file.
 *
 * Edits are applied to [scenes] immediately and written to disk after a short debounce,
 * so volume sliders can call [updateLayer] on every drag event.
 */
@OptIn(FlowPreview::class)
class SceneRepository(private val file: File, scope: CoroutineScope) {
    private val atomicFile = AtomicFile(file)
    private val _scenes = MutableStateFlow(load())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    init {
        scope.launch {
            _scenes.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    fun createScene(name: String): String {
        val id = newId()
        _scenes.update { it + Scene(id = id, name = name) }
        return id
    }

    fun renameScene(sceneId: String, name: String) =
        _scenes.update { list -> list.updateScene(sceneId) { it.copy(name = name) } }

    fun deleteScene(sceneId: String) = _scenes.update { list -> list.filterNot { it.id == sceneId } }

    fun addLayers(sceneId: String, layers: List<SoundLayer>) =
        _scenes.update { list -> list.updateScene(sceneId) { it.copy(layers = it.layers + layers) } }

    fun updateLayer(sceneId: String, layerId: String, transform: (SoundLayer) -> SoundLayer) =
        _scenes.update { list -> list.updateScene(sceneId) { it.updateLayer(layerId, transform) } }

    fun removeLayer(sceneId: String, layerId: String) =
        _scenes.update { list -> list.updateScene(sceneId) { it.removeLayer(layerId) } }

    private fun load(): List<Scene> {
        if (!file.exists()) return listOf(Scene(id = newId(), name = "Town"))
        return try {
            SceneCodec.decode(atomicFile.readFully().decodeToString())
        } catch (e: Exception) {
            // Keep the unreadable file for recovery instead of overwriting it on the next save.
            Log.e(TAG, "Could not read ${file.name}; moving it aside", e)
            file.renameTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            emptyList()
        }
    }

    private suspend fun save(scenes: List<Scene>) = withContext(Dispatchers.IO) {
        val out = atomicFile.startWrite()
        try {
            out.write(SceneCodec.encode(scenes).encodeToByteArray())
            atomicFile.finishWrite(out)
        } catch (e: Exception) {
            atomicFile.failWrite(out)
            Log.e(TAG, "Could not save scenes", e)
        }
    }

    private companion object {
        const val TAG = "SceneRepository"
        const val SAVE_DEBOUNCE_MS = 400L
    }
}
