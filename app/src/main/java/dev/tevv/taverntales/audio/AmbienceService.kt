package dev.tevv.taverntales.audio

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.tevv.taverntales.MainActivity
import dev.tevv.taverntales.TavernTalesApp
import dev.tevv.taverntales.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while [AmbienceMixer] plays, and shows the
 * "now playing" notification with a Stop action. It does not own any players itself.
 */
class AmbienceService : Service() {

    private val scope = MainScope()
    private val container get() = (application as TavernTalesApp).container

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_playback))
                .build(),
        )
        scope.launch {
            combine(container.mixer.state, container.scenes.scenes) { state, scenes ->
                val sceneName = scenes.find { it.id == state.sceneId }?.name
                buildNotification(sceneName, state.playing.size)
            }.collect(::notify)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            container.mixer.stopAll()
            return START_NOT_STICKY
        }
        val state = container.mixer.state.value
        val sceneName = container.scenes.scenes.value.find { it.id == state.sceneId }?.name
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(sceneName, state.playing.size),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(sceneName: String?, playingCount: Int): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, AmbienceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(sceneName ?: getString(R.string.app_name))
            .setContentText(resources.getQuantityString(R.plurals.sounds_playing, playingCount, playingCount))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notify(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "dev.tevv.taverntales.action.STOP"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AmbienceService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, AmbienceService::class.java))
        }
    }
}
