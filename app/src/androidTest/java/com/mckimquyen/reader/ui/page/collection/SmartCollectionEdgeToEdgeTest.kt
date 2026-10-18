package com.mckimquyen.reader.ui.page.collection

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.sv.SmartCollectionMatcher
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartCollectionEdgeToEdgeTest {

    private data class Bounds(val titleTop: Int, val statusInsetTop: Int, val fabBottom: Int, val decorHeight: Int, val navInsetBottom: Int)

    @Test
    fun titleAndFab_clearSystemBarInsetsInEdgeToEdgeWindow() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).allowMainThreadQueries().build()

        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val dao = database.smartCollectionDao()
                    val matcher = SmartCollectionMatcher(
                        smartCollectionDao = dao,
                        semanticSearchEngine = SemanticSearchEngine(database.articleEmbeddingDao()),
                        articleDao = database.articleDao(),
                        dispatcher = Dispatchers.Unconfined,
                    )
                    val viewModel = SmartCollectionsViewModel(activity.application, dao, matcher)
                    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                    activity.setContentView(
                        ComposeView(activity).apply {
                            setContent {
                                SmartCollectionsPage(
                                    navController = androidx.navigation.NavHostController(activity),
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
                        val titleBounds = root?.findTagBounds(TEST_TAG_TITLE)
                        val fabBounds = root?.findTagBounds(TEST_TAG_FAB)
                        if (topInset > 0 && bottomInset > 0 && titleBounds != null && fabBounds != null) {
                            bounds = Bounds(titleBounds.top, topInset, fabBounds.bottom, decor.height, bottomInset)
                        }
                    }
                    if (bounds == null) Thread.sleep(POLL_DELAY_MS)
                }

                val measured = bounds ?: throw AssertionError("Smart Collections title/FAB or real system insets not observed")
                assertTrue("Title under status bar: $measured", measured.titleTop >= measured.statusInsetTop)
                assertTrue(
                    "FAB overlaps navigation bar: $measured",
                    measured.fabBottom <= measured.decorHeight - measured.navInsetBottom,
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
