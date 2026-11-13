package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import com.mckimquyen.reader.ui.page.oracle.OracleEvidenceUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OracleEvidenceSectionTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<String>()
    private val date = LocalDate.of(2026, 12, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun item(id: String) = OracleEvidence(id, "Title $id", "Wire", date)
    private fun bet(status: OracleBetStatus = OracleBetStatus.OPEN) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = OracleBetSide.YES, stake = 100, status = status, placedAt = 1)

    private fun show(phase: OracleBetPhase, evidence: OracleEvidenceUiState, withBet: Boolean = true) = compose.setContent {
        OracleBettingSection(
            state = OracleBetUiState(phase, 900, if (withBet) bet(if (phase == OracleBetPhase.SETTLED) OracleBetStatus.WON else OracleBetStatus.OPEN) else null),
            failed = false, onPlaceBet = { _, _ -> }, onSettle = {},
            evidence = evidence, onOpenArticle = { opened += it },
        )
    }

    private fun str(id: Int) = compose.activity.getString(id)
    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test fun awaiting_withItems_showsTitleRowsAndTheHint() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"), item("e2"))))
        assertEquals(1, count(ORACLE_EVIDENCE_SECTION_TAG))
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Title e1", substring = true)
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Wire", substring = true)
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1").assertTextContains("Dec 20, 2026", substring = true)
        assertEquals(1, count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e2"))
        compose.onNodeWithTag(ORACLE_EVIDENCE_HINT_TAG).assertTextContains(str(R.string.oracle_due_evidence_hint))
    }

    @Test fun fiveItems_showsOnlyThree() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready((1..5).map { item("e$it") }))
        assertEquals(3, count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e1") + count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e2") + count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e3"))
        assertEquals(0, count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e4") + count(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e5"))
    }

    @Test fun clickingARow_opensThatArticle() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"), item("e2"))))
        compose.onNodeWithTag(ORACLE_EVIDENCE_ITEM_TAG_PREFIX + "e2").performClick()
        assertEquals(listOf("e2"), opened)
    }

    @Test fun awaiting_withNothingFound_saysSo_andStillShowsTheHint() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(emptyList()))
        compose.onNodeWithTag(ORACLE_EVIDENCE_NONE_TAG).assertTextContains(str(R.string.oracle_due_evidence_none))
        assertEquals(1, count(ORACLE_EVIDENCE_HINT_TAG))
    }

    @Test fun awaiting_whileLoading_showsProgress_notTheNoneMessage() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Loading)
        assertEquals(1, count(ORACLE_EVIDENCE_LOADING_TAG))
        assertEquals(0, count(ORACLE_EVIDENCE_NONE_TAG))
    }

    @Test fun idle_showsNothingOfTheEvidenceSection() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Idle)
        assertEquals(0, count(ORACLE_EVIDENCE_SECTION_TAG))
    }

    private fun assertNoSectionIn(phase: OracleBetPhase, withBet: Boolean) {
        show(phase, OracleEvidenceUiState.Ready(listOf(item("e1"))), withBet = withBet)
        assertEquals("$phase", 0, count(ORACLE_EVIDENCE_SECTION_TAG))
    }

    @Test fun canBet_neverShowsTheSection() = assertNoSectionIn(OracleBetPhase.CAN_BET, withBet = false)

    @Test fun outOfPoints_neverShowsTheSection() = assertNoSectionIn(OracleBetPhase.OUT_OF_POINTS, withBet = false)

    @Test fun betOpen_neverShowsTheSection() = assertNoSectionIn(OracleBetPhase.BET_OPEN, withBet = true)

    @Test fun settled_neverShowsTheSection() = assertNoSectionIn(OracleBetPhase.SETTLED, withBet = true)

    @Test fun closedNoBet_neverShowsTheSection() = assertNoSectionIn(OracleBetPhase.CLOSED_NO_BET, withBet = false)

    @Test fun theSectionIsAboveTheThreeResultButtons() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"))))
        val evidenceTop = compose.onNodeWithTag(ORACLE_EVIDENCE_SECTION_TAG).fetchSemanticsNode().boundsInRoot.top
        val buttonTop = compose.onNodeWithTag(ORACLE_BET_RESULT_YES_TAG).fetchSemanticsNode().boundsInRoot.top
        assertEquals(true, evidenceTop < buttonTop)
    }

    @Test fun theExistingResultButtonsStillWork_withEvidenceShown() {
        show(OracleBetPhase.AWAITING_RESULT, OracleEvidenceUiState.Ready(listOf(item("e1"))))
        compose.onNodeWithTag(ORACLE_BET_RESULT_UNCLEAR_TAG).performClick()
        assertEquals(emptyList<String>(), opened)
    }
}
