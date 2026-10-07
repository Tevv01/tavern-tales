package dev.tevv.pocketbard.data

import dev.tevv.pocketbard.model.Scene
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On-disk format of `scenes.json`. Bump [version] and migrate in [SceneCodec.decode] on breaking changes. */
@Serializable
private data class SceneLibrary(val version: Int = 1, val scenes: List<Scene> = emptyList())

object SceneCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(scenes: List<Scene>): String = json.encodeToString(SceneLibrary(scenes = scenes))

    fun decode(text: String): List<Scene> = json.decodeFromString<SceneLibrary>(text).scenes
}
