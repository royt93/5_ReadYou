package com.mckimquyen.reader.ui.page.reels

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.infrastructure.media.video.VideoPipController
import com.mckimquyen.reader.infrastructure.media.video.VideoPipHelper
import com.mckimquyen.reader.ui.component.reader.ArticleVideoPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives the real in-article player on a device: it renders, the controls are reachable, playing a
 * video arms the Picture-in-Picture marker with a ratio the platform accepts, and leaving the page
 * disarms it.
 *
 * This exists because tapping blind screen coordinates could neither start playback nor prove PiP
 * was armed — the [VideoPipController] marker is the actual contract between the player and
 * `MainActivity.onUserLeaveHint()`, so the test asserts on that instead of on pixels.
 */
@RunWith(AndroidJUnit4::class)
class ArticleVideoPlaybackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @After
    fun tearDown() {
        VideoPipController.onStopped()
    }

    @Test
    fun playingAnArticleVideoArmsPipAndLeavingTheScreenDisarmsIt() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val attached = CountDownLatch(1)
        var playerView: PlayerView? = null

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ArticleVideoPlayer(url = PLAYABLE_VIDEO_URL) }
            }
            activity.setContentView(composeView)
        }

        // AndroidView inflates on the next frame, so poll the hierarchy instead of assuming.
        scenario.onActivity { activity ->
            playerView = activity.window.decorView.findPlayerView()
            if (playerView != null) attached.countDown()
        }
        if (attached.count > 0L) {
            repeat(ATTACH_POLLS) {
                if (playerView != null) return@repeat
                Thread.sleep(POLL_INTERVAL_MS)
                scenario.onActivity { activity ->
                    playerView = activity.window.decorView.findPlayerView()
                }
            }
        }

        val view = playerView
        assertNotNull("PlayerView never reached the view hierarchy", view)
        val player = view!!.player
        assertNotNull("PlayerView has no player attached", player)

        // The controls must be on screen, or the reader can never start the video by hand.
        assertTrue("PlayerView was laid out with no size", view.width > 0 && view.height > 0)

        val started = CountDownLatch(1)
        instrumentation.runOnMainSync {
            player!!.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) started.countDown()
                }
            })
            player.play()
        }

        val didStart = started.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue(
            "Video never started playing — check the source URL is reachable from the device",
            didStart,
        )

        val armed = VideoPipController.playing
        assertNotNull("Playing a video must arm the PiP marker", armed)

        val ratio = VideoPipHelper.aspectRatioFor(armed!!.width, armed.height)
        val value = ratio.numerator.toFloat() / ratio.denominator.toFloat()
        assertTrue(
            "Ratio $value from ${armed.width}x${armed.height} would crash enterPictureInPictureMode",
            value in MIN_LEGAL_RATIO..MAX_LEGAL_RATIO,
        )
        assertTrue("PiP must be supported on the test device", VideoPipHelper.isPipSupported(context))

        // Leaving the reading page has to release the codec and clear the marker, or a stale entry
        // would shrink a dead player into PiP later.
        scenario.close()
        assertNull("Leaving the page must disarm the PiP marker", VideoPipController.playing)
    }

    @Test
    fun autoplayStartsTheVideoMutedAndArmsPip() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var playerView: PlayerView? = null

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ArticleVideoPlayer(url = PLAYABLE_VIDEO_URL, autoplay = true) }
            }
            activity.setContentView(composeView)
        }

        repeat(ATTACH_POLLS) {
            if (playerView != null) return@repeat
            Thread.sleep(POLL_INTERVAL_MS)
            scenario.onActivity { activity ->
                playerView = activity.window.decorView.findPlayerView()
            }
        }

        val player = playerView?.player
        assertNotNull("PlayerView never reached the view hierarchy", player)

        // Autoplay that makes noise is worse than no autoplay at all.
        var volume = -1f
        instrumentation.runOnMainSync { volume = player!!.volume }
        assertEquals("Autoplay must start muted", 0f, volume, 0f)

        val started = CountDownLatch(1)
        instrumentation.runOnMainSync {
            if (player!!.isPlaying) {
                started.countDown()
            } else {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) started.countDown()
                    }
                })
            }
        }

        assertTrue(
            "Autoplay never started the video",
            started.await(PLAYBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        assertNotNull("Autoplaying must arm the PiP marker", VideoPipController.playing)

        scenario.close()
        assertNull("Leaving the page must disarm the PiP marker", VideoPipController.playing)
    }

    @Test
    fun autoplayDisabledLeavesTheVideoPausedAndPipDisarmed() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        var playerView: PlayerView? = null

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ArticleVideoPlayer(url = PLAYABLE_VIDEO_URL, autoplay = false) }
            }
            activity.setContentView(composeView)
        }

        repeat(ATTACH_POLLS) {
            if (playerView != null) return@repeat
            Thread.sleep(POLL_INTERVAL_MS)
            scenario.onActivity { activity ->
                playerView = activity.window.decorView.findPlayerView()
            }
        }

        val player = playerView?.player
        assertNotNull("PlayerView never reached the view hierarchy", player)

        // Give it as long as autoplay would have needed; it still must not start.
        Thread.sleep(NO_AUTOPLAY_GRACE_MS)
        var playWhenReady = true
        instrumentation.runOnMainSync { playWhenReady = player!!.playWhenReady }
        assertFalse("Autoplay is off, so the video must stay paused", playWhenReady)
        assertNull(
            "Nothing is playing, so PiP must stay disarmed",
            VideoPipController.playing,
        )

        scenario.close()
    }

    private fun View.findPlayerView(): PlayerView? {
        if (this is PlayerView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findPlayerView()?.let { return it }
        }
        return null
    }

    private companion object {
        /** Verified reachable with curl; the 403-ing Google sample cannot prove playback. */
        const val PLAYABLE_VIDEO_URL = "https://download.samplelib.com/mp4/sample-5s.mp4"
        const val PLAYBACK_TIMEOUT_SECONDS = 30L
        const val ATTACH_POLLS = 40
        const val POLL_INTERVAL_MS = 100L
        const val MAX_LEGAL_RATIO = 2.39f
        const val MIN_LEGAL_RATIO = 1f / MAX_LEGAL_RATIO

        /** Long enough that a video which was going to autoplay would already have started. */
        const val NO_AUTOPLAY_GRACE_MS = 5_000L
    }
}
