package dev.tevv.taverntales.data.backup

import dev.tevv.taverntales.model.Library
import dev.tevv.taverntales.model.newId
import kotlinx.serialization.Serializable

class BackupException(message: String) : Exception(message)

/** What a backup contained, for telling the user. */
data class BackupSummary(val scenes: Int, val pictures: Int, val sounds: Int, val missingSounds: Int)

/**
 * A backup is a zip file:
 * - `manifest.json`: [BackupManifest]
 * - `library.json`: the library in [dev.tevv.taverntales.data.LibraryCodec] format, with references to
 *   files inside the backup written as `backup:<entry name>`
 * - `backgrounds/<name>`: scene pictures copied into the app
 * - `audio/<name>`: copies of audio the user imported (built-in `asset:` sounds are not copied)
 *
 * The Hue pairing is deliberately not included: it belongs to one bridge and network.
 */
object BackupFormat {
    const val FORMAT = "tavern-tales-backup"
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val LIBRARY = "library.json"
    const val BACKGROUNDS = "backgrounds/"
    const val AUDIO = "audio/"
    const val REF_PREFIX = "backup:"

    private val FILE_NAME = Regex("[A-Za-z0-9._-]{1,100}")

    /** Only these entries are read from a backup; anything else (e.g. `../` paths) is ignored. */
    fun isAllowedEntry(name: String): Boolean = when {
        name == MANIFEST || name == LIBRARY -> true
        name.startsWith(BACKGROUNDS) -> isFileName(name.removePrefix(BACKGROUNDS))
        name.startsWith(AUDIO) -> isFileName(name.removePrefix(AUDIO))
        else -> false
    }

    private fun isFileName(name: String) = FILE_NAME.matches(name) && name != "." && name != ".."

    /**
     * Adds [incoming]'s collections and events to [current] ("add to my library"). Everything gets
     * fresh ids so nothing collides with what's already there; a collection whose name is taken gets
     * " (imported)" appended. Events already present (same name and sound) are skipped.
     */
    fun merge(current: Library, incoming: Library): Library {
        val takenNames = current.collections.map { it.name }.toMutableSet()
        val added = incoming.collections.map { collection ->
            val name = if (collection.name in takenNames) "${collection.name} (imported)" else collection.name
            takenNames += name
            collection.copy(
                id = newId(),
                name = name,
                scenes = collection.scenes.map { scene ->
                    scene.copy(id = newId(), layers = scene.layers.map { it.copy(id = newId()) })
                },
            )
        }
        val existingEvents = current.events.map { it.name to it.uri }.toSet()
        val newEvents = incoming.events.filter { (it.name to it.uri) !in existingEvents }.map { it.copy(id = newId()) }
        return Library(collections = current.collections + added, events = current.events + newEvents)
    }

    /** Rewrites every file reference (layer and event sounds, scene pictures) in [library]. */
    fun mapReferences(library: Library, sound: (String) -> String, picture: (String) -> String): Library = library.copy(
        collections = library.collections.map { collection ->
            collection.copy(scenes = collection.scenes.map { scene ->
                scene.copy(
                    background = scene.background?.let(picture),
                    layers = scene.layers.map { it.copy(uri = sound(it.uri)) },
                )
            })
        },
        events = library.events.map { it.copy(uri = sound(it.uri)) },
    )

    /** Every sound and picture reference in [library]. */
    fun references(library: Library): Set<String> {
        val scenes = library.collections.flatMap { it.scenes }
        return (scenes.flatMap { s -> s.layers.map { it.uri } } + library.events.map { it.uri } +
            scenes.mapNotNull { it.background }).toSet()
    }
}

@Serializable
data class BackupManifest(
    val format: String = BackupFormat.FORMAT,
    val version: Int = BackupFormat.VERSION,
    val created: String = "",
    val appVersion: String = "",
)
