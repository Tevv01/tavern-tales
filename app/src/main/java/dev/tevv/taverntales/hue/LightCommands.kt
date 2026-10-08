package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.LightSetup
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Turns a [LightSetup] into one CLIP v2 light update per bulb. Pure, so it can be unit-tested. */
object LightCommands {
    const val NO_EFFECT = "no_effect"

    /**
     * Commands for [lights] (all in the target room). Lights are taken in name order so the same bulb
     * gets the same colour every time; slots repeat when there are more lights than colours.
     */
    fun build(setup: LightSetup, lights: List<HueLight>, transitionMs: Int = 1500): List<Pair<String, JsonObject>> {
        val ordered = lights.sortedWith(compareBy({ it.name }, { it.id }))
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
}
