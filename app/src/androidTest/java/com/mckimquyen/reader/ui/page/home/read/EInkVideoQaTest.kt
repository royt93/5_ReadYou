package com.mckimquyen.reader.ui.page.home.read

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.infrastructure.media.video.VideoPipController
import com.mckimquyen.reader.infrastructure.pref.EInkModePref
import com.mckimquyen.reader.infrastructure.pref.LocalEInkMode
import com.mckimquyen.reader.ui.component.reader.ArticleVideoPlayer
import com.mckimquyen.reader.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** PlayerView thật trong tap-zone E-Ink; fixture video cục bộ, không gọi dịch vụ ngoài. */
class EInkVideoQaTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun centerVideoControlsWorkAndLeavingReaderDetachesPlayer() {
        val turns = AtomicInteger()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(composeRule.activity.cacheDir, "eink-red-video.mp4")
        instrumentation.context.assets.open("eink/red-video.mp4").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        var visible by mutableStateOf(true)
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalEInkMode provides EInkModePref.ON) {
                    AppTheme(useDarkTheme = false) {
                        if (visible) {
                            Box(Modifier.fillMaxSize().eInkTapZones(true, "video-qa") { turns.incrementAndGet() }) {
                                ArticleVideoPlayer(url = fixture.toURI().toString(), modifier = Modifier.fillMaxSize(), autoplay = false)
                            }
                        }
                    }
                }
            }
            var view: PlayerView? = null
            composeRule.waitUntil(TIMEOUT_MS) {
                composeRule.runOnUiThread { view = findPlayer(composeRule.activity.window.decorView) }
                view != null
            }
            val playerView = requireNotNull(view)
            composeRule.waitUntil(TIMEOUT_MS) {
                var ready = false
                composeRule.runOnUiThread { ready = playerView.player?.playbackState == Player.STATE_READY }
                ready
            }
            composeRule.runOnUiThread {
                playerView.showController()
                // Chạm giữa AndroidView thật: event phải tới player view hierarchy, tap-zone không được lật trang.
                val now = android.os.SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, playerView.width / 2f, playerView.height / 2f, 0)
                val up = MotionEvent.obtain(now, now + TAP_DURATION_MS, MotionEvent.ACTION_UP, playerView.width / 2f, playerView.height / 2f, 0)
                try {
                    playerView.dispatchTouchEvent(down)
                    playerView.dispatchTouchEvent(up)
                } finally { down.recycle(); up.recycle() }
                val button = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)
                assertNotNull("Player phải có play/pause control", button)
                assertTrue("Play button phải enabled trong controller", requireNotNull(button).isEnabled)
            }
            composeRule.waitForIdle()
            assertEquals("Center video không được lật trang", 0, turns.get())
            visible = false
            composeRule.waitForIdle()
            composeRule.runOnUiThread { assertNull("PlayerView còn giữ player sau dispose", playerView.player) }
        } finally {
            visible = false
            composeRule.waitForIdle()
            fixture.delete()
        }
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val TAP_DURATION_MS = 50L
    }
}
