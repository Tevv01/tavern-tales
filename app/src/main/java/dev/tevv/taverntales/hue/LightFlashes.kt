package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.LightFlash
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A light's state before an event flash, so it can be put back exactly. */
data class LightState(val on: Boolean, val brightness: Double?, val xy: Xy?, val mirek: Int?, val effect: String?)

/**
 * The commands behind an event's light flash. Pure, so it can be unit-tested. Flash steps go to the
 * room's grouped light (one command, so every bulb changes at the same moment); restoring goes
 * per light. Bridges only keep up with about one grouped-light command per second and drop or bunch
 * a faster burst, so steps are at least [MIN_GROUP_GAP_MS] apart.
 */
object LightFlashes {
    /** One room-wide command, then how long to wait before the next. */
    data class Step(val body: JsonObject, val holdMs: Long)

    fun steps(flash: LightFlash): List<Step> {
        val bright = { transitionMs: Int -> lit(flash.color, transitionMs) }
        return when (flash.style) {
            // Lightning: a flash, a moment of dark, a second flash, then the lights fade back.
            LightFlash.STROBE -> listOf(
                Step(bright(0), MIN_GROUP_GAP_MS),
                Step(dark(), MIN_GROUP_GAP_MS),
                Step(bright(0), 450),
            )
            LightFlash.GLOW -> listOf(Step(bright(GLOW_RISE_MS), GLOW_RISE_MS + 900L))
            else -> listOf(Step(bright(0), 450))
        }
    }

    /** How long the lights take to fade back afterwards. */
    fun restoreMs(flash: LightFlash): Int = when (flash.style) {
        LightFlash.GLOW -> 1500
        LightFlash.STROBE -> 600
        else -> 700
    }

    /** Puts one light back the way it was before the flash. */
    fun restore(state: LightState, light: HueLight, transitionMs: Int): JsonObject = buildJsonObject {
        putJsonObject("on") { put("on", state.on) }
        if (state.on) {
            state.brightness?.let { putJsonObject("dimming") { put("brightness", it) } }
            when {
                state.mirek != null && light.mirekRange != null ->
                    putJsonObject("color_temperature") { put("mirek", state.mirek) }
                state.xy != null && light.color -> putJsonObject("color") {
                    putJsonObject("xy") {
                        put("x", state.xy.x)
                        put("y", state.xy.y)
                    }
                }
            }
            if (light.effects.isNotEmpty()) {
                val effect = state.effect?.takeIf { it in light.effects } ?: LightCommands.NO_EFFECT
                if (effect in light.effects) putJsonObject("effects") { put("effect", effect) }
            }
        }
        putJsonObject("dynamics") { put("duration", transitionMs) }
    }

    private fun lit(color: String, transitionMs: Int) = buildJsonObject {
        val xy = LightMath.hexToXy(color)
        putJsonObject("on") { put("on", true) }
        putJsonObject("dimming") { put("brightness", 100.0) }
        putJsonObject("color") {
            putJsonObject("xy") {
                put("x", xy.x)
                put("y", xy.y)
            }
        }
        putJsonObject("dynamics") { put("duration", transitionMs) }
    }

    private fun dark() = buildJsonObject {
        putJsonObject("dimming") { put("brightness", 1.0) }
        putJsonObject("dynamics") { put("duration", 0) }
    }

    private const val GLOW_RISE_MS = 500

    /** The shortest time between two grouped-light commands that bridges reliably carry out. */
    const val MIN_GROUP_GAP_MS = 400L
}
