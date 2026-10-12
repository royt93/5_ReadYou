package com.mckimquyen.reader.ui.component.reader

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.bilingual.ParagraphTranslator
import com.mckimquyen.reader.domain.model.bilingual.TranslationResult
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingual
import com.mckimquyen.reader.infrastructure.pref.LocalReadingBilingualTarget
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualPref
import com.mckimquyen.reader.infrastructure.pref.ReadingBilingualTargetPref
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bilingual Reader adds a second text block under every paragraph. On a real edge-to-edge window the
 * translation must stay inside the screen (never under the cutout / system bars, never wider than the
 * window), also when the translated text is much longer than the original.
 *
 * Same approach as EdgeToEdgeInsetsTest: own the Activity through ActivityScenario, go edge-to-edge before the
 * first frame and read the real node bounds instead of using ComposeTestRule.
 */
@RunWith(AndroidJUnit4::class)
class BilingualEdgeToEdgeTest {

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private data class Measured(val node: Bounds, val window: Bounds, val insets: Bounds)

    private val longTranslation = "Rất dài ".repeat(80).trim()

    private fun measure(translation: String, html: String): List<Measured> {
        val translator = ParagraphTranslator { _, _ -> TranslationResult.Translated(translation) }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                activity.setContentView(
                    ComposeView(activity).apply {
                        setContent {
                            CompositionLocalProvider(
                                LocalReadingBilingual provides ReadingBilingualPref.ON,
                                LocalReadingBilingualTarget provides ReadingBilingualTargetPref("vi"),
                                LocalParagraphTranslator provides translator,
                            ) {
                                LazyColumn(Modifier.safeDrawingPadding()) {
                                    Reader(link = "https://example.test/", content = html, onLinkClick = {})
                                }
                            }
                        }
                    }
                )
            }
            val deadline = System.currentTimeMillis() + 10_000
            var result: List<Measured> = emptyList()
            while (result.isEmpty() && System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val decor = activity.window.decorView
                    val i = ViewCompat.getRootWindowInsets(decor)
                        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    val compose = findComposeView(decor)
                    if (i != null && compose != null && i.top > 0) {
                        val found = translationBounds(compose)
                        if (found.isNotEmpty()) {
                            val w = Bounds(0, 0, decor.width, decor.height)
                            result = found.map { Measured(it, w, Bounds(i.left, i.top, i.right, i.bottom)) }
                        }
                    }
                }
                if (result.isEmpty()) Thread.sleep(50)
            }
            assertTrue("Never saw a translation node with real insets within 10s", result.isNotEmpty())
            return result
        }
    }

    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun translationBounds(compose: ViewRootForTest): List<Bounds> {
        val out = ArrayList<Bounds>()
        val stack = ArrayDeque<SemanticsNode>().apply { add(compose.semanticsOwner.unmergedRootSemanticsNode) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.config.getOrNull(SemanticsProperties.TestTag) == BILINGUAL_TRANSLATION_TAG) {
                val b = node.boundsInWindow
                out += Bounds(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            }
            stack.addAll(node.children)
        }
        return out
    }

    private fun assertInsideSafeArea(name: String, m: Measured) {
        assertTrue("$name left ${m.node.left} < inset ${m.insets.left}", m.node.left >= m.insets.left)
        assertTrue("$name top ${m.node.top} < status bar ${m.insets.top}", m.node.top >= m.insets.top)
        assertTrue(
            "$name right ${m.node.right} > window ${m.window.right - m.insets.right}",
            m.node.right <= m.window.right - m.insets.right,
        )
    }

    @Test
    fun shortTranslation_staysInsideTheSafeArea() {
        measure("Bản dịch ngắn.", "<p>A short paragraph for the test.</p>")
            .forEach { assertInsideSafeArea("short translation", it) }
    }

    @Test
    fun veryLongTranslation_wrapsInsteadOfOverflowingTheWindow() {
        val all = measure(longTranslation, "<p>One short original sentence.</p>")
        all.forEach { assertInsideSafeArea("long translation", it) }
        assertTrue(
            "long translation was not wrapped (width ${all.first().node.right - all.first().node.left})",
            all.first().node.bottom - all.first().node.top > 100,
        )
    }

    @Test
    fun firstParagraph_isNotPushedUnderTheStatusBar() {
        val first = measure("Bản dịch.", "<p>First paragraph, long enough to translate.</p>").minBy { it.node.top }
        assertInsideSafeArea("first translation", first)
    }

    @Test
    fun device_reportsANonZeroStatusBarInset_soTheAssertionsAreMeaningful() {
        val m = measure("Bản dịch.", "<p>Any paragraph that gets translated.</p>").first()
        assertTrue("No status-bar inset on this device; the test proves nothing here", m.insets.top > 0)
    }
}
