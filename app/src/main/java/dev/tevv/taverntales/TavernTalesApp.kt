package dev.tevv.taverntales

import android.app.Application
import android.content.Context
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.audio.SceneArtwork
import dev.tevv.taverntales.audio.SceneLauncher
import dev.tevv.taverntales.data.BackgroundStore
import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.data.Preferences
import dev.tevv.taverntales.data.backup.BackupManager
import dev.tevv.taverntales.hue.HueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TavernTalesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        crashReporting = CrashReporting(this).also { it.start() } // first, so later startup crashes are caught
        container = AppContainer(this)
    }

    lateinit var crashReporting: CrashReporting
        private set
}

/** Process-wide singletons. Kept by hand instead of a DI framework while the app is small. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val library = LibraryRepository(context.filesDir, appScope)
    val preferences = Preferences(context)
    val backgrounds = BackgroundStore(context)
    val mixer = AmbienceMixer(context, appScope)
    val hue = HueController(context, appScope)
    val launcher = SceneLauncher(mixer, hue, library, preferences, appScope)
    val backup = BackupManager(context, library, mixer)
    val artwork = SceneArtwork(context, appScope)
}
