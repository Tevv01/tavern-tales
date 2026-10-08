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
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import dev.tevv.taverntales.MainActivity
import dev.tevv.taverntales.R
import dev.tevv.taverntales.TavernTalesApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive while [AmbienceMixer] plays. It does not own any
 * players itself. While it runs it also holds a [MediaSession] (backed by [MixerPlayer]), so the
 * playing scene shows up in Android's media controls: a media notification with previous scene,
 * stop and next scene, the lock screen, the quick settings player and headset buttons.
 */
@OptIn(UnstableApi::class)
class AmbienceService : Service() {

    private val scope = MainScope()
    private val container get() = (application as TavernTalesApp).container
    private lateinit var player: MixerPlayer
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.notification_channel_playback))
                .build(),
        )
        player = MixerPlayer(container.mixer, container.launcher, container.library.library, container.artwork)
        session = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(openAppIntent())
            .build()
        scope.launch {
            combine(container.mixer.state, container.library.library, container.artwork.updates) { _, _, _ -> }
                .collect {
                    player.refresh()
                    notify(buildNotification())
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                container.mixer.stopAll()
                return START_NOT_STICKY
            }
            ACTION_PREVIOUS -> {
                player.seekToPrevious()
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                player.seekToNext()
                return START_NOT_STICKY
            }
        }
        player.refresh()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Android 13 and newer draw the media controls from the session (title, collection, artwork,
     * previous/play-pause/next); older versions show this notification's own text and actions.
     */
    private fun buildNotification(): Notification {
        val item = player.currentMediaItem?.mediaMetadata
        val scene = player.currentMediaItem?.mediaId?.let { id ->
            container.library.library.value.collections.flatMap { it.scenes }.find { it.id == id }
        }
        val playingCount = container.mixer.state.value.playing.size
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(item?.title ?: getString(R.string.app_name))
            .setContentText(
                listOfNotNull(
                    item?.artist?.toString(),
                    resources.getQuantityString(R.plurals.sounds_playing, playingCount, playingCount),
                ).joinToString(" · "),
            )
            .setLargeIcon(scene?.let { container.artwork.get(it)?.bitmap })
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_skip_previous, getString(R.string.previous_scene), serviceIntent(ACTION_PREVIOUS, 1))
            .addAction(R.drawable.ic_stop, getString(R.string.stop), serviceIntent(ACTION_STOP, 2))
            .addAction(R.drawable.ic_skip_next, getString(R.string.next_scene), serviceIntent(ACTION_NEXT, 3))
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // scene names on the lock screen are fine
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, AmbienceService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val SESSION_ID = "tavern-tales"
        private const val ACTION_STOP = "dev.tevv.taverntales.action.STOP"
        private const val ACTION_PREVIOUS = "dev.tevv.taverntales.action.PREVIOUS"
        private const val ACTION_NEXT = "dev.tevv.taverntales.action.NEXT"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AmbienceService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, AmbienceService::class.java))
        }
    }
}
