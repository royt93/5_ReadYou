package com.mckimquyen.reader.ui.page.reels

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Reels close button once sat under the status bar and swallowed every tap: [FeedbackIconButton]
 * forwards its `modifier` to the inner `Icon`, not to the clickable `IconButton`, so the inset has
 * to go on a wrapper around it. This checks the wrapper genuinely pushes the button clear.
 */
@RunWith(AndroidJUnit4::class)
class ReelsCloseButtonTest {

    @Test
    fun closeButtonSitsBelowTheStatusBar() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val latch = CountDownLatch(1)
        val buttonTop = AtomicInteger(Int.MIN_VALUE)
        val statusBarInset = AtomicInteger(Int.MIN_VALUE)

        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    val view = LocalView.current
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Mirrors ReelsPage: the inset goes on the wrapper, never on the button.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                        ) {
                            Box(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    // Where the button really lands, in window coordinates.
                                    buttonTop.compareAndSet(
                                        Int.MIN_VALUE,
                                        coordinates.boundsInWindow().top.toInt(),
                                    )
                                    statusBarInset.compareAndSet(
                                        Int.MIN_VALUE,
                                        ViewCompat.getRootWindowInsets(view)
                                            ?.getInsets(WindowInsetsCompat.Type.statusBars())
                                            ?.top
                                            ?: 0,
                                    )
                                    latch.countDown()
                                }
                            ) {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "close",
                                ) {}
                            }
                        }
                    }
                }
            }
            activity.setContentView(composeView)
        }

        assertTrue("Layout never settled", latch.await(10, TimeUnit.SECONDS))
        scenario.close()

        val top = buttonTop.get()
        val inset = statusBarInset.get()
        assertTrue("Never measured the button", top != Int.MIN_VALUE)
        assertTrue(
            "Close button top was $top but the status bar occupies the first $inset px, " +
                "so taps would be swallowed",
            top >= inset,
        )
    }
}
