package dev.tevv.taverntales.hue

import dev.tevv.taverntales.model.HueSceneRef
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HueParsingTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun parsePair_success() {
        val result = HueParsing.parsePair(json("""[{"success":{"username":"abc123","clientkey":"KEY"}}]"""))
        assertEquals(PairResult.Success("abc123"), result)
    }

    @Test
    fun parsePair_linkButtonNotPressed() {
        val result = HueParsing.parsePair(json("""[{"error":{"type":101,"address":"","description":"link button not pressed"}}]"""))
        assertEquals(PairResult.LinkButtonNotPressed, result)
    }

    @Test
    fun parsePair_otherError() {
        val result = HueParsing.parsePair(json("""[{"error":{"type":7,"address":"/devicetype","description":"invalid value"}}]"""))
        assertEquals(PairResult.Failed("invalid value"), result)
    }

    @Test
    fun parseConfig_readsIdAndName() {
        val info = HueParsing.parseConfig(json("""{"name":"Hue Bridge","bridgeid":"001788FFFE123456","swversion":"1967054020"}"""))
        assertEquals(BridgeInfo("001788FFFE123456", "Hue Bridge"), info)
    }

    @Test
    fun parseConfig_rejectsOtherDevices() {
        assertThrows(HueException::class.java) { HueParsing.parseConfig(json("""{"name":"router"}""")) }
    }

    @Test
    fun parseScenes_labelsWithRoomAndSortsByRoomThenName() {
        val rooms = HueParsing.parseGroupNames(json("""
            {"errors":[],"data":[
              {"id":"room-1","type":"room","metadata":{"name":"Living room","archetype":"living_room"}},
              {"id":"room-2","type":"room","metadata":{"name":"Bedroom","archetype":"bedroom"}}]}
        """))
        val scenes = HueParsing.parseScenes(json("""
            {"errors":[],"data":[
              {"id":"s1","type":"scene","metadata":{"name":"Relax"},"group":{"rid":"room-1","rtype":"room"}},
              {"id":"s2","type":"scene","metadata":{"name":"Concentrate"},"group":{"rid":"room-1","rtype":"room"}},
              {"id":"s3","type":"scene","metadata":{"name":"Nightlight"},"group":{"rid":"room-2","rtype":"room"}},
              {"id":"s4","type":"scene","metadata":{"name":"Orphan"},"group":{"rid":"gone","rtype":"zone"}}]}
        """), rooms)
        assertEquals(
            listOf(
                HueSceneRef("s3", "Nightlight", "Bedroom"),
                HueSceneRef("s2", "Concentrate", "Living room"),
                HueSceneRef("s1", "Relax", "Living room"),
                HueSceneRef("s4", "Orphan", null),
            ),
            scenes,
        )
    }

    @Test
    fun v2Errors_reportsDescriptionsOrNull() {
        assertNull(HueParsing.v2Errors(json("""{"errors":[],"data":[{"rid":"s1","rtype":"scene"}]}""")))
        assertEquals(
            "unauthorized user",
            HueParsing.v2Errors(json("""{"errors":[{"description":"unauthorized user"}],"data":[]}""")),
        )
    }

    @Test
    fun parseScenes_throwsOnErrors() {
        assertThrows(HueException::class.java) {
            HueParsing.parseScenes(json("""{"errors":[{"description":"resource not found"}],"data":[]}"""), emptyMap())
        }
    }
}
