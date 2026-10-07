package dev.tevv.pocketbard

import android.app.Application
import android.content.Context
import dev.tevv.pocketbard.audio.AmbienceMixer
import dev.tevv.pocketbard.data.SceneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class PocketBardApp : Application() {
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
    val scenes = SceneRepository(File(context.filesDir, "scenes.json"), appScope)
    val mixer = AmbienceMixer(context, appScope)
}
