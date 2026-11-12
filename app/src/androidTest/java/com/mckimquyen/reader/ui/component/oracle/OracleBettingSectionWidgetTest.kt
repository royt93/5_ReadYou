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
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Same approach as OracleBadgeSheetWidgetTest: no ComposeTestRule, the Activity is owned through ActivityScenario and
 * the section is driven through its semantics tree.
 */
@RunWith(AndroidJUnit4::class)
class OracleBettingSectionWidgetTest {

    private fun str(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun bet(status: OracleBetStatus, side: OracleBetSide = OracleBetSide.YES, stake: Int = 100) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = side, stake = stake, status = status, placedAt = 1)

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
        placed: MutableList<Pair<OracleBetSide, Int>>,
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
                                state = state, failed = false,
                                onPlaceBet = { s, n -> placed += s to n }, onSettle = { settled += it },
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

    @Test fun choosingASideAndAStake_thenPlacing_reportsTheChoice() {
        val placed = mutableListOf<Pair<OracleBetSide, Int>>()
        withSection(OracleBetUiState(OracleBetPhase.CAN_BET, 1000, null), placed, mutableListOf(), str(R.string.oracle_bet_pick_side)) { screen ->
            assertFalse("nothing chosen yet", screen.isEnabled(str(R.string.oracle_bet_place)))
            assertTrue(screen.click(str(R.string.oracle_bet_no)))
            assertTrue(screen.click(str(R.string.oracle_bet_stake_option, "100")))
            val summary = str(R.string.oracle_bet_place_summary, "100", str(R.string.oracle_bet_no))
            assertTrue(screen.await { screen.has(summary) })
            assertTrue(screen.click(summary))
        }
        assertEquals(listOf(OracleBetSide.NO to 100), placed)
    }

    @Test fun aStakeAboveTheBalance_cannotBeChosen() {
        withSection(OracleBetUiState(OracleBetPhase.CAN_BET, 120, null), mutableListOf(), mutableListOf(), str(R.string.oracle_bet_pick_side)) { screen ->
            assertTrue(screen.isEnabled(str(R.string.oracle_bet_stake_option, "100")))
            assertFalse(screen.isEnabled(str(R.string.oracle_bet_stake_option, "500")))
        }
    }

    @Test fun outOfPoints_hasNoControlAtAll_notEvenAWayToWatchAnAd() {
        withSection(OracleBetUiState(OracleBetPhase.OUT_OF_POINTS, 10, null), mutableListOf(), mutableListOf(), str(R.string.oracle_bet_out_of_points)) { screen ->
            assertEquals("a card that is out of points offers nothing to tap", emptyList<String>(), screen.clickableLabels())
        }
    }

    @Test fun whenTheDateHasPassed_threeOutcomesAreOffered_andEachIsReported() {
        val settled = mutableListOf<OracleOutcome>()
        withSection(OracleBetUiState(OracleBetPhase.AWAITING_RESULT, 900, bet(OracleBetStatus.OPEN)), mutableListOf(), settled, str(R.string.oracle_bet_awaiting_title)) { screen ->
            assertTrue(screen.click(str(R.string.oracle_bet_yes)))
            assertTrue(screen.click(str(R.string.oracle_bet_no)))
            assertTrue(screen.click(str(R.string.oracle_bet_outcome_unclear)))
        }
        assertEquals(listOf(OracleOutcome.YES, OracleOutcome.NO, OracleOutcome.UNCLEAR), settled)
    }
}
