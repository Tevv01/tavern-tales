package dev.tevv.taverntales.data

import dev.tevv.taverntales.hue.LightCommands
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneChangeTest {
    @Test
    fun descriptionsReadNaturally() {
        assertEquals("Quick: 1.5 seconds", SceneChange.Quick.description)
        assertEquals("Smooth: 4 seconds", SceneChange.Smooth.description)
        assertEquals("Slow: 8 seconds", SceneChange.Slow.description)
    }

    @Test
    fun unknownOrMissingStoredValueFallsBackToTheDefault() {
        assertEquals(SceneChange.Slow, SceneChange.fromName("Slow"))
        assertEquals(SceneChange.DEFAULT, SceneChange.fromName(null))
        assertEquals(SceneChange.DEFAULT, SceneChange.fromName("Glacial"))
    }

    @Test
    fun hueSceneRecallFadesOverTheSceneChangeTime() {
        val recall = LightCommands.recall(SceneChange.Slow.durationMs)["recall"]!!.jsonObject
        assertEquals("active", recall["action"]!!.jsonPrimitive.content)
        assertEquals(8000, recall["duration"]!!.jsonPrimitive.int)
    }
}
