package dev.tevv.taverntales.hue

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Log
import dev.tevv.taverntales.model.HueSceneRef
import dev.tevv.taverntales.model.LightFlash
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
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
 *
 * Every bridge request goes through [withBridge]: if the bridge stops answering at its address (the
 * router gave it a new one), it is looked for on the network and the request retried there.
 */
class HueController(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(context.filesDir, "hue.json"))
    private val json = Json { ignoreUnknownKeys = true }

    private val _bridge = MutableStateFlow(load())
    val bridge: StateFlow<HueBridge?> = _bridge.asStateFlow()

    /** Things worth telling the user about (shown as snackbars): failures, and a bridge found at a new address. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val relocating = Mutex()

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
    suspend fun scenes(): List<HueSceneRef> = withBridge { bridge, api -> api.scenes(bridge.appKey) }

    /** Rooms and zones on the bridge, for choosing where light setups go. */
    suspend fun groups(): List<HueGroup> =
        withBridge { bridge, api -> api.groups(bridge.appKey) }.sortedWith(compareBy({ it.type }, { it.name }))

    suspend fun setGroup(group: HueGroup) {
        val bridge = _bridge.value ?: return
        save(bridge.copy(group = HueGroupRef(group.id, group.type, group.name)))
    }

    /** Switches the lights to [scene] in the background; failures are reported on [messages]. */
    fun recall(scene: HueSceneRef) = launchLightChange("Couldn't set the lights to \"${scene.name}\"") { bridge, api ->
        flashBaseline = null
        api.recall(bridge.appKey, scene.id)
    }

    /**
     * Applies a light setup to the chosen room or zone in the background. With [animate], the lights
     * then keep drifting gently (see [LightSetup.motion]) until the next light change or [stopMotion].
     */
    fun apply(setup: LightSetup, animate: Boolean = false) = launchLightChange("Couldn't set the lights") { bridge, api ->
        flashBaseline = null
        val room = room(bridge, api)
        val lights = api.lights(bridge.appKey).filter(room::contains)
        if (lights.isEmpty()) throw HueException("there are no lights in \"${room.name}\"")
        sendSetup(bridge, api, setup, lights, room.name, transitionMs = 1500)
        if (animate && setup.motion > 0f && setup.brightness > 0f) animate(bridge, api, setup, lights)
    }

    /**
     * Lights up the room for an event: the steps go to the room's grouped light so every bulb
     * changes at once. Afterwards the lights go back to [restoreTo] (the playing scene's light setup,
     * moving again if [animate]) or, without one, to exactly how they were before the flash.
     * Does nothing if no room is chosen for scene lighting.
     */
    fun flash(flash: LightFlash, restoreTo: LightSetup?, animate: Boolean) = launchLightChange("Couldn't flash the lights") { bridge, api ->
        if (bridge.group == null) return@launchLightChange
        val room = room(bridge, api)
        val groupedLight = room.groupedLightId ?: throw HueException("\"${room.name}\" can't be switched as one group")
        val (allLights, states) = api.lightsWithStates(bridge.appKey)
        val lights = allLights.filter(room::contains)
        // A flash interrupting another must restore to the state before the first one, not mid-flash.
        val baseline = flashBaseline ?: states.filterKeys { id -> lights.any { it.id == id } }
        flashBaseline = baseline
        for (step in LightFlashes.steps(flash)) {
            // Holds count from when a command is sent, so a slow reply doesn't stretch the rhythm.
            val sent = SystemClock.elapsedRealtime()
            api.setGroupedLight(bridge.appKey, groupedLight, step.body.toString())
            delay((step.holdMs - (SystemClock.elapsedRealtime() - sent)).coerceAtLeast(0))
        }
        val restoreMs = LightFlashes.restoreMs(flash)
        if (restoreTo != null) {
            sendSetup(bridge, api, restoreTo, lights, room.name, restoreMs)
        } else {
            for (light in lights) {
                val state = baseline[light.id] ?: continue
                runCatching { api.setLight(bridge.appKey, light.id, LightFlashes.restore(state, light, restoreMs).toString()) }
                    .onFailure { if (it is CancellationException) throw it }
                delay(LIGHT_COMMAND_GAP_MS)
            }
        }
        flashBaseline = null
        if (restoreTo != null && animate && restoreTo.motion > 0f && restoreTo.brightness > 0f) {
            animate(bridge, api, restoreTo, lights)
        }
    }

    /** Light states captured before a flash that hasn't finished restoring yet. */
    @Volatile private var flashBaseline: Map<String, LightState>? = null

    private suspend fun room(bridge: HueBridge, api: HueApi): HueGroup {
        val group = bridge.group ?: throw HueException("choose a room for scene lighting in the Hue settings")
        return api.groups(bridge.appKey).find { it.id == group.id }
            ?: throw HueException("the room \"${group.name}\" no longer exists; choose another in the Hue settings")
    }

    /** Sends a light setup to each light on its own: one that's switched off at the wall mustn't stop the others. */
    private suspend fun sendSetup(bridge: HueBridge, api: HueApi, setup: LightSetup, lights: List<HueLight>, roomName: String, transitionMs: Int) {
        val unresponsive = mutableListOf<HueLight>()
        LightCommands.build(setup, lights, transitionMs).forEach { (lightId, body) ->
            try {
                api.setLight(bridge.appKey, lightId, body.toString())
            } catch (e: HueException) {
                Log.w(TAG, "Light $lightId refused the update: ${e.message}")
                unresponsive += lights.first { it.id == lightId }
            }
            delay(LIGHT_COMMAND_GAP_MS)
        }
        if (unresponsive.size == lights.size) throw HueException("none of the lights in \"$roomName\" responded")
        reportUnresponsive(unresponsive)
    }

    /** Ids of the lights last reported as not responding, so the same ones aren't reported on every scene change. */
    private var lastUnresponsive = emptySet<String>()

    private fun reportUnresponsive(lights: List<HueLight>) {
        val ids = lights.map { it.id }.toSet()
        if (ids.isNotEmpty() && ids != lastUnresponsive) {
            val names = lights.joinToString(", ") { it.name.ifBlank { "a light" } }
            _messages.tryEmit(if (lights.size == 1) "$names didn't respond" else "These lights didn't respond: $names")
        }
        lastUnresponsive = ids
    }

    /** Stops the drifting started by [apply]; the lights stay as they are. */
    fun stopMotion() {
        if (moving) lightJob?.cancel()
    }

    @Volatile private var moving = false

    /**
     * Nudges one light at a time, cycling through the room so each light gets a new target about once
     * per [LightCommands.motionPeriodMs], fading to it over that same period. Runs until cancelled.
     */
    private suspend fun animate(startBridge: HueBridge, startApi: HueApi, setup: LightSetup, lights: List<HueLight>) {
        var bridge = startBridge
        var api = startApi
        val ordered = LightCommands.ordered(lights)
        val period = LightCommands.motionPeriodMs(setup.motion)
        val step = (period / ordered.size).coerceAtLeast(MIN_MOTION_STEP_MS)
        val random = kotlin.random.Random(System.nanoTime())
        var failures = 0
        moving = true
        try {
            delay(LIGHT_SETTLE_MS) // let the scene's own fade finish first
            while (true) {
                for ((index, light) in ordered.withIndex()) {
                    LightCommands.motionStep(setup, light, index, random)?.let { body ->
                        try {
                            api.setLight(bridge.appKey, light.id, body.toString())
                            failures = 0
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // A missed drift step isn't worth a message. After a few in a row the bridge has
                            // probably moved: look for it, and stop only if it can't be found.
                            Log.w(TAG, "Light motion step failed", e)
                            if (++failures >= 3) {
                                bridge = relocate(bridge) ?: return
                                api = HueApi(bridge.ip, bridge.certPin)
                                failures = 0
                            }
                        }
                    }
                    delay(step)
                }
            }
        } finally {
            moving = false
        }
    }

    private var lightJob: Job? = null

    /** Runs one light change at a time: starting another scene's lights cancels one still being sent. */
    private fun launchLightChange(failure: String, block: suspend (HueBridge, HueApi) -> Unit) {
        if (_bridge.value == null) return
        lightJob?.cancel()
        lightJob = scope.launch {
            runCatching { withBridge(block) }
                .onFailure { if (it !is CancellationException) _messages.tryEmit("$failure: ${it.message}") }
        }
    }

    /**
     * Runs [block] against the paired bridge. If the bridge can't be reached (connection refused or
     * timed out, or something else now answers at its address), it is looked for on the network and
     * [block] is retried once at its new address.
     */
    private suspend fun <T> withBridge(block: suspend (HueBridge, HueApi) -> T): T {
        val bridge = _bridge.value ?: throw HueException("No Hue bridge connected")
        try {
            return block(bridge, HueApi(bridge.ip, bridge.certPin))
        } catch (e: IOException) {
            Log.w(TAG, "Bridge at ${bridge.ip} not reachable", e)
        }
        val moved = relocate(bridge) ?: throw HueException(UNREACHABLE)
        return try {
            block(moved, HueApi(moved.ip, moved.certPin))
        } catch (e: IOException) {
            Log.w(TAG, "Bridge at ${moved.ip} not reachable either", e)
            throw HueException(UNREACHABLE)
        }
    }

    /**
     * Looks for the paired bridge at a new address: first on the Wi-Fi (mDNS), then via Hue's online
     * discovery service. A candidate only counts if it reports the paired bridge's id over a connection
     * with the pinned certificate. Saves and returns the bridge at its new address, or null.
     */
    private suspend fun relocate(bridge: HueBridge): HueBridge? = relocating.withLock {
        // Another request may have found it while this one waited for the lock.
        _bridge.value?.takeIf { it.id == bridge.id && it.ip != bridge.ip }?.let { return@withLock it }
        val candidates = flow {
            withTimeoutOrNull(MDNS_SEARCH_MS) { discover().collect { emit(it.ip) } }
            cloudDiscovery().forEach { (id, ip) -> if (id.equals(bridge.id, ignoreCase = true)) emit(ip) }
        }
        val ip = BridgeLocator.locate(bridge.id, bridge.ip, candidates) { candidate ->
            runCatching { HueApi(candidate, bridge.certPin).config().id }.getOrNull()
        } ?: return@withLock null
        Log.i(TAG, "Bridge ${bridge.id} moved from ${bridge.ip} to $ip")
        val moved = bridge.copy(ip = ip)
        save(moved)
        _messages.tryEmit("Found your Hue Bridge at its new address ($ip)")
        moved
    }

    /** Bridges Hue's discovery service knows on this network, as id to IP; empty if it can't be reached. */
    private suspend fun cloudDiscovery(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(CLOUD_DISCOVERY_URL).header("User-Agent", "TavernTales").build()
            cloudClient.newCall(request).execute().use { HueParsing.parseDiscovery(Json.parseToJsonElement(it.body.string())) }
        }.onFailure { Log.w(TAG, "Cloud discovery failed", it) }.getOrDefault(emptyList())
    }

    private val cloudClient by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
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
        const val UNREACHABLE = "the bridge can't be reached (is the phone on the same Wi-Fi?)"
        const val MDNS_SEARCH_MS = 6_000L
        const val CLOUD_DISCOVERY_URL = "https://discovery.meethue.com/"
        /** Drift steps are much rarer still, so other apps and switches stay responsive. */
        const val MIN_MOTION_STEP_MS = 700L
        const val LIGHT_SETTLE_MS = 1500L
    }
}
