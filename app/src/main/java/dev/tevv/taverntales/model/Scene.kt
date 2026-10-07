package dev.tevv.taverntales.model

import kotlinx.serialization.Serializable

/** Everything the user has set up: scenes grouped into collections, plus the global event pads. */
@Serializable
data class Library(
    val collections: List<SceneCollection> = emptyList(),
    val events: List<SoundEvent> = emptyList(),
)

@Serializable
data class SceneCollection(
    val id: String,
    val name: String,
    val scenes: List<Scene> = emptyList(),
)

/**
 * A table situation (e.g. "Town") with the sound layers that make up its ambience.
 *
 * [background] is either `builtin:<key>` (an image shipped with the app) or a `file://` URI of an
 * image copied into app storage; null means no image.
 */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val layers: List<SoundLayer> = emptyList(),
    val background: String? = null,
)

/**
 * One sound in a scene, e.g. crowd chatter or tavern music.
 *
 * [uri] is a `content://` URI the app holds a persistable read permission for, or an
 * `asset:///sounds/...` URI for built-in sounds.
 * [volume] is the slider position in 0..1; the mixer maps it to a gain curve.
 * [autoPlay] layers start when the whole scene is played; others are toggled by hand.
 * [loop] is false for one-shot effects (a door slam, a dragon roar).
 */
@Serializable
data class SoundLayer(
    val id: String,
    val name: String,
    val uri: String,
    val volume: Float = 0.7f,
    val autoPlay: Boolean = true,
    val loop: Boolean = true,
)

/**
 * A one-shot sound effect on the Events tab (fireball, explosion...). Events are global: the same
 * pads are available in every scene. [icon] and [color] are keys into the UI's icon/colour sets.
 */
@Serializable
data class SoundEvent(
    val id: String,
    val name: String,
    val uri: String,
    val volume: Float = 0.8f,
    val icon: String = "magic",
    val color: String = "violet",
)
