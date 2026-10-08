package dev.tevv.taverntales

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import androidx.core.content.edit
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.Feedback
import io.sentry.protocol.SentryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.IOException
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
 *
 * Bug reports the user writes ([sendBugReport]) go to the same Sentry project as user feedback.
 * Sending one is consent for that report only: it works with crash reports switched off.
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
    fun readReport(file: File): String = runCatching { pretty(file.readText()) }
        .getOrElse { runCatching { file.readText() }.getOrDefault("") }

    /** The bug report being sent, as Sentry serialized it (set in beforeSendFeedback). */
    @Volatile private var lastFeedback: String? = null
    private val feedbackLock = Mutex()

    /**
     * Sends a bug report: the user's [message], their [email] if they want a reply, and the app's
     * [log] if they chose to include it (as an attachment). With crash reports off, Sentry is started
     * just for this report, without crash or ANR capture or sessions, and closed again afterwards.
     * Returns the report exactly as it was sent, for the user to see; throws if it can't be sent.
     */
    suspend fun sendBugReport(message: String, email: String?, log: String?): String = feedbackLock.withLock {
        check(available) { "This build can't send reports" }
        if (!online()) throw IOException("No internet connection. Connect and try again.")
        val temporary = !Sentry.isEnabled()
        if (temporary) withContext(Dispatchers.Main) { init(crashReports = false) }
        try {
            withContext(Dispatchers.IO) {
                lastFeedback = null
                val feedback = Feedback(message).apply { contactEmail = email }
                val hint = Hint()
                log?.let { hint.addAttachment(Attachment(it.toByteArray(), LOG_FILE, "text/plain")) }
                if (Sentry.feedback().capture(feedback, hint) == SentryId.EMPTY_ID) throw IOException("The report couldn't be sent.")
                Sentry.flush(FLUSH_TIMEOUT_MS)
                val sent = lastFeedback?.let { runCatching { pretty(it) }.getOrDefault(it) }.orEmpty()
                if (log != null) "$sent\n\nAttached as $LOG_FILE:\n\n$log" else sent
            }
        } finally {
            if (temporary) withContext(Dispatchers.Main) { Sentry.close() }
        }
    }

    /**
     * This process's recent log lines (Android only lets an app read its own): the app's messages and
     * ExoPlayer's, plus warnings and errors from anything else in the process (Android and the phone's
     * system libraries). The phone's info-level screen-drawing chatter is left out, so the useful lines
     * aren't pushed out. Keep the wording on the bug report and Privacy screens in line with this.
     */
    suspend fun recentLog(): String = withContext(Dispatchers.IO) {
        runCatching {
            val filters = LOG_TAGS.map { "$it:V" } + "*:W"
            val process = ProcessBuilder(listOf("logcat", "-d", "-v", "time", "-t", "$LOG_LINES", "--pid", "${Process.myPid()}") + filters)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }.trim()
        }.getOrDefault("")
    }

    private fun online(): Boolean {
        val connectivity = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

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

    /** Starts Sentry; with [crashReports] false only for sending a bug report (no crash, ANR or session data). */
    private fun init(crashReports: Boolean = true) {
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
            options.setBeforeSendFeedback { event, _ ->
                // Sentry adds the stack of the thread sending the report, which says nothing about the bug.
                event.threads = null
                lastFeedback = runCatching { StringWriter().also { options.serializer.serialize(event, it) }.toString() }.getOrNull()
                event
            }
            if (!crashReports) {
                options.isEnableUncaughtExceptionHandler = false
                options.isAnrEnabled = false
                options.isEnableAutoSessionTracking = false
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

    private fun pretty(json: String): String =
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(json))

    private companion object {
        const val KEY = "crash_reports"
        const val LOG_FILE = "app-log.txt"
        const val LOG_LINES = 400
        val LOG_TAGS = listOf("HueController", "LibraryRepository", "HomeViewModel", "ExoPlayerImpl", "AudioTrack")
        const val FLUSH_TIMEOUT_MS = 10_000L
        @OptIn(ExperimentalSerializationApi::class)
        val prettyJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  " // narrow phone screens
        }
    }
}
