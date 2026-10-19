package com.mckimquyen.reader.infrastructure.podcast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mckimquyen.reader.domain.sv.PodcastEngine

/** ExoPlayer-backed [PodcastEngine]. Create and call on the main thread. */
@OptIn(UnstableApi::class)
class ExoPodcastEngine(context: Context) : PodcastEngine {

    var onEnded: (() -> Unit)? = null
    var onError: (() -> Unit)? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                        .setReadTimeoutMs(READ_TIMEOUT_MS),
                )
            )
        )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded?.invoke()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onError?.invoke()
                }
            })
        }

    override fun prepare(url: String, startPositionSec: Int) {
        player.setMediaItem(MediaItem.fromUri(url), startPositionSec * MILLIS_PER_SECOND)
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekToSec(sec: Int) = player.seekTo(sec * MILLIS_PER_SECOND)

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    override fun positionSec(): Int = (player.currentPosition / MILLIS_PER_SECOND).toInt()

    override fun durationSec(): Int =
        player.duration.takeIf { it != C.TIME_UNSET }?.let { (it / MILLIS_PER_SECOND).toInt() } ?: 0

    fun release() = player.release()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
