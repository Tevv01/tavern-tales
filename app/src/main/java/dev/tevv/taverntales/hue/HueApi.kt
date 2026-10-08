package dev.tevv.taverntales.hue

import android.annotation.SuppressLint
import dev.tevv.taverntales.model.HueSceneRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class HueException(message: String) : Exception(message)

/** Result of asking the bridge for an app key. */
sealed interface PairResult {
    data class Success(val appKey: String) : PairResult
    data object LinkButtonNotPressed : PairResult
    data class Failed(val message: String) : PairResult
}

data class BridgeInfo(val id: String, val name: String)

/**
 * Minimal client for a Hue bridge on the local network (CLIP API v2, plus the v1 endpoints still
 * needed for pairing and the unauthenticated config).
 *
 * The bridge only serves HTTPS with its own certificate, which no public CA vouches for. Instead the
 * certificate is pinned on first contact during pairing ([seenPin]) and required to match afterwards
 * ([expectedPin]): trust on first use, as the Hue app itself does on the local network.
 */
class HueApi(private val ip: String, expectedPin: String?) {
    private val trust = PinningTrustManager(expectedPin)
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory, trust)
        // The certificate names the bridge id, not its IP; the pin above is what identifies the bridge.
        .hostnameVerifier { hostname, _ -> hostname == ip }
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** SHA-256 of the bridge certificate's public key, as seen on the last connection. */
    val seenPin: String? get() = trust.seenPin

    suspend fun config(): BridgeInfo = HueParsing.parseConfig(get("/api/0/config"))

    suspend fun pair(): PairResult =
        HueParsing.parsePair(post("/api", """{"devicetype":"tavern_tales#android","generateclientkey":true}"""))

    /** All scenes on the bridge, labelled with their room or zone and sorted for display. */
    suspend fun scenes(appKey: String): List<HueSceneRef> {
        val groups = HueParsing.parseGroupNames(get("/clip/v2/resource/room", appKey)) +
            HueParsing.parseGroupNames(get("/clip/v2/resource/zone", appKey))
        return HueParsing.parseScenes(get("/clip/v2/resource/scene", appKey), groups)
    }

    suspend fun recall(appKey: String, sceneId: String) {
        val response = put("/clip/v2/resource/scene/$sceneId", """{"recall":{"action":"active"}}""", appKey)
        HueParsing.v2Errors(response)?.let { throw HueException(it) }
    }

    private suspend fun get(path: String, appKey: String? = null) = call(Request.Builder().get(), path, appKey)

    private suspend fun post(path: String, body: String) =
        call(Request.Builder().post(body.toRequestBody(JSON_TYPE)), path, null)

    private suspend fun put(path: String, body: String, appKey: String) =
        call(Request.Builder().put(body.toRequestBody(JSON_TYPE)), path, appKey)

    private suspend fun call(builder: Request.Builder, path: String, appKey: String?): JsonElement =
        withContext(Dispatchers.IO) {
            val request = builder.url("https://$ip$path").apply { appKey?.let { header("hue-application-key", it) } }.build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.code == 403 || response.code == 401) throw HueException("The bridge no longer accepts this app; connect it again")
                if (!response.isSuccessful && body.isBlank()) throw HueException("Bridge answered ${response.code}")
                Json.parseToJsonElement(body)
            }
        }

    @SuppressLint("CustomX509TrustManager")
    private class PinningTrustManager(private val expectedPin: String?) : X509TrustManager {
        @Volatile var seenPin: String? = null

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val pin = MessageDigest.getInstance("SHA-256").digest(chain.first().publicKey.encoded)
                .joinToString("") { "%02x".format(it) }
            seenPin = pin
            if (expectedPin != null && pin != expectedPin) {
                throw CertificateException("This is not the Hue bridge the app was paired with")
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("Client certificates are not used")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}

/** Response parsing, kept free of networking so it can be unit-tested. */
object HueParsing {
    fun parseConfig(json: JsonElement): BridgeInfo {
        val obj = json.jsonObject
        val id = obj["bridgeid"]?.jsonPrimitive?.contentOrNull ?: throw HueException("Not a Hue bridge")
        return BridgeInfo(id = id, name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Hue Bridge")
    }

    fun parsePair(json: JsonElement): PairResult {
        val first = (json as? JsonArray)?.firstOrNull()?.jsonObject ?: return PairResult.Failed("Unexpected answer from the bridge")
        first["success"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull?.let { return PairResult.Success(it) }
        val error = first["error"]?.jsonObject ?: return PairResult.Failed("Unexpected answer from the bridge")
        if (error["type"]?.jsonPrimitive?.int == LINK_BUTTON_NOT_PRESSED) return PairResult.LinkButtonNotPressed
        return PairResult.Failed(error["description"]?.jsonPrimitive?.contentOrNull ?: "Pairing failed")
    }

    /** Room/zone id -> name. */
    fun parseGroupNames(json: JsonElement): Map<String, String> =
        data(json).associate { item ->
            item["id"]!!.jsonPrimitive.content to (item["metadata"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "")
        }

    fun parseScenes(json: JsonElement, groupNames: Map<String, String>): List<HueSceneRef> =
        data(json).map { item ->
            val groupId = item["group"]?.jsonObject?.get("rid")?.jsonPrimitive?.contentOrNull
            HueSceneRef(
                id = item["id"]!!.jsonPrimitive.content,
                name = item["metadata"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Scene",
                room = groupId?.let { groupNames[it] },
            )
        }.sortedWith(compareBy({ it.room ?: "￿" }, { it.name }))

    /** The error descriptions in a v2 response, or null if there are none. */
    fun v2Errors(json: JsonElement): String? =
        (json as? JsonObject)?.get("errors")?.jsonArray
            ?.mapNotNull { it.jsonObject["description"]?.jsonPrimitive?.contentOrNull }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("; ")

    private fun data(json: JsonElement): List<JsonObject> {
        v2Errors(json)?.let { throw HueException(it) }
        return json.jsonObject["data"]?.jsonArray?.map { it.jsonObject }.orEmpty()
    }

    private const val LINK_BUTTON_NOT_PRESSED = 101
}
