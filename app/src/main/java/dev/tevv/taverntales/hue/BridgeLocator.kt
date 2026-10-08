package dev.tevv.taverntales.hue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

/** Finds the paired bridge again after the router gave it a new IP address. */
object BridgeLocator {
    /**
     * The first address in [candidates] (other than [oldIp]) where [idAt] reports [bridgeId], or null.
     * [idAt] should return null when there is no bridge there, or when it isn't the paired one (wrong
     * certificate). Each address is checked once, in the order found.
     */
    suspend fun locate(
        bridgeId: String,
        oldIp: String,
        candidates: Flow<String>,
        idAt: suspend (ip: String) -> String?,
    ): String? {
        val checked = mutableSetOf(oldIp)
        return candidates
            .filter { checked.add(it) }
            .firstOrNull { ip -> idAt(ip)?.equals(bridgeId, ignoreCase = true) == true }
    }
}
