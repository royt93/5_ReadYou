package com.mckimquyen.reader.ui.component.oracle

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.oracle.OracleClaimCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

/**
 * Same approach as DeepDiveSheetWidgetTest: no ComposeTestRule, the Activity is owned through ActivityScenario and the
 * body is driven through its semantics tree (a Dialog would be a separate window the tree walk cannot see).
 */
@RunWith(AndroidJUnit4::class)
class OracleBadgeSheetWidgetTest {

    private val card = OracleClaimCard(
        articleId = "a",
        question = "Will the chipmaker open the fab?",
        expiresAt = LocalDate.of(2026, 12, 31).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1,
        resolutionCriteria = "The company's own announcement.",
    )

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private class Screen(private val scenario: ActivityScenario<ComponentActivity>) {

        fun texts(): List<String> {
            val out = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                out.set(root?.let { collect(it) }.orEmpty())
            }
            return out.get()
        }

        fun has(text: String): Boolean = texts().any { it.contains(text) }

        fun clickableCount(): Int {
            val n = AtomicReference(0)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                n.set(countClickable(root))
            }
            return n.get()
        }

        fun click(text: String): Boolean {
            val clicked = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val target = firstClickable(root, text) ?: return@onActivity
                if (target.config.getOrNull(SemanticsProperties.Disabled) == null) {
                    target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                    clicked.set(true)
                }
            }
            return clicked.get()
        }

        fun await(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(50)
            }
            return condition()
        }

        private fun textsOf(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

        private fun collect(node: SemanticsNode): List<String> = textsOf(node) + node.children.flatMap { collect(it) }

        private fun countClickable(node: SemanticsNode): Int =
            (if (node.config.getOrNull(SemanticsActions.OnClick) != null) 1 else 0) + node.children.sumOf { countClickable(it) }

        private fun firstClickable(node: SemanticsNode, text: String): SemanticsNode? {
            val hasText = textsOf(node).any { it.contains(text) }
            if (hasText && node.config.getOrNull(SemanticsActions.OnClick) != null) return node
            for (child in node.children) firstClickable(child, text)?.let { return it }
            return null
        }

        @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
        private fun findComposeView(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
            return null
        }
    }

    private fun <T> withContent(awaitText: String, content: @androidx.compose.runtime.Composable () -> Unit, block: (Screen) -> T): T =
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply { setContent { MaterialTheme { content() } } })
            }
            val screen = Screen(scenario)
            assertTrue("the content never rendered", screen.await { screen.has(awaitText) })
            block(screen)
        }

    @Test fun badge_isShownAndClickingItCallsBack() {
        var clicks = 0
        withContent(str(R.string.oracle_badge_label), { OracleBadge(onClick = { clicks++ }) }) { screen ->
            assertTrue(screen.click(str(R.string.oracle_badge_label)))
        }
        assertEquals(1, clicks)
    }

    @Test fun badge_isSpokenWithItsAction() {
        val spoken = "${str(R.string.oracle_badge_label)}. ${str(R.string.oracle_badge_open)}"
        withContent(spoken, { OracleBadge(onClick = {}) }) { screen -> assertTrue(screen.has(spoken)) }
    }

    @Test fun sheet_showsQuestionDateCriteriaAndTheAiLabel() {
        withContent(card.question, { OracleClaimSheetContent(card, onDismiss = {}) }) { screen ->
            assertTrue(screen.has(card.question))
            assertTrue(screen.has(card.resolutionCriteria))
            assertTrue(screen.has(str(R.string.oracle_sheet_ai_label)))
            // Dated by the UTC day the AI gave, on whatever time zone the phone is in.
            assertTrue("the expected-by line must carry the year 2026", screen.texts().any { it.contains("2026") })
        }
    }

    @Test fun sheet_isReadOnly_onlyTheCloseButtonIsInteractive_andItDismisses() {
        var dismissed = 0
        withContent(card.question, { OracleClaimSheetContent(card, onDismiss = { dismissed++ }) }) { screen ->
            assertEquals("a read-only card has exactly one clickable control", 1, screen.clickableCount())
            assertFalse(screen.has("0 points"))
            assertTrue(screen.click(str(R.string.oracle_sheet_close)))
        }
        assertEquals(1, dismissed)
    }
}
