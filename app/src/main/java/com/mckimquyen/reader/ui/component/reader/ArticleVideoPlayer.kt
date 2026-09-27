package com.mckimquyen.reader.ui.component.reader

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mckimquyen.reader.infrastructure.media.video.VideoPipController
import com.mckimquyen.reader.infrastructure.pref.LocalReadingVideoAutoplay

/**
 * Plays an in-article video with the native ExoPlayer UI.
 *
 * The player is owned by this composable, never by a ViewModel or singleton: it is created in
 * [remember] and released in [DisposableEffect], so leaving the reading page frees the codec. While
 * a video is actually playing it registers with [VideoPipController] so `MainActivity` can shrink
 * it into Picture-in-Picture when the user leaves the app.
 *
 * @param autoplay when true the video starts on its own, always muted so it cannot make noise the
 *   reader did not ask for. Follows [LocalReadingVideoAutoplay] unless a caller overrides it.
 */
@Composable
fun ArticleVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = LocalReadingVideoAutoplay.current.value,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            // Autoplay starts silent: a video that the reader never asked to play must never make
            // noise. Unmuting is one tap on the player's own volume control.
            if (autoplay) volume = 0f
            playWhenReady = autoplay
            prepare()
        }
    }

    // Keep the PiP marker in step with what the player is really doing, so a paused or finished
    // video does not shrink into PiP when the user simply leaves the app.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    val size = player.videoSize
                    VideoPipController.onPlaying(width = size.width, height = size.height)
                } else {
                    VideoPipController.onStopped()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Dimensions arrive after the first decoded frame; refresh so PiP uses the real
                // aspect ratio rather than the 16:9 fallback.
                if (player.isPlaying) {
                    VideoPipController.onPlaying(width = videoSize.width, height = videoSize.height)
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            VideoPipController.onStopped()
            player.release()
        }
    }

    // Pause when the app goes to the background, unless PiP took over — PiP is precisely the case
    // where playback should keep running.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
            }
        },
        onRelease = { view ->
            // Detach before the view dies so PlayerView does not hold a released player.
            view.player = null
        },
    )
}
