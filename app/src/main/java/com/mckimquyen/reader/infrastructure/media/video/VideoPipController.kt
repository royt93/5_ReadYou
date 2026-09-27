package com.mckimquyen.reader.infrastructure.media.video

import java.util.concurrent.atomic.AtomicReference

/**
 * Tells the activity whether a video is currently worth shrinking into Picture-in-Picture.
 *
 * The player lives inside a composable while `onUserLeaveHint()` fires on the activity, so the two
 * need a meeting point. This holder stores only plain video dimensions — never a Context, Activity,
 * View or player reference — so a stale entry cannot leak anything; the player still clears it in
 * its `DisposableEffect` so a released player never triggers PiP.
 */
object VideoPipController {

    private val activeVideo = AtomicReference<PlayingVideo?>(null)

    /** Dimensions of the video currently playing, or null when nothing is playing. */
    val playing: PlayingVideo?
        get() = activeVideo.get()

    /** Marks a video as playing so leaving the app shrinks it into PiP. */
    fun onPlaying(width: Int, height: Int) {
        activeVideo.set(PlayingVideo(width = width, height = height))
    }

    /** Clears the marker; call on pause, on release, and whenever the player leaves composition. */
    fun onStopped() {
        activeVideo.set(null)
    }

    data class PlayingVideo(
        val width: Int,
        val height: Int,
    )
}
