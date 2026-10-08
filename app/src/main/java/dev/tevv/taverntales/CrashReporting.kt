package dev.tevv.taverntales

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Opt-in crash reporting with Sentry. Nothing is sent unless the user has agreed, and only builds
 * that have a DSN (release and releaseTest) can report at all; see the manifest and build file.
 *
 * Reports contain the crash's stack trace, device facts (model, Android version, memory, locale,
 * time zone...) and Sentry's random per-install id. No user info, IP address, screenshots or view
 * hierarchy. Keep the README's Privacy section in sync with this.
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
        }
    }

    private companion object {
        const val KEY = "crash_reports"
    }
}
