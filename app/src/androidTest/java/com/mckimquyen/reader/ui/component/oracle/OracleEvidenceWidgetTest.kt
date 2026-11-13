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
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import com.mckimquyen.reader.ui.page.oracle.OracleEvidenceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** ORACLE-03: the "related articles" part of the due-bet card, driven through its semantics tree on a real device. */
@RunWith(AndroidJUnit4::class)
class OracleEvidenceWidgetTest {

    private fun str(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val date = 1_766_188_800_000L // 2025-12-20 UTC
    private fun item(id: String) = OracleEvidence(id, "Title $id", "Wire", date)
    private fun bet(status: OracleBetStatus = OracleBetStatus.OPEN) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = OracleBetSide.YES, stake = 100, status = status, placedAt = 1)

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

        fun clickableLabels(): List<String> {
            val out = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                out.set(clickables(root).map { textsOf(it).joinToString(" ") })
            }
            return out.get()
        }

        fun isEnabled(text: String): Boolean {
            val out = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val node = firstClickable(root, text) ?: return@onActivity
                out.set(node.config.getOrNull(SemanticsProperties.Disabled) == null)
            }
            return out.get()
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

        private fun clickables(node: SemanticsNode): List<SemanticsNode> =
            (if (node.config.getOrNull(SemanticsActions.OnClick) != null) listOf(node) else emptyList()) +
                node.children.flatMap { clickables(it) }

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

    private fun <T> withSection(
        state: OracleBetUiState,
        evidence: OracleEvidenceUiState,
        opened: MutableList<String>,
        settled: MutableList<OracleOutcome>,
        awaitText: String,
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        MaterialTheme {
                            OracleBettingSection(
                                state = state, failed = false, onPlaceBet = { _, _ -> }, onSettle = { settled += it },
                                evidence = evidence, onOpenArticle = { opened += it },
                            )
                        }
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the section never rendered", screen.await { screen.has(awaitText) })
        block(screen)
    }

    private val awaiting get() = OracleBetUiState(OracleBetPhase.AWAITING_RESULT, 900, bet())
    private val awaitingTitle get() = str(R.string.oracle_bet_awaiting_title)

    @Test fun awaitingResult_listsTheRelatedArticles_andTappingOneReportsItsId() {
        val opened = mutableListOf<String>()
        withSection(awaiting, OracleEvidenceUiState.Ready(listOf(item("e1"), item("e2"))), opened, mutableListOf(), awaitingTitle) { screen ->
            assertTrue(screen.has(str(R.string.oracle_due_evidence_title)))
            assertTrue(screen.has("Title e1"))
            assertTrue(screen.has("Wire"))
            assertTrue(screen.has(str(R.string.oracle_due_evidence_hint)))
            assertTrue(screen.click("Title e2"))
        }
        assertEquals(listOf("e2"), opened)
    }

    @Test fun awaitingResult_withNothingFound_saysSo_andKeepsTheHint() {
        withSection(awaiting, OracleEvidenceUiState.Ready(emptyList()), mutableListOf(), mutableListOf(), awaitingTitle) { screen ->
            assertTrue(screen.has(str(R.string.oracle_due_evidence_none)))
            assertTrue(screen.has(str(R.string.oracle_due_evidence_hint)))
        }
    }

    @Test fun otherPhases_haveNoEvidenceSection_evenIfEvidenceIsPassed() {
        val open = OracleBetUiState(OracleBetPhase.BET_OPEN, 900, bet())
        withSection(open, OracleEvidenceUiState.Ready(listOf(item("e1"))), mutableListOf(), mutableListOf(), str(R.string.oracle_bet_open_hint)) { screen ->
            assertFalse(screen.has(str(R.string.oracle_due_evidence_title)))
            assertFalse(screen.has("Title e1"))
        }
    }

    @Test fun theThreeOutcomeButtons_stillWork_withEvidenceShown() {
        val settled = mutableListOf<OracleOutcome>()
        val opened = mutableListOf<String>()
        withSection(awaiting, OracleEvidenceUiState.Ready(listOf(item("e1"))), opened, settled, awaitingTitle) { screen ->
            assertTrue(screen.click(str(R.string.oracle_bet_yes)))
            assertTrue(screen.click(str(R.string.oracle_bet_no)))
            assertTrue(screen.click(str(R.string.oracle_bet_outcome_unclear)))
        }
        assertEquals(listOf(OracleOutcome.YES, OracleOutcome.NO, OracleOutcome.UNCLEAR), settled)
        assertEquals(emptyList<String>(), opened)
    }
}
