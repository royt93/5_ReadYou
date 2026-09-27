package com.mckimquyen.reader.infrastructure.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.android.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground Service hosting the MediaSession and lock-screen/Bluetooth media controls for CommuteCast.
 */
@AndroidEntryPoint
class CommuteMediaSessionService : Service() {

    @Inject
    lateinit var audioPlayer: CommuteAudioPlayer

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    audioPlayer.resume()
                }

                override fun onPause() {
                    audioPlayer.pause()
                }

                override fun onSkipToNext() {
                    audioPlayer.skipNext()
                }

                override fun onSkipToPrevious() {
                    audioPlayer.skipPrevious()
                }

                override fun onStop() {
                    audioPlayer.stopAndReset()
                    stopServiceGracefully()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_PLAY
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: audioPlayer.playerState.value.episode?.title
            ?: getString(R.string.commute_cast_title)
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)
            ?: audioPlayer.playerState.value.currentDialogue?.let { "${it.speaker.name}: ${it.text}" }
            ?: ""

        when (action) {
            ACTION_PLAY -> {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                val notification = buildNotification(title, subtitle, isPlaying = true)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_PAUSE -> {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                val notification = buildNotification(title, subtitle, isPlaying = false)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_SKIP_NEXT -> {
                audioPlayer.skipNext()
            }
            ACTION_SKIP_PREV -> {
                audioPlayer.skipPrevious()
            }
            ACTION_STOP -> {
                stopServiceGracefully()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopServiceGracefully() {
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )
    }

    private fun buildNotification(title: String, subtitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_SKIP_PREV },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous,
            getString(R.string.commute_media_action_prev),
            prevIntent
        ).build()

        val toggleAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this, 2,
                Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(R.string.commute_media_action_pause),
                pauseIntent
            ).build()
        } else {
            val playIntent = PendingIntent.getService(
                this, 2,
                Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(R.string.commute_media_action_play),
                playIntent
            ).build()
        }

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_SKIP_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next,
            getString(R.string.commute_media_action_next),
            nextIntent
        ).build()

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, CommuteMediaSessionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            getString(R.string.commute_media_action_stop),
            stopIntent
        ).build()

        val token = mediaSession?.sessionToken

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(prevAction)
            .addAction(toggleAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.commute_media_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.commute_media_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "CommuteMediaSession"
        const val CHANNEL_ID = "commute_cast_playback_channel"
        const val NOTIFICATION_ID = 9183

        const val ACTION_PLAY = "com.mckimquyen.reader.commute.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mckimquyen.reader.commute.ACTION_PAUSE"
        const val ACTION_SKIP_NEXT = "com.mckimquyen.reader.commute.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.mckimquyen.reader.commute.ACTION_SKIP_PREV"
        const val ACTION_STOP = "com.mckimquyen.reader.commute.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun start(context: Context, title: String, subtitle: String) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CommuteMediaSessionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
