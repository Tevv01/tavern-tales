package dev.tevv.taverntales.data

import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.Scene
import dev.tevv.taverntales.model.SceneCollection
import dev.tevv.taverntales.model.SoundEvent
import dev.tevv.taverntales.model.newId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes `library.json`.
 *
 * Versions: 1 = flat `scenes` list (first release); 2 = collections + events; 3 = built-in scenes
 * come with light setups; 4 = light setups can move; 5 = built-in events flash the lights; 6 = the
 * built-in scenes are grouped by theme, with five new ones. Older
 * files are migrated on read. Bump [CURRENT_VERSION] and add a migration on breaking changes; adding a field
 * with a default value needs neither.
 */
object LibraryCodec {
    const val CURRENT_VERSION = 6

    @Serializable
    private data class FileV2(
        val version: Int = CURRENT_VERSION,  // same shape for v2 to v6
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

    /** The format version of a stored file (files from the first release have none: 1). */
    fun version(text: String): Int = json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.int ?: 1

    fun decode(text: String): Library {
        val root = json.parseToJsonElement(text).jsonObject
        return when (val version = root["version"]?.jsonPrimitive?.int ?: 1) {
            1 -> migrateV1(json.decodeFromJsonElement<FileV1>(root))
            2 -> regroupBuiltIns(addDefaultFlashes(addDefaultMotion(addDefaultLighting(decodeV2(root)))))
            3 -> regroupBuiltIns(addDefaultFlashes(addDefaultMotion(decodeV2(root))))
            4 -> regroupBuiltIns(addDefaultFlashes(decodeV2(root)))
            5 -> regroupBuiltIns(decodeV2(root))
            CURRENT_VERSION -> json.decodeFromJsonElement<FileV2>(root).let { Library(it.collections, it.events) }
            else -> error("Unsupported library version $version")
        }
    }

    /** v3 gave the built-in scenes light setups; add them where the user hasn't set up lights already. */
    private fun addDefaultLighting(library: Library): Library = library.copy(
        collections = library.collections.map { collection ->
            collection.copy(scenes = collection.scenes.map { scene ->
                val default = DefaultLibrary.lighting[scene.id.removePrefix("default-")]
                if (scene.id.startsWith("default-") && default != null && scene.lighting == null && scene.lights == null) {
                    scene.copy(lighting = default)
                } else {
                    scene
                }
            })
        },
    )

    private fun decodeV2(root: JsonObject) =
        json.decodeFromJsonElement<FileV2>(root).let { Library(it.collections, it.events) }

    /**
     * v6 replaced the single "Essentials" collection with themed ones and added new scenes. Built-in
     * scenes still in Essentials move to their themed collection, keeping the user's changes; ones
     * the user moved elsewhere or deleted stay that way. Essentials stays only if the user's own
     * scenes are left in it. The themed collections take its place in the list.
     */
    private fun regroupBuiltIns(library: Library): Library {
        val essentials = library.collections.find { it.id == DefaultLibrary.LEGACY_COLLECTION_ID }
        val kept = essentials?.scenes.orEmpty().filter { it.id in DefaultLibrary.LEGACY_SCENE_IDS }.associateBy { it.id }
        val themed = DefaultLibrary.collections()
            .filter { c -> library.collections.none { it.id == c.id } }
            .map { c ->
                c.copy(
                    collapsed = essentials?.collapsed ?: false,
                    // An original scene comes along only from Essentials; a new one is added as shipped.
                    scenes = c.scenes.mapNotNull { scene ->
                        kept[scene.id] ?: scene.takeIf { it.id !in DefaultLibrary.LEGACY_SCENE_IDS }
                    },
                )
            }
            .filter { it.scenes.isNotEmpty() }
        val leftover = essentials?.copy(scenes = essentials.scenes.filter { it.id !in kept })
        if (essentials == null) return library.copy(collections = library.collections + themed)
        return library.copy(
            collections = library.collections.flatMap { collection ->
                if (collection.id != essentials.id) {
                    listOf(collection)
                } else {
                    themed + listOfNotNull(leftover?.takeIf { it.scenes.isNotEmpty() })
                }
            },
        )
    }

    /** v5 gave the built-in events light flashes. */
    private fun addDefaultFlashes(library: Library): Library = library.copy(
        events = library.events.map { event ->
            val default = DefaultLibrary.flashes[event.id]
            if (event.flash == null && default != null) event.copy(flash = default) else event
        },
    )

    /** v4 added movement; built-in scenes keep the user's colours and brightness but get the default movement. */
    private fun addDefaultMotion(library: Library): Library = library.copy(
        collections = library.collections.map { collection ->
            collection.copy(scenes = collection.scenes.map { scene ->
                val lighting = scene.lighting
                val default = DefaultLibrary.lighting[scene.id.removePrefix("default-")]
                if (scene.id.startsWith("default-") && default != null && lighting != null && lighting.motion == 0f) {
                    scene.copy(lighting = lighting.copy(motion = default.motion))
                } else {
                    scene
                }
            })
        },
    )

    /** v1 had no collections or events: keep the user's scenes in their own collection next to the defaults. */
    private fun migrateV1(file: FileV1): Library {
        val defaults = DefaultLibrary.create()
        if (file.scenes.isEmpty()) return defaults
        return defaults.copy(collections = defaults.collections + SceneCollection(newId(), "My scenes", file.scenes))
    }
}
