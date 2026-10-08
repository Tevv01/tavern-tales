package dev.tevv.taverntales

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.StringWriter

/**
 * Opt-in crash reporting with Sentry. Nothing is sent unless the user has agreed, and only builds
 * that have a DSN (release and releaseTest) can report at all; see the manifest and build file.
 *
 * Reports contain the crash's stack trace, device facts (model, Android version, memory, locale,
 * time zone...) and Sentry's random per-install id. No user info, IP address, screenshots or view
 * hierarchy. Keep the README's Privacy section in sync with this.
 *
 * For transparency, every report is also saved on the phone exactly as it is sent ([pendingReports]),
 * and shown to the user (with a copy button) until they dismiss it.
 */
class CrashReporting(private val app: Application) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** False in builds without a DSN (debug): the question is never asked there. */
    val available: Boolean = runCatching {
        app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("io.sentry.dsn").isNullOrBlank().not()
    }.getOrDefault(false)

    /** The user's choice: null until they have answered. */
    private val _enabled = MutableStateFlow(if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null)
    val enabled: StateFlow<Boolean?> = _enabled.asStateFlow()

    private val reportsDir = File(app.filesDir, "crash-reports")

    /** Copies of sent reports the user hasn't seen yet, oldest first. */
    private val _pendingReports = MutableStateFlow(listReports())
    val pendingReports: StateFlow<List<File>> = _pendingReports.asStateFlow()

    /** The report as sent, pretty-printed for reading. */
    fun readReport(file: File): String = runCatching {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(file.readText()))
    }.getOrElse { runCatching { file.readText() }.getOrDefault("") }

    fun dismissReport(file: File) {
        file.delete()
        _pendingReports.value = listReports()
    }

    /** Call once at startup: starts reporting if the user agreed earlier. */
    fun start() {
        if (available && _enabled.value == true) init()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY, enabled) }
        _enabled.value = enabled
        if (!available) return
        if (enabled) init() else Sentry.close()
    }

    private fun init() {
        if (Sentry.isEnabled()) return
        SentryAndroid.init(app) { options ->
            options.environment = if (app.packageName.endsWith(".releasetest")) "test" else "production"
            options.dataCollection.userInfo = false // no user id or IP address
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.tracesSampleRate = null // crashes only, no performance data
            options.isEnableUserInteractionBreadcrumbs = false
            options.setBeforeSend { event, _ ->
                keepCopy(options, event)
                event
            }
        }
    }

    /**
     * Saves the event exactly as Sentry will send it (beforeSend runs after all of Sentry's own
     * processing). For a crash this runs in the dying process, so it only writes a small file.
     */
    private fun keepCopy(options: SentryOptions, event: SentryEvent) {
        try {
            reportsDir.mkdirs()
            val writer = StringWriter()
            options.serializer.serialize(event, writer)
            File(reportsDir, "${System.currentTimeMillis()}-${event.eventId}.json").writeText(writer.toString())
            _pendingReports.value = listReports() // shows it right away for reports made at startup (e.g. ANRs)
        } catch (e: Exception) {
            // Never let the transparency copy get in the way of the report itself.
        }
    }

    private fun listReports(): List<File> =
        reportsDir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()

    private companion object {
        const val KEY = "crash_reports"
        @OptIn(ExperimentalSerializationApi::class)
        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  " // narrow phone screens
        }
    }
}
