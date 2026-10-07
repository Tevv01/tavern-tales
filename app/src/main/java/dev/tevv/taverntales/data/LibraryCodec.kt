package dev.tevv.taverntales.data

import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.newId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes `library.json`.
 *
 * Versions: 1 = flat `scenes` list (first release); 2 = collections + events. Older files are
 * migrated on read. Bump [CURRENT_VERSION] and add a migration on breaking changes; adding a field
 * with a default value needs neither.
 */
object LibraryCodec {
    const val CURRENT_VERSION = 2

    @Serializable
    private data class FileV2(
        val version: Int = CURRENT_VERSION,
        val collections: List<SceneCollection> = emptyList(),
        val events: List<SoundEvent> = emptyList(),
    )

    @Serializable
    private data class FileV1(val scenes: List<Scene> = emptyList())

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(library: Library): String =
        json.encodeToString(FileV2(collections = library.collections, events = library.events))

    fun decode(text: String): Library {
        val root = json.parseToJsonElement(text).jsonObject
        return when (val version = root["version"]?.jsonPrimitive?.int ?: 1) {
            1 -> migrateV1(json.decodeFromJsonElement<FileV1>(root))
            CURRENT_VERSION -> json.decodeFromJsonElement<FileV2>(root).let { Library(it.collections, it.events) }
            else -> error("Unsupported library version $version")
        }
    }

    /** v1 had no collections or events: keep the user's scenes in their own collection next to the defaults. */
    private fun migrateV1(file: FileV1): Library {
        val defaults = DefaultLibrary.create()
        if (file.scenes.isEmpty()) return defaults
        return defaults.copy(collections = defaults.collections + SceneCollection(newId(), "My scenes", file.scenes))
    }
}
