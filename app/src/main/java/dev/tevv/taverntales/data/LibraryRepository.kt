package dev.tevv.taverntales.data

import android.util.AtomicFile
import android.util.Log
import dev.tevv.taverntales.data.backup.BackupFormat
import dev.tevv.taverntales.model.HueSceneRef
import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.LightSetup
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.SoundLayer
import dev.tevv.taverntales.model.addScene
import dev.tevv.taverntales.model.moveScene
import dev.tevv.taverntales.model.newId
import dev.tevv.taverntales.model.removeLayer
import dev.tevv.taverntales.model.removeScene
import dev.tevv.taverntales.model.updateCollection
import dev.tevv.taverntales.model.updateEvent
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
 * Holds the whole [Library] in memory and persists it to `library.json`.
 *
 * Edits are applied to [library] immediately and written to disk after a short debounce,
 * so volume sliders can call [updateLayer] on every drag event.
 */
@OptIn(FlowPreview::class)
class LibraryRepository(private val dir: File, scope: CoroutineScope) {
    private val file = File(dir, "library.json")
    private val atomicFile = AtomicFile(file)
    /** Set while loading when the stored file is missing or in an older format, so it is rewritten. */
    private var needsSave = false
    private val _library = MutableStateFlow(load())
    val library: StateFlow<Library> = _library.asStateFlow()

    init {
        scope.launch {
            // First run or migration: write the file now, so ids created while loading stay stable and
            // migrations don't run again on every launch.
            if (needsSave) save(_library.value)
            _library.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { save(it) }
        }
    }

    // Whole library (backup restore)

    fun replace(library: Library) = _library.update { library }

    fun merge(library: Library) = _library.update { BackupFormat.merge(it, library) }

    // Collections

    fun createCollection(name: String): String {
        val id = newId()
        _library.update { it.copy(collections = it.collections + SceneCollection(id, name)) }
        return id
    }

    fun renameCollection(collectionId: String, name: String) =
        _library.update { lib -> lib.updateCollection(collectionId) { it.copy(name = name) } }

    fun deleteCollection(collectionId: String) =
        _library.update { lib -> lib.copy(collections = lib.collections.filterNot { it.id == collectionId }) }

    // Scenes

    fun createScene(collectionId: String, name: String): String {
        val id = newId()
        _library.update { it.addScene(collectionId, Scene(id = id, name = name)) }
        return id
    }

    fun renameScene(sceneId: String, name: String) =
        _library.update { lib -> lib.updateScene(sceneId) { it.copy(name = name) } }

    fun setBackground(sceneId: String, background: String?) =
        _library.update { lib -> lib.updateScene(sceneId) { it.copy(background = background) } }

    /** Links a Hue app scene; replaces any light setup made in the app. */
    fun setLights(sceneId: String, lights: HueSceneRef?) =
        _library.update { lib -> lib.updateScene(sceneId) { it.copy(lights = lights, lighting = null) } }

    /** Sets a light setup made in the app; replaces any linked Hue app scene. */
    fun setLighting(sceneId: String, lighting: LightSetup?) =
        _library.update { lib -> lib.updateScene(sceneId) { it.copy(lighting = lighting, lights = null) } }

    fun moveScene(sceneId: String, toCollectionId: String) = _library.update { it.moveScene(sceneId, toCollectionId) }

    fun deleteScene(sceneId: String) = _library.update { it.removeScene(sceneId) }

    // Layers

    fun addLayers(sceneId: String, layers: List<SoundLayer>) =
        _library.update { lib -> lib.updateScene(sceneId) { it.copy(layers = it.layers + layers) } }

    fun updateLayer(sceneId: String, layerId: String, transform: (SoundLayer) -> SoundLayer) =
        _library.update { lib -> lib.updateScene(sceneId) { it.updateLayer(layerId, transform) } }

    fun removeLayer(sceneId: String, layerId: String) =
        _library.update { lib -> lib.updateScene(sceneId) { it.removeLayer(layerId) } }

    // Events

    fun addEvent(event: SoundEvent) = _library.update { it.copy(events = it.events + event) }

    fun updateEvent(eventId: String, transform: (SoundEvent) -> SoundEvent) =
        _library.update { it.updateEvent(eventId, transform) }

    fun removeEvent(eventId: String) = _library.update { lib -> lib.copy(events = lib.events.filterNot { it.id == eventId }) }

    private fun load(): Library {
        // The first release stored a flat scene list in scenes.json; it is migrated on read.
        val source = listOf(file, File(dir, LEGACY_FILE)).firstOrNull { it.exists() }
        if (source == null) {
            needsSave = true
            return DefaultLibrary.create()
        }
        return try {
            val text = AtomicFile(source).readFully().decodeToString()
            needsSave = source != file || LibraryCodec.version(text) < LibraryCodec.CURRENT_VERSION
            LibraryCodec.decode(text)
        } catch (e: Exception) {
            // Keep the unreadable file for recovery instead of overwriting it on the next save.
            Log.e(TAG, "Could not read ${source.name}; moving it aside", e)
            source.renameTo(File(dir, "${source.name}.corrupt-${System.currentTimeMillis()}"))
            DefaultLibrary.create()
        }
    }

    private suspend fun save(library: Library) = withContext(Dispatchers.IO) {
        val out = atomicFile.startWrite()
        try {
            out.write(LibraryCodec.encode(library).encodeToByteArray())
            atomicFile.finishWrite(out)
            File(dir, LEGACY_FILE).takeIf { it.exists() }?.renameTo(File(dir, "$LEGACY_FILE.migrated"))
        } catch (e: Exception) {
            atomicFile.failWrite(out)
            Log.e(TAG, "Could not save library", e)
        }
    }

    private companion object {
        const val TAG = "LibraryRepository"
        const val LEGACY_FILE = "scenes.json"
        const val SAVE_DEBOUNCE_MS = 400L
    }
}
