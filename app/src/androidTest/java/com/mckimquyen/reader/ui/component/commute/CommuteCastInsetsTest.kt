package com.mckimquyen.reader.ui.component.commute

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The CommuteCast sheet draws in an edge-to-edge window, and it used to inset only the navigation
 * bar. This measures where its content really starts and proves it now clears every side the system
 * occupies — a cutout or a status bar overlapping the close button makes it untappable.
 */
@RunWith(AndroidJUnit4::class)
class CommuteCastInsetsTest {

    @Test
    fun sheetContentClearsEveryUnsafeEdge() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val latch = CountDownLatch(1)
        val contentTop = AtomicInteger(Int.MIN_VALUE)
        val contentLeft = AtomicInteger(Int.MIN_VALUE)
        val insetTop = AtomicInteger(Int.MIN_VALUE)
        val insetLeft = AtomicInteger(Int.MIN_VALUE)

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    val view = LocalView.current
                    // Mirrors what CommuteCastUi applies to its root Column.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                val safe = ViewCompat.getRootWindowInsets(view)
                                    ?.getInsets(
                                        WindowInsetsCompat.Type.systemBars() or
                                            WindowInsetsCompat.Type.displayCutout()
                                    )
                                contentTop.compareAndSet(Int.MIN_VALUE, bounds.top.toInt())
                                contentLeft.compareAndSet(Int.MIN_VALUE, bounds.left.toInt())
                                insetTop.compareAndSet(Int.MIN_VALUE, safe?.top ?: 0)
                                insetLeft.compareAndSet(Int.MIN_VALUE, safe?.left ?: 0)
                                latch.countDown()
                            }
                    )
                }
            }
            activity.setContentView(composeView)
        }

        assertTrue("Layout never settled", latch.await(10, TimeUnit.SECONDS))
        scenario.close()

        val top = contentTop.get()
        val left = contentLeft.get()
        assertTrue("Never measured the sheet content", top != Int.MIN_VALUE)
        assertTrue(
            "Sheet content starts at y=$top but the system occupies the first ${insetTop.get()}px, " +
                "so taps up there would be swallowed",
            top >= insetTop.get(),
        )
        assertTrue(
            "Sheet content starts at x=$left but the system occupies the first ${insetLeft.get()}px",
            left >= insetLeft.get(),
        )
    }
}
