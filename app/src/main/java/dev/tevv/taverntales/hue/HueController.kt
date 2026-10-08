package dev.tevv.taverntales.hue

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import dev.tevv.taverntales.model.HueSceneRef
import dev.tevv.taverntales.model.LightSetup
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * The bridge this app is paired with. [certPin] identifies it on later connections (see [HueApi]).
 * [group] is the room or zone that light setups made in the app are applied to.
 */
@Serializable
data class HueBridge(
    val ip: String,
    val id: String,
    val name: String,
    val appKey: String,
    val certPin: String,
    val group: HueGroupRef? = null,
)

@Serializable
data class HueGroupRef(val id: String, val type: String, val name: String)

/** A bridge seen on the local network but not necessarily paired. */
data class FoundBridge(val name: String, val ip: String)

/**
 * Pairing, discovery and scene recall for one Philips Hue bridge. Process-wide (in the AppContainer);
 * the pairing is stored in `filesDir/hue.json`.
 */
class HueController(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(context.filesDir, "hue.json"))
    private val json = Json { ignoreUnknownKeys = true }

    private val _bridge = MutableStateFlow(load())
    val bridge: StateFlow<HueBridge?> = _bridge.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Hue bridges advertised on the Wi-Fi (mDNS `_hue._tcp`), as they are found. */
    fun discover(): Flow<FoundBridge> = callbackFlow {
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val resolveExecutor = Executors.newSingleThreadExecutor()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) = resolve(nsd, service, resolveExecutor) { trySend(it) }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery failed: $errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices("_hue._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            resolveExecutor.shutdown()
        }
    }

    /**
     * Pairs with the bridge at [ip]. The user has to press the bridge's link button; this keeps asking
     * for up to [timeoutSeconds], reporting the seconds left through [onWaiting].
     */
    suspend fun pair(ip: String, timeoutSeconds: Int = 90, onWaiting: (secondsLeft: Int) -> Unit): Result<HueBridge> =
        runCatching {
            val api = HueApi(ip, expectedPin = null)
            val info = api.config()
            for (secondsLeft in timeoutSeconds downTo 1) {
                when (val result = api.pair()) {
                    is PairResult.Success -> {
                        val bridge = HueBridge(ip, info.id, info.name, result.appKey, api.seenPin!!)
                        save(bridge)
                        return@runCatching bridge
                    }
                    PairResult.LinkButtonNotPressed -> {
                        onWaiting(secondsLeft)
                        delay(1000)
                    }
                    is PairResult.Failed -> throw HueException(result.message)
                }
            }
            throw HueException("The link button wasn't pressed in time")
        }

    fun disconnect() {
        _bridge.value = null
        file.delete()
    }

    /** All scenes on the paired bridge. Throws if none is paired or it can't be reached. */
    suspend fun scenes(): List<HueSceneRef> {
        val bridge = _bridge.value ?: throw HueException("No Hue bridge connected")
        return withBridgeErrors { HueApi(bridge.ip, bridge.certPin).scenes(bridge.appKey) }
    }

    /** Rooms and zones on the bridge, for choosing where light setups go. */
    suspend fun groups(): List<HueGroup> {
        val bridge = _bridge.value ?: throw HueException("No Hue bridge connected")
        return withBridgeErrors { HueApi(bridge.ip, bridge.certPin).groups(bridge.appKey) }
            .sortedWith(compareBy({ it.type }, { it.name }))
    }

    suspend fun setGroup(group: HueGroup) {
        val bridge = _bridge.value ?: return
        save(bridge.copy(group = HueGroupRef(group.id, group.type, group.name)))
    }

    /** Switches the lights to [scene] in the background; failures are reported on [errors]. */
    fun recall(scene: HueSceneRef) = launchLightChange("Couldn't set the lights to \"${scene.name}\"") { bridge, api ->
        api.recall(bridge.appKey, scene.id)
    }

    /** Applies a light setup to the chosen room or zone in the background. */
    fun apply(setup: LightSetup) = launchLightChange("Couldn't set the lights") { bridge, api ->
        val group = bridge.group ?: throw HueException("choose a room for scene lighting in the Hue settings")
        val members = api.groups(bridge.appKey).find { it.id == group.id }
            ?: throw HueException("the room \"${group.name}\" no longer exists; choose another in the Hue settings")
        val lights = api.lights(bridge.appKey).filter(members::contains)
        if (lights.isEmpty()) throw HueException("there are no lights in \"${group.name}\"")
        LightCommands.build(setup, lights).forEach { (lightId, body) ->
            api.setLight(bridge.appKey, lightId, body.toString())
            delay(LIGHT_COMMAND_GAP_MS)
        }
    }

    private var lightJob: Job? = null

    /** Runs one light change at a time: starting another scene's lights cancels one still being sent. */
    private fun launchLightChange(failure: String, block: suspend (HueBridge, HueApi) -> Unit) {
        val bridge = _bridge.value ?: return
        lightJob?.cancel()
        lightJob = scope.launch {
            runCatching { withBridgeErrors { block(bridge, HueApi(bridge.ip, bridge.certPin)) } }
                .onFailure { if (it !is CancellationException) _errors.tryEmit("$failure: ${it.message}") }
        }
    }

    private suspend fun <T> withBridgeErrors(block: suspend () -> T): T = try {
        block()
    } catch (e: HueException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Bridge request failed", e)
        throw HueException("the bridge can't be reached (is the phone on the same Wi-Fi?)")
    }

    private fun load(): HueBridge? = try {
        if (file.baseFile.exists()) json.decodeFromString<HueBridge>(file.readFully().decodeToString()) else null
    } catch (e: Exception) {
        Log.e(TAG, "Could not read hue.json", e)
        null
    }

    private suspend fun save(bridge: HueBridge) {
        withContext(Dispatchers.IO) {
            val out = file.startWrite()
            try {
                out.write(json.encodeToString(HueBridge.serializer(), bridge).encodeToByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
        _bridge.value = bridge
    }

    @Suppress("DEPRECATION") // resolveService is replaced by registerServiceInfoCallback only from API 34
    private fun resolve(nsd: NsdManager, service: NsdServiceInfo, executor: java.util.concurrent.Executor, found: (FoundBridge) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsd.registerServiceInfoCallback(service, executor, object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) {
                    info.hostAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress?.let {
                        found(FoundBridge(info.serviceName, it))
                    }
                    runCatching { nsd.unregisterServiceInfoCallback(this) }
                }
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackUnregistered() = Unit
            })
        } else {
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    info.host?.hostAddress?.let { found(FoundBridge(info.serviceName, it)) }
                }
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            })
        }
    }

    private companion object {
        const val TAG = "HueController"
        /** The bridge handles about 10 light commands per second; stay under that. */
        const val LIGHT_COMMAND_GAP_MS = 110L
    }
}
