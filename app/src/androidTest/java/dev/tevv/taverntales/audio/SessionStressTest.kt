package dev.tevv.taverntales.audio

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import dev.tevv.taverntales.MainActivity
import dev.tevv.taverntales.data.DefaultLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A game night's worth of mixer activity, compressed into a couple of minutes: far more scene
 * switches, layer toggles and events than a real session, with overlapping fades and event tails.
 * Every player must be released and the playback service stopped after each session, and a second
 * session must not add threads or memory on top of the first: leaks that would build up over hours
 * show up here quickly.
 *
 * Plays real audio: mute the phone's media volume before running it.
 *
 * Don't run it with `connectedDebugAndroidTest` on a phone with real data: that task uninstalls the
 * app afterwards, deleting its library and Hue pairing. Instead:
 *   ./gradlew installDebug installDebugAndroidTest
 *   adb shell am instrument -w -e class dev.tevv.taverntales.audio.SessionStressTest \
 *       dev.tevv.taverntales.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SessionStressTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun longSessionReleasesEverything() {
        // The playback service may only be started while the app is in the foreground.
        ActivityScenario.launch(MainActivity::class.java).use {
            settle()
            val threadsBefore = threadNames()
            val pssBefore = Debug.getPss()

            // Two full sessions: the first may warm up pools that Android and the media stack keep
            // around (binder and codec threads); a real leak keeps growing in the second.
            val first = session()
            settle()
            val threadsMiddle = threadNames()
            val pssMiddle = Debug.getPss()
            val second = session()
            settle()
            val threadsAfter = threadNames()
            val pssAfter = Debug.getPss()

            val report = "threads ${threadsBefore.size} -> ${threadsMiddle.size} -> ${threadsAfter.size} " +
                "(peaks ${first.peakThreads}, ${second.peakThreads}); PSS ${pssBefore / 1024} -> ${pssMiddle / 1024} -> " +
                "${pssAfter / 1024} MB (peaks ${first.peakPss / 1024}, ${second.peakPss / 1024}); peak voices ${second.peakVoices}"
            Log.i(TAG, report)
            Log.i(TAG, "threads added by first session: ${diff(threadsMiddle, threadsBefore)}")
            Log.i(TAG, "threads added by second session: ${diff(threadsAfter, threadsMiddle)}")

            assertFalse("playback service still running", serviceRunning())
            assertTrue("threads leaked: $report", threadsAfter.size <= threadsMiddle.size + THREAD_SLACK)
            assertTrue("memory grew too much: $report", pssAfter - pssMiddle < PSS_SLACK_KB)
        }
    }

    private class Peaks(val peakThreads: Int, val peakPss: Long, val peakVoices: Int)

    /** One compressed game night on a fresh mixer; checks everything is released at the end. */
    private fun session(): Peaks {
        val scope = MainScope()
        val mixer = AmbienceMixer(context, scope)
        val scenes = DefaultLibrary.create().collections.flatMap { it.scenes }
        val events = DefaultLibrary.events()
        var peakThreads = 0
        var peakPss = 0L
        var peakVoices = 0
        runBlocking {
            withContext(Dispatchers.Main) {
                repeat(CYCLES) { i ->
                    val scene = scenes[i % scenes.size]
                    mixer.startScene(scene) // fades out the previous scene
                    delay(400)
                    scene.layers.firstOrNull { !it.autoPlay }?.let { mixer.toggleLayer(scene.id, it) }
                    delay(200)
                    mixer.toggleLayer(scene.id, scene.layers.first()) // turn one off again
                    mixer.playEvent(events[i % events.size])
                    if (i % 4 == 0) mixer.setMasterVolume(0.4f + (i % 3) * 0.3f)
                    delay(300)
                    if (i % 10 == 9) {
                        mixer.stopAll()
                        delay(2_000)
                    }
                    peakThreads = maxOf(peakThreads, threadNames().size)
                    peakPss = maxOf(peakPss, Debug.getPss())
                    peakVoices = maxOf(peakVoices, mixer.state.value.playing.size + mixer.state.value.events.size)
                }
                mixer.stopAll()
                delay(EVENT_TAIL_MS) // fade-outs and the longest event (thunder) finishing
                assertTrue("layers still playing: ${mixer.state.value.playing}", mixer.state.value.playing.isEmpty())
                assertTrue("events still playing: ${mixer.state.value.events}", mixer.state.value.events.isEmpty())
            }
        }
        scope.cancel()
        return Peaks(peakThreads, peakPss, peakVoices)
    }

    /** Thread names of this process (several threads can share a name). */
    private fun threadNames(): List<String> =
        File("/proc/self/task").listFiles().orEmpty().mapNotNull { runCatching { File(it, "comm").readText().trim() }.getOrNull() }

    /** Names in [after] beyond those in [before], with counts. */
    private fun diff(after: List<String>, before: List<String>): Map<String, Int> {
        val remaining = before.groupingBy { it }.eachCount().toMutableMap()
        val added = mutableMapOf<String, Int>()
        after.forEach { name ->
            val left = remaining[name] ?: 0
            if (left > 0) remaining[name] = left - 1 else added[name] = (added[name] ?: 0) + 1
        }
        return added
    }

    private fun settle() {
        repeat(3) {
            Runtime.getRuntime().gc()
            Thread.sleep(500)
        }
    }

    @Suppress("DEPRECATION") // still returns this app's own services
    private fun serviceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == AmbienceService::class.java.name }
    }

    private companion object {
        const val TAG = "SessionStress"
        const val CYCLES = 120
        const val EVENT_TAIL_MS = 14_000L
        const val THREAD_SLACK = 8
        const val PSS_SLACK_KB = 40 * 1024
    }
}
