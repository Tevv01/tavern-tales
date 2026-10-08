package dev.tevv.taverntales

import android.app.Application
import android.content.Context
import dev.tevv.taverntales.audio.AmbienceMixer
import dev.tevv.taverntales.audio.SceneLauncher
import dev.tevv.taverntales.data.BackgroundStore
import dev.tevv.taverntales.data.LibraryRepository
import dev.tevv.taverntales.hue.HueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TavernTalesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Process-wide singletons. Kept by hand instead of a DI framework while the app is small. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val library = LibraryRepository(context.filesDir, appScope)
    val backgrounds = BackgroundStore(context)
    val mixer = AmbienceMixer(context, appScope)
    val hue = HueController(context, appScope)
    val launcher = SceneLauncher(mixer, hue)
}
