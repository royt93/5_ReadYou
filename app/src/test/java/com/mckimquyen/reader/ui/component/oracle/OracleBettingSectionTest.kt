package com.mckimquyen.reader.ui.component.oracle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.ui.page.oracle.OracleBetUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OracleBettingSectionTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val placed = mutableListOf<Pair<OracleBetSide, Int>>()
    private val settled = mutableListOf<OracleOutcome>()

    private fun bet(status: OracleBetStatus, side: OracleBetSide = OracleBetSide.YES, stake: Int = 100) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = 9, side = side, stake = stake, status = status, placedAt = 1)

    private fun show(phase: OracleBetPhase, balance: Long = 1000, bet: OracleBet? = null, failed: Boolean = false) =
        compose.setContent {
            OracleBettingSection(
                state = OracleBetUiState(phase, balance, bet), failed = failed,
                onPlaceBet = { s, n -> placed += s to n }, onSettle = { settled += it },
            )
        }

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun tag(t: String) = compose.onNodeWithTag(t)
    private fun count(t: String) = compose.onAllNodesWithTag(t).fetchSemanticsNodes().size

    // --- always present

    private fun assertBalanceAndNote(phase: OracleBetPhase, bet: OracleBet?) {
        compose.setContent { OracleBettingSection(OracleBetUiState(phase, 1234, bet), false, { _, _ -> }, {}) }
        tag(ORACLE_BET_BALANCE_TAG).assertTextContains(str(R.string.oracle_bet_balance, "1,234"))
        tag(ORACLE_BET_NOTE_TAG).assertTextContains(str(R.string.oracle_bet_virtual_note))
    }

    @Test fun canBet_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.CAN_BET, null)
    @Test fun outOfPoints_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.OUT_OF_POINTS, null)
    @Test fun betOpen_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.BET_OPEN, bet(OracleBetStatus.OPEN))
    @Test fun awaiting_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.AWAITING_RESULT, bet(OracleBetStatus.OPEN))
    @Test fun settled_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.SETTLED, bet(OracleBetStatus.WON))
    @Test fun closedNoBet_showsTheBalanceAndTheVirtualNote() = assertBalanceAndNote(OracleBetPhase.CLOSED_NO_BET, null)

    // --- CAN_BET

    @Test fun canBet_startsWithNothingChosen_andThePlaceButtonIsOff() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_PLACE_TAG).assertIsNotEnabled()
        assertEquals(0, placed.size)
    }

    @Test fun canBet_sideAloneOrStakeAlone_isNotEnough() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_YES_TAG).performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsNotEnabled()
    }

    @Test fun canBet_stakeAlone_isNotEnough() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "50").performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsNotEnabled()
    }

    @Test fun canBet_sideAndStake_enableThePlaceButton_andReportTheChoice() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_NO_TAG).performClick()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "100").performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsEnabled().assertTextContains(str(R.string.oracle_bet_place_summary, "100", str(R.string.oracle_bet_no)))
        tag(ORACLE_BET_PLACE_TAG).performClick()
        assertEquals(listOf(OracleBetSide.NO to 100), placed)
    }

    @Test fun canBet_aStakeAboveTheBalanceIsDisabled() {
        show(OracleBetPhase.CAN_BET, balance = 120)
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "50").assertIsEnabled()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "100").assertIsEnabled()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "500").assertIsNotEnabled()
    }

    @Test fun canBet_aStakeEqualToTheBalanceCanBePlaced() {
        show(OracleBetPhase.CAN_BET, balance = 100)
        tag(ORACLE_BET_SIDE_YES_TAG).performClick()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "100").assertIsEnabled().performClick()
        tag(ORACLE_BET_PLACE_TAG).assertIsEnabled().performClick()
        assertEquals(listOf(OracleBetSide.YES to 100), placed)
    }

    @Test fun canBet_offersExactlyTheThreeStakes() {
        show(OracleBetPhase.CAN_BET)
        listOf("50", "100", "500").forEach { assertEquals(it, 1, count(ORACLE_BET_STAKE_TAG_PREFIX + it)) }
        assertEquals(0, count(ORACLE_BET_STAKE_TAG_PREFIX + "75"))
    }

    @Test fun canBet_changingTheSideKeepsTheStake_andTheLastChoiceWins() {
        show(OracleBetPhase.CAN_BET)
        tag(ORACLE_BET_SIDE_YES_TAG).performClick()
        tag(ORACLE_BET_STAKE_TAG_PREFIX + "50").performClick()
        tag(ORACLE_BET_SIDE_NO_TAG).performClick()
        tag(ORACLE_BET_PLACE_TAG).performClick()
        assertEquals(listOf(OracleBetSide.NO to 50), placed)
    }

    // --- OUT_OF_POINTS: no control at all, and above all no ad button

    @Test fun outOfPoints_hasOnlyText_noButtons() {
        show(OracleBetPhase.OUT_OF_POINTS, balance = 10)
        tag(ORACLE_BET_OUT_OF_POINTS_TAG).assertTextContains(str(R.string.oracle_bet_out_of_points))
        listOf(ORACLE_BET_PLACE_TAG, ORACLE_BET_SIDE_YES_TAG, ORACLE_BET_SIDE_NO_TAG, ORACLE_BET_RESULT_YES_TAG).forEach { assertEquals(it, 0, count(it)) }
        assertEquals(0, count(ORACLE_BET_STAKE_TAG_PREFIX + "50"))
    }

    // --- BET_OPEN

    @Test fun betOpen_showsTheStakeAndTheSide_withNoControls() {
        show(OracleBetPhase.BET_OPEN, balance = 900, bet = bet(OracleBetStatus.OPEN, OracleBetSide.NO, 100))
        tag(ORACLE_BET_OPEN_TAG).assertTextContains(str(R.string.oracle_bet_open, "100", str(R.string.oracle_bet_no)))
        listOf(ORACLE_BET_PLACE_TAG, ORACLE_BET_RESULT_YES_TAG, ORACLE_BET_RESULT_UNCLEAR_TAG).forEach { assertEquals(it, 0, count(it)) }
    }

    // --- AWAITING_RESULT

    @Test fun awaiting_offersThreeOutcomes_andReportsEach() {
        show(OracleBetPhase.AWAITING_RESULT, balance = 900, bet = bet(OracleBetStatus.OPEN))
        tag(ORACLE_BET_RESULT_YES_TAG).performClick()
        tag(ORACLE_BET_RESULT_NO_TAG).performClick()
        tag(ORACLE_BET_RESULT_UNCLEAR_TAG).performClick()
        assertEquals(listOf(OracleOutcome.YES, OracleOutcome.NO, OracleOutcome.UNCLEAR), settled)
    }

    @Test fun awaiting_explainsThatUnclearGivesThePointsBack() {
        show(OracleBetPhase.AWAITING_RESULT, balance = 900, bet = bet(OracleBetStatus.OPEN, OracleBetSide.YES, 500))
        tag(ORACLE_BET_OPEN_TAG).assertTextContains(str(R.string.oracle_bet_awaiting_hint, "500", str(R.string.oracle_bet_yes)))
    }

    // --- SETTLED

    @Test fun settled_won_showsTheNetGain() {
        show(OracleBetPhase.SETTLED, balance = 1100, bet = bet(OracleBetStatus.WON, stake = 100))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_won, "100"))
    }

    @Test fun settled_lost_showsTheLoss() {
        show(OracleBetPhase.SETTLED, balance = 900, bet = bet(OracleBetStatus.LOST, stake = 500))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_lost, "500"))
    }

    @Test fun settled_refunded_showsTheRefund() {
        show(OracleBetPhase.SETTLED, balance = 1000, bet = bet(OracleBetStatus.REFUNDED, stake = 50))
        tag(ORACLE_BET_SETTLED_TAG).assertTextContains(str(R.string.oracle_bet_settled_refunded, "50"))
    }

    // --- CLOSED_NO_BET and failure

    @Test fun closedWithoutABet_saysSo_withNoControls() {
        show(OracleBetPhase.CLOSED_NO_BET)
        tag(ORACLE_BET_CLOSED_TAG).assertTextContains(str(R.string.oracle_bet_closed_no_bet))
        assertEquals(0, count(ORACLE_BET_PLACE_TAG))
    }

    @Test fun failure_showsTheErrorMessage() {
        show(OracleBetPhase.CAN_BET, failed = true)
        tag(ORACLE_BET_ERROR_TAG).assertTextContains(str(R.string.oracle_bet_error))
    }

    @Test fun noFailure_noErrorText() {
        show(OracleBetPhase.CAN_BET, failed = false)
        assertEquals(0, count(ORACLE_BET_ERROR_TAG))
    }
}
