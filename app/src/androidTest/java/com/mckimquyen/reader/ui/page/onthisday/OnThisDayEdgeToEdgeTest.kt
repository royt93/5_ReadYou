package com.mckimquyen.reader.ui.page.onthisday

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendar
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnThisDayEdgeToEdgeTest {

    private data class Bounds(
        val titleTop: Int,
        val statusInsetTop: Int,
        val emptyBottom: Int,
        val decorHeight: Int,
        val navInsetBottom: Int,
    )

    @Test
    fun titleAndEmptyState_clearSystemBarInsetsInEdgeToEdgeWindow() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val viewModel = OnThisDayViewModel(
                        context = activity.application,
                        articleDao = database.articleDao(),
                        calendar = OnThisDayCalendar(),
                        ioDispatcher = Dispatchers.Unconfined,
                    )
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    activity.setContentView(
                        ComposeView(activity).apply {
                            setContent {
                                OnThisDayPage(
                                    navController = NavHostController(activity),
                                    viewModel = viewModel,
                                )
                            }
                        }
                    )
                }

                val deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS
                var bounds: Bounds? = null
                while (bounds == null && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val decor = activity.window.decorView
                        val insets = ViewCompat.getRootWindowInsets(decor)
                        val topInset = insets?.getInsets(
                            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                        )?.top ?: 0
                        val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                        val root = findComposeRoot(decor)
                        val titleBounds = root?.findTagBounds(ON_THIS_DAY_PAGE_TITLE_TAG)
                        val emptyBounds = root?.findTagBounds(ON_THIS_DAY_EMPTY_TAG)
                        if (topInset > 0 && bottomInset > 0 && titleBounds != null && emptyBounds != null) {
                            bounds = Bounds(titleBounds.top, topInset, emptyBounds.bottom, decor.height, bottomInset)
                        }
                    }
                    if (bounds == null) Thread.sleep(POLL_DELAY_MS)
                }

                val measured = bounds ?: throw AssertionError("On This Day title/empty or system insets not observed within deadline")
                assertTrue("Title under status bar: $measured", measured.titleTop >= measured.statusInsetTop)
                assertTrue(
                    "Empty text clears navigation bar: $measured",
                    measured.emptyBottom <= measured.decorHeight - measured.navInsetBottom,
                )
            }
        } finally {
            database.close()
        }
    }

    private fun findComposeRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findComposeRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun ViewRootForTest.findTagBounds(tag: String): android.graphics.Rect? {
        val stack = ArrayDeque<SemanticsNode>().apply { add(semanticsOwner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) {
                val bounds = node.boundsInWindow
                return android.graphics.Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            }
            stack.addAll(node.children)
        }
        return null
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
        const val POLL_DELAY_MS = 50L
    }
}
