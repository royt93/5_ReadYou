package com.mckimquyen.reader.infrastructure.podcast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import com.mckimquyen.reader.domain.sv.PodcastPlaybackController
import com.mckimquyen.reader.infrastructure.android.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground service that owns the ExoPlayer for podcasts and exposes a media session + notification. */
@AndroidEntryPoint
class PodcastPlaybackService : Service() {

    @Inject
    lateinit var controller: PodcastPlaybackController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: ExoPodcastEngine? = null
    private var session: MediaSessionCompat? = null
    private var lastNotificationKey: Triple<String?, PodcastStatus, String>? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val exo = ExoPodcastEngine(this).apply {
            onEnded = { controller.onEngineEnded() }
            onError = { controller.onEngineError() }
        }
        engine = exo
        val podcast = controller
        session = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = podcast.play()
                override fun onPause() = podcast.pause()
                override fun onStop() = podcast.stop()
                override fun onFastForward() = podcast.skip(PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onRewind() = podcast.skip(-PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onSeekTo(pos: Long) = podcast.seekTo((pos / MILLIS_PER_SECOND).toInt())
            })
            isActive = true
        }
        startForeground(NOTIFICATION_ID, buildNotification(controller.state.value))
        controller.attachEngine(exo)
        scope.launch {
            while (isActive) {
                controller.onEngineTick()
                render(controller.state.value)
                if (controller.state.value.status == PodcastStatus.IDLE) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                delay(PodcastPlaybackController.TICK_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> controller.play()
            ACTION_PAUSE -> controller.pause()
            ACTION_REWIND -> controller.skip(-PodcastPlaybackController.SKIP_INTERVAL_SEC)
            ACTION_FORWARD -> controller.skip(PodcastPlaybackController.SKIP_INTERVAL_SEC)
            ACTION_STOP -> controller.stop()
        }
        render(controller.state.value)
        return START_NOT_STICKY
    }

    private fun render(state: PodcastPlaybackState) {
        updateSession(state)
        val key = Triple(state.articleId, state.status, state.title)
        if (key != lastNotificationKey) {
            lastNotificationKey = key
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun updateSession(state: PodcastPlaybackState) {
        val code = when (state.status) {
            PodcastStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PodcastStatus.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
            PodcastStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PodcastStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
            PodcastStatus.IDLE -> PlaybackStateCompat.STATE_STOPPED
        }
        val speed = if (state.status == PodcastStatus.PLAYING) state.speed else 0f
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(SESSION_ACTIONS)
                .setState(code, state.positionSec * MILLIS_PER_SECOND, speed)
                .build()
        )
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.feedName)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.durationSec * MILLIS_PER_SECOND)
                .build()
        )
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, PodcastPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(state: PodcastPlaybackState): Notification {
        val isPlaying = state.status == PodcastStatus.PLAYING || state.status == PodcastStatus.PREPARING
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.title.ifBlank { getString(R.string.read_you) })
            .setContentText(state.feedName)
            .setContentIntent(openApp)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
        PodcastNotificationActions.actionNames(isPlaying).forEachIndexed { index, name ->
            val (icon, label, action) = when (name) {
                PodcastNotificationActions.REWIND ->
                    Triple(android.R.drawable.ic_media_rew, getString(R.string.podcast_skip_back), ACTION_REWIND)
                PodcastNotificationActions.TOGGLE ->
                    if (isPlaying) Triple(android.R.drawable.ic_media_pause, getString(R.string.podcast_pause), ACTION_PAUSE)
                    else Triple(android.R.drawable.ic_media_play, getString(R.string.podcast_play), ACTION_PLAY)
                PodcastNotificationActions.FORWARD ->
                    Triple(android.R.drawable.ic_media_ff, getString(R.string.podcast_skip_forward), ACTION_FORWARD)
                else -> Triple(android.R.drawable.ic_delete, getString(R.string.podcast_stop), ACTION_STOP)
            }
            builder.addAction(NotificationCompat.Action.Builder(icon, label, serviceIntent(action, index)).build())
        }
        return builder.setStyle(
            MediaStyle()
                .setMediaSession(session?.sessionToken)
                .setShowActionsInCompactView(*PodcastNotificationActions.compactIndices.toIntArray())
        ).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.podcast_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        engine?.let {
            controller.detachEngine(it)
            it.release()
        }
        engine = null
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "PodcastPlaybackService"
        const val CHANNEL_ID = "podcast_playback_channel"
        const val NOTIFICATION_ID = 9183
        const val ACTION_PLAY = "com.mckimquyen.reader.podcast.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mckimquyen.reader.podcast.ACTION_PAUSE"
        const val ACTION_REWIND = "com.mckimquyen.reader.podcast.ACTION_REWIND"
        const val ACTION_FORWARD = "com.mckimquyen.reader.podcast.ACTION_FORWARD"
        const val ACTION_STOP = "com.mckimquyen.reader.podcast.ACTION_STOP"
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_SEEK_TO

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PodcastPlaybackService::class.java).setAction(ACTION_PLAY),
            )
        }
    }
}
