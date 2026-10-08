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
 *
 * When the scene is played the lights follow [lighting] (a setup made in this app) or, failing that,
 * [lights] (a scene made in the Hue app). At most one of them is set; neither means the lights are
 * left alone.
 */
@Serializable
data class Scene(
    val id: String,
    val name: String,
    val layers: List<SoundLayer> = emptyList(),
    val background: String? = null,
    val lights: HueSceneRef? = null,
    val lighting: LightSetup? = null,
)

/**
 * Lighting made in this app, applied to the room or zone chosen in the Hue settings. The [slots]
 * are spread over that room's lights in turn (light 1 gets slot 1, ...). [brightness] is 0..1, and 0
 * turns the lights off. [motion] (0..1) is how much the lights slowly drift in brightness and colour
 * while the scene plays; 0 keeps them still.
 */
@Serializable
data class LightSetup(
    val slots: List<LightSlot> = listOf(LightSlot("#FFC27A")),
    val brightness: Float = 0.6f,
    val motion: Float = 0f,
)

/**
 * One colour in a [LightSetup], as `#RRGGBB`. [effect] is a Hue light effect such as `candle` or
 * `fire`; lights that don't support it just show the colour.
 */
@Serializable
data class LightSlot(
    val color: String,
    val effect: String? = null,
)

/**
 * A scene on the user's Hue bridge. [id] is the CLIP v2 resource id; [name] and [room] are kept so
 * the link can be shown even when the bridge can't be reached.
 */
@Serializable
data class HueSceneRef(
    val id: String,
    val name: String,
    val room: String? = null,
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
