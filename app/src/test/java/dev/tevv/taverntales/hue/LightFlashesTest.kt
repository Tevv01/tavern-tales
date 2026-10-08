package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.LightFlash
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

class LightFlashesTest {
    private val colorBulb = HueLight("a", "dev-a", "Lamp", color = true, mirekRange = 153..500, effects = setOf("no_effect", "candle"))
    private val whiteBulb = HueLight("b", "dev-b", "Spot", color = false, mirekRange = 153..454, effects = emptySet())

    @Test
    fun flashIsOneInstantFullBrightnessBurstInTheColour() {
        val steps = LightFlashes.steps(LightFlash(LightFlash.FLASH, "#FF0000"))
        val body = steps.single().body
        assertEquals(100.0, body["dimming"]!!.jsonObject["brightness"]!!.jsonPrimitive.double, 0.0)
        assertEquals(0, body["dynamics"]!!.jsonObject["duration"]!!.jsonPrimitive.int)
        assertEquals(LightMath.hexToXy("#FF0000").x, body["color"]!!.jsonObject["xy"]!!.jsonObject["x"]!!.jsonPrimitive.double, 1e-9)
    }

    @Test
    fun strobeFlashesTwiceWithDarkBetween() {
        val steps = LightFlashes.steps(LightFlash(LightFlash.STROBE, "#FFFFFF"))
        val brightness = steps.map { it.body["dimming"]!!.jsonObject["brightness"]!!.jsonPrimitive.double }
        assertEquals(listOf(100.0, 1.0, 100.0), brightness)
        assertTrue("strobe should be over within 1.5 s", steps.sumOf { it.holdMs } < 1500)
    }

    @Test
    fun groupCommandsAreSpacedSoTheBridgeKeepsUp() {
        for (style in listOf(LightFlash.FLASH, LightFlash.STROBE, LightFlash.GLOW)) {
            val steps = LightFlashes.steps(LightFlash(style, "#FFFFFF"))
            steps.dropLast(1).forEach { assertTrue("$style step held ${it.holdMs} ms", it.holdMs >= LightFlashes.MIN_GROUP_GAP_MS) }
        }
    }

    @Test
    fun glowSwellsInsteadOfJumping() {
        val step = LightFlashes.steps(LightFlash(LightFlash.GLOW, "#FFE08A")).single()
        assertTrue(step.body["dynamics"]!!.jsonObject["duration"]!!.jsonPrimitive.int > 0)
        assertTrue(LightFlashes.restoreMs(LightFlash(LightFlash.GLOW)) > LightFlashes.restoreMs(LightFlash(LightFlash.FLASH)))
    }

    @Test
    fun restorePutsBackColourTemperatureOrXyAndEffect() {
        val ct = LightFlashes.restore(LightState(on = true, brightness = 40.0, xy = Xy(0.4, 0.4), mirek = 366, effect = "candle"), colorBulb, 700)
        assertEquals(366, ct["color_temperature"]!!.jsonObject["mirek"]!!.jsonPrimitive.int)
        assertNull(ct["color"])
        assertEquals("candle", ct["effects"]!!.jsonObject["effect"]!!.jsonPrimitive.content)
        assertEquals(40.0, ct["dimming"]!!.jsonObject["brightness"]!!.jsonPrimitive.double, 0.0)

        val xy = LightFlashes.restore(LightState(on = true, brightness = 80.0, xy = Xy(0.6, 0.3), mirek = null, effect = null), colorBulb, 700)
        assertEquals(0.6, xy["color"]!!.jsonObject["xy"]!!.jsonObject["x"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("no_effect", xy["effects"]!!.jsonObject["effect"]!!.jsonPrimitive.content)
    }

    @Test
    fun restoreTurnsOffLightsThatWereOffAndSkipsUnsupportedFields() {
        val off = LightFlashes.restore(LightState(on = false, brightness = 50.0, xy = null, mirek = 300, effect = null), colorBulb, 700)
        assertEquals("false", off["on"]!!.jsonObject["on"]!!.jsonPrimitive.content)
        assertNull(off["dimming"])

        val white = LightFlashes.restore(LightState(on = true, brightness = 50.0, xy = Xy(0.5, 0.4), mirek = null, effect = null), whiteBulb, 700)
        assertNull(white["color"]) // white bulbs can't take xy
        assertFalse("effects" in white)
    }

    @Test
    fun parsesGroupedLightAndLightStates() {
        val room = HueParsing.parseGroups(
            Json.parseToJsonElement(
                """{"errors":[],"data":[{"id":"r1","metadata":{"name":"Stue"},"children":[{"rid":"dev-a","rtype":"device"}],
                "services":[{"rid":"g1","rtype":"grouped_light"}]}]}""",
            ),
            "room",
        ).single()
        assertEquals("g1", room.groupedLightId)

        val states = HueParsing.parseLightStates(
            Json.parseToJsonElement(
                """{"errors":[],"data":[
                {"id":"a","on":{"on":true},"dimming":{"brightness":42.5},"color":{"xy":{"x":0.45,"y":0.41}},
                 "color_temperature":{"mirek":null,"mirek_valid":false},"effects":{"status":"candle"}},
                {"id":"b","on":{"on":false},"dimming":{"brightness":10.0},
                 "color_temperature":{"mirek":370,"mirek_valid":true}}]}""",
            ),
        )
        assertEquals(LightState(true, 42.5, Xy(0.45, 0.41), null, "candle"), states["a"])
        assertEquals(LightState(false, 10.0, null, 370, null), states["b"])
    }
}
