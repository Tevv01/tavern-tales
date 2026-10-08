package dev.tevv.taverntales.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long switching from one playing scene to another takes. The old scene's sounds fade out while
 * the new one's fade in, and the lights change over the same time.
 */
enum class SceneChange(val label: String, val durationMs: Long) {
    Quick("Quick", 1_500),
    Smooth("Smooth", 4_000),
    Slow("Slow", 8_000),
    ;

    /** "Smooth: 4 seconds". */
    val description: String
        get() = "$label: ${"%.1f".format(java.util.Locale.ROOT, durationMs / 1000.0).removeSuffix(".0")} seconds"

    companion object {
        val DEFAULT = Smooth

        fun fromName(name: String?): SceneChange = entries.find { it.name == name } ?: DEFAULT
    }
}

/**
 * Settings that belong to this phone rather than to the library (so they're not in backups), kept in
 * the same SharedPreferences file as the crash-report choice.
 */
class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _sceneChange = MutableStateFlow(SceneChange.fromName(prefs.getString(KEY_SCENE_CHANGE, null)))
    val sceneChange: StateFlow<SceneChange> = _sceneChange.asStateFlow()

    fun setSceneChange(value: SceneChange) {
        prefs.edit { putString(KEY_SCENE_CHANGE, value.name) }
        _sceneChange.value = value
    }

    private companion object {
        const val KEY_SCENE_CHANGE = "scene_change"
    }
}
