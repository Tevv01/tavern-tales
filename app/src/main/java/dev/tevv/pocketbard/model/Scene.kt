package dev.tevv.pocketbard.model

import kotlinx.serialization.Serializable

/** A table situation (e.g. "Town") with the sound layers that make up its ambience. */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val layers: List<SoundLayer> = emptyList(),
)

/**
 * One sound in a scene, e.g. crowd chatter or tavern music.
 *
 * [uri] is a `content://` URI the app holds a persistable read permission for.
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
