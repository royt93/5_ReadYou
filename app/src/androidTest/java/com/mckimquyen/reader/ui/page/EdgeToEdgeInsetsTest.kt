package com.mckimquyen.reader.ui.page

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticleInfo
import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import com.mckimquyen.reader.ui.page.digest.DIGEST_TITLE_TAG
import com.mckimquyen.reader.ui.page.digest.DigestPage
import com.mckimquyen.reader.ui.page.digest.DigestUiState
import com.mckimquyen.reader.ui.page.secondbrain.SECOND_BRAIN_TITLE_TAG
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainPage
import com.mckimquyen.reader.ui.page.secondbrain.SecondBrainUiState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MainActivity draws edge-to-edge (decorFitsSystemWindows=false). A full-screen page that ignores insets
 * puts its title under the status bar/cutout, where it is clipped and untappable.
 *
 * The page's outer modifier spans the whole window whether or not it pads, so measuring it proves nothing;
 * each page tags its title and we read that node's real position.
 *
 * No ComposeTestRule here on purpose: `createEmptyComposeRule` discovers compose hierarchies process-wide
 * and intermittently reports "No compose hierarchies found" when an ActivityScenario-owned Activity is
 * not yet registered (seen when this runs after other compose test classes). We own the Activity via
 * ActivityScenario, go edge-to-edge before the first frame, and walk the View tree ourselves.
 */
@RunWith(AndroidJUnit4::class)
class EdgeToEdgeInsetsTest {

    private data class Measured(val titleTop: Int, val insetTop: Int)

    private fun measureTitle(tag: String, page: @Composable (Modifier) -> Unit): Measured {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.setContentView(ComposeView(activity).apply { setContent { page(Modifier) } })
            }
            val deadline = System.currentTimeMillis() + 10_000
            var result: Measured? = null
            while (result == null && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val inset = ViewCompat.getRootWindowInsets(decor)
                        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        ?.top ?: 0
                    val top = findComposeView(decor)?.let { titleTopInWindow(it, tag) }
                    if (inset > 0 && top != null) result = Measured(top, inset)
                }
                if (result == null) Thread.sleep(50)
            }
            return result ?: throw AssertionError("Never saw title '$tag' with real system-bar insets within 10s")
        }
    }

    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun titleTopInWindow(compose: ViewRootForTest, tag: String): Int? {
        val owner: SemanticsOwner = compose.semanticsOwner
        val stack = ArrayDeque<SemanticsNode>().apply { add(owner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) return node.boundsInWindow.top.toInt()
            stack.addAll(node.children)
        }
        return null
    }

    private fun assertTitleClearsStatusBar(name: String, m: Measured) {
        assertTrue(
            "$name title starts at y=${m.titleTop} but the status bar/cutout occupies the first ${m.insetTop}px",
            m.titleTop >= m.insetTop,
        )
    }

    private val graph = BrainGraph(
        nodes = listOf(BrainNode("ai", "Ai", 1, listOf("a1"), 1L, 2L), BrainNode("c", "C", 1, listOf("a1"), 1L, 2L)),
        edges = listOf(BrainEdge("ai", "c", 1)),
        minTime = 1L,
        maxTime = 2L,
        articleInfo = mapOf("a1" to BrainArticleInfo("a1", "Story", 1L)),
    )

    @Test
    fun secondBrainReady_titleClearsStatusBar() = assertTitleClearsStatusBar(
        "SecondBrainPage(ready)",
        measureTitle(SECOND_BRAIN_TITLE_TAG) {
            SecondBrainPage(SecondBrainUiState.Ready(graph, "ai", 1L, 2L), {}, { _, _ -> }, {}, {}, it)
        },
    )

    @Test
    fun secondBrainEmpty_titleClearsStatusBar() = assertTitleClearsStatusBar(
        "SecondBrainPage(empty)",
        measureTitle(SECOND_BRAIN_TITLE_TAG) { SecondBrainPage(SecondBrainUiState.Empty, {}, { _, _ -> }, {}, {}, it) },
    )

    @Test
    fun digestEmpty_titleClearsStatusBar() = assertTitleClearsStatusBar(
        "DigestPage(empty)",
        measureTitle(DIGEST_TITLE_TAG) { DigestPage(DigestUiState.Empty, {}, {}, {}, modifier = it) },
    )

    @Test
    fun device_reportsANonZeroStatusBarInset_soTheAssertionsAreMeaningful() {
        val m = measureTitle(SECOND_BRAIN_TITLE_TAG) { SecondBrainPage(SecondBrainUiState.Empty, {}, { _, _ -> }, {}, {}, it) }
        assertTrue("No status-bar inset on this device; the insets tests prove nothing here", m.insetTop > 0)
    }
}
