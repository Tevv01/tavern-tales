package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.LightSetup
import dev.tevv.taverntales.model.LightSlot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightCommandsTest {
    private val colorBulb = HueLight("a", "dev-a", "A lamp", color = true, mirekRange = 153..500, effects = setOf("no_effect", "candle", "fire"))
    private val oldColorBulb = HueLight("b", "dev-b", "B lamp", color = true, mirekRange = 153..500, effects = emptySet())
    private val whiteAmbiance = HueLight("c", "dev-c", "C lamp", color = false, mirekRange = 153..454, effects = emptySet())
    private val dimmableOnly = HueLight("d", "dev-d", "D lamp", color = false, mirekRange = null, effects = emptySet())

    @Test
    fun spreadsSlotsOverLightsInNameOrder() {
        val setup = LightSetup(listOf(LightSlot("#FF0000"), LightSlot("#0000FF")), brightness = 0.5f)
        val commands = LightCommands.build(setup, listOf(oldColorBulb, colorBulb)).toMap()
        val red = LightMath.hexToXy("#FF0000")
        val blue = LightMath.hexToXy("#0000FF")
        assertEquals(red.x, commands.getValue("a").xy().first, 1e-9)   // "A lamp" sorts first -> slot 1
        assertEquals(blue.x, commands.getValue("b").xy().first, 1e-9)
        assertEquals(50.0, commands.getValue("a")["dimming"]!!.jsonObject["brightness"]!!.jsonPrimitive.double, 1e-9)
    }

    @Test
    fun usesColorTemperatureForWhiteBulbsAndOnlyBrightnessForDimmable() {
        val commands = LightCommands.build(LightSetup(listOf(LightSlot("#FF8A2B")), 0.4f), listOf(whiteAmbiance, dimmableOnly)).toMap()
        val c = commands.getValue("c")
        assertNull(c["color"])
        assertEquals(454, c["color_temperature"]!!.jsonObject["mirek"]!!.jsonPrimitive.int) // warm amber -> warmest the bulb allows
        val d = commands.getValue("d")
        assertNull(d["color"])
        assertNull(d["color_temperature"])
        assertTrue("dimming" in d)
    }

    @Test
    fun setsSupportedEffectAndClearsOtherwise() {
        val flicker = LightCommands.build(LightSetup(listOf(LightSlot("#FF8A2B", "candle")), 0.5f), listOf(colorBulb, oldColorBulb)).toMap()
        assertEquals("candle", flicker.getValue("a").effect())
        assertFalse("effects" in flicker.getValue("b")) // bulb without effects: no effects field at all

        val unsupported = LightCommands.build(LightSetup(listOf(LightSlot("#FF8A2B", "prism")), 0.5f), listOf(colorBulb)).toMap()
        assertEquals("no_effect", unsupported.getValue("a").effect())
        val plain = LightCommands.build(LightSetup(listOf(LightSlot("#FF8A2B")), 0.5f), listOf(colorBulb)).toMap()
        assertEquals("no_effect", plain.getValue("a").effect()) // stops a previous scene's flicker
    }

    @Test
    fun zeroBrightnessTurnsLightsOff() {
        val commands = LightCommands.build(LightSetup(listOf(LightSlot("#FFFFFF")), 0f), listOf(colorBulb))
        val body = commands.single().second
        assertEquals("false", body["on"]!!.jsonObject["on"]!!.jsonPrimitive.content)
        assertNull(body["dimming"])
    }

    @Test
    fun motionStepStaysNearTheSetupAndSkipsEffectLights() {
        val setup = LightSetup(listOf(LightSlot("#FF0000", "candle"), LightSlot("#0000FF")), brightness = 0.5f, motion = 1f)
        val random = kotlin.random.Random(42)
        // Light 0 runs the candle effect: left alone.
        assertNull(LightCommands.motionStep(setup, colorBulb, 0, random))
        val red = LightMath.hexToXy("#FF0000")
        val blue = LightMath.hexToXy("#0000FF")
        repeat(200) {
            val body = LightCommands.motionStep(setup, oldColorBulb, 1, random)!!
            val brightness = body["dimming"]!!.jsonObject["brightness"]!!.jsonPrimitive.double
            assertTrue(brightness in 35.0..65.0) // +-30 % around 50 at full motion
            val (x, _) = body.xy()
            // Slot 2 (blue) leans at most 40 % of the way toward the next slot (red).
            assertTrue(x >= blue.x - 1e-9 && x <= blue.x + (red.x - blue.x) * 0.4 + 1e-9)
            assertEquals(LightCommands.motionPeriodMs(1f).toInt(), body["dynamics"]!!.jsonObject["duration"]!!.jsonPrimitive.int)
        }
    }

    @Test
    fun noMotionWhenStillOrOff() {
        assertNull(LightCommands.motionStep(LightSetup(listOf(LightSlot("#FF0000")), 0.5f, motion = 0f), oldColorBulb, 0, kotlin.random.Random(1)))
        assertNull(LightCommands.motionStep(LightSetup(listOf(LightSlot("#FF0000")), 0f, motion = 1f), oldColorBulb, 0, kotlin.random.Random(1)))
        // White-only bulbs only drift in brightness.
        val white = LightCommands.motionStep(LightSetup(listOf(LightSlot("#FF0000"), LightSlot("#00FF00")), 0.5f, 0.5f), whiteAmbiance, 0, kotlin.random.Random(1))!!
        assertNull(white["color"])
    }

    @Test
    fun bodiesAreValidJson() {
        val body = LightCommands.build(LightSetup(), listOf(colorBulb)).single().second
        assertEquals(body, Json.parseToJsonElement(body.toString()))
    }

    @Test
    fun hexToXyMatchesKnownPrimariesAndHandlesBadInput() {
        val red = LightMath.hexToXy("#FF0000")
        assertEquals(0.7006, red.x, 1e-3)
        assertEquals(0.2993, red.y, 1e-3)
        val white = LightMath.hexToXy("#FFFFFF")
        assertEquals(0.3227, white.x, 2e-3)
        assertEquals(LightMath.hexToXy("#FFFFFF"), LightMath.hexToXy("not a colour"))
        assertEquals(LightMath.hexToXy("not a colour"), LightMath.hexToXy("#000000")) // black has no chromaticity: white
    }

    @Test
    fun hexToMirekIsWarmForAmberAndCoolForBlueish() {
        assertTrue(LightMath.hexToMirek("#FFB054") > 300)
        assertTrue(LightMath.hexToMirek("#C8DCFF") < 200)
    }

    private fun kotlinx.serialization.json.JsonObject.xy(): Pair<Double, Double> {
        val xy = this["color"]!!.jsonObject["xy"]!!.jsonObject
        return xy["x"]!!.jsonPrimitive.double to xy["y"]!!.jsonPrimitive.double
    }

    private fun kotlinx.serialization.json.JsonObject.effect() = this["effects"]!!.jsonObject["effect"]!!.jsonPrimitive.content
}
