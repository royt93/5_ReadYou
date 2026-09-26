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
 * Foreground Service hosting the MediaSession and lock-screen/Bluetooth media controls for TTS.
 */
@AndroidEntryPoint
class TtsForegroundService : Service() {

    @Inject
    lateinit var ttsManager: TtsManager

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    ttsManager.resume()
                }

                override fun onPause() {
                    ttsManager.pause()
                }

                override fun onStop() {
                    ttsManager.stop()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_PLAY
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ttsManager.currentTitle.ifBlank { getString(R.string.read_you) }
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: ttsManager.currentSubtitle

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
            ACTION_STOP -> {
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
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

        val toggleAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this, 1,
                Intent(this, TtsForegroundService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pauseIntent
            ).build()
        } else {
            val playIntent = PendingIntent.getService(
                this, 1,
                Intent(this, TtsForegroundService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playIntent
            ).build()
        }

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TtsForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Stop",
            stopIntent
        ).build()

        val token = mediaSession?.sessionToken

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text to Speech Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and media status for article reading playback"
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
        const val TAG = "TtsForegroundService"
        const val CHANNEL_ID = "tts_playback_channel"
        const val NOTIFICATION_ID = 9182

        const val ACTION_PLAY = "com.mckimquyen.reader.tts.ACTION_PLAY"
        const val ACTION_PAUSE = "com.mckimquyen.reader.tts.ACTION_PAUSE"
        const val ACTION_STOP = "com.mckimquyen.reader.tts.ACTION_STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun start(context: Context, title: String, subtitle: String) {
            val intent = Intent(context, TtsForegroundService::class.java).apply {
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
            val intent = Intent(context, TtsForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TtsForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
