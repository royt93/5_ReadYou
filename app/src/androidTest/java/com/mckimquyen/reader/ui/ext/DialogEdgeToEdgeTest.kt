package com.mckimquyen.reader.ui.ext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A Compose [Dialog] gets its own window, and that window defaults to
 * `decorFitsSystemWindows = true`: the OS insets the dialog's decor view around the system bars
 * before Compose sees it, so every `WindowInsets` composable inside — `safeDrawingPadding()`
 * included — reports zero. A full-bleed sheet that relies on `safeDrawingPadding()` alone then
 * draws its content flush with the very edge of the window, right where the status bar, a camera
 * cutout, or the gesture nav bar sits.
 *
 * This proves [DialogEdgeToEdge] fixes that: without it, the padding measured inside a dialog is
 * zero even on a device that has a real status bar; with it, the padding matches what the same
 * window's system bars actually require.
 */
@RunWith(AndroidJUnit4::class)
class DialogEdgeToEdgeTest {

    private fun measureSafeDrawingTopInsideDialog(applyFix: Boolean): Int {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        // Kept live across every layout pass, not just the first: setDecorFitsSystemWindows takes
        // an extra frame to propagate real insets down to Compose, so an early read can still see 0
        // even when the fix is working. The test waits out that race below instead of trusting the
        // first callback.
        val measuredTop = AtomicInteger(Int.MIN_VALUE)

        scenario.onActivity { activity ->
            // Matches MainActivity's own setup: the host window has to be edge-to-edge itself before
            // a child dialog window's insets can extend past its status bar, exactly like the real
            // app. Without this the bare test Activity constrains the dialog to its own non-edge-to-edge
            // bounds and the fix would look like a no-op for reasons that have nothing to do with it.
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val composeView = ComposeView(activity).apply {
                setContent {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        if (applyFix) {
                            DialogEdgeToEdge()
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .onGloballyPositioned { coordinates ->
                                    measuredTop.set(coordinates.boundsInWindow().top.toInt())
                                }
                        )
                    }
                }
            }
            activity.setContentView(composeView)
        }

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        var lastSeen = Int.MIN_VALUE
        while (System.currentTimeMillis() < deadline) {
            lastSeen = measuredTop.get()
            if (lastSeen > 0) break
            Thread.sleep(100)
        }
        assertTrue("Dialog content never laid out at all", lastSeen != Int.MIN_VALUE)
        scenario.close()
        return lastSeen
    }

    @Test
    fun withoutTheFix_dialogInsetsReportZeroEvenBehindARealStatusBar() {
        val top = measureSafeDrawingTopInsideDialog(applyFix = false)
        assertTrue(
            "A stock Dialog window fits system windows itself, so safeDrawingPadding() must see " +
                "nothing to pad for — got top=$top. If this fails, Compose's Dialog default changed " +
                "and DialogEdgeToEdge may no longer be necessary.",
            top == 0,
        )
    }

    @Test
    fun withTheFix_dialogContentClearsTheRealStatusBar() {
        val top = measureSafeDrawingTopInsideDialog(applyFix = true)
        assertTrue(
            "DialogEdgeToEdge must let safeDrawingPadding() see the real status bar inset, but " +
                "measured top=$top (expected > 0 on a device with a status bar)",
            top > 0,
        )
    }
}
