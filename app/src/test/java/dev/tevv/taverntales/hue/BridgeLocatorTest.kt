package dev.tevv.taverntales.hue

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeLocatorTest {
    private val bridgeId = "001788FFFE123456"

    @Test
    fun findsTheBridgeByIdAtANewAddress() = runBlocking {
        val bridges = mapOf("10.0.0.7" to "ecb5fafffe000001", "10.0.0.9" to "001788fffe123456") // ids differ in case
        val found = BridgeLocator.locate(bridgeId, "10.0.0.3", flowOf("10.0.0.7", "10.0.0.9")) { bridges[it] }
        assertEquals("10.0.0.9", found)
    }

    @Test
    fun skipsTheOldAddressAndChecksEachAddressOnce() = runBlocking {
        val checked = mutableListOf<String>()
        val found = BridgeLocator.locate(bridgeId, "10.0.0.3", flowOf("10.0.0.3", "10.0.0.5", "10.0.0.5", "10.0.0.6")) {
            checked += it
            null
        }
        assertNull(found)
        assertEquals(listOf("10.0.0.5", "10.0.0.6"), checked)
    }

    @Test
    fun stopsCheckingOnceFound() = runBlocking {
        val checked = mutableListOf<String>()
        BridgeLocator.locate(bridgeId, "10.0.0.3", flowOf("10.0.0.4", "10.0.0.5")) {
            checked += it
            bridgeId
        }
        assertEquals(listOf("10.0.0.4"), checked)
    }

    @Test
    fun parseDiscoveryReadsIdsAndAddresses() {
        val json = Json.parseToJsonElement(
            """[{"id":"001788fffe123456","internalipaddress":"10.0.0.9","port":443},{"id":"broken"}]""",
        )
        assertEquals(listOf("001788fffe123456" to "10.0.0.9"), HueParsing.parseDiscovery(json))
        assertEquals(emptyList<Pair<String, String>>(), HueParsing.parseDiscovery(Json.parseToJsonElement("{}")))
    }
}
