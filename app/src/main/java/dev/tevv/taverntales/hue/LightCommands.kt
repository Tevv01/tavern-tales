package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.LightSetup
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random

/** Turns a [LightSetup] into one CLIP v2 light update per bulb. Pure, so it can be unit-tested. */
object LightCommands {
    const val NO_EFFECT = "no_effect"

    /**
     * Commands for [lights] (all in the target room). Lights are taken in name order so the same bulb
     * gets the same colour every time; slots repeat when there are more lights than colours.
     */
    /** Recalls a Hue scene, fading the lights to it over [transitionMs]. */
    fun recall(transitionMs: Long): JsonObject = buildJsonObject {
        putJsonObject("recall") {
            put("action", "active")
            put("duration", transitionMs)
        }
    }

    fun build(setup: LightSetup, lights: List<HueLight>, transitionMs: Int = 1500): List<Pair<String, JsonObject>> {
        val ordered = ordered(lights)
        if (setup.brightness <= 0f || setup.slots.isEmpty()) {
            return ordered.map { light ->
                light.id to buildJsonObject {
                    putJsonObject("on") { put("on", false) }
                    putJsonObject("dynamics") { put("duration", transitionMs) }
                }
            }
        }
        val brightness = (setup.brightness * 100).toDouble().coerceIn(1.0, 100.0)
        return ordered.mapIndexed { index, light ->
            val slot = setup.slots[index % setup.slots.size]
            light.id to buildJsonObject {
                putJsonObject("on") { put("on", true) }
                putJsonObject("dimming") { put("brightness", brightness) }
                when {
                    light.color -> putJsonObject("color") {
                        val xy = LightMath.hexToXy(slot.color)
                        putJsonObject("xy") {
                            put("x", xy.x)
                            put("y", xy.y)
                        }
                    }
                    light.mirekRange != null -> putJsonObject("color_temperature") {
                        put("mirek", LightMath.hexToMirek(slot.color, light.mirekRange.first, light.mirekRange.last))
                    }
                }
                putJsonObject("dynamics") { put("duration", transitionMs) }
                // Always set the effect on bulbs that have effects, so a previous scene's flicker stops.
                if (light.effects.isNotEmpty()) {
                    val effect = slot.effect?.takeIf { it in light.effects } ?: NO_EFFECT
                    if (effect in light.effects) putJsonObject("effects") { put("effect", effect) }
                }
            }
        }
    }

    /** The order lights are assigned slots in; [build] and [motionStep] must agree. */
    fun ordered(lights: List<HueLight>) = lights.sortedWith(compareBy({ it.name }, { it.id }))

    /**
     * How long one light takes to drift to its next variation, for a [LightSetup.motion] of 0..1:
     * about 8 s when barely moving, 3.5 s at the liveliest.
     */
    fun motionPeriodMs(motion: Float): Long = (8000 - 4500 * motion.coerceIn(0f, 1f)).toLong()

    /**
     * One step of the slow "living light" drift for the light at [index] (in [ordered] order): its
     * brightness varies around the setup's, and its colour leans a little toward the next slot's.
     * The change fades over a full period, so consecutive steps blend into continuous movement.
     * Returns null for lights running a native effect (candle, fire...), which already move.
     */
    fun motionStep(setup: LightSetup, light: HueLight, index: Int, random: Random): JsonObject? {
        if (setup.brightness <= 0f || setup.slots.isEmpty() || setup.motion <= 0f) return null
        val slot = setup.slots[index % setup.slots.size]
        if (slot.effect != null && slot.effect in light.effects) return null
        val motion = setup.motion.coerceIn(0f, 1f).toDouble()
        val swing = 0.08 + 0.22 * motion
        val brightness = (setup.brightness * (1 + random.nextDouble(-swing, swing)) * 100).coerceIn(1.0, 100.0)
        return buildJsonObject {
            putJsonObject("dimming") { put("brightness", brightness) }
            if (light.color && setup.slots.size > 1) {
                val base = LightMath.hexToXy(slot.color)
                val toward = LightMath.hexToXy(setup.slots[(index + 1) % setup.slots.size].color)
                val t = random.nextDouble(0.0, 0.1 + 0.3 * motion)
                putJsonObject("color") {
                    putJsonObject("xy") {
                        put("x", base.x + (toward.x - base.x) * t)
                        put("y", base.y + (toward.y - base.y) * t)
                    }
                }
            }
            putJsonObject("dynamics") { put("duration", motionPeriodMs(setup.motion).toInt()) }
        }
    }
}
